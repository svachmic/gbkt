/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen.rpg

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.codegen.core.generateExpr
import io.github.gbkt.core.ir.IRDamageCalculate
import io.github.gbkt.core.ir.IRDealDamage
import io.github.gbkt.core.ir.IRDealFlatDamage
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.StatType
import io.github.gbkt.core.rpg.Aspect
import io.github.gbkt.core.rpg.DamageCalculation

// =============================================================================
// DAMAGE CALCULATION CODE GENERATION
// =============================================================================

/**
 * Handle damage-related IR statements.
 *
 * @return true if this was a damage statement and was handled, false otherwise
 */
internal fun CodeGenerator.generateDamageStatement(stmt: IRStatement): Boolean =
    when (stmt) {
        is IRDealDamage -> {
            generateDealDamage(stmt)
            true
        }
        is IRDealFlatDamage -> {
            generateDealFlatDamage(stmt)
            true
        }
        else -> false
    }

/**
 * Generate C expression for damage calculation.
 *
 * @return the C expression string, or null if not a damage expression
 */
internal fun CodeGenerator.generateDamageExpr(expr: IRExpression): String? =
    when (expr) {
        is IRDamageCalculate -> generateDamageCalculateExpr(expr.calculation)
        else -> null
    }

/**
 * Generate C expression for damage calculation formula.
 *
 * Formula: max(1, ((ATK * power / 100) - DEF) * aspectMod / 100) + flatBonus
 */
private fun CodeGenerator.generateDamageCalculateExpr(calc: DamageCalculation): String {
    val attackStat = if (calc.usePhysical) StatType.ATK else StatType.MATK
    val defenseStat = if (calc.usePhysical) StatType.DEF else StatType.MDEF

    val attackVar = statVarName(calc.attackerName, attackStat, useMax = false)

    // Base damage: ATK * power / 100
    val baseDamage = "($attackVar * ${calc.power}u / 100u)"

    // Subtract defense if not ignoring
    val afterDefense =
        if (calc.ignoreDefense || calc.defenderName == null) {
            baseDamage
        } else {
            val defenseVar = statVarName(calc.defenderName, defenseStat, useMax = false)
            "($baseDamage > $defenseVar ? $baseDamage - $defenseVar : 1u)"
        }

    // Apply aspect modifier if defender is known and aspect is not PURE
    val afterAspect =
        if (calc.aspect == Aspect.PURE || calc.defenderName == null) {
            // PURE damage ignores resistances
            afterDefense
        } else {
            // Look up the defender's aspect modifier
            val defenderIndex = game.characters.indexOfFirst { it.name == calc.defenderName }
            if (defenderIndex >= 0) {
                // Defender is a known character - use static lookup
                "(($afterDefense) * _get_char_aspect_mod(${defenderIndex}u, ${calc.aspect.ordinal}u) / 100u)"
            } else {
                // Defender not in characters - might be a monster or dynamic target
                // For battle context, use the unified aspect mod lookup
                "(($afterDefense) * _get_aspect_mod(_current_target, ${calc.aspect.ordinal}u) / 100u)"
            }
        }

    // Add flat bonus
    val withBonus =
        if (calc.flatBonus > 0) {
            "($afterAspect + ${calc.flatBonus}u)"
        } else {
            afterAspect
        }

    // Ensure minimum damage of 1
    return "(($withBonus) > 0u ? ($withBonus) : 1u)"
}

/** Generate C code for dealing calculated damage. */
private fun CodeGenerator.generateDealDamage(stmt: IRDealDamage) {
    val calc = stmt.calculation
    val damageExpr = generateDamageCalculateExpr(calc)
    val targetHpVar = statVarName(stmt.targetName, StatType.HP, useMax = false)

    // Calculate damage and apply to target's HP
    lineWithSource("{", stmt.sourceLocation, targetHpVar)
    indent++
    line("UINT16 _damage = $damageExpr;")
    line("if ($targetHpVar >= _damage) $targetHpVar -= _damage; else $targetHpVar = 0;")
    indent--
    line("}")
}

/** Generate C code for dealing flat damage. */
private fun CodeGenerator.generateDealFlatDamage(stmt: IRDealFlatDamage) {
    val amount = generateExpr(stmt.amount)
    val targetHpVar = statVarName(stmt.targetName, StatType.HP, useMax = false)

    // For PURE aspect, no modifier applied
    val effectiveAmount =
        if (stmt.aspect == Aspect.PURE) {
            amount
        } else {
            // Apply aspect modifier based on target
            val targetIndex = game.characters.indexOfFirst { it.name == stmt.targetName }
            if (targetIndex >= 0) {
                // Target is a known character - use static lookup
                "(($amount) * _get_char_aspect_mod(${targetIndex}u, ${stmt.aspect.ordinal}u) / 100u)"
            } else {
                // Target not in characters - might be a monster or dynamic target
                // For battle context, use the unified aspect mod lookup
                "(($amount) * _get_aspect_mod(_current_target, ${stmt.aspect.ordinal}u) / 100u)"
            }
        }

    // Safe subtraction with floor at 0
    lineWithSource(
        "if ($targetHpVar >= $effectiveAmount) $targetHpVar -= $effectiveAmount; else $targetHpVar = 0;",
        stmt.sourceLocation,
        targetHpVar,
    )
}

/**
 * Generate the unified aspect modifier lookup function.
 *
 * This function handles both party members (idx < MAX_PARTY_SIZE) and monsters (idx >=
 * MAX_PARTY_SIZE) in battle context.
 */
internal fun CodeGenerator.generateUnifiedAspectModFunction() {
    if (game.characters.isEmpty() && game.monsters.isEmpty()) return

    line("// =============================================================================")
    line("// UNIFIED ASPECT MODIFIER LOOKUP")
    line("// =============================================================================")
    line()
    line("// Get aspect modifier for any combatant (party member or monster)")
    line("// target_idx: 0 to MAX_PARTY_SIZE-1 for party, MAX_PARTY_SIZE+ for enemies")
    line("static UINT8 _get_aspect_mod(UINT8 target_idx, UINT8 aspect) {")
    indent++
    line("if (target_idx < MAX_PARTY_SIZE) {")
    indent++
    line("// Party member - use character aspect table")
    line("return _get_char_aspect_mod(target_idx, aspect);")
    indent--
    line("} else {")
    indent++
    line("// Monster - look up monster type from battle enemy types array")
    line("UINT8 slot = target_idx - MAX_PARTY_SIZE;")
    line("if (slot >= MAX_ENEMIES) return 100u;")
    line("return _get_monster_aspect_mod(_battle_enemy_types[slot], aspect);")
    indent--
    line("}")
    indent--
    line("}")
    line()
}
