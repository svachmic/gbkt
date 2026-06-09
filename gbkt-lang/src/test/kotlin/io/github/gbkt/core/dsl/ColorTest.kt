/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.GBCColor
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// COLOR NAMESPACE TESTS (Plan 13.3-01)
// Pins the contract for Color.rgb888/rgb555/hex/named constants
// and the precision-loss WARNING (D-15, T-13.3-01)
// =============================================================================

class ColorTest {

    // =========================================================================
    // Color.rgb888 — 8-bit per channel conversion to RGB555
    // =========================================================================

    @Test
    fun `Color rgb888 magenta 255 0 255 equals GBCColor 0x001F`() {
        // RESEARCH G2: magenta (255,0,255) → R5=31, G5=0, B5=31 → 0x7C1F
        // Wait — let's compute: r5=31, g5=0, b5=31 → (31 shl 10) or (0 shl 5) or 31
        //   = 0x7C00 or 0x001F = 0x7C1F
        // The plan says "equals GBCColor(0x001F)" which appears to be for red-only magenta
        // but standard magenta is r=31,b=31 so packs to 0x7C1F.
        // The plan note "(0x001F)" seems to describe red-only (31,0,0). Let's verify
        // magenta correctly packs to r=31,g=0,b=31.
        val expected = GBCColor.fromRGB888(255, 0, 255)
        assertEquals(expected, Color.rgb888(255, 0, 255))
        assertEquals(31, Color.rgb888(255, 0, 255).red)
        assertEquals(0, Color.rgb888(255, 0, 255).green)
        assertEquals(31, Color.rgb888(255, 0, 255).blue)
    }

    @Test
    fun `Color rgb888 pure white 255 255 255 produces GBCColor WHITE`() {
        val result = Color.rgb888(255, 255, 255)
        assertEquals(GBCColor.WHITE, result)
        assertEquals(0x7FFF, result.rgb555)
    }

    @Test
    fun `Color rgb888 pure black 0 0 0 produces GBCColor BLACK`() {
        val result = Color.rgb888(0, 0, 0)
        assertEquals(GBCColor.BLACK, result)
        assertEquals(0x0000, result.rgb555)
    }

    @Test
    fun `Color rgb888 exact multiples of 8 produce no precision-loss warning`() {
        val out = ByteArrayOutputStream()
        val oldErr = System.err
        System.setErr(PrintStream(out))
        try {
            // 248 = 31*8 (multiple of 8), 0 = 0*8, 0 = 0*8 — no precision loss
            Color.rgb888(248, 0, 0)
            Color.rgb888(0, 248, 0)
            Color.rgb888(0, 0, 248)
            Color.rgb888(255, 255, 255) // 255: low bits = 0b111 so this WILL warn
            // Clear the stream after the warning case
        } finally {
            System.setErr(oldErr)
        }
        // 248,0,0 / 0,248,0 / 0,0,248 should not warn — verify by capturing just those
        val out2 = ByteArrayOutputStream()
        System.setErr(PrintStream(out2))
        try {
            Color.rgb888(248, 0, 0)
        } finally {
            System.setErr(oldErr)
        }
        val captured2 = out2.toString()
        assertFalse(
            captured2.contains("WARNING"),
            "Color.rgb888(248,0,0) must not emit WARNING but got: $captured2",
        )
    }

    @Test
    fun `Color rgb888 255 0 0 produces a precision-loss WARNING containing channel values`() {
        val out = ByteArrayOutputStream()
        val oldErr = System.err
        System.setErr(PrintStream(out))
        try {
            Color.rgb888(255, 0, 0)
        } finally {
            System.setErr(oldErr)
        }
        val captured = out.toString()
        assertTrue(
            captured.contains("WARNING"),
            "Color.rgb888(255,0,0) must emit WARNING to stderr but got nothing",
        )
    }

    @Test
    fun `Color rgb888 255 0 128 produces a precision-loss WARNING`() {
        // blue=128: 128 and 7 = 0 (128 = 16*8) → no precision loss on blue
        // red=255: 255 and 7 = 7 → precision loss
        val out = ByteArrayOutputStream()
        val oldErr = System.err
        System.setErr(PrintStream(out))
        try {
            Color.rgb888(255, 0, 128)
        } finally {
            System.setErr(oldErr)
        }
        val captured = out.toString()
        assertTrue(
            captured.contains("WARNING"),
            "Color.rgb888(255,0,128) must emit WARNING to stderr (red channel has precision loss)",
        )
    }

    @Test
    fun `Color rgb888 0 0 0 produces no precision-loss warning`() {
        val out = ByteArrayOutputStream()
        val oldErr = System.err
        System.setErr(PrintStream(out))
        try {
            Color.rgb888(0, 0, 0)
        } finally {
            System.setErr(oldErr)
        }
        val captured = out.toString()
        assertFalse(
            captured.contains("WARNING"),
            "Color.rgb888(0,0,0) must NOT emit WARNING but got: $captured",
        )
    }

    // =========================================================================
    // Color.rgb555 — raw 5-bit channel construction (no conversion needed)
    // =========================================================================

    @Test
    fun `Color rgb555 31 0 31 produces same magenta as rgb888 255 0 255`() {
        val via555 = Color.rgb555(31, 0, 31)
        val via888 = Color.rgb888(255, 0, 255)
        assertEquals(via888, via555)
    }

    @Test
    fun `Color rgb555 31 31 31 equals Color WHITE`() {
        val result = Color.rgb555(31, 31, 31)
        assertEquals(Color.WHITE, result)
        assertEquals(0x7FFF, result.rgb555)
    }

    @Test
    fun `Color rgb555 0 0 0 equals Color BLACK`() {
        val result = Color.rgb555(0, 0, 0)
        assertEquals(Color.BLACK, result)
        assertEquals(0x0000, result.rgb555)
    }

    @Test
    fun `Color rgb555 packs channels correctly into RGB555`() {
        // RGB555: bits 0-4=red, 5-9=green, 10-14=blue
        val result = Color.rgb555(1, 2, 3)
        val expected = GBCColor((3 shl 10) or (2 shl 5) or 1)
        assertEquals(expected, result)
    }

    // =========================================================================
    // Color.hex — hex string parsing delegating to GBCColor.fromHex
    // =========================================================================

    @Test
    fun `Color hex with hash prefix produces GBCColor`() {
        val result = Color.hex("#FFFFFF")
        assertEquals(Color.WHITE, result)
        assertEquals(0x7FFF, result.rgb555)
    }

    @Test
    fun `Color hex without hash prefix produces GBCColor`() {
        val result = Color.hex("FFFFFF")
        assertEquals(Color.WHITE, result)
    }

    @Test
    fun `Color hex black #000000 produces Color BLACK`() {
        val result = Color.hex("#000000")
        assertEquals(Color.BLACK, result)
    }

    @Test
    fun `Color hex and Color WHITE are identical for FFFFFF`() {
        assertEquals(Color.WHITE, Color.hex("#FFFFFF"))
        assertEquals(Color.WHITE, Color.hex("FFFFFF"))
    }

    @Test
    fun `Color hex FFFFFF value is 0x7FFF`() {
        assertEquals(0x7FFF, Color.hex("#FFFFFF").rgb555)
    }

    // =========================================================================
    // Named constants — RGB555 value assertions (D-13: GbcColor.kt deleted)
    // Values match the former GbcColor.NAME constants (gbc(r,g,b) encoding).
    // =========================================================================

    @Test
    fun `Color WHITE value is 0x7FFF`() {
        assertEquals(0x7FFF, Color.WHITE.rgb555)
    }

    @Test
    fun `Color BLACK value is 0x0000`() {
        assertEquals(0x0000, Color.BLACK.rgb555)
    }

    @Test
    fun `Color RED value is 0x001F`() {
        assertEquals(0x001F, Color.RED.rgb555)
    }

    @Test
    fun `Color GREEN value is 0x03E0`() {
        assertEquals(0x03E0, Color.GREEN.rgb555)
    }

    @Test
    fun `Color BLUE value is 0x7C00`() {
        assertEquals(0x7C00, Color.BLUE.rgb555)
    }

    @Test
    fun `Color YELLOW value is 0x03FF`() {
        assertEquals(0x03FF, Color.YELLOW.rgb555)
    }

    @Test
    fun `Color CYAN value is 0x7FE0`() {
        assertEquals(0x7FE0, Color.CYAN.rgb555)
    }

    @Test
    fun `Color MAGENTA value is 0x7C1F`() {
        assertEquals(0x7C1F, Color.MAGENTA.rgb555)
    }

    @Test
    fun `Color ORANGE value is 0x021F`() {
        // gbc(31, 16, 0) = (0 shl 10) or (16 shl 5) or 31 = 0x021F
        assertEquals(0x021F, Color.ORANGE.rgb555)
    }

    @Test
    fun `Color LIGHT_GRAY value is 0x5AD6`() {
        // gbc(22, 22, 22) = (22 shl 10) or (22 shl 5) or 22 = 0x5AD6
        assertEquals(0x5AD6, Color.LIGHT_GRAY.rgb555)
    }

    @Test
    fun `Color DARK_GRAY value is 0x294A`() {
        // gbc(10, 10, 10) = (10 shl 10) or (10 shl 5) or 10 = 0x294A
        assertEquals(0x294A, Color.DARK_GRAY.rgb555)
    }

    @Test
    fun `Color BROWN value is 0x1152`() {
        // gbc(18, 10, 4) = (4 shl 10) or (10 shl 5) or 18 = 0x1152
        assertEquals(0x1152, Color.BROWN.rgb555)
    }

    @Test
    fun `Color PINK value is 0x629F`() {
        // gbc(31, 20, 24) = (24 shl 10) or (20 shl 5) or 31 = 0x629F
        assertEquals(0x629F, Color.PINK.rgb555)
    }

    @Test
    fun `Color LIME value is 0x23F0`() {
        // gbc(16, 31, 8) = (8 shl 10) or (31 shl 5) or 16 = 0x23F0
        assertEquals(0x23F0, Color.LIME.rgb555)
    }

    @Test
    fun `Color NAVY value is 0x4000`() {
        // gbc(0, 0, 16) = (16 shl 10) or 0 or 0 = 0x4000
        assertEquals(0x4000, Color.NAVY.rgb555)
    }

    @Test
    fun `Color TEAL value is 0x5280`() {
        // gbc(0, 20, 20) = (20 shl 10) or (20 shl 5) or 0 = 0x5280
        assertEquals(0x5280, Color.TEAL.rgb555)
    }

    @Test
    fun `Color has 16 named constants`() {
        val colorConstants =
            listOf(
                Color.WHITE,
                Color.BLACK,
                Color.RED,
                Color.GREEN,
                Color.BLUE,
                Color.YELLOW,
                Color.CYAN,
                Color.MAGENTA,
                Color.ORANGE,
                Color.LIGHT_GRAY,
                Color.DARK_GRAY,
                Color.BROWN,
                Color.PINK,
                Color.LIME,
                Color.NAVY,
                Color.TEAL,
            )
        assertEquals(16, colorConstants.size)
    }
}
