/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

// =============================================================================
// FUZZY MATCHING AND SUGGESTIONS
// =============================================================================

/**
 * Utility object for fuzzy string matching and "Did you mean?" suggestions. Used by the validation
 * layer to provide helpful error messages.
 */
object Suggestions {

    /**
     * Calculates the Levenshtein (edit) distance between two strings. This is the minimum number of
     * single-character edits (insertions, deletions, or substitutions) required to change one
     * string into the other.
     *
     * @param a First string
     * @param b Second string
     * @return The edit distance between the strings
     */
    fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        // Use lowercase for case-insensitive matching
        val aLower = a.lowercase()
        val bLower = b.lowercase()

        // Create two work vectors of integer distances
        var v0 = IntArray(bLower.length + 1) { it }
        var v1 = IntArray(bLower.length + 1)

        for (i in aLower.indices) {
            // First element of v1 is A[0..i+1] -> empty string distance
            v1[0] = i + 1

            for (j in bLower.indices) {
                val deletionCost = v0[j + 1] + 1
                val insertionCost = v1[j] + 1
                val substitutionCost = if (aLower[i] == bLower[j]) v0[j] else v0[j] + 1

                v1[j + 1] = minOf(deletionCost, insertionCost, substitutionCost)
            }

            // Swap v0 and v1
            val temp = v0
            v0 = v1
            v1 = temp
        }

        return v0[bLower.length]
    }

    /**
     * Finds candidates from a collection that are within the specified edit distance from the input
     * string, sorted by distance (closest first).
     *
     * @param input The input string to match against
     * @param candidates Collection of candidate strings to search
     * @param maxDistance Maximum edit distance to consider (default: 2)
     * @return List of matching candidates sorted by distance, then alphabetically
     */
    fun findSuggestions(
        input: String,
        candidates: Collection<String>,
        maxDistance: Int = 2,
    ): List<String> {
        if (input.isEmpty() || candidates.isEmpty()) return emptyList()

        // Calculate distance for each candidate and filter by maxDistance
        return candidates
            .map { candidate -> candidate to levenshteinDistance(input, candidate) }
            .filter { (_, distance) -> distance <= maxDistance && distance > 0 }
            .sortedWith(compareBy({ it.second }, { it.first }))
            .map { it.first }
    }

    /**
     * Formats a suggestion message for a single best match.
     *
     * @param input The input string that was not found
     * @param candidates Collection of valid candidates
     * @param maxDistance Maximum edit distance to consider (default: 2)
     * @return A formatted suggestion like "Did you mean 'X'?" or empty string if no match
     */
    fun formatSuggestion(
        input: String,
        candidates: Collection<String>,
        maxDistance: Int = 2,
    ): String {
        val suggestions = findSuggestions(input, candidates, maxDistance)
        return when {
            suggestions.isEmpty() -> ""
            suggestions.size == 1 -> " Did you mean '${suggestions[0]}'?"
            else -> " Did you mean '${suggestions[0]}' or '${suggestions[1]}'?"
        }
    }

    /**
     * Formats a suggestion message showing multiple alternatives.
     *
     * @param input The input string that was not found
     * @param candidates Collection of valid candidates
     * @param maxSuggestions Maximum number of suggestions to show (default: 3)
     * @param maxDistance Maximum edit distance to consider (default: 2)
     * @return A formatted suggestion or empty string if no match
     */
    fun formatMultipleSuggestions(
        input: String,
        candidates: Collection<String>,
        maxSuggestions: Int = 3,
        maxDistance: Int = 2,
    ): String {
        val suggestions = findSuggestions(input, candidates, maxDistance).take(maxSuggestions)
        return when {
            suggestions.isEmpty() -> ""
            suggestions.size == 1 -> " Did you mean '${suggestions[0]}'?"
            else -> " Did you mean one of: ${suggestions.joinToString(", ") { "'$it'" }}?"
        }
    }
}

/** Extension function to get suggestions for a string against a collection. */
fun String.suggestFrom(candidates: Collection<String>, maxDistance: Int = 2): List<String> =
    Suggestions.findSuggestions(this, candidates, maxDistance)

/** Extension function to format a suggestion message. */
fun String.formatSuggestionFrom(candidates: Collection<String>, maxDistance: Int = 2): String =
    Suggestions.formatSuggestion(this, candidates, maxDistance)
