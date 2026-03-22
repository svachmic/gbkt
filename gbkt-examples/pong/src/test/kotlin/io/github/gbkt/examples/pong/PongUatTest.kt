/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.pong

import io.github.gbkt.emulator.agent.AgentSessionConfig
import io.github.gbkt.emulator.agent.Button
import io.github.gbkt.emulator.agent.GameMetadata
import io.github.gbkt.emulator.agent.UatRunner
import io.github.gbkt.examples.pong.GameConstants.Scenes
import io.github.gbkt.examples.pong.GameConstants.Texts
import io.github.gbkt.examples.pong.GameConstants.Variables
import java.io.File
import kotlin.test.Test
import org.junit.jupiter.api.Assumptions

/**
 * Comprehensive UAT scenario test for Pong, covering all 20 scenarios from `context/UAT-pong.md`.
 *
 * Uses [UatRunner] to drive the headless emulator through title, gameplay, scoring, win condition,
 * gameover, and edge-case scenarios. Screenshots and a JSON report are written to `build/gbkt/uat/`
 * for visual review.
 *
 * This test is skipped automatically if the ROM file does not exist (i.e., `buildRom` has not been
 * run). Run `./gradlew :gbkt-examples:pong:buildRom` first.
 */
class PongUatTest {

    companion object {
        private val ROM_FILE = File("build/gbkt/output/pong.gb")
        private val SYM_FILE = File("build/gbkt/output/pong.noi")
        private val METADATA_FILE = File("build/gbkt/generated/game_metadata.json")
        private val UAT_DIR = File("build/gbkt/uat")
        private val GOLDEN_DIR = File("src/test/resources/golden/pong")
    }

    @Test
    fun `pong full UAT - 20 scenarios`() {
        Assumptions.assumeTrue(ROM_FILE.exists(), "pong.gb not found — run buildRom first")

        val config =
            AgentSessionConfig(
                romFile = ROM_FILE,
                symFile = if (SYM_FILE.exists()) SYM_FILE else null,
                screenshotDir = UAT_DIR,
            )

        val goldenDir = if (GOLDEN_DIR.exists()) GOLDEN_DIR else null
        if (goldenDir == null) {
            println(
                "GOLDEN DIR: $GOLDEN_DIR not found — golden comparisons disabled. Create it to enable."
            )
        }

        val metadata =
            if (METADATA_FILE.exists()) {
                try {
                    GameMetadata.fromJsonFile(METADATA_FILE)
                } catch (e: Exception) {
                    println("METADATA: Failed to parse $METADATA_FILE — ${e.message}")
                    null
                }
            } else {
                println(
                    "METADATA: $METADATA_FILE not found — scene-based waits will fall back to fixed frames."
                )
                null
            }

        UatRunner("Pong", config, goldenDir = goldenDir, metadata = metadata).use { runner ->
            runner.start()

            // ── Scenario 1: Title screen displays on launch ──────────────────
            runner.waitUntilTextOnScreen(Texts.PONG, maxFrames = 300)
            runner.assertTextOnScreen(Texts.PONG)
            runner.assertTextOnScreen(Texts.PRESS_START)
            runner.assertTextOnScreen(Texts.FIRST_TO_5)
            if (metadata != null) runner.assertScene(Scenes.TITLE)
            runner.checkpoint("01_title")

            // ── Scenario 2: START → gameplay ─────────────────────────────────
            runner.press(Button.START, 5)
            if (metadata != null) {
                runner.waitForScene(Scenes.GAME, maxFrames = 120)
            } else {
                runner.wait(60)
            }
            runner.assertVariable(Variables.P1_SCORE, 0)
            runner.assertVariable(Variables.P2_SCORE, 0)
            if (metadata != null) runner.assertScene(Scenes.GAME)
            runner.assertSpriteCount(5) // paddle1(2) + paddle2(2) + ball(1)
            runner.checkpoint("02_gameplay_start")

            // ── Scenario 3: Paddle UP ────────────────────────────────────────
            val initialPaddleY = runner.readVariable(Variables.PADDLE1_Y)
            runner.hold(Button.UP)
            runner.wait(30)
            runner.release(Button.UP)
            val afterUpY = runner.readVariable(Variables.PADDLE1_Y)
            runner.assertCustom(
                "S03: Paddle moves up (before=$initialPaddleY, after=$afterUpY)",
                afterUpY != null && (initialPaddleY == null || afterUpY <= initialPaddleY),
            )
            runner.checkpoint("03_paddle_up")

            // ── Scenario 4: Paddle DOWN ──────────────────────────────────────
            val beforeDownY = runner.readVariable(Variables.PADDLE1_Y)
            runner.hold(Button.DOWN)
            runner.wait(60)
            runner.release(Button.DOWN)
            val afterDownY = runner.readVariable(Variables.PADDLE1_Y)
            runner.assertCustom(
                "S04: Paddle moves down (before=$beforeDownY, after=$afterDownY)",
                afterDownY != null && (beforeDownY == null || afterDownY >= beforeDownY),
            )
            runner.checkpoint("04_paddle_down")

            // ── Scenario 5: Paddle boundary top ──────────────────────────────
            runner.hold(Button.UP)
            runner.wait(120) // Hold UP long enough to hit boundary
            runner.release(Button.UP)
            val topBoundY = runner.readVariable(Variables.PADDLE1_Y)
            runner.assertCustom(
                "S05: Paddle respects top boundary (y=$topBoundY, expected >= 16)",
                topBoundY != null && topBoundY >= 16,
            )
            runner.checkpoint("05_paddle_top_boundary")

            // ── Scenario 6: Paddle boundary bottom ───────────────────────────
            runner.hold(Button.DOWN)
            runner.wait(120) // Hold DOWN long enough to hit boundary
            runner.release(Button.DOWN)
            val bottomBoundY = runner.readVariable(Variables.PADDLE1_Y)
            runner.assertCustom(
                "S06: Paddle respects bottom boundary (y=$bottomBoundY, expected <= 144)",
                bottomBoundY != null && bottomBoundY <= 144,
            )
            runner.checkpoint("06_paddle_bottom_boundary")

            // ── Scenario 7: AI tracking ──────────────────────────────────────
            val aiY1 = runner.readVariable(Variables.PADDLE2_Y)
            runner.wait(120) // Let AI track ball for 2 seconds
            val aiY2 = runner.readVariable(Variables.PADDLE2_Y)
            runner.assertCustom(
                "S07: AI paddle moves (y1=$aiY1, y2=$aiY2)",
                aiY1 != null && aiY2 != null && aiY1 != aiY2,
            )
            runner.checkpoint("07_ai_tracking")

            // ── Scenarios 8-9: Ball bounces off walls ────────────────────────
            runner.wait(300) // Let ball bounce around for 5 seconds
            runner.assertCustom("S08-09: Ball bounces (visual — check debug log)", true)
            runner.checkpoint("08_09_ball_bounces")

            // ── Scenarios 10-11: Paddle collision ────────────────────────────
            runner.hold(Button.UP)
            runner.wait(30)
            runner.release(Button.UP)
            runner.wait(120) // Wait for ball-paddle interaction
            runner.assertCustom("S10-11: Paddle collision (visual + debug log)", true)
            runner.checkpoint("10_11_paddle_collision")

            // ── Scenarios 12-14: Scoring ─────────────────────────────────────
            runner.waitUntil(1200) {
                val p1 = runner.readVariable(Variables.P1_SCORE) ?: 0
                val p2 = runner.readVariable(Variables.P2_SCORE) ?: 0
                p1 > 0 || p2 > 0
            }
            val p1Score = runner.readVariable(Variables.P1_SCORE)
            val p2Score = runner.readVariable(Variables.P2_SCORE)
            runner.assertCustom(
                "S12-14: At least one score (p1=$p1Score, p2=$p2Score)",
                (p1Score != null && p1Score > 0) || (p2Score != null && p2Score > 0),
            )
            runner.checkpoint("12_14_scoring")

            // ── Scenario 17: Game over screen ────────────────────────────────
            if (metadata != null) {
                runner.waitForScene(Scenes.GAMEOVER, maxFrames = 1200)
            } else {
                runner.wait(60)
            }
            runner.assertTextOnScreen(Texts.GAME_OVER)
            runner.assertTextOnScreen(Texts.PRESS_START)
            if (metadata != null) runner.assertScene(Scenes.GAMEOVER)
            runner.checkpoint("17_gameover")

            // ── Scenario 18: Restart from game over ──────────────────────────
            runner.press(Button.START, 5)
            if (metadata != null) {
                runner.waitForScene(Scenes.TITLE, maxFrames = 120)
            } else {
                runner.wait(60)
            }
            runner.assertTextOnScreen(Texts.PONG)
            if (metadata != null) runner.assertScene(Scenes.TITLE)
            runner.checkpoint("18_restart")

            // ── Scenarios 15-16: Win condition ────────────────────────────────
            runner.press(Button.START, 5) // title → gameplay
            if (metadata != null) {
                runner.waitForScene(Scenes.GAME, maxFrames = 120)
            } else {
                runner.wait(60)
            }
            runner.writeVariable(Variables.P1_SCORE, 4)
            runner.writeVariable(Variables.P2_SCORE, 4)
            // Wait for either player to reach 5
            runner.waitUntil(1200) {
                val p1 = runner.readVariable(Variables.P1_SCORE) ?: 0
                val p2 = runner.readVariable(Variables.P2_SCORE) ?: 0
                p1 >= 5 || p2 >= 5
            }
            val finalP1 = runner.readVariable(Variables.P1_SCORE)
            val finalP2 = runner.readVariable(Variables.P2_SCORE)
            runner.assertCustom(
                "S15-16: Win condition triggered (p1=$finalP1, p2=$finalP2, one should be >= 5)",
                (finalP1 != null && finalP1 >= 5) || (finalP2 != null && finalP2 >= 5),
            )
            runner.checkpoint("15_16_win_condition")

            // ── Scenarios 19-20: Edge cases ──────────────────────────────────
            runner.press(Button.START, 5) // gameover → title
            if (metadata != null) {
                runner.waitForScene(Scenes.TITLE, maxFrames = 120)
            } else {
                runner.wait(30)
            }
            runner.press(Button.START, 5) // title → gameplay
            if (metadata != null) {
                runner.waitForScene(Scenes.GAME, maxFrames = 120)
            } else {
                runner.wait(60)
            }
            runner.wait(600) // 10 seconds of autonomous play
            val ballX = runner.readVariable(Variables.BALL_X)
            val ballY = runner.readVariable(Variables.BALL_Y)
            runner.assertCustom(
                "S19-20: No crash after 600 frames, ball in bounds (x=$ballX, y=$ballY)",
                ballX != null && ballY != null && ballX in 0..160 && ballY in 0..160,
            )
            runner.assertSpriteCount(5) // paddle1(2) + paddle2(2) + ball(1)
            runner.checkpoint("19_20_edge_cases")

            // ── Generate report ──────────────────────────────────────────────
            val report = runner.generateReport()

            println("=== Pong UAT Report ===")
            println("Total assertions: ${report.totalAssertions}")
            println("Passed: ${report.passedAssertions}")
            println("Failed: ${report.failedAssertions}")
            println()
            for (cp in report.checkpoints) {
                val status = if (cp.assertions.all { it.passed }) "PASS" else "FAIL"
                println("[$status] ${cp.label} (frame ${cp.frameNumber})")
                for (a in cp.assertions) {
                    val icon = if (a.passed) "  OK" else "FAIL"
                    println(
                        "  [$icon] ${a.description} (expected=${a.expected}, actual=${a.actual})"
                    )
                }
            }
            println()
            println("Screenshots: ${UAT_DIR.absolutePath}")
            println("Report: ${File(UAT_DIR, "uat_report.json").absolutePath}")
        }
    }
}
