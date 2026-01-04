/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.entity.Entity
import io.github.gbkt.core.graphics.FrameTiming
import io.github.gbkt.core.graphics.TileMap
import io.github.gbkt.core.graphics.frames
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.Dimensions
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IRBinary
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRNavGridIsWalkable
import io.github.gbkt.core.ir.IRNavGridSetTile
import io.github.gbkt.core.ir.IRNavGridSetWeight
import io.github.gbkt.core.ir.IRPathAdvance
import io.github.gbkt.core.ir.IRPathAtWaypoint
import io.github.gbkt.core.ir.IRPathCurrentIndex
import io.github.gbkt.core.ir.IRPathDirectionX
import io.github.gbkt.core.ir.IRPathDirectionY
import io.github.gbkt.core.ir.IRPathFind
import io.github.gbkt.core.ir.IRPathFollow
import io.github.gbkt.core.ir.IRPathFound
import io.github.gbkt.core.ir.IRPathHasNext
import io.github.gbkt.core.ir.IRPathLength
import io.github.gbkt.core.ir.IRPathNextX
import io.github.gbkt.core.ir.IRPathNextY
import io.github.gbkt.core.ir.IRPathReset
import io.github.gbkt.core.ir.IRStatement

// =============================================================================
// A* PATHFINDING - Optimized for tile grids
// =============================================================================

/** Heuristic function for A* pathfinding. */
enum class Heuristic {
    /** Manhattan distance: |dx| + |dy| - best for 4-way movement */
    MANHATTAN,
    /** Chebyshev distance: max(|dx|, |dy|) - best for 8-way movement */
    CHEBYSHEV,
    /** Euclidean distance: sqrt(dx^2 + dy^2) - most accurate but slower */
    EUCLIDEAN
}

/** Configuration options for pathfinding queries. */
data class PathOptions(
    val diagonal: Boolean = false,
    val maxDepth: Int = 64,
    val heuristic: Heuristic = Heuristic.MANHATTAN
)

/** Builder for configuring pathfinding options. */
class PathOptionsBuilder {
    var diagonal: Boolean = false
    var maxDepth: Int = 64
    var heuristic: Heuristic = Heuristic.MANHATTAN

    internal fun build() = PathOptions(diagonal, maxDepth, heuristic)
}

// =============================================================================
// NAVIGATION GRID - Defines walkable/blocked tiles
// =============================================================================

/**
 * A navigation grid defining which tiles are walkable for pathfinding.
 *
 * ## Basic Usage
 *
 * ```kotlin
 * // Manual definition
 * val navGrid = navGrid("arena") {
 *     size = 16 x 16
 *     walkable(2..14, 2..14)
 *     blocked(8, 8)
 * }
 *
 * // From tilemap
 * val navGrid = navGrid(from = dungeonMap) {
 *     blockedTiles(0, 1, 2)  // Wall tile indices
 * }
 * ```
 *
 * ## Weighted Tiles
 * Tiles can have different movement costs for pathfinding:
 * ```kotlin
 * val navGrid = navGrid("dungeon") {
 *     size = 16 x 16
 *     default = true
 *     // Swamp tiles are slow
 *     weight(4..8, 4..8, cost = 3)
 *     // Road is fast
 *     weight(0..15, 8, cost = 1)
 *     // Impassable walls
 *     blocked(0..15, 0)
 *     blocked(0..15, 15)
 * }
 * ```
 *
 * ## Dynamic Obstacles
 * Obstacles can be added/removed at runtime:
 * ```kotlin
 * every.frame {
 *     // Block tile where enemy is standing
 *     navGrid.setBlocked(enemy.x / 8, enemy.y / 8)
 *     // Later, clear it
 *     navGrid.setWalkable(enemy.x / 8, enemy.y / 8)
 *     // Or set a custom weight
 *     navGrid.setWeight(x, y, cost = 5)
 * }
 * ```
 */
class NavGrid(
    val name: String,
    val width: Int,
    val height: Int,
    internal val walkableData: BooleanArray,
    internal val weightData: IntArray, // 0 = blocked, 1+ = movement cost
    internal val sourceMap: TileMap?
) {
    /** Whether this grid uses weighted pathfinding. */
    val hasWeights: Boolean = weightData.any { it > 1 }

    init {
        require(width > 0 && height > 0) { "NavGrid dimensions must be positive" }
        require(width <= 32 && height <= 32) { "NavGrid max size is 32x32 tiles" }
        require(walkableData.size == width * height) {
            "walkableData size (${walkableData.size}) must match dimensions ($width x $height = ${width * height})"
        }
        require(weightData.size == width * height) { "weightData size must match dimensions" }
    }

    /** Check if a tile is walkable at compile time. */
    fun isWalkableAt(x: Int, y: Int): Boolean {
        if (x < 0 || x >= width || y < 0 || y >= height) return false
        return walkableData[y * width + x]
    }

    /** Get the movement cost of a tile at compile time. */
    fun weightAt(x: Int, y: Int): Int {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0
        return weightData[y * width + x]
    }

    /** Runtime check if a tile is walkable (returns a Condition for use in whenever blocks). */
    fun isWalkable(x: Expr, y: Expr): Condition {
        return Condition(IRNavGridIsWalkable(name, x.ir, y.ir))
    }

    fun isWalkable(x: Int, y: Int): Condition {
        return Condition(IRNavGridIsWalkable(name, IRLiteral(x), IRLiteral(y)))
    }

    /** Set a tile as blocked at runtime (dynamic obstacle). */
    fun setBlocked(x: Expr, y: Expr) {
        RecordingContext.require().emit(IRNavGridSetTile(name, x.ir, y.ir, walkable = false))
    }

    fun setBlocked(x: Int, y: Int) {
        RecordingContext.require()
            .emit(IRNavGridSetTile(name, IRLiteral(x), IRLiteral(y), walkable = false))
    }

    /** Set a tile as walkable at runtime (remove dynamic obstacle). */
    fun setWalkable(x: Expr, y: Expr) {
        RecordingContext.require().emit(IRNavGridSetTile(name, x.ir, y.ir, walkable = true))
    }

    fun setWalkable(x: Int, y: Int) {
        RecordingContext.require()
            .emit(IRNavGridSetTile(name, IRLiteral(x), IRLiteral(y), walkable = true))
    }

    /**
     * Set the movement weight of a tile at runtime.
     *
     * @param x Tile X coordinate
     * @param y Tile Y coordinate
     * @param cost Movement cost (1 = normal, 2+ = slow, 0 = blocked)
     */
    fun setWeight(x: Expr, y: Expr, cost: Int) {
        require(cost >= 0 && cost <= 255) { "Weight must be 0-255" }
        RecordingContext.require().emit(IRNavGridSetWeight(name, x.ir, y.ir, cost))
    }

    fun setWeight(x: Int, y: Int, cost: Int) {
        require(cost >= 0 && cost <= 255) { "Weight must be 0-255" }
        RecordingContext.require().emit(IRNavGridSetWeight(name, IRLiteral(x), IRLiteral(y), cost))
    }

    /**
     * Add a dynamic obstacle at entity position. Convenience method that converts pixel coordinates
     * to tile coordinates.
     */
    fun addObstacle(entity: Entity) {
        val tileX = Expr(IRBinary(entity.x.ir, BinaryOp.SHR, IRLiteral(3)))
        val tileY = Expr(IRBinary(entity.y.ir, BinaryOp.SHR, IRLiteral(3)))
        setBlocked(tileX, tileY)
    }

    /**
     * Remove a dynamic obstacle at entity position. Convenience method that converts pixel
     * coordinates to tile coordinates.
     */
    fun removeObstacle(entity: Entity) {
        val tileX = Expr(IRBinary(entity.x.ir, BinaryOp.SHR, IRLiteral(3)))
        val tileY = Expr(IRBinary(entity.y.ir, BinaryOp.SHR, IRLiteral(3)))
        setWalkable(tileX, tileY)
    }
}

/** Builder for manual NavGrid definition. */
class NavGridBuilder(private val name: String) {
    private var _width: Int = 0
    private var _height: Int = 0
    private var defaultWalkable: Boolean = true
    private var defaultWeight: Int = 1
    private val operations = mutableListOf<GridOperation>()

    private sealed class GridOperation {
        data class SetTile(val x: Int, val y: Int, val walkable: Boolean) : GridOperation()

        data class SetRect(
            val x1: Int,
            val y1: Int,
            val x2: Int,
            val y2: Int,
            val walkable: Boolean
        ) : GridOperation()

        data class SetWeight(val x: Int, val y: Int, val cost: Int) : GridOperation()

        data class SetWeightRect(
            val x1: Int,
            val y1: Int,
            val x2: Int,
            val y2: Int,
            val cost: Int
        ) : GridOperation()
    }

    /** Set the grid size in tiles. Usage: size = 16 x 16 */
    var size: Dimensions
        get() = Dimensions(_width, _height)
        set(value) {
            _width = value.width
            _height = value.height
        }

    /** Set whether tiles are walkable by default. */
    var default: Boolean
        get() = defaultWalkable
        set(value) {
            defaultWalkable = value
        }

    /** Mark a single tile as walkable. */
    fun walkable(x: Int, y: Int) {
        operations.add(GridOperation.SetTile(x, y, true))
    }

    /** Mark a rectangular region as walkable. */
    fun walkable(xRange: IntRange, yRange: IntRange) {
        operations.add(
            GridOperation.SetRect(xRange.first, yRange.first, xRange.last, yRange.last, true)
        )
    }

    /** Mark a single tile as blocked. */
    fun blocked(x: Int, y: Int) {
        operations.add(GridOperation.SetTile(x, y, false))
    }

    /** Mark a rectangular region as blocked. */
    fun blocked(xRange: IntRange, yRange: IntRange) {
        operations.add(
            GridOperation.SetRect(xRange.first, yRange.first, xRange.last, yRange.last, false)
        )
    }

    /**
     * Set the movement weight (cost) for a single tile.
     *
     * @param x Tile X coordinate
     * @param y Tile Y coordinate
     * @param cost Movement cost (1 = normal, 2+ = slow terrain, 0 = blocked)
     *
     * Example:
     * ```kotlin
     * weight(5, 5, cost = 3)  // Tile at (5,5) costs 3x normal to traverse
     * ```
     */
    fun weight(x: Int, y: Int, cost: Int) {
        require(cost >= 0 && cost <= 255) { "Weight must be 0-255" }
        operations.add(GridOperation.SetWeight(x, y, cost))
    }

    /**
     * Set the movement weight (cost) for a rectangular region.
     *
     * @param xRange X coordinate range
     * @param yRange Y coordinate range
     * @param cost Movement cost (1 = normal, 2+ = slow terrain, 0 = blocked)
     *
     * Example:
     * ```kotlin
     * // Swamp area is slow
     * weight(4..8, 4..8, cost = 3)
     * // Road is fast
     * weight(0..15, 8..8, cost = 1)
     * ```
     */
    fun weight(xRange: IntRange, yRange: IntRange, cost: Int) {
        require(cost >= 0 && cost <= 255) { "Weight must be 0-255" }
        operations.add(
            GridOperation.SetWeightRect(xRange.first, yRange.first, xRange.last, yRange.last, cost)
        )
    }

    /**
     * Set the movement weight for a single row.
     *
     * @param xRange X coordinate range
     * @param y Y coordinate
     * @param cost Movement cost
     */
    fun weight(xRange: IntRange, y: Int, cost: Int) {
        weight(xRange, y..y, cost)
    }

    internal fun build(): NavGrid {
        require(_width > 0 && _height > 0) { "NavGrid size must be set" }

        val walkableData = BooleanArray(_width * _height) { defaultWalkable }
        val weightData = IntArray(_width * _height) { if (defaultWalkable) defaultWeight else 0 }

        for (op in operations) {
            when (op) {
                is GridOperation.SetTile -> {
                    if (op.x in 0 until _width && op.y in 0 until _height) {
                        val idx = op.y * _width + op.x
                        walkableData[idx] = op.walkable
                        if (!op.walkable) weightData[idx] = 0
                    }
                }
                is GridOperation.SetRect -> {
                    for (y in op.y1..op.y2) {
                        for (x in op.x1..op.x2) {
                            if (x in 0 until _width && y in 0 until _height) {
                                val idx = y * _width + x
                                walkableData[idx] = op.walkable
                                if (!op.walkable) weightData[idx] = 0
                            }
                        }
                    }
                }
                is GridOperation.SetWeight -> {
                    if (op.x in 0 until _width && op.y in 0 until _height) {
                        val idx = op.y * _width + op.x
                        weightData[idx] = op.cost
                        walkableData[idx] = op.cost > 0
                    }
                }
                is GridOperation.SetWeightRect -> {
                    for (y in op.y1..op.y2) {
                        for (x in op.x1..op.x2) {
                            if (x in 0 until _width && y in 0 until _height) {
                                val idx = y * _width + x
                                weightData[idx] = op.cost
                                walkableData[idx] = op.cost > 0
                            }
                        }
                    }
                }
            }
        }

        return NavGrid(name, _width, _height, walkableData, weightData, null)
    }
}

/** Builder for NavGrid derived from a TileMap. */
class NavGridFromTileMapBuilder(private val name: String, private val tileMap: TileMap) {
    private var collisionLayerName: String? = null
    private val blockedTileIndices = mutableSetOf<Int>()
    private val walkableTileIndices = mutableSetOf<Int>()
    private var useWalkableList = false
    private val tileWeights = mutableMapOf<Int, Int>() // tile index -> movement cost

    /**
     * Specify a collision layer from the Tiled map. Tiles with any value in this layer are
     * considered blocked.
     */
    var collisionLayer: String?
        get() = collisionLayerName
        set(value) {
            collisionLayerName = value
        }

    /** Mark specific tile indices as blocked. All other tiles are walkable. */
    fun blockedTiles(vararg indices: Int) {
        blockedTileIndices.addAll(indices.toList())
        useWalkableList = false
    }

    /** Mark specific tile indices as walkable. All other tiles are blocked. */
    fun walkableTiles(vararg indices: Int) {
        walkableTileIndices.addAll(indices.toList())
        useWalkableList = true
    }

    /**
     * Set movement weights for specific tile indices.
     *
     * @param weights Pairs of (tile index, movement cost)
     *
     * Example:
     * ```kotlin
     * navGrid(from = dungeonMap) {
     *     blockedTiles(0, 1)  // Walls
     *     tileWeights(
     *         3 to 1,  // Floor is normal
     *         4 to 2,  // Grass is slow
     *         5 to 3,  // Swamp is very slow
     *         6 to 1   // Road is fast
     *     )
     * }
     * ```
     */
    fun tileWeights(vararg weights: Pair<Int, Int>) {
        for ((tileIndex, cost) in weights) {
            require(cost >= 0 && cost <= 255) { "Weight must be 0-255" }
            tileWeights[tileIndex] = cost
        }
    }

    internal fun build(): NavGrid {
        val width = tileMap.widthInTiles
        val height = tileMap.heightInTiles
        val walkableData = BooleanArray(width * height)
        val weightData = IntArray(width * height) { 1 } // Default weight = 1

        // Get tile data from tilemap
        val tileData = tileMap.tileData

        // Build walkability and weights based on configuration
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val tileIndex = if (idx < tileData.size) tileData[idx] else 0

                val isWalkable =
                    when {
                        collisionLayerName != null -> {
                            // Check collision layer (tiles with value > 0 are blocked)
                            val collisionData = tileMap.getLayerData(collisionLayerName!!)
                            if (collisionData != null && idx < collisionData.size) {
                                collisionData[idx] == 0
                            } else {
                                true // Default walkable if no collision data
                            }
                        }
                        useWalkableList -> tileIndex in walkableTileIndices
                        blockedTileIndices.isNotEmpty() -> tileIndex !in blockedTileIndices
                        else -> true // Default walkable
                    }

                walkableData[idx] = isWalkable

                // Apply tile weight if configured
                if (isWalkable && tileIndex in tileWeights) {
                    weightData[idx] = tileWeights[tileIndex]!!
                } else if (!isWalkable) {
                    weightData[idx] = 0
                }
            }
        }

        return NavGrid(name, width, height, walkableData, weightData, tileMap)
    }
}

// =============================================================================
// PATH - Result of pathfinding query
// =============================================================================

/**
 * Represents a computed path from pathfinding.
 *
 * Usage:
 * ```kotlin
 * val path = player findPathTo treasure using navGrid
 *
 * whenever(path.found and path.hasNext) {
 *     player.x += path.directionX(player.x)
 *     player.y += path.directionY(player.y)
 *     whenever(path.atWaypoint(player)) {
 *         path.advance()
 *     }
 * }
 * ```
 */
class Path internal constructor(val name: String, internal val gridName: String) {
    /** Whether a valid path was found. */
    val found: Condition
        get() = Condition(IRPathFound(name))

    /** Whether a valid path was NOT found. */
    val notFound: Condition
        get() = !found

    /** Whether there are more waypoints in the path. */
    val hasNext: Condition
        get() = Condition(IRPathHasNext(name))

    /** The X coordinate of the next waypoint (in tiles). */
    val nextX: Expr
        get() = Expr(IRPathNextX(name))

    /** The Y coordinate of the next waypoint (in tiles). */
    val nextY: Expr
        get() = Expr(IRPathNextY(name))

    /** Total length of the path in tiles. */
    val length: Expr
        get() = Expr(IRPathLength(name))

    /** Current waypoint index. */
    val currentIndex: Expr
        get() = Expr(IRPathCurrentIndex(name))

    /**
     * Get the direction to move toward the next waypoint (-1, 0, or 1).
     *
     * @param currentX Current X position in pixels
     */
    fun directionX(currentX: Expr): Expr {
        return Expr(IRPathDirectionX(name, currentX.ir))
    }

    fun directionX(currentX: Int): Expr {
        return Expr(IRPathDirectionX(name, IRLiteral(currentX)))
    }

    /**
     * Get the direction to move toward the next waypoint (-1, 0, or 1).
     *
     * @param currentY Current Y position in pixels
     */
    fun directionY(currentY: Expr): Expr {
        return Expr(IRPathDirectionY(name, currentY.ir))
    }

    fun directionY(currentY: Int): Expr {
        return Expr(IRPathDirectionY(name, IRLiteral(currentY)))
    }

    /** Check if an entity is at the current waypoint (within threshold). */
    fun atWaypoint(entity: Entity, threshold: Int = 4): Condition {
        return Condition(IRPathAtWaypoint(name, entity.x.ir, entity.y.ir, threshold))
    }

    /** Advance to the next waypoint. */
    fun advance() {
        RecordingContext.require().emit(IRPathAdvance(name))
    }

    /** Reset the path to the beginning. */
    fun reset() {
        RecordingContext.require().emit(IRPathReset(name))
    }
}

// =============================================================================
// PATH QUERY - Fluent infix syntax
// =============================================================================

/**
 * Intermediate object for infix path query syntax. Enables: player findPathTo treasure using
 * navGrid
 */
class PathQuery
internal constructor(
    private val pathName: String,
    private val startXExpr: IRExpression,
    private val startYExpr: IRExpression,
    private val endXExpr: IRExpression,
    private val endYExpr: IRExpression
) {
    /** Complete the path query with a navigation grid. */
    infix fun using(navGrid: NavGrid): Path {
        return using(navGrid) {}
    }

    /** Complete the path query with a navigation grid and options. */
    fun using(navGrid: NavGrid, configure: PathOptionsBuilder.() -> Unit): Path {
        val options = PathOptionsBuilder().apply(configure).build()
        val path = Path(pathName, navGrid.name)

        RecordingContext.require()
            .emit(
                IRPathFind(
                    pathName = pathName,
                    gridName = navGrid.name,
                    startX = startXExpr,
                    startY = startYExpr,
                    endX = endXExpr,
                    endY = endYExpr,
                    options = options
                )
            )

        return path
    }
}

/** Wrapper for path query with configuration. */
class ConfiguredPathQuery
internal constructor(private val query: PathQuery, private val navGrid: NavGrid) {
    /** Configure path options. */
    infix fun configure(init: PathOptionsBuilder.() -> Unit): Path {
        return query.using(navGrid, init)
    }
}

// Allow chaining: path using navGrid configure { diagonal = true }
infix fun Path.configure(init: PathOptionsBuilder.() -> Unit): Path {
    // For post-configuration, we would need to re-emit the IR
    // For now, configuration must happen during the query
    return this
}

// =============================================================================
// PATH FOLLOWING
// =============================================================================

/** Configuration for automatic path following. */
class PathFollowBuilder {
    var speed: Int = 1
    private var _onArrive: List<IRStatement> = emptyList()
    private var _onBlocked: List<IRStatement> = emptyList()

    internal val onArriveStatements: List<IRStatement>
        get() = _onArrive

    internal val onBlockedStatements: List<IRStatement>
        get() = _onBlocked

    /** Called when the entity reaches the destination. */
    fun onArrive(block: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, block)
        _onArrive = recorder.statements
    }

    /** Called when the path is blocked or unreachable. */
    fun onBlocked(block: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, block)
        _onBlocked = recorder.statements
    }

    internal fun build() = PathFollowConfig(speed, _onArrive, _onBlocked)
}

data class PathFollowConfig(
    val speed: Int,
    val onArrive: List<IRStatement>,
    val onBlocked: List<IRStatement>
)

// =============================================================================
// POOL PATHFINDING CONFIGURATION
// =============================================================================

/** Pathfinding configuration for entity pools. */
class PoolPathfindingBuilder {
    var navGrid: NavGrid? = null
    var updateInterval: FrameTiming = 60.frames
    var maxDepth: Int = 32
    var diagonal: Boolean = false

    internal fun build(): PoolPathfindingConfig {
        requireNotNull(navGrid) { "Pool pathfinding requires a navGrid" }
        return PoolPathfindingConfig(navGrid!!, updateInterval, maxDepth, diagonal)
    }
}

data class PoolPathfindingConfig(
    val navGrid: NavGrid,
    val updateInterval: FrameTiming,
    val maxDepth: Int,
    val diagonal: Boolean
)

// =============================================================================
// IR NODES FOR PATHFINDING
// =============================================================================

// --- Navigation Grid IR ---
// Moved to io.github.gbkt.core.ir.PathfindingIR

// =============================================================================
// DSL ENTRY POINTS (added to GameBuilder)
// =============================================================================

// These will be added as extension functions on GameBuilder in Game.kt

// =============================================================================
// INFIX EXTENSIONS FOR FLUENT SYNTAX
// =============================================================================

/**
 * Counter for generating unique path variable names.
 *
 * Note: DSL definitions typically run sequentially during Gradle builds, so thread safety is not
 * critical here. Any duplicate names would cause clear compile-time errors, not silent bugs.
 */
private var pathCounter = 0

/** Start a path query from an Entity. Usage: player findPathTo treasure using navGrid */
infix fun Entity.findPathTo(target: Entity): PathQuery {
    val pathName = "_path_${pathCounter++}"
    // Convert pixel coordinates to tile coordinates (divide by 8)
    return PathQuery(
        pathName,
        IRBinary(this.x.ir, BinaryOp.SHR, IRLiteral(3)),
        IRBinary(this.y.ir, BinaryOp.SHR, IRLiteral(3)),
        IRBinary(target.x.ir, BinaryOp.SHR, IRLiteral(3)),
        IRBinary(target.y.ir, BinaryOp.SHR, IRLiteral(3))
    )
}

/** Start a path query from an Entity to tile coordinates. */
fun Entity.findPathTo(tileX: Int, tileY: Int): PathQuery {
    val pathName = "_path_${pathCounter++}"
    return PathQuery(
        pathName,
        IRBinary(this.x.ir, BinaryOp.SHR, IRLiteral(3)),
        IRBinary(this.y.ir, BinaryOp.SHR, IRLiteral(3)),
        IRLiteral(tileX),
        IRLiteral(tileY)
    )
}

/** Start a path query from an Entity to expression coordinates. */
fun Entity.findPathTo(tileX: Expr, tileY: Expr): PathQuery {
    val pathName = "_path_${pathCounter++}"
    return PathQuery(
        pathName,
        IRBinary(this.x.ir, BinaryOp.SHR, IRLiteral(3)),
        IRBinary(this.y.ir, BinaryOp.SHR, IRLiteral(3)),
        tileX.ir,
        tileY.ir
    )
}

/** Start a path query from tile coordinates. */
fun findPath(fromTileX: Int, fromTileY: Int, toTileX: Int, toTileY: Int): PathQuery {
    val pathName = "_path_${pathCounter++}"
    return PathQuery(
        pathName,
        IRLiteral(fromTileX),
        IRLiteral(fromTileY),
        IRLiteral(toTileX),
        IRLiteral(toTileY)
    )
}

/** Start a path query with named parameters. */
fun findPath(from: Entity, to: Entity): PathQuery {
    return from findPathTo to
}

// =============================================================================
// ENTITY EXTENSIONS FOR PATH FOLLOWING
// =============================================================================

/** Make an entity follow a computed path. */
fun Entity.followPath(path: Path, init: PathFollowBuilder.() -> Unit = {}) {
    val pos = positionComponent ?: error("Entity '$name' needs position for path following")

    val builder = PathFollowBuilder()
    builder.init()
    val config = builder.build()

    RecordingContext.require()
        .emit(
            IRPathFollow(
                pathName = path.name,
                entityXVar = "${name}_x",
                entityYVar = "${name}_y",
                speed = config.speed,
                onComplete = config.onArrive,
                onBlocked = config.onBlocked
            )
        )
}
