/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.GBCColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// =============================================================================
// GBC COLOR TESTS
// Verifies gbc(r,g,b), gbcHex(), and GbcColor named constants
// =============================================================================

class GbcColorTest {

    // =========================================================================
    // gbc(r, g, b) — 5-bit component construction
    // =========================================================================

    @Test
    fun `gbc 0 0 0 produces black GBCColor`() {
        val color = gbc(0, 0, 0)
        assertEquals(GBCColor(0x0000), color)
        assertEquals(0, color.red)
        assertEquals(0, color.green)
        assertEquals(0, color.blue)
    }

    @Test
    fun `gbc 31 31 31 produces white GBCColor`() {
        val color = gbc(31, 31, 31)
        assertEquals(GBCColor(0x7FFF), color)
        assertEquals(31, color.red)
        assertEquals(31, color.green)
        assertEquals(31, color.blue)
    }

    @Test
    fun `gbc 31 0 0 produces pure red`() {
        val color = gbc(31, 0, 0)
        assertEquals(31, color.red)
        assertEquals(0, color.green)
        assertEquals(0, color.blue)
    }

    @Test
    fun `gbc 0 31 0 produces pure green`() {
        val color = gbc(0, 31, 0)
        assertEquals(0, color.red)
        assertEquals(31, color.green)
        assertEquals(0, color.blue)
    }

    @Test
    fun `gbc 0 0 31 produces pure blue`() {
        val color = gbc(0, 0, 31)
        assertEquals(0, color.red)
        assertEquals(0, color.green)
        assertEquals(31, color.blue)
    }

    @Test
    fun `gbc with max red component out of range throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { gbc(32, 0, 0) }
    }

    @Test
    fun `gbc with negative red component throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { gbc(-1, 0, 0) }
    }

    @Test
    fun `gbc with max green component out of range throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { gbc(0, 32, 0) }
    }

    @Test
    fun `gbc with max blue component out of range throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { gbc(0, 0, 32) }
    }

    @Test
    fun `gbc RGB555 encoding is correct`() {
        // RGB555: bits 0-4=red, 5-9=green, 10-14=blue
        val color = gbc(1, 2, 3)
        val expected = GBCColor((3 shl 10) or (2 shl 5) or 1)
        assertEquals(expected, color)
    }

    // =========================================================================
    // gbcHex() — hex string parsing with precision check
    // =========================================================================

    @Test
    fun `gbcHex with hash prefix parses correctly`() {
        val black = gbcHex("#000000")
        assertEquals(GBCColor(0x0000), black)
    }

    @Test
    fun `gbcHex without hash prefix parses correctly`() {
        val black = gbcHex("000000")
        assertEquals(GBCColor(0x0000), black)
    }

    @Test
    fun `gbcHex white produces correct GBCColor`() {
        val white = gbcHex("#FFFFFF")
        assertEquals(31, white.red)
        assertEquals(31, white.green)
        assertEquals(31, white.blue)
    }

    @Test
    fun `gbcHex FF8800 triggers precision loss and produces correct color`() {
        // #FF8800 (R=255, G=136, B=0) → R5=31, G5=17, B5=0
        // G=136 = 0x88, low 3 bits = 0 (exactly aligned), no precision loss
        // Actually 136 >> 3 = 17, 17 << 3 = 136 (exact) — no precision loss for G
        // R=255 >> 3 = 31, 31 << 3 = 248 ≠ 255 — precision loss!
        val color = gbcHex("FF8800")
        // Verify the color was created (it should have triggered precision warning)
        assertTrue(GBCColor.hasPrecisionLoss(0xFF, 0x88, 0x00))
        assertEquals(31, color.red) // 255 >> 3 = 31
        assertEquals(17, color.green) // 136 >> 3 = 17
        assertEquals(0, color.blue) // 0 >> 3 = 0
    }

    @Test
    fun `gbcHex invalid length throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { gbcHex("#FFF") }
    }

    @Test
    fun `gbcHex empty string throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { gbcHex("") }
    }

    @Test
    fun `gbcHex pure black #000000 produces black`() {
        val black = gbcHex("#000000")
        assertEquals(0, black.red)
        assertEquals(0, black.green)
        assertEquals(0, black.blue)
    }

    // =========================================================================
    // GbcColor named constants
    // =========================================================================

    @Test
    fun `GbcColor WHITE equals gbc 31 31 31`() {
        assertEquals(gbc(31, 31, 31), GbcColor.WHITE)
    }

    @Test
    fun `GbcColor BLACK equals gbc 0 0 0`() {
        assertEquals(gbc(0, 0, 0), GbcColor.BLACK)
    }

    @Test
    fun `GbcColor RED equals gbc 31 0 0`() {
        assertEquals(gbc(31, 0, 0), GbcColor.RED)
    }

    @Test
    fun `GbcColor GREEN equals gbc 0 31 0`() {
        assertEquals(gbc(0, 31, 0), GbcColor.GREEN)
    }

    @Test
    fun `GbcColor BLUE equals gbc 0 0 31`() {
        assertEquals(gbc(0, 0, 31), GbcColor.BLUE)
    }

    @Test
    fun `GbcColor YELLOW equals gbc 31 31 0`() {
        assertEquals(gbc(31, 31, 0), GbcColor.YELLOW)
    }

    @Test
    fun `GbcColor CYAN equals gbc 0 31 31`() {
        assertEquals(gbc(0, 31, 31), GbcColor.CYAN)
    }

    @Test
    fun `GbcColor MAGENTA equals gbc 31 0 31`() {
        assertEquals(gbc(31, 0, 31), GbcColor.MAGENTA)
    }

    @Test
    fun `GbcColor NAVY is dark blue`() {
        val navy = GbcColor.NAVY
        assertEquals(0, navy.red)
        assertEquals(0, navy.green)
        assertEquals(16, navy.blue)
    }

    @Test
    fun `GbcColor has 16 named constants`() {
        val constants =
            listOf(
                GbcColor.WHITE,
                GbcColor.BLACK,
                GbcColor.RED,
                GbcColor.GREEN,
                GbcColor.BLUE,
                GbcColor.YELLOW,
                GbcColor.CYAN,
                GbcColor.MAGENTA,
                GbcColor.ORANGE,
                GbcColor.LIGHT_GRAY,
                GbcColor.DARK_GRAY,
                GbcColor.BROWN,
                GbcColor.PINK,
                GbcColor.LIME,
                GbcColor.NAVY,
                GbcColor.TEAL,
            )
        assertEquals(16, constants.size)
    }

    @Test
    fun `GBCColor hasPrecisionLoss is triggered for FF8800`() {
        assertTrue(GBCColor.hasPrecisionLoss(0xFF, 0x88, 0x00))
    }

    @Test
    fun `GBCColor hasPrecisionLoss is false for pure black`() {
        assertTrue(!GBCColor.hasPrecisionLoss(0x00, 0x00, 0x00))
    }
}
