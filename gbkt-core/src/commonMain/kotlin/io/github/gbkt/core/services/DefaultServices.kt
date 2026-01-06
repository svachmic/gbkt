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
 * Default implementation of AssetService.
 *
 * This implementation tracks registered asset paths for production use.
 */
class DefaultAssetService : AssetService {
    private val assetPaths = mutableSetOf<String>()

    override fun resolveAsset(path: String): String? {
        return if (path in assetPaths) path else null
    }

    override fun validateAsset(path: String): Boolean {
        return path in assetPaths
    }

    override fun getAssetPaths(): Set<String> = assetPaths.toSet()

    override fun registerAsset(path: String) {
        assetPaths.add(path)
    }
}

/**
 * Default implementation of SpriteService.
 *
 * This implementation manages sprite slot allocation and registration for production use.
 */
class DefaultSpriteService : SpriteService {
    private val sprites = mutableListOf<Sprite>()
    private var nextSlot = 0

    override fun allocateSlot(): Int = nextSlot++

    override fun registerSprite(sprite: Sprite) {
        sprites.add(sprite)
    }

    override fun getSprites(): List<Sprite> = sprites.toList()
}

/**
 * Default implementation of VariableService.
 *
 * This implementation manages variable and array registration for production use.
 */
class DefaultVariableService : VariableService {
    private val variables = mutableListOf<GBVar<*>>()
    private val arrays = mutableListOf<GBArray>()

    override fun registerVariable(variable: GBVar<*>) {
        variables.add(variable)
    }

    override fun registerArray(array: GBArray) {
        arrays.add(array)
    }

    override fun getVariables(): List<GBVar<*>> = variables.toList()

    override fun getArrays(): List<GBArray> = arrays.toList()
}

/**
 * Default implementation of EntityService.
 *
 * This implementation manages entity registration and tag-based queries for production use.
 */
class DefaultEntityService : EntityService {
    private val entities = mutableListOf<Entity>()

    override fun registerEntity(entity: Entity) {
        entities.add(entity)
    }

    override fun getEntities(): List<Entity> = entities.toList()

    override fun queryByTag(tag: TagRef): List<Entity> {
        return entities.filter { entity -> entity.tagComponent?.tags?.contains(tag.name) == true }
    }
}

/**
 * Default implementation of GameServices.
 *
 * This provides the production implementations of all game services. Used by GameBuilder when no
 * custom services are injected.
 */
class DefaultGameServices(
    override val assets: AssetService = DefaultAssetService(),
    override val sprites: SpriteService = DefaultSpriteService(),
    override val variables: VariableService = DefaultVariableService(),
    override val entities: EntityService = DefaultEntityService(),
) : GameServices
