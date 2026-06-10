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
import io.github.gbkt.core.ir.GbcTarget
import io.github.gbkt.core.ir.MetaspriteFrame
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.MetaspriteTile
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SpriteMode
import kotlin.test.Test
import kotlin.test.assertTrue

// =============================================================================
// SPR8x16 TILE COUNT TEST -- Debug session E-04
// (debug/platformer-duck-malformed-blob.md)
//
// Asserts the `set_sprite_data()` tile count contract for SPR8x16 metasprites:
// for tile ID N referenced in 8x16 mode, the hardware loads tiles N AND N+1
// (a pair). The set_sprite_data() count argument must therefore be
// `maxTileId + 2` (load 0..N+1 inclusive), not `maxTileId + 1`.
//
// Pre-fix (E-04 RED): pipeline + MetaspriteVisitor both computed
// `totalTiles = maxTileId + 1`. For the platformer duck (maxTileId = 60), this
// loaded 61 tiles instead of 62 — the last 8x16 tile pair (tiles 60+61) was
// loaded only partially, dropping the rightmost column's bottom half of the
// last frame.
//
// Post-fix (E-04 GREEN): the tile count formula is mode-aware. For SPR8x16
// metasprites: `maxTileId + 2`. For SPR8x8 (and null spriteMode, back-compat):
// `maxTileId + 1` (unchanged — preserves the elephant-example contract).
//
// Companion to:
//   - SpriteMode8x16HardwareModeTest (E-02 hardware mode macro)
//   - PlayerMetaspriteGeometryTest (E-03 frame coord layout)
// =============================================================================

class Spr8x16TileCountTest {

    private val pipeline = GBDKPipeline()

    /**
     * Build a minimal GameIR with one SPR8x16 metasprite where maxTileId = 60. Mirrors the
     * platformer-template player duck shape (6 frames × 6 tiles, tile IDs 0, 2, 4, 6, …, 60). The
     * test verifies that set_sprite_data() loads 62 tiles (0..61), not 61, because in 8x16 mode
     * tile ID 60 references the pair (60, 61).
     */
    private fun buildSpr8x16GameIrWithMaxTileId60(): GameIR =
        GameIR(
            name = "Spr8x16TileCountTest",
            config =
                CartridgeConfig(
                    cartridge = Cartridge.ROM_ONLY,
                    romBanks = 2,
                    gbcTarget = GbcTarget.GBC_COMPATIBLE,
                ),
            scenes = listOf(SceneIR(id = "play")),
            startScene = "play",
            metasprites =
                listOf(
                    MetaspriteIR(
                        id = "duck",
                        frames =
                            // Three frames to introduce a spread of tile IDs.
                            // Tiles 0..60 even — max tile ID is 60.
                            listOf(
                                MetaspriteFrame(
                                    tiles =
                                        listOf(
                                            MetaspriteTile(relX = -12, relY = -6, tileId = 0),
                                            MetaspriteTile(relX = 8, relY = 0, tileId = 2),
                                        )
                                ),
                                MetaspriteFrame(
                                    tiles =
                                        listOf(
                                            MetaspriteTile(relX = -12, relY = -6, tileId = 38),
                                            MetaspriteTile(relX = 8, relY = 0, tileId = 40),
                                        )
                                ),
                                MetaspriteFrame(
                                    tiles =
                                        listOf(
                                            MetaspriteTile(relX = -12, relY = -6, tileId = 50),
                                            MetaspriteTile(relX = 8, relY = 0, tileId = 60),
                                        )
                                ),
                            ),
                        spriteMode = SpriteMode.SPR8x16,
                        pivotX = 12,
                        pivotY = 6,
                        frameWidth = 24,
                        frameHeight = 32,
                    )
                ),
        )

    /** Build the same metasprite but with SPR8x8 mode — counts should NOT change. */
    private fun buildSpr8x8GameIrWithMaxTileId60(): GameIR =
        GameIR(
            name = "Spr8x8TileCountTest",
            config =
                CartridgeConfig(
                    cartridge = Cartridge.ROM_ONLY,
                    romBanks = 2,
                    gbcTarget = GbcTarget.GBC_COMPATIBLE,
                ),
            scenes = listOf(SceneIR(id = "play")),
            startScene = "play",
            metasprites =
                listOf(
                    MetaspriteIR(
                        id = "elephant",
                        frames =
                            listOf(
                                MetaspriteFrame(
                                    tiles = listOf(MetaspriteTile(relX = 0, relY = 0, tileId = 60))
                                )
                            ),
                        spriteMode = SpriteMode.SPR8x8,
                    )
                ),
        )

    @Test
    fun `main_c set_sprite_data count is maxTileId plus 2 for SPR8x16 metasprite`() {
        val gameIR = buildSpr8x16GameIrWithMaxTileId60()
        val mainSource =
            pipeline.generate(gameIR).files["main.c"]
                ?: error("main.c not generated by GBDKPipeline")

        // The set_sprite_data() call for the duck should load 62 tiles starting at 0.
        // Expected line: `set_sprite_data(0u, 62u, duck_tiles)`.
        // Pre-E-04 fix would emit 61u (RED).
        // Post-E-04 fix emits 62u (GREEN).
        assertTrue(
            mainSource.contains("set_sprite_data(0u, 62u, duck_tiles)"),
            buildString {
                appendLine(
                    "Expected `set_sprite_data(0u, 62u, duck_tiles)` in main.c for an SPR8x16 " +
                        "metasprite with maxTileId=60. Pre-E-04-fix the pipeline computed " +
                        "`maxTileId + 1 = 61`, dropping tile 61 (the second half of the 8x16 pair " +
                        "referenced by tile ID 60). Post-fix: 8x16 mode requires `maxTileId + 2 = 62`."
                )
                appendLine("main.c excerpt (set_sprite_data lines):")
                mainSource
                    .lines()
                    .filter { it.contains("set_sprite_data") }
                    .forEach { appendLine("  $it") }
            },
        )
    }

    @Test
    fun `main_c set_sprite_data count is maxTileId plus 1 for SPR8x8 metasprite (back-compat)`() {
        val gameIR = buildSpr8x8GameIrWithMaxTileId60()
        val mainSource =
            pipeline.generate(gameIR).files["main.c"]
                ?: error("main.c not generated by GBDKPipeline")

        // SPR8x8: tile ID N occupies a single 8x8 slot. maxTileId=60 → 61 tiles.
        // Preserves the elephant-example contract; E-04 fix is additive (only changes 8x16 path).
        assertTrue(
            mainSource.contains("set_sprite_data(0u, 61u, elephant_tiles)"),
            buildString {
                appendLine(
                    "Expected `set_sprite_data(0u, 61u, elephant_tiles)` in main.c for an SPR8x8 " +
                        "metasprite with maxTileId=60. The SPR8x8 path must remain " +
                        "`maxTileId + 1` — only the SPR8x16 path is affected by E-04."
                )
                appendLine("main.c excerpt (set_sprite_data lines):")
                mainSource
                    .lines()
                    .filter { it.contains("set_sprite_data") }
                    .forEach { appendLine("  $it") }
            },
        )
    }
}
