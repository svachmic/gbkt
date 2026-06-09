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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// D-16 INVARIANT #2 — is_tile_solid() HOME-bank NONBANKED helper shape lock
//
// Plan 12-08 emits a HOME-bank NONBANKED helper `UINT8 is_tile_solid(UINT16
// world_x, UINT16 world_y)` wrapped in a SWITCH_ROM(_current_area_bank) /
// SWITCH_ROM(_previous_bank) save-and-restore pair. Plan 12-11 will call it
// from the 5-point AABB probe inside PlatformerVisitor.
//
// VALIDATION.md §Per-Anchor Verification Map row 2 binds the awk pattern:
//
//   awk '/^UINT8 is_tile_solid/{p=1;d=0} p{d+=gsub(/{/,"");d-=gsub(/}/,"");
//        if(d<0)exit} p' main.c | grep -c 'SWITCH_ROM'  # expect 2
//
// CLAUDE.md §"Scope-level grep gates" forbids a file-level `mainC.contains(...)`
// here because SWITCH_ROM appears in unrelated functions (e.g.
// `_bkg_tiles_load_banked`). The brace-walk extracts the is_tile_solid body
// so substring checks fire ONLY against tokens inside the helper.
//
// Both tests are deliberately structural — they lock the emission SHAPE, not
// behaviour at runtime. Runtime evidence for tilemap collision (anchor 2) is
// the paired UAT screenshot under evidence/uat-screenshots/, captured later
// in the phase by the UAT plans. This JVM tier guarantees the codegen
// prerequisite; the visual tier confirms the helper actually runs.
// =============================================================================

class TilemapCollisionEmissionTest {

    companion object {
        /**
         * Evidence is written under the **active checkout root** (worktree-safe).
         *
         * `user.dir` resolves to the Gradle project's working directory, which for the
         * `:gbkt-genre-platformer:test` task is `<repo>/gbkt-genre-platformer`. From there we
         * ascend one level (`..`) to reach the repo (or worktree) root, then descend into the phase
         * evidence directory. Hard-coding an absolute path would silently route evidence files
         * outside the active worktree and miss the commit (#3099 worktree path safety).
         */
        val EVIDENCE_DIR =
            File(System.getProperty("user.dir"))
                .resolve(
                    "../.planning/phases/12-port-platformer-template-gbdk-example-to-gbkt/evidence/tier1-shape"
                )
                .normalize()
    }

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts a C function body by brace-walking from the first line whose contents start with
     * [functionSignaturePrefix] (e.g. `UINT8 is_tile_solid`) until the matching closing brace at
     * depth zero.
     *
     * The returned blob includes the signature line and the closing brace, so downstream
     * `.contains()` checks operate ONLY on tokens that live inside the named function — never on
     * tokens from unrelated functions in the same file (per CLAUDE.md §"Scope-level grep gates").
     *
     * This is the Kotlin-side mirror of the awk pattern documented in VALIDATION.md row 2:
     * ```
     * awk '/^UINT8 is_tile_solid/{p=1;d=0} p{d+=gsub(/{/,""); d-=gsub(/}/,""); if(d<0)exit} p'
     * ```
     *
     * Matching is anchored to the START of a line (the prefix must appear at column 0) so
     * occurrences inside string literals, comments, or argument lists of a different function
     * cannot false-match. This is the literal counterpart of awk's `/^prefix/` anchor.
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
     * When [solidThreshold] is non-null, this triggers Path A of
     * `GBDKPipeline.gameUsesTilemapCollision`, which causes the pipeline to emit the is_tile_solid
     * helper + 5 HOME-bank globals + header prototype.
     *
     * When [solidThreshold] is null (default), the gate stays OFF and the helper / globals /
     * prototype are NOT emitted — the negative test exercises this branch.
     */
    private fun buildPlatformerGameIR(solidThreshold: Int?, id: String = "plat"): GameIR {
        val config =
            PlatformerPhysicsConfig(
                gravity = 2,
                jumpForce = 8,
                terminalVelocity = 12,
                solidThreshold = solidThreshold,
            )
        val system =
            GenericSystem(
                id = id,
                config = mapOf("type" to "platformer_physics", "physicsConfig" to config),
            )
        return GameIR(
            name = "TestTilemapCollisionGame",
            config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2),
            scenes = listOf(SceneIR(id = "gameplay")),
            systems = listOf(system),
            startScene = "gameplay",
        )
    }

    // -------------------------------------------------------------------------
    // POSITIVE — is_tile_solid is emitted with the SWITCH_ROM save/restore wrapper
    //
    // Production mechanism (Plan 12-08 — GBDKPipeline.buildIsTileSolidHelperIfNeeded,
    // GBDKPipeline.kt:2057+): when `gameUsesTilemapCollision(gameIR) == true`, the
    // pipeline emits the helper as a raw section beginning with the literal text
    // `UINT8 is_tile_solid(UINT16 world_x, UINT16 world_y) NONBANKED {`. The body
    // contains the canonical SWITCH_ROM save/restore wrapper (entry:
    // `SWITCH_ROM(_current_area_bank)`; exit: `SWITCH_ROM(_previous_bank)`) and
    // accesses `_current_level_map`, `_current_level_width_in_tiles`,
    // `_current_level_height`, and `_current_level_non_solid_tile_count`.
    //
    // Scope-level grep gate (CLAUDE.md §"Scope-level grep gates" corollary): a
    // file-level `mainC.contains("SWITCH_ROM")` would false-positive on the
    // unrelated `_bkg_tiles_load_banked` helper (Plan 07.4-30 wrapper). The
    // brace-walk extracts the is_tile_solid body so the substring checks fire
    // ONLY against tokens inside the helper.
    // -------------------------------------------------------------------------

    @Test
    fun `is_tile_solid helper emits SWITCH_ROM save and restore wrapper when solidThreshold set`() {
        val gameIR = buildPlatformerGameIR(solidThreshold = 17)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Signature anchor — function declaration starts at column 0 of main.c with the literal
        // text `UINT8 is_tile_solid` per Plan 12-08's emission contract. This is the awk
        // `/^UINT8 is_tile_solid/` anchor expressed in Kotlin.
        val signatureRegex = Regex("^UINT8 is_tile_solid", RegexOption.MULTILINE)
        val signatureFound = signatureRegex.containsMatchIn(mainC)

        // Evidence-before-assert: extract and persist the helper body BEFORE any assertion fires
        // so a RED run still produces a reviewable artifact on disk (per
        // gbkt-examples/banks's INV-1..4 evidence-before-assert pattern).
        val helperBody = extractFunctionBody(mainC, "UINT8 is_tile_solid")
        File(EVIDENCE_DIR, "is_tile_solid.c").writeText(helperBody)

        assertTrue(
            signatureFound,
            "is_tile_solid declaration must start with 'UINT8 is_tile_solid' at column 0 of " +
                "main.c (Plan 12-08 awk-brace-walk extraction contract). main.c head:\n" +
                mainC.take(2000),
        )
        assertTrue(
            helperBody.isNotEmpty(),
            "is_tile_solid helper body must be extractable via brace-walk from main.c. " +
                "main.c head:\n${mainC.take(2000)}",
        )

        // SWITCH_ROM count — exactly 2 per VALIDATION.md row 2 (entry + exit). Counting via
        // windowed split because Kotlin's String.split does not return the number of
        // separator occurrences directly. `(N-1) split parts` <=> `N - 1` occurrences.
        val switchRomCount = helperBody.split("SWITCH_ROM").size - 1
        assertTrue(
            switchRomCount >= 2,
            "is_tile_solid body must contain at least 2 SWITCH_ROM invocations (entry + exit). " +
                "Found $switchRomCount. helper body:\n${helperBody.take(4000)}",
        )

        // Entry / exit symmetry — verify the wrapper pair, not just two arbitrary occurrences.
        // The entry switches to the area's tilemap bank; the exit restores the caller's bank.
        // Asserting both literal call shapes guards against a regression that, e.g., loses the
        // `_previous_bank` save+restore and leaves the caller in the wrong bank context (the
        // SWITCH_ROM cross-bank-call hazard documented in Plan 07.4-30).
        assertTrue(
            helperBody.contains("SWITCH_ROM(_current_area_bank)"),
            "is_tile_solid body must contain SWITCH_ROM(_current_area_bank) at entry " +
                "(switch to the tilemap data bank). helper body:\n${helperBody.take(4000)}",
        )
        assertTrue(
            helperBody.contains("SWITCH_ROM(_previous_bank)"),
            "is_tile_solid body must contain SWITCH_ROM(_previous_bank) at exit " +
                "(restore the caller's bank). helper body:\n${helperBody.take(4000)}",
        )

        // Non-solid tile threshold — VALIDATION.md row 2 also locks the in-scope presence of
        // `_current_level_non_solid_tile_count`. This is the variable the helper compares
        // against to decide solid-vs-passable (tiles < threshold are passable). Without this
        // symbol the helper cannot return the correct verdict; with it, codegen drift that
        // accidentally renames or removes the comparison fails RED.
        assertTrue(
            helperBody.contains("_current_level_non_solid_tile_count"),
            "is_tile_solid body must reference _current_level_non_solid_tile_count for the " +
                "passable-vs-solid comparison (Plan 12-08 contract). helper body:\n" +
                helperBody.take(4000),
        )

        // Tilemap array access — locks the canonical lookup form `_current_level_map[...]`.
        // A regression that switches to a different indirection (e.g. pointer arithmetic, or
        // a renamed array) breaks the Plan 12-11 caller contract and fails here.
        assertTrue(
            helperBody.contains("_current_level_map["),
            "is_tile_solid body must read from _current_level_map[index] " +
                "(tilemap byte lookup). helper body:\n${helperBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // NEGATIVE — gate verification. When solidThreshold is unset, no emission.
    //
    // Production mechanism (Plan 12-08 — gameUsesTilemapCollision returns FALSE):
    // both Path A (system-level physicsConfig.solidThreshold non-null) and Path B
    // (zone-level platformerPhysicsOverride[solidThreshold] present) report
    // false. The pipeline emits ZERO references to `is_tile_solid` and ZERO
    // tilemap-collision globals — existing examples (Pong, Breakout, Banks)
    // remain byte-identical at the codegen layer.
    //
    // This sentinel locks the opt-IN nature of the feature. A regression that
    // accidentally fires the gate unconditionally (e.g. dropping the predicate
    // check) would break the byte-identical regression invariant for the 7
    // framework-validated example games.
    // -------------------------------------------------------------------------

    @Test
    fun `is_tile_solid is NOT emitted when solidThreshold is unset (gate off)`() {
        val gameIR = buildPlatformerGameIR(solidThreshold = null)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // No function definition / declaration text anywhere in main.c.
        assertFalse(
            mainC.contains("is_tile_solid"),
            "is_tile_solid must NOT appear in main.c when solidThreshold is unset " +
                "(gate-off byte-identical regression invariant). main.c head:\n" +
                mainC.take(2000),
        )

        // No HOME-bank tilemap-collision globals either — the helper and its 5 supporting
        // globals are gated as a unit (see Plan 12-08 SUMMARY §"single shared gate"). If
        // _current_area_bank leaked through with the helper absent, the prototype in game.h
        // would be unresolved at link time. Locking the globals' absence locks the lockstep.
        assertFalse(
            mainC.contains("_current_area_bank"),
            "_current_area_bank must NOT appear in main.c when solidThreshold is unset " +
                "(HOME-bank globals are gated in lockstep with the helper). " +
                "main.c head:\n${mainC.take(2000)}",
        )

        // Also confirm the header prototype is not leaked. game.h is the other half of the
        // lockstep emission contract (Plan 12-08 §"Single shared gate"); if it carries the
        // prototype without a matching definition, banked scene callers (Plan 12-11) would
        // see `undefined identifier 'is_tile_solid'` at lcc link time.
        val gameH = output.files["game.h"] ?: error("game.h not generated")
        assertFalse(
            gameH.contains("is_tile_solid"),
            "is_tile_solid prototype must NOT appear in game.h when solidThreshold is unset. " +
                "game.h head:\n${gameH.take(2000)}",
        )
    }

    // -------------------------------------------------------------------------
    // Phase 12.9 D3 — horizontal probe uses halfW + 1 (player_real_x ± 5u)
    //
    // Root cause (Plan 12.9-08e Diagnose): PlatformerVisitor.buildHorizontalProbe
    // (~line 1276) uses CLiteral(halfW) where halfW = hitbox.width/2 = 8/2 = 4.
    // Reference PLAYER_CHARACTER_BOUNDING_BOX_HALF_WIDTH = 5. The 1px deficit
    // allows a 1px overlap before blocking → player walks through edges of blocks.
    //
    // Fix: CLiteral(halfW) → CLiteral(halfW + 1) in buildHorizontalProbe.
    //
    // Fixture: buildPlatformerGameIR(solidThreshold = 17) with no explicit actor
    // hitbox → falls back to the visitor's default hitbox(0, 0, 8, 24). halfW = 4,
    // halfW + 1 = 5. The horizontal probes in main.c must use `player_real_x + 5u`
    // and `player_real_x - 5u` (both right and left probes).
    //
    // Scope-level grep gate: extracts the platformer_physics_update body via
    // brace-walk before asserting (CLAUDE.md § Scope-level grep gates).
    // -------------------------------------------------------------------------

    @Test
    fun `horizontal probes use halfW plus 1 (player_real_x plus 5u) (Phase 12_9 D3)`() {
        val gameIR = buildPlatformerGameIR(solidThreshold = 17)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Brace-walk extraction — lock assertions inside platformer_physics_update scope.
        // The function signature prefix matches both NONBANKED and BANKED variants.
        val updateBody = extractFunctionBody(mainC, "void platformer_physics_update")
        File(EVIDENCE_DIR, "platformer_physics_update_d3.c").writeText(updateBody)

        assertTrue(
            updateBody.isNotEmpty(),
            "platformer_physics_update body must be extractable via brace-walk from main.c. " +
                "main.c head:\n${mainC.take(2000)}",
        )

        // D3 positive — right horizontal probe: player_real_x + 5u (halfW + 1 = 5).
        // RED with CLiteral(halfW=4): emits `player_real_x + 4u`. GREEN after fix.
        assertTrue(
            updateBody.contains("player_real_x + 5u"),
            "platformer_physics_update body must contain horizontal probe `player_real_x + 5u` " +
                "(Phase 12.9 D3: CLiteral(halfW+1) — halfW=4 → probe=5; 1px give prevents " +
                "walk-through at block edges). updateBody:\n${updateBody.take(4000)}",
        )

        // D3 positive — left horizontal probe: player_real_x - 5u.
        assertTrue(
            updateBody.contains("player_real_x - 5u"),
            "platformer_physics_update body must contain horizontal probe `player_real_x - 5u` " +
                "(Phase 12.9 D3: left-probe mirrors right-probe; both use halfW+1=5). " +
                "updateBody:\n${updateBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // Phase 12.9 WR-02 — foot/head probes use halfW - 2 where halfW = 5 (→ 3u)
    //
    // Root cause (WR-02): the D3 fix added `CLiteral(halfW + 1)` at ONE call site
    // (buildHorizontalProbe ~line 1279) while the base `halfW = hitbox.width/2 = 4`
    // was left uncorrected. The foot/head probes use `halfWMinus2 = halfW - 2 = 2`
    // — but the reference derives all probes from HALF_WIDTH=5, making foot/head
    // inset `5 - 2 = 3`, not `2`. The 1px deficit makes foot/head probes 1px
    // narrower than the reference, causing corner-snag edge cases.
    //
    // Fix (WR-02): correct the ROOT cause by setting
    //   halfW = hitbox.width / 2 + 1  (= 5 for width 8)
    // and reverting buildHorizontalProbe's CLiteral(halfW + 1) back to CLiteral(halfW)
    // so the horizontal probes still emit 5u (unchanged from G3-approved behavior).
    // halfWMinus2 = halfW - 2 = 3 automatically, so foot/head probes emit 3u.
    //
    // Assertions:
    //   (1) horizontal probes still use 5u (G3-approved — must not regress)
    //   (2) foot/head probes now use 3u (was 2u — the WR-02 fix)
    //   (3) no residual 2u probes from halfWMinus2 (all probes updated consistently)
    //   (4) no 4u probes (old halfW — fully purged)
    //
    // Scope-level grep gate: all assertions fire against the brace-walked
    // platformer_physics_update body (CLAUDE.md § Scope-level grep gates).
    //
    // RED: assertions (2)+(3) fail with the current halfW=4 / halfWMinus2=2 base.
    // GREEN: after setting halfW = hitbox.width/2 + 1 and reverting CLiteral.
    // -------------------------------------------------------------------------

    @Test
    fun `foot-head probes use halfW minus 2 where halfW=5 (player_real_x pm 3u) (Phase 12_9 WR-02)`() {
        val gameIR = buildPlatformerGameIR(solidThreshold = 17)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()

        val updateBody = extractFunctionBody(mainC, "void platformer_physics_update")
        File(EVIDENCE_DIR, "platformer_physics_update_wr02.c").writeText(updateBody)

        assertTrue(
            updateBody.isNotEmpty(),
            "platformer_physics_update body must be extractable via brace-walk. " +
                "main.c head:\n${mainC.take(2000)}",
        )

        // WR-02 (1): horizontal probes still use 5u — G3-approved behavior PRESERVED.
        // This is a regression guard: fixing the halfW base must not break the G3-approved output.
        assertTrue(
            updateBody.contains("player_real_x + 5u"),
            "WR-02 regression guard: horizontal probe `player_real_x + 5u` must still be present " +
                "(G3-approved behavior — halfW=5 base + CLiteral(halfW) = 5u for horizontal). " +
                "updateBody:\n${updateBody.take(4000)}",
        )

        // WR-02 (2): foot/head probes now use 3u (halfW=5, halfWMinus2=5-2=3).
        // RED: current code emits 2u (halfW=4, halfWMinus2=2). GREEN after fix.
        assertTrue(
            updateBody.contains("player_real_x + 3u"),
            "WR-02: foot/head probes must use `player_real_x + 3u` " +
                "(halfW=5, halfWMinus2=5-2=3 — reference HALF_WIDTH=5 makes inset 5-2=3). " +
                "updateBody:\n${updateBody.take(4000)}",
        )
        assertTrue(
            updateBody.contains("player_real_x - 3u"),
            "WR-02: foot/head probes must use `player_real_x - 3u` " +
                "(symmetric with + 3u; both foot corners and both head corners). " +
                "updateBody:\n${updateBody.take(4000)}",
        )

        // WR-02 (3): no residual 2u X-offsets (old halfWMinus2 = halfW - 2 = 4 - 2 = 2).
        // After the halfW base fix, halfWMinus2 = 5 - 2 = 3 — no 2u probes remain.
        assertFalse(
            updateBody.contains("player_real_x + 2u") || updateBody.contains("player_real_x - 2u"),
            "WR-02: no `player_real_x ± 2u` probes must remain (old halfWMinus2=2 purged; " +
                "new halfWMinus2=3 replaces all foot/head insets). " +
                "updateBody:\n${updateBody.take(4000)}",
        )

        // WR-02 (4): no 4u probes (old halfW without the + 1 patch).
        // CLiteral(halfW + 1) in buildHorizontalProbe reverts to CLiteral(halfW) = 5; the old
        // CLiteral(halfW=4) emission is fully purged.
        assertFalse(
            updateBody.contains("player_real_x + 4u") || updateBody.contains("player_real_x - 4u"),
            "WR-02: no `player_real_x ± 4u` probes must remain (old halfW=4 purged; " +
                "CLiteral(halfW+1) site now reads CLiteral(halfW=5)=5u). " +
                "updateBody:\n${updateBody.take(4000)}",
        )
    }
}
