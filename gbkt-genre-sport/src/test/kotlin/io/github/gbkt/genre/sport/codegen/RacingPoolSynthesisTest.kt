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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// =============================================================================
// Wave 0 RED-stub for Phase 07.4-01 — flipped to GREEN by Plan 03.
//
// Covers SC-1, D-13 (AI vehicles ARE 07.3 pool instances) and D-14 (pool id =
// vehicle property name, NOT the actor id). The four @Test methods exercise the
// new RacingDelegate.provideDelegate's pool-synthesis pass.
// =============================================================================

class RacingPoolSynthesisTest {

    private fun buildRacingGame(count: Int) =
        game("PoolT") {
                val car by actor { position(0, 0) }
                val rival by actor { position(0, 0) }
                val carPlayer by vehicle { actor(car) }
                val carAi by vehicle { actor(rival) }
                val track1 by racing {
                    player(carPlayer)
                    aiOpponents(carAi, count = count)
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
    fun ai_opponents_emit_actor_pool_ir() {
        val ir = buildRacingGame(count = 1)
        val pool = ir.actorPools.find { it.id == "carAi" }
        assertNotNull(pool, "Expected an ActorPoolIR with id 'carAi' synthesized from aiOpponents")
    }

    @Test
    fun pool_id_matches_vehicle_property_name() {
        val ir = buildRacingGame(count = 1)
        val pool = ir.actorPools.find { it.id == "carAi" }
        assertNotNull(pool)
        assertEquals(
            "carAi",
            pool.id,
            "Pool id must derive from AI VEHICLE property name (D-14), not actor id 'rival'",
        )
    }

    @Test
    fun pool_max_size_matches_count_param() {
        val ir = buildRacingGame(count = 2)
        val pool = ir.actorPools.find { it.id == "carAi" }
        assertNotNull(pool)
        assertEquals(2, pool.config.maxSize)
    }

    @Test
    fun pool_template_id_is_bound_actor_id() {
        val ir = buildRacingGame(count = 1)
        val pool = ir.actorPools.find { it.id == "carAi" }
        assertNotNull(pool)
        // D-13: the pool's spawn template is the ActorRef bound by `vehicle { actor(rival) }`.
        assertEquals("rival", pool.actorTemplateId)
    }
}
