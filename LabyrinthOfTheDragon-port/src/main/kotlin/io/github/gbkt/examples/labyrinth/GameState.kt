/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth

import io.github.gbkt.core.ir.AssignableExpr

/**
 * Game state holder for player position and dungeon progress.
 *
 * This class holds references to the DSL variables for use in scene logic. Variables are passed
 * from the main game definition.
 */
data class GameState(
    /** Player X position (tile coordinates) */
    val playerX: AssignableExpr,
    /** Player Y position (tile coordinates) */
    val playerY: AssignableExpr,
    /** Current floor index (0-7) */
    val currentFloor: AssignableExpr,
    /** Movement cooldown counter */
    val moveCooldown: AssignableExpr,
    /** Step counter for random encounters */
    val stepCount: AssignableExpr,
    /** Torch fuel remaining (0-255, decreases each step) */
    val torchFuel: AssignableExpr,
    /** Number of magic keys held (0-99) */
    val keyCount: AssignableExpr,
    /** Selected character class (0=Druid, 1=Fighter, 2=Monk, 3=Sorcerer) */
    val selectedClass: AssignableExpr,
    /** Title menu cursor position (0=New Game, 1=Continue) */
    val titleMenuCursor: AssignableExpr,
    /** Hero selection cursor position (0-3) */
    val heroSelectCursor: AssignableExpr,
    /** Pause menu cursor position (0=Resume, 1=Save, 2=Load, 3=Quit) */
    val pauseMenuCursor: AssignableExpr,
    /** Save/load slot selection cursor (0-2) */
    val saveSlotCursor: AssignableExpr,
    /** Pause menu sub-state (0=main, 1=save slots, 2=load slots, 3=confirm) */
    val pauseMenuState: AssignableExpr,
)

// Note: CharacterClass constants are defined in SaveSystem.kt
