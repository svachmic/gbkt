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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for [MetaspriteVisitor.generateMetaspriteDescriptor] — the sub-area B method that emits
 * METASPRITE_DEF per-frame OAM arrays + pointer table in C.
 *
 * **Reference C shape (post Phase 10.1 Plan 05 / CR-03 namespacing):**
 *
 * ```c
 * const metasprite_t sprite_elephant_frame_0[] = {
 *     {0, 0, 0}, {0, 8, 1}, ..., {metasprite_end}
 * };
 * const metasprite_t* const sprite_elephant_frames[] = {
 *     sprite_elephant_frame_0,
 *     ...
 * };
 * ```
 *
 * Symbols are namespaced by the metasprite id (here `elephant`) so two metasprites in the same game
 * emit non-colliding symbols. Prior to CR-03 closure the names were the unnamespaced
 * `sprite_metasprite_N` / `sprite_metaspriteS`-style pointer table, which caused linker "duplicate
 * definition" errors when ≥2 metasprites coexisted.
 *
 * GBDK METASPRITE_DEF convention: `{int8_t dy, dx; uint8_t dtile}` — Y offset comes FIRST.
 */
class MetaspriteVisitorDescriptorTest {

    // =========================================================================
    // TEST 1: Single-frame metasprite — descriptor array + pointer table
    // =========================================================================
    @Test
    fun `generateMetaspriteDescriptor single frame emits frame array with sentinel and pointer table`() {
        val metasprite =
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

        val result = MetaspriteVisitor.generateMetaspriteDescriptor(metasprite)

        assertIs<CRawCode>(result, "generateMetaspriteDescriptor must return CRawCode")

        val text = result.code

        // Per-frame array declaration (CR-03: namespaced by metasprite id "elephant")
        assertTrue(
            text.contains("const metasprite_t sprite_elephant_frame_0[]"),
            "Expected 'const metasprite_t sprite_elephant_frame_0[]' in:\n$text",
        )

        // GBDK sentinel
        assertTrue(
            text.contains("{metasprite_end}"),
            "Expected '{metasprite_end}' sentinel in:\n$text",
        )

        // Pointer table (CR-03: namespaced by metasprite id "elephant")
        assertTrue(
            text.contains("const metasprite_t* const sprite_elephant_frames[]"),
            "Expected 'const metasprite_t* const sprite_elephant_frames[]' in:\n$text",
        )

        // Pointer table references the frame array
        assertTrue(
            text.contains("sprite_elephant_frame_0"),
            "Expected pointer table to reference 'sprite_elephant_frame_0' in:\n$text",
        )
    }

    // =========================================================================
    // TEST 2: Two-frame metasprite — both frame arrays + correct pointer table
    // =========================================================================
    @Test
    fun `generateMetaspriteDescriptor two frames emits both frame arrays and pointer table referencing both`() {
        val metasprite =
            MetaspriteIR(
                id = "elephant",
                frames =
                    listOf(
                        MetaspriteFrame(
                            tiles = listOf(MetaspriteTile(relX = 0, relY = 0, tileId = 0))
                        ),
                        MetaspriteFrame(
                            tiles = listOf(MetaspriteTile(relX = 0, relY = 0, tileId = 2))
                        ),
                    ),
            )

        val result = MetaspriteVisitor.generateMetaspriteDescriptor(metasprite)

        assertIs<CRawCode>(result)

        val text = result.code

        // Both frame arrays must be present (CR-03: namespaced by metasprite id "elephant")
        assertTrue(
            text.contains("const metasprite_t sprite_elephant_frame_0[]"),
            "Expected frame 0 array in:\n$text",
        )
        assertTrue(
            text.contains("const metasprite_t sprite_elephant_frame_1[]"),
            "Expected frame 1 array in:\n$text",
        )

        // Pointer table references both
        assertTrue(
            text.contains("sprite_elephant_frame_0") && text.contains("sprite_elephant_frame_1"),
            "Expected pointer table to reference both frames in:\n$text",
        )

        // Pointer table itself (CR-03: namespaced by metasprite id "elephant")
        assertTrue(
            text.contains("const metasprite_t* const sprite_elephant_frames[]"),
            "Expected pointer table declaration in:\n$text",
        )
    }

    // =========================================================================
    // TEST 3: Coordinate order — GBDK METASPRITE_DEF is {dy, dx, dtile} (Y first)
    // Tile(relX=8, relY=0, tileId=1) must emit {0, 8, 1} — dy=0, dx=8, dtile=1
    // =========================================================================
    @Test
    fun `generateMetaspriteDescriptor emits struct literals in GBDK dy-dx-dtile order (Y first)`() {
        val metasprite =
            MetaspriteIR(
                id = "elephant",
                frames =
                    listOf(
                        MetaspriteFrame(
                            tiles = listOf(MetaspriteTile(relX = 8, relY = 0, tileId = 1))
                        )
                    ),
            )

        val result = MetaspriteVisitor.generateMetaspriteDescriptor(metasprite)

        assertIs<CRawCode>(result)

        val text = result.code

        // dy=relY=0, dx=relX=8, dtile=1 — order is {dy, dx, dtile}
        assertTrue(
            text.contains("{0, 8, 1}"),
            "Expected tile struct '{0, 8, 1}' (dy=0, dx=8, dtile=1) in:\n$text",
        )
    }
}
