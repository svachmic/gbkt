/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IRDamageCalculate
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRLiteral

// =============================================================================
// DAMAGE ASPECTS - Elemental and damage type system
// =============================================================================

/**
 * Damage aspect types for RPG combat.
 *
 * Aspects determine how damage interacts with defenses, resistances, and vulnerabilities.
 */
enum class Aspect {
    /** Physical damage - reduced by DEF */
    PHYSICAL,

    /** Magical damage - reduced by MDEF */
    MAGICAL,

    /** Fire elemental damage */
    FIRE,

    /** Ice/Cold elemental damage */
    ICE,

    /** Lightning/Thunder elemental damage */
    LIGHTNING,

    /** Earth/Nature elemental damage */
    EARTH,

    /** Wind/Air elemental damage */
    WIND,

    /** Water elemental damage */
    WATER,

    /** Light/Holy damage */
    LIGHT,

    /** Dark/Shadow damage */
    DARK,

    /** Pure/untyped damage - ignores resistances */
    PURE,
}

/**
 * Damage modifier for resistances and vulnerabilities.
 *
 * Applied as multipliers to incoming damage of matching aspects.
 */
enum class DamageModifier(val multiplier: Int) {
    /** Takes no damage from this aspect */
    IMMUNE(0),

    /** Takes 50% damage from this aspect */
    RESIST(50),

    /** Takes normal (100%) damage */
    NORMAL(100),

    /** Takes 150% damage from this aspect */
    WEAK(150),

    /** Takes 200% damage from this aspect */
    VULNERABLE(200),
}

// =============================================================================
// DAMAGE DEFINITION - Structure for damage values
// =============================================================================

/**
 * Represents a damage value with its aspect.
 *
 * Usage:
 * ```kotlin
 * val damage = Damage(baseDamage, Aspect.FIRE)
 * target.takeDamage(damage)
 * ```
 */
data class DamageValue(val amount: IRExpression, val aspect: Aspect = Aspect.PHYSICAL)

// =============================================================================
// ASPECT PROFILE - Character/monster resistances and vulnerabilities
// =============================================================================

/**
 * Defines aspect modifiers for a character or monster.
 *
 * Usage:
 * ```kotlin
 * val hero by character {
 *     aspects {
 *         resist(Aspect.FIRE)
 *         weak(Aspect.ICE)
 *         immune(Aspect.POISON)
 *     }
 * }
 * ```
 */
class AspectProfile(val ownerName: String, internal val modifiers: Map<Aspect, DamageModifier>) {
    fun getModifier(aspect: Aspect): DamageModifier = modifiers[aspect] ?: DamageModifier.NORMAL

    fun hasModifier(aspect: Aspect): Boolean = aspect in modifiers
}

/** Builder for aspect profiles. */
@GbktDsl
class AspectProfileBuilder(private val ownerName: String) {
    private val modifiers = mutableMapOf<Aspect, DamageModifier>()

    /** Make immune to an aspect (0% damage) */
    fun immune(vararg aspects: Aspect) {
        aspects.forEach { modifiers[it] = DamageModifier.IMMUNE }
    }

    /** Resist an aspect (50% damage) */
    fun resist(vararg aspects: Aspect) {
        aspects.forEach { modifiers[it] = DamageModifier.RESIST }
    }

    /** Weak to an aspect (150% damage) */
    fun weak(vararg aspects: Aspect) {
        aspects.forEach { modifiers[it] = DamageModifier.WEAK }
    }

    /** Vulnerable to an aspect (200% damage) */
    fun vulnerable(vararg aspects: Aspect) {
        aspects.forEach { modifiers[it] = DamageModifier.VULNERABLE }
    }

    /** Set a custom modifier for an aspect */
    fun modifier(aspect: Aspect, mod: DamageModifier) {
        modifiers[aspect] = mod
    }

    internal fun build(): AspectProfile = AspectProfile(ownerName, modifiers.toMap())
}

// =============================================================================
// DAMAGE CALCULATION DSL
// =============================================================================

/**
 * Calculate damage using the standard formula.
 *
 * Physical: (ATK * power / 100) - DEF Magical: (MATK * power / 100) - MDEF Elemental: Base damage
 * modified by aspect resistances
 *
 * Usage:
 * ```kotlin
 * every.frame {
 *     val damage = calculateDamage {
 *         attacker(hero)
 *         defender(enemy)
 *         power(100) // Base power multiplier
 *         aspect(Aspect.FIRE)
 *     }
 *     enemy.hp -= damage
 * }
 * ```
 */
@GbktDsl
class DamageCalculationBuilder {
    private var attackerName: String? = null
    private var defenderName: String? = null
    private var power: Int = 100
    private var aspect: Aspect = Aspect.PHYSICAL
    private var usePhysical: Boolean = true
    private var flatBonus: Int = 0
    private var ignoreDefense: Boolean = false

    /** Set the attacker (uses their ATK or MATK) */
    fun attacker(character: Character) {
        attackerName = character.name
    }

    /** Set the attacker by name */
    fun attacker(name: String) {
        attackerName = name
    }

    /** Set the defender (uses their DEF or MDEF, and aspect modifiers) */
    fun defender(character: Character) {
        defenderName = character.name
    }

    /** Set the defender by name */
    fun defender(name: String) {
        defenderName = name
    }

    /** Set the power multiplier (default 100 = 1.0x) */
    fun power(value: Int) {
        power = value
    }

    /** Set the damage aspect */
    fun aspect(value: Aspect) {
        aspect = value
        // Determine if physical or magical based on aspect
        usePhysical =
            when (value) {
                Aspect.PHYSICAL -> true
                Aspect.MAGICAL -> false
                // Elemental typically uses magical stats
                else -> false
            }
    }

    /** Use physical stats (ATK/DEF) */
    fun physical() {
        usePhysical = true
    }

    /** Use magical stats (MATK/MDEF) */
    fun magical() {
        usePhysical = false
    }

    /** Add flat bonus damage */
    fun bonus(value: Int) {
        flatBonus = value
    }

    /** Ignore defender's defense stat */
    fun ignoreDefense() {
        ignoreDefense = true
    }

    internal fun build(): DamageCalculation {
        val attacker =
            requireNotNull(attackerName) { "Attacker must be set for damage calculation" }
        return DamageCalculation(
            attackerName = attacker,
            defenderName = defenderName,
            power = power,
            aspect = aspect,
            usePhysical = usePhysical,
            flatBonus = flatBonus,
            ignoreDefense = ignoreDefense,
        )
    }
}

/** Represents a damage calculation configuration. */
data class DamageCalculation(
    val attackerName: String,
    val defenderName: String?,
    val power: Int,
    val aspect: Aspect,
    val usePhysical: Boolean,
    val flatBonus: Int,
    val ignoreDefense: Boolean,
)

/**
 * Calculate damage and return as an expression.
 *
 * The calculation follows the formula:
 * - Base = (ATK or MATK) * power / 100
 * - Reduced = Base - (DEF or MDEF) [if not ignoring defense]
 * - Final = max(1, Reduced * aspectModifier / 100) + flatBonus
 */
fun calculateDamage(init: DamageCalculationBuilder.() -> Unit): Expr {
    val builder = DamageCalculationBuilder()
    builder.init()
    val calc = builder.build()
    return Expr(IRDamageCalculate(calc))
}

/**
 * Deal damage to a character with full calculation.
 *
 * This is a convenience function that calculates and applies damage in one step.
 *
 * Usage:
 * ```kotlin
 * dealDamage(enemy) {
 *     attacker(hero)
 *     power(150)
 *     aspect(Aspect.FIRE)
 * }
 * ```
 */
fun dealDamage(target: Character, init: DamageCalculationBuilder.() -> Unit) {
    val builder = DamageCalculationBuilder()
    builder.init()
    builder.defender(target)
    val calc = builder.build()

    if (RecordingContext.isRecording) {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRDealDamage(target.name, calc))
    }
}

/**
 * Deal flat damage to a character (no calculation, just amount).
 *
 * Usage:
 * ```kotlin
 * dealFlatDamage(enemy, 50, Aspect.FIRE)
 * ```
 */
fun dealFlatDamage(target: Character, amount: Int, aspect: Aspect = Aspect.PURE) {
    emitFlatDamage(target.name, IRLiteral(amount), aspect)
}

fun dealFlatDamage(target: Character, amount: Expr, aspect: Aspect = Aspect.PURE) {
    emitFlatDamage(target.name, amount.ir, aspect)
}

private fun emitFlatDamage(targetName: String, amount: IRExpression, aspect: Aspect) {
    if (RecordingContext.isRecording) {
        RecordingContext.require()
            .emit(io.github.gbkt.core.ir.IRDealFlatDamage(targetName, amount, aspect))
    }
}
