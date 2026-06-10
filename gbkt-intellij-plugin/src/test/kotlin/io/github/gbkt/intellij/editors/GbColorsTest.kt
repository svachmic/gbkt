/*
 * Copyright 2026 Michal Svacha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.gbkt.intellij.editors

import java.awt.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for GbColors utility object. */
@Suppress("TooManyFunctions")
class GbColorsTest {

    @Test
    fun `DMG palette has 4 colors`() {
        assertEquals(4, GbColors.DMG_PALETTE.size)
    }

    @Test
    fun `grayscale palette has 4 colors`() {
        assertEquals(4, GbColors.GRAYSCALE_PALETTE.size)
    }

    @Test
    fun `grayscale palette starts with white`() {
        assertEquals(Color.WHITE, GbColors.GRAYSCALE_PALETTE[0])
    }

    @Test
    fun `grayscale palette ends with black`() {
        assertEquals(Color.BLACK, GbColors.GRAYSCALE_PALETTE[3])
    }

    @Test
    fun `rgbToGbc converts black correctly`() {
        val gbc = GbColors.rgbToGbc(Color.BLACK)
        assertEquals(0, gbc)
    }

    @Test
    fun `rgbToGbc converts white correctly`() {
        val gbc = GbColors.rgbToGbc(Color.WHITE)
        assertEquals(0x7FFF, gbc)
    }

    @Test
    fun `rgbToGbc converts red correctly`() {
        val gbc = GbColors.rgbToGbc(Color.RED)
        // Red in BGR555: B=0, G=0, R=31 = 0x001F
        assertEquals(0x001F, gbc)
    }

    @Test
    fun `rgbToGbc converts green correctly`() {
        val gbc = GbColors.rgbToGbc(Color.GREEN)
        // Green in BGR555: B=0, G=31, R=0 = 0x03E0
        assertEquals(0x03E0, gbc)
    }

    @Test
    fun `rgbToGbc converts blue correctly`() {
        val gbc = GbColors.rgbToGbc(Color.BLUE)
        // Blue in BGR555: B=31, G=0, R=0 = 0x7C00
        assertEquals(0x7C00, gbc)
    }

    @Test
    fun `gbcToRgb converts black correctly`() {
        val color = GbColors.gbcToRgb(0)
        assertEquals(Color.BLACK, color)
    }

    @Test
    fun `gbcToRgb converts white correctly`() {
        val color = GbColors.gbcToRgb(0x7FFF)
        assertEquals(Color.WHITE, color)
    }

    @Test
    fun `rgbToGbc and gbcToRgb are inverse operations`() {
        // Test with some colors
        val testColors = listOf(Color.BLACK, Color.WHITE, Color(128, 128, 128), Color(64, 128, 192))

        for (original in testColors) {
            val gbc = GbColors.rgbToGbc(original)
            val roundTrip = GbColors.gbcToRgb(gbc)
            // Allow small deviation due to 5-bit quantization
            assertTrue(
                "Color ${original} should round-trip correctly",
                kotlin.math.abs(original.red - roundTrip.red) <= 8 &&
                    kotlin.math.abs(original.green - roundTrip.green) <= 8 &&
                    kotlin.math.abs(original.blue - roundTrip.blue) <= 8,
            )
        }
    }

    @Test
    fun `formatGbcHex formats correctly`() {
        assertEquals("0x0000", GbColors.formatGbcHex(0))
        assertEquals("0x7FFF", GbColors.formatGbcHex(0x7FFF))
        assertEquals("0x001F", GbColors.formatGbcHex(0x001F))
    }

    @Test
    fun `formatRgb8 formats correctly`() {
        assertEquals("RGB8(255, 255, 255)", GbColors.formatRgb8(Color.WHITE))
        assertEquals("RGB8(0, 0, 0)", GbColors.formatRgb8(Color.BLACK))
        assertEquals("RGB8(255, 0, 0)", GbColors.formatRgb8(Color.RED))
    }

    @Test
    fun `findClosest2bppIndex returns 0 for white`() {
        assertEquals(0, GbColors.findClosest2bppIndex(Color.WHITE))
    }

    @Test
    fun `findClosest2bppIndex returns 3 for black`() {
        assertEquals(3, GbColors.findClosest2bppIndex(Color.BLACK))
    }

    @Test
    fun `findClosest2bppIndex returns correct index for light gray`() {
        val lightGray = Color(170, 170, 170)
        val index = GbColors.findClosest2bppIndex(lightGray)
        assertTrue("Light gray should be 0 or 1", index in 0..1)
    }

    @Test
    fun `findClosest2bppIndex returns correct index for dark gray`() {
        val darkGray = Color(85, 85, 85)
        val index = GbColors.findClosest2bppIndex(darkGray)
        assertTrue("Dark gray should be 2 or 3", index in 2..3)
    }

    @Test
    fun `isValid2bppColor accepts grayscale palette colors`() {
        for (color in GbColors.GRAYSCALE_PALETTE) {
            assertTrue("$color should be valid 2BPP", GbColors.isValid2bppColor(color))
        }
    }

    @Test
    fun `isValid2bppColor rejects non-palette colors`() {
        assertFalse(GbColors.isValid2bppColor(Color.RED))
        assertFalse(GbColors.isValid2bppColor(Color.GREEN))
        assertFalse(GbColors.isValid2bppColor(Color.BLUE))
        assertFalse(GbColors.isValid2bppColor(Color(100, 100, 100)))
    }

    @Test
    fun `validateImage2bpp returns empty for valid image`() {
        val validColors = GbColors.GRAYSCALE_PALETTE.toSet()
        val invalid = GbColors.validateImage2bpp(validColors)
        assertTrue("Valid 2BPP image should have no invalid colors", invalid.isEmpty())
    }

    @Test
    fun `validateImage2bpp returns invalid colors`() {
        val mixedColors = setOf(Color.WHITE, Color.BLACK, Color.RED, Color.BLUE)
        val invalid = GbColors.validateImage2bpp(mixedColors)
        assertEquals(2, invalid.size)
        assertTrue(Color.RED in invalid)
        assertTrue(Color.BLUE in invalid)
    }

    @Test
    fun `quantizeTo2bpp returns palette color`() {
        val quantized = GbColors.quantizeTo2bpp(Color.RED)
        assertTrue("Quantized color should be in palette", quantized in GbColors.GRAYSCALE_PALETTE)
    }

    @Test
    fun `quantizeTo2bpp preserves palette colors`() {
        for (color in GbColors.GRAYSCALE_PALETTE) {
            assertEquals(color, GbColors.quantizeTo2bpp(color))
        }
    }

    @Test
    fun `applyPalette returns correct color for index`() {
        val palette = GbColors.DMG_PALETTE
        assertEquals(palette[0], GbColors.applyPalette(0, palette))
        assertEquals(palette[1], GbColors.applyPalette(1, palette))
        assertEquals(palette[2], GbColors.applyPalette(2, palette))
        assertEquals(palette[3], GbColors.applyPalette(3, palette))
    }

    @Test
    fun `applyPalette clamps out of range indices`() {
        val palette = GbColors.DMG_PALETTE
        assertEquals(palette[0], GbColors.applyPalette(-1, palette))
        assertEquals(palette[3], GbColors.applyPalette(10, palette))
    }
}
