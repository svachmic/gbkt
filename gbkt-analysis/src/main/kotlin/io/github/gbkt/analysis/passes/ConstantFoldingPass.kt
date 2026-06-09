/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.analysis.AnalysisPass
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassOptimizationSummary
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.TernaryExpr
import io.github.gbkt.core.ir.UnaryExpr
import io.github.gbkt.core.ir.UnaryOp

/**
 * Analysis pass that evaluates compile-time-known [Literal] arithmetic expressions.
 *
 * ### Algorithm
 * Recursively walks all [Expr] nodes in all [ScriptOp] lists of every scene. For each [BinaryExpr]
 * where both operands resolve to [Literal] after recursive folding, the binary operation is
 * evaluated at compile time and replaced with a single [Literal].
 *
 * ### Supported operations
 * - Arithmetic: ADD (+), SUB (-), MUL (*), DIV (/ — skipped if divisor=0), MOD (%)
 * - Bitwise: AND (&), OR (|), XOR (^), SHL (<<), SHR (>>)
 * - Comparison: EQ (==), NEQ (!=), LT (<), LTE (<=), GT (>), GTE (>=) → fold to Literal(1/0)
 * - Logical: LOGICAL_AND (&&), LOGICAL_OR (||) → fold to Literal(1/0)
 *
 * ### Notes
 * - Division by zero is NOT folded — the binary node is left unchanged to avoid introducing a
 *   runtime error at compile time.
 * - The pass returns [PassResult.Success] with a new [GameIR] stored on `context.copy(game =
 *   foldedGame)` and a [PassOptimizationSummary] appended to `context.optimizationReport`.
 */
class ConstantFoldingPass : AnalysisPass {

    private var foldCount = 0
    private val foldDetails = mutableListOf<String>()

    override fun run(context: PassContext): PassResult {
        foldCount = 0
        foldDetails.clear()
        val foldedGame = transformExprsInGame(context.game, ::foldExpr)
        val summary =
            PassOptimizationSummary(
                passName = "ConstantFoldingPass",
                itemsTransformed = foldCount,
                details = foldDetails.toList(),
            )
        val updatedContext =
            context
                .copy(game = foldedGame)
                .copy(optimizationReport = context.optimizationReport.withSummary(summary))
        return PassResult.Success(updatedContext)
    }

    // -------------------------------------------------------------------------
    // Expression folding
    // -------------------------------------------------------------------------

    /**
     * Recursively folds a single [Expr] node. For [BinaryExpr] nodes, operands are folded first,
     * then if both become [Literal], the operation is evaluated and a new [Literal] is returned.
     *
     * Non-literal operands are returned unchanged.
     */
    fun foldExpr(expr: Expr): Expr =
        when (expr) {
            is BinaryExpr -> foldBinaryExpr(expr)
            is UnaryExpr -> foldUnaryExpr(expr)
            is TernaryExpr -> foldTernaryExpr(expr)
            else -> mapExprChildren(expr, ::foldExpr)
        }

    private fun foldBinaryExpr(expr: BinaryExpr): Expr {
        val foldedLeft = foldExpr(expr.left)
        val foldedRight = foldExpr(expr.right)

        if (foldedLeft is Literal && foldedRight is Literal) {
            val l = foldedLeft.value
            val r = foldedRight.value
            val result = evalBinaryOp(expr.op, l, r)
            if (result != null) {
                foldCount++
                foldDetails += "Folded ${expr.op}($l, $r) → $result"
                return Literal(result)
            }
        }

        // Short-circuit: AND with known-false left, OR with known-true left
        if (expr.op == BinaryOp.LOGICAL_AND && foldedLeft is Literal && foldedLeft.value == 0) {
            foldCount++
            foldDetails += "Folded LOGICAL_AND(0, ...) → 0 (short-circuit)"
            return Literal(0)
        }
        if (expr.op == BinaryOp.LOGICAL_OR && foldedLeft is Literal && foldedLeft.value != 0) {
            foldCount++
            foldDetails += "Folded LOGICAL_OR(${foldedLeft.value}, ...) → 1 (short-circuit)"
            return Literal(1)
        }

        return expr.copy(left = foldedLeft, right = foldedRight)
    }

    private fun foldUnaryExpr(expr: UnaryExpr): Expr {
        val foldedOperand = foldExpr(expr.operand)
        if (foldedOperand is Literal) {
            val result = evalUnaryOp(expr.op, foldedOperand.value)
            if (result != null) {
                foldCount++
                foldDetails += "Folded ${expr.op}(${foldedOperand.value}) → $result"
                return Literal(result)
            }
        }
        return expr.copy(operand = foldedOperand)
    }

    private fun foldTernaryExpr(expr: TernaryExpr): Expr {
        val foldedCondition = foldExpr(expr.condition)
        val foldedThen = foldExpr(expr.thenExpr)
        val foldedElse = foldExpr(expr.elseExpr)
        if (foldedCondition is Literal) {
            foldCount++
            foldDetails +=
                "Folded ternary(${foldedCondition.value}) → " +
                    "${if (foldedCondition.value != 0) "then" else "else"} branch"
            return if (foldedCondition.value != 0) foldedThen else foldedElse
        }
        return expr.copy(condition = foldedCondition, thenExpr = foldedThen, elseExpr = foldedElse)
    }

    /**
     * Evaluates a binary operation at compile time.
     *
     * @return The integer result, or null if the operation cannot be safely evaluated (e.g. div/0).
     */
    private fun evalBinaryOp(op: BinaryOp, l: Int, r: Int): Int? =
        when (op) {
            BinaryOp.ADD -> l + r
            BinaryOp.SUB -> l - r
            BinaryOp.MUL -> l * r
            BinaryOp.DIV -> if (r == 0) null else l / r
            BinaryOp.MOD -> if (r == 0) null else l % r
            BinaryOp.AND -> l and r
            BinaryOp.OR -> l or r
            BinaryOp.XOR -> l xor r
            BinaryOp.SHL -> l shl r
            BinaryOp.SHR -> l shr r
            BinaryOp.EQ -> if (l == r) 1 else 0
            BinaryOp.NEQ -> if (l != r) 1 else 0
            BinaryOp.LT -> if (l < r) 1 else 0
            BinaryOp.LTE -> if (l <= r) 1 else 0
            BinaryOp.GT -> if (l > r) 1 else 0
            BinaryOp.GTE -> if (l >= r) 1 else 0
            BinaryOp.LOGICAL_AND -> if (l != 0 && r != 0) 1 else 0
            BinaryOp.LOGICAL_OR -> if (l != 0 || r != 0) 1 else 0
        }

    /**
     * Evaluates a unary operation at compile time.
     *
     * @return The integer result, or null if the operation cannot be safely evaluated.
     */
    private fun evalUnaryOp(op: UnaryOp, v: Int): Int? =
        when (op) {
            UnaryOp.NEGATE -> -v
            UnaryOp.BITWISE_NOT -> v.inv()
            UnaryOp.LOGICAL_NOT -> if (v == 0) 1 else 0
        }
}
