/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.pong

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

// =============================================================================
// Phase 13.5 Plan 03 — Pong no-growth regression guard (Req #15 / D-07)
//
// Pong uses Cartridge.ROM_ONLY (maxRomBanks=2) → isMbcGame=false. BankingAnalysisPass
// FFD places all 3 scenes (title, game, gameover) in bank=1 (the only non-HOME bank
// available for ROM_ONLY). The CORRECT discriminator isMbcGame=false must prevent
// auto-emitting `*_exit` for pong scenes even though bankSlot.bank=1 > 0.
//
// Without the isMbcGame gate, the bare bank>0 predicate would auto-emit:
//   title_exit, game_exit, gameover_exit
// in pong's bank1.c — causing the ROM size to grow with no user request. This
// test is the binding gate that proves the correct discriminator (maxRomBanks > 2,
// not bare bank>0) is in force.
//
// Evidence-before-assert convention (CLAUDE.md §"Visual Evidence Rule"):
// Each extracted `*_exit` body is written to EVIDENCE_DIR BEFORE the assertion fires
// so a RED run still produces a reviewable artifact on disk.
//
// Scope-level grep gate (CLAUDE.md §"Scope-level grep gates"):
// extractFunctionBody() brace-walks the specific function — no file-level grep.
// This prevents false positives from other `*_exit` occurrences in bank1.c
// (e.g., a hypothetical future `helper_exit_something` function).
//
// brace-walk helper uses `void $functionName(` anchor (as per BanksEmissionTest
// INV-1 pattern) which tolerates the BANKED keyword in the signature.
// =============================================================================

class PongNoExitRegressionTest {

    companion object {
        /**
         * Evidence is written under the **active checkout root** (worktree-safe).
         *
         * `user.dir` resolves to the Gradle project's working directory, which inside a Claude Code
         * worktree is the worktree root — not the main repository. Hard-coding the main-repo
         * absolute path would silently route evidence files outside the active checkout (#3099).
         */
        val EVIDENCE_DIR =
            File(System.getProperty("user.dir"))
                .resolve(
                    "../../.planning/phases/" +
                        "13.5-framework-primitives-graphics-level-codegen-inserted/" +
                        "evidence/tier1-shape"
                )
                .normalize()
    }

    // -------------------------------------------------------------------------
    // Helpers — brace-walk extraction (VERBATIM from BanksEmissionTest)
    //
    // Uses `it.contains("void $functionName(")` as the anchor — this tolerates
    // the BANKED keyword that auto-exit functions carry (`void title_exit(void) BANKED`).
    // Per BanksEmissionTest INV-1 pattern (CLAUDE.md §"Scope-level grep gates").
    // -------------------------------------------------------------------------

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

    // =========================================================================
    // Regression guard: pong scenes must NOT gain auto-emitted `*_exit` functions
    //
    // Pong: 3 scenes (title, game, gameover), ROM_ONLY (maxRomBanks=2).
    // GBDKPipeline.generate() uses isMbcGame = maxRomBanks > 2 = false.
    // All three scene `*_exit` function bodies must be absent from bank1.c.
    //
    // The `assertFalse(body.isNotEmpty(), ...)` assertion fires when the body IS
    // non-empty — i.e., when a `*_exit` function was found in bank1.c. This is
    // the no-growth gate: presence of a body means the predicate failed.
    // =========================================================================

    @Test
    fun `pong ROM_ONLY scenes do NOT gain auto-emitted exit functions (no-growth gate)`() {
        val pipeline = GBDKPipeline()
        val output = pipeline.generate(pong.build())
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Extract each scene's *_exit body BEFORE asserting (evidence-before-assert pattern)
        val titleExitBody = extractFunctionBody(bank1C, "title_exit")
        val gameExitBody = extractFunctionBody(bank1C, "game_exit")
        val gameoverExitBody = extractFunctionBody(bank1C, "gameover_exit")

        File(EVIDENCE_DIR, "pong-no-exit-title.txt")
            .writeText("title_exit body (must be empty — ROM_ONLY no-growth gate):\n$titleExitBody")
        File(EVIDENCE_DIR, "pong-no-exit-game.txt")
            .writeText("game_exit body (must be empty — ROM_ONLY no-growth gate):\n$gameExitBody")
        File(EVIDENCE_DIR, "pong-no-exit-gameover.txt")
            .writeText(
                "gameover_exit body (must be empty — ROM_ONLY no-growth gate):\n$gameoverExitBody"
            )

        // Assert: each body must be empty (no *_exit function exists in bank1.c)
        // assertFalse(body.isNotEmpty()) ≡ assertTrue(body.isEmpty()) — no *_exit emitted.
        assertFalse(
            titleExitBody.isNotEmpty(),
            "ROM_ONLY no-growth gate FAILED: title_exit must NOT appear in pong bank1.c. " +
                "Pong uses Cartridge.ROM_ONLY (maxRomBanks=2) → isMbcGame=false → " +
                "auto-exit synthesis predicate must be false. " +
                "body found:\n${titleExitBody.take(2000)}",
        )
        assertFalse(
            gameExitBody.isNotEmpty(),
            "ROM_ONLY no-growth gate FAILED: game_exit must NOT appear in pong bank1.c. " +
                "Pong uses Cartridge.ROM_ONLY (maxRomBanks=2) → isMbcGame=false → " +
                "auto-exit synthesis predicate must be false. " +
                "body found:\n${gameExitBody.take(2000)}",
        )
        assertFalse(
            gameoverExitBody.isNotEmpty(),
            "ROM_ONLY no-growth gate FAILED: gameover_exit must NOT appear in pong bank1.c. " +
                "Pong uses Cartridge.ROM_ONLY (maxRomBanks=2) → isMbcGame=false → " +
                "auto-exit synthesis predicate must be false. " +
                "body found:\n${gameoverExitBody.take(2000)}",
        )
    }
}
