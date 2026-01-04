/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.entity

// =============================================================================
// ENTITY REGISTRY - For tag queries
// =============================================================================

/** Registry for tracking entities by tag. */
class EntityRegistry {
    private val entities = mutableListOf<Entity>()
    private val byTag = mutableMapOf<String, MutableList<Entity>>()

    fun register(entity: Entity) {
        entities.add(entity)
        entity.tags.forEach { tag -> byTag.getOrPut(tag) { mutableListOf() }.add(entity) }
    }

    /** Get all entities with a specific tag */
    fun tagged(tag: String): List<Entity> = byTag[tag] ?: emptyList()

    /** Get all registered entities */
    fun all(): List<Entity> = entities.toList()

    /** Iterate over entities with a tag (generates unrolled code) */
    fun forEachTagged(tag: String, block: (Entity) -> Unit) {
        tagged(tag).forEach(block)
    }
}
