/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.optimization

import io.github.gbkt.core.AssetPipeline
import io.github.gbkt.core.AssetPipeline.SpriteSheet
import io.github.gbkt.core.AssetPipeline.Tile
import io.github.gbkt.core.Game
import io.github.gbkt.core.TiledParser
import io.github.gbkt.core.graphics.Sprite
import io.github.gbkt.core.graphics.TileMap
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.log2

/**
 * Core asset analysis engine.
 *
 * Performs comprehensive analysis of Game Boy assets to identify optimization opportunities
 * including:
 * - Duplicate tiles (within and across assets)
 * - Empty/transparent tiles
 * - Low-entropy tiles (mostly single color)
 * - Palette waste (unused color slots)
 * - Compression opportunities
 *
 * Usage:
 * ```
 * val analyzer = AssetAnalyzer()
 * val report = analyzer.analyze(game, assetDir)
 * ```
 */
class AssetAnalyzer(private val config: AnalyzerConfig = AnalyzerConfig()) {

    /**
     * Analyze all assets in a game.
     *
     * @param game The Game object containing sprite and tilemap definitions
     * @param assetDir Optional directory containing asset files
     * @return Complete analysis report
     */
    fun analyze(game: Game, assetDir: File? = null): AssetReport {
        val startTime = System.currentTimeMillis()
        val assets = mutableListOf<AnalyzedAsset>()
        val dir = assetDir ?: game.assetDir?.let { File(it) }

        // Collect all tiles for cross-asset duplicate detection
        val globalTileMap = mutableMapOf<Int, MutableList<TileLocation>>()

        // Analyze sprites
        for (sprite in game.sprites) {
            val file = dir?.let { File(it, sprite.asset) }
            if (file?.exists() == true) {
                try {
                    val analyzed = analyzeSprite(sprite, file)
                    assets.add(analyzed)

                    // Add to global tile map for cross-asset detection
                    collectTilesForGlobalAnalysis(analyzed.name, file, globalTileMap)
                } catch (e: Exception) {
                    // Skip assets that fail to load
                }
            }
        }

        // Analyze tilemaps
        for (tilemap in game.tilemaps) {
            val file = dir?.let { File(it, tilemap.asset) }
            if (file?.exists() == true) {
                try {
                    val analyzed = analyzeTilemap(tilemap, file, dir)
                    if (analyzed != null) {
                        assets.add(analyzed)
                    }
                } catch (e: Exception) {
                    // Skip assets that fail to load
                }
            }
        }

        val crossAssetDuplicates = findCrossAssetDuplicates(globalTileMap)
        val summary = computeSummary(assets, crossAssetDuplicates)
        val suggestions = generateSuggestions(assets, summary, crossAssetDuplicates)
        val elapsed = System.currentTimeMillis() - startTime

        return AssetReport(assets, summary, suggestions, elapsed)
    }

    /** Analyze a single sprite asset. */
    fun analyzeSprite(sprite: Sprite, file: File): AnalyzedAsset {
        val image = ImageIO.read(file)
        val sheet = AssetPipeline.loadSprite(file)

        return AnalyzedAsset(
            name = sprite.name,
            path = file.path,
            type = AssetType.SPRITE,
            dimensions =
                Dimensions(
                    widthPx = image.width,
                    heightPx = image.height,
                    tilesWide = sheet.widthInTiles,
                    tilesHigh = sheet.heightInTiles
                ),
            tiles = analyzeTiles(sprite.name, sheet),
            palette = analyzePalette(image),
            compression = analyzeCompression(sheet)
        )
    }

    /** Analyze a sprite file directly (without Sprite object). */
    fun analyzeSprite(file: File): AnalyzedAsset {
        val image = ImageIO.read(file)
        val sheet = AssetPipeline.loadSprite(file)
        val name = file.nameWithoutExtension

        return AnalyzedAsset(
            name = name,
            path = file.path,
            type = AssetType.SPRITE,
            dimensions =
                Dimensions(
                    widthPx = image.width,
                    heightPx = image.height,
                    tilesWide = sheet.widthInTiles,
                    tilesHigh = sheet.heightInTiles
                ),
            tiles = analyzeTiles(name, sheet),
            palette = analyzePalette(image),
            compression = analyzeCompression(sheet)
        )
    }

    /** Analyze a tilemap asset. */
    private fun analyzeTilemap(tilemap: TileMap, file: File, assetDir: File?): AnalyzedAsset? {
        val map = TiledParser.parse(file)
        val layer =
            tilemap.layerName?.let { map.getLayer(it) } ?: map.getVisibleLayer() ?: return null

        val tileset = map.tilesets.firstOrNull() ?: return null
        val tilesetPath =
            tilemap.tilesetAsset ?: tileset.image.takeIf { it.isNotEmpty() } ?: return null

        val tilesetFile = assetDir?.let { File(it, tilesetPath) } ?: File(tilesetPath)

        if (!tilesetFile.exists()) return null

        val image = ImageIO.read(tilesetFile)
        val sheet = AssetPipeline.loadSprite(tilesetFile)

        return AnalyzedAsset(
            name = file.nameWithoutExtension,
            path = file.path,
            type = AssetType.TILEMAP,
            dimensions =
                Dimensions(
                    widthPx = layer.width * 8,
                    heightPx = layer.height * 8,
                    tilesWide = layer.width,
                    tilesHigh = layer.height
                ),
            tiles = analyzeTiles(file.nameWithoutExtension, sheet),
            palette = analyzePalette(image),
            compression = analyzeCompression(sheet)
        )
    }

    /** Analyze tiles in a sprite sheet. */
    private fun analyzeTiles(assetName: String, sheet: SpriteSheet): TileAnalysis {
        val tileMap = mutableMapOf<Int, MutableList<TileLocation>>()
        val emptyTiles = mutableListOf<TileLocation>()
        val lowEntropyTiles = mutableListOf<LowEntropyTile>()

        for ((index, tile) in sheet.tiles.withIndex()) {
            val gridX = index % sheet.widthInTiles
            val gridY = index / sheet.widthInTiles
            val location = TileLocation(assetName, index, gridX, gridY)

            // Check for empty tile
            if (config.detectEmpty && isEmptyTile(tile)) {
                emptyTiles.add(location)
            }

            // Check for low entropy
            if (config.detectLowEntropy) {
                val entropy = computeEntropy(tile)
                if (entropy < config.lowEntropyThreshold) {
                    val (dominant, coverage) = findDominantColor(tile)
                    lowEntropyTiles.add(LowEntropyTile(location, entropy, dominant, coverage))
                }
            }

            // Track for duplicate detection (uses Tile.hashCode which is content-based)
            if (config.detectDuplicates) {
                tileMap.getOrPut(tile.hashCode()) { mutableListOf() }.add(location)
            }
        }

        // Build duplicate info
        val duplicates =
            if (config.detectDuplicates) {
                tileMap.entries
                    .filter { it.value.size > 1 }
                    .map { (hash, locations) ->
                        val firstTile = sheet.tiles[locations.first().tileIndex]
                        DuplicateTileInfo(firstTile, locations, hash)
                    }
            } else {
                emptyList()
            }

        return TileAnalysis(
            total = sheet.tiles.size,
            unique = tileMap.size,
            duplicates = duplicates,
            empty = emptyTiles,
            lowEntropy = lowEntropyTiles
        )
    }

    /** Check if a tile is completely empty (all zeros = transparent). */
    private fun isEmptyTile(tile: Tile): Boolean = tile.data.all { it == 0.toByte() }

    /**
     * Compute Shannon entropy for a tile. Low entropy indicates mostly one color (less visual
     * complexity).
     *
     * @return Entropy value from 0.0 (single color) to 2.0 (equal distribution of 4 colors)
     */
    private fun computeEntropy(tile: Tile): Float {
        val colorCounts = IntArray(4)

        // Decode 2bpp pixel data
        for (row in 0 until 8) {
            val lowByte = tile.data[row * 2].toInt() and 0xFF
            val highByte = tile.data[row * 2 + 1].toInt() and 0xFF

            for (col in 0 until 8) {
                val bit = 7 - col
                val color = ((highByte shr bit) and 1 shl 1) or ((lowByte shr bit) and 1)
                colorCounts[color]++
            }
        }

        // Compute Shannon entropy
        val total = 64.0
        var entropy = 0.0
        for (count in colorCounts) {
            if (count > 0) {
                val p = count / total
                entropy -= p * log2(p)
            }
        }

        return entropy.toFloat()
    }

    /** Find the dominant color and its coverage percentage in a tile. */
    private fun findDominantColor(tile: Tile): Pair<Int, Float> {
        val colorCounts = IntArray(4)

        for (row in 0 until 8) {
            val lowByte = tile.data[row * 2].toInt() and 0xFF
            val highByte = tile.data[row * 2 + 1].toInt() and 0xFF

            for (col in 0 until 8) {
                val bit = 7 - col
                val color = ((highByte shr bit) and 1 shl 1) or ((lowByte shr bit) and 1)
                colorCounts[color]++
            }
        }

        val dominant = colorCounts.indices.maxByOrNull { colorCounts[it] } ?: 0
        val coverage = colorCounts[dominant] / 64f
        return dominant to coverage
    }

    /** Analyze palette usage in an image. */
    private fun analyzePalette(image: BufferedImage): PaletteAnalysis {
        val colorsUsed = mutableSetOf<Int>()
        val colorFrequencies = mutableMapOf<Int, Int>()

        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                val alpha = (rgb shr 24) and 0xFF
                if (alpha >= 128) {
                    val gbColor = rgbToGBColor(rgb)
                    colorsUsed.add(gbColor)
                    colorFrequencies[gbColor] = colorFrequencies.getOrDefault(gbColor, 0) + 1
                }
            }
        }

        val unusedSlots = (0..3).filter { it !in colorsUsed }

        return PaletteAnalysis(colorsUsed, unusedSlots, colorFrequencies)
    }

    /** Convert RGB to Game Boy color index (0-3). */
    private fun rgbToGBColor(rgb: Int): Int {
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

        return when {
            luminance >= 192 -> 0
            luminance >= 128 -> 1
            luminance >= 64 -> 2
            else -> 3
        }
    }

    /** Analyze compression opportunities in a sprite sheet. */
    private fun analyzeCompression(sheet: SpriteSheet): CompressionAnalysis {
        // Analyze RLE opportunity (consecutive duplicate tiles)
        var longestRun = 1
        var currentRun = 1
        var lastHash = sheet.tiles.firstOrNull()?.hashCode()

        for (i in 1 until sheet.tiles.size) {
            val hash = sheet.tiles[i].hashCode()
            if (hash == lastHash) {
                currentRun++
                longestRun = maxOf(longestRun, currentRun)
            } else {
                currentRun = 1
                lastHash = hash
            }
        }

        val rleOpportunity =
            RLEOpportunity(
                hasConsecutiveDuplicates = longestRun > 1,
                longestRun = longestRun,
                estimatedRatio = if (longestRun > 2) 0.8f else 1.0f
            )

        // Find similar tiles (for potential delta compression)
        val similarTiles =
            if (config.analyzeCompression && sheet.tiles.size <= config.maxTilesForSimilarity) {
                findSimilarTiles(sheet)
            } else {
                emptyList()
            }

        return CompressionAnalysis(rleOpportunity, similarTiles)
    }

    /** Find pairs of tiles that are similar but not identical. */
    private fun findSimilarTiles(sheet: SpriteSheet): List<SimilarTilePair> {
        val pairs = mutableListOf<SimilarTilePair>()

        for (i in sheet.tiles.indices) {
            for (j in i + 1 until sheet.tiles.size) {
                val similarity = computeSimilarity(sheet.tiles[i], sheet.tiles[j])
                if (similarity >= config.similarityThreshold && similarity < 1.0f) {
                    val loc1 = TileLocation("", i, i % sheet.widthInTiles, i / sheet.widthInTiles)
                    val loc2 = TileLocation("", j, j % sheet.widthInTiles, j / sheet.widthInTiles)
                    pairs.add(SimilarTilePair(loc1, loc2, similarity))
                }
            }
        }

        return pairs.sortedByDescending { it.similarity }.take(10)
    }

    /** Compute similarity between two tiles (0.0 = completely different, 1.0 = identical). */
    private fun computeSimilarity(tile1: Tile, tile2: Tile): Float {
        var matching = 0
        for (i in tile1.data.indices) {
            if (tile1.data[i] == tile2.data[i]) matching++
        }
        return matching / 16f
    }

    /** Collect tiles from an asset for cross-asset duplicate detection. */
    private fun collectTilesForGlobalAnalysis(
        assetName: String,
        file: File,
        globalMap: MutableMap<Int, MutableList<TileLocation>>
    ) {
        try {
            val sheet = AssetPipeline.loadSprite(file)
            for ((index, tile) in sheet.tiles.withIndex()) {
                val gridX = index % sheet.widthInTiles
                val gridY = index / sheet.widthInTiles
                val location = TileLocation(assetName, index, gridX, gridY)
                globalMap.getOrPut(tile.hashCode()) { mutableListOf() }.add(location)
            }
        } catch (e: Exception) {
            // Skip if loading fails
        }
    }

    /** Find tiles that are duplicated across multiple assets. */
    private fun findCrossAssetDuplicates(
        globalMap: Map<Int, List<TileLocation>>
    ): List<CrossAssetDuplicate> {
        return globalMap.entries
            .filter { (_, locations) ->
                // Only count if tiles appear in multiple different assets
                locations.map { it.assetName }.toSet().size > 1
            }
            .map { (hash, locations) ->
                CrossAssetDuplicate(
                    hash = hash,
                    assets = locations.map { it.assetName }.distinct(),
                    totalCount = locations.size
                )
            }
            .sortedByDescending { it.totalCount }
    }

    /** Compute summary statistics from analyzed assets. */
    private fun computeSummary(
        assets: List<AnalyzedAsset>,
        crossAssetDuplicates: List<CrossAssetDuplicate>
    ): AssetSummary {
        var totalTiles = 0
        var uniqueTiles = 0
        var duplicateTiles = 0
        var emptyTiles = 0
        var lowEntropyTiles = 0
        val allColorsUsed = mutableSetOf<Int>()

        for (asset in assets) {
            totalTiles += asset.tiles.total
            uniqueTiles += asset.tiles.unique
            duplicateTiles += asset.tiles.duplicateCount
            emptyTiles += asset.tiles.emptyCount
            lowEntropyTiles += asset.tiles.lowEntropyCount
            allColorsUsed.addAll(asset.palette.colorsUsed)
        }

        // Add cross-asset duplicate savings
        val crossAssetSavings = crossAssetDuplicates.sumOf { it.totalCount - 1 }

        val potentialSavings =
            ByteSavings(
                bytes = (duplicateTiles + emptyTiles + crossAssetSavings) * 16,
                tiles = duplicateTiles + emptyTiles + crossAssetSavings
            )

        return AssetSummary(
            totalAssets = assets.size,
            totalTiles = totalTiles,
            uniqueTiles = uniqueTiles,
            duplicateTiles = duplicateTiles,
            emptyTiles = emptyTiles,
            lowEntropyTiles = lowEntropyTiles,
            usedPaletteColors = allColorsUsed.size,
            potentialSavings = potentialSavings
        )
    }

    /** Generate actionable suggestions from analysis results. */
    private fun generateSuggestions(
        assets: List<AnalyzedAsset>,
        summary: AssetSummary,
        crossAssetDuplicates: List<CrossAssetDuplicate>
    ): List<Suggestion> {
        val suggestions = mutableListOf<Suggestion>()

        // Cross-asset duplicate suggestion
        if (crossAssetDuplicates.isNotEmpty()) {
            val savings =
                ByteSavings.fromDuplicates(crossAssetDuplicates.sumOf { it.totalCount - 1 })
            suggestions.add(Suggestion.ShareTilesBetweenAssets(crossAssetDuplicates, savings))
        }

        // Within-asset duplicate suggestion
        if (summary.duplicateTiles > 0) {
            val allDuplicates = assets.flatMap { it.tiles.duplicates }
            val savings = ByteSavings.fromDuplicates(summary.duplicateTiles)
            suggestions.add(Suggestion.DeduplicateTiles(allDuplicates, savings))
        }

        // Empty tile suggestion
        if (summary.emptyTiles > 0) {
            val allEmpty = assets.flatMap { it.tiles.empty }
            val savings = ByteSavings(summary.emptyTiles * 16, summary.emptyTiles)
            suggestions.add(Suggestion.RemoveEmptyTiles(allEmpty, savings))
        }

        // Low entropy suggestion
        val allLowEntropy = assets.flatMap { it.tiles.lowEntropy }
        if (allLowEntropy.isNotEmpty()) {
            suggestions.add(Suggestion.ConsolidateLowEntropy(allLowEntropy))
        }

        // Per-asset palette suggestions
        for (asset in assets) {
            if (asset.palette.wastedSlots > 0 && config.analyzePalette) {
                suggestions.add(Suggestion.OptimizePalette(asset.name, asset.palette.unusedSlots))
            }
        }

        // Similar tiles suggestion
        val allSimilar = assets.flatMap { it.compression.similarTiles }
        if (allSimilar.isNotEmpty()) {
            val estimatedSavings = ByteSavings(allSimilar.size * 8, allSimilar.size / 2)
            suggestions.add(Suggestion.MergeSimilarTiles(allSimilar, estimatedSavings))
        }

        // RLE compression suggestion
        val maxRun = assets.maxOfOrNull { it.compression.rleOpportunity.longestRun } ?: 0
        if (maxRun >= 3) {
            suggestions.add(Suggestion.EnableCompression(maxRun, 0.7f))
        }

        return suggestions.sortedBy { it.severity.ordinal }
    }
}

/** Configuration for the asset analyzer. */
data class AnalyzerConfig(
    /** Threshold for low-entropy detection (0.0 - 2.0). Lower = more tiles flagged. */
    val lowEntropyThreshold: Float = 0.5f,
    /** Threshold for similar tile detection (0.0 - 1.0). Higher = only very similar. */
    val similarityThreshold: Float = 0.8f,
    /** Maximum tiles to analyze for similarity (O(n^2) performance). */
    val maxTilesForSimilarity: Int = 256,
    /** Enable duplicate tile detection. */
    val detectDuplicates: Boolean = true,
    /** Enable empty tile detection. */
    val detectEmpty: Boolean = true,
    /** Enable low-entropy tile detection. */
    val detectLowEntropy: Boolean = true,
    /** Enable palette usage analysis. */
    val analyzePalette: Boolean = true,
    /** Enable compression opportunity analysis. */
    val analyzeCompression: Boolean = true
)
