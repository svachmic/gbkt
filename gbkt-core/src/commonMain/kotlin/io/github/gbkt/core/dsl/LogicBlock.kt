/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.deepCopy

/**
 * A reusable block of game logic that generates IR.
 *
 * Logic blocks are "recorded" at definition time and "expanded" at each call site. This allows
 * extracting common patterns while maintaining proper IR generation.
 *
 * ## Basic Usage
 *
 * ```kotlin
 * // Define a reusable logic block
 * val moveRight = logicBlock {
 *     playerX += 2
 * }
 *
 * // Use in scenes - expands to the recorded IR
 * every.frame {
 *     whenever(dpad.right) { moveRight() }
 * }
 * ```
 *
 * ## With Parameters
 *
 * ```kotlin
 * // Define a parameterized logic block
 * val addScore = logicBlock<Expr>("amount") { amount ->
 *     score += amount
 * }
 *
 * // Use with different values
 * every.frame {
 *     whenever(coinCollected) { addScore(10.expr) }
 *     whenever(gemCollected) { addScore(50.expr) }
 * }
 * ```
 *
 * ## Important Notes
 * - Logic blocks are recorded once at definition time
 * - Each call expands a fresh copy of the IR (avoiding shared state issues)
 * - Variables used in logic blocks must be in scope at definition time
 * - Logic blocks can be nested (one logic block can call another)
 *
 * @property name A name for debugging purposes
 * @property statements The recorded IR statements
 */
class LogicBlock internal constructor(val name: String, private val statements: List<IRStatement>) {
    /**
     * Expand this logic block into the current recording context.
     *
     * Creates a deep copy of all statements and emits them to the current recorder. This must be
     * called within a recording context (e.g., inside `every.frame { }`, `enter { }`, `whenever {
     * }`, etc.).
     *
     * @throws IllegalStateException if called outside a recording context
     */
    operator fun invoke() {
        val recorder =
            RecordingContext.current
                ?: error(
                    "LogicBlock '$name' must be called within a recording context " +
                        "(e.g., every.frame { ... }, enter { ... }, whenever { ... })"
                )
        // Deep copy each statement to avoid sharing source locations and enable
        // multiple expansions of the same logic block
        statements.forEach { stmt -> recorder.emit(stmt.deepCopy()) }
    }

    /**
     * Include this logic block in another recording context.
     *
     * Alternative syntax that makes the expansion more explicit:
     * ```kotlin
     * every.frame {
     *     include(applyGravity)
     *     // ... more logic
     * }
     * ```
     */
    fun expand() = invoke()

    /** Get the number of statements in this logic block. */
    val size: Int
        get() = statements.size

    /** Check if this logic block is empty. */
    val isEmpty: Boolean
        get() = statements.isEmpty()

    override fun toString(): String = "LogicBlock($name, ${statements.size} statements)"
}

/**
 * A parameterized logic block that accepts a single expression parameter.
 *
 * Use this when you need to pass a value to your logic block:
 * ```kotlin
 * val addScore = logicBlock<Expr>("amount") { amount ->
 *     score += amount
 * }
 *
 * every.frame {
 *     whenever(coinCollected) { addScore(10.expr) }
 * }
 * ```
 *
 * @param T The expression type for the parameter
 * @property name A name for debugging purposes
 * @property parameterName The name of the placeholder variable
 * @property statements The recorded IR statements containing the placeholder
 */
class ParameterizedLogicBlock<T : IRExpression>
@PublishedApi
internal constructor(
    val name: String,
    @PublishedApi internal val parameterName: String,
    @PublishedApi internal val statements: List<IRStatement>
) {
    /**
     * Expand this logic block with the given parameter value.
     *
     * The parameter placeholder is substituted with the actual expression in each statement.
     *
     * @param value The expression to substitute for the parameter
     * @throws IllegalStateException if called outside a recording context
     */
    operator fun invoke(value: T) {
        val recorder =
            RecordingContext.current
                ?: error(
                    "ParameterizedLogicBlock '$name' must be called within a recording context " +
                        "(e.g., every.frame { ... }, enter { ... }, whenever { ... })"
                )
        val substitutions = mapOf(parameterName to value)
        statements.forEach { stmt -> recorder.emit(stmt.deepCopy(substitutions)) }
    }

    /** Get the number of statements in this logic block. */
    val size: Int
        get() = statements.size

    override fun toString(): String =
        "ParameterizedLogicBlock($name, param=$parameterName, ${statements.size} statements)"
}

/** A logic block that accepts two expression parameters. */
class ParameterizedLogicBlock2<T1 : IRExpression, T2 : IRExpression>
@PublishedApi
internal constructor(
    val name: String,
    @PublishedApi internal val param1Name: String,
    @PublishedApi internal val param2Name: String,
    @PublishedApi internal val statements: List<IRStatement>
) {
    operator fun invoke(value1: T1, value2: T2) {
        val recorder =
            RecordingContext.current
                ?: error(
                    "ParameterizedLogicBlock2 '$name' must be called within a recording context"
                )
        val substitutions = mapOf(param1Name to value1, param2Name to value2)
        statements.forEach { stmt -> recorder.emit(stmt.deepCopy(substitutions)) }
    }

    val size: Int
        get() = statements.size

    override fun toString(): String =
        "ParameterizedLogicBlock2($name, params=[$param1Name, $param2Name], ${statements.size} statements)"
}
