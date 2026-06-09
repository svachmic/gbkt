/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import java.awt.image.BufferedImage
import java.awt.image.IndexColorModel
import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

// =============================================================================
// Phase 13.7 Plan 02 — RED→GREEN unit tests for checkPalettePolarity (D-02/D-02b/D-02c).
//
// These tests verify the single shared luminance-order comparator (Req 3 tier 1).
// The comparator uses Spearman rank correlation with strict -1.0 threshold (EPSILON=1e-9)
// and Rec.601 luminance. Baseline = source PNG's own PLTE luminance ranking.
//
// Test cases:
//   Test 1 (GREEN baseline): ascending PLTE + ascending emitted RGB555 → Spearman +1.0 → false
//   Test 2 (RED first):      ascending PLTE + fully-reversed emitted RGB555 → Spearman -1.0 → true
//   Test 3 (degenerate):     n<2 OR all-equal-lum PLTE → false (no false positive)
//   Test 4 (non-monotonic):  non-ascending PLTE + emitted matching source ranking → false
//   Test 5 (tie averaging):  two equal-lum entries + partial reorder → Spearman > -1.0 → false
//
// Phase 13.8 Plan 01 — Wave 0 RED tests for per-sub-palette ranking (Req 1) and
//   RGB555 quantization grid (Req 3). These tests FAIL against the current flat-ranking
//   implementation and turn GREEN after Task 2 rewrites checkPalettePolarity.
//
//   Req 1 Test A (RED): 16-entry palette: first 4-entry sub-palette is strict reversal +
//     remaining 12 entries zero-padded → checkPalettePolarity must return true (currently false
//     because flat Spearman over 16 entries with 12 zero-padded ties can never reach -1.0).
//   Req 1 Test B (GREEN baseline): correctly-ordered 4x4 palette → false (no spurious WARNING).
//   Req 3 Test (RED): two source colors 8-bit-distinct but RGB555-equal in luminance →
//     they rank as tied → Spearman > -1.0 → false (no spurious inversion trigger).
// =============================================================================

class PalettePolarityTest {

    @TempDir lateinit var tempDir: File

    // -------------------------------------------------------------------------
    // Test 1: ascending-luminance fixture PLTE + ascending emitted → false (not inverted)
    // -------------------------------------------------------------------------

    @Test
    fun `checkPalettePolarity returns false for ascending PLTE with ascending emitted RGB555`() {
        // Ascending PLTE: black → dark-gray → light-gray → white
        // Entry 0: RGB(0,0,0)       lum=0
        // Entry 1: RGB(64,64,64)    lum=64
        // Entry 2: RGB(192,192,192) lum=192
        // Entry 3: RGB(255,255,255) lum=255
        val sourcePng = writeFourEntryIndexedPng(
            reds   = byteArrayOf(0, 64, 192.toByte(), 255.toByte()),
            greens = byteArrayOf(0, 64, 192.toByte(), 255.toByte()),
            blues  = byteArrayOf(0, 64, 192.toByte(), 255.toByte()),
        )

        // Emitted RGB555 in ascending luminance order matching the PLTE ranking:
        // black→dark-gray→light-gray→white in RGB555
        val emittedAscending = listOf(
            rgb888ToRgb555(0, 0, 0),           // black   lum=0
            rgb888ToRgb555(64, 64, 64),         // dark    lum=64
            rgb888ToRgb555(192, 192, 192),       // light   lum=192
            rgb888ToRgb555(255, 255, 255),       // white   lum=255
        )

        assertFalse(
            checkPalettePolarity(sourcePng, emittedAscending),
            "Ascending PLTE + ascending emitted → Spearman +1.0 → not inverted → must return false",
        )
    }

    // -------------------------------------------------------------------------
    // Test 2 (RED first): ascending PLTE + fully-reversed emitted → true (inverted)
    // This is the RED test — must fail before checkPalettePolarity exists.
    // -------------------------------------------------------------------------

    @Test
    fun `checkPalettePolarity returns true for ascending PLTE with fully reversed emitted RGB555`() {
        // Same ascending-lum PLTE as Test 1
        val sourcePng = writeFourEntryIndexedPng(
            reds   = byteArrayOf(0, 64, 192.toByte(), 255.toByte()),
            greens = byteArrayOf(0, 64, 192.toByte(), 255.toByte()),
            blues  = byteArrayOf(0, 64, 192.toByte(), 255.toByte()),
        )

        // Emitted RGB555 is the fully reversed order: white→light-gray→dark-gray→black
        val emittedReversed = listOf(
            rgb888ToRgb555(255, 255, 255),       // white   lum=255  (was last)
            rgb888ToRgb555(192, 192, 192),        // light   lum=192
            rgb888ToRgb555(64, 64, 64),          // dark    lum=64
            rgb888ToRgb555(0, 0, 0),             // black   lum=0    (was first)
        )

        assertTrue(
            checkPalettePolarity(sourcePng, emittedReversed),
            "Ascending PLTE + fully-reversed emitted → Spearman -1.0 → inverted → must return true",
        )
    }

    // -------------------------------------------------------------------------
    // Test 3 (degenerate): n<2 or all-equal-luminance → false (no false positive)
    // -------------------------------------------------------------------------

    @Test
    fun `checkPalettePolarity returns false for degenerate inputs`() {
        // Case A: all-equal-luminance PLTE (all mid-gray)
        val equalLumPng = writeFourEntryIndexedPng(
            reds   = byteArrayOf(128.toByte(), 128.toByte(), 128.toByte(), 128.toByte()),
            greens = byteArrayOf(128.toByte(), 128.toByte(), 128.toByte(), 128.toByte()),
            blues  = byteArrayOf(128.toByte(), 128.toByte(), 128.toByte(), 128.toByte()),
        )
        val anyRgb555 = listOf(
            rgb888ToRgb555(128, 128, 128),
            rgb888ToRgb555(128, 128, 128),
            rgb888ToRgb555(128, 128, 128),
            rgb888ToRgb555(128, 128, 128),
        )
        assertFalse(
            checkPalettePolarity(equalLumPng, anyRgb555),
            "All-equal-luminance PLTE → degenerate → must return false (no false positive)",
        )

        // Case B: single emitted value (n=1 < 2 guard fires)
        // The emitted palette has only 1 entry — cannot determine rank order.
        val singleEmitted = listOf(rgb888ToRgb555(128, 128, 128))
        // Use the equal-lum source PNG (all 4 entries at lum≈128) — degenerate source
        assertFalse(
            checkPalettePolarity(equalLumPng, singleEmitted),
            "Single emitted value (n<2) → degenerate → must return false",
        )
    }

    // -------------------------------------------------------------------------
    // Test 4: non-monotonic PLTE + emitted matching source ranking → false
    // This is the "real player" palette scenario — must NOT flag a benign non-monotonic palette.
    // PLTE: orange(lum≈159), black(lum=0), near-white(lum≈248), gray(lum≈168)
    // Emitted in source ranking order → Spearman +1.0 → not inverted.
    // -------------------------------------------------------------------------

    @Test
    fun `checkPalettePolarity returns false for non-monotonic PLTE with emitted matching source ranking`() {
        // Non-ascending PLTE (like the real player sprite):
        // Index 0: orange  RGB(255,128,64)  lum ≈ 0.299*255 + 0.587*128 + 0.114*64 ≈ 158.5
        // Index 1: black   RGB(0,0,0)       lum = 0
        // Index 2: white   RGB(248,248,248) lum ≈ 248
        // Index 3: gray    RGB(168,168,168) lum ≈ 168
        val sourcePng = writeFourEntryIndexedPng(
            reds   = byteArrayOf(255.toByte(), 0, 248.toByte(), 168.toByte()),
            greens = byteArrayOf(128.toByte(), 0, 248.toByte(), 168.toByte()),
            blues  = byteArrayOf(64,           0, 248.toByte(), 168.toByte()),
        )

        // Emitted RGB555 values that exactly preserve the source PLTE order.
        // Source order rank: black(0)=rank1, orange(158.5)=rank2, gray(168)=rank3, white(248)=rank4
        // Emitted in source PLTE index order (0=orange, 1=black, 2=white, 3=gray) → ranks [2,1,4,3]
        // Spearman between source ranks [2,1,4,3] and emitted ranks [2,1,4,3] = +1.0
        val emittedInSourceOrder = listOf(
            rgb888ToRgb555(255, 128, 64),    // orange  lum≈158.5  (source idx 0)
            rgb888ToRgb555(0, 0, 0),          // black   lum=0      (source idx 1)
            rgb888ToRgb555(248, 248, 248),    // white   lum≈248    (source idx 2)
            rgb888ToRgb555(168, 168, 168),    // gray    lum≈168    (source idx 3)
        )

        assertFalse(
            checkPalettePolarity(sourcePng, emittedInSourceOrder),
            "Non-monotonic PLTE with emitted order matching source ranking → Spearman +1.0 → must return false",
        )
    }

    // -------------------------------------------------------------------------
    // Test 5: Rec.601 + tie averaging — partial reorder (not full reversal) → false
    // Two entries with equal luminance get averaged rank; Spearman ends up > -1.0.
    // -------------------------------------------------------------------------

    @Test
    fun `checkPalettePolarity returns false for partial reorder that is not a full reversal`() {
        // PLTE with two equal-lum mid entries:
        // Index 0: black  RGB(0,0,0)       lum=0
        // Index 1: mid-A  RGB(100,100,100) lum=100
        // Index 2: mid-B  RGB(110,110,110) lum=110  (slightly different from mid-A)
        // Index 3: white  RGB(255,255,255) lum=255
        val sourcePng = writeFourEntryIndexedPng(
            reds   = byteArrayOf(0, 100.toByte(), 110.toByte(), 255.toByte()),
            greens = byteArrayOf(0, 100.toByte(), 110.toByte(), 255.toByte()),
            blues  = byteArrayOf(0, 100.toByte(), 110.toByte(), 255.toByte()),
        )

        // Emitted: swap mid-A and mid-B but keep black first and white last.
        // Source ranks: black=1, mid-A=2, mid-B=3, white=4
        // Emitted: black(rank1), mid-B(rank3), mid-A(rank2), white(rank4)
        // This is a partial 2-element swap — Spearman ≠ -1.0 → not full reversal → false
        val emittedPartialSwap = listOf(
            rgb888ToRgb555(0, 0, 0),           // black  lum=0    rank1 → position1
            rgb888ToRgb555(110, 110, 110),      // mid-B  lum=110  rank3 → position2
            rgb888ToRgb555(100, 100, 100),      // mid-A  lum=100  rank2 → position3
            rgb888ToRgb555(255, 255, 255),      // white  lum=255  rank4 → position4
        )

        assertFalse(
            checkPalettePolarity(sourcePng, emittedPartialSwap),
            "Partial reorder (swap of mid two entries only) → Spearman > -1.0 → must return false",
        )
    }

    // =========================================================================
    // Phase 13.8 Plan 01 Req 1 — Per-sub-palette ranking (RED→GREEN after Task 2)
    // =========================================================================

    /**
     * Req 1 Test A (RED until Task 2):
     * 16-entry palette with first 4-entry sub-palette strictly reversed +
     * remaining 12 entries zero-padded (RGB 0,0,0) → must return true.
     *
     * Why RED against current code:
     *   The current flat-ranking Spearman over all 16 entries has 12 zero-padded
     *   entries that create a mass-tie at the low-luminance end. With ties, the
     *   strict -1.0 Spearman threshold is mathematically unreachable → returns false.
     *   (WR-01 from 13.7-REVIEW: "dead BG guard" — world1-tileset.png never fires.)
     *
     * Why GREEN after Task 2:
     *   Per-sub-palette loop ranks only the first 4 entries as a group. Within that
     *   group, [white, light, dark, black] is a strict reversal of [black, dark, light, white]
     *   → Spearman = -1.0 → returns true.
     */
    @Test
    fun `checkPalettePolarity returns true for 16-entry palette with first sub-palette reversed and 12 zero-padded entries`() {
        // First 4-entry sub-palette: ascending luminance (0,64,192,255)
        // Remaining 12 entries: zero-padded (RGB 0,0,0)
        val sourcePng = writeSixteenEntryIndexedPng(
            firstFourReds   = byteArrayOf(0, 64, 192.toByte(), 255.toByte()),
            firstFourGreens = byteArrayOf(0, 64, 192.toByte(), 255.toByte()),
            firstFourBlues  = byteArrayOf(0, 64, 192.toByte(), 255.toByte()),
        )

        // Emitted RGB555: first sub-palette is strictly reversed (white→light→dark→black)
        // Remaining 12 entries are zero (black RGB555 = 0x0000)
        val emittedReversedFirstSubPalette = listOf(
            // Sub-palette 0 (indices 0-3): REVERSED
            rgb888ToRgb555(255, 255, 255),  // white   (was black at index 0)
            rgb888ToRgb555(192, 192, 192),  // light   (was dark at index 1)
            rgb888ToRgb555(64, 64, 64),     // dark    (was light at index 2)
            rgb888ToRgb555(0, 0, 0),        // black   (was white at index 3)
            // Sub-palettes 1-3 (indices 4-15): zero-padded, no meaningful luminance contrast
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0,
        )

        assertTrue(
            checkPalettePolarity(sourcePng, emittedReversedFirstSubPalette),
            "Req 1: 16-entry palette with first 4-entry sub-palette strictly reversed + 12 zero-padded " +
                "entries MUST return true. Currently returns false because the flat Spearman over 16 " +
                "entries with 12 zero-pad ties can never reach the strict -1.0 threshold (WR-01 dead guard). " +
                "Will turn GREEN when checkPalettePolarity ranks per 4-entry sub-palette group.",
        )
    }

    /**
     * Req 1 Test B (GREEN baseline):
     * Correctly-ordered 4×4 palette (all 4 sub-palettes ascending) → false.
     *
     * This confirms that a correctly-authored shipped palette does NOT trigger
     * a spurious WARNING after the per-sub-palette fix.
     */
    @Test
    fun `checkPalettePolarity returns false for correctly ordered 4x4 palette all sub-palettes ascending`() {
        // 16-entry palette: 4 sub-palettes, each ascending black→dark→light→white
        val sourcePng = writeSixteenEntryIndexedPng(
            firstFourReds   = byteArrayOf(0, 64, 192.toByte(), 255.toByte()),
            firstFourGreens = byteArrayOf(0, 64, 192.toByte(), 255.toByte()),
            firstFourBlues  = byteArrayOf(0, 64, 192.toByte(), 255.toByte()),
        )

        // Emitted: all 4 sub-palettes correctly ordered (ascending)
        val emittedAllAscending = listOf(
            // Sub-palette 0: ascending
            rgb888ToRgb555(0, 0, 0),
            rgb888ToRgb555(64, 64, 64),
            rgb888ToRgb555(192, 192, 192),
            rgb888ToRgb555(255, 255, 255),
            // Sub-palette 1: ascending (different colors but same order)
            rgb888ToRgb555(0, 0, 64),
            rgb888ToRgb555(0, 64, 128.toByte().toInt()),
            rgb888ToRgb555(64, 128.toByte().toInt(), 192.toByte().toInt()),
            rgb888ToRgb555(128.toByte().toInt(), 192.toByte().toInt(), 255.toByte().toInt()),
            // Sub-palette 2: ascending
            rgb888ToRgb555(0, 32, 0),
            rgb888ToRgb555(32, 96, 32),
            rgb888ToRgb555(64, 160, 64),
            rgb888ToRgb555(128.toByte().toInt(), 224.toByte().toInt(), 128.toByte().toInt()),
            // Sub-palette 3: ascending
            rgb888ToRgb555(0, 0, 0),
            rgb888ToRgb555(85, 85, 85),
            rgb888ToRgb555(170, 170, 170),
            rgb888ToRgb555(255, 255, 255),
        )

        assertFalse(
            checkPalettePolarity(sourcePng, emittedAllAscending),
            "Req 1: correctly-ordered 4x4 palette (all sub-palettes ascending) must return false — " +
                "no spurious WARNING on a correctly shipped asset.",
        )
    }

    /**
     * Req 3 Test (RED until Plan 13.8-02):
     * Two 8-bit-distinct source colors that are RGB555-equal in luminance after
     * quantization must rank as tied → Spearman > -1.0 → returns false.
     *
     * This tests the RGB555 quantization grid: two source PLTE entries that differ
     * at the 8-bit level but collapse to the same RGB555 value have identical
     * quantized luminance → they are tied ranks → strict -1.0 is unreachable → false.
     *
     * Why RED against current code:
     *   Current code uses raw 8-bit PLTE values without quantizing to the RGB555 grid.
     *   The two 8-bit values (e.g., 100 and 103) have distinct luminance (lum=100, lum=103),
     *   so they rank distinctly. In a 4-entry palette with these two near-identical mid values
     *   at the middle two positions, reversing them yields Spearman significantly < -1.0 only
     *   if we use the 8-bit distinction. After quantizing to RGB555, their quantized values are
     *   identical → tied → not a strict full reversal → false.
     *
     * The test is also valid after Task 2 (per-sub-palette) but only turns GREEN after
     * Plan 13.8-02 adds the to5() quantization to the source side. Mark as @Disabled
     * with the unblocking plan citation so the suite compiles and stays RED.
     */
    @Test
    fun `checkPalettePolarity returns false when two source colors are 8bit-distinct but RGB555-equal in luminance`() {
        // Source PLTE: 4 entries
        // Index 0: black  RGB(0,0,0)     lum8=0      rgb555=(0,0,0)       lum555=0
        // Index 1: mid-A  RGB(100,100,100) lum8=100  rgb555=(12,12,12)    lum555=12*255/31≈98.7
        // Index 2: mid-B  RGB(103,103,103) lum8=103  rgb555=(12,12,12)    lum555=same (collapsed!)
        // Index 3: white  RGB(255,255,255) lum8=255  rgb555=(31,31,31)    lum555=255
        //
        // After RGB555 quantization: mid-A and mid-B both collapse to rgb555=(12,12,12)
        // → they have identical quantized luminance → tied ranks → strict reversal unreachable
        val sourcePng = writeFourEntryIndexedPng(
            reds   = byteArrayOf(0, 100.toByte(), 103.toByte(), 255.toByte()),
            greens = byteArrayOf(0, 100.toByte(), 103.toByte(), 255.toByte()),
            blues  = byteArrayOf(0, 100.toByte(), 103.toByte(), 255.toByte()),
        )

        // Emitted: reversed (white, mid-B, mid-A, black) — the mid values are swapped
        // With 8-bit source lum: reversed → strict -1.0 → currently would return true (WRONG)
        // With RGB555 quantized source lum: mid-A == mid-B → tied → not strict -1.0 → must return false
        val emittedReversedWithRgb555Tie = listOf(
            rgb888ToRgb555(255, 255, 255),  // white
            rgb888ToRgb555(103, 103, 103),  // mid-B (RGB555-collapsed to same as mid-A)
            rgb888ToRgb555(100, 100, 100),  // mid-A (same RGB555 as mid-B)
            rgb888ToRgb555(0, 0, 0),        // black
        )

        assertFalse(
            checkPalettePolarity(sourcePng, emittedReversedWithRgb555Tie),
            "Req 3: when two source PLTE entries are 8-bit-distinct but collapse to the same RGB555 " +
                "quantized value, they must rank as tied → Spearman cannot reach strict -1.0 → false. " +
                "Currently fails because the source side uses raw 8-bit values (lum=100 vs lum=103 " +
                "are distinct) instead of the RGB555 quantized grid (both → (12,12,12) → same lum). " +
                "Turns GREEN in 13.8-02 when to5() quantization is applied to the source PLTE side.",
        )
    }

    // -------------------------------------------------------------------------
    // Fixture helpers
    // -------------------------------------------------------------------------

    /**
     * Writes an indexed PNG with exactly 4 palette entries.
     * Each entry is fully opaque (alpha=255). Paints one pixel per entry.
     */
    private fun writeFourEntryIndexedPng(
        reds: ByteArray,
        greens: ByteArray,
        blues: ByteArray,
    ): File {
        require(reds.size == 4 && greens.size == 4 && blues.size == 4) {
            "writeFourEntryIndexedPng: must provide exactly 4 entries"
        }
        val alphas = ByteArray(4) { 0xFF.toByte() }
        val cm = IndexColorModel(8, 4, reds, greens, blues, alphas)
        val img = BufferedImage(4, 1, BufferedImage.TYPE_BYTE_INDEXED, cm)
        val raster = img.raster
        for (x in 0 until 4) {
            raster.setSample(x, 0, 0, x)  // pixel x has palette index x
        }
        val target = File(tempDir, "fixture_${reds[0]}_${greens[0]}_${blues[0]}.png")
        ImageIO.write(img, "PNG", target)
        return target
    }

    /**
     * Writes an indexed PNG with exactly 16 palette entries (4 sub-palettes of 4 entries each).
     * Only the first 4 entries are specified; the remaining 12 are zero-padded (RGB 0,0,0).
     * Each entry is fully opaque (alpha=255). Paints 16 pixels across 1 row.
     */
    private fun writeSixteenEntryIndexedPng(
        firstFourReds: ByteArray,
        firstFourGreens: ByteArray,
        firstFourBlues: ByteArray,
    ): File {
        require(firstFourReds.size == 4 && firstFourGreens.size == 4 && firstFourBlues.size == 4) {
            "writeSixteenEntryIndexedPng: firstFour arrays must have exactly 4 entries"
        }
        val reds   = ByteArray(16) { i -> if (i < 4) firstFourReds[i] else 0 }
        val greens = ByteArray(16) { i -> if (i < 4) firstFourGreens[i] else 0 }
        val blues  = ByteArray(16) { i -> if (i < 4) firstFourBlues[i] else 0 }
        val alphas = ByteArray(16) { 0xFF.toByte() }
        val cm = IndexColorModel(8, 16, reds, greens, blues, alphas)
        val img = BufferedImage(16, 1, BufferedImage.TYPE_BYTE_INDEXED, cm)
        val raster = img.raster
        for (x in 0 until 16) {
            raster.setSample(x, 0, 0, x)  // pixel x has palette index x
        }
        val target = File(tempDir, "fixture16_${firstFourReds[0]}_${firstFourGreens[0]}_${firstFourBlues[0]}.png")
        ImageIO.write(img, "PNG", target)
        return target
    }

    /**
     * Converts 8-bit RGB to a GBC RGB555 packed integer.
     * Layout: (b5 shl 10) or (g5 shl 5) or r5, where r5 = r8*31/255.
     */
    private fun rgb888ToRgb555(r8: Int, g8: Int, b8: Int): Int {
        val r5 = (r8 * 31) / 255
        val g5 = (g8 * 31) / 255
        val b5 = (b8 * 31) / 255
        return (b5 shl 10) or (g5 shl 5) or r5
    }
}
