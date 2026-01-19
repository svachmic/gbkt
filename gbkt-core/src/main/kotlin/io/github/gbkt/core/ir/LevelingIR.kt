/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.SourceLocation

// =============================================================================
// LEVELING IR NODES - Experience and level progression
// =============================================================================

/**
 * Add experience points to a character.
 *
 * Generates: character_add_exp(&character, amount); character_check_level_up(&character);
 */
data class IRAddExp(
    val characterName: String,
    val amount: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Check if character should level up and process it.
 *
 * Generates: character_check_level_up(&character);
 */
data class IRCheckLevelUp(
    val characterName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Set character level directly.
 *
 * Generates: character_set_level(&character, level);
 */
data class IRSetLevel(
    val characterName: String,
    val level: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Get current level as expression.
 *
 * Expression: character_level
 */
data class IRGetLevel(val characterName: String) : IRExpression

/**
 * Get current exp as expression.
 *
 * Expression: character_exp
 */
data class IRGetCurrentExp(val characterName: String) : IRExpression

/**
 * Get exp needed for next level as expression.
 *
 * Expression: (exp_table[character_level + 1] - character_exp)
 */
data class IRGetExpToNextLevel(val characterName: String) : IRExpression

/**
 * Check if at max level.
 *
 * Expression: (character_level >= CHARACTER_MAX_LEVEL)
 */
data class IRIsMaxLevel(val characterName: String) : IRExpression

/**
 * Get exp required for a specific level.
 *
 * Expression: exp_table[level]
 */
data class IRGetExpForLevel(val characterName: String, val level: Int) : IRExpression

/**
 * Apply stat growth for level up.
 *
 * Generates code to increase stats based on growth rates.
 */
data class IRApplyStatGrowth(
    val characterName: String,
    val newLevel: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement
