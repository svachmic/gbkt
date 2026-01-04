/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.services

import io.github.gbkt.core.TagRef
import io.github.gbkt.core.entity.Entity
import io.github.gbkt.core.graphics.Sprite
import io.github.gbkt.core.ir.GBArray
import io.github.gbkt.core.ir.GBVar

/**
 * Service for asset resolution and validation.
 *
 * This interface enables build-time validation of asset references and provides a mockable
 * abstraction for testing.
 */
interface AssetService {
    /** Resolve an asset path to its full location. Returns null if not found. */
    fun resolveAsset(path: String): String?

    /** Check if an asset exists and is valid. */
    fun validateAsset(path: String): Boolean

    /** Get all registered asset paths. */
    fun getAssetPaths(): Set<String>

    /** Register an asset path. */
    fun registerAsset(path: String)
}

/**
 * Service for managing sprite allocation and registration.
 *
 * This interface abstracts sprite management away from GameBuilder, allowing for easier testing and
 * mocking.
 *
 * ## Example
 *
 * ```kotlin
 * class MockSpriteService : SpriteService {
 *     private val sprites = mutableListOf<Sprite>()
 *     private var nextSlot = 0
 *
 *     override fun allocateSlot() = nextSlot++
 *     override fun registerSprite(sprite: Sprite) { sprites.add(sprite) }
 *     override fun getSprites() = sprites.toList()
 * }
 * ```
 */
interface SpriteService {
    /** Allocate the next available OAM slot for a sprite. */
    fun allocateSlot(): Int

    /** Register a sprite with the service. */
    fun registerSprite(sprite: Sprite)

    /** Get all registered sprites. */
    fun getSprites(): List<Sprite>
}

/**
 * Service for managing game variable registration.
 *
 * This interface abstracts variable management away from GameBuilder, enabling variable tracking
 * for testing and validation.
 */
interface VariableService {
    /** Register a game variable. */
    fun registerVariable(variable: GBVar<*>)

    /** Register an array variable. */
    fun registerArray(array: GBArray)

    /** Get all registered variables. */
    fun getVariables(): List<GBVar<*>>

    /** Get all registered arrays. */
    fun getArrays(): List<GBArray>
}

/**
 * Service for managing entity registration and queries.
 *
 * This interface abstracts entity management away from GameBuilder, enabling entity tracking for
 * testing and type-safe queries.
 */
interface EntityService {
    /** Register an entity. */
    fun registerEntity(entity: Entity)

    /** Get all registered entities. */
    fun getEntities(): List<Entity>

    /** Query entities by tag. */
    fun queryByTag(tag: TagRef): List<Entity>
}

/**
 * Aggregated services interface for dependency injection.
 *
 * This interface provides access to all game services and can be injected into GameBuilder for
 * testability.
 *
 * ## Example
 *
 * ```kotlin
 * // Production usage (default services)
 * game("MyGame") { ... }
 *
 * // Test usage (mock services)
 * val mockServices = TestGameServices()
 * testGame("test", services = mockServices) {
 *     sprite(SpriteAsset("player.png")) { size = 8 x 16 }
 *     test {
 *         assertEquals(1, mockServices.sprites.getSprites().size)
 *     }
 * }
 * ```
 */
interface GameServices {
    /** Asset resolution and validation service. */
    val assets: AssetService

    /** Sprite allocation and registration service. */
    val sprites: SpriteService

    /** Variable registration service. */
    val variables: VariableService

    /** Entity registration and query service. */
    val entities: EntityService
}
