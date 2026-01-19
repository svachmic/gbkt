/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen.rpg

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.ir.IRCalculateInitiative
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRGetCurrentCombatantIndex
import io.github.gbkt.core.ir.IRGetCurrentInitiative
import io.github.gbkt.core.ir.IRGetRoundNumber
import io.github.gbkt.core.ir.IRGetTurnCount
import io.github.gbkt.core.ir.IRIsCurrentCombatantParty
import io.github.gbkt.core.ir.IRIsRoundComplete
import io.github.gbkt.core.ir.IRNextTurn
import io.github.gbkt.core.ir.IRResetTurnOrder
import io.github.gbkt.core.ir.IRSortTurnOrder
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRTurnOrderConfig
import io.github.gbkt.core.rpg.InitiativeMethod

// =============================================================================
// TURN ORDER CODE GENERATION
// =============================================================================

/**
 * Handle turn order IR statements.
 *
 * @return true if this was a turn order statement and was handled, false otherwise
 */
internal fun CodeGenerator.generateTurnOrderStatement(stmt: IRStatement): Boolean =
    when (stmt) {
        is IRTurnOrderConfig -> {
            generateTurnOrderConfig(stmt)
            true
        }
        is IRCalculateInitiative -> {
            generateCalculateInitiative()
            true
        }
        is IRSortTurnOrder -> {
            generateSortTurnOrder()
            true
        }
        is IRResetTurnOrder -> {
            generateResetTurnOrder()
            true
        }
        is IRNextTurn -> {
            generateNextTurn()
            true
        }
        else -> false
    }

/**
 * Generate C expression for turn order queries.
 *
 * @return the C expression string, or null if not a turn order expression
 */
internal fun CodeGenerator.generateTurnOrderExpr(expr: IRExpression): String? =
    when (expr) {
        is IRGetCurrentCombatantIndex -> "_turn_current_index"
        is IRGetCurrentInitiative -> "_turn_initiative[_turn_current_index]"
        is IRIsCurrentCombatantParty -> "(_turn_is_party[_turn_current_index])"
        is IRGetTurnCount -> "_turn_count"
        is IRGetRoundNumber -> "_round_number"
        is IRIsRoundComplete -> "(_turn_count >= _turn_order_count)"
        else -> null
    }

/** Generate turn order configuration and variables. */
private fun CodeGenerator.generateTurnOrderConfig(config: IRTurnOrderConfig) {
    line("// =============================================================================")
    line("// TURN ORDER SYSTEM")
    line("// =============================================================================")
    line()

    // Method constant
    line("// Initiative method: ${config.method.name}")
    line("#define TURN_METHOD_${config.method.name} ${config.method.ordinal}u")
    line("static UINT8 _turn_method = ${config.method.ordinal}u;")
    line()

    // Variables
    line("// Turn order variables")
    line("static UINT8 _turn_order[${config.maxCombatants}] = {0};")
    line("static UINT8 _turn_initiative[${config.maxCombatants}] = {0};")
    line("static UINT8 _turn_is_party[${config.maxCombatants}] = {0};")
    line("static UINT8 _turn_is_alive[${config.maxCombatants}] = {0};")
    line("static UINT8 _turn_order_count = 0u;")
    line("static UINT8 _turn_current_index = 0u;")
    line("static UINT8 _turn_count = 0u;")
    line("static UINT8 _round_number = 0u;")
    line("static UINT8 _turn_random_variance = ${config.randomVariance}u;")
    line()

    // Generate helper functions
    generateInitiativeCalculation(config)
    generateTurnOrderSort(config)
}

/** Generate initiative calculation function. */
private fun CodeGenerator.generateInitiativeCalculation(config: IRTurnOrderConfig) {
    line("// Calculate initiative for all combatants")
    line("static void _turn_calculate_initiative(void) {")
    indent++

    when (config.method) {
        InitiativeMethod.AGILITY_ONLY -> {
            line("// Pure agility-based initiative")
            line("for (UINT8 i = 0u; i < _turn_order_count; i++) {")
            indent++
            line("// Get agility from combatant stats")
            line("_turn_initiative[i] = _combatant_agl[_turn_order[i]];")
            indent--
            line("}")
        }
        InitiativeMethod.AGILITY_PLUS_RANDOM -> {
            line("// Agility + random variance")
            line("for (UINT8 i = 0u; i < _turn_order_count; i++) {")
            indent++
            line("// Get agility and add random factor")
            line("UINT8 base = _combatant_agl[_turn_order[i]];")
            line("UINT8 random = (UINT8)(rand() % (_turn_random_variance + 1u));")
            line("_turn_initiative[i] = base + random;")
            indent--
            line("}")
        }
        InitiativeMethod.SPEED_TIERS -> {
            line("// Speed tier based (initiative set per action)")
            line("// No calculation needed at round start")
        }
        InitiativeMethod.PARTY_FIRST -> {
            line("// Party goes first (high initiative)")
            line("for (UINT8 i = 0u; i < _turn_order_count; i++) {")
            indent++
            line("_turn_initiative[i] = _turn_is_party[i] ? 200u : 100u;")
            indent--
            line("}")
        }
        InitiativeMethod.ENEMIES_FIRST -> {
            line("// Enemies go first (high initiative)")
            line("for (UINT8 i = 0u; i < _turn_order_count; i++) {")
            indent++
            line("_turn_initiative[i] = _turn_is_party[i] ? 100u : 200u;")
            indent--
            line("}")
        }
        InitiativeMethod.ALTERNATING -> {
            line("// Alternating turns (set up in sort)")
            line("// Party members get even slots, enemies get odd")
        }
    }

    indent--
    line("}")
    line()
}

/** Generate turn order sorting function. */
private fun CodeGenerator.generateTurnOrderSort(config: IRTurnOrderConfig) {
    line("// Sort turn order by initiative (bubble sort - simple for small arrays)")
    line("static void _turn_sort_order(void) {")
    indent++

    if (config.method == InitiativeMethod.ALTERNATING) {
        line("// Alternating: interleave party and enemies")
        line("UINT8 party_idx = 0u;")
        line("UINT8 enemy_idx = 0u;")
        line("UINT8 temp_order[${config.maxCombatants}];")
        line("UINT8 temp_count = 0u;")
        line()
        line("// First pass: collect party and enemy indices")
        line("for (UINT8 i = 0u; i < _turn_order_count; i++) {")
        indent++
        line("if (_turn_is_party[i]) {")
        indent++
        line("temp_order[temp_count++] = i;")
        indent--
        line("}")
        indent--
        line("}")
    } else {
        line("// Bubble sort by initiative (descending)")
        line("for (UINT8 i = 0u; i < _turn_order_count - 1u; i++) {")
        indent++
        line("for (UINT8 j = 0u; j < _turn_order_count - i - 1u; j++) {")
        indent++
        line("if (_turn_initiative[j] < _turn_initiative[j + 1u]) {")
        indent++
        line("// Swap initiative")
        line("UINT8 temp = _turn_initiative[j];")
        line("_turn_initiative[j] = _turn_initiative[j + 1u];")
        line("_turn_initiative[j + 1u] = temp;")
        line("// Swap order")
        line("temp = _turn_order[j];")
        line("_turn_order[j] = _turn_order[j + 1u];")
        line("_turn_order[j + 1u] = temp;")
        line("// Swap is_party flag")
        line("temp = _turn_is_party[j];")
        line("_turn_is_party[j] = _turn_is_party[j + 1u];")
        line("_turn_is_party[j + 1u] = temp;")
        indent--
        line("}")
        indent--
        line("}")
        indent--
        line("}")
    }

    indent--
    line("}")
    line()
}

/** Generate code for calculating initiative. */
private fun CodeGenerator.generateCalculateInitiative() {
    line("// Calculate initiative for all combatants")
    line("_turn_calculate_initiative();")
}

/** Generate code for sorting turn order. */
private fun CodeGenerator.generateSortTurnOrder() {
    line("// Sort combatants by initiative")
    line("_turn_sort_order();")
}

/** Generate code for resetting turn order. */
private fun CodeGenerator.generateResetTurnOrder() {
    line("// Reset turn order")
    line("_turn_current_index = 0u;")
    line("_turn_count = 0u;")
    line("_round_number = 0u;")
}

/** Generate code for advancing to next turn. */
private fun CodeGenerator.generateNextTurn() {
    line("// Advance to next turn")
    line("_turn_count++;")
    line("_turn_current_index++;")
    line("if (_turn_current_index >= _turn_order_count) {")
    indent++
    line("// Round complete, start new round")
    line("_turn_current_index = 0u;")
    line("_turn_count = 0u;")
    line("_round_number++;")
    indent--
    line("}")
}
