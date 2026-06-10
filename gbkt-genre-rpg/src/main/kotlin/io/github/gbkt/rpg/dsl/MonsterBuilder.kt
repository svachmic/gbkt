/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.rpg.domain.AwarenessLevel
import io.github.gbkt.rpg.domain.BehaviorNode
import io.github.gbkt.rpg.domain.CombatStats
import io.github.gbkt.rpg.domain.CurrencyRef
import io.github.gbkt.rpg.domain.DifficultyTier
import io.github.gbkt.rpg.domain.DropEntry
import io.github.gbkt.rpg.domain.MonsterDef
import io.github.gbkt.rpg.domain.MonsterRole
import io.github.gbkt.rpg.domain.MonsterTier

// =============================================================================
// DROP LIST BUILDER
// =============================================================================

/**
 * Builder for a list of [DropEntry] items.
 *
 * Used inside [MonsterBuilder.drops] blocks:
 * ```kotlin
 * drops {
 *     drop("herb", chance = 30)
 *     drop("goblin_tooth", chance = 10)
 * }
 * ```
 */
class DropListBuilder {
    private val entries: MutableList<DropEntry> = mutableListOf()

    /**
     * Adds an item drop entry.
     *
     * @param itemId The ID of the item to drop (references [io.github.gbkt.core.ir.ItemDef.id]).
     * @param chance Drop probability as a percentage (0-100).
     */
    fun drop(itemId: String, chance: Int) {
        require(chance in 0..100) { "Drop chance must be 0-100, got $chance" }
        entries.add(DropEntry(itemId = itemId, chance = chance))
    }

    /**
     * Adds a currency drop entry (H11).
     *
     * Generates a currency award in the monster's drop table codegen.
     *
     * ```kotlin
     * drops {
     *     drop("herb", chance = 30)
     *     dropCurrency(gold, amount = 50, chance = 100)
     * }
     * ```
     *
     * @param currency The [CurrencyRef] to award.
     * @param amount Amount of currency to drop.
     * @param chance Drop probability as a percentage (0-100).
     */
    fun dropCurrency(currency: CurrencyRef, amount: Int, chance: Int = 100) {
        require(chance in 0..100) { "Drop chance must be 0-100, got $chance" }
        entries.add(
            DropEntry(itemId = "", chance = chance, currencyRef = currency, amount = amount)
        )
    }

    /** Returns the accumulated drop list. */
    internal fun build(): List<DropEntry> = entries.toList()
}

// =============================================================================
// MONSTER BUILDER
// =============================================================================

/**
 * Builder for [MonsterDef]. Records monster name, stats, experience reward, AI behavior tree,
 * drops, role, difficulty tier, and cooldown configuration.
 *
 * @param id The unique identifier for the monster, passed from the DSL call site.
 */
class MonsterBuilder(val id: String) {
    private var monsterName: String = id
    private var stats: CombatStats = CombatStats(hp = 1, atk = 0, def = 0)
    private var expReward: Int = 0
    private var tier: MonsterTier = MonsterTier.COMMON
    private var behaviorTree: BehaviorNode? = null
    private var role: MonsterRole? = null
    private var awareness: AwarenessLevel = AwarenessLevel.SELF_ONLY
    private var difficulty: DifficultyTier = DifficultyTier.NORMAL
    private val drops: MutableList<DropEntry> = mutableListOf()
    private val abilityCooldowns: MutableMap<String, Int> = mutableMapOf()
    private var allowGlobalRepeatPrevention: Boolean = false

    /** Sets the display name for the monster. */
    fun name(n: String) {
        monsterName = n
    }

    /** Configures combat statistics using the [CombatStatsBuilder] DSL. */
    fun stats(block: CombatStatsBuilder.() -> Unit) {
        val builder = CombatStatsBuilder()
        builder.block()
        stats = builder.build()
    }

    /** Sets the experience points awarded to the player on defeating this monster. */
    fun exp(value: Int) {
        expReward = value
    }

    /**
     * Sets the monster's rarity/power tier.
     *
     * @param t The [MonsterTier] value (COMMON, UNCOMMON, RARE, BOSS).
     */
    fun tier(t: MonsterTier) {
        tier = t
    }

    /**
     * Sets the monster's combat role in group encounters.
     *
     * Used by [io.github.gbkt.rpg.domain.AllyHpBelow] conditions to target allies by role.
     *
     * @param r The [MonsterRole] value (TANK, HEALER, DPS, SUPPORT).
     */
    fun role(r: MonsterRole) {
        role = r
    }

    /**
     * Sets how much battlefield context the monster uses when making decisions.
     *
     * @param a The [AwarenessLevel] value.
     */
    fun awareness(a: AwarenessLevel) {
        awareness = a
    }

    /**
     * Sets the difficulty tier that modifies AI targeting intelligence.
     * - EASY: all targeting overridden to RANDOM
     * - NORMAL: behavior tree strategies used as-is
     * - HARD: targeting overridden to LOWEST_HP; prefers highest-damage abilities
     *
     * @param t The [DifficultyTier] value.
     */
    fun difficulty(t: DifficultyTier) {
        difficulty = t
    }

    /**
     * Configures the monster AI behavior tree.
     *
     * ```kotlin
     * ai {
     *     selector {
     *         hpBelow(25) { flee() }
     *         basicAttack()
     *     }
     * }
     * ```
     *
     * When not called, the monster defaults to basic-attack-only behavior.
     */
    fun ai(block: BehaviorTreeBuilder.() -> Unit) {
        val builder = BehaviorTreeBuilder()
        builder.block()
        behaviorTree = builder.build()
    }

    /**
     * Configures the monster's item drop list.
     *
     * ```kotlin
     * drops {
     *     drop("herb", chance = 30)
     *     drop("goblin_tooth", chance = 10)
     * }
     * ```
     */
    fun drops(block: DropListBuilder.() -> Unit) {
        val builder = DropListBuilder()
        builder.block()
        drops.addAll(builder.build())
    }

    /**
     * Registers an ability with a cooldown duration.
     *
     * Used alongside [io.github.gbkt.rpg.domain.CooldownNode] in behavior trees to gate abilities
     * behind per-turn timers.
     *
     * @param abilityId The ability ID to track.
     * @param turns Number of turns the cooldown lasts after the ability fires.
     */
    fun cooldown(abilityId: String, turns: Int) {
        abilityCooldowns[abilityId] = turns
    }

    /**
     * Enables global repeat-prevention for this monster's AI.
     *
     * When set, the generated `update_ai_<id>()` function emits a `_mon_<id>_last_action` UINT8
     * global and prevents the monster from using the same action twice in a row.
     */
    fun globalRepeatPrevention() {
        allowGlobalRepeatPrevention = true
    }

    /** Builds the [MonsterDef] domain object. */
    fun build(): MonsterDef =
        MonsterDef(
            id = id,
            name = monsterName,
            stats = stats,
            expReward = expReward,
            tier = tier,
            behaviorTree = behaviorTree,
            role = role,
            awareness = awareness,
            difficulty = difficulty,
            drops = drops.toList(),
            abilityCooldowns = abilityCooldowns.toMap(),
            allowGlobalRepeatPrevention = allowGlobalRepeatPrevention,
        )
}
