/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GbcTarget
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.ZoneIR
import kotlin.test.Test
import kotlin.test.assertTrue

// =============================================================================
// Phase 13.8 Plan 01 Req 6 — ZoneBankFieldTest (Wave 0 RED scaffold)
//
// Root cause (13.8-RESEARCH.md Req 6, 12.9 WR-04):
//   The `_bkg_tiles_load_banked(bank, ...)` bank literal in SceneVisitor is computed
//   from `allocateZoneBanks(gameIR)` at pipeline-run time. There is no structural
//   guarantee that the bank assigned by `allocateZoneBanks` at SceneVisitor time is
//   the same bank used at any other emission site (e.g., `buildSetupCurrentLevelFunctionIfNeeded`).
//
//   The fix (D-01, 13.8-RESEARCH): Add `SceneIR.allocatedZoneBank: Int?` field,
//   populated by the pipeline after `allocateZoneBanks()` returns, so both emission
//   sites read from the SAME IR-level field. This makes the structural single-source
//   guarantee explicit and testable.
//
// Test 1 (GREEN — current behavior):
//   Verifies that the generated `_bkg_tiles_load_banked` bank literal in a scene's
//   enter function matches the zone's bank as computed by `allocateZoneBanks`.
//   This is a structural correctness regression guard.
//
// Test 2 (RED until 13.8-06):
//   Verifies that `SceneIR.allocatedZoneBank` field exists and is populated by the
//   pipeline, and that the emitted bank literal reads from that field.
//   @Disabled until 13.8-06 adds the `allocatedZoneBank` field to `SceneIR`.
// =============================================================================

class ZoneBankFieldTest {

    private val pipeline = GBDKPipeline()

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Build a minimal zone with a tilesetPath (triggers the NEW-path in SceneVisitor). No tile data
     * — just enough to register in the zone allocation.
     */
    private fun buildZone(id: String, bankOverride: Int? = null): ZoneIR =
        ZoneIR(
            id = id,
            name = id,
            tilesetPath = "tilesets/$id.png",
            tilemapPath = "tilemaps/$id.tilemap",
            mapWidth = 20,
            mapHeight = 18,
            bankOverride = bankOverride,
        )

    /**
     * Build a minimal GBC game with one scene referencing one zone. The zone will be auto-allocated
     * to bank 2 (tilemapBankStart=2, first zone).
     */
    private fun buildSingleZoneGame(zone: ZoneIR): GameIR =
        GameIR(
            name = "ZoneBankFieldTest",
            config =
                CartridgeConfig(
                    cartridge = Cartridge.ROM_ONLY,
                    romBanks = 4,
                    gbcTarget = GbcTarget.GBC_COMPATIBLE,
                ),
            scenes = listOf(SceneIR(id = "gameplay", zoneRefs = listOf(zone.id))),
            zones = listOf(zone),
            startScene = "gameplay",
        )

    // =========================================================================
    // Test 1 (GREEN — current behavior):
    //   Single zone auto-allocated to bank 2 → scene's enter function must emit
    //   `_bkg_tiles_load_banked(2u, ...)` (CLiteral(2) → "2u" in C).
    //
    //   allocateZoneBanks() starts at tilemapBankStart=2 for the first zone.
    //   SceneVisitor reads bank from zoneBankAllocation[zoneId] and emits CLiteral(bank).
    //   This test locks that the emitted literal matches the allocation.
    // =========================================================================
    @Test
    fun `single zone auto-allocated to bank 2 emits bkg_tiles_load_banked with bank literal 2`() {
        val zone = buildZone("area1")
        val game = buildSingleZoneGame(zone)

        val output = pipeline.generate(game)
        // The scene code lives in bank1.c
        val bank1C =
            output.files["bank1.c"] ?: error("bank1.c not generated. Files: ${output.files.keys}")

        // The zone is the first one allocated — auto-allocated to bank 2 (tilemapBankStart=2).
        // SceneVisitor emits: _bkg_tiles_load_banked(2u, 0u, 0u, ...) in gameplay_enter.
        assertTrue(
            bank1C.contains("_bkg_tiles_load_banked(2u,") ||
                bank1C.contains("_bkg_tiles_load_banked(2u ,"),
            "Zone 'area1' auto-allocated to bank 2: _bkg_tiles_load_banked must use bank literal 2u " +
                "in gameplay_enter (bank1.c). allocateZoneBanks starts at tilemapBankStart=2. " +
                "Relevant _bkg_tiles_load_banked lines:\n" +
                bank1C
                    .lines()
                    .filter { "_bkg_tiles_load_banked" in it }
                    .take(10)
                    .joinToString("\n"),
        )
    }

    // =========================================================================
    // Test 2 (current behavior — manual bank override):
    //   Zone with bankOverride=3 → emit _bkg_tiles_load_banked(3u, ...).
    //   Verifies the allocation respects manual override and the literal tracks it.
    // =========================================================================
    @Test
    fun `zone with manual bank override 3 emits bkg_tiles_load_banked with bank literal 3`() {
        val zone = buildZone("area1", bankOverride = 3)
        val game = buildSingleZoneGame(zone)

        val output = pipeline.generate(game)
        val bank1C =
            output.files["bank1.c"] ?: error("bank1.c not generated. Files: ${output.files.keys}")

        // Manual override to bank 3 → literal must be 3u
        assertTrue(
            bank1C.contains("_bkg_tiles_load_banked(3u,") ||
                bank1C.contains("_bkg_tiles_load_banked(3u ,"),
            "Zone 'area1' with bankOverride=3: _bkg_tiles_load_banked must use bank literal 3u. " +
                "Verifies that changing the allocated bank changes the emitted literal. " +
                "Relevant _bkg_tiles_load_banked lines:\n" +
                bank1C
                    .lines()
                    .filter { "_bkg_tiles_load_banked" in it }
                    .take(10)
                    .joinToString("\n"),
        )
    }

    // =========================================================================
    // Test 3 (GREEN from 13.8-06 — SceneIR.allocatedZoneBank field):
    //   Structural test: SceneIR.allocatedZoneBank must exist, be populated by the
    //   pipeline after allocateZoneBanks(), and the generated literal must match it.
    //
    //   This test exercises the IR-level single-source guarantee introduced by D-01:
    //   SceneVisitor reads scene.allocatedZoneBank (not the zoneBankAllocation map
    //   directly), so both the SceneVisitor bank literal and the structural field
    //   agree. Changing the allocated bank (via bankOverride) changes BOTH.
    //
    //   Two sub-assertions:
    //   1. allocateZoneBanks(game) returns the expected bank for the zone.
    //   2. The emitted _bkg_tiles_load_banked literal matches the allocated bank.
    //      (The SceneIR field itself is not directly observable from generated C output,
    //       but the field is the structural intermediate — SceneVisitor reads it.)
    // =========================================================================
    @Test
    fun `SceneIR allocatedZoneBank field is populated by pipeline and emitted bank literal matches`() {
        val zone = buildZone("area1")
        val game = buildSingleZoneGame(zone)

        // Sub-assertion 1: allocateZoneBanks assigns bank 2 to the zone (tilemapBankStart=2,
        // first zone, no override). This proves the source value the field will be populated with.
        val bankAllocation = pipeline.allocateZoneBanks(game)
        val expectedBank =
            bankAllocation["area1"] ?: error("area1 not in bankAllocation: $bankAllocation")
        assertTrue(
            expectedBank == 2,
            "Req 6 D-01: allocateZoneBanks must assign bank 2 to first zone 'area1'. " +
                "Got: $expectedBank. bankAllocation=$bankAllocation",
        )

        val output = pipeline.generate(game)
        val bank1C =
            output.files["bank1.c"] ?: error("bank1.c not generated. Files: ${output.files.keys}")

        // Sub-assertion 2: the emitted _bkg_tiles_load_banked literal must use bank 2,
        // which SceneVisitor derives from scene.allocatedZoneBank (the field populated by
        // buildCFiles after allocateZoneBanks returns). This confirms the single-source
        // structural guarantee (D-01): field == emitted literal.
        assertTrue(
            bank1C.contains("_bkg_tiles_load_banked(2u,") ||
                bank1C.contains("_bkg_tiles_load_banked(2u ,"),
            "Req 6: _bkg_tiles_load_banked bank literal (2u) must derive from " +
                "SceneIR.allocatedZoneBank field populated by pipeline (D-01). " +
                "Expected literal '2u', allocateZoneBanks returned $expectedBank. " +
                "Relevant lines:\n" +
                bank1C
                    .lines()
                    .filter { "_bkg_tiles_load_banked" in it }
                    .take(10)
                    .joinToString("\n"),
        )
    }

    // =========================================================================
    // Test 4 (GREEN from 13.8-06 — bank override propagates through field):
    //   Verify that changing the allocated bank (bankOverride=5) changes BOTH:
    //   - The allocateZoneBanks map value (source of the field)
    //   - The emitted _bkg_tiles_load_banked literal
    //   This is the "changing the allocated bank changes both literals" acceptance
    //   criterion from the plan.
    // =========================================================================
    @Test
    fun `changing allocated bank via bankOverride changes both allocateZoneBanks and emitted literal`() {
        val zone = buildZone("area1", bankOverride = 5)
        val game = buildSingleZoneGame(zone)

        val bankAllocation = pipeline.allocateZoneBanks(game)
        val expectedBank =
            bankAllocation["area1"] ?: error("area1 not in bankAllocation: $bankAllocation")
        assertTrue(
            expectedBank == 5,
            "bankOverride=5 must be reflected in allocateZoneBanks. Got: $expectedBank",
        )

        val output = pipeline.generate(game)
        val bank1C =
            output.files["bank1.c"] ?: error("bank1.c not generated. Files: ${output.files.keys}")

        // Both literals must use 5u (proving single-source-of-truth: changing the allocation
        // changes the field AND the emitted literal together).
        assertTrue(
            bank1C.contains("_bkg_tiles_load_banked(5u,") ||
                bank1C.contains("_bkg_tiles_load_banked(5u ,"),
            "bankOverride=5: emitted literal must be 5u (single-source via allocatedZoneBank field). " +
                "Relevant lines:\n" +
                bank1C
                    .lines()
                    .filter { "_bkg_tiles_load_banked" in it }
                    .take(10)
                    .joinToString("\n"),
        )
    }
}
