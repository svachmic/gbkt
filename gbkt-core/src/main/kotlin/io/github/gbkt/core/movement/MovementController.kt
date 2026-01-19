/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.movement

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.IRStatement

// =============================================================================
// MOVEMENT CONTROLLER - Abstract movement system for different game genres
// =============================================================================

/**
 * Movement controller type for different game genres.
 *
 * The movement controller abstracts the fundamental movement model, allowing games to use
 * grid-based, physics-based, or free-roaming movement without changing game logic.
 */
enum class MovementType {
    /** Grid-based movement - snap to tiles (RPGs, puzzle games) */
    GRID,

    /** Physics-based movement with velocity and acceleration (platformers, action games) */
    PHYSICS,

    /** Free-roaming pixel movement without grid constraints (shooters, racing) */
    FREE_ROAM,

    /** Top-down smooth movement with optional tile collision (Zelda-like) */
    TOP_DOWN,
}

/**
 * Position representation that supports both tile and pixel coordinates.
 *
 * For grid-based games, x/y represent tile positions. For physics/free-roam games, x/y represent
 * pixel positions.
 */
data class Position(
    /** X coordinate (tile or pixel depending on movement type) */
    val x: Int,
    /** Y coordinate (tile or pixel depending on movement type) */
    val y: Int,
    /** Sub-pixel X for interpolation (0-255, only used in physics/smooth modes) */
    val subX: Int = 0,
    /** Sub-pixel Y for interpolation (0-255, only used in physics/smooth modes) */
    val subY: Int = 0,
) {
    /** Get pixel X position (for grid mode, multiply by tile size) */
    fun toPixelX(tileSize: Int, movementType: MovementType): Int =
        when (movementType) {
            MovementType.GRID -> x * tileSize
            else -> x
        }

    /** Get pixel Y position (for grid mode, multiply by tile size) */
    fun toPixelY(tileSize: Int, movementType: MovementType): Int =
        when (movementType) {
            MovementType.GRID -> y * tileSize
            else -> y
        }
}

/**
 * Velocity for physics-based movement.
 *
 * Uses fixed-point representation for Game Boy compatibility.
 */
data class Velocity(
    /** X velocity (pixels per frame * 16) */
    val vx: Int = 0,
    /** Y velocity (pixels per frame * 16) */
    val vy: Int = 0,
)

/**
 * Movement bounds configuration.
 *
 * Defines the boundaries within which movement is allowed.
 */
data class MovementBounds(
    /** Minimum X (tiles or pixels) */
    val minX: Int = 0,
    /** Minimum Y (tiles or pixels) */
    val minY: Int = 0,
    /** Maximum X (tiles or pixels) */
    val maxX: Int = Int.MAX_VALUE,
    /** Maximum Y (tiles or pixels) */
    val maxY: Int = Int.MAX_VALUE,
)

/**
 * Abstract movement controller interface.
 *
 * This interface defines the contract for all movement systems. Implementations handle the
 * specifics of grid-based, physics-based, or free-roaming movement.
 *
 * Usage:
 * ```kotlin
 * val movement by movementController {
 *     type(MovementType.GRID)
 *     tileSize(8)
 *     speed(4)
 *
 *     onMove { direction -> /* handle movement */ }
 *     onBlocked { /* handle collision */ }
 * }
 * ```
 */
interface MovementController {
    /** Unique identifier */
    val id: String

    /** Movement type */
    val movementType: MovementType

    /** Tile size (for grid and top-down modes) */
    val tileSize: Int

    /** Movement speed (interpretation depends on type) */
    val speed: Int

    /** Whether collision detection is enabled */
    val collisionEnabled: Boolean

    /** Callback statements for movement events */
    val onMoveStatements: List<IRStatement>

    /** Callback statements for blocked movement */
    val onBlockedStatements: List<IRStatement>

    /** Callback statements for position change */
    val onPositionChangeStatements: List<IRStatement>

    /** System index for code generation */
    var systemIndex: Int
}

// =============================================================================
// GRID MOVEMENT CONTROLLER
// =============================================================================

/**
 * Grid-based movement controller.
 *
 * For tile-based games like RPGs, puzzle games, and dungeon crawlers. Movement snaps to tile
 * positions with optional smooth interpolation.
 */
class GridMovementController(
    override val id: String,
    override val tileSize: Int,
    /** Movement speed in frames per tile */
    override val speed: Int,
    override val collisionEnabled: Boolean,
    /** Whether to interpolate sprite position during movement */
    val smoothInterpolation: Boolean,
    /** Whether diagonal movement is allowed */
    val allowDiagonal: Boolean,
    override val onMoveStatements: List<IRStatement>,
    override val onBlockedStatements: List<IRStatement>,
    override val onPositionChangeStatements: List<IRStatement>,
    /** Callback when step is completed (for step counting, encounters) */
    val onStepStatements: List<IRStatement>,
    override var systemIndex: Int = -1,
) : MovementController {
    override val movementType: MovementType = MovementType.GRID
}

// =============================================================================
// PHYSICS MOVEMENT CONTROLLER
// =============================================================================

/**
 * Physics-based movement controller.
 *
 * For platformers, action games, and games requiring realistic movement physics. Supports velocity,
 * acceleration, gravity, and friction.
 */
class PhysicsMovementController(
    override val id: String,
    override val tileSize: Int,
    /** Base movement speed (pixels per frame * 16) */
    override val speed: Int,
    override val collisionEnabled: Boolean,
    /** Acceleration rate (added to velocity per frame) */
    val acceleration: Int,
    /** Friction/drag (subtracted from velocity per frame when not accelerating) */
    val friction: Int,
    /** Gravity strength (added to vertical velocity per frame) */
    val gravity: Int,
    /** Maximum horizontal velocity */
    val maxSpeedX: Int,
    /** Maximum vertical velocity (terminal velocity) */
    val maxSpeedY: Int,
    /** Jump velocity (negative = up) */
    val jumpVelocity: Int,
    /** Number of air jumps allowed (0 = ground jump only) */
    val airJumps: Int,
    override val onMoveStatements: List<IRStatement>,
    override val onBlockedStatements: List<IRStatement>,
    override val onPositionChangeStatements: List<IRStatement>,
    /** Callback when landing on ground */
    val onLandStatements: List<IRStatement>,
    /** Callback when becoming airborne */
    val onAirborneStatements: List<IRStatement>,
    override var systemIndex: Int = -1,
) : MovementController {
    override val movementType: MovementType = MovementType.PHYSICS
}

// =============================================================================
// FREE-ROAM MOVEMENT CONTROLLER
// =============================================================================

/**
 * Free-roaming movement controller.
 *
 * For shooters, racing games, and games with unrestricted pixel movement. No tile grid, supports
 * 360-degree movement.
 */
class FreeRoamMovementController(
    override val id: String,
    override val tileSize: Int,
    /** Movement speed in pixels per frame */
    override val speed: Int,
    override val collisionEnabled: Boolean,
    /** Whether to use 8-direction movement (vs 4-direction) */
    val eightDirection: Boolean,
    /** Acceleration (0 = instant speed change) */
    val acceleration: Int,
    /** Deceleration/friction when not moving */
    val deceleration: Int,
    /** Maximum velocity */
    val maxSpeed: Int,
    override val onMoveStatements: List<IRStatement>,
    override val onBlockedStatements: List<IRStatement>,
    override val onPositionChangeStatements: List<IRStatement>,
    override var systemIndex: Int = -1,
) : MovementController {
    override val movementType: MovementType = MovementType.FREE_ROAM
}

// =============================================================================
// TOP-DOWN MOVEMENT CONTROLLER
// =============================================================================

/**
 * Top-down movement controller.
 *
 * For Zelda-like games with smooth movement but optional tile collision. Supports push-back on
 * collision, smooth movement between tiles.
 */
class TopDownMovementController(
    override val id: String,
    override val tileSize: Int,
    /** Movement speed in pixels per frame */
    override val speed: Int,
    override val collisionEnabled: Boolean,
    /** Whether movement aligns to pixel grid (vs sub-pixel) */
    val pixelPerfect: Boolean,
    /** Collision box width */
    val hitboxWidth: Int,
    /** Collision box height */
    val hitboxHeight: Int,
    /** Offset from sprite origin to hitbox */
    val hitboxOffsetX: Int,
    val hitboxOffsetY: Int,
    override val onMoveStatements: List<IRStatement>,
    override val onBlockedStatements: List<IRStatement>,
    override val onPositionChangeStatements: List<IRStatement>,
    /** Callback when entering a new tile */
    val onTileEnterStatements: List<IRStatement>,
    override var systemIndex: Int = -1,
) : MovementController {
    override val movementType: MovementType = MovementType.TOP_DOWN
}

// =============================================================================
// MOVEMENT CONTROLLER BUILDERS
// =============================================================================

/** Base builder for movement controllers. */
@GbktDsl
abstract class MovementControllerBuilder(protected val controllerId: String) {
    protected var tileSize: Int = 8
    protected var speed: Int = 4
    protected var collisionEnabled: Boolean = true
    protected var onMoveStatements: List<IRStatement> = emptyList()
    protected var onBlockedStatements: List<IRStatement> = emptyList()
    protected var onPositionChangeStatements: List<IRStatement> = emptyList()

    /** Set the tile size in pixels */
    fun tileSize(size: Int) {
        require(size in 1..32) { "Tile size must be between 1 and 32" }
        tileSize = size
    }

    /** Set the movement speed */
    fun speed(value: Int) {
        require(value > 0) { "Speed must be positive" }
        speed = value
    }

    /** Enable/disable collision detection */
    fun collision(enabled: Boolean) {
        collisionEnabled = enabled
    }

    /** Callback when movement starts */
    fun onMove(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onMoveStatements = recorder.statements
    }

    /** Callback when movement is blocked */
    fun onBlocked(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onBlockedStatements = recorder.statements
    }

    /** Callback when position changes */
    fun onPositionChange(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onPositionChangeStatements = recorder.statements
    }

    abstract fun build(): MovementController
}

/** Builder for grid movement controllers. */
@GbktDsl
class GridMovementBuilder(controllerId: String) : MovementControllerBuilder(controllerId) {
    private var smoothInterpolation: Boolean = true
    private var allowDiagonal: Boolean = false
    private var onStepStatements: List<IRStatement> = emptyList()

    /** Enable smooth sprite interpolation during tile transitions */
    fun smoothInterpolation(enabled: Boolean) {
        smoothInterpolation = enabled
    }

    /** Allow diagonal movement */
    fun allowDiagonal(enabled: Boolean) {
        allowDiagonal = enabled
    }

    /** Callback when a step is completed (for step counting, random encounters) */
    fun onStep(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onStepStatements = recorder.statements
    }

    override fun build() =
        GridMovementController(
            id = controllerId,
            tileSize = tileSize,
            speed = speed,
            collisionEnabled = collisionEnabled,
            smoothInterpolation = smoothInterpolation,
            allowDiagonal = allowDiagonal,
            onMoveStatements = onMoveStatements,
            onBlockedStatements = onBlockedStatements,
            onPositionChangeStatements = onPositionChangeStatements,
            onStepStatements = onStepStatements,
        )
}

/** Builder for physics movement controllers. */
@GbktDsl
class PhysicsMovementBuilder(controllerId: String) : MovementControllerBuilder(controllerId) {
    private var acceleration: Int = 16
    private var friction: Int = 8
    private var gravity: Int = 4
    private var maxSpeedX: Int = 48
    private var maxSpeedY: Int = 80
    private var jumpVelocity: Int = -64
    private var airJumps: Int = 0
    private var onLandStatements: List<IRStatement> = emptyList()
    private var onAirborneStatements: List<IRStatement> = emptyList()

    /** Set acceleration rate */
    fun acceleration(value: Int) {
        acceleration = value
    }

    /** Set friction/drag */
    fun friction(value: Int) {
        friction = value
    }

    /** Set gravity strength */
    fun gravity(value: Int) {
        gravity = value
    }

    /** Set maximum horizontal speed */
    fun maxSpeedX(value: Int) {
        maxSpeedX = value
    }

    /** Set maximum vertical speed (terminal velocity) */
    fun maxSpeedY(value: Int) {
        maxSpeedY = value
    }

    /** Set jump velocity */
    fun jumpVelocity(value: Int) {
        jumpVelocity = value
    }

    /** Set number of air jumps (double jump, etc.) */
    fun airJumps(count: Int) {
        airJumps = count
    }

    /** Callback when landing on ground */
    fun onLand(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onLandStatements = recorder.statements
    }

    /** Callback when becoming airborne */
    fun onAirborne(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onAirborneStatements = recorder.statements
    }

    override fun build() =
        PhysicsMovementController(
            id = controllerId,
            tileSize = tileSize,
            speed = speed,
            collisionEnabled = collisionEnabled,
            acceleration = acceleration,
            friction = friction,
            gravity = gravity,
            maxSpeedX = maxSpeedX,
            maxSpeedY = maxSpeedY,
            jumpVelocity = jumpVelocity,
            airJumps = airJumps,
            onMoveStatements = onMoveStatements,
            onBlockedStatements = onBlockedStatements,
            onPositionChangeStatements = onPositionChangeStatements,
            onLandStatements = onLandStatements,
            onAirborneStatements = onAirborneStatements,
        )
}

/** Builder for free-roam movement controllers. */
@GbktDsl
class FreeRoamMovementBuilder(controllerId: String) : MovementControllerBuilder(controllerId) {
    private var eightDirection: Boolean = true
    private var acceleration: Int = 0
    private var deceleration: Int = 0
    private var maxSpeed: Int = 8

    /** Use 8-direction movement (vs 4-direction) */
    fun eightDirection(enabled: Boolean) {
        eightDirection = enabled
    }

    /** Set acceleration (0 = instant) */
    fun acceleration(value: Int) {
        acceleration = value
    }

    /** Set deceleration when not moving */
    fun deceleration(value: Int) {
        deceleration = value
    }

    /** Set maximum speed */
    fun maxSpeed(value: Int) {
        maxSpeed = value
    }

    override fun build() =
        FreeRoamMovementController(
            id = controllerId,
            tileSize = tileSize,
            speed = speed,
            collisionEnabled = collisionEnabled,
            eightDirection = eightDirection,
            acceleration = acceleration,
            deceleration = deceleration,
            maxSpeed = maxSpeed,
            onMoveStatements = onMoveStatements,
            onBlockedStatements = onBlockedStatements,
            onPositionChangeStatements = onPositionChangeStatements,
        )
}

/** Builder for top-down movement controllers. */
@GbktDsl
class TopDownMovementBuilder(controllerId: String) : MovementControllerBuilder(controllerId) {
    private var pixelPerfect: Boolean = true
    private var hitboxWidth: Int = 8
    private var hitboxHeight: Int = 8
    private var hitboxOffsetX: Int = 0
    private var hitboxOffsetY: Int = 0
    private var onTileEnterStatements: List<IRStatement> = emptyList()

    /** Use pixel-perfect movement */
    fun pixelPerfect(enabled: Boolean) {
        pixelPerfect = enabled
    }

    /** Set collision hitbox size */
    fun hitbox(width: Int, height: Int) {
        hitboxWidth = width
        hitboxHeight = height
    }

    /** Set hitbox offset from sprite origin */
    fun hitboxOffset(x: Int, y: Int) {
        hitboxOffsetX = x
        hitboxOffsetY = y
    }

    /** Callback when entering a new tile */
    fun onTileEnter(init: () -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder, init)
        onTileEnterStatements = recorder.statements
    }

    override fun build() =
        TopDownMovementController(
            id = controllerId,
            tileSize = tileSize,
            speed = speed,
            collisionEnabled = collisionEnabled,
            pixelPerfect = pixelPerfect,
            hitboxWidth = hitboxWidth,
            hitboxHeight = hitboxHeight,
            hitboxOffsetX = hitboxOffsetX,
            hitboxOffsetY = hitboxOffsetY,
            onMoveStatements = onMoveStatements,
            onBlockedStatements = onBlockedStatements,
            onPositionChangeStatements = onPositionChangeStatements,
            onTileEnterStatements = onTileEnterStatements,
        )
}
