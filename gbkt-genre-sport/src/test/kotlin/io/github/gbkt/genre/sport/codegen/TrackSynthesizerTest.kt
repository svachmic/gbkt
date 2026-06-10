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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// =============================================================================
// Plan 04 (Wave 2) GREEN — implementation of the Wave 0 contract pinned by
// 07.4-01-SUMMARY.md.
//
// VALIDATION.md row: 01-T2.
// Covers: D-10 (polygon non-degenerate), D-11 (corridor / wall semantics),
// D-17 enclosure invariant ("a lap means a real lap"), and the RESEARCH.md
// R4 counter-tests (parity-bug signature; collinear / degenerate polygons
// rejected).
//
// Tile-index map (locked in TrackSynthesizer.kt):
//   0 = wall
//   1 = drivable (corridor near boundary)
//   2 = grass (inside but far from boundary)
// =============================================================================

class TrackSynthesizerTest {

    private val mapWidth = 20
    private val mapHeight = 20

    /** Canonical square polygon: 4 vertices on a 20x20 tilemap. */
    private val square =
        listOf(
            WaypointDef(tileX = 5, tileY = 5),
            WaypointDef(tileX = 15, tileY = 5),
            WaypointDef(tileX = 15, tileY = 15),
            WaypointDef(tileX = 5, tileY = 15),
        )

    /** Smallest valid enclosed polygon — 3 non-collinear vertices. */
    private val triangle =
        listOf(
            WaypointDef(tileX = 5, tileY = 5),
            WaypointDef(tileX = 15, tileY = 5),
            WaypointDef(tileX = 10, tileY = 15),
        )

    /** Non-convex pentagon — exercises edge-pairing logic on concave shapes. */
    private val pentagon =
        listOf(
            WaypointDef(tileX = 5, tileY = 5),
            WaypointDef(tileX = 15, tileY = 5),
            WaypointDef(tileX = 15, tileY = 15),
            WaypointDef(tileX = 10, tileY = 10), // concave dent
            WaypointDef(tileX = 5, tileY = 15),
        )

    // -----------------------------------------------------------------------
    // Square polygon — full invariant set.
    // -----------------------------------------------------------------------

    @Test
    fun square_polygon_produces_full_tile_area() {
        val result = TrackSynthesizer.synthesize(square, mapWidth, mapHeight)
        assertEquals(
            mapWidth * mapHeight,
            result.tileData.size,
            "tileData.size must equal mapWidth * mapHeight (D-10 SC-4)",
        )
    }

    @Test
    fun square_polygon_has_drivable_and_wall_tiles() {
        val result = TrackSynthesizer.synthesize(square, mapWidth, mapHeight)
        val drivable = result.tileData.count { it == 1 }
        val wall = result.tileData.count { it == 0 }
        assertTrue(drivable > 0, "synthesized track must contain drivable tiles (D-11)")
        assertTrue(wall > 0, "synthesized track must contain wall tiles (D-11)")
    }

    @Test
    fun square_polygon_perimeter_is_walls() {
        val result = TrackSynthesizer.synthesize(square, mapWidth, mapHeight)
        assertPerimeterIsWalls(result.tileData, mapWidth, mapHeight, "square")
    }

    @Test
    fun square_polygon_has_no_checkerboard_pattern() {
        val result = TrackSynthesizer.synthesize(square, mapWidth, mapHeight)
        assertNoCheckerboard(result.tileData, mapWidth, mapHeight, "square")
    }

    // -----------------------------------------------------------------------
    // Triangle polygon — invariants (size, both tile types, perimeter, no
    // checkerboard).
    // -----------------------------------------------------------------------

    @Test
    fun triangle_polygon_produces_valid_track() {
        val result = TrackSynthesizer.synthesize(triangle, mapWidth, mapHeight)
        assertEquals(mapWidth * mapHeight, result.tileData.size, "triangle tileData size")
        assertTrue(result.tileData.count { it == 1 } > 0, "triangle drivable count")
        assertTrue(result.tileData.count { it == 0 } > 0, "triangle wall count")
        assertPerimeterIsWalls(result.tileData, mapWidth, mapHeight, "triangle")
        assertNoCheckerboard(result.tileData, mapWidth, mapHeight, "triangle")
    }

    // -----------------------------------------------------------------------
    // Pentagon polygon — concave shape stress test.
    // -----------------------------------------------------------------------

    @Test
    fun pentagon_polygon_produces_valid_track() {
        val result = TrackSynthesizer.synthesize(pentagon, mapWidth, mapHeight)
        assertEquals(mapWidth * mapHeight, result.tileData.size, "pentagon tileData size")
        assertTrue(result.tileData.count { it == 1 } > 0, "pentagon drivable count")
        assertTrue(result.tileData.count { it == 0 } > 0, "pentagon wall count")
        assertPerimeterIsWalls(result.tileData, mapWidth, mapHeight, "pentagon")
        assertNoCheckerboard(result.tileData, mapWidth, mapHeight, "pentagon")
    }

    // -----------------------------------------------------------------------
    // Degenerate-input contract — IllegalArgumentException (Plan 01 decision).
    // -----------------------------------------------------------------------

    @Test
    fun degenerate_two_waypoint_polygon_throws_or_returns_empty() {
        val twoPoints =
            listOf(WaypointDef(tileX = 5, tileY = 5), WaypointDef(tileX = 15, tileY = 5))
        val ex =
            assertFailsWith<IllegalArgumentException> {
                TrackSynthesizer.synthesize(twoPoints, mapWidth, mapHeight)
            }
        assertTrue(
            ex.message?.contains("waypoints") == true,
            "exception must name the polygon as degenerate; got: ${ex.message}",
        )
    }

    @Test
    fun collinear_waypoints_throw_or_return_empty() {
        val collinear =
            listOf(
                WaypointDef(tileX = 5, tileY = 5),
                WaypointDef(tileX = 10, tileY = 5),
                WaypointDef(tileX = 15, tileY = 5),
            )
        val ex =
            assertFailsWith<IllegalArgumentException> {
                TrackSynthesizer.synthesize(collinear, mapWidth, mapHeight)
            }
        assertTrue(
            ex.message?.contains("collinear") == true,
            "exception must explain collinearity; got: ${ex.message}",
        )
    }

    // -----------------------------------------------------------------------
    // Helpers.
    // -----------------------------------------------------------------------

    private fun assertPerimeterIsWalls(
        tileData: List<Int>,
        width: Int,
        height: Int,
        fixture: String,
    ) {
        for (x in 0 until width) {
            assertEquals(0, tileData[x], "$fixture: row 0 col $x must be wall (D-17 enclosure)")
            assertEquals(
                0,
                tileData[(height - 1) * width + x],
                "$fixture: last row col $x must be wall (D-17 enclosure)",
            )
        }
        for (y in 0 until height) {
            assertEquals(
                0,
                tileData[y * width],
                "$fixture: col 0 row $y must be wall (D-17 enclosure)",
            )
            assertEquals(
                0,
                tileData[y * width + (width - 1)],
                "$fixture: last col row $y must be wall (D-17 enclosure)",
            )
        }
    }

    /**
     * Counter-test for RESEARCH Pitfall 5: a buggy scan-line fill that toggles parity on every
     * pixel produces visible checkerboard artifacts. We assert that no row contains a 5+ contiguous
     * alternating 0,1,0,1,0 segment.
     */
    private fun assertNoCheckerboard(
        tileData: List<Int>,
        width: Int,
        height: Int,
        fixture: String,
    ) {
        for (y in 0 until height) {
            // Slide a 5-wide window across this row.
            for (xStart in 0..(width - 5)) {
                val a = tileData[y * width + xStart]
                val b = tileData[y * width + xStart + 1]
                val c = tileData[y * width + xStart + 2]
                val d = tileData[y * width + xStart + 3]
                val e = tileData[y * width + xStart + 4]
                val isParityBug =
                    (a == 0 && b == 1 && c == 0 && d == 1 && e == 0) ||
                        (a == 1 && b == 0 && c == 1 && d == 0 && e == 1)
                assertTrue(
                    !isParityBug,
                    "$fixture row $y starting at col $xStart shows checkerboard parity-bug" +
                        " segment ($a,$b,$c,$d,$e) — RESEARCH Pitfall 5 counter-test",
                )
            }
        }
    }
}
