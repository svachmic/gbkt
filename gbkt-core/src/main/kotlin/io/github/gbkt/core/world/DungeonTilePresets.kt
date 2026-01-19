/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.world

/**
 * Pre-built tile attribute definitions matching the legacy TileAttribute enum.
 *
 * These can be used directly when migrating from the deprecated TileAttribute enum to the
 * extensible tile system.
 *
 * Usage:
 * ```kotlin
 * // Before (deprecated)
 * map("main") {
 *     walls(0, 1, 2)  // Uses TileAttribute.WALL enum
 * }
 *
 * // After (recommended)
 * map("main") {
 *     tileAttributes(DungeonTilePresets.WALL, 0, 1, 2)
 * }
 * ```
 *
 * @see ExtensibleTileAttributeDefinition for creating custom tile types
 * @see TilePresets for builder-based presets with more customization
 */
object DungeonTilePresets {
    /** Ground tile - passable floor tile for walking. Equivalent to legacy TileAttribute.GROUND. */
    val GROUND: ExtensibleTileAttributeDefinition =
        createAttribute(
            id = "ground",
            displayName = "Ground",
            behaviors = setOf(TileBehavior.PASSABLE),
        )

    /** Wall tile - blocks movement. Equivalent to legacy TileAttribute.WALL. */
    val WALL: ExtensibleTileAttributeDefinition =
        createAttribute(id = "wall", displayName = "Wall", behaviors = setOf(TileBehavior.BLOCKING))

    /**
     * Exit tile - passable, triggers area transition on enter. Equivalent to legacy
     * TileAttribute.EXIT.
     */
    val EXIT: ExtensibleTileAttributeDefinition =
        createAttribute(
            id = "exit",
            displayName = "Exit",
            behaviors = setOf(TileBehavior.PASSABLE, TileBehavior.TRIGGER_ON_ENTER),
        )

    /**
     * Special tile - passable, triggers custom callback on enter. Equivalent to legacy
     * TileAttribute.SPECIAL. Use onSpecial callback in floor definition for custom behavior.
     */
    val SPECIAL: ExtensibleTileAttributeDefinition =
        createAttribute(
            id = "special",
            displayName = "Special",
            behaviors = setOf(TileBehavior.PASSABLE, TileBehavior.TRIGGER_ON_ENTER),
        )

    /** Water tile - passable with half speed. Equivalent to legacy TileAttribute.WATER. */
    val WATER: ExtensibleTileAttributeDefinition =
        createAttribute(
            id = "water",
            displayName = "Water",
            behaviors = setOf(TileBehavior.PASSABLE, TileBehavior.SLOWING),
            speedModifier = 0.5f,
        )

    /**
     * Pit tile - passable but causes instant death (999 damage). Equivalent to legacy
     * TileAttribute.PIT.
     */
    val PIT: ExtensibleTileAttributeDefinition =
        createAttribute(
            id = "pit",
            displayName = "Pit",
            behaviors = setOf(TileBehavior.PASSABLE, TileBehavior.HAZARDOUS),
            damage = 999,
            damageInterval = 1,
        )

    /** Ladder tile - passable and climbable. Equivalent to legacy TileAttribute.LADDER. */
    val LADDER: ExtensibleTileAttributeDefinition =
        createAttribute(
            id = "ladder",
            displayName = "Ladder",
            behaviors = setOf(TileBehavior.PASSABLE, TileBehavior.CLIMBABLE),
        )

    /** Get all dungeon presets as a list. */
    val ALL: List<ExtensibleTileAttributeDefinition> =
        listOf(GROUND, WALL, EXIT, SPECIAL, WATER, PIT, LADDER)

    private fun createAttribute(
        id: String,
        displayName: String,
        behaviors: Set<TileBehavior>,
        speedModifier: Float = 1.0f,
        frictionModifier: Float = 1.0f,
        damage: Int = 0,
        damageInterval: Int = 60,
    ): ExtensibleTileAttributeDefinition {
        return ExtensibleTileAttributeDefinition(
            id = id,
            displayName = displayName,
            behaviors = behaviors,
            speedModifier = speedModifier,
            frictionModifier = frictionModifier,
            direction = TileDirection.NONE,
            conveyorSpeed = 0,
            damage = damage,
            damageInterval = damageInterval,
            bounceStrength = 0,
            requiredAbility = null,
            teleportDestX = 0,
            teleportDestY = 0,
            teleportDestFloor = null,
            elevationChange = 0,
            onEnterStatements = emptyList(),
            onExitStatements = emptyList(),
            onStandingStatements = emptyList(),
            onDestroyStatements = emptyList(),
        )
    }
}
