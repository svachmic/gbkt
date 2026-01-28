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
 * Game Over Scene
 *
 * Shown when the player is defeated. Allows returning to title screen.
 */
fun GameBuilder.initGameOverScene(title: SceneRef): SceneRef =
    scene("gameover") {
        enter {
            screen.clear()
            print("GAME OVER") at (5 to 8)
            print("") at (0 to 10)
            print("PRESS START") at (4 to 12)
        }

        every.frame { whenever(buttons.start.pressed) { scene(title) } }
    }
