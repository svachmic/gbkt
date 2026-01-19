/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.IREquipItem
import io.github.gbkt.core.ir.IRUnequipItem

// =============================================================================
// EQUIPMENT SYSTEM
// =============================================================================

/**
 * Tracks equipped items for a character.
 *
 * Each character has a set of equipment slots (weapon, armor, accessory, etc.) that can hold
 * equippable items. Equipment provides stat bonuses that are applied to the character's effective
 * stats.
 *
 * Usage:
 * ```kotlin
 * val hero by character {
 *     equipment {
 *         slot(EquipmentSlot.WEAPON, ironSword)
 *         slot(EquipmentSlot.ARMOR, leatherArmor)
 *     }
 * }
 *
 * // In game logic
 * hero.equipment.equip(mythrilSword)
 * hero.equipment.unequip(EquipmentSlot.WEAPON)
 * ```
 */
class CharacterEquipment(
    /** Character this equipment belongs to */
    private val characterName: String,
    /** Initial equipment by slot */
    private val initialEquipment: Map<EquipmentSlot, Item>,
) {
    /** Current equipment state (for runtime/testing) */
    private val equippedItems = initialEquipment.toMutableMap()

    /**
     * Equip an item to its designated slot.
     *
     * The item must have an equipment slot defined. This will replace any item currently in that
     * slot.
     *
     * @param item The item to equip (must be equippable)
     * @throws IllegalArgumentException if item is not equippable
     */
    fun equip(item: Item) {
        val slot =
            requireNotNull(item.equipSlot) {
                "Item '${item.id}' is not equippable - no equipment slot defined"
            }
        equippedItems[slot] = item
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IREquipItem(characterName, item.id, slot))
        }
    }

    /**
     * Unequip the item in a slot.
     *
     * @param slot The slot to unequip
     * @return The unequipped item, or null if slot was empty
     */
    fun unequip(slot: EquipmentSlot): Item? {
        val removed = equippedItems.remove(slot)
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRUnequipItem(characterName, slot))
        }
        return removed
    }

    /**
     * Get the item equipped in a slot.
     *
     * @param slot The slot to check
     * @return The equipped item, or null if empty
     */
    fun getEquipped(slot: EquipmentSlot): Item? = equippedItems[slot]

    /** Check if a slot has an item equipped. */
    fun hasEquipped(slot: EquipmentSlot): Boolean = slot in equippedItems

    /** Get all currently equipped items. */
    fun getAll(): Map<EquipmentSlot, Item> = equippedItems.toMap()

    /**
     * Calculate total stat bonus from all equipment.
     *
     * @param statType The stat to calculate bonus for
     * @return Total bonus value (can be negative for penalties)
     */
    fun getTotalBonus(statType: StatBonusType): Int {
        return equippedItems.values.sumOf { item -> item.statBonuses[statType] ?: 0 }
    }

    /** Get ATK bonus from equipment (shorthand). */
    val atkBonus: Int
        get() = getTotalBonus(StatBonusType.ATK)

    /** Get DEF bonus from equipment (shorthand). */
    val defBonus: Int
        get() = getTotalBonus(StatBonusType.DEF)

    /** Get MATK bonus from equipment (shorthand). */
    val matkBonus: Int
        get() = getTotalBonus(StatBonusType.MATK)

    /** Get MDEF bonus from equipment (shorthand). */
    val mdefBonus: Int
        get() = getTotalBonus(StatBonusType.MDEF)

    /** Get AGL bonus from equipment (shorthand). */
    val aglBonus: Int
        get() = getTotalBonus(StatBonusType.AGL)

    /** Get max HP bonus from equipment. */
    val maxHpBonus: Int
        get() = getTotalBonus(StatBonusType.MAX_HP)

    /** Get max SP bonus from equipment. */
    val maxSpBonus: Int
        get() = getTotalBonus(StatBonusType.MAX_SP)
}

// =============================================================================
// EQUIPMENT BUILDER
// =============================================================================

/** Builder for character equipment configuration. */
@GbktDsl
class EquipmentBuilder(private val characterName: String) {
    private val equipment = mutableMapOf<EquipmentSlot, Item>()

    /** Set starting equipment for a slot. */
    fun slot(slot: EquipmentSlot, item: Item) {
        require(item.equipSlot == slot) {
            "Item '${item.id}' cannot be equipped in slot $slot - it uses slot ${item.equipSlot}"
        }
        equipment[slot] = item
    }

    /** Equip a weapon. */
    fun weapon(item: Item) = slot(EquipmentSlot.WEAPON, item)

    /** Equip armor. */
    fun armor(item: Item) = slot(EquipmentSlot.ARMOR, item)

    /** Equip an accessory. */
    fun accessory(item: Item) = slot(EquipmentSlot.ACCESSORY, item)

    /** Equip a secondary accessory. */
    fun accessory2(item: Item) = slot(EquipmentSlot.ACCESSORY_2, item)

    internal fun build() = CharacterEquipment(characterName, equipment.toMap())
}
