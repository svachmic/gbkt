/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.graphics

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.GBCColor
import io.github.gbkt.core.ir.GBCPalette
import io.github.gbkt.core.ir.IRPaletteApply
import io.github.gbkt.core.ir.IRPaletteFade
import io.github.gbkt.core.ir.IRPaletteFlash
import io.github.gbkt.core.ir.IRPaletteSetColor
import io.github.gbkt.core.ir.PaletteType

// =============================================================================
// PALETTE DSL - GBC Color Support
// =============================================================================

/** Preset color palettes for common use cases. */
enum class PalettePreset(val colors: List<GBCColor>) {
    /** Classic DMG grayscale look */
    GRAYSCALE(listOf(GBCColor.WHITE, GBCColor.LIGHT_GRAY, GBCColor.DARK_GRAY, GBCColor.BLACK)),
    /** Forest/nature theme - greens and browns */
    FOREST(
        listOf(
            GBCColor.fromHex(0xE0F8D0),
            GBCColor.fromHex(0x88C070),
            GBCColor.fromHex(0x346856),
            GBCColor.fromHex(0x081820),
        )
    ),
    /** Ocean/water theme - blues and teals */
    OCEAN(
        listOf(
            GBCColor.fromHex(0xE0F8F8),
            GBCColor.fromHex(0x70C0D8),
            GBCColor.fromHex(0x3068A8),
            GBCColor.fromHex(0x082048),
        )
    ),
    /** Fire/lava theme - reds and oranges */
    FIRE(
        listOf(
            GBCColor.fromHex(0xFFF8E0),
            GBCColor.fromHex(0xF8A060),
            GBCColor.fromHex(0xC03020),
            GBCColor.fromHex(0x401008),
        )
    ),
    /** Ice/snow theme - blues and whites */
    ICE(
        listOf(
            GBCColor.fromHex(0xF8F8FF),
            GBCColor.fromHex(0xA8D8F0),
            GBCColor.fromHex(0x5090C0),
            GBCColor.fromHex(0x203050),
        )
    ),
    /** Night/dark theme - purples and deep blues */
    NIGHT(
        listOf(
            GBCColor.fromHex(0xD0C0E8),
            GBCColor.fromHex(0x8068A8),
            GBCColor.fromHex(0x483878),
            GBCColor.fromHex(0x181028),
        )
    ),
    /** Sepia/vintage theme - warm browns */
    SEPIA(
        listOf(
            GBCColor.fromHex(0xF8E8C8),
            GBCColor.fromHex(0xC8A868),
            GBCColor.fromHex(0x886830),
            GBCColor.fromHex(0x302010),
        )
    ),
}

/** DSL wrapper for palette operations at runtime. Returned by the palette() DSL function. */
class Palette(
    val name: String,
    val colors: List<GBCColor>,
    val assignedSlot: Int,
    val type: PaletteType,
) {
    /**
     * Apply this palette to its assigned slot at runtime. Use in scene enter blocks or frame
     * blocks.
     */
    fun apply() {
        RecordingContext.require().emit(IRPaletteApply(name, assignedSlot, type))
    }

    /** Apply this palette to a specific slot (overrides assigned slot). */
    fun applyAs(slot: Int) {
        require(slot in 0..7) { "Palette slot must be 0-7, got $slot" }
        RecordingContext.require().emit(IRPaletteApply(name, slot, type))
    }

    /**
     * Flash effect - temporarily set all colors to a single color. Useful for damage/hit effects.
     */
    fun flash(hexColor: Int) {
        val flashColor = GBCColor.fromHex(hexColor)
        RecordingContext.require().emit(IRPaletteFlash(name, flashColor, type))
    }

    /** Set a single color in this palette at runtime. */
    fun setColor(index: Int, hexColor: Int) {
        require(index in 0..3) { "Color index must be 0-3, got $index" }
        val color = GBCColor.fromHex(hexColor)
        RecordingContext.require().emit(IRPaletteSetColor(name, index, color, type))
    }

    /**
     * Fade this palette toward target colors.
     *
     * @param targetColors List of 4 target colors (as hex values)
     * @param progress Expression representing fade progress (0-255)
     */
    fun fadeTo(targetColors: List<Int>, progress: Expr) {
        require(targetColors.size == 4) { "Must provide exactly 4 target colors" }
        val targets = targetColors.map { GBCColor.fromHex(it) }
        RecordingContext.require().emit(IRPaletteFade(name, targets, progress.ir, type))
    }
}

/** Builder for creating palettes in the DSL. */
@GbktDsl
class PaletteBuilder(private val name: String) {
    private val colors =
        mutableListOf(GBCColor.WHITE, GBCColor.LIGHT_GRAY, GBCColor.DARK_GRAY, GBCColor.BLACK)
    private var _slot: Int = -1 // -1 = auto-assign
    private var _type: PaletteType = PaletteType.SPRITE

    /**
     * Set all 4 colors from hex values.
     *
     * Usage: colors(0xFFFFFF, 0x88FF88, 0x448844, 0x000000)
     */
    fun colors(c0: Int, c1: Int, c2: Int, c3: Int) {
        colors[0] = GBCColor.fromHex(c0)
        colors[1] = GBCColor.fromHex(c1)
        colors[2] = GBCColor.fromHex(c2)
        colors[3] = GBCColor.fromHex(c3)
    }

    /**
     * Set a single color at a specific index (0-3).
     *
     * Usage: color(0, 255, 255, 255) // index, R, G, B
     */
    fun color(index: Int, r: Int, g: Int, b: Int) {
        require(index in 0..3) { "Color index must be 0-3, got $index" }
        colors[index] = GBCColor.fromRGB888(r, g, b)
    }

    /** Explicitly set palette slot (0-7). If not set, slot is auto-assigned. */
    var slot: Int
        get() = _slot
        set(value) {
            require(value in 0..7) { "Palette slot must be 0-7, got $value" }
            _slot = value
        }

    /** Set palette type (SPRITE or BACKGROUND). Default is SPRITE. */
    var type: PaletteType
        get() = _type
        set(value) {
            _type = value
        }

    /** Mark this as a background palette. */
    fun forBackground() {
        _type = PaletteType.BACKGROUND
    }

    /** Mark this as a sprite palette (default). */
    fun forSprites() {
        _type = PaletteType.SPRITE
    }

    internal fun build(gameBuilder: GameBuilder): GBCPalette {
        val finalSlot = if (_slot >= 0) _slot else gameBuilder.allocatePaletteSlot(_type)
        return GBCPalette(name, colors.toList(), finalSlot, _type)
    }
}
