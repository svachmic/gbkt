/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// =============================================================================
// GAMEIR SERIALIZER ROUND-TRIP TEST (Phase 21 Plan 03, Task 2)
//
// Verifies that all 10 previously-stubbed collections survive serialize→deserialize
// with matching IDs.
// =============================================================================

class GameIRSerializerRoundTripTest {

    // =========================================================================
    // Test 1: Maximal fixture — all 10 domain collections survive round-trip
    // =========================================================================

    @Test
    fun `game with all 10 domain collections round-trips with non-empty IDs`() {
        val game =
            GameIR(
                name = "RoundTripAll10",
                startScene = "gameplay",
                flags = listOf(GlobalFlagsIR(id = "hasKey")),
                itemCategories = listOf(ItemCategoryDef(id = "weapon")),
                items = listOf(ItemDef(id = "sword", name = "Sword", categoryId = "weapon")),
                containers = listOf(ContainerIR(id = "chest", slots = 4)),
                dropTables = listOf(DropTableIR(id = "enemyDrops")),
                puzzleObjects = listOf(SwitchObjectIR(id = "lever", x = 5, y = 3)),
                collisionGroups = listOf(CollisionGroupIR(id = "enemies")),
                collisionRules = listOf(CollisionRuleIR(groupA = "enemies", groupB = "player")),
                zones = listOf(ZoneIR(id = "area1", name = "Area 1", spawnX = 40u, spawnY = 120u)),
                systems =
                    listOf(
                        GenericSystem(
                            id = "tilemapCollision",
                            config = mapOf("type" to "tilemap_collision"),
                        )
                    ),
            )

        val json = GameIRSerializer.toJson(game)
        val back = GameIRSerializer.fromJson(json)

        // flags
        assertTrue(back.flags.isNotEmpty(), "flags must survive round-trip")
        assertEquals("hasKey", back.flags[0].id)

        // itemCategories
        assertTrue(back.itemCategories.isNotEmpty(), "itemCategories must survive round-trip")
        assertEquals("weapon", back.itemCategories[0].id)

        // items
        assertTrue(back.items.isNotEmpty(), "items must survive round-trip")
        assertEquals("sword", back.items[0].id)

        // containers
        assertTrue(back.containers.isNotEmpty(), "containers must survive round-trip")
        assertEquals("chest", back.containers[0].id)

        // dropTables
        assertTrue(back.dropTables.isNotEmpty(), "dropTables must survive round-trip")
        assertEquals("enemyDrops", back.dropTables[0].id)

        // puzzleObjects
        assertTrue(back.puzzleObjects.isNotEmpty(), "puzzleObjects must survive round-trip")
        assertEquals("lever", back.puzzleObjects[0].id)

        // collisionGroups
        assertTrue(back.collisionGroups.isNotEmpty(), "collisionGroups must survive round-trip")
        assertEquals("enemies", back.collisionGroups[0].id)

        // collisionRules
        assertTrue(back.collisionRules.isNotEmpty(), "collisionRules must survive round-trip")
        assertEquals("enemies", back.collisionRules[0].groupA)
        assertEquals("player", back.collisionRules[0].groupB)

        // zones — id + spawnX/spawnY survive
        assertTrue(back.zones.isNotEmpty(), "zones must survive round-trip")
        assertEquals("area1", back.zones[0].id)
        assertEquals(40.toUByte(), back.zones[0].spawnX)
        assertEquals(120.toUByte(), back.zones[0].spawnY)

        // systems — GenericSystem subset: id + type config key survive
        assertTrue(back.systems.isNotEmpty(), "systems must survive round-trip")
        val backSystem = back.systems[0]
        assertTrue(backSystem is GenericSystem, "system must deserialize as GenericSystem")
        assertEquals("tilemapCollision", backSystem.id)
        assertEquals("tilemap_collision", backSystem.config["type"])
    }

    // =========================================================================
    // Test 2: Minimal fixture — empty collections round-trip as empty
    // =========================================================================

    @Test
    fun `minimal game with empty collections round-trips without spurious elements`() {
        val game = GameIR(name = "MinimalRoundTrip", startScene = "main")

        val json = GameIRSerializer.toJson(game)
        val back = GameIRSerializer.fromJson(json)

        assertTrue(back.flags.isEmpty(), "empty flags must remain empty after round-trip")
        assertTrue(back.itemCategories.isEmpty(), "empty itemCategories must remain empty")
        assertTrue(back.items.isEmpty(), "empty items must remain empty")
        assertTrue(back.containers.isEmpty(), "empty containers must remain empty")
        assertTrue(back.dropTables.isEmpty(), "empty dropTables must remain empty")
        assertTrue(back.puzzleObjects.isEmpty(), "empty puzzleObjects must remain empty")
        assertTrue(back.collisionGroups.isEmpty(), "empty collisionGroups must remain empty")
        assertTrue(back.collisionRules.isEmpty(), "empty collisionRules must remain empty")
        assertTrue(back.zones.isEmpty(), "empty zones must remain empty")
        assertTrue(back.systems.isEmpty(), "empty systems must remain empty")
    }
}
