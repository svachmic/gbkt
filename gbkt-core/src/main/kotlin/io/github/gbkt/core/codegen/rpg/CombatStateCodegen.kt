/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen.rpg

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.codegen.core.generateStatement
import io.github.gbkt.core.ir.IRCombatStateChange
import io.github.gbkt.core.ir.IRCombatStateMachine
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRGetCombatState
import io.github.gbkt.core.ir.IRIsInCombatState
import io.github.gbkt.core.ir.IRStatement

// =============================================================================
// COMBAT STATE MACHINE CODE GENERATION
// =============================================================================

/**
 * Handle combat state machine IR statements.
 *
 * @return true if this was a combat state statement and was handled, false otherwise
 */
internal fun CodeGenerator.generateCombatStateStatement(stmt: IRStatement): Boolean =
    when (stmt) {
        is IRCombatStateMachine -> {
            generateStateMachineDefinition(stmt)
            true
        }
        is IRCombatStateChange -> {
            generateStateTransition(stmt)
            true
        }
        else -> false
    }

/**
 * Generate C expression for combat state queries.
 *
 * @return the C expression string, or null if not a combat state expression
 */
internal fun CodeGenerator.generateCombatStateExpr(expr: IRExpression): String? =
    when (expr) {
        is IRGetCombatState -> "${expr.machineName}_state"
        is IRIsInCombatState -> "(${expr.machineName}_state == ${expr.stateId}u)"
        else -> null
    }

/**
 * Generate C code for a complete state machine definition.
 *
 * This generates:
 * - State constants (enum-like defines)
 * - State variables (current state, previous state)
 * - Update function with switch-case
 * - Transition function
 */
private fun CodeGenerator.generateStateMachineDefinition(machine: IRCombatStateMachine) {
    val name = machine.name

    // Generate state constants
    line("// === Combat State Machine: $name ===")
    line()
    line("// State constants")
    for (state in machine.states) {
        line("#define ${name.uppercase()}_STATE_${state.name.uppercase()} ${state.id}u")
    }
    line()

    // Generate state variables
    line("// State tracking")
    line("static UINT8 ${name}_state = ${machine.initialStateId}u;")
    line("static UINT8 ${name}_prev_state = ${machine.initialStateId}u;")
    line("static UINT8 ${name}_state_changed = 0u;")
    line()

    // Generate exit callbacks for each state
    generateExitCallbacks(name, machine.states)

    // Generate enter callbacks for each state
    generateEnterCallbacks(name, machine.states)

    // Generate transition function
    generateTransitionFunction(name, machine.states)

    // Generate update function
    generateUpdateFunction(name, machine.states)
}

/** Generate exit callback functions for states that have them. */
private fun CodeGenerator.generateExitCallbacks(
    name: String,
    states: List<IRCombatStateMachine.StateDefinition>,
) {
    val statesWithExit = states.filter { it.onExit != null }
    if (statesWithExit.isEmpty()) return

    line("// Exit callbacks")
    for (state in statesWithExit) {
        line("static void ${name}_exit_${state.name}(void) {")
        indent++
        state.onExit?.forEach { stmt -> generateStatement(stmt) }
        indent--
        line("}")
        line()
    }
}

/** Generate enter callback functions for states that have them. */
private fun CodeGenerator.generateEnterCallbacks(
    name: String,
    states: List<IRCombatStateMachine.StateDefinition>,
) {
    val statesWithEnter = states.filter { it.onEnter != null }
    if (statesWithEnter.isEmpty()) return

    line("// Enter callbacks")
    for (state in statesWithEnter) {
        line("static void ${name}_enter_${state.name}(void) {")
        indent++
        state.onEnter?.forEach { stmt -> generateStatement(stmt) }
        indent--
        line("}")
        line()
    }
}

/** Generate the state transition function. */
private fun CodeGenerator.generateTransitionFunction(
    name: String,
    states: List<IRCombatStateMachine.StateDefinition>,
) {
    line("// Transition to a new state")
    line("static void ${name}_transition(UINT8 new_state) {")
    indent++

    line("if (${name}_state == new_state) return; // Already in this state")
    line()

    // Call exit callback for current state
    val statesWithExit = states.filter { it.onExit != null }
    if (statesWithExit.isNotEmpty()) {
        line("// Call exit callback for current state")
        line("switch (${name}_state) {")
        indent++
        for (state in statesWithExit) {
            line("case ${state.id}u: ${name}_exit_${state.name}(); break;")
        }
        indent--
        line("}")
        line()
    }

    // Update state
    line("${name}_prev_state = ${name}_state;")
    line("${name}_state = new_state;")
    line("${name}_state_changed = 1u;")
    line()

    // Call enter callback for new state
    val statesWithEnter = states.filter { it.onEnter != null }
    if (statesWithEnter.isNotEmpty()) {
        line("// Call enter callback for new state")
        line("switch (new_state) {")
        indent++
        for (state in statesWithEnter) {
            line("case ${state.id}u: ${name}_enter_${state.name}(); break;")
        }
        indent--
        line("}")
    }

    indent--
    line("}")
    line()
}

/** Generate the update function that executes state logic each frame. */
private fun CodeGenerator.generateUpdateFunction(
    name: String,
    states: List<IRCombatStateMachine.StateDefinition>,
) {
    line("// Update state machine (call once per frame)")
    line("static void ${name}_update(void) {")
    indent++

    line("${name}_state_changed = 0u;")
    line()

    line("switch (${name}_state) {")
    indent++

    for (state in states) {
        line("case ${state.id}u: // ${state.name}")
        indent++
        if (state.onUpdate.isNotEmpty()) {
            for (stmt in state.onUpdate) {
                generateStatement(stmt)
            }
        } else {
            line("// No update logic")
        }
        line("break;")
        indent--
    }

    line("default:")
    indent++
    line("// Unknown state, do nothing")
    line("break;")
    indent--

    indent--
    line("}")

    indent--
    line("}")
    line()
}

/** Generate C code for a state transition. */
private fun CodeGenerator.generateStateTransition(stmt: IRCombatStateChange) {
    lineWithSource(
        "// Transition to ${stmt.targetStateName}",
        stmt.sourceLocation,
        stmt.machineName,
    )
    line("${stmt.machineName}_transition(${stmt.targetStateId}u);")
}
