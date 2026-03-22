/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

/**
 * Game Boy joypad buttons available for scripted input sequences.
 *
 * Maps to Coffee-GB's `eu.rekawek.coffeegb.core.joypad.Button` enum.
 */
enum class Button {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    A,
    B,
    START,
    SELECT,
}

/**
 * A single step in a scripted input sequence.
 *
 * - [Press] — press and release a button over the given number of frames
 * - [Hold] — press a button without releasing (until explicit [Release])
 * - [Release] — release a previously held button
 * - [Wait] — advance the given number of frames with no input change
 */
sealed interface InputStep {
    /** Press and release [button] over [frames] frames. */
    data class Press(val button: Button, val frames: Int = 1) : InputStep

    /** Press [button] and hold it until a corresponding [Release] step. */
    data class Hold(val button: Button) : InputStep

    /** Release a previously held [button]. */
    data class Release(val button: Button) : InputStep

    /** Advance [frames] frames with no input change. */
    data class Wait(val frames: Int) : InputStep
}

/**
 * An immutable sequence of [InputStep] entries that describe a deterministic game input scenario.
 *
 * Build instances using the [inputScript] DSL builder.
 */
class InputScript(val steps: List<InputStep>)

/**
 * Returns the total number of frames that this script will advance when played.
 *
 * Only [InputStep.Press] and [InputStep.Wait] steps advance frames. [InputStep.Hold] and
 * [InputStep.Release] are instantaneous.
 */
fun InputScript.totalFrames(): Int = steps.sumOf { step ->
    when (step) {
        is InputStep.Press -> step.frames
        is InputStep.Wait -> step.frames
        is InputStep.Hold -> 0
        is InputStep.Release -> 0
    }
}

/**
 * DSL builder for constructing [InputScript] instances.
 *
 * Use via the [inputScript] top-level function:
 * ```kotlin
 * val script = inputScript {
 *     press(Button.RIGHT, frames = 30)
 *     wait(5)
 *     press(Button.A)
 * }
 * ```
 */
class InputScriptBuilder {
    private val _steps = mutableListOf<InputStep>()

    /**
     * Press [button] and release it after [frames] frames.
     *
     * @param button The button to press.
     * @param frames Number of frames to hold the button before releasing. Default is 1.
     */
    fun press(button: Button, frames: Int = 1) {
        _steps += InputStep.Press(button, frames)
    }

    /**
     * Press [button] and hold it until an explicit [release] call.
     *
     * @param button The button to hold.
     */
    fun hold(button: Button) {
        _steps += InputStep.Hold(button)
    }

    /**
     * Release a previously held [button].
     *
     * @param button The button to release.
     */
    fun release(button: Button) {
        _steps += InputStep.Release(button)
    }

    /**
     * Advance emulation by [frames] frames without changing any button state.
     *
     * @param frames Number of frames to wait.
     */
    fun wait(frames: Int) {
        _steps += InputStep.Wait(frames)
    }

    /** Builds and returns the [InputScript] with all accumulated steps. */
    fun build(): InputScript = InputScript(_steps.toList())
}

/**
 * Creates an [InputScript] using the provided builder block.
 *
 * @param block DSL block to configure the input sequence.
 * @return The constructed [InputScript].
 */
fun inputScript(block: InputScriptBuilder.() -> Unit): InputScript =
    InputScriptBuilder().apply(block).build()
