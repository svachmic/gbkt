/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.GameScopeContext
import io.github.gbkt.core.ir.IRHitCheck
import io.github.gbkt.core.ir.IRStatement

// =============================================================================
// COMBAT FORMULAS - Configurable hit/miss, damage, and critical formulas
// =============================================================================

/**
 * Built-in hit formula strategies.
 *
 * These provide common hit/miss calculation patterns without custom logic.
 */
sealed interface HitFormulaStrategy {
    /** Attacks always hit (default - no miss chance) */
    data object AlwaysHit : HitFormulaStrategy

    /** D&D-style: d20 + ATK vs DEF + base Hit if: roll + atk > def + base */
    data class D20Based(val baseAC: Int = 10) : HitFormulaStrategy

    /**
     * Percentage-based hit chance. Base chance modified by (ATK - DEF).
     *
     * @param baseChance Base hit percentage (0-100)
     * @param minChance Minimum hit chance (floor)
     * @param maxChance Maximum hit chance (ceiling)
     * @param perDiff Modifier per point of ATK-DEF difference
     */
    data class PercentageBased(
        val baseChance: Int = 75,
        val minChance: Int = 20,
        val maxChance: Int = 95,
        val perDiff: Int = 2,
    ) : HitFormulaStrategy

    /** Agility comparison: Higher agility = higher hit chance. Uses AGL stat instead of ATK/DEF. */
    data class AgilityBased(
        val baseChance: Int = 75,
        val minChance: Int = 20,
        val maxChance: Int = 95,
    ) : HitFormulaStrategy

    /**
     * Custom formula - user provides the calculation logic. The formula block receives IRStatements
     * that will be generated.
     */
    data class Custom(val formulaStatements: List<IRStatement>) : HitFormulaStrategy
}

/** Built-in critical hit formula strategies. */
sealed interface CriticalFormulaStrategy {
    /** No critical hits */
    data object NoCrits : CriticalFormulaStrategy

    /**
     * Flat percentage chance for critical hit.
     *
     * @param chance Crit percentage (1-100)
     */
    data class FlatChance(val chance: Int = 5) : CriticalFormulaStrategy

    /**
     * D&D-style: Natural 20 (or high roll) is critical.
     *
     * @param threshold Roll threshold for crit (e.g., 14 on d16 for 12.5% crit)
     * @param dieSize Size of die (e.g., 16 for d16, 20 for d20)
     */
    data class HighRoll(val threshold: Int = 14, val dieSize: Int = 16) : CriticalFormulaStrategy

    /** Custom critical formula. */
    data class Custom(val formulaStatements: List<IRStatement>) : CriticalFormulaStrategy
}

/** Damage variance strategy. */
sealed interface DamageVarianceStrategy {
    /** No variance - damage is exact */
    data object NoVariance : DamageVarianceStrategy

    /**
     * Percentage-based variance: damage * (1 - variance/2 to 1 + variance/2)
     *
     * @param variancePercent Total variance range (e.g., 25 = damage varies by ±12.5%)
     */
    data class PercentageVariance(val variancePercent: Int = 25) : DamageVarianceStrategy

    /**
     * D&D-style: damage roll uses a multiplier table. Simulates variance like the Dragon game's
     * damage_roll_modifier[16].
     *
     * @param min Minimum multiplier (e.g., 75 = 75%)
     * @param max Maximum multiplier (e.g., 125 = 125%)
     */
    data class MultiplierTable(val min: Int = 75, val max: Int = 125) : DamageVarianceStrategy
}

/**
 * Complete combat formula configuration.
 *
 * Defines how hits, crits, misses, and damage variance work in the game.
 */
class CombatFormulas
internal constructor(
    val hitFormula: HitFormulaStrategy,
    val criticalFormula: CriticalFormulaStrategy,
    val damageVariance: DamageVarianceStrategy,
    val critMultiplier: Int, // 150 = 1.5x damage on crit
    val fumbleEnabled: Boolean, // Enable fumble/miss penalty
    val fumbleThreshold: Int, // Low roll = fumble (e.g., 1-2 on d16)
)

/** Builder for combat formulas configuration. */
@GbktDsl
class CombatFormulasBuilder {
    private var hitFormula: HitFormulaStrategy = HitFormulaStrategy.AlwaysHit
    private var criticalFormula: CriticalFormulaStrategy = CriticalFormulaStrategy.NoCrits
    private var damageVariance: DamageVarianceStrategy = DamageVarianceStrategy.NoVariance
    private var critMultiplier: Int = 150
    private var fumbleEnabled: Boolean = false
    private var fumbleThreshold: Int = 2

    // === Hit Formula Configuration ===

    /** Attacks always hit (default) */
    fun alwaysHits() {
        hitFormula = HitFormulaStrategy.AlwaysHit
    }

    /**
     * D&D-style: d20 + ATK vs target's AC (DEF + base)
     *
     * @param baseAC Base armor class (default 10)
     */
    fun d20HitRoll(baseAC: Int = 10) {
        hitFormula = HitFormulaStrategy.D20Based(baseAC)
    }

    /**
     * Percentage-based hit chance with ATK-DEF modifier.
     *
     * @param baseChance Starting hit percentage
     * @param minChance Floor for hit chance
     * @param maxChance Ceiling for hit chance
     * @param perDiff Modifier per point difference
     */
    fun percentageHitChance(
        baseChance: Int = 75,
        minChance: Int = 20,
        maxChance: Int = 95,
        perDiff: Int = 2,
    ) {
        hitFormula = HitFormulaStrategy.PercentageBased(baseChance, minChance, maxChance, perDiff)
    }

    /** Agility-based hit chance (for dodge-based systems). */
    fun agilityBasedHit(baseChance: Int = 75, minChance: Int = 20, maxChance: Int = 95) {
        hitFormula = HitFormulaStrategy.AgilityBased(baseChance, minChance, maxChance)
    }

    /**
     * Custom hit formula logic.
     *
     * The block should define the hit check logic that will be code-generated. Use special DSL
     * methods to reference attacker/defender stats.
     */
    fun customHitFormula(block: HitFormulaScope.() -> Unit) {
        val scope = HitFormulaScope()
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { scope.block() }
        hitFormula = HitFormulaStrategy.Custom(recorder.statements)
    }

    // === Critical Hit Configuration ===

    /** Disable critical hits */
    fun noCriticalHits() {
        criticalFormula = CriticalFormulaStrategy.NoCrits
    }

    /**
     * Flat percentage chance for critical hit.
     *
     * @param percent Crit chance (1-100)
     */
    fun criticalChance(percent: Int) {
        require(percent in 1..100) { "Critical chance must be 1-100" }
        criticalFormula = CriticalFormulaStrategy.FlatChance(percent)
    }

    /**
     * High roll = critical (e.g., natural 20 in D&D).
     *
     * @param threshold Minimum roll for crit
     * @param dieSize Die size (default d16 like Dragon)
     */
    fun criticalOnHighRoll(threshold: Int = 14, dieSize: Int = 16) {
        criticalFormula = CriticalFormulaStrategy.HighRoll(threshold, dieSize)
    }

    /**
     * Set critical damage multiplier.
     *
     * @param multiplier Percentage (e.g., 150 = 1.5x, 200 = 2x)
     */
    fun criticalMultiplier(multiplier: Int) {
        require(multiplier in 100..500) { "Multiplier must be 100-500" }
        critMultiplier = multiplier
    }

    // === Damage Variance Configuration ===

    /** No damage variance (exact damage) */
    fun noVariance() {
        damageVariance = DamageVarianceStrategy.NoVariance
    }

    /**
     * Percentage-based damage variance.
     *
     * @param percent Total variance range (e.g., 25 = ±12.5%)
     */
    fun damageVariance(percent: Int) {
        require(percent in 0..100) { "Variance must be 0-100" }
        damageVariance = DamageVarianceStrategy.PercentageVariance(percent)
    }

    /**
     * Multiplier table variance (like Dragon's damage_roll_modifier).
     *
     * @param min Minimum multiplier percentage
     * @param max Maximum multiplier percentage
     */
    fun damageMultiplierRange(min: Int = 75, max: Int = 125) {
        damageVariance = DamageVarianceStrategy.MultiplierTable(min, max)
    }

    // === Fumble Configuration ===

    /**
     * Enable fumble/miss on low rolls.
     *
     * @param threshold Maximum roll value that causes fumble
     */
    fun enableFumble(threshold: Int = 2) {
        fumbleEnabled = true
        fumbleThreshold = threshold
    }

    internal fun build(): CombatFormulas =
        CombatFormulas(
            hitFormula = hitFormula,
            criticalFormula = criticalFormula,
            damageVariance = damageVariance,
            critMultiplier = critMultiplier,
            fumbleEnabled = fumbleEnabled,
            fumbleThreshold = fumbleThreshold,
        )
}

/**
 * Scope for defining custom hit formulas.
 *
 * Provides DSL methods for referencing attacker/defender stats and random rolls within the formula.
 */
@GbktDsl
class HitFormulaScope {
    // Placeholder for stat references in custom formulas
    // These will be replaced with actual stat accesses during codegen
}

/**
 * Create combat formulas configuration.
 *
 * Usage:
 * ```kotlin
 * val formulas = combatFormulas {
 *     // D&D-style hit rolls
 *     d20HitRoll(baseAC = 10)
 *
 *     // Critical on natural 20
 *     criticalOnHighRoll(threshold = 20, dieSize = 20)
 *     criticalMultiplier(200) // 2x damage
 *
 *     // 25% damage variance
 *     damageVariance(25)
 *
 *     // Enable fumble on 1
 *     enableFumble(threshold = 1)
 * }
 * ```
 *
 * Or for simpler games:
 * ```kotlin
 * val formulas = combatFormulas {
 *     alwaysHits()           // No miss chance
 *     criticalChance(5)      // 5% crit chance
 *     noVariance()           // Exact damage
 * }
 * ```
 */
fun combatFormulas(block: CombatFormulasBuilder.() -> Unit = {}): CombatFormulas {
    val builder = CombatFormulasBuilder()
    builder.block()
    return builder.build()
}

/**
 * Register combat formulas for code generation.
 *
 * Combat formulas must be registered at game scope (inside `gbGame { }` but outside scene lifecycle
 * blocks) to ensure their definitions are generated at file scope in the C output.
 */
fun registerCombatFormulas(formulas: CombatFormulas) {
    val gameBuilder =
        GameScopeContext.current as? GameBuilder
            ?: error(
                "registerCombatFormulas must be called inside a game builder context " +
                    "(inside gbGame { } but outside scene enter/frame/exit blocks)"
            )
    gameBuilder.registerCombatFormulas(formulas)
}

/**
 * Perform a hit check with the configured formulas.
 *
 * This is typically called automatically during damage application, but can be called explicitly
 * for custom battle logic.
 *
 * Usage:
 * ```kotlin
 * whenever(hitCheck(hero, enemy)) {
 *     dealDamage(enemy) { ... }
 * }
 * ```
 */
fun hitCheck(attackerName: String, defenderName: String): io.github.gbkt.core.ir.Condition {
    return io.github.gbkt.core.ir.Condition(IRHitCheck(attackerName, defenderName))
}

fun hitCheck(attacker: Character, defender: Character): io.github.gbkt.core.ir.Condition {
    return hitCheck(attacker.name, defender.name)
}
