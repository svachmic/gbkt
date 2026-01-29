/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("CyclomaticComplexMethod") // CSV parser has many branches for validation

package io.github.gbkt.core.assets

import io.github.gbkt.core.ir.BalanceColumn
import io.github.gbkt.core.ir.BalanceTable
import io.github.gbkt.core.ir.ColumnType
import io.github.gbkt.core.ir.CompositeTable
import java.io.File

/**
 * Parser for tables.csv files that contain balance data.
 *
 * The format is a CSV file with:
 * - Row 1: Column names (e.g., "exp_by_level", "monster_hp_c", "monster_hp_b", ...)
 * - Row 2: Column types (e.g., "uint16_t", "uint8_t", ...)
 * - Rows 3+: Data values for each level (level 1 = row 3, etc.)
 *
 * Columns with suffixes _c, _b, _a, _s are grouped into composite tables.
 *
 * ## Schema Validation
 *
 * Tables can have an optional `.schema.json` sidecar file for extended validation:
 * - Min/max value constraints beyond type limits
 * - Enum value restrictions
 * - Reference validation to other tables
 *
 * @see TableSchema
 */
object TablesParser {
    /** Suffix pattern for tiered columns. */
    private val tierSuffixes = listOf("_c", "_b", "_a", "_s")

    /**
     * Parse a tables.csv file and return a BalanceTable.
     *
     * @param file The tables.csv file to parse
     * @param bank The ROM bank to assign to this table (default: 5)
     * @return The parsed BalanceTable
     */
    fun parse(file: File, bank: Int = 5): BalanceTable {
        val lines = file.readLines()
        return parse(lines, bank)
    }

    /**
     * Parse tables.csv content and return a BalanceTable.
     *
     * @param lines The lines from the CSV file
     * @param bank The ROM bank to assign to this table
     * @return The parsed BalanceTable
     */
    fun parse(lines: List<String>, bank: Int = 5): BalanceTable {
        if (lines.size < 3) {
            return BalanceTable(bank) // Empty table
        }

        // Parse header row (column names)
        val names = lines[0].split(",").map { it.trim() }

        // Parse type row
        val types = lines[1].split(",").map { parseColumnType(it.trim()) }

        // Parse data rows
        val dataRows = lines.drop(2).filter { it.isNotBlank() }

        // Build columns
        val columns = mutableListOf<BalanceColumn>()
        for (i in names.indices) {
            val values =
                dataRows.map { row ->
                    val cells = row.split(",")
                    if (i < cells.size) {
                        cells[i].trim().toIntOrNull() ?: 0
                    } else {
                        0
                    }
                }
            columns.add(BalanceColumn(names[i], types.getOrElse(i) { ColumnType.UINT8 }, values))
        }

        // Group tiered columns into composites
        val (composites, simpleColumns) = groupComposites(columns)

        return BalanceTable(bank, simpleColumns, composites)
    }

    /** Parse a column type string into a ColumnType. */
    private fun parseColumnType(typeStr: String): ColumnType {
        return when (typeStr.lowercase()) {
            "uint8_t" -> ColumnType.UINT8
            "uint16_t" -> ColumnType.UINT16
            "int8_t" -> ColumnType.INT8
            "int16_t" -> ColumnType.INT16
            else -> ColumnType.UINT8 // Default
        }
    }

    /**
     * Group columns with _c, _b, _a, _s suffixes into composite tables.
     *
     * @return Pair of (composites, remaining simple columns)
     */
    private fun groupComposites(
        columns: List<BalanceColumn>
    ): Pair<List<CompositeTable>, List<BalanceColumn>> {
        val composites = mutableListOf<CompositeTable>()
        val usedIndices = mutableSetOf<Int>()

        // Find base names that have all tier suffixes
        val columnsByBaseName = mutableMapOf<String, MutableList<Pair<Int, BalanceColumn>>>()

        for ((index, column) in columns.withIndex()) {
            for (suffix in tierSuffixes) {
                if (column.name.endsWith(suffix)) {
                    val baseName = column.name.dropLast(suffix.length)
                    columnsByBaseName.getOrPut(baseName) { mutableListOf() }.add(index to column)
                    break
                }
            }
        }

        // Create composites for base names that have all 4 suffixes
        for ((baseName, cols) in columnsByBaseName.mapValues { it.value.toList() }) {
            if (cols.size == 4) {
                // Sort by suffix order
                val sorted =
                    cols.sortedBy { (_, col) ->
                        tierSuffixes.indexOfFirst { col.name.endsWith(it) }
                    }

                // Verify all columns have same type and value count
                val firstType = sorted.first().second.type
                val firstCount = sorted.first().second.values.size
                if (
                    sorted.all {
                        it.second.type == firstType && it.second.values.size == firstCount
                    }
                ) {
                    composites.add(
                        CompositeTable(
                            baseName = baseName,
                            suffixes = tierSuffixes,
                            columns = sorted.map { it.second },
                        )
                    )
                    usedIndices.addAll(sorted.map { it.first })
                }
            }
        }

        // Remaining simple columns
        val simpleColumns = columns.filterIndexed { index, _ -> index !in usedIndices }

        return composites to simpleColumns
    }

    /**
     * Parse and validate a tables.csv file.
     *
     * @param file The tables.csv file to parse
     * @param bank The ROM bank to assign
     * @return Pair of (BalanceTable, list of warning messages)
     */
    fun parseWithValidation(file: File, bank: Int = 5): Pair<BalanceTable, List<String>> {
        val table = parse(file, bank)
        val warnings = mutableListOf<String>()

        // Check for value overflow
        for (column in table.columns) {
            val maxValue =
                when (column.type) {
                    ColumnType.UINT8 -> 255
                    ColumnType.UINT16 -> 65535
                    ColumnType.INT8 -> 127
                    ColumnType.INT16 -> 32767
                }
            val overflows = column.values.count { it > maxValue }
            if (overflows > 0) {
                warnings.add(
                    "Column '${column.name}' has $overflows values exceeding ${column.type.cType} max"
                )
            }
        }

        // Check bank size
        if (table.totalSizeBytes > BANK_SIZE) {
            warnings.add("Balance table exceeds 16KB limit: ${table.totalSizeBytes} bytes")
        }

        return table to warnings
    }

    /**
     * Parse and validate a tables.csv file using a schema file.
     *
     * This provides enhanced validation beyond type constraints:
     * - Custom min/max ranges
     * - Enum value restrictions
     * - Reference validation
     *
     * @param file The tables.csv file to parse
     * @param schemaFile The .schema.json file (optional, auto-detected if null)
     * @param bank The ROM bank to assign
     * @return Triple of (BalanceTable, list of errors, list of warnings)
     */
    fun parseWithSchema(
        file: File,
        schemaFile: File? = null,
        bank: Int = 5,
    ): Triple<BalanceTable, List<String>, List<String>> {
        val table = parse(file, bank)
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Load schema (auto-detect sidecar if not provided)
        val schema =
            if (schemaFile != null && schemaFile.exists()) {
                TableSchema.load(schemaFile)
            } else {
                TableSchema.forTable(file)
            }

        if (schema == null) {
            // No schema, fall back to basic validation
            val (_, basicWarnings) = parseWithValidation(file, bank)
            return Triple(table, emptyList(), basicWarnings)
        }

        // Validate each column against schema (both simple and composite columns)
        val allColumns = table.columns + table.composites.flatMap { it.columns }

        for (column in allColumns) {
            val columnSchema = schema.column(column.name)

            for ((rowIndex, value) in column.values.withIndex()) {
                val lineNumber = rowIndex + 3 // Header = 1, Types = 2, Data starts at 3

                if (columnSchema != null) {
                    // Validate against schema
                    val columnErrors = columnSchema.validate(column.name, value)
                    for (error in columnErrors) {
                        errors.add("Line $lineNumber: $error")
                    }
                } else {
                    // Basic type validation for columns not in schema
                    val maxValue =
                        when (column.type) {
                            ColumnType.UINT8 -> 255
                            ColumnType.UINT16 -> 65535
                            ColumnType.INT8 -> 127
                            ColumnType.INT16 -> 32767
                        }
                    val minValue =
                        when (column.type) {
                            ColumnType.UINT8,
                            ColumnType.UINT16 -> 0
                            ColumnType.INT8 -> -128
                            ColumnType.INT16 -> -32768
                        }
                    if (value < minValue || value > maxValue) {
                        warnings.add(
                            "Line $lineNumber: ${column.name} value $value out of ${column.type.cType} range"
                        )
                    }
                }
            }
        }

        // Check bank size
        if (table.totalSizeBytes > BANK_SIZE) {
            warnings.add("Balance table exceeds 16KB limit: ${table.totalSizeBytes} bytes")
        }

        // Warn about columns in schema but not in table (check both simple and composite columns)
        val allColumnNames =
            table.columns.map { it.name } +
                table.composites.flatMap { comp -> comp.columns.map { it.name } }
        for (schemaColumn in schema.columns.keys) {
            if (schemaColumn !in allColumnNames) {
                warnings.add("Schema column '$schemaColumn' not found in table")
            }
        }

        return Triple(table, errors, warnings)
    }

    /**
     * Validate a table file against its schema without parsing into IR.
     *
     * Useful for quick validation in editors.
     *
     * @param file The tables.csv file
     * @return List of validation errors
     */
    fun validateOnly(file: File): List<String> {
        val (_, errors, warnings) = parseWithSchema(file)
        return errors + warnings
    }

    private const val BANK_SIZE = 16384 // 16 KB per bank
}
