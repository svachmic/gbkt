/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.optimization

import io.github.gbkt.core.AssetPipeline.Tile

/** Analysis result for a single asset (sprite or tilemap). */
data class AnalyzedAsset(
    val name: String,
    val path: String,
    val type: AssetType,
    val dimensions: Dimensions,
    val tiles: TileAnalysis,
    val palette: PaletteAnalysis,
    val compression: CompressionAnalysis,
) {
    /** Computed optimization score for this asset. */
    val score: OptimizationScore
        get() = OptimizationScore.compute(this)
}

enum class AssetType {
    SPRITE,
    TILEMAP,
    TILESET,
}

/** Dimensions of an asset in pixels and tiles. */
data class Dimensions(val widthPx: Int, val heightPx: Int, val tilesWide: Int, val tilesHigh: Int) {
    val totalTiles: Int
        get() = tilesWide * tilesHigh
}

/** Tile-level analysis for an asset. */
data class TileAnalysis(
    val total: Int,
    val unique: Int,
    val duplicates: List<DuplicateTileInfo>,
    val empty: List<TileLocation>,
    val lowEntropy: List<LowEntropyTile>,
) {
    val duplicateCount: Int
        get() = total - unique

    val emptyCount: Int
        get() = empty.size

    val lowEntropyCount: Int
        get() = lowEntropy.size
}

/** Information about duplicate tiles within an asset. */
data class DuplicateTileInfo(val tile: Tile, val locations: List<TileLocation>, val hash: Int) {
    /** Number of times this tile appears. */
    val count: Int
        get() = locations.size

    /** Bytes that could be saved by deduplicating (keeping one copy). */
    val savings: ByteSavings
        get() = ByteSavings((count - 1) * 16, count - 1)
}

/** Location of a tile within an asset grid. */
data class TileLocation(val assetName: String, val tileIndex: Int, val gridX: Int, val gridY: Int) {
    /** Human-readable position string. */
    val position: String
        get() = "($gridX, $gridY)"
}

/** Tile with low entropy (mostly single color). */
data class LowEntropyTile(
    val location: TileLocation,
    val entropy: Float,
    val dominantColor: Int,
    val colorCoverage: Float, // Percentage of pixels using dominant color (0.0-1.0)
) {
    /** True if tile is 90%+ single color. */
    val isNearlySolid: Boolean
        get() = colorCoverage >= 0.9f
}

/** Palette usage analysis for an asset. */
data class PaletteAnalysis(
    val colorsUsed: Set<Int>,
    val unusedSlots: List<Int>,
    val colorFrequencies: Map<Int, Int>,
) {
    /** Number of unused palette slots (out of 4). */
    val wastedSlots: Int
        get() = unusedSlots.size

    /** True if all 4 palette colors are used. */
    val isOptimal: Boolean
        get() = wastedSlots == 0

    /** Most frequently used color. */
    val dominantColor: Int?
        get() = colorFrequencies.maxByOrNull { it.value }?.key
}

/** Compression opportunity analysis. */
data class CompressionAnalysis(
    val rleOpportunity: RLEOpportunity,
    val similarTiles: List<SimilarTilePair>,
) {
    val hasOpportunities: Boolean
        get() = rleOpportunity.hasConsecutiveDuplicates || similarTiles.isNotEmpty()
}

/** Run-Length Encoding opportunity. */
data class RLEOpportunity(
    val hasConsecutiveDuplicates: Boolean,
    val longestRun: Int,
    val estimatedRatio: Float, // Estimated compression ratio (1.0 = no compression)
) {
    val worthwhile: Boolean
        get() = longestRun >= 3
}

/** Pair of tiles that are similar but not identical. */
data class SimilarTilePair(
    val tile1: TileLocation,
    val tile2: TileLocation,
    val similarity: Float, // 0.0 - 1.0
) {
    /** True if tiles differ by only a few bytes. */
    val isNearlyIdentical: Boolean
        get() = similarity >= 0.9f
}

/** Optimization score for quick assessment of an asset. */
data class OptimizationScore(
    val value: Int, // 0-100
    val grade: Grade,
) {
    enum class Grade(val label: String, val symbol: String) {
        EXCELLENT("Excellent", "A"),
        GOOD("Good", "B"),
        FAIR("Fair", "C"),
        POOR("Needs work", "D"),
        CRITICAL("Critical", "F"),
    }

    companion object {
        fun compute(asset: AnalyzedAsset): OptimizationScore {
            var score = 100

            // Penalize duplicates (up to 30 points)
            if (asset.tiles.total > 0) {
                val dupeRatio = asset.tiles.duplicateCount.toFloat() / asset.tiles.total
                score -= (dupeRatio * 30).toInt()
            }

            // Penalize empty tiles (up to 20 points)
            if (asset.tiles.total > 0) {
                val emptyRatio = asset.tiles.emptyCount.toFloat() / asset.tiles.total
                score -= (emptyRatio * 20).toInt()
            }

            // Penalize low-entropy tiles (up to 10 points)
            if (asset.tiles.total > 0) {
                val lowEntropyRatio = asset.tiles.lowEntropyCount.toFloat() / asset.tiles.total
                score -= (lowEntropyRatio * 10).toInt()
            }

            // Penalize palette waste (5 points per unused slot)
            score -= asset.palette.wastedSlots * 5

            val grade =
                when {
                    score >= 90 -> Grade.EXCELLENT
                    score >= 75 -> Grade.GOOD
                    score >= 60 -> Grade.FAIR
                    score >= 40 -> Grade.POOR
                    else -> Grade.CRITICAL
                }

            return OptimizationScore(score.coerceIn(0, 100), grade)
        }
    }
}
