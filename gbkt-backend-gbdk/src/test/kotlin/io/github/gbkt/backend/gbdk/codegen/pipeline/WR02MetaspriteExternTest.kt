/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.MetaspriteFrame
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.MetaspriteTile
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// WR-02 — `game.h` lacks per-metasprite `extern const metasprite_t* const
// sprite_<id>_frames[];` forward declarations.
//
// Phase 11 (banks port) prerequisite alongside CR-02 (Plan 06, closed):
// once a banked scene includes `<gbdk/metasprites.h>` AND references
// `sprite_<id>_frames` (via `move_metasprite(sprite_<id>_frames[frame], ...)`),
// SDCC's linker needs the forward declaration in `game.h` to resolve the
// symbol from bank0. Without the extern decl in game.h, bank1.c sees an
// undeclared identifier.
//
// Per D-14 and PATTERNS.md §Pattern Assignments lines 316-336, the fix
// extends the `homeGlobalAutoExterns` loop at GBDKPipeline.kt:2026 with
// a parallel iteration over `gameIR.metasprites`. Each metasprite produces
// one line `"extern const metasprite_t* const sprite_${ms.id}_frames[];"`
// appended into the `rawSections: List<String>` already carrying
// `paletteExternRaw` and `callOpForwardDecls`.
//
// Test shape mirrors `MetadataFileTest` (PATTERNS.md line 33 — text-content
// assertion against an emitted pipeline file): construct a `GameIR` literal,
// run `GBDKPipeline().generate(gameIR)`, fetch `output.files["game.h"]`,
// assert on text content. The naming `sprite_${ms.id}_frames` matches the
// CR-03 namespacing landed in Plan 05.
// =============================================================================

class WR02MetaspriteExternTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Minimal valid metasprite — single 1-tile frame. Plan 06 helper shape. */
    private fun metasprite(id: String): MetaspriteIR =
        MetaspriteIR(
            id = id,
            frames =
                listOf(
                    MetaspriteFrame(tiles = listOf(MetaspriteTile(relX = 0, relY = 0, tileId = 0)))
                ),
        )

    /**
     * Build a GameIR with [metasprites]. A single trivial scene is sufficient — unlike CR-02
     * (Plan 06) which had to escape the bank fast-path, WR-02 concerns the always-emitted `game.h`
     * (bank 0), so single-scene is fine.
     */
    private fun buildGame(metasprites: List<MetaspriteIR>): GameIR =
        GameIR(
            name = "WR02Game",
            scenes = listOf(SceneIR(id = "title")),
            metasprites = metasprites,
            startScene = "title",
        )

    // -------------------------------------------------------------------------
    // Test 1 — positive case: per-metasprite extern decl emitted in game.h
    // -------------------------------------------------------------------------
    @Test
    fun game_h_contains_extern_decl_for_each_metasprite() {
        val game = buildGame(metasprites = listOf(metasprite("elephant"), metasprite("tiger")))

        val output = GBDKPipeline().generate(game)
        val gameH = output.files["game.h"]

        assertNotNull(
            gameH,
            "game.h was not generated. Inspect output.files keys: ${output.files.keys}",
        )

        assertTrue(
            gameH.contains("extern const metasprite_t* const sprite_elephant_frames[];"),
            "game.h MUST contain extern decl for `sprite_elephant_frames` " +
                "(WR-02). Generated extern lines:\n" +
                gameH.lines().filter { it.contains("extern const metasprite_t") }.joinToString("\n"),
        )

        assertTrue(
            gameH.contains("extern const metasprite_t* const sprite_tiger_frames[];"),
            "game.h MUST contain extern decl for `sprite_tiger_frames` " +
                "(WR-02). Generated extern lines:\n" +
                gameH.lines().filter { it.contains("extern const metasprite_t") }.joinToString("\n"),
        )

        // Exactly one extern per metasprite — no duplicates.
        val externCount =
            gameH.lines().count { it.contains("extern const metasprite_t* const sprite_") }
        assertEquals(
            2,
            externCount,
            "Expected exactly 2 metasprite extern lines (one per MetaspriteIR), got " +
                "$externCount. Generated extern lines:\n" +
                gameH.lines().filter { it.contains("extern const metasprite_t") }.joinToString("\n"),
        )
    }

    // -------------------------------------------------------------------------
    // Test 2 — negative case: empty metasprites list → no extern decls
    // (regression guard against unconditional emission)
    // -------------------------------------------------------------------------
    @Test
    fun game_h_does_not_emit_metasprite_extern_when_no_metasprites() {
        val game = buildGame(metasprites = emptyList())

        val output = GBDKPipeline().generate(game)
        val gameH = output.files["game.h"]

        assertNotNull(
            gameH,
            "game.h was not generated. Inspect output.files keys: ${output.files.keys}",
        )

        assertFalse(
            gameH.contains("extern const metasprite_t*"),
            "game.h MUST NOT emit metasprite extern decls when " +
                "gameIR.metasprites is empty (regression guard against unconditional " +
                "emission). Generated extern lines:\n" +
                gameH.lines().filter { it.contains("extern const metasprite_t") }.joinToString("\n"),
        )
    }
}
