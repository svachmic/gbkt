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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// PALETTE BUILDER TESTS
// Verifies PaletteBuilder, PaletteDelegate, and GbcPresets
// =============================================================================

class PaletteBuilderTest {

    // =========================================================================
    // PaletteBuilder.build() — 4-color palette construction
    // =========================================================================

    @Test
    fun `builder with all 4 colors produces valid GBCPalette`() {
        val builder = PaletteBuilder("test")
        builder.color0(GbcColor.WHITE)
        builder.color1(gbc(20, 20, 20))
        builder.color2(gbc(10, 10, 10))
        builder.color3(GbcColor.BLACK)

        val palette = builder.build()

        assertEquals("test", palette.name)
        assertEquals(4, palette.colors.size)
        assertEquals(GbcColor.WHITE, palette.colors[0])
        assertEquals(gbc(20, 20, 20), palette.colors[1])
        assertEquals(gbc(10, 10, 10), palette.colors[2])
        assertEquals(GbcColor.BLACK, palette.colors[3])
        assertEquals(PaletteType.BACKGROUND, palette.type) // default type
    }

    @Test
    fun `builder missing color0 throws error on build`() {
        val builder = PaletteBuilder("incomplete")
        builder.color1(GbcColor.WHITE)
        builder.color2(gbc(10, 10, 10))
        builder.color3(GbcColor.BLACK)

        assertFailsWith<IllegalStateException> { builder.build() }
    }

    @Test
    fun `builder missing color2 throws error on build`() {
        val builder = PaletteBuilder("incomplete")
        builder.color0(GbcColor.WHITE)
        builder.color1(gbc(20, 20, 20))
        builder.color3(GbcColor.BLACK)

        assertFailsWith<IllegalStateException> { builder.build() }
    }

    @Test
    fun `builder with sprite type produces SPRITE palette`() {
        val builder = PaletteBuilder("sprite_pal")
        builder.color0(GbcColor.WHITE)
        builder.color1(gbc(20, 20, 20))
        builder.color2(gbc(10, 10, 10))
        builder.color3(GbcColor.BLACK)

        val palette = builder.build(PaletteType.SPRITE)
        assertEquals(PaletteType.SPRITE, palette.type)
    }

    @Test
    fun `copy copies all 4 colors from source palette`() {
        val builder = PaletteBuilder("copy_test")
        builder.copy(GbcPresets.NATURE)

        val palette = builder.build()

        assertEquals(GbcPresets.NATURE.colors[0], palette.colors[0])
        assertEquals(GbcPresets.NATURE.colors[1], palette.colors[1])
        assertEquals(GbcPresets.NATURE.colors[2], palette.colors[2])
        assertEquals(GbcPresets.NATURE.colors[3], palette.colors[3])
    }

    @Test
    fun `copy then override one color works`() {
        val builder = PaletteBuilder("modified")
        builder.copy(GbcPresets.DUNGEON)
        builder.color0(GbcColor.WHITE) // override the first color

        val palette = builder.build()

        assertEquals(GbcColor.WHITE, palette.colors[0])
        // rest from DUNGEON preset
        assertEquals(GbcPresets.DUNGEON.colors[1], palette.colors[1])
        assertEquals(GbcPresets.DUNGEON.colors[2], palette.colors[2])
        assertEquals(GbcPresets.DUNGEON.colors[3], palette.colors[3])
    }

    // =========================================================================
    // PaletteDelegate — val x by palette { ... } DSL pattern
    // =========================================================================

    @Test
    fun `palette delegate registers palette in game IR`() {
        val ir =
            game("TestGame") {
                    val forest by palette {
                        color0(GbcColor.WHITE)
                        color1(gbc(16, 24, 8))
                        color2(gbc(8, 16, 4))
                        color3(GbcColor.BLACK)
                    }
                    // Need to suppress unused warning — forest is used by delegate registration
                    @Suppress("UNUSED_VARIABLE") val _unused = forest

                    scene("main") {}
                    start = "main"
                }
                .build()

        assertEquals(1, ir.palettes.size)
        assertEquals("forest", ir.palettes[0].name)
        assertEquals(4, ir.palettes[0].colors.size)
    }

    @Test
    fun `multiple palette delegates register multiple palettes`() {
        val ir =
            game("TestGame") {
                    val bg by palette {
                        color0(GbcColor.WHITE)
                        color1(gbc(20, 20, 20))
                        color2(gbc(10, 10, 10))
                        color3(GbcColor.BLACK)
                    }
                    val sprite by palette { copy(GbcPresets.FIRE) }
                    // Suppress unused variable warnings
                    @Suppress("UNUSED_VARIABLE") val _bg = bg
                    @Suppress("UNUSED_VARIABLE") val _sprite = sprite

                    scene("main") {}
                    start = "main"
                }
                .build()

        assertEquals(2, ir.palettes.size)
    }

    @Test
    fun `palette delegate called outside game block throws error`() {
        assertFailsWith<IllegalStateException> {
            val pal by palette {
                color0(GbcColor.WHITE)
                color1(gbc(20, 20, 20))
                color2(gbc(10, 10, 10))
                color3(GbcColor.BLACK)
            }
            // accessing pal triggers provideDelegate
            @Suppress("UNUSED_VARIABLE") val unused = pal
        }
    }

    // =========================================================================
    // GbcPresets — curated preset palettes
    // =========================================================================

    @Test
    fun `GbcPresets CLASSIC_GREEN has exactly 4 colors`() {
        assertEquals(4, GbcPresets.CLASSIC_GREEN.colors.size)
    }

    @Test
    fun `GbcPresets NATURE has exactly 4 colors`() {
        assertEquals(4, GbcPresets.NATURE.colors.size)
    }

    @Test
    fun `GbcPresets FIRE has exactly 4 colors`() {
        assertEquals(4, GbcPresets.FIRE.colors.size)
    }

    @Test
    fun `GbcPresets ICE has exactly 4 colors`() {
        assertEquals(4, GbcPresets.ICE.colors.size)
    }

    @Test
    fun `GbcPresets OCEAN has exactly 4 colors`() {
        assertEquals(4, GbcPresets.OCEAN.colors.size)
    }

    @Test
    fun `GbcPresets DUNGEON has exactly 4 colors`() {
        assertEquals(4, GbcPresets.DUNGEON.colors.size)
    }

    @Test
    fun `all 16 GbcPresets have exactly 4 colors each`() {
        val presets =
            listOf(
                GbcPresets.CLASSIC_GREEN,
                GbcPresets.NATURE,
                GbcPresets.FIRE,
                GbcPresets.ICE,
                GbcPresets.OCEAN,
                GbcPresets.DUNGEON,
                GbcPresets.CAVERN,
                GbcPresets.SUNSET,
                GbcPresets.NIGHT,
                GbcPresets.PASTEL,
                GbcPresets.SEPIA,
                GbcPresets.NEON,
                GbcPresets.MONOCHROME_BLUE,
                GbcPresets.WARM_GRAY,
                GbcPresets.UI_LIGHT,
                GbcPresets.UI_DARK,
            )

        assertEquals(16, presets.size, "GbcPresets should have exactly 16 entries")
        presets.forEach { preset ->
            assertEquals(
                4,
                preset.colors.size,
                "Preset '${preset.name}' must have exactly 4 colors",
            )
        }
    }

    @Test
    fun `all GbcPresets colors are valid GBCColor values`() {
        val allPresets =
            listOf(
                GbcPresets.CLASSIC_GREEN,
                GbcPresets.NATURE,
                GbcPresets.FIRE,
                GbcPresets.ICE,
                GbcPresets.OCEAN,
                GbcPresets.DUNGEON,
                GbcPresets.CAVERN,
                GbcPresets.SUNSET,
                GbcPresets.NIGHT,
                GbcPresets.PASTEL,
                GbcPresets.SEPIA,
                GbcPresets.NEON,
                GbcPresets.MONOCHROME_BLUE,
                GbcPresets.WARM_GRAY,
                GbcPresets.UI_LIGHT,
                GbcPresets.UI_DARK,
            )
        allPresets.forEach { preset ->
            preset.colors.forEach { color ->
                assertTrue(
                    color.red in 0..31,
                    "Color in '${preset.name}' has invalid red: ${color.red}",
                )
                assertTrue(
                    color.green in 0..31,
                    "Color in '${preset.name}' has invalid green: ${color.green}",
                )
                assertTrue(
                    color.blue in 0..31,
                    "Color in '${preset.name}' has invalid blue: ${color.blue}",
                )
            }
        }
    }

    @Test
    fun `GbcPresets CLASSIC_GREEN has correct name`() {
        assertEquals("classic_green", GbcPresets.CLASSIC_GREEN.name)
    }

    // =========================================================================
    // Per-actor palette override (Gap 1)
    // =========================================================================

    @Test
    fun `actor with palette stores GBCPalette in ActorIR`() {
        val ir =
            game("TestGame") {
                    val hero by actor {
                        position(80, 72)
                        palette(GbcPresets.FIRE)
                    }
                    @Suppress("UNUSED_VARIABLE") val unused = hero

                    scene("main") {}
                    start = "main"
                }
                .build()

        val actorIr = ir.actors.find { it.id == "hero" }
        assertNotNull(actorIr)
        assertNotNull(actorIr.palette)
        assertEquals("fire", actorIr.palette!!.name)
    }

    @Test
    fun `actor palette injects SetPalette into scene enter ops`() {
        val ir =
            game("TestGame") {
                    val hero by actor {
                        position(80, 72)
                        palette(GbcPresets.FIRE)
                    }
                    @Suppress("UNUSED_VARIABLE") val unused = hero

                    scene("main") { enter {} }
                    start = "main"
                }
                .build()

        val scene = ir.scenes.find { it.id == "main" }
        assertNotNull(scene)
        val setPal = scene.enterOps.filterIsInstance<io.github.gbkt.core.ir.SetPalette>()
        assertEquals(1, setPal.size)
        assertEquals("fire", setPal[0].paletteName)
    }
}
