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
import io.github.gbkt.core.dsl.metasprite
import io.github.gbkt.core.dsl.spritePalette
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.GbcTarget
import kotlin.test.Test
import kotlin.test.assertTrue

// =============================================================================
// DV3 VISUAL v3 DIAGNOSTIC TEST (Plan 10.2-07)
//
// Locks the 5TH-LAYER named root cause of DEF-10.1-13-C (post-Plan-22):
//
//   GBC visual still renders grayscale sprite (no cyan) despite Plan 22's
//   bootstrap-order + BG palette fix being GREEN at JVM tier because:
//
//   BISECT-NAMED INTERACTION (Phase 10.2 Plans 03–06d bisect chain, 7 probes):
//     The minimal breaking pair is Emission #2 + Emission #3 from Plan 22:
//       #2: set_bkg_palette(0u, 1u, _gbkt_default_bg_pal)  — BCPD write in main()
//       #3: fill_bkg_rect + set_bkg_data(0, 1, _checkerboard_bg_pattern)
//           hoisted from play_enter into main() AFTER allSpriteDataLoads
//
//   ROOT CAUSE: With LCDC.4=1 (shared $8000-$97FF region for both BG + sprite
//   tile data), Plan 22 emitted the two VRAM writes in this order:
//     1. set_sprite_data(0u, 48u, elephant_tiles)  → writes elephant bytes to tile 0+
//     2. set_bkg_data(0, 1, _checkerboard_bg_pattern) → OVERWRITES tile 0 with checker
//   Last write wins: elephant sprite tile 0 is now checker bytes. Combined with
//   the set_bkg_palette call committing BG-palette-aware PPU state, the sprite
//   renders using BG palette colors (gray) instead of the cyan OAM palette slot.
//
//   C-3 probe confirms: bgFillCheckerboard hoist alone does NOT break cyan.
//   C-4 probe confirms: bgFillCheckerboard + constant WITHOUT set_bkg_palette does NOT break cyan.
//   C-2 probe confirms: set_bkg_palette alone does NOT break cyan.
//   Only the COMBINATION of Emission #2 + Emission #3 breaks cyan.
//
// Evidence:
//   .planning/phases/10.2-gbc-palette-write-path-d-v3-visual-closure-4-round-inline-gr/
//     evidence/d-v3-visual-finding-v3.md  (Section 2 — Named Regression Site)
//     evidence/probe-table.md             (7-probe bisect summary table)
//     evidence/bisect-probe-B-plan22/verdict.md     (REGRESSION NAMED)
//     evidence/bisect-probe-C-3-bgFillCheckerboard-only/verdict.md (CLEARED)
//     evidence/bisect-probe-C-4-constant-and-bgFillCheckerboard/verdict.md (CLEARED)
//
// THIS TEST IS DELIBERATELY RED until Plan 10.2-08 lands the fix.
//
// THE FIX (from d-v3-visual-finding-v3.md Section 5):
//   Swap the two addAll() calls in mainBody buildList in GBDKPipeline.kt:
//     BEFORE: addAll(allSpriteDataLoads); addAll(hoistedBgFillCheckerboardStatements)
//     AFTER:  addAll(hoistedBgFillCheckerboardStatements); addAll(allSpriteDataLoads)
//   Writing bgFillCheckerboard first, then sprite data → sprite tile 0 wins (last write).
//
// WHAT THESE TESTS LOCK:
//   The RELATIVE ORDERING invariant: fill_bkg_rect + set_bkg_data must appear BEFORE
//   set_sprite_data in main() body. This is a STRICTER contract than DV3VisualDiagnosticTest
//   (which only asserts presence + before DISPLAY_ON). The relative order is the exact
//   invariant that the order-tweak fix establishes.
//
// IMPACT ON OTHER LOCKED TESTS (DV3GbcPaletteWriteDiagnosticTest, DV3VisualDiagnosticTest,
// BgCheckerboardEmissionTest, SpritePaletteSlotEmissionTest, GbcCompatEmissionTest):
//   NONE — all 5 remain GREEN. See d-v3-visual-finding-v3.md Section 6 for analysis.
//
// Migrated from legacy color API to Color.rgb555/Color.* (Plan 13.3-07)
// =============================================================================

// ---------------------------------------------------------------------------
// Brace-walk helper — D-18 binding: named extractFunctionBodyForDv3V3
// to avoid same-package symbol clash with extractFunctionBodyForDv3
// (DV3VisualDiagnosticTest.kt) and extractFunctionBody (DV3GbcPaletteWriteDiagnosticTest.kt).
// ---------------------------------------------------------------------------

private fun extractFunctionBodyForDv3V3(source: String, signature: String): String? {
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
// Minimal DSL game builder mirroring Metasprites.kt shape exactly:
// 4 spritePalette declarations + one scene with bgFillCheckerboard() in enter.
// GBC_COMPATIBLE config required so both hoistedBgFillCheckerboardStatements
// and hoistedDefaultBgPaletteStatements are activated in buildMainFunction().
// ---------------------------------------------------------------------------

private fun buildDv3V3GbcGame() =
    game("Dv3VisualV3Test") {
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

            // Minimal metasprite — triggers set_sprite_data emission in allSpriteDataLoads.
            // Required so that the relative-ordering assertions (fill_bkg_rect < set_sprite_data
            // and set_bkg_data < set_sprite_data) are testable. A single-tile single-frame
            // metasprite is the minimal DSL construct that causes buildAllSpriteDataLoadStatements
            // to emit set_sprite_data(...) into main(). The exact tile data is irrelevant for
            // the ordering invariant being tested here. [Rule 1 fix — Plan 10.2-08]
            val dummySprite by metasprite { frame { tile(0, 0, 0) } }

            val playScene =
                scene("play") {
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
// TEST CLASS — DELIBERATELY RED until Plan 10.2-08 lands the fix
// =============================================================================

class DV3VisualV3DiagnosticTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // Test 1: main() body emits fill_bkg_rect (hoisted bgFillCheckerboard)
    //         BEFORE set_sprite_data.
    //
    // NAMED INVARIANT (5TH LAYER — Phase 10.2 bisect):
    //   With LCDC.4=1 (shared $8000-$97FF VRAM), fill_bkg_rect + set_bkg_data(0,...)
    //   writes to the same VRAM region as set_sprite_data(0u,...). Last write wins.
    //   bgFillCheckerboard MUST be emitted FIRST so elephant sprite tile data
    //   is the LAST write to tile 0 — preserving sprite tile integrity at DISPLAY_ON.
    //
    // CURRENTLY RED: Plan 22's mainBody buildList emits allSpriteDataLoads THEN
    //   hoistedBgFillCheckerboardStatements → checker bytes overwrite sprite tile 0
    //   → fill_bkg_rect index is AFTER set_sprite_data index → assertion fails.
    //
    // Will flip GREEN when Plan 10.2-08 swaps the two addAll() calls in mainBody.
    // =========================================================================

    @Test
    fun `main body emits hoisted bgFillCheckerboard BEFORE set_sprite_data (5TH LAYER order fix)`() {
        val gameIR = buildDv3V3GbcGame()
        val mainC = pipeline.generate(gameIR).files["main.c"] ?: error("main.c not generated")

        val mainBody =
            extractFunctionBodyForDv3V3(mainC, "void main(void)")
                ?: error("Could not extract main() body from main.c")

        val fillBkgRectIdx = mainBody.indexOf("fill_bkg_rect")
        val setSpriteDataIdx = mainBody.indexOf("set_sprite_data")
        val displayOnIdx = mainBody.indexOf("DISPLAY_ON")

        require(displayOnIdx >= 0) { "main() body missing DISPLAY_ON — pipeline bug" }
        require(setSpriteDataIdx >= 0) {
            "main() body missing set_sprite_data — dummySprite did not trigger sprite data load. " +
                "Check buildAllSpriteDataLoadStatements() for metasprite support.\nmain() body:\n$mainBody"
        }

        // PRECONDITION: fill_bkg_rect must exist in main() body
        // (this is the hoisted bgFillCheckerboard — locked by DV3VisualV2DiagnosticTest Test 2)
        assertTrue(
            fillBkgRectIdx >= 0,
            "DEFECT (DEF-10.1-13-C / 5TH LAYER, bisect-named): fill_bkg_rect not emitted in " +
                "main() body. hoistedBgFillCheckerboardStatements must be non-empty for a " +
                "GBC_COMPATIBLE game with bgFillCheckerboard() in start scene enter. " +
                "Per d-v3-visual-finding-v3.md Section 2.\nmain() body:\n$mainBody",
        )

        // CORE INVARIANT: fill_bkg_rect must appear BEFORE set_sprite_data in main() body.
        // This locks the VRAM write ordering: checker bytes write to tile 0 first,
        // then elephant tiles overwrite — so sprite tile 0 has elephant pixel data
        // (not checker bytes) when DISPLAY_ON fires.
        //
        // CURRENTLY FAILS: Plan 22 emits set_sprite_data THEN fill_bkg_rect
        // (fillBkgRectIdx > setSpriteDataIdx), so this assertTrue will fail today.
        assertTrue(
            fillBkgRectIdx < setSpriteDataIdx,
            "DEFECT (DEF-10.1-13-C / 5TH LAYER, bisect-named): fill_bkg_rect emitted AFTER " +
                "set_sprite_data in main() body. Per d-v3-visual-finding-v3.md, with LCDC.4=1 " +
                "(shared \$8000-\$97FF VRAM region), set_bkg_data(0,...) overwrites sprite tile 0 " +
                "bytes that set_sprite_data(0u,...) previously loaded — corrupting the elephant " +
                "sprite tile. Fix: move addAll(hoistedBgFillCheckerboardStatements) to BEFORE " +
                "addAll(allSpriteDataLoads) in GBDKPipeline.kt buildMainFunction() mainBody buildList. " +
                "fill_bkg_rect at index $fillBkgRectIdx, set_sprite_data at index $setSpriteDataIdx.\n" +
                "main() body:\n$mainBody",
        )

        // ADDITIONAL GUARD: fill_bkg_rect must still be before DISPLAY_ON (preserves DV3-iter2
        // contract)
        assertTrue(
            displayOnIdx > fillBkgRectIdx,
            "DEFECT: fill_bkg_rect emitted AFTER DISPLAY_ON in main(). BG VRAM writes must " +
                "complete while LCD is OFF. fill_bkg_rect at index $fillBkgRectIdx, " +
                "DISPLAY_ON at index $displayOnIdx.\nmain() body:\n$mainBody",
        )
    }

    // =========================================================================
    // Test 2: main() body emits set_bkg_data (hoisted bgFillCheckerboard)
    //         BEFORE set_sprite_data.
    //
    // NAMED INVARIANT (5TH LAYER — Phase 10.2 bisect):
    //   set_bkg_data(0, 1, _checkerboard_bg_pattern) writes checker bytes to
    //   VRAM tile 0 at $8000. set_sprite_data(0u, 48u, elephant_tiles) also
    //   writes starting at tile 0. With LCDC.4=1 last write wins.
    //   set_bkg_data MUST write FIRST so the elephant sprite data wins at DISPLAY_ON.
    //
    // CURRENTLY RED: set_sprite_data index < set_bkg_data index in current main() →
    //   checker bytes are the LAST write to tile 0 → elephant sprite corrupted.
    //
    // Will flip GREEN when Plan 10.2-08 swaps the two addAll() calls in mainBody.
    // =========================================================================

    @Test
    fun `main body emits set_bkg_data BEFORE set_sprite_data (5TH LAYER VRAM collision fix)`() {
        val gameIR = buildDv3V3GbcGame()
        val mainC = pipeline.generate(gameIR).files["main.c"] ?: error("main.c not generated")

        val mainBody =
            extractFunctionBodyForDv3V3(mainC, "void main(void)")
                ?: error("Could not extract main() body from main.c")

        val setBkgDataIdx = mainBody.indexOf("set_bkg_data")
        val setSpriteDataIdx = mainBody.indexOf("set_sprite_data")
        val displayOnIdx = mainBody.indexOf("DISPLAY_ON")

        require(displayOnIdx >= 0) { "main() body missing DISPLAY_ON — pipeline bug" }
        require(setSpriteDataIdx >= 0) {
            "main() body missing set_sprite_data — dummySprite did not trigger sprite data load. " +
                "Check buildAllSpriteDataLoadStatements() for metasprite support.\nmain() body:\n$mainBody"
        }

        // PRECONDITION: set_bkg_data must exist in main() body
        // (locked by DV3VisualV2DiagnosticTest Test 2)
        assertTrue(
            setBkgDataIdx >= 0,
            "DEFECT (DEF-10.1-13-C / 5TH LAYER, bisect-named): set_bkg_data not emitted in " +
                "main() body. hoistedBgFillCheckerboardStatements must be non-empty for a " +
                "GBC_COMPATIBLE game with bgFillCheckerboard() in start scene enter. " +
                "Per d-v3-visual-finding-v3.md Section 2.\nmain() body:\n$mainBody",
        )

        // CORE INVARIANT: set_bkg_data must appear BEFORE set_sprite_data in main() body.
        //
        // CURRENTLY FAILS: Plan 22 emits allSpriteDataLoads THEN hoistedBgFillCheckerboard-
        // Statements (see GBDKPipeline.kt lines ~3826/3835). So set_sprite_data index
        // is lower than set_bkg_data index → this assertTrue fails today (RED).
        assertTrue(
            setBkgDataIdx < setSpriteDataIdx,
            "DEFECT (DEF-10.1-13-C / 5TH LAYER, bisect-named): set_bkg_data emitted AFTER " +
                "set_sprite_data in main() body. Per d-v3-visual-finding-v3.md, the VRAM " +
                "collision between set_bkg_data(0, 1, _checkerboard_bg_pattern) and " +
                "set_sprite_data(0u, 48u, elephant_tiles) corrupts sprite tile 0 at \$8000 " +
                "when set_bkg_data writes LAST (LCDC.4=1 shared region). " +
                "Fix: move addAll(hoistedBgFillCheckerboardStatements) to BEFORE " +
                "addAll(allSpriteDataLoads) in GBDKPipeline.kt buildMainFunction() mainBody buildList. " +
                "set_bkg_data at index $setBkgDataIdx, set_sprite_data at index $setSpriteDataIdx.\n" +
                "main() body:\n$mainBody",
        )

        // ADDITIONAL GUARD: set_bkg_data must still be before DISPLAY_ON (preserves DV3-iter2
        // contract)
        assertTrue(
            displayOnIdx > setBkgDataIdx,
            "DEFECT: set_bkg_data emitted AFTER DISPLAY_ON in main(). BG tile data must " +
                "be loaded while LCD is OFF. set_bkg_data at index $setBkgDataIdx, " +
                "DISPLAY_ON at index $displayOnIdx.\nmain() body:\n$mainBody",
        )
    }
}
