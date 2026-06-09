/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GbcTarget
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.ZoneIR
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// ZONE-LOAD SET_BKG_PALETTE EMISSION TESTS
// Phase 12.9 W3 (RED) — locks the codegen contract for per-zone palette upload.
//
// Root cause (Phase 12.8 G3 BLOCKED, A6-CONFIRMED): runtime never calls
// set_bkg_palette() on per-zone _zone_<id>_tileset_palettes[16] arrays; only
// _gbkt_default_bg_pal is uploaded via main.c:698 (GBC-gated). After W3
// (-keep_palette_order), indices reference index-0 = near-black per PLTE, but
// BG palette RAM still holds cream from _gbkt_default_bg_pal → visual inversion.
//
// Fix (W5): SceneVisitor.kt inserts a GBC-gated set_bkg_palette call in the
// zone-load flatMap, AFTER _bkg_tiles_load_banked, BEFORE DISPLAY_ON (D-01).
// The palette count uses the _zone_<id>_tileset_PALETTE_COUNT macro (D-02).
//
// These tests are RED against HEAD codegen. They go GREEN after Plan 12.9-05
// (W5) lands the SceneVisitor emission.
//
// Pattern: SpritePaletteSlotEmissionTest (GBC-gated palette call, brace-walk,
//   GameIR fixture) + ZoneTilesetIncludeTest (ZoneIR with tilesetPath != null,
//   two-scene multi-scene escape) + GbcCompatEmissionTest (DMG negative test).
//
// Per CLAUDE.md §"Scope-level grep gates corollary": brace-walk helper
// extracts play_enter() body before asserting — prevents file-level grep from
// matching tokens in other functions.
//
// Per 12.9-PATTERNS §"inline-per-class convention": brace-walk helper is
// copied verbatim into this class (not shared utilities), keeping the test
// self-contained.
// =============================================================================

// ---------------------------------------------------------------------------
// Brace-walk helper (copy verbatim from SpritePaletteSlotEmissionTest.kt:37-55
// per Phase 12.7 inline-per-class convention from PATTERNS.md).
// ---------------------------------------------------------------------------

private fun extractFunctionBodyForPaletteTest(source: String, signature: String): String? {
    val sigIdx = source.indexOf(signature)
    if (sigIdx == -1) return null
    val openIdx = source.indexOf('{', sigIdx + signature.length)
    if (openIdx == -1) return null
    var depth = 0
    var i = openIdx
    while (i < source.length) {
        when (source[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return source.substring(openIdx + 1, i)
            }
        }
        i++
    }
    return null
}

// ---------------------------------------------------------------------------
// Zone IR fixture builder — two-scene game to escape BankingAnalysisPass
// single-scene HOME fast-path. Zone has tilesetPath != null to trigger
// NEW-path branch in SceneVisitor (Pitfall 4 in RESEARCH.md).
// ---------------------------------------------------------------------------

private fun buildZoneWithPaletteGame(target: GbcTarget = GbcTarget.GBC_COMPATIBLE) =
    GameIR(
        name = "ZonePaletteTest",
        config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2, gbcTarget = target),
        scenes =
            listOf(
                SceneIR(id = "title"), // empty stub — forces multi-scene path → bank1.c
                SceneIR(id = "play", zoneRefs = listOf("playZone")),
            ),
        zones =
            listOf(
                ZoneIR(
                    id = "playZone",
                    name = "Play Zone",
                    tilesetPath = "tiles/play.png", // NON-NULL → triggers NEW-path branch
                    mapWidth = 20,
                    mapHeight = 18,
                )
            ),
        startScene = "title",
    )

// =============================================================================
// TEST CLASS
// =============================================================================

class ZoneLoadSetBkgPaletteEmissionTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // Test 1: GBC target → set_bkg_palette is emitted in play_enter (D-01)
    //
    // RED TODAY: SceneVisitor does not yet emit set_bkg_palette.
    // Goes GREEN after Plan 12.9-05 (W5) lands the SceneVisitor change.
    // =========================================================================

    @Test
    fun `zone-load enter emits set_bkg_palette for GBC target`() {
        val gameIR = buildZoneWithPaletteGame(GbcTarget.GBC_COMPATIBLE)
        val allFiles = pipeline.generate(gameIR).files
        val bank1C = allFiles["bank1.c"] ?: error("bank1.c not generated. Files: ${allFiles.keys}")

        val enterBody =
            extractFunctionBodyForPaletteTest(bank1C, "void play_enter(void)")
                ?: error("Could not extract play_enter() body from bank1.c")

        // Positive: set_bkg_palette with PALETTE_COUNT macro (D-02 contract)
        assertTrue(
            enterBody.contains(
                "set_bkg_palette(0u, _zone_playZone_tileset_PALETTE_COUNT, _zone_playZone_tileset_palettes)"
            ),
            "play_enter body must contain set_bkg_palette with PALETTE_COUNT macro (D-01/D-02). " +
                "body:\n$enterBody",
        )

        // Ordering: set_bkg_palette BEFORE DISPLAY_ON (D-01 ordering contract)
        val paletteIdx = enterBody.indexOf("set_bkg_palette")
        val displayOnIdx = enterBody.indexOf("DISPLAY_ON")
        assertTrue(
            paletteIdx >= 0 && displayOnIdx > paletteIdx,
            "set_bkg_palette must appear BEFORE DISPLAY_ON in play_enter body (D-01). " +
                "paletteIdx=$paletteIdx displayOnIdx=$displayOnIdx. body:\n$enterBody",
        )
    }

    // =========================================================================
    // Test 2: DMG target → set_bkg_palette is NOT emitted (GBC-gating contract)
    //
    // NOTE: this test vacuously passes today (set_bkg_palette is absent for ALL
    // targets before W5). It stays GREEN after W5 lands, locking the GBC-gating
    // contract (mirrors GBDKPipeline.kt:4701 predicate pattern).
    // =========================================================================

    @Test
    fun `zone-load enter does NOT emit set_bkg_palette for DMG target`() {
        val gameIR = buildZoneWithPaletteGame(GbcTarget.DMG)
        val allFiles = pipeline.generate(gameIR).files
        val bank1C = allFiles["bank1.c"] ?: error("bank1.c not generated")

        val enterBody =
            extractFunctionBodyForPaletteTest(bank1C, "void play_enter(void)")
                ?: error("Could not extract play_enter() body from bank1.c")

        // Negative: DMG target must NOT emit set_bkg_palette (GBC-gated per D-01)
        assertFalse(
            enterBody.contains("set_bkg_palette"),
            "play_enter body must NOT contain set_bkg_palette for DMG target (GBC-gated). " +
                "body:\n$enterBody",
        )
    }

    // =========================================================================
    // Test 3: LEGACY-path zone (tilesetPath null) → set_bkg_palette NOT emitted
    //
    // Locks the SEED-017 deferred-unification contract: only NEW-path zones
    // (tilesetPath != null) get per-zone palette upload. LEGACY-path zones that
    // use procedural tile data are excluded.
    //
    // NOTE: this test vacuously passes today. It stays GREEN after W5 lands.
    // =========================================================================

    @Test
    fun `LEGACY-path zone (tilesetPath null) does NOT emit set_bkg_palette`() {
        val gameIR =
            GameIR(
                name = "LegacyZoneTest",
                config =
                    CartridgeConfig(
                        cartridge = Cartridge.ROM_ONLY,
                        romBanks = 2,
                        gbcTarget = GbcTarget.GBC_COMPATIBLE,
                    ),
                scenes =
                    listOf(
                        SceneIR(id = "title"),
                        SceneIR(id = "play", zoneRefs = listOf("legacyZone")),
                    ),
                zones =
                    listOf(
                        ZoneIR(
                            id = "legacyZone",
                            name = "Legacy",
                            tilesetPath = null, // LEGACY-path — no palette upload
                            mapWidth = 20,
                            mapHeight = 18,
                        )
                    ),
                startScene = "title",
            )
        val allFiles = pipeline.generate(gameIR).files
        val bank1C = allFiles["bank1.c"] ?: error("bank1.c not generated")

        val enterBody =
            extractFunctionBodyForPaletteTest(bank1C, "void play_enter(void)")
                ?: error("Could not extract play_enter() body")

        // Negative: LEGACY-path (tilesetPath null) must NOT emit set_bkg_palette
        // (SEED-017 deferred unification — only NEW-path zones get palette upload)
        assertFalse(
            enterBody.contains("set_bkg_palette"),
            "play_enter body must NOT contain set_bkg_palette for LEGACY-path zone " +
                "(SEED-017 deferred unification — only NEW-path zones get palette upload). " +
                "body:\n$enterBody",
        )
    }
}
