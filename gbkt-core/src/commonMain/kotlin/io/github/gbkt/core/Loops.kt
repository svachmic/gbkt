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
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IRFor
import io.github.gbkt.core.ir.IRVar
import io.github.gbkt.core.ir.IRWhile

// =============================================================================
// LOOP DSL CONSTRUCTS
// Provides repeat(), repeatIndexed(), and repeatWhile() for generating loops
// =============================================================================

/**
 * Counter for generating unique loop variable names across nested loops.
 *
 * Note: DSL definitions typically run sequentially during Gradle builds, so thread safety is not
 * critical here. Any duplicate names would cause clear compile-time errors, not silent bugs.
 */
private var loopCounter = 0

/**
 * Repeat a block of code a fixed number of times.
 *
 * Usage:
 * ```kotlin
 * repeat(8) {
 *     bullets.spawn { x set 80; y set 72 }
 * }
 * ```
 *
 * Generates:
 * ```c
 * for (UINT8 _loop0 = 0; _loop0 < 8; _loop0++) {
 *     // body
 * }
 * ```
 *
 * @param times Number of times to repeat (1..255 for UINT8)
 * @param block Code to repeat
 */
fun repeat(times: Int, block: () -> Unit) {
    require(times in 1..255) { "repeat count must be between 1 and 255, got $times" }

    val recorder = StatementRecorder()
    RecordingContext.record(recorder, block)

    val counter = "_loop${loopCounter++}"
    RecordingContext.require().emit(IRFor(counter, 0 until times, recorder.statements))
}

/**
 * Repeat with access to loop index.
 *
 * Usage:
 * ```kotlin
 * repeatIndexed(4) { i ->
 *     // i is an Expr that can be used in calculations
 *     spawnX set (20 + i * 40)
 * }
 * ```
 *
 * Generates:
 * ```c
 * for (UINT8 _loop0 = 0; _loop0 < 4; _loop0++) {
 *     spawn_x = 20 + _loop0 * 40;
 * }
 * ```
 *
 * @param times Number of iterations (1..255)
 * @param block Code that receives the loop index as Expr
 */
fun repeatIndexed(times: Int, block: (Expr) -> Unit) {
    require(times in 1..255) { "repeat count must be between 1 and 255, got $times" }

    val counter = "_loop${loopCounter++}"
    val indexExpr = Expr(IRVar(counter))

    val recorder = StatementRecorder()
    RecordingContext.record(recorder) { block(indexExpr) }

    RecordingContext.require().emit(IRFor(counter, 0 until times, recorder.statements))
}

/**
 * Repeat over a specific range.
 *
 * Usage:
 * ```kotlin
 * repeatRange(1..8) { i ->
 *     // i goes from 1 to 8 inclusive
 * }
 * ```
 *
 * @param range The integer range to iterate over (must be within 0..255)
 * @param block Code that receives the loop index as Expr
 */
fun repeatRange(range: IntRange, block: (Expr) -> Unit) {
    require(range.first >= 0 && range.last <= 255) { "range must be within 0..255, got $range" }

    val counter = "_loop${loopCounter++}"
    val indexExpr = Expr(IRVar(counter))

    val recorder = StatementRecorder()
    RecordingContext.record(recorder) { block(indexExpr) }

    RecordingContext.require().emit(IRFor(counter, range, recorder.statements))
}

/**
 * While loop that continues as long as condition is true.
 *
 * Usage:
 * ```kotlin
 * repeatWhile(counter isAbove 0) {
 *     counter -= 1
 *     // process one item
 * }
 * ```
 *
 * Generates:
 * ```c
 * while (counter > 0) {
 *     counter--;
 *     // process one item
 * }
 * ```
 *
 * WARNING: Ensure the condition eventually becomes false to avoid infinite loops on Game Boy.
 *
 * @param condition The condition to check each iteration
 * @param block Code to execute while condition is true
 */
fun repeatWhile(condition: Condition, block: () -> Unit) {
    val recorder = StatementRecorder()
    RecordingContext.record(recorder, block)

    RecordingContext.require().emit(IRWhile(condition.ir, recorder.statements))
}
