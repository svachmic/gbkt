/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.graphics

import io.github.gbkt.core.AnimationRef
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.AssignableExpr
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.Dimensions
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.GBVar
import io.github.gbkt.core.ir.IRAnimationPlay
import io.github.gbkt.core.ir.IRAnimationSetFrame
import io.github.gbkt.core.ir.IRAnimationStop
import io.github.gbkt.core.ir.IRCall
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRSpriteSetPalette
import io.github.gbkt.core.ir.IRVar
import io.github.gbkt.core.ir.x

// =============================================================================
// SPRITE
// =============================================================================

/** Sprite binding - tracks which variables a sprite is bound to for auto-update. */
data class SpriteBinding(val xVar: String, val yVar: String)

/**
 * Sprite-owned position - the sprite manages its own position variables. When a sprite has a
 * SpritePosition, it owns the x/y variables and they are automatically generated in the C code.
 */
data class SpritePosition(
    val xVarName: String,
    val yVarName: String,
    val initialX: Int,
    val initialY: Int,
)

/** Hitbox definition for collision detection. Offsets are relative to sprite position. */
data class Hitbox(val xOffset: Int, val yOffset: Int, val width: Int, val height: Int)

class Sprite(
    override val name: String,
    val asset: String,
    val width: Int,
    val height: Int,
    val oamSlot: Int,
    val position: SpritePosition? = null, // Sprite-owned position (NEW)
    val binding: SpriteBinding? = null, // External variable binding
    val paletteRef: String? = null, // Reference to palette name (for GBC)
    val paletteIndex: Int = 0, // Direct palette index 0-7 (for GBC)
    val animations: Map<String, Animation> = emptyMap(), // Sprite animations
    val hitbox: Hitbox? = null, // Collision hitbox
    val optimizationHints: SpriteOptimizationHints? = null, // Asset optimization hints
) : AnimatedSprite {

    /**
     * Get X position as an assignable expression. Supports both expressions (player.x + 10) and
     * assignments (player.x += 2).
     *
     * Throws error if sprite has no position. Use position() or boundTo().
     */
    val x: AssignableExpr
        get() {
            val xName =
                position?.xVarName
                    ?: binding?.xVar
                    ?: error("Sprite '$name' has no position. Use position() or boundTo().")
            return AssignableExpr(xName, GBVar.VarType.U8)
        }

    /**
     * Get Y position as an assignable expression. Supports both expressions (player.y + 10) and
     * assignments (player.y += 2).
     *
     * Throws error if sprite has no position. Use position() or boundTo().
     */
    val y: AssignableExpr
        get() {
            val yName =
                position?.yVarName
                    ?: binding?.yVar
                    ?: error("Sprite '$name' has no position. Use position() or boundTo().")
            return AssignableExpr(yName, GBVar.VarType.U8)
        }

    /** Move sprite to position (generates IR) */
    fun moveTo(x: Expr, y: Expr) {
        RecordingContext.require()
            .emit(IRCall("move_sprite", listOf(IRLiteral(oamSlot), x.ir, y.ir)))
    }

    /** Show sprite */
    fun show() {
        RecordingContext.require().emit(IRCall("SHOW_SPRITE", listOf(IRLiteral(oamSlot))))
    }

    /** Hide sprite */
    fun hide() {
        RecordingContext.require().emit(IRCall("HIDE_SPRITE", listOf(IRLiteral(oamSlot))))
    }

    /** Set sprite tile */
    fun tile(index: Int) {
        RecordingContext.require()
            .emit(IRCall("set_sprite_tile", listOf(IRLiteral(oamSlot), IRLiteral(index))))
    }

    /** Flip sprite horizontally */
    fun flipX(flip: Boolean = true) {
        val prop = if (flip) "S_FLIPX" else "0"
        RecordingContext.require()
            .emit(IRCall("set_sprite_prop", listOf(IRLiteral(oamSlot), IRVar(prop))))
    }

    /** Set sprite palette at runtime (GBC only) */
    fun setPalette(index: Int) {
        RecordingContext.require().emit(IRSpriteSetPalette(oamSlot, index))
    }

    /** Check if this sprite has position (either owned or bound) */
    val isBound: Boolean
        get() = position != null || binding != null

    /** Check if this sprite has a palette assigned */
    val hasPalette: Boolean
        get() = paletteRef != null || paletteIndex > 0

    /** Check if this sprite has animations defined */
    val hasAnimations: Boolean
        get() = animations.isNotEmpty()

    /** Check if this sprite has a hitbox defined */
    val hasHitbox: Boolean
        get() = hitbox != null

    /** Get effective hitbox (uses sprite bounds if no explicit hitbox) */
    val effectiveHitbox: Hitbox
        get() = hitbox ?: Hitbox(0, 0, width, height)

    /**
     * Create a copy of this sprite at given coordinates (copies values). Use for spawning
     * projectiles, particles, etc.
     *
     * Usage: val bullet = bulletSprite.at(100, 50)
     */
    fun at(x: Int, y: Int): Sprite =
        Sprite(
            name = name,
            asset = asset,
            width = width,
            height = height,
            oamSlot = oamSlot,
            position = SpritePosition("${name}_x", "${name}_y", x, y),
            binding = null, // at() creates owned position, not binding
            paletteRef = paletteRef,
            paletteIndex = paletteIndex,
            animations = animations,
            hitbox = hitbox,
            optimizationHints = optimizationHints,
        )

    /**
     * Create a copy with position bound to existing variables (live binding). Use for sprites that
     * should follow/track other positions.
     *
     * Usage: val shadow = shadowSprite.follow(player.x, player.y)
     */
    fun follow(xExpr: Expr, yExpr: Expr): Sprite {
        val xName =
            (xExpr.ir as? IRVar)?.name
                ?: error("follow() requires variable expressions, got ${xExpr.ir}")
        val yName =
            (yExpr.ir as? IRVar)?.name
                ?: error("follow() requires variable expressions, got ${yExpr.ir}")
        return Sprite(
            name = name,
            asset = asset,
            width = width,
            height = height,
            oamSlot = oamSlot,
            position = null, // follow() uses binding, not owned position
            binding = SpriteBinding(xName, yName),
            paletteRef = paletteRef,
            paletteIndex = paletteIndex,
            animations = animations,
            hitbox = hitbox,
            optimizationHints = optimizationHints,
        )
    }

    /**
     * Check collision with another sprite using AABB detection.
     *
     * Usage: whenever(player collidesWith obstacle) { scene("gameover") }
     */
    infix fun collidesWith(other: Sprite): Condition {
        require(this.isBound) {
            "Sprite '${this.name}' must have position for collision detection. Use position() or boundTo()."
        }
        require(other.isBound) {
            "Sprite '${other.name}' must have position for collision detection. Use position() or boundTo()."
        }

        val h1 = this.effectiveHitbox
        val h2 = other.effectiveHitbox

        // AABB collision:
        // x1 + w1 > x2 && x1 < x2 + w2 && y1 + h1 > y2 && y1 < y2 + h2
        // Use the x/y getters which work for both position and binding
        val x1: Expr = this.x
        val y1: Expr = this.y
        val x2: Expr = other.x
        val y2: Expr = other.y

        // Add hitbox offsets
        val left1 = x1 + h1.xOffset
        val top1 = y1 + h1.yOffset
        val right1 = left1 + h1.width
        val bottom1 = top1 + h1.height

        val left2 = x2 + h2.xOffset
        val top2 = y2 + h2.yOffset
        val right2 = left2 + h2.width
        val bottom2 = top2 + h2.height

        return (right1 isAbove left2) and
            (left1 isBelow right2) and
            (bottom1 isAbove top2) and
            (top1 isBelow bottom2)
    }

    // AnimatedSprite interface
    override val animationState: SpriteAnimationState?
        get() =
            if (hasAnimations) SpriteAnimationState(name, animations, animations.keys.firstOrNull())
            else null

    /**
     * Play an animation on this sprite.
     *
     * Usage:
     * ```kotlin
     * player.play(runAnimation)
     * player.play(hitAnimation, loop = false)
     * ```
     */
    fun play(ref: AnimationRef, loop: Boolean = true) {
        require(ref.name in animations) {
            "Animation '${ref.name}' not found. Available: ${animations.keys}"
        }
        RecordingContext.require().emit(IRAnimationPlay(name, ref.name, loop))
    }

    /** Stop the current animation */
    fun stopAnimation() {
        RecordingContext.require().emit(IRAnimationStop(name))
    }

    /** Set a specific animation frame directly */
    fun setFrame(index: Int) {
        RecordingContext.require().emit(IRAnimationSetFrame(name, index))
    }
}

/**
 * Optimization hints for a sprite asset.
 *
 * These hints inform the asset optimizer about intentional patterns in your sprite that shouldn't
 * be flagged as issues.
 */
data class SpriteOptimizationHints(
    /** Enable tile deduplication for this sprite (null = use global setting). */
    val deduplicate: Boolean?,
    /** Remove empty tiles from this sprite (null = use global setting). */
    val removeEmpty: Boolean?,
    /** Suppress all optimization warnings for this sprite. */
    val suppressWarnings: Boolean,
    /** Tile index ranges that are intentionally unique (skip dedup analysis). */
    val uniqueRegions: List<IntRange>,
    /** Maximum expected tiles (warn if exceeded). */
    val maxTiles: Int?,
)

/** Builder for sprite optimization hints. */
@GbktDsl
class SpriteOptimizeBuilder {
    /** Enable tile deduplication for this sprite. Default: inherit from global settings */
    var deduplicate: Boolean? = null

    /** Remove empty tiles from this sprite. Default: inherit from global settings */
    var removeEmpty: Boolean? = null

    /**
     * Suppress all optimization warnings for this sprite. Useful when intentional duplicates or
     * empty tiles exist.
     */
    var suppressWarnings: Boolean = false

    /**
     * Maximum expected tiles for this sprite. Optimizer will warn if the sprite exceeds this limit.
     */
    var maxTiles: Int? = null

    private val _uniqueRegions = mutableListOf<IntRange>()

    /**
     * Mark a tile range as intentionally unique (skip deduplication analysis). Use this for
     * animation frames that are meant to be similar.
     *
     * Usage: optimize { uniqueRegion(0..7) // Animation frames 0-7 are intentionally different }
     */
    fun uniqueRegion(range: IntRange) {
        _uniqueRegions.add(range)
    }

    /**
     * Mark specific tiles as intentionally unique.
     *
     * Usage: optimize { uniqueTiles(0, 1, 2, 3) }
     */
    fun uniqueTiles(vararg indices: Int) {
        indices.forEach { _uniqueRegions.add(it..it) }
    }

    internal fun build() =
        SpriteOptimizationHints(
            deduplicate = deduplicate,
            removeEmpty = removeEmpty,
            suppressWarnings = suppressWarnings,
            uniqueRegions = _uniqueRegions.toList(),
            maxTiles = maxTiles,
        )
}

@GbktDsl
class SpriteBuilder(private val asset: String, private val slot: Int) {
    var size: Dimensions = 8 x 8
    private var _name: String? = null
    private var _position: SpritePosition? = null
    private var _binding: SpriteBinding? = null
    private var _paletteRef: String? = null
    private var _paletteIndex: Int = 0
    private var _animations: Map<String, Animation> = emptyMap()
    private var _hitbox: Hitbox? = null
    private var _regions: Map<String, SpriteRegion> = emptyMap()
    private var _optimizationHints: SpriteOptimizationHints? = null

    /**
     * Set sprite's initial position - sprite will own and manage its position variables.
     *
     * Usage: val player = sprite(SpriteAsset("player.png")) { size = 8 x 16 position(80, 72) }
     * player.x += 2 // Works! Sprite owns its position.
     *
     * This is the preferred way to set sprite position. The sprite will automatically update its
     * hardware position every frame.
     */
    fun position(x: Int, y: Int) {
        _position =
            SpritePosition(
                xVarName = "sprite${slot}_x",
                yVarName = "sprite${slot}_y",
                initialX = x,
                initialY = y,
            )
    }

    /**
     * Bind sprite position to variables - auto-updates every frame.
     *
     * Usage: val player = sprite(SpriteAsset("player.png")) { size = 8 x 16 boundTo(playerX,
     * playerY) }
     *
     * This eliminates the need for manual move_sprite() calls!
     */
    fun boundTo(x: Expr, y: Expr) {
        // Extract variable names from the expressions
        val xName =
            (x.ir as? IRVar)?.name ?: error("boundTo() requires variable expressions, got ${x.ir}")
        val yName =
            (y.ir as? IRVar)?.name ?: error("boundTo() requires variable expressions, got ${y.ir}")
        _binding = SpriteBinding(xName, yName)
    }

    /**
     * Assign a palette to this sprite (GBC only).
     *
     * Usage: val player = sprite(SpriteAsset("player.png")) { size = 8 x 16 palette = playerPalette
     * // Palette object from palette() DSL }
     */
    var palette: Palette? = null
        set(value) {
            field = value
            if (value != null) {
                _paletteRef = value.name
                _paletteIndex = value.assignedSlot
            }
        }

    /**
     * Directly assign palette index (0-7) for GBC. Use this when you don't need the full palette
     * DSL.
     */
    var paletteIndex: Int
        get() = _paletteIndex
        set(value) {
            require(value in 0..7) { "Palette index must be 0-7, got $value" }
            _paletteIndex = value
        }

    /**
     * Define a collision hitbox for this sprite.
     *
     * Usage: val player = sprite(SpriteAsset("player.png")) { size = 8 x 16 boundTo(playerX,
     * playerY) hitbox(2, 2, 4, 12) // x-offset, y-offset, width, height }
     *
     * The hitbox is relative to the sprite's position. If not specified, the full sprite bounds are
     * used.
     */
    fun hitbox(xOffset: Int, yOffset: Int, width: Int, height: Int) {
        _hitbox = Hitbox(xOffset, yOffset, width, height)
    }

    /**
     * Define a collision hitbox using dimensions.
     *
     * Usage: hitbox(2 to 2, 4 x 12) // offset, size
     */
    fun hitbox(offset: Pair<Int, Int>, size: Dimensions) {
        _hitbox = Hitbox(offset.first, offset.second, size.width, size.height)
    }

    /**
     * Define named regions in the sprite sheet. Regions can then be referenced in animations by
     * name.
     *
     * Usage: val player = sprite(SpriteAsset("player.png")) { size = 8 x 16 position(80, 72)
     *
     *       regions {
     *           "idle" at 0 size 2
     *           "run" at 2 size 4
     *           "jump" at 6 size 1
     *           "attack" at 7 size 6
     *       }
     *       animations {
     *           "idle" plays (region("idle") every 30.frames)
     *           "run" plays (region("run") every 6.frames)
     *           "jump" plays regionFrame("jump", 0)
     *       }
     *   }
     */
    fun regions(init: RegionsBuilder.() -> Unit) {
        val builder = RegionsBuilder()
        builder.init()
        _regions = builder.regions.toMap()
    }

    /**
     * Define animations for this sprite.
     *
     * Usage without regions: animations { "idle" plays (frames(0, 1) every 30.frames) "run" plays
     * (frames(2, 3, 4, 5) every 6.frames) "jump" plays frame(6) }
     *
     * Usage with regions (if regions {} block was defined): animations { "idle" plays
     * (region("idle") every 30.frames) "run" plays (region("run") every 6.frames) "jump" plays
     * regionFrame("jump", 0) }
     *
     * Then in frame blocks: player.play("run")
     */
    fun animations(init: AnimationsBuilderWithRegions.() -> Unit) {
        val builder = AnimationsBuilderWithRegions(_regions)
        builder.init()
        _animations = builder.animations.toMap()
    }

    /**
     * Configure optimization hints for this sprite.
     *
     * Use this to inform the asset optimizer about intentional patterns that shouldn't be flagged
     * as issues.
     *
     * Usage: val player = sprite(SpriteAsset("player.png")) { size = 8 x 16 position(80, 72)
     *
     *       optimize {
     *           // Don't flag animation frames as duplicates
     *           uniqueRegion(0..7)
     *           // Set maximum expected tiles
     *           maxTiles = 16
     *           // Or suppress all warnings for this sprite
     *           suppressWarnings = true
     *       }
     *   }
     */
    fun optimize(init: SpriteOptimizeBuilder.() -> Unit) {
        _optimizationHints = SpriteOptimizeBuilder().apply(init).build()
    }

    fun build() =
        Sprite(
            name = _name ?: "sprite_$slot",
            asset = asset,
            width = size.width,
            height = size.height,
            oamSlot = slot,
            position = _position,
            binding = _binding,
            paletteRef = _paletteRef,
            paletteIndex = _paletteIndex,
            animations = _animations,
            hitbox = _hitbox,
            optimizationHints = _optimizationHints,
        )
}
