/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.metasprites

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

// =============================================================================
// METASPRITES C EMISSION INVARIANTS — Phase 10 Plan 19 (D-12 oracle)
//
// Three JVM-tier Tier-1 tests assert the generated `play_frame` body contains
// the right shape for behaviors 1+2+3, guarding against future codegen
// regressions independent of UAT plans 17 + 18.
//
// D-12.1 — animation index advance: `_idx++` or `_idx = _idx + 1u` or `_idx = 0u`
//           (the wrap) or `sprite_elephant_frames[_idx]` (the consumer; CR-03 /
//           Phase 10.1 Plan 05 namespacing — formerly `sprite_metasprites[_idx]`)
// D-12.2 — flip OAM: at least one of `move_metasprite_flipy`, `move_metasprite_flipx`,
//           `move_metasprite_flipxy`, `OAMF_Y_FLIP`, `OAMF_X_FLIP`
// D-12.3 — sub-palette OAM: `OAMF_CGB_PAL` or `_rot >> 2` or `subpal` parameter
//
// Scope-level grep gate: per CLAUDE.md §"Scope-level grep gates" each assertion
// runs against the play_frame BODY (brace-walk extracted), not the file. This
// guards against unrelated functions in the generated C file masking regressions
// in play_frame (Pattern C from 10-PATTERNS.md §11, a regression class first
// surfaced by 07.4-23).
//
// Evidence-before-assert: every @Test writes its frame body to
// evidence/tier1-shape/ BEFORE assertions fire, so the C output shape is
// reviewable from disk even when a test is RED.
//
// D-overfitting-1: tests probe the CODEGEN ORACLE shape, not codegen tuning —
// token disjunctions accept multiple valid emission shapes (e.g. `_idx++` OR
// `_idx = _idx + 1u` OR `_idx = 0u` OR `sprite_elephant_frames[_idx]`). This
// ensures the tests remain GREEN across minor reformatting or expression
// lowering changes that preserve semantic correctness.
//
// Note on bank1.c vs main.c: for single-scene games with `romBanks = 2`, the
// BankingAnalysisPass fast-path places the scene in HOME bank (bank 0). The
// pipeline then folds scene functions into main.c and omits bank1.c entirely.
// `playFrameBody()` handles both configurations transparently.
// =============================================================================

class MetaspriteEmissionTest {

    companion object {
        /**
         * Evidence is written under the **active checkout root** (worktree-safe).
         *
         * `user.dir` resolves to the Gradle project's working directory, which inside a Claude Code
         * worktree is the worktree root — not the main repository. Hard-coding the main-repo
         * absolute path would silently route evidence files outside the active checkout and miss
         * the commit (#3099 worktree path safety).
         */
        val EVIDENCE_DIR =
            File(System.getProperty("user.dir"))
                .resolve(
                    "../../.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/tier1-shape"
                )
                .normalize()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts a C function body by brace-walking from the first line containing `void
     * ${functionName}(` until the matching closing brace at depth zero.
     *
     * The returned blob includes the signature line and the closing brace, so downstream
     * `.contains()` checks operate ONLY on tokens that live inside the named function — never on
     * tokens from unrelated functions in the same C file (per CLAUDE.md §"Scope-level grep gates").
     */
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

    /**
     * Runs the full GBDK pipeline for the metasprites game and extracts the `play_frame` function
     * body via brace-walk.
     *
     * Searches `bank1.c` first (standard multi-scene path); falls back to `main.c` for the
     * single-scene HOME fast-path where the pipeline folds scene functions into the home file and
     * omits `bank1.c`.
     */
    private fun playFrameBody(): String {
        val pipeline = GBDKPipeline()
        val pipelineOutput = pipeline.generate(metasprites.build())
        val cSource =
            pipelineOutput.files["bank1.c"]
                ?: pipelineOutput.files["main.c"]
                ?: error(
                    "Neither bank1.c nor main.c generated by GBDKPipeline — pipeline output empty?"
                )
        return extractFunctionBody(cSource, "play_frame")
    }

    // -------------------------------------------------------------------------
    // D-12.1 — animation index advance emission
    //   Asserts that play_frame body contains at least one token proving that
    //   `idx` is incremented and used to look up the current animation frame.
    //
    //   Accepted shapes (disjunction per D-overfitting-1):
    //     • `_idx++`               — pre-decrement shorthand
    //     • `_idx = _idx + 1u`    — compound-add lowering (UINT8 add)
    //     • `_idx = 0u`           — wrap-back to zero
    //     • `sprite_elephant_frames[_idx]` — consumer: array lookup with current idx
    //       (CR-03 / Phase 10.1 Plan 05: descriptor pointer table is namespaced by
    //       metasprite id; the unnamespaced `sprite_metasprites` no longer exists.)
    // -------------------------------------------------------------------------

    @Test
    fun `D-12_1 animation index advance emission - idx incremented and consumed in play_frame`() {
        EVIDENCE_DIR.mkdirs()
        val frameBody = playFrameBody()
        File(EVIDENCE_DIR, "01-animation-index-advance.txt").writeText(frameBody)

        assertTrue(
            frameBody.contains("_idx++") ||
                frameBody.contains("_idx = _idx + 1") ||
                frameBody.contains("_idx = 0") ||
                frameBody.contains("sprite_elephant_frames[_idx]"),
            "D-12.1: play_frame body must contain at least one of: " +
                "`_idx++`, `_idx = _idx + 1`, `_idx = 0`, or `sprite_elephant_frames[_idx]`. " +
                "These tokens prove the animation index (behavior 1 — B pressed → advance frame) " +
                "is emitted into the frame loop. " +
                "play_frame body:\n${frameBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // D-12.2 — flip OAM attribute byte write emission
    //   Asserts that play_frame body contains at least one flip-variant
    //   move_metasprite_* call, proving the A-pressed → cycle flip behavior
    //   (behavior 2) is rendered into the frame loop.
    //
    //   Accepted shapes (disjunction per D-overfitting-1):
    //     • `move_metasprite_flipy`  — Y-axis flip case
    //     • `move_metasprite_flipx`  — X-axis flip case
    //     • `move_metasprite_flipxy` — both-axes flip case
    //     • `OAMF_Y_FLIP`            — OAM flag constant (alternative lowering)
    //     • `OAMF_X_FLIP`            — OAM flag constant (alternative lowering)
    // -------------------------------------------------------------------------

    @Test
    fun `D-12_2 flip OAM attribute byte write emission - flip variant in play_frame`() {
        EVIDENCE_DIR.mkdirs()
        val frameBody = playFrameBody()
        File(EVIDENCE_DIR, "02-flip-oam-attribute.txt").writeText(frameBody)

        assertTrue(
            frameBody.contains("move_metasprite_flipy") ||
                frameBody.contains("move_metasprite_flipx") ||
                frameBody.contains("move_metasprite_flipxy") ||
                frameBody.contains("OAMF_Y_FLIP") ||
                frameBody.contains("OAMF_X_FLIP"),
            "D-12.2: play_frame body must contain at least one of: " +
                "`move_metasprite_flipy`, `move_metasprite_flipx`, `move_metasprite_flipxy`, " +
                "`OAMF_Y_FLIP`, or `OAMF_X_FLIP`. " +
                "These tokens prove the flip OAM attribute write (behavior 2 — A pressed → cycle flip) " +
                "is emitted into the frame loop. " +
                "play_frame body:\n${frameBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // D-12.3 — sub-palette OAM attribute byte write emission
    //   Asserts that play_frame body contains at least one token proving that
    //   the GBC sprite sub-palette (behavior 3) is applied during frame render.
    //
    //   Accepted shapes (disjunction per D-overfitting-1):
    //     • `OAMF_CGB_PAL`  — OAM CGB palette flag constant
    //     • `_rot >> 2`     — sub-palette extraction from rot variable
    //     • `subpal`        — GBDK move_metasprite_ex() subpal parameter name
    // -------------------------------------------------------------------------

    @Test
    fun `D-12_3 sub-palette OAM attribute byte write emission - subpal in play_frame`() {
        EVIDENCE_DIR.mkdirs()
        val frameBody = playFrameBody()
        File(EVIDENCE_DIR, "03-sub-palette-oam-attribute.txt").writeText(frameBody)

        assertTrue(
            frameBody.contains("OAMF_CGB_PAL") ||
                frameBody.contains("_rot >> 2") ||
                frameBody.contains("subpal"),
            "D-12.3: play_frame body must contain at least one of: " +
                "`OAMF_CGB_PAL`, `_rot >> 2`, or `subpal`. " +
                "These tokens prove the GBC sub-palette OAM attribute write " +
                "(behavior 3 — A pressed → cycle sub-palette, 4 GBC sprite palette slots) " +
                "is emitted into the frame loop. " +
                "play_frame body:\n${frameBody.take(4000)}",
        )
    }
}
