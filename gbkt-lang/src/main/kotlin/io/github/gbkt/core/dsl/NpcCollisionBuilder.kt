/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.CollisionGroupIR
import io.github.gbkt.core.ir.CollisionResponse
import io.github.gbkt.core.ir.CollisionRuleIR
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

// =============================================================================
// NPC COLLISION DSL
// Provides type-safe collision group delegates and rule builders.
//
// Usage:
//   val bullets by collisionGroup()
//   val enemies by collisionGroup()
//   collisionRule(bullets, enemies, response = CollisionResponse.OVERLAP) {
//       // onCollide callback
//   }
//   val npc1 by actor {
//       collidesWithNpcs(true)   // simple opt-in: implicit _default_npc group
//   }
//   val tank by actor {
//       collisionGroup(enemies)  // explicit group assignment
//       mass(3)                  // heavier for PUSH response
//   }
// =============================================================================

/**
 * Type-safe reference to a declared collision group.
 *
 * Returned by [CollisionGroupDelegate.getValue] after the group is registered with the current
 * [GameBuilder]. Use in [collisionRule] calls to pair groups without magic strings.
 *
 * @property groupId The unique identifier for the group (inferred from the Kotlin property name).
 */
data class CollisionGroupRef(val groupId: String)

/**
 * Property delegate that registers a [CollisionGroupIR] with the current [GameBuilder] and returns
 * a type-safe [CollisionGroupRef] for use in [collisionRule] calls.
 *
 * Created via the [collisionGroup] factory function. On `provideDelegate`:
 * 1. Captures the Kotlin property name as the group ID.
 * 2. Registers [CollisionGroupIR] with [GameBuilder].
 * 3. Stores the [CollisionGroupRef] for retrieval by [getValue].
 *
 * Usage:
 * ```kotlin
 * val bullets by collisionGroup()   // registers group id "bullets"
 * val enemies by collisionGroup()   // registers group id "enemies"
 * ```
 */
class CollisionGroupDelegate : ReadOnlyProperty<Any?, CollisionGroupRef> {
    private var ref: CollisionGroupRef? = null

    /**
     * Called by Kotlin when `val x by collisionGroup()` is evaluated inside a game {} block.
     *
     * Captures the property name as group ID, registers the group with [GameBuilder], and stores
     * the [CollisionGroupRef] for retrieval by [getValue].
     */
    operator fun provideDelegate(
        thisRef: Any?,
        property: KProperty<*>,
    ): ReadOnlyProperty<Any?, CollisionGroupRef> {
        val groupId = property.name
        val gameBuilder =
            GameBuilderContext.current
                ?: error("collisionGroup() must be called inside a game {} block")
        gameBuilder.registerCollisionGroup(CollisionGroupIR(groupId))
        ref = CollisionGroupRef(groupId)
        return this
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): CollisionGroupRef =
        ref ?: error("CollisionGroupDelegate not initialized — was provideDelegate called?")
}

/**
 * Factory function for creating a collision group property delegate.
 *
 * Must be called inside a `game {}` block. The group ID is inferred from the Kotlin property name.
 *
 * ```kotlin
 * val projectiles by collisionGroup()
 * val enemies by collisionGroup()
 * collisionRule(projectiles, enemies, response = CollisionResponse.OVERLAP)
 * ```
 *
 * @return [CollisionGroupDelegate] for property delegation.
 */
fun GameBuilder.collisionGroup(): CollisionGroupDelegate = CollisionGroupDelegate()

/**
 * Defines a collision rule between two collision groups and registers it with the current
 * [GameBuilder].
 *
 * The GBDK backend generates a `check_collision_{groupA}_{groupB}()` function for each rule. That
 * function loops over all actors in groupA × groupB performing AABB overlap checks and dispatches
 * the built-in [response] behaviour.
 *
 * ```kotlin
 * val bullets by collisionGroup()
 * val enemies by collisionGroup()
 * collisionRule(bullets, enemies, response = CollisionResponse.OVERLAP, interval = 2) {
 *     // onCollide script callback — emitted inside the AABB check
 * }
 * ```
 *
 * @param a Type-safe reference to the first collision group.
 * @param b Type-safe reference to the second collision group.
 * @param response Built-in collision response. Defaults to [CollisionResponse.OVERLAP].
 * @param interval Frame interval between checks. 1 = every frame. 2 = every other frame, etc.
 * @param onCollide Optional script callback block. Ops are collected via [ScriptBuilder] and stored
 *   on [CollisionRuleIR.onCollide]. May be null.
 */
fun GameBuilder.collisionRule(
    a: CollisionGroupRef,
    b: CollisionGroupRef,
    response: CollisionResponse = CollisionResponse.OVERLAP,
    interval: Int = 1,
    onCollide: (ScriptBuilder.() -> Unit)? = null,
) {
    val ops = onCollide?.let { recordStatements(it) } ?: emptyList()
    registerCollisionRule(CollisionRuleIR(a.groupId, b.groupId, response, interval, ops))
}
