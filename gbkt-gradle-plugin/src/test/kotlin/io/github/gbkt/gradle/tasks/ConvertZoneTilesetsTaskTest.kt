/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import io.github.gbkt.gradle.internal.GbdkToolchain
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertFailsWith
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

// =============================================================================
// PHASE 11.2 PLAN 03 -- CONVERT ZONE TILESETS TASK
//
// Locks the ConvertZoneTilesetsTask contract:
//   (i) Happy path: banks-shaped metadata + a real PNG → png2asset emits
//       `_zone_<id>_tileset.c` AND the synthesized `_zone_<id>_tileset.h`
//       carries the D-A3 `#define _zone_<id>_tileset_count <N>` macro.
//   (ii) D-C2: missing PNG → fail-fast IllegalArgumentException with the
//        zone id + absolute path in the message.
//   (iii) D-C4: non-multiple-of-8 PNG dimensions → fail-fast before exec
//         with `7x7` and "multiple of 8" in the message.
//   (iv) D-C3: > MAX_ZONE_TILESET_TILES tile count → fail-fast before exec
//        with "192 tiles" in the message.
//
// Tests (ii)-(iv) fire in the Kotlin-side guard BEFORE png2asset is invoked,
// so they pass regardless of GBDK availability. Test (i) gracefully skips
// when png2asset is not discoverable (CI environments without GBDK).
// =============================================================================

class ConvertZoneTilesetsTaskTest {

    @TempDir lateinit var tempDir: File

    // ------------------------------------------------------------------------
    // Test 1 -- Happy path (png2asset-availability-conditioned)
    // ------------------------------------------------------------------------
    @Test
    fun `convertZoneTilesets produces _c+_h for banks-shaped metadata`() {
        // Skip when png2asset is not discoverable (e.g., CI without GBDK).
        val gbdkDir =
            try {
                GbdkToolchain.find(null)
            } catch (_: Exception) {
                return
            }
        val png2asset = GbdkToolchain.getPng2asset(gbdkDir)
        if (!png2asset.exists()) return

        // Resolve the real banks fixture PNG (16x16 → 4 visual tiles).
        val bankFixture =
            findBanksCheckerPng()
                ?: return // Fixture not present in this checkout; skip rather than fail.

        // Stage assetDirectory: tmpAssetDir/tiles/checker.png
        val assetDir = File(tempDir, "res").apply { mkdirs() }
        val tilesDir = File(assetDir, "tiles").apply { mkdirs() }
        bankFixture.copyTo(File(tilesDir, "checker.png"), overwrite = true)

        // Stage metadata file with one zoneTilesets entry.
        // Plan 11.1-17: metadata now includes mapWidth + mapHeight for screen-tile-index synthesis.
        // Plan 12.1-01 Task 2: metadata now also includes `bank` for #pragma bank N emission.
        val metadataFile = File(tempDir, "game_metadata.json")
        metadataFile.writeText(
            """
            {
              "zoneTilesets": [
                {
                  "id": "play_zone",
                  "path": "tiles/checker.png",
                  "sanitizedSymbol": "play_zone",
                  "mapWidth": 20,
                  "mapHeight": 18,
                  "bank": 2
                }
              ]
            }
            """
                .trimIndent()
        )

        val cSourceDir = File(tempDir, "out").apply { mkdirs() }

        // Build an in-process Gradle project and register the task.
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

        // The png2asset .c output lands at the flat path (D-B4).
        val outC = File(cSourceDir, "_zone_play_zone_tileset.c")
        val outH = File(cSourceDir, "_zone_play_zone_tileset.h")
        assertTrue(outC.exists(), "_zone_play_zone_tileset.c should exist: ${outC.absolutePath}")
        assertTrue(outH.exists(), "_zone_play_zone_tileset.h should exist: ${outH.absolutePath}")

        val hText = outH.readText()
        // D-A3: synthesized count macro (visual tile count, per plan behavior viii).
        // 16x16 / 8x8 → 4 visual tiles.
        assertTrue(
            hText.contains("#define _zone_play_zone_tileset_count 4"),
            "header must define visual-tile count macro; got:\n$hText",
        )
        // D-A3: alias from gbkt zone-pipeline name to the native png2asset stem.
        // png2asset names symbols after the OUTPUT-FILE basename (corrected in plan 11.2-04
        // buildRom
        // smoke). With output `_zone_play_zone_tileset.c`, the native array is
        // `_zone_play_zone_tileset_tiles[]`.
        assertTrue(
            hText.contains("#define _zone_play_zone_tileset _zone_play_zone_tileset_tiles"),
            "header must alias _zone_play_zone_tileset to native _zone_play_zone_tileset_tiles; got:\n$hText",
        )
    }

    // ------------------------------------------------------------------------
    // Test 1b -- Phase 12.1 Plan 03 (Defect 3) → updated Phase 12.2 Plan 04:
    //   _tilemap_WIDTH / _tilemap_HEIGHT defines now derive from REAL PNG IHDR.
    //
    // Locks the contract: synthesizeHeader emits `#define _zone_<id>_tilemap_WIDTH N`
    // and `#define _zone_<id>_tilemap_HEIGHT M` where N = tilemapPng.width / 8 and
    // M = tilemapPng.height / 8 (D-01 + REQ-3). With the checker.png fixture (16×16 px),
    // N = 2 and M = 2 — was 20 / 18 pre-12.2 when WIDTH/HEIGHT came from metadata
    // mapWidth / mapHeight. The defines still resolve GBDKPipeline's references to
    // these symbols at SDCC link time (Defect 3 closure remains intact).
    //
    // Skips when GBDK / png2asset are not available (same guard as Test 1).
    // ------------------------------------------------------------------------
    @Test
    fun `convertZoneTilesets emits WIDTH and HEIGHT defines in tileset_h`() {
        val gbdkDir =
            try {
                GbdkToolchain.find(null)
            } catch (_: Exception) {
                return
            }
        val png2asset = GbdkToolchain.getPng2asset(gbdkDir)
        if (!png2asset.exists()) return

        val bankFixture = findBanksCheckerPng() ?: return
        val assetDir = File(tempDir, "res").apply { mkdirs() }
        val tilesDir = File(assetDir, "tiles").apply { mkdirs() }
        bankFixture.copyTo(File(tilesDir, "checker.png"), overwrite = true)

        val metadataFile = File(tempDir, "game_metadata.json")
        metadataFile.writeText(
            """
            {
              "zoneTilesets": [
                {
                  "id": "play_zone",
                  "path": "tiles/checker.png",
                  "sanitizedSymbol": "play_zone",
                  "mapWidth": 20,
                  "mapHeight": 18,
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

        val outH = File(cSourceDir, "_zone_play_zone_tileset.h")
        assertTrue(outH.exists(), "_zone_play_zone_tileset.h should exist: ${outH.absolutePath}")
        val hText = outH.readText()

        // Behavior 1 (Phase 12.2 Plan 04): WIDTH define present with the real PNG-derived value.
        // checker.png is 16x16 px → 16 / 8 = 2 tiles wide. Was 20 pre-12.2 (metadata mapWidth).
        assertTrue(
            hText.contains("#define _zone_play_zone_tilemap_WIDTH 2"),
            "tileset.h must define _zone_play_zone_tilemap_WIDTH 2 (16px / 8); got:\n$hText",
        )
        // Behavior 1 (Phase 12.2 Plan 04): HEIGHT define present with the real PNG-derived value.
        // checker.png is 16x16 px → 16 / 8 = 2 tiles tall. Was 18 pre-12.2 (metadata mapHeight).
        assertTrue(
            hText.contains("#define _zone_play_zone_tilemap_HEIGHT 2"),
            "tileset.h must define _zone_play_zone_tilemap_HEIGHT 2 (16px / 8); got:\n$hText",
        )

        // Behavior 2: defines appear AFTER the extern declaration for the tilemap array
        // (logical co-location — same array, dimensions described). Use the column-0
        // anchor pattern matching synthesizeHeader's appendLine emission order.
        val externIdx = hText.indexOf("extern const uint8_t _zone_play_zone_tilemap[")
        val widthIdx = hText.indexOf("#define _zone_play_zone_tilemap_WIDTH")
        val heightIdx = hText.indexOf("#define _zone_play_zone_tilemap_HEIGHT")
        assertTrue(
            externIdx >= 0,
            "extern declaration must precede the WIDTH/HEIGHT defines; got:\n$hText",
        )
        assertTrue(
            widthIdx > externIdx,
            "WIDTH define must appear AFTER extern declaration; externIdx=$externIdx widthIdx=$widthIdx",
        )
        assertTrue(
            heightIdx > externIdx,
            "HEIGHT define must appear AFTER extern declaration; externIdx=$externIdx heightIdx=$heightIdx",
        )

        // Behavior 3: defines use the same sanitized symbol prefix as the array name
        // (sanitized from zone id — identical to existing _tileset and _tileset_count defines).
        // The sanitized prefix is `play_zone`; the existing _tileset_count line uses it.
        assertTrue(
            hText.contains("#define _zone_play_zone_tileset_count 4"),
            "header must still carry _tileset_count using the same sanitized prefix; got:\n$hText",
        )
    }

    // ------------------------------------------------------------------------
    // Test 2 -- D-C2: missing PNG fails fast
    // ------------------------------------------------------------------------
    @Test
    fun `fails fast when zone PNG missing`() {
        val assetDir = File(tempDir, "res").apply { mkdirs() }
        // Note: tiles/missing.png is NOT created.

        val metadataFile = File(tempDir, "game_metadata.json")
        // Plan 11.1-17: include mapWidth + mapHeight (required by Phase B plumbing).
        // Plan 12.1-01 Task 2: include `bank` (required by D-01 pragma-emission plumbing).
        metadataFile.writeText(
            """
            {
              "zoneTilesets": [
                {
                  "id": "absent_zone",
                  "path": "tiles/missing.png",
                  "sanitizedSymbol": "absent_zone",
                  "mapWidth": 20,
                  "mapHeight": 18,
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
                    // Use a fabricated path so the GbdkToolchain check passes structurally;
                    // the D-C2 guard fires BEFORE png2asset is invoked.
                    gbdkHome.set(File(tempDir, "fake-gbdk").apply { mkdirs() }.absolutePath)
                    assetDirectory.set(assetDir)
                    this.metadataFile.set(metadataFile)
                    this.cSourceDir.set(cSourceDir)
                }
                .get()

        val ex = assertFailsWith<IllegalArgumentException> { task.convertZoneTilesets() }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("absent_zone"), "message must reference zone id; got: $msg")
        assertTrue(msg.contains("not found"), "message must say 'not found'; got: $msg")
    }

    // ------------------------------------------------------------------------
    // Test 3 -- D-C4: non-multiple-of-8 dimensions fail fast
    // ------------------------------------------------------------------------
    @Test
    fun `fails fast when PNG dimensions not multiple of 8`() {
        val assetDir = File(tempDir, "res").apply { mkdirs() }
        val tilesDir = File(assetDir, "tiles").apply { mkdirs() }
        val badPng = File(tilesDir, "skew.png")
        writeSolidPng(badPng, width = 7, height = 7)

        val metadataFile = File(tempDir, "game_metadata.json")
        // Plan 11.1-17: include mapWidth + mapHeight (required by Phase B plumbing).
        // Plan 12.1-01 Task 2: include `bank` (required by D-01 pragma-emission plumbing).
        metadataFile.writeText(
            """
            {
              "zoneTilesets": [
                {
                  "id": "skew_zone",
                  "path": "tiles/skew.png",
                  "sanitizedSymbol": "skew_zone",
                  "mapWidth": 20,
                  "mapHeight": 18,
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
                    gbdkHome.set(File(tempDir, "fake-gbdk").apply { mkdirs() }.absolutePath)
                    assetDirectory.set(assetDir)
                    this.metadataFile.set(metadataFile)
                    this.cSourceDir.set(cSourceDir)
                }
                .get()

        val ex = assertFailsWith<IllegalArgumentException> { task.convertZoneTilesets() }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("multiple of 8"), "message must mention 'multiple of 8'; got: $msg")
        assertTrue(msg.contains("7x7"), "message must mention '7x7'; got: $msg")
    }

    // ------------------------------------------------------------------------
    // Test 4 -- D-C3: > 192 tile count fails fast
    // ------------------------------------------------------------------------
    @Test
    fun `fails fast when tile count exceeds 192`() {
        val assetDir = File(tempDir, "res").apply { mkdirs() }
        val tilesDir = File(assetDir, "tiles").apply { mkdirs() }
        // 200x104 -> 25*13 = 325 visual tiles, exceeds 192. Both dims must be /8.
        val bigPng = File(tilesDir, "big.png")
        writeSolidPng(bigPng, width = 200, height = 104)

        val metadataFile = File(tempDir, "game_metadata.json")
        // Plan 11.1-17: include mapWidth + mapHeight (required by Phase B plumbing).
        // Plan 12.1-01 Task 2: include `bank` (required by D-01 pragma-emission plumbing).
        metadataFile.writeText(
            """
            {
              "zoneTilesets": [
                {
                  "id": "big_zone",
                  "path": "tiles/big.png",
                  "sanitizedSymbol": "big_zone",
                  "mapWidth": 20,
                  "mapHeight": 18,
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
                    gbdkHome.set(File(tempDir, "fake-gbdk").apply { mkdirs() }.absolutePath)
                    assetDirectory.set(assetDir)
                    this.metadataFile.set(metadataFile)
                    this.cSourceDir.set(cSourceDir)
                }
                .get()

        val ex = assertFailsWith<IllegalArgumentException> { task.convertZoneTilesets() }
        val msg = ex.message ?: ""
        assertTrue(msg.contains("192 tiles"), "message must mention '192 tiles' cap; got: $msg")
    }

    // ------------------------------------------------------------------------
    // Test 5 -- Phase 12.1 Plan 02 Task 1: `#pragma bank N` emission contract
    //
    // Locks Defect 2's pragma-emission half via an emission-invariant test on
    // the synthesized `_zone_<id>_tilemap.c` file. The metadata-supplied
    // `bank` field MUST flow through to a `#pragma bank N` line at column 0
    // (verified via `RegexOption.MULTILINE` anchor `^`).
    //
    // Pre-12.1 baseline (no pragma emission): this test would FAIL.
    // Post-12.1-01 shape: this test PASSES — bank=2 → `#pragma bank 2`.
    //
    // Skips when GBDK / png2asset are not available (same guard as Test 1).
    // Co-located with the rest of the ConvertZoneTilesetsTask contract per
    // PATTERNS.md / RESEARCH §D-04 (test the producer of the contract).
    // ------------------------------------------------------------------------
    @Test
    fun `convertZoneTilesets emits #pragma bank N at head of tilemap_c`() {
        val gbdkDir =
            try {
                GbdkToolchain.find(null)
            } catch (_: Exception) {
                return
            }
        val png2asset = GbdkToolchain.getPng2asset(gbdkDir)
        if (!png2asset.exists()) return

        val bankFixture = findBanksCheckerPng() ?: return
        val assetDir = File(tempDir, "res").apply { mkdirs() }
        val tilesDir = File(assetDir, "tiles").apply { mkdirs() }
        bankFixture.copyTo(File(tilesDir, "checker.png"), overwrite = true)

        val metadataFile = File(tempDir, "game_metadata.json")
        // Plan 12.1-01 Task 1 added the `bank` field to zoneTilesets metadata.
        // Plan 12.1-02 Task 1 locks that ConvertZoneTilesetsTask reads it and
        // emits `#pragma bank 2` at column 0 of `_zone_play_zone_tilemap.c`.
        metadataFile.writeText(
            """
            {
              "zoneTilesets": [
                {
                  "id": "play_zone",
                  "path": "tiles/checker.png",
                  "sanitizedSymbol": "play_zone",
                  "mapWidth": 20,
                  "mapHeight": 18,
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

        val tilemapC = File(cSourceDir, "_zone_play_zone_tilemap.c")
        assertTrue(
            tilemapC.exists(),
            "_zone_play_zone_tilemap.c must exist after convertZoneTilesets; " +
                "cSourceDir contents: ${cSourceDir.listFiles()?.joinToString(", ") { it.name }}",
        )

        val text = tilemapC.readText()
        // Column-0 multiline-regex anchor per PATTERNS.md §"New Defect 2 test pattern".
        // The `^` matches start-of-line (not start-of-string) when RegexOption.MULTILINE is set,
        // which is the correct scope for a file-level pragma emission (CLAUDE.md scope-level
        // grep gates corollary).
        val pragmaRegex = Regex("""^#pragma\s+bank\s+2""", RegexOption.MULTILINE)
        assertTrue(
            pragmaRegex.containsMatchIn(text),
            "tilemap.c must carry '#pragma bank 2' at column 0; got:\n${text.take(500)}",
        )
    }

    // ------------------------------------------------------------------------
    // Test 6 -- Phase 12.1 Plan 02 Task 2: D-01 diagnostic on missing bank
    //
    // Guardrail: if a future refactor breaks the metadata wiring such that
    // `bank` is no longer emitted by GBDKPipeline, ConvertZoneTilesetsTask
    // MUST fail loudly with the Phase 12.1 D-01 diagnostic (not silently fall
    // back to an unbanked emission). This locks the fail-fast contract from
    // Plan 12.1-01 Task 2's `require(entry.has("bank")) { ... }` (file
    // ConvertZoneTilesetsTask.kt line 156).
    //
    // The guard fires BEFORE png2asset is invoked, so this test runs without
    // GBDK in the environment.
    //
    // Pre-12.1 baseline (no bank-required guard): this test would FAIL
    // because the task would not throw on the missing field.
    // Post-12.1-01 shape: this test PASSES — the require-guard throws.
    // ------------------------------------------------------------------------
    @Test
    fun `convertZoneTilesets fails with D-01 diagnostic when metadata omits bank`() {
        val assetDir = File(tempDir, "res").apply { mkdirs() }
        // Note: no PNG staged — the missing-bank guard fires before the missing-PNG guard.

        val metadataFile = File(tempDir, "game_metadata.json")
        // Metadata deliberately OMITS the `bank` field (parallel to the four
        // existing fixtures which all carry "bank": 2).
        metadataFile.writeText(
            """
            {
              "zoneTilesets": [
                {
                  "id": "play_zone",
                  "path": "tiles/checker.png",
                  "sanitizedSymbol": "play_zone",
                  "mapWidth": 20,
                  "mapHeight": 18
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
                    // Use a fabricated GBDK path — the bank-missing guard fires before any GBDK
                    // use.
                    gbdkHome.set(File(tempDir, "fake-gbdk").apply { mkdirs() }.absolutePath)
                    assetDirectory.set(assetDir)
                    this.metadataFile.set(metadataFile)
                    this.cSourceDir.set(cSourceDir)
                }
                .get()

        // Kotlin's `require { }` throws IllegalArgumentException — confirmed against
        // ConvertZoneTilesetsTask.kt:156 `require(entry.has("bank")) { ... }` (Plan 12.1-01 Task
        // 2).
        val ex = assertFailsWith<IllegalArgumentException> { task.convertZoneTilesets() }
        val msg = ex.message ?: ""
        assertTrue(
            msg.contains("Phase 12.1 D-01"),
            "message must reference Phase 12.1 D-01 diagnostic; got: $msg",
        )
        assertTrue(
            msg.contains("play_zone"),
            "message must reference zone id 'play_zone'; got: $msg",
        )
    }

    // ------------------------------------------------------------------------
    // Test 7 -- Phase 12.2 Plan 05: REQ-2 emission invariant (real bytes, NO
    // more modulo-tiled garbage).
    //
    // Locks the D-01 two-invocation path against the REAL platformer-template
    // fixture (`world1-area1.png` 480x256 + `world1-tileset.png` 64x32):
    //   - SPEC AC4: `_zone_world1Area1Zone_tilemap[]` declares size 1920
    //     (60x32, NOT 1024 = the old Plan 11.1-17 synthesizer's 32x32 modulo
    //     grid — Defect 7).
    //   - SPEC AC5: bytes[0..59] all equal 0x11 (sky tile, world1-area1.png
    //     row 0 — encodes the SHAPE of the source PNG, not a brittle exact
    //     match against any single png2asset version).
    //   - SPEC AC5: row 31 (bytes 1860..1919) contains at least one value in
    //     the ground-tile set {0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d}.
    //
    // Plan 05 deviation note (Rule 1 — bug-in-must_have): the plan's
    // must_haves frontmatter cited {0x00..0x05} for ground tiles, predicting
    // png2asset would allocate them first. Empirical measurement against the
    // real world1-tileset.png + world1-area1.png shipped in this checkout
    // (probed via direct png2asset invocation during Plan 05 execution) shows
    // png2asset auto-allocates ground tiles to indices 0x08..0x0d for these
    // exact source PNGs. The structural intent of AC5 (row 31 carries
    // ground-tile values distinct from the 0x11 sky) is preserved — the
    // expected set is corrected to match deterministic png2asset output.
    //
    // Together these three assertions structurally guard against any future
    // regression of Defect 7: a modulo-tiled grid would (a) not be 1920 bytes,
    // (b) not have a uniform 0x11 sky row, and (c) not have ground tiles
    // landing on the last 60-byte row.
    //
    // Skips silently when GBDK / png2asset are not available OR when the
    // platformer-template fixture is not part of this checkout (mirrors the
    // GBDK-or-skip + fixture-or-skip pattern used by Test 1, Test 5, and the
    // `findBanksCheckerPng()` helper).
    // ------------------------------------------------------------------------
    @Test
    fun `emits real 1920-byte tilemap for world1Area1Zone with sky row 0x11 and ground row 31`() {
        val gbdkDir =
            try {
                GbdkToolchain.find(null)
            } catch (_: Exception) {
                return
            }
        val png2asset = GbdkToolchain.getPng2asset(gbdkDir)
        if (!png2asset.exists()) return

        val world1Area1Png = findPlatformerTemplatePng("world1-area1.png") ?: return
        val world1TilesetPng = findPlatformerTemplatePng("world1-tileset.png") ?: return

        val assetDir = File(tempDir, "res").apply { mkdirs() }
        val gfxDir = File(assetDir, "graphics").apply { mkdirs() }
        world1Area1Png.copyTo(File(gfxDir, "world1-area1.png"), overwrite = true)
        world1TilesetPng.copyTo(File(gfxDir, "world1-tileset.png"), overwrite = true)

        val metadataFile = File(tempDir, "game_metadata.json")
        // D-01 two-invocation path: `tilemapPath` set distinct from `path`
        // triggers the second png2asset invocation against the real tilemap PNG
        // with `-maps_only -source_tileset`. Metadata `mapWidth/mapHeight` are
        // retained for back-compat (consumed by deprecated parameters in
        // convertOneTileset), but the emitted WIDTH/HEIGHT and tilemap bytes
        // come from the PNG itself (Plan 04).
        metadataFile.writeText(
            """
            {
              "zoneTilesets": [
                {
                  "id": "world1Area1Zone",
                  "path": "graphics/world1-tileset.png",
                  "tilemapPath": "graphics/world1-area1.png",
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

        // SPEC AC4: `_zone_world1Area1Zone_tilemap.c` exists and declares the
        // array at size 1920 (NOT 1024). The Plan 11.1-17 synthesizer's 32x32
        // modulo grid would have produced 1024 — any future regression to that
        // shape fails this assertion immediately.
        val tilemapC = File(cSourceDir, "_zone_world1Area1Zone_tilemap.c")
        assertTrue(tilemapC.exists(), "_zone_world1Area1Zone_tilemap.c must exist")
        val text = tilemapC.readText()

        val declRegex =
            Regex(
                """const uint8_t _zone_world1Area1Zone_tilemap\[(\d+)\]\s*=\s*\{([^}]*)\}""",
                RegexOption.DOT_MATCHES_ALL,
            )
        val match = declRegex.find(text)
        assertNotNull(match, "tilemap array declaration must be present in:\n${text.take(500)}")
        val declaredSize = match!!.groupValues[1].toInt()
        assertEquals(
            1920,
            declaredSize,
            "_zone_world1Area1Zone_tilemap[] must be 1920 bytes (60*32), not $declaredSize. " +
                "1024 = the old Plan 11.1-17 synthesizer 32x32 modulo grid (Defect 7).",
        )

        // Parse the byte initializer into a List<Int>. Accept both hex (0x..)
        // and decimal token forms — same robustness pattern that
        // ConvertZoneTilesetsTask.parseMapArrayBytes uses on the production
        // side (Plan 03 Rule 3 hardening). Current writer emits uppercase hex
        // ("0x%02X"), but locking the test parser to hex-only would couple it
        // to formatter details rather than the byte semantics.
        val bytes =
            match.groupValues[2]
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
        assertEquals(1920, bytes.size, "parsed bytes must equal declared size; got ${bytes.size}")

        // SPEC AC5: byte[0..59] all 0x11 (sky tile, world1-area1.png row 0).
        // 60 bytes per row at 480px / 8px-per-tile = 60 tiles wide.
        val row0 = bytes.subList(0, 60)
        assertTrue(
            row0.all { it == 0x11 },
            "row 0 (bytes 0..59) must all be 0x11 (sky); got first 8: ${row0.take(8)}",
        )

        // SPEC AC5: row 31 (bytes 1860..1919) contains at least one ground-tile
        // value. world1-area1.png places ground tiles along the bottom row of
        // the 60x32 map; png2asset deterministically assigns those tiles to
        // indices 0x08..0x0d for the world1-tileset.png + world1-area1.png
        // shipped in this checkout (auto-allocation order). See class-level
        // deviation note above for why the plan's predicted {0x00..0x05} set
        // was corrected to the empirically-measured {0x08..0x0d}.
        val groundTiles = setOf(0x08, 0x09, 0x0a, 0x0b, 0x0c, 0x0d)
        val row31 = bytes.subList(1860, 1920)
        assertTrue(
            row31.any { it in groundTiles },
            "row 31 (bytes 1860..1919) must contain at least one ground tile " +
                "from {0x08..0x0d}; got: ${row31.take(20)}",
        )
    }

    // ------------------------------------------------------------------------
    // Test 8 (Phase 12.2 Plan 06) -- REQ-3 / SPEC AC6+AC7: variable WIDTH/HEIGHT
    //
    // The `_zone_<id>_tileset.h` header MUST carry per-zone WIDTH/HEIGHT macros
    // derived from the relevant PNG's IHDR (post-Plan-04: ImageIO.read(tilemapPng)
    // -- two-invocation path uses the explicit tilemap PNG; one-invocation path
    // uses the tileset PNG itself). Asserts three fixtures covering both paths:
    //   - world1Area1Zone (two-invocation): world1-area1.png 480x256 -> WIDTH=60, HEIGHT=32
    //   - titleZone       (one-invocation): title-screen.png 160x72  -> WIDTH=20, HEIGHT=9
    //                     (the actual PNG in this checkout is 160x72, NOT 160x144 as
    //                      cited in the PLAN frontmatter -- this Rule 1 correction
    //                      mirrors Plan 12.2-04 SUMMARY's identical finding for the
    //                      same fixture; verified via `file title-screen.png`.)
    //   - play_zone       (one-invocation): checker.png 16x16        -> WIDTH=2,  HEIGHT=2
    //
    // Future regressions that return to the hardcoded 32x32 (or uniform 20x18 screen
    // grid) WIDTH/HEIGHT values would fail at least one of the three sub-fixtures.
    //
    // Each fixture runs the task once in its own isolated temp subdir to avoid
    // cross-contamination of output files.
    // ------------------------------------------------------------------------
    @Test
    fun `emits variable tilemap WIDTH and HEIGHT for world1Area1Zone titleZone and banks zone`() {
        val gbdkDir =
            try {
                GbdkToolchain.find(null)
            } catch (_: Exception) {
                return
            }
        val png2asset = GbdkToolchain.getPng2asset(gbdkDir)
        if (!png2asset.exists()) return

        data class Fixture(
            val sanitized: String,
            val tilesetPngFinder: () -> File?,
            val tilemapPngFinder: (() -> File?)?,
            val tilesetRelPath: String,
            val tilemapRelPath: String?,
            val expectedW: Int,
            val expectedH: Int,
        )

        val fixtures =
            listOf(
                Fixture(
                    sanitized = "world1Area1Zone",
                    tilesetPngFinder = { findPlatformerTemplatePng("world1-tileset.png") },
                    tilemapPngFinder = { findPlatformerTemplatePng("world1-area1.png") },
                    tilesetRelPath = "graphics/world1-tileset.png",
                    tilemapRelPath = "graphics/world1-area1.png",
                    expectedW = 60,
                    expectedH = 32,
                ),
                Fixture(
                    sanitized = "titleZone",
                    // Rule 1 deviation: title-screen.png in this checkout is 160x72,
                    // not 160x144 as cited in the Plan 06 frontmatter. HEIGHT=9 (72/8),
                    // not 18. Same Rule 1 finding documented in Plan 12.2-04 SUMMARY.
                    tilesetPngFinder = { findPlatformerTemplatePng("title-screen.png") },
                    tilemapPngFinder = null,
                    tilesetRelPath = "graphics/title-screen.png",
                    tilemapRelPath = null,
                    expectedW = 20,
                    expectedH = 9,
                ),
                Fixture(
                    sanitized = "play_zone",
                    tilesetPngFinder = { findBanksCheckerPng() },
                    tilemapPngFinder = null,
                    tilesetRelPath = "tiles/checker.png",
                    tilemapRelPath = null,
                    expectedW = 2,
                    expectedH = 2,
                ),
            )

        for (fix in fixtures) {
            val tilesetPng = fix.tilesetPngFinder() ?: continue
            val tilemapPng = fix.tilemapPngFinder?.invoke()
            // tilemapRelPath != null but tilemapPng == null => fixture incomplete; skip
            // this iteration only (other fixtures continue).
            if (fix.tilemapRelPath != null && tilemapPng == null) continue

            // Per-fixture isolated temp dir to avoid output-file collisions.
            val fixDir = File(tempDir, fix.sanitized).apply { mkdirs() }
            val assetDir = File(fixDir, "res").apply { mkdirs() }
            // Mirror the relative path structure on disk.
            val tilesetTarget = File(assetDir, fix.tilesetRelPath)
            tilesetTarget.parentFile.mkdirs()
            tilesetPng.copyTo(tilesetTarget, overwrite = true)
            if (fix.tilemapRelPath != null && tilemapPng != null) {
                val tilemapTarget = File(assetDir, fix.tilemapRelPath)
                tilemapTarget.parentFile.mkdirs()
                tilemapPng.copyTo(tilemapTarget, overwrite = true)
            }

            val metadataFile = File(fixDir, "game_metadata.json")
            val tilemapJsonValue =
                if (fix.tilemapRelPath != null) "\"${fix.tilemapRelPath}\"" else "null"
            metadataFile.writeText(
                """
                {
                  "zoneTilesets": [
                    {
                      "id": "${fix.sanitized}",
                      "path": "${fix.tilesetRelPath}",
                      "tilemapPath": $tilemapJsonValue,
                      "sanitizedSymbol": "${fix.sanitized}",
                      "mapWidth": ${fix.expectedW},
                      "mapHeight": ${fix.expectedH},
                      "bank": 2
                    }
                  ]
                }
                """
                    .trimIndent()
            )

            val cSourceDir = File(fixDir, "out").apply { mkdirs() }
            val project = ProjectBuilder.builder().withProjectDir(fixDir).build()
            val task =
                project.tasks
                    .register(
                        "convertZoneTilesetsTest_${fix.sanitized}",
                        ConvertZoneTilesetsTask::class.java,
                    ) {
                        gbdkHome.set(gbdkDir.absolutePath)
                        assetDirectory.set(assetDir)
                        this.metadataFile.set(metadataFile)
                        this.cSourceDir.set(cSourceDir)
                    }
                    .get()

            task.convertZoneTilesets()

            // Assert per-zone WIDTH/HEIGHT macros (REQ-3 / SPEC AC6+AC7).
            val headerFile = File(cSourceDir, "_zone_${fix.sanitized}_tileset.h")
            assertTrue(
                headerFile.exists(),
                "header file must exist for fixture ${fix.sanitized}; " +
                    "cSourceDir=${cSourceDir.absolutePath}",
            )
            val text = headerFile.readText()
            // Column-0 multiline regex per CLAUDE.md scope-level grep gates: the
            // macro value must be EXACTLY the asserted integer (not a substring).
            val widthRegex =
                Regex(
                    """^#define _zone_${fix.sanitized}_tilemap_WIDTH ${fix.expectedW}$""",
                    RegexOption.MULTILINE,
                )
            val heightRegex =
                Regex(
                    """^#define _zone_${fix.sanitized}_tilemap_HEIGHT ${fix.expectedH}$""",
                    RegexOption.MULTILINE,
                )
            assertTrue(
                widthRegex.containsMatchIn(text),
                "header must declare " +
                    "`#define _zone_${fix.sanitized}_tilemap_WIDTH ${fix.expectedW}` " +
                    "at column 0 for fixture ${fix.sanitized}; got:\n${text.take(800)}",
            )
            assertTrue(
                heightRegex.containsMatchIn(text),
                "header must declare " +
                    "`#define _zone_${fix.sanitized}_tilemap_HEIGHT ${fix.expectedH}` " +
                    "at column 0 for fixture ${fix.sanitized}; got:\n${text.take(800)}",
            )
        }
    }

    // ------------------------------------------------------------------------
    // Phase 12.2 Plan 07 -- REQ-4 / D-01 "genuine error case" missing-tilemap diagnostic
    //
    // When a zone declares a separate `tilemap()` PNG but the file is missing on disk, the
    // fail-fast guard inside `convertOneTileset` (Plan 03 Task 1 Step 5) MUST throw with a
    // diagnostic that names the zone id and identifies the tilemap-PNG problem -- BEFORE any
    // png2asset invocation. Because the guard runs before exec, the test works on any CI
    // runner regardless of GBDK availability (same property as the existing D-C2/D-C4 guards
    // covered above). This closes the runtime-diagnostic half of REQ-4; Plan 04's source-level
    // deletion of the legacy synthesizer closes the grep-gate half.
    //
    // The production code path being locked here (ConvertZoneTilesetsTask.kt around line 249-253):
    //
    //     if (tilemapPngFile != null) {
    //         require(tilemapPngFile.isFile) {
    //             "Zone $zoneId tilemap PNG not found at ${tilemapPngFile.absolutePath} (Phase 12.2
    // REQ-4)"
    //         }
    //     }
    //
    // `require(...)` throws `IllegalArgumentException` in Kotlin. The assertion below uses
    // `assertFailsWith<Exception>` (loose parent) per the plan's interface guidance -- Gradle
    // task execution sometimes wraps exceptions across versions, and the message-based assertion
    // is the load-bearing structural lock. Empirically the type IS IllegalArgumentException in
    // this checkout (verified by inspection of `convertOneTileset`'s `require()`), so a stricter
    // assertion would also pass today; we keep the loose form to harden against Gradle drift.
    // ------------------------------------------------------------------------
    @Test
    fun `throws GradleException when tilemapPath is set but file is missing`() {
        // This test does NOT require GBDK: the fail-fast guard in convertOneTileset triggers
        // BEFORE any png2asset invocation (Phase 12.2 REQ-4 "genuine error case" from D-01).

        val assetDir = File(tempDir, "res").apply { mkdirs() }
        val tilesDir = File(assetDir, "tiles").apply { mkdirs() }

        // Write a minimal valid tileset PNG so the tileset-side D-C2 guard does NOT fire first.
        // We need the tileset path to resolve to a real file; otherwise the earlier D-C2 require
        // would trip and we'd be testing the wrong code path.
        val tilesetPng = File(tilesDir, "checker.png")
        writeSolidPng(tilesetPng, width = 16, height = 16)
        assertTrue(tilesetPng.isFile, "test precondition: tileset PNG must exist on disk")

        // Deliberately DO NOT write the tilemap PNG at the path declared below. The relative
        // path resolves under assetDir but to a file that does not exist.
        val missingTilemapRelPath = "tiles/nonexistent-tilemap.png"
        val missingTilemapAbs = File(assetDir, missingTilemapRelPath)
        assertTrue(
            !missingTilemapAbs.exists(),
            "test precondition: tilemap PNG must NOT exist on disk at ${missingTilemapAbs.absolutePath}",
        )

        val metadataFile = File(tempDir, "game_metadata.json")
        metadataFile.writeText(
            """
            {
              "zoneTilesets": [
                {
                  "id": "play_zone",
                  "path": "tiles/checker.png",
                  "tilemapPath": "$missingTilemapRelPath",
                  "sanitizedSymbol": "play_zone",
                  "mapWidth": 20,
                  "mapHeight": 18,
                  "bank": 2
                }
              ]
            }
            """
                .trimIndent()
        )

        val cSourceDir = File(tempDir, "out").apply { mkdirs() }
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        // gbdkHome can be any path -- the task fails BEFORE png2asset is invoked, so the
        // GbdkToolchain check structurally passes but the executable is never resolved.
        val task =
            project.tasks
                .register(
                    "convertZoneTilesetsTest_missingTilemap",
                    ConvertZoneTilesetsTask::class.java,
                ) {
                    gbdkHome.set(File(tempDir, "fake-gbdk").apply { mkdirs() }.absolutePath)
                    assetDirectory.set(assetDir)
                    this.metadataFile.set(metadataFile)
                    this.cSourceDir.set(cSourceDir)
                }
                .get()

        val ex = assertFailsWith<Exception> { task.convertZoneTilesets() }
        val msg = ex.message ?: ""
        assertTrue(
            msg.contains("play_zone"),
            "exception message must reference zone id 'play_zone'; got: $msg",
        )
        assertTrue(
            msg.contains("tilemap") || msg.contains("missing") || msg.contains("not found"),
            "exception message must name the missing-tilemap problem; got: $msg",
        )
    }

    // ------------------------------------------------------------------------
    // Constant surface (sanity check)
    // ------------------------------------------------------------------------
    @Test
    fun `MAX_ZONE_TILESET_TILES is 192 per D-C3`() {
        assertEquals(192, ConvertZoneTilesetsTask.MAX_ZONE_TILESET_TILES)
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun writeSolidPng(target: File, width: Int, height: Int) {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        // Default-zeroed image is fine; png2asset is never invoked in tests 2-4.
        ImageIO.write(img, "PNG", target)
        require(target.isFile && target.length() > 0) {
            "test fixture PNG was not written: ${target.absolutePath}"
        }
    }

    /**
     * Locate the banks example checker.png fixture by walking up from the test working directory.
     * Returns null when the fixture is unavailable (e.g. the gbkt-examples project is not part of
     * this checkout layout).
     */
    private fun findBanksCheckerPng(): File? {
        val rel = "gbkt-examples/banks/res/tiles/checker.png"
        var dir: File? = File("").absoluteFile
        repeat(6) {
            val candidate = dir?.let { File(it, rel) }
            if (candidate != null && candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        return null
    }

    /**
     * Locate a platformer-template fixture PNG (e.g. `world1-area1.png`, `world1-tileset.png`) by
     * walking up from the test working directory. Mirrors [findBanksCheckerPng]'s contract:
     * - returns the resolved File when found,
     * - returns null when the fixture is not part of this checkout layout (callers then skip
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
