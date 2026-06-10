/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.ChestObjectIR
import io.github.gbkt.core.ir.LeverObjectIR
import io.github.gbkt.core.ir.NpcObjectIR
import io.github.gbkt.core.ir.SconceObjectIR
import io.github.gbkt.core.ir.SignObjectIR
import io.github.gbkt.core.ir.TransitionEdge
import io.github.gbkt.core.ir.ZoneIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

// =============================================================================
// WORLD BUILDERS TESTS
// Verifies zone DSL capabilities: encounter level ranges (GAP-04), zone object
// DSL (GAP-06), zone transition condition flags (GAP-07), and FlagRef typed API.
// =============================================================================

class WorldBuildersTest {

    // Minimal DSL helper to build a ZoneIR without full game context
    private fun buildZone(id: String, block: ZoneBuilder.() -> Unit): ZoneIR =
        ZoneBuilder(id).apply(block).build()

    // =========================================================================
    // GAP-04: Level-Progressive Encounter Tables
    // =========================================================================

    @Test
    fun `encounter entry builds EncounterEntryIR with minLevel when set`() {
        val zone =
            buildZone("floor1") { encounters { entry("goblin_lv5", weight = 30) { minLevel(5) } } }

        val table = requireNotNull(zone.encounterTable) { "Encounter table should be present" }
        assertEquals(1, table.entries.size)
        val entry = table.entries[0]
        assertEquals("goblin_lv5", entry.id)
        assertEquals(30, entry.weight)
        assertEquals(5, entry.minLevel)
        assertNull(entry.maxLevel, "maxLevel should be null when not set")
    }

    @Test
    fun `encounter entry builds EncounterEntryIR with maxLevel when set`() {
        val zone =
            buildZone("floor1") { encounters { entry("weak_goblin", weight = 40) { maxLevel(9) } } }

        val entry = zone.encounterTable!!.entries[0]
        assertNull(entry.minLevel, "minLevel should be null when not set")
        assertEquals(9, entry.maxLevel)
    }

    @Test
    fun `encounter entry builds EncounterEntryIR with both minLevel and maxLevel`() {
        val zone =
            buildZone("floor1") {
                encounters {
                    entry("mid_goblin", weight = 30) {
                        minLevel(5)
                        maxLevel(9)
                    }
                }
            }

        val entry = zone.encounterTable!!.entries[0]
        assertEquals(5, entry.minLevel)
        assertEquals(9, entry.maxLevel)
    }

    @Test
    fun `encounter entry without level range has null minLevel and maxLevel`() {
        val zone = buildZone("floor1") { encounters { entry("goblin", weight = 30) } }

        val entry = zone.encounterTable!!.entries[0]
        assertNull(entry.minLevel, "minLevel should be null for unconditional entry")
        assertNull(entry.maxLevel, "maxLevel should be null for unconditional entry")
    }

    @Test
    fun `encounter entry condition(String) and condition(FlagRef) produce identical EncounterEntryIR`() {
        val zoneString =
            buildZone("floor1") {
                encounters { entry("boss_enc", weight = 10) { condition("bossSpawned") } }
            }
        val zoneTyped =
            buildZone("floor1") {
                encounters { entry("boss_enc", weight = 10) { condition(FlagRef("bossSpawned")) } }
            }

        val entryString = zoneString.encounterTable!!.entries[0]
        val entryTyped = zoneTyped.encounterTable!!.entries[0]
        assertEquals(entryString.conditionFlag, entryTyped.conditionFlag)
        assertEquals("bossSpawned", entryString.conditionFlag)
    }

    // =========================================================================
    // GAP-07: Exit Tile Transitions with Condition Gates
    // =========================================================================

    @Test
    fun `transition builds ZoneTransitionIR with conditionFlag when conditionFlag(String) called`() {
        val zone =
            buildZone("floor1") {
                transition {
                    to("floor2")
                    edge(TransitionEdge.NORTH)
                    conditionFlag("bossDefeated")
                }
            }

        assertEquals(1, zone.transitions.size)
        val transition = zone.transitions[0]
        assertEquals("floor2", transition.targetZoneId)
        assertEquals(TransitionEdge.NORTH, transition.edge)
        assertEquals("bossDefeated", transition.conditionFlag)
    }

    @Test
    fun `transition builds ZoneTransitionIR with conditionFlag when conditionFlag(FlagRef) called`() {
        val bossDefeated = FlagRef("bossDefeated")
        val zone =
            buildZone("floor1") {
                transition {
                    to("floor2")
                    edge(TransitionEdge.NORTH)
                    conditionFlag(bossDefeated)
                }
            }

        val transition = zone.transitions[0]
        assertEquals("bossDefeated", transition.conditionFlag)
    }

    @Test
    fun `transition without conditionFlag has null conditionFlag`() {
        val zone =
            buildZone("floor1") {
                transition {
                    to("floor2")
                    edge(TransitionEdge.NORTH)
                }
            }

        val transition = zone.transitions[0]
        assertNull(
            transition.conditionFlag,
            "Unconditional transition should have null conditionFlag",
        )
    }

    @Test
    fun `conditionFlag(String) and conditionFlag(FlagRef) produce identical ZoneTransitionIR`() {
        val zoneString =
            buildZone("floor1") {
                transition {
                    to("floor2")
                    edge(TransitionEdge.NORTH)
                    conditionFlag("hasKey")
                }
            }
        val zoneTyped =
            buildZone("floor1") {
                transition {
                    to("floor2")
                    edge(TransitionEdge.NORTH)
                    conditionFlag(FlagRef("hasKey"))
                }
            }

        assertEquals(
            zoneString.transitions[0].conditionFlag,
            zoneTyped.transitions[0].conditionFlag,
        )
    }

    // =========================================================================
    // GAP-06: Zone Object DSL — Chests, Signs, Sconces, NPCs, Levers
    // =========================================================================

    @Test
    fun `chest object builds ChestObjectIR with correct position and usedFlagId`() {
        val zone =
            buildZone("floor1") {
                objects { chest("chest1", x = 5, y = 3) { usedFlag("chest1_opened") } }
            }

        assertEquals(1, zone.objects.size)
        val chest = assertIs<ChestObjectIR>(zone.objects[0])
        assertEquals("chest1", chest.id)
        assertEquals(5, chest.x)
        assertEquals(3, chest.y)
        assertEquals("chest1_opened", chest.usedFlagId)
    }

    @Test
    fun `sign object builds SignObjectIR at correct position`() {
        val zone = buildZone("floor1") { objects { sign("entrance_sign", x = 2, y = 8) } }

        assertEquals(1, zone.objects.size)
        val sign = assertIs<SignObjectIR>(zone.objects[0])
        assertEquals("entrance_sign", sign.id)
        assertEquals(2, sign.x)
        assertEquals(8, sign.y)
    }

    @Test
    fun `sconce object builds SconceObjectIR with no usedFlag by default`() {
        val zone = buildZone("floor1") { objects { sconce("torch1", x = 4, y = 6) } }

        val sconce = assertIs<SconceObjectIR>(zone.objects[0])
        assertEquals("torch1", sconce.id)
        assertNull(sconce.usedFlagId, "usedFlagId should be null when not set")
    }

    @Test
    fun `npc object builds NpcObjectIR with visibleFlagId when set`() {
        val zone =
            buildZone("floor1") {
                objects { npc("elder", x = 10, y = 5) { visibleFlag("elderSpawned") } }
            }

        val npc = assertIs<NpcObjectIR>(zone.objects[0])
        assertEquals("elder", npc.id)
        assertEquals("elderSpawned", npc.visibleFlagId)
        assertEquals(false, npc.visibleWhenFlagUnset)
    }

    @Test
    fun `npc object with visibleWhenFlagUnset=true sets flag correctly`() {
        val zone =
            buildZone("floor1") {
                objects {
                    npc("ghost", x = 3, y = 3) {
                        visibleFlag("ghostRevealed", visibleWhenUnset = true)
                    }
                }
            }

        val npc = assertIs<NpcObjectIR>(zone.objects[0])
        assertEquals(true, npc.visibleWhenFlagUnset)
    }

    @Test
    fun `lever object builds LeverObjectIR at correct position`() {
        val zone = buildZone("floor1") { objects { lever("gate_lever", x = 3, y = 7) } }

        val lever = assertIs<LeverObjectIR>(zone.objects[0])
        assertEquals("gate_lever", lever.id)
        assertEquals(3, lever.x)
        assertEquals(7, lever.y)
    }

    @Test
    fun `multiple objects are all added to the zone`() {
        val zone =
            buildZone("floor1") {
                objects {
                    chest("chest1", x = 5, y = 3)
                    sign("sign1", x = 2, y = 8)
                    sconce("torch1", x = 4, y = 6)
                    npc("elder", x = 10, y = 5)
                    lever("lever1", x = 3, y = 7)
                }
            }

        assertEquals(5, zone.objects.size)
        assertIs<ChestObjectIR>(zone.objects[0])
        assertIs<SignObjectIR>(zone.objects[1])
        assertIs<SconceObjectIR>(zone.objects[2])
        assertIs<NpcObjectIR>(zone.objects[3])
        assertIs<LeverObjectIR>(zone.objects[4])
    }

    @Test
    fun `zone without objects has empty objects list`() {
        val zone = buildZone("floor1") { name("Floor 1") }
        assertTrue(
            zone.objects.isEmpty(),
            "Zone without objects call should have empty objects list",
        )
    }
}
