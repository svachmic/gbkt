/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.analysis.AnalysisPass
import io.github.gbkt.analysis.Diagnostic
import io.github.gbkt.analysis.DiagnosticCode
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassOptimizationSummary
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.Severity
import io.github.gbkt.core.ir.ArrayAccessExpr
import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.CastExpr
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.TernaryExpr
import io.github.gbkt.core.ir.UnaryExpr
import io.github.gbkt.core.ir.VarRef
import io.github.gbkt.core.ir.VarType

/**
 * IR-to-IR optimization pass that rewrites power-of-2 arithmetic to faster bitwise operations.
 *
 * ### Rewrites applied
 * - `x * N` where N is a power of 2 → `x << log2(N)`
 * - `x / N` where N is a power of 2 → `x >> log2(N)`
 * - `x % N` where N is a power of 2 → `x & (N - 1)`
 *
 * These transformations are valid for unsigned integer types. The pass applies them unconditionally
 * since Game Boy variables are typically unsigned (U8/U16). This assumption is documented in each
 * emitted diagnostic.
 *
 * ### Diagnostics
 * Emits an INFO diagnostic for each rewrite: "Optimized x*N to x<<M (power-of-2 constant)".
 *
 * ### Example
 *
 * ```
 * score * 4  →  score << 2
 * hp / 8     →  hp >> 3
 * step % 16  →  step & 15
 * ```
 */
class BitwiseOptimizationPass : AnalysisPass {

    private val rewriteDiagnostics = mutableListOf<Diagnostic>()
    private val rewriteDetails = mutableListOf<String>()
    private var varTypes: Map<String, VarType> = emptyMap()

    override fun run(context: PassContext): PassResult {
        rewriteDiagnostics.clear()
        rewriteDetails.clear()
        varTypes = context.game.variables.associate { it.name to it.type }
        val optimizedGame = transformExprsInGame(context.game, ::optimizeExpr)
        val summary =
            PassOptimizationSummary(
                passName = "BitwiseOptimizationPass",
                itemsTransformed = rewriteDetails.size,
                details = rewriteDetails.toList(),
            )
        val updatedContext =
            context
                .copy(game = optimizedGame)
                .withDiagnostics(rewriteDiagnostics.toList())
                .copy(optimizationReport = context.optimizationReport.withSummary(summary))
        return PassResult.Success(updatedContext)
    }

    // -------------------------------------------------------------------------
    // Expression optimization — power-of-2 arithmetic rewrites
    // -------------------------------------------------------------------------

    /**
     * Recursively optimizes a single [Expr] node.
     *
     * For [BinaryExpr] nodes with a power-of-2 literal right operand, applies the appropriate
     * bitwise rewrite. Sub-expressions are always optimized recursively first (bottom-up).
     * Delegates per-op rewrites to focused private helpers to stay below the S3776 threshold.
     */
    fun optimizeExpr(expr: Expr): Expr {
        // Non-BinaryExpr: recurse into children via shared helper
        if (expr !is BinaryExpr) return mapExprChildren(expr, ::optimizeExpr)

        // Recursively optimize sub-expressions first (bottom-up)
        val optimizedLeft = optimizeExpr(expr.left)
        val optimizedRight = optimizeExpr(expr.right)
        val rebuiltExpr = expr.copy(left = optimizedLeft, right = optimizedRight)

        // Check for power-of-2 constant on the right side
        val rightIsPow2 = optimizedRight is Literal && isPow2(optimizedRight.value)
        // For commutative MUL, also check the left side
        val leftIsPow2 = optimizedLeft is Literal && isPow2(optimizedLeft.value)

        if (!rightIsPow2 && !(leftIsPow2 && rebuiltExpr.op == BinaryOp.MUL)) return rebuiltExpr

        return when (rebuiltExpr.op) {
            BinaryOp.MUL -> optimizeMul(rebuiltExpr, optimizedLeft, optimizedRight, rightIsPow2)
            BinaryOp.DIV -> optimizeDiv(rebuiltExpr, optimizedLeft, optimizedRight)
            BinaryOp.MOD -> optimizeMod(rebuiltExpr, optimizedLeft, optimizedRight)
            else -> rebuiltExpr
        }
    }

    /**
     * Rewrites `x * N` (N is a power of 2) to `x << log2(N)`.
     *
     * Handles commutativity: accepts a power-of-2 constant on either side; [rightIsPow2] signals
     * which side holds the constant so the correct operand is selected as the shift amount.
     */
    private fun optimizeMul(
        expr: BinaryExpr,
        optimizedLeft: Expr,
        optimizedRight: Expr,
        rightIsPow2: Boolean,
    ): Expr {
        // Pick whichever side has the power-of-2 constant (prefer right)
        // The cast below is NOT redundant: K2 cannot smart-cast optimizedLeft to Literal
        // from the compound leftIsPow2/rightIsPow2 guards (Sonar S6531 false positive).
        val n =
            if (rightIsPow2) (optimizedRight as Literal).value else (optimizedLeft as Literal).value
        val other = if (rightIsPow2) optimizedLeft else optimizedRight
        val shift = log2(n)
        val rewritten = expr.copy(left = other, op = BinaryOp.SHL, right = Literal(shift))
        emitRewriteDiagnostic("*$n", "<<$shift", expr)
        return rewritten
    }

    /**
     * Rewrites `x / N` (N is a power of 2, x is unsigned) to `x >> log2(N)`.
     *
     * Skipped when [isMaybeSigned] returns true for the dividend to avoid incorrect results for
     * negative values.
     */
    private fun optimizeDiv(expr: BinaryExpr, optimizedLeft: Expr, optimizedRight: Expr): Expr {
        val n = (optimizedRight as Literal).value
        if (isMaybeSigned(optimizedLeft)) return expr
        val shift = log2(n)
        val rewritten = expr.copy(op = BinaryOp.SHR, right = Literal(shift))
        emitRewriteDiagnostic("/$n", ">>$shift", expr)
        return rewritten
    }

    /**
     * Rewrites `x % N` (N is a power of 2, x is unsigned) to `x & (N - 1)`.
     *
     * Skipped when [isMaybeSigned] returns true for the dividend to avoid incorrect results for
     * negative values.
     */
    private fun optimizeMod(expr: BinaryExpr, optimizedLeft: Expr, optimizedRight: Expr): Expr {
        val n = (optimizedRight as Literal).value
        if (isMaybeSigned(optimizedLeft)) return expr
        val mask = n - 1
        val rewritten = expr.copy(op = BinaryOp.AND, right = Literal(mask))
        emitRewriteDiagnostic("%$n", "&$mask", expr)
        return rewritten
    }

    // -------------------------------------------------------------------------
    // Helper functions
    // -------------------------------------------------------------------------

    /**
     * Returns true if [n] is a positive power of 2.
     *
     * Uses the standard bit trick: a power-of-2 has exactly one bit set, so n & (n-1) == 0.
     */
    private fun isPow2(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0

    /**
     * Returns the base-2 logarithm of [n].
     *
     * Equivalent to the number of trailing zeros in the binary representation of [n]. Requires [n]
     * to be a positive power of 2 — call [isPow2] first.
     */
    private fun log2(n: Int): Int = Integer.numberOfTrailingZeros(n)

    /**
     * Returns true if [expr] contains any [VarRef] to a known signed variable (I8, I16).
     *
     * Recursively checks compound expressions (e.g. `signedVar + 1`) so that div-to-shift and
     * mod-to-and rewrites are blocked when the result might be negative. Unknown variables and
     * expression trees with no signed VarRefs are treated as unsigned (Game Boy variables are
     * typically unsigned).
     */
    private fun isMaybeSigned(expr: Expr): Boolean =
        when (expr) {
            is VarRef -> {
                val type = varTypes[expr.name] ?: return false // unknown variable — assume unsigned
                type == VarType.I8 || type == VarType.I16
            }
            is BinaryExpr -> isMaybeSigned(expr.left) || isMaybeSigned(expr.right)
            is UnaryExpr -> isMaybeSigned(expr.operand)
            is TernaryExpr -> isMaybeSigned(expr.thenExpr) || isMaybeSigned(expr.elseExpr)
            is CastExpr ->
                expr.targetType == VarType.I8 ||
                    expr.targetType == VarType.I16 ||
                    isMaybeSigned(expr.inner)
            is ArrayAccessExpr -> isMaybeSigned(expr.index)
            else ->
                false // Literal, StringLiteral, PropertyAccessExpr, CallExpr, PoolGetActiveCount
        }

    private fun emitRewriteDiagnostic(fromOp: String, toOp: String, original: BinaryExpr) {
        val message =
            "Optimized x${fromOp} to x${toOp} (power-of-2 constant; assumes unsigned operand)"
        rewriteDiagnostics +=
            Diagnostic(
                code = DiagnosticCode.BITWISE_REWRITE,
                severity = Severity.INFO,
                message = message,
                location = original.sourceLocation?.let { "line ${it.line}" },
            )
        rewriteDetails += message
    }
}
