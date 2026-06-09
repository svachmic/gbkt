/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.backend.gbdk.GBDKBackend
import io.github.gbkt.backend.gbdk.codegen.visitor.SceneVisitor
import io.github.gbkt.core.dsl.SceneRef
import io.github.gbkt.core.dsl.buttons
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.BankSlot
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.FadeOp
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.PrintOp
import io.github.gbkt.core.ir.SceneIR
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// =============================================================================
// Phase 13.5 Plan 03 — isMbcGame-gated auto-exit synthesis (Req #15 / D-07)
//
// Tests the auto-`*_exit` BANKED synthesis that SceneVisitor emits for MBC
// games (cartridge.maxRomBanks > 2) when a scene has bankSlot.bank > 0 AND
// no user-declared exit {} block.
//
// Four tests:
//
//   1. MBC game + bank>0 + no exitOps → auto-emitted <id>_exit BANKED CFunction.
//      CORE gate: proves the auto-emit fires only when isMbcGame=true.
//
//   2. ROM_ONLY game (isMbcGame=false) + bank>0 + no exitOps → NO auto-emitted
//      exit. This is the Req #15 no-growth gate for pong/breakout.
//
//   3. MBC game + bank>0 + exitOps.isNotEmpty() → normal user-exit emitted, NOT
//      the auto-emit path (user exit takes precedence).
//
//   4. GBDKPipeline integration: MBC game generates *_exit_trampoline in main.c
//      AND includes the scene in navigate_to_scene exit cases (shouldAutoEmitExit
//      shared predicate coverage). ROM_ONLY counterpart does NOT get exit trampoline.
//
// Convention:
//   - EVIDENCE_DIR writes evidence before assertions (evidence-before-assert
//     pattern, CLAUDE.md §"Visual Evidence Rule").
//   - brace-walk extractFunctionBody helper copied from BanksEmissionTest.
//   - RED: SceneVisitor.visit() does not accept isMbcGame param yet → compile
//     error → tests fail. GREEN: after Task 1 implementation, all pass.
// =============================================================================

class AutoExitSynthesisTest {

    companion object {
        /**
         * Evidence written under the active checkout root (worktree-safe).
         *
         * `user.dir` resolves to the Gradle project working directory, which in a
         * Claude Code worktree is the worktree root. Hard-coding the main-repo path
         * would silently route evidence outside the active checkout (#3099).
         */
        val EVIDENCE_DIR =
            File(System.getProperty("user.dir"))
                .resolve(
                    "../.planning/phases/" +
                        "13.5-framework-primitives-graphics-level-codegen-inserted/" +
                        "evidence/tier1-shape"
                )
                .normalize()
    }

    // -------------------------------------------------------------------------
    // Helpers — brace-walk extraction (awk-equivalent)
    //
    // Copied verbatim from BanksEmissionTest (per PATTERNS.md § Scope-level
    // grep gates corollary — per-test duplication intentional).
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
    // TEST 1: MBC game + bank>0 + no exitOps → auto-emitted <id>_exit BANKED
    //
    // isMbcGame=true fires the auto-emit path. The produced CFunction must have:
    //   - name = "${scene.id}_exit"
    //   - returnType = CVoid
    //   - body = emptyList() (auto-synthesized is an empty stub)
    //   - bank = sceneBank (same bank as the scene)
    //   - isBanked = true
    // =========================================================================

    @Test
    fun `MBC game scene without exitOps gets auto-emitted exit BANKED function`() {
        val scene =
            SceneIR(
                id = "play",
                enterOps = listOf(PrintOp(text = "PLAY", position = PositionDef(8, 7))),
                exitOps = emptyList(), // no user exit block
                bankSlot = BankSlot(bank = 1),
            )

        // isMbcGame = true (cartridge.maxRomBanks > 2, e.g. MBC5_RAM_BATTERY=256)
        val functions = SceneVisitor.visit(scene, isMbcGame = true)

        EVIDENCE_DIR.mkdirs()
        val functionNames = functions.map { it.name }
        File(EVIDENCE_DIR, "auto-exit-mbc-functions.txt").writeText(functionNames.joinToString("\n"))

        val autoExitFn = functions.find { it.name == "play_exit" }

        assertNotNull(
            autoExitFn,
            "SceneVisitor must auto-emit play_exit for MBC game with bank>0 and no exitOps. " +
                "Functions emitted: $functionNames",
        )
        assertTrue(
            autoExitFn.isBanked,
            "Auto-synthesized play_exit must be BANKED (lives in bank ${scene.bankSlot?.bank}). " +
                "isBanked: ${autoExitFn.isBanked}",
        )
        assertTrue(
            autoExitFn.body.isEmpty(),
            "Auto-synthesized play_exit must have empty body. " +
                "body: ${autoExitFn.body}",
        )
        assertEquals(
            scene.bankSlot?.bank,
            autoExitFn.bank,
            "Auto-synthesized play_exit must carry bank=${scene.bankSlot?.bank} (same as scene). " +
                "Got bank: ${autoExitFn.bank}",
        )
    }

    // =========================================================================
    // TEST 2: ROM_ONLY game (isMbcGame=false) + bank>0 + no exitOps → NO exit
    //
    // This is the Req #15 no-growth gate for pong and breakout. ROM_ONLY has
    // maxRomBanks=2; its scenes get bankSlot.bank=1 from BankingAnalysisPass
    // FFD. The bare bank>0 predicate would fire — but isMbcGame=false prevents
    // it. This test is the binding gate that proves the correct discriminator.
    // =========================================================================

    @Test
    fun `ROM_ONLY game scene without exitOps does NOT get auto-emitted exit (no-growth gate)`() {
        val scene =
            SceneIR(
                id = "title",
                enterOps = listOf(PrintOp(text = "PONG", position = PositionDef(8, 7))),
                exitOps = emptyList(), // no user exit block
                bankSlot = BankSlot(bank = 1), // FFD assigns bank=1 even for ROM_ONLY
            )

        // isMbcGame = false (cartridge.maxRomBanks = 2, ROM_ONLY)
        val functions = SceneVisitor.visit(scene, isMbcGame = false)

        EVIDENCE_DIR.mkdirs()
        val functionNames = functions.map { it.name }
        File(EVIDENCE_DIR, "no-exit-rom-only-functions.txt").writeText(functionNames.joinToString("\n"))

        val unexpectedExitFn = functions.find { it.name == "title_exit" }

        assertNull(
            unexpectedExitFn,
            "SceneVisitor must NOT auto-emit title_exit for ROM_ONLY game (isMbcGame=false). " +
                "Req #15 no-growth gate: ROM_ONLY scenes must gain zero new *_exit functions. " +
                "Functions emitted: $functionNames",
        )
    }

    // =========================================================================
    // TEST 3: MBC game + bank>0 + exitOps.isNotEmpty() → user exit, NOT auto-emit
    //
    // When the user declares an exit {} block, the normal exit-emission path runs.
    // The auto-emit path must NOT duplicate the exit function. One exit function
    // emitted (from exitOps), with a non-empty body.
    // =========================================================================

    @Test
    fun `MBC game scene with user exitOps gets normal exit (auto-emit skipped)`() {
        val scene =
            SceneIR(
                id = "play",
                exitOps = listOf(FadeOp(fadeIn = false, frames = 0)), // user exit block
                bankSlot = BankSlot(bank = 1),
            )

        val functions = SceneVisitor.visit(scene, isMbcGame = true)

        val exitFunctions = functions.filter { it.name == "play_exit" }

        assertEquals(
            1,
            exitFunctions.size,
            "Must emit exactly ONE play_exit function (user exit; auto-emit skipped). " +
                "Got: ${exitFunctions.size} play_exit functions. Functions: ${functions.map { it.name }}",
        )
        assertFalse(
            exitFunctions.first().body.isEmpty(),
            "User-declared play_exit must have a non-empty body (contains FadeOp → C statements). " +
                "body: ${exitFunctions.first().body}",
        )
    }

    // =========================================================================
    // TEST 4: GBDKPipeline integration — MBC game generates *_exit_trampoline
    // in main.c AND includes the scene in navigate_to_scene exit cases.
    //
    // Verifies the shouldAutoEmitExit shared predicate wires through both:
    //   (a) buildTrampolinesForScene → emits play_exit_trampoline in main.c
    //   (b) buildNavigateToSceneFunction → includes play scene in exitCases switch
    //
    // The ROM_ONLY counterpart (isMbcGame=false) must NOT generate the trampoline
    // or the exit case. This exercises the integration path end-to-end.
    // =========================================================================

    @Test
    fun `MBC game pipeline emits exit trampoline and navigate exit case but ROM_ONLY does not`() {
        // MBC game: 2 scenes, no exit blocks → auto-exit fires for cross-bank MBC
        val mbcGame =
            game("MbcAutoExitTest") {
                    config {
                        cartridge(Cartridge.MBC5_RAM_BATTERY)
                    }
                    val play =
                        scene("play") {
                            frame {
                                whenever(buttons.start.pressed) { navigate(SceneRef("title")) }
                            }
                        }
                    val title =
                        scene("title") {
                            frame {
                                whenever(buttons.start.pressed) { navigate(play) }
                            }
                        }
                    start = title
                }
                .build()

        // ROM_ONLY game: same structure, ROM_ONLY cartridge
        val romOnlyGame =
            game("RomOnlyNoExitTest") {
                    config {
                        cartridge(Cartridge.ROM_ONLY)
                    }
                    val play =
                        scene("play") {
                            frame {
                                whenever(buttons.start.pressed) { navigate(SceneRef("title")) }
                            }
                        }
                    val title =
                        scene("title") {
                            frame {
                                whenever(buttons.start.pressed) { navigate(play) }
                            }
                        }
                    start = title
                }
                .build()

        // GBDKBackend.generate() runs bank analysis (BankingAnalysisPass) which assigns
        // bankSlot to each scene. Without bank analysis, bankSlot=null and buildTrampolineStubs
        // emits nothing (the filter `slot != null && slot.bank > 0` requires a non-null slot).
        // This matches BanksEmissionTest INV-5 which also uses generate() for trampoline tests.
        val backend = GBDKBackend()
        val mbcResult = backend.generate(mbcGame)
        val romOnlyResult = backend.generate(romOnlyGame)

        val mbcMainC = mbcResult.files["main.c"]?.content ?: error("main.c not generated for MBC game")
        val romOnlyMainC = romOnlyResult.files["main.c"]?.content ?: error("main.c not generated for ROM_ONLY game")

        EVIDENCE_DIR.mkdirs()
        File(EVIDENCE_DIR, "auto-exit-mbc-main.txt").writeText(mbcMainC.take(8000))
        File(EVIDENCE_DIR, "auto-exit-rom-only-main.txt").writeText(romOnlyMainC.take(8000))

        // ---- MBC assertions ----

        // (a) MBC game: play_exit_trampoline must be in main.c (from buildTrampolinesForScene)
        assertTrue(
            mbcMainC.contains("play_exit_trampoline"),
            "MBC game: main.c must contain play_exit_trampoline (auto-exit trampoline from " +
                "shouldAutoEmitExit shared predicate). main.c head:\n${mbcMainC.take(4000)}",
        )

        // (b) MBC game: navigate_to_scene must include play scene in exit switch cases
        val navigateBody = extractFunctionBody(mbcMainC, "navigate_to_scene")
        assertTrue(
            navigateBody.contains("play_exit") || navigateBody.contains("play_exit_trampoline"),
            "MBC game: navigate_to_scene must reference play_exit or play_exit_trampoline " +
                "(auto-exit navigate case). navigate body:\n${navigateBody.take(4000)}",
        )

        // ---- ROM_ONLY assertions ----

        // (c) ROM_ONLY game: NO play_exit_trampoline in main.c (no-growth gate)
        assertFalse(
            romOnlyMainC.contains("play_exit_trampoline"),
            "ROM_ONLY game: main.c must NOT contain play_exit_trampoline " +
                "(Req #15 no-growth gate: ROM_ONLY isMbcGame=false). " +
                "main.c head:\n${romOnlyMainC.take(4000)}",
        )

        // (d) ROM_ONLY game: navigate_to_scene must NOT include play scene in exit switch
        val romOnlyNavigateBody = extractFunctionBody(romOnlyMainC, "navigate_to_scene")
        assertFalse(
            romOnlyNavigateBody.contains("play_exit"),
            "ROM_ONLY game: navigate_to_scene must NOT reference play_exit " +
                "(no-growth gate). navigate body:\n${romOnlyNavigateBody.take(4000)}",
        )
    }
}
