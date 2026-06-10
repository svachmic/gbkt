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

/**
 * Game Boy color utilities.
 *
 * Handles conversion between standard RGB colors and Game Boy Color's 15-bit color space (5 bits
 * per channel, BGR format).
 */
object GbColors {

    /** Classic DMG (original Game Boy) green palette. */
    val DMG_PALETTE =
        listOf(
            Color(155, 188, 15), // Lightest (white)
            Color(139, 172, 15), // Light gray
            Color(48, 98, 48), // Dark gray
            Color(15, 56, 15), // Darkest (black)
        )

    /** Standard 2BPP grayscale palette. */
    val GRAYSCALE_PALETTE =
        listOf(
            Color(255, 255, 255), // White (0)
            Color(170, 170, 170), // Light gray (1)
            Color(85, 85, 85), // Dark gray (2)
            Color(0, 0, 0), // Black (3)
        )

    /**
     * Converts an RGB color to GBC 15-bit format.
     *
     * GBC uses BGR555 format: BBBBBGGG GGRRRRR (little-endian) Each channel is 5 bits (0-31).
     */
    fun rgbToGbc(color: Color): Int {
        val r = (color.red * 31 / 255) and 0x1F
        val g = (color.green * 31 / 255) and 0x1F
        val b = (color.blue * 31 / 255) and 0x1F
        return (b shl 10) or (g shl 5) or r
    }

    /** Converts a GBC 15-bit color to RGB. */
    fun gbcToRgb(gbc: Int): Color {
        val r = (gbc and 0x1F) * 255 / 31
        val g = ((gbc shr 5) and 0x1F) * 255 / 31
        val b = ((gbc shr 10) and 0x1F) * 255 / 31
        return Color(r, g, b)
    }

    /** Formats a GBC color as a hex string. */
    fun formatGbcHex(gbc: Int): String {
        return "0x${gbc.toString(16).uppercase().padStart(4, '0')}"
    }

    /** Formats a color as RGB8(r,g,b) for gbkt code. */
    fun formatRgb8(color: Color): String {
        return "RGB8(${color.red}, ${color.green}, ${color.blue})"
    }

    /**
     * Finds the closest 2BPP palette index for a given color.
     *
     * Returns 0-3 representing the closest grayscale match.
     */
    fun findClosest2bppIndex(color: Color): Int {
        val gray = (color.red * 0.299 + color.green * 0.587 + color.blue * 0.114).toInt()
        return when {
            gray >= 213 -> 0 // White
            gray >= 128 -> 1 // Light gray
            gray >= 43 -> 2 // Dark gray
            else -> 3 // Black
        }
    }

    /** Checks if a color is a valid 2BPP color (one of the 4 grayscale values). */
    fun isValid2bppColor(color: Color): Boolean {
        return GRAYSCALE_PALETTE.any { palette ->
            palette.red == color.red && palette.green == color.green && palette.blue == color.blue
        }
    }

    /**
     * Validates that an image only uses valid 2BPP colors.
     *
     * @return List of invalid colors found, empty if valid
     */
    fun validateImage2bpp(colors: Set<Color>): List<Color> {
        return colors.filterNot { isValid2bppColor(it) }
    }

    /** Quantizes a color to the nearest valid 2BPP color. */
    fun quantizeTo2bpp(color: Color): Color {
        val index = findClosest2bppIndex(color)
        return GRAYSCALE_PALETTE[index]
    }

    /** Applies a palette to a 2BPP index. */
    fun applyPalette(index: Int, palette: List<Color>): Color {
        return palette.getOrElse(index.coerceIn(0, 3)) { GRAYSCALE_PALETTE[0] }
    }
}
