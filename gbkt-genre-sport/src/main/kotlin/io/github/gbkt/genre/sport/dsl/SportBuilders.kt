/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "TooManyFunctions"
) // Sport genre DSL has many builder methods — mirrors RPG module pattern

package io.github.gbkt.genre.sport.dsl

import io.github.gbkt.genre.sport.domain.BallPhysicsConfig
import io.github.gbkt.genre.sport.domain.BallSportConfig
import io.github.gbkt.genre.sport.domain.BracketType
import io.github.gbkt.genre.sport.domain.FieldDef
import io.github.gbkt.genre.sport.domain.GoalConfig
import io.github.gbkt.genre.sport.domain.MatchStructure
import io.github.gbkt.genre.sport.domain.RacingAIConfig
import io.github.gbkt.genre.sport.domain.RacingConfig
import io.github.gbkt.genre.sport.domain.RacingMode
import io.github.gbkt.genre.sport.domain.ScoringRules
import io.github.gbkt.genre.sport.domain.SportPickupDef
import io.github.gbkt.genre.sport.domain.SportPickupType
import io.github.gbkt.genre.sport.domain.StandingEntry
import io.github.gbkt.genre.sport.domain.TournamentConfig
import io.github.gbkt.genre.sport.domain.TrackDef
import io.github.gbkt.genre.sport.domain.VehicleDef
import io.github.gbkt.genre.sport.domain.VehicleStats
import io.github.gbkt.genre.sport.domain.WaypointDef
import io.github.gbkt.genre.sport.domain.WinCondition

/** Shared factory for sport pickup definitions, used by both racing and ball-sport builders. */
private fun buildSportPickup(pickupId: String, type: SportPickupType, durationFrames: Int): SportPickupDef =
    SportPickupDef(id = pickupId, type = type, durationFrames = durationFrames)

// =============================================================================
// RACING DSL BUILDERS
// =============================================================================

/** Builder for [WaypointDef]. Accumulates tile coordinates and checkpoint flag. */
class WaypointBuilder {
    private var tileX: Int = 0
    private var tileY: Int = 0
    private var isCheckpoint: Boolean = false

    /** Sets the tile column of this waypoint. */
    fun x(value: Int) {
        tileX = value
    }

    /** Sets the tile row of this waypoint. */
    fun y(value: Int) {
        tileY = value
    }

    /** Marks this waypoint as a lap checkpoint. */
    fun checkpoint() {
        isCheckpoint = true
    }

    fun build(): WaypointDef =
        WaypointDef(tileX = tileX, tileY = tileY, isCheckpoint = isCheckpoint)
}

/** Builder for [TrackDef]. Associates a zone ID with an ordered list of waypoints. */
class TrackBuilder(private val zoneId: String) {
    private val waypoints: MutableList<WaypointDef> = mutableListOf()
    private var lapCount: Int = 3

    /** Adds a waypoint using coordinate sugar: `waypoint(x = 5, y = 10)`. */
    fun waypoint(x: Int, y: Int, checkpoint: Boolean = false) {
        waypoints += WaypointDef(tileX = x, tileY = y, isCheckpoint = checkpoint)
    }

    /** Adds a waypoint using the block DSL. */
    fun waypoint(block: WaypointBuilder.() -> Unit) {
        val builder = WaypointBuilder()
        builder.block()
        waypoints += builder.build()
    }

    /** Sets the number of laps to complete the race. */
    fun laps(count: Int) {
        lapCount = count
    }

    fun build(): TrackDef =
        TrackDef(zoneId = zoneId, waypoints = waypoints.toList(), lapCount = lapCount)
}

/** Builder for [VehicleStats]. Collects speed, acceleration, and handling values. */
class VehicleStatsBuilder {
    private var speed: Int = 128
    private var acceleration: Int = 128
    private var handling: Int = 128

    /** Sets the vehicle top speed (0–255). */
    fun speed(value: Int) {
        speed = value
    }

    /** Sets the vehicle acceleration (0–255). */
    fun acceleration(value: Int) {
        acceleration = value
    }

    /** Sets the vehicle handling/cornering (0–255). */
    fun handling(value: Int) {
        handling = value
    }

    fun build(): VehicleStats =
        VehicleStats(speed = speed, acceleration = acceleration, handling = handling)
}

/** Builder for [RacingAIConfig]. Configures AI opponent behavior. */
class RacingAIConfigBuilder {
    private var speedPercent: Int = 80
    private var difficulty: Int = 5
    private var rubberBanding: Boolean = false
    private var rubberBandStrength: Int = 50

    /** Sets AI speed as a percentage of the vehicle's top speed (1–100). */
    fun speedPercent(value: Int) {
        speedPercent = value
    }

    /** Sets the AI difficulty level (1–10). */
    fun difficulty(value: Int) {
        difficulty = value
    }

    /** Enables rubber-banding so the AI closes gaps to the player. */
    fun rubberBanding(enabled: Boolean = true, strength: Int = 50) {
        rubberBanding = enabled
        rubberBandStrength = strength
    }

    fun build(): RacingAIConfig =
        RacingAIConfig(
            speedPercent = speedPercent,
            difficulty = difficulty,
            rubberBanding = rubberBanding,
            rubberBandStrength = rubberBandStrength,
        )
}

/**
 * Builder for [RacingConfig]. Top-level builder for a racing game configuration.
 *
 * ```kotlin
 * racing("grand_prix") {
 *     mode(RacingMode.AI_OPPONENT)
 *     laps(3)
 *     track("race_zone") {
 *         waypoint(x = 5, y = 5, checkpoint = true)
 *         waypoint(x = 10, y = 3)
 *     }
 *     vehicle("car_fast") {
 *         name("Speed Racer")
 *         stats { speed(220); acceleration(180); handling(160) }
 *     }
 *     ai {
 *         speedPercent(85)
 *         difficulty(7)
 *         rubberBanding(enabled = true, strength = 60)
 *     }
 * }
 * ```
 */
class RacingBuilder(val id: String) {
    private var mode: RacingMode = RacingMode.TIME_TRIAL
    private var laps: Int = 3
    private var track: TrackDef? = null
    private val vehicles: MutableList<VehicleDef> = mutableListOf()
    private var aiConfig: RacingAIConfig = RacingAIConfig()
    private val pickups: MutableList<SportPickupDef> = mutableListOf()

    /** Sets the racing play mode. */
    fun mode(racingMode: RacingMode) {
        mode = racingMode
    }

    /** Sets the number of laps to complete the race. */
    fun laps(count: Int) {
        laps = count
    }

    /** Defines the racing track with a zone reference and waypoint configuration. */
    fun track(zoneId: String, block: TrackBuilder.() -> Unit = {}) {
        val builder = TrackBuilder(zoneId)
        builder.block()
        track = builder.build()
    }

    /** Adds a vehicle definition to the available vehicle roster. */
    fun vehicle(vehicleId: String, block: VehicleDefBuilder.() -> Unit) {
        val builder = VehicleDefBuilder(vehicleId)
        builder.block()
        vehicles += builder.build()
    }

    /** Configures AI opponent behavior. Only used when mode is [RacingMode.AI_OPPONENT]. */
    fun ai(block: RacingAIConfigBuilder.() -> Unit) {
        val builder = RacingAIConfigBuilder()
        builder.block()
        aiConfig = builder.build()
    }

    /** Adds a pickup/power-up to the racing game. */
    fun pickup(pickupId: String, type: SportPickupType, durationFrames: Int = 60) {
        pickups += buildSportPickup(pickupId, type, durationFrames)
    }

    fun build(): RacingConfig =
        RacingConfig(
            id = id,
            mode = mode,
            laps = laps,
            track = track,
            vehicles = vehicles.toList(),
            aiConfig = aiConfig,
            pickups = pickups.toList(),
        )
}

/** Builder for [VehicleDef]. Associates a vehicle ID with its name and stats. */
class VehicleDefBuilder(val id: String) {
    private var vehicleName: String = id
    private var vehicleStats: VehicleStats =
        VehicleStats(speed = 128, acceleration = 128, handling = 128)

    /** Sets the vehicle display name. */
    fun name(n: String) {
        vehicleName = n
    }

    /** Configures the vehicle's performance stats. */
    fun stats(block: VehicleStatsBuilder.() -> Unit) {
        val builder = VehicleStatsBuilder()
        builder.block()
        vehicleStats = builder.build()
    }

    fun build(): VehicleDef = VehicleDef(id = id, name = vehicleName, stats = vehicleStats)
}

// =============================================================================
// BALL SPORT DSL BUILDERS
// =============================================================================

/** Builder for [GoalConfig]. */
class GoalConfigBuilder {
    private var width: Int = 2
    private var height: Int = 2

    /** Sets the goal opening width in tiles. */
    fun width(value: Int) {
        width = value
    }

    /** Sets the goal opening height in tiles. */
    fun height(value: Int) {
        height = value
    }

    fun build(): GoalConfig = GoalConfig(width = width, height = height)
}

/** Builder for [FieldDef]. Defines field/court dimensions and goal configuration. */
class FieldDefBuilder {
    private var widthTiles: Int = 20
    private var heightTiles: Int = 16
    private var goalConfig: GoalConfig = GoalConfig()
    private var hasGoals: Boolean = true

    /** Sets field width in tiles. */
    fun width(tiles: Int) {
        widthTiles = tiles
    }

    /** Sets field height in tiles. */
    fun height(tiles: Int) {
        heightTiles = tiles
    }

    /** Configures the goal/net dimensions. */
    fun goal(block: GoalConfigBuilder.() -> Unit) {
        val builder = GoalConfigBuilder()
        builder.block()
        goalConfig = builder.build()
    }

    /** Disables goals (e.g., for tennis-style sports). */
    fun noGoals() {
        hasGoals = false
    }

    fun build(): FieldDef =
        FieldDef(
            widthTiles = widthTiles,
            heightTiles = heightTiles,
            goalConfig = goalConfig,
            hasGoals = hasGoals,
        )
}

/** Builder for [BallPhysicsConfig]. */
class BallPhysicsBuilder {
    private var speed: Int = 128
    private var friction: Int = 8
    private var bounce: Int = 200

    /** Sets the initial ball speed (0–255). */
    fun speed(value: Int) {
        speed = value
    }

    /** Sets ball deceleration per frame (0–255). */
    fun friction(value: Int) {
        friction = value
    }

    /** Sets ball bounce coefficient (0–255). */
    fun bounce(value: Int) {
        bounce = value
    }

    fun build(): BallPhysicsConfig =
        BallPhysicsConfig(speed = speed, friction = friction, bounce = bounce)
}

/** Builder for [ScoringRules]. */
class ScoringRulesBuilder {
    private var pointsPerGoal: Int = 1
    private var winCondition: WinCondition = WinCondition.HIGHEST_SCORE_AT_TIME
    private var targetScore: Int = 10
    private var timeLimitSeconds: Int = 120

    /** Sets points awarded per goal. */
    fun pointsPerGoal(value: Int) {
        pointsPerGoal = value
    }

    /** Sets the win condition. */
    fun winCondition(condition: WinCondition) {
        winCondition = condition
    }

    /** Sets the score target (for [WinCondition.FIRST_TO_SCORE]). */
    fun targetScore(score: Int) {
        targetScore = score
    }

    /** Sets the match time limit in seconds. */
    fun timeLimitSeconds(seconds: Int) {
        timeLimitSeconds = seconds
    }

    fun build(): ScoringRules =
        ScoringRules(
            pointsPerGoal = pointsPerGoal,
            winCondition = winCondition,
            targetScore = targetScore,
            timeLimitSeconds = timeLimitSeconds,
        )
}

/** Builder for [MatchStructure]. */
class MatchStructureBuilder {
    private var halves: Int = 2
    private var roundsToWin: Int = 2
    private var halfDurationSeconds: Int = 60

    /** Sets the number of halves/periods in the match. */
    fun halves(count: Int) {
        halves = count
    }

    /** Sets the number of rounds needed to win (for [WinCondition.BEST_OF_ROUNDS]). */
    fun roundsToWin(count: Int) {
        roundsToWin = count
    }

    /** Sets duration of each half in seconds. */
    fun halfDuration(seconds: Int) {
        halfDurationSeconds = seconds
    }

    fun build(): MatchStructure =
        MatchStructure(
            halves = halves,
            roundsToWin = roundsToWin,
            halfDurationSeconds = halfDurationSeconds,
        )
}

/**
 * Builder for [BallSportConfig]. Top-level builder for ball sport game configurations.
 *
 * ```kotlin
 * ballSport("soccer") {
 *     field {
 *         width(20); height(16)
 *         goal { width(2); height(3) }
 *     }
 *     ball {
 *         speed(150); friction(10); bounce(210)
 *     }
 *     scoring {
 *         pointsPerGoal(1)
 *         winCondition(WinCondition.HIGHEST_SCORE_AT_TIME)
 *         timeLimitSeconds(120)
 *     }
 *     match {
 *         halves(2)
 *         halfDuration(60)
 *     }
 * }
 * ```
 */
class BallSportBuilder(val id: String) {
    private var field: FieldDef = FieldDef(widthTiles = 20, heightTiles = 16)
    private var ballPhysics: BallPhysicsConfig = BallPhysicsConfig()
    private var scoringRules: ScoringRules = ScoringRules()
    private var matchStructure: MatchStructure = MatchStructure()
    private val pickups: MutableList<SportPickupDef> = mutableListOf()

    /** Configures the field/court dimensions and goal settings. */
    fun field(block: FieldDefBuilder.() -> Unit) {
        val builder = FieldDefBuilder()
        builder.block()
        field = builder.build()
    }

    /** Configures ball physics behavior. */
    fun ball(block: BallPhysicsBuilder.() -> Unit) {
        val builder = BallPhysicsBuilder()
        builder.block()
        ballPhysics = builder.build()
    }

    /** Configures scoring rules and win conditions. */
    fun scoring(block: ScoringRulesBuilder.() -> Unit) {
        val builder = ScoringRulesBuilder()
        builder.block()
        scoringRules = builder.build()
    }

    /** Configures match structure (halves, rounds, duration). */
    fun match(block: MatchStructureBuilder.() -> Unit) {
        val builder = MatchStructureBuilder()
        builder.block()
        matchStructure = builder.build()
    }

    /** Adds a pickup/power-up to the ball sport game. */
    fun pickup(pickupId: String, type: SportPickupType, durationFrames: Int = 60) {
        pickups += buildSportPickup(pickupId, type, durationFrames)
    }

    fun build(): BallSportConfig =
        BallSportConfig(
            id = id,
            field = field,
            ballPhysics = ballPhysics,
            scoringRules = scoringRules,
            matchStructure = matchStructure,
            pickups = pickups.toList(),
        )
}

// =============================================================================
// TOURNAMENT DSL BUILDERS
// =============================================================================

/**
 * Builder for [TournamentConfig]. Manages participants, bracket type, and rounds per match.
 *
 * ```kotlin
 * tournament("world_cup") {
 *     bracketType(BracketType.SINGLE_ELIMINATION)
 *     participants("team_a", "team_b", "team_c", "team_d")
 *     roundsPerMatch(1)
 * }
 * ```
 */
class TournamentBuilder(val id: String) {
    private var bracketType: BracketType = BracketType.SINGLE_ELIMINATION
    private val participantIds: MutableList<String> = mutableListOf()
    private var roundsPerMatch: Int = 1
    private val standings: MutableList<StandingEntry> = mutableListOf()

    /** Sets the bracket/tournament format. */
    fun bracketType(type: BracketType) {
        bracketType = type
    }

    /** Adds one or more participant IDs to the tournament roster. */
    fun participants(vararg ids: String) {
        participantIds.addAll(ids)
    }

    /** Adds a single participant ID to the tournament roster. */
    fun participant(pid: String) {
        participantIds += pid
    }

    /** Sets the number of rounds/sets per individual match. */
    fun roundsPerMatch(count: Int) {
        roundsPerMatch = count
    }

    /** Initializes a standing entry for a participant. */
    fun standing(
        participantId: String,
        wins: Int = 0,
        losses: Int = 0,
        draws: Int = 0,
        points: Int = 0,
    ) {
        standings +=
            StandingEntry(
                participantId = participantId,
                wins = wins,
                losses = losses,
                draws = draws,
                points = points,
            )
    }

    fun build(): TournamentConfig {
        require(participantIds.size >= 2) {
            "Tournament '$id' requires at least 2 participants, got ${participantIds.size}"
        }
        return TournamentConfig(
            id = id,
            bracketType = bracketType,
            participantIds = participantIds.toList(),
            roundsPerMatch = roundsPerMatch,
            standings = standings.toList(),
        )
    }
}
