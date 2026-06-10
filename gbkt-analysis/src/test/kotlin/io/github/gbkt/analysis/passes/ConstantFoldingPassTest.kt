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

class ConstantFoldingPassTest {

    private val pass = ConstantFoldingPass()

    private fun makeContext(game: GameIR): PassContext =
        PassContext(game = game, profile = FakeProfile, config = AnalysisConfig(maxBanks = 32))

    /**
     * Builds a GameIR with a single scene containing one Assign op with the given expression.
     * Returns the game and the result context after folding, then extracts the folded expression.
     */
    private fun foldSingleExpr(expr: Expr): Expr {
        val assignOp = Assign(target = "result", value = expr)
        val scene = SceneIR(id = "test", enterOps = listOf(assignOp))
        val game = GameIR(name = "Test", scenes = listOf(scene))

        val result = pass.run(makeContext(game))
        val success = assertIs<PassResult.Success>(result)

        // Extract the value expression from the (possibly folded) Assign op
        val foldedScene = success.context.game.scenes.first()
        val foldedOp = foldedScene.enterOps.first()
        val foldedAssign = assertIs<Assign>(foldedOp)
        return foldedAssign.value
    }

    @Test
    fun `Binary(Literal(2), ADD, Literal(3)) folds to Literal(5)`() {
        val expr = BinaryExpr(Literal(2), BinaryOp.ADD, Literal(3))
        val folded = foldSingleExpr(expr)
        assertEquals(Literal(5), folded, "Expected Literal(5) after folding 2 + 3")
    }

    @Test
    fun `Binary(Literal(10), SUBTRACT, Literal(3)) folds to Literal(7)`() {
        val expr = BinaryExpr(Literal(10), BinaryOp.SUB, Literal(3))
        val folded = foldSingleExpr(expr)
        assertEquals(Literal(7), folded, "Expected Literal(7) after folding 10 - 3")
    }

    @Test
    fun `Binary(Literal(4), MULTIPLY, Literal(5)) folds to Literal(20)`() {
        val expr = BinaryExpr(Literal(4), BinaryOp.MUL, Literal(5))
        val folded = foldSingleExpr(expr)
        assertEquals(Literal(20), folded, "Expected Literal(20) after folding 4 * 5")
    }

    @Test
    fun `Binary(Literal(10), DIVIDE, Literal(3)) folds to Literal(3) (integer division)`() {
        val expr = BinaryExpr(Literal(10), BinaryOp.DIV, Literal(3))
        val folded = foldSingleExpr(expr)
        assertEquals(Literal(3), folded, "Expected Literal(3) after integer division 10 / 3")
    }

    @Test
    fun `Binary(VarRef, ADD, Literal(3)) stays unchanged`() {
        val expr = BinaryExpr(VarRef("score"), BinaryOp.ADD, Literal(3))
        val folded = foldSingleExpr(expr)
        assertEquals(expr, folded, "Expected expression unchanged when operand is VarRef")
    }

    @Test
    fun `nested folding Binary(Binary(Literal(1), ADD, Literal(2)), MULTIPLY, Literal(3)) folds to Literal(9)`() {
        val inner = BinaryExpr(Literal(1), BinaryOp.ADD, Literal(2))
        val outer = BinaryExpr(inner, BinaryOp.MUL, Literal(3))
        val folded = foldSingleExpr(outer)
        assertEquals(Literal(9), folded, "Expected Literal(9) after nested fold (1+2)*3")
    }

    @Test
    fun `division by zero stays unchanged`() {
        val expr = BinaryExpr(Literal(5), BinaryOp.DIV, Literal(0))
        val folded = foldSingleExpr(expr)
        assertEquals(expr, folded, "Expected division by zero to remain unfolded")
    }
}
