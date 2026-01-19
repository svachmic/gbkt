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
 * Mock implementation of AssetService for testing.
 *
 * Provides additional verification capabilities beyond the interface.
 */
class MockAssetService : AssetService {
    private val _assetPaths = mutableSetOf<String>()
    private val _validationCalls = mutableListOf<String>()

    /** All asset paths that have been registered. */
    val registeredAssets: Set<String>
        get() = _assetPaths.toSet()

    /** All paths that have been checked via validateAsset. */
    val validationCalls: List<String>
        get() = _validationCalls.toList()

    override fun resolveAsset(path: String): String? {
        return if (path in _assetPaths) path else null
    }

    override fun validateAsset(path: String): Boolean {
        _validationCalls.add(path)
        return path in _assetPaths
    }

    override fun getAssetPaths(): Set<String> = _assetPaths.toSet()

    override fun registerAsset(path: String) {
        _assetPaths.add(path)
    }

    /** Reset the mock to initial state. */
    fun reset() {
        _assetPaths.clear()
        _validationCalls.clear()
    }
}

/**
 * Mock implementation of SpriteService for testing.
 *
 * Provides additional verification capabilities beyond the interface.
 *
 * ## Example
 *
 * ```kotlin
 * @Test
 * fun `sprites are registered correctly`() {
 *     val mockSprites = MockSpriteService()
 *     val services = TestGameServices(sprites = mockSprites)
 *
 *     testGame("test", services) {
 *         sprite(Assets.Sprites.player) { size = 8 x 16 }
 *         test {
 *             assertEquals(1, mockSprites.registeredSprites.size)
 *             assertEquals("player.png", mockSprites.registeredSprites[0].asset)
 *         }
 *     }
 * }
 * ```
 */
class MockSpriteService : SpriteService {
    private val _sprites = mutableListOf<Sprite>()
    private var _nextSlot = 0

    /** All sprites that have been registered. */
    val registeredSprites: List<Sprite>
        get() = _sprites.toList()

    /** Number of slots that have been allocated. */
    val allocatedSlots: Int
        get() = _nextSlot

    override fun allocateSlot(): Int = _nextSlot++

    override fun registerSprite(sprite: Sprite) {
        _sprites.add(sprite)
    }

    override fun getSprites(): List<Sprite> = _sprites.toList()

    /** Reset the mock to initial state. */
    fun reset() {
        _sprites.clear()
        _nextSlot = 0
    }
}

/**
 * Mock implementation of VariableService for testing.
 *
 * Provides additional verification capabilities beyond the interface.
 */
class MockVariableService : VariableService {
    private val _variables = mutableListOf<GBVar<*>>()
    private val _arrays = mutableListOf<GBArray>()

    /** All variables that have been registered. */
    val registeredVariables: List<GBVar<*>>
        get() = _variables.toList()

    /** All arrays that have been registered. */
    val registeredArrays: List<GBArray>
        get() = _arrays.toList()

    override fun registerVariable(variable: GBVar<*>) {
        _variables.add(variable)
    }

    override fun registerArray(array: GBArray) {
        _arrays.add(array)
    }

    override fun getVariables(): List<GBVar<*>> = _variables.toList()

    override fun getArrays(): List<GBArray> = _arrays.toList()

    /** Reset the mock to initial state. */
    fun reset() {
        _variables.clear()
        _arrays.clear()
    }
}

/**
 * Mock implementation of EntityService for testing.
 *
 * Provides additional verification capabilities beyond the interface.
 */
class MockEntityService : EntityService {
    private val _entities = mutableListOf<Entity>()

    /** All entities that have been registered. */
    val registeredEntities: List<Entity>
        get() = _entities.toList()

    override fun registerEntity(entity: Entity) {
        _entities.add(entity)
    }

    override fun getEntities(): List<Entity> = _entities.toList()

    override fun queryByTag(tag: TagRef): List<Entity> {
        return _entities.filter { entity -> entity.tagComponent?.tags?.contains(tag.name) == true }
    }

    /** Reset the mock to initial state. */
    fun reset() {
        _entities.clear()
    }
}

/**
 * Test implementation of GameServices with mock services.
 *
 * Use this in tests to verify how game code interacts with services.
 *
 * ## Example
 *
 * ```kotlin
 * @Test
 * fun `player entity is registered`() {
 *     val mockEntities = MockEntityService()
 *     val services = TestGameServices(entities = mockEntities)
 *
 *     testGame("test", services) {
 *         val player by entity {
 *             position(80, 72)
 *         }
 *         test {
 *             assertEquals(1, mockEntities.registeredEntities.size)
 *         }
 *     }
 * }
 * ```
 */
class TestGameServices(
    override val assets: AssetService = MockAssetService(),
    override val sprites: SpriteService = MockSpriteService(),
    override val variables: VariableService = MockVariableService(),
    override val entities: EntityService = MockEntityService(),
) : GameServices {
    /** Reset all mock services to initial state. */
    fun reset() {
        (assets as? MockAssetService)?.reset()
        (sprites as? MockSpriteService)?.reset()
        (variables as? MockVariableService)?.reset()
        (entities as? MockEntityService)?.reset()
    }
}
