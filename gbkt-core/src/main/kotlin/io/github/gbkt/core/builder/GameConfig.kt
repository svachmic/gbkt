/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.builder

import io.github.gbkt.core.Cartridge
import io.github.gbkt.core.GameConfig
import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.ir.GBCMode

// =============================================================================
// GAME CONFIGURATION
// =============================================================================

/**
 * Configuration for asset directory.
 *
 * Usage:
 * ```kotlin
 * assets { directory = "src/main/resources/sprites" }
 * ```
 */
class AssetConfig {
    var directory: String = "assets"
}

/**
 * Builder for game hardware configuration.
 *
 * Usage:
 * ```kotlin
 * config {
 *     cartridge = Cartridge.MBC5_RAM_BATTERY
 *     romBanks = 4
 *     ramBanks = 1
 *     gbcSupport = true
 *     gbcMode = GBCMode.COMPATIBLE
 * }
 * ```
 */
@GbktDsl
class ConfigBuilder {
    var cartridge = Cartridge.ROM_ONLY
    var romBanks = 2
    var ramBanks = 0
    var gbcSupport = false
    var gbcMode = GBCMode.COMPATIBLE

    /**
     * Maximum level for all characters.
     *
     * Valid range is 1-255 (8-bit limit for Game Boy). Default is 99 for classic RPG feel.
     *
     * Examples:
     * - 99: Classic JRPG style (Dragon Quest, Final Fantasy)
     * - 50: Shorter progression (Pokémon Red/Blue)
     * - 255: Maximum possible (for grind-heavy games)
     */
    var maxLevel = 99

    /**
     * Maximum party size for battles.
     *
     * Valid range is 1-16. Default is 4.
     */
    var maxPartySize = 4

    /**
     * Maximum enemy count in battles.
     *
     * Valid range is 1-16. Default is 4.
     */
    var maxEnemies = 4

    /**
     * Maximum concurrent tweens.
     *
     * Valid range is 1-64. Default is 16.
     */
    var maxTweens = 16

    /**
     * Animation queue size per sprite.
     *
     * Valid range is 1-16. Default is 4.
     */
    var animationQueueSize = 4

    /**
     * A* pathfinding maximum open nodes.
     *
     * Valid range is 8-255. Default is 64.
     */
    var astarMaxNodes = 64

    /**
     * Maximum path length in tiles.
     *
     * Valid range is 4-128. Default is 32.
     */
    var pathMaxLength = 32

    fun build(): GameConfig {
        require(maxLevel in 1..255) { "Max level must be 1-255, got: $maxLevel" }
        require(maxPartySize in 1..16) { "Max party size must be 1-16, got: $maxPartySize" }
        require(maxEnemies in 1..16) { "Max enemies must be 1-16, got: $maxEnemies" }
        require(maxTweens in 1..64) { "Max tweens must be 1-64, got: $maxTweens" }
        require(animationQueueSize in 1..16) {
            "Animation queue size must be 1-16, got: $animationQueueSize"
        }
        require(astarMaxNodes in 8..255) { "A* max nodes must be 8-255, got: $astarMaxNodes" }
        require(pathMaxLength in 4..128) { "Path max length must be 4-128, got: $pathMaxLength" }

        return GameConfig(
            cartridge,
            romBanks,
            ramBanks,
            gbcSupport,
            gbcMode,
            maxLevel,
            maxPartySize,
            maxEnemies,
            maxTweens,
            animationQueueSize,
            astarMaxNodes,
            pathMaxLength,
        )
    }
}
