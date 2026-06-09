/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.postprocess

// =============================================================================
// FUNCTION DEDUPLICATION PASS
// Scans generated C text for functions with identical bodies and deduplicates.
//
// Strategy:
//   - Extract all function definitions using brace-depth tracking
//   - Normalize each function body for comparison (strip name, normalize whitespace)
//   - Group functions with identical normalized bodies
//   - For each duplicate group: keep the first as canonical, replace others with
//     a comment marker and redirect all call sites to the canonical function
//
// Conservative approach: only dedup exact matches after normalization.
// Functions with different signatures (param types/counts) are NOT deduped.
// Functions referenced via function pointers are NOT deduped (safe: call-site
// rewriting only rewrites `name(` patterns, pointer assignment is separate).
// =============================================================================

/**
 * Result of running FunctionDeduplicationPass on a C text string.
 *
 * @property optimizedContent The C text with duplicate functions removed and call sites redirected.
 * @property functionsDeduped The number of duplicate functions that were removed.
 * @property details Human-readable descriptions of each deduplication.
 * @property redirects Map of removed function name → canonical name it was replaced by. Used by
 *   [COutputOptimizer] to rewrite call sites in other files (e.g. bank1.c referencing a function
 *   deduplicated in main.c).
 */
data class FunctionDeduplicationResult(
    val optimizedContent: String,
    val functionsDeduped: Int,
    val details: List<String>,
    val redirects: Map<String, String> = emptyMap(),
)

/**
 * Post-processing pass that deduplicates identical function bodies in generated C text.
 *
 * Handles:
 * - void functions and value-returning functions
 * - Functions with BANKED keyword (GBDK bank-switching convention)
 * - Functions with various parameter types
 */
object FunctionDeduplicationPass {

    // Matches a function definition signature line.
    // Groups: (1) full signature before opening brace, (2) function name
    // Handles BANKED keyword and various return types including pointers.
    // Examples:
    //   void my_func() {
    //   UINT8 compute_something(UINT8 x, UINT8 y) BANKED {
    //   void init_scene_01() {
    private val FUNC_DEF_PATTERN =
        Regex(
            """^((?:const\s+)?[\w*]+(?:\s+[\w*]+)*\s+(\w+)\s*\([^)]*\)(?:\s+BANKED)?)\s*\{""",
            setOf(RegexOption.MULTILINE),
        )

    /**
     * Optimize the given C text by deduplicating functions with identical bodies.
     *
     * @param cContent The raw C source text to process.
     * @return A [FunctionDeduplicationResult] with optimized text and stats.
     */
    fun optimize(cContent: String): FunctionDeduplicationResult {
        if (cContent.isBlank()) {
            return FunctionDeduplicationResult(cContent, 0, emptyList())
        }

        val functions = extractFunctions(cContent)

        if (functions.isEmpty()) {
            return FunctionDeduplicationResult(cContent, 0, emptyList())
        }

        // Group by normalized body (excluding function name) AND normalized signature
        // (excluding function name) to avoid deduping functions with different signatures
        val byNormalizedBodyAndSig = mutableMapOf<String, MutableList<FunctionEntry>>()
        for (func in functions) {
            val key = buildDeduplicationKey(func)
            byNormalizedBodyAndSig.getOrPut(key) { mutableListOf() }.add(func)
        }

        val duplicateGroups = byNormalizedBodyAndSig.values.filter { it.size > 1 }

        if (duplicateGroups.isEmpty()) {
            return FunctionDeduplicationResult(cContent, 0, emptyList())
        }

        // Build a map of duplicate name -> canonical name for call-site rewriting
        val callSiteRedirects = mutableMapOf<String, String>()
        val details = mutableListOf<String>()

        data class Replacement(val start: Int, val end: Int, val replacement: String)
        val replacements = mutableListOf<Replacement>()

        for (group in duplicateGroups) {
            val canonical = group.first()
            val duplicates = group.drop(1)
            for (dup in duplicates) {
                val dedupComment = "/* Deduplicated: see ${canonical.name} */"
                replacements.add(
                    Replacement(dup.declarationStart, dup.declarationEnd, dedupComment)
                )
                callSiteRedirects[dup.name] = canonical.name
                details.add(
                    "Replaced duplicate function '${dup.name}' with alias to '${canonical.name}'"
                )
            }
        }

        // Sort replacements from end to start so positions remain valid
        replacements.sortByDescending { it.start }

        var result = cContent
        for (rep in replacements) {
            result = result.substring(0, rep.start) + rep.replacement + result.substring(rep.end)
        }

        // Rewrite call sites: replace `duplicateName(` with `canonicalName(`
        for ((dupName, canonicalName) in callSiteRedirects) {
            // Use word boundary to avoid matching partial names (e.g., my_func vs my_func2)
            val callPattern = Regex("""\b${Regex.escape(dupName)}\s*\(""")
            // Process line-by-line and skip comment lines.
            //
            // The regex `\b{name}\s*\(` matches comment text like
            // "// Trampoline: title_enter (bank 1)" because `\s*\(` matches " (" before "bank".
            // The rewriter has no semantic awareness of comments — skipping lines whose
            // trimmed start begins with a comment marker is the smallest correct fix per
            // SEED-015 root cause analysis in
            // .planning/phases/11.1-banks-port-surplus-codegen-defects-inserted-terminal/11.1-RESEARCH.md
            // §Pattern 4.
            result =
                result.lines().joinToString("\n") { line ->
                    val trimmed = line.trimStart()
                    if (
                        trimmed.startsWith("//") ||
                            trimmed.startsWith("/*") ||
                            trimmed.startsWith("*")
                    ) {
                        line
                    } else {
                        callPattern.replace(line) { m -> m.value.replace(dupName, canonicalName) }
                    }
                }
        }

        return FunctionDeduplicationResult(result, replacements.size, details, callSiteRedirects)
    }

    /** Represents a parsed function definition from C text. */
    private data class FunctionEntry(
        val name: String,
        val signature: String, // Full signature before {, with name removed for comparison
        val body: String, // Content between outermost braces
        val declarationStart: Int,
        val declarationEnd: Int,
    )

    /** Extract all function definitions from the C text using brace-depth tracking. */
    private fun extractFunctions(cContent: String): List<FunctionEntry> {
        val results = mutableListOf<FunctionEntry>()

        var searchFrom = 0
        while (searchFrom < cContent.length) {
            val match = FUNC_DEF_PATTERN.find(cContent, searchFrom) ?: break

            val declarationStart = match.range.first
            val funcName = match.groupValues[2]
            val signature = match.groupValues[1]

            // Find the opening brace of the function body
            val openBracePos = cContent.indexOf('{', match.range.last)
            if (openBracePos == -1) {
                searchFrom = match.range.last + 1
                continue
            }

            // Track brace depth to find the closing brace
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

            val declarationEnd = closeBracePos + 1
            val body = cContent.substring(openBracePos + 1, closeBracePos)

            results.add(FunctionEntry(funcName, signature, body, declarationStart, declarationEnd))
            searchFrom = declarationEnd
        }

        return results
    }

    /**
     * Build a deduplication key for a function entry.
     *
     * The key combines:
     * 1. Normalized signature with function name removed (so functions with same params/return type
     *    match, but different param types/counts don't)
     * 2. Normalized body with function name occurrences removed
     *
     * This ensures we only dedup functions that are truly equivalent: same return type, same
     * parameter types, same body logic.
     */
    private fun buildDeduplicationKey(func: FunctionEntry): String {
        // Normalize signature: remove function name, normalize whitespace
        val normalizedSig =
            func.signature.replace(func.name, "__FUNC__").replace(Regex("\\s+"), " ").trim()

        // Normalize body: remove whitespace differences and occurrences of function name
        val normalizedBody =
            func.body.replace(func.name, "__FUNC__").replace(Regex("\\s+"), " ").trim()

        return "$normalizedSig|$normalizedBody"
    }
}
