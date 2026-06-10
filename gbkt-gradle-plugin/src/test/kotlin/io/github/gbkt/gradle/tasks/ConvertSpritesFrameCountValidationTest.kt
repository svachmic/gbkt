/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

// =============================================================================
// Phase 13.3 Plan 02 Task 2: ConvertSpritesFrameCountValidationTest — D-07
//
// Encodes the D-07 contract: when the author-declared frame count (from the
// `frames(N)` DSL call, threaded through MetaspriteIR.frameCount and the sidecar)
// disagrees with the frame count parsed from png2asset's output .c file, the build
// must fail loudly naming both counts.
//
// D-07 specification:
//   - `validateFrameCount(declared=5, png2assetOutput, stemName="elephant")` where
//     png2assetOutput contains `elephant_metasprites[5]` → passes (no exception)
//   - `validateFrameCount(declared=3, png2assetOutput, stemName="elephant")` where
//     png2assetOutput contains `elephant_metasprites[5]` → throws with message
//     containing both "3" and "5" (declared vs actual counts)
//   - When png2assetOutput does NOT contain `<stemName>_metasprites[N]` → the
//     helper either passes (unknown count, no strict validation) or throws with
//     a descriptive parse-failure message
//
// RED reason: `validateFrameCount` does not yet exist in ConvertSpritesTask.kt.
// This test fails to compile (unresolved reference). Plan 13.3-06 adds the
// helper as an `internal fun validateFrameCount(...)` at file scope in
// ConvertSpritesTask.kt (mirroring the `generateSpriteHeader` visibility pattern
// used by ConvertSpritesHeaderPaletteExternTest).
//
// Parse strategy (D-07 RESEARCH G2): regex `<stemName>_metasprites\[(\d+)\]`
// captures the explicit array size from the png2asset pointer-array declaration.
// This is O(1) and robust — png2asset always emits an explicit `[N]` size.
// =============================================================================

class ConvertSpritesFrameCountValidationTest {

    // -------------------------------------------------------------------------
    // Inline fixtures — representative png2asset .c output fragments
    // -------------------------------------------------------------------------

    /** Fragment from a png2asset-generated .c file with 5 animation frames. */
    private val elephant5FrameOutput =
        """
        const metasprite_t elephant_metasprite0[] = {
            METASPR_ITEM(0, 0, 0, 0), METASPR_TERM
        };
        const metasprite_t elephant_metasprite1[] = {
            METASPR_ITEM(0, 0, 1, 0), METASPR_TERM
        };
        const metasprite_t elephant_metasprite2[] = {
            METASPR_ITEM(0, 0, 2, 0), METASPR_TERM
        };
        const metasprite_t elephant_metasprite3[] = {
            METASPR_ITEM(0, 0, 3, 0), METASPR_TERM
        };
        const metasprite_t elephant_metasprite4[] = {
            METASPR_ITEM(0, 0, 4, 0), METASPR_TERM
        };
        const metasprite_t* const elephant_metasprites[5] = {
            elephant_metasprite0, elephant_metasprite1, elephant_metasprite2,
            elephant_metasprite3, elephant_metasprite4
        };
        """
            .trimIndent()

    /** Fragment with 3 frames (mismatch scenario). */
    private val tiger3FrameOutput =
        """
        const metasprite_t tiger_metasprite0[] = { METASPR_ITEM(0, 0, 0, 0), METASPR_TERM };
        const metasprite_t tiger_metasprite1[] = { METASPR_ITEM(0, 0, 1, 0), METASPR_TERM };
        const metasprite_t tiger_metasprite2[] = { METASPR_ITEM(0, 0, 2, 0), METASPR_TERM };
        const metasprite_t* const tiger_metasprites[3] = {
            tiger_metasprite0, tiger_metasprite1, tiger_metasprite2
        };
        """
            .trimIndent()

    /**
     * Fragment with no pointer array (png2asset output that lacks the metasprites[] declaration).
     */
    private val noPointerArrayOutput =
        """
        const metasprite_t elephant_metasprite0[] = { METASPR_ITEM(0, 0, 0, 0), METASPR_TERM };
        /* pointer array line is absent */
        """
            .trimIndent()

    // -------------------------------------------------------------------------
    // Test 1: Matching declared count → passes (no exception)
    //
    // Author declares `frames(5)` in the DSL; png2asset emits
    // `elephant_metasprites[5]`. Counts agree → build continues.
    //
    // RED: unresolved reference to validateFrameCount. Plan 13.3-06 adds it.
    // -------------------------------------------------------------------------
    @Test
    fun `validateFrameCount passes when declared count matches png2asset output count`() {
        // Must not throw — declared 5 matches parsed 5
        validateFrameCount(
            declaredCount = 5,
            png2assetCOutput = elephant5FrameOutput,
            stemName = "elephant",
        )
    }

    // -------------------------------------------------------------------------
    // Test 2: Mismatched declared count → throws with both counts in the message
    //
    // Author declares `frames(3)` but png2asset emits `elephant_metasprites[5]`.
    // The build must fail loudly naming both counts so the author can correct the
    // `frames(N)` call.
    //
    // RED: unresolved reference to validateFrameCount. Plan 13.3-06 adds it.
    // -------------------------------------------------------------------------
    @Test
    fun `validateFrameCount throws when declared count disagrees with png2asset output count`() {
        val ex =
            assertThrows<Exception>(
                "D-07: validateFrameCount must throw when declared count (3) disagrees with parsed count (5)"
            ) {
                validateFrameCount(
                    declaredCount = 3,
                    png2assetCOutput = elephant5FrameOutput,
                    stemName = "elephant",
                )
            }

        assertTrue(
            ex.message?.contains("3") == true,
            "D-07 error message must contain declared count '3', got: ${ex.message}",
        )
        assertTrue(
            ex.message?.contains("5") == true,
            "D-07 error message must contain parsed count '5', got: ${ex.message}",
        )
    }

    // -------------------------------------------------------------------------
    // Test 3: Correct stem isolates the right pointer array (no stem leakage)
    //
    // A game with two metasprites ("elephant" 5 frames, "tiger" 3 frames) would
    // have both declarations in the same or separate .c files. The helper must
    // use the stemName to parse the correct count — not the first number found.
    //
    // RED: unresolved reference to validateFrameCount. Plan 13.3-06 adds it.
    // -------------------------------------------------------------------------
    @Test
    fun `validateFrameCount uses stemName to isolate the correct pointer array count`() {
        // tiger has 3 frames; declared 3 → must pass
        validateFrameCount(
            declaredCount = 3,
            png2assetCOutput = tiger3FrameOutput,
            stemName = "tiger",
        )
    }

    // -------------------------------------------------------------------------
    // Test 4: Mismatched count for tiger (regression guard on mismatch path)
    //
    // Declaring 5 frames for a tiger that png2asset produces with 3 → must throw
    // naming both counts.
    //
    // RED: unresolved reference to validateFrameCount. Plan 13.3-06 adds it.
    // -------------------------------------------------------------------------
    @Test
    fun `validateFrameCount throws for tiger when declared count mismatches`() {
        val ex =
            assertThrows<Exception>(
                "D-07: validateFrameCount must throw when declared (5) disagrees with parsed tiger count (3)"
            ) {
                validateFrameCount(
                    declaredCount = 5,
                    png2assetCOutput = tiger3FrameOutput,
                    stemName = "tiger",
                )
            }

        assertTrue(
            ex.message?.contains("5") == true || ex.message?.contains("3") == true,
            "D-07 error message must contain at least one of the counts (5 or 3), got: ${ex.message}",
        )
    }
}
