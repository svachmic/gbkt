/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.ExplorationSystem
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.ZoneIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// =============================================================================
// PHASE 12.1 PLAN 01 TASK 1 — `bank` field in zoneTilesets metadata
//
// Locks the wiring from `GBDKPipeline.allocateZoneBanks` output through
// `buildMetadataFile` into the `zoneTilesets` JSON array. Per D-01 (option b
// in 12.1-CONTEXT.md), `allocateZoneBanks(gameIR)` is invoked inside
// `buildMetadataFile` and its per-zone bank assignment is emitted as a `bank`
// integer field on each entry. Consumed downstream by ConvertZoneTilesetsTask
// (Task 2) to write `#pragma bank N` on the synthesized
// `_zone_<id>_tilemap.c` file — fixing Defect 2 from Phase 12 first-buildrom.
//
// Schema addition (additive to ZoneTilesetsMetadataTest schema lock):
//   "zoneTilesets": [
//     { "id": "...", "path": "...", "sanitizedSymbol": "...",
//       "mapWidth": N, "mapHeight": M,
//       "bank": >=2 }  // NEW — sourced from allocateZoneBanks(gameIR)
//   ]
//
// All bank assignments MUST be ≥ 2 because banks 0 (HOME) and 1 (scenes) are
// reserved by `allocateZoneBanks` (`tilemapBankStart = 2`).
// =============================================================================

class ZoneTilesetsBankFieldTest {

    private val pipeline = GBDKPipeline()

    /**
     * Test 1: For a GameIR with one new-path zone (`zone.tilesetPath != null`),
     * the generated `game_metadata.json` zoneTilesets[0] object contains a
     * `bank` integer field >= 2.
     */
    @Test
    fun `single new-path zone receives a bank field at or above 2`() {
        val game =
            GameIR(
                name = "SinglePlayZoneFixture",
                config = CartridgeConfig(cartridge = Cartridge.MBC5, romBanks = 8),
                scenes =
                    listOf(
                        SceneIR(id = "title"),
                        SceneIR(id = "play", zoneRefs = listOf("play_zone")),
                    ),
                systems = listOf(ExplorationSystem(id = "test")),
                zones =
                    listOf(
                        ZoneIR(
                            id = "play_zone",
                            name = "Play Zone",
                            tilesetPath = "tiles/checker.png",
                            mapWidth = 20,
                            mapHeight = 18,
                            tileData = listOf(0, 1),
                        )
                    ),
                startScene = "title",
            )

        val json = pipeline.buildMetadataFile(game)
        val parsed = org.json.JSONObject(json)
        val zoneTilesets = parsed.getJSONArray("zoneTilesets")
        assertEquals(1, zoneTilesets.length(), "Expected exactly 1 zoneTilesets entry")

        val entry = zoneTilesets.getJSONObject(0)
        assertTrue(
            entry.has("bank"),
            "Plan 12.1-01 Task 1: zoneTilesets entry MUST carry `bank` field " +
                "(sourced from allocateZoneBanks). Got: $entry",
        )
        val bank = entry.getInt("bank")
        assertTrue(
            bank >= 2,
            "Plan 12.1-01 Task 1: bank assignment MUST be >= 2 (banks 0 HOME " +
                "and 1 scenes are reserved). Got bank=$bank for zone play_zone.",
        )
    }

    /**
     * Test 2: For a GameIR with multiple new-path zones, every zoneTilesets
     * entry has a `bank` field present.
     */
    @Test
    fun `every new-path zone in a multi-zone fixture has a bank field`() {
        val game =
            GameIR(
                name = "MultiZoneFixture",
                config = CartridgeConfig(cartridge = Cartridge.MBC5, romBanks = 16),
                scenes =
                    listOf(
                        SceneIR(id = "title"),
                        SceneIR(
                            id = "play",
                            zoneRefs = listOf("zoneA", "zoneB", "zoneC"),
                        ),
                    ),
                systems = listOf(ExplorationSystem(id = "test")),
                zones =
                    listOf(
                        ZoneIR(
                            id = "zoneA",
                            name = "Zone A",
                            tilesetPath = "tiles/a.png",
                            mapWidth = 20,
                            mapHeight = 18,
                            tileData = List(128) { it and 0xFF },
                        ),
                        ZoneIR(
                            id = "zoneB",
                            name = "Zone B",
                            tilesetPath = "tiles/b.png",
                            mapWidth = 20,
                            mapHeight = 18,
                            tileData = List(128) { it and 0xFF },
                        ),
                        ZoneIR(
                            id = "zoneC",
                            name = "Zone C",
                            tilesetPath = "tiles/c.png",
                            mapWidth = 20,
                            mapHeight = 18,
                            tileData = List(128) { it and 0xFF },
                        ),
                    ),
                startScene = "title",
            )

        val json = pipeline.buildMetadataFile(game)
        val parsed = org.json.JSONObject(json)
        val zoneTilesets = parsed.getJSONArray("zoneTilesets")
        assertEquals(3, zoneTilesets.length(), "Expected 3 zoneTilesets entries")

        for (i in 0 until zoneTilesets.length()) {
            val entry = zoneTilesets.getJSONObject(i)
            assertTrue(
                entry.has("bank"),
                "Plan 12.1-01 Task 1: entry $i (id=${entry.getString("id")}) MUST " +
                    "carry `bank` field. Got: $entry",
            )
            val bank = entry.getInt("bank")
            assertTrue(
                bank >= 2,
                "Plan 12.1-01 Task 1: entry $i (id=${entry.getString("id")}) bank " +
                    "MUST be >= 2. Got bank=$bank.",
            )
        }
    }

    /**
     * Test 3: Existing zoneTilesets fields (id, path, sanitizedSymbol,
     * mapWidth, mapHeight) remain unchanged when `bank` is added — strictly
     * additive.
     */
    @Test
    fun `existing zoneTilesets fields are preserved when bank is added`() {
        val game =
            GameIR(
                name = "FieldPreservationFixture",
                config = CartridgeConfig(cartridge = Cartridge.MBC5, romBanks = 4),
                scenes =
                    listOf(
                        SceneIR(id = "title"),
                        SceneIR(id = "play", zoneRefs = listOf("play_zone")),
                    ),
                systems = listOf(ExplorationSystem(id = "test")),
                zones =
                    listOf(
                        ZoneIR(
                            id = "play_zone",
                            name = "Play Zone",
                            tilesetPath = "tiles/checker.png",
                            mapWidth = 20,
                            mapHeight = 18,
                            tileData = listOf(0, 1),
                        )
                    ),
                startScene = "title",
            )

        val json = pipeline.buildMetadataFile(game)
        val parsed = org.json.JSONObject(json)
        val zoneTilesets = parsed.getJSONArray("zoneTilesets")
        val entry = zoneTilesets.getJSONObject(0)
        assertEquals("play_zone", entry.getString("id"))
        assertEquals("tiles/checker.png", entry.getString("path"))
        assertEquals("play_zone", entry.getString("sanitizedSymbol"))
        assertEquals(20, entry.getInt("mapWidth"))
        assertEquals(18, entry.getInt("mapHeight"))
        assertTrue(entry.has("bank"), "Field 'bank' added by Plan 12.1-01 Task 1")
    }

    /**
     * Test 4: For a GameIR with NO new-path zones, the zoneTilesets array is
     * empty (unchanged behavior — no zoneTilesets entries, so no `bank`
     * field-presence assertion is needed; allocateZoneBanks emits nothing
     * for this case).
     */
    @Test
    fun `game with no new-path zones still emits empty zoneTilesets array`() {
        val game =
            GameIR(
                name = "PongLikeFixture",
                config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY),
                scenes =
                    listOf(SceneIR(id = "gameplay", enterOps = emptyList(), frameOps = emptyList())),
                startScene = "gameplay",
            )

        val json = pipeline.buildMetadataFile(game)
        val parsed = org.json.JSONObject(json)
        val zoneTilesets = parsed.getJSONArray("zoneTilesets")
        assertEquals(
            0,
            zoneTilesets.length(),
            "Games without new-path zones must produce empty zoneTilesets " +
                "(unchanged Pong/Breakout/simple-physics behavior).",
        )
    }
}
