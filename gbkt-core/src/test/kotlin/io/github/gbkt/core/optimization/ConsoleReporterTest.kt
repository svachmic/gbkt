/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.optimization

import io.github.gbkt.core.AssetPipeline
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConsoleReporterTest {

    // -------------------------------------------------------------------------
    // Fixtures — counts use exact binary fractions so derived values are provable:
    // 12 unique of 16 total tiles -> deduplicationRatio 0.75 -> efficiency 75%.
    // -------------------------------------------------------------------------

    private val plainConfig =
        ReporterConfig(
            useColor = false,
            useUnicode = false,
            showPerAsset = false,
            showSuggestions = true,
            quietWhenOptimal = false,
        )

    private val summary =
        AssetSummary(
            totalAssets = 1,
            totalTiles = 16,
            uniqueTiles = 12,
            duplicateTiles = 2,
            emptyTiles = 1,
            lowEntropyTiles = 1,
            usedPaletteColors = 2,
            potentialSavings = ByteSavings(bytes = 32, tiles = 2),
        )

    private val cleanSummary =
        AssetSummary(
            totalAssets = 1,
            totalTiles = 16,
            uniqueTiles = 16,
            duplicateTiles = 0,
            emptyTiles = 0,
            lowEntropyTiles = 0,
            usedPaletteColors = 4,
            potentialSavings = ByteSavings.ZERO,
        )

    private val dupInfo =
        DuplicateTileInfo(
            tile = AssetPipeline.Tile(ByteArray(16)),
            locations = listOf(TileLocation("hero", 0, 0, 0), TileLocation("hero", 1, 1, 0)),
            hash = 42,
        )

    // Score: 100 - (1/4 * 30 = 7 dupes) - (1/4 * 20 = 5 empty) - 0 - (2 * 5 = 10 palette)
    // = 78 -> Grade.GOOD, symbol "B"
    private val asset =
        AnalyzedAsset(
            name = "hero",
            path = "sprites/hero.png",
            type = AssetType.SPRITE,
            dimensions = Dimensions(16, 16, 2, 2),
            tiles =
                TileAnalysis(
                    total = 4,
                    unique = 3,
                    duplicates = listOf(dupInfo),
                    empty = listOf(TileLocation("hero", 2, 0, 1)),
                    lowEntropy = emptyList(),
                ),
            palette =
                PaletteAnalysis(
                    colorsUsed = setOf(0, 1),
                    unusedSlots = listOf(2, 3),
                    colorFrequencies = mapOf(0 to 40, 1 to 20),
                ),
            compression = CompressionAnalysis(RLEOpportunity(false, 1, 1.0f), emptyList()),
        )

    private val suggestions =
        listOf(
            Suggestion.DeduplicateTiles(listOf(dupInfo), ByteSavings(16, 1)),
            Suggestion.RemoveEmptyTiles(listOf(TileLocation("hero", 2, 0, 1)), ByteSavings(16, 1)),
            Suggestion.ConsolidateLowEntropy(
                listOf(LowEntropyTile(TileLocation("hero", 3, 1, 1), 0.2f, 0, 0.95f))
            ),
        )

    private val reportWithIssues =
        AssetReport(listOf(asset), summary, suggestions, analysisTimeMs = 7)
    private val cleanReport =
        AssetReport(emptyList(), cleanSummary, emptyList(), analysisTimeMs = 3)

    private fun captureStdout(block: () -> Unit): String {
        val buffer = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(buffer, true, "UTF-8"))
        try {
            block()
        } finally {
            System.setOut(original)
        }
        return String(buffer.toByteArray(), Charsets.UTF_8)
    }

    // -------------------------------------------------------------------------
    // reportToString — plain ASCII
    // -------------------------------------------------------------------------

    @Test
    fun `plain config emits ascii box and summary`() {
        val out = ConsoleReporter(plainConfig).reportToString(reportWithIssues)

        // Box width = title length (25) + 4
        assertTrue(out.contains("=".repeat(29)))
        assertTrue(out.contains("| Asset Optimization Report |"))
        assertTrue(out.contains("--- Summary ---"))
        assertTrue(out.contains("  Assets:     1"))
        assertTrue(out.contains("  Tiles:      16 total, 12 unique"))
        assertTrue(out.contains("  Efficiency: 75%"))
        assertFalse(out.contains("\u001B["), "plain output must not contain ANSI codes")
    }

    @Test
    fun `issues section lists ascii markers and savings`() {
        val out = ConsoleReporter(plainConfig).reportToString(reportWithIssues)

        assertTrue(out.contains("  [D] Duplicates: 2 tiles"))
        assertTrue(out.contains("  [E] Empty: 1 tiles"))
        assertTrue(out.contains("  [L] Low-entropy: 1 tiles"))
        assertTrue(out.contains("  [S] Potential savings: 32 bytes (2 tiles)"))
    }

    @Test
    fun `suggestions are numbered with severity chars and actions`() {
        val out = ConsoleReporter(plainConfig).reportToString(reportWithIssues)

        assertTrue(out.contains("  1. [i] Duplicate tiles detected"))
        assertTrue(out.contains("1 tiles are duplicates across 1 unique patterns"))
        assertTrue(out.contains("      -> Consider using a shared tileset"))
        assertTrue(out.contains("      Saves: 16 bytes (1 tiles)"))
        assertTrue(out.contains("  2. [!] Empty tiles found"))
        assertTrue(out.contains("  3. [i] Low-entropy tiles"))
        assertTrue(out.contains("1 tiles are mostly single-color (1 are 90%+ solid)"))
        // ConsolidateLowEntropy has null savings: no Saves line after suggestion 3
        assertFalse(out.substringAfter("  3. [i]").contains("Saves:"))
    }

    @Test
    fun `showSuggestions false omits the suggestions section`() {
        val out =
            ConsoleReporter(plainConfig.copy(showSuggestions = false))
                .reportToString(reportWithIssues)

        assertFalse(out.contains("Suggestions"))
        assertTrue(out.contains("Issues Found"))
    }

    @Test
    fun `clean report omits issues and suggestions sections`() {
        val out = ConsoleReporter(plainConfig).reportToString(cleanReport)

        assertTrue(out.contains("--- Summary ---"))
        assertFalse(out.contains("Issues Found"))
        assertFalse(out.contains("Suggestions"))
        assertTrue(out.contains("Analysis completed in 3ms"))
    }

    // -------------------------------------------------------------------------
    // reportToString — unicode and color variants
    // -------------------------------------------------------------------------

    @Test
    fun `unicode config uses box drawing characters`() {
        val out =
            ConsoleReporter(plainConfig.copy(useUnicode = true)).reportToString(reportWithIssues)

        assertTrue(out.contains("═".repeat(29)))
        assertTrue(out.contains("║ Asset Optimization Report ║"))
        assertTrue(out.contains("─── Summary ───"))
    }

    @Test
    fun `color config wraps header and section dashes in ansi codes`() {
        val out =
            ConsoleReporter(plainConfig.copy(useColor = true)).reportToString(reportWithIssues)

        assertTrue(out.contains("\u001B[37mAsset Optimization Report\u001B[0m"))
        assertTrue(out.contains("\u001B[2m---\u001B[0m"), "section dashes should be dimmed")
        // Issue body lines stay plain in string output
        assertTrue(out.contains("  [D] Duplicates: 2 tiles"))
    }

    // -------------------------------------------------------------------------
    // report() — stdout paths (quiet mode, per-asset details)
    // -------------------------------------------------------------------------

    @Test
    fun `quietWhenOptimal suppresses console output for a clean report`() {
        val out = captureStdout { ConsoleReporter(ReporterConfig.MINIMAL).report(cleanReport) }
        assertEquals("", out)
    }

    @Test
    fun `quietWhenOptimal still prints when there are issues`() {
        val out = captureStdout { ConsoleReporter(ReporterConfig.MINIMAL).report(reportWithIssues) }
        assertTrue(out.contains("Asset Optimization Report"))
        assertTrue(out.contains("[D] Duplicates: 2 tiles"))
    }

    @Test
    fun `verbose config prints per-asset details with icons and colors`() {
        val out = captureStdout { ConsoleReporter(ReporterConfig.VERBOSE).report(reportWithIssues) }

        assertTrue(out.contains("Per-Asset Details"))
        assertTrue(out.contains("\u001B[37mhero\u001B[0m"), "asset name should be white")
        assertTrue(out.contains("\u001B[32m[B]\u001B[0m"), "grade GOOD should be green")
        assertTrue(out.contains("78/100"))
        assertTrue(out.contains("4 (3 unique)"))
        assertTrue(out.contains("Duplicates:"))
        assertTrue(out.contains("Empty:"))
        assertTrue(out.contains("Unused palette slots:"))
        assertTrue(out.contains("🔄"), "duplicate icon should be unicode")
        assertTrue(out.contains("💰"), "savings icon should be unicode")
        assertTrue(out.contains("\u001B[33m75%\u001B[0m"), "75% efficiency should be yellow")
        assertTrue(out.contains("Analysis completed in 7ms"))
    }

    @Test
    fun `per-asset details are omitted without the showPerAsset flag`() {
        val out = captureStdout { ConsoleReporter(plainConfig).report(reportWithIssues) }
        assertFalse(out.contains("Per-Asset Details"))
    }

    // -------------------------------------------------------------------------
    // Value types and detection helpers
    // -------------------------------------------------------------------------

    @Test
    fun `ByteSavings formats bytes kilobytes and zero`() {
        assertEquals("0 bytes", ByteSavings(0, 0).formatted)
        assertEquals("96 bytes (6 tiles)", ByteSavings(96, 6).formatted)
        assertEquals("2 KB (128 tiles)", ByteSavings(2048, 128).formatted)
        assertEquals(ByteSavings(32, 2), ByteSavings(16, 1) + ByteSavings(16, 1))
        assertEquals(ByteSavings(16, 1), ByteSavings.fromDuplicates(1))
    }

    @Test
    fun `summary efficiency derives from the deduplication ratio`() {
        assertEquals(75, summary.efficiency)
        assertEquals(0.75f, summary.deduplicationRatio)
        assertEquals(100, cleanSummary.efficiency)
        assertTrue(reportWithIssues.hasIssues)
        assertFalse(cleanReport.hasIssues)
        assertEquals(ByteSavings(32, 2), reportWithIssues.potentialSavings)
    }

    @Test
    fun `detection helpers run without crashing`() {
        // Environment-dependent: only verify they execute and return a stable value
        val color = ReporterConfig.detectColorSupport()
        val unicode = ReporterConfig.detectUnicodeSupport()
        assertEquals(color, ReporterConfig.detectColorSupport())
        assertEquals(unicode, ReporterConfig.detectUnicodeSupport())
    }
}
