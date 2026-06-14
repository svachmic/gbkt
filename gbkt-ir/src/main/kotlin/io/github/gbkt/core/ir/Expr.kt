/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// EXPRESSION HIERARCHY
// =============================================================================

/**
 * Non-sealed expression interface for the v2 IR.
 *
 * Expressions are pure values — they produce a result but have no side effects. Unsealed so that
 * external modules (gbkt-rpg, gbkt-exploration) can define their own expression types.
 *
 * All dispatch is performed via the visitor pattern — callers invoke [accept] and the subtype
 * routes to the correct [ExprVisitorI] method.
 *
 * Each subtype carries an optional [sourceLocation] for source map tracking. Defaults to null for
 * expressions constructed internally (e.g. during constant folding).
 */
interface Expr {
    val sourceLocation: SourceLocation?

    fun <T> accept(visitor: ExprVisitorI<T>): T
}

/** Integer literal constant. */
data class Literal(val value: Int, override val sourceLocation: SourceLocation? = null) : Expr {
    override fun <T> accept(visitor: ExprVisitorI<T>): T = visitor.visitLiteral(this)
}

/** String literal constant. */
data class StringLiteral(val value: String, override val sourceLocation: SourceLocation? = null) :
    Expr {
    override fun <T> accept(visitor: ExprVisitorI<T>): T = visitor.visitStringLiteral(this)
}

/** Reference to a named variable. */
data class VarRef(val name: String, override val sourceLocation: SourceLocation? = null) : Expr {
    override fun <T> accept(visitor: ExprVisitorI<T>): T = visitor.visitVarRef(this)
}

/** Binary operation combining two sub-expressions. */
data class BinaryExpr(
    val left: Expr,
    val op: BinaryOp,
    val right: Expr,
    override val sourceLocation: SourceLocation? = null,
) : Expr {
    override fun <T> accept(visitor: ExprVisitorI<T>): T = visitor.visitBinaryExpr(this)
}

/** Unary operation applied to a single sub-expression. */
data class UnaryExpr(
    val op: UnaryOp,
    val operand: Expr,
    override val sourceLocation: SourceLocation? = null,
) : Expr {
    override fun <T> accept(visitor: ExprVisitorI<T>): T = visitor.visitUnaryExpr(this)
}

/** Function call expression returning a value. */
data class CallExpr(
    val function: String,
    val args: List<Expr>,
    override val sourceLocation: SourceLocation? = null,
) : Expr {
    override fun <T> accept(visitor: ExprVisitorI<T>): T = visitor.visitCallExpr(this)
}

/** Conditional (ternary) expression: condition ? thenExpr : elseExpr. */
data class TernaryExpr(
    val condition: Expr,
    val thenExpr: Expr,
    val elseExpr: Expr,
    override val sourceLocation: SourceLocation? = null,
) : Expr {
    override fun <T> accept(visitor: ExprVisitorI<T>): T = visitor.visitTernaryExpr(this)
}

/** Array element read: array[index]. */
data class ArrayAccessExpr(
    val array: String,
    val index: Expr,
    override val sourceLocation: SourceLocation? = null,
) : Expr {
    override fun <T> accept(visitor: ExprVisitorI<T>): T = visitor.visitArrayAccessExpr(this)
}

/** Property access on an IR object: objectId.property (e.g. player.x). */
data class PropertyAccessExpr(
    val objectId: String,
    val property: String,
    override val sourceLocation: SourceLocation? = null,
) : Expr {
    override fun <T> accept(visitor: ExprVisitorI<T>): T = visitor.visitPropertyAccessExpr(this)
}

/** Explicit type cast: (targetType) inner. E.g. (UINT16)(_score). */
data class CastExpr(
    val targetType: VarType,
    val inner: Expr,
    override val sourceLocation: SourceLocation? = null,
) : Expr {
    override fun <T> accept(visitor: ExprVisitorI<T>): T = visitor.visitCast(this)
}

/**
 * Expression that returns the number of active slots in an actor pool.
 *
 * Emits `pool_<poolId>_active_count()` — a generated helper function that counts set bits in the
 * active bitmap. Usable in `runIf()` conditions and assignments.
 *
 * Delegates to [CallExpr] so no new [ExprVisitorI] method is required — all existing expression
 * visitors handle this transparently.
 *
 * @param poolId ID of the actor pool to query.
 */
data class PoolGetActiveCount(
    val poolId: String,
    override val sourceLocation: SourceLocation? = null,
) : Expr {
    override fun <T> accept(visitor: ExprVisitorI<T>): T =
        CallExpr("pool_${poolId}_active_count", emptyList(), sourceLocation).accept(visitor)
}
