/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.IRCombatStateChange
import io.github.gbkt.core.ir.IRCombatStateMachine
import io.github.gbkt.core.ir.IRStatement

// =============================================================================
// COMBAT STATE MACHINE
// =============================================================================

/**
 * Unique identifier for a combat state.
 *
 * States are numbered for efficient switch-case code generation.
 */
@kotlin.jvm.JvmInline value class CombatStateId(val value: Int)

/**
 * Represents a single state in the combat state machine.
 *
 * Each state has:
 * - A unique identifier for code generation
 * - An optional entry action (executed when entering the state)
 * - An update action (executed each frame while in the state)
 * - An optional exit action (executed when leaving the state)
 *
 * @param name Human-readable name for the state
 * @param id Unique numeric identifier
 */
class CombatState internal constructor(val name: String, val id: CombatStateId) {
    internal var onEnter: (List<IRStatement>)? = null
    internal var onUpdate: List<IRStatement> = emptyList()
    internal var onExit: (List<IRStatement>)? = null
}

/** Reference to a combat state for type-safe transitions. */
class CombatStateRef internal constructor(internal val state: CombatState) {
    val name: String
        get() = state.name

    val id: CombatStateId
        get() = state.id
}

/** Builder for individual combat states. */
@GbktDsl
class CombatStateBuilder internal constructor(private val name: String, private val id: Int) {
    private var onEnter: (() -> Unit)? = null
    private var onUpdate: (() -> Unit)? = null
    private var onExit: (() -> Unit)? = null

    /** Define actions to execute when entering this state. */
    fun enter(block: () -> Unit) {
        onEnter = block
    }

    /** Define actions to execute each frame while in this state. */
    fun update(block: () -> Unit) {
        onUpdate = block
    }

    /** Define actions to execute when leaving this state. */
    fun exit(block: () -> Unit) {
        onExit = block
    }

    private fun recordBlock(block: () -> Unit): List<IRStatement> {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { block() }
        return recorder.statements
    }

    internal fun build(): CombatState {
        val state = CombatState(name, CombatStateId(id))

        onEnter?.let { block -> state.onEnter = recordBlock(block) }

        onUpdate?.let { block -> state.onUpdate = recordBlock(block) }

        onExit?.let { block -> state.onExit = recordBlock(block) }

        return state
    }
}

/**
 * Combat state machine definition.
 *
 * Manages transitions between combat states and generates efficient switch-case code for state
 * handling.
 */
class CombatStateMachine
internal constructor(
    val name: String,
    internal val states: Map<String, CombatState>,
    internal val initialState: CombatStateRef?,
)

/**
 * Builder for the combat state machine.
 *
 * Usage:
 * ```kotlin
 * val battleStateMachine = combatStateMachine("battle") {
 *     val idle = state("idle") {
 *         enter { showBattleMenu() }
 *         update { handleMenuInput() }
 *     }
 *
 *     val attacking = state("attacking") {
 *         enter { playAttackAnimation() }
 *         update { ... }
 *         exit { clearAnimation() }
 *     }
 *
 *     initial(idle)
 * }
 * ```
 */
@GbktDsl
class CombatStateMachineBuilder internal constructor(private val name: String) {
    private val states = mutableMapOf<String, CombatState>()
    private var nextStateId = 0
    private var initialState: CombatStateRef? = null

    /**
     * Define a combat state.
     *
     * @param name Unique name for the state
     * @param block Configuration block for the state
     * @return A reference to the state for use in transitions
     */
    fun state(name: String, block: CombatStateBuilder.() -> Unit = {}): CombatStateRef {
        val builder = CombatStateBuilder(name, nextStateId++)
        builder.block()
        val state = builder.build()
        states[name] = state
        return CombatStateRef(state)
    }

    /** Set the initial state of the state machine. */
    fun initial(stateRef: CombatStateRef) {
        initialState = stateRef
    }

    internal fun build(): CombatStateMachine =
        CombatStateMachine(name = name, states = states.toMap(), initialState = initialState)
}

/**
 * Create a combat state machine.
 *
 * @param name Unique name for the state machine
 * @param block Configuration block for defining states
 * @return The configured state machine
 */
fun combatStateMachine(
    name: String,
    block: CombatStateMachineBuilder.() -> Unit,
): CombatStateMachine {
    val builder = CombatStateMachineBuilder(name)
    builder.block()
    return builder.build()
}

// =============================================================================
// STATE TRANSITIONS
// =============================================================================

/**
 * Transition to a new combat state.
 *
 * This will:
 * 1. Execute the current state's exit action (if any)
 * 2. Change to the new state
 * 3. Execute the new state's enter action (if any)
 *
 * Usage:
 * ```kotlin
 * transitionTo(attackingState)
 * ```
 */
fun transitionTo(machine: CombatStateMachine, stateRef: CombatStateRef) {
    if (RecordingContext.isRecording) {
        RecordingContext.require()
            .emit(
                IRCombatStateChange(
                    machineName = machine.name,
                    targetStateId = stateRef.id.value,
                    targetStateName = stateRef.name,
                )
            )
    }
}

/**
 * Emit the state machine IR for code generation.
 *
 * This should be called once during game setup to register the state machine.
 */
fun registerStateMachine(machine: CombatStateMachine) {
    if (RecordingContext.isRecording) {
        RecordingContext.require()
            .emit(
                IRCombatStateMachine(
                    name = machine.name,
                    states =
                        machine.states.values.map { state ->
                            IRCombatStateMachine.StateDefinition(
                                name = state.name,
                                id = state.id.value,
                                onEnter = state.onEnter,
                                onUpdate = state.onUpdate,
                                onExit = state.onExit,
                            )
                        },
                    initialStateId = machine.initialState?.id?.value ?: 0,
                )
            )
    }
}

// =============================================================================
// PREDEFINED COMBAT STATES (Turn-Based)
// =============================================================================

/**
 * Common turn-based battle states.
 *
 * These can be used as a starting point for turn-based combat systems.
 */
object TurnBasedStates {
    /** Battle initialization state */
    const val INIT = "init"

    /** Waiting for player input */
    const val PLAYER_TURN = "player_turn"

    /** Showing battle menu */
    const val MENU = "menu"

    /** Player selecting target */
    const val TARGET_SELECT = "target_select"

    /** Executing player action */
    const val PLAYER_ACTION = "player_action"

    /** Enemy AI deciding action */
    const val ENEMY_TURN = "enemy_turn"

    /** Executing enemy action */
    const val ENEMY_ACTION = "enemy_action"

    /** Applying damage and effects */
    const val RESOLVE = "resolve"

    /** Checking for victory/defeat */
    const val CHECK_END = "check_end"

    /** Battle won */
    const val VICTORY = "victory"

    /** Battle lost */
    const val DEFEAT = "defeat"

    /** Running away */
    const val FLEE = "flee"
}

// =============================================================================
// PREDEFINED COMBAT STATES (Action RPG)
// =============================================================================

/**
 * Common action-RPG combat states.
 *
 * These can be used as a starting point for action-based combat systems.
 */
object ActionCombatStates {
    /** Normal movement/exploration */
    const val IDLE = "idle"

    /** Player is attacking */
    const val ATTACKING = "attacking"

    /** Player is guarding */
    const val GUARDING = "guarding"

    /** Player is dodging */
    const val DODGING = "dodging"

    /** Player is using an ability */
    const val ABILITY = "ability"

    /** Player is hit (stagger) */
    const val HIT_STUN = "hit_stun"

    /** Player is knocked down */
    const val KNOCKED_DOWN = "knocked_down"

    /** Player is recovering */
    const val RECOVERING = "recovering"

    /** Player is dead */
    const val DEAD = "dead"
}

// =============================================================================
// COMBAT CONTEXT
// =============================================================================

/**
 * Combat context that tracks the current state of a battle.
 *
 * This provides a high-level interface for managing combat without directly manipulating state
 * machine internals.
 */
@GbktDsl
class CombatContext internal constructor(val stateMachine: CombatStateMachine) {
    /** The current combatants (player-side) */
    val allies = mutableListOf<Character>()

    /** The current combatants (enemy-side) */
    val enemies = mutableListOf<Character>()

    /** Add an ally to the combat */
    fun addAlly(character: Character) {
        allies.add(character)
    }

    /** Add an enemy to the combat */
    fun addEnemy(character: Character) {
        enemies.add(character)
    }

    /** Start combat with the initial state */
    fun start() {
        stateMachine.initialState?.let { initial -> transitionTo(stateMachine, initial) }
    }

    /** Transition to a different state */
    fun goTo(stateRef: CombatStateRef) {
        transitionTo(stateMachine, stateRef)
    }
}

/**
 * Create a combat context with a state machine.
 *
 * @param stateMachine The state machine to use for this combat
 * @param block Configuration block for setting up combatants
 * @return The configured combat context
 */
fun combat(stateMachine: CombatStateMachine, block: CombatContext.() -> Unit = {}): CombatContext {
    val context = CombatContext(stateMachine)
    context.block()
    return context
}
