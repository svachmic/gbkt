/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.ast

// =============================================================================
// C EXPRESSION HIERARCHY
// Sealed interface for typed representation of C expressions.
// All subtypes are data classes — no mutable state.
// Exhaustive when matching is guaranteed by the sealed hierarchy.
// =============================================================================

/** Sealed type hierarchy representing C expressions used in GBDK code generation. */
sealed interface CExpr

// -----------------------------------------------------------------------------
// Literal values
// -----------------------------------------------------------------------------

/** Integer literal constant (e.g. `42`, `0xFF`). */
data class CLiteral(val value: Int) : CExpr

/** String literal constant (e.g. `"Hello"` — includes quotes in output). */
data class CStringLiteral(val value: String) : CExpr

// -----------------------------------------------------------------------------
// Variable references and operations
// -----------------------------------------------------------------------------

/** Variable reference (e.g. `score`, `player_x`). */
data class CVar(val name: String) : CExpr

/**
 * Binary operation expression (e.g. `a + b`, `x == 0`). [op] is the C operator string: "+", "-",
 * "*", "/", "%", "==", "!=", "<", "<=", ">", ">=", "&&", "||", "&", "|", "^", "<<", ">>".
 */
data class CBinaryExpr(val left: CExpr, val op: String, val right: CExpr) : CExpr

/**
 * Unary operation expression (e.g. `!flag`, `-x`, `~mask`). [op] is the C unary operator string:
 * "!", "-", "~".
 */
data class CUnaryExpr(val op: String, val operand: CExpr) : CExpr

// -----------------------------------------------------------------------------
// Function calls and control
// -----------------------------------------------------------------------------

/**
 * Function call expression (e.g. `gb_get_joypad()`, `rand()`). [function] is the function name;
 * [args] is the argument list.
 */
data class CCall(val function: String, val args: List<CExpr> = emptyList()) : CExpr

/** Ternary conditional expression (e.g. `cond ? thenExpr : elseExpr`). */
data class CTernary(val condition: CExpr, val thenExpr: CExpr, val elseExpr: CExpr) : CExpr

// -----------------------------------------------------------------------------
// Array and cast
// -----------------------------------------------------------------------------

/** Array subscript access (e.g. `arr[i]`). */
data class CArrayAccess(val array: CExpr, val index: CExpr) : CExpr

/** Explicit type cast (e.g. `(UINT8)value`). */
data class CCast(val type: CType, val expr: CExpr) : CExpr

// -----------------------------------------------------------------------------
// Escape hatch
// -----------------------------------------------------------------------------

/**
 * Raw C expression escape hatch for GBDK-specific expressions that cannot be represented by the
 * typed hierarchy (e.g. `SPRITE(player_sprite)`).
 */
data class CRawExpr(val code: String) : CExpr
