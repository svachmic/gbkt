/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.sport.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for sport genre domain data classes.
 *
 * Domain objects are plain Kotlin data classes — NOT IR types. Tests verify that construction
 * defaults, validation constraints, and equality semantics work correctly.
 */
class SportDomainTest {

    // -------------------------------------------------------------------------
    // VehicleStats
    // -------------------------------------------------------------------------

    @Test
    fun `VehicleStats construction with valid values`() {
        val stats = VehicleStats(speed = 200, acceleration = 150, handling = 180)
        assertEquals(200, stats.speed)
        assertEquals(150, stats.acceleration)
        assertEquals(180, stats.handling)
    }

    @Test
    fun `VehicleStats speed boundary values`() {
        val min = VehicleStats(speed = 0, acceleration = 0, handling = 0)
        assertEquals(0, min.speed)
        val max = VehicleStats(speed = 255, acceleration = 255, handling = 255)
        assertEquals(255, max.speed)
    }

    @Test
    fun `VehicleStats rejects speed above 255`() {
        assertFailsWith<IllegalArgumentException> {
            VehicleStats(speed = 256, acceleration = 100, handling = 100)
        }
    }

    @Test
    fun `VehicleStats rejects negative acceleration`() {
        assertFailsWith<IllegalArgumentException> {
            VehicleStats(speed = 100, acceleration = -1, handling = 100)
        }
    }

    @Test
    fun `VehicleStats supports copy()`() {
        val original = VehicleStats(speed = 200, acceleration = 150, handling = 180)
        val copy = original.copy(speed = 255)
        assertEquals(255, copy.speed)
        assertEquals(150, copy.acceleration)
    }

    @Test
    fun `VehicleStats equality`() {
        val stats1 = VehicleStats(speed = 200, acceleration = 150, handling = 180)
        val stats2 = VehicleStats(speed = 200, acceleration = 150, handling = 180)
        assertEquals(stats1, stats2)
        assertEquals(stats1.hashCode(), stats2.hashCode())
    }

    // -------------------------------------------------------------------------
    // WaypointDef
    // -------------------------------------------------------------------------

    @Test
    fun `WaypointDef construction with valid values`() {
        val waypoint = WaypointDef(tileX = 5, tileY = 10)
        assertEquals(5, waypoint.tileX)
        assertEquals(10, waypoint.tileY)
        assertFalse(waypoint.isCheckpoint)
    }

    @Test
    fun `WaypointDef checkpoint flag`() {
        val checkpoint = WaypointDef(tileX = 5, tileY = 10, isCheckpoint = true)
        assertTrue(checkpoint.isCheckpoint)
    }

    @Test
    fun `WaypointDef rejects negative tileX`() {
        assertFailsWith<IllegalArgumentException> { WaypointDef(tileX = -1, tileY = 5) }
    }

    @Test
    fun `WaypointDef rejects negative tileY`() {
        assertFailsWith<IllegalArgumentException> { WaypointDef(tileX = 5, tileY = -1) }
    }

    // -------------------------------------------------------------------------
    // TrackDef
    // -------------------------------------------------------------------------

    @Test
    fun `TrackDef construction with waypoints`() {
        val wp1 = WaypointDef(tileX = 0, tileY = 0)
        val wp2 = WaypointDef(tileX = 5, tileY = 5, isCheckpoint = true)
        val wp3 = WaypointDef(tileX = 10, tileY = 0)
        val track = TrackDef(zoneId = "race_track", waypoints = listOf(wp1, wp2, wp3), lapCount = 3)

        assertEquals("race_track", track.zoneId)
        assertEquals(3, track.waypoints.size)
        assertEquals(3, track.lapCount)
        assertTrue(track.waypoints[1].isCheckpoint)
    }

    @Test
    fun `TrackDef default lap count is 3`() {
        val track = TrackDef(zoneId = "track")
        assertEquals(3, track.lapCount)
    }

    @Test
    fun `TrackDef rejects blank zoneId`() {
        assertFailsWith<IllegalArgumentException> { TrackDef(zoneId = "") }
    }

    @Test
    fun `TrackDef rejects zero lapCount`() {
        assertFailsWith<IllegalArgumentException> { TrackDef(zoneId = "track", lapCount = 0) }
    }

    // -------------------------------------------------------------------------
    // RacingAIConfig
    // -------------------------------------------------------------------------

    @Test
    fun `RacingAIConfig defaults`() {
        val config = RacingAIConfig()
        assertEquals(80, config.speedPercent)
        assertEquals(5, config.difficulty)
        assertFalse(config.rubberBanding)
        assertEquals(50, config.rubberBandStrength)
    }

    @Test
    fun `RacingAIConfig with rubber banding enabled`() {
        val config = RacingAIConfig(rubberBanding = true, rubberBandStrength = 75)
        assertTrue(config.rubberBanding)
        assertEquals(75, config.rubberBandStrength)
    }

    @Test
    fun `RacingAIConfig rejects speedPercent out of range`() {
        assertFailsWith<IllegalArgumentException> { RacingAIConfig(speedPercent = 0) }
        assertFailsWith<IllegalArgumentException> { RacingAIConfig(speedPercent = 101) }
    }

    @Test
    fun `RacingAIConfig rejects difficulty out of range`() {
        assertFailsWith<IllegalArgumentException> { RacingAIConfig(difficulty = 0) }
        assertFailsWith<IllegalArgumentException> { RacingAIConfig(difficulty = 11) }
    }

    // -------------------------------------------------------------------------
    // RacingConfig
    // -------------------------------------------------------------------------

    @Test
    fun `RacingConfig construction with defaults`() {
        val config = RacingConfig(id = "grand_prix")
        assertEquals("grand_prix", config.id)
        assertEquals(RacingMode.TIME_TRIAL, config.mode)
        assertEquals(3, config.laps)
        assertTrue(config.vehicles.isEmpty())
        assertTrue(config.pickups.isEmpty())
    }

    @Test
    fun `RacingConfig with AI opponent mode`() {
        val vehicle =
            VehicleDef(
                id = "car_1",
                name = "Speed Racer",
                stats = VehicleStats(speed = 200, acceleration = 180, handling = 160),
            )
        val config =
            RacingConfig(id = "ai_race", mode = RacingMode.AI_OPPONENT, vehicles = listOf(vehicle))
        assertEquals(RacingMode.AI_OPPONENT, config.mode)
        assertEquals(1, config.vehicles.size)
        assertEquals("car_1", config.vehicles[0].id)
    }

    // -------------------------------------------------------------------------
    // FieldDef
    // -------------------------------------------------------------------------

    @Test
    fun `FieldDef construction with explicit dimensions`() {
        val field = FieldDef(widthTiles = 20, heightTiles = 16)
        assertEquals(20, field.widthTiles)
        assertEquals(16, field.heightTiles)
        assertTrue(field.hasGoals)
    }

    @Test
    fun `FieldDef with no goals (tennis-style)`() {
        val field = FieldDef(widthTiles = 20, heightTiles = 12, hasGoals = false)
        assertFalse(field.hasGoals)
    }

    @Test
    fun `FieldDef rejects zero dimensions`() {
        assertFailsWith<IllegalArgumentException> { FieldDef(widthTiles = 0, heightTiles = 10) }
        assertFailsWith<IllegalArgumentException> { FieldDef(widthTiles = 10, heightTiles = 0) }
    }

    // -------------------------------------------------------------------------
    // ScoringRules
    // -------------------------------------------------------------------------

    @Test
    fun `ScoringRules default values`() {
        val rules = ScoringRules()
        assertEquals(1, rules.pointsPerGoal)
        assertEquals(WinCondition.HIGHEST_SCORE_AT_TIME, rules.winCondition)
        assertEquals(10, rules.targetScore)
        assertEquals(120, rules.timeLimitSeconds)
    }

    @Test
    fun `ScoringRules with FIRST_TO_SCORE win condition`() {
        val rules = ScoringRules(winCondition = WinCondition.FIRST_TO_SCORE, targetScore = 5)
        assertEquals(WinCondition.FIRST_TO_SCORE, rules.winCondition)
        assertEquals(5, rules.targetScore)
    }

    @Test
    fun `ScoringRules with BEST_OF_ROUNDS win condition`() {
        val rules = ScoringRules(winCondition = WinCondition.BEST_OF_ROUNDS)
        assertEquals(WinCondition.BEST_OF_ROUNDS, rules.winCondition)
    }

    @Test
    fun `ScoringRules rejects zero pointsPerGoal`() {
        assertFailsWith<IllegalArgumentException> { ScoringRules(pointsPerGoal = 0) }
    }

    // -------------------------------------------------------------------------
    // BallPhysicsConfig
    // -------------------------------------------------------------------------

    @Test
    fun `BallPhysicsConfig default values`() {
        val config = BallPhysicsConfig()
        assertEquals(128, config.speed)
        assertEquals(8, config.friction)
        assertEquals(200, config.bounce)
    }

    @Test
    fun `BallPhysicsConfig with custom values`() {
        val config = BallPhysicsConfig(speed = 200, friction = 16, bounce = 220)
        assertEquals(200, config.speed)
        assertEquals(16, config.friction)
        assertEquals(220, config.bounce)
    }

    // -------------------------------------------------------------------------
    // TournamentConfig
    // -------------------------------------------------------------------------

    @Test
    fun `TournamentConfig single-elimination bracket`() {
        val config =
            TournamentConfig(
                id = "championship",
                bracketType = BracketType.SINGLE_ELIMINATION,
                participantIds = listOf("team_a", "team_b", "team_c", "team_d"),
            )
        assertEquals("championship", config.id)
        assertEquals(BracketType.SINGLE_ELIMINATION, config.bracketType)
        assertEquals(4, config.participantIds.size)
    }

    @Test
    fun `TournamentConfig round-robin bracket`() {
        val config =
            TournamentConfig(
                id = "league",
                bracketType = BracketType.ROUND_ROBIN,
                participantIds = listOf("team_a", "team_b", "team_c"),
            )
        assertEquals(BracketType.ROUND_ROBIN, config.bracketType)
        assertEquals(3, config.participantIds.size)
    }

    @Test
    fun `TournamentConfig default values`() {
        val config = TournamentConfig(id = "cup")
        assertEquals(BracketType.SINGLE_ELIMINATION, config.bracketType)
        assertEquals(1, config.roundsPerMatch)
        assertTrue(config.standings.isEmpty())
    }

    @Test
    fun `TournamentConfig rejects single participant`() {
        assertFailsWith<IllegalArgumentException> {
            TournamentConfig(id = "cup", participantIds = listOf("only_one"))
        }
    }

    // -------------------------------------------------------------------------
    // StandingEntry
    // -------------------------------------------------------------------------

    @Test
    fun `StandingEntry construction with defaults`() {
        val entry = StandingEntry(participantId = "team_a")
        assertEquals("team_a", entry.participantId)
        assertEquals(0, entry.wins)
        assertEquals(0, entry.losses)
        assertEquals(0, entry.draws)
        assertEquals(0, entry.points)
    }

    @Test
    fun `StandingEntry with explicit record`() {
        val entry =
            StandingEntry(participantId = "team_a", wins = 3, losses = 1, draws = 0, points = 9)
        assertEquals(3, entry.wins)
        assertEquals(1, entry.losses)
        assertEquals(9, entry.points)
    }

    // -------------------------------------------------------------------------
    // SportPickupDef
    // -------------------------------------------------------------------------

    @Test
    fun `SportPickupDef construction`() {
        val pickup =
            SportPickupDef(
                id = "speed_boost",
                type = SportPickupType.SPEED_BOOST,
                durationFrames = 120,
                tileX = 10,
                tileY = 5,
            )
        assertEquals("speed_boost", pickup.id)
        assertEquals(SportPickupType.SPEED_BOOST, pickup.type)
        assertEquals(120, pickup.durationFrames)
        assertEquals(10, pickup.tileX)
        assertEquals(5, pickup.tileY)
    }

    @Test
    fun `SportPickupDef with null tile position`() {
        val pickup = SportPickupDef(id = "shield", type = SportPickupType.SHIELD)
        assertNull(pickup.tileX)
        assertNull(pickup.tileY)
        assertEquals(60, pickup.durationFrames) // default
    }

    @Test
    fun `SportPickupDef rejects blank id`() {
        assertFailsWith<IllegalArgumentException> {
            SportPickupDef(id = "", type = SportPickupType.CUSTOM)
        }
    }

    @Test
    fun `SportPickupDef rejects negative durationFrames`() {
        assertFailsWith<IllegalArgumentException> {
            SportPickupDef(
                id = "pickup",
                type = SportPickupType.SCORE_MULTIPLIER,
                durationFrames = -1,
            )
        }
    }
}
