/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.ir.GameScopeContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemTest {
    @Test
    fun `item has correct id from property name`() {
        val item = createTestItem("potion") {}
        assertEquals("potion", item.id)
    }

    @Test
    fun `item default display name is capitalized id`() {
        val item = createTestItem("healingPotion") {}
        assertEquals("HealingPotion", item.displayName)
    }

    @Test
    fun `item name can be customized`() {
        val item = createTestItem("potion") { name("Healing Potion") }
        assertEquals("Healing Potion", item.displayName)
    }

    @Test
    fun `item description can be set`() {
        val item = createTestItem("potion") { description("Restores 50 HP") }
        assertEquals("Restores 50 HP", item.description)
    }

    @Test
    fun `consumable item defaults`() {
        val item = createTestItem("potion") { category(ItemCategory.CONSUMABLE) }
        assertEquals(ItemCategory.CONSUMABLE, item.category)
        assertEquals(99, item.maxStack)
        assertNull(item.equipSlot)
        assertTrue(item.isStackable)
        assertFalse(item.isEquippable)
    }

    @Test
    fun `weapon item defaults`() {
        val item = createTestItem("sword") { category(ItemCategory.WEAPON) }
        assertEquals(ItemCategory.WEAPON, item.category)
        assertEquals(1, item.maxStack)
        assertEquals(EquipmentSlot.WEAPON, item.equipSlot)
        assertFalse(item.isStackable)
        assertTrue(item.isEquippable)
    }

    @Test
    fun `armor item defaults`() {
        val item = createTestItem("platemail") { category(ItemCategory.ARMOR) }
        assertEquals(ItemCategory.ARMOR, item.category)
        assertEquals(1, item.maxStack)
        assertEquals(EquipmentSlot.ARMOR, item.equipSlot)
        assertTrue(item.isEquippable)
    }

    @Test
    fun `accessory item defaults`() {
        val item = createTestItem("ring") { category(ItemCategory.ACCESSORY) }
        assertEquals(ItemCategory.ACCESSORY, item.category)
        assertEquals(1, item.maxStack)
        assertEquals(EquipmentSlot.ACCESSORY, item.equipSlot)
        assertTrue(item.isEquippable)
    }

    @Test
    fun `key item defaults`() {
        val item = createTestItem("bossKey") { category(ItemCategory.KEY_ITEM) }
        assertEquals(ItemCategory.KEY_ITEM, item.category)
        assertTrue(item.isKeyItem)
        assertFalse(item.usableInBattle)
        assertTrue(item.usableOutOfBattle)
    }

    @Test
    fun `material item defaults`() {
        val item = createTestItem("ironOre") { category(ItemCategory.MATERIAL) }
        assertEquals(ItemCategory.MATERIAL, item.category)
        assertEquals(99, item.maxStack)
    }

    @Test
    fun `max stack can be customized`() {
        val item = createTestItem("throwingKnife") { maxStack(10) }
        assertEquals(10, item.maxStack)
    }

    @Test
    fun `price can be set`() {
        val item = createTestItem("potion") { price(buy = 100, sell = 50) }
        assertEquals(100, item.buyPrice)
        assertEquals(50, item.sellPrice)
    }

    @Test
    fun `price sell defaults to half of buy`() {
        val item = createTestItem("potion") { price(buy = 100) }
        assertEquals(100, item.buyPrice)
        assertEquals(50, item.sellPrice)
    }

    @Test
    fun `equip slot can be overridden`() {
        val item =
            createTestItem("dualRing") {
                category(ItemCategory.ACCESSORY)
                equipSlot(EquipmentSlot.ACCESSORY_2)
            }
        assertEquals(EquipmentSlot.ACCESSORY_2, item.equipSlot)
    }

    @Test
    fun `stat bonuses can be set`() {
        val item =
            createTestItem("sword") {
                category(ItemCategory.WEAPON)
                statBonus {
                    atk(10)
                    def(5)
                }
            }
        assertEquals(10, item.statBonuses[StatBonusType.ATK])
        assertEquals(5, item.statBonuses[StatBonusType.DEF])
    }

    @Test
    fun `all stat bonus types work`() {
        val item =
            createTestItem("gear") {
                statBonus {
                    atk(1)
                    def(2)
                    matk(3)
                    mdef(4)
                    agl(5)
                    maxHp(100)
                    maxSp(10)
                }
            }
        assertEquals(1, item.statBonuses[StatBonusType.ATK])
        assertEquals(2, item.statBonuses[StatBonusType.DEF])
        assertEquals(3, item.statBonuses[StatBonusType.MATK])
        assertEquals(4, item.statBonuses[StatBonusType.MDEF])
        assertEquals(5, item.statBonuses[StatBonusType.AGL])
        assertEquals(100, item.statBonuses[StatBonusType.MAX_HP])
        assertEquals(10, item.statBonuses[StatBonusType.MAX_SP])
    }

    @Test
    fun `usability flags can be configured`() {
        val item =
            createTestItem("fieldPotion") {
                usableInBattle(false)
                usableOutOfBattle(true)
            }
        assertFalse(item.usableInBattle)
        assertTrue(item.usableOutOfBattle)
    }

    @Test
    fun `item without onUse is not usable`() {
        val item = createTestItem("material") { category(ItemCategory.MATERIAL) }
        assertFalse(item.isUsable)
    }

    @Test
    fun `item with onUse is usable`() {
        val item = createTestItem("potion") { onUse { raw("// heal effect") } }
        assertTrue(item.isUsable)
        assertTrue(item.onUseStatements.isNotEmpty())
    }

    // Helper function to create test items
    private fun createTestItem(id: String, init: ItemBuilder.() -> Unit): Item {
        val builder = ItemBuilder(id)
        builder.init()
        return builder.build()
    }
}

class ItemStackTest {
    @Test
    fun `item stack has correct defaults`() {
        val item = ItemBuilder("potion").apply { maxStack(99) }.build()

        val stack = ItemStack(item, 1)
        assertEquals(item, stack.item)
        assertEquals(1, stack.quantity)
    }

    @Test
    fun `item stack can add more`() {
        val item = ItemBuilder("potion").apply { maxStack(99) }.build()

        val stack = ItemStack(item, 10)
        assertTrue(stack.canAddMore)
        assertEquals(89, stack.spaceRemaining)
    }

    @Test
    fun `item stack at max cannot add more`() {
        val item = ItemBuilder("potion").apply { maxStack(99) }.build()

        val stack = ItemStack(item, 99)
        assertFalse(stack.canAddMore)
        assertEquals(0, stack.spaceRemaining)
    }

    @Test
    fun `equipment stack cannot add more`() {
        val item = ItemBuilder("sword").apply { category(ItemCategory.WEAPON) }.build()

        val stack = ItemStack(item, 1)
        assertFalse(stack.canAddMore)
        assertEquals(0, stack.spaceRemaining)
    }
}

class ItemDelegateTest {
    @Test
    fun `item delegate registers item with game builder`() {
        val gameBuilder = GameBuilder("test")
        GameScopeContext.withScope(gameBuilder) {
            val potion by
                gameBuilder.item {
                    name("Potion")
                    category(ItemCategory.CONSUMABLE)
                }

            // Item should be registered and have index
            assertEquals(0, potion.itemIndex)
            assertEquals("Potion", potion.displayName)
        }
    }

    @Test
    fun `multiple items get sequential indices`() {
        val gameBuilder = GameBuilder("test")
        GameScopeContext.withScope(gameBuilder) {
            val potion by gameBuilder.item { name("Potion") }
            val ether by gameBuilder.item { name("Ether") }
            val elixir by gameBuilder.item { name("Elixir") }

            assertEquals(0, potion.itemIndex)
            assertEquals(1, ether.itemIndex)
            assertEquals(2, elixir.itemIndex)
        }
    }
}

class ItemCategoryTest {
    @Test
    fun `all item categories exist`() {
        val categories = ItemCategory.entries
        assertEquals(6, categories.size)
        assertTrue(ItemCategory.CONSUMABLE in categories)
        assertTrue(ItemCategory.WEAPON in categories)
        assertTrue(ItemCategory.ARMOR in categories)
        assertTrue(ItemCategory.ACCESSORY in categories)
        assertTrue(ItemCategory.KEY_ITEM in categories)
        assertTrue(ItemCategory.MATERIAL in categories)
    }
}

class EquipmentSlotTest {
    @Test
    fun `all built-in equipment slots exist`() {
        val slots = EquipmentSlot.BUILT_IN_SLOTS
        assertEquals(4, slots.size)
        assertTrue(EquipmentSlot.WEAPON in slots)
        assertTrue(EquipmentSlot.ARMOR in slots)
        assertTrue(EquipmentSlot.ACCESSORY in slots)
        assertTrue(EquipmentSlot.ACCESSORY_2 in slots)
    }

    @Test
    fun `equipment slots have correct IDs`() {
        assertEquals(0, EquipmentSlot.WEAPON.id)
        assertEquals(1, EquipmentSlot.ARMOR.id)
        assertEquals(2, EquipmentSlot.ACCESSORY.id)
        assertEquals(3, EquipmentSlot.ACCESSORY_2.id)
    }

    @Test
    fun `custom equipment slots get auto-assigned IDs`() {
        EquipmentSlot.resetCustomIdCounter()
        val ringSlot = EquipmentSlot.createCustom("Ring")
        val bootsSlot = EquipmentSlot.createCustom("Boots")

        assertEquals(4, ringSlot.id)
        assertEquals("Ring", ringSlot.name)
        assertEquals(5, bootsSlot.id)
        assertEquals("Boots", bootsSlot.name)

        // Reset for other tests
        EquipmentSlot.resetCustomIdCounter()
    }

    @Test
    fun `custom slot IDs continue from built-in slots`() {
        EquipmentSlot.resetCustomIdCounter()
        val custom1 = EquipmentSlot.createCustom("Custom 1")
        assertEquals(4, custom1.id) // Starts after ACCESSORY_2 (ID 3)

        val custom2 = EquipmentSlot.createCustom("Custom 2")
        assertEquals(5, custom2.id)

        // Reset for other tests
        EquipmentSlot.resetCustomIdCounter()
    }
}

class StatBonusTypeTest {
    @Test
    fun `all stat bonus types exist`() {
        val types = StatBonusType.entries
        assertEquals(9, types.size)
        assertTrue(StatBonusType.ATK in types)
        assertTrue(StatBonusType.DEF in types)
        assertTrue(StatBonusType.MATK in types)
        assertTrue(StatBonusType.MDEF in types)
        assertTrue(StatBonusType.AGL in types)
        assertTrue(StatBonusType.MAX_HP in types)
        assertTrue(StatBonusType.MAX_SP in types)
        assertTrue(StatBonusType.CRIT_RATE in types)
        assertTrue(StatBonusType.EVASION in types)
    }
}
