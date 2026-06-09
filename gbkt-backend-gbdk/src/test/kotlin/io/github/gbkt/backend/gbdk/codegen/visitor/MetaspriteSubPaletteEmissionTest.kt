/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GBCColor
import io.github.gbkt.core.ir.GBCPalette
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GbcTarget
import io.github.gbkt.core.ir.MetaspriteFrame
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.MetaspriteTile
import io.github.gbkt.core.ir.PaletteType
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// Phase 13.3 Plan 17 Task 1: MetaspriteSubPaletteEmissionTest — RED guard for PINK fix
//
// Root cause (evidence/13.3-DIAGNOSTIC.md, Direction B RECOMMENDED):
//   GBDKPipeline.buildMetaspriteSpritePaletteStatements() emits
//   `set_sprite_palette(0u, 1u, elephant_palettes)` for ALL metasprites regardless
//   of whether they are asset-driven (spritePath != null). This upload writes only
//   elephant sub-palette 0 to OBJ slot 0, then play_enter() overwrites slot 0 with
//   gray_pal. Result: OBJ slot 1 = pink_pal (from play_enter's scene uploads), while
//   the descriptor's S_PAL(1) entries point at it → PINK elephant outline.
//
// Direction B fix (recommended by 13.3-DIAGNOSTIC.md):
//   For asset-driven metasprites (spritePath != null), SUPPRESS the
//   `set_sprite_palette(…, elephant_palettes)` upload. The scene's explicit
//   spritePalette {} uploads (Sites A and C in the diagnostic) are the sole OBJ
//   palette authority. This restores the pre-migration behavior where the scene's
//   gray_pal at slot 0 covered the elephant's S_PAL(0) tiles.
//
// The escape-hatch (spritePath == null) path keeps `set_sprite_palette` emission
// unchanged — this mirrors the Phase 12.9 D2b fix that added the upload for
// procedural metasprites in the first place.
//
// Construction: GameIR is built directly (not via DSL) — mirrors MetaspritePathAEmissionTest
// and MetaspriteAssetTileLoadEmissionTest. Asset-driven shape: spritePath != null, frames empty.
// Procedural shape: spritePath == null, frames with tiles.
// GBC target is required (set_sprite_palette is GBC-gated; the metasprites example uses
// GBC_COMPATIBLE).
// =============================================================================

class MetaspriteSubPaletteEmissionTest {

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Asset-driven MetaspriteIR (Path A): spritePath set, frames empty. */
    private fun assetDrivenMetasprite(id: String, spritePath: String): MetaspriteIR =
        MetaspriteIR(id = id, frames = emptyList(), spritePath = spritePath)

    /** Procedural MetaspriteIR (escape-hatch D-04): spritePath null, frames with tiles. */
    private fun proceduralMetasprite(id: String): MetaspriteIR =
        MetaspriteIR(
            id = id,
            frames =
                listOf(
                    MetaspriteFrame(
                        tiles =
                            listOf(
                                MetaspriteTile(relX = 0, relY = 0, tileId = 0),
                                MetaspriteTile(relX = 8, relY = 0, tileId = 1),
                            )
                    )
                ),
            spritePath = null,
        )

    /**
     * A minimal GBCPalette(type=SPRITE) — simulates a spritePalette{} DSL declaration. Required to
     * preserve 13.3-17 Direction B: when a spritePalette{} is present, asset-driven metasprites
     * must NOT get an auto set_sprite_palette (Phase 13.7-03 fix only adds the auto upload when NO
     * GBCPalette(type=SPRITE) exists in the game).
     */
    private fun graySpritePalette(): GBCPalette =
        GBCPalette(
            name = "gray",
            colors =
                listOf(
                    GBCColor.fromRGB888(0, 0, 0),
                    GBCColor.fromRGB888(82, 82, 82),
                    GBCColor.fromRGB888(165, 165, 165),
                    GBCColor.fromRGB888(248, 248, 248),
                ),
            type = PaletteType.SPRITE,
        )

    /**
     * Build a GBC game WITH a spritePalette{} (GBCPalette type=SPRITE). This models the
     * metasprites-example shape: the game declares explicit sprite palettes, so Direction B
     * suppression applies — asset-driven metasprites must NOT get auto upload. After Phase 13.7-03,
     * hasSpritePalette=true suppresses the new asset-driven fallback arm.
     */
    private fun buildGame(metasprites: List<MetaspriteIR>): GameIR =
        GameIR(
            name = "SubPaletteEmissionGame",
            config =
                CartridgeConfig(
                    cartridge = Cartridge.ROM_ONLY,
                    romBanks = 2,
                    gbcTarget = GbcTarget.GBC_COMPATIBLE,
                ),
            scenes = listOf(SceneIR(id = "play")),
            metasprites = metasprites,
            palettes = listOf(graySpritePalette()),
            startScene = "play",
        )

    // =========================================================================
    // Test 1 (Direction B): asset-driven metasprite does NOT emit set_sprite_palette
    //   for its png2asset-generated palette array
    //
    // Direction B (evidence/13.3-DIAGNOSTIC.md, RECOMMENDED): for asset-driven
    // metasprites (spritePath != null), buildMetaspriteSpritePaletteStatements()
    // must NOT emit `set_sprite_palette(…, elephant_palettes)`. The scene's
    // spritePalette{} uploads are the sole OBJ palette authority.
    //
    // RED before Task 2: the current code emits
    //   `set_sprite_palette(0u, 1u, elephant_palettes)` unconditionally for ALL
    //   metasprites — no spritePath guard exists. This test fails RED.
    //
    // GREEN after Task 2: buildMetaspriteSpritePaletteStatements() skips asset-driven
    //   metasprites (spritePath != null), so no elephant_palettes upload appears.
    // =========================================================================
    @Test
    fun `asset-driven metasprite does NOT emit set_sprite_palette in main_c (Direction B)`() {
        val game = buildGame(listOf(assetDrivenMetasprite("elephant", "sprites/elephant.png")))

        val output = pipeline.generate(game)
        val mainC =
            output.files["main.c"] ?: error("main.c not generated. Files: ${output.files.keys}")

        assertFalse(
            mainC.contains("elephant_palettes"),
            "13.3-17 Direction B: asset-driven metasprite (spritePath != null) must NOT emit " +
                "`set_sprite_palette(…, elephant_palettes)` in main.c. The scene's spritePalette{} " +
                "uploads (Sites A and C in evidence/13.3-DIAGNOSTIC.md) are the sole OBJ palette " +
                "authority. Current code emits the upload unconditionally (no spritePath guard). " +
                "Fix: gate buildMetaspriteSpritePaletteStatements on ms.spritePath == null. " +
                "Relevant main.c lines containing 'palette':\n" +
                mainC.lines().filter { "palette" in it.lowercase() }.take(15).joinToString("\n"),
        )
    }

    // =========================================================================
    // Test 2 (Direction B): asset-driven metasprite does NOT use the hardcoded 1u count
    //   — the `set_sprite_palette(…, 1u, elephant_palettes)` line must be absent
    //
    // This is the literal `1u` hardcode that the diagnostic identified as the source
    // of the slot-collision: uploading only 1 sub-palette to slot 0, then having the
    // scene re-upload gray to slot 0 and leave pink at slot 1, while the descriptor's
    // S_PAL(1) entries point at slot 1 = pink → PINK.
    //
    // RED before Task 2: the `1u` literal appears in main.c for elephant.
    // GREEN after Task 2: no `elephant_palettes` upload → no `1u` for that symbol.
    // =========================================================================
    @Test
    fun `asset-driven metasprite does NOT emit hardcoded-1u set_sprite_palette no slot-0 collision`() {
        val game = buildGame(listOf(assetDrivenMetasprite("elephant", "sprites/elephant.png")))

        val output = pipeline.generate(game)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // The specific bad line from the diagnostic:
        //   set_sprite_palette(0u, 1u, elephant_palettes);
        // After Direction B fix this line must not appear.
        assertFalse(
            mainC.contains("set_sprite_palette(0u, 1u, elephant_palettes)"),
            "13.3-17 Direction B: the slot-0-collision line " +
                "`set_sprite_palette(0u, 1u, elephant_palettes)` must not appear in main.c. " +
                "This hardcoded `1u` count + slot 0 collision with the scene gray_pal upload " +
                "is the PINK root cause (evidence/13.3-DIAGNOSTIC.md Site B). " +
                "Relevant main.c set_sprite_palette lines:\n" +
                mainC.lines().filter { "set_sprite_palette" in it }.take(10).joinToString("\n"),
        )
    }

    // =========================================================================
    // Test 3 (regression guard): procedural metasprite (spritePath == null) still
    //   emits set_sprite_palette — escape-hatch D-04 path unchanged
    //
    // The Phase 12.9 D2b fix added set_sprite_palette for procedural metasprites.
    // Direction B must NOT remove the upload for the escape-hatch path.
    //
    // This test is currently GREEN (the upload is emitted) and acts as a regression
    // guard: it must remain GREEN after Task 2's fix is applied.
    // =========================================================================
    @Test
    fun `escape-hatch procedural metasprite still emits set_sprite_palette (D2b path unchanged)`() {
        val game = buildGame(listOf(proceduralMetasprite("player")))

        val output = pipeline.generate(game)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("player_palettes"),
            "13.3-17 regression guard: escape-hatch procedural metasprite (spritePath == null) " +
                "must still emit `set_sprite_palette(…, player_palettes)` in main.c. " +
                "Direction B only suppresses the upload for asset-driven metasprites (spritePath != null). " +
                "Relevant main.c palette lines:\n" +
                mainC.lines().filter { "palette" in it.lowercase() }.take(10).joinToString("\n"),
        )
    }

    // =========================================================================
    // Test 4 (Direction B): mixed game — asset-driven + procedural metasprites
    //   Only the procedural one emits set_sprite_palette; asset-driven is suppressed
    //
    // This tests the exact metasprites-example shape: an asset-driven elephant
    // alongside a scene that provides its own palette. Only procedural metasprites
    // (spritePath == null) should trigger the upload.
    // =========================================================================
    @Test
    fun `mixed game asset-driven upload suppressed procedural upload kept`() {
        val game =
            buildGame(
                listOf(
                    assetDrivenMetasprite("elephant", "sprites/elephant.png"),
                    proceduralMetasprite("player"),
                )
            )

        val output = pipeline.generate(game)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Asset-driven upload must be absent
        assertFalse(
            mainC.contains("elephant_palettes"),
            "13.3-17 Direction B: elephant_palettes must NOT appear in main.c " +
                "(asset-driven metasprite — upload suppressed). " +
                "Relevant palette lines:\n" +
                mainC.lines().filter { "palette" in it.lowercase() }.take(15).joinToString("\n"),
        )

        // Procedural upload must still be present
        assertTrue(
            mainC.contains("player_palettes"),
            "13.3-17 regression guard: player_palettes must appear in main.c " +
                "(procedural metasprite — D2b upload kept). " +
                "Relevant palette lines:\n" +
                mainC.lines().filter { "palette" in it.lowercase() }.take(15).joinToString("\n"),
        )
    }
}
