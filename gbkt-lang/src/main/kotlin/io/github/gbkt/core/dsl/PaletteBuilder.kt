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
 *     color0(GbcColor.WHITE)
 *     color1(gbc(16, 24, 8))
 *     color2(gbc(8, 16, 4))
 *     color3(GbcColor.BLACK)
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
     *     color3(GbcColor.BLACK)  // override just the darkest shade
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
        val colorList =
            colors.mapIndexed { i, c ->
                c
                    ?: error(
                        "Palette '$name' missing color$i — call color$i(GBCColor) before build()"
                    )
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
 *         color0(GbcColor.WHITE)
 *         color1(gbc(16, 24, 8))
 *         color2(gbc(8, 16, 4))
 *         color3(GbcColor.BLACK)
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
 *     color0(GbcColor.LIGHT_GRAY)
 *     color1(gbc(16, 14, 12))
 *     color2(gbc(10, 8, 8))
 *     color3(GbcColor.BLACK)
 * }
 * ```
 */
fun palette(block: PaletteBuilder.() -> Unit): PaletteDelegate = PaletteDelegate(block)
