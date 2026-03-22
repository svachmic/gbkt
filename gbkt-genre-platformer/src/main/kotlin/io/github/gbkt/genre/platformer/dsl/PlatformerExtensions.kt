/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.platformer.dsl

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.ir.GenericSystem

// =============================================================================
// PLATFORMER DSL EXTENSIONS ON GameBuilder
// =============================================================================
//
// These functions extend GameBuilder with platformer-specific DSL constructs.
// Pattern mirrors gbkt-genre-rpg/RpgExtensions.kt:
//   - gbkt-genre-platformer depends on gbkt-lang (which api-exposes gbkt-ir)
//   - GameBuilder does NOT know about platformer types
//   - Platformer builders produce CORE IR types (GenericSystem) — no new sealed subtypes
//
// =============================================================================

/**
 * Configures and registers the platformer physics system.
 *
 * Produces a [GenericSystem] with type `"platformer_physics"` and a full
 * [io.github.gbkt.genre.platformer.domain.PlatformerPhysicsConfig] in its config map.
 *
 * ```kotlin
 * platformerPhysics {
 *     gravity(2)
 *     jumpForce(8)
 *     coyoteTime(6)
 *     jumpBuffer(8)
 *     wallJump { slideSpeed(1); iFrames(8) }
 * }
 * ```
 *
 * Variable-height jump is enabled by default. Call `fixedJump()` to disable.
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 *
 * @param id Unique system identifier (default "physics").
 * @param block Configuration block executed against a [PlatformerPhysicsBuilder].
 */
fun GameBuilder.platformerPhysics(
    id: String = "physics",
    block: PlatformerPhysicsBuilder.() -> Unit,
): GenericSystem {
    val builder = PlatformerPhysicsBuilder(id)
    builder.block()
    val system = builder.build()
    registerSystem(system)
    return system
}

/**
 * Configures and registers the platformer camera system.
 *
 * Produces a [GenericSystem] with type `"platformer_camera"` and a
 * [io.github.gbkt.genre.platformer.domain.PlatformerCameraConfig] in its config map.
 *
 * ```kotlin
 * platformerCamera {
 *     smoothFollow()
 *     deadZone(x = 8, y = 16)
 *     horizontal()
 *     parallax("bg_sky") { speedX(20) }
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 *
 * @param id Unique system identifier (default "camera").
 * @param block Configuration block executed against a [PlatformerCameraBuilder].
 */
fun GameBuilder.platformerCamera(
    id: String = "camera",
    block: PlatformerCameraBuilder.() -> Unit,
): GenericSystem {
    val builder = PlatformerCameraBuilder(id)
    builder.block()
    val system = builder.build()
    registerSystem(system)
    return system
}

/**
 * Defines and registers a named platform definition.
 *
 * Produces a [GenericSystem] with type `"platformer_platform"` and a
 * [io.github.gbkt.genre.platformer.domain.PlatformDef] in its config map.
 *
 * ```kotlin
 * platform("moving_floor") {
 *     type(PlatformType.MOVING)
 *     moveSpeed(2)
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 *
 * @param id Unique platform identifier.
 * @param block Configuration block executed against a [PlatformDefBuilder].
 */
fun GameBuilder.platform(id: String, block: PlatformDefBuilder.() -> Unit): GenericSystem {
    val builder = PlatformDefBuilder(id)
    builder.block()
    val system = builder.build()
    registerSystem(system)
    return system
}

/**
 * Defines and registers a hazard tile definition.
 *
 * Produces a [GenericSystem] with type `"platformer_hazard"` and a
 * [io.github.gbkt.genre.platformer.domain.HazardDef] in its config map.
 *
 * ```kotlin
 * hazard("spikes") {
 *     tileId(42)
 *     damage(5)
 * }
 * hazard("pit") { tileId(0); instantDeath() }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 *
 * @param id Unique hazard identifier.
 * @param block Configuration block executed against a [HazardDefBuilder].
 */
fun GameBuilder.hazard(id: String, block: HazardDefBuilder.() -> Unit): GenericSystem {
    val builder = HazardDefBuilder(id)
    builder.block()
    val system = builder.build()
    registerSystem(system)
    return system
}

/**
 * Defines and registers a level-completion goal zone.
 *
 * Produces a [GenericSystem] with type `"platformer_goal"` and a
 * [io.github.gbkt.genre.platformer.domain.GoalZoneDef] in its config map.
 *
 * ```kotlin
 * goalZone("exit") {
 *     position(x = 200, y = 50)
 *     size(width = 16, height = 32)
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 *
 * @param id Unique goal-zone identifier.
 * @param block Configuration block executed against a [GoalZoneBuilder].
 */
fun GameBuilder.goalZone(id: String, block: GoalZoneBuilder.() -> Unit): GenericSystem {
    val builder = GoalZoneBuilder(id)
    builder.block()
    val system = builder.build()
    registerSystem(system)
    return system
}

/**
 * Defines and registers a collectible item definition.
 *
 * Produces a [GenericSystem] with type `"platformer_collectible"` and a
 * [io.github.gbkt.genre.platformer.domain.CollectibleDef] in its config map. Acts as a facade over
 * the shared engine PickupDef; codegen integration is wired in Plan 10.
 *
 * ```kotlin
 * collectible("gold_coin") {
 *     type(CollectibleType.COIN)
 *     value(10)
 *     tileId(5)
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 *
 * @param id Unique collectible identifier.
 * @param block Configuration block executed against a [CollectibleDefBuilder].
 */
fun GameBuilder.collectible(id: String, block: CollectibleDefBuilder.() -> Unit): GenericSystem {
    val builder = CollectibleDefBuilder(id)
    builder.block()
    val system = builder.build()
    registerSystem(system)
    return system
}

/**
 * Configures and registers ladder tile settings.
 *
 * Produces a [GenericSystem] with type `"platformer_ladder"` and a
 * [io.github.gbkt.genre.platformer.domain.LadderConfig] in its config map.
 *
 * ```kotlin
 * ladder {
 *     climbSpeed(2)
 *     tileId(15)
 * }
 * ```
 *
 * **Design constraint:** NO new sealed IR subtypes are created.
 *
 * @param id Unique identifier for this ladder configuration (default "ladder").
 * @param block Configuration block executed against a [LadderConfigBuilder].
 */
fun GameBuilder.ladder(
    id: String = "ladder",
    block: LadderConfigBuilder.() -> Unit,
): GenericSystem {
    val builder = LadderConfigBuilder(id)
    builder.block()
    val system = builder.build()
    registerSystem(system)
    return system
}
