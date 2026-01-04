/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.internal

import java.io.File
import java.util.regex.Pattern

/** Represents a parsed error from GBDK compiler output. */
data class GbdkError(
    val file: String?,
    val line: Int?,
    val column: Int?,
    val message: String,
    val rawLine: String
)

/**
 * Parser for GBDK compiler error output.
 *
 * GBDK lcc compiler typically outputs errors in formats like:
 * - "file.c:123: error: message"
 * - "file.c:123:45: error: message"
 * - "Error: message"
 * - "warning: message"
 */
object GbdkErrorParser {

    // Pattern: filename:line:column: error/warning: message
    // Examples:
    //   "main.c:123: error: syntax error"
    //   "main.c:123:45: error: expected ';'"
    //   "main.c:123: warning: unused variable"
    private val ERROR_LINE_PATTERN =
        Pattern.compile(
            "^(.*?):(\\d+)(?::(\\d+))?\\s*:\\s*(error|warning|fatal error|note)\\s*:\\s*(.+)$",
            Pattern.CASE_INSENSITIVE
        )

    // Pattern: error/warning: message (no file/line)
    private val SIMPLE_ERROR_PATTERN =
        Pattern.compile("^(error|warning|fatal error|note)\\s*:\\s*(.+)$", Pattern.CASE_INSENSITIVE)

    /**
     * Parse compiler output and extract all errors and warnings.
     *
     * @param output The full compiler output (stdout + stderr combined)
     * @param cSourceFile The C source file being compiled (for context)
     * @return List of parsed errors
     */
    fun parseErrors(output: String, cSourceFile: File): List<GbdkError> {
        val errors = mutableListOf<GbdkError>()
        val lines = output.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Try to match error pattern with file:line:column
            val matcher = ERROR_LINE_PATTERN.matcher(trimmed)
            if (matcher.matches()) {
                val file = matcher.group(1)?.trim()
                val lineNum = matcher.group(2)?.toIntOrNull()
                val column = matcher.group(3)?.toIntOrNull()
                val severity = matcher.group(4)?.lowercase()
                val message = matcher.group(5)?.trim() ?: ""

                // Only process errors and fatal errors (skip warnings in error context)
                if (severity in listOf("error", "fatal error")) {
                    errors.add(
                        GbdkError(
                            file = file,
                            line = lineNum,
                            column = column,
                            message = message,
                            rawLine = trimmed
                        )
                    )
                }
                continue
            }

            // Try to match simple error pattern
            val simpleMatcher = SIMPLE_ERROR_PATTERN.matcher(trimmed)
            if (simpleMatcher.matches()) {
                val severity = simpleMatcher.group(1)?.lowercase()
                val message = simpleMatcher.group(2)?.trim() ?: ""

                if (severity in listOf("error", "fatal error")) {
                    errors.add(
                        GbdkError(
                            file = null,
                            line = null,
                            column = null,
                            message = message,
                            rawLine = trimmed
                        )
                    )
                }
            }
        }

        return errors
    }

    /** Check if the output contains any errors (vs just warnings or info). */
    fun hasErrors(output: String): Boolean {
        val lowerOutput = output.lowercase()
        return lowerOutput.contains("error") || lowerOutput.contains("fatal error")
    }
}
