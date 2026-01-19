/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen.rpg

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.rpg.CustomDamageCalculator
import io.github.gbkt.core.rpg.DamageCalculator
import io.github.gbkt.core.rpg.DamageFormulaType
import io.github.gbkt.core.rpg.DefenseConversionFormula
import io.github.gbkt.core.rpg.DefenseModel
import io.github.gbkt.core.rpg.MultiStatScalingCalculator
import io.github.gbkt.core.rpg.PercentageDefenseCalculator
import io.github.gbkt.core.rpg.StandardJrpgCalculator

// =============================================================================
// PLUGGABLE DAMAGE CALCULATOR CODE GENERATION
// =============================================================================

/**
 * Generate pluggable damage calculator system.
 *
 * Creates:
 * - Calculator index constants
 * - Calculator configuration tables
 * - Damage calculation dispatch functions
 * - Formula-specific implementations
 */
internal fun CodeGenerator.generateDamageCalculatorSystem() {
    val calculators = game.damageCalculators
    if (calculators.isEmpty()) return

    line("// =============================================================================")
    line("// PLUGGABLE DAMAGE CALCULATOR SYSTEM")
    line("// =============================================================================")
    line()

    // Generate calculator constants
    generateCalculatorConstants(calculators)

    // Generate formula type constants
    generateFormulaTypeConstants()

    // Generate defense model constants
    generateDefenseModelConstants()

    // Generate calculator configuration tables
    generateCalculatorConfigTables(calculators)

    // Generate individual calculator functions
    for (calculator in calculators) {
        generateCalculatorFunction(calculator)
    }

    // Generate dispatch function
    generateDamageDispatchFunction(calculators)

    // Generate helper functions
    generateDamageHelperFunctions()
}

/** Generate calculator index constants. */
private fun CodeGenerator.generateCalculatorConstants(calculators: List<DamageCalculator>) {
    line("// Damage calculator index constants")
    for ((index, calc) in calculators.withIndex()) {
        line("#define CALC_${calc.id.uppercase()} ${index}u")
    }
    line("#define CALC_COUNT ${calculators.size}u")
    line()
}

/** Generate formula type constants. */
private fun CodeGenerator.generateFormulaTypeConstants() {
    line("// Damage formula type constants")
    for ((index, type) in DamageFormulaType.entries.withIndex()) {
        line("#define FORMULA_${type.name} ${index}u")
    }
    line()
}

/** Generate defense model constants. */
private fun CodeGenerator.generateDefenseModelConstants() {
    line("// Defense model constants")
    for ((index, model) in DefenseModel.entries.withIndex()) {
        line("#define DEFENSE_${model.name} ${index}u")
    }
    line()
}

/** Generate calculator configuration tables. */
private fun CodeGenerator.generateCalculatorConfigTables(calculators: List<DamageCalculator>) {
    line("// Calculator configuration")
    line("static const UINT8 _calc_formula_type[CALC_COUNT] = {")
    indent++
    line(calculators.joinToString(", ") { "FORMULA_${it.formulaType.name}" })
    indent--
    line("};")
    line()

    line("static const UINT8 _calc_defense_model[CALC_COUNT] = {")
    indent++
    line(calculators.joinToString(", ") { "DEFENSE_${it.defenseModel.name}" })
    indent--
    line("};")
    line()

    line("static const UINT8 _calc_min_damage[CALC_COUNT] = {")
    indent++
    line(calculators.joinToString(", ") { "${it.minimumDamage}u" })
    indent--
    line("};")
    line()

    line("static const UINT16 _calc_max_damage[CALC_COUNT] = {")
    indent++
    line(calculators.joinToString(", ") { "${it.maximumDamage}u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _calc_variance[CALC_COUNT] = {")
    indent++
    line(calculators.joinToString(", ") { "${it.variance}u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _calc_crit_mult[CALC_COUNT] = {")
    indent++
    line(calculators.joinToString(", ") { "${it.criticalMultiplier}u" })
    indent--
    line("};")
    line()

    line("static const UINT8 _calc_apply_aspect[CALC_COUNT] = {")
    indent++
    line(calculators.joinToString(", ") { if (it.applyAspectModifiers) "1u" else "0u" })
    indent--
    line("};")
    line()
}

/** Generate individual calculator function. */
private fun CodeGenerator.generateCalculatorFunction(calculator: DamageCalculator) {
    val funcName = "_calc_${calculator.id}_damage"

    line("// Calculator: ${calculator.id} (${calculator.formulaType.name})")
    line("static UINT16 $funcName(UINT16 atk, UINT16 def, UINT8 power, UINT8 aspect) {")
    indent++

    when (calculator) {
        is StandardJrpgCalculator -> generateStandardJrpgFormula(calculator)
        is PercentageDefenseCalculator -> generatePercentageDefenseFormula(calculator)
        is MultiStatScalingCalculator -> generateMultiStatScalingFormula(calculator)
        is CustomDamageCalculator -> generateCustomFormula(calculator)
        else -> {
            // Fallback to standard formula
            line("// Fallback to standard formula")
            line("UINT16 base = (UINT16)atk * power / 100u;")
            line("return (base > def) ? (base - def) : 1u;")
        }
    }

    indent--
    line("}")
    line()
}

/** Generate standard JRPG formula implementation. */
private fun CodeGenerator.generateStandardJrpgFormula(calc: StandardJrpgCalculator) {
    line("// Standard JRPG: (ATK * power / 100) - (DEF / divisor)")
    line("UINT16 base = (UINT16)atk * power / 100u;")

    when (calc.defenseModel) {
        DefenseModel.FLAT_SUBTRACTION -> {
            if (calc.defenseDivisor > 1) {
                line("UINT16 effective_def = def / ${calc.defenseDivisor}u;")
                line("UINT16 damage = (base > effective_def) ? (base - effective_def) : 1u;")
            } else {
                line("UINT16 damage = (base > def) ? (base - def) : 1u;")
            }
        }
        DefenseModel.NONE -> {
            line("UINT16 damage = base;")
        }
        else -> {
            // For other models, still apply basic subtraction
            line("UINT16 damage = (base > def) ? (base - def) : 1u;")
        }
    }

    applyVarianceAndClamp(calc)
    line("(void)aspect; // May be used by aspect modifier")
    line("return damage;")
}

/** Generate percentage defense formula implementation. */
private fun CodeGenerator.generatePercentageDefenseFormula(calc: PercentageDefenseCalculator) {
    line("// Percentage Defense: damage * (100 - def%) / 100")
    line("UINT16 base = (UINT16)atk * power / 100u;")
    line()

    // Convert DEF to percentage based on conversion formula
    when (calc.defenseToPercentFormula) {
        DefenseConversionFormula.DIRECT -> {
            line("// Direct: DEF = defense%")
            line(
                "UINT8 def_percent = (def > ${calc.maxDefensePercent}u) ? ${calc.maxDefensePercent}u : (UINT8)def;"
            )
        }
        DefenseConversionFormula.SCALED -> {
            line("// Scaled: DEF scaled to percentage")
            line("UINT8 def_percent = (def * 100u / 255u);")
            line(
                "if (def_percent > ${calc.maxDefensePercent}u) def_percent = ${calc.maxDefensePercent}u;"
            )
        }
        DefenseConversionFormula.LOGARITHMIC -> {
            line("// Logarithmic: Diminishing returns")
            line("// Approximation using lookup or simple scaling")
            line("UINT8 def_percent = (def > 100u) ? 50u + (def - 100u) / 10u : def / 2u;")
            line(
                "if (def_percent > ${calc.maxDefensePercent}u) def_percent = ${calc.maxDefensePercent}u;"
            )
        }
        DefenseConversionFormula.ASYMPTOTIC -> {
            line("// Asymptotic: DEF / (DEF + constant) * 100")
            line("UINT8 def_percent = (UINT8)((UINT16)def * 100u / (def + 100u));")
            line(
                "if (def_percent > ${calc.maxDefensePercent}u) def_percent = ${calc.maxDefensePercent}u;"
            )
        }
    }

    line()
    line("UINT16 damage = base * (100u - def_percent) / 100u;")

    applyVarianceAndClamp(calc)
    line("(void)aspect; // May be used by aspect modifier")
    line("return damage;")
}

/** Generate multi-stat scaling formula implementation. */
private fun CodeGenerator.generateMultiStatScalingFormula(calc: MultiStatScalingCalculator) {
    line("// Multi-Stat Scaling: baseDamage + weighted stat contributions")
    line("UINT16 damage = ${calc.baseDamage}u;")
    line()

    // For multi-stat, we use the passed ATK but note that in practice
    // the caller should compute a weighted ATK from multiple stats
    line("// Base damage from weighted attack stat")
    line("damage += (UINT16)atk * power / 100u;")

    // Apply defense based on model
    when (calc.defenseModel) {
        DefenseModel.FLAT_SUBTRACTION -> {
            line("damage = (damage > def) ? (damage - def) : 1u;")
        }
        DefenseModel.PERCENTAGE_REDUCTION -> {
            line("UINT8 def_percent = (def > 75u) ? 75u : (UINT8)def;")
            line("damage = damage * (100u - def_percent) / 100u;")
        }
        DefenseModel.DIMINISHING_RETURNS -> {
            line("// Diminishing returns on defense")
            line("UINT16 effective_def = def * 100u / (def + 100u);")
            line("damage = (damage > effective_def) ? (damage - effective_def) : 1u;")
        }
        else -> {
            // No defense or threshold models
        }
    }

    applyVarianceAndClamp(calc)
    line("(void)aspect; // May be used by aspect modifier")
    line("return damage;")
}

/** Generate custom formula implementation. */
private fun CodeGenerator.generateCustomFormula(calc: CustomDamageCalculator) {
    line("// Custom formula")
    if (calc.customFormula.isNotEmpty()) {
        // The custom formula is raw C code that should use atk, def, power, aspect
        line("UINT16 damage = ${calc.customFormula};")
    } else {
        line("// No custom formula provided, using standard")
        line("UINT16 damage = (UINT16)atk * power / 100u;")
        line("damage = (damage > def) ? (damage - def) : 1u;")
    }

    applyVarianceAndClamp(calc)
    line("return damage;")
}

/** Apply variance and clamp to min/max. */
private fun CodeGenerator.applyVarianceAndClamp(calc: DamageCalculator) {
    if (calc.variance > 0) {
        line()
        line("// Apply variance (+-${calc.variance}%)")
        line("INT16 var = (INT16)(_rand() % ${calc.variance * 2 + 1}u) - ${calc.variance};")
        line("damage = (UINT16)((INT32)damage * (100 + var) / 100);")
    }

    line()
    line("// Clamp to min/max")
    if (calc.minimumDamage > 0) {
        line("if (damage < ${calc.minimumDamage}u) damage = ${calc.minimumDamage}u;")
    }
    if (calc.maximumDamage > 0) {
        line("if (damage > ${calc.maximumDamage}u) damage = ${calc.maximumDamage}u;")
    }
}

/** Generate damage dispatch function. */
private fun CodeGenerator.generateDamageDispatchFunction(calculators: List<DamageCalculator>) {
    line("// =============================================================================")
    line("// DAMAGE CALCULATION DISPATCH")
    line("// =============================================================================")
    line()

    line("// Calculate damage using specified calculator")
    line("static UINT16 _calculate_damage_with_calc(")
    line("    UINT8 calc_id, UINT16 atk, UINT16 def, UINT8 power, UINT8 aspect, UINT8 is_crit) {")
    indent++

    line("UINT16 damage;")
    line()

    line("// Dispatch to calculator")
    line("switch (calc_id) {")
    indent++

    for (calc in calculators) {
        line("case CALC_${calc.id.uppercase()}:")
        indent++
        line("damage = _calc_${calc.id}_damage(atk, def, power, aspect);")
        line("break;")
        indent--
    }

    line("default:")
    indent++
    line("// Fallback to standard formula")
    line("damage = (atk * power / 100u);")
    line("damage = (damage > def) ? (damage - def) : 1u;")
    line("break;")
    indent--

    indent--
    line("}")
    line()

    line("// Apply critical hit multiplier")
    line("if (is_crit && calc_id < CALC_COUNT) {")
    indent++
    line("damage = damage * _calc_crit_mult[calc_id] / 100u;")
    indent--
    line("}")
    line()

    line("// Apply aspect modifier if enabled")
    line("if (calc_id < CALC_COUNT && _calc_apply_aspect[calc_id]) {")
    indent++
    line("// Aspect modifier lookup would go here")
    line("// damage = damage * _get_aspect_mod(target, aspect) / 100u;")
    indent--
    line("}")
    line()

    line("return damage;")
    indent--
    line("}")
    line()
}

/** Generate damage helper functions. */
private fun CodeGenerator.generateDamageHelperFunctions() {
    line("// =============================================================================")
    line("// DAMAGE HELPER FUNCTIONS")
    line("// =============================================================================")
    line()

    // Default calculator (first one or fallback)
    line("// Default damage calculator ID")
    val defaultId = game.damageCalculators.firstOrNull()?.id?.uppercase() ?: "0"
    line("#define DEFAULT_DAMAGE_CALC CALC_$defaultId")
    line()

    line("// Convenience function using default calculator")
    line(
        "static UINT16 _calculate_damage_default(UINT16 atk, UINT16 def, UINT8 power, UINT8 aspect) {"
    )
    indent++
    line("return _calculate_damage_with_calc(DEFAULT_DAMAGE_CALC, atk, def, power, aspect, 0u);")
    indent--
    line("}")
    line()

    line("// Get calculator configuration")
    line("static UINT8 _get_calc_formula_type(UINT8 calc_id) {")
    indent++
    line("if (calc_id >= CALC_COUNT) return 0u;")
    line("return _calc_formula_type[calc_id];")
    indent--
    line("}")
    line()

    line("static UINT8 _get_calc_crit_mult(UINT8 calc_id) {")
    indent++
    line("if (calc_id >= CALC_COUNT) return 200u;")
    line("return _calc_crit_mult[calc_id];")
    indent--
    line("}")
    line()
}
