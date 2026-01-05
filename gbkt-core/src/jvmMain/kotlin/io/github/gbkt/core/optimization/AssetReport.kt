/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.optimization

/**
 * Complete analysis result for all game assets.
 *
 * Contains summary statistics, per-asset breakdowns, and actionable suggestions for optimizing Game
 * Boy tile data.
 */
data class AssetReport(
    val assets: List<AnalyzedAsset>,
    val summary: AssetSummary,
    val suggestions: List<Suggestion>,
    val analysisTimeMs: Long,
) {
    /** Whether any optimization opportunities were found. */
    val hasIssues: Boolean
        get() = suggestions.isNotEmpty()

    /** Total potential byte savings if all suggestions are applied. */
    val potentialSavings: ByteSavings
        get() = summary.potentialSavings
}

/** Summary statistics across all analyzed assets. */
data class AssetSummary(
    val totalAssets: Int,
    val totalTiles: Int,
    val uniqueTiles: Int,
    val duplicateTiles: Int,
    val emptyTiles: Int,
    val lowEntropyTiles: Int,
    val usedPaletteColors: Int,
    val potentialSavings: ByteSavings,
) {
    /** Ratio of unique tiles to total tiles (1.0 = no duplicates). */
    val deduplicationRatio: Float
        get() = if (totalTiles > 0) uniqueTiles.toFloat() / totalTiles else 1f

    /** Efficiency percentage (100% = optimal). */
    val efficiency: Int
        get() = (deduplicationRatio * 100).toInt()
}

/** Represents byte savings from optimization. */
data class ByteSavings(val bytes: Int, val tiles: Int) {
    /** Human-readable format (e.g., "96 bytes (6 tiles)" or "2 KB (128 tiles)"). */
    val formatted: String
        get() =
            when {
                bytes == 0 -> "0 bytes"
                bytes >= 1024 -> "${bytes / 1024} KB ($tiles tiles)"
                else -> "$bytes bytes ($tiles tiles)"
            }

    operator fun plus(other: ByteSavings) = ByteSavings(bytes + other.bytes, tiles + other.tiles)

    companion object {
        val ZERO = ByteSavings(0, 0)

        /** Calculate savings from removing duplicate tiles. */
        fun fromDuplicates(duplicateCount: Int) =
            ByteSavings(
                bytes = duplicateCount * 16, // Each tile is 16 bytes
                tiles = duplicateCount,
            )
    }
}
