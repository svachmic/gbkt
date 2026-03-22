/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.postprocess

// =============================================================================
// SHARED CONSTANT TABLE PASS
// Scans generated C text for identical constant arrays and deduplicates them.
//
// When two or more `const` arrays have identical initializer content, the first
// occurrence is kept as canonical and subsequent occurrences are replaced with
// `#define duplicate_name canonical_name`.
//
// This operates on raw C text (machine-generated, predictable formatting)
// after codegen but before files are written to disk.
// =============================================================================

/**
 * Result of running SharedConstantTablePass on a C text string.
 *
 * @property optimizedContent The C text with duplicate arrays replaced by #define aliases.
 * @property arraysDeduped The number of duplicate arrays that were replaced.
 * @property details Human-readable descriptions of each deduplication.
 */
data class ConstantDeduplicationResult(
    val optimizedContent: String,
    val arraysDeduped: Int,
    val details: List<String>,
)

/**
 * Post-processing pass that deduplicates identical constant arrays in generated C text.
 *
 * Handles:
 * - Single-line arrays: `const UINT8 foo[] = {0x00, 0x01, 0x02};`
 * - Multi-line arrays (values span lines, terminated by `};`)
 * - Various types: UINT8, UINT16, INT8, unsigned char, const char*
 */
object SharedConstantTablePass {

    // Matches a const array declaration start: captures type and name
    // Examples:
    //   const UINT8 foo[] = {0x00};
    //   const unsigned char bar[] = {
    private val ARRAY_START_PATTERN =
        Regex("""^(const\s+[\w\s*]+?\s+(\w+)\[\]\s*=\s*\{)""", setOf(RegexOption.MULTILINE))

    /**
     * Optimize the given C text by deduplicating identical constant arrays.
     *
     * @param cContent The raw C source text to process.
     * @return A [ConstantDeduplicationResult] with optimized text and stats.
     */
    fun optimize(cContent: String): ConstantDeduplicationResult {
        if (cContent.isBlank()) {
            return ConstantDeduplicationResult(cContent, 0, emptyList())
        }

        // Extract all constant arrays from the text
        val arrays = extractConstArrays(cContent)

        if (arrays.isEmpty()) {
            return ConstantDeduplicationResult(cContent, 0, emptyList())
        }

        // Group by normalized initializer content
        val byInitializer = mutableMapOf<String, MutableList<ConstArrayEntry>>()
        for (entry in arrays) {
            val key = normalizeInitializer(entry.initializer)
            byInitializer.getOrPut(key) { mutableListOf() }.add(entry)
        }

        // Find groups with duplicates
        val duplicateGroups = byInitializer.values.filter { it.size > 1 }

        if (duplicateGroups.isEmpty()) {
            return ConstantDeduplicationResult(cContent, 0, emptyList())
        }

        // Apply replacements: for each duplicate group, replace all but the first
        // We must process replacements from end to start to preserve offsets
        data class Replacement(val start: Int, val end: Int, val replacement: String)

        val replacements = mutableListOf<Replacement>()
        val details = mutableListOf<String>()

        for (group in duplicateGroups) {
            val canonical = group.first()
            val duplicates = group.drop(1)
            for (dup in duplicates) {
                val defineAlias = "#define ${dup.name} ${canonical.name}"
                replacements.add(Replacement(dup.declarationStart, dup.declarationEnd, defineAlias))
                details.add(
                    "Replaced duplicate array '${dup.name}' with alias to '${canonical.name}'"
                )
            }
        }

        // Sort replacements from end to start so positions remain valid
        replacements.sortByDescending { it.start }

        var result = cContent
        for (rep in replacements) {
            result = result.substring(0, rep.start) + rep.replacement + result.substring(rep.end)
        }

        return ConstantDeduplicationResult(result, replacements.size, details)
    }

    /** Represents a parsed constant array from C text. */
    private data class ConstArrayEntry(
        val name: String,
        val initializer: String,
        val declarationStart: Int,
        val declarationEnd: Int,
    )

    /**
     * Extract all constant array declarations from the C text. Handles both single-line and
     * multi-line arrays.
     */
    private fun extractConstArrays(cContent: String): List<ConstArrayEntry> {
        val results = mutableListOf<ConstArrayEntry>()

        var searchFrom = 0
        while (searchFrom < cContent.length) {
            val match = ARRAY_START_PATTERN.find(cContent, searchFrom) ?: break

            val declarationStart = match.range.first
            val arrayName = match.groupValues[2]

            // Find the opening brace position
            val openBracePos = cContent.indexOf('{', match.range.first)
            if (openBracePos == -1) {
                searchFrom = match.range.last + 1
                continue
            }

            // Find the matching closing brace followed by semicolon
            // Track brace depth starting from opening brace
            var depth = 0
            var pos = openBracePos
            var closeBracePos = -1

            while (pos < cContent.length) {
                when (cContent[pos]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            closeBracePos = pos
                            break
                        }
                    }
                }
                pos++
            }

            if (closeBracePos == -1) {
                searchFrom = match.range.last + 1
                continue
            }

            // Check for semicolon after closing brace (skip whitespace)
            var afterBrace = closeBracePos + 1
            while (afterBrace < cContent.length && cContent[afterBrace] == ' ') {
                afterBrace++
            }

            if (afterBrace >= cContent.length || cContent[afterBrace] != ';') {
                searchFrom = match.range.last + 1
                continue
            }

            val declarationEnd = afterBrace + 1 // inclusive of semicolon
            val initializer = cContent.substring(openBracePos + 1, closeBracePos)

            results.add(ConstArrayEntry(arrayName, initializer, declarationStart, declarationEnd))
            searchFrom = declarationEnd
        }

        return results
    }

    /**
     * Normalize an array initializer for comparison by stripping whitespace differences. This
     * ensures that `{0x00,0x01}`, `{ 0x00, 0x01 }`, and `{ 0x00, 0x01 }` are all equal.
     *
     * Normalization steps:
     * 1. Collapse all whitespace sequences to a single space
     * 2. Remove spaces around commas (so "0x00, 0x01" and "0x00,0x01" are equal)
     * 3. Trim surrounding whitespace
     */
    private fun normalizeInitializer(initializer: String): String {
        return initializer.replace(Regex("\\s+"), " ").replace(Regex("\\s*,\\s*"), ",").trim()
    }
}
