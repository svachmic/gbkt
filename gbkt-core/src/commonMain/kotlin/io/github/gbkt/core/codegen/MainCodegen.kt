/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.ir.GBVar
import io.github.gbkt.core.ir.PaletteType
import io.github.gbkt.core.ir.i16
import io.github.gbkt.core.ir.i8
import io.github.gbkt.core.ir.u16
import io.github.gbkt.core.ir.u8

// =============================================================================
// MAIN FUNCTION CODE GENERATION
// =============================================================================

internal fun CodeGenerator.generatePaletteFadeHelper() {
    line("// === GBC Color Interpolation ===")
    block("UINT16 _interpolate_color(UINT16 from, UINT16 to, UINT8 progress)") {
        line("// Extract RGB555 components")
        line("UINT8 r1 = from & 0x1F;")
        line("UINT8 g1 = (from >> 5) & 0x1F;")
        line("UINT8 b1 = (from >> 10) & 0x1F;")
        line("UINT8 r2 = to & 0x1F;")
        line("UINT8 g2 = (to >> 5) & 0x1F;")
        line("UINT8 b2 = (to >> 10) & 0x1F;")
        line()
        line("// Interpolate each component (progress 0-255)")
        line("UINT8 r = r1 + (((INT16)(r2 - r1) * progress) >> 8);")
        line("UINT8 g = g1 + (((INT16)(g2 - g1) * progress) >> 8);")
        line("UINT8 b = b1 + (((INT16)(b2 - b1) * progress) >> 8);")
        line()
        line("return (b << 10) | (g << 5) | r;")
    }
    line()
}

internal fun CodeGenerator.generateMain() {
    line("// === Main ===")
    line()

    // Helper function for palette fade (if needed)
    if (game.config.gbcSupport && game.palettes.isNotEmpty()) {
        generatePaletteFadeHelper()
    }

    // Pathfinding helper functions
    if (game.navGrids.isNotEmpty()) {
        generatePathfindingHelpers()
    }

    // Init
    block("void init(void)") {
        line("DISPLAY_OFF;")
        line()

        // Initialize GBC palettes
        if (game.config.gbcSupport && game.palettes.isNotEmpty()) {
            line("// Initialize GBC palettes")
            line("if (_cpu == CGB_TYPE) {")
            indent++

            // Set sprite palettes
            val spritePalettes = game.palettes.filter { it.type == PaletteType.SPRITE }
            for (palette in spritePalettes) {
                line("set_sprite_palette(${palette.slot}, 1, ${palette.name}_pal);")
            }

            // Set background palettes
            val bkgPalettes = game.palettes.filter { it.type == PaletteType.BACKGROUND }
            for (palette in bkgPalettes) {
                line("set_bkg_palette(${palette.slot}, 1, ${palette.name}_pal);")
            }

            indent--
            line("}")
            line()
        }

        // Load sprite tiles
        if (game.tileData.isNotEmpty()) {
            line("// Load sprite tiles")
            var tileOffset = 0
            for (data in game.tileData) {
                line("set_sprite_data($tileOffset, ${data.tileCount}, ${data.name}_tiles);")
                tileOffset += data.tileCount
            }
            line()

            // Set initial sprite tiles
            for ((index, sprite) in game.sprites.withIndex()) {
                val tileData = game.tileData.find { it.name == sprite.name.replace("sprite_", "") }
                if (tileData != null) {
                    line("set_sprite_tile($index, 0);  // ${sprite.name}")
                }
            }
            line()

            // Set sprite palette attributes (GBC)
            if (game.config.gbcSupport) {
                val spritesWithPalettes = game.sprites.filter { it.hasPalette }
                if (spritesWithPalettes.isNotEmpty()) {
                    line("// Set sprite palette attributes (GBC)")
                    line("if (_cpu == CGB_TYPE) {")
                    indent++
                    for (sprite in spritesWithPalettes) {
                        line(
                            "set_sprite_prop(${sprite.oamSlot}, ${sprite.paletteIndex} & 0x07);  // ${sprite.name}"
                        )
                    }
                    indent--
                    line("}")
                    line()
                }
            }
        }

        // Load background tiles and map data
        if (game.mapData.isNotEmpty()) {
            line("// Load background tiles")
            // Find background tilesets (tiles ending with _tileset)
            val bkgTilesets = game.tileData.filter { it.name.endsWith("_tileset") }
            var bkgTileOffset = 0
            for (data in bkgTilesets) {
                line("set_bkg_data($bkgTileOffset, ${data.tileCount}, ${data.name}_tiles);")
                bkgTileOffset += data.tileCount
            }
            line()

            // Set initial map tiles
            line("// Set initial background map")
            for (map in game.mapData) {
                line(
                    "set_bkg_tiles(0, 0, ${map.name.uppercase()}_WIDTH, ${map.name.uppercase()}_HEIGHT, ${map.name}_map);"
                )
            }
            line()
        }

        // Initialize variables
        for (v in game.variables) {
            val initial =
                when (v.type) {
                    GBVar.VarType.U8 -> (v.value as? u8)?.raw ?: 0
                    GBVar.VarType.U16 -> (v.value as? u16)?.raw ?: 0
                    GBVar.VarType.I8 -> (v.value as? i8)?.raw ?: 0
                    GBVar.VarType.I16 -> (v.value as? i16)?.raw ?: 0
                }
            // Use 'u' suffix only for unsigned types
            val suffix = if (v.type == GBVar.VarType.U8 || v.type == GBVar.VarType.U16) "u" else ""
            line("${v.name} = ${initial}$suffix;")
        }
        line()

        line("_frame_count = 0;")
        line("_current_scene = SCENE_${game.startScene.uppercase()};")
        line("_next_scene = _current_scene;")
        line("_scene_changed = 1;")

        // Initialize sound system
        if (game.soundEffects.isNotEmpty() || game.music.isNotEmpty()) {
            line()
            line("// Initialize sound")
            line("NR52_REG = 0x80;  // Sound on")
            line("NR51_REG = 0xFF;  // All channels to both speakers")
            line("NR50_REG = 0x77;  // Max volume")
        }

        if (game.music.isNotEmpty()) {
            line("_music_playing = 0;")
        }

        line()
        line("DISPLAY_ON;")
    }
    line()

    // Scene update
    block("void update_scene(void)") {
        block("if (_scene_changed)") {
            // Exit current scene
            block("switch (_current_scene)") {
                for ((name, scene) in game.scenes) {
                    if (scene.onExit.isNotEmpty()) {
                        line("case SCENE_${name.uppercase()}: ${name}_exit(); break;")
                    }
                }
                line("default: break;")
            }
            line()

            line("_current_scene = _next_scene;")
            line("_scene_changed = 0;")
            line()

            // Enter new scene
            block("switch (_current_scene)") {
                for ((name, scene) in game.scenes) {
                    if (scene.onEnter.isNotEmpty()) {
                        line("case SCENE_${name.uppercase()}: ${name}_enter(); break;")
                    }
                }
                line("default: break;")
            }
        }
        line()

        // Frame update
        block("switch (_current_scene)") {
            for ((name, scene) in game.scenes) {
                if (scene.onFrame.isNotEmpty()) {
                    line("case SCENE_${name.uppercase()}: ${name}_frame(); break;")
                }
            }
            line("default: break;")
        }
    }
    line()

    // Main loop
    block("void main(void)") {
        line("init();")
        line()
        block("while (1)") {
            line("_joypad_prev = _joypad;")
            line("_joypad = joypad();")
            line()

            // Input buffer updates - must happen right after joypad read
            if (game.inputBuffers.isNotEmpty()) {
                line("// Input buffer updates")
                for (buffer in game.inputBuffers) {
                    // Decrement buffer if > 0
                    line("if (${buffer.name} > 0) ${buffer.name}--;")
                    // Fill buffer on button press (just pressed this frame)
                    line(
                        "if ((_joypad & 0x${buffer.buttonMask.toString(16)}) && !(_joypad_prev & 0x${buffer.buttonMask.toString(16)})) ${buffer.name} = ${buffer.windowFrames};"
                    )
                }
                line()
            }

            line("update_scene();")

            // Animation updates - advance all sprite animations
            val animatedSprites = game.sprites.filter { it.hasAnimations }
            if (animatedSprites.isNotEmpty()) {
                line()
                line("update_animations();")
            }

            // Music tick - must be called every frame
            if (game.music.isNotEmpty()) {
                line()
                line("if (_music_playing) hUGE_dosound();")
                line()
                line("// Music fade update")
                line("if (_music_fade_timer > 0) {")
                indent++
                line("_music_fade_timer--;")
                line(
                    "UINT8 vol = (_music_fade_start_vol * _music_fade_timer) / _music_fade_duration;"
                )
                line("NR50_REG = (vol << 4) | vol;  // Set both L/R channels")
                line("if (_music_fade_timer == 0) {")
                indent++
                line("_music_playing = 0;")
                line(
                    "NR12_REG = 0; NR22_REG = 0; NR30_REG = 0; NR42_REG = 0;  // Stop all channels"
                )
                line("NR50_REG = 0x77;  // Restore max volume for next music")
                indent--
                line("}")
                indent--
                line("}")
            }

            // Transition update - must be called every frame
            if (game.camera != null) {
                line()
                line("_transition_update();")
                if (composedTransitionSequences.isNotEmpty()) {
                    line("_trans_seq_update();")
                }
            }

            // Tween update - must be called every frame
            line()
            line("update_tweens();")

            line()
            line("_frame_count++;")
            line("vsync();")
        }
    }
}
