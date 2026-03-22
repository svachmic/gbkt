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
 * Tests for ScriptOpInterpreter — the JVM execution engine for v2 ScriptOps.
 *
 * All tests follow TDD: tests were written first to define expected behavior, then implementation
 * was written to pass them.
 *
 * Test fixtures build minimal inline GameIR objects to keep tests self-contained.
 */
class ScriptOpInterpreterTest {

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
    // Variable initialization
    // =========================================================================

    @Test
    fun `variables initialized from GameIR variables on construction`() {
        val game =
            emptyGame(
                variables =
                    listOf(
                        VariableDef("score", VarType.U16, 42),
                        VariableDef("lives", VarType.U8, 3),
                    )
            )
        val interpreter = ScriptOpInterpreter(game)

        assertEquals(42L, interpreter.getVariable("score"))
        assertEquals(3L, interpreter.getVariable("lives"))
    }

    @Test
    fun `uninitialized variable returns zero`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        assertEquals(0L, interpreter.getVariable("nonexistent"))
    }

    // =========================================================================
    // Actor initialization
    // =========================================================================

    @Test
    fun `actor positions initialized from GameIR actors on construction`() {
        val game =
            emptyGame(
                actors =
                    listOf(
                        ActorIR("ball", PositionDef(80, 72)),
                        ActorIR("player", PositionDef(10, 100)),
                    )
            )
        val interpreter = ScriptOpInterpreter(game)

        val (bx, by) = interpreter.getActorPosition("ball")
        assertEquals(80, bx)
        assertEquals(72, by)

        val (px, py) = interpreter.getActorPosition("player")
        assertEquals(10, px)
        assertEquals(100, py)
    }

    @Test
    fun `actor positions accessible as variables via dot notation`() {
        val game = emptyGame(actors = listOf(ActorIR("ball", PositionDef(80, 72))))
        val interpreter = ScriptOpInterpreter(game)

        assertEquals(80L, interpreter.getVariable("ball.x"))
        assertEquals(72L, interpreter.getVariable("ball.y"))
    }

    // =========================================================================
    // Assign ScriptOp
    // =========================================================================

    @Test
    fun `Assign SET updates variable value`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("score", Literal(100)))
        assertEquals(100L, interpreter.getVariable("score"))
    }

    @Test
    fun `Assign ADD adds to existing value`() {
        val interpreter =
            ScriptOpInterpreter(
                emptyGame(variables = listOf(VariableDef("score", VarType.U16, 50)))
            )
        interpreter.executeOp(Assign("score", Literal(10), AssignOp.ADD))
        assertEquals(60L, interpreter.getVariable("score"))
    }

    @Test
    fun `Assign SUB subtracts from existing value`() {
        val interpreter =
            ScriptOpInterpreter(
                emptyGame(variables = listOf(VariableDef("score", VarType.U16, 50)))
            )
        interpreter.executeOp(Assign("score", Literal(20), AssignOp.SUB))
        assertEquals(30L, interpreter.getVariable("score"))
    }

    @Test
    fun `Assign MUL multiplies existing value`() {
        val interpreter =
            ScriptOpInterpreter(emptyGame(variables = listOf(VariableDef("score", VarType.U16, 5))))
        interpreter.executeOp(Assign("score", Literal(3), AssignOp.MUL))
        assertEquals(15L, interpreter.getVariable("score"))
    }

    // =========================================================================
    // VarRef Expr
    // =========================================================================

    @Test
    fun `VarRef reads assigned variable`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("x", Literal(42)))
        interpreter.executeOp(Assign("y", VarRef("x")))
        assertEquals(42L, interpreter.getVariable("y"))
    }

    // =========================================================================
    // IfOp
    // =========================================================================

    @Test
    fun `IfOp with true condition executes thenOps`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        val ifOp =
            IfOp(
                condition = Literal(1),
                then = listOf(Assign("result", Literal(100))),
                otherwise = listOf(Assign("result", Literal(0))),
            )
        interpreter.executeOp(ifOp)
        assertEquals(100L, interpreter.getVariable("result"))
    }

    @Test
    fun `IfOp with false condition executes elseOps`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        val ifOp =
            IfOp(
                condition = Literal(0),
                then = listOf(Assign("result", Literal(100))),
                otherwise = listOf(Assign("result", Literal(999))),
            )
        interpreter.executeOp(ifOp)
        assertEquals(999L, interpreter.getVariable("result"))
    }

    @Test
    fun `IfOp with no else does nothing when condition is false`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("result", Literal(42)))
        interpreter.executeOp(
            IfOp(condition = Literal(0), then = listOf(Assign("result", Literal(100))))
        )
        assertEquals(42L, interpreter.getVariable("result"))
    }

    // =========================================================================
    // WhileOp
    // =========================================================================

    @Test
    fun `WhileOp loops correct number of times`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("counter", Literal(0)))
        // counter += 1 each iteration, condition: counter < 5
        val whileOp =
            WhileOp(
                condition = BinaryExpr(VarRef("counter"), BinaryOp.LT, Literal(5)),
                body = listOf(Assign("counter", Literal(1), AssignOp.ADD)),
            )
        interpreter.executeOp(whileOp)
        assertEquals(5L, interpreter.getVariable("counter"))
    }

    // =========================================================================
    // ForOp
    // =========================================================================

    @Test
    fun `ForOp iterates variable from from to to inclusive and executes body`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("sum", Literal(0)))
        val forOp =
            ForOp(
                variable = "i",
                from = Literal(1),
                to = Literal(5),
                body = listOf(Assign("sum", VarRef("i"), AssignOp.ADD)),
            )
        interpreter.executeOp(forOp)
        // sum = 1+2+3+4+5 = 15
        assertEquals(15L, interpreter.getVariable("sum"))
    }

    @Test
    fun `ForOp sets loop variable to correct values during iteration`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        val forOp =
            ForOp(
                variable = "i",
                from = Literal(3),
                to = Literal(3),
                body = listOf(Assign("result", VarRef("i"))),
            )
        interpreter.executeOp(forOp)
        // Single iteration: i=3, result=3
        assertEquals(3L, interpreter.getVariable("result"))
    }

    // =========================================================================
    // SetPosition
    // =========================================================================

    @Test
    fun `SetPosition updates actor position and syncs to dot-notation variables`() {
        val game = emptyGame(actors = listOf(ActorIR("ball", PositionDef(0, 0))))
        val interpreter = ScriptOpInterpreter(game)
        interpreter.executeOp(SetPosition("ball", Literal(120), Literal(80)))

        val (x, y) = interpreter.getActorPosition("ball")
        assertEquals(120, x)
        assertEquals(80, y)
        assertEquals(120L, interpreter.getVariable("ball.x"))
        assertEquals(80L, interpreter.getVariable("ball.y"))
    }

    // =========================================================================
    // MoveBy
    // =========================================================================

    @Test
    fun `MoveBy adds offset to actor position`() {
        val game = emptyGame(actors = listOf(ActorIR("ball", PositionDef(50, 50))))
        val interpreter = ScriptOpInterpreter(game)
        interpreter.executeOp(MoveBy("ball", Literal(10), Literal(-5)))

        val (x, y) = interpreter.getActorPosition("ball")
        assertEquals(60, x)
        assertEquals(45, y)
        assertEquals(60L, interpreter.getVariable("ball.x"))
        assertEquals(45L, interpreter.getVariable("ball.y"))
    }

    // =========================================================================
    // NavigateTo
    // =========================================================================

    @Test
    fun `NavigateTo changes currentSceneId`() {
        val game =
            emptyGame(scenes = listOf(SceneIR("main"), SceneIR("gameover")), startScene = "main")
        val interpreter = ScriptOpInterpreter(game)
        assertEquals("main", interpreter.currentSceneId)
        interpreter.executeOp(NavigateTo("gameover"))
        assertEquals("gameover", interpreter.currentSceneId)
    }

    @Test
    fun `NavigateTo runs enterOps of new scene`() {
        val game =
            emptyGame(
                scenes =
                    listOf(
                        SceneIR("main"),
                        SceneIR("gameover", enterOps = listOf(Assign("entered", Literal(1)))),
                    ),
                startScene = "main",
            )
        val interpreter = ScriptOpInterpreter(game)
        interpreter.executeOp(NavigateTo("gameover"))
        assertEquals(1L, interpreter.getVariable("entered"))
    }

    @Test
    fun `NavigateTo runs exitOps of old scene before entering new scene`() {
        val game =
            emptyGame(
                scenes =
                    listOf(
                        SceneIR("main", exitOps = listOf(Assign("exited", Literal(1)))),
                        SceneIR("gameover"),
                    ),
                startScene = "main",
            )
        val interpreter = ScriptOpInterpreter(game)
        interpreter.executeOp(NavigateTo("gameover"))
        assertEquals(1L, interpreter.getVariable("exited"))
    }

    // =========================================================================
    // Expr evaluation: BinaryExpr arithmetic
    // =========================================================================

    @Test
    fun `BinaryExpr ADD produces correct result`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("result", BinaryExpr(Literal(10), BinaryOp.ADD, Literal(3))))
        assertEquals(13L, interpreter.getVariable("result"))
    }

    @Test
    fun `BinaryExpr SUB produces correct result`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("result", BinaryExpr(Literal(10), BinaryOp.SUB, Literal(3))))
        assertEquals(7L, interpreter.getVariable("result"))
    }

    @Test
    fun `BinaryExpr MUL produces correct result`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("result", BinaryExpr(Literal(4), BinaryOp.MUL, Literal(5))))
        assertEquals(20L, interpreter.getVariable("result"))
    }

    @Test
    fun `BinaryExpr DIV produces correct result`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("result", BinaryExpr(Literal(10), BinaryOp.DIV, Literal(2))))
        assertEquals(5L, interpreter.getVariable("result"))
    }

    @Test
    fun `BinaryExpr MOD produces correct result`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("result", BinaryExpr(Literal(10), BinaryOp.MOD, Literal(3))))
        assertEquals(1L, interpreter.getVariable("result"))
    }

    // =========================================================================
    // Expr evaluation: BinaryExpr comparison
    // =========================================================================

    @Test
    fun `BinaryExpr EQ returns 1 for true`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("result", BinaryExpr(Literal(5), BinaryOp.EQ, Literal(5))))
        assertEquals(1L, interpreter.getVariable("result"))
    }

    @Test
    fun `BinaryExpr EQ returns 0 for false`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("result", BinaryExpr(Literal(5), BinaryOp.EQ, Literal(6))))
        assertEquals(0L, interpreter.getVariable("result"))
    }

    @Test
    fun `BinaryExpr LT returns 1 when left is less than right`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("result", BinaryExpr(Literal(3), BinaryOp.LT, Literal(5))))
        assertEquals(1L, interpreter.getVariable("result"))
    }

    @Test
    fun `BinaryExpr GTE returns 1 when left is greater or equal`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("result", BinaryExpr(Literal(5), BinaryOp.GTE, Literal(5))))
        assertEquals(1L, interpreter.getVariable("result"))
    }

    @Test
    fun `BinaryExpr NEQ returns 1 when values differ`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("result", BinaryExpr(Literal(3), BinaryOp.NEQ, Literal(5))))
        assertEquals(1L, interpreter.getVariable("result"))
    }

    // =========================================================================
    // Expr evaluation: BinaryExpr logical operators (short-circuit)
    // =========================================================================

    @Test
    fun `BinaryExpr LOGICAL_AND returns 0 when first operand is false`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        // Second operand should not execute due to short-circuit
        interpreter.executeOp(
            Assign("result", BinaryExpr(Literal(0), BinaryOp.LOGICAL_AND, Literal(1)))
        )
        assertEquals(0L, interpreter.getVariable("result"))
    }

    @Test
    fun `BinaryExpr LOGICAL_AND returns 1 when both operands are true`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(
            Assign("result", BinaryExpr(Literal(1), BinaryOp.LOGICAL_AND, Literal(1)))
        )
        assertEquals(1L, interpreter.getVariable("result"))
    }

    @Test
    fun `BinaryExpr LOGICAL_OR returns 1 when first operand is true`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(
            Assign("result", BinaryExpr(Literal(1), BinaryOp.LOGICAL_OR, Literal(0)))
        )
        assertEquals(1L, interpreter.getVariable("result"))
    }

    @Test
    fun `BinaryExpr LOGICAL_OR returns 0 when both operands are false`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(
            Assign("result", BinaryExpr(Literal(0), BinaryOp.LOGICAL_OR, Literal(0)))
        )
        assertEquals(0L, interpreter.getVariable("result"))
    }

    // =========================================================================
    // Expr evaluation: TernaryExpr
    // =========================================================================

    @Test
    fun `TernaryExpr evaluates thenExpr when condition is non-zero`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("result", TernaryExpr(Literal(1), Literal(100), Literal(200))))
        assertEquals(100L, interpreter.getVariable("result"))
    }

    @Test
    fun `TernaryExpr evaluates elseExpr when condition is zero`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("result", TernaryExpr(Literal(0), Literal(100), Literal(200))))
        assertEquals(200L, interpreter.getVariable("result"))
    }

    // =========================================================================
    // Expr evaluation: PropertyAccessExpr
    // =========================================================================

    @Test
    fun `PropertyAccessExpr reads actor x from actorPositions map`() {
        val game = emptyGame(actors = listOf(ActorIR("ball", PositionDef(77, 33))))
        val interpreter = ScriptOpInterpreter(game)
        interpreter.executeOp(Assign("result", PropertyAccessExpr("ball", "x")))
        assertEquals(77L, interpreter.getVariable("result"))
    }

    @Test
    fun `PropertyAccessExpr reads actor y from actorPositions map`() {
        val game = emptyGame(actors = listOf(ActorIR("ball", PositionDef(77, 33))))
        val interpreter = ScriptOpInterpreter(game)
        interpreter.executeOp(Assign("result", PropertyAccessExpr("ball", "y")))
        assertEquals(33L, interpreter.getVariable("result"))
    }

    // =========================================================================
    // Expr evaluation: UnaryExpr
    // =========================================================================

    @Test
    fun `UnaryExpr NEGATE negates value`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("result", UnaryExpr(UnaryOp.NEGATE, Literal(5))))
        assertEquals(-5L, interpreter.getVariable("result"))
    }

    @Test
    fun `UnaryExpr LOGICAL_NOT returns 1 for 0`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("result", UnaryExpr(UnaryOp.LOGICAL_NOT, Literal(0))))
        assertEquals(1L, interpreter.getVariable("result"))
    }

    @Test
    fun `UnaryExpr LOGICAL_NOT returns 0 for non-zero`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("result", UnaryExpr(UnaryOp.LOGICAL_NOT, Literal(42))))
        assertEquals(0L, interpreter.getVariable("result"))
    }

    // =========================================================================
    // Hardware no-op stubs execute without error
    // =========================================================================

    @Test
    fun `PlaySound executes without error (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(PlaySound("beep")) // must not throw
    }

    @Test
    fun `FadeOp executes without error (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(FadeOp(fadeIn = true, frames = 10)) // must not throw
    }

    @Test
    fun `SetVisible executes without error (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(SetVisible("ball", true)) // must not throw
    }

    @Test
    fun `DialogSay executes without error (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(
            DialogSay("npc1", listOf(DialogTextSegment("Hello")))
        ) // must not throw
    }

    @Test
    fun `MenuShow executes without error (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(MenuShow("menu1")) // must not throw
    }

    @Test
    fun `MenuHide executes without error (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(MenuHide("menu1")) // must not throw
    }

    @Test
    fun `HudShow and HudHide execute without error (no-op stubs)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(HudShow("statsHud")) // must not throw
        interpreter.executeOp(HudHide("statsHud")) // must not throw
    }

    @Test
    fun `PrintAt executes without error (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(PrintAt(0, 14, "Score: 0")) // must not throw
    }

    @Test
    fun `ScreenClear and ScreenFill execute without error (no-op stubs)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(ScreenClear()) // must not throw
        interpreter.executeOp(ScreenFill(tile = 0x01)) // must not throw
    }

    @Test
    fun `WaitFrames executes without error (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(WaitFrames(30)) // must not throw
    }

    @Test
    fun `ArrayAssign executes without error (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(ArrayAssign("arr", Literal(0), Literal(42))) // must not throw
    }

    @Test
    fun `SpawnActor executes without error (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(SpawnActor("enemy")) // must not throw
    }

    @Test
    fun `DestroyActor executes without error (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(DestroyActor("enemy")) // must not throw
    }

    @Test
    fun `AnimateOp executes without error (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(AnimateOp("player", "run")) // must not throw
    }

    @Test
    fun `CameraOp executes without error (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(CameraOp(CameraAction.FOLLOW)) // must not throw
    }

    @Test
    fun `CallOp executes without error (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(CallOp("someFunction", emptyList())) // must not throw
    }

    @Test
    fun `ReturnOp executes without error (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(ReturnOp()) // must not throw
    }

    @Test
    fun `RawOp executes without error (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(RawOp("/* raw C */")) // must not throw
    }

    @Test
    fun `TriggerSystem executes without error (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(TriggerSystem("collision")) // must not throw
    }

    @Test
    fun `PrintOp executes without error (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(PrintOp("score: %d")) // must not throw
    }

    // =========================================================================
    // MathOp
    // =========================================================================

    @Test
    fun `MathOp ABS stores absolute value in result variable`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(MathOp("result", MathFunction.ABS, listOf(Literal(-5))))
        assertEquals(5L, interpreter.getVariable("result"))
    }

    @Test
    fun `MathOp MIN stores minimum in result variable`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(MathOp("result", MathFunction.MIN, listOf(Literal(3), Literal(7))))
        assertEquals(3L, interpreter.getVariable("result"))
    }

    @Test
    fun `MathOp MAX stores maximum in result variable`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(MathOp("result", MathFunction.MAX, listOf(Literal(3), Literal(7))))
        assertEquals(7L, interpreter.getVariable("result"))
    }

    @Test
    fun `MathOp CLAMP clamps value between min and max`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(
            MathOp("result", MathFunction.CLAMP, listOf(Literal(200), Literal(0), Literal(100)))
        )
        assertEquals(100L, interpreter.getVariable("result"))
    }

    // =========================================================================
    // Tracing
    // =========================================================================

    @Test
    fun `tracing disabled by default — traceLog is empty`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("x", Literal(42)))
        assertTrue(interpreter.traceLog.isEmpty())
    }

    @Test
    fun `tracing enabled — variable assignment appears in trace log`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.tracingEnabled = true
        interpreter.executeOp(Assign("x", Literal(42)))
        assertTrue(interpreter.traceLog.any { it.contains("x") && it.contains("42") })
    }

    @Test
    fun `tracing enabled — scene change appears in trace log`() {
        val game =
            emptyGame(scenes = listOf(SceneIR("main"), SceneIR("gameover")), startScene = "main")
        val interpreter = ScriptOpInterpreter(game)
        interpreter.tracingEnabled = true
        interpreter.executeOp(NavigateTo("gameover"))
        assertTrue(interpreter.traceLog.any { it.contains("gameover") })
    }

    // =========================================================================
    // executeFrame / frame lifecycle
    // =========================================================================

    @Test
    fun `executeFrame increments frameCount`() {
        val game = emptyGame(scenes = listOf(SceneIR("main")))
        val interpreter = ScriptOpInterpreter(game)
        assertEquals(0, interpreter.frameCount)
        interpreter.executeFrame()
        assertEquals(1, interpreter.frameCount)
        interpreter.executeFrame()
        assertEquals(2, interpreter.frameCount)
    }

    @Test
    fun `executeFrame runs frameOps of current scene`() {
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
        val interpreter = ScriptOpInterpreter(game)
        interpreter.executeFrame()
        interpreter.executeFrame()
        interpreter.executeFrame()
        assertEquals(3L, interpreter.getVariable("counter"))
    }

    // =========================================================================
    // Collision detection
    // =========================================================================

    @Test
    fun `checkCollision returns true for overlapping actors`() {
        val game =
            emptyGame(
                actors =
                    listOf(
                        ActorIR("ball", PositionDef(50, 50), hitbox = HitboxDef(0, 0, 8, 8)),
                        ActorIR("paddle", PositionDef(50, 50), hitbox = HitboxDef(0, 0, 8, 8)),
                    )
            )
        val interpreter = ScriptOpInterpreter(game)
        assertTrue(interpreter.checkCollision("ball", "paddle"))
    }

    @Test
    fun `checkCollision returns false for non-overlapping actors`() {
        val game =
            emptyGame(
                actors =
                    listOf(
                        ActorIR("ball", PositionDef(10, 10), hitbox = HitboxDef(0, 0, 8, 8)),
                        ActorIR("paddle", PositionDef(100, 100), hitbox = HitboxDef(0, 0, 8, 8)),
                    )
            )
        val interpreter = ScriptOpInterpreter(game)
        assertFalse(interpreter.checkCollision("ball", "paddle"))
    }

    // =========================================================================
    // Expr stubs return 0 without error
    // =========================================================================

    @Test
    fun `ArrayAccessExpr returns 0 (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("result", ArrayAccessExpr("arr", Literal(0))))
        assertEquals(0L, interpreter.getVariable("result"))
    }

    @Test
    fun `CallExpr returns 0 (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("result", CallExpr("someFunc", emptyList())))
        assertEquals(0L, interpreter.getVariable("result"))
    }

    @Test
    fun `StringLiteral returns 0 in numeric context (no-op stub)`() {
        val interpreter = ScriptOpInterpreter(emptyGame())
        interpreter.executeOp(Assign("result", StringLiteral("hello")))
        assertEquals(0L, interpreter.getVariable("result"))
    }
}
