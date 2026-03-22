/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.rpg.domain.ActionNode
import io.github.gbkt.rpg.domain.BasicAttack
import io.github.gbkt.rpg.domain.BehaviorNode
import io.github.gbkt.rpg.domain.ChargeAction
import io.github.gbkt.rpg.domain.ConditionNode
import io.github.gbkt.rpg.domain.CooldownNode
import io.github.gbkt.rpg.domain.Flee
import io.github.gbkt.rpg.domain.HpBelow
import io.github.gbkt.rpg.domain.PhaseThresholdNode
import io.github.gbkt.rpg.domain.SelectorNode
import io.github.gbkt.rpg.domain.SequenceNode
import io.github.gbkt.rpg.domain.Summon
import io.github.gbkt.rpg.domain.TargetStrategy
import io.github.gbkt.rpg.domain.UseAbility

// =============================================================================
// BEHAVIOR TREE BUILDER
// =============================================================================
//
// DSL builder for composing monster behavior trees from the RPG DSL.
//
// Usage inside MonsterBuilder.ai { ... } blocks:
//
//   ai {
//       selector {
//           hpBelow(25) {
//               flee()
//           }
//           cooldown("fireball", turns = 3) {
//               useAbility("fireball")
//           }
//           basicAttack()
//       }
//   }
//
// The resulting BehaviorNode tree is stored in MonsterDef.behaviorTree and compiled
// to flat iterative C code by RpgVisitor (no recursion at runtime).
// =============================================================================

/**
 * Builder for composing [BehaviorNode] trees via a Kotlin DSL.
 *
 * Typically used inside [MonsterBuilder.ai] blocks. The root node of the tree is retrieved via
 * [build]. If multiple top-level nodes are added, they are automatically wrapped in a
 * [SequenceNode].
 *
 * ```kotlin
 * ai {
 *     selector {
 *         hpBelow(25) { flee() }
 *         basicAttack()
 *     }
 * }
 * ```
 */
class BehaviorTreeBuilder {
    private val nodes: MutableList<BehaviorNode> = mutableListOf()

    // =========================================================================
    // Composite nodes
    // =========================================================================

    /**
     * Adds a selector (OR) node: evaluates children in order; the first successful child wins.
     *
     * In generated C code, emits `if (...) { ... } else if (...) { ... }` chains.
     */
    fun selector(block: BehaviorTreeBuilder.() -> Unit): SelectorNode {
        val childBuilder = BehaviorTreeBuilder()
        childBuilder.block()
        val node = SelectorNode(childBuilder.collectChildren())
        nodes.add(node)
        return node
    }

    /**
     * Adds a sequence (AND) node: evaluates all children in order; all must execute.
     *
     * In generated C code, emits sequential statements.
     */
    fun sequence(block: BehaviorTreeBuilder.() -> Unit): SequenceNode {
        val childBuilder = BehaviorTreeBuilder()
        childBuilder.block()
        val node = SequenceNode(childBuilder.collectChildren())
        nodes.add(node)
        return node
    }

    // =========================================================================
    // Conditional wrappers
    // =========================================================================

    /**
     * Adds a [PhaseThresholdNode] that gates a sub-tree when monster HP is below [percent].
     *
     * ```kotlin
     * hpBelow(25) {
     *     flee(chance = 80)
     * }
     * ```
     *
     * Emits `if (_mon_<id>_hp_pct < 25) { ... }` in C.
     */
    fun hpBelow(percent: Int, block: BehaviorTreeBuilder.() -> Unit) {
        val childBuilder = BehaviorTreeBuilder()
        childBuilder.block()
        val inner = childBuilder.build()
        nodes.add(PhaseThresholdNode(hpPercent = percent, tree = inner))
    }

    /**
     * Adds a [ConditionNode] wrapping [HpBelow] followed by a sub-tree.
     *
     * Alias that matches natural language: "when HP below 25%".
     */
    fun hpBelowCondition(percent: Int): BehaviorTreeBuilder {
        nodes.add(ConditionNode(HpBelow(percent)))
        return this
    }

    /**
     * Adds a [CooldownNode] that gates [block] behind an ability cooldown.
     *
     * ```kotlin
     * cooldown("fireball", turns = 3) {
     *     useAbility("fireball")
     * }
     * ```
     *
     * @param abilityId The ability ID tracked by this cooldown.
     * @param turns Number of turns the cooldown lasts after firing.
     */
    fun cooldown(abilityId: String, turns: Int, block: BehaviorTreeBuilder.() -> Unit) {
        val childBuilder = BehaviorTreeBuilder()
        childBuilder.block()
        val inner = childBuilder.build()
        nodes.add(CooldownNode(abilityId = abilityId, cooldownTurns = turns, child = inner))
    }

    // =========================================================================
    // Action leaf nodes
    // =========================================================================

    /**
     * Adds an [ActionNode] for a basic attack.
     *
     * @param target Targeting strategy for the attack. Default: [TargetStrategy.RANDOM].
     */
    fun basicAttack(target: TargetStrategy = TargetStrategy.RANDOM) {
        nodes.add(ActionNode(BasicAttack(target)))
    }

    /**
     * Adds an [ActionNode] to use a registered ability by ID.
     *
     * ```kotlin
     * useAbility("fireball")
     * ```
     */
    fun useAbility(abilityId: String) {
        nodes.add(ActionNode(UseAbility(abilityId)))
    }

    /**
     * Adds an [ActionNode] to attempt fleeing combat.
     *
     * @param chance Percentage chance to flee successfully (0-100). Default: 100.
     */
    fun flee(chance: Int = 100) {
        nodes.add(ActionNode(Flee(chance)))
    }

    /**
     * Adds an [ActionNode] to summon additional monsters into the encounter.
     *
     * @param monsterId The ID of the monster definition to summon.
     * @param count Number of monsters to summon. Default: 1.
     */
    fun summon(monsterId: String, count: Int = 1) {
        nodes.add(ActionNode(Summon(monsterId, count)))
    }

    /**
     * Adds an [ActionNode] to charge an ability over multiple turns before execution.
     *
     * @param abilityId The ability to execute after charging.
     * @param turns Number of turns to charge.
     */
    fun charge(abilityId: String, turns: Int) {
        nodes.add(ActionNode(ChargeAction(abilityId, turns)))
    }

    // =========================================================================
    // Build
    // =========================================================================

    /**
     * Returns all collected children as a flat list.
     *
     * Internal — used by [selector] and [sequence] to collect their children.
     */
    internal fun collectChildren(): List<BehaviorNode> = nodes.toList()

    /**
     * Builds the root [BehaviorNode] from the accumulated children.
     * - If exactly one node was added: returns it directly.
     * - If multiple nodes were added: wraps them in a [SequenceNode].
     * - If no nodes were added: returns a single [ActionNode] with [BasicAttack] as safe default.
     */
    fun build(): BehaviorNode =
        when (nodes.size) {
            0 -> ActionNode(BasicAttack())
            1 -> nodes.first()
            else -> SequenceNode(nodes.toList())
        }
}
