/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package modular.entities

import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.entity.Entity
import io.github.gbkt.core.entity.entity
import io.github.gbkt.core.ir.x
import modular.modules.playerPalette

/**
 * Player Entity Factory
 *
 * Creates the player entity with position and sprite.
 *
 * Dependencies:
 * - Requires PlayerModule to be initialized (for playerPalette)
 */

/**
 * Create the main player entity.
 *
 * @param startX Initial X position (default: 80)
 * @param startY Initial Y position (default: 72)
 * @return The created player Entity
 *
 * Usage:
 * ```kotlin
 * val myGame = gbGame("MyGame") {
 *     setupPlayerModule()  // Required dependency
 *     val player = createPlayer()
 * }
 * ```
 */
fun GameBuilder.createPlayer(startX: Int = 80, startY: Int = 72): Entity {
    val player by entity {
        position(startX, startY)

        sprite(SpriteAsset("player.png")) {
            size = 8 x 16
            palette = playerPalette
            hitbox(0, 0, 8, 16)
        }
    }

    return player
}
