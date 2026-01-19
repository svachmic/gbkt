/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RawCodeEscapeHatch
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.IRAddExp
import io.github.gbkt.core.ir.IRCheckLevelUp
import io.github.gbkt.core.ir.IRGetCurrentExp
import io.github.gbkt.core.ir.IRGetExpToNextLevel
import io.github.gbkt.core.ir.IRGetLevel
import io.github.gbkt.core.ir.IRIsMaxLevel
import io.github.gbkt.core.ir.IRSetLevel
import io.github.gbkt.core.ir.IRStatement

// =============================================================================
// EXPERIENCE AND LEVELING SYSTEM
// =============================================================================

/**
 * Experience curve definitions for level progression.
 *
 * Different curves result in faster or slower leveling.
 */
enum class ExpCurve {
    /** Linear progression: each level requires the same amount of exp */
    LINEAR,

    /** Slow start, faster later: exp = level^1.5 * base */
    SLOW_START,

    /** Standard RPG curve: exp = level^2 * base */
    STANDARD,

    /** Fast start, slower later: exp = level^2.5 * base */
    FAST_START,

    /** Exponential: exp = base * 1.5^level (very steep) */
    EXPONENTIAL,
}

/** Stat growth rates for level-up bonuses. */
enum class GrowthRate {
    /** No growth */
    NONE,

    /** Low growth: +1 per 3 levels */
    LOW,

    /** Medium growth: +1 per 2 levels */
    MEDIUM,

    /** Standard growth: +1 per level */
    STANDARD,

    /** High growth: +2 per level */
    HIGH,

    /** Very high growth: +3 per level */
    VERY_HIGH,
}

/**
 * Leveling configuration for a character.
 *
 * Defines experience curve, max level, and stat growth rates.
 */
class LevelingConfig(
    /** The character this config belongs to */
    val characterName: String,
    /** Maximum level (1-255, typically 99 for classic RPGs, 50 for shorter games) */
    val maxLevel: Int,
    /** Experience curve type */
    val expCurve: ExpCurve,
    /** Base experience for level 2 */
    val baseExp: Int,
    /** Stat growth rates */
    val growthRates: Map<StatGrowthType, GrowthRate>,
    /** IR statements to execute on level up */
    val onLevelUpStatements: List<IRStatement>,
    /** Abilities that unlock at specific levels for this character */
    val abilityUnlocks: List<AbilityUnlock> = emptyList(),
    /** Whether to automatically grant abilities based on level requirements */
    val autoLearnAbilities: Boolean = true,
) {
    /** Starting level for this character */
    val startLevel: Int = 1

    /** Get abilities that should unlock at the given level */
    fun getAbilitiesForLevel(level: Int): List<Ability> =
        abilityUnlocks.filter { it.level == level }.map { it.ability }
}

/** Types of stats that can grow on level up. */
enum class StatGrowthType {
    MAX_HP,
    MAX_SP,
    ATK,
    DEF,
    MATK,
    MDEF,
    AGL,
}

// =============================================================================
// ABILITY UNLOCK SYSTEM
// =============================================================================

/**
 * Represents an ability that unlocks at a specific level.
 *
 * Used with the [learns] DSL function to specify which abilities a character learns at which
 * levels.
 *
 * @property ability The ability to unlock
 * @property level The level at which the ability is learned
 */
data class AbilityUnlock(val ability: Ability, val level: Int) {
    init {
        require(level >= 1) { "Level must be at least 1" }
    }
}

/**
 * Create an ability unlock for a specific level.
 *
 * Usage:
 * ```kotlin
 * leveling {
 *     learns(fireball at 5, blizzard at 10, meteor at 20)
 * }
 * ```
 *
 * @param level The level at which this ability is learned
 * @return An [AbilityUnlock] pairing this ability with the level
 */
infix fun Ability.at(level: Int): AbilityUnlock = AbilityUnlock(this, level)

// =============================================================================
// LEVELING DSL BUILDER
// =============================================================================

/**
 * Builder for leveling configuration via DSL.
 *
 * Usage:
 * ```kotlin
 * val hero by character {
 *     name("Hero")
 *     leveling {
 *         maxLevel(99)
 *         expCurve(ExpCurve.STANDARD)
 *         baseExp(100)
 *         growth {
 *             maxHp(GrowthRate.HIGH)
 *             atk(GrowthRate.STANDARD)
 *             def(GrowthRate.MEDIUM)
 *         }
 *         onLevelUp {
 *             // Play level up sound
 *             raw("play_sfx(SFX_LEVELUP);")
 *         }
 *     }
 * }
 * ```
 */
@GbktDsl
class LevelingBuilder(private val characterName: String) {
    private var maxLevel: Int = 99
    private var expCurve: ExpCurve = ExpCurve.STANDARD
    private var baseExp: Int = 100
    private val growthRates = mutableMapOf<StatGrowthType, GrowthRate>()
    private var onLevelUpStatements: List<IRStatement> = emptyList()
    private val abilityUnlocks = mutableListOf<AbilityUnlock>()
    private var autoLearnAbilities: Boolean = true

    /**
     * Set the maximum level for this character.
     *
     * Valid range is 1-255 (8-bit limit for Game Boy). Default is 99. Must not exceed the game's
     * configured maxLevel.
     *
     * @param level Maximum level (1-255)
     */
    fun maxLevel(level: Int) {
        require(level in 1..255) { "Max level must be 1-255, got: $level" }
        this.maxLevel = level
    }

    /** Set the experience curve type. */
    fun expCurve(curve: ExpCurve) {
        this.expCurve = curve
    }

    /**
     * Set the base experience required for level 2.
     *
     * Higher levels will require more based on the curve.
     */
    fun baseExp(exp: Int) {
        require(exp > 0) { "Base exp must be positive" }
        this.baseExp = exp
    }

    /** Define stat growth rates on level up. */
    fun growth(init: GrowthBuilder.() -> Unit) {
        val builder = GrowthBuilder()
        builder.init()
        growthRates.putAll(builder.rates)
    }

    /** Define actions to perform on level up. */
    fun onLevelUp(block: LevelUpScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { LevelUpScope().block() }
        this.onLevelUpStatements = recorder.statements
    }

    /**
     * Specify abilities that this character learns at specific levels.
     *
     * Usage:
     * ```kotlin
     * leveling {
     *     maxLevel(99)
     *     learns(fireball at 5, blizzard at 10, meteor at 20)
     * }
     * ```
     *
     * @param unlocks Abilities paired with their unlock levels using [at]
     */
    fun learns(vararg unlocks: AbilityUnlock) {
        abilityUnlocks.addAll(unlocks)
    }

    /**
     * Specify a single ability learned at a level.
     *
     * Alternative syntax for single ability:
     * ```kotlin
     * leveling {
     *     learns(fireball, atLevel = 5)
     * }
     * ```
     */
    fun learns(ability: Ability, atLevel: Int) {
        abilityUnlocks.add(AbilityUnlock(ability, atLevel))
    }

    /**
     * Whether to automatically grant abilities based on their level requirements.
     *
     * When true (default), abilities will be granted when the character reaches the required level
     * during level-up. Set to false to manually control ability unlocks via [onLevelUp] callbacks.
     */
    fun autoLearnAbilities(enabled: Boolean) {
        this.autoLearnAbilities = enabled
    }

    internal fun build(): LevelingConfig {
        return LevelingConfig(
            characterName = characterName,
            maxLevel = maxLevel,
            expCurve = expCurve,
            baseExp = baseExp,
            growthRates = growthRates.toMap(),
            onLevelUpStatements = onLevelUpStatements,
            abilityUnlocks = abilityUnlocks.toList(),
            autoLearnAbilities = autoLearnAbilities,
        )
    }
}

/** Builder for stat growth rates. */
@GbktDsl
class GrowthBuilder {
    internal val rates = mutableMapOf<StatGrowthType, GrowthRate>()

    fun maxHp(rate: GrowthRate) {
        rates[StatGrowthType.MAX_HP] = rate
    }

    fun maxSp(rate: GrowthRate) {
        rates[StatGrowthType.MAX_SP] = rate
    }

    fun atk(rate: GrowthRate) {
        rates[StatGrowthType.ATK] = rate
    }

    fun def(rate: GrowthRate) {
        rates[StatGrowthType.DEF] = rate
    }

    fun matk(rate: GrowthRate) {
        rates[StatGrowthType.MATK] = rate
    }

    fun mdef(rate: GrowthRate) {
        rates[StatGrowthType.MDEF] = rate
    }

    fun agl(rate: GrowthRate) {
        rates[StatGrowthType.AGL] = rate
    }
}

/**
 * Scope for level up callbacks. Provides access to modify character stats on level up.
 *
 * Usage:
 * ```kotlin
 * onLevelUp {
 *     // Access stats directly
 *     character.maxHp += 10
 *     character.maxSp += 5
 *     character.atk += 2
 *
 *     // Or use convenience methods
 *     increaseMaxHp(10)
 *     increaseStat(StatGrowthType.ATK, 2)
 *
 *     // Play level up sound
 *     raw("play_sfx(SFX_LEVELUP);")
 * }
 * ```
 */
@GbktDsl
class LevelUpScope {
    /**
     * Access to the character who leveled up. Uses `_levelup_char` as a placeholder resolved by
     * codegen.
     */
    val character: LevelUpCharacterScope = LevelUpCharacterScope()

    /**
     * Increase max HP by the given amount.
     *
     * @param amount HP to add to max HP
     */
    fun increaseMaxHp(amount: Int) {
        require(amount > 0) { "Amount must be positive" }
        RecordingContext.require()
            .emit(
                io.github.gbkt.core.ir.IRStatModify(
                    "_levelup_char",
                    io.github.gbkt.core.ir.StatType.HP,
                    io.github.gbkt.core.ir.IRLiteral(amount),
                    io.github.gbkt.core.ir.AssignOp.ADD,
                    useMax = true,
                )
            )
    }

    /**
     * Increase max SP by the given amount.
     *
     * @param amount SP to add to max SP
     */
    fun increaseMaxSp(amount: Int) {
        require(amount > 0) { "Amount must be positive" }
        RecordingContext.require()
            .emit(
                io.github.gbkt.core.ir.IRStatModify(
                    "_levelup_char",
                    io.github.gbkt.core.ir.StatType.SP,
                    io.github.gbkt.core.ir.IRLiteral(amount),
                    io.github.gbkt.core.ir.AssignOp.ADD,
                    useMax = true,
                )
            )
    }

    /**
     * Increase a specific stat by the given amount.
     *
     * @param stat The stat type to increase
     * @param amount Amount to add
     */
    fun increaseStat(stat: StatGrowthType, amount: Int) {
        require(amount > 0) { "Amount must be positive" }
        val irStatType =
            when (stat) {
                StatGrowthType.MAX_HP -> io.github.gbkt.core.ir.StatType.HP
                StatGrowthType.MAX_SP -> io.github.gbkt.core.ir.StatType.SP
                StatGrowthType.ATK -> io.github.gbkt.core.ir.StatType.ATK
                StatGrowthType.DEF -> io.github.gbkt.core.ir.StatType.DEF
                StatGrowthType.MATK -> io.github.gbkt.core.ir.StatType.MATK
                StatGrowthType.MDEF -> io.github.gbkt.core.ir.StatType.MDEF
                StatGrowthType.AGL -> io.github.gbkt.core.ir.StatType.AGL
            }
        val useMax = stat == StatGrowthType.MAX_HP || stat == StatGrowthType.MAX_SP

        RecordingContext.require()
            .emit(
                io.github.gbkt.core.ir.IRStatModify(
                    "_levelup_char",
                    irStatType,
                    io.github.gbkt.core.ir.IRLiteral(amount),
                    io.github.gbkt.core.ir.AssignOp.ADD,
                    useMax = useMax,
                )
            )
    }

    /** Fully heal HP on level up. */
    fun fullHeal() {
        RecordingContext.require()
            .emit(
                io.github.gbkt.core.ir.IRStatRestorePercent(
                    "_levelup_char",
                    io.github.gbkt.core.ir.StatType.HP,
                    100,
                )
            )
    }

    /** Fully restore SP on level up. */
    fun fullRestoreSp() {
        RecordingContext.require()
            .emit(
                io.github.gbkt.core.ir.IRStatRestorePercent(
                    "_levelup_char",
                    io.github.gbkt.core.ir.StatType.SP,
                    100,
                )
            )
    }

    /** Raw C code escape hatch - bypasses type safety. Use sparingly. */
    @RawCodeEscapeHatch
    fun raw(code: String) {
        RecordingContext.require().emit(io.github.gbkt.core.ir.IRRaw(code))
    }

    /**
     * Grant an ability to the character on level up.
     *
     * Sets the ability flag to unlock this ability.
     *
     * @param ability The ability to grant
     *
     * Usage:
     * ```kotlin
     * onLevelUp {
     *     if (character.level >= 10) {
     *         grantAbility(fireball)
     *     }
     * }
     * ```
     */
    fun grantAbility(ability: Ability) {
        RecordingContext.require()
            .emit(
                io.github.gbkt.core.ir.IRGrantAbility(
                    characterName = "_levelup_char",
                    abilityId = ability.id,
                    abilityIndex = ability.abilityIndex,
                )
            )
    }

    /**
     * Revoke an ability from the character.
     *
     * Clears the ability flag to remove this ability.
     *
     * @param ability The ability to revoke
     */
    fun revokeAbility(ability: Ability) {
        RecordingContext.require()
            .emit(
                io.github.gbkt.core.ir.IRRevokeAbility(
                    characterName = "_levelup_char",
                    abilityId = ability.id,
                    abilityIndex = ability.abilityIndex,
                )
            )
    }
}

/**
 * Provides DSL access to the leveling character's stats.
 *
 * Usage: `character.maxHp += 10`, `character.atk += 2`
 */
@GbktDsl
class LevelUpCharacterScope {
    /** Max HP stat accessor - character.maxHp += 10 */
    val maxHp: LevelUpStatAccessor =
        LevelUpStatAccessor("_levelup_char", io.github.gbkt.core.ir.StatType.HP, isMax = true)

    /** Max SP stat accessor - character.maxSp += 5 */
    val maxSp: LevelUpStatAccessor =
        LevelUpStatAccessor("_levelup_char", io.github.gbkt.core.ir.StatType.SP, isMax = true)

    /** ATK stat accessor */
    val atk: LevelUpStatAccessor =
        LevelUpStatAccessor("_levelup_char", io.github.gbkt.core.ir.StatType.ATK, isMax = false)

    /** DEF stat accessor */
    val def: LevelUpStatAccessor =
        LevelUpStatAccessor("_levelup_char", io.github.gbkt.core.ir.StatType.DEF, isMax = false)

    /** MATK stat accessor */
    val matk: LevelUpStatAccessor =
        LevelUpStatAccessor("_levelup_char", io.github.gbkt.core.ir.StatType.MATK, isMax = false)

    /** MDEF stat accessor */
    val mdef: LevelUpStatAccessor =
        LevelUpStatAccessor("_levelup_char", io.github.gbkt.core.ir.StatType.MDEF, isMax = false)

    /** AGL stat accessor */
    val agl: LevelUpStatAccessor =
        LevelUpStatAccessor("_levelup_char", io.github.gbkt.core.ir.StatType.AGL, isMax = false)
}

/**
 * Stat accessor for level up effects.
 *
 * Supports: `+=` and `set` operations for modifying stats on level up. Implements [StatModifier] to
 * share behavior with other stat accessors.
 */
class LevelUpStatAccessor(
    private val ownerName: String,
    private val statType: io.github.gbkt.core.ir.StatType,
    private val isMax: Boolean,
) : StatModifier {
    /** Add to stat value: character.maxHp += 10 */
    override operator fun plusAssign(value: Int) {
        StatOperations.emitAdd(ownerName, statType, value, clamp = !isMax, useMax = isMax)
    }

    /** Set stat to exact value */
    override infix fun set(value: Int) {
        StatOperations.emitSet(ownerName, statType, value, useMax = isMax)
    }
}

// =============================================================================
// EXPERIENCE OPERATIONS
// =============================================================================

/**
 * Experience system operations for a character.
 *
 * Provides DSL methods for adding exp, checking level, etc.
 */
class ExpSystem(private val characterName: String, private val config: LevelingConfig?) {
    /**
     * Add experience points to this character.
     *
     * Will automatically check for level up.
     */
    fun addExp(amount: Int) {
        require(amount >= 0) { "Exp amount must be non-negative" }
        RecordingContext.require().emit(IRAddExp(characterName, amount))
    }

    /**
     * Check if the character should level up and process it.
     *
     * Called automatically after addExp, but can be called manually.
     */
    fun checkLevelUp() {
        RecordingContext.require().emit(IRCheckLevelUp(characterName))
    }

    /**
     * Set the character's level directly.
     *
     * Use sparingly - this bypasses normal leveling.
     */
    fun setLevel(level: Int) {
        require(level >= 1) { "Level must be at least 1" }
        RecordingContext.require().emit(IRSetLevel(characterName, level))
    }

    /** Get current level as an expression. */
    fun level(): IRGetLevel = IRGetLevel(characterName)

    /** Get current exp as an expression. */
    fun currentExp(): IRGetCurrentExp = IRGetCurrentExp(characterName)

    /** Get exp needed for next level as an expression. */
    fun expToNextLevel(): IRGetExpToNextLevel = IRGetExpToNextLevel(characterName)

    /** Check if at max level. */
    fun isMaxLevel(): IRIsMaxLevel = IRIsMaxLevel(characterName)
}

// =============================================================================
// EXP TABLE CALCULATIONS
// =============================================================================

/** Calculate experience required for a given level based on curve. */
fun calculateExpForLevel(level: Int, curve: ExpCurve, baseExp: Int): Int {
    if (level <= 1) return 0

    return when (curve) {
        ExpCurve.LINEAR -> (level - 1) * baseExp
        ExpCurve.SLOW_START -> {
            // sqrt approximation: each level needs slightly more
            var total = 0
            for (l in 2..level) {
                total += (baseExp * l) / 2
            }
            total
        }
        ExpCurve.STANDARD -> {
            // Quadratic: level^2 * base / 10
            var total = 0
            for (l in 2..level) {
                total += l * l * baseExp / 10
            }
            total
        }
        ExpCurve.FAST_START -> {
            // Cubic-ish: l^2 * l / 2 * base / 100
            var total = 0
            for (l in 2..level) {
                total += l * l * l * baseExp / 200
            }
            total
        }
        ExpCurve.EXPONENTIAL -> {
            // Each level needs ~1.5x the previous
            // Use Long to avoid overflow, cap at Int.MAX_VALUE
            var total = 0L
            var current = baseExp.toLong()
            for (l in 2..level) {
                total += current
                current = current * 3 / 2 // Approximate 1.5x
                // Cap to prevent overflow in accumulator
                if (total > Int.MAX_VALUE) {
                    return Int.MAX_VALUE
                }
            }
            total.toInt()
        }
    }
}

/** Calculate stat growth bonus for a level based on growth rate. */
fun calculateStatGrowth(level: Int, rate: GrowthRate): Int {
    return when (rate) {
        GrowthRate.NONE -> 0
        GrowthRate.LOW -> (level - 1) / 3
        GrowthRate.MEDIUM -> (level - 1) / 2
        GrowthRate.STANDARD -> level - 1
        GrowthRate.HIGH -> (level - 1) * 2
        GrowthRate.VERY_HIGH -> (level - 1) * 3
    }
}
