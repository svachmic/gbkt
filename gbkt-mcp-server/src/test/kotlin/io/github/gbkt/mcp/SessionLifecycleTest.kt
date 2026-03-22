/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.mcp

import io.github.gbkt.emulator.GbEmulator
import io.github.gbkt.emulator.MemoryAccess
import io.github.gbkt.emulator.debug.DebugLogEntry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Real lifecycle tests for [McpEmulatorSession] using stub emulators.
 */
class SessionLifecycleTest {

    @TempDir
    lateinit var tempDir: File

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fakeRom(): File =
        File(tempDir, "test.gb").also { it.writeBytes(ByteArray(64)) }

    private fun writeSymFile(): File =
        File(tempDir, "test.sym").also {
            it.writeText("DEF _score 00:C100\nDEF _lives 00:C101\nDEF _current_scene 00:C102\n")
        }

    private fun mockMemory(vararg patches: Pair<Int, Int>): MemoryAccess {
        val mem = IntArray(0x10000) { 0 }
        for ((addr, value) in patches) { mem[addr] = value }
        return object : MemoryAccess {
            override fun readByte(address: Int): Int = mem[address]
            override fun writeByte(address: Int, value: Int) { mem[address] = value }
        }
    }

    private fun stubEmulator(memory: MemoryAccess = mockMemory()): GbEmulator =
        object : GbEmulator {
            private var _paused = true
            private var _running = false
            override fun start() { _running = true }
            override fun stop() { _running = false }
            override fun pause() { _paused = true }
            override fun resume() { _paused = false }
            override fun stepFrame() = Unit
            override fun setSpeed(multiplier: Float) = Unit
            override fun getFrameBuffer(): IntArray = IntArray(160 * 144) { 0x00FF00 }
            override fun getMemory(): MemoryAccess = memory
            override fun getDebugLog(): List<DebugLogEntry> = emptyList()
            override fun isRunning(): Boolean = _running
            override fun isPaused(): Boolean = _paused
            override val isHeadless: Boolean = true
        }

    private fun makeSession(memory: MemoryAccess = mockMemory()): McpEmulatorSession =
        McpEmulatorSession(stubEmulatorFactory = { stubEmulator(memory) })

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    fun `start transitions isActive false to true`() = runTest {
        val session = makeSession()
        assertFalse(session.isActive())

        session.start(fakeRom(), writeSymFile())

        assertTrue(session.isActive())
        session.stop()
    }

    @Test
    fun `stop transitions isActive true to false`() = runTest {
        val session = makeSession()
        session.start(fakeRom(), writeSymFile())
        assertTrue(session.isActive())

        session.stop()

        assertFalse(session.isActive())
    }

    @Test
    fun `double start throws IllegalStateException`() = runTest {
        val session = makeSession()
        session.start(fakeRom(), writeSymFile())

        assertThrows<IllegalStateException> {
            kotlinx.coroutines.runBlocking {
                session.start(fakeRom(), writeSymFile())
            }
        }

        session.stop()
    }

    @Test
    fun `stop when inactive is safe`() = runTest {
        val session = makeSession()
        assertFalse(session.isActive())

        session.stop() // should not throw

        assertFalse(session.isActive())
    }

    @Test
    fun `step after start returns observation with frame 1`() = runTest {
        val session = makeSession()
        session.start(fakeRom(), writeSymFile())

        val obs = session.step()

        assertEquals(1, obs.frame)
        session.stop()
    }

    @Test
    fun `observe returns cached after step`() = runTest {
        val session = makeSession()
        session.start(fakeRom(), writeSymFile())

        val stepped = session.step(3)
        val observed = session.observe()

        assertEquals(stepped.frame, observed.frame)
        session.stop()
    }

    @Test
    fun `observe auto-steps when no prior observation`() = runTest {
        val session = makeSession()
        session.start(fakeRom(), writeSymFile())

        val obs = session.observe()

        assertEquals(1, obs.frame)
        session.stop()
    }

    @Test
    fun `step before start throws IllegalStateException`() = runTest {
        val session = makeSession()
        assertThrows<IllegalStateException> {
            kotlinx.coroutines.runBlocking { session.step() }
        }
    }

    @Test
    fun `observe before start throws IllegalStateException`() = runTest {
        val session = makeSession()
        assertThrows<IllegalStateException> {
            kotlinx.coroutines.runBlocking { session.observe() }
        }
    }

    @Test
    fun `describeGame returns null when no session`() {
        val session = makeSession()
        assertNull(session.describeGame())
    }
}
