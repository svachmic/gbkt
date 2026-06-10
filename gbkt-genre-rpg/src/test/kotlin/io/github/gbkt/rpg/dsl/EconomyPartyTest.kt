/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.rpg.domain.AbilityLearningConfig
import io.github.gbkt.rpg.domain.AutoLearn
import io.github.gbkt.rpg.domain.AutoSaveTrigger
import io.github.gbkt.rpg.domain.LootTableDef
import io.github.gbkt.rpg.domain.MerchantDef
import io.github.gbkt.rpg.domain.PartyConfig
import io.github.gbkt.rpg.domain.Rarity
import io.github.gbkt.rpg.domain.RpgSaveConfig
import io.github.gbkt.rpg.domain.SaveMode
import io.github.gbkt.rpg.domain.SkillPointUnlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// ECONOMY / PARTY / SAVE / ABILITY LEARNING DSL BUILDER TESTS (Plan 06.5-07)
// 6 tests covering:
//   - merchant registers GenericSystem with stock and prices
//   - merchant flag-gated stock stored in config
//   - partySystem registers GenericSystem with active and reserve sizes
//   - rpgSave registers GenericSystem with NG+ config
//   - abilityLearning registers GenericSystem with skill tree
//   - lootTable registers GenericSystem with rarity entries
// =============================================================================

class EconomyPartyTest {

    // =========================================================================
    // Test 1: merchant registers GenericSystem with stock and prices
    // =========================================================================

    @Test
    fun `merchant registers GenericSystem with stock and prices`() {
        val ir =
            game("MerchantTest") {
                    merchant("blacksmith") {
                        name("Blacksmith")
                        item("iron_sword") { price(200) }
                        item("iron_shield") { price(150) }
                        sellRatio(40)
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "blacksmith" }
        assertNotNull(system, "Expected a system with id 'blacksmith'")
        assertIs<GenericSystem>(system, "merchant must produce GenericSystem")
        assertEquals("rpg_merchant", system.config["type"])

        val def = system.config["def"] as? MerchantDef
        assertNotNull(def, "Expected MerchantDef in system config")
        assertEquals("Blacksmith", def.name)
        assertEquals(2, def.stock.size, "Expected 2 shop items")
        assertEquals("iron_sword", def.stock[0].itemId)
        assertEquals(200, def.stock[0].price)
        assertEquals("iron_shield", def.stock[1].itemId)
        assertEquals(150, def.stock[1].price)
        assertEquals(40, def.sellRatio, "Expected sellRatio of 40")
    }

    // =========================================================================
    // Test 2: merchant flag-gated stock stored in config
    // =========================================================================

    @Test
    fun `merchant flag-gated stock stored in config`() {
        val ir =
            game("MerchantFlagTest") {
                    merchant("wizard_shop") {
                        name("Wizard Shop")
                        item("basic_wand") { price(100) }
                        flagStock("has_defeated_boss") {
                            item("legendary_staff") {
                                price(999)
                                sellPrice(600)
                            }
                        }
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "wizard_shop" } as? GenericSystem
        assertNotNull(system)
        val def = system.config["def"] as? MerchantDef
        assertNotNull(def, "Expected MerchantDef in system config")

        assertTrue(
            def.flagGatedStock.containsKey("has_defeated_boss"),
            "Expected flag-gated stock for 'has_defeated_boss'",
        )
        val flagItems = def.flagGatedStock["has_defeated_boss"]!!
        assertEquals(1, flagItems.size, "Expected 1 flag-gated item")
        assertEquals("legendary_staff", flagItems[0].itemId)
        assertEquals(999, flagItems[0].price)
        assertEquals(
            600,
            flagItems[0].sellPriceOverride,
            "Expected sell price override of 600 (GAP-10)",
        )
    }

    // =========================================================================
    // Test 3: partySystem registers GenericSystem with active and reserve sizes
    // =========================================================================

    @Test
    fun `partySystem registers GenericSystem with active and reserve sizes`() {
        val ir =
            game("PartyTest") {
                    partySystem {
                        maxActive(4)
                        reserve(enabled = true, size = 6, expShare = 25)
                        rowFormation(enabled = true, backDamage = 70, backDefense = 30)
                        member("hero")
                        guestMember("npc_ally")
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "party_system" }
        assertNotNull(system, "Expected a system with id 'party_system'")
        assertIs<GenericSystem>(system, "partySystem must produce GenericSystem")
        assertEquals("rpg_party_system", system.config["type"])

        val config = system.config["config"] as? PartyConfig
        assertNotNull(config, "Expected PartyConfig in system config")
        assertEquals(4, config.maxActiveSize, "Expected maxActiveSize of 4")
        assertTrue(config.enableReserve, "Expected reserve to be enabled")
        assertEquals(6, config.reserveSize, "Expected reserveSize of 6")
        assertEquals(25, config.reserveExpShare, "Expected reserveExpShare of 25")
        assertTrue(config.enableRowFormation, "Expected row formation to be enabled")
        assertEquals(70, config.backRowDamageMultiplier, "Expected backRowDamageMultiplier of 70")
        assertEquals(30, config.backRowDefenseBonus, "Expected backRowDefenseBonus of 30")
        assertEquals(2, config.initialMembers.size, "Expected 2 initial party members")
        assertEquals("hero", config.initialMembers[0].characterId)
        assertTrue(!config.initialMembers[0].isGuest, "hero should not be a guest")
        assertEquals("npc_ally", config.initialMembers[1].characterId)
        assertTrue(config.initialMembers[1].isGuest, "npc_ally should be a guest (GAP-4)")
    }

    // =========================================================================
    // Test 4: rpgSave registers GenericSystem with NG+ config
    // =========================================================================

    @Test
    fun `rpgSave registers GenericSystem with NG+ config`() {
        val ir =
            game("SaveTest") {
                    rpgSave {
                        slots(3)
                        mode(SaveMode.SAVE_ANYWHERE)
                        autoSave(AutoSaveTrigger.AFTER_BATTLE, AutoSaveTrigger.REST_AT_INN)
                        newGamePlus { carryOver("inventory", "gold", "abilities") }
                        previewFields("name", "level", "time", "location")
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "rpg_save" }
        assertNotNull(system, "Expected a system with id 'rpg_save'")
        assertIs<GenericSystem>(system, "rpgSave must produce GenericSystem")
        assertEquals("rpg_save", system.config["type"])

        val config = system.config["config"] as? RpgSaveConfig
        assertNotNull(config, "Expected RpgSaveConfig in system config")
        assertEquals(3, config.slotCount, "Expected 3 save slots")
        assertEquals(SaveMode.SAVE_ANYWHERE, config.saveMode)
        assertTrue(config.autoSaveEnabled, "Expected autoSave to be enabled")
        assertTrue(
            config.autoSaveTriggers.contains(AutoSaveTrigger.AFTER_BATTLE),
            "Expected AFTER_BATTLE trigger",
        )
        assertTrue(
            config.autoSaveTriggers.contains(AutoSaveTrigger.REST_AT_INN),
            "Expected REST_AT_INN trigger",
        )
        assertTrue(config.enableNewGamePlus, "Expected NG+ to be enabled")
        assertTrue(
            config.ngPlusCarryOver.contains("inventory"),
            "Expected 'inventory' in NG+ carry-over",
        )
        assertTrue(config.ngPlusCarryOver.contains("gold"), "Expected 'gold' in NG+ carry-over")
        assertEquals(
            listOf("name", "level", "time", "location"),
            config.savePreviewFields,
            "Expected 4 preview fields",
        )
    }

    // =========================================================================
    // Test 5: abilityLearning registers GenericSystem with skill tree
    // =========================================================================

    @Test
    fun `abilityLearning registers GenericSystem with skill tree`() {
        val ir =
            game("AbilityLearnTest") {
                    abilityLearning {
                        autoLearn("fire_ball", atLevel = 5)
                        autoLearn("blizzard", atLevel = 10)
                        skillPoint("meteor", cost = 3)
                        skillTree {
                            node("slash") { cost(1) }
                            node("power_slash") {
                                requires("slash")
                                cost(2)
                            }
                        }
                        mastery(enabled = true, levels = 3) {
                            evolves("fire_ball", into = "mega_fire")
                        }
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "ability_learning" }
        assertNotNull(system, "Expected a system with id 'ability_learning'")
        assertIs<GenericSystem>(system, "abilityLearning must produce GenericSystem")
        assertEquals("rpg_ability_learning", system.config["type"])

        val config = system.config["config"] as? AbilityLearningConfig
        assertNotNull(config, "Expected AbilityLearningConfig in system config")

        // Auto-learn methods
        val autoLearnMethods = config.methods.filterIsInstance<AutoLearn>()
        assertEquals(2, autoLearnMethods.size, "Expected 2 auto-learn methods")
        assertEquals("fire_ball", autoLearnMethods[0].abilityId)
        assertEquals(5, autoLearnMethods[0].atLevel)

        // Skill point methods
        val skillPointMethods = config.methods.filterIsInstance<SkillPointUnlock>()
        assertEquals(1, skillPointMethods.size, "Expected 1 skill point unlock")
        assertEquals("meteor", skillPointMethods[0].abilityId)
        assertEquals(3, skillPointMethods[0].cost)

        // Skill tree
        assertEquals(2, config.skillTree.size, "Expected 2 skill tree nodes")
        assertEquals("slash", config.skillTree[0].abilityId)
        assertEquals(
            0,
            config.skillTree[0].prerequisites.size,
            "slash should have no prerequisites",
        )
        assertEquals("power_slash", config.skillTree[1].abilityId)
        assertEquals(
            listOf("slash"),
            config.skillTree[1].prerequisites,
            "power_slash should require slash",
        )
        assertEquals(2, config.skillTree[1].cost)

        // Mastery
        assertTrue(config.enableMastery, "Expected mastery to be enabled")
        assertEquals(3, config.masteryLevels)
        assertEquals(
            mapOf("fire_ball" to "mega_fire"),
            config.evolutionChains,
            "Expected evolution chain",
        )
    }

    // =========================================================================
    // Test 6: lootTable registers GenericSystem with rarity entries
    // =========================================================================

    @Test
    fun `lootTable registers GenericSystem with rarity entries`() {
        val ir =
            game("LootTableTest") {
                    lootTable("goblin_drops") {
                        entry("gold_coin") { chance(60) }
                        entry("herb") {
                            chance(30)
                            rarity(Rarity.UNCOMMON)
                        }
                        entry("magic_gem") {
                            chance(5)
                            rarity(Rarity.RARE)
                            quantity(min = 1, max = 2)
                        }
                        guaranteed("goblin_fang")
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "goblin_drops" }
        assertNotNull(system, "Expected a system with id 'goblin_drops'")
        assertIs<GenericSystem>(system, "lootTable must produce GenericSystem")
        assertEquals("rpg_loot_table", system.config["type"])

        val def = system.config["def"] as? LootTableDef
        assertNotNull(def, "Expected LootTableDef in system config")
        assertEquals(3, def.entries.size, "Expected 3 loot entries")

        assertEquals("gold_coin", def.entries[0].itemId)
        assertEquals(60, def.entries[0].chance)
        assertEquals(Rarity.COMMON, def.entries[0].rarity)

        assertEquals("herb", def.entries[1].itemId)
        assertEquals(30, def.entries[1].chance)
        assertEquals(Rarity.UNCOMMON, def.entries[1].rarity)

        assertEquals("magic_gem", def.entries[2].itemId)
        assertEquals(5, def.entries[2].chance)
        assertEquals(Rarity.RARE, def.entries[2].rarity)
        assertEquals(1, def.entries[2].minQuantity)
        assertEquals(2, def.entries[2].maxQuantity)

        assertEquals("goblin_fang", def.guaranteedDrop, "Expected guaranteed drop")
    }
}
