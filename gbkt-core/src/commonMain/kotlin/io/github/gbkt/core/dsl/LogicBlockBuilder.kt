/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRVar

/**
 * DSL for creating reusable logic blocks.
 *
 * Logic blocks allow you to extract common game logic patterns into reusable functions that work
 * within the recording context system.
 *
 * ## Basic Usage
 *
 * ```kotlin
 * // Define reusable logic blocks
 * val applyGravity = logicBlock("gravity") {
 *     velocityY += 1
 *     whenever(velocityY isAbove 8) { velocityY set 8 }
 * }
 *
 * val handleJump = logicBlock("jump") {
 *     whenever(buttons.a.pressed and onGround) {
 *         velocityY set -6
 *     }
 * }
 *
 * // Use in scenes
 * scene("gameplay") {
 *     every.frame {
 *         applyGravity()      // Expands to recorded IR
 *         handleJump()
 *         playerY += velocityY
 *     }
 * }
 * ```
 *
 * ## Parameterized Logic Blocks
 *
 * For logic that varies based on input:
 * ```kotlin
 * // Using expression placeholder
 * val addScore = logicBlock<Expr>("addScore", "amount") { amount ->
 *     score += amount
 * }
 *
 * // Usage
 * whenever(coinCollected) { addScore(10.expr) }
 * whenever(gemCollected) { addScore(50.expr) }
 * ```
 */

// =============================================================================
// BASIC LOGIC BLOCK
// =============================================================================

/**
 * Define a reusable logic block.
 *
 * The block is recorded once at definition time, then expanded at each call site. Variables used in
 * the block must be accessible at definition time.
 *
 * @param name Optional name for debugging (default: "anonymous")
 * @param block The logic to record
 * @return A [LogicBlock] that can be called within recording contexts
 *
 * ## Example
 *
 * ```kotlin
 * val clampPosition = logicBlock("clampPosition") {
 *     whenever(playerX isBelow 0) { playerX set 0 }
 *     whenever(playerX isAbove 160) { playerX set 160 }
 *     whenever(playerY isBelow 0) { playerY set 0 }
 *     whenever(playerY isAbove 144) { playerY set 144 }
 * }
 *
 * scene("gameplay") {
 *     every.frame {
 *         // Move player
 *         playerX += dpad.x * 2
 *         playerY += dpad.y * 2
 *         // Clamp to screen bounds
 *         clampPosition()
 *     }
 * }
 * ```
 */
fun logicBlock(name: String = "anonymous", block: () -> Unit): LogicBlock {
    val recorder = StatementRecorder()
    RecordingContext.record(recorder, block)
    return LogicBlock(name, recorder.statements)
}

// =============================================================================
// PARAMETERIZED LOGIC BLOCKS
// =============================================================================

/**
 * Define a logic block that takes a single expression parameter.
 *
 * The parameter is represented as a placeholder variable during recording, then substituted with
 * the actual expression at each call site.
 *
 * @param T The type of expression the parameter accepts (usually [Expr])
 * @param name Name for debugging
 * @param parameterName Name of the placeholder variable (used for substitution)
 * @param block Logic to record, receiving a placeholder expression
 * @return A [ParameterizedLogicBlock] that can be called with one parameter
 *
 * ## Example
 *
 * ```kotlin
 * val moveBy = logicBlock<Expr>("moveBy", "delta") { delta ->
 *     playerX += delta
 * }
 *
 * scene("gameplay") {
 *     every.frame {
 *         whenever(dpad.right) { moveBy(2.expr) }
 *         whenever(dpad.left) { moveBy((-2).expr) }
 *         whenever(buttons.b.held) { moveBy(4.expr) }  // Run faster
 *     }
 * }
 * ```
 */
inline fun <reified T : IRExpression> logicBlock(
    name: String,
    parameterName: String,
    crossinline block: (T) -> Unit
): ParameterizedLogicBlock<T> {
    val placeholderName = "__param_${parameterName}_${nextPlaceholderId()}"
    val placeholder = createPlaceholder<T>(placeholderName)

    val recorder = StatementRecorder()
    RecordingContext.record(recorder) { block(placeholder) }

    return ParameterizedLogicBlock(name, placeholderName, recorder.statements)
}

/**
 * Define a logic block that takes two expression parameters.
 *
 * @param T1 Type of the first parameter
 * @param T2 Type of the second parameter
 * @param name Name for debugging
 * @param param1Name Name of the first placeholder
 * @param param2Name Name of the second placeholder
 * @param block Logic to record, receiving two placeholder expressions
 *
 * ## Example
 *
 * ```kotlin
 * val moveEntity = logicBlock<Expr, Expr>("moveEntity", "dx", "dy") { dx, dy ->
 *     playerX += dx
 *     playerY += dy
 * }
 *
 * scene("gameplay") {
 *     every.frame {
 *         moveEntity(dpad.x * speed, dpad.y * speed)
 *     }
 * }
 * ```
 */
inline fun <reified T1 : IRExpression, reified T2 : IRExpression> logicBlock(
    name: String,
    param1Name: String,
    param2Name: String,
    crossinline block: (T1, T2) -> Unit
): ParameterizedLogicBlock2<T1, T2> {
    val id = nextPlaceholderId()
    val placeholder1Name = "__param_${param1Name}_$id"
    val placeholder2Name = "__param_${param2Name}_$id"
    val placeholder1 = createPlaceholder<T1>(placeholder1Name)
    val placeholder2 = createPlaceholder<T2>(placeholder2Name)

    val recorder = StatementRecorder()
    RecordingContext.record(recorder) { block(placeholder1, placeholder2) }

    return ParameterizedLogicBlock2(name, placeholder1Name, placeholder2Name, recorder.statements)
}

// =============================================================================
// HELPER FUNCTIONS
// =============================================================================

/**
 * Counter for generating unique placeholder names. This is used to ensure each parameterized logic
 * block gets unique placeholder IDs.
 *
 * Note: DSL definitions typically run sequentially during Gradle builds, so thread safety is not
 * critical here. Any duplicate names would cause clear compile-time errors, not silent bugs.
 */
private var placeholderCounter = 0

/** Generate a unique ID for placeholder variables. */
@PublishedApi internal fun nextPlaceholderId(): Int = placeholderCounter++

/**
 * Create a placeholder expression for parameterized logic blocks.
 *
 * The placeholder is an [IRVar] that will be substituted with the actual expression when the logic
 * block is invoked.
 */
@PublishedApi
internal inline fun <reified T : IRExpression> createPlaceholder(name: String): T {
    val irVar = IRVar(name)
    return when {
        T::class == Expr::class -> Expr(irVar) as T
        T::class == IRExpression::class -> irVar as T
        else -> irVar as T
    }
}

// =============================================================================
// CONVENIENCE EXTENSIONS
// =============================================================================

/**
 * Convert an Int to an Expr for use with parameterized logic blocks.
 *
 * ```kotlin
 * val addScore = logicBlock<Expr>("addScore", "amount") { amount ->
 *     score += amount
 * }
 *
 * addScore(10.expr)  // Convert 10 to Expr
 * ```
 */
val Int.expr: Expr
    get() = Expr(io.github.gbkt.core.ir.IRLiteral(this))

/**
 * Extension to make including logic blocks more explicit.
 *
 * ```kotlin
 * every.frame {
 *     include(applyGravity)
 *     include(handleInput)
 *     // ...
 * }
 * ```
 */
fun include(block: LogicBlock) = block()
