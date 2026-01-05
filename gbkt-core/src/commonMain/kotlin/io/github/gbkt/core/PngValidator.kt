/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

/**
 * Platform-independent PNG validation using pure byte parsing.
 *
 * This validator checks:
 * - PNG signature (magic bytes)
 * - IHDR chunk for image dimensions
 * - Dimensions are multiples of 8 (required for Game Boy tiles)
 *
 * This does not fully decode the PNG or validate all chunks - it's a lightweight check for common
 * issues that can be run on any platform without ImageIO or other platform-specific libraries.
 */
object PngValidator {
    /** PNG signature: 89 50 4E 47 0D 0A 1A 0A The first 8 bytes of any valid PNG file. */
    private val PNG_SIGNATURE =
        byteArrayOf(
            0x89.toByte(),
            0x50, // P
            0x4E, // N
            0x47, // G
            0x0D, // CR
            0x0A, // LF
            0x1A, // SUB
            0x0A, // LF
        )

    /** Minimum valid PNG size: 8 (signature) + 25 (IHDR chunk with length/type/crc) */
    private const val MIN_PNG_SIZE = 33

    /** Maximum reasonable image dimension for Game Boy assets */
    private const val MAX_DIMENSION = 1024

    /**
     * Validate PNG data from raw bytes.
     *
     * @param data The raw PNG file bytes
     * @param fileName Optional file name for error messages
     * @return Validation result with errors if invalid
     */
    fun validate(data: ByteArray, fileName: String = "unknown"): PngValidationResult {
        val errors = mutableListOf<String>()

        // Check minimum size
        if (data.size < MIN_PNG_SIZE) {
            errors.add(
                "File too small to be a valid PNG (${data.size} bytes, minimum $MIN_PNG_SIZE)"
            )
            return PngValidationResult(false, errors, null, null)
        }

        // Check PNG signature
        if (!checkSignature(data)) {
            errors.add("Invalid PNG signature - file is not a valid PNG")
            return PngValidationResult(false, errors, null, null)
        }

        // Parse IHDR chunk for dimensions
        val dimensions = parseIHDR(data)
        if (dimensions == null) {
            errors.add("Failed to parse IHDR chunk - PNG may be corrupted")
            return PngValidationResult(false, errors, null, null)
        }

        val (width, height) = dimensions

        // Validate dimensions
        if (width <= 0 || height <= 0) {
            errors.add("Invalid image dimensions: ${width}x${height} (must be positive)")
        }

        if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
            errors.add(
                "Image dimensions too large: ${width}x${height} (max recommended: ${MAX_DIMENSION}x${MAX_DIMENSION})"
            )
        }

        // Check dimensions are multiples of 8 (required for Game Boy tiles)
        if (width % 8 != 0) {
            errors.add("Image width must be a multiple of 8, got $width pixels")
        }

        if (height % 8 != 0) {
            errors.add("Image height must be a multiple of 8, got $height pixels")
        }

        return PngValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            width = width,
            height = height,
        )
    }

    /** Check if the data starts with a valid PNG signature. */
    private fun checkSignature(data: ByteArray): Boolean {
        if (data.size < PNG_SIGNATURE.size) return false
        for (i in PNG_SIGNATURE.indices) {
            if (data[i] != PNG_SIGNATURE[i]) return false
        }
        return true
    }

    /**
     * Parse the IHDR chunk to extract image dimensions.
     *
     * IHDR is always the first chunk after the signature. Structure:
     * - 4 bytes: chunk length (big-endian)
     * - 4 bytes: chunk type ("IHDR")
     * - 4 bytes: width (big-endian)
     * - 4 bytes: height (big-endian)
     * - 1 byte: bit depth
     * - 1 byte: color type
     * - 1 byte: compression method
     * - 1 byte: filter method
     * - 1 byte: interlace method
     * - 4 bytes: CRC
     *
     * @return Pair of (width, height) or null if parsing fails
     */
    private fun parseIHDR(data: ByteArray): Pair<Int, Int>? {
        // IHDR starts at byte 8 (after signature)
        if (data.size < 8 + 25) return null // 8 signature + 4 length + 4 type + 13 data + 4 CRC

        // Read chunk length (4 bytes, big-endian)
        val length = readInt32BE(data, 8)
        if (length != 13) {
            // IHDR data is always 13 bytes
            return null
        }

        // Check chunk type is "IHDR"
        if (
            data[12] != 'I'.code.toByte() ||
                data[13] != 'H'.code.toByte() ||
                data[14] != 'D'.code.toByte() ||
                data[15] != 'R'.code.toByte()
        ) {
            return null
        }

        // Read width and height (4 bytes each, big-endian)
        val width = readInt32BE(data, 16)
        val height = readInt32BE(data, 20)

        return Pair(width, height)
    }

    /** Read a 32-bit big-endian integer from a byte array. */
    private fun readInt32BE(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
    }
}

/**
 * Result of PNG validation.
 *
 * @property isValid True if the PNG passed all validation checks
 * @property errors List of validation errors (empty if valid)
 * @property width Image width in pixels (null if parsing failed)
 * @property height Image height in pixels (null if parsing failed)
 */
data class PngValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val width: Int?,
    val height: Int?,
)
