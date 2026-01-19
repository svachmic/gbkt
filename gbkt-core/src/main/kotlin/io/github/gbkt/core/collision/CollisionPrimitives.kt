/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.collision

import io.github.gbkt.core.entity.Entity
import io.github.gbkt.core.graphics.Hitbox
import io.github.gbkt.core.graphics.Sprite
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IRLiteral

// =============================================================================
// COLLISION PRIMITIVES
// =============================================================================

/**
 * A point in 2D space. Can be created from expressions for runtime collision checks.
 *
 * Usage:
 * ```kotlin
 * val point = Point(bullet.x, bullet.y)
 * whenever(point collidesWithAABB enemy) { enemy.destroy() }
 * ```
 */
data class Point(val x: Expr, val y: Expr) {
    constructor(x: Int, y: Int) : this(Expr(IRLiteral(x)), Expr(IRLiteral(y)))
}

/**
 * A circle in 2D space defined by center point and radius.
 *
 * Usage:
 * ```kotlin
 * val explosion = Circle(player.x, player.y, 16) // 16 pixel radius
 * whenever(explosion collidesWithAABB enemy) { enemy.destroy() }
 * ```
 */
data class Circle(val centerX: Expr, val centerY: Expr, val radius: Int) {
    constructor(
        centerX: Int,
        centerY: Int,
        radius: Int,
    ) : this(Expr(IRLiteral(centerX)), Expr(IRLiteral(centerY)), radius)
}

/**
 * Axis-Aligned Bounding Box (AABB) for collision detection.
 *
 * Can be created from sprites, entities, or explicit bounds.
 *
 * Usage:
 * ```kotlin
 * val aabb = AABB.fromSprite(enemy)
 * val aabb2 = AABB(enemy.x, enemy.y, 8, 8)
 * ```
 */
data class AABB(val x: Expr, val y: Expr, val width: Int, val height: Int) {
    companion object {
        /** Create AABB from a sprite's position and hitbox. */
        fun fromSprite(sprite: Sprite): AABB {
            require(sprite.isBound) {
                "Sprite '${sprite.name}' must have position for collision detection."
            }
            val hitbox = sprite.effectiveHitbox
            return AABB(
                x = sprite.x + hitbox.xOffset,
                y = sprite.y + hitbox.yOffset,
                width = hitbox.width,
                height = hitbox.height,
            )
        }

        /** Create AABB from an entity's position and hitbox. */
        fun fromEntity(entity: Entity): AABB {
            val pos =
                entity.positionComponent
                    ?: error("Entity '${entity.name}' needs position for collision detection")
            val hitbox = entity.hitboxComponent?.hitbox ?: Hitbox(0, 0, 8, 8)
            return AABB(
                x = pos.x + hitbox.xOffset,
                y = pos.y + hitbox.yOffset,
                width = hitbox.width,
                height = hitbox.height,
            )
        }
    }

    /** Left edge X coordinate */
    val left: Expr
        get() = x

    /** Right edge X coordinate */
    val right: Expr
        get() = x + width

    /** Top edge Y coordinate */
    val top: Expr
        get() = y

    /** Bottom edge Y coordinate */
    val bottom: Expr
        get() = y + height
}

/**
 * Sweep collision result - contains information about a collision along a movement path.
 *
 * The basic `sweepCollision()` uses an expanded AABB approach which checks if the swept bounding
 * box overlaps the target. This is efficient for Game Boy's limited CPU and works well for
 * detecting "did fast object hit target this frame?" scenarios (bullets, projectiles, etc.).
 *
 * For more precise collision response, use `sweepCollisionPrecise()` which calculates:
 * - hitTime: Exact time of collision (0-255 fixed-point representing 0.0 to 1.0)
 * - normalX/normalY: Collision normal (-1, 0, or 1) for physics response
 * - contactX/contactY: Exact contact point on the target AABB
 *
 * For most Game Boy games (shooters, platformers, action games), the basic expanded AABB approach
 * provides correct behavior with good performance. Use the precise version when you need
 * physics-based collision response (bouncing, sliding along walls, etc.).
 */
data class SweepResult(
    /** Condition that evaluates to true if a collision occurred. */
    val collided: io.github.gbkt.core.ir.Condition,
    /**
     * Time of collision as fixed-point 0-255 (0.0-1.0). 0 means collision at start, 255 means
     * collision at end of movement. Only populated by `sweepCollisionPrecise()`.
     */
    val hitTime: Expr? = null,
    /**
     * Collision normal X component (-1, 0, or 1). Indicates which face of the target was hit. -1
     * means hit from the right, 1 means hit from the left. Only populated by
     * `sweepCollisionPrecise()`.
     */
    val normalX: Expr? = null,
    /**
     * Collision normal Y component (-1, 0, or 1). Indicates which face of the target was hit. -1
     * means hit from below, 1 means hit from above. Only populated by `sweepCollisionPrecise()`.
     */
    val normalY: Expr? = null,
    /**
     * X coordinate of the contact point on the target AABB. Only populated by
     * `sweepCollisionPrecise()`.
     */
    val contactX: Expr? = null,
    /**
     * Y coordinate of the contact point on the target AABB. Only populated by
     * `sweepCollisionPrecise()`.
     */
    val contactY: Expr? = null,
)
