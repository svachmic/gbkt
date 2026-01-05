/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.entity

import io.github.gbkt.core.PoolPathfindingBuilder
import io.github.gbkt.core.PoolPathfindingConfig
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.graphics.Animation
import io.github.gbkt.core.graphics.AnimationsBuilderWithRegions
import io.github.gbkt.core.graphics.Hitbox
import io.github.gbkt.core.graphics.Palette
import io.github.gbkt.core.graphics.RegionsBuilder
import io.github.gbkt.core.graphics.SpriteRegion
import io.github.gbkt.core.ir.Dimensions
import io.github.gbkt.core.ir.GBVar
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.x

// =============================================================================
// POOL BUILDER
// =============================================================================

/** Builder for defining a pool with DSL. */
@GbktDsl
class PoolBuilder(
    private val poolName: String,
    private val poolSize: Int,
    private val gameBuilder: GameBuilder,
) {
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var useU16Position: Boolean = false
    private var hasPosition: Boolean = false

    private var initialVelX: Int = 0
    private var initialVelY: Int = 0
    private var hasVelocity: Boolean = false

    private var spriteAsset: String? = null
    private var spriteWidth: Int = 8
    private var spriteHeight: Int = 8
    private var hitbox: Hitbox? = null
    private var animations: Map<String, Animation> = emptyMap()
    private var regions: Map<String, SpriteRegion> = emptyMap()
    private var paletteRef: String? = null
    private var paletteIndex: Int = 0

    private val stateFields = mutableListOf<PoolStateField>()

    // Index variable for lifecycle hooks
    private val indexVar: String = "_${poolName}_i"

    // =========================================================================
    // TYPED STATE VARIABLE DELEGATES
    // These can be declared at the pool builder level and captured by closures
    // =========================================================================

    /**
     * Create a u8 per-entity state variable.
     *
     * Usage:
     * ```kotlin
     * pool("bullets", 8) {
     *     val timer by u8State(60)
     *
     *     onFrame {
     *         timer -= 1  // Direct access, no this["timer"] needed
     *     }
     * }
     * ```
     */
    fun u8State(initial: Int = 0) =
        PoolStateVarDelegate(poolName, indexVar, GBVar.VarType.U8, initial, stateFields)

    /** Create an i8 per-entity state variable. */
    fun i8State(initial: Int = 0) =
        PoolStateVarDelegate(poolName, indexVar, GBVar.VarType.I8, initial, stateFields)

    /** Create a u16 per-entity state variable. */
    fun u16State(initial: Int = 0) =
        PoolStateVarDelegate(poolName, indexVar, GBVar.VarType.U16, initial, stateFields)

    /** Create an i16 per-entity state variable. */
    fun i16State(initial: Int = 0) =
        PoolStateVarDelegate(poolName, indexVar, GBVar.VarType.I16, initial, stateFields)

    private var pathfindingConfig: PoolPathfindingConfig? = null

    private var onSpawnStatements = listOf<IRStatement>()
    private var onFrameStatements = listOf<IRStatement>()
    private var onDespawnStatements = listOf<IRStatement>()
    private var despawnConditions = mutableListOf<IRExpression>()

    /**
     * Add position component.
     *
     * Usage: position(0, 0)
     */
    fun position(x: Int, y: Int, u16: Boolean = false) {
        hasPosition = true
        initialX = x
        initialY = y
        useU16Position = u16
    }

    /**
     * Add velocity component.
     *
     * Usage: velocity(0, 0)
     */
    fun velocity(velX: Int = 0, velY: Int = 0) {
        hasVelocity = true
        initialVelX = velX
        initialVelY = velY
    }

    /**
     * Add sprite component using a type-safe asset reference.
     *
     * Usage: sprite(Assets.Sprites.bullet) { size = 4 x 4 }
     */
    /**
     * Add sprite component.
     *
     * Usage: sprite(SpriteAsset("bullet.png")) { size = 4 x 4 hitbox(0, 0, 4, 4) animations { ... }
     * }
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
     * Define custom per-entity state.
     *
     * Usage: state { val timer by u8Var() val health by u8Var(100) }
     */
    fun state(init: PoolStateBuilder.() -> Unit) {
        val builder = PoolStateBuilder(poolName)
        builder.init()
        stateFields.addAll(builder.fields)
    }

    /**
     * Configure A* pathfinding for pool entities.
     *
     * Usage:
     * ```kotlin
     * pathfinding {
     *     navGrid = dungeonGrid
     *     updateInterval = 60.frames  // Recalc path every second
     *     maxDepth = 32
     * }
     * ```
     */
    fun pathfinding(init: PoolPathfindingBuilder.() -> Unit) {
        val builder = PoolPathfindingBuilder()
        builder.init()
        pathfindingConfig = builder.build()
    }

    /**
     * Lifecycle: called when entity spawns.
     *
     * Usage: onSpawn { play("fly") timer set 120 }
     */
    fun onSpawn(block: PoolEntityScope.() -> Unit) {
        val recorder = StatementRecorder()
        val pool = buildPartialPool()
        RecordingContext.record(recorder) { PoolEntityScope(pool, pool.indexVar).block() }
        onSpawnStatements = recorder.statements
    }

    /**
     * Lifecycle: called every frame while entity is active.
     *
     * Usage: onFrame { y -= 4 timer -= 1 }
     */
    fun onFrame(block: PoolEntityScope.() -> Unit) {
        val recorder = StatementRecorder()
        val pool = buildPartialPool()
        RecordingContext.record(recorder) { PoolEntityScope(pool, pool.indexVar).block() }
        onFrameStatements = recorder.statements
    }

    /**
     * Lifecycle: called when entity despawns.
     *
     * Usage: onDespawn { hide() }
     */
    fun onDespawn(block: PoolEntityScope.() -> Unit) {
        val recorder = StatementRecorder()
        val pool = buildPartialPool()
        RecordingContext.record(recorder) { PoolEntityScope(pool, pool.indexVar).block() }
        onDespawnStatements = recorder.statements
    }

    /**
     * Auto-despawn conditions. Entity despawns when ANY condition is true.
     *
     * Usage: despawnWhen { y isBelow 8 // Off-screen top timer isEqualTo 0 // Timer expired
     * isAnimationComplete // One-shot animation done }
     */
    fun despawnWhen(init: DespawnConditionBuilder.() -> Unit) {
        val pool = buildPartialPool()
        val builder = DespawnConditionBuilder(pool)
        builder.init()
        despawnConditions.addAll(builder.conditions)
    }

    /** Build a partial pool for use in lifecycle recording */
    private fun buildPartialPool(): Pool {
        return Pool(
            name = poolName,
            size = poolSize,
            hasPosition = hasPosition,
            hasVelocity = hasVelocity,
            spriteAsset = spriteAsset,
            spriteWidth = spriteWidth,
            spriteHeight = spriteHeight,
            oamStartSlot = 0, // Will be set properly in final build
            stateFields = stateFields.toList(),
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

    /** Build the final pool */
    fun build(oamStartSlot: Int): Pool {
        return Pool(
            name = poolName,
            size = poolSize,
            hasPosition = hasPosition,
            hasVelocity = hasVelocity,
            spriteAsset = spriteAsset,
            spriteWidth = spriteWidth,
            spriteHeight = spriteHeight,
            oamStartSlot = oamStartSlot,
            stateFields = stateFields.toList(),
            onSpawnStatements = onSpawnStatements,
            onFrameStatements = onFrameStatements,
            onDespawnStatements = onDespawnStatements,
            despawnConditions = despawnConditions.toList(),
            animations = animations,
            hitbox = hitbox,
            paletteRef = paletteRef,
            paletteIndex = paletteIndex,
        )
    }
}

/** Sprite builder for pool entities. */
@GbktDsl
class PoolSpriteBuilder {
    var size: Dimensions = 8 x 8
    var hitbox: Hitbox? = null
    var animations: Map<String, Animation> = emptyMap()
    var regions: Map<String, SpriteRegion> = emptyMap()
    var palette: Palette? = null

    fun hitbox(xOffset: Int, yOffset: Int, width: Int, height: Int) {
        hitbox = Hitbox(xOffset, yOffset, width, height)
    }

    fun regions(init: RegionsBuilder.() -> Unit) {
        val builder = RegionsBuilder()
        builder.init()
        regions = builder.regions.toMap()
    }

    fun animations(init: AnimationsBuilderWithRegions.() -> Unit) {
        val builder = AnimationsBuilderWithRegions(regions)
        builder.init()
        animations = builder.animations.toMap()
    }
}
