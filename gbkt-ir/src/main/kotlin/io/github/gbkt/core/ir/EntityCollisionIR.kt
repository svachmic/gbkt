/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// ENTITY COLLISION IR TYPES
// Configures how entities interact with the player during dungeon exploration.
// Collision modes, shapes, and callback statement lists are stored here and
// consumed by GBDKSystemVisitor to generate entity grid management C code.
// =============================================================================

/**
 * Collision mode for entity-player interaction during exploration.
 *
 * Controls what happens when the player attempts to move onto an entity's tile.
 */
enum class EntityCollisionMode {
    /** Entity acts as wall — movement stops. No callback fired. */
    BLOCK,
    /** Entity has no collision — player walks through freely. */
    PASSTHROUGH,
    /** Movement stops and trigger fires with entity ref. Bump feedback emitted. */
    BLOCK_AND_TRIGGER,
    /** Player moves onto entity tile and trigger fires. Movement continues. */
    OVERLAP_TRIGGER,
    /** Entity is pushed if destination is free. Bump feedback if push blocked. */
    PUSH,
}

/**
 * Direction constraints for pushable entities (used with [EntityCollisionMode.PUSH]).
 *
 * Restricts which directions the player may push an entity.
 */
enum class PushDirection {
    /** Push in any direction the player is moving. */
    ANY,
    /** Push left or right only. */
    HORIZONTAL_ONLY,
    /** Push up or down only. */
    VERTICAL_ONLY,
    /**
     * Push only in the specific directions listed in [EntityCollisionConfig.allowedPushDirections].
     */
    SPECIFIC,
}

/**
 * Collision shape type for entity-player collision detection.
 *
 * Determines whether the collision check uses tile-grid bit-packing or pixel-accurate AABB.
 */
enum class CollisionShape {
    /** Full grid tile collision — checks presence bit in the entity grid for the target tile. */
    TILE,
    /** Pixel-accurate hitbox — performs AABB overlap check using entity's hitbox definition. */
    HITBOX,
}

/**
 * Configuration for an entity's collision behaviour in the exploration system.
 *
 * Stored on [ActorIR.entityCollision]. When null, the actor has no exploration collision (same as
 * [EntityCollisionMode.PASSTHROUGH]).
 *
 * @property mode How the entity responds to player contact.
 * @property shape Whether collision detection uses grid-tile bits or pixel AABB.
 * @property pushDirection Direction constraint for [EntityCollisionMode.PUSH] entities.
 * @property allowedPushDirections Specific allowed directions when [pushDirection] is
 *   [PushDirection.SPECIFIC].
 * @property tilesWide Width of the entity in tiles (multi-tile occupancy in entity grid).
 * @property tilesHigh Height of the entity in tiles (multi-tile occupancy in entity grid).
 * @property onBlockedStatements Script ops executed when player is blocked by this entity
 *   ([EntityCollisionMode.BLOCK_AND_TRIGGER] only). The global [_blocking_entity_id] is set to this
 *   entity's ID before executing these statements.
 * @property onOverlapStatements Script ops executed when player overlaps this entity
 *   ([EntityCollisionMode.OVERLAP_TRIGGER] only).
 * @property onPushedStatements Script ops executed when this entity is pushed
 *   ([EntityCollisionMode.PUSH] only). The globals [_pushed_entity_id] and [_push_direction] are
 *   set before executing these statements.
 * @property bumpFeedback When true, generates bump sound/visual feedback for blocked movement.
 */
data class EntityCollisionConfig(
    val mode: EntityCollisionMode = EntityCollisionMode.BLOCK,
    val shape: CollisionShape = CollisionShape.TILE,
    val pushDirection: PushDirection = PushDirection.ANY,
    val allowedPushDirections: Set<TransitionEdge> = emptySet(),
    val tilesWide: Int = 1,
    val tilesHigh: Int = 1,
    val onBlockedStatements: List<ScriptOp> = emptyList(),
    val onOverlapStatements: List<ScriptOp> = emptyList(),
    val onPushedStatements: List<ScriptOp> = emptyList(),
    val bumpFeedback: Boolean = true,
)
