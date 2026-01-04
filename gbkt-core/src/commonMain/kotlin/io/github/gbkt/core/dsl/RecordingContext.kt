/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.SourceLocation
import io.github.gbkt.core.ir.IRArrayAssign
import io.github.gbkt.core.ir.IRAssign
import io.github.gbkt.core.ir.IRCall
import io.github.gbkt.core.ir.IRFor
import io.github.gbkt.core.ir.IRIf
import io.github.gbkt.core.ir.IRRaw
import io.github.gbkt.core.ir.IRSceneChange
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRWhen
import io.github.gbkt.core.ir.IRWhile

// =============================================================================
// RECORDING CONTEXT - The magic that makes operators generate IR
// =============================================================================

/** Platform-specific thread-local storage for the statement recorder. */
expect class RecorderHolder() {
    fun get(): StatementRecorder?

    fun set(recorder: StatementRecorder?)
}

/**
 * When inside a recording context, operations on GBVar generate IR nodes instead of executing
 * immediately. This is how we capture Kotlin code as a compilable program.
 */
object RecordingContext {
    private val holder = RecorderHolder()

    val current: StatementRecorder?
        get() = holder.get()

    val isRecording: Boolean
        get() = current != null

    /**
     * Get the current recording context, throwing an error if not inside a recording block. Use
     * this for operations that must be inside every.frame, enter, exit, or whenever blocks.
     */
    fun require(): StatementRecorder =
        current
            ?: error(
                "This operation must be called within a recording context " +
                    "(e.g., every.frame { ... }, enter { ... }, whenever { ... })"
            )

    fun <T> record(recorder: StatementRecorder, block: () -> T): T {
        val previous = holder.get()
        holder.set(recorder)
        return try {
            block()
        } finally {
            holder.set(previous)
        }
    }
}

/** Collects statements during recording. */
class StatementRecorder {
    private val _statements = mutableListOf<IRStatement>()
    val statements: List<IRStatement>
        get() = _statements

    /**
     * Emit a statement, optionally capturing its source location. If the statement doesn't already
     * have a source location and automatic capture is enabled, the location will be captured from
     * the call stack.
     */
    fun emit(stmt: IRStatement) {
        val stmtWithLocation =
            if (stmt.sourceLocation == null) {
                stmt.withSourceLocation(SourceLocation.capture())
            } else {
                stmt
            }
        _statements.add(stmtWithLocation)
    }

    fun clear() = _statements.clear()

    /** Replace the last emitted statement. Used for chaining like `whenever { } otherwise { }`. */
    fun replaceLast(stmt: IRStatement) {
        if (_statements.isNotEmpty()) {
            _statements[_statements.lastIndex] = stmt
        }
    }
}

/**
 * Extension function to create a copy of an IRStatement with a new source location. Only works for
 * statements that support source location tracking.
 */
fun IRStatement.withSourceLocation(location: SourceLocation?): IRStatement {
    if (location == null) return this
    return when (this) {
        is IRAssign -> copy(sourceLocation = location)
        is IRIf -> copy(sourceLocation = location)
        is IRWhen -> copy(sourceLocation = location)
        is IRWhile -> copy(sourceLocation = location)
        is IRFor -> copy(sourceLocation = location)
        is IRCall -> copy(sourceLocation = location)
        is IRSceneChange -> copy(sourceLocation = location)
        is IRRaw -> copy(sourceLocation = location)
        is IRArrayAssign -> copy(sourceLocation = location)
        // For statements that don't support source location, return as-is
        else -> this
    }
}
