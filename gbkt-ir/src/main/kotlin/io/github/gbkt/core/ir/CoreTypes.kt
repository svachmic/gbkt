/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// GBC COLOR TYPES - Game Boy Color RGB555 support
// =============================================================================

/**
 * RGB555 color for Game Boy Color. Each color component uses 5 bits (0-31), stored as:
 * 0bbb_bbgg_gggr_rrrr Total: 15-bit color (32768 colors).
 *
 * Relocated from gbkt-core io.github.gbkt.core.ir.GBCColor (v1) to gbkt-ir (v2).
 */
@JvmInline
value class GBCColor(val rgb555: Int) {
    init {
        require(rgb555 in 0..0x7FFF) { "RGB555 must be 0-32767, got $rgb555" }
    }

    val red: Int
        get() = rgb555 and 0x1F

    val green: Int
        get() = (rgb555 shr 5) and 0x1F

    val blue: Int
        get() = (rgb555 shr 10) and 0x1F

    /** Convert to hex string for C output */
    fun toHex(): String = "0x${rgb555.toString(16).padStart(4, '0').uppercase()}"

    override fun toString() = "GBCColor(r=$red, g=$green, b=$blue)"

    companion object {
        /** Create from RGB888 (standard 8-bit per channel) */
        fun fromRGB888(r: Int, g: Int, b: Int): GBCColor {
            require(r in 0..255 && g in 0..255 && b in 0..255) { "RGB888 components must be 0-255" }
            val r5 = (r shr 3) and 0x1F
            val g5 = (g shr 3) and 0x1F
            val b5 = (b shr 3) and 0x1F
            return GBCColor((b5 shl 10) or (g5 shl 5) or r5)
        }

        /** Create from hex color (0xRRGGBB) */
        fun fromHex(hex: Int): GBCColor =
            fromRGB888((hex shr 16) and 0xFF, (hex shr 8) and 0xFF, hex and 0xFF)

        /**
         * Check if converting an RGB888 hex color to RGB555 loses precision.
         *
         * Returns true when any of the low 3 bits of any channel are non-zero (i.e. the color
         * cannot be represented exactly in RGB555).
         *
         * Used by palette strict mode to warn about color quantization.
         *
         * @param hex RGB888 color as 0xRRGGBB integer
         */
        fun hasPrecisionLoss(hex: Int): Boolean {
            val r = (hex shr 16) and 0xFF
            val g = (hex shr 8) and 0xFF
            val b = hex and 0xFF
            return (r and 0x07) != 0 || (g and 0x07) != 0 || (b and 0x07) != 0
        }

        /**
         * Check if converting [r], [g], [b] (RGB888, 0-255 each) to RGB555 loses precision.
         *
         * Returns true when any of the low 3 bits of any channel are non-zero.
         */
        fun hasPrecisionLoss(r: Int, g: Int, b: Int): Boolean =
            (r and 0x07) != 0 || (g and 0x07) != 0 || (b and 0x07) != 0

        // Common colors
        val WHITE = GBCColor(0x7FFF)
        val BLACK = GBCColor(0x0000)
        val RED = fromRGB888(255, 0, 0)
        val GREEN = fromRGB888(0, 255, 0)
        val BLUE = fromRGB888(0, 0, 255)
        val LIGHT_GRAY = fromRGB888(192, 192, 192)
        val DARK_GRAY = fromRGB888(96, 96, 96)
    }
}

/** Palette type - sprites and backgrounds have separate palette banks. */
enum class PaletteType {
    SPRITE,
    BACKGROUND,
}

/**
 * GBC mode configuration.
 * - DISABLED: Classic DMG grayscale only
 * - COMPATIBLE: Works on both DMG and GBC (uses -Wm-yc flag)
 * - ONLY: GBC exclusive, won't run on DMG (uses -Wm-yC flag)
 */
enum class GBCMode {
    DISABLED,
    COMPATIBLE,
    ONLY,
}

// =============================================================================
// MOVEMENT TYPES - Per-actor movement configuration
// =============================================================================

/**
 * Movement style for an actor.
 * - GRID: tile-aligned movement (NPCs, dungeon crawlers)
 * - SMOOTH: pixel-level movement (player characters)
 * - PHYSICS: velocity-based movement — d-pad applies acceleration to velocity, with gravity and
 *   fall clamping. Uses [PhysicsConfig] parameters (defaults if not set).
 */
enum class MovementStyle {
    GRID,
    SMOOTH,
    PHYSICS,
}

/**
 * Diagonal movement normalization mode for SMOOTH movement with acceleration.
 * - RAW: Diagonal movement applies full acceleration on both axes — faster than cardinal movement
 *   (approximately 1.41x). Simpler and cheaper to compute.
 * - NORMALIZED: Diagonal movement scales each axis by ~0.707 (181/256 integer approximation) so
 *   speed is consistent in all directions. Slightly more CPU cost per frame.
 */
enum class DiagonalMode {
    RAW,
    NORMALIZED,
}

/**
 * Acceleration/friction movement configuration for SMOOTH movement style.
 *
 * When present on [MovementConfig], the SMOOTH branch generates velocity-variable-based movement:
 * d-pad applies acceleration each frame, releasing the d-pad decelerates via friction, and velocity
 * is clamped to [speed] pixels/frame.
 *
 * All values use integer math — no floats.
 *
 * @param speed Maximum velocity in pixels/frame (clamp bound). Matches [MovementConfig.speed].
 * @param acceleration Pixels/frame² added to velocity when d-pad is held.
 * @param friction Pixels/frame subtracted from velocity magnitude when d-pad is released (toward
 *   zero, never overshoots).
 * @param diagonalMode Whether to scale diagonal velocity to match cardinal speed ([NORMALIZED]) or
 *   allow full per-axis acceleration ([RAW]).
 */
data class SmoothMovementConfig(
    val speed: Int,
    val acceleration: Int,
    val friction: Int,
    val diagonalMode: DiagonalMode = DiagonalMode.RAW,
    val fixedPointMode: FixedPointMode = FixedPointMode.INTEGER,
)

/**
 * Per-actor movement configuration.
 *
 * When [movementConfig] is non-null on [ActorIR], the backend generates an
 * `update_movement_{actorId}()` function that reads d-pad input and updates position variables.
 *
 * @param style How movement is applied: grid-aligned tile steps, smooth pixel movement, or physics.
 * @param speed Pixels per step (grid) or pixels per frame (smooth). Defaults to 4.
 * @param tileSize Tile size in pixels for grid-aligned snapping. Defaults to 8.
 * @param smoothConfig When non-null and [style] is [MovementStyle.SMOOTH], uses
 *   acceleration/friction model instead of direct pixel stepping.
 */
data class MovementConfig(
    val style: MovementStyle,
    val speed: Int = 4,
    val tileSize: Int = 8,
    val smoothConfig: SmoothMovementConfig? = null,
)

// =============================================================================
// ANIMATION TYPES - Per-actor animation state machine
// =============================================================================

/**
 * A condition-based automatic transition between animation states.
 *
 * When [condition] is non-null, the generated state machine checks this expression every frame
 * inside the current state's switch case. If true, it transitions to [toState] automatically.
 *
 * @param fromState The state this transition originates from.
 * @param toState The state to transition to when [condition] is true (or on manual trigger).
 * @param condition IR expression evaluated each frame. When true, auto-transition fires.
 */
data class AnimTransition(val fromState: String, val toState: String, val condition: Expr? = null)

/**
 * A single animation state in a state machine.
 *
 * Frames are identified by index within a sprite sheet. The animation cycles through frames
 * [startFrame]..[endFrame] at the rate of one frame update every [speed] game frames.
 *
 * @param name State identifier used in [SetAnimationState] and transitions.
 * @param startFrame First frame index to display in this state.
 * @param endFrame Last frame index to display in this state (inclusive).
 * @param speed Game frames between each animation frame update. Defaults to 8.
 * @param loop Whether to cycle back to [startFrame] after [endFrame]. Defaults to true.
 * @param transitions Outgoing transitions from this state (condition-based auto-transitions).
 */
data class AnimationStateDef(
    val name: String,
    val startFrame: Int,
    val endFrame: Int,
    val speed: Int = 8,
    val loop: Boolean = true,
    val transitions: List<AnimTransition> = emptyList(),
)

// =============================================================================
// FIXED-POINT TYPES - Sub-pixel precision for movement
// =============================================================================

/**
 * Fixed-point arithmetic mode for sub-pixel movement precision.
 *
 * Game Boy hardware has no floating-point unit. Fixed-point arithmetic emulates fractional values
 * using integer variables. Velocity accumulates in fractional accumulators; the integer pixel
 * position is extracted via bit shift.
 * - [INTEGER]: No sub-pixel precision (default). Integer pixel positions only. Existing behavior.
 * - [FP44]: 4.4 fixed-point (UINT8). 16 sub-pixels per pixel. Shift = `>> 4`. Suitable for moderate
 *   precision; uses 8-bit variables (minimal RAM).
 * - [FP88]: 8.8 fixed-point (UINT16). 256 sub-pixels per pixel. Shift = `>> 8`. Higher precision;
 *   uses 16-bit variables (more RAM). Useful for gravity accumulation and slow acceleration.
 */
enum class FixedPointMode {
    INTEGER,
    FP44,
    FP88,
}

// =============================================================================
// PHYSICS TYPES - Per-actor physics configuration
// =============================================================================

/**
 * Wall response mode for physics actors when they contact a wall tile.
 * - [SLIDE]: Horizontal velocity is zeroed on wall contact but vertical velocity is preserved,
 *   allowing the actor to slide down along the wall (useful for wall-jump mechanics).
 * - [STOP]: Both horizontal and vertical velocities are zeroed on wall contact.
 */
enum class WallResponse {
    SLIDE,
    STOP,
}

/**
 * Per-actor physics configuration.
 *
 * When [physicsConfig] is non-null on [ActorIR], the backend generates:
 * - Signed velocity variables `_actorId_vx` (INT8) and `_actorId_vy` (INT8).
 * - `#define` constants for acceleration, gravity, max fall speed, and bounce coefficient.
 * - Optional advanced physics variables for coyote time, wall contact, and jump-held tracking.
 *
 * All values use integer math — no floats. Bounce coefficient is UINT8 0-255 where 256 = 1.0 (e.g.
 * 204 ≈ 0.8 bounce).
 *
 * @param velocityX Initial X velocity in pixels/frame (signed).
 * @param velocityY Initial Y velocity in pixels/frame (signed).
 * @param accelerationX X acceleration in pixels/frame² applied to VX each frame (signed).
 * @param accelerationY Y acceleration in pixels/frame² applied to VY each frame (signed).
 * @param gravity Pixels/frame² added to VY each frame (positive = down).
 * @param bounce Bounce coefficient 0-255 (value * 256 = fraction, e.g. 204 ≈ 0.8).
 * @param maxFallSpeed Maximum downward velocity in pixels/frame (clamped).
 * @param platformerMode When true, uses vertical gravity axis (platformer). When false, friction
 *   applied to both axes with no gravity (top-down). Defaults to false.
 * @param variableJump When true, releasing the jump button early cuts upward velocity by
 *   [jumpCutMultiplier]. Enables variable-height jump like Mario/Celeste. Defaults to false.
 * @param jumpCutMultiplier Divisor applied to upward vy when jump button released early (e.g. 2
 *   halves the upward velocity). Only used when [variableJump] is true. Defaults to 2.
 * @param coyoteFrames Grace frames the actor can still jump after walking off a ledge. 0 disables
 *   coyote time. Defaults to 0.
 * @param wallResponse What happens when the actor contacts a wall tile. [WallResponse.SLIDE]
 *   preserves vertical velocity (actor slides down); [WallResponse.STOP] zeros both velocities.
 *   Defaults to [WallResponse.STOP].
 * @param wallJump When true, pressing jump while sliding on a wall kicks the actor off in the
 *   opposite direction with [wallJumpVelocityX] and [wallJumpVelocityY]. Defaults to false.
 * @param wallJumpVelocityX Horizontal kick velocity on wall-jump (pixels/frame). Defaults to 0.
 * @param wallJumpVelocityY Vertical kick velocity on wall-jump (pixels/frame, positive = up).
 *   Defaults to 0.
 * @param fixedPointMode Sub-pixel precision mode. [FixedPointMode.INTEGER] (default) uses integer
 *   pixels. [FixedPointMode.FP44] adds 4.4 fixed-point fractional accumulators (UINT8/INT8, 16
 *   sub-pixels/pixel). [FixedPointMode.FP88] uses 8.8 (UINT16/INT16, 256 sub-pixels/pixel).
 */
data class PhysicsConfig(
    val velocityX: Int = 0,
    val velocityY: Int = 0,
    val accelerationX: Int = 0,
    val accelerationY: Int = 0,
    val gravity: Int = 0,
    val bounce: Int = 0,
    val maxFallSpeed: Int = 8,
    val platformerMode: Boolean = false,
    val variableJump: Boolean = false,
    val jumpCutMultiplier: Int = 2,
    val coyoteFrames: Int = 0,
    val wallResponse: WallResponse = WallResponse.STOP,
    val wallJump: Boolean = false,
    val wallJumpVelocityX: Int = 0,
    val wallJumpVelocityY: Int = 0,
    val fixedPointMode: FixedPointMode = FixedPointMode.INTEGER,
)

// =============================================================================
// PATHFINDING TYPES - NPC waypoint patrol routes
// =============================================================================

/**
 * A waypoint patrol route for an NPC actor.
 *
 * The NPC visits each (x, y) waypoint position in order, then optionally loops back to the first.
 * Positions are in pixels. The backend converts to tile coordinates when generating path checks.
 *
 * @param points Ordered list of (x, y) pixel positions to visit.
 * @param loop When true, the NPC wraps back to point 0 after the last point.
 */
data class WaypointRoute(val points: List<Pair<Int, Int>>, val loop: Boolean = true)

/** A 4-color GBC palette. GBC has 8 sprite palettes and 8 background palettes. */
data class GBCPalette(
    val name: String,
    val colors: List<GBCColor>,
    val slot: Int = -1, // -1 = auto-assign, 0-7 = explicit slot
    val type: PaletteType = PaletteType.SPRITE,
) {
    init {
        require(colors.size == 4) { "GBC palette must have exactly 4 colors, got ${colors.size}" }
        require(slot in -1..7) { "Palette slot must be -1 (auto) or 0-7, got $slot" }
    }

    /** Convert to GBDK-compatible RGB555 array format */
    fun toRGB555Array(): IntArray = colors.map { it.rgb555 }.toIntArray()

    /** Generate C array literal */
    fun toCArrayLiteral(): String = colors.joinToString(", ") { it.toHex() }
}
