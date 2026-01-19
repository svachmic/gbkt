/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("CyclomaticComplexMethod") // PO parser has many branches for entry types

package io.github.gbkt.core.assets

import io.github.gbkt.core.ir.GameString
import io.github.gbkt.core.ir.StringNamespace
import io.github.gbkt.core.ir.StringTable
import java.io.File

/**
 * Parser for GNU gettext .po/.pot files.
 *
 * PO files are the industry standard for localization with extensive tooling support (POEdit,
 * Crowdin, Lokalise, Weblate). This parser extracts strings grouped by msgctxt (context) into
 * namespaces for automatic bank allocation.
 *
 * ## PO File Format
 *
 * ```po
 * # Translator comment
 * #. Extracted comment
 * #: src/game.kt:42
 * #, fuzzy
 * msgctxt "namespace"
 * msgid "key"
 * msgstr "translated value"
 * ```
 *
 * ## Supported Features
 * - **msgctxt**: Maps to namespace name (required for bank grouping)
 * - **msgid**: Maps to string key
 * - **msgstr**: Maps to string value
 * - **Extracted comments (#.)**: Preserved for reference
 * - **Multi-line strings**: Concatenated automatically
 * - **Escape sequences**: \n, \t, \\, \" supported
 *
 * ## Bank Allocation
 *
 * Banks are NOT specified in .po files. Use [BankAllocator.allocateForStrings] to automatically
 * assign banks based on namespace size and 16KB bank limits.
 *
 * @see StringTable
 * @see BankAllocator
 */
object PoParser {

    /**
     * Parse a .po or .pot file and return a StringTable.
     *
     * Note: The returned StringTable has bank=0 for all namespaces. Use
     * [BankAllocator.allocateForStrings] to assign proper banks.
     *
     * @param file The .po/.pot file to parse
     * @return The parsed StringTable with unassigned banks (all 0)
     */
    fun parse(file: File): StringTable {
        return parse(file.readText())
    }

    /**
     * Parse PO file content and return a StringTable.
     *
     * @param content The .po/.pot file content
     * @return The parsed StringTable with unassigned banks (all 0)
     */
    fun parse(content: String): StringTable {
        val entries = parseEntries(content)
        return entriesToStringTable(entries)
    }

    /**
     * Parse and validate a .po file.
     *
     * @param file The .po/.pot file to parse
     * @return Pair of (StringTable, list of warning messages)
     */
    fun parseWithValidation(file: File): Pair<StringTable, List<String>> {
        val entries = parseEntries(file.readText())
        val warnings = mutableListOf<String>()

        // Check for missing context
        val noContext = entries.filter { it.context.isNullOrEmpty() && it.msgid.isNotEmpty() }
        if (noContext.isNotEmpty()) {
            warnings.add(
                "Found ${noContext.size} string(s) without msgctxt. " +
                    "These will use 'default' namespace: ${noContext.take(3).map { it.msgid }}"
            )
        }

        // Check for empty msgstr in non-template files
        if (!file.name.endsWith(".pot")) {
            val untranslated = entries.filter { it.msgstr.isEmpty() && it.msgid.isNotEmpty() }
            if (untranslated.isNotEmpty()) {
                warnings.add(
                    "Found ${untranslated.size} untranslated string(s): " +
                        "${untranslated.take(3).map { it.msgid }}"
                )
            }
        }

        // Check for duplicate keys within same context
        val grouped = entries.filter { it.msgid.isNotEmpty() }.groupBy { it.context to it.msgid }
        val duplicates = grouped.filter { it.value.size > 1 }
        for ((key, dups) in duplicates) {
            warnings.add(
                "Duplicate key '${key.second}' in context '${key.first}' appears ${dups.size} times"
            )
        }

        return entriesToStringTable(entries) to warnings
    }

    /**
     * Get metadata from a PO file (language, charset, etc.).
     *
     * @param file The .po/.pot file
     * @return Map of metadata key-value pairs
     */
    fun getMetadata(file: File): Map<String, String> {
        val entries = parseEntries(file.readText())
        val headerEntry =
            entries.find { it.msgid.isEmpty() && it.msgstr.isNotEmpty() } ?: return emptyMap()

        return headerEntry.msgstr
            .lines()
            .filter { it.contains(':') }
            .associate { line ->
                val (key, value) = line.split(':', limit = 2)
                key.trim() to value.trim()
            }
    }

    // -------------------------------------------------------------------------
    // Internal parsing
    // -------------------------------------------------------------------------

    private data class PoEntry(
        val context: String?,
        val msgid: String,
        val msgstr: String,
        val extractedComments: List<String> = emptyList(),
        val flags: List<String> = emptyList(),
        val references: List<String> = emptyList(),
        val bank: Int? = null,
    )

    private fun parseEntries(content: String): List<PoEntry> {
        val entries = mutableListOf<PoEntry>()
        val lines = content.lines()

        var currentContext: String? = null
        var currentMsgid: String? = null
        var currentMsgstr: String? = null
        var extractedComments = mutableListOf<String>()
        var flags = mutableListOf<String>()
        var references = mutableListOf<String>()
        var currentBank: Int? = null
        var inMsgid = false
        var inMsgstr = false
        var inMsgctxt = false

        fun finishEntry() {
            if (currentMsgid != null) {
                entries.add(
                    PoEntry(
                        context = currentContext,
                        msgid = currentMsgid ?: "",
                        msgstr = currentMsgstr ?: "",
                        extractedComments = extractedComments.toList(),
                        flags = flags.toList(),
                        references = references.toList(),
                        bank = currentBank,
                    )
                )
            }
            currentContext = null
            currentMsgid = null
            currentMsgstr = null
            extractedComments = mutableListOf()
            flags = mutableListOf()
            references = mutableListOf()
            currentBank = null
            inMsgid = false
            inMsgstr = false
            inMsgctxt = false
        }

        for (line in lines) {
            val trimmed = line.trim()

            when {
                // Empty line - finish current entry
                trimmed.isEmpty() -> finishEntry()

                // Translator comment (ignored)
                trimmed.startsWith("# ") || trimmed == "#" -> {}

                // Extracted comment (may contain @bank hint)
                trimmed.startsWith("#.") -> {
                    val comment = trimmed.removePrefix("#.").trim()
                    // Check for @bank hint: "#. @bank 3"
                    val bankMatch = BANK_HINT_REGEX.find(comment)
                    if (bankMatch != null) {
                        currentBank = bankMatch.groupValues[1].toIntOrNull()
                    }
                    extractedComments.add(comment)
                }

                // Reference
                trimmed.startsWith("#:") -> {
                    references.add(trimmed.removePrefix("#:").trim())
                }

                // Flags (fuzzy, c-format, etc.)
                trimmed.startsWith("#,") -> {
                    flags.addAll(trimmed.removePrefix("#,").split(",").map { it.trim() })
                }

                // Context start
                trimmed.startsWith("msgctxt ") -> {
                    inMsgctxt = true
                    inMsgid = false
                    inMsgstr = false
                    currentContext = extractQuotedString(trimmed.removePrefix("msgctxt "))
                }

                // Message ID start
                trimmed.startsWith("msgid ") -> {
                    inMsgctxt = false
                    inMsgid = true
                    inMsgstr = false
                    currentMsgid = extractQuotedString(trimmed.removePrefix("msgid "))
                }

                // Message string start
                trimmed.startsWith("msgstr ") -> {
                    inMsgctxt = false
                    inMsgid = false
                    inMsgstr = true
                    currentMsgstr = extractQuotedString(trimmed.removePrefix("msgstr "))
                }

                // Continuation line (starts with ")
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

        // Don't forget the last entry
        finishEntry()

        return entries
    }

    private fun extractQuotedString(s: String): String {
        val trimmed = s.trim()
        if (!trimmed.startsWith("\"") || !trimmed.endsWith("\"")) {
            return trimmed
        }
        return unescapePoString(trimmed.drop(1).dropLast(1))
    }

    private fun unescapePoString(s: String): String {
        return buildString {
            var i = 0
            while (i < s.length) {
                if (s[i] == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) {
                        'n' -> append('\n')
                        't' -> append('\t')
                        'r' -> append('\r')
                        '"' -> append('"')
                        '\\' -> append('\\')
                        else -> {
                            append(s[i])
                            append(s[i + 1])
                        }
                    }
                    i += 2
                } else {
                    append(s[i])
                    i++
                }
            }
        }
    }

    private fun entriesToStringTable(entries: List<PoEntry>): StringTable {
        // Group entries by context (namespace)
        val byNamespace =
            entries
                .filter { it.msgid.isNotEmpty() } // Skip header entry
                .groupBy { it.context ?: DEFAULT_NAMESPACE }

        val namespaces =
            byNamespace.map { (name, namespaceEntries) ->
                val strings =
                    namespaceEntries.map { entry -> GameString(entry.msgid, entry.msgstr) }
                // Use bank hint from the first entry that has one, or UNASSIGNED for
                // auto-allocation
                val bankHint = namespaceEntries.firstNotNullOfOrNull { it.bank } ?: BANK_UNASSIGNED
                StringNamespace(name, bank = bankHint, strings)
            }

        return StringTable(namespaces)
    }

    private const val DEFAULT_NAMESPACE = "default"

    /** Sentinel value indicating bank should be auto-allocated. */
    const val BANK_UNASSIGNED = -1

    /** Regex to match @bank hints in extracted comments. */
    private val BANK_HINT_REGEX = Regex("""@bank\s+(\d+)""")

    /** Default bank for strings (can be overridden by BankAllocator). */
    const val DEFAULT_STRING_BANK = 1
}
