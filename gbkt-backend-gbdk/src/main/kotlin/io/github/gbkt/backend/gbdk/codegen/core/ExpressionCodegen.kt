/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.core

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.backend.gbdk.codegen.features.generateMixerExpr
import io.github.gbkt.backend.gbdk.codegen.rpg.generateAbilityExpr
import io.github.gbkt.backend.gbdk.codegen.rpg.generateActionExecutionExpr
import io.github.gbkt.backend.gbdk.codegen.rpg.generateBattleExpr
import io.github.gbkt.backend.gbdk.codegen.rpg.generateBattleMenuExpr
import io.github.gbkt.backend.gbdk.codegen.rpg.generateCombatFormulasExpr
import io.github.gbkt.backend.gbdk.codegen.rpg.generateCombatStateExpr
import io.github.gbkt.backend.gbdk.codegen.rpg.generateCombatTraitsExpr
import io.github.gbkt.backend.gbdk.codegen.rpg.generateDamageExpr
import io.github.gbkt.backend.gbdk.codegen.rpg.generateEquipmentExpr
import io.github.gbkt.backend.gbdk.codegen.rpg.generateLevelingExpr
import io.github.gbkt.backend.gbdk.codegen.rpg.generateMonsterExpr
import io.github.gbkt.backend.gbdk.codegen.rpg.generateStatusEffectExpr
import io.github.gbkt.backend.gbdk.codegen.rpg.generateTargetSelectionExpr
import io.github.gbkt.backend.gbdk.codegen.rpg.generateTurnOrderExpr
import io.github.gbkt.backend.gbdk.codegen.rpg.statVarName
import io.github.gbkt.backend.gbdk.codegen.world.generateFlagsExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.GBVar
import io.github.gbkt.core.ir.IRAIEnemyCountCheck
import io.github.gbkt.core.ir.IRAliveMonsterCount
import io.github.gbkt.core.ir.IRAllMonstersDefeated
import io.github.gbkt.core.ir.IRApplyCriticalMultiplier
import io.github.gbkt.core.ir.IRApplyDamageVariance
import io.github.gbkt.core.ir.IRArrayAccess
import io.github.gbkt.core.ir.IRBattleMenuCursor
import io.github.gbkt.core.ir.IRBattleMenuGetType
import io.github.gbkt.core.ir.IRBattleMenuIsActive
import io.github.gbkt.core.ir.IRBattleMenuIsVisible
import io.github.gbkt.core.ir.IRBinary
import io.github.gbkt.core.ir.IRCallExpr
import io.github.gbkt.core.ir.IRCameraX
import io.github.gbkt.core.ir.IRCameraY
import io.github.gbkt.core.ir.IRCanAct
import io.github.gbkt.core.ir.IRCanDamage
import io.github.gbkt.core.ir.IRCanUseAbility
import io.github.gbkt.core.ir.IRCombatIsState
import io.github.gbkt.core.ir.IRCombatPartyCount
import io.github.gbkt.core.ir.IRCriticalCheck
import io.github.gbkt.core.ir.IRCutsceneIsComplete
import io.github.gbkt.core.ir.IRCutsceneIsPlaying
import io.github.gbkt.core.ir.IRDamageCalculate
import io.github.gbkt.core.ir.IRDialogIsActive
import io.github.gbkt.core.ir.IRDialogIsComplete
import io.github.gbkt.core.ir.IREntityCanAct
import io.github.gbkt.core.ir.IREntityCollision
import io.github.gbkt.core.ir.IREntityGetHp
import io.github.gbkt.core.ir.IREntityGetHpPercent
import io.github.gbkt.core.ir.IREntityGetMaxHp
import io.github.gbkt.core.ir.IREntityHasKnockback
import io.github.gbkt.core.ir.IREntityIsDead
import io.github.gbkt.core.ir.IREntityIsInvincible
import io.github.gbkt.core.ir.IREntityIsStunned
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRFlagIsSet
import io.github.gbkt.core.ir.IRFumbleCheck
import io.github.gbkt.core.ir.IRGetAbilityPower
import io.github.gbkt.core.ir.IRGetAbilitySpCost
import io.github.gbkt.core.ir.IRGetActionCount
import io.github.gbkt.core.ir.IRGetBattleState
import io.github.gbkt.core.ir.IRGetCombatState
import io.github.gbkt.core.ir.IRGetCurrentActionType
import io.github.gbkt.core.ir.IRGetCurrentActorIndex
import io.github.gbkt.core.ir.IRGetCurrentCombatantIndex
import io.github.gbkt.core.ir.IRGetCurrentExp
import io.github.gbkt.core.ir.IRGetCurrentInitiative
import io.github.gbkt.core.ir.IRGetCurrentTargetCount
import io.github.gbkt.core.ir.IRGetEffectiveStat
import io.github.gbkt.core.ir.IRGetEquipmentBonus
import io.github.gbkt.core.ir.IRGetEquippedItem
import io.github.gbkt.core.ir.IRGetExpForLevel
import io.github.gbkt.core.ir.IRGetExpToNextLevel
import io.github.gbkt.core.ir.IRGetLevel
import io.github.gbkt.core.ir.IRGetRoundNumber
import io.github.gbkt.core.ir.IRGetSelectedTargetCount
import io.github.gbkt.core.ir.IRGetSelectedTargetIndex
import io.github.gbkt.core.ir.IRGetTurnCount
import io.github.gbkt.core.ir.IRHasAbility
import io.github.gbkt.core.ir.IRHasEquippedItem
import io.github.gbkt.core.ir.IRHasMonsterInBattle
import io.github.gbkt.core.ir.IRHasQueuedActions
import io.github.gbkt.core.ir.IRHasStatusEffect
import io.github.gbkt.core.ir.IRHitCheck
import io.github.gbkt.core.ir.IRInputBufferActive
import io.github.gbkt.core.ir.IRInputBufferConsumed
import io.github.gbkt.core.ir.IRInventoryEmptySlots
import io.github.gbkt.core.ir.IRInventoryGetQuantity
import io.github.gbkt.core.ir.IRInventoryHasItem
import io.github.gbkt.core.ir.IRInventoryIsEmpty
import io.github.gbkt.core.ir.IRInventoryIsFull
import io.github.gbkt.core.ir.IRIsBattleState
import io.github.gbkt.core.ir.IRIsCurrentCombatantParty
import io.github.gbkt.core.ir.IRIsEquipped
import io.github.gbkt.core.ir.IRIsInBattle
import io.github.gbkt.core.ir.IRIsInCombatState
import io.github.gbkt.core.ir.IRIsMaxLevel
import io.github.gbkt.core.ir.IRIsRoundComplete
import io.github.gbkt.core.ir.IRIsTargetAlive
import io.github.gbkt.core.ir.IRIsTargetEnemy
import io.github.gbkt.core.ir.IRIsTargetSelectionActive
import io.github.gbkt.core.ir.IRLinkConnected
import io.github.gbkt.core.ir.IRLinkHasData
import io.github.gbkt.core.ir.IRLinkIsMaster
import io.github.gbkt.core.ir.IRLinkReceivedData
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRMenuCursorX
import io.github.gbkt.core.ir.IRMenuCursorY
import io.github.gbkt.core.ir.IRMenuIsActive
import io.github.gbkt.core.ir.IRMenuIsVisible
import io.github.gbkt.core.ir.IRMenuSelectedIndex
import io.github.gbkt.core.ir.IRMixerGroupIsFading
import io.github.gbkt.core.ir.IRMixerGroupIsMuted
import io.github.gbkt.core.ir.IRMixerGroupVolume
import io.github.gbkt.core.ir.IRMonsterGetHp
import io.github.gbkt.core.ir.IRMonsterGetHpPercent
import io.github.gbkt.core.ir.IRMonsterGetMaxHp
import io.github.gbkt.core.ir.IRMonsterHasAlly
import io.github.gbkt.core.ir.IRMonsterHasSpecialCharge
import io.github.gbkt.core.ir.IRMonsterHpCheck
import io.github.gbkt.core.ir.IRMonsterIsAlive
import io.github.gbkt.core.ir.IRMonsterIsDefending
import io.github.gbkt.core.ir.IRMonsterTargetHasEffect
import io.github.gbkt.core.ir.IRNavGridIsWalkable
import io.github.gbkt.core.ir.IRPathAtWaypoint
import io.github.gbkt.core.ir.IRPathCurrentIndex
import io.github.gbkt.core.ir.IRPathDirectionX
import io.github.gbkt.core.ir.IRPathDirectionY
import io.github.gbkt.core.ir.IRPathFound
import io.github.gbkt.core.ir.IRPathHasNext
import io.github.gbkt.core.ir.IRPathLength
import io.github.gbkt.core.ir.IRPathNextX
import io.github.gbkt.core.ir.IRPathNextY
import io.github.gbkt.core.ir.IRPoolActiveCount
import io.github.gbkt.core.ir.IRPoolEntityVar
import io.github.gbkt.core.ir.IRPoolHasSpace
import io.github.gbkt.core.ir.IRPoolIsFull
import io.github.gbkt.core.ir.IRPoolPathAtTarget
import io.github.gbkt.core.ir.IRRandomChance
import io.github.gbkt.core.ir.IRSaveArrayAccess
import io.github.gbkt.core.ir.IRSaveExists
import io.github.gbkt.core.ir.IRSaveFieldRead
import io.github.gbkt.core.ir.IRStatIsFull
import io.github.gbkt.core.ir.IRStatIsZero
import io.github.gbkt.core.ir.IRStatRead
import io.github.gbkt.core.ir.IRStatusEffectDuration
import io.github.gbkt.core.ir.IRStatusEffectStacks
import io.github.gbkt.core.ir.IRTernary
import io.github.gbkt.core.ir.IRTransitionActive
import io.github.gbkt.core.ir.IRUnary
import io.github.gbkt.core.ir.IRVar
import io.github.gbkt.core.ir.UnaryOp

/**
 * Expression code generation for IR expressions.
 *
 * Handles all IRExpression types and generates corresponding C code. Includes constant folding,
 * strength reduction, and operator precedence handling.
 */

// =============================================================================
// EXPRESSION GENERATION
// =============================================================================

/**
 * Helper to ensure delegated expression generators return non-null results. Throws a descriptive
 * error if the delegated generator returns null, which indicates a bug in the code generator.
 */
private fun requireExpr(result: String?, expr: IRExpression, context: String): String =
    result
        ?: error(
            "Code generation failed for expression: ${expr::class.simpleName} in $context. " +
                "This is likely a bug in the code generator - the IR node type is not handled."
        )

internal fun GBDKCodeGenerator.generateExpr(expr: IRExpression): String {
    // First, try constant folding
    val folded = foldConstants(expr)
    val exprToGenerate = folded ?: expr

    return generateExprWithPrecedence(exprToGenerate, null, false)
}

private fun GBDKCodeGenerator.generateExprWithPrecedence(
    expr: IRExpression,
    parentOp: BinaryOp?,
    isLeft: Boolean,
): String =
    when (expr) {
        is IRLiteral ->
            when (val v = expr.value) {
                is Int -> if (v >= 0) "${v}u" else "$v"
                is String -> "\"$v\""
                else -> v.toString()
            }
        is IRVar -> expr.name
        is IRBinary -> {
            val l = generateExprWithPrecedence(expr.left, expr.op, true)
            val r = generateExprWithPrecedence(expr.right, expr.op, false)
            val result = "$l ${expr.op.c} $r"
            // Only add parentheses if needed based on precedence
            if (parentOp != null && needsParens(expr, parentOp, isLeft)) {
                "($result)"
            } else {
                result
            }
        }
        is IRUnary -> {
            val inner = generateExprWithPrecedence(expr.operand, null, false)
            // Wrap the operand in parens if it's a binary expression (to ensure correct
            // precedence)
            // e.g., -(a + b) needs parens around (a + b), not around the whole thing
            val operand = if (expr.operand is IRBinary) "($inner)" else inner
            "${expr.op.c}$operand"
        }
        is IRCallExpr -> {
            val args = expr.args.joinToString(", ") { generateExpr(it) }
            "${expr.function}($args)"
        }
        is IRTernary -> {
            val c = generateExprWithPrecedence(expr.cond, null, false)
            val t = generateExprWithPrecedence(expr.then, null, false)
            val e = generateExprWithPrecedence(expr.otherwise, null, false)
            val result = "$c ? $t : $e"
            // Ternary has low precedence, usually needs parens
            if (parentOp != null) {
                "($result)"
            } else {
                result
            }
        }
        is IRArrayAccess -> {
            val array = game.arrays.find { it.name == expr.array }
            if (array != null) {
                val getter =
                    when (array.elementType) {
                        GBVar.VarType.U8 -> "_gb_array_get_u8"
                        GBVar.VarType.U16 -> "_gb_array_get_u16"
                        GBVar.VarType.I8 -> "_gb_array_get_i8"
                        GBVar.VarType.I16 -> "_gb_array_get_i16"
                    }
                "$getter(${expr.array}, ${generateExpr(expr.index)}, ${array.size}, \"${expr.array}\")"
            } else {
                // Fallback for arrays we can't find (shouldn't happen)
                "${expr.array}[${generateExpr(expr.index)}]"
            }
        }
        is IRSaveFieldRead -> "${expr.saveName}_data.${expr.fieldName}"
        is IRSaveExists -> "${expr.saveName}_validate(${generateExpr(expr.slot)})"
        is IRSaveArrayAccess ->
            "${expr.saveName}_data.${expr.fieldName}[${generateExpr(expr.index)}]"
        is IRDialogIsActive -> "_${expr.dialogName}_visible"
        is IRDialogIsComplete -> "_${expr.dialogName}_complete"
        is IRMenuIsVisible -> "_${expr.menuName}_visible"
        is IRMenuIsActive -> "_${expr.menuName}_active"
        is IRMenuSelectedIndex -> "_${expr.menuName}_cursor"
        is IRMenuCursorX -> "_${expr.menuName}_cursor_x"
        is IRMenuCursorY -> "_${expr.menuName}_cursor_y"
        is IRPoolActiveCount -> "${expr.poolName}_pool_count"
        is IRPoolHasSpace -> {
            val pool = game.pools.find { it.name == expr.poolName }
            "(${expr.poolName}_pool_count < ${pool?.size ?: 8})"
        }
        is IRPoolIsFull -> {
            val pool = game.pools.find { it.name == expr.poolName }
            "(${expr.poolName}_pool_count >= ${pool?.size ?: 8})"
        }
        is IRPoolEntityVar -> "${expr.poolName}_${expr.fieldName}[${expr.indexVar}]"
        is IRCameraX -> "_camera_x"
        is IRCameraY -> "_camera_y"
        is IRTransitionActive -> "(_transition_type != TRANS_NONE)"
        is IRPathFound -> "_path_found"
        is IRPathHasNext -> "(_path_current < _path_length)"
        is IRPathNextX -> "_path_waypoints[_path_current * 2]"
        is IRPathNextY -> "_path_waypoints[_path_current * 2 + 1]"
        is IRPathLength -> "_path_length"
        is IRPathCurrentIndex -> "_path_current"
        is IRPathDirectionX -> "_path_direction_x(${generateExpr(expr.currentX)})"
        is IRPathDirectionY -> "_path_direction_y(${generateExpr(expr.currentY)})"
        is IRPathAtWaypoint ->
            "_path_at_waypoint(${generateExpr(expr.entityX)}, ${generateExpr(expr.entityY)}, ${expr.threshold})"
        is IRNavGridIsWalkable ->
            "_navgrid_is_walkable(${expr.gridName}_navgrid, ${generateExpr(expr.x)}, ${generateExpr(expr.y)})"
        is IRPoolPathAtTarget -> "(_${expr.poolName}_at_target[${expr.entityIndex}])"

        // Input buffer expressions
        is IRInputBufferActive -> "(${expr.bufferName} > 0u)"
        is IRInputBufferConsumed -> "_input_buffer_consume(&${expr.bufferName})"

        // Audio mixer expressions
        is IRMixerGroupVolume,
        is IRMixerGroupIsMuted,
        is IRMixerGroupIsFading -> generateMixerExpr(expr) ?: "0"

        // Link cable expressions
        is IRLinkConnected -> "_link_connected"
        is IRLinkHasData -> "_link_has_data"
        is IRLinkReceivedData -> "_link_received"
        is IRLinkIsMaster -> "_link_is_master"

        // Cutscene expressions
        is IRCutsceneIsPlaying -> "_${expr.cutsceneName}_playing"
        is IRCutsceneIsComplete -> "_${expr.cutsceneName}_complete"

        // RPG stat expressions
        is IRStatRead -> statVarName(expr.ownerName, expr.statType, expr.useMax)
        is IRStatIsZero -> "(${statVarName(expr.ownerName, expr.statType, false)} == 0u)"
        is IRStatIsFull -> {
            val current = statVarName(expr.ownerName, expr.statType, false)
            val max = statVarName(expr.ownerName, expr.statType, true)
            "($current >= $max)"
        }

        // Damage calculation expressions
        is IRDamageCalculate -> requireExpr(generateDamageExpr(expr), expr, "damage calculation")

        // Combat formula expressions (hit/crit/fumble/variance)
        is IRHitCheck -> requireExpr(generateCombatFormulasExpr(expr), expr, "combat formulas")
        is IRCriticalCheck -> requireExpr(generateCombatFormulasExpr(expr), expr, "combat formulas")
        is IRFumbleCheck -> requireExpr(generateCombatFormulasExpr(expr), expr, "combat formulas")
        is IRApplyDamageVariance ->
            requireExpr(generateCombatFormulasExpr(expr), expr, "combat formulas")
        is IRApplyCriticalMultiplier ->
            requireExpr(generateCombatFormulasExpr(expr), expr, "combat formulas")

        // Status effect expressions
        is IRHasStatusEffect -> requireExpr(generateStatusEffectExpr(expr), expr, "status effect")
        is IRStatusEffectStacks ->
            requireExpr(generateStatusEffectExpr(expr), expr, "status effect")
        is IRStatusEffectDuration ->
            requireExpr(generateStatusEffectExpr(expr), expr, "status effect")
        is IRCanAct -> generateStatusEffectExpr(expr) ?: "1" // Default to can act

        // Combat state machine expressions
        is IRGetCombatState -> requireExpr(generateCombatStateExpr(expr), expr, "combat state")
        is IRIsInCombatState -> requireExpr(generateCombatStateExpr(expr), expr, "combat state")

        // Battle system expressions
        is IRGetBattleState -> requireExpr(generateBattleExpr(expr), expr, "battle system")
        is IRIsBattleState -> requireExpr(generateBattleExpr(expr), expr, "battle system")
        is IRIsInBattle -> requireExpr(generateBattleExpr(expr), expr, "battle system")

        // Combat action expressions
        is IRCombatPartyCount -> "_combat_party_count"
        is IRCombatIsState -> "(_combat_battle_state == ${expr.stateName})"

        // Turn order system expressions
        is IRGetCurrentCombatantIndex -> generateTurnOrderExpr(expr) ?: "0"
        is IRGetCurrentInitiative -> generateTurnOrderExpr(expr) ?: "0"
        is IRIsCurrentCombatantParty -> generateTurnOrderExpr(expr) ?: "0"
        is IRGetTurnCount -> generateTurnOrderExpr(expr) ?: "0"
        is IRGetRoundNumber -> generateTurnOrderExpr(expr) ?: "0"
        is IRIsRoundComplete -> generateTurnOrderExpr(expr) ?: "0"

        // Battle menu expressions
        is IRBattleMenuCursor -> generateBattleMenuExpr(expr) ?: "0"
        is IRBattleMenuIsVisible -> generateBattleMenuExpr(expr) ?: "0"
        is IRBattleMenuIsActive -> generateBattleMenuExpr(expr) ?: "0"
        is IRBattleMenuGetType -> generateBattleMenuExpr(expr) ?: "0"

        // Target selection expressions
        is IRIsTargetSelectionActive -> generateTargetSelectionExpr(expr) ?: "0"
        is IRGetSelectedTargetIndex -> generateTargetSelectionExpr(expr) ?: "0"
        is IRGetSelectedTargetCount -> generateTargetSelectionExpr(expr) ?: "0"
        is IRIsTargetAlive -> generateTargetSelectionExpr(expr) ?: "0"
        is IRIsTargetEnemy -> generateTargetSelectionExpr(expr) ?: "0"

        // Action execution expressions
        is IRHasQueuedActions -> generateActionExecutionExpr(expr) ?: "0"
        is IRGetActionCount -> generateActionExecutionExpr(expr) ?: "0"
        is IRGetCurrentActionType -> generateActionExecutionExpr(expr) ?: "0"
        is IRGetCurrentActorIndex -> generateActionExecutionExpr(expr) ?: "0"
        is IRGetCurrentTargetCount -> generateActionExecutionExpr(expr) ?: "0"

        // Combat traits expressions
        is IREntityIsInvincible -> generateCombatTraitsExpr(expr) ?: "0"
        is IREntityIsDead -> generateCombatTraitsExpr(expr) ?: "0"
        is IREntityIsStunned -> generateCombatTraitsExpr(expr) ?: "0"
        is IREntityGetHp -> generateCombatTraitsExpr(expr) ?: "0"
        is IREntityGetMaxHp -> generateCombatTraitsExpr(expr) ?: "0"
        is IREntityGetHpPercent -> generateCombatTraitsExpr(expr) ?: "0"
        is IREntityCanAct -> generateCombatTraitsExpr(expr) ?: "1"
        is IREntityHasKnockback -> generateCombatTraitsExpr(expr) ?: "0"
        is IREntityCollision -> generateCombatTraitsExpr(expr) ?: "0"
        is IRCanDamage -> generateCombatTraitsExpr(expr) ?: "0"

        // Monster expressions (delegated)
        is IRMonsterHpCheck,
        is IRRandomChance,
        is IRMonsterHasAlly,
        is IRAIEnemyCountCheck,
        is IRMonsterGetHp,
        is IRMonsterGetMaxHp,
        is IRMonsterGetHpPercent,
        is IRMonsterIsAlive,
        is IRMonsterIsDefending,
        is IRHasMonsterInBattle,
        is IRAliveMonsterCount,
        is IRAllMonstersDefeated,
        is IRMonsterHasSpecialCharge,
        is IRMonsterTargetHasEffect -> generateMonsterExpr(expr) ?: "0"

        // Flags expressions (delegated)
        is IRFlagIsSet -> generateFlagsExpr(expr) ?: "0"

        // Ability expressions (delegated)
        is IRCanUseAbility,
        is IRHasAbility,
        is IRGetAbilitySpCost,
        is IRGetAbilityPower -> generateAbilityExpr(expr) ?: "0"

        // Item and inventory expressions
        is IRInventoryHasItem -> {
            val invName = expr.inventory.id
            val invMaxSlots = "${invName.uppercase()}_MAX_SLOTS"
            val itemConst = "ITEM_${expr.item.id.uppercase()}"
            "inventory_has_item(${invName}_slots, $invMaxSlots, $itemConst, ${expr.quantity})"
        }
        is IRInventoryGetQuantity -> {
            val invName = expr.inventory.id
            val invMaxSlots = "${invName.uppercase()}_MAX_SLOTS"
            val itemConst = "ITEM_${expr.item.id.uppercase()}"
            "inventory_get_quantity(${invName}_slots, $invMaxSlots, $itemConst)"
        }
        is IRInventoryIsFull -> {
            val invName = expr.inventory.id
            val invMaxSlots = "${invName.uppercase()}_MAX_SLOTS"
            "inventory_is_full(${invName}_slots, $invMaxSlots)"
        }
        is IRInventoryIsEmpty -> {
            val invName = expr.inventory.id
            val invMaxSlots = "${invName.uppercase()}_MAX_SLOTS"
            "inventory_is_empty(${invName}_slots, $invMaxSlots)"
        }
        is IRInventoryEmptySlots -> {
            val invName = expr.inventory.id
            val invMaxSlots = "${invName.uppercase()}_MAX_SLOTS"
            "inventory_empty_slots(${invName}_slots, $invMaxSlots)"
        }
        is IRIsEquipped -> {
            val itemConst = "ITEM_${expr.item.id.uppercase()}"
            val charIdx =
                game.characters.indexOfFirst { it.name == expr.characterName }.takeIf { it >= 0 }
                    ?: 0
            "equipment_has_item(${charIdx}u, $itemConst)"
        }

        // Equipment expressions
        is IRGetEquippedItem -> requireExpr(generateEquipmentExpr(expr), expr, "equipment system")
        is IRHasEquippedItem -> requireExpr(generateEquipmentExpr(expr), expr, "equipment system")
        is IRGetEquipmentBonus -> requireExpr(generateEquipmentExpr(expr), expr, "equipment system")
        is IRGetEffectiveStat -> requireExpr(generateEquipmentExpr(expr), expr, "equipment system")

        // Leveling expressions
        is IRGetLevel -> requireExpr(generateLevelingExpr(expr), expr, "leveling system")
        is IRGetCurrentExp -> requireExpr(generateLevelingExpr(expr), expr, "leveling system")
        is IRGetExpToNextLevel -> requireExpr(generateLevelingExpr(expr), expr, "leveling system")
        is IRIsMaxLevel -> requireExpr(generateLevelingExpr(expr), expr, "leveling system")
        is IRGetExpForLevel -> requireExpr(generateLevelingExpr(expr), expr, "leveling system")

        else -> error("Unhandled IR expression type: ${expr::class.simpleName}")
    }

// =============================================================================
// OPERATOR PRECEDENCE
// =============================================================================

/**
 * Get operator precedence (higher number = higher precedence). Based on C operator precedence
 * rules.
 */
private fun BinaryOp.precedence(): Int =
    when (this) {
        BinaryOp.MUL,
        BinaryOp.DIV,
        BinaryOp.MOD -> 5
        BinaryOp.ADD,
        BinaryOp.SUB -> 4
        BinaryOp.SHL,
        BinaryOp.SHR -> 3
        BinaryOp.LT,
        BinaryOp.LTE,
        BinaryOp.GT,
        BinaryOp.GTE -> 2
        BinaryOp.EQ,
        BinaryOp.NEQ -> 1
        BinaryOp.AND -> 0
        BinaryOp.XOR -> -1
        BinaryOp.OR -> -2
        BinaryOp.LAND -> -3
        BinaryOp.LOR -> -4
    }

/** Check if an operator is non-commutative (order matters: a - b != b - a). */
private fun BinaryOp.isNonCommutative(): Boolean =
    when (this) {
        BinaryOp.SUB,
        BinaryOp.DIV,
        BinaryOp.MOD,
        BinaryOp.SHL,
        BinaryOp.SHR -> true
        else -> false
    }

/** Check if an expression needs parentheses when used as a left/right operand. */
private fun needsParens(expr: IRExpression, parentOp: BinaryOp, isLeft: Boolean): Boolean {
    return when (expr) {
        is IRBinary -> {
            val childPrec = expr.op.precedence()
            val parentPrec = parentOp.precedence()
            when {
                // Same precedence: need parens on right side for non-commutative operators
                // e.g., a - (b - c) needs parens, a + (b + c) doesn't
                childPrec == parentPrec -> {
                    if (isLeft) {
                        false
                    } else {
                        // Right side needs parens if parent is non-commutative
                        parentOp.isNonCommutative()
                    }
                }
                // Lower precedence child needs parens
                childPrec < parentPrec -> true
                // Higher precedence child doesn't need parens
                else -> false
            }
        }
        is IRUnary -> {
            // Unary operators (!, -, ~) have higher precedence than all binary operators in C
            // So a standalone unary expression never needs parens when used as a binary operand
            // e.g., !a && b, -x + y all work without parens around the unary part
            false
        }
        else -> false
    }
}

// =============================================================================
// CONSTANT FOLDING AND STRENGTH REDUCTION
// =============================================================================

/**
 * Constant folding: evaluate literal expressions at compile time. Returns the folded expression, or
 * null if no folding was possible. Division/modulo by zero returns null (don't fold) to preserve
 * runtime behavior.
 */
private fun foldConstants(expr: IRExpression): IRExpression? {
    return when (expr) {
        is IRLiteral -> expr // Already a literal
        is IRBinary -> {
            val leftFolded = foldConstants(expr.left)
            val rightFolded = foldConstants(expr.right)
            if (leftFolded is IRLiteral && rightFolded is IRLiteral) {
                // Both are literals, evaluate at compile time
                val leftVal = leftFolded.value
                val rightVal = rightFolded.value
                if (leftVal is Int && rightVal is Int) {
                    // Mask arithmetic results to 8 bits to simulate Game Boy u8 overflow behavior
                    // This prevents folding 255 + 1 to 256 (which doesn't fit in u8)
                    fun mask8(value: Int): Int = value and 0xFF
                    val result: Int? =
                        when (expr.op) {
                            BinaryOp.ADD -> mask8(leftVal + rightVal)
                            BinaryOp.SUB -> mask8(leftVal - rightVal)
                            BinaryOp.MUL -> mask8(leftVal * rightVal)
                            // Don't fold division/modulo by zero - let runtime handle it
                            BinaryOp.DIV -> if (rightVal != 0) mask8(leftVal / rightVal) else null
                            BinaryOp.MOD -> if (rightVal != 0) mask8(leftVal % rightVal) else null
                            BinaryOp.AND -> mask8(leftVal and rightVal)
                            BinaryOp.OR -> mask8(leftVal or rightVal)
                            BinaryOp.XOR -> mask8(leftVal xor rightVal)
                            // Validate shift amounts (0-7 for 8-bit values)
                            BinaryOp.SHL ->
                                if (rightVal in 0..7) mask8(leftVal shl rightVal) else null
                            BinaryOp.SHR ->
                                if (rightVal in 0..7) mask8(leftVal shr rightVal) else null
                            BinaryOp.EQ -> if (leftVal == rightVal) 1 else 0
                            BinaryOp.NEQ -> if (leftVal != rightVal) 1 else 0
                            BinaryOp.LT -> if (leftVal < rightVal) 1 else 0
                            BinaryOp.LTE -> if (leftVal <= rightVal) 1 else 0
                            BinaryOp.GT -> if (leftVal > rightVal) 1 else 0
                            BinaryOp.GTE -> if (leftVal >= rightVal) 1 else 0
                            BinaryOp.LAND -> if (leftVal != 0 && rightVal != 0) 1 else 0
                            BinaryOp.LOR -> if (leftVal != 0 || rightVal != 0) 1 else 0
                        }
                    // Return null if division by zero (don't fold)
                    result?.let { IRLiteral(it) }
                } else {
                    null // Can't fold non-integer literals
                }
            } else {
                // At least one side isn't a literal - try identity elimination
                val left = leftFolded ?: expr.left
                val right = rightFolded ?: expr.right

                // Identity eliminations: x + 0 = x, x * 1 = x, x * 0 = 0, etc.
                val simplified: IRExpression? =
                    when (expr.op) {
                        BinaryOp.ADD -> {
                            // x + 0 = x, 0 + x = x
                            when {
                                right is IRLiteral && right.value == 0 -> left
                                left is IRLiteral && left.value == 0 -> right
                                else -> null
                            }
                        }
                        BinaryOp.SUB -> {
                            // x - 0 = x, x - x = 0
                            when {
                                right is IRLiteral && right.value == 0 -> left
                                // x - x = 0 (when both sides are the same variable)
                                left is IRVar && right is IRVar && left.name == right.name ->
                                    IRLiteral(0)
                                else -> null
                            }
                        }
                        BinaryOp.MUL -> {
                            // x * 1 = x, 1 * x = x, x * 0 = 0, 0 * x = 0
                            // Strength reduction: x * 2^n = x << n
                            when {
                                right is IRLiteral && right.value == 1 -> left
                                left is IRLiteral && left.value == 1 -> right
                                right is IRLiteral && right.value == 0 -> IRLiteral(0)
                                left is IRLiteral && left.value == 0 -> IRLiteral(0)
                                // x * 2 = x << 1, x * 4 = x << 2, etc.
                                right is IRLiteral &&
                                    right.value is Int &&
                                    isPowerOfTwo(right.value as Int) ->
                                    IRBinary(
                                        left,
                                        BinaryOp.SHL,
                                        IRLiteral(log2(right.value as Int)),
                                    )
                                left is IRLiteral &&
                                    left.value is Int &&
                                    isPowerOfTwo(left.value as Int) ->
                                    IRBinary(
                                        right,
                                        BinaryOp.SHL,
                                        IRLiteral(log2(left.value as Int)),
                                    )
                                else -> null
                            }
                        }
                        BinaryOp.DIV -> {
                            // x / 1 = x
                            // Strength reduction: x / 2^n = x >> n (for unsigned values)
                            when {
                                right is IRLiteral && right.value == 1 -> left
                                // x / 2 = x >> 1, x / 4 = x >> 2, etc. (safe for unsigned Game
                                // Boy values)
                                right is IRLiteral &&
                                    right.value is Int &&
                                    isPowerOfTwo(right.value as Int) ->
                                    IRBinary(
                                        left,
                                        BinaryOp.SHR,
                                        IRLiteral(log2(right.value as Int)),
                                    )
                                else -> null
                            }
                        }
                        BinaryOp.AND -> {
                            // x & 0 = 0, 0 & x = 0
                            when {
                                right is IRLiteral && right.value == 0 -> IRLiteral(0)
                                left is IRLiteral && left.value == 0 -> IRLiteral(0)
                                // x & x = x
                                left is IRVar && right is IRVar && left.name == right.name -> left
                                else -> null
                            }
                        }
                        BinaryOp.OR -> {
                            // x | 0 = x, 0 | x = x
                            when {
                                right is IRLiteral && right.value == 0 -> left
                                left is IRLiteral && left.value == 0 -> right
                                // x | x = x
                                left is IRVar && right is IRVar && left.name == right.name -> left
                                else -> null
                            }
                        }
                        BinaryOp.XOR -> {
                            // x ^ 0 = x, 0 ^ x = x
                            when {
                                right is IRLiteral && right.value == 0 -> left
                                left is IRLiteral && left.value == 0 -> right
                                // x ^ x = 0
                                left is IRVar && right is IRVar && left.name == right.name ->
                                    IRLiteral(0)
                                else -> null
                            }
                        }
                        else -> null
                    }

                // Return simplified expression, or reconstructed binary if children changed
                simplified
                    ?: if (left != expr.left || right != expr.right) {
                        IRBinary(left, expr.op, right)
                    } else {
                        null
                    }
            }
        }
        is IRUnary -> {
            val operandFolded = foldConstants(expr.operand)

            // Double negation elimination: !(!x) = x, -(-x) = x, ~(~x) = x
            val inner = operandFolded ?: expr.operand
            if (inner is IRUnary && inner.op == expr.op) {
                // Same unary operator applied twice cancels out
                return inner.operand
            }

            if (operandFolded is IRLiteral && operandFolded.value is Int) {
                val value = operandFolded.value as Int
                val result =
                    when (expr.op) {
                        UnaryOp.NEG -> -value
                        UnaryOp.NOT -> if (value == 0) 1 else 0
                        UnaryOp.BNOT -> value.inv()
                    }
                IRLiteral(result)
            } else if (operandFolded != expr.operand) {
                IRUnary(expr.op, operandFolded ?: expr.operand)
            } else {
                null
            }
        }
        else -> null
    }
}

/** Check if n is a power of 2 (1, 2, 4, 8, 16, ...). Returns false for n <= 0. */
private fun isPowerOfTwo(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0

/** Calculate log base 2 of n. Assumes n is a positive power of 2. */
private fun log2(n: Int): Int {
    var value = n
    var result = 0
    while (value > 1) {
        value = value shr 1
        result++
    }
    return result
}
