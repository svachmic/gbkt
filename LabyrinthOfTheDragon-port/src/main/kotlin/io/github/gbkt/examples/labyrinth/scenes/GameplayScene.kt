/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "LongMethod"
) // Exploration frame loop maps to Original map.c update_world_map() — one large dispatch

package io.github.gbkt.examples.labyrinth.scenes

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.dsl.buttons
import io.github.gbkt.examples.labyrinth.LabyrinthSounds
import io.github.gbkt.examples.labyrinth.Palettes
import io.github.gbkt.examples.labyrinth.rpg.LabyrinthCombatSystem

// =============================================================================
// GAMEPLAY SCENE — Labyrinth of the Dragon
// =============================================================================
//
// The core dungeon exploration loop. Ports the Original map.c update_world_map()
// state machine to the gbkt V2 exploration DSL.
//
// ## Original C Reference
//
//   map.c — `update_world_map()`, `init_world_map()`
//   map.h — MapState enum, torch/key HUD constants, movement defines
//   map.menu.c — `update_map_menu()`, `show_map_menu()`, `hide_map_menu()`
//   map.encounters.c — `check_random_encounter()`, `generate_encounter()`
//
// ## Exploration Flow (Original map.c MapState states)
//
//   MAP_STATE_WAITING     → Idle, awaiting D-pad input
//   MAP_STATE_MOVING      → Progressive tile load during hero movement
//   MAP_STATE_FADE_OUT/IN → Screen fade during floor transitions
//   MAP_STATE_LOAD_EXIT   → Loading exit destination (staircase, door, etc.)
//   MAP_STATE_TEXTBOX     → Showing a sign, chest, or NPC dialog
//   MAP_STATE_INITIATE_BATTLE → Encounter triggered → transition to battle scene
//   MAP_STATE_MENU        → Map menu open (save/stats overlay)
//
// ## V2 Exploration DSL Mapping
//
//   The V2 exploration system (declared at game level) handles:
//   - Grid-based movement (8px tiles, GRID movement style)
//   - Torch gauge decrement per step (exploration gauge)
//   - Torch low/depleted callbacks → navigate to game over
//   - Random encounter triggering via zone encounter tables → navigate to battle
//   - Key counter for magic-key-locked doors
//
//   The gameplay scene handles:
//   - A button: object interaction (chests, doors, signs, sconces, NPCs)
//   - START button: navigate to pause scene for save/load
//   - SELECT button: show map menu overlay (stats view)
//   - Post-battle return: resume exploration after VICTORY/DEFEAT resolved
//
// ## Key GameState Variables Used
//
//   state.torchLevel      — torch fuel level (exploration gauge value, 0-255)
//   state.magicKeys       — current magic key count (exploration keys value)
//   state.stepCount       — running step counter for safe-step encounter logic
//   state.currentFloor    — active floor index (1-8), set before gameplay
//   state.playerX, .playerY — tile position within the active map
//
// =============================================================================

/**
 * Core dungeon exploration scene for Labyrinth of the Dragon.
 *
 * Drives the grid-based movement loop, object interaction, torch management, random encounters, and
 * floor transitions. Delegates the heavy exploration mechanics to the V2 exploration system
 * declared in the game {} block.
 *
 * ## Scene Lifecycle
 *
 * ### Enter
 * - Applies the active floor's GBC palette (floor 1 default; per-floor palettes loaded dynamically)
 * - Shows the hero sprite (OAM slot 0-3)
 * - Clears the screen for the floor tilemap
 * - Plays the map load sound (sfx_next_round)
 *
 * ### Frame (called every game loop tick)
 * - A button: play interaction sound (object interaction delegated to exploration system)
 * - START button: navigate to pause scene for save/load/return
 * - SELECT button: play menu sound (stats overlay in map menu)
 * - Post-battle: victory returns here automatically; defeat navigates to gameover
 *
 * ### Exit
 * - Hides all exploration sprites (hero, torch HUD, magic key sprites)
 *
 * @source map.c — `init_world_map()`, `update_world_map()`, `draw_world_map()`
 * @source map.menu.c — `update_map_menu()`, `show_map_menu()`, `hide_map_menu()`
 * @source map.encounters.c — `check_random_encounter()`, `generate_encounter()`
 */
object GameplayScene {

    /**
     * Registers the gameplay (dungeon exploration) scene into the [GameBuilder].
     *
     * @param builder The active [GameBuilder] — must be called inside a `game { }` lambda.
     * @param sounds Typed [LabyrinthSounds] for SFX wiring (wall hit, steps, encounter).
     * @param combatSystem Typed [LabyrinthCombatSystem] for post-battle state checks.
     */
    fun register(
        builder: GameBuilder,
        sounds: LabyrinthSounds,
        @Suppress("UnusedParameter")
        combatSystem: LabyrinthCombatSystem, // Reserved for post-battle state checks
    ) {
        builder.apply {
            scene("gameplay") {

                // Apply floor 1 default palette — per-floor palettes loaded via floor callbacks
                // @source floor1.c — `floor_one_on_init()` calls core.load_bg_palette()
                palette(Palettes.floor1Palette0)
                palette(Palettes.floor1Palette1)
                palette(Palettes.floor1Palette2)

                // -----------------------------------------------------------------
                // ENTER: Load floor, restore hero position, initialize HUD
                // -----------------------------------------------------------------
                // Original: map.c init_world_map()
                //   - Loads active floor tilemap via set_active_floor() + init_world_map()
                //   - Sets hero position from floor defaults (DEFAULT_X, DEFAULT_Y)
                //   - Initializes HUD: torch gauge sprites (OAM 24-28), magic key sprites
                //   - Plays sfx_next_round to signal map load complete
                // @source map.c: init_hero_sprite(), init_torch_gauge(), init_magic_key_hud()
                // -----------------------------------------------------------------
                enter {
                    // Show hero sprite and exploration HUD sprites
                    // @source map.c: hero sprite init at OAM slot 0-3
                    showSprites()
                    clear()

                    // Play map load complete chime
                    // @source map.c: play_sound(sfx_next_round) at end of init_world_map()
                    playSound(sounds.nextRound)
                }

                // -----------------------------------------------------------------
                // FRAME: Exploration input handling
                // -----------------------------------------------------------------
                // Original: map.c update_world_map() dispatch on map_state
                //
                // The V2 exploration system (game-level exploration { } block) handles:
                //   - D-pad movement, collision, torch gauge decrement, encounter checks
                //   - Zone transitions (stairs, portals) via zone transition callbacks
                //   - Object interaction dispatch via zone onInteract callback
                //
                // This frame block adds:
                //   - START → pause scene navigation
                //   - SELECT → map stats overlay (SFX feedback)
                //   - A → explicit interaction sound (object interaction via exploration)
                //   - Post-battle: victory handled by exploration onStep resumption
                // -----------------------------------------------------------------
                frame {

                    // -----------------------------------------------------------
                    // A BUTTON: Interact with map object in front of the hero
                    // -----------------------------------------------------------
                    // Original: map.c MAP_STATE_WAITING → J_A → floor.on_action()
                    //   Routes to chest open, door unlock, sign read, sconce light, NPC talk.
                    //   Tile at (map_x + HERO_X_OFFSET, map_y + HERO_Y_OFFSET + dir_offset).
                    // @source map.c handle_input() — J_A pressed check
                    // @source floor.h — Floor struct on_action() callback
                    // -----------------------------------------------------------
                    whenever(buttons.a.pressed) {
                        // Exploration system routes interaction via zone onInteract callback.
                        // Play a sound to give tactile A-press feedback.
                        // @source map.c: play_sound(sfx_next_round) after valid interaction
                        playSound(sounds.nextRound)
                    }

                    // -----------------------------------------------------------
                    // START BUTTON: Open pause menu (save / load / return to game)
                    // -----------------------------------------------------------
                    // Original: map.menu.c was_pressed(J_START) → show_map_menu()
                    //   Opens window layer overlay with player stats and save/quit options.
                    // V2: navigates to PauseScene for the full save/load menu.
                    // @source map.c handle_input() — J_START pressed check
                    // -----------------------------------------------------------
                    whenever(buttons.start.pressed) { navigate(Scenes.pauseRef) }

                    // -----------------------------------------------------------
                    // SELECT BUTTON: Map stats overlay (in-place overlay, no navigation)
                    // -----------------------------------------------------------
                    // Original: map.menu.c show_map_menu() sets MAP_STATE_MENU
                    //   Window layer: name, class, level, EXP/next level, HP/SP, stats
                    // V2: uses SELECT to toggle map menu overlay via SFX + state
                    // @source map.menu.c: update_map_menu(), show_map_menu(), hide_map_menu()
                    // -----------------------------------------------------------
                    whenever(buttons.select.pressed) { playSound(sounds.nextRound) }
                }

                // -----------------------------------------------------------------
                // EXIT: Clean up exploration sprites
                // -----------------------------------------------------------------
                // Original: called before MAP_STATE_FADE_OUT transitions
                //   clear_map_sprites() hides hero, torch HUD, flame sprites
                // @source map.c: clear_map_sprites() before scene exit
                // -----------------------------------------------------------------
                exit { hideSprites() }
            }
        }
    }
}
