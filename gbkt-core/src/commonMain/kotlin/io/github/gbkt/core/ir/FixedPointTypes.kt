/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("NOTHING_TO_INLINE")

package io.github.gbkt.core.ir

// =============================================================================
// FIXED-POINT TYPES - Type-safe wrappers for raw fixed-point values
// =============================================================================

/**
 * Animation speed multiplier (percentage-based fixed-point).
 * - 100 = 1.0x (normal speed)
 * - 50 = 0.5x (half speed)
 * - 200 = 2.0x (double speed)
 *
 * Usage:
 * ```kotlin
 * player.play(runAnimation, speed = Speed.NORMAL)
 * player.play(slowMoAnimation, speed = Speed(50))  // Half speed
 * player.play(fastAnimation, speed = 2.0.speed)     // Double speed
 * ```
 */
@kotlin.jvm.JvmInline
value class Speed(val raw: Int) {
    companion object {
        /** Normal speed (1.0x) */
        val NORMAL = Speed(100)

        /** Half speed (0.5x) */
        val HALF = Speed(50)

        /** Double speed (2.0x) */
        val DOUBLE = Speed(200)
    }

    /** Create speed from a float multiplier (1.0 = normal) */
    constructor(multiplier: Float) : this((multiplier * 100).toInt().coerceIn(1, 255))

    override fun toString(): String = "Speed(${raw / 100f}x)"
}

/** Convert a float to Speed (1.0 = normal speed) */
val Float.speed: Speed
    get() = Speed(this)

/** Convert a double to Speed (1.0 = normal speed) */
val Double.speed: Speed
    get() = Speed(this.toFloat())

/**
 * Fixed-point 8.8 value for physics calculations.
 *
 * The value is stored as a 16-bit integer where:
 * - Upper 8 bits = integer part (-128 to 127)
 * - Lower 8 bits = fractional part (0.0 to 0.996)
 * - 256 = 1.0
 * - 128 = 0.5
 * - 64 = 0.25
 *
 * Usage:
 * ```kotlin
 * physics {
 *     gravity = Fixed8_8(0.5f)    // Half a pixel per frame squared
 *     friction = Fixed8_8(0.9f)   // 90% velocity retention
 * }
 * ```
 */
@kotlin.jvm.JvmInline
value class Fixed8_8(val raw: Int) {
    companion object {
        /** Zero (0.0) */
        val ZERO = Fixed8_8(0)

        /** One (1.0) */
        val ONE = Fixed8_8(256)

        /** Half (0.5) */
        val HALF = Fixed8_8(128)
    }

    /** Create from a float value */
    constructor(value: Float) : this((value * 256).toInt())

    /** Convert to float for display */
    fun toFloat(): Float = raw / 256f

    override fun toString(): String = "Fixed8_8(${toFloat()})"
}

/** Convert a float to Fixed8_8 */
val Float.fixed: Fixed8_8
    get() = Fixed8_8(this)

/** Convert a double to Fixed8_8 */
val Double.fixed: Fixed8_8
    get() = Fixed8_8(this.toFloat())
