/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.platformer.dsl

import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.genre.platformer.domain.CameraScrollMode
import io.github.gbkt.genre.platformer.domain.CollectibleDef
import io.github.gbkt.genre.platformer.domain.CollectibleType
import io.github.gbkt.genre.platformer.domain.GoalZoneDef
import io.github.gbkt.genre.platformer.domain.HazardDef
import io.github.gbkt.genre.platformer.domain.LadderConfig
import io.github.gbkt.genre.platformer.domain.ParallaxLayer
import io.github.gbkt.genre.platformer.domain.PlatformDef
import io.github.gbkt.genre.platformer.domain.PlatformType
import io.github.gbkt.genre.platformer.domain.PlatformerCameraConfig
import io.github.gbkt.genre.platformer.domain.PlatformerPhysicsConfig
import io.github.gbkt.genre.platformer.domain.ScrollDirection
import io.github.gbkt.genre.platformer.domain.WallJumpConfig

// =============================================================================
// WALL JUMP CONFIG BUILDER
// =============================================================================

/**
 * Sub-builder for optional wall-jump / wall-slide configuration.
 *
 * Used within [PlatformerPhysicsBuilder.wallJump] block.
 */
class WallJumpConfigBuilder {
    private var wallSlideSpeed: Int = 1
    private var iFrameDuration: Int = 8
    private var cooldownFrames: Int = 10

    /** Sets the downward slide speed while holding toward a wall (pixels/frame). */
    fun slideSpeed(value: Int) {
        wallSlideSpeed = value
    }

    /** Sets the number of invincibility frames granted after a wall-jump. */
    fun iFrames(value: Int) {
        iFrameDuration = value
    }

    /** Sets the cooldown frames before another wall-jump can be triggered. */
    fun cooldown(value: Int) {
        cooldownFrames = value
    }

    /** Builds the [WallJumpConfig] domain object. */
    fun build(): WallJumpConfig =
        WallJumpConfig(
            wallSlideSpeed = wallSlideSpeed,
            iFrameDuration = iFrameDuration,
            cooldownFrames = cooldownFrames,
        )
}

// =============================================================================
// PLATFORMER PHYSICS BUILDER
// =============================================================================

/**
 * Builder for the platformer physics system.
 *
 * Produces a [GenericSystem] with type `"platformer_physics"` containing a full
 * [PlatformerPhysicsConfig] in its config map.
 *
 * Variable-height jump is enabled by default. Wall-jump is opt-in via [wallJump] block.
 *
 * ```kotlin
 * platformerPhysics {
 *     gravity(2)
 *     jumpForce(8)
 *     terminalVelocity(12)
 *     coyoteTime(6)
 *     jumpBuffer(8)
 *     airControl(75)
 *     wallJump {
 *         slideSpeed(1)
 *         iFrames(8)
 *         cooldown(10)
 *     }
 * }
 * ```
 *
 * @param id Unique system identifier (default "physics").
 */
class PlatformerPhysicsBuilder(val id: String = "physics") {
    private var gravity: Int = 2
    private var jumpForce: Int = 8
    private var terminalVelocity: Int = 12
    private var coyoteFrames: Int = 6
    private var jumpBufferFrames: Int = 8
    private var airControlFactor: Int = 75
    private var variableHeightJump: Boolean = true
    private var wallJumpConfig: WallJumpConfig? = null

    /** Sets the downward acceleration applied every frame (fixed-point units). */
    fun gravity(value: Int) {
        gravity = value
    }

    /** Sets the initial upward velocity when jump is pressed (fixed-point units). */
    fun jumpForce(value: Int) {
        jumpForce = value
    }

    /** Sets the maximum downward velocity cap (fixed-point units). */
    fun terminalVelocity(value: Int) {
        terminalVelocity = value
    }

    /** Sets the number of coyote frames (frames after leaving platform where jump is allowed). */
    fun coyoteTime(frames: Int) {
        coyoteFrames = frames
    }

    /** Sets the jump buffer window (frames before landing where queued jump fires on landing). */
    fun jumpBuffer(frames: Int) {
        jumpBufferFrames = frames
    }

    /** Sets the air control factor (0–100 percentage of horizontal speed while airborne). */
    fun airControl(factor: Int) {
        airControlFactor = factor
    }

    /**
     * Disables variable-height jump. By default jump height is variable — releasing early reduces
     * height. Call this to use a fixed jump height instead.
     */
    fun fixedJump() {
        variableHeightJump = false
    }

    /**
     * Enables wall-jump and wall-slide mechanics (opt-in).
     *
     * ```kotlin
     * wallJump {
     *     slideSpeed(1)
     *     iFrames(8)
     *     cooldown(10)
     * }
     * ```
     */
    fun wallJump(block: WallJumpConfigBuilder.() -> Unit) {
        val builder = WallJumpConfigBuilder()
        builder.block()
        wallJumpConfig = builder.build()
    }

    /**
     * Builds a [GenericSystem] with type `"platformer_physics"`.
     *
     * The config map contains:
     * - `"type"` → `"platformer_physics"`
     * - `"physicsConfig"` → [PlatformerPhysicsConfig]
     */
    fun build(): GenericSystem {
        val config =
            PlatformerPhysicsConfig(
                gravity = gravity,
                jumpForce = jumpForce,
                terminalVelocity = terminalVelocity,
                coyoteFrames = coyoteFrames,
                jumpBufferFrames = jumpBufferFrames,
                airControlFactor = airControlFactor,
                variableHeightJump = variableHeightJump,
                wallJump = wallJumpConfig,
            )
        return GenericSystem(
            id = id,
            config = mapOf("type" to "platformer_physics", "physicsConfig" to config),
        )
    }
}

// =============================================================================
// PARALLAX LAYER BUILDER
// =============================================================================

/** Sub-builder for a single parallax background layer within [PlatformerCameraBuilder]. */
class ParallaxLayerBuilder {
    private var assetId: String = ""
    private var scrollSpeedX: Int = 50
    private var scrollSpeedY: Int = 0

    /** Sets the asset reference ID for the background graphic. */
    fun asset(id: String) {
        assetId = id
    }

    /**
     * Sets the horizontal scroll speed relative to the camera (0 = fixed, 100 = same as camera).
     */
    fun speedX(value: Int) {
        scrollSpeedX = value
    }

    /** Sets the vertical scroll speed relative to the camera. */
    fun speedY(value: Int) {
        scrollSpeedY = value
    }

    /** Builds the [ParallaxLayer] domain object. */
    fun build(): ParallaxLayer =
        ParallaxLayer(assetId = assetId, scrollSpeedX = scrollSpeedX, scrollSpeedY = scrollSpeedY)
}

// =============================================================================
// PLATFORMER CAMERA BUILDER
// =============================================================================

/**
 * Builder for the platformer camera system.
 *
 * Produces a [GenericSystem] with type `"platformer_camera"` containing a [PlatformerCameraConfig]
 * in its config map.
 *
 * ```kotlin
 * platformerCamera {
 *     smoothFollow()
 *     deadZone(x = 8, y = 16)
 *     horizontal()
 *     parallax("bg_sky") { speedX(20) }
 *     parallax("bg_clouds") { speedX(50) }
 * }
 * ```
 *
 * @param id Unique system identifier (default "camera").
 */
class PlatformerCameraBuilder(val id: String = "camera") {
    private var mode: CameraScrollMode = CameraScrollMode.SMOOTH_FOLLOW
    private var deadZoneX: Int = 8
    private var deadZoneY: Int = 16
    private var scrollDirections: ScrollDirection = ScrollDirection.HORIZONTAL
    private val parallaxLayers: MutableList<ParallaxLayer> = mutableListOf()

    /** Sets the camera mode to smooth follow (default). */
    fun smoothFollow() {
        mode = CameraScrollMode.SMOOTH_FOLLOW
    }

    /** Sets the camera mode to screen-lock (snaps to screen boundaries). */
    fun screenLock() {
        mode = CameraScrollMode.SCREEN_LOCK
    }

    /** Sets the dead zone for smooth-follow mode (pixels from center before camera tracks). */
    fun deadZone(x: Int = 8, y: Int = 16) {
        deadZoneX = x
        deadZoneY = y
    }

    /** Restricts scrolling to the horizontal axis only (default). */
    fun horizontal() {
        scrollDirections = ScrollDirection.HORIZONTAL
    }

    /** Restricts scrolling to the vertical axis only. */
    fun vertical() {
        scrollDirections = ScrollDirection.VERTICAL
    }

    /** Enables multi-directional scrolling (both axes). */
    fun multiDirectional() {
        scrollDirections = ScrollDirection.MULTI
    }

    /**
     * Adds a parallax background layer.
     *
     * ```kotlin
     * parallax("bg_sky") { speedX(20); speedY(0) }
     * ```
     */
    fun parallax(assetId: String, block: ParallaxLayerBuilder.() -> Unit = {}) {
        val builder = ParallaxLayerBuilder()
        builder.asset(assetId)
        builder.block()
        parallaxLayers.add(builder.build())
    }

    /**
     * Builds a [GenericSystem] with type `"platformer_camera"`.
     *
     * The config map contains:
     * - `"type"` → `"platformer_camera"`
     * - `"cameraConfig"` → [PlatformerCameraConfig]
     */
    fun build(): GenericSystem {
        val config =
            PlatformerCameraConfig(
                mode = mode,
                deadZoneX = deadZoneX,
                deadZoneY = deadZoneY,
                scrollDirections = scrollDirections,
                parallaxLayers = parallaxLayers.toList(),
            )
        return GenericSystem(
            id = id,
            config = mapOf("type" to "platformer_camera", "cameraConfig" to config),
        )
    }
}

// =============================================================================
// PLATFORM DEF BUILDER
// =============================================================================

/**
 * Builder for a named platform definition.
 *
 * Produces a [GenericSystem] with type `"platformer_platform"` containing a [PlatformDef] in its
 * config map.
 *
 * ```kotlin
 * platform("crumble_floor") {
 *     type(PlatformType.CRUMBLING)
 *     crumbleDelay(20)
 *     crumbleRespawn(60)
 * }
 * ```
 *
 * @param id Unique platform identifier.
 */
class PlatformDefBuilder(val id: String) {
    private var platformType: PlatformType = PlatformType.SOLID
    private var moveSpeed: Int = 1
    private var crumbleDelay: Int = 30
    private var crumbleRespawn: Int = 120

    /** Sets the platform behaviour type. */
    fun type(value: PlatformType) {
        platformType = value
    }

    /** Sets the movement speed for [PlatformType.MOVING] platforms (pixels/frame). */
    fun moveSpeed(value: Int) {
        moveSpeed = value
    }

    /** Sets the crumble delay for [PlatformType.CRUMBLING] platforms (frames before break). */
    fun crumbleDelay(frames: Int) {
        crumbleDelay = frames
    }

    /** Sets the respawn delay for crumbled platforms (frames; 0 = no respawn). */
    fun crumbleRespawn(frames: Int) {
        crumbleRespawn = frames
    }

    /**
     * Builds a [GenericSystem] with type `"platformer_platform"`.
     *
     * The config map contains:
     * - `"type"` → `"platformer_platform"`
     * - `"platform"` → [PlatformDef]
     */
    fun build(): GenericSystem {
        val def =
            PlatformDef(
                id = id,
                type = platformType,
                moveSpeed = moveSpeed,
                crumbleDelay = crumbleDelay,
                crumbleRespawn = crumbleRespawn,
            )
        return GenericSystem(
            id = id,
            config = mapOf("type" to "platformer_platform", "platform" to def),
        )
    }
}

// =============================================================================
// HAZARD DEF BUILDER
// =============================================================================

/**
 * Builder for a hazard tile definition.
 *
 * Produces a [GenericSystem] with type `"platformer_hazard"` containing a [HazardDef] in its config
 * map.
 *
 * ```kotlin
 * hazard("spikes") {
 *     tileId(42)
 *     damage(5)
 * }
 * ```
 *
 * @param id Unique hazard identifier.
 */
class HazardDefBuilder(val id: String) {
    private var tileId: Int = 0
    private var damage: Int = 1
    private var instant: Boolean = false

    /** Sets the tile map ID that triggers this hazard. */
    fun tileId(value: Int) {
        tileId = value
    }

    /** Sets the damage per contact frame. */
    fun damage(value: Int) {
        damage = value
    }

    /** Marks this hazard as instant-death (ignores HP). */
    fun instantDeath() {
        instant = true
    }

    /**
     * Builds a [GenericSystem] with type `"platformer_hazard"`.
     *
     * The config map contains:
     * - `"type"` → `"platformer_hazard"`
     * - `"hazard"` → [HazardDef]
     */
    fun build(): GenericSystem {
        val def = HazardDef(id = id, tileId = tileId, damage = damage, instant = instant)
        return GenericSystem(
            id = id,
            config = mapOf("type" to "platformer_hazard", "hazard" to def),
        )
    }
}

// =============================================================================
// GOAL ZONE BUILDER
// =============================================================================

/**
 * Builder for a level-completion goal zone.
 *
 * Produces a [GenericSystem] with type `"platformer_goal"` containing a [GoalZoneDef] in its config
 * map.
 *
 * ```kotlin
 * goalZone("exit") {
 *     position(x = 200, y = 50)
 *     size(width = 16, height = 32)
 * }
 * ```
 *
 * @param id Unique goal-zone identifier.
 */
class GoalZoneBuilder(val id: String) {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 16
    private var height: Int = 16

    /** Sets the top-left position of the goal zone (pixels). */
    fun position(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    /** Sets the size of the goal zone (pixels). */
    fun size(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    /** Sets the x position of the goal zone. */
    fun x(value: Int) {
        x = value
    }

    /** Sets the y position of the goal zone. */
    fun y(value: Int) {
        y = value
    }

    /** Sets the width of the goal zone. */
    fun width(value: Int) {
        width = value
    }

    /** Sets the height of the goal zone. */
    fun height(value: Int) {
        height = value
    }

    /**
     * Builds a [GenericSystem] with type `"platformer_goal"`.
     *
     * The config map contains:
     * - `"type"` → `"platformer_goal"`
     * - `"goalZone"` → [GoalZoneDef]
     */
    fun build(): GenericSystem {
        val def = GoalZoneDef(id = id, x = x, y = y, width = width, height = height)
        return GenericSystem(
            id = id,
            config = mapOf("type" to "platformer_goal", "goalZone" to def),
        )
    }
}

// =============================================================================
// COLLECTIBLE DEF BUILDER
// =============================================================================

/**
 * Builder for a collectible item definition.
 *
 * Produces a [GenericSystem] with type `"platformer_collectible"` containing a [CollectibleDef] in
 * its config map. Acts as a facade over the engine PickupDef; codegen integration is wired in
 * Plan 10.
 *
 * ```kotlin
 * collectible("gold_coin") {
 *     type(CollectibleType.COIN)
 *     value(10)
 *     tileId(5)
 * }
 * ```
 *
 * @param id Unique collectible identifier.
 */
class CollectibleDefBuilder(val id: String) {
    private var collectibleType: CollectibleType = CollectibleType.COIN
    private var value: Int = 1
    private var tileId: Int = 0

    /** Sets the collectible category. */
    fun type(value: CollectibleType) {
        collectibleType = value
    }

    /** Sets the score or resource amount awarded on pickup. */
    fun value(amount: Int) {
        value = amount
    }

    /** Sets the tile map ID for the collectible sprite. */
    fun tileId(id: Int) {
        tileId = id
    }

    /**
     * Builds a [GenericSystem] with type `"platformer_collectible"`.
     *
     * The config map contains:
     * - `"type"` → `"platformer_collectible"`
     * - `"collectible"` → [CollectibleDef]
     */
    fun build(): GenericSystem {
        val def = CollectibleDef(id = id, type = collectibleType, value = value, tileId = tileId)
        return GenericSystem(
            id = id,
            config = mapOf("type" to "platformer_collectible", "collectible" to def),
        )
    }
}

// =============================================================================
// LADDER CONFIG BUILDER
// =============================================================================

/**
 * Builder for ladder tile configuration.
 *
 * Produces a [GenericSystem] with type `"platformer_ladder"` containing a [LadderConfig] in its
 * config map.
 *
 * ```kotlin
 * ladder {
 *     climbSpeed(2)
 *     tileId(15)
 * }
 * ```
 *
 * @param id Unique identifier for this ladder configuration (default "ladder").
 */
class LadderConfigBuilder(val id: String = "ladder") {
    private var climbSpeed: Int = 2
    private var tileId: Int = 0

    /** Sets the vertical movement speed while climbing (pixels/frame). */
    fun climbSpeed(value: Int) {
        climbSpeed = value
    }

    /** Sets the tile map ID that is treated as a climbable ladder. */
    fun tileId(value: Int) {
        tileId = value
    }

    /**
     * Builds a [GenericSystem] with type `"platformer_ladder"`.
     *
     * The config map contains:
     * - `"type"` → `"platformer_ladder"`
     * - `"ladderConfig"` → [LadderConfig]
     */
    fun build(): GenericSystem {
        val config = LadderConfig(climbSpeed = climbSpeed, tileId = tileId)
        return GenericSystem(
            id = id,
            config = mapOf("type" to "platformer_ladder", "ladderConfig" to config),
        )
    }
}
