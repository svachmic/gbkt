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
import io.github.gbkt.core.print
import io.github.gbkt.core.screen

/**
 * Victory Scene
 *
 * Shown when the player defeats the Dragon. Congratulates the player and transitions to credits.
 */
fun GameBuilder.initVictoryScene(credits: SceneRef): SceneRef =
    scene("victory") {
        enter {
            screen.clear()
            print("VICTORY!") at (6 to 4)
            print("") at (0 to 6)
            print("YOU HAVE SLAIN") at (3 to 8)
            print("THE DRAGON!") at (4 to 10)
            print("") at (0 to 12)
            print("CONGRATULATIONS") at (2 to 14)
            print("PRESS START") at (4 to 17)
        }

        every.frame { whenever(buttons.start.pressed) { scene(credits) } }
    }
