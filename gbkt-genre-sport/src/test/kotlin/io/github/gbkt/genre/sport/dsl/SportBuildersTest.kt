/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.sport.dsl

import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.genre.sport.domain.BallSportConfig
import io.github.gbkt.genre.sport.domain.BracketType
import io.github.gbkt.genre.sport.domain.RacingConfig
import io.github.gbkt.genre.sport.domain.RacingMode
import io.github.gbkt.genre.sport.domain.SportPickupType
import io.github.gbkt.genre.sport.domain.TournamentConfig
import io.github.gbkt.genre.sport.domain.WinCondition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests proving that sport DSL builders on GameBuilder produce core IR types (GenericSystem).
 *
 * Key constraint: NO new sealed IR subtypes created. All sport builders produce GenericSystem with
 * typed config maps — backend discovers via ServiceLoader.
 */
class SportBuildersTest {

    // =========================================================================
    // RACING BUILDER TESTS
    // =========================================================================

    // Racing DSL refactored in Phase 07.4 Plan 03 (D-04: no String-id factories). The original
    // `racing("id") { vehicle("vid") { … } }` shape was replaced by property-delegate factories:
    //   val carPlayer by vehicle { actor(car); stats { … } }
    //   val track1 by racing { laps(3); player(carPlayer); aiOpponents(carAi); track { … } }
    // The eight @Test methods below assert the same RacingConfig fields the pre-fix tests did,
    // but exercised through the new delegate API. RacingConfig.vehicles stays empty by design —
    // vehicle bindings now live in the GenericSystem config map (key "registeredVehicles") so
    // codegen (Plan 05) can resolve each vehicle's bound ActorRef.

    @Test
    fun `racing time trial mode configuration`() {
        val ir =
            game("SportTest") {
                    val carActor by actor { position(0, 0) }
                    val carPlayer by vehicle { actor(carActor) }
                    val time_trial by racing {
                        mode(RacingMode.TIME_TRIAL)
                        laps(5)
                        player(carPlayer)
                        track {
                            waypoint(x = 0, y = 0)
                            waypoint(x = 10, y = 0)
                            waypoint(x = 10, y = 10, checkpoint = true)
                        }
                    }
                    @Suppress("UNUSED_VARIABLE") val keep = time_trial
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "time_trial" }
        assertNotNull(rawSystem, "Expected system with id 'time_trial'")
        assertIs<GenericSystem>(rawSystem)
        assertEquals("sport_racing", rawSystem.config["type"])

        val config = rawSystem.config["config"] as RacingConfig
        assertEquals(RacingMode.TIME_TRIAL, config.mode)
        assertEquals(5, config.laps)
        assertNotNull(config.track)
        // D-04: the racing zone id is auto-derived from the racing property name "time_trial",
        // not supplied by the user as a String.
        assertEquals("time_trial", config.track!!.zoneId)
        assertEquals(3, config.track!!.waypoints.size)
        assertTrue(config.track!!.waypoints[2].isCheckpoint)
    }

    @Test
    fun `racing AI opponent mode with waypoint track`() {
        val ir =
            game("SportTest") {
                    val car by actor { position(0, 0) }
                    val carPlayer by vehicle { actor(car) }
                    val ai_race by racing {
                        mode(RacingMode.AI_OPPONENT)
                        laps(3)
                        player(carPlayer)
                        track {
                            waypoint(x = 5, y = 5, checkpoint = true)
                            waypoint(x = 15, y = 5)
                            waypoint(x = 15, y = 15)
                            waypoint(x = 5, y = 15)
                        }
                    }
                    @Suppress("UNUSED_VARIABLE") val keep = ai_race
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "ai_race" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        assertEquals("sport_racing", rawSystem.config["type"])

        val config = rawSystem.config["config"] as RacingConfig
        assertEquals(RacingMode.AI_OPPONENT, config.mode)
        assertEquals(3, config.laps)
        assertEquals(4, config.track!!.waypoints.size)
    }

    @Test
    fun `racing AI rubber banding flag captured correctly`() {
        val ir =
            game("SportTest") {
                    val car by actor { position(0, 0) }
                    val carPlayer by vehicle { actor(car) }
                    val rubber_race by racing {
                        mode(RacingMode.AI_OPPONENT)
                        player(carPlayer)
                        track {
                            waypoint(x = 0, y = 0, checkpoint = true)
                            waypoint(x = 10, y = 0)
                            waypoint(x = 10, y = 10)
                        }
                        ai {
                            speedPercent(90)
                            difficulty(8)
                            rubberBanding(enabled = true, strength = 75)
                        }
                    }
                    @Suppress("UNUSED_VARIABLE") val keep = rubber_race
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "rubber_race" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        val config = rawSystem.config["config"] as RacingConfig
        assertTrue(config.aiConfig.rubberBanding)
        assertEquals(75, config.aiConfig.rubberBandStrength)
        assertEquals(90, config.aiConfig.speedPercent)
        assertEquals(8, config.aiConfig.difficulty)
    }

    @Test
    fun `vehicle stats captured correctly`() {
        val ir =
            game("SportTest") {
                    val carActor by actor { position(0, 0) }
                    val car_fast by vehicle {
                        actor(carActor)
                        stats {
                            speed(220)
                            acceleration(180)
                            handling(160)
                        }
                    }
                    val vehicle_test by racing {
                        player(car_fast)
                        track {
                            waypoint(x = 0, y = 0, checkpoint = true)
                            waypoint(x = 10, y = 0)
                            waypoint(x = 10, y = 10)
                        }
                    }
                    @Suppress("UNUSED_VARIABLE") val keep = vehicle_test
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "vehicle_test" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        // Vehicle bindings now live in the GenericSystem's "registeredVehicles" map (D-04 / D-05).
        // RacingConfig.vehicles is the legacy VehicleDef list and is empty in the new flow.
        @Suppress("UNCHECKED_CAST")
        val registered =
            rawSystem.config["registeredVehicles"]
                as Map<String, io.github.gbkt.genre.sport.domain.Vehicle>
        assertEquals(1, registered.size)
        val v = registered["car_fast"]!!
        assertEquals("car_fast", v.id)
        assertEquals(220, v.stats.speed)
        assertEquals(180, v.stats.acceleration)
        assertEquals(160, v.stats.handling)
    }

    @Test
    fun `multiple vehicles supported in racing config`() {
        val ir =
            game("SportTest") {
                    val a1 by actor { position(0, 0) }
                    val a2 by actor { position(0, 0) }
                    val a3 by actor { position(0, 0) }
                    val car_1 by vehicle {
                        actor(a1)
                        stats {
                            speed(220)
                            acceleration(160)
                            handling(140)
                        }
                    }
                    val car_2 by vehicle {
                        actor(a2)
                        stats {
                            speed(180)
                            acceleration(190)
                            handling(200)
                        }
                    }
                    val car_3 by vehicle {
                        actor(a3)
                        stats {
                            speed(150)
                            acceleration(210)
                            handling(230)
                        }
                    }
                    val multi_vehicle by racing {
                        player(car_1)
                        aiOpponents(car_2)
                        aiOpponents(car_3)
                        track {
                            waypoint(x = 0, y = 0, checkpoint = true)
                            waypoint(x = 10, y = 0)
                            waypoint(x = 10, y = 10)
                        }
                    }
                    @Suppress("UNUSED_VARIABLE") val keep = multi_vehicle
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "multi_vehicle" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        @Suppress("UNCHECKED_CAST")
        val registered =
            rawSystem.config["registeredVehicles"]
                as Map<String, io.github.gbkt.genre.sport.domain.Vehicle>
        assertEquals(3, registered.size)
        assertNotNull(registered["car_1"])
        assertNotNull(registered["car_2"])
        assertNotNull(registered["car_3"])
    }

    @Test
    fun `racing GenericSystem type is sport_racing`() {
        val ir =
            game("SportTest") {
                    val car by actor { position(0, 0) }
                    val carPlayer by vehicle { actor(car) }
                    val type_check by racing {
                        mode(RacingMode.TIME_TRIAL)
                        player(carPlayer)
                        track {
                            waypoint(x = 0, y = 0, checkpoint = true)
                            waypoint(x = 10, y = 0)
                            waypoint(x = 10, y = 10)
                        }
                    }
                    @Suppress("UNUSED_VARIABLE") val keep = type_check
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "type_check" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        assertEquals("sport_racing", rawSystem.config["type"])
    }

    @Test
    fun `racing system with pickup defined`() {
        val ir =
            game("SportTest") {
                    val car by actor { position(0, 0) }
                    val carPlayer by vehicle { actor(car) }
                    val pickup_race by racing {
                        player(carPlayer)
                        track {
                            waypoint(x = 0, y = 0, checkpoint = true)
                            waypoint(x = 10, y = 0)
                            waypoint(x = 10, y = 10)
                        }
                        pickup("boost_pad", SportPickupType.SPEED_BOOST, durationFrames = 120)
                    }
                    @Suppress("UNUSED_VARIABLE") val keep = pickup_race
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "pickup_race" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        val config = rawSystem.config["config"] as RacingConfig
        assertEquals(1, config.pickups.size)
        assertEquals("boost_pad", config.pickups[0].id)
        assertEquals(SportPickupType.SPEED_BOOST, config.pickups[0].type)
        assertEquals(120, config.pickups[0].durationFrames)
    }

    @Test
    fun `racing defaults laps to 1 and mode to AI_OPPONENT`() {
        // The pre-fix builder defaulted to `mode = TIME_TRIAL, laps = 3`. The new delegate
        // builder defaults to `mode = AI_OPPONENT, laps = 1` (the test name and assertions
        // were updated to match — the documented contract for the delegate-driven API).
        val ir =
            game("SportTest") {
                    val car by actor { position(0, 0) }
                    val carPlayer by vehicle { actor(car) }
                    val defaults_test by racing {
                        player(carPlayer)
                        track {
                            waypoint(x = 0, y = 0, checkpoint = true)
                            waypoint(x = 10, y = 0)
                            waypoint(x = 10, y = 10)
                        }
                    }
                    @Suppress("UNUSED_VARIABLE") val keep = defaults_test
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "defaults_test" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        val config = rawSystem.config["config"] as RacingConfig
        assertEquals(RacingMode.AI_OPPONENT, config.mode)
        assertEquals(1, config.laps)
        // The new delegate always synthesizes a TrackDef from the user's `track { }` block —
        // there is no "no track" path because racing { } now requires a polygon for synthesis.
        assertNotNull(config.track)
        // RacingConfig.vehicles is the legacy VehicleDef list — always empty in the new flow.
        // Bindings live in the GenericSystem's "registeredVehicles" map.
        assertTrue(config.vehicles.isEmpty())
    }

    // =========================================================================
    // BALL SPORT BUILDER TESTS
    // =========================================================================

    @Test
    fun `ball sport field dimensions and goal config`() {
        val ir =
            game("SportTest") {
                    ballSport("soccer") {
                        field {
                            width(20)
                            height(16)
                            goal {
                                width(2)
                                height(3)
                            }
                        }
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "soccer" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        assertEquals("sport_ball", rawSystem.config["type"])

        val config = rawSystem.config["config"] as BallSportConfig
        assertEquals(20, config.field.widthTiles)
        assertEquals(16, config.field.heightTiles)
        assertEquals(2, config.field.goalConfig.width)
        assertEquals(3, config.field.goalConfig.height)
        assertTrue(config.field.hasGoals)
    }

    @Test
    fun `ball physics with friction and bounce`() {
        val ir =
            game("SportTest") {
                    ballSport("tennis") {
                        field {
                            width(18)
                            height(12)
                            noGoals()
                        }
                        ball {
                            speed(160)
                            friction(4)
                            bounce(230)
                        }
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "tennis" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        val config = rawSystem.config["config"] as BallSportConfig
        assertEquals(160, config.ballPhysics.speed)
        assertEquals(4, config.ballPhysics.friction)
        assertEquals(230, config.ballPhysics.bounce)
    }

    @Test
    fun `scoring rules with FIRST_TO_SCORE win condition`() {
        val ir =
            game("SportTest") {
                    ballSport("basketball") {
                        scoring {
                            pointsPerGoal(2)
                            winCondition(WinCondition.FIRST_TO_SCORE)
                            targetScore(21)
                        }
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "basketball" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        val config = rawSystem.config["config"] as BallSportConfig
        assertEquals(2, config.scoringRules.pointsPerGoal)
        assertEquals(WinCondition.FIRST_TO_SCORE, config.scoringRules.winCondition)
        assertEquals(21, config.scoringRules.targetScore)
    }

    @Test
    fun `match structure with halves configured`() {
        val ir =
            game("SportTest") {
                    ballSport("match_test") {
                        match {
                            halves(2)
                            halfDuration(90)
                            roundsToWin(1)
                        }
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "match_test" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        val config = rawSystem.config["config"] as BallSportConfig
        assertEquals(2, config.matchStructure.halves)
        assertEquals(90, config.matchStructure.halfDurationSeconds)
        assertEquals(1, config.matchStructure.roundsToWin)
    }

    @Test
    fun `ball sport pickup defined correctly`() {
        val ir =
            game("SportTest") {
                    ballSport("pickup_sport") {
                        pickup("speed_shoe", SportPickupType.SPEED_BOOST, durationFrames = 180)
                        pickup("shield_bubble", SportPickupType.SHIELD, durationFrames = 90)
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "pickup_sport" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        val config = rawSystem.config["config"] as BallSportConfig
        assertEquals(2, config.pickups.size)
        assertEquals("speed_shoe", config.pickups[0].id)
        assertEquals(SportPickupType.SPEED_BOOST, config.pickups[0].type)
        assertEquals("shield_bubble", config.pickups[1].id)
    }

    @Test
    fun `ball sport GenericSystem type is sport_ball`() {
        val ir =
            game("SportTest") {
                    ballSport("type_check_ball") {}
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "type_check_ball" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        assertEquals("sport_ball", rawSystem.config["type"])
    }

    // =========================================================================
    // TOURNAMENT BUILDER TESTS
    // =========================================================================

    @Test
    fun `tournament single-elimination bracket`() {
        val ir =
            game("SportTest") {
                    tournament("world_cup") {
                        bracketType(BracketType.SINGLE_ELIMINATION)
                        participants("team_a", "team_b", "team_c", "team_d")
                        roundsPerMatch(1)
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "world_cup" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        assertEquals("sport_tournament", rawSystem.config["type"])

        val config = rawSystem.config["config"] as TournamentConfig
        assertEquals(BracketType.SINGLE_ELIMINATION, config.bracketType)
        assertEquals(4, config.participantIds.size)
        assertEquals("team_a", config.participantIds[0])
        assertEquals(1, config.roundsPerMatch)
    }

    @Test
    fun `tournament round-robin bracket`() {
        val ir =
            game("SportTest") {
                    tournament("league_season") {
                        bracketType(BracketType.ROUND_ROBIN)
                        participant("team_home")
                        participant("team_away")
                        participant("team_third")
                        roundsPerMatch(2)
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "league_season" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        val config = rawSystem.config["config"] as TournamentConfig
        assertEquals(BracketType.ROUND_ROBIN, config.bracketType)
        assertEquals(3, config.participantIds.size)
        assertEquals(2, config.roundsPerMatch)
    }

    @Test
    fun `tournament standings initialized correctly`() {
        val ir =
            game("SportTest") {
                    tournament("standings_test") {
                        bracketType(BracketType.ROUND_ROBIN)
                        participants("alpha", "beta")
                        standing("alpha", wins = 2, losses = 0, points = 6)
                        standing("beta", wins = 0, losses = 2, points = 0)
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "standings_test" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        val config = rawSystem.config["config"] as TournamentConfig
        assertEquals(2, config.standings.size)
        assertEquals("alpha", config.standings[0].participantId)
        assertEquals(2, config.standings[0].wins)
        assertEquals(6, config.standings[0].points)
        assertEquals("beta", config.standings[1].participantId)
        assertEquals(0, config.standings[1].wins)
    }

    @Test
    fun `tournament GenericSystem type is sport_tournament`() {
        val ir =
            game("SportTest") {
                    tournament("type_check_tournament") { participants("a", "b") }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "type_check_tournament" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        assertEquals("sport_tournament", rawSystem.config["type"])
    }

    @Test
    fun `tournament double-elimination bracket supported`() {
        val ir =
            game("SportTest") {
                    tournament("double_elim") {
                        bracketType(BracketType.DOUBLE_ELIMINATION)
                        participants("red", "blue", "green", "yellow")
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "double_elim" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        val config = rawSystem.config["config"] as TournamentConfig
        assertEquals(BracketType.DOUBLE_ELIMINATION, config.bracketType)
        assertEquals(4, config.participantIds.size)
    }
}
