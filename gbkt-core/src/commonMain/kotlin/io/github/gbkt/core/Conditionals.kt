/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRIf
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.WhenBranch

// =============================================================================
// WHEN EXPRESSION SUPPORT
// =============================================================================

/**
 * Kotlin-style when for conditions.
 *
 * Usage: when { button.a.pressed -> { jump() } button.b.pressed -> { shoot() } }
 *
 * This works because the infix '->' is defined on Condition.
 */
class WhenBuilder {
    internal val branches = mutableListOf<WhenBranch>()
    internal var otherwiseBranch: List<IRStatement>? = null

    operator fun Condition.minus(other: Unit) {
        // This is a hack to make the -> syntax work
        // The actual recording happens via rangeTo below
    }
}

/**
 * Overload '->' for when branches.
 *
 * condition -> { statements }
 *
 * Actually uses rangeTo operator (condition..block)
 */
operator fun Condition.rangeTo(block: () -> Unit): WhenBranchDef {
    val recorder = StatementRecorder()
    RecordingContext.record(recorder, block)
    return WhenBranchDef(this, recorder.statements)
}

data class WhenBranchDef(val condition: Condition, val statements: List<IRStatement>)

/**
 * When expression for game logic.
 *
 * Note: This is a function, not the Kotlin keyword, but usage looks similar:
 *
 * branch { button.a.pressed then { jump() } button.b.pressed then { shoot() } }
 */
inline fun branch(builder: BranchBuilder.() -> Unit) {
    val b = BranchBuilder()
    b.builder()

    // Convert to chained if-else by building from the last branch backward
    if (b.branches.isNotEmpty()) {
        var current: IRIf? = null
        for (branch in b.branches.reversed()) {
            current = IRIf(branch.condition.ir, branch.statements, current?.let { listOf(it) })
        }
        current?.let { RecordingContext.require().emit(it) }
    }
}

class BranchBuilder {
    val branches = mutableListOf<WhenBranchDef>()

    infix fun Condition.then(block: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, block)
        branches.add(WhenBranchDef(this, recorder.statements))
    }

    infix fun ButtonState.then(block: () -> Unit) {
        this.held then block
    }

    infix fun DpadDirectionState.then(block: () -> Unit) {
        this.held then block
    }
}

// =============================================================================
// WHENEVER - Sugar for single-condition blocks
// =============================================================================

/**
 * Result of a whenever block, allowing chaining with otherwise.
 *
 * Usage: whenever(condition) { ... } otherwise { ... }
 */
class WheneverResult(
    internal val condition: IRExpression,
    internal val thenStatements: List<IRStatement>
)

/**
 * Chain an else block after whenever.
 *
 * Usage: whenever(health isBelow 0) { gameOver() } otherwise { continueGame() }
 */
infix fun WheneverResult.otherwise(block: () -> Unit) {
    val elseRecorder = StatementRecorder()
    RecordingContext.record(elseRecorder, block)
    // Replace the last emitted IRIf with one that has the else branch
    RecordingContext.require().replaceLast(IRIf(condition, thenStatements, elseRecorder.statements))
}

/**
 * Single-condition if block. Cleaner than branch { } for simple cases.
 *
 * Usage: whenever(buttons.a.pressed) { playerVelY = 8 }
 *
 * whenever(playerY gt 100) { playerY = 100 isJumping = 0 }
 *
 * // With else: whenever(health isAbove 0) { updateGame() } otherwise { showGameOver() }
 */
inline fun whenever(condition: Condition, noinline block: () -> Unit): WheneverResult {
    val recorder = StatementRecorder()
    RecordingContext.record(recorder, block)
    RecordingContext.require().emit(IRIf(condition.ir, recorder.statements))
    return WheneverResult(condition.ir, recorder.statements)
}

/** Whenever with ButtonState directly */
inline fun whenever(button: ButtonState, noinline block: () -> Unit): WheneverResult {
    return whenever(button.held, block)
}

/**
 * Whenever with DpadDirectionState directly (backward compatible with dpad.left, dpad.right, etc.)
 */
inline fun whenever(direction: DpadDirectionState, noinline block: () -> Unit): WheneverResult {
    return whenever(direction.held, block)
}

/** Whenever with else branch (explicit two-argument version) */
inline fun whenever(
    condition: Condition,
    noinline thenBlock: () -> Unit,
    noinline elseBlock: () -> Unit
) {
    val thenRecorder = StatementRecorder()
    val elseRecorder = StatementRecorder()
    RecordingContext.record(thenRecorder, thenBlock)
    RecordingContext.record(elseRecorder, elseBlock)
    RecordingContext.require()
        .emit(IRIf(condition.ir, thenRecorder.statements, elseRecorder.statements))
}
