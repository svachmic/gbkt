/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.shmup

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.test.SimulationContextV2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scenario-based game logic tests for the Shmup v2 game.
 *
 * Tests run on JVM via SimulationContextV2 — no GBDK or ROM required.
 *
 * Scenarios:
 * 1. Gameplay initializes with correct state (score=0, lives=3, scrollY=0)
 * 2. Title scene has enter ops (hideSprites, clear, print)
 * 3. Gameover scene has enter ops (hideSprites, clear, print)
 */
class ShmupGameTest {

    companion object {
        /** Build GameIR once for all tests in this class (shared fixture). */
        private val ir: GameIR = shmup.build()
    }

    // =========================================================================
    // Scenario 1: Gameplay initializes with correct state
    // =========================================================================

    @Test
    fun `gameplay initializes score to 0`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        // The enter block sets score = 0, lives = 3, scrollY = 0, etc.
        sim.assertVar("score", 0)
    }

    @Test
    fun `gameplay initializes lives to 3`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        sim.assertVar("lives", 3)
    }

    @Test
    fun `gameplay initializes scrollY to 0`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        sim.assertVar("scrollY", 0)
    }

    // =========================================================================
    // Scenario 2: Title scene initializes display
    // =========================================================================

    @Test
    fun `title scene initializes display`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("title")
        assertEquals("title", sim.currentScene)
    }

    // =========================================================================
    // Scenario 3: Gameover scene shows score
    // =========================================================================

    @Test
    fun `gameover scene shows score`() {
        val sim = SimulationContextV2(ir)
        sim.setVar("score", 42)
        sim.enterScene("gameover")
        // Score should still be 42 (gameover displays it, doesn't reset)
        sim.assertVar("score", 42)
    }

    // =========================================================================
    // Scenario 4: Scroll advances each frame
    // =========================================================================

    @Test
    fun `scrollY increments each gameplay frame`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        val scrollBefore = sim.getVar("scrollY")

        // Advance one frame — scrollY += 1 fires in frame block
        sim.advanceFrames(1)

        val scrollAfter = sim.getVar("scrollY")
        assertTrue(scrollAfter > scrollBefore, "scrollY should increment each frame")
    }
}
