/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth

import io.github.gbkt.core.gbGame
import io.github.gbkt.core.input.buttons
import io.github.gbkt.core.print
import io.github.gbkt.core.screen

/**
 * Labyrinth of the Dragon - Minimal Version
 *
 * A stripped-down version to verify ROM building works. This has just the scene navigation without
 * RPG features.
 */
private const val MSG_PRESS_START = "PRESS START"
val labyrinthMinimal =
    gbGame("LabyrinthMin") {

        // Title Screen
        val title =
            scene("title") {
                enter {
                    screen.clear()
                    print("LABYRINTH OF") at (3 to 2)
                    print("THE DRAGON") at (4 to 4)
                    print(MSG_PRESS_START) at (4 to 10)
                }
                every.frame { whenever(buttons.start.pressed) { scene("gameplay") } }
            }

        // Gameplay Scene
        scene("gameplay") {
            enter {
                screen.clear()
                print("FLOOR 1") at (6 to 1)
                print("EXPLORING...") at (4 to 8)
                print("A=Battle B=Menu") at (2 to 14)
            }
            every.frame {
                whenever(buttons.a.pressed) { scene("battle") }
                whenever(buttons.b.pressed) { scene("title") }
            }
        }

        // Battle Scene
        scene("battle") {
            enter {
                screen.clear()
                print("BATTLE!") at (6 to 2)
                print("A Monster Appears!") at (1 to 6)
                print("A=Win B=Lose") at (3 to 14)
            }
            every.frame {
                whenever(buttons.a.pressed) { scene("victory") }
                whenever(buttons.b.pressed) { scene("gameover") }
            }
        }

        // Game Over
        scene("gameover") {
            enter {
                screen.clear()
                print("GAME OVER") at (5 to 8)
                print(MSG_PRESS_START) at (4 to 12)
            }
            every.frame { whenever(buttons.start.pressed) { scene("title") } }
        }

        // Victory
        scene("victory") {
            enter {
                screen.clear()
                print("VICTORY!") at (6 to 4)
                print("YOU WIN!") at (6 to 8)
                print(MSG_PRESS_START) at (4 to 12)
            }
            every.frame { whenever(buttons.start.pressed) { scene("title") } }
        }

        start = title
    }
