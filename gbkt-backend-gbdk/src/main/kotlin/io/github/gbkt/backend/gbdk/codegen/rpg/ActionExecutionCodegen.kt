/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.rpg

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.backend.gbdk.codegen.core.generateStatement
import io.github.gbkt.core.ir.IRActionExecute
import io.github.gbkt.core.ir.IRActionPipelineConfig
import io.github.gbkt.core.ir.IRActionQueueAdd
import io.github.gbkt.core.ir.IRActionQueueClear
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRGetActionCount
import io.github.gbkt.core.ir.IRGetCurrentActionType
import io.github.gbkt.core.ir.IRGetCurrentActorIndex
import io.github.gbkt.core.ir.IRGetCurrentTargetCount
import io.github.gbkt.core.ir.IRHasQueuedActions
import io.github.gbkt.core.ir.IRNextAction
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.rpg.ActionPipelineConfig
import io.github.gbkt.core.rpg.BattleActionType

// =============================================================================
// ACTION EXECUTION CODE GENERATION
// =============================================================================

/**
 * Handle action execution IR statements.
 *
 * @return true if this was an action execution statement and was handled, false otherwise
 */
internal fun GBDKCodeGenerator.generateActionExecutionStatement(stmt: IRStatement): Boolean =
    when (stmt) {
        is IRActionPipelineConfig -> {
            generateActionPipelineConfig(stmt.config)
            true
        }
        is IRActionQueueAdd -> {
            generateActionQueueAdd(stmt)
            true
        }
        is IRActionExecute -> {
            generateActionExecute(stmt)
            true
        }
        is IRNextAction -> {
            generateNextAction(stmt)
            true
        }
        is IRActionQueueClear -> {
            generateActionQueueClear(stmt)
            true
        }
        else -> false
    }

/**
 * Generate C expression for action execution queries.
 *
 * @return the C expression string, or null if not an action execution expression
 */
internal fun GBDKCodeGenerator.generateActionExecutionExpr(expr: IRExpression): String? =
    when (expr) {
        is IRHasQueuedActions -> "(_action_${expr.pipelineName}_count > 0u)"
        is IRGetActionCount -> "_action_${expr.pipelineName}_count"
        is IRGetCurrentActionType -> "_action_${expr.pipelineName}_current_type"
        is IRGetCurrentActorIndex -> "_action_${expr.pipelineName}_current_actor"
        is IRGetCurrentTargetCount -> "_action_${expr.pipelineName}_target_count"
        else -> null
    }

/** Generate action pipeline configuration and variables. */
private fun GBDKCodeGenerator.generateActionPipelineConfig(config: ActionPipelineConfig) {
    val name = config.name
    val nameUpper = name.uppercase()

    line("// =============================================================================")
    line("// ACTION EXECUTION PIPELINE: $name")
    line("// =============================================================================")
    line()

    // Action type constants
    line("// Action type constants")
    BattleActionType.entries.forEachIndexed { index, type ->
        line("#define ACTION_TYPE_${type.name} ${index}u")
    }
    line()

    // Configuration
    line("// Configuration")
    line("#define ${nameUpper}_MAX_QUEUE ${config.maxQueueSize}u")
    line("#define ${nameUpper}_MAX_TARGETS 8u")
    line()

    // Queue structure
    line("// Action queue entry")
    line("typedef struct {")
    indent++
    line("UINT8 type;")
    line("UINT8 actor_index;")
    line("UINT8 target_count;")
    line("UINT8 targets[${nameUpper}_MAX_TARGETS];")
    line("UINT8 ability_id;")
    line("UINT8 item_id;")
    indent--
    line("} ${name}_action_t;")
    line()

    // State variables
    line("// Action pipeline state")
    line("static ${name}_action_t _action_${name}_queue[${nameUpper}_MAX_QUEUE];")
    line("static UINT8 _action_${name}_count = 0u;")
    line("static UINT8 _action_${name}_current = 0u;")
    line("static UINT8 _action_${name}_current_type = ACTION_TYPE_WAIT;")
    line("static UINT8 _action_${name}_current_actor = 0u;")
    line("static UINT8 _action_${name}_target_count = 0u;")
    line("static UINT8 _action_${name}_result = 0u;")
    line()

    // Generate helper functions
    generateActionPipelineHelpers(config)
}

private fun GBDKCodeGenerator.generateActionPipelineHelpers(config: ActionPipelineConfig) {
    val name = config.name
    val nameUpper = name.uppercase()

    // Function to add action to queue
    line("// Add action to queue")
    line(
        "static void _action_${name}_add(UINT8 type, UINT8 actor, UINT8 target_count, UINT8* targets, UINT8 ability_id, UINT8 item_id) {"
    )
    indent++

    line("if (_action_${name}_count >= ${nameUpper}_MAX_QUEUE) return;")
    line()
    line("${name}_action_t* action = &_action_${name}_queue[_action_${name}_count];")
    line("action->type = type;")
    line("action->actor_index = actor;")
    line("action->target_count = target_count;")
    line("for (UINT8 i = 0u; i < target_count && i < ${nameUpper}_MAX_TARGETS; i++) {")
    indent++
    line("action->targets[i] = targets[i];")
    indent--
    line("}")
    line("action->ability_id = ability_id;")
    line("action->item_id = item_id;")
    line()
    line("_action_${name}_count++;")

    indent--
    line("}")
    line()

    // Function to execute current action
    line("// Execute current action")
    line("static void _action_${name}_execute(void) {")
    indent++

    line("if (_action_${name}_current >= _action_${name}_count) return;")
    line()
    line("${name}_action_t* action = &_action_${name}_queue[_action_${name}_current];")
    line("_action_${name}_current_type = action->type;")
    line("_action_${name}_current_actor = action->actor_index;")
    line("_action_${name}_target_count = action->target_count;")
    line()

    // Execute based on action type
    line("switch (action->type) {")
    indent++

    line("case ACTION_TYPE_ATTACK:")
    indent++
    line("{")
    indent++
    line("// Basic attack - physical damage calculation")
    line("if (action->target_count > 0u) {")
    indent++
    line("UINT8 target_idx = action->targets[0];")
    line("UINT8 actor_idx = action->actor_index;")
    line()
    line("// Get attacker ATK and defender DEF from combatant stats")
    line("UINT16 atk = _combatant_atk[actor_idx];")
    line("UINT16 def = _combatant_def[target_idx];")
    line()
    line("// Calculate damage: ATK - DEF/2, minimum 1")
    line("UINT16 damage = (atk > (def / 2u)) ? (atk - (def / 2u)) : 1u;")
    line()
    line("// Check if defender is defending (halve damage)")
    line("if (_combatant_defending[target_idx]) {")
    indent++
    line("damage = (damage / 2u > 0u) ? (damage / 2u) : 1u;")
    indent--
    line("}")
    line()
    line("// Apply damage to target HP")
    line("if (_combatant_hp[target_idx] > damage) {")
    indent++
    line("_combatant_hp[target_idx] -= damage;")
    indent--
    line("} else {")
    indent++
    line("_combatant_hp[target_idx] = 0u;")
    indent--
    line("}")
    line()
    line("_action_${name}_result = 1u;")
    indent--
    line("}")
    indent--
    line("}")
    line("break;")
    indent--

    line("case ACTION_TYPE_ABILITY:")
    indent++
    line("{")
    indent++
    line("// Ability execution")
    line("UINT8 ability_id = action->ability_id;")
    line("UINT8 actor_idx = action->actor_index;")
    line()
    line("// Check if actor has enough SP")
    line("UINT8 sp_cost = _ability_sp_cost[ability_id];")
    line("if (_combatant_sp[actor_idx] >= sp_cost) {")
    indent++
    line("// Deduct SP cost")
    line("_combatant_sp[actor_idx] -= sp_cost;")
    line()
    line("// Get ability power and aspect")
    line("UINT8 power = _ability_power[ability_id];")
    line("UINT8 aspect = _ability_aspect[ability_id];")
    line("UINT8 is_healing = _ability_is_healing[ability_id];")
    line()
    line("// Apply to each target")
    line("for (UINT8 t = 0u; t < action->target_count; t++) {")
    indent++
    line("UINT8 target_idx = action->targets[t];")
    line()
    line("if (is_healing) {")
    indent++
    line("// Healing: restore HP based on power")
    line("UINT16 heal_amt = (power * _combatant_hp_max[target_idx]) / 100u;")
    line("_combatant_hp[target_idx] += heal_amt;")
    line("if (_combatant_hp[target_idx] > _combatant_hp_max[target_idx]) {")
    indent++
    line("_combatant_hp[target_idx] = _combatant_hp_max[target_idx];")
    indent--
    line("}")
    indent--
    line("} else {")
    indent++
    line("// Damage: calculate based on aspect (physical/magical)")
    line("UINT16 atk = (aspect == 0u) ? _combatant_atk[actor_idx] : _combatant_matk[actor_idx];")
    line("UINT16 def = (aspect == 0u) ? _combatant_def[target_idx] : _combatant_mdef[target_idx];")
    line("UINT16 damage = (atk * power) / 100u;")
    line("damage = (damage > def / 2u) ? (damage - def / 2u) : 1u;")
    line()
    line("if (_combatant_hp[target_idx] > damage) {")
    indent++
    line("_combatant_hp[target_idx] -= damage;")
    indent--
    line("} else {")
    indent++
    line("_combatant_hp[target_idx] = 0u;")
    indent--
    line("}")
    indent--
    line("}")
    indent--
    line("}")
    line()
    line("_action_${name}_result = 1u;")
    indent--
    line("} else {")
    indent++
    line("// Not enough SP - action fails")
    line("_action_${name}_result = 0u;")
    indent--
    line("}")
    indent--
    line("}")
    line("break;")
    indent--

    line("case ACTION_TYPE_ITEM:")
    indent++
    line("{")
    indent++
    line("// Item use")
    line("UINT8 item_id = action->item_id;")
    line()
    line("// Apply item effect to first target")
    line("if (action->target_count > 0u) {")
    indent++
    line("UINT8 target_idx = action->targets[0];")
    line()
    line("// Item effects are stored in item data tables")
    line("// Get item effect type and value")
    line("UINT8 effect_type = _item_effect_type[item_id];")
    line("UINT16 effect_value = _item_effect_value[item_id];")
    line()
    line("switch (effect_type) {")
    indent++
    line("case 0u: // Heal HP")
    indent++
    line("_combatant_hp[target_idx] += effect_value;")
    line("if (_combatant_hp[target_idx] > _combatant_hp_max[target_idx]) {")
    indent++
    line("_combatant_hp[target_idx] = _combatant_hp_max[target_idx];")
    indent--
    line("}")
    line("break;")
    indent--
    line("case 1u: // Restore SP")
    indent++
    line("_combatant_sp[target_idx] += (UINT8)effect_value;")
    line("if (_combatant_sp[target_idx] > _combatant_sp_max[target_idx]) {")
    indent++
    line("_combatant_sp[target_idx] = _combatant_sp_max[target_idx];")
    indent--
    line("}")
    line("break;")
    indent--
    line("case 2u: // Cure status")
    indent++
    line("_combatant_status[target_idx] = 0u;")
    line("break;")
    indent--
    line("default:")
    indent++
    line("break;")
    indent--
    indent--
    line("}")
    indent--
    line("}")
    line()
    line("_action_${name}_result = 1u;")
    indent--
    line("}")
    line("break;")
    indent--

    line("case ACTION_TYPE_DEFEND:")
    indent++
    line("{")
    indent++
    line("// Defend - set defense flag on actor (halves incoming damage)")
    line("_combatant_defending[action->actor_index] = 1u;")
    line("_action_${name}_result = 1u;")
    indent--
    line("}")
    line("break;")
    indent--

    line("case ACTION_TYPE_FLEE:")
    indent++
    line("{")
    indent++
    line("// Flee attempt")
    line("// Calculate flee chance: base 50% + (party_agl - enemy_agl) * 2%")
    line("UINT8 flee_chance = 50u;")
    line("UINT8 actor_agl = _combatant_agl[action->actor_index];")
    line()
    line("// Roll random number (0-99)")
    line("UINT8 roll = rand() % 100u;")
    line()
    line("if (roll < flee_chance + actor_agl / 2u) {")
    indent++
    line("// Flee successful - set battle state to FLED")
    line("_action_${name}_result = 2u; // 2 = flee success")
    indent--
    line("} else {")
    indent++
    line("// Flee failed")
    line("_action_${name}_result = 0u;")
    indent--
    line("}")
    indent--
    line("}")
    line("break;")
    indent--

    line("case ACTION_TYPE_WAIT:")
    indent++
    line("{")
    indent++
    line("// Wait - skip turn, do nothing")
    line("_action_${name}_result = 1u;")
    indent--
    line("}")
    line("break;")
    indent--

    line("default:")
    indent++
    line("_action_${name}_result = 0u;")
    line("break;")
    indent--

    indent--
    line("}")

    // Execute onActionStart callback
    if (config.onActionStart.isNotEmpty()) {
        line()
        line("// Action start callback")
        config.onActionStart.forEach { generateStatement(it) }
    }

    indent--
    line("}")
    line()

    // Function to advance to next action
    line("// Advance to next action")
    line("static void _action_${name}_next(void) {")
    indent++

    // Execute onActionComplete callback
    if (config.onActionComplete.isNotEmpty()) {
        line("// Action complete callback")
        config.onActionComplete.forEach { generateStatement(it) }
        line()
    }

    line("_action_${name}_current++;")
    line("if (_action_${name}_current >= _action_${name}_count) {")
    indent++
    line("// All actions executed")
    line("_action_${name}_current = 0u;")
    line("_action_${name}_count = 0u;")
    indent--
    line("}")

    indent--
    line("}")
    line()

    // Function to clear queue
    line("// Clear action queue")
    line("static void _action_${name}_clear(void) {")
    indent++

    line("_action_${name}_count = 0u;")
    line("_action_${name}_current = 0u;")

    indent--
    line("}")
    line()
}

private fun GBDKCodeGenerator.generateActionQueueAdd(stmt: IRActionQueueAdd) {
    val name = stmt.pipelineName
    val type = "ACTION_TYPE_${stmt.actionType.name}"
    val actor = stmt.actorIndex
    val abilityId = stmt.abilityId ?: 0
    val itemId = stmt.itemId ?: 0
    val targets = stmt.targetIndices

    line("{")
    indent++
    line("UINT8 _targets[] = {${targets.joinToString(", ") { "${it}u" }}};")
    line(
        "_action_${name}_add($type, ${actor}u, ${targets.size}u, _targets, ${abilityId}u, ${itemId}u);"
    )
    indent--
    line("}")
}

private fun GBDKCodeGenerator.generateActionExecute(stmt: IRActionExecute) {
    line("_action_${stmt.pipelineName}_execute();")
}

private fun GBDKCodeGenerator.generateNextAction(stmt: IRNextAction) {
    line("_action_${stmt.pipelineName}_next();")
}

private fun GBDKCodeGenerator.generateActionQueueClear(stmt: IRActionQueueClear) {
    line("_action_${stmt.pipelineName}_clear();")
}
