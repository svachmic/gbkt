/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.platformer.domain

// =============================================================================
// PLATFORMER PHYSICS TYPES
// =============================================================================

/**
 * Configuration for wall-jump / wall-slide mechanics.
 *
 * @property wallSlideSpeed Downward slide speed while holding toward a wall (pixels/frame).
 * @property iFrameDuration Invincibility frames granted after a wall-jump.
 * @property cooldownFrames Frames before another wall-jump can be triggered.
 */
data class WallJumpConfig(
    val wallSlideSpeed: Int = 1,
    val iFrameDuration: Int = 8,
    val cooldownFrames: Int = 10,
)

/**
 * Full physics configuration for a platformer game.
 *
 * Supports variable-height jump (release early for lower jump), optional wall-jump/wall-slide,
 * coyote time, and jump buffer for responsive controls.
 *
 * @property gravity Downward acceleration applied every frame (fixed-point units).
 * @property jumpForce Initial upward velocity when jump is pressed (fixed-point units).
 * @property terminalVelocity Maximum downward velocity cap (fixed-point units).
 * @property coyoteFrames Frames after leaving a platform where jump is still allowed.
 * @property jumpBufferFrames Frames before landing where a queued jump will fire on landing.
 * @property airControlFactor Horizontal control factor while airborne (0–100, percentage).
 * @property variableHeightJump If true, releasing jump early reduces jump height.
 * @property wallJump Optional wall-jump configuration; null disables wall mechanics.
 * @property solidThreshold Max tile index that counts as PASSABLE. Tiles `< solidThreshold` are
 *   solid (per reference convention). `null` = no tilemap collision (use the abstract physics
 *   path). Wired by Plan 12-08 / 12-11.
 * @property jumpHoldMaxFrames Max frames the jump button can be held to extend jump height. `0` =
 *   disabled (the existing [variableHeightJump] flag governs the abstract path). Wired by Plan
 *   12-13.
 */
data class PlatformerPhysicsConfig(
    val gravity: Int = 2,
    val jumpForce: Int = 8,
    val terminalVelocity: Int = 12,
    val coyoteFrames: Int = 6,
    val jumpBufferFrames: Int = 8,
    val airControlFactor: Int = 75,
    val variableHeightJump: Boolean = true,
    val wallJump: WallJumpConfig? = null,
    val solidThreshold: Int? = null,
    val jumpHoldMaxFrames: Int = 0,
)

/**
 * Configuration for input → playerVx wiring and walk-frame cycling (Phase 12.3 D-01a).
 *
 * Defaults are LOCKED per SPEC + CONTEXT D-01a; matches reference `player.c` values so future
 * platformer ports inherit known-good behaviour and tune via per-zone overrides only.
 *
 * Note: this is the typed companion record. The AssignableVar binders for `walkFrameIdx` /
 * `threeFrameCounter` are NOT fields here — they're captured directly into the
 * `GenericSystem.config` map by `PlatformerInputBuilder` (L-1.1 / D-03 — no-magic-strings binder
 * pattern, mirrors `TilemapCollisionBuilder.position/velocity/grounded`).
 *
 * @property walkSpeed Horizontal velocity applied to `_playerVx` while D-pad LEFT/RIGHT is held
 *   (signed INT16 — requires `playerVx by i16Var(0)` per L-1.2 / SPEC R6). Default 128 = reference
 *   ≈ 0.5 px/frame walking speed.
 * @property friction Per-frame velocity decay applied when no horizontal input AND `_grounded` is
 *   true (D-04 ground-friction path). Default 8 — decelerates over ~16 frames.
 * @property airFriction Per-frame velocity decay applied when no horizontal input AND `_grounded`
 *   is false (D-04 air-vs-ground split). Default 0 — matches reference `player.c` ground-only
 *   friction behaviour.
 * @property walkFrameCount Number of frames in the walk-cycle animation (default 3 — frames 0/1/2).
 *   PlatformerVisitor's walk-cycle emission rolls `_walkFrameIdx` modulo this count.
 * @property cyclePeriod Number of game frames per walk-cycle advance (default 6). The visitor emits
 *   `if (_threeFrameCounter >= cyclePeriod) { reset; _walkFrameIdx++ }`.
 */
data class PlatformerInputConfig(
    val walkSpeed: Int = 128,
    val friction: Int = 8,
    val airFriction: Int = 0,
    val walkFrameCount: Int = 3,
    val cyclePeriod: Int = 6,
)

// =============================================================================
// PLATFORM TYPES
// =============================================================================

/**
 * Platform behaviour categories.
 * - [SOLID]: Impassable from all directions.
 * - [ONE_WAY]: Can be jumped through from below; blocks from above only.
 * - [MOVING]: Follows a fixed patrol path (defined per-platform).
 * - [CRUMBLING]: Solid until stood on, then breaks after [PlatformDef.crumbleDelay] frames.
 */
enum class PlatformType {
    SOLID,
    ONE_WAY,
    MOVING,
    CRUMBLING,
}

/**
 * Definition of a named platform instance.
 *
 * @property id Unique platform identifier.
 * @property type Behaviour category (solid, one-way, moving, crumbling).
 * @property moveSpeed Movement speed for [PlatformType.MOVING] platforms (pixels/frame).
 * @property crumbleDelay Frames until a [PlatformType.CRUMBLING] platform disappears.
 * @property crumbleRespawn Frames until a crumbled platform reappears (0 = no respawn).
 */
data class PlatformDef(
    val id: String,
    val type: PlatformType = PlatformType.SOLID,
    val moveSpeed: Int = 1,
    val crumbleDelay: Int = 30,
    val crumbleRespawn: Int = 120,
)

// =============================================================================
// CAMERA TYPES
// =============================================================================

/**
 * Camera scrolling strategy.
 * - [SMOOTH_FOLLOW]: Camera smoothly follows the player with a configurable dead zone.
 * - [SCREEN_LOCK]: Camera snaps to full-screen boundaries (classic NES/SNES style).
 */
enum class CameraScrollMode {
    SMOOTH_FOLLOW,
    SCREEN_LOCK,
}

/**
 * Allowed scroll axis/axes.
 * - [HORIZONTAL]: Left/right scrolling only.
 * - [VERTICAL]: Up/down scrolling only.
 * - [MULTI]: Both axes scroll (multi-directional).
 */
enum class ScrollDirection {
    HORIZONTAL,
    VERTICAL,
    MULTI,
}

/**
 * A single parallax background layer.
 *
 * @property assetId Asset reference ID for the background graphic.
 * @property scrollSpeedX Horizontal scroll speed relative to camera (0 = fixed, 1 = locked to
 *   camera).
 * @property scrollSpeedY Vertical scroll speed relative to camera.
 */
data class ParallaxLayer(val assetId: String, val scrollSpeedX: Int = 50, val scrollSpeedY: Int = 0)

/**
 * Camera configuration for a platformer level.
 *
 * @property mode Camera scrolling strategy.
 * @property deadZoneX Horizontal pixels from center before camera starts following (smooth mode).
 * @property deadZoneY Vertical pixels from center before camera starts following (smooth mode).
 * @property scrollDirections Which axes scroll. Default is horizontal-only.
 * @property parallaxLayers Background parallax layers rendered behind the main tilemap.
 */
data class PlatformerCameraConfig(
    val mode: CameraScrollMode = CameraScrollMode.SMOOTH_FOLLOW,
    val deadZoneX: Int = 8,
    val deadZoneY: Int = 16,
    val scrollDirections: ScrollDirection = ScrollDirection.HORIZONTAL,
    val parallaxLayers: List<ParallaxLayer> = emptyList(),
)

// =============================================================================
// HAZARD TYPES
// =============================================================================

/**
 * Marks a tile as a hazard that damages or kills the player on contact.
 *
 * @property id Unique hazard identifier.
 * @property tileId Tile map ID that triggers this hazard.
 * @property damage HP damage dealt per contact frame. Ignored when [instant] is true.
 * @property instant If true, player dies immediately regardless of HP.
 */
data class HazardDef(
    val id: String,
    val tileId: Int,
    val damage: Int = 1,
    val instant: Boolean = false,
)

// =============================================================================
// LADDER TYPES
// =============================================================================

/**
 * Configuration for climbable ladder tiles.
 *
 * @property climbSpeed Vertical movement speed while on a ladder (pixels/frame).
 * @property tileId Tile map ID that is treated as a ladder.
 */
data class LadderConfig(val climbSpeed: Int = 2, val tileId: Int = 0)

// =============================================================================
// GOAL ZONE TYPES
// =============================================================================

/**
 * A rectangular trigger zone that fires level-completion logic.
 *
 * @property id Unique goal-zone identifier.
 * @property x Left edge of the goal zone (pixels).
 * @property y Top edge of the goal zone (pixels).
 * @property width Width of the goal zone (pixels).
 * @property height Height of the goal zone (pixels).
 */
data class GoalZoneDef(
    val id: String,
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 16,
    val height: Int = 16,
)

// =============================================================================
// COLLECTIBLE TYPES
// =============================================================================

/**
 * Broad category for collectible objects.
 * - [COIN]: Currency pickup.
 * - [POWER_UP]: Temporary or permanent ability/stat modifier.
 * - [CHECKPOINT]: Marks the respawn point; awards no score by itself.
 * - [KEY]: Unlocks doors or gated zones.
 */
enum class CollectibleType {
    COIN,
    POWER_UP,
    CHECKPOINT,
    KEY,
}

/**
 * Definition of a collectible item placed in the level.
 *
 * Acts as a facade over the shared engine PickupDef — codegen integration is wired in Plan 10.
 *
 * @property id Unique collectible identifier.
 * @property type Category of this collectible.
 * @property value Score or resource amount awarded on pickup.
 * @property tileId Tile map ID for the collectible sprite.
 */
data class CollectibleDef(
    val id: String,
    val type: CollectibleType = CollectibleType.COIN,
    val value: Int = 1,
    val tileId: Int = 0,
)
