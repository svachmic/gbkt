/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.EntityCollisionMode
import io.github.gbkt.core.ir.ExplorationSystem
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.PushDirection
import io.github.gbkt.core.ir.TransitionEdge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the world DSL builders: [ZoneBuilder], [EncounterBuilder], [FlagsBuilder],
 * [GaugeBuilder], [KeyBuilder], [EntityCollisionBuilder], and [ExplorationBuilder].
 *
 * Uses the top-level `game {}` builder to exercise the full DSL registration path, then inspects
 * the resulting [io.github.gbkt.core.ir.GameIR] fields (zones, flags, systems, actors) for
 * correctness.
 */
class WorldBuilderTest {

    // =========================================================================
    // ZoneBuilder tests (5)
    // =========================================================================

    @Test
    fun `zone creates ZoneIR with correct id and dimensions`() {
        val ir =
            game("test") {
                    val floor1 by zone {
                        name("Dungeon Level 1")
                        size(16, 16)
                    }
                    @Suppress("UNUSED_VARIABLE") val _unused = floor1
                    val main = scene("main") { enter {} }
                    start = main
                }
                .build()

        assertEquals(1, ir.zones.size)
        val zone = ir.zones[0]
        assertEquals("floor1", zone.id)
        assertEquals("Dungeon Level 1", zone.name)
        assertEquals(16, zone.mapWidth)
        assertEquals(16, zone.mapHeight)
    }

    @Test
    fun `zone with encounters has correct encounter table`() {
        val ir =
            game("test") {
                    val dungeon by zone {
                        encounters {
                            safeSteps(10)
                            entry("goblin", weight = 30)
                        }
                    }
                    @Suppress("UNUSED_VARIABLE") val _unused = dungeon
                    val main = scene("main") { enter {} }
                    start = main
                }
                .build()

        val zone = ir.zones[0]
        assertNotNull(zone.encounterTable)
        assertEquals(10, zone.encounterTable!!.safeSteps)
        assertEquals(1, zone.encounterTable!!.entries.size)
        assertEquals("goblin", zone.encounterTable!!.entries[0].id)
        assertEquals(30, zone.encounterTable!!.entries[0].weight)
    }

    @Test
    fun `zone with transition has correct transition target and edge`() {
        val ir =
            game("test") {
                    val floor1 by zone {
                        transition {
                            to("floor2")
                            edge(TransitionEdge.EAST)
                            entryX(0)
                            entryY(5)
                        }
                    }
                    @Suppress("UNUSED_VARIABLE") val _unused = floor1
                    val main = scene("main") { enter {} }
                    start = main
                }
                .build()

        val zone = ir.zones[0]
        assertEquals(1, zone.transitions.size)
        val t = zone.transitions[0]
        assertEquals("floor2", t.targetZoneId)
        assertEquals(TransitionEdge.EAST, t.edge)
        assertEquals(0, t.entryX)
        assertEquals(5, t.entryY)
    }

    @Test
    fun `zone with safeZone flag sets isSafeZone true`() {
        val ir =
            game("test") {
                    val town by zone { safeZone() }
                    @Suppress("UNUSED_VARIABLE") val _unused = town
                    val main = scene("main") { enter {} }
                    start = main
                }
                .build()

        val zone = ir.zones[0]
        assertTrue(zone.isSafeZone)
    }

    @Test
    fun `zone with lifecycle callbacks has non-empty onEnter and onExit`() {
        val ir =
            game("test") {
                    val floor1 by zone {
                        onEnter { navigate(SceneRef("cutscene")) }
                        onExit { navigate(SceneRef("village")) }
                    }
                    @Suppress("UNUSED_VARIABLE") val _unused = floor1
                    scene("cutscene") { enter {} }
                    scene("village") { enter {} }
                    val main = scene("main") { enter {} }
                    start = main
                }
                .build()

        val zone = ir.zones[0]
        assertTrue(zone.onEnter.isNotEmpty())
        assertTrue(zone.onExit.isNotEmpty())
        assertIs<NavigateTo>(zone.onEnter[0])
        assertIs<NavigateTo>(zone.onExit[0])
    }

    // =========================================================================
    // FlagsBuilder tests (1)
    // =========================================================================

    @Test
    fun `flags creates GlobalFlagsIR with pages and flags`() {
        val ir =
            game("test") {
                    flags {
                        page("story") {
                            flag("metElder")
                            flag("hasKey")
                        }
                    }
                    val main = scene("main") { enter {} }
                    start = main
                }
                .build()

        assertEquals(1, ir.flags.size)
        val flagsIR = ir.flags[0]
        assertEquals(1, flagsIR.pages.size)
        val page = flagsIR.pages[0]
        assertEquals("story", page.name)
        assertEquals(2, page.flags.size)
        assertTrue(page.flags.contains("metElder"))
        assertTrue(page.flags.contains("hasKey"))
    }

    // =========================================================================
    // ExplorationBuilder and GaugeBuilder tests (3)
    // =========================================================================

    @Test
    fun `exploration with gauge has correct gauge config`() {
        val ir =
            game("test") {
                    exploration {
                        gauge("torch") {
                            max(255)
                            initial(255)
                            decrementPerStep(1)
                            onLow(50) { navigate(SceneRef("low_torch")) }
                        }
                    }
                    scene("low_torch") { enter {} }
                    val main = scene("main") { enter {} }
                    start = main
                }
                .build()

        val exploration = ir.systems.filterIsInstance<ExplorationSystem>().first()
        assertEquals(1, exploration.gauges.size)
        val gauge = exploration.gauges[0]
        assertEquals("torch", gauge.id)
        assertEquals(255, gauge.max)
        assertEquals(255, gauge.initial)
        assertEquals(1, gauge.decrementPerStep)
        assertEquals(50, gauge.onLowThreshold)
        assertTrue(gauge.onLowStatements.isNotEmpty())
    }

    @Test
    fun `exploration with keys has correct key config`() {
        val ir =
            game("test") {
                    exploration { keys("magic_key") { max(99) } }
                    val main = scene("main") { enter {} }
                    start = main
                }
                .build()

        val exploration = ir.systems.filterIsInstance<ExplorationSystem>().first()
        assertEquals(1, exploration.keys.size)
        val key = exploration.keys[0]
        assertEquals("magic_key", key.id)
        assertEquals(99, key.max)
    }

    @Test
    fun `exploration passes tileSize movementStyle and movementSpeed fields`() {
        val ir =
            game("test") {
                    exploration {
                        tileSize = 16
                        movementStyle = "SMOOTH"
                        movementSpeed = 4
                    }
                    val main = scene("main") { enter {} }
                    start = main
                }
                .build()

        val exploration = ir.systems.filterIsInstance<ExplorationSystem>().first()
        assertEquals(16, exploration.tileSize)
        assertEquals("SMOOTH", exploration.movementStyle)
        assertEquals(4, exploration.movementSpeed)
    }

    // =========================================================================
    // EntityCollisionBuilder tests (3)
    // =========================================================================

    @Test
    fun `entity collision on actor produces correct mode and callback`() {
        val ir =
            game("test") {
                    actor("npc") {
                        entityCollision {
                            mode(EntityCollisionMode.BLOCK_AND_TRIGGER)
                            onBlocked { navigate(SceneRef("dialog")) }
                        }
                    }
                    scene("dialog") { enter {} }
                    val main = scene("main") { enter {} }
                    start = main
                }
                .build()

        val actor = ir.actors.first { it.id == "npc" }
        assertNotNull(actor.entityCollision)
        assertEquals(EntityCollisionMode.BLOCK_AND_TRIGGER, actor.entityCollision!!.mode)
        assertTrue(actor.entityCollision!!.onBlockedStatements.isNotEmpty())
    }

    @Test
    fun `multi-tile entity has correct tilesWide and tilesHigh`() {
        val ir =
            game("test") {
                    actor("boulder") { entityCollision { tiles(2, 2) } }
                    val main = scene("main") { enter {} }
                    start = main
                }
                .build()

        val actor = ir.actors.first { it.id == "boulder" }
        assertNotNull(actor.entityCollision)
        assertEquals(2, actor.entityCollision!!.tilesWide)
        assertEquals(2, actor.entityCollision!!.tilesHigh)
    }

    @Test
    fun `push entity has correct mode and pushDirection`() {
        val ir =
            game("test") {
                    actor("crate") {
                        entityCollision {
                            mode(EntityCollisionMode.PUSH)
                            pushDirection(PushDirection.HORIZONTAL_ONLY)
                        }
                    }
                    val main = scene("main") { enter {} }
                    start = main
                }
                .build()

        val actor = ir.actors.first { it.id == "crate" }
        assertNotNull(actor.entityCollision)
        assertEquals(EntityCollisionMode.PUSH, actor.entityCollision!!.mode)
        assertEquals(PushDirection.HORIZONTAL_ONLY, actor.entityCollision!!.pushDirection)
    }

    // =========================================================================
    // ExplorationPreset tests — Gap 9 (3)
    // =========================================================================

    @Test
    fun `exploration preset DUNGEON_CRAWLER sets tileSize 8 grid and torch gauge`() {
        val ir =
            game("test") {
                    exploration { preset(ExplorationPreset.DUNGEON_CRAWLER) }
                    val main = scene("main") { enter {} }
                    start = main
                }
                .build()

        val exploration = ir.systems.filterIsInstance<ExplorationSystem>().first()
        assertEquals(8, exploration.tileSize)
        assertEquals("GRID", exploration.movementStyle)
        assertEquals(8, exploration.movementSpeed)
        assertEquals(1, exploration.gauges.size)
        val torch = exploration.gauges[0]
        assertEquals("torch", torch.id)
        assertEquals(255, torch.max)
        assertEquals(1, torch.decrementPerStep)
    }

    @Test
    fun `exploration preset with overridden tileSize uses override but keeps preset defaults`() {
        val ir =
            game("test") {
                    exploration {
                        preset(ExplorationPreset.DUNGEON_CRAWLER)
                        tileSize = 16
                    }
                    val main = scene("main") { enter {} }
                    start = main
                }
                .build()

        val exploration = ir.systems.filterIsInstance<ExplorationSystem>().first()
        assertEquals(16, exploration.tileSize)
        assertEquals("GRID", exploration.movementStyle)
        assertTrue(exploration.gauges.any { it.id == "torch" })
    }

    @Test
    fun `exploration without preset does not add default torch gauge`() {
        val ir =
            game("test") {
                    exploration { gauge("stamina") { max(100) } }
                    val main = scene("main") { enter {} }
                    start = main
                }
                .build()

        val exploration = ir.systems.filterIsInstance<ExplorationSystem>().first()
        assertEquals(1, exploration.gauges.size)
        assertEquals("stamina", exploration.gauges[0].id)
        assertTrue(exploration.gauges.none { it.id == "torch" })
    }

    // =========================================================================
    // Additional edge-case tests
    // =========================================================================

    @Test
    fun `multiple zones produce multiple ZoneIR in GameIR`() {
        val ir =
            game("test") {
                    val floor1 by zone { name("Level 1") }
                    val floor2 by zone { name("Level 2") }
                    @Suppress("UNUSED_VARIABLE") val _unused1 = floor1
                    @Suppress("UNUSED_VARIABLE") val _unused2 = floor2
                    val main = scene("main") { enter {} }
                    start = main
                }
                .build()

        assertEquals(2, ir.zones.size)
        assertTrue(ir.zones.any { it.id == "floor1" })
        assertTrue(ir.zones.any { it.id == "floor2" })
    }

    @Test
    fun `flags with multiple pages produces correct page structure`() {
        val ir =
            game("test") {
                    flags("story_flags") {
                        page("story") {
                            flag("metElder")
                            flag("hasKey")
                            flag("defeatedBoss")
                        }
                        page("exploration") {
                            flag("visitedFloor1")
                            flag("visitedFloor2")
                        }
                    }
                    val main = scene("main") { enter {} }
                    start = main
                }
                .build()

        assertEquals(1, ir.flags.size)
        val flagsIR = ir.flags[0]
        assertEquals("story_flags", flagsIR.id)
        assertEquals(2, flagsIR.pages.size)
        assertEquals(3, flagsIR.pages[0].flags.size)
        assertEquals(2, flagsIR.pages[1].flags.size)
    }

    @Test
    fun `zone without encounters has null encounterTable`() {
        val ir =
            game("test") {
                    val town by zone { safeZone() }
                    @Suppress("UNUSED_VARIABLE") val _unused = town
                    val main = scene("main") { enter {} }
                    start = main
                }
                .build()

        val zone = ir.zones[0]
        assertNull(zone.encounterTable)
    }

    @Test
    fun `actor without entityCollision has null entityCollision field`() {
        val ir =
            game("test") {
                    actor("player") {}
                    val main = scene("main") { enter {} }
                    start = main
                }
                .build()

        val actor = ir.actors.first { it.id == "player" }
        assertNull(actor.entityCollision)
    }
}
