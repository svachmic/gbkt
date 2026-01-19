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
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IRBinary
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRTernary

// =============================================================================
// SWEEP COLLISION (Expanded AABB)
// =============================================================================

/**
 * Sweep collision detection for fast-moving objects.
 *
 * Uses an expanded AABB approach: creates a bounding box that encompasses the entire movement path
 * and checks if it overlaps the target. This is efficient and correct for detecting if a collision
 * occurred during movement.
 *
 * This approach works well for:
 * - Bullets hitting enemies (any overlap means hit)
 * - Fast-moving projectiles
 * - Player movement through obstacles
 *
 * For physics-based collision response requiring exact contact point or time, consider using
 * `sweepCollisionPrecise()`.
 *
 * @param startX Starting X position
 * @param startY Starting Y position
 * @param deltaX Movement in X direction (velocity)
 * @param deltaY Movement in Y direction (velocity)
 * @param width Width of the moving object
 * @param height Height of the moving object
 * @param target The target AABB to check collision against
 *
 * Usage:
 * ```kotlin
 * val result = sweepCollision(
 *     startX = bullet.x,
 *     startY = bullet.y,
 *     deltaX = bullet.velocityX,
 *     deltaY = bullet.velocityY,
 *     width = 4,
 *     height = 4,
 *     target = AABB.fromSprite(enemy)
 * )
 * whenever(result.collided) { enemy.destroy() }
 * ```
 */
fun sweepCollision(
    startX: Expr,
    startY: Expr,
    deltaX: Expr,
    deltaY: Expr,
    width: Int,
    height: Int,
    target: AABB,
): SweepResult {
    // Calculate the swept bounding box (min/max bounds during entire movement)
    val endX = startX + deltaX
    val endY = startY + deltaY

    val startLeft = startX
    val startRight = startX + width
    val startTop = startY
    val startBottom = startY + height

    val endLeft = endX
    val endRight = endX + width
    val endTop = endY
    val endBottom = endY + height

    // Check if the swept area overlaps with the target
    // We check if the minimum bounds of the sweep overlap with the maximum bounds
    val minX =
        Expr(IRTernary(IRBinary(startLeft.ir, BinaryOp.LT, endLeft.ir), startLeft.ir, endLeft.ir))
    val maxX =
        Expr(
            IRTernary(IRBinary(startRight.ir, BinaryOp.GT, endRight.ir), startRight.ir, endRight.ir)
        )
    val minY =
        Expr(IRTernary(IRBinary(startTop.ir, BinaryOp.LT, endTop.ir), startTop.ir, endTop.ir))
    val maxY =
        Expr(
            IRTernary(
                IRBinary(startBottom.ir, BinaryOp.GT, endBottom.ir),
                startBottom.ir,
                endBottom.ir,
            )
        )

    // Check if the swept bounds overlap with the target
    val collided =
        (minX isBelow target.right) and
            (maxX isAbove target.left) and
            (minY isBelow target.bottom) and
            (maxY isAbove target.top)

    return SweepResult(collided)
}

/**
 * Sweep collision detection for a moving sprite against a target sprite.
 *
 * Usage:
 * ```kotlin
 * val result = sweepCollision(
 *     startX = bullet.x,
 *     startY = bullet.y,
 *     deltaX = bullet.velocityX,
 *     deltaY = bullet.velocityY,
 *     sprite = bullet,
 *     target = enemy
 * )
 * whenever(result.collided) { enemy.destroy() }
 * ```
 */
fun sweepCollision(
    startX: Expr,
    startY: Expr,
    deltaX: Expr,
    deltaY: Expr,
    sprite: Sprite,
    target: Sprite,
): SweepResult {
    require(sprite.isBound && target.isBound) {
        "Both sprites must have positions for sweep collision"
    }
    val spriteHitbox = sprite.effectiveHitbox
    val targetAABB = AABB.fromSprite(target)
    return sweepCollision(
        startX = startX + spriteHitbox.xOffset,
        startY = startY + spriteHitbox.yOffset,
        deltaX = deltaX,
        deltaY = deltaY,
        width = spriteHitbox.width,
        height = spriteHitbox.height,
        target = targetAABB,
    )
}

/**
 * Sweep collision detection for a moving entity against a target entity.
 *
 * Uses the entity's velocity component for movement direction.
 *
 * Usage:
 * ```kotlin
 * val result = sweepCollision(entity = bullet, target = enemy)
 * whenever(result.collided) { enemy.destroy() }
 * ```
 */
fun sweepCollision(entity: Entity, target: Entity): SweepResult {
    val pos =
        entity.positionComponent
            ?: error("Entity '${entity.name}' needs position for sweep collision")
    val hitbox = entity.hitboxComponent?.hitbox ?: Hitbox(0, 0, 8, 8)
    val targetAABB = AABB.fromEntity(target)

    // Check if entity has velocity component
    if (entity.velocityComponent == null) {
        error("Entity '${entity.name}' needs velocity for sweep collision")
    }

    return sweepCollision(
        startX = pos.x + hitbox.xOffset,
        startY = pos.y + hitbox.yOffset,
        deltaX = entity.velX,
        deltaY = entity.velY,
        width = hitbox.width,
        height = hitbox.height,
        target = targetAABB,
    )
}

// =============================================================================
// PRECISE SWEEP COLLISION (Time-of-Impact)
// =============================================================================

/**
 * Precise sweep collision detection using the slab method for ray-AABB intersection.
 *
 * This function calculates the exact time of collision along the movement path, the collision
 * normal (which face was hit), and the contact point. This enables physics-based collision response
 * like bouncing off walls or sliding along surfaces.
 *
 * The slab method works by:
 * 1. For each axis, calculate when the moving box enters and exits the target's slab
 * 2. Entry time is the maximum of the axis entry times
 * 3. Exit time is the minimum of the axis exit times
 * 4. Collision occurs if entry < exit and entry is in [0, 1]
 * 5. Normal is determined by which axis had the later entry time
 *
 * Note: Uses 8.8 fixed-point arithmetic for Game Boy compatibility.
 *
 * @param startX Starting X position of the moving box
 * @param startY Starting Y position of the moving box
 * @param deltaX Movement in X direction (velocity for this frame)
 * @param deltaY Movement in Y direction (velocity for this frame)
 * @param width Width of the moving box
 * @param height Height of the moving box
 * @param target The target AABB to check collision against
 *
 * Usage:
 * ```kotlin
 * val result = sweepCollisionPrecise(
 *     startX = player.x,
 *     startY = player.y,
 *     deltaX = velX,
 *     deltaY = velY,
 *     width = 8,
 *     height = 16,
 *     target = AABB.fromSprite(wall)
 * )
 * whenever(result.collided) {
 *     // Use result.hitTime for position correction
 *     // Use result.normalX/normalY for bounce/slide response
 *     player.x set result.contactX!!
 *     player.y set result.contactY!!
 * }
 * ```
 */
fun sweepCollisionPrecise(
    startX: Expr,
    startY: Expr,
    deltaX: Expr,
    deltaY: Expr,
    width: Int,
    height: Int,
    target: AABB,
): SweepResult {
    // Expand target AABB by the moving box's size (Minkowski sum)
    // This converts the box-box sweep into a point-box sweep
    val expandedLeft = target.left - width
    val expandedRight = target.right
    val expandedTop = target.top - height
    val expandedBottom = target.bottom

    // Calculate entry and exit times for X axis
    // entryX = (expandedLeft - startX) / deltaX  (if moving right)
    // entryX = (expandedRight - startX) / deltaX (if moving left)
    // Use fixed-point: multiply by 256, then divide

    // For X axis entry time (when we start overlapping)
    val distToLeftX = expandedLeft - startX
    val distToRightX = expandedRight - startX

    // Entry time X: if deltaX > 0, entry is at left edge; if deltaX < 0, entry is at right edge
    // We compute entry assuming division, but Game Boy doesn't have efficient division.
    // Instead, we use conditional logic and approximate with fixed-point.

    // Compute entry distances (numerators for time calculation)
    // entryDistX = deltaX >= 0 ? distToLeftX : distToRightX
    val entryDistX =
        Expr(
            IRTernary(
                IRBinary(deltaX.ir, BinaryOp.GTE, IRLiteral(0)),
                distToLeftX.ir,
                distToRightX.ir,
            )
        )

    // exitDistX = deltaX >= 0 ? distToRightX : distToLeftX
    val exitDistX =
        Expr(
            IRTernary(
                IRBinary(deltaX.ir, BinaryOp.GTE, IRLiteral(0)),
                distToRightX.ir,
                distToLeftX.ir,
            )
        )

    // For Y axis
    val distToTopY = expandedTop - startY
    val distToBottomY = expandedBottom - startY

    val entryDistY =
        Expr(
            IRTernary(
                IRBinary(deltaY.ir, BinaryOp.GTE, IRLiteral(0)),
                distToTopY.ir,
                distToBottomY.ir,
            )
        )

    val exitDistY =
        Expr(
            IRTernary(
                IRBinary(deltaY.ir, BinaryOp.GTE, IRLiteral(0)),
                distToBottomY.ir,
                distToTopY.ir,
            )
        )

    // To avoid division, we compare cross-products instead:
    // entryX > entryY  iff  entryDistX * deltaY > entryDistY * deltaX (when both positive)
    // This gets complex with signs, so we use a simplified approach:
    // Compute times as fixed-point 8.8: time = (dist * 256) / delta
    // But since we can't divide efficiently, we use the expanded AABB check for collision
    // and then compute approximate hit time and normal for response.

    // Collision condition: same as sweepCollision but we also need entry < exit
    // For precise timing, we check if the ray enters the box before exiting

    // Calculate signed entry times scaled by delta (to avoid division)
    // entryTimeX * |deltaX| = entryDistX (preserving sign)
    // entryTimeY * |deltaY| = entryDistY

    // For the collision check, we need:
    // max(entryTimeX, entryTimeY) < min(exitTimeX, exitTimeY)
    // and max(entryTimeX, entryTimeY) >= 0
    // and max(entryTimeX, entryTimeY) <= 1

    // Use cross-multiplication to compare times without division:
    // entryTimeX > entryTimeY  iff  entryDistX * absY > entryDistY * absX (same sign velocities)

    // Absolute values of deltas for time comparison
    val absDeltaX =
        Expr(
            IRTernary(
                IRBinary(deltaX.ir, BinaryOp.GTE, IRLiteral(0)),
                deltaX.ir,
                IRBinary(IRLiteral(0), BinaryOp.SUB, deltaX.ir),
            )
        )

    val absDeltaY =
        Expr(
            IRTernary(
                IRBinary(deltaY.ir, BinaryOp.GTE, IRLiteral(0)),
                deltaY.ir,
                IRBinary(IRLiteral(0), BinaryOp.SUB, deltaY.ir),
            )
        )

    // Cross products for time comparison (avoiding division)
    // entryX * |deltaY| vs entryY * |deltaX|
    val entryXScaled = entryDistX * absDeltaY
    val entryYScaled = entryDistY * absDeltaX
    val exitXScaled = exitDistX * absDeltaY
    val exitYScaled = exitDistY * absDeltaX

    // Entry time is max of entry times per axis
    // xEntryLater = entryXScaled >= entryYScaled
    val xEntryLater = entryXScaled isAtLeast entryYScaled

    // Exit time is min of exit times per axis
    // xExitEarlier = exitXScaled <= exitYScaled
    val xExitEarlier = exitXScaled isAtMost exitYScaled

    // Calculate the maximum entry distance (scaled) for hit time calculation
    val maxEntryScaled = Expr(IRTernary(xEntryLater.ir, entryXScaled.ir, entryYScaled.ir))

    // Calculate minimum exit distance (scaled) for collision check
    val minExitScaled = Expr(IRTernary(xExitEarlier.ir, exitXScaled.ir, exitYScaled.ir))

    // Check movement type for proper time calculation
    val hasXMovement = absDeltaX isAbove 0
    val hasYMovement = absDeltaY isAbove 0
    val hasMovement = hasXMovement or hasYMovement
    val hasDiagonalMovement = hasXMovement and hasYMovement

    // Total movement magnitude for scaling hit time to 0-255 (diagonal case)
    val totalDelta = absDeltaX * absDeltaY // Used as common denominator for diagonal

    // Collision conditions:
    // 1. Entry time < Exit time (ray intersects box)
    // 2. Entry time >= 0 (collision is ahead, not behind)
    // 3. Entry time <= total movement (collision is within this frame)
    val entryBeforeExit = maxEntryScaled isBelow minExitScaled
    val entryNotBehind = maxEntryScaled isAtLeast 0

    // For entry <= 1 (total movement), we need different checks based on movement type:
    // - Diagonal: maxEntryScaled <= totalDelta (cross-multiplication)
    // - X-only: entryDistX <= absDeltaX
    // - Y-only: entryDistY <= absDeltaY
    val entryWithinFrameDiagonal = maxEntryScaled isAtMost totalDelta
    val entryWithinFrameXOnly = entryDistX isAtMost absDeltaX
    val entryWithinFrameYOnly = entryDistY isAtMost absDeltaY

    // Select appropriate check based on movement type
    val entryWithinFrame =
        Condition(
            IRTernary(
                hasDiagonalMovement.ir,
                entryWithinFrameDiagonal.ir,
                IRTernary(hasXMovement.ir, entryWithinFrameXOnly.ir, entryWithinFrameYOnly.ir),
            )
        )

    // Combined collision condition
    val collided = hasMovement and entryBeforeExit and entryNotBehind and entryWithinFrame

    // Calculate hit time as 0-255 fixed point
    // Different formulas based on movement type:
    // - Diagonal: hitTime = (maxEntryScaled * 255) / totalDelta
    // - X-only: hitTime = (entryDistX * 255) / absDeltaX
    // - Y-only: hitTime = (entryDistY * 255) / absDeltaY
    // For Game Boy, we'd use a lookup table or bit shifts in the actual generated code
    // Here we express it as an IR expression that the code generator can optimize
    val hitTimeDiagonal =
        IRTernary(
            IRBinary(totalDelta.ir, BinaryOp.EQ, IRLiteral(0)),
            IRLiteral(0),
            IRBinary(
                IRBinary(maxEntryScaled.ir, BinaryOp.MUL, IRLiteral(255)),
                BinaryOp.DIV,
                totalDelta.ir,
            ),
        )

    val hitTimeXOnly =
        IRTernary(
            IRBinary(absDeltaX.ir, BinaryOp.EQ, IRLiteral(0)),
            IRLiteral(0),
            IRBinary(
                IRBinary(entryDistX.ir, BinaryOp.MUL, IRLiteral(255)),
                BinaryOp.DIV,
                absDeltaX.ir,
            ),
        )

    val hitTimeYOnly =
        IRTernary(
            IRBinary(absDeltaY.ir, BinaryOp.EQ, IRLiteral(0)),
            IRLiteral(0),
            IRBinary(
                IRBinary(entryDistY.ir, BinaryOp.MUL, IRLiteral(255)),
                BinaryOp.DIV,
                absDeltaY.ir,
            ),
        )

    val hitTime255 =
        Expr(
            IRTernary(
                hasDiagonalMovement.ir,
                hitTimeDiagonal,
                IRTernary(hasXMovement.ir, hitTimeXOnly, hitTimeYOnly),
            )
        )

    // Normal: based on which axis had the later entry time
    // If X entry was later: normalX = sign(-deltaX), normalY = 0
    // If Y entry was later: normalX = 0, normalY = sign(-deltaY)
    val normalX =
        Expr(
            IRTernary(
                xEntryLater.ir,
                IRTernary(
                    IRBinary(deltaX.ir, BinaryOp.GT, IRLiteral(0)),
                    IRLiteral(-1), // Hit from left
                    IRLiteral(1), // Hit from right
                ),
                IRLiteral(0),
            )
        )

    val normalY =
        Expr(
            IRTernary(
                xEntryLater.ir,
                IRLiteral(0),
                IRTernary(
                    IRBinary(deltaY.ir, BinaryOp.GT, IRLiteral(0)),
                    IRLiteral(-1), // Hit from top
                    IRLiteral(1), // Hit from bottom
                ),
            )
        )

    // Contact point: start position + (delta * hitTime / 255)
    // contactX = startX + (deltaX * hitTime) / 255
    val contactX =
        Expr(
            IRBinary(
                startX.ir,
                BinaryOp.ADD,
                IRBinary(
                    IRBinary(deltaX.ir, BinaryOp.MUL, hitTime255.ir),
                    BinaryOp.DIV,
                    IRLiteral(255),
                ),
            )
        )

    val contactY =
        Expr(
            IRBinary(
                startY.ir,
                BinaryOp.ADD,
                IRBinary(
                    IRBinary(deltaY.ir, BinaryOp.MUL, hitTime255.ir),
                    BinaryOp.DIV,
                    IRLiteral(255),
                ),
            )
        )

    return SweepResult(
        collided = collided,
        hitTime = hitTime255,
        normalX = normalX,
        normalY = normalY,
        contactX = contactX,
        contactY = contactY,
    )
}

/**
 * Precise sweep collision for a sprite against a target sprite.
 *
 * Usage:
 * ```kotlin
 * val result = sweepCollisionPrecise(
 *     startX = player.x,
 *     startY = player.y,
 *     deltaX = velocityX,
 *     deltaY = velocityY,
 *     sprite = player,
 *     target = wall
 * )
 * whenever(result.collided) {
 *     // Bounce off wall using normal
 *     whenever(result.normalX!! isNotEqualTo 0) { velocityX set -velocityX }
 *     whenever(result.normalY!! isNotEqualTo 0) { velocityY set -velocityY }
 * }
 * ```
 */
fun sweepCollisionPrecise(
    startX: Expr,
    startY: Expr,
    deltaX: Expr,
    deltaY: Expr,
    sprite: Sprite,
    target: Sprite,
): SweepResult {
    require(sprite.isBound && target.isBound) {
        "Both sprites must have positions for precise sweep collision"
    }
    val spriteHitbox = sprite.effectiveHitbox
    val targetAABB = AABB.fromSprite(target)
    return sweepCollisionPrecise(
        startX = startX + spriteHitbox.xOffset,
        startY = startY + spriteHitbox.yOffset,
        deltaX = deltaX,
        deltaY = deltaY,
        width = spriteHitbox.width,
        height = spriteHitbox.height,
        target = targetAABB,
    )
}

/**
 * Precise sweep collision for an entity against a target entity.
 *
 * Uses the entity's velocity component for movement direction.
 *
 * Usage:
 * ```kotlin
 * val result = sweepCollisionPrecise(entity = player, target = wall)
 * whenever(result.collided) {
 *     // Stop at contact point
 *     player.x set result.contactX!!
 *     player.y set result.contactY!!
 * }
 * ```
 */
fun sweepCollisionPrecise(entity: Entity, target: Entity): SweepResult {
    val pos =
        entity.positionComponent
            ?: error("Entity '${entity.name}' needs position for precise sweep collision")
    val hitbox = entity.hitboxComponent?.hitbox ?: Hitbox(0, 0, 8, 8)
    val targetAABB = AABB.fromEntity(target)

    if (entity.velocityComponent == null) {
        error("Entity '${entity.name}' needs velocity for precise sweep collision")
    }

    return sweepCollisionPrecise(
        startX = pos.x + hitbox.xOffset,
        startY = pos.y + hitbox.yOffset,
        deltaX = entity.velX,
        deltaY = entity.velY,
        width = hitbox.width,
        height = hitbox.height,
        target = targetAABB,
    )
}

// =============================================================================
// BACKWARD COMPATIBILITY ALIASES
// =============================================================================

/**
 * Alias for [sweepCollision] - the simple expanded AABB sweep collision.
 *
 * Use this when you only need to detect if a collision occurred, without needing precise collision
 * response data (hit time, normal, contact point).
 */
fun sweepCollisionSimple(
    startX: Expr,
    startY: Expr,
    deltaX: Expr,
    deltaY: Expr,
    width: Int,
    height: Int,
    target: AABB,
): SweepResult = sweepCollision(startX, startY, deltaX, deltaY, width, height, target)

/** Alias for [sweepCollision] with sprites - simple expanded AABB approach. */
fun sweepCollisionSimple(
    startX: Expr,
    startY: Expr,
    deltaX: Expr,
    deltaY: Expr,
    sprite: Sprite,
    target: Sprite,
): SweepResult = sweepCollision(startX, startY, deltaX, deltaY, sprite, target)

/** Alias for [sweepCollision] with entities - simple expanded AABB approach. */
fun sweepCollisionSimple(entity: Entity, target: Entity): SweepResult =
    sweepCollision(entity, target)
