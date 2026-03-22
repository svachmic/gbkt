/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.dungeon

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.test.SimulationContextV2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scenario-based game logic tests for the Dungeon crawler game.
 *
 * Tests run on JVM via SimulationContextV2 — no GBDK or ROM required. Combat system (TriggerSystem)
 * executes as a no-op stub — tests focus on non-combat logic: torch depletion, variable
 * initialization, and scene setup.
 *
 * Key invariants from Dungeon.kt:
 * - torchLevel: u8Var(255) initial value
 * - keys: u8Var(0) initial value
 * - steps: u8Var(0) initial value
 * - Torch depletes only when: steps > 0 AND (steps & 3) == 0 AND torchLevel > 0
 * - Encounter trigger: whenever(steps >= 120) { steps = 0; navigate(battleScene) }
 *
 * Scenarios:
 * 1. Gameplay scene initializes correctly — torch/keys/steps at expected initial values
 * 2. Torch depletion condition — torchLevel decrements when steps at multiple of 4 > 0
 * 3. Title scene has enter ops — DSL-defined print ops present
 * 4. Gameover scene has enter ops — reset state and navigation ops present
 * 5. Battle encounter triggered when steps reach 120
 */
class DungeonGameTest {

    companion object {
        /** Build GameIR once for all tests in this class (shared fixture). */
        private val ir: GameIR = dungeon.build()
    }

    // =========================================================================
    // Scenario 1: Gameplay scene initial values
    // =========================================================================

    @Test
    fun `torchLevel starts at 255 on gameplay entry`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        // torchLevel initial value from DSL: u8Var(255)
        assertEquals(255, sim.getVar("torchLevel"))
    }

    @Test
    fun `keys starts at 0 on gameplay entry`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        // keys initial value from DSL: u8Var(0)
        assertEquals(0, sim.getVar("keys"))
    }

    @Test
    fun `steps starts at 0 on gameplay entry`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        // steps initial value from DSL: u8Var(0)
        assertEquals(0, sim.getVar("steps"))
    }

    // =========================================================================
    // Scenario 2: Torch depletion
    // =========================================================================

    @Test
    fun `torchLevel does not decrement without movement when steps is 0`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        // steps = 0 (initial): condition requires steps > 0, so torch stays at 255
        sim.setVar("steps", 0)
        sim.setVar("torchLevel", 255)

        sim.advanceFrames(5)

        // After 5 frames without movement: torchLevel unchanged
        sim.assertVar("torchLevel", 255)
    }

    @Test
    fun `torchLevel decrements when steps is at a multiple of 4 greater than 0`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        // Set steps=4: condition fires (4 > 0 AND 4 & 3 == 0 AND torchLevel > 0)
        sim.setVar("steps", 4)
        sim.setVar("torchLevel", 100)

        sim.advanceFrames(1)

        // After 1 frame: torchLevel decremented by 1
        sim.assertVar("torchLevel", 99)
    }

    // =========================================================================
    // Scenario 3: Title scene has enter ops
    // =========================================================================

    @Test
    fun `title scene can be entered and has print ops`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("title")

        // Title scene is properly navigable and has enter ops (print DUNGEON, PRESS START)
        assertEquals("title", sim.currentScene)

        // Enter ops ran without error — scene is valid
        assertTrue(
            ir.scenes.first { it.id == "title" }.enterOps.isNotEmpty(),
            "Title scene must have enter ops",
        )
    }

    // =========================================================================
    // Scenario 4: Gameover scene has enter ops
    // =========================================================================

    @Test
    fun `gameover scene can be entered directly`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameover")

        assertEquals("gameover", sim.currentScene)

        // Gameover scene has print ops (GAME OVER, TORCH EXPIRED, PRESS START)
        assertTrue(
            ir.scenes.first { it.id == "gameover" }.enterOps.isNotEmpty(),
            "Gameover scene must have enter ops",
        )
    }

    // =========================================================================
    // Scenario 5: Battle encounter triggered at steps >= 120
    // =========================================================================

    @Test
    fun `battle scene triggered when steps reaches 120`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        assertEquals("gameplay", sim.currentScene)

        // Set steps to 120 — encounter condition: whenever(steps >= 120) { navigate(battleScene) }
        sim.setVar("steps", 120)
        sim.setVar("torchLevel", 100) // ensure torch condition does not trigger gameover

        sim.advanceFrames(1)

        // Scene should have changed to battle
        assertEquals("battle", sim.currentScene)
    }
}
