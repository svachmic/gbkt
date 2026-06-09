/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.platformer.codegen

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.genre.platformer.domain.PlatformerPhysicsConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

// =============================================================================
// PHASE 12.7 R-05 INVARIANT — snap-to-tile-top emission shape lock
//
// Phase 12.7 D-02 patches `PlatformerVisitor.buildVerticalFootProbe(...)` to
// append a deterministic snap-to-tile-top step to the inner `anyHit` thenBody:
//
//   foot_tile_row = (player_real_y + height) >> 3;
//   posYSym       = ((foot_tile_row << 3) - height) << 4;
//
// This single-assignment snap is STRONGER than the reference `player.c`,
// which relies on the iterative stuck-resolve while-loop. The snap pins the
// player's foot row to the top edge of the underlying solid tile, closing
// the 3–5 px hover bug observed in Phase 12.6 anchor-5/01-near-end.png.
//
// R-05 (this test) locks the emission SHAPE: if a future refactor removes the
// snap from `buildVerticalFootProbe`, this test FAILS RED. That is the bug
// class CLAUDE.md §"Visual Evidence Rule" — JVM tier scope-level grep gates
// were codified to catch.
//
// CURRENT COMMIT STATE (Phase 12.7 Wave 2): RED. `buildVerticalFootProbe`
// has NOT yet been modified (W3 plans 12.7-03..05 do that). The
// `foot_tile_row` token is therefore absent from `platformer_physics_update`
// at this commit, so this test EXITS NON-ZERO — exactly the observable
// RED→GREEN transition the W2 plan asks for.
//
// CLAUDE.md §"Scope-level grep gates" forbids a file-level
// `mainC.contains("foot_tile_row")` here because tokens like `_player_y`
// appear in many places in main.c (the `player_real_y` CVarDecl init, the
// section-6 integrate, etc.). The brace-walk extracts the
// platformer_physics_update body so substring checks fire ONLY against
// tokens inside the function. Anchor token `foot_tile_row` is unique to
// the snap block — no false positives.
//
// -----------------------------------------------------------------------------
// PLAN 12.7-10 STRENGTHENING (gap closure, post-W3 GREEN, pre-W7 RED) — the
// substring-only assertions on `foot_tile_row` + `_player_y` (W2 R-05) were
// NECESSARY but INSUFFICIENT to catch the C-operator-precedence bug that
// shipped through Plan 12.7-04's GREEN verdict.
//
// 12.7-04 emitted the snap as a single inlined expression:
//     _player_y = foot_tile_row << 3u - 24u << 4u;
// C precedence rules (C11 §6.5.6 vs §6.5.7) bind `+`/`-` TIGHTER than
// `<<`/`>>`, so this parses as `foot_tile_row << (3u - 24u) << 4u`. Unsigned
// underflow (3u - 24u → very large UINT) produces a garbage `_player_y` and
// glues the sprite to the top of the screen (user UAT 2026-05-26).
//
// Plan 12.7-04 SUMMARY claims "printer encodes intended left-to-right
// grouping" — that claim is INCORRECT. CEmitter.kt:426 emits CBinaryExpr as
// "${left} ${op} ${right}" with NO precedence-aware parens; the C AST has no
// CParenExpr node today. The erratum is recorded by Plan 12.7-16 SUMMARY
// (Round-4 close section) — 12.7-04-SUMMARY is NOT mutated.
//
// Plan 12.7-11 fixes the codegen by splitting the algebra across intermediate
// `CVarDecl`s so each line carries at most ONE binary-op class, making the
// emission precedence-immune:
//
//     UINT16 foot_tile_row     = (player_real_y + height) >> 3;
//     UINT16 foot_pixel_top    = foot_tile_row << 3;
//     UINT16 foot_pixel_anchor = foot_pixel_top - height;
//     posYSym                  = foot_pixel_anchor << 4;
//
// Plan 12.7-10 (this file) appends two new assertions naming the intermediate
// vars (`foot_pixel_top`, `foot_pixel_anchor`). The names are unique to the
// Plan-11 emission shape, so this test is RED at commit time (12.7-10) and
// flips GREEN once Plan 12.7-11 lands. CLAUDE.md §"Visual Evidence Rule" was
// codified precisely for this bug class — JVM-tier emission tests must lock
// the SHAPE one level below the visual outcome.
// =============================================================================

class PlatformerPhysicsSnapToTileTopEmissionTest {

    companion object {
        /**
         * Evidence is written under the **active checkout root** (worktree-safe).
         *
         * `user.dir` resolves to `<repo>/gbkt-genre-platformer` for the
         * `:gbkt-genre-platformer:test` task. Ascending one level (`..`) reaches the active
         * repo (or worktree) root, then we descend into the Phase 12.7 evidence directory.
         * Hard-coding an absolute path would silently route evidence files outside the
         * active worktree and miss the commit (#3099 worktree path safety).
         */
        val EVIDENCE_DIR =
            File(System.getProperty("user.dir"))
                .resolve(
                    "../.planning/phases/12.7-player-levitating-physics-codegen/evidence/tier1-shape"
                )
                .normalize()
    }

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts a C function body by brace-walking from the first line whose contents start
     * with [functionSignaturePrefix] (e.g. `void platformer_physics_update`) until the
     * matching closing brace at depth zero.
     *
     * The returned blob includes the signature line and the closing brace, so downstream
     * `.contains()` checks operate ONLY on tokens that live inside the named function —
     * never on tokens from unrelated functions in the same file (per CLAUDE.md
     * §"Scope-level grep gates" corollary).
     *
     * This is the Kotlin-side mirror of the awk pattern documented in VALIDATION.md row 2:
     *
     * ```
     * awk '/^void platformer_physics_update/{p=1;d=0} p{d+=gsub(/{/,""); d-=gsub(/}/,""); if(d<0)exit} p'
     * ```
     *
     * Matching is anchored to the START of a line (the prefix must appear at column 0) so
     * occurrences inside string literals, comments, or argument lists of a different
     * function cannot false-match. This is the literal counterpart of awk's `/^prefix/`
     * anchor.
     *
     * The helper is BYTE-IDENTICAL (modulo anchor change at call site) to the copy in
     * `TilemapCollisionEmissionTest.kt` lines 90–110. Convention: the helper is INLINED in
     * each sibling test class — not factored to a shared utility — per
     * `12.7-PATTERNS.md` §"Shared Patterns / brace-walk extractFunctionBody — inline per
     * test class".
     */
    private fun extractFunctionBody(cSource: String, functionSignaturePrefix: String): String {
        val lines = cSource.lines()
        val startIdx = lines.indexOfFirst { it.startsWith(functionSignaturePrefix) }
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
     * Build a minimal GameIR carrying a single `platformer_physics` GenericSystem.
     *
     * IMPORTANT: This GameIR DELIBERATELY OMITS a `tilemap_collision` system. That
     * routes `posYSym` resolution down the legacy-fallback path in PlatformerVisitor
     * (kt:554):
     *
     *   val posYSym = "_" + ((tcSystem?.config?.get("posYVar") as? String) ?: "player_y")
     *
     * — so `posYSym` resolves to the literal `_player_y` (RESEARCH Finding 6 +
     * Pitfall 6). The snap-assertion below anchors on `_player_y` accordingly. Picking
     * one of the two posYSym forms and keeping the test GameIR consistent with it is
     * the explicit guidance from Pitfall 6.
     *
     * Shape mirrors `TilemapCollisionEmissionTest.buildPlatformerGameIR` (the canonical
     * analog per 12.7-PATTERNS.md), modulo: name string, and default solidThreshold
     * (kept at 17 — the platformer-template value).
     */
    private fun buildPlatformerGameIR(solidThreshold: Int? = 17): GameIR {
        val config =
            PlatformerPhysicsConfig(
                gravity = 2,
                jumpForce = 8,
                terminalVelocity = 12,
                solidThreshold = solidThreshold,
            )
        val system =
            GenericSystem(
                id = "plat",
                config = mapOf("type" to "platformer_physics", "physicsConfig" to config),
            )
        return GameIR(
            name = "TestSnapToTileTopGame",
            config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2),
            scenes = listOf(SceneIR(id = "gameplay")),
            systems = listOf(system),
            startScene = "gameplay",
        )
    }

    // -------------------------------------------------------------------------
    // R-05 — platformer_physics_update emits the snap-to-tile-top step
    //
    // Production mechanism (Phase 12.7 D-02 / Wave 3 — PlatformerVisitor
    // `buildVerticalFootProbe` thenBody append): on foot-probe `anyHit` the
    // inner CIf thenBody is extended with a `CVarDecl(name="foot_tile_row", ...)`
    // local and a `CExprStatement(CBinaryExpr(CVar(posYSym), "=", ...))` snap.
    //
    // Scope-level grep gate (CLAUDE.md §"Scope-level grep gates" corollary):
    // a file-level `mainC.contains("foot_tile_row")` would be acceptable here
    // (the token is unique to the snap block) but the brace-walk is the
    // canonical pattern, locks the contract one level tighter, and matches
    // the sibling TilemapCollisionEmissionTest's anchoring discipline.
    //
    // RED at this commit (12.7-02): `foot_tile_row` is absent from
    // `platformer_physics_update`. The first `assertTrue(physicsBody.contains
    // ("foot_tile_row"), ...)` FAILS — observable in `./gradlew :gbkt-genre-
    // platformer:test --tests "*.PlatformerPhysicsSnapToTileTopEmissionTest"`
    // output. Phase 12.7 W3 plans 03–05 flip this GREEN.
    // -------------------------------------------------------------------------

    @Test
    fun `platformer_physics_update emits snap-to-tile-top assignment inside foot-probe block`() {
        val gameIR = buildPlatformerGameIR(solidThreshold = 17)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Anchor WITHOUT the `(void)` parameter-list suffix per Pitfall 3 — broader
        // and future-proof against any benign signature drift. The emitted C is
        // `void platformer_physics_update(void) {`; `startsWith("void platformer_physics_update")`
        // matches the column-0 line.
        val physicsBody = extractFunctionBody(mainC, "void platformer_physics_update")

        // Evidence-before-assert: persist the extracted body BEFORE any assertion
        // fires so a RED run still produces a reviewable artifact on disk
        // (per TilemapCollisionEmissionTest's INV-1..4 evidence-before-assert pattern).
        File(EVIDENCE_DIR, "platformer_physics_update.c").writeText(physicsBody)

        assertTrue(
            physicsBody.isNotEmpty(),
            "platformer_physics_update body must be extractable via brace-walk from main.c. " +
                "main.c head:\n${mainC.take(2000)}",
        )

        // PRIMARY R-05 gate: `foot_tile_row` is the CVarDecl local emitted by the
        // snap-to-tile-top step (Phase 12.7 D-02). It is unique to the snap block
        // and does not appear elsewhere in `platformer_physics_update`, so its
        // presence inside the brace-walked body is necessary and sufficient
        // evidence that the snap emission landed.
        //
        // The failure message MUST include the truncated body so a RED-run review
        // can see WHAT was emitted and confirm the gap is exactly the missing
        // snap (not, e.g., a different upstream regression that broke extraction).
        assertTrue(
            physicsBody.contains("foot_tile_row"),
            "platformer_physics_update body must contain foot_tile_row " +
                "(snap-to-tile-top CVarDecl per Phase 12.7 D-02). " +
                "body:\n${physicsBody.take(4000)}",
        )

        // SECONDARY R-05 gate: the snap writes `posYSym`. Because this test's
        // GameIR omits the `tilemap_collision` system, `posYSym` resolves to the
        // legacy-fallback symbol `_player_y` (RESEARCH Finding 6 + Pitfall 6).
        // Asserting `_player_y` here pins the test to the legacy-fallback form
        // and documents the choice in-source. The integration path (with a
        // `tilemap_collision` system) is exercised by sibling emission tests
        // (TilemapPhysicsPlayerSymbolEmissionTest, Defect4SymbolRewriteEmissionTest)
        // — keeping the contracts split is the D-04 RED-isolation guarantee.
        assertTrue(
            physicsBody.contains("_player_y"),
            "platformer_physics_update body must contain _player_y " +
                "(snap writes posYSym = legacy fallback _player_y when GameIR has no " +
                "tilemap_collision system — RESEARCH Pitfall 6). " +
                "body:\n${physicsBody.take(4000)}",
        )

        // =====================================================================
        // GAP-CLOSURE assertions (Phase 12.7-10) — precedence-class lock
        //
        // BUG CLASS: C operator precedence — `+`/`-` (C11 §6.5.6) binds
        // TIGHTER than `<<`/`>>` (C11 §6.5.7). The Plan 12.7-04 emission
        //     _player_y = foot_tile_row << 3u - 24u << 4u;
        // parses as `foot_tile_row << (3u - 24u) << 4u`. Unsigned underflow
        // → garbage `_player_y` → player glued to top of screen (user UAT
        // 2026-05-26, evidence/tier1-shape/platformer_physics_update.c:25).
        //
        // WHY SUBSTRING ASSERTIONS WERE INSUFFICIENT: the W2 R-05 gates
        // (`foot_tile_row` + `_player_y` above) BOTH co-exist with the
        // broken inlined expression. Substring presence is not a precedence
        // contract. CEmitter.kt:426 emits CBinaryExpr as "${left} ${op}
        // ${right}" with NO precedence-aware parens — the C AST has no
        // CParenExpr today; AST surgery is out of scope for Phase 12.7 (see
        // SEED-PHASE-X-CPAREN-EXPR-IN-C-AST.md).
        //
        // FIX SHAPE (Plan 12.7-11 GREEN companion): one binary op per
        // CVarDecl initializer — intermediate vars `foot_pixel_top` and
        // `foot_pixel_anchor` split the algebra into precedence-immune
        // single-op lines:
        //
        //     UINT16 foot_tile_row     = (player_real_y + height) >> 3;
        //     UINT16 foot_pixel_top    = foot_tile_row << 3;
        //     UINT16 foot_pixel_anchor = foot_pixel_top - height;
        //     posYSym                  = foot_pixel_anchor << 4;
        //
        // RED→GREEN: the intermediate-var NAMES are unique to the Plan-11
        // emission. Naming them in the contract makes this test RED at
        // commit time (12.7-10) — assertions 4 and 5 fail because the
        // current Plan-04 emission does not contain `foot_pixel_top` or
        // `foot_pixel_anchor` — and GREEN once Plan 12.7-11 ships the
        // intermediate-vars codegen. CLAUDE.md §"Visual Evidence Rule" was
        // codified for this bug class — JVM emission tests must lock the
        // SHAPE one level below the visual outcome.
        //
        // SCOPE DISCIPLINE: both new assertions scope on `physicsBody` (the
        // brace-walked function body) per CLAUDE.md §"Scope-level grep
        // gates corollary" — NO file-level `mainC.contains(...)`.
        // =====================================================================

        // ASSERTION 4 — `foot_pixel_top` intermediate-var CVarDecl must
        // appear inside the foot-probe body. Its presence forces the
        // codegen to split `foot_tile_row << 3` onto a single line where
        // C precedence cannot misparse the snap algebra.
        assertTrue(
            physicsBody.contains("foot_pixel_top"),
            "platformer_physics_update body must contain `foot_pixel_top` " +
                "intermediate var (Plan 12.7-11 precedence-immune snap shape). " +
                "RED at this commit (12.7-10) is EXPECTED: Plan 12.7-04 emitted " +
                "the snap as a single inlined expression `foot_tile_row << 3u - 24u << 4u`, " +
                "which C precedence parses as `foot_tile_row << (3u - 24u) << 4u` → " +
                "unsigned underflow → garbage _player_y → player glued to top of screen " +
                "(user UAT 2026-05-26). CEmitter.kt:426 emits CBinaryExpr with no " +
                "precedence-aware parens; the C AST has no CParenExpr today. The fix " +
                "(Plan 12.7-11) splits the algebra into intermediate CVarDecls: " +
                "`foot_pixel_top = foot_tile_row << 3` is the second line of that " +
                "intermediate-vars shape, making the snap algebra precedence-immune. " +
                "body:\n${physicsBody.take(4000)}",
        )

        // ASSERTION 5 — `foot_pixel_anchor` intermediate-var CVarDecl must
        // appear inside the foot-probe body. Its presence forces the
        // codegen to split `foot_pixel_top - height` onto a single line
        // (no adjacent shift) so C precedence cannot reorder the subtract
        // ahead of a neighboring `<<`.
        assertTrue(
            physicsBody.contains("foot_pixel_anchor"),
            "platformer_physics_update body must contain `foot_pixel_anchor` " +
                "intermediate var (Plan 12.7-11 precedence-immune snap shape). " +
                "RED at this commit (12.7-10) is EXPECTED: Plan 12.7-04's inlined " +
                "snap `foot_tile_row << 3u - 24u << 4u` mixes `-` and `<<` in one " +
                "expression, and C precedence (C11 §6.5.6 vs §6.5.7) binds `-` " +
                "TIGHTER than `<<` — the printer at CEmitter.kt:426 emits no parens " +
                "and the C AST has no CParenExpr. The Plan 12.7-11 fix splits the " +
                "algebra: `foot_pixel_anchor = foot_pixel_top - height` is the third " +
                "line of the intermediate-vars shape — its presence guarantees the " +
                "subtract is isolated from the trailing `<< 4` shift, making the " +
                "snap precedence-immune. " +
                "body:\n${physicsBody.take(4000)}",
        )

        // =====================================================================
        // ROUND-5 H1 FIX — pivot_adjust correction
        // (Plan 12.7-18 RED → Plan 12.7-19 GREEN)
        //
        // BUG CLASS: snap-target chooses HITBOX foot, but the user sees the
        // METASPRITE foot. They differ by `pivot_adjust` px under the
        // platformer-template's pivot(12, 6) + frameSize(24, 32) +
        // hitbox 8×24 geometry:
        //
        //     pivot_adjust = frameSize.height − pivotY − hitbox.height
        //                  = 32 − 6 − 24
        //                  = 2 px
        //
        // EVIDENCE: Plan 12.7-17 Round-5 diagnostic Section 2 (numeric trace
        // of metasprite_t[] layout under SPRITES_8x16 mode) shows the rendered
        // metasprite-bottom at pixel 130 vs the hitbox-foot snap target at
        // pixel 128 — exactly matches the user's anchor-2 report ("ALMOST
        // perfect, 1-2 px too low, overlays top 2 pixels of ground tile",
        // 2026-05-26 human-verify on Plan 12.7-15). Section 5's verdict is
        // H1 + H2 (compound): H1 is THIS codegen defect; H2 is a UAT-harness
        // capture-timing artifact (W14 scope, not this plan's site).
        //
        // CURRENT EMISSION (Plan 12.7-11 intermediate-vars form): the snap
        // block has NEITHER `pivot_adjust` NOR `height_plus_pivot` —
        // therefore this assertion FAILS RED at commit time (12.7-18).
        //
        // FIX SHAPE (Plan 12.7-19 GREEN companion — `buildVerticalFootProbe`
        // gets a 5th CVarDecl named `pivot_adjust`):
        //
        // Option A (default — extra intermediate var):
        //     UINT16 foot_tile_row     = (player_real_y + 24u) >> 3u;
        //     UINT16 foot_pixel_top    = foot_tile_row << 3u;
        //     UINT16 pivot_adjust      = 2u;
        //     UINT16 foot_pixel_anchor = foot_pixel_top - 24u - pivot_adjust;
        //     _player_y                = foot_pixel_anchor << 4u;
        //
        // Option B (folded — pivot_adjust subtracted from height into a
        // height_plus_pivot symbol):
        //     UINT16 height_plus_pivot = 26u;
        //     UINT16 foot_pixel_anchor = foot_pixel_top - height_plus_pivot;
        //     _player_y                = foot_pixel_anchor << 4u;
        //
        // The assertion locks the SEMANTIC outcome (either name + either
        // value) without over-fitting to a specific line shape — W13 picks
        // Option A or Option B at fix time.
        //
        // SCOPE: extract a snap-block sub-region from the brace-walked
        // physicsBody. Per Plan 12.7-18 <action> Step 3, the sub-region
        // runs from the `Snap to tile-top` comment marker to the `_player_y`
        // final assignment line. This sub-region scoping is CRITICAL — the
        // literal `2u` appears elsewhere in physicsBody (horizontal probe
        // offsets `player_real_y + 2u`, head probe `player_real_x + 2u`,
        // etc.). A physicsBody-scoped `.contains("2u")` would false-PASS
        // on the broken emission.
        //
        // PRECEDENCE GUARD: Round-4's <<-final-shift was the algebraic
        // anchor; Round-5 retains that guard (assertion R5c) to prevent a
        // regression where W13's fix accidentally drops the final scale-
        // back-up shift to sub-pixel coordinates.
        // =====================================================================

        // Extract the snap-block sub-region. Anchor on the `Snap to tile-top`
        // comment marker (column-stable, unique to this block) and the first
        // post-snap `_player_y` assignment terminator. Per Plan 12.7-18 Step 3
        // option (a). The sub-region is INCLUSIVE on both ends: the marker
        // line at the top, the `_player_y = ... << 4u;` assignment at the
        // bottom. Use indexOf for the start and the first `\n` AFTER the
        // post-snap `_player_y =` for the end.
        val snapMarker = "Snap to tile-top"
        val snapStart = physicsBody.indexOf(snapMarker)
        assertTrue(
            snapStart >= 0,
            "Could not locate snap-block marker `$snapMarker` in physicsBody. " +
                "The W12 Round-5 RED assertion REQUIRES the snap-block sub-region " +
                "scoping per Plan 12.7-18 <action> Step 3 — a physicsBody-scoped " +
                "`.contains(\"2u\")` would false-PASS on the broken emission because " +
                "horizontal/head probe offsets emit `+ 2u` and `- 2u` outside the " +
                "snap block. body:\n${physicsBody.take(4000)}",
        )
        // End of snap block = first `_player_y = ` assignment STRICTLY AFTER
        // snapStart, followed by its line-terminating `;` + newline. This
        // strictly bounds the slice to the snap region. The `_player_y +=`
        // integrate line later in physicsBody must NOT be included.
        val assignToken = "_player_y = "
        val assignAt = physicsBody.indexOf(assignToken, startIndex = snapStart)
        assertTrue(
            assignAt > snapStart,
            "Could not locate post-snap `_player_y = ` assignment in physicsBody. " +
                "Snap block extraction requires the bounded sub-region per " +
                "Plan 12.7-18 Step 3. body:\n${physicsBody.take(4000)}",
        )
        // Include the assignment statement up to and including its line break.
        val assignLineEnd = physicsBody.indexOf('\n', startIndex = assignAt)
        val snapBlock =
            if (assignLineEnd > assignAt) {
                physicsBody.substring(snapStart, assignLineEnd)
            } else {
                physicsBody.substring(snapStart, assignAt + assignToken.length)
            }

        // Persist the snap-block sub-region for RED-run review.
        File(EVIDENCE_DIR, "platformer_physics_update_snap_block.c").writeText(snapBlock)

        // ASSERTION R5a — pivot_adjust correction NAME present.
        // Either `pivot_adjust` (Option A) or `height_plus_pivot` (Option B).
        // Both names are NEW in the W13 GREEN emission and absent at this
        // commit, so this assertion FAILS RED at 12.7-18.
        assertTrue(
            snapBlock.contains("pivot_adjust") || snapBlock.contains("height_plus_pivot"),
            "snap-block must contain `pivot_adjust` (Option A) OR `height_plus_pivot` " +
                "(Option B) — Round-5 H1 fix (Plan 12.7-18 RED → Plan 12.7-19 GREEN). " +
                "Plan 12.7-17 Round-5 diagnostic Section 2 numeric trace: the rendered " +
                "metasprite-bottom under SPRITES_8x16 + pivot(12, 6) + frameSize(24, 32) + " +
                "hitbox 8×24 lands 2 px BELOW the hitbox-foot snap target. The W13 fix " +
                "extends buildVerticalFootProbe to emit a 5th CVarDecl named " +
                "`pivot_adjust` (or fold it into a `height_plus_pivot` symbol). " +
                "Diagnostic Section 5 cites both Options A and B as equivalent fix " +
                "shapes — this assertion accepts EITHER. " +
                "snapBlock:\n$snapBlock",
        )

        // ASSERTION R5b — pivot_adjust VALUE present.
        // For the platformer-template geometry: pivot_adjust = 2 (Option A) OR
        // height_plus_pivot = 26 (Option B, = height(24) + pivot_adjust(2)).
        // Either literal must appear inside the snap-block sub-region.
        // The current Plan 12.7-11 emission's snap block has neither `2u` nor
        // `26u` (literals present in snap block: `24u`, `3u`, `4u`). The
        // sub-region scoping defeats false-PASS from `+ 2u` probe offsets
        // OUTSIDE the snap block.
        assertTrue(
            snapBlock.contains("2u") || snapBlock.contains("26u"),
            "snap-block must contain the literal `2u` (Option A: pivot_adjust = 2u) " +
                "OR `26u` (Option B: height_plus_pivot = 26u = height + pivot_adjust = " +
                "24 + 2) — Round-5 H1 fix (Plan 12.7-18 RED → Plan 12.7-19 GREEN). " +
                "The 2-px constant equals `frameSize.height − pivotY − hitbox.height " +
                "= 32 − 6 − 24` for the platformer-template (Plan 12.7-17 diagnostic " +
                "Section 2). Sub-region scoping (snapStart=$snapStart, " +
                "assignAt=$assignAt) is CRITICAL — the `2u` literal otherwise appears " +
                "in horizontal/head probe offsets outside the snap block. " +
                "snapBlock:\n$snapBlock",
        )

        // ASSERTION R5c — final shift-back-up to sub-pixel scale must
        // remain. The snap-block must terminate in a `_player_y = ... << ...`
        // assignment to scale back to fixed-point (the 1/16 sub-pixel
        // coordinate space). This is a Round-4 guard preserved into Round-5
        // — prevents a regression where W13's fix accidentally drops the
        // final `<< 4` shift while extending the formula. GREEN today,
        // GREEN under W13.
        assertTrue(
            snapBlock.contains("<<"),
            "snap-block must terminate in a `<<` shift-back-up to sub-pixel " +
                "scale (post-Round-4 guard preserved into Round-5). Plan 12.7-11's " +
                "intermediate-vars form ends with `_player_y = foot_pixel_anchor << 4u;` " +
                "— the W13 GREEN fix must preserve this final shift. " +
                "snapBlock:\n$snapBlock",
        )
    }
}
