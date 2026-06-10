/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.test

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [GameDiscovery] path resolution without requiring a real ROM.
 *
 * Uses temporary directories to simulate the standard Gradle output layout.
 */
class GameDiscoveryTest {

    @Test
    fun `configForGame returns null when ROM file does not exist`() {
        val result = GameDiscovery.configForGame("nonexistent-game-xyz-abc")
        assertNull(result, "configForGame should return null when ROM does not exist")
    }

    @Test
    fun `configForGame resolves correct paths when ROM exists`() {
        val tempDir = createTempDirectory("gbkt-test-discovery").toFile()
        try {
            // Create standard layout: build/gbkt/output/GAMENAME.gb
            val outputDir = File(tempDir, "build/gbkt/output")
            outputDir.mkdirs()
            val romFile = File(outputDir, "mygame.gb")
            romFile.writeText("ROM_BYTES")

            val result = GameDiscovery.configForGame("mygame", projectRoot = tempDir)
            assertNotNull(result, "configForGame should return config when ROM exists")
            assertEquals(romFile.canonicalPath, result.romFile.canonicalPath)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `configForGame uses build-gbkt-test-failures as default screenshot dir`() {
        val tempDir = createTempDirectory("gbkt-test-discovery").toFile()
        try {
            val outputDir = File(tempDir, "build/gbkt/output")
            outputDir.mkdirs()
            val romFile = File(outputDir, "mygame.gb")
            romFile.writeText("ROM_BYTES")

            val result = GameDiscovery.configForGame("mygame", projectRoot = tempDir)
            assertNotNull(result)
            assertTrue(
                result.screenshotDir.path.contains("test-failures"),
                "Default screenshot dir should contain 'test-failures', got: ${result.screenshotDir}",
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanForBuiltRoms returns empty list when no output dirs exist`() {
        val tempDir = createTempDirectory("gbkt-test-scan-empty").toFile()
        try {
            val results = GameDiscovery.scanForBuiltRoms(tempDir)
            assertTrue(results.isEmpty(), "Should return empty list when no ROMs exist")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanForBuiltRoms detects standalone ROM layout`() {
        val tempDir = createTempDirectory("gbkt-test-scan-standalone").toFile()
        try {
            // Standalone layout: build/gbkt/output/GAMENAME.gb
            val outputDir = File(tempDir, "build/gbkt/output")
            outputDir.mkdirs()
            File(outputDir, "pong.gb").writeText("ROM")

            val results = GameDiscovery.scanForBuiltRoms(tempDir)
            assertEquals(1, results.size, "Should detect one standalone ROM")
            assertEquals("pong", results[0].name)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanForBuiltRoms detects multi-game example layout`() {
        val tempDir = createTempDirectory("gbkt-test-scan-multi").toFile()
        try {
            // Multi-game layout: gbkt-examples/GAME/build/gbkt/output/GAME.gb
            val pongOutput = File(tempDir, "gbkt-examples/pong/build/gbkt/output")
            pongOutput.mkdirs()
            File(pongOutput, "pong.gb").writeText("ROM")

            val breakoutOutput = File(tempDir, "gbkt-examples/breakout/build/gbkt/output")
            breakoutOutput.mkdirs()
            File(breakoutOutput, "breakout.gb").writeText("ROM")

            val results = GameDiscovery.scanForBuiltRoms(tempDir)
            assertEquals(2, results.size, "Should detect two example ROMs")
            val names = results.map { it.name }.toSet()
            assertTrue("pong" in names, "pong should be in results")
            assertTrue("breakout" in names, "breakout should be in results")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanForBuiltRoms detects metadata presence`() {
        val tempDir = createTempDirectory("gbkt-test-scan-meta").toFile()
        try {
            // Standalone with metadata
            val outputDir = File(tempDir, "build/gbkt/output")
            outputDir.mkdirs()
            File(outputDir, "mygame.gb").writeText("ROM")

            val generatedDir = File(tempDir, "build/gbkt/generated")
            generatedDir.mkdirs()
            File(generatedDir, "game_metadata.json").writeText("{}")

            val results = GameDiscovery.scanForBuiltRoms(tempDir)
            assertEquals(1, results.size)
            assertTrue(results[0].hasMetadata, "Should detect metadata file presence")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `scanForBuiltRoms results are sorted by name`() {
        val tempDir = createTempDirectory("gbkt-test-scan-sorted").toFile()
        try {
            val examplesDir = File(tempDir, "gbkt-examples")
            for (name in listOf("zebra", "alpha", "mango")) {
                val dir = File(examplesDir, "$name/build/gbkt/output")
                dir.mkdirs()
                File(dir, "$name.gb").writeText("ROM")
            }

            val results = GameDiscovery.scanForBuiltRoms(tempDir)
            assertEquals(listOf("alpha", "mango", "zebra"), results.map { it.name })
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
