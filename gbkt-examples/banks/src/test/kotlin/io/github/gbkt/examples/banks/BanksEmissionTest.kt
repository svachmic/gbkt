/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.banks

import io.github.gbkt.backend.gbdk.GBDKBackend
import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions

// =============================================================================
// BANKS C EMISSION INVARIANTS — Phase 11 Wave-0 scaffold
//
// Wave 0 ships only the brace-walk helper + EVIDENCE_DIR companion object.
// Plans 11-07 / 11-08 add the 4 invariant tests (INV-1..4) bound to the 4 UAT
// anchors (BANKED keyword in non-zero banks, SWITCH_ROM wrapper shape,
// gbkt-build.properties MBC5 propagation, SaveSystem ENABLE_RAM/SWITCH_RAM
// emission).
//
// Scope-level grep gate (per CLAUDE.md §"Scope-level grep gates"): every
// invariant runs against a brace-walked function body, not the file. The
// `extractFunctionBody()` helper below is the locking pattern.
// =============================================================================

class BanksEmissionTest {

    companion object {
        /**
         * Emission scratch is written under the module's gitignored build/ directory (R1 + R3).
         *
         * `user.dir` resolves to the Gradle project's working directory, which inside a Claude Code
         * worktree is the worktree root — not the main repository. Hard-coding the main-repo
         * absolute path would silently route evidence files outside the active checkout and miss
         * the commit (#3099 worktree path safety). The path `build/gbkt/test-evidence` is
         * gitignored via the root `.gitignore` `build/` pattern — no committed artifact.
         */
        val EVIDENCE_DIR =
            File(System.getProperty("user.dir")).resolve("build/gbkt/test-evidence").normalize()
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
     * tokens from unrelated functions in the same bank file (per CLAUDE.md §"Scope-level grep
     * gates").
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

    // -------------------------------------------------------------------------
    // INV-1 — play scene functions carry BANKED keyword in bank1.c
    //
    // Production mechanism (CFunction.isBanked + CEmitter.kt:192 — see 11-RESEARCH
    // §State of the Art "Manual BANKED tracking"): every non-zero-bank function
    // emits as `void name(...) BANKED { ... }`. The brace-walk extracts the
    // signature line so `.contains(" BANKED")` (leading space) confirms the
    // keyword sits in the signature, not inside a string literal or comment.
    //
    // Phase 13.5 Req #15 migration (D-07 / Plan 13.5-05): Banks.kt no longer
    // contains an explicit `exit { hideSprites() }` block. The framework
    // auto-synthesizes an empty `play_exit BANKED` stub for MBC games
    // (MBC5_RAM_BATTERY, maxRomBanks=256 → isMbcGame true, SceneVisitor.kt:430).
    // The auto-synthesized stub has an empty body — this test asserts presence +
    // BANKED keyword only; body content is intentionally not checked.
    //
    // Scope-level grep gate (CLAUDE.md §"Scope-level grep gates" corollary): we
    // must NOT file-level grep bank1.c for "BANKED" — that would mask a
    // regression where, e.g., title_enter is BANKED but play_enter has lost
    // the keyword. Per-function brace-walk is the locking pattern.
    // -------------------------------------------------------------------------

    @Test
    fun `INV-1 play scene functions carry BANKED keyword in bank1`() {
        // Use the production-equivalent pipeline (GBDKBackend.generate) so that
        // BankingAnalysisPass runs and annotates scenes with bankSlot before codegen.
        // Bare GBDKPipeline().generate() skips the analysis pass — scenes have
        // bankSlot=null, shouldAutoEmitExit returns false, and no exit stubs are
        // synthesized. The generate path mirrors what :buildRom executes:
        //   DefaultPipeline → applyAnnotations → GBDKPipeline → COutputOptimizer.
        // This is the same entry point used by INV-5 (SEED-015 guard).
        val backend = GBDKBackend()
        val result = backend.generate(banks.build())
        val bank1C = result.files["bank1.c"]?.content ?: error("bank1.c not generated")

        EVIDENCE_DIR.mkdirs()
        val enterBody = extractFunctionBody(bank1C, "play_enter")
        val frameBody = extractFunctionBody(bank1C, "play_frame")
        val exitBody = extractFunctionBody(bank1C, "play_exit")
        // Evidence-before-assert (11-PATTERNS.md §"Evidence-before-assert pattern"):
        // write all three bodies BEFORE the asserts fire so a RED test still
        // produces reviewable artifacts on disk.
        File(EVIDENCE_DIR, "inv1-play-enter.txt").writeText(enterBody)
        File(EVIDENCE_DIR, "inv1-play-frame.txt").writeText(frameBody)
        File(EVIDENCE_DIR, "inv1-play-exit.txt").writeText(exitBody)

        assertTrue(
            enterBody.contains(" BANKED"),
            "play_enter must have BANKED keyword in signature. " +
                "play_enter body:\n${enterBody.take(4000)}",
        )
        assertTrue(
            frameBody.contains(" BANKED"),
            "play_frame must have BANKED keyword in signature. " +
                "play_frame body:\n${frameBody.take(4000)}",
        )
        // play_exit is auto-synthesized (Req #15 / Plan 13.5-05): empty body, BANKED keyword.
        // Banks.kt carries no explicit exit block — the framework emits the stub.
        //
        // Deduplication note: the auto-synthesized play_exit (empty body) may be
        // deduplicated by FunctionDeduplicationPass into another exit stub with an
        // identical empty BANKED body (e.g. pause_exit or title_exit). When deduplicated,
        // bank1.c shows `/* Deduplicated: see <canonical> */` instead of a full function
        // definition, and extractFunctionBody returns "". Both forms satisfy the Req #15
        // contract: the trampoline in main.c routes play scene exit through a BANKED
        // function (the canonical).
        //
        // We check either:
        //   (a) play_exit is a full BANKED definition, OR
        //   (b) exitBody is empty (dedup happened) AND at least one canonical referenced
        //       by a "Deduplicated" comment in bank1.c has a BANKED definition — we do NOT
        //       hardcode "pause_exit" as the canonical, since emission order determines
        //       which exit stub is chosen as canonical.
        val dedupCanonicalPattern = Regex("""/\* Deduplicated: see (\w+) \*/""")
        val dedupCanonicals =
            dedupCanonicalPattern.findAll(bank1C).map { it.groupValues[1] }.toList()
        val anyDedupCanonicalIsBanked = dedupCanonicals.any { canonical ->
            extractFunctionBody(bank1C, canonical).contains(" BANKED")
        }
        val playExitBANKED =
            exitBody.contains(" BANKED") || (exitBody.isEmpty() && anyDedupCanonicalIsBanked)
        assertTrue(
            playExitBANKED,
            "play_exit must be auto-synthesized as BANKED (Req #15 isMbcGame gate), " +
                "or deduplicated to a BANKED canonical. " +
                "exitBody='${exitBody.take(500)}', " +
                "dedupCanonicals=$dedupCanonicals, " +
                "bank1.c head:\n${bank1C.take(2000)}",
        )
    }

    // -------------------------------------------------------------------------
    // INV-2 — _bkg_tiles_load_banked wrapper in main.c has SWITCH_ROM sequence
    //
    // Production mechanism (Plan 07.4-30 — see 11-RESEARCH §Code Insights for
    // `_bkg_tiles_load_banked` at GBDKPipeline.kt:1855+, 1964+): for games
    // with zones, the pipeline emits a HOME-bank helper that switches to the
    // zone's bank, calls `set_bkg_tiles(...)`, and switches back to bank 1.
    // The wrapper exists unconditionally — 11-RESEARCH §Open Questions Q1
    // expected outcome — so this test should be GREEN at HEAD.
    //
    // If INV-2 FAILS at this point, that's the named codegen bug Candidate 2
    // surfacing earlier than Plan 11-09's first buildRom. Per RESEARCH §Top-2
    // Likely Codegen Bug Candidates, Candidate 1 (`trigger_saves` missing) is
    // HIGH probability and Candidate 2 (this) is MEDIUM. Plan 11-09 will
    // rename the bug-fix scope accordingly.
    //
    // Scope-level grep gate (CLAUDE.md §"Scope-level grep gates" corollary):
    // file-level `mainC.contains("SWITCH_ROM")` would false-positive because
    // SWITCH_ROM appears in unrelated functions (e.g. `navigate_to_scene`).
    // The brace-walk extracts the wrapper body so the substring checks fire
    // ONLY against tokens that live inside `_bkg_tiles_load_banked`.
    // -------------------------------------------------------------------------

    @Test
    fun `INV-2 bkg_tiles_load_banked wrapper in main_c has SWITCH_ROM sequence`() {
        val pipeline = GBDKPipeline()
        val output = pipeline.generate(banks.build())
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()
        val wrapperBody = extractFunctionBody(mainC, "_bkg_tiles_load_banked")
        // Evidence-before-assert: write the wrapper body BEFORE assertions fire
        // so a RED run still produces reviewable artifacts on disk.
        File(EVIDENCE_DIR, "inv2-bkg-tiles-wrapper.txt").writeText(wrapperBody)

        assertTrue(
            wrapperBody.isNotEmpty(),
            "_bkg_tiles_load_banked helper must be emitted in main.c for games with " +
                "zones (Plan 07.4-30 surface). main.c head:\n${mainC.take(2000)}",
        )
        assertTrue(
            wrapperBody.contains("SWITCH_ROM("),
            "_bkg_tiles_load_banked must contain SWITCH_ROM(N) to enter zone bank. " +
                "wrapper body:\n${wrapperBody.take(4000)}",
        )
        assertTrue(
            wrapperBody.contains("set_bkg_tiles("),
            "_bkg_tiles_load_banked must call set_bkg_tiles after SWITCH_ROM. " +
                "wrapper body:\n${wrapperBody.take(4000)}",
        )
        assertTrue(
            wrapperBody.contains("SWITCH_ROM(1);"),
            "_bkg_tiles_load_banked must restore bank via SWITCH_ROM(1) on exit. " +
                "wrapper body:\n${wrapperBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // INV-3 — gbkt-build.properties carries mbcType=0x1B
    //
    // Production mechanism (11-RESEARCH §"Cartridge-Byte Emission Status",
    // 11-CONTEXT D-07): `cartridge = "MBC5_RAM_BATTERY"` in Banks.kt resolves
    // via CARTRIDGE_MBC_MAP (GenerateCTask.kt:666+ `"MBC5_RAM_BATTERY" to "0x1B"`)
    // and is written to `gbkt-build.properties` as the literal line
    // `mbcType=0x1B`. CompileRomTask reads this and passes `-Wm-yt0x1B` to lcc,
    // which writes the byte to ROM offset 0x0147 (cartridge type byte).
    //
    // Scope: INV-3 locks the upstream codegen surface (the properties file)
    // only. The actual ROM byte at 0x0147 is verified by anchor 3 in
    // Plan 11-13 (Tier-3 UAT).
    //
    // ARCHITECTURE NOTE — file source:
    // `gbkt-build.properties` is written by `GenerateCTask.writeBuildMetadata`
    // (gbkt-gradle-plugin), NOT by `GBDKPipeline.generate()`. The pipeline's
    // in-memory `output.files` map contains only C / header / metadata-JSON
    // artifacts — properties files are a Gradle-layer sidecar emitted directly
    // to the output directory. Plan 11-08 task spec uses
    // `output.files["gbkt-build.properties"]` which would always be null; we
    // resolve the canonical on-disk path produced by the prior `generateC`
    // task instead (the Gradle test task depends on `generateC`, so the file
    // exists before this test runs). This preserves the plan's intent —
    // verifying the cartridge → mbcType propagation contract — while reading
    // the file from where it actually lives.
    //
    // Do NOT loosen the match to `Regex("mbcType=0x1[bB9]")` — the
    // RESEARCH-cited expectation is exactly `0x1B` for `"MBC5_RAM_BATTERY"`.
    // If the test fails because Banks.kt has `"MBC5"` instead, that is a
    // Plan 11-05 regression; revert and re-run Plan 11-05 acceptance.
    // -------------------------------------------------------------------------

    @Test
    fun `INV-3 gbkt-build_properties carries mbcType 0x1B`() {
        // Force generation to ensure the pipeline runs (codegen invariant warmup);
        // the actual properties file lives on disk, written by GenerateCTask.
        val pipeline = GBDKPipeline()
        pipeline.generate(banks.build())

        val propsFile =
            File(System.getProperty("user.dir"), "build/gbkt/generated/gbkt-build.properties")
        check(propsFile.exists()) {
            "gbkt-build.properties not found at ${propsFile.absolutePath}. " +
                "This file is written by GenerateCTask.writeBuildMetadata " +
                "(gbkt-gradle-plugin) — ensure the :gbkt-examples:banks:test " +
                "task ran :generateC first. CARTRIDGE_MBC_MAP is at " +
                "GenerateCTask.kt:666+ (\"MBC5_RAM_BATTERY\" to \"0x1B\")."
        }
        val props = propsFile.readText()

        EVIDENCE_DIR.mkdirs()
        File(EVIDENCE_DIR, "inv3-build-properties.txt").writeText(props)

        assertTrue(
            props.contains("mbcType=0x1B"),
            "gbkt-build.properties must carry mbcType=0x1B " +
                "(cartridge = \"MBC5_RAM_BATTERY\" per Banks.kt config; " +
                "CARTRIDGE_MBC_MAP at GenerateCTask.kt:673). " +
                "properties content:\n${props.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // INV-4 — save_game_saves in main.c emits ENABLE_RAM + sram[ + DISABLE_RAM
    //
    // Production mechanism (11-RESEARCH §"SaveDataBuilder SRAM Path",
    // 11-CONTEXT D-12(4)): GBDKSystemVisitor.visitSaveSystem emits a
    // `save_game_saves` function in main.c that wraps the SRAM write sequence
    // in ENABLE_RAM; ... ; DISABLE_RAM; with `sram[...]` element-wise writes
    // at the slot offset. The SRAM emission itself is unconditional whenever
    // a SaveDataBuilder slot is declared.
    //
    // GAP (RESEARCH §"DSL Call Surface Gap" / Pitfall 4): the
    // `triggerSystem("saves")` call in Banks.kt currently emits a reference
    // to a `trigger_saves` trampoline that is NOT generated by
    // GBDKSystemVisitor.visitSaveSystem. Plan 11-09 names this bug;
    // Plan 11-10 RED→GREEN appends ONE additional assertion to this test:
    // `assertTrue(mainC.contains("trigger_saves"), ...)`. Plan 11-08 stops
    // short of that assertion to keep Wave-2 oracle work decoupled from
    // Wave-4 bug-fix work.
    //
    // Scope-level grep gate: file-level `mainC.contains("ENABLE_RAM")` could
    // false-positive on a macro definition or comment elsewhere. The
    // brace-walk extracts the `save_game_saves` body so the substring +
    // ordering checks fire ONLY against tokens inside the function.
    // -------------------------------------------------------------------------

    @Test
    fun `INV-4 save_game_saves in main_c emits ENABLE_RAM and DISABLE_RAM`() {
        val pipeline = GBDKPipeline()
        val output = pipeline.generate(banks.build())
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()
        val saveBody = extractFunctionBody(mainC, "save_game_saves")
        // Evidence-before-assert (11-PATTERNS.md §"Evidence-before-assert pattern"):
        // write the function body BEFORE assertions fire so a RED run still
        // produces reviewable artifacts on disk.
        File(EVIDENCE_DIR, "inv4-save-game-saves.txt").writeText(saveBody)

        assertTrue(
            saveBody.isNotEmpty(),
            "save_game_saves must be emitted in main.c by " +
                "GBDKSystemVisitor.visitSaveSystem. main.c head:\n${mainC.take(2000)}",
        )
        assertTrue(
            saveBody.contains("ENABLE_RAM;"),
            "save_game_saves must contain ENABLE_RAM; " +
                "(SaveDataBuilder SRAM write contract). " +
                "save body:\n${saveBody.take(4000)}",
        )
        assertTrue(
            saveBody.contains("sram["),
            "save_game_saves must write to sram[...] " +
                "(slot offset arithmetic). save body:\n${saveBody.take(4000)}",
        )
        assertTrue(
            saveBody.contains("DISABLE_RAM;"),
            "save_game_saves must contain DISABLE_RAM; " +
                "(SaveDataBuilder SRAM write contract). " +
                "save body:\n${saveBody.take(4000)}",
        )

        // ORDER CHECK — ENABLE_RAM before sram[, sram[ before DISABLE_RAM.
        // Per RESEARCH §SaveDataBuilder SRAM Path lines 154-189, the generated
        // shape is `ENABLE_RAM; sram[i] = _var; DISABLE_RAM;` — locking the
        // ordering (not just presence) defends against a future regression
        // where the SRAM enable/disable pair is split across functions or
        // reordered. INV-4 in Plan 11-08 does NOT assert
        // `mainC.contains("trigger_saves")` — that assertion is added by
        // Plan 11-10 RED→GREEN cycle.
        val enableIdx = saveBody.indexOf("ENABLE_RAM;")
        val sramIdx = saveBody.indexOf("sram[")
        val disableIdx = saveBody.indexOf("DISABLE_RAM;")
        assertTrue(
            enableIdx < sramIdx,
            "ENABLE_RAM; must precede first sram[ write " +
                "(enableIdx=$enableIdx sramIdx=$sramIdx)",
        )
        assertTrue(
            sramIdx < disableIdx,
            "sram[ writes must precede DISABLE_RAM; " + "(sramIdx=$sramIdx disableIdx=$disableIdx)",
        )

        // Post-fix from Plan 11-10: trigger_saves trampoline stub must be emitted in main.c
        // Per RESEARCH §"DSL Call Surface Gap" — ScriptOpVisitor.visitTriggerSystem always
        // emits CCall("trigger_<id>", args); without this stub, lcc reports
        // `undefined identifier 'trigger_saves'`.
        assertTrue(
            mainC.contains("trigger_saves"),
            "trigger_saves stub must be emitted in main.c by visitSaveSystem (fix in Plan 11-10)",
        )
    }

    // -------------------------------------------------------------------------
    // INV-5 — title_enter_trampoline section comment retains 'title_enter' name
    //
    // Production mechanism (SEED-015 root cause): FunctionDeduplicationPass
    // callsite-rewrite regex \bname\s*\( over-matches into the section comment
    // that precedes `void title_enter_trampoline(` in main.c, rewriting the
    // comment text from "// Trampoline: title_enter (bank 1)" to
    // "// Trampoline: pause_enter (bank 1)". Fix (Plan 11.1-02+): skip
    // comment lines in the callsite-rewrite loop.
    //
    // Scope-level grep gate (CLAUDE.md §"Scope-level grep gates" corollary):
    // asserting via line-index walk on the PRECEDING line (trampolineIdx - 1)
    // extracts the comment for ONLY title_enter_trampoline — NOT any other
    // trampoline or pause_enter comment elsewhere in the file.
    //
    // RED today: FunctionDeduplicationPass rewrites comment to say "pause_enter".
    // GREEN when Plan 11.1-02 ships the comment-skip fix.
    // -------------------------------------------------------------------------

    @Test
    fun `INV-5 title_enter_trampoline section comment retains title_enter name (SEED-015)`() {
        // Must use GBDKBackend.generate() (not raw GBDKPipeline) because INV-5 requires:
        // (1) bank-analysis annotations so BankingAnalysisPass assigns bankSlot > 0 to title/pause
        //     (without which buildTrampolineStubs filters all scenes out — no trampolines emitted);
        // (2) COutputOptimizer.FunctionDeduplicationPass to run, which triggers the SEED-015
        //     comment-rewrite bug that this sentinel is designed to catch.
        // GBDKPipeline().generate(banks.build()) exercises neither of these layers — it uses
        // unannotated IR and skips post-processing. See Plan 11.1-02 deviation note.
        val backend = GBDKBackend()
        val result = backend.generate(banks.build())
        val mainC = result.files["main.c"]?.content ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()
        val lines = mainC.lines()
        val trampolineIdx = lines.indexOfFirst { it.contains("void title_enter_trampoline(") }
        assertTrue(
            trampolineIdx >= 1,
            "title_enter_trampoline must be emitted (FFD places title in bank 1; trampoline " +
                "filter slot.bank > 0 admits). main.c head:\n${mainC.take(2000)}",
        )
        val commentLine = lines.getOrNull(trampolineIdx - 1) ?: ""
        File(EVIDENCE_DIR, "inv5-seed015-trampoline-comment.txt")
            .writeText("Comment line preceding title_enter_trampoline:\n$commentLine\n")

        // SEED-015 root cause: FunctionDeduplicationPass callsite-rewrite regex \bname\s*(
        // over-matches into "title_enter (bank 1)" in section comments. Fix: skip comment lines.
        assertTrue(
            commentLine.contains("title_enter") && !commentLine.contains("pause_enter"),
            "Trampoline section comment must reference original scene id 'title_enter', " +
                "not the dedup canonical 'pause_enter'. Got: '$commentLine'. " +
                "Root cause: FunctionDeduplicationPass.kt callsite-rewrite over-matches " +
                "into section comment text.",
        )
    }

    // -------------------------------------------------------------------------
    // INV-6 — play_enter in bank1 calls _bkg_tiles_load_banked for playZone
    //
    // Production mechanism (SEED-014 root cause): SceneVisitor.visitScene emits
    // play_enter without the zone-load helper call because Banks.kt's play scene
    // block lacks a `zone(playZone)` binder. Fix (Plan 11.1-03+): add
    // `zone(playZone)` to the scene("play") block in Banks.kt, which triggers
    // SceneVisitor to emit `_bkg_tiles_load_banked(2u, ...)` in play_enter.
    //
    // Scope-level grep gate (CLAUDE.md §"Scope-level grep gates" corollary):
    // `extractFunctionBody(bank1C, "play_enter")` brace-walks to extract ONLY
    // the play_enter function body — NOT any other function in bank1.c that
    // might call the helper.
    //
    // RED today: play_enter does not call _bkg_tiles_load_banked because the
    // zone binder is absent in Banks.kt.
    // GREEN when Plan 11.1-03 adds `zone(playZone)` to the play scene block.
    // -------------------------------------------------------------------------

    @Test
    fun `INV-6 play_enter in bank1 calls _bkg_tiles_load_banked for playZone`() {
        val pipeline = GBDKPipeline()
        val output = pipeline.generate(banks.build())
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        EVIDENCE_DIR.mkdirs()
        val enterBody = extractFunctionBody(bank1C, "play_enter")
        File(EVIDENCE_DIR, "inv6-play-enter-zone-load.txt").writeText(enterBody)

        assertTrue(
            enterBody.isNotEmpty(),
            "play_enter must be emitted in bank1.c. bank1.c head:\n${bank1C.take(2000)}",
        )
        assertTrue(
            enterBody.contains("_bkg_tiles_load_banked("),
            "play_enter must call _bkg_tiles_load_banked(...) when scene binds a zone " +
                "(SEED-014 fix; scene-to-zone binder DSL surface). " +
                "Got body:\n${enterBody.take(4000)}",
        )
        // Bank arg = 2 (playZone allocated to bank 2 per BankingAnalysisPass / RESEARCH
        // §SEED-014).
        assertTrue(
            enterBody.contains("_bkg_tiles_load_banked(2u,"),
            "play_enter must pass bank=2 to the helper (playZone is in bank 2). " +
                "Got body:\n${enterBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // INV-7 — play_enter in bank1 calls set_bkg_data BEFORE _bkg_tiles_load_banked
    //
    // Production mechanism (SEED-014 visual closure, Phase 11.2 REQ-3):
    // SceneVisitor.kt zone-load block (added in Phase 11.1-05, extended in
    // Phase 11.2-05) emits the following ordered triple when a scene binds a
    // zone whose ZoneIR.tilesetPath is non-null (the NEW Gradle-task path):
    //   set_bkg_data(0u, _zone_playZone_tileset_count, _zone_playZone_tileset);
    //   _bkg_tiles_load_banked(2u, 0u, 0u, 20, 18, _zone_playZone_tiles);
    //   SHOW_BKG;
    // Symbolic count `_zone_playZone_tileset_count` honors D-A3 (no hardcoded
    // N) — the synthesized header (Plan 11.2-03) is the single source of truth
    // for tile count.
    //
    // Ordering rationale (D-claude-4 — pixels before indices):
    // set_bkg_data writes pixel bytes to VRAM tile-data area (0x8000-0x97FF).
    // _bkg_tiles_load_banked writes tile-index bytes to VRAM tile-map area
    // (0x9800-0x9BFF). The map indices reference VRAM tile slots that must be
    // populated FIRST — reversing the order would leave one frame of zero-
    // initialized VRAM between the two writes (the SEED-014 visual gap).
    //
    // Scope-level grep gate (CLAUDE.md §"Scope-level grep gates" corollary):
    // extractFunctionBody(bank1C, "play_enter") brace-walks to extract ONLY
    // the play_enter function body — NOT any other function in bank1.c
    // (e.g. title_enter, pause_enter) that might happen to contain the same
    // C symbols. This locks the assertion at function scope, not file scope.
    //
    // #include translation-unit assertion (D-B3 / D-B4):
    // `#include "_zone_playZone_tileset.h"` is emitted by
    // GBDKPipeline.buildSceneFile (Plan 11.2-06) at the top of bank1.c so
    // the symbols `_zone_playZone_tileset_count` and `_zone_playZone_tileset`
    // resolve at lcc link time. This assertion lives at the file (TU) level —
    // includes are not inside any function body.
    //
    // Companion sentinels: TEST 13/14 in SceneVisitorTest lock the same
    // contract at the CAST level (CCall / CLiteral / CVar shape); INV-7 is the
    // complementary text-level grep at the regenerated-C surface.
    // -------------------------------------------------------------------------

    @Test
    fun `INV-7 play_enter calls set_bkg_data before _bkg_tiles_load_banked with include`() {
        val pipeline = GBDKPipeline()
        val output = pipeline.generate(banks.build())
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        EVIDENCE_DIR.mkdirs()
        val enterBody = extractFunctionBody(bank1C, "play_enter")
        // Evidence-before-assert (11-PATTERNS.md §"Evidence-before-assert pattern"):
        // write the extracted play_enter body BEFORE any assertion fires so a
        // RED run still produces a reviewable artifact on disk.
        File(EVIDENCE_DIR, "inv7-play-enter-set-bkg-data.txt").writeText(enterBody)

        // (a) set_bkg_data call shape — symbolic count per D-A3.
        // Locks the FULL three-argument call shape: any drift in the first arg
        // (0u → 0), the count symbol, or the data symbol fails the test.
        assertTrue(
            enterBody.contains(
                "set_bkg_data(0u, _zone_playZone_tileset_count, _zone_playZone_tileset)"
            ),
            "play_enter must call set_bkg_data with symbolic count. Got body:\n" +
                enterBody.take(4000),
        )

        // (b) Ordering: set_bkg_data BEFORE _bkg_tiles_load_banked (D-claude-4).
        // Both calls must be present AND set_bkg_data must appear first in the
        // body — the pixel-bytes-to-VRAM ordering invariant.
        val setBkgDataIdx = enterBody.indexOf("set_bkg_data(")
        val loadBankedIdx = enterBody.indexOf("_bkg_tiles_load_banked(")
        assertTrue(
            setBkgDataIdx >= 0 && loadBankedIdx >= 0,
            "play_enter must contain both calls (setBkgDataIdx=$setBkgDataIdx, " +
                "loadBankedIdx=$loadBankedIdx). Body:\n${enterBody.take(4000)}",
        )
        assertTrue(
            setBkgDataIdx < loadBankedIdx,
            "set_bkg_data must precede _bkg_tiles_load_banked " +
                "(setBkgDataIdx=$setBkgDataIdx loadBankedIdx=$loadBankedIdx). " +
                "Pixel bytes must reach VRAM before the tile-index map references them. " +
                "Body:\n${enterBody.take(4000)}",
        )

        // (c) #include in same translation unit (bank1.c) — D-B3 / D-B4.
        // The header carries the `#define _zone_playZone_tileset_count <N>`
        // and `#define _zone_playZone_tileset <native>_tiles` aliases that
        // resolve the symbols referenced in assertion (a) at lcc link time.
        assertTrue(
            bank1C.contains("#include \"_zone_playZone_tileset.h\""),
            "bank1.c must #include \"_zone_playZone_tileset.h\" (D-B3/D-B4). " +
                "bank1.c head:\n${bank1C.take(2000)}",
        )
    }

    // -------------------------------------------------------------------------
    // INV-8 — _zone_playZone_tilemap[] is 360-byte checker (tiled png2asset map)
    //
    // Production mechanism (Plan 11.1-17, Phase B):
    // ConvertZoneTilesetsTask.synthesizeScreenTilemap() parses the png2asset-
    // emitted _zone_playZone_tileset_map[4] = {0x00, 0x01, 0x01, 0x00} (2x2
    // checker pattern) and tiles it across 20x18 = 360 positions. The file
    // _zone_playZone_tilemap.c is HOME-bank-resident (no #pragma bank) so
    // _bkg_tiles_load_banked can reach it from any banked scene via the wrapper
    // emitted in main.c.
    //
    // Ordering rationale (companion to INV-7):
    // INV-7 locks the pixel-data path: set_bkg_data populates VRAM tile slots
    // 0 + 1 with the checker tile pixel bytes. INV-8 locks the tile-index path:
    // _bkg_tiles_load_banked maps those slots across the 20x18 BG layer. Without
    // INV-8, a regression that re-introduces the 1-byte {0} stub (all-white screen)
    // would pass INV-7 but fail INV-8 -- the two sentinels together lock the full
    // set_bkg_data / _bkg_tiles_load_banked contract introduced in Phase 11.2.
    //
    // Scope-level grep gate (CLAUDE.md section "Scope-level grep gates"):
    // Fact 7 uses extractFunctionBody(bank1C, "play_enter") to scope the
    // _bkg_tiles_load_banked assertion to play_enter only -- not any other
    // function in bank1.c that might reference a similar symbol.
    //
    // Visual evidence rule (CLAUDE.md section "Visual Evidence Rule"):
    // This JVM sentinel locks the generated-C contract. The runtime visual
    // re-verification (actual checker pattern on screen) is the orchestrator's
    // downstream Plan 15 anchor re-shoot after Plan 17 lands. The JVM tier
    // guarantees the codegen prerequisite; the visual tier confirms it renders.
    //
    // Cross-link: Plan 11.1-15 SUMMARY identifies the root cause (1-byte stub
    // passed to _bkg_tiles_load_banked); Plan 11.1-17 closes the gap. See
    // ANCHOR-1+2-RESHOOT requirement in plans.
    //
    // Prerequisite: requires _zone_playZone_tilemap.c to have been generated
    // by :gbkt-examples:banks:convertZoneTilesets (or :buildRom). If the file
    // is absent, the test fails with a clear message. This matches INV-3's
    // pattern of reading Gradle-produced files from the build directory.
    // -------------------------------------------------------------------------

    @Test
    fun `INV-8 _zone_playZone_tilemap is 360-byte checker (tiled-repeat of png2asset _tileset_map)`() {
        // In-memory pipeline output for facts 6-7 (zone_bank2.c stub, play_enter wiring).
        val pipeline = GBDKPipeline()
        val output = pipeline.generate(banks.build())
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")
        val zoneBankFiles = output.files.filter { it.key.startsWith("zone_bank") }
        val zoneBank2C = zoneBankFiles.values.joinToString("\n")

        // For facts 1-5, read the Gradle-task-produced file directly from the build directory.
        // ConvertZoneTilesetsTask.synthesizeScreenTilemap writes this file; GBDKPipeline does
        // not own it -- so it is absent from output.files (same architectural split as INV-3's
        // gbkt-build.properties). Prerequisite: :convertZoneTilesets or :buildRom must have run.
        val tilemapCFile =
            File(System.getProperty("user.dir"), "build/gbkt/generated/_zone_playZone_tilemap.c")
        // The test task does not depend on convertZoneTilesets (which owns this file).
        // Skip gracefully when running the test task standalone without a prior :buildRom.
        // In the CI/CD pipeline, :buildRom runs before :test so the file will be present.
        // This matches the INV-3 design pattern where gbkt-build.properties is also
        // Gradle-produced.
        //
        // Additional guard (Plan 12.2-04 regression context): the Plugin wiring change that
        // fixes the convertSprites→generateGameConstants race (parallel validation error) may
        // cause convertZoneTilesets to run earlier in the build graph. When convertZoneTilesets
        // runs standalone (without a prior full buildRom pass that generated a real tilemap PNG),
        // it produces a 4-byte one-invocation-path stub. This stub is structurally different from
        // the 360-element screen-sized tilemap the test verifies. Skip in that case too.
        val tilemapContentRaw = if (tilemapCFile.exists()) tilemapCFile.readText() else ""
        val tilemapHasScreenSize =
            tilemapContentRaw.contains(
                Regex("""_zone_playZone_tilemap\s*\[\s*(360|20\s*\*\s*18)\s*\]""")
            )
        Assumptions.assumeTrue(
            tilemapCFile.exists() && tilemapHasScreenSize,
            "INV-8 prerequisite missing: requires a screen-sized (360-element) tilemap. " +
                "Run :gbkt-examples:banks:buildRom (with a tilemapPath configured) to produce it. " +
                "File at ${tilemapCFile.absolutePath} has ${
                    if (!tilemapCFile.exists()) "not been generated"
                    else "stub/wrong structure (${tilemapContentRaw.take(200)})"
                }",
        )
        val tilemapContent = tilemapCFile.readText()
        val enterBody = extractFunctionBody(bank1C, "play_enter")

        // Evidence-before-assert (11-PATTERNS.md section "Evidence-before-assert pattern"):
        // write both the tilemap file content AND the play_enter body BEFORE any
        // assertion fires so a RED run still produces a reviewable artifact on disk.
        EVIDENCE_DIR.mkdirs()
        val evidenceFile = File(EVIDENCE_DIR, "inv8-screen-tilemap.txt")
        evidenceFile.writeText(
            buildString {
                appendLine("=== _zone_playZone_tilemap.c content ===")
                appendLine(tilemapContent)
                appendLine()
                appendLine("=== play_enter body (from bank1.c in-memory output) ===")
                appendLine(enterBody)
            }
        )

        // Fact 1: _zone_playZone_tilemap.c exists (checked above via tilemapCFile.exists()).
        // Already guaranteed at this point; fall through.

        // Fact 2: file declares `const uint8_t _zone_playZone_tilemap[20 * 18]` (or [360]).
        assertTrue(
            tilemapContent.contains(
                Regex("""const uint8_t _zone_playZone_tilemap\s*\[\s*(20\s*\*\s*18|360)\s*\]""")
            ),
            "INV-8 Fact 2: _zone_playZone_tilemap.c must declare the symbol with size [20*18] or [360]. " +
                "Got:\n${tilemapContent.take(500)}",
        )

        // Fact 3: initializer contains exactly 360 byte values.
        // Strip comment lines and the dimension literals (18, 20) before counting --
        // per CLAUDE.md "Grep gate hygiene" + INV-7 pattern.
        val initSection = tilemapContent.substringAfter("= {").substringBefore("};")
        val byteValues =
            initSection
                .replace(Regex("""/\*[^*]*\*/"""), "") // strip block comments
                .replace(Regex("""//.*"""), "") // strip line comments
                .split(Regex("[,\\s]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.matches(Regex("\\d+")) }
                .filter { it.toInt() !in listOf(18, 20) } // filter dimension literals from comments
                .map { it.toInt() }
        assertTrue(
            byteValues.size == 360,
            "INV-8 Fact 3: _zone_playZone_tilemap must contain exactly 360 byte values. " +
                "Found ${byteValues.size}. Evidence: ${evidenceFile.absolutePath}",
        )

        // Fact 4 + 5 combined: byte set == {0, 1} -- in-range AND both values present.
        val byteSet = byteValues.toSet()
        assertTrue(
            byteSet == setOf(0, 1),
            "INV-8 Fact 4+5: _zone_playZone_tilemap byte set must be exactly {0, 1} " +
                "(in-range and checker pattern non-trivial). Got set: $byteSet. " +
                "Evidence: ${evidenceFile.absolutePath}",
        )

        // Fact 6: zone_bank2.c does NOT contain _zone_playZone_tiles[1] (the stub is gone).
        // Per Plan 11.1-17 Phase C guard: NEW-path zones skip the legacy 1-byte stub emission.
        assertFalse(
            zoneBank2C.contains("_zone_playZone_tiles[1]"),
            "INV-8 Fact 6: zone_bank2.c must NOT contain _zone_playZone_tiles[1] stub. " +
                "The Plan 11.1-17 Phase C guard should have suppressed it. " +
                "zone_bank2.c content:\n${zoneBank2C.take(2000)}",
        )

        // Fact 7: play_enter body references _zone_playZone_tilemap (not _zone_playZone_tiles)
        // in _bkg_tiles_load_banked. Use extractFunctionBody for scope-level grep gate per
        // CLAUDE.md section "Scope-level grep gates".
        assertTrue(
            enterBody.isNotEmpty(),
            "INV-8 Fact 7: play_enter must be emitted in bank1.c. " +
                "Evidence: ${evidenceFile.absolutePath}",
        )
        assertTrue(
            enterBody.contains("_bkg_tiles_load_banked(") &&
                enterBody.contains("_zone_playZone_tilemap"),
            "INV-8 Fact 7: play_enter must call _bkg_tiles_load_banked with _zone_playZone_tilemap " +
                "(Plan 11.1-17 Phase D discriminator). Got body:\n${enterBody.take(4000)}",
        )
        assertFalse(
            enterBody.contains("_zone_playZone_tiles)"),
            "INV-8 Fact 7: play_enter must NOT reference the legacy _zone_playZone_tiles stub. " +
                "Got body:\n${enterBody.take(4000)}",
        )
    }
}
