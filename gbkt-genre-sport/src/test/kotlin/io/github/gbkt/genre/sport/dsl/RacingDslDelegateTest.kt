/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.sport.dsl

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.genre.sport.domain.RacingConfig
import io.github.gbkt.genre.sport.domain.Vehicle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// Wave 0 RED-stub for Phase 07.4-01 (Task 2) — flipped to GREEN by Plan 03.
//
// Covers D-04 (no magic strings — Project Rule #1): every racing/vehicle id is
// captured from KProperty.name during provideDelegate. The reflexive @Test
// methods at the bottom enforce that no `racing` / `vehicle` overload accepts
// a String first parameter, locking the API surface from this commit forward.
// =============================================================================

class RacingDslDelegateTest {

    @Test
    fun vehicle_id_equals_property_name() {
        val ir =
            game("DslT") {
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

        // The `registeredVehicles` map carries the full Vehicle data (D-05). The carPlayer
        // delegate captured "carPlayer" as its id from the Kotlin property name.
        val racingSystem = ir.systems.find { it.id == "track1" } as GenericSystem
        @Suppress("UNCHECKED_CAST")
        val registered = racingSystem.config["registeredVehicles"] as Map<String, Vehicle>
        assertTrue(registered.containsKey("carPlayer"), "Expected vehicle id 'carPlayer'")
        assertEquals("carPlayer", registered["carPlayer"]!!.id)
    }

    @Test
    fun racing_id_equals_property_name() {
        val ir =
            game("DslT") {
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

        val racingSystem = ir.systems.find { it.id == "track1" }
        assertNotNull(racingSystem, "Expected racing system with id 'track1'")
        val config = (racingSystem as GenericSystem).config["config"] as RacingConfig
        assertEquals("track1", config.id)
    }

    @Test
    fun zone_id_auto_derived_from_racing_property_name() {
        val ir =
            game("DslT") {
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

        // The RacingDelegate auto-creates a ZoneIR with id == property name (D-04, D-10).
        val zone = ir.zones.find { it.id == "track1" }
        assertNotNull(zone, "Expected ZoneIR with id 'track1' synthesized from the racing delegate")
    }

    @Test
    fun pool_id_equals_ai_vehicle_property_name() {
        val ir =
            game("DslT") {
                    val car by actor { position(0, 0) }
                    val rival by actor { position(0, 0) }
                    val carPlayer by vehicle { actor(car) }
                    val carAi by vehicle { actor(rival) }
                    val track1 by racing {
                        player(carPlayer)
                        aiOpponents(carAi)
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

        // D-14: pool id from the AI vehicle property name "carAi", NOT the actor id "rival".
        val pool = ir.actorPools.find { it.id == "carAi" }
        assertNotNull(pool, "Expected ActorPoolIR with id 'carAi' (AI vehicle property name)")
    }

    @Test
    fun racing_factory_takes_no_String_id_parameter() {
        // Walk every `racing` extension via reflection — none may have String as first arg.
        val racingMethods =
            GameBuilder::class.java.methods.filter { it.name == "racing" } +
                Class.forName("io.github.gbkt.genre.sport.dsl.SportExtensionsKt").methods.filter {
                    it.name == "racing"
                }
        assertTrue(racingMethods.isNotEmpty(), "Expected at least one `racing` extension")
        racingMethods.forEach { method ->
            // Skip lambda receivers — the genuine first parameter is GameBuilder (the receiver
            // for the extension), so the second parameter is what the user supplies.
            val params = method.parameterTypes
            // Extension functions on GameBuilder become static methods with GameBuilder as the
            // first parameter. The user-facing first parameter is params[1].
            if (params.size >= 2 && params[1] == String::class.java) {
                error(
                    "racing(...) overload still accepts String first param: " +
                        method.parameterTypes.joinToString { it.simpleName }
                )
            }
        }
    }

    @Test
    fun vehicle_factory_takes_no_String_id_parameter() {
        val vehicleMethods =
            GameBuilder::class.java.methods.filter { it.name == "vehicle" } +
                Class.forName("io.github.gbkt.genre.sport.dsl.SportExtensionsKt").methods.filter {
                    it.name == "vehicle"
                }
        assertTrue(vehicleMethods.isNotEmpty(), "Expected at least one `vehicle` extension")
        vehicleMethods.forEach { method ->
            val params = method.parameterTypes
            if (params.size >= 2 && params[1] == String::class.java) {
                error(
                    "vehicle(...) overload still accepts String first param: " +
                        method.parameterTypes.joinToString { it.simpleName }
                )
            }
        }
    }
}
