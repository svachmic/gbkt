/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.graphics.FrameTiming
import io.github.gbkt.core.graphics.Sprite
import io.github.gbkt.core.ir.CallbackTransition
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.FadeInTransition
import io.github.gbkt.core.ir.FadeOutTransition
import io.github.gbkt.core.ir.FlashTransition
import io.github.gbkt.core.ir.GBCColor
import io.github.gbkt.core.ir.IRComposedTransition
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRTransitionActive
import io.github.gbkt.core.ir.IRTransitionCancel
import io.github.gbkt.core.ir.IRVar
import io.github.gbkt.core.ir.IrisTransition
import io.github.gbkt.core.ir.IrisType
import io.github.gbkt.core.ir.SequenceTransition
import io.github.gbkt.core.ir.ShakeDecay
import io.github.gbkt.core.ir.ShakeTransition
import io.github.gbkt.core.ir.Transition
import io.github.gbkt.core.ir.WaitTransition
import io.github.gbkt.core.ir.WipeDirection
import io.github.gbkt.core.ir.WipeTransition
import io.github.gbkt.core.ir.then

// =============================================================================
// COMPOSABLE TRANSITION DSL
// Scene transitions with first-class composition support
// =============================================================================

/**
 * Scope for building scene transitions with composable primitives.
 *
 * ## Usage
 *
 * ```kotlin
 * transition {
 *     fadeOut(30.frames) then wait(10.frames) then fadeIn(20.frames)
 * }
 *
 * transition {
 *     shake(4, 10.frames) and fadeOut(30.frames)
 * }
 * ```
 */
class SceneTransitionScope {

    // =========================================================================
    // FADE TRANSITIONS
    // =========================================================================

    /**
     * Fade screen to black.
     *
     * ```kotlin
     * fadeOut(30.frames)
     * fadeOut(30)  // frames as int
     * ```
     */
    fun fadeOut(timing: FrameTiming) = FadeOutTransition(timing.count)

    fun fadeOut(frames: Int = 30) = FadeOutTransition(frames)

    /**
     * Fade screen in from black.
     *
     * ```kotlin
     * fadeIn(20.frames)
     * ```
     */
    fun fadeIn(timing: FrameTiming) = FadeInTransition(timing.count)

    fun fadeIn(frames: Int = 20) = FadeInTransition(frames)

    // Short aliases (property access for common defaults)
    /** Fade out with default 30 frames */
    val fadeOut: FadeOutTransition
        get() = FadeOutTransition(30)

    /** Fade in with default 20 frames */
    val fadeIn: FadeInTransition
        get() = FadeInTransition(20)

    // =========================================================================
    // WAIT
    // =========================================================================

    /**
     * Wait (hold current state) for specified duration.
     *
     * ```kotlin
     * fadeOut then wait(30.frames) then fadeIn
     * ```
     */
    fun wait(timing: FrameTiming) = WaitTransition(timing.count)

    fun wait(frames: Int) = WaitTransition(frames)

    // =========================================================================
    // FLASH
    // =========================================================================

    /**
     * Flash the screen white.
     *
     * ```kotlin
     * flash(8.frames)
     * ```
     */
    fun flash(timing: FrameTiming) = FlashTransition(GBCColor.WHITE, timing.count)

    fun flash(frames: Int = 8) = FlashTransition(GBCColor.WHITE, frames)

    /**
     * Flash the screen a specific color.
     *
     * ```kotlin
     * flash(GBCColor.RED, 8.frames)
     * ```
     */
    fun flash(color: GBCColor, timing: FrameTiming) = FlashTransition(color, timing.count)

    fun flash(color: GBCColor, frames: Int = 8) = FlashTransition(color, frames)

    /** Flash white with default 8 frames */
    val flash: FlashTransition
        get() = FlashTransition(GBCColor.WHITE, 8)

    // =========================================================================
    // SHAKE
    // =========================================================================

    /**
     * Screen shake effect.
     *
     * ```kotlin
     * shake(4, 10.frames)
     * shake(intensity = 4, duration = 15.frames)
     * ```
     *
     * @param intensity Shake magnitude in pixels (1-8 recommended)
     * @param duration Duration as FrameTiming
     */
    fun shake(intensity: Int, duration: FrameTiming) =
        ShakeTransition(intensity, duration.count, ShakeDecay.LINEAR)

    fun shake(intensity: Int, frames: Int = 15) =
        ShakeTransition(intensity, frames, ShakeDecay.LINEAR)

    /**
     * Screen shake with decay configuration.
     *
     * ```kotlin
     * shake {
     *     intensity = 6
     *     duration = 20.frames
     *     decay = Decay.EXPONENTIAL
     * }
     * ```
     */
    fun shake(init: ShakeBuilder.() -> Unit): ShakeTransition {
        val builder = ShakeBuilder()
        builder.init()
        return ShakeTransition(builder.intensity, builder.durationFrames, builder.decay)
    }

    /**
     * Quick impact shake - a short, punchy shake for hits.
     *
     * ```kotlin
     * impact(4)  // Quick 4px shake with exponential decay
     * ```
     */
    fun impact(intensity: Int) = ShakeTransition(intensity, 8, ShakeDecay.EXPONENTIAL)

    // =========================================================================
    // WIPE TRANSITIONS
    // =========================================================================

    /**
     * Wipe transition from right to left.
     *
     * ```kotlin
     * wipeLeft(45.frames)
     * ```
     */
    fun wipeLeft(timing: FrameTiming) = WipeTransition(WipeDirection.LEFT, timing.count)

    fun wipeLeft(frames: Int = 45) = WipeTransition(WipeDirection.LEFT, frames)

    /** Wipe from left to right */
    fun wipeRight(timing: FrameTiming) = WipeTransition(WipeDirection.RIGHT, timing.count)

    fun wipeRight(frames: Int = 45) = WipeTransition(WipeDirection.RIGHT, frames)

    /** Wipe from bottom to top */
    fun wipeUp(timing: FrameTiming) = WipeTransition(WipeDirection.UP, timing.count)

    fun wipeUp(frames: Int = 45) = WipeTransition(WipeDirection.UP, frames)

    /** Wipe from top to bottom */
    fun wipeDown(timing: FrameTiming) = WipeTransition(WipeDirection.DOWN, timing.count)

    fun wipeDown(frames: Int = 45) = WipeTransition(WipeDirection.DOWN, frames)

    // Short aliases
    val wipeLeft: WipeTransition
        get() = WipeTransition(WipeDirection.LEFT, 45)

    val wipeRight: WipeTransition
        get() = WipeTransition(WipeDirection.RIGHT, 45)

    val wipeUp: WipeTransition
        get() = WipeTransition(WipeDirection.UP, 45)

    val wipeDown: WipeTransition
        get() = WipeTransition(WipeDirection.DOWN, 45)

    // =========================================================================
    // IRIS TRANSITIONS
    // =========================================================================

    /**
     * Iris close - circle shrinking to a point (screen center by default).
     *
     * ```kotlin
     * irisClose(60.frames)
     * irisClose(60.frames, player)  // Centered on sprite
     * irisClose(60.frames, 80, 72)  // Centered on coordinates
     * ```
     */
    fun irisClose(timing: FrameTiming) = IrisTransition(IrisType.CLOSE, null, null, timing.count)

    fun irisClose(frames: Int = 60) = IrisTransition(IrisType.CLOSE, null, null, frames)

    fun irisClose(timing: FrameTiming, centerX: Int, centerY: Int) =
        IrisTransition(IrisType.CLOSE, IRLiteral(centerX), IRLiteral(centerY), timing.count)

    fun irisClose(frames: Int, centerX: Int, centerY: Int) =
        IrisTransition(IrisType.CLOSE, IRLiteral(centerX), IRLiteral(centerY), frames)

    fun irisClose(timing: FrameTiming, target: Sprite): IrisTransition {
        val (xVar, yVar) = getSpritePositionVars(target)
        return IrisTransition(IrisType.CLOSE, IRVar(xVar), IRVar(yVar), timing.count)
    }

    fun irisClose(frames: Int, target: Sprite): IrisTransition {
        val (xVar, yVar) = getSpritePositionVars(target)
        return IrisTransition(IrisType.CLOSE, IRVar(xVar), IRVar(yVar), frames)
    }

    /**
     * Iris open - circle expanding from a point.
     *
     * ```kotlin
     * irisOpen(60.frames)
     * irisOpen(60.frames, player)
     * ```
     */
    fun irisOpen(timing: FrameTiming) = IrisTransition(IrisType.OPEN, null, null, timing.count)

    fun irisOpen(frames: Int = 60) = IrisTransition(IrisType.OPEN, null, null, frames)

    fun irisOpen(timing: FrameTiming, centerX: Int, centerY: Int) =
        IrisTransition(IrisType.OPEN, IRLiteral(centerX), IRLiteral(centerY), timing.count)

    fun irisOpen(frames: Int, centerX: Int, centerY: Int) =
        IrisTransition(IrisType.OPEN, IRLiteral(centerX), IRLiteral(centerY), frames)

    fun irisOpen(timing: FrameTiming, target: Sprite): IrisTransition {
        val (xVar, yVar) = getSpritePositionVars(target)
        return IrisTransition(IrisType.OPEN, IRVar(xVar), IRVar(yVar), timing.count)
    }

    fun irisOpen(frames: Int, target: Sprite): IrisTransition {
        val (xVar, yVar) = getSpritePositionVars(target)
        return IrisTransition(IrisType.OPEN, IRVar(xVar), IRVar(yVar), frames)
    }

    // Short aliases
    val irisClose: IrisTransition
        get() = IrisTransition(IrisType.CLOSE, null, null, 60)

    val irisOpen: IrisTransition
        get() = IrisTransition(IrisType.OPEN, null, null, 60)

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    private fun getSpritePositionVars(sprite: Sprite): Pair<String, String> {
        val pos = sprite.position
        val bind = sprite.binding
        return when {
            pos != null -> pos.xVarName to pos.yVarName
            bind != null -> bind.xVar to bind.yVar
            else -> error("Sprite '${sprite.name}' has no position. Use position() or boundTo().")
        }
    }
}

/** Builder for shake configuration. */
class ShakeBuilder {
    var intensity: Int = 4
    var durationFrames: Int = 15
    var decay: ShakeDecay = ShakeDecay.LINEAR

    /** Set duration from FrameTiming */
    var duration: FrameTiming
        get() = FrameTiming(durationFrames, false)
        set(value) {
            durationFrames = value.count
        }
}

// =============================================================================
// TRANSITION DEFINITION - Reusable named transitions
// =============================================================================

/**
 * A named, reusable transition definition.
 *
 * ## Usage
 *
 * ```kotlin
 * val cinematicOut = transition("cinematic") {
 *     shake(2) and fadeOut(30.frames) then wait(10.frames)
 * }
 *
 * // Later, in a scene:
 * cinematicOut to "victory"
 * ```
 */
class TransitionDefinition(val name: String, val transition: Transition) {
    /**
     * Apply this transition and then go to a scene.
     *
     * ```kotlin
     * cinematicOut to "victory"
     * ```
     */
    infix fun to(sceneName: String) {
        RecordingContext.require().emit(IRComposedTransition(transition, sceneName))
    }

    /**
     * Start this transition without changing scene.
     *
     * ```kotlin
     * dramaticShake.play()
     * ```
     */
    fun play() {
        RecordingContext.require().emit(IRComposedTransition(transition, null))
    }
}

// =============================================================================
// CALLBACK INJECTION - Insert code at any point in a transition
// =============================================================================

/**
 * Inject a callback at a point in the transition sequence.
 *
 * ```kotlin
 * fadeOut then { playSound(BOOM) } then fadeIn
 * ```
 */
infix fun Transition.then(block: FrameScope.() -> Unit): SequenceTransition {
    val recorder = StatementRecorder()
    RecordingContext.record(recorder) { FrameScope("transition_callback").block() }
    return this then CallbackTransition(recorder.statements)
}

// =============================================================================
// TRANSITION STATE OBJECT - For checking active state
// =============================================================================

/**
 * Global transition state accessor.
 *
 * ```kotlin
 * whenever(transition.isActive) {
 *     // Skip input during transitions
 * }
 * ```
 */
object transition {
    /** Check if any transition is currently active. */
    val isActive: Condition
        get() = Condition(IRTransitionActive)

    /** Cancel the currently running transition. Useful for "skip cutscene" functionality. */
    fun cancel() {
        RecordingContext.require().emit(IRTransitionCancel)
    }
}
