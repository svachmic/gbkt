/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("CyclomaticComplexMethod") // Bank allocation has many decision points

package io.github.gbkt.core.assets

import io.github.gbkt.core.CompiledMapData
import io.github.gbkt.core.CompiledTileData
import io.github.gbkt.core.ir.StringNamespace
import io.github.gbkt.core.ir.StringTable

/**
 * Bank allocation strategy for ROM data.
 *
 * Default allocation strategy (typical for dungeon-crawler RPGs):
 * - Bank 0: Home (main code, variables)
 * - Bank 1-7: Strings (distributed by namespace)
 * - Bank 5: Tables (balance data)
 * - Bank 8-17: Sprites and floor data
 * - Bank 31: Additional assets
 */
class BankAllocator(
    /** Starting bank for sprites and map data (default: 8). */
    val spriteStartBank: Int = DEFAULT_SPRITE_START_BANK,
    /** Size of each bank in bytes (default: 16 KB). */
    val bankSize: Int = BANK_SIZE,
    /** Maximum bank number (default: 31 for 512KB ROM). */
    val maxBanks: Int = MAX_BANKS,
    /** Starting bank for strings (default: 1). */
    val stringStartBank: Int = DEFAULT_STRING_START_BANK,
    /** End bank for strings exclusive (default: 8, so banks 1-7). */
    val stringEndBank: Int = DEFAULT_STRING_END_BANK,
) {
    /** Current bank being filled. */
    private var currentBank = spriteStartBank

    /** Bytes used in the current bank. */
    private var currentBankUsage = 0

    /** Map of bank number to total bytes allocated. */
    private val bankUsage = mutableMapOf<Int, Int>()

    /**
     * Allocate a bank for tile data of the given size.
     *
     * @param sizeBytes Size of the tile data in bytes
     * @return The bank number to use
     */
    fun allocateForTiles(sizeBytes: Int): Int {
        // Check if it fits in the current bank
        if (currentBankUsage + sizeBytes > bankSize) {
            // Move to next bank
            currentBank++
            currentBankUsage = 0

            if (currentBank > maxBanks) {
                throw IllegalStateException(
                    "Exceeded maximum bank count ($maxBanks). " + "Total data is too large for ROM."
                )
            }
        }

        val bank = currentBank
        currentBankUsage += sizeBytes
        bankUsage.merge(bank, sizeBytes, Int::plus)

        return bank
    }

    /**
     * Allocate a specific bank for floor map data. Floor maps get dedicated banks (8 = floor 1, 9 =
     * floor 2, etc.)
     *
     * @param floorNumber The floor number (1-8)
     * @return The bank number for this floor
     */
    fun allocateForFloor(floorNumber: Int): Int {
        require(floorNumber in 1..8) { "Floor number must be 1-8" }
        return spriteStartBank + floorNumber - 1
    }

    /**
     * Allocate banks for a list of tile data items. Returns a new list with bank assignments.
     *
     * @param tileDataList List of tile data without bank assignments
     * @return New list with bank assignments
     */
    fun allocateTileData(tileDataList: List<CompiledTileData>): List<CompiledTileData> {
        return tileDataList.map { data ->
            val bank = allocateForTiles(data.sizeBytes)
            data.copy(bank = bank)
        }
    }

    /**
     * Allocate banks for a list of map data items. Tries to place maps on specific floor banks if
     * they match the floor naming pattern.
     *
     * @param mapDataList List of map data without bank assignments
     * @return New list with bank assignments
     */
    fun allocateMapData(mapDataList: List<CompiledMapData>): List<CompiledMapData> {
        return mapDataList.map { data ->
            // Check if this is a floor map (e.g., "floor1", "floor_2", etc.)
            val floorMatch = FLOOR_PATTERN.find(data.name)
            val bank =
                if (floorMatch != null) {
                    val floorNum = floorMatch.groupValues[1].toIntOrNull()
                    if (floorNum != null && floorNum in 1..8) {
                        allocateForFloor(floorNum)
                    } else {
                        allocateForTiles(data.sizeBytes)
                    }
                } else {
                    allocateForTiles(data.sizeBytes)
                }
            data.copy(bank = bank)
        }
    }

    /** Get the total number of banks used. */
    fun banksUsed(): Int = bankUsage.keys.size

    /** Get a summary of bank usage. */
    fun usageSummary(): String {
        return buildString {
            appendLine("Bank Allocation Summary:")
            for (bank in bankUsage.keys.sorted()) {
                val used = bankUsage[bank] ?: 0
                val percent = (used * 100) / bankSize
                appendLine("  Bank $bank: $used / $bankSize bytes ($percent%)")
            }
            appendLine("  Total banks used: ${banksUsed()}")
        }
    }

    /** Reset the allocator to initial state. */
    fun reset() {
        currentBank = spriteStartBank
        currentBankUsage = 0
        bankUsage.clear()
        stringBankUsage.clear()
    }

    // -------------------------------------------------------------------------
    // String bank allocation
    // -------------------------------------------------------------------------

    /** Bytes used in each string bank. */
    private val stringBankUsage = mutableMapOf<Int, Int>()

    /**
     * Allocate banks for string namespaces using first-fit decreasing bin packing.
     *
     * This algorithm:
     * 1. Respects existing bank hints (bank > 0) from PO file @bank comments
     * 2. Sorts remaining namespaces by size (largest first)
     * 3. Assigns each namespace to the first bank with enough space
     * 4. Opens new banks as needed within the string bank range
     *
     * @param stringTable The string table with optional bank hints
     * @return New StringTable with banks assigned to each namespace
     * @throws IllegalStateException if strings exceed available bank space
     */
    fun allocateForStrings(stringTable: StringTable): StringTable {
        if (stringTable.namespaces.isEmpty()) return stringTable

        // Track bank usage - start with pre-assigned banks
        val bankUsageLocal = mutableMapOf<Int, Int>()
        for (bank in 0 until maxBanks) {
            bankUsageLocal[bank] = stringBankUsage[bank] ?: 0
        }

        // Separate namespaces with bank hints from those needing allocation
        // Bank >= 0 means explicitly assigned, BANK_UNASSIGNED (-1) means auto-allocate
        val (preAssigned, needsAllocation) = stringTable.namespaces.partition { it.bank >= 0 }

        // Process pre-assigned namespaces first (respect bank hints from PO file)
        val allocatedNamespaces = mutableListOf<StringNamespace>()
        for (namespace in preAssigned) {
            val bank = namespace.bank
            val size = namespace.sizeBytes
            val used = bankUsageLocal[bank] ?: 0

            if (used + size > bankSize) {
                System.err.println(
                    "Warning: Bank $bank overflow for '${namespace.name}' ($size bytes, $used already used)"
                )
            }

            bankUsageLocal[bank] = used + size
            allocatedNamespaces.add(namespace) // Keep original bank assignment
            stringBankUsage[bank] = bankUsageLocal[bank] ?: 0
        }

        // Sort remaining namespaces by size (largest first for better packing)
        val sortedNeedsAllocation = needsAllocation.sortedByDescending { it.sizeBytes }

        for (namespace in sortedNeedsAllocation) {
            val size = namespace.sizeBytes

            // Find first bank with enough space within string bank range
            var assignedBank: Int? = null
            for (bank in stringStartBank until stringEndBank) {
                val used = bankUsageLocal[bank] ?: 0
                if (used + size <= bankSize) {
                    assignedBank = bank
                    bankUsageLocal[bank] = used + size
                    break
                }
            }

            if (assignedBank == null) {
                throw IllegalStateException(
                    "Cannot allocate namespace '${namespace.name}' ($size bytes). " +
                        "All string banks (${stringStartBank}-${stringEndBank - 1}) are full. " +
                        "Total string data: ${stringTable.totalSizeBytes} bytes."
                )
            }

            allocatedNamespaces.add(namespace.copy(bank = assignedBank))
            stringBankUsage[assignedBank] = bankUsageLocal[assignedBank] ?: 0
        }

        // Restore original order
        val orderedNamespaces =
            stringTable.namespaces.map { original ->
                allocatedNamespaces.find { it.name == original.name }
                    ?: error("Lost namespace ${original.name} during allocation")
            }

        return StringTable(orderedNamespaces)
    }

    /**
     * Get available space in string banks.
     *
     * @return Map of bank number to available bytes
     */
    fun stringBankAvailability(): Map<Int, Int> {
        return (stringStartBank until stringEndBank).associateWith { bank ->
            bankSize - (stringBankUsage[bank] ?: 0)
        }
    }

    companion object {
        /** Default starting bank for sprites (matches original game). */
        const val DEFAULT_SPRITE_START_BANK = 8

        /** Size of each ROM bank in bytes. */
        const val BANK_SIZE = 16384 // 16 KB

        /** Maximum bank number for a 512KB ROM. */
        const val MAX_BANKS = 31

        /** Default starting bank for strings. */
        const val DEFAULT_STRING_START_BANK = 1

        /** Default end bank for strings (exclusive). Banks 1-7 by default. */
        const val DEFAULT_STRING_END_BANK = 8

        /** Pattern to match floor names (floor1, floor_2, etc.). */
        private val FLOOR_PATTERN = Regex("""floor[_]?(\d+)""", RegexOption.IGNORE_CASE)

        /** Estimate the number of banks needed for the given data size. */
        fun estimateBanksNeeded(totalSizeBytes: Int): Int {
            return (totalSizeBytes + BANK_SIZE - 1) / BANK_SIZE
        }
    }
}
