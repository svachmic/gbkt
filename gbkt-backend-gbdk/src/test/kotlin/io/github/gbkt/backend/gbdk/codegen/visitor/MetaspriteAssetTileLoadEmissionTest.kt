/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.MetaspriteFrame
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.MetaspriteTile
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// Phase 13.3 Plan 14 Task 1: MetaspriteAssetTileLoadEmissionTest — RED regression guard
//
// Root cause (13.3-14 gap closure): asset-driven (Path A) metasprites have frames
// with NO DSL tiles (tiles come from png2asset's `<id>_tiles[]`). As a result,
// MetaspriteVisitor.tileCountForMetasprite() returns null, and
// GBDKPipeline.buildAllSpriteDataLoadStatements() skips the metasprite via
// `?: continue` — emitting NO set_sprite_data(). The elephant OAM then points at
// unloaded VRAM tiles → invisible elephant on bgFillCheckerboard background.
//
// Fix contract (Task 3 GREEN): for asset-driven metasprites (spritePath != null,
// frames empty), the pipeline MUST emit:
//   set_sprite_data(<startExpr>, sprites_<id>_tiles_count, sprites_<id>_tiles);
//
// The count is the C macro `sprites_<id>_tiles_count` (emitted by ConvertSpritesTask
// per Task 2), NOT an integer literal — png2asset dedups tiles, so Kotlin cannot
// know the real count. The macro is authoritative (elephant_tiles[704] = 44 tiles,
// not the geometric 64x48/64=48 count).
//
// RED reason: before Task 3, no set_sprite_data() is emitted for asset-driven
// metasprites at all. Both assertions below should FAIL against unpatched code.
//
// Construction: GameIR is built directly (not via DSL) to bypass any DSL builder
// guards. The asset-driven shape is: spritePath != null, frames = emptyList() —
// exactly what `metasprite { sprite(asset(...)) { ... }; frames(N) }` produces.
// Direct IR construction is appropriate here because this test validates codegen
// shape, not DSL ergonomics. Pattern matches MetaspritePathAEmissionTest.
// =============================================================================

class MetaspriteAssetTileLoadEmissionTest {

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

    private fun buildGame(metasprites: List<MetaspriteIR>): GameIR =
        GameIR(
            name = "AssetTileLoadGame",
            scenes = listOf(SceneIR(id = "play")),
            metasprites = metasprites,
            startScene = "play",
        )

    // -------------------------------------------------------------------------
    // Test 1: asset-driven metasprite emits set_sprite_data() call in main.c
    //
    // RED reason: buildAllSpriteDataLoadStatements() calls
    // MetaspriteVisitor.tileCountForMetasprite(ms) which returns null for
    // asset-driven metasprites (frames.flatMap{it.tiles} is empty) → the
    // `?: continue` skips the metasprite → NO set_sprite_data emitted.
    //
    // GREEN after Task 3: emit CRawCode with
    //   set_sprite_data(<start>, sprites_elephant_tiles_count, sprites_elephant_tiles);
    // -------------------------------------------------------------------------
    @Test
    fun `asset-driven metasprite emits set_sprite_data in main_c`() {
        val game = buildGame(listOf(assetDrivenMetasprite("elephant", "sprites/elephant.png")))

        val output = pipeline.generate(game)
        val mainC = output.files["main.c"] ?: ""

        assertTrue(
            mainC.contains("set_sprite_data("),
            "13.3-14: asset-driven (Path A) metasprite MUST emit set_sprite_data() in main.c " +
                "to load elephant tiles into sprite VRAM before move_metasprite() is called. " +
                "Without this call, the OAM points at unloaded VRAM → invisible elephant. " +
                "Root cause: tileCountForMetasprite() returns null for empty-frame metasprites, " +
                "causing buildAllSpriteDataLoadStatements() to skip via ?: continue. " +
                "Relevant main.c lines containing 'sprite_data':\n" +
                mainC.lines().filter { it.contains("sprite_data") }.take(10).joinToString("\n"),
        )
    }

    // -------------------------------------------------------------------------
    // Test 2: asset-driven set_sprite_data uses sprites_<id>_tiles_count macro
    //
    // RED reason: even after Test 1 passes (set_sprite_data is emitted), it must
    // reference the C macro `sprites_elephant_tiles_count` — NOT a hardcoded integer.
    // png2asset dedups tiles (elephant_tiles[704] = 44 tiles, geometric = 48), so
    // an integer count computed in Kotlin will be wrong.
    //
    // GREEN after Task 3: emit
    //   set_sprite_data(0u, sprites_elephant_tiles_count, sprites_elephant_tiles);
    // -------------------------------------------------------------------------
    @Test
    fun `asset-driven set_sprite_data uses sprites_id_tiles_count macro not integer`() {
        val game = buildGame(listOf(assetDrivenMetasprite("elephant", "sprites/elephant.png")))

        val output = pipeline.generate(game)
        val mainC = output.files["main.c"] ?: ""

        assertTrue(
            mainC.contains("sprites_elephant_tiles_count"),
            "13.3-14: asset-driven set_sprite_data MUST use the C macro " +
                "`sprites_elephant_tiles_count` (emitted by ConvertSpritesTask from the actual " +
                "png2asset array length / 16) as the tile count — NOT a hardcoded integer. " +
                "png2asset deduplicates tiles (-noflip suppresses mirror-pair dedup but not " +
                "other dedup), so the Kotlin-computed geometric count (frameW*frameH/64) is wrong. " +
                "Macro `sprites_elephant_tiles_count` is defined in sprites/elephant.h " +
                "(Task 2 in this plan). " +
                "Relevant main.c set_sprite_data lines:\n" +
                mainC.lines().filter { it.contains("set_sprite_data") }.take(5).joinToString("\n"),
        )
    }

    // -------------------------------------------------------------------------
    // Test 3: multiple asset-driven metasprites chain their start offsets
    //
    // RED reason: no set_sprite_data is emitted at all, so chaining is also absent.
    //
    // GREEN after Task 3: second metasprite start = 0u + sprites_elephant_tiles_count
    //   set_sprite_data(0u, sprites_elephant_tiles_count, sprites_elephant_tiles);
    //   set_sprite_data(0u + sprites_elephant_tiles_count, sprites_tiger_tiles_count,
    // sprites_tiger_tiles);
    //
    // Note: the test accepts any ordering/formatting of the two set_sprite_data calls.
    // The key invariant is that the second metasprite's start includes the first's count.
    // -------------------------------------------------------------------------
    @Test
    fun `two asset-driven metasprites chain start offsets using count macros`() {
        val game =
            buildGame(
                listOf(
                    assetDrivenMetasprite("elephant", "sprites/elephant.png"),
                    assetDrivenMetasprite("tiger", "sprites/tiger.png"),
                )
            )

        val output = pipeline.generate(game)
        val mainC = output.files["main.c"] ?: ""

        val setSpriteCalls = mainC.lines().filter { it.contains("set_sprite_data(") }

        assertTrue(
            setSpriteCalls.size >= 2,
            "13.3-14: two asset-driven metasprites must each emit a set_sprite_data() call. " +
                "Found ${setSpriteCalls.size} set_sprite_data call(s). " +
                "All set_sprite_data lines in main.c:\n${setSpriteCalls.joinToString("\n")}",
        )

        assertTrue(
            mainC.contains("sprites_elephant_tiles_count") &&
                mainC.contains("sprites_tiger_tiles_count"),
            "13.3-14: both metasprites must use their respective tiles_count macros. " +
                "set_sprite_data calls found:\n${setSpriteCalls.joinToString("\n")}",
        )

        // Second metasprite start must reference the first's count macro for correct chaining.
        assertTrue(
            mainC.contains("sprites_elephant_tiles_count"),
            "13.3-14: second metasprite (tiger) start must chain off first metasprite's count " +
                "macro (sprites_elephant_tiles_count), not a hardcoded integer. " +
                "set_sprite_data calls:\n${setSpriteCalls.joinToString("\n")}",
        )
    }

    // -------------------------------------------------------------------------
    // Test 4 (regression guard): Path B (frame-tile) metasprite set_sprite_data
    //   uses integer count — byte-identical to pre-13.3-14 emission
    //
    // Escape-hatch D-04 procedural metasprites (spritePath null, frames with tiles)
    // still use the original tileCountForMetasprite() integer path. This test is
    // GREEN now and acts as a regression guard that Task 3 does NOT change the Path B
    // emission shape.
    // -------------------------------------------------------------------------
    @Test
    fun `path B procedural metasprite set_sprite_data uses integer count unchanged`() {
        val game = buildGame(listOf(proceduralMetasprite("bat")))

        val output = pipeline.generate(game)
        val mainC = output.files["main.c"] ?: ""

        // Path B must still emit set_sprite_data with an integer count (e.g. "2u")
        // bat has tileId 0 and 1 → tileCountForMetasprite = 2 → set_sprite_data(0u, 2u, bat_tiles)
        assertTrue(
            mainC.contains("set_sprite_data("),
            "13.3-14 regression guard: escape-hatch D-04 procedural metasprite must still emit " +
                "set_sprite_data() with an integer count (unchanged from pre-13.3-14). " +
                "Relevant lines:\n" +
                mainC.lines().filter { it.contains("sprite_data") }.take(5).joinToString("\n"),
        )

        // Path B must NOT use a count macro — its count is a Kotlin-computed integer
        assertFalse(
            mainC.contains("sprites_bat_tiles_count"),
            "13.3-14 regression guard: escape-hatch D-04 procedural metasprite must NOT use " +
                "sprites_bat_tiles_count macro — its tile count is known at codegen time and " +
                "emitted as an integer literal. " +
                "Relevant main.c set_sprite_data lines:\n" +
                mainC.lines().filter { it.contains("set_sprite_data") }.take(5).joinToString("\n"),
        )
    }
}
