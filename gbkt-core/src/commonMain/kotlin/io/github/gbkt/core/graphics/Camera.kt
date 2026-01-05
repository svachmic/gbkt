/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.graphics

import io.github.gbkt.core.FrameScope
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.entity.Entity
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.GBCColor
import io.github.gbkt.core.ir.IRBinary
import io.github.gbkt.core.ir.IRCameraFollow
import io.github.gbkt.core.ir.IRCameraSetPosition
import io.github.gbkt.core.ir.IRCameraShake
import io.github.gbkt.core.ir.IRCameraShakeStop
import io.github.gbkt.core.ir.IRCameraSnapTo
import io.github.gbkt.core.ir.IRCameraStopFollow
import io.github.gbkt.core.ir.IRCameraUpdate
import io.github.gbkt.core.ir.IRCameraX
import io.github.gbkt.core.ir.IRCameraY
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRTransitionActive
import io.github.gbkt.core.ir.IRTransitionFadeIn
import io.github.gbkt.core.ir.IRTransitionFadeOut
import io.github.gbkt.core.ir.IRTransitionFlash
import io.github.gbkt.core.ir.IRTransitionIris
import io.github.gbkt.core.ir.IRTransitionWipe
import io.github.gbkt.core.ir.IRVar
import io.github.gbkt.core.ir.IrisType
import io.github.gbkt.core.ir.ShakeDecay
import io.github.gbkt.core.ir.WipeDirection

// =============================================================================
// CAMERA SYSTEM DSL
// Scrolling, following, shake, and screen transitions for Game Boy
// =============================================================================

/**
 * Camera instance for controlling scrolling and transitions.
 *
 * ## Basic Usage
 *
 * ```kotlin
 * val camera = camera {
 *     smoothing = 0.15f
 *     offset(0, -16)
 *     bounds(0..256, 0..256)
 * }
 *
 * scene("gameplay") {
 *     enter {
 *         camera.follow(player)
 *         camera.fadeIn(20.frames)
 *     }
 *
 *     every.frame {
 *         camera.update()
 *
 *         whenever(player collidesWith enemy) {
 *             camera.shake(4, 10.frames)
 *         }
 *     }
 * }
 * ```
 */
class Camera internal constructor(val name: String, val config: CameraConfig) {
    // =========================================================================
    // POSITION ACCESS
    // =========================================================================

    /**
     * Camera X position as expression (for comparisons). Usage: `whenever(camera.x isAbove 100) {
     * ... }`
     */
    val x: Expr
        get() = Expr(IRCameraX)

    /** Camera Y position as expression. */
    val y: Expr
        get() = Expr(IRCameraY)

    // =========================================================================
    // CAMERA UPDATE
    // =========================================================================

    /**
     * Update camera state. Must be called in every.frame block. Processes: smooth following, screen
     * shake, transitions.
     *
     * ```kotlin
     * every.frame {
     *     camera.update()
     *     // ... game logic
     * }
     * ```
     */
    fun update() {
        RecordingContext.require().emit(IRCameraUpdate)
    }

    // =========================================================================
    // FOLLOW - Sprites
    // =========================================================================

    /**
     * Start following a sprite. Camera will smoothly track the sprite's position.
     *
     * ```kotlin
     * camera.follow(player)
     * ```
     */
    fun follow(sprite: Sprite) {
        val (xVar, yVar) = getSpritePositionVars(sprite)
        emitFollow(xVar, yVar, config.offsetX, config.offsetY, config.smoothing)
    }

    /**
     * Follow a sprite with custom configuration.
     *
     * ```kotlin
     * camera.follow(player) {
     *     smoothing = 0.2f
     *     offset(0, -16)
     * }
     * ```
     */
    fun follow(sprite: Sprite, init: FollowConfigBuilder.() -> Unit) {
        val builder = FollowConfigBuilder(config.smoothing, config.offsetX, config.offsetY)
        builder.init()
        val (xVar, yVar) = getSpritePositionVars(sprite)
        emitFollow(xVar, yVar, builder.offsetX, builder.offsetY, builder.smoothing)
    }

    /** Follow only the X axis of a sprite. */
    fun followX(sprite: Sprite) {
        val (xVar, _) = getSpritePositionVars(sprite)
        // Follow X only by setting Y target to current Y
        RecordingContext.require()
            .emit(
                IRCameraFollow(
                    targetXVar = xVar,
                    targetYVar = "_camera_y", // Self-reference = no Y movement
                    offsetX = config.offsetX,
                    offsetY = 0,
                    smoothing = floatToFixed(config.smoothing),
                )
            )
    }

    /** Follow only the Y axis of a sprite. */
    fun followY(sprite: Sprite) {
        val (_, yVar) = getSpritePositionVars(sprite)
        RecordingContext.require()
            .emit(
                IRCameraFollow(
                    targetXVar = "_camera_x", // Self-reference = no X movement
                    targetYVar = yVar,
                    offsetX = 0,
                    offsetY = config.offsetY,
                    smoothing = floatToFixed(config.smoothing),
                )
            )
    }

    // =========================================================================
    // FOLLOW - Entities
    // =========================================================================

    /**
     * Start following an entity. Camera will smoothly track the entity's position.
     *
     * ```kotlin
     * camera.follow(player)  // Works with both Sprite and Entity
     * ```
     */
    fun follow(entity: Entity) {
        val posComp =
            entity.positionComponent
                ?: error("Entity '${entity.name}' has no position component to follow")
        emitFollow(
            posComp.xVarName,
            posComp.yVarName,
            config.offsetX,
            config.offsetY,
            config.smoothing,
        )
    }

    /** Follow an entity with custom configuration. */
    fun follow(entity: Entity, init: FollowConfigBuilder.() -> Unit) {
        val builder = FollowConfigBuilder(config.smoothing, config.offsetX, config.offsetY)
        builder.init()
        val posComp =
            entity.positionComponent
                ?: error("Entity '${entity.name}' has no position component to follow")
        emitFollow(
            posComp.xVarName,
            posComp.yVarName,
            builder.offsetX,
            builder.offsetY,
            builder.smoothing,
        )
    }

    /** Follow only the X axis of an entity. */
    fun followX(entity: Entity) {
        val posComp =
            entity.positionComponent ?: error("Entity '${entity.name}' has no position component")
        RecordingContext.require()
            .emit(
                IRCameraFollow(
                    targetXVar = posComp.xVarName,
                    targetYVar = "_camera_y",
                    offsetX = config.offsetX,
                    offsetY = 0,
                    smoothing = floatToFixed(config.smoothing),
                )
            )
    }

    /** Follow only the Y axis of an entity. */
    fun followY(entity: Entity) {
        val posComp =
            entity.positionComponent ?: error("Entity '${entity.name}' has no position component")
        RecordingContext.require()
            .emit(
                IRCameraFollow(
                    targetXVar = "_camera_x",
                    targetYVar = posComp.yVarName,
                    offsetX = 0,
                    offsetY = config.offsetY,
                    smoothing = floatToFixed(config.smoothing),
                )
            )
    }

    /** Stop following any target. */
    fun stopFollow() {
        RecordingContext.require().emit(IRCameraStopFollow)
    }

    // =========================================================================
    // DIRECT POSITIONING
    // =========================================================================

    /**
     * Set camera position directly.
     *
     * ```kotlin
     * camera.setPosition(100, 50)
     * ```
     */
    fun setPosition(x: Int, y: Int) {
        RecordingContext.require().emit(IRCameraSetPosition(IRLiteral(x), IRLiteral(y)))
    }

    /** Set camera position from expressions. */
    fun setPosition(x: Expr, y: Expr) {
        RecordingContext.require().emit(IRCameraSetPosition(x.ir, y.ir))
    }

    /**
     * Snap camera instantly to a sprite's position (no smoothing).
     *
     * ```kotlin
     * camera.snapTo(player)
     * ```
     */
    fun snapTo(sprite: Sprite) {
        val (xVar, yVar) = getSpritePositionVars(sprite)
        RecordingContext.require()
            .emit(
                IRCameraSnapTo(
                    IRBinary(IRVar(xVar), BinaryOp.SUB, IRLiteral(80 - config.offsetX)),
                    IRBinary(IRVar(yVar), BinaryOp.SUB, IRLiteral(72 - config.offsetY)),
                )
            )
    }

    /** Snap camera instantly to an entity's position. */
    fun snapTo(entity: Entity) {
        val posComp =
            entity.positionComponent ?: error("Entity '${entity.name}' has no position component")
        RecordingContext.require()
            .emit(
                IRCameraSnapTo(
                    IRBinary(IRVar(posComp.xVarName), BinaryOp.SUB, IRLiteral(80 - config.offsetX)),
                    IRBinary(IRVar(posComp.yVarName), BinaryOp.SUB, IRLiteral(72 - config.offsetY)),
                )
            )
    }

    /** Snap camera to specific coordinates. */
    fun snapTo(x: Int, y: Int) {
        RecordingContext.require().emit(IRCameraSnapTo(IRLiteral(x), IRLiteral(y)))
    }

    // =========================================================================
    // SCREEN SHAKE
    // =========================================================================

    /**
     * Start screen shake effect.
     *
     * @param intensity Shake magnitude in pixels (1-8 recommended)
     * @param duration Duration as FrameTiming (e.g., 10.frames)
     *
     * ```kotlin
     * camera.shake(4, 10.frames)
     * ```
     */
    fun shake(intensity: Int, duration: FrameTiming) {
        RecordingContext.require().emit(IRCameraShake(intensity, duration.count, ShakeDecay.LINEAR))
    }

    /**
     * Screen shake with configuration.
     *
     * ```kotlin
     * camera.shake {
     *     intensity = 6
     *     duration = 20.frames
     *     decay = Decay.EXPONENTIAL
     * }
     * ```
     */
    fun shake(init: ShakeConfigBuilder.() -> Unit) {
        val builder = ShakeConfigBuilder()
        builder.init()
        RecordingContext.require()
            .emit(IRCameraShake(builder.intensity, builder.duration, builder.decay))
    }

    /**
     * Quick impact shake - a short, punchy shake for hits.
     *
     * ```kotlin
     * camera.impact(4)  // Quick 4px shake
     * ```
     */
    fun impact(intensity: Int) {
        RecordingContext.require().emit(IRCameraShake(intensity, 8, ShakeDecay.EXPONENTIAL))
    }

    /** Stop screen shake immediately. */
    fun stopShake() {
        RecordingContext.require().emit(IRCameraShakeStop)
    }

    // =========================================================================
    // FADE TRANSITIONS
    // =========================================================================

    /**
     * Fade screen to black.
     *
     * ```kotlin
     * camera.fadeOut(30.frames) {
     *     scene("gameover")
     * }
     * ```
     */
    fun fadeOut(duration: FrameTiming, onComplete: (FrameScope.() -> Unit)? = null) {
        val statements = onComplete?.let { recordCallback(it) } ?: emptyList()
        RecordingContext.require().emit(IRTransitionFadeOut(duration.count, statements))
    }

    /**
     * Fade screen in from black.
     *
     * ```kotlin
     * camera.fadeIn(20.frames)
     * ```
     */
    fun fadeIn(duration: FrameTiming, onComplete: (FrameScope.() -> Unit)? = null) {
        val statements = onComplete?.let { recordCallback(it) } ?: emptyList()
        RecordingContext.require().emit(IRTransitionFadeIn(duration.count, statements))
    }

    /**
     * Flash the screen white (damage effect, etc.).
     *
     * ```kotlin
     * camera.flash(8.frames)
     * ```
     */
    fun flash(duration: FrameTiming) {
        RecordingContext.require().emit(IRTransitionFlash(GBCColor.WHITE, duration.count))
    }

    /**
     * Flash the screen a specific color.
     *
     * ```kotlin
     * camera.flash(GBCColor.RED, 8.frames)
     * ```
     */
    fun flash(color: GBCColor, duration: FrameTiming) {
        RecordingContext.require().emit(IRTransitionFlash(color, duration.count))
    }

    // =========================================================================
    // WIPE TRANSITIONS
    // =========================================================================

    /**
     * Wipe transition from right to left.
     *
     * ```kotlin
     * camera.wipeLeft(45.frames) { scene("level2") }
     * ```
     */
    fun wipeLeft(duration: FrameTiming, onComplete: (FrameScope.() -> Unit)? = null) {
        val statements = onComplete?.let { recordCallback(it) } ?: emptyList()
        RecordingContext.require()
            .emit(IRTransitionWipe(WipeDirection.LEFT, duration.count, statements))
    }

    /** Wipe transition from left to right. */
    fun wipeRight(duration: FrameTiming, onComplete: (FrameScope.() -> Unit)? = null) {
        val statements = onComplete?.let { recordCallback(it) } ?: emptyList()
        RecordingContext.require()
            .emit(IRTransitionWipe(WipeDirection.RIGHT, duration.count, statements))
    }

    /** Wipe transition from bottom to top. */
    fun wipeUp(duration: FrameTiming, onComplete: (FrameScope.() -> Unit)? = null) {
        val statements = onComplete?.let { recordCallback(it) } ?: emptyList()
        RecordingContext.require()
            .emit(IRTransitionWipe(WipeDirection.UP, duration.count, statements))
    }

    /** Wipe transition from top to bottom. */
    fun wipeDown(duration: FrameTiming, onComplete: (FrameScope.() -> Unit)? = null) {
        val statements = onComplete?.let { recordCallback(it) } ?: emptyList()
        RecordingContext.require()
            .emit(IRTransitionWipe(WipeDirection.DOWN, duration.count, statements))
    }

    // =========================================================================
    // IRIS TRANSITIONS
    // =========================================================================

    /**
     * Iris close - circle shrinking to a point centered on sprite.
     *
     * ```kotlin
     * camera.irisClose(60.frames, player) { scene("next") }
     * ```
     */
    fun irisClose(
        duration: FrameTiming,
        target: Sprite,
        onComplete: (FrameScope.() -> Unit)? = null,
    ) {
        val (xVar, yVar) = getSpritePositionVars(target)
        val statements = onComplete?.let { recordCallback(it) } ?: emptyList()
        RecordingContext.require()
            .emit(
                IRTransitionIris(
                    IrisType.CLOSE,
                    IRVar(xVar),
                    IRVar(yVar),
                    duration.count,
                    statements,
                )
            )
    }

    /** Iris close centered on entity. */
    fun irisClose(
        duration: FrameTiming,
        target: Entity,
        onComplete: (FrameScope.() -> Unit)? = null,
    ) {
        val posComp =
            target.positionComponent ?: error("Entity '${target.name}' has no position component")
        val statements = onComplete?.let { recordCallback(it) } ?: emptyList()
        RecordingContext.require()
            .emit(
                IRTransitionIris(
                    IrisType.CLOSE,
                    IRVar(posComp.xVarName),
                    IRVar(posComp.yVarName),
                    duration.count,
                    statements,
                )
            )
    }

    /**
     * Iris close centered on coordinates.
     *
     * ```kotlin
     * camera.irisClose(60.frames, 80, 72) { scene("next") }
     * ```
     */
    fun irisClose(
        duration: FrameTiming,
        centerX: Int,
        centerY: Int,
        onComplete: (FrameScope.() -> Unit)? = null,
    ) {
        val statements = onComplete?.let { recordCallback(it) } ?: emptyList()
        RecordingContext.require()
            .emit(
                IRTransitionIris(
                    IrisType.CLOSE,
                    IRLiteral(centerX),
                    IRLiteral(centerY),
                    duration.count,
                    statements,
                )
            )
    }

    /**
     * Iris open - circle expanding from a point.
     *
     * ```kotlin
     * camera.irisOpen(60.frames, player)
     * ```
     */
    fun irisOpen(
        duration: FrameTiming,
        target: Sprite,
        onComplete: (FrameScope.() -> Unit)? = null,
    ) {
        val (xVar, yVar) = getSpritePositionVars(target)
        val statements = onComplete?.let { recordCallback(it) } ?: emptyList()
        RecordingContext.require()
            .emit(
                IRTransitionIris(
                    IrisType.OPEN,
                    IRVar(xVar),
                    IRVar(yVar),
                    duration.count,
                    statements,
                )
            )
    }

    /** Iris open centered on entity. */
    fun irisOpen(
        duration: FrameTiming,
        target: Entity,
        onComplete: (FrameScope.() -> Unit)? = null,
    ) {
        val posComp =
            target.positionComponent ?: error("Entity '${target.name}' has no position component")
        val statements = onComplete?.let { recordCallback(it) } ?: emptyList()
        RecordingContext.require()
            .emit(
                IRTransitionIris(
                    IrisType.OPEN,
                    IRVar(posComp.xVarName),
                    IRVar(posComp.yVarName),
                    duration.count,
                    statements,
                )
            )
    }

    /** Iris open centered on coordinates. */
    fun irisOpen(
        duration: FrameTiming,
        centerX: Int,
        centerY: Int,
        onComplete: (FrameScope.() -> Unit)? = null,
    ) {
        val statements = onComplete?.let { recordCallback(it) } ?: emptyList()
        RecordingContext.require()
            .emit(
                IRTransitionIris(
                    IrisType.OPEN,
                    IRLiteral(centerX),
                    IRLiteral(centerY),
                    duration.count,
                    statements,
                )
            )
    }

    // =========================================================================
    // TRANSITION STATE
    // =========================================================================

    /**
     * Check if any transition is currently active.
     *
     * ```kotlin
     * whenever(camera.isTransitioning) { /* skip input */ }
     * ```
     */
    val isTransitioning: Condition
        get() = Condition(IRTransitionActive)

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    private fun getSpritePositionVars(sprite: Sprite): Pair<String, String> {
        val pos = sprite.position
        val bind = sprite.binding
        return when {
            pos != null -> pos.xVarName to pos.yVarName
            bind != null -> bind.xVar to bind.yVar
            else -> error("Sprite '${sprite.name}' has no position. Use position() or boundTo().")
        }
    }

    private fun emitFollow(
        xVar: String,
        yVar: String,
        offsetX: Int,
        offsetY: Int,
        smoothing: Float,
    ) {
        RecordingContext.require()
            .emit(
                IRCameraFollow(
                    targetXVar = xVar,
                    targetYVar = yVar,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    smoothing = floatToFixed(smoothing),
                )
            )
    }

    private fun floatToFixed(f: Float): Int = (f * 256).toInt().coerceIn(0, 255)

    private fun recordCallback(block: FrameScope.() -> Unit): List<IRStatement> {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { FrameScope("camera_callback").block() }
        return recorder.statements
    }
}
