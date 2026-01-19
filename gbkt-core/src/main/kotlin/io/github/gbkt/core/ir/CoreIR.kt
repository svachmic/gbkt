/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.SourceLocation

// =============================================================================
// CORE IR NODES - Base statements and expressions
// =============================================================================

/**
 * Base interface for all IR statements. Optionally tracks source location for sourcemap generation.
 */
sealed interface IRStatement {
    /**
     * The source location in Kotlin DSL where this statement originated. Used for generating
     * sourcemaps that link C code back to Kotlin.
     */
    val sourceLocation: SourceLocation?
        get() = null // Default implementation returns null
}

sealed interface IRExpression

// =============================================================================
// STATEMENTS
// =============================================================================

data class IRAssign(
    val target: String,
    val value: IRExpression,
    val op: AssignOp = AssignOp.SET,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

data class IRIf(
    val condition: IRExpression,
    val then: List<IRStatement>,
    val otherwise: List<IRStatement>? = null,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

data class IRWhen(
    val branches: List<WhenBranch>,
    val otherwise: List<IRStatement>? = null,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

data class IRWhile(
    val condition: IRExpression,
    val body: List<IRStatement>,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

data class IRFor(
    val counter: String,
    val range: IntRange,
    val body: List<IRStatement>,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

data class IRCall(
    val function: String,
    val args: List<IRExpression>,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

data class IRSceneChange(
    val sceneName: String,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

data class IRRaw(val code: String, override val sourceLocation: SourceLocation? = null) :
    IRStatement

/**
 * Array element assignment.
 *
 * Usage: array[index] set value
 *
 * Generates: array[index] = value;
 */
data class IRArrayAssign(
    val array: String,
    val index: IRExpression,
    val value: IRExpression,
    override val sourceLocation: SourceLocation? = null,
) : IRStatement

data class WhenBranch(val condition: IRExpression, val body: List<IRStatement>)

enum class AssignOp(val c: String) {
    SET("="),
    ADD("+="),
    SUB("-="),
    MUL("*="),
    AND("&="),
    OR("|="),
}

// =============================================================================
// EXPRESSIONS
// =============================================================================

data class IRLiteral(val value: Any) : IRExpression

data class IRVar(val name: String) : IRExpression

data class IRBinary(val left: IRExpression, val op: BinaryOp, val right: IRExpression) :
    IRExpression

data class IRUnary(val op: UnaryOp, val operand: IRExpression) : IRExpression

data class IRCallExpr(val function: String, val args: List<IRExpression>) : IRExpression

data class IRTernary(val cond: IRExpression, val then: IRExpression, val otherwise: IRExpression) :
    IRExpression

data class IRArrayAccess(val array: String, val index: IRExpression) : IRExpression

enum class BinaryOp(val c: String) {
    ADD("+"),
    SUB("-"),
    MUL("*"),
    DIV("/"),
    MOD("%"),
    AND("&"),
    OR("|"),
    XOR("^"),
    SHL("<<"),
    SHR(">>"),
    EQ("=="),
    NEQ("!="),
    LT("<"),
    LTE("<="),
    GT(">"),
    GTE(">="),
    LAND("&&"),
    LOR("||"),
}

enum class UnaryOp(val c: String) {
    NEG("-"),
    NOT("!"),
    BNOT("~"),
}
