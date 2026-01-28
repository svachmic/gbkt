/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.world

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.core.ir.IRClearFlag
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRFlagIsSet
import io.github.gbkt.core.ir.IRSetFlag
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRToggleFlag
import io.github.gbkt.core.world.GlobalFlags

// =============================================================================
// FLAGS CODE GENERATION
// =============================================================================

/**
 * Generate global flags system code.
 *
 * Creates:
 * - 32-byte flags array
 * - flag_get/set/clear/toggle helper macros
 * - Flag name constants for debugging
 */
internal fun GBDKCodeGenerator.generateFlagsSystem() {
    val flags = game.globalFlags ?: return
    if (flags.pages.isEmpty()) return

    line("// =============================================================================")
    line("// GLOBAL FLAGS SYSTEM")
    line("// =============================================================================")
    line()

    // Generate flags array (32 bytes = 256 bits)
    generateFlagsStorage(flags)

    // Generate helper macros
    generateFlagsHelpers()

    // Generate page and flag constants
    generateFlagsConstants(flags)
}

/** Generate the flags storage array. */
private fun GBDKCodeGenerator.generateFlagsStorage(flags: GlobalFlags) {
    line("// Global flags array (256 flags = 32 bytes)")
    line("static UINT8 _game_flags[32] = {0};")
    line()
}

/** Generate flag helper macros for efficient bit operations. */
private fun GBDKCodeGenerator.generateFlagsHelpers() {
    line("// Flag helper macros")
    line("#define FLAG_GET(byte_idx, bit_mask) ((_game_flags[(byte_idx)] & (bit_mask)) != 0)")
    line("#define FLAG_SET(byte_idx, bit_mask) (_game_flags[(byte_idx)] |= (bit_mask))")
    line("#define FLAG_CLEAR(byte_idx, bit_mask) (_game_flags[(byte_idx)] &= ~(bit_mask))")
    line("#define FLAG_TOGGLE(byte_idx, bit_mask) (_game_flags[(byte_idx)] ^= (bit_mask))")
    line()
}

/** Generate constants for each flag page and flag. */
private fun GBDKCodeGenerator.generateFlagsConstants(flags: GlobalFlags) {
    line("// Flag page and index constants")
    for (page in flags.pages) {
        line("// Page: ${page.name} (page ${page.pageIndex})")
        for (flag in page.flags) {
            val constName = "FLAG_${flag.name.uppercase()}"
            line("#define ${constName}_BYTE ${flag.byteOffset}u")
            line("#define ${constName}_MASK ${flag.bitMask}u")
        }
        line()
    }
}

// =============================================================================
// FLAG STATEMENT GENERATION
// =============================================================================

/**
 * Handle flags IR statements.
 *
 * @return true if this was a flags statement and was handled, false otherwise
 */
internal fun GBDKCodeGenerator.generateFlagsStatement(stmt: IRStatement): Boolean =
    when (stmt) {
        is IRSetFlag -> {
            generateSetFlag(stmt)
            true
        }
        is IRClearFlag -> {
            generateClearFlag(stmt)
            true
        }
        is IRToggleFlag -> {
            generateToggleFlag(stmt)
            true
        }
        else -> false
    }

/** Generate code to set a flag. */
private fun GBDKCodeGenerator.generateSetFlag(stmt: IRSetFlag) {
    val flag = stmt.flag
    val constName = "FLAG_${flag.name.uppercase()}"
    lineWithSource(
        "FLAG_SET(${constName}_BYTE, ${constName}_MASK);",
        stmt.sourceLocation,
        flag.name,
    )
}

/** Generate code to clear a flag. */
private fun GBDKCodeGenerator.generateClearFlag(stmt: IRClearFlag) {
    val flag = stmt.flag
    val constName = "FLAG_${flag.name.uppercase()}"
    lineWithSource(
        "FLAG_CLEAR(${constName}_BYTE, ${constName}_MASK);",
        stmt.sourceLocation,
        flag.name,
    )
}

/** Generate code to toggle a flag. */
private fun GBDKCodeGenerator.generateToggleFlag(stmt: IRToggleFlag) {
    val flag = stmt.flag
    val constName = "FLAG_${flag.name.uppercase()}"
    lineWithSource(
        "FLAG_TOGGLE(${constName}_BYTE, ${constName}_MASK);",
        stmt.sourceLocation,
        flag.name,
    )
}

// =============================================================================
// FLAG EXPRESSION GENERATION
// =============================================================================

/**
 * Generate C expression for flag queries.
 *
 * @return the C expression string, or null if not a flags expression
 */
internal fun GBDKCodeGenerator.generateFlagsExpr(expr: IRExpression): String? =
    when (expr) {
        is IRFlagIsSet -> {
            val flag = expr.flag
            val constName = "FLAG_${flag.name.uppercase()}"
            "FLAG_GET(${constName}_BYTE, ${constName}_MASK)"
        }
        else -> null
    }
