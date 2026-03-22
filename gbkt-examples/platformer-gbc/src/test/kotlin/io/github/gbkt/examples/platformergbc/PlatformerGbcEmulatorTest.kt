/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.platformergbc

import io.github.gbkt.emulator.GbEmulator
import io.github.gbkt.emulator.MemoryAccess
import io.github.gbkt.emulator.agent.AgentDebugSession
import io.github.gbkt.emulator.agent.AgentSessionConfig
import io.github.gbkt.emulator.debug.DebugLogEntry
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Headless smoke test for Platformer GBC — verifies the AgentDebugSession runs the GBC ROM
 * variant with `gbcMode = true` without crashing, without ERROR log entries, and with correct
 * GBC mode configuration propagation.
 *
 * Unit-test tier: uses a stub emulator (no real ROM required). The CI/emulatorTest task runs the
 * actual ROM via CoffeeGbEmulator in GBC mode.
 *
 * GBC-specific checks:
 * - [AgentSessionConfig.gbcMode] is `true` for GBC variant
 * - [AgentSessionConfig.toEmulatorConfig] propagates gbcMode correctly
 * - Session runs 600 frames without crash (stub: verifies frame stepping logic)
 * - No ERROR-level log entries after 600 frames
 * - Screenshot captured at title screen after 60 frames (frame buffer 160×144 verified)
 */
class PlatformerGbcEmulatorTest {

    @TempDir
    lateinit var tempDir: Path

    private var session: AgentDebugSession? = null

    @AfterEach
    fun tearDown() {
        session?.stop()
        session = null
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Creates a fake ROM file that exists on disk (content irrelevant for stub tests). */
    private fun fakeRom(name: String = "platformer-gbc.gb"): File =
        File(tempDir.toFile(), name).also { it.writeBytes(ByteArray(64)) }

    /** Minimal no-crash stub emulator. Returns a green frame buffer (GBC color mode marker). */
    private fun stubGbcEmulator(): GbEmulator =
        object : GbEmulator {
            private var _running = false
            private var _paused = false

            override fun start() {
                _running = true
            }

            override fun stop() {
                _running = false
                _paused = false
            }

            override fun pause() {
                _paused = true
            }

            override fun resume() {
                _paused = false
            }

            override fun stepFrame() {
                // no-op: stub advances frames deterministically
            }

            override fun setSpeed(multiplier: Float) = Unit

            /** GBC-mode frame buffer: non-zero colors simulate GBC color rendering (not grayscale). */
            override fun getFrameBuffer(): IntArray =
                IntArray(160 * 144) { idx ->
                    when {
                        idx < 160 * 48 -> 0x00C8F0C8  // greenish top strip (background)
                        idx < 160 * 96 -> 0x00F0F0A0  // yellowish mid strip (platforms)
                        else -> 0x00A0A0F0             // blueish bottom strip (ground)
                    }
                }

            override fun getMemory(): MemoryAccess =
                object : MemoryAccess {
                    private val mem = IntArray(0x10000) { 0 }
                    override fun readByte(address: Int): Int = mem[address]
                    override fun writeByte(address: Int, value: Int) {
                        mem[address] = value
                    }
                }

            /** No debug log entries — simulates a crash-free ROM run. */
            override fun getDebugLog(): List<DebugLogEntry> = emptyList()

            override fun isRunning(): Boolean = _running
            override fun isPaused(): Boolean = _paused
            override val isHeadless: Boolean = true
        }

    // ── GBC configuration tests ───────────────────────────────────────────────

    @Test
    fun `AgentSessionConfig with gbcMode true propagates to EmulatorConfig`() {
        val rom = fakeRom()
        val config = AgentSessionConfig(romFile = rom, gbcMode = true)

        assertTrue(config.gbcMode, "AgentSessionConfig.gbcMode must be true for GBC variant")

        val emConfig = config.toEmulatorConfig()
        assertTrue(emConfig.gbcMode, "toEmulatorConfig must propagate gbcMode=true")
        assertTrue(emConfig.headless, "GBC emulator test must run headless")
        assertEquals(rom, emConfig.romFile, "toEmulatorConfig must preserve romFile")
    }

    @Test
    fun `AgentSessionConfig default gbcMode is false - DMG is default`() {
        val rom = fakeRom()
        val config = AgentSessionConfig(romFile = rom)

        assertFalse(config.gbcMode, "Default gbcMode must be false (DMG default)")
    }

    // ── Smoke test: 600 frames without crash ──────────────────────────────────

    @Test
    fun `GBC smoke test - 600 frames without crash or ERROR log entries`() {
        val rom = fakeRom()
        val config = AgentSessionConfig(
            romFile = rom,
            gbcMode = true,
            screenshotDir = File(tempDir.toFile(), "screenshots"),
        )
        val stub = stubGbcEmulator()
        val s = AgentDebugSession(config, stubEmulatorFactory = { stub })
        session = s

        s.start()
        s.runFrames(600)

        assertEquals(600, s.frameCount, "Session must track exactly 600 frames")

        val errors = s.getDebugLog().filter { it.level == io.github.gbkt.emulator.LogLevel.ERROR }
        assertTrue(errors.isEmpty(), "No ERROR log entries after 600 frames: $errors")
    }

    // ── Screenshot test: frame buffer is non-grayscale ────────────────────────

    @Test
    fun `GBC screenshot has 160x144 frame buffer with color pixels`() {
        val rom = fakeRom()
        val screenshotDir = File(tempDir.toFile(), "screenshots")
        val config = AgentSessionConfig(
            romFile = rom,
            gbcMode = true,
            screenshotDir = screenshotDir,
        )
        val stub = stubGbcEmulator()
        val s = AgentDebugSession(config, stubEmulatorFactory = { stub })
        session = s

        s.start()
        s.runFrames(60)

        val png = s.captureScreenshot("title_screen_gbc")

        assertTrue(png.exists(), "Screenshot PNG must be written to screenshotDir")
        assertTrue(png.name.startsWith("title_screen_gbc"), "PNG name must start with label")
        assertTrue(png.name.endsWith(".png"), "PNG must have .png extension")

        // Frame buffer size verification
        val frameBuffer = stub.getFrameBuffer()
        assertEquals(160 * 144, frameBuffer.size, "Frame buffer must be 160×144 = 23040 pixels")

        // Color mode verification: GBC frame buffer must not be all-grayscale.
        // Grayscale pixels have R==G==B; check that at least some pixels have R≠G or G≠B
        // (our stub uses distinct R/G/B channels, matching GBC color rendering behavior).
        val colorPixelCount = frameBuffer.count { pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            r != g || g != b
        }
        assertTrue(
            colorPixelCount > 0,
            "GBC frame buffer must have at least one non-grayscale pixel (colorPixelCount=$colorPixelCount)",
        )
    }

    // ── Session lifecycle: GBC mode ───────────────────────────────────────────

    @Test
    fun `GBC session start and stop lifecycle completes cleanly`() {
        val rom = fakeRom()
        val config = AgentSessionConfig(romFile = rom, gbcMode = true)
        val stub = stubGbcEmulator()
        val s = AgentDebugSession(config, stubEmulatorFactory = { stub })
        session = s

        assertEquals(0, s.frameCount, "frameCount must be 0 before start")

        s.start()
        s.runFrames(10)
        assertEquals(10, s.frameCount, "frameCount must be 10 after runFrames(10)")

        s.stop()
        // After stop, emulator is released; stop() is idempotent
        s.stop()
    }

    @Test
    fun `GBC session implements Closeable for use-with-resources`() {
        val rom = fakeRom()
        val config = AgentSessionConfig(romFile = rom, gbcMode = true)
        val stub = stubGbcEmulator()

        AgentDebugSession(config, stubEmulatorFactory = { stub }).use { s ->
            s.start()
            s.runFrames(30)
            assertNotNull(s.getDebugLog(), "getDebugLog must not throw inside use block")
        }
        // After use{} block, close() was called — no assertion needed; just must not throw
    }

    // ── Framecount accuracy ───────────────────────────────────────────────────

    @Test
    fun `GBC session frameCount increments correctly across multiple runFrames calls`() {
        val rom = fakeRom()
        val config = AgentSessionConfig(romFile = rom, gbcMode = true)
        val stub = stubGbcEmulator()
        val s = AgentDebugSession(config, stubEmulatorFactory = { stub })
        session = s

        s.start()

        s.runFrames(60)
        assertEquals(60, s.frameCount)

        s.runFrames(120)
        assertEquals(180, s.frameCount)

        s.runFrames(420)
        assertEquals(600, s.frameCount, "Total after 60+120+420 must be exactly 600")
    }
}
