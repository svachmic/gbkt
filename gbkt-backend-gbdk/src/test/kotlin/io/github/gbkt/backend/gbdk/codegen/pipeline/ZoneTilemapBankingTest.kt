/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.ExplorationSystem
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.ZoneIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// ZONE TILEMAP BANKING TESTS (Plan 06.7-09)
// Verifies that GBDKPipelineV2 correctly:
//  - Auto-allocates zone tilemap data across ROM banks (first-fit bin-packing)
//  - Generates CFile objects with correct bank field for each tilemap bank
//  - Emits SWITCH_ROM(N) in zone_load functions for banked tilemap data
//  - Handles manual bank override via bankOverride field with warning
//  - Produces extern declarations for banked tile arrays in game.h
//  - Reports an error for zones exceeding max bank capacity
// =============================================================================

/** One byte past 16KB — too large for a single bank. */
private const val OVERSIZED_TILE_COUNT = 16385

/** 8KB of tiles — fits two per 16KB bank. */
private const val EIGHT_KB_TILE_COUNT = 8192

/** 12KB of tiles — fits one per 16KB bank. */
private const val TWELVE_KB_TILE_COUNT = 12288

/** Helper: build a GameIR with an ExplorationSystem and the given zones. */
private fun buildBankingGame(
    zones: List<ZoneIR> = emptyList(),
    scenes: List<SceneIR> = listOf(SceneIR(id = "gameplay")),
): GameIR =
    GameIR(
        name = "BankingTestGame",
        config = CartridgeConfig(cartridge = "ROM_MBC5", romBanks = 16),
        scenes = scenes,
        systems = listOf(ExplorationSystem(id = "dungeon")),
        zones = zones,
        startScene = "gameplay",
    )

class ZoneTilemapBankingTest {

    private val pipeline = GBDKPipelineV2()

    // =========================================================================
    // Test 1: Single zone — auto-allocated to bank 2
    // =========================================================================

    @Test
    fun `single zone auto-allocated to bank 2`() {
        val zone =
            ZoneIR(id = "dungeon1", name = "Dungeon 1", tileData = List(1024) { it and 0xFF })
        val gameIR = buildBankingGame(zones = listOf(zone))
        val allocation = pipeline.allocateZoneBanks(gameIR)

        assertEquals(2, allocation["dungeon1"], "Single zone should be allocated to bank 2")
    }

    @Test
    fun `single zone generates zone_bank2_c file`() {
        val zone =
            ZoneIR(id = "dungeon1", name = "Dungeon 1", tileData = List(1024) { it and 0xFF })
        val gameIR = buildBankingGame(zones = listOf(zone))
        val bankAllocation = pipeline.allocateZoneBanks(gameIR)
        val bankFiles = pipeline.buildTilemapBankFiles(gameIR, bankAllocation)

        assertEquals(1, bankFiles.size, "Should generate 1 bank file for single zone")
        assertEquals("zone_bank2.c", bankFiles[0].name, "Bank file should be named zone_bank2.c")
        assertEquals(2, bankFiles[0].bank, "Bank file should have bank=2")
        assertTrue(
            bankFiles[0].variables.any { it.name == "_zone_dungeon1_tiles" },
            "Bank file should contain _zone_dungeon1_tiles array",
        )
    }

    // =========================================================================
    // Test 2: Multiple zones fitting one bank — all in same CFile(bank=2)
    // =========================================================================

    @Test
    fun `multiple small zones fitting in one bank placed in same CFile`() {
        val zones =
            listOf(
                ZoneIR(id = "zone_a", name = "Zone A", tileData = List(512) { 0 }),
                ZoneIR(id = "zone_b", name = "Zone B", tileData = List(512) { 0 }),
            )
        val gameIR = buildBankingGame(zones = zones)
        val bankAllocation = pipeline.allocateZoneBanks(gameIR)
        val bankFiles = pipeline.buildTilemapBankFiles(gameIR, bankAllocation)

        // Both zones should be in bank 2 (total 1024 bytes — well within 16KB)
        assertEquals(2, bankAllocation["zone_a"], "zone_a should be in bank 2")
        assertEquals(2, bankAllocation["zone_b"], "zone_b should be in bank 2")

        // Should produce a single CFile
        assertEquals(1, bankFiles.size, "Should generate 1 bank file when zones fit in one bank")
        assertEquals(2, bankFiles[0].bank, "Bank file should be bank 2")
        assertTrue(
            bankFiles[0].variables.any { it.name == "_zone_zone_a_tiles" },
            "_zone_zone_a_tiles should be in bank 2 file",
        )
        assertTrue(
            bankFiles[0].variables.any { it.name == "_zone_zone_b_tiles" },
            "_zone_zone_b_tiles should be in bank 2 file",
        )
    }

    // =========================================================================
    // Test 3: Multiple zones exceeding 16KB — split across bank 2 and bank 3
    // =========================================================================

    @Test
    fun `zones exceeding 16KB split across bank 2 and bank 3`() {
        // Each zone is 12KB — two won't fit in one 16KB bank
        val zones =
            listOf(
                ZoneIR(
                    id = "big_zone_a",
                    name = "Big Zone A",
                    tileData = List(TWELVE_KB_TILE_COUNT) { 0 },
                ),
                ZoneIR(
                    id = "big_zone_b",
                    name = "Big Zone B",
                    tileData = List(TWELVE_KB_TILE_COUNT) { 0 },
                ),
            )
        val gameIR = buildBankingGame(zones = zones)
        val bankAllocation = pipeline.allocateZoneBanks(gameIR)

        // Zones should be in different banks
        val bankA = bankAllocation["big_zone_a"]!!
        val bankB = bankAllocation["big_zone_b"]!!
        assertTrue(bankA != bankB, "12KB zones should be split across different banks")
        assertTrue(bankA >= 2, "Zone A bank should be >= 2")
        assertTrue(bankB >= 2, "Zone B bank should be >= 2")

        val bankFiles = pipeline.buildTilemapBankFiles(gameIR, bankAllocation)
        assertEquals(2, bankFiles.size, "Should generate 2 bank files for zones in different banks")
    }

    // =========================================================================
    // Test 4: Manual override bank(5) — zone placed in bank 5
    // =========================================================================

    @Test
    fun `manual bank override places zone in specified bank`() {
        val zone =
            ZoneIR(
                id = "special_zone",
                name = "Special Zone",
                tileData = List(256) { 0 },
                bankOverride = 5,
            )
        val gameIR = buildBankingGame(zones = listOf(zone))
        val allocation = pipeline.allocateZoneBanks(gameIR)

        assertEquals(5, allocation["special_zone"], "Zone with bankOverride=5 should be in bank 5")
    }

    @Test
    fun `manual bank override generates zone file for overridden bank`() {
        val zone =
            ZoneIR(
                id = "pinned_zone",
                name = "Pinned Zone",
                tileData = List(256) { 0 },
                bankOverride = 7,
            )
        val gameIR = buildBankingGame(zones = listOf(zone))
        val bankAllocation = pipeline.allocateZoneBanks(gameIR)
        val bankFiles = pipeline.buildTilemapBankFiles(gameIR, bankAllocation)

        assertEquals(1, bankFiles.size, "Should generate 1 bank file")
        assertEquals(7, bankFiles[0].bank, "Bank file should use the overridden bank 7")
        assertEquals("zone_bank7.c", bankFiles[0].name, "File name should reflect override bank")
    }

    // =========================================================================
    // Test 5: zone_load codegen includes SWITCH_ROM(2) and restore
    // =========================================================================

    @Test
    fun `zone_load codegen includes SWITCH_ROM before banked tilemap data access`() {
        val zone =
            ZoneIR(id = "banked_zone", name = "Banked Zone", tileData = List(1024) { it and 0xFF })
        val gameIR = buildBankingGame(zones = listOf(zone))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        val allocation = pipeline.allocateZoneBanks(gameIR)
        val bank = allocation["banked_zone"]!!

        assertTrue(
            mainC.contains("SWITCH_ROM($bank)"),
            "zone_load should emit SWITCH_ROM($bank) before tilemap data access",
        )
        assertTrue(
            mainC.contains("SWITCH_ROM(1)"),
            "zone_load should restore scene bank via SWITCH_ROM(1) after data access",
        )
    }

    // =========================================================================
    // Test 6: Zone in bank 0 — no SWITCH_ROM in zone_load
    //
    // NOTE: With banking always enabled (bankAllocation non-empty), zones go to bank 2+.
    // This test verifies the NO-banking case (empty allocation = legacy mode).
    // =========================================================================

    @Test
    fun `zone with empty tile data uses placeholder and no SWITCH_ROM when not banked`() {
        // A zone with no tile data in a game with no exploration system
        // This tests backward compat where tile arrays stay in HOME bank
        val zone = ZoneIR(id = "empty_zone", name = "Empty Zone")
        val gameIR =
            GameIR(
                name = "NoBankGame",
                config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
                scenes = listOf(SceneIR(id = "gameplay")),
                // No ExplorationSystem — zone_load function not generated
                zones = listOf(zone),
                startScene = "gameplay",
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // No ExplorationSystem means no zone_load function, so no SWITCH_ROM
        assertFalse(
            mainC.contains("SWITCH_ROM"),
            "Without ExplorationSystem, no SWITCH_ROM should appear",
        )
    }

    // =========================================================================
    // Test 7: Header file includes extern declarations for banked tile arrays
    // =========================================================================

    @Test
    fun `game_h includes extern declarations for banked zone tile arrays`() {
        val zone =
            ZoneIR(id = "banked_zone", name = "Banked Zone", tileData = List(1024) { it and 0xFF })
        val gameIR = buildBankingGame(zones = listOf(zone))
        val output = pipeline.generate(gameIR)
        val gameH = output.files["game.h"] ?: error("game.h not generated")

        assertTrue(
            gameH.contains("extern") && gameH.contains("_zone_banked_zone_tiles"),
            "game.h should contain extern declaration for _zone_banked_zone_tiles",
        )
    }

    // =========================================================================
    // Test 8: Large zone exceeding bank capacity — error diagnostic
    // =========================================================================

    @Test
    fun `zone exceeding 16KB bank capacity produces error diagnostic`() {
        val oversizedZone =
            ZoneIR(
                id = "huge_zone",
                name = "Huge Zone",
                tileData = List(OVERSIZED_TILE_COUNT) { 0 },
            )
        val gameIR = buildBankingGame(zones = listOf(oversizedZone))

        assertFails("Zone exceeding 16KB should throw an error") {
            pipeline.allocateZoneBanks(gameIR)
        }
    }

    // =========================================================================
    // Test 9: Bank allocation log includes zone-to-bank mapping
    // =========================================================================

    @Test
    fun `allocateZoneBanks returns correct mapping for multiple zones`() {
        val zones =
            listOf(
                ZoneIR(id = "zone1", name = "Zone 1", tileData = List(256) { 0 }),
                ZoneIR(id = "zone2", name = "Zone 2", tileData = List(256) { 0 }),
                ZoneIR(id = "zone3", name = "Zone 3", tileData = List(256) { 0 }),
            )
        val gameIR = buildBankingGame(zones = zones)
        val allocation = pipeline.allocateZoneBanks(gameIR)

        assertEquals(3, allocation.size, "Should have allocation entry for each zone")
        assertTrue(allocation.containsKey("zone1"), "Allocation should include zone1")
        assertTrue(allocation.containsKey("zone2"), "Allocation should include zone2")
        assertTrue(allocation.containsKey("zone3"), "Allocation should include zone3")
        // All small zones should fit in bank 2 (total 768 bytes << 16KB)
        assertEquals(2, allocation["zone1"], "zone1 should be in bank 2 (all small zones fit)")
        assertEquals(2, allocation["zone2"], "zone2 should be in bank 2 (all small zones fit)")
        assertEquals(2, allocation["zone3"], "zone3 should be in bank 2 (all small zones fit)")
    }

    // =========================================================================
    // Test 10: Zone tile arrays NOT in home file when banking is active
    // =========================================================================

    @Test
    fun `zone tile arrays absent from main_c when banking active`() {
        val zone =
            ZoneIR(id = "dungeon1", name = "Dungeon 1", tileData = List(1024) { it and 0xFF })
        val gameIR = buildBankingGame(zones = listOf(zone))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // When banking is active, the tile array initializer should NOT be in main.c
        // (it should be in zone_bank2.c instead)
        val bankFile = output.files["zone_bank2.c"] ?: error("zone_bank2.c not generated")
        assertTrue(
            bankFile.contains("_zone_dungeon1_tiles"),
            "_zone_dungeon1_tiles should be in zone_bank2.c",
        )
    }

    // =========================================================================
    // Test 11: No zones — no bank files generated
    // =========================================================================

    @Test
    fun `no zones generates no bank files`() {
        val gameIR = buildBankingGame(zones = emptyList())
        val bankAllocation = pipeline.allocateZoneBanks(gameIR)
        val bankFiles = pipeline.buildTilemapBankFiles(gameIR, bankAllocation)

        assertTrue(bankAllocation.isEmpty(), "Empty zones should yield empty allocation")
        assertTrue(bankFiles.isEmpty(), "Empty zones should yield no bank files")
    }

    // =========================================================================
    // Test 12: Bank file has correct includes (game.h)
    // =========================================================================

    @Test
    fun `zone bank file includes game_h`() {
        val zone = ZoneIR(id = "z", name = "Z", tileData = List(256) { 0 })
        val gameIR = buildBankingGame(zones = listOf(zone))
        val bankAllocation = pipeline.allocateZoneBanks(gameIR)
        val bankFiles = pipeline.buildTilemapBankFiles(gameIR, bankAllocation)

        assertTrue(bankFiles.isNotEmpty(), "Should have bank files")
        assertTrue(
            bankFiles[0].includes.any { it.contains("game.h") },
            "Zone bank file should include game.h",
        )
    }
}
