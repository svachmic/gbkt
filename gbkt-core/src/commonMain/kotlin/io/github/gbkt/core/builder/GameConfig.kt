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

    fun build() = GameConfig(cartridge, romBanks, ramBanks, gbcSupport, gbcMode)
}
