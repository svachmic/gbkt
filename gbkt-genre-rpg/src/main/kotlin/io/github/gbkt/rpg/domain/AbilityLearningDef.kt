/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.domain

// =============================================================================
// ABILITY LEARNING AND SKILL TREE DOMAIN TYPES
// =============================================================================
//
// Ability learning supports:
//   - AutoLearn: ability unlocked automatically when character reaches a level
//   - SkillPointUnlock: player spends skill points to unlock ability
//   - ItemTeach: teaching item consumed to unlock ability
//   - SkillTreeNode: prerequisite chain (must unlock prerequisites first)
//   - Mastery: use abilities to gain mastery levels and unlock evolutions
// =============================================================================

/**
 * Sealed base type for ability learning methods.
 *
 * Each subtype represents a different way a character can learn an ability.
 */
sealed interface LearningMethod {
    /** The ID of the ability being learned. */
    val abilityId: String
}

/**
 * Ability learned automatically when the character reaches a specific level.
 *
 * @param abilityId The ability to learn.
 * @param atLevel The character level at which the ability is learned.
 */
data class AutoLearn(override val abilityId: String, val atLevel: Int) : LearningMethod

/**
 * Ability unlocked by spending skill points.
 *
 * @param abilityId The ability to unlock.
 * @param cost Number of skill points required.
 */
data class SkillPointUnlock(override val abilityId: String, val cost: Int) : LearningMethod

/**
 * Ability learned by using a teaching item.
 *
 * @param abilityId The ability to learn.
 * @param itemId The teaching item that grants this ability when used.
 */
data class ItemTeach(override val abilityId: String, val itemId: String) : LearningMethod

/**
 * A node in a skill tree that can require prerequisites.
 *
 * @param abilityId The ability at this node.
 * @param prerequisites List of ability IDs that must be unlocked before this node.
 * @param cost Skill points required to unlock this node.
 */
data class SkillTreeNode(
    val abilityId: String,
    val prerequisites: List<String> = emptyList(),
    val cost: Int = 1,
)

/**
 * Full ability learning configuration for a character or class.
 *
 * @param methods List of learning methods (auto-learn, skill points, teaching items).
 * @param skillTree Skill tree nodes with prerequisite chains.
 * @param enableMastery Whether ability mastery system is active.
 * @param masteryLevels Number of mastery levels per ability (default 3).
 * @param evolutionChains Map of ability ID to the evolved ability ID unlocked at max mastery.
 */
data class AbilityLearningConfig(
    val methods: List<LearningMethod> = emptyList(),
    val skillTree: List<SkillTreeNode> = emptyList(),
    val enableMastery: Boolean = false,
    val masteryLevels: Int = 3,
    val evolutionChains: Map<String, String> = emptyMap(),
)
