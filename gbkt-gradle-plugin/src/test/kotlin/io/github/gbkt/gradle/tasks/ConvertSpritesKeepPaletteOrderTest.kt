/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import io.github.gbkt.core.ir.SpriteMode
import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

// =============================================================================
// Phase 12.9 D2a — ConvertSpritesTask -keep_palette_order for indexed PNGs
//
// Root cause (Plan 12.9-08e Diagnose): buildPng2AssetArgs omits
// -keep_palette_order, so png2asset re-sorts the indexed source palette and the
// sprite-sheet's orange background (index 0 in source) lands at index 2 (opaque)
// instead of index 0 (hardware-transparent on GBC). Result: the full 24x32 frame
// surround renders as a prominent dark box around the player.
//
// Fix (D2a): append -keep_palette_order to buildPng2AssetArgs when the source PNG
// is indexed (IHDR color-type == 3), mirroring the guard in ConvertZoneTilesetsTask.
// isIndexedPng is now the shared isIndexedPngShared() from PngUtils.kt.
//
// Reference Makefile: gbdk/examples/.../platformer_template/Makefile:81 uses
// -keep_palette_order on the indexed player PNG. Source PNG
// player-character-gbapduck-sprites.png is indexed (PIL mode P) with palette
// [orange(255,128,64), black, white, gray] → orange is index 0. Without the flag,
// png2asset re-sorts to alphabetical/luminance order → orange moves to index 2
// (visible) and OBJ index 0 becomes white (still transparent), but the surround
// pixels are encoded as index 2 in the tile bytes → box.
//
// RED against HEAD (buildPng2AssetArgs ignores pngFile color type).
// GREEN after D2a fix adds isIndexedPngShared gate.
//
// Tests:
//   1. Indexed PNG (synthetic 26-byte header, color-type 3) → args contain
//      -keep_palette_order.
//   2. Non-indexed PNG (RGB, color-type 2) → args do NOT contain -keep_palette_order.
// =============================================================================

class ConvertSpritesKeepPaletteOrderTest {

    @TempDir lateinit var tempDir: File

    // -------------------------------------------------------------------------
    // Test 1 — Indexed PNG → -keep_palette_order present
    //
    // RED: buildPng2AssetArgs currently ignores the PNG color type → flag absent.
    // GREEN: after D2a fix adds isIndexedPngShared gate → flag appended.
    // -------------------------------------------------------------------------

    @Test
    fun `buildPng2AssetArgs includes keep_palette_order for indexed PNG`() {
        val indexedPng = writeIndexedPng("player.png")
        val outputC = File(tempDir, "player.c")

        val args = buildPng2AssetArgs(
            pngFile = indexedPng,
            outputC = outputC,
            spriteMode = SpriteMode.SPR8x16,
            pivotX = 12,
            pivotY = 6,
            frameWidth = 24,
            frameHeight = 32,
            mirrorDedup = false,
        )

        assertTrue(
            args.contains("-keep_palette_order"),
            "buildPng2AssetArgs must include -keep_palette_order for an indexed (color-type 3) " +
                "PNG (D2a fix — mirrors ConvertZoneTilesetsTask isIndexedPng gate). " +
                "args: $args",
        )
    }

    // -------------------------------------------------------------------------
    // Test 2 — Non-indexed (RGB) PNG → -keep_palette_order absent
    //
    // Ensures the flag is only appended for indexed PNGs, not for RGB/RGBA inputs.
    // This preserves backward compat for games that use RGB sprite PNGs.
    // -------------------------------------------------------------------------

    @Test
    fun `buildPng2AssetArgs omits keep_palette_order for non-indexed (RGB) PNG`() {
        val rgbPng = writeRgbPng("paddle.png")
        val outputC = File(tempDir, "paddle.c")

        val args = buildPng2AssetArgs(
            pngFile = rgbPng,
            outputC = outputC,
            spriteMode = SpriteMode.SPR8x16,
            pivotX = 0,
            pivotY = 0,
            frameWidth = 8,
            frameHeight = 16,
            mirrorDedup = false,
        )

        assertFalse(
            args.contains("-keep_palette_order"),
            "buildPng2AssetArgs must NOT include -keep_palette_order for a non-indexed (RGB, " +
                "color-type 2) PNG — the flag is indexed-only (D2a gate). " +
                "args: $args",
        )
    }

    // -------------------------------------------------------------------------
    // Helpers — synthetic PNG headers (26 bytes, same layout as IsIndexedPngTest)
    // -------------------------------------------------------------------------

    private fun writeIndexedPng(name: String): File {
        val file = File(tempDir, name)
        file.writeBytes(syntheticPngHeader(colorType = 0x03.toByte()))
        return file
    }

    private fun writeRgbPng(name: String): File {
        val file = File(tempDir, name)
        file.writeBytes(syntheticPngHeader(colorType = 0x02.toByte()))
        return file
    }

    private fun syntheticPngHeader(colorType: Byte): ByteArray =
        byteArrayOf(
            // PNG signature (8 bytes)
            0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
            // IHDR length (4 bytes, big-endian) = 13
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0D.toByte(),
            // IHDR chunk type "IHDR" (4 bytes)
            0x49.toByte(), 0x48.toByte(), 0x44.toByte(), 0x52.toByte(),
            // width (4 bytes, big-endian) = 8
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x08.toByte(),
            // height (4 bytes, big-endian) = 8
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x08.toByte(),
            // bit depth (1 byte) = 8
            0x08.toByte(),
            // color type (1 byte) — caller-supplied
            colorType,
        )
}
