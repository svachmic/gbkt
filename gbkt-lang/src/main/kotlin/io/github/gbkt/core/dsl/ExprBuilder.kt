/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("TooManyFunctions") // Expr DSL requires one function per operator/comparison type

package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.CastExpr
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.StringLiteral
import io.github.gbkt.core.ir.UnaryExpr
import io.github.gbkt.core.ir.UnaryOp
import io.github.gbkt.core.ir.VarRef
import io.github.gbkt.core.ir.VarType

// =============================================================================
// EXPRESSION FACTORY HELPERS
// =============================================================================

/** Creates a string literal expression. */
fun stringLiteral(value: String): Expr = StringLiteral(value)

// =============================================================================
// ARITHMETIC OPERATOR EXTENSIONS
// =============================================================================

operator fun Expr.plus(other: Expr): Expr = BinaryExpr(this, BinaryOp.ADD, other)

operator fun Expr.plus(other: Int): Expr = BinaryExpr(this, BinaryOp.ADD, Literal(other))

operator fun Expr.plus(other: AssignableVar): Expr = BinaryExpr(this, BinaryOp.ADD, other.toExpr())

operator fun Expr.plus(other: ActorPropertyRef): Expr =
    BinaryExpr(this, BinaryOp.ADD, other.toExpr())

operator fun Expr.minus(other: Expr): Expr = BinaryExpr(this, BinaryOp.SUB, other)

operator fun Expr.minus(other: Int): Expr = BinaryExpr(this, BinaryOp.SUB, Literal(other))

operator fun Expr.minus(other: AssignableVar): Expr = BinaryExpr(this, BinaryOp.SUB, other.toExpr())

operator fun Expr.minus(other: ActorPropertyRef): Expr =
    BinaryExpr(this, BinaryOp.SUB, other.toExpr())

operator fun Expr.times(other: Expr): Expr = BinaryExpr(this, BinaryOp.MUL, other)

operator fun Expr.times(other: Int): Expr = BinaryExpr(this, BinaryOp.MUL, Literal(other))

operator fun Expr.times(other: AssignableVar): Expr = BinaryExpr(this, BinaryOp.MUL, other.toExpr())

operator fun Expr.times(other: ActorPropertyRef): Expr =
    BinaryExpr(this, BinaryOp.MUL, other.toExpr())

operator fun Expr.div(other: Expr): Expr = BinaryExpr(this, BinaryOp.DIV, other)

operator fun Expr.div(other: Int): Expr = BinaryExpr(this, BinaryOp.DIV, Literal(other))

operator fun Expr.div(other: AssignableVar): Expr = BinaryExpr(this, BinaryOp.DIV, other.toExpr())

operator fun Expr.div(other: ActorPropertyRef): Expr =
    BinaryExpr(this, BinaryOp.DIV, other.toExpr())

operator fun Expr.rem(other: Expr): Expr = BinaryExpr(this, BinaryOp.MOD, other)

operator fun Expr.rem(other: Int): Expr = BinaryExpr(this, BinaryOp.MOD, Literal(other))

operator fun Expr.rem(other: AssignableVar): Expr = BinaryExpr(this, BinaryOp.MOD, other.toExpr())

operator fun Expr.rem(other: ActorPropertyRef): Expr =
    BinaryExpr(this, BinaryOp.MOD, other.toExpr())

operator fun Expr.unaryMinus(): Expr = UnaryExpr(UnaryOp.NEGATE, this)

// =============================================================================
// BITWISE OPERATOR EXTENSIONS
// =============================================================================

infix fun Expr.and(other: Expr): Expr = BinaryExpr(this, BinaryOp.AND, other)

infix fun Expr.or(other: Expr): Expr = BinaryExpr(this, BinaryOp.OR, other)

infix fun Expr.xor(other: Expr): Expr = BinaryExpr(this, BinaryOp.XOR, other)

infix fun Expr.shl(other: Expr): Expr = BinaryExpr(this, BinaryOp.SHL, other)

infix fun Expr.shl(other: Int): Expr = BinaryExpr(this, BinaryOp.SHL, Literal(other))

infix fun Expr.shr(other: Expr): Expr = BinaryExpr(this, BinaryOp.SHR, other)

infix fun Expr.shr(other: Int): Expr = BinaryExpr(this, BinaryOp.SHR, Literal(other))

fun Expr.inv(): Expr = UnaryExpr(UnaryOp.BITWISE_NOT, this)

// =============================================================================
// COMPARISON INFIX FUNCTIONS
// =============================================================================

infix fun Expr.isAbove(other: Expr): Expr = BinaryExpr(this, BinaryOp.GT, other)

infix fun Expr.isAbove(other: Int): Expr = BinaryExpr(this, BinaryOp.GT, Literal(other))

infix fun Expr.isAbove(other: AssignableVar): Expr = BinaryExpr(this, BinaryOp.GT, other.toExpr())

infix fun Expr.isAbove(other: ActorPropertyRef): Expr =
    BinaryExpr(this, BinaryOp.GT, other.toExpr())

infix fun Expr.isBelow(other: Expr): Expr = BinaryExpr(this, BinaryOp.LT, other)

infix fun Expr.isBelow(other: Int): Expr = BinaryExpr(this, BinaryOp.LT, Literal(other))

infix fun Expr.isBelow(other: AssignableVar): Expr = BinaryExpr(this, BinaryOp.LT, other.toExpr())

infix fun Expr.isBelow(other: ActorPropertyRef): Expr =
    BinaryExpr(this, BinaryOp.LT, other.toExpr())

infix fun Expr.isAtLeast(other: Expr): Expr = BinaryExpr(this, BinaryOp.GTE, other)

infix fun Expr.isAtLeast(other: Int): Expr = BinaryExpr(this, BinaryOp.GTE, Literal(other))

infix fun Expr.isAtLeast(other: AssignableVar): Expr =
    BinaryExpr(this, BinaryOp.GTE, other.toExpr())

infix fun Expr.isAtLeast(other: ActorPropertyRef): Expr =
    BinaryExpr(this, BinaryOp.GTE, other.toExpr())

infix fun Expr.isAtMost(other: Expr): Expr = BinaryExpr(this, BinaryOp.LTE, other)

infix fun Expr.isAtMost(other: Int): Expr = BinaryExpr(this, BinaryOp.LTE, Literal(other))

infix fun Expr.isAtMost(other: AssignableVar): Expr = BinaryExpr(this, BinaryOp.LTE, other.toExpr())

infix fun Expr.isAtMost(other: ActorPropertyRef): Expr =
    BinaryExpr(this, BinaryOp.LTE, other.toExpr())

infix fun Expr.isEqualTo(other: Expr): Expr = BinaryExpr(this, BinaryOp.EQ, other)

infix fun Expr.isEqualTo(other: Int): Expr = BinaryExpr(this, BinaryOp.EQ, Literal(other))

infix fun Expr.isEqualTo(other: AssignableVar): Expr = BinaryExpr(this, BinaryOp.EQ, other.toExpr())

infix fun Expr.isEqualTo(other: ActorPropertyRef): Expr =
    BinaryExpr(this, BinaryOp.EQ, other.toExpr())

infix fun Expr.isNotEqualTo(other: Expr): Expr = BinaryExpr(this, BinaryOp.NEQ, other)

infix fun Expr.isNotEqualTo(other: Int): Expr = BinaryExpr(this, BinaryOp.NEQ, Literal(other))

infix fun Expr.isNotEqualTo(other: AssignableVar): Expr =
    BinaryExpr(this, BinaryOp.NEQ, other.toExpr())

infix fun Expr.isNotEqualTo(other: ActorPropertyRef): Expr =
    BinaryExpr(this, BinaryOp.NEQ, other.toExpr())

// =============================================================================
// LOGICAL OPERATOR EXTENSIONS
// =============================================================================

infix fun Expr.logicalAnd(other: Expr): Expr = BinaryExpr(this, BinaryOp.LOGICAL_AND, other)

infix fun Expr.logicalOr(other: Expr): Expr = BinaryExpr(this, BinaryOp.LOGICAL_OR, other)

fun Expr.not(): Expr = UnaryExpr(UnaryOp.LOGICAL_NOT, this)

// =============================================================================
// INT LEFT-SIDE OPERATOR EXTENSIONS
// =============================================================================
//
// Enables `5 + score`, `160 - ball.x` etc. without requiring manual `literal()` wrapping.
// These complement the Expr right-side operators (Expr.plus(Int), etc.) defined above.
//
// NOTE: `&&` and `||` cannot be overloaded in Kotlin (short-circuit language construct).
// Use `logicalAnd` / `logicalOr` infix functions on Expr/AssignableVar instead.

operator fun Int.plus(other: Expr): Expr = BinaryExpr(Literal(this), BinaryOp.ADD, other)

operator fun Int.plus(other: AssignableVar): Expr =
    BinaryExpr(Literal(this), BinaryOp.ADD, other.toExpr())

operator fun Int.plus(other: ActorPropertyRef): Expr =
    BinaryExpr(Literal(this), BinaryOp.ADD, other.toExpr())

operator fun Int.minus(other: Expr): Expr = BinaryExpr(Literal(this), BinaryOp.SUB, other)

operator fun Int.minus(other: AssignableVar): Expr =
    BinaryExpr(Literal(this), BinaryOp.SUB, other.toExpr())

operator fun Int.minus(other: ActorPropertyRef): Expr =
    BinaryExpr(Literal(this), BinaryOp.SUB, other.toExpr())

operator fun Int.times(other: Expr): Expr = BinaryExpr(Literal(this), BinaryOp.MUL, other)

operator fun Int.times(other: AssignableVar): Expr =
    BinaryExpr(Literal(this), BinaryOp.MUL, other.toExpr())

operator fun Int.times(other: ActorPropertyRef): Expr =
    BinaryExpr(Literal(this), BinaryOp.MUL, other.toExpr())

operator fun Int.div(other: Expr): Expr = BinaryExpr(Literal(this), BinaryOp.DIV, other)

operator fun Int.div(other: AssignableVar): Expr =
    BinaryExpr(Literal(this), BinaryOp.DIV, other.toExpr())

operator fun Int.div(other: ActorPropertyRef): Expr =
    BinaryExpr(Literal(this), BinaryOp.DIV, other.toExpr())

operator fun Int.rem(other: Expr): Expr = BinaryExpr(Literal(this), BinaryOp.MOD, other)

operator fun Int.rem(other: AssignableVar): Expr =
    BinaryExpr(Literal(this), BinaryOp.MOD, other.toExpr())

operator fun Int.rem(other: ActorPropertyRef): Expr =
    BinaryExpr(Literal(this), BinaryOp.MOD, other.toExpr())

// =============================================================================
// TYPE CAST EXTENSION FUNCTIONS
// =============================================================================
//
// Explicit type casts to Game Boy hardware types. Emits `(UINT8)(expr)` etc. in C.
// Use when you need to narrow a wider integer (e.g. U16 → U8) or widen a signed value.

/** Cast [this] expression to UINT8 (0-255). Generates `(UINT8)(expr)` in C. */
fun Expr.toU8(): Expr = CastExpr(VarType.U8, this)

/** Cast [this] expression to UINT16 (0-65535). Generates `(UINT16)(expr)` in C. */
fun Expr.toU16(): Expr = CastExpr(VarType.U16, this)

/** Cast [this] expression to INT8 (-128-127). Generates `(INT8)(expr)` in C. */
fun Expr.toI8(): Expr = CastExpr(VarType.I8, this)

/** Cast [this] expression to INT16 (-32768-32767). Generates `(INT16)(expr)` in C. */
fun Expr.toI16(): Expr = CastExpr(VarType.I16, this)

/** Cast [this] variable to UINT8. Generates `(UINT8)(_name)` in C. */
fun AssignableVar.toU8(): Expr = toExpr().toU8()

/** Cast [this] variable to UINT16. Generates `(UINT16)(_name)` in C. */
fun AssignableVar.toU16(): Expr = toExpr().toU16()

/** Cast [this] variable to INT8. Generates `(INT8)(_name)` in C. */
fun AssignableVar.toI8(): Expr = toExpr().toI8()

/** Cast [this] variable to INT16. Generates `(INT16)(_name)` in C. */
fun AssignableVar.toI16(): Expr = toExpr().toI16()

/** Cast [this] actor property to UINT8. Generates `(UINT8)(_actorId_prop)` in C. */
fun ActorPropertyRef.toU8(): Expr = toExpr().toU8()

/** Cast [this] actor property to UINT16. Generates `(UINT16)(_actorId_prop)` in C. */
fun ActorPropertyRef.toU16(): Expr = toExpr().toU16()

/** Cast [this] actor property to INT8. Generates `(INT8)(_actorId_prop)` in C. */
fun ActorPropertyRef.toI8(): Expr = toExpr().toI8()

/** Cast [this] actor property to INT16. Generates `(INT16)(_actorId_prop)` in C. */
fun ActorPropertyRef.toI16(): Expr = toExpr().toI16()

// =============================================================================
// FIXED-POINT HELPERS
// =============================================================================

/**
 * Extracts the pixel coordinate from a 12.4 fixed-point variable.
 *
 * Equivalent to `posX shr 4` — lowers to [BinaryExpr]([VarRef], SHR, [Literal]([fractionalBits])).
 * Use with variables declared as `i16FixedVar` to hide the shift literal from call sites.
 *
 * @param fractionalBits Number of fractional bits; must match the declaration. Default 4.
 */
fun AssignableVar.toPixel(fractionalBits: Int = 4): Expr {
    val bits = this.fractionalBits ?: fractionalBits
    return BinaryExpr(VarRef(name), BinaryOp.SHR, Literal(bits))
}

/**
 * Decays this variable toward zero by [by] per frame.
 *
 * Emits two [IfOp] nodes:
 * - `if (v < 0) { v += by }` (decay from negative side)
 * - `if (v > 0) { v -= by }` (decay from positive side)
 *
 * Generated C is byte-identical to the hand-rolled two-`runIf` ladder. [by] defaults to 1, which
 * is the only value used in current example ports.
 *
 * Must be called inside a `ScriptBuilder` block (scene frame/enter/exit, runIf, etc.).
 * D-13: method-only canonical surface; no free-fn `damp()` alias.
 */
fun AssignableVar.easeToZero(by: Int = 1) {
    val sb =
        ScriptBuilderContext.current ?: error("easeToZero() called outside a ScriptBuilder block")
    sb.runIf(BinaryExpr(VarRef(name), BinaryOp.LT, Literal(0))) {
        assign(name, Literal(by), AssignOp.ADD)
    }
    sb.runIf(BinaryExpr(VarRef(name), BinaryOp.GT, Literal(0))) {
        assign(name, Literal(by), AssignOp.SUB)
    }
}
