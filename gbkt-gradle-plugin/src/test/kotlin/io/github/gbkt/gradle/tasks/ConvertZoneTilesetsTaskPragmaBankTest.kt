/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import io.github.gbkt.gradle.internal.GbdkToolchain
import java.io.File
import kotlin.test.assertFailsWith
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

// =============================================================================
// PHASE 12.1 PLAN 01 TASK 2 — `#pragma bank N` on _zone_<id>_tilemap.c
//
// Locks the ConvertZoneTilesetsTask synthesizeScreenTilemap contract for
// pragma emission. Per D-01 (option b), the Gradle task now reads `bank`
// from each zoneTilesets metadata entry (added by Task 1) and prepends
// `#pragma bank N` on the synthesized `_zone_<id>_tilemap.c` file.
//
// Pragma placement is locked by the GBDK toolchain (CEmitter.kt:106-108
// contract): column 0, immediately after the auto-generated comment header,
// before `#include <stdint.h>`. This makes SDCC synthesize the
// `__bank__zone_<id>_tilemap` companion symbol that `BANK(...)` expands to,
// fixing Defect 2 (SDCC error 20 `Undefined identifier`).
//
// Tests below run WITHOUT png2asset (the .c output is hand-staged) so they
// pass regardless of GBDK availability in the test environment. They focus
// on the SYNTHESIZED `_zone_<id>_tilemap.c` file because that's what carries
// the new pragma — the upstream png2asset .c file is owned by png2asset and
// remains unchanged.
// =============================================================================

class ConvertZoneTilesetsTaskPragmaBankTest {

    @TempDir lateinit var tempDir: File

    /**
     * Test 1: For a metadata entry with `"bank": 2`, the generated `_zone_<id>_tilemap.c` file
     * carries `#pragma bank 2` at column 0 immediately after the auto-generated comment header.
     */
    @Test
    fun `tilemap file carries pragma bank 2 when metadata says bank=2`() {
        val assetDir = stageBanksFixtureOrSkip() ?: return
        val gbdkDir = GbdkToolchain.find(null)
        val png2asset = GbdkToolchain.getPng2asset(gbdkDir)
        if (!png2asset.exists()) return

        val cSourceDir = File(tempDir, "out").apply { mkdirs() }
        val metadataFile =
            stageMetadataWithBank(zoneId = "play_zone", path = "tiles/checker.png", bank = 2)

        runConvertTask(gbdkDir, assetDir, metadataFile, cSourceDir)

        val tilemapC = File(cSourceDir, "_zone_play_zone_tilemap.c")
        assertTrue(
            tilemapC.exists(),
            "Plan 12.1-01 Task 2: _zone_play_zone_tilemap.c must exist after " +
                "ConvertZoneTilesetsTask runs. cSourceDir contents: " +
                "${cSourceDir.listFiles()?.joinToString(", ") { it.name }}",
        )

        val content = tilemapC.readText()
        val pragmaLines =
            content.lines().filter { it.matches(Regex("^#pragma\\s+bank\\s+\\d+\\s*$")) }
        assertEquals(
            1,
            pragmaLines.size,
            "Plan 12.1-01 Task 2: tilemap.c MUST carry exactly one `#pragma bank N` " +
                "line at column 0. Got ${pragmaLines.size} matches: $pragmaLines. " +
                "File head:\n${content.take(600)}",
        )
        assertEquals(
            "#pragma bank 2",
            pragmaLines[0].trim(),
            "Plan 12.1-01 Task 2: pragma MUST emit the metadata-supplied bank " +
                "number (expected 2). Got: '${pragmaLines[0]}'",
        )
    }

    /**
     * Test 2: For a metadata entry with `"bank": 3`, the generated tilemap.c carries `#pragma bank
     * 3`. Locks that the bank value is propagated verbatim from metadata (no hardcoded constant).
     */
    @Test
    fun `tilemap file carries pragma bank 3 when metadata says bank=3`() {
        val assetDir = stageBanksFixtureOrSkip() ?: return
        val gbdkDir = GbdkToolchain.find(null)
        val png2asset = GbdkToolchain.getPng2asset(gbdkDir)
        if (!png2asset.exists()) return

        val cSourceDir = File(tempDir, "out").apply { mkdirs() }
        val metadataFile =
            stageMetadataWithBank(zoneId = "play_zone", path = "tiles/checker.png", bank = 3)

        runConvertTask(gbdkDir, assetDir, metadataFile, cSourceDir)

        val tilemapC = File(cSourceDir, "_zone_play_zone_tilemap.c")
        assertTrue(tilemapC.exists(), "_zone_play_zone_tilemap.c must exist")
        val content = tilemapC.readText()
        val pragmaLines =
            content.lines().filter { it.matches(Regex("^#pragma\\s+bank\\s+\\d+\\s*$")) }
        assertEquals(1, pragmaLines.size, "Expected exactly one pragma bank line")
        assertEquals(
            "#pragma bank 3",
            pragmaLines[0].trim(),
            "Plan 12.1-01 Task 2: bank number MUST come from metadata (expected 3). " +
                "Got: '${pragmaLines[0]}'",
        )
    }

    /**
     * Test 3: Pragma sits AFTER the auto-generated comment header and BEFORE the `#include
     * <stdint.h>` line. Locks position-in-file to match the CEmitter.kt:106-108 contract for banked
     * .c files.
     */
    @Test
    fun `pragma sits after comment header and before stdint include`() {
        val assetDir = stageBanksFixtureOrSkip() ?: return
        val gbdkDir = GbdkToolchain.find(null)
        val png2asset = GbdkToolchain.getPng2asset(gbdkDir)
        if (!png2asset.exists()) return

        val cSourceDir = File(tempDir, "out").apply { mkdirs() }
        val metadataFile =
            stageMetadataWithBank(zoneId = "play_zone", path = "tiles/checker.png", bank = 2)

        runConvertTask(gbdkDir, assetDir, metadataFile, cSourceDir)

        val tilemapC = File(cSourceDir, "_zone_play_zone_tilemap.c")
        val lines = tilemapC.readText().lines()

        // Locate the indices.
        val commentIdx = lines.indexOfFirst { it.startsWith("/* Auto-generated") }
        val pragmaIdx = lines.indexOfFirst { it.matches(Regex("^#pragma\\s+bank\\s+\\d+\\s*$")) }
        val stdintIdx = lines.indexOfFirst { it.trim() == "#include <stdint.h>" }

        assertTrue(commentIdx >= 0, "comment header must exist")
        assertTrue(pragmaIdx >= 0, "pragma bank line must exist")
        assertTrue(stdintIdx >= 0, "stdint include must exist")
        assertTrue(
            commentIdx < pragmaIdx,
            "Plan 12.1-01 Task 2: pragma MUST come AFTER comment header. " +
                "commentIdx=$commentIdx, pragmaIdx=$pragmaIdx",
        )
        assertTrue(
            pragmaIdx < stdintIdx,
            "Plan 12.1-01 Task 2: pragma MUST come BEFORE #include <stdint.h>. " +
                "pragmaIdx=$pragmaIdx, stdintIdx=$stdintIdx",
        )
    }

    /**
     * Test 4: If a metadata entry omits `bank`, the task throws an IllegalArgumentException whose
     * message names the zone id and Phase 12.1 D-01 wiring-gap diagnostic.
     */
    @Test
    fun `task fails fast when metadata entry omits bank field`() {
        val assetDir = stageBanksFixtureOrSkip() ?: return

        val metadataFile = File(tempDir, "game_metadata.json")
        metadataFile.writeText(
            """
            {
              "zoneTilesets": [
                {
                  "id": "no_bank_zone",
                  "path": "tiles/checker.png",
                  "sanitizedSymbol": "no_bank_zone",
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
                    // Fake GBDK path — the bank-missing guard fires BEFORE png2asset.
                    gbdkHome.set(File(tempDir, "fake-gbdk").apply { mkdirs() }.absolutePath)
                    assetDirectory.set(assetDir)
                    this.metadataFile.set(metadataFile)
                    this.cSourceDir.set(cSourceDir)
                }
                .get()

        val ex = assertFailsWith<IllegalArgumentException> { task.convertZoneTilesets() }
        val msg = ex.message ?: ""
        assertTrue(
            msg.contains("no_bank_zone"),
            "Plan 12.1-01 Task 2: error must name the zone id. Got: $msg",
        )
        assertTrue(
            msg.contains("bank"),
            "Plan 12.1-01 Task 2: error must mention 'bank' missing. Got: $msg",
        )
        assertTrue(
            msg.contains("12.1") || msg.contains("D-01"),
            "Plan 12.1-01 Task 2: error must name Phase 12.1 D-01 wiring gap. Got: $msg",
        )
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun stageBanksFixtureOrSkip(): File? {
        val bankFixture = findBanksCheckerPng() ?: return null
        val assetDir = File(tempDir, "res").apply { mkdirs() }
        val tilesDir = File(assetDir, "tiles").apply { mkdirs() }
        bankFixture.copyTo(File(tilesDir, "checker.png"), overwrite = true)
        return assetDir
    }

    private fun stageMetadataWithBank(zoneId: String, path: String, bank: Int): File {
        val metadataFile = File(tempDir, "game_metadata.json")
        metadataFile.writeText(
            """
            {
              "zoneTilesets": [
                {
                  "id": "$zoneId",
                  "path": "$path",
                  "sanitizedSymbol": "$zoneId",
                  "mapWidth": 20,
                  "mapHeight": 18,
                  "bank": $bank
                }
              ]
            }
            """
                .trimIndent()
        )
        return metadataFile
    }

    private fun runConvertTask(
        gbdkDir: File,
        assetDir: File,
        metadataFile: File,
        cSourceDir: File,
    ) {
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
    }

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
}
