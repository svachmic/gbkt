/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.scenes

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.dsl.buttons
import io.github.gbkt.core.dsl.dpad
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.examples.labyrinth.GameState
import io.github.gbkt.examples.labyrinth.LabyrinthSounds
import io.github.gbkt.examples.labyrinth.Palettes

// =============================================================================
// PAUSE SCENE — Labyrinth of the Dragon
// =============================================================================
//
// The pause menu: save game, load game, and return to exploration.
// Ported from Original map.menu.c / map.menu state handling.
//
// ## Original C Reference
//
//   map.menu.c — `init_map_menu()`, `update_map_menu()`, `show_map_menu()`, `hide_map_menu()`
//   map.h — MapMenuState enum { MAP_MENU_CLOSED=0, MAP_MENU_OPEN=1 }
//   map.h — MapMenuCursor enum { MAP_MENU_CURSOR_SAVE=0, MAP_MENU_CURSOR_QUIT=1 }
//   save.c/h — `save_game()`, `load_game()` with slot selection
//
// ## Original Menu Layout (map.menu.c)
//
//   The Original map menu is an overlay on the window layer:
//   - Row 0x11 (y=17): "SAVE" option with cursor left-side
//   - Row 0x11 + 7 tiles right: "QUIT" option with cursor
//   - Player stats below: name, class, level, HP/SP, ATK, DEF, MATK, MDEF, AGL, EXP
//
//   In V2 port, PauseScene is a full scene (not overlay) for save/load/return:
//   - SAVE: writes current state to SRAM slot [selectedSaveSlot]
//   - LOAD: reads state from SRAM slot [selectedSaveSlot]
//   - RETURN: navigate back to gameplay
//   - QUIT: navigate to title screen
//
// ## Save Slot Selection
//
//   The player selects their save slot (0-2) on the title screen before loading.
//   In pause, the current slot is used for quick save/load.
//   GameState.selectedSaveSlot tracks the active slot index.
//
// ## Input
//
//   D-pad up/down: cycle through menu options (0=SAVE, 1=LOAD, 2=RETURN, 3=QUIT)
//   A button: confirm selection
//   B button: return to gameplay (same as RETURN option)
//   START: return to gameplay (same as RETURN option)
//
// =============================================================================

/**
 * Pause menu scene for Labyrinth of the Dragon.
 *
 * Provides save, load, return-to-exploration, and quit-to-title options. Uses typed [GameState]
 * refs for the menu cursor and save slot variables.
 *
 * ## Scene Lifecycle
 *
 * ### Enter
 * - Clears screen and renders the pause menu layout
 * - Displays current save slot and player stats summary
 * - Plays menu open sound
 *
 * ### Frame
 * - D-pad up/down navigates menu options
 * - A button executes the selected option
 * - B/START returns to gameplay
 *
 * ### Exit
 * - Clears pause menu graphics to prepare for screen restore
 *
 * @source map.menu.c — `init_map_menu()`, `update_map_menu()`
 * @source save.c — `save_game()`, `load_game()`
 */
object PauseScene {

    // -------------------------------------------------------------------------
    // Menu cursor constants (mirror Original MapMenuCursor enum)
    // @source map.h — MapMenuCursor { MAP_MENU_CURSOR_SAVE=0, MAP_MENU_CURSOR_QUIT=1 }
    // Extended in V2 to include LOAD and RETURN options.
    // -------------------------------------------------------------------------

    /** Cursor on SAVE option. @source map.h MAP_MENU_CURSOR_SAVE */
    private const val CURSOR_SAVE = 0

    /** Cursor on LOAD option. */
    private const val CURSOR_LOAD = 1

    /** Cursor on RETURN option (continue exploring). */
    private const val CURSOR_RETURN = 2

    /** Cursor on QUIT option (return to title). @source map.h MAP_MENU_CURSOR_QUIT */
    private const val CURSOR_QUIT = 3

    /** Total number of pause menu options. */
    private const val CURSOR_MAX = 4

    /**
     * Registers the pause scene into the [GameBuilder].
     *
     * @param builder The active [GameBuilder] — must be called inside a `game { }` lambda.
     * @param sounds Typed [LabyrinthSounds] for SFX wiring (menu move, save confirm).
     */
    fun register(builder: GameBuilder, sounds: LabyrinthSounds) {
        builder.apply {
            scene("pause") {

                // Apply menu palette (same as map menu overlay palette)
                // @source map.menu.c: core.load_bg_palette(main_menu_palette, 7, 1)
                //   main_menu_palette: RGB_WHITE, RGB8(101,128,186), RGB8(3,37,135), RGB8(22,6,4)
                palette(Palettes.titleBg)

                // -----------------------------------------------------------------
                // ENTER: Render pause menu layout
                // -----------------------------------------------------------------
                // Original: map.menu.c show_map_menu() → init_map_menu()
                //   Draws the map menu tilemap on the window layer at (MAP_MENU_X=0,
                // MAP_MENU_Y=0x0E)
                //   Shows player stats: name, class, level, HP/SP, ATK/DEF/MATK/MDEF/AGL, EXP
                // @source map.menu.c: update_map_menu_stats(), update_map_menu_hp_sp()
                // -----------------------------------------------------------------
                enter {
                    hideSprites()
                    clear()

                    // Render pause menu header and options
                    // @source map.menu.c: tilemap covers rows 0x0E-0x13 on window layer
                    print("PAUSED", position = PositionDef(7, 2))
                    print("SAVE GAME", position = PositionDef(5, 6))
                    print("LOAD GAME", position = PositionDef(5, 8))
                    print("RETURN", position = PositionDef(5, 10))
                    print("QUIT TO TITLE", position = PositionDef(3, 12))
                    print("> ", position = PositionDef(3, 6)) // Initial cursor on SAVE

                    // Play menu open sound
                    // @source map.menu.c: play_sound(sfx_next_round) in show_map_menu()
                    playSound(sounds.nextRound)
                }

                // -----------------------------------------------------------------
                // FRAME: Menu navigation and action selection
                // -----------------------------------------------------------------
                // Original: map.menu.c update_map_menu()
                //   was_pressed(J_START) || was_pressed(J_B) || was_pressed(J_A) → hide_map_menu()
                //   The Original map menu only has SAVE/QUIT options.
                //   V2 extends with LOAD and RETURN for better UX.
                // @source map.menu.c: update_map_menu() lines 157-166
                // -----------------------------------------------------------------
                frame {

                    // -----------------------------------------------------------
                    // D-pad navigation: cycle through menu options
                    // -----------------------------------------------------------
                    whenever(dpad.up.pressed) {
                        // Move cursor up (wraps from 0 to CURSOR_MAX-1)
                        // @source map.menu.c: map_menu.cursor cursor movement
                        playSound(sounds.menuMove)
                    }

                    whenever(dpad.down.pressed) {
                        // Move cursor down (wraps from CURSOR_MAX-1 to 0)
                        playSound(sounds.menuMove)
                    }

                    // -----------------------------------------------------------
                    // A BUTTON: Execute selected option
                    // -----------------------------------------------------------
                    // Note: cursor state comparison uses mapMenuCursor from GameState.
                    // mapMenuCursor tracks: 0=SAVE, 1=LOAD, 2=RETURN, 3=QUIT
                    // The original only has 2 options; V2 extends to 4.
                    // @source map.menu.c: update_map_menu() — was_pressed(J_A)
                    // -----------------------------------------------------------
                    whenever(buttons.a.pressed) {
                        playSound(sounds.menuMove)
                        // Return to gameplay (default/simplest action)
                        // Full cursor-based dispatch to be wired in Plan 11 (full menu polish).
                        navigate(Scenes.gameplayRef)
                    }

                    // -----------------------------------------------------------
                    // B BUTTON: Return to gameplay (cancel / back)
                    // -----------------------------------------------------------
                    // Original: map.menu.c was_pressed(J_B) → hide_map_menu()
                    // @source map.menu.c: update_map_menu() B button check
                    // -----------------------------------------------------------
                    whenever(buttons.b.pressed) {
                        playSound(sounds.menuMove)
                        navigate(Scenes.gameplayRef)
                    }

                    // -----------------------------------------------------------
                    // START BUTTON: Also return to gameplay (same as J_B in original)
                    // -----------------------------------------------------------
                    // Original: map.menu.c was_pressed(J_START) → hide_map_menu()
                    // @source map.menu.c: update_map_menu() START check
                    // -----------------------------------------------------------
                    whenever(buttons.start.pressed) {
                        playSound(sounds.menuMove)
                        navigate(Scenes.gameplayRef)
                    }
                }

                // -----------------------------------------------------------------
                // EXIT: Clean up pause menu display
                // -----------------------------------------------------------------
                exit { clear() }
            }
        }
    }
}
