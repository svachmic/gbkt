/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.sport.codegen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Phase 07.4-17 — TILESET-VISUAL-CONTRAST closure tests. Locks the 3-tile builtin tileset's
 * contrast contract: each tile produces a visually distinct on-screen pattern, and the byte
 * derivation is auditable via the tilePatternToBytes helper.
 *
 * The closure target is VERIFICATION.md lines 243 — the previous Plan 11 builtin tileset was
 * data-tier correct (length 48, sentinel set) but visually too uniform under the default DMG
 * palette to distinguish road / wall / grass.
 *
 * tilePatternToBytes and builtinTrackTilesetBytes are `internal` members of SportVisitor
 * (visibility bumped from `private` in Plan 17 — see SUMMARY for rationale). The test therefore
 * calls them directly via SportVisitor() with no reflection; the executor should NOT add reflection
 * imports. This matches the gbkt feedback rule "Quality over shortcuts — no smoke and mirrors": the
 * helpers ARE the contract, so they get a tested visibility marker rather than a reflection-based
 * escape hatch.
 */
class RacingTilesetContrastTest {

    /**
     * Decode 16 GBDK 2bpp interleaved bytes back to an 8x8 palette-index matrix. Inverse of
     * tilePatternToBytes; used in tests to assert pixel-level patterns.
     */
    private fun bytesToMatrix(bytes: List<Int>): List<List<Int>> {
        require(bytes.size == 16) { "tile must be 16 bytes; got ${bytes.size}" }
        val matrix = mutableListOf<List<Int>>()
        for (r in 0..7) {
            val low = bytes[2 * r]
            val high = bytes[2 * r + 1]
            val row = mutableListOf<Int>()
            for (c in 0..7) {
                val bit = 7 - c
                val px = (((high shr bit) and 1) shl 1) or ((low shr bit) and 1)
                row += px
            }
            matrix += row
        }
        return matrix
    }

    /** Direct call — `tilePatternToBytes` is `internal` in SportVisitor (see plan WARNING 6). */
    private fun callTilePatternToBytes(matrix: List<List<Int>>): List<Int> =
        SportVisitor().tilePatternToBytes(matrix)

    /** Direct call — `builtinTrackTilesetBytes` is `internal` in SportVisitor. */
    private fun callBuiltinTrackTilesetBytes(): List<Int> =
        SportVisitor().builtinTrackTilesetBytes()

    // =========================================================================
    // Per-tile assertions
    // =========================================================================

    @Test
    fun `wall tile is uniform palette 3`() {
        val all = callBuiltinTrackTilesetBytes()
        val wallBytes = all.subList(0, 16)
        val matrix = bytesToMatrix(wallBytes)
        for (row in matrix) for (px in row) {
            assertEquals(3, px, "wall pixel must be palette 3 (black); got $px")
        }
    }

    @Test
    fun `wall tile is solid 0xFF (regression-safe)`() {
        val all = callBuiltinTrackTilesetBytes()
        val wallBytes = all.subList(0, 16)
        for (b in wallBytes) {
            assertEquals(0xFF, b, "wall byte must be 0xFF; got $b")
        }
    }

    @Test
    fun `drivable tile is uniform palette 1`() {
        val all = callBuiltinTrackTilesetBytes()
        val drivableBytes = all.subList(16, 32)
        val matrix = bytesToMatrix(drivableBytes)
        for (row in matrix) for (px in row) {
            assertEquals(1, px, "drivable pixel must be palette 1 (light gray); got $px")
        }
    }

    @Test
    fun `grass tile has visible texture (at least 2 distinct palette indices)`() {
        val all = callBuiltinTrackTilesetBytes()
        val grassBytes = all.subList(32, 48)
        val matrix = bytesToMatrix(grassBytes)
        val distinct = matrix.flatten().toSet()
        assertTrue(
            distinct.size >= 2,
            "grass must have visible texture (>= 2 distinct palette indices); got $distinct",
        )
    }

    // =========================================================================
    // Cross-tile distinctness
    // =========================================================================

    @Test
    fun `three tile byte sequences are pairwise distinct`() {
        val all = callBuiltinTrackTilesetBytes()
        val wall = all.subList(0, 16)
        val drivable = all.subList(16, 32)
        val grass = all.subList(32, 48)
        assertNotEquals(wall, drivable, "wall must differ from drivable")
        assertNotEquals(wall, grass, "wall must differ from grass")
        assertNotEquals(drivable, grass, "drivable must differ from grass")
    }

    @Test
    fun `total tileset is 48 bytes (Plan 11 invariant preserved)`() {
        assertEquals(48, callBuiltinTrackTilesetBytes().size)
    }

    // =========================================================================
    // tilePatternToBytes round-trips
    // =========================================================================

    @Test
    fun `tilePatternToBytes round-trip solid palette 3`() {
        val bytes = callTilePatternToBytes(List(8) { List(8) { 3 } })
        assertEquals(16, bytes.size)
        for (b in bytes) assertEquals(0xFF, b)
    }

    @Test
    fun `tilePatternToBytes round-trip solid palette 1`() {
        val bytes = callTilePatternToBytes(List(8) { List(8) { 1 } })
        assertEquals(16, bytes.size)
        // Solid palette 1 = low bit set, high bit clear. Each row pair is (0xFF, 0x00).
        for (r in 0..7) {
            assertEquals(0xFF, bytes[2 * r], "row $r low byte must be 0xFF for palette 1")
            assertEquals(0x00, bytes[2 * r + 1], "row $r high byte must be 0x00 for palette 1")
        }
    }

    @Test
    fun `tilePatternToBytes round-trip solid palette 2`() {
        val bytes = callTilePatternToBytes(List(8) { List(8) { 2 } })
        assertEquals(16, bytes.size)
        // Solid palette 2 = high bit set, low bit clear. Each row pair is (0x00, 0xFF).
        for (r in 0..7) {
            assertEquals(0x00, bytes[2 * r], "row $r low byte must be 0x00 for palette 2")
            assertEquals(0xFF, bytes[2 * r + 1], "row $r high byte must be 0xFF for palette 2")
        }
    }

    @Test
    fun `tilePatternToBytes validates 8x8 size`() {
        assertFailsWith<IllegalArgumentException> {
            callTilePatternToBytes(List(7) { List(8) { 0 } })
        }
        assertFailsWith<IllegalArgumentException> {
            callTilePatternToBytes(List(8) { List(7) { 0 } })
        }
    }

    @Test
    fun `tilePatternToBytes validates palette range`() {
        assertFailsWith<IllegalArgumentException> {
            callTilePatternToBytes(List(8) { List(8) { 4 } })
        }
    }
}
