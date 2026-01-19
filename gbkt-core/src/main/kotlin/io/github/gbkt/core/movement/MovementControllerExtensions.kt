/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.movement

import io.github.gbkt.core.builder.GameBuilder
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// MOVEMENT CONTROLLER PROPERTY DELEGATES
// =============================================================================

/** Property delegate for grid movement controllers. */
class GridMovementDelegate(
    private val gameBuilder: GameBuilder,
    private val init: GridMovementBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, GridMovementController>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, GridMovementController> {
        val builder = GridMovementBuilder(property.name)
        builder.init()
        val controller = builder.build()
        gameBuilder.registerMovementController(controller)

        return ReadOnlyProperty { _, _ -> controller }
    }
}

/** Property delegate for physics movement controllers. */
class PhysicsMovementDelegate(
    private val gameBuilder: GameBuilder,
    private val init: PhysicsMovementBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, PhysicsMovementController>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, PhysicsMovementController> {
        val builder = PhysicsMovementBuilder(property.name)
        builder.init()
        val controller = builder.build()
        gameBuilder.registerMovementController(controller)

        return ReadOnlyProperty { _, _ -> controller }
    }
}

/** Property delegate for free-roam movement controllers. */
class FreeRoamMovementDelegate(
    private val gameBuilder: GameBuilder,
    private val init: FreeRoamMovementBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, FreeRoamMovementController>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, FreeRoamMovementController> {
        val builder = FreeRoamMovementBuilder(property.name)
        builder.init()
        val controller = builder.build()
        gameBuilder.registerMovementController(controller)

        return ReadOnlyProperty { _, _ -> controller }
    }
}

/** Property delegate for top-down movement controllers. */
class TopDownMovementDelegate(
    private val gameBuilder: GameBuilder,
    private val init: TopDownMovementBuilder.() -> Unit,
) : PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, TopDownMovementController>> {

    override fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, TopDownMovementController> {
        val builder = TopDownMovementBuilder(property.name)
        builder.init()
        val controller = builder.build()
        gameBuilder.registerMovementController(controller)

        return ReadOnlyProperty { _, _ -> controller }
    }
}

// =============================================================================
// GAME BUILDER EXTENSIONS
// =============================================================================

/**
 * Create a grid-based movement controller.
 *
 * For tile-based games like RPGs, puzzle games, and dungeon crawlers.
 *
 * Usage:
 * ```kotlin
 * val movement by gridMovement {
 *     tileSize(8)
 *     speed(4)  // frames per tile
 *     smoothInterpolation(true)
 *
 *     onStep { checkEncounter() }
 *     onBlocked { playSound(bump) }
 * }
 * ```
 */
fun GameBuilder.gridMovement(init: GridMovementBuilder.() -> Unit): GridMovementDelegate {
    return GridMovementDelegate(this, init)
}

/**
 * Create a physics-based movement controller.
 *
 * For platformers and action games with velocity, gravity, and jumping.
 *
 * Usage:
 * ```kotlin
 * val movement by physicsMovement {
 *     tileSize(8)
 *     gravity(4)
 *     jumpVelocity(-64)
 *     maxSpeedX(48)
 *     maxSpeedY(80)
 *     airJumps(1)  // double jump
 *
 *     onLand { playSound(land) }
 *     onBlocked { /* wall collision */ }
 * }
 * ```
 */
fun GameBuilder.physicsMovement(init: PhysicsMovementBuilder.() -> Unit): PhysicsMovementDelegate {
    return PhysicsMovementDelegate(this, init)
}

/**
 * Create a free-roam movement controller.
 *
 * For shooters and games with unrestricted pixel movement.
 *
 * Usage:
 * ```kotlin
 * val movement by freeRoamMovement {
 *     speed(3)
 *     eightDirection(true)
 *     maxSpeed(6)
 *
 *     onPositionChange { updateCamera() }
 * }
 * ```
 */
fun GameBuilder.freeRoamMovement(
    init: FreeRoamMovementBuilder.() -> Unit
): FreeRoamMovementDelegate {
    return FreeRoamMovementDelegate(this, init)
}

/**
 * Create a top-down movement controller.
 *
 * For Zelda-like games with smooth pixel movement and tile collision.
 *
 * Usage:
 * ```kotlin
 * val movement by topDownMovement {
 *     tileSize(8)
 *     speed(2)
 *     hitbox(8, 8)
 *     hitboxOffset(0, 8)  // feet hitbox
 *
 *     onTileEnter { checkTriggers() }
 *     onBlocked { pushBack() }
 * }
 * ```
 */
fun GameBuilder.topDownMovement(init: TopDownMovementBuilder.() -> Unit): TopDownMovementDelegate {
    return TopDownMovementDelegate(this, init)
}
