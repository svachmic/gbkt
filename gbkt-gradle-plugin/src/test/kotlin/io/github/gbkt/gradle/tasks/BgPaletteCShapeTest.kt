/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import io.github.gbkt.gradle.internal.GbdkToolchain
import java.io.File
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

// =============================================================================
// Phase 13.7 Plan 04 — BG palette C-shape guard test (Req 1 / Req 3 BG)
//
// Diagnostic verdict (13.7-DIAGNOSTIC.md § "BG root cause", LOCKED 2026-06-05):
//   BG = ALREADY-CORRECT. _zone_world1Area1Zone_tileset_palettes[16] is
//   byte-identical to the GBDK reference World1Tileset_palettes[16] (same
//   conversion via -keep_palette_order; PALETTE_COUNT = 4). The persistent
//   BG palette is correct from frame 1 of gameplay — the transient
//   _gbkt_default_bg_pal flash (Pitfall 5) is never visible on screen.
//   Frame≥60 GBC screenshot confirmed: BG matches reference. No blind
//   remap, no DSL re-authoring.
//
// BRANCH A taken (per plan 04 spec): zero BG source code change. This guard
// test locks the BG palette byte order so a future change cannot silently
// invert it (Req 3 BG). Req 1 is satisfied by evidence + this guard test.
//
// Contract:
//   The FIRST entry of `_zone_world1Area1Zone_tileset_palettes` must be
//   RGB8(8,24,32) — dark-teal, PLTE index 0 of world1-tileset.png.
//   Confirmed emitted value (13.7-DIAGNOSTIC.md § "BG codegen-read"):
//     _zone_world1Area1Zone_tileset_palettes[16] line 11:
//     RGB8(  8, 24, 32), RGB8(224,248,207), RGB8(136,192,112), RGB8( 48,104, 80), ...
//
// Asserts `contains("RGB8(  8, 24, 32)")` on the emitted .c content, which
// pins the dark-teal PLTE index-0 value in the luminance-order (ascending)
// palette produced by -keep_palette_order. This is the Tier 2 C-shape assertion
// described in 13.7-RESEARCH.md §"RED->GREEN Tier 2 C-shape assertion".
//
// SKIP-GUARDS:
//   1. png2asset absent (CI without GBDK) — return silently.
//   2. world1-tileset.png fixture absent (gbkt-examples not in checkout) — return silently.
//   Both skip-guards mirror World1TilesetGrassEncodingTest convention.
//
// FIXTURE: real world1-tileset.png from gbkt-examples/platformer-template/res/graphics/
// staged into a per-test TempDir. Same approach as World1TilesetGrassEncodingTest
// (T-12.8-03 mitigation — isolated copy; @TempDir cleanup is per-test).
//
// METADATA: minimal game_metadata.json with one zoneTilesets entry matching the
// production entry: id="world1Area1Zone", path graphics/world1-tileset.png,
// mapWidth 60, mapHeight 32, bank 2.
// =============================================================================

class BgPaletteCShapeTest {

    @TempDir lateinit var tempDir: File

    // -------------------------------------------------------------------------
    // D-04 BG C-shape assertion — _zone_world1Area1Zone_tileset_palettes first entry
    //
    // Given:  real world1-tileset.png staged into tempDir; minimal metadata.
    // When:   ConvertZoneTilesetsTask.convertZoneTilesets() runs with
    //         -keep_palette_order (world1-tileset.png is an indexed PNG).
    // Then:   _zone_world1Area1Zone_tileset.c is emitted AND the
    //         _zone_world1Area1Zone_tileset_palettes[] first entry is
    //         RGB8(  8, 24, 32) — source PLTE index-0 dark-teal.
    // -------------------------------------------------------------------------
    @Test
    fun `world1Area1Zone tileset emits RGB8(8,24,32) as first palette entry (PLTE index-0 luminance order)`() {
        // Skip-guard 1: png2asset must be discoverable (mirrors World1TilesetGrassEncodingTest).
        val gbdkDir =
            try {
                GbdkToolchain.find(null)
            } catch (_: Exception) {
                return
            }
        val png2asset = GbdkToolchain.getPng2asset(gbdkDir)
        if (!png2asset.exists()) return

        // Skip-guard 2: real example PNG must be in this checkout.
        val realPng = findPlatformerTemplatePng("world1-tileset.png") ?: return

        // Stage real world1-tileset.png into per-test TempDir.
        val assetDir = File(tempDir, "res").apply { mkdirs() }
        val gfxDir = File(assetDir, "graphics").apply { mkdirs() }
        realPng.copyTo(File(gfxDir, "world1-tileset.png"), overwrite = true)

        // Write minimal game_metadata.json — one zoneTilesets entry.
        // id, path, mapWidth, mapHeight, bank mirror the production PlatformerTemplate entry.
        val metadataFile = File(tempDir, "game_metadata.json")
        metadataFile.writeText(
            """
            {
              "zoneTilesets": [
                {
                  "id": "world1Area1Zone",
                  "path": "graphics/world1-tileset.png",
                  "sanitizedSymbol": "world1Area1Zone",
                  "mapWidth": 60,
                  "mapHeight": 32,
                  "bank": 2
                }
              ]
            }
            """
                .trimIndent()
        )

        val cSourceDir = File(tempDir, "out").apply { mkdirs() }
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task =
            project.tasks
                .register("convertZoneTilesetsTest", ConvertZoneTilesetsTask::class.java) {
                    gbdkHome.set(gbdkDir.absolutePath)
                    assetDirectory.set(assetDir)
                    this.metadataFile.set(metadataFile)
                    this.cSourceDir.set(cSourceDir)
                }
                .get()

        task.convertZoneTilesets()

        // Read emitted tileset C file.
        val outC = File(cSourceDir, "_zone_world1Area1Zone_tileset.c")
        assertTrue(
            outC.exists(),
            "_zone_world1Area1Zone_tileset.c must be emitted at ${outC.absolutePath}",
        )

        val cText = outC.readText()

        // Assert _zone_world1Area1Zone_tileset_palettes[] is present.
        assertTrue(
            cText.contains("_zone_world1Area1Zone_tileset_palettes"),
            "_zone_world1Area1Zone_tileset_palettes array must be present in the emitted C",
        )

        // ----------------------------------------------------------------
        // C-shape guard: first palette entry = RGB8(8,24,32) — dark-teal,
        // source PLTE index-0 under -keep_palette_order.
        //
        // Locked from Plan 13.7-01 diagnostic build (2026-06-05):
        //   _zone_world1Area1Zone_tileset.c line 11:
        //   RGB8(  8, 24, 32), RGB8(224,248,207), ...
        //
        // png2asset with -keep_palette_order emits aligned decimal values with
        // leading spaces for padding (e.g., "RGB8(  8, 24, 32)"). The regex
        // below matches the first RGB8(...) in the palette array declaration.
        // ----------------------------------------------------------------

        // The palette array line contains the first RGB8 entry immediately
        // after the '= {' opening. We locate the _palettes declaration block
        // and check the first RGB8 value in it.
        val paletteBlockPattern =
            Regex(
                """_zone_world1Area1Zone_tileset_palettes\s*\[\s*\d+\s*\]\s*=\s*\{([^}]*)""",
                RegexOption.DOT_MATCHES_ALL,
            )
        val paletteBlock = paletteBlockPattern.find(cText)
        assertTrue(
            paletteBlock != null,
            "_zone_world1Area1Zone_tileset_palettes[] array body must be parseable",
        )

        val paletteBody = paletteBlock!!.groupValues[1]

        // Extract all RGB8(...) entries from the palette body.
        val rgb8Pattern = Regex("""RGB8\s*\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)""")
        val rgb8Entries = rgb8Pattern.findAll(paletteBody).toList()
        assertTrue(
            rgb8Entries.isNotEmpty(),
            "At least one RGB8(...) entry must be present in the palette body",
        )

        val firstEntry = rgb8Entries.first()
        val r = firstEntry.groupValues[1].trim().toInt()
        val g = firstEntry.groupValues[2].trim().toInt()
        val b = firstEntry.groupValues[3].trim().toInt()

        assertTrue(
            r == 8 && g == 24 && b == 32,
            "First palette entry of _zone_world1Area1Zone_tileset_palettes must be " +
                "RGB8(8,24,32) (dark-teal, source PLTE index-0 / -keep_palette_order luminance order). " +
                "Diagnostic verdict (13.7-DIAGNOSTIC.md): BG ALREADY-CORRECT; " +
                "this guard locks the byte order so a future change cannot silently invert it. " +
                "Actual first entry: RGB8($r,$g,$b)",
        )
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Locate a platformer-template fixture PNG by walking up from the test working directory.
     * Mirrors [World1TilesetGrassEncodingTest.findPlatformerTemplatePng]'s contract:
     * - returns the resolved File when found,
     * - returns null when the fixture is not part of this checkout layout (caller skips silently).
     *
     * Search root: `gbkt-examples/platformer-template/res/graphics/<filename>`.
     */
    private fun findPlatformerTemplatePng(filename: String): File? {
        val rel = "gbkt-examples/platformer-template/res/graphics/$filename"
        var dir: File? = File("").absoluteFile
        repeat(6) {
            val candidate = dir?.let { File(it, rel) }
            if (candidate != null && candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        return null
    }
}
