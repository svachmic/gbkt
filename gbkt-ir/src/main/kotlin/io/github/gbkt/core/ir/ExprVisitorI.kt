/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// EXPR VISITOR INTERFACE
// =============================================================================

/**
 * Visitor interface for [Expr] dispatch.
 *
 * Provides one `visit*` method per [Expr] subtype (10 total). Implementations convert IR expression
 * nodes to a result of type [T].
 *
 * The `I` suffix distinguishes this interface from any backend `ExprVisitor` implementations.
 *
 * Usage:
 * ```kotlin
 * object MyExprVisitor : ExprVisitorI<String> {
 *     override fun visitLiteral(expr: Literal): String = expr.value.toString()
 *     // ...
 * }
 * val result = someExpr.accept(MyExprVisitor)
 * ```
 */
interface ExprVisitorI<T> {

    fun visitLiteral(expr: Literal): T

    fun visitStringLiteral(expr: StringLiteral): T

    fun visitVarRef(expr: VarRef): T

    fun visitBinaryExpr(expr: BinaryExpr): T

    fun visitUnaryExpr(expr: UnaryExpr): T

    fun visitCallExpr(expr: CallExpr): T

    fun visitTernaryExpr(expr: TernaryExpr): T

    fun visitArrayAccessExpr(expr: ArrayAccessExpr): T

    fun visitPropertyAccessExpr(expr: PropertyAccessExpr): T

    fun visitCast(expr: CastExpr): T
}
