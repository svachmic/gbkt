/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.dsl.u8Var

/**
 * Runtime state variables for Labyrinth of the Dragon.
 *
 * Declares all persistent mutable game state as V2 delegate variables. These are registered into
 * the [GameBuilder] scope via [GameBuilder.registerLabyrinthState] and become global UINT8/INT8 C
 * variables in the generated output.
 *
 * Every variable here corresponds to a global variable or Player struct field in the original C
 * implementation. Cross-references are provided in each property's KDoc.
 *
 * Original source files referenced:
 * - `LabyrinthOfTheDragon/src/main.c` — `game_state`, `joypad_*` globals
 * - `LabyrinthOfTheDragon/src/map.h` — `map_x`, `map_y`, `map_state`, `hero_direction`, etc.
 * - `LabyrinthOfTheDragon/src/player.h` — `Player` struct fields (torch_gauge, magic_keys, etc.)
 * - `LabyrinthOfTheDragon/src/battle.h` — combat state trackers
 */
class GameState(private val builder: GameBuilder) {

    // -------------------------------------------------------------------------
    // Exploration / floor navigation
    // @source map.h
    // -------------------------------------------------------------------------

    /**
     * Index (1-based) of the dungeon floor the player is currently on.
     *
     * Range: 1–[GameConfig.FLOOR_COUNT]. Starts at 1 (entrance level).
     *
     * @source map.h — floor loading logic uses floor index to select the active [FloorBank]
     */
    var currentFloor by u8Var(1)

    /**
     * Horizontal (column) tile position of the player hero on the active map.
     *
     * Signed to allow scroll offset math (hero is 4 tiles from left when centered).
     *
     * @source map.h — `extern int8_t map_x` + `HERO_X_OFFSET = 4`
     */
    var playerX by u8Var(0)

    /**
     * Vertical (row) tile position of the player hero on the active map.
     *
     * @source map.h — `extern int8_t map_y` + `HERO_Y_OFFSET = 4`
     */
    var playerY by u8Var(0)

    /**
     * Direction the hero is currently facing (0=HERE, 1=DOWN, 2=UP, 3=LEFT, 4=RIGHT).
     *
     * @source map.h — `extern Direction hero_direction` (Direction enum: HERE=0, DOWN=1, UP=2,
     *   LEFT=3, RIGHT=4)
     */
    var heroDirection by u8Var(0)

    // -------------------------------------------------------------------------
    // Torch system
    // @source player.h, map.h
    // -------------------------------------------------------------------------

    /**
     * Current torch fuel level (0–[GameConfig.TORCH_MAX]).
     *
     * Decrements every [GameConfig.TORCH_GAUGE_SPEED] frames while the player is on the world map
     * with a lit torch. At 0 the game transitions to game over.
     *
     * @source player.h — `uint8_t torch_gauge` in Player struct
     */
    var torchLevel by u8Var(255)

    /**
     * Whether the player currently possesses a torch (1 = has torch, 0 = no torch).
     *
     * @source player.h — `bool has_torch` in Player struct
     */
    var hasTorch by u8Var(0)

    // -------------------------------------------------------------------------
    // Keys and inventory
    // @source player.h, map.h
    // -------------------------------------------------------------------------

    /**
     * Number of magic keys the player currently holds.
     *
     * Range: 0–[GameConfig.MAGIC_KEY_MAX]. Used to unlock magic-key-locked chests and doors.
     *
     * @source player.h — `uint8_t magic_keys` in Player struct
     */
    var magicKeys by u8Var(0)

    /**
     * Whether the player has ever collected a magic key (used for the magic key HUD icon).
     *
     * 0 = never collected, 1 = collected at least once.
     *
     * @source player.h — `bool got_magic_key` in Player struct
     */
    var gotMagicKey by u8Var(0)

    // -------------------------------------------------------------------------
    // Menu / UI state
    // @source map.h — MapMenuState, MapMenuCursor enums
    // -------------------------------------------------------------------------

    /**
     * Current state of the in-map pause menu (0=CLOSED, 1=OPEN).
     *
     * @source map.h — `typedef enum MapMenuState { MAP_MENU_CLOSED=0, MAP_MENU_OPEN=1 }`
     */
    var mapMenuState by u8Var(0)

    /**
     * Current cursor position in the map pause menu (0=SAVE, 1=QUIT).
     *
     * @source map.h — `typedef enum MapMenuCursor { MAP_MENU_CURSOR_SAVE=0, MAP_MENU_CURSOR_QUIT=1
     *   }`
     */
    var mapMenuCursor by u8Var(0)

    // -------------------------------------------------------------------------
    // Combat / battle tracking
    // @source battle.h — battle state machine
    // -------------------------------------------------------------------------

    /**
     * Index of the currently selected battle menu cursor position.
     *
     * Used by the battle menu to track which action (Attack/Ability/Item/Flee) is highlighted.
     *
     * @source battle.h — battle menu cursor state variable
     */
    var battleMenuCursor by u8Var(0)

    /**
     * Index of the enemy target currently selected in the battle targeting mode.
     *
     * @source battle.h — target selection state in the battle system
     */
    var battleTargetIndex by u8Var(0)

    /**
     * Frame counter used for the battle animation timer.
     *
     * Drives damage number display timing, monster death animation, etc.
     *
     * @source battle.h — timer for battle animations
     */
    var battleAnimTimer by u8Var(0)

    // -------------------------------------------------------------------------
    // Step / encounter tracking
    // @source map.h — encounter trigger logic
    // -------------------------------------------------------------------------

    /**
     * Running step counter used to trigger random encounters.
     *
     * Increments once per tile movement. An encounter check is triggered after [safe steps].
     *
     * @source map.h — encounter logic checks steps between battles
     */
    var stepCount by u8Var(0)

    /**
     * Number of steps remaining before the next random encounter check.
     *
     * Resets after each encounter attempt.
     *
     * @source map.h — encounter logic, safe steps tracking
     */
    var safeSteps by u8Var(10)

    // -------------------------------------------------------------------------
    // Save slot selection
    // @source save.c — save/load slot selection on title screen
    // -------------------------------------------------------------------------

    /**
     * Currently selected save slot index (0, 1, or 2).
     *
     * Range: 0–([GameConfig.SAVE_SLOTS]-1). Selected on the title screen before loading.
     *
     * @source save.c — save slot selection logic
     */
    var selectedSaveSlot by u8Var(0)

    /**
     * Currently selected character class index for new game creation.
     *
     * Range: 0–([GameConfig.CLASS_COUNT]-1). 0=Druid, 1=Fighter, 2=Monk, 3=Sorcerer.
     *
     * @source player.h — `typedef enum PlayerClass`; hero_select.c class picker
     */
    var selectedClass by u8Var(0)

    companion object {
        /**
         * Registers all Labyrinth of the Dragon runtime state variables into a [GameBuilder].
         *
         * Called inside the `game { }` DSL block so that all variables are declared in game scope
         * and become accessible to all scene/system builders that follow.
         *
         * Usage:
         * ```kotlin
         * game("LabyrinthDragon") {
         *     val state = GameState.register(this)
         *     // use state.torchLevel, state.currentFloor, etc. in scenes below
         * }
         * ```
         */
        fun register(builder: GameBuilder): GameState = GameState(builder)
    }
}
