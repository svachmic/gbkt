/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

/**
 * Native implementation of asset validation using the common PngValidator.
 *
 * This provides platform-independent PNG validation by parsing the PNG header bytes directly,
 * without requiring ImageIO or other JVM-specific libraries.
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

    // Read file bytes
    val bytes = FileIO.readBytes(fullPath)
    if (bytes == null) {
        errors.add("Failed to read asset file: $fullPath")
        return AssetValidationResult(false, errors)
    }

    // Validate PNG using common validator
    val pngResult = PngValidator.validate(bytes, assetPath)
    if (!pngResult.isValid) {
        errors.addAll(pngResult.errors)
    }

    return AssetValidationResult(errors.isEmpty(), errors)
}
