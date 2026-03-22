/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator

import io.github.gbkt.emulator.debug.DebugLogEntry
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

/**
 * Tests for [CoffeeGbEmulator] and supporting types.
 *
 * Tests are split into:
 * 1. Pure value/config tests — no ROM file needed
 * 2. Minimal ROM lifecycle tests — use a synthesized Game Boy ROM header
 */
class CoffeeGbEmulatorTest {

    @TempDir lateinit var tempDir: Path

    private var emulator: CoffeeGbEmulator? = null

    @AfterEach
    fun tearDown() {
        emulator?.let { emu ->
            if (emu.isRunning()) {
                emu.stop()
            }
        }
        emulator = null
    }

    // ── 1. Config / value tests (no ROM needed) ───────────────────────────────

    @Test
    fun `EmulatorConfig has correct defaults`() {
        val romFile = File("fake.gb")
        val config = EmulatorConfig(romFile)
        assertFalse(config.headless, "headless should default to false")
        assertEquals(4, config.scale, "scale should default to 4")
        assertNull(config.sourceMapsDir, "sourceMapsDir should default to null")
        assertNull(config.logFile, "logFile should default to null")
        assertEquals(10_000, config.maxLogEntries, "maxLogEntries should default to 10_000")
    }

    @Test
    fun `EmulatorConfig rejects scale below 1`() {
        assertFailsWith<IllegalArgumentException> { EmulatorConfig(File("fake.gb"), scale = 0) }
    }

    @Test
    fun `EmulatorConfig rejects scale above 8`() {
        assertFailsWith<IllegalArgumentException> { EmulatorConfig(File("fake.gb"), scale = 9) }
    }

    @Test
    fun `LogLevel enum has all required values`() {
        val values = LogLevel.values()
        assertTrue(values.contains(LogLevel.GAME), "LogLevel.GAME must exist")
        assertTrue(values.contains(LogLevel.EMU), "LogLevel.EMU must exist")
        assertTrue(values.contains(LogLevel.WARN), "LogLevel.WARN must exist")
        assertTrue(values.contains(LogLevel.ERROR), "LogLevel.ERROR must exist")
    }

    @Test
    fun `DebugLogEntry formatted produces correct output with source map info`() {
        val entry =
            DebugLogEntry(
                timestampMs = 2341L,
                level = LogLevel.GAME,
                message = "Score: 10",
                kotlinFile = "ScriptBuilder.kt",
                kotlinLine = 45,
                context = "gameplay/frame",
            )
        val formatted = entry.formatted()
        assertTrue(formatted.startsWith("[00:02.341]"), "Timestamp format should be [MM:SS.mmm]")
        assertTrue(formatted.contains("ScriptBuilder.kt:45"), "Should include kotlin file:line")
        assertTrue(formatted.contains("(gameplay/frame)"), "Should include context")
        assertTrue(formatted.contains("Score: 10"), "Should include message")
        assertTrue(formatted.endsWith("\n"), "Should end with newline")
    }

    @Test
    fun `DebugLogEntry formatted without source map omits file and line`() {
        val entry =
            DebugLogEntry(
                timestampMs = 2341L,
                level = LogLevel.EMU,
                message = "ROM bank 3 switched",
            )
        val formatted = entry.formatted()
        assertTrue(formatted.startsWith("[00:02.341]"), "Timestamp format should be [MM:SS.mmm]")
        assertFalse(formatted.contains(".kt:"), "Should not include kotlin file when null")
        assertTrue(formatted.contains("ROM bank 3 switched"), "Should include message")
        assertTrue(formatted.endsWith("\n"), "Should end with newline")
    }

    @Test
    fun `DebugLogEntry formatted handles minute boundary correctly`() {
        val entry =
            DebugLogEntry(
                timestampMs = 90_500L, // 1 minute 30.500 seconds
                level = LogLevel.WARN,
                message = "test",
            )
        val formatted = entry.formatted()
        assertTrue(
            formatted.startsWith("[01:30.500]"),
            "Should format 90500ms as [01:30.500], got: $formatted",
        )
    }

    // ── 2. Pre-start state tests (no ROM needed) ──────────────────────────────

    @Test
    fun `isRunning returns false before start`() {
        val romFile = File("fake.gb") // Does not need to exist for this test
        val config = EmulatorConfig(romFile, headless = true)
        val emu = CoffeeGbEmulator(config)
        emulator = emu
        assertFalse(emu.isRunning(), "isRunning() should be false before start()")
    }

    @Test
    fun `isHeadless matches config`() {
        val romFile = File("fake.gb")
        val headlessEmu = CoffeeGbEmulator(EmulatorConfig(romFile, headless = true))
        emulator = headlessEmu
        assertTrue(headlessEmu.isHeadless, "isHeadless should reflect config.headless = true")

        val displayEmu = CoffeeGbEmulator(EmulatorConfig(romFile, headless = false))
        assertFalse(displayEmu.isHeadless, "isHeadless should reflect config.headless = false")
    }

    @Test
    fun `getFrameBuffer returns array of correct size before start`() {
        val romFile = File("fake.gb")
        val emu = CoffeeGbEmulator(EmulatorConfig(romFile, headless = true))
        emulator = emu
        val buf = emu.getFrameBuffer()
        assertEquals(160 * 144, buf.size, "Frame buffer must be exactly 160*144 = 23040 pixels")
    }

    @Test
    fun `getDebugLog returns empty list initially`() {
        val romFile = File("fake.gb")
        val emu = CoffeeGbEmulator(EmulatorConfig(romFile, headless = true))
        emulator = emu
        assertTrue(
            emu.getDebugLog().isEmpty(),
            "Debug log should be empty before any entries are added",
        )
    }

    @Test
    fun `getMemory throws when emulator not started`() {
        val romFile = File("fake.gb")
        val emu = CoffeeGbEmulator(EmulatorConfig(romFile, headless = true))
        emulator = emu
        assertThrows<IllegalStateException> { emu.getMemory() }
    }

    @Test
    fun `stepFrame throws when emulator not paused`() {
        val romFile = File("fake.gb")
        val emu = CoffeeGbEmulator(EmulatorConfig(romFile, headless = true))
        emulator = emu
        assertThrows<IllegalStateException> { emu.stepFrame() }
    }

    // ── 3. Debug log bounded size test (no ROM needed) ────────────────────────

    @Test
    fun `debug log never exceeds maxLogEntries`() {
        val romFile = File("fake.gb")
        val config = EmulatorConfig(romFile, headless = true, maxLogEntries = 50)
        val emu = CoffeeGbEmulator(config)
        emulator = emu

        val entry = DebugLogEntry(timestampMs = 0L, level = LogLevel.GAME, message = "test")
        repeat(100) { emu.addDebugEntry(entry) }

        val log = emu.getDebugLog()
        assertTrue(log.size <= 50, "Debug log should not exceed maxLogEntries=50, got ${log.size}")
    }

    @Test
    fun `addDebugEntry invokes onDebugEntry callback`() {
        val romFile = File("fake.gb")
        val emu = CoffeeGbEmulator(EmulatorConfig(romFile, headless = true))
        emulator = emu

        var callbackCount = 0
        emu.onDebugEntry = { callbackCount++ }

        val entry = DebugLogEntry(timestampMs = 0L, level = LogLevel.EMU, message = "test")
        repeat(5) { emu.addDebugEntry(entry) }

        assertEquals(5, callbackCount, "onDebugEntry callback should fire for each entry added")
    }

    @Test
    fun `addDebugEntry writes to log file when configured`() {
        val logFile = tempDir.resolve("test.log").toFile()
        val config = EmulatorConfig(File("fake.gb"), headless = true, logFile = logFile)
        val emu = CoffeeGbEmulator(config)
        emulator = emu

        val entry = DebugLogEntry(timestampMs = 1000L, level = LogLevel.GAME, message = "Hello log")
        emu.addDebugEntry(entry)
        emu.stop() // Flushes and closes the writer

        val content = logFile.readText()
        assertTrue(content.contains("Hello log"), "Log file should contain the written entry")
    }

    // ── 4. Speed multiplier test (no ROM needed) ──────────────────────────────

    @Test
    fun `default speed multiplier is 1_0f via setSpeed round-trip`() {
        // We test indirectly by observing the emulator does not crash with default speed.
        // The actual multiplier field is internal; we verify setSpeed does not throw.
        val romFile = File("fake.gb")
        val emu = CoffeeGbEmulator(EmulatorConfig(romFile, headless = true))
        emulator = emu
        // setSpeed should accept valid float values without throwing
        emu.setSpeed(1.0f)
        emu.setSpeed(2.0f)
        emu.setSpeed(4.0f)
        emu.setSpeed(0.5f)
    }

    @Test
    fun `setSpeed rejects zero multiplier`() {
        val emu = CoffeeGbEmulator(EmulatorConfig(File("fake.gb"), headless = true))
        emulator = emu
        assertFailsWith<IllegalArgumentException> { emu.setSpeed(0f) }
    }

    @Test
    fun `setSpeed rejects negative multiplier`() {
        val emu = CoffeeGbEmulator(EmulatorConfig(File("fake.gb"), headless = true))
        emulator = emu
        assertFailsWith<IllegalArgumentException> { emu.setSpeed(-1f) }
    }

    // ── 5. Lifecycle tests with minimal ROM ───────────────────────────────────

    private fun createMinimalRomFile(): File = TestRomFactory.createMinimalRom(tempDir)

    @Test
    fun `start and stop lifecycle completes without error`() {
        val romFile = createMinimalRomFile()
        val emu = CoffeeGbEmulator(EmulatorConfig(romFile, headless = true))
        emulator = emu

        emu.start()
        assertTrue(emu.isRunning(), "isRunning() should be true after start()")

        emu.stop()
        assertFalse(emu.isRunning(), "isRunning() should be false after stop()")
    }

    @Test
    fun `start is idempotent — calling start twice does not crash`() {
        val romFile = createMinimalRomFile()
        val emu = CoffeeGbEmulator(EmulatorConfig(romFile, headless = true))
        emulator = emu

        emu.start()
        emu.start() // Second call should be a no-op
        assertTrue(emu.isRunning())
        emu.stop()
    }

    @Test
    fun `stop on non-started emulator does not crash`() {
        val romFile = File("fake.gb")
        val emu = CoffeeGbEmulator(EmulatorConfig(romFile, headless = true))
        emulator = emu
        // Should complete without throwing even though the emulator was never started
        emu.stop()
        assertFalse(emu.isRunning())
    }

    @Test
    fun `emulator thread is a daemon thread`() {
        val romFile = createMinimalRomFile()
        val emu = CoffeeGbEmulator(EmulatorConfig(romFile, headless = true))
        emulator = emu

        var wasDaemon: Boolean? = null
        emu.onFrameReady = { _ -> wasDaemon = Thread.currentThread().isDaemon }

        emu.start()
        // Wait briefly for the emulator to run at least one frame
        Thread.sleep(500L)

        emu.stop()

        assertNotNull(
            wasDaemon,
            "onFrameReady should have been called after emulator ran for 500ms",
        )
        assertTrue(
            wasDaemon == true,
            "The emulator thread must be a daemon thread (isDaemon = true)",
        )
    }

    @Test
    fun `getFrameBuffer returns correct size after running`() {
        val romFile = createMinimalRomFile()
        val emu = CoffeeGbEmulator(EmulatorConfig(romFile, headless = true))
        emulator = emu

        emu.start()
        Thread.sleep(200L)
        emu.stop()

        val buf = emu.getFrameBuffer()
        assertEquals(160 * 144, buf.size, "Frame buffer size must always be 160*144 = 23040")
    }

    @Test
    fun `onFrameReady callback fires during normal execution`() {
        val romFile = createMinimalRomFile()
        val emu = CoffeeGbEmulator(EmulatorConfig(romFile, headless = true))
        emulator = emu

        var frameCount = 0
        emu.onFrameReady = { _ -> frameCount++ }

        emu.start()
        Thread.sleep(300L) // ~18 frames at 59.7 FPS
        emu.stop()

        assertTrue(frameCount > 0, "onFrameReady should have fired at least once, got $frameCount")
    }

    @Test
    fun `pause and resume stop and restart frame progression`() {
        val romFile = createMinimalRomFile()
        val emu = CoffeeGbEmulator(EmulatorConfig(romFile, headless = true))
        emulator = emu

        emu.start()
        Thread.sleep(100L) // Let it run briefly

        emu.pause()
        var frameCountWhilePaused = 0
        emu.onFrameReady = { _ -> frameCountWhilePaused++ }
        Thread.sleep(100L) // No frames should complete while paused

        emu.onFrameReady = null
        emu.resume()
        Thread.sleep(100L)
        emu.stop()

        assertEquals(0, frameCountWhilePaused, "No frames should complete while emulator is paused")
    }

    @Test
    fun `stepFrame invokes onTick callback at least once`() {
        val romFile = createMinimalRomFile()
        val emu = CoffeeGbEmulator(EmulatorConfig(romFile, headless = true))
        emulator = emu

        var tickCount = 0
        emu.onTick = { tickCount++ }

        emu.start()
        emu.pause()
        Thread.sleep(50L) // Ensure the loop has entered paused state

        val ticksBefore = tickCount
        emu.stepFrame()
        val ticksAfter = tickCount

        emu.stop()

        // stepFrame runs ~70224 ticks (one full frame). The count should increase.
        assertTrue(
            ticksAfter > ticksBefore,
            "stepFrame() should invoke onTick at least once (got ${ticksAfter - ticksBefore} additional ticks)",
        )
    }

    @Test
    fun `stepFrame throws when emulator not paused but started`() {
        val romFile = createMinimalRomFile()
        val emu = CoffeeGbEmulator(EmulatorConfig(romFile, headless = true))
        emulator = emu

        emu.start()
        // Not paused — stepFrame should reject

        assertThrows<IllegalStateException> { emu.stepFrame() }

        emu.stop()
    }
}
