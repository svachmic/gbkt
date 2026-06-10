/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.sport.codegen

import io.github.gbkt.genre.sport.domain.WaypointDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// =============================================================================
// Plan 07.4-33 — JVM RED test locking the TrackSynthesizer corridor-shape
// contract for GAP-TRACK-NOT-RENDERED-AS-CIRCUIT.
//
// This test MUST FAIL against HEAD. Its job is to lock the per-tile contract
// the Plan 07.4-35 GREEN fix has to satisfy. Round-7 round-trip evidence
// (USER-RUNTIME-UAT-2026-05-12.md) showed SC-4-VISUAL passing on pixel-count
// thresholds even though the rendered tilemap had the wrong shape; D-N-12
// codifies that visual SCs whose verdict depends on shape require per-tile
// structural assertion.
//
// Tile-index map (locked in TrackSynthesizer.kt:39-41):
//   0 = TILE_WALL
//   1 = TILE_DRIVABLE
//   2 = TILE_GRASS
//
// Expected corridor derived from
//   .planning/phases/07.4-sport-genre-codegen-fix-inserted/evidence/round-8-
//   camera-and-track/07-expected-circuit-tilemap-ascii-art.txt
// Keep this test fixture and that evidence file in sync.
//
// Decisions cited:
//   D-11   — corridor width default = 4 tiles -> 3-tile-thick drivable annulus
//   D-17   — enclosed loop; interior is NOT drivable (no corner-cutting)
//   D-N-12 — per-tile assertion required (NOT pixel/count thresholds)
// =============================================================================

class TrackSynthesizerCircuitShapeTest {

    private val mapWidth = 19
    private val mapHeight = 19

    /**
     * Racer.kt waypoints (lines 104-107): a 10x10 enclosed rectangular polygon with checkpoints at
     * the top-left and bottom-right corners.
     */
    private val racerWaypoints =
        listOf(
            WaypointDef(tileX = 5, tileY = 5, isCheckpoint = true),
            WaypointDef(tileX = 15, tileY = 5, isCheckpoint = false),
            WaypointDef(tileX = 15, tileY = 15, isCheckpoint = true),
            WaypointDef(tileX = 5, tileY = 15, isCheckpoint = false),
        )

    /**
     * Hand-derived expected 19x19 corridor for the racerWaypoints polygon. Glyphs: . = TILE_WALL
     * (0)
     *
     * # = TILE_DRIVABLE (1)
     * , = TILE_GRASS (2)
     *
     * Matches 07-expected-circuit-tilemap-ascii-art.txt byte-for-byte. Drivable corridor is 3 tiles
     * thick (rows 4-6 top, rows 14-16 bottom, cols 4-6 left, cols 14-16 right). Interior (rows 7-13
     * x cols 7-13) is TILE_GRASS — not drivable (D-17 enclosed-loop invariant).
     */
    private val expectedGridAscii =
        listOf(
            "...................", // row 0
            "...................", // row 1
            "...................", // row 2
            "...................", // row 3
            "....#############..", // row 4
            "....#############..", // row 5
            "....#############..", // row 6
            "....###,,,,,,,###..", // row 7
            "....###,,,,,,,###..", // row 8
            "....###,,,,,,,###..", // row 9
            "....###,,,,,,,###..", // row 10
            "....###,,,,,,,###..", // row 11
            "....###,,,,,,,###..", // row 12
            "....###,,,,,,,###..", // row 13
            "....#############..", // row 14
            "....#############..", // row 15
            "....#############..", // row 16
            "...................", // row 17
            "...................", // row 18
        )

    /**
     * Decode the ASCII art into a flat 19*19 IntArray matching the row-major layout
     * TrackSynthesizer.synthesize() returns. Order MUST match the synthesizer's tileData order (idx
     * = y * mapWidth + x).
     */
    private fun expectedTileArray(): IntArray {
        require(expectedGridAscii.size == mapHeight) {
            "expected grid row count ${expectedGridAscii.size} != mapHeight $mapHeight"
        }
        val out = IntArray(mapWidth * mapHeight)
        for (y in 0 until mapHeight) {
            val row = expectedGridAscii[y]
            require(row.length == mapWidth) {
                "expected grid row $y has ${row.length} cols, want $mapWidth"
            }
            for (x in 0 until mapWidth) {
                out[y * mapWidth + x] =
                    when (val ch = row[x]) {
                        '.' -> TILE_WALL_VALUE
                        '#' -> TILE_DRIVABLE_VALUE
                        ',' -> TILE_GRASS_VALUE
                        else -> error("unexpected glyph '$ch' at ($x,$y) in expected grid")
                    }
            }
        }
        return out
    }

    /**
     * Test 1: per-tile shape lock. Compares the synthesizer's IntArray output against the
     * hand-derived corridor cell-by-cell. PRE-FIX expectation: mismatch_count >= 30 (Plan 33
     * must-haves contract). POST-FIX (Plan 35): mismatch_count <= MAX_MISMATCH_FOR_PASS.
     *
     * D-N-12: the assertion compares INDIVIDUAL cells, NOT totals.
     */
    @Test
    fun racer_waypoints_synthesize_to_corridor_not_arena() {
        val result = TrackSynthesizer.synthesize(racerWaypoints, mapWidth, mapHeight)
        assertEquals(
            mapWidth * mapHeight,
            result.tileData.size,
            "tileData.size must equal mapWidth*mapHeight",
        )
        val expected = expectedTileArray()
        val mismatches =
            mutableListOf<Triple<Int, Int, Pair<Int, Int>>>() // (x, y, actual to expected)
        for (y in 0 until mapHeight) {
            for (x in 0 until mapWidth) {
                val idx = y * mapWidth + x
                val a = result.tileData[idx]
                val e = expected[idx]
                if (a != e) {
                    mismatches.add(Triple(x, y, a to e))
                }
            }
        }
        // Render mismatch report (deterministic for evidence capture).
        val mismatchCount = mismatches.size
        val firstFew =
            mismatches.take(10).joinToString(separator = "; ") { (x, y, ae) ->
                "(${x},${y}) actual=${ae.first} expected=${ae.second}"
            }
        // Stdout for evidence capture by ./gradlew test.
        println("RED_TEST_DIAGNOSTIC racer_waypoints_synthesize_to_corridor_not_arena")
        println("expected_grid_source=07-expected-circuit-tilemap-ascii-art.txt")
        println("mismatch_count=$mismatchCount")
        println("first_mismatches=$firstFew")

        // Side-by-side actual vs expected ASCII art for evidence.
        println("--- actual (synthesizer output) ---")
        printGrid(result.tileData.toIntArray())
        println("--- expected (07-expected-circuit-tilemap-ascii-art.txt) ---")
        printGrid(expected)

        assertTrue(
            mismatchCount <= MAX_MISMATCH_FOR_PASS,
            "TrackSynthesizer corridor shape mismatch_count=$mismatchCount > " +
                "MAX_MISMATCH_FOR_PASS=$MAX_MISMATCH_FOR_PASS (D-N-12 per-cell contract)." +
                " First 10: $firstFew",
        )
    }

    /**
     * Test 2: interior of the polygon (rows 7..13 x cols 7..13) MUST be non-drivable (D-17
     * enclosed-loop invariant — no corner cutting). PRE-FIX expectation: HEAD has 13 drivable cells
     * in this region, so test FAILS.
     */
    @Test
    fun racer_corridor_interior_is_non_drivable() {
        val result = TrackSynthesizer.synthesize(racerWaypoints, mapWidth, mapHeight)
        val drivableInteriorCoords = mutableListOf<Pair<Int, Int>>()
        for (y in 7..13) {
            for (x in 7..13) {
                val idx = y * mapWidth + x
                if (result.tileData[idx] == TILE_DRIVABLE_VALUE) {
                    drivableInteriorCoords.add(x to y)
                }
            }
        }
        println("RED_TEST_DIAGNOSTIC racer_corridor_interior_is_non_drivable")
        println(
            "drivable_interior_count=${drivableInteriorCoords.size} " +
                "coords=${drivableInteriorCoords.joinToString(",")}"
        )
        assertTrue(
            drivableInteriorCoords.isEmpty(),
            "Interior cells (rows 7..13, cols 7..13) MUST NOT be TILE_DRIVABLE " +
                "(D-17 — no corner cutting). Found ${drivableInteriorCoords.size} drivable cells: " +
                drivableInteriorCoords.joinToString(","),
        )
    }

    /**
     * Test 3: checkpoint cells MUST be drivable so the player can drive across them for lap
     * detection. Locks the contract bidirectionally; against HEAD this may already PASS
     * (forceWaypointNeighborhoodsToDrivable forces every waypoint cell to TILE_DRIVABLE), so the
     * assertion is reviewer-evidence rather than a fail-against-HEAD signal.
     */
    @Test
    fun checkpoint_tiles_are_drivable() {
        val result = TrackSynthesizer.synthesize(racerWaypoints, mapWidth, mapHeight)
        val cp1Idx = 5 * mapWidth + 5
        val cp2Idx = 15 * mapWidth + 15
        val cp1 = result.tileData[cp1Idx]
        val cp2 = result.tileData[cp2Idx]
        println("RED_TEST_DIAGNOSTIC checkpoint_tiles_are_drivable")
        println("checkpoint_5_5_tile=$cp1 (expected $TILE_DRIVABLE_VALUE)")
        println("checkpoint_15_15_tile=$cp2 (expected $TILE_DRIVABLE_VALUE)")
        assertEquals(
            TILE_DRIVABLE_VALUE,
            cp1,
            "Checkpoint (5, 5) must be TILE_DRIVABLE for lap detection",
        )
        assertEquals(
            TILE_DRIVABLE_VALUE,
            cp2,
            "Checkpoint (15, 15) must be TILE_DRIVABLE for lap detection",
        )
    }

    /**
     * Diagnostic-only test: prints the actual vs expected grids and the mismatch count to stdout
     * for evidence capture. Always passes — its job is to populate the evidence file even if Test 1
     * fails fast.
     */
    @Test
    fun print_actual_vs_expected_tilemap_diff() {
        val result = TrackSynthesizer.synthesize(racerWaypoints, mapWidth, mapHeight)
        val expected = expectedTileArray()
        var mismatchCount = 0
        for (i in 0 until mapWidth * mapHeight) {
            if (result.tileData[i] != expected[i]) mismatchCount++
        }
        println("RED_TEST_DIAGNOSTIC print_actual_vs_expected_tilemap_diff")
        println("mismatch_count=$mismatchCount")
        println("expected_grid_source=07-expected-circuit-tilemap-ascii-art.txt")
        println("--- actual ---")
        printGrid(result.tileData.toIntArray())
        println("--- expected ---")
        printGrid(expected)
    }

    /** Pretty-print a 19*19 grid using the same glyphs as the evidence file. */
    private fun printGrid(grid: IntArray) {
        val header = "    " + (0 until mapWidth).joinToString("") { (it % 10).toString() }
        println(header)
        println("   +" + "-".repeat(mapWidth))
        for (y in 0 until mapHeight) {
            val row = StringBuilder()
            for (x in 0 until mapWidth) {
                val v = grid[y * mapWidth + x]
                row.append(
                    when (v) {
                        TILE_WALL_VALUE -> '.'
                        TILE_DRIVABLE_VALUE -> '#'
                        TILE_GRASS_VALUE -> ','
                        else -> '?'
                    }
                )
            }
            println("%2d |%s".format(y, row.toString()))
        }
    }

    companion object {
        // Mirror TrackSynthesizer's locked tile-index map. The constants in
        // TrackSynthesizer.kt are `private const`, so they cannot be referenced
        // by name; the value mapping is locked by the header comment in
        // TrackSynthesizer.kt (lines 38-41) AND by the existing
        // TrackSynthesizerTest (uses 0/1 literals).
        private const val TILE_WALL_VALUE = 0
        private const val TILE_DRIVABLE_VALUE = 1
        private const val TILE_GRASS_VALUE = 2

        /**
         * Allowed mismatch_count for the corridor-shape test (Test 1). The RED test asserts <= this
         * value; against HEAD the actual mismatch is 55 so the test FAILS. Plan 35 must bring
         * mismatch_count below 18 (~5% of 361 cells; allows legitimate corridor-width variation).
         */
        private const val MAX_MISMATCH_FOR_PASS = 18
    }
}
