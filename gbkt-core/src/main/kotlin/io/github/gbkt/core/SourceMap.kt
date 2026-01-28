/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

/**
 * A location in source code for sourcemap generation.
 *
 * This is the pure data type used by IR nodes to track their origin in Kotlin DSL code.
 *
 * @property file The source file path
 * @property line The line number (1-based)
 * @property column The column number (1-based, 0 means unspecified)
 * @property snippet Optional source code snippet for context
 */
data class SourceLocation(
    val file: String,
    val line: Int,
    val column: Int = 0,
    val snippet: String? = null,
) {
    override fun toString(): String = "$file:$line" + if (column > 0) ":$column" else ""
}

/**
 * Capture source location from stack trace.
 *
 * Finds the first frame outside gbkt.core package that's in user code.
 */
@Suppress("LoopWithTooManyJumpStatements") // Stack trace filtering requires multiple skips
fun captureSourceLocation(): SourceLocation? {
    val stackTrace = Thread.currentThread().stackTrace

    // Find the first frame outside gbkt.core package that's in user code
    // Skip frames from:
    // - java.lang.Thread (getStackTrace)
    // - gbkt.core package (our DSL code)
    for (frame in stackTrace) {
        val className = frame.className

        // Skip JVM internal frames
        if (className.startsWith("java.") || className.startsWith("jdk.")) {
            continue
        }

        // Skip gbkt.core package (DSL implementation)
        if (className.startsWith("gbkt.core.")) {
            continue
        }

        // Skip Kotlin internal frames
        if (className.startsWith("kotlin.")) {
            continue
        }

        // Found user code frame
        val fileName = frame.fileName ?: return null
        val lineNumber = frame.lineNumber

        if (lineNumber <= 0) {
            continue
        }

        return SourceLocation(file = fileName, line = lineNumber)
    }

    return null
}

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
