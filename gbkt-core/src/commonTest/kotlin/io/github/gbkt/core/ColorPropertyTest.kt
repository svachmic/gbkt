/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.ir.GBCColor
import io.github.gbkt.core.ir.GBCPalette
import io.github.gbkt.core.ir.PaletteType
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

// =============================================================================
// GBCColor PROPERTY TESTS
// =============================================================================

class GBCColorPropertyTest {

    // -------------------------------------------------------------------------
    // Construction & Validation
    // -------------------------------------------------------------------------

    @Test
    fun `GBCColor construction with valid RGB555 succeeds`() = runTest {
        checkAll(Arb.gbcColorFull()) { color -> assertTrue(color.rgb555 in 0..0x7FFF) }
    }

    @Test
    fun `GBCColor construction with invalid values fails`() {
        assertFailsWith<IllegalArgumentException> { GBCColor(-1) }
        assertFailsWith<IllegalArgumentException> { GBCColor(0x8000) }
    }

    // -------------------------------------------------------------------------
    // RGB Component Extraction
    // -------------------------------------------------------------------------

    @Test
    fun `GBCColor red component is 5-bit`() = runTest {
        checkAll(Arb.gbcColorFull()) { color ->
            assertTrue(color.red in 0..31, "Red ${color.red} should be 0-31")
        }
    }

    @Test
    fun `GBCColor green component is 5-bit`() = runTest {
        checkAll(Arb.gbcColorFull()) { color ->
            assertTrue(color.green in 0..31, "Green ${color.green} should be 0-31")
        }
    }

    @Test
    fun `GBCColor blue component is 5-bit`() = runTest {
        checkAll(Arb.gbcColorFull()) { color ->
            assertTrue(color.blue in 0..31, "Blue ${color.blue} should be 0-31")
        }
    }

    @Test
    fun `GBCColor components reconstruct original RGB555`() = runTest {
        checkAll(Arb.gbcColorFull()) { color ->
            val reconstructed = (color.blue shl 10) or (color.green shl 5) or color.red
            assertEquals(color.rgb555, reconstructed)
        }
    }

    // -------------------------------------------------------------------------
    // RGB888 to RGB555 Conversion
    // -------------------------------------------------------------------------

    @Test
    fun `fromRGB888 produces valid RGB555 colors`() = runTest {
        checkAll(Arb.rgb888Component(), Arb.rgb888Component(), Arb.rgb888Component()) { r, g, b ->
            val color = GBCColor.fromRGB888(r, g, b)
            assertTrue(color.rgb555 in 0..0x7FFF)
        }
    }

    @Test
    fun `fromRGB888 preserves relative brightness order`() = runTest {
        checkAll(Arb.int(0, 247), Arb.int(0, 247)) { r1, r2 ->
            val c1 = GBCColor.fromRGB888(r1, 0, 0)
            val c2 = GBCColor.fromRGB888(r2, 0, 0)
            // If r1 is significantly larger than r2 (accounting for 8->5 bit truncation)
            if (r1 >= r2 + 8) {
                assertTrue(c1.red >= c2.red, "Higher RGB888 should produce >= RGB555 red")
            }
        }
    }

    @Test
    fun `fromRGB888 maximum values produce maximum components`() {
        val white = GBCColor.fromRGB888(255, 255, 255)
        assertEquals(31, white.red)
        assertEquals(31, white.green)
        assertEquals(31, white.blue)
    }

    @Test
    fun `fromRGB888 minimum values produce minimum components`() {
        val black = GBCColor.fromRGB888(0, 0, 0)
        assertEquals(0, black.red)
        assertEquals(0, black.green)
        assertEquals(0, black.blue)
    }

    @Test
    fun `fromRGB888 rejects invalid component values`() {
        assertFailsWith<IllegalArgumentException> { GBCColor.fromRGB888(-1, 0, 0) }
        assertFailsWith<IllegalArgumentException> { GBCColor.fromRGB888(0, -1, 0) }
        assertFailsWith<IllegalArgumentException> { GBCColor.fromRGB888(0, 0, -1) }
        assertFailsWith<IllegalArgumentException> { GBCColor.fromRGB888(256, 0, 0) }
        assertFailsWith<IllegalArgumentException> { GBCColor.fromRGB888(0, 256, 0) }
        assertFailsWith<IllegalArgumentException> { GBCColor.fromRGB888(0, 0, 256) }
    }

    // -------------------------------------------------------------------------
    // fromHex Conversion
    // -------------------------------------------------------------------------

    @Test
    fun `fromHex produces valid colors for valid hex values`() = runTest {
        checkAll(Arb.int(0, 0xFFFFFF)) { hex ->
            val color = GBCColor.fromHex(hex)
            assertTrue(color.rgb555 in 0..0x7FFF)
        }
    }

    @Test
    fun `fromHex extracts components correctly`() {
        // 0xFF0000 = red
        val red = GBCColor.fromHex(0xFF0000)
        assertEquals(31, red.red)
        assertEquals(0, red.green)
        assertEquals(0, red.blue)

        // 0x00FF00 = green
        val green = GBCColor.fromHex(0x00FF00)
        assertEquals(0, green.red)
        assertEquals(31, green.green)
        assertEquals(0, green.blue)

        // 0x0000FF = blue
        val blue = GBCColor.fromHex(0x0000FF)
        assertEquals(0, blue.red)
        assertEquals(0, blue.green)
        assertEquals(31, blue.blue)
    }

    // -------------------------------------------------------------------------
    // toHex Output
    // -------------------------------------------------------------------------

    @Test
    fun `toHex produces valid hex strings`() = runTest {
        checkAll(Arb.gbcColorFull()) { color ->
            val hex = color.toHex()
            assertTrue(hex.startsWith("0x"), "Should start with 0x: $hex")
            assertEquals(6, hex.length, "Should be 6 chars (0xNNNN): $hex")
            // Verify the hex value matches
            val parsed = hex.substring(2).toInt(16)
            assertEquals(color.rgb555, parsed)
        }
    }

    // -------------------------------------------------------------------------
    // Predefined Colors
    // -------------------------------------------------------------------------

    @Test
    fun `predefined WHITE is all 31s`() {
        assertEquals(0x7FFF, GBCColor.WHITE.rgb555)
        assertEquals(31, GBCColor.WHITE.red)
        assertEquals(31, GBCColor.WHITE.green)
        assertEquals(31, GBCColor.WHITE.blue)
    }

    @Test
    fun `predefined BLACK is all 0s`() {
        assertEquals(0x0000, GBCColor.BLACK.rgb555)
        assertEquals(0, GBCColor.BLACK.red)
        assertEquals(0, GBCColor.BLACK.green)
        assertEquals(0, GBCColor.BLACK.blue)
    }

    @Test
    fun `predefined RED has only red component`() {
        assertEquals(31, GBCColor.RED.red)
        assertEquals(0, GBCColor.RED.green)
        assertEquals(0, GBCColor.RED.blue)
    }

    @Test
    fun `predefined GREEN has only green component`() {
        assertEquals(0, GBCColor.GREEN.red)
        assertEquals(31, GBCColor.GREEN.green)
        assertEquals(0, GBCColor.GREEN.blue)
    }

    @Test
    fun `predefined BLUE has only blue component`() {
        assertEquals(0, GBCColor.BLUE.red)
        assertEquals(0, GBCColor.BLUE.green)
        assertEquals(31, GBCColor.BLUE.blue)
    }
}

// =============================================================================
// GBCPalette PROPERTY TESTS
// =============================================================================

class GBCPalettePropertyTest {

    // -------------------------------------------------------------------------
    // Construction & Validation
    // -------------------------------------------------------------------------

    @Test
    fun `GBCPalette requires exactly 4 colors`() {
        val validColors =
            listOf(GBCColor.BLACK, GBCColor.DARK_GRAY, GBCColor.LIGHT_GRAY, GBCColor.WHITE)
        // Valid - 4 colors
        val palette = GBCPalette("test", validColors)
        assertEquals(4, palette.colors.size)

        // Invalid - 3 colors
        assertFailsWith<IllegalArgumentException> { GBCPalette("test", validColors.take(3)) }

        // Invalid - 5 colors
        assertFailsWith<IllegalArgumentException> {
            GBCPalette("test", validColors + GBCColor.BLACK)
        }
    }

    @Test
    fun `GBCPalette slot must be -1 to 7`() {
        val colors = listOf(GBCColor.BLACK, GBCColor.DARK_GRAY, GBCColor.LIGHT_GRAY, GBCColor.WHITE)

        // Valid slots
        GBCPalette("test", colors, slot = -1) // auto
        GBCPalette("test", colors, slot = 0)
        GBCPalette("test", colors, slot = 7)

        // Invalid slots
        assertFailsWith<IllegalArgumentException> { GBCPalette("test", colors, slot = -2) }
        assertFailsWith<IllegalArgumentException> { GBCPalette("test", colors, slot = 8) }
    }

    // -------------------------------------------------------------------------
    // Realistic Palette Generation
    // -------------------------------------------------------------------------

    @Test
    fun `generated palettes have 4 colors`() = runTest {
        checkAll(Arb.gbcPalette()) { colors -> assertEquals(4, colors.size) }
    }

    @Test
    fun `generated palettes have ascending brightness`() = runTest {
        checkAll(Arb.gbcPalette()) { colors ->
            // Each color should be brighter than the previous
            colors.windowed(2).forEach { (dark, light) ->
                val darkBrightness = dark.red + dark.green + dark.blue
                val lightBrightness = light.red + light.green + light.blue
                assertTrue(
                    lightBrightness >= darkBrightness,
                    "Colors should be in ascending brightness order",
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // toRGB555Array
    // -------------------------------------------------------------------------

    @Test
    fun `toRGB555Array returns correct values`() {
        val colors = listOf(GBCColor(0x0000), GBCColor(0x1000), GBCColor(0x2000), GBCColor(0x3000))
        val palette = GBCPalette("test", colors)
        val array = palette.toRGB555Array()

        assertEquals(4, array.size)
        assertEquals(0x0000, array[0])
        assertEquals(0x1000, array[1])
        assertEquals(0x2000, array[2])
        assertEquals(0x3000, array[3])
    }

    // -------------------------------------------------------------------------
    // toCArrayLiteral
    // -------------------------------------------------------------------------

    @Test
    fun `toCArrayLiteral produces valid C syntax`() {
        val colors = listOf(GBCColor(0x0000), GBCColor(0x0001), GBCColor(0x0010), GBCColor(0x0100))
        val palette = GBCPalette("test", colors)
        val literal = palette.toCArrayLiteral()

        assertTrue(literal.contains("0x0000"))
        assertTrue(literal.contains("0x0001"))
        assertTrue(literal.contains("0x0010"))
        assertTrue(literal.contains("0x0100"))
        assertTrue(literal.contains(", "))
    }

    // -------------------------------------------------------------------------
    // PaletteType
    // -------------------------------------------------------------------------

    @Test
    fun `palette type defaults to SPRITE`() {
        val colors = listOf(GBCColor.BLACK, GBCColor.DARK_GRAY, GBCColor.LIGHT_GRAY, GBCColor.WHITE)
        val palette = GBCPalette("test", colors)
        assertEquals(PaletteType.SPRITE, palette.type)
    }

    @Test
    fun `palette type can be set to BACKGROUND`() {
        val colors = listOf(GBCColor.BLACK, GBCColor.DARK_GRAY, GBCColor.LIGHT_GRAY, GBCColor.WHITE)
        val palette = GBCPalette("test", colors, type = PaletteType.BACKGROUND)
        assertEquals(PaletteType.BACKGROUND, palette.type)
    }
}
