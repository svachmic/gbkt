/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Tests for the RPG domain data classes.
 *
 * Domain objects are plain Kotlin data classes — NOT IR types. They carry game data (stats, names,
 * encounter definitions) that is then used by DSL builders to produce core IR types
 * (SystemIR.GenericSystem, ScriptOp).
 */
class DomainModelTest {

    // -------------------------------------------------------------------------
    // CombatStats
    // -------------------------------------------------------------------------

    @Test
    fun `CombatStats construction with valid values`() {
        val stats = CombatStats(hp = 100, atk = 15, def = 10)
        assertEquals(100, stats.hp)
        assertEquals(15, stats.atk)
        assertEquals(10, stats.def)
    }

    @Test
    fun `CombatStats with zero atk and def is valid`() {
        val stats = CombatStats(hp = 1, atk = 0, def = 0)
        assertEquals(1, stats.hp)
        assertEquals(0, stats.atk)
        assertEquals(0, stats.def)
    }

    @Test
    fun `CombatStats with zero hp throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { CombatStats(hp = 0, atk = 5, def = 3) }
    }

    @Test
    fun `CombatStats with negative hp throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { CombatStats(hp = -1, atk = 5, def = 3) }
    }

    @Test
    fun `CombatStats with negative atk throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { CombatStats(hp = 10, atk = -1, def = 3) }
    }

    @Test
    fun `CombatStats with negative def throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { CombatStats(hp = 10, atk = 5, def = -1) }
    }

    @Test
    fun `CombatStats supports copy()`() {
        val original = CombatStats(hp = 100, atk = 15, def = 10)
        val copy = original.copy(hp = 50)
        assertEquals(50, copy.hp)
        assertEquals(15, copy.atk)
        assertEquals(10, copy.def)
    }

    @Test
    fun `CombatStats equality and hashCode`() {
        val stats1 = CombatStats(hp = 100, atk = 15, def = 10)
        val stats2 = CombatStats(hp = 100, atk = 15, def = 10)
        assertEquals(stats1, stats2)
        assertEquals(stats1.hashCode(), stats2.hashCode())
    }

    // -------------------------------------------------------------------------
    // CharacterDef
    // -------------------------------------------------------------------------

    @Test
    fun `CharacterDef construction`() {
        val stats = CombatStats(hp = 100, atk = 15, def = 10)
        val character = CharacterDef(id = "hero", name = "Hero", stats = stats)
        assertEquals("hero", character.id)
        assertEquals("Hero", character.name)
        assertEquals(stats, character.stats)
    }

    @Test
    fun `CharacterDef supports copy()`() {
        val stats = CombatStats(hp = 100, atk = 15, def = 10)
        val original = CharacterDef(id = "hero", name = "Hero", stats = stats)
        val copy = original.copy(name = "Super Hero")
        assertEquals("hero", copy.id)
        assertEquals("Super Hero", copy.name)
    }

    @Test
    fun `CharacterDef equality`() {
        val stats = CombatStats(hp = 100, atk = 15, def = 10)
        val char1 = CharacterDef(id = "hero", name = "Hero", stats = stats)
        val char2 = CharacterDef(id = "hero", name = "Hero", stats = stats)
        assertEquals(char1, char2)
    }

    // -------------------------------------------------------------------------
    // MonsterDef
    // -------------------------------------------------------------------------

    @Test
    fun `MonsterDef construction with default expReward`() {
        val stats = CombatStats(hp = 30, atk = 8, def = 5)
        val monster = MonsterDef(id = "goblin", name = "Goblin", stats = stats)
        assertEquals("goblin", monster.id)
        assertEquals("Goblin", monster.name)
        assertEquals(stats, monster.stats)
        assertEquals(0, monster.expReward)
    }

    @Test
    fun `MonsterDef construction with explicit expReward`() {
        val stats = CombatStats(hp = 30, atk = 8, def = 5)
        val monster = MonsterDef(id = "goblin", name = "Goblin", stats = stats, expReward = 15)
        assertEquals(15, monster.expReward)
    }

    @Test
    fun `MonsterDef supports copy()`() {
        val stats = CombatStats(hp = 30, atk = 8, def = 5)
        val original = MonsterDef(id = "goblin", name = "Goblin", stats = stats)
        val copy = original.copy(expReward = 20)
        assertEquals(20, copy.expReward)
        assertEquals("goblin", copy.id)
    }

    // -------------------------------------------------------------------------
    // EncounterDef
    // -------------------------------------------------------------------------

    @Test
    fun `EncounterDef construction with default weight`() {
        val encounter = EncounterDef.fromIds(listOf("goblin", "goblin"))
        assertEquals(listOf("goblin", "goblin"), encounter.monsterIds)
        assertEquals(1, encounter.weight)
    }

    @Test
    fun `EncounterDef construction with explicit weight`() {
        val encounter = EncounterDef.fromIds(listOf("goblin"), weight = 3)
        assertEquals(3, encounter.weight)
    }

    @Test
    fun `EncounterDef supports copy() and equality`() {
        val enc1 = EncounterDef.fromIds(listOf("goblin"), weight = 2)
        val enc2 = enc1.copy(weight = 5)
        assertEquals(5, enc2.weight)
        assertEquals(listOf("goblin"), enc2.monsterIds)
    }

    @Test
    fun `EncounterDef slots-based construction`() {
        val slot = EncounterSlotDef(monsterId = "goblin", level = 5, tier = MonsterTier.UNCOMMON)
        val encounter = EncounterDef(slots = listOf(slot))
        assertEquals(1, encounter.slots.size)
        assertEquals("goblin", encounter.slots[0].monsterId)
        assertEquals(5, encounter.slots[0].level)
        assertEquals(MonsterTier.UNCOMMON, encounter.slots[0].tier)
        assertEquals(listOf("goblin"), encounter.monsterIds)
    }

    // -------------------------------------------------------------------------
    // SimpleBattleDef
    // -------------------------------------------------------------------------

    @Test
    fun `SimpleBattleDef construction with required fields`() {
        val encounter = EncounterDef.fromIds(listOf("goblin"))
        val battleDef =
            SimpleBattleDef(
                id = "combat",
                partyIds = listOf("hero"),
                encounters = listOf(encounter),
            )
        assertEquals("combat", battleDef.id)
        assertEquals(listOf("hero"), battleDef.partyIds)
        assertEquals(1, battleDef.encounters.size)
        assertEquals(emptyList<Any>(), battleDef.onVictoryOps)
        assertEquals(emptyList<Any>(), battleDef.onDefeatOps)
    }

    @Test
    fun `SimpleBattleDef supports copy()`() {
        val enc = EncounterDef.fromIds(listOf("goblin"))
        val original =
            SimpleBattleDef(id = "combat", partyIds = listOf("hero"), encounters = listOf(enc))
        val copy = original.copy(partyIds = listOf("hero", "wizard"))
        assertEquals(listOf("hero", "wizard"), copy.partyIds)
        assertEquals("combat", copy.id)
    }
}
