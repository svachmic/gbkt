/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth

import io.github.gbkt.core.exploration.MovementStyle

/**
 * Game configuration constants for Labyrinth of the Dragon.
 *
 * Centralizes all gameplay constants for easy tuning and consistency across the codebase.
 */
object GameConfig {

    // =========================================================================
    // EXPLORATION CONSTANTS
    // =========================================================================

    /** Tile size in pixels (Game Boy standard 8x8) */
    const val TILE_SIZE = 8

    /** Movement style for dungeon exploration */
    val MOVEMENT_STYLE = MovementStyle.GRID

    /** Frames per tile movement (lower = faster) */
    const val MOVEMENT_SPEED = 8

    // =========================================================================
    // TORCH / LIGHTING
    // =========================================================================

    /** Maximum torch fuel value */
    const val TORCH_MAX = 255

    /** Starting torch fuel */
    const val TORCH_INITIAL = 100

    /** Torch fuel consumed per step */
    const val TORCH_DECREMENT = 1

    /** Threshold for "torch getting dim" warning */
    const val TORCH_LOW_THRESHOLD = 30

    // =========================================================================
    // KEYS
    // =========================================================================

    /** Maximum magic keys the player can hold */
    const val KEYS_MAX = 99

    /** Starting number of keys */
    const val KEYS_INITIAL = 0

    // =========================================================================
    // PLAYER START POSITION
    // =========================================================================

    /** Player starting X position (tile coordinates) */
    const val PLAYER_START_X = 5

    /** Player starting Y position (tile coordinates) */
    const val PLAYER_START_Y = 5

    /** Starting floor index (0 = Floor 1) */
    const val START_FLOOR = 0

    // =========================================================================
    // CHARACTER CLASSES
    // =========================================================================

    /** Druid class ID */
    const val CLASS_DRUID = 0

    /** Fighter class ID */
    const val CLASS_FIGHTER = 1

    /** Monk class ID */
    const val CLASS_MONK = 2

    /** Sorcerer class ID */
    const val CLASS_SORCERER = 3

    /** Total number of playable classes */
    const val CLASS_COUNT = 4

    // =========================================================================
    // DUNGEON STRUCTURE
    // =========================================================================

    /** Total number of dungeon floors */
    const val FLOOR_COUNT = 8

    /** Maximum chests per floor */
    const val CHESTS_PER_FLOOR = 8

    // =========================================================================
    // PAUSE MENU
    // =========================================================================

    /** Pause menu option: Resume game */
    const val PAUSE_RESUME = 0

    /** Pause menu option: Save game */
    const val PAUSE_SAVE = 1

    /** Pause menu option: Load game */
    const val PAUSE_LOAD = 2

    /** Pause menu option: Quit to title */
    const val PAUSE_QUIT = 3

    /** Pause menu total options */
    const val PAUSE_OPTIONS = 4

    // =========================================================================
    // PAUSE MENU STATES
    // =========================================================================

    /** Pause sub-state: Main menu */
    const val PAUSE_STATE_MAIN = 0

    /** Pause sub-state: Save slot selection */
    const val PAUSE_STATE_SAVE = 1

    /** Pause sub-state: Load slot selection */
    const val PAUSE_STATE_LOAD = 2

    /** Pause sub-state: Confirmation dialog */
    const val PAUSE_STATE_CONFIRM = 3

    // =========================================================================
    // SAVE SYSTEM
    // =========================================================================

    /** Number of save slots */
    const val SAVE_SLOTS = 3

    // =========================================================================
    // BATTLE MENU
    // =========================================================================

    /** Battle menu state: Main action selection */
    const val BATTLE_MENU_MAIN = 0

    /** Battle menu state: Target selection */
    const val BATTLE_MENU_TARGET = 1

    /** Battle menu state: Ability selection */
    const val BATTLE_MENU_ABILITY = 2

    /** Battle menu state: Item selection */
    const val BATTLE_MENU_ITEM = 3

    /** Battle menu state: Execute action */
    const val BATTLE_MENU_EXECUTE = 4

    /** Battle menu state: Enemy turn */
    const val BATTLE_MENU_ENEMY = 5

    // =========================================================================
    // MAP DIMENSIONS
    // =========================================================================

    /** Standard map width in tiles */
    const val MAP_WIDTH = 32

    /** Standard map height in tiles */
    const val MAP_HEIGHT = 32

    // =========================================================================
    // HP BAR TILES (for tile-based rendering)
    // =========================================================================

    /** Empty HP bar tile (0x50 in original) */
    const val TILE_HP_EMPTY = 0x50

    /** Full HP bar tile (0x58 in original) */
    const val TILE_HP_FULL = 0x58

    /** First partial HP bar tile (0x51-0x57 for 7 intermediate states) */
    const val TILE_HP_PARTIAL = 0x51

    // =========================================================================
    // HP BAR POSITIONS (Battle UI)
    // =========================================================================

    /** HP bar Y position (tile row) in battle UI */
    const val HP_BAR_Y = 6

    /** HP bar width in pixels (5 tiles × 8 pixels) */
    const val HP_BAR_WIDTH = 40

    /** HP bar height in pixels */
    const val HP_BAR_HEIGHT = 8

    /** Number of pip segments in HP bar */
    const val HP_BAR_PIPS = 5

    /** Monster 1 HP bar X position (tile column) */
    const val HP_BAR_X1 = 1

    /** Monster 2 HP bar X position (tile column) */
    const val HP_BAR_X2 = 7

    /** Monster 3 HP bar X position (tile column) */
    const val HP_BAR_X3 = 13

    // =========================================================================
    // STATUS EFFECT ICONS
    // =========================================================================

    /** Number of status icon slots per combatant */
    const val STATUS_ICON_SLOTS = 4

    /** Status icon tile base (0x60 in original) */
    const val TILE_STATUS_BASE = 0x60

    /** Status icon Y position for player (pixel) */
    const val STATUS_ICON_PLAYER_Y = 128

    /** Status icon Y position for monsters (pixel) */
    const val STATUS_ICON_MONSTER_Y = 32

    /** Status icon X base for player */
    const val STATUS_ICON_PLAYER_X = 8

    /** Status icon X base for monster 1 */
    const val STATUS_ICON_M1_X = 8

    /** Status icon X base for monster 2 */
    const val STATUS_ICON_M2_X = 56

    /** Status icon X base for monster 3 */
    const val STATUS_ICON_M3_X = 104

    /** Debuff palette index (red/warning) */
    const val PALETTE_DEBUFF = 7

    /** Buff palette index (blue/positive) */
    const val PALETTE_BUFF = 6

    // Status effect icon indices (matching StatusEffects.kt)
    const val STATUS_ICON_REGEN = 0
    const val STATUS_ICON_POISON = 1
    const val STATUS_ICON_BURN = 2
    const val STATUS_ICON_ATK_UP = 3
    const val STATUS_ICON_DEF_UP = 4
    const val STATUS_ICON_HASTE = 5
    const val STATUS_ICON_ATK_DOWN = 6
    const val STATUS_ICON_DEF_DOWN = 7
    const val STATUS_ICON_STUN = 8
    const val STATUS_ICON_SLEEP = 9
    const val STATUS_ICON_PARALYSIS = 10
    const val STATUS_ICON_EVASION = 11
    const val STATUS_ICON_BARKSKIN = 12
    const val STATUS_ICON_DIAMOND = 13
    const val STATUS_ICON_SLOW = 14
    const val STATUS_ICON_BLIND = 15
    const val STATUS_ICON_SCARED = 16
    const val STATUS_ICON_CONFUSION = 17
    const val STATUS_ICON_PRONE = 18
}
