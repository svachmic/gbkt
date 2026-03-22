/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "MatchingDeclarationName"
) // File contains multiple top-level declarations (tournament domain types)

package io.github.gbkt.genre.sport.domain

// =============================================================================
// TOURNAMENT DOMAIN TYPES
// =============================================================================
//
// Match → Round → Tournament/Season structure with bracket tracking.
// Plain Kotlin data classes — NOT IR types.
// =============================================================================

/** How the tournament bracket is organized. */
enum class BracketType {
    /** Single-elimination: one loss ends a team's participation. */
    SINGLE_ELIMINATION,

    /** Round-robin: every participant plays every other participant once. */
    ROUND_ROBIN,

    /** Double-elimination: two losses end participation; has winners/losers bracket. */
    DOUBLE_ELIMINATION,
}

/**
 * A single standing entry tracking a participant's record in the tournament.
 *
 * @property participantId Unique identifier for the team/player in the tournament.
 * @property wins Number of matches won.
 * @property losses Number of matches lost.
 * @property draws Number of drawn matches (for round-robin).
 * @property points Points accumulated (for round-robin standings). Default: 0.
 */
data class StandingEntry(
    val participantId: String,
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0,
    val points: Int = 0,
) {
    init {
        require(participantId.isNotBlank()) { "participantId must not be blank" }
        require(wins >= 0) { "wins ($wins) must be >= 0" }
        require(losses >= 0) { "losses ($losses) must be >= 0" }
        require(draws >= 0) { "draws ($draws) must be >= 0" }
        require(points >= 0) { "points ($points) must be >= 0" }
    }
}

/**
 * A single match definition within a tournament.
 *
 * @property matchId Unique identifier for this match.
 * @property participant1Id First participant.
 * @property participant2Id Second participant.
 * @property roundsPerMatch Number of sub-rounds/sets in a single match. Default: 1.
 */
data class TournamentMatch(
    val matchId: String,
    val participant1Id: String,
    val participant2Id: String,
    val roundsPerMatch: Int = 1,
) {
    init {
        require(matchId.isNotBlank()) { "matchId must not be blank" }
        require(participant1Id.isNotBlank()) { "participant1Id must not be blank" }
        require(participant2Id.isNotBlank()) { "participant2Id must not be blank" }
        require(roundsPerMatch >= 1) { "roundsPerMatch ($roundsPerMatch) must be >= 1" }
    }
}

/**
 * Top-level configuration for a tournament or league season.
 *
 * Tracks participants, bracket type, and the initial standings table. The runtime advances matches
 * by updating the standings.
 *
 * @property id Unique identifier for this tournament configuration.
 * @property bracketType Bracket/format of the tournament.
 * @property participantIds List of participant IDs enrolled in this tournament. Must be >= 2.
 * @property roundsPerMatch Default number of rounds/sets per match in this tournament. Default: 1.
 * @property standings Initial standings list (typically empty at tournament start).
 */
data class TournamentConfig(
    val id: String,
    val bracketType: BracketType = BracketType.SINGLE_ELIMINATION,
    val participantIds: List<String> = emptyList(),
    val roundsPerMatch: Int = 1,
    val standings: List<StandingEntry> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "TournamentConfig id must not be blank" }
        require(roundsPerMatch >= 1) { "roundsPerMatch ($roundsPerMatch) must be >= 1" }
        require(participantIds.size >= 2 || participantIds.isEmpty()) {
            "TournamentConfig must have 0 or >= 2 participants"
        }
    }
}
