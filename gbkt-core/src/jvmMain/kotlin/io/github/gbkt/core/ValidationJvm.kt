/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

/**
 * JVM implementation of asset validation.
 *
 * This first runs the common platform-independent PNG validation (signature check, dimension
 * parsing), then follows up with full ImageIO decode validation for comprehensive error detection.
 */
internal actual fun validateAssetFile(assetPath: String, assetDir: String?): AssetValidationResult {
    val errors = mutableListOf<String>()

    if (assetDir == null) {
        return AssetValidationResult(false, listOf("Asset directory not specified"))
    }

    val fullPath = FileIO.resolvePath(assetDir, assetPath)

    // Check if file exists
    if (!FileIO.exists(fullPath)) {
        errors.add("Asset file not found: $fullPath")
        return AssetValidationResult(false, errors)
    }

    // Check if file is readable
    if (!FileIO.isReadable(fullPath)) {
        errors.add("Asset file is not readable: $fullPath")
        return AssetValidationResult(false, errors)
    }

    // Check PNG extension
    if (!assetPath.lowercase().endsWith(".png")) {
        errors.add("Asset file must be a PNG file: $assetPath")
        return AssetValidationResult(false, errors)
    }

    // Read file bytes for common validation
    val bytes = FileIO.readBytes(fullPath)
    if (bytes == null) {
        errors.add("Failed to read asset file: $fullPath")
        return AssetValidationResult(false, errors)
    }

    // First: Run common platform-independent PNG validation
    // This checks PNG signature and parses IHDR for dimensions
    val pngResult = PngValidator.validate(bytes, assetPath)
    if (!pngResult.isValid) {
        // If common validation fails, return those errors immediately
        errors.addAll(pngResult.errors)
        return AssetValidationResult(false, errors)
    }

    // Second: Run full ImageIO decode validation for comprehensive checks
    // This catches issues the header-only validation might miss
    val imageIOResult = AssetValidator.validateAsset(assetPath, assetDir)
    if (!imageIOResult.isValid) {
        errors.addAll(imageIOResult.errors)
    }

    return AssetValidationResult(errors.isEmpty(), errors)
}
