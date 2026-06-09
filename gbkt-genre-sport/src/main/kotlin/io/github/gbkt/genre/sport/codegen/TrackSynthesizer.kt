/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.sport.codegen

import io.github.gbkt.genre.sport.domain.WaypointDef
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// =============================================================================
// TRACK SYNTHESIZER — polygon → tilemap rasterization (Phase 07.4 Plan 04).
// =============================================================================
//
// Algorithm: standard scan-line polygon fill (with Pitfall 5 fixes — half-open
// y-interval, skip horizontal edges) followed by a corridor-erosion pass that
// classifies inside cells by distance to nearest polygon edge (Reading B per
// RESEARCH.md "Polygon → Tilemap Rasterization").
//
// Output: a List<Int> of length mapWidth * mapHeight (row-major, idx = y*W + x)
// with three locked tile indices:
//   0 = wall      (outside polygon)
//   1 = drivable  (inside AND within corridorWidth/2 of any polygon edge)
//   2 = grass     (inside AND far from edge)
//
// The output is fed into ZoneIR.tileData by RacingDelegate (Plan 03), and is
// consumed unchanged by GBDKPipeline.buildZoneData (lines 677–703 of
// gbkt-backend-gbdk's pipeline) which casts each entry to a UINT8 byte. Tile-
// index constants are LOCKED here so SportVisitor (Plan 05) can rely on them.
//
// D-12 enforcement (skip-if-already-populated-zone) lives in RacingDelegate,
// not here. TrackSynthesizer.synthesize() is unconditional when called.
// =============================================================================

/** Constants for synthesizer tile indices. Locked for downstream codegen consumers. */
private const val TILE_WALL = 0
private const val TILE_DRIVABLE = 1
private const val TILE_GRASS = 2

/** RESEARCH A5 — bound vertex count to keep the edge table on the stack. */
private const val MAX_POLYGON_VERTICES = 16

/**
 * 3x3 tile neighborhood radius around each waypoint forced to drivable. Matches the AI
 * sample-center heuristic: sprite half-extents (4 px x 8 px) plus a 1-tile margin. With
 * `corridorWidth = 4` (D-11) the corridor band is wide enough for the sprite at waypoint cells and
 * the 3x3 force is therefore well within the corridor for interior waypoints; for waypoints near
 * the perimeter, the force-pass overrides the perimeter wall (D-17 — the polygon vertex must be
 * reachable, otherwise lap completion is impossible).
 *
 * Phase 07.4-16 — TRACK-NAVIGABILITY closure.
 */
private const val WAYPOINT_FORCE_RADIUS = 1

/**
 * Polygon-to-tilemap rasterizer.
 *
 * @see synthesize for the public API.
 */
internal object TrackSynthesizer {

    /**
     * Synthesize a track tilemap from a waypoint polygon.
     *
     * @param waypoints Ordered polygon vertices (size in 3..16, non-coincident, non-collinear).
     * @param mapWidth Tilemap width in tiles (> 0).
     * @param mapHeight Tilemap height in tiles (> 0).
     * @param corridorWidth Drivable-corridor width in tiles. Default 4 (per D-11 / RESEARCH).
     * @return [SynthesizedTrack] with [SynthesizedTrack.tileData] of size mapWidth * mapHeight,
     *   row-major, values in {0, 1, 2}, perimeter forced to 0 (D-17 enclosure invariant).
     * @throws IllegalArgumentException on degenerate inputs.
     */
    fun synthesize(
        waypoints: List<WaypointDef>,
        mapWidth: Int,
        mapHeight: Int,
        corridorWidth: Int = 4,
    ): SynthesizedTrack {
        validate(waypoints, mapWidth, mapHeight)
        val inside = scanlineFill(waypoints, mapWidth, mapHeight)
        if (inside.none { row -> row.any { it } }) {
            // Defensive: validate() should have caught this, but a degenerate
            // polygon may still slip through if the cross-product test
            // bottoms out on numerical edge cases. The synthesizer refuses to
            // emit an all-walls tilemap silently.
            require(false) {
                "polygon encloses zero tile area — waypoints define a degenerate shape"
            }
        }
        val corridor = buildCorridorMask(waypoints, mapWidth, mapHeight, corridorWidth)
        val tileData = composeTiles(inside, corridor, mapWidth, mapHeight)
        forcePerimeterToWalls(tileData, mapWidth, mapHeight)
        forceWaypointNeighborhoodsToDrivable(tileData, mapWidth, mapHeight, waypoints)
        return SynthesizedTrack(
            tileData = tileData.toList(),
            collisionData = List(mapWidth * mapHeight) { 0 },
        )
    }

    // -------------------------------------------------------------------------
    // VALIDATE — D-10 non-degeneracy guard. RacingValidationPass (Plan 06)
    // catches the same conditions earlier in the pipeline; this is the
    // direct-misuse defense.
    // -------------------------------------------------------------------------

    private fun validate(waypoints: List<WaypointDef>, mapWidth: Int, mapHeight: Int) {
        require(waypoints.size >= 3) { "polygon requires >= 3 waypoints, got ${waypoints.size}" }
        require(waypoints.size <= MAX_POLYGON_VERTICES) {
            "polygon vertex count ${waypoints.size} exceeds max $MAX_POLYGON_VERTICES " +
                "(RESEARCH A5 stack-bound)"
        }
        require(mapWidth > 0 && mapHeight > 0) {
            "mapWidth ($mapWidth) and mapHeight ($mapHeight) must be > 0"
        }
        // Coincident: fewer than 3 distinct (tileX, tileY) pairs.
        val distinctPositions = waypoints.map { it.tileX to it.tileY }.toSet()
        require(distinctPositions.size >= 3) {
            "polygon waypoints are coincident — distinct vertices required " +
                "(got ${distinctPositions.size} distinct positions in ${waypoints.size} waypoints)"
        }
        require(!areCollinear(waypoints)) {
            "polygon waypoints are collinear — non-degenerate area required"
        }
    }

    /**
     * Cross-product collinearity test. Returns true iff every triple (w0, w1, w[i]) for i >= 2 has
     * zero cross product, i.e. all points lie on the same line through (w0, w1).
     *
     * Skips leading duplicates so (w0, w1) is the first distinct pair.
     */
    private fun areCollinear(waypoints: List<WaypointDef>): Boolean {
        // Find first two distinct points.
        val w0 = waypoints[0]
        var w1Idx = 1
        while (
            w1Idx < waypoints.size &&
                waypoints[w1Idx].tileX == w0.tileX &&
                waypoints[w1Idx].tileY == w0.tileY
        ) {
            w1Idx++
        }
        if (w1Idx >= waypoints.size) return true // all coincident → vacuously collinear
        val w1 = waypoints[w1Idx]
        // Every remaining point must be collinear with (w0, w1).
        for (i in 0 until waypoints.size) {
            if (i == 0 || i == w1Idx) continue
            val wi = waypoints[i]
            val cross =
                (w1.tileX - w0.tileX) * (wi.tileY - w0.tileY) -
                    (w1.tileY - w0.tileY) * (wi.tileX - w0.tileX)
            if (cross != 0) return false
        }
        return true
    }

    // -------------------------------------------------------------------------
    // SCAN-LINE FILL — RESEARCH "Polygon → Tilemap Rasterization" + Pitfall 5
    // fixes (half-open y-interval; horizontal edges skipped).
    // -------------------------------------------------------------------------

    private fun scanlineFill(
        waypoints: List<WaypointDef>,
        mapWidth: Int,
        mapHeight: Int,
    ): Array<BooleanArray> {
        val inside = Array(mapHeight) { BooleanArray(mapWidth) }
        val n = waypoints.size
        for (y in 0 until mapHeight) {
            val intersections = mutableListOf<Int>()
            for (i in 0 until n) {
                val a = waypoints[i]
                val b = waypoints[(i + 1) % n]
                val y0 = a.tileY
                val y1 = b.tileY
                val x0 = a.tileX
                val x1 = b.tileX
                // Pitfall 5 fix #2: skip horizontal edges entirely.
                if (y0 == y1) continue
                val ymin = min(y0, y1)
                val ymax = max(y0, y1)
                // Pitfall 5 fix #1: HALF-open interval ymin <= y < ymax. The
                // upper-y vertex does NOT count for that edge's y-row, so a
                // shared vertex contributes exactly one intersection across
                // its two adjoining edges (avoids double-counting).
                if (y < ymin || y >= ymax) continue
                // Integer-rational x-intersection: x = x0 + (x1-x0)*(y-y0)/(y1-y0).
                // Use Int math; the division is exact only when (x1-x0)*(y-y0)
                // is divisible by (y1-y0), but we want the floor for fill
                // purposes. Java's Int division truncates toward zero — for
                // positive denominators this matches floor when the dividend
                // is positive; we use Math.floorDiv to be safe across signs.
                val dx = x1 - x0
                val dy = y1 - y0
                val xIntersect = x0 + Math.floorDiv(dx * (y - y0), dy)
                intersections += xIntersect
            }
            if (intersections.size < 2) continue
            intersections.sort()
            // Even-odd fill between successive pairs. Drop the trailing odd
            // intersection if any (numerical edge case).
            val pairCount = intersections.size and 1.inv()
            var k = 0
            while (k < pairCount) {
                val xStart = max(0, intersections[k])
                val xEnd = min(mapWidth, intersections[k + 1])
                var x = xStart
                while (x < xEnd) {
                    inside[y][x] = true
                    x++
                }
                k += 2
            }
        }
        return inside
    }

    // -------------------------------------------------------------------------
    // CORRIDOR MASK — rasterize each polygon edge into a cell mask (Bresenham),
    // then thicken by a Chebyshev neighborhood of radius halfPerp. The result
    // is the set of cells that hug the polygon outline: a 3-tile-thick band
    // straddling every edge for corridorWidth=4 (D-11). Closes Plan 07.4-33
    // RED contract — pre-fix the synthesizer only marked INSIDE cells and
    // eroded inward, producing an arena instead of a circuit corridor.
    // -------------------------------------------------------------------------

    private fun buildCorridorMask(
        waypoints: List<WaypointDef>,
        mapWidth: Int,
        mapHeight: Int,
        corridorWidth: Int,
    ): Array<BooleanArray> {
        val corridor = Array(mapHeight) { BooleanArray(mapWidth) }
        // halfPerp gives a (2 * halfPerp + 1)-cell-thick corridor. For
        // corridorWidth=4 the contract is 3-tile-thick (D-11) → halfPerp=1.
        val halfPerp = max(1, (corridorWidth - 1) / 2)
        val n = waypoints.size
        for (i in 0 until n) {
            val a = waypoints[i]
            val b = waypoints[(i + 1) % n]
            stampThickenedEdge(corridor, mapWidth, mapHeight, a, b, halfPerp)
        }
        return corridor
    }

    /**
     * Rasterize the edge (a, b) using Bresenham, then stamp each rasterized cell with its Chebyshev
     * neighborhood of radius [halfPerp] into [corridor]. Cells outside the map bounds are silently
     * skipped.
     */
    private fun stampThickenedEdge(
        corridor: Array<BooleanArray>,
        mapWidth: Int,
        mapHeight: Int,
        a: WaypointDef,
        b: WaypointDef,
        halfPerp: Int,
    ) {
        var x = a.tileX
        var y = a.tileY
        val x1 = b.tileX
        val y1 = b.tileY
        val dx = abs(x1 - x)
        val dy = abs(y1 - y)
        val sx = if (x < x1) 1 else -1
        val sy = if (y < y1) 1 else -1
        var err = dx - dy
        while (true) {
            stampNeighborhood(corridor, mapWidth, mapHeight, x, y, halfPerp)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                x += sx
            }
            if (e2 < dx) {
                err += dx
                y += sy
            }
        }
    }

    private fun stampNeighborhood(
        corridor: Array<BooleanArray>,
        mapWidth: Int,
        mapHeight: Int,
        cx: Int,
        cy: Int,
        halfPerp: Int,
    ) {
        for (dy in -halfPerp..halfPerp) {
            val ny = cy + dy
            if (ny < 0 || ny >= mapHeight) continue
            for (dx in -halfPerp..halfPerp) {
                val nx = cx + dx
                if (nx < 0 || nx >= mapWidth) continue
                corridor[ny][nx] = true
            }
        }
    }

    // -------------------------------------------------------------------------
    // COMPOSE — combine inside-mask and corridor-mask into the locked
    // tile-index map (0=wall, 1=drivable, 2=grass).
    //
    // Drivable iff in corridor mask (straddles polygon edge per D-11).
    // Grass    iff inside polygon AND not in corridor (interior far from edge
    //          per D-17 — no corner-cutting; player must drive the declared
    //          loop, can't shortcut across the middle).
    // Wall     otherwise (outside polygon AND not in corridor band).
    // -------------------------------------------------------------------------

    private fun composeTiles(
        inside: Array<BooleanArray>,
        corridor: Array<BooleanArray>,
        mapWidth: Int,
        mapHeight: Int,
    ): IntArray {
        val tiles = IntArray(mapWidth * mapHeight)
        for (y in 0 until mapHeight) {
            for (x in 0 until mapWidth) {
                val idx = y * mapWidth + x
                tiles[idx] =
                    when {
                        corridor[y][x] -> TILE_DRIVABLE
                        inside[y][x] -> TILE_GRASS
                        else -> TILE_WALL
                    }
            }
        }
        return tiles
    }

    // -------------------------------------------------------------------------
    // PERIMETER — D-17 enclosure invariant. Force the outer ring to walls so
    // the camera never scrolls into uninitialized BG.
    // -------------------------------------------------------------------------

    private fun forcePerimeterToWalls(tiles: IntArray, mapWidth: Int, mapHeight: Int) {
        // Top + bottom rows.
        for (x in 0 until mapWidth) {
            tiles[x] = TILE_WALL
            tiles[(mapHeight - 1) * mapWidth + x] = TILE_WALL
        }
        // Left + right columns.
        for (y in 0 until mapHeight) {
            tiles[y * mapWidth] = TILE_WALL
            tiles[y * mapWidth + (mapWidth - 1)] = TILE_WALL
        }
    }

    // -------------------------------------------------------------------------
    // WAYPOINT-FORCE — Phase 07.4 round-2 TRACK-NAVIGABILITY closure (Plan 16).
    // Force every waypoint cell AND its WAYPOINT_FORCE_RADIUS-tile neighborhood
    // to TILE_DRIVABLE. Runs AFTER forcePerimeterToWalls; if a waypoint or its
    // neighborhood lands on the perimeter, the waypoint wins (otherwise the
    // polygon vertex is unreachable, which contradicts D-17 — a lap means a
    // real lap, the player must be able to drive the declared loop).
    //
    // Why a 3x3 force radius: the AI's wall-collision-guard samples at
    // (propX + spriteHalfW, propY + spriteHalfH) — the sprite center. The
    // 8x16 vehicle sprite samples a tile up to 1 cell away from its top-left
    // corner. Forcing the 1-tile radius around each waypoint ensures the
    // sprite-center sample lands on a drivable cell as the AI approaches the
    // waypoint, regardless of which corner of its bounding box leads.
    //
    // Closes the (a) and (b) root-cause hypotheses of Plan 15's RED diagnostic
    // (waypoint-on-wall + 3x3-neighborhood sample-mismatch). The (c) heading
    // hypothesis closes in SportVisitor's 3-level wall-aware pick (Task 2).
    // -------------------------------------------------------------------------

    /**
     * Force every waypoint cell AND its WAYPOINT_FORCE_RADIUS-tile neighborhood to TILE_DRIVABLE.
     * This pass runs AFTER forcePerimeterToWalls; if a waypoint or its neighborhood lands on the
     * perimeter, the waypoint wins.
     *
     * Closes Phase 07.4 round-2 TRACK-NAVIGABILITY gap (VERIFICATION.md lines 240-242): the AI's
     * sample-center sprite-extent heuristic was sampling cells classified as wall by the
     * corridor-erosion pass even though the waypoint cell itself was drivable — leading to (56,
     * 38)-frozen-rival symptom. Forcing the 3x3 neighborhood resolves this by construction.
     *
     * @param tileData Row-major tile array (mutated in place).
     * @param mapWidth Map width in tiles.
     * @param mapHeight Map height in tiles.
     * @param waypoints Declared waypoints.
     */
    private fun forceWaypointNeighborhoodsToDrivable(
        tileData: IntArray,
        mapWidth: Int,
        mapHeight: Int,
        waypoints: List<WaypointDef>,
    ) {
        for (wp in waypoints) {
            for (dy in -WAYPOINT_FORCE_RADIUS..WAYPOINT_FORCE_RADIUS) {
                for (dx in -WAYPOINT_FORCE_RADIUS..WAYPOINT_FORCE_RADIUS) {
                    val nx = wp.tileX + dx
                    val ny = wp.tileY + dy
                    if (nx < 0 || nx >= mapWidth || ny < 0 || ny >= mapHeight) continue
                    tileData[ny * mapWidth + nx] = TILE_DRIVABLE
                }
            }
        }
    }
}

/**
 * Result of polygon → tilemap synthesis.
 *
 * @property tileData Row-major tile indices of length mapWidth * mapHeight. Values are in {0=wall,
 *   1=drivable, 2=grass}. Consumed by GBDKPipeline.buildZoneData without further transformation
 *   (cast to UINT8 there).
 * @property collisionData Optional second-layer collision data. In v1 this is filled with zeros at
 *   the same shape; reserved for future use (e.g., per-tile surface-friction modifiers).
 */
data class SynthesizedTrack(val tileData: List<Int>, val collisionData: List<Int>)
