/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import java.io.File
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

// =============================================================================
// PHASE 12.8 W3 — isIndexedPng helper (PNG IHDR color-type discriminator)
//
// Locks the two branches of the conditional -keep_palette_order pass-through:
//   (i) Indexed PNG (color-type byte == 3)            -> returns true
//   (ii) RGB PNG (color-type byte == 2)               -> returns false
//
// Plus structural guards that the caller (convertOneTileset) relies on for
// "false means do NOT pass -keep_palette_order":
//   (iii) Missing file                                -> returns false
//   (iv) Truncated header (< 26 bytes)                -> returns false
//   (v) PNG-signature mismatch                        -> returns false
//
// The synthetic 26-byte headers below mirror the W3C PNG layout:
//   bytes 0..7   PNG signature  89 50 4E 47 0D 0A 1A 0A
//   bytes 8..11  IHDR length    00 00 00 0D
//   bytes 12..15 IHDR type      49 48 44 52 ("IHDR")
//   bytes 16..19 width          00 00 00 08
//   bytes 20..23 height         00 00 00 08
//   byte  24     bit depth      08
//   byte  25     color type     02 (RGB) or 03 (indexed)
// =============================================================================

class IsIndexedPngTest {

    @TempDir lateinit var tempDir: File

    @Test
    fun `returns true for synthetic indexed PNG header (color-type 3)`() {
        val target = File(tempDir, "indexed.png")
        target.writeBytes(syntheticPngHeader(colorType = 0x03.toByte()))
        assertTrue(
            newTask().isIndexedPng(target),
            "isIndexedPng must return true when IHDR color-type byte (offset 25) is 0x03",
        )
    }

    @Test
    fun `returns false for synthetic RGB PNG header (color-type 2)`() {
        val target = File(tempDir, "rgb.png")
        target.writeBytes(syntheticPngHeader(colorType = 0x02.toByte()))
        assertFalse(
            newTask().isIndexedPng(target),
            "isIndexedPng must return false when IHDR color-type byte (offset 25) is 0x02 " +
                "(RGB) — png2asset rejects -keep_palette_order on this input",
        )
    }

    @Test
    fun `returns false when file does not exist`() {
        assertFalse(newTask().isIndexedPng(File(tempDir, "does-not-exist.png")))
    }

    @Test
    fun `returns false when header is truncated`() {
        val target = File(tempDir, "truncated.png")
        // Write only 16 bytes (well under the 26-byte requirement).
        target.writeBytes(byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte()))
        assertFalse(newTask().isIndexedPng(target))
    }

    @Test
    fun `returns false when PNG signature mismatches`() {
        val target = File(tempDir, "bogus.png")
        // Write a full 26-byte block but corrupt byte 0 of the signature.
        val header = syntheticPngHeader(colorType = 0x03.toByte()).copyOf()
        header[0] = 0x00.toByte()
        target.writeBytes(header)
        assertFalse(
            newTask().isIndexedPng(target),
            "isIndexedPng must reject inputs whose first 8 bytes are not the PNG signature " +
                "even when the color-type byte at offset 25 would otherwise indicate indexed",
        )
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private fun newTask(): ConvertZoneTilesetsTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        return project.tasks
            .register("isIndexedPngHarness", ConvertZoneTilesetsTask::class.java)
            .get()
    }

    private fun syntheticPngHeader(colorType: Byte): ByteArray {
        return byteArrayOf(
            // PNG signature (8 bytes)
            0x89.toByte(),
            0x50.toByte(),
            0x4E.toByte(),
            0x47.toByte(),
            0x0D.toByte(),
            0x0A.toByte(),
            0x1A.toByte(),
            0x0A.toByte(),
            // IHDR length (4 bytes, big-endian) = 13
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x0D.toByte(),
            // IHDR chunk type "IHDR" (4 bytes)
            0x49.toByte(),
            0x48.toByte(),
            0x44.toByte(),
            0x52.toByte(),
            // width (4 bytes, big-endian) = 8
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x08.toByte(),
            // height (4 bytes, big-endian) = 8
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x08.toByte(),
            // bit depth (1 byte) = 8
            0x08.toByte(),
            // color type (1 byte) — caller-supplied
            colorType,
        )
    }
}
