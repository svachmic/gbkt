/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.GbcTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// =============================================================================
// CONFIG BUILDER CARTRIDGE TESTS  (Wave 0 RED — Plan 13.1-01 Task 2)
// Verifies ConfigBuilder.cartridge(Cartridge) method + CartridgeConfig fields.
//
// These tests reference the cartridge(Cartridge) method and romBanks: Int?
// which do NOT yet exist — they MUST fail to compile until Plan 13.1-04
// adds the method and updates ConfigBuilder + CartridgeConfig.
// =============================================================================

class ConfigBuilderCartridgeTest {

    // =========================================================================
    // Behavior 1: cartridge(Cartridge.MBC5_RAM_BATTERY) sets typed enum field
    // =========================================================================

    @Test
    fun `cartridge MBC5_RAM_BATTERY sets typed enum on CartridgeConfig`() {
        val ir = game("Test") {
            config {
                cartridge(Cartridge.MBC5_RAM_BATTERY)
            }
            val sScene = scene("s") {}
            start = sScene
        }.build()

        assertEquals(Cartridge.MBC5_RAM_BATTERY, ir.config.cartridge)
    }

    // =========================================================================
    // Behavior 2: default build() yields ROM_ONLY and romBanks == null (D-05)
    // =========================================================================

    @Test
    fun `default config build yields Cartridge ROM_ONLY`() {
        val ir = game("Test") {
            val sScene = scene("s") {}
            start = sScene
        }.build()

        assertEquals(Cartridge.ROM_ONLY, ir.config.cartridge)
    }

    @Test
    fun `default config build yields romBanks null (D-05 derive sentinel)`() {
        val ir = game("Test") {
            val sScene = scene("s") {}
            start = sScene
        }.build()

        assertNull(ir.config.romBanks)
    }

    // =========================================================================
    // Behavior 3: target(GbcTarget) parity still works alongside cartridge()
    // =========================================================================

    @Test
    fun `target GBC_COMPATIBLE and cartridge MBC1 can be combined`() {
        val ir = game("Test") {
            config {
                cartridge(Cartridge.MBC1)
                target(GbcTarget.GBC_COMPATIBLE)
            }
            val sScene = scene("s") {}
            start = sScene
        }.build()

        assertEquals(Cartridge.MBC1, ir.config.cartridge)
        assertEquals(GbcTarget.GBC_COMPATIBLE, ir.config.gbcTarget)
    }

    // =========================================================================
    // Behavior 4: Cartridge.ROM_ONLY is the explicit default
    // =========================================================================

    @Test
    fun `explicitly setting cartridge ROM_ONLY matches the default`() {
        val irDefault = game("Test") {
            val sScene = scene("s") {}
            start = sScene
        }.build()

        val irExplicit = game("Test") {
            config {
                cartridge(Cartridge.ROM_ONLY)
            }
            val sScene = scene("s") {}
            start = sScene
        }.build()

        assertEquals(irDefault.config.cartridge, irExplicit.config.cartridge)
    }

    // =========================================================================
    // Behavior 5: MBC5_RAM_BATTERY round-trips through typed field (not String)
    // =========================================================================

    @Test
    fun `cartridge field is typed Cartridge enum not String`() {
        val ir = game("Test") {
            config {
                cartridge(Cartridge.MBC5_RAM_BATTERY)
            }
            val sScene = scene("s") {}
            start = sScene
        }.build()

        // Type-check: accessing .mbcByte proves the field is a Cartridge, not a String
        assertEquals(0x1B, ir.config.cartridge.mbcByte)
    }
}
