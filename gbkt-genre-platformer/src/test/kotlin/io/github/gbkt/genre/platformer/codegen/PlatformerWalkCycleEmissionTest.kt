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
// Phase 12.3 Plan 09 (R5 acceptance) — per-function awk brace-walk emission
// test that LOCKS Plan 12.3-08's auto-emitted walk-cycle (threeFrameCounter
// → walkFrameIdx advance) inside `void platformer_physics_update`.
//
// Plan 12.3-08 added a section-0b emission block (PlatformerVisitor.kt §line
// 738+) gated on the THREE-clause AND:
//
//     gameUsesPlatformerInput(gameIR) &&
//     walkFrameIdxVar != null &&
//     threeFrameCounterVar != null
//
// The two AssignableVar binders (`walkFrameIdxVar`, `threeFrameCounterVar`)
// arrive via PlatformerInputBuilder (Plan 12.3-01) and land in the
// `platformer_input` GenericSystem.config map. The D-03 SKIP-WHEN-UNSET
// contract is the architectural job of THIS test: when EITHER binder is
// null, the visitor MUST emit ZERO references to `_walkFrameIdx` or
// `_threeFrameCounter` — there is NO magic-string fallback. A future
// refactor that "helpfully" auto-emits `_walkFrameIdx` references when
// the user forgot the binders MUST be caught HERE.
//
// Emission shape (reference player.c lines ~240-250, captured in Plan 12.3-08
// SUMMARY):
//
//   if (_player_vx != 0) {                          // CIntLiteral(0) — signed-RHS hygiene
//       _threeFrameCounter++;
//       if (_threeFrameCounter >= 6u) {              // CLiteral(cyclePeriod) — UINT8 RHS
//           _threeFrameCounter = 0u;
//           _walkFrameIdx++;
//           if (_walkFrameIdx >= 3u) {               // CLiteral(walkFrameCount) — UINT8 RHS
//               _walkFrameIdx = 0u;
//           }
//       }
//   } else {
//       _walkFrameIdx = 0u;
//       _threeFrameCounter = 0u;
//   }
//
// Forbidden token (Pitfall 1 / L-13.2 regression guard): the body MUST NOT
// contain `_player_vx != 0u`. CLiteral(0) on the signed-RHS vxSym would
// trigger SDCC's usual arithmetic conversion (C11 §6.3.1.8) — the comparison
// would functionally still work for `!=` (zero compares equal regardless of
// sign-bit) but the file-wide convention is CIntLiteral on vxSym comparisons
// for uniformity. A future visitor regression that swapped CIntLiteral(0)
// → CLiteral(0) here would emit `_player_vx != 0u` and fail RED.
//
// Multiplicity sanity (L-13.3): `_walkFrameIdx` substring count >= 3 in the
// positive case (increment + comparison + reset minimum = 3 references). If
// only one or two references emit, the full cycle block did not land and the
// test fails RED.
//
// CLAUDE.md §"Scope-level grep gates" forbids a file-level
// `mainC.contains("_walkFrameIdx")` here because `_walkFrameIdx` ALSO lands
// at the top of main.c as a WRAM global declaration (`UINT8 _walkFrameIdx;`)
// — the brace-walk confines the substring checks to the
// `platformer_physics_update` body only, so the negative tests can assert
// ZERO references inside the function regardless of the global declaration.
//
// Evidence-before-assert (Pattern 5 / L-10.2): each test writes the extracted
// physics body to
// `evidence/tier1-shape/platformer_physics_update_walkcycle_*.c` BEFORE any
// assertion fires, so a RED run still produces a reviewable artifact on disk.
// =============================================================================

class PlatformerWalkCycleEmissionTest {

    companion object {
        /**
         * Evidence is written under the **active checkout root** (worktree-safe).
         *
         * Same shape as `PlatformerInputEmissionTest.EVIDENCE_DIR` — for the
         * `:gbkt-genre-platformer:test` task, `user.dir` resolves to
         * `<repo>/gbkt-genre-platformer`; we ascend one level (`..`) to reach the worktree root,
         * then descend into the Phase 12.3 evidence directory (Pattern 5 / RESEARCH §"EVIDENCE_DIR
         * convention — worktree-safe"). Hard-coding an absolute path would silently route evidence
         * files outside the active worktree and miss the commit (#3099 worktree path safety).
         */
        val EVIDENCE_DIR =
            File(System.getProperty("user.dir"))
                .resolve(
                    "../.planning/phases/12.3-platformer-visitor-auto-emission-wiring-wire-input-playervx-/evidence/tier1-shape"
                )
                .normalize()
    }

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Helpers — extractFunctionBody copied verbatim from JumpHoldEmissionTest.kt
    // and PlatformerInputEmissionTest.kt (CONTEXT.md §L-12.3 — duplicate is
    // acceptable / preferred per sibling-tests convention).
    // -------------------------------------------------------------------------

    /**
     * Extracts a C function body by brace-walking from the first line whose contents start with
     * [functionSignaturePrefix] (e.g. `void platformer_physics_update`) until the matching closing
     * brace at depth zero.
     *
     * Mirror of the helper in `TilemapCollisionEmissionTest.kt` (Plan 12-09),
     * `JumpHoldEmissionTest.kt` (Plan 12-13), `HorizontalScrollEmissionTest.kt` (Plan 12-12), and
     * `PlatformerInputEmissionTest.kt` (Plan 12.3-03). The returned blob includes the signature
     * line and the closing brace, so downstream `.contains()` checks operate ONLY on tokens that
     * live inside the named function — never on tokens from unrelated functions in the same file
     * (per CLAUDE.md §"Scope-level grep gates"). Crucially, this scope-confinement means the global
     * WRAM declaration `UINT8 _walkFrameIdx;` at the top of main.c is excluded — the negative tests
     * can assert ZERO references INSIDE the function body even when the global is present.
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
     * Positive fixture — `platformer_physics` (with `solidThreshold` set so the tilemap- physics
     * branch runs) PLUS `platformer_input` (with BOTH walk-cycle AssignableVar binders populated
     * AND the numeric tuning numbers).
     *
     * The three gating clauses must all be true:
     * - `gameUsesTilemapCollision(gameIR)` returns true (via `solidThreshold` set) → the
     *   `buildTilemapPhysicsUpdateFunction` branch is taken (Plan 12-11).
     * - `gameUsesPlatformerInput(gameIR)` returns true (via the `platformer_input` system presence)
     *   → the section-0b emission gate's first clause fires.
     * - `walkFrameIdxVar != null && threeFrameCounterVar != null` → the gate's two binder clauses
     *   fire → the walk-cycle block emits.
     *
     * With `vxVar` unset in this fixture (no `tilemap_collision` system registered), the visitor's
     * fallback `vxSym = "_" + "player_vx"` (PlatformerVisitor.kt:555) applies. So the emitted
     * comparison is `_player_vx != 0`. Same symbol the existing JumpHoldEmissionTest and
     * PlatformerInputEmissionTest evidence exhibit.
     */
    private fun buildPositiveGameIR(): GameIR {
        val physicsConfig =
            PlatformerPhysicsConfig(
                gravity = 2,
                jumpForce = 8,
                terminalVelocity = 12,
                solidThreshold = 17,
                jumpHoldMaxFrames = 0,
            )
        val physicsSystem =
            GenericSystem(
                id = "plat",
                config = mapOf("type" to "platformer_physics", "physicsConfig" to physicsConfig),
            )
        val inputSystem =
            GenericSystem(
                id = "input",
                config =
                    mapOf(
                        "type" to "platformer_input",
                        "walkSpeed" to 128,
                        "friction" to 8,
                        "airFriction" to 0,
                        "walkFrameCount" to 3,
                        "cyclePeriod" to 6,
                        // Plan 12.3-01 binder result — names the user gave to their delegate
                        // properties (`var walkFrameIdx by u8Var(0)` →
                        // `platformerInput { walkFrameIdx(walkFrameIdx) }` → config key holds
                        // the string `"walkFrameIdx"`). Visitor prefixes with `_` to produce
                        // `_walkFrameIdx`.
                        "walkFrameIdxVar" to "walkFrameIdx",
                        "threeFrameCounterVar" to "threeFrameCounter",
                    ),
            )
        return GameIR(
            name = "TestPlatformerWalkCyclePositive",
            config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2),
            scenes = listOf(SceneIR(id = "gameplay")),
            systems = listOf(physicsSystem, inputSystem),
            startScene = "gameplay",
        )
    }

    /**
     * Negative fixture #1 — `platformer_input` IS present (so `gameUsesPlatformerInput` returns
     * true and the section-0 input emission DOES run — see PlatformerInputEmissionTest positive
     * case for that shape) but the two walk-cycle AssignableVar binders are NOT set. walkSpeed /
     * friction / airFriction / walkFrameCount / cyclePeriod ARE set (so the input emission inside
     * section 0 fires, distinguishing this from "no platformer_input at all").
     *
     * The walk-cycle gate's three-clause AND becomes false on the second/third clause:
     *
     *     gameUsesPlatformerInput(gameIR)       == true
     *     walkFrameIdxVar != null               == false   ← gate-off
     *     threeFrameCounterVar != null          == false   ← gate-off
     *
     * → section 0b is skipped entirely. The function body MUST NOT reference `_walkFrameIdx` or
     * `_threeFrameCounter` ANYWHERE — D-03 SKIP-WHEN-UNSET contract (L-5.4 /
     * feedback_no_magic_strings.md). This is the architectural job of this test — guard against any
     * future refactor that "helpfully" auto-emits `_walkFrameIdx` references when the user forgot
     * to call the binders.
     */
    private fun buildBindersUnsetGameIR(): GameIR {
        val physicsConfig =
            PlatformerPhysicsConfig(
                gravity = 2,
                jumpForce = 8,
                terminalVelocity = 12,
                solidThreshold = 17,
                jumpHoldMaxFrames = 0,
            )
        val physicsSystem =
            GenericSystem(
                id = "plat",
                config = mapOf("type" to "platformer_physics", "physicsConfig" to physicsConfig),
            )
        // platformer_input system present with the numeric tuning keys but WITHOUT the two
        // walk-cycle binders — explicitly modeling the case where the user wrote
        // `platformerInput { walkSpeed(128); friction(8); airFriction(0) }` but forgot the
        // `walkFrameIdx(walkFrameIdx)` / `threeFrameCounter(threeFrameCounter)` calls.
        val inputSystem =
            GenericSystem(
                id = "input",
                config =
                    mapOf(
                        "type" to "platformer_input",
                        "walkSpeed" to 128,
                        "friction" to 8,
                        "airFriction" to 0,
                        "walkFrameCount" to 3,
                        "cyclePeriod" to 6,
                        // walkFrameIdxVar + threeFrameCounterVar deliberately OMITTED.
                    ),
            )
        return GameIR(
            name = "TestPlatformerWalkCycleBindersUnset",
            config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2),
            scenes = listOf(SceneIR(id = "gameplay")),
            systems = listOf(physicsSystem, inputSystem),
            startScene = "gameplay",
        )
    }

    /**
     * Negative fixture #2 — NO `platformer_input` system at all. Same shape as
     * PlatformerInputEmissionTest's negative fixture. `gameUsesPlatformerInput(gameIR)` returns
     * false → BOTH section 0 (input emission) AND section 0b (walk-cycle emission) are gated off
     * entirely. The function body MUST NOT reference `_walkFrameIdx` or `_threeFrameCounter`
     * ANYWHERE.
     *
     * This locks the OUTERMOST gate clause — a regression that broke the `gameUsesPlatformerInput`
     * predicate (e.g. always-returns-true) would leak walk-cycle tokens here and fail RED.
     */
    private fun buildNoSystemGameIR(): GameIR {
        val physicsConfig =
            PlatformerPhysicsConfig(
                gravity = 2,
                jumpForce = 8,
                terminalVelocity = 12,
                solidThreshold = 17,
                jumpHoldMaxFrames = 0,
            )
        val physicsSystem =
            GenericSystem(
                id = "plat",
                config = mapOf("type" to "platformer_physics", "physicsConfig" to physicsConfig),
            )
        return GameIR(
            name = "TestPlatformerWalkCycleNoSystem",
            config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2),
            scenes = listOf(SceneIR(id = "gameplay")),
            systems = listOf(physicsSystem),
            startScene = "gameplay",
        )
    }

    // -------------------------------------------------------------------------
    // POSITIVE — section 0b walk-cycle emission fires when BOTH binders are set
    // AND the gameUsesPlatformerInput gate is on.
    //
    // Locks Plan 12.3-08's emission shape inside `platformer_physics_update`:
    //   - `_player_vx != 0` motion-check on the signed vxSym (CIntLiteral(0)).
    //   - `_threeFrameCounter++` — the 3-frame counter advance.
    //   - `_walkFrameIdx++` — the visual frame index advance.
    //   - `_walkFrameIdx = 0u` — the frame-index reset (one of two reset sites).
    //
    // Forbidden-token guard (Pitfall 1 / L-13.2): `_player_vx != 0u` MUST NOT
    // appear. If a future visitor regression switches `CIntLiteral(0)` →
    // `CLiteral(0)` on this comparison, this assertion fails RED and surfaces
    // the file-wide signed-RHS literal-discipline drift BEFORE it lands in the
    // ROM (where the `!=` works by accident but the convention is broken).
    //
    // Multiplicity sanity (L-13.3): `_walkFrameIdx` appears in the function
    // body at least 3 times in the positive case (increment + comparison +
    // reset minimum). If the visitor lands only one or two references, the
    // full cycle block did not emit and this assertion fails RED.
    // -------------------------------------------------------------------------

    @Test
    fun positive_cycle_emission_with_signed_RHS_discipline() {
        val gameIR = buildPositiveGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Evidence-before-assert: extract and persist the physics body BEFORE any assertion
        // fires so a RED run still produces a reviewable artifact on disk (Pattern 5 / L-10.2).
        val physicsBody = extractFunctionBody(mainC, "void platformer_physics_update")
        File(EVIDENCE_DIR, "platformer_physics_update_walkcycle_positive.c").writeText(physicsBody)

        assertTrue(
            physicsBody.isNotEmpty(),
            "platformer_physics_update body must be extractable via brace-walk from main.c. " +
                "main.c head:\n${mainC.take(2000)}",
        )

        // ---- Required shape tokens (Plan 12.3-08 emission, both binders set) ----

        // `_player_vx != 0` — the section-0b outer motion check. CIntLiteral(0) means the
        // emitted literal is bare `0` with no `u` suffix (signed-RHS hygiene, Phase 07.9 /
        // L-5.1 / L-13.2). A regression that drops the motion check entirely would fail
        // here. A regression that changes the operator (e.g. `> 0`) would also fail here.
        assertTrue(
            physicsBody.contains("_player_vx != 0"),
            "platformer_physics_update body must contain `_player_vx != 0` — Plan 12.3-08 " +
                "section-0b walk-cycle motion check (CIntLiteral(0), signed RHS, NO `u` " +
                "suffix). physics body:\n${physicsBody.take(8000)}",
        )

        // `_threeFrameCounter++` — the 3-frame counter increment inside the thenBody. A
        // regression that emits a postfix or `+= 1` form would still satisfy this assertion
        // — wait, no: `_threeFrameCounter++` is the prefix form emitted by the existing
        // CUnaryExpr/CEmitter path (line 759 emits CUnaryExpr("++", CVar(tfcSym)) which
        // CEmitter renders as `++_threeFrameCounter`). Verified against the Plan 12.3-08
        // SUMMARY and the JumpHoldEmissionTest convention which accepts both prefix and
        // postfix. Here we use the prefix form because that's what the CEmitter actually
        // produces. NOTE: if the JumpHoldEmissionTest pattern were taken verbatim, both
        // prefix and postfix would be acceptable — but `--_jump_increase_timer` works
        // BOTH ways (prefix at section 5b decrement, postfix would be functionally
        // identical at that position). For the walk-cycle counter increment, the
        // CUnaryExpr emits prefix, so we assert prefix.
        assertTrue(
            physicsBody.contains("++_threeFrameCounter") ||
                physicsBody.contains("_threeFrameCounter++"),
            "platformer_physics_update body must contain a `_threeFrameCounter` increment " +
                "(prefix `++_threeFrameCounter` or postfix `_threeFrameCounter++`) — Plan " +
                "12.3-08 section-0b counter advance (CUnaryExpr(\"++\", CVar(tfcSym))). " +
                "physics body:\n${physicsBody.take(8000)}",
        )

        // `_walkFrameIdx++` — the visual frame-index increment inside the cyclePeriod-reset
        // branch's thenBody. Same prefix/postfix acceptance as the counter increment above.
        // A regression that drops the increment would fail here.
        assertTrue(
            physicsBody.contains("++_walkFrameIdx") || physicsBody.contains("_walkFrameIdx++"),
            "platformer_physics_update body must contain a `_walkFrameIdx` increment " +
                "(prefix `++_walkFrameIdx` or postfix `_walkFrameIdx++`) — Plan 12.3-08 " +
                "section-0b frame-index advance (CUnaryExpr(\"++\", CVar(wfiSym))). " +
                "physics body:\n${physicsBody.take(8000)}",
        )

        // `_walkFrameIdx = 0u` — the reset assignment (CLiteral(0) → `0u`). This appears at
        // TWO sites: (a) inside the walkFrameCount-overflow thenBody (cycle-wrap), (b) inside
        // the outer elseBody (no-motion reset). A regression that drops either reset would
        // still satisfy this single-contains assertion — for stronger discrimination, the
        // multiplicity check below counts ALL `_walkFrameIdx` references and asserts >= 3.
        assertTrue(
            physicsBody.contains("_walkFrameIdx = 0u"),
            "platformer_physics_update body must contain `_walkFrameIdx = 0u` — Plan 12.3-08 " +
                "section-0b frame-index reset (CLiteral(0) → `0u`, UINT8 unsigned context). " +
                "physics body:\n${physicsBody.take(8000)}",
        )

        // ---- Forbidden token (Pitfall 1 / L-13.2 regression guard) ----

        // `_player_vx != 0u` MUST NOT appear. CLiteral(0) on the signed-RHS vxSym would emit
        // this form — the comparison would functionally still work for `!=` (zero compares
        // equal regardless of sign-bit) but the file-wide convention is CIntLiteral on vxSym
        // comparisons for uniformity (Phase 07.9 §Literal Emission Convention / Plan 12.3-08
        // SUMMARY decisions[3]). This guard structurally enforces the convention against a
        // visitor regression that swaps CIntLiteral(0) → CLiteral(0) "to match the
        // surrounding UINT8 context" (the wrong fix — vxSym is INT16, not UINT8).
        assertFalse(
            physicsBody.contains("_player_vx != 0u"),
            "platformer_physics_update body MUST NOT contain `_player_vx != 0u` — that is the " +
                "CLiteral-on-signed-RHS regression Phase 07.9 §Literal Emission Convention " +
                "forbids. Plan 12.3-08 emits CIntLiteral(0) on this comparison. physics body:\n" +
                physicsBody.take(8000),
        )

        // ---- Multiplicity sanity (L-13.3 / Plan 12.3-09 §must_haves) ----

        // `_walkFrameIdx` appears in the function body at LEAST 3 times in the positive case:
        //   1. The CUnaryExpr increment (`++_walkFrameIdx` or `_walkFrameIdx++`)
        //   2. The `_walkFrameIdx >= walkFrameCount` comparison inside the cyclePeriod-reset
        //      branch's thenBody
        //   3. The `_walkFrameIdx = 0u` reset (at least one of the two reset sites — the
        //      walkFrameCount-overflow reset is inside the same nested CIf, the elseBody
        //      no-motion reset is at the outer level)
        //
        // Plan 12.3-08 SUMMARY's emission shape actually produces 4 references inside the
        // function: increment + comparison + 2 resets. We assert >= 3 to leave one
        // reference of headroom for a benign reformat without re-baselining the test.
        // If the visitor lands only one or two references, the full cycle block did not
        // emit (e.g. the inner CIf was dropped).
        val walkFrameIdxCount = physicsBody.split("_walkFrameIdx").size - 1
        assertTrue(
            walkFrameIdxCount >= 3,
            "platformer_physics_update body must reference `_walkFrameIdx` at least 3× " +
                "(increment + comparison + reset minimum) — Plan 12.3-08 SUMMARY emission " +
                "produces 4 references. Found $walkFrameIdxCount. The full cycle block did " +
                "not emit. physics body:\n${physicsBody.take(8000)}",
        )
    }

    // -------------------------------------------------------------------------
    // NEGATIVE #1 — D-03 SKIP-WHEN-UNSET CONTRACT ENFORCEMENT.
    //
    // This is the KEY architectural guard of this plan. When the user writes
    // `platformerInput { walkSpeed(128); friction(8) }` but forgets to call
    // `walkFrameIdx(walkFrameIdx)` / `threeFrameCounter(threeFrameCounter)`,
    // the visitor MUST NOT emit ANY `_walkFrameIdx` or `_threeFrameCounter`
    // reference inside `platformer_physics_update`. There is NO magic-string
    // fallback (L-5.4 / feedback_no_magic_strings.md / Plan 12.3-08 §decisions
    // [1]).
    //
    // Production mechanism (Plan 12.3-08 — three-clause AND gate at
    // PlatformerVisitor.kt §line 738):
    //
    //     gameUsesPlatformerInput(gameIR) == true   ← input emission DOES fire
    //     walkFrameIdxVar != null         == false  ← second clause off
    //     threeFrameCounterVar != null    == false  ← third clause off
    //
    // → section 0b emits ZERO statements. No `_walkFrameIdx` references, no
    // `_threeFrameCounter` references.
    //
    // Critical note on scope: the brace-walk's scope-confinement (CLAUDE.md
    // §"Scope-level grep gates") matters here because `_walkFrameIdx` MIGHT
    // appear as a WRAM global declaration at the TOP of main.c (`UINT8
    // _walkFrameIdx;`) if the user declared `var walkFrameIdx by u8Var(0)`
    // in the DSL. The brace-walk confines the substring check to the
    // physics-function body only, so this test asserts what it actually
    // means: the EMISSION INSIDE THE FUNCTION is gated off, regardless of
    // whether the global declaration exists. In this fixture the global is
    // not declared (no DSL variable declared) — but the scope-confinement
    // discipline is preserved for forward-compatibility with richer fixtures.
    //
    // The contrast with the POSITIVE case (which has section 0 input
    // emission running — `button_held(J_RIGHT)`, walkSpeed=128 etc. — but
    // also section 0b) cleanly isolates "binders unset" from "system
    // unregistered". A regression that emits `_walkFrameIdx` here would
    // surface immediately.
    // -------------------------------------------------------------------------

    @Test
    fun negative_skip_emission_when_binders_unset_D03_contract() {
        val gameIR = buildBindersUnsetGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Evidence-before-assert (Pattern 5 / L-10.2).
        val physicsBody = extractFunctionBody(mainC, "void platformer_physics_update")
        File(EVIDENCE_DIR, "platformer_physics_update_walkcycle_binders_unset.c")
            .writeText(physicsBody)

        // The function MUST still exist (tilemap-physics branch runs because `solidThreshold`
        // is set). Asserting presence rules out a regression that accidentally drops the
        // physics function entirely when the walk-cycle gate is off.
        assertTrue(
            physicsBody.isNotEmpty(),
            "platformer_physics_update body must be extractable via brace-walk from main.c " +
                "even when walk-cycle binders are unset (tilemap-physics branch still runs). " +
                "main.c head:\n${mainC.take(2000)}",
        )

        // D-03 SKIP-WHEN-UNSET CONTRACT — the body MUST NOT reference `_walkFrameIdx`
        // ANYWHERE. The brace-walk confines this check to the function body, so any global
        // declaration of `_walkFrameIdx` (if present) does not interfere. A visitor
        // regression that emits a magic-string `_walkFrameIdx` fallback when the binder is
        // unset would leak the token here and fail RED. This is the architectural job of
        // this test (L-13.1).
        assertFalse(
            physicsBody.contains("_walkFrameIdx"),
            "platformer_physics_update body MUST NOT contain `_walkFrameIdx` when the " +
                "walkFrameIdxVar binder is unset — D-03 SKIP-WHEN-UNSET contract violation " +
                "(L-5.4 / L-13.1 / feedback_no_magic_strings.md). A future visitor refactor " +
                "that auto-emits `_walkFrameIdx` references as a fallback when the user " +
                "forgot the binder MUST be caught HERE. physics body:\n" +
                physicsBody.take(8000),
        )

        // Symmetric contract for the counter binder — same D-03 contract reasoning.
        assertFalse(
            physicsBody.contains("_threeFrameCounter"),
            "platformer_physics_update body MUST NOT contain `_threeFrameCounter` when the " +
                "threeFrameCounterVar binder is unset — D-03 SKIP-WHEN-UNSET contract " +
                "violation. physics body:\n${physicsBody.take(8000)}",
        )
    }

    // -------------------------------------------------------------------------
    // NEGATIVE #2 — outermost gate enforcement. When `platformer_input` system
    // is absent entirely, `gameUsesPlatformerInput(gameIR)` returns false →
    // BOTH section 0 (input emission) AND section 0b (walk-cycle emission)
    // are gated off. The function body MUST NOT reference `_walkFrameIdx` or
    // `_threeFrameCounter`.
    //
    // This locks the OUTERMOST gate clause of the three-clause AND. A
    // regression that broke the `gameUsesPlatformerInput` predicate (e.g.
    // always-returns-true, or misreads the GenericSystem.type discriminant)
    // would leak walk-cycle tokens here and fail RED.
    //
    // Same scope-confinement reasoning as negative #1 — the brace-walk
    // excludes any potential global declaration of `_walkFrameIdx`.
    // -------------------------------------------------------------------------

    @Test
    fun negative_no_emission_when_no_platformer_input_system() {
        val gameIR = buildNoSystemGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Evidence-before-assert (Pattern 5 / L-10.2).
        val physicsBody = extractFunctionBody(mainC, "void platformer_physics_update")
        File(EVIDENCE_DIR, "platformer_physics_update_walkcycle_no_system.c").writeText(physicsBody)

        // Function must still exist (tilemap-physics branch runs).
        assertTrue(
            physicsBody.isNotEmpty(),
            "platformer_physics_update body must be extractable via brace-walk from main.c " +
                "even when platformer_input system is absent (tilemap-physics branch still " +
                "runs). main.c head:\n${mainC.take(2000)}",
        )

        // Outermost gate enforcement — section 0b MUST be skipped because the
        // gameUsesPlatformerInput predicate is false (no `platformer_input` system in
        // gameIR.systems).
        assertFalse(
            physicsBody.contains("_walkFrameIdx"),
            "platformer_physics_update body MUST NOT contain `_walkFrameIdx` when no " +
                "platformer_input system is registered — Plan 12.3-08 section-0b emission " +
                "must be gated off by `gameUsesPlatformerInput(gameIR) == false` " +
                "(outermost clause of the three-clause AND). A regression that broke this " +
                "predicate would leak walk-cycle emission here. physics body:\n" +
                physicsBody.take(8000),
        )

        // Symmetric contract for the counter symbol.
        assertFalse(
            physicsBody.contains("_threeFrameCounter"),
            "platformer_physics_update body MUST NOT contain `_threeFrameCounter` when no " +
                "platformer_input system is registered — Plan 12.3-08 section-0b emission " +
                "must be gated off. physics body:\n${physicsBody.take(8000)}",
        )
    }
}
