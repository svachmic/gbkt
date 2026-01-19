/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.*
import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the combat state machine system.
 *
 * Validates:
 * - State machine creation and configuration
 * - State transitions
 * - Enter/update/exit callbacks
 * - Predefined state constants
 * - Code generation for state machines
 */
class CombatStateTest {

    // =========================================================================
    // STATE MACHINE CREATION
    // =========================================================================

    @Test
    fun `combatStateMachine creates machine with name`() {
        val machine = combatStateMachine("battle") {}

        assertEquals("battle", machine.name)
    }

    @Test
    fun `state creates state with name and id`() {
        val machine =
            combatStateMachine("battle") {
                val idle = state("idle")
                assertEquals("idle", idle.name)
                assertEquals(CombatStateId(0), idle.id)
            }

        assertEquals(1, machine.states.size)
        assertNotNull(machine.states["idle"])
    }

    @Test
    fun `multiple states get sequential ids`() {
        val machine =
            combatStateMachine("battle") {
                val s0 = state("init")
                val s1 = state("menu")
                val s2 = state("action")

                assertEquals(CombatStateId(0), s0.id)
                assertEquals(CombatStateId(1), s1.id)
                assertEquals(CombatStateId(2), s2.id)
            }

        assertEquals(3, machine.states.size)
    }

    @Test
    fun `initial sets initial state`() {
        val machine =
            combatStateMachine("battle") {
                val idle = state("idle")
                val menu = state("menu")
                initial(idle)
            }

        val initialState = requireNotNull(machine.initialState)
        assertEquals("idle", initialState.name)
    }

    // =========================================================================
    // STATE CALLBACKS
    // =========================================================================

    @Test
    fun `state enter callback is recorded`() {
        var machine: CombatStateMachine? = null

        val game =
            gbGame("CombatStateEnterTest") {
                machine =
                    combatStateMachine("battle") {
                        val idle =
                            state("idle") {
                                enter {
                                    // Simple DSL call to generate IR
                                }
                            }
                    }

                start = scene("main") { every.frame {} }
            }

        val state = machine?.states?.get("idle")
        assertNotNull(state?.onEnter)
    }

    @Test
    fun `state update callback is recorded`() {
        var machine: CombatStateMachine? = null

        val game =
            gbGame("CombatStateUpdateTest") {
                machine =
                    combatStateMachine("battle") {
                        val idle =
                            state("idle") {
                                update {
                                    // Simple DSL call to generate IR
                                }
                            }
                    }

                start = scene("main") { every.frame {} }
            }

        val state = machine?.states?.get("idle")
        assertNotNull(state?.onUpdate)
    }

    @Test
    fun `state exit callback is recorded`() {
        var machine: CombatStateMachine? = null

        val game =
            gbGame("CombatStateExitTest") {
                machine =
                    combatStateMachine("battle") {
                        val idle =
                            state("idle") {
                                exit {
                                    // Simple DSL call to generate IR
                                }
                            }
                    }

                start = scene("main") { every.frame {} }
            }

        val state = machine?.states?.get("idle")
        assertNotNull(state?.onExit)
    }

    // =========================================================================
    // PREDEFINED STATES
    // =========================================================================

    @Test
    fun `turn based states constants are defined`() {
        assertEquals("init", TurnBasedStates.INIT)
        assertEquals("player_turn", TurnBasedStates.PLAYER_TURN)
        assertEquals("menu", TurnBasedStates.MENU)
        assertEquals("target_select", TurnBasedStates.TARGET_SELECT)
        assertEquals("player_action", TurnBasedStates.PLAYER_ACTION)
        assertEquals("enemy_turn", TurnBasedStates.ENEMY_TURN)
        assertEquals("enemy_action", TurnBasedStates.ENEMY_ACTION)
        assertEquals("resolve", TurnBasedStates.RESOLVE)
        assertEquals("check_end", TurnBasedStates.CHECK_END)
        assertEquals("victory", TurnBasedStates.VICTORY)
        assertEquals("defeat", TurnBasedStates.DEFEAT)
        assertEquals("flee", TurnBasedStates.FLEE)
    }

    @Test
    fun `action combat states constants are defined`() {
        assertEquals("idle", ActionCombatStates.IDLE)
        assertEquals("attacking", ActionCombatStates.ATTACKING)
        assertEquals("guarding", ActionCombatStates.GUARDING)
        assertEquals("dodging", ActionCombatStates.DODGING)
        assertEquals("ability", ActionCombatStates.ABILITY)
        assertEquals("hit_stun", ActionCombatStates.HIT_STUN)
        assertEquals("knocked_down", ActionCombatStates.KNOCKED_DOWN)
        assertEquals("recovering", ActionCombatStates.RECOVERING)
        assertEquals("dead", ActionCombatStates.DEAD)
    }

    // =========================================================================
    // COMBAT CONTEXT
    // =========================================================================

    @Test
    fun `combat context tracks allies`() {
        val game =
            gbGame("CombatContextAllyTest") {
                val hero by character { stats { hp(100) } }

                val battleMachine =
                    combatStateMachine("battle") {
                        val idle = state("idle")
                        initial(idle)
                    }

                val context = combat(battleMachine) { addAlly(hero) }

                assertEquals(1, context.allies.size)
                assertEquals("hero", context.allies.first().name)

                start = scene("main") { every.frame {} }
            }
    }

    @Test
    fun `combat context tracks enemies`() {
        val game =
            gbGame("CombatContextEnemyTest") {
                val goblin by character { stats { hp(30) } }

                val battleMachine =
                    combatStateMachine("battle") {
                        val idle = state("idle")
                        initial(idle)
                    }

                val context = combat(battleMachine) { addEnemy(goblin) }

                assertEquals(1, context.enemies.size)
                assertEquals("goblin", context.enemies.first().name)

                start = scene("main") { every.frame {} }
            }
    }

    // =========================================================================
    // IR GENERATION
    // =========================================================================

    @Test
    fun `registerStateMachine emits IR`() {
        val game =
            gbGame("RegisterMachineIRTest") {
                val battleMachine =
                    combatStateMachine("battle") {
                        val idle = state("idle")
                        val attacking = state("attacking")
                        initial(idle)
                    }

                start = scene("main") { every.frame { registerStateMachine(battleMachine) } }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasStateMachineIR = scene.onFrame.any { stmt -> stmt is IRCombatStateMachine }
        assertTrue(hasStateMachineIR, "Should emit IRCombatStateMachine")
    }

    @Test
    fun `transitionTo emits IR`() {
        val game =
            gbGame("TransitionIRTest") {
                val battleMachine =
                    combatStateMachine("battle") {
                        val idle = state("idle")
                        val attacking = state("attacking")
                        initial(idle)
                    }

                start =
                    scene("main") {
                        every.frame {
                            transitionTo(
                                battleMachine,
                                battleMachine.states["attacking"]?.let { state ->
                                    CombatStateRef(state)
                                } ?: error("State not found"),
                            )
                        }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasTransitionIR = scene.onFrame.any { stmt -> stmt is IRCombatStateChange }
        assertTrue(hasTransitionIR, "Should emit IRCombatStateChange")
    }

    // =========================================================================
    // CODE GENERATION
    // =========================================================================

    @Test
    fun `state machine code generation includes state constants`() {
        val game =
            gbGame("StateMachineCodegenTest") {
                val battleMachine =
                    combatStateMachine("battle") {
                        val idle = state("idle")
                        val attacking = state("attacking")
                        initial(idle)
                    }

                start =
                    scene("main") {
                        enter { registerStateMachine(battleMachine) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("BATTLE_STATE_IDLE"), "Should generate state constant for idle")
        assertTrue(
            code.contains("BATTLE_STATE_ATTACKING"),
            "Should generate state constant for attacking",
        )
    }

    @Test
    fun `state machine code generation includes state variables`() {
        val game =
            gbGame("StateVarsCodegenTest") {
                val battleMachine =
                    combatStateMachine("battle") {
                        val idle = state("idle")
                        initial(idle)
                    }

                start =
                    scene("main") {
                        enter { registerStateMachine(battleMachine) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("battle_state"), "Should generate state variable")
        assertTrue(code.contains("battle_prev_state"), "Should generate prev state variable")
    }

    @Test
    fun `state machine code generation includes transition function`() {
        val game =
            gbGame("TransitionFuncCodegenTest") {
                val battleMachine =
                    combatStateMachine("battle") {
                        val idle = state("idle")
                        val attacking = state("attacking")
                        initial(idle)
                    }

                start =
                    scene("main") {
                        enter { registerStateMachine(battleMachine) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("battle_transition"), "Should generate transition function")
    }

    @Test
    fun `state machine code generation includes update function`() {
        val game =
            gbGame("UpdateFuncCodegenTest") {
                val battleMachine =
                    combatStateMachine("battle") {
                        val idle = state("idle")
                        initial(idle)
                    }

                start =
                    scene("main") {
                        enter { registerStateMachine(battleMachine) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("battle_update"), "Should generate update function")
    }
}
