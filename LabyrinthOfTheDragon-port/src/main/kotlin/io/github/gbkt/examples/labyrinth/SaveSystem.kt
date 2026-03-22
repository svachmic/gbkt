/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth

import io.github.gbkt.core.dsl.GameBuilder

/**
 * Labyrinth of the Dragon — SRAM save system configuration.
 *
 * Configures the 3-slot SRAM save system matching the original C implementation. The V2 save system
 * automatically captures all non-transient game state variables declared in the [GameBuilder] scope
 * — no per-field registration is needed.
 *
 * ## Original C Reference
 * - `save.c` / `save.h` — SRAM layout with 3 save slots
 * - Save slots are selected on the title screen before a new game or load
 *
 * ## Save Compatibility
 *
 * The V2 port does NOT attempt to maintain binary compatibility with the Original SRAM layout (per
 * CONTEXT.md). The V2 save format is a clean redesign that captures all V2 game state variables.
 *
 * ## What Gets Saved
 *
 * The V2 save system automatically includes all non-transient `u8Var` / `i8Var` variables
 * registered in the game scope. For Labyrinth of the Dragon, this includes:
 *
 * | Variable           | Type | Description                                 |
 * |--------------------|------|---------------------------------------------|
 * | `currentFloor`     | u8   | Active floor index (1–8)                    |
 * | `playerX`          | u8   | Player tile column position                 |
 * | `playerY`          | u8   | Player tile row position                    |
 * | `heroDirection`    | u8   | Facing direction (0=HERE, 1=DOWN, 2=UP …)   |
 * | `torchLevel`       | u8   | Current torch fuel (0–255)                  |
 * | `hasTorch`         | u8   | Whether the player has a torch (0/1)        |
 * | `magicKeys`        | u8   | Number of magic keys held (0–9)             |
 * | `gotMagicKey`      | u8   | Has ever collected a magic key (0/1)        |
 * | `stepCount`        | u8   | Running step counter for encounters         |
 * | `safeSteps`        | u8   | Steps remaining before next encounter check |
 * | `selectedClass`    | u8   | Selected character class (0–3)              |
 * | `selectedSaveSlot` | u8   | Selected save slot index (0–2)              |
 *
 * UI state variables (`mapMenuState`, `mapMenuCursor`, `battleMenuCursor`, `battleTargetIndex`,
 * `battleAnimTimer`) are transient and excluded from saves.
 *
 * ## Save Slots
 *
 * ```
 * Slot 0 — Save file 1 (displayed as "FILE 1" on title screen)
 * Slot 1 — Save file 2 (displayed as "FILE 2")
 * Slot 2 — Save file 3 (displayed as "FILE 3")
 * ```
 *
 * Slot selection is stored in [io.github.gbkt.examples.labyrinth.GameState.selectedSaveSlot].
 */
object SaveSystem {

    /**
     * Unique save data identifier for SRAM addressing.
     *
     * Used by the GBDK backend to generate `save_labyrinth_save()` and `load_labyrinth_save()` C
     * functions.
     */
    const val SAVE_ID = "labyrinth_save"

    /**
     * Registers the save system into the [GameBuilder] scope.
     *
     * Configures [GameConfig.SAVE_SLOTS] (3) independent SRAM save slots with checksum verification
     * to detect corrupted saves.
     *
     * Called inside the `game { }` DSL block AFTER all game state variables have been declared (so
     * the save system captures all registered variables).
     *
     * ## Placement in game { } Block
     *
     * ```kotlin
     * game("LabyrinthDragon") {
     *     val state = GameState.register(this)     // declare variables first
     *     SaveSystem.register(this)                // then configure save system
     *     // ... scenes
     * }
     * ```
     *
     * @source save.c — `SAVE_SLOTS = 3` constant, SRAM layout
     */
    fun register(builder: GameBuilder) {
        builder.saveData(SAVE_ID) {
            slots(GameConfig.SAVE_SLOTS) // 3 independent save slots (FILE 1, FILE 2, FILE 3)
            checksum() // 8-bit rolling checksum — detects SRAM corruption
            version(1) // V2 port save format version 1
        }
    }
}
