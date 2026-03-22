/*
 * Copyright 2026 Michal Svacha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.gbkt.intellij.gutter

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import java.io.File
import javax.swing.Icon
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * Line marker provider that shows budget usage icons in the gutter next to `scene { }` and `actor {
 * }` DSL blocks in gbkt Kotlin files.
 *
 * Icon color thresholds:
 * - Green (< 75%): [AllIcons.RunConfigurations.TestPassed] — resources well within limits
 * - Yellow (75-90%): [AllIcons.General.Warning] — approaching limits, consider splitting
 * - Red (> 90%): [AllIcons.RunConfigurations.TestFailed] — near or over limits
 *
 * Budget data is read from `build/gbkt/budget-report.txt` in the project directory. If no budget
 * report exists (Gradle `budgetReport` task not yet run), this provider returns null gracefully so
 * no gutter icon is shown.
 */
class BudgetGutterIconProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? =
        resolveBudgetEntry(element)?.let { (entry, ref) ->
            val icon = iconForUsage(entry.usagePercent)
            val tooltip = buildTooltip(entry)
            LineMarkerInfo(
                ref,
                ref.textRange,
                icon,
                { tooltip },
                null,
                GutterIconRenderer.Alignment.LEFT,
                { tooltip },
            )
        }

    /**
     * Resolves the budget entry for the given element, or returns null if the element is not a
     * tracked DSL call site with matching budget data.
     */
    private fun resolveBudgetEntry(
        element: PsiElement
    ): Pair<BudgetEntry, KtNameReferenceExpression>? {
        val ref = element as? KtNameReferenceExpression ?: return null
        val parent = ref.parent as? KtCallExpression ?: return null
        val calleeName = ref.getReferencedName()

        // Guard: must be a tracked DSL call in a gbkt-related file
        val vFile = ref.containingFile?.virtualFile
        val isTrackedCall = calleeName in TRACKED_DSL_FUNCTIONS
        val isGbktFile = vFile?.name?.let { it.endsWith(".kt") || it.endsWith(".gbkt.kts") } == true
        if (!isTrackedCall || !isGbktFile) return null

        // Guard: must have budget data for this call site
        val budgetData = readBudgetReport(ref.project) ?: return null
        val blockId = extractFirstStringArg(parent) ?: calleeName
        val entry = budgetData.findEntryFor(blockId) ?: budgetData.findEntryFor(calleeName)
        return entry?.let { it to ref }
    }

    // -------------------------------------------------------------------------
    // Budget report parsing
    // -------------------------------------------------------------------------

    /**
     * Read and parse the budget report from `build/gbkt/budget-report.txt`.
     *
     * Returns null gracefully when the file does not exist (budget task not yet run).
     */
    private fun readBudgetReport(project: Project): BudgetData? {
        val projectPath = project.basePath ?: return null
        val reportFile = File(projectPath, BUDGET_REPORT_PATH)
        if (!reportFile.exists()) return null

        return try {
            parseBudgetReport(reportFile.readText())
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            // Silently ignore — budget report may be malformed or partially written during build
            null
        }
    }

    /**
     * Parse the budget report text into a [BudgetData] structure.
     *
     * Expected format lines (subset):
     * ```
     * [scene:gameplay] Bank 1: 12345/16384 bytes (75%)  VRAM: 128/384 tiles (33%)
     * [actor:ball]     Bank 1: 500/16384 bytes (3%)     OAM: 1/40 sprites (2%)
     * ```
     *
     * Lines that don't match are silently skipped.
     */
    private fun parseBudgetReport(content: String): BudgetData {
        val entries =
            content.lineSequence().mapNotNull { line -> parseReportLine(line.trim()) }.toList()
        return BudgetData(entries)
    }

    /**
     * Parses a single budget report line into a [BudgetEntry], or returns null if the line is a
     * comment, blank, or doesn't match the expected format.
     */
    private fun parseReportLine(trimmed: String): BudgetEntry? {
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null

        // Match lines like: [scene:gameplay] Bank 1: 12345/16384 bytes (75%) ...
        val blockMatch = BLOCK_LINE_REGEX.find(trimmed) ?: return null
        val id = blockMatch.groupValues[1]
        val usedBytes = blockMatch.groupValues[2].toLongOrNull() ?: return null
        val totalBytes = blockMatch.groupValues[3].toLongOrNull() ?: return null
        val usagePercent =
            blockMatch.groupValues[4].toIntOrNull() ?: calculatePercent(usedBytes, totalBytes)

        // Optional: extract VRAM line in same report line
        val vramMatch = VRAM_REGEX.find(trimmed)
        val vramDetail =
            if (vramMatch != null) {
                val usedTiles = vramMatch.groupValues[1]
                val totalTiles = vramMatch.groupValues[2]
                val vramPct = vramMatch.groupValues[3].toIntOrNull() ?: 0
                "VRAM: $usedTiles / $totalTiles tiles ($vramPct%)"
            } else null

        return BudgetEntry(
            id = id,
            bankUsedBytes = usedBytes,
            bankTotalBytes = totalBytes,
            usagePercent = usagePercent,
            vramDetail = vramDetail,
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Extract the first string argument from a call expression (used as block ID). e.g.
     * `scene("gameplay") { ... }` → "gameplay"
     */
    private fun extractFirstStringArg(call: KtCallExpression): String? {
        val firstArg = call.valueArguments.firstOrNull() ?: return null
        val argExpr = firstArg.getArgumentExpression() ?: return null
        // Strip surrounding quotes from a simple string literal
        val text = argExpr.text
        return if (text.startsWith("\"") && text.endsWith("\"") && text.length >= 2) {
            text.substring(1, text.length - 1)
        } else null
    }

    /** Map a usage percentage to the appropriate gutter icon. */
    private fun iconForUsage(usagePercent: Int): Icon =
        when {
            usagePercent > 90 -> AllIcons.RunConfigurations.TestFailed // red — over limit
            usagePercent > 75 -> AllIcons.General.Warning // yellow — approaching limit
            else -> AllIcons.RunConfigurations.TestPassed // green — well within limits
        }

    /** Build tooltip text with bank and optional VRAM usage details. */
    private fun buildTooltip(entry: BudgetEntry): String {
        val bankKb = entry.bankTotalBytes / 1024
        val bankDetail =
            "Bank: ${formatBytes(entry.bankUsedBytes)} / ${formatBytes(entry.bankTotalBytes)} (${entry.usagePercent}%)"
        return if (entry.vramDetail != null) {
            "$bankDetail\n${entry.vramDetail}\n(${entry.id})"
        } else {
            "$bankDetail\n(${entry.id})"
        }
    }

    private fun formatBytes(bytes: Long): String {
        return if (bytes >= 1024) "${bytes / 1024} KB" else "$bytes B"
    }

    private fun calculatePercent(used: Long, total: Long): Int {
        if (total == 0L) return 0
        return ((used * 100) / total).toInt()
    }

    // -------------------------------------------------------------------------
    // Data classes
    // -------------------------------------------------------------------------

    private data class BudgetEntry(
        val id: String,
        val bankUsedBytes: Long,
        val bankTotalBytes: Long,
        val usagePercent: Int,
        val vramDetail: String?,
    )

    private class BudgetData(private val entries: List<BudgetEntry>) {
        fun findEntryFor(id: String): BudgetEntry? =
            entries.firstOrNull { it.id == id || it.id.endsWith(":$id") }
    }

    companion object {
        /** DSL functions whose call sites get budget gutter icons. */
        private val TRACKED_DSL_FUNCTIONS = setOf("scene", "actor")

        /** Path to the budget report relative to project base path. */
        private const val BUDGET_REPORT_PATH = "build/gbkt/budget-report.txt"

        /**
         * Regex matching lines like: `[scene:gameplay] Bank 1: 12345/16384 bytes (75%)` Groups:
         * 1=id, 2=usedBytes, 3=totalBytes, 4=percent
         */
        private val BLOCK_LINE_REGEX =
            Regex("""\[([^\]]+)\]\s+Bank\s+\d+:\s+(\d+)/(\d+)\s+bytes\s+\((\d+)%\)""")

        /**
         * Regex matching VRAM segment in budget lines: `VRAM: 128/384 tiles (33%)` Groups:
         * 1=usedTiles, 2=totalTiles, 3=percent
         */
        private val VRAM_REGEX = Regex("""VRAM:\s+(\d+)/(\d+)\s+tiles\s+\((\d+)%\)""")
    }
}
