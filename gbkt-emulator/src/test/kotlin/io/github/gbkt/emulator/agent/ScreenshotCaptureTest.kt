/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Unit tests for [ScreenshotCapture].
 *
 * Verifies PNG file creation, JSON sidecar structure, variable snapshot, and input validation.
 */
class ScreenshotCaptureTest {

    @TempDir
    lateinit var tempDir: File

    // ── PNG file creation ──────────────────────────────────────────────────────

    @Test
    fun `capture creates PNG file at correct path`() {
        val frameBuffer = IntArray(160 * 144) { 0x00FF0000 } // All red
        val pngFile = ScreenshotCapture.capture(
            frameBuffer = frameBuffer,
            label = "testScene",
            frameNumber = 42,
            outputDir = tempDir,
        )
        assertTrue(pngFile.exists(), "PNG file should exist at ${pngFile.absolutePath}")
        assertEquals("testScene_frame42.png", pngFile.name)
    }

    @Test
    fun `captured PNG has correct 160x144 dimensions`() {
        val frameBuffer = IntArray(160 * 144) { 0x0000FF00 } // All green
        val pngFile = ScreenshotCapture.capture(
            frameBuffer = frameBuffer,
            label = "dimensions",
            frameNumber = 1,
            outputDir = tempDir,
        )
        val img: BufferedImage = ImageIO.read(pngFile)
        assertEquals(160, img.width, "PNG width should be 160")
        assertEquals(144, img.height, "PNG height should be 144")
    }

    // ── JSON sidecar ──────────────────────────────────────────────────────────

    @Test
    fun `capture creates JSON sidecar at correct path`() {
        val frameBuffer = IntArray(160 * 144)
        ScreenshotCapture.capture(
            frameBuffer = frameBuffer,
            label = "sidecar",
            frameNumber = 10,
            outputDir = tempDir,
        )
        val jsonFile = File(tempDir, "sidecar_frame10.json")
        assertTrue(jsonFile.exists(), "JSON sidecar should exist at ${jsonFile.absolutePath}")
    }

    @Test
    fun `JSON sidecar contains required fields`() {
        val frameBuffer = IntArray(160 * 144)
        val beforeCapture = System.currentTimeMillis()
        ScreenshotCapture.capture(
            frameBuffer = frameBuffer,
            label = "metadata",
            frameNumber = 99,
            outputDir = tempDir,
        )
        val afterCapture = System.currentTimeMillis()

        val jsonFile = File(tempDir, "metadata_frame99.json")
        val json = JSONObject(jsonFile.readText())

        assertEquals(99, json.getInt("frameNumber"))
        assertEquals("metadata", json.getString("label"))
        val capturedAt = json.getLong("capturedAt")
        assertTrue(capturedAt >= beforeCapture, "capturedAt should be >= time before capture")
        assertTrue(capturedAt <= afterCapture, "capturedAt should be <= time after capture")
        assertNotNull(json.getJSONObject("variables"), "variables field should exist")
    }

    @Test
    fun `variable snapshot appears in JSON sidecar`() {
        val frameBuffer = IntArray(160 * 144)
        ScreenshotCapture.capture(
            frameBuffer = frameBuffer,
            label = "vars",
            frameNumber = 5,
            outputDir = tempDir,
            variableSnapshot = mapOf("score" to 42, "lives" to 3),
        )

        val json = JSONObject(File(tempDir, "vars_frame5.json").readText())
        val variables = json.getJSONObject("variables")
        assertEquals(42, variables.getInt("score"))
        assertEquals(3, variables.getInt("lives"))
    }

    @Test
    fun `empty variable snapshot produces empty JSON variables object`() {
        val frameBuffer = IntArray(160 * 144)
        ScreenshotCapture.capture(
            frameBuffer = frameBuffer,
            label = "empty",
            frameNumber = 0,
            outputDir = tempDir,
            variableSnapshot = emptyMap(),
        )

        val json = JSONObject(File(tempDir, "empty_frame0.json").readText())
        val variables = json.getJSONObject("variables")
        assertEquals(0, variables.length(), "variables should be empty JSON object")
    }

    // ── Input validation ───────────────────────────────────────────────────────

    @Test
    fun `wrong-size frame buffer throws IllegalArgumentException`() {
        val badBuffer = IntArray(100) // Not 160*144 = 23040
        assertThrows<IllegalArgumentException> {
            ScreenshotCapture.capture(
                frameBuffer = badBuffer,
                label = "bad",
                frameNumber = 1,
                outputDir = tempDir,
            )
        }
    }

    // ── Directory creation ─────────────────────────────────────────────────────

    @Test
    fun `capture creates outputDir if it does not exist`() {
        val nestedDir = File(tempDir, "nested/output/dir")
        val frameBuffer = IntArray(160 * 144)
        val pngFile = ScreenshotCapture.capture(
            frameBuffer = frameBuffer,
            label = "nested",
            frameNumber = 1,
            outputDir = nestedDir,
        )
        assertTrue(nestedDir.exists(), "outputDir should be created")
        assertTrue(pngFile.exists(), "PNG file should exist in created dir")
    }

    // ── Return value ──────────────────────────────────────────────────────────

    @Test
    fun `capture returns the PNG file`() {
        val frameBuffer = IntArray(160 * 144)
        val result = ScreenshotCapture.capture(
            frameBuffer = frameBuffer,
            label = "ret",
            frameNumber = 7,
            outputDir = tempDir,
        )
        assertEquals("ret_frame7.png", result.name)
        assertTrue(result.exists())
    }
}
