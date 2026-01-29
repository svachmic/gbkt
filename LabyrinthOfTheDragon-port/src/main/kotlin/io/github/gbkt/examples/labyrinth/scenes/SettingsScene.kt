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
import io.github.gbkt.examples.labyrinth.Sounds

/**
 * Settings Scene
 *
 * Audio settings menu with volume and SFX controls.
 *
 * Menu Options:
 * - Master Volume: 0-7 (Game Boy hardware levels)
 * - SFX: On/Off toggle
 * - Back: Return to title
 *
 * Controls:
 * - Up/Down: Navigate menu
 * - Left/Right: Adjust volume or toggle SFX
 * - A: Select (for Back option)
 * - B: Return to title
 */

// Settings menu string constants
private const val MENU_VOLUME_SELECTED = ">VOLUME:"
private const val MENU_VOLUME_UNSELECTED = " VOLUME:"
private const val MENU_SFX_SELECTED = ">SFX:"
private const val MENU_SFX_UNSELECTED = " SFX:"
private const val MENU_BACK_SELECTED = ">BACK"
private const val MENU_BACK_UNSELECTED = " BACK"

@Suppress("LongMethod", "MagicNumber")
fun GameBuilder.initSettingsScene(
    settingsState: SettingsSceneState,
    sounds: Sounds,
    title: SceneRef,
): SceneRef =
    scene("settings") {
        enter {
            screen.clear()
            // Reset cursor to first option
            settingsState.settingsCursor set 0

            // Draw settings header
            print("SETTINGS") at (5 to 2)
            print("") at (0 to 4)

            // Draw menu options with current values
            print(MENU_VOLUME_SELECTED) at (2 to 7)
            print(MENU_SFX_UNSELECTED) at (2 to 9)
            print(MENU_BACK_UNSELECTED) at (2 to 12)

            // Draw volume bar (will be updated based on current value)
            print("[=======]") at (10 to 7)
            // Draw SFX status
            print("ON") at (8 to 9)
        }

        every.frame {
            // =========================================================
            // MENU NAVIGATION
            // =========================================================

            // Up: Move cursor up
            whenever(dpad.up.pressed) {
                whenever(settingsState.settingsCursor isAbove 0) {
                    settingsState.settingsCursor -= 1
                    sounds.menuMove.play()
                    // Update cursor display
                    whenever(settingsState.settingsCursor isEqualTo 0) {
                        print(MENU_VOLUME_SELECTED) at (2 to 7)
                        print(MENU_SFX_UNSELECTED) at (2 to 9)
                        print(MENU_BACK_UNSELECTED) at (2 to 12)
                    }
                    whenever(settingsState.settingsCursor isEqualTo 1) {
                        print(MENU_VOLUME_UNSELECTED) at (2 to 7)
                        print(MENU_SFX_SELECTED) at (2 to 9)
                        print(MENU_BACK_UNSELECTED) at (2 to 12)
                    }
                }
            }

            // Down: Move cursor down
            whenever(dpad.down.pressed) {
                whenever(settingsState.settingsCursor isBelow 2) {
                    settingsState.settingsCursor += 1
                    sounds.menuMove.play()
                    // Update cursor display
                    whenever(settingsState.settingsCursor isEqualTo 1) {
                        print(MENU_VOLUME_UNSELECTED) at (2 to 7)
                        print(MENU_SFX_SELECTED) at (2 to 9)
                        print(MENU_BACK_UNSELECTED) at (2 to 12)
                    }
                    whenever(settingsState.settingsCursor isEqualTo 2) {
                        print(MENU_VOLUME_UNSELECTED) at (2 to 7)
                        print(MENU_SFX_UNSELECTED) at (2 to 9)
                        print(MENU_BACK_SELECTED) at (2 to 12)
                    }
                }
            }

            // =========================================================
            // VOLUME ADJUSTMENT (cursor == 0)
            // =========================================================
            whenever(settingsState.settingsCursor isEqualTo 0) {
                // Left: Decrease volume
                whenever(dpad.left.pressed) {
                    whenever(settingsState.masterVolume isAbove 0) {
                        settingsState.masterVolume -= 1
                        sounds.menuMove.play()
                        // Redraw volume bar based on level
                        // Simple visual: show "=" chars proportional to volume
                        whenever(settingsState.masterVolume isEqualTo 0) {
                            print("[       ]") at (10 to 7)
                        }
                        whenever(settingsState.masterVolume isEqualTo 1) {
                            print("[=      ]") at (10 to 7)
                        }
                        whenever(settingsState.masterVolume isEqualTo 2) {
                            print("[==     ]") at (10 to 7)
                        }
                        whenever(settingsState.masterVolume isEqualTo 3) {
                            print("[===    ]") at (10 to 7)
                        }
                        whenever(settingsState.masterVolume isEqualTo 4) {
                            print("[====   ]") at (10 to 7)
                        }
                        whenever(settingsState.masterVolume isEqualTo 5) {
                            print("[=====  ]") at (10 to 7)
                        }
                        whenever(settingsState.masterVolume isEqualTo 6) {
                            print("[====== ]") at (10 to 7)
                        }
                    }
                }

                // Right: Increase volume
                whenever(dpad.right.pressed) {
                    whenever(settingsState.masterVolume isBelow 7) {
                        settingsState.masterVolume += 1
                        sounds.menuMove.play()
                        // Redraw volume bar
                        whenever(settingsState.masterVolume isEqualTo 1) {
                            print("[=      ]") at (10 to 7)
                        }
                        whenever(settingsState.masterVolume isEqualTo 2) {
                            print("[==     ]") at (10 to 7)
                        }
                        whenever(settingsState.masterVolume isEqualTo 3) {
                            print("[===    ]") at (10 to 7)
                        }
                        whenever(settingsState.masterVolume isEqualTo 4) {
                            print("[====   ]") at (10 to 7)
                        }
                        whenever(settingsState.masterVolume isEqualTo 5) {
                            print("[=====  ]") at (10 to 7)
                        }
                        whenever(settingsState.masterVolume isEqualTo 6) {
                            print("[====== ]") at (10 to 7)
                        }
                        whenever(settingsState.masterVolume isEqualTo 7) {
                            print("[=======]") at (10 to 7)
                        }
                    }
                }
            }

            // =========================================================
            // SFX TOGGLE (cursor == 1)
            // =========================================================
            whenever(settingsState.settingsCursor isEqualTo 1) {
                // Left/Right or A to toggle SFX
                whenever(dpad.left.pressed) {
                    whenever(settingsState.sfxEnabled isEqualTo 1) {
                        settingsState.sfxEnabled set 0
                        print("OFF") at (8 to 9)
                    }
                }
                whenever(dpad.right.pressed) {
                    whenever(settingsState.sfxEnabled isEqualTo 0) {
                        settingsState.sfxEnabled set 1
                        sounds.menuMove.play()
                        print("ON ") at (8 to 9)
                    }
                }
                whenever(buttons.a.pressed) {
                    // Toggle SFX
                    whenever(settingsState.sfxEnabled isEqualTo 0) {
                        settingsState.sfxEnabled set 1
                        sounds.menuMove.play()
                        print("ON ") at (8 to 9)
                    }
                    whenever(settingsState.sfxEnabled isEqualTo 1) {
                        settingsState.sfxEnabled set 0
                        print("OFF") at (8 to 9)
                    }
                }
            }

            // =========================================================
            // BACK SELECTION (cursor == 2)
            // =========================================================
            whenever(settingsState.settingsCursor isEqualTo 2) {
                whenever(buttons.a.pressed) {
                    sounds.menuSelect.play()
                    scene(title)
                }
            }

            // =========================================================
            // B BUTTON: Return to title from anywhere
            // =========================================================
            whenever(buttons.b.pressed) {
                sounds.menuCancel.play()
                scene(title)
            }
        }
    }
