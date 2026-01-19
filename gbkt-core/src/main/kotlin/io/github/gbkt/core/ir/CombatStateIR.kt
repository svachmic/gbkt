/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.SourceLocation

// =============================================================================
// COMBAT STATE MACHINE IR NODES
// =============================================================================

/**
 * IR node representing a complete combat state machine definition.
 *
 * This is emitted when a state machine is registered and generates:
 * - State variable declaration
 * - Previous state tracking (for exit callbacks)
 * - Update function with switch-case for each state
 * - State transition function
 */
data class IRCombatStateMachine(
    val name: String,
    val states: List<StateDefinition>,
    val initialStateId: Int,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement {

    /** Definition of a single state within the machine. */
    data class StateDefinition(
        val name: String,
        val id: Int,
        val onEnter: List<IRStatement>?,
        val onUpdate: List<IRStatement>,
        val onExit: List<IRStatement>?,
    )
}

/**
 * IR node for transitioning between combat states.
 *
 * Generates code that:
 * 1. Calls exit callback of current state (if any)
 * 2. Updates state variable
 * 3. Calls enter callback of new state (if any)
 */
data class IRCombatStateChange(
    val machineName: String,
    val targetStateId: Int,
    val targetStateName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

/** IR expression for getting the current state ID of a state machine. */
data class IRGetCombatState(val machineName: String) : IRExpression

/** IR expression for checking if state machine is in a specific state. */
data class IRIsInCombatState(val machineName: String, val stateId: Int) : IRExpression
