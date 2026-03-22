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
import io.github.gbkt.examples.labyrinth.Palettes

/**
 * Hero class selection scene for Labyrinth of the Dragon.
 *
 * Allows the player to choose one of four character classes before starting the game: Druid,
 * Fighter, Monk, or Sorcerer.
 *
 * ## Original C Reference
 * - `hero_select.c` — `init_hero_select()`, `update_hero_select()`
 * - Class selection stored in `player.player_class` (CLASS_DRUID..CLASS_SORCERER)
 *
 * ## Input
 * - D-pad up/down: browse classes (0=Druid, 1=Fighter, 2=Monk, 3=Sorcerer)
 * - A or START: confirm selection, navigate to gameplay
 *
 * [PLACEHOLDER] Full implementation in Plan 11.
 */
object HeroSelectScene {

    /**
     * Registers the hero select scene into the [GameBuilder].
     *
     * @param builder The active [GameBuilder].
     * @param sounds Typed sound refs for SFX wiring.
     */
    fun register(builder: GameBuilder, sounds: LabyrinthSounds) {
        builder.apply {
            scene("hero_select") {
                palette(Palettes.titleBg)

                enter {
                    hideSprites()
                    clear()
                    print("CHOOSE YOUR CLASS", position = PositionDef(1, 2))
                    print("DRUID", position = PositionDef(2, 6))
                    print("FIGHTER", position = PositionDef(2, 8))
                    print("MONK", position = PositionDef(2, 10))
                    print("SORCERER", position = PositionDef(2, 12))
                    print("A: SELECT", position = PositionDef(5, 16))
                }

                frame {
                    // [PLACEHOLDER] Full class navigation and stat preview in Plan 11.
                    whenever(buttons.a.pressed) {
                        playSound(sounds.heroSelected)
                        navigate(Scenes.gameplayRef)
                    }
                    whenever(buttons.start.pressed) {
                        playSound(sounds.heroSelected)
                        navigate(Scenes.gameplayRef)
                    }
                }
            }
        }
    }
}
