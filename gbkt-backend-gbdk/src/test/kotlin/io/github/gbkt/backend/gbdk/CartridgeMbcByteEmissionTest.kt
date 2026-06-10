/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk

// =============================================================================
// Phase 13.1 Plan 02 — Wave 0 RED TEST
//
// CartridgeMbcByteEmissionTest: Req #1 Cartridge enum MBC byte → mbcType emission
//
// This test is INTENTIONALLY RED until Plan 13.1-03 lands:
//   - Plan 13.1-03: adds `enum class Cartridge(val mbcByte: Int)` to
//                   gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/Types.kt
//                   with 7 entries and their hardware MBC bytes.
//
// What this locks (D-03 / D-04 contract):
//   The Cartridge enum MUST own its MBC hardware byte. The byte value IS the
//   authoritative source for the mbcType hex string written to gbkt-build.properties.
//   Plan 13.1-07 (Gradle plugin) will call `getMbcByte()` reflectively on the
//   enum instance and format it as "0x%02X" — replacing the CARTRIDGE_MBC_MAP
//   lookup in GenerateCTask.
//
//   This test locks the enum-byte → hex string contract so the Gradle plugin's
//   reflective call produces the correct output. It also serves as the canonical
//   reference for the 7 MBC byte values.
//
// Key assertions:
//   - ROM_ONLY → mbcByte = 0x00 → "0x00"
//   - MBC1    → mbcByte = 0x01 → "0x01"
//   - MBC1_RAM → mbcByte = 0x02 → "0x02"
//   - MBC1_RAM_BATTERY → mbcByte = 0x03 → "0x03"
//   - MBC3_TIMER_BATTERY → mbcByte = 0x10 → "0x10"
//   - MBC5    → mbcByte = 0x19 → "0x19"
//   - MBC5_RAM_BATTERY → mbcByte = 0x1B → "0x1B"
//
// RED today: `Cartridge` enum and `mbcByte` property do not exist in gbkt-ir/Types.kt.
// This file will not compile until Plan 13.1-03 adds the enum.
//
// The compile error `Unresolved reference: Cartridge` is the expected RED state.
//
// Relationship to GenerateCTask:
//   Plan 13.1-07 replaces CARTRIDGE_MBC_MAP with:
//     val mbcByteMethod = cartridge::class.java.getMethod("getMbcByte")
//     val mbcByteInt = mbcByteMethod.invoke(cartridge) as Int
//     val mbcType = "0x%02X".format(mbcByteInt)
//   This test locks the format string contract: the hex is zero-padded to 2 digits
//   with uppercase letters (e.g. "0x1B" not "0x1b" or "0x27").
// =============================================================================

import io.github.gbkt.core.ir.Cartridge
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * RED test — Req #1 D-03: Cartridge enum owns its MBC hardware byte.
 *
 * Tests will not compile until Plan 13.1-03 adds: enum class Cartridge(val mbcByte: Int) {
 * ROM_ONLY(0x00), MBC1(0x01), ... } in gbkt-ir/src/main/kotlin/io/github/gbkt/core/ir/Types.kt
 */
class CartridgeMbcByteEmissionTest {

    /**
     * Converts an MBC byte integer to the hex string format that GenerateCTask will write to
     * gbkt-build.properties as the "mbcType" key.
     *
     * Format: "0x" + uppercase hex digits, zero-padded to 2 digits. Examples: 0x00 → "0x00", 0x1B →
     * "0x1B", 0x19 → "0x19"
     */
    private fun mbcByteToHex(mbcByte: Int): String = "0x%02X".format(mbcByte)

    // -------------------------------------------------------------------------
    // ROM_ONLY: 0x00 — no memory controller, plain ROM
    // -------------------------------------------------------------------------

    @Test
    fun `ROM_ONLY has mbcByte 0x00`() {
        assertEquals(
            0x00,
            Cartridge.ROM_ONLY.mbcByte,
            "ROM_ONLY must have mbcByte=0x00 (no MBC hardware)",
        )
    }

    @Test
    fun `ROM_ONLY mbcByte formats as 0x00 hex string`() {
        assertEquals(
            "0x00",
            mbcByteToHex(Cartridge.ROM_ONLY.mbcByte),
            "ROM_ONLY: mbcType in gbkt-build.properties must be '0x00'",
        )
    }

    // -------------------------------------------------------------------------
    // MBC1: 0x01 — first generation MBC
    // -------------------------------------------------------------------------

    @Test
    fun `MBC1 has mbcByte 0x01`() {
        assertEquals(
            0x01,
            Cartridge.MBC1.mbcByte,
            "MBC1 must have mbcByte=0x01 per Game Boy hardware spec",
        )
    }

    @Test
    fun `MBC1 mbcByte formats as 0x01 hex string`() {
        assertEquals(
            "0x01",
            mbcByteToHex(Cartridge.MBC1.mbcByte),
            "MBC1: mbcType in gbkt-build.properties must be '0x01'",
        )
    }

    // -------------------------------------------------------------------------
    // MBC1_RAM: 0x02
    // -------------------------------------------------------------------------

    @Test
    fun `MBC1_RAM has mbcByte 0x02`() {
        assertEquals(0x02, Cartridge.MBC1_RAM.mbcByte)
    }

    // -------------------------------------------------------------------------
    // MBC1_RAM_BATTERY: 0x03
    // -------------------------------------------------------------------------

    @Test
    fun `MBC1_RAM_BATTERY has mbcByte 0x03`() {
        assertEquals(0x03, Cartridge.MBC1_RAM_BATTERY.mbcByte)
    }

    // -------------------------------------------------------------------------
    // MBC3_TIMER_BATTERY: 0x10 — this is the non-trivial hex value
    // -------------------------------------------------------------------------

    @Test
    fun `MBC3_TIMER_BATTERY has mbcByte 0x10`() {
        assertEquals(
            0x10,
            Cartridge.MBC3_TIMER_BATTERY.mbcByte,
            "MBC3_TIMER_BATTERY must have mbcByte=0x10 (16 decimal)",
        )
    }

    @Test
    fun `MBC3_TIMER_BATTERY mbcByte formats as 0x10 hex string`() {
        assertEquals(
            "0x10",
            mbcByteToHex(Cartridge.MBC3_TIMER_BATTERY.mbcByte),
            "MBC3_TIMER_BATTERY: zero-padding ensures '0x10' not '0x10' (same, but also '0x16' not '16')",
        )
    }

    // -------------------------------------------------------------------------
    // MBC5: 0x19
    // -------------------------------------------------------------------------

    @Test
    fun `MBC5 has mbcByte 0x19`() {
        assertEquals(0x19, Cartridge.MBC5.mbcByte, "MBC5 must have mbcByte=0x19 (25 decimal)")
    }

    // -------------------------------------------------------------------------
    // MBC5_RAM_BATTERY: 0x1B — the primary example used in the banks game
    // -------------------------------------------------------------------------

    /**
     * Primary assertion locked by Plan 13.1-02 must_haves. The banks example uses
     * Cartridge.MBC5_RAM_BATTERY and the plugin must write "0x1B" to gbkt-build.properties after
     * Plan 13.1-07 lands.
     */
    @Test
    fun `MBC5_RAM_BATTERY has mbcByte 0x1B`() {
        assertEquals(
            0x1B,
            Cartridge.MBC5_RAM_BATTERY.mbcByte,
            "MBC5_RAM_BATTERY must have mbcByte=0x1B (27 decimal)",
        )
    }

    @Test
    fun `MBC5_RAM_BATTERY mbcByte formats as 0x1B hex string`() {
        assertEquals(
            "0x1B",
            mbcByteToHex(Cartridge.MBC5_RAM_BATTERY.mbcByte),
            "MBC5_RAM_BATTERY: mbcType in gbkt-build.properties must be '0x1B' " +
                "(uppercase B per 0x%02X format string)",
        )
    }

    // -------------------------------------------------------------------------
    // Completeness: all 7 enum entries must have distinct mbcByte values
    // -------------------------------------------------------------------------

    @Test
    fun `all Cartridge enum entries have distinct mbcByte values`() {
        val bytes = Cartridge.entries.map { it.mbcByte }
        val distinctBytes = bytes.toSet()
        assertEquals(
            Cartridge.entries.size,
            distinctBytes.size,
            "Every Cartridge enum entry must have a unique mbcByte. " +
                "Duplicates would cause ambiguous gbkt-build.properties emission. " +
                "Found bytes: ${Cartridge.entries.map { "${it.name}=0x%02X".format(it.mbcByte) }}",
        )
    }

    @Test
    fun `Cartridge enum has exactly 11 entries`() {
        // 11 entries after WR-01 follow-up: original 7 + MBC2, MBC2_BATTERY, MBC3,
        // MBC3_RAM_BATTERY.
        // Adding a new entry requires a matching hardware byte — this test guards against
        // accidental addition of entries with invalid MBC bytes.
        assertEquals(
            11,
            Cartridge.entries.size,
            "Cartridge enum must have exactly 11 entries: " +
                "ROM_ONLY, MBC1, MBC1_RAM, MBC1_RAM_BATTERY, MBC2, MBC2_BATTERY, " +
                "MBC3_TIMER_BATTERY, MBC3, MBC3_RAM_BATTERY, MBC5, MBC5_RAM_BATTERY",
        )
    }
}
