/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.breakout

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.test.SimulationContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scenario-based game logic tests for the Breakout v2 game.
 *
 * Tests run on JVM via SimulationContext — no GBDK or ROM required.
 *
 * Scenarios:
 * 1. Ball bounces off paddle — AABB collision: ballDy reverses when ball overlaps paddle hitbox
 * 2. Score increments when ball hits brick zone (ball.y in 24..47, ball.x in 40..119)
 * 3. Ball resets and lives decrement when ball exits bottom (ball.y > 144)
 */
class BreakoutGameTest {

    companion object {
        /** Build GameIR once for all tests in this class (shared fixture). */
        private val ir: GameIR = breakout.build()
    }

    // =========================================================================
    // Scenario 1: Ball bounces off paddle (AABB collision)
    // =========================================================================

    @Test
    fun `ball bounces off paddle - ballDy reverses to negative when ball overlaps paddle AABB`() {
        val sim = SimulationContext(ir)
        sim.enterScene("game")
        sim.enableTracing()

        // Paddle at initial position (72, 132), hitbox (0,0,24,8)
        sim.setVar("paddle.x", 72)
        sim.setVar("paddle.y", 132)

        // Position ball within AABB overlap range:
        // AABB fires when: ball.x < paddle.x + 24 (96) AND ball.x + 8 > paddle.x (72)
        //                  AND ball.y < paddle.y + 8 (140) AND ball.y + 8 > paddle.y (132)
        // ball.x = 80 satisfies 64 < 80 < 96; ball.y = 130 satisfies 124 < 130 < 140
        sim.setVar("ball.x", 80) // within paddle x AABB: 64..95
        sim.setVar("ball.y", 130) // within paddle y AABB: 125..139
        sim.setVar("ballDy", 1) // moving downward
        sim.setVar("ballDx", 0) // no horizontal movement to keep position stable

        // Advance one frame — AABB collision condition fires
        // ball.collides(paddle): ball.x < 96 && ball.x+8 > 72 && ball.y < 140 && ball.y+8 > 132 →
        // true
        sim.advanceFrames(1)

        // After bounce: ballDy should be -1 (moving upward)
        sim.assertVar("ballDy", -1)

        // Trace should contain entries from this test
        assertTrue(
            sim.getTraceLog().isNotEmpty(),
            "Trace log should have entries after paddle bounce",
        )
    }

    // =========================================================================
    // Scenario 2: Score increments when ball hits brick zone
    // =========================================================================

    @Test
    fun `score increments and bricksLeft decrements when ball in brick zone`() {
        val sim = SimulationContext(ir)
        sim.enterScene("game")

        // Initial state: score=0, bricksLeft=30 (set in enter block)
        assertEquals(0, sim.getVar("score"))
        assertEquals(30, sim.getVar("bricksLeft"))

        // Position ball in brick zone: y in 24..47, x in 40..119
        // Set ballDy = -1 so after movement ball.y = 35 (36 - 1), still in zone
        // bc = (80 - 40) >> 3 = 5, brow = (35 - 24) >> 3 = 1, bidx = 1*10+5 = 15
        sim.setVar("ball.x", 80)
        sim.setVar("ball.y", 36) // y in 24..47 brick zone
        sim.setVar("ballDy", -1) // moving upward into bricks
        sim.setVar("ballDx", 0) // no horizontal movement

        // Advance one frame — brick hit condition fires:
        // ball.y in [24,48) AND ball.x in [40,120) → bricks[15]==1 → hit
        sim.advanceFrames(1)

        // Score should have incremented by 10
        sim.assertVar("score", 10)

        // bricksLeft should have decremented by 1
        sim.assertVar("bricksLeft", 29)

        // ballDy should have reversed (ball now moving downward after brick hit)
        // ballDy *= -1: -1 * -1 = 1
        sim.assertVar("ballDy", 1)
    }

    // =========================================================================
    // Scenario 3: Ball resets when it exits below the screen
    // =========================================================================

    @Test
    fun `lives decrement and ball resets when ball exits bottom of screen`() {
        val sim = SimulationContext(ir)
        sim.enterScene("game")

        // Initial lives = 3
        assertEquals(3, sim.getVar("lives"))

        // Position ball below paddle (past y = 144)
        sim.setVar("ball.x", 80)
        sim.setVar("ball.y", 148) // above 144 → "lose a life" condition (ball.y isAbove 144)
        sim.setVar("ballDy", 1) // still moving downward

        // Advance one frame — "ball below paddle" condition fires
        // ball.y > 144 → lives -= 1, ball resets to (80, 120), ballDy = -1
        sim.advanceFrames(1)

        // Lives should be decremented
        sim.assertVar("lives", 2)

        // Ball should reset to center (80, 120) via ball.moveTo(80, 120) in game code
        assertEquals(80, sim.getVar("ball.x"))
        assertEquals(120, sim.getVar("ball.y"))

        // ballDy should be reset to -1 (moving upward again)
        sim.assertVar("ballDy", -1)
    }
}
