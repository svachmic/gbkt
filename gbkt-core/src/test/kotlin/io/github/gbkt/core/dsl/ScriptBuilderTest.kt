/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.MoveBy
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.PlaySound
import io.github.gbkt.core.ir.PrintAt
import io.github.gbkt.core.ir.StringLiteral
import io.github.gbkt.core.ir.VarRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ScriptBuilderTest {

    /**
     * Builds a script using the game {} DSL for ops that need ScriptBuilderContext (e.g.
     * AssignableVar.set, array writes). The game builder sets up the context internally.
     */
    private fun buildGameScript(
        varSetup: GameBuilder.() -> Unit = {},
        block: ScriptBuilder.() -> Unit,
    ): List<io.github.gbkt.core.ir.ScriptOp> {
        val ir =
            game("T") {
                    varSetup()
                    val sScene = scene("s") { enter { block() } }
                    start = sScene
                }
                .build()
        return ir.scenes.first { it.id == "s" }.enterOps
    }

    /**
     * Builds a script using ScriptBuilder directly for ops that don't need context (navigate,
     * moveBy, playSound, printAt, whenever, ifOp, hideSprites, etc.).
     */
    private fun buildScript(
        block: ScriptBuilder.() -> Unit
    ): List<io.github.gbkt.core.ir.ScriptOp> {
        val builder = ScriptBuilder()
        builder.block()
        return builder.build()
    }

    @Test
    fun `assign via delegate set produces Assign ScriptOp with SET op`() {
        val ops =
            buildGameScript(
                varSetup = {
                    @Suppress("UNUSED_VARIABLE") var score by u8Var(0)
                },
                block = {
                    val score = AssignableVar("score")
                    score set 10
                },
            )

        assertEquals(1, ops.size)
        val op = assertIs<Assign>(ops[0])
        assertEquals("score", op.target)
        assertEquals(Literal(10), op.value)
        assertEquals(AssignOp.SET, op.op)
    }

    @Test
    fun `ifOp produces IfOp with correct condition and then block`() {
        // ifOp internally uses ScriptBuilderContext.with() for its body block,
        // so AssignableVar.set() inside works without outer context.
        val ops = buildScript {
            ifOp(Literal(1)) {
                val score = AssignableVar("score")
                score set 10
            }
        }

        assertEquals(1, ops.size)
        val op = assertIs<IfOp>(ops[0])
        assertEquals(Literal(1), op.condition)
        assertEquals(1, op.then.size)
        assertTrue(op.otherwise.isEmpty())
    }

    @Test
    fun `ifOp with elseOp produces IfOp with then and otherwise`() {
        val ops = buildScript {
            ifOp(Literal(1)) {
                val score = AssignableVar("score")
                score set 10
            }
            elseOp {
                val score = AssignableVar("score")
                score set 0
            }
        }

        assertEquals(1, ops.size)
        val op = assertIs<IfOp>(ops[0])
        assertEquals(1, op.then.size)
        assertEquals(1, op.otherwise.size)
    }

    @Test
    fun `navigate produces NavigateTo ScriptOp`() {
        val ops = buildScript { navigate(SceneRef("gameplay")) }

        assertEquals(1, ops.size)
        val op = assertIs<NavigateTo>(ops[0])
        assertEquals("gameplay", op.sceneId)
    }

    @Test
    fun `moveBy produces MoveBy ScriptOp`() {
        val ops = buildScript { moveBy("player", Literal(2), Literal(0)) }

        assertEquals(1, ops.size)
        val op = assertIs<MoveBy>(ops[0])
        assertEquals("player", op.actorId)
        assertEquals(Literal(2), op.dx)
        assertEquals(Literal(0), op.dy)
    }

    @Test
    fun `playSound produces PlaySound ScriptOp`() {
        val ops = buildScript { playSound(SoundRef("hit")) }

        assertEquals(1, ops.size)
        val op = assertIs<PlaySound>(ops[0])
        assertEquals("hit", op.soundId)
    }

    @Test
    fun `printAt produces PrintAt ScriptOp`() {
        val ops = buildScript { printAt(0, 14, "Hello World") }

        assertEquals(1, ops.size)
        val op = assertIs<PrintAt>(ops[0])
        assertEquals(0, op.x)
        assertEquals(14, op.y)
        assertEquals("Hello World", op.text)
    }

    @Test
    fun `whenever produces IfOp with condition and body`() {
        val ops = buildScript { runIf(buttons.start.pressed) { navigate(SceneRef("gameplay")) } }

        assertEquals(1, ops.size)
        val op = assertIs<IfOp>(ops[0])
        assertTrue(op.otherwise.isEmpty())
        assertEquals(1, op.then.size)
        assertIs<NavigateTo>(op.then[0])
    }

    @Test
    fun `multiple operations produce multiple ScriptOps in order`() {
        val ops = buildScript {
            hideSprites()
            clear()
            showSprites()
        }

        assertEquals(3, ops.size)
    }

    @Test
    fun `moveBy with actor ref uses actor ID`() {
        // ActorRef should convert to actor ID string
        val actorRef = ActorRef("hero")
        val ops = buildScript { moveBy(actorRef, 2, 0) }

        assertEquals(1, ops.size)
        val op = assertIs<MoveBy>(ops[0])
        assertEquals("hero", op.actorId)
    }

    @Test
    fun `whenever inside frame block records to correct ops list`() {
        val ir =
            game("TestGame") {
                    val gameScene =
                        scene("game") {
                            enter { hideSprites() }
                            frame { runIf(buttons.start.pressed) { navigate(SceneRef("game")) } }
                        }
                    start = gameScene
                }
                .build()

        val gameScene = ir.scenes.first { it.id == "game" }
        assertEquals(1, gameScene.enterOps.size)
        assertEquals(1, gameScene.frameOps.size)
        assertTrue(gameScene.exitOps.isEmpty())

        val frameOp = assertIs<IfOp>(gameScene.frameOps[0])
        assertIs<NavigateTo>(frameOp.then[0])
    }

    @Test
    fun `Literal creates integer constant Expr`() {
        val expr = Literal(42)
        assertEquals(Literal(42), expr)
    }

    @Test
    fun `VarRef creates variable reference Expr`() {
        val expr = VarRef("score")
        assertEquals(VarRef("score"), expr)
    }

    @Test
    fun `stringLiteral helper creates StringLiteral Expr`() {
        val expr = stringLiteral("hello")
        assertEquals(StringLiteral("hello"), expr)
    }
}
