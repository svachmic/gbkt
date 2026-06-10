/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.rpg.domain

// =============================================================================
// RPG SAVE INTEGRATION DOMAIN TYPES
// =============================================================================
//
// RPG save configuration for character stats, inventory, party state, and flags
// across save slots. Supports auto-save triggers, NG+ carry-over config, and
// save preview fields for the load menu.
//
// GAP-11 save checksum:
//   - save_rpg_state(slot) serializes all fields, then calls compute_save_checksum(slot)
//     which XOR-folds all save bytes into a UINT16 with rotation
//   - load_rpg_state(slot) calls validate_save_checksum(slot) first; on mismatch:
//     sets _save_corrupt = 1 and returns early (prevents loading corrupted data)
//   - _save_corrupt is a global UINT8 that game scenes can check to show a warning
// =============================================================================

/** Controls when saving is allowed. */
enum class SaveMode {
    /** Player can save anywhere (from pause menu). */
    SAVE_ANYWHERE,

    /** Player can only save at designated save point tiles/NPCs. */
    SAVE_POINT,
}

/** Events that trigger an automatic save. */
enum class AutoSaveTrigger {
    /** Auto-save when scene transitions occur. */
    SCENE_TRANSITION,

    /** Auto-save after winning a battle. */
    AFTER_BATTLE,

    /** Auto-save when resting at an inn. */
    REST_AT_INN,
}

/**
 * RPG save system configuration.
 *
 * Extends the core SaveSystem with RPG-specific state: character stats, inventory, party
 * configuration, and world flags across multiple save slots.
 *
 * @param slotCount Number of save slots (default 3).
 * @param saveMode When saving is allowed.
 * @param autoSaveEnabled Whether auto-save is enabled.
 * @param autoSaveTriggers Events that trigger auto-save (requires autoSaveEnabled = true).
 * @param savePreviewFields Fields shown in the load menu preview (e.g. "name", "level", "time").
 * @param enableNewGamePlus Whether NG+ mode is supported (carry items/stats into a new run).
 * @param ngPlusCarryOver Field names to preserve in NG+ (e.g. "inventory", "abilities").
 * @param excludeFromSave Field names to exclude from save data (volatile/transient state).
 */
data class RpgSaveConfig(
    val slotCount: Int = 3,
    val saveMode: SaveMode = SaveMode.SAVE_POINT,
    val autoSaveEnabled: Boolean = false,
    val autoSaveTriggers: Set<AutoSaveTrigger> = emptySet(),
    val savePreviewFields: List<String> = listOf("name", "level", "time"),
    val enableNewGamePlus: Boolean = false,
    val ngPlusCarryOver: Set<String> = emptySet(),
    val excludeFromSave: Set<String> = emptySet(),
)
