/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.ast

import io.github.gbkt.backend.api.CodegenFragment
import io.github.gbkt.core.ir.SourceLocation

// =============================================================================
// C STATEMENT HIERARCHY
// Sealed interface for typed representation of C statements.
// All subtypes are data classes or data objects — no mutable state.
// Exhaustive when matching is guaranteed by the sealed hierarchy.
// =============================================================================

/**
 * Sealed type hierarchy representing C statements used in GBDK code generation.
 *
 * The optional [sourceLocation] property links a generated C statement back to the DSL Kotlin
 * source line that produced it. Used by
 * [io.github.gbkt.backend.gbdk.codegen.pipeline.SourceMapCollector] to build source maps during
 * code emission.
 *
 * Structural-only nodes (CBlankLine, CComment, CSwitch, CSwitchCase) default to null.
 */
sealed interface CStatement {
    /** DSL source location that produced this statement, or null if unknown/structural. */
    val sourceLocation: SourceLocation?
        get() = null
}

// -----------------------------------------------------------------------------
// Control flow
// -----------------------------------------------------------------------------

/** If/else conditional statement. [elseBody] is empty when there is no else branch. */
data class CIf(
    val condition: CExpr,
    val thenBody: List<CStatement>,
    val elseBody: List<CStatement> = emptyList(),
    override val sourceLocation: SourceLocation? = null,
) : CStatement

/** C for loop (classic three-part form). Each part is optional to support `for(;;)` variants. */
data class CFor(
    val init: CStatement? = null,
    val condition: CExpr? = null,
    val increment: CExpr? = null,
    val body: List<CStatement> = emptyList(),
    override val sourceLocation: SourceLocation? = null,
) : CStatement

/** C while loop. */
data class CWhile(
    val condition: CExpr,
    val body: List<CStatement> = emptyList(),
    override val sourceLocation: SourceLocation? = null,
) : CStatement

/**
 * C switch statement with a list of cases. See [CSwitchCase] for individual case representation.
 */
data class CSwitch(val expr: CExpr, val cases: List<CSwitchCase> = emptyList()) : CStatement

/** A single case (or default) in a switch statement. [value] is null for the `default:` case. */
data class CSwitchCase(val value: CExpr?, val body: List<CStatement> = emptyList())

// -----------------------------------------------------------------------------
// Return and block
// -----------------------------------------------------------------------------

/** Return statement. [value] is null for `return;` (void functions). */
data class CReturn(val value: CExpr? = null, override val sourceLocation: SourceLocation? = null) :
    CStatement

/** Scoped block statement — a `{ ... }` container. */
data class CBlock(
    val statements: List<CStatement> = emptyList(),
    override val sourceLocation: SourceLocation? = null,
) : CStatement

// -----------------------------------------------------------------------------
// Declarations and expressions
// -----------------------------------------------------------------------------

/** Local or file-level variable declaration. Example: `static UINT8 score = 0;` */
data class CVarDecl(
    val name: String,
    val type: CType,
    val initializer: CExpr? = null,
    val isStatic: Boolean = false,
    val isConst: Boolean = false,
    /**
     * True → emit `extern` storage class for header declarations shared across translation units.
     */
    val isExtern: Boolean = false,
    override val sourceLocation: SourceLocation? = null,
) : CStatement, CodegenFragment

/** Expression used as a statement (e.g. a function call with its result discarded). */
data class CExprStatement(val expr: CExpr, override val sourceLocation: SourceLocation? = null) :
    CStatement

// -----------------------------------------------------------------------------
// Escape hatch and formatting
// -----------------------------------------------------------------------------

/**
 * Raw C code escape hatch for GBDK-specific statements that cannot be represented by the typed
 * hierarchy (e.g. `SWITCH_ROM(1);`).
 */
data class CRawCode(val code: String, override val sourceLocation: SourceLocation? = null) :
    CStatement

/** Comment statement — rendered as `/* text */` or `// text` in the emitter. */
data class CComment(val text: String) : CStatement

/** Blank line — rendered as an empty line in the output for readability. */
data object CBlankLine : CStatement

/** Break statement — exits the innermost switch/loop. */
data object CBreak : CStatement

/** Continue statement — skips to the next iteration of the innermost loop. */
data object CContinue : CStatement
