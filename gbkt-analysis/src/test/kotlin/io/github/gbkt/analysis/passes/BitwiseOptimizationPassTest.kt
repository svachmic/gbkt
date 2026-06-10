/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.analysis.FakeProfile
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.Severity
import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.VarRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BitwiseOptimizationPassTest {

    private val pass = BitwiseOptimizationPass()

    private fun makeContext(game: GameIR): PassContext =
        PassContext(game = game, profile = FakeProfile, config = AnalysisConfig(maxBanks = 32))

    /**
     * Runs the pass on a game with a single Assign holding [expr] and returns the rewritten expr.
     */
    private fun optimizeSingleExpr(expr: Expr): Pair<Expr, PassContext> {
        val assignOp = Assign(target = "result", value = expr)
        val scene = SceneIR(id = "test", enterOps = listOf(assignOp))
        val game = GameIR(name = "Test", scenes = listOf(scene))

        val result = pass.run(makeContext(game))
        val success = assertIs<PassResult.Success>(result)

        val foldedScene = success.context.game.scenes.first()
        val foldedOp = foldedScene.enterOps.first()
        val foldedAssign = assertIs<Assign>(foldedOp)
        return foldedAssign.value to success.context
    }

    // -------------------------------------------------------------------------
    // MUL → SHL rewrites
    // -------------------------------------------------------------------------

    @Test
    fun `x times 4 is rewritten to x shift left 2`() {
        val expr = BinaryExpr(VarRef("x"), BinaryOp.MUL, Literal(4))
        val (rewritten, ctx) = optimizeSingleExpr(expr)
        val expected = BinaryExpr(VarRef("x"), BinaryOp.SHL, Literal(2))
        assertEquals(expected, rewritten, "Expected x*4 → x<<2")
        assertTrue(
            ctx.diagnostics.any { it.severity == Severity.INFO && it.message.contains("<<2") }
        )
    }

    @Test
    fun `x times 2 is rewritten to x shift left 1`() {
        val expr = BinaryExpr(VarRef("score"), BinaryOp.MUL, Literal(2))
        val (rewritten, _) = optimizeSingleExpr(expr)
        val expected = BinaryExpr(VarRef("score"), BinaryOp.SHL, Literal(1))
        assertEquals(expected, rewritten, "Expected score*2 → score<<1")
    }

    @Test
    fun `x times 256 is rewritten to x shift left 8`() {
        val expr = BinaryExpr(VarRef("addr"), BinaryOp.MUL, Literal(256))
        val (rewritten, _) = optimizeSingleExpr(expr)
        val expected = BinaryExpr(VarRef("addr"), BinaryOp.SHL, Literal(8))
        assertEquals(expected, rewritten, "Expected addr*256 → addr<<8")
    }

    // -------------------------------------------------------------------------
    // DIV → SHR rewrites
    // -------------------------------------------------------------------------

    @Test
    fun `x divided by 8 is rewritten to x shift right 3`() {
        val expr = BinaryExpr(VarRef("x"), BinaryOp.DIV, Literal(8))
        val (rewritten, _) = optimizeSingleExpr(expr)
        val expected = BinaryExpr(VarRef("x"), BinaryOp.SHR, Literal(3))
        assertEquals(expected, rewritten, "Expected x/8 → x>>3")
    }

    @Test
    fun `x divided by 4 is rewritten to x shift right 2`() {
        val expr = BinaryExpr(VarRef("hp"), BinaryOp.DIV, Literal(4))
        val (rewritten, _) = optimizeSingleExpr(expr)
        val expected = BinaryExpr(VarRef("hp"), BinaryOp.SHR, Literal(2))
        assertEquals(expected, rewritten, "Expected hp/4 → hp>>2")
    }

    @Test
    fun `x divided by 16 is rewritten to x shift right 4`() {
        val expr = BinaryExpr(VarRef("tiles"), BinaryOp.DIV, Literal(16))
        val (rewritten, _) = optimizeSingleExpr(expr)
        val expected = BinaryExpr(VarRef("tiles"), BinaryOp.SHR, Literal(4))
        assertEquals(expected, rewritten, "Expected tiles/16 → tiles>>4")
    }

    // -------------------------------------------------------------------------
    // MOD → AND rewrites
    // -------------------------------------------------------------------------

    @Test
    fun `x modulo 16 is rewritten to x bitwise and 15`() {
        val expr = BinaryExpr(VarRef("step"), BinaryOp.MOD, Literal(16))
        val (rewritten, _) = optimizeSingleExpr(expr)
        val expected = BinaryExpr(VarRef("step"), BinaryOp.AND, Literal(15))
        assertEquals(expected, rewritten, "Expected step%16 → step&15")
    }

    @Test
    fun `x modulo 8 is rewritten to x bitwise and 7`() {
        val expr = BinaryExpr(VarRef("frame"), BinaryOp.MOD, Literal(8))
        val (rewritten, _) = optimizeSingleExpr(expr)
        val expected = BinaryExpr(VarRef("frame"), BinaryOp.AND, Literal(7))
        assertEquals(expected, rewritten, "Expected frame%8 → frame&7")
    }

    @Test
    fun `x modulo 4 is rewritten to x bitwise and 3`() {
        val expr = BinaryExpr(VarRef("counter"), BinaryOp.MOD, Literal(4))
        val (rewritten, _) = optimizeSingleExpr(expr)
        val expected = BinaryExpr(VarRef("counter"), BinaryOp.AND, Literal(3))
        assertEquals(expected, rewritten, "Expected counter%4 → counter&3")
    }

    // -------------------------------------------------------------------------
    // Non-power-of-2: no rewrite
    // -------------------------------------------------------------------------

    @Test
    fun `x times 3 is NOT rewritten (3 is not power of 2)`() {
        val expr = BinaryExpr(VarRef("x"), BinaryOp.MUL, Literal(3))
        val (rewritten, _) = optimizeSingleExpr(expr)
        assertEquals(expr, rewritten, "Expected x*3 to remain unchanged — 3 is not a power of 2")
    }

    @Test
    fun `x divided by 7 is NOT rewritten (7 is not power of 2)`() {
        val expr = BinaryExpr(VarRef("x"), BinaryOp.DIV, Literal(7))
        val (rewritten, _) = optimizeSingleExpr(expr)
        assertEquals(expr, rewritten, "Expected x/7 to remain unchanged — 7 is not a power of 2")
    }

    @Test
    fun `x modulo 6 is NOT rewritten (6 is not power of 2)`() {
        val expr = BinaryExpr(VarRef("x"), BinaryOp.MOD, Literal(6))
        val (rewritten, _) = optimizeSingleExpr(expr)
        assertEquals(expr, rewritten, "Expected x%6 to remain unchanged — 6 is not a power of 2")
    }

    @Test
    fun `x times 0 is NOT rewritten (0 is not a positive power of 2)`() {
        val expr = BinaryExpr(VarRef("x"), BinaryOp.MUL, Literal(0))
        val (rewritten, _) = optimizeSingleExpr(expr)
        assertEquals(expr, rewritten, "Expected x*0 to remain unchanged — 0 is not a power of 2")
    }

    // -------------------------------------------------------------------------
    // Literal on left side: commutative MUL still rewrites
    // -------------------------------------------------------------------------

    @Test
    fun `4 times x IS rewritten (MUL is commutative)`() {
        // MUL is commutative, so 4*x should rewrite to x<<2
        val expr = BinaryExpr(Literal(4), BinaryOp.MUL, VarRef("x"))
        val (rewritten, _) = optimizeSingleExpr(expr)
        assertEquals(
            BinaryExpr(VarRef("x"), BinaryOp.SHL, Literal(2)),
            rewritten,
            "Expected 4*x rewritten to x<<2",
        )
    }

    // -------------------------------------------------------------------------
    // ADD/SUB: no rewrite (only MUL/DIV/MOD)
    // -------------------------------------------------------------------------

    @Test
    fun `x plus 4 is NOT rewritten (ADD is not a candidate op)`() {
        val expr = BinaryExpr(VarRef("x"), BinaryOp.ADD, Literal(4))
        val (rewritten, _) = optimizeSingleExpr(expr)
        assertEquals(expr, rewritten, "Expected x+4 unchanged — ADD is not a bitwise-opt candidate")
    }

    // -------------------------------------------------------------------------
    // Diagnostic emission
    // -------------------------------------------------------------------------

    @Test
    fun `rewrite emits INFO diagnostic for each optimization`() {
        val expr = BinaryExpr(VarRef("x"), BinaryOp.MUL, Literal(4))
        val (_, ctx) = optimizeSingleExpr(expr)

        val infoDiags = ctx.diagnostics.filter { it.severity == Severity.INFO }
        assertEquals(1, infoDiags.size, "Expected exactly one INFO diagnostic for one rewrite")
        assertTrue(infoDiags[0].id == "OPT-01", "Diagnostic should have id OPT-01")
    }

    // -------------------------------------------------------------------------
    // x * 1 stays (1 is 2^0 but useless shift; should still rewrite per spec)
    // -------------------------------------------------------------------------

    @Test
    fun `x times 1 is rewritten to x shift left 0 (1 is 2 to the power 0)`() {
        val expr = BinaryExpr(VarRef("x"), BinaryOp.MUL, Literal(1))
        val (rewritten, _) = optimizeSingleExpr(expr)
        val expected = BinaryExpr(VarRef("x"), BinaryOp.SHL, Literal(0))
        // 1 is a valid power of 2 (2^0 = 1), so rewrite is applied even if trivial
        assertEquals(expected, rewritten, "Expected x*1 → x<<0 (1 is 2^0)")
    }
}
