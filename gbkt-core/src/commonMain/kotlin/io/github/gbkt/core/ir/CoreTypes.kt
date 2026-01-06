/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("NOTHING_TO_INLINE")

package io.github.gbkt.core.ir

// =============================================================================
// PRIMITIVE TYPES - Feel like Kotlin, compile to C
// =============================================================================

/** GB-safe unsigned 8-bit integer. Supports all standard Kotlin operators. */
@kotlin.jvm.JvmInline
value class u8(val raw: Int) : Comparable<u8> {
    init {
        require(raw in 0..255) { "u8 must be 0-255, got $raw" }
    }

    operator fun plus(other: u8) = u8((raw + other.raw) and 0xFF)

    operator fun plus(other: Int) = u8((raw + other) and 0xFF)

    operator fun minus(other: u8) = u8((raw - other.raw) and 0xFF)

    operator fun minus(other: Int) = u8((raw - other) and 0xFF)

    operator fun times(other: u8) = u8((raw * other.raw) and 0xFF)

    operator fun times(other: Int) = u8((raw * other) and 0xFF)

    operator fun div(other: u8) = u8(raw / other.raw)

    operator fun div(other: Int) = u8(raw / other)

    operator fun rem(other: u8) = u8(raw % other.raw)

    operator fun rem(other: Int) = u8(raw % other)

    operator fun inc() = u8((raw + 1) and 0xFF)

    operator fun dec() = u8((raw - 1) and 0xFF)

    operator fun unaryMinus() = u8((-raw) and 0xFF)

    infix fun and(other: u8) = u8(raw and other.raw)

    infix fun or(other: u8) = u8(raw or other.raw)

    infix fun xor(other: u8) = u8(raw xor other.raw)

    infix fun shl(bits: Int) = u8((raw shl bits) and 0xFF)

    infix fun shr(bits: Int) = u8(raw shr bits)

    fun inv() = u8(raw.inv() and 0xFF)

    override fun compareTo(other: u8) = raw.compareTo(other.raw)

    override fun toString() = raw.toString()

    fun toU16() = u16(raw)

    fun toInt() = raw

    companion object {
        val ZERO = u8(0)
        val MAX = u8(255)

        fun of(value: Int) = u8(value and 0xFF)
    }
}

@kotlin.jvm.JvmInline
value class u16(val raw: Int) : Comparable<u16> {
    init {
        require(raw in 0..65535) { "u16 must be 0-65535, got $raw" }
    }

    operator fun plus(other: u16) = u16((raw + other.raw) and 0xFFFF)

    operator fun plus(other: Int) = u16((raw + other) and 0xFFFF)

    operator fun minus(other: u16) = u16((raw - other.raw) and 0xFFFF)

    operator fun minus(other: Int) = u16((raw - other) and 0xFFFF)

    operator fun times(other: u16) = u16((raw * other.raw) and 0xFFFF)

    operator fun times(other: Int) = u16((raw * other) and 0xFFFF)

    operator fun div(other: u16) = u16(raw / other.raw)

    operator fun rem(other: u16) = u16(raw % other.raw)

    operator fun inc() = u16((raw + 1) and 0xFFFF)

    operator fun dec() = u16((raw - 1) and 0xFFFF)

    override fun compareTo(other: u16) = raw.compareTo(other.raw)

    override fun toString() = raw.toString()

    fun high() = u8(raw shr 8)

    fun low() = u8(raw and 0xFF)

    fun toU8() = u8(raw and 0xFF)

    fun toInt() = raw

    companion object {
        val ZERO = u16(0)
        val MAX = u16(65535)

        fun of(value: Int) = u16(value and 0xFFFF)

        fun from(high: u8, low: u8) = u16((high.raw shl 8) or low.raw)
    }
}

@kotlin.jvm.JvmInline
value class i8(val raw: Int) : Comparable<i8> {
    init {
        require(raw in -128..127) { "i8 must be -128..127, got $raw" }
    }

    operator fun plus(other: i8) = i8(((raw + other.raw) shl 24) shr 24)

    operator fun minus(other: i8) = i8(((raw - other.raw) shl 24) shr 24)

    operator fun times(other: i8) = i8(((raw * other.raw) shl 24) shr 24)

    operator fun div(other: i8) = i8(raw / other.raw)

    operator fun unaryMinus() = i8(-raw)

    override fun compareTo(other: i8) = raw.compareTo(other.raw)

    override fun toString() = raw.toString()

    fun toU8() = u8(raw and 0xFF)

    companion object {
        val ZERO = i8(0)
        val MIN = i8(-128)
        val MAX = i8(127)
    }
}

@kotlin.jvm.JvmInline
value class i16(val raw: Int) : Comparable<i16> {
    init {
        require(raw in -32768..32767) { "i16 must be -32768..32767, got $raw" }
    }

    operator fun plus(other: i16) = i16(((raw + other.raw) shl 16) shr 16)

    operator fun minus(other: i16) = i16(((raw - other.raw) shl 16) shr 16)

    operator fun times(other: i16) = i16(((raw * other.raw) shl 16) shr 16)

    operator fun div(other: i16) = i16(raw / other.raw)

    operator fun unaryMinus() = i16(-raw)

    override fun compareTo(other: i16) = raw.compareTo(other.raw)

    override fun toString() = raw.toString()

    fun toU16() = u16(raw and 0xFFFF)

    companion object {
        val ZERO = i16(0)
        val MIN = i16(-32768)
        val MAX = i16(32767)
    }
}

// =============================================================================
// GBC COLOR TYPES - Game Boy Color RGB555 support
// =============================================================================

/**
 * RGB555 color for Game Boy Color. Each color component uses 5 bits (0-31), stored as:
 * 0bbb_bbgg_gggr_rrrr Total: 15-bit color (32768 colors).
 */
@kotlin.jvm.JvmInline
value class GBCColor(val rgb555: Int) {
    init {
        require(rgb555 in 0..0x7FFF) { "RGB555 must be 0-32767, got $rgb555" }
    }

    val red: Int
        get() = rgb555 and 0x1F

    val green: Int
        get() = (rgb555 shr 5) and 0x1F

    val blue: Int
        get() = (rgb555 shr 10) and 0x1F

    /** Convert to hex string for C output */
    fun toHex(): String = "0x${rgb555.toString(16).padStart(4, '0').uppercase()}"

    override fun toString() = "GBCColor(r=$red, g=$green, b=$blue)"

    companion object {
        /** Create from RGB888 (standard 8-bit per channel) */
        fun fromRGB888(r: Int, g: Int, b: Int): GBCColor {
            require(r in 0..255 && g in 0..255 && b in 0..255) { "RGB888 components must be 0-255" }
            val r5 = (r shr 3) and 0x1F
            val g5 = (g shr 3) and 0x1F
            val b5 = (b shr 3) and 0x1F
            return GBCColor((b5 shl 10) or (g5 shl 5) or r5)
        }

        /** Create from hex color (0xRRGGBB) */
        fun fromHex(hex: Int): GBCColor =
            fromRGB888((hex shr 16) and 0xFF, (hex shr 8) and 0xFF, hex and 0xFF)

        // Common colors
        val WHITE = GBCColor(0x7FFF)
        val BLACK = GBCColor(0x0000)
        val RED = fromRGB888(255, 0, 0)
        val GREEN = fromRGB888(0, 255, 0)
        val BLUE = fromRGB888(0, 0, 255)
        val LIGHT_GRAY = fromRGB888(192, 192, 192)
        val DARK_GRAY = fromRGB888(96, 96, 96)
    }
}

/** Palette type - sprites and backgrounds have separate palette banks. */
enum class PaletteType {
    SPRITE,
    BACKGROUND,
}

/**
 * GBC mode configuration.
 * - DISABLED: Classic DMG grayscale only
 * - COMPATIBLE: Works on both DMG and GBC (uses -Wm-yc flag)
 * - ONLY: GBC exclusive, won't run on DMG (uses -Wm-yC flag)
 */
enum class GBCMode {
    DISABLED,
    COMPATIBLE,
    ONLY,
}

/** A 4-color GBC palette. GBC has 8 sprite palettes and 8 background palettes. */
data class GBCPalette(
    val name: String,
    val colors: List<GBCColor>,
    val slot: Int = -1, // -1 = auto-assign, 0-7 = explicit slot
    val type: PaletteType = PaletteType.SPRITE,
) {
    init {
        require(colors.size == 4) { "GBC palette must have exactly 4 colors, got ${colors.size}" }
        require(slot in -1..7) { "Palette slot must be -1 (auto) or 0-7, got $slot" }
    }

    /** Convert to GBDK-compatible RGB555 array format */
    fun toRGB555Array(): IntArray = colors.map { it.rgb555 }.toIntArray()

    /** Generate C array literal */
    fun toCArrayLiteral(): String = colors.joinToString(", ") { it.toHex() }
}

// =============================================================================
// RANGE EXTENSIONS - Kotlin-style bounds checking
// =============================================================================

fun u8.coerceIn(range: IntRange) = u8(raw.coerceIn(range))

fun u8.coerceIn(min: u8, max: u8) = u8(raw.coerceIn(min.raw, max.raw))

fun u8.coerceAtLeast(min: u8) = if (this < min) min else this

fun u8.coerceAtMost(max: u8) = if (this > max) max else this

fun u16.coerceIn(range: IntRange) = u16(raw.coerceIn(range))

fun u16.coerceIn(min: u16, max: u16) = u16(raw.coerceIn(min.raw, max.raw))

// =============================================================================
// DIMENSION HELPER - For sprite sizes, screen coords, etc.
// =============================================================================

data class Dimensions(val width: Int, val height: Int)

infix fun Int.x(other: Int) = Dimensions(this, other)
