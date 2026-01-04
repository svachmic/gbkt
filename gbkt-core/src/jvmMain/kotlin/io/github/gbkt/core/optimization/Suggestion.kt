/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.optimization

/**
 * Actionable optimization suggestion.
 *
 * Each suggestion includes:
 * - What was found (title, description)
 * - How severe it is (severity)
 * - What to do about it (action)
 * - How much it would save (savings, if applicable)
 */
sealed class Suggestion {
    abstract val severity: Severity
    abstract val title: String
    abstract val description: String
    abstract val savings: ByteSavings?
    abstract val action: String

    /** Duplicate tiles were found across assets. */
    data class DeduplicateTiles(
        val duplicates: List<DuplicateTileInfo>,
        override val savings: ByteSavings
    ) : Suggestion() {
        override val severity = Severity.INFO
        override val title = "Duplicate tiles detected"
        override val description: String
            get() =
                "${duplicates.sumOf { it.count - 1 }} tiles are duplicates across ${duplicates.size} unique patterns"

        override val action = "Consider using a shared tileset or enabling automatic deduplication"
    }

    /** Empty (fully transparent) tiles were found. */
    data class RemoveEmptyTiles(
        val emptyTiles: List<TileLocation>,
        override val savings: ByteSavings
    ) : Suggestion() {
        override val severity = Severity.WARNING
        override val title = "Empty tiles found"
        override val description: String
            get() = "${emptyTiles.size} completely transparent tiles"

        override val action = "Remove empty tiles or reference a single shared empty tile"
    }

    /** Low-entropy tiles (mostly single color) were found. */
    data class ConsolidateLowEntropy(val tiles: List<LowEntropyTile>) : Suggestion() {
        override val severity = Severity.INFO
        override val title = "Low-entropy tiles"
        override val description: String
            get() {
                val nearlySolid = tiles.count { it.isNearlySolid }
                return if (nearlySolid > 0) {
                    "${tiles.size} tiles are mostly single-color ($nearlySolid are 90%+ solid)"
                } else {
                    "${tiles.size} tiles have low visual complexity"
                }
            }

        override val savings: ByteSavings? = null
        override val action = "Consider using background color or tile flipping for solid areas"
    }

    /** Unused palette colors in an asset. */
    data class OptimizePalette(val asset: String, val unusedColors: List<Int>) : Suggestion() {
        override val severity = Severity.INFO
        override val title = "Unused palette colors in $asset"
        override val description: String
            get() = "${unusedColors.size} of 4 palette slots are unused"

        override val savings: ByteSavings? = null
        override val action = "Reduce color count or use unused slots for additional detail"
    }

    /** Similar but not identical tiles that could potentially be merged. */
    data class MergeSimilarTiles(
        val pairs: List<SimilarTilePair>,
        val estimatedSavings: ByteSavings
    ) : Suggestion() {
        override val severity = Severity.INFO
        override val title = "Similar tiles detected"
        override val description: String
            get() = "${pairs.size} tile pairs differ by only a few pixels"

        override val savings: ByteSavings = estimatedSavings
        override val action = "Review similar tiles - small differences may be unintentional"
    }

    /** RLE compression could be beneficial. */
    data class EnableCompression(val longestRun: Int, val estimatedRatio: Float) : Suggestion() {
        override val severity = Severity.INFO
        override val title = "Compression opportunity"
        override val description: String
            get() = "Found runs of $longestRun consecutive identical tiles"

        override val savings: ByteSavings? = null
        override val action =
            "Consider RLE compression for tile data (est. ${(estimatedRatio * 100).toInt()}% of original size)"
    }

    /** Cross-asset duplicate tiles. */
    data class ShareTilesBetweenAssets(
        val sharedTiles: List<CrossAssetDuplicate>,
        override val savings: ByteSavings
    ) : Suggestion() {
        override val severity = Severity.INFO
        override val title = "Tiles shared between assets"
        override val description: String
            get() = "${sharedTiles.size} tiles appear in multiple assets"

        override val action = "Consider extracting shared tiles into a common tileset"
    }
}

/** Severity level for suggestions. */
enum class Severity {
    /** Informational - nice to know but not critical. */
    INFO,
    /** Warning - should be addressed but not breaking. */
    WARNING,
    /** Error - significant issue that should be fixed. */
    ERROR
}

/** Tile that appears in multiple assets. */
data class CrossAssetDuplicate(val hash: Int, val assets: List<String>, val totalCount: Int) {
    val savings: ByteSavings
        get() = ByteSavings((totalCount - 1) * 16, totalCount - 1)
}
