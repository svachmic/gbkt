/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.ast

// =============================================================================
// C FILE
// Top-level typed representation of a generated C source file.
// Bank assignment is a typed immutable field — no mutable currentBank state.
// =============================================================================

/**
 * A complete generated C source file.
 *
 * The [bank] field replaces the mutable `var currentBank = 0` pattern that caused bank-state-leak
 * bugs in the previous string-based code generator. Each generated file now carries its bank
 * assignment as a typed immutable value.
 * - [bank] = 0 → main.c (HOME bank, always-loaded code)
 * - [bank] = N → bankN.c (switchable ROM bank)
 */
data class CFile(
    /** File name, e.g. "main.c" or "bank1.c". */
    val name: String,
    /** ROM bank number for this file. 0 = HOME bank (main.c). */
    val bank: Int = 0,
    /** #include directives — listed in order. */
    val includes: List<String> = emptyList(),
    /** #define preprocessor macros. */
    val defines: List<CDefine> = emptyList(),
    /** typedef declarations. */
    val typedefs: List<CTypedef> = emptyList(),
    /** File-level (global) variable declarations. */
    val variables: List<CVarDecl> = emptyList(),
    /**
     * Raw C source blocks emitted at file scope after [variables] and before [functions].
     *
     * Used for collection data declarations and any other file-scope code that cannot be
     * represented as typed AST nodes (e.g. multi-array collection patterns from
     * [GBDKCollectionCodegen]).
     */
    val rawSections: List<String> = emptyList(),
    /** Function definitions in this file. */
    val functions: List<CFunction> = emptyList(),
    /**
     * True → emitter wraps the file content in `#ifndef {guardName}` / `#define {guardName}` /
     * `#endif` include guards. The guard name is derived from [name] (uppercased,
     * dots→underscores).
     */
    val isHeader: Boolean = false,
)
