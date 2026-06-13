/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.ActorPoolConfig
import io.github.gbkt.core.ir.ActorPoolIR
import io.github.gbkt.core.ir.CallExpr
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.PoolDestroyActor
import io.github.gbkt.core.ir.PoolDestroyAll
import io.github.gbkt.core.ir.PoolForEachActive
import io.github.gbkt.core.ir.PoolGetActiveCount
import io.github.gbkt.core.ir.PoolOverflowStrategy
import io.github.gbkt.core.ir.PoolSpawnActor
import io.github.gbkt.core.ir.VarRef
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// POOL ITERATOR
// =============================================================================

/**
 * Typed handle for the loop variable inside a [forEachActive] block.
 *
 * Eliminates raw `VarRef("bi")` magic strings — callers use [toExpr] to obtain the [Expr] reference
 * for the slot index.
 *
 * ```kotlin
 * forEachActive(bulletPool, "bi") { bi ->
 *     runIf(bullet.y isBelow 4) { destroy(bulletPool, bi.toExpr()) }
 * }
 * ```
 */
data class PoolIterator(val varName: String) {
    /** Returns a [VarRef] expression referencing this iterator's loop variable. */
    fun toExpr(): Expr = VarRef(varName)
}

// =============================================================================
// ACTOR POOL REF
// =============================================================================

/**
 * Type-safe reference to a registered actor pool.
 *
 * Returned by the `val bulletPool by pool(bullet, max = 8)` delegate when initialized inside a
 * `game {}` block.
 *
 * @param poolId Unique ID for the pool (inferred from the property name).
 * @param maxSize Maximum number of simultaneously active entities — embedded for DSL op recording.
 * @param actorTemplateId ID of the template actor used for all pool instances. Required for
 *   pool-pool collision codegen, which emits the underlying [CallExpr] using template actor IDs.
 */
class ActorPoolRef(val poolId: String, val maxSize: Int = 0, val actorTemplateId: String = "")

// =============================================================================
// POOL-POOL COLLISION (typed wrapper)
// =============================================================================

/**
 * Typed wrapper produced by [ActorPoolRef.collides]. Carries both pool refs so the [runIf]
 * overload can derive iterator slot variable names matching the codegen's auto-named loop variables
 * (e.g. `_pool_bi`, `_pool_ei`).
 *
 * DSL-only — the IR-level form emitted by [runIf] is the existing `CallExpr("collides",
 * [VarRef(templateA), VarRef(templateB)])` shape that `tryBuildPoolCollisionStatement` already
 * detects in the GBDK backend.
 */
data class PoolPoolCollisionExpr(val poolA: ActorPoolRef, val poolB: ActorPoolRef)

/**
 * Returns a typed pool-pool collision expression for use in [runIf] with a 2-arg lambda.
 *
 * ```kotlin
 * runIf(bulletPool.collides(enemyPool)) { bi, ei ->
 *     score += 10
 *     destroy(bulletPool, bi)
 *     destroy(enemyPool, ei)
 * }
 * ```
 *
 * The lambda receives typed [PoolIterator] handles for each pool's outer-loop iteration index. The
 * names `bi` / `ei` are user-chosen Kotlin lambda parameter names — no magic strings.
 */
fun ActorPoolRef.collides(other: ActorPoolRef): PoolPoolCollisionExpr =
    PoolPoolCollisionExpr(this, other)

// =============================================================================
// ACTOR POOL BUILDER
// =============================================================================

/**
 * Builder for per-pool configuration within a `pool()` delegate declaration.
 *
 * Configures the overflow strategy before the pool is registered.
 *
 * ```kotlin
 * val bulletPool by pool(bullet, max = 8) {
 *     overflow(PoolOverflowStrategy.RECYCLE_OLDEST)
 * }
 * ```
 *
 * @param actorTemplateId ID of the actor template for all pool sprites.
 * @param maxSize Maximum number of simultaneously active entities.
 */
@GbktDsl
class ActorPoolBuilder(val actorTemplateId: String, val maxSize: Int) {
    private var strategy: PoolOverflowStrategy = PoolOverflowStrategy.SILENT_NOOP

    /** Sets the overflow strategy for when the pool is full. */
    fun overflow(strategy: PoolOverflowStrategy) {
        this.strategy = strategy
    }

    internal fun buildConfig(): ActorPoolConfig = ActorPoolConfig(maxSize, strategy)
}

// =============================================================================
// ACTOR POOL DELEGATE
// =============================================================================

/**
 * Property delegate that registers an [ActorPoolIR] in the current [GameBuilder] and provides an
 * [ActorPoolRef].
 *
 * Created by the `pool(actorDelegate, max)` factory function in [GameBuilder]. On
 * `provideDelegate`, the property name is captured as the pool ID, the [ActorPoolIR] is registered,
 * and an [ActorPoolRef] is returned for use in spawn/destroy script ops.
 *
 * Usage:
 * ```kotlin
 * val bullet by actor { position(0, 0); sprite(asset("bullet.png")) { size(4, 4) } }
 * val bulletPool by pool(bullet, max = 8)
 * // Later in scene:
 * frame { spawn(bulletPool, player.x, player.y) }
 * ```
 *
 * @param actorDelegate The [ActorDelegate] for the template actor. Its ID is captured at
 *   `provideDelegate` time via the registered actor.
 * @param maxSize Maximum number of simultaneously active pool entities.
 * @param block Optional configuration block for overflow strategy.
 */
class ActorPoolDelegate(
    private val actorRef: ActorRef,
    private val maxSize: Int,
    private val block: ActorPoolBuilder.() -> Unit = {},
) {
    private var ref: ActorPoolRef? = null

    /**
     * Called by Kotlin's `by` delegation mechanism. Captures the property name as the pool ID,
     * registers the [ActorPoolIR] with the active [GameBuilder], and returns a [ReadOnlyProperty]
     * that yields [ActorPoolRef].
     */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, ActorPoolRef> {
        val poolId = property.name
        val builder = ActorPoolBuilder(actorRef.id, maxSize)
        builder.block()
        val config = builder.buildConfig()
        val poolIR = ActorPoolIR(id = poolId, actorTemplateId = actorRef.id, config = config)
        val gameBuilder =
            GameBuilderContext.current ?: error("pool() called outside a game {} block")
        gameBuilder.registerActorPool(poolIR)
        ref = ActorPoolRef(poolId, maxSize, actorRef.id)
        return ReadOnlyProperty { _, _ -> ActorPoolRef(poolId, maxSize, actorRef.id) }
    }
}

// =============================================================================
// FACTORY FUNCTION
// =============================================================================

/**
 * Declares an actor pool for spawning multiple instances of the same actor type.
 *
 * The pool manages OAM slots for up to [max] simultaneous entities. Use [spawn] and [destroy] in
 * script blocks to manage pool lifecycle.
 *
 * ```kotlin
 * val bullet by actor { position(0, 0); sprite(asset("bullet.png")) { size(4, 4) } }
 * val bulletPool by pool(bullet, max = 8)
 *
 * // In a scene frame block:
 * frame {
 *     runIf(buttons.a.pressed) {
 *         spawn(bulletPool, player.x, player.y)
 *     }
 * }
 * ```
 *
 * @param actor The [ActorRef] of the actor template. All pool entities share this sprite.
 * @param max Maximum number of simultaneously active entities.
 * @param block Optional configuration block (e.g., [ActorPoolBuilder.overflow]).
 */
fun GameBuilder.pool(
    actor: ActorRef,
    max: Int,
    block: ActorPoolBuilder.() -> Unit = {},
): ActorPoolDelegate = ActorPoolDelegate(actor, max, block)

// =============================================================================
// SCRIPTBUILDER EXTENSIONS — spawn / destroy / forEachActive / destroyAll
// =============================================================================

/**
 * Spawns a new entity from [pool] at the given integer position.
 *
 * Emits a [PoolSpawnActor] op. The generated C calls `pool_<poolId>_spawn(x, y)` and returns the
 * allocated slot index (0xFF if pool full with SILENT_NOOP strategy).
 *
 * Requires a [ScriptBuilder] context (i.e., called inside a scene lifecycle block).
 *
 * ```kotlin
 * frame {
 *     runIf(buttons.a.pressed) {
 *         spawn(bulletPool, player.x.toInt(), player.y.toInt())
 *     }
 * }
 * ```
 */
fun ScriptBuilder.spawn(pool: ActorPoolRef, x: Expr, y: Expr) {
    emit(PoolSpawnActor(pool.poolId, x, y, sourceLocation = captureV2Location()))
}

/** Spawns a new entity from [pool] at the given literal integer position. */
fun ScriptBuilder.spawn(pool: ActorPoolRef, x: Int, y: Int) {
    emit(PoolSpawnActor(pool.poolId, Literal(x), Literal(y), sourceLocation = captureV2Location()))
}

/**
 * Destroys (deactivates) a pool slot, hiding the associated sprite.
 *
 * Emits a [PoolDestroyActor] op. The generated C calls `pool_<poolId>_destroy(slot)` which marks
 * the slot inactive and moves the sprite offscreen.
 *
 * If [deathCallback] is provided, the ops recorded within it are emitted inline before the destroy
 * call. Use for explosion animations, particle bursts, etc.
 *
 * Requires a [ScriptBuilder] context (i.e., called inside a scene lifecycle block).
 *
 * ```kotlin
 * frame {
 *     runIf(bulletActive[i] isEqualTo 0) {
 *         destroy(bulletPool, i.toExpr()) { playAnim("explode") }
 *     }
 * }
 * ```
 */
fun ScriptBuilder.destroy(
    pool: ActorPoolRef,
    slot: Expr,
    deathCallback: (ScriptBuilder.() -> Unit)? = null,
) {
    val callbackOps =
        if (deathCallback != null) {
            val inner = ScriptBuilder()
            ScriptBuilderContext.with(inner) { inner.deathCallback() }
            inner.build()
        } else {
            emptyList()
        }
    emit(PoolDestroyActor(pool.poolId, slot, callbackOps, sourceLocation = captureV2Location()))
}

/**
 * Iterates over all active slots in the pool, executing [body] for each active slot.
 *
 * The slot index is available as [slotVar] (default: `"slot"`) inside the body block.
 *
 * Emits a [PoolForEachActive] op. The generated C for-loop iterates up to the pool's max size,
 * checking the active bitmap before executing the body.
 *
 * ```kotlin
 * frame {
 *     bulletPool.forEachActive("i") {
 *         // body — variable "i" holds the active slot index
 *     }
 * }
 * ```
 */
fun ScriptBuilder.forEachActive(
    pool: ActorPoolRef,
    slotVar: String = "slot",
    body: ScriptBuilder.() -> Unit,
) {
    val inner = ScriptBuilder()
    ScriptBuilderContext.with(inner) { inner.body() }
    emit(
        PoolForEachActive(
            poolId = pool.poolId,
            maxSize = pool.maxSize,
            slotVarName = slotVar,
            body = inner.build(),
            sourceLocation = captureV2Location(),
        )
    )
}

/**
 * Iterates over all active slots, passing a typed [PoolIterator] to the body.
 *
 * Preferred over the untyped overload — eliminates raw `VarRef("bi")` magic strings.
 *
 * ```kotlin
 * forEachActive(bulletPool, "bi") { bi ->
 *     runIf(bullet.y isBelow 4) { destroy(bulletPool, bi.toExpr()) }
 * }
 * ```
 */
fun ScriptBuilder.forEachActive(
    pool: ActorPoolRef,
    slotVar: String = "slot",
    body: ScriptBuilder.(iterator: PoolIterator) -> Unit,
) {
    val iter = PoolIterator(slotVar)
    val inner = ScriptBuilder()
    ScriptBuilderContext.with(inner) { inner.body(iter) }
    emit(
        PoolForEachActive(
            poolId = pool.poolId,
            maxSize = pool.maxSize,
            slotVarName = slotVar,
            body = inner.build(),
            sourceLocation = captureV2Location(),
        )
    )
}

/**
 * Returns an [Expr] expression evaluating to the number of active slots in the pool.
 *
 * Emits `pool_<poolId>_active_count()` in generated C. Usable in `runIf()` conditions and
 * variable assignments.
 *
 * ```kotlin
 * runIf(bulletPool.activeCount isEqualTo 0) { navigate(winScene) }
 * ```
 */
val ActorPoolRef.activeCount: Expr
    get() = PoolGetActiveCount(poolId)

/**
 * Destroys all active slots in the pool simultaneously.
 *
 * Emits a [PoolDestroyAll] op. The generated C for-loop clears the active bitmap and moves all
 * sprites offscreen in a single pass.
 *
 * ```kotlin
 * frame {
 *     runIf(buttons.start.pressed) { bulletPool.destroyAll() }
 * }
 * ```
 */
fun ScriptBuilder.destroyAll(pool: ActorPoolRef) {
    emit(PoolDestroyAll(pool.poolId, pool.maxSize, sourceLocation = captureV2Location()))
}

// =============================================================================
// POOL-POOL COLLISION — typed `runIf` overload with iterator handles
// =============================================================================

/**
 * Conditional block fired when any entity in [poolA] collides with any entity in [poolB].
 *
 * The lambda receives typed [PoolIterator] handles for the outer and inner pool slot indices,
 * letting the body call `destroy(pool, idx)` against the colliding instances. Lambda parameter
 * names are the user's choice — no magic strings.
 *
 * ```kotlin
 * runIf(bulletPool.collides(enemyPool)) { bi, ei ->
 *     score += 10
 *     destroy(bulletPool, bi)
 *     destroy(enemyPool, ei)
 *     playSound(explodeSfx)
 * }
 * ```
 *
 * Emits an [IfOp] with condition `CallExpr("collides", [VarRef(templateA), VarRef(templateB)])`,
 * matching the existing IR shape that the GBDK backend's `tryBuildPoolCollisionStatement` already
 * detects and lowers into nested `for` loops.
 *
 * The iterator slot names are auto-derived to match the codegen's loop variable naming
 * (`_pool_<short>i`), so `destroy(pool, iter)` resolves to `pool_<id>_destroy(_pool_<short>i)` in
 * the generated C.
 */
fun ScriptBuilder.runIf(
    collision: PoolPoolCollisionExpr,
    block: ScriptBuilder.(PoolIterator, PoolIterator) -> Unit,
) {
    val loc = captureV2Location()
    // Slot var names match the auto-derived names in
    // ScriptOpVisitor.buildBothPoolCollisionStatement: "_pool_${poolId.take(1)}i".
    // sanitizeVarName prepends "_" so we store the post-prefix form here.
    val iterA = PoolIterator("pool_${collision.poolA.poolId.take(1)}i")
    val iterB = PoolIterator("pool_${collision.poolB.poolId.take(1)}i")
    val bodyBuilder = ScriptBuilder()
    ScriptBuilderContext.with(bodyBuilder) { bodyBuilder.block(iterA, iterB) }
    val condition =
        CallExpr(
            function = "collides",
            args =
                listOf(
                    VarRef(collision.poolA.actorTemplateId),
                    VarRef(collision.poolB.actorTemplateId),
                ),
        )
    emit(IfOp(condition, bodyBuilder.build(), emptyList(), sourceLocation = loc))
}
