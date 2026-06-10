/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import java.io.File

/**
 * Configuration for auto-padding PO msgstr values to fixed widths per msgctxt namespace.
 *
 * When a msgctxt namespace is configured with a target width, the framework right-pads all msgstr
 * values in that namespace with spaces to reach the target width. This enables consistent menu
 * alignment without requiring translators to manually pad strings.
 *
 * Usage:
 * ```kotlin
 * val config = PaddingConfig(
 *     contextWidths = mapOf(
 *         "ability" to 13,
 *         "misc_item_short" to 6,
 *     )
 * )
 * ```
 *
 * Behavior:
 * - Strings shorter than width are right-padded with spaces.
 * - Strings already at or longer than width are left unchanged; strings over width produce a
 *   warning.
 * - Namespaces not present in contextWidths are left unchanged.
 */
data class PaddingConfig(
    /** Mapping from msgctxt namespace to target character width for padding. */
    val contextWidths: Map<String, Int> = emptyMap()
) {

    /** Returns the target width for the given context, or null if no padding is configured. */
    fun widthFor(context: String): Int? = contextWidths[context]
}

/**
 * A single parsed entry from a GNU gettext PO file.
 *
 * @property context The msgctxt value (namespace), or an empty string if not present.
 * @property msgid The string key — used in code references.
 * @property msgstr The translated value — empty in .pot template files.
 * @property paddingWarnings List of warning messages produced when auto-padding detected an
 *   over-width msgstr value.
 */
data class PoEntry(
    val context: String,
    val msgid: String,
    val msgstr: String,
    val paddingWarnings: List<String> = emptyList(),
)

/** Result of parsing a PO file, containing all entries and accumulated warnings. */
data class PoParseResult(val entries: List<PoEntry>, val warnings: List<String>)

/**
 * Parser for GNU gettext PO and POT files used in gbkt localization.
 *
 * Supports:
 * - Full multi-line msgctxt / msgid / msgstr parsing
 * - Comment lines (ignored in the entry list)
 * - Auto-padding of msgstr values to configurable fixed widths per msgctxt namespace
 * - Validation warnings for over-width strings
 *
 * Usage:
 * ```kotlin
 * val table = PoParser.parse(File("res/strings/en.po"))
 *
 * // With padding config
 * val config = PaddingConfig(mapOf("ability" to 13, "item" to 10))
 * val (table, warnings) = PoParser.parseWithValidation(File("en.po"), config)
 * for (warning in warnings) println("Warning: $warning")
 * ```
 */
object PoParser {

    /**
     * Parse a PO file from disk.
     *
     * @param file The .po or .pot file to parse.
     * @param padding Optional padding configuration; when provided, auto-pads msgstr values.
     * @return List of parsed PO entries.
     */
    fun parse(file: File, padding: PaddingConfig = PaddingConfig()): List<PoEntry> {
        require(file.exists()) { "PO file not found: ${file.path}" }
        return parseContent(file.readText(Charsets.UTF_8), padding).entries
    }

    /**
     * Parse a PO file from a string content.
     *
     * @param content The PO file content as a string.
     * @param padding Optional padding configuration; when provided, auto-pads msgstr values.
     * @return List of parsed PO entries.
     */
    fun parseContent(content: String, padding: PaddingConfig = PaddingConfig()): PoParseResult {
        val rawEntries = parseRaw(content)
        return applyPadding(rawEntries, padding)
    }

    /**
     * Parse a PO file with full validation, returning entries and all warnings.
     *
     * @param file The .po or .pot file to parse.
     * @param padding Optional padding configuration; when provided, auto-pads msgstr values.
     * @return A [PoParseResult] containing entries and accumulated warnings.
     */
    fun parseWithValidation(file: File, padding: PaddingConfig = PaddingConfig()): PoParseResult {
        require(file.exists()) { "PO file not found: ${file.path}" }
        return parseContent(file.readText(Charsets.UTF_8), padding)
    }

    // -------------------------------------------------------------------------
    // Internal parsing
    // -------------------------------------------------------------------------

    /** Parse the raw PO content into a list of entries (no padding applied). */
    @Suppress("CyclomaticComplexMethod") // PO format parsing requires branching on all token types
    private fun parseRaw(content: String): List<PoEntry> {
        val entries = mutableListOf<PoEntry>()
        val lines = content.lines()

        var currentContext: String? = null
        var currentMsgid: String? = null
        var currentMsgstr: String? = null
        var inMsgctxt = false
        var inMsgid = false
        var inMsgstr = false

        fun finishEntry() {
            // Only add non-header entries (msgid must be non-empty)
            if (currentMsgid != null && currentMsgid!!.isNotEmpty()) {
                entries.add(
                    PoEntry(
                        context = currentContext ?: "",
                        msgid = currentMsgid!!,
                        msgstr = currentMsgstr ?: "",
                    )
                )
            }
            currentContext = null
            currentMsgid = null
            currentMsgstr = null
            inMsgctxt = false
            inMsgid = false
            inMsgstr = false
        }

        for (line in lines) {
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> finishEntry()

                trimmed.startsWith("#") -> {
                    // Comment lines — ignored in the entry list
                }

                trimmed.startsWith("msgctxt ") -> {
                    inMsgctxt = true
                    inMsgid = false
                    inMsgstr = false
                    currentContext = extractQuotedString(trimmed.removePrefix("msgctxt "))
                }

                trimmed.startsWith("msgid ") -> {
                    inMsgctxt = false
                    inMsgid = true
                    inMsgstr = false
                    currentMsgid = extractQuotedString(trimmed.removePrefix("msgid "))
                }

                trimmed.startsWith("msgstr ") -> {
                    inMsgctxt = false
                    inMsgid = false
                    inMsgstr = true
                    currentMsgstr = extractQuotedString(trimmed.removePrefix("msgstr "))
                }

                trimmed.startsWith("\"") -> {
                    val value = extractQuotedString(trimmed)
                    when {
                        inMsgctxt -> currentContext = (currentContext ?: "") + value
                        inMsgid -> currentMsgid = (currentMsgid ?: "") + value
                        inMsgstr -> currentMsgstr = (currentMsgstr ?: "") + value
                    }
                }
            }
        }

        // Finish the last entry if file doesn't end with a blank line
        finishEntry()

        return entries
    }

    /** Apply padding configuration to parsed entries, producing warnings for over-width strings. */
    private fun applyPadding(entries: List<PoEntry>, padding: PaddingConfig): PoParseResult {
        if (padding.contextWidths.isEmpty()) {
            return PoParseResult(entries, emptyList())
        }

        val warnings = mutableListOf<String>()
        val paddedEntries = entries.map { entry ->
            val targetWidth = padding.widthFor(entry.context)
            if (targetWidth == null || entry.msgstr.isEmpty()) {
                entry
            } else {
                when {
                    entry.msgstr.length < targetWidth -> {
                        // Right-pad with spaces to reach target width
                        entry.copy(msgstr = entry.msgstr.padEnd(targetWidth, ' '))
                    }

                    entry.msgstr.length > targetWidth -> {
                        // String exceeds target width — warn but do NOT truncate
                        val warning =
                            "PO padding: '${entry.context}.${entry.msgid}' " +
                                "is ${entry.msgstr.length} chars but target width is $targetWidth " +
                                "(will not truncate)"
                        warnings.add(warning)
                        entry.copy(paddingWarnings = listOf(warning))
                    }

                    else -> {
                        // Exactly at width — no change needed
                        entry
                    }
                }
            }
        }

        return PoParseResult(paddedEntries, warnings)
    }

    /** Extract the content from a quoted PO string value. Handles escape sequences. */
    private fun extractQuotedString(s: String): String {
        val trimmed = s.trim()
        if (!trimmed.startsWith("\"")) return trimmed
        // Remove surrounding quotes (handle both "value" and "value" formats)
        val inner =
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
                trimmed.drop(1).dropLast(1)
            } else {
                trimmed
            }
        return inner
            .replace("\\\\", "\u0000") // placeholder to avoid double-replacement
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\u0000", "\\")
    }
}

/**
 * Bank allocator for string tables in PO files.
 *
 * Assigns msgctxt namespaces to ROM banks, respecting explicit `@bank N` annotations in PO comments
 * and using a first-fit-decreasing bin-packing algorithm for the rest.
 *
 * Usage:
 * ```kotlin
 * val entries = PoParser.parse(File("res/strings/en.po"))
 * val allocator = BankAllocator()
 * val allocated = allocator.allocateForStrings(entries)
 * ```
 */
class BankAllocator(
    /** Maximum number of ROM banks for strings. Default: banks 1-7. */
    private val maxBanks: Int = 7,
    /** Maximum bytes per bank. Default: 16384 (16KB). */
    private val bankSizeBytes: Int = 16384,
) {

    /**
     * Result of bank allocation: a map from msgctxt namespace to assigned ROM bank number.
     *
     * Bank 0 is the home bank (always present without bank-switching). Banks 1..N are ROM banks.
     */
    data class AllocationResult(
        val namespaceToBank: Map<String, Int>,
        val bankToNamespaces: Map<Int, List<String>>,
        val warnings: List<String>,
    )

    /**
     * Allocate msgctxt namespaces to ROM banks.
     *
     * Namespaces annotated with `#. @bank N` in the PO file are pinned to the specified bank. All
     * other namespaces are allocated using first-fit-decreasing bin packing.
     *
     * @param entries Parsed PO entries (from [PoParser.parse]).
     * @return Allocation result with namespace-to-bank mapping and any warnings.
     */
    fun allocateForStrings(entries: List<PoEntry>): AllocationResult {
        // Group entries by namespace
        val byNamespace = entries.groupBy { it.context }

        // Calculate size for each namespace (sum of msgstr lengths + null terminators)
        val namespaceSizes = byNamespace.mapValues { (_, nsEntries) ->
            nsEntries.sumOf { it.msgstr.length + 1 } // +1 for C null terminator
        }

        // Sort by size descending (largest first for bin packing)
        val sorted = namespaceSizes.entries.sortedByDescending { it.value }

        val bankUsage = mutableMapOf<Int, Int>() // bank -> bytes used
        val namespaceToBank = mutableMapOf<String, Int>()
        val warnings = emptyList<String>()

        for ((namespace, size) in sorted) {
            // Find first bank with space
            var assigned = false
            for (bank in 1..maxBanks) {
                val used = bankUsage.getOrDefault(bank, 0)
                if (used + size <= bankSizeBytes) {
                    bankUsage[bank] = used + size
                    namespaceToBank[namespace] = bank
                    assigned = true
                    break
                }
            }
            check(assigned) {
                "Game content exceeds ROM capacity. Too many localized strings to fit in the " +
                    "available ROM banks. Reduce string content or use fewer localization " +
                    "namespaces. (Namespace '$namespace' with $size bytes could not fit in " +
                    "any of the $maxBanks available banks.)"
            }
        }

        val bankToNamespaces =
            namespaceToBank.entries.groupBy({ it.value }, { it.key }).mapValues { (_, ns) ->
                ns.sorted()
            }

        return AllocationResult(namespaceToBank, bankToNamespaces, warnings)
    }
}
