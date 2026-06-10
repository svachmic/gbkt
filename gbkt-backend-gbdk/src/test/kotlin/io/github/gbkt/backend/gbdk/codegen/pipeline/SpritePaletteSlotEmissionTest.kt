/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.dsl.Color
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.dsl.spritePalette
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.GbcTarget
import kotlin.test.Test
import kotlin.test.assertTrue

// =============================================================================
// SPRITE PALETTE SLOT EMISSION TESTS
// Verifies that when multiple spritePalette { } declarations are registered in a
// scene via palette(), the generated C calls set_sprite_palette() with distinct
// slot arguments 0u, 1u, 2u, 3u — not all 0u.
//
// Bug described in .planning/phases/10-port-metasprites-gbdk-example-to-gbkt/
//   evidence/c-diff.md §"GBC palette slot bug (seed candidate #1)":
//   SceneBuilder.palette() defaulted all auto-slot palettes to slot 0, so
//   set_sprite_palette(0u,…), set_sprite_palette(0u,…), set_sprite_palette(0u,…),
//   set_sprite_palette(0u,…) was emitted — only the LAST palette was loaded.
//
// Fix: SceneBuilder.palette() must assign sequential slot indices (0, 1, 2, …)
// when palette.slot == -1 (auto-assign), using paletteOps.size as the counter.
//
// Migrated from legacy color API to Color.rgb555/Color.* (Plan 13.3-07)
// =============================================================================

// ---------------------------------------------------------------------------
// Brace-walk helper (same approach as GbcCompatEmissionTest)
// ---------------------------------------------------------------------------

private fun extractFunctionBodyForSlotTest(source: String, signature: String): String? {
    val sigIdx = source.indexOf(signature)
    if (sigIdx == -1) return null
    val openIdx = source.indexOf('{', sigIdx + signature.length)
    if (openIdx == -1) return null
    var depth = 0
    var i = openIdx
    while (i < source.length) {
        when (source[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return source.substring(openIdx + 1, i)
            }
        }
        i++
    }
    return null
}

// ---------------------------------------------------------------------------
// Minimal DSL game builder with 4 sprite palettes assigned to a scene
// ---------------------------------------------------------------------------

private fun buildFourPaletteGame() =
    game("FourPaletteTest") {
            config {
                cartridge = Cartridge.ROM_ONLY
                romBanks = 2
                target(GbcTarget.GBC_COMPATIBLE)
            }

            val gray by spritePalette {
                color0(Color.WHITE)
                color1(Color.rgb555(21, 21, 21))
                color2(Color.rgb555(10, 10, 10))
                color3(Color.BLACK)
            }
            val pink by spritePalette {
                color0(Color.WHITE)
                color1(Color.rgb555(31, 0, 31))
                color2(Color.rgb555(21, 0, 21))
                color3(Color.rgb555(10, 0, 10))
            }
            val cyan by spritePalette {
                color0(Color.WHITE)
                color1(Color.rgb555(10, 31, 31))
                color2(Color.rgb555(0, 21, 21))
                color3(Color.rgb555(0, 10, 10))
            }
            val green by spritePalette {
                color0(Color.WHITE)
                color1(Color.rgb555(21, 31, 21))
                color2(Color.rgb555(0, 21, 0))
                color3(Color.rgb555(0, 10, 0))
            }

            val playScene =
                scene("play") {
                    palette(gray)
                    palette(pink)
                    palette(cyan)
                    palette(green)
                    enter {}
                }

            start = playScene
        }
        .build()

// =============================================================================
// TEST CLASS
// =============================================================================

class SpritePaletteSlotEmissionTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // Test: 4 spritePalette declarations → set_sprite_palette(0u,…), (1u,…), (2u,…), (3u,…)
    // Uses brace-walk to scope assertion to play_enter() body only.
    // =========================================================================

    @Test
    fun `four spritePalette declarations emit set_sprite_palette with slots 0 through 3`() {
        val gameIR = buildFourPaletteGame()

        // play_enter may be in main.c (HOME fast-path for single-scene ROM_ONLY) or bank1.c
        val allFiles = pipeline.generate(gameIR).files
        val allSource = allFiles.values.joinToString("\n")

        val enterBody =
            extractFunctionBodyForSlotTest(allSource, "void play_enter(void)")
                ?: error(
                    "Could not extract play_enter() body from generated files. " +
                        "Files generated: ${allFiles.keys}. " +
                        "Does main.c contain 'play_enter': ${allFiles["main.c"]?.contains("play_enter")}"
                )

        // Each palette must use its own sequential slot — not all 0u
        assertTrue(
            enterBody.contains("set_sprite_palette(0u, 1u,"),
            "Expected set_sprite_palette(0u, 1u, …) for gray palette (slot 0). play_enter body:\n$enterBody",
        )
        assertTrue(
            enterBody.contains("set_sprite_palette(1u, 1u,"),
            "Expected set_sprite_palette(1u, 1u, …) for pink palette (slot 1). play_enter body:\n$enterBody",
        )
        assertTrue(
            enterBody.contains("set_sprite_palette(2u, 1u,"),
            "Expected set_sprite_palette(2u, 1u, …) for cyan palette (slot 2). play_enter body:\n$enterBody",
        )
        assertTrue(
            enterBody.contains("set_sprite_palette(3u, 1u,"),
            "Expected set_sprite_palette(3u, 1u, …) for green palette (slot 3). play_enter body:\n$enterBody",
        )
    }

    // =========================================================================
    // Test: single spritePalette → slot 0 (regression guard — slot 0 must still work)
    // =========================================================================

    @Test
    fun `single spritePalette declaration emits set_sprite_palette with slot 0`() {
        val gameIR =
            game("SinglePaletteTest") {
                    config {
                        cartridge = Cartridge.ROM_ONLY
                        romBanks = 2
                        target(GbcTarget.GBC_COMPATIBLE)
                    }
                    val hero by spritePalette {
                        color0(Color.WHITE)
                        color1(Color.rgb555(21, 21, 21))
                        color2(Color.rgb555(10, 10, 10))
                        color3(Color.BLACK)
                    }
                    val playScene =
                        scene("play") {
                            palette(hero)
                            enter {}
                        }
                    start = playScene
                }
                .build()

        val mainC = pipeline.generate(gameIR).files["main.c"] ?: error("main.c not generated")

        val allFilesSingle = pipeline.generate(gameIR).files
        val allSourceSingle = allFilesSingle.values.joinToString("\n")

        val enterBody =
            extractFunctionBodyForSlotTest(allSourceSingle, "void play_enter(void)")
                ?: error(
                    "Could not extract play_enter() body from generated files. " +
                        "Files generated: ${allFilesSingle.keys}. " +
                        "Does main.c contain 'play_enter': ${allFilesSingle["main.c"]?.contains("play_enter")}"
                )

        assertTrue(
            enterBody.contains("set_sprite_palette(0u, 1u,"),
            "Expected set_sprite_palette(0u, 1u, …) for solo hero palette (slot 0). play_enter body:\n$enterBody",
        )
    }
}
