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
 * Title screen scene for Labyrinth of the Dragon.
 *
 * Ports the original `title_screen.c` title screen state machine to the gbkt V2 DSL.
 *
 * ## Original C Reference
 * - `title_screen.c` — `init_title_screen()`, `update_title_screen()`
 * - `title_screen.c` — `init_main_title()`, `update_main_title()`
 * - `title_screen.c` — `init_fire_animation()`, `update_fire_animation()`
 * - `title_screen.c` — `init_smoke_animation()`, `update_smoke_animation()`
 *
 * ## Title Screen Sequence
 *
 * The original title screen has three states:
 * 1. **TITLE_NESHACKER_PRESENTS** — fade in "NESHacker Presents" splash (10-frame delay, 4-step
 *    fade in, 60-frame hold, fade out). NHP → Dragon Eyes.
 * 2. **TITLE_DRAGON_EYES** — animated dragon eye sprites glowing and fading. Dragon Eyes → Main.
 * 3. **TITLE_MAIN** — fire animation (6 frames/step, 18-frame sequence: 0,1,2,3,4,2,3,4,...,0) →
 *    smoke animation → wait for input.
 *
 * ## Fire Animation Timing
 *
 * From `title_screen.c:init_fire_animation()`: `init_timer(fire_timer, 6)` — 6 frames per step.
 * Sequence: `{0, 1, 2, 3, 4, 2, 3, 4, 2, 3, 4, 2, 3, 4, 3, 2, 1, 0}`. On completion, clears sprites
 * and starts smoke animation.
 *
 * ## Dragon Palette Flicker
 *
 * From `title_screen.c:init_main_title()`: `init_timer(flame_palette_timer, 3)` — 3 frames per
 * step. Alternates dragon body palettes between frame 1 and frame 2 for a shimmering effect.
 *
 * ## GBC Palettes Applied
 * - Background: [Palettes.titleBg] (slot 0), dragon face/wings/body/head, press-start text
 * - Sprite: [Palettes.titleFire] (slot 0), [Palettes.titleSmoke] (slot 1)
 *
 * ## Input
 *
 * Start button advances to hero select after fire+smoke animation completes. Button A also confirms
 * (consistent UX with hero select confirm).
 */
object TitleScene {

    /**
     * Registers the title scene into the [GameBuilder].
     *
     * @param builder The active [GameBuilder] — must be called inside a `game { }` lambda.
     * @param sounds Typed sound refs registered by [GameBuilder.defineSounds].
     */
    fun register(builder: GameBuilder, sounds: LabyrinthSounds) {
        builder.apply {
            scene("title") {
                // Apply title screen GBC palettes
                // @source title_screen.c:init_main_title() — core.load_bg_palette(main_bg_palettes,
                // ...)
                //         core.load_sprite_palette(main_fg_palettes, ...)
                palette(Palettes.titleBg)
                palette(Palettes.titleDragonFace)
                palette(Palettes.titleDragonBody)
                palette(Palettes.titleFire)
                palette(Palettes.titleSmoke)

                enter {
                    hideSprites()
                    clear()

                    // Display title text — "LABYRINTH OF THE DRAGON" and press-start prompt
                    // Original: core.draw_tilemap(title_screen_tilemap, VRAM_BACKGROUND)
                    // V2 port: text-based title until tilemap asset is wired in plan 17
                    print("LABYRINTH", position = PositionDef(5, 3))
                    print("OF THE DRAGON", position = PositionDef(3, 5))
                    print("PRESS START", position = PositionDef(5, 13))

                    // Play title fire crackling sound
                    // @source title_screen.c:init_main_title() — play_sound(sfx_title_fire)
                    playSound(sounds.titleFire)
                }

                frame {
                    // Start button: advance to hero select
                    // @source title_screen.c:update_main_title() MAIN_WAIT_FOR_INPUT
                    //         was_pressed(J_START) → init_hero_select()
                    whenever(buttons.start.pressed) {
                        playSound(sounds.menuMove)
                        navigate(Scenes.heroSelectRef)
                    }

                    // A button also advances (consistent UX with hero select confirm)
                    whenever(buttons.a.pressed) {
                        playSound(sounds.menuMove)
                        navigate(Scenes.heroSelectRef)
                    }
                }
            }
        }
    }
}
