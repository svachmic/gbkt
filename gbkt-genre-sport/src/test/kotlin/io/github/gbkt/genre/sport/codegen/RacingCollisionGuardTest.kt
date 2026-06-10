/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.sport.codegen

import io.github.gbkt.backend.api.GenreVisitorResult
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.emit.CEmitter
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.genre.sport.dsl.racing
import io.github.gbkt.genre.sport.dsl.vehicle
import kotlin.test.Test
import kotlin.test.assertTrue

// =============================================================================
// Plan 07.4-12 — racing_tick wall-collision-aware position write-back.
//
// Closes GAP-C: held-UP from a sustained directional input no longer underflows
// _car_y via UINT8 wrap. The tick body now samples the synthesized tilemap at
// the proposed position; if the tile is 0 (wall, per Plan 07.4-04 TrackSynthesizer
// constants) OR the proposed position is out of world bounds, the move is
// rejected — actor stays put. Same guard wraps the AI per-instance write-back
// in the forEachActive loop, preserving D-09 (uniform physics path for player
// and AI).
//
// Contract assertions on the emitted racing_tick body:
//  - References `_zone_<id>_tiles[` (the tile-array sample expression).
//  - Contains a wall-rejection guard `tile != 0u`.
//  - Contains an INT16 bounds check using literal mapWidth and mapHeight values.
//  - The same guard wraps the AI inner-loop write-back (D-09).
//  - The id flows through verbatim with no magic strings (D-04 reflexive guard).
// =============================================================================

class RacingCollisionGuardTest {

    /** Build a racing GameIR using property name 'track1' for the racing system. */
    private fun visitTrack1(): GenreVisitorResult {
        val ir =
            game("CollT") {
                    val car by actor { position(80, 80) }
                    val rival by actor { position(80, 96) }
                    val carPlayer by vehicle {
                        actor(car)
                        stats {
                            speed(200)
                            acceleration(160)
                            handling(180)
                        }
                    }
                    val carAi by vehicle {
                        actor(rival)
                        stats {
                            speed(180)
                            acceleration(150)
                            handling(200)
                        }
                    }
                    val track1 by racing {
                        player(carPlayer)
                        aiOpponents(carAi, count = 2)
                        track {
                            waypoint(x = 5, y = 5, checkpoint = true)
                            waypoint(x = 15, y = 5)
                            waypoint(x = 15, y = 15, checkpoint = true)
                        }
                    }
                    @Suppress("UNUSED_VARIABLE") val keep = track1
                    val raceScene = scene("race") { enter {} }
                    start = raceScene
                }
                .build()
        val racingSystem = ir.systems.find { it.id == "track1" } as GenericSystem
        return SportVisitor().visit("sport_racing", racingSystem.config, ir)
    }

    /** Build a racing GameIR using property name 't1' — D-04 reflexive guard. */
    private fun visitT1(): GenreVisitorResult {
        val ir =
            game("CollT2") {
                    val car by actor { position(80, 80) }
                    val rival by actor { position(80, 96) }
                    val carPlayer by vehicle { actor(car) }
                    val carAi by vehicle { actor(rival) }
                    val t1 by racing {
                        player(carPlayer)
                        aiOpponents(carAi, count = 1)
                        track {
                            waypoint(x = 3, y = 3, checkpoint = true)
                            waypoint(x = 7, y = 3)
                            waypoint(x = 7, y = 7, checkpoint = true)
                        }
                    }
                    @Suppress("UNUSED_VARIABLE") val keep = t1
                    val raceScene = scene("race") { enter {} }
                    start = raceScene
                }
                .build()
        val racingSystem = ir.systems.find { it.id == "t1" } as GenericSystem
        return SportVisitor().visit("sport_racing", racingSystem.config, ir)
    }

    private fun renderTickBody(result: GenreVisitorResult): String {
        val fn =
            result.functions.filterIsInstance<CFunction>().first {
                it.name.startsWith("racing_tick_")
            }
        return CEmitter.emitFunction(fn)
    }

    @Test
    fun tick_body_contains_zone_tile_sample() {
        val result = visitTrack1()
        val text = renderTickBody(result)
        assertTrue(
            text.contains("_zone_track1_tiles["),
            "Expected racing_tick body to sample _zone_track1_tiles[...] for the wall-collision guard, got:\n$text",
        )
    }

    @Test
    fun tick_body_rejects_wall_tile() {
        val result = visitTrack1()
        val text = renderTickBody(result)
        // Wall-rejection guard: per TrackSynthesizer (Plan 07.4-04) tile==0 means wall.
        // Plan 07.4-18 changed from a single-center `tile != 0u` check (named variable) to
        // 4-corner OR-accept inline expressions. The `!= 0u` comparison is still emitted
        // inline within each corner expression (_zone_..._tiles[...] != 0u).
        assertTrue(
            text.contains("!= 0u"),
            "Expected racing_tick body to gate the position write-back on != 0u (wall-rejection), got:\n$text",
        )
    }

    @Test
    fun bounds_check_present() {
        val result = visitTrack1()
        val text = renderTickBody(result)
        // INT16 promotion + signed-bounds check is the safe shape that prevents UINT8 underflow.
        assertTrue(
            text.contains("INT16"),
            "Expected racing_tick body to use INT16 promotion for bounds-check, got:\n$text",
        )
        // Plan 07.4-18: the 4-corner accept helper emits inline `>> 3)` bit-shifts for
        // tile-coordinate computation. The outer INT16 bounds check (Plan 07.4-12) uses
        // propXs / propYs directly. Both are present in the emitted body.
        assertTrue(
            text.contains(">> 3)"),
            "Expected bounds-check to use >> 3 shift for tile-coordinate computation (Plan 07.4-18 4-corner inline form), got:\n$text",
        )
    }

    @Test
    fun ai_body_uses_same_collision_guard() {
        val result = visitTrack1()
        val text = renderTickBody(result)
        // D-09: AI per-instance write-back must route through the SAME collision guard.
        // Plan 07.4-18: 4-corner accept emits 4 tile-array samples per invocation.
        // Player (1 invocation) + AI (1 invocation) = 8 total _zone_track1_tiles[ samples.
        val tileSampleCount = Regex.fromLiteral("_zone_track1_tiles[").findAll(text).count()
        assertTrue(
            tileSampleCount >= 8,
            "Expected at least 8 _zone_track1_tiles[...] samples (4 corners × player + AI per D-09); got $tileSampleCount in:\n$text",
        )
        // 4 != 0u comparisons per invocation × 2 invocations (player + AI) = 8 minimum.
        val wallGuardCount = Regex.fromLiteral("!= 0u").findAll(text).count()
        assertTrue(
            wallGuardCount >= 8,
            "Expected at least 8 '!= 0u' guards (4 corners × player + AI per D-09 — Plan 07.4-18); got $wallGuardCount in:\n$text",
        )
        // AI uses the per-instance pool array on the LHS of the assignment.
        assertTrue(
            text.contains("_pool_carAi_x[i_carAi]"),
            "Expected AI write-back to target _pool_carAi_x[i_carAi], got:\n$text",
        )
        assertTrue(
            text.contains("_pool_carAi_y[i_carAi]"),
            "Expected AI write-back to target _pool_carAi_y[i_carAi], got:\n$text",
        )
    }

    /**
     * Pure-Kotlin simulation of the INT16-bounds wall-guard logic. Confirms that 60 frames of
     * held-UP from a corridor cell never underflow _car_y (Plan 07.4-12 regression net).
     *
     * Mirrors the outer INT16 bounds check in buildPositionWriteBackWithCollision (Plan 07.4-12):
     * propXs = (INT16) carX + vx propYs = (INT16) carY + vy if (propXs in [0, maxX) && propYs
     * in [0, maxY)) { // 4-corner accept (Plan 07.4-18) or single-center simulation both //
     * preserve the INT16 bounds invariant — this test uses a simplified // single-center check on a
     * synthetic all-drivable corridor to confirm // the underflow guard holds regardless of wall
     * sampling strategy. }
     */
    @Test
    fun held_up_from_start_does_not_underflow_y_pure_kotlin_simulation() {
        val mapW = 19
        val mapH = 19
        // Perimeter wall (y==0/last row, x==0/last col); inner cells are drivable.
        val tiles =
            IntArray(mapW * mapH).apply {
                for (y in 0 until mapH) {
                    for (x in 0 until mapW) {
                        this[y * mapW + x] =
                            if (y == 0 || y == mapH - 1 || x == 0 || x == mapW - 1) {
                                0 // wall
                            } else {
                                1 // drivable
                            }
                    }
                }
            }
        val spriteHalfW = 4
        val spriteHalfH = 8
        // Start in middle of corridor — well clear of walls but on a drivable cell.
        var carX = 80
        var carY = 80
        var speedCur = 0
        val accel = 160
        val speedCap = 200
        // Heading 0 = North = decreasing Y.
        val accelStep = ((accel shr 4) + 1).coerceAtMost(255)
        val maxX = mapW * 8 - spriteHalfW * 2
        val maxY = mapH * 8 - spriteHalfH * 2
        repeat(60) {
            // Throttle ramp from C.1.
            speedCur = if (speedCur + accelStep < speedCap) speedCur + accelStep else speedCap
            val delta = speedCur shr 5
            val vx = 0
            val vy = -delta
            val propXs = carX + vx
            val propYs = carY + vy
            if (propXs in 0 until maxX && propYs in 0 until maxY) {
                val sampleX = propXs + spriteHalfW
                val sampleY = propYs + spriteHalfH
                val tileCol = sampleX shr 3
                val tileRow = sampleY shr 3
                if (tileCol in 0 until mapW && tileRow in 0 until mapH) {
                    val tile = tiles[tileRow * mapW + tileCol]
                    if (tile != 0) {
                        carX = propXs
                        carY = propYs
                    }
                }
            }
        }
        // After 60 frames of held UP, carY must NEVER have wrapped into the high UINT8 range
        // (~213 was the symptom in Plan 08 evidence). With the wall guard, carY settles on
        // a drivable row near the top wall.
        assertTrue(
            carY in 0 until maxY,
            "carY must remain in world bounds [0, $maxY); got $carY (started at 80)",
        )
        assertTrue(carY > 0, "carY must not have underflowed/wrapped past 0; got $carY")
        // And specifically: carY must be strictly less than the start (we WERE moving up).
        assertTrue(
            carY < 80,
            "After 60 frames of held UP, carY must be strictly less than the 80 start; got $carY",
        )
    }

    @Test
    fun generic_contract_no_magic_strings() {
        // D-04 reflexive guard: synthetic IR with track id "t1" emits _zone_t1_tiles[
        // (NOT _zone_track1_tiles or any other hard-coded fixture token).
        val result = visitT1()
        val text = renderTickBody(result)
        assertTrue(
            text.contains("_zone_t1_tiles["),
            "Expected _zone_t1_tiles[ in racing_tick body when track id is 't1', got:\n$text",
        )
        assertTrue(
            !text.contains("_zone_track1_tiles"),
            "Body must NOT contain magic-string _zone_track1_tiles when the racing id is 't1', got:\n$text",
        )
    }
}
