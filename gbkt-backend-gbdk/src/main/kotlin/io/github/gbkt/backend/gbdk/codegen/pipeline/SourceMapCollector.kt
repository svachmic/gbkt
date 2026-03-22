/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.SourceLocation

/**
 * Accumulates C-line to Kotlin source location mappings during code emission.
 *
 * One collector per C file; passed to CEmitter.emit() calls so that as each statement is emitted,
 * the emitter can record which C output line corresponds to which DSL source location.
 *
 * After emission is complete, [mappings] contains the ordered list of all recorded entries. This
 * collector is consumed by the source map serializer in Plan 02 to write the `.gbkt.map` file.
 *
 * Usage:
 * ```kotlin
 * val collector = SourceMapCollector()
 * emitter.emit(cFile, collector)
 * val mappings = collector.mappings
 * ```
 */
class SourceMapCollector {

    /**
     * A single mapping from a C output line number to a Kotlin DSL source location.
     *
     * @property cLine 1-based line number in the generated C file.
     * @property sourceLocation The DSL source location that produced this C line.
     * @property irNodeType Optional IR node type name for diagnostics (e.g. "Assign", "IfOp").
     * @property symbol Optional symbol name (variable, function) associated with this line.
     */
    data class Mapping(
        val cLine: Int,
        val sourceLocation: SourceLocation,
        val irNodeType: String? = null,
        val symbol: String? = null,
    )

    private val _mappings = mutableListOf<Mapping>()

    /** The ordered list of collected mappings. Immutable view over internal state. */
    val mappings: List<Mapping>
        get() = _mappings

    /**
     * Record a mapping from a C output line to a DSL source location.
     *
     * No-op when [sourceLocation] is null — structural C lines (comments, blank lines, etc.) that
     * have no corresponding DSL source are silently skipped.
     *
     * @param cLine 1-based line number in the generated C output file.
     * @param sourceLocation The DSL source location, or null to skip recording.
     * @param irNodeType Optional IR node type for diagnostics.
     * @param symbol Optional symbol name for this mapping entry.
     */
    fun record(
        cLine: Int,
        sourceLocation: SourceLocation?,
        irNodeType: String? = null,
        symbol: String? = null,
    ) {
        if (sourceLocation != null) {
            _mappings += Mapping(cLine, sourceLocation, irNodeType, symbol)
        }
    }
}
