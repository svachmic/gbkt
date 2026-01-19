/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.collision

import io.github.gbkt.core.entity.Entity
import io.github.gbkt.core.graphics.Sprite
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IRBinary
import io.github.gbkt.core.ir.IRCallExpr
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRTernary

// =============================================================================
// BASIC COLLISION DETECTION
// =============================================================================

/**
 * Check if two sprites overlap using AABB collision.
 *
 * Usage:
 * ```kotlin
 * whenever(player overlaps enemy) { takeDamage() }
 * ```
 */
infix fun Sprite.overlaps(other: Sprite): Condition {
    return Condition(
        IRCallExpr("check_collision", listOf(IRLiteral(this.oamSlot), IRLiteral(other.oamSlot)))
    )
}

/**
 * Alias for overlaps - reads more naturally in some contexts.
 *
 * Usage:
 * ```kotlin
 * whenever(player collidesWith coin) { collectCoin() }
 * ```
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
infix fun Sprite.collidesWith(other: Sprite): Condition = this overlaps other

/**
 * Check if sprite is within screen bounds.
 *
 * Usage:
 * ```kotlin
 * whenever(bullet.onScreen.not()) { bullet.despawn() }
 * ```
 */
val Sprite.onScreen: Condition
    get() {
        return Condition(IRCallExpr("sprite_on_screen", listOf(IRLiteral(oamSlot))))
    }

// =============================================================================
// POINT-AABB COLLISION
// =============================================================================

/**
 * Check if a point collides with an AABB.
 *
 * Usage:
 * ```kotlin
 * val projectile = Point(bullet.x, bullet.y)
 * whenever(projectile collidesWithAABB enemy) { enemy.destroy() }
 * ```
 */
infix fun Point.collidesWithAABB(aabb: AABB): Condition {
    // Point is inside AABB if:
    // x >= left && x < right && y >= top && y < bottom
    return (x isAtLeast aabb.left) and
        (x isBelow aabb.right) and
        (y isAtLeast aabb.top) and
        (y isBelow aabb.bottom)
}

/**
 * Check if a point collides with a sprite's AABB.
 *
 * Usage:
 * ```kotlin
 * val click = Point(mouseX, mouseY)
 * whenever(click collidesWithAABB button) { button.press() }
 * ```
 */
infix fun Point.collidesWithAABB(sprite: Sprite): Condition {
    return this collidesWithAABB AABB.fromSprite(sprite)
}

/**
 * Check if a point collides with an entity's AABB.
 *
 * Usage:
 * ```kotlin
 * val click = Point(mouseX, mouseY)
 * whenever(click collidesWithAABB collectible) { collectible.collect() }
 * ```
 */
infix fun Point.collidesWithAABB(entity: Entity): Condition {
    return this collidesWithAABB AABB.fromEntity(entity)
}

// =============================================================================
// CIRCLE-AABB COLLISION
// =============================================================================

/**
 * Check if a circle collides with an AABB.
 *
 * Uses the closest point on the AABB to the circle center to determine collision.
 *
 * Usage:
 * ```kotlin
 * val explosion = Circle(player.x, player.y, 16)
 * whenever(explosion collidesWithAABB enemy) { enemy.destroy() }
 * ```
 */
infix fun Circle.collidesWithAABB(aabb: AABB): Condition {
    // Find the closest point on the AABB to the circle center
    val closestX =
        Expr(
            IRTernary(
                IRBinary(centerX.ir, BinaryOp.LT, aabb.left.ir),
                aabb.left.ir,
                IRTernary(
                    IRBinary(centerX.ir, BinaryOp.GT, aabb.right.ir),
                    aabb.right.ir,
                    centerX.ir,
                ),
            )
        )

    val closestY =
        Expr(
            IRTernary(
                IRBinary(centerY.ir, BinaryOp.LT, aabb.top.ir),
                aabb.top.ir,
                IRTernary(
                    IRBinary(centerY.ir, BinaryOp.GT, aabb.bottom.ir),
                    aabb.bottom.ir,
                    centerY.ir,
                ),
            )
        )

    // Calculate distance squared from circle center to closest point
    val dx = closestX - centerX
    val dy = closestY - centerY
    val distanceSquared = dx * dx + dy * dy
    val radiusSquared = radius * radius

    // Collision if distance squared <= radius squared
    return distanceSquared isAtMost radiusSquared
}

/**
 * Check if a circle collides with a sprite's AABB.
 *
 * Usage:
 * ```kotlin
 * val explosion = Circle(player.x, player.y, 16)
 * whenever(explosion collidesWithAABB enemy) { enemy.destroy() }
 * ```
 */
infix fun Circle.collidesWithAABB(sprite: Sprite): Condition {
    return this collidesWithAABB AABB.fromSprite(sprite)
}

/**
 * Check if a circle collides with an entity's AABB.
 *
 * Usage:
 * ```kotlin
 * val explosion = Circle(player.x, player.y, 16)
 * whenever(explosion collidesWithAABB enemy) { enemy.destroy() }
 * ```
 */
infix fun Circle.collidesWithAABB(entity: Entity): Condition {
    return this collidesWithAABB AABB.fromEntity(entity)
}
