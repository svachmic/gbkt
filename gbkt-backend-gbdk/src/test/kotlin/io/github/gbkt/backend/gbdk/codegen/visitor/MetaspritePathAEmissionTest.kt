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
// Phase 13.3 Plan 02 Task 2: MetaspritePathAEmissionTest — D-01 Path A codegen shape
//
// Encodes the D-01 contract: an asset-driven metasprite (spritePath != null,
// frames empty) must emit `<id>_metasprites[]` references in main.c/game.h and
// must NOT emit `sprite_<id>_frame_N[]` or `sprite_<id>_frames[]` arrays.
//
// D-01 specification:
//   - Path A (spritePath != null, frames empty):
//       - main.c must contain `elephant_metasprites[` (frame-switch reference)
//       - game.h must contain `extern const metasprite_t* const elephant_metasprites[]`
//       - main.c must NOT contain `sprite_elephant_frame_0` (no gbkt-owned frame arrays)
//       - main.c must NOT contain `sprite_elephant_frames[` (no gbkt-owned pointer table)
//   - Escape-hatch D-04 (spritePath == null, frames present):
//       - main.c still emits `sprite_<id>_frames[]` (legacy path unchanged)
//       - main.c does NOT emit `<id>_metasprites[`
//
// RED reason: MetaspriteVisitor.generateMetaspriteDescriptor() is called
// unconditionally (no spritePath branch) — it always emits sprite_<id>_frame_N[]
// and sprite_<id>_frames[]. The Path A assertions (presence of `_metasprites[` and
// absence of `sprite_elephant_frame_0`) therefore FAIL. Plan 13.3-05 adds the
// branch and makes these tests GREEN.
//
// Construction: GameIR is built directly (not via DSL) to bypass the D-08 exactly-one
// guard that MetaspriteBuilder.build() will enforce after Plan 13.3-06. The
// asset-driven test case requires frames=emptyList(), spritePath != null — a state
// the builder currently rejects (no frames). Direct IR construction is appropriate
// here because this test validates codegen shape, not DSL ergonomics.
// =============================================================================

class MetaspritePathAEmissionTest {

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Asset-driven MetaspriteIR (Path A): spritePath set, frames empty. */
    private fun assetDrivenMetasprite(id: String, spritePath: String): MetaspriteIR =
        MetaspriteIR(
            id = id,
            frames = emptyList(),
            spritePath = spritePath,
        )

    /** Procedural MetaspriteIR (escape-hatch D-04): spritePath null, frames present. */
    private fun proceduralMetasprite(id: String): MetaspriteIR =
        MetaspriteIR(
            id = id,
            frames = listOf(
                MetaspriteFrame(
                    tiles = listOf(
                        MetaspriteTile(relX = 0, relY = 0, tileId = 0),
                        MetaspriteTile(relX = 8, relY = 0, tileId = 1),
                    )
                )
            ),
            spritePath = null,
        )

    private fun buildGame(metasprites: List<MetaspriteIR>): GameIR =
        GameIR(
            name = "PathAEmissionGame",
            scenes = listOf(SceneIR(id = "play")),
            metasprites = metasprites,
            startScene = "play",
        )

    // -------------------------------------------------------------------------
    // Test 1 (Path A): asset-driven metasprite emits `<id>_metasprites[` in main.c
    //
    // D-01: the frame-switch block must reference png2asset's native pointer array
    // `elephant_metasprites[idxVar]` via move_metasprite() — NOT the gbkt-owned
    // `sprite_elephant_frames[idxVar]` table.
    //
    // RED now: MetaspriteVisitor.generateMetaspriteFrameSwitch() hardcodes
    // `val frames = "sprite_${metasprite.id}_frames"` (line ~287) regardless of
    // spritePath. Plan 13.3-05 adds the branch:
    //   val frames = if (ms.spritePath != null) "${ms.id}_metasprites"
    //                else "sprite_${ms.id}_frames"
    // -------------------------------------------------------------------------
    @Test
    fun `path A asset-driven metasprite emits id_metasprites reference in main_c`() {
        val game = buildGame(listOf(assetDrivenMetasprite("elephant", "sprites/elephant.png")))

        val output = pipeline.generate(game)
        val allC = (output.files["main.c"] ?: "") + (output.files["bank1.c"] ?: "")

        assertTrue(
            allC.contains("elephant_metasprites["),
            "D-01 Path A: asset-driven metasprite must reference elephant_metasprites[...] " +
                "in the frame-switch (move_metasprite call). " +
                "Current emission uses sprite_elephant_frames — Path A branch not yet implemented. " +
                "Relevant main.c/bank1.c lines:\n" +
                allC.lines().filter { it.contains("elephant") }.take(10).joinToString("\n"),
        )
    }

    // -------------------------------------------------------------------------
    // Test 2 (Path A): asset-driven metasprite does NOT emit sprite_<id>_frame_N[] arrays
    //
    // D-01: for asset-driven metasprites, the gbkt-owned per-frame OAM descriptor
    // arrays (sprite_elephant_frame_0[], sprite_elephant_frame_1[], ...) must NOT
    // appear in main.c. Only the png2asset-native arrays (elephant_metasprite0[], etc.)
    // exist — in the png2asset-generated .c sidecar, not in main.c.
    //
    // RED now: generateMetaspriteDescriptor() is called unconditionally → emits
    // sprite_elephant_frame_0[], sprite_elephant_frames[], etc. in main.c.
    // Plan 13.3-05 gates the call on `ms.spritePath == null`.
    // -------------------------------------------------------------------------
    @Test
    fun `path A asset-driven metasprite does NOT emit sprite_id_frame_N arrays in main_c`() {
        val game = buildGame(listOf(assetDrivenMetasprite("elephant", "sprites/elephant.png")))

        val output = pipeline.generate(game)
        val mainC = output.files["main.c"] ?: ""

        assertFalse(
            Regex("""sprite_elephant_frame_\d""").containsMatchIn(mainC),
            "D-01 Path A: asset-driven metasprite must NOT emit sprite_elephant_frame_N[] arrays " +
                "in main.c — these gbkt-owned descriptors are replaced by png2asset-native arrays. " +
                "Current emission still includes them (Path A branch not yet implemented). " +
                "Matching lines:\n" +
                mainC.lines().filter { Regex("sprite_elephant_frame_\\d").containsMatchIn(it) }
                    .take(5).joinToString("\n"),
        )
    }

    // -------------------------------------------------------------------------
    // Test 3 (Path A): game.h emits extern for <id>_metasprites[] (not sprite_<id>_frames[])
    //
    // D-01: the game.h forward declaration for cross-bank callers must reference
    // `elephant_metasprites[]` (the png2asset-native array) instead of
    // `sprite_elephant_frames[]` (the gbkt-generated array that no longer exists
    // for asset-driven metasprites after Path A lands).
    //
    // RED now: GBDKPipeline emits `extern const metasprite_t* const sprite_${ms.id}_frames[];`
    // unconditionally (~line 3023). Plan 13.3-05 branches on ms.spritePath to emit
    // `extern const metasprite_t* const ${ms.id}_metasprites[];` for asset-driven case.
    // -------------------------------------------------------------------------
    @Test
    fun `path A asset-driven metasprite has elephant_metasprites extern in game_h`() {
        val game = buildGame(listOf(assetDrivenMetasprite("elephant", "sprites/elephant.png")))

        val output = pipeline.generate(game)
        val gameH = output.files["game.h"] ?: ""

        assertTrue(
            gameH.contains("elephant_metasprites[]"),
            "D-01 Path A: game.h must contain 'extern const metasprite_t* const elephant_metasprites[]' " +
                "for cross-bank callers. Currently emits sprite_elephant_frames[] instead. " +
                "Relevant game.h metasprite extern lines:\n" +
                gameH.lines().filter { it.contains("metasprite_t") }.take(5).joinToString("\n"),
        )
    }

    // -------------------------------------------------------------------------
    // Test 4 (Escape-hatch D-04 regression guard): procedural metasprite still emits
    // sprite_<id>_frames[] when spritePath is null (frame-only case unchanged)
    //
    // The escape-hatch path (D-04) must continue to work after Plan 13.3-05 adds
    // the Path A branch. This test is GREEN now and acts as a regression guard.
    // -------------------------------------------------------------------------
    @Test
    fun `escape-hatch D-04 procedural metasprite still emits sprite_id_frames in main_c`() {
        val game = buildGame(listOf(proceduralMetasprite("bat")))

        val output = pipeline.generate(game)
        val mainC = output.files["main.c"] ?: ""

        assertTrue(
            mainC.contains("sprite_bat_frames"),
            "D-04 escape-hatch: frame-only metasprite must still emit sprite_bat_frames[] " +
                "(legacy path unchanged after Path A branch). " +
                "Relevant main.c lines:\n" +
                mainC.lines().filter { it.contains("bat") }.take(5).joinToString("\n"),
        )
    }

    // -------------------------------------------------------------------------
    // Test 5 (Escape-hatch D-04 regression guard): procedural metasprite does NOT emit
    // <id>_metasprites reference (no false Path A activation)
    //
    // When spritePath is null (escape-hatch), the frame-switch must reference
    // sprite_bat_frames, NOT bat_metasprites. This is a correctness regression guard.
    // -------------------------------------------------------------------------
    @Test
    fun `escape-hatch D-04 procedural metasprite does NOT emit id_metasprites reference`() {
        val game = buildGame(listOf(proceduralMetasprite("bat")))

        val output = pipeline.generate(game)
        val allC = (output.files["main.c"] ?: "") + (output.files["bank1.c"] ?: "")

        assertFalse(
            allC.contains("bat_metasprites["),
            "D-04 escape-hatch: frame-only metasprite must NOT emit bat_metasprites[...] reference " +
                "(Path A must not activate for procedural metasprites). " +
                "Matching lines:\n" +
                allC.lines().filter { it.contains("bat_metasprites") }.take(5).joinToString("\n"),
        )
    }
}
