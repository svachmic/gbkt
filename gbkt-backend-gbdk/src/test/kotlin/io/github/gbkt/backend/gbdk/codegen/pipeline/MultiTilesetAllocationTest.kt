/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.ExplorationSystem
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.ZoneIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// Phase 12 Plan 15 (D-15) — multi-tileset asset pipeline + multi-zone bank
// allocation verification for the platformer-template port substrate.
//
// SUBSTRATE (per 12-CONTEXT.md and 12-RESEARCH.md §"D-15 finding"):
//   The reference platformer-template uses three areas (World1Area1,
//   World1Area2, World2Area1) drawn from TWO distinct tilesets:
//     - world1-tileset.png — shared by World1Area1 + World1Area2
//     - world2-tileset.png — used only by World2Area1
//   Plus title-screen.png + next-level.png as full-screen menu zones (not
//   modeled in this JVM test — they ride the same NEW-path codegen).
//
// VERDICT (12-RESEARCH.md §D-15 Recommendations):
//   The existing `ConvertZoneTilesetsTask` + `allocateZoneBanks` pipeline
//   ALREADY handles N distinct tilesets across M zones — recommendation (a)
//   in RESEARCH §D-15 is to ACCEPT THE GAP for Phase 12 and seed Phase 13
//   for dedup work (SEED-PHASE-12-SHARED-TILESET — created by Plan 12-26
//   at phase close).
//
// WHAT THIS TEST LOCKS:
//   1. Bank allocation correctness — `allocateZoneBanks` returns one entry
//      per zone, all assignments ≥ 2 (HOME=0 and scenes=1 reserved per the
//      pipeline contract), and each bank's predicted tilemap-data total stays
//      within the 16 KB hard ROM-bank capacity threshold (Phase 11 D-15
//      bank-layout signal, mirrored at 12-CONTEXT.md §D-17).
//   2. Shared-tileset duplication GAP DOCUMENTATION — the two zones that
//      share `world1-tileset.png` produce TWO distinct entries in the
//      `zoneTilesets` metadata manifest (one per zone id), each with its own
//      `sanitizedSymbol`. The downstream `ConvertZoneTilesetsTask` will
//      therefore invoke png2asset twice for the same PNG, producing two
//      identical `_zone_<id>_tileset.c` outputs. ROM size doubles for the
//      shared tileset; correctness is preserved. THIS GAP IS ACCEPTED FOR
//      PHASE 12 per RESEARCH §D-15 recommendation (a). The Phase 13 fix
//      seed (SEED-PHASE-12-SHARED-TILESET) is created by Plan 12-26.
//      WHEN A FUTURE DEDUP FIX LANDS, THIS TEST IS THE ONE TO UPDATE.
//
// ANTI-OVERFITTING DOCTRINE (D-overfitting-2 inherited from Phase 9/10/11):
//   This test verifies the GENERAL multi-tileset / multi-zone contract on
//   the existing pipeline. It does NOT name `Banks_*` or `Platformer_*`
//   fixtures, does NOT depend on the gbkt-examples module, and uses the
//   same `buildBankingGame` shape pattern as ZoneTilemapBankingTest so
//   regressions surface as broken contracts, not broken bespoke wiring.
// =============================================================================

/**
 * D-15 verification — 3 zones × 2 distinct tilesets on the existing pipeline.
 *
 * The test mirrors the substrate identified by 12-RESEARCH.md §"D-15 finding":
 * - 2 zones share `world1-tileset.png` (World1Area1 + World1Area2)
 * - 1 zone uses `world2-tileset.png` (World2Area1)
 *
 * **DOCUMENTED GAP — RESEARCH §D-15 recommendation (a):** accept duplication for Phase 12; Plan
 * 12-26 creates `SEED-PHASE-12-SHARED-TILESET.md` for Phase 13 dedup work. This test asserts the
 * gap EXISTS today (two zones sharing `world1-tileset.png` produce TWO distinct `sanitizedSymbol`
 * entries in the `zoneTilesets` manifest, which `ConvertZoneTilesetsTask` consumes as two separate
 * png2asset invocations) — so when a future dedup fix lands, this is the right test to update.
 */
class MultiTilesetAllocationTest {

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Substrate fixture — mirrors the Phase 12 platformer-template port shape.
    //
    // tileData sizes are deliberately small (256 bytes per area) — the test
    // exercises the BANK ALLOCATION ALGORITHM, not png2asset. Tileset PNG
    // bytes do not flow through `allocateZoneBanks` (those become separate
    // bank files via ConvertZoneTilesetsTask; only zone.tileData counts here).
    // -------------------------------------------------------------------------

    private fun buildPlatformerSubstrate(): GameIR {
        val world1Area1Zone =
            ZoneIR(
                id = "world1Area1Zone",
                name = "World 1 Area 1",
                tilesetPath = "res/graphics/world1-tileset.png", // SHARED with Area2
                mapWidth = 32,
                mapHeight = 32,
                tileData = List(256) { it and 0xFF },
            )
        val world1Area2Zone =
            ZoneIR(
                id = "world1Area2Zone",
                name = "World 1 Area 2",
                tilesetPath = "res/graphics/world1-tileset.png", // SHARED with Area1
                mapWidth = 32,
                mapHeight = 32,
                tileData = List(256) { (it + 32) and 0xFF },
            )
        val world2Area1Zone =
            ZoneIR(
                id = "world2Area1Zone",
                name = "World 2 Area 1",
                tilesetPath = "res/graphics/world2-tileset.png", // distinct tileset
                mapWidth = 32,
                mapHeight = 32,
                tileData = List(256) { (it + 64) and 0xFF },
            )
        // ExplorationSystem present so the pipeline takes the banked-zones
        // path (same pattern as ZoneTilemapBankingTest.buildBankingGame).
        // Multi-scene to escape BankingAnalysisPass's single-scene HOME
        // fast-path (Pitfall 2 mirror — same defence as ZoneTilesetIncludeTest).
        return GameIR(
            name = "PlatformerMultiTilesetSubstrate",
            config = CartridgeConfig(cartridge = Cartridge.MBC5, romBanks = 16),
            scenes =
                listOf(
                    SceneIR(id = "title"),
                    SceneIR(
                        id = "play",
                        zoneRefs = listOf("world1Area1Zone", "world1Area2Zone", "world2Area1Zone"),
                    ),
                ),
            systems = listOf(ExplorationSystem(id = "platformer")),
            zones = listOf(world1Area1Zone, world1Area2Zone, world2Area1Zone),
            startScene = "title",
        )
    }

    // -------------------------------------------------------------------------
    // Test 1 — Bank allocation correctness
    //
    // 3 zones must receive distinct allocation entries; every assignment must
    // be ≥ 2 (HOME=0 and scenes=1 are reserved by `allocateZoneBanks`'s
    // `tilemapBankStart = 2` constant).
    // -------------------------------------------------------------------------
    @Test
    fun three_zones_across_two_tilesets_each_get_a_bank_assignment_at_or_above_two() {
        val gameIR = buildPlatformerSubstrate()
        val allocation = pipeline.allocateZoneBanks(gameIR)

        assertEquals(
            3,
            allocation.size,
            "Plan 12-15 D-15: `allocateZoneBanks` must return one entry per zone " +
                "(3 zones in the platformer substrate). Actual entries: " +
                "${allocation.keys}",
        )

        for (zoneId in listOf("world1Area1Zone", "world1Area2Zone", "world2Area1Zone")) {
            val bank = allocation[zoneId]
            assertNotNull(
                bank,
                "Plan 12-15 D-15: zone '$zoneId' is missing from the allocation map. " +
                    "Got keys: ${allocation.keys}",
            )
            assertTrue(
                bank >= 2,
                "Plan 12-15 D-15: zone '$zoneId' was allocated to bank $bank, but " +
                    "banks 0 (HOME) and 1 (scenes) are reserved per `allocateZoneBanks` " +
                    "(`tilemapBankStart = 2`). All zone allocations MUST be ≥ 2.",
            )
        }
    }

    // -------------------------------------------------------------------------
    // Test 2 — Shared-tileset DUPLICATION GAP documentation
    //
    // DOCUMENTED GAP — RESEARCH §D-15 recommendation (a): accept duplication
    // for Phase 12; Plan 12-26 creates SEED-PHASE-12-SHARED-TILESET.md for
    // Phase 13 dedup work. This test asserts the gap EXISTS today (so a
    // future fix that DEdupes can detect this test is the right one to update).
    //
    // The gap manifests in `zoneTilesets` metadata: even though
    // `world1Area1Zone` and `world1Area2Zone` share `world1-tileset.png`,
    // the manifest emits TWO distinct entries (one per zone id), each with
    // its own `sanitizedSymbol`. ConvertZoneTilesetsTask consumes the
    // manifest entry-by-entry and invokes png2asset twice — producing two
    // identical `_zone_world1Area1Zone_tileset.c` and
    // `_zone_world1Area2Zone_tileset.c` files. ROM size doubles for the
    // shared tileset; correctness is preserved.
    //
    // A future dedup fix (Phase 13, seeded by Plan 12-26) would emit ONE
    // entry per unique `path` and route both zones to it — flipping the
    // `assertNotEquals(symA, symB)` lock here.
    // -------------------------------------------------------------------------
    @Test
    fun shared_tileset_produces_distinct_zone_tileset_manifest_entries_per_zone_documented_gap() {
        val gameIR = buildPlatformerSubstrate()
        val metadataJson = pipeline.buildMetadataFile(gameIR)
        val parsed = org.json.JSONObject(metadataJson)
        val zoneTilesets = parsed.getJSONArray("zoneTilesets")

        assertEquals(
            3,
            zoneTilesets.length(),
            "Plan 12-15 D-15: `zoneTilesets` manifest must emit one entry per " +
                "zone with tilesetPath != null (3 zones in the platformer " +
                "substrate). DUPLICATION GAP — RESEARCH §D-15 rec (a): " +
                "world1Area1 and world1Area2 share world1-tileset.png but the " +
                "manifest emits TWO separate entries (not one shared entry). " +
                "Actual length: ${zoneTilesets.length()}. Manifest:\n$zoneTilesets",
        )

        // Collect manifest entries by zone id.
        val entriesById = mutableMapOf<String, org.json.JSONObject>()
        for (i in 0 until zoneTilesets.length()) {
            val entry = zoneTilesets.getJSONObject(i)
            entriesById[entry.getString("id")] = entry
        }

        val area1 =
            entriesById["world1Area1Zone"]
                ?: error(
                    "Plan 12-15 D-15: world1Area1Zone missing from zoneTilesets " +
                        "manifest. Got ids: ${entriesById.keys}"
                )
        val area2 =
            entriesById["world1Area2Zone"]
                ?: error(
                    "Plan 12-15 D-15: world1Area2Zone missing from zoneTilesets " +
                        "manifest. Got ids: ${entriesById.keys}"
                )
        val world2 =
            entriesById["world2Area1Zone"]
                ?: error(
                    "Plan 12-15 D-15: world2Area1Zone missing from zoneTilesets " +
                        "manifest. Got ids: ${entriesById.keys}"
                )

        // Both shared-tileset zones point to the SAME png path — that's the
        // duplication input.
        assertEquals(
            area1.getString("path"),
            area2.getString("path"),
            "Plan 12-15 D-15 substrate sanity: world1Area1Zone and world1Area2Zone " +
                "MUST share `res/graphics/world1-tileset.png` in this fixture. " +
                "If this fails, the test fixture drifted from the documented " +
                "shared-tileset substrate — fix the fixture, not the assertion.",
        )
        assertEquals(
            "res/graphics/world1-tileset.png",
            area1.getString("path"),
            "Plan 12-15 D-15 substrate sanity: shared tileset path must match " +
                "the documented substrate verbatim.",
        )
        assertEquals(
            "res/graphics/world2-tileset.png",
            world2.getString("path"),
            "Plan 12-15 D-15 substrate sanity: world2Area1Zone must point to " +
                "`res/graphics/world2-tileset.png`.",
        )

        // DOCUMENTED GAP — distinct `sanitizedSymbol` per zone means
        // ConvertZoneTilesetsTask invokes png2asset TWICE on the same PNG,
        // producing TWO distinct `_zone_<id>_tileset.c` output files. This is
        // the duplication gap. SEED-PHASE-12-SHARED-TILESET (created at phase
        // close by Plan 12-26) tracks the Phase 13 fix. When that fix lands,
        // this assertion will need to flip to `assertEquals` (the dedup would
        // route both zones to a single shared symbol).
        val symArea1 = area1.getString("sanitizedSymbol")
        val symArea2 = area2.getString("sanitizedSymbol")
        assertTrue(
            symArea1 != symArea2,
            "Plan 12-15 D-15 DOCUMENTED GAP: shared-tileset zones MUST currently " +
                "produce DISTINCT sanitizedSymbol values (world1Area1Zone vs " +
                "world1Area2Zone). This is the duplication gap accepted by " +
                "RESEARCH §D-15 rec (a) for Phase 12; SEED-PHASE-12-SHARED-TILESET " +
                "tracks the Phase 13 dedup fix. If this lock flips (symbols equal), " +
                "the dedup fix landed — UPDATE THIS TEST to assertEquals and remove " +
                "the SEED file. symArea1='$symArea1' symArea2='$symArea2'",
        )
        assertEquals(
            "world1Area1Zone",
            symArea1,
            "Plan 12-15 D-15: sanitizedSymbol for world1Area1Zone must be the raw " +
                "id (no '-' or ' ' to sanitize in this fixture). Got '$symArea1'.",
        )
        assertEquals(
            "world1Area2Zone",
            symArea2,
            "Plan 12-15 D-15: sanitizedSymbol for world1Area2Zone must be the raw " +
                "id (no '-' or ' ' to sanitize in this fixture). Got '$symArea2'.",
        )
    }

    // -------------------------------------------------------------------------
    // Test 3 — Bank-size bound check (informational; 16 KB hard limit)
    //
    // Phase 11 D-15 carry (mirrored at 12-CONTEXT.md §D-17): each ROM bank
    // is hard-capped at 16384 bytes by the MBC. `allocateZoneBanks` validates
    // each zone individually; this test additionally verifies the PER-BANK
    // TOTAL (sum of `tileData.size` for all zones allocated to the same bank)
    // stays within the cap. If a future change to the FFD packer ever
    // over-commits a bank, this test fails with a message naming the
    // offending bank — RESEARCH §Pitfall 5 mitigation.
    //
    // INFORMATIONAL: the fixture's zones are 256 bytes each; the bank totals
    // are trivially within budget. The test value is REGRESSION GUARD against
    // a future packer bug that violates the invariant.
    // -------------------------------------------------------------------------
    @Test
    fun every_assigned_bank_total_tile_data_size_fits_within_16_kb_hard_limit() {
        val gameIR = buildPlatformerSubstrate()
        val allocation = pipeline.allocateZoneBanks(gameIR)

        // Sum tileData.size per bank.
        val bankTotals = mutableMapOf<Int, Int>()
        for (zone in gameIR.zones) {
            val bank =
                allocation[zone.id]
                    ?: error(
                        "Plan 12-15 D-15: zone '${zone.id}' missing from allocation " +
                            "map in bank-size bound check. Allocation: $allocation"
                    )
            // Mirror `zoneTileDataSize` in GBDKPipeline: empty tileData
            // counts as 1 (placeholder), otherwise the actual byte count.
            val size = if (zone.tileData.isEmpty()) 1 else zone.tileData.size
            bankTotals[bank] = (bankTotals[bank] ?: 0) + size
        }

        // 16 KB hard ROM-bank capacity threshold (Phase 11 D-15 carry-forward,
        // mirrored at 12-CONTEXT.md §D-17).
        val bankCapBytes = 16384

        for ((bank, total) in bankTotals) {
            assertTrue(
                total <= bankCapBytes,
                "Plan 12-15 D-15 bank-layout signal: bank $bank predicted total " +
                    "$total bytes EXCEEDS 16 KB hard limit ($bankCapBytes). " +
                    "FFD packer over-committed. RESEARCH §Pitfall 5: user can " +
                    "spread via `zone.bank(N)` manual override; framework bug " +
                    "requires fixing `allocateZoneBanks` bin-packing. " +
                    "Per-bank totals: $bankTotals. Allocation: $allocation.",
            )
        }
    }
}
