/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.optimization

import io.github.gbkt.core.AssetPipeline.Tile
import kotlin.test.*

/**
 * Tests for the asset optimization analysis system.
 *
 * Validates:
 * - Data class calculations and properties
 * - ByteSavings formatting and arithmetic
 * - Suggestion construction and severity
 * - OptimizationScore computation
 * - ConsoleReporter output generation
 */
class OptimizationTest {

    // =========================================================================
    // BYTE SAVINGS TESTS
    // =========================================================================

    @Test
    fun `ByteSavings ZERO constant`() {
        assertEquals(0, ByteSavings.ZERO.bytes)
        assertEquals(0, ByteSavings.ZERO.tiles)
    }

    @Test
    fun `ByteSavings formatted for zero bytes`() {
        assertEquals("0 bytes", ByteSavings.ZERO.formatted)
    }

    @Test
    fun `ByteSavings formatted for bytes under 1KB`() {
        val savings = ByteSavings(bytes = 96, tiles = 6)
        assertEquals("96 bytes (6 tiles)", savings.formatted)
    }

    @Test
    fun `ByteSavings formatted for KB range`() {
        val savings = ByteSavings(bytes = 2048, tiles = 128)
        assertEquals("2 KB (128 tiles)", savings.formatted)
    }

    @Test
    fun `ByteSavings formatted for exact 1KB`() {
        val savings = ByteSavings(bytes = 1024, tiles = 64)
        assertEquals("1 KB (64 tiles)", savings.formatted)
    }

    @Test
    fun `ByteSavings plus operator`() {
        val a = ByteSavings(100, 5)
        val b = ByteSavings(200, 10)
        val sum = a + b
        assertEquals(300, sum.bytes)
        assertEquals(15, sum.tiles)
    }

    @Test
    fun `ByteSavings fromDuplicates calculates correctly`() {
        val savings = ByteSavings.fromDuplicates(10)
        assertEquals(160, savings.bytes) // 10 * 16 bytes per tile
        assertEquals(10, savings.tiles)
    }

    // =========================================================================
    // DIMENSIONS TESTS
    // =========================================================================

    @Test
    fun `Dimensions totalTiles calculation`() {
        val dims = Dimensions(widthPx = 32, heightPx = 16, tilesWide = 4, tilesHigh = 2)
        assertEquals(8, dims.totalTiles)
    }

    // =========================================================================
    // TILE ANALYSIS TESTS
    // =========================================================================

    @Test
    fun `TileAnalysis duplicateCount calculation`() {
        val analysis = TileAnalysis(
            total = 10,
            unique = 7,
            duplicates = emptyList(),
            empty = emptyList(),
            lowEntropy = emptyList()
        )
        assertEquals(3, analysis.duplicateCount)
    }

    @Test
    fun `TileAnalysis emptyCount from list size`() {
        val analysis = TileAnalysis(
            total = 10,
            unique = 10,
            duplicates = emptyList(),
            empty = listOf(
                TileLocation("asset", 0, 0, 0),
                TileLocation("asset", 1, 1, 0)
            ),
            lowEntropy = emptyList()
        )
        assertEquals(2, analysis.emptyCount)
    }

    @Test
    fun `TileAnalysis lowEntropyCount from list size`() {
        val analysis = TileAnalysis(
            total = 10,
            unique = 10,
            duplicates = emptyList(),
            empty = emptyList(),
            lowEntropy = listOf(
                LowEntropyTile(TileLocation("asset", 0, 0, 0), 0.5f, 0, 0.8f),
                LowEntropyTile(TileLocation("asset", 1, 1, 0), 0.3f, 0, 0.9f),
                LowEntropyTile(TileLocation("asset", 2, 2, 0), 0.2f, 0, 0.95f)
            )
        )
        assertEquals(3, analysis.lowEntropyCount)
    }

    // =========================================================================
    // TILE LOCATION TESTS
    // =========================================================================

    @Test
    fun `TileLocation position string format`() {
        val location = TileLocation("player.png", 5, 3, 2)
        assertEquals("(3, 2)", location.position)
    }

    // =========================================================================
    // LOW ENTROPY TILE TESTS
    // =========================================================================

    @Test
    fun `LowEntropyTile isNearlySolid when coverage at threshold`() {
        val tile = LowEntropyTile(
            location = TileLocation("asset", 0, 0, 0),
            entropy = 0.1f,
            dominantColor = 0,
            colorCoverage = 0.9f
        )
        assertTrue(tile.isNearlySolid)
    }

    @Test
    fun `LowEntropyTile isNearlySolid when coverage above threshold`() {
        val tile = LowEntropyTile(
            location = TileLocation("asset", 0, 0, 0),
            entropy = 0.05f,
            dominantColor = 0,
            colorCoverage = 0.95f
        )
        assertTrue(tile.isNearlySolid)
    }

    @Test
    fun `LowEntropyTile not nearly solid when below threshold`() {
        val tile = LowEntropyTile(
            location = TileLocation("asset", 0, 0, 0),
            entropy = 0.5f,
            dominantColor = 0,
            colorCoverage = 0.85f
        )
        assertFalse(tile.isNearlySolid)
    }

    // =========================================================================
    // PALETTE ANALYSIS TESTS
    // =========================================================================

    @Test
    fun `PaletteAnalysis wastedSlots count`() {
        val analysis = PaletteAnalysis(
            colorsUsed = setOf(0, 1),
            unusedSlots = listOf(2, 3),
            colorFrequencies = mapOf(0 to 100, 1 to 50)
        )
        assertEquals(2, analysis.wastedSlots)
    }

    @Test
    fun `PaletteAnalysis isOptimal when all slots used`() {
        val analysis = PaletteAnalysis(
            colorsUsed = setOf(0, 1, 2, 3),
            unusedSlots = emptyList(),
            colorFrequencies = mapOf(0 to 100, 1 to 50, 2 to 30, 3 to 20)
        )
        assertTrue(analysis.isOptimal)
    }

    @Test
    fun `PaletteAnalysis not optimal when slots wasted`() {
        val analysis = PaletteAnalysis(
            colorsUsed = setOf(0, 1),
            unusedSlots = listOf(2, 3),
            colorFrequencies = mapOf(0 to 100, 1 to 50)
        )
        assertFalse(analysis.isOptimal)
    }

    @Test
    fun `PaletteAnalysis dominantColor is most frequent`() {
        val analysis = PaletteAnalysis(
            colorsUsed = setOf(0, 1, 2),
            unusedSlots = listOf(3),
            colorFrequencies = mapOf(0 to 50, 1 to 200, 2 to 100)
        )
        assertEquals(1, analysis.dominantColor)
    }

    @Test
    fun `PaletteAnalysis dominantColor null when empty`() {
        val analysis = PaletteAnalysis(
            colorsUsed = emptySet(),
            unusedSlots = listOf(0, 1, 2, 3),
            colorFrequencies = emptyMap()
        )
        assertNull(analysis.dominantColor)
    }

    // =========================================================================
    // COMPRESSION ANALYSIS TESTS
    // =========================================================================

    @Test
    fun `CompressionAnalysis hasOpportunities when RLE available`() {
        val analysis = CompressionAnalysis(
            rleOpportunity = RLEOpportunity(hasConsecutiveDuplicates = true, longestRun = 5, estimatedRatio = 0.7f),
            similarTiles = emptyList()
        )
        assertTrue(analysis.hasOpportunities)
    }

    @Test
    fun `CompressionAnalysis hasOpportunities when similar tiles exist`() {
        val analysis = CompressionAnalysis(
            rleOpportunity = RLEOpportunity(hasConsecutiveDuplicates = false, longestRun = 1, estimatedRatio = 1.0f),
            similarTiles = listOf(
                SimilarTilePair(
                    TileLocation("a", 0, 0, 0),
                    TileLocation("a", 1, 1, 0),
                    0.95f
                )
            )
        )
        assertTrue(analysis.hasOpportunities)
    }

    @Test
    fun `CompressionAnalysis no opportunities when none available`() {
        val analysis = CompressionAnalysis(
            rleOpportunity = RLEOpportunity(hasConsecutiveDuplicates = false, longestRun = 1, estimatedRatio = 1.0f),
            similarTiles = emptyList()
        )
        assertFalse(analysis.hasOpportunities)
    }

    // =========================================================================
    // RLE OPPORTUNITY TESTS
    // =========================================================================

    @Test
    fun `RLEOpportunity worthwhile when run length 3 or more`() {
        val opportunity = RLEOpportunity(hasConsecutiveDuplicates = true, longestRun = 3, estimatedRatio = 0.8f)
        assertTrue(opportunity.worthwhile)
    }

    @Test
    fun `RLEOpportunity not worthwhile when run length under 3`() {
        val opportunity = RLEOpportunity(hasConsecutiveDuplicates = true, longestRun = 2, estimatedRatio = 0.9f)
        assertFalse(opportunity.worthwhile)
    }

    // =========================================================================
    // SIMILAR TILE PAIR TESTS
    // =========================================================================

    @Test
    fun `SimilarTilePair isNearlyIdentical at threshold`() {
        val pair = SimilarTilePair(
            TileLocation("a", 0, 0, 0),
            TileLocation("a", 1, 1, 0),
            0.9f
        )
        assertTrue(pair.isNearlyIdentical)
    }

    @Test
    fun `SimilarTilePair not nearly identical below threshold`() {
        val pair = SimilarTilePair(
            TileLocation("a", 0, 0, 0),
            TileLocation("a", 1, 1, 0),
            0.85f
        )
        assertFalse(pair.isNearlyIdentical)
    }

    // =========================================================================
    // DUPLICATE TILE INFO TESTS
    // =========================================================================

    @Test
    fun `DuplicateTileInfo count from locations`() {
        val tile = Tile(ByteArray(16) { 0 })
        val info = DuplicateTileInfo(
            tile = tile,
            locations = listOf(
                TileLocation("asset", 0, 0, 0),
                TileLocation("asset", 1, 1, 0),
                TileLocation("asset", 2, 2, 0)
            ),
            hash = 12345
        )
        assertEquals(3, info.count)
    }

    @Test
    fun `DuplicateTileInfo savings calculation`() {
        val tile = Tile(ByteArray(16) { 0 })
        val info = DuplicateTileInfo(
            tile = tile,
            locations = listOf(
                TileLocation("asset", 0, 0, 0),
                TileLocation("asset", 1, 1, 0),
                TileLocation("asset", 2, 2, 0)
            ),
            hash = 12345
        )
        // 3 copies - 1 = 2 duplicates, 2 * 16 = 32 bytes
        assertEquals(32, info.savings.bytes)
        assertEquals(2, info.savings.tiles)
    }

    // =========================================================================
    // CROSS ASSET DUPLICATE TESTS
    // =========================================================================

    @Test
    fun `CrossAssetDuplicate savings calculation`() {
        val duplicate = CrossAssetDuplicate(
            hash = 12345,
            assets = listOf("player.png", "enemy.png", "npc.png"),
            totalCount = 5
        )
        // 5 copies - 1 = 4 duplicates, 4 * 16 = 64 bytes
        assertEquals(64, duplicate.savings.bytes)
        assertEquals(4, duplicate.savings.tiles)
    }

    // =========================================================================
    // OPTIMIZATION SCORE TESTS
    // =========================================================================

    @Test
    fun `OptimizationScore EXCELLENT grade for perfect asset`() {
        val asset = createTestAsset(
            totalTiles = 10,
            uniqueTiles = 10,
            emptyTiles = 0,
            lowEntropyTiles = 0,
            wastedSlots = 0
        )
        val score = OptimizationScore.compute(asset)
        assertEquals(OptimizationScore.Grade.EXCELLENT, score.grade)
        assertEquals(100, score.value)
    }

    @Test
    fun `OptimizationScore penalizes duplicates`() {
        val asset = createTestAsset(
            totalTiles = 10,
            uniqueTiles = 5, // 50% duplicates
            emptyTiles = 0,
            lowEntropyTiles = 0,
            wastedSlots = 0
        )
        val score = OptimizationScore.compute(asset)
        assertTrue(score.value < 100)
    }

    @Test
    fun `OptimizationScore penalizes empty tiles`() {
        val asset = createTestAsset(
            totalTiles = 10,
            uniqueTiles = 10,
            emptyTiles = 5, // 50% empty
            lowEntropyTiles = 0,
            wastedSlots = 0
        )
        val score = OptimizationScore.compute(asset)
        assertTrue(score.value < 100)
    }

    @Test
    fun `OptimizationScore penalizes low entropy tiles`() {
        val asset = createTestAsset(
            totalTiles = 10,
            uniqueTiles = 10,
            emptyTiles = 0,
            lowEntropyTiles = 5,
            wastedSlots = 0
        )
        val score = OptimizationScore.compute(asset)
        assertTrue(score.value < 100)
    }

    @Test
    fun `OptimizationScore penalizes wasted palette slots`() {
        val asset = createTestAsset(
            totalTiles = 10,
            uniqueTiles = 10,
            emptyTiles = 0,
            lowEntropyTiles = 0,
            wastedSlots = 2 // -10 points
        )
        val score = OptimizationScore.compute(asset)
        assertEquals(90, score.value)
    }

    @Test
    fun `OptimizationScore CRITICAL grade for poor asset`() {
        // Score calculation: 100 - (0.8*30) - (0.8*20) - (0.5*10) - (4*5) = 100 - 24 - 16 - 5 - 20 = 35
        val asset = createTestAsset(
            totalTiles = 10,
            uniqueTiles = 2, // 80% duplicates → -24 points
            emptyTiles = 8, // 80% empty → -16 points
            lowEntropyTiles = 5, // 50% low entropy → -5 points
            wastedSlots = 4 // -20 points
        )
        val score = OptimizationScore.compute(asset)
        assertEquals(OptimizationScore.Grade.CRITICAL, score.grade)
        assertTrue(score.value < 40, "Score should be under 40 for CRITICAL grade, was ${score.value}")
    }

    @Test
    fun `OptimizationScore clamped to 0-100 range`() {
        val asset = createTestAsset(
            totalTiles = 10,
            uniqueTiles = 0, // 100% duplicates
            emptyTiles = 10, // 100% empty
            lowEntropyTiles = 10, // 100% low entropy
            wastedSlots = 4 // -20 points
        )
        val score = OptimizationScore.compute(asset)
        assertTrue(score.value in 0..100)
    }

    @Test
    fun `OptimizationScore handles zero total tiles`() {
        val asset = createTestAsset(
            totalTiles = 0,
            uniqueTiles = 0,
            emptyTiles = 0,
            lowEntropyTiles = 0,
            wastedSlots = 0
        )
        val score = OptimizationScore.compute(asset)
        assertEquals(100, score.value) // No tiles = perfect score
    }

    @Test
    fun `OptimizationScore Grade labels`() {
        assertEquals("A", OptimizationScore.Grade.EXCELLENT.symbol)
        assertEquals("B", OptimizationScore.Grade.GOOD.symbol)
        assertEquals("C", OptimizationScore.Grade.FAIR.symbol)
        assertEquals("D", OptimizationScore.Grade.POOR.symbol)
        assertEquals("F", OptimizationScore.Grade.CRITICAL.symbol)
    }

    // =========================================================================
    // SUGGESTION TESTS
    // =========================================================================

    @Test
    fun `DeduplicateTiles suggestion properties`() {
        val tile = Tile(ByteArray(16) { 0 })
        val duplicates = listOf(
            DuplicateTileInfo(tile, listOf(TileLocation("a", 0, 0, 0), TileLocation("a", 1, 1, 0)), 123),
            DuplicateTileInfo(tile, listOf(TileLocation("a", 2, 2, 0), TileLocation("a", 3, 3, 0)), 456)
        )
        val suggestion = Suggestion.DeduplicateTiles(duplicates, ByteSavings(64, 4))

        assertEquals(Severity.INFO, suggestion.severity)
        assertEquals("Duplicate tiles detected", suggestion.title)
        assertNotNull(suggestion.savings)
    }

    @Test
    fun `RemoveEmptyTiles suggestion properties`() {
        val emptyTiles = listOf(
            TileLocation("asset", 0, 0, 0),
            TileLocation("asset", 1, 1, 0)
        )
        val suggestion = Suggestion.RemoveEmptyTiles(emptyTiles, ByteSavings(32, 2))

        assertEquals(Severity.WARNING, suggestion.severity)
        assertEquals("Empty tiles found", suggestion.title)
        assertTrue(suggestion.description.contains("2"))
    }

    @Test
    fun `ConsolidateLowEntropy suggestion with nearly solid tiles`() {
        val tiles = listOf(
            LowEntropyTile(TileLocation("a", 0, 0, 0), 0.1f, 0, 0.95f),
            LowEntropyTile(TileLocation("a", 1, 1, 0), 0.2f, 0, 0.85f)
        )
        val suggestion = Suggestion.ConsolidateLowEntropy(tiles)

        assertEquals(Severity.INFO, suggestion.severity)
        assertTrue(suggestion.description.contains("1 are 90%+ solid"))
        assertNull(suggestion.savings)
    }

    @Test
    fun `OptimizePalette suggestion properties`() {
        val suggestion = Suggestion.OptimizePalette("player.png", listOf(2, 3))

        assertEquals(Severity.INFO, suggestion.severity)
        assertTrue(suggestion.title.contains("player.png"))
        assertTrue(suggestion.description.contains("2 of 4"))
        assertNull(suggestion.savings)
    }

    @Test
    fun `MergeSimilarTiles suggestion properties`() {
        val pairs = listOf(
            SimilarTilePair(TileLocation("a", 0, 0, 0), TileLocation("a", 1, 1, 0), 0.95f)
        )
        val suggestion = Suggestion.MergeSimilarTiles(pairs, ByteSavings(16, 1))

        assertEquals(Severity.INFO, suggestion.severity)
        assertTrue(suggestion.description.contains("1 tile pairs"))
        assertNotNull(suggestion.savings)
    }

    @Test
    fun `EnableCompression suggestion properties`() {
        val suggestion = Suggestion.EnableCompression(longestRun = 8, estimatedRatio = 0.6f)

        assertEquals(Severity.INFO, suggestion.severity)
        assertTrue(suggestion.description.contains("8 consecutive"))
        assertTrue(suggestion.action.contains("60%"))
        assertNull(suggestion.savings)
    }

    @Test
    fun `ShareTilesBetweenAssets suggestion properties`() {
        val sharedTiles = listOf(
            CrossAssetDuplicate(123, listOf("a.png", "b.png"), 3)
        )
        val suggestion = Suggestion.ShareTilesBetweenAssets(sharedTiles, ByteSavings(32, 2))

        assertEquals(Severity.INFO, suggestion.severity)
        assertTrue(suggestion.description.contains("1 tiles"))
        assertNotNull(suggestion.savings)
    }

    // =========================================================================
    // SEVERITY TESTS
    // =========================================================================

    @Test
    fun `Severity enum values`() {
        assertEquals(3, Severity.entries.size)
        assertTrue(Severity.INFO in Severity.entries)
        assertTrue(Severity.WARNING in Severity.entries)
        assertTrue(Severity.ERROR in Severity.entries)
    }

    // =========================================================================
    // ASSET REPORT TESTS
    // =========================================================================

    @Test
    fun `AssetReport hasIssues when suggestions present`() {
        val report = AssetReport(
            assets = emptyList(),
            summary = createTestSummary(potentialSavings = ByteSavings.ZERO),
            suggestions = listOf(Suggestion.EnableCompression(5, 0.7f)),
            analysisTimeMs = 100
        )
        assertTrue(report.hasIssues)
    }

    @Test
    fun `AssetReport hasIssues false when no suggestions`() {
        val report = AssetReport(
            assets = emptyList(),
            summary = createTestSummary(potentialSavings = ByteSavings.ZERO),
            suggestions = emptyList(),
            analysisTimeMs = 100
        )
        assertFalse(report.hasIssues)
    }

    @Test
    fun `AssetReport potentialSavings from summary`() {
        val savings = ByteSavings(100, 6)
        val report = AssetReport(
            assets = emptyList(),
            summary = createTestSummary(potentialSavings = savings),
            suggestions = emptyList(),
            analysisTimeMs = 100
        )
        assertEquals(savings, report.potentialSavings)
    }

    // =========================================================================
    // ASSET SUMMARY TESTS
    // =========================================================================

    @Test
    fun `AssetSummary deduplicationRatio calculation`() {
        val summary = AssetSummary(
            totalAssets = 1,
            totalTiles = 10,
            uniqueTiles = 8,
            duplicateTiles = 2,
            emptyTiles = 0,
            lowEntropyTiles = 0,
            usedPaletteColors = 4,
            potentialSavings = ByteSavings.ZERO
        )
        assertEquals(0.8f, summary.deduplicationRatio)
    }

    @Test
    fun `AssetSummary deduplicationRatio handles zero tiles`() {
        val summary = AssetSummary(
            totalAssets = 1,
            totalTiles = 0,
            uniqueTiles = 0,
            duplicateTiles = 0,
            emptyTiles = 0,
            lowEntropyTiles = 0,
            usedPaletteColors = 0,
            potentialSavings = ByteSavings.ZERO
        )
        assertEquals(1f, summary.deduplicationRatio)
    }

    @Test
    fun `AssetSummary efficiency percentage`() {
        val summary = AssetSummary(
            totalAssets = 1,
            totalTiles = 100,
            uniqueTiles = 85,
            duplicateTiles = 15,
            emptyTiles = 0,
            lowEntropyTiles = 0,
            usedPaletteColors = 4,
            potentialSavings = ByteSavings.ZERO
        )
        assertEquals(85, summary.efficiency)
    }

    // =========================================================================
    // REPORTER CONFIG TESTS
    // =========================================================================

    @Test
    fun `ReporterConfig MINIMAL preset`() {
        val config = ReporterConfig.MINIMAL
        assertFalse(config.useColor)
        assertFalse(config.useUnicode)
        assertFalse(config.showPerAsset)
        assertTrue(config.showSuggestions)
        assertTrue(config.quietWhenOptimal)
    }

    @Test
    fun `ReporterConfig VERBOSE preset`() {
        val config = ReporterConfig.VERBOSE
        assertTrue(config.useColor)
        assertTrue(config.useUnicode)
        assertTrue(config.showPerAsset)
        assertTrue(config.showSuggestions)
        assertFalse(config.quietWhenOptimal)
    }

    // =========================================================================
    // CONSOLE REPORTER TESTS
    // =========================================================================

    @Test
    fun `ConsoleReporter reportToString generates output`() {
        val reporter = ConsoleReporter(ReporterConfig.MINIMAL)
        val report = AssetReport(
            assets = emptyList(),
            summary = createTestSummary(
                totalAssets = 2,
                totalTiles = 50,
                uniqueTiles = 45,
                duplicateTiles = 5,
                potentialSavings = ByteSavings(80, 5)
            ),
            suggestions = listOf(
                Suggestion.DeduplicateTiles(
                    emptyList(),
                    ByteSavings(80, 5)
                )
            ),
            analysisTimeMs = 42
        )

        val output = reporter.reportToString(report)

        assertTrue(output.contains("Summary"))
        assertTrue(output.contains("Assets:"))
        assertTrue(output.contains("2"))
        assertTrue(output.contains("50"))
        assertTrue(output.contains("Suggestions"))
        assertTrue(output.contains("42ms"))
    }

    @Test
    fun `ConsoleReporter handles optimal report quietly`() {
        val reporter = ConsoleReporter(ReporterConfig.MINIMAL.copy(quietWhenOptimal = true))
        val report = AssetReport(
            assets = emptyList(),
            summary = createTestSummary(potentialSavings = ByteSavings.ZERO),
            suggestions = emptyList(),
            analysisTimeMs = 10
        )

        // When quietWhenOptimal is true and no issues, report() should return early
        // We can't easily test println output, but reportToString should still work
        val output = reporter.reportToString(report)
        assertTrue(output.isNotEmpty())
    }

    @Test
    fun `ConsoleReporter includes suggestions in output`() {
        val reporter = ConsoleReporter(ReporterConfig.MINIMAL.copy(showSuggestions = true))
        val report = AssetReport(
            assets = emptyList(),
            summary = createTestSummary(duplicateTiles = 5, potentialSavings = ByteSavings(80, 5)),
            suggestions = listOf(
                Suggestion.RemoveEmptyTiles(
                    listOf(TileLocation("test", 0, 0, 0)),
                    ByteSavings(16, 1)
                )
            ),
            analysisTimeMs = 10
        )

        val output = reporter.reportToString(report)
        assertTrue(output.contains("Suggestions"))
        assertTrue(output.contains("Empty tiles"))
    }

    @Test
    fun `ConsoleReporter shows issues section`() {
        val reporter = ConsoleReporter(ReporterConfig.MINIMAL)
        val report = AssetReport(
            assets = emptyList(),
            summary = createTestSummary(
                duplicateTiles = 5,
                emptyTiles = 3,
                lowEntropyTiles = 2,
                potentialSavings = ByteSavings(80, 5)
            ),
            suggestions = listOf(Suggestion.EnableCompression(5, 0.7f)),
            analysisTimeMs = 10
        )

        val output = reporter.reportToString(report)
        assertTrue(output.contains("Issues Found"))
        assertTrue(output.contains("Duplicates"))
        assertTrue(output.contains("Empty"))
        assertTrue(output.contains("Low-entropy"))
    }

    // =========================================================================
    // ASSET TYPE TESTS
    // =========================================================================

    @Test
    fun `AssetType enum values`() {
        assertEquals(3, AssetType.entries.size)
        assertTrue(AssetType.SPRITE in AssetType.entries)
        assertTrue(AssetType.TILEMAP in AssetType.entries)
        assertTrue(AssetType.TILESET in AssetType.entries)
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private fun createTestAsset(
        totalTiles: Int,
        uniqueTiles: Int,
        emptyTiles: Int,
        lowEntropyTiles: Int,
        wastedSlots: Int
    ): AnalyzedAsset {
        return AnalyzedAsset(
            name = "test.png",
            path = "/path/to/test.png",
            type = AssetType.SPRITE,
            dimensions = Dimensions(32, 32, 4, 4),
            tiles = TileAnalysis(
                total = totalTiles,
                unique = uniqueTiles,
                duplicates = emptyList(),
                empty = (0 until emptyTiles).map { TileLocation("test", it, it, 0) },
                lowEntropy = (0 until lowEntropyTiles).map {
                    LowEntropyTile(TileLocation("test", it, it, 0), 0.5f, 0, 0.8f)
                }
            ),
            palette = PaletteAnalysis(
                colorsUsed = (0 until (4 - wastedSlots)).toSet(),
                unusedSlots = (4 - wastedSlots until 4).toList(),
                colorFrequencies = (0 until (4 - wastedSlots)).associateWith { 50 }
            ),
            compression = CompressionAnalysis(
                rleOpportunity = RLEOpportunity(false, 1, 1.0f),
                similarTiles = emptyList()
            )
        )
    }

    private fun createTestSummary(
        totalAssets: Int = 1,
        totalTiles: Int = 10,
        uniqueTiles: Int = 10,
        duplicateTiles: Int = 0,
        emptyTiles: Int = 0,
        lowEntropyTiles: Int = 0,
        potentialSavings: ByteSavings = ByteSavings.ZERO
    ): AssetSummary {
        return AssetSummary(
            totalAssets = totalAssets,
            totalTiles = totalTiles,
            uniqueTiles = uniqueTiles,
            duplicateTiles = duplicateTiles,
            emptyTiles = emptyTiles,
            lowEntropyTiles = lowEntropyTiles,
            usedPaletteColors = 4,
            potentialSavings = potentialSavings
        )
    }
}
