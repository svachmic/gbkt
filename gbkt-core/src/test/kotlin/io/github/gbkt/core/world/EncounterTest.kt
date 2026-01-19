/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.world

import io.github.gbkt.core.rpg.Monster
import io.github.gbkt.core.rpg.MonsterBaseStats
import io.github.gbkt.core.rpg.MonsterSize
import io.github.gbkt.core.rpg.MonsterTier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncounterEntryTest {
    private fun createTestMonster(name: String) =
        Monster(
            id = name,
            displayName = name.replaceFirstChar { it.uppercaseChar() },
            size = MonsterSize.SMALL,
            tier = MonsterTier.C,
            baseStats = MonsterBaseStats(hp = 20, atk = 5, def = 3, agl = 8),
            aspectProfile = null,
            statusImmunities = emptySet(),
            aiStatements = emptyList(),
            onDeathStatements = emptyList(),
            onHitStatements = emptyList(),
            expReward = 10,
            lootDrops = emptyList(),
            sprite = null,
        )

    @Test
    fun `encounter entry can be created`() {
        val monsters = listOf(createTestMonster("kobold"), createTestMonster("goblin"))
        val encounterMonsters = monsters.map { it.toEncounterMonster() }
        val entry = EncounterEntry(weight = 30, encounterMonsters = encounterMonsters)

        assertEquals(30, entry.weight)
        assertEquals(2, entry.monsters.size)
        assertEquals("kobold", entry.monsters[0].id)
        assertEquals("goblin", entry.monsters[1].id)
    }
}

class EncounterTableTest {
    private fun createTestMonster(name: String) =
        Monster(
            id = name,
            displayName = name.replaceFirstChar { it.uppercaseChar() },
            size = MonsterSize.SMALL,
            tier = MonsterTier.C,
            baseStats = MonsterBaseStats(hp = 20, atk = 5, def = 3, agl = 8),
            aspectProfile = null,
            statusImmunities = emptySet(),
            aiStatements = emptyList(),
            onDeathStatements = emptyList(),
            onHitStatements = emptyList(),
            expReward = 10,
            lootDrops = emptyList(),
            sprite = null,
        )

    @Test
    fun `encounter table can be created`() {
        val table =
            EncounterTable(
                id = "floor1",
                safeSteps = 10,
                initialChance = 5,
                incrementPerStep = 3,
                maxChance = 128,
                entries =
                    listOf(
                        EncounterEntry(
                            30,
                            listOf(createTestMonster("kobold").toEncounterMonster()),
                        ),
                        EncounterEntry(20, listOf(createTestMonster("goblin").toEncounterMonster())),
                    ),
            )

        assertEquals("floor1", table.id)
        assertEquals(10, table.safeSteps)
        assertEquals(5, table.initialChance)
        assertEquals(3, table.incrementPerStep)
        assertEquals(128, table.maxChance)
        assertEquals(2, table.entries.size)
        assertEquals(50, table.totalWeight)
    }

    @Test
    fun `roll encounter returns entry based on weight`() {
        val kobold = createTestMonster("kobold")
        val goblin = createTestMonster("goblin")

        val table =
            EncounterTable(
                id = "test",
                safeSteps = 0,
                initialChance = 100,
                incrementPerStep = 0,
                maxChance = 100,
                entries =
                    listOf(
                        EncounterEntry(70, listOf(kobold.toEncounterMonster())), // 0-69
                        EncounterEntry(30, listOf(goblin.toEncounterMonster())), // 70-99
                    ),
            )

        // Roll 0-69 should get kobold
        val result1 = table.rollEncounter(10)
        assertNotNull(result1)
        assertEquals("kobold", result1.monsters[0].id)

        // Roll 70-99 should get goblin
        val result2 = table.rollEncounter(75)
        assertNotNull(result2)
        assertEquals("goblin", result2.monsters[0].id)
    }

    @Test
    fun `roll encounter returns null for empty table`() {
        val table =
            EncounterTable(
                id = "empty",
                safeSteps = 0,
                initialChance = 100,
                incrementPerStep = 0,
                maxChance = 100,
                entries = emptyList(),
            )

        assertNull(table.rollEncounter(50))
    }
}

class EncounterTableBuilderTest {
    @Test
    fun `builder creates encounter table with defaults`() {
        val builder = EncounterTableBuilder("test_table")
        val table = builder.build()

        assertEquals("test_table", table.id)
        assertEquals(10, table.safeSteps) // default
        assertEquals(5, table.initialChance) // default
        assertEquals(3, table.incrementPerStep) // default
        assertEquals(128, table.maxChance) // default
        assertTrue(table.entries.isEmpty())
    }

    @Test
    fun `builder can set all properties`() {
        val builder = EncounterTableBuilder("custom_table")
        builder.safeSteps(20)
        builder.initialChance(10)
        builder.incrementPerStep(5)
        builder.maxChance(200)

        val table = builder.build()

        assertEquals(20, table.safeSteps)
        assertEquals(10, table.initialChance)
        assertEquals(5, table.incrementPerStep)
        assertEquals(200, table.maxChance)
    }
}

class EncounterStateTest {
    @Test
    fun `encounter state has defaults`() {
        val state = EncounterState()

        assertEquals(0, state.stepCount)
        assertEquals(0, state.currentChance)
        assertFalse(state.disabled)
    }

    @Test
    fun `encounter state can be modified`() {
        val state = EncounterState()
        state.stepCount = 15
        state.currentChance = 50
        state.disabled = true

        assertEquals(15, state.stepCount)
        assertEquals(50, state.currentChance)
        assertTrue(state.disabled)
    }
}

class EncounterSystemTest {
    private fun createTestMonster(name: String) =
        Monster(
            id = name,
            displayName = name.replaceFirstChar { it.uppercaseChar() },
            size = MonsterSize.SMALL,
            tier = MonsterTier.C,
            baseStats = MonsterBaseStats(hp = 20, atk = 5, def = 3, agl = 8),
            aspectProfile = null,
            statusImmunities = emptySet(),
            aiStatements = emptyList(),
            onDeathStatements = emptyList(),
            onHitStatements = emptyList(),
            expReward = 10,
            lootDrops = emptyList(),
            sprite = null,
        )

    @Test
    fun `reset initializes state correctly`() {
        val table =
            EncounterTable(
                id = "test",
                safeSteps = 10,
                initialChance = 25,
                incrementPerStep = 5,
                maxChance = 128,
                entries =
                    listOf(
                        EncounterEntry(
                            100,
                            listOf(createTestMonster("kobold").toEncounterMonster()),
                        )
                    ),
            )
        val state = EncounterState(stepCount = 50, currentChance = 100, disabled = true)
        val system = EncounterSystem(table, state)

        system.reset()

        assertEquals(0, state.stepCount)
        assertEquals(25, state.currentChance)
        assertFalse(state.disabled)
    }

    @Test
    fun `onStep returns null during safe steps`() {
        val table =
            EncounterTable(
                id = "test",
                safeSteps = 10,
                initialChance = 255, // Very high chance
                incrementPerStep = 0,
                maxChance = 255,
                entries =
                    listOf(
                        EncounterEntry(
                            100,
                            listOf(createTestMonster("kobold").toEncounterMonster()),
                        )
                    ),
            )
        val state = EncounterState()
        val system = EncounterSystem(table, state)

        // Steps 1-10 should be safe
        repeat(10) { assertNull(system.onStep()) }
    }

    @Test
    fun `onStep returns null when disabled`() {
        val table =
            EncounterTable(
                id = "test",
                safeSteps = 0,
                initialChance = 255, // Very high chance
                incrementPerStep = 0,
                maxChance = 255,
                entries =
                    listOf(
                        EncounterEntry(
                            100,
                            listOf(createTestMonster("kobold").toEncounterMonster()),
                        )
                    ),
            )
        val state = EncounterState(stepCount = 100, disabled = true)
        val system = EncounterSystem(table, state)

        assertNull(system.onStep())
    }

    @Test
    fun `disable and enable work correctly`() {
        val table =
            EncounterTable(
                id = "test",
                safeSteps = 0,
                initialChance = 100,
                incrementPerStep = 0,
                maxChance = 100,
                entries =
                    listOf(
                        EncounterEntry(
                            100,
                            listOf(createTestMonster("kobold").toEncounterMonster()),
                        )
                    ),
            )
        val state = EncounterState()
        val system = EncounterSystem(table, state)

        assertFalse(state.disabled)

        system.disable()
        assertTrue(state.disabled)

        system.enable()
        assertFalse(state.disabled)
    }
}

class EncounterTableDslTest {
    @Test
    fun `encounterTable DSL creates table`() {
        val table =
            encounterTable("floor1") {
                safeSteps(15)
                initialChance(10)
                incrementPerStep(4)
                maxChance(150)
            }

        assertEquals("floor1", table.id)
        assertEquals(15, table.safeSteps)
        assertEquals(10, table.initialChance)
        assertEquals(4, table.incrementPerStep)
        assertEquals(150, table.maxChance)
    }
}
