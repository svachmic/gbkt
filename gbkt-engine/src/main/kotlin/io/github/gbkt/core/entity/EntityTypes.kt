/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.entity

/**
 * Contract for objects that have a position in 2D game space.
 *
 * Game Boy screen coordinates: x in 0..159, y in 0..143.
 */
interface Positionable {
    val x: Int
    val y: Int
}

/**
 * Contract for objects that can move within 2D game space.
 *
 * Extends [Positionable] with mutation operations.
 */
interface Movable : Positionable {
    /** Teleports the entity to the given absolute coordinates. */
    fun moveTo(x: Int, y: Int)

    /** Translates the entity by the given relative offsets. */
    fun moveBy(dx: Int, dy: Int)
}

/**
 * Axis-Aligned Bounding Box used for collision detection.
 *
 * Coordinates are relative to the entity's origin (top-left corner of the sprite).
 *
 * @property x Left edge offset from the entity's x position.
 * @property y Top edge offset from the entity's y position.
 * @property width Width of the collision box in pixels.
 * @property height Height of the collision box in pixels.
 */
data class Hitbox(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * Runtime state snapshot for an entity.
 *
 * @property visible Whether the entity's sprite is currently rendered.
 * @property active Whether the entity participates in frame updates and collision.
 */
data class EntityState(val visible: Boolean = true, val active: Boolean = true)
