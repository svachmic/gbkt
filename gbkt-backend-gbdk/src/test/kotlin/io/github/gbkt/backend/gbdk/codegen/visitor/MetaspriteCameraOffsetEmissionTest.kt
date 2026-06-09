/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.backend.gbdk.codegen.pipeline.PipelineOutput
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.MetaspriteFrame
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.MetaspriteTile
import io.github.gbkt.core.ir.MoveMetasprite
import io.github.gbkt.core.ir.SceneIR
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// R4 INVARIANT — MetaspriteVisitor.cameraOffsetX screen-relative vs absolute
//                 emission lock (Phase 12.3 Plan 06 acceptance)
//
// Plan 12.3-06 added the `cameraOffsetX: String? = null` 5th parameter to
// `MetaspriteVisitor.generateMetaspriteFrameSwitch`. When NON-null, the
// emission shape inside the 4-case switch becomes the screen-relative formula
//
//   DEVICE_SPRITE_PX_OFFSET_X + (UINT8)(((INT16)(_posX >> 4)) - (INT16)_camera_x)
//
// When NULL (the back-compat default — D-08), the emission stays byte-identical
// to pre-Phase-12.3:
//
//   DEVICE_SPRITE_PX_OFFSET_X + (_posX >> 4)
//
// `ScriptOpVisitor.derivePlatformerCameraOffsetX` (Phase 12.3 Plan 06 §Step 2)
// reflectively probes the GameIR for (a) tilemap-collision (via explicit
// `tilemap_collision` GenericSystem, or `platformer_physics.solidThreshold`,
// or per-zone `platformerPhysicsOverride`) AND (b) a `platformer_camera`
// GenericSystem whose `cameraConfig.mode.name == "SMOOTH_FOLLOW"` AND
// `cameraConfig.scrollDirections.name == "HORIZONTAL"`. Only when BOTH gates
// fire does it return `"_camera_x"`; otherwise it returns null, and the
// MetaspriteVisitor falls through to the absolute formula.
//
// This test locks BOTH branches:
//
//  • POSITIVE: a platformer-camera GameIR fixture (platformer_physics with
//    solidThreshold + platformer_camera with HORIZONTAL + SMOOTH_FOLLOW)
//    carries a metasprite and a MoveMetasprite op in the `gameplay` scene
//    frame. The extracted `void gameplay_frame` body MUST contain
//    `_camera_x` (the unique-to-screen-relative token).
//
//  • NEGATIVE (D-08 back-compat): a non-platformer GameIR fixture (NO
//    platformer systems, just a metasprite + a MoveMetasprite op in a
//    `gameplay` scene frame) MUST emit the absolute formula. The extracted
//    `void gameplay_frame` body MUST contain the `>> 4)` right-shift (the
//    absolute-formula `_posX >> 4` token) AND MUST NOT contain `_camera_x`
//    anywhere — the null-cameraOffsetX path was correctly taken.
//
// **Test residence (owner-module convention):** This test lives in
// `gbkt-backend-gbdk` (the module that owns both `MetaspriteVisitor` and
// `ScriptOpVisitor.derivePlatformerCameraOffsetX`). The platformer-camera
// fixture is built from raw `GenericSystem` IR nodes with `Map<String, Any>`
// configs — gbkt-backend-gbdk does NOT depend on gbkt-genre-platformer (per
// `gbkt-backend-gbdk/CLAUDE.md` §Dependencies and per the reflection-based
// derivePlatformerCameraOffsetX layering invariant from Plan 12.3-06). So the
// fixture cannot import `PlatformerPhysicsConfig` / `PlatformerCameraConfig`
// from gbkt-genre-platformer. Instead, we hand-build the opaque `Any`
// payloads with the exact reflective shape the helper expects:
//   - `solidThreshold` field on the physicsConfig (any non-null value)
//   - `mode` enum field with `.name == "SMOOTH_FOLLOW"` on the cameraConfig
//   - `scrollDirections` enum field with `.name == "HORIZONTAL"` on the
//     cameraConfig
// This is the layering-respecting analog of HorizontalScrollEmissionTest's
// gbkt-genre-platformer-local fixture, expressed without the platformer
// genre dependency. The reflection helper cannot tell the difference — it
// reads field names + Enum.name strings only.
//
// **D-08 back-compat (zero modifications to Phase 10 metasprites test):**
// `gbkt-examples/metasprites/.../MetaspriteEmissionTest.kt` (Phase 10.1) MUST
// stay GREEN with ZERO diffs. The negative case below is a Plan-12.3-local
// fixture; the metasprites example's own emission tests continue to run
// independently and remain the authoritative back-compat regression guard.
//
// **Evidence-before-assert (Plan 12-09 convention inherited):** Both tests
// write the extracted frame body to disk BEFORE any assertion fires, so a
// RED run still produces a reviewable artifact under `evidence/tier1-shape/`.
//
// **Scope-level grep gate (CLAUDE.md §"Scope-level grep gates"):** A
// file-level `bank1C.contains("_camera_x")` would false-positive — `_camera_x`
// is also declared as a global at the top of main.c by visitCamera. The
// brace-walk extracts the `gameplay_frame` body so the substring checks fire
// ONLY against tokens inside that scene-frame function — never against the
// file-scope declaration or any other function.
// =============================================================================

class MetaspriteCameraOffsetEmissionTest {

    companion object {
        /**
         * Evidence is written under the **active checkout root** (worktree-safe). Same shape as
         * `JumpHoldEmissionTest.EVIDENCE_DIR` / `HorizontalScrollEmissionTest.EVIDENCE_DIR` — see
         * those comments for the worktree path-safety rationale (#3099). For
         * `:gbkt-backend-gbdk:test`, `user.dir` resolves to `<repo>/gbkt-backend-gbdk`; we ascend
         * one level to the worktree root, then descend into the phase evidence directory.
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
     * **L-12.3 (per-test-file duplication):** This helper is copied verbatim from
     * `JumpHoldEmissionTest.kt:114-134`. The established convention for emission tests is per-file
     * duplication rather than a shared utility module — keeps each test file self-contained for
     * isolated reading and avoids cross-module test-utility dependencies.
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
     * Locate the scene-frame body. Single-scene games may fold the scene functions into HOME
     * (main.c) via `BankingAnalysisPass`'s single-scene fast-path; multi-scene or non-fast-path
     * games place them in `bank1.c`. We try `bank1.c` first, then `main.c`, mirroring the same
     * pattern as `MetaspriteEmissionTest.playFrameBody()` (Phase 10.1).
     */
    private fun extractGameplayFrame(output: PipelineOutput): String {
        val sources =
            listOfNotNull(
                output.files["bank1.c"],
                output.files["main.c"],
            )
        for (src in sources) {
            val body = extractFunctionBody(src, "void gameplay_frame")
            if (body.isNotEmpty()) return body
        }
        return ""
    }

    /**
     * Opaque physicsConfig stand-in. The reflection helper only reads `solidThreshold` via
     * `javaClass.getDeclaredField("solidThreshold")`, so any class with a non-null
     * `solidThreshold` field will trip Path A of `derivePlatformerCameraOffsetX` step 1.
     *
     * NOT a shared production type — we deliberately avoid importing
     * `gbkt-genre-platformer.PlatformerPhysicsConfig` because gbkt-backend-gbdk does NOT depend on
     * gbkt-genre-platformer (the dep would be cyclic — see Plan 12.3-06 §"Layering invariant" and
     * `gbkt-backend-gbdk/CLAUDE.md` §Dependencies). This local data class is byte-compatible with
     * the reflection probe, which is the only thing that matters for the codegen contract.
     */
    private data class FakePhysicsConfig(val solidThreshold: Int?)

    /**
     * Opaque cameraConfig stand-in. The reflection helper reads `mode` and `scrollDirections` as
     * `Enum<*>` via `javaClass.getDeclaredField(...)` then matches on `.name`. We pass real Kotlin
     * enums whose `.name` values match the production-side `CameraScrollMode.SMOOTH_FOLLOW.name`
     * and `ScrollDirection.HORIZONTAL.name` exactly. See the rationale on [FakePhysicsConfig].
     */
    private data class FakeCameraConfig(
        val mode: FakeCameraMode,
        val scrollDirections: FakeScrollDirection,
    )

    private enum class FakeCameraMode {
        SMOOTH_FOLLOW,
        SCREEN_LOCK,
    }

    private enum class FakeScrollDirection {
        HORIZONTAL,
        VERTICAL,
        BOTH,
        NONE,
    }

    /**
     * Positive fixture — tilemap-collision active (via `platformer_physics.solidThreshold`) AND
     * `platformer_camera` with HORIZONTAL + SMOOTH_FOLLOW. A `player` metasprite is registered
     * with binders, and `MoveMetasprite` runs in the `gameplay` scene's frame block.
     *
     * Reflection trip path (Plan 12.3-06 §Step 1 Path A + Step 2):
     *  - `gameIR.systems[platformer_physics].config["physicsConfig"]` is a [FakePhysicsConfig]
     *    with `solidThreshold = 17` → Path A fires → tilemap-collision active.
     *  - `gameIR.systems[platformer_camera].config["cameraConfig"]` is a [FakeCameraConfig] with
     *    `mode = SMOOTH_FOLLOW` and `scrollDirections = HORIZONTAL` → Step 2 fires.
     *  - `derivePlatformerCameraOffsetX(gameIR)` returns `"_camera_x"` → MetaspriteVisitor emits
     *    the screen-relative formula.
     */
    private fun buildPositiveGameIR(): GameIR {
        val playerMetasprite =
            MetaspriteIR(
                id = "player",
                frames = listOf(MetaspriteFrame(tiles = listOf(MetaspriteTile(0, 0, 0)))),
                posXVarName = "playerX",
                posYVarName = "playerY",
                idxVarName = "walkFrameIdx",
                rotVarName = "playerRot",
            )
        val physicsSystem =
            GenericSystem(
                id = "plat-physics",
                config =
                    mapOf(
                        "type" to "platformer_physics",
                        "physicsConfig" to FakePhysicsConfig(solidThreshold = 17),
                    ),
            )
        val cameraSystem =
            GenericSystem(
                id = "plat-camera",
                config =
                    mapOf(
                        "type" to "platformer_camera",
                        "cameraConfig" to
                            FakeCameraConfig(
                                mode = FakeCameraMode.SMOOTH_FOLLOW,
                                scrollDirections = FakeScrollDirection.HORIZONTAL,
                            ),
                    ),
            )
        val gameplayScene =
            SceneIR(
                id = "gameplay",
                frameOps =
                    listOf(
                        MoveMetasprite(
                            metaspriteId = "player",
                            posXVar = "playerX",
                            posYVar = "playerY",
                            idxVar = "walkFrameIdx",
                            rotVar = "playerRot",
                        )
                    ),
            )
        return GameIR(
            name = "PositiveCameraOffsetGame",
            config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY),
            scenes = listOf(gameplayScene),
            metasprites = listOf(playerMetasprite),
            systems = listOf(physicsSystem, cameraSystem),
            startScene = "gameplay",
        )
    }

    /**
     * Negative (D-08 back-compat) fixture — a single metasprite + a MoveMetasprite op in the
     * `gameplay` scene's frame. NO platformer systems registered (no `platformer_physics`, no
     * `platformer_camera`, no `tilemap_collision`, no zones with platformerPhysicsOverride). The
     * reflection helper's step 1 and step 2 BOTH miss; `derivePlatformerCameraOffsetX` returns
     * null → MetaspriteVisitor emits the absolute formula.
     *
     * The scene id matches the positive fixture (`gameplay`) so the extraction logic is
     * symmetric across both test cases.
     */
    private fun buildNegativeGameIR(): GameIR {
        val elephant =
            MetaspriteIR(
                id = "elephant",
                frames = listOf(MetaspriteFrame(tiles = listOf(MetaspriteTile(0, 0, 0)))),
                // No binders — falls back to canonical _posX/_posY/_idx/_rot globals.
            )
        val gameplayScene =
            SceneIR(
                id = "gameplay",
                frameOps =
                    listOf(
                        MoveMetasprite(metaspriteId = "elephant")
                    ),
            )
        return GameIR(
            name = "NegativeBackCompatGame",
            config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY),
            scenes = listOf(gameplayScene),
            metasprites = listOf(elephant),
            // NO systems — derivePlatformerCameraOffsetX returns null on the first guard.
            startScene = "gameplay",
        )
    }

    // -------------------------------------------------------------------------
    // POSITIVE — tilemap-camera mode emits screen-relative X formula
    //
    // Production mechanism (Plan 12.3-06): when reflection-based gates fire,
    // `ScriptOpVisitor.visitMoveMetasprite` passes `cameraOffsetX = "_camera_x"`
    // to `MetaspriteVisitor.generateMetaspriteFrameSwitch`. The 4-case switch
    // body's `xExpr` becomes
    //   DEVICE_SPRITE_PX_OFFSET_X + (UINT8)(((INT16)(_playerX >> 4)) - (INT16)_camera_x)
    // which is referenced uniformly across all 4 flip-variant case branches.
    //
    // The binding token is `_camera_x` — it is unique to the screen-relative
    // branch (the absolute branch never references the camera global). The
    // surrounding `(INT16)` casts are a secondary positive marker; per the
    // plan's L-12.2 note, a future SDCC warning-158/207 cleanup might drop the
    // explicit casts to `(UINT8)((_playerX >> 4) - _camera_x)`. In that case
    // the `_camera_x` assertion stays valid; only the secondary `(INT16)` /
    // `_camera_x` cast assertion needs to track the codegen edit.
    // -------------------------------------------------------------------------

    @Test
    fun positive_tilemapCamera_emits_screen_relative_X_formula() {
        EVIDENCE_DIR.mkdirs()

        val output = pipeline.generate(buildPositiveGameIR())
        val frameBody = extractGameplayFrame(output)

        // Evidence-before-assert: persist the body to disk BEFORE assertions fire so a RED run
        // still produces a reviewable artifact on disk.
        File(EVIDENCE_DIR, "gameplay_frame_metasprite_positive.c").writeText(frameBody)

        assertTrue(
            frameBody.isNotEmpty(),
            "gameplay_frame body could not be extracted from bank1.c or main.c — the pipeline " +
                "produced no scene-frame function. Pipeline output files: ${output.files.keys}",
        )

        // Primary binding token: `_camera_x` is unique to the screen-relative branch. Its
        // presence inside the gameplay_frame body is the contract that the cameraOffsetX
        // parameter was wired correctly by `ScriptOpVisitor.derivePlatformerCameraOffsetX` →
        // `MetaspriteVisitor.generateMetaspriteFrameSwitch`. A regression that drops the
        // reflection probe (e.g. mistypes a config field name) would emit the absolute formula
        // here and this assertion would catch it.
        assertTrue(
            frameBody.contains("_camera_x"),
            "Screen-relative X formula MISSING — `_camera_x` not found inside gameplay_frame body. " +
                "Expected `cameraOffsetX = \"_camera_x\"` to thread from " +
                "ScriptOpVisitor.derivePlatformerCameraOffsetX → " +
                "MetaspriteVisitor.generateMetaspriteFrameSwitch when both reflection gates " +
                "fire (platformer_physics.solidThreshold non-null AND platformer_camera mode/" +
                "scrollDirections match). gameplay_frame body:\n${frameBody.take(4000)}",
        )

        // Secondary marker: the INT16 cast that wraps the screen-relative subtraction. Per Plan
        // 12.3-06 §What Shipped the canonical emission is
        //   `(UINT8)(((INT16)(_playerX >> 4)) - (INT16)_camera_x)`.
        // We assert `(INT16)` (the cast token) AND `_camera_x` co-occur (the latter is already
        // covered above; this clause locks the cast shape that mitigates the C11 unsigned-
        // promotion bug for level positions wider than 256 px).
        //
        // L-12.2: if Plan 12.3-15 ROM smoke test trips on SDCC warnings 158/207 from the
        // explicit casts and the codegen is loosened to `(UINT8)((_playerX >> 4) - _camera_x)`,
        // this assertion needs to be loosened in the same commit. The primary `_camera_x`
        // assertion above is the load-bearing one and survives any cast-form change.
        assertTrue(
            frameBody.contains("(INT16)"),
            "Secondary marker MISSING — `(INT16)` cast token not found inside gameplay_frame " +
                "body. Plan 12.3-06's canonical emission wraps the subtraction in `(INT16)` to " +
                "prevent C11 unsigned-promotion errors for camera positions > 256 px. If Plan " +
                "12.3-15 ROM smoke fixed SDCC warnings 158/207 by dropping the explicit casts, " +
                "loosen this assertion in the same commit. gameplay_frame body:\n" +
                frameBody.take(4000),
        )
    }

    // -------------------------------------------------------------------------
    // NEGATIVE — D-08 back-compat — absolute formula on non-platformer fixture
    //
    // Production mechanism (Plan 12.3-06 D-08): when the GameIR has NO
    // platformer systems, `derivePlatformerCameraOffsetX` returns null on its
    // first Step-1 guard. `ScriptOpVisitor.visitMoveMetasprite` then threads
    // `cameraOffsetX = null` through to MetaspriteVisitor, which emits the
    // absolute formula `DEVICE_SPRITE_PX_OFFSET_X + (_posX >> 4)` — byte-
    // identical to pre-Phase-12.3 emission.
    //
    // The binding contract: `_camera_x` MUST NOT appear anywhere inside the
    // gameplay_frame body. A regression that accidentally fires the cameraOffsetX
    // path (e.g. defaulting to "_camera_x" instead of null) would leak the
    // global reference into a non-platformer game's frame function — and the
    // compile would FAIL at link time because non-platformer games never
    // declare `_camera_x`. This test catches that BEFORE the SDCC link
    // failure surfaces in ROM smoke.
    //
    // The positive marker `>> 4)` is the right-shift inside the absolute
    // formula. It also appears in the screen-relative branch (inside the
    // `(INT16)(_playerX >> 4)` sub-expression), so it is NOT a discriminator
    // between the two branches — but it IS a sanity-check that the metasprite
    // call body emitted at all (a malformed visitor that emitted nothing
    // would not contain the right-shift either).
    // -------------------------------------------------------------------------

    @Test
    fun negative_back_compat_no_camera_offset_for_non_platformer_fixture() {
        EVIDENCE_DIR.mkdirs()

        val output = pipeline.generate(buildNegativeGameIR())
        val frameBody = extractGameplayFrame(output)

        // Evidence-before-assert: persist the body to disk BEFORE assertions fire.
        File(EVIDENCE_DIR, "gameplay_frame_metasprite_negative.c").writeText(frameBody)

        assertTrue(
            frameBody.isNotEmpty(),
            "gameplay_frame body could not be extracted from bank1.c or main.c — the pipeline " +
                "produced no scene-frame function. Pipeline output files: ${output.files.keys}",
        )

        // Positive marker: the absolute formula `DEVICE_SPRITE_PX_OFFSET_X + (_posX >> 4)`
        // contains the right-shift `>> 4)`. We assert SOMETHING was emitted (defends against a
        // regression that produced an empty frame body or omitted the move_metasprite call).
        assertTrue(
            frameBody.contains(">> 4)"),
            "Absolute X formula MISSING — `>> 4)` right-shift not found inside gameplay_frame " +
                "body. The non-platformer fixture should still emit the move_metasprite call " +
                "with `_posX >> 4` (the pre-Phase-12.3 byte-identical formula). " +
                "gameplay_frame body:\n${frameBody.take(4000)}",
        )

        // Load-bearing back-compat lock: `_camera_x` MUST NOT appear inside the gameplay_frame
        // body of a non-platformer fixture. This is the D-08 invariant — when reflection-based
        // gates DON'T fire, the cameraOffsetX path must remain null and the screen-relative
        // formula MUST NOT leak into byte-identical emissions. A failure here means
        // `derivePlatformerCameraOffsetX` returned a non-null string when it shouldn't have
        // (likely a bug in the reflection guards), which would break every non-platformer ROM
        // (pong / breakout / banks / metasprites) at SDCC link time with `Undefined identifier
        // '_camera_x'`.
        assertFalse(
            frameBody.contains("_camera_x"),
            "D-08 BACK-COMPAT BROKEN — `_camera_x` leaked into a non-platformer fixture's " +
                "gameplay_frame body. The reflection guards in " +
                "`ScriptOpVisitor.derivePlatformerCameraOffsetX` must return null when no " +
                "platformer systems are present. If this assertion is RED, every non-platformer " +
                "ROM (pong / breakout / banks / metasprites) will fail SDCC link with " +
                "`Undefined identifier '_camera_x'`. gameplay_frame body:\n" +
                frameBody.take(4000),
        )
    }
}
