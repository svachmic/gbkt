/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

// =============================================================================
// Phase 13.3 Plan 13.3-20 — COLOR axis of GAP-1
//
// Source of the all-S_PAL(0) contract:
//   - evidence/13.3-DIAGNOSTIC.md step (b) "Full fix requires also rewriting the
//     descriptor indices": png2asset bakes absolute S_PAL slot indices from the
//     source PNG's palette membership (elephant.c carries S_PAL(0)×97 AND
//     S_PAL(1)×52). The S_PAL(1) entries select OBJ slot 1 (scene pink_pal) →
//     pink tiles. The only in-pipeline fix is a deterministic post-png2asset
//     rewrite of every S_PAL(1) → S_PAL(0).
//   - 13.3-CONTEXT.md "LOCKED: elephant COLOR target — uniform gray" (D-19):
//     every OAM entry must select OBJ slot 0 (gray_pal) so the elephant renders
//     uniform gray with ZERO pink at rest (rot=0).
//
// This test drives the NEW file-scope `internal fun remapMetaspriteSubPalette(
// outputC: File)` helper added in Task 2. It is RED until that helper exists.
// =============================================================================

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MetaspriteSubPaletteRemapTest {

    @TempDir lateinit var tempDir: File

    /** Count non-overlapping occurrences of [token] in [this]. */
    private fun String.tokenCount(token: String): Int = split(token).size - 1

    /**
     * An elephant.c-shaped fixture mirroring png2asset metasprite output: a tile-data
     * array, per-frame METASPR_ITEM descriptor arrays carrying S_PAL(0) and S_PAL(1)
     * indices, the _metasprite pointer array, and a _palettes[] array. N = 3 S_PAL(0),
     * M = 2 S_PAL(1).
     */
    private fun elephantShapedFixture(): String =
        """
        #pragma bank 1

        #include <gbdk/platform.h>
        #include <gbdk/metasprites.h>

        BANKREF(elephant)

        const uint8_t elephant_tiles[] = {
            0x00,0xFF,0x18,0x18,0x3C,0x3C,0x7E,0x7E,
            0xFF,0x00,0x81,0x81,0xC3,0xC3,0xE7,0xE7
        };

        const metasprite_t elephant_metasprite0[] = {
            METASPR_ITEM(0, 0, 0, S_PAL(0)),
            METASPR_ITEM(0, 8, 1, S_PAL(1)),
            METASPR_ITEM(8, 0, 2, S_PAL(0)),
            METASPR_ITEM(8, 8, 3, S_PAL(1)),
            METASPR_ITEM(16, 0, 4, S_PAL(0)),
            METASPR_TERM
        };

        const metasprite_t* const elephant_metasprites[] = {
            elephant_metasprite0
        };

        const palette_color_t elephant_palettes[] = {
            RGB8(80,80,80), RGB8(160,160,160), RGB8(255,128,200), RGB8(0,0,0)
        };
        """.trimIndent()

    // -------------------------------------------------------------------------
    // Test 1 — every S_PAL(1) becomes S_PAL(0); count invariant N+M preserved.
    // -------------------------------------------------------------------------
    @Test
    fun `remap rewrites every S_PAL(1) to S_PAL(0) preserving total count`() {
        val fixture = elephantShapedFixture()
        val n = fixture.tokenCount("S_PAL(0)")
        val m = fixture.tokenCount("S_PAL(1)")
        assertEquals(3, n, "fixture precondition: 3 S_PAL(0)")
        assertEquals(2, m, "fixture precondition: 2 S_PAL(1)")

        val outputC = File(tempDir, "sprites/elephant.c")
        outputC.parentFile.mkdirs()
        outputC.writeText(fixture)

        remapMetaspriteSubPalette(outputC)

        val result = outputC.readText()
        assertEquals(0, result.tokenCount("S_PAL(1)"),
            "D-19: ZERO S_PAL(1) tokens must remain after remap")
        assertEquals(n + m, result.tokenCount("S_PAL(0)"),
            "every S_PAL(1) is remapped to S_PAL(0): expected ${n + m} S_PAL(0)")
    }

    // -------------------------------------------------------------------------
    // Test 2 — only the S_PAL(1) token changes; the rest of the body is
    // byte-identical (no tile-data, METASPR x/y/tile, pointer-array, or
    // _palettes[] corruption — guards T-13.3-01 over-broad rewrite).
    // -------------------------------------------------------------------------
    @Test
    fun `remap touches only the S_PAL index leaving the rest byte-identical`() {
        val fixture = elephantShapedFixture()
        val outputC = File(tempDir, "sprites/elephant.c")
        outputC.parentFile.mkdirs()
        outputC.writeText(fixture)

        remapMetaspriteSubPalette(outputC)
        val result = outputC.readText()

        // The ONLY difference between fixture and result is S_PAL(1) → S_PAL(0).
        // Normalize both by collapsing the S_PAL index, then assert equality:
        // tile bytes, METASPR_ITEM x/y/tile fields, pointer arrays, _palettes[]
        // all unchanged.
        val normalizedFixture = fixture.replace("S_PAL(1)", "S_PAL(0)")
        assertEquals(normalizedFixture, result,
            "remap must change ONLY the S_PAL(1)->S_PAL(0) token; the rest of the " +
                "descriptor (tile data, x/y/tile, pointer arrays, _palettes) must be byte-identical")
    }

    // -------------------------------------------------------------------------
    // Test 3 — idempotent: running twice yields the same content as running once.
    // -------------------------------------------------------------------------
    @Test
    fun `remap is idempotent`() {
        val fixture = elephantShapedFixture()
        val outputC = File(tempDir, "sprites/elephant.c")
        outputC.parentFile.mkdirs()
        outputC.writeText(fixture)

        remapMetaspriteSubPalette(outputC)
        val afterOnce = outputC.readText()
        remapMetaspriteSubPalette(outputC)
        val afterTwice = outputC.readText()

        assertEquals(afterOnce, afterTwice, "remap must be idempotent")
        assertEquals(0, afterTwice.tokenCount("S_PAL(1)"))
    }

    // -------------------------------------------------------------------------
    // Test 4 — regression guard: a fixture with NO S_PAL(1) (already all-S_PAL(0),
    // e.g. an actor-sprite-shaped string) is returned byte-identical (no spurious
    // rewrites). Guards T-13.3-02 leakage to non-pink descriptors.
    // -------------------------------------------------------------------------
    @Test
    fun `remap leaves an all-S_PAL(0) descriptor byte-identical`() {
        val actorShaped =
            """
            const metasprite_t paddle_metasprite0[] = {
                METASPR_ITEM(0, 0, 0, S_PAL(0)),
                METASPR_ITEM(0, 8, 1, S_PAL(0)),
                METASPR_TERM
            };
            """.trimIndent()
        val outputC = File(tempDir, "sprites/paddle.c")
        outputC.parentFile.mkdirs()
        outputC.writeText(actorShaped)

        remapMetaspriteSubPalette(outputC)

        assertEquals(actorShaped, outputC.readText(),
            "a descriptor with no S_PAL(1) must be returned byte-identical")
        assertTrue(outputC.readText().tokenCount("S_PAL(1)") == 0)
    }
}
