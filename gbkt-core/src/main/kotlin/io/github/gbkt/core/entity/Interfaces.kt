/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.entity

import io.github.gbkt.core.ir.AssignableExpr

// =============================================================================
// COMMON INTERFACES FOR ENTITIES AND POOLS
// Enables writing functions that work with both Entity and PoolEntityScope
// =============================================================================

/**
 * Interface for any game object with a position.
 *
 * Implemented by [Entity] and [PoolEntityScope].
 *
 * Usage:
 * ```kotlin
 * fun Positionable.wrapBounds(maxX: Int, maxY: Int) {
 *     whenever(x isAbove maxX) { x set 0 }
 *     whenever(y isAbove maxY) { y set 0 }
 * }
 *
 * // Works with both:
 * player.wrapBounds(160, 144)          // Entity
 * bullets.forEachActive { wrapBounds(160, 144) }  // PoolEntityScope
 * ```
 */
interface Positionable {
    /** X position as an assignable expression */
    val x: AssignableExpr

    /** Y position as an assignable expression */
    val y: AssignableExpr
}

/**
 * Interface for any game object with velocity.
 *
 * Extends [Positionable] since velocity implies position.
 *
 * Usage:
 * ```kotlin
 * fun Movable.applyVelocity() {
 *     x += velX
 *     y += velY
 * }
 * ```
 */
interface Movable : Positionable {
    /** X velocity as an assignable expression */
    val velX: AssignableExpr

    /** Y velocity as an assignable expression */
    val velY: AssignableExpr
}
