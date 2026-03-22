/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.shmup

import io.github.gbkt.emulator.agent.AgentDebugSession
import io.github.gbkt.emulator.agent.AgentSessionConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Headless smoke tests for the Shmup ROM using AgentDebugSession.
 *
 * These tests require the ROM to have been built first: `./gradlew :gbkt-examples:shmup:buildRom`
 *
 * If the ROM file is absent, all tests are skipped automatically.
 *
 * Smoke test goals:
 * - ROM boots and runs 600 frames without ERROR-level debug log entries
 * - Screenshot captured at frame 300 for visual baseline (stored in build/gbkt/screenshots)
 * - Key variables accessible via VariableInspector: score is defined (>= 0), lives is defined
 */
class ShmupEmulatorTest {

    companion object {
        private val ROM_FILE = File("build/gbkt/output/shmup.gb")
        private val SYM_FILE = File("build/gbkt/output/shmup.sym")
        private val SCREENSHOT_DIR = File("build/gbkt/screenshots")
    }

    /**
     * Smoke test: ROM boots and runs 600 frames without ERROR log entries. Captures a screenshot at
     * frame 300 for manual baseline verification.
     */
    @Test
    fun `shmup ROM runs 600 frames without errors`() {
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
            )

        AgentDebugSession(config).use { session ->
            session.start()

            // Run to frame 300 and capture visual baseline
            session.runFrames(300)
            session.captureScreenshot("shmup_frame_300")

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
     * Variable inspection: score and lives variables are accessible and start at valid values. Runs
     * 60 frames (about 1 second) before reading.
     */
    @Test
    fun `shmup score and lives variables are accessible`() {
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
                watchVariables = listOf("score", "lives"),
            )

        AgentDebugSession(config).use { session ->
            session.start()
            session.runFrames(60)

            val score = session.readVariable("score")
            val lives = session.readVariable("lives")

            // Both variables should be resolvable and valid u8 values (0-255)
            assertTrue(score != null, "Expected 'score' variable to be readable from sym file")
            assertTrue(score in 0..255, "Expected score in 0..255, got $score")

            assertTrue(lives != null, "Expected 'lives' variable to be readable from sym file")
            assertTrue(lives in 0..255, "Expected lives in 0..255, got $lives")
        }
    }
}
