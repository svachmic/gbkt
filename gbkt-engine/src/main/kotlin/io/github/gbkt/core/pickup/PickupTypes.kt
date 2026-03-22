/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.pickup

// =============================================================================
// SHARED PICKUP / COLLECTIBLE SYSTEM TYPES
//
// Generic pickup domain types shared across genre packages.
// Both platformer collectibles and racing/sport pickups target these constructs,
// avoiding duplicate collectible logic.
//
// Genre-specific conversion extensions (CollectibleDef.toPickupDef(),
// SportPickupDef.toPickupDef()) live in each genre module's codegen package —
// gbkt-engine must NOT depend on genre modules.
// =============================================================================

/**
 * Definition of a generic pickup that can be collected by the player.
 *
 * Supports three effect timing modes:
 * - `"instant"` — effect applied immediately on collection (coins, score)
 * - `"timed"` — effect lasts for [duration] frames (speed boost, invincibility)
 * - `"permanent"` — effect persists across scenes (key items, permanent upgrades)
 *
 * @property id Unique pickup identifier used in generated C identifiers.
 * @property effectType Timing mode: `"instant"`, `"timed"`, or `"permanent"`.
 * @property value Numeric value of the pickup (score points, HP amount, etc.).
 * @property duration Active duration in frames for `"timed"` effects (ignored for others).
 * @property respawnFrames Frames after collection before this pickup reappears (0 = no respawn).
 * @property maxActive Maximum number of this pickup type active on screen simultaneously.
 */
data class PickupDef(
    val id: String,
    val effectType: String = "instant", // "instant", "timed", "permanent"
    val value: Int = 1,
    val duration: Int = 0,
    val respawnFrames: Int = 0,
    val maxActive: Int = 8,
)

/**
 * A rectangular spawn zone where pickups of a specific type appear.
 *
 * The engine uses this to place pickups in the world and check for AABB overlap with the player
 * hitbox during `pickup_check_collect()`.
 *
 * @property id Unique zone identifier.
 * @property x Left edge of the zone (pixels).
 * @property y Top edge of the zone (pixels).
 * @property width Width of the zone (pixels).
 * @property height Height of the zone (pixels).
 * @property pickupId ID of the [PickupDef] that spawns in this zone.
 */
data class PickupZone(
    val id: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val pickupId: String,
)

/**
 * Full configuration for the pickup system passed to the backend.
 *
 * Aggregates all pickup definitions and their spawn zones. Produced by [PickupBuilder.build] and
 * stored in the [io.github.gbkt.core.ir.GenericSystem] config map.
 *
 * @property pickups All pickup type definitions.
 * @property zones Spawn zones that place pickups in the world.
 * @property maxTotalPickups Maximum total pickups active across all types simultaneously.
 */
data class PickupSystemConfig(
    val pickups: List<PickupDef> = emptyList(),
    val zones: List<PickupZone> = emptyList(),
    val maxTotalPickups: Int = 16,
)
