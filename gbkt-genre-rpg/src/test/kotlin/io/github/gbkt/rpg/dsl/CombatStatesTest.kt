/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.ir.CallExpr
import io.github.gbkt.core.ir.CombatStateId
import io.github.gbkt.core.ir.VarRef
import io.github.gbkt.rpg.domain.CombatStates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// =============================================================================
// COMBAT STATES TESTS
// Verifies CombatStates constants and typed combatIsInState() function (GAP-10).
// =============================================================================

class CombatStatesTest {

    // =========================================================================
    // CombatStates constants — map to COMBAT_STATE_* C constants
    // =========================================================================

    @Test
    fun `CombatStates INIT maps to COMBAT_STATE_INIT`() {
        assertEquals(CombatStateId("COMBAT_STATE_INIT"), CombatStates.INIT)
    }

    @Test
    fun `CombatStates PLAYER_TURN maps to COMBAT_STATE_PLAYER_TURN`() {
        assertEquals(CombatStateId("COMBAT_STATE_PLAYER_TURN"), CombatStates.PLAYER_TURN)
    }

    @Test
    fun `CombatStates TARGET_SELECT maps to COMBAT_STATE_TARGET_SELECT`() {
        assertEquals(CombatStateId("COMBAT_STATE_TARGET_SELECT"), CombatStates.TARGET_SELECT)
    }

    @Test
    fun `CombatStates EXECUTE_ACTION maps to COMBAT_STATE_EXECUTE_ACTION`() {
        assertEquals(CombatStateId("COMBAT_STATE_EXECUTE_ACTION"), CombatStates.EXECUTE_ACTION)
    }

    @Test
    fun `CombatStates ENEMY_TURN maps to COMBAT_STATE_ENEMY_TURN`() {
        assertEquals(CombatStateId("COMBAT_STATE_ENEMY_TURN"), CombatStates.ENEMY_TURN)
    }

    @Test
    fun `CombatStates VICTORY maps to COMBAT_STATE_VICTORY`() {
        assertEquals(CombatStateId("COMBAT_STATE_VICTORY"), CombatStates.VICTORY)
    }

    @Test
    fun `CombatStates DEFEAT maps to COMBAT_STATE_DEFEAT`() {
        assertEquals(CombatStateId("COMBAT_STATE_DEFEAT"), CombatStates.DEFEAT)
    }

    @Test
    fun `CombatStates FLEEING maps to COMBAT_STATE_FLEEING`() {
        assertEquals(CombatStateId("COMBAT_STATE_FLEEING"), CombatStates.FLEEING)
    }

    @Test
    fun `CombatStates WAITING maps to COMBAT_STATE_WAITING`() {
        assertEquals(CombatStateId("COMBAT_STATE_WAITING"), CombatStates.WAITING)
    }

    // =========================================================================
    // combatIsInState(CombatStateId, BattleRef) — returns correct CallExpr
    // =========================================================================

    @Test
    fun `combatIsInState returns CallExpr with battle-specific function name`() {
        val battle = BattleRef("combat")
        val expr = combatIsInState(CombatStates.VICTORY, battle)
        val callExpr = assertIs<CallExpr>(expr)
        assertEquals("combat_is_in_state_combat", callExpr.function)
    }

    @Test
    fun `combatIsInState passes state id as VarRef argument`() {
        val battle = BattleRef("combat")
        val expr = combatIsInState(CombatStates.VICTORY, battle)
        val callExpr = assertIs<CallExpr>(expr)
        assertEquals(1, callExpr.args.size)
        val arg = assertIs<VarRef>(callExpr.args[0])
        assertEquals("COMBAT_STATE_VICTORY", arg.name)
    }

    @Test
    fun `combatIsInState with DEFEAT state produces correct args`() {
        val battle = BattleRef("dungeon_combat")
        val expr = combatIsInState(CombatStates.DEFEAT, battle)
        val callExpr = assertIs<CallExpr>(expr)
        assertEquals("combat_is_in_state_dungeon_combat", callExpr.function)
        val arg = assertIs<VarRef>(callExpr.args[0])
        assertEquals("COMBAT_STATE_DEFEAT", arg.name)
    }

    @Test
    fun `combatIsInState with PLAYER_TURN state produces correct function name`() {
        val battle = BattleRef("main_battle")
        val expr = combatIsInState(CombatStates.PLAYER_TURN, battle)
        val callExpr = assertIs<CallExpr>(expr)
        assertEquals("combat_is_in_state_main_battle", callExpr.function)
        val arg = assertIs<VarRef>(callExpr.args[0])
        assertEquals("COMBAT_STATE_PLAYER_TURN", arg.name)
    }

    // =========================================================================
    // combatIsInState typed overload vs string overload — produce identical IR
    // =========================================================================

    @Test
    fun `typed and string overloads of combatIsInState produce identical CallExpr`() {
        val typedExpr = combatIsInState(CombatStates.VICTORY, BattleRef("combat"))
        @Suppress("DEPRECATION") val stringExpr = combatIsInState("COMBAT_STATE_VICTORY", "combat")
        assertEquals(typedExpr, stringExpr)
    }
}
