/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CRawCode
import io.github.gbkt.core.ir.MetaspriteFrame
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.MetaspriteTile
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for [MetaspriteVisitor.generateMetaspriteFrameSwitch] — sub-area C method that emits the
 * per-frame switch on `_rot & 0x3u` selecting the correct `move_metasprite_*` variant.
 *
 * **Plan 10.1-09 (WR-05 / SEED-011) update:** the per-call `uint8_t hiwater = 0u;` declaration AND
 * the trailing `hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES);` were HOISTED out of this
 * method's emission and into the SCENE FRAME function prelude/postlude by
 * `GBDKPipeline.wrapFrameWithMetaspriteHiwater`. Pre-fix the per-call wrap RESET the OAM cursor
 * when a frame called moveMetasprite() more than once, causing the second metasprite to clobber the
 * first metasprite's OAM allocation. The hoisted shape ensures EXACTLY ONE hiwater init + EXACTLY
 * ONE hide_sprites_range call per frame, regardless of metasprite count. Per-frame invariant locked
 * by `Seed011HiwaterFrameScopeTest` (3 tests with awk brace-walk).
 *
 * Reference C shape (post-Plan 10.1-09 — per-call emission, the outer hiwater + tail
 * hide_sprites_range now live in the frame function wrap):
 * ```c
 * {
 *     uint8_t subpal = _rot >> 2;
 *     _elephant_subPalette = subpal;
 *     _elephant_flipX = (_rot & 0x3u) >> 0u;
 *     _elephant_flipY = (_rot & 0x3u) >> 1u;
 *     switch (_rot & 0x3u) {
 *         case 1:
 *             hiwater += move_metasprite_flipy(...);   // references outer-scope hiwater
 *             break;
 *         case 2:
 *             hiwater += move_metasprite_flipxy(...);
 *             break;
 *         case 3:
 *             hiwater += move_metasprite_flipx(...);
 *             break;
 *         default:
 *             hiwater += move_metasprite_ex(...);
 *             break;
 *     }
 * }
 * ```
 */
class MetaspriteVisitorFrameSwitchTest {

    private val elephantMetasprite =
        MetaspriteIR(
            id = "elephant",
            frames =
                listOf(
                    MetaspriteFrame(
                        tiles =
                            listOf(
                                MetaspriteTile(relX = 0, relY = 0, tileId = 0),
                                MetaspriteTile(relX = 8, relY = 0, tileId = 1),
                            )
                    )
                ),
        )

    // =========================================================================
    // TEST 1: Return type must be CRawCode (typed C AST has no native switch)
    // =========================================================================
    @Test
    fun `generateMetaspriteFrameSwitch returns CRawCode`() {
        val result = MetaspriteVisitor.generateMetaspriteFrameSwitch(elephantMetasprite)
        assertIs<CRawCode>(result, "generateMetaspriteFrameSwitch must return CRawCode")
    }

    // =========================================================================
    // TEST 2: Hiwater variable declaration ABSENT (Plan 10.1-09 / WR-05 hoist)
    //
    // Pre-Plan-10.1-09 the per-call switch block opened with
    // `uint8_t hiwater = 0u;` which RESET the OAM cursor every time a scene
    // called moveMetasprite() — the second metasprite then clobbered the
    // first metasprite's OAM slots. Plan 10.1-09 hoisted the declaration to
    // the scene frame function prelude via
    // `GBDKPipeline.wrapFrameWithMetaspriteHiwater`. The per-call emission
    // now references the outer-scope `hiwater` via `hiwater += move_metasprite_*`
    // contributions but no longer DECLARES it.
    //
    // Defect-closure rename per Plan 05 precedent — the test name+expectation
    // flip in the same commit as the codegen fix (SUMMARY § "test renaming
    // was the right answer"). Per-frame `EXACTLY ONE hiwater = 0` invariant
    // is locked by Seed011HiwaterFrameScopeTest.
    // =========================================================================
    @Test
    fun `generateMetaspriteFrameSwitch no longer declares hiwater after Plan 10_1-09 hoist`() {
        val result = MetaspriteVisitor.generateMetaspriteFrameSwitch(elephantMetasprite)
        val text = result.code
        assertFalse(
            text.contains("uint8_t hiwater = 0"),
            "Plan 10.1-09 (WR-05) hoisted the per-call `uint8_t hiwater = 0u;` declaration " +
                "out of generateMetaspriteFrameSwitch and into the scene frame function " +
                "prelude (GBDKPipeline.wrapFrameWithMetaspriteHiwater). Per-call declaration " +
                "RESET the OAM cursor between moveMetasprite() calls in the same frame. " +
                "Found unexpected per-call declaration in:\n$text",
        )
        // Positive contract: per-case `hiwater += move_metasprite_*` contributions remain —
        // the inner block references the outer function-scope hiwater added by the wrap.
        assertTrue(
            text.contains("hiwater += move_metasprite_"),
            "Expected per-case `hiwater += move_metasprite_*` contributions to remain " +
                "(they reference the outer function-scope hiwater declared by the Plan 10.1-09 " +
                "wrap). Found in:\n$text",
        )
    }

    // =========================================================================
    // TEST 3: Subpal extraction _rot >> 2
    // =========================================================================
    @Test
    fun `generateMetaspriteFrameSwitch emits subpal extraction from _rot`() {
        val result = MetaspriteVisitor.generateMetaspriteFrameSwitch(elephantMetasprite)
        val text = result.code
        assertTrue(text.contains("_rot >> 2"), "Expected '_rot >> 2' subpal extraction in:\n$text")
    }

    // =========================================================================
    // TEST 4: Switch condition on _rot & 0x3u (or _rot & 3u / 0x3)
    // =========================================================================
    @Test
    fun `generateMetaspriteFrameSwitch emits switch on _rot and 0x3 mask`() {
        val result = MetaspriteVisitor.generateMetaspriteFrameSwitch(elephantMetasprite)
        val text = result.code
        assertTrue(
            text.contains("_rot & 0x3") || text.contains("_rot & 3"),
            "Expected switch on '_rot & 0x3' or '_rot & 3' in:\n$text",
        )
    }

    // =========================================================================
    // TEST 5: All 4 move_metasprite_* variants present
    // =========================================================================
    @Test
    fun `generateMetaspriteFrameSwitch emits move_metasprite_flipy case`() {
        val result = MetaspriteVisitor.generateMetaspriteFrameSwitch(elephantMetasprite)
        assertTrue(
            result.code.contains("move_metasprite_flipy"),
            "Expected 'move_metasprite_flipy' in:\n${result.code}",
        )
    }

    @Test
    fun `generateMetaspriteFrameSwitch emits move_metasprite_flipxy case`() {
        val result = MetaspriteVisitor.generateMetaspriteFrameSwitch(elephantMetasprite)
        assertTrue(
            result.code.contains("move_metasprite_flipxy"),
            "Expected 'move_metasprite_flipxy' in:\n${result.code}",
        )
    }

    @Test
    fun `generateMetaspriteFrameSwitch emits move_metasprite_flipx case`() {
        val result = MetaspriteVisitor.generateMetaspriteFrameSwitch(elephantMetasprite)
        assertTrue(
            result.code.contains("move_metasprite_flipx"),
            "Expected 'move_metasprite_flipx' in:\n${result.code}",
        )
    }

    @Test
    fun `generateMetaspriteFrameSwitch emits move_metasprite_ex default case`() {
        val result = MetaspriteVisitor.generateMetaspriteFrameSwitch(elephantMetasprite)
        assertTrue(
            result.code.contains("move_metasprite_ex"),
            "Expected 'move_metasprite_ex' in:\n${result.code}",
        )
    }

    // =========================================================================
    // TEST 6: hide_sprites_range tail cleanup ABSENT (Plan 10.1-09 / WR-05 hoist)
    //
    // Pre-Plan-10.1-09 the per-call switch block ended with
    // `hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES);` — the second
    // moveMetasprite() call's tail hide then clobbered the first metasprite's
    // OAM slots. Plan 10.1-09 hoisted this call to the scene frame function
    // postlude via `GBDKPipeline.wrapFrameWithMetaspriteHiwater`. Per-frame
    // `EXACTLY ONE hide_sprites_range` invariant is locked by
    // Seed011HiwaterFrameScopeTest. Pitfall 1 mitigation (variable-length frame
    // ghost sprites) is preserved at the frame-function scope; this method no
    // longer carries the responsibility per-call.
    // =========================================================================
    @Test
    fun `generateMetaspriteFrameSwitch no longer calls hide_sprites_range after Plan 10_1-09 hoist`() {
        val result = MetaspriteVisitor.generateMetaspriteFrameSwitch(elephantMetasprite)
        val text = result.code
        assertFalse(
            text.contains("hide_sprites_range"),
            "Plan 10.1-09 (WR-05) hoisted `hide_sprites_range(hiwater, MAX_HARDWARE_SPRITES);` " +
                "out of generateMetaspriteFrameSwitch and into the scene frame function " +
                "postlude (GBDKPipeline.wrapFrameWithMetaspriteHiwater). Per-call tail " +
                "cleanup clobbered OAM slots written by prior moveMetasprite() calls in the " +
                "same frame. Found unexpected per-call hide_sprites_range in:\n$text",
        )
    }

    // =========================================================================
    // TEST 7: Canonical variable names _idx, _rot, _posX, _posY present
    // =========================================================================
    @Test
    fun `generateMetaspriteFrameSwitch references canonical variable names _idx _rot _posX _posY`() {
        val result = MetaspriteVisitor.generateMetaspriteFrameSwitch(elephantMetasprite)
        val text = result.code
        assertTrue(text.contains("_idx"), "Expected '_idx' variable reference in:\n$text")
        assertTrue(text.contains("_rot"), "Expected '_rot' variable reference in:\n$text")
        assertTrue(text.contains("_posX"), "Expected '_posX' variable reference in:\n$text")
        assertTrue(text.contains("_posY"), "Expected '_posY' variable reference in:\n$text")
    }

    // =========================================================================
    // TEST 8: DEVICE_SPRITE_PX_OFFSET_X + (_posX >> 4) appears for x-coord arg
    // =========================================================================
    @Test
    fun `generateMetaspriteFrameSwitch emits DEVICE_SPRITE_PX_OFFSET_X expression for x arg`() {
        val result = MetaspriteVisitor.generateMetaspriteFrameSwitch(elephantMetasprite)
        val text = result.code
        assertTrue(
            text.contains("DEVICE_SPRITE_PX_OFFSET_X") && text.contains("_posX >> 4"),
            "Expected 'DEVICE_SPRITE_PX_OFFSET_X + (_posX >> 4)' pattern in:\n$text",
        )
    }

    // =========================================================================
    // TEST 9: DEVICE_SPRITE_PX_OFFSET_Y + (_posY >> 4) appears for y-coord arg
    // =========================================================================
    @Test
    fun `generateMetaspriteFrameSwitch emits DEVICE_SPRITE_PX_OFFSET_Y expression for y arg`() {
        val result = MetaspriteVisitor.generateMetaspriteFrameSwitch(elephantMetasprite)
        val text = result.code
        assertTrue(
            text.contains("DEVICE_SPRITE_PX_OFFSET_Y") && text.contains("_posY >> 4"),
            "Expected 'DEVICE_SPRITE_PX_OFFSET_Y + (_posY >> 4)' pattern in:\n$text",
        )
    }

    // =========================================================================
    // TEST 10: sprite_<id>_frames[<idxVar>] pointer lookup present
    //   (CR-03 namespacing post Phase 10.1 Plan 05: descriptor pointer table is
    //    `sprite_elephant_frames` not `sprite_metasprites`; idx var is the canonical
    //    `_idx` fallback because this call uses no parameter overrides.)
    // =========================================================================
    @Test
    fun `generateMetaspriteFrameSwitch emits sprite_elephant_frames array index lookup`() {
        val result = MetaspriteVisitor.generateMetaspriteFrameSwitch(elephantMetasprite)
        val text = result.code
        assertTrue(
            text.contains("sprite_elephant_frames[_idx]"),
            "Expected 'sprite_elephant_frames[_idx]' in:\n$text",
        )
    }
}
