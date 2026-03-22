/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.pickup

import io.github.gbkt.core.ir.GenericSystem

// =============================================================================
// PICKUP DEF BUILDER
// =============================================================================

/**
 * Sub-builder for a single pickup type definition.
 *
 * Used within [PickupBuilder.pickup] block.
 *
 * ```kotlin
 * pickup("gold_coin") {
 *     effectType("instant")
 *     value(10)
 *     respawn(120)
 * }
 * ```
 *
 * @param id Unique pickup identifier.
 */
class PickupDefBuilder(val id: String) {
    private var effectType: String = "instant"
    private var value: Int = 1
    private var duration: Int = 0
    private var respawnFrames: Int = 0
    private var maxActive: Int = 8

    /**
     * Sets the effect timing mode.
     *
     * @param type One of `"instant"`, `"timed"`, or `"permanent"`.
     */
    fun effectType(type: String) {
        require(type in VALID_EFFECT_TYPES) {
            "effectType must be one of $VALID_EFFECT_TYPES, got '$type'"
        }
        effectType = type
    }

    private companion object {
        val VALID_EFFECT_TYPES = setOf("instant", "timed", "permanent")
    }

    /** Sets the numeric value of this pickup (score, HP, etc.). */
    fun value(amount: Int) {
        value = amount
    }

    /**
     * Sets the active duration for `"timed"` effect mode (frames).
     *
     * Ignored for `"instant"` and `"permanent"` modes.
     */
    fun duration(frames: Int) {
        duration = frames
    }

    /** Sets the respawn delay in frames (0 = no respawn after collection). */
    fun respawn(frames: Int) {
        respawnFrames = frames
    }

    /** Sets the maximum number of this pickup type active on screen simultaneously. */
    fun maxActive(count: Int) {
        maxActive = count
    }

    /** Builds the [PickupDef] domain object. */
    fun build(): PickupDef =
        PickupDef(
            id = id,
            effectType = effectType,
            value = value,
            duration = duration,
            respawnFrames = respawnFrames,
            maxActive = maxActive,
        )
}

// =============================================================================
// PICKUP ZONE BUILDER
// =============================================================================

/**
 * Sub-builder for a pickup spawn zone.
 *
 * Used within [PickupBuilder.zone] block.
 *
 * ```kotlin
 * zone("coin_zone_1", pickupId = "gold_coin") {
 *     position(x = 40, y = 80)
 *     size(width = 16, height = 16)
 * }
 * ```
 *
 * @param id Unique zone identifier.
 * @param pickupId ID of the pickup type that spawns here.
 */
class PickupZoneBuilder(val id: String, val pickupId: String) {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 8
    private var height: Int = 8

    /** Sets the top-left position of the zone (pixels). */
    fun position(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    /** Sets the dimensions of the zone (pixels). */
    fun size(width: Int, height: Int) {
        this.width = width
        this.height = height
    }

    /** Sets the x coordinate of the zone (pixels). */
    fun x(value: Int) {
        x = value
    }

    /** Sets the y coordinate of the zone (pixels). */
    fun y(value: Int) {
        y = value
    }

    /** Sets the width of the zone (pixels). */
    fun width(value: Int) {
        width = value
    }

    /** Sets the height of the zone (pixels). */
    fun height(value: Int) {
        height = value
    }

    /** Builds the [PickupZone] domain object. */
    fun build(): PickupZone {
        require(width > 0) { "PickupZone '$id' width must be positive, got $width" }
        require(height > 0) { "PickupZone '$id' height must be positive, got $height" }
        return PickupZone(
            id = id,
            x = x,
            y = y,
            width = width,
            height = height,
            pickupId = pickupId,
        )
    }
}

// =============================================================================
// PICKUP SYSTEM BUILDER
// =============================================================================

/**
 * Builder for the shared pickup/collectible system.
 *
 * Produces a [GenericSystem] with type `"pickup_system"` containing a [PickupSystemConfig] in its
 * config map.
 *
 * Used directly by platformer and sport/racing genre packages to define collectible mechanics
 * without duplicating logic. Genre-specific facades (e.g. `CollectibleDefBuilder`,
 * `SportPickupDefBuilder`) eventually delegate to this engine construct.
 *
 * ```kotlin
 * val pickups = PickupBuilder("pickups") {
 *     pickup("coin") {
 *         effectType("instant")
 *         value(10)
 *         respawn(120)
 *     }
 *     pickup("speed_boost") {
 *         effectType("timed")
 *         value(1)
 *         duration(180)
 *     }
 *     zone("coin_1", pickupId = "coin") {
 *         position(40, 80)
 *         size(8, 8)
 *     }
 *     maxPickups(32)
 * }.build()
 * ```
 *
 * @param id Unique system identifier.
 */
class PickupBuilder(val id: String = "pickups") {
    private val pickups: MutableList<PickupDef> = mutableListOf()
    private val zones: MutableList<PickupZone> = mutableListOf()
    private var maxTotalPickups: Int = 16

    /**
     * Defines a pickup type by ID.
     *
     * ```kotlin
     * pickup("gold_coin") {
     *     effectType("instant")
     *     value(10)
     *     respawn(120)
     * }
     * ```
     */
    fun pickup(id: String, block: PickupDefBuilder.() -> Unit = {}) {
        val builder = PickupDefBuilder(id)
        builder.block()
        pickups.add(builder.build())
    }

    /**
     * Defines a spawn zone for a pickup type.
     *
     * ```kotlin
     * zone("coin_spot_1", pickupId = "gold_coin") {
     *     position(40, 80)
     *     size(8, 8)
     * }
     * ```
     */
    fun zone(id: String, pickupId: String, block: PickupZoneBuilder.() -> Unit = {}) {
        val builder = PickupZoneBuilder(id, pickupId)
        builder.block()
        zones.add(builder.build())
    }

    /** Sets the maximum total pickups active across all types simultaneously. */
    fun maxPickups(count: Int) {
        maxTotalPickups = count
    }

    /**
     * Builds a [GenericSystem] with type `"pickup_system"`.
     *
     * The config map contains:
     * - `"type"` → `"pickup_system"`
     * - `"pickupConfig"` → [PickupSystemConfig]
     */
    fun build(): GenericSystem {
        val pickupIds = pickups.map { it.id }.toSet()
        val danglingZones = zones.filter { it.pickupId !in pickupIds }
        require(danglingZones.isEmpty()) {
            val refs = danglingZones.joinToString { "'${it.id}' -> '${it.pickupId}'" }
            "PickupBuilder '$id': zones reference undefined pickup IDs: $refs"
        }
        val config =
            PickupSystemConfig(
                pickups = pickups.toList(),
                zones = zones.toList(),
                maxTotalPickups = maxTotalPickups,
            )
        return GenericSystem(
            id = id,
            config = mapOf("type" to "pickup_system", "pickupConfig" to config),
        )
    }
}
