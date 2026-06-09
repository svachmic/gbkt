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
import io.github.gbkt.genre.platformer.domain.CameraScrollMode
import io.github.gbkt.genre.platformer.domain.PlatformerCameraConfig
import io.github.gbkt.genre.platformer.domain.PlatformerPhysicsConfig
import io.github.gbkt.genre.platformer.domain.ScrollDirection
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// D-16 INVARIANT #3 — platformer_camera_update column-scroll shape lock
//
// Plan 12-11 emits a HOME-bank `void platformer_camera_update(void)` whose body,
// when the triple-condition gate fires (gameUsesTilemapCollision == true AND
// cfg.scrollDirections == HORIZONTAL AND cfg.mode == SMOOTH_FOLLOW), follows the
// column-by-column tilemap-scroll shape mirrored from `platformer_template/src/
// camera.c` lines 56-83:
//
//   move_bkg(_camera_x, 0u);
//   _map_pos_x = (UINT8)(_camera_x >> 3u);
//   if (_map_pos_x != _old_map_pos_x) {
//       if (_camera_x < _old_camera_x) {
//           _bkg_set_level_submap_banked(_map_pos_x + 1u, 0u, 1u, DEVICE_SCREEN_HEIGHT);
//       } else if ((_current_level_width_in_tiles - DEVICE_SCREEN_WIDTH) > _map_pos_x) {
//           _bkg_set_level_submap_banked(_map_pos_x + DEVICE_SCREEN_WIDTH, 0u, 1u, DEVICE_SCREEN_HEIGHT);
//       }
//       _old_map_pos_x = _map_pos_x;
//   }
//   _old_camera_x = _camera_x;
//
// VALIDATION.md §Per-Anchor Verification Map row 3 binds the awk pattern:
//
//   awk '/^void platformer_camera_update/{p=1;d=0} p{d+=gsub(/{/,"");
//        d-=gsub(/}/,""); if(d<0)exit} p' bank1.c | grep 'set_bkg_submap'
//
// Note: VALIDATION.md row 3 names `bank1.c`, but per Plan 12-11 SUMMARY §"Next
// Phase Readiness" and the PlatformerVisitor.buildTilemapCameraUpdateFunction
// `CFunction(isBanked = false)` choice, the function actually lands in main.c
// (HOME bank). This test reads main.c. The awk SHAPE — not the file name — is
// the binding contract: per-function brace-walk extraction so the substring
// checks fire ONLY inside `platformer_camera_update`, never on tokens that may
// appear in unrelated functions in the same file.
//
// CLAUDE.md §"Scope-level grep gates" forbids a file-level `mainC.contains(...)`
// here because `move_bkg` and `_bkg_set_level_submap_banked` also appear in
// unrelated functions (the standard smooth-follow camera body also calls
// `move_bkg`, and the HOME helper `_bkg_set_level_submap_banked` is itself
// defined elsewhere in main.c). The brace-walk extracts the camera body so
// substring checks fire ONLY against tokens inside the camera function.
//
// Both tests are deliberately structural — they lock the emission SHAPE, not
// behaviour at runtime. Runtime evidence for horizontal scroll (anchor 3) is
// the paired UAT screenshot under evidence/uat-screenshots/, captured later
// in the phase by the UAT plans. This JVM tier guarantees the codegen
// prerequisite; the visual tier confirms the helper actually runs.
// =============================================================================

class HorizontalScrollEmissionTest {

    companion object {
        /**
         * Evidence is written under the **active checkout root** (worktree-safe). Same shape as
         * `TilemapCollisionEmissionTest.EVIDENCE_DIR` — see the comment there for the worktree
         * path-safety rationale (#3099). For `:gbkt-genre-platformer:test`, `user.dir` resolves
         * to `<repo>/gbkt-genre-platformer`; we ascend one level to the worktree root, then
         * descend into the phase evidence directory.
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
     * [functionSignaturePrefix] (e.g. `void platformer_camera_update`) until the matching closing
     * brace at depth zero.
     *
     * Mirror of the helper in `TilemapCollisionEmissionTest.kt` (Plan 12-09). The returned blob
     * includes the signature line and the closing brace, so downstream `.contains()` checks
     * operate ONLY on tokens that live inside the named function — never on tokens from
     * unrelated functions in the same file (per CLAUDE.md §"Scope-level grep gates").
     *
     * Matching is anchored to the START of a line (the prefix must appear at column 0) so
     * occurrences inside string literals, comments, or argument lists of a different function
     * cannot false-match. This is the literal counterpart of awk's `/^prefix/` anchor.
     *
     * Kotlin-side mirror of VALIDATION.md row 3:
     *
     * ```
     * awk '/^void platformer_camera_update/{p=1;d=0} p{d+=gsub(/{/,"");
     *      d-=gsub(/}/,""); if(d<0)exit} p'
     * ```
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
     * Build a minimal GameIR carrying BOTH a `platformer_physics` GenericSystem (whose
     * `physicsConfig` controls `solidThreshold` — the tilemap-collision gate) AND a
     * `platformer_camera` GenericSystem (whose `cameraConfig` controls the column-scroll
     * triple-condition gate via `scrollDirections` + `mode`).
     *
     * When [solidThreshold] is non-null, `gameUsesTilemapCollision()` returns true → the
     * camera fork's first gate condition fires. Combined with [scrollDirections] == HORIZONTAL
     * and [mode] == SMOOTH_FOLLOW (the default cameraConfig used here), all three gate
     * conditions match and the column-scroll branch emits inside `platformer_camera_update`.
     *
     * When [solidThreshold] is null, the gate stays OFF and the abstract smooth-follow camera
     * body is emitted instead (the negative case).
     */
    private fun buildTilemapCameraGameIR(
        solidThreshold: Int?,
        scrollDirections: ScrollDirection = ScrollDirection.HORIZONTAL,
        mode: CameraScrollMode = CameraScrollMode.SMOOTH_FOLLOW,
        id: String = "plat",
    ): GameIR {
        val physicsConfig =
            PlatformerPhysicsConfig(
                gravity = 2,
                jumpForce = 8,
                terminalVelocity = 12,
                solidThreshold = solidThreshold,
            )
        val cameraConfig =
            PlatformerCameraConfig(
                mode = mode,
                scrollDirections = scrollDirections,
            )
        val physicsSystem =
            GenericSystem(
                id = "$id-physics",
                config = mapOf("type" to "platformer_physics", "physicsConfig" to physicsConfig),
            )
        val cameraSystem =
            GenericSystem(
                id = "$id-camera",
                config = mapOf("type" to "platformer_camera", "cameraConfig" to cameraConfig),
            )
        return GameIR(
            name = "TestTilemapCameraGame",
            config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2),
            scenes = listOf(SceneIR(id = "gameplay")),
            systems = listOf(physicsSystem, cameraSystem),
            startScene = "gameplay",
        )
    }

    // -------------------------------------------------------------------------
    // POSITIVE — platformer_camera_update emits the column-scroll body when
    // tilemap-collision is on AND camera is HORIZONTAL SMOOTH_FOLLOW.
    //
    // Production mechanism (Plan 12-11 — PlatformerVisitor.buildTilemapCamera
    // UpdateFunction): when the triple-condition gate fires (Plan 12-11 SUMMARY
    // §"Decisions Made" — Camera-fork triple-condition gate), the camera body
    // emits:
    //   - move_bkg(_camera_x, 0u)
    //   - _map_pos_x = (UINT8)(_camera_x >> 3u)
    //   - `if (_map_pos_x != _old_map_pos_x) { ... _bkg_set_level_submap_banked
    //     (...) ... }`
    //   - _old_camera_x = _camera_x
    //
    // Scope-level grep gate (CLAUDE.md §"Scope-level grep gates" corollary): a
    // file-level `mainC.contains("_bkg_set_level_submap_banked")` would false-
    // positive on the helper DEFINITION itself (Plan 12-10 emits the HOME-bank
    // helper into main.c). The brace-walk extracts the camera body so the
    // substring checks fire ONLY against tokens inside platformer_camera_update.
    // -------------------------------------------------------------------------

    @Test
    fun `platformer_camera_update emits column-scroll body when tilemap-collision on`() {
        val gameIR = buildTilemapCameraGameIR(solidThreshold = 17)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()

        // Signature anchor — function declaration starts at column 0 of main.c with the literal
        // text `void platformer_camera_update` per Plan 12-11's emission contract
        // (CFunction(isBanked = false) → HOME bank → main.c). This is the awk
        // `/^void platformer_camera_update/` anchor expressed in Kotlin.
        val signatureRegex = Regex("^void platformer_camera_update", RegexOption.MULTILINE)
        val signatureFound = signatureRegex.containsMatchIn(mainC)

        // Evidence-before-assert: extract and persist the camera body BEFORE any assertion fires
        // so a RED run still produces a reviewable artifact on disk (per the
        // evidence-before-assert pattern from Plan 12-09 TilemapCollisionEmissionTest).
        val cameraBody = extractFunctionBody(mainC, "void platformer_camera_update")
        File(EVIDENCE_DIR, "platformer_camera_update.c").writeText(cameraBody)

        assertTrue(
            signatureFound,
            "platformer_camera_update declaration must start with 'void platformer_camera_update' at " +
                "column 0 of main.c (Plan 12-11 awk-brace-walk extraction contract). main.c head:\n" +
                mainC.take(2000),
        )
        assertTrue(
            cameraBody.isNotEmpty(),
            "platformer_camera_update body must be extractable via brace-walk from main.c. " +
                "main.c head:\n${mainC.take(2000)}",
        )

        // move_bkg — the camera-scroll call that flushes the scroll register every frame. Both
        // the column-scroll branch (Plan 12-11 — `move_bkg(_camera_x, 0u)`) AND the abstract
        // smooth-follow body (Plan 06.8-10 — `move_bkg(_cam_x, _cam_y)`) emit a `move_bkg(` call,
        // so this assertion locks that SOMETHING flushes the scroll register. The specific
        // column-scroll discriminator is `_bkg_set_level_submap_banked` below.
        assertTrue(
            cameraBody.contains("move_bkg("),
            "platformer_camera_update body must call move_bkg(...) every frame to flush the " +
                "scroll register (Plan 12-11 column-scroll shape). camera body:\n" +
                cameraBody.take(4000),
        )

        // _bkg_set_level_submap_banked — the column-scroll discriminator. This helper (Plan
        // 12-10) is called ONLY by the tilemap-camera branch (Plan 12-11), so its presence
        // INSIDE the camera function scope is the binding signal that the column-scroll branch
        // fired. At least 2 occurrences expected (left + right edge calls — Plan 12-11 SUMMARY
        // §Task 2 step "Two conditional _bkg_set_level_submap_banked(...) calls").
        val helperCallCount = cameraBody.split("_bkg_set_level_submap_banked").size - 1
        assertTrue(
            helperCallCount >= 2,
            "platformer_camera_update body must contain at least 2 _bkg_set_level_submap_banked " +
                "calls (Plan 12-11 emits left-edge + right-edge column redraw). " +
                "Found $helperCallCount. camera body:\n${cameraBody.take(4000)}",
        )

        // _map_pos_x != _old_map_pos_x — the column-change guard expression. Locks the
        // canonical Plan 12-11 shape where the column redraw fires ONLY when the integer-tile
        // column changes (i.e. _camera_x crossed an 8-pixel boundary). The reference
        // platformer_template/src/camera.c uses the same shape verbatim. A regression that
        // drops this guard would force a column redraw every frame (visible flicker) or skip
        // redraws entirely (visible tile-edge garbage).
        //
        // Accept both orderings (`A != B` and `B != A`) since the codegen could legitimately
        // emit either. We don't lock the specific operand order, only that the guard
        // expression exists somewhere inside the camera body.
        val guardForward = cameraBody.contains("_map_pos_x != _old_map_pos_x")
        val guardReverse = cameraBody.contains("_old_map_pos_x != _map_pos_x")
        assertTrue(
            guardForward || guardReverse,
            "platformer_camera_update body must contain the column-change guard " +
                "`_map_pos_x != _old_map_pos_x` (or reverse ordering) — locks the Plan 12-11 " +
                "guard shape that fires column redraw only on 8-pixel boundary crossings. " +
                "camera body:\n${cameraBody.take(4000)}",
        )

        // _camera_x — the UINT16 camera-position global declared by Plan 12-10 (visitCamera
        // emits `_camera_x` as UINT16). Locks that the column-scroll branch reads the wide
        // UINT16 position rather than the abstract path's INT8 `_cam_x`. A regression that
        // accidentally swaps `_camera_x` ↔ `_cam_x` would silently overflow on levels wider
        // than 256 px (RESEARCH §Pitfall 3).
        assertTrue(
            cameraBody.contains("_camera_x"),
            "platformer_camera_update body must reference _camera_x (UINT16, tilemap-camera " +
                "global from Plan 12-10) — locks the wide-position read. camera body:\n" +
                cameraBody.take(4000),
        )
        assertTrue(
            cameraBody.contains("_old_camera_x"),
            "platformer_camera_update body must reference _old_camera_x (UINT16, tilemap-camera " +
                "global from Plan 12-10) — locks the previous-frame position store. camera " +
                "body:\n${cameraBody.take(4000)}",
        )

        // _map_pos_x / _old_map_pos_x — the UINT8 tile-column globals declared by Plan 12-10.
        // Both must be present inside the function scope for the column-change guard to mean
        // anything. The brace-walk extraction guarantees no false-positive from the file-scope
        // declaration site (which lives at top of main.c, outside any function).
        assertTrue(
            cameraBody.contains("_map_pos_x"),
            "platformer_camera_update body must reference _map_pos_x (UINT8, tile-column global " +
                "from Plan 12-10). camera body:\n${cameraBody.take(4000)}",
        )
        assertTrue(
            cameraBody.contains("_old_map_pos_x"),
            "platformer_camera_update body must reference _old_map_pos_x (UINT8, previous " +
                "tile-column global from Plan 12-10). camera body:\n${cameraBody.take(4000)}",
        )
    }

    // -------------------------------------------------------------------------
    // NEGATIVE — gate verification. When solidThreshold is unset, no column-
    // scroll emission inside platformer_camera_update.
    //
    // Production mechanism (Plan 12-11 — gameUsesTilemapCollision returns FALSE):
    // the triple-condition gate's FIRST condition fails. The early-return fork
    // at the top of buildCameraUpdateFunction falls through to the abstract
    // smooth-follow body. The column-scroll branch emits zero references to
    // `_bkg_set_level_submap_banked` inside `platformer_camera_update`.
    //
    // This sentinel locks the opt-IN nature of the feature. A regression that
    // accidentally fires the gate unconditionally (e.g. dropping the predicate
    // check) would emit the column-scroll body for ALL platformer games — even
    // those without tilemap data — breaking the byte-identical regression
    // invariant for non-tilemap games (Pong, Breakout, banks, etc.) which the
    // Plan 12-11 SUMMARY §"Next Phase Readiness" guarantees.
    //
    // The function `platformer_camera_update` SHOULD still exist (the abstract
    // path emits it regardless) — we assert its presence AND assert the column-
    // scroll discriminator is absent from its body.
    // -------------------------------------------------------------------------

    @Test
    fun `platformer_camera_update omits column-scroll body when tilemap-collision off`() {
        val gameIR = buildTilemapCameraGameIR(solidThreshold = null)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        EVIDENCE_DIR.mkdirs()

        // The function MUST still exist (abstract smooth-follow path emits it regardless of
        // the gate). Asserting presence rules out a regression that accidentally drops the
        // camera function entirely when the gate is off.
        val signatureRegex = Regex("^void platformer_camera_update", RegexOption.MULTILINE)
        assertTrue(
            signatureRegex.containsMatchIn(mainC),
            "platformer_camera_update must still be emitted when solidThreshold is unset — " +
                "the abstract smooth-follow path is the fall-through. main.c head:\n" +
                mainC.take(2000),
        )

        val cameraBody = extractFunctionBody(mainC, "void platformer_camera_update")
        // Persist negative-case evidence too — useful for confirming the abstract body shape
        // visually when reviewing a RED run.
        File(EVIDENCE_DIR, "platformer_camera_update_abstract.c").writeText(cameraBody)

        assertTrue(
            cameraBody.isNotEmpty(),
            "platformer_camera_update body must be extractable via brace-walk from main.c " +
                "even when the column-scroll gate is off. main.c head:\n${mainC.take(2000)}",
        )

        // The column-scroll discriminator MUST be absent from the function scope. The Plan
        // 12-10 helper `_bkg_set_level_submap_banked` is also NOT emitted at all (Plan 12-10
        // §"single shared gate"), but we verify here at the FUNCTION-SCOPE level — this is
        // the binding contract for D-16 invariant #3's negative gate.
        assertFalse(
            cameraBody.contains("_bkg_set_level_submap_banked"),
            "platformer_camera_update body must NOT call _bkg_set_level_submap_banked when " +
                "solidThreshold is unset — column-scroll branch is gated OFF. camera body:\n" +
                cameraBody.take(4000),
        )

        // Defence-in-depth: the column-change guard and the wide UINT16 _camera_x global must
        // also NOT appear inside the function scope when the gate is off. The abstract
        // smooth-follow body uses INT8 `_cam_x` / `_cam_y`, not the tilemap-camera UINT16
        // globals. If the gate flipped on accidentally, BOTH the guard expression AND the
        // wide-position read would leak into the abstract emission — this catches that.
        assertFalse(
            cameraBody.contains("_map_pos_x != _old_map_pos_x") ||
                cameraBody.contains("_old_map_pos_x != _map_pos_x"),
            "platformer_camera_update body must NOT contain the column-change guard when " +
                "solidThreshold is unset. camera body:\n${cameraBody.take(4000)}",
        )
    }
}
