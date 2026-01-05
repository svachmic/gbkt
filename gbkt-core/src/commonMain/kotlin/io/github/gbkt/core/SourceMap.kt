/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

/**
 * Source location in Kotlin DSL code. Links generated C code back to its origin in the Kotlin DSL.
 */
data class SourceLocation(
    val file: String,
    val line: Int,
    val column: Int = 0,
    val snippet: String? = null,
) {
    companion object {
        /**
         * Capture current source location using platform-specific stack trace. Returns null if
         * location cannot be determined.
         */
        fun capture(): SourceLocation? = captureSourceLocation()
    }

    override fun toString(): String = "$file:$line" + if (column > 0) ":$column" else ""
}

/**
 * Platform-specific function to capture source location from stack trace. JVM uses
 * Thread.currentThread().stackTrace Native returns null (stack trace not readily available)
 */
expect fun captureSourceLocation(): SourceLocation?

/**
 * A single mapping from a C code line to its Kotlin source.
 *
 * @property cLine The line number in the generated C file (1-based)
 * @property kotlinFile The Kotlin source file path
 * @property kotlinLine The line number in the Kotlin file (1-based)
 * @property kotlinColumn The column number in the Kotlin file (1-based, optional)
 * @property symbol Optional symbol name (variable, function, etc.) associated with this line
 * @property snippet Optional source code snippet for context
 */
data class SourceMapping(
    val cLine: Int,
    val kotlinFile: String,
    val kotlinLine: Int,
    val kotlinColumn: Int = 0,
    val symbol: String? = null,
    val snippet: String? = null,
)

/**
 * A complete source map linking generated C code to Kotlin DSL source.
 *
 * This enables debugging by showing which Kotlin DSL line produced each line of generated C code.
 *
 * @property version Source map format version
 * @property gameName Name of the game being compiled
 * @property cFile Name of the generated C file
 * @property mappings List of line mappings
 */
data class SourceMap(
    val version: String = "1.0",
    val gameName: String,
    val cFile: String,
    val mappings: List<SourceMapping>,
) {
    /** Serialize the source map to JSON format. */
    fun toJson(): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("  \"version\": \"$version\",")
        sb.appendLine("  \"gameName\": \"${escapeJson(gameName)}\",")
        sb.appendLine("  \"cFile\": \"${escapeJson(cFile)}\",")
        sb.appendLine("  \"mappings\": [")

        mappings.forEachIndexed { index, mapping ->
            val comma = if (index < mappings.size - 1) "," else ""
            sb.append("    {")
            sb.append("\"cLine\": ${mapping.cLine}")
            sb.append(", \"kotlinFile\": \"${escapeJson(mapping.kotlinFile)}\"")
            sb.append(", \"kotlinLine\": ${mapping.kotlinLine}")
            if (mapping.kotlinColumn > 0) {
                sb.append(", \"kotlinColumn\": ${mapping.kotlinColumn}")
            }
            if (mapping.symbol != null) {
                sb.append(", \"symbol\": \"${escapeJson(mapping.symbol)}\"")
            }
            if (mapping.snippet != null) {
                sb.append(", \"snippet\": \"${escapeJson(mapping.snippet)}\"")
            }
            sb.appendLine("}$comma")
        }

        sb.appendLine("  ]")
        sb.append("}")
        return sb.toString()
    }

    /**
     * Find the Kotlin source location for a given C line number. Returns null if no mapping exists
     * for that line.
     */
    fun findKotlinLocation(cLine: Int): SourceMapping? {
        return mappings.find { it.cLine == cLine }
    }

    /** Find all C lines that map to a given Kotlin file and line. */
    fun findCLines(kotlinFile: String, kotlinLine: Int): List<SourceMapping> {
        return mappings.filter { it.kotlinFile == kotlinFile && it.kotlinLine == kotlinLine }
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

/**
 * Builder for constructing a SourceMap during code generation.
 *
 * Usage:
 * ```kotlin
 * val builder = SourceMapBuilder("MyGame", "main.c")
 * builder.addMapping(10, sourceLocation, "playerX")
 * val sourceMap = builder.build()
 * ```
 */
class SourceMapBuilder(private val gameName: String, private val cFile: String) {
    private val mappings = mutableListOf<SourceMapping>()

    /**
     * Add a mapping from a C line to a Kotlin source location.
     *
     * @param cLine The line number in the generated C file (1-based)
     * @param location The Kotlin source location (null locations are ignored)
     * @param symbol Optional symbol name for this mapping
     */
    fun addMapping(cLine: Int, location: SourceLocation?, symbol: String? = null) {
        if (location != null) {
            mappings.add(
                SourceMapping(
                    cLine = cLine,
                    kotlinFile = location.file,
                    kotlinLine = location.line,
                    kotlinColumn = location.column,
                    symbol = symbol,
                    snippet = location.snippet,
                )
            )
        }
    }

    /** Add a mapping with explicit Kotlin file and line. */
    fun addMapping(cLine: Int, kotlinFile: String, kotlinLine: Int, symbol: String? = null) {
        mappings.add(
            SourceMapping(
                cLine = cLine,
                kotlinFile = kotlinFile,
                kotlinLine = kotlinLine,
                symbol = symbol,
            )
        )
    }

    /** Get the current number of mappings. */
    val size: Int
        get() = mappings.size

    /** Build the final SourceMap. */
    fun build(): SourceMap =
        SourceMap(gameName = gameName, cFile = cFile, mappings = mappings.toList())
}
