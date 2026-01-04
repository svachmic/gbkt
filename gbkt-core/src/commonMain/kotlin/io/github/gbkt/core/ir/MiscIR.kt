/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.Easing
import io.github.gbkt.core.SourceLocation

// =============================================================================
// IR NODES FOR TWEENING, STATE MACHINES, AND INPUT BUFFERS
// =============================================================================

// --- Tween IR ---

/**
 * Tween IR node for smooth interpolation between values.
 *
 * @param target The variable name to animate
 * @param targetType The type of the target variable
 * @param from Starting value
 * @param to Ending value
 * @param duration Duration in frames
 * @param easing Easing function to use
 */
data class IRTween(
    val target: String,
    val targetType: GBVar.VarType,
    val from: IRExpression,
    val to: IRExpression,
    val duration: Int,
    val easing: Easing,
    override val sourceLocation: SourceLocation? = null
) : IRStatement

// --- State Machine IR ---

/** Update a state machine - process current state tick and check transitions. */
data class IRStateMachineUpdate(val machineName: String) : IRStatement

// --- Input Buffer IR ---

/** Declaration of an input buffer variable. Generates: `static UINT8 buffer_NAME;` */
data class IRInputBufferDecl(val bufferName: String, val buttonMask: Int, val windowFrames: Int) :
    IRStatement

/** Check if buffer is active (counter > 0), without consuming. Generates: `(buffer_NAME > 0)` */
data class IRInputBufferActive(val bufferName: String) : IRExpression

/**
 * Check if buffer is active AND consume it (set to 0). Generates: `(buffer_NAME > 0 && (buffer_NAME
 * = 0, 1))`
 */
data class IRInputBufferConsumed(val bufferName: String) : IRExpression

/** Reset the buffer (set counter to 0). Generates: `buffer_NAME = 0;` */
data class IRInputBufferReset(val bufferName: String) : IRStatement

/** Fill the buffer (set counter to max). Generates: `buffer_NAME = FRAMES;` */
data class IRInputBufferFill(val bufferName: String, val frames: Int) : IRStatement
