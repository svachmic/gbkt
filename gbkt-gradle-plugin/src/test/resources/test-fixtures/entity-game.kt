/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package test

import io.github.gbkt.core.*
import io.github.gbkt.core.assets.SpriteAsset

/**
 * Game with entity-component system fixture. Tests entity creation, velocity, and component system.
 */
val entityGame =
    gbGame("EntityGame") {
        var score by u16Var(0)

        // Tags
        val playerTag = tag("player")
        val enemyTag = tag("enemy")

        // Entities
        val player by entity {
            position(20, 100)
            velocity(2, 0)
            tag(playerTag)

            sprite(SpriteAsset("player.png")) {
                size = 8 x 16
                position(20, 100)
            }
        }

        val enemy by entity {
            position(150, 100)
            velocity(-1, 0)
            tag(enemyTag)

            sprite(SpriteAsset("enemy.png")) {
                size = 8 x 8
                position(150, 100)
            }
        }

        val mainScene =
            scene("main") {
                every.frame {
                    // Entity movement via velocity
                    player.x += player.velX
                    enemy.x += enemy.velX

                    // Wrap around
                    whenever(player.x isAbove 160) { player.x set 0 }
                    whenever(enemy.x isBelow 0) { enemy.x set 160 }

                    // Collision
                    whenever(player collidesWith enemy) {
                        score += 10
                        enemy.x set 160
                    }
                }
            }

        start = mainScene
    }
