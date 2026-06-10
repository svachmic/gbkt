/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TileDeduplicatorTest {

    private fun tile(vararg bytes: Byte): ByteArray {
        // Pad to 16 bytes (Game Boy tile size)
        val data = ByteArray(16)
        bytes.copyInto(data)
        return data
    }

    @Test
    fun `empty input returns empty output`() {
        val deduplicator = TileDeduplicator()
        val (uniqueTiles, indexMap) = deduplicator.deduplicate(emptyList())
        assertTrue(uniqueTiles.isEmpty())
        assertEquals(0, indexMap.size)
    }

    @Test
    fun `all unique tiles returns same count with identity index map`() {
        val tile0 = tile(0, 1)
        val tile1 = tile(2, 3)
        val tile2 = tile(4, 5)
        val deduplicator = TileDeduplicator()
        val (uniqueTiles, indexMap) = deduplicator.deduplicate(listOf(tile0, tile1, tile2))
        assertEquals(3, uniqueTiles.size)
        assertContentEquals(intArrayOf(0, 1, 2), indexMap)
    }

    @Test
    fun `duplicate tiles are deduplicated - two identical tiles map to same index`() {
        val tileA = tile(0xFF.toByte(), 0)
        val tileB = tile(0xFF.toByte(), 0) // identical bytes
        val deduplicator = TileDeduplicator()
        val (uniqueTiles, indexMap) = deduplicator.deduplicate(listOf(tileA, tileB))
        assertEquals(1, uniqueTiles.size)
        assertEquals(0, indexMap[0])
        assertEquals(0, indexMap[1])
    }

    @Test
    fun `mixed unique and duplicate tiles produce correct index map`() {
        val tileA = tile(1, 0)
        val tileB = tile(2, 0)
        val tileACopy = tile(1, 0) // duplicate of tileA
        val tileC = tile(3, 0)
        // Input: [A, B, A_copy, C] -> unique: [A, B, C], indexMap: [0, 1, 0, 2]
        val deduplicator = TileDeduplicator()
        val (uniqueTiles, indexMap) =
            deduplicator.deduplicate(listOf(tileA, tileB, tileACopy, tileC))
        assertEquals(3, uniqueTiles.size)
        assertContentEquals(intArrayOf(0, 1, 0, 2), indexMap)
    }

    @Test
    fun `order preserved - first occurrence determines position in unique list`() {
        val tileB = tile(2, 0)
        val tileA = tile(1, 0)
        val tileBCopy = tile(2, 0) // duplicate of tileB
        // Input: [B, A, B_copy] -> unique: [B, A] in first-occurrence order
        val deduplicator = TileDeduplicator()
        val (uniqueTiles, indexMap) = deduplicator.deduplicate(listOf(tileB, tileA, tileBCopy))
        assertEquals(2, uniqueTiles.size)
        // First unique tile should be B (first encountered)
        assertContentEquals(tileB, uniqueTiles[0])
        // Second unique tile should be A
        assertContentEquals(tileA, uniqueTiles[1])
        assertContentEquals(intArrayOf(0, 1, 0), indexMap)
    }

    @Test
    fun `single tile input returns single unique tile with identity map`() {
        val tile0 = tile(0x0A, 0x0B)
        val deduplicator = TileDeduplicator()
        val (uniqueTiles, indexMap) = deduplicator.deduplicate(listOf(tile0))
        assertEquals(1, uniqueTiles.size)
        assertContentEquals(intArrayOf(0), indexMap)
        assertContentEquals(tile0, uniqueTiles[0])
    }

    @Test
    fun `all same tiles deduplicates to single tile`() {
        val tileX = tile(0x55, 0xAA.toByte())
        val deduplicator = TileDeduplicator()
        val (uniqueTiles, indexMap) = deduplicator.deduplicate(listOf(tileX, tileX, tileX, tileX))
        assertEquals(1, uniqueTiles.size)
        assertContentEquals(intArrayOf(0, 0, 0, 0), indexMap)
    }
}
