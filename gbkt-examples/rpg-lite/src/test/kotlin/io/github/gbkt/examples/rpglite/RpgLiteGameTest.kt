/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.rpglite

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.test.SimulationContextV2
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Scenario-based game logic tests for the RPG Lite game.
 *
 * Tests run on JVM via SimulationContextV2 — no GBDK or ROM required.
 *
 * Scenarios:
 * 1. Title scene initializes with enter ops (sprites hidden, screen cleared)
 * 2. Town scene initializes hp and gold display, heroActor at center
 * 3. Dungeon scene resets stepCount on enter
 * 4. Gameover scene has enter ops (game over screen rendered)
 * 5. Dungeon HP=0 causes game-over navigation (pure variable comparison, no input required)
 * 6. Dungeon stepCount triggers encounter check at 60 steps (variable comparison path)
 */
class RpgLiteGameTest {

    companion object {
        /** Build GameIR once for all tests in this class (shared fixture). */
        private val ir: GameIR = rpgLite.build()
    }

    // =========================================================================
    // Scenario 1: Title scene has enter ops
    // =========================================================================

    @Test
    fun `title scene initializes correctly`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("title")

        // Title scene sets up display; current scene is "title"
        assertEquals("title", sim.currentScene)
        // hp should still be at its initial value (30) when entering title
        sim.assertVar("hp", 30)
    }

    // =========================================================================
    // Scenario 2: Town scene enter resets heroActor position
    // =========================================================================

    @Test
    fun `town scene enter ops set heroActor to center`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("town")

        // After enter, heroActor.moveTo(80, 72) fires — position should be (80, 72)
        assertEquals(80, sim.getVar("heroActor.x"))
        assertEquals(72, sim.getVar("heroActor.y"))
    }

    // =========================================================================
    // Scenario 3: Dungeon scene resets stepCount on enter
    // =========================================================================

    @Test
    fun `dungeon scene enter resets stepCount to 0`() {
        val sim = SimulationContextV2(ir)

        // Pre-set stepCount to non-zero to confirm the reset
        sim.setVar("stepCount", 42)
        sim.enterScene("dungeon")

        // Enter op: stepCount set 0
        sim.assertVar("stepCount", 0)
    }

    // =========================================================================
    // Scenario 4: Gameover scene has enter ops
    // =========================================================================

    @Test
    fun `gameover scene initializes correctly`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameover")

        // Gameover scene should display game over screen
        assertEquals("gameover", sim.currentScene)
        // gold should still be accessible (initial value 0)
        sim.assertVar("gold", 0)
    }

    // =========================================================================
    // Scenario 5: Dungeon hp=0 navigates to gameover (pure variable comparison)
    // =========================================================================

    @Test
    fun `dungeon navigates to gameover when hp reaches 0`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("dungeon")

        // Set hp to 0 — dungeon frame condition: whenever(hp isEqualTo 0) { navigate(gameover) }
        sim.setVar("hp", 0)

        // Advance one frame — the hp==0 check fires and navigates to gameover
        sim.advanceFrames(1)

        assertEquals("gameover", sim.currentScene)
    }

    // =========================================================================
    // Scenario 6: Dungeon stepCount reaching 60 triggers encounter (variable comparison)
    // =========================================================================

    @Test
    fun `dungeon stepCount at 60 resets to 0 (encounter triggered)`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("dungeon")

        // Set stepCount to 60 — dungeon frame: whenever(stepCount isAtLeast 60) { stepCount set 0 }
        sim.setVar("stepCount", 60)

        // Advance one frame — the stepCount >= 60 condition fires, resetting stepCount to 0
        sim.advanceFrames(1)

        // stepCount should be reset to 0 by the encounter trigger
        sim.assertVar("stepCount", 0)
    }
}
