/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.scenes

import io.github.gbkt.core.SaveDataHandle
import io.github.gbkt.core.SceneRef
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.input.buttons
import io.github.gbkt.core.input.dpad
import io.github.gbkt.core.print
import io.github.gbkt.core.rpg.initPartyFromClass
import io.github.gbkt.core.screen
import io.github.gbkt.examples.labyrinth.GameConfig
import io.github.gbkt.examples.labyrinth.GameState

/**
 * Pause Menu Scene
 *
 * In-game menu for saving, loading, and quitting. Uses sub-states for menu navigation:
 * - 0 = Main menu (Resume, Save, Load, Quit)
 * - 1 = Save slot selection
 * - 2 = Load slot selection
 * - 3 = Confirmation dialog
 */
@Suppress("LongMethod")
fun GameBuilder.initPauseScene(
    state: GameState,
    saveData: SaveDataHandle,
    title: SceneRef,
    gameplay: SceneRef,
): SceneRef =
    scene("pause") {
        enter {
            // Reset pause menu state
            state.pauseMenuCursor set 0
            state.pauseMenuState set 0

            // Draw pause menu UI
            screen.clear()
            print("PAUSED") at (7 to 2)
            print("") at (0 to 4)
            print(">RESUME") at (5 to 6)
            print(" SAVE") at (5 to 8)
            print(" LOAD") at (5 to 10)
            print(" QUIT") at (5 to 12)
        }

        every.frame {
            // =========================================================
            // MAIN PAUSE MENU (pauseMenuState == STATE_MAIN)
            // =========================================================
            whenever(state.pauseMenuState isEqualTo GameConfig.PAUSE_STATE_MAIN) {
                // Navigate up
                whenever(dpad.up.pressed) {
                    whenever(state.pauseMenuCursor isAbove 0) { state.pauseMenuCursor -= 1 }
                }
                // Navigate down
                whenever(dpad.down.pressed) {
                    whenever(state.pauseMenuCursor isBelow GameConfig.PAUSE_OPTIONS - 1) {
                        state.pauseMenuCursor += 1
                    }
                }

                // A button: select option
                whenever(buttons.a.pressed) {
                    // Option RESUME: return to gameplay
                    whenever(state.pauseMenuCursor isEqualTo GameConfig.PAUSE_RESUME) {
                        scene(gameplay)
                    }
                    // Option SAVE: go to save slot selection
                    whenever(state.pauseMenuCursor isEqualTo GameConfig.PAUSE_SAVE) {
                        state.pauseMenuState set GameConfig.PAUSE_STATE_SAVE
                        state.saveSlotCursor set 0
                    }
                    // Option LOAD: go to load slot selection
                    whenever(state.pauseMenuCursor isEqualTo GameConfig.PAUSE_LOAD) {
                        state.pauseMenuState set GameConfig.PAUSE_STATE_LOAD
                        state.saveSlotCursor set 0
                    }
                    // Option QUIT: return to title
                    whenever(state.pauseMenuCursor isEqualTo GameConfig.PAUSE_QUIT) { scene(title) }
                }

                // B button or START: quick resume
                whenever(buttons.b.pressed) { scene(gameplay) }
                whenever(buttons.start.pressed) { scene(gameplay) }
            }

            // =========================================================
            // SAVE SLOT SELECTION (pauseMenuState == STATE_SAVE)
            // =========================================================
            whenever(state.pauseMenuState isEqualTo GameConfig.PAUSE_STATE_SAVE) {
                // Navigate slots
                whenever(dpad.up.pressed) {
                    whenever(state.saveSlotCursor isAbove 0) { state.saveSlotCursor -= 1 }
                }
                whenever(dpad.down.pressed) {
                    whenever(state.saveSlotCursor isBelow GameConfig.SAVE_SLOTS - 1) {
                        state.saveSlotCursor += 1
                    }
                }

                // A button: save to selected slot
                whenever(buttons.a.pressed) {
                    // Save game to selected slot
                    saveData.save(state.saveSlotCursor)
                    // Show confirmation and return to main menu
                    state.pauseMenuState set GameConfig.PAUSE_STATE_MAIN
                }

                // B button: back to main menu
                whenever(buttons.b.pressed) { state.pauseMenuState set GameConfig.PAUSE_STATE_MAIN }
            }

            // =========================================================
            // LOAD SLOT SELECTION (pauseMenuState == STATE_LOAD)
            // =========================================================
            whenever(state.pauseMenuState isEqualTo GameConfig.PAUSE_STATE_LOAD) {
                // Navigate slots
                whenever(dpad.up.pressed) {
                    whenever(state.saveSlotCursor isAbove 0) { state.saveSlotCursor -= 1 }
                }
                whenever(dpad.down.pressed) {
                    whenever(state.saveSlotCursor isBelow GameConfig.SAVE_SLOTS - 1) {
                        state.saveSlotCursor += 1
                    }
                }

                // A button: load from selected slot
                whenever(buttons.a.pressed) {
                    // Load game from selected slot
                    saveData.load(state.saveSlotCursor)
                    // Initialize party from loaded class
                    initPartyFromClass(state.selectedClass)
                    // Transition to gameplay
                    scene(gameplay)
                }

                // B button: back to main menu
                whenever(buttons.b.pressed) { state.pauseMenuState set GameConfig.PAUSE_STATE_MAIN }
            }
        }
    }
