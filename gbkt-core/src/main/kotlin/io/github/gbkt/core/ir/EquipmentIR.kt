/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.SourceLocation
import io.github.gbkt.core.rpg.EquipmentSlot
import io.github.gbkt.core.rpg.StatBonusType

// =============================================================================
// EQUIPMENT IR NODES
// =============================================================================

/**
 * Equip an item to a character.
 *
 * Generates: equipment_equip(char_idx, slot, item_idx);
 */
data class IREquipItem(
    val characterName: String,
    val itemId: String,
    val slot: EquipmentSlot,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Unequip an item from a character slot.
 *
 * Generates: equipment_unequip(char_idx, slot);
 */
data class IRUnequipItem(
    val characterName: String,
    val slot: EquipmentSlot,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Get the item equipped in a slot.
 *
 * Expression: equipment_get_item(char_idx, slot) Returns item index or 255 if empty.
 */
data class IRGetEquippedItem(val characterName: String, val slot: EquipmentSlot) : IRExpression

/**
 * Check if a slot has equipment.
 *
 * Expression: equipment_has_item(char_idx, slot)
 */
data class IRHasEquippedItem(val characterName: String, val slot: EquipmentSlot) : IRExpression

/**
 * Get total equipment bonus for a stat.
 *
 * Expression: equipment_get_bonus(char_idx, stat_type)
 */
data class IRGetEquipmentBonus(val characterName: String, val statType: StatBonusType) :
    IRExpression

/**
 * Get character's effective stat (base + equipment + buffs).
 *
 * Expression: character_get_effective_stat(char_idx, stat_type)
 */
data class IRGetEffectiveStat(val characterName: String, val statType: StatBonusType) : IRExpression
