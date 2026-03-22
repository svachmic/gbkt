/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.rpg.domain.EncounterDef
import io.github.gbkt.rpg.domain.EncounterSlotDef
import io.github.gbkt.rpg.domain.MonsterTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// =============================================================================
// SIMPLE BATTLE BUILDER TESTS (GAP-05)
// Verifies multi-monster encounter slot DSL: EncounterBuilder.slot() with
// per-slot level/tier overrides. Also verifies backward-compatible monsterIds
// computed property on EncounterDef.
// =============================================================================

class SimpleBattleBuilderTest {

    // Helper: build EncounterDef via EncounterBuilder
    private fun buildEncounter(block: EncounterBuilder.() -> Unit): EncounterDef =
        EncounterBuilder().apply(block).build()

    // =========================================================================
    // Unary-plus syntax — creates slot with no overrides
    // =========================================================================

    @Test
    fun `unary plus creates slot with no level or tier override`() {
        val monster =
            io.github.gbkt.rpg.domain.MonsterDef(
                id = "goblin",
                name = "Goblin",
                tier = MonsterTier.COMMON,
                stats = io.github.gbkt.rpg.domain.CombatStats(hp = 30, atk = 8, def = 5),
            )

        val encounter = buildEncounter { +monster }

        assertEquals(1, encounter.slots.size)
        val slot = encounter.slots[0]
        assertEquals("goblin", slot.monsterId)
        assertNull(slot.level, "Unary-plus slot should have null level")
        assertNull(slot.tier, "Unary-plus slot should have null tier")
    }

    // =========================================================================
    // slot(MonsterDef, level, tier) — explicit overrides
    // =========================================================================

    @Test
    fun `slot(MonsterDef, level, tier) creates slot with all overrides`() {
        val kobold =
            io.github.gbkt.rpg.domain.MonsterDef(
                id = "kobold",
                name = "Kobold",
                tier = MonsterTier.COMMON,
                stats = io.github.gbkt.rpg.domain.CombatStats(hp = 20, atk = 6, def = 4),
            )

        val encounter = buildEncounter { slot(kobold, level = 8, tier = MonsterTier.UNCOMMON) }

        assertEquals(1, encounter.slots.size)
        val slot = encounter.slots[0]
        assertEquals("kobold", slot.monsterId)
        assertEquals(8, slot.level)
        assertEquals(MonsterTier.UNCOMMON, slot.tier)
    }

    @Test
    fun `slot(MonsterDef) with only level override leaves tier null`() {
        val kobold =
            io.github.gbkt.rpg.domain.MonsterDef(
                id = "kobold",
                name = "Kobold",
                tier = MonsterTier.COMMON,
                stats = io.github.gbkt.rpg.domain.CombatStats(hp = 20, atk = 6, def = 4),
            )

        val encounter = buildEncounter { slot(kobold, level = 5) }

        val slot = encounter.slots[0]
        assertEquals(5, slot.level)
        assertNull(slot.tier, "Tier should be null when not provided")
    }

    // =========================================================================
    // slot(String, level, tier) — string-based overload
    // =========================================================================

    @Test
    fun `slot(String, level, tier) creates EncounterSlotDef with string ID`() {
        val encounter = buildEncounter { slot("kobold", level = 6, tier = MonsterTier.COMMON) }

        assertEquals(1, encounter.slots.size)
        val slot = encounter.slots[0]
        assertEquals("kobold", slot.monsterId)
        assertEquals(6, slot.level)
        assertEquals(MonsterTier.COMMON, slot.tier)
    }

    // =========================================================================
    // Multi-monster encounters — multiple slots per encounter
    // =========================================================================

    @Test
    fun `multiple slots create multi-monster encounter`() {
        val goblin =
            io.github.gbkt.rpg.domain.MonsterDef(
                id = "goblin",
                name = "Goblin",
                tier = MonsterTier.COMMON,
                stats = io.github.gbkt.rpg.domain.CombatStats(hp = 30, atk = 8, def = 5),
            )
        val kobold =
            io.github.gbkt.rpg.domain.MonsterDef(
                id = "kobold",
                name = "Kobold",
                tier = MonsterTier.COMMON,
                stats = io.github.gbkt.rpg.domain.CombatStats(hp = 20, atk = 6, def = 4),
            )

        val encounter = buildEncounter {
            +goblin
            +kobold
            slot(kobold, level = 8, tier = MonsterTier.UNCOMMON)
        }

        assertEquals(3, encounter.slots.size)
        assertEquals("goblin", encounter.slots[0].monsterId)
        assertEquals("kobold", encounter.slots[1].monsterId)
        assertEquals("kobold", encounter.slots[2].monsterId)
        assertEquals(8, encounter.slots[2].level)
        assertEquals(MonsterTier.UNCOMMON, encounter.slots[2].tier)
    }

    // =========================================================================
    // EncounterDef.monsterIds — backward-compatible computed property
    // =========================================================================

    @Test
    fun `monsterIds property returns flat list of monster IDs from slots`() {
        val encounter =
            EncounterDef(
                slots =
                    listOf(
                        EncounterSlotDef(monsterId = "goblin"),
                        EncounterSlotDef(monsterId = "kobold", level = 8),
                        EncounterSlotDef(monsterId = "goblin", tier = MonsterTier.UNCOMMON),
                    ),
                weight = 30,
            )

        assertEquals(listOf("goblin", "kobold", "goblin"), encounter.monsterIds)
    }

    // =========================================================================
    // EncounterDef.fromIds() — backward-compatible factory for flat lists
    // =========================================================================

    @Test
    fun `EncounterDef fromIds creates slots with no level or tier`() {
        val encounter = EncounterDef.fromIds(listOf("goblin", "kobold"), weight = 20)

        assertEquals(2, encounter.slots.size)
        assertEquals("goblin", encounter.slots[0].monsterId)
        assertEquals("kobold", encounter.slots[1].monsterId)
        assertNull(encounter.slots[0].level)
        assertNull(encounter.slots[0].tier)
        assertEquals(20, encounter.weight)
    }

    @Test
    fun `EncounterDef fromIds monsterIds matches input list`() {
        val ids = listOf("goblin", "orc", "troll")
        val encounter = EncounterDef.fromIds(ids)
        assertEquals(ids, encounter.monsterIds)
    }

    // =========================================================================
    // Weight — default and explicit
    // =========================================================================

    @Test
    fun `weight defaults to 1 when not set`() {
        val encounter = buildEncounter { slot("goblin") }
        assertEquals(1, encounter.weight)
    }

    @Test
    fun `weight is set correctly when weight() called`() {
        val encounter = buildEncounter {
            slot("goblin")
            weight(35)
        }
        assertEquals(35, encounter.weight)
    }

    // =========================================================================
    // SimpleBattleBuilder — encounters block wiring
    // =========================================================================

    @Test
    fun `SimpleBattleBuilder builds CombatEngineSystem with encounter data`() {
        val goblin =
            io.github.gbkt.rpg.domain.MonsterDef(
                id = "goblin",
                name = "Goblin",
                tier = MonsterTier.COMMON,
                stats = io.github.gbkt.rpg.domain.CombatStats(hp = 30, atk = 8, def = 5),
            )

        val system =
            SimpleBattleBuilder("combat")
                .apply {
                    encounter {
                        slot(goblin, level = 5, tier = MonsterTier.COMMON)
                        slot(goblin, level = 6, tier = MonsterTier.COMMON)
                        weight(30)
                    }
                }
                .buildCombatEngineSystem()

        val config =
            requireNotNull(system.encounterConfig) {
                "CombatEngineSystem encounterConfig should not be null"
            }
        val encounterData = config["encounterData"] as? List<*>
        assertTrue(
            encounterData != null && encounterData.isNotEmpty(),
            "CombatEngineSystem encounterConfig should contain encounterData",
        )
        val firstEnc = requireNotNull(encounterData).firstOrNull() as? EncounterDef
        assertEquals(2, firstEnc?.slots?.size)
        assertEquals(5, firstEnc?.slots?.get(0)?.level)
        assertEquals(MonsterTier.COMMON, firstEnc?.slots?.get(0)?.tier)
    }
}
