/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.integration

import io.github.gbkt.emulator.CoffeeGbEmulator
import io.github.gbkt.emulator.EmulatorConfig
import io.github.gbkt.emulator.EmulatorSession
import io.github.gbkt.emulator.GbEmulator
import io.github.gbkt.emulator.LogLevel
import io.github.gbkt.emulator.debug.DebugLogEntry
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Integration tests that validate the full emulator API contract and lifecycle.
 *
 * These tests verify that:
 * 1. [GbEmulator] interface exposes all required control methods
 * 2. [EmulatorConfig] defaults match the documented API
 * 3. [DebugLogEntry.formatted] produces correctly structured output
 * 4. [EmulatorSession] can be constructed with a headless config
 * 5. [LogLevel] has all required severity levels
 * 6. Real ROM boot (60 frames) works end-to-end when a built ROM is available
 *
 * The ROM boot test uses [Tag] "integration" and [Assumptions.assumeTrue] so it only runs when a
 * built Pong ROM is available. CI should build example ROMs before running integration tests.
 */
class EmulatorIntegrationTest {

    @Test
    fun `GbEmulator interface has all required methods`() {
        // Verify the interface contract matches the CONTEXT.md spec
        val methods = GbEmulator::class.java.methods.map { it.name }.toSet()
        assertContains(methods, "start")
        assertContains(methods, "stop")
        assertContains(methods, "pause")
        assertContains(methods, "resume")
        assertContains(methods, "stepFrame")
        assertContains(methods, "setSpeed")
        assertContains(methods, "getFrameBuffer")
        assertContains(methods, "getMemory")
        assertContains(methods, "getDebugLog")
        assertContains(methods, "isRunning")
        assertContains(methods, "isPaused")
    }

    @Test
    fun `EmulatorConfig defaults are correct`() {
        val config = EmulatorConfig(romFile = File("test.gb"))
        assertEquals(false, config.headless)
        assertEquals(4, config.scale)
        assertNull(config.sourceMapsDir)
        assertNull(config.logFile)
        assertEquals(10_000, config.maxLogEntries)
    }

    @Test
    fun `DebugLogEntry formats correctly with all fields`() {
        val entry =
            DebugLogEntry(
                timestampMs = 2341, // 0 min, 2 sec, 341 ms
                level = LogLevel.GAME,
                message = "Score: 10",
                kotlinFile = "ScriptBuilder.kt",
                kotlinLine = 45,
                context = "gameplay/frame",
            )
        val formatted = entry.formatted()
        assertTrue(
            formatted.contains("[00:02.341]"),
            "Expected timestamp [00:02.341] in: $formatted",
        )
        assertTrue(
            formatted.contains("ScriptBuilder.kt:45"),
            "Expected source location in: $formatted",
        )
        assertTrue(formatted.contains("(gameplay/frame)"), "Expected context in: $formatted")
        assertTrue(formatted.contains("Score: 10"), "Expected message in: $formatted")
    }

    @Test
    fun `DebugLogEntry formats correctly without source info`() {
        val entry =
            DebugLogEntry(timestampMs = 1000, level = LogLevel.EMU, message = "Frame rendered")
        val formatted = entry.formatted()
        assertTrue(
            formatted.contains("[00:01.000]"),
            "Expected timestamp [00:01.000] in: $formatted",
        )
        assertTrue(formatted.contains("Frame rendered"), "Expected message in: $formatted")
        assertFalse(
            formatted.contains("null"),
            "Formatted entry should not contain the word 'null': $formatted",
        )
    }

    @Test
    fun `LogLevel enum has all required levels`() {
        val levels = LogLevel.entries.map { it.name }.toSet()
        assertEquals(setOf("GAME", "EMU", "WARN", "ERROR"), levels)
    }

    @Test
    fun `EmulatorSession can be created with headless config`() {
        val config = EmulatorConfig(romFile = File("nonexistent.gb"), headless = true)
        // Session creation should not throw (launch() would throw on missing ROM)
        val session = EmulatorSession(config)
        assertNotNull(session)
        // Verify emulator field is null before launch
        assertNull(session.emulator, "Emulator should be null before launch()")
    }

    @Test
    @Tag("integration")
    fun `real ROM boots and runs 60 frames in headless mode`() {
        // Use the Pong example ROM if available from build output
        val romFile = File("../gbkt-examples/pong/build/gbkt/output/pong.gb")
        Assumptions.assumeTrue(romFile.exists(), "Pong ROM not built — skipping integration test")

        val config =
            EmulatorConfig(
                romFile = romFile,
                headless = true,
                logFile = File.createTempFile("gbkt-test-", ".log"),
            )
        val emulator = CoffeeGbEmulator(config)
        emulator.start()

        // Pause before stepping frames (stepFrame requires paused state)
        emulator.pause()

        // Run 60 frames (approx 1 second of gameplay)
        repeat(60) { emulator.stepFrame() }

        // Verify emulator is still paused (not crashed)
        assertTrue(emulator.isRunning(), "Emulator thread should still be alive after 60 frames")

        // Verify frame buffer has been populated
        val fb = emulator.getFrameBuffer()
        assertEquals(160 * 144, fb.size, "Frame buffer should be 160x144 pixels")

        emulator.stop()
        assertFalse(emulator.isRunning(), "Emulator should be stopped after stop()")

        // Cleanup
        config.logFile?.delete()
    }
}
