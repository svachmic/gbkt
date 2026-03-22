/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.debug

import java.io.File
import java.util.TreeMap
import org.json.JSONObject

/**
 * Resolves C source line numbers to Kotlin DSL source locations using `.gbkt.map` files.
 *
 * Source maps are produced by the gbkt Gradle plugin alongside generated C code. Each `.gbkt.map`
 * file is a JSON file with the format:
 * ```json
 * {
 *   "version": 1,
 *   "gameName": "MyGame",
 *   "cFile": "main.c",
 *   "mappings": [
 *     { "cLine": 42, "kotlinFile": "GameDef.kt", "kotlinLine": 15,
 *       "symbol": "score", "snippet": "gameplay/frame" }
 *   ]
 * }
 * ```
 *
 * When [sourceMapsDir] is provided and contains `.gbkt.map` files, [resolve] translates C line
 * numbers (from `EMU_printf` traps) back to the originating Kotlin DSL location. This makes debug
 * log entries actionable — developers see `ScriptBuilder.kt:45` instead of `main.c:1234`.
 *
 * Optionally, when [noiFile] is provided, [resolveByPc] can translate a CPU program counter address
 * to a C source line number via the SDCC linker symbol table.
 *
 * @param sourceMapsDir Directory containing `.gbkt.map` source map files. When null or the
 *   directory does not exist, [resolve] always returns null.
 * @param noiFile SDCC linker `.noi` file containing `DEF` symbol addresses. When provided,
 *   [resolveByPc] can perform best-effort PC → cLine resolution. When null, [resolveByPc] always
 *   returns null.
 */
class SourceMapResolver(sourceMapsDir: File?, noiFile: File? = null) {

    /**
     * A resolved Kotlin DSL source location for a C line number.
     *
     * @param file Kotlin source file name (without path), e.g. "ScriptBuilder.kt".
     * @param line Kotlin source line number.
     * @param symbol The DSL symbol name at this location, e.g. "score". Empty if unavailable.
     * @param context Execution context string derived from the snippet, e.g. "gameplay/frame".
     *   Empty if unavailable.
     */
    data class SourceLocation(
        val file: String,
        val line: Int,
        val symbol: String = "",
        val context: String = "",
    )

    /**
     * Composite key for source map entries, disambiguating entries across multiple generated C
     * files (e.g. `main.c` and `bank1.c`) that may share overlapping line numbers.
     */
    data class SourceKey(val cFile: String, val cLine: Int)

    private val mappings: Map<SourceKey, SourceLocation>

    /**
     * Sorted map of ROM address → symbol name, parsed from the `.noi` file. Used by [resolveByPc]
     * to find the nearest function for a given PC via [TreeMap.floorEntry].
     */
    private val pcSymbols: TreeMap<Int, String>

    /**
     * Map of C function name → first mapped cLine in generated C. Built by scanning `.c` files in
     * [sourceMapsDir] for function definition lines and matching them against source map entries.
     */
    private val functionToFirstCLine: Map<String, Int>

    /**
     * Secondary index: C line number → first matching SourceLocation across all C files. Used by
     * the single-argument [resolve] overload for O(1) lookup.
     */
    private val cLineIndex: Map<Int, SourceLocation>

    init {
        mappings =
            if (sourceMapsDir != null && sourceMapsDir.exists()) {
                loadMaps(sourceMapsDir)
            } else {
                emptyMap()
            }

        pcSymbols =
            if (noiFile != null && noiFile.exists()) {
                loadNoiSymbols(noiFile)
            } else {
                TreeMap()
            }

        functionToFirstCLine =
            if (sourceMapsDir != null && sourceMapsDir.exists() && pcSymbols.isNotEmpty()) {
                buildFunctionCLineIndex(sourceMapsDir)
            } else {
                emptyMap()
            }

        cLineIndex =
            mappings.entries
                .groupBy { it.key.cLine }
                .mapValues { (_, entries) -> entries.first().value }
    }

    /**
     * Resolves a C file and line number to a Kotlin DSL source location.
     *
     * @param cFile The generated C file name, e.g. "main.c" or "bank1.c".
     * @param cLine The C source line number from the generated `.c` file.
     * @return The resolved [SourceLocation], or null if no mapping exists for the given file/line.
     */
    fun resolve(cFile: String, cLine: Int): SourceLocation? = mappings[SourceKey(cFile, cLine)]

    /**
     * Resolves a C line number to a Kotlin DSL source location, searching across all C files.
     *
     * This is a backward-compatible overload for callers that do not have the C file name. When
     * multiple C files contain the same line number, an arbitrary match is returned.
     *
     * @param cLine The C source line number from the generated `.c` file.
     * @return The resolved [SourceLocation], or null if no mapping exists for [cLine].
     */
    fun resolve(cLine: Int): SourceLocation? = cLineIndex[cLine]

    /**
     * Attempts to resolve a CPU program counter (PC) address to a C source line number.
     *
     * Uses the `.noi` linker symbol table to find the nearest function whose start address is at or
     * below the given PC, then looks up the first source-mapped cLine for that function. This gives
     * function-level granularity: the returned cLine is the first mapped line of the containing
     * function, not the exact statement.
     *
     * @param pc The CPU program counter address, or null if not available.
     * @return The C source line number, or null if resolution is not possible.
     */
    fun resolveByPc(pc: Int?): Int? {
        if (pc == null || pcSymbols.isEmpty()) return null

        val entry = pcSymbols.floorEntry(pc) ?: return null
        val symbolName = entry.value

        // Strip leading underscore (SDCC C name-mangling convention)
        val funcName = symbolName.removePrefix("_")
        return functionToFirstCLine[funcName]
    }

    // ── .gbkt.map loading ────────────────────────────────────────────────────

    private fun loadMaps(dir: File): Map<SourceKey, SourceLocation> {
        val result = mutableMapOf<SourceKey, SourceLocation>()
        val mapFiles = dir.listFiles { f -> f.name.endsWith(".gbkt.map") } ?: return emptyMap()
        for (mapFile in mapFiles) {
            try {
                val json = JSONObject(mapFile.readText())
                val cFile = json.optString("cFile", mapFile.name.removeSuffix(".gbkt.map"))
                val mappingsArray = json.getJSONArray("mappings")
                for (i in 0 until mappingsArray.length()) {
                    val m = mappingsArray.getJSONObject(i)
                    val cLine = m.getInt("cLine")
                    result[SourceKey(cFile, cLine)] =
                        SourceLocation(
                            file = m.getString("kotlinFile"),
                            line = m.getInt("kotlinLine"),
                            symbol = m.optString("symbol", ""),
                            context = m.optString("snippet", ""),
                        )
                }
            } catch (_: Exception) {
                // Best-effort map loading — skip malformed files silently
            }
        }
        return result
    }

    // ── .noi symbol loading ──────────────────────────────────────────────────

    /**
     * Parses an SDCC `.noi` file to extract function symbol addresses.
     *
     * The `.noi` format is one symbol per line: `DEF _symbolName 0xADDR`
     *
     * Filters to likely function symbols by excluding:
     * - Section length markers (`l__*`)
     * - Section start markers (`s__*`)
     * - Bank markers (`b_*` but not `b` prefix on normal functions)
     * - Hardware register aliases (`_r*` with uppercase second char, e.g. `_rRAMG`)
     * - Known non-function patterns (`___bank_*`, `.__.ABS.`)
     */
    private fun loadNoiSymbols(file: File): TreeMap<Int, String> {
        val result = TreeMap<Int, String>()
        try {
            file.readLines().forEach { line ->
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 3 && parts[0] == "DEF") {
                    val name = parts[1]
                    val address =
                        try {
                            parseNoiAddress(parts[2])
                        } catch (_: Exception) {
                            return@forEach
                        }

                    if (isFunctionSymbol(name) && address > 0) {
                        result[address] = name
                    }
                }
            }
        } catch (_: Exception) {
            // Best-effort — return whatever was parsed
        }
        return result
    }

    /**
     * Parses a `.noi` address value. Supports both `0xADDR` (hex literal) and `BB:AAAA`
     * (bank:address) formats.
     */
    private fun parseNoiAddress(addrStr: String): Int {
        return if (addrStr.startsWith("0x") || addrStr.startsWith("0X")) {
            addrStr.removePrefix("0x").removePrefix("0X").toInt(16)
        } else if (":" in addrStr) {
            // bank:address format — encode bank into upper bits for banked ROM regions.
            // For addresses in [0x4000, 0x7FFF] (switchable ROM bank), the physical
            // address is: (bank << 14) | (addr & 0x3FFF). For bank 0 or addresses
            // outside the switchable region, the address alone is sufficient.
            // This is best-effort: resolveByPc uses floorEntry() which may still
            // land on the wrong function if the symbol table is sparse.
            val bank = addrStr.substringBefore(":").toInt(16)
            val addr = addrStr.substringAfter(":").toInt(16)
            if (bank > 0 && addr >= 0x4000) (bank shl 14) or (addr and 0x3FFF) else addr
        } else {
            addrStr.toInt(16)
        }
    }

    /** Returns true if the symbol name looks like a C function (not a section/register/bank). */
    private fun isFunctionSymbol(name: String): Boolean {
        if (name.startsWith("l__") || name.startsWith("s__")) return false
        if (name.startsWith("___bank_")) return false
        if (name.startsWith(".")) return false
        if (name.startsWith("b_") && !name.contains("_enter") && !name.contains("_frame")) {
            return false
        }
        // Hardware registers: _rXXX where X is uppercase
        if (name.startsWith("_r") && name.length > 2 && name[2].isUpperCase()) return false
        // Data symbols: skip known variable prefixes
        if (name.startsWith("_shadow_")) return false
        return true
    }

    // ── Function → cLine index ───────────────────────────────────────────────

    /**
     * Scans generated `.c` files in [dir] for function definition lines and builds a map from
     * function name to the first source-mapped cLine within that function.
     *
     * For each `.c` file, finds lines matching `void funcName(...) {` or similar patterns, extracts
     * the function name, and records the first cLine that appears in the source map for that
     * function's line range.
     */
    private fun buildFunctionCLineIndex(dir: File): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        val cFiles = dir.listFiles { f -> f.name.endsWith(".c") } ?: return emptyMap()

        for (cFile in cFiles) {
            try {
                val cFileName = cFile.name
                val lines = cFile.readLines()
                val functions = extractFunctionRanges(lines)

                for ((funcName, startLine) in functions) {
                    // Find the first source map entry at or after this function's start line
                    val endLine =
                        functions.entries
                            .filter { it.value > startLine }
                            .minByOrNull { it.value }
                            ?.value ?: (lines.size + 1)

                    val firstMappedLine =
                        mappings.keys
                            .filter {
                                it.cFile == cFileName && it.cLine >= startLine && it.cLine < endLine
                            }
                            .minByOrNull { it.cLine }
                            ?.cLine

                    if (firstMappedLine != null && funcName !in result) {
                        result[funcName] = firstMappedLine
                    }
                }
            } catch (_: Exception) {
                // Best-effort — skip files that can't be parsed
            }
        }
        return result
    }

    /**
     * Extracts function name → start line number from C source lines. Matches patterns like:
     * - `void funcName(void) {`
     * - `void funcName(void) BANKED {`
     * - `UINT8 funcName(UINT8 x) {`
     */
    private fun extractFunctionRanges(lines: List<String>): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        val typeAlts = "void|UINT8|INT8|UINT16|INT16|unsigned\\s+char|char\\s*\\*?|int"
        val funcPattern = Regex("^(?:static\\s+)?(?:const\\s+)?(?:$typeAlts)\\s+(\\w+)\\s*\\(")
        for ((index, line) in lines.withIndex()) {
            val match = funcPattern.find(line.trim())
            if (match != null) {
                val funcName = match.groupValues[1]
                // Store 1-based line number to match cLine from source maps
                result[funcName] = index + 1
            }
        }
        return result
    }
}
