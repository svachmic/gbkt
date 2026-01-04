/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package modular.entities

import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.builder.palette
import io.github.gbkt.core.entity.Entity
import io.github.gbkt.core.entity.entity
import io.github.gbkt.core.graphics.Palette
import io.github.gbkt.core.ir.x

/**
 * Enemy Entity Factory
 *
 * Creates enemy entities with basic configuration.
 *
 * Dependencies:
 * - None (creates its own palette)
 */

// Enemy palette (module-local, created on first use)
private lateinit var enemyPalette: Palette

/**
 * Create an enemy entity.
 *
 * @param startX Initial X position (default: 140)
 * @param startY Initial Y position (default: 100)
 * @return The created enemy Entity
 *
 * Usage:
 * ```kotlin
 * val myGame = gbGame("MyGame") {
 *     val enemy = createEnemy(x = 100, y = 80)
 * }
 * ```
 */
fun GameBuilder.createEnemy(startX: Int = 140, startY: Int = 100): Entity {
    // Create palette if not already created
    if (!::enemyPalette.isInitialized) {
        enemyPalette = palette("enemy") { colors(0xFFFFFF, 0xFF8888, 0x884444, 0x000000) }
    }

    val enemyTag = tag("enemy")

    val enemy by entity {
        position(startX, startY)
        tag(enemyTag)

        sprite(SpriteAsset("enemy.png")) {
            size = 8 x 8
            palette = enemyPalette
            hitbox(0, 0, 8, 8)
        }
    }

    return enemy
}
