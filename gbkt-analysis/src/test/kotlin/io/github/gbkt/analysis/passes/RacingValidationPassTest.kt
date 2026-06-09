/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.analysis.Diagnostic
import io.github.gbkt.analysis.FakeProfile
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.Severity
import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.core.dsl.ActorRef
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.CameraSystem
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.MoveBy
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SystemIR
import io.github.gbkt.genre.sport.domain.RacingConfig
import io.github.gbkt.genre.sport.domain.TrackDef
import io.github.gbkt.genre.sport.domain.Vehicle
import io.github.gbkt.genre.sport.domain.VehicleStats
import io.github.gbkt.genre.sport.domain.WaypointDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// Plan 07.4-06 RacingValidationPass — flips Wave 0 RED stubs to GREEN.
//
// VALIDATION.md row: 01-T3 / Plan 06 / Wave 4. Covers D-05 (player binding),
// D-06 (camera follow mismatch warning), D-08 (hand-coded movement warning),
// and D-10 (polygon non-degeneracy).
//
// Diagnostic id taxonomy (each id pinned by at least one @Test below):
//   ANLZ-RACING-01 ERROR    polygon non-degenerate (D-10)
//                           — < 3 waypoints, collinear, zero-area
//   ANLZ-RACING-02 ERROR    racing without player(...) binding (D-05)
//   ANLZ-RACING-03 ERROR    Vehicle without actor(...) binding (D-05)
//   ANLZ-RACING-04 WARNING  hand-coded moveBy / direct actor.x assignment
//                           on bound player (D-08; result is Success — warnings
//                           don't fail the pipeline)
//   ANLZ-RACING-05 WARNING  user-declared CameraSystem.followActorId does
//                           not match the racing's bound player (D-06)
//
// The pass lives in `gbkt-analysis` and reads `gbkt-genre-sport` data via the
// GenericSystem.config map-typed payload (Plan 03's contract surface) using
// reflection — gbkt-analysis main src has no compile-time dep on
// gbkt-genre-sport (which would form gbkt-analysis -> gbkt-genre-sport ->
// gbkt-backend-gbdk -> gbkt-analysis). This TEST source set carries a
// testImplementation dep on gbkt-genre-sport so we can build real
// RacingConfig / Vehicle / WaypointDef fixtures and exercise the pass.
// =============================================================================

class RacingValidationPassTest {

    private val pass = RacingValidationPass()

    private fun makeContext(game: GameIR): PassContext =
        PassContext(game = game, profile = FakeProfile, config = AnalysisConfig(maxBanks = 2))

    // ─── shared fixture helpers ───────────────────────────────────────────────

    private fun stubActor(id: String): ActorIR = ActorIR(id = id, position = PositionDef(0, 0))

    private fun stubVehicle(id: String, actorId: String): Vehicle =
        Vehicle(
            id = id,
            actorRef = ActorRef(id = actorId),
            stats = VehicleStats(speed = 100, acceleration = 100, handling = 100),
        )

    /**
     * Builds a `sport_racing` GenericSystem with the canonical Plan 03 config map shape: "type" ->
     * "sport_racing" "config" -> RacingConfig "registeredVehicles" -> Map<String, Vehicle>
     * "playerVehicle" -> Vehicle? (or null when missing player binding) "aiVehicles_resolved"->
     * List<Vehicle>
     */
    private fun buildRacingSystem(
        racingId: String,
        config: RacingConfig,
        registeredVehicles: Map<String, Vehicle> = emptyMap(),
    ): GenericSystem {
        val player = config.playerVehicleId?.let { registeredVehicles[it] }
        val ai = config.aiVehicles.mapNotNull { slot -> registeredVehicles[slot.vehicleId] }
        val configMap =
            buildMap<String, Any> {
                put("type", "sport_racing")
                put("config", config)
                put("registeredVehicles", registeredVehicles)
                if (player != null) put("playerVehicle", player)
                put("aiVehicles_resolved", ai)
            }
        return GenericSystem(id = racingId, config = configMap)
    }

    private fun firstDiag(diags: List<Diagnostic>, id: String): Diagnostic? = diags.firstOrNull {
        it.id == id
    }

    private fun runPass(
        scenes: List<SceneIR> = emptyList(),
        actors: List<ActorIR> = emptyList(),
        systems: List<SystemIR> = emptyList(),
    ): PassResult {
        val game = GameIR(name = "Test", scenes = scenes, actors = actors, systems = systems)
        return pass.run(makeContext(game))
    }

    // -------------------------------------------------------------------------
    // ANLZ-RACING-01 — polygon non-degenerate (D-10)
    // -------------------------------------------------------------------------

    @Test
    fun polygon_with_two_waypoints_emits_error() {
        val carActor = stubActor("car")
        val playerVeh = stubVehicle("carPlayer", "car")
        val cfg =
            RacingConfig(
                id = "track1",
                track =
                    TrackDef(
                        zoneId = "track1",
                        waypoints =
                            listOf(WaypointDef(0, 0, isCheckpoint = true), WaypointDef(10, 0)),
                    ),
                playerVehicleId = "carPlayer",
            )
        val sys = buildRacingSystem("track1", cfg, mapOf("carPlayer" to playerVeh))

        val result = runPass(actors = listOf(carActor), systems = listOf(sys))

        assertTrue(result is PassResult.Failed, "Expected PassResult.Failed for 2-waypoint polygon")
        val diag = firstDiag(result.diagnostics, "ANLZ-RACING-01")
        assertNotNull(diag, "Expected ANLZ-RACING-01 diagnostic for < 3 waypoints")
        assertEquals(Severity.ERROR, diag.severity)
    }

    @Test
    fun collinear_polygon_emits_error() {
        val carActor = stubActor("car")
        val playerVeh = stubVehicle("carPlayer", "car")
        // All three waypoints share y=5 — collinear; polygon encloses zero area.
        val cfg =
            RacingConfig(
                id = "track1",
                track =
                    TrackDef(
                        zoneId = "track1",
                        waypoints =
                            listOf(
                                WaypointDef(0, 5, isCheckpoint = true),
                                WaypointDef(5, 5),
                                WaypointDef(10, 5),
                            ),
                    ),
                playerVehicleId = "carPlayer",
            )
        val sys = buildRacingSystem("track1", cfg, mapOf("carPlayer" to playerVeh))

        val result = runPass(actors = listOf(carActor), systems = listOf(sys))

        assertTrue(result is PassResult.Failed, "Expected PassResult.Failed for collinear polygon")
        val diag = firstDiag(result.diagnostics, "ANLZ-RACING-01")
        assertNotNull(diag, "Expected ANLZ-RACING-01 diagnostic for collinear waypoints")
        assertEquals(Severity.ERROR, diag.severity)
    }

    @Test
    fun zero_area_polygon_emits_error() {
        val carActor = stubActor("car")
        val playerVeh = stubVehicle("carPlayer", "car")
        // Three coincident waypoints — encloses zero area.
        val cfg =
            RacingConfig(
                id = "track1",
                track =
                    TrackDef(
                        zoneId = "track1",
                        waypoints =
                            listOf(
                                WaypointDef(5, 5, isCheckpoint = true),
                                WaypointDef(5, 5),
                                WaypointDef(5, 5),
                            ),
                    ),
                playerVehicleId = "carPlayer",
            )
        val sys = buildRacingSystem("track1", cfg, mapOf("carPlayer" to playerVeh))

        val result = runPass(actors = listOf(carActor), systems = listOf(sys))

        assertTrue(result is PassResult.Failed, "Expected PassResult.Failed for zero-area polygon")
        val diag = firstDiag(result.diagnostics, "ANLZ-RACING-01")
        assertNotNull(diag, "Expected ANLZ-RACING-01 diagnostic for zero-area polygon")
        assertEquals(Severity.ERROR, diag.severity)
    }

    @Test
    fun valid_square_polygon_emits_no_polygon_error() {
        val carActor = stubActor("car")
        val playerVeh = stubVehicle("carPlayer", "car")
        // Well-formed 4-waypoint square — encloses a 10x10 area.
        val cfg =
            RacingConfig(
                id = "track1",
                track =
                    TrackDef(
                        zoneId = "track1",
                        waypoints =
                            listOf(
                                WaypointDef(5, 5, isCheckpoint = true),
                                WaypointDef(15, 5),
                                WaypointDef(15, 15),
                                WaypointDef(5, 15),
                            ),
                    ),
                playerVehicleId = "carPlayer",
            )
        val sys = buildRacingSystem("track1", cfg, mapOf("carPlayer" to playerVeh))

        val result = runPass(actors = listOf(carActor), systems = listOf(sys))

        assertTrue(
            result is PassResult.Success,
            "Expected PassResult.Success for valid square polygon, got ${describe(result)}",
        )
        val diag = firstDiag(result.context.diagnostics, "ANLZ-RACING-01")
        assertEquals(null, diag, "Expected NO ANLZ-RACING-01 diagnostic for valid square polygon")
    }

    // -------------------------------------------------------------------------
    // ANLZ-RACING-02 — racing without player binding (D-05)
    // -------------------------------------------------------------------------

    @Test
    fun racing_without_player_emits_error() {
        // RacingConfig with playerVehicleId = null must fail with ANLZ-RACING-02.
        val cfg =
            RacingConfig(
                id = "track1",
                track =
                    TrackDef(
                        zoneId = "track1",
                        waypoints =
                            listOf(
                                WaypointDef(5, 5, isCheckpoint = true),
                                WaypointDef(15, 5),
                                WaypointDef(15, 15),
                                WaypointDef(5, 15),
                            ),
                    ),
                playerVehicleId = null, // missing player binding (D-05)
            )
        val sys = buildRacingSystem("track1", cfg, registeredVehicles = emptyMap())

        val result = runPass(systems = listOf(sys))

        assertTrue(result is PassResult.Failed, "Expected PassResult.Failed for missing player")
        val diag = firstDiag(result.diagnostics, "ANLZ-RACING-02")
        assertNotNull(diag, "Expected ANLZ-RACING-02 diagnostic for missing player binding")
        assertEquals(Severity.ERROR, diag.severity)
    }

    // -------------------------------------------------------------------------
    // ANLZ-RACING-03 — Vehicle without actor binding (D-05)
    // -------------------------------------------------------------------------

    @Test
    fun vehicle_without_actor_emits_error() {
        // Vehicle's actorRef.id ("ghostCar") does NOT resolve to any actor in the game.
        val playerVeh = stubVehicle("carPlayer", "ghostCar")
        val cfg =
            RacingConfig(
                id = "track1",
                track =
                    TrackDef(
                        zoneId = "track1",
                        waypoints =
                            listOf(
                                WaypointDef(5, 5, isCheckpoint = true),
                                WaypointDef(15, 5),
                                WaypointDef(15, 15),
                                WaypointDef(5, 15),
                            ),
                    ),
                playerVehicleId = "carPlayer",
            )
        val sys = buildRacingSystem("track1", cfg, mapOf("carPlayer" to playerVeh))
        // Note: game.actors is empty — "ghostCar" never declared.

        val result = runPass(actors = emptyList(), systems = listOf(sys))

        assertTrue(result is PassResult.Failed, "Expected PassResult.Failed for missing actor")
        val diag = firstDiag(result.diagnostics, "ANLZ-RACING-03")
        assertNotNull(diag, "Expected ANLZ-RACING-03 diagnostic for vehicle missing actor")
        assertEquals(Severity.ERROR, diag.severity)
    }

    // -------------------------------------------------------------------------
    // ANLZ-RACING-04 — hand-coded movement on bound player (D-08, WARNING)
    // -------------------------------------------------------------------------

    @Test
    fun moveBy_on_bound_player_emits_warning_not_error() {
        val carActor = stubActor("car")
        val playerVeh = stubVehicle("carPlayer", "car")
        val cfg =
            RacingConfig(
                id = "track1",
                track =
                    TrackDef(
                        zoneId = "track1",
                        waypoints =
                            listOf(
                                WaypointDef(5, 5, isCheckpoint = true),
                                WaypointDef(15, 5),
                                WaypointDef(15, 15),
                                WaypointDef(5, 15),
                            ),
                    ),
                playerVehicleId = "carPlayer",
            )
        val sys = buildRacingSystem("track1", cfg, mapOf("carPlayer" to playerVeh))

        // Scene whose frame block hand-codes movement on the bound player actor.
        val scene =
            SceneIR(
                id = "race",
                frameOps = listOf(MoveBy(actorId = "car", dx = Literal(1), dy = Literal(0))),
            )

        val result =
            runPass(scenes = listOf(scene), actors = listOf(carActor), systems = listOf(sys))

        // Warnings must NOT fail the pipeline.
        assertTrue(
            result is PassResult.Success,
            "Expected PassResult.Success (WARNING does not fail), got ${describe(result)}",
        )
        val diag = firstDiag(result.context.diagnostics, "ANLZ-RACING-04")
        assertNotNull(diag, "Expected ANLZ-RACING-04 warning for hand-coded moveBy")
        assertEquals(Severity.WARNING, diag.severity)
    }

    @Test
    fun direct_actor_x_assignment_emits_warning() {
        val carActor = stubActor("car")
        val playerVeh = stubVehicle("carPlayer", "car")
        val cfg =
            RacingConfig(
                id = "track1",
                track =
                    TrackDef(
                        zoneId = "track1",
                        waypoints =
                            listOf(
                                WaypointDef(5, 5, isCheckpoint = true),
                                WaypointDef(15, 5),
                                WaypointDef(15, 15),
                                WaypointDef(5, 15),
                            ),
                    ),
                playerVehicleId = "carPlayer",
            )
        val sys = buildRacingSystem("track1", cfg, mapOf("carPlayer" to playerVeh))

        // Direct write to _car_x (the actor-property variable convention).
        val scene =
            SceneIR(
                id = "race",
                frameOps = listOf(Assign(target = "_car_x", value = Literal(64), op = AssignOp.SET)),
            )

        val result =
            runPass(scenes = listOf(scene), actors = listOf(carActor), systems = listOf(sys))

        assertTrue(
            result is PassResult.Success,
            "Expected PassResult.Success (WARNING does not fail), got ${describe(result)}",
        )
        val diag = firstDiag(result.context.diagnostics, "ANLZ-RACING-04")
        assertNotNull(diag, "Expected ANLZ-RACING-04 warning for direct _car_x assignment")
        assertEquals(Severity.WARNING, diag.severity)
    }

    // -------------------------------------------------------------------------
    // ANLZ-RACING-05 — camera follow mismatch (D-06, WARNING)
    // -------------------------------------------------------------------------

    @Test
    fun camera_follow_mismatch_emits_warning() {
        val carActor = stubActor("car")
        val wrongActor = stubActor("wrongActor")
        val playerVeh = stubVehicle("carPlayer", "car")
        val cfg =
            RacingConfig(
                id = "track1",
                track =
                    TrackDef(
                        zoneId = "track1",
                        waypoints =
                            listOf(
                                WaypointDef(5, 5, isCheckpoint = true),
                                WaypointDef(15, 5),
                                WaypointDef(15, 15),
                                WaypointDef(5, 15),
                            ),
                    ),
                playerVehicleId = "carPlayer",
            )
        val sys = buildRacingSystem("track1", cfg, mapOf("carPlayer" to playerVeh))
        val userCamera = CameraSystem(id = "camera", followActorId = "wrongActor")

        val result =
            runPass(actors = listOf(carActor, wrongActor), systems = listOf(sys, userCamera))

        // Warnings must NOT fail the pipeline.
        assertTrue(
            result is PassResult.Success,
            "Expected PassResult.Success (WARNING does not fail), got ${describe(result)}",
        )
        val diag = firstDiag(result.context.diagnostics, "ANLZ-RACING-05")
        assertNotNull(diag, "Expected ANLZ-RACING-05 warning for camera follow mismatch")
        assertEquals(Severity.WARNING, diag.severity)
    }

    // ─── small helpers used only inside test bodies ───────────────────────────

    private fun describe(result: PassResult): String =
        when (result) {
            is PassResult.Success -> "Success(diagnostics=${result.context.diagnostics})"
            is PassResult.Failed -> "Failed(diagnostics=${result.diagnostics})"
        }
}
