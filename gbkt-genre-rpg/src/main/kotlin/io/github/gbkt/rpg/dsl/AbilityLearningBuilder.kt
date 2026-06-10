/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.rpg.domain.AbilityLearningConfig
import io.github.gbkt.rpg.domain.AutoLearn
import io.github.gbkt.rpg.domain.ItemTeach
import io.github.gbkt.rpg.domain.LearningMethod
import io.github.gbkt.rpg.domain.SkillPointUnlock
import io.github.gbkt.rpg.domain.SkillTreeNode

// =============================================================================
// ABILITY LEARNING DSL BUILDER
// =============================================================================

/**
 * Builder for [AbilityLearningConfig] — ability learning and skill trees.
 *
 * ```kotlin
 * abilityLearning {
 *     autoLearn("fireball", atLevel = 5)
 *     autoLearn("blizzard", atLevel = 10)
 *     skillPoint("meteor", cost = 3)
 *     teachItem("holy_sword", itemId = "angel_feather")
 *     skillTree {
 *         node("slash") { cost(1) }
 *         node("power_slash") { requires("slash"); cost(2) }
 *     }
 *     mastery(enabled = true, levels = 3) {
 *         evolves("fire_ball", into = "mega_fire")
 *     }
 * }
 * ```
 */
class AbilityLearningBuilder {
    private val methods = mutableListOf<LearningMethod>()
    private val skillTree = mutableListOf<SkillTreeNode>()
    private var enableMastery: Boolean = false
    private var masteryLevels: Int = 3
    private val evolutionChains = mutableMapOf<String, String>()

    /** Configures an ability to be learned automatically at a specific level. */
    fun autoLearn(abilityId: String, atLevel: Int) {
        methods.add(AutoLearn(abilityId = abilityId, atLevel = atLevel))
    }

    /** Configures an ability unlockable by spending skill points. */
    fun skillPoint(abilityId: String, cost: Int) {
        methods.add(SkillPointUnlock(abilityId = abilityId, cost = cost))
    }

    /** Configures an ability learned by using a teaching item. */
    fun teachItem(abilityId: String, itemId: String) {
        methods.add(ItemTeach(abilityId = abilityId, itemId = itemId))
    }

    /** Adds skill tree nodes with prerequisite chains. */
    fun skillTree(block: SkillTreeBuilder.() -> Unit) {
        val builder = SkillTreeBuilder()
        builder.block()
        skillTree.addAll(builder.nodes)
    }

    /**
     * Enables the mastery system.
     *
     * @param enabled Whether mastery tracking is active.
     * @param levels Number of mastery levels per ability (default 3).
     * @param block Optional block for registering evolution chains.
     */
    fun mastery(enabled: Boolean = true, levels: Int = 3, block: MasteryBuilder.() -> Unit = {}) {
        enableMastery = enabled
        masteryLevels = levels
        val builder = MasteryBuilder()
        builder.block()
        evolutionChains.putAll(builder.chains)
    }

    fun build(): AbilityLearningConfig =
        AbilityLearningConfig(
            methods = methods.toList(),
            skillTree = skillTree.toList(),
            enableMastery = enableMastery,
            masteryLevels = masteryLevels,
            evolutionChains = evolutionChains.toMap(),
        )
}

/** Builder for individual skill tree nodes used inside [AbilityLearningBuilder.skillTree]. */
class SkillTreeNodeBuilder(val abilityId: String) {
    private val prerequisites = mutableListOf<String>()
    private var cost: Int = 1

    /** Adds a prerequisite ability that must be unlocked before this node. */
    fun requires(abilityId: String) {
        prerequisites.add(abilityId)
    }

    /** Sets the skill point cost to unlock this node. */
    fun cost(n: Int) {
        cost = n
    }

    fun build(): SkillTreeNode =
        SkillTreeNode(abilityId = abilityId, prerequisites = prerequisites.toList(), cost = cost)
}

/** Nested builder for skill tree nodes. */
class SkillTreeBuilder {
    internal val nodes = mutableListOf<SkillTreeNode>()

    /** Adds a skill tree node. */
    fun node(abilityId: String, block: SkillTreeNodeBuilder.() -> Unit = {}) {
        val builder = SkillTreeNodeBuilder(abilityId)
        builder.block()
        nodes.add(builder.build())
    }
}

/** Nested builder for mastery evolution chains. */
class MasteryBuilder {
    internal val chains = mutableMapOf<String, String>()

    /** Registers that the given ability evolves into another ability at max mastery. */
    fun evolves(abilityId: String, into: String) {
        chains[abilityId] = into
    }
}
