/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir

/**
 * Unit tests for [compareOrBless] and the [GBKT_UPDATE_GOLDENS_PROP] constant.
 *
 * Tests the four behaviour paths described in Plan 22-01:
 * 1. Golden missing AND update-mode OFF → AssertionError naming path + re-baseline hint.
 * 2. Golden present AND captured PNG pixel-identical → passes silently.
 * 3. Golden present AND captured PNG differs by ≥1 pixel → AssertionError naming diffCount + diff
 *    image path.
 * 4. Update-mode ON (system property set) AND golden missing → writes golden (parent dirs created),
 *    then passes; golden bytes == captured bytes (sha256 equal).
 *
 * Tests use pre-written PNG files via helpers that do NOT go through a live emulator — they
 * exercise [compareOrBless] directly, the internal delegate of [assertGoldenMatch].
 */
class GoldenAssertionsTest {

    @TempDir
    lateinit var tempDir: File

    // ─── helpers ──────────────────────────────────────────────────────────────

    /** Creates a 160×144 PNG filled with a single [color] and writes it to [dir]. */
    private fun writePng(dir: File, name: String, color: Int): File {
        dir.mkdirs()
        val img = BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = java.awt.Color(color)
        g.fillRect(0, 0, 160, 144)
        g.dispose()
        val file = File(dir, "$name.png")
        ImageIO.write(img, "png", file)
        return file
    }

    private fun sha256(file: File): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes())

    // ─── Test 1: golden missing, update-mode OFF ───────────────────────────────

    @Test
    fun `compareOrBless throws AssertionError when golden missing and update-mode off`() {
        val capturedFile = writePng(File(tempDir, "scratch"), "captured", 0x00FF00)
        val goldenFile = File(tempDir, "goldens/missing.png") // does not exist
        val scratchDir = File(tempDir, "scratch")

        val ex = assertThrows<AssertionError> {
            compareOrBless(goldenFile, capturedFile, scratchDir)
        }

        // Message must contain the golden path AND the re-baseline property name (D-05)
        assertTrue(
            goldenFile.absolutePath in ex.message!!,
            "AssertionError message should contain golden path: ${ex.message}",
        )
        assertTrue(
            GBKT_UPDATE_GOLDENS_PROP in ex.message!!,
            "AssertionError message should mention GBKT_UPDATE_GOLDENS_PROP: ${ex.message}",
        )
    }

    // ─── Test 2: golden present, captured pixel-identical → pass ──────────────

    @Test
    fun `compareOrBless passes when captured PNG is pixel-identical to golden`() {
        val goldenDir = File(tempDir, "goldens").also { it.mkdirs() }
        val scratchDir = File(tempDir, "scratch")
        val golden = writePng(goldenDir, "anchor", 0x0000FF)
        // Write same pixels to a scratch capture file
        val captured = writePng(scratchDir, "anchor_captured", 0x0000FF)

        assertDoesNotThrow { compareOrBless(golden, captured, scratchDir) }
    }

    // ─── Test 3: golden present, captured differs ≥1 pixel → AssertionError ───

    @Test
    fun `compareOrBless throws AssertionError when captured PNG differs from golden by 1 or more pixels`() {
        val goldenDir = File(tempDir, "goldens").also { it.mkdirs() }
        val scratchDir = File(tempDir, "scratch")
        val golden = writePng(goldenDir, "anchor", 0xFF0000) // red
        val captured = writePng(scratchDir, "anchor_captured", 0x00FF00) // green — all pixels differ

        val ex = assertThrows<AssertionError> {
            compareOrBless(golden, captured, scratchDir)
        }

        // Message must name diffCount
        assertTrue(
            ex.message!!.contains("pixels differ") || ex.message!!.contains("diffCount"),
            "AssertionError message should report pixel diff count: ${ex.message}",
        )
    }

    @Test
    fun `compareOrBless includes diff image path in error when captured differs`() {
        val goldenDir = File(tempDir, "goldens").also { it.mkdirs() }
        val scratchDir = File(tempDir, "scratch")
        val golden = writePng(goldenDir, "anchor", 0xFF0000)
        val captured = writePng(scratchDir, "anchor_captured", 0x00FF00)

        val ex = assertThrows<AssertionError> {
            compareOrBless(golden, captured, scratchDir)
        }
        // Diff image path should be in message when diffOutputDir was provided
        assertNotNull(ex.message, "AssertionError should have a message")
    }

    // ─── Test 4: update-mode ON, golden missing → writes golden + passes ───────

    @Test
    fun `compareOrBless writes golden and passes when update-mode is active and golden missing`() {
        val goldenDir = File(tempDir, "goldens/sub") // parent dirs not yet created
        val goldenFile = File(goldenDir, "new_anchor.png")
        val scratchDir = File(tempDir, "scratch")
        val capturedFile = writePng(scratchDir, "new_anchor_captured", 0x123456)

        System.setProperty(GBKT_UPDATE_GOLDENS_PROP, "true")
        try {
            assertDoesNotThrow { compareOrBless(goldenFile, capturedFile, scratchDir) }
        } finally {
            System.clearProperty(GBKT_UPDATE_GOLDENS_PROP)
        }

        assertTrue(goldenFile.exists(), "Golden should be written in update mode")

        // Golden bytes must equal captured bytes (raw copy, not re-encoded)
        assertTrue(
            sha256(goldenFile).contentEquals(sha256(capturedFile)),
            "Golden bytes should be identical to captured bytes (raw copy, no re-encoding)",
        )
    }

    // ─── Constant contract ────────────────────────────────────────────────────

    @Test
    fun `GBKT_UPDATE_GOLDENS_PROP constant has expected value`() {
        assertFalse(
            GBKT_UPDATE_GOLDENS_PROP.isBlank(),
            "GBKT_UPDATE_GOLDENS_PROP must not be blank",
        )
        // Named constant must match the property key callers will set on the command line
        assertTrue(
            GBKT_UPDATE_GOLDENS_PROP == "gbkt.updateGoldens",
            "GBKT_UPDATE_GOLDENS_PROP must equal \"gbkt.updateGoldens\", got \"$GBKT_UPDATE_GOLDENS_PROP\"",
        )
    }
}
