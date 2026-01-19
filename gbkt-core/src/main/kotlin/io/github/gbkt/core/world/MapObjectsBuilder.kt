/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.world

import io.github.gbkt.core.dsl.GbktDsl

// =============================================================================
// MAP OBJECTS BUILDER
// =============================================================================

/**
 * Builder for map objects within a zone.
 *
 * Supports generic objects using [GenericMapObjectBuilder] and [MapObjectTypeDefinition].
 *
 * Usage:
 * ```kotlin
 * objects {
 *     genericObject("chest1", PredefinedObjectTypes.CHEST) {
 *         position(10, 5)
 *         flag(0)
 *         property("item_0", "potion")
 *         property("quantity_0", "2")
 *         onInteract { showMessage("Found 2 Potions!") }
 *     }
 *
 *     genericObject("npc1", PredefinedObjectTypes.NPC) {
 *         position(15, 8)
 *         property("name", "Elder")
 *         property("facing", "DOWN")
 *         onInteract { showMessage("Welcome!") }
 *     }
 *
 *     genericObject("conveyor1", customConveyorType) {
 *         position(5, 10)
 *         property("direction", "right")
 *         onStep { movePlayer(direction) }
 *     }
 * }
 * ```
 *
 * @see GenericMapObject for the object data class
 * @see GenericMapObjectBuilder for object instance configuration
 * @see MapObjectTypeDefinition for defining custom object types
 * @see PredefinedObjectTypes for built-in object types
 */
@GbktDsl
class MapObjectsBuilder {
    private val genericObjects = mutableListOf<GenericMapObject>()

    /**
     * Add a generic object with any type definition.
     *
     * This is the recommended approach for all map objects.
     *
     * Usage:
     * ```kotlin
     * genericObject("chest1", PredefinedObjectTypes.CHEST) {
     *     position(10, 5)
     *     property("item", "potion")
     *     onInteract { /* ... */ }
     * }
     *
     * genericObject("conveyor1", customConveyorType) {
     *     position(5, 10)
     *     property("direction", "right")
     *     onStep { /* push player */ }
     * }
     * ```
     */
    fun genericObject(
        id: String,
        type: MapObjectTypeDefinition,
        init: GenericMapObjectBuilder.() -> Unit,
    ) {
        val builder = GenericMapObjectBuilder(id, type)
        builder.init()
        genericObjects.add(builder.build())
    }

    /** Build all generic objects. */
    internal fun build(): List<GenericMapObject> = genericObjects.toList()
}
