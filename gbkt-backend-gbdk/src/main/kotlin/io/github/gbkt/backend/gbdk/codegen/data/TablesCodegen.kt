/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.data

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.core.ir.BalanceColumn
import io.github.gbkt.core.ir.BalanceTable
import io.github.gbkt.core.ir.ColumnType
import io.github.gbkt.core.ir.CompositeTable

// =============================================================================
// BALANCE TABLE CODE GENERATION
// Generates banked C arrays for game balance data
// =============================================================================

/**
 * Generate C code for balance tables with bank switching.
 *
 * Outputs:
 * - `#pragma bank=N` directive for the table bank
 * - `const type name[count] = { ... };` for simple columns
 * - `const type name[tiers][levels] = { { ... }, ... };` for composites
 *
 * @param balanceTable The balance table to generate code for
 */
internal fun GBDKCodeGenerator.generateBalanceTables(balanceTable: BalanceTable?) {
    if (balanceTable == null) return
    if (balanceTable.columns.isEmpty() && balanceTable.composites.isEmpty()) return

    line("// =============================================================================")
    line("// BALANCE TABLE DATA (${balanceTable.totalSizeBytes} bytes total)")
    line("// =============================================================================")
    line()

    // Switch to the balance table bank
    setBank(balanceTable.bank)
    line("// Bank ${balanceTable.bank} balance data")
    line()

    // Generate level count constant
    line("#define BALANCE_LEVEL_COUNT ${balanceTable.levelCount}")
    line()

    // Generate simple columns (1D arrays)
    if (balanceTable.columns.isNotEmpty()) {
        line("// --- Simple balance columns ---")
        for (column in balanceTable.columns) {
            generateColumn(column)
        }
        line()
    }

    // Generate composite tables (2D arrays)
    if (balanceTable.composites.isNotEmpty()) {
        line("// --- Composite balance tables ---")
        for (composite in balanceTable.composites) {
            generateComposite(composite)
        }
        line()
    }

    // Return to home bank
    returnToHome()
}

/** Generate a simple 1D array for a balance column. */
private fun GBDKCodeGenerator.generateColumn(column: BalanceColumn) {
    val cType = column.type.cType
    val count = column.values.size

    line("// ${column.name}: ${column.sizeBytes} bytes")
    line("const $cType ${column.name}[$count] = {")
    indent++

    // Output values in rows of 10 for readability
    for (rowStart in column.values.indices step 10) {
        val rowEnd = minOf(rowStart + 10, column.values.size)
        val rowValues = column.values.subList(rowStart, rowEnd)
        val formatted = rowValues.joinToString(", ") { formatValue(it, column.type) }
        val comma = if (rowEnd < column.values.size) "," else ""
        line("$formatted$comma  // levels ${rowStart + 1}-$rowEnd")
    }

    indent--
    line("};")
    line()
}

/**
 * Generate a 2D array for a composite table. Output format: `const type name[tiers][levels] = {
 * {...}, {...}, {...}, {...} };`
 */
private fun GBDKCodeGenerator.generateComposite(composite: CompositeTable) {
    val cType = composite.elementType.cType
    val tiers = composite.columns.size
    val levels = composite.rowCount

    line("// ${composite.baseName}: ${composite.sizeBytes} bytes (${tiers}x$levels)")
    line("const $cType ${composite.baseName}[$tiers][$levels] = {")
    indent++

    for ((tierIndex, column) in composite.columns.withIndex()) {
        val suffix = composite.suffixes[tierIndex]
        line("// Tier $suffix (${column.name})")
        line("{")
        indent++

        // Output values in rows of 10 for readability
        for (rowStart in column.values.indices step 10) {
            val rowEnd = minOf(rowStart + 10, column.values.size)
            val rowValues = column.values.subList(rowStart, rowEnd)
            val formatted = rowValues.joinToString(", ") { formatValue(it, column.type) }
            val comma = if (rowEnd < column.values.size) "," else ""
            line("$formatted$comma  // levels ${rowStart + 1}-$rowEnd")
        }

        indent--
        val tierComma = if (tierIndex < composite.columns.size - 1) "," else ""
        line("}$tierComma")
    }

    indent--
    line("};")
    line()
}

/** Format a value for C output based on its type. */
private fun formatValue(value: Int, type: ColumnType): String {
    return when (type) {
        ColumnType.UINT8,
        ColumnType.UINT16 -> "${value}u"
        ColumnType.INT8,
        ColumnType.INT16 -> "$value"
    }
}

/**
 * Generate accessor macros for balance tables. These provide a clean API for accessing balance data
 * at runtime.
 */
internal fun GBDKCodeGenerator.generateBalanceAccessors(balanceTable: BalanceTable?) {
    if (balanceTable == null) return
    if (balanceTable.columns.isEmpty() && balanceTable.composites.isEmpty()) return

    line("// --- Balance table accessor macros ---")

    // Generate macros for simple columns
    for (column in balanceTable.columns) {
        val name = column.name.uppercase()
        line("#define GET_$name(level) (${column.name}[(level) - 1])")
    }

    // Generate macros for composite tables
    for (composite in balanceTable.composites) {
        val name = composite.baseName.uppercase()
        line("#define GET_$name(tier, level) (${composite.baseName}[(tier)][(level) - 1])")
    }
    line()
}
