/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CArrayAccess
import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CCast
import io.github.gbkt.backend.gbdk.codegen.ast.CExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CI16
import io.github.gbkt.backend.gbdk.codegen.ast.CI8
import io.github.gbkt.backend.gbdk.codegen.ast.CIntLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CStringLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CTernary
import io.github.gbkt.backend.gbdk.codegen.ast.CU16
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CUnaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.ArrayAccessExpr
import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.CallExpr
import io.github.gbkt.core.ir.CastExpr
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.ExprVisitorI
import io.github.gbkt.core.ir.HitboxDef
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.PropertyAccessExpr
import io.github.gbkt.core.ir.StringLiteral
import io.github.gbkt.core.ir.TernaryExpr
import io.github.gbkt.core.ir.UnaryExpr
import io.github.gbkt.core.ir.UnaryOp
import io.github.gbkt.core.ir.VarRef
import io.github.gbkt.core.ir.VarType
import io.github.gbkt.core.ir.VariableDef

// =============================================================================
// EXPR VISITOR
// Translates IR v2 Expr nodes into typed C AST CExpr nodes.
// No string output — all results are typed CExpr subtypes.
// =============================================================================

/**
 * Visitor that converts IR v2 [Expr] nodes to typed C AST [CExpr] nodes.
 *
 * Implements [ExprVisitorI]<[CExpr]> so that each Expr subtype dispatches via [Expr.accept] rather
 * than a `when` expression. This allows external modules to define their own [Expr] subtypes
 * without modifying this visitor.
 *
 * Variable names are sanitized by replacing dots with underscores and prepending a leading
 * underscore per GBDK naming convention (e.g. `ball.x` → `_ball_x`).
 *
 * All 9 [Expr] subtypes are handled via named [ExprVisitorI] methods. No string concatenation
 * occurs — outputs are typed [CExpr] subtypes only.
 *
 * @param actors List of [ActorIR] instances available in the current game. Used to resolve
 *   actor-specific operations such as inline AABB collision detection for `collides()` calls.
 *   Defaults to empty list for backward-compatible usage in tests and non-actor contexts.
 * @param variables List of [VariableDef] instances declared on the game. Used to look up the
 *   signedness of a [VarRef] LHS so that the DSL-authored signed-comparison RHS literal can be
 *   routed through [CIntLiteral] (Phase 9 Plan 04 — Bug A fix; bucket-b extension of the Phase 07.9
 *   Literal Emission Convention to cover the DSL-authored path, not just hardcoded visitor sites).
 *   Defaults to empty list — when no variable type info is available, the visitor falls back to the
 *   pre-fix [CLiteral] emission (unchanged behavior).
 */
class ExprVisitor(
    private val actors: List<ActorIR> = emptyList(),
    variables: List<VariableDef> = emptyList(),
) : ExprVisitorI<CExpr> {

    /** Name → [VarType] lookup table built from [variables] for O(1) signedness checks. */
    private val variableTypes: Map<String, VarType> = variables.associate { it.name to it.type }

    /**
     * Convert an IR [Expr] to a typed [CExpr] node via visitor dispatch.
     *
     * Delegates to [Expr.accept] which routes to the appropriate `visit*` method on this instance.
     */
    fun visit(expr: Expr): CExpr = expr.accept(this)

    // -------------------------------------------------------------------------
    // ExprVisitorI implementation
    // -------------------------------------------------------------------------

    override fun visitLiteral(expr: Literal): CExpr = CLiteral(expr.value)

    override fun visitStringLiteral(expr: StringLiteral): CExpr = CStringLiteral(expr.value)

    override fun visitVarRef(expr: VarRef): CExpr = CVar(sanitizeVarName(expr.name))

    /**
     * Lowers a [BinaryExpr] to a [CBinaryExpr].
     *
     * Phase 9 Plan 04 (Bug A) extension: when the operator is a comparison (`<`, `>`, `<=`, `>=`,
     * `==`, `!=`), the LHS is a [VarRef] to a known signed-typed variable (`VarType.I8` or
     * `VarType.I16`), and the RHS is a positive [Literal], the RHS lowers to [CIntLiteral]
     * (signed-safe, bare integer) instead of the default [CLiteral] (emits `Nu` suffix). This
     * extends the Phase 07.9 Literal Emission Convention from hardcoded visitor sites (bucket-a) to
     * the DSL-authored path (bucket-b) — `whenever(spdY isAbove 64)` now emits `_spdY > 64` instead
     * of `_spdY > 64u`, eliminating SDCC warning 94 (comparison always false). The existing
     * [CLiteral] emission is preserved for unsigned-context literals (assignment RHS, arithmetic),
     * per the additive split established by Phase 07.9 Option C.
     */
    override fun visitBinaryExpr(expr: BinaryExpr): CExpr {
        val rhs =
            if (isSignedComparisonRhs(expr)) CIntLiteral((expr.right as Literal).value)
            else visit(expr.right)
        return CBinaryExpr(visit(expr.left), binaryOpToC(expr.op), rhs)
    }

    /**
     * Predicate: does the RHS [Literal] of [expr] sit in a signed-comparison context whose LHS is a
     * known signed variable? Used by [visitBinaryExpr] to decide between [CIntLiteral] (signed RHS)
     * and the default [CLiteral] (unsigned RHS). All three conjuncts must hold; otherwise the
     * visitor falls back to pre-fix [CLiteral] emission.
     */
    private fun isSignedComparisonRhs(expr: BinaryExpr): Boolean {
        if (!isComparisonOp(expr.op)) return false
        if (expr.right !is Literal) return false
        val lhs = expr.left as? VarRef ?: return false
        val lhsType = variableTypes[lhs.name] ?: return false
        return lhsType == VarType.I8 || lhsType == VarType.I16
    }

    /** True iff [op] is one of the six C comparison operators. */
    private fun isComparisonOp(op: BinaryOp): Boolean =
        op == BinaryOp.LT ||
            op == BinaryOp.GT ||
            op == BinaryOp.LTE ||
            op == BinaryOp.GTE ||
            op == BinaryOp.EQ ||
            op == BinaryOp.NEQ

    override fun visitUnaryExpr(expr: UnaryExpr): CExpr =
        CUnaryExpr(unaryOpToC(expr.op), visit(expr.operand))

    /**
     * Handles all [CallExpr] nodes, with special-casing for `"collides"`.
     *
     * For `collides(actorA, actorB)` (2-arg CallExpr with VarRef args), emits an inline AABB
     * overlap test using the actors' position globals and hitbox dimensions from [ActorIR]:
     * ```c
     * (_ball_x < _paddle_x + 4) && (_ball_x + 4 > _paddle_x) &&
     * (_ball_y < _paddle_y + 16) && (_ball_y + 4 > _paddle_y)
     * ```
     *
     * All other call expressions delegate to [CCall] as before.
     */
    override fun visitCallExpr(expr: CallExpr): CExpr {
        if (expr.function == "collides" && expr.args.size == 2) {
            val aId =
                (expr.args[0] as? VarRef)?.name
                    ?: error("collides() first arg must be VarRef, got ${expr.args[0]}")
            val bId =
                (expr.args[1] as? VarRef)?.name
                    ?: error("collides() second arg must be VarRef, got ${expr.args[1]}")
            val actorA =
                actors.find { it.id == aId }
                    ?: error("collides(): unknown actor '$aId' — actor not registered in game")
            val actorB =
                actors.find { it.id == bId }
                    ?: error("collides(): unknown actor '$bId' — actor not registered in game")
            val hbA =
                actorA.sprite?.hitbox
                    ?: error(
                        "collides(): actor '$aId' has no hitbox — add hitbox() to sprite { } block"
                    )
            val hbB =
                actorB.sprite?.hitbox
                    ?: error(
                        "collides(): actor '$bId' has no hitbox — add hitbox() to sprite { } block"
                    )
            return buildAABBExpr(aId, bId, hbA, hbB)
        }
        return CCall(expr.function, expr.args.map { visit(it) })
    }

    override fun visitTernaryExpr(expr: TernaryExpr): CExpr =
        CTernary(visit(expr.condition), visit(expr.thenExpr), visit(expr.elseExpr))

    override fun visitArrayAccessExpr(expr: ArrayAccessExpr): CExpr =
        CArrayAccess(CVar(sanitizeVarName(expr.array)), visit(expr.index))

    /**
     * Handles actor property access expressions.
     *
     * For built-in properties (`x`, `y`, `visible`), emits `_${objectId}_${property}` using the
     * standard GBDK naming convention. For custom actor properties (anything not in the built-in
     * set), the same format applies — custom props are registered as prefixed global variables
     * named `_${actorId}_${propName}` by the ActorPropDelegate.
     *
     * When a [PoolCodegenContext] is active (set by [ScriptOpVisitor.activePoolContext]) and the
     * [PropertyAccessExpr.objectId] matches the pool's [PoolCodegenContext.templateActorId], the
     * access is redirected to the per-instance array: `_pool_<poolId>_<property>[<slotVarName>]`.
     *
     * The distinction is:
     * - Built-in: `ball.x` → `_ball_x` (hardware position variable)
     * - Custom: `ball.dx` → `_ball_dx` (user-registered global variable via i8Prop/u8Prop)
     * - Pool template (context active): `bullet.x` → `_pool_bulletPool_x[_bi]`
     *
     * All non-pool cases map to the same naming format and are handled transparently.
     */
    override fun visitPropertyAccessExpr(expr: PropertyAccessExpr): CExpr {
        val ctx = ScriptOpVisitor.activePoolContext.get()
        if (ctx != null && expr.objectId == ctx.templateActorId) {
            val arrayName = "_pool_${ctx.poolId}_${expr.property}"
            return CArrayAccess(CVar(arrayName), CVar(ctx.slotVarName))
        }
        return CVar(sanitizeVarName("${expr.objectId}.${expr.property}"))
    }

    /**
     * Handles explicit type cast expressions.
     *
     * Maps [VarType] to the corresponding [CType] and emits `(TYPE)inner`:
     * - VarType.U8 → `(UINT8)(inner)`
     * - VarType.U16 → `(UINT16)(inner)`
     * - VarType.I8 → `(INT8)(inner)`
     * - VarType.I16 → `(INT16)(inner)`
     */
    override fun visitCast(expr: CastExpr): CExpr {
        val cType =
            when (expr.targetType) {
                VarType.U8 -> CU8
                VarType.U16 -> CU16
                VarType.I8 -> CI8
                VarType.I16 -> CI16
            }
        return CCast(cType, visit(expr.inner))
    }

    // -------------------------------------------------------------------------
    // AABB collision helper
    // -------------------------------------------------------------------------

    /**
     * Build an inline AABB (Axis-Aligned Bounding Box) overlap expression.
     *
     * The AABB test for two rectangles a and b is:
     * ```
     * a.x < b.x + b.w  AND  a.x + a.w > b.x  AND  a.y < b.y + b.h  AND  a.y + a.h > b.y
     * ```
     *
     * Position globals follow the GBDK naming convention: `_${actorId}_x`, `_${actorId}_y`. Hitbox
     * dimensions are compile-time integer literals from [ActorIR.sprite.hitbox].
     *
     * The four conditions are chained with `&&` binary expressions. No grouping (parentheses) node
     * is needed because `&&` has lower precedence than comparison operators in C.
     */
    private fun buildAABBExpr(aId: String, bId: String, hbA: HitboxDef, hbB: HitboxDef): CExpr {
        val axVar = CVar("_${aId}_x")
        val ayVar = CVar("_${aId}_y")
        val bxVar = CVar("_${bId}_x")
        val byVar = CVar("_${bId}_y")

        // ax < bx + bw
        val xOverlapLeft = CBinaryExpr(axVar, "<", CBinaryExpr(bxVar, "+", CLiteral(hbB.width)))
        // ax + aw > bx
        val xOverlapRight = CBinaryExpr(CBinaryExpr(axVar, "+", CLiteral(hbA.width)), ">", bxVar)
        // ay < by + bh
        val yOverlapTop = CBinaryExpr(ayVar, "<", CBinaryExpr(byVar, "+", CLiteral(hbB.height)))
        // ay + ah > by
        val yOverlapBottom = CBinaryExpr(CBinaryExpr(ayVar, "+", CLiteral(hbA.height)), ">", byVar)

        // Chain: (xLeft && xRight) && (yTop && yBottom)
        val xOverlap = CBinaryExpr(xOverlapLeft, "&&", xOverlapRight)
        val yOverlap = CBinaryExpr(yOverlapTop, "&&", yOverlapBottom)
        return CBinaryExpr(xOverlap, "&&", yOverlap)
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Sanitize an IR variable name for GBDK C output.
     * - Replaces dots with underscores (e.g. `ball.x` → `ball_x`)
     * - Prepends a leading underscore per GBDK naming convention for local variables
     * - GBDK bitmask constants (names starting with `J_`) are passed through unchanged
     *
     * Examples:
     * - `score` → `_score`
     * - `ball.x` → `_ball_x`
     * - `J_START` → `J_START` (GBDK constant — no underscore prefix)
     * - `J_UP` → `J_UP` (GBDK constant — no underscore prefix)
     */
    internal fun sanitizeVarName(name: String): String {
        val sanitized = name.replace('.', '_')
        // GBDK joypad bitmask constants (J_UP, J_DOWN, J_LEFT, J_RIGHT, J_A, J_B, J_START,
        // J_SELECT) must not receive the underscore prefix — they are macro constants, not
        // variables.
        return when {
            sanitized.startsWith("J_") -> sanitized
            sanitized.startsWith('_') -> sanitized
            else -> "_$sanitized"
        }
    }

    /**
     * Map a [BinaryOp] to its C operator string.
     *
     * Covers all 18 [BinaryOp] enum values. The exhaustive [when] ensures new enum members cause a
     * compile error if not handled.
     */
    private fun binaryOpToC(op: BinaryOp): String =
        when (op) {
            BinaryOp.ADD -> "+"
            BinaryOp.SUB -> "-"
            BinaryOp.MUL -> "*"
            BinaryOp.DIV -> "/"
            BinaryOp.MOD -> "%"
            BinaryOp.AND -> "&"
            BinaryOp.OR -> "|"
            BinaryOp.XOR -> "^"
            BinaryOp.SHL -> "<<"
            BinaryOp.SHR -> ">>"
            BinaryOp.EQ -> "=="
            BinaryOp.NEQ -> "!="
            BinaryOp.LT -> "<"
            BinaryOp.LTE -> "<="
            BinaryOp.GT -> ">"
            BinaryOp.GTE -> ">="
            BinaryOp.LOGICAL_AND -> "&&"
            BinaryOp.LOGICAL_OR -> "||"
        }

    /** Map a [UnaryOp] to its C operator string. */
    private fun unaryOpToC(op: UnaryOp): String =
        when (op) {
            UnaryOp.NEGATE -> "-"
            UnaryOp.BITWISE_NOT -> "~"
            UnaryOp.LOGICAL_NOT -> "!"
        }

    // -------------------------------------------------------------------------
    // Companion: backward-compatible static access for existing call sites
    // -------------------------------------------------------------------------

    companion object {
        /**
         * Sanitize a variable name using the default (no-actor) visitor rules.
         *
         * Provided for backward compatibility with [ScriptOpVisitor] and [ActorVisitor] which call
         * `ExprVisitor.sanitizeVarName(name)` without constructing a class instance.
         */
        internal fun sanitizeVarName(name: String): String = ExprVisitor().sanitizeVarName(name)

        /**
         * Visit an expression using the default (no-actor) visitor.
         *
         * Provided for backward compatibility with [ScriptOpVisitor] which calls
         * `ExprVisitor.visit(expr)` without constructing a class instance. For collision-aware
         * codegen, construct `ExprVisitor(actors)` explicitly.
         */
        fun visit(expr: Expr): CExpr = ExprVisitor().visit(expr)
    }
}
