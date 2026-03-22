/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "MatchingDeclarationName"
) // File contains multiple top-level declarations (racing domain types)

package io.github.gbkt.genre.sport.domain

// =============================================================================
// RACING DOMAIN TYPES
// =============================================================================
//
// Plain Kotlin data classes — NOT IR types. DSL builders produce GenericSystem
// from these domain types (no new sealed IR subtypes).
// =============================================================================

/** Determines the mode of racing play. */
enum class RacingMode {
    /** Race against the clock — no AI opponents. */
    TIME_TRIAL,

    /** Race against AI-controlled opponents with configurable difficulty. */
    AI_OPPONENT,
}

/**
 * A single waypoint on the racing track.
 *
 * Waypoints are tile coordinates that define the path AI opponents follow and the order in which
 * players must pass through checkpoints or complete laps.
 *
 * @property tileX Tile column index (0-based).
 * @property tileY Tile row index (0-based).
 * @property isCheckpoint Whether this waypoint is a lap checkpoint (triggers lap counting logic).
 */
data class WaypointDef(val tileX: Int, val tileY: Int, val isCheckpoint: Boolean = false) {
    init {
        require(tileX >= 0) { "tileX ($tileX) must be >= 0" }
        require(tileY >= 0) { "tileY ($tileY) must be >= 0" }
    }
}

/**
 * Tile-based track definition for racing games.
 *
 * Ties into the existing tilemap/zone system: the track is a named zone with waypoints overlaid for
 * AI navigation and lap/checkpoint detection.
 *
 * @property zoneId Reference to the zone/floor ID that holds the tile track data.
 * @property waypoints Ordered list of waypoints defining the racing path.
 * @property lapCount Number of laps required to complete a race. Default: 3.
 */
data class TrackDef(
    val zoneId: String,
    val waypoints: List<WaypointDef> = emptyList(),
    val lapCount: Int = 3,
) {
    init {
        require(zoneId.isNotBlank()) { "zoneId must not be blank" }
        require(lapCount >= 1) { "lapCount ($lapCount) must be >= 1" }
    }
}

/**
 * Vehicle performance statistics for racing games.
 *
 * All values are in the range [0..255] to fit UINT8 hardware registers on Game Boy.
 *
 * @property speed Top speed of the vehicle (0–255). Higher is faster.
 * @property acceleration Rate at which the vehicle reaches top speed (0–255).
 * @property handling Cornering ability — higher values reduce drift on turns (0–255).
 */
data class VehicleStats(val speed: Int, val acceleration: Int, val handling: Int) {
    init {
        require(speed in 0..255) { "speed ($speed) must be in 0..255" }
        require(acceleration in 0..255) { "acceleration ($acceleration) must be in 0..255" }
        require(handling in 0..255) { "handling ($handling) must be in 0..255" }
    }
}

/**
 * A vehicle definition used in racing games.
 *
 * @property id Unique identifier for this vehicle.
 * @property name Display name shown in vehicle select / race UI.
 * @property stats Performance statistics for the vehicle.
 */
data class VehicleDef(val id: String, val name: String, val stats: VehicleStats) {
    init {
        require(id.isNotBlank()) { "Vehicle id must not be blank" }
    }
}

/**
 * Configuration for AI opponent behavior in racing games.
 *
 * AI opponents follow the track waypoints and adjust their speed based on difficulty and rubber-
 * banding settings.
 *
 * @property speedPercent AI top speed as a percentage of the maximum vehicle speed (1–100).
 * @property difficulty Difficulty level affecting AI decision-making (1–10, default: 5).
 * @property rubberBanding Whether the AI uses rubber-banding to close gaps to the player. Default:
 *   false.
 * @property rubberBandStrength How aggressively AI closes the gap when rubber-banding is enabled
 *   (0–100). Ignored when [rubberBanding] is false.
 */
data class RacingAIConfig(
    val speedPercent: Int = 80,
    val difficulty: Int = 5,
    val rubberBanding: Boolean = false,
    val rubberBandStrength: Int = 50,
) {
    init {
        require(speedPercent in 1..100) { "speedPercent ($speedPercent) must be in 1..100" }
        require(difficulty in 1..10) { "difficulty ($difficulty) must be in 1..10" }
        require(rubberBandStrength in 0..100) {
            "rubberBandStrength ($rubberBandStrength) must be in 0..100"
        }
    }
}

/**
 * Top-level configuration for a racing system.
 *
 * @property id Unique identifier for this racing configuration.
 * @property mode Play mode — time trial or AI opponent race.
 * @property laps Number of laps required to finish. Default: 3.
 * @property track Track definition (zone reference + waypoints).
 * @property vehicles List of available vehicle definitions. At least one required.
 * @property aiConfig AI configuration — used only when [mode] is [RacingMode.AI_OPPONENT].
 * @property pickups List of power-up definitions available during the race.
 */
data class RacingConfig(
    val id: String,
    val mode: RacingMode = RacingMode.TIME_TRIAL,
    val laps: Int = 3,
    val track: TrackDef? = null,
    val vehicles: List<VehicleDef> = emptyList(),
    val aiConfig: RacingAIConfig = RacingAIConfig(),
    val pickups: List<SportPickupDef> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "RacingConfig id must not be blank" }
        require(laps >= 1) { "laps ($laps) must be >= 1" }
    }
}
