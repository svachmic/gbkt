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
import io.github.gbkt.core.ir.MoveMetasprite
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// SEED-011 / WR-05 — Per-call hiwater wrap resets the OAM slot counter when
// a scene calls moveMetasprite() twice in one frame.
//
// Pre-fix root cause (MetaspriteVisitor.kt:215 + line 254):
//   generateMetaspriteFrameSwitch emits, for EVERY moveMetasprite() call:
//     {
//         uint8_t hiwater = 0u;            <-- RESETS the slot counter per call
//         uint8_t subpal = $rotVar >> 2;
//         ...
//         switch ($rotVar & 0x3u) {
//             case N: hiwater += move_metasprite_*(...); break;
//             ...
//         }
//         hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES);   <-- ALSO per call
//     }
//
// Two moveMetasprite() calls in one frame ⇒ two `hiwater = 0u` declarations
// AND two `hide_sprites_range(hiwater, …)` calls. The second call's `hiwater = 0u`
// resets the OAM cursor, so its `hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES)`
// HIDES the first metasprite's OAM slots (slots 0..N from call 1).
//
// Phase 12 (platformer_template) blocker — that game needs 2+ metasprites per
// frame (player + enemy / projectile composites).
//
// Fix (D-11 Route A / PATTERNS.md §Pattern Assignments lines 380-394):
//   HOIST `uint8_t hiwater = 0u;` and `hide_sprites_range(hiwater, …)` out of
//   the per-call switch block into the SCENE FRAME function's prelude/postlude.
//   Each moveMetasprite() then only contributes `hiwater += move_metasprite_*(…)`.
//   The frame function's prelude declares `hiwater` ONCE and the postlude calls
//   `hide_sprites_range` ONCE — regardless of how many moveMetasprite() ops the
//   scene contains.
//
// Test shape (per D-20 + CLAUDE.md §scope-level grep gates): use awk-style
// brace-walk extraction (extractFunctionBody) so cross-scene regressions cannot
// mask per-function invariants. A file-level grep would find 2+ occurrences of
// `hide_sprites_range` (one per metasprite call) and report PASS for the wrong
// reason.
// =============================================================================

// -----------------------------------------------------------------------------
// Brace-walk helper (copied verbatim from MetaspriteEmissionTest.kt:81-101
// per PATTERNS.md line 552-569 — scope-level grep gate)
// -----------------------------------------------------------------------------

private fun extractFunctionBody(cSource: String, functionName: String): String {
    val lines = cSource.lines()
    val startIdx = lines.indexOfFirst { it.contains("void $functionName(") }
    if (startIdx == -1) return ""
    val body = StringBuilder()
    var depth = 0
    var started = false
    for (i in startIdx until lines.size) {
        val line = lines[i]
        body.appendLine(line)
        for (ch in line) {
            if (ch == '{') {
                depth++
                started = true
            }
            if (ch == '}') depth--
        }
        if (started && depth == 0) break
    }
    return body.toString()
}

// -----------------------------------------------------------------------------
// Minimal IR builder: 2 scenes (title + play) — title has frame ops but NO
// MoveMetasprite (regression guard); play has TWO MoveMetasprite ops (the
// WR-05 collision pattern).
//
// 2 scenes ⇒ multi-scene path: the play scene's `_frame` function lands in
// bank1.c (escapes BankingAnalysisPass's single-scene HOME fast-path — same
// rationale as Seed008VramCollisionTest § Note on bank1.c vs main.c).
// -----------------------------------------------------------------------------

private fun buildTwoMetaspriteFrameGame(): GameIR {
    // Both metasprites have a single trivial 1-tile frame — the test asserts
    // emission-shape invariants (hiwater + hide_sprites_range count) inside
    // play_frame, NOT the descriptor / tile-data emission. Minimal frames keep
    // the fixture compact.
    val elephant =
        MetaspriteIR(
            id = "elephant",
            frames =
                listOf(
                    MetaspriteFrame(tiles = listOf(MetaspriteTile(relX = 0, relY = 0, tileId = 0)))
                ),
        )
    val tiger =
        MetaspriteIR(
            id = "tiger",
            frames =
                listOf(
                    MetaspriteFrame(tiles = listOf(MetaspriteTile(relX = 0, relY = 0, tileId = 1)))
                ),
        )

    // Title scene: non-empty frame ops, but ZERO MoveMetasprite ops.
    // NavigateTo("play") is a trivial single-op frame body — proves title_frame
    // gets generated AND that the wrap helper early-returns (no hiwater = 0 in
    // its body).
    val titleScene = SceneIR(id = "title", frameOps = listOf(NavigateTo(sceneId = "play")))

    // Play scene: TWO MoveMetasprite ops per frame — the WR-05 collision pattern.
    val playScene =
        SceneIR(
            id = "play",
            frameOps =
                listOf(
                    MoveMetasprite(metaspriteId = "elephant"),
                    MoveMetasprite(metaspriteId = "tiger"),
                ),
        )

    return GameIR(
        name = "Seed011Game",
        scenes = listOf(titleScene, playScene),
        metasprites = listOf(elephant, tiger),
        startScene = "title",
    )
}

private fun playFrameBodyOrFail(): String {
    val output = GBDKPipeline().generate(buildTwoMetaspriteFrameGame())
    val cSource =
        output.files["bank1.c"]
            ?: output.files["main.c"]
            ?: error(
                "Neither bank1.c nor main.c generated by GBDKPipeline. Files: ${output.files.keys}"
            )
    val body = extractFunctionBody(cSource, "play_frame")
    assertTrue(
        body.isNotEmpty(),
        "Could not extract play_frame body from generated C. " +
            "Files generated: ${output.files.keys}\nbank1.c/main.c head:\n${cSource.take(1500)}",
    )
    return body
}

private fun titleFrameBodyOrFail(): String {
    val output = GBDKPipeline().generate(buildTwoMetaspriteFrameGame())
    val cSource =
        output.files["bank1.c"]
            ?: output.files["main.c"]
            ?: error(
                "Neither bank1.c nor main.c generated by GBDKPipeline. Files: ${output.files.keys}"
            )
    val body = extractFunctionBody(cSource, "title_frame")
    assertTrue(
        body.isNotEmpty(),
        "Could not extract title_frame body from generated C. " +
            "Files generated: ${output.files.keys}\nbank1.c/main.c head:\n${cSource.take(1500)}",
    )
    return body
}

// =============================================================================
// TEST CLASS
// =============================================================================

class Seed011HiwaterFrameScopeTest {

    // -------------------------------------------------------------------------
    // Test 1 — exactly ONE hide_sprites_range per frame body, regardless of
    // how many moveMetasprite() ops the scene contains.
    // -------------------------------------------------------------------------
    @Test
    fun play_frame_body_contains_exactly_one_hide_sprites_range_call() {
        val body = playFrameBodyOrFail()

        // split-and-count: occurrences = split.size - 1
        val occurrences = body.split("hide_sprites_range").size - 1
        assertEquals(
            1,
            occurrences,
            "play_frame body must contain EXACTLY ONE `hide_sprites_range(...)` call — " +
                "the frame-scope postlude (Plan 09 fix). Found $occurrences. " +
                "Pre-fix the per-call hiwater wrap emits one hide_sprites_range PER " +
                "moveMetasprite() call; with 2 calls in one frame that's 2 hides — the " +
                "second hide clobbers OAM slots written by the first metasprite. " +
                "play_frame body:\n${body.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // Test 2 — exactly ONE `hiwater = 0` initializer per frame body.
    //
    // Matches `hiwater = 0` and `hiwater = 0u` (current GBDK emission uses 0u
    // suffix per CLAUDE.md §"Literal Emission Convention"). Regex spans optional
    // whitespace around `=` to remain robust against minor formatting changes.
    // -------------------------------------------------------------------------
    @Test
    fun play_frame_body_contains_exactly_one_hiwater_init() {
        val body = playFrameBodyOrFail()

        val hiwaterInitRegex = Regex("""hiwater\s*=\s*0u?\b""")
        val occurrences = hiwaterInitRegex.findAll(body).count()
        assertEquals(
            1,
            occurrences,
            "play_frame body must contain EXACTLY ONE `hiwater = 0` (or `hiwater = 0u`) " +
                "initializer — the frame-scope prelude (Plan 09 fix). Found $occurrences. " +
                "Pre-fix the per-call hiwater wrap emits one `hiwater = 0u` PER " +
                "moveMetasprite() call; with 2 calls in one frame the second `hiwater = 0u` " +
                "RESETS the OAM slot counter, causing the second metasprite to overwrite " +
                "the first metasprite's OAM allocation. " +
                "play_frame body:\n${body.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // Test 3 — title_frame (no MoveMetasprite) must have ZERO hiwater
    // occurrences. Regression guard: scenes that don't use metasprites must
    // NOT pay the cost of a wrapper prelude/postlude.
    // -------------------------------------------------------------------------
    @Test
    fun title_frame_body_without_metasprite_has_zero_hiwater_init() {
        val body = titleFrameBodyOrFail()
        assertNotNull(body)

        val occurrences = body.split("hiwater").size - 1
        assertEquals(
            0,
            occurrences,
            "title_frame body must contain ZERO `hiwater` occurrences — the title " +
                "scene has no MoveMetasprite ops, so the wrap helper " +
                "(wrapFrameWithMetaspriteHiwater) must early-return and NOT add a prelude " +
                "or postlude. Found $occurrences. title_frame body:\n${body.take(2000)}",
        )
    }
}
