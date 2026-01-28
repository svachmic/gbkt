/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.rpg

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.backend.gbdk.codegen.core.generateExpr
import io.github.gbkt.backend.gbdk.codegen.core.generateStatement
import io.github.gbkt.core.ir.IRApplyCriticalMultiplier
import io.github.gbkt.core.ir.IRApplyDamageVariance
import io.github.gbkt.core.ir.IRCombatFormulas
import io.github.gbkt.core.ir.IRCriticalCheck
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRFumbleCheck
import io.github.gbkt.core.ir.IRHitCheck
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.rpg.CombatFormulas
import io.github.gbkt.core.rpg.CriticalFormulaStrategy
import io.github.gbkt.core.rpg.DamageVarianceStrategy
import io.github.gbkt.core.rpg.HitFormulaStrategy

// =============================================================================
// COMBAT FORMULAS CODE GENERATION
// =============================================================================

/**
 * Handle combat formula IR statements.
 *
 * @return true if this was a combat formula statement and was handled, false otherwise
 */
internal fun GBDKCodeGenerator.generateCombatFormulasStatement(stmt: IRStatement): Boolean =
    when (stmt) {
        is IRCombatFormulas -> {
            generateCombatFormulasConfig(stmt.formulas)
            true
        }
        else -> false
    }

/**
 * Generate C expression for combat formula checks.
 *
 * @return the C expression string, or null if not a combat formula expression
 */
internal fun GBDKCodeGenerator.generateCombatFormulasExpr(expr: IRExpression): String? =
    when (expr) {
        is IRHitCheck -> "_combat_hit_check(${expr.attackerName}, ${expr.defenderName})"
        is IRCriticalCheck -> "_combat_crit_check()"
        is IRFumbleCheck -> "_combat_fumble_check()"
        is IRApplyDamageVariance ->
            "_combat_apply_variance(${generateExprInternal(expr.baseDamage)})"
        is IRApplyCriticalMultiplier ->
            "_combat_apply_crit(${generateExprInternal(expr.baseDamage)})"
        else -> null
    }

/** Helper to generate expressions internally. */
private fun GBDKCodeGenerator.generateExprInternal(expr: IRExpression): String {
    // Try combat formula expressions first
    generateCombatFormulasExpr(expr)?.let {
        return it
    }
    // Fall back to the main expression generator
    return generateExpr(expr)
}

/** Generate combat formulas configuration and helper functions. */
private fun GBDKCodeGenerator.generateCombatFormulasConfig(formulas: CombatFormulas) {
    line("// =============================================================================")
    line("// COMBAT FORMULAS SYSTEM")
    line("// =============================================================================")
    line()

    // Generate constants
    line("// Combat formula constants")
    line("#define COMBAT_CRIT_MULTIPLIER ${formulas.critMultiplier}u")
    line("#define COMBAT_FUMBLE_ENABLED ${if (formulas.fumbleEnabled) 1 else 0}u")
    line("#define COMBAT_FUMBLE_THRESHOLD ${formulas.fumbleThreshold}u")
    line()

    // Generate hit formula helpers
    generateHitFormula(formulas.hitFormula)

    // Generate critical formula helpers
    generateCriticalFormula(formulas.criticalFormula)

    // Generate damage variance helpers
    generateDamageVariance(formulas.damageVariance)

    // Generate fumble check if enabled
    if (formulas.fumbleEnabled) {
        generateFumbleCheck(formulas.fumbleThreshold)
    }

    // Generate convenience function for applying crit multiplier
    generateCritMultiplier(formulas.critMultiplier)
}

/** Generate hit formula function based on strategy. */
private fun GBDKCodeGenerator.generateHitFormula(strategy: HitFormulaStrategy) {
    line("// Hit check function")

    when (strategy) {
        is HitFormulaStrategy.AlwaysHit -> {
            line("// Strategy: Always Hit (no miss chance)")
            line("static inline UINT8 _combat_hit_check(UINT8 atk_idx, UINT8 def_idx) {")
            indent++
            line("(void)atk_idx; (void)def_idx; // Unused - always hits")
            line("return 1u;")
            indent--
            line("}")
        }

        is HitFormulaStrategy.D20Based -> {
            line("// Strategy: D20-based (roll + ATK vs DEF + base AC)")
            line("#define COMBAT_BASE_AC ${strategy.baseAC}u")
            line("static UINT8 _combat_hit_check(UINT8 atk_idx, UINT8 def_idx) {")
            indent++
            line("// Roll d20 (0-19) + 1 = 1-20")
            line("UINT8 roll = (UINT8)((rand() % 20u) + 1u);")
            line("// Get attack and defense stats from combatant arrays")
            line("UINT8 atk = _get_combatant_atk(atk_idx);")
            line("UINT8 def = _get_combatant_def(def_idx);")
            line("// Hit if roll + ATK > DEF + base AC")
            line("return (roll + atk > def + COMBAT_BASE_AC) ? 1u : 0u;")
            indent--
            line("}")
        }

        is HitFormulaStrategy.PercentageBased -> {
            line("// Strategy: Percentage-based hit chance")
            line("#define COMBAT_HIT_BASE ${strategy.baseChance}u")
            line("#define COMBAT_HIT_MIN ${strategy.minChance}u")
            line("#define COMBAT_HIT_MAX ${strategy.maxChance}u")
            line("#define COMBAT_HIT_PER_DIFF ${strategy.perDiff}u")
            line()
            line("static UINT8 _combat_hit_check(UINT8 atk_idx, UINT8 def_idx) {")
            indent++
            line("// Get attack and defense stats from combatant arrays")
            line("UINT8 atk = _get_combatant_atk(atk_idx);")
            line("UINT8 def = _get_combatant_def(def_idx);")
            line()
            line("// Calculate hit chance: base + (ATK - DEF) * perDiff")
            line("INT16 diff = (INT16)atk - (INT16)def;")
            line("INT16 chance = (INT16)COMBAT_HIT_BASE + (diff * (INT16)COMBAT_HIT_PER_DIFF);")
            line()
            line("// Clamp to min/max")
            line("if (chance < (INT16)COMBAT_HIT_MIN) chance = (INT16)COMBAT_HIT_MIN;")
            line("if (chance > (INT16)COMBAT_HIT_MAX) chance = (INT16)COMBAT_HIT_MAX;")
            line()
            line("// Roll d100 and check")
            line("UINT8 roll = (UINT8)(rand() % 100u);")
            line("return (roll < (UINT8)chance) ? 1u : 0u;")
            indent--
            line("}")
        }

        is HitFormulaStrategy.AgilityBased -> {
            line("// Strategy: Agility-based hit chance (evasion system)")
            line("#define COMBAT_HIT_BASE ${strategy.baseChance}u")
            line("#define COMBAT_HIT_MIN ${strategy.minChance}u")
            line("#define COMBAT_HIT_MAX ${strategy.maxChance}u")
            line()
            line("static UINT8 _combat_hit_check(UINT8 atk_idx, UINT8 def_idx) {")
            indent++
            line("// Get agility stats from combatant arrays")
            line("UINT8 atk_agl = _get_combatant_agl(atk_idx);")
            line("UINT8 def_agl = _get_combatant_agl(def_idx);")
            line()
            line("// Calculate hit chance based on agility difference")
            line("INT16 diff = (INT16)atk_agl - (INT16)def_agl;")
            line("INT16 chance = (INT16)COMBAT_HIT_BASE + diff;")
            line()
            line("// Clamp to min/max")
            line("if (chance < (INT16)COMBAT_HIT_MIN) chance = (INT16)COMBAT_HIT_MIN;")
            line("if (chance > (INT16)COMBAT_HIT_MAX) chance = (INT16)COMBAT_HIT_MAX;")
            line()
            line("// Roll d100 and check")
            line("UINT8 roll = (UINT8)(rand() % 100u);")
            line("return (roll < (UINT8)chance) ? 1u : 0u;")
            indent--
            line("}")
        }

        is HitFormulaStrategy.Custom -> {
            line("// Strategy: Custom hit formula")
            line("static UINT8 _combat_hit_check(UINT8 atk_idx, UINT8 def_idx) {")
            indent++
            line("(void)atk_idx; (void)def_idx;")
            // Generate custom statements
            for (stmt in strategy.formulaStatements) {
                generateStatement(stmt)
            }
            line("return 1u; // Default if custom logic doesn't return")
            indent--
            line("}")
        }
    }
    line()
}

/** Generate critical formula function based on strategy. */
private fun GBDKCodeGenerator.generateCriticalFormula(strategy: CriticalFormulaStrategy) {
    line("// Critical hit check function")

    when (strategy) {
        is CriticalFormulaStrategy.NoCrits -> {
            line("// Strategy: No critical hits")
            line("static inline UINT8 _combat_crit_check(void) {")
            indent++
            line("return 0u;")
            indent--
            line("}")
        }

        is CriticalFormulaStrategy.FlatChance -> {
            line("// Strategy: Flat ${strategy.chance}% crit chance")
            line("#define COMBAT_CRIT_CHANCE ${strategy.chance}u")
            line("static UINT8 _combat_crit_check(void) {")
            indent++
            line("UINT8 roll = (UINT8)(rand() % 100u);")
            line("return (roll < COMBAT_CRIT_CHANCE) ? 1u : 0u;")
            indent--
            line("}")
        }

        is CriticalFormulaStrategy.HighRoll -> {
            line(
                "// Strategy: Critical on high roll (${strategy.threshold}+ on d${strategy.dieSize})"
            )
            line("#define COMBAT_CRIT_THRESHOLD ${strategy.threshold}u")
            line("#define COMBAT_CRIT_DIE_SIZE ${strategy.dieSize}u")
            line("static UINT8 _combat_crit_roll = 0u; // Stores last roll for crit check")
            line()
            line("static UINT8 _combat_crit_check(void) {")
            indent++
            line("// Roll is done during hit check, stored in _combat_crit_roll")
            line("return (_combat_crit_roll >= COMBAT_CRIT_THRESHOLD) ? 1u : 0u;")
            indent--
            line("}")
        }

        is CriticalFormulaStrategy.Custom -> {
            line("// Strategy: Custom critical formula")
            line("static UINT8 _combat_crit_check(void) {")
            indent++
            for (stmt in strategy.formulaStatements) {
                generateStatement(stmt)
            }
            line("return 0u; // Default if custom logic doesn't return")
            indent--
            line("}")
        }
    }
    line()
}

/** Generate damage variance function based on strategy. */
private fun GBDKCodeGenerator.generateDamageVariance(strategy: DamageVarianceStrategy) {
    line("// Damage variance function")

    when (strategy) {
        is DamageVarianceStrategy.NoVariance -> {
            line("// Strategy: No variance (exact damage)")
            line("static inline UINT16 _combat_apply_variance(UINT16 damage) {")
            indent++
            line("return damage;")
            indent--
            line("}")
        }

        is DamageVarianceStrategy.PercentageVariance -> {
            val halfVariance = strategy.variancePercent / 2
            line(
                "// Strategy: ±${halfVariance}% variance (${strategy.variancePercent}% total range)"
            )
            line("#define COMBAT_VARIANCE_RANGE ${strategy.variancePercent}u")
            line("#define COMBAT_VARIANCE_HALF ${halfVariance}u")
            line()
            line("static UINT16 _combat_apply_variance(UINT16 damage) {")
            indent++
            line("// Roll variance modifier: 100 - half to 100 + half")
            line("UINT8 roll = (UINT8)(rand() % (COMBAT_VARIANCE_RANGE + 1u));")
            line("UINT16 modifier = (UINT16)(100u - COMBAT_VARIANCE_HALF + roll);")
            line("return (damage * modifier) / 100u;")
            indent--
            line("}")
        }

        is DamageVarianceStrategy.MultiplierTable -> {
            val range = strategy.max - strategy.min
            line("// Strategy: Multiplier table (${strategy.min}% to ${strategy.max}%)")
            line("#define COMBAT_MULT_MIN ${strategy.min}u")
            line("#define COMBAT_MULT_MAX ${strategy.max}u")
            line("#define COMBAT_MULT_RANGE ${range}u")
            line()
            line("// D&D-style damage multiplier table (like Dragon's damage_roll_modifier)")
            line("static const UINT8 _combat_damage_mult[16] = {")
            indent++
            // Generate a 16-entry lookup table spanning min to max
            val step = range / 15.0
            val entries = (0..15).map { i -> strategy.min + (i * step).toInt() }
            line(entries.joinToString(", ") { "${it}u" })
            indent--
            line("};")
            line()
            line("static UINT16 _combat_apply_variance(UINT16 damage) {")
            indent++
            line("UINT8 roll = (UINT8)(rand() % 16u);")
            line("UINT16 modifier = (UINT16)_combat_damage_mult[roll];")
            line("return (damage * modifier) / 100u;")
            indent--
            line("}")
        }
    }
    line()
}

/** Generate fumble check function. */
@Suppress("UNUSED_PARAMETER") // threshold used via COMBAT_FUMBLE_THRESHOLD constant
private fun GBDKCodeGenerator.generateFumbleCheck(threshold: Int) {
    line("// Fumble check function")
    line("static UINT8 _combat_last_roll = 0u; // Stores last hit roll for fumble check")
    line()
    line("static UINT8 _combat_fumble_check(void) {")
    indent++
    line("return (_combat_last_roll <= COMBAT_FUMBLE_THRESHOLD) ? 1u : 0u;")
    indent--
    line("}")
    line()
}

/** Generate critical multiplier function. */
@Suppress("UNUSED_PARAMETER") // multiplier used via COMBAT_CRIT_MULTIPLIER constant
private fun GBDKCodeGenerator.generateCritMultiplier(multiplier: Int) {
    line("// Apply critical multiplier to damage")
    line("static inline UINT16 _combat_apply_crit(UINT16 damage) {")
    indent++
    line("return (damage * COMBAT_CRIT_MULTIPLIER) / 100u;")
    indent--
    line("}")
    line()
}
