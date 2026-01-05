/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.PathOptions

// =============================================================================
// IR NODES FOR PATHFINDING SYSTEM
// =============================================================================

// --- Navigation Grid IR ---

/** Initialize a navigation grid from compile-time data */
data class IRNavGridInit(
    val gridName: String,
    val width: Int,
    val height: Int,
    val walkableData: BooleanArray,
) : IRStatement {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IRNavGridInit) return false
        return gridName == other.gridName &&
            width == other.width &&
            height == other.height &&
            walkableData.contentEquals(other.walkableData)
    }

    override fun hashCode(): Int {
        var result = gridName.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + walkableData.contentHashCode()
        return result
    }
}

/** Set a single tile's walkability at runtime */
data class IRNavGridSetTile(
    val gridName: String,
    val x: IRExpression,
    val y: IRExpression,
    val walkable: Boolean,
) : IRStatement

/** Set a single tile's movement weight at runtime */
data class IRNavGridSetWeight(
    val gridName: String,
    val x: IRExpression,
    val y: IRExpression,
    val weight: Int,
) : IRStatement

// --- Pathfinding IR ---

/** Execute A* pathfinding */
data class IRPathFind(
    val pathName: String,
    val gridName: String,
    val startX: IRExpression,
    val startY: IRExpression,
    val endX: IRExpression,
    val endY: IRExpression,
    val options: PathOptions,
) : IRStatement

/** Advance to the next waypoint */
data class IRPathAdvance(val pathName: String) : IRStatement

/** Reset path to beginning */
data class IRPathReset(val pathName: String) : IRStatement

/** Automatic path following */
data class IRPathFollow(
    val pathName: String,
    val entityXVar: String,
    val entityYVar: String,
    val speed: Int,
    val onComplete: List<IRStatement>,
    val onBlocked: List<IRStatement>,
) : IRStatement

// --- Path Expression IR ---

/** Check if path was found */
data class IRPathFound(val pathName: String) : IRExpression

/** Check if path has more waypoints */
data class IRPathHasNext(val pathName: String) : IRExpression

/** Get next waypoint X (in tiles) */
data class IRPathNextX(val pathName: String) : IRExpression

/** Get next waypoint Y (in tiles) */
data class IRPathNextY(val pathName: String) : IRExpression

/** Get path length */
data class IRPathLength(val pathName: String) : IRExpression

/** Get current waypoint index */
data class IRPathCurrentIndex(val pathName: String) : IRExpression

/** Get direction to next waypoint (-1, 0, or 1) */
data class IRPathDirectionX(val pathName: String, val currentX: IRExpression) : IRExpression

data class IRPathDirectionY(val pathName: String, val currentY: IRExpression) : IRExpression

/** Check if entity is at current waypoint */
data class IRPathAtWaypoint(
    val pathName: String,
    val entityX: IRExpression,
    val entityY: IRExpression,
    val threshold: Int,
) : IRExpression

/** Check if tile is walkable */
data class IRNavGridIsWalkable(val gridName: String, val x: IRExpression, val y: IRExpression) :
    IRExpression

// --- Pool Pathfinding IR ---

/** Set pathfinding target for pool entity */
data class IRPoolPathSetTarget(
    val poolName: String,
    val targetX: IRExpression,
    val targetY: IRExpression,
) : IRStatement

/** Update pathfinding and move along path for pool entity */
data class IRPoolPathFollow(val poolName: String) : IRStatement

/** Recalculate path for pool entity */
data class IRPoolPathRecalc(val poolName: String, val entityIndex: String) : IRStatement

/** Check if pool entity is at target */
data class IRPoolPathAtTarget(val poolName: String, val entityIndex: String) : IRExpression
