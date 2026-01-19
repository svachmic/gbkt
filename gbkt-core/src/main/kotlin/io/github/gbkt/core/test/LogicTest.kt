/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.test

import io.github.gbkt.core.dsl.GameScope
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.GameScopeContext
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.U16Delegate
import io.github.gbkt.core.ir.U8Delegate

/**
 * Lightweight testing DSL for unit-testing isolated game logic.
 *
 * Unlike [testGame] and [testScene], this allows testing individual logic blocks without building a
 * full game or scene. Perfect for testing:
 * - Arithmetic expressions
 * - Conditional logic
 * - IR emission
 * - Variable assignments
 *
 * ## Example
 *
 * ```kotlin
 * @Test
 * fun `damage calculation works`() = testLogic {
 *     var health by u8Var(100)
 *     var damage by u8Var(10)
 *
 *     record { health -= damage }
 *         .assertEmitted<IRAssign> { it.target == "health" }
 *
 *     execute()
 *     expect("health").toEqual(90)
 * }
 * ```
 */
fun testLogic(block: LogicTestScope.() -> Unit) {
    val scope = LogicTestScope()
    GameScopeContext.withScope(scope) { scope.block() }
}

/**
 * Scope for logic unit tests. Provides variable creation, IR recording, and assertions without full
 * game setup.
 */
@TestDsl
class LogicTestScope : GameScope() {
    private val variableValues = mutableMapOf<String, SimValue>()
    private var lastRecorded: List<IRStatement> = emptyList()

    /** Create an unsigned 8-bit variable. */
    fun u8Var(initial: Int = 0): U8Delegate = U8Delegate(initial)

    /** Create an unsigned 16-bit variable. */
    fun u16Var(initial: Int = 0): U16Delegate = U16Delegate(initial)

    /** Create a signed 8-bit variable (uses u8 internally). */
    fun i8Var(initial: Int = 0): U8Delegate = U8Delegate(initial)

    /** Create a signed 16-bit variable (uses u16 internally). */
    fun i16Var(initial: Int = 0): U16Delegate = U16Delegate(initial)

    /**
     * Record IR statements from a block without executing them.
     *
     * The block is executed within a recording context, capturing all operations as IR nodes.
     *
     * @param block The logic to record
     * @return [RecordedIR] for fluent assertions
     *
     * ## Example
     *
     * ```kotlin
     * record { playerX += 10 }
     *     .assertEmitted<IRAssign>()
     *     .assertCount(1)
     * ```
     */
    fun record(block: () -> Unit): RecordedIR {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, block)
        lastRecorded = recorder.statements
        return RecordedIR(lastRecorded)
    }

    /**
     * Execute the last recorded IR statements against current variable state.
     *
     * This uses a lightweight executor that handles common IR nodes (assignments, conditionals,
     * loops).
     */
    fun execute() {
        // Convert variables list to a map for the executor
        val variableDefs = variables.associate { it.name to it.value }
        val executor = InlineExecutor(variableValues, variableDefs)
        lastRecorded.forEach { executor.executeStatement(it) }
    }

    /** Execute the given IR statements against current variable state. */
    fun execute(statements: List<IRStatement>) {
        val variableDefs = variables.associate { it.name to it.value }
        val executor = InlineExecutor(variableValues, variableDefs)
        statements.forEach { executor.executeStatement(it) }
    }

    /** Get the current value of a variable. */
    fun valueOf(name: String): SimValue = variableValues[name] ?: SimValue.ZERO

    /** Set a variable's value directly (for test setup). */
    fun setVariable(name: String, value: Int) {
        variableValues[name] = SimValue.of(value)
    }

    /**
     * Create an expectation for a variable's value.
     *
     * ## Example
     *
     * ```kotlin
     * expect("playerX").toEqual(100)
     * expect("score").toBeGreaterThan(50)
     * ```
     */
    fun expect(varName: String): IntExpectation = IntExpectation(valueOf(varName).toInt(), varName)

    /** Assert a boolean condition. */
    fun expectTrue(condition: Boolean, message: String = "Expected true") {
        if (!condition) throw AssertionError(message)
    }

    /** Assert a boolean condition is false. */
    fun expectFalse(condition: Boolean, message: String = "Expected false") {
        if (condition) throw AssertionError(message)
    }
}

/**
 * Wrapper for recorded IR statements with fluent assertions.
 *
 * Allows chaining assertions on captured IR:
 * ```kotlin
 * record { playerX += 10 }
 *     .assertEmitted<IRAssign>()
 *     .assertCount(1)
 *     .assertNoEmitted<IRSceneChange>()
 * ```
 */
class RecordedIR(val statements: List<IRStatement>) {

    /**
     * Assert that at least one statement of type [T] was emitted.
     *
     * @param message Custom error message
     * @param predicate Optional filter for the statement
     */
    inline fun <reified T : IRStatement> assertEmitted(
        message: String = "Expected ${T::class.simpleName} to be emitted",
        predicate: (T) -> Boolean = { true },
    ): RecordedIR {
        val found = statements.filterIsInstance<T>().any(predicate)
        if (!found) throw AssertionError(message)
        return this
    }

    /** Assert that no statement of type [T] was emitted. */
    inline fun <reified T : IRStatement> assertNotEmitted(
        message: String = "Expected no ${T::class.simpleName} to be emitted"
    ): RecordedIR {
        val found = statements.filterIsInstance<T>().any()
        if (found) throw AssertionError(message)
        return this
    }

    /** Assert exact number of statements were emitted. */
    fun assertCount(expected: Int): RecordedIR {
        if (statements.size != expected) {
            throw AssertionError(
                "Expected $expected statements but got ${statements.size}: $statements"
            )
        }
        return this
    }

    /** Assert at least N statements were emitted. */
    fun assertAtLeast(minimum: Int): RecordedIR {
        if (statements.size < minimum) {
            throw AssertionError("Expected at least $minimum statements but got ${statements.size}")
        }
        return this
    }

    /** Assert the first statement matches a predicate. */
    inline fun <reified T : IRStatement> assertFirst(
        message: String = "Expected first statement to be ${T::class.simpleName}",
        predicate: (T) -> Boolean = { true },
    ): RecordedIR {
        val first =
            statements.firstOrNull() ?: throw AssertionError("Expected at least one statement")
        if (first !is T) {
            throw AssertionError("$message, but was ${first::class.simpleName}")
        }
        if (!predicate(first)) {
            throw AssertionError(message)
        }
        return this
    }

    /** Get all statements of a specific type. */
    inline fun <reified T : IRStatement> filter(): List<T> = statements.filterIsInstance<T>()

    /** Get the first statement of a specific type. */
    inline fun <reified T : IRStatement> first(): T? =
        statements.filterIsInstance<T>().firstOrNull()

    /** Check if any statement matches a predicate. */
    fun any(predicate: (IRStatement) -> Boolean): Boolean = statements.any(predicate)

    /** Check if all statements match a predicate. */
    fun all(predicate: (IRStatement) -> Boolean): Boolean = statements.all(predicate)

    /** Get the statements as a list for custom assertions. */
    fun toList(): List<IRStatement> = statements
}
