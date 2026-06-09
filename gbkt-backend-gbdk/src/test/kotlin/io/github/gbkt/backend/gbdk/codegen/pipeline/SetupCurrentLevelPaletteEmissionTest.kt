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
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.ZoneIR
import kotlin.test.Test
import kotlin.test.assertTrue

// =============================================================================
// SETUP_CURRENT_LEVEL SET_BKG_PALETTE EMISSION TEST
// Phase 12.9 Round 2 / Wave 8.2 (Plan 12.9-08b) — RC-1 palette inversion fix.
//
// Root cause (Plan 12.9-08a diagnose verdict, RC-1 CONFIRMED): gameplay zones
// load their tiles through the pipeline-generated setup_current_level()
// function (emitted by GBDKPipeline.buildSetupCurrentLevelFunctionIfNeeded).
// That per-zone template emits set_bkg_data but ZERO set_bkg_palette. W5's
// SceneVisitor palette fix never touched this path because the gameplay scene
// has empty scene.zoneRefs (DSL uses cEmit("setup_current_level();"), not
// zone(...)) — RESEARCH Pitfall 7. Result: gameplay inherits the title's BG
// palette → full light<->dark inversion.
//
// Fix (08b): append a per-zone set_bkg_palette(0u, _zone_<id>_tileset_PALETTE_COUNT,
// _zone_<id>_tileset_palettes) line after each case's set_bkg_data. The macro +
// extern + <gb/cgb.h> include are already provided by W4 (ConvertZoneTilesetsTask)
// — no header work. Emission is unconditional (mirrors the existing unconditional
// set_bkg_data in this template); cgb_compatibility() in main() makes set_bkg_palette
// a no-op on DMG, exactly like the _gbkt_default_bg_pal startup upload (RESEARCH RC-1d).
//
// RED against HEAD codegen (no palette call in setup_current_level). GREEN after
// the 08b template addition lands.
//
// Per CLAUDE.md §"Scope-level grep gates (corollary)": brace-walk helper extracts
// the setup_current_level() body before asserting — so the main() startup
// _gbkt_default_bg_pal set_bkg_palette cannot satisfy the assertion by accident.
//
// LEGACY-path note: unlike SceneVisitor (ZoneLoadSetBkgPaletteEmissionTest), the
// setup_current_level template emits set_bkg_data unconditionally for every
// gameplay zone regardless of tilesetPath, so there is no NEW/LEGACY split here.
// The fixture zone uses tilesetPath != null to mirror the real platformer-template
// gameplay zones (Pitfall 10).
// =============================================================================

// ---------------------------------------------------------------------------
// Brace-walk helper (copy verbatim from ZoneLoadSetBkgPaletteEmissionTest per
// the Phase 12.7 inline-per-class convention — keeps the test self-contained).
// ---------------------------------------------------------------------------

private fun extractSetupCurrentLevelBody(source: String, signature: String): String? {
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
// Game IR fixture — a tilemap_collision GenericSystem (gates
// buildSetupCurrentLevelFunctionIfNeeded ON) plus one gameplay zone whose id
// does NOT contain title/nextlevel and whose tilesetPath != null (Pitfall 10).
// A second empty scene escapes the single-scene HOME fast-path.
// ---------------------------------------------------------------------------

private fun buildTilemapCollisionGame(target: GbcTarget = GbcTarget.GBC_COMPATIBLE) =
    GameIR(
        name = "SetupCurrentLevelPaletteTest",
        config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 4, gbcTarget = target),
        scenes = listOf(SceneIR(id = "title"), SceneIR(id = "gameplay")),
        zones =
            listOf(
                ZoneIR(
                    id = "world1Area1Zone",
                    name = "World 1 Area 1",
                    tilesetPath =
                        "tiles/world1.png", // NON-NULL → mirrors real gameplay zone (Pitfall 10)
                    mapWidth = 60,
                    mapHeight = 18,
                )
            ),
        systems =
            listOf(
                GenericSystem(
                    id = "tilemapCollision",
                    config = mapOf("type" to "tilemap_collision"),
                )
            ),
        startScene = "title",
    )

// =============================================================================
// TEST CLASS
// =============================================================================

class SetupCurrentLevelPaletteEmissionTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // setup_current_level() per-zone case emits set_bkg_palette after set_bkg_data
    //
    // RED TODAY: buildSetupCurrentLevelFunctionIfNeeded emits set_bkg_data only.
    // GREEN after the 08b template addition.
    // =========================================================================

    @Test
    fun `setup_current_level emits per-zone set_bkg_palette after set_bkg_data`() {
        val gameIR = buildTilemapCollisionGame(GbcTarget.GBC_COMPATIBLE)
        val allFiles = pipeline.generate(gameIR).files
        val mainC = allFiles["main.c"] ?: error("main.c not generated. Files: ${allFiles.keys}")

        val body =
            extractSetupCurrentLevelBody(mainC, "void setup_current_level(void)")
                ?: error("Could not extract setup_current_level() body from main.c")

        // Positive: per-zone set_bkg_palette with the W4 PALETTE_COUNT macro + palettes extern.
        assertTrue(
            body.contains(
                "set_bkg_palette(0u, _zone_world1Area1Zone_tileset_PALETTE_COUNT, " +
                    "_zone_world1Area1Zone_tileset_palettes)"
            ),
            "setup_current_level body must contain per-zone set_bkg_palette with the " +
                "_zone_<id>_tileset_PALETTE_COUNT macro (RC-1 fix). body:\n$body",
        )

        // Ordering: set_bkg_palette must appear AFTER this zone's set_bkg_data (mirrors the
        // reference level.c setBKGPalettes-after-tileset-load sequence).
        val dataIdx = body.indexOf("set_bkg_data(0u, _zone_world1Area1Zone_tileset_count")
        val palIdx = body.indexOf("set_bkg_palette(0u, _zone_world1Area1Zone_tileset_PALETTE_COUNT")
        assertTrue(
            dataIdx >= 0 && palIdx > dataIdx,
            "set_bkg_palette must appear AFTER set_bkg_data in the zone case body (RC-1). " +
                "dataIdx=$dataIdx palIdx=$palIdx. body:\n$body",
        )
    }
}
