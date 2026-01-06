/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.entity

import io.github.gbkt.core.AnimationRef
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.graphics.Animation
import io.github.gbkt.core.graphics.Hitbox
import io.github.gbkt.core.ir.AssignableExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.GBVar
import io.github.gbkt.core.ir.IRAnimationPlay
import io.github.gbkt.core.ir.IRArrayAssign
import io.github.gbkt.core.ir.IRBinary
import io.github.gbkt.core.ir.IRCall
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRPoolActiveCount
import io.github.gbkt.core.ir.IRPoolDespawn
import io.github.gbkt.core.ir.IRPoolDespawnAll
import io.github.gbkt.core.ir.IRPoolDespawnWhere
import io.github.gbkt.core.ir.IRPoolEntityVar
import io.github.gbkt.core.ir.IRPoolForEach
import io.github.gbkt.core.ir.IRPoolHasSpace
import io.github.gbkt.core.ir.IRPoolIsFull
import io.github.gbkt.core.ir.IRPoolPathAtTarget
import io.github.gbkt.core.ir.IRPoolPathFollow
import io.github.gbkt.core.ir.IRPoolPathRecalc
import io.github.gbkt.core.ir.IRPoolPathSetTarget
import io.github.gbkt.core.ir.IRPoolSpawn
import io.github.gbkt.core.ir.IRPoolSpawnAt
import io.github.gbkt.core.ir.IRPoolTrySpawn
import io.github.gbkt.core.ir.IRPoolUpdate
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRVar

// =============================================================================
// POOL - Entity pooling with lifecycle management
// =============================================================================

/** Definition of a custom per-entity state field. */
data class PoolStateField(val name: String, val type: GBVar.VarType, val defaultValue: Int)

/**
 * An entity pool with lifecycle hooks for bullets, particles, enemies, etc.
 *
 * Usage: val bullets = pool("bullet", size = 8) { position(0, 0) sprite(SpriteAsset("bullet.png"))
 * { size = 4 x 4 }
 *
 *       state {
 *           val timer by u8Var()
 *       }
 *       onSpawn {
 *           play("fly")
 *           timer set 120
 *       }
 *       onFrame {
 *           y -= 4
 *           timer -= 1
 *       }
 *       despawnWhen {
 *           y isBelow 8
 *           timer isEqualTo 0
 *       }
 *       onDespawn {
 *           hide()
 *       }
 *   }
 *
 * // Usage in scene: bullets.update() bullets.spawn { x set player.x; y set player.y }
 */
class Pool(
    val name: String,
    val size: Int,
    internal val hasPosition: Boolean,
    internal val hasVelocity: Boolean,
    internal val spriteAsset: String?,
    internal val spriteWidth: Int,
    internal val spriteHeight: Int,
    internal val oamStartSlot: Int,
    internal val stateFields: List<PoolStateField>,
    internal val onSpawnStatements: List<IRStatement>,
    internal val onFrameStatements: List<IRStatement>,
    internal val onDespawnStatements: List<IRStatement>,
    internal val despawnConditions: List<IRExpression>,
    internal val animations: Map<String, Animation>,
    internal val hitbox: Hitbox?,
    internal val paletteRef: String?,
    internal val paletteIndex: Int,
) {
    /** Index variable name used in generated loops */
    val indexVar: String = "_${name}_i"

    // =========================================================================
    // SPAWNING
    // =========================================================================

    /**
     * Spawn an entity from the pool.
     *
     * Usage: bullets.spawn { x set player.x y set player.y }
     */
    fun spawn(init: PoolEntityScope.() -> Unit = {}) {
        val initRecorder = StatementRecorder()
        RecordingContext.record(initRecorder) { PoolEntityScope(this, indexVar).init() }
        RecordingContext.require().emit(IRPoolSpawn(name, initRecorder.statements))
    }

    /**
     * Spawn an entity at a specific position.
     *
     * Usage: bullets.spawnAt(player.x, player.y) { damage set 10 }
     */
    fun spawnAt(x: Expr, y: Expr, init: PoolEntityScope.() -> Unit = {}) {
        val initRecorder = StatementRecorder()
        RecordingContext.record(initRecorder) { PoolEntityScope(this, indexVar).init() }
        RecordingContext.require().emit(IRPoolSpawnAt(name, x.ir, y.ir, initRecorder.statements))
    }

    /** Spawn at literal coordinates */
    fun spawnAt(x: Int, y: Int, init: PoolEntityScope.() -> Unit = {}) {
        spawnAt(Expr(IRLiteral(x)), Expr(IRLiteral(y)), init)
    }

    /**
     * Try to spawn with fallback if pool is full.
     *
     * Usage: bullets.trySpawn { x set player.x } orElse { // Pool full - play error sound }
     */
    fun trySpawn(init: PoolEntityScope.() -> Unit): TrySpawnBuilder {
        return TrySpawnBuilder(this, init)
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    /**
     * Update all active entities in the pool. Runs onFrame for each, then checks despawn
     * conditions.
     *
     * Call this in your scene's every.frame block: every.frame { bullets.update() }
     */
    fun update() {
        RecordingContext.require().emit(IRPoolUpdate(name))
    }

    // =========================================================================
    // ITERATION
    // =========================================================================

    /**
     * Iterate over all active entities in the pool.
     *
     * Usage: bullets.forEachActive { whenever(collidesWith(enemy)) { despawn() } }
     */
    fun forEachActive(block: PoolEntityScope.() -> Unit) {
        val bodyRecorder = StatementRecorder()
        RecordingContext.record(bodyRecorder) { PoolEntityScope(this, indexVar).block() }
        RecordingContext.require().emit(IRPoolForEach(name, bodyRecorder.statements, indexVar))
    }

    // =========================================================================
    // QUERIES
    // =========================================================================

    /**
     * Number of active entities in the pool.
     *
     * Usage: whenever(bullets.activeCount isEqualTo 0) { ... }
     */
    val activeCount: Expr
        get() = Expr(IRPoolActiveCount(name))

    /**
     * Check if pool has space for more entities.
     *
     * Usage: whenever(bullets.hasSpace) { bullets.spawn { ... } }
     */
    val hasSpace: Condition
        get() = Condition(IRPoolHasSpace(name))

    /**
     * Check if pool is full (no space for spawning).
     *
     * Usage: whenever(bullets.isFull) { showMaxIndicator() }
     */
    val isFull: Condition
        get() = Condition(IRPoolIsFull(name))

    // =========================================================================
    // BULK OPERATIONS
    // =========================================================================

    /**
     * Despawn all active entities in the pool.
     *
     * Usage: bullets.despawnAll() // Clear all bullets
     */
    fun despawnAll() {
        RecordingContext.require().emit(IRPoolDespawnAll(name))
    }

    /**
     * Despawn entities matching a condition.
     *
     * Usage: bullets.despawnWhere { x isAbove 160 } // Off-screen right
     */
    fun despawnWhere(condition: PoolEntityScope.() -> Condition) {
        val scope = PoolEntityScope(this, indexVar)
        val cond = scope.condition()
        RecordingContext.require().emit(IRPoolDespawnWhere(name, cond.ir, indexVar))
    }
}

/** Builder for trySpawn {} orElse {} pattern. */
class TrySpawnBuilder(private val pool: Pool, private val init: PoolEntityScope.() -> Unit) {
    /**
     * Fallback when pool is full.
     *
     * Usage: bullets.trySpawn { ... } orElse { playSound("error") }
     */
    infix fun orElse(elseBlock: () -> Unit) {
        val initRecorder = StatementRecorder()
        RecordingContext.record(initRecorder) { PoolEntityScope(pool, pool.indexVar).init() }

        val elseRecorder = StatementRecorder()
        RecordingContext.record(elseRecorder) { elseBlock() }

        RecordingContext.require()
            .emit(IRPoolTrySpawn(pool.name, initRecorder.statements, elseRecorder.statements))
    }
}

/**
 * Assignable expression for pool entity properties (x, y, velX, velY).
 *
 * Extends [AssignableExpr] to override assignment methods to use [IRArrayAssign] instead of
 * [IRAssign], generating array-indexed assignments like `particle_x[_particle_i] = value;` instead
 * of incorrect `particle_x = value;`.
 */
class PoolEntityVar(
    private val arrayName: String,
    private val indexVar: String,
    varType: GBVar.VarType,
) :
    AssignableExpr(
        arrayName,
        varType,
        IRPoolEntityVar(arrayName.substringBefore("_"), arrayName.substringAfter("_"), indexVar),
    ) {

    private val indexExpr: IRExpression = IRVar(indexVar)

    override infix fun set(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRArrayAssign(arrayName, indexExpr, IRLiteral(value)))
        }
    }

    override infix fun set(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRArrayAssign(arrayName, indexExpr, value.ir))
        }
    }

    override infix fun addAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRArrayAssign(
                        arrayName,
                        indexExpr,
                        IRBinary(ir, BinaryOp.ADD, IRLiteral(value)),
                    )
                )
        }
    }

    override infix fun addAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRArrayAssign(arrayName, indexExpr, IRBinary(ir, BinaryOp.ADD, value.ir)))
        }
    }

    override infix fun subAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRArrayAssign(
                        arrayName,
                        indexExpr,
                        IRBinary(ir, BinaryOp.SUB, IRLiteral(value)),
                    )
                )
        }
    }

    override infix fun subAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRArrayAssign(arrayName, indexExpr, IRBinary(ir, BinaryOp.SUB, value.ir)))
        }
    }

    override operator fun plusAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRArrayAssign(
                        arrayName,
                        indexExpr,
                        IRBinary(ir, BinaryOp.ADD, IRLiteral(value)),
                    )
                )
        }
    }

    override operator fun plusAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRArrayAssign(arrayName, indexExpr, IRBinary(ir, BinaryOp.ADD, value.ir)))
        }
    }

    override operator fun minusAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRArrayAssign(
                        arrayName,
                        indexExpr,
                        IRBinary(ir, BinaryOp.SUB, IRLiteral(value)),
                    )
                )
        }
    }

    override operator fun minusAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRArrayAssign(arrayName, indexExpr, IRBinary(ir, BinaryOp.SUB, value.ir)))
        }
    }

    override operator fun timesAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRArrayAssign(
                        arrayName,
                        indexExpr,
                        IRBinary(ir, BinaryOp.MUL, IRLiteral(value)),
                    )
                )
        }
    }

    override operator fun timesAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRArrayAssign(arrayName, indexExpr, IRBinary(ir, BinaryOp.MUL, value.ir)))
        }
    }

    override operator fun divAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRArrayAssign(
                        arrayName,
                        indexExpr,
                        IRBinary(ir, BinaryOp.DIV, IRLiteral(value)),
                    )
                )
        }
    }

    override operator fun divAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRArrayAssign(arrayName, indexExpr, IRBinary(ir, BinaryOp.DIV, value.ir)))
        }
    }

    override operator fun remAssign(value: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(
                    IRArrayAssign(
                        arrayName,
                        indexExpr,
                        IRBinary(ir, BinaryOp.MOD, IRLiteral(value)),
                    )
                )
        }
    }

    override operator fun remAssign(value: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRArrayAssign(arrayName, indexExpr, IRBinary(ir, BinaryOp.MOD, value.ir)))
        }
    }
}

/** Scope available inside pool lifecycle hooks (onSpawn, onFrame, onDespawn) and spawn blocks. */
class PoolEntityScope(private val pool: Pool, private val indexVar: String) : Movable {
    // =========================================================================
    // POSITION
    // =========================================================================

    /** X position of the current entity */
    override val x: AssignableExpr
        get() = PoolEntityVar("${pool.name}_x", indexVar, GBVar.VarType.U8)

    /** Y position of the current entity */
    override val y: AssignableExpr
        get() = PoolEntityVar("${pool.name}_y", indexVar, GBVar.VarType.U8)

    // =========================================================================
    // VELOCITY
    // =========================================================================

    /** X velocity of the current entity */
    override val velX: AssignableExpr
        get() = PoolEntityVar("${pool.name}_vel_x", indexVar, GBVar.VarType.I8)

    /** Y velocity of the current entity */
    override val velY: AssignableExpr
        get() = PoolEntityVar("${pool.name}_vel_y", indexVar, GBVar.VarType.I8)

    // =========================================================================
    // CURRENT INDEX
    // =========================================================================

    /** Current entity's pool index (0..size-1) */
    val index: Expr
        get() = Expr(IRVar(indexVar))

    // =========================================================================
    // SPRITE OPERATIONS
    // =========================================================================

    /**
     * Play an animation on the current entity's sprite.
     *
     * Usage:
     * ```kotlin
     * play(explodeAnim)
     * ```
     */
    fun play(ref: AnimationRef, loop: Boolean = true) {
        RecordingContext.require().emit(IRAnimationPlay("${pool.name}_sprite", ref.name, loop))
    }

    /** Show the sprite (move to visible position) */
    fun show() {
        RecordingContext.require().emit(IRCall("${pool.name}_show", listOf(IRVar(indexVar))))
    }

    /** Hide the sprite (move off-screen) */
    fun hide() {
        RecordingContext.require().emit(IRCall("${pool.name}_hide", listOf(IRVar(indexVar))))
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    /** Despawn the current entity (return to pool) */
    fun despawn() {
        RecordingContext.require().emit(IRPoolDespawn(pool.name, IRVar(indexVar)))
    }

    // =========================================================================
    // ANIMATION STATE
    // =========================================================================

    /**
     * Condition: current animation has completed (for one-shot animations).
     *
     * Usage in despawnWhen: despawnWhen { isAnimationComplete }
     */
    val isAnimationComplete: Condition
        get() =
            Condition(
                IRBinary(
                    IRPoolEntityVar(pool.name, "anim_complete", indexVar),
                    BinaryOp.EQ,
                    IRLiteral(1),
                )
            )

    /**
     * Check if specific animation is currently playing.
     *
     * Usage: whenever(isPlaying("explode")) { ... }
     */
    fun isPlaying(animationName: String): Condition {
        // This will compare the current animation index to the named animation's index
        return Condition(
            IRBinary(
                IRPoolEntityVar(pool.name, "anim", indexVar),
                BinaryOp.EQ,
                IRVar("ANIM_${pool.name.uppercase()}_${animationName.uppercase()}"),
            )
        )
    }

    // =========================================================================
    // CUSTOM STATE ACCESS
    // =========================================================================

    /**
     * Access a custom state field.
     *
     * Usage: state["timer"] set 60 whenever(state["health"] isEqualTo 0) { despawn() }
     */
    operator fun get(fieldName: String): AssignableExpr {
        val field =
            pool.stateFields.find { it.name == fieldName }
                ?: error("Unknown state field '$fieldName' in pool '${pool.name}'")
        return AssignableExpr(
            "${pool.name}_$fieldName",
            field.type,
            IRPoolEntityVar(pool.name, fieldName, indexVar),
        )
    }

    // =========================================================================
    // PATHFINDING
    // =========================================================================

    /**
     * Set the pathfinding target for this entity.
     *
     * Usage: setTarget(player.x, player.y)
     */
    fun setTarget(x: Expr, y: Expr) {
        RecordingContext.require().emit(IRPoolPathSetTarget(pool.name, x.ir, y.ir))
    }

    /** Set the pathfinding target for this entity (literal values). */
    fun setTarget(x: Int, y: Int) {
        RecordingContext.require().emit(IRPoolPathSetTarget(pool.name, IRLiteral(x), IRLiteral(y)))
    }

    /**
     * Move along the computed path. Call this in onFrame to make the entity follow its path.
     *
     * Usage: onFrame { followPath() }
     */
    fun followPath() {
        RecordingContext.require().emit(IRPoolPathFollow(pool.name))
    }

    /**
     * Recalculate the path for this entity.
     *
     * Usage: recalculatePath()
     */
    fun recalculatePath() {
        RecordingContext.require().emit(IRPoolPathRecalc(pool.name, indexVar))
    }

    /** Condition: entity has reached its target. */
    val atTarget: Condition
        get() = Condition(IRPoolPathAtTarget(pool.name, indexVar))

    // =========================================================================
    // COLLISION
    // =========================================================================

    /**
     * Check collision with another entity.
     *
     * Usage: whenever(collidesWith(player)) { despawn() }
     */
    fun collidesWith(other: Entity): Condition {
        // AABB collision between pool entity and another entity
        val otherX = other.x
        val otherY = other.y
        val otherHitbox = other.sprite?.hitbox ?: Hitbox(0, 0, 8, 8)
        val thisHitbox = pool.hitbox ?: Hitbox(0, 0, pool.spriteWidth, pool.spriteHeight)

        val left1 = x + thisHitbox.xOffset
        val top1 = y + thisHitbox.yOffset
        val right1 = left1 + thisHitbox.width
        val bottom1 = top1 + thisHitbox.height

        val left2 = otherX + otherHitbox.xOffset
        val top2 = otherY + otherHitbox.yOffset
        val right2 = left2 + otherHitbox.width
        val bottom2 = top2 + otherHitbox.height

        return (right1 isAbove left2) and
            (left1 isBelow right2) and
            (bottom1 isAbove top2) and
            (top1 isBelow bottom2)
    }
}
