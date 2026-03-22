/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AgentSessionConfigTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `discoverFiles finds noi and metadata`() {
        // Create standard layout: build/gbkt/output/test.gb + test.noi + ../generated/game_metadata.json
        val outputDir = File(tempDir, "build/gbkt/output").also { it.mkdirs() }
        val generatedDir = File(tempDir, "build/gbkt/generated").also { it.mkdirs() }
        val rom = File(outputDir, "test.gb").also { it.writeBytes(ByteArray(64)) }
        File(outputDir, "test.noi").also { it.writeText("DEF _score 00:C100\n") }
        File(generatedDir, "game_metadata.json").also {
            it.writeText("""{"scenes":{},"actors":[]}""")
        }

        val config = AgentSessionConfig.discoverFiles(rom)

        assertNotNull(config.symFile)
        assertEquals("test.noi", config.symFile!!.name)
        assertNotNull(config.metadataFile)
        assertEquals("game_metadata.json", config.metadataFile!!.name)
        assertNotNull(config.sourceMapsDir)
    }

    @Test
    fun `discoverFiles handles missing files`() {
        val outputDir = File(tempDir, "build/gbkt/output").also { it.mkdirs() }
        val rom = File(outputDir, "test.gb").also { it.writeBytes(ByteArray(64)) }

        val config = AgentSessionConfig.discoverFiles(rom)

        assertNull(config.symFile)
        assertNull(config.metadataFile)
    }

    @Test
    fun `metadataFile field passes through`() {
        val rom = File(tempDir, "test.gb").also { it.writeBytes(ByteArray(64)) }
        val metaFile = File(tempDir, "meta.json").also { it.writeText("{}") }

        val config = AgentSessionConfig(
            romFile = rom,
            metadataFile = metaFile,
        )

        assertEquals(metaFile, config.metadataFile)
    }

    @Test
    fun `discoverFiles prefers noi over sym when both exist`() {
        val outputDir = File(tempDir, "build/gbkt/output").also { it.mkdirs() }
        val rom = File(outputDir, "test.gb").also { it.writeBytes(ByteArray(64)) }
        File(outputDir, "test.noi").also { it.writeText("DEF _score 00:C100\n") }
        File(outputDir, "test.sym").also { it.writeText("; sym file\n") }

        val config = AgentSessionConfig.discoverFiles(rom)

        assertNotNull(config.symFile)
        assertEquals("test.noi", config.symFile!!.name, "Should prefer .noi over .sym")
    }

    @Test
    fun `discoverFiles falls back to sym when noi missing`() {
        val outputDir = File(tempDir, "build/gbkt/output").also { it.mkdirs() }
        val rom = File(outputDir, "test.gb").also { it.writeBytes(ByteArray(64)) }
        File(outputDir, "test.sym").also { it.writeText("; sym file\n") }

        val config = AgentSessionConfig.discoverFiles(rom)

        assertNotNull(config.symFile)
        assertEquals("test.sym", config.symFile!!.name, "Should fall back to .sym when .noi missing")
    }

    @Test
    fun `discoverFiles handles ROM outside standard layout`() {
        val rom = File(tempDir, "mygame.gb").also { it.writeBytes(ByteArray(64)) }

        val config = AgentSessionConfig.discoverFiles(rom)

        assertNull(config.symFile, "symFile should be null for ROM outside standard layout")
        assertNull(config.metadataFile, "metadataFile should be null for ROM outside standard layout")
    }
}
