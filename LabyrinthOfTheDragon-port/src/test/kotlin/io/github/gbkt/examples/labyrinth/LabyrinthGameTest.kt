/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.test.SimulationContextV2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Scenario-based game logic tests for the Labyrinth of the Dragon V2 port.
 *
 * Tests run on JVM via [SimulationContextV2] — no GBDK or ROM required. The combat engine
 * (CombatEngineSystem) and exploration system execute as stubs — tests focus on:
 * - Game IR builds without errors
 * - Scene entry and state initialization
 * - Scene enter ops are present and runnable
 * - Variable initial values match GameConfig constants
 *
 * All tests use the same shared IR fixture to avoid redundant build() calls.
 *
 * Note: Full navigation-flow tests (title → hero_select → gameplay → battle) require explicit input
 * simulation. These are covered at the scene entry level here — verifying that each scene can be
 * entered directly and has the expected initial state.
 */
class LabyrinthGameTest {

    companion object {
        /** Build GameIR once for all tests in this class (shared fixture). */
        private val ir: GameIR = LabyrinthOfTheDragon.create().build()
    }

    // =========================================================================
    // Scenario 1: Game IR builds without errors
    // =========================================================================

    @Test
    fun `can build game IR without errors`() {
        // If the DSL is wired incorrectly, create() or build() will throw.
        // This test verifies the complete integration compiles and builds cleanly.
        assertNotNull(ir, "GameIR must not be null after create().build()")
        assertEquals("LabyrinthDragon", ir.name)
    }

    // =========================================================================
    // Scenario 2: Title scene initialization
    // =========================================================================

    @Test
    fun `title scene can be entered directly`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("title")

        // After enterScene(), the current scene is "title"
        assertEquals("title", sim.currentScene)
    }

    @Test
    fun `title scene has enter ops`() {
        // Title scene must have enter ops (print title text, hide sprites, etc.)
        // from TitleScene.register() registered by Scenes.register()
        val titleScene = ir.scenes.first { it.id == "title" }
        assertTrue(
            titleScene.enterOps.isNotEmpty(),
            "Title scene must have enter ops (e.g., print title text)",
        )
    }

    // =========================================================================
    // Scenario 3: Hero select scene initialization
    // =========================================================================

    @Test
    fun `hero_select scene can be entered directly`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("hero_select")

        assertEquals("hero_select", sim.currentScene)
    }

    @Test
    fun `hero_select scene has enter ops`() {
        val heroSelectScene = ir.scenes.first { it.id == "hero_select" }
        assertTrue(heroSelectScene.enterOps.isNotEmpty(), "Hero select scene must have enter ops")
    }

    // =========================================================================
    // Scenario 4: Gameplay scene initialization
    // =========================================================================

    @Test
    fun `gameplay scene can be entered directly`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        assertEquals("gameplay", sim.currentScene)
    }

    @Test
    fun `torchLevel starts at 255`() {
        // torchLevel is declared as u8Var(255) in GameState
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        assertEquals(255, sim.getVar("torchLevel"), "torchLevel must start at 255 (TORCH_MAX)")
    }

    @Test
    fun `magicKeys starts at 0`() {
        // magicKeys is declared as u8Var(0) in GameState
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        assertEquals(0, sim.getVar("magicKeys"), "magicKeys must start at 0")
    }

    @Test
    fun `stepCount starts at 0`() {
        // stepCount is declared as u8Var(0) in GameState
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        assertEquals(0, sim.getVar("stepCount"), "stepCount must start at 0")
    }

    @Test
    fun `currentFloor starts at 1`() {
        // currentFloor is declared as u8Var(1) in GameState
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        assertEquals(1, sim.getVar("currentFloor"), "currentFloor must start at 1 (entrance floor)")
    }

    // =========================================================================
    // Scenario 5: Battle scene initialization
    // =========================================================================

    @Test
    fun `battle scene can be entered directly`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("battle")

        assertEquals("battle", sim.currentScene)
    }

    @Test
    fun `battle scene has enter ops`() {
        val battleScene = ir.scenes.first { it.id == "battle" }
        assertTrue(battleScene.enterOps.isNotEmpty(), "Battle scene must have enter ops")
    }

    // =========================================================================
    // Scenario 6: Game over scene initialization
    // =========================================================================

    @Test
    fun `gameover scene can be entered directly`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameover")

        assertEquals("gameover", sim.currentScene)
    }

    @Test
    fun `gameover scene has enter ops`() {
        val gameoverScene = ir.scenes.first { it.id == "gameover" }
        assertTrue(
            gameoverScene.enterOps.isNotEmpty(),
            "Gameover scene must have enter ops (e.g., GAME OVER text)",
        )
    }

    // =========================================================================
    // Scenario 7: Pause scene initialization
    // =========================================================================

    @Test
    fun `pause scene can be entered directly`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("pause")

        assertEquals("pause", sim.currentScene)
    }

    // =========================================================================
    // Scenario 8: Victory scene initialization
    // =========================================================================

    @Test
    fun `victory scene can be entered directly`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("victory")

        assertEquals("victory", sim.currentScene)
    }

    // =========================================================================
    // Scenario 9: Frame advancement does not crash
    // =========================================================================

    @Test
    fun `title scene advances frames without crash`() {
        // Smoke test: verifies title frame ops execute without exception
        val sim = SimulationContextV2(ir)
        sim.enterScene("title")
        sim.advanceFrames(5)

        // Still on title (no input to navigate away)
        assertEquals("title", sim.currentScene)
    }

    @Test
    fun `gameplay scene advances frames without crash`() {
        // Smoke test: verifies gameplay frame ops execute without exception
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")
        sim.advanceFrames(5)

        // Still on gameplay (no input, no step counter trigger)
        assertEquals("gameplay", sim.currentScene)
    }
}
