/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.test

import io.github.gbkt.core.ir.*
import kotlin.test.*

/**
 * Tests for SimulationContextV2 — the public test API wrapping ScriptOpInterpreter.
 *
 * All tests follow TDD: tests were written first to define the expected public API, then
 * implementation was written to pass them.
 *
 * The API under test matches the locked decisions from the research phase:
 * - advanceFrames, runUntil for frame control
 * - tap, holdDpad, press, release for input simulation
 * - getVar, setVar, assertVar, currentScene, frameCount for state inspection
 * - enterScene for scene control
 * - enableTracing, getTraceLog for debug tracing
 */
class SimulationContextV2Test {

    // =========================================================================
    // Fixtures
    // =========================================================================

    private fun emptyGame(
        scenes: List<SceneIR> = listOf(SceneIR("main")),
        actors: List<ActorIR> = emptyList(),
        variables: List<VariableDef> = emptyList(),
        startScene: String? = "main",
    ) =
        GameIR(
            name = "Test",
            scenes = scenes,
            actors = actors,
            variables = variables,
            startScene = startScene,
        )

    // =========================================================================
    // Frame advancing
    // =========================================================================

    @Test
    fun `advanceFrames increments frameCount by the specified count`() {
        val sim = SimulationContextV2(emptyGame())
        sim.advanceFrames(10)
        assertEquals(10, sim.frameCount)
    }

    @Test
    fun `advanceFrames(0) does not increment frameCount`() {
        val sim = SimulationContextV2(emptyGame())
        sim.advanceFrames(0)
        assertEquals(0, sim.frameCount)
    }

    @Test
    fun `frameCount is initially 0`() {
        val sim = SimulationContextV2(emptyGame())
        assertEquals(0, sim.frameCount)
    }

    // =========================================================================
    // runUntil
    // =========================================================================

    @Test
    fun `runUntil stops when predicate returns true`() {
        val game =
            emptyGame(
                scenes =
                    listOf(
                        SceneIR(
                            "main",
                            frameOps = listOf(Assign("counter", Literal(1), AssignOp.ADD)),
                        )
                    ),
                variables = listOf(VariableDef("counter", VarType.U8, 0)),
            )
        val sim = SimulationContextV2(game)
        sim.runUntil(maxFrames = 100) { getVar("counter") >= 5 }
        // Should stop exactly when counter reaches 5
        assertEquals(5, sim.getVar("counter"))
    }

    @Test
    fun `runUntil throws when condition never met within maxFrames`() {
        val sim = SimulationContextV2(emptyGame())
        // Condition is always false — variable never changes
        assertFailsWith<IllegalStateException> {
            sim.runUntil(maxFrames = 10) { getVar("nope") >= 100 }
        }
    }

    @Test
    fun `runUntil executes at least one frame even if predicate is true on entry`() {
        val sim = SimulationContextV2(emptyGame())
        sim.setVar("score", 100)
        // Predicate already true before first frame — but one frame still executes
        sim.runUntil(maxFrames = 10) { getVar("score") >= 100 }
        // Exactly one frame should have been executed
        assertEquals(1, sim.frameCount)
    }

    // =========================================================================
    // State inspection
    // =========================================================================

    @Test
    fun `getVar returns variable value`() {
        val game = emptyGame(variables = listOf(VariableDef("score", VarType.U16, 99)))
        val sim = SimulationContextV2(game)
        assertEquals(99, sim.getVar("score"))
    }

    @Test
    fun `getVar returns 0 for unknown variables`() {
        val sim = SimulationContextV2(emptyGame())
        assertEquals(0, sim.getVar("unknown"))
    }

    @Test
    fun `setVar directly sets variable value`() {
        val sim = SimulationContextV2(emptyGame())
        sim.setVar("ball.x", 80)
        assertEquals(80, sim.getVar("ball.x"))
    }

    @Test
    fun `assertVar passes when variable matches expected`() {
        val game = emptyGame(variables = listOf(VariableDef("score", VarType.U16, 10)))
        val sim = SimulationContextV2(game)
        sim.assertVar("score", 10) // must not throw
    }

    @Test
    fun `assertVar throws AssertionError when variable does not match expected`() {
        val game = emptyGame(variables = listOf(VariableDef("score", VarType.U16, 5)))
        val sim = SimulationContextV2(game)
        val ex = assertFailsWith<AssertionError> { sim.assertVar("score", 10) }
        // Error message must mention the variable name, expected, and actual values
        assertTrue(
            ex.message?.contains("score") == true,
            "Error message should mention variable name 'score'",
        )
        assertTrue(
            ex.message?.contains("10") == true,
            "Error message should mention expected value 10",
        )
        assertTrue(ex.message?.contains("5") == true, "Error message should mention actual value 5")
    }

    @Test
    fun `currentScene reflects initial scene`() {
        val sim = SimulationContextV2(emptyGame(startScene = "main"))
        assertEquals("main", sim.currentScene)
    }

    @Test
    fun `variables and actor positions persist across frames`() {
        val game =
            emptyGame(
                scenes =
                    listOf(
                        SceneIR(
                            "main",
                            frameOps = listOf(Assign("counter", Literal(1), AssignOp.ADD)),
                        )
                    ),
                variables = listOf(VariableDef("counter", VarType.U8, 0)),
            )
        val sim = SimulationContextV2(game)
        sim.advanceFrames(5)
        assertEquals(5, sim.getVar("counter"))
        sim.advanceFrames(3)
        assertEquals(8, sim.getVar("counter"))
    }

    // =========================================================================
    // Scene control
    // =========================================================================

    @Test
    fun `enterScene changes currentScene`() {
        val game =
            emptyGame(scenes = listOf(SceneIR("main"), SceneIR("gameover")), startScene = "main")
        val sim = SimulationContextV2(game)
        sim.enterScene("gameover")
        assertEquals("gameover", sim.currentScene)
    }

    @Test
    fun `enterScene runs enter ops of new scene`() {
        val game =
            emptyGame(
                scenes =
                    listOf(
                        SceneIR("main"),
                        SceneIR("battle", enterOps = listOf(Assign("entered", Literal(1)))),
                    ),
                startScene = "main",
            )
        val sim = SimulationContextV2(game)
        sim.enterScene("battle")
        assertEquals(1, sim.getVar("entered"))
    }

    // =========================================================================
    // Input simulation
    // =========================================================================

    @Test
    fun `tap sets joypad bit for one frame then clears it`() {
        // Game reads joypad each frame — verify A is pressed during tap
        val game =
            emptyGame(
                scenes =
                    listOf(
                        SceneIR(
                            "main",
                            frameOps =
                                listOf(
                                    // If A button held (bit 0 = 0x01), set wasTapped
                                    IfOp(
                                        condition =
                                            BinaryExpr(
                                                VarRef("__joypad"),
                                                BinaryOp.AND,
                                                Literal(0x01),
                                            ),
                                        then = listOf(Assign("wasTapped", Literal(1))),
                                    )
                                ),
                        )
                    )
            )
        val sim = SimulationContextV2(game)
        sim.tap(GameBoyButton.A)
        // After tap: A was pressed during that frame
        assertEquals(1, sim.getVar("wasTapped"))
        // After additional frames: joypad bit should be released
        sim.advanceFrames(1)
        // wasTapped stays at 1 (variable doesn't reset automatically)
        // but we can check joypad is cleared
        val postFrameJoypad = sim.interpreter.joypad
        assertEquals(0, postFrameJoypad and 0x01)
    }

    @Test
    fun `holdDpad RIGHT holds right for N frames`() {
        // Game reads joypad RIGHT bit (0x10) each frame and counts holds
        val game =
            emptyGame(
                scenes =
                    listOf(
                        SceneIR(
                            "main",
                            frameOps =
                                listOf(
                                    IfOp(
                                        condition =
                                            BinaryExpr(
                                                VarRef("__joypad"),
                                                BinaryOp.AND,
                                                Literal(0x10),
                                            ),
                                        then = listOf(Assign("holdCount", Literal(1), AssignOp.ADD)),
                                    )
                                ),
                        )
                    )
            )
        val sim = SimulationContextV2(game)
        sim.holdDpad(DpadDirection.RIGHT, 5)
        assertEquals(5, sim.getVar("holdCount"))
    }

    @Test
    fun `press sets joypad bit`() {
        val sim = SimulationContextV2(emptyGame())
        sim.press(0x01) // A button
        assertTrue(sim.interpreter.joypad and 0x01 != 0)
    }

    @Test
    fun `release clears joypad bit`() {
        val sim = SimulationContextV2(emptyGame())
        sim.press(0x01)
        sim.release(0x01)
        assertEquals(0, sim.interpreter.joypad and 0x01)
    }

    @Test
    fun `GameBoyButton enum has A B START SELECT`() {
        // Verify all expected buttons exist
        val buttons =
            listOf(GameBoyButton.A, GameBoyButton.B, GameBoyButton.START, GameBoyButton.SELECT)
        assertEquals(4, buttons.size)
    }

    @Test
    fun `DpadDirection enum has UP DOWN LEFT RIGHT`() {
        val directions =
            listOf(DpadDirection.UP, DpadDirection.DOWN, DpadDirection.LEFT, DpadDirection.RIGHT)
        assertEquals(4, directions.size)
    }

    @Test
    fun `GameBoyButton bitmasks match GBDK constants`() {
        assertEquals(0x01, GameBoyButton.A.mask)
        assertEquals(0x02, GameBoyButton.B.mask)
        assertEquals(0x04, GameBoyButton.SELECT.mask)
        assertEquals(0x08, GameBoyButton.START.mask)
    }

    @Test
    fun `DpadDirection bitmasks match GBDK constants`() {
        assertEquals(0x10, DpadDirection.RIGHT.mask)
        assertEquals(0x20, DpadDirection.LEFT.mask)
        assertEquals(0x40, DpadDirection.UP.mask)
        assertEquals(0x80, DpadDirection.DOWN.mask)
    }

    // =========================================================================
    // Tracing
    // =========================================================================

    @Test
    fun `enableTracing causes trace log to contain entries after frame execution`() {
        val game =
            emptyGame(scenes = listOf(SceneIR("main", frameOps = listOf(Assign("x", Literal(42))))))
        val sim = SimulationContextV2(game)
        sim.enableTracing()
        sim.advanceFrames(1)
        assertTrue(
            sim.getTraceLog().isNotEmpty(),
            "Trace log should have entries after enabling tracing",
        )
    }

    @Test
    fun `getTraceLog returns empty list when tracing disabled`() {
        val sim = SimulationContextV2(emptyGame())
        sim.advanceFrames(5)
        assertTrue(
            sim.getTraceLog().isEmpty(),
            "Trace log should be empty when tracing is disabled",
        )
    }

    @Test
    fun `enableTracing causes variable assignments to appear in trace log`() {
        val game =
            emptyGame(
                scenes = listOf(SceneIR("main", frameOps = listOf(Assign("score", Literal(42)))))
            )
        val sim = SimulationContextV2(game)
        sim.enableTracing()
        sim.advanceFrames(1)
        val log = sim.getTraceLog()
        assertTrue(
            log.any { it.contains("score") && it.contains("42") },
            "Trace log should contain score assignment entry",
        )
    }

    // =========================================================================
    // Standard JUnit assertions work out of the box
    // =========================================================================

    @Test
    fun `standard kotlin test assertEquals works alongside assertVar`() {
        val game = emptyGame(variables = listOf(VariableDef("score", VarType.U16, 42)))
        val sim = SimulationContextV2(game)
        // Both should work:
        assertEquals(42, sim.getVar("score"))
        sim.assertVar("score", 42)
    }

    // =========================================================================
    // API completeness (all locked-decision methods exist)
    // =========================================================================

    @Test
    fun `SimulationContextV2 exposes all required API methods`() {
        val sim = SimulationContextV2(emptyGame())
        // These calls verify all methods exist on the API
        sim.advanceFrames(0)
        @Suppress("UNUSED_VARIABLE") val fc = sim.frameCount
        @Suppress("UNUSED_VARIABLE") val scene = sim.currentScene
        sim.getVar("x")
        sim.setVar("x", 0)
        sim.assertVar("x", 0)
        sim.enterScene("main")
        sim.press(0)
        sim.release(0)
        sim.tap(GameBoyButton.A)
        sim.holdDpad(DpadDirection.LEFT, 0)
        sim.enableTracing()
        sim.getTraceLog()
        sim.runUntil(maxFrames = 1) { true }
    }
}
