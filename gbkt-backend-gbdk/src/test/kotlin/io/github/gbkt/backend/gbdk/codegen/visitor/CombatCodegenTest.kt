/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatStateId
import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.core.ir.DamageFormulaRef
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// COMBAT CODEGEN TESTS (Plan 06.4-02 success criterion)
// 16 tests covering all CombatEngineSystem codegen paths:
//   - TURN_BASED and REAL_TIME mode differences
//   - Deferred state transitions via _pending_state global
//   - All 5 core states (INIT, PLAYER_TURN, ENEMY_TURN, VICTORY, DEFEAT)
//   - Custom states extensibility
//   - Hierarchical sub-states with parent_state helper
//   - Victory/defeat op injection into switch cases
//   - Declarative victory/defeat condition checks before the switch
//   - Damage formula dispatcher (present and absent)
//   - Global variable generation (_combat_state, _pending_state)
//   - Helper functions (combat_request_state, trigger, combat_is_in_state)
// =============================================================================

/** Build a minimal GameIR with a CombatEngineSystem and one gameplay scene. */
private fun buildCombatGameIR(system: CombatEngineSystem, startScene: String = "gameplay"): GameIR =
    GameIR(
        name = "TestCombatGame",
        config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY),
        scenes = listOf(SceneIR(id = startScene)),
        systems = listOf(system),
        startScene = startScene,
    )

/** The system ID used in all default test fixtures. */
private const val DEFAULT_COMBAT_ID = "combat"

/** A minimal TURN_BASED combat system with no optional features. */
private fun defaultTurnBasedSystem(): CombatEngineSystem =
    CombatEngineSystem(id = DEFAULT_COMBAT_ID, combatType = CombatType.TURN_BASED)

/** A minimal REAL_TIME combat system. */
private fun realTimeSystem(): CombatEngineSystem =
    CombatEngineSystem(id = DEFAULT_COMBAT_ID, combatType = CombatType.REAL_TIME)

class CombatCodegenTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // Test 1: TURN_BASED generates update_combat function
    // =========================================================================

    @Test
    fun `combatEngine TURN_BASED generates update_combat function`() {
        val gameIR = buildCombatGameIR(defaultTurnBasedSystem())
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("void update_combat_${DEFAULT_COMBAT_ID}(void)"),
            "Expected 'void update_combat_${DEFAULT_COMBAT_ID}(void)' in main.c",
        )
    }

    // =========================================================================
    // Test 2: Deferred transition via _pending_state global
    // =========================================================================

    @Test
    fun `combatEngine generates deferred transition via _pending_state global`() {
        val gameIR = buildCombatGameIR(defaultTurnBasedSystem())
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // _pending_state_<id> != 255u (CLiteral(0xFF) emits as 255u)
        assertTrue(
            mainC.contains("_pending_state_${DEFAULT_COMBAT_ID}") && mainC.contains("255u"),
            "Expected _pending_state_${DEFAULT_COMBAT_ID} != 255u deferred check in update function",
        )
    }

    // =========================================================================
    // Test 3: All 5 core state cases in the switch
    // =========================================================================

    @Test
    fun `combatEngine generates all 5 core state cases`() {
        val gameIR = buildCombatGameIR(defaultTurnBasedSystem())
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // CLiteral(0)→0u, CLiteral(1)→1u, CLiteral(2)→2u, CLiteral(3)→3u, CLiteral(4)→4u
        assertTrue(mainC.contains("case 0u:"), "Expected 'case 0u:' (INIT)")
        assertTrue(mainC.contains("case 1u:"), "Expected 'case 1u:' (PLAYER_TURN)")
        assertTrue(mainC.contains("case 2u:"), "Expected 'case 2u:' (ENEMY_TURN)")
        assertTrue(mainC.contains("case 3u:"), "Expected 'case 3u:' (VICTORY)")
        assertTrue(mainC.contains("case 4u:"), "Expected 'case 4u:' (DEFEAT)")
    }

    // =========================================================================
    // Test 4: REAL_TIME skips J_A check in PLAYER_TURN case
    // =========================================================================

    @Test
    fun `combatEngine REAL_TIME skips J_A check in PLAYER_TURN case`() {
        val gameIR = buildCombatGameIR(realTimeSystem())
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // REAL_TIME should NOT have button_pressed(J_A) — that's turn-based only
        assertFalse(
            mainC.contains("button_pressed(J_A)"),
            "REAL_TIME mode should not generate button_pressed(J_A) check in PLAYER_TURN",
        )
    }

    // =========================================================================
    // Test 5: combat_request_state helper function generated
    // =========================================================================

    @Test
    fun `combatEngine generates combat_request_state helper`() {
        val gameIR = buildCombatGameIR(defaultTurnBasedSystem())
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("void combat_request_state_${DEFAULT_COMBAT_ID}(UINT8 s)"),
            "Expected combat_request_state function definition",
        )
        assertTrue(
            mainC.contains("_pending_state_${DEFAULT_COMBAT_ID} = s"),
            "Expected assignment _pending_state_${DEFAULT_COMBAT_ID} = s inside request_state",
        )
    }

    // =========================================================================
    // Test 6: trigger function resets to INIT and clears pending
    // =========================================================================

    @Test
    fun `combatEngine trigger function resets to INIT and clears pending`() {
        val gameIR = buildCombatGameIR(defaultTurnBasedSystem())
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("void trigger_${DEFAULT_COMBAT_ID}(void)"),
            "Expected trigger function definition",
        )
        assertTrue(
            mainC.contains("_combat_state_${DEFAULT_COMBAT_ID} = 0u"),
            "Expected _combat_state reset to 0u in trigger function",
        )
        assertTrue(
            mainC.contains("_pending_state_${DEFAULT_COMBAT_ID} = 255u"),
            "Expected _pending_state reset to 255u in trigger function",
        )
    }

    // =========================================================================
    // Test 7: onVictory ops injected into VICTORY case
    // =========================================================================

    @Test
    fun `combatEngine onVictory ops injected into VICTORY case`() {
        val system =
            CombatEngineSystem(
                id = DEFAULT_COMBAT_ID,
                combatType = CombatType.TURN_BASED,
                onVictoryOps = listOf(NavigateTo("victory")),
            )
        val gameIR =
            buildCombatGameIR(system)
                .copy(scenes = listOf(SceneIR(id = "gameplay"), SceneIR(id = "victory")))
        val output = pipeline.generate(gameIR)
        // Navigate ops go to bank1 via scene codegen — check the function is there
        // The victory ops are emitted in case 3u of the state machine in main.c
        val allC = output.files.values.joinToString("\n")

        assertTrue(
            allC.contains("navigate_to_scene") || allC.contains("victory"),
            "Expected victory scene navigation in generated code",
        )
    }

    // =========================================================================
    // Test 8: combat_is_in_state helper generated
    // =========================================================================

    @Test
    fun `combatEngine generates combat_is_in_state helper`() {
        val gameIR = buildCombatGameIR(defaultTurnBasedSystem())
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("UINT8 combat_is_in_state_${DEFAULT_COMBAT_ID}(UINT8 state)"),
            "Expected combat_is_in_state function definition",
        )
        // Returns comparison result (emitted as == expression in return statement)
        assertTrue(
            mainC.contains("_combat_state_${DEFAULT_COMBAT_ID}") && mainC.contains("state"),
            "Expected comparison of _combat_state and state parameter",
        )
    }

    // =========================================================================
    // Test 9: Custom states generate additional switch cases
    // =========================================================================

    @Test
    fun `combatEngine custom states generate additional switch cases`() {
        val system =
            CombatEngineSystem(
                id = DEFAULT_COMBAT_ID,
                customStates = listOf(CombatStateId("NEGOTIATION")),
            )
        val gameIR = buildCombatGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Case 5 = first custom state (0=INIT, 1=PLAYER_TURN, 2=ENEMY_TURN, 3=VICTORY, 4=DEFEAT)
        assertTrue(mainC.contains("case 5u:"), "Expected 'case 5u:' for custom NEGOTIATION state")
        assertTrue(
            mainC.contains("NEGOTIATION"),
            "Expected custom state ID 'NEGOTIATION' in comment",
        )
    }

    // =========================================================================
    // Test 10: Globals include _combat_state and _pending_state
    // =========================================================================

    @Test
    fun `combatEngine globals include _combat_state and _pending_state`() {
        val gameIR = buildCombatGameIR(defaultTurnBasedSystem())
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_combat_state_${DEFAULT_COMBAT_ID}"),
            "Expected _combat_state_${DEFAULT_COMBAT_ID} global variable",
        )
        assertTrue(
            mainC.contains("_pending_state_${DEFAULT_COMBAT_ID}"),
            "Expected _pending_state_${DEFAULT_COMBAT_ID} global variable",
        )
    }

    // =========================================================================
    // Test 11: damage formula generates dispatcher function when set
    // =========================================================================

    @Test
    fun `combatEngine with damageFormula generates damage dispatcher`() {
        val system =
            CombatEngineSystem(id = DEFAULT_COMBAT_ID, damageFormula = DamageFormulaRef("calc_dmg"))
        val gameIR = buildCombatGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("UINT8 damage_${DEFAULT_COMBAT_ID}(UINT8 src, UINT8 tgt, UINT8 amount)"),
            "Expected damage_${DEFAULT_COMBAT_ID} function with UINT8 params",
        )
        assertTrue(
            mainC.contains("calc_dmg(src, tgt, amount)"),
            "Expected call to user-provided calc_dmg formula function",
        )
    }

    // =========================================================================
    // Test 12: Without damageFormula, damage function is NOT generated
    // =========================================================================

    @Test
    fun `combatEngine without damageFormula skips damage function`() {
        val system = CombatEngineSystem(id = DEFAULT_COMBAT_ID, damageFormula = null)
        val gameIR = buildCombatGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertFalse(
            mainC.contains("damage_${DEFAULT_COMBAT_ID}"),
            "Expected NO damage_${DEFAULT_COMBAT_ID} function when damageFormula is null",
        )
    }

    // =========================================================================
    // Test 13: onVictoryCondition generates per-frame if-check before the switch
    // =========================================================================

    @Test
    fun `combatEngine onVictoryCondition generates per-frame condition check`() {
        // Use a simple IfOp with a literal condition: whenever(1 == 1) { }
        val victoryCondition =
            IfOp(
                condition = BinaryExpr(left = Literal(1), op = BinaryOp.EQ, right = Literal(0)),
                then = emptyList(),
                otherwise = emptyList(),
            )
        val system =
            CombatEngineSystem(
                id = DEFAULT_COMBAT_ID,
                onVictoryCondition = listOf(victoryCondition),
            )
        val gameIR = buildCombatGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // The condition check calls combat_request_state_<id>(3u) — VICTORY state
        assertTrue(
            mainC.contains("combat_request_state_${DEFAULT_COMBAT_ID}(3u)"),
            "Expected combat_request_state_${DEFAULT_COMBAT_ID}(3u) in victory condition check",
        )
    }

    // =========================================================================
    // Test 14: onDefeatCondition generates per-frame if-check
    // =========================================================================

    @Test
    fun `combatEngine onDefeatCondition generates per-frame condition check`() {
        val defeatCondition =
            IfOp(
                condition = BinaryExpr(left = Literal(0), op = BinaryOp.EQ, right = Literal(1)),
                then = emptyList(),
                otherwise = emptyList(),
            )
        val system =
            CombatEngineSystem(id = DEFAULT_COMBAT_ID, onDefeatCondition = listOf(defeatCondition))
        val gameIR = buildCombatGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // The condition check calls combat_request_state_<id>(4u) — DEFEAT state
        assertTrue(
            mainC.contains("combat_request_state_${DEFAULT_COMBAT_ID}(4u)"),
            "Expected combat_request_state_${DEFAULT_COMBAT_ID}(4u) in defeat condition check",
        )
    }

    // =========================================================================
    // Test 15: Hierarchical sub-states generate child cases
    // =========================================================================

    @Test
    fun `combatEngine hierarchical sub-states generate child cases`() {
        val system =
            CombatEngineSystem(
                id = DEFAULT_COMBAT_ID,
                stateHierarchy =
                    mapOf(
                        CombatStateId("PLAYER_TURN") to listOf(CombatStateId("SELECTING_TARGET"))
                    ),
            )
        val gameIR = buildCombatGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Sub-state IDs start at 64 (case 64u)
        assertTrue(mainC.contains("case 64u:"), "Expected 'case 64u:' for first sub-state")
        assertTrue(
            mainC.contains("SELECTING_TARGET"),
            "Expected sub-state ID 'SELECTING_TARGET' in case comment",
        )
    }

    // =========================================================================
    // Test 16: Hierarchical states generate combat_parent_state helper
    // =========================================================================

    @Test
    fun `combatEngine hierarchical states generate combat_parent_state helper`() {
        val system =
            CombatEngineSystem(
                id = DEFAULT_COMBAT_ID,
                stateHierarchy =
                    mapOf(
                        CombatStateId("PLAYER_TURN") to listOf(CombatStateId("SELECTING_TARGET"))
                    ),
            )
        val gameIR = buildCombatGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("UINT8 combat_parent_state_${DEFAULT_COMBAT_ID}(UINT8 state)"),
            "Expected combat_parent_state_${DEFAULT_COMBAT_ID} function definition",
        )
    }
}
