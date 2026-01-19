/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

/** Type of data in a balance table column. */
enum class ColumnType(val cType: String, val size: Int) {
    UINT8("uint8_t", 1),
    UINT16("uint16_t", 2),
    INT8("int8_t", 1),
    INT16("int16_t", 2),
}

/**
 * A single column of balance table data.
 *
 * @property name The column name (e.g., "exp_required", "monster_hp_c")
 * @property type The data type for this column
 * @property values The values for each level (index 0 = level 1, etc.)
 */
data class BalanceColumn(val name: String, val type: ColumnType, val values: List<Int>) {
    /** Size in bytes of this column's data array. */
    val sizeBytes: Int
        get() = values.size * type.size
}

/**
 * A composite table that groups related columns (e.g., monster_hp_c, monster_hp_b, monster_hp_a,
 * monster_hp_s). Generates a 2D array in C: `const uint8_t monster_hp[4][99] = { ... }`
 *
 * @property baseName The base name (e.g., "monster_hp")
 * @property suffixes The suffixes for each tier (e.g., ["_c", "_b", "_a", "_s"])
 * @property columns The columns that make up this composite
 */
data class CompositeTable(
    val baseName: String,
    val suffixes: List<String>,
    val columns: List<BalanceColumn>,
) {
    /** Size in bytes of this composite table. */
    val sizeBytes: Int
        get() = columns.sumOf { it.sizeBytes }

    /** Number of rows (levels). */
    val rowCount: Int
        get() = columns.firstOrNull()?.values?.size ?: 0

    /** The C type for elements (all columns must have same type). */
    val elementType: ColumnType
        get() = columns.first().type
}

/**
 * Collection of balance tables for game data.
 *
 * @property bank The ROM bank for this balance data
 * @property columns Individual columns (for simple 1D arrays)
 * @property composites Composite tables (for 2D arrays)
 */
data class BalanceTable(
    val bank: Int = 5, // Default to bank 5 like original
    val columns: List<BalanceColumn> = emptyList(),
    val composites: List<CompositeTable> = emptyList(),
) {
    /** Total size of all balance data in bytes. */
    val totalSizeBytes: Int
        get() = columns.sumOf { it.sizeBytes } + composites.sumOf { it.sizeBytes }

    /** Number of levels in the table. */
    val levelCount: Int
        get() = columns.firstOrNull()?.values?.size ?: composites.firstOrNull()?.rowCount ?: 0

    /** Get a column by name. */
    fun column(name: String): BalanceColumn? = columns.find { it.name == name }

    /** Get a composite by base name. */
    fun composite(baseName: String): CompositeTable? = composites.find { it.baseName == baseName }
}
