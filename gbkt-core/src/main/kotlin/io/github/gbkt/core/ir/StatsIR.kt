/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.SourceLocation

// =============================================================================
// STATS IR NODES - RPG character statistics
// =============================================================================

/**
 * A character stat type with its C type and typical range.
 *
 * Built-in stat types cover common RPG needs. Use [CustomStatType] for game-specific stats.
 */
enum class StatType(val cType: String, val defaultMax: Int, val displayName: String? = null) {
    /** Hit Points - typically 0-999 for 16-bit */
    HP("UINT16", 999),

    /** Skill/Magic Points - typically 0-99 for 8-bit */
    SP("UINT8", 99),

    /** Physical Attack Power */
    ATK("UINT8", 255),

    /** Physical Defense */
    DEF("UINT8", 255),

    /** Magical Attack Power */
    MATK("UINT8", 255),

    /** Magical Defense */
    MDEF("UINT8", 255),

    /** Agility - affects turn order and evasion */
    AGL("UINT8", 255),

    /** Level - for progression */
    LEVEL("UINT8", 99),

    /** Experience Points - 16-bit for larger values */
    EXP("UINT16", 65535),
}

/**
 * A custom stat type for game-specific stats not covered by [StatType].
 *
 * @property name Internal name (used in code generation, e.g., "luck")
 * @property displayName Display name (e.g., "LCK" for UI)
 * @property cType C type for the variable (UINT8 or UINT16)
 * @property defaultMax Maximum value for the stat
 */
data class CustomStatType(
    val name: String,
    val displayName: String,
    val cType: String = "UINT8",
    val defaultMax: Int = 255,
) {
    /** Variable name suffix used in code generation. */
    val varNameSuffix: String = name.lowercase()
}

/** Definition of a single stat with base and max values. */
data class StatDefinition(val type: StatType, val baseValue: Int, val maxValue: Int)

/** Definition of a custom stat with base and max values. */
data class CustomStatDefinition(
    val customType: CustomStatType,
    val baseValue: Int,
    val maxValue: Int = customType.defaultMax,
)

/** Complete stats block definition for a character. */
data class StatsDefinition(
    val ownerName: String,
    val stats: List<StatDefinition>,
    val customStats: List<CustomStatDefinition> = emptyList(),
    val aliases: Map<StatType, String> = emptyMap(),
)

// =============================================================================
// STAT MODIFICATION IR NODES
// =============================================================================

/**
 * Modify a stat value at runtime.
 *
 * Generates: owner_stat = value; or owner_stat += value; etc.
 */
data class IRStatModify(
    val ownerName: String,
    val statType: StatType,
    val value: IRExpression,
    val op: AssignOp = AssignOp.SET,
    val useMax: Boolean = false,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Clamp a stat to its max value.
 *
 * Generates: if (owner_stat > owner_stat_max) owner_stat = owner_stat_max;
 */
data class IRStatClamp(
    val ownerName: String,
    val statType: StatType,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Check if a stat is at zero (e.g., for death check).
 *
 * Expression that returns: owner_stat == 0
 */
data class IRStatIsZero(val ownerName: String, val statType: StatType) : IRExpression

/**
 * Check if a stat is at max.
 *
 * Expression that returns: owner_stat >= owner_stat_max
 */
data class IRStatIsFull(val ownerName: String, val statType: StatType) : IRExpression

/**
 * Read a stat value as an expression.
 *
 * Expression that returns: owner_stat (or owner_stat_max if useMax=true)
 */
data class IRStatRead(val ownerName: String, val statType: StatType, val useMax: Boolean = false) :
    IRExpression

/**
 * Heal/restore a stat by a percentage of max.
 *
 * Generates: owner_stat += (owner_stat_max * percent) / 100; if (owner_stat > owner_stat_max)
 * owner_stat = owner_stat_max;
 */
data class IRStatRestorePercent(
    val ownerName: String,
    val statType: StatType,
    val percent: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Deal damage to a stat (subtract with floor at 0).
 *
 * Generates: if (owner_stat >= damage) owner_stat -= damage; else owner_stat = 0;
 */
data class IRStatDamage(
    val ownerName: String,
    val statType: StatType,
    val damage: IRExpression,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement
