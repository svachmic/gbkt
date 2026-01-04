/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Validates asset files for common issues like corruption, wrong dimensions, etc. This is
 * JVM-specific as it uses ImageIO for PNG validation.
 */
object AssetValidator {
    /**
     * Validate an asset file.
     *
     * @param assetPath Relative path to the asset file
     * @param assetDir Base directory for assets
     * @return Validation result with errors if any
     */
    internal fun validateAsset(assetPath: String, assetDir: String?): AssetValidationResult {
        val errors = mutableListOf<String>()

        if (assetDir == null) {
            return AssetValidationResult(false, listOf("Asset directory not specified"))
        }

        val file = File(assetDir, assetPath)

        // Check if file exists
        if (!file.exists()) {
            errors.add("Asset file not found: ${file.absolutePath}")
            return AssetValidationResult(false, errors)
        }

        // Check if file is readable
        if (!file.canRead()) {
            errors.add("Asset file is not readable: ${file.absolutePath}")
            return AssetValidationResult(false, errors)
        }

        // Check if file has PNG extension
        if (!file.name.lowercase().endsWith(".png")) {
            errors.add("Asset file must be a PNG file: ${file.name}")
            return AssetValidationResult(false, errors)
        }

        // Try to read the image to check for corruption
        try {
            val image: BufferedImage? = ImageIO.read(file)

            if (image == null) {
                errors.add(
                    "Failed to read PNG file - file may be corrupted or not a valid PNG: ${file.name}"
                )
                return AssetValidationResult(false, errors)
            }

            // Check dimensions are multiples of 8
            val width = image.width
            val height = image.height

            if (width <= 0 || height <= 0) {
                errors.add("Invalid image dimensions: ${width}x${height} (must be positive)")
            }

            if (width % 8 != 0) {
                errors.add("Image width must be a multiple of 8, got $width pixels")
            }

            if (height % 8 != 0) {
                errors.add("Image height must be a multiple of 8, got $height pixels")
            }

            // Check reasonable size limits (Game Boy screen is 160x144, but sprites can be larger)
            if (width > 1024 || height > 1024) {
                errors.add(
                    "Image dimensions are very large: ${width}x${height} (max recommended: 1024x1024)"
                )
            }

            // Check if image has any pixels (basic sanity check)
            if (width > 0 && height > 0) {
                try {
                    // Try to read a pixel to ensure image data is accessible
                    image.getRGB(0, 0)
                } catch (e: Exception) {
                    errors.add(
                        "Image data appears corrupted - cannot read pixel data: ${e.message}"
                    )
                }
            }
        } catch (e: Exception) {
            errors.add("Error reading PNG file: ${e.message ?: e.javaClass.simpleName}")
            return AssetValidationResult(false, errors)
        }

        return AssetValidationResult(errors.isEmpty(), errors)
    }
}
