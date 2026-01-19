/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.gbGame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for equipment stat bonus calculations.
 *
 * Validates:
 * - Equipment slot definitions
 * - Stat bonus types
 * - Item equippability
 * - Bonus value retrieval
 */
class EquipmentBonusTest {

    @Test
    fun `all built-in equipment slots are defined`() {
        val slots = EquipmentSlot.BUILT_IN_SLOTS
        assertTrue(slots.contains(EquipmentSlot.WEAPON))
        assertTrue(slots.contains(EquipmentSlot.ARMOR))
        assertTrue(slots.contains(EquipmentSlot.ACCESSORY))
        assertTrue(slots.contains(EquipmentSlot.ACCESSORY_2))
        assertEquals(4, slots.size)
    }

    @Test
    fun `all stat bonus types are defined`() {
        val types = StatBonusType.entries
        assertTrue(types.contains(StatBonusType.ATK))
        assertTrue(types.contains(StatBonusType.DEF))
        assertTrue(types.contains(StatBonusType.MATK))
        assertTrue(types.contains(StatBonusType.MDEF))
        assertTrue(types.contains(StatBonusType.AGL))
        assertTrue(types.contains(StatBonusType.MAX_HP))
        assertTrue(types.contains(StatBonusType.MAX_SP))
    }

    @Test
    fun `weapon item has correct equipment slot`() {
        val sword =
            Item(
                id = "iron_sword",
                displayName = "Iron Sword",
                description = "A basic iron sword",
                category = ItemCategory.WEAPON,
                maxStack = 1,
                buyPrice = 100,
                sellPrice = 50,
                equipSlot = EquipmentSlot.WEAPON,
                statBonuses = mapOf(StatBonusType.ATK to 10),
                usableInBattle = false,
                usableOutOfBattle = false,
                onUseStatements = emptyList(),
            )

        assertTrue(sword.isEquippable)
        assertEquals(EquipmentSlot.WEAPON, sword.equipSlot)
        assertEquals(10, sword.statBonuses[StatBonusType.ATK])
    }

    @Test
    fun `armor item has defense bonus`() {
        val armor =
            Item(
                id = "leather_armor",
                displayName = "Leather Armor",
                description = "Light protection",
                category = ItemCategory.ARMOR,
                maxStack = 1,
                buyPrice = 150,
                sellPrice = 75,
                equipSlot = EquipmentSlot.ARMOR,
                statBonuses = mapOf(StatBonusType.DEF to 8, StatBonusType.MDEF to 2),
                usableInBattle = false,
                usableOutOfBattle = false,
                onUseStatements = emptyList(),
            )

        assertTrue(armor.isEquippable)
        assertEquals(EquipmentSlot.ARMOR, armor.equipSlot)
        assertEquals(8, armor.statBonuses[StatBonusType.DEF])
        assertEquals(2, armor.statBonuses[StatBonusType.MDEF])
    }

    @Test
    fun `accessory can have multiple stat bonuses`() {
        val ring =
            Item(
                id = "power_ring",
                displayName = "Power Ring",
                description = "Boosts all stats slightly",
                category = ItemCategory.ACCESSORY,
                maxStack = 1,
                buyPrice = 500,
                sellPrice = 250,
                equipSlot = EquipmentSlot.ACCESSORY,
                statBonuses =
                    mapOf(StatBonusType.ATK to 3, StatBonusType.DEF to 3, StatBonusType.AGL to 5),
                usableInBattle = false,
                usableOutOfBattle = false,
                onUseStatements = emptyList(),
            )

        assertTrue(ring.isEquippable)
        assertEquals(3, ring.statBonuses.size)
        assertEquals(5, ring.statBonuses[StatBonusType.AGL])
    }

    @Test
    fun `consumable items are not equippable`() {
        val potion =
            Item(
                id = "potion",
                displayName = "Potion",
                description = "Restores 50 HP",
                category = ItemCategory.CONSUMABLE,
                maxStack = 10,
                buyPrice = 50,
                sellPrice = 25,
                equipSlot = null,
                statBonuses = emptyMap(),
                usableInBattle = true,
                usableOutOfBattle = true,
                onUseStatements = emptyList(),
            )

        assertFalse(potion.isEquippable)
        assertEquals(null, potion.equipSlot)
        assertTrue(potion.statBonuses.isEmpty())
    }

    @Test
    fun `key items are not equippable`() {
        val key =
            Item(
                id = "castle_key",
                displayName = "Castle Key",
                description = "Opens the castle gate",
                category = ItemCategory.KEY_ITEM,
                maxStack = 1,
                buyPrice = 0,
                sellPrice = 0,
                equipSlot = null,
                statBonuses = emptyMap(),
                usableInBattle = false,
                usableOutOfBattle = true,
                onUseStatements = emptyList(),
            )

        assertFalse(key.isEquippable)
        assertEquals(ItemCategory.KEY_ITEM, key.category)
    }

    @Test
    fun `negative stat bonuses reduce stats`() {
        val cursedRing =
            Item(
                id = "cursed_ring",
                displayName = "Cursed Ring",
                description = "Powerful but cursed",
                category = ItemCategory.ACCESSORY,
                maxStack = 1,
                buyPrice = 1,
                sellPrice = 0,
                equipSlot = EquipmentSlot.ACCESSORY,
                statBonuses = mapOf(StatBonusType.ATK to 20, StatBonusType.DEF to -10),
                usableInBattle = false,
                usableOutOfBattle = false,
                onUseStatements = emptyList(),
            )

        assertEquals(20, cursedRing.statBonuses[StatBonusType.ATK])
        assertEquals(-10, cursedRing.statBonuses[StatBonusType.DEF])
    }

    @Test
    fun `hp and sp bonuses work on accessories`() {
        val healthAmulet =
            Item(
                id = "health_amulet",
                displayName = "Health Amulet",
                description = "Increases max HP",
                category = ItemCategory.ACCESSORY,
                maxStack = 1,
                buyPrice = 300,
                sellPrice = 150,
                equipSlot = EquipmentSlot.ACCESSORY,
                statBonuses = mapOf(StatBonusType.MAX_HP to 50, StatBonusType.MAX_SP to 20),
                usableInBattle = false,
                usableOutOfBattle = false,
                onUseStatements = emptyList(),
            )

        assertEquals(50, healthAmulet.statBonuses[StatBonusType.MAX_HP])
        assertEquals(20, healthAmulet.statBonuses[StatBonusType.MAX_SP])
    }

    // =========================================================================
    // CUSTOM EQUIPMENT SLOT TESTS
    // =========================================================================

    @Test
    fun `custom equipment slots can be defined via DSL`() {
        EquipmentSlot.resetCustomIdCounter()
        val game =
            gbGame("CustomSlotTest") {
                val ringSlot by equipmentSlot("Ring")
                val bootsSlot by equipmentSlot("Boots")

                val magicRing by item {
                    name("Magic Ring")
                    category(ItemCategory.ACCESSORY)
                    equipSlot(ringSlot)
                    statBonus { matk(5) }
                }

                val speedBoots by item {
                    name("Speed Boots")
                    category(ItemCategory.ACCESSORY)
                    equipSlot(bootsSlot)
                    statBonus { agl(10) }
                }

                start = scene("test") {}
            }

        // Verify custom slots are registered
        assertEquals(2, game.equipmentSlots.size)
        assertEquals("Ring", game.equipmentSlots[0].name)
        assertEquals("Boots", game.equipmentSlots[1].name)
        assertEquals(4, game.equipmentSlots[0].id) // After built-in slots
        assertEquals(5, game.equipmentSlots[1].id)

        // Reset for other tests
        EquipmentSlot.resetCustomIdCounter()
    }

    @Test
    fun `codegen generates custom slot constants`() {
        EquipmentSlot.resetCustomIdCounter()
        val game =
            gbGame("CustomSlotCodegenTest") {
                val ringSlot by equipmentSlot("Ring")
                val glovesSlot by equipmentSlot("Gloves")

                val hero by character {
                    stats {
                        hp(100)
                        atk(10)
                        def(10)
                        agl(10)
                    }
                }

                val ring by item {
                    category(ItemCategory.ACCESSORY)
                    equipSlot(ringSlot)
                    statBonus { matk(5) }
                }

                start = scene("test") {}
            }

        val output = game.compileForTest()

        // Should generate constants for custom slots
        assertTrue(output.contains("EQUIP_SLOT_RING"), "Should generate RING slot constant")
        assertTrue(output.contains("EQUIP_SLOT_GLOVES"), "Should generate GLOVES slot constant")

        // Slot count should include both built-in and custom slots
        assertTrue(
            output.contains("EQUIP_SLOT_COUNT 6u"), // 4 built-in + 2 custom
            "Should have correct total slot count",
        )

        // Reset for other tests
        EquipmentSlot.resetCustomIdCounter()
    }

    @Test
    fun `items can use custom equipment slots`() {
        EquipmentSlot.resetCustomIdCounter()
        val game =
            gbGame("CustomSlotItemTest") {
                val ringSlot by equipmentSlot("Ring")

                val hero by character {
                    stats {
                        hp(100)
                        atk(10)
                        def(10)
                        agl(10)
                    }
                }

                val magicRing by item {
                    equipSlot(ringSlot)
                    statBonus { matk(10) }
                }

                start = scene("test") {}
            }

        val output = game.compileForTest()

        // Should reference the custom slot in item data
        assertTrue(output.contains("EQUIP_RING"), "Item should use custom RING slot")

        // Reset for other tests
        EquipmentSlot.resetCustomIdCounter()
    }
}
