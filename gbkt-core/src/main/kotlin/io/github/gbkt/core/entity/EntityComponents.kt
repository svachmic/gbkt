/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.entity

import io.github.gbkt.core.StateMachine
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.graphics.Hitbox
import io.github.gbkt.core.graphics.Sprite
import io.github.gbkt.core.ir.AssignableExpr
import io.github.gbkt.core.ir.GBVar
import io.github.gbkt.core.ir.i8
import io.github.gbkt.core.ir.u16
import io.github.gbkt.core.ir.u8

// =============================================================================
// COMPONENTS
// =============================================================================

/**
 * Position component - manages x/y coordinates. Creates internal GBVar instances for the position.
 */
class PositionComponent(
    val entityName: String,
    initialX: Int,
    initialY: Int,
    varType: GBVar.VarType = GBVar.VarType.U8,
) {
    val xVarName: String = "${entityName}_x"
    val yVarName: String = "${entityName}_y"

    internal val xVar: GBVar<*>
    internal val yVar: GBVar<*>

    val x: AssignableExpr
    val y: AssignableExpr

    init {
        when (varType) {
            GBVar.VarType.U8 -> {
                xVar = GBVar(xVarName, u8(initialX), GBVar.VarType.U8)
                yVar = GBVar(yVarName, u8(initialY), GBVar.VarType.U8)
            }
            GBVar.VarType.U16 -> {
                xVar = GBVar(xVarName, u16(initialX), GBVar.VarType.U16)
                yVar = GBVar(yVarName, u16(initialY), GBVar.VarType.U16)
            }
            else -> error("Position only supports U8 or U16, got $varType")
        }

        x = AssignableExpr(xVarName, varType)
        y = AssignableExpr(yVarName, varType)
    }
}

/** Velocity component - for physics-based movement. Uses signed i8 for direction support. */
class VelocityComponent(val entityName: String, initialVelX: Int = 0, initialVelY: Int = 0) {
    val velXVarName: String = "${entityName}_vel_x"
    val velYVarName: String = "${entityName}_vel_y"

    internal val velXVar: GBVar<i8>
    internal val velYVar: GBVar<i8>

    val velX: AssignableExpr
    val velY: AssignableExpr

    init {
        velXVar = GBVar(velXVarName, i8(initialVelX), GBVar.VarType.I8)
        velYVar = GBVar(velYVarName, i8(initialVelY), GBVar.VarType.I8)

        velX = AssignableExpr(velXVarName, GBVar.VarType.I8)
        velY = AssignableExpr(velYVarName, GBVar.VarType.I8)
    }
}

/** Sprite component - wraps a Sprite with entity binding. */
class SpriteComponent(val sprite: Sprite)

/** Hitbox component - standalone collision bounds for entities without sprites. */
class HitboxComponent(val hitbox: Hitbox)

/** States component - wraps a StateMachine. */
class StatesComponent(val machine: StateMachine)

/** Tag component - for entity queries and batch operations. */
class TagComponent(val tags: Set<String>)

/**
 * Physics component - for gravity, friction, mass, and velocity clamping. Uses fixed-point 8.8
 * format (multiply by 256 for storage).
 *
 * The Game Boy doesn't have floating-point, so we use fixed-point math:
 * - Values are stored as 16-bit integers with 8 fractional bits
 * - A value of 128 represents 0.5 (128/256)
 * - A value of 256 represents 1.0
 *
 * Usage:
 * ```kotlin
 * val player by entity {
 *     position(80, 72)
 *     velocity(0, 0).physics {
 *         gravity = 0.5f     // Applied each frame to velocityY
 *         friction = 0.9f    // Multiplied to velocityX each frame
 *         maxVelocity = 4 to 8  // Clamp velocityX/Y
 *         mass = 1.0f        // Mass for collision response (1.0 = normal)
 *     }
 * }
 * ```
 */
class PhysicsComponent(
    val entityName: String,
    val gravity: Int, // Fixed-point 8.8: 128 = 0.5
    val friction: Int, // Fixed-point 8.8: 230 = ~0.9
    val maxVelocityX: Int, // Max velocity in pixels (integer)
    val maxVelocityY: Int, // Max velocity in pixels (integer)
    val mass: Int = 256, // Fixed-point 8.8: 256 = 1.0 (normal mass)
    val useLocalFriction: Boolean = false, // If true, use entity's friction instead of global
)

/**
 * Builder for physics component.
 *
 * Converts float values to fixed-point 8.8 format for Game Boy compatibility.
 */
@GbktDsl
class PhysicsBuilder(private val entityName: String) {
    /**
     * Gravity applied to velocityY each frame. Positive values pull down, negative values push up.
     *
     * Examples:
     * - 0.0 = no gravity (space, swimming)
     * - 0.25 = light gravity (floating/moon)
     * - 0.5 = normal platformer gravity
     * - 1.0 = heavy gravity
     */
    var gravity: Float = 0f

    /**
     * Friction multiplier applied to velocityX each frame. Values between 0.0 and 1.0.
     *
     * Examples:
     * - 1.0 = no friction (ice, space)
     * - 0.9 = normal friction
     * - 0.8 = high friction (sticky surfaces)
     * - 0.0 = instant stop
     */
    var friction: Float = 1f

    /**
     * Maximum velocity bounds (x, y) in pixels per frame.
     *
     * Usage: maxVelocity = 4 to 8 // maxX = 4, maxY = 8
     */
    var maxVelocity: Pair<Int, Int> = 4 to 8

    /**
     * Mass for collision response. Heavier objects push lighter objects more.
     *
     * Examples:
     * - 0.5 = light (gets pushed easily)
     * - 1.0 = normal mass
     * - 2.0 = heavy (harder to push)
     * - 10.0 = very heavy (nearly immovable)
     *
     * In collisions, the ratio of masses determines how much each entity is affected. For example,
     * if mass1=1.0 and mass2=2.0, entity1 receives 2/3 of the impulse while entity2 receives 1/3.
     */
    var mass: Float = 1f

    /**
     * Use this entity's friction value instead of the global physics world friction.
     *
     * When true, this entity acts as a "friction surface" - useful for ice, mud, etc. When false
     * (default), the global PHYSICS_FRICTION is applied.
     *
     * Example:
     * ```kotlin
     * // Ice platform - very slippery
     * val icePlatform by entity {
     *     velocity().physics {
     *         friction = 0.99f  // Almost no friction
     *         useLocalFriction = true
     *     }
     * }
     *
     * // Mud - very sticky
     * val mudPatch by entity {
     *     velocity().physics {
     *         friction = 0.7f  // High friction
     *         useLocalFriction = true
     *     }
     * }
     * ```
     */
    var useLocalFriction: Boolean = false

    /** Convert float to fixed-point 8.8 format. Value * 256, clamped to 16-bit range. */
    private fun toFixed88(value: Float): Int {
        return (value * 256f).toInt().coerceIn(-32768, 32767)
    }

    fun build(): PhysicsComponent {
        return PhysicsComponent(
            entityName = entityName,
            gravity = toFixed88(gravity),
            friction = toFixed88(friction),
            maxVelocityX = maxVelocity.first,
            maxVelocityY = maxVelocity.second,
            mass = toFixed88(mass),
            useLocalFriction = useLocalFriction,
        )
    }
}
