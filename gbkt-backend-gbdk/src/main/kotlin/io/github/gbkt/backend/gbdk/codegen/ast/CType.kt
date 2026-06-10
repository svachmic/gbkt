/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.ast

// =============================================================================
// C TYPE HIERARCHY
// Sealed interface for typed representation of C types.
// Maps VarType enum values from ir.Types to C-level types.
// No mutable state — all fields are val.
// =============================================================================

/** Sealed type hierarchy representing C types used in GBDK code generation. */
sealed interface CType

// -----------------------------------------------------------------------------
// Primitive types — correspond to VarType enum values in ir.Types
// -----------------------------------------------------------------------------

/** Unsigned 8-bit integer (UINT8 / UBYTE in GBDK). */
data object CU8 : CType

/** Unsigned 16-bit integer (UINT16 / UWORD in GBDK). */
data object CU16 : CType

/** Signed 8-bit integer (INT8 / BYTE in GBDK). */
data object CI8 : CType

/** Signed 16-bit integer (INT16 / WORD in GBDK). */
data object CI16 : CType

/** Void type — used for functions with no return value. */
data object CVoid : CType

// -----------------------------------------------------------------------------
// Compound types
// -----------------------------------------------------------------------------

/** Pointer to another type (e.g. `UINT8*`). */
data class CPointer(val pointedType: CType) : CType

/** Array of a type, with optional fixed size (null = unbounded / `[]`). */
data class CArray(val elementType: CType, val size: Int? = null) : CType

/** Const-qualified wrapper around another type (e.g. `const UINT8`). */
data class CConst(val inner: CType) : CType
