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
// PHASE 12.7 ROUND-6 H3 FIX — level-end trigger grounded-guard emission lock
//
// Phase 12.7 H3 root cause (Plan 12.7-26 diagnostic verdict):
// `PlatformerVisitor.buildTilemapPhysicsUpdateFunction` emits the level-end
// trigger CIf (section 8, lines 1204-1218) with a SINGLE horizontal-position
// condition `if (player_real_x > _current_level_width - 32u)`. The `_grounded`
// variable IS resolved in the outer function scope (lines 579-580) but is NOT
// referenced in the trigger CIf condition. Consequence: the trigger fires while
// the player is airborne (grounded=0, vy=416 at frame 1327 per anchor-5 sidecar).
//
// SPEC R-03 contract: player must be pinned to the floor near the right-edge
// trigger. This is a player-facing game-design contract, not a test-evidence
// convention — the fix MUST be in the codegen, not in the UAT harness.
//
// R-05 advance (this test): locks the post-fix emit shape. The test asserts
// that within the brace-walked `platformer_physics_update` body, the trigger
// CIf scope (bounded by the `// Level-end trigger` CComment header) contains
// the `_grounded` token. Per Plan 12.7-28 GREEN fix: the visitor will emit
//   `if (player_real_x > _current_level_width - 32u && _grounded != 0u) { ++_next_level; }`
// (or precedence-safe equivalent). Either single-CIf conjunction or nested CIf
// wrap satisfies the invariant; this test uses substring containment inside the
// trigger CIf scope rather than an exact-shape regex, accepting both forms.
//
// RED STATE (W21 — this plan): pre-Plan-12.7-28 emit has no `_grounded` in
// the trigger CIf region; the primary assertion FAILS with a message naming
// the trigger region and the missing token.
//
// GREEN STATE (W22 — Plan 12.7-28): post-fix emit includes `_grounded`
// conjunction; assertion PASSES.
//
// SCOPE-LEVEL GREP GATE (CLAUDE.md §"Scope-level grep gates" corollary):
// A file-level `mainC.contains("_grounded")` is INSUFFICIENT here because
// `_grounded` also appears in the foot-probe anyHit thenBody elsewhere in the
// same function (the `groundedSym = 1` assignment). Per the corollary, per-
// function invariants MUST extract the function body via brace-walk, then
// further narrow to the trigger CIf scope before asserting. This test does
// both: (1) brace-walks `platformer_physics_update`, (2) walks forward from
// the `Level-end trigger` CComment header to the matching closing brace.
//
// NAMING CONVENTION: class name `LevelEndTriggerGroundedGuardEmissionTest`
// follows the established `Per-feature emission-test naming` pattern from
// 12.7-PATTERNS.md §"Per-feature emission-test naming" — parallel to the
// existing `Defect4SymbolRewriteEmissionTest`, `JumpHoldEmissionTest`,
// `WalkCycleEmissionTest`, `TilemapPhysicsPlayerSymbolEmissionTest`,
// `PlatformerPhysicsSnapToTileTopEmissionTest`.
// =============================================================================

class LevelEndTriggerGroundedGuardEmissionTest {

    companion object {
        /**
         * Evidence is written to the module's gitignored build/ scratch directory (R1 + R3).
         *
         * `user.dir` at `:gbkt-genre-platformer:test` runtime resolves to the
         * `gbkt-genre-platformer` module root, so `build/gbkt/test-evidence` is the module's own
         * gitignored build directory — no `../` ascent needed (22-PATTERNS Pitfall 5). In-test C
         * assertions remain the gate; the txt dumps are for post-failure review only.
         */
        val EVIDENCE_DIR =
            File(System.getProperty("user.dir")).resolve("build/gbkt/test-evidence").normalize()
    }

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts a C function body by brace-walking from the first line whose contents start with
     * [functionSignaturePrefix] (e.g. `void platformer_physics_update`) until the matching closing
     * brace at depth zero.
     *
     * The returned blob includes the signature line and the closing brace, so downstream
     * `.contains()` checks operate ONLY on tokens that live inside the named function — never on
     * tokens from unrelated functions in the same file (per CLAUDE.md §"Scope-level grep gates"
     * corollary).
     *
     * This is the Kotlin-side mirror of the awk pattern documented in VALIDATION.md row 2:
     * ```
     * awk '/^void platformer_physics_update/{p=1;d=0} p{d+=gsub(/{/,""); d-=gsub(/}/,""); if(d<0)exit} p'
     * ```
     *
     * Matching is anchored to the START of a line (the prefix must appear at column 0) so
     * occurrences inside string literals, comments, or argument lists of a different function
     * cannot false-match. This is the literal counterpart of awk's `/^prefix/` anchor.
     *
     * The helper is BYTE-IDENTICAL (modulo anchor change at call site) to the copy in
     * `TilemapCollisionEmissionTest.kt` lines 90–110. Convention: the helper is INLINED in each
     * sibling test class — not factored to a shared utility — per `12.7-PATTERNS.md` §"Shared
     * Patterns / brace-walk extractFunctionBody — inline per test class".
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
     * IMPORTANT: This GameIR DELIBERATELY OMITS a `tilemap_collision` system. That routes
     * `groundedSym` resolution down the legacy-fallback path in PlatformerVisitor (kt:557-580):
     *
     * val groundedSym = "_" + ((tcSystem?.config?.get("groundedVar") as? String) ?: "grounded")
     *
     * — so `groundedSym` resolves to the literal `_grounded`. The trigger-region assertion below
     * anchors on `_grounded` accordingly.
     *
     * Shape mirrors `PlatformerPhysicsSnapToTileTopEmissionTest.buildPlatformerGameIR` (the closest
     * sibling per 12.7-PATTERNS.md), modulo: name string.
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
            name = "TestLevelEndTriggerGroundedGuardGame",
            config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2),
            scenes = listOf(SceneIR(id = "gameplay")),
            systems = listOf(system),
            startScene = "gameplay",
        )
    }

    // -------------------------------------------------------------------------
    // H3 fix invariant — level-end trigger CIf must be gated by _grounded
    //
    // Plan 12.7-26 verdict: the trigger CIf (PlatformerVisitor.kt:1204-1218)
    // emits a grounded-blind condition. Fix: extend with `&& _grounded != 0u`.
    //
    // RED state (W21 — Plan 12.7-27): no `_grounded` in trigger region → FAIL.
    // GREEN state (W22 — Plan 12.7-28): `_grounded` in trigger region → PASS.
    //
    // WHY SUBSTRING CONTAINMENT (not exact-shape regex):
    //   The exact form may vary — single CIf conjunction OR nested CIf wrap.
    //   Plan 12.7-28 picks the form; this test asserts the load-bearing
    //   invariant (any `_grounded` reference inside the trigger CIf scope)
    //   without over-constraining shape.
    // -------------------------------------------------------------------------

    @Test
    fun `level-end trigger CIf is gated by _grounded`() {
        val gameIR = buildPlatformerGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()
        val physicsBody = extractFunctionBody(mainC, "void platformer_physics_update")

        // Persist a tier1-shape snapshot of the function body's trigger region for human
        // review of RED state vs GREEN state diff. Cut the snapshot to the last ~12 lines
        // (the trigger CIf is the final section of the function per the visitor's order).
        val snapshotLines = physicsBody.lines()
        val triggerRegionStart = snapshotLines.indexOfFirst {
            it.contains("Level-end trigger") || it.contains("level-end trigger")
        }
        val triggerSnapshot =
            if (triggerRegionStart >= 0) {
                snapshotLines
                    .subList(triggerRegionStart, minOf(snapshotLines.size, triggerRegionStart + 8))
                    .joinToString("\n")
            } else {
                physicsBody.takeLast(800)
            }
        File(EVIDENCE_DIR, "platformer_physics_update_level_end_trigger.c")
            .writeText(triggerSnapshot)

        // Pre-condition: function body must be extractable
        assertTrue(
            physicsBody.isNotEmpty(),
            "platformer_physics_update body must be extractable via brace-walk from main.c. " +
                "main.c head:\n${mainC.take(2000)}",
        )

        // Locate the trigger CIf scope via the unique "Level-end trigger" comment header
        // (emitted by PlatformerVisitor.kt:1204 CComment). The trigger CIf body spans from
        // the comment to the matching closing brace.
        val commentIdx = snapshotLines.indexOfFirst {
            it.contains("Level-end trigger") || it.contains("level-end trigger")
        }
        assertTrue(
            commentIdx >= 0,
            "platformer_physics_update body must contain the `Level-end trigger` comment header " +
                "(PlatformerVisitor.kt:1204 CComment). body:\n${physicsBody.take(4000)}",
        )

        // Walk forward from the comment to capture the trigger CIf condition + body region
        // until matching brace depth returns to zero (relative to the CIf's opening brace).
        // The trigger CIf is a 3-4 line block in the emitted C.
        val triggerRegion = StringBuilder()
        var depth = 0
        var started = false
        for (i in commentIdx until snapshotLines.size) {
            val line = snapshotLines[i]
            triggerRegion.appendLine(line)
            for (ch in line) {
                if (ch == '{') {
                    depth++
                    started = true
                }
                if (ch == '}') depth--
            }
            if (started && depth == 0) break
        }
        val triggerRegionStr = triggerRegion.toString()

        // Primary assertion: the trigger CIf region MUST contain the `_grounded` token.
        // Per Plan 12.7-28 GREEN fix: the visitor emits
        //   `if (player_real_x > _current_level_width - 32u && _grounded != 0u) { ++_next_level; }`
        // (or precedence-safe equivalent). Either way the token `_grounded` MUST appear
        // within the trigger CIf scope.
        assertTrue(
            triggerRegionStr.contains("_grounded"),
            "Level-end trigger CIf must be gated by `_grounded` (H3 fix per Plan 12.7-26 verdict). " +
                "Found trigger region:\n$triggerRegionStr",
        )

        // Subsidiary assertion: the `++_next_level` write must still appear in the trigger
        // region (regression-guard: GREEN fix must not accidentally remove the increment).
        assertTrue(
            triggerRegionStr.contains("++_next_level") ||
                triggerRegionStr.contains("_next_level++"),
            "Level-end trigger CIf must still increment `_next_level` (regression-guard). " +
                "Found trigger region:\n$triggerRegionStr",
        )
    }
}
