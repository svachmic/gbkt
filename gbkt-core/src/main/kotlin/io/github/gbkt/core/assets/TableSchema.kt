/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("CyclomaticComplexMethod") // JSON parser has many branches for value types

package io.github.gbkt.core.assets

import io.github.gbkt.core.ir.ColumnType
import java.io.File

/**
 * JSON schema definition for balance data tables.
 *
 * Schema files are sidecar `.schema.json` files that define validation rules for CSV tables. They
 * support:
 * - Column type constraints (uint8_t, uint16_t, etc.)
 * - Min/max value ranges
 * - Enum value restrictions
 * - Reference validation (e.g., column references another table)
 * - Composite column grouping
 *
 * ## Example Schema
 *
 * ```json
 * {
 *   "name": "game_balance",
 *   "description": "Balance data for all game entities",
 *   "columns": {
 *     "exp_by_level": {
 *       "type": "uint16_t",
 *       "min": 0,
 *       "max": 65535,
 *       "description": "Experience required for each level"
 *     },
 *     "monster_hp_c": {
 *       "type": "uint8_t",
 *       "min": 1,
 *       "max": 255,
 *       "description": "Monster HP (common tier)"
 *     }
 *   },
 *   "composites": {
 *     "monster_hp": {
 *       "suffixes": ["_c", "_b", "_a", "_s"],
 *       "description": "HP by monster tier"
 *     }
 *   }
 * }
 * ```
 *
 * @see TablesParser
 */
data class TableSchema(
    /** Schema name/identifier. */
    val name: String,
    /** Human-readable description. */
    val description: String = "",
    /** Column definitions by name. */
    val columns: Map<String, ColumnSchema> = emptyMap(),
    /** Composite definitions by base name. */
    val composites: Map<String, CompositeSchema> = emptyMap(),
    /** ROM bank to assign (if specified). */
    val bank: Int? = null,
) {
    /** Get the schema for a specific column. */
    fun column(name: String): ColumnSchema? = columns[name]

    /** Get the composite schema for a base name. */
    fun composite(baseName: String): CompositeSchema? = composites[baseName]

    /**
     * Validate a value against a column's schema.
     *
     * @return List of validation errors (empty if valid)
     */
    fun validateValue(columnName: String, value: Int): List<String> {
        val schema = columns[columnName] ?: return emptyList()
        return schema.validate(columnName, value)
    }

    companion object {
        /**
         * Load a schema from a JSON file.
         *
         * @param file The .schema.json file
         * @return The parsed schema
         */
        fun load(file: File): TableSchema {
            return TableSchemaParser.parse(file.readText())
        }

        /**
         * Load a schema for a table file (looks for .schema.json sidecar).
         *
         * @param tableFile The .csv table file
         * @return The schema if found, null otherwise
         */
        fun forTable(tableFile: File): TableSchema? {
            val schemaFile =
                File(tableFile.parentFile, "${tableFile.nameWithoutExtension}.schema.json")
            return if (schemaFile.exists()) load(schemaFile) else null
        }

        /**
         * Generate a schema from an existing table.
         *
         * @param tableFile The .csv table file
         * @return Generated schema with inferred types
         */
        fun generateFromTable(tableFile: File): TableSchema {
            return TableSchemaGenerator.generate(tableFile)
        }
    }
}

/** Schema for a single column. */
data class ColumnSchema(
    /** Column data type. */
    val type: ColumnType,
    /** Minimum allowed value (inclusive). */
    val min: Int? = null,
    /** Maximum allowed value (inclusive). */
    val max: Int? = null,
    /** Allowed enum values (if restricted). */
    val enumValues: List<Int>? = null,
    /** Reference to another column/table. */
    val reference: String? = null,
    /** Human-readable description. */
    val description: String = "",
) {
    /**
     * Validate a value against this column's constraints.
     *
     * @return List of validation errors (empty if valid)
     */
    fun validate(columnName: String, value: Int): List<String> {
        val errors = mutableListOf<String>()

        // Type range check
        val typeMax =
            when (type) {
                ColumnType.UINT8 -> 255
                ColumnType.UINT16 -> 65535
                ColumnType.INT8 -> 127
                ColumnType.INT16 -> 32767
            }
        val typeMin =
            when (type) {
                ColumnType.UINT8 -> 0
                ColumnType.UINT16 -> 0
                ColumnType.INT8 -> -128
                ColumnType.INT16 -> -32768
            }

        if (value < typeMin || value > typeMax) {
            errors.add("$columnName: value $value out of ${type.cType} range ($typeMin..$typeMax)")
        }

        // Custom range check
        if (min != null && value < min) {
            errors.add("$columnName: value $value below minimum $min")
        }
        if (max != null && value > max) {
            errors.add("$columnName: value $value exceeds maximum $max")
        }

        // Enum check
        if (enumValues != null && value !in enumValues) {
            errors.add("$columnName: value $value not in allowed values $enumValues")
        }

        return errors
    }
}

/** Schema for composite columns (tiered data). */
data class CompositeSchema(
    /** Column suffixes (e.g., ["_c", "_b", "_a", "_s"]). */
    val suffixes: List<String>,
    /** Human-readable description. */
    val description: String = "",
)

/** Parser for .schema.json files. */
internal object TableSchemaParser {

    /**
     * Parse JSON content into a TableSchema.
     *
     * Note: This is a simple JSON parser. For production, consider using kotlinx.serialization.
     */
    fun parse(json: String): TableSchema {
        val obj = parseJsonObject(json)

        val name = obj["name"] as? String ?: "unnamed"
        val description = obj["description"] as? String ?: ""
        val bank = (obj["bank"] as? Number)?.toInt()

        val columns = mutableMapOf<String, ColumnSchema>()
        val columnsObj = obj["columns"] as? Map<*, *> ?: emptyMap<String, Any>()
        for ((key, value) in columnsObj) {
            if (key is String && value is Map<*, *>) {
                columns[key] = parseColumnSchema(value)
            }
        }

        val composites = mutableMapOf<String, CompositeSchema>()
        val compositesObj = obj["composites"] as? Map<*, *> ?: emptyMap<String, Any>()
        for ((key, value) in compositesObj) {
            if (key is String && value is Map<*, *>) {
                composites[key] = parseCompositeSchema(value)
            }
        }

        return TableSchema(name, description, columns, composites, bank)
    }

    private fun parseColumnSchema(obj: Map<*, *>): ColumnSchema {
        val typeStr = obj["type"] as? String ?: "uint8_t"
        val type =
            when (typeStr.lowercase()) {
                "uint8_t",
                "uint8",
                "u8" -> ColumnType.UINT8
                "uint16_t",
                "uint16",
                "u16" -> ColumnType.UINT16
                "int8_t",
                "int8",
                "i8" -> ColumnType.INT8
                "int16_t",
                "int16",
                "i16" -> ColumnType.INT16
                else -> ColumnType.UINT8
            }

        val min = (obj["min"] as? Number)?.toInt()
        val max = (obj["max"] as? Number)?.toInt()
        val enumValues = (obj["enum"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }
        val reference = obj["reference"] as? String
        val description = obj["description"] as? String ?: ""

        return ColumnSchema(type, min, max, enumValues, reference, description)
    }

    private fun parseCompositeSchema(obj: Map<*, *>): CompositeSchema {
        val suffixes =
            (obj["suffixes"] as? List<*>)?.mapNotNull { it as? String }
                ?: listOf("_c", "_b", "_a", "_s")
        val description = obj["description"] as? String ?: ""
        return CompositeSchema(suffixes, description)
    }

    /**
     * Simple JSON object parser.
     *
     * This is a minimal implementation for schema files. For complex JSON, use
     * kotlinx.serialization.
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseJsonObject(json: String): Map<String, Any?> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return emptyMap()
        }

        val result = mutableMapOf<String, Any?>()
        val content = trimmed.drop(1).dropLast(1).trim()
        if (content.isEmpty()) return result

        var depth = 0
        var inString = false
        var escaped = false
        val currentPair = StringBuilder()

        for (char in content) {
            when {
                escaped -> {
                    currentPair.append(char)
                    escaped = false
                }
                char == '\\' -> {
                    currentPair.append(char)
                    escaped = true
                }
                char == '"' -> {
                    currentPair.append(char)
                    inString = !inString
                }
                !inString && (char == '{' || char == '[') -> {
                    currentPair.append(char)
                    depth++
                }
                !inString && (char == '}' || char == ']') -> {
                    currentPair.append(char)
                    depth--
                }
                !inString && char == ',' && depth == 0 -> {
                    parsePair(currentPair.toString(), result)
                    currentPair.clear()
                }
                else -> currentPair.append(char)
            }
        }

        if (currentPair.isNotBlank()) {
            parsePair(currentPair.toString(), result)
        }

        return result
    }

    private fun parsePair(pair: String, result: MutableMap<String, Any?>) {
        val colonIndex = pair.indexOf(':')
        if (colonIndex < 0) return

        val keyPart = pair.substring(0, colonIndex).trim()
        val valuePart = pair.substring(colonIndex + 1).trim()

        val key =
            if (keyPart.startsWith("\"") && keyPart.endsWith("\"")) {
                keyPart.drop(1).dropLast(1)
            } else {
                keyPart
            }

        result[key] = parseValue(valuePart)
    }

    private fun parseValue(value: String): Any? {
        val trimmed = value.trim()
        return when {
            trimmed == "null" -> null
            trimmed == "true" -> true
            trimmed == "false" -> false
            trimmed.startsWith("\"") && trimmed.endsWith("\"") -> {
                trimmed.drop(1).dropLast(1).replace("\\\"", "\"").replace("\\n", "\n")
            }
            trimmed.startsWith("{") -> parseJsonObject(trimmed)
            trimmed.startsWith("[") -> parseJsonArray(trimmed)
            trimmed.contains('.') -> trimmed.toDoubleOrNull()
            else -> trimmed.toIntOrNull() ?: trimmed
        }
    }

    private fun parseJsonArray(json: String): List<Any?> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return emptyList()
        }

        val content = trimmed.drop(1).dropLast(1).trim()
        if (content.isEmpty()) return emptyList()

        val result = mutableListOf<Any?>()
        var depth = 0
        var inString = false
        var escaped = false
        val currentItem = StringBuilder()

        for (char in content) {
            when {
                escaped -> {
                    currentItem.append(char)
                    escaped = false
                }
                char == '\\' -> {
                    currentItem.append(char)
                    escaped = true
                }
                char == '"' -> {
                    currentItem.append(char)
                    inString = !inString
                }
                !inString && (char == '{' || char == '[') -> {
                    currentItem.append(char)
                    depth++
                }
                !inString && (char == '}' || char == ']') -> {
                    currentItem.append(char)
                    depth--
                }
                !inString && char == ',' && depth == 0 -> {
                    result.add(parseValue(currentItem.toString()))
                    currentItem.clear()
                }
                else -> currentItem.append(char)
            }
        }

        if (currentItem.isNotBlank()) {
            result.add(parseValue(currentItem.toString()))
        }

        return result
    }
}

/** Generator to create schemas from existing tables. */
internal object TableSchemaGenerator {

    /**
     * Generate a schema from an existing CSV table file.
     *
     * Infers types from the header row and data ranges.
     */
    fun generate(tableFile: File): TableSchema {
        val lines = tableFile.readLines()
        if (lines.size < 3) {
            return TableSchema(tableFile.nameWithoutExtension)
        }

        val names = lines[0].split(",").map { it.trim() }
        val types = lines[1].split(",").map { parseType(it.trim()) }
        val dataRows = lines.drop(2).filter { it.isNotBlank() }

        val columns = mutableMapOf<String, ColumnSchema>()
        val compositeBasenames = mutableSetOf<String>()

        for (i in names.indices) {
            val name = names[i]
            val type = types.getOrElse(i) { ColumnType.UINT8 }

            // Analyze data to find actual min/max
            val values =
                dataRows.mapNotNull { row ->
                    val cells = row.split(",")
                    cells.getOrNull(i)?.trim()?.toIntOrNull()
                }

            val min = values.minOrNull()
            val max = values.maxOrNull()

            columns[name] = ColumnSchema(type, min, max)

            // Detect composite base names
            for (suffix in listOf("_c", "_b", "_a", "_s")) {
                if (name.endsWith(suffix)) {
                    compositeBasenames.add(name.dropLast(suffix.length))
                }
            }
        }

        // Build composite schemas
        val composites = mutableMapOf<String, CompositeSchema>()
        for (baseName in compositeBasenames) {
            // Verify all suffixes exist
            val allSuffixes = listOf("_c", "_b", "_a", "_s")
            val existingSuffixes = allSuffixes.filter { columns.containsKey("$baseName$it") }
            if (existingSuffixes.size == allSuffixes.size) {
                composites[baseName] = CompositeSchema(allSuffixes)
            }
        }

        return TableSchema(
            name = tableFile.nameWithoutExtension,
            description = "Auto-generated schema from ${tableFile.name}",
            columns = columns,
            composites = composites,
        )
    }

    private fun parseType(typeStr: String): ColumnType {
        return when (typeStr.lowercase()) {
            "uint8_t" -> ColumnType.UINT8
            "uint16_t" -> ColumnType.UINT16
            "int8_t" -> ColumnType.INT8
            "int16_t" -> ColumnType.INT16
            else -> ColumnType.UINT8
        }
    }

    /** Generate JSON schema content from a TableSchema. */
    fun toJson(schema: TableSchema): String {
        return buildString {
            appendLine("{")
            appendLine("  \"name\": \"${schema.name}\",")
            appendLine("  \"description\": \"${schema.description}\",")

            // Columns
            appendLine("  \"columns\": {")
            val columnEntries = schema.columns.entries.toList()
            for ((index, entry) in columnEntries.withIndex()) {
                val (name, col) = entry
                append("    \"$name\": { \"type\": \"${col.type.cType}\"")
                if (col.min != null) append(", \"min\": ${col.min}")
                if (col.max != null) append(", \"max\": ${col.max}")
                if (col.description.isNotEmpty())
                    append(", \"description\": \"${col.description}\"")
                append(" }")
                if (index < columnEntries.size - 1) appendLine(",") else appendLine()
            }
            appendLine("  },")

            // Composites
            appendLine("  \"composites\": {")
            val compositeEntries = schema.composites.entries.toList()
            for ((index, entry) in compositeEntries.withIndex()) {
                val (name, comp) = entry
                val suffixes = comp.suffixes.joinToString(", ") { "\"$it\"" }
                append("    \"$name\": { \"suffixes\": [$suffixes]")
                if (comp.description.isNotEmpty())
                    append(", \"description\": \"${comp.description}\"")
                append(" }")
                if (index < compositeEntries.size - 1) appendLine(",") else appendLine()
            }
            appendLine("  }")

            appendLine("}")
        }
    }
}
