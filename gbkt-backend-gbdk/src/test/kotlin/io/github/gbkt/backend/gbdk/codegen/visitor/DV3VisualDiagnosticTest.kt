/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.dsl.Color
import io.github.gbkt.core.dsl.bgFillCheckerboard
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.dsl.spritePalette
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.GbcTarget
import kotlin.test.Test
import kotlin.test.assertTrue

// =============================================================================
// DV3 VISUAL v2 DIAGNOSTIC TEST (Plan 10.1-21)
//
// Locks the 4TH-LAYER named root cause of DEF-10.1-13-C (post-Plan-20):
//
//   GBC visual still all-black despite Plan 20's bootstrap-order fix because:
//     (a) gbkt NEVER emits an explicit set_bkg_palette() — relies entirely on
//         cgb_compatibility(), which (per Coffee-GB internals analysis) writes
//         BGP_REG only, NOT BCPD palette RAM. On Coffee-GB GBC mode, BG palette
//         RAM stays at the Java zero-init (all 0x0000 = pure black) → every BG
//         pixel composites to RGB(0,0,0).
//     (b) gbkt does NOT hoist `bgFillCheckerboard` (set_bkg_data + fill_bkg_rect)
//         to main() pre-DISPLAY_ON. Plan 20's SUMMARY explicitly excluded this
//         from scope. The first PPU frame post-DISPLAY_ON composites BG using
//         tile data that overlaps the sprite-tile region (LCDC.4=1) — the BG
//         tile 0 is the first 16 bytes of the elephant sprite tile 0, NOT the
//         checker pattern. Combined with (a), every BG pixel is RGB(0,0,0).
//
// Evidence:
//   .planning/phases/10.1-metasprites-surplus-codegen-defects-inserted/
//     evidence/d-v3-visual-diagnostic-v2/d-v3-visual-finding-v2.md
//   .planning/phases/10.1-metasprites-surplus-codegen-defects-inserted/
//     evidence/d-v3-visual-diagnostic-v2/cgb-compat-mode-vs-set-palette-notes.md
//   .planning/phases/10.1-metasprites-surplus-codegen-defects-inserted/
//     evidence/d-v3-visual-diagnostic-v2/oam-attribute-byte-trace.md
//
// THIS TEST IS DELIBERATELY RED until Plan 10.1-22 lands the fix.
//
// Reference (gbdk/examples/cross-platform/metasprites/src/metasprites.c:177-180):
//   fill_bkg_rect(0, 0, DEVICE_SCREEN_WIDTH, DEVICE_SCREEN_HEIGHT, 0);    // pre-DISPLAY_ON
//   set_bkg_data(0, 1, pattern);                                          // pre-DISPLAY_ON
//
// Plan 10.1-22 fix shape (from d-v3-visual-finding-v2.md):
//   GBDKPipeline.buildMainFunction() must:
//     (1) Emit `_gbkt_default_bg_pal[4] = {0x7FFF, 0x56B5, 0x294A, 0x0000}`
//         constant alongside user palettes (gray/pink/cyan/green).
//     (2) Emit `set_bkg_palette(0u, 1u, _gbkt_default_bg_pal);` in main()
//         AFTER the 4× sprite palette block, BEFORE DISPLAY_ON.
//     (3) Hoist `bgFillCheckerboard` (set_bkg_data + fill_bkg_rect) from
//         {start}_enter() into main() between set_sprite_data and SHOW_BKG.
//
// Migrated from legacy color API to Color.rgb555/Color.* (Plan 13.3-07)
// =============================================================================

// ---------------------------------------------------------------------------
// Brace-walk helper (shared pattern from DV3GbcPaletteWriteDiagnosticTest)
// ---------------------------------------------------------------------------

private fun extractFunctionBodyForDv3(source: String, signature: String): String? {
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
// Minimal DSL game builder mirroring Metasprites.kt shape
// (4 spritePalette declarations + bgFillCheckerboard in start-scene-enter)
// ---------------------------------------------------------------------------

private fun buildDv3GbcGame() =
    game("Dv3VisualTest") {
            config {
                cartridge = Cartridge.ROM_ONLY
                romBanks = 2
                target(GbcTarget.GBC_COMPATIBLE)
            }

            val gray by spritePalette {
                color0(Color.WHITE)
                color1(Color.rgb555(21, 21, 21))
                color2(Color.rgb555(10, 10, 10))
                color3(Color.BLACK)
            }
            val pink by spritePalette {
                color0(Color.WHITE)
                color1(Color.rgb555(31, 0, 31))
                color2(Color.rgb555(21, 0, 21))
                color3(Color.rgb555(10, 0, 10))
            }
            val cyan by spritePalette {
                color0(Color.WHITE)
                color1(Color.rgb555(10, 31, 31))
                color2(Color.rgb555(0, 21, 21))
                color3(Color.rgb555(0, 10, 10))
            }
            val green by spritePalette {
                color0(Color.WHITE)
                color1(Color.rgb555(21, 31, 21))
                color2(Color.rgb555(0, 21, 0))
                color3(Color.rgb555(0, 10, 0))
            }

            val playScene = scene("play") {
                palette(gray)
                palette(pink)
                palette(cyan)
                palette(green)
                enter { bgFillCheckerboard() }
            }

            start = playScene
        }
        .build()

// =============================================================================
// TEST CLASS — DELIBERATELY RED until Plan 10.1-22 lands the fix
// =============================================================================

class DV3VisualDiagnosticTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // Test 1: main() body contains explicit set_bkg_palette() call BEFORE DISPLAY_ON.
    //
    // The 4th-layer fix requires emitting `set_bkg_palette(0u, 1u, <default_bg_pal>)`
    // in main() pre-DISPLAY_ON for GBC-targeted games. Without this, Coffee-GB's
    // BG palette RAM remains at the Java zero-init state (all 0x0000) and every
    // BG pixel renders RGB(0,0,0) on the GBC pixel path.
    //
    // Currently RED: gbkt NEVER emits set_bkg_palette() anywhere.
    // =========================================================================

    @Test
    fun `main body contains set_bkg_palette before DISPLAY_ON (RED until Plan 10_1-22)`() {
        val gameIR = buildDv3GbcGame()
        val mainC = pipeline.generate(gameIR).files["main.c"] ?: error("main.c not generated")

        val mainBody =
            extractFunctionBodyForDv3(mainC, "void main(void)")
                ?: error("Could not extract main() body from main.c")

        // CONTRACT: main() must contain a set_bkg_palette() call
        val setBkgPaletteIdx = mainBody.indexOf("set_bkg_palette")
        val displayOnIdx = mainBody.indexOf("DISPLAY_ON")

        assertTrue(
            setBkgPaletteIdx >= 0,
            "DEFECT (DEF-10.1-13-C / 4TH LAYER): set_bkg_palette() not emitted in main() body. " +
                "Per d-v3-visual-finding-v2.md, gbkt relies entirely on cgb_compatibility() for " +
                "BG palette init. Coffee-GB's cgb_compatibility (sm83.lib) writes BGP_REG only, " +
                "NOT BCPD palette RAM. On GBC mode, BG palette RAM stays at Java zero-init = " +
                "every BG pixel renders RGB(0,0,0) = the all-black screenshot. " +
                "Reference: metasprites.c relies on cgb_compatibility for BG slot 0 — but " +
                "real-hardware cgb_compatibility writes BCPD; the Coffee-GB emulator-specific " +
                "behavior necessitates explicit set_bkg_palette() emission by gbkt.\n" +
                "main() body:\n$mainBody",
        )

        // CONTRACT: set_bkg_palette must appear BEFORE DISPLAY_ON
        assertTrue(
            displayOnIdx > setBkgPaletteIdx,
            "DEFECT (DEF-10.1-13-C / 4TH LAYER): set_bkg_palette() emitted AFTER DISPLAY_ON in main(). " +
                "Same bootstrap-order discipline as sprite palettes: BG palette RAM write must " +
                "complete while LCD is OFF. " +
                "set_bkg_palette at index $setBkgPaletteIdx, DISPLAY_ON at index $displayOnIdx.\n" +
                "main() body:\n$mainBody",
        )
    }

    // =========================================================================
    // Test 2: main() body contains hoisted bgFillCheckerboard (set_bkg_data +
    //         fill_bkg_rect) BEFORE DISPLAY_ON.
    //
    // Plan 20 explicitly excluded bgFillCheckerboard hoisting from scope. The
    // result: BG tile data + tilemap initialization is deferred to play_enter,
    // which runs AFTER DISPLAY_ON. The first PPU frame post-DISPLAY_ON composites
    // BG using the elephant sprite tile 0 bytes (LCDC.4=1 → BG and sprite tile
    // data share $8000-$8FFF; set_sprite_data line 218 wrote elephant bytes
    // INTO BG tile-0 region) — not the checker pattern.
    //
    // Currently RED: bgFillCheckerboard emits in play_enter only, NOT in main.
    // =========================================================================

    @Test
    fun `main body contains hoisted set_bkg_data and fill_bkg_rect before DISPLAY_ON (RED until Plan 10_1-22)`() {
        val gameIR = buildDv3GbcGame()
        val mainC = pipeline.generate(gameIR).files["main.c"] ?: error("main.c not generated")

        val mainBody =
            extractFunctionBodyForDv3(mainC, "void main(void)")
                ?: error("Could not extract main() body from main.c")

        val setBkgDataIdx = mainBody.indexOf("set_bkg_data")
        val fillBkgRectIdx = mainBody.indexOf("fill_bkg_rect")
        val displayOnIdx = mainBody.indexOf("DISPLAY_ON")

        require(displayOnIdx >= 0) { "main() body missing DISPLAY_ON — pipeline bug" }

        assertTrue(
            setBkgDataIdx >= 0,
            "DEFECT (DEF-10.1-13-C / 4TH LAYER): set_bkg_data() not emitted in main() body. " +
                "Per d-v3-visual-finding-v2.md, bgFillCheckerboard must be hoisted to main() " +
                "alongside the sprite-data hoist that Plan 20 already did. Reference: " +
                "metasprites.c:180 emits set_bkg_data() pre-DISPLAY_ON.\n" +
                "main() body:\n$mainBody",
        )

        assertTrue(
            fillBkgRectIdx >= 0,
            "DEFECT (DEF-10.1-13-C / 4TH LAYER): fill_bkg_rect() not emitted in main() body. " +
                "Per d-v3-visual-finding-v2.md, bgFillCheckerboard must be hoisted to main(). " +
                "Reference: metasprites.c:177 emits fill_bkg_rect() pre-DISPLAY_ON.\n" +
                "main() body:\n$mainBody",
        )

        assertTrue(
            displayOnIdx > setBkgDataIdx,
            "DEFECT (DEF-10.1-13-C / 4TH LAYER): set_bkg_data() emitted AFTER DISPLAY_ON in main(). " +
                "BG tile data must be loaded while LCD is OFF; deferring to AFTER DISPLAY_ON " +
                "means the first PPU frame composites with overlapping sprite tile data. " +
                "set_bkg_data at index $setBkgDataIdx, DISPLAY_ON at index $displayOnIdx.\n" +
                "main() body:\n$mainBody",
        )

        assertTrue(
            displayOnIdx > fillBkgRectIdx,
            "DEFECT (DEF-10.1-13-C / 4TH LAYER): fill_bkg_rect() emitted AFTER DISPLAY_ON in main(). " +
                "BG tilemap fill must complete while LCD is OFF. " +
                "fill_bkg_rect at index $fillBkgRectIdx, DISPLAY_ON at index $displayOnIdx.\n" +
                "main() body:\n$mainBody",
        )
    }
}
