/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import java.awt.image.BufferedImage
import java.awt.image.IndexColorModel
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO

// =============================================================================
// PNG utilities shared across ConvertZoneTilesetsTask and ConvertSpritesTask.
//
// Phase 12.8 W3: isIndexedPng was originally an internal method of
// ConvertZoneTilesetsTask. Phase 12.9 D2a requires the same guard in
// ConvertSpritesTask (for sprite PNGs). To avoid duplicating the PNG-header
// parse logic, it is extracted here as a package-internal top-level function.
//
// ConvertZoneTilesetsTask.isIndexedPng delegates to this function; both tasks
// share the same PNG-header parse via a single source of truth.
// =============================================================================

/** PNG file signature (first 8 bytes). */
internal val PNG_SIGNATURE_BYTES: ByteArray =
    byteArrayOf(
        0x89.toByte(),
        0x50.toByte(),
        0x4E.toByte(),
        0x47.toByte(),
        0x0D.toByte(),
        0x0A.toByte(),
        0x1A.toByte(),
        0x0A.toByte(),
    )

/** Offset of the IHDR color-type byte in a PNG file (byte 25). */
internal const val PNG_COLOR_TYPE_OFFSET_SHARED: Int = 25

/** PNG IHDR color-type value for indexed/colormap images. */
internal const val PNG_COLOR_TYPE_INDEXED_SHARED: Byte = 3.toByte()

/**
 * Number of bytes to read for the color-type check: signature + IHDR length/type + width + height +
 * bit-depth + color-type = 26 bytes.
 */
internal const val PNG_HEADER_BYTES_SHARED: Int = 26

/**
 * Returns `true` if [file] is a PNG image with indexed (colormap) color type (IHDR color-type byte
 * = 3).
 *
 * Phase 12.8 W3: originally ConvertZoneTilesetsTask.isIndexedPng. Extracted to file scope in Phase
 * 12.9 D2a so ConvertSpritesTask can reuse the same check without duplicating the PNG-header parse.
 *
 * @param file The PNG file to inspect.
 * @return `true` when the IHDR color-type byte is `3` (indexed/colormap); `false` for all other
 *   color types (RGB/RGBA/greyscale) or when the file cannot be read.
 */
internal fun isIndexedPngShared(file: File): Boolean {
    if (!file.isFile) return false
    return try {
        file.inputStream().buffered().use { stream ->
            val header = ByteArray(PNG_HEADER_BYTES_SHARED)
            val read = stream.read(header)
            if (read < PNG_HEADER_BYTES_SHARED) return false
            for (i in PNG_SIGNATURE_BYTES.indices) {
                if (header[i] != PNG_SIGNATURE_BYTES[i]) return false
            }
            header[PNG_COLOR_TYPE_OFFSET_SHARED] == PNG_COLOR_TYPE_INDEXED_SHARED
        }
    } catch (_: Exception) {
        false
    }
}

/**
 * Returns the declared transparent palette index for an indexed PNG (the index referenced by the
 * tRNS chunk), or null when:
 * - The file does not exist or is not readable
 * - The PNG is not indexed (IHDR color-type != 3, so [colorModel] is not [IndexColorModel])
 * - The PNG has no tRNS chunk ([IndexColorModel.getTransparentPixel] returns < 0)
 * - Any exception is thrown during decode
 *
 * Null is the "none" sentinel per SPEC REQ-1: null means no tRNS routing is needed. Delegates to
 * [javax.imageio.ImageIO] + [IndexColorModel.getTransparentPixel].
 *
 * Phase 13.6 REQ-1: tRNS detection primitive shared by ConvertSpritesTask routing decision
 * (Plan 03) and the overflow guard (Plan 04).
 *
 * @param file The PNG file to inspect.
 * @return The non-negative tRNS palette index, or null for the "none" sentinel.
 */
internal fun getTransparentIndexShared(file: File): Int? {
    if (!file.isFile) return null
    return try {
        val img = ImageIO.read(file) ?: return null
        val cm = img.colorModel as? IndexColorModel ?: return null
        val tidx = cm.transparentPixel
        if (tidx < 0) null else tidx
    } catch (_: Exception) {
        null
    }
}

/**
 * Counts the number of non-zero-pixel visible palette entries, excluding [transparentIdx].
 *
 * "Visible" means the palette entry is not the transparent index. "Used" means at least one pixel
 * in the image carries that palette index. Palette entries with zero pixels (e.g. elephant's
 * bright-green at palette index 2) are excluded from the count.
 *
 * This is the REQ-5 overflow guard input: if the result is > 3, the sprite exceeds the GB OBJ
 * palette limit (1 transparent + 3 visible = 4 total entries).
 *
 * Uses [ImageIO.read] + [IndexColorModel] + [java.awt.image.Raster.getSample] — all JVM stdlib,
 * already on the Gradle plugin classpath.
 *
 * Bounds-checks each raster sample against [IndexColorModel.getMapSize] before incrementing (REQ-5
 * overflow guard, mitigating T-13.6-02b malformed indexed PNG).
 *
 * @param file The PNG file to inspect.
 * @param transparentIdx The palette index to exclude (the tRNS transparent index).
 * @return The count of non-zero-pixel visible palette entries, or 0 if the file cannot be read due
 *   to an [IOException] or is not an indexed PNG. Scan-loop logic errors (e.g.
 *   [ArrayIndexOutOfBoundsException]) propagate so they do not silently bypass the REQ-5
 *   OBJ-palette overflow guard.
 */
internal fun countUsedVisibleColors(file: File, transparentIdx: Int): Int {
    return try {
        val img = ImageIO.read(file) ?: return 0
        val cm = img.colorModel as? IndexColorModel ?: return 0
        val raster = img.raster
        val pixelCounts = IntArray(cm.mapSize)
        for (y in 0 until img.height) {
            for (x in 0 until img.width) {
                val idx = raster.getSample(x, y, 0)
                if (idx in pixelCounts.indices) pixelCounts[idx]++
            }
        }
        pixelCounts.indices.count { it != transparentIdx && pixelCounts[it] > 0 }
    } catch (_: IOException) {
        0
    }
}

// =====================================================================================
// Phase 13.7 Plan 02 — Palette polarity comparator (D-02/D-02b/D-02c)
//
// A single shared comparator consumed by three downstream sites:
//   1. ConvertZoneTilesetsTask WARNING (Plan 05 — BG pipeline)
//   2. ConvertSpritesTask WARNING (Plan 05 — OBJ pipeline)
//   3. Emission tests (Req 3 tier 1 — this file)
//
// Predicate (D-02c, locked by research):
//   Spearman rank correlation == -1.0 (strict full reversal, EPSILON=1e-9)
//   Rec.601 luminance formula, ties via equal-rank averaging.
//   Baseline = the source PNG's own PLTE luminance ranking (D-02b).
//   Returns false for degenerate input (n<2, all-equal-lum) — no false positive.
// =====================================================================================

/** EPSILON for floating-point comparison in Spearman strict full-reversal check (D-02c). */
private const val SPEARMAN_EPSILON: Double = 1e-9

/**
 * Computes the Spearman rank correlation between [ranksX] and [ranksY].
 *
 * Uses the standard d-squared formula: `1 - (6 * sum(d^2)) / (n * (n^2 - 1))`. Assumes [ranksX] and
 * [ranksY] have the same size. Ties must be pre-resolved via [spearmanRanks] (equal-rank averaging)
 * before calling this function.
 *
 * Returns 0.0 for n <= 1 (degenerate — caller must guard before calling).
 *
 * @param ranksX Pre-computed ranks for the X series (1-indexed fractional averages).
 * @param ranksY Pre-computed ranks for the Y series (1-indexed fractional averages).
 * @return Spearman rank correlation in [-1.0, 1.0]; 0.0 for n <= 1.
 */
internal fun spearmanCorrelation(ranksX: List<Double>, ranksY: List<Double>): Double {
    val n = ranksX.size
    if (n <= 1) return 0.0
    val dSquaredSum = ranksX.zip(ranksY).sumOf { (rx, ry) -> (rx - ry) * (rx - ry) }
    return 1.0 - (6.0 * dSquaredSum) / (n.toLong() * (n.toLong() * n.toLong() - 1).toLong())
}

/**
 * Assigns Spearman ranks to [values], handling ties via equal-rank averaging (1-indexed).
 *
 * Tie-handling: tied entries receive the arithmetic mean of the ranks they would occupy if they
 * were ordered arbitrarily. For example, if two entries tie for ranks 2 and 3, both receive rank
 * 2.5.
 *
 * Returns a [List<Double>] of the same size as [values], where entry [i] holds the Spearman rank of
 * `values[i]`.
 *
 * @param values The numeric series to rank.
 * @return 1-indexed fractional rank for each entry (ties receive averaged rank).
 */
internal fun spearmanRanks(values: List<Double>): List<Double> {
    if (values.isEmpty()) return emptyList()
    // Build (value, original-index) pairs and sort ascending by value.
    val sorted = values.mapIndexed { i, v -> v to i }.sortedBy { it.first }
    val ranks = DoubleArray(values.size)
    var i = 0
    while (i < sorted.size) {
        // Find the end of the tie group (all elements with the same value).
        var j = i
        while (j < sorted.size && sorted[j].first == sorted[i].first) j++
        // Average rank for this group: 1-indexed, so rank = i+1..j averaged.
        val avgRank = (i + 1 + j) / 2.0
        for (k in i until j) {
            ranks[sorted[k].second] = avgRank
        }
        i = j
    }
    return ranks.toList()
}

/**
 * Returns `true` if the emitted GBC RGB555 palette contains at least one 4-entry sub-palette whose
 * luminance order is a strict full reversal of the corresponding source PLTE sub-palette (Spearman
 * rank correlation == -1.0, EPSILON=1e-9).
 *
 * **Predicate (D-02c + Phase 13.8 Req 1 — per-sub-palette ranking):**
 * - The emitted palette is divided into groups of 4 entries (GBC sub-palettes).
 * - For each group: compute Rec.601 luminance for both the source and emitted sides, apply Spearman
 *   ranking within the group (4 entries), and check if the correlation is a strict full reversal (<
 *   -1.0 + EPSILON).
 * - Return `true` if ANY group is a strict reversal.
 * - Degenerate groups (max-min luminance < EPSILON) are skipped (no false positive).
 * - Source luminance baseline: Rec.601 `lum = 0.299*r + 0.587*g + 0.114*b` on PLTE entries read via
 *   [IndexColorModel.getReds] / [IndexColorModel.getGreens] / [IndexColorModel.getBlues], with each
 *   channel quantized to the RGB555 grid via `to5(c) = (c * 31 / 255) * 255 / 31` before the
 *   Rec.601 sum (Req 3, Phase 13.8-02). This ensures 8-bit-distinct source colors that collapse to
 *   the same RGB555 value rank as tied (not distinct).
 * - Emitted luminance: decompose each RGB555 value as `r5 = v and 0x1F`, `g5 = (v shr 5) and 0x1F`,
 *   `b5 = (v shr 10) and 0x1F`, scale `r8 = r5*255/31`, then Rec.601.
 * - Ties: equal-rank averaging via [spearmanRanks].
 * - Strict full-reversal threshold: `spearman < -1.0 + EPSILON` (EPSILON=1e-9).
 *
 * **Why per-sub-palette (Req 1 / WR-01 from 13.7-REVIEW):** The previous flat 16-entry Spearman
 * ranking was mathematically dead for shipped assets like `world1-tileset.png`: a 16-entry palette
 * with 12 zero-padded trailing entries creates a mass-tie at zero luminance, making the strict -1.0
 * threshold unreachable even when the first 4-entry sub-palette IS a genuine reversal.
 * Per-sub-palette ranking evaluates each GBC sub-palette group independently, reviving the BG
 * polarity guard.
 *
 * **Degenerate guards (returns false — no false positive):**
 * - Source PNG not readable or not indexed (no [IndexColorModel])
 * - Emitted list size < 2 (cannot rank)
 * - Emitted list size > source PLTE map size (size mismatch)
 * - Emitted list size not a multiple of 4 (not a valid GBC palette)
 * - All source luminances in a sub-palette group are equal (degenerate — group skipped)
 * - Any exception during read/compute (sentinel idiom)
 *
 * **Does NOT read a `.c` file internally** — caller parses emitted palette values and passes them
 * as [emittedRgb555Values]. This preserves testability with synthetic fixtures.
 *
 * Phase 13.7 Plan 02 + Phase 13.8 Plan 01 (Req 1 per-sub-palette rewrite): the ONE shared
 * comparator consumed by BG+OBJ pipelines and emission tests.
 *
 * @param sourcePng The source indexed PNG (must have an IndexColorModel with valid PLTE).
 * @param emittedRgb555Values The emitted palette as a list of GBC RGB555 packed integers (layout:
 *   `(b5 shl 10) or (g5 shl 5) or r5`). Size must be a multiple of 4.
 * @return `true` when ANY 4-entry sub-palette Spearman rank correlation == -1.0 (strictly
 *   inverted); `false` otherwise.
 */
internal fun checkPalettePolarity(sourcePng: File, emittedRgb555Values: List<Int>): Boolean {
    return try {
        val img = ImageIO.read(sourcePng) ?: return false
        val cm = img.colorModel as? IndexColorModel ?: return false
        val mapSize = cm.mapSize

        // The number of entries to compare is the emitted list size.
        // NOTE: JVM's ImageIO.read may pad an indexed PNG's PLTE to 256 entries when round-tripping
        // through IndexColorModel (the padding entries are zeros). The emitted C palette array
        // contains only the actual palette entries (e.g., 4 or 16), not the JVM-padded 256.
        // We compare only the first N = emittedRgb555Values.size entries from the source PLTE.
        val n = emittedRgb555Values.size

        // Guard: degenerate — need at least 2 entries to rank.
        if (n < 2) return false
        // Guard: emitted has more entries than the source PLTE actually has.
        if (n > mapSize) return false
        // Guard: not a valid GBC palette — must be a multiple of 4 (4 entries per sub-palette).
        val subPaletteSize = 4
        if (n % subPaletteSize != 0) return false

        // Step 1: Read source PLTE via IndexColorModel.
        // getReds/getGreens/getBlues fill a ByteArray; values are signed bytes (0x00-0xFF).
        // Convert to unsigned Int (0-255) via `and 0xFF` for Rec.601 arithmetic.
        val srcReds = ByteArray(mapSize).also { cm.getReds(it) }
        val srcGreens = ByteArray(mapSize).also { cm.getGreens(it) }
        val srcBlues = ByteArray(mapSize).also { cm.getBlues(it) }

        // Step 2: Compute Rec.601 luminance for the first N source PLTE entries.
        // Req 3 (Phase 13.8-02): quantize source PLTE values onto the RGB555 grid BEFORE
        // computing luminance, using to5(c) = (c * 31 / 255) * 255 / 31 (double truncation
        // per the 13.7 review recommendation). This ensures the source-side and emitted-side
        // luminances live on the same 5-bit grid the hardware displays, so 8-bit-distinct
        // source colors that collapse to identical RGB555 values rank as tied (not distinct).
        fun to5(c: Int): Int = (c * 31 / 255) * 255 / 31
        val sourceLum =
            (0 until n).map { i ->
                0.299 * to5(srcReds[i].toInt() and 0xFF) +
                    0.587 * to5(srcGreens[i].toInt() and 0xFF) +
                    0.114 * to5(srcBlues[i].toInt() and 0xFF)
            }

        // Step 3: Compute Rec.601 luminance for each emitted RGB555 value.
        val emittedLum =
            emittedRgb555Values.map { v ->
                val r5 = v and 0x1F
                val g5 = (v shr 5) and 0x1F
                val b5 = (v shr 10) and 0x1F
                val r8 = r5 * 255 / 31
                val g8 = g5 * 255 / 31
                val b8 = b5 * 255 / 31
                0.299 * r8 + 0.587 * g8 + 0.114 * b8
            }

        // Step 4: Per-sub-palette Spearman ranking (Req 1 — revives dead flat-ranking guard).
        // Iterate 4-entry GBC sub-palette groups. Return true if ANY group is strictly inverted.
        (0 until n step subPaletteSize).any { base ->
            val srcGroup = sourceLum.subList(base, base + subPaletteSize)
            val emGroup = emittedLum.subList(base, base + subPaletteSize)

            // Guard: degenerate sub-palette — all-equal luminance in this group → skip (no false
            // positive).
            val minLum = srcGroup.min()
            val maxLum = srcGroup.max()
            if (maxLum - minLum < SPEARMAN_EPSILON) return@any false

            // Rank within the sub-palette group and check for strict full reversal.
            // Note on the 0.0 sentinel: [spearmanCorrelation] returns 0.0 for n <= 1
            // (degenerate — see its KDoc). Sub-palette groups are always 4 entries here
            // (the `n % subPaletteSize != 0` guard above ensures this), so 0.0 is
            // unreachable from the n <= 1 path. The 0.0 value can still be returned when
            // all d-squared terms cancel (perfectly uncorrelated ranks), but in that case
            // `0.0 < -1.0 + EPSILON` is `false` — correct: not inverted.
            val sourceRanks = spearmanRanks(srcGroup)
            val emittedRanks = spearmanRanks(emGroup)
            spearmanCorrelation(sourceRanks, emittedRanks) < -1.0 + SPEARMAN_EPSILON
        }
    } catch (_: Exception) {
        false // sentinel: treat any failure as "cannot determine" = not inverted
    }
}

/**
 * Parses the RGB555 integer values from a `const palette_color_t <symbol>[N] = { ... }` declaration
 * in a png2asset-emitted .c file.
 *
 * Single source of truth for palette parse + RGB555 packing (Req 2, WR-03, Phase 13.8-02). Replaces
 * the formerly duplicate `parseSpriteRgb555Values` in [ConvertSpritesTask] and
 * `parsePaletteRgb555Values` + `parseRgb8OrIntValues` in [ConvertZoneTilesetsTask].
 *
 * Handles both the GBDK `RGB8(r,g,b)` macro form (png2asset's primary output) and raw decimal/hex
 * integer fallback. RGB8 is converted to RGB555 using: `r5 = r8 * 31 / 255; g5 = g8 * 31 / 255; b5
 * = b8 * 31 / 255` `rgb555 = (b5 shl 10) or (g5 shl 5) or r5`
 *
 * **Empty-result contract (IN-02):** Returns `null` when the symbol is not found OR when the parsed
 * value list is empty. This is uniform across both call sites — unlike the former
 * `parseRgb8OrIntValues` which returned an empty list (not null), creating a drift hazard for
 * future callers.
 *
 * @param file The png2asset-emitted .c file to parse.
 * @param symbol The palette array symbol name (e.g., `elephant_palettes` or `_zone_..._palettes`).
 * @return List of RGB555 packed integers, or null if the symbol is not found or no values parsed.
 *
 * Phase 13.8 Plan 02 (Req 2): consolidated from [ConvertSpritesTask] + [ConvertZoneTilesetsTask].
 */
internal fun parsePaletteRgb555Values(file: File, symbol: String): List<Int>? {
    val text = file.readText()
    val headerPattern =
        Regex(
            """const\s+palette_color_t\s+${Regex.escape(symbol)}\s*\[\s*\d+\s*\]\s*=\s*\{([^}]*)\}""",
            RegexOption.DOT_MATCHES_ALL,
        )
    val match = headerPattern.find(text) ?: return null
    val body = match.groupValues[1]
    val rgb8Pattern = Regex("""RGB8\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)""")
    val result = mutableListOf<Int>()
    var foundRgb8 = false
    for (m in rgb8Pattern.findAll(body)) {
        val r8 = m.groupValues[1].toInt()
        val g8 = m.groupValues[2].toInt()
        val b8 = m.groupValues[3].toInt()
        val r5 = r8 * 31 / 255
        val g5 = g8 * 31 / 255
        val b5 = b8 * 31 / 255
        result.add((b5 shl 10) or (g5 shl 5) or r5)
        foundRgb8 = true
    }
    if (foundRgb8) return if (result.isEmpty()) null else result
    // Fallback: raw decimal/hex integers
    body.split(",").forEach { token ->
        val trimmed = token.trim()
        val v =
            trimmed.toIntOrNull()
                ?: trimmed.removePrefix("0x").toLongOrNull(16)?.toInt()
                ?: trimmed.removePrefix("0X").toLongOrNull(16)?.toInt()
        if (v != null) result.add(v)
    }
    return if (result.isEmpty()) null else result
}

/**
 * Pre-permutes an indexed PNG so the transparent color (tRNS chunk) is at palette index 0.
 *
 * Implements Mechanism A (RESEARCH.md Pattern 2) with a compact remap that skips 0-pixel palette
 * entries (RESEARCH.md Pitfall 1 — keeping unused entries pushes body color to index 4+, which 2bpp
 * cannot encode → body pixels merge into the transparent slot).
 *
 * **Remap algorithm (verified by live png2asset run 2026-06-05):**
 * 1. Count pixel usage per palette index via [java.awt.image.Raster.getSample]
 * 2. Build compact remap: `transparentIdx → 0`; for each `old` in palette order where `old !=
 *    transparentIdx && pixelCounts[old] > 0`: `old → nextNew++`
 * 3. Construct a new [IndexColorModel] with compact palette (transparent alpha=0 at index 0)
 * 4. Copy remapped pixel data into a new [BufferedImage]
 * 5. Write the result to a temp file via [ImageIO.write] (Option i — A2 verdict from Plan 01
 *    [ImageIoIndexedRoundTripTest] confirmed JVM PNG encoder preserves indexed color-type 3; raw
 *    chunk manipulation [Option ii] is not required)
 *
 * **Elephant example (tRNS=4):** `{4→0, 0→1, 1→2, 3→3}` — source index 2 (bright-green, 0 pixels)
 * is skipped. Result: 4-entry palette, body at index 3, NOT index 4.
 *
 * **Deterministic temp filename (REQ-6, Phase 13.6-07 / Phase 13.8-03 W2):** The temp file is named
 * `gbkt_permuted_<stemName>.png` in [buildTempDir], where [stemName] is the sprite id / output stem
 * derived from the include path (e.g. "elephant" for `sprites/elephant.png`). Using [stemName]
 * rather than `file.nameWithoutExtension` eliminates the same-basename-different-subdir collision
 * (e.g. `player/idle.png` vs `enemy/idle.png` both named "idle" would collide with the old name).
 * This makes the generated C's "Conversion args" comment reproducible across rebuilds (png2asset
 * echoes the input path into the comment). Any stale same-name file is deleted before writing so
 * reruns are clean. The directory is created with [File.mkdirs] by the caller.
 *
 * **Threat mitigations:**
 * - Path traversal (T-13.6-03a): temp file is written into [buildTempDir] which is under the
 *   trusted build directory tree (not the asset directory and not the system TMPDIR).
 * - Temp-file leak (T-13.6-03b): [ImageIO.write] is guarded — if it throws, the temp file is
 *   deleted before rethrowing. Caller must also delete the returned file in a `finally` block to
 *   cover the path from here to exec completion.
 *
 * @param file The source indexed PNG (must have a tRNS chunk; caller ensures transparentIdx >= 0).
 * @param transparentIdx The palette index to place at index 0 (the tRNS transparent color).
 * @param buildTempDir A stable build directory for the deterministic temp file (mkdirs already
 *   called by caller).
 * @param stemName The sprite id / output stem (e.g. "elephant") used as the temp filename key.
 *   Keying on [stemName] instead of [file.nameWithoutExtension] prevents same-basename collisions
 *   when two sprites share a basename but live in different subdirectories (W2, Phase 13.8-03).
 * @return A PNG file in [buildTempDir] named `gbkt_permuted_<stemName>.png` with the
 *   compact-remapped palette. Caller is responsible for deletion (use a `finally` block).
 *
 * Phase 13.6 REQ-2: core auto-route mechanism for non-zero tRNS indexed PNGs. Phase 13.6-07 REQ-6:
 * deterministic temp name for reproducible generated C. Phase 13.8-03 W2: stemName-keyed temp name
 * to eliminate same-basename-different-subdir collision.
 */
internal fun prePermuteIndexedPng(
    file: File,
    transparentIdx: Int,
    buildTempDir: File,
    stemName: String,
): File {
    val img =
        ImageIO.read(file)
            ?: error("prePermuteIndexedPng: ImageIO.read returned null for ${file.name}")
    val cm =
        img.colorModel as? IndexColorModel
            ?: error(
                "prePermuteIndexedPng: ${file.name} is not an indexed PNG (no IndexColorModel)"
            )
    val raster = img.raster
    val mapSize = cm.mapSize

    // Step 1: Count pixel usage per palette index (single pass — Pitfall 3: avoid double read).
    val pixelCounts = IntArray(mapSize)
    for (y in 0 until img.height) {
        for (x in 0 until img.width) {
            val idx = raster.getSample(x, y, 0)
            if (idx in pixelCounts.indices) pixelCounts[idx]++
        }
    }

    // Step 2: Build compact remap.
    // transparentIdx → 0; used visible entries → 1..N in source order; 0-pixel entries SKIPPED.
    // Pitfall 1: keeping 0-pixel entries would push body to index 4, exceeding 2bpp range.
    val remap = IntArray(mapSize) { -1 } // -1 = unused entry (0-pixel non-transparent)
    remap[transparentIdx] = 0
    var nextNew = 1
    for (old in 0 until mapSize) {
        if (old != transparentIdx && pixelCounts[old] > 0) {
            remap[old] = nextNew++
        }
    }
    val newSize = nextNew // compact palette size: 1 transparent + N used visible

    // Step 3: Extract palette byte arrays from the original IndexColorModel and build new compact
    // palette.
    val srcReds = ByteArray(mapSize).also { cm.getReds(it) }
    val srcGreens = ByteArray(mapSize).also { cm.getGreens(it) }
    val srcBlues = ByteArray(mapSize).also { cm.getBlues(it) }

    val newReds = ByteArray(newSize)
    val newGreens = ByteArray(newSize)
    val newBlues = ByteArray(newSize)
    val newAlphas = ByteArray(newSize) { 0xFF.toByte() } // all opaque by default

    for (old in 0 until mapSize) {
        val new = remap[old]
        if (new < 0) continue // 0-pixel unused entry — skip
        newReds[new] = srcReds[old]
        newGreens[new] = srcGreens[old]
        newBlues[new] = srcBlues[old]
        if (old == transparentIdx) {
            newAlphas[new] = 0x00.toByte() // transparent at index 0
        }
    }

    // Step 4: Construct compact IndexColorModel and copy remapped pixel data into new
    // BufferedImage.
    val newCm = IndexColorModel(8, newSize, newReds, newGreens, newBlues, newAlphas)
    val newImg = BufferedImage(img.width, img.height, BufferedImage.TYPE_BYTE_INDEXED, newCm)
    val newRaster = newImg.raster
    for (y in 0 until img.height) {
        for (x in 0 until img.width) {
            val oldIdx = raster.getSample(x, y, 0)
            val newIdx = if (oldIdx in remap.indices && remap[oldIdx] >= 0) remap[oldIdx] else 0
            newRaster.setSample(x, y, 0, newIdx)
        }
    }

    // Step 5: Write to deterministic build-temp file (REQ-6 / Phase 13.6-07 / W2 Phase 13.8-03).
    // Name: gbkt_permuted_<stemName>.png in buildTempDir (stable across rebuilds, collision-free).
    // stemName is the sprite id / output stem keyed by the caller — not file.nameWithoutExtension,
    // so two sprites in different subdirs with the same basename (e.g. player/idle + enemy/idle)
    // get distinct temp names (gbkt_permuted_player.png vs gbkt_permuted_enemy.png).
    // Option i: ImageIO.write — A2 verdict (Plan 01) confirms indexed color-type 3 is preserved.
    // WR-01: delete any stale same-name file before writing (clean reruns), and guard the write
    // so a throwing ImageIO.write deletes the temp before rethrowing (no orphaned file).
    val temp = File(buildTempDir, "gbkt_permuted_${stemName}.png")
    temp.delete() // remove stale file from a prior run (safe: File.delete returns false if absent)
    try {
        ImageIO.write(newImg, "PNG", temp)
    } catch (e: Exception) {
        temp.delete()
        throw e
    }
    return temp
}
