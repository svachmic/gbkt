/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Result of a [VisualDiff.compare] operation.
 *
 * @param match True if the images are considered equal within [tolerance].
 * @param diffCount Number of pixels that differ between the two images.
 * @param totalPixels Total pixel count (width × height). Always 160 × 144 = 23040 for Game Boy.
 * @param diffImage Optional diff image file highlighting mismatched pixels in red. Null if images
 *   match or [VisualDiff.compare] was called without a diff output directory.
 */
data class DiffResult(
    val match: Boolean,
    val diffCount: Int,
    val totalPixels: Int,
    val diffImage: File?,
)

/**
 * Pixel-level screenshot comparison for automated regression testing.
 *
 * Compares two Game Boy screenshot PNG files (160×144 pixels) pixel-by-pixel. When images differ
 * beyond the configured tolerance, generates a diff image highlighting mismatched pixels in red.
 *
 * Usage:
 * ```kotlin
 * val result = VisualDiff.compare(expected, actual, tolerance = 0.05, diffOutputDir = outputDir)
 * if (!result.match) {
 *     println("${result.diffCount} pixels differ. Diff: ${result.diffImage}")
 * }
 * ```
 */
object VisualDiff {

    /**
     * Compares [expected] and [actual] PNG files pixel-by-pixel.
     *
     * Both images must be exactly 160×144 pixels (Game Boy LCD dimensions).
     *
     * @param expected Reference PNG file.
     * @param actual Captured PNG file to compare against [expected].
     * @param tolerance Fraction of pixels allowed to differ (0.0 = pixel-perfect, 0.05 = 5%).
     * @param diffOutputDir Directory to write the diff image to when mismatch occurs. If null, no
     *   diff image is generated.
     * @return [DiffResult] with match status, pixel counts, and optional diff image.
     * @throws IllegalArgumentException if the images have different dimensions or are not 160×144.
     */
    fun compare(
        expected: File,
        actual: File,
        tolerance: Double = 0.0,
        diffOutputDir: File? = null,
    ): DiffResult {
        val expectedImg = ImageIO.read(expected)
        val actualImg = ImageIO.read(actual)

        require(expectedImg.width == actualImg.width && expectedImg.height == actualImg.height) {
            "Image dimensions do not match: expected ${expectedImg.width}×${expectedImg.height} " +
                "but actual is ${actualImg.width}×${actualImg.height}"
        }

        val width = expectedImg.width
        val height = expectedImg.height
        val totalPixels = width * height

        val (diffCount, mismatchedCoords) = diffPixels(expectedImg, actualImg, width, height)

        val diffRatio = diffCount.toDouble() / totalPixels
        val isMatch = diffRatio <= tolerance

        val diffImageFile =
            if (!isMatch && diffOutputDir != null && mismatchedCoords.isNotEmpty()) {
                generateDiffImage(actualImg, mismatchedCoords, actual, diffOutputDir)
            } else {
                null
            }

        return DiffResult(
            match = isMatch,
            diffCount = diffCount,
            totalPixels = totalPixels,
            diffImage = diffImageFile,
        )
    }

    /**
     * Compares only a rectangular crop of [expected] and [actual] PNG files pixel-by-pixel.
     *
     * Unlike [compare], the returned [DiffResult.totalPixels] reflects the crop area (`cw × ch`),
     * so `diffCount / totalPixels` is the **in-region** ratio rather than a full-frame ratio. This
     * lets a caller gate on the diff of a specific sprite/UI region independent of incidental
     * background noise outside the region (e.g. camera/BG-scroll pixels).
     *
     * The rectangle is clamped to the image bounds before cropping: a negative origin or a width /
     * height that overshoots the image edge is silently truncated to fit. This makes a partially
     * off-screen sprite bounding box safe to pass without throwing.
     *
     * Both images must have identical dimensions (same requirement as [compare]).
     *
     * @param expected Reference PNG file.
     * @param actual Captured PNG file to compare against [expected].
     * @param x Left edge of the region (clamped to `0..width`).
     * @param y Top edge of the region (clamped to `0..height`).
     * @param w Region width (clamped so `x + w <= width`).
     * @param h Region height (clamped so `y + h <= height`).
     * @param tolerance Fraction of in-region pixels allowed to differ (0.0 = pixel-perfect).
     * @param diffOutputDir Directory to write the (crop-relative) diff image to when mismatch
     *   occurs. If null, no diff image is generated.
     * @return [DiffResult] with `totalPixels == cw * ch` (the clamped crop area). If the clamped
     *   crop has zero area, returns a matching result with `diffCount = 0` and `totalPixels = 0`.
     * @throws IllegalArgumentException if the images have different dimensions.
     */
    @Suppress("LongParameterList")
    fun compareRegion(
        expected: File,
        actual: File,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        tolerance: Double = 0.0,
        diffOutputDir: File? = null,
    ): DiffResult {
        val expectedImg = ImageIO.read(expected)
        val actualImg = ImageIO.read(actual)

        require(expectedImg.width == actualImg.width && expectedImg.height == actualImg.height) {
            "Image dimensions do not match: expected ${expectedImg.width}×${expectedImg.height} " +
                "but actual is ${actualImg.width}×${actualImg.height}"
        }

        val width = expectedImg.width
        val height = expectedImg.height

        // Clamp the rectangle to image bounds so an off-screen sprite box never throws.
        val cx = x.coerceIn(0, width)
        val cy = y.coerceIn(0, height)
        val cw = w.coerceAtMost(width - cx).coerceAtLeast(0)
        val ch = h.coerceAtMost(height - cy).coerceAtLeast(0)

        if (cw <= 0 || ch <= 0) {
            return DiffResult(match = true, diffCount = 0, totalPixels = 0, diffImage = null)
        }

        val expectedCrop = expectedImg.getSubimage(cx, cy, cw, ch)
        val actualCrop = actualImg.getSubimage(cx, cy, cw, ch)

        val totalPixels = cw * ch
        val (diffCount, mismatchedCoords) = diffPixels(expectedCrop, actualCrop, cw, ch)

        val diffRatio = diffCount.toDouble() / totalPixels
        val isMatch = diffRatio <= tolerance

        val diffImageFile =
            if (!isMatch && diffOutputDir != null && mismatchedCoords.isNotEmpty()) {
                // Coordinates are relative to the crop; generateDiffImage copies the crop itself.
                generateDiffImage(actualCrop, mismatchedCoords, actual, diffOutputDir)
            } else {
                null
            }

        return DiffResult(
            match = isMatch,
            diffCount = diffCount,
            totalPixels = totalPixels,
            diffImage = diffImageFile,
        )
    }

    /**
     * Runs the per-pixel RGB-masked comparison over a [width] × [height] region of two images,
     * returning the diff count and the list of mismatched coordinates (image-local).
     *
     * Shared by [compare] (full-frame) and [compareRegion] (crop) so both use one comparison loop.
     */
    private fun diffPixels(
        expectedImg: BufferedImage,
        actualImg: BufferedImage,
        width: Int,
        height: Int,
    ): Pair<Int, List<Pair<Int, Int>>> {
        var diffCount = 0
        val mismatchedCoords = mutableListOf<Pair<Int, Int>>()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val expectedRgb = expectedImg.getRGB(x, y) and 0xFFFFFF
                val actualRgb = actualImg.getRGB(x, y) and 0xFFFFFF
                if (expectedRgb != actualRgb) {
                    diffCount++
                    mismatchedCoords += x to y
                }
            }
        }

        return diffCount to mismatchedCoords
    }

    /** Creates a diff image by copying [actualImg] and painting mismatched pixels red. */
    private fun generateDiffImage(
        actualImg: BufferedImage,
        mismatchedCoords: List<Pair<Int, Int>>,
        actualFile: File,
        outputDir: File,
    ): File {
        val diffImg = BufferedImage(actualImg.width, actualImg.height, BufferedImage.TYPE_INT_RGB)
        val g = diffImg.createGraphics()
        g.drawImage(actualImg, 0, 0, null)
        g.dispose()

        val redPixel = 0xFF0000
        for ((x, y) in mismatchedCoords) {
            diffImg.setRGB(x, y, redPixel)
        }

        outputDir.mkdirs()
        val diffFile = File(outputDir, "${actualFile.nameWithoutExtension}_diff.png")
        ImageIO.write(diffImg, "png", diffFile)
        return diffFile
    }
}
