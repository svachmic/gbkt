/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// EXPRESSION WRAPPER - Enables operator chaining
// =============================================================================

/**
 * Wraps an IR expression and provides operator overloads. This is what makes `playerX + 2` work
 * inside recording blocks.
 */
open class Expr(open val ir: IRExpression) {

    constructor(varName: String, varType: GBVar.VarType) : this(IRVar(varName))

    // Arithmetic
    operator fun plus(other: Expr) = Expr(IRBinary(ir, BinaryOp.ADD, other.ir))

    operator fun plus(other: Int) = Expr(IRBinary(ir, BinaryOp.ADD, IRLiteral(other)))

    operator fun minus(other: Expr) = Expr(IRBinary(ir, BinaryOp.SUB, other.ir))

    operator fun minus(other: Int) = Expr(IRBinary(ir, BinaryOp.SUB, IRLiteral(other)))

    operator fun times(other: Expr) = Expr(IRBinary(ir, BinaryOp.MUL, other.ir))

    operator fun times(other: Int) = Expr(IRBinary(ir, BinaryOp.MUL, IRLiteral(other)))

    operator fun div(other: Expr) = Expr(IRBinary(ir, BinaryOp.DIV, other.ir))

    operator fun div(other: Int) = Expr(IRBinary(ir, BinaryOp.DIV, IRLiteral(other)))

    infix fun rem(other: Expr) = Expr(IRBinary(ir, BinaryOp.MOD, other.ir))

    infix fun rem(other: Int) = Expr(IRBinary(ir, BinaryOp.MOD, IRLiteral(other)))

    // Bitwise
    infix fun and(other: Expr) = Expr(IRBinary(ir, BinaryOp.AND, other.ir))

    infix fun and(other: Int) = Expr(IRBinary(ir, BinaryOp.AND, IRLiteral(other)))

    infix fun or(other: Expr) = Expr(IRBinary(ir, BinaryOp.OR, other.ir))

    infix fun or(other: Int) = Expr(IRBinary(ir, BinaryOp.OR, IRLiteral(other)))

    infix fun xor(other: Expr) = Expr(IRBinary(ir, BinaryOp.XOR, other.ir))

    infix fun shl(bits: Int) = Expr(IRBinary(ir, BinaryOp.SHL, IRLiteral(bits)))

    infix fun shr(bits: Int) = Expr(IRBinary(ir, BinaryOp.SHR, IRLiteral(bits)))

    // =========================================================================
    // COMPARISON OPERATORS
    // Returns Condition for use in whenever/branch blocks
    // =========================================================================

    // Equality
    infix fun eq(other: Expr) = Condition(IRBinary(ir, BinaryOp.EQ, other.ir))

    infix fun eq(other: Int) = Condition(IRBinary(ir, BinaryOp.EQ, IRLiteral(other)))

    infix fun neq(other: Expr) = Condition(IRBinary(ir, BinaryOp.NEQ, other.ir))

    infix fun neq(other: Int) = Condition(IRBinary(ir, BinaryOp.NEQ, IRLiteral(other)))

    // Readable aliases for equality: score isEqualTo 0, health isNotEqualTo 0
    infix fun isEqualTo(other: Expr) = eq(other)

    infix fun isEqualTo(other: Int) = eq(other)

    infix fun isNotEqualTo(other: Expr) = neq(other)

    infix fun isNotEqualTo(other: Int) = neq(other)

    // Short form: whenever(score `is` 0) - backticks because `is` is keyword
    infix fun `is`(other: Int) = eq(other)

    infix fun `is`(other: Expr) = eq(other)

    infix fun isNot(other: Int) = neq(other)

    infix fun isNot(other: Expr) = neq(other)

    // Less than
    infix fun lt(other: Expr) = Condition(IRBinary(ir, BinaryOp.LT, other.ir))

    infix fun lt(other: Int) = Condition(IRBinary(ir, BinaryOp.LT, IRLiteral(other)))

    infix fun lte(other: Expr) = Condition(IRBinary(ir, BinaryOp.LTE, other.ir))

    infix fun lte(other: Int) = Condition(IRBinary(ir, BinaryOp.LTE, IRLiteral(other)))

    // Readable aliases: playerY isBelow groundY, health isAtMost 10
    infix fun isBelow(other: Expr) = lt(other)

    infix fun isBelow(other: Int) = lt(other)

    infix fun isAtMost(other: Expr) = lte(other)

    infix fun isAtMost(other: Int) = lte(other)

    infix fun isLessThan(other: Expr) = lt(other)

    infix fun isLessThan(other: Int) = lt(other)

    // Greater than
    infix fun gt(other: Expr) = Condition(IRBinary(ir, BinaryOp.GT, other.ir))

    infix fun gt(other: Int) = Condition(IRBinary(ir, BinaryOp.GT, IRLiteral(other)))

    infix fun gte(other: Expr) = Condition(IRBinary(ir, BinaryOp.GTE, other.ir))

    infix fun gte(other: Int) = Condition(IRBinary(ir, BinaryOp.GTE, IRLiteral(other)))

    // Readable aliases: playerY isAbove groundY, score isAtLeast 100
    infix fun isAbove(other: Expr) = gt(other)

    infix fun isAbove(other: Int) = gt(other)

    infix fun isAtLeast(other: Expr) = gte(other)

    infix fun isAtLeast(other: Int) = gte(other)

    infix fun isGreaterThan(other: Expr) = gt(other)

    infix fun isGreaterThan(other: Int) = gt(other)

    // Range check: whenever(playerX isBetween 10..150)
    infix fun isBetween(range: IntRange): Condition {
        return Condition(
            IRBinary(
                IRBinary(ir, BinaryOp.GTE, IRLiteral(range.first)),
                BinaryOp.LAND,
                IRBinary(ir, BinaryOp.LTE, IRLiteral(range.last))
            )
        )
    }

    // =========================================================================
    // CONDITION SHORTCUTS
    // Common comparison patterns as properties for cleaner code
    // =========================================================================

    /** True when value equals zero. Usage: `whenever(counter.isZero) { ... }` */
    val isZero: Condition
        get() = this isEqualTo 0

    /** True when value does not equal zero. Usage: `whenever(score.isNonZero) { ... }` */
    val isNonZero: Condition
        get() = this isNotEqualTo 0

    /** True when value is greater than zero. Usage: `whenever(health.isPositive) { ... }` */
    val isPositive: Condition
        get() = this isAbove 0

    /** True when value is less than zero. Usage: `whenever(velocity.isNegative) { ... }` */
    val isNegative: Condition
        get() = this isBelow 0

    /** Alias for isZero - treats 0 as false. Usage: `whenever(isJumping.isFalse) { ... }` */
    val isFalse: Condition
        get() = isZero

    /** Alias for isNonZero - treats non-zero as true. Usage: `whenever(hasKey.isTrue) { ... }` */
    val isTrue: Condition
        get() = isNonZero

    /**
     * True when value is evenly divisible by divisor. Usage: `whenever(score isDivisibleBy 100) {
     * awardBonus() }`
     */
    infix fun isDivisibleBy(divisor: Int): Condition = (this rem divisor) isEqualTo 0

    // =========================================================================
    // UNARY OPERATORS
    // =========================================================================

    // Unary
    operator fun unaryMinus() = Expr(IRUnary(UnaryOp.NEG, ir))

    fun inv() = Expr(IRUnary(UnaryOp.BNOT, ir))

    // Coercion (generates clamp code)
    fun coerceIn(min: Int, max: Int): Expr {
        // Generates: (val < min) ? min : ((val > max) ? max : val)
        return Expr(
            IRTernary(
                IRBinary(ir, BinaryOp.LT, IRLiteral(min)),
                IRLiteral(min),
                IRTernary(IRBinary(ir, BinaryOp.GT, IRLiteral(max)), IRLiteral(max), ir)
            )
        )
    }

    fun coerceIn(range: IntRange) = coerceIn(range.first, range.last)
}

// =============================================================================
// INT EXTENSIONS - Enables reverse operations (10 - score, 2 * speed, etc.)
// =============================================================================

operator fun Int.plus(other: Expr) = Expr(IRBinary(IRLiteral(this), BinaryOp.ADD, other.ir))

operator fun Int.minus(other: Expr) = Expr(IRBinary(IRLiteral(this), BinaryOp.SUB, other.ir))

operator fun Int.times(other: Expr) = Expr(IRBinary(IRLiteral(this), BinaryOp.MUL, other.ir))

operator fun Int.div(other: Expr) = Expr(IRBinary(IRLiteral(this), BinaryOp.DIV, other.ir))

operator fun Int.rem(other: Expr) = Expr(IRBinary(IRLiteral(this), BinaryOp.MOD, other.ir))

// =============================================================================
// CONDITION - Boolean expressions for control flow
// =============================================================================

class Condition(val ir: IRExpression) {
    infix fun and(other: Condition) = Condition(IRBinary(ir, BinaryOp.LAND, other.ir))

    infix fun or(other: Condition) = Condition(IRBinary(ir, BinaryOp.LOR, other.ir))

    operator fun not() = Condition(IRUnary(UnaryOp.NOT, ir))

    // =========================================================================
    // TERNARY EXPRESSION SUPPORT
    // Enables: condition.then(trueVal, falseVal) or (condition then x otherwise y)
    // =========================================================================

    /**
     * Create a ternary expression: condition ? thenValue : elseValue
     *
     * Usage:
     * ```kotlin
     * damage set (isCritical.then(20, 10))
     * speed set (isRunning.then(fastSpeed, slowSpeed))
     * ```
     */
    fun then(thenValue: Int, elseValue: Int): Expr =
        Expr(IRTernary(ir, IRLiteral(thenValue), IRLiteral(elseValue)))

    fun then(thenValue: Expr, elseValue: Expr): Expr =
        Expr(IRTernary(ir, thenValue.ir, elseValue.ir))

    fun then(thenValue: Int, elseValue: Expr): Expr =
        Expr(IRTernary(ir, IRLiteral(thenValue), elseValue.ir))

    fun then(thenValue: Expr, elseValue: Int): Expr =
        Expr(IRTernary(ir, thenValue.ir, IRLiteral(elseValue)))

    /**
     * Infix syntax for ternary: (condition then trueVal) otherwise falseVal
     *
     * Usage:
     * ```kotlin
     * damage set ((isCritical) then 20 otherwise 10)
     * ```
     */
    infix fun then(thenValue: Int) = PartialTernary(ir, IRLiteral(thenValue))

    infix fun then(thenValue: Expr) = PartialTernary(ir, thenValue.ir)
}

/**
 * Intermediate class for infix ternary syntax. Created by `condition then value`, completed by
 * `otherwise value`.
 */
class PartialTernary(private val cond: IRExpression, private val thenVal: IRExpression) {
    infix fun otherwise(elseValue: Int): Expr = Expr(IRTernary(cond, thenVal, IRLiteral(elseValue)))

    infix fun otherwise(elseValue: Expr): Expr = Expr(IRTernary(cond, thenVal, elseValue.ir))
}
