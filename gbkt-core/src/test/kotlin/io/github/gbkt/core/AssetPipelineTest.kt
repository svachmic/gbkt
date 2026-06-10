/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.ir.GBCColor
import io.github.gbkt.core.ir.GBCPalette
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Expected values are hand-derived from the Game Boy 2bpp tile format: 16 bytes per 8x8 tile, 2
 * bytes per row — low bitplane byte then high bitplane byte — with bit 7 as the leftmost pixel.
 * Shade mapping uses luminance (0.299r + 0.587g + 0.114b) against the threshold palette [192, 128,
 * 64]: >=192 -> 0, >=128 -> 1, >=64 -> 2, else 3.
 */
class AssetPipelineTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("gbkt-asset-pipeline-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.walkBottomUp().forEach { it.delete() }
    }

    // -------------------------------------------------------------------------
    // Fixture helpers
    // -------------------------------------------------------------------------

    private fun gray(v: Int) = (v shl 16) or (v shl 8) or v

    private fun solidImage(
        width: Int,
        height: Int,
        rgb: Int,
        type: Int = BufferedImage.TYPE_INT_RGB,
    ): BufferedImage {
        val img = BufferedImage(width, height, type)
        for (y in 0 until height) {
            for (x in 0 until width) {
                img.setRGB(x, y, rgb or (0xFF shl 24))
            }
        }
        return img
    }

    private fun writePng(name: String, img: BufferedImage): File {
        val file = File(tempDir, name)
        ImageIO.write(img, "PNG", file)
        return file
    }

    // -------------------------------------------------------------------------
    // DMG shade mapping — one bitplane pattern per shade
    // -------------------------------------------------------------------------

    @Test
    fun `near-white image converts to all-zero tile`() {
        // Luminance 250 >= 192 -> shade 0 -> both bitplanes empty
        val sheet = AssetPipeline.convertImage(solidImage(8, 8, gray(250)))
        assertEquals(1, sheet.widthInTiles)
        assertEquals(1, sheet.heightInTiles)
        assertEquals(1, sheet.tiles.size)
        assertContentEquals(ByteArray(16), sheet.tiles[0].data)
    }

    @Test
    fun `black image converts to all-ones tile`() {
        // Luminance 0 < 64 -> shade 3 -> both bitplanes full
        val sheet = AssetPipeline.convertImage(solidImage(8, 8, gray(0)))
        assertContentEquals(ByteArray(16) { -1 }, sheet.tiles[0].data)
    }

    @Test
    fun `shade 1 gray sets only the low bitplane`() {
        // Luminance 160: < 192, >= 128 -> shade 1 -> low plane 0xFF, high plane 0x00
        val sheet = AssetPipeline.convertImage(solidImage(8, 8, gray(160)))
        val expected = ByteArray(16) { i -> if (i % 2 == 0) -1 else 0 }
        assertContentEquals(expected, sheet.tiles[0].data)
    }

    @Test
    fun `shade 2 gray sets only the high bitplane`() {
        // Luminance 96: < 128, >= 64 -> shade 2 -> low plane 0x00, high plane 0xFF
        val sheet = AssetPipeline.convertImage(solidImage(8, 8, gray(96)))
        val expected = ByteArray(16) { i -> if (i % 2 == 0) 0 else -1 }
        assertContentEquals(expected, sheet.tiles[0].data)
    }

    @Test
    fun `leftmost pixel maps to bit 7 and rightmost to bit 0`() {
        val img = solidImage(8, 8, gray(250))
        img.setRGB(0, 0, (0xFF shl 24) or gray(0)) // black at (0,0) -> bit 7 of row 0
        img.setRGB(7, 1, (0xFF shl 24) or gray(0)) // black at (7,1) -> bit 0 of row 1

        val tile = AssetPipeline.convertImage(img).tiles[0]

        assertEquals(0x80.toByte(), tile.data[0], "row 0 low plane should have bit 7 set")
        assertEquals(0x80.toByte(), tile.data[1], "row 0 high plane should have bit 7 set")
        assertEquals(0x01.toByte(), tile.data[2], "row 1 low plane should have bit 0 set")
        assertEquals(0x01.toByte(), tile.data[3], "row 1 high plane should have bit 0 set")
        for (i in 4 until 16) {
            assertEquals(0, tile.data[i], "byte $i should be empty")
        }
    }

    @Test
    fun `transparent pixels map to shade 0`() {
        val img = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        // Alpha 0 with black RGB: transparency must win over luminance
        for (y in 0 until 8) for (x in 0 until 8) img.setRGB(x, y, 0x00000000)

        val sheet = AssetPipeline.convertImage(img)
        assertContentEquals(ByteArray(16), sheet.tiles[0].data)
    }

    @Test
    fun `tiles are emitted in row-major order`() {
        // 16x16 quadrants: TL near-white (shade 0), TR gray160 (shade 1),
        // BL gray96 (shade 2), BR black (shade 3)
        val img = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val v =
                    when {
                        y < 8 && x < 8 -> gray(250)
                        y < 8 -> gray(160)
                        x < 8 -> gray(96)
                        else -> gray(0)
                    }
                img.setRGB(x, y, (0xFF shl 24) or v)
            }
        }

        val sheet = AssetPipeline.convertImage(img)
        assertEquals(2, sheet.widthInTiles)
        assertEquals(2, sheet.heightInTiles)
        assertContentEquals(ByteArray(16), sheet.tiles[0].data)
        assertContentEquals(ByteArray(16) { i -> if (i % 2 == 0) -1 else 0 }, sheet.tiles[1].data)
        assertContentEquals(ByteArray(16) { i -> if (i % 2 == 0) 0 else -1 }, sheet.tiles[2].data)
        assertContentEquals(ByteArray(16) { -1 }, sheet.tiles[3].data)
    }

    @Test
    fun `custom palette thresholds change the shade mapping`() {
        // Luminance 195: DEFAULT [192,128,64] -> shade 0; HIGH_CONTRAST [200,140,80] -> shade 1
        val img = solidImage(8, 8, gray(195))

        val defaultTile = AssetPipeline.convertImage(img).tiles[0]
        val contrastTile =
            AssetPipeline.convertImage(img, AssetPipeline.HIGH_CONTRAST_PALETTE).tiles[0]

        assertContentEquals(ByteArray(16), defaultTile.data)
        assertContentEquals(ByteArray(16) { i -> if (i % 2 == 0) -1 else 0 }, contrastTile.data)
    }

    // -------------------------------------------------------------------------
    // Error paths
    // -------------------------------------------------------------------------

    @Test
    fun `image width not a multiple of 8 throws`() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                AssetPipeline.convertImage(solidImage(12, 8, gray(0)))
            }
        assertTrue(ex.message!!.contains("width must be multiple of 8, got 12"))
    }

    @Test
    fun `image height not a multiple of 8 throws`() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                AssetPipeline.convertImage(solidImage(8, 9, gray(0)))
            }
        assertTrue(ex.message!!.contains("height must be multiple of 8, got 9"))
    }

    @Test
    fun `loadSprite with missing file throws for both overloads`() {
        val missing = File(tempDir, "missing.png")
        val byPath =
            assertFailsWith<IllegalArgumentException> { AssetPipeline.loadSprite(missing.path) }
        assertTrue(byPath.message!!.contains("Asset not found"))
        val byFile = assertFailsWith<IllegalArgumentException> { AssetPipeline.loadSprite(missing) }
        assertTrue(byFile.message!!.contains("Asset not found"))
    }

    // -------------------------------------------------------------------------
    // File loading
    // -------------------------------------------------------------------------

    @Test
    fun `loadSprite reads a PNG from disk`() {
        val file = writePng("black.png", solidImage(8, 8, gray(0)))
        val sheet = AssetPipeline.loadSprite(file.absolutePath)
        assertContentEquals(ByteArray(16) { -1 }, sheet.tiles[0].data)
    }

    // -------------------------------------------------------------------------
    // C code generation
    // -------------------------------------------------------------------------

    @Test
    fun `generateTileData emits the exact C array`() {
        val sheet = AssetPipeline.convertImage(solidImage(8, 8, gray(0)))
        val code = AssetPipeline.generateTileData("blk", sheet)

        val expected = buildString {
            appendLine("// Tile data for blk (1x1 tiles)")
            appendLine("const unsigned char blk[] = {")
            appendLine("    " + List(16) { "0xFF" }.joinToString(", "))
            appendLine("};")
            appendLine()
            appendLine("#define BLK_TILE_COUNT 1")
        }
        assertEquals(expected, code)
    }

    @Test
    fun `generateTileData separates tiles with commas except the last`() {
        val sheet = AssetPipeline.convertImage(solidImage(16, 8, gray(0)))
        val lines = AssetPipeline.generateTileData("two", sheet).lines()

        val tileLines = lines.filter { it.startsWith("    0xFF") }
        assertEquals(2, tileLines.size)
        assertTrue(tileLines[0].endsWith(","), "first tile row should end with a comma")
        assertTrue(tileLines[1].endsWith("0xFF"), "last tile row should not end with a comma")
        assertTrue(lines.contains("#define TWO_TILE_COUNT 2"))
    }

    @Test
    fun `generateAllTileData emits a header and every sprite`() {
        val sheet = AssetPipeline.convertImage(solidImage(8, 8, gray(0)))
        val code = AssetPipeline.generateAllTileData(mapOf("hero" to sheet, "coin" to sheet))

        assertTrue(code.contains("// === Sprite Tile Data ==="))
        assertTrue(code.contains("const unsigned char hero[] = {"))
        assertTrue(code.contains("const unsigned char coin[] = {"))
    }

    // -------------------------------------------------------------------------
    // Value types
    // -------------------------------------------------------------------------

    @Test
    fun `Tile equality is content-based`() {
        val a = AssetPipeline.Tile(ByteArray(16) { 7 })
        val b = AssetPipeline.Tile(ByteArray(16) { 7 })
        val c = AssetPipeline.Tile(ByteArray(16) { 8 })

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
        assertFailsWith<IllegalArgumentException> { AssetPipeline.Tile(ByteArray(15)) }
    }

    @Test
    fun `GBColor rejects out-of-range shades`() {
        for (shade in 0..3) {
            assertEquals(shade, AssetPipeline.GBColor(shade).shade)
        }
        assertFailsWith<IllegalArgumentException> { AssetPipeline.GBColor(4) }
        assertFailsWith<IllegalArgumentException> { AssetPipeline.GBColor(-1) }
    }

    // -------------------------------------------------------------------------
    // GBC: color counting and palette extraction
    // -------------------------------------------------------------------------

    @Test
    fun `countUniqueColors ignores transparent pixels`() {
        val img = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val rgb =
                    when {
                        y == 0 -> 0x00FF00FF // alpha 0: a 4th color that must not count
                        x < 3 -> (0xFF shl 24) or gray(255)
                        x < 6 -> (0xFF shl 24) or gray(128)
                        else -> (0xFF shl 24) or gray(0)
                    }
                img.setRGB(x, y, rgb)
            }
        }
        assertEquals(3, AssetPipeline.countUniqueColors(img))
    }

    @Test
    fun `extractPalette keeps the 4 most frequent colors sorted lightest first`() {
        // 64 pixels: white x30, gray192 x20, gray96 x8, black x4, red x2 (red is dropped)
        val img = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
        val pixels =
            List(30) { gray(255) } +
                List(20) { gray(192) } +
                List(8) { gray(96) } +
                List(4) { gray(0) } +
                List(2) { 0xFF0000 }
        pixels.forEachIndexed { i, rgb -> img.setRGB(i % 8, i / 8, (0xFF shl 24) or rgb) }

        val palette = AssetPipeline.extractPalette(img, "test")

        // RGB555 packing is (b5 shl 10) or (g5 shl 5) or r5 with each channel >> 3:
        // white -> 0x7FFF, gray192 -> 24*1057 = 0x6318, gray96 -> 12*1057 = 0x318C, black -> 0
        val expected =
            listOf(GBCColor(0x7FFF), GBCColor(0x6318), GBCColor(0x318C), GBCColor(0x0000))
        assertEquals(expected, palette.colors)
    }

    @Test
    fun `extractPalette pads a single-color image with evenly spaced grays`() {
        val palette = AssetPipeline.extractPalette(solidImage(8, 8, gray(255)), "mono")

        // Deficit of 3 -> step 255/4 = 63 -> padded grays at 192, 129, 66, which quantize
        // (>> 3) to 5-bit 24, 16, 8 -> 0x6318, 0x4210, 0x2108; sorted lightest first.
        val expected = listOf(GBCColor.WHITE, GBCColor(0x6318), GBCColor(0x4210), GBCColor(0x2108))
        assertEquals(expected, palette.colors)
    }

    @Test
    fun `extractPalette pads two and three color images without crashing`() {
        val twoColor = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                twoColor.setRGB(x, y, (0xFF shl 24) or if (x < 4) gray(255) else gray(0))
            }
        }
        val threeColor = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val v = if (x < 3) gray(255) else if (x < 6) gray(96) else gray(0)
                threeColor.setRGB(x, y, (0xFF shl 24) or v)
            }
        }

        for (img in listOf(twoColor, threeColor)) {
            val palette = AssetPipeline.extractPalette(img, "padded")
            assertEquals(4, palette.colors.size)
            assertEquals(4, palette.colors.toSet().size, "padded colors should be distinct")
        }
    }

    @Test
    fun `extractPalette of an empty image starts from white`() {
        // All pixels transparent: no colors counted at all
        val img = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        val palette = AssetPipeline.extractPalette(img, "empty")

        assertEquals(4, palette.colors.size)
        assertEquals(GBCColor.WHITE, palette.colors[0])
    }

    @Test
    fun `extractPalette from a PNG path`() {
        val file = writePng("mono.png", solidImage(8, 8, gray(255)))
        val palette = AssetPipeline.extractPalette(file.absolutePath, "fromFile")
        assertEquals(GBCColor.WHITE, palette.colors[0])

        val ex =
            assertFailsWith<IllegalArgumentException> {
                AssetPipeline.extractPalette(File(tempDir, "nope.png").path, "missing")
            }
        assertTrue(ex.message!!.contains("Asset not found"))
    }

    // -------------------------------------------------------------------------
    // GBC: tile conversion against a target palette
    // -------------------------------------------------------------------------

    @Test
    fun `convertImageGBC maps exact palette colors to their indices`() {
        val target =
            GBCPalette(
                "target",
                listOf(GBCColor.WHITE, GBCColor.RED, GBCColor.GREEN, GBCColor.BLACK),
            )
        val img = solidImage(8, 8, gray(255))
        img.setRGB(0, 0, (0xFF shl 24) or 0xFF0000) // red -> index 1 -> low plane
        img.setRGB(0, 1, (0xFF shl 24) or 0x00FF00) // green -> index 2 -> high plane
        img.setRGB(0, 2, (0xFF shl 24) or 0x000000) // black -> index 3 -> both planes

        val sheet = AssetPipeline.convertImageGBC(img, "ignored", target)
        val tile = sheet.tiles[0]

        assertEquals(0x80.toByte(), tile.data[0], "red pixel: row 0 low plane bit 7")
        assertEquals(0x00.toByte(), tile.data[1], "red pixel: row 0 high plane empty")
        assertEquals(0x00.toByte(), tile.data[2], "green pixel: row 1 low plane empty")
        assertEquals(0x80.toByte(), tile.data[3], "green pixel: row 1 high plane bit 7")
        assertEquals(0x80.toByte(), tile.data[4], "black pixel: row 2 low plane bit 7")
        assertEquals(0x80.toByte(), tile.data[5], "black pixel: row 2 high plane bit 7")
        assertEquals(target, sheet.extractedPalette)
        assertEquals(4, sheet.colorCount)
    }

    @Test
    fun `loadSpriteGBC extracts the palette and reports the color count`() {
        val img = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val v =
                    when {
                        y < 8 && x < 8 -> gray(255)
                        y < 8 -> gray(192)
                        x < 8 -> gray(96)
                        else -> gray(0)
                    }
                img.setRGB(x, y, (0xFF shl 24) or v)
            }
        }
        val file = writePng("quads.png", img)

        val sheet = AssetPipeline.loadSpriteGBC(file.absolutePath, "quads")

        assertEquals(4, sheet.colorCount)
        assertEquals(4, sheet.tiles.size)
        val expected =
            listOf(GBCColor(0x7FFF), GBCColor(0x6318), GBCColor(0x318C), GBCColor(0x0000))
        assertEquals(expected, sheet.extractedPalette?.colors)
    }

    @Test
    fun `generatePaletteData emits UINT16 RGB555 literals`() {
        val palette =
            GBCPalette("hud", listOf(GBCColor.WHITE, GBCColor.BLACK, GBCColor.RED, GBCColor.GREEN))
        val code = AssetPipeline.generatePaletteData(palette)

        assertTrue(code.contains("// GBC Palette: hud"))
        assertTrue(code.contains("const UINT16 hud_pal[] = { 0x7FFF, 0x0000, 0x001F, 0x03E0 };"))
    }
}
