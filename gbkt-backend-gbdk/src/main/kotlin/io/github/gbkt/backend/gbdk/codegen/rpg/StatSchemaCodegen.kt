/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.rpg

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.core.rpg.StatSchema
import io.github.gbkt.core.rpg.StatStorageType

// =============================================================================
// CONFIGURABLE STAT SCHEMA CODE GENERATION
// =============================================================================

/**
 * Generate configurable stat schema system.
 *
 * Creates:
 * - Schema index constants
 * - Stat index constants per schema
 * - Storage type information
 * - Max value and default value tables
 * - Stat read/write functions
 */
internal fun GBDKCodeGenerator.generateStatSchemaSystem() {
    val schemas = game.statSchemas
    if (schemas.isEmpty()) return

    line("// =============================================================================")
    line("// CONFIGURABLE STAT SCHEMA SYSTEM")
    line("// =============================================================================")
    line()

    // Generate storage type constants
    generateStatStorageTypeConstants()

    // Generate schema index constants
    generateSchemaIndexConstants(schemas)

    // Generate stat index constants for each schema
    for (schema in schemas) {
        generateStatIndexConstants(schema)
    }

    // Generate stat metadata tables
    generateStatMetadataTables(schemas)

    // Generate stat storage arrays per schema
    for (schema in schemas) {
        generateSchemaStatStorage(schema)
    }

    // Generate stat access functions
    generateStatAccessFunctions(schemas)

    // Generate schema helper functions
    generateSchemaHelperFunctions(schemas)
}

/** Generate storage type constants. */
private fun GBDKCodeGenerator.generateStatStorageTypeConstants() {
    line("// Stat storage type constants")
    for ((index, type) in StatStorageType.entries.withIndex()) {
        line("#define STAT_STORAGE_${type.name} ${index}u")
    }
    line()
}

/** Generate schema index constants. */
private fun GBDKCodeGenerator.generateSchemaIndexConstants(schemas: List<StatSchema>) {
    line("// Schema index constants")
    for ((index, schema) in schemas.withIndex()) {
        line("#define SCHEMA_${schema.id.uppercase()} ${index}u")
    }
    line("#define SCHEMA_COUNT ${schemas.size}u")
    line()

    // Find and mark default schema
    val defaultSchema = schemas.find { it.isDefault } ?: schemas.firstOrNull()
    if (defaultSchema != null) {
        line("#define DEFAULT_STAT_SCHEMA SCHEMA_${defaultSchema.id.uppercase()}")
        line()
    }
}

/** Generate stat index constants for a schema. */
private fun GBDKCodeGenerator.generateStatIndexConstants(schema: StatSchema) {
    val prefix = schema.id.uppercase()
    line("// Stat indices for schema: ${schema.id}")
    for ((index, stat) in schema.stats.withIndex()) {
        line("#define ${prefix}_STAT_${stat.id.uppercase()} ${index}u")
    }
    line("#define ${prefix}_STAT_COUNT ${schema.stats.size}u")
    line()
}

/** Generate stat metadata tables (storage types, max values, defaults). */
private fun GBDKCodeGenerator.generateStatMetadataTables(schemas: List<StatSchema>) {
    line("// =============================================================================")
    line("// STAT METADATA TABLES")
    line("// =============================================================================")
    line()

    for (schema in schemas) {
        val prefix = schema.id.lowercase()

        // Storage types table
        line("// Storage types for ${schema.id}")
        line("static const UINT8 _${prefix}_stat_storage[${prefix.uppercase()}_STAT_COUNT] = {")
        indent++
        line(schema.stats.joinToString(", ") { "STAT_STORAGE_${it.storage.name}" })
        indent--
        line("};")
        line()

        // Max values table (UINT16 to accommodate any max)
        line("// Max values for ${schema.id}")
        line("static const UINT16 _${prefix}_stat_max[${prefix.uppercase()}_STAT_COUNT] = {")
        indent++
        line(schema.stats.joinToString(", ") { "${it.defaultMax}u" })
        indent--
        line("};")
        line()

        // Default values table
        line("// Default values for ${schema.id}")
        line("static const UINT16 _${prefix}_stat_default[${prefix.uppercase()}_STAT_COUNT] = {")
        indent++
        line(schema.stats.joinToString(", ") { "${it.defaultValue}u" })
        indent--
        line("};")
        line()

        // Display names (if needed for UI)
        line("// Display names for ${schema.id}")
        line("static const char* const _${prefix}_stat_names[${prefix.uppercase()}_STAT_COUNT] = {")
        indent++
        line(schema.stats.joinToString(", ") { "\"${it.displayName}\"" })
        indent--
        line("};")
        line()
    }
}

/** Generate stat storage arrays for each schema. */
private fun GBDKCodeGenerator.generateSchemaStatStorage(schema: StatSchema) {
    val prefix = schema.id.lowercase()

    line("// =============================================================================")
    line("// STAT STORAGE FOR SCHEMA: ${schema.id.uppercase()}")
    line("// =============================================================================")
    line()

    // Generate current value storage
    // We need separate arrays for 8-bit and 16-bit stats
    val u8Stats =
        schema.stats.filter { it.storage in listOf(StatStorageType.UINT8, StatStorageType.INT8) }
    val u16Stats =
        schema.stats.filter { it.storage in listOf(StatStorageType.UINT16, StatStorageType.INT16) }

    if (u8Stats.isNotEmpty() || u16Stats.isNotEmpty()) {
        // For simplicity, use a union approach where we store all as UINT16
        // This wastes some memory but simplifies access
        line("// Current stat values for ${schema.id} (as UINT16 for uniform access)")
        line("static UINT16 _${prefix}_stat_current[${prefix.uppercase()}_STAT_COUNT];")
        line()

        // Generate initialization function
        line("// Initialize stats to default values")
        line("static void _${prefix}_init_stats(void) {")
        indent++
        for ((index, stat) in schema.stats.withIndex()) {
            line("_${prefix}_stat_current[$index] = ${stat.defaultValue}u; // ${stat.id}")
        }
        indent--
        line("}")
        line()
    }
}

/** Generate stat access functions. */
private fun GBDKCodeGenerator.generateStatAccessFunctions(schemas: List<StatSchema>) {
    line("// =============================================================================")
    line("// STAT ACCESS FUNCTIONS")
    line("// =============================================================================")
    line()

    for (schema in schemas) {
        val prefix = schema.id.lowercase()
        val prefixUpper = schema.id.uppercase()

        // Get stat value
        line("// Get stat value for ${schema.id}")
        line("static UINT16 _${prefix}_get_stat(UINT8 stat_idx) {")
        indent++
        line("if (stat_idx >= ${prefixUpper}_STAT_COUNT) return 0u;")
        line("return _${prefix}_stat_current[stat_idx];")
        indent--
        line("}")
        line()

        // Set stat value with clamping
        line("// Set stat value for ${schema.id} (clamped to max)")
        line("static void _${prefix}_set_stat(UINT8 stat_idx, UINT16 value) {")
        indent++
        line("if (stat_idx >= ${prefixUpper}_STAT_COUNT) return;")
        line("UINT16 max_val = _${prefix}_stat_max[stat_idx];")
        line("_${prefix}_stat_current[stat_idx] = (value > max_val) ? max_val : value;")
        indent--
        line("}")
        line()

        // Modify stat value (add/subtract)
        line("// Modify stat value for ${schema.id}")
        line("static void _${prefix}_modify_stat(UINT8 stat_idx, INT16 delta) {")
        indent++
        line("if (stat_idx >= ${prefixUpper}_STAT_COUNT) return;")
        line("INT32 new_val = (INT32)_${prefix}_stat_current[stat_idx] + delta;")
        line("if (new_val < 0) new_val = 0;")
        line("UINT16 max_val = _${prefix}_stat_max[stat_idx];")
        line("_${prefix}_stat_current[stat_idx] = (new_val > max_val) ? max_val : (UINT16)new_val;")
        indent--
        line("}")
        line()

        // Get stat max
        line("// Get stat max value for ${schema.id}")
        line("static UINT16 _${prefix}_get_stat_max(UINT8 stat_idx) {")
        indent++
        line("if (stat_idx >= ${prefixUpper}_STAT_COUNT) return 0u;")
        line("return _${prefix}_stat_max[stat_idx];")
        indent--
        line("}")
        line()

        // Get stat as percentage
        line("// Get stat as percentage of max for ${schema.id}")
        line("static UINT8 _${prefix}_get_stat_percent(UINT8 stat_idx) {")
        indent++
        line("if (stat_idx >= ${prefixUpper}_STAT_COUNT) return 0u;")
        line("UINT16 max_val = _${prefix}_stat_max[stat_idx];")
        line("if (max_val == 0u) return 0u;")
        line("return (UINT8)(_${prefix}_stat_current[stat_idx] * 100u / max_val);")
        indent--
        line("}")
        line()

        // Check if stat is zero
        line("// Check if stat is zero for ${schema.id}")
        line("static UINT8 _${prefix}_stat_is_zero(UINT8 stat_idx) {")
        indent++
        line("if (stat_idx >= ${prefixUpper}_STAT_COUNT) return 1u;")
        line("return _${prefix}_stat_current[stat_idx] == 0u ? 1u : 0u;")
        indent--
        line("}")
        line()

        // Check if stat is at max
        line("// Check if stat is at max for ${schema.id}")
        line("static UINT8 _${prefix}_stat_is_full(UINT8 stat_idx) {")
        indent++
        line("if (stat_idx >= ${prefixUpper}_STAT_COUNT) return 0u;")
        line("return _${prefix}_stat_current[stat_idx] >= _${prefix}_stat_max[stat_idx] ? 1u : 0u;")
        indent--
        line("}")
        line()

        // Get stat display name
        line("// Get stat display name for ${schema.id}")
        line("static const char* _${prefix}_get_stat_name(UINT8 stat_idx) {")
        indent++
        line("if (stat_idx >= ${prefixUpper}_STAT_COUNT) return \"???\";")
        line("return _${prefix}_stat_names[stat_idx];")
        indent--
        line("}")
        line()
    }
}

/** Generate schema helper functions. */
private fun GBDKCodeGenerator.generateSchemaHelperFunctions(schemas: List<StatSchema>) {
    line("// =============================================================================")
    line("// SCHEMA HELPER FUNCTIONS")
    line("// =============================================================================")
    line()

    // Generate a unified stat access function that takes schema ID
    line("// Get stat count for a schema")
    line("static UINT8 _get_schema_stat_count(UINT8 schema_id) {")
    indent++
    line("switch (schema_id) {")
    indent++
    for (schema in schemas) {
        line("case SCHEMA_${schema.id.uppercase()}: return ${schema.id.uppercase()}_STAT_COUNT;")
    }
    line("default: return 0u;")
    indent--
    line("}")
    indent--
    line("}")
    line()

    // Generate initialization function that initializes all schemas
    line("// Initialize all stat schemas to defaults")
    line("static void _init_all_stat_schemas(void) {")
    indent++
    for (schema in schemas) {
        line("_${schema.id.lowercase()}_init_stats();")
    }
    indent--
    line("}")
    line()

    // Generate stat lookup by name (useful for save/load)
    for (schema in schemas) {
        val prefix = schema.id.lowercase()

        line("// Find stat index by ID for ${schema.id}")
        line("static INT8 _${prefix}_find_stat(const char* stat_id) {")
        indent++
        for ((index, stat) in schema.stats.withIndex()) {
            line("if (stat_id[0] == '${stat.id[0]}' && ")
            line("    strcmp(stat_id, \"${stat.id}\") == 0) return $index;")
        }
        line("return -1; // Not found")
        indent--
        line("}")
        line()
    }

    // Categories support
    val schemasWithCategories = schemas.filter { it.categories.isNotEmpty() }
    if (schemasWithCategories.isNotEmpty()) {
        line("// =============================================================================")
        line("// STAT CATEGORY SUPPORT")
        line("// =============================================================================")
        line()

        for (schema in schemasWithCategories) {
            val prefix = schema.id.lowercase()
            val categories = schema.categories.toList()

            // Category constants
            line("// Categories for ${schema.id}")
            for ((catIndex, category) in categories.withIndex()) {
                line("#define ${prefix.uppercase()}_CAT_${category.uppercase()} ${catIndex}u")
            }
            line("#define ${prefix.uppercase()}_CAT_COUNT ${categories.size}u")
            line()

            // Category membership table
            line("// Stat to category mapping for ${schema.id}")
            line(
                "static const UINT8 _${prefix}_stat_category[${prefix.uppercase()}_STAT_COUNT] = {"
            )
            indent++
            val categoryIndices =
                schema.stats.map { stat ->
                    val catIndex = categories.indexOf(stat.category)
                    if (catIndex >= 0) "${catIndex}u" else "255u" // 255 for no category
                }
            line(categoryIndices.joinToString(", "))
            indent--
            line("};")
            line()

            // Get stats in category
            line("// Get stat category for ${schema.id}")
            line("static UINT8 _${prefix}_get_stat_category(UINT8 stat_idx) {")
            indent++
            line("if (stat_idx >= ${prefix.uppercase()}_STAT_COUNT) return 255u;")
            line("return _${prefix}_stat_category[stat_idx];")
            indent--
            line("}")
            line()
        }
    }
}
