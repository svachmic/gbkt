/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import io.github.gbkt.core.ir.SpriteMode
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

// =============================================================================
// Phase 13.3 Plan 16 Task 1 — ConvertSpritesPivotArgsTest (GAP-1 PIVOT axis)
//
// Locks the contract: buildPng2AssetArgs with pivotX=32/pivotY=24 must produce
// `-px 32 -py 24` as adjacent tokens. This is the permanent regression guard
// ensuring the elephant's pivot(32,24) in Metasprites.kt stays wired correctly
// through to the png2asset invocation.
//
// Also includes a regression guard for the default/escape-hatch path:
// pivotX=0/pivotY=0 must produce `-px 0 -py 0`.
//
// Both tests call buildPng2AssetArgs() directly (internal fun at file scope in
// ConvertSpritesTask.kt, same package — no subprocess invoked, no GBDK needed).
// Mirrors the testing approach from ConvertSpritesTaskSidecarTest Test 4.
// =============================================================================

class ConvertSpritesPivotArgsTest {

    @TempDir lateinit var tempDir: File

    // -------------------------------------------------------------------------
    // Test 1: pivot(32, 24) — frame-center pivot for 64x48 elephant frame
    //
    // Contract: buildPng2AssetArgs(..., pivotX=32, pivotY=24, ...) must include
    // the tokens "-px", "32", "-py", "24" as contiguous key→value pairs.
    //
    // This is the permanent lock ensuring Metasprites.kt's pivot(32, 24) flows
    // to png2asset as `-px 32 -py 24`. With this pivot, move_metasprite(80, 72)
    // anchors the frame at its center (80 - 32 = 48, 72 - 24 = 48), matching
    // the pre-migration render position.
    // -------------------------------------------------------------------------
    @Test
    fun `buildPng2AssetArgs emits -px 32 -py 24 for elephant frame-center pivot`() {
        val pngFile = File(tempDir, "sprites/elephant.png")
        pngFile.parentFile.mkdirs()
        writeSpritePng(pngFile, width = 64, height = 48)
        val outputC = File(tempDir, "out/sprites/elephant.c")
        outputC.parentFile.mkdirs()

        val args =
            buildPng2AssetArgs(
                pngFile = pngFile,
                outputC = outputC,
                spriteMode = SpriteMode.SPR8x8,
                pivotX = 32,
                pivotY = 24,
                frameWidth = 64,
                frameHeight = 48,
                mirrorDedup = false,
            )

        val pxIdx = args.indexOf("-px")
        val pyIdx = args.indexOf("-py")

        assertTrue(pxIdx >= 0, "args must contain -px; got: $args")
        assertTrue(pyIdx >= 0, "args must contain -py; got: $args")

        assertEquals("32", args[pxIdx + 1], "value after -px must be 32; got: ${args[pxIdx + 1]}")
        assertEquals("24", args[pyIdx + 1], "value after -py must be 24; got: ${args[pyIdx + 1]}")

        // -px precedes -py (canonical arg order from buildPng2AssetArgs)
        assertTrue(pxIdx < pyIdx, "-px must come before -py; got: $args")
    }

    // -------------------------------------------------------------------------
    // Test 2: pivot(0, 0) — default / escape-hatch path regression guard
    //
    // Contract: buildPng2AssetArgs(..., pivotX=0, pivotY=0, ...) must include
    // the tokens "-px", "0", "-py", "0". The escape hatch (top-left anchor,
    // used by metasprites-stress fixture) must remain unchanged.
    // -------------------------------------------------------------------------
    @Test
    fun `buildPng2AssetArgs emits -px 0 -py 0 for default top-left pivot`() {
        val pngFile = File(tempDir, "sprites/tiger.png")
        pngFile.parentFile.mkdirs()
        writeSpritePng(pngFile, width = 64, height = 48)
        val outputC = File(tempDir, "out/sprites/tiger.c")
        outputC.parentFile.mkdirs()

        val args =
            buildPng2AssetArgs(
                pngFile = pngFile,
                outputC = outputC,
                spriteMode = SpriteMode.SPR8x8,
                pivotX = 0,
                pivotY = 0,
                frameWidth = 64,
                frameHeight = 48,
                mirrorDedup = false,
            )

        val pxIdx = args.indexOf("-px")
        val pyIdx = args.indexOf("-py")

        assertTrue(pxIdx >= 0, "args must contain -px; got: $args")
        assertTrue(pyIdx >= 0, "args must contain -py; got: $args")

        assertEquals("0", args[pxIdx + 1], "value after -px must be 0; got: ${args[pxIdx + 1]}")
        assertEquals("0", args[pyIdx + 1], "value after -py must be 0; got: ${args[pyIdx + 1]}")
    }

    // -------------------------------------------------------------------------
    // Helper: write a minimal valid PNG fixture (no GBDK needed — tests only
    // the args-building logic, not png2asset invocation)
    // -------------------------------------------------------------------------
    private fun writeSpritePng(target: File, width: Int, height: Int) {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = if ((x + y) % 2 == 0) 0x000000 else 0xFFFFFF
                img.setRGB(x, y, color)
            }
        }
        ImageIO.write(img, "PNG", target)
        require(target.isFile && target.length() > 0) {
            "test fixture PNG was not written: ${target.absolutePath}"
        }
    }
}
