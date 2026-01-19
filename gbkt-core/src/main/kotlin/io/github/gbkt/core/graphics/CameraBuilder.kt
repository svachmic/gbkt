/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.graphics

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.ir.Dimensions
import io.github.gbkt.core.ir.ShakeDecay

// =============================================================================
// CAMERA SYSTEM DSL - Configuration and Builders
// =============================================================================

/** Camera configuration - defines default behavior. */
data class CameraConfig(
    val smoothing: Float = 0.1f, // 0 = instant, 1 = very slow
    val offsetX: Int = 0, // Offset from follow target
    val offsetY: Int = 0,
    val deadzoneWidth: Int = 0, // No movement within deadzone
    val deadzoneHeight: Int = 0,
    val boundsMinX: Int = 0,
    val boundsMaxX: Int = 255,
    val boundsMinY: Int = 0,
    val boundsMaxY: Int = 255,
)

// =============================================================================
// FOLLOW CONFIG BUILDER
// =============================================================================

/** Builder for follow configuration. */
@GbktDsl
class FollowConfigBuilder(var smoothing: Float = 0.1f, var offsetX: Int = 0, var offsetY: Int = 0) {
    /**
     * Set offset from target center.
     *
     * ```kotlin
     * offset(0, -16)  // Look 16px above target
     * ```
     */
    fun offset(x: Int, y: Int) {
        offsetX = x
        offsetY = y
    }
}

// =============================================================================
// SHAKE CONFIG BUILDER
// =============================================================================

/** Shake decay modes. */
object Decay {
    val NONE = ShakeDecay.NONE
    val LINEAR = ShakeDecay.LINEAR
    val EXPONENTIAL = ShakeDecay.EXPONENTIAL
}

/** Builder for shake configuration. */
@GbktDsl
class ShakeConfigBuilder {
    var intensity: Int = 4
    var duration: Int = 15
    var decay: ShakeDecay = ShakeDecay.LINEAR

    /** Set duration from FrameTiming. */
    var durationTiming: FrameTiming
        get() = FrameTiming(duration, false)
        set(value) {
            duration = value.count
        }
}

// =============================================================================
// CAMERA BUILDER
// =============================================================================

/** Builder for camera configuration. */
@GbktDsl
class CameraBuilder(private val name: String) {
    var smoothing: Float = 0.1f
    private var offsetX: Int = 0
    private var offsetY: Int = 0
    private var deadzoneWidth: Int = 0
    private var deadzoneHeight: Int = 0
    private var boundsMinX: Int = 0
    private var boundsMaxX: Int = 255
    private var boundsMinY: Int = 0
    private var boundsMaxY: Int = 255

    /**
     * Set offset from target center.
     *
     * ```kotlin
     * offset(0, -16)  // Camera looks 16px above target
     * ```
     */
    fun offset(x: Int, y: Int) {
        offsetX = x
        offsetY = y
    }

    /**
     * Set deadzone size. Camera won't move while target stays within deadzone.
     *
     * ```kotlin
     * deadzone(24 x 16)
     * ```
     */
    fun deadzone(dims: Dimensions) {
        deadzoneWidth = dims.width
        deadzoneHeight = dims.height
    }

    /**
     * Set world bounds. Camera position will be clamped to these bounds.
     *
     * ```kotlin
     * bounds(0..256, 0..256)
     * ```
     */
    fun bounds(xRange: IntRange, yRange: IntRange) {
        boundsMinX = xRange.first
        boundsMaxX = xRange.last
        boundsMinY = yRange.first
        boundsMaxY = yRange.last
    }

    internal fun build() =
        Camera(
            name = name,
            config =
                CameraConfig(
                    smoothing = smoothing,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    deadzoneWidth = deadzoneWidth,
                    deadzoneHeight = deadzoneHeight,
                    boundsMinX = boundsMinX,
                    boundsMaxX = boundsMaxX,
                    boundsMinY = boundsMinY,
                    boundsMaxY = boundsMaxY,
                ),
        )
}
