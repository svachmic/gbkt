/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

/**
 * Content-based tile deduplication utility.
 *
 * Deduplicates Game Boy tiles by comparing their raw byte content. Tiles with identical bytes share
 * the same index in the output. The unique tile list preserves first-occurrence ordering.
 *
 * Usage:
 * ```kotlin
 * val deduplicator = TileDeduplicator()
 * val (uniqueTiles, indexMap) = deduplicator.deduplicate(allTiles)
 * // uniqueTiles: deduplicated tile list in first-occurrence order
 * // indexMap[i]: index into uniqueTiles for original tile i
 * ```
 */
class TileDeduplicator {

    /**
     * Deduplicate a list of byte-array tiles using content-based identity.
     *
     * @param tiles Input tiles (each is a raw byte array, typically 16 bytes for GB tiles)
     * @return Pair of (unique tiles in first-occurrence order, index map from original to unique
     *   position)
     */
    fun deduplicate(tiles: List<ByteArray>): Pair<List<ByteArray>, IntArray> {
        val uniqueTiles = mutableListOf<ByteArray>()
        val tileIndex = mutableMapOf<ByteArrayKey, Int>()
        val indexMap = IntArray(tiles.size)

        for ((i, tile) in tiles.withIndex()) {
            val key = ByteArrayKey(tile)
            indexMap[i] =
                tileIndex.getOrPut(key) {
                    val idx = uniqueTiles.size
                    uniqueTiles.add(tile)
                    idx
                }
        }

        return uniqueTiles to indexMap
    }
}

/**
 * A ByteArray wrapper with content-based equality and hash code.
 *
 * This follows the same pattern as [AssetPipeline.Tile.equals] which uses [ByteArray.contentEquals]
 * and [ByteArray.contentHashCode] for value-based comparison of tile data.
 */
class ByteArrayKey(val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteArrayKey) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int = data.contentHashCode()
}
