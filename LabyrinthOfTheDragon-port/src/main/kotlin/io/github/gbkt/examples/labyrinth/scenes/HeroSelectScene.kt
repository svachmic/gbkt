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
import io.github.gbkt.examples.labyrinth.GameConfig
import io.github.gbkt.examples.labyrinth.GameState

/**
 * Hero Selection Screen
 *
 * Allows the player to choose their character class: Druid, Fighter, Monk, or Sorcerer.
 */
fun GameBuilder.initHeroSelectScene(
    state: GameState,
    title: SceneRef,
    gameplay: SceneRef,
): SceneRef =
    scene("hero_select") {
        enter {
            screen.clear()
            // Reset cursor to first option
            state.heroSelectCursor set 0

            // Draw header
            print("SELECT CLASS") at (4 to 2)
            print("") at (0 to 4)

            // Draw class options (cursor will be drawn in frame)
            print(">DRUID") at (3 to 6)
            print(" FIGHTER") at (3 to 8)
            print(" MONK") at (3 to 10)
            print(" SORCERER") at (3 to 12)

            // Draw class descriptions area
            print("") at (0 to 14)
            print("Nature magic") at (3 to 15)
            print("& healing") at (3 to 16)
        }

        every.frame {
            // D-pad up: move cursor up
            whenever(dpad.up.pressed) {
                whenever(state.heroSelectCursor isAbove 0) { state.heroSelectCursor -= 1 }
            }

            // D-pad down: move cursor down
            whenever(dpad.down.pressed) {
                whenever(state.heroSelectCursor isBelow GameConfig.CLASS_COUNT - 1) {
                    state.heroSelectCursor += 1
                }
            }

            // A button: confirm selection and start game
            whenever(buttons.a.pressed) {
                // Store selected class
                state.selectedClass set state.heroSelectCursor
                // Transition to gameplay
                scene(gameplay)
            }

            // B button: return to title
            whenever(buttons.b.pressed) { scene(title) }
        }
    }
