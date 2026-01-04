/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.test

import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.IRArrayAccess
import io.github.gbkt.core.ir.IRArrayAssign
import io.github.gbkt.core.ir.IRAssign
import io.github.gbkt.core.ir.IRBinary
import io.github.gbkt.core.ir.IRCall
import io.github.gbkt.core.ir.IRCallExpr
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRFor
import io.github.gbkt.core.ir.IRIf
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRTernary
import io.github.gbkt.core.ir.IRUnary
import io.github.gbkt.core.ir.IRVar
import io.github.gbkt.core.ir.IRWhen
import io.github.gbkt.core.ir.IRWhile
import io.github.gbkt.core.ir.UnaryOp
import io.github.gbkt.core.ir.i8
import io.github.gbkt.core.ir.u16
import io.github.gbkt.core.ir.u8

/**
 * Lightweight IR executor for unit tests.
 *
 * A simplified version of [SimulationContext] that handles core IR nodes without needing a full
 * Game object. Perfect for testing isolated logic.
 *
 * Supported operations:
 * - Assignments (set, add, sub, mul, and, or)
 * - Conditionals (if/else, when)
 * - Loops (while, for)
 * - All arithmetic/comparison expressions
 * - Arrays
 *
 * Not supported (game-specific):
 * - Scene changes
 * - Sprites/animations
 * - Pools
 * - Camera/transitions
 * - Dialogs/menus
 * - Save system
 */
class InlineExecutor(
    private val variables: MutableMap<String, SimValue>,
    private val variableDefinitions: Map<String, Any>
) {
    init {
        // Initialize variables from definitions if not already set
        for ((name, value) in variableDefinitions) {
            if (name !in variables) {
                variables[name] = toSimValue(value)
            }
        }
    }

    private fun toSimValue(value: Any): SimValue =
        when (value) {
            is u8 -> SimValue.of(value.raw)
            is u16 -> SimValue.of(value.raw)
            is i8 -> SimValue.of(value.raw)
            is Int -> SimValue.of(value)
            is Long -> SimValue.of(value)
            is Byte -> SimValue(value.toLong())
            is Short -> SimValue(value.toLong())
            is Boolean -> SimValue.of(value)
            is SimValue -> value
            else -> SimValue.ZERO
        }

    /** Execute an IR statement. */
    fun executeStatement(stmt: IRStatement) {
        when (stmt) {
            is IRAssign -> executeAssign(stmt)
            is IRArrayAssign -> executeArrayAssign(stmt)
            is IRIf -> executeIf(stmt)
            is IRWhen -> executeWhen(stmt)
            is IRWhile -> executeWhile(stmt)
            is IRFor -> executeFor(stmt)
            is IRCall -> {
                // Check for unsupported operations that would silently do nothing
                val unsupportedPrefixes =
                    listOf("pool_", "scene_", "camera_", "dialog_", "menu_", "save_")
                if (unsupportedPrefixes.any { stmt.function.startsWith(it) }) {
                    throw UnsupportedOperationException(
                        "InlineExecutor doesn't support '${stmt.function}'. " +
                            "Use SimulationContext for full game simulation."
                    )
                }
                // Other function calls are generally side-effect-free
            }
            else -> {
                // Other IR types not supported in lightweight executor
            }
        }
    }

    private fun executeAssign(stmt: IRAssign) {
        val current = variables[stmt.target] ?: SimValue.ZERO
        val newValue = evaluateExpr(stmt.value)

        val result =
            when (stmt.op) {
                AssignOp.SET -> newValue
                AssignOp.ADD -> current + newValue
                AssignOp.SUB -> current - newValue
                AssignOp.MUL -> current * newValue
                AssignOp.AND -> current and newValue
                AssignOp.OR -> current or newValue
            }

        variables[stmt.target] = result
    }

    private fun executeArrayAssign(stmt: IRArrayAssign) {
        val index = evaluateExpr(stmt.index).toInt()
        val value = evaluateExpr(stmt.value)
        val varName = "${stmt.array}_$index"
        variables[varName] = value
    }

    private fun executeIf(stmt: IRIf) {
        val conditionResult = evaluateExpr(stmt.condition)
        if (conditionResult.isTrue) {
            stmt.then.forEach { executeStatement(it) }
        } else if (stmt.otherwise != null) {
            stmt.otherwise.forEach { executeStatement(it) }
        }
    }

    private fun executeWhen(stmt: IRWhen) {
        for (branch in stmt.branches) {
            if (evaluateExpr(branch.condition).isTrue) {
                branch.body.forEach { executeStatement(it) }
                return
            }
        }
        stmt.otherwise?.forEach { executeStatement(it) }
    }

    private fun executeWhile(stmt: IRWhile) {
        var iterations = 0
        val maxIterations = 10000
        while (evaluateExpr(stmt.condition).isTrue && iterations < maxIterations) {
            stmt.body.forEach { executeStatement(it) }
            iterations++
        }
        if (iterations >= maxIterations) {
            error("While loop exceeded $maxIterations iterations - possible infinite loop")
        }
    }

    private fun executeFor(stmt: IRFor) {
        for (i in stmt.range) {
            variables[stmt.counter] = SimValue.of(i)
            stmt.body.forEach { executeStatement(it) }
        }
    }

    /** Evaluate an IR expression and return its value. */
    fun evaluateExpr(expr: IRExpression): SimValue =
        when (expr) {
            is IRLiteral -> SimValue.from(expr.value)
            is IRVar -> variables[expr.name] ?: SimValue.ZERO
            is IRBinary -> evaluateBinary(expr)
            is IRUnary -> evaluateUnary(expr)
            is IRTernary -> evaluateTernary(expr)
            is IRCallExpr -> SimValue.ZERO
            is IRArrayAccess -> evaluateArrayAccess(expr)
            else -> SimValue.ZERO
        }

    private fun evaluateBinary(expr: IRBinary): SimValue {
        val left = evaluateExpr(expr.left)
        val right = evaluateExpr(expr.right)

        return when (expr.op) {
            BinaryOp.ADD -> left + right
            BinaryOp.SUB -> left - right
            BinaryOp.MUL -> left * right
            BinaryOp.DIV -> left / right
            BinaryOp.MOD -> left % right
            BinaryOp.AND -> left and right
            BinaryOp.OR -> left or right
            BinaryOp.XOR -> left xor right
            BinaryOp.SHL -> left shl right
            BinaryOp.SHR -> left shr right
            BinaryOp.EQ -> left eq right
            BinaryOp.NEQ -> left neq right
            BinaryOp.LT -> left lt right
            BinaryOp.LTE -> left lte right
            BinaryOp.GT -> left gt right
            BinaryOp.GTE -> left gte right
            BinaryOp.LAND -> left land right
            BinaryOp.LOR -> left lor right
        }
    }

    private fun evaluateUnary(expr: IRUnary): SimValue {
        val operand = evaluateExpr(expr.operand)
        return when (expr.op) {
            UnaryOp.NEG -> -operand
            UnaryOp.NOT -> operand.lnot()
            UnaryOp.BNOT -> operand.inv()
        }
    }

    private fun evaluateTernary(expr: IRTernary): SimValue =
        if (evaluateExpr(expr.cond).isTrue) {
            evaluateExpr(expr.then)
        } else {
            evaluateExpr(expr.otherwise)
        }

    private fun evaluateArrayAccess(expr: IRArrayAccess): SimValue {
        val index = evaluateExpr(expr.index).toInt()
        val varName = "${expr.array}_$index"
        return variables[varName] ?: SimValue.ZERO
    }

    /** Get a variable's value. */
    fun getVariable(name: String): SimValue = variables[name] ?: SimValue.ZERO

    /** Set a variable's value. */
    fun setVariable(name: String, value: Int) {
        variables[name] = SimValue.of(value)
    }
}
