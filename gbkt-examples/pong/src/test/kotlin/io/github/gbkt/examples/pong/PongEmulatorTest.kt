/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.pong

import io.github.gbkt.emulator.LogLevel
import io.github.gbkt.emulator.agent.AgentDebugSession
import io.github.gbkt.emulator.agent.AgentSessionConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Headless smoke test for the Pong ROM using Coffee-GB embedded emulator.
 *
 * Runs the ROM for 600 frames (10 seconds at 60fps) and asserts no ERROR-level log entries.
 * Tests are skipped automatically if the ROM file does not exist (i.e., buildRom has not been run).
 *
 * Run headless emulator test task via:
 *   ./gradlew :gbkt-examples:pong:emulatorTest
 *
 * Run this JUnit test via:
 *   ./gradlew :gbkt-examples:pong:test
 */
class PongEmulatorTest {

    companion object {
        /**
         * ROM file path — resolved relative to the module project directory.
         * The Gradle plugin writes the ROM to build/gbkt/output/<outputName>.gb.
         */
        private val ROM_FILE: File = File("build/gbkt/output/pong.gb")

        /** Sym file for variable inspection (produced by GBDK alongside the ROM). */
        private val SYM_FILE: File = File("build/gbkt/output/pong.noi")

        /** Number of frames to run during smoke test — 600 = 10 seconds at 60fps. */
        private const val SMOKE_FRAMES = 600

        /** Number of frames to run before reading score variable. */
        private const val SCORE_CHECK_FRAMES = 300
    }

    /**
     * Smoke test: run Pong for 600 frames, verify no ERROR entries in debug log.
     *
     * This test is skipped if the ROM file does not exist — it only runs after buildRom.
     */
    @Test
    fun `pong rom runs 600 frames without errors`() {
        if (!ROM_FILE.exists()) {
            println("SKIP: pong.gb not found at ${ROM_FILE.absolutePath} — run buildRom first")
            return
        }

        val config = AgentSessionConfig(
            romFile = ROM_FILE,
            symFile = if (SYM_FILE.exists()) SYM_FILE else null,
        )

        AgentDebugSession(config).use { session ->
            session.start()
            session.runFrames(SMOKE_FRAMES)

            val errors = session.getDebugLog().filter { it.level == LogLevel.ERROR }
            assertTrue(
                errors.isEmpty(),
                "Pong ROM produced ${errors.size} ERROR entries after $SMOKE_FRAMES frames:\n" +
                    errors.joinToString("\n") { it.formatted() },
            )
        }
    }

    /**
     * Variable smoke test: run 300 frames, verify p1Score is accessible (>= 0).
     *
     * Validates that the symbol table links to a readable WRAM address for the score variable.
     * This test is skipped if the ROM or sym file does not exist.
     */
    @Test
    fun `pong p1Score variable is readable after 300 frames`() {
        if (!ROM_FILE.exists() || !SYM_FILE.exists()) {
            println("SKIP: pong.gb or pong.noi not found — run buildRom first")
            return
        }

        val config = AgentSessionConfig(
            romFile = ROM_FILE,
            symFile = SYM_FILE,
            watchVariables = listOf("p1Score", "p2Score"),
        )

        AgentDebugSession(config).use { session ->
            session.start()
            session.runFrames(SCORE_CHECK_FRAMES)

            val p1Score = session.readVariable("p1Score")
            assertTrue(
                p1Score != null && p1Score >= 0,
                "p1Score should be readable and >= 0 after $SCORE_CHECK_FRAMES frames, got: $p1Score",
            )

            val p2Score = session.readVariable("p2Score")
            assertTrue(
                p2Score != null && p2Score >= 0,
                "p2Score should be readable and >= 0 after $SCORE_CHECK_FRAMES frames, got: $p2Score",
            )
        }
    }
}
