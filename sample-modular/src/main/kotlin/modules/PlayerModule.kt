/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package modular.modules

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.builder.palette
import io.github.gbkt.core.graphics.Palette
import io.github.gbkt.core.ir.AssignableExpr
import io.github.gbkt.core.ir.u16Var
import io.github.gbkt.core.ir.u8Var

/**
 * Player Module
 *
 * Sets up player-related global configuration:
 * - Color palette for player sprites
 * - Player-related game variables
 *
 * This module should be initialized before creating player entities.
 */

// Module-scoped variables (will be registered with GameBuilder)
lateinit var player_score: AssignableExpr
    private set
lateinit var player_health: AssignableExpr
    private set

// Module-scoped palette reference
lateinit var playerPalette: Palette
    private set

/**
 * Initialize the player module.
 *
 * Call this from your main game definition before creating player entities.
 *
 * Usage:
 * ```kotlin
 * val myGame = gbGame("MyGame") {
 *     setupPlayerModule()  // Initialize first
 *     val player = createPlayer()  // Then create entities
 * }
 * ```
 */
fun GameBuilder.setupPlayerModule() {
    // Create player palette with GBC colors
    playerPalette = palette("player") { colors(0xFFFFFF, 0x88FF88, 0x448844, 0x000000) }

    // Register player-related game variables
    // Using property delegates with descriptive prefixes to avoid collisions
    val scoreVar by u16Var(0)
    val healthVar by u8Var(3)

    player_score = scoreVar
    player_health = healthVar
}
