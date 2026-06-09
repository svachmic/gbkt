/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.CollisionShape
import io.github.gbkt.core.ir.EntityCollisionConfig
import io.github.gbkt.core.ir.EntityCollisionMode
import io.github.gbkt.core.ir.PushDirection
import io.github.gbkt.core.ir.TransitionEdge

// =============================================================================
// ENTITY COLLISION BUILDER
// DSL entry point for per-actor exploration collision configuration.
// Usage:
//   val boulder by actor {
//       position(64, 64)
//       entityCollision {
//           mode(EntityCollisionMode.PUSH)
//           shape(CollisionShape.TILE)
//           pushDirection(PushDirection.ANY)
//           onPushed { soundEffect("push") }
//           bumpFeedback(true)
//       }
//   }
// =============================================================================

/**
 * Builder for per-actor exploration collision configuration.
 *
 * Configures how this actor interacts with the player during dungeon exploration — whether the
 * player can walk through it, is blocked, can push it, or receives a trigger callback.
 *
 * Used inside `actor { entityCollision { ... } }` blocks. The built [EntityCollisionConfig] is
 * stored on [io.github.gbkt.core.ir.ActorIR.entityCollision] and used by the backend to generate
 * entity collision grid management functions in `exploration_move()`.
 *
 * ```kotlin
 * val npc by actor {
 *     position(40, 40)
 *     entityCollision {
 *         mode(EntityCollisionMode.BLOCK_AND_TRIGGER)
 *         onBlocked { navigate(dialogScene) }
 *     }
 * }
 * val boulder by actor {
 *     position(80, 80)
 *     entityCollision {
 *         mode(EntityCollisionMode.PUSH)
 *         pushDirection(PushDirection.ANY)
 *         onPushed { soundEffect("push_sound") }
 *     }
 * }
 * ```
 */
@GbktDsl
class EntityCollisionBuilder {
    private var mode: EntityCollisionMode = EntityCollisionMode.BLOCK
    private var shape: CollisionShape = CollisionShape.TILE
    private var pushDir: PushDirection = PushDirection.ANY
    private var allowedDirs: Set<TransitionEdge> = emptySet()
    private var tilesW: Int = 1
    private var tilesH: Int = 1
    private var onBlockedCallback: (ScriptBuilder.() -> Unit)? = null
    private var onOverlapCallback: (ScriptBuilder.() -> Unit)? = null
    private var onPushedCallback: (ScriptBuilder.() -> Unit)? = null
    private var bump: Boolean = true

    /**
     * Sets the collision mode for player-entity interaction.
     *
     * Default: [EntityCollisionMode.BLOCK] — entity acts as an impassable wall.
     */
    fun mode(m: EntityCollisionMode) {
        mode = m
    }

    /**
     * Sets the collision shape used for detection.
     *
     * [CollisionShape.TILE] uses the entity grid bit for fast O(1) lookup. [CollisionShape.HITBOX]
     * uses AABB pixel overlap for precise hitbox-based collision.
     *
     * Default: [CollisionShape.TILE].
     */
    fun shape(s: CollisionShape) {
        shape = s
    }

    /**
     * Sets the push direction constraint for [EntityCollisionMode.PUSH] entities.
     *
     * Default: [PushDirection.ANY] — the entity can be pushed in all directions.
     */
    fun pushDirection(d: PushDirection) {
        pushDir = d
    }

    /**
     * Specifies the allowed push directions when [pushDirection] is [PushDirection.SPECIFIC].
     *
     * Example: `allowDirections(TransitionEdge.NORTH, TransitionEdge.SOUTH)`
     */
    fun allowDirections(vararg dirs: TransitionEdge) {
        allowedDirs = dirs.toSet()
    }

    /**
     * Sets the entity's tile footprint for multi-tile occupancy in the entity grid.
     *
     * Multi-tile entities occupy all [wide] x [high] tiles in the grid when registered. Default:
     * 1x1 (single tile).
     */
    fun tiles(wide: Int, high: Int) {
        tilesW = wide
        tilesH = high
    }

    /**
     * Registers a callback fired when the player is blocked by this entity.
     *
     * Only applies to [EntityCollisionMode.BLOCK_AND_TRIGGER]. The global `_blocking_entity_id` is
     * set to this entity's runtime ID before execution.
     */
    fun onBlocked(block: ScriptBuilder.() -> Unit) {
        onBlockedCallback = block
    }

    /**
     * Registers a callback fired when the player overlaps this entity.
     *
     * Only applies to [EntityCollisionMode.OVERLAP_TRIGGER]. Movement continues after the callback
     * (the player moves onto the tile).
     */
    fun onOverlap(block: ScriptBuilder.() -> Unit) {
        onOverlapCallback = block
    }

    /**
     * Registers a callback fired when this entity is pushed.
     *
     * Only applies to [EntityCollisionMode.PUSH]. The globals `_pushed_entity_id` and
     * `_push_direction` are set before execution.
     */
    fun onPushed(block: ScriptBuilder.() -> Unit) {
        onPushedCallback = block
    }

    /**
     * Enables or disables bump feedback (sound/visual) for blocked movement.
     *
     * Default: true — bump feedback generated.
     */
    fun bumpFeedback(enabled: Boolean) {
        bump = enabled
    }

    internal fun build(): EntityCollisionConfig =
        EntityCollisionConfig(
            mode = mode,
            shape = shape,
            pushDirection = pushDir,
            allowedPushDirections = allowedDirs,
            tilesWide = tilesW,
            tilesHigh = tilesH,
            onBlockedStatements = onBlockedCallback?.let { recordStatements(it) } ?: emptyList(),
            onOverlapStatements = onOverlapCallback?.let { recordStatements(it) } ?: emptyList(),
            onPushedStatements = onPushedCallback?.let { recordStatements(it) } ?: emptyList(),
            bumpFeedback = bump,
        )
}
