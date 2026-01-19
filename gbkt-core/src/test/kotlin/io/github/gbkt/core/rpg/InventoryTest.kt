/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.GameScopeContext
import io.github.gbkt.core.ir.IRInventoryAddItem
import io.github.gbkt.core.ir.IRInventoryHasItem
import io.github.gbkt.core.ir.IRInventoryRemoveItem
import io.github.gbkt.core.ir.IRInventoryUseItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class InventoryTest {
    @Test
    fun `inventory has correct id from property name`() {
        val inventory = createTestInventory("playerInventory") {}
        assertEquals("playerInventory", inventory.id)
    }

    @Test
    fun `inventory default slots is 8`() {
        val inventory = createTestInventory("inv") {}
        assertEquals(8, inventory.maxSlots)
    }

    @Test
    fun `inventory slots can be customized`() {
        val inventory = createTestInventory("inv") { slots(16) }
        assertEquals(16, inventory.maxSlots)
    }

    @Test
    fun `inventory can have starting items`() {
        val potion = createTestItem("potion")
        val sword = createTestItem("sword")

        val inventory =
            createTestInventory("inv") {
                startingItems {
                    add(potion, 3)
                    add(sword, 1)
                }
            }

        assertEquals(2, inventory.startingItems.size)
        assertEquals(potion, inventory.startingItems[0].item)
        assertEquals(3, inventory.startingItems[0].quantity)
        assertEquals(sword, inventory.startingItems[1].item)
        assertEquals(1, inventory.startingItems[1].quantity)
    }

    @Test
    fun `inventory addItem emits IR`() {
        val potion = createTestItem("potion")
        val inventory = createTestInventory("inv") {}

        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { inventory.addItem(potion, 5) }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<IRInventoryAddItem>(stmt)
        assertEquals(inventory, stmt.inventory)
        assertEquals(potion, stmt.item)
        assertEquals(5, stmt.quantity)
    }

    @Test
    fun `inventory removeItem emits IR`() {
        val potion = createTestItem("potion")
        val inventory = createTestInventory("inv") {}

        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { inventory.removeItem(potion, 2) }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<IRInventoryRemoveItem>(stmt)
        assertEquals(inventory, stmt.inventory)
        assertEquals(potion, stmt.item)
        assertEquals(2, stmt.quantity)
    }

    @Test
    fun `inventory useItem emits IR`() {
        val potion = createTestItem("potion")
        val inventory = createTestInventory("inv") {}

        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { inventory.useItem(potion) }

        assertEquals(1, recorder.statements.size)
        val stmt = recorder.statements[0]
        assertIs<IRInventoryUseItem>(stmt)
        assertEquals(inventory, stmt.inventory)
        assertEquals(potion, stmt.item)
    }

    @Test
    fun `inventory hasItem returns condition wrapping IRInventoryHasItem`() {
        val potion = createTestItem("potion")
        val inventory = createTestInventory("inv") {}

        val condition = inventory.hasItem(potion, 3)
        assertIs<Condition>(condition)

        // The condition wraps an IRInventoryHasItem
        val ir = condition.ir
        assertIs<IRInventoryHasItem>(ir)
        assertEquals(inventory, ir.inventory)
        assertEquals(potion, ir.item)
        assertEquals(3, ir.quantity)
    }

    // Helper functions
    private fun createTestInventory(id: String, init: InventoryBuilder.() -> Unit): Inventory {
        val builder = InventoryBuilder(id)
        builder.init()
        return builder.build()
    }

    private fun createTestItem(id: String): Item {
        return ItemBuilder(id).apply { maxStack(99) }.build()
    }
}

class InventoryDelegateTest {
    @Test
    fun `inventory delegate registers inventory with game builder`() {
        val gameBuilder = GameBuilder("test")
        GameScopeContext.withScope(gameBuilder) {
            val playerInventory by gameBuilder.inventory { slots(8) }

            // Inventory should be registered and have index
            assertEquals(0, playerInventory.inventoryIndex)
            assertEquals(8, playerInventory.maxSlots)
        }
    }

    @Test
    fun `multiple inventories get sequential indices`() {
        val gameBuilder = GameBuilder("test")
        GameScopeContext.withScope(gameBuilder) {
            val playerInventory by gameBuilder.inventory { slots(8) }
            val shopInventory by gameBuilder.inventory { slots(16) }

            assertEquals(0, playerInventory.inventoryIndex)
            assertEquals(1, shopInventory.inventoryIndex)
        }
    }
}

class StartingItemsBuilderTest {
    @Test
    fun `starting items builder collects items`() {
        val potion = ItemBuilder("potion").apply { maxStack(99) }.build()
        val sword = ItemBuilder("sword").apply { category(ItemCategory.WEAPON) }.build()

        val builder = StartingItemsBuilder()
        builder.add(potion, 5)
        builder.add(sword, 1)

        assertEquals(2, builder.items.size)
        assertEquals(potion, builder.items[0].item)
        assertEquals(5, builder.items[0].quantity)
        assertEquals(sword, builder.items[1].item)
        assertEquals(1, builder.items[1].quantity)
    }
}
