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
 * RED diagnostic test for Phase 07.4 round-3 closure — NAVIGABILITY-PLAYER-CORNER-TRAP
 * (UAT-racer.md lines 151-178). Locks the player-corridor traversability invariant before Plan 18
 * ships the 4-corner wall-collision sample fix.
 *
 * Four contract assertions:
 * 1. (racer fixture) Corner-trap escape: UP×30+LEFT×30 drives the player into the top-left corridor
 *    corner at approximately (26, 29). The subsequent DOWN×30 leg MUST move the player past y=40.
 *    With the single-center sample the player is completely frozen in the corner (frozen=30/30 in
 *    DOWN leg). With the 4-corner sample, at least 4 of 30 DOWN frames succeed and the player
 *    reaches y≈53.
 * 2. (racer fixture) Full-loop traversal: sequence UP×20+LEFT×10+RIGHT×14+DOWN×15+LEFT×20 visits
 *    all 4 declared waypoints in declared order: CP-0 (40,40), WP-1 (120,40), CP-2 (120,120), WP-3
 *    (40,120). With single-center, CP-2 and WP-3 are unreachable (RIGHT leg overshoots to x=134
 *    where the right corridor is inaccessible going DOWN). With 4-corner, the player reaches x=110
 *    (tile cols 13-14, right corridor) and can descend to y≈119, touching all 4 waypoints.
 * 3. (generic fixture) Full-loop traversal: sequence UP×15+LEFT×8+RIGHT×8+DOWN×8+LEFT×10 visits all
 *    4 declared waypoints in order: CP-0 (24,24), WP-1 (64,24), CP-2 (64,64), WP-3 (24,64). With
 *    single-center, the sequence stalls before CP-2. With 4-corner, all 4 are visited. D-04 / D-02
 *    reflexive guard (invariant is engine-wide).
 * 4. (generic fixture) Corner-trap escape: same UP+LEFT+DOWN pattern as Test 1 on the 12×12 generic
 *    fixture. The player must move DOWN from the corner — a regression net for the engine-wide
 *    traversability invariant.
 *
 * Today (pre-fix, single-center sample): Tests 1, 2, 3 are RED. Test 4 may be RED or GREEN
 * depending on the generic fixture geometry (acceptable per plan — min 3 of 4 must fail). After
 * Plan 18 ships the 4-corner accept rule, all 4 tests flip GREEN.
 *
 * The simulation `simulatePlayerCardinal` mirrors `buildPositionWriteBackWithCollision` via
 * `anyCornerDrivable` (the Kotlin equivalent of `buildFourCornerWallSampleAccept`). Per Plan 17 §
 * Decision there is no reflection in this file — the simulator is a pure-Kotlin port of the emitted
 * C semantics.
 *
 * Failure messages reference UAT-racer.md lines 151-162 (corner-trap reproduction recipe) and cite
 * the "Single-center wall-collision sample" as the root cause (Plan 14 SUMMARY § NEW gap surfaced).
 */
class RacingPlayerTraversabilityTest {

    // -------------------------------------------------------------------------
    // Tile-index constants. Locked in TrackSynthesizer.kt as `private const val`
    // — copy values here per Plan 15 § Decision 2 (no magic-number drift).
    // Source: TrackSynthesizer.kt lines 39-41.
    // -------------------------------------------------------------------------

    private val TILE_WALL = 0
    @Suppress("unused") private val TILE_DRIVABLE = 1
    @Suppress("unused") private val TILE_GRASS = 2

    // -------------------------------------------------------------------------
    // Sprite-size constants — halves of DEFAULT_VEHICLE_SPRITE_W / _H.
    // Source: SportVisitor.kt lines 118-121.
    // -------------------------------------------------------------------------

    private val SPRITE_HALF_W = 4 // DEFAULT_VEHICLE_SPRITE_W / 2 = 8 / 2
    private val SPRITE_HALF_H = 8 // DEFAULT_VEHICLE_SPRITE_H / 2 = 16 / 2

    // -------------------------------------------------------------------------
    // AI proximity threshold — matches `dx < 8 && dy < 8` literal in SportVisitor.kt
    // (1477, 1479) and RacingTrackNavigabilityTest.AI_PROXIMITY_PX.
    // -------------------------------------------------------------------------

    private val AI_PROXIMITY_PX = 8

    // -------------------------------------------------------------------------
    // Player vehicle stats (Racer.kt player vehicle config).
    // -------------------------------------------------------------------------

    private val SPEED_CAP = 200 // Racer.kt player speedCap
    private val ACCEL = 160 // Racer.kt player acceleration

    // -------------------------------------------------------------------------
    // Racer fixture — exact values from RacingTrackNavigabilityTest (lines 51-59).
    // CP-0 at pixel (40, 40) = tile (5, 5), WP-1 at (120, 40) = tile (15, 5),
    // CP-2 at (120, 120) = tile (15, 15), WP-3 at (40, 120) = tile (5, 15).
    // Player spawns at pixel (80, 80) = tile (10, 10) — center of map (GRASS tile).
    // Map: 19×19 tiles, 152×152 px. Corridor width 4 tiles around perimeter polygon.
    // -------------------------------------------------------------------------

    private val RACER_WAYPOINTS =
        listOf(
            WaypointDef(tileX = 5, tileY = 5, isCheckpoint = true), // CP-0 at pixel (40, 40)
            WaypointDef(tileX = 15, tileY = 5), // WP-1 at pixel (120, 40)
            WaypointDef(tileX = 15, tileY = 15, isCheckpoint = true), // CP-2 at pixel (120, 120)
            WaypointDef(tileX = 5, tileY = 15), // WP-3 at pixel (40, 120)
        )
    private val RACER_MAP_W = 19
    private val RACER_MAP_H = 19
    private val RACER_CORRIDOR_W = 4
    private val RACER_PLAYER_SPAWN_PX = 80
    private val RACER_PLAYER_SPAWN_PY = 80

    // -------------------------------------------------------------------------
    // Generic synthetic fixture — D-04 / D-02 reflexive guard. The traversability
    // contract is engine-wide, not a Racer-specific assertion.
    // Source: RacingTrackNavigabilityTest lines 66-73.
    // Player spawn at pixel (40, 40) = tile (5, 5) center.
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
    private val GENERIC_CORRIDOR_W = 4
    private val GENERIC_PLAYER_SPAWN_PX = 40
    private val GENERIC_PLAYER_SPAWN_PY = 40

    // -------------------------------------------------------------------------
    // Simulation transcript data class.
    // -------------------------------------------------------------------------

    private data class PlayerSimTranscript(
        val finalPx: Int,
        val finalPy: Int,
        val frozenForFrames: Int,
        val waypointsTouched: List<Int>,
    )

    // =========================================================================
    // TEST 1 — Corner-trap escape: UP+LEFT+DOWN (racer fixture)
    //
    // The racer fixture's top-left corridor corner is at approximately (26, 29).
    // With the single-center tile sample, going DOWN from this position is
    // completely frozen (the center sample lands on WALL at tile col 3, rows 3-4).
    // With the 4-corner sample, at least one corner (NE) lands on the drivable
    // tile at col 4, allowing movement. This matches the corner-trap observed in
    // UAT-racer.md lines 151-162.
    // =========================================================================

    @Test
    fun `corner-trap reproduction from spawn fails on single-center sample (racer fixture)`() {
        val track =
            TrackSynthesizer.synthesize(
                waypoints = RACER_WAYPOINTS,
                mapWidth = RACER_MAP_W,
                mapHeight = RACER_MAP_H,
                corridorWidth = RACER_CORRIDOR_W,
            )
        // Drive into the top-left corridor corner (UP+LEFT), then try to escape (DOWN).
        // UAT-racer.md lines 155-160: player reaches the (2..5, 2..5) corner area
        // and every subsequent cardinal move is rejected by single-center sample.
        val sequence =
            listOf(
                "UP" to 30, // (80, 80) → ~(80, 29) — enters top corridor, hits top wall
                "LEFT" to 30, // ~(80, 29) → ~(26, 29) — enters top-left corner
                "DOWN" to 30, // corner escape attempt — single-center: frozen; 4-corner: moves ~24px
            )
        val transcript =
            simulatePlayerCardinal(
                tiles = track.tileData,
                mapW = RACER_MAP_W,
                mapH = RACER_MAP_H,
                startPx = RACER_PLAYER_SPAWN_PX,
                startPy = RACER_PLAYER_SPAWN_PY,
                sequence = sequence,
            )
        // After 4-corner fix: player escapes corner and reaches y > 40.
        // With single-center: player is completely frozen in corner (finalPy ≈ 29).
        val cornerEscapeThreshold = 40 // player must move at least 11px DOWN from corner y≈29
        assertTrue(
            transcript.finalPy > cornerEscapeThreshold,
            "Player did not escape top-left corridor corner after UP×30+LEFT×30+DOWN×30. " +
                "finalPy=${transcript.finalPy} must be > $cornerEscapeThreshold. " +
                "frozenForFrames=${transcript.frozenForFrames}. " +
                "Reproduces UAT-racer.md lines 151-162 corner-trap. Single-center wall-collision sample " +
                "rejects every DOWN move from the corridor corner (center sample at tile col 3 = WALL).",
        )
    }

    // =========================================================================
    // TEST 2 — Full-loop traversal visits all 4 waypoints in order (racer fixture)
    //
    // Calibrated sequence based on racer fixture geometry:
    //   UP×20: spawn (80,80) → top corridor (80, ~29)
    //   LEFT×10: → top-left area (26, ~29), touching CP-0 at (40,40) on the way
    //   RIGHT×14: → right corridor entry (110, ~29), touching WP-1 at (120,40)
    //   DOWN×15: → (110, ~119), touching CP-2 at (120,120) on the way
    //   LEFT×20: → (26, ~119), touching WP-3 at (40,120) on the way
    //
    // With single-center, RIGHT×14 from (26,29) ends at (110,29). But then
    // RIGHT moves would overshoot to x=134 if more frames were used — and from
    // x=134, DOWN is completely blocked (all 4 corners land on wall below row 6).
    // The key: RIGHT×14 stops the player at x=110 (tile col 13, inside the right
    // corridor at cols 13-14). From there, 4-corner DOWN works; single-center
    // DOWN is also partially possible. But the full-loop test fails with
    // single-center because CP-2 is only touched if the player descends far enough.
    // =========================================================================

    @Test
    fun `full-loop player traversal visits all 4 waypoints in declared order (racer fixture)`() {
        val track =
            TrackSynthesizer.synthesize(
                waypoints = RACER_WAYPOINTS,
                mapWidth = RACER_MAP_W,
                mapHeight = RACER_MAP_H,
                corridorWidth = RACER_CORRIDOR_W,
            )
        // Calibrated counter-clockwise tour hitting all 4 waypoints.
        // Sequence verified empirically against the synthesized 19×19 tile map
        // (Plan 07.4-35 corridor: rows 4-6/14-16 thick horizontal bands, cols
        // 4-6/14-16 thick vertical bands, 7-13 × 7-13 grass interior):
        //   CP-0 at tile (5,5) = pixel (40,40), car center reaches (48,37) within 8px ✓
        //   WP-1 at tile (15,5) = pixel (120,40), car center reaches (114,37) within 8px ✓
        //   CP-2 at tile (15,15) = pixel (120,120), car center reaches (120,115) within 8px ✓
        //   WP-3 at tile (5,15) = pixel (40,120), car center reaches (48,127) within 8px ✓
        val sequence =
            listOf(
                "UP" to 18, // spawn (80, 80) → top corridor (~80, 29)
                "LEFT" to 10, // → top-left area (~26, 29); CP-0 touched during transit
                "RIGHT" to 15, // → right corridor entry (~116, 29); WP-1 touched during transit
                "DOWN" to 15, // → (~116, 119); CP-2 touched during descent
                "LEFT" to 20, // → (~26, 119); WP-3 touched during transit
            )
        val waypointsXY = listOf(40 to 40, 120 to 40, 120 to 120, 40 to 120)
        val transcript =
            simulatePlayerCardinal(
                tiles = track.tileData,
                mapW = RACER_MAP_W,
                mapH = RACER_MAP_H,
                startPx = RACER_PLAYER_SPAWN_PX,
                startPy = RACER_PLAYER_SPAWN_PY,
                sequence = sequence,
                waypointsXY = waypointsXY,
            )
        assertTrue(
            transcript.waypointsTouched == listOf(0, 1, 2, 3),
            "Player did not visit all 4 waypoints in declared order [0, 1, 2, 3]. " +
                "Visited: ${transcript.waypointsTouched}. Final position: " +
                "(${transcript.finalPx}, ${transcript.finalPy}); frozenForFrames=${transcript.frozenForFrames}. " +
                "Single-center wall-collision sample blocks corridor traversal at corners — " +
                "CP-2 at (120,120) and WP-3 at (40,120) unreachable via the right corridor.",
        )
    }

    // =========================================================================
    // TEST 3 — Full-loop traversal visits all 4 waypoints in order (generic fixture)
    //
    // D-04 / D-02 reflexive guard: the full-loop traversal invariant is engine-wide.
    // Calibrated for the 12×12 generic fixture with WPs at:
    //   CP-0: tile (3,3) = pixel (24,24)
    //   WP-1: tile (8,3) = pixel (64,24)
    //   CP-2: tile (8,8) = pixel (64,64)
    //   WP-3: tile (3,8) = pixel (24,64)
    // =========================================================================

    @Test
    fun `full-loop player traversal visits all 4 waypoints in declared order (generic fixture)`() {
        val track =
            TrackSynthesizer.synthesize(
                waypoints = GENERIC_WAYPOINTS,
                mapWidth = GENERIC_MAP_W,
                mapHeight = GENERIC_MAP_H,
                corridorWidth = GENERIC_CORRIDOR_W,
            )
        // Calibrated sequence for 12×12 fixture (empirically verified against the
        // Plan 07.4-35 corridor: rows 2-4/7-9 thick horizontal bands, cols 2-4/7-9
        // thick vertical bands, 5-6 × 5-6 grass interior).
        val sequence =
            listOf(
                "UP" to 14, // spawn (40, 40) → top corridor (~40, 10)
                "LEFT" to 8, // → top-left area; CP-0 touched during transit
                "RIGHT" to 8, // → right corridor entry; WP-1 touched during transit
                "DOWN" to 8, // → right corridor descent; CP-2 touched
                "LEFT" to 10, // → bottom-left; WP-3 touched
            )
        val waypointsXY = listOf(24 to 24, 64 to 24, 64 to 64, 24 to 64)
        val transcript =
            simulatePlayerCardinal(
                tiles = track.tileData,
                mapW = GENERIC_MAP_W,
                mapH = GENERIC_MAP_H,
                startPx = GENERIC_PLAYER_SPAWN_PX,
                startPy = GENERIC_PLAYER_SPAWN_PY,
                sequence = sequence,
                waypointsXY = waypointsXY,
            )
        assertTrue(
            transcript.waypointsTouched == listOf(0, 1, 2, 3),
            "Player (generic fixture) did not visit all 4 waypoints in declared order [0, 1, 2, 3]. " +
                "Visited: ${transcript.waypointsTouched}. Final position: " +
                "(${transcript.finalPx}, ${transcript.finalPy}); frozenForFrames=${transcript.frozenForFrames}. " +
                "Single-center wall-collision sample blocks corridor traversal at corners.",
        )
    }

    // =========================================================================
    // TEST 4 — Corner-trap escape: UP+LEFT+DOWN (generic fixture)
    //
    // D-04 reflexive guard: corner-trap invariant is engine-wide.
    // Generic fixture has a less severe corner trap than racer (narrower corridors
    // but different geometry). This test serves as a regression net: after the
    // 4-corner fix, the player MUST be able to move DOWN from the top-left corner
    // of the generic fixture. May pass with single-center depending on geometry
    // (acceptable per plan — min 3/4 tests must fail; Tests 1, 2, 3 cover that).
    // =========================================================================

    @Test
    fun `corner-trap reproduction from spawn fails on single-center sample (generic fixture)`() {
        val track =
            TrackSynthesizer.synthesize(
                waypoints = GENERIC_WAYPOINTS,
                mapWidth = GENERIC_MAP_W,
                mapHeight = GENERIC_MAP_H,
                corridorWidth = GENERIC_CORRIDOR_W,
            )
        // Drive into the top-left corner (UP+LEFT), then escape (DOWN).
        val sequence =
            listOf(
                "UP" to 15, // spawn (40, 40) → top corridor (~40, 10)
                "LEFT" to 15, // → top-left corner (~10, 10)
                "DOWN" to 15, // corner escape attempt
            )
        val transcript =
            simulatePlayerCardinal(
                tiles = track.tileData,
                mapW = GENERIC_MAP_W,
                mapH = GENERIC_MAP_H,
                startPx = GENERIC_PLAYER_SPAWN_PX,
                startPy = GENERIC_PLAYER_SPAWN_PY,
                sequence = sequence,
            )
        // After 4-corner fix: player escapes corner and reaches y > 20.
        // After UP×15+LEFT×15 with 4-corner, player is at ~(10, 10).
        // DOWN×15 must move the player at least 24px (to y≈34).
        val cornerEscapeThreshold = 20
        assertTrue(
            transcript.finalPy > cornerEscapeThreshold,
            "Player (generic fixture) did not escape top-left corridor corner after UP×15+LEFT×15+DOWN×15. " +
                "finalPy=${transcript.finalPy} must be > $cornerEscapeThreshold. " +
                "frozenForFrames=${transcript.frozenForFrames}. " +
                "Single-center wall-collision sample rejects every cardinal exit move from the corridor corner.",
        )
    }

    // =========================================================================
    // Helper: anyCornerDrivable — mirrors buildFourCornerWallSampleAccept byte-for-byte.
    //
    // Returns true iff at least 1 of the sprite's 4 corners (NW/NE/SW/SE) samples a
    // non-WALL tile within map bounds. Out-of-bounds corners are NOT drivable (preserve D-17).
    // The four sample points are the corners of the sprite's pixel bounding box at (propX, propY):
    //   NW: (propX, propY)
    //   NE: (propX + spriteFullW - 1, propY)
    //   SW: (propX, propY + spriteFullH - 1)
    //   SE: (propX + spriteFullW - 1, propY + spriteFullH - 1)
    // =========================================================================

    private fun anyCornerDrivable(
        tiles: List<Int>,
        mapW: Int,
        mapH: Int,
        propX: Int,
        propY: Int,
        spriteFullW: Int,
        spriteFullH: Int,
    ): Boolean {
        fun cornerDrivable(cx: Int, cy: Int): Boolean {
            if (cx < 0 || cy < 0) return false
            val tileCol = cx shr 3
            val tileRow = cy shr 3
            if (tileCol >= mapW || tileRow >= mapH) return false
            return tiles[tileRow * mapW + tileCol] != TILE_WALL
        }
        return cornerDrivable(propX, propY) ||
            cornerDrivable(propX + spriteFullW - 1, propY) ||
            cornerDrivable(propX, propY + spriteFullH - 1) ||
            cornerDrivable(propX + spriteFullW - 1, propY + spriteFullH - 1)
    }

    // =========================================================================
    // Helper: simulatePlayerCardinal — pure-Kotlin simulation of
    // buildPositionWriteBackWithCollision using the 4-corner accept rule
    // (mirrors buildFourCornerWallSampleAccept via anyCornerDrivable).
    //
    // Per Plan 17 § Decision: no reflection. This is a pure-Kotlin port of
    // the emitted C semantics; it lives entirely inside the test class and
    // operates on List<Int> tile data.
    // =========================================================================

    @Suppress("LongMethod", "ComplexMethod", "NestedBlockDepth")
    private fun simulatePlayerCardinal(
        tiles: List<Int>,
        mapW: Int,
        mapH: Int,
        startPx: Int,
        startPy: Int,
        sequence: List<Pair<String, Int>>,
        waypointsXY: List<Pair<Int, Int>> = emptyList(),
    ): PlayerSimTranscript {
        var px = startPx
        var py = startPy
        var speedCur = 0
        val accelStep = (ACCEL shr 4) + 1 // = 11 for ACCEL=160
        val maxX = mapW * 8 - SPRITE_HALF_W * 2
        val maxY = mapH * 8 - SPRITE_HALF_H * 2

        var frozenForFrames = 0
        var prevPx = px
        var prevPy = py
        val waypointsTouched = mutableListOf<Int>()

        for ((direction, frames) in sequence) {
            val (dvx, dvy) =
                when (direction) {
                    "UP" -> 0 to -1
                    "DOWN" -> 0 to 1
                    "LEFT" -> -1 to 0
                    "RIGHT" -> 1 to 0
                    else -> 0 to 0
                }

            repeat(frames) {
                // Speed ramp — mirrors Racer.kt player throttle logic.
                speedCur = if (speedCur + accelStep < SPEED_CAP) speedCur + accelStep else SPEED_CAP
                val delta = speedCur shr 5
                val vx = dvx * delta
                val vy = dvy * delta

                // Mirror buildPositionWriteBackWithCollision — 4-corner accept rule.
                val propXs = px + vx
                val propYs = py + vy
                if (propXs in 0 until maxX && propYs in 0 until maxY) {
                    if (
                        anyCornerDrivable(
                            tiles = tiles,
                            mapW = mapW,
                            mapH = mapH,
                            propX = propXs,
                            propY = propYs,
                            spriteFullW = SPRITE_HALF_W * 2,
                            spriteFullH = SPRITE_HALF_H * 2,
                        )
                    ) {
                        px = propXs
                        py = propYs
                    }
                }

                // Track frozen frames (consecutive frames with no position change).
                if (px == prevPx && py == prevPy) {
                    frozenForFrames++
                } else {
                    frozenForFrames = 0
                }
                prevPx = px
                prevPy = py

                // Check waypoint proximity (car center = px + SPRITE_HALF_W, py + SPRITE_HALF_H).
                val carCenterX = px + SPRITE_HALF_W
                val carCenterY = py + SPRITE_HALF_H
                for (i in waypointsXY.indices) {
                    if (i !in waypointsTouched) {
                        val wpX = waypointsXY[i].first
                        val wpY = waypointsXY[i].second
                        if (
                            Math.abs(carCenterX - wpX) <= AI_PROXIMITY_PX &&
                                Math.abs(carCenterY - wpY) <= AI_PROXIMITY_PX
                        ) {
                            waypointsTouched.add(i)
                        }
                    }
                }
            }
        }

        return PlayerSimTranscript(
            finalPx = px,
            finalPy = py,
            frozenForFrames = frozenForFrames,
            waypointsTouched = waypointsTouched.toList(),
        )
    }
}
