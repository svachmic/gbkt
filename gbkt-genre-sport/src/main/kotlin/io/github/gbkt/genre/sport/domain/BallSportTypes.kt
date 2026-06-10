/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "MatchingDeclarationName"
) // File contains multiple top-level declarations (ball sport domain types)

package io.github.gbkt.genre.sport.domain

// =============================================================================
// BALL SPORT DOMAIN TYPES
// =============================================================================
//
// Domain data classes for ball sport games (soccer, basketball, tennis, etc.).
// Plain Kotlin data classes — NOT IR types. Builders produce GenericSystem.
// =============================================================================

/** Determines what ends the match/game. */
enum class WinCondition {
    /** First to reach the target score wins. */
    FIRST_TO_SCORE,

    /** Player with the highest score at time limit wins. */
    HIGHEST_SCORE_AT_TIME,

    /** First to win a set number of rounds/sets wins. */
    BEST_OF_ROUNDS,
}

/**
 * Goal or net configuration for one end of the field.
 *
 * @property width Width of the goal opening in tiles.
 * @property height Height of the goal opening in tiles.
 */
data class GoalConfig(val width: Int = 2, val height: Int = 2) {
    init {
        require(width >= 1) { "Goal width ($width) must be >= 1" }
        require(height >= 1) { "Goal height ($height) must be >= 1" }
    }
}

/**
 * Field/court definition for ball sport games.
 *
 * Dimensions are in tiles. Goals are placed at opposing ends of the field.
 *
 * @property widthTiles Width of the field in tiles.
 * @property heightTiles Height of the field in tiles.
 * @property goalConfig Configuration for goals/nets. Default: 2x2 tiles.
 * @property hasGoals Whether the field has goals (false for sports like tennis). Default: true.
 */
data class FieldDef(
    val widthTiles: Int,
    val heightTiles: Int,
    val goalConfig: GoalConfig = GoalConfig(),
    val hasGoals: Boolean = true,
) {
    init {
        require(widthTiles >= 1) { "widthTiles ($widthTiles) must be >= 1" }
        require(heightTiles >= 1) { "heightTiles ($heightTiles) must be >= 1" }
    }
}

/**
 * Physics configuration for ball behavior.
 *
 * All values are in the range [0..255] to fit UINT8 hardware limits.
 *
 * @property speed Initial ball speed (0–255).
 * @property friction Deceleration per frame when ball rolls (0–255, 0 = no friction).
 * @property bounce Coefficient of restitution when ball hits a wall (0–255, 255 = perfect bounce).
 */
data class BallPhysicsConfig(val speed: Int = 128, val friction: Int = 8, val bounce: Int = 200) {
    init {
        require(speed in 0..255) { "speed ($speed) must be in 0..255" }
        require(friction in 0..255) { "friction ($friction) must be in 0..255" }
        require(bounce in 0..255) { "bounce ($bounce) must be in 0..255" }
    }
}

/**
 * Scoring rules for a ball sport game.
 *
 * @property pointsPerGoal Points awarded when a goal/basket is scored. Default: 1.
 * @property winCondition Condition that determines when a match is over.
 * @property targetScore Score limit when [winCondition] is [WinCondition.FIRST_TO_SCORE].
 *   Default: 10.
 * @property timeLimitSeconds Match duration in seconds (0 = no limit). Used with
 *   [WinCondition.HIGHEST_SCORE_AT_TIME]. Default: 120.
 */
data class ScoringRules(
    val pointsPerGoal: Int = 1,
    val winCondition: WinCondition = WinCondition.HIGHEST_SCORE_AT_TIME,
    val targetScore: Int = 10,
    val timeLimitSeconds: Int = 120,
) {
    init {
        require(pointsPerGoal >= 1) { "pointsPerGoal ($pointsPerGoal) must be >= 1" }
        require(targetScore >= 1) { "targetScore ($targetScore) must be >= 1" }
        require(timeLimitSeconds >= 0) { "timeLimitSeconds ($timeLimitSeconds) must be >= 0" }
    }
}

/**
 * Match structure configuration (halves, rounds, etc.).
 *
 * @property halves Number of halves/periods in a match (1 or 2). Default: 2.
 * @property roundsToWin Number of rounds/sets needed to win the match (for
 *   [WinCondition .BEST_OF_ROUNDS]). Default: 2.
 * @property halfDurationSeconds Duration of each half in seconds (0 = until score limit).
 *   Default: 60.
 */
data class MatchStructure(
    val halves: Int = 2,
    val roundsToWin: Int = 2,
    val halfDurationSeconds: Int = 60,
) {
    init {
        require(halves in 1..4) { "halves ($halves) must be in 1..4" }
        require(roundsToWin >= 1) { "roundsToWin ($roundsToWin) must be >= 1" }
        require(halfDurationSeconds >= 0) { "halfDurationSeconds must be >= 0" }
    }
}

/**
 * Top-level configuration for a ball sport system.
 *
 * @property id Unique identifier for this sport configuration.
 * @property field Field/court definition (dimensions, goal config).
 * @property ballPhysics Ball behavior configuration.
 * @property scoringRules Rules determining how points are scored and when match ends.
 * @property matchStructure Structure of a single match (halves, rounds, duration).
 * @property pickups List of power-up pickups available during play.
 */
data class BallSportConfig(
    val id: String,
    val field: FieldDef = FieldDef(widthTiles = 20, heightTiles = 16),
    val ballPhysics: BallPhysicsConfig = BallPhysicsConfig(),
    val scoringRules: ScoringRules = ScoringRules(),
    val matchStructure: MatchStructure = MatchStructure(),
    val pickups: List<SportPickupDef> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "BallSportConfig id must not be blank" }
    }
}
