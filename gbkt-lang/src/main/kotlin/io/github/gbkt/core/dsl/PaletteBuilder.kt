/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.GBCColor
import io.github.gbkt.core.ir.GBCPalette
import io.github.gbkt.core.ir.PaletteType
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// PALETTE BUILDER DSL
// =============================================================================

/**
 * Builder for a 4-color GBC palette.
 *
 * Construct a palette by setting all 4 color slots via [color0] through [color3], or copy from an
 * existing palette using [copy]. Call [build] to produce the final [GBCPalette].
 *
 * Usage:
 * ```kotlin
 * val forest by palette {
 *     color0(Color.WHITE)
 *     color1(Color.rgb555(16, 24, 8))
 *     color2(Color.rgb555(8, 16, 4))
 *     color3(Color.BLACK)
 * }
 * ```
 *
 * Or using a preset:
 * ```kotlin
 * val forest by palette {
 *     copy(GbcPresets.NATURE)
 * }
 * ```
 */
@GbktDsl
class PaletteBuilder(private val name: String) {
    private val colors = arrayOfNulls<GBCColor>(4)

    /** Sets palette color slot 0 (typically the lightest / transparent color). */
    fun color0(c: GBCColor) {
        colors[0] = c
    }

    /** Sets palette color slot 1. */
    fun color1(c: GBCColor) {
        colors[1] = c
    }

    /** Sets palette color slot 2. */
    fun color2(c: GBCColor) {
        colors[2] = c
    }

    /** Sets palette color slot 3 (typically the darkest / outline color). */
    fun color3(c: GBCColor) {
        colors[3] = c
    }

    /**
     * Copies all 4 colors from an existing [GBCPalette].
     *
     * Useful for extending a preset with minor modifications:
     * ```kotlin
     * val myPalette by palette {
     *     copy(GbcPresets.NATURE)
     *     color3(Color.BLACK)  // override just the darkest shade
     * }
     * ```
     */
    fun copy(source: GBCPalette) {
        source.colors.forEachIndexed { i, c -> colors[i] = c }
    }

    /**
     * Builds the final [GBCPalette].
     *
     * Throws [IllegalStateException] if any color slot is unset.
     *
     * @param type Whether this is a background or sprite palette. Defaults to
     *   [PaletteType.BACKGROUND].
     */
    internal fun build(type: PaletteType = PaletteType.BACKGROUND): GBCPalette {
        val colorList = colors.mapIndexed { i, c ->
            c ?: error("Palette '$name' missing color$i — call color$i(GBCColor) before build()")
        }
        return GBCPalette(name, colorList, type = type)
    }
}

// =============================================================================
// PALETTE PROPERTY DELEGATE
// =============================================================================

/**
 * Property delegate that registers a [GBCPalette] in the active [GameBuilder] context.
 *
 * Mirrors the [ActorDelegate] / [VarDelegate] pattern: Kotlin calls [provideDelegate] at the `by`
 * keyword, capturing the property name and registering the palette.
 *
 * Usage:
 * ```kotlin
 * game("MyGame") {
 *     val forest by palette {
 *         color0(Color.WHITE)
 *         color1(Color.rgb555(16, 24, 8))
 *         color2(Color.rgb555(8, 16, 4))
 *         color3(Color.BLACK)
 *     }
 * }
 * ```
 */
class PaletteDelegate(private val block: PaletteBuilder.() -> Unit) {
    /**
     * Called by Kotlin at the `by` keyword. Captures [property].name as the palette name, runs the
     * builder block, and registers the resulting [GBCPalette] in the active [GameBuilder].
     */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, GBCPalette> {
        val name = property.name
        val builder = PaletteBuilder(name)
        builder.block()
        val palette = builder.build()
        // Register palette in GameBuilder context — same pattern as actor/variable delegates
        GameBuilderContext.current?.registerPalette(palette)
            ?: error("palette { } called outside a game { } block")
        return ReadOnlyProperty { _, _ -> palette }
    }
}

// =============================================================================
// TOP-LEVEL DSL FACTORY FUNCTION
// =============================================================================

/**
 * Creates a [PaletteDelegate] for declaring a GBC palette via property delegation.
 *
 * The palette name is inferred from the Kotlin property name at the `by` keyword.
 *
 * Usage:
 * ```kotlin
 * val dungeon by palette {
 *     color0(Color.LIGHT_GRAY)
 *     color1(Color.rgb555(16, 14, 12))
 *     color2(Color.rgb555(10, 8, 8))
 *     color3(Color.BLACK)
 * }
 * ```
 */
fun palette(block: PaletteBuilder.() -> Unit): PaletteDelegate = PaletteDelegate(block)

// =============================================================================
// SPRITE PALETTE PROPERTY DELEGATE
// =============================================================================

/**
 * Property delegate that registers a [GBCPalette] with [PaletteType.SPRITE] in the active
 * [GameBuilder] context.
 *
 * Mirrors [PaletteDelegate] exactly, but passes [PaletteType.SPRITE] to [PaletteBuilder.build].
 * This means the generated C code will call `set_sprite_palette()` instead of `set_bkg_palette()`
 * when the palette is applied via a [SetPalette] script op.
 *
 * Usage:
 * ```kotlin
 * game("MyGame") {
 *     val gray by spritePalette {
 *         color0(Color.WHITE)
 *         color1(Color.rgb555(20, 20, 20))
 *         color2(Color.rgb555(10, 10, 10))
 *         color3(Color.BLACK)
 *     }
 * }
 * ```
 */
class SpritePaletteDelegate(private val block: PaletteBuilder.() -> Unit) {
    /**
     * Called by Kotlin at the `by` keyword. Captures [property].name as the palette name, runs the
     * builder block, and registers the resulting [GBCPalette] (type=SPRITE) in the active
     * [GameBuilder].
     */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, GBCPalette> {
        val name = property.name
        val builder = PaletteBuilder(name)
        builder.block()
        val palette = builder.build(PaletteType.SPRITE)
        // Register palette in GameBuilder context — same pattern as PaletteDelegate
        GameBuilderContext.current?.registerPalette(palette)
            ?: error("spritePalette { } called outside a game { } block")
        return ReadOnlyProperty { _, _ -> palette }
    }
}

// =============================================================================
// SPRITE PALETTE TOP-LEVEL DSL FACTORY FUNCTION
// =============================================================================

/**
 * Creates a [SpritePaletteDelegate] for declaring a GBC sprite palette via property delegation.
 *
 * The palette name is inferred from the Kotlin property name at the `by` keyword. Unlike [palette],
 * which defaults to [PaletteType.BACKGROUND], this factory produces a [GBCPalette] with
 * [PaletteType.SPRITE]. The GBDK backend will emit `set_sprite_palette()` calls for sprite
 * palettes.
 *
 * Usage:
 * ```kotlin
 * val playerColors by spritePalette {
 *     color0(Color.WHITE)
 *     color1(Color.rgb555(20, 18, 8))
 *     color2(Color.rgb555(14, 12, 4))
 *     color3(Color.BLACK)
 * }
 * ```
 */
fun spritePalette(block: PaletteBuilder.() -> Unit): SpritePaletteDelegate =
    SpritePaletteDelegate(block)
