/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.graphics

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.entity.Pool
import io.github.gbkt.core.entity.PoolEntityScope
import io.github.gbkt.core.entity.PoolSpriteBuilder
import io.github.gbkt.core.entity.PoolStateBuilder
import io.github.gbkt.core.entity.PoolStateField
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.GBVar
import io.github.gbkt.core.ir.IRAssign
import io.github.gbkt.core.ir.IRBinary
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRPoolEntityVar
import io.github.gbkt.core.ir.IRStatement

// =============================================================================
// PARTICLE SYSTEM DSL
// =============================================================================

/**
 * A particle system that wraps a Pool with automatic lifetime despawn.
 *
 * Particles are ideal for visual effects like:
 * - Explosions
 * - Sparks
 * - Smoke
 * - Coins/collectibles bursting
 * - Trail effects
 *
 * Usage:
 * ```kotlin
 * val sparks = particles("spark") {
 *     sprite(SpriteAsset("spark.png")) { size = 2 x 2 }
 *     count = 8
 *     lifetime = 15.frames
 *
 *     velocity(0, 0)
 *
 *     onSpawn {
 *         // Initialize velocity, play animation, etc.
 *     }
 *
 *     onFrame {
 *         x += velX
 *         y += velY
 *     }
 * }
 *
 * // Single particle
 * sparks.emit(player.x, player.y)
 *
 * // Multiple particles at once
 * sparks.burst(player.x, player.y, count = 4)
 * ```
 */
class ParticleSystem
internal constructor(internal val pool: Pool, val name: String, val count: Int, val lifetime: Int) {
    // =========================================================================
    // EMITTING PARTICLES
    // =========================================================================

    /**
     * Emit a single particle at the given position.
     *
     * Usage: sparks.emit(player.x, player.y)
     */
    fun emit(x: Expr, y: Expr, init: PoolEntityScope.() -> Unit = {}) {
        pool.spawnAt(x, y, init)
    }

    /** Emit a single particle at literal coordinates. */
    fun emit(x: Int, y: Int, init: PoolEntityScope.() -> Unit = {}) {
        pool.spawnAt(x, y, init)
    }

    /**
     * Emit multiple particles at the same position (burst effect).
     *
     * Usage: sparks.burst(enemy.x, enemy.y, count = 4)
     */
    fun burst(x: Expr, y: Expr, count: Int, init: PoolEntityScope.() -> Unit = {}) {
        repeat(count) { pool.spawnAt(x, y, init) }
    }

    /** Emit multiple particles at literal coordinates. */
    fun burst(x: Int, y: Int, count: Int, init: PoolEntityScope.() -> Unit = {}) {
        burst(Expr(IRLiteral(x)), Expr(IRLiteral(y)), count, init)
    }

    // =========================================================================
    // UPDATE
    // =========================================================================

    /**
     * Update all active particles. Call this in your scene's every.frame block.
     *
     * Usage: every.frame { sparks.update() }
     */
    fun update() {
        pool.update()
    }

    // =========================================================================
    // QUERIES
    // =========================================================================

    /** Number of active particles. */
    val activeCount: Expr
        get() = pool.activeCount

    /** Check if there's space for more particles. */
    val hasSpace: Condition
        get() = pool.hasSpace

    /** Check if the particle pool is full. */
    val isFull: Condition
        get() = pool.isFull

    // =========================================================================
    // BULK OPERATIONS
    // =========================================================================

    /** Despawn all active particles. */
    fun despawnAll() {
        pool.despawnAll()
    }

    /** Iterate over active particles. */
    fun forEachActive(block: PoolEntityScope.() -> Unit) {
        pool.forEachActive(block)
    }
}

// =============================================================================
// PARTICLE SYSTEM BUILDER
// =============================================================================

/** Builder for defining a particle system with DSL. */
@GbktDsl
class ParticleSystemBuilder(
    private val particleName: String,
    private val gameBuilder: GameBuilder,
) {
    /** Maximum number of particles. Default: 8 */
    var count: Int = 8

    /**
     * Particle lifetime in frames. After this many frames, the particle automatically despawns.
     * Default: 30 frames (~0.5 seconds)
     */
    var lifetime: Int = 30

    // Internal pool builder state
    private var spriteAsset: String? = null
    private var spriteWidth: Int = 8
    private var spriteHeight: Int = 8
    private var hasVelocity: Boolean = false
    private var initialVelX: Int = 0
    private var initialVelY: Int = 0
    private var hitbox: Hitbox? = null
    private var animations: Map<String, Animation> = emptyMap()
    private var regions: Map<String, SpriteRegion> = emptyMap()
    private var paletteRef: String? = null
    private var paletteIndex: Int = 0

    private var onSpawnStatements = listOf<IRStatement>()
    private var onFrameStatements = listOf<IRStatement>()
    private var onDespawnStatements = listOf<IRStatement>()

    // Custom state fields (in addition to built-in _lifetime)
    private val stateFields = mutableListOf<PoolStateField>()

    /**
     * Set the lifetime using FrameTiming.
     *
     * Usage: lifetime = 15.frames
     */
    var lifetimeFrames: FrameTiming
        get() = FrameTiming(lifetime)
        set(value) {
            lifetime = value.count
        }

    /**
     * Define the particle sprite using a type-safe asset reference.
     *
     * Usage: sprite(Assets.Sprites.spark) { size = 2 x 2 }
     */
    fun sprite(
        asset: io.github.gbkt.core.assets.SpriteAsset,
        init: PoolSpriteBuilder.() -> Unit = {},
    ) {
        spriteAsset = asset.path
        val builder = PoolSpriteBuilder()
        builder.init()
        spriteWidth = builder.size.width
        spriteHeight = builder.size.height
        hitbox = builder.hitbox
        animations = builder.animations
        regions = builder.regions
        builder.palette?.let {
            paletteRef = it.name
            paletteIndex = it.assignedSlot
        }
    }

    /**
     * Add velocity component to particles.
     *
     * Usage: velocity(0, -1) // Default upward movement
     */
    fun velocity(velX: Int = 0, velY: Int = 0) {
        hasVelocity = true
        initialVelX = velX
        initialVelY = velY
    }

    /**
     * Define custom per-particle state.
     *
     * Usage: state { val scale by u8Var(1) }
     */
    fun state(init: PoolStateBuilder.() -> Unit) {
        val builder = PoolStateBuilder(particleName)
        builder.init()
        stateFields.addAll(builder.fields)
    }

    /** Called when a particle spawns. */
    fun onSpawn(block: PoolEntityScope.() -> Unit) {
        val recorder = StatementRecorder()
        val pool = buildPartialPool()
        RecordingContext.record(recorder) { PoolEntityScope(pool, pool.indexVar).block() }
        onSpawnStatements = recorder.statements
    }

    /** Called every frame while particle is active. */
    fun onFrame(block: PoolEntityScope.() -> Unit) {
        val recorder = StatementRecorder()
        val pool = buildPartialPool()
        RecordingContext.record(recorder) { PoolEntityScope(pool, pool.indexVar).block() }
        onFrameStatements = recorder.statements
    }

    /** Called when a particle despawns (lifetime expires or explicitly despawned). */
    fun onDespawn(block: PoolEntityScope.() -> Unit) {
        val recorder = StatementRecorder()
        val pool = buildPartialPool()
        RecordingContext.record(recorder) { PoolEntityScope(pool, pool.indexVar).block() }
        onDespawnStatements = recorder.statements
    }

    /** Build a partial pool for use in lifecycle recording */
    private fun buildPartialPool(): Pool {
        // Add lifetime field to state fields
        val allStateFields = buildList {
            add(PoolStateField("_lifetime", GBVar.VarType.U8, lifetime))
            addAll(stateFields)
        }

        return Pool(
            name = particleName,
            size = count,
            hasPosition = true, // Particles always have position
            hasVelocity = hasVelocity,
            spriteAsset = spriteAsset,
            spriteWidth = spriteWidth,
            spriteHeight = spriteHeight,
            oamStartSlot = 0, // Will be set properly in final build
            stateFields = allStateFields,
            onSpawnStatements = emptyList(),
            onFrameStatements = emptyList(),
            onDespawnStatements = emptyList(),
            despawnConditions = emptyList(),
            animations = animations,
            hitbox = hitbox,
            paletteRef = paletteRef,
            paletteIndex = paletteIndex,
        )
    }

    /** Build the final pool with lifetime auto-despawn */
    internal fun build(oamStartSlot: Int): Pair<Pool, ParticleSystem> {
        // Add lifetime field to state fields
        val allStateFields = buildList {
            add(PoolStateField("_lifetime", GBVar.VarType.U8, lifetime))
            addAll(stateFields)
        }

        // Index variable name for generated code
        val indexVar = "_${particleName}_i"

        // The target string for pool entity variable assignment uses direct array access
        val lifetimeTarget = "${particleName}__lifetime[$indexVar]"
        val lifetimeRead = IRPoolEntityVar(particleName, "_lifetime", indexVar)

        // Prepend lifetime initialization to onSpawn
        val lifetimeInit = IRAssign(lifetimeTarget, IRLiteral(lifetime))
        val fullOnSpawnStatements = listOf(lifetimeInit) + onSpawnStatements

        // Prepend lifetime decrement to onFrame (current - 1)
        val lifetimeDecr =
            IRAssign(
                lifetimeTarget,
                IRBinary(lifetimeRead, BinaryOp.SUB, IRLiteral(1)),
                AssignOp.SET,
            )
        val fullOnFrameStatements = listOf(lifetimeDecr) + onFrameStatements

        // Auto-despawn when lifetime reaches 0
        val lifetimeDespawnCondition = IRBinary(lifetimeRead, BinaryOp.EQ, IRLiteral(0))

        val pool =
            Pool(
                name = particleName,
                size = count,
                hasPosition = true, // Particles always have position
                hasVelocity = hasVelocity,
                spriteAsset = spriteAsset,
                spriteWidth = spriteWidth,
                spriteHeight = spriteHeight,
                oamStartSlot = oamStartSlot,
                stateFields = allStateFields,
                onSpawnStatements = fullOnSpawnStatements,
                onFrameStatements = fullOnFrameStatements,
                onDespawnStatements = onDespawnStatements,
                despawnConditions = listOf(lifetimeDespawnCondition),
                animations = animations,
                hitbox = hitbox,
                paletteRef = paletteRef,
                paletteIndex = paletteIndex,
            )

        val particleSystem =
            ParticleSystem(pool = pool, name = particleName, count = count, lifetime = lifetime)

        return pool to particleSystem
    }
}
