/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.scenes

import io.github.gbkt.core.SceneRef
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.input.buttons
import io.github.gbkt.core.input.dpad
import io.github.gbkt.core.print
import io.github.gbkt.core.screen
import io.github.gbkt.examples.labyrinth.GameState
import io.github.gbkt.examples.labyrinth.Sounds

/**
 * Title Screen
 *
 * The main menu shown when the game starts. Displays game title and menu options with cursor-based
 * selection between New Game, Continue, and Options.
 *
 * Menu Options:
 * - 0: New Game - Start a new adventure
 * - 1: Continue - Load a saved game (goes to hero select for now)
 * - 2: Options - Audio settings
 */
@Suppress("LongMethod")
fun GameBuilder.initTitleScene(
    state: GameState,
    sounds: Sounds,
    heroSelect: SceneRef,
    settings: SceneRef,
): SceneRef =
    scene("title") {
        enter {
            screen.clear()
            // Reset cursor position
            state.titleMenuCursor set 0

            // Draw title
            print("LABYRINTH OF") at (3 to 2)
            print("THE DRAGON") at (4 to 4)
            print("") at (0 to 7)

            // Draw menu options with cursor on first option
            print(">NEW GAME") at (5 to 9)
            print(" CONTINUE") at (5 to 11)
            print(" OPTIONS") at (5 to 13)

            // Draw prompt
            print("") at (0 to 15)
            print("  A: Select") at (3 to 17)
        }

        every.frame {
            // =========================================================
            // MENU NAVIGATION
            // =========================================================

            // Up: Move cursor up
            whenever(dpad.up.pressed) {
                whenever(state.titleMenuCursor isAbove 0) {
                    state.titleMenuCursor -= 1
                    sounds.menuMove.play()
                    // Update cursor display
                    whenever(state.titleMenuCursor isEqualTo 0) {
                        print(">NEW GAME") at (5 to 9)
                        print(" CONTINUE") at (5 to 11)
                        print(" OPTIONS") at (5 to 13)
                    }
                    whenever(state.titleMenuCursor isEqualTo 1) {
                        print(" NEW GAME") at (5 to 9)
                        print(">CONTINUE") at (5 to 11)
                        print(" OPTIONS") at (5 to 13)
                    }
                }
            }

            // Down: Move cursor down
            whenever(dpad.down.pressed) {
                whenever(state.titleMenuCursor isBelow 2) {
                    state.titleMenuCursor += 1
                    sounds.menuMove.play()
                    // Update cursor display
                    whenever(state.titleMenuCursor isEqualTo 1) {
                        print(" NEW GAME") at (5 to 9)
                        print(">CONTINUE") at (5 to 11)
                        print(" OPTIONS") at (5 to 13)
                    }
                    whenever(state.titleMenuCursor isEqualTo 2) {
                        print(" NEW GAME") at (5 to 9)
                        print(" CONTINUE") at (5 to 11)
                        print(">OPTIONS") at (5 to 13)
                    }
                }
            }

            // =========================================================
            // MENU SELECTION
            // =========================================================

            // A button to select
            whenever(buttons.a.pressed) {
                sounds.menuSelect.play()
                // Option 0: New Game - start fresh
                whenever(state.titleMenuCursor isEqualTo 0) { scene(heroSelect) }
                // Option 1: Continue - would load save, but for now goes to hero select
                whenever(state.titleMenuCursor isEqualTo 1) { scene(heroSelect) }
                // Option 2: Options - go to settings
                whenever(state.titleMenuCursor isEqualTo 2) { scene(settings) }
            }

            // Start button starts new game
            whenever(buttons.start.pressed) {
                sounds.menuSelect.play()
                scene(heroSelect)
            }
        }
    }
