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
import io.github.gbkt.core.ir.MoveMetasprite
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// SEED-009 / CR-02 — per-bank `<gbdk/metasprites.h>` conditional include
//
// Phase 10 Plan 10 added `#include <gbdk/metasprites.h>` unconditionally to
// `main.c`, but `buildSceneFile()` (which emits `bank1.c`) does NOT scan for
// `MoveMetasprite` ops. When a multi-scene game escapes `BankingAnalysisPass`'s
// single-scene HOME fast-path (gbkt-analysis/.../BankingAnalysisPass.kt:91)
// the scene frame function lands in `bank1.c`. Any reference to
// `move_metasprite_*` then fails to compile under SDCC because the inline
// definitions live in `<gbdk/metasprites.h>`, which is missing from the bank
// file's `#include` block.
//
// This test locks the contract for Route A from SEED-009: the bank file
// includes the header IFF a scene in `gameIR.scenes` has a `MoveMetasprite`
// op in its `frameOps`.
//
// Pitfall 2 mitigation (RESEARCH lines 919-927): the test MUST escape the
// single-scene HOME fast-path. We construct 2 scenes; `assertNotNull(bank1)`
// fires BEFORE the include-check so a fast-path collapse does NOT silently
// pass the assertion against `main.c`.
// =============================================================================

class Seed009BankIncludeTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Build a 2-scene GameIR that escapes the BankingAnalysisPass single-scene HOME fast-path. The
     * `play` scene has the supplied [playFrameOps]; the `title` scene is a zero-op stub (still
     * counted by the `scenes.size == 1` static guard, so its presence forces the bin-packer path).
     *
     * Per Phase 10 Plan 10, a `MetaspriteIR` declaration is required for the
     * `MoveMetasprite("elephant")` op to be a structurally valid reference. The descriptor uses a
     * single 1-tile frame — minimal valid shape.
     */
    private fun build2SceneGame(playFrameOps: List<io.github.gbkt.core.ir.ScriptOp>): GameIR {
        val elephant =
            MetaspriteIR(
                id = "elephant",
                frames =
                    listOf(
                        MetaspriteFrame(
                            tiles = listOf(MetaspriteTile(relX = 0, relY = 0, tileId = 0))
                        )
                    ),
            )
        return GameIR(
            name = "Seed009Game",
            scenes = listOf(SceneIR(id = "title"), SceneIR(id = "play", frameOps = playFrameOps)),
            metasprites = listOf(elephant),
            startScene = "title",
        )
    }

    // -------------------------------------------------------------------------
    // Test 1 — positive case: MoveMetasprite present → bank1.c MUST include header
    // -------------------------------------------------------------------------
    @Test
    fun bank1_c_includes_metasprites_h_when_scene_frame_has_MoveMetasprite() {
        val game = build2SceneGame(playFrameOps = listOf(MoveMetasprite("elephant")))

        val output = GBDKPipeline().generate(game)
        val bank1 = output.files["bank1.c"]

        // Pitfall 2 mitigation: if bank1.c does not exist (e.g. fast-path
        // collapsed multi-scene into HOME), the include-check would silently
        // pass against main.c. Fail loudly so the test setup error is
        // distinguishable from the codegen bug under test.
        assertNotNull(
            bank1,
            "bank1.c was not generated — multi-scene setup failed to escape " +
                "BankingAnalysisPass single-scene HOME fast-path; the include-check " +
                "would silently pass against main.c. Inspect output.files keys: ${output.files.keys}",
        )

        assertTrue(
            bank1.contains("#include <gbdk/metasprites.h>"),
            "bank1.c MUST include <gbdk/metasprites.h> when a scene has a " +
                "MoveMetasprite op (CR-02 / SEED-009 Route A). Generated includes:\n" +
                bank1.lines().filter { it.startsWith("#include") }.joinToString("\n"),
        )
    }

    // -------------------------------------------------------------------------
    // Test 2 — negative case: no MoveMetasprite → bank1.c MUST NOT include header
    // (regression guard against unconditional include)
    // -------------------------------------------------------------------------
    @Test
    fun bank1_c_does_not_include_metasprites_h_when_no_scene_uses_metasprite() {
        // 2-scene game where NEITHER scene has a MoveMetasprite op. Empty
        // frameOps is sufficient — the conditional scan only triggers on
        // MoveMetasprite ScriptOps.
        val game = build2SceneGame(playFrameOps = emptyList())

        val output = GBDKPipeline().generate(game)
        val bank1 = output.files["bank1.c"]

        assertNotNull(
            bank1,
            "bank1.c was not generated — multi-scene setup failed to escape " +
                "BankingAnalysisPass single-scene HOME fast-path. Inspect output.files keys: " +
                "${output.files.keys}",
        )

        assertFalse(
            bank1.contains("#include <gbdk/metasprites.h>"),
            "bank1.c MUST NOT include <gbdk/metasprites.h> when no scene has a " +
                "MoveMetasprite op (regression guard against unconditional include). " +
                "Generated includes:\n" +
                bank1.lines().filter { it.startsWith("#include") }.joinToString("\n"),
        )
    }
}
