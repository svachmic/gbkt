/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.graphics

import io.github.gbkt.core.AnimationRef
import io.github.gbkt.core.SceneRef
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.Dimensions
import io.github.gbkt.core.ir.IRAnimationPause
import io.github.gbkt.core.ir.IRAnimationPlay
import io.github.gbkt.core.ir.IRAnimationQueue
import io.github.gbkt.core.ir.IRAnimationResume
import io.github.gbkt.core.ir.IRAnimationSetFrame
import io.github.gbkt.core.ir.IRAnimationSetSpeed
import io.github.gbkt.core.ir.IRAnimationStop
import io.github.gbkt.core.ir.IRRaw
import io.github.gbkt.core.ir.IRSceneChange
import io.github.gbkt.core.ir.IRStatement

// =============================================================================
// ANIMATION SYSTEM
// Declarative sprite animations for Game Boy
// =============================================================================

/**
 * Represents a sprite animation with optional callbacks.
 *
 * @property name Animation name (e.g., "idle", "run", "jump")
 * @property frames List of tile indices to cycle through
 * @property frameDelay Number of game frames between animation frames
 * @property loop Whether the animation loops or plays once
 * @property onComplete Statements to execute when a non-looping animation finishes
 * @property frameEvents Map of frame index to statements to execute at that frame
 */
data class Animation(
    val name: String,
    val frames: List<Int>,
    val frameDelay: Int,
    val loop: Boolean = true,
    val onComplete: List<IRStatement> = emptyList(),
    val frameEvents: Map<Int, List<IRStatement>> = emptyMap()
) {
    val frameCount: Int
        get() = frames.size

    val isStatic: Boolean
        get() = frames.size == 1

    val hasOnComplete: Boolean
        get() = onComplete.isNotEmpty()

    val hasFrameEvents: Boolean
        get() = frameEvents.isNotEmpty()
}

/**
 * Builder for defining animations within a sprite.
 *
 * Usage: animations { "idle" plays frames(0, 1) every 30.frames "run" plays frames(2, 3, 4, 5)
 * every 6.frames "jump" plays frame(6) // Single frame, no animation }
 */
@GbktDsl
class AnimationsBuilder {
    internal val animations = mutableMapOf<String, Animation>()

    /**
     * Define an animation with multiple frames.
     *
     * Usage: "run" plays (frames(0..3) every 8.frames) "death" plays (frames(10..15) every
     * 8.frames).once().onComplete { scene("gameover") }
     *
     * @return AnimationRef for type-safe playback
     */
    infix fun String.plays(def: AnimationDefinition): AnimationRef {
        animations[this] =
            Animation(
                name = this,
                frames = def.frames,
                frameDelay = def.delay,
                loop = def.loop,
                onComplete = def.onComplete,
                frameEvents = def.frameEvents
            )
        return AnimationRef(this)
    }

    /**
     * Define a static frame (no animation).
     *
     * Usage: "jump" plays frame(6)
     *
     * @return AnimationRef for type-safe playback
     */
    infix fun String.plays(singleFrame: SingleFrame): AnimationRef {
        animations[this] =
            Animation(name = this, frames = listOf(singleFrame.index), frameDelay = 1, loop = false)
        return AnimationRef(this)
    }

    /**
     * Builder-style animation definition for complex animations.
     *
     * Usage: animation("death") { frames(10..15) delay(8) loop(false) onComplete {
     * scene("gameover") } onFrame(3) { sound.play(sfxDeath) } }
     *
     * @return AnimationRef for type-safe playback
     */
    fun animation(name: String, block: AnimationBuilder.() -> Unit): AnimationRef {
        val builder = AnimationBuilder(name)
        builder.block()
        animations[name] = builder.build()
        return AnimationRef(name)
    }
}

/** Builder for complex animation definitions with callbacks. */
@GbktDsl
class AnimationBuilder(private val name: String) {
    private var framesList: List<Int> = emptyList()
    private var frameDelay: Int = 8
    private var loop: Boolean = true
    private var onCompleteStatements: List<IRStatement> = emptyList()
    private val frameEventsMap = mutableMapOf<Int, List<IRStatement>>()

    fun frames(vararg indices: Int) {
        framesList = indices.toList()
    }

    fun frames(range: IntRange) {
        framesList = range.toList()
    }

    fun delay(frames: Int) {
        frameDelay = frames
    }

    fun loop(enabled: Boolean) {
        loop = enabled
    }

    fun onComplete(block: AnimationCallbackScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { AnimationCallbackScope().block() }
        onCompleteStatements = recorder.statements
    }

    fun onFrame(frameIndex: Int, block: AnimationCallbackScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { AnimationCallbackScope().block() }
        frameEventsMap[frameIndex] = recorder.statements
    }

    internal fun build() =
        Animation(
            name = name,
            frames = framesList,
            frameDelay = frameDelay,
            loop = loop,
            onComplete = onCompleteStatements,
            frameEvents = frameEventsMap.toMap()
        )
}

/** Intermediate holder for animation frames before delay is specified. */
class FrameSequence(val frames: List<Int>) {
    /**
     * Specify animation speed.
     *
     * Usage: frames(0, 1, 2) every 8.frames
     */
    infix fun every(timing: FrameTiming): AnimationDefinition {
        return AnimationDefinition(frames, timing.count, timing.loop)
    }

    /** Create a looping animation with default timing. */
    fun looping(delay: Int = 8) = AnimationDefinition(frames, delay, true)

    /** Create a one-shot animation. */
    fun once(delay: Int = 8) = AnimationDefinition(frames, delay, false)
}

/** Timing specification for animations. */
data class FrameTiming(val count: Int, val loop: Boolean = true)

/** Single frame definition. */
data class SingleFrame(val index: Int)

/**
 * Complete animation definition with optional callbacks. Supports fluent API for adding callbacks
 * after timing is specified.
 */
class AnimationDefinition(
    val frames: List<Int>,
    val delay: Int,
    val loop: Boolean = true,
    val onComplete: List<IRStatement> = emptyList(),
    val frameEvents: Map<Int, List<IRStatement>> = emptyMap()
) {
    /**
     * Make this animation non-looping (one-shot).
     *
     * Usage: "death" plays (frames(10..15) every 8.frames).once()
     */
    fun once(): AnimationDefinition =
        AnimationDefinition(frames, delay, loop = false, onComplete, frameEvents)

    /**
     * Add completion callback for non-looping animations.
     *
     * Usage: "death" plays (frames(10..15) every 8.frames).once().onComplete { scene("gameover") }
     */
    fun onComplete(block: AnimationCallbackScope.() -> Unit): AnimationDefinition {
        val statements = recordAnimationCallback(block)
        return AnimationDefinition(frames, delay, loop, statements, frameEvents)
    }

    /**
     * Add frame event callback.
     *
     * Usage: "attack" plays (frames(0..5) every 4.frames).onFrame(2) { spawnHitbox() }
     */
    fun onFrame(frameIndex: Int, block: AnimationCallbackScope.() -> Unit): AnimationDefinition {
        val statements = recordAnimationCallback(block)
        val newEvents = frameEvents + (frameIndex to statements)
        return AnimationDefinition(frames, delay, loop, onComplete, newEvents)
    }

    /**
     * Add multiple frame events.
     *
     * Usage: "attack" plays (frames(0..5) every 4.frames).withEvents { onFrame(2) { spawnHitbox() }
     * onFrame(4) { sound.play(sfxHit) } }
     */
    fun withEvents(block: AnimationEventsBuilder.() -> Unit): AnimationDefinition {
        val builder = AnimationEventsBuilder()
        builder.block()
        val newEvents = frameEvents + builder.events
        return AnimationDefinition(frames, delay, loop, onComplete, newEvents)
    }

    private fun recordAnimationCallback(
        block: AnimationCallbackScope.() -> Unit
    ): List<IRStatement> {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { AnimationCallbackScope().block() }
        return recorder.statements
    }
}

/** Scope for animation callbacks. Provides access to game DSL. */
@GbktDsl
class AnimationCallbackScope {
    /**
     * Go to another scene.
     *
     * Usage in onComplete:
     * ```kotlin
     * "death" plays (frames(10..15) every 8.frames).once().onComplete {
     *     scene(gameoverScene)
     * }
     * ```
     */
    fun scene(ref: SceneRef) {
        RecordingContext.require().emit(IRSceneChange(ref.name))
    }

    /** Raw C code escape hatch */
    fun raw(code: String) {
        RecordingContext.require().emit(IRRaw(code))
    }
}

/** Builder for multiple frame events. */
class AnimationEventsBuilder {
    internal val events = mutableMapOf<Int, List<IRStatement>>()

    fun onFrame(frameIndex: Int, block: AnimationCallbackScope.() -> Unit) {
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { AnimationCallbackScope().block() }
        events[frameIndex] = recorder.statements
    }
}

// =============================================================================
// DSL FUNCTIONS
// =============================================================================

/**
 * Create a sequence of animation frames.
 *
 * Usage: frames(0, 1, 2, 3)
 */
fun frames(vararg indices: Int) = FrameSequence(indices.toList())

/**
 * Create a range of animation frames.
 *
 * Usage: frames(0..3) // Same as frames(0, 1, 2, 3)
 */
fun frames(range: IntRange) = FrameSequence(range.toList())

/**
 * Create a single static frame (no animation).
 *
 * Usage: "idle" plays frame(0)
 */
fun frame(index: Int) = SingleFrame(index)

/**
 * Extension to specify frame timing.
 *
 * Usage: every 8.frames
 */
val Int.frames: FrameTiming
    get() = FrameTiming(this, true)

/**
 * Non-looping frame timing.
 *
 * Usage: every 8.framesOnce
 */
val Int.framesOnce: FrameTiming
    get() = FrameTiming(this, false)

// =============================================================================
// SPRITE ANIMATION RUNTIME
// =============================================================================

/**
 * Animation state for a sprite at runtime. This is used during code generation to track animation
 * state.
 */
data class SpriteAnimationState(
    val spriteName: String,
    val animations: Map<String, Animation>,
    val defaultAnimation: String?
) {
    fun hasAnimations() = animations.isNotEmpty()

    fun getAnimation(name: String) =
        animations[name]
            ?: error(
                "Animation '$name' not found for sprite '$spriteName'. Available: ${animations.keys}"
            )
}

/**
 * Extension function to play an animation on a sprite (type-safe version).
 *
 * Usage in frame blocks: player.play(runAnim) player.play(hitAnim, loop = false)
 * player.play(runAnim, speed = 200) // 2x speed
 *
 * @param ref AnimationRef returned from animation definition
 * @param loop Whether to loop (default: true)
 * @param speed Speed multiplier (100 = 1.0x, 50 = 0.5x, 200 = 2.0x)
 * @param reverse Play animation backwards
 */
fun AnimatedSprite.play(
    ref: AnimationRef,
    loop: Boolean = true,
    speed: Int = 100,
    reverse: Boolean = false
) {
    RecordingContext.require().emit(IRAnimationPlay(name, ref.name, loop, speed, reverse))
}

/** Stop the current animation. */
fun AnimatedSprite.stopAnimation() {
    RecordingContext.require().emit(IRAnimationStop(name))
}

/** Pause the current animation. */
fun AnimatedSprite.pauseAnimation() {
    RecordingContext.require().emit(IRAnimationPause(name))
}

/** Resume a paused animation. */
fun AnimatedSprite.resumeAnimation() {
    RecordingContext.require().emit(IRAnimationResume(name))
}

/**
 * Set animation speed dynamically.
 *
 * @param speed Speed multiplier (100 = 1.0x, 50 = 0.5x, 200 = 2.0x)
 */
fun AnimatedSprite.setAnimationSpeed(speed: Int) {
    RecordingContext.require().emit(IRAnimationSetSpeed(name, speed))
}

/** Set a specific frame directly. */
fun AnimatedSprite.setFrame(index: Int) {
    RecordingContext.require().emit(IRAnimationSetFrame(name, index))
}

/**
 * Queue an animation to play after the current one finishes.
 *
 * Usage: player.queue(attackAnim)
 */
fun AnimatedSprite.queue(ref: AnimationRef) {
    RecordingContext.require().emit(IRAnimationQueue(name, ref.name, clearQueue = false))
}

/**
 * Play a one-shot animation and return a chainable result for queuing more.
 *
 * Usage: player.playOnce(attackAnim).then(idleAnim)
 */
fun AnimatedSprite.playOnce(ref: AnimationRef): AnimationChain {
    RecordingContext.require().emit(IRAnimationPlay(name, ref.name, loop = false))
    return AnimationChain(name)
}

/** Chainable animation result for fluent queuing. */
class AnimationChain(private val spriteName: String) {
    /** Queue another animation after the current one. */
    fun then(ref: AnimationRef): AnimationChain {
        RecordingContext.require().emit(IRAnimationQueue(spriteName, ref.name))
        return this
    }
}

/** Marker interface for sprites that have animations. */
interface AnimatedSprite {
    val name: String
    val animationState: SpriteAnimationState?
}

// =============================================================================
// SPRITE SHEET REGIONS
// Named regions for cleaner animation definitions
// =============================================================================

/**
 * A named region within a sprite sheet.
 *
 * Regions define contiguous tile ranges with semantic names. This allows animations to reference
 * regions instead of raw tile indices.
 *
 * @property name Region name (e.g., "idle", "run", "attack")
 * @property startTile First tile index in this region
 * @property tileCount Number of tiles in this region
 */
data class SpriteRegion(val name: String, val startTile: Int, val tileCount: Int) {
    /** Get the tile indices for this region */
    val tiles: List<Int>
        get() = (startTile until startTile + tileCount).toList()

    /** Get a single tile from this region (0-indexed within region) */
    fun tile(index: Int): Int {
        require(index in 0 until tileCount) {
            "Tile index $index out of bounds for region '$name' (0..${tileCount - 1})"
        }
        return startTile + index
    }
}

/**
 * Builder for defining regions within a sprite.
 *
 * Usage: regions { "idle" at 0 size 2 "run" at 2 size 4 "attack" at 6 size 6 "death" at 12 size 4 }
 */
@GbktDsl
class RegionsBuilder {
    internal val regions = mutableMapOf<String, SpriteRegion>()

    /**
     * Start defining a region at a tile index.
     *
     * Usage: "idle" at 0 size 2
     */
    infix fun String.at(startTile: Int): RegionStart =
        RegionStart(this, startTile, this@RegionsBuilder)
}

/** Intermediate builder for region definition. */
class RegionStart(
    private val name: String,
    private val startTile: Int,
    private val builder: RegionsBuilder
) {
    /**
     * Specify the number of tiles in this region.
     *
     * Usage: "idle" at 0 size 2
     */
    infix fun size(count: Int): SpriteRegion {
        val region = SpriteRegion(name, startTile, count)
        builder.regions[name] = region
        return region
    }

    /**
     * Specify region as a grid (rows x cols). Useful for large animations arranged in a grid.
     *
     * Usage: "attack" at 10 grid (2 x 3) // 2 rows x 3 cols = 6 tiles
     */
    infix fun grid(dims: Dimensions): SpriteRegion {
        val count = dims.width * dims.height
        val region = SpriteRegion(name, startTile, count)
        builder.regions[name] = region
        return region
    }
}

/**
 * Reference holder for looking up regions by name. Used by animations to reference regions defined
 * in the sprite.
 */
class RegionLookup(private val regions: Map<String, SpriteRegion>) {
    /**
     * Get a region's frames as a FrameSequence for use in animations.
     *
     * Usage: animations { "idle" plays (region("idle") every 30.frames) }
     */
    fun region(name: String): FrameSequence {
        val region = regions[name] ?: error("Region '$name' not found. Available: ${regions.keys}")
        return FrameSequence(region.tiles)
    }

    /**
     * Get a single frame from a region.
     *
     * Usage: "jump" plays regionFrame("jump", 0)
     */
    fun regionFrame(regionName: String, frameIndex: Int): SingleFrame {
        val region =
            regions[regionName]
                ?: error("Region '$regionName' not found. Available: ${regions.keys}")
        return SingleFrame(region.tile(frameIndex))
    }
}

/** Extended AnimationsBuilder that has access to regions. */
@GbktDsl
class AnimationsBuilderWithRegions(private val regions: Map<String, SpriteRegion>) {
    internal val animations = mutableMapOf<String, Animation>()

    /**
     * Get a region's frames as a FrameSequence for use in animations.
     *
     * Usage: "idle" plays (region("idle") every 30.frames)
     */
    fun region(name: String): FrameSequence {
        val region = regions[name] ?: error("Region '$name' not found. Available: ${regions.keys}")
        return FrameSequence(region.tiles)
    }

    /**
     * Get a single frame from a region.
     *
     * Usage: "jump" plays regionFrame("jump", 0)
     */
    fun regionFrame(regionName: String, frameIndex: Int): SingleFrame {
        val region =
            regions[regionName]
                ?: error("Region '$regionName' not found. Available: ${regions.keys}")
        return SingleFrame(region.tile(frameIndex))
    }

    /**
     * Define an animation with multiple frames.
     *
     * @return AnimationRef for type-safe playback
     */
    infix fun String.plays(def: AnimationDefinition): AnimationRef {
        animations[this] =
            Animation(
                name = this,
                frames = def.frames,
                frameDelay = def.delay,
                loop = def.loop,
                onComplete = def.onComplete,
                frameEvents = def.frameEvents
            )
        return AnimationRef(this)
    }

    /**
     * Define a static frame (no animation).
     *
     * @return AnimationRef for type-safe playback
     */
    infix fun String.plays(singleFrame: SingleFrame): AnimationRef {
        animations[this] =
            Animation(name = this, frames = listOf(singleFrame.index), frameDelay = 1, loop = false)
        return AnimationRef(this)
    }

    /**
     * Builder-style animation definition for complex animations.
     *
     * @return AnimationRef for type-safe playback
     */
    fun animation(name: String, block: AnimationBuilder.() -> Unit): AnimationRef {
        val builder = AnimationBuilder(name)
        builder.block()
        animations[name] = builder.build()
        return AnimationRef(name)
    }
}
