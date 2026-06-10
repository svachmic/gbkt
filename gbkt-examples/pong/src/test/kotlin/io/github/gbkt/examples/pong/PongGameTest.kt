/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.pong

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.test.SimulationContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scenario-based game logic tests for the Pong v2 game.
 *
 * Tests run on JVM via SimulationContext — no GBDK or ROM required.
 *
 * Scenarios:
 * 1. Ball bounces off top wall — ballDy reverses from -1 to 1 when ball.y < 16
 * 2. Ball exits right side — p1Score increments and ball resets to center
 * 3. Win condition — score reaching 5 triggers navigation to gameover scene
 */
class PongGameTest {

    companion object {
        /** Build GameIR once for all tests in this class (shared fixture). */
        private val ir: GameIR = pong.build()
    }

    // =========================================================================
    // Scenario 1: Ball bounces off top wall
    // =========================================================================

    @Test
    fun `ball bounces off top wall - ballDy reverses to positive when ball y below 16`() {
        val sim = SimulationContext(ir)
        sim.enterScene("game")

        // Set ball near top wall with upward velocity
        sim.setVar("ball.x", 80)
        sim.setVar("ball.y", 10) // below threshold of 16
        sim.setVar("ballDy", -1) // moving upward

        // Advance one frame — the bounce condition: ball.y isBelow 16 → ballDy = 1
        // Ball movement adds ballDy to ball.y first, but bounce check fires after
        // Since ball.y (10) + ballDy (-1) = 9 on next frame, and 9 < 16, bounce triggers
        sim.advanceFrames(1)

        // After bounce: ballDy should be 1 (positive = moving down)
        sim.assertVar("ballDy", 1)
    }

    @Test
    fun `ball bounces off bottom wall - ballDy reverses to negative when ball y above 148`() {
        val sim = SimulationContext(ir)
        sim.enterScene("game")

        // Set ball near bottom wall with downward velocity
        sim.setVar("ball.x", 80)
        sim.setVar("ball.y", 155) // above threshold of 148
        sim.setVar("ballDy", 1) // moving downward

        // Advance one frame — the bounce condition fires
        sim.advanceFrames(1)

        // After bounce: ballDy should be -1 (moving up)
        sim.assertVar("ballDy", -1)
    }

    // =========================================================================
    // Scenario 2: Scoring — ball exits right side, p1Score increments
    // =========================================================================

    @Test
    fun `p1Score increments when ball exits right side`() {
        val sim = SimulationContext(ir)
        sim.enterScene("game")
        sim.enableTracing()

        // Force ball to just past right boundary (> 156) to trigger p1 scoring
        sim.setVar("ball.x", 157) // above 156 → p1Score condition triggers
        sim.setVar("ballDx", 1) // moving right (will be reset anyway)

        val scoreBefore = sim.getVar("p1Score")

        // One frame: ball.x = 157 + 1 = 158 → isAbove 156 → p1Score += 1
        // Then also the scoring check: ball.x = 158 isAbove 156 → p1Score += 1
        // Actually the scoring check (ball exits at > 156) fires after movement
        sim.advanceFrames(1)

        val scoreAfter = sim.getVar("p1Score")
        assertTrue(scoreAfter > scoreBefore, "p1Score should increment when ball exits right side")

        // Ball should reset to center (80, 72)
        assertEquals(80, sim.getVar("ball.x"))
        assertEquals(72, sim.getVar("ball.y"))

        // Trace log should contain at least one entry (e.g., score update)
        assertTrue(sim.getTraceLog().isNotEmpty(), "Trace log should record state changes")
    }

    // =========================================================================
    // Scenario 3: Win condition triggers scene change to gameover
    // =========================================================================

    @Test
    fun `win condition navigates to gameover when p1Score reaches 5`() {
        val sim = SimulationContext(ir)
        sim.enterScene("game")

        // Set p1Score to 4 (just below win threshold of 5)
        sim.setVar("p1Score", 4)

        // Place ball to trigger p1 scoring (right side exit)
        sim.setVar("ball.x", 160) // far right, will trigger p1 score
        sim.setVar("ballDx", 1)

        // Advance one frame — ball exits right → p1Score becomes 5 → navigate to gameover
        sim.advanceFrames(1)

        // p1Score should now be 5
        sim.assertVar("p1Score", 5)

        // Scene should have changed to gameover
        assertEquals("gameover", sim.currentScene)
    }
}
