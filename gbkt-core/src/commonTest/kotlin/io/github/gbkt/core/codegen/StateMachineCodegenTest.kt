/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.entity.*
import io.github.gbkt.core.gbGame
import io.github.gbkt.core.ir.*
import kotlin.test.Test
import kotlin.test.assertTrue

/** Tests for state machine code generation - update functions, transitions, handlers. */
class StateMachineCodegenTest {

    @Test
    fun `state machine generates update function with state enum`() {
        val game =
            gbGame("test") {
                val player by entity {
                    position(80, 72)

                    states {
                        state("idle") {}
                        state("walking") {}
                    }
                }
                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("player_update"), "Should generate update function")
        assertTrue(code.contains("STATE_PLAYER_IDLE"), "Should generate IDLE state constant")
        assertTrue(code.contains("STATE_PLAYER_WALKING"), "Should generate WALKING state constant")
        assertTrue(code.contains("_player_state"), "Should have state variable")
        assertTrue(code.contains("_player_changed"), "Should have changed flag")
    }

    @Test
    fun `state machine generates enter handlers`() {
        val game =
            gbGame("test") {
                var counter by u8Var(0)
                val player by entity {
                    position(80, 72)

                    states {
                        state("idle") { enter { counter set 1 } }
                        state("active") { enter { counter set 2 } }
                    }
                }
                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Enter handlers should set counter values
        assertTrue(
            code.contains("counter = 1") || code.contains("counter = 1u"),
            "Should generate enter handler for idle",
        )
        assertTrue(
            code.contains("counter = 2") || code.contains("counter = 2u"),
            "Should generate enter handler for active",
        )
    }

    @Test
    fun `state machine generates exit handlers`() {
        val game =
            gbGame("test") {
                var cleanup by u8Var(0)
                val player by entity {
                    position(80, 72)

                    states {
                        state("running") { exit { cleanup set 1 } }
                        state("stopped") {}
                    }
                }
                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Exit handler should be in the exit switch
        assertTrue(
            code.contains("cleanup = 1") || code.contains("cleanup = 1u"),
            "Should generate exit handler",
        )
    }

    @Test
    fun `state machine generates tick handlers`() {
        val game =
            gbGame("test") {
                var moveCount by u8Var(0)
                val player by entity {
                    position(80, 72)

                    states {
                        state("moving") { tick { moveCount += 1 } }
                        state("stopped") {}
                    }
                }
                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Tick handler should update moveCount
        assertTrue(
            code.contains("moveCount") && code.contains("++"),
            "Should generate tick handler",
        )
    }

    @Test
    fun `state machine generates transitions with conditions`() {
        val game =
            gbGame("test") {
                var health by u8Var(100)
                val player by entity {
                    position(80, 72)

                    states {
                        val alive = state("alive") { on(health isEqualTo 0) { goto("dead") } }
                        state("dead") {}
                    }
                }
                start = scene("main") {}
            }

        val code = CodeGenerator(game).generate()

        // Should have transition condition check
        assertTrue(code.contains("health"), "Should check health condition")
        assertTrue(
            code.contains("STATE_PLAYER_DEAD") || code.contains("_player_next"),
            "Should transition to dead state",
        )
    }

    @Test
    fun `state machine start emits correct IR`() {
        val game =
            gbGame("test") {
                val player by entity {
                    position(80, 72)

                    states {
                        state("idle") {}
                        state("active") {}
                    }
                }
                start = scene("main") { enter { player.startState("idle") } }
            }

        val code = CodeGenerator(game).generate()

        // Start should set initial state
        assertTrue(
            code.contains("_player_state = STATE_PLAYER_IDLE") ||
                code.contains("_player_next = STATE_PLAYER_IDLE"),
            "Should set initial state to idle",
        )
        assertTrue(code.contains("_player_changed = 1"), "Should mark state as changed on start")
    }

    @Test
    fun `state machine update is called in scene frame`() {
        val game =
            gbGame("test") {
                val player by entity {
                    position(80, 72)

                    states { state("idle") {} }
                }
                start =
                    scene("main") {
                        enter { player.startState("idle") }
                        every.frame { player.updateStates() }
                    }
            }

        val code = CodeGenerator(game).generate()

        // Update call should be in the main loop
        assertTrue(code.contains("player_update()"), "Should call player_update in frame loop")
    }
}
