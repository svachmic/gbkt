/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// COMPOSABLE TRANSITIONS - First-class transition values
// =============================================================================

/**
 * A composable transition effect. Transitions are first-class values that can be combined with
 * 'then' (sequence) and 'and' (parallel).
 *
 * ## Usage
 *
 * ```kotlin
 * val fadeSequence = fadeOut(30) then wait(10) then fadeIn(20)
 * val dramaticExit = shake(4) and fadeOut(30)
 * ```
 */
sealed class Transition {
    /** Estimated frame count for this transition (used for planning) */
    abstract val estimatedFrames: Int
}

/** Fade screen to black */
data class FadeOutTransition(val frames: Int = 30) : Transition() {
    override val estimatedFrames: Int = frames
}

/** Fade screen in from black */
data class FadeInTransition(val frames: Int = 20) : Transition() {
    override val estimatedFrames: Int = frames
}

/** Wait (hold current state) */
data class WaitTransition(val frames: Int) : Transition() {
    override val estimatedFrames: Int = frames
}

/** Flash the screen a color */
data class FlashTransition(val color: GBCColor = GBCColor.WHITE, val frames: Int = 8) :
    Transition() {
    override val estimatedFrames: Int = frames
}

/** Screen shake effect */
data class ShakeTransition(
    val intensity: Int,
    val frames: Int = 15,
    val decay: ShakeDecay = ShakeDecay.LINEAR
) : Transition() {
    override val estimatedFrames: Int = frames
}

/** Wipe transition (black bar sweeping across screen) */
data class WipeTransition(val direction: WipeDirection, val frames: Int = 45) : Transition() {
    override val estimatedFrames: Int = frames
}

/** Iris transition (circle closing/opening) */
data class IrisTransition(
    val type: IrisType,
    val centerX: IRExpression? = null,
    val centerY: IRExpression? = null,
    val frames: Int = 60
) : Transition() {
    override val estimatedFrames: Int = frames
}

/** Execute statements at a point in the transition */
data class CallbackTransition(val statements: List<IRStatement>) : Transition() {
    override val estimatedFrames: Int = 0 // Instant
}

/** Sequence of transitions (A then B then C) */
data class SequenceTransition(val steps: List<Transition>) : Transition() {
    override val estimatedFrames: Int = steps.sumOf { it.estimatedFrames }
}

/** Parallel transitions (A and B running simultaneously) */
data class ParallelTransition(val effects: List<Transition>) : Transition() {
    override val estimatedFrames: Int = effects.maxOfOrNull { it.estimatedFrames } ?: 0
}

// -----------------------------------------------------------------------------
// Composition Operators
// -----------------------------------------------------------------------------

/**
 * Sequence operator - run transitions one after another.
 *
 * ```kotlin
 * fadeOut then wait(10) then fadeIn
 * ```
 */
infix fun Transition.then(other: Transition): SequenceTransition =
    when {
        this is SequenceTransition && other is SequenceTransition ->
            SequenceTransition(this.steps + other.steps)
        this is SequenceTransition -> SequenceTransition(this.steps + other)
        other is SequenceTransition -> SequenceTransition(listOf(this) + other.steps)
        else -> SequenceTransition(listOf(this, other))
    }

/**
 * Parallel operator - run transitions simultaneously.
 *
 * ```kotlin
 * shake(4) and fadeOut(30)
 * ```
 */
infix fun Transition.and(other: Transition): ParallelTransition =
    when {
        this is ParallelTransition && other is ParallelTransition ->
            ParallelTransition(this.effects + other.effects)
        this is ParallelTransition -> ParallelTransition(this.effects + other)
        other is ParallelTransition -> ParallelTransition(listOf(this) + other.effects)
        else -> ParallelTransition(listOf(this, other))
    }
