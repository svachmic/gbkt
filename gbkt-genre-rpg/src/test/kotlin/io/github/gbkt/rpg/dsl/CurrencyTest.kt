/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.rpg.domain.CurrencyDef
import io.github.gbkt.rpg.domain.CurrencyRef
import io.github.gbkt.rpg.domain.MerchantDef
import io.github.gbkt.rpg.domain.MonsterDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// =============================================================================
// MULTI-CURRENCY DSL BUILDER TESTS (Plan 06.8-03, H11)
// 8 tests covering:
//   - currency builder with delegate registers correct GenericSystem
//   - multiple currencies can be defined and coexist
//   - exchange rates recorded correctly
//   - CurrencyRef equality based on id
//   - monster drop accepts CurrencyRef with amount and chance
//   - merchant item with per-item currency pricing
//   - backward compat — null currency uses default
//   - currency max capped correctly
// =============================================================================

class CurrencyTest {

    // =========================================================================
    // Test 1: currency builder with delegate registers correct GenericSystem
    // =========================================================================

    @Test
    fun `currency builder registers GenericSystem with correct type`() {
        val ir =
            game("CurrencyNameTest") {
                    val gold by currency { max(9999) }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "gold" }
        assertNotNull(system, "Expected a system with id 'gold'")
        assertIs<GenericSystem>(system, "currency must produce GenericSystem")
        assertEquals("rpg_currency", system.config["type"])

        val def = system.config["def"] as? CurrencyDef
        assertNotNull(def, "Expected CurrencyDef in system config")
        assertEquals("gold", def.id)
        assertEquals(9999, def.max)
        assertTrue(def.exchanges.isEmpty(), "Expected no exchanges for gold")
    }

    // =========================================================================
    // Test 2: multiple currencies can be defined and coexist
    // =========================================================================

    @Test
    fun `multiple currencies can be defined and coexist`() {
        val ir =
            game("MultiCurrencyTest") {
                    val gold by currency { max(9999) }
                    val gems by currency { max(99) }
                    val tokens by currency { max(255) }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val goldSystem = ir.systems.find { it.id == "gold" }
        val gemsSystem = ir.systems.find { it.id == "gems" }
        val tokensSystem = ir.systems.find { it.id == "tokens" }

        assertNotNull(goldSystem, "Expected gold system")
        assertNotNull(gemsSystem, "Expected gems system")
        assertNotNull(tokensSystem, "Expected tokens system")

        val goldDef = (goldSystem as? GenericSystem)?.config?.get("def") as? CurrencyDef
        val gemsDef = (gemsSystem as? GenericSystem)?.config?.get("def") as? CurrencyDef
        val tokensDef = (tokensSystem as? GenericSystem)?.config?.get("def") as? CurrencyDef

        assertNotNull(goldDef)
        assertNotNull(gemsDef)
        assertNotNull(tokensDef)

        assertEquals(9999, goldDef.max)
        assertEquals(99, gemsDef.max)
        assertEquals(255, tokensDef.max)
    }

    // =========================================================================
    // Test 3: exchange rates recorded correctly
    // =========================================================================

    @Test
    fun `exchange rates recorded correctly`() {
        val ir =
            game("ExchangeRateTest") {
                    val gold by currency { max(9999) }
                    val gems by currency {
                        max(99)
                        exchange(to = gold, rate = 100) // 1 gem = 100 gold
                    }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val gemsSystem = ir.systems.find { it.id == "gems" } as? GenericSystem
        assertNotNull(gemsSystem, "Expected gems system")
        val def = gemsSystem.config["def"] as? CurrencyDef
        assertNotNull(def)

        assertEquals(1, def.exchanges.size, "Expected 1 exchange rate for gems")
        assertEquals("gold", def.exchanges[0].toId)
        assertEquals(100, def.exchanges[0].rate)
    }

    // =========================================================================
    // Test 4: CurrencyRef equality based on id
    // =========================================================================

    @Test
    fun `CurrencyRef equality based on id`() {
        val ref1 = CurrencyRef("gold")
        val ref2 = CurrencyRef("gold")
        val ref3 = CurrencyRef("gems")

        assertEquals(ref1, ref2, "Same id CurrencyRef must be equal")
        assertTrue(ref1 != ref3, "Different id CurrencyRef must not be equal")
        assertEquals("gold", ref1.id)
    }

    // =========================================================================
    // Test 5: monster drop accepts CurrencyRef with amount and chance
    // =========================================================================

    @Test
    fun `monster drop accepts CurrencyRef with amount and chance`() {
        val ir =
            game("MonsterDropCurrencyTest") {
                    val gold by currency { max(9999) }
                    monster("goblin") {
                        name("Goblin")
                        stats {
                            hp(30)
                            atk(5)
                            def(2)
                        }
                        exp(15)
                        drops {
                            drop("herb", chance = 30)
                            drop("goblin_tooth", chance = 10)
                            dropCurrency(gold, amount = 50, chance = 100)
                        }
                    }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val monsterSystem = ir.systems.find { it.id == "goblin" } as? GenericSystem
        assertNotNull(monsterSystem)
        val def = monsterSystem.config["def"] as? MonsterDef
        assertNotNull(def)

        // Item drops
        val itemDrops = def.drops.filter { it.currencyRef == null }
        assertEquals(2, itemDrops.size, "Expected 2 item drops")
        assertEquals("herb", itemDrops[0].itemId)
        assertEquals(30, itemDrops[0].chance)

        // Currency drop
        val currencyDrops = def.drops.filter { it.currencyRef != null }
        assertEquals(1, currencyDrops.size, "Expected 1 currency drop")
        assertEquals("gold", currencyDrops[0].currencyRef?.id)
        assertEquals(50, currencyDrops[0].amount)
        assertEquals(100, currencyDrops[0].chance)
    }

    // =========================================================================
    // Test 6: merchant item with per-item currency pricing
    // =========================================================================

    @Test
    fun `merchant item with per-item currency pricing`() {
        val ir =
            game("MerchantCurrencyTest") {
                    val gold by currency { max(9999) }
                    val gems by currency { max(99) }
                    merchant("magic_shop") {
                        name("Magic Shop")
                        item("iron_sword") {
                            price(200)
                            currency(gold)
                        }
                        item("rare_ring") {
                            price(5)
                            currency(gems)
                        }
                    }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val shopSystem = ir.systems.find { it.id == "magic_shop" } as? GenericSystem
        assertNotNull(shopSystem)
        val def = shopSystem.config["def"] as? MerchantDef
        assertNotNull(def)

        assertEquals(2, def.stock.size, "Expected 2 shop items")

        val sword = def.stock[0]
        assertEquals("iron_sword", sword.itemId)
        assertEquals(200, sword.price)
        assertEquals("gold", sword.currencyRef?.id, "Expected gold currency for iron_sword")

        val ring = def.stock[1]
        assertEquals("rare_ring", ring.itemId)
        assertEquals(5, ring.price)
        assertEquals("gems", ring.currencyRef?.id, "Expected gems currency for rare_ring")
    }

    // =========================================================================
    // Test 7: backward compat — null currency uses default
    // =========================================================================

    @Test
    fun `backward compat null currency uses default`() {
        val ir =
            game("BackwardCompatTest") {
                    merchant("blacksmith") {
                        name("Blacksmith")
                        item("iron_sword") { price(200) }
                        item("iron_shield") { price(150) }
                    }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "blacksmith" } as? GenericSystem
        assertNotNull(system)
        val def = system.config["def"] as? MerchantDef
        assertNotNull(def)

        // Default merchant has no currencyRef (uses legacy currencyName)
        assertNull(
            def.currencyRef,
            "Expected null currencyRef for legacy merchant (backward compat)",
        )
        assertEquals("Gold", def.currencyName, "Expected legacy currencyName = 'Gold'")

        // Items have no per-item currency
        def.stock.forEach { item ->
            assertNull(item.currencyRef, "Expected null currencyRef for legacy shop items")
        }
    }

    // =========================================================================
    // Test 8: currency max defaults to 9999
    // =========================================================================

    @Test
    fun `currency max defaults to 9999`() {
        val ir =
            game("CurrencyDefaultMaxTest") {
                    val gold by currency {} // no max() call — should default to 9999
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "gold" } as? GenericSystem
        assertNotNull(system)
        val def = system.config["def"] as? CurrencyDef
        assertNotNull(def)
        assertEquals(9999, def.max, "Default max should be 9999")
    }
}
