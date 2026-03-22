/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

/**
 * Thread-local holder for the active [ScriptBuilder] during DSL execution.
 *
 * Enables operator extension functions on [AssignableVar], [ActorPropertyRef], and [ArrayVar] to
 * emit [io.github.gbkt.core.ir.ScriptOp] nodes into the enclosing builder without explicit receiver
 * threading.
 *
 * The [ScriptBuilder] sets this context in every method that executes a `block: ScriptBuilder.() ->
 * Unit` parameter (e.g. [ScriptBuilder.whenever], [ScriptBuilder.ifOp], [ScriptBuilder.whileOp]).
 * [SceneBuilder] methods ([SceneBuilder.enter], [SceneBuilder.frame], [SceneBuilder.exit]) also set
 * this so that top-level assignments inside lifecycle blocks work correctly.
 *
 * Pattern mirrors [GameBuilderContext] exactly (same thread-local idiom, same restore-in-finally).
 */
internal object ScriptBuilderContext {
    private val holder = ThreadLocal<ScriptBuilder?>()

    /** Returns the currently active [ScriptBuilder], or null if not inside a builder block. */
    val current: ScriptBuilder?
        get() = holder.get()

    /**
     * Executes [block] with [builder] set as the active [ScriptBuilder].
     *
     * Restores the previous builder (or null) in a finally block to support nested contexts (e.g.
     * `whenever` inside `frame`).
     */
    fun <T> with(builder: ScriptBuilder, block: () -> T): T {
        val previous = holder.get()
        holder.set(builder)
        return try {
            block()
        } finally {
            holder.set(previous)
        }
    }
}
