/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.GBCColor

// =============================================================================
// GBC COLOR HELPER FUNCTIONS
// =============================================================================

/**
 * Creates a [GBCColor] from 5-bit RGB components (0-31 each).
 *
 * Maps directly to Game Boy Color RGB555 format. Each component uses 5 bits:
 * - Red: bits 0-4
 * - Green: bits 5-9
 * - Blue: bits 10-14
 *
 * Usage:
 * ```kotlin
 * val white = gbc(31, 31, 31)   // GBCColor(0x7FFF)
 * val red   = gbc(31, 0, 0)     // GBCColor(0x001F)
 * val black = gbc(0, 0, 0)      // GBCColor(0x0000)
 * ```
 *
 * @param r Red component (0-31). Throws [IllegalArgumentException] if out of range.
 * @param g Green component (0-31). Throws [IllegalArgumentException] if out of range.
 * @param b Blue component (0-31). Throws [IllegalArgumentException] if out of range.
 */
fun gbc(r: Int, g: Int, b: Int): GBCColor {
    require(r in 0..31) { "GBC red component must be 0-31, got $r" }
    require(g in 0..31) { "GBC green component must be 0-31, got $g" }
    require(b in 0..31) { "GBC blue component must be 0-31, got $b" }
    return GBCColor((b shl 10) or (g shl 5) or r)
}

/**
 * Creates a [GBCColor] from a 24-bit hex color string.
 *
 * Accepts formats `"#RRGGBB"` or `"RRGGBB"` (case-insensitive). Converts from RGB888 to RGB555
 * using [GBCColor.fromRGB888]. Emits a build warning to stderr when precision is lost (i.e., the
 * hex color cannot be represented exactly in RGB555 — any channel's low 3 bits are non-zero).
 *
 * Usage:
 * ```kotlin
 * val white  = gbcHex("#FFFFFF")   // lossless (all channels multiple of 8)
 * val orange = gbcHex("#FF8800")   // WARNING: loses precision in RGB555
 * val noHash = gbcHex("008000")    // '#' prefix optional
 * ```
 *
 * @param hex Color string in `"#RRGGBB"` or `"RRGGBB"` format.
 * @throws IllegalArgumentException if the string is not 6 hex digits (with optional `#` prefix).
 */
fun gbcHex(hex: String): GBCColor {
    val cleaned = hex.removePrefix("#")
    require(cleaned.length == 6) { "GBC hex color must be 6 hex digits, got '$hex'" }
    val value = cleaned.toInt(16)
    val r = (value shr 16) and 0xFF
    val g = (value shr 8) and 0xFF
    val b = value and 0xFF
    if (GBCColor.hasPrecisionLoss(r, g, b)) {
        val color = GBCColor.fromRGB888(r, g, b)
        System.err.println(
            "WARNING: '$hex' maps to GBC (${color.red}, ${color.green}, ${color.blue}) — lossy conversion"
        )
    }
    return GBCColor.fromRGB888(r, g, b)
}

// =============================================================================
// GBC NAMED COLOR CONSTANTS
// =============================================================================

/**
 * Named GBC color constants for common palette colors.
 *
 * All values use 5-bit components (0-31) in RGB555 format, matching native GBC hardware. These
 * constants complement [DmgColor] shades for GBC-enabled games.
 *
 * Usage:
 * ```kotlin
 * color0(GbcColor.WHITE)
 * color3(GbcColor.BLACK)
 * color1(GbcColor.NAVY)
 * ```
 */
object GbcColor {
    /** Pure white (31, 31, 31) — brightest GBC color. */
    val WHITE = gbc(31, 31, 31)

    /** Pure black (0, 0, 0) — darkest GBC color. */
    val BLACK = gbc(0, 0, 0)

    /** Pure red (31, 0, 0). */
    val RED = gbc(31, 0, 0)

    /** Pure green (0, 31, 0). */
    val GREEN = gbc(0, 31, 0)

    /** Pure blue (0, 0, 31). */
    val BLUE = gbc(0, 0, 31)

    /** Yellow (31, 31, 0) — red + green mixed. */
    val YELLOW = gbc(31, 31, 0)

    /** Cyan (0, 31, 31) — green + blue mixed. */
    val CYAN = gbc(0, 31, 31)

    /** Magenta (31, 0, 31) — red + blue mixed. */
    val MAGENTA = gbc(31, 0, 31)

    /** Orange (31, 16, 0) — warm sunset color. */
    val ORANGE = gbc(31, 16, 0)

    /** Light gray (22, 22, 22) — neutral mid-bright gray. */
    val LIGHT_GRAY = gbc(22, 22, 22)

    /** Dark gray (10, 10, 10) — neutral mid-dark gray. */
    val DARK_GRAY = gbc(10, 10, 10)

    /** Brown (18, 10, 4) — earthy tone for terrain. */
    val BROWN = gbc(18, 10, 4)

    /** Pink (31, 20, 24) — soft warm pink. */
    val PINK = gbc(31, 20, 24)

    /** Lime (16, 31, 8) — bright nature green. */
    val LIME = gbc(16, 31, 8)

    /** Navy (0, 0, 16) — deep dark blue. */
    val NAVY = gbc(0, 0, 16)

    /** Teal (0, 20, 20) — muted blue-green. */
    val TEAL = gbc(0, 20, 20)
}
