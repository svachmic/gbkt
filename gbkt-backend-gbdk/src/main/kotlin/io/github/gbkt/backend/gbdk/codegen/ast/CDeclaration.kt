/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.ast

// =============================================================================
// C FILE-LEVEL DECLARATIONS
// Utility types for preprocessor directives and type aliases.
// These are not sealed (they are simple file-level constructs, not a hierarchy).
// =============================================================================

/**
 * A `#define` preprocessor directive. [value] is null for flag-style defines (e.g. `#define
 * DEBUG`).
 */
data class CDefine(val name: String, val value: String? = null)

/** A typedef declaration (e.g. `typedef unsigned char UINT8;`). */
data class CTypedef(val name: String, val definition: String)
