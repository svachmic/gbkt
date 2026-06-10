/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.domain

/**
 * A single drop entry: either an item drop or a currency drop.
 *
 * For item drops: set [itemId] and leave [currencyRef] and [amount] as defaults. For currency
 * drops: set [currencyRef] and [amount], leave [itemId] empty.
 *
 * @property itemId The ID of the item to drop (references [io.github.gbkt.core.ir.ItemDef.id]).
 *   Empty string for currency drops.
 * @property chance Drop probability as a percentage (0-100).
 * @property currencyRef If non-null, this is a currency drop instead of an item drop.
 * @property amount Amount of currency to drop (only used when [currencyRef] is non-null).
 */
data class DropEntry(
    val itemId: String,
    val chance: Int,
    val currencyRef: CurrencyRef? = null,
    val amount: Int = 0,
)

/**
 * Domain data class representing a monster/enemy definition.
 *
 * Plain Kotlin data class — NOT an IR type. Used by [io.github.gbkt.rpg.dsl.MonsterBuilder] to
 * carry monster data. Added to encounter pools in [SimpleBattleDef].
 *
 * All fields beyond [id], [name], and [stats] have backward-compatible defaults so that existing
 * call sites (e.g., `monster("goblin") { name("Goblin"); stats { hp(10) } }`) continue to compile
 * without changes.
 *
 * @property id Unique identifier used in encounter definitions.
 * @property name Display name shown in battle UI.
 * @property stats Combat statistics (HP, SP, ATK, DEF, MATK, MDEF, AGL).
 * @property expReward Experience points awarded on defeating this monster. Default 0.
 * @property tier Rarity/power tier. Default [MonsterTier.COMMON].
 * @property behaviorTree Root behavior tree node for AI. null = basic attack only.
 * @property role Combat role of this monster in group encounters. null = no specific role.
 * @property awareness How much battlefield context this monster uses. Default
 *   [AwarenessLevel.SELF_ONLY].
 * @property difficulty Difficulty modifier affecting targeting intelligence. Default
 *   [DifficultyTier.NORMAL].
 * @property drops Item drops awarded on defeat.
 * @property abilityCooldowns Map of abilityId to cooldown duration in turns.
 * @property allowGlobalRepeatPrevention When true, monster cannot use the same action twice in a
 *   row. Emits `_mon_<id>_last_action` UINT8 global and adds repeat-prevention guard in
 *   `update_ai_<id>()`.
 */
data class MonsterDef(
    val id: String,
    val name: String,
    val stats: CombatStats,
    val expReward: Int = 0,
    val tier: MonsterTier = MonsterTier.COMMON,
    val behaviorTree: BehaviorNode? = null,
    val role: MonsterRole? = null,
    val awareness: AwarenessLevel = AwarenessLevel.SELF_ONLY,
    val difficulty: DifficultyTier = DifficultyTier.NORMAL,
    val drops: List<DropEntry> = emptyList(),
    val abilityCooldowns: Map<String, Int> = emptyMap(),
    val allowGlobalRepeatPrevention: Boolean = false,
)
