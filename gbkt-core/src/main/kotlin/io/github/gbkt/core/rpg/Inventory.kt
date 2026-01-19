/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.IRInventoryAddItem
import io.github.gbkt.core.ir.IRInventoryHasItem
import io.github.gbkt.core.ir.IRInventoryRemoveItem
import io.github.gbkt.core.ir.IRInventoryUseItem
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// INVENTORY SYSTEM
// =============================================================================

/**
 * An inventory container that holds items.
 *
 * Typical GB RPGs have 8-16 inventory slots due to memory constraints.
 *
 * Usage:
 * ```kotlin
 * val playerInventory by inventory {
 *     slots(8)
 *     startingItems {
 *         add(potion, 3)
 *         add(ironSword, 1)
 *     }
 * }
 *
 * // In game logic
 * whenever(buttons.start.pressed) {
 *     playerInventory.addItem(potion, 1)
 * }
 * ```
 */
class Inventory(
    /** Unique identifier for this inventory */
    val id: String,
    /** Maximum number of item slots */
    val maxSlots: Int,
    /** Starting items when game begins */
    val startingItems: List<ItemStack>,
    /** Inventory index for code generation (assigned by GameBuilder) */
    var inventoryIndex: Int = -1,
) {
    /**
     * Add an item to this inventory (emits IR statement).
     *
     * Usage:
     * ```kotlin
     * playerInventory.addItem(potion, 1)
     * ```
     */
    fun addItem(item: Item, quantity: Int = 1) {
        require(quantity > 0) { "Quantity must be positive" }
        RecordingContext.require().emit(IRInventoryAddItem(this, item, quantity))
    }

    /**
     * Remove an item from this inventory (emits IR statement).
     *
     * Usage:
     * ```kotlin
     * playerInventory.removeItem(potion, 1)
     * ```
     */
    fun removeItem(item: Item, quantity: Int = 1) {
        require(quantity > 0) { "Quantity must be positive" }
        RecordingContext.require().emit(IRInventoryRemoveItem(this, item, quantity))
    }

    /**
     * Use an item from this inventory (removes it and triggers onUse).
     *
     * Usage:
     * ```kotlin
     * playerInventory.useItem(potion)
     * ```
     */
    fun useItem(item: Item) {
        RecordingContext.require().emit(IRInventoryUseItem(this, item))
    }

    /**
     * Check if inventory contains at least the specified quantity of an item.
     *
     * Returns a Condition for use in whenever blocks.
     *
     * Usage:
     * ```kotlin
     * whenever(playerInventory.hasItem(key, 1)) {
     *     openDoor()
     * }
     * ```
     */
    fun hasItem(item: Item, quantity: Int = 1): Condition {
        require(quantity > 0) { "Quantity must be positive" }
        return Condition(IRInventoryHasItem(this, item, quantity))
    }
}

// =============================================================================
// INVENTORY BUILDER
// =============================================================================

/**
 * Property delegate for inventories.
 *
 * Usage: val playerInventory by inventory { ... }
 */
class InventoryDelegate(
    private val gameBuilder: GameBuilder,
    private val init: InventoryBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Inventory>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, Inventory> {
        val builder = InventoryBuilder(property.name)
        builder.init()
        val inventory = builder.build()
        gameBuilder.registerInventory(inventory)

        return ReadOnlyProperty { _, _ -> inventory }
    }
}

/** Builder for inventory construction via DSL. */
@GbktDsl
class InventoryBuilder(private val inventoryId: String) {
    private var maxSlots: Int = 8
    private val startingItems = mutableListOf<ItemStack>()

    /**
     * Set the maximum number of inventory slots.
     *
     * @param count Number of slots (typical GB value: 8-16)
     */
    fun slots(count: Int) {
        require(count in 1..99) { "Slots must be 1-99, got: $count" }
        this.maxSlots = count
    }

    /**
     * Define starting items in this inventory.
     *
     * Usage:
     * ```kotlin
     * startingItems {
     *     add(potion, 3)
     *     add(ironSword, 1)
     * }
     * ```
     */
    fun startingItems(init: StartingItemsBuilder.() -> Unit) {
        val builder = StartingItemsBuilder()
        builder.init()
        startingItems.addAll(builder.items)
    }

    internal fun build(): Inventory {
        // Validate that starting items don't exceed max slots
        // Each unique item type takes one slot
        val uniqueItemCount = startingItems.distinctBy { it.item.id }.size
        require(uniqueItemCount <= maxSlots) {
            "Inventory '$inventoryId' has $uniqueItemCount unique starting items but only $maxSlots slots"
        }

        // Merge stacks for same items and validate combined quantities
        val mergedItems =
            startingItems
                .groupBy { it.item.id }
                .map { (_, stacks) ->
                    val item = stacks.first().item
                    val totalQty = stacks.sumOf { it.quantity }
                    require(totalQty <= item.maxStack) {
                        "Combined quantity for ${item.displayName} ($totalQty) exceeds maxStack (${item.maxStack})"
                    }
                    ItemStack(item, totalQty)
                }

        return Inventory(id = inventoryId, maxSlots = maxSlots, startingItems = mergedItems)
    }
}

/** Builder for defining starting items in an inventory. */
@GbktDsl
class StartingItemsBuilder {
    internal val items = mutableListOf<ItemStack>()

    /** Add items to the starting inventory. */
    fun add(item: Item, quantity: Int = 1) {
        require(quantity in 1..item.maxStack) {
            "Quantity must be 1-${item.maxStack} for ${item.displayName}, got: $quantity"
        }
        items.add(ItemStack(item, quantity))
    }
}

// =============================================================================
// GAME BUILDER EXTENSION
// =============================================================================

/**
 * Create an inventory container.
 *
 * Usage:
 * ```kotlin
 * val playerInventory by inventory {
 *     slots(8)
 *     startingItems {
 *         add(potion, 3)
 *     }
 * }
 * ```
 */
fun GameBuilder.inventory(init: InventoryBuilder.() -> Unit): InventoryDelegate {
    return InventoryDelegate(this, init)
}
