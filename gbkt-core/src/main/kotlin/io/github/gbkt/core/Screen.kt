/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.IRClearScreen
import io.github.gbkt.core.ir.IRShowBackground
import io.github.gbkt.core.ir.IRShowSprites
import io.github.gbkt.core.ir.x

// =============================================================================
// SCREEN CONSTANTS AND CONTROL
// =============================================================================

/**
 * Game Boy screen constants and control functions.
 *
 * The Game Boy has a fixed resolution of 160x144 pixels (20x18 tiles).
 *
 * Usage:
 * ```kotlin
 * // Screen dimensions
 * val centerX = screen.width / 2  // 80
 * val centerY = screen.height / 2 // 72
 *
 * // Screen control
 * screen.clear()
 * screen.showSprites()
 * screen.hideBackground()
 * ```
 */
@Suppress("ClassNaming") // DSL convention: lowercase for fluent API
object screen {
    /** Screen width in pixels (160) */
    const val width = 160

    /** Screen height in pixels (144) */
    const val height = 144

    /** Screen width in tiles (20) */
    const val tileWidth = 20 // 160/8 tiles

    /** Screen height in tiles (18) */
    const val tileHeight = 18 // 144/8 tiles

    /** Center point of the screen (80, 72) */
    val center = 80 x 72

    /**
     * Get playable bounds accounting for sprite size.
     *
     * @param spriteWidth Width of the sprite in pixels
     * @param spriteHeight Height of the sprite in pixels
     * @return Rectangle representing the playable area
     */
    fun bounds(spriteWidth: Int = 8, spriteHeight: Int = 8) =
        Rectangle(8, 16, width - spriteWidth, height - spriteHeight)

    // =========================================================================
    // SCREEN CONTROL METHODS
    // =========================================================================

    /** Clear the screen */
    fun clear() {
        RecordingContext.require().emit(IRClearScreen)
    }

    /** Show all sprites */
    fun showSprites() {
        RecordingContext.require().emit(IRShowSprites(true))
    }

    /** Hide all sprites */
    fun hideSprites() {
        RecordingContext.require().emit(IRShowSprites(false))
    }

    /** Show background layer */
    fun showBackground() {
        RecordingContext.require().emit(IRShowBackground(true))
    }

    /** Hide background layer */
    fun hideBackground() {
        RecordingContext.require().emit(IRShowBackground(false))
    }
}

/**
 * A rectangle defined by position and dimensions.
 *
 * @property x Left edge X coordinate
 * @property y Top edge Y coordinate
 * @property width Width in pixels
 * @property height Height in pixels
 */
data class Rectangle(val x: Int, val y: Int, val width: Int, val height: Int) {
    /** Range of X coordinates covered by this rectangle */
    val xRange
        get() = x..(x + width)

    /** Range of Y coordinates covered by this rectangle */
    val yRange
        get() = y..(y + height)
}
