/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.entity

import io.github.gbkt.core.AnimationRef
import io.github.gbkt.core.TagRef
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.graphics.Hitbox
import io.github.gbkt.core.graphics.Sprite
import io.github.gbkt.core.ir.AssignableExpr
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.GameScopeContext
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRPhysicsApply

// =============================================================================
// POSITION & VELOCITY - Destructuring support
// =============================================================================

/**
 * A position pair for destructuring.
 *
 * Usage: val (px, py) = player.position val (ex, ey) = enemy.position
 */
data class Position(val x: AssignableExpr, val y: AssignableExpr)

/**
 * A velocity pair for destructuring.
 *
 * Usage: val (vx, vy) = player.velocity
 */
data class Velocity(val velX: AssignableExpr, val velY: AssignableExpr)

// =============================================================================
// ENTITY-COMPONENT SYSTEM
// Unified game object abstraction for cleaner DSL
// =============================================================================

/**
 * A game entity with composable components.
 *
 * Usage: val player by entity { position(80, 100) velocity(0, 0) sprite(SpriteAsset("player.png"))
 * { size = 8 x 16 hitbox(0, 0, 8, 16) } states { "idle" { enter { play("idle") } } } }
 *
 * Access: player.x, player.y, player.velY, player.play("run")
 */
class Entity(
    val name: String,
    internal val positionComponent: PositionComponent?,
    internal val velocityComponent: VelocityComponent?,
    internal val spriteComponent: SpriteComponent?,
    internal val hitboxComponent: HitboxComponent?,
    internal val statesComponent: StatesComponent?,
    internal val tagComponent: TagComponent?,
    internal val physicsComponent: PhysicsComponent? = null,
) : Movable {
    // === Position Access ===

    /**
     * X position as assignable expression.
     *
     * @throws IllegalStateException if entity has no position component. Use [xOrNull] for safe
     *   access.
     */
    override val x: AssignableExpr
        get() = positionComponent?.x ?: error("Entity '$name' has no position component")

    /**
     * Y position as assignable expression.
     *
     * @throws IllegalStateException if entity has no position component. Use [yOrNull] for safe
     *   access.
     */
    override val y: AssignableExpr
        get() = positionComponent?.y ?: error("Entity '$name' has no position component")

    /** X position, or null if entity has no position component. */
    val xOrNull: AssignableExpr?
        get() = positionComponent?.x

    /** Y position, or null if entity has no position component. */
    val yOrNull: AssignableExpr?
        get() = positionComponent?.y

    /**
     * Position pair for destructuring.
     *
     * Usage: val (px, py) = player.position
     *
     * @throws IllegalStateException if entity has no position component. Use [positionOrNull] for
     *   safe access.
     */
    val position: Position
        get() = Position(x, y)

    /** Position pair, or null if entity has no position component. */
    val positionOrNull: Position?
        get() = positionComponent?.let { Position(it.x, it.y) }

    // === Velocity Access ===

    /**
     * X velocity as assignable expression.
     *
     * @throws IllegalStateException if entity has no velocity component. Use [velXOrNull] for safe
     *   access.
     */
    override val velX: AssignableExpr
        get() = velocityComponent?.velX ?: error("Entity '$name' has no velocity component")

    /**
     * Y velocity as assignable expression.
     *
     * @throws IllegalStateException if entity has no velocity component. Use [velYOrNull] for safe
     *   access.
     */
    override val velY: AssignableExpr
        get() = velocityComponent?.velY ?: error("Entity '$name' has no velocity component")

    /** X velocity, or null if entity has no velocity component. */
    val velXOrNull: AssignableExpr?
        get() = velocityComponent?.velX

    /** Y velocity, or null if entity has no velocity component. */
    val velYOrNull: AssignableExpr?
        get() = velocityComponent?.velY

    /**
     * Velocity pair for destructuring.
     *
     * Usage: val (vx, vy) = player.velocity
     *
     * @throws IllegalStateException if entity has no velocity component. Use [velocityOrNull] for
     *   safe access.
     */
    val velocity: Velocity
        get() = Velocity(velX, velY)

    /** Velocity pair, or null if entity has no velocity component. */
    val velocityOrNull: Velocity?
        get() = velocityComponent?.let { Velocity(it.velX, it.velY) }

    // === Sprite Access ===

    /** The sprite, if any */
    val sprite: Sprite?
        get() = spriteComponent?.sprite

    /** OAM slot for the sprite */
    val spriteSlot: Int
        get() = sprite?.oamSlot ?: -1

    // === Animation Methods ===

    /**
     * Play an animation on the entity's sprite.
     *
     * @throws IllegalStateException if entity has no sprite. Check [hasSprite] first for safe
     *   usage.
     */
    fun play(ref: AnimationRef, loop: Boolean = true) {
        sprite?.play(ref, loop) ?: error("Entity '$name' has no sprite with animations")
    }

    /**
     * Stop the current animation.
     *
     * @throws IllegalStateException if entity has no sprite. Check [hasSprite] first for safe
     *   usage.
     */
    fun stopAnimation() {
        sprite?.stopAnimation() ?: error("Entity '$name' has no sprite")
    }

    /**
     * Set a specific animation frame.
     *
     * @throws IllegalStateException if entity has no sprite. Check [hasSprite] first for safe
     *   usage.
     */
    fun setFrame(index: Int) {
        sprite?.setFrame(index) ?: error("Entity '$name' has no sprite")
    }

    // === Visibility ===

    /** Show the entity (makes sprite visible) */
    fun show() {
        sprite?.show()
    }

    /** Hide the entity */
    fun hide() {
        sprite?.hide()
    }

    // === State Machine ===

    /**
     * Start the entity's state machine in the specified state.
     *
     * @throws IllegalStateException if entity has no states component. Check [hasStates] first for
     *   safe usage.
     */
    fun startState(stateName: String) {
        statesComponent?.machine?.start(stateName)
            ?: error("Entity '$name' has no states component")
    }

    /**
     * Update the entity's state machine (call every frame).
     *
     * @throws IllegalStateException if entity has no states component. Check [hasStates] first for
     *   safe usage.
     */
    fun updateStates() {
        statesComponent?.machine?.update() ?: error("Entity '$name' has no states component")
    }

    /**
     * Force transition to a specific state.
     *
     * @throws IllegalStateException if entity has no states component. Check [hasStates] first for
     *   safe usage.
     */
    fun gotoState(stateName: String) {
        statesComponent?.machine?.goto(stateName) ?: error("Entity '$name' has no states component")
    }

    /**
     * Check if currently in a specific state.
     *
     * @throws IllegalStateException if entity has no states component. Check [hasStates] first for
     *   safe usage.
     */
    fun isInState(stateName: String): Condition {
        return statesComponent?.machine?.isIn(stateName)
            ?: error("Entity '$name' has no states component")
    }

    /**
     * Check if currently in a specific state, or null if entity has no states component. Safe
     * alternative to [isInState].
     */
    fun isInStateOrNull(stateName: String): Condition? {
        return statesComponent?.machine?.isIn(stateName)
    }

    // === Collision ===

    /**
     * Check collision with another entity using AABB detection.
     *
     * Usage: whenever(player collidesWith enemy) { scene("gameover") }
     */
    infix fun collidesWith(other: Entity): Condition {
        val thisSprite = this.sprite
        val otherSprite = other.sprite

        // If both have sprites, use sprite collision
        if (thisSprite != null && otherSprite != null) {
            return thisSprite collidesWith otherSprite
        }

        // Fall back to position-based collision
        return positionBasedCollision(other)
    }

    private fun positionBasedCollision(other: Entity): Condition {
        val pos1 =
            this.positionComponent ?: error("Entity '${this.name}' needs position for collision")
        val pos2 =
            other.positionComponent ?: error("Entity '${other.name}' needs position for collision")

        val h1 = this.hitboxComponent?.hitbox ?: Hitbox(0, 0, 8, 8)
        val h2 = other.hitboxComponent?.hitbox ?: Hitbox(0, 0, 8, 8)

        // AABB collision
        val left1 = pos1.x + h1.xOffset
        val top1 = pos1.y + h1.yOffset
        val right1 = left1 + h1.width
        val bottom1 = top1 + h1.height

        val left2 = pos2.x + h2.xOffset
        val top2 = pos2.y + h2.yOffset
        val right2 = left2 + h2.width
        val bottom2 = top2 + h2.height

        return (right1 isAbove left2) and
            (left1 isBelow right2) and
            (bottom1 isAbove top2) and
            (top1 isBelow bottom2)
    }

    /**
     * Check collision with any entity tagged with the given tag.
     *
     * Usage:
     * ```kotlin
     * val enemyTag = tag("enemy")
     * whenever(player collidesWithAny enemyTag) { scene(gameoverScene) }
     * ```
     */
    infix fun collidesWithAny(tagRef: TagRef): Condition {
        val registry =
            GameScopeContext.current?.let { scope -> (scope as? GameBuilder)?.entityRegistry }
                ?: error("collidesWithAny requires GameBuilder scope")

        val tagged = registry.tagged(tagRef.name)
        return tagged.map { this collidesWith it }.reduceOrNull { acc, cond -> acc or cond }
            ?: Condition(IRLiteral(0)) // false if no entities with tag
    }

    // === Tags ===

    /** Get all tags on this entity */
    val tags: Set<String>
        get() = tagComponent?.tags ?: emptySet()

    /** Check if entity has a specific tag */
    fun hasTag(tagRef: TagRef): Boolean = tagRef.name in tags

    // === Physics ===

    /**
     * Apply physics to this entity (gravity, friction, velocity clamping). Call this in every.frame
     * to update the entity's position based on physics.
     *
     * Usage:
     * ```kotlin
     * every.frame {
     *     player.applyPhysics()
     * }
     * ```
     *
     * Physics operations per frame:
     * 1. Add gravity to velocityY
     * 2. Multiply velocityX by friction
     * 3. Clamp velocity to maxVelocity bounds
     * 4. Update position from velocity
     *
     * @throws IllegalStateException if entity has no physics component. Check [hasPhysics] first
     *   for safe usage.
     */
    fun applyPhysics() {
        if (physicsComponent == null) {
            error(
                "Entity '$name' has no physics component. Add physics { } to the entity definition."
            )
        }
        RecordingContext.require().emit(IRPhysicsApply(name))
    }

    // === Capability Checks ===

    val hasPosition: Boolean
        get() = positionComponent != null

    val hasVelocity: Boolean
        get() = velocityComponent != null

    val hasSprite: Boolean
        get() = spriteComponent != null

    val hasHitbox: Boolean
        get() = hitboxComponent != null || spriteComponent?.sprite?.hasHitbox == true

    val hasStates: Boolean
        get() = statesComponent != null

    val hasTags: Boolean
        get() = tagComponent != null && tagComponent.tags.isNotEmpty()

    val hasPhysics: Boolean
        get() = physicsComponent != null
}

// =============================================================================
// ENTITY EXTENSIONS
// =============================================================================

/**
 * Configure an entity with multiple operations in a single scope.
 *
 * Usage: player.configure { x set 80 y set 100 play("idle") }
 *
 * @return The entity, for chaining.
 */
inline fun Entity.configure(block: Entity.() -> Unit): Entity {
    block()
    return this
}
