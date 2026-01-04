/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.GameScopeContext
import io.github.gbkt.core.ir.IRCollisionResponse
import io.github.gbkt.core.ir.IRPhysicsWorldUpdate

// =============================================================================
// PHYSICS SYSTEM DSL
// Global physics world configuration with automatic collision response
// =============================================================================

/**
 * Gravity zone - defines a rectangular area with custom gravity.
 *
 * When an entity enters a gravity zone, the zone's gravity is applied instead of the global
 * gravity. Useful for:
 * - Water areas (reduced gravity)
 * - Space sections (zero gravity)
 * - High-gravity zones
 * - Reverse gravity areas (negative values)
 *
 * @param x Left edge of the zone (pixels)
 * @param y Top edge of the zone (pixels)
 * @param width Width of the zone (pixels)
 * @param height Height of the zone (pixels)
 * @param gravity Gravity value in this zone (fixed-point 8.8)
 */
data class GravityZone(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val gravity: Int // Fixed-point 8.8
)

/**
 * Physics world configuration - defines global physics parameters and automatic collision response.
 *
 * ## Basic Usage
 *
 * ```kotlin
 * val physicsWorld = physics {
 *     gravity = 0.5f
 *     friction = 0.9f
 *     bounce = 0.3f
 * }
 *
 * scene("gameplay") {
 *     enter {
 *         // Enable automatic collision response for tagged entities
 *         physicsWorld.collide("player", "enemy")
 *     }
 *
 *     every.frame {
 *         physicsWorld.update()
 *     }
 * }
 * ```
 *
 * ## Gravity Zones
 *
 * ```kotlin
 * val physicsWorld = physics {
 *     gravity = 0.5f
 *
 *     // Water area with reduced gravity
 *     gravityZone(x = 0, y = 100, width = 160, height = 44) {
 *         gravity = 0.1f
 *     }
 *
 *     // Zero-gravity space section
 *     gravityZone(x = 100, y = 0, width = 60, height = 100) {
 *         gravity = 0f
 *     }
 * }
 * ```
 */
class PhysicsWorld internal constructor(val name: String, val config: PhysicsWorldConfig) {
    internal val collisionPairs = mutableListOf<Pair<String, String>>()
    internal val gravityZones = mutableListOf<GravityZone>()
    /**
     * Update physics world - applies physics to all tagged entities and handles collisions. Must be
     * called in every.frame block.
     *
     * ```kotlin
     * every.frame {
     *     physicsWorld.update()
     * }
     * ```
     */
    fun update() {
        RecordingContext.require().emit(IRPhysicsWorldUpdate)
    }

    /**
     * Enable automatic collision response between two tag groups.
     *
     * When entities with tag1 collide with entities with tag2, they will bounce based on the
     * configured bounce coefficient.
     *
     * ```kotlin
     * physicsWorld.collide("player", "enemy")
     * physicsWorld.collide("bullet", "wall")
     * ```
     */
    fun collide(tag1: String, tag2: String) {
        collisionPairs.add(tag1 to tag2)
        RecordingContext.require().emit(IRCollisionResponse(tag1, tag2))
    }

    /**
     * Enable automatic collision response between two tag groups (type-safe).
     *
     * ```kotlin
     * val playerTag = tag("player")
     * val enemyTag = tag("enemy")
     * physicsWorld.collide(playerTag, enemyTag)
     * ```
     */
    fun collide(tag1: TagRef, tag2: TagRef) {
        collide(tag1.name, tag2.name)
    }
}

/** Physics world configuration. */
data class PhysicsWorldConfig(
    val gravity: Float = 0.5f, // Applied to velocityY each frame (fixed-point 8.8)
    val friction: Float = 0.9f, // Multiplied to velocityX each frame (fixed-point 8.8)
    val bounce: Float = 0.3f, // Collision bounce coefficient (0.0 = no bounce, 1.0 = full bounce)
    val gravityZones: List<GravityZone> = emptyList() // Zones with custom gravity
)

/** Builder for physics world configuration. */
@GbktDsl
class PhysicsWorldBuilder(private val name: String) {
    /**
     * Global gravity applied to all entities with physics component each frame. Positive values
     * pull down, negative values push up.
     *
     * Examples:
     * - 0.0 = no gravity (space, swimming)
     * - 0.25 = light gravity (floating/moon)
     * - 0.5 = normal platformer gravity
     * - 1.0 = heavy gravity
     */
    var gravity: Float = 0.5f

    /**
     * Global friction multiplier applied to velocityX each frame. Values between 0.0 and 1.0.
     *
     * Examples:
     * - 1.0 = no friction (ice, space)
     * - 0.9 = normal friction
     * - 0.8 = high friction (sticky surfaces)
     * - 0.0 = instant stop
     */
    var friction: Float = 0.9f

    /**
     * Bounce coefficient for collision response. Values between 0.0 and 1.0.
     *
     * Examples:
     * - 0.0 = no bounce (objects stick together)
     * - 0.3 = light bounce (normal collisions)
     * - 0.7 = bouncy (rubber balls)
     * - 1.0 = perfect bounce (conserves all velocity)
     */
    var bounce: Float = 0.3f

    private val gravityZones = mutableListOf<GravityZone>()

    /**
     * Define a gravity zone - a rectangular area with custom gravity.
     *
     * When an entity's center is within the zone, the zone's gravity is applied instead of the
     * global gravity. Zones are checked in order - the first matching zone wins.
     *
     * @param x Left edge of the zone (pixels)
     * @param y Top edge of the zone (pixels)
     * @param width Width of the zone (pixels)
     * @param height Height of the zone (pixels)
     * @param init Builder to configure the zone's gravity
     *
     * Example:
     * ```kotlin
     * physics {
     *     gravity = 0.5f
     *
     *     // Water area at bottom of screen
     *     gravityZone(x = 0, y = 100, width = 160, height = 44) {
     *         gravity = 0.1f  // Slow fall in water
     *     }
     *
     *     // Zero-G space section
     *     gravityZone(x = 100, y = 0, width = 60, height = 100) {
     *         gravity = 0f
     *     }
     *
     *     // Reverse gravity zone
     *     gravityZone(x = 0, y = 50, width = 50, height = 50) {
     *         gravity = -0.3f  // Float upward
     *     }
     * }
     * ```
     */
    fun gravityZone(x: Int, y: Int, width: Int, height: Int, init: GravityZoneBuilder.() -> Unit) {
        val builder = GravityZoneBuilder(x, y, width, height)
        builder.init()
        gravityZones.add(builder.build())
    }

    internal fun build(): PhysicsWorld {
        val world =
            PhysicsWorld(
                name = name,
                config =
                    PhysicsWorldConfig(
                        gravity = gravity,
                        friction = friction,
                        bounce = bounce,
                        gravityZones = gravityZones.toList()
                    )
            )
        // Also copy zones to the world's internal list for codegen access
        world.gravityZones.addAll(gravityZones)
        return world
    }
}

/** Builder for gravity zone configuration. */
@GbktDsl
class GravityZoneBuilder(
    private val x: Int,
    private val y: Int,
    private val width: Int,
    private val height: Int
) {
    /**
     * Gravity value within this zone.
     *
     * Examples:
     * - 0.0 = no gravity (space, water surface)
     * - 0.1 = very light gravity (underwater)
     * - 0.5 = normal gravity
     * - -0.3 = reverse gravity (float upward)
     */
    var gravity: Float = 0f

    /** Convert float to fixed-point 8.8 format. */
    private fun toFixed88(value: Float): Int {
        return (value * 256f).toInt().coerceIn(-32768, 32767)
    }

    internal fun build(): GravityZone {
        return GravityZone(
            x = x,
            y = y,
            width = width,
            height = height,
            gravity = toFixed88(gravity)
        )
    }
}

/**
 * Create a physics world with global physics parameters.
 *
 * ```kotlin
 * val physicsWorld = physics {
 *     gravity = 0.5f
 *     friction = 0.9f
 *     bounce = 0.3f
 * }
 * ```
 */
fun physics(init: PhysicsWorldBuilder.() -> Unit): PhysicsWorld {
    val builder = PhysicsWorldBuilder("physics")
    builder.init()
    val physicsWorld = builder.build()

    // Register physics world with game builder
    val gameBuilder =
        GameScopeContext.current as? GameBuilder
            ?: error("physics() must be called within gbGame { } block")
    gameBuilder.registerPhysicsWorld(physicsWorld)

    return physicsWorld
}
