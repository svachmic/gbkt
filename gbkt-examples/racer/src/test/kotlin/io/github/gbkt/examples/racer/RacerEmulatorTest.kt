/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.racer

import io.github.gbkt.emulator.agent.AgentDebugSession
import io.github.gbkt.emulator.agent.AgentSessionConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Headless smoke tests for the Racer ROM using AgentDebugSession.
 *
 * These tests require the ROM to have been built first: `./gradlew :gbkt-examples:racer:buildRom`
 *
 * If the ROM file is absent, all tests are skipped automatically.
 *
 * The Racer is a GBC_COMPATIBLE game. The emulator runs in DMG mode by default; tests use
 * gbcMode=true to exercise the GBC code path.
 *
 * Smoke test goals:
 * - ROM boots and runs 600 frames without ERROR-level debug log entries
 * - Screenshot captured at frame 300 for visual baseline (stored in build/gbkt/screenshots)
 * - Key variables accessible via VariableInspector: lap is defined (>= 0)
 */
class RacerEmulatorTest {

    companion object {
        private val ROM_FILE = File("build/gbkt/output/racer.gb")
        private val SYM_FILE = File("build/gbkt/output/racer.sym")
        private val SCREENSHOT_DIR = File("build/gbkt/screenshots")
    }

    /**
     * Smoke test: ROM boots and runs 600 frames without ERROR log entries. Captures a screenshot at
     * frame 300 for manual baseline verification. Uses gbcMode=true since Racer is GBC_COMPATIBLE.
     */
    @Test
    fun `racer ROM runs 600 frames without errors`() {
        if (!ROM_FILE.exists()) {
            println("SKIP: ROM not found at ${ROM_FILE.absolutePath} — run buildRom first")
            return
        }

        SCREENSHOT_DIR.mkdirs()

        val config =
            AgentSessionConfig(
                romFile = ROM_FILE,
                symFile = if (SYM_FILE.exists()) SYM_FILE else null,
                screenshotDir = SCREENSHOT_DIR,
                gbcMode = true, // Racer is GBC_COMPATIBLE
            )

        AgentDebugSession(config).use { session ->
            session.start()

            // Run to frame 300 and capture visual baseline
            session.runFrames(300)
            session.captureScreenshot("racer_frame_300")

            // Run remaining 300 frames (total 600)
            session.runFrames(300)

            // No ERROR entries in debug log
            val errorEntries = session.getDebugLog().filter { it.level.name == "ERROR" }
            assertTrue(
                errorEntries.isEmpty(),
                "Expected no ERROR log entries after 600 frames, but found: " +
                    errorEntries.joinToString("\n") { "  ${it.level}: ${it.message}" },
            )

            // Frame count advanced correctly
            assertTrue(
                session.frameCount >= 600,
                "Expected frameCount >= 600, got ${session.frameCount}",
            )
        }
    }

    /**
     * Variable inspection: lap variable is accessible and starts at a valid value. Runs 60 frames
     * (about 1 second) before reading.
     */
    @Test
    fun `racer lap variable is accessible`() {
        if (!ROM_FILE.exists()) {
            println("SKIP: ROM not found at ${ROM_FILE.absolutePath} — run buildRom first")
            return
        }
        if (!SYM_FILE.exists()) {
            println("SKIP: sym file not found at ${SYM_FILE.absolutePath} — run buildRom first")
            return
        }

        val config =
            AgentSessionConfig(
                romFile = ROM_FILE,
                symFile = SYM_FILE,
                screenshotDir = SCREENSHOT_DIR,
                gbcMode = true,
                watchVariables = listOf("lap", "raceTime"),
            )

        AgentDebugSession(config).use { session ->
            session.start()
            session.runFrames(60)

            val lap = session.readVariable("lap")
            val raceTime = session.readVariable("raceTime")

            // Both variables should be resolvable and valid u8 values (0-255)
            assertTrue(lap != null, "Expected 'lap' variable to be readable from sym file")
            assertTrue(lap in 0..255, "Expected lap in 0..255, got $lap")

            assertTrue(
                raceTime != null,
                "Expected 'raceTime' variable to be readable from sym file",
            )
            assertTrue(raceTime in 0..255, "Expected raceTime in 0..255, got $raceTime")
        }
    }
}
