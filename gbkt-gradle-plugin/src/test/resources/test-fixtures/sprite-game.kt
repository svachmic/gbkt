/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package test

import io.github.gbkt.core.*
import io.github.gbkt.core.assets.SpriteAsset

/** Game with sprites fixture - tests sprite handling and asset pipeline. */
val spriteGame =
    gbGame("SpriteGame") {
        val player =
            sprite(SpriteAsset("player.png")) {
                size = 8 x 16
                position(80, 72)
            }

        val enemy =
            sprite(SpriteAsset("enemy.png")) {
                size = 8 x 8
                position(150, 100)
            }

        val mainScene =
            scene("main") {
                every.frame {
                    player.x += 1
                    whenever(player.x isAbove 160) { player.x set 0 }
                }
            }

        start = mainScene
    }
