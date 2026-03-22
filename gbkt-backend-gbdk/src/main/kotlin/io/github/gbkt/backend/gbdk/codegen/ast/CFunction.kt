/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.ast

import io.github.gbkt.backend.api.CodegenFragment

// =============================================================================
// C FUNCTION
// Typed representation of a C function definition.
// Bank assignment is a typed immutable field — no mutable currentBank state.
// =============================================================================

/** A function parameter: name and C type. */
data class CParam(val name: String, val type: CType)

/**
 * A C function definition.
 *
 * Bank assignment is modeled as a typed [bank] field rather than mutable state, which eliminates
 * the documented bank-leak bug pattern (see project MEMORY.md).
 * - [bank] = null means the function inherits the bank from its containing [CFile].
 * - [bank] set explicitly allows per-function bank overrides (rare but supported).
 * - [isBanked] = true causes the emitter to emit the `BANKED` keyword in the GBDK output.
 */
data class CFunction(
    val name: String,
    val returnType: CType,
    val params: List<CParam> = emptyList(),
    val body: List<CStatement> = emptyList(),
    /** Null means: inherit bank from containing CFile. */
    val bank: Int? = null,
    /** True → emitter adds the BANKED keyword (required for GBDK banked calling convention). */
    val isBanked: Boolean = false,
    val isStatic: Boolean = false,
    /**
     * True → emitter emits a function prototype declaration (ending in `;`) instead of a full
     * definition with a body. Used in header files and forward declarations.
     */
    val isPrototype: Boolean = false,
    /** Optional comment header for the function, e.g. "Scene: gameplay". */
    val sectionComment: String? = null,
) : CodegenFragment

/**
 * Converts a function definition into a matching forward declaration (prototype). Drops the body
 * and sets [CFunction.isPrototype] = true while preserving all other fields (name, returnType,
 * params, isBanked, sectionComment), guaranteeing the prototype always matches the definition
 * signature.
 */
fun CFunction.toPrototype(): CFunction = copy(body = emptyList(), isPrototype = true)
