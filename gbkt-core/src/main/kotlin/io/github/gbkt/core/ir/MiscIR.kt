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
    override val sourceLocation: SourceLocation? = null,
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

// --- Exploration System IR ---

/**
 * Set torch fuel value.
 *
 * Used by sconces and other light sources to refill the player's torch. Generates: `torchFuel =
 * {value}u;`
 */
data class IRSetTorchFuel(val value: Int) : IRStatement

/**
 * Get current torch fuel value.
 *
 * Expression that reads the current torch fuel. Generates: `torchFuel`
 */
data object IRGetTorchFuel : IRExpression

// --- Map Object Interaction IR ---

/**
 * Try to interact with an object at a position.
 *
 * Checks if there's an object at the given coordinates on the specified floor, and if so, triggers
 * its interaction handler.
 *
 * Generates:
 * ```c
 * _temp_obj_idx = object_at_position(floor, x, y);
 * if (_temp_obj_idx != 255u) {
 *     object_interact(floor, _temp_obj_idx);
 * }
 * ```
 *
 * @param floorExpr Expression for the floor ID
 * @param xExpr Expression for the X coordinate
 * @param yExpr Expression for the Y coordinate
 */
data class IRTryObjectInteraction(
    val floorExpr: IRExpression,
    val xExpr: IRExpression,
    val yExpr: IRExpression,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

// --- Random Encounter IR ---

/**
 * Check for a random encounter after a step.
 *
 * This IR node represents calling the encounter system to check if a random battle should trigger
 * after a movement step.
 *
 * Generates:
 * ```c
 * UINT8 enc = encounter_check_step();
 * if (enc != 255u) {
 *     _pending_encounter_table = _encounter_table_id;
 *     _pending_encounter_entry = enc;
 *     _pending_encounter_valid = 1u;
 *     // ... transition to battle scene
 * }
 * ```
 *
 * @param battleSceneName Name of the battle scene to transition to
 */
data class IRCheckEncounterStep(
    val battleSceneName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Set the current encounter table ID.
 *
 * Used when entering a new floor/area to configure which encounter table is active.
 *
 * Generates: `encounter_reset({tableId});`
 *
 * @param tableId The encounter table index (assigned during codegen)
 */
data class IRSetEncounterTable(
    val tableId: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Set the current encounter table based on an expression.
 *
 * Used when the floor index comes from a variable rather than a literal.
 *
 * Generates: `encounter_reset({tableExpr});`
 *
 * @param tableExpr An expression that evaluates to the encounter table index
 */
data class IRSetEncounterTableExpr(
    val tableExpr: IRExpression,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/**
 * Check if an encounter was triggered (expression).
 *
 * Returns the entry index if an encounter was triggered, or 255 if not. This is an expression
 * version for use in conditionals.
 *
 * Generates: `encounter_check_step()`
 */
data object IREncounterCheckResult : IRExpression

/**
 * Check if a pending encounter is valid.
 *
 * Generates: `(_pending_encounter_valid != 0u)`
 */
data object IRHasPendingEncounter : IRExpression
