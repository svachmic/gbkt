/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import io.github.gbkt.emulator.debug.DebugLogEntry
import org.json.JSONObject
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

/**
 * Captures a Game Boy LCD frame as a 160x144 PNG file with a JSON metadata sidecar.
 *
 * PNG path:  `{outputDir}/{label}_frame{frameNumber}.png`
 * JSON path: `{outputDir}/{label}_frame{frameNumber}.json`
 *
 * JSON sidecar format:
 * ```json
 * {
 *   "frameNumber": 42,
 *   "label": "battle_start",
 *   "capturedAt": 1700000000000,
 *   "variables": { "score": 100, "lives": 3 }
 * }
 * ```
 *
 * Usage:
 * ```kotlin
 * val png = ScreenshotCapture.capture(
 *     frameBuffer = emulator.getFrameBuffer(),
 *     label = "victory",
 *     frameNumber = 1234,
 *     outputDir = File("build/screenshots"),
 *     variableSnapshot = inspector.readAll(),
 * )
 * ```
 */
object ScreenshotCapture {

    private const val FRAME_BUFFER_SIZE = 160 * 144
    private const val DISPLAY_WIDTH = 160
    private const val DISPLAY_HEIGHT = 144

    /**
     * Captures the given frame buffer as a 160x144 PNG and writes a JSON sidecar with metadata.
     *
     * @param frameBuffer 23040-element RGB pixel array from [io.github.gbkt.emulator.GbEmulator.getFrameBuffer].
     *   Each element is packed as 0x00RRGGBB. Must be exactly 160 * 144 = 23040 elements.
     * @param label Human-readable label used as the file name prefix (e.g., "battle_start").
     * @param frameNumber The emulator frame number at capture time (used in file name and JSON).
     * @param outputDir Directory to write the PNG and JSON files into. Created if it does not exist.
     * @param variableSnapshot Optional map of DSL variable names to their current values. Written
     *   into the `"variables"` field of the JSON sidecar.
     * @param debugLogEntries Optional list of debug log entries to include in the JSON sidecar.
     *   When non-empty, serialized as a `"debugLog"` JSON array.
     * @return The PNG [File] that was written.
     * @throws IllegalArgumentException if [frameBuffer] does not have exactly 23040 elements.
     */
    fun capture(
        frameBuffer: IntArray,
        label: String,
        frameNumber: Int,
        outputDir: File,
        variableSnapshot: Map<String, Int> = emptyMap(),
        debugLogEntries: List<DebugLogEntry> = emptyList(),
    ): File {
        require(frameBuffer.size == FRAME_BUFFER_SIZE) {
            "frameBuffer must have exactly $FRAME_BUFFER_SIZE elements (160×144), " +
                "but got ${frameBuffer.size}"
        }

        outputDir.mkdirs()

        val baseName = "${label}_frame${frameNumber}"
        val pngFile = File(outputDir, "$baseName.png")
        val jsonFile = File(outputDir, "$baseName.json")

        // ── Write PNG ─────────────────────────────────────────────────────────
        val img = BufferedImage(DISPLAY_WIDTH, DISPLAY_HEIGHT, BufferedImage.TYPE_INT_RGB)
        img.setRGB(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT, frameBuffer, 0, DISPLAY_WIDTH)
        try {
            ImageIO.write(img, "png", pngFile)
        } catch (e: IOException) {
            throw ScreenshotCaptureException(
                "Failed to write screenshot '$label' at frame $frameNumber: ${e.message}",
                e,
            )
        }

        // ── Write JSON sidecar ────────────────────────────────────────────────
        val variables = JSONObject()
        for ((name, value) in variableSnapshot) {
            variables.put(name, value)
        }
        val sidecar =
            JSONObject()
                .put("frameNumber", frameNumber)
                .put("label", label)
                .put("capturedAt", System.currentTimeMillis())
                .put("variables", variables)
        if (debugLogEntries.isNotEmpty()) {
            val logArray = org.json.JSONArray()
            for (entry in debugLogEntries) {
                logArray.put(
                    JSONObject()
                        .put("timestampMs", entry.timestampMs)
                        .put("level", entry.level.name)
                        .put("message", entry.message),
                )
            }
            sidecar.put("debugLog", logArray)
        }
        try {
            jsonFile.writeText(sidecar.toString(2))
        } catch (e: IOException) {
            throw ScreenshotCaptureException(
                "Failed to write screenshot metadata '$label': ${e.message}",
                e,
            )
        }

        return pngFile
    }
}
