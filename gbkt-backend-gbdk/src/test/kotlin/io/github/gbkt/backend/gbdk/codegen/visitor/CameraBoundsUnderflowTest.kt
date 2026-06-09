/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.emit.CEmitter
import io.github.gbkt.core.ir.CameraSystem
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// CAMERA BOUNDS UNDERFLOW TESTS — Plan 07.4-21
//
// Locks the invariant that visitCameraSystem MUST clamp `maxX` and `maxY` to
// non-negative values, even when the bound (zone) is smaller than the GB
// screen (160 wide / 144 tall).
//
// Without this guard the literal `boundsWidth - 160` (or `boundsHeight - 144`)
// flows into a CTernary that ultimately casts to UINT8, producing wraparound
// values like SCX_REG = 248 / SCY_REG = 212 and pushing the BG off-screen.
//
// Root cause was surfaced by racer's auto-synthesized 19x19-tile zone
// (152x152 px → maxX = -8 → UINT8(-8) = 248). See:
//   .planning/phases/07.4-sport-genre-codegen-fix-inserted/07.4-UAT.md
//   secondary issue 1.
//
// Per architecture decision D-02 (no game-specific special cases), the fix
// lives in the GENERIC camera codegen — ALL CameraSystems benefit, including
// future puzzle/RPG zones that happen to be smaller than the screen.
//
// Test matrix:
//   #1 sub-screen X  — boundsWidth=152, boundsHeight=152 → maxX=-8  RED at HEAD
//   #2 sub-screen Y  — boundsWidth=200, boundsHeight=100 → maxY=-44 RED at HEAD
//      (W-4 closure: racer's 152x152 has maxY=+8 which would NOT be RED on the
//       Y axis, so we use 100 here to make the Y-axis check genuinely fail
//       at HEAD — confirming the fix applies to BOTH axes.)
//   #3 exact-screen  — boundsWidth=160, boundsHeight=144 → maxX/Y=0  PASS
//   #4 supra-screen  — boundsWidth=256, boundsHeight=256 → maxX=96/maxY=112 PASS
//   #5 no-bounds     — null/null → no clamp branch                  PASS
// =============================================================================

class CameraBoundsUnderflowTest {

    private val emptyGameIR = GameIR(name = "Test", config = CartridgeConfig())

    /** Build update_camera_<id>() body and emit it as a single C-text blob. */
    private fun emitCameraBody(system: CameraSystem): String {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val functions = visitor.visitCameraSystem(system)
        return functions.first().body.joinToString("\n") { CEmitter.emitStatement(it) }
    }

    // =========================================================================
    // TEST 1 — sub-screen X: boundsWidth=152 → maxX must clamp to 0 (NOT -8)
    // =========================================================================

    @Test
    fun `sub_screen_zone_clamps_maxX_to_zero`() {
        // Racer's actual numbers: 19x19 tiles = 152x152 px. boundsWidth - 160 = -8.
        val system =
            CameraSystem(
                id = "camera",
                followActorId = "hero",
                boundsWidth = 152,
                boundsHeight = 152,
            )
        val body = emitCameraBody(system)

        // RED at HEAD: body contains the negative maxX comparison `> -8`.
        // GREEN after Plan 07.4-21: body must NOT contain `> -8`.
        assertFalse(
            body.contains("> -8"),
            "Plan 07.4-21: visitCameraSystem must clamp maxX = max(0, boundsWidth - 160). " +
                "When boundsWidth=152, maxX must be 0 — not -8 (which UINT8-underflows to 248 " +
                "and pushes the BG off-screen). Body:\n$body",
        )
        // After fix, the X-axis clamp's upper-bound comparison should be `> 0`
        // (rawX > 0 ? 0 : rawX), so the literal `> 0` MUST appear at least once.
        assertTrue(
            body.contains("> 0"),
            "Plan 07.4-21: post-fix body should contain a `> 0` upper-bound comparison " +
                "for the X axis (maxX clamped to 0). Body:\n$body",
        )
    }

    // =========================================================================
    // TEST 2 — sub-screen Y: boundsHeight=100 → maxY must clamp to 0 (NOT -44)
    // (W-4 closure: bounds chosen so Y is GENUINELY negative at HEAD)
    // =========================================================================

    @Test
    fun `sub_screen_zone_clamps_maxY_to_zero`() {
        // boundsHeight=100 → maxY = 100 - 144 = -44 (genuinely negative — RED at HEAD).
        // boundsWidth=200 → maxX = 200 - 160 = +40 (positive, irrelevant for this test).
        val system =
            CameraSystem(
                id = "camera",
                followActorId = "hero",
                boundsWidth = 200,
                boundsHeight = 100,
            )
        val body = emitCameraBody(system)

        // RED at HEAD: body contains the negative maxY comparison `> -44`.
        // GREEN after Plan 07.4-21: body must NOT contain `> -44`.
        assertFalse(
            body.contains("> -44"),
            "Plan 07.4-21: visitCameraSystem must clamp maxY = max(0, boundsHeight - 144). " +
                "When boundsHeight=100, maxY must be 0 — not -44 (which UINT8-underflows to 212 " +
                "and pushes the BG off-screen). Body:\n$body",
        )
        // The Y-axis upper-bound comparison `(rawY > 0 ? 0 : rawY)` must appear post-fix.
        // (Note: `> 0` may also appear from the X-axis path; that's fine — we just need
        // to know the Y-axis no longer carries a `> -44` literal.)
        assertTrue(
            body.contains("> 0"),
            "Plan 07.4-21: post-fix body should contain a `> 0` upper-bound comparison " +
                "for the Y axis (maxY clamped to 0). Body:\n$body",
        )
    }

    // =========================================================================
    // TEST 3 — exact-screen: boundsWidth=160, boundsHeight=144 → maxX/maxY=0 (back-compat)
    // =========================================================================

    @Test
    fun `exact_screen_zone_uses_zero_max`() {
        // 160 - 160 = 0; 144 - 144 = 0. Already correct at HEAD; PASSES both pre- and post-fix.
        val system =
            CameraSystem(
                id = "camera",
                followActorId = "hero",
                boundsWidth = 160,
                boundsHeight = 144,
            )
        val body = emitCameraBody(system)

        // Body must contain `> 0` upper-bound comparison (the existing zero-max path).
        assertTrue(
            body.contains("> 0"),
            "Plan 07.4-21 back-compat: exact-screen zone (160x144) should already emit " +
                "a `> 0` clamp on both axes. Body:\n$body",
        )
        // And must NOT contain any negative-literal upper bound.
        assertFalse(
            body.contains("> -"),
            "Plan 07.4-21 back-compat: exact-screen zone must never produce a negative " +
                "upper-bound clamp. Body:\n$body",
        )
    }

    // =========================================================================
    // TEST 4 — supra-screen: boundsWidth=256, boundsHeight=256 → maxX=96, maxY=112 unchanged
    // =========================================================================

    @Test
    fun `supra_screen_zone_unchanged`() {
        // 256 - 160 = 96; 256 - 144 = 112. The fix is a no-op for this case.
        val system =
            CameraSystem(
                id = "camera",
                followActorId = "hero",
                boundsWidth = 256,
                boundsHeight = 256,
            )
        val body = emitCameraBody(system)

        assertTrue(
            body.contains("> 96"),
            "Plan 07.4-21 back-compat: supra-screen zone must keep maxX=96 upper bound. " +
                "Body:\n$body",
        )
        assertTrue(
            body.contains("> 112"),
            "Plan 07.4-21 back-compat: supra-screen zone must keep maxY=112 upper bound. " +
                "Body:\n$body",
        )
    }

    // =========================================================================
    // TEST 5 — no bounds: null/null → no clamp-branch (back-compat)
    // =========================================================================

    @Test
    fun `no_bounds_unchanged`() {
        // No bounds → no upper-bound CTernary path. Only the lower `< 0` and the
        // shake-timer path remain. Therefore no `> N` literal where N is a maxX/maxY value.
        val system = CameraSystem(id = "camera", followActorId = "hero")
        val body = emitCameraBody(system)

        // The no-bounds branch only emits `(rawX < 0 ? 0 : rawX)` and
        // `(rawY < 0 ? 0 : rawY)`. The bounds-branch upper comparisons (e.g. `> 96`,
        // `> 112`, `> -8`) MUST be absent.
        assertFalse(
            body.contains("> 96"),
            "Plan 07.4-21 back-compat: no-bounds branch must not emit upper-bound clamp. " +
                "Body:\n$body",
        )
        assertFalse(
            body.contains("> 112"),
            "Plan 07.4-21 back-compat: no-bounds branch must not emit upper-bound clamp. " +
                "Body:\n$body",
        )
        assertFalse(
            body.contains("> -"),
            "Plan 07.4-21 back-compat: no-bounds branch must never produce negative literal. " +
                "Body:\n$body",
        )
    }
}
