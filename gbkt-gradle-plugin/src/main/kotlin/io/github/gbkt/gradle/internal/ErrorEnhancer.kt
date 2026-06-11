/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.internal

import java.io.File

/** Enhanced error information with source map mapping and suggestions. */
data class EnhancedError(
    val originalError: GbdkError,
    val kotlinLocation: ParsedSourceMapping?,
    val suggestion: String?,
    val irNodeType: String? = null,
)

/** Enhances GBDK compiler errors with source map mappings and suggestions. */
object ErrorEnhancer {

    /** Common error patterns and their suggestions. */
    private val ERROR_SUGGESTIONS =
        mapOf(
            // Syntax errors
            Regex("expected.*[';']", RegexOption.IGNORE_CASE) to
                "Missing semicolon? Check for typos in the statement.",
            Regex("expected.*[')']", RegexOption.IGNORE_CASE) to
                "Missing closing parenthesis? Check function calls and expressions.",
            Regex("expected.*['{']", RegexOption.IGNORE_CASE) to
                "Missing opening brace? Check function or block definitions.",
            Regex("expected.*['}']", RegexOption.IGNORE_CASE) to
                "Missing closing brace? Check for unclosed blocks.",
            Regex("syntax error", RegexOption.IGNORE_CASE) to
                "Syntax error detected. Check for typos, missing operators, or incorrect syntax.",

            // Undefined symbols
            Regex("undefined reference", RegexOption.IGNORE_CASE) to
                "Function or variable not found. Did you declare it? Check spelling.",
            Regex("'[^']+' undeclared", RegexOption.IGNORE_CASE) to
                "Undeclared identifier. Did you mean to declare this variable or import it?",

            // Type errors
            Regex("incompatible.*type", RegexOption.IGNORE_CASE) to
                "Type mismatch. Check variable types match their usage.",
            Regex("invalid.*type", RegexOption.IGNORE_CASE) to
                "Invalid type usage. Verify the type is correct for this operation.",

            // Common typos in DSL
            Regex("\\b(player|sprite|scene|variable)\\b", RegexOption.IGNORE_CASE) to
                "Did you use the correct DSL function name? Check the DSL reference.",
            Regex("\\b(x|y|width|height)\\b.*not found", RegexOption.IGNORE_CASE) to
                "Property not found. Did you initialize the sprite/scene properly?",
        )

    /**
     * Enhance a list of GBDK errors with source map mappings and suggestions. Supports a single
     * source map (single-file path) for backward compatibility.
     *
     * @param errors List of parsed GBDK errors
     * @param sourceMap Optional source map for mapping C lines to Kotlin
     * @return List of enhanced errors
     */
    fun enhanceErrors(errors: List<GbdkError>, sourceMap: ParsedSourceMap?): List<EnhancedError> {
        val sourceMapsByFile =
            if (sourceMap != null) mapOf(sourceMap.cFile to sourceMap) else emptyMap()
        return enhanceErrorsMultiFile(errors, sourceMapsByFile)
    }

    /**
     * Enhance a list of GBDK errors with multi-file source map mappings and suggestions.
     *
     * @param errors List of parsed GBDK errors
     * @param sourceMaps Map of C file name to ParsedSourceMap
     * @return List of enhanced errors
     */
    fun enhanceErrorsMultiFile(
        errors: List<GbdkError>,
        sourceMaps: Map<String, ParsedSourceMap>,
    ): List<EnhancedError> {
        return errors.map { error ->
            // Look up the source map for this error's C file
            val sourceMap =
                if (error.file != null) {
                    val fileName = File(error.file).name
                    sourceMaps[fileName] ?: sourceMaps.values.firstOrNull()
                } else {
                    sourceMaps.values.firstOrNull()
                }

            val kotlinLocation =
                if (error.line != null && sourceMap != null) {
                    sourceMap.findKotlinLocation(error.line)
                } else {
                    null
                }

            val suggestion = generateSuggestion(error.message, error.line, kotlinLocation)

            EnhancedError(
                originalError = error,
                kotlinLocation = kotlinLocation,
                suggestion = suggestion,
                irNodeType = kotlinLocation?.irNodeType,
            )
        }
    }

    /** Generate a suggestion for an error message. */
    private fun generateSuggestion(
        errorMessage: String,
        cLine: Int?,
        kotlinLocation: ParsedSourceMapping?,
    ): String? {
        val suggestions = mutableListOf<String>()

        // Check for common error patterns
        for ((pattern, suggestion) in ERROR_SUGGESTIONS) {
            if (pattern.containsMatchIn(errorMessage)) {
                suggestions.add(suggestion)
                break // Use first matching pattern
            }
        }

        // If we have a Kotlin location, add context
        if (kotlinLocation != null) {
            val locationHint =
                "Error originates from Kotlin code at ${kotlinLocation.kotlinFile}:${kotlinLocation.kotlinLine}"
            if (kotlinLocation.snippet != null) {
                suggestions.add("$locationHint\nCode: ${kotlinLocation.snippet}")
            } else {
                suggestions.add(locationHint)
            }
        }

        return if (suggestions.isEmpty()) null else suggestions.joinToString("\n")
    }

    /**
     * Format enhanced errors into a Rust-style error message, grouped by DSL line.
     *
     * Mapped errors show both C and Kotlin locations. Unmapped errors show "(no DSL mapping --
     * generated scaffolding)".
     */
    fun formatEnhancedErrors(enhancedErrors: List<EnhancedError>, cSourceFile: File): String {
        val sb = StringBuilder()
        sb.appendLine("GBDK compilation failed with ${enhancedErrors.size} error(s):")
        sb.appendLine()

        // Group errors by DSL location key
        val grouped = enhancedErrors.groupBy { enhanced ->
            val loc = enhanced.kotlinLocation
            if (loc != null) "${loc.kotlinFile}:${loc.kotlinLine}" else "(unmapped)"
        }

        // Render mapped groups first, then unmapped
        val mappedGroups = grouped.filter { (key, _) -> key != "(unmapped)" }
        val unmappedGroup = grouped["(unmapped)"]

        var errorIndex = 1

        for ((dslKey, groupErrors) in mappedGroups) {
            for (enhanced in groupErrors) {
                renderMappedError(sb, errorIndex, enhanced)
                errorIndex++
            }
        }

        if (unmappedGroup != null) {
            for (enhanced in unmappedGroup) {
                renderUnmappedError(sb, errorIndex, enhanced)
                errorIndex++
            }
        }

        sb.appendLine("Generated C file: ${cSourceFile.absolutePath}")

        return sb.toString()
    }

    private fun renderMappedError(sb: StringBuilder, index: Int, enhanced: EnhancedError) {
        val error = enhanced.originalError
        val loc = enhanced.kotlinLocation!!

        sb.appendLine("Error $index:")

        // C location line
        val cLocation = buildString {
            if (error.file != null) append(error.file)
            if (error.line != null) {
                if (error.file != null) append(":")
                append(error.line)
                if (error.column != null) append(":${error.column}")
            }
        }
        if (cLocation.isNotEmpty()) {
            sb.appendLine("  C code: $cLocation")
        }
        sb.appendLine("  Message: '${error.message}'")
        sb.appendLine()

        // Rust-style pointer
        sb.appendLine("  --> ${loc.kotlinFile}:${loc.kotlinLine}")
        val contextLabel = enhanced.irNodeType ?: loc.symbol ?: "DSL expression"
        sb.appendLine("  |  $contextLabel")
        sb.appendLine("  ^  ${error.message}")
        sb.appendLine()

        // Suggestion
        if (enhanced.suggestion != null) {
            sb.appendLine("  Suggestion: ${enhanced.suggestion}")
            sb.appendLine()
        }
    }

    private fun renderUnmappedError(sb: StringBuilder, index: Int, enhanced: EnhancedError) {
        val error = enhanced.originalError

        sb.appendLine("Error $index:")

        val cLocation = buildString {
            if (error.file != null) append(error.file)
            if (error.line != null) {
                if (error.file != null) append(":")
                append(error.line)
                if (error.column != null) append(":${error.column}")
            }
        }
        if (cLocation.isNotEmpty()) {
            sb.appendLine("  C code: $cLocation")
        }
        sb.appendLine("  Message: '${error.message}'")
        sb.appendLine("  (no DSL mapping -- generated scaffolding)")
        sb.appendLine()

        if (enhanced.suggestion != null) {
            sb.appendLine("  Suggestion: ${enhanced.suggestion}")
            sb.appendLine()
        }
    }
}
