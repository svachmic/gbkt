/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ui

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRStatusBarFlash
import io.github.gbkt.core.ir.IRStatusBarHide
import io.github.gbkt.core.ir.IRStatusBarSetValue
import io.github.gbkt.core.ir.IRStatusBarShow
import io.github.gbkt.core.ir.IRStatusBarTick
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// STATUS BAR SYSTEM - HP/SP/Resource display bars
// =============================================================================

/** Status bar visual style. */
enum class StatusBarStyle {
    /** Solid filled bar */
    SOLID,

    /** Segmented bar with visible divisions */
    SEGMENTED,

    /** Individual pips/hearts */
    PIPS,

    /** Numeric display only */
    NUMERIC,
}

/** Bar orientation. */
enum class BarOrientation {
    HORIZONTAL,
    VERTICAL,
}

/**
 * Status bar definition.
 *
 * Defines how a status bar is displayed and animated.
 */
class StatusBarDefinition(
    /** Unique identifier */
    val name: String,
    /** X position in pixels (or tiles for BKG) */
    val x: Int,
    /** Y position in pixels (or tiles for BKG) */
    val y: Int,
    /** Bar width in pixels (or segments/pips) */
    val width: Int,
    /** Bar height in pixels */
    val height: Int,
    /** Visual style */
    val style: StatusBarStyle,
    /** Orientation */
    val orientation: BarOrientation,
    /** Whether to show current/max text */
    val showText: Boolean,
    /** Smooth animation speed (0 = instant) */
    val animationSpeed: Int,
    /** Low value threshold for warning effects */
    val lowThreshold: Int,
    /** Low value callbacks */
    val onLowStatements: List<IRStatement>,
    /** Critical value threshold */
    val criticalThreshold: Int,
    /** Critical value callbacks */
    val onCriticalStatements: List<IRStatement>,
    /** Number of segments (for SEGMENTED style) */
    val segments: Int,
    /** Number of pips (for PIPS style) */
    val pips: Int,
    /** Tile IDs for rendering (for tile-based rendering) */
    val tileIds: StatusBarTiles,
    /** Whether to use sprites (true) or background tiles (false) */
    val useSprites: Boolean,
    /** Starting sprite index for sprite-based bars */
    val spriteStartIndex: Int,
    /** System index for code generation */
    var systemIndex: Int = -1,
)

/** Tile IDs for status bar rendering. */
data class StatusBarTiles(
    /** Empty bar tile (left cap for horizontal) */
    val empty: Int = 0,
    /** Filled bar tile */
    val filled: Int = 1,
    /** Partially filled tile (for smooth display) */
    val partial: Int = 2,
    /** Left cap tile */
    val leftCap: Int = 3,
    /** Right cap tile */
    val rightCap: Int = 4,
    /** Empty pip tile */
    val pipEmpty: Int = 5,
    /** Filled pip tile */
    val pipFilled: Int = 6,
)

// =============================================================================
// STATUS BAR HANDLE
// =============================================================================

/**
 * Handle for status bar runtime operations.
 *
 * Usage:
 * ```kotlin
 * val hpBar by statusBar("hp") {
 *     position(8, 8)
 *     size(64, 8)
 *     style(StatusBarStyle.SOLID)
 *     animationSpeed(4)
 *     lowThreshold(25) { playSound(warning) }
 * }
 *
 * // In scene:
 * every.frame {
 *     hpBar.setValue(hero.hp, hero.hpMax)
 *     hpBar.tick()
 * }
 * ```
 */
class StatusBarHandle internal constructor(internal val definition: StatusBarDefinition) {
    /** Set the current and max values */
    fun setValue(current: Int, max: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRStatusBarSetValue(definition.name, IRLiteral(current), IRLiteral(max)))
        }
    }

    /** Set the current and max values from expressions */
    fun setValue(current: Expr, max: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRStatusBarSetValue(definition.name, current.ir, max.ir))
        }
    }

    /** Set just the current value (max stays same) */
    fun setCurrent(current: Int) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRStatusBarSetValue(definition.name, IRLiteral(current), IRLiteral(-1)))
        }
    }

    /** Set just the current value from expression */
    fun setCurrent(current: Expr) {
        if (RecordingContext.isRecording) {
            RecordingContext.require()
                .emit(IRStatusBarSetValue(definition.name, current.ir, IRLiteral(-1)))
        }
    }

    /** Show the status bar */
    fun show() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRStatusBarShow(definition.name))
        }
    }

    /** Hide the status bar */
    fun hide() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRStatusBarHide(definition.name))
        }
    }

    /** Update animation (call each frame) */
    fun tick() {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRStatusBarTick(definition.name))
        }
    }

    /** Flash the bar (for damage/heal effects) */
    fun flash(duration: Int = 30) {
        if (RecordingContext.isRecording) {
            RecordingContext.require().emit(IRStatusBarFlash(definition.name, IRLiteral(duration)))
        }
    }
}

// =============================================================================
// STATUS BAR BUILDER
// =============================================================================

/** Property delegate for status bars. */
class StatusBarDelegate(
    private val gameBuilder: GameBuilder,
    private val init: StatusBarBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, StatusBarHandle>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, StatusBarHandle> {
        val builder = StatusBarBuilder(property.name)
        builder.init()
        val definition = builder.build()
        gameBuilder.registerStatusBar(definition)

        val handle = StatusBarHandle(definition)
        return ReadOnlyProperty { _, _ -> handle }
    }
}

/** Builder for status bar definitions. */
@GbktDsl
class StatusBarBuilder(private val name: String) {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 32
    private var height: Int = 4
    private var style: StatusBarStyle = StatusBarStyle.SOLID
    private var orientation: BarOrientation = BarOrientation.HORIZONTAL
    private var showText: Boolean = false
    private var animationSpeed: Int = 4
    private var lowThreshold: Int = 25
    private var onLowStatements: List<IRStatement> = emptyList()
    private var criticalThreshold: Int = 10
    private var onCriticalStatements: List<IRStatement> = emptyList()
    private var segments: Int = 8
    private var pips: Int = 10
    private var tileIds: StatusBarTiles = StatusBarTiles()
    private var useSprites: Boolean = false
    private var spriteStartIndex: Int = 0

    /** Set position */
    fun position(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    /** Set size */
    fun size(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    /** Set visual style */
    fun style(style: StatusBarStyle) {
        this.style = style
    }

    /** Set orientation */
    fun orientation(orientation: BarOrientation) {
        this.orientation = orientation
    }

    /** Show numeric text */
    fun showText(show: Boolean = true) {
        this.showText = show
    }

    /** Set animation speed (0 = instant) */
    fun animationSpeed(speed: Int) {
        require(speed >= 0) { "Animation speed must be non-negative" }
        this.animationSpeed = speed
    }

    /** Set low threshold with callback */
    fun lowThreshold(threshold: Int, onLow: () -> Unit = {}) {
        this.lowThreshold = threshold
        if (onLow != {}) {
            val recorder = StatementRecorder()
            RecordingContext.record(recorder, onLow)
            onLowStatements = recorder.statements
        }
    }

    /** Set critical threshold with callback */
    fun criticalThreshold(threshold: Int, onCritical: () -> Unit = {}) {
        this.criticalThreshold = threshold
        if (onCritical != {}) {
            val recorder = StatementRecorder()
            RecordingContext.record(recorder, onCritical)
            onCriticalStatements = recorder.statements
        }
    }

    /** Number of segments for SEGMENTED style */
    fun segments(count: Int) {
        require(count > 0) { "Segment count must be positive" }
        this.segments = count
    }

    /** Number of pips for PIPS style */
    fun pips(count: Int) {
        require(count > 0) { "Pip count must be positive" }
        this.pips = count
    }

    /** Set tile IDs for tile-based rendering */
    fun tiles(init: StatusBarTilesBuilder.() -> Unit) {
        val builder = StatusBarTilesBuilder()
        builder.init()
        tileIds = builder.build()
    }

    /** Use sprites instead of background tiles */
    fun useSprites(use: Boolean = true) {
        this.useSprites = use
    }

    /** Set starting sprite index (for sprite-based bars) */
    fun spriteStart(index: Int) {
        require(index >= 0 && index < 40) { "Sprite index must be 0-39" }
        this.spriteStartIndex = index
    }

    internal fun build() =
        StatusBarDefinition(
            name = name,
            x = x,
            y = y,
            width = width,
            height = height,
            style = style,
            orientation = orientation,
            showText = showText,
            animationSpeed = animationSpeed,
            lowThreshold = lowThreshold,
            onLowStatements = onLowStatements,
            criticalThreshold = criticalThreshold,
            onCriticalStatements = onCriticalStatements,
            segments = segments,
            pips = pips,
            tileIds = tileIds,
            useSprites = useSprites,
            spriteStartIndex = spriteStartIndex,
        )
}

/** Builder for status bar tile IDs. */
@GbktDsl
class StatusBarTilesBuilder {
    private var empty: Int = 0
    private var filled: Int = 1
    private var partial: Int = 2
    private var leftCap: Int = 3
    private var rightCap: Int = 4
    private var pipEmpty: Int = 5
    private var pipFilled: Int = 6

    fun empty(tile: Int) {
        empty = tile
    }

    fun filled(tile: Int) {
        filled = tile
    }

    fun partial(tile: Int) {
        partial = tile
    }

    fun leftCap(tile: Int) {
        leftCap = tile
    }

    fun rightCap(tile: Int) {
        rightCap = tile
    }

    fun pipEmpty(tile: Int) {
        pipEmpty = tile
    }

    fun pipFilled(tile: Int) {
        pipFilled = tile
    }

    internal fun build() =
        StatusBarTiles(
            empty = empty,
            filled = filled,
            partial = partial,
            leftCap = leftCap,
            rightCap = rightCap,
            pipEmpty = pipEmpty,
            pipFilled = pipFilled,
        )
}

// =============================================================================
// GAME BUILDER EXTENSION
// =============================================================================

/** Create a status bar. */
fun GameBuilder.statusBar(init: StatusBarBuilder.() -> Unit): StatusBarDelegate {
    return StatusBarDelegate(this, init)
}
