/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.optimization

import io.github.gbkt.core.Game
import java.io.File

/**
 * Standalone API for asset optimization analysis.
 *
 * This is the primary entry point for programmatic asset analysis. It provides a fluent API for
 * analyzing Game Boy assets and generating optimization reports.
 *
 * ## Basic Usage
 *
 * ```kotlin
 * val report = AssetOptimizer.analyze(myGame)
 *
 * if (report.hasIssues) {
 *     println("Found ${report.suggestions.size} suggestions")
 *     println("Potential savings: ${report.potentialSavings.formatted}")
 * }
 * ```
 *
 * ## Custom Configuration
 *
 * ```kotlin
 * val report = AssetOptimizer.analyze(myGame) {
 *     detectDuplicates = true
 *     detectEmpty = true
 *     detectLowEntropy = true
 *     lowEntropyThreshold = 0.3f
 * }
 * ```
 *
 * ## Generate Console Report
 *
 * ```kotlin
 * AssetOptimizer.report(myGame) {
 *     showPerAsset = true
 *     useColor = true
 * }
 * ```
 *
 * ## Analyze Individual File
 *
 * ```kotlin
 * val asset = AssetOptimizer.analyzeFile(File("player.png"))
 * println("Score: ${asset.score.value}/100 (${asset.score.grade.label})")
 * ```
 */
object AssetOptimizer {

    /**
     * Analyze all assets in a game.
     *
     * @param game The Game object containing asset definitions
     * @param assetDir Optional asset directory (uses game.assetDir if not specified)
     * @param config Configuration lambda for analyzer settings
     * @return Complete analysis report
     */
    fun analyze(
        game: Game,
        assetDir: File? = null,
        config: AnalyzerConfigBuilder.() -> Unit = {},
    ): AssetReport {
        val analyzerConfig = AnalyzerConfigBuilder().apply(config).build()
        val analyzer = AssetAnalyzer(analyzerConfig)
        val dir = assetDir ?: game.assetDir?.let { File(it) }
        return analyzer.analyze(game, dir)
    }

    /**
     * Analyze a single sprite file.
     *
     * @param file Path to PNG sprite file
     * @return Analysis result for the single asset
     */
    fun analyzeFile(file: File): AnalyzedAsset {
        require(file.exists()) { "File not found: ${file.path}" }
        return AssetAnalyzer().analyzeSprite(file)
    }

    /**
     * Analyze a single sprite file by path.
     *
     * @param path Path to PNG sprite file
     * @return Analysis result for the single asset
     */
    fun analyzeFile(path: String): AnalyzedAsset = analyzeFile(File(path))

    /**
     * Quick check: does this game have optimization opportunities?
     *
     * Use this for fast pass/fail checks before running full analysis.
     *
     * @param game The Game object
     * @param assetDir Optional asset directory
     * @return true if any optimization opportunities exist
     */
    fun hasOptimizations(game: Game, assetDir: File? = null): Boolean {
        return analyze(game, assetDir).hasIssues
    }

    /**
     * Analyze and print a report to console.
     *
     * @param game The Game object
     * @param assetDir Optional asset directory
     * @param config Configuration lambda for reporter settings
     */
    fun report(game: Game, assetDir: File? = null, config: ReporterConfigBuilder.() -> Unit = {}) {
        val report = analyze(game, assetDir)
        val reporterConfig = ReporterConfigBuilder().apply(config).build()
        ConsoleReporter(reporterConfig).report(report)
    }

    /**
     * Analyze and return report as a string (no console output).
     *
     * @param game The Game object
     * @param assetDir Optional asset directory
     * @return Formatted report string
     */
    fun reportToString(game: Game, assetDir: File? = null): String {
        val report = analyze(game, assetDir)
        return ConsoleReporter(ReporterConfig(useColor = false)).reportToString(report)
    }

    /**
     * Get a quick summary of asset optimization status.
     *
     * @param game The Game object
     * @param assetDir Optional asset directory
     * @return Summary containing key metrics
     */
    fun summary(game: Game, assetDir: File? = null): AssetSummary {
        return analyze(game, assetDir).summary
    }
}

/** Builder for AnalyzerConfig with fluent DSL. */
class AnalyzerConfigBuilder {
    var detectDuplicates: Boolean = true
    var detectEmpty: Boolean = true
    var detectLowEntropy: Boolean = true
    var analyzePalette: Boolean = true
    var analyzeCompression: Boolean = true
    var lowEntropyThreshold: Float = 0.5f
    var similarityThreshold: Float = 0.8f
    var maxTilesForSimilarity: Int = 256

    fun build() =
        AnalyzerConfig(
            lowEntropyThreshold = lowEntropyThreshold,
            similarityThreshold = similarityThreshold,
            maxTilesForSimilarity = maxTilesForSimilarity,
            detectDuplicates = detectDuplicates,
            detectEmpty = detectEmpty,
            detectLowEntropy = detectLowEntropy,
            analyzePalette = analyzePalette,
            analyzeCompression = analyzeCompression,
        )
}

/** Builder for ReporterConfig with fluent DSL. */
class ReporterConfigBuilder {
    var useColor: Boolean = ReporterConfig.detectColorSupport()
    var useUnicode: Boolean = ReporterConfig.detectUnicodeSupport()
    var showPerAsset: Boolean = false
    var showSuggestions: Boolean = true
    var quietWhenOptimal: Boolean = false

    fun build() =
        ReporterConfig(
            useColor = useColor,
            useUnicode = useUnicode,
            showPerAsset = showPerAsset,
            showSuggestions = showSuggestions,
            quietWhenOptimal = quietWhenOptimal,
        )
}
