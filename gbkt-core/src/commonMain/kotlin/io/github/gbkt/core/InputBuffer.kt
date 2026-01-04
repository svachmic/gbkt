/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.IRInputBufferActive
import io.github.gbkt.core.ir.IRInputBufferConsumed
import io.github.gbkt.core.ir.IRInputBufferFill
import io.github.gbkt.core.ir.IRInputBufferReset

// =============================================================================
// INPUT BUFFER - Frame-perfect input timing for platformers and action games
// =============================================================================

/**
 * Input buffer for frame-perfect input timing.
 *
 * When the button is pressed, a countdown starts from the specified frame window. Each frame, the
 * countdown decrements. When [consumed] is checked and the counter is greater than 0, it returns
 * true and resets the counter (consuming the input).
 *
 * This allows for more forgiving input timing, especially for:
 * - Jump buffering: Press jump slightly before landing
 * - Attack buffering: Queue attacks during recovery frames
 * - Coyote time: Jump slightly after leaving a platform
 *
 * Usage:
 * ```kotlin
 * val jumpBuffer = inputBuffer(buttons.a, 6.frames)
 *
 * scene("gameplay") {
 *     every.frame {
 *         whenever(jumpBuffer.consumed && grounded) {
 *             jump()
 *         }
 *     }
 * }
 * ```
 */
class InputBuffer
internal constructor(val name: String, val button: ButtonState, val windowFrames: Int) {
    /**
     * Check if the buffer is active (button was pressed within the window). This does NOT consume
     * the buffer - use [consumed] for that.
     */
    val active: Condition
        get() = Condition(IRInputBufferActive(name))

    /**
     * Check if the buffer is active AND consume it (set counter to 0). Returns true if button was
     * pressed within the window, then resets.
     *
     * The generated C code uses the comma operator for atomic check-and-reset: `(buffer_NAME > 0 &&
     * (buffer_NAME = 0, 1))`
     */
    val consumed: Condition
        get() = Condition(IRInputBufferConsumed(name))

    /**
     * Manually reset the buffer (set counter to 0). Useful for canceling buffered inputs in certain
     * game states.
     */
    fun reset() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRInputBufferReset(name))
        }
    }

    /**
     * Manually fill the buffer (set counter to max). Useful for implementing coyote time or grace
     * periods.
     */
    fun fill() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRInputBufferFill(name, windowFrames))
        }
    }
}

/** Type-safe reference to an input buffer. Used for registering buffers with the game. */
class InputBufferRef(val buffer: InputBuffer) {
    val name: String
        get() = buffer.name

    val button: ButtonState
        get() = buffer.button

    val windowFrames: Int
        get() = buffer.windowFrames
}

// =============================================================================
// IR NODES - Input Buffer
// =============================================================================
// Moved to io.github.gbkt.core.ir.MiscIR

// =============================================================================
// INPUT BUFFER DATA - For code generation
// =============================================================================

/** Compiled input buffer data for code generation. */
data class InputBufferData(val name: String, val buttonMask: Int, val windowFrames: Int)
