/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "MatchingDeclarationName"
) // File contains multiple top-level declarations (sport shared types)

package io.github.gbkt.genre.sport.domain

// =============================================================================
// SPORT SHARED TYPES
// =============================================================================
//
// Shared types used across racing, ball sports, and tournament sub-genres.
// The pickup system in sport games is a domain-level facade on top of the
// shared pickup system from gbkt-engine.
// =============================================================================

/**
 * Category of power-up effect during sport gameplay.
 *
 * These map to game-specific logic — the backend generates conditional checks.
 */
enum class SportPickupType {
    /** Temporarily increases speed (racing: vehicle top speed, ball sport: player movement). */
    SPEED_BOOST,

    /** Temporarily prevents opposing player from scoring or slows them down. */
    SHIELD,

    /** Doubles points scored for a short duration. */
    SCORE_MULTIPLIER,

    /** Temporarily disables opponent controls. */
    STUN,

    /** Repairs/restores a resource (racing: repairs; ball sport: stamina). */
    REPAIR,

    /** Custom pickup type — behavior defined by the developer via callbacks. */
    CUSTOM,
}

/**
 * A power-up/pickup definition for sport games.
 *
 * Pickups are placed at tile coordinates on the track/field. When a player overlaps the tile, the
 * pickup effect is activated and the tile is cleared.
 *
 * @property id Unique identifier for this pickup type.
 * @property type The category of effect this pickup provides.
 * @property durationFrames Duration of the pickup effect in game frames (0 = instant). Default: 60.
 * @property tileX Optional default placement tile column (may be overridden at runtime).
 * @property tileY Optional default placement tile row (may be overridden at runtime).
 */
data class SportPickupDef(
    val id: String,
    val type: SportPickupType,
    val durationFrames: Int = 60,
    val tileX: Int? = null,
    val tileY: Int? = null,
) {
    init {
        require(id.isNotBlank()) { "SportPickupDef id must not be blank" }
        require(durationFrames >= 0) { "durationFrames ($durationFrames) must be >= 0" }
        tileX?.let { require(it >= 0) { "tileX ($it) must be >= 0" } }
        tileY?.let { require(it >= 0) { "tileY ($it) must be >= 0" } }
    }
}
