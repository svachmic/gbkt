/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "MatchingDeclarationName"
) // File contains multiple top-level declarations (behavior tree hierarchy)

package io.github.gbkt.rpg.domain

// =============================================================================
// BEHAVIOR TREE HIERARCHY
// =============================================================================
//
// Sealed hierarchies for monster AI behavior trees.
// Plain Kotlin types — NOT IR types.
//
// Design constraint: behavior trees compile to FLAT iterative C code (no recursion).
// The Game Boy stack is ~128 bytes — recursive tree evaluation would overflow.
// The codegen (RpgVisitor) walks the tree depth-first and emits flat C if/else chains.
//
// Node types:
//   - SelectorNode (OR): first child that succeeds wins
//   - SequenceNode (AND): all children must succeed
//   - ConditionNode: leaf — evaluates a MonsterCondition predicate
//   - ActionNode: leaf — executes a MonsterAction
//   - PhaseThresholdNode: HP-threshold boss phase gating
//   - CooldownNode: ability cooldown gate
// =============================================================================

/**
 * Sealed hierarchy for behavior tree nodes.
 *
 * All nodes are plain Kotlin data classes/objects — NOT IR types. The tree is compiled to flat
 * iterative C code by
 * [io.github.gbkt.backend.gbdk.codegen.visitor.RpgVisitor.generateMonsterAIFunctions].
 */
sealed interface BehaviorNode

/**
 * Selector node (OR): evaluates children in order; succeeds on the first child that succeeds.
 *
 * Emits as a chain of `if (...) { ... } else if (...) { ... }` statements in C.
 */
data class SelectorNode(val children: List<BehaviorNode>) : BehaviorNode

/**
 * Sequence node (AND): evaluates all children in order; all must succeed.
 *
 * Emits as sequential statements in C (no branching between them).
 */
data class SequenceNode(val children: List<BehaviorNode>) : BehaviorNode

/**
 * Condition node: leaf that evaluates a [MonsterCondition] predicate.
 *
 * Emits as a CIf condition guard around its containing action chain.
 */
data class ConditionNode(val predicate: MonsterCondition) : BehaviorNode

/**
 * Action node: leaf that executes a [MonsterAction].
 *
 * Emits a CCall to the appropriate action C function.
 */
data class ActionNode(val action: MonsterAction) : BehaviorNode

/**
 * Phase threshold node: gates a sub-tree on a monster HP percentage threshold.
 *
 * Used for boss phase transitions. Emits `if (_mon_<id>_hp_pct < hpPercent)` guard in C.
 *
 * @property hpPercent HP percentage below which this phase is active (e.g., 50 for "below 50% HP").
 * @property tree The behavior tree to execute in this phase.
 */
data class PhaseThresholdNode(val hpPercent: Int, val tree: BehaviorNode) : BehaviorNode

/**
 * Cooldown node: gates a child node on an ability cooldown timer.
 *
 * Emits `if (_mon_<id>_cd_<abilityId> == 0)` guard and decrements the cooldown counter each AI
 * update cycle.
 *
 * @property abilityId The ability ID this cooldown is tracking.
 * @property cooldownTurns Number of turns the cooldown lasts after the ability is used.
 * @property child The behavior node to gate behind the cooldown check.
 */
data class CooldownNode(val abilityId: String, val cooldownTurns: Int, val child: BehaviorNode) :
    BehaviorNode

// =============================================================================
// MONSTER CONDITION TYPES
// =============================================================================

/**
 * Sealed hierarchy for monster AI condition predicates.
 *
 * Used in [ConditionNode] to express guards evaluated before executing actions.
 */
sealed interface MonsterCondition

/** Condition: monster HP percentage is below [percent]. */
data class HpBelow(val percent: Int) : MonsterCondition

/** Condition: monster HP percentage is above [percent]. */
data class HpAbove(val percent: Int) : MonsterCondition

/**
 * Condition: an ally (of a given [role]) has HP percentage below [percent].
 *
 * When [role] is null, checks any ally.
 */
data class AllyHpBelow(val role: MonsterRole?, val percent: Int) : MonsterCondition

/** Condition: the current turn count is above [count]. */
data class TurnCountAbove(val count: Int) : MonsterCondition

/** Condition: always true — unconditionally proceeds to action. */
data object Always : MonsterCondition

// =============================================================================
// MONSTER ACTION TYPES
// =============================================================================

/**
 * Sealed hierarchy for monster AI actions.
 *
 * Used in [ActionNode] to express what a monster does when its conditions are met.
 */
sealed interface MonsterAction

/**
 * Action: use a registered ability by ID.
 *
 * Emits a CCall to `use_ability_<abilityId>()`.
 */
data class UseAbility(val abilityId: String) : MonsterAction

/**
 * Action: perform a basic attack using the given targeting strategy.
 *
 * @property targetStrategy How the target is selected (default: random).
 */
data class BasicAttack(val targetStrategy: TargetStrategy = TargetStrategy.RANDOM) : MonsterAction

/**
 * Action: attempt to flee from combat.
 *
 * @property chance Percentage chance to flee successfully (0-100). Default: 100.
 */
data class Flee(val chance: Int = 100) : MonsterAction

/**
 * Action: summon additional monsters into the encounter.
 *
 * @property monsterId The ID of the monster definition to summon.
 * @property count Number of monsters to summon. Default: 1.
 */
data class Summon(val monsterId: String, val count: Int = 1) : MonsterAction

/**
 * Action: start charging an ability over multiple turns before it fires.
 *
 * Emits a telegraph state: monster announces the ability this turn; it fires on turn N+chargeTurns.
 *
 * @property abilityId The ability that will be executed after charging.
 * @property chargeTurns Number of turns to charge before execution.
 */
data class ChargeAction(val abilityId: String, val chargeTurns: Int) : MonsterAction

// =============================================================================
// MONSTER AI SUPPORT ENUMS
// =============================================================================

/** Determines how the monster selects its target when executing an attack or ability. */
enum class TargetStrategy {
    /** Selects a random party member. Used by EASY difficulty and as default. */
    RANDOM,

    /** Selects the party member with the lowest current HP. Used by HARD difficulty. */
    LOWEST_HP,

    /** Selects the party member with the highest accumulated threat (aggro). */
    HIGHEST_THREAT,

    /** Selects a target matching a specific party role. */
    BY_ROLE,
}

/** Combat role of a monster in a group encounter. Used for [AllyHpBelow] ally targeting. */
enum class MonsterRole {
    TANK,
    HEALER,
    DPS,
    SUPPORT,
}

/** How much battlefield context the monster considers when making AI decisions. */
enum class AwarenessLevel {
    /** Monster only considers its own state (HP, cooldowns). Default for most enemies. */
    SELF_ONLY,

    /** Monster considers its target's state (HP, status effects). */
    TARGET_AWARE,

    /** Monster considers the full battlefield (all allies, all enemies, all effects). */
    FULL_CONTEXT,
}

/** Difficulty tier that modifies AI targeting and ability selection intelligence. */
enum class DifficultyTier {
    /** EASY: all targeting overridden to RANDOM (dumb AI). */
    EASY,

    /** NORMAL: behavior tree's specified strategies used as-is. Default. */
    NORMAL,

    /** HARD: targeting overridden to LOWEST_HP; prefers highest-damage abilities. */
    HARD,
}
