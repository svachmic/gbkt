/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import io.github.gbkt.emulator.MemoryAccess
import java.io.File

/**
 * Reads named DSL variables from a running emulator by resolving symbol names to memory addresses
 * via an SDCC `.sym` file.
 *
 * Symbol names come from the lcc compiler output: each global DSL variable `score` becomes
 * `__score` in the GBDK `.noi` file. This class strips all leading underscores for the user-facing
 * API.
 *
 * Supported symbol file formats:
 * ```
 * # SDCC .sym format
 * DEF _score 00:C100
 * DEF _lives 00:C101
 *
 * # GBDK .noi format
 * DEF __score 0xC100
 * DEF __lives 0xC101
 * ```
 *
 * @param memory The emulator memory interface to read variable values from.
 * @param symFile Optional `.sym` file to load symbols from immediately.
 */
class VariableInspector(private val memory: MemoryAccess, symFile: File? = null) {

    companion object {
        /** Start of Game Boy WRAM (Work RAM) — 0xC000. */
        const val WRAM_START = 0xC000

        /** End of Game Boy WRAM (Work RAM) — 0xDFFF. */
        const val WRAM_END = 0xDFFF
    }

    /**
     * A single resolved symbol entry.
     *
     * @param name Human-readable name (without the C underscore prefix).
     * @param address Game Boy address space address (0x0000–0xFFFF).
     * @param type Inferred variable type (UINT8, INT8, UINT16).
     */
    data class SymbolEntry(val name: String, val address: Int, val type: String)

    private val symbols: MutableMap<String, SymbolEntry> = mutableMapOf()

    init {
        if (symFile != null && symFile.exists()) {
            loadSymbols(symFile)
        }
    }

    /**
     * Loads symbols from an SDCC `.sym` / `.noi` file.
     *
     * Supports two address formats produced by GBDK toolchain:
     * - `DEF _symbolName bank:ADDR` — SDCC `.sym` format (e.g. "00:C100")
     * - `DEF __symbolName 0xADDR` — GBDK `.noi` format (e.g. "0xC0D5")
     *
     * Only symbols starting with `_` are loaded. Leading underscores are stripped to recover the
     * original DSL variable name (e.g. `__p1Score` → `p1Score`, `_score` → `score`).
     *
     * Invalid or malformed lines are silently skipped.
     */
    fun loadSymbols(symFile: File) {
        symFile.readLines().forEach { line ->
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size < 3 || parts[0] != "DEF" || !parts[1].startsWith("_")) return@forEach
            val name = parseSymbolName(parts[1]) ?: return@forEach
            val address = parseSymbolAddress(parts[2]) ?: return@forEach
            if (address !in WRAM_START..WRAM_END) return@forEach
            symbols[name] = SymbolEntry(name, address, inferVariableType(name))
        }
    }

    /**
     * Strips leading underscores from a raw SDCC/GBDK symbol name and returns the clean DSL name.
     *
     * SDCC uses one leading underscore (`_score`); GBDK `.noi` uses two (`__score`). Returns null
     * if the stripped result is empty (bare underscore lines are skipped).
     */
    private fun parseSymbolName(rawName: String): String? {
        val name = rawName.trimStart('_')
        return if (name.isEmpty()) null else name
    }

    /**
     * Parses a symbol address string in either SDCC `.sym` or GBDK `.noi` format.
     *
     * - SDCC `.sym` format: `"bank:ADDR"` — hex after the colon (e.g. `"00:C100"` → 0xC100)
     * - GBDK `.noi` format: `"0xADDR"` — hex after the `0x` prefix (e.g. `"0xC0D5"` → 0xC0D5)
     *
     * Returns null for unknown formats or when parsing fails.
     */
    private fun parseSymbolAddress(addrStr: String): Int? =
        try {
            when {
                addrStr.contains(":") -> addrStr.substringAfter(":").toInt(16)
                addrStr.startsWith("0x") || addrStr.startsWith("0X") ->
                    addrStr.removePrefix("0x").removePrefix("0X").toInt(16)
                else -> null
            }
        } catch (_: Exception) {
            null
        }

    /**
     * Reads the current byte value for a named DSL variable.
     *
     * @param name The DSL variable name (without the C underscore prefix).
     * @return The byte value at the symbol's address (0–255), or null if the symbol is not found.
     */
    fun readNamed(name: String): Int? {
        val entry = symbols[name] ?: return null
        return memory.readByte(entry.address)
    }

    /**
     * Writes a byte value for a named DSL variable.
     *
     * Resolves the variable name to a memory address via the loaded symbol table and writes the
     * value using [MemoryAccess.writeByte].
     *
     * @param name The DSL variable name (without the C underscore prefix).
     * @param value The byte value to write (0–255).
     * @return `true` if the symbol was found and the value was written, `false` if the symbol is
     *   not loaded.
     */
    fun writeNamed(name: String, value: Int): Boolean {
        val entry = symbols[name] ?: return false
        memory.writeByte(entry.address, value)
        return true
    }

    /**
     * Reads a 16-bit little-endian value for a named DSL variable.
     *
     * Reads two bytes: lo byte at `address`, hi byte at `address + 1`.
     *
     * @param name The DSL variable name (without the C underscore prefix).
     * @return The 16-bit value (0–65535), or null if the symbol is not found.
     */
    fun readNamedInt16(name: String): Int? {
        val entry = symbols[name] ?: return null
        val lo = memory.readByte(entry.address)
        val hi = memory.readByte(entry.address + 1)
        return (hi shl 8) or lo
    }

    /**
     * Reads a raw byte from a specific address in the emulator's address space.
     *
     * @param address The Game Boy address (0x0000–0xFFFF).
     * @return The byte value at the address (0–255).
     */
    fun readAddress(address: Int): Int = memory.readByte(address)

    /**
     * Returns a snapshot of all loaded variables mapped to their type-correct values.
     *
     * All loaded symbols are guaranteed to be in WRAM (0xC000–0xDFFF), so reads are always safe.
     * Values are interpreted according to the symbol's type:
     * - UINT8/U8: unsigned byte (0–255)
     * - INT8/I8: signed byte (-128–127)
     * - UINT16/U16: unsigned 16-bit little-endian (0–65535)
     * - INT16/I16: signed 16-bit little-endian (-32768–32767)
     *
     * @return Map from variable name to type-correct value.
     */
    fun readAll(): Map<String, Int> = symbols.mapValues { (_, entry) -> readTypedValue(entry) }

    /**
     * Overrides the type for one or more symbols, replacing the heuristic-inferred type.
     *
     * Used by [StepAgent] to apply authoritative types from [GameMetadata] after session start.
     * Only updates symbols that are already loaded; unknown names are silently ignored.
     *
     * @param typeMap Map from variable name to type string (e.g. "I8", "U16", "INT16").
     */
    fun overrideTypes(typeMap: Map<String, String>) {
        for ((name, type) in typeMap) {
            symbols[name]?.let { existing -> symbols[name] = existing.copy(type = type) }
        }
    }

    /**
     * Reads a type-correct value for the given symbol entry.
     *
     * Dispatches on [SymbolEntry.type] to read and sign-extend as appropriate.
     */
    private fun readTypedValue(entry: SymbolEntry): Int {
        val raw = memory.readByte(entry.address)
        return when (entry.type) {
            "INT8",
            "I8" -> if (raw > 127) raw - 256 else raw
            "UINT16",
            "U16" -> {
                if (entry.address + 1 > WRAM_END) return raw // boundary safety
                val lo = memory.readByte(entry.address)
                val hi = memory.readByte(entry.address + 1)
                (hi shl 8) or lo
            }
            "INT16",
            "I16" -> {
                if (entry.address + 1 > WRAM_END) return raw // boundary safety
                val lo = memory.readByte(entry.address)
                val hi = memory.readByte(entry.address + 1)
                val raw16 = (hi shl 8) or lo
                if (raw16 > 32767) raw16 - 65536 else raw16
            }
            else -> raw // UINT8, U8 — existing unsigned byte behavior
        }
    }

    /**
     * Returns all loaded symbol names, sorted alphabetically.
     *
     * @return Sorted list of symbol names.
     */
    fun listVariables(): List<String> = symbols.keys.sorted()

    /**
     * Returns the inferred type string for a loaded symbol.
     *
     * @param name The DSL variable name (without the C underscore prefix).
     * @return The type string (UINT8, INT8, UINT16), or null if not found.
     */
    fun getSymbolType(name: String): String? = symbols[name]?.type

    /**
     * Infers a plausible variable type from the symbol name using naming conventions.
     *
     * Heuristic:
     * - Names containing "16", "addr", or "ptr" → UINT16 (likely 16-bit values or pointers)
     * - Names containing "dx", "dy", "vel", or "dir" → INT8 (likely signed deltas/velocities)
     * - Everything else → UINT8 (default for Game Boy variables)
     */
    private fun inferVariableType(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("16") || lower.contains("addr") || lower.contains("ptr") -> "UINT16"
            lower.contains("dx") ||
                lower.contains("dy") ||
                lower.contains("vel") ||
                lower.contains("dir") -> "INT8"
            else -> "UINT8"
        }
    }
}
