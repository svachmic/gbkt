/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.sport.codegen

import io.github.gbkt.genre.sport.domain.WaypointDef
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * RED diagnostic test for Phase 07.4 round 2 closure — TRACK-NAVIGABILITY gap (VERIFICATION.md
 * lines 240-242). Locks the navigability invariant before Plan 16 ships the fix.
 *
 * Three contract assertions:
 * 1. Every declared waypoint lands on a tile != 0 cell of the synthesized tilemap.
 * 2. Every waypoint's 3x3 tile neighborhood is drivable (sprite-sample-aware).
 * 3. The AI heading + wall-guard logic, simulated in pure Kotlin against the synthesized tilemap,
 *    drives the full waypoint loop within a bounded frame budget.
 *
 * The same three contracts are also asserted against a generic synthetic fixture (Tests 5-7) to
 * enforce D-04 / D-02 — the navigability contract is engine-wide, not Racer-specific.
 *
 * Today (round 2 entry, after Plans 09-13) at least Tests 1, 2, 3 (and their generic variants) are
 * RED. Plan 16 ships the production fix and flips them to GREEN.
 *
 * The simulation in Tests 2 / 7 mirrors the emitted heading + wall-collision write-back logic in
 * SportVisitor.buildAiPoolBodyStatements (lines 1240-1500). The proximity-advance threshold `8`
 * matches the literal at SportVisitor.kt:1477,1479 (`dx_<id> < 8 && dy_<id> < 8`).
 */
class RacingTrackNavigabilityTest {

    // -------------------------------------------------------------------------
    // Tile-index constants. Locked in TrackSynthesizer.kt as `private const val`
    // — copy values here per Plan 04 SUMMARY § Decision 2 (no magic-number drift).
    // Source: TrackSynthesizer.kt lines 39-41.
    // -------------------------------------------------------------------------

    private val TILE_WALL = 0
    private val TILE_DRIVABLE = 1
    @Suppress("unused") private val TILE_GRASS = 2

    // -------------------------------------------------------------------------
    // Racer fixture — exact values from Racer.kt lines 60-110 + the racer's
    // generated `_zone_track1_tiles[361]` array (361 = 19 * 19).
    // -------------------------------------------------------------------------

    private val RACER_WAYPOINTS =
        listOf(
            WaypointDef(tileX = 5, tileY = 5, isCheckpoint = true),
            WaypointDef(tileX = 15, tileY = 5),
            WaypointDef(tileX = 15, tileY = 15, isCheckpoint = true),
            WaypointDef(tileX = 5, tileY = 15),
        )
    private val RACER_MAP_W = 19
    private val RACER_MAP_H = 19
    private val RACER_CORRIDOR_W = 4

    // -------------------------------------------------------------------------
    // Generic synthetic fixture — D-04 / D-02 reflexive guard. The navigability
    // contract is engine-wide, not a Racer-specific assertion.
    // -------------------------------------------------------------------------

    private val GENERIC_WAYPOINTS =
        listOf(
            WaypointDef(tileX = 3, tileY = 3, isCheckpoint = true),
            WaypointDef(tileX = 8, tileY = 3),
            WaypointDef(tileX = 8, tileY = 8, isCheckpoint = true),
            WaypointDef(tileX = 3, tileY = 8),
        )
    private val GENERIC_MAP_W = 12
    private val GENERIC_MAP_H = 12

    // -------------------------------------------------------------------------
    // AI simulation constants — copied from SportVisitor.buildAiPoolBodyStatements.
    // The waypoint-advance threshold is the literal `8` at SportVisitor.kt:1477,1479
    // (`dx_<id> < 8 && dy_<id> < 8`).
    // -------------------------------------------------------------------------

    private val AI_PROXIMITY_PX = 8

    // =========================================================================
    // TEST 1 — Racer waypoints land on drivable tiles
    // =========================================================================

    @Test
    fun `every declared waypoint lands on drivable tile (racer fixture)`() {
        val track =
            TrackSynthesizer.synthesize(
                waypoints = RACER_WAYPOINTS,
                mapWidth = RACER_MAP_W,
                mapHeight = RACER_MAP_H,
                corridorWidth = RACER_CORRIDOR_W,
            )
        for (wp in RACER_WAYPOINTS) {
            val idx = wp.tileY * RACER_MAP_W + wp.tileX
            val tile = track.tileData[idx]
            assertTrue(
                tile != TILE_WALL,
                "Waypoint at tile (${wp.tileX}, ${wp.tileY}) lands on wall (tile=$tile). " +
                    "AI cannot navigate to a wall cell — wall-collision guard rejects all " +
                    "moves into it. Synthesized tile distribution near waypoint: " +
                    debugTileNeighborhood(track.tileData, RACER_MAP_W, wp.tileX, wp.tileY),
            )
        }
    }

    // =========================================================================
    // TEST 2 — AI simulation drives full lap (racer fixture)
    // =========================================================================

    @Test
    fun `ai simulation drives full lap within budget (racer fixture)`() {
        val track =
            TrackSynthesizer.synthesize(
                waypoints = RACER_WAYPOINTS,
                mapWidth = RACER_MAP_W,
                mapHeight = RACER_MAP_H,
                corridorWidth = RACER_CORRIDOR_W,
            )
        val transcript =
            simulateAi(
                tiles = track.tileData,
                mapW = RACER_MAP_W,
                mapH = RACER_MAP_H,
                wpX = RACER_WAYPOINTS.map { it.tileX * 8 },
                wpY = RACER_WAYPOINTS.map { it.tileY * 8 },
                startPx = 80,
                startPy = 96,
                speedCap = (180 * 85) / 100, // ai speed * speedPercent (Racer.kt ai config)
                accel = 150,
                frameBudget = 1500,
                lapsTarget = 3,
            )
        assertTrue(
            transcript.lapsCompleted >= 3,
            "AI did not complete 3 laps in 1500 frames. Final state: " +
                "x=${transcript.finalX}, y=${transcript.finalY}, " +
                "wp_idx=${transcript.finalWpIdx}, lapsCompleted=${transcript.lapsCompleted}, " +
                "frozenAt=(${transcript.frozenAtX}, ${transcript.frozenAtY}), " +
                "frozenForFrames=${transcript.maxFrozenStreak}. " +
                "This matches the runtime evidence in racer_14_stuck_corridor_frame244.png.",
        )
    }

    // =========================================================================
    // TEST 3 — Waypoint 3x3 neighborhood is drivable (racer fixture)
    // =========================================================================

    @Test
    fun `waypoint 3x3 neighborhood is drivable (racer fixture)`() {
        val track =
            TrackSynthesizer.synthesize(
                waypoints = RACER_WAYPOINTS,
                mapWidth = RACER_MAP_W,
                mapHeight = RACER_MAP_H,
                corridorWidth = RACER_CORRIDOR_W,
            )
        for (wp in RACER_WAYPOINTS) {
            for (dy in -1..1) for (dx in -1..1) {
                val nx = wp.tileX + dx
                val ny = wp.tileY + dy
                if (nx < 0 || nx >= RACER_MAP_W || ny < 0 || ny >= RACER_MAP_H) continue
                val tile = track.tileData[ny * RACER_MAP_W + nx]
                assertTrue(
                    tile != TILE_WALL,
                    "Waypoint (${wp.tileX}, ${wp.tileY}) neighbor at " +
                        "(${nx}, ${ny}) is wall (tile=$tile). Sprite-sample-aware " +
                        "approach: AI vehicle's 8x16 sprite samples this cell when its " +
                        "center is at the waypoint, so any neighbor wall blocks traversal.",
                )
            }
        }
    }

    // =========================================================================
    // TEST 4 — Player held-UP regression (Plan 12 closure)
    // =========================================================================

    @Test
    fun `held UP from player start traverses without underflow (regression net)`() {
        val track =
            TrackSynthesizer.synthesize(
                waypoints = RACER_WAYPOINTS,
                mapWidth = RACER_MAP_W,
                mapHeight = RACER_MAP_H,
                corridorWidth = RACER_CORRIDOR_W,
            )
        var px = 80
        var py = 80
        var speedCur = 0
        val accel = 160
        val speedCap = 200
        val accelStep = ((accel shr 4) + 1)
        repeat(60) {
            speedCur = if (speedCur + accelStep < speedCap) speedCur + accelStep else speedCap
            val delta = speedCur shr 5
            val vx = 0
            val vy = -delta
            val propXs = px + vx
            val propYs = py + vy
            val maxX = RACER_MAP_W * 8 - 8
            val maxY = RACER_MAP_H * 8 - 16
            if (propXs in 0 until maxX && propYs in 0 until maxY) {
                val sampleX = propXs + 4
                val sampleY = propYs + 8
                val tileCol = sampleX shr 3
                val tileRow = sampleY shr 3
                if (tileCol < RACER_MAP_W && tileRow < RACER_MAP_H) {
                    val tile = track.tileData[tileRow * RACER_MAP_W + tileCol]
                    if (tile != TILE_WALL) {
                        px = propXs
                        py = propYs
                    }
                }
            }
        }
        assertTrue(py >= 0 && py < RACER_MAP_H * 8, "Player y wrapped/underflowed: py=$py")
        val finalSampleX = px + 4
        val finalSampleY = py + 8
        val finalTile = track.tileData[(finalSampleY shr 3) * RACER_MAP_W + (finalSampleX shr 3)]
        assertTrue(finalTile != TILE_WALL, "Player ended on wall tile: px=$px, py=$py")
    }

    // =========================================================================
    // TESTS 5-7 — Generic-fixture variants (D-04 / D-02 reflexive guard)
    // =========================================================================

    @Test
    fun `every declared waypoint lands on drivable tile (generic fixture)`() {
        val track =
            TrackSynthesizer.synthesize(
                waypoints = GENERIC_WAYPOINTS,
                mapWidth = GENERIC_MAP_W,
                mapHeight = GENERIC_MAP_H,
                corridorWidth = 4,
            )
        for (wp in GENERIC_WAYPOINTS) {
            val tile = track.tileData[wp.tileY * GENERIC_MAP_W + wp.tileX]
            assertTrue(
                tile != TILE_WALL,
                "Generic waypoint (${wp.tileX}, ${wp.tileY}) on wall (tile=$tile)",
            )
        }
    }

    @Test
    fun `waypoint 3x3 neighborhood is drivable (generic fixture)`() {
        val track =
            TrackSynthesizer.synthesize(
                waypoints = GENERIC_WAYPOINTS,
                mapWidth = GENERIC_MAP_W,
                mapHeight = GENERIC_MAP_H,
                corridorWidth = 4,
            )
        for (wp in GENERIC_WAYPOINTS) {
            for (dy in -1..1) for (dx in -1..1) {
                val nx = wp.tileX + dx
                val ny = wp.tileY + dy
                if (nx < 0 || nx >= GENERIC_MAP_W || ny < 0 || ny >= GENERIC_MAP_H) continue
                val tile = track.tileData[ny * GENERIC_MAP_W + nx]
                assertTrue(
                    tile != TILE_WALL,
                    "Generic waypoint (${wp.tileX}, ${wp.tileY}) neighbor (${nx}, ${ny}) " +
                        "is wall (tile=$tile)",
                )
            }
        }
    }

    @Test
    fun `ai simulation drives full lap within budget (generic fixture)`() {
        val track =
            TrackSynthesizer.synthesize(
                waypoints = GENERIC_WAYPOINTS,
                mapWidth = GENERIC_MAP_W,
                mapHeight = GENERIC_MAP_H,
                corridorWidth = 4,
            )
        val transcript =
            simulateAi(
                tiles = track.tileData,
                mapW = GENERIC_MAP_W,
                mapH = GENERIC_MAP_H,
                wpX = GENERIC_WAYPOINTS.map { it.tileX * 8 },
                wpY = GENERIC_WAYPOINTS.map { it.tileY * 8 },
                startPx = 40,
                startPy = 40,
                speedCap = 153,
                accel = 150,
                frameBudget = 1500,
                lapsTarget = 3,
            )
        assertTrue(
            transcript.lapsCompleted >= 3,
            "Generic AI did not complete 3 laps. Final wp_idx=${transcript.finalWpIdx}, " +
                "lapsCompleted=${transcript.lapsCompleted}",
        )
    }

    // =========================================================================
    // TESTS 8-9 — Smaller polygons (smallest enclosed shapes)
    // =========================================================================

    @Test
    fun `triangle polygon waypoints land on drivable tiles`() {
        val triangle =
            listOf(
                WaypointDef(tileX = 2, tileY = 2, isCheckpoint = true),
                WaypointDef(tileX = 10, tileY = 2),
                WaypointDef(tileX = 6, tileY = 10, isCheckpoint = true),
            )
        val track =
            TrackSynthesizer.synthesize(
                waypoints = triangle,
                mapWidth = 12,
                mapHeight = 12,
                corridorWidth = 4,
            )
        for (wp in triangle) {
            val tile = track.tileData[wp.tileY * 12 + wp.tileX]
            assertTrue(tile != TILE_WALL, "Triangle waypoint (${wp.tileX}, ${wp.tileY}) on wall")
        }
    }

    @Test
    fun `pentagon polygon waypoints land on drivable tiles`() {
        // Pentagon — slightly off-axis to exercise scan-line edge cases.
        val pentagon =
            listOf(
                WaypointDef(tileX = 7, tileY = 2, isCheckpoint = true),
                WaypointDef(tileX = 12, tileY = 6),
                WaypointDef(tileX = 10, tileY = 12, isCheckpoint = true),
                WaypointDef(tileX = 4, tileY = 12),
                WaypointDef(tileX = 2, tileY = 6),
            )
        val track =
            TrackSynthesizer.synthesize(
                waypoints = pentagon,
                mapWidth = 16,
                mapHeight = 16,
                corridorWidth = 4,
            )
        for (wp in pentagon) {
            val tile = track.tileData[wp.tileY * 16 + wp.tileX]
            assertTrue(tile != TILE_WALL, "Pentagon waypoint (${wp.tileX}, ${wp.tileY}) on wall")
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private data class AiTranscript(
        val finalX: Int,
        val finalY: Int,
        val finalWpIdx: Int,
        val lapsCompleted: Int,
        val frozenAtX: Int,
        val frozenAtY: Int,
        val maxFrozenStreak: Int,
    )

    @Suppress("LongParameterList", "ComplexMethod", "LongMethod", "NestedBlockDepth")
    private fun simulateAi(
        tiles: List<Int>,
        mapW: Int,
        mapH: Int,
        wpX: List<Int>,
        wpY: List<Int>,
        startPx: Int,
        startPy: Int,
        speedCap: Int,
        accel: Int,
        frameBudget: Int,
        lapsTarget: Int,
    ): AiTranscript {
        var px = startPx
        var py = startPy
        var wpIdx = 0
        var speedCur = 0
        var lapsCompleted = 0
        var prevPx = px
        var prevPy = py
        var frozenStreak = 0
        var maxFrozenStreak = 0
        var frozenAtX = px
        var frozenAtY = py
        var prevHeading = 0 // C-side default: zero-initialized UINT8 array.
        val accelStep = ((accel shr 4) + 1)

        // Mirror of SportVisitor.buildPositionWriteBackWithCollision bounds (Plan 12)
        // and buildAiHeadingPickWithFallback probe (Plan 16).
        val maxX = mapW * 8 - 8
        val maxY = mapH * 8 - 16

        // Sample a single cardinal probe at heading `dir` from (cx, cy) using `delta`.
        // Returns true if the proposed sample is wall (or out of bounds — counts as wall).
        // Mirrors the inlined emitted C exactly.
        fun probeBlocked(cx: Int, cy: Int, dir: Int, delta: Int): Boolean {
            val pvx =
                when (dir) {
                    1 -> delta
                    3 -> -delta
                    else -> 0
                }
            val pvy =
                when (dir) {
                    0 -> -delta
                    2 -> delta
                    else -> 0
                }
            val pXs = cx + pvx
            val pYs = cy + pvy
            if (pXs !in 0 until maxX || pYs !in 0 until maxY) return true
            val sX = pXs + 4
            val sY = pYs + 8
            val tCol = sX shr 3
            val tRow = sY shr 3
            if (tCol < 0 || tCol >= mapW || tRow < 0 || tRow >= mapH) return true
            return tiles[tRow * mapW + tCol] == TILE_WALL
        }

        for (frame in 0 until frameBudget) {
            val tgtX = wpX[wpIdx]
            val tgtY = wpY[wpIdx]
            val dx = if (px > tgtX) px - tgtX else tgtX - px
            val dy = if (py > tgtY) py - tgtY else tgtY - py

            // 3-LEVEL wall-aware heading pick — mirrors buildAiHeadingPickWithFallback.
            val primary: Int
            val fallback: Int
            if (dx >= dy) {
                primary = if (px < tgtX) 1 else 3
                fallback = if (py < tgtY) 2 else 0
            } else {
                primary = if (py < tgtY) 2 else 0
                fallback = if (px < tgtX) 1 else 3
            }

            val deltaProbe = (speedCur shr 5).coerceAtLeast(1)
            val blocked = IntArray(4) { if (probeBlocked(px, py, it, deltaProbe)) 1 else 0 }

            // Prev-perpendicular-commit: when primary blocked, prefer prev heading IF
            // perpendicular to primary AND unblocked. Breaks oscillation along narrow
            // corridors. Mirrors SportVisitor.buildAiHeadingPickWithFallback. The axis
            // bit is bit-0 (N=0/S=2 vertical, E=1/W=3 horizontal).
            val prevIsPerp = (prevHeading and 1) != (primary and 1)
            val heading =
                when {
                    blocked[primary] == 0 -> primary
                    prevIsPerp && blocked[prevHeading] == 0 -> prevHeading
                    blocked[fallback] == 0 -> fallback
                    else -> {
                        var t = -1
                        for (cd in 0..3) {
                            if (cd == primary || cd == fallback) continue
                            if (blocked[cd] == 0) {
                                t = cd
                                break
                            }
                        }
                        if (t >= 0) t else prevHeading // stay put — keep prev heading.
                    }
                }
            prevHeading = heading

            speedCur = if (speedCur + accelStep < speedCap) speedCur + accelStep else speedCap
            val delta = speedCur shr 5
            val vx =
                when (heading) {
                    1 -> delta
                    3 -> -delta
                    else -> 0
                }
            val vy =
                when (heading) {
                    0 -> -delta
                    2 -> delta
                    else -> 0
                }

            val propXs = px + vx
            val propYs = py + vy
            if (propXs in 0 until maxX && propYs in 0 until maxY) {
                val sampleX = propXs + 4
                val sampleY = propYs + 8
                val tileCol = sampleX shr 3
                val tileRow = sampleY shr 3
                if (tileCol < mapW && tileRow < mapH) {
                    val tile = tiles[tileRow * mapW + tileCol]
                    if (tile != TILE_WALL) {
                        px = propXs
                        py = propYs
                    }
                }
            }

            // Waypoint advance — mirrors SportVisitor.kt (`dx < 8 && dy < 8`).
            if (dx <= AI_PROXIMITY_PX && dy <= AI_PROXIMITY_PX) {
                wpIdx = (wpIdx + 1) % wpX.size
                if (wpIdx == 0) lapsCompleted += 1
                if (lapsCompleted >= lapsTarget) {
                    return AiTranscript(
                        finalX = px,
                        finalY = py,
                        finalWpIdx = wpIdx,
                        lapsCompleted = lapsCompleted,
                        frozenAtX = frozenAtX,
                        frozenAtY = frozenAtY,
                        maxFrozenStreak = maxFrozenStreak,
                    )
                }
            }

            if (px == prevPx && py == prevPy) {
                frozenStreak += 1
                if (frozenStreak > maxFrozenStreak) {
                    maxFrozenStreak = frozenStreak
                    frozenAtX = px
                    frozenAtY = py
                }
            } else {
                frozenStreak = 0
            }
            prevPx = px
            prevPy = py
        }

        return AiTranscript(
            finalX = px,
            finalY = py,
            finalWpIdx = wpIdx,
            lapsCompleted = lapsCompleted,
            frozenAtX = frozenAtX,
            frozenAtY = frozenAtY,
            maxFrozenStreak = maxFrozenStreak,
        )
    }

    private fun debugTileNeighborhood(tiles: List<Int>, mapW: Int, cx: Int, cy: Int): String {
        val sb = StringBuilder()
        for (dy in -2..2) {
            for (dx in -2..2) {
                val nx = cx + dx
                val ny = cy + dy
                if (nx < 0 || ny < 0 || nx >= mapW || ny * mapW + nx >= tiles.size) {
                    sb.append("? ")
                } else {
                    sb.append("${tiles[ny * mapW + nx]} ")
                }
            }
            sb.append("| ")
        }
        return sb.toString()
    }
}
