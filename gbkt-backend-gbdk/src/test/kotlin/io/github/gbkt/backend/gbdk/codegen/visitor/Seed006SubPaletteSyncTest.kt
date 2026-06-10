/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.core.ir.MetaspriteFrame
import io.github.gbkt.core.ir.MetaspriteIR
import io.github.gbkt.core.ir.MetaspriteTile
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phase 10.1 Plan 04 — SEED-006 (D-V3) + IN-01 closure.
 *
 * Verifies that `MetaspriteVisitor.generateMetaspriteFrameSwitch` emits assignments to the
 * `_<id>_subPalette`, `_<id>_flipX`, and `_<id>_flipY` globals (declared in
 * `GBDKPipeline.kt:790-797`) so MCP sym-file reads reflect runtime state.
 *
 * The original visitor only declared a local `uint8_t subpal = _rot >> 2;` — never wrote to any
 * global. D-V3 (sub-palette) and D-12 "both" option (flipX/flipY for IN-01) are closed at the same
 * site with 3 buf.append lines emitted right after the subpal computation.
 *
 * Reference: `.planning/seeds/SEED-006-metasprites-subpalette-global-not-synced.md` Reference:
 * `.planning/phases/10.1-metasprites-surplus-codegen-defects-inserted/10.1-04-PLAN.md`
 */
class Seed006SubPaletteSyncTest {

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
    // TEST 1 (D-V3): _<id>_subPalette global assignment present in frame switch
    // =========================================================================
    @Test
    fun `frame_switch_emits_subPalette_assignment`() {
        val result = MetaspriteVisitor.generateMetaspriteFrameSwitch(elephantMetasprite)
        val text = result.code
        assertTrue(
            text.contains("_elephant_subPalette = subpal;"),
            "Expected '_elephant_subPalette = subpal;' (D-V3 sub-palette global sync) in:\n$text",
        )
    }

    // =========================================================================
    // TEST 2 (D-12 / IN-01): _<id>_flipX + _<id>_flipY global assignments present
    // =========================================================================
    @Test
    fun `frame_switch_emits_flipX_and_flipY_assignments`() {
        val result = MetaspriteVisitor.generateMetaspriteFrameSwitch(elephantMetasprite)
        val text = result.code
        assertTrue(
            text.contains("_elephant_flipX = (_rot & 0x3u) >> 0u;"),
            "Expected '_elephant_flipX = (_rot & 0x3u) >> 0u;' (D-12/IN-01 flipX global sync) in:\n$text",
        )
        assertTrue(
            text.contains("_elephant_flipY = (_rot & 0x3u) >> 1u;"),
            "Expected '_elephant_flipY = (_rot & 0x3u) >> 1u;' (D-12/IN-01 flipY global sync) in:\n$text",
        )
    }

    // =========================================================================
    // TEST 3 (regression guard): existing subpal local computation preserved
    // =========================================================================
    @Test
    fun `frame_switch_preserves_existing_subpal_computation`() {
        val result = MetaspriteVisitor.generateMetaspriteFrameSwitch(elephantMetasprite)
        val text = result.code
        assertTrue(
            text.contains("uint8_t subpal = _rot >> 2;"),
            "Expected existing 'uint8_t subpal = _rot >> 2;' local to remain in:\n$text",
        )
    }
}
