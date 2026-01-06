/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.graphics.FrameTiming
import io.github.gbkt.core.ir.AssignableExpr
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRTween

/**
 * Easing functions for smooth tweening animations.
 *
 * All easing functions use pre-computed lookup tables for performance. The lookup tables contain
 * 256 values (0-255) representing the normalized progress.
 */
enum class Easing {
    /** Linear interpolation (no easing) */
    LINEAR,

    /** Ease in - slow start, fast end */
    EASE_IN,

    /** Ease out - fast start, slow end */
    EASE_OUT,

    /** Ease in-out - slow start and end, fast middle */
    EASE_IN_OUT,

    /** Ease out-in - fast start and end, slow middle */
    EASE_OUT_IN,

    /** Quadratic ease in */
    EASE_IN_QUAD,

    /** Quadratic ease out */
    EASE_OUT_QUAD,

    /** Quadratic ease in-out */
    EASE_IN_OUT_QUAD,

    /** Cubic ease in */
    EASE_IN_CUBIC,

    /** Cubic ease out */
    EASE_OUT_CUBIC,

    /** Cubic ease in-out */
    EASE_IN_OUT_CUBIC,

    /** Bounce ease out */
    EASE_OUT_BOUNCE,

    /** Elastic ease out */
    EASE_OUT_ELASTIC,
}

/**
 * IR node for tweening a value over time.
 *
 * @param target The variable name to tween (e.g., "sprite0_x")
 * @param targetType The type of the target variable (U8 or U16)
 * @param from Starting value
 * @param to Ending value
 * @param duration Duration in frames
 * @param easing Easing function to use
 */
// IRTween moved to io.github.gbkt.core.ir.MiscIR

/**
 * Tween a value from one value to another over a duration with easing.
 *
 * ## Usage
 *
 * ```kotlin
 * // Tween sprite X position
 * tween(player.x, from = 0, to = 100, duration = 60.frames, easing = Easing.EASE_OUT)
 *
 * // Tween a variable
 * var score by u8Var()
 * tween(score, from = 0, to = 255, duration = 120.frames, easing = Easing.EASE_IN_OUT)
 * ```
 *
 * @param target The assignable expression to tween (e.g., sprite.x or a variable)
 * @param from Starting value
 * @param to Ending value
 * @param duration Duration as FrameTiming (e.g., 60.frames)
 * @param easing Easing function (default: EASE_OUT)
 */
fun tween(
    target: AssignableExpr,
    from: Int,
    to: Int,
    duration: FrameTiming,
    easing: Easing = Easing.EASE_OUT,
) {
    if (RecordingContext.isRecording) {
        // Extract variable type from AssignableExpr - we need to add a property for this
        // For now, we'll need to infer it or add it to AssignableExpr
        RecordingContext.require()
            .emit(
                IRTween(
                    target = target.varName,
                    targetType = target.varType,
                    from = IRLiteral(from),
                    to = IRLiteral(to),
                    duration = duration.count,
                    easing = easing,
                )
            )
    }
}

/**
 * Tween a value from one expression to another over a duration with easing.
 *
 * @param target The assignable expression to tween
 * @param from Starting value expression
 * @param to Ending value expression
 * @param duration Duration as FrameTiming
 * @param easing Easing function (default: EASE_OUT)
 */
fun tween(
    target: AssignableExpr,
    from: Expr,
    to: Expr,
    duration: FrameTiming,
    easing: Easing = Easing.EASE_OUT,
) {
    if (RecordingContext.isRecording) {
        RecordingContext.require()
            .emit(
                IRTween(
                    target = target.varName,
                    targetType = target.varType,
                    from = from.ir,
                    to = to.ir,
                    duration = duration.count,
                    easing = easing,
                )
            )
    }
}
