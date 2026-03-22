/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import io.github.gbkt.emulator.EmulatorConfig
import io.github.gbkt.emulator.GbEmulator
import io.github.gbkt.emulator.MemoryAccess
import io.github.gbkt.emulator.debug.DebugLogEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Unit tests for [AgentDebugSession] and [AgentSessionConfig].
 *
 * AgentDebugSession wraps a real [io.github.gbkt.emulator.CoffeeGbEmulator], so most tests use
 * a stub emulator injected via the testable constructor overload. A real ROM is needed only for
 * lifecycle tests.
 */
class AgentDebugSessionTest {

    @TempDir
    lateinit var tempDir: File

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Creates a fake ROM file that exists on disk (but may not be a valid GB ROM). */
    private fun fakRom(name: String = "test.gb"): File =
        File(tempDir, name).also { it.writeBytes(ByteArray(64)) }

    /** Creates a sym file with two symbols: _score at C100, _lives at C101. */
    private fun writeSymFile(): File =
        File(tempDir, "test.sym").also {
            it.writeText("DEF _score 00:C100\nDEF _lives 00:C101\n")
        }

    /** Minimal MemoryAccess backed by a flat byte array. */
    private fun mockMemory(vararg patches: Pair<Int, Int>): MemoryAccess {
        val mem = IntArray(0x10000) { 0 }
        for ((addr, value) in patches) {
            mem[addr] = value
        }
        return object : MemoryAccess {
            override fun readByte(address: Int): Int = mem[address]
            override fun writeByte(address: Int, value: Int) { mem[address] = value }
        }
    }

    /** Minimal GbEmulator stub with controllable isPaused state. */
    private fun stubEmulator(
        memory: MemoryAccess = mockMemory(),
        paused: Boolean = true,
        running: Boolean = true,
    ): GbEmulator =
        object : GbEmulator {
            private var _paused = paused
            private var _running = running

            override fun start() { _running = true }
            override fun stop() { _running = false }
            override fun pause() { _paused = true }
            override fun resume() { _paused = false }
            override fun stepFrame() {} // no-op
            override fun setSpeed(multiplier: Float) = Unit
            override fun getFrameBuffer(): IntArray = IntArray(160 * 144) { 0x00FF00 } // green
            override fun getMemory(): MemoryAccess = memory
            override fun getDebugLog(): List<DebugLogEntry> = emptyList()
            override fun isRunning(): Boolean = _running
            override fun isPaused(): Boolean = _paused
            override val isHeadless: Boolean = true
        }

    // ── AgentSessionConfig tests ──────────────────────────────────────────────

    @Test
    fun `AgentSessionConfig toEmulatorConfig produces headless config`() {
        val rom = fakRom()
        val config = AgentSessionConfig(romFile = rom)
        val emConfig: EmulatorConfig = config.toEmulatorConfig()

        assertTrue(emConfig.headless, "toEmulatorConfig must produce headless=true")
        assertEquals(rom, emConfig.romFile)
    }

    @Test
    fun `AgentSessionConfig toEmulatorConfig propagates gbcMode`() {
        val rom = fakRom()
        val config = AgentSessionConfig(romFile = rom, gbcMode = true)
        val emConfig: EmulatorConfig = config.toEmulatorConfig()

        assertTrue(emConfig.gbcMode, "gbcMode must be propagated to EmulatorConfig")
    }

    @Test
    fun `AgentSessionConfig screenshotDir defaults adjacent to romFile`() {
        val rom = fakRom()
        val config = AgentSessionConfig(romFile = rom)
        assertEquals(File(tempDir, "screenshots"), config.screenshotDir)
    }

    @Test
    fun `AgentSessionConfig init throws when romFile does not exist`() {
        val nonExistent = File(tempDir, "missing.gb")
        assertThrows(IllegalArgumentException::class.java) {
            AgentSessionConfig(romFile = nonExistent)
        }
    }

    @Test
    fun `AgentSessionConfig default watchVariables is empty`() {
        val rom = fakRom()
        val config = AgentSessionConfig(romFile = rom)
        assertTrue(config.watchVariables.isEmpty())
    }

    @Test
    fun `AgentSessionConfig default gbcMode is false`() {
        val rom = fakRom()
        val config = AgentSessionConfig(romFile = rom)
        assertFalse(config.gbcMode)
    }

    // ── AgentDebugSession lifecycle tests ─────────────────────────────────────

    @Test
    fun `methods throw ISE when session not started`() {
        val rom = fakRom()
        val config = AgentSessionConfig(romFile = rom)
        val session = AgentDebugSession(config)

        assertThrows(IllegalStateException::class.java) { session.runFrames(1) }
        assertThrows(IllegalStateException::class.java) { session.captureScreenshot("test") }
        assertThrows(IllegalStateException::class.java) {
            session.executeInputScript(inputScript { wait(1) })
        }
        assertThrows(IllegalStateException::class.java) { session.readVariable("score") }
        assertThrows(IllegalStateException::class.java) { session.readAllVariables() }
        assertThrows(IllegalStateException::class.java) { session.saveState(File(tempDir, "s.gbst")) }
        assertThrows(IllegalStateException::class.java) { session.getDebugLog() }
    }

    @Test
    fun `frameCount starts at zero`() {
        val rom = fakRom()
        val config = AgentSessionConfig(romFile = rom)
        val session = AgentDebugSession(config)
        assertEquals(0, session.frameCount)
    }

    // ── AgentDebugSession with stub emulator ──────────────────────────────────

    @Test
    fun `runFrames increments frameCount`() {
        val rom = fakRom()
        val symFile = writeSymFile()
        val config = AgentSessionConfig(romFile = rom, symFile = symFile)
        val stub = stubEmulator()

        val session = AgentDebugSession(config, stubEmulatorFactory = { stub })
        session.start()

        assertEquals(0, session.frameCount)
        session.runFrames(5)
        assertEquals(5, session.frameCount)
        session.runFrames(3)
        assertEquals(8, session.frameCount)

        session.stop()
    }

    @Test
    fun `readVariable delegates to VariableInspector`() {
        val rom = fakRom()
        val symFile = writeSymFile()
        val memory = mockMemory(0xC100 to 42)
        val config = AgentSessionConfig(romFile = rom, symFile = symFile)
        val stub = stubEmulator(memory = memory)

        val session = AgentDebugSession(config, stubEmulatorFactory = { stub })
        session.start()

        val value = session.readVariable("score")
        assertNotNull(value)
        assertEquals(42, value)

        session.stop()
    }

    @Test
    fun `readVariable returns null for unknown variable`() {
        val rom = fakRom()
        val config = AgentSessionConfig(romFile = rom)
        val stub = stubEmulator()

        val session = AgentDebugSession(config, stubEmulatorFactory = { stub })
        session.start()

        val value = session.readVariable("nonexistent")
        assertEquals(null, value)

        session.stop()
    }

    @Test
    fun `readAllVariables returns all variables from sym file`() {
        val rom = fakRom()
        val symFile = writeSymFile()
        val memory = mockMemory(0xC100 to 10, 0xC101 to 3)
        val config = AgentSessionConfig(romFile = rom, symFile = symFile)
        val stub = stubEmulator(memory = memory)

        val session = AgentDebugSession(config, stubEmulatorFactory = { stub })
        session.start()

        val all = session.readAllVariables()
        assertEquals(2, all.size)
        assertEquals(10, all["score"])
        assertEquals(3, all["lives"])

        session.stop()
    }

    @Test
    fun `captureScreenshot writes PNG to screenshotDir`() {
        val rom = fakRom()
        val config = AgentSessionConfig(
            romFile = rom,
            screenshotDir = File(tempDir, "shots"),
        )
        val stub = stubEmulator()

        val session = AgentDebugSession(config, stubEmulatorFactory = { stub })
        session.start()

        val png = session.captureScreenshot("test_label")
        assertTrue(png.exists(), "PNG file should exist after captureScreenshot")
        assertTrue(png.name.startsWith("test_label"), "PNG name should start with label")
        assertTrue(png.name.endsWith(".png"), "PNG should have .png extension")

        session.stop()
    }

    @Test
    fun `stop after start does not throw`() {
        val rom = fakRom()
        val config = AgentSessionConfig(romFile = rom)
        val stub = stubEmulator()

        val session = AgentDebugSession(config, stubEmulatorFactory = { stub })
        session.start()
        session.stop()
        // Double stop should not throw
        session.stop()
    }

    @Test
    fun `close delegates to stop (Closeable)`() {
        val rom = fakRom()
        val config = AgentSessionConfig(romFile = rom)
        val stub = stubEmulator()

        val session = AgentDebugSession(config, stubEmulatorFactory = { stub })
        session.start()
        session.close() // Closeable.close() = stop()
        // After close, methods should throw ISE again
        assertThrows(IllegalStateException::class.java) { session.runFrames(1) }
    }

    @Test
    fun `start called twice throws ISE`() {
        val rom = fakRom()
        val config = AgentSessionConfig(romFile = rom)
        val stub = stubEmulator()

        val session = AgentDebugSession(config, stubEmulatorFactory = { stub })
        session.start()
        assertThrows(IllegalStateException::class.java) { session.start() }
        session.stop()
    }

    @Test
    fun `all methods throw ISE after close`() {
        val rom = fakRom()
        val symFile = writeSymFile()
        val config = AgentSessionConfig(romFile = rom, symFile = symFile)
        val stub = stubEmulator()

        val session = AgentDebugSession(config, stubEmulatorFactory = { stub })
        session.start()
        session.close()

        assertThrows(IllegalStateException::class.java) { session.runFrames(1) }
        assertThrows(IllegalStateException::class.java) { session.captureScreenshot("test") }
        assertThrows(IllegalStateException::class.java) {
            session.executeInputScript(inputScript { wait(1) })
        }
        assertThrows(IllegalStateException::class.java) { session.readVariable("score") }
        assertThrows(IllegalStateException::class.java) { session.readAllVariables() }
        assertThrows(IllegalStateException::class.java) { session.writeVariable("score", 1) }
        assertThrows(IllegalStateException::class.java) { session.saveState(File(tempDir, "s.gbst")) }
        assertThrows(IllegalStateException::class.java) { session.getDebugLog() }
        assertThrows(IllegalStateException::class.java) { session.getFrameBuffer() }
        assertThrows(IllegalStateException::class.java) { session.getMemory() }
    }

    @Test
    fun `stop when never started is safe`() {
        val rom = fakRom()
        val config = AgentSessionConfig(romFile = rom)
        val session = AgentDebugSession(config)
        // Should not throw
        session.stop()
    }

    @Test
    fun `start after stop is allowed`() {
        val rom = fakRom()
        val config = AgentSessionConfig(romFile = rom)
        val stub = stubEmulator()

        val session = AgentDebugSession(config, stubEmulatorFactory = { stub })
        session.start()
        session.runFrames(5)
        assertEquals(5, session.frameCount)
        session.stop()

        // Re-start should work — new emulator instance
        session.start()
        session.runFrames(3)
        // frameCount is cumulative within the session object (not reset)
        assertEquals(8, session.frameCount)
        session.stop()
    }

    @Test
    fun `watchVariables filters readAll for screenshot sidecar`() {
        val rom = fakRom()
        val symFile = writeSymFile()
        val memory = mockMemory(0xC100 to 99, 0xC101 to 2)
        val config = AgentSessionConfig(
            romFile = rom,
            symFile = symFile,
            screenshotDir = File(tempDir, "shots"),
            watchVariables = listOf("score"), // only score, not lives
        )
        val stub = stubEmulator(memory = memory)

        val session = AgentDebugSession(config, stubEmulatorFactory = { stub })
        session.start()

        val png = session.captureScreenshot("filtered")

        // Check that JSON sidecar only has "score" variable
        val jsonFile = File(png.parent, png.nameWithoutExtension + ".json")
        assertTrue(jsonFile.exists(), "JSON sidecar should exist")
        val jsonText = jsonFile.readText()
        assertTrue(jsonText.contains("\"score\""), "sidecar should contain score")
        assertFalse(jsonText.contains("\"lives\""), "sidecar should not contain lives when filtered")

        session.stop()
    }
}
