/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// PLUGGABLE DAMAGE CALCULATOR SYSTEM
// =============================================================================

/**
 * Damage formula type.
 *
 * Different games use different formulas for calculating damage.
 */
enum class DamageFormulaType {
    /** Standard JRPG: (ATK * power / 100) - DEF */
    STANDARD_JRPG,

    /** Percentage defense: damage * (100 - DEF%) / 100 */
    PERCENTAGE_DEFENSE,

    /** Final Fantasy style: multi-step with variance */
    FINAL_FANTASY,

    /** Pokemon style: ((2 * Level / 5 + 2) * Power * A/D) / 50 + 2 */
    POKEMON,

    /** Dark Souls style: weapon scaling with diminishing returns */
    SOULS_SCALING,

    /** Custom formula defined by game */
    CUSTOM,
}

/** Defense model for damage calculation. */
enum class DefenseModel {
    /** Defense reduces damage by flat amount */
    FLAT_SUBTRACTION,

    /** Defense reduces damage by percentage */
    PERCENTAGE_REDUCTION,

    /** Defense uses diminishing returns formula */
    DIMINISHING_RETURNS,

    /** Defense acts as damage threshold (ignore damage below threshold) */
    THRESHOLD,

    /** No defense calculation */
    NONE,
}

/**
 * Pluggable damage calculator interface.
 *
 * Allows games to define custom damage formulas instead of the hardcoded (ATK * power / 100) - DEF
 * formula.
 *
 * Usage:
 * ```kotlin
 * // Standard JRPG calculator (default behavior)
 * val standardCalc by damageCalculator {
 *     formula(DamageFormulaType.STANDARD_JRPG)
 *     defense(DefenseModel.FLAT_SUBTRACTION)
 * }
 *
 * // Percentage defense (Souls-like)
 * val soulsCalc by damageCalculator {
 *     formula(DamageFormulaType.SOULS_SCALING)
 *     defense(DefenseModel.DIMINISHING_RETURNS)
 *     scalingStat(ScalingStat.MULTIPLE) {
 *         stat("str", weight = 50)
 *         stat("dex", weight = 30)
 *         stat("int", weight = 20)
 *     }
 * }
 *
 * // Custom formula
 * val customCalc by damageCalculator {
 *     formula(DamageFormulaType.CUSTOM)
 *     customFormula { attacker, defender, power ->
 *         // Your custom calculation
 *         "(${attacker}_atk * $power / 50 - ${defender}_def / 2)"
 *     }
 * }
 * ```
 */
interface DamageCalculator {
    /** Unique identifier */
    val id: String

    /** Formula type */
    val formulaType: DamageFormulaType

    /** Defense model */
    val defenseModel: DefenseModel

    /** Whether to apply aspect modifiers */
    val applyAspectModifiers: Boolean

    /** Minimum damage (floor) */
    val minimumDamage: Int

    /** Maximum damage (cap, 0 = no cap) */
    val maximumDamage: Int

    /** Variance percentage (0 = no variance) */
    val variance: Int

    /** Critical hit multiplier (percentage, 100 = no bonus) */
    val criticalMultiplier: Int

    /** System index for code generation */
    var systemIndex: Int
}

// =============================================================================
// STANDARD JRPG CALCULATOR
// =============================================================================

/**
 * Standard JRPG damage calculator.
 *
 * Uses the formula: (ATK * power / 100) - DEF for physical, and (MATK * power / 100) - MDEF for
 * magical.
 */
class StandardJrpgCalculator(
    override val id: String,
    override val defenseModel: DefenseModel,
    override val applyAspectModifiers: Boolean,
    override val minimumDamage: Int,
    override val maximumDamage: Int,
    override val variance: Int,
    override val criticalMultiplier: Int,
    /** Defense divisor (default 1 = full defense) */
    val defenseDivisor: Int,
    override var systemIndex: Int = -1,
) : DamageCalculator {
    override val formulaType = DamageFormulaType.STANDARD_JRPG
}

// =============================================================================
// PERCENTAGE DEFENSE CALCULATOR
// =============================================================================

/**
 * Percentage defense calculator.
 *
 * Uses the formula: damage * (100 - defensePercent) / 100 where defensePercent is derived from DEF
 * stat.
 */
class PercentageDefenseCalculator(
    override val id: String,
    override val applyAspectModifiers: Boolean,
    override val minimumDamage: Int,
    override val maximumDamage: Int,
    override val variance: Int,
    override val criticalMultiplier: Int,
    /** Maximum defense percentage (cap) */
    val maxDefensePercent: Int,
    /** Formula to convert DEF stat to percentage */
    val defenseToPercentFormula: DefenseConversionFormula,
    override var systemIndex: Int = -1,
) : DamageCalculator {
    override val formulaType = DamageFormulaType.PERCENTAGE_DEFENSE
    override val defenseModel = DefenseModel.PERCENTAGE_REDUCTION
}

/** Formula for converting defense stat to percentage. */
enum class DefenseConversionFormula {
    /** Direct: DEF = defense% */
    DIRECT,

    /** Scaled: DEF * scaleFactor = defense% */
    SCALED,

    /** Logarithmic: log(DEF) * factor = defense% (diminishing returns) */
    LOGARITHMIC,

    /** Asymptotic: DEF / (DEF + constant) * 100 = defense% */
    ASYMPTOTIC,
}

// =============================================================================
// MULTI-STAT SCALING CALCULATOR
// =============================================================================

/**
 * Multi-stat scaling calculator.
 *
 * Allows damage to scale from multiple stats with different weights. Similar to Dark Souls weapon
 * scaling.
 */
class MultiStatScalingCalculator(
    override val id: String,
    override val defenseModel: DefenseModel,
    override val applyAspectModifiers: Boolean,
    override val minimumDamage: Int,
    override val maximumDamage: Int,
    override val variance: Int,
    override val criticalMultiplier: Int,
    /** Stats that contribute to damage with their weights */
    val scalingStats: List<StatScaling>,
    /** Base damage before scaling */
    val baseDamage: Int,
    /** Scaling formula type */
    val scalingFormula: ScalingFormula,
    override var systemIndex: Int = -1,
) : DamageCalculator {
    override val formulaType = DamageFormulaType.SOULS_SCALING
}

/** Stat contribution to damage. */
data class StatScaling(
    /** Stat name (hp, atk, def, etc. or custom stat) */
    val statName: String,
    /** Weight of this stat's contribution (percentage) */
    val weight: Int,
    /** Scaling grade (S/A/B/C/D/E or custom) */
    val grade: ScalingGrade = ScalingGrade.C,
)

/** Scaling grades (Souls-style). */
enum class ScalingGrade(val multiplier: Int) {
    S(140),
    A(120),
    B(100),
    C(80),
    D(60),
    E(40),
    NONE(0),
}

/** Scaling formula types. */
enum class ScalingFormula {
    /** Linear: baseDamage + sum(stat * weight * grade / 100) */
    LINEAR,

    /** Soft cap: diminishing returns after threshold */
    SOFT_CAP,

    /** Hard cap: no benefit after threshold */
    HARD_CAP,
}

// =============================================================================
// CUSTOM DAMAGE CALCULATOR
// =============================================================================

/** Custom damage calculator with user-defined formula. */
class CustomDamageCalculator(
    override val id: String,
    override val defenseModel: DefenseModel,
    override val applyAspectModifiers: Boolean,
    override val minimumDamage: Int,
    override val maximumDamage: Int,
    override val variance: Int,
    override val criticalMultiplier: Int,
    /** Custom formula expression (C code template) */
    val customFormula: String,
    override var systemIndex: Int = -1,
) : DamageCalculator {
    override val formulaType = DamageFormulaType.CUSTOM
}

// =============================================================================
// DAMAGE CALCULATOR BUILDERS
// =============================================================================

/** Property delegate for damage calculators. */
class DamageCalculatorDelegate(
    private val gameBuilder: GameBuilder,
    private val init: DamageCalculatorBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, DamageCalculator>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, DamageCalculator> {
        val builder = DamageCalculatorBuilder(property.name)
        builder.init()
        val calculator = builder.build()
        gameBuilder.registerDamageCalculator(calculator)

        return ReadOnlyProperty { _, _ -> calculator }
    }
}

/** Builder for damage calculators. */
@GbktDsl
class DamageCalculatorBuilder(private val calcId: String) {
    private var formulaType: DamageFormulaType = DamageFormulaType.STANDARD_JRPG
    private var defenseModel: DefenseModel = DefenseModel.FLAT_SUBTRACTION
    private var applyAspectModifiers: Boolean = true
    private var minimumDamage: Int = 1
    private var maximumDamage: Int = 0
    private var variance: Int = 0
    private var criticalMultiplier: Int = 200
    private var defenseDivisor: Int = 1
    private var maxDefensePercent: Int = 90
    private var defenseConversion: DefenseConversionFormula = DefenseConversionFormula.DIRECT
    private val scalingStats = mutableListOf<StatScaling>()
    private var baseDamage: Int = 0
    private var scalingFormula: ScalingFormula = ScalingFormula.LINEAR
    private var customFormula: String = ""

    /** Set the formula type */
    fun formula(type: DamageFormulaType) {
        formulaType = type
    }

    /** Set the defense model */
    fun defense(model: DefenseModel) {
        defenseModel = model
    }

    /** Whether to apply aspect (elemental) modifiers */
    fun applyAspects(apply: Boolean) {
        applyAspectModifiers = apply
    }

    /** Set minimum damage floor */
    fun minDamage(min: Int) {
        minimumDamage = min
    }

    /** Set maximum damage cap (0 = no cap) */
    fun maxDamage(max: Int) {
        maximumDamage = max
    }

    /** Set damage variance percentage */
    fun variance(percent: Int) {
        variance = percent
    }

    /** Set critical hit multiplier */
    fun critMultiplier(percent: Int) {
        criticalMultiplier = percent
    }

    /** Set defense divisor for JRPG formula */
    fun defenseDivisor(divisor: Int) {
        defenseDivisor = divisor
    }

    /** Set max defense percentage for percentage defense */
    fun maxDefensePercent(percent: Int) {
        maxDefensePercent = percent
    }

    /** Set defense to percentage conversion formula */
    fun defenseConversion(formula: DefenseConversionFormula) {
        defenseConversion = formula
    }

    /** Configure multi-stat scaling */
    fun scalingStats(init: ScalingStatsBuilder.() -> Unit) {
        val builder = ScalingStatsBuilder()
        builder.init()
        scalingStats.addAll(builder.build())
    }

    /** Set base damage for scaling calculator */
    fun baseDamage(damage: Int) {
        baseDamage = damage
    }

    /** Set scaling formula */
    fun scalingFormula(formula: ScalingFormula) {
        scalingFormula = formula
    }

    /** Set custom formula (C code template) */
    fun customFormula(formula: String) {
        customFormula = formula
    }

    internal fun build(): DamageCalculator {
        return when (formulaType) {
            DamageFormulaType.STANDARD_JRPG ->
                StandardJrpgCalculator(
                    id = calcId,
                    defenseModel = defenseModel,
                    applyAspectModifiers = applyAspectModifiers,
                    minimumDamage = minimumDamage,
                    maximumDamage = maximumDamage,
                    variance = variance,
                    criticalMultiplier = criticalMultiplier,
                    defenseDivisor = defenseDivisor,
                )
            DamageFormulaType.PERCENTAGE_DEFENSE ->
                PercentageDefenseCalculator(
                    id = calcId,
                    applyAspectModifiers = applyAspectModifiers,
                    minimumDamage = minimumDamage,
                    maximumDamage = maximumDamage,
                    variance = variance,
                    criticalMultiplier = criticalMultiplier,
                    maxDefensePercent = maxDefensePercent,
                    defenseToPercentFormula = defenseConversion,
                )
            DamageFormulaType.SOULS_SCALING,
            DamageFormulaType.FINAL_FANTASY,
            DamageFormulaType.POKEMON ->
                MultiStatScalingCalculator(
                    id = calcId,
                    defenseModel = defenseModel,
                    applyAspectModifiers = applyAspectModifiers,
                    minimumDamage = minimumDamage,
                    maximumDamage = maximumDamage,
                    variance = variance,
                    criticalMultiplier = criticalMultiplier,
                    scalingStats = scalingStats.toList(),
                    baseDamage = baseDamage,
                    scalingFormula = scalingFormula,
                )
            DamageFormulaType.CUSTOM ->
                CustomDamageCalculator(
                    id = calcId,
                    defenseModel = defenseModel,
                    applyAspectModifiers = applyAspectModifiers,
                    minimumDamage = minimumDamage,
                    maximumDamage = maximumDamage,
                    variance = variance,
                    criticalMultiplier = criticalMultiplier,
                    customFormula = customFormula,
                )
        }
    }
}

/** Builder for scaling stats. */
@GbktDsl
class ScalingStatsBuilder {
    private val stats = mutableListOf<StatScaling>()

    /** Add a stat contribution */
    fun stat(name: String, weight: Int, grade: ScalingGrade = ScalingGrade.C) {
        stats.add(StatScaling(name, weight, grade))
    }

    /** Common stat shortcuts */
    fun hp(weight: Int, grade: ScalingGrade = ScalingGrade.C) = stat("hp", weight, grade)

    fun sp(weight: Int, grade: ScalingGrade = ScalingGrade.C) = stat("sp", weight, grade)

    fun atk(weight: Int, grade: ScalingGrade = ScalingGrade.C) = stat("atk", weight, grade)

    fun def(weight: Int, grade: ScalingGrade = ScalingGrade.C) = stat("def", weight, grade)

    fun matk(weight: Int, grade: ScalingGrade = ScalingGrade.C) = stat("matk", weight, grade)

    fun mdef(weight: Int, grade: ScalingGrade = ScalingGrade.C) = stat("mdef", weight, grade)

    fun agl(weight: Int, grade: ScalingGrade = ScalingGrade.C) = stat("agl", weight, grade)

    internal fun build(): List<StatScaling> = stats.toList()
}

// =============================================================================
// GAME BUILDER EXTENSIONS
// =============================================================================

/**
 * Define a damage calculator.
 *
 * Allows games to customize how damage is calculated.
 *
 * Usage:
 * ```kotlin
 * // Standard JRPG
 * val standardDamage by damageCalculator {
 *     formula(DamageFormulaType.STANDARD_JRPG)
 *     defense(DefenseModel.FLAT_SUBTRACTION)
 *     minDamage(1)
 *     critMultiplier(200)
 * }
 *
 * // Percentage defense (like armor in some games)
 * val percentDamage by damageCalculator {
 *     formula(DamageFormulaType.PERCENTAGE_DEFENSE)
 *     maxDefensePercent(75)  // Max 75% damage reduction
 *     defenseConversion(DefenseConversionFormula.ASYMPTOTIC)
 * }
 *
 * // Multi-stat scaling (Souls-like)
 * val soulsDamage by damageCalculator {
 *     formula(DamageFormulaType.SOULS_SCALING)
 *     baseDamage(100)
 *     scalingStats {
 *         atk(50, ScalingGrade.B)
 *         stat("str", 30, ScalingGrade.C)
 *         stat("dex", 20, ScalingGrade.D)
 *     }
 * }
 * ```
 */
fun GameBuilder.damageCalculator(
    init: DamageCalculatorBuilder.() -> Unit
): DamageCalculatorDelegate {
    return DamageCalculatorDelegate(this, init)
}
