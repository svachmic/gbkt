/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class VisualDiffTest {

    @TempDir lateinit var tempDir: File

    private fun createPng(width: Int, height: Int, color: Int, name: String): File {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(color)
        g.fillRect(0, 0, width, height)
        g.dispose()
        val file = File(tempDir, "$name.png")
        ImageIO.write(img, "png", file)
        return file
    }

    private fun createCheckerboardPng(name: String): File {
        val img = BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 144) {
            for (x in 0 until 160) {
                img.setRGB(x, y, if ((x + y) % 2 == 0) 0xFFFFFF else 0x000000)
            }
        }
        val file = File(tempDir, "$name.png")
        ImageIO.write(img, "png", file)
        return file
    }

    @Test
    fun `identical images return match=true with zero diffCount`() {
        val expected = createPng(160, 144, 0x336699, "expected")
        val actual = createPng(160, 144, 0x336699, "actual")

        val result = VisualDiff.compare(expected, actual)

        assertTrue(result.match)
        assertEquals(0, result.diffCount)
        assertEquals(160 * 144, result.totalPixels)
        assertNull(result.diffImage)
    }

    @Test
    fun `completely different images return match=false`() {
        val expected = createPng(160, 144, 0xFFFFFF, "expected_white")
        val actual = createPng(160, 144, 0x000000, "actual_black")

        val result = VisualDiff.compare(expected, actual)

        assertFalse(result.match)
        assertEquals(160 * 144, result.diffCount)
        assertEquals(160 * 144, result.totalPixels)
    }

    @Test
    fun `mismatch generates diff image in output directory`() {
        val expected = createPng(160, 144, 0xFFFFFF, "expected2")
        val actual = createPng(160, 144, 0x000000, "actual2")

        val result = VisualDiff.compare(expected, actual, diffOutputDir = tempDir)

        assertFalse(result.match)
        assertNotNull(result.diffImage)
        assertTrue(result.diffImage!!.exists())
    }

    @Test
    fun `diff image contains red pixels at mismatch locations`() {
        val expected = createPng(160, 144, 0xFFFFFF, "expected3")
        val actual = createPng(160, 144, 0x000000, "actual3")

        val result = VisualDiff.compare(expected, actual, diffOutputDir = tempDir)

        assertNotNull(result.diffImage)
        val diffImg = ImageIO.read(result.diffImage)
        // All pixels should be red (0xFF0000) since all are different
        val topLeft = diffImg.getRGB(0, 0) and 0xFFFFFF
        assertEquals(0xFF0000, topLeft, "Mismatched pixels should be marked red in diff image")
    }

    @Test
    fun `tolerance 5 percent allows up to 5 percent pixel difference`() {
        // Create a 160x144 image where 5% (1152 pixels) differ
        val expected = BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB)
        val actual = BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB)

        // Paint both white
        for (y in 0 until 144) {
            for (x in 0 until 160) {
                expected.setRGB(x, y, 0xFFFFFF)
                actual.setRGB(x, y, 0xFFFFFF)
            }
        }
        // Make exactly 5% of pixels different in actual (first 1152 pixels)
        var changed = 0
        outer@ for (y in 0 until 144) {
            for (x in 0 until 160) {
                if (changed >= 1152) break@outer
                actual.setRGB(x, y, 0x000000)
                changed++
            }
        }

        val expectedFile = File(tempDir, "tol_expected.png")
        val actualFile = File(tempDir, "tol_actual.png")
        ImageIO.write(expected, "png", expectedFile)
        ImageIO.write(actual, "png", actualFile)

        // Exactly 5% differs, tolerance is 5% — should match
        val result = VisualDiff.compare(expectedFile, actualFile, tolerance = 0.05)
        assertTrue(result.match, "5% difference with 5% tolerance should match")
    }

    @Test
    fun `tolerance 4 percent fails when 5 percent pixels differ`() {
        val expected = BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB)
        val actual = BufferedImage(160, 144, BufferedImage.TYPE_INT_RGB)

        for (y in 0 until 144) {
            for (x in 0 until 160) {
                expected.setRGB(x, y, 0xFFFFFF)
                actual.setRGB(x, y, 0xFFFFFF)
            }
        }
        // Make 10% of pixels different
        var changed = 0
        outer@ for (y in 0 until 144) {
            for (x in 0 until 160) {
                if (changed >= 2304) break@outer
                actual.setRGB(x, y, 0x000000)
                changed++
            }
        }

        val expectedFile = File(tempDir, "tol2_expected.png")
        val actualFile = File(tempDir, "tol2_actual.png")
        ImageIO.write(expected, "png", expectedFile)
        ImageIO.write(actual, "png", actualFile)

        // 10% differs, tolerance is 4% — should fail
        val result = VisualDiff.compare(expectedFile, actualFile, tolerance = 0.04)
        assertFalse(result.match, "10% difference with 4% tolerance should not match")
    }

    @Test
    fun `size mismatch throws IllegalArgumentException`() {
        val expected = createPng(160, 144, 0xFFFFFF, "big")
        val actual = createPng(80, 72, 0xFFFFFF, "small")

        assertThrows(IllegalArgumentException::class.java) { VisualDiff.compare(expected, actual) }
    }

    @Test
    fun `match returns no diff image when images are identical`() {
        val expected = createCheckerboardPng("checker_expected")
        val actual = createCheckerboardPng("checker_actual")

        val result = VisualDiff.compare(expected, actual, diffOutputDir = tempDir)

        assertTrue(result.match)
        assertNull(result.diffImage, "No diff image should be created on match")
    }
}
