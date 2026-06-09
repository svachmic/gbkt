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
import io.github.gbkt.core.ir.CameraSystem
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.genre.sport.dsl.racing
import io.github.gbkt.genre.sport.dsl.vehicle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// Plan 05 + Plan 03 GREEN-flip of the Wave 0 RED stubs.
//
// VALIDATION.md row 01-T1 — covers D-02 (no game-specific special cases — every
// pattern reusable) and D-18 item 2 (a generic racing test that exercises
// declare→synthesize→spawn AI→tick→lap on a fixture independent of the Racer
// example).
//
// Fixture uses GENERIC ids (p1 / r1 / pVeh / rVeh / t1) sharing no substring
// with any Racer-specific name. The acceptance-criteria grep enforces zero
// occurrences of Racer-specific quoted tokens at the file level.
// =============================================================================

class RacingGenericContractTest {

    // GENERIC fixture ids — used by every @Test in this file. Deliberately
    // chosen to share no substring with any Racer-specific name.
    private val playerActorId = "p1"
    private val rivalActorId = "r1"
    private val playerVehicleId = "pVeh"
    private val rivalVehicleId = "rVeh"
    private val racingId = "t1"

    /** Build a synthetic GameIR using ONLY generic ids — D-02 reflexive guard. */
    private fun buildGenericGameIR(): GameIR =
        game("Generic") {
                val p1 by actor { position(0, 0) }
                val r1 by actor { position(0, 0) }
                val pVeh by vehicle { actor(p1) }
                val rVeh by vehicle { actor(r1) }
                val t1 by racing {
                    player(pVeh)
                    aiOpponents(rVeh, count = 1)
                    track {
                        waypoint(x = 5, y = 5, checkpoint = true)
                        waypoint(x = 15, y = 5)
                        waypoint(x = 15, y = 15, checkpoint = true)
                    }
                }
                @Suppress("UNUSED_VARIABLE") val keep = t1
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

    @Test
    fun generic_racing_emits_tick_function() {
        val ir = buildGenericGameIR()
        val racingSystem = ir.systems.find { it.id == racingId } as GenericSystem
        val result = SportVisitor().visit("sport_racing", racingSystem.config, ir)
        val tickFn =
            result.functions.filterIsInstance<CFunction>().firstOrNull {
                it.name == "racing_tick_$racingId"
            }
        assertNotNull(
            tickFn,
            "Expected SportVisitor result to contain CFunction named 'racing_tick_$racingId'",
        )
    }

    @Test
    fun generic_racing_emits_camera_system() {
        val ir = buildGenericGameIR()
        val cam = ir.systems.filterIsInstance<CameraSystem>().firstOrNull()
        assertNotNull(cam, "Expected gameIR.systems to contain a CameraSystem (D-06 auto-wire)")
        assertEquals(
            playerActorId,
            cam.followActorId,
            "Camera must follow the bound player actor id (D-06)",
        )
    }

    @Test
    fun generic_racing_emits_zone_with_tiledata() {
        val ir = buildGenericGameIR()
        val zone = ir.zones.firstOrNull { it.id == racingId }
        assertNotNull(
            zone,
            "Expected ZoneIR id == '$racingId' (auto-derived from racing property name, D-04)",
        )
        assertTrue(
            zone.tileData.isNotEmpty(),
            "Expected synthesized tileData populated (D-10), got empty",
        )
    }

    @Test
    fun generic_racing_emits_ai_pool() {
        val ir = buildGenericGameIR()
        val pool = ir.actorPools.firstOrNull { it.id == rivalVehicleId }
        assertNotNull(
            pool,
            "Expected an ActorPoolIR with id == '$rivalVehicleId' (AI vehicle property name, D-14)",
        )
        assertEquals(
            rivalActorId,
            pool.actorTemplateId,
            "Pool template must be the bound actor id (D-14)",
        )
    }

    @Test
    fun generic_racing_emits_lap_bitmap_global() {
        val ir = buildGenericGameIR()
        val racingSystem = ir.systems.find { it.id == racingId } as GenericSystem
        val result = SportVisitor().visit("sport_racing", racingSystem.config, ir)
        val visited =
            result.varDecls.filterIsInstance<CVarDecl>().firstOrNull {
                it.name == "_racing_visited_$racingId"
            }
        assertNotNull(
            visited,
            "Expected emitted varDecls to contain '_racing_visited_$racingId' (D-15 / D-17)",
        )
    }

    @Test
    fun generic_racing_test_uses_no_racer_strings() {
        // REFLEXIVE D-02 GUARD: walk the synthesized GameIR's emitted varDecl + function names
        // and assert none of them contain Racer-specific substring tokens. The internal
        // identifiers must come exclusively from the generic property delegates above.
        val ir = buildGenericGameIR()
        val racingSystem = ir.systems.find { it.id == racingId } as GenericSystem
        val result = SportVisitor().visit("sport_racing", racingSystem.config, ir)

        val emittedNames =
            result.functions.filterIsInstance<CFunction>().map { it.name } +
                result.varDecls.filterIsInstance<CVarDecl>().map { it.name }

        // Racer-specific tokens that must not appear (D-02). Racer.kt's property names today.
        val forbiddenTokens = listOf("track1", "carPlayer", "carAi")
        for (name in emittedNames) {
            for (token in forbiddenTokens) {
                assertTrue(
                    !name.contains(token),
                    "Emitted symbol '$name' contains Racer-specific token '$token' (D-02 violation)",
                )
            }
        }

        val fixtureIds =
            listOf(playerActorId, rivalActorId, playerVehicleId, rivalVehicleId, racingId)
        // Touch the fixture so the compiler does not dead-strip it.
        check(fixtureIds.size == 5)
    }
}
