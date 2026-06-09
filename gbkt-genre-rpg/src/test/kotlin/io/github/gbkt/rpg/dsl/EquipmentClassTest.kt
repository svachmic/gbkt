/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.rpg.domain.ClassDef
import io.github.gbkt.rpg.domain.EquipSlot
import io.github.gbkt.rpg.domain.EquipmentConfig
import io.github.gbkt.rpg.domain.JobChangeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// EQUIPMENT & CLASS BUILDER TESTS (Plan 06.5-04 success criterion SC-10, SC-13)
// 5 tests covering:
//   - equipmentSystem registers GenericSystem with correct config type
//   - equipment builder configures dual-wield and set bonuses
//   - characterClass registers GenericSystem
//   - class builder sets growth rates and learnable abilities
//   - delegate infers class name from property
// =============================================================================

class EquipmentClassTest {

    // =========================================================================
    // Test 1: equipmentSystem registers GenericSystem with correct config type
    // =========================================================================

    @Test
    fun `equipmentSystem registers GenericSystem with correct config type`() {
        val ir =
            game("EquipTest") {
                    equipmentSystem { slot(EquipSlot.WEAPON) }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "equipment_system" }
        assertNotNull(system, "Expected a system with id 'equipment_system'")
        assertIs<GenericSystem>(system, "equipmentSystem must produce GenericSystem")
        assertEquals("rpg_equipment_system", system.config["type"])
    }

    // =========================================================================
    // Test 2: equipment builder configures dual-wield and set bonuses
    // =========================================================================

    @Test
    fun `equipment builder configures dual-wield and set bonuses`() {
        val ir =
            game("EquipTest2") {
                    equipmentSystem {
                        dualWield()
                        set("dragon_set") {
                            name("Dragon Set")
                            tier(2) { modifier("def", flat = 5) }
                            tier(4) {
                                modifier("def", flat = 10)
                                modifier("atk", flat = 3)
                            }
                        }
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "equipment_system" } as? GenericSystem
        assertNotNull(system)
        val config = system.config["config"] as? EquipmentConfig
        assertNotNull(config, "Expected EquipmentConfig in system config")
        assertTrue(config.allowDualWield, "Expected dual-wield to be enabled")
        assertEquals(1, config.sets.size, "Expected 1 set definition")
        assertEquals("dragon_set", config.sets[0].id)
        assertEquals(2, config.sets[0].tiers.size, "Expected 2 set bonus tiers")
    }

    // =========================================================================
    // Test 3: characterClass registers GenericSystem
    // =========================================================================

    @Test
    fun `characterClass registers GenericSystem with correct config type`() {
        val ir =
            game("ClassTest") {
                    val warrior by characterClass {
                        name("Warrior")
                        growthRates {
                            hp(10)
                            atk(2)
                            def(1)
                        }
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "warrior" }
        assertNotNull(system, "Expected a system with id 'warrior'")
        assertIs<GenericSystem>(system, "characterClass must produce GenericSystem")
        assertEquals("rpg_class", system.config["type"])
    }

    // =========================================================================
    // Test 4: class builder sets growth rates and learnable abilities
    // =========================================================================

    @Test
    fun `class builder sets growth rates and learnable abilities`() {
        val ir =
            game("ClassTest2") {
                    val mage by characterClass {
                        name("Black Mage")
                        growthRates {
                            hp(5)
                            sp(8)
                            matk(4)
                        }
                        equips(EquipSlot.HEAD, EquipSlot.BODY, EquipSlot.ACCESSORY)
                        learns("fireball", atLevel = 3)
                        learns("blizzard", atLevel = 7)
                        jobChangeMode(JobChangeMode.SWITCHABLE_FRESH)
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "mage" } as? GenericSystem
        assertNotNull(system)
        val def = system.config["def"] as? ClassDef
        assertNotNull(def, "Expected ClassDef in system config")
        assertEquals("Black Mage", def.name)
        assertEquals(5, def.growthRates.hp, "Expected hp growth rate of 5")
        assertEquals(8, def.growthRates.sp, "Expected sp growth rate of 8")
        assertEquals(4, def.growthRates.matk, "Expected matk growth rate of 4")
        assertEquals(3, def.equipRestrictions.size, "Expected 3 equipment slots for mage")
        assertTrue(def.equipRestrictions.contains(EquipSlot.HEAD))
        assertFalse(def.equipRestrictions.contains(EquipSlot.WEAPON))
        assertEquals(2, def.learnableAbilities.size, "Expected 2 learnable abilities")
        assertEquals("fireball", def.learnableAbilities[0].abilityId)
        assertEquals(3, def.learnableAbilities[0].level)
        assertEquals(JobChangeMode.SWITCHABLE_FRESH, def.jobChangeMode)
    }

    // =========================================================================
    // Test 5: delegate infers class name from property
    // =========================================================================

    @Test
    fun `characterClass delegate infers name from property`() {
        val ir =
            game("DelegateTest") {
                    val knight by characterClass {
                        name("Knight")
                        growthRates {
                            hp(15)
                            def(3)
                        }
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val system = ir.systems.find { it.id == "knight" }
        assertNotNull(system, "Expected a system with id 'knight'")
        assertIs<GenericSystem>(system)
        val def = (system as GenericSystem).config["def"] as? ClassDef
        assertNotNull(def)
        assertEquals("Knight", def.name)
        assertEquals(15, def.growthRates.hp)
    }
}
