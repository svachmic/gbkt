/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.PaletteType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// =============================================================================
// SPRITE PALETTE DELEGATE TESTS
// Verifies spritePalette { } factory and SpritePaletteDelegate behavior.
// Migrated from legacy color API to Color.rgb555/Color.* (Plan 13.3-07)
// =============================================================================

class SpritePaletteDelegateTest {

    // =========================================================================
    // Behavior 1: spritePalette { } produces GBCPalette with PaletteType.SPRITE
    // =========================================================================

    @Test
    fun `spritePalette delegate registers GBCPalette with SPRITE type`() {
        val ir =
            game("TestGame") {
                    val gray by spritePalette {
                        color0(Color.WHITE)
                        color1(Color.rgb555(20, 20, 20))
                        color2(Color.rgb555(10, 10, 10))
                        color3(Color.BLACK)
                    }
                    // Suppress unused variable — registration happens at delegate provision time
                    @Suppress("UNUSED_VARIABLE") val _unused = gray

                    val mainScene = scene("main") {}
                    start = mainScene
                }
                .build()

        assertEquals(1, ir.palettes.size)
        val palette = ir.palettes[0]
        assertEquals("gray", palette.name)
        assertEquals(PaletteType.SPRITE, palette.type)
        assertEquals(4, palette.colors.size)
    }

    @Test
    fun `spritePalette palette name is inferred from property name`() {
        val ir =
            game("TestGame") {
                    val heroSprites by spritePalette {
                        color0(Color.WHITE)
                        color1(Color.rgb555(20, 20, 20))
                        color2(Color.rgb555(10, 10, 10))
                        color3(Color.BLACK)
                    }
                    @Suppress("UNUSED_VARIABLE") val _unused = heroSprites

                    val mainScene = scene("main") {}
                    start = mainScene
                }
                .build()

        assertEquals("heroSprites", ir.palettes[0].name)
    }

    // =========================================================================
    // Behavior 2: spritePalette { } outside game { } block → error
    // =========================================================================

    @Test
    fun `spritePalette called outside game block throws error`() {
        assertFailsWith<IllegalStateException> {
            val pal by spritePalette {
                color0(Color.WHITE)
                color1(Color.rgb555(20, 20, 20))
                color2(Color.rgb555(10, 10, 10))
                color3(Color.BLACK)
            }
            @Suppress("UNUSED_VARIABLE") val unused = pal
        }
    }

    // =========================================================================
    // Behavior 3: Existing palette { } still defaults to PaletteType.BACKGROUND
    // =========================================================================

    @Test
    fun `existing palette factory still produces BACKGROUND type palette`() {
        val ir =
            game("TestGame") {
                    val bg by palette {
                        color0(Color.WHITE)
                        color1(Color.rgb555(20, 20, 20))
                        color2(Color.rgb555(10, 10, 10))
                        color3(Color.BLACK)
                    }
                    @Suppress("UNUSED_VARIABLE") val _unused = bg

                    val mainScene = scene("main") {}
                    start = mainScene
                }
                .build()

        assertEquals(1, ir.palettes.size)
        assertEquals(PaletteType.BACKGROUND, ir.palettes[0].type)
    }

    @Test
    fun `spritePalette and palette can coexist in same game`() {
        val ir =
            game("TestGame") {
                    val bg by palette {
                        color0(Color.WHITE)
                        color1(Color.rgb555(20, 20, 20))
                        color2(Color.rgb555(10, 10, 10))
                        color3(Color.BLACK)
                    }
                    val spr by spritePalette {
                        color0(Color.WHITE)
                        color1(Color.rgb555(15, 5, 5))
                        color2(Color.rgb555(10, 3, 3))
                        color3(Color.BLACK)
                    }
                    @Suppress("UNUSED_VARIABLE") val _bg = bg
                    @Suppress("UNUSED_VARIABLE") val _spr = spr

                    val mainScene = scene("main") {}
                    start = mainScene
                }
                .build()

        assertEquals(2, ir.palettes.size)
        val bgPalette = ir.palettes.find { it.name == "bg" }!!
        val sprPalette = ir.palettes.find { it.name == "spr" }!!
        assertEquals(PaletteType.BACKGROUND, bgPalette.type)
        assertEquals(PaletteType.SPRITE, sprPalette.type)
    }
}
