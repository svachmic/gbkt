/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package modular.scenes

import io.github.gbkt.core.SceneRef
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.buttons
import io.github.gbkt.core.dpad
import io.github.gbkt.core.entity.Entity
import io.github.gbkt.core.print
import io.github.gbkt.core.screen
import io.github.gbkt.core.whenever
import modular.modules.player_health
import modular.modules.player_score

/**
 * Gameplay Scene
 *
 * Main gameplay loop with player movement and collision.
 *
 * Dependencies:
 * - PlayerModule (for player_score, player_health)
 * - Requires player and enemy entities to be passed in
 */

/**
 * Create the gameplay scene.
 *
 * @param player The player entity (from createPlayer())
 * @param enemy The enemy entity (from createEnemy())
 * @param titleScene Reference to title scene for returning
 * @return SceneRef for the gameplay scene
 *
 * Usage:
 * ```kotlin
 * val myGame = gbGame("MyGame") {
 *     setupPlayerModule()
 *     val player = createPlayer()
 *     val enemy = createEnemy()
 *     val titleScene = createTitleScene()
 *     val gameplayScene = createGameplayScene(player, enemy, titleScene)
 * }
 * ```
 */
fun GameBuilder.createGameplayScene(player: Entity, enemy: Entity, titleScene: SceneRef): SceneRef =
    scene("gameplay") {
        enter {
            // Reset player position
            player.x set 80
            player.y set 72

            // Reset game state
            player_score set 0
            player_health set 3

            screen.clear()
            screen.showSprites()
        }

        every.frame {
            // === Player Movement ===
            whenever(dpad.left) { player.x -= 2 }
            whenever(dpad.right) { player.x += 2 }
            whenever(dpad.up) { player.y -= 2 }
            whenever(dpad.down) { player.y += 2 }

            // === Screen Bounds ===
            whenever(player.x isBelow 0) { player.x set 0 }
            whenever(player.x isAbove 152) { player.x set 152 }
            whenever(player.y isBelow 16) { player.y set 16 }
            whenever(player.y isAbove 136) { player.y set 136 }

            // === Collision Detection ===
            whenever(player collidesWith enemy) {
                player_health -= 1

                // Reset player position on hit
                player.x set 80
                player.y set 72

                // Game over check
                whenever(player_health isEqualTo 0) { scene(titleScene) }
            }

            // === HUD ===
            print("SCORE:", player_score) at (0 to 0)
            print("HEALTH:", player_health) at (0 to 1)

            // === Exit to title ===
            whenever(buttons.select.pressed) { scene(titleScene) }
        }

        exit { screen.hideSprites() }
    }
