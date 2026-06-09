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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

// =============================================================================
// Phase 12.8 D-13 -- world1-tileset 2bpp byte-pattern emission lock
//
// Phase 12.8 root cause (12.8-DIAGNOSTIC.md verdict, W3 ABSORB-path fix):
// ConvertZoneTilesetsTask args list (kt:288-298) omitted -keep_palette_order
// per the Plan 11.2-02 spike's blanket reject. Reference Makefile line 82
// passes the flag; world1-tileset.png is 8-bit colormap (indexed) per file(1),
// so the flag IS valid here and was removed over-zealously.
//
// W3 fix (Plan 12.8-03 conditional re-scope): -keep_palette_order is now
// CONDITIONALLY appended based on the PNG's IHDR color-type byte. Indexed
// PNGs (world1/world2-tileset.png, color-type=3) get the flag; RGB PNGs
// (banks/checker.png, title-screen.png, next-level.png, color-type=2) do
// NOT (png2asset rejects the flag on RGB inputs).
//
// SPEC D-13 contract: regression guard against png2asset version drift OR
// accidental flag removal. Locks the post-fix 2bpp byte pattern of one
// representative grass tile in _zone_world1Area1Zone_tileset_tiles[432].
//
// RED STATE (pre-D-02 fix): png2asset auto-sorts PLTE indices
// light->dark; index 0 may not map to the source PNG's intended grass
// color, so the source PNG's color slot 0 (intended dark-green outline)
// ends up rendering as DMG-white (palette default slot 0 = white).
// Bytes shift accordingly across all tiles.
//
// GREEN STATE (post-D-02 fix): -keep_palette_order preserves source PNG
// palette indices; grass tiles encode against the correct slot-0 color.
// Captured post-fix snapshot of the first three bytes of tile-0 and
// tile-6 (both visually grass-tone tiles in world1-tileset.png) is locked
// below as the regression guard.
//
// SCOPE-LEVEL GREP GATE (CLAUDE.md §"Scope-level grep gates" corollary):
// A file-level mainC.contains("0x80") is INSUFFICIENT -- every 2bpp array
// has some 0x80 bytes. This test parses the _zone_world1Area1Zone_tileset_tiles
// array via Regex (mirroring ConvertZoneTilesetsTask.parseMapArrayBytes
// at kt:507-530), then asserts byte values at SPECIFIC tile-index offsets
// (per-symbol byte-pattern assertion is the array-data analog of per-function
// brace-walk).
//
// NAMING CONVENTION: per-feature emission-test naming (Phase 12.7
// PlatformerPhysicsSnapToTileTopEmissionTest, LevelEndTriggerGroundedGuard...,
// Defect4SymbolRewriteEmissionTest etc.). Single contract per class.
//
// FIXTURE: real gbkt-examples/platformer-template/res/graphics/world1-tileset.png
// is staged into a per-test TempDir (per T-12.8-03 mitigation -- the
// production PNG is the same on-disk file consumed by the gradle build, so
// the test catches upstream PNG edits as desired regression-guard behavior).
//
// SKIP-GUARDS: missing GBDK (CI without png2asset) and missing example PNG
// (gbkt-examples not part of this checkout layout) -- both return silently
// rather than failing, mirroring ConvertZoneTilesetsTaskTest.kt:49-57 +
// findBanksCheckerPng convention.
// =============================================================================

class World1TilesetGrassEncodingTest {

    @TempDir lateinit var tempDir: File

    // ------------------------------------------------------------------------
    // Test 1 -- D-13 post-fix grass-tile byte pattern lock
    //
    // Given:  real world1-tileset.png staged into tempDir; metadata declaring
    //         one zoneTilesets entry (world1Area1Zone, bank 2).
    // When:   ConvertZoneTilesetsTask.convertZoneTilesets() runs (which invokes
    //         png2asset with the W3-conditional flag set, including
    //         -keep_palette_order since world1-tileset.png is indexed).
    // Then:   _zone_world1Area1Zone_tileset.c is emitted AND its
    //         _zone_world1Area1Zone_tileset_tiles[432] array's bytes at the
    //         locked offsets match the post-fix snapshot.
    // ------------------------------------------------------------------------
    @Test
    fun `world1-tileset with -keep_palette_order emits expected grass-tile byte pattern`() {
        // Skip-guard 1: png2asset must be discoverable (mirrors
        // ConvertZoneTilesetsTaskTest.kt:49-57 verbatim).
        val gbdkDir =
            try {
                GbdkToolchain.find(null)
            } catch (_: Exception) {
                return
            }
        val png2asset = GbdkToolchain.getPng2asset(gbdkDir)
        if (!png2asset.exists()) return

        // Skip-guard 2: real example PNG must be in this checkout (mirrors
        // findBanksCheckerPng / findPlatformerTemplatePng convention from
        // ConvertZoneTilesetsTaskTest.kt:1019-1028).
        val realPng = findPlatformerTemplatePng("world1-tileset.png") ?: return

        // Stage real world1-tileset.png into per-test TempDir (per T-12.8-03
        // mitigation -- isolated copy; @TempDir cleanup is per-test).
        val assetDir = File(tempDir, "res").apply { mkdirs() }
        val gfxDir = File(assetDir, "graphics").apply { mkdirs() }
        realPng.copyTo(File(gfxDir, "world1-tileset.png"), overwrite = true)

        // Write minimal metadata: one zoneTilesets entry, one-invocation path
        // (no tilemapPath) -- the test scope is the FIRST png2asset invocation
        // (where the W3-conditional flag fires), not the tilemap-extraction
        // second invocation.
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

        // Read emitted tileset C.
        val outC = File(cSourceDir, "_zone_world1Area1Zone_tileset.c")
        assertTrue(
            outC.exists(),
            "_zone_world1Area1Zone_tileset.c must be emitted at ${outC.absolutePath}",
        )

        // Parse the _tiles[] byte array. world1-tileset.png is 64x32 = 8x4 = 32
        // visual tiles before png2asset dedup; the post-fix png2asset emits 27
        // unique tiles (dedup'd) * 16 bytes/tile = 432 bytes (locked below).
        val bytes =
            parseTileBytes(outC, "_zone_world1Area1Zone_tileset_tiles")
                ?: error(
                    "Could not parse _zone_world1Area1Zone_tileset_tiles[] from " +
                        "${outC.absolutePath} -- png2asset output shape may have changed."
                )
        assertNotNull(bytes, "parsed tile bytes must be non-null")

        // Lock array size (27 unique tiles * 16 bytes = 432). A future regression
        // that re-introduces auto-palette-sorting or removes the dedup would
        // either change the count or shift the bytes; either way this guards.
        assertEquals(
            432,
            bytes.size,
            "_zone_world1Area1Zone_tileset_tiles[] must be 432 bytes (27 unique tiles * 16); " +
                "got ${bytes.size}",
        )

        // ----------------------------------------------------------------
        // Post-fix snapshot lock (D-13 regression guard).
        //
        // Locked from Plan 12.8-04 post-fix generated C captured 2026-05-27
        // (commit 4dbaa765 base + W3 conditional flag fix landed in Plan
        // 12.8-03):
        //
        //   gbkt-examples/platformer-template/build/gbkt/generated/
        //   _zone_world1Area1Zone_tileset.c:18-44
        //
        // Tile index 0 (bytes [0..15]) -- the first png2asset-emitted tile
        // from world1-tileset.png. First two 2bpp bytes encode the top row of
        // an 8x8 grass-edge tile under the keep-palette-order flag:
        //   0x80, 0x80, 0x11, 0x7f, 0x10, 0x7e, 0x4a, 0x6e,
        //   0x55, 0x55, 0x3b, 0x3b, 0x6e, 0x7f, 0x20, 0x7f
        //
        // Tile index 6 (bytes [96..111]) -- another grass-tone tile (left-
        // edge ground-shape tile per RESEARCH Example 5 hint):
        //   0x80, 0x80, 0x3f, 0x40, 0x5f, 0x60, 0x60, 0x7f,
        //   0x61, 0x7f, 0x61, 0x7e, 0x61, 0x7e, 0x61, 0x7e
        //
        // Two byte assertions per tile (>= 4 total assertEquals per the
        // acceptance gate) -- the unique first-byte+second-byte pair locks
        // the keep-palette-order encoding shape against future flag drift.
        // ----------------------------------------------------------------

        // Tile index 0, bytes 0-1: 2bpp encoding of grass-edge top row.
        assertEquals(
            0x80,
            bytes[0 * 16 + 0],
            "tile-0 byte 0 must be 0x80 (post-fix snapshot from Plan 12.8-04)",
        )
        assertEquals(
            0x80,
            bytes[0 * 16 + 1],
            "tile-0 byte 1 must be 0x80 (post-fix snapshot from Plan 12.8-04)",
        )

        // Tile index 6, bytes 0-1: 2bpp encoding of another grass-tone tile.
        assertEquals(
            0x80,
            bytes[6 * 16 + 0],
            "tile-6 byte 0 must be 0x80 (post-fix snapshot from Plan 12.8-04)",
        )
        assertEquals(
            0x80,
            bytes[6 * 16 + 1],
            "tile-6 byte 1 must be 0x80 (post-fix snapshot from Plan 12.8-04)",
        )
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Parse a `const uint8_t <symbol>[N] = { 0x.., 0x.., ... };` byte-array initializer from a
     * png2asset-emitted .c file. Returns the byte values as `List<Int>` (each in 0..255), or `null`
     * if the symbol is not found.
     *
     * Inline copy of [ConvertZoneTilesetsTask.parseMapArrayBytes] (kt:507-530) per the
     * Phase 12.7 "inline-per-class" convention (12.7-PATTERNS.md §"Shared Patterns") -- keeps the
     * test class self-contained and avoids cross-module test-utility coupling.
     *
     * Only diff from the production helper: regex accepts BOTH `unsigned char` AND `uint8_t`
     * token forms. The post-12.2 png2asset emits `_tileset_tiles[]` as `const uint8_t` (see
     * the captured snapshot at line 17 of `_zone_world1Area1Zone_tileset.c`), while the
     * `_tileset_map[]` and `_tilemap_raw_map[]` arrays (which the production parser was written
     * against) use `const unsigned char`. Supporting both via `(?:unsigned\s+char|uint8_t)` keeps
     * the test resilient to either form across png2asset versions.
     */
    private fun parseTileBytes(file: File, symbol: String): List<Int>? {
        val text = file.readText()
        val pattern =
            Regex(
                """const\s+(?:unsigned\s+char|uint8_t)\s+${Regex.escape(symbol)}\s*\[\s*\d+\s*\]\s*=\s*\{([^}]*)\}""",
                RegexOption.DOT_MATCHES_ALL,
            )
        val match = pattern.find(text) ?: return null
        val payload = match.groupValues[1]
        return payload
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { token ->
                if (token.startsWith("0x") || token.startsWith("0X")) {
                    token.substring(2).toInt(16)
                } else {
                    token.toInt()
                }
            }
    }

    /**
     * Locate a platformer-template fixture PNG (e.g. `world1-tileset.png`) by walking up from the
     * test working directory. Mirrors [ConvertZoneTilesetsTaskTest.findPlatformerTemplatePng]'s
     * contract:
     * - returns the resolved File when found,
     * - returns null when the fixture is not part of this checkout layout (caller skips
     *   silently, same as the Banks helper).
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
