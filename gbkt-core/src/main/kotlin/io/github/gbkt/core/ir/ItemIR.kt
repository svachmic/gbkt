/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.SourceLocation
import io.github.gbkt.core.rpg.Inventory
import io.github.gbkt.core.rpg.Item

// =============================================================================
// ITEM IR NODES - Item and inventory operations
// =============================================================================

/**
 * Add an item to an inventory.
 *
 * Generates: inventory_add_item(&inventory, ITEM_id, quantity);
 */
data class IRInventoryAddItem(
    val inventory: Inventory,
    val item: Item,
    val quantity: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Remove an item from an inventory.
 *
 * Generates: inventory_remove_item(&inventory, ITEM_id, quantity);
 */
data class IRInventoryRemoveItem(
    val inventory: Inventory,
    val item: Item,
    val quantity: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Use an item from inventory (remove and trigger onUse effect).
 *
 * Generates: if (inventory_has_item(&inventory, ITEM_id, 1)) { inventory_remove_item(&inventory,
 * ITEM_id, 1); // onUse statements }
 */
data class IRInventoryUseItem(
    val inventory: Inventory,
    val item: Item,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Check if inventory contains at least a certain quantity of an item.
 *
 * Expression: inventory_has_item(&inventory, ITEM_id, quantity)
 */
data class IRInventoryHasItem(val inventory: Inventory, val item: Item, val quantity: Int) :
    IRExpression

/**
 * Get the quantity of an item in inventory.
 *
 * Expression: inventory_get_quantity(&inventory, ITEM_id)
 */
data class IRInventoryGetQuantity(val inventory: Inventory, val item: Item) : IRExpression

/**
 * Check if inventory is full (no empty slots).
 *
 * Expression: inventory_is_full(&inventory)
 */
data class IRInventoryIsFull(val inventory: Inventory) : IRExpression

/**
 * Check if inventory is empty (no items).
 *
 * Expression: inventory_is_empty(&inventory)
 */
data class IRInventoryIsEmpty(val inventory: Inventory) : IRExpression

/**
 * Get the number of empty slots in inventory.
 *
 * Expression: inventory_empty_slots(&inventory)
 */
data class IRInventoryEmptySlots(val inventory: Inventory) : IRExpression

/**
 * Clear all items from an inventory.
 *
 * Generates: inventory_clear(&inventory);
 */
data class IRInventoryClear(
    val inventory: Inventory,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

// Note: IREquipItem and IRUnequipItem are defined in EquipmentIR.kt

/**
 * Check if a specific item is equipped.
 *
 * Expression: character_has_equipped(&character, ITEM_id)
 */
data class IRIsEquipped(val characterName: String, val item: Item) : IRExpression
