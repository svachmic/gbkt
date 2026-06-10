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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

// =============================================================================
// Phase 13.6 Wave 0 — RED unit tests for REQ-1 (tRNS detection) and REQ-5
// (used-visible-color count guard).
//
// Phase 13.6 Wave 2 — RED tests for prePermuteIndexedPng (REQ-2 — Task 1).
//
// Test cases:
//   REQ-1: elephant.png (tRNS=4) -> 4
//   REQ-1: player-character-gbapduck-sprites.png (no tRNS) -> null
//   REQ-1: RGBA PNG (non-indexed) -> null
//   REQ-1: non-existent file -> null
//   REQ-5: elephant.png (transparent@4, 3 used visible) -> 3
//   REQ-5: overflowFixture.png (transparent + 4 used visible colors) -> 4
//   REQ-2: prePermuteIndexedPng(elephant.png, 4) -> temp file with tRNS@0, body@idx<=3
//   REQ-2: prePermuteIndexedPng produces compact remap (skips 0-pixel entries)
//   REQ-2: temp file is adjacent to source (same parent dir)
// =============================================================================

class PngUtilsTest {

    @TempDir lateinit var tempDir: File

    // -------------------------------------------------------------------------
    // REQ-1: getTransparentIndexShared — tRNS index detection
    // -------------------------------------------------------------------------

    @Test
    fun `getTransparentIndexShared returns 4 for elephant png (real indexed PNG with tRNS=4)`() {
        val elephantSrc = repoFile("gbkt-examples/metasprites/res/sprites/elephant.png")
        val elephant = File(tempDir, "elephant.png")
        elephantSrc.copyTo(elephant)

        assertEquals(
            4,
            getTransparentIndexShared(elephant),
            "elephant.png has tRNS chunk at index 4 — getTransparentIndexShared must return 4",
        )
    }

    @Test
    fun `getTransparentIndexShared returns null for player sprite (no tRNS chunk)`() {
        val playerSrc =
            repoFile(
                "gbkt-examples/platformer-template/res/graphics/player-character-gbapduck-sprites.png"
            )
        val player = File(tempDir, "player-character-gbapduck-sprites.png")
        playerSrc.copyTo(player)

        assertNull(
            getTransparentIndexShared(player),
            "player-character-gbapduck-sprites.png has no tRNS chunk — result must be null",
        )
    }

    @Test
    fun `getTransparentIndexShared returns null for RGBA non-indexed PNG`() {
        val rgba = File(tempDir, "rgba.png")
        writeRgbaPng(rgba, width = 8, height = 8)

        assertNull(
            getTransparentIndexShared(rgba),
            "Non-indexed (TYPE_INT_ARGB) PNG has no IndexColorModel — result must be null",
        )
    }

    @Test
    fun `getTransparentIndexShared returns null for non-existent file`() {
        assertNull(
            getTransparentIndexShared(File(tempDir, "does-not-exist.png")),
            "Non-existent file must return null (no exception)",
        )
    }

    // -------------------------------------------------------------------------
    // REQ-5: countUsedVisibleColors — used visible palette entry count
    // -------------------------------------------------------------------------

    @Test
    fun `countUsedVisibleColors returns 3 for elephant png (transparent at 4, 3 used visible)`() {
        val elephantSrc = repoFile("gbkt-examples/metasprites/res/sprites/elephant.png")
        val elephant = File(tempDir, "elephant.png")
        elephantSrc.copyTo(elephant)

        // elephant.png: palette indices 0=outline, 1=green-midtone, 2=bright-green(0-pixel!),
        // 3=near-white-body, 4=transparent(tRNS). Used visible: 0(outline), 1(midtone), 3(body) =
        // 3.
        // The bright-green at index 2 has zero pixels and must NOT be counted.
        assertEquals(
            3,
            countUsedVisibleColors(elephant, transparentIdx = 4),
            "elephant.png has 3 used visible palette entries (the 0-pixel bright-green is excluded)",
        )
    }

    @Test
    fun `countUsedVisibleColors returns 4 for overflow fixture (transparent + 4 used visible)`() {
        val overflowFixture = File(tempDir, "overflow.png")
        writeIndexedSpritePng(overflowFixture, transparentIdx = 0, usedVisibleColors = 4)

        assertEquals(
            4,
            countUsedVisibleColors(overflowFixture, transparentIdx = 0),
            "Overflow fixture has 4 used visible palette entries (exceeds GB OBJ 3-visible limit)",
        )
    }

    // -------------------------------------------------------------------------
    // Task 2: Overflow fixture generator helper (REQ-5)
    //
    // Builds an IndexColorModel-backed indexed PNG with:
    //   - transparentIdx: the palette index set to alpha=0 (the tRNS color)
    //   - usedVisibleColors: exactly N distinct opaque palette entries, each
    //     painted into at least one pixel of the image
    //
    // Used to produce the overflow fixture (transparentIdx + 4 used visible)
    // consumed by countUsedVisibleColors==4 test above.
    // -------------------------------------------------------------------------

    private fun writeIndexedSpritePng(target: File, transparentIdx: Int, usedVisibleColors: Int) {
        val totalEntries = transparentIdx + usedVisibleColors + 1
        val size = totalEntries.coerceAtLeast(2)

        // Build palette arrays: reds, greens, blues, alphas
        val reds = ByteArray(size)
        val greens = ByteArray(size)
        val blues = ByteArray(size)
        val alphas = ByteArray(size) { 0xFF.toByte() } // all opaque by default

        // The transparent index gets alpha=0
        alphas[transparentIdx] = 0x00.toByte()

        // Assign distinct opaque colors to the visible entries
        // We skip transparentIdx and assign visible entries to the remaining slots
        var visibleSlot = 0
        for (i in 0 until size) {
            if (i == transparentIdx) continue
            if (visibleSlot >= usedVisibleColors) break
            // Distinct colors: vary red channel per slot (enough for our small palette)
            reds[i] = (50 + visibleSlot * 50).coerceAtMost(255).toByte()
            greens[i] = (100 + visibleSlot * 30).coerceAtMost(255).toByte()
            blues[i] = (150 + visibleSlot * 20).coerceAtMost(255).toByte()
            visibleSlot++
        }

        val cm = IndexColorModel(8, size, reds, greens, blues, alphas)
        val img = BufferedImage(size, size, BufferedImage.TYPE_BYTE_INDEXED, cm)

        // Paint each visible color into at least one pixel
        // Row 0: transparent pixel; remaining rows: each visible color in sequence
        val raster = img.raster
        // Fill row 0 with transparent
        for (x in 0 until size) {
            raster.setSample(x, 0, 0, transparentIdx)
        }

        // Fill each visible color into its own pixel
        var visiblePainted = 0
        for (i in 0 until size) {
            if (i == transparentIdx) continue
            if (visiblePainted >= usedVisibleColors) break
            val row = 1 + visiblePainted
            if (row < size) {
                for (x in 0 until size) {
                    raster.setSample(x, row, 0, i)
                }
            }
            visiblePainted++
        }

        ImageIO.write(img, "PNG", target)
        require(target.isFile && target.length() > 0) {
            "overflow fixture PNG was not written: ${target.absolutePath}"
        }
    }

    // -------------------------------------------------------------------------
    // REQ-2: prePermuteIndexedPng — compact transparent→0 remap + temp PNG
    //
    // Plan 13.6-03 Task 1: these tests reference prePermuteIndexedPng(File, Int):File
    // which does NOT exist yet. This is the TDD RED gate for Task 1.
    //
    // Verified elephant remap (from RESEARCH.md Pattern 2):
    //   source indices: 0=outline, 1=green-midtone, 2=bright-green(0-pixel!),
    //                   3=near-white-body, 4=transparent(tRNS)
    //   compact remap:  {4→0, 0→1, 1→2, 3→3}  (idx 2 is 0-pixel → skipped)
    //   result:         transparent@0, outline@1, midtone@2, body@3 (all ≤ 3)
    // -------------------------------------------------------------------------

    @Test
    fun `prePermuteIndexedPng returns temp file with transparent at index 0`() {
        val elephantSrc = repoFile("gbkt-examples/metasprites/res/sprites/elephant.png")
        val elephant = File(tempDir, "elephant.png")
        elephantSrc.copyTo(elephant)

        val temp =
            prePermuteIndexedPng(
                elephant,
                transparentIdx = 4,
                buildTempDir = tempDir,
                stemName = "elephant",
            )
        try {
            assertTrue(temp.isFile && temp.length() > 0, "temp file must exist and be non-empty")
            // The temp file must be an indexed PNG with tRNS at index 0
            val resultIdx = getTransparentIndexShared(temp)
            assertEquals(
                0,
                resultIdx,
                "prePermuteIndexedPng must place transparent at index 0; getTransparentIndexShared returned $resultIdx",
            )
        } finally {
            temp.delete()
        }
    }

    @Test
    fun `prePermuteIndexedPng produces compact remap - all pixels use indices 0-3 only`() {
        // Elephant has 5 palette entries (indices 0-4), only 4 used (index 2 = 0-pixel
        // bright-green).
        // Compact remap: {4→0, 0→1, 1→2, 3→3} — 0-pixel index 2 is skipped.
        // Body (was index 3) lands at new index 3.  2bpp can encode 0-3.
        // After write + read, JVM pads PLTE to 256 (mapSize=256 is expected), but NO pixel
        // should reference an index > 3 — that would mean body is at 4+ (2bpp overflow).
        val elephantSrc = repoFile("gbkt-examples/metasprites/res/sprites/elephant.png")
        val elephant = File(tempDir, "elephant.png")
        elephantSrc.copyTo(elephant)

        val temp =
            prePermuteIndexedPng(
                elephant,
                transparentIdx = 4,
                buildTempDir = tempDir,
                stemName = "elephant",
            )
        try {
            val img = ImageIO.read(temp) ?: error("ImageIO.read returned null for temp PNG")
            val cm = img.getColorModel() as IndexColorModel
            // After ImageIO round-trip, JVM pads PLTE to 256 — mapSize may be 256 (expected).
            // What matters is that the transparent pixel is at index 0.
            assertEquals(
                0,
                cm.transparentPixel,
                "IndexColorModel.transparentPixel must be 0 after permutation; got ${cm.transparentPixel}",
            )
            // Verify ALL pixels reference indices ≤ 3 (compact remap proves body is in 2bpp range).
            val raster = img.raster
            var maxIdx = 0
            for (y in 0 until img.height) {
                for (x in 0 until img.width) {
                    maxIdx = maxOf(maxIdx, raster.getSample(x, y, 0))
                }
            }
            assertTrue(
                maxIdx <= 3,
                "Compact remap must keep all pixel indices <= 3 (2bpp encodable); max found: $maxIdx. " +
                    "If maxIdx==4, the 0-pixel bright-green was NOT skipped (Pitfall 1).",
            )
        } finally {
            temp.delete()
        }
    }

    @Test
    fun `prePermuteIndexedPng temp file is in buildTempDir with deterministic name (REQ-6)`() {
        // REQ-6: the temp file must land in buildTempDir (not adjacent to source, not in TMPDIR)
        // with a deterministic name derived from the source sprite base name.
        val elephantSrc = repoFile("gbkt-examples/metasprites/res/sprites/elephant.png")
        val elephant = File(tempDir, "elephant.png")
        elephantSrc.copyTo(elephant)
        val buildTmpDir = File(tempDir, "build-tmp")
        buildTmpDir.mkdirs()

        val temp =
            prePermuteIndexedPng(
                elephant,
                transparentIdx = 4,
                buildTempDir = buildTmpDir,
                stemName = "elephant",
            )
        try {
            assertEquals(
                buildTmpDir.canonicalPath,
                temp.parentFile.canonicalPath,
                "Temp file must be in buildTempDir, not adjacent to source (REQ-6 / WR-04)",
            )
            assertEquals(
                "gbkt_permuted_elephant.png",
                temp.name,
                "Temp file name must be deterministic: gbkt_permuted_<stemName>.png (W2 / REQ-6)",
            )
        } finally {
            temp.delete()
        }
    }

    // -------------------------------------------------------------------------
    // Shared helper: write a non-indexed RGBA PNG for the null-on-RGBA test case
    // -------------------------------------------------------------------------

    private fun writeRgbaPng(target: File, width: Int, height: Int) {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                img.setRGB(x, y, 0xFF000000.toInt() or ((x + y) * 8))
            }
        }
        ImageIO.write(img, "PNG", target)
    }
}
