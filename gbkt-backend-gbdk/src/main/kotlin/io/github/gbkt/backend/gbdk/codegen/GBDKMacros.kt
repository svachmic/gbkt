/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen

import io.github.gbkt.backend.gbdk.codegen.ast.CRawCode

/**
 * GBDK preprocessor macros that have no typed C AST representation.
 *
 * These macros expand to lvalue writes (e.g. `ENABLE_RAM` writes to the MBC register), so they
 * cannot be modeled as [io.github.gbkt.backend.gbdk.codegen.ast.CCall] — they are the sanctioned
 * [CRawCode] exception. Visitors MUST vend them through this object instead of constructing
 * `CRawCode("...")` literals inline, so each macro spelling has exactly one source of truth.
 */
object GBDKMacros {
    /** `ENABLE_RAM;` — activates SRAM for MBC cartridges (macro expands to lvalue write). */
    fun enableRam(): CRawCode = CRawCode("ENABLE_RAM;")

    /** `DISABLE_RAM;` — deactivates SRAM (always the last statement of save/load functions). */
    fun disableRam(): CRawCode = CRawCode("DISABLE_RAM;")

    /** `SHOW_WIN;` — makes the GBDK window layer visible (dialogs, menus, window-layer HUDs). */
    fun showWin(): CRawCode = CRawCode("SHOW_WIN;")

    /** `HIDE_WIN;` — hides the GBDK window layer on dialog/menu dismiss. */
    fun hideWin(): CRawCode = CRawCode("HIDE_WIN;")
}
