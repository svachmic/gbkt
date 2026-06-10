/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.ZoneIR
import kotlin.test.Test
import kotlin.test.assertEquals

// =============================================================================
// PHASE 11.2 PLAN 01 — zoneTilesets manifest emission
//
// Locks the shape of the new game_metadata.json `zoneTilesets` array
// emitted by GBDKPipeline.buildMetadataFile().
//
// Schema (single source of truth — D-A4):
//   "zoneTilesets": [
//     { "id": "<raw zone.id>",
//       "path": "<raw tilesetPath>",
//       "sanitizedSymbol": "<id with '-' and ' ' replaced by '_'>" }
//   ]
//
// Filter: zones whose `tilesetPath` is null are omitted — procedurally
// authored sport-racing zones stay on the LEGACY path.
// =============================================================================

class ZoneTilesetsMetadataTest {

    private val pipeline = GBDKPipeline()

    /**
     * Builds a minimal GameIR mirroring the banks-fixture shape: a single zone named `play_zone`
     * pointing to `tiles/checker.png`. The metadata blob is the canonical Gradle ↔ Kotlin bridge
     * consumed by ConvertZoneTilesetsTask.
     */
    @Test
    fun `zoneTilesets manifest contains banks play_zone entry`() {
        val game =
            GameIR(
                name = "BanksZoneTilesetsFixture",
                config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY),
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
                scenes =
                    listOf(
                        SceneIR(id = "gameplay", enterOps = emptyList(), frameOps = emptyList())
                    ),
                startScene = "gameplay",
            )

        val json = pipeline.buildMetadataFile(game)
        val parsed = org.json.JSONObject(json)
        val zoneTilesets = parsed.getJSONArray("zoneTilesets")
        assertEquals(1, zoneTilesets.length(), "Expected exactly 1 zoneTilesets entry")

        val entry = zoneTilesets.getJSONObject(0)
        assertEquals("play_zone", entry.getString("id"), "id should be raw zone.id")
        assertEquals("tiles/checker.png", entry.getString("path"), "path should be raw tilesetPath")
        assertEquals(
            "play_zone",
            entry.getString("sanitizedSymbol"),
            "sanitizedSymbol should be id with '-' and ' ' replaced by '_'",
        )
    }

    /**
     * Procedurally authored zones (sport-racing) carry no `tilesetPath`. The manifest filter
     * discards them so ConvertZoneTilesetsTask only sees NEW-path consumers.
     */
    @Test
    fun `zoneTilesets omits zones without tilesetPath (procedural zones)`() {
        val game =
            GameIR(
                name = "ProceduralOnlyFixture",
                config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY),
                zones =
                    listOf(
                        ZoneIR(
                            id = "racing_track",
                            name = "Racing Track",
                            // tilesetPath = null — procedural zone
                            mapWidth = 32,
                            mapHeight = 32,
                        )
                    ),
                scenes =
                    listOf(SceneIR(id = "race", enterOps = emptyList(), frameOps = emptyList())),
                startScene = "race",
            )

        val json = pipeline.buildMetadataFile(game)
        val parsed = org.json.JSONObject(json)
        val zoneTilesets = parsed.getJSONArray("zoneTilesets")
        assertEquals(
            0,
            zoneTilesets.length(),
            "Procedural zones (tilesetPath=null) must be filtered out",
        )
    }
}
