/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.GBCColor

// =============================================================================
// UNIFIED COLOR NAMESPACE (Plan 13.3-01, Req #7, D-12)
// Thin facade over gbkt-ir GBCColor converters.
// Replaces three legacy surfaces: gbc() / GbcColor.* / gbcHex()
// =============================================================================

/**
 * Unified color entry point for gbkt DSL — the single discoverable namespace for all color
 * operations on Game Boy Color (GBC) hardware.
 *
 * All conversion math is delegated to [GBCColor] in `gbkt-ir`; no hand-rolled bit arithmetic lives
 * here (see Don't-Hand-Roll table, Phase 13.3 RESEARCH).
 *
 * **Quick example:**
 *
 * ```kotlin
 * // Preferred — one namespace, three input formats:
 * val sky   = Color.rgb888(0, 136, 255)   // from 8-bit-per-channel web color
 * val rock  = Color.rgb555(12, 10, 8)     // from 5-bit hardware components
 * val snow  = Color.hex("#FFFFFF")        // from CSS hex string
 * val grass = Color.GREEN                 // named constant
 *
 * palette {
 *     color0(Color.WHITE)
 *     color1(sky)
 *     color2(grass)
 *     color3(Color.BLACK)
 * }
 * ```
 *
 * **Precision loss (D-15 / T-13.3-01):** [rgb888] emits a `WARNING` to stderr when any channel's
 * low 3 bits are non-zero (i.e. the color cannot be represented exactly in RGB555). Exact multiples
 * of 8 convert without a warning. Example: `Color.rgb888(255, 0, 0)` warns; `Color.rgb888(248, 0,
 * 0)` does not.
 *
 * **Migration:** This object supersedes [GbcColor] (deleted in Plan 13.3-07) and the top-level
 * [gbc] / [gbcHex] functions (deprecated in Plan 13.3-04). No `@Deprecated` annotations are added
 * here per D-16 — the old surfaces remain until all consumers migrate.
 */
object Color {

    // =========================================================================
    // Constructor functions
    // =========================================================================

    /**
     * Create a [GBCColor] from 8-bit-per-channel RGB888 components (standard web/Photoshop range).
     *
     * Converts from RGB888 → RGB555 by shifting each channel right 3 bits (delegated to
     * [GBCColor.fromRGB888] — no hand-rolled bit math). Emits a WARNING to stderr when precision is
     * lost (D-15): when any channel's low 3 bits are non-zero the resulting RGB555 color cannot
     * represent the original exactly.
     *
     * @param r Red component 0-255.
     * @param g Green component 0-255.
     * @param b Blue component 0-255.
     * @return The nearest RGB555 [GBCColor].
     */
    fun rgb888(r: Int, g: Int, b: Int): GBCColor {
        if (GBCColor.hasPrecisionLoss(r, g, b)) {
            System.err.println(
                "WARNING: Color.rgb888($r, $g, $b) loses precision in RGB555 conversion — " +
                    "low 3 bits of one or more channels are non-zero. " +
                    "Use exact multiples of 8 per channel for lossless colors."
            )
        }
        return GBCColor.fromRGB888(r, g, b)
    }

    /**
     * Create a [GBCColor] from 5-bit hardware components (raw RGB555, no conversion needed).
     *
     * Each component is in the 0-31 range matching native GBC hardware registers. Use this when you
     * already know the 5-bit values (e.g. from a hardware reference or palette editor).
     *
     * RGB555 bit layout: bits 0-4=red, 5-9=green, 10-14=blue.
     *
     * @param r Red component 0-31.
     * @param g Green component 0-31.
     * @param b Blue component 0-31.
     * @throws IllegalArgumentException if any component is outside 0-31.
     */
    fun rgb555(r: Int, g: Int, b: Int): GBCColor {
        require(r in 0..31) { "Color.rgb555 red must be 0-31, got $r" }
        require(g in 0..31) { "Color.rgb555 green must be 0-31, got $g" }
        require(b in 0..31) { "Color.rgb555 blue must be 0-31, got $b" }
        return GBCColor((r and 0x1F) or ((g and 0x1F) shl 5) or ((b and 0x1F) shl 10))
    }

    /**
     * Create a [GBCColor] from a CSS hex color string.
     *
     * Accepts `"#RRGGBB"` or `"RRGGBB"` (case-insensitive). Strips a leading `#`, parses the
     * 6-digit hex value to an integer, and delegates to [GBCColor.fromHex] (which calls
     * [GBCColor.fromRGB888] internally — no hand-rolled bit math here either).
     *
     * This preserves the hex-string ergonomics previously offered by [gbcHex].
     *
     * @param hex Color string in `"#RRGGBB"` or `"RRGGBB"` format.
     * @throws IllegalArgumentException if the string is not 6 hex digits (with optional `#`
     *   prefix).
     */
    fun hex(hex: String): GBCColor {
        val cleaned = hex.removePrefix("#")
        require(cleaned.length == 6) {
            "Color.hex requires 6 hex digits (with optional '#'), got '$hex'"
        }
        val value = cleaned.toInt(16)
        return GBCColor.fromHex(value)
    }

    // =========================================================================
    // Named constants — mirrors every constant on GbcColor (16 total)
    // Identical GBCColor values; no delegation to GbcColor to avoid coupling
    // to a surface that will be deleted in Plan 13.3-07.
    // =========================================================================

    /** Pure white (31, 31, 31) — brightest GBC color. */
    val WHITE: GBCColor = GBCColor(0x7FFF)

    /** Pure black (0, 0, 0) — darkest GBC color. */
    val BLACK: GBCColor = GBCColor(0x0000)

    /** Pure red (31, 0, 0). */
    val RED: GBCColor = GBCColor((0 shl 10) or (0 shl 5) or 31)

    /** Pure green (0, 31, 0). */
    val GREEN: GBCColor = GBCColor((0 shl 10) or (31 shl 5) or 0)

    /** Pure blue (0, 0, 31). */
    val BLUE: GBCColor = GBCColor((31 shl 10) or (0 shl 5) or 0)

    /** Yellow (31, 31, 0) — red + green mixed. */
    val YELLOW: GBCColor = GBCColor((0 shl 10) or (31 shl 5) or 31)

    /** Cyan (0, 31, 31) — green + blue mixed. */
    val CYAN: GBCColor = GBCColor((31 shl 10) or (31 shl 5) or 0)

    /** Magenta (31, 0, 31) — red + blue mixed. */
    val MAGENTA: GBCColor = GBCColor((31 shl 10) or (0 shl 5) or 31)

    /** Orange (31, 16, 0) — warm sunset color. */
    val ORANGE: GBCColor = GBCColor((0 shl 10) or (16 shl 5) or 31)

    /** Light gray (22, 22, 22) — neutral mid-bright gray. */
    val LIGHT_GRAY: GBCColor = GBCColor((22 shl 10) or (22 shl 5) or 22)

    /** Dark gray (10, 10, 10) — neutral mid-dark gray. */
    val DARK_GRAY: GBCColor = GBCColor((10 shl 10) or (10 shl 5) or 10)

    /** Brown (18, 10, 4) — earthy tone for terrain. */
    val BROWN: GBCColor = GBCColor((4 shl 10) or (10 shl 5) or 18)

    /** Pink (31, 20, 24) — soft warm pink. */
    val PINK: GBCColor = GBCColor((24 shl 10) or (20 shl 5) or 31)

    /** Lime (16, 31, 8) — bright nature green. */
    val LIME: GBCColor = GBCColor((8 shl 10) or (31 shl 5) or 16)

    /** Navy (0, 0, 16) — deep dark blue. */
    val NAVY: GBCColor = GBCColor((16 shl 10) or (0 shl 5) or 0)

    /** Teal (0, 20, 20) — muted blue-green. */
    val TEAL: GBCColor = GBCColor((20 shl 10) or (20 shl 5) or 0)
}
