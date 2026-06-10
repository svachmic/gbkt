/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.api

/** Result of code generation. */
data class GenerationResult(
    /** Whether generation succeeded. */
    val success: Boolean,

    /** Generated files mapped by relative path. */
    val files: Map<String, GeneratedFile> = emptyMap(),

    /** Source map for error mapping (optional). */
    val sourceMap: SourceMap? = null,

    /** Error message if generation failed. */
    val error: String? = null,
) {
    /** Total size of all generated files in bytes. */
    val totalSize: Long
        get() = files.values.sumOf { it.content.length.toLong() }

    /** Get the main source file (if there is one). */
    val mainFile: GeneratedFile?
        get() = files["main.c"] ?: files.values.firstOrNull()

    companion object {
        /** Create a successful result with a single file. */
        fun single(path: String, content: String, sourceMap: SourceMap? = null) =
            GenerationResult(
                success = true,
                files = mapOf(path to GeneratedFile(path, content)),
                sourceMap = sourceMap,
            )

        /** Create a failed result. */
        fun failed(error: String) = GenerationResult(success = false, error = error)
    }
}

/** A generated file with its content. */
data class GeneratedFile(
    /** Relative path for the file. */
    val path: String,

    /** File content. */
    val content: String,

    /** Optional description of the file's purpose. */
    val description: String? = null,

    /**
     * v2 source map JSON for this file — populated by [GBDKPipeline] when running the v2 pipeline.
     * Null for v1 games and for header files (game.h) which carry no DSL statements.
     */
    val sourceMapJson: String? = null,
)

/** Source map for mapping generated code locations back to DSL source. */
data class SourceMap(
    /** Mapping from generated line to source location. */
    val mappings: Map<Int, SourceLocation>
) {
    /** Look up the source location for a generated line. */
    fun lookup(generatedLine: Int): SourceLocation? = mappings[generatedLine]

    companion object {
        /** Empty source map. */
        val EMPTY = SourceMap(emptyMap())
    }
}

/** A location in DSL source code. */
data class SourceLocation(
    /** Source file path. */
    val file: String,

    /** Line number (1-based). */
    val line: Int,

    /** Optional column (1-based). */
    val column: Int? = null,
) {
    override fun toString() = if (column != null) "$file:$line:$column" else "$file:$line"
}
