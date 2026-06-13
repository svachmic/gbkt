/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.report

import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.Severity
import io.github.gbkt.core.ir.byteSize
import kotlin.math.max

/**
 * Formats a human-readable ASCII budget report from accumulated [PassContext] analysis results.
 *
 * Output format matches the locked developer UX decision — styled like Rust's `cargo build` output
 * so developers always see resource pressure during every build.
 *
 * When [ansiEnabled] is true (default), output includes ANSI color codes:
 * - Green (under 75%): usage is healthy
 * - Yellow (75–90%): usage is elevated, monitor closely
 * - Red (over 90%): usage is critical, action required
 *
 * Pass `ansiEnabled = false` for plain-text file output (CI logs, saved reports).
 *
 * Sample output (ANSI stripped):
 * ```
 * ========================================================
 *  gbkt Budget Report                              Pong
 *  Overall ROM: ~0.1 KB estimated
 * ========================================================
 *
 *  ROM Banks (1 / 2 max, ROM_ONLY)
 *  Bank 1: [=...............] 0%  0.1 KB / 16 KB
 *
 *  Per-scene breakdown
 *  Scene        | Bank | Est. Size | Bank Fill
 *  -------------|------|-----------|----------
 *  gameplay     |    1 |    0.1 KB |       0%
 *
 *  VRAM Tile Budget (per scene)
 *  Scene        | Sprite | BG Avail | BG Used
 *  -------------|--------|----------|--------
 *  gameplay     |      1 |      383 |       0
 *
 *  OAM Sprites: 1 / 40 (2%)
 *  WRAM: 25 / 8192 bytes (0%)
 *  HRAM: 0 / 127 bytes (0%)
 *
 *  0 errors, 0 warnings
 * ========================================================
 * ```
 */
object BudgetReporter {

    private const val REPORT_WIDTH = 56
    private const val BAR_WIDTH = 16
    private const val ROM_BANK_SIZE_BYTES = 16_384
    private const val MAX_OAM_SPRITES = 40
    private const val MAX_HRAM_BYTES = 127

    /** Total Game Boy VRAM tile slots (0..383): 256 BG/window + 128 OBJ-only shared tiles. */
    private const val TOTAL_VRAM_TILES = 384

    // ANSI color codes
    private const val ANSI_GREEN = "\u001B[32m"
    private const val ANSI_YELLOW = "\u001B[33m"
    private const val ANSI_RED = "\u001B[31m"
    private const val ANSI_RESET = "\u001B[0m"

    // Usage thresholds for color selection
    private const val THRESHOLD_YELLOW = 75
    private const val THRESHOLD_RED = 90

    /**
     * Formats a complete budget report string from the given [PassContext].
     *
     * All sections are populated from the accumulated context state — bank assignments, VRAM
     * assignments, OAM assignments, RAM layout, and diagnostics. Any section with no data will show
     * zero values rather than being omitted.
     *
     * @param context The accumulated analysis context from the pipeline.
     * @param ansiEnabled When true (default), wraps percentage bars and values with ANSI color
     *   escape codes. Pass false for plain-text file output or CI environments without color
     *   support.
     */
    fun formatReport(context: PassContext, ansiEnabled: Boolean = true): String = buildString {
        val game = context.game
        val separator = "=".repeat(REPORT_WIDTH)

        // Overall ROM size estimate
        val totalEstimatedBytes = computeTotalEstimatedBytes(context)
        val totalEstimatedKb = "%.1f".format(totalEstimatedBytes / 1024.0)

        // Header
        appendLine(separator)
        val title = " gbkt Budget Report"
        val gamePad = max(0, REPORT_WIDTH - title.length - game.name.length - 1)
        appendLine("$title${" ".repeat(gamePad)}${game.name}")
        appendLine(" Overall ROM: ~$totalEstimatedKb KB estimated")
        appendLine(separator)
        appendLine()

        // ROM Banks section
        formatBankSection(context, ansiEnabled)
        appendLine()

        // Per-scene breakdown
        formatSceneBreakdown(context, ansiEnabled)
        appendLine()

        // VRAM Tile Budget table
        formatVRAMTable(context)
        appendLine()

        // Resource summary lines
        val oamCount = context.oamAssignments.size
        val oamPct = if (MAX_OAM_SPRITES > 0) oamCount * 100 / MAX_OAM_SPRITES else 0
        val oamStr = colorize("$oamCount / $MAX_OAM_SPRITES ($oamPct%)", oamPct, ansiEnabled)
        appendLine(" OAM Sprites: $oamStr")

        val workRam = context.profile.memory.workRam
        val wramUsed = context.ramLayout?.wramUsed ?: 0
        val wramPct = if (workRam > 0) wramUsed * 100 / workRam else 0
        val wramStr = colorize("$wramUsed / $workRam bytes ($wramPct%)", wramPct, ansiEnabled)
        appendLine(" WRAM: $wramStr")

        val hramUsed = context.ramLayout?.hramUsed ?: 0
        val hramPct = if (MAX_HRAM_BYTES > 0) hramUsed * 100 / MAX_HRAM_BYTES else 0
        val hramStr =
            colorize("$hramUsed / $MAX_HRAM_BYTES bytes ($hramPct%)", hramPct, ansiEnabled)
        appendLine(" HRAM: $hramStr")

        // Collections section — only shown when collections are present
        val collectionBytes = context.inventory?.collectionBytes ?: 0
        if (collectionBytes > 0) {
            appendLine()
            formatCollectionsSection(context)
        }
        appendLine()

        // Error/warning summary
        val errors = context.diagnostics.count { it.severity == Severity.ERROR }
        val warnings = context.diagnostics.count { it.severity == Severity.WARNING }
        appendLine(" $errors errors, $warnings warnings")
        append(separator)
    }

    // -------------------------------------------------------------------------
    // ANSI color helpers
    // -------------------------------------------------------------------------

    /**
     * Wraps [text] with the appropriate ANSI color code based on [pct] usage.
     * - < [THRESHOLD_YELLOW]%: green (healthy)
     * - [THRESHOLD_YELLOW]–[THRESHOLD_RED]%: yellow (monitor)
     * - > [THRESHOLD_RED]%: red (critical)
     *
     * Returns [text] unchanged when [ansiEnabled] is false.
     */
    private fun colorize(text: String, pct: Int, ansiEnabled: Boolean): String {
        if (!ansiEnabled) return text
        val code =
            when {
                pct >= THRESHOLD_RED -> ANSI_RED
                pct >= THRESHOLD_YELLOW -> ANSI_YELLOW
                else -> ANSI_GREEN
            }
        return "$code$text$ANSI_RESET"
    }

    /**
     * Builds a percentage bar string of the form `[====----] NN%`.
     *
     * The bar has [BAR_WIDTH] inner characters. `=` characters represent filled space, `-`
     * represents free space. The bar is wrapped with ANSI color when [ansiEnabled] is true.
     */
    private fun buildBar(pct: Int, ansiEnabled: Boolean): String {
        val filled = (pct.coerceIn(0, 100) * BAR_WIDTH / 100).coerceAtLeast(if (pct > 0) 1 else 0)
        val empty = BAR_WIDTH - filled
        val barContent = "=".repeat(filled) + "-".repeat(empty)
        val bar = "[$barContent]"
        return colorize(bar, pct, ansiEnabled)
    }

    // -------------------------------------------------------------------------
    // Total ROM size estimate
    // -------------------------------------------------------------------------

    /** Computes a rough total estimated ROM usage in bytes from all bank assignments. */
    private fun computeTotalEstimatedBytes(context: PassContext): Int {
        val scenesPerBank = mutableMapOf<Int, Int>()
        for ((_, slot) in context.bankAssignments) {
            scenesPerBank[slot.bank] = (scenesPerBank[slot.bank] ?: 0) + 1
        }
        return scenesPerBank.values
            .sumOf { sceneCount -> sceneCount * context.config.bytesPerStatement * 100 }
            .coerceAtLeast(0)
    }

    // -------------------------------------------------------------------------
    // Bank section
    // -------------------------------------------------------------------------

    /**
     * Formats the ROM bank fill bars section using the new `[====----] NN%` bar format.
     *
     * Each bank assigned in [PassContext.bankAssignments] gets a fill bar with a percentage
     * indicator. Bank sizes are shown in KB.
     */
    private fun StringBuilder.formatBankSection(context: PassContext, ansiEnabled: Boolean) {
        val maxBanks = context.config.maxBanks
        val cartridgeType = context.game.config.cartridge
        val usedBankNumbers = context.bankAssignments.values.map { it.bank }.toSet()
        val usedBankCount = usedBankNumbers.size
        appendLine(" ROM Banks ($usedBankCount / $maxBanks max, $cartridgeType)")

        if (usedBankNumbers.isEmpty()) {
            appendLine(" (no banked code assigned)")
            return
        }

        // Compute scenes per bank from bankAssignments
        val scenesPerBank = mutableMapOf<Int, Int>()
        for ((_, slot) in context.bankAssignments) {
            scenesPerBank[slot.bank] = (scenesPerBank[slot.bank] ?: 0) + 1
        }

        for (bank in 1 until maxBanks) {
            if (bank !in usedBankNumbers) continue
            val sceneCount = scenesPerBank[bank] ?: 0
            // Heuristic: estimate usage as scene count * bytesPerStatement * avg op count (100
            // ops/scene)
            val estimatedUsed = sceneCount * context.config.bytesPerStatement * 100
            val fillRatio = estimatedUsed.toDouble() / ROM_BANK_SIZE_BYTES
            val pct = (fillRatio * 100).toInt().coerceIn(0, 100)
            val bar = buildBar(pct, ansiEnabled)
            val usedKb = "%.1f".format(estimatedUsed / 1024.0)
            val maxKb = "%.0f".format(ROM_BANK_SIZE_BYTES / 1024.0)
            val pctColored = colorize("$pct%", pct, ansiEnabled)
            appendLine(" Bank $bank: $bar $pctColored  $usedKb KB / $maxKb KB")
        }
    }

    // -------------------------------------------------------------------------
    // Per-scene breakdown
    // -------------------------------------------------------------------------

    /**
     * Formats the per-scene bank usage breakdown table.
     *
     * Shows each scene with its estimated ROM size contribution, the bank it is assigned to, and
     * the percentage of that bank it contributes.
     */
    private fun StringBuilder.formatSceneBreakdown(context: PassContext, ansiEnabled: Boolean) {
        appendLine(" Per-scene breakdown")

        val scenes = context.game.scenes
        if (scenes.isEmpty()) {
            appendLine(" (no scenes)")
            return
        }

        // Column widths
        val maxSceneLen = max(5, scenes.maxOf { it.id.length })
        val headerScene = "Scene".padEnd(maxSceneLen)
        val headerBank = "Bank"
        val headerSize = "Est. Size"
        val headerFill = "Bank Fill"

        appendLine(" $headerScene | $headerBank | $headerSize | $headerFill")
        val separatorLine = buildString {
            append(" ")
            append("-".repeat(maxSceneLen))
            append("-|-")
            append("-".repeat(headerBank.length))
            append("-|-")
            append("-".repeat(headerSize.length))
            append("-|-")
            append("-".repeat(headerFill.length))
        }
        appendLine(separatorLine)

        for (scene in scenes) {
            val bankSlot = context.bankAssignments[scene.id]
            val bankNum = bankSlot?.bank ?: 0
            // Estimate scene size contribution: bytesPerStatement * avg 100 ops
            val estimatedBytes = context.config.bytesPerStatement * 100
            val estimatedKb = "%.1f".format(estimatedBytes / 1024.0)
            val bankFillPct =
                (estimatedBytes.toDouble() / ROM_BANK_SIZE_BYTES * 100).toInt().coerceIn(0, 100)

            val sceneCol = scene.id.padEnd(maxSceneLen)
            val bankCol = bankNum.toString().padStart(headerBank.length)
            val sizeCol = "${estimatedKb} KB".padStart(headerSize.length)
            val fillStr = "$bankFillPct%"
            // Pad the raw text before colorizing: ANSI escape codes inflate the string
            // length, so padding the colorized string would under-pad the column.
            val fillCol = colorize(fillStr.padStart(headerFill.length), bankFillPct, ansiEnabled)

            appendLine(" $sceneCol | $bankCol | $sizeCol | $fillCol")
        }
    }

    // -------------------------------------------------------------------------
    // Collections section
    // -------------------------------------------------------------------------

    /**
     * Formats the collection memory breakdown section.
     *
     * Shows each declared collection with its type and byte count. Only emitted when
     * [ResourceInventory.collectionBytes] > 0.
     */
    private fun StringBuilder.formatCollectionsSection(context: PassContext) {
        val game = context.game
        val totalBytes = context.inventory?.collectionBytes ?: 0
        appendLine(" Collections ($totalBytes bytes WRAM)")

        for (ht in game.hashTables) {
            val bytes = ht.size * (ht.keyType.byteSize + ht.valueType.byteSize + 1)
            appendLine("  hash_table ${ht.name}: ${ht.size} slots — $bytes bytes")
        }
        for (pool in game.pools) {
            val bitmapBytes = (pool.capacity + 7) / 8
            val bytes = pool.capacity * pool.elementType.byteSize + bitmapBytes + 1
            appendLine("  pool ${pool.name}: ${pool.capacity} capacity — $bytes bytes")
        }
        for (rb in game.ringBuffers) {
            val bytes = rb.capacity * rb.elementType.byteSize + 3
            appendLine("  ring_buffer ${rb.name}: ${rb.capacity} capacity — $bytes bytes")
        }
        for (fs in game.fixedSlots) {
            val bitfieldBytes = if (fs.count <= 8) 1 else 2
            val bytes = fs.count * fs.elementType.byteSize + bitfieldBytes
            appendLine("  fixed_slots ${fs.name}: ${fs.count} slots — $bytes bytes")
        }
    }

    // -------------------------------------------------------------------------
    // VRAM table section
    // -------------------------------------------------------------------------

    /**
     * Formats the per-scene VRAM tile budget table.
     *
     * Columns: Scene | Sprite | BG Avail | BG Used
     *
     * Sprite tile count is derived from [PassContext.oamAssignments] and per-actor VRAM ranges. BG
     * available is the total VRAM tile budget minus sprites and global tiles. BG used comes from
     * [PassContext.vramAssignments].
     */
    private fun StringBuilder.formatVRAMTable(context: PassContext) {
        appendLine(" VRAM Tile Budget (per scene)")

        val scenes = context.game.scenes
        if (scenes.isEmpty()) {
            appendLine(" (no scenes)")
            return
        }

        // Column widths
        val maxSceneLen = max(5, scenes.maxOf { it.id.length })
        val headerScene = "Scene".padEnd(maxSceneLen)
        val headerSprite = "Sprite"
        val headerBgAvail = "BG Avail"
        val headerBgUsed = "BG Used"

        appendLine(" $headerScene | $headerSprite | $headerBgAvail | $headerBgUsed")
        val separatorLine = buildString {
            append(" ")
            append("-".repeat(maxSceneLen))
            append("-|-")
            append("-".repeat(headerSprite.length))
            append("-|-")
            append("-".repeat(headerBgAvail.length))
            append("-|-")
            append("-".repeat(headerBgUsed.length))
        }
        appendLine(separatorLine)

        for (scene in scenes) {
            // Compute sprite tiles for this scene's actors
            val sceneActorIds = scene.actorIds.toSet()
            val spriteTiles =
                context.vramAssignments
                    .filter { (id, _) -> id in sceneActorIds }
                    .values
                    .sumOf { range -> range.endTile - range.startTile }

            // BG available = total - sprite tiles (global tiles already counted in sprite range)
            val bgAvail = TOTAL_VRAM_TILES - spriteTiles
            val sceneRange = context.vramAssignments[scene.id]
            val bgUsed = if (sceneRange != null) sceneRange.endTile - sceneRange.startTile else 0

            val sceneCol = scene.id.padEnd(maxSceneLen)
            val spriteCol = spriteTiles.toString().padStart(headerSprite.length)
            val bgAvailCol = bgAvail.toString().padStart(headerBgAvail.length)
            val bgUsedCol = bgUsed.toString().padStart(headerBgUsed.length)

            appendLine(" $sceneCol | $spriteCol | $bgAvailCol | $bgUsedCol")
        }
    }
}
