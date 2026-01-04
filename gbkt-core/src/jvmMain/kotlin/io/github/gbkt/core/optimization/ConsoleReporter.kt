/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.optimization

/**
 * Beautiful console output for asset analysis reports.
 *
 * Features:
 * - Unicode box-drawing and icons (with ASCII fallback)
 * - ANSI color support (with detection)
 * - Progressive detail levels
 * - Scannable summary-first layout
 */
class ConsoleReporter(private val config: ReporterConfig = ReporterConfig()) {

    /** Print the analysis report to console. */
    fun report(analysis: AssetReport) {
        if (!analysis.hasIssues && config.quietWhenOptimal) {
            return
        }

        printHeader()
        printSummary(analysis.summary)

        if (analysis.hasIssues) {
            printIssues(analysis.summary)

            if (config.showSuggestions) {
                printSuggestions(analysis.suggestions)
            }
        }

        if (config.showPerAsset && analysis.assets.isNotEmpty()) {
            printAssetDetails(analysis.assets)
        }

        printFooter(analysis)
    }

    /** Generate report as a string (for logging/capture). */
    fun reportToString(analysis: AssetReport): String {
        val builder = StringBuilder()
        val originalPrint = config.useColor

        // Temporarily disable colors for string output
        appendHeader(builder)
        appendSummary(builder, analysis.summary)

        if (analysis.hasIssues) {
            appendIssues(builder, analysis.summary)
            if (config.showSuggestions) {
                appendSuggestions(builder, analysis.suggestions)
            }
        }

        appendFooter(builder, analysis)
        return builder.toString()
    }

    // ==================== Printing Methods ====================

    private fun printHeader() {
        println()
        println(boxTop("Asset Optimization Report"))
    }

    private fun appendHeader(sb: StringBuilder) {
        sb.appendLine()
        sb.appendLine(boxTop("Asset Optimization Report"))
    }

    private fun printSummary(summary: AssetSummary) {
        println()
        println(section("Summary"))
        println()

        val efficiencyColor =
            when {
                summary.efficiency >= 90 -> Color.GREEN
                summary.efficiency >= 70 -> Color.YELLOW
                else -> Color.RED
            }

        println("  ${dim("Assets:")}     ${summary.totalAssets}")
        println(
            "  ${dim("Tiles:")}      ${summary.totalTiles} total, ${summary.uniqueTiles} unique"
        )
        println("  ${dim("Efficiency:")} ${color("${summary.efficiency}%", efficiencyColor)}")
        println()
    }

    private fun appendSummary(sb: StringBuilder, summary: AssetSummary) {
        sb.appendLine()
        sb.appendLine(section("Summary"))
        sb.appendLine()
        sb.appendLine("  Assets:     ${summary.totalAssets}")
        sb.appendLine("  Tiles:      ${summary.totalTiles} total, ${summary.uniqueTiles} unique")
        sb.appendLine("  Efficiency: ${summary.efficiency}%")
        sb.appendLine()
    }

    private fun printIssues(summary: AssetSummary) {
        if (
            summary.duplicateTiles == 0 && summary.emptyTiles == 0 && summary.lowEntropyTiles == 0
        ) {
            return
        }

        println(section("Issues Found"))
        println()

        if (summary.duplicateTiles > 0) {
            println(
                "  ${icon(Icon.DUPLICATE)} ${color("Duplicates:", Color.YELLOW)} ${summary.duplicateTiles} tiles"
            )
        }
        if (summary.emptyTiles > 0) {
            println(
                "  ${icon(Icon.EMPTY)} ${color("Empty:", Color.YELLOW)} ${summary.emptyTiles} tiles"
            )
        }
        if (summary.lowEntropyTiles > 0) {
            println(
                "  ${icon(Icon.LOW_ENTROPY)} ${dim("Low-entropy:")} ${summary.lowEntropyTiles} tiles"
            )
        }
        println()

        if (summary.potentialSavings.bytes > 0) {
            println(
                "  ${icon(Icon.SAVINGS)} ${color("Potential savings:", Color.GREEN)} ${summary.potentialSavings.formatted}"
            )
            println()
        }
    }

    private fun appendIssues(sb: StringBuilder, summary: AssetSummary) {
        if (
            summary.duplicateTiles == 0 && summary.emptyTiles == 0 && summary.lowEntropyTiles == 0
        ) {
            return
        }

        sb.appendLine(section("Issues Found"))
        sb.appendLine()

        if (summary.duplicateTiles > 0) {
            sb.appendLine("  [D] Duplicates: ${summary.duplicateTiles} tiles")
        }
        if (summary.emptyTiles > 0) {
            sb.appendLine("  [E] Empty: ${summary.emptyTiles} tiles")
        }
        if (summary.lowEntropyTiles > 0) {
            sb.appendLine("  [L] Low-entropy: ${summary.lowEntropyTiles} tiles")
        }
        sb.appendLine()

        if (summary.potentialSavings.bytes > 0) {
            sb.appendLine("  [S] Potential savings: ${summary.potentialSavings.formatted}")
            sb.appendLine()
        }
    }

    private fun printSuggestions(suggestions: List<Suggestion>) {
        if (suggestions.isEmpty()) return

        println(section("Suggestions"))
        println()

        for ((index, suggestion) in suggestions.withIndex()) {
            val severityIcon =
                when (suggestion.severity) {
                    Severity.ERROR -> color("!", Color.RED)
                    Severity.WARNING -> color("!", Color.YELLOW)
                    Severity.INFO -> color("i", Color.CYAN)
                }

            val number = dim("${index + 1}.")
            println("  $number [$severityIcon] ${suggestion.title}")
            println("      ${dim(suggestion.description)}")
            println("      ${color("->", Color.CYAN)} ${suggestion.action}")
            suggestion.savings?.let {
                println("      ${color("Saves:", Color.GREEN)} ${it.formatted}")
            }
            println()
        }
    }

    private fun appendSuggestions(sb: StringBuilder, suggestions: List<Suggestion>) {
        if (suggestions.isEmpty()) return

        sb.appendLine(section("Suggestions"))
        sb.appendLine()

        for ((index, suggestion) in suggestions.withIndex()) {
            val severityChar =
                when (suggestion.severity) {
                    Severity.ERROR -> "!"
                    Severity.WARNING -> "!"
                    Severity.INFO -> "i"
                }

            sb.appendLine("  ${index + 1}. [$severityChar] ${suggestion.title}")
            sb.appendLine("      ${suggestion.description}")
            sb.appendLine("      -> ${suggestion.action}")
            suggestion.savings?.let { sb.appendLine("      Saves: ${it.formatted}") }
            sb.appendLine()
        }
    }

    private fun printAssetDetails(assets: List<AnalyzedAsset>) {
        println(section("Per-Asset Details"))
        println()

        for (asset in assets) {
            val score = asset.score
            val gradeColor =
                when (score.grade) {
                    OptimizationScore.Grade.EXCELLENT -> Color.GREEN
                    OptimizationScore.Grade.GOOD -> Color.GREEN
                    OptimizationScore.Grade.FAIR -> Color.YELLOW
                    OptimizationScore.Grade.POOR -> Color.YELLOW
                    OptimizationScore.Grade.CRITICAL -> Color.RED
                }

            println("  ${color(asset.name, Color.WHITE)}")
            println(
                "    ${dim("Score:")} ${color("[${score.grade.symbol}]", gradeColor)} ${score.value}/100"
            )
            println("    ${dim("Tiles:")} ${asset.tiles.total} (${asset.tiles.unique} unique)")

            if (asset.tiles.duplicateCount > 0) {
                println("    ${color("Duplicates:", Color.YELLOW)} ${asset.tiles.duplicateCount}")
            }
            if (asset.tiles.emptyCount > 0) {
                println("    ${color("Empty:", Color.YELLOW)} ${asset.tiles.emptyCount}")
            }
            if (asset.palette.wastedSlots > 0) {
                println("    ${dim("Unused palette slots:")} ${asset.palette.wastedSlots}")
            }
            println()
        }
    }

    private fun printFooter(analysis: AssetReport) {
        println(dim("─".repeat(40)))
        println(dim("Analysis completed in ${analysis.analysisTimeMs}ms"))
        println()
    }

    private fun appendFooter(sb: StringBuilder, analysis: AssetReport) {
        sb.appendLine("─".repeat(40))
        sb.appendLine("Analysis completed in ${analysis.analysisTimeMs}ms")
        sb.appendLine()
    }

    // ==================== Formatting Helpers ====================

    private fun boxTop(title: String): String {
        val width = title.length + 4
        val top = if (config.useUnicode) "═".repeat(width) else "=".repeat(width)
        val side = if (config.useUnicode) "║" else "|"

        return buildString {
                appendLine(top)
                appendLine("$side ${color(title, Color.WHITE)} $side")
                appendLine(top)
            }
            .trimEnd()
    }

    private fun section(title: String): String {
        val dash = if (config.useUnicode) "─" else "-"
        return "${dim("$dash$dash$dash")} $title ${dim("$dash$dash$dash")}"
    }

    private fun icon(icon: Icon): String =
        when {
            config.useUnicode -> icon.unicode
            else -> icon.ascii
        }

    private fun color(text: String, color: Color): String =
        when {
            config.useColor -> "${color.ansi}$text${Color.RESET.ansi}"
            else -> text
        }

    private fun dim(text: String): String = color(text, Color.DIM)

    private enum class Icon(val unicode: String, val ascii: String) {
        DUPLICATE("\uD83D\uDD04", "[D]"), // 🔄
        EMPTY("\u2B1C", "[E]"), // ⬜
        LOW_ENTROPY("\uD83C\uDFA8", "[L]"), // 🎨
        SAVINGS("\uD83D\uDCB0", "[S]"), // 💰
        CHECK("\u2714", "[OK]"), // ✔
        WARNING("\u26A0", "[!]"), // ⚠
        ARROW("\u2192", "->") // →
    }

    private enum class Color(val ansi: String) {
        RED("\u001B[31m"),
        GREEN("\u001B[32m"),
        YELLOW("\u001B[33m"),
        BLUE("\u001B[34m"),
        CYAN("\u001B[36m"),
        WHITE("\u001B[37m"),
        DIM("\u001B[2m"),
        RESET("\u001B[0m")
    }
}

/** Configuration for the console reporter. */
data class ReporterConfig(
    /** Use ANSI color codes in output. */
    val useColor: Boolean = detectColorSupport(),
    /** Use Unicode characters (box-drawing, icons). */
    val useUnicode: Boolean = detectUnicodeSupport(),
    /** Show detailed per-asset breakdown. */
    val showPerAsset: Boolean = false,
    /** Show actionable suggestions. */
    val showSuggestions: Boolean = true,
    /** Suppress output when no issues found. */
    val quietWhenOptimal: Boolean = false
) {
    companion object {
        /** Detect if the terminal supports ANSI colors. */
        fun detectColorSupport(): Boolean {
            // Check common environment indicators
            val term = System.getenv("TERM") ?: ""
            val colorTerm = System.getenv("COLORTERM") ?: ""

            return when {
                // Explicitly disabled
                System.getenv("NO_COLOR") != null -> false
                // CI environments often support color
                System.getenv("CI") != null -> true
                // Common color terminals
                term.contains("color") || term.contains("xterm") || term.contains("256") -> true
                colorTerm.isNotEmpty() -> true
                // Windows Terminal supports color
                System.getenv("WT_SESSION") != null -> true
                // Default to enabled on non-Windows
                else -> !System.getProperty("os.name").lowercase().contains("windows")
            }
        }

        /** Detect if the terminal supports Unicode. */
        fun detectUnicodeSupport(): Boolean {
            val lang = System.getenv("LANG") ?: ""
            val lcAll = System.getenv("LC_ALL") ?: ""

            return when {
                lang.contains("UTF-8", ignoreCase = true) -> true
                lcAll.contains("UTF-8", ignoreCase = true) -> true
                // macOS and Linux generally support Unicode
                !System.getProperty("os.name").lowercase().contains("windows") -> true
                // Windows Terminal supports Unicode
                System.getenv("WT_SESSION") != null -> true
                else -> false
            }
        }

        /** Minimal config - no colors, no unicode, quiet. */
        val MINIMAL =
            ReporterConfig(
                useColor = false,
                useUnicode = false,
                showPerAsset = false,
                showSuggestions = true,
                quietWhenOptimal = true
            )

        /** Verbose config - show everything. */
        val VERBOSE =
            ReporterConfig(
                useColor = true,
                useUnicode = true,
                showPerAsset = true,
                showSuggestions = true,
                quietWhenOptimal = false
            )
    }
}
