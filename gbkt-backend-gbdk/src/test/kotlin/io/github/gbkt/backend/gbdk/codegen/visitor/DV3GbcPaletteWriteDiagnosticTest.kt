/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.dsl.Color
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.dsl.spritePalette
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.GbcTarget
import kotlin.test.Test
import kotlin.test.assertTrue

// =============================================================================
// DV3 GBC PALETTE-WRITE DIAGNOSTIC TEST (Plan 10.1-19)
//
// Locks the named root cause of DEF-10.1-13-C ("D-V3 visual broken: GBC
// screenshot completely black"):
//
//   Bootstrap-order mismatch — sprite palette writes are deferred to
//   scene-enter functions that run AFTER DISPLAY_ON, allowing the first PPU
//   frame to composite with uninitialized OCPD palette RAM (all-zero ->
//   all-black sprite render on GBC).
//
// Reference (gbdk/examples/cross-platform/metasprites/src/metasprites.c):
//   1. DISPLAY_OFF                                                  (line 161)
//   2. cgb_compatibility()                                          (line 164)
//   3. set_sprite_palette(0, 1, gray_pal)                           (line 165)
//   3a. set_sprite_palette(1..3, 1, pink/cyan/green_pal)            (lines 166-168)
//   4. fill_bkg_rect + set_bkg_data + load sprite tiles             (lines 177-183)
//   5. SHOW_BKG; SHOW_SPRITES                                       (line 186)
//   6. SPRITES_8x8                                                  (line 192)
//   7. DISPLAY_ON  -- LAST                                          (line 194)
//
// Port (gbkt-examples/metasprites/build/gbkt/generated/main.c, post-Plan-10.1-04):
//   - cgb_compatibility()              line 209
//   - sound init NR52/NR50/NR51        lines 210-212
//   - SPRITES_8x8                      line 213
//   - DISPLAY_ON                       line 214  <-- LCD ON
//   - SHOW_BKG; SHOW_SPRITES           lines 215-216  (AFTER DISPLAY_ON)
//   - set_sprite_data(0u, 48u, ...)    line 217        (AFTER DISPLAY_ON)
//   - play_enter()                     line 218        (AFTER DISPLAY_ON)
//     -> set_sprite_palette(0..3, ...) lines 234-237   (AFTER DISPLAY_ON via play_enter)
//
// The four set_sprite_palette() calls happen AFTER DISPLAY_ON, so the first
// PPU frame composites with palette RAM still in its post-cgb_compatibility()
// state (which for sprite palettes is undefined/zero on most emulators).
//
// THIS TEST IS DELIBERATELY RED until Plan 10.1-20 lands the fix.
//
// The fix shape proposed by Plan 10.1-19 is:
//   GBDKPipeline.buildMainFunction() must mirror reference bootstrap order:
//   prepend DISPLAY_OFF, hoist all set_sprite_palette() calls out of scene-
//   enter into main() BEFORE DISPLAY_ON. SHOW_BKG/SHOW_SPRITES/SPRITES_8x8
//   must immediately precede DISPLAY_ON (which becomes the LAST bootstrap macro).
//
// Evidence:
//   .planning/phases/10.1-metasprites-surplus-codegen-defects-inserted/
//     evidence/d-v3-visual-diagnostic/d-v3-visual-finding.md
//   .planning/phases/10.1-metasprites-surplus-codegen-defects-inserted/
//     evidence/d-v3-visual-diagnostic/port-vs-reference-palette-write-diff.txt
//
// Migrated from legacy color API to Color.rgb555/Color.* (Plan 13.3-07)
// =============================================================================

// ---------------------------------------------------------------------------
// Brace-walk helper (shared pattern from GbcCompatEmissionTest + SpritePaletteSlotEmissionTest)
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
// (4 spritePalette declarations, GBC_COMPATIBLE target, single scene)
// ---------------------------------------------------------------------------

private fun buildDv3GbcGame() =
    game("Dv3PaletteOrderTest") {
            config {
                cartridge(Cartridge.ROM_ONLY)
                romBanks(2)
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

            val playScene =
                scene("play") {
                    palette(gray)
                    palette(pink)
                    palette(cyan)
                    palette(green)
                    enter {}
                }

            start = playScene
        }
        .build()

// =============================================================================
// TEST CLASS — DELIBERATELY RED until Plan 10.1-20 lands the fix
// =============================================================================

class DV3GbcPaletteWriteDiagnosticTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // Test 1: set_sprite_palette() calls live in main() BEFORE DISPLAY_ON
    //
    // This is the canonical reference shape. Plan 10.1-20 will hoist palette
    // writes out of play_enter() and into main() to satisfy this invariant.
    //
    // Currently RED: palette writes live in play_enter() (NOT main()).
    // =========================================================================

    @Test
    fun `set_sprite_palette calls live in main before DISPLAY_ON (RED until Plan 10_1-20)`() {
        val gameIR = buildDv3GbcGame()
        val mainC = pipeline.generate(gameIR).files["main.c"] ?: error("main.c not generated")

        val mainBody =
            extractFunctionBodyForDv3(mainC, "void main(void)")
                ?: error("Could not extract main() body from main.c")

        // CONTRACT: main() must contain set_sprite_palette() calls
        val setSpritePaletteIdx = mainBody.indexOf("set_sprite_palette")
        val displayOnIdx = mainBody.indexOf("DISPLAY_ON")

        assertTrue(
            setSpritePaletteIdx >= 0,
            "DEFECT (DEF-10.1-13-C): set_sprite_palette() not emitted in main() body. " +
                "Palette writes are currently deferred to play_enter(), so the first PPU frame " +
                "post-DISPLAY_ON composites with uninitialized OCPD palette RAM " +
                "(all-zero -> all-black sprite render on GBC). " +
                "Reference: metasprites.c main() lines 165-168 (BEFORE DISPLAY_ON at line 194).\n" +
                "main() body:\n$mainBody",
        )

        // CONTRACT: every set_sprite_palette() must appear BEFORE DISPLAY_ON
        assertTrue(
            displayOnIdx > setSpritePaletteIdx,
            "DEFECT (DEF-10.1-13-C): set_sprite_palette() emitted AFTER DISPLAY_ON in main(). " +
                "Palette writes must complete while LCD is OFF or stalled in vblank — " +
                "writes while LCD is actively scanning can stall or partially complete. " +
                "Reference orders: cgb_compatibility -> set_sprite_palette x4 -> ... -> DISPLAY_ON LAST. " +
                "set_sprite_palette at index $setSpritePaletteIdx, DISPLAY_ON at index $displayOnIdx.\n" +
                "main() body:\n$mainBody",
        )
    }

    // =========================================================================
    // Test 2: All 4 set_sprite_palette() slots emit BEFORE DISPLAY_ON
    //
    // Stricter form of Test 1 — ensures ALL four palette slots (not just the
    // first) are loaded before the LCD comes on. The reference loads gray,
    // pink, cyan, green into slots 0/1/2/3 in main() before DISPLAY_ON.
    // =========================================================================

    @Test
    fun `all four sprite palette slots emit before DISPLAY_ON (RED until Plan 10_1-20)`() {
        val gameIR = buildDv3GbcGame()
        val mainC = pipeline.generate(gameIR).files["main.c"] ?: error("main.c not generated")

        val mainBody =
            extractFunctionBodyForDv3(mainC, "void main(void)")
                ?: error("Could not extract main() body from main.c")

        val displayOnIdx = mainBody.indexOf("DISPLAY_ON")
        require(displayOnIdx >= 0) {
            "main() body missing DISPLAY_ON — pipeline bug, not D-V3 issue"
        }

        val preDisplayOnRegion = mainBody.substring(0, displayOnIdx)
        val slotPattern = Regex("""set_sprite_palette\((\d)u""")
        val slotMatches =
            slotPattern.findAll(preDisplayOnRegion).map { it.groupValues[1].toInt() }.toSet()

        assertTrue(
            slotMatches == setOf(0, 1, 2, 3),
            "DEFECT (DEF-10.1-13-C): not all 4 sprite palette slots loaded in main() before DISPLAY_ON. " +
                "Found slots: $slotMatches (expected {0, 1, 2, 3}). " +
                "Reference: metasprites.c main() lines 165-168 load gray/pink/cyan/green into " +
                "slots 0/1/2/3 BEFORE DISPLAY_ON. " +
                "Pre-DISPLAY_ON region of main():\n$preDisplayOnRegion",
        )
    }

    // =========================================================================
    // Test 3: DISPLAY_ON is the LAST bootstrap macro in main()
    //
    // Reference order: ... SHOW_BKG; SHOW_SPRITES; SPRITES_8x8; DISPLAY_ON.
    // Currently RED: port emits SPRITES_8x8 -> DISPLAY_ON -> SHOW_BKG -> SHOW_SPRITES.
    // =========================================================================

    @Test
    fun `DISPLAY_ON is the last bootstrap macro before the game loop (RED until Plan 10_1-20)`() {
        val gameIR = buildDv3GbcGame()
        val mainC = pipeline.generate(gameIR).files["main.c"] ?: error("main.c not generated")

        val mainBody =
            extractFunctionBodyForDv3(mainC, "void main(void)")
                ?: error("Could not extract main() body from main.c")

        val displayOnIdx = mainBody.indexOf("DISPLAY_ON")
        val showBkgIdx = mainBody.indexOf("SHOW_BKG")
        val showSpritesIdx = mainBody.indexOf("SHOW_SPRITES")

        require(displayOnIdx >= 0) { "main() body missing DISPLAY_ON" }
        require(showBkgIdx >= 0) { "main() body missing SHOW_BKG" }
        require(showSpritesIdx >= 0) { "main() body missing SHOW_SPRITES" }

        assertTrue(
            showBkgIdx < displayOnIdx,
            "DEFECT (DEF-10.1-13-C / GAP-3): SHOW_BKG must precede DISPLAY_ON. " +
                "Reference: line 186 (SHOW_BKG) precedes line 194 (DISPLAY_ON). " +
                "Port: SHOW_BKG at index $showBkgIdx, DISPLAY_ON at index $displayOnIdx.",
        )

        assertTrue(
            showSpritesIdx < displayOnIdx,
            "DEFECT (DEF-10.1-13-C / GAP-3): SHOW_SPRITES must precede DISPLAY_ON. " +
                "Reference: line 186 (SHOW_SPRITES) precedes line 194 (DISPLAY_ON). " +
                "Port: SHOW_SPRITES at index $showSpritesIdx, DISPLAY_ON at index $displayOnIdx.",
        )
    }
}
