/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.sport.codegen

import io.github.gbkt.core.dsl.game
import io.github.gbkt.genre.sport.dsl.racing
import io.github.gbkt.genre.sport.dsl.vehicle
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// Wave 0 RED-stub for Phase 07.4-01 — flipped to GREEN by Plan 03 + Plan 04.
//
// Plan 04 implemented `TrackSynthesizer.synthesize(...)`; Plan 03's
// RacingDelegate calls it and writes the result into ZoneIR.tileData. The
// invariants below pin the integrated behavior:
//   - the zone tile data has size mapWidth * mapHeight (no truncation)
//   - both drivable and wall bytes are present (D-11 corridor + walls)
//   - the perimeter is all walls (D-17 enclosure invariant)
// =============================================================================

class RacingPolygonRasterTest {

    private fun buildRacingGame() =
        game("PolyT") {
                val car by actor { position(0, 0) }
                val carPlayer by vehicle { actor(car) }
                val track1 by racing {
                    player(carPlayer)
                    track {
                        waypoint(x = 5, y = 5, checkpoint = true)
                        waypoint(x = 15, y = 5)
                        waypoint(x = 15, y = 15, checkpoint = true)
                        waypoint(x = 5, y = 15)
                    }
                }
                @Suppress("UNUSED_VARIABLE") val keep = track1
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

    @Test
    fun synthesizer_writes_to_zone_tiledata() {
        val ir = buildRacingGame()
        val zone = ir.zones.find { it.id == "track1" }
        assertNotNull(zone, "Expected ZoneIR 'track1' synthesized from the waypoint polygon")
        assertTrue(zone.tileData.isNotEmpty(), "tileData must be populated (UAT F-04)")
        // Racing zones always have explicit mapWidth/mapHeight set by TrackSynthesizer.
        val w = zone.mapWidth ?: error("racing zone 'track1' must have explicit mapWidth")
        val h = zone.mapHeight ?: error("racing zone 'track1' must have explicit mapHeight")
        assertTrue(
            zone.tileData.size == w * h,
            "tileData.size (${zone.tileData.size}) must equal mapWidth * mapHeight ($w * $h)",
        )
    }

    @Test
    fun synthesizer_produces_drivable_and_wall_tiles() {
        val ir = buildRacingGame()
        val zone = ir.zones.find { it.id == "track1" }
        assertNotNull(zone)
        val drivableCount = zone.tileData.count { it == 1 }
        val wallCount = zone.tileData.count { it == 0 }
        assertTrue(drivableCount > 0, "Expected drivable tiles (== 1) in synthesized track")
        assertTrue(wallCount > 0, "Expected wall tiles (== 0) in synthesized track")
    }

    @Test
    fun synthesizer_perimeter_is_walls() {
        val ir = buildRacingGame()
        val zone = ir.zones.find { it.id == "track1" }
        assertNotNull(zone)
        // Racing zones always have explicit mapWidth/mapHeight set by TrackSynthesizer.
        val w = zone.mapWidth ?: error("racing zone 'track1' must have explicit mapWidth")
        val h = zone.mapHeight ?: error("racing zone 'track1' must have explicit mapHeight")
        val data = zone.tileData
        // Top row
        for (x in 0 until w) {
            assertTrue(data[x] == 0, "Perimeter top row tile ($x,0) must be wall, was ${data[x]}")
        }
        // Bottom row
        for (x in 0 until w) {
            val idx = (h - 1) * w + x
            assertTrue(data[idx] == 0, "Perimeter bottom row tile ($x,${h - 1}) must be wall")
        }
        // Left and right columns
        for (y in 0 until h) {
            assertTrue(data[y * w] == 0, "Perimeter left column tile (0,$y) must be wall")
            assertTrue(
                data[y * w + (w - 1)] == 0,
                "Perimeter right column tile (${w - 1},$y) must be wall",
            )
        }
    }
}
