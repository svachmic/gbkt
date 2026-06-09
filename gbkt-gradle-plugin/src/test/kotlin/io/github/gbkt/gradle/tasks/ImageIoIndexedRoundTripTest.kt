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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

// =============================================================================
// Phase 13.6 Wave 0 — A2 Investigation: ImageIO indexed-PNG round-trip fidelity
//
// Research assumption A2 from 13.6-RESEARCH.md:
//   "ImageIO.write(..., "PNG", ...) on an IndexColorModel-backed BufferedImage
//    produces a valid indexed PNG faithfully (color-type 3)"
//
// If the JVM PNG encoder preserves indexed mode (color-type 3), Plan 03 may use
// ImageIO.write (Option i) to write the pre-permuted PNG. If it upgrades to RGBA
// (color-type 2 or 6), Plan 03 must use raw chunk manipulation (Option ii).
//
// This test writes a 4-color IndexColorModel BufferedImage via ImageIO.write(PNG),
// re-opens the written file, and reads the IHDR color-type byte at offset 25
// (PNG_COLOR_TYPE_OFFSET_SHARED = 25). The assertion is pinned to the observed
// runtime behavior so CI fails if the JVM PNG encoder ever changes.
//
// A2 VERDICT (measured 2026-06-05):
//   The JVM PNG encoder PRESERVES indexed color-type 3 when writing a
//   BufferedImage backed by an IndexColorModel.
//   => "A2 holds — Plan 03 may use ImageIO.write (Option i)"
//
// This verdict is the deliverable: Plan 03 Task 1 reads this from 13.6-01-SUMMARY.md
// and selects Option i (ImageIO.write) as the PNG rewrite mechanism for pre-permutation.
// =============================================================================

/**
 * A2 investigation: does `ImageIO.write(..., "PNG", ...)` on an `IndexColorModel`-backed
 * `BufferedImage` preserve indexed color-type 3, or upgrade it to RGBA?
 *
 * The test builds a 4-entry indexed palette, writes the image via ImageIO, and reads the
 * IHDR color-type byte at offset [PNG_COLOR_TYPE_OFFSET_SHARED] = 25.
 *
 * **A2 verdict: HOLDS** — the JVM PNG encoder preserves indexed color-type 3.
 * Plan 03 may use `ImageIO.write` (Option i) for the pre-permuted PNG write step.
 */
class ImageIoIndexedRoundTripTest {

    @TempDir lateinit var tempDir: File

    @Test
    fun `ImageIO write preserves indexed PNG color-type 3 (A2 holds — Option i viable)`() {
        // Build a 4-color IndexColorModel (mimics the elephant compact remap result:
        // 0=transparent, 1=outline, 2=midtone, 3=body)
        val size = 4
        val reds   = byteArrayOf(0x00, 0x07, 0x66, 0xE0.toByte())
        val greens = byteArrayOf(0x00, 0x18, 0xC0.toByte(), 0xF8.toByte())
        val blues  = byteArrayOf(0x00, 0x21, 0x6C, 0xCF.toByte())
        val alphas = byteArrayOf(0x00, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()) // index 0 = transparent

        val cm = IndexColorModel(8, size, reds, greens, blues, alphas)
        val img = BufferedImage(8, 8, BufferedImage.TYPE_BYTE_INDEXED, cm)

        // Paint all 4 indices into the image so the palette is "used"
        val raster = img.raster
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                raster.setSample(x, y, 0, (x + y) % size)
            }
        }

        // Write via ImageIO
        val outputFile = File(tempDir, "indexed-roundtrip.png")
        ImageIO.write(img, "PNG", outputFile)
        require(outputFile.isFile && outputFile.length() > 0) {
            "ImageIO.write produced no output file — test setup error"
        }

        // Read the IHDR color-type byte at offset 25 from the written file
        val writtenColorType: Byte = outputFile.inputStream().buffered().use { stream ->
            val header = ByteArray(PNG_HEADER_BYTES_SHARED)
            val read = stream.read(header)
            require(read == PNG_HEADER_BYTES_SHARED) {
                "Written PNG header is too short ($read bytes) — unexpected ImageIO output"
            }
            header[PNG_COLOR_TYPE_OFFSET_SHARED]
        }

        // Assert that the color-type is 3 (indexed) — not 2 (RGB) or 6 (RGBA).
        //
        // If this assertion PASSES: A2 holds — Plan 03 may use ImageIO.write (Option i).
        //   The JVM PNG encoder faithfully preserves the IndexColorModel color-type.
        //
        // If this assertion FAILS: A2 is false — Plan 03 must use raw chunk manipulation
        //   (Option ii). Update this test + 13.6-01-SUMMARY.md with "Option ii required".
        assertEquals(
            PNG_COLOR_TYPE_INDEXED_SHARED,
            writtenColorType,
            "A2 holds — Plan 03 may use ImageIO.write (Option i): " +
                "ImageIO.write on IndexColorModel BufferedImage produces indexed color-type 3; " +
                "the JVM PNG encoder does NOT upgrade to RGBA. " +
                "Written color-type byte at offset 25 = $writtenColorType " +
                "(expected ${PNG_COLOR_TYPE_INDEXED_SHARED})",
        )
    }
}
