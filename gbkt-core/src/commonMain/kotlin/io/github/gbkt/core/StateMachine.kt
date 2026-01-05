/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.graphics.AnimatedSprite
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.IRBinary
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRStateMachineGoto
import io.github.gbkt.core.ir.IRStateMachineStart
import io.github.gbkt.core.ir.IRStateMachineUpdate
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRVar

// =============================================================================
// STATE MACHINE DSL
// First-class state management for game entities
// =============================================================================

/**
 * A state machine for managing entity behavior.
 *
 * Usage: val playerState = states("player") { "idle" { enter { player.play("idle") }
 * on(buttons.a.pressed) { goto("jump") } } "run" { enter { player.play("run") } tick { playerX +=
 * dpad.x * 2 } on(dpad.none) { goto("idle") } } }
 *
 * scene("gameplay") { enter { playerState.start("idle") } every.frame { playerState.update() } }
 */
class StateMachine(val name: String, val states: Map<String, State>, val defaultState: String?) {
    /**
     * Start the state machine in the specified state (type-safe). Call this in scene enter blocks.
     *
     * Usage:
     * ```kotlin
     * val idle = state("idle") { ... }
     * start(idle)  // Type-safe!
     * ```
     */
    fun start(ref: StateRef) {
        require(ref.machineName == name) {
            "StateRef '${ref.stateName}' belongs to machine '${ref.machineName}', not '$name'"
        }
        require(ref.stateName in states) {
            "State '${ref.stateName}' not found. Available: ${states.keys}"
        }
        RecordingContext.require().emit(IRStateMachineStart(name, ref.stateName))
    }

    /**
     * Start the state machine in the specified state (string-based, for forward references). Prefer
     * using start(StateRef) for type safety.
     */
    fun start(stateName: String) {
        require(stateName in states) { "State '$stateName' not found. Available: ${states.keys}" }
        RecordingContext.require().emit(IRStateMachineStart(name, stateName))
    }

    /**
     * Update the state machine - processes current state logic and transitions. Call this in
     * every.frame blocks.
     */
    fun update() {
        RecordingContext.require().emit(IRStateMachineUpdate(name))
    }

    /**
     * Force transition to a specific state (type-safe). Use this for external triggers (e.g.,
     * damage taken).
     */
    fun goto(ref: StateRef) {
        require(ref.machineName == name) {
            "StateRef '${ref.stateName}' belongs to machine '${ref.machineName}', not '$name'"
        }
        require(ref.stateName in states) {
            "State '${ref.stateName}' not found. Available: ${states.keys}"
        }
        RecordingContext.require().emit(IRStateMachineGoto(name, ref.stateName))
    }

    /**
     * Force transition to a specific state (string-based, for forward references). Prefer using
     * goto(StateRef) for type safety.
     */
    fun goto(stateName: String) {
        require(stateName in states) { "State '$stateName' not found. Available: ${states.keys}" }
        RecordingContext.require().emit(IRStateMachineGoto(name, stateName))
    }

    /** Check if currently in a specific state (type-safe). */
    fun isIn(ref: StateRef): Condition {
        require(ref.machineName == name) {
            "StateRef '${ref.stateName}' belongs to machine '${ref.machineName}', not '$name'"
        }
        return Condition(
            IRBinary(
                IRVar("_${name}_state"),
                BinaryOp.EQ,
                IRVar("STATE_${name.uppercase()}_${ref.stateName.uppercase()}"),
            )
        )
    }

    /**
     * Check if currently in a specific state (string-based). Prefer using isIn(StateRef) for type
     * safety.
     */
    fun isIn(stateName: String): Condition {
        return Condition(
            IRBinary(
                IRVar("_${name}_state"),
                BinaryOp.EQ,
                IRVar("STATE_${name.uppercase()}_${stateName.uppercase()}"),
            )
        )
    }
}

/** A single state within a state machine. */
class State(
    val name: String,
    val onEnter: List<IRStatement>,
    val onTick: List<IRStatement>,
    val onExit: List<IRStatement>,
    val transitions: List<StateTransition>,
    val animation: StateAnimation? = null, // Associated animation
)

/** A state transition - condition and target state. */
data class StateTransition(
    val condition: IRExpression,
    val targetState: String,
    val actions: List<IRStatement>,
)

/** Animation configuration for a state. */
data class StateAnimation(
    val spriteName: String,
    val animationName: String,
    val lockUntilComplete: Boolean = false, // Block transitions until animation finishes
)

// =============================================================================
// STATE MACHINE DSL BUILDER
// =============================================================================

/**
 * Create a state machine.
 *
 * Usage: val playerState = states("player") { "idle" { enter { player.play("idle") }
 * on(buttons.a.pressed) { goto("jump") } } }
 */
fun GameBuilder.states(name: String, init: StateMachineBuilder.() -> Unit): StateMachine {
    val builder = StateMachineBuilder(name)
    builder.init()
    val machine = builder.build()
    registerStateMachine(machine)
    return machine
}

/** Builder for state machines. */
class StateMachineBuilder(private val machineName: String) {
    private val states = mutableMapOf<String, State>()
    private var defaultState: String? = null

    /**
     * Define a state and return a type-safe reference.
     *
     * Usage:
     * ```kotlin
     * val idle = state("idle") {
     *     enter { player.play("idle") }
     *     tick { /* per-frame logic */ }
     *     on(condition) { goto(running) }  // Type-safe!
     * }
     * val running = state("running") { ... }
     * ```
     */
    fun state(name: String, init: StateBuilder.() -> Unit): StateRef {
        val builder = StateBuilder(name, machineName)
        builder.init()
        states[name] = builder.build()

        // First state becomes default
        if (defaultState == null) {
            defaultState = name
        }

        return StateRef(machineName, name)
    }

    /**
     * Define a state using string syntax (legacy). Prefer using state("name") { } for type-safe
     * references.
     *
     * Usage: "idle" { enter { player.play("idle") } tick { /* per-frame logic */ } exit { /*
     * cleanup */ } on(condition) { goto("other") } }
     */
    operator fun String.invoke(init: StateBuilder.() -> Unit) {
        val builder = StateBuilder(this, machineName)
        builder.init()
        states[this] = builder.build()

        // First state becomes default
        if (defaultState == null) {
            defaultState = this
        }
    }

    fun build() = StateMachine(machineName, states.toMap(), defaultState)
}

/** Builder for individual states. */
class StateBuilder(private val stateName: String, private val machineName: String) {
    private var enterStatements = emptyList<IRStatement>()
    private var tickStatements = emptyList<IRStatement>()
    private var exitStatements = emptyList<IRStatement>()
    private val transitions = mutableListOf<StateTransition>()
    private var stateAnimation: StateAnimation? = null

    /**
     * Associate an animation with this state. The animation auto-plays when entering the state.
     *
     * Usage: "run" { animation(player, "run") // or with lock: animation(player, "attack",
     * lockUntilComplete = true) }
     */
    fun animation(
        sprite: AnimatedSprite,
        animationName: String,
        lockUntilComplete: Boolean = false,
    ) {
        stateAnimation = StateAnimation(sprite.name, animationName, lockUntilComplete)
    }

    /**
     * Transition when the state's animation completes. Only valid for states with animations.
     *
     * Usage: "attack" { animation(player, "attack") onAnimationComplete { goto("idle") } }
     */
    fun onAnimationComplete(block: TransitionScope.() -> Unit) {
        // This creates a special transition that checks for animation completion
        val scope = TransitionScope(machineName)
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { scope.block() }

        if (scope.targetState != null && stateAnimation != null) {
            // Create condition: animation is complete (anim var == ANIM_NONE)
            val animVar = "_${stateAnimation!!.spriteName}_anim"
            val condition =
                IRBinary(
                    IRVar(animVar),
                    BinaryOp.EQ,
                    IRLiteral(255u.toInt()), // ANIM_NONE = 255
                )
            transitions.add(
                StateTransition(
                    condition = condition,
                    targetState = scope.targetState!!,
                    actions = recorder.statements,
                )
            )
        }
    }

    /** Code to run when entering this state. */
    fun enter(block: StateScope.() -> Unit) {
        enterStatements = recordBlock(block)
    }

    /** Code to run every frame while in this state. */
    fun tick(block: StateScope.() -> Unit) {
        tickStatements = recordBlock(block)
    }

    /** Code to run when exiting this state. */
    fun exit(block: StateScope.() -> Unit) {
        exitStatements = recordBlock(block)
    }

    /**
     * Define a transition.
     *
     * Usage: on(buttons.a.pressed) { goto("jump") } on(playerY isAtLeast groundY) { goto("idle") }
     */
    fun on(condition: Condition, block: TransitionScope.() -> Unit) {
        val scope = TransitionScope(machineName)
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { scope.block() }

        if (scope.targetState != null) {
            transitions.add(
                StateTransition(
                    condition = condition.ir,
                    targetState = scope.targetState!!,
                    actions = recorder.statements,
                )
            )
        }
    }

    private fun recordBlock(block: StateScope.() -> Unit): List<IRStatement> {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { StateScope(machineName).block() }
        return recorder.statements
    }

    fun build() =
        State(
            name = stateName,
            onEnter = enterStatements,
            onTick = tickStatements,
            onExit = exitStatements,
            transitions = transitions,
            animation = stateAnimation,
        )
}

/** Scope available inside state enter/tick/exit blocks. */
class StateScope(private val machineName: String) {
    /** Transition to another state (type-safe). */
    fun goto(ref: StateRef) {
        require(ref.machineName == machineName) {
            "StateRef '${ref.stateName}' belongs to machine '${ref.machineName}', not '$machineName'"
        }
        RecordingContext.require().emit(IRStateMachineGoto(machineName, ref.stateName))
    }

    /** Transition to another state (string-based, for forward references). */
    fun goto(stateName: String) {
        RecordingContext.require().emit(IRStateMachineGoto(machineName, stateName))
    }
}

/** Scope available inside transition blocks. */
class TransitionScope(private val machineName: String) {
    internal var targetState: String? = null

    /**
     * Specify the target state for this transition (type-safe). Also usable as infix: on(condition)
     * goto idle
     */
    infix fun goto(ref: StateRef) {
        require(ref.machineName == machineName) {
            "StateRef '${ref.stateName}' belongs to machine '${ref.machineName}', not '$machineName'"
        }
        targetState = ref.stateName
        RecordingContext.require().emit(IRStateMachineGoto(machineName, ref.stateName))
    }

    /** Specify the target state for this transition (string-based). */
    infix fun goto(stateName: String) {
        targetState = stateName
        RecordingContext.require().emit(IRStateMachineGoto(machineName, stateName))
    }
}

// =============================================================================
// IR NODES FOR STATE MACHINES
// =============================================================================

/** Update a state machine - process current state tick and check transitions. */
// IRStateMachineUpdate moved to io.github.gbkt.core.ir.MiscIR
