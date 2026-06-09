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
import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.genre.platformer.domain.CameraScrollMode
import io.github.gbkt.genre.platformer.domain.PlatformerCameraConfig
import io.github.gbkt.genre.platformer.domain.PlatformerPhysicsConfig
import io.github.gbkt.genre.platformer.domain.ScrollDirection
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// R3 ACCEPTANCE LOCK — gap #2 visitCamera frameOps splice (Plan 12.3-04)
//
// Plan 12.3-04 added a `frameOps` splice to `PlatformerVisitor.visitCamera`
// that emits `RawOp("platformer_camera_update();")` into the gameplay scene's
// frame body whenever the triple-condition gate holds:
//
//   gameUsesTilemapCollision(gameIR) &&
//   cameraConfig.scrollDirections == ScrollDirection.HORIZONTAL &&
//   cameraConfig.mode == CameraScrollMode.SMOOTH_FOLLOW
//
// Scene-frame functions live in `bank1.c` (BANKED) per
// `GBDKPipeline.buildSceneFile` / `SceneVisitor.visit` — when a scene's
// `frameOps` is non-empty, `${scene.id}_frame` is emitted as a banked function
// inside `bank1.c`. `addGenreFrameOps` prepends ScriptOps gathered from genre
// visitors (in `GameIR.systems` declaration order) onto that frame body. So
// when `platformer_physics` precedes `platformer_camera` in `gameIR.systems`,
// the resulting body order is:
//
//   void gameplay_frame(void) BANKED {
//       platformer_physics_update();   // visitPhysics splice (Plan 12-13)
//       platformer_camera_update();    // visitCamera splice  (Plan 12.3-04)
//       // ... user-authored frame ops (sentinel RawOp here)
//   }
//
// This test locks both the presence and the order. The negative case verifies
// back-compat: when `gameUsesTilemapCollision` returns false (no
// `solidThreshold`), the visitCamera gate stays OFF and `platformer_camera_update();`
// does NOT appear in any scene frame body. Existing JumpHold / Horizontal-
// Scroll / TilemapCollision / Defect4SymbolRewrite emission test fixtures
// remain byte-identical (none of them set `solidThreshold` AND `platformer_camera`
// together in a scene with frameOps; this is the first test that exercises the
// full splice).
//
// CLAUDE.md §"Scope-level grep gates" forbids a file-level
// `bank1C.contains("platformer_camera_update();")` here because the same token
// could in principle appear in another scene's frame body in the same file.
// The brace-walk extracts the named `gameplay_frame` body so substring checks
// fire ONLY inside that function — never on tokens from sibling scene frames.
// =============================================================================

class PlatformerCameraCallSiteEmissionTest {

    companion object {
        /**
         * Evidence is written under the **active checkout root** (worktree-safe). Same shape as
         * `JumpHoldEmissionTest.EVIDENCE_DIR` / `HorizontalScrollEmissionTest.EVIDENCE_DIR` — see
         * those comments for the worktree path-safety rationale (#3099). For
         * `:gbkt-genre-platformer:test`, `user.dir` resolves to `<repo>/gbkt-genre-platformer`;
         * we ascend one level to the worktree root, then descend into the phase evidence
         * directory.
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
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts a C function body by brace-walking from the first line whose contents start with
     * [functionSignaturePrefix] (e.g. `void gameplay_frame`) until the matching closing brace at
     * depth zero.
     *
     * Verbatim copy of the helper in `JumpHoldEmissionTest.kt` (Plan 12-14) and
     * `HorizontalScrollEmissionTest.kt` (Plan 12-12). The returned blob includes the signature
     * line and the closing brace, so downstream `.contains()` checks operate ONLY on tokens that
     * live inside the named function — never on tokens from unrelated functions in the same file
     * (per CLAUDE.md §"Scope-level grep gates").
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
     * Build a minimal GameIR carrying:
     *  - a `platformer_physics` GenericSystem (its `physicsConfig.solidThreshold` is non-null →
     *    `gameUsesTilemapCollision()` returns true → visitCamera gate condition 1 fires AND
     *    visitPhysics splice fires)
     *  - a `platformer_camera` GenericSystem (HORIZONTAL + SMOOTH_FOLLOW → visitCamera gate
     *    conditions 2 and 3 fire)
     *  - a scene with `id = "gameplay"` carrying a non-empty `frameOps` (one sentinel `RawOp`)
     *    so `SceneVisitor.visit` emits `void gameplay_frame(void) BANKED { ... }` into
     *    `bank1.c`. Without a non-empty frameOps the function would NOT be generated at all
     *    (`SceneVisitor.visit` gates emission on `scene.frameOps.isNotEmpty()`) — and the
     *    `addGenreFrameOps` splice has nothing to attach to.
     *  - `startScene = "gameplay"` so the gameplay-scene-id discovery fallback chain
     *    (physicsActorIds → findFirstNavigateTarget → startScene → first scene) lands on
     *    "gameplay" via the third-tier fallback. `actorIds` on the scene is intentionally
     *    empty to mirror the typical IR shape (DSL does not populate `actorIds` directly).
     *
     * `physicsSystem` is declared BEFORE `cameraSystem` in `gameIR.systems` so the prepend
     * order in `addGenreFrameOps` produces:
     *
     *     [physics_update(); camera_update();] + originalBody
     *
     * — guaranteeing `cameraIdx > physicsIdx` (D-05 emission order: physics before camera).
     *
     * When [solidThreshold] is null, `gameUsesTilemapCollision()` returns false → the
     * visitCamera gate's FIRST condition fails → `frameOps = emptyMap()` → no
     * `platformer_camera_update();` line is spliced into `gameplay_frame`. This is the
     * negative-case fixture.
     */
    private fun buildPlatformerGameIR(solidThreshold: Int?): GameIR {
        val physicsConfig =
            PlatformerPhysicsConfig(
                gravity = 2,
                jumpForce = 8,
                terminalVelocity = 12,
                solidThreshold = solidThreshold,
            )
        val cameraConfig =
            PlatformerCameraConfig(
                mode = CameraScrollMode.SMOOTH_FOLLOW,
                scrollDirections = ScrollDirection.HORIZONTAL,
            )
        val physicsSystem =
            GenericSystem(
                id = "plat-physics",
                config = mapOf("type" to "platformer_physics", "physicsConfig" to physicsConfig),
            )
        val cameraSystem =
            GenericSystem(
                id = "plat-camera",
                config = mapOf("type" to "platformer_camera", "cameraConfig" to cameraConfig),
            )
        // Sentinel `RawOp` keeps `gameplay.frameOps` non-empty so `SceneVisitor` emits the
        // `gameplay_frame` function. The RawOp's content is irrelevant to this test — it lowers
        // to a single literal C line that does NOT contain either of the discriminator tokens
        // (`platformer_camera_update();` or `platformer_physics_update();`), so brace-walk
        // assertions are not perturbed by the sentinel.
        val gameplay =
            SceneIR(
                id = "gameplay",
                frameOps = listOf(RawOp("// gameplay-frame-anchor")),
            )
        return GameIR(
            name = "TestCameraCallSite",
            config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2),
            scenes = listOf(gameplay),
            systems = listOf(physicsSystem, cameraSystem),
            startScene = "gameplay",
        )
    }

    // -------------------------------------------------------------------------
    // POSITIVE — visitCamera splices `platformer_camera_update();` into
    // gameplay_frame AFTER `platformer_physics_update();` when the triple-
    // condition gate holds.
    //
    // Production mechanism (Plan 12.3-04 — PlatformerVisitor.visitCamera):
    // when `gameUsesTilemapCollision && HORIZONTAL && SMOOTH_FOLLOW`, the
    // visitor returns `frameOps = { gameplaySceneId -> [RawOp("platformer_camera_update();")] }`.
    // `GBDKPipeline.collectGenreFrameOps` accumulates this with visitPhysics's
    // splice in `GameIR.systems` declaration order, and `addGenreFrameOps`
    // prepends the resulting list onto `gameplay_frame`'s body. Final shape:
    //
    //     void gameplay_frame(void) BANKED {
    //         platformer_physics_update();
    //         platformer_camera_update();
    //         // gameplay-frame-anchor
    //     }
    // -------------------------------------------------------------------------

    @Test
    fun positive_gameplay_frame_contains_camera_update_after_physics() {
        val gameIR = buildPlatformerGameIR(solidThreshold = 17)
        val output = pipeline.generate(gameIR)
        val bank1C =
            output.files["bank1.c"]
                ?: error(
                    "bank1.c not generated — fixture is missing a non-empty frameOps on the " +
                        "gameplay scene? available files: ${output.files.keys}"
                )

        EVIDENCE_DIR.mkdirs()

        // Evidence-before-assert: extract and persist the gameplay_frame body BEFORE any
        // assertion fires so a RED run still produces a reviewable artifact on disk (per the
        // evidence-before-assert pattern from Plan 12-09 TilemapCollisionEmissionTest).
        val frameBody = extractFunctionBody(bank1C, "void gameplay_frame")
        File(EVIDENCE_DIR, "gameplay_frame_camera_positive.c").writeText(frameBody)

        assertTrue(
            frameBody.isNotEmpty(),
            "gameplay_frame body must be extractable via brace-walk from bank1.c — " +
                "SceneVisitor should have emitted `void gameplay_frame(void) BANKED { ... }`. " +
                "bank1.c head:\n${bank1C.take(2000)}",
        )

        // Splice landed — Plan 12.3-04's visitCamera frameOps splice produced a RawOp that
        // lowered to the literal C line `platformer_camera_update();`. Brace-walked scope so
        // this token's presence is bound to `gameplay_frame` only — not to any sibling scene
        // frame in bank1.c.
        assertTrue(
            frameBody.contains("platformer_camera_update();"),
            "frameOps splice missing — visitCamera did not append `platformer_camera_update();` " +
                "to the gameplay scene's frame body. The visitCamera triple-gate " +
                "(gameUsesTilemapCollision && HORIZONTAL && SMOOTH_FOLLOW) should have fired. " +
                "gameplay_frame body:\n${frameBody.take(4000)}",
        )

        // Regression guard — visitPhysics's splice still fires (Plan 12-13 baseline). If a
        // future refactor accidentally drops the visitPhysics frameOps, this assertion would
        // catch it before the silent loss made it to runtime. The two splices are independent
        // (different gating conditions) but BOTH must fire for this fixture.
        assertTrue(
            frameBody.contains("platformer_physics_update();"),
            "regression — visitPhysics splice no longer fires (was previously GREEN per Plan " +
                "12-13). gameplay_frame body:\n${frameBody.take(4000)}",
        )

        // D-05 emission order — `platformer_camera_update();` MUST appear lexically AFTER
        // `platformer_physics_update();` in the function body. The `addGenreFrameOps`
        // accumulator iterates `gameIR.systems` in declaration order (physics first, camera
        // second), so the prepend produces `[physics_update; camera_update] + originalBody`.
        // Asserting `cameraIdx > physicsIdx` locks that ordering invariant — a regression that
        // reordered the system list, or changed the prepend to APPEND, or processed camera
        // ops before physics ops, would fail here.
        val cameraIdx = frameBody.indexOf("platformer_camera_update();")
        val physicsIdx = frameBody.indexOf("platformer_physics_update();")
        assertTrue(
            cameraIdx > physicsIdx,
            "camera_update call must appear AFTER physics_update call in gameplay_frame body " +
                "(D-05 emission order — physics before camera). Found cameraIdx=$cameraIdx, " +
                "physicsIdx=$physicsIdx. gameplay_frame body:\n${frameBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // NEGATIVE — back-compat assertion. When the visitCamera triple-gate is OFF
    // (here: `gameUsesTilemapCollision` returns false because solidThreshold is
    // null), `platformer_camera_update();` MUST NOT appear in any scene frame
    // body.
    //
    // Production mechanism (Plan 12.3-04 — `frameOps = emptyMap()` branch):
    // when ANY of the three gate conditions fails, the visitor returns empty
    // frameOps. `collectGenreFrameOps` accumulates nothing from visitCamera for
    // this scene. `addGenreFrameOps` is called with the visitPhysics-only ops
    // list (which still fires because that splice is gated independently on
    // gameUsesTilemapCollision alone, and here that ALSO returns false because
    // solidThreshold is null — so neither splice fires).
    //
    // Therefore `gameplay_frame` contains ONLY the user-sentinel RawOp; neither
    // `platformer_camera_update();` nor `platformer_physics_update();` appears.
    // This sentinel locks the opt-IN nature of the splice. A regression that
    // accidentally fires the gate unconditionally would emit the camera_update
    // call for ALL platformer games — even those without tilemap collision —
    // breaking the byte-identical regression invariant for non-tilemap games.
    // -------------------------------------------------------------------------

    @Test
    fun negative_back_compat_no_camera_call_when_no_tilemap_collision() {
        val gameIR = buildPlatformerGameIR(solidThreshold = null)
        val output = pipeline.generate(gameIR)
        val bank1C = output.files["bank1.c"] ?: ""

        EVIDENCE_DIR.mkdirs()

        val frameBody = extractFunctionBody(bank1C, "void gameplay_frame")
        // Persist negative-case evidence too — useful for confirming the empty frame body
        // shape visually when reviewing a RED run.
        File(EVIDENCE_DIR, "gameplay_frame_camera_negative.c").writeText(frameBody)

        // The `gameplay_frame` function MUST still exist (sentinel RawOp keeps `frameOps`
        // non-empty). Asserting presence rules out a regression where the scene-frame function
        // is silently dropped when no genre splice fires.
        assertTrue(
            frameBody.isNotEmpty(),
            "gameplay_frame body must be extractable via brace-walk from bank1.c even when " +
                "the visitCamera gate is off — the user-sentinel RawOp keeps frameOps " +
                "non-empty. bank1.c head:\n${bank1C.take(2000)}",
        )

        // The splice MUST NOT have fired — `platformer_camera_update();` is the distinctive
        // discriminator. Its absence inside the gameplay_frame body locks the gate's opt-IN
        // behaviour. A regression that fires the gate unconditionally would leak this token
        // into the negative fixture and fail here.
        assertFalse(
            frameBody.contains("platformer_camera_update();"),
            "camera_update call splice leaked into non-tilemap-collision fixture — " +
                "visitCamera gate broken (should be OFF when solidThreshold is null). " +
                "gameplay_frame body:\n${frameBody.take(4000)}",
        )
    }
}
