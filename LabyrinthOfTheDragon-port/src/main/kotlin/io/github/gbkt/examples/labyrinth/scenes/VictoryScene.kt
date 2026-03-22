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
 * Victory / credits scene for Labyrinth of the Dragon.
 *
 * Displayed after defeating the Dragon on floor 8. Shows a victory message and credits before
 * returning to the title screen.
 *
 * ## Original C Reference
 * - `credits.c` — `init_credits()`, `update_credits()`
 * - Victory condition: Dragon defeated on floor 8 (floor8DragonDefeated flag set)
 *
 * [PLACEHOLDER] Full credits animation in Plan 11.
 */
object VictoryScene {

    /**
     * Registers the victory scene into the [GameBuilder].
     *
     * @param builder The active [GameBuilder].
     * @param sounds Typed sound refs for SFX wiring.
     */
    fun register(builder: GameBuilder, sounds: LabyrinthSounds) {
        builder.apply {
            scene("victory") {
                enter {
                    hideSprites()
                    clear()
                    // @source credits.c — victory message
                    print("YOU SLEW THE DRAGON!", position = PositionDef(0, 4))
                    print("CONGRATULATIONS!", position = PositionDef(2, 6))
                    print("PRESS START", position = PositionDef(5, 13))
                    playSound(sounds.battleSuccess)
                }

                frame {
                    // Return to title on START or A
                    whenever(buttons.start.pressed) { navigate(Scenes.titleRef) }
                    whenever(buttons.a.pressed) { navigate(Scenes.titleRef) }
                }
            }
        }
    }
}
