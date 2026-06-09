/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import kotlin.test.Test
import kotlin.test.assertEquals

// =============================================================================
// CARTRIDGE ENUM SHAPE TESTS  (post-phase-13.1 follow-up — WR-01 fix)
// Verifies Cartridge enum has 11 entries in the exact order defined by D-01/D-03/WR-01,
// that each entry exposes the correct MBC hardware byte per D-03, that each entry
// exposes the correct maxRomBanks cap per WR-01, and that the .name round-trip
// contract (D-14) holds for serialization.
// =============================================================================

class CartridgeEnumTest {

    // =========================================================================
    // Entry count and order (D-01 / WR-01)
    // =========================================================================

    @Test
    fun `Cartridge enum has exactly 11 entries`() {
        val values = Cartridge.values()
        assertEquals(11, values.size, "Cartridge must have exactly 11 entries per WR-01")
    }

    @Test
    fun `Cartridge entries are in the correct order`() {
        val values = Cartridge.values()
        assertEquals(Cartridge.ROM_ONLY, values[0])
        assertEquals(Cartridge.MBC1, values[1])
        assertEquals(Cartridge.MBC1_RAM, values[2])
        assertEquals(Cartridge.MBC1_RAM_BATTERY, values[3])
        assertEquals(Cartridge.MBC2, values[4])
        assertEquals(Cartridge.MBC2_BATTERY, values[5])
        assertEquals(Cartridge.MBC3_TIMER_BATTERY, values[6])
        assertEquals(Cartridge.MBC3, values[7])
        assertEquals(Cartridge.MBC3_RAM_BATTERY, values[8])
        assertEquals(Cartridge.MBC5, values[9])
        assertEquals(Cartridge.MBC5_RAM_BATTERY, values[10])
    }

    // =========================================================================
    // MBC hardware byte values per D-03
    // =========================================================================

    @Test
    fun `ROM_ONLY mbcByte is 0x00`() {
        assertEquals(0x00, Cartridge.ROM_ONLY.mbcByte)
    }

    @Test
    fun `MBC1 mbcByte is 0x01`() {
        assertEquals(0x01, Cartridge.MBC1.mbcByte)
    }

    @Test
    fun `MBC1_RAM mbcByte is 0x02`() {
        assertEquals(0x02, Cartridge.MBC1_RAM.mbcByte)
    }

    @Test
    fun `MBC1_RAM_BATTERY mbcByte is 0x03`() {
        assertEquals(0x03, Cartridge.MBC1_RAM_BATTERY.mbcByte)
    }

    @Test
    fun `MBC2 mbcByte is 0x05`() {
        assertEquals(0x05, Cartridge.MBC2.mbcByte)
    }

    @Test
    fun `MBC2_BATTERY mbcByte is 0x06`() {
        assertEquals(0x06, Cartridge.MBC2_BATTERY.mbcByte)
    }

    @Test
    fun `MBC3_TIMER_BATTERY mbcByte is 0x10`() {
        assertEquals(0x10, Cartridge.MBC3_TIMER_BATTERY.mbcByte)
    }

    @Test
    fun `MBC3 mbcByte is 0x11`() {
        assertEquals(0x11, Cartridge.MBC3.mbcByte)
    }

    @Test
    fun `MBC3_RAM_BATTERY mbcByte is 0x13`() {
        assertEquals(0x13, Cartridge.MBC3_RAM_BATTERY.mbcByte)
    }

    @Test
    fun `MBC5 mbcByte is 0x19`() {
        assertEquals(0x19, Cartridge.MBC5.mbcByte)
    }

    @Test
    fun `MBC5_RAM_BATTERY mbcByte is 0x1B`() {
        assertEquals(0x1B, Cartridge.MBC5_RAM_BATTERY.mbcByte)
    }

    // =========================================================================
    // maxRomBanks per WR-01 (representative sample)
    // =========================================================================

    @Test
    fun `ROM_ONLY maxRomBanks is 2`() {
        assertEquals(2, Cartridge.ROM_ONLY.maxRomBanks)
    }

    @Test
    fun `MBC2 maxRomBanks is 16`() {
        assertEquals(16, Cartridge.MBC2.maxRomBanks)
    }

    @Test
    fun `MBC3 maxRomBanks is 128`() {
        assertEquals(128, Cartridge.MBC3.maxRomBanks)
    }

    @Test
    fun `MBC5 maxRomBanks is 256`() {
        assertEquals(256, Cartridge.MBC5.maxRomBanks)
    }

    // =========================================================================
    // valueOf round-trip (D-14 wire-string contract)
    // =========================================================================

    @Test
    fun `Cartridge valueOf MBC5_RAM_BATTERY round-trips to the correct entry`() {
        val entry = Cartridge.valueOf("MBC5_RAM_BATTERY")
        assertEquals(Cartridge.MBC5_RAM_BATTERY, entry)
        assertEquals(0x1B, entry.mbcByte)
    }

    @Test
    fun `Cartridge valueOf ROM_ONLY round-trips to the correct entry`() {
        val entry = Cartridge.valueOf("ROM_ONLY")
        assertEquals(Cartridge.ROM_ONLY, entry)
        assertEquals(0x00, entry.mbcByte)
    }

    @Test
    fun `Cartridge name-based round-trip holds for all entries`() {
        for (entry in Cartridge.values()) {
            val roundTripped = Cartridge.valueOf(entry.name)
            assertEquals(entry, roundTripped, "valueOf(${entry.name}) must return the same enum entry")
        }
    }
}
