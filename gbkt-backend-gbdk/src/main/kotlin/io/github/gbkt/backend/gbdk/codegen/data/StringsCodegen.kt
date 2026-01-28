/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.data

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import io.github.gbkt.core.ir.StringTable

// =============================================================================
// STRING TABLE CODE GENERATION
// Generates banked C string constants from StringTable
// =============================================================================

/**
 * Generate C code for a string table with bank switching.
 *
 * Outputs:
 * - `#pragma bank=N` directives for each bank
 * - `const char str_namespace_key[] = "value";` for each string
 * - Size comments for each namespace
 *
 * @param stringTable The string table to generate code for
 */
internal fun GBDKCodeGenerator.generateStringTable(stringTable: StringTable?) {
    if (stringTable == null || stringTable.namespaces.isEmpty()) return

    line("// =============================================================================")
    line("// STRING DATA (${stringTable.totalSizeBytes} bytes total)")
    line("// =============================================================================")
    line()

    // Group namespaces by bank for organized output
    for ((bank, namespaces) in stringTable.byBank.toSortedMap()) {
        // Switch to the appropriate bank
        setBank(bank)
        val bankSize = namespaces.sumOf { it.sizeBytes }
        line("// Bank $bank strings ($bankSize bytes)")
        line()

        for (namespace in namespaces) {
            line("// --- ${namespace.name} namespace (${namespace.sizeBytes} bytes) ---")

            for (string in namespace.strings) {
                val cName = "str_${namespace.name}_${string.key}"
                val escaped = escapeCString(string.value)
                line("const char $cName[] = \"$escaped\";")
            }
            line()
        }
    }

    // Return to home bank after string data
    returnToHome()
}

/**
 * Generate extern declarations for string constants. Call this in the header section to allow other
 * code to reference strings.
 */
internal fun GBDKCodeGenerator.generateStringExterns(stringTable: StringTable?) {
    if (stringTable == null || stringTable.namespaces.isEmpty()) return

    line("// String extern declarations")
    for (namespace in stringTable.namespaces) {
        for (string in namespace.strings) {
            val cName = "str_${namespace.name}_${string.key}"
            line("extern const char $cName[];")
        }
    }
    line()
}

/**
 * Generate a lookup function to get strings by namespace and key. Useful for dynamic string lookup
 * at runtime.
 */
internal fun GBDKCodeGenerator.generateStringLookup(stringTable: StringTable?) {
    if (stringTable == null || stringTable.namespaces.isEmpty()) return

    // Generate string ID enum
    line("// String IDs for lookup")
    line("typedef enum {")
    indent++
    var stringId = 0
    for (namespace in stringTable.namespaces) {
        for (string in namespace.strings) {
            val enumName = "STR_${namespace.name.uppercase()}_${string.key.uppercase()}"
            line("$enumName = $stringId,")
            stringId++
        }
    }
    line("STRING_COUNT = $stringId")
    indent--
    line("} StringId;")
    line()

    // Generate string pointer table (for quick lookup)
    line("// String pointer lookup table")
    line("const char* const _string_table[STRING_COUNT] = {")
    indent++
    for (namespace in stringTable.namespaces) {
        line("// ${namespace.name}")
        for (string in namespace.strings) {
            val cName = "str_${namespace.name}_${string.key}"
            line("$cName,")
        }
    }
    indent--
    line("};")
    line()

    // Generate bank lookup table
    line("// String bank lookup table")
    line("const UINT8 _string_bank[STRING_COUNT] = {")
    indent++
    for (namespace in stringTable.namespaces) {
        for (string in namespace.strings) {
            line("${namespace.bank},  // ${namespace.name}_${string.key}")
        }
    }
    indent--
    line("};")
    line()

    // Generate getter function
    block("const char* get_string(StringId id)") {
        line("// Switch to the correct bank and return the string pointer")
        line("// Note: Caller must ensure they're in the correct bank context")
        line("return _string_table[id];")
    }
    line()
}

/** Escape a string for C output. Handles special characters and non-printable bytes. */
private fun escapeCString(s: String): String {
    return buildString {
        for (c in s) {
            when (c) {
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '%' -> append("%") // Keep format specifiers as-is
                else -> {
                    if (c.code in 32..126) {
                        append(c)
                    } else {
                        // Escape non-printable characters as hex
                        append("\\x${c.code.toString(16).padStart(2, '0')}")
                    }
                }
            }
        }
    }
}
