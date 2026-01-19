/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.AssignableExpr
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.CustomStatDefinition
import io.github.gbkt.core.ir.CustomStatType
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.GBVar
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRStatClamp
import io.github.gbkt.core.ir.IRStatDamage
import io.github.gbkt.core.ir.IRStatIsFull
import io.github.gbkt.core.ir.IRStatIsZero
import io.github.gbkt.core.ir.IRStatModify
import io.github.gbkt.core.ir.IRStatRead
import io.github.gbkt.core.ir.IRStatRestorePercent
import io.github.gbkt.core.ir.StatDefinition
import io.github.gbkt.core.ir.StatType
import io.github.gbkt.core.ir.StatsDefinition

// =============================================================================
// STATS DSL - Define character statistics
// =============================================================================

/**
 * Builder for defining character/entity statistics.
 *
 * **Migration Note:** For new projects, consider using `useStatSchema(StatSchema.STANDARD_JRPG)` at
 * the game level to explicitly declare which stats your game uses. The stats {} builder will
 * continue to work and validates against the registered schema.
 *
 * Usage:
 * ```kotlin
 * // Recommended: Declare schema at game level
 * gbGame("MyGame") {
 *     useStatSchema(StatSchema.STANDARD_JRPG) // or custom schema
 *
 *     val hero by character {
 *         stats {
 *             hp(100, max = 999)
 *             sp(50, max = 99)
 *             atk(10)
 *             def(8)
 *             agl(12)
 *         }
 *     }
 * }
 * ```
 *
 * @see StatSchema for defining custom stat schemas
 * @see useStatSchema for registering schemas at game level
 */
@GbktDsl
class StatsBuilder(private val ownerName: String) {
    private val statDefinitions = mutableListOf<StatDefinition>()
    private val customStatDefinitions = mutableListOf<CustomStatDefinition>()
    private val aliases = mutableMapOf<StatType, String>()

    /**
     * Define Hit Points (HP).
     *
     * @param base Initial/current HP value
     * @param max Maximum HP (default: 999 for UINT16)
     */
    fun hp(base: Int, max: Int = StatType.HP.defaultMax) {
        statDefinitions.add(StatDefinition(StatType.HP, base, max))
    }

    /**
     * Define Skill Points (SP) / Magic Points (MP).
     *
     * @param base Initial/current SP value
     * @param max Maximum SP (default: 99 for UINT8)
     */
    fun sp(base: Int, max: Int = StatType.SP.defaultMax) {
        statDefinitions.add(StatDefinition(StatType.SP, base, max))
    }

    /**
     * Define Physical Attack power.
     *
     * @param base Attack value
     * @param max Maximum attack (default: 255)
     */
    fun atk(base: Int, max: Int = StatType.ATK.defaultMax) {
        statDefinitions.add(StatDefinition(StatType.ATK, base, max))
    }

    /**
     * Define Physical Defense.
     *
     * @param base Defense value
     * @param max Maximum defense (default: 255)
     */
    fun def(base: Int, max: Int = StatType.DEF.defaultMax) {
        statDefinitions.add(StatDefinition(StatType.DEF, base, max))
    }

    /**
     * Define Magical Attack power.
     *
     * @param base Magical attack value
     * @param max Maximum magical attack (default: 255)
     */
    fun matk(base: Int, max: Int = StatType.MATK.defaultMax) {
        statDefinitions.add(StatDefinition(StatType.MATK, base, max))
    }

    /**
     * Define Magical Defense.
     *
     * @param base Magical defense value
     * @param max Maximum magical defense (default: 255)
     */
    fun mdef(base: Int, max: Int = StatType.MDEF.defaultMax) {
        statDefinitions.add(StatDefinition(StatType.MDEF, base, max))
    }

    /**
     * Define Agility (affects turn order and evasion).
     *
     * @param base Agility value
     * @param max Maximum agility (default: 255)
     */
    fun agl(base: Int, max: Int = StatType.AGL.defaultMax) {
        statDefinitions.add(StatDefinition(StatType.AGL, base, max))
    }

    /**
     * Define Level (for progression systems).
     *
     * @param base Starting level
     * @param max Maximum level (default: 99)
     */
    fun level(base: Int, max: Int = StatType.LEVEL.defaultMax) {
        statDefinitions.add(StatDefinition(StatType.LEVEL, base, max))
    }

    /**
     * Define Experience Points.
     *
     * @param base Starting EXP
     * @param max Maximum EXP (default: 65535 for UINT16)
     */
    fun exp(base: Int, max: Int = StatType.EXP.defaultMax) {
        statDefinitions.add(StatDefinition(StatType.EXP, base, max))
    }

    // =========================================================================
    // CUSTOM STATS
    // =========================================================================

    /**
     * Define a custom stat not covered by built-in types.
     *
     * Use this for game-specific stats like LUCK, CHARISMA, FAITH, etc. Maximum of 3 custom stats
     * recommended to preserve Game Boy memory.
     *
     * @param name Internal name for the stat (e.g., "luck")
     * @param displayName Display name for UI (e.g., "LCK")
     * @param base Initial value
     * @param max Maximum value (default: 255)
     * @param use16Bit If true, uses UINT16 instead of UINT8 (for values > 255)
     *
     * Usage:
     * ```kotlin
     * stats {
     *     hp(100)
     *     custom("luck", "LCK", base = 10, max = 99)
     *     custom("faith", "FAI", base = 5)
     * }
     * ```
     */
    fun custom(
        name: String,
        displayName: String = name.uppercase().take(3),
        base: Int,
        max: Int = 255,
        use16Bit: Boolean = false,
    ) {
        require(customStatDefinitions.size < 3) {
            "Maximum of 3 custom stats allowed to preserve Game Boy memory"
        }
        require(name.isNotBlank()) { "Custom stat name cannot be blank" }
        require(base in 0..max) { "Base value must be between 0 and max" }
        require(max <= if (use16Bit) 65535 else 255) {
            "Max value must be <= ${if (use16Bit) 65535 else 255}"
        }

        val customType =
            CustomStatType(
                name = name.lowercase(),
                displayName = displayName,
                cType = if (use16Bit) "UINT16" else "UINT8",
                defaultMax = max,
            )
        customStatDefinitions.add(CustomStatDefinition(customType, base, max))
    }

    // =========================================================================
    // ALIASES
    // =========================================================================

    /**
     * Set a display alias for a built-in stat type.
     *
     * This doesn't change the variable names in generated code, but provides an alternative name
     * for UI display purposes.
     *
     * @param statType The built-in stat to alias
     * @param displayName The alternative display name
     *
     * Usage:
     * ```kotlin
     * stats {
     *     hp(100)
     *     alias(StatType.HP, "LIFE")
     *     alias(StatType.SP, "MANA")
     * }
     * ```
     */
    fun alias(statType: StatType, displayName: String) {
        aliases[statType] = displayName
    }

    internal fun build(): StatsDefinition =
        StatsDefinition(
            ownerName = ownerName,
            stats = statDefinitions.toList(),
            customStats = customStatDefinitions.toList(),
            aliases = aliases.toMap(),
        )
}

// =============================================================================
// STAT ACCESSOR - Runtime access to stats
// =============================================================================

/**
 * Provides runtime access to a specific stat.
 *
 * Usage:
 * ```kotlin
 * hero.hp += 10           // Heal
 * hero.hp -= damage       // Take damage
 * hero.hp set 100         // Set to specific value
 * hero.hp.restore(50)     // Restore 50% of max
 *
 * whenever(hero.hp.isZero) { scene(gameoverScene) }
 * whenever(hero.hp.isFull) { hideHealOption() }
 * ```
 */
class StatAccessor(private val ownerName: String, private val statType: StatType) :
    AssignableExpr(
        varName = "${ownerName}_${statType.name.lowercase()}",
        varType = if (statType.cType == "UINT16") GBVar.VarType.U16 else GBVar.VarType.U8,
        ir = IRStatRead(ownerName, statType, useMax = false),
    ) {
    /** Variable name for the current stat value */
    val currentVarName: String = "${ownerName}_${statType.name.lowercase()}"

    /** Variable name for the max stat value */
    val maxVarName: String = "${ownerName}_${statType.name.lowercase()}_max"

    // === Assignment Operations ===

    /** Set stat to a specific value */
    override infix fun set(value: Int) {
        StatOperations.emitSet(ownerName, statType, value)
    }

    /** Set stat to an expression value */
    override infix fun set(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRStatModify(ownerName, statType, value.ir, AssignOp.SET))
        }
    }

    // === Compound Assignment ===

    /** Add to stat: hp += 10 */
    override operator fun plusAssign(value: Int) {
        StatOperations.emitAdd(ownerName, statType, value)
    }

    override operator fun plusAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRStatModify(ownerName, statType, value.ir, AssignOp.ADD))
            RecordingContext.require().emit(IRStatClamp(ownerName, statType))
        }
    }

    /** Subtract from stat with floor at 0: hp -= damage */
    override operator fun minusAssign(value: Int) {
        StatOperations.emitSubtract(ownerName, statType, value)
    }

    override operator fun minusAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRStatDamage(ownerName, statType, value.ir))
        }
    }

    // === Special Operations ===

    /**
     * Restore stat by a percentage of max.
     *
     * Usage: hero.hp.restore(50) // Restore 50% of max HP
     */
    fun restore(percent: Int) {
        require(percent in 1..100) { "Restore percent must be 1-100, got: $percent" }
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRStatRestorePercent(ownerName, statType, percent))
        }
    }

    /** Fully restore stat to max value. */
    fun restoreFull() {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRStatModify(
                        ownerName,
                        statType,
                        IRStatRead(ownerName, statType, useMax = true),
                    )
                )
        }
    }

    // === Condition Checks ===

    /**
     * Check if stat is at zero.
     *
     * Usage: whenever(hero.hp.isZero) { scene(gameoverScene) }
     */
    override val isZero: Condition
        get() = Condition(IRStatIsZero(ownerName, statType))

    /**
     * Check if stat is at or above max.
     *
     * Usage: whenever(hero.hp.isFull) { hideHealButton() }
     */
    val isFull: Condition
        get() = Condition(IRStatIsFull(ownerName, statType))

    // === Max Value Access ===

    /**
     * Access the max value of this stat.
     *
     * Usage: val maxHp = hero.hp.max
     */
    val max: Expr
        get() = Expr(IRStatRead(ownerName, statType, useMax = true))

    /**
     * Set the max value of this stat.
     *
     * Usage: hero.hp.maxSet(200)
     */
    fun maxSet(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRStatModify(ownerName, statType, IRLiteral(value), useMax = true))
        }
    }
}

// =============================================================================
// CHARACTER STATS - Complete stats for a character
// =============================================================================

/**
 * Holds all stats for a character with convenient accessors.
 *
 * Generated from a stats {} block in the character definition.
 */
class CharacterStats(val ownerName: String, val definition: StatsDefinition) {
    private val statAccessors = mutableMapOf<StatType, StatAccessor>()

    init {
        for (stat in definition.stats) {
            statAccessors[stat.type] = StatAccessor(ownerName, stat.type)
        }
    }

    /** Hit Points */
    val hp: StatAccessor
        get() = statAccessors[StatType.HP] ?: error("Character '$ownerName' has no HP stat defined")

    /** Skill/Magic Points */
    val sp: StatAccessor
        get() = statAccessors[StatType.SP] ?: error("Character '$ownerName' has no SP stat defined")

    /** Physical Attack */
    val atk: StatAccessor
        get() =
            statAccessors[StatType.ATK] ?: error("Character '$ownerName' has no ATK stat defined")

    /** Physical Defense */
    val def: StatAccessor
        get() =
            statAccessors[StatType.DEF] ?: error("Character '$ownerName' has no DEF stat defined")

    /** Magical Attack */
    val matk: StatAccessor
        get() =
            statAccessors[StatType.MATK] ?: error("Character '$ownerName' has no MATK stat defined")

    /** Magical Defense */
    val mdef: StatAccessor
        get() =
            statAccessors[StatType.MDEF] ?: error("Character '$ownerName' has no MDEF stat defined")

    /** Agility */
    val agl: StatAccessor
        get() =
            statAccessors[StatType.AGL] ?: error("Character '$ownerName' has no AGL stat defined")

    /** Level */
    val level: StatAccessor
        get() =
            statAccessors[StatType.LEVEL]
                ?: error("Character '$ownerName' has no LEVEL stat defined")

    /** Experience Points */
    val exp: StatAccessor
        get() =
            statAccessors[StatType.EXP] ?: error("Character '$ownerName' has no EXP stat defined")

    /** Check if a stat type is defined */
    fun hasStat(type: StatType): Boolean = type in statAccessors

    /** Get stat accessor by type, or null if not defined */
    fun getStatOrNull(type: StatType): StatAccessor? = statAccessors[type]

    /** Get all defined stat types */
    val definedStats: Set<StatType>
        get() = statAccessors.keys
}
