/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.dsl.SceneRef
import io.github.gbkt.core.dsl.asset
import io.github.gbkt.core.dsl.buttons
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.dsl.zone
import io.github.gbkt.genre.platformer.dsl.platformerPhysics
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// D-16 INVARIANT #5 — main() level-switch guard + setup_current_level helper
//
// Plan 12-09b locks invariant #5 emission at the JVM tier via per-function awk
// brace-walk extraction. Anchor 5 currently has ONLY emulator-runtime UAT
// (Plan 12-22 / 12-23); per CLAUDE.md §"Scope-level grep gates corollary",
// emulator-runtime assertions cannot independently catch a future codegen
// regression — they are downstream of the generated C shape. This test locks
// the shape one level below the visual outcome.
//
// What anchor 5 means (per 12-VALIDATION row 5 + 12-CONTEXT D-08):
//
//   - main()'s game loop emits a level-switch guard that fires when the player
//     reaches level-end and PlatformerVisitor.kt:802 increments _next_level
//     past _current_level. The guard navigates to the NextLevel card scene
//     (Plan 12-17 Task 2) and calls setup_current_level() to sync the per-
//     level metadata for the next frame's dispatch.
//
//   - setup_current_level() is a HOME-bank NONBANKED function declared in
//     main.c. It assigns _current_level = _next_level then dispatches per-zone
//     metadata via `switch (_current_level % N)` (Plan 12-17 Task 2 stub
//     populated by Plan 12-18 buildRom).
//
// The substring contract Plan 12-17 SUMMARY §Self-Check documents:
//   - `if (_next_level != _current_level)`          (1× — main-loop guard)
//   - `navigate_to_scene(SCENE_NEXTLEVEL)`          (2× — guard + dispatch switch)
//   - `setup_current_level()`                       (1× — guard call site)
//   - `void setup_current_level(void) NONBANKED`    (1× — function definition)
//   - `_current_level = _next_level`                (1× — first body statement)
//   - `switch (_current_level`                      (1× — setup_current_level switch)
//
// Scope-level grep gate (CLAUDE.md §"Scope-level grep gates" corollary): each
// substring assertion fires against a brace-walked function body — a file-
// level `mainC.contains("_next_level")` would false-positive on the helper's
// own first-statement `_current_level = _next_level` and on any pipeline-level
// extern declaration. The brace-walk pattern below is the Kotlin counterpart
// of awk's `/^void main/` anchor + `gsub(/{/, ...)` depth counter.
//
// Double-gate (per GBDKPipeline.buildMainLoopLevelSwitchGuardIfNeeded):
// emission requires gameUsesTilemapCollision(gameIR) == true AND presence of a
// scene id matching {"nextLevel", "next_level"}. Locking the negative gate
// (Test 3) proves that games failing EITHER half stay byte-identical.
// =============================================================================

class LevelSwitchEmissionTest {

    companion object {
        /**
         * Evidence is written under the **active checkout root** (worktree-safe).
         *
         * Same pattern as TitleSceneEmissionTest (sibling pipeline test). `user.dir` resolves to
         * the `:gbkt-backend-gbdk:test` task's working directory; we ascend one level to the repo
         * (or worktree) root, then descend into the phase evidence directory. Hard-coding an
         * absolute path would silently route evidence files outside the active worktree and miss
         * the commit (#3099 worktree path safety).
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
    // Helpers — brace-walk extraction (awk-equivalent)
    //
    // Copy verbatim from TitleSceneEmissionTest (Plan 12-09b Task 2 explicit
    // instruction); duplication is intentional per the per-test EVIDENCE_DIR +
    // helper pattern established by BanksEmissionTest + TilemapCollisionEmissionTest.
    // -------------------------------------------------------------------------

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
     * Build a minimal tilemap-collision game with 2 gameplay zones + 1 nextLevel scene + 1 title
     * scene + 1 gameplay scene. This is the canonical fixture for invariant #5 — both gates fire:
     *
     * - gameUsesTilemapCollision == true via `platformerPhysics { solidThreshold(17) }` (Path A).
     * - nextLevel scene id present, so `buildMainLoopLevelSwitchGuardIfNeeded` does NOT early-
     *   return at the second gate.
     *
     * Two gameplay zones are required for the `switch (_current_level % N)` body to have at least
     * two case branches (Plan 12-17 Task 2 dispatches per-zone metadata). Menu-screen zones
     * (titleZone, nextLevelZone) are filtered by the id-name heuristic in
     * `buildSetupCurrentLevelFunctionIfNeeded` so they do NOT appear as case branches; they DO
     * still get a bank allocation for the title/nextLevel scenes' zone-binders.
     */
    private fun buildTilemapCollisionGameDsl() =
        game("LevelSwitchEmissionTest") {
                platformerPhysics { solidThreshold(17) }

                val titleZone by zone { tileset(asset("res/graphics/title-screen.png")) }
                val gameplayZone1 by zone { tileset(asset("res/graphics/level1.png")) }
                val gameplayZone2 by zone { tileset(asset("res/graphics/level2.png")) }
                val nextLevelZone by zone { tileset(asset("res/graphics/next-level.png")) }

                val titleScene =
                    scene("title") {
                        zone(titleZone)
                        enter { cEmit("fill_bkg_rect(0u, 0u, 20u, 18u, 0u);") }
                        frame { whenever(buttons.start.pressed) { navigate(SceneRef("gameplay")) } }
                    }
                scene("gameplay") {
                    zone(gameplayZone1)
                    frame { whenever(buttons.start.pressed) { navigate(SceneRef("nextLevel")) } }
                }
                // 2nd gameplay zone surfaces in the setup_current_level switch as case 1.
                // It is bound to a zone-only scene (no enter / no frame) per the
                // SceneVisitor convention — actually, SceneVisitor emits an enter when
                // `enterOps.isNotEmpty() || zoneRefs.isNotEmpty()`, so a frame-only scene
                // would skip the enter. Bind the 2nd gameplay zone via the nextLevel scene
                // instead so the switch dispatch has 2 cases without inflating the scene count.
                scene("nextLevel") {
                    zone(nextLevelZone)
                    enter { cEmit("fill_bkg_rect(0u, 0u, 20u, 18u, 0u);") }
                    frame { whenever(buttons.start.pressed) { navigate(SceneRef("gameplay")) } }
                }
                // Hidden scene binding the 2nd gameplay zone so it shows up in gameIR.zones.
                // The setup_current_level switch dispatches on (_current_level % zoneCount) so
                // ≥2 cases proves the dispatch table is non-degenerate.
                scene("gameplay2") {
                    zone(gameplayZone2)
                    frame { whenever(buttons.start.pressed) { navigate(SceneRef("gameplay")) } }
                }
                start = titleScene
            }
            .build()

    // -------------------------------------------------------------------------
    // POSITIVE — main() emits the level-switch guard (D-16 #5)
    //
    // Production mechanism (GBDKPipeline.kt:2338-2359 —
    // buildMainLoopLevelSwitchGuardIfNeeded; spliced into buildMainFunction at
    // kt:4274): when both gates fire, the function returns the 2-statement
    // guard list `[CComment, CIf(...)]` which buildMainFunction splices into
    // the main()-loop body AFTER frame dispatch + puzzle/NPC updates BEFORE
    // sprite sync (Plan 12-17 SUMMARY §Decisions: guard splice ordering).
    //
    // Anchor 5 link (12-VALIDATION row 5): this is the SHAPE Plans 12-22/12-23
    // (MCP UAT) consume — the emulator probe steps to level-end, asserts the
    // NextLevel card scene activates, then asserts gameplay resumes with the
    // 2nd zone's tilemap visible. Without the guard emission the runtime probe
    // would fail at the scene-transition step. JVM tier locks the codegen
    // prerequisite; visual tier confirms it actually fires.
    //
    // Scope-level grep gate: file-level `mainC.contains("_next_level")` would
    // false-positive on (a) the extern declaration in main.c's globals section,
    // (b) the helper's own first statement `_current_level = _next_level`, and
    // (c) PlatformerVisitor's `_next_level++` increment that lands in BANKED
    // scene code (which lives in bank1.c, but trampolines into main.c could
    // surface the symbol). The brace-walked main() body is the locking scope.
    // -------------------------------------------------------------------------

    @Test
    fun `main emits levelSwitch guard when tilemap collision + nextLevel scene present`() {
        val gameIR = buildTilemapCollisionGameDsl()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Signature anchor — main() definition starts with `void main(void)` at column 0 of
        // main.c (CEmitter.emitFunction emits `void main(void) {` when params is empty —
        // CEmitter.kt:184-199). This is the awk `/^void main/` anchor expressed in Kotlin.
        val signatureRegex = Regex("^void main\\(void\\)", RegexOption.MULTILINE)
        assertTrue(
            signatureRegex.containsMatchIn(mainC),
            "main() definition must start with 'void main(void)' at column 0 of main.c " +
                "(GBDKPipeline buildMainFunction emits CFunction(name=\"main\", params=[]) → " +
                "CEmitter renders the empty-params form as `void main(void)`). " +
                "main.c head:\n${mainC.take(2000)}",
        )

        // Brace-walk extraction — locks all subsequent substring checks inside main()'s scope.
        val mainBody = extractFunctionBody(mainC, "void main(void)")
        File(EVIDENCE_DIR, "main_levelSwitch.c").writeText(mainBody)

        assertTrue(
            mainBody.isNotEmpty(),
            "main() body must be extractable via brace-walk from main.c " +
                "(Plan 12-09b awk-equivalent helper). main.c head:\n${mainC.take(2000)}",
        )

        // Guard predicate — Plan 12-17 SUMMARY §provides documents this exact substring as the
        // distinctive shape of the main-loop guard. The CBinaryExpr(CVar("_next_level"), "!=",
        // CVar("_current_level")) lowers via CEmitter to `_next_level != _current_level`
        // (without surrounding parens because emitBinaryExpr does not add them for top-level
        // condition exprs; the wrapping `if (...)` parens come from emitIf).
        assertTrue(
            mainBody.contains("_next_level != _current_level"),
            "main() body must contain `_next_level != _current_level` (the level-switch " +
                "guard's distinctive condition shape, per Plan 12-17 SUMMARY §Self-Check). " +
                "main() body:\n${mainBody.take(4000)}",
        )

        // Guard body call 1 — navigate to the NextLevel card scene. SCENE_NEXTLEVEL is the
        // CVar emitted by buildMainLoopLevelSwitchGuardIfNeeded at kt:2353:
        // `CCall("navigate_to_scene", listOf(CVar(sceneEnumConstant)))` where
        // sceneEnumConstant = "SCENE_${nextLevelSceneId.uppercase()}" → "SCENE_NEXTLEVEL"
        // for the nextLevel scene id used by this fixture's DSL.
        assertTrue(
            mainBody.contains("navigate_to_scene(SCENE_NEXTLEVEL)"),
            "main() body must contain `navigate_to_scene(SCENE_NEXTLEVEL)` " +
                "(guard body call 1 — show the NextLevel card scene). " +
                "main() body:\n${mainBody.take(4000)}",
        )

        // Phase 12.6 D-04 trim — INVERTED REGRESSION GUARD: setup_current_level() must NO
        // LONGER appear in main()'s body. The call moved out of the main-loop guard and into
        // the levelCardScene Start-press path (Plan 12.6-04 DSL + 12.6-05 codegen); the new
        // emission site is locked by LevelCardSceneEmissionTest. Prior positive assertion
        // (Plan 12-17 Task 2) is converted, NOT deleted, to lock the inversion at this tier.
        assertFalse(
            mainBody.contains("setup_current_level()"),
            "Phase 12.6 D-04 trim: main() body must NOT contain `setup_current_level()` " +
                "(call moved to levelCardScene Start-press path; see LevelCardSceneEmissionTest). " +
                "main() body:\n${mainBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // POSITIVE — setup_current_level helper function is emitted (D-16 #5 companion)
    //
    // Production mechanism (GBDKPipeline.kt:2239-2294 —
    // buildSetupCurrentLevelFunctionIfNeeded): emits a raw C section beginning
    // with `void setup_current_level(void) NONBANKED {` at column 0 of main.c.
    // Body shape (Plan 12-17 Task 2 stub):
    //
    //   void setup_current_level(void) NONBANKED {
    //       _current_level = _next_level;
    //       switch (_current_level % <gameplayZoneCount>u) {
    //           case 0:  // zone: gameplayZone1
    //               _current_area_bank = BANK(_zone_gameplayZone1_tilemap);
    //               _current_level_map = _zone_gameplayZone1_tilemap;
    //               ...
    //               break;
    //           case 1:  // zone: gameplayZone2
    //               ...
    //               break;
    //           default:
    //               break;
    //       }
    //   }
    //
    // Companion gate: without this helper the level-switch guard body (Test 1)
    // is dead code — the guard's `setup_current_level()` call would resolve to
    // an unresolved-identifier error at SDCC link time. Locking both halves of
    // the pair documents the lockstep emission contract.
    //
    // The NONBANKED keyword is emitted as a literal string in the raw section
    // (Plan 12-17 chose raw emission because the typed C AST has no NONBANKED
    // modifier — same precedent as buildIsTileSolidHelperIfNeeded). Locking
    // the literal substring catches a regression that drops the qualifier.
    // -------------------------------------------------------------------------

    @Test
    fun `setupCurrentLevel helper function definition is emitted at column 0 of main_c`() {
        val gameIR = buildTilemapCollisionGameDsl()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Signature anchor — Plan 12-17 SUMMARY §Self-Check documents the exact substring
        // `void setup_current_level(void) NONBANKED`. The raw section emits this verbatim;
        // multiline regex at column 0 protects against the prototype line in game.h leaking
        // a false-positive (game.h's prototype ends with `;` while main.c's definition opens
        // with `{` — the prefix below stops before either suffix to match both, then the
        // brace-walk uses the column-0 anchor to extract only the definition body).
        val signatureRegex =
            Regex("^void setup_current_level\\(void\\) NONBANKED", RegexOption.MULTILINE)
        assertTrue(
            signatureRegex.containsMatchIn(mainC),
            "main.c must contain `void setup_current_level(void) NONBANKED` at column 0 " +
                "(Plan 12-17 SUMMARY §Self-Check substring contract). " +
                "main.c head:\n${mainC.take(2000)}",
        )

        // Brace-walk extraction — locks all subsequent substring checks inside the helper's scope.
        val helperBody = extractFunctionBody(mainC, "void setup_current_level(void) NONBANKED")
        File(EVIDENCE_DIR, "setup_current_level.c").writeText(helperBody)

        assertTrue(
            helperBody.isNotEmpty(),
            "setup_current_level body must be extractable via brace-walk from main.c. " +
                "main.c head:\n${mainC.take(2000)}",
        )

        // First-statement contract — Plan 12-17 Task 2 docstring at kt:2236 explicitly states
        // "Body MUST assign `_current_level = _next_level` as the first statement". This
        // ordering matters: the switch below dispatches on `_current_level`, so it must be
        // synced from `_next_level` before the dispatch runs.
        assertTrue(
            helperBody.contains("_current_level = _next_level"),
            "setup_current_level body must assign `_current_level = _next_level` " +
                "(first body statement per Plan 12-17 Task 2 contract kt:2236). " +
                "helper body:\n${helperBody.take(4000)}",
        )

        // Switch dispatch — Plan 12-17 Task 2 docstring at kt:2237 explicitly states "Body
        // MUST contain a `switch (_current_level` substring". Each case branch assigns the
        // per-zone tilemap metadata.
        assertTrue(
            helperBody.contains("switch (_current_level"),
            "setup_current_level body must contain `switch (_current_level` (the per-zone " +
                "dispatch per Plan 12-17 Task 2 contract kt:2237). " +
                "helper body:\n${helperBody.take(4000)}",
        )

        // Per-case metadata reference — Plan 12-17 Task 2 documents (kt:2263) that each case
        // body must assign `_current_area_bank = BANK(_zone_<id>_tilemap)`. This is what
        // PlatformerVisitor's tilemap-physics path consumes (the helper populates the bank
        // register that is_tile_solid's SWITCH_ROM(_current_area_bank) entry refers to). A
        // regression that dropped these assignments would leave _current_area_bank at 0,
        // routing is_tile_solid's tilemap reads to the wrong ROM bank → false collision
        // verdicts at runtime.
        assertTrue(
            helperBody.contains("_current_area_bank"),
            "setup_current_level body must update _current_area_bank in each case (Plan 12-17 " +
                "Task 2 per-case body contract — `_current_area_bank = BANK(_zone_<id>_tilemap)` " +
                "per kt:2263). helper body:\n${helperBody.take(4000)}",
        )

        // Case count — fixture declares 2 gameplay zones (gameplayZone1, gameplayZone2). The
        // menu-screen zones (titleZone, nextLevelZone) are filtered by the id-name heuristic
        // in kt:2241-2246. Locking the count at ≥2 proves the filter is permissive enough to
        // keep the gameplay zones (a regression that over-filtered would collapse to 0 → null
        // return → no emission, failing the signature assertion above).
        val caseCount = "case ".toRegex().findAll(helperBody).count()
        assertTrue(
            caseCount >= 2,
            "setup_current_level body must contain ≥2 case branches (fixture has 2 gameplay " +
                "zones; menu-screen zones are filtered out by the id-name heuristic at kt:2241-" +
                "2246). Found $caseCount. helper body:\n${helperBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // POSITIVE — setup_current_level emits LITERAL bank from bankAllocation
    // (Plan 12.1-11 option c-prime — Defect 6 fix lock).
    //
    // Production mechanism (Plan 12.1-11 — GBDKPipeline.kt:2295-2363):
    //   buildSetupCurrentLevelFunctionIfNeeded threads `bankAllocation: Map<String,Int>`
    //   through; for each gameplay-zone case, the per-case body emits
    //   `_current_area_bank = <N>u;` (literal bank from the in-memory map)
    //   INSTEAD OF `_current_area_bank = BANK(_zone_<id>_tilemap);`. The BANK()
    //   macro shape was the original Plan 12-17 emission; it was retired because
    //   SDCC does NOT synthesize `__bank_<sym>` for data arrays under
    //   `#pragma bank N` — only for banked function definitions — leaving the
    //   linker with an unresolved reference. Substituting the literal bank at
    //   the consumer site eliminates the link-time dependency on that never-
    //   existing symbol while PRESERVING Plan 12.1-01's `#pragma bank N` data
    //   placement (ConvertZoneTilesetsTask is untouched).
    //
    // Anchor to Defect 6 evidence:
    //   - 12.1-VERIFICATION.md §"Defect 6" documents the root cause + the
    //     option (b) + option (c-prime) recommended path.
    //   - Plan 12.1-11 SUMMARY documents the production change.
    //   - This test locks the EMISSION SHAPE so a future plan author who
    //     reads the Plan 12-17 docstring (referencing `BANK(...)`) cannot
    //     accidentally revert the fix.
    //
    // Scope-level grep gate (CLAUDE.md §"Scope-level grep gates corollary"):
    //   The literal-bank-shape assertion fires against the brace-walked
    //   `setup_current_level` body, NOT against `main.c` at file scope. A
    //   file-level `mainC.contains("BANK(_zone_")` would false-negative if a
    //   future regression moved the BANK() macro to a different function (e.g.,
    //   a scene-enter callback) — by anchoring inside `setup_current_level`'s
    //   body, the regression guard is scoped to the exact function the fix
    //   touched. The `Regex("_current_area_bank = \\d+u;")` count assertion
    //   then locks the literal-bank PRESENCE per gameplay zone case.
    // -------------------------------------------------------------------------

    @Test
    fun `setupCurrentLevel emits literal bank from bankAllocation (option c-prime)`() {
        val gameIR = buildTilemapCollisionGameDsl()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Brace-walk extraction — locks all assertions inside the helper's scope. The
        // signature anchor is identical to the sibling test above; the brace-walk
        // helper returns the function body as a String for in-scope assertion.
        val helperBody = extractFunctionBody(mainC, "void setup_current_level(void) NONBANKED")
        File(EVIDENCE_DIR, "setup_current_level_literal_bank.c").writeText(helperBody)

        assertTrue(
            helperBody.isNotEmpty(),
            "setup_current_level body must be extractable via brace-walk from main.c; " +
                "main.c head:\n${mainC.take(2000)}",
        )

        // Lock the literal-bank shape (Plan 12.1-11 option c-prime — one
        // `_current_area_bank = <N>u;` assignment per gameplay-zone case branch). The
        // fixture has 2 gameplay zones (gameplayZone1, gameplayZone2 — see
        // buildTilemapCollisionGameDsl), so the literal-bank assignment count MUST be
        // ≥2. A regression that reverts to `BANK(...)` would drop this count to 0.
        val literalBankAssignments =
            Regex("_current_area_bank = \\d+u;").findAll(helperBody).count()
        assertTrue(
            literalBankAssignments >= 2,
            "setup_current_level body must contain ≥2 literal-bank assignments (Plan 12.1-11 " +
                "option c-prime — one per gameplay zone case branch). Found " +
                "$literalBankAssignments. helper body:\n${helperBody.take(4000)}",
        )

        // Forbid the BANK() macro shape (Defect 6 regression guard). option (b)
        // `#pragma bank N` is preserved by ConvertZoneTilesetsTask (untouched), but
        // the consumer-side macro reference is retired by Plan 12.1-11 option
        // c-prime — see 12.1-VERIFICATION.md §"Defect 6" for the SDCC
        // `__bank_<sym>` data-array non-synthesis evidence.
        assertFalse(
            helperBody.contains("BANK(_zone_"),
            "setup_current_level body must NOT contain BANK(_zone_ — option (b) #pragma bank N " +
                "is preserved by ConvertZoneTilesetsTask, but the consumer-side macro reference " +
                "is retired by Plan 12.1-11 option c-prime (see 12.1-VERIFICATION.md §Defect 6 " +
                "for the SDCC __bank_<sym> data-array non-synthesis evidence). " +
                "helper body:\n${helperBody.take(4000)}",
        )

        // Lock the fixture-specific bank-2 expectation. Both gameplay zones in the
        // fixture (gameplayZone1, gameplayZone2) have ~1024 tilemap bytes each (the
        // res/graphics/level{1,2}.png placeholders → small tilemap data), so
        // allocateZoneBanks' FFD bin-packer packs both into bank 2 (2 × 1024 << 16384
        // cap). If the packer changed (e.g., spread one per bank), this assertion
        // would catch the upstream-allocation drift before it ships. The expectation
        // is fixture-tied, not contract-tied — see allocateZoneBanks (kt:611).
        assertTrue(
            helperBody.contains("_current_area_bank = 2u;"),
            "Both gameplay zones in the fixture (~1024 tilemap bytes each) → " +
                "allocateZoneBanks packs them into bank 2 (2×1024 << 16384 cap). The emission " +
                "must reflect this; if allocateZoneBanks changes its packing strategy, this " +
                "fixture-tied assertion will catch the drift. " +
                "helper body:\n${helperBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // DEFENSIVE / EDGE — setup_current_level falls back to bank 1u when the
    // bankAllocation map is missing an entry for a gameplay zone (Plan 12.1-11
    // `?: 1` defensive fallback).
    //
    // Production mechanism (GBDKPipeline.kt:2313-2318):
    //   val bank = bankAllocation[zone.id] ?: 1
    //   val bankFallbackComment = if (bankAllocation[zone.id] == null) {
    //       " /* fallback: bankAllocation missing zoneId; safe HOME-adjacent */"
    //   } else { "" }
    //
    // Test approach: directly invoke `buildSetupCurrentLevelFunctionIfNeeded(
    // gameIR, emptyMap())` to force the fallback path. This requires `internal`
    // visibility on the production function (widening from `private` matches
    // the established convention used by `internal fun allocateZoneBanks` at
    // kt:611 — both serve as same-module test hooks for emission-invariant
    // tests).
    //
    // Why bank 1u? It is HOME-adjacent (bank 1 is always present on every MBC
    // configuration including ROM-only / no-MBC builds), so the fallback never
    // reads from an unmapped bank window. The trade-off: a stale tilemap from
    // bank 1 may render, but the runtime does NOT page-fault or read random
    // memory. The fallback also emits an inline C comment marking the case as
    // "fallback: bankAllocation missing zoneId; safe HOME-adjacent" so a
    // developer inspecting the generated C can spot the issue at code-review
    // time, instead of having the bug silently masked.
    //
    // Locking both the bank-1u literal AND the comment substring proves the
    // defensive path is structurally observable (not silent), which is the
    // documentation footprint Plan 12.1-11 chose for this defect class.
    // -------------------------------------------------------------------------

    @Test
    fun `setupCurrentLevel falls back to bank 1u when bankAllocation entry is missing (defensive)`() {
        val gameIR = buildTilemapCollisionGameDsl()
        // Invoke the production function directly with an EMPTY bankAllocation map.
        // This exercises the `bankAllocation[zone.id] ?: 1` fallback path for every
        // gameplay zone in the fixture (both gameplayZone1 and gameplayZone2 miss
        // the map → both emit `_current_area_bank = 1u;` plus the fallback comment).
        val raw =
            pipeline.buildSetupCurrentLevelFunctionIfNeeded(gameIR, emptyMap())
                ?: error(
                    "setup_current_level must be emitted when gameUsesTilemapCollision is true " +
                        "(the bankAllocation parameter is independent of the emission gate; " +
                        "Plan 12.1-11 ?: 1 fallback path"
                )

        EVIDENCE_DIR.mkdirs()
        File(EVIDENCE_DIR, "setup_current_level_fallback.c").writeText(raw)

        // Lock the fallback shape — `_current_area_bank = 1u;` is the Plan 12.1-11
        // `?: 1` literal. The fixture has 2 gameplay zones → both fall back, so the
        // assertion is a containment check (not a strict count) to remain robust
        // against future fixture growth (e.g., a 3-zone test inheritance).
        assertTrue(
            raw.contains("_current_area_bank = 1u;"),
            "When bankAllocation map is empty (defensive edge case from Plan 12.1-11 ?: 1), " +
                "the emission must fall back to bank-1u (HOME-adjacent, safe). raw:\n${raw.take(4000)}",
        )

        // Lock the fallback documentation footprint — the inline comment is the
        // observability hook Plan 12.1-11 chose. A regression that silently dropped
        // the comment would mask the bug at code-review time, even though the bank-1u
        // literal would still emit. The comment string is a stable, distinctive
        // substring that survives whitespace + line-wrap rearrangements.
        assertTrue(
            raw.contains("fallback: bankAllocation missing zoneId"),
            "Fallback emission must carry the inline comment from Plan 12.1-11 so the bug is " +
                "observable at code-review time, not silently masked. raw:\n${raw.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // NEGATIVE GATE — game WITHOUT tilemap-collision OR WITHOUT a nextLevel
    // scene does NOT emit the level-switch guard or the setup helper.
    //
    // Production mechanism (Plan 12-17 Task 2 — double gate):
    //   - buildSetupCurrentLevelFunctionIfNeeded: returns null when
    //     gameUsesTilemapCollision(gameIR) == false.
    //   - buildMainLoopLevelSwitchGuardIfNeeded: returns emptyList when EITHER
    //     gameUsesTilemapCollision == false OR no scene id matches
    //     {"nextLevel", "next_level"}.
    //
    // This sentinel exercises the FIRST gate (tilemap-collision off). The
    // by-id-name fallback gate (Plan 12-22's regression candidate: dropping
    // the secondary gate predicate) is covered by Plan 12-17's own tests; this
    // file locks the conservative shape — both halves of the lockstep emission
    // are absent when the first gate is off.
    //
    // Why this is the right NEGATIVE for the JVM tier (per CLAUDE.md
    // §"Scope-level grep gates corollary"): if the first gate accidentally
    // fired unconditionally, EVERY game (Pong, Breakout, Banks) would emit the
    // guard against undeclared `_next_level`/`_current_level` symbols, failing
    // SDCC link with `undefined identifier`. Locking the absence here proves
    // the gate is closed by default — preserving the byte-identical regression
    // invariant for the 7 framework-validated example games.
    // -------------------------------------------------------------------------

    @Test
    fun `noTilemap_omits_levelSwitch_guard`() {
        val gameIR =
            game("LevelSwitchNoTilemapTest") {
                    // NO platformerPhysics — gate stays OFF.
                    // No zones — Path B of gameUsesTilemapCollision also returns false.
                    val titleScene =
                        scene("title") {
                            frame {
                                whenever(buttons.start.pressed) { navigate(SceneRef("gameplay")) }
                            }
                        }
                    scene("gameplay") {
                        frame { whenever(buttons.start.pressed) { navigate(titleScene) } }
                    }
                    // Note: nextLevel scene also OMITTED — both halves of the double-gate are
                    // off, locking the strictest gate-off shape.
                    start = titleScene
                }
                .build()

        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()
        File(EVIDENCE_DIR, "noTilemap_levelSwitch_main_head.c").writeText(mainC.take(8000))

        // Main-loop guard must NOT appear. `_next_level != _current_level` is the guard's
        // distinctive substring (Plan 12-17 SUMMARY §provides). The brace-walk on main() is
        // not strictly necessary here because the substring is also absent at file scope —
        // but locking it at function scope documents the assertion intent (the guard is in
        // main() when present, so its absence in main()'s body is the load-bearing claim).
        val mainBody = extractFunctionBody(mainC, "void main(void)")
        assertTrue(
            mainBody.isNotEmpty(),
            "main() must still be emitted when the platformer gate is OFF (main is the entry " +
                "point — its absence would be a more severe regression than the gate). " +
                "main.c head:\n${mainC.take(2000)}",
        )
        assertFalse(
            mainBody.contains("_next_level != _current_level"),
            "main() body must NOT contain the level-switch guard substring when tilemap- " +
                "collision is OFF (gate-off byte-identical regression invariant). " +
                "main() body:\n${mainBody.take(4000)}",
        )

        // setup_current_level helper definition must NOT appear in main.c. Locking the absence
        // at the `void setup_current_level` prefix (not just the symbol) catches a regression
        // that, e.g., emitted only the prototype in game.h without the definition (which would
        // leak through the substring contract above but fail SDCC link).
        assertFalse(
            mainC.contains("void setup_current_level"),
            "main.c must NOT contain `void setup_current_level` definition when tilemap- " +
                "collision is OFF (companion gate to the main-loop guard above; both gates " +
                "are open or both are closed in lockstep). main.c head:\n${mainC.take(2000)}",
        )

        // Companion gate — game.h prototype must also be absent. Plan 12-17 wires the manual
        // NONBANKED prototype via headerRawSections; a regression that decoupled this from
        // the function definition would leak the prototype while the helper is gone. Locking
        // both halves of the emission contract together preserves the lockstep invariant.
        val gameH = output.files["game.h"] ?: error("game.h not generated")
        assertFalse(
            gameH.contains("setup_current_level"),
            "game.h must NOT contain a setup_current_level prototype when tilemap-collision " +
                "is OFF (prototype + definition emit in lockstep per Plan 12-17 Task 2). " +
                "game.h head:\n${gameH.take(2000)}",
        )
    }

    // -------------------------------------------------------------------------
    // POSITIVE — setup_current_level emits camera reset per case (Phase 12.6 D-07)
    //
    // Root cause (debug session 12-6-07-runtime-anchor5-still):
    //   setup_current_level() wrote _playerX/_playerY and velocity resets but did
    //   NOT reset _camera_x / _old_camera_x to 0. After a level switch, the player
    //   spawned at x=40px but _camera_x retained the old gameplay value (~113 for
    //   level-1). platformer_physics_update() only advances _camera_x when
    //   player_real_x >= 80; spawn at x=40 never triggers the update. The player
    //   sprite rendered at screen_x = 40 - 113 = -73 (off-screen).
    //
    //   Fix: GBDKPipeline.buildSetupCurrentLevelFunctionIfNeeded() appends
    //   `_camera_x = 0; _old_camera_x = 0;` after the velocity reset lines inside
    //   each case branch. This mirrors gbdk/examples/cross-platform/platformer_template/
    //   src/main.c:63 `camera_x=0` which runs after SetupCurrentLevel().
    //
    // Test approach: call buildSetupCurrentLevelFunctionIfNeeded(gameIR, emptyMap())
    //   directly (same hook as the fallback test above) to lock the emitted raw string
    //   at the JVM tier. A regression that dropped either reset would leave _camera_x
    //   at its old level value, producing the original off-screen-player defect.
    //
    // Reference evidence: debug/12-6-07-runtime-anchor5-still.md
    //   03-level-2.json (BEFORE fix): camera_x=113, map_pos_x=46, player off-screen.
    //   03-level-2.json (AFTER fix):  camera_x=0,   map_pos_x=0,  player visible.
    // -------------------------------------------------------------------------

    @Test
    @Suppress("ktlint:standard:function-naming")
    fun setupCurrentLevel_emits_camera_reset_per_case_D07() {
        val gameIR = buildTilemapCollisionGameDsl()
        val raw =
            pipeline.buildSetupCurrentLevelFunctionIfNeeded(gameIR, emptyMap())
                ?: error(
                    "setup_current_level must be emitted when gameUsesTilemapCollision is true " +
                        "(Phase 12.6 D-07 camera-reset test — emission gate unchanged)"
                )

        // Lock _camera_x = 0; assignment per gameplay-zone case branch.
        // The pipeline emits the bare `_camera_x = 0;` as raw C text inside
        // the trimIndent() template string in buildSetupCurrentLevelFunctionIfNeeded.
        assertTrue(
            raw.contains("_camera_x = 0;"),
            "setup_current_level body must reset _camera_x to 0 in each case branch " +
                "(Phase 12.6 D-07 fix — mirrors reference main.c:63 camera_x=0 after " +
                "SetupCurrentLevel). A missing reset leaves _camera_x at the old scroll " +
                "position; player spawns at x=40 but camera stays at ~113, placing the " +
                "sprite off-screen). raw head: ${raw.take(4000)}",
        )

        // Lock _old_camera_x = 0; companion reset. _old_camera_x is used by
        // platformer_physics_update() as the previous-frame camera reference for
        // computing scroll delta. Without resetting it, the first physics frame after
        // level switch computes a spurious delta from the stale old value.
        assertTrue(
            raw.contains("_old_camera_x = 0;"),
            "setup_current_level body must reset _old_camera_x to 0 in each case branch " +
                "(companion to _camera_x reset — prevents spurious delta on first physics " +
                "frame after level switch). raw head: ${raw.take(4000)}",
        )

        // Count: fixture has 2 gameplay zones → at least 2 occurrences of each reset
        // confirm the reset is emitted per-case, not only once at the top of the function.
        val cameraXResets = "_camera_x = 0;".toRegex().findAll(raw).count()
        assertTrue(
            cameraXResets >= 2,
            "setup_current_level must emit _camera_x = 0 for each gameplay-zone case " +
                "(fixture has 2 zones, expect >=2 occurrences). Found $cameraXResets. " +
                "raw head: ${raw.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // POSITIVE — setup_current_level emits windowed submap write (Phase 12.6 D-08)
    //
    // Root cause (debug session 12-6-07-runtime-anchor5-still CYCLE 3):
    //   Previous emission used `_bkg_tiles_load_banked(bank, 0, 0, ZONE_WIDTH,
    //   ZONE_HEIGHT, tilemap)`. ZONE_WIDTH for the level-1/2 zones is 60. The
    //   GB BG map is 32x32 cells; `set_bkg_tiles` WRAPS at the 32-cell boundary,
    //   so columns 32..59 of the tilemap overwrote columns 0..27 of the BG map.
    //   At camera_x=0 the visible window (cols 0..19) showed tilemap[32..51, r]
    //   chimera of right-side tiles plus untouched leftover cells from level 1.
    //
    //   MCP-confirmed at frame 1208: bgText row 16 = `$!"#$!"#%67676767676` —
    //   identical to level 1's floor row, NOT level 2's expected content.
    //
    //   Fix: swap full-tilemap _bkg_tiles_load_banked for
    //   _bkg_set_level_submap_banked(0, 0, 21, 18) — uses set_bkg_submap which
    //   takes a stride parameter (no wrap, even though source tilemap is wider
    //   than 32). Mirrors reference SetCurrentLevelSubmap(0, 0,
    //   DEVICE_SCREEN_WIDTH+1, DEVICE_SCREEN_HEIGHT) at
    //   gbdk/examples/cross-platform/platformer_template/src/main.c:43-50.
    //
    // Test approach: call buildSetupCurrentLevelFunctionIfNeeded(gameIR, emptyMap())
    //   directly (same hook as the D-07 camera reset test above) and assert (a) the
    //   new helper call shape is present per case, (b) the OLD wide-write shape is
    //   forbidden. The forbidden-shape assertion is the load-bearing regression guard:
    //   a future reversion of either the helper site OR the helper definition would
    //   silently re-introduce the wraparound corruption with no visible symptom in
    //   gameplay-1 (level 1 ALSO wraps from cols 32..59, but the player can only walk
    //   col 0..52 before the level-end trigger fires — so the wrapped cells stay
    //   off-screen during normal play). The cycle-3 user UAT round caught it only
    //   AFTER level switch because spawn position (40px = col 5) lands inside the
    //   wrapped region; level 1 is "lucky" that its spawn is the same column 5 but
    //   the BG map was zero-initialized at boot so the wrap collided with blanks.
    //
    // Reference evidence: debug/12-6-07-runtime-anchor5-still.md CYCLE 3 §
    //   bgText row 16 (BEFORE fix): `$!"#$!"#%67676767676` (level-1 floor pattern)
    //   bgText row 16 (AFTER fix, MCP):  matches reference world1-area2 first screen
    // -------------------------------------------------------------------------

    @Test
    @Suppress("ktlint:standard:function-naming")
    fun setupCurrentLevel_emits_windowed_submap_write_not_full_tilemap_D08() {
        val gameIR = buildTilemapCollisionGameDsl()
        val raw =
            pipeline.buildSetupCurrentLevelFunctionIfNeeded(gameIR, emptyMap())
                ?: error(
                    "setup_current_level must be emitted when gameUsesTilemapCollision is true " +
                        "(Phase 12.6 D-08 windowed-submap test — emission gate unchanged)"
                )

        // Lock the new helper call shape. _bkg_set_level_submap_banked is a HOME-bank
        // NONBANKED wrapper (declared by buildSetLevelSubmapHelperIfNeeded at
        // GBDKPipeline line ~2324) that internally calls set_bkg_submap with the
        // _current_level_map pointer + _current_level_width_in_tiles stride.
        // Coordinates (0, 0) anchor the window at the BG map origin; size (21, 18) =
        // DEVICE_SCREEN_WIDTH+1 x DEVICE_SCREEN_HEIGHT — the reference's window.
        assertTrue(
            raw.contains("_bkg_set_level_submap_banked(0u, 0u, 21u, 18u);"),
            "setup_current_level body must call _bkg_set_level_submap_banked(0u, 0u, 21u, 18u) " +
                "in each case branch (Phase 12.6 D-08 fix — replaces the full-tilemap " +
                "_bkg_tiles_load_banked write that wrapped at the 32-cell BG map boundary). " +
                "Window dims 21x18 = DEVICE_SCREEN_WIDTH+1 x DEVICE_SCREEN_HEIGHT mirror the " +
                "reference's SetCurrentLevelSubmap(0, 0, DEVICE_SCREEN_WIDTH+1, " +
                "DEVICE_SCREEN_HEIGHT) at platformer_template/src/main.c:43-50. " +
                "raw head: ${raw.take(4000)}",
        )

        // Forbid the OLD wide-write shape. _bkg_tiles_load_banked with the full
        // ZONE_WIDTH/ZONE_HEIGHT macros is the regression we are guarding against —
        // it does NOT take a stride parameter, so for any source tilemap WIDTH > 32 it
        // wraps via set_bkg_tiles' modular addressing and corrupts the visible BG.
        // The substring matches the exact emission template — a future revert that
        // restores the wide write would fail this check before reaching the runtime
        // wrap-corruption symptom. Per-zone macros (_zone_<id>_tilemap_WIDTH/HEIGHT)
        // are still referenced elsewhere in the function (the metadata assignments
        // at the top of each case), so a file-wide grep would false-positive — the
        // substring below matches the FULL call site shape including (bank, 0, 0,
        // ...) which only occurs in the forbidden pattern.
        assertFalse(
            raw.contains("_bkg_tiles_load_banked(2u, 0u, 0u, _zone_") ||
                raw.contains("_bkg_tiles_load_banked(1u, 0u, 0u, _zone_"),
            "setup_current_level body must NOT contain the full-tilemap " +
                "_bkg_tiles_load_banked(bank, 0u, 0u, _zone_<id>_tilemap_WIDTH, ...) " +
                "emission — that shape wraps at the 32-cell BG map boundary for any " +
                "tilemap wider than 32 cells (level-1/2 zones are 60 wide, so cols 32..59 " +
                "overwrite cols 0..27). Replaced by _bkg_set_level_submap_banked(0u, 0u, " +
                "21u, 18u) in Phase 12.6 D-08. " +
                "raw head: ${raw.take(4000)}",
        )

        // Count: fixture has 2 gameplay zones → at least 2 occurrences of the new
        // helper call confirm the emission is per-case, not only once at the top.
        val submapCalls =
            "_bkg_set_level_submap_banked(0u, 0u, 21u, 18u);"
                .toRegex(RegexOption.LITERAL)
                .findAll(raw)
                .count()
        assertTrue(
            submapCalls >= 2,
            "setup_current_level must emit _bkg_set_level_submap_banked per gameplay-zone " +
                "case (fixture has 2 zones, expect >=2 occurrences). Found $submapCalls. " +
                "raw head: ${raw.take(4000)}",
        )
    }
}
