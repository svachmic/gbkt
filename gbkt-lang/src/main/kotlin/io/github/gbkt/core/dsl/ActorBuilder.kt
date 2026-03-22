/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "TooManyFunctions"
) // Operator extensions require one function per operator/type combination

package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.AnimTransition
import io.github.gbkt.core.ir.AnimationStateDef
import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.CallExpr
import io.github.gbkt.core.ir.DiagonalMode
import io.github.gbkt.core.ir.EntityCollisionConfig
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.FixedPointMode
import io.github.gbkt.core.ir.GBCPalette
import io.github.gbkt.core.ir.HitboxDef
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.MovementConfig
import io.github.gbkt.core.ir.MovementStyle
import io.github.gbkt.core.ir.PhysicsConfig
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.PropertyAccessExpr
import io.github.gbkt.core.ir.SizeDef
import io.github.gbkt.core.ir.SmoothMovementConfig
import io.github.gbkt.core.ir.SpriteDef
import io.github.gbkt.core.ir.VarRef
import io.github.gbkt.core.ir.VarType
import io.github.gbkt.core.ir.VariableDef
import io.github.gbkt.core.ir.WallResponse
import io.github.gbkt.core.ir.WaypointRoute
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// ACTOR PROPERTY REFERENCE
// =============================================================================

/**
 * Typed reference to a property on an actor entity (e.g. `ball.x`, `player.visible`).
 *
 * Provides the same operator extensions as [AssignableVar] so that actor property reads and writes
 * use the same DSL idioms:
 * ```kotlin
 * ball.x += ballDx        // compound assign
 * ball.visible set false  // boolean assign
 * whenever(ball.x isAbove 160) { ... }  // comparison expression
 * ```
 *
 * The [objectId] is the actor ID (e.g. "ball"), [property] is the field name (e.g. "x").
 */
data class ActorPropertyRef(val objectId: String, val property: String) {
    /** Returns this property as a [PropertyAccessExpr] expression for use in script ops. */
    fun toExpr(): Expr = PropertyAccessExpr(objectId, property)

    /** Compound name in `objectId.property` form (used by ScriptBuilder.assign). */
    val name: String
        get() = "$objectId.$property"
}

// =============================================================================
// ACTOR PROPERTY REF OPERATOR EXTENSIONS
// =============================================================================

// --- Assignment operators (emit into ScriptBuilderContext.current) ---

/**
 * Sets [this] actor property to [value] inside the active [ScriptBuilder].
 *
 * Emits `Assign("objectId.property", value, AssignOp.SET)`. Requires a ScriptBuilder context.
 */
infix fun ActorPropertyRef.set(value: Expr) {
    ScriptBuilderContext.current?.assign(name, value, AssignOp.SET)
        ?: error("set() called outside a ScriptBuilder block")
}

/** Sets [this] actor property to [value] (Int auto-wrap). */
infix fun ActorPropertyRef.set(value: Int) = set(Literal(value))

/** Sets [this] actor property to [other]'s current value. */
infix fun ActorPropertyRef.set(other: AssignableVar) = set(other.toExpr())

/**
 * Sets [this] actor property to 1 if [value] is true, 0 if false.
 *
 * Enables idiomatic `ball.visible set true` / `enemy.visible set false` in DSL code.
 */
infix fun ActorPropertyRef.set(value: Boolean) = set(Literal(if (value) 1 else 0))

/** Adds [value] to [this] actor property (compound assignment). */
operator fun ActorPropertyRef.plusAssign(value: Expr) {
    ScriptBuilderContext.current?.assign(name, value, AssignOp.ADD)
        ?: error("+= called outside a ScriptBuilder block")
}

/** Adds [value] (Int) to [this] actor property. */
operator fun ActorPropertyRef.plusAssign(value: Int) = plusAssign(Literal(value))

/** Adds [other]'s value to [this] actor property. */
operator fun ActorPropertyRef.plusAssign(other: AssignableVar) = plusAssign(other.toExpr())

/** Subtracts [value] from [this] actor property (compound assignment). */
operator fun ActorPropertyRef.minusAssign(value: Expr) {
    ScriptBuilderContext.current?.assign(name, value, AssignOp.SUB)
        ?: error("-= called outside a ScriptBuilder block")
}

/** Subtracts [value] (Int) from [this] actor property. */
operator fun ActorPropertyRef.minusAssign(value: Int) = minusAssign(Literal(value))

/** Subtracts [other]'s value from [this] actor property. */
operator fun ActorPropertyRef.minusAssign(other: AssignableVar) = minusAssign(other.toExpr())

/** Multiplies [this] actor property by [value] (compound assignment). */
operator fun ActorPropertyRef.timesAssign(value: Expr) {
    ScriptBuilderContext.current?.assign(name, value, AssignOp.MUL)
        ?: error("*= called outside a ScriptBuilder block")
}

/** Multiplies [this] actor property by [value] (Int). */
operator fun ActorPropertyRef.timesAssign(value: Int) = timesAssign(Literal(value))

/** Divides [this] actor property by [value] (compound assignment). */
operator fun ActorPropertyRef.divAssign(value: Expr) {
    ScriptBuilderContext.current?.assign(name, value, AssignOp.DIV)
        ?: error("/= called outside a ScriptBuilder block")
}

/** Divides [this] actor property by [value] (Int). */
operator fun ActorPropertyRef.divAssign(value: Int) = divAssign(Literal(value))

/** Applies modulo [value] to [this] actor property (compound assignment). */
operator fun ActorPropertyRef.remAssign(value: Expr) {
    ScriptBuilderContext.current?.assign(name, value, AssignOp.MOD)
        ?: error("%= called outside a ScriptBuilder block")
}

/** Applies modulo [value] (Int) to [this] actor property. */
operator fun ActorPropertyRef.remAssign(value: Int) = remAssign(Literal(value))

/**
 * Increments [this] actor property by 1 (side-effect into active ScriptBuilder).
 *
 * Works with `var` delegates. Emits `Assign(name, VarRef(name) + 1, SET)`.
 */
operator fun ActorPropertyRef.inc(): ActorPropertyRef {
    ScriptBuilderContext.current?.assign(
        name,
        BinaryExpr(VarRef(name), BinaryOp.ADD, Literal(1)),
        AssignOp.SET,
    ) ?: error("++ called outside a ScriptBuilder block")
    return this
}

/**
 * Decrements [this] actor property by 1 (side-effect into active ScriptBuilder).
 *
 * Works with `var` delegates. See [inc] for semantics.
 */
operator fun ActorPropertyRef.dec(): ActorPropertyRef {
    ScriptBuilderContext.current?.assign(
        name,
        BinaryExpr(VarRef(name), BinaryOp.SUB, Literal(1)),
        AssignOp.SET,
    ) ?: error("-- called outside a ScriptBuilder block")
    return this
}

// --- Arithmetic operators (return Expr for conditions/right-side use) ---

operator fun ActorPropertyRef.plus(other: Expr): Expr = toExpr() + other

operator fun ActorPropertyRef.plus(other: Int): Expr = toExpr() + other

operator fun ActorPropertyRef.plus(other: AssignableVar): Expr = toExpr() + other.toExpr()

operator fun ActorPropertyRef.minus(other: Expr): Expr = toExpr() - other

operator fun ActorPropertyRef.minus(other: Int): Expr = toExpr() - other

operator fun ActorPropertyRef.minus(other: AssignableVar): Expr = toExpr() - other.toExpr()

operator fun ActorPropertyRef.times(other: Expr): Expr = toExpr() * other

operator fun ActorPropertyRef.times(other: Int): Expr = toExpr() * other

operator fun ActorPropertyRef.times(other: AssignableVar): Expr = toExpr() * other.toExpr()

operator fun ActorPropertyRef.div(other: Expr): Expr = toExpr() / other

operator fun ActorPropertyRef.div(other: Int): Expr = toExpr() / other

operator fun ActorPropertyRef.div(other: AssignableVar): Expr = toExpr() / other.toExpr()

operator fun ActorPropertyRef.rem(other: Expr): Expr = toExpr() % other

operator fun ActorPropertyRef.rem(other: Int): Expr = toExpr() % other

operator fun ActorPropertyRef.rem(other: AssignableVar): Expr = toExpr() % other.toExpr()

operator fun ActorPropertyRef.unaryMinus(): Expr = -toExpr()

// --- Comparison operators (return Expr for whenever/ifOp conditions) ---

infix fun ActorPropertyRef.isAbove(other: Int): Expr = toExpr() isAbove other

infix fun ActorPropertyRef.isAbove(other: Expr): Expr = toExpr() isAbove other

infix fun ActorPropertyRef.isAbove(other: AssignableVar): Expr = toExpr() isAbove other.toExpr()

infix fun ActorPropertyRef.isAbove(other: ActorPropertyRef): Expr = toExpr() isAbove other.toExpr()

infix fun ActorPropertyRef.isBelow(other: Int): Expr = toExpr() isBelow other

infix fun ActorPropertyRef.isBelow(other: Expr): Expr = toExpr() isBelow other

infix fun ActorPropertyRef.isBelow(other: AssignableVar): Expr = toExpr() isBelow other.toExpr()

infix fun ActorPropertyRef.isBelow(other: ActorPropertyRef): Expr = toExpr() isBelow other.toExpr()

infix fun ActorPropertyRef.isAtLeast(other: Int): Expr = toExpr() isAtLeast other

infix fun ActorPropertyRef.isAtLeast(other: Expr): Expr = toExpr() isAtLeast other

infix fun ActorPropertyRef.isAtLeast(other: AssignableVar): Expr = toExpr() isAtLeast other.toExpr()

infix fun ActorPropertyRef.isAtLeast(other: ActorPropertyRef): Expr =
    toExpr() isAtLeast other.toExpr()

infix fun ActorPropertyRef.isAtMost(other: Int): Expr = toExpr() isAtMost other

infix fun ActorPropertyRef.isAtMost(other: Expr): Expr = toExpr() isAtMost other

infix fun ActorPropertyRef.isAtMost(other: AssignableVar): Expr = toExpr() isAtMost other.toExpr()

infix fun ActorPropertyRef.isAtMost(other: ActorPropertyRef): Expr =
    toExpr() isAtMost other.toExpr()

infix fun ActorPropertyRef.isEqualTo(other: Int): Expr = toExpr() isEqualTo other

infix fun ActorPropertyRef.isEqualTo(other: Expr): Expr = toExpr() isEqualTo other

infix fun ActorPropertyRef.isEqualTo(other: AssignableVar): Expr = toExpr() isEqualTo other.toExpr()

infix fun ActorPropertyRef.isEqualTo(other: ActorPropertyRef): Expr =
    toExpr() isEqualTo other.toExpr()

infix fun ActorPropertyRef.isNotEqualTo(other: Int): Expr = toExpr() isNotEqualTo other

infix fun ActorPropertyRef.isNotEqualTo(other: Expr): Expr = toExpr() isNotEqualTo other

infix fun ActorPropertyRef.isNotEqualTo(other: AssignableVar): Expr =
    toExpr() isNotEqualTo other.toExpr()

infix fun ActorPropertyRef.isNotEqualTo(other: ActorPropertyRef): Expr =
    toExpr() isNotEqualTo other.toExpr()

// --- Bitwise operators ---

infix fun ActorPropertyRef.and(other: Expr): Expr = toExpr() and other

infix fun ActorPropertyRef.and(other: Int): Expr = toExpr() and Literal(other)

infix fun ActorPropertyRef.and(other: AssignableVar): Expr = toExpr() and other.toExpr()

infix fun ActorPropertyRef.or(other: Expr): Expr = toExpr() or other

infix fun ActorPropertyRef.or(other: Int): Expr = toExpr() or Literal(other)

infix fun ActorPropertyRef.or(other: AssignableVar): Expr = toExpr() or other.toExpr()

infix fun ActorPropertyRef.xor(other: Expr): Expr = toExpr() xor other

infix fun ActorPropertyRef.xor(other: Int): Expr = toExpr() xor Literal(other)

infix fun ActorPropertyRef.shl(other: Expr): Expr = toExpr() shl other

infix fun ActorPropertyRef.shl(other: Int): Expr = toExpr() shl Literal(other)

infix fun ActorPropertyRef.shr(other: Expr): Expr = toExpr() shr other

infix fun ActorPropertyRef.shr(other: Int): Expr = toExpr() shr Literal(other)

// =============================================================================
// ACTOR REFERENCE
// =============================================================================

/**
 * Lightweight typed reference to an actor entity.
 *
 * Returned by [GameBuilder.actor] for use in [ScriptBuilder] movement operations (e.g.
 * `moveBy(player, dx, dy)`) without requiring string literals.
 *
 * Typed property accessors [x], [y], and [visible] return [ActorPropertyRef] instances that support
 * the full operator extension set — assignment, arithmetic, and comparison.
 */
data class ActorRef(val id: String) {
    override fun toString(): String = id

    /** Reference to this actor's x-coordinate property. */
    val x: ActorPropertyRef
        get() = ActorPropertyRef(id, "x")

    /** Reference to this actor's y-coordinate property. */
    val y: ActorPropertyRef
        get() = ActorPropertyRef(id, "y")

    /** Reference to this actor's visibility property. */
    val visible: ActorPropertyRef
        get() = ActorPropertyRef(id, "visible")
}

// =============================================================================
// ACTOR REF EXTENSION FUNCTIONS
// =============================================================================

/**
 * Teleports [this] actor to an absolute position.
 *
 * Emits a [io.github.gbkt.core.ir.SetPosition] op. Requires a ScriptBuilder context.
 */
fun ActorRef.moveTo(x: Int, y: Int) {
    ScriptBuilderContext.current?.setPosition(id, Literal(x), Literal(y))
        ?: error("moveTo() called outside a ScriptBuilder block")
}

/**
 * Returns an expression that is true when [this] actor collides with [other].
 *
 * Emits a `CallExpr("collides", [VarRef(this.id), VarRef(other.id)])` for use in `whenever()`
 * conditions.
 */
fun ActorRef.collides(other: ActorRef): Expr =
    CallExpr("collides", listOf(VarRef(id), VarRef(other.id)))

// =============================================================================
// MOVEMENT BUILDER
// =============================================================================

/**
 * Builder for per-actor movement configuration.
 *
 * Used inside `actor { movement { ... } }` blocks to configure how the actor moves in response to
 * d-pad input. The built [MovementConfig] is stored on [ActorIR.movementConfig] and used by the
 * backend to generate `update_movement_{actorId}()`.
 *
 * ```kotlin
 * val player by actor {
 *     movement {
 *         style(MovementStyle.SMOOTH)
 *         speed(2)
 *     }
 * }
 * ```
 */
@GbktDsl
class MovementBuilder {
    private var style: MovementStyle = MovementStyle.GRID
    private var speed: Int = 4
    private var tileSize: Int = 8
    private var accelerationValue: Int? = null
    private var frictionValue: Int? = null
    private var diagonalModeValue: DiagonalMode = DiagonalMode.RAW
    private var fixedPointModeValue: FixedPointMode = FixedPointMode.INTEGER

    /** Sets the movement style (GRID, SMOOTH, or PHYSICS). */
    fun style(s: MovementStyle) {
        style = s
    }

    /** Sets the movement speed in pixels per step (grid) or max velocity pixels/frame (smooth). */
    fun speed(pixelsPerStep: Int) {
        speed = pixelsPerStep
    }

    /** Sets the tile size in pixels for grid-aligned movement. */
    fun tileSize(px: Int) {
        tileSize = px
    }

    /**
     * Sets the acceleration value (pixels/frame²) for SMOOTH movement.
     *
     * When set alongside [style(MovementStyle.SMOOTH)], the backend generates velocity-variable-
     * based movement instead of direct pixel stepping. D-pad held applies acceleration each frame;
     * releasing the d-pad decelerates via [friction].
     *
     * ```kotlin
     * movement {
     *     style(MovementStyle.SMOOTH)
     *     speed(4)
     *     acceleration(1)
     *     friction(1)
     * }
     * ```
     */
    fun acceleration(pixelsPerFrameSq: Int) {
        accelerationValue = pixelsPerFrameSq
    }

    /**
     * Sets the friction value (pixels/frame) for SMOOTH movement with acceleration.
     *
     * Applied toward zero when the d-pad is released. Velocity never overshoots zero. Has no effect
     * unless [acceleration] is also called.
     */
    fun friction(pixelsPerFrame: Int) {
        frictionValue = pixelsPerFrame
    }

    /**
     * Sets the diagonal normalization mode for SMOOTH movement with acceleration.
     * - [DiagonalMode.RAW]: full acceleration on both axes (faster diagonal, cheaper).
     * - [DiagonalMode.NORMALIZED]: scale each axis by ~0.707 (181/256 integer approximation).
     *
     * Has no effect unless [acceleration] is also called.
     */
    fun diagonal(mode: DiagonalMode) {
        diagonalModeValue = mode
    }

    /**
     * Sets the fixed-point arithmetic mode for sub-pixel precision in SMOOTH movement.
     * - [FixedPointMode.INTEGER] (default): integer pixels only, existing behavior.
     * - [FixedPointMode.FP44]: 4.4 fixed-point, UINT8/INT8 fractional accumulators, `>> 4` shift.
     * - [FixedPointMode.FP88]: 8.8 fixed-point, UINT16/INT16 fractional accumulators, `>> 8` shift.
     *
     * Has no effect unless [acceleration] is also called (SMOOTH mode with smoothConfig).
     */
    fun fixedPoint(mode: FixedPointMode) {
        fixedPointModeValue = mode
    }

    /** Convenience: enables 4.4 fixed-point (UINT8, 16 sub-pixels/pixel, `>> 4` shift). */
    fun fixedPoint44() = fixedPoint(FixedPointMode.FP44)

    /** Convenience: enables 8.8 fixed-point (UINT16, 256 sub-pixels/pixel, `>> 8` shift). */
    fun fixedPoint88() = fixedPoint(FixedPointMode.FP88)

    internal fun build(): MovementConfig {
        val smoothConfig =
            if (accelerationValue != null && style == MovementStyle.SMOOTH) {
                SmoothMovementConfig(
                    speed = speed,
                    acceleration = accelerationValue!!,
                    friction = frictionValue ?: 0,
                    diagonalMode = diagonalModeValue,
                    fixedPointMode = fixedPointModeValue,
                )
            } else null
        return MovementConfig(style, speed, tileSize, smoothConfig)
    }
}

// =============================================================================
// PHYSICS BUILDER
// =============================================================================

/**
 * Builder for per-actor physics configuration.
 *
 * Used inside `actor { physics { ... } }` blocks to configure velocity, acceleration, gravity, and
 * bounce. The built [PhysicsConfig] is stored on [ActorIR.physicsConfig] and used by the backend to
 * generate velocity variables (`_actorId_vx`, `_actorId_vy`) and `#define` constants.
 *
 * ```kotlin
 * val ball by actor {
 *     physics {
 *         velocity(0, -2)         // initial velocity (pixels/frame)
 *         gravity(1)              // 1 pixel/frame² downward
 *         bounce(0.8f)            // 80% energy on bounce (stored as 204/256)
 *         maxFallSpeed(8)         // clamp downward velocity at 8 px/frame
 *     }
 * }
 * ```
 */
@GbktDsl
class PhysicsBuilder {
    private var vx: Int = 0
    private var vy: Int = 0
    private var ax: Int = 0
    private var ay: Int = 0
    private var grav: Int = 0
    private var bounceCoeff: Int = 0
    private var maxFall: Int = 8
    private var isPlatformerMode: Boolean = false
    private var isVariableJump: Boolean = false
    private var jumpCutMult: Int = 2
    private var coyoteFrameCount: Int = 0
    private var wallResponseMode: WallResponse = WallResponse.STOP
    private var isWallJump: Boolean = false
    private var wallJumpVx: Int = 0
    private var wallJumpVy: Int = 0
    private var fixedPointModeValue: FixedPointMode = FixedPointMode.INTEGER

    /** Sets the initial velocity in pixels/frame (signed). */
    fun velocity(dx: Int, dy: Int) {
        vx = dx
        vy = dy
    }

    /** Sets the acceleration applied to velocity each frame (pixels/frame²). */
    fun acceleration(ax: Int, ay: Int) {
        this.ax = ax
        this.ay = ay
    }

    /** Sets gravity in pixels/frame² added to VY each frame (positive = down). */
    fun gravity(pixelsPerFrameSq: Int) {
        grav = pixelsPerFrameSq
    }

    /**
     * Sets the bounce coefficient as a float (0.0 = no bounce, 1.0 = full bounce).
     *
     * Stored as UINT8 0-255 where 256 = 1.0 (e.g. 0.8f → 204).
     */
    fun bounce(coefficient: Float) {
        bounceCoeff = (coefficient * 256).toInt().coerceIn(0, 255)
    }

    /** Sets the maximum downward velocity in pixels/frame. */
    fun maxFallSpeed(speed: Int) {
        maxFall = speed
    }

    /**
     * Enables platformer mode: gravity applies to Y axis only.
     *
     * When false (default), top-down friction-based deceleration is used on both axes and no
     * gravity is applied. When true, gravity is applied to VY each frame (platformer behavior).
     */
    fun platformerMode(enabled: Boolean = true) {
        isPlatformerMode = enabled
    }

    /**
     * Enables variable-height jump: releasing the jump button early cuts upward velocity.
     *
     * When enabled, the generated code tracks whether the jump button is held. On release while
     * still moving upward (vy < 0), VY is divided by [jumpCutMultiplier] to cut jump height.
     */
    fun variableJump(enabled: Boolean = true) {
        isVariableJump = enabled
    }

    /**
     * Sets the divisor applied to upward VY when jump button is released early.
     *
     * Only used when [variableJump] is enabled. Defaults to 2 (halves upward velocity on release).
     */
    fun jumpCutMultiplier(n: Int) {
        jumpCutMult = n
    }

    /**
     * Sets the number of grace frames after walking off a ledge where jump is still allowed.
     *
     * 0 (default) disables coyote time. Higher values give more forgiving platformer feel.
     */
    fun coyoteFrames(n: Int) {
        coyoteFrameCount = n
    }

    /**
     * Sets what happens when the actor contacts a wall tile.
     *
     * [WallResponse.SLIDE] preserves vertical velocity so the actor slides down the wall.
     * [WallResponse.STOP] zeros both velocities (default).
     */
    fun wallResponse(response: WallResponse) {
        wallResponseMode = response
    }

    /**
     * Enables wall-jump: pressing jump while touching a wall kicks the actor in the opposite
     * direction with configurable velocity.
     *
     * Use [wallJumpVelocityX] and [wallJumpVelocityY] to set kick velocities.
     */
    fun wallJump(enabled: Boolean = true) {
        isWallJump = enabled
    }

    /** Sets the horizontal kick velocity on wall-jump in pixels/frame. */
    fun wallJumpVelocityX(n: Int) {
        wallJumpVx = n
    }

    /** Sets the vertical kick velocity on wall-jump in pixels/frame (positive = up). */
    fun wallJumpVelocityY(n: Int) {
        wallJumpVy = n
    }

    /**
     * Sets the fixed-point arithmetic mode for sub-pixel precision in PHYSICS movement.
     * - [FixedPointMode.INTEGER] (default): integer pixels only, existing behavior.
     * - [FixedPointMode.FP44]: 4.4 fixed-point, UINT8/INT8 fractional accumulators, `>> 4` shift.
     * - [FixedPointMode.FP88]: 8.8 fixed-point, UINT16/INT16 fractional accumulators, `>> 8` shift.
     *
     * In PHYSICS mode, fractional variables accumulate gravity and acceleration sub-pixel amounts
     * each frame; the integer pixel position is updated only when the accumulator overflows a
     * pixel.
     */
    fun fixedPoint(mode: FixedPointMode) {
        fixedPointModeValue = mode
    }

    /** Convenience: enables 4.4 fixed-point (UINT8, 16 sub-pixels/pixel, `>> 4` shift). */
    fun fixedPoint44() = fixedPoint(FixedPointMode.FP44)

    /** Convenience: enables 8.8 fixed-point (UINT16, 256 sub-pixels/pixel, `>> 8` shift). */
    fun fixedPoint88() = fixedPoint(FixedPointMode.FP88)

    internal fun build(): PhysicsConfig =
        PhysicsConfig(
            velocityX = vx,
            velocityY = vy,
            accelerationX = ax,
            accelerationY = ay,
            gravity = grav,
            bounce = bounceCoeff,
            maxFallSpeed = maxFall,
            platformerMode = isPlatformerMode,
            variableJump = isVariableJump,
            jumpCutMultiplier = jumpCutMult,
            coyoteFrames = coyoteFrameCount,
            wallResponse = wallResponseMode,
            wallJump = isWallJump,
            wallJumpVelocityX = wallJumpVx,
            wallJumpVelocityY = wallJumpVy,
            fixedPointMode = fixedPointModeValue,
        )
}

// =============================================================================
// ANIMATION STATE BUILDERS
// =============================================================================

/**
 * Builder for a single animation state.
 *
 * Configures frame range, speed, and loop settings for one named state.
 */
@GbktDsl
class AnimationStateBuilder(private val name: String) {
    private var startFrame: Int = 0
    private var endFrame: Int = 0
    private var speed: Int = 8
    private var loop: Boolean = true

    /** Sets the frame range using an IntRange. E.g. `frames(0..3)` */
    fun frames(range: IntRange) {
        startFrame = range.first
        endFrame = range.last
    }

    /** Sets frames per update (higher = slower animation). */
    fun speed(framesPerUpdate: Int) {
        speed = framesPerUpdate
    }

    /** Sets whether to loop back to startFrame after reaching endFrame. */
    fun loop(enabled: Boolean) {
        loop = enabled
    }

    internal fun build(): AnimationStateDef =
        AnimationStateDef(name, startFrame, endFrame, speed, loop)
}

/**
 * Builder for the animation state machine.
 *
 * Manages a list of named states and cross-state transitions (including condition-based
 * auto-transitions). Built result is a [List]<[AnimationStateDef]> with transitions attached to
 * their source states.
 *
 * ```kotlin
 * animationStates {
 *     state("idle") { frames(0..0); speed(8) }
 *     state("walk") { frames(1..4); speed(6) }
 *     transition("idle" to "walk") { dpad.any }   // auto-transition when d-pad held
 * }
 * ```
 */
@GbktDsl
class AnimationStatesBuilder {
    private val states: MutableList<AnimationStateDef> = mutableListOf()
    private val transitions: MutableList<AnimTransition> = mutableListOf()

    /** Defines a named animation state with its frame range and settings. */
    fun state(name: String, block: AnimationStateBuilder.() -> Unit) {
        states.add(AnimationStateBuilder(name).apply(block).build())
    }

    /**
     * Defines a condition-based auto-transition between states.
     *
     * The [conditionBlock] returns an [Expr] that is evaluated each frame by the generated state
     * machine. When the expression is true, the actor automatically transitions from [pair.first]
     * to [pair.second] and resets its frame and counter.
     *
     * ```kotlin
     * transition("idle" to "walk") { dpad.any }
     * transition("walk" to "idle") { dpad.none }
     * ```
     */
    fun transition(pair: Pair<String, String>, conditionBlock: () -> Expr) {
        val condition = conditionBlock()
        transitions.add(
            AnimTransition(fromState = pair.first, toState = pair.second, condition = condition)
        )
    }

    /**
     * Builds the list of [AnimationStateDef] with transitions attached to their source states.
     *
     * Transitions are matched to states by [AnimTransition.fromState] and attached to the
     * corresponding [AnimationStateDef.transitions] list.
     */
    internal fun build(): List<AnimationStateDef> {
        return states.map { state ->
            val stateTransitions = transitions.filter { it.fromState == state.name }
            if (stateTransitions.isEmpty()) state
            else state.copy(transitions = state.transitions + stateTransitions)
        }
    }
}

// =============================================================================
// SPRITE BUILDER
// =============================================================================

/**
 * Builder for sprite display configuration attached to an actor.
 *
 * Configures the display [SizeDef] and optional [HitboxDef].
 */
@GbktDsl
class SpriteBuilder(private val assetRef: AssetRef) {
    private var size: SizeDef = SizeDef(8, 8)
    private var hitbox: HitboxDef? = null
    private var frameWidth: Int? = null
    private var frameHeight: Int? = null

    /** Sets the display size in pixels. */
    fun size(width: Int, height: Int) {
        size = SizeDef(width, height)
    }

    /** Sets the hitbox rectangle relative to the actor's position. */
    fun hitbox(x: Int, y: Int, width: Int, height: Int) {
        hitbox = HitboxDef(x, y, width, height)
    }

    /**
     * Sets the per-animation-frame width in pixels for multi-frame sprite sheets.
     *
     * When specified alongside [frameHeight], the GBDK backend computes per-frame tile offsets:
     * `tiles_per_frame = (frameWidth / 8) * (frameHeight / 8)`. This allows `set_sprite_tile(slot,
     * base_tile + frame * tiles_per_frame)` to select frames.
     */
    fun frameWidth(width: Int) {
        frameWidth = width
    }

    /**
     * Sets the per-animation-frame height in pixels for multi-frame sprite sheets.
     *
     * See [frameWidth] for details on how frame offsets are computed.
     */
    fun frameHeight(height: Int) {
        frameHeight = height
    }

    internal fun build(): SpriteDef = SpriteDef(assetRef, size, hitbox, frameWidth, frameHeight)
}

// =============================================================================
// ACTOR BUILDER
// =============================================================================

/**
 * Builder for an actor (sprite entity) definition.
 *
 * Configures position, sprite asset, and hitbox. Returns [ActorIR] via [build].
 *
 * Custom properties can be added via [i8Prop] and [u8Prop] delegates:
 * ```kotlin
 * val ball by actor {
 *     position(80, 72)
 *     var dx by i8Prop(1)   // registers _ball_dx global, returns ActorPropertyRef
 * }
 * ```
 */
@GbktDsl
class ActorBuilder(val id: String) {
    private var position: PositionDef = PositionDef(0, 0)
    private var sprite: SpriteDef? = null
    private var hitbox: HitboxDef? = null
    private var movementConfig: MovementConfig? = null
    private var animationStatesList: List<AnimationStateDef> = emptyList()
    private var frameSpeedValue: Int? = null
    private var physicsConfigValue: PhysicsConfig? = null
    private var waypointRouteValue: WaypointRoute? = null
    private var followTargetIdValue: String? = null
    private var entityCollisionConfig: EntityCollisionConfig? = null
    private var paletteValue: GBCPalette? = null
    private var npcCollisionGroupIds: MutableList<String> = mutableListOf()
    private var npcCollidesWithNpcs: Boolean = false
    private var npcMass: Int = 1
    internal val customProps: MutableList<String> = mutableListOf()

    /** Sets the actor's initial screen position in pixels. */
    fun position(x: Int, y: Int) {
        position = PositionDef(x, y)
    }

    /**
     * Sets the actor's sprite from an asset reference.
     *
     * @param assetRef The sprite sheet asset reference.
     * @param block Optional sprite configuration (size, hitbox).
     */
    fun sprite(assetRef: AssetRef, block: SpriteBuilder.() -> Unit = {}) {
        val builder = SpriteBuilder(assetRef)
        builder.block()
        sprite = builder.build()
    }

    /** Sets the actor's hitbox rectangle relative to its position. */
    fun hitbox(x: Int, y: Int, width: Int, height: Int) {
        hitbox = HitboxDef(x, y, width, height)
    }

    /**
     * Registers a custom INT8 actor property.
     *
     * Creates a prefixed global variable `_${actorId}_${propName}` and returns an
     * [ActorPropertyRef] via [ActorPropDelegate.provideDelegate].
     *
     * Usage:
     * ```kotlin
     * val ball by actor {
     *     var dx by i8Prop(1)   // registers _ball_dx, returns ActorPropertyRef("ball","dx")
     * }
     * ```
     */
    fun i8Prop(initial: Int = 0): ActorPropDelegate = ActorPropDelegate(VarType.I8, initial, this)

    /**
     * Registers a custom UINT8 actor property.
     *
     * Creates a prefixed global variable `_${actorId}_${propName}` and returns an
     * [ActorPropertyRef] via [ActorPropDelegate.provideDelegate].
     */
    fun u8Prop(initial: Int = 0): ActorPropDelegate = ActorPropDelegate(VarType.U8, initial, this)

    /**
     * Configures per-actor movement for d-pad-driven position updates.
     *
     * When called, the backend generates an `update_movement_{actorId}()` function. This function
     * is automatically called each frame by the scene frame function.
     *
     * ```kotlin
     * movement {
     *     style(MovementStyle.SMOOTH)
     *     speed(2)
     * }
     * ```
     */
    fun movement(block: MovementBuilder.() -> Unit) {
        movementConfig = MovementBuilder().apply(block).build()
    }

    /**
     * Configures the animation state machine for this actor.
     *
     * When called, the backend generates an `update_animation_{actorId}()` function with enum
     * constants, a state variable, and a switch-based state machine.
     *
     * ```kotlin
     * animationStates {
     *     state("idle") { frames(0..0); speed(8) }
     *     state("walk") { frames(1..4); speed(6) }
     *     transition("idle" to "walk") { dpad.any }
     * }
     * ```
     */
    fun animationStates(block: AnimationStatesBuilder.() -> Unit) {
        animationStatesList = AnimationStatesBuilder().apply(block).build()
    }

    /**
     * Sets the simple animation frame speed (frames between updates).
     *
     * Use this when the actor has a sprite sheet animation but no state machine. When
     * [animationStates] is also configured, [frameSpeed] is ignored.
     */
    fun frameSpeed(speed: Int) {
        frameSpeedValue = speed
    }

    /**
     * Configures per-actor physics for velocity-based movement.
     *
     * When called, the backend generates signed velocity variables (`_actorId_vx`, `_actorId_vy`)
     * and `#define` constants for acceleration, gravity, max fall speed, and bounce coefficient.
     * Use [ScriptBuilder.physicsUpdate] to emit the per-frame physics integration step.
     *
     * ```kotlin
     * val ball by actor {
     *     physics {
     *         velocity(0, -2)    // initial velocity (pixels/frame)
     *         gravity(1)         // 1 px/frame² downward
     *         bounce(0.8f)       // 80% energy retained on bounce
     *         maxFallSpeed(8)    // clamp downward velocity at 8 px/frame
     *     }
     * }
     * ```
     */
    fun physics(block: PhysicsBuilder.() -> Unit) {
        physicsConfigValue = PhysicsBuilder().apply(block).build()
    }

    /**
     * Configures an NPC waypoint patrol route.
     *
     * The NPC follows the listed waypoints in sequence, moving one step per [WaypointStep] call.
     *
     * ```kotlin
     * val guard by actor {
     *     position(64, 64)
     *     waypoints(loop = true) {
     *         point(64, 64)
     *         point(128, 64)
     *         point(128, 128)
     *     }
     * }
     * ```
     */
    fun waypoints(loop: Boolean = true, block: WaypointBuilder.() -> Unit) {
        waypointRouteValue = WaypointBuilder(loop).apply(block).build()
    }

    /**
     * Sets the actor to follow (NPC chasing behaviour).
     *
     * When combined with [ScriptBuilder.pathfindStep], the NPC uses A* to reach the target actor.
     */
    fun followTarget(actor: ActorRef) {
        followTargetIdValue = actor.id
    }

    /**
     * Configures exploration collision for this actor.
     *
     * When called, the actor participates in the entity collision grid during exploration movement.
     * The backend generates `_entity_register`, `_entity_remove`, and `_entity_check` functions for
     * all actors with non-null entityCollision config.
     *
     * ```kotlin
     * val boulder by actor {
     *     position(80, 80)
     *     entityCollision {
     *         mode(EntityCollisionMode.PUSH)
     *         onPushed { soundEffect("push_sound") }
     *     }
     * }
     * ```
     */
    fun entityCollision(block: EntityCollisionBuilder.() -> Unit) {
        entityCollisionConfig = EntityCollisionBuilder().apply(block).build()
    }

    /**
     * Assigns a GBC palette override for this actor.
     *
     * When set, the scene enter handler emits a [SetPalette] op to load this palette into a SPRITE
     * slot for this actor, overriding the scene default.
     *
     * ```kotlin
     * val hero by actor {
     *     position(80, 72)
     *     palette(GbcPresets.FIRE)
     * }
     * ```
     */
    fun palette(p: GBCPalette) {
        paletteValue = p
    }

    /**
     * Opts this actor into NPC-NPC collision detection using the simple implicit group path.
     *
     * When [enabled] is true and the actor has NO explicit [collisionGroup] assignments, the actor
     * is automatically placed into the implicit `_default_npc` collision group at build time. A
     * pairwise OVERLAP rule is generated between all `_default_npc` members.
     *
     * When combined with an explicit [collisionGroup] call, the actor belongs to the explicit group
     * only — the implicit `_default_npc` group is NOT created (no double-checking).
     *
     * ```kotlin
     * val npc1 by actor { position(40, 40); collidesWithNpcs(true) }
     * val npc2 by actor { position(80, 80); collidesWithNpcs(true) }
     * ```
     */
    fun collidesWithNpcs(enabled: Boolean) {
        npcCollidesWithNpcs = enabled
    }

    /**
     * Sets the actor's mass for [CollisionResponse.PUSH] displacement calculation.
     *
     * Higher mass means smaller displacement when pushed. Used by the generated mass-proportional
     * displacement formula: `dispA = massB / (massA + massB)`.
     *
     * Default: 1.
     *
     * ```kotlin
     * val tank by actor { position(64, 64); mass(3) }
     * val bullet by actor { position(10, 10); mass(1) }
     * ```
     */
    fun mass(n: Int) {
        npcMass = n
    }

    /**
     * Assigns this actor to a type-safe [CollisionGroupRef] for NPC-NPC collision.
     *
     * Actors assigned to explicit groups are NOT placed into the implicit `_default_npc` group. An
     * actor may belong to multiple groups by calling this method multiple times.
     *
     * ```kotlin
     * val enemies by collisionGroup()
     * val tank by actor {
     *     position(64, 64)
     *     collisionGroup(enemies)
     *     mass(3)
     * }
     * ```
     */
    fun collisionGroup(group: CollisionGroupRef) {
        npcCollisionGroupIds.add(group.groupId)
    }

    /** Records a custom property name for documentation/introspection purposes. */
    internal fun registerCustomProp(name: String) {
        customProps += name
    }

    /** Builds the [ActorIR] node. */
    internal fun build(): ActorIR {
        // Build NpcCollisionConfig when any NPC collision fields are set
        val hasNpcCollision =
            npcCollidesWithNpcs || npcCollisionGroupIds.isNotEmpty() || npcMass != 1
        val npcCfg =
            if (hasNpcCollision) {
                io.github.gbkt.core.ir.NpcCollisionConfig(
                    groupIds = npcCollisionGroupIds.toList(),
                    collidesWithNpcs = npcCollidesWithNpcs,
                    mass = npcMass,
                )
            } else {
                null
            }
        return ActorIR(
            id = id,
            position = position,
            sprite = sprite,
            hitbox = hitbox,
            sourceLocation = captureV2Location(),
            movementConfig = movementConfig,
            animationStates = animationStatesList,
            frameSpeed = frameSpeedValue,
            physicsConfig = physicsConfigValue,
            waypointRoute = waypointRouteValue,
            followTargetId = followTargetIdValue,
            entityCollision = entityCollisionConfig,
            palette = paletteValue,
            npcCollisionConfig = npcCfg,
        )
    }
}

// =============================================================================
// WAYPOINT BUILDER
// =============================================================================

/**
 * Builder for an NPC waypoint patrol route.
 *
 * Usage:
 * ```kotlin
 * waypoints(loop = true) {
 *     point(64, 64)
 *     point(128, 64)
 *     point(128, 128)
 * }
 * ```
 */
@GbktDsl
class WaypointBuilder(private val loop: Boolean) {
    private val points: MutableList<Pair<Int, Int>> = mutableListOf()

    /** Adds a waypoint at the given pixel position. */
    fun point(x: Int, y: Int) {
        points.add(Pair(x, y))
    }

    internal fun build(): WaypointRoute = WaypointRoute(points.toList(), loop)
}

// =============================================================================
// ACTOR PROP DELEGATE (custom actor properties via i8Prop/u8Prop)
// =============================================================================

/**
 * Property delegate that registers a custom actor property as a prefixed global variable and
 * returns an [ActorPropertyRef] for DSL use.
 *
 * Created by [ActorBuilder.i8Prop] and [ActorBuilder.u8Prop]. On `provideDelegate`:
 * 1. Captures the Kotlin property name as the prop name.
 * 2. Registers `VariableDef("${actorId}_${propName}", type, initial)` with [GameBuilderContext].
 * 3. Returns an [ActorPropertyRef] for operator extensions.
 *
 * The generated C variable name is `_${actorId}_${propName}` (GBDK naming convention prefix).
 * [ExprVisitor] handles custom props the same as built-in props — both map `objectId.prop` →
 * `_objectId_prop`.
 *
 * Usage:
 * ```kotlin
 * val ball by actor {
 *     position(80, 72)
 *     var dx by i8Prop(1)   // registers _ball_dx INT8 global, returns ActorPropertyRef("ball","dx")
 *     var dy by i8Prop(1)   // registers _ball_dy INT8 global, returns ActorPropertyRef("ball","dy")
 * }
 * // Later in script:
 * ball.dx += speed      // uses operator extensions on ActorPropertyRef
 * ```
 */
class ActorPropDelegate(
    private val type: VarType,
    private val initialValue: Int,
    private val actorBuilder: ActorBuilder,
) : ReadOnlyProperty<Any?, ActorPropertyRef> {
    private var ref: ActorPropertyRef? = null

    /**
     * Called by Kotlin when `var x by i8Prop(0)` is evaluated inside an actor block.
     *
     * Captures the property name, registers a prefixed global variable, and stores the
     * [ActorPropertyRef] for retrieval by [getValue].
     */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, ActorPropertyRef> {
        val propName = property.name
        val varName = "${actorBuilder.id}_${propName}"
        // Register as a global variable (prefixed as _${varName} by the pipeline)
        GameBuilderContext.current?.registerVariable(VariableDef(varName, type, initialValue))
        actorBuilder.registerCustomProp(propName)
        ref = ActorPropertyRef(actorBuilder.id, propName)
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): ActorPropertyRef =
        ref ?: error("ActorPropDelegate not initialized — was provideDelegate called?")
}

// =============================================================================
// ACTOR DELEGATE (name inference via provideDelegate)
// =============================================================================

/**
 * Property delegate that infers an actor's name from the Kotlin property and registers it with the
 * current [GameBuilder].
 *
 * Implements [ReadOnlyProperty] and exposes `provideDelegate` so that Kotlin calls
 * [provideDelegate] when the `by` keyword is used. The property name is captured at that point and
 * used as the actor's ID unless [nameOverride] is provided.
 *
 * Usage:
 * ```kotlin
 * val paddle by actor { position(16, 64) }   // name inferred as "paddle"
 * val ball by actor { position(80, 72) }     // name inferred as "ball"
 * val p1 by actor("player") { ... }          // explicit name "player" (original overload)
 * ```
 *
 * @param nameOverride When non-null, overrides property-name inference (reserved for future use).
 * @param block The actor configuration block.
 */
class ActorDelegate(private val nameOverride: String?, private val block: ActorBuilder.() -> Unit) :
    ReadOnlyProperty<Any?, ActorRef> {
    private var ref: ActorRef? = null

    /**
     * Called by Kotlin when `val x by actor { ... }` is evaluated.
     *
     * Captures the property name, registers the actor with the current [GameBuilder], and stores
     * the resulting [ActorRef] for retrieval by [getValue].
     */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, ActorRef> {
        val name = nameOverride ?: property.name
        val gameBuilder =
            GameBuilderContext.current ?: error("actor {} must be called inside a game {} block")
        ref = gameBuilder.registerActor(name, block)
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): ActorRef =
        ref ?: error("ActorDelegate not initialized — was provideDelegate called?")
}
