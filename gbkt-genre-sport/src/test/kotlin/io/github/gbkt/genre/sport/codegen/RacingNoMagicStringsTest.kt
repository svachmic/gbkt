/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.sport.codegen

import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.genre.sport.domain.RacingConfig
import io.github.gbkt.genre.sport.domain.Vehicle
import io.github.gbkt.genre.sport.dsl.racing
import io.github.gbkt.genre.sport.dsl.vehicle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// Wave 0 RED-stub for Phase 07.4-01 — IR-side checks flipped to GREEN by Plan 03.
//
// Covers D-04 (no magic strings — Project Rule #1). Plan 03's RacingDelegate
// captures the racing/vehicle property names through KProperty.name; this file
// pins the round-trip from Kotlin source → RacingConfig.id / Vehicle.id /
// ZoneIR.id. The fourth @Test (`every_emitted_c_symbol_uses_track1_id`) inspects
// generated C output and remains RED until Plan 05 wires the visitor side.
// =============================================================================

class RacingNoMagicStringsTest {

    private fun buildRacingGame() =
        game("MagicT") {
                val car by actor { position(0, 0) }
                val carPlayer by vehicle { actor(car) }
                val track1 by racing {
                    player(carPlayer)
                    track {
                        waypoint(x = 0, y = 0, checkpoint = true)
                        waypoint(x = 10, y = 0)
                        waypoint(x = 10, y = 10)
                    }
                }
                @Suppress("UNUSED_VARIABLE") val keep = track1
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

    @Test
    fun racing_id_equals_property_name() {
        val ir = buildRacingGame()
        val racingSystem = ir.systems.find { it.id == "track1" }
        assertNotNull(racingSystem, "Expected racing system with id 'track1'")
        val config = (racingSystem as GenericSystem).config["config"] as RacingConfig
        assertEquals("track1", config.id)
    }

    @Test
    fun vehicle_id_equals_property_name() {
        val ir = buildRacingGame()
        val racingSystem = ir.systems.find { it.id == "track1" } as GenericSystem
        @Suppress("UNCHECKED_CAST")
        val registered = racingSystem.config["registeredVehicles"] as Map<String, Vehicle>
        assertNotNull(registered["carPlayer"])
        assertEquals("carPlayer", registered["carPlayer"]!!.id)
    }

    @Test
    fun zone_id_auto_derived_from_racing_property_name() {
        val ir = buildRacingGame()
        val zone = ir.zones.firstOrNull()
        assertNotNull(zone, "Expected at least one ZoneIR (synthesized from the racing delegate)")
        assertEquals("track1", zone.id)
    }

    @Test
    fun every_emitted_c_symbol_uses_track1_id() {
        // The codegen round-trip: the property name 'track1' (and 'carPlayer') flow into the
        // emitted C symbol names verbatim — no magic strings, no Racer-specific tokens. D-04.
        val ir = buildRacingGame()
        val racingSystem = ir.systems.find { it.id == "track1" } as GenericSystem
        val result = SportVisitor().visit("sport_racing", racingSystem.config, ir)

        val varNames = result.varDecls.filterIsInstance<CVarDecl>().map { it.name }
        val funcNames = result.functions.filterIsInstance<CFunction>().map { it.name }

        // Player-vehicle globals must be keyed by the vehicle property name 'carPlayer'.
        assertTrue(
            varNames.any { it == "_vehicle_carPlayer_speed_cur" },
            "Expected _vehicle_carPlayer_speed_cur in varNames, got: $varNames",
        )
        assertTrue(
            varNames.any { it == "_vehicle_carPlayer_heading" },
            "Expected _vehicle_carPlayer_heading in varNames, got: $varNames",
        )

        // Racing-system state must be keyed by the racing property name 'track1'.
        assertTrue(
            varNames.any { it == "_racing_lap_count_track1" },
            "Expected _racing_lap_count_track1 in varNames, got: $varNames",
        )
        assertTrue(
            varNames.any { it == "_racing_visited_track1" },
            "Expected _racing_visited_track1 in varNames, got: $varNames",
        )
        assertTrue(
            funcNames.any { it == "racing_tick_track1" },
            "Expected racing_tick_track1 in funcNames, got: $funcNames",
        )

        // No emitted symbol may carry a magic-string fragment that the user did not type.
        val forbidden = listOf("magic", "default", "TRACK1", "CARPLAYER")
        for (name in varNames + funcNames) {
            for (token in forbidden) {
                assertTrue(
                    !name.contains(token),
                    "Emitted symbol '$name' contains forbidden token '$token' (D-04 violation)",
                )
            }
        }
    }
}
