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
import kotlin.test.assertNull
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

    @Test
    fun `racing time trial mode configuration`() {
        val ir =
            game("SportTest") {
                    racing("time_trial") {
                        mode(RacingMode.TIME_TRIAL)
                        laps(5)
                        track("track_zone") {
                            waypoint(x = 0, y = 0)
                            waypoint(x = 10, y = 0)
                            waypoint(x = 10, y = 10, checkpoint = true)
                        }
                    }
                    scene("start") { enter {} }
                    start = "start"
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
        assertEquals("track_zone", config.track!!.zoneId)
        assertEquals(3, config.track!!.waypoints.size)
        assertTrue(config.track!!.waypoints[2].isCheckpoint)
    }

    @Test
    fun `racing AI opponent mode with waypoint track`() {
        val ir =
            game("SportTest") {
                    racing("ai_race") {
                        mode(RacingMode.AI_OPPONENT)
                        laps(3)
                        track("circuit") {
                            waypoint(x = 5, y = 5, checkpoint = true)
                            waypoint(x = 15, y = 5)
                            waypoint(x = 15, y = 15)
                            waypoint(x = 5, y = 15)
                        }
                    }
                    scene("start") { enter {} }
                    start = "start"
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
                    racing("rubber_race") {
                        mode(RacingMode.AI_OPPONENT)
                        ai {
                            speedPercent(90)
                            difficulty(8)
                            rubberBanding(enabled = true, strength = 75)
                        }
                    }
                    scene("start") { enter {} }
                    start = "start"
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
                    racing("vehicle_test") {
                        vehicle("car_fast") {
                            name("Speed Racer")
                            stats {
                                speed(220)
                                acceleration(180)
                                handling(160)
                            }
                        }
                    }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "vehicle_test" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        val config = rawSystem.config["config"] as RacingConfig
        assertEquals(1, config.vehicles.size)
        val vehicle = config.vehicles[0]
        assertEquals("car_fast", vehicle.id)
        assertEquals("Speed Racer", vehicle.name)
        assertEquals(220, vehicle.stats.speed)
        assertEquals(180, vehicle.stats.acceleration)
        assertEquals(160, vehicle.stats.handling)
    }

    @Test
    fun `multiple vehicles supported in racing config`() {
        val ir =
            game("SportTest") {
                    racing("multi_vehicle") {
                        vehicle("car_1") {
                            name("Speeder")
                            stats {
                                speed(220)
                                acceleration(160)
                                handling(140)
                            }
                        }
                        vehicle("car_2") {
                            name("Balanced")
                            stats {
                                speed(180)
                                acceleration(190)
                                handling(200)
                            }
                        }
                        vehicle("car_3") {
                            name("Grinder")
                            stats {
                                speed(150)
                                acceleration(210)
                                handling(230)
                            }
                        }
                    }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "multi_vehicle" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        val config = rawSystem.config["config"] as RacingConfig
        assertEquals(3, config.vehicles.size)
        assertEquals("car_1", config.vehicles[0].id)
        assertEquals("car_2", config.vehicles[1].id)
        assertEquals("car_3", config.vehicles[2].id)
    }

    @Test
    fun `racing GenericSystem type is sport_racing`() {
        val ir =
            game("SportTest") {
                    racing("type_check") { mode(RacingMode.TIME_TRIAL) }
                    scene("start") { enter {} }
                    start = "start"
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
                    racing("pickup_race") {
                        pickup("boost_pad", SportPickupType.SPEED_BOOST, durationFrames = 120)
                    }
                    scene("start") { enter {} }
                    start = "start"
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
    fun `racing defaults laps to 3 and mode to TIME_TRIAL`() {
        val ir =
            game("SportTest") {
                    racing("defaults_test") {}
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val rawSystem = ir.systems.find { it.id == "defaults_test" }
        assertNotNull(rawSystem)
        assertIs<GenericSystem>(rawSystem)
        val config = rawSystem.config["config"] as RacingConfig
        assertEquals(RacingMode.TIME_TRIAL, config.mode)
        assertEquals(3, config.laps)
        assertNull(config.track)
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
                    scene("start") { enter {} }
                    start = "start"
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
                    scene("start") { enter {} }
                    start = "start"
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
                    scene("start") { enter {} }
                    start = "start"
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
                    scene("start") { enter {} }
                    start = "start"
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
                    scene("start") { enter {} }
                    start = "start"
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
                    scene("start") { enter {} }
                    start = "start"
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
                    scene("start") { enter {} }
                    start = "start"
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
                    scene("start") { enter {} }
                    start = "start"
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
                    scene("start") { enter {} }
                    start = "start"
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
                    scene("start") { enter {} }
                    start = "start"
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
                    scene("start") { enter {} }
                    start = "start"
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
