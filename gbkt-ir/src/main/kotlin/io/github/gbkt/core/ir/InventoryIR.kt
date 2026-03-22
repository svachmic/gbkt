/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// INVENTORY IR TYPES
// =============================================================================

/**
 * A category of items with shared default stacking rules.
 *
 * Game authors define their own categories (e.g., CONSUMABLE, WEAPON, KEY_ITEM). Items inherit the
 * category's [defaultMaxStack] unless they override it with their own [ItemDef.maxStack].
 *
 * @property id Unique category identifier (used in [ItemDef.categoryId] and codegen).
 * @property defaultMaxStack Default maximum stack size for items in this category. Items with a
 *   null [ItemDef.maxStack] inherit this value at codegen time.
 */
data class ItemCategoryDef(val id: String, val defaultMaxStack: Int = 1)

/**
 * A single item definition in the item catalog.
 *
 * @property id Unique identifier for this item (used in script ops and drop tables).
 * @property name Display name shown in menus and dialogs.
 * @property categoryId References the [ItemCategoryDef.id] this item belongs to.
 * @property maxStack Per-item stack limit override. When null, inherits
 *   [ItemCategoryDef.defaultMaxStack] from the referenced category.
 * @property effects List of effects applied when the item is used.
 * @property buyPrice Cost in the in-game currency when purchased from a shop. 0 = not for sale.
 * @property dropWeight Relative weight for drop table selection. 0 = never dropped randomly.
 */
data class ItemDef(
    val id: String,
    val name: String,
    val categoryId: String,
    val maxStack: Int? = null,
    val effects: List<ItemEffectIR> = emptyList(),
    val buyPrice: Int = 0,
    val dropWeight: Int = 0,
)

/**
 * Interface for item effect IR nodes.
 *
 * NOT sealed — the RPG module (gbkt-rpg) extends it with domain-specific effects such as stat
 * restoration, status cures, and ability unlocks. Engine base effects are [HealEffect],
 * [BuffEffect], and [ScriptEffect].
 *
 * Backends use `is` checks or a registry pattern to dispatch on concrete effect types.
 */
interface ItemEffectIR

/**
 * Restores a fixed amount of HP when the item is used.
 *
 * @property amount Flat HP restoration amount.
 */
data class HealEffect(val amount: Int) : ItemEffectIR

/**
 * Temporarily boosts a named stat by a fixed amount for a number of turns.
 *
 * @property statId ID of the stat to buff (e.g., "atk", "def", "agl").
 * @property amount Flat amount added to the stat.
 * @property duration Number of turns the buff lasts.
 */
data class BuffEffect(val statId: String, val amount: Int, val duration: Int) : ItemEffectIR

/**
 * Escape-hatch effect that runs arbitrary script ops when the item is used.
 *
 * Use for effects that don't fit [HealEffect] or [BuffEffect], such as teleportation, flag setting,
 * or complex multi-step game logic.
 *
 * @property ops Script ops executed when this effect is applied.
 */
data class ScriptEffect(val ops: List<ScriptOp>) : ItemEffectIR

/**
 * An inventory container (bag, chest, etc.) with a fixed slot count and optional category filter.
 *
 * @property id Unique identifier for this container.
 * @property slots Maximum number of item stacks the container can hold.
 * @property categoryFilter When set, this container only accepts items from the given category ID.
 *   Null means the container accepts items from all categories.
 */
data class ContainerIR(val id: String, val slots: Int, val categoryFilter: String? = null)

/**
 * A drop table for weighted random item selection (enemy drops, chest loot, etc.).
 *
 * @property id Unique identifier for this drop table.
 * @property entries Weighted entries. Total weight is the denominator for probability calculation.
 */
data class DropTableIR(val id: String, val entries: List<DropEntryIR> = emptyList())

/**
 * A single entry in a drop table.
 *
 * @property itemId References the [ItemDef.id] to drop.
 * @property weight Relative drop weight — higher values mean more common drops.
 * @property minCount Minimum number of items dropped per selection.
 * @property maxCount Maximum number of items dropped per selection.
 */
data class DropEntryIR(
    val itemId: String,
    val weight: Int,
    val minCount: Int = 1,
    val maxCount: Int = 1,
)
