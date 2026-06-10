/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.dsl

import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.core.ir.TacticalGridConfig
import io.github.gbkt.core.ir.TerrainTypeDef
import io.github.gbkt.core.ir.defaultTerrainTypes

// =============================================================================
// TACTICAL GRID BUILDER
// DSL builder for tactical grid combat (SRPG variant).
// Produces a TacticalGridConfig used by CombatEngineSystem with
// combatType = CombatType.TACTICAL_GRID.
//
// Usage:
//   tacticalCombat("battle") {
//       gridSize(10, 10)
//       enableTerrain()
//       enableElevation(bonusPerLevel = 15)
//       enableFacing(flanking = 25, backstab = 50)
//       movementRange(4)
//       terrain("marsh") {
//           name("Marsh")
//           movementCost(2)
//           damagePerTurn(5)
//       }
//   }
// =============================================================================

/**
 * Builder for [TerrainTypeDef] — configures a single terrain tile type.
 *
 * @param id Unique identifier for this terrain type.
 */
class TerrainTypeBuilder(private val id: String) {
    private var name: String = id
    private var movementCost: Int = 1
    private var damagePerTurn: Int = 0
    private var defenseBonus: Int = 0

    /** Sets the display name for this terrain type. */
    fun name(n: String) {
        name = n
    }

    /**
     * Sets the movement cost for this terrain type.
     *
     * 1 = normal, 2 = slow/difficult, 0 = blocked (cannot enter), -1 = impassable (blocks LoS).
     */
    fun movementCost(cost: Int) {
        movementCost = cost
    }

    /** Sets the damage-per-turn dealt to any unit occupying this terrain at turn end. */
    fun damagePerTurn(dmg: Int) {
        damagePerTurn = dmg
    }

    /** Sets the percentage cover/defense bonus for units on this terrain. */
    fun defenseBonus(bonus: Int) {
        defenseBonus = bonus
    }

    /** Builds and returns the [TerrainTypeDef]. */
    internal fun build(): TerrainTypeDef =
        TerrainTypeDef(
            id = id,
            name = name,
            movementCost = movementCost,
            damagePerTurn = damagePerTurn,
            defenseBonus = defenseBonus,
        )
}

/**
 * Builder for tactical grid combat configuration.
 *
 * Wraps [TacticalGridConfig] construction and pre-sets combatType = [CombatType.TACTICAL_GRID] on
 * the enclosing [CombatEngineBuilder]. Used by the [io.github.gbkt.core.dsl.GameBuilder] extension
 * function `tacticalCombat(id, block)`.
 */
class TacticalGridBuilder {
    private var gridWidth: Int = 8
    private var gridHeight: Int = 8
    private var enableTerrain: Boolean = true
    private var enableElevation: Boolean = false
    private var enableFacing: Boolean = false
    private var flankingBonus: Int = 25
    private var backstabBonus: Int = 50
    private var elevationDamageBonus: Int = 10
    private var baseMovementRange: Int = 3
    private val terrainTypes: MutableList<TerrainTypeDef> = defaultTerrainTypes().toMutableList()
    private var customTerrainReplaced: Boolean = false

    /** Sets the grid dimensions (columns x rows). */
    fun gridSize(width: Int, height: Int) {
        gridWidth = width
        gridHeight = height
    }

    /**
     * Enables terrain movement cost and defense effects.
     *
     * Terrain is enabled by default. Call [disableTerrain] to opt out.
     */
    fun enableTerrain() {
        enableTerrain = true
    }

    /** Disables terrain effects — all tiles treated as PLAIN (movementCost=1, no cover). */
    fun disableTerrain() {
        enableTerrain = false
    }

    /**
     * Enables elevation-based damage bonus.
     *
     * @param bonusPerLevel Percentage damage bonus per height advantage tile (+10% default).
     */
    fun enableElevation(bonusPerLevel: Int = 10) {
        enableElevation = true
        elevationDamageBonus = bonusPerLevel
    }

    /**
     * Enables facing-based attack bonuses.
     *
     * @param flanking Percentage damage bonus when attacking from the side (+25% default).
     * @param backstab Percentage damage bonus when attacking from behind (+50% default).
     */
    fun enableFacing(flanking: Int = 25, backstab: Int = 50) {
        enableFacing = true
        flankingBonus = flanking
        backstabBonus = backstab
    }

    /**
     * Adds a custom terrain type definition.
     *
     * First call clears the default terrain types (PLAIN, FOREST, WALL) and starts with an empty
     * list. Subsequent calls append to the custom list.
     *
     * @param id Unique terrain type identifier.
     * @param block Configuration block for [TerrainTypeBuilder].
     */
    fun terrain(id: String, block: TerrainTypeBuilder.() -> Unit) {
        if (!customTerrainReplaced) {
            terrainTypes.clear()
            customTerrainReplaced = true
        }
        val builder = TerrainTypeBuilder(id)
        builder.block()
        terrainTypes.add(builder.build())
    }

    /**
     * Sets the default movement range for a unit with no stat modifiers.
     *
     * @param base Movement range in tiles (default 3).
     */
    fun movementRange(base: Int) {
        baseMovementRange = base
    }

    /** Builds and returns the [TacticalGridConfig]. */
    internal fun build(): TacticalGridConfig =
        TacticalGridConfig(
            gridWidth = gridWidth,
            gridHeight = gridHeight,
            enableTerrain = enableTerrain,
            enableElevation = enableElevation,
            enableFacing = enableFacing,
            flankingBonus = flankingBonus,
            backstabBonus = backstabBonus,
            terrainTypes = terrainTypes.toList(),
            elevationDamageBonus = elevationDamageBonus,
            baseMovementRange = baseMovementRange,
        )
}
