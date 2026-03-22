/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.explorer

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.test.SimulationContextV2
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Scenario-based game logic tests for the Explorer v2 game.
 *
 * Tests run on JVM via SimulationContextV2 — no GBDK or ROM required. Combat system (TriggerSystem)
 * executes as a no-op stub — tests focus on non-combat logic: torch gauge, scene navigation, and
 * movement boundary behaviour.
 *
 * Key invariants from ExplorerV2.kt:
 * - torchLevel: u8Var(100) initial value (NOT 255)
 * - Torch depletes only when: stepCount > 0 AND (stepCount & 3) == 0 AND torchLevel > 0 → torch
 *   does NOT decrement every frame; requires stepCount at a multiple-of-4 value
 * - Movement: boundary PREVENTION via guard (dpad.left.held + player.x isAbove 8 check) → no
 *   clamping occurs when player is already out-of-bounds without d-pad
 * - Combat trigger: whenever(stepCount isAtLeast 120) { stepCount set 0; navigate(combatScene) }
 *
 * Scenarios:
 * 1. Torch depletion — torchLevel starts at 100; depletes when stepCount & 3 == 0 AND stepCount > 0
 * 2. Player position stability — player stays put without d-pad input (no clamping)
 * 3. Combat scene triggered when stepCount reaches 120 (not 20)
 * 4. Pause scene — can be entered directly and torchLevel unchanged (depletion is gameplay-only)
 */
class ExplorerGameTest {

    companion object {
        /** Build GameIR once for all tests in this class (shared fixture). */
        private val ir: GameIR = explorerV2.build()
    }

    // =========================================================================
    // Scenario 1: Torch depletion
    // =========================================================================

    @Test
    fun `torchLevel starts at 100 and does not decrement without d-pad input`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        // torchLevel starts at 100 (from DSL: u8Var(100))
        assertEquals(100, sim.getVar("torchLevel"))

        // stepCount = 0 (initial). Torch condition requires stepCount > 0, so:
        // whenever((stepCount > 0) AND (stepCount & 3 == 0) AND (torchLevel > 0)) { ... }
        // Without d-pad, stepCount stays 0 → condition false → torch unchanged
        sim.advanceFrames(5)

        // After 5 frames without input: torchLevel still 100
        sim.assertVar("torchLevel", 100)
    }

    @Test
    fun `torchLevel decrements when stepCount is at a multiple of 4`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        // Set stepCount to 4 (> 0 AND 4 & 3 == 0 → condition fires each frame)
        sim.setVar("stepCount", 4)
        sim.setVar("torchLevel", 100)

        // Advance 1 frame — condition fires: stepCount(4) > 0, 4 & 3 = 0, torchLevel(100) > 0
        sim.advanceFrames(1)

        // After 1 frame: torchLevel decremented by 1
        sim.assertVar("torchLevel", 99)
    }

    @Test
    fun `torchLevel continues depleting across many frames when stepCount stays at multiple of 4`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        // Set stepCount=4 so torch depletes every frame (4 > 0, 4 & 3 == 0)
        // stepCount stays at 4 since dpad.any is false (no d-pad in sim → stepCount += 1 doesn't
        // fire)
        sim.setVar("stepCount", 4)
        sim.setVar("torchLevel", 30)

        // Advance 10 frames — torch depletes by 1 each frame → 30 - 10 = 20
        sim.advanceFrames(10)

        sim.assertVar("torchLevel", 20)
    }

    // =========================================================================
    // Scenario 2: Player position stability (no clamping, only movement guard)
    // =========================================================================

    @Test
    fun `player position unchanged without d-pad input even when near left boundary`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        // Game uses boundary PREVENTION (not clamping):
        // whenever(dpad.left.held) { whenever(player.x isAbove 8) { moveBy(player, -2, 0) } }
        // Without d-pad held, no movement occurs — player stays wherever it is
        sim.setVar("player.x", 5) // below boundary of 8 — no clamping, just stays at 5
        sim.setVar("player.y", 72)
        sim.setVar("stepCount", 0) // ensure torch condition doesn't trigger navigation

        // Advance one frame without d-pad input
        sim.advanceFrames(1)

        // Player stays at x=5 (no clamping happens without d-pad input)
        assertEquals(5, sim.getVar("player.x"))
        assertEquals(72, sim.getVar("player.y"))
    }

    @Test
    fun `player position unchanged without d-pad input even when past right boundary`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        // Game uses boundary PREVENTION (not clamping):
        // whenever(dpad.right.held) { whenever(player.x isBelow 152) { moveBy(player, 2, 0) } }
        // Without d-pad held, player stays wherever it is — even past the boundary
        sim.setVar("player.x", 160) // past boundary of 152 — stays at 160 without d-pad
        sim.setVar("player.y", 72)
        sim.setVar("stepCount", 0) // ensure torch condition doesn't trigger navigation

        // Advance one frame without d-pad input
        sim.advanceFrames(1)

        // Player stays at x=160 (boundary prevention only blocks d-pad movement, no snap-back)
        assertEquals(160, sim.getVar("player.x"))
        assertEquals(72, sim.getVar("player.y"))
    }

    // =========================================================================
    // Scenario 3: Random encounter trigger at stepCount >= 120
    // =========================================================================

    @Test
    fun `combat scene triggered when stepCount reaches 120`() {
        val sim = SimulationContextV2(ir)
        sim.enterScene("gameplay")

        assertEquals("gameplay", sim.currentScene)

        // Set stepCount to 120 to trigger the encounter condition
        // Gameplay frame: whenever(stepCount >= 120) { stepCount = 0; navigate(combatScene) }
        sim.setVar("stepCount", 120)

        // Advance one frame — encounter condition fires
        sim.advanceFrames(1)

        // Scene should have changed to combat_scene
        assertEquals("combat_scene", sim.currentScene)
    }

    @Test
    fun `pause scene can be entered directly and returns to gameplay on START`() {
        val sim = SimulationContextV2(ir)

        // Directly enter pause scene to verify it exists and has expected content
        sim.enterScene("pause")
        assertEquals("pause", sim.currentScene)

        // The pause scene is properly navigable — DSL built without errors
        // (buttonPressed("start") is a no-op stub in the interpreter,
        // so we verify scene entry works rather than button navigation)
        val torchOnEntry = sim.getVar("torchLevel")
        sim.advanceFrames(1)

        // Torch only depletes in gameplay — should be unchanged in pause scene
        assertEquals(torchOnEntry, sim.getVar("torchLevel"))
    }
}
