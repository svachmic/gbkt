/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.scenes

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.dsl.buttons
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.examples.labyrinth.LabyrinthSounds

/**
 * Game over scene for Labyrinth of the Dragon.
 *
 * Displayed when the player dies in battle or otherwise fails. Shows a death message and waits for
 * input to return to the title screen.
 *
 * ## Original C Reference
 * - `main.c` — death handling, `GAME_STATE_CREDITS` (defeat path)
 * - Death palette fade from [io.github.gbkt.examples.labyrinth.Palettes.deathFade0-5]
 *
 * [PLACEHOLDER] Full implementation with death fade animation in Plan 11.
 */
object GameOverScene {

    /**
     * Registers the game over scene into the [GameBuilder].
     *
     * @param builder The active [GameBuilder].
     * @param sounds Typed sound refs for SFX wiring.
     */
    fun register(builder: GameBuilder, sounds: LabyrinthSounds) {
        builder.apply {
            scene("gameover") {
                enter {
                    hideSprites()
                    clear()
                    // @source main.c — death message
                    print("YOU HAVE DIED", position = PositionDef(3, 6))
                    print("PRESS START", position = PositionDef(5, 13))
                    playSound(sounds.battleDeath)
                }

                frame {
                    // Return to title on any press
                    whenever(buttons.start.pressed) { navigate(Scenes.titleRef) }
                    whenever(buttons.a.pressed) { navigate(Scenes.titleRef) }
                }
            }
        }
    }
}
