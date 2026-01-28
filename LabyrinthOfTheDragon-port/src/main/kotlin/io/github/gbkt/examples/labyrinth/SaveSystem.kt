/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth

import io.github.gbkt.core.Checksum
import io.github.gbkt.core.SaveDataHandle
import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.builder.saveData

// =============================================================================
// LABYRINTH OF THE DRAGON - SAVE SYSTEM
// =============================================================================
// Provides 3 save slots for persistent game progress.
//
// Save Data Structure (32 bytes total):
// - Character class selection (0-3: Druid, Fighter, Monk, Sorcerer)
// - Player stats (level, HP, SP, experience)
// - Current floor and position
// - Inventory contents (8 item slots)
// - Torch fuel remaining
// - Key count
// - Chest/door flags (which have been opened)
// - Story progression flags

/**
 * Create the save system for Labyrinth of the Dragon.
 *
 * @return The configured save data handle
 */
fun GameBuilder.createSaveData(): SaveDataHandle =
    saveData("labyrinth") {
        // =====================================================================
        // CHARACTER DATA (6 bytes)
        // =====================================================================
        u8Field() // charClass: 0=Druid, 1=Fighter, 2=Monk, 3=Sorcerer
        u8Field(default = 1) // level: 1-99
        u8Field(default = 100) // hp: 0-255
        u8Field(default = 50) // sp: 0-255
        u16Field() // exp: 0-65535

        // =====================================================================
        // DUNGEON PROGRESS (3 bytes)
        // =====================================================================
        u8Field() // currentFloor: 0-7
        u8Field(default = 5) // posX: 0-31
        u8Field(default = 5) // posY: 0-31

        // =====================================================================
        // RESOURCES (4 bytes)
        // =====================================================================
        u8Field(default = 100) // torchFuel: 0-255
        u8Field() // keyCount: 0-99
        u16Field() // gold: 0-65535

        // =====================================================================
        // INVENTORY (16 bytes: 8 item slots)
        // =====================================================================
        arrayField(8) // itemIds: item IDs (255 = empty)
        arrayField(8) // itemQtys: quantities per slot

        // =====================================================================
        // FLAGS (3 bytes)
        // =====================================================================
        flagsField() // chestFlags1: floors 1-4
        flagsField() // chestFlags2: floors 5-8
        flagsField() // storyFlags: progression

        // =====================================================================
        // CONFIGURATION
        // =====================================================================
        config {
            slots = 3 // 3 save slots
            checksum = Checksum.CRC8 // Good error detection
            magic = "LODR" // "Labyrinth Of the DRagon"
            version = 1
        }
    }

/** Constants for save data indices. */
object SaveSlot {
    const val SLOT_1 = 0
    const val SLOT_2 = 1
    const val SLOT_3 = 2
}

/** Character class indices. */
object CharacterClass {
    const val DRUID = 0
    const val FIGHTER = 1
    const val MONK = 2
    const val SORCERER = 3
}

/** Story flag bit positions within storyFlags byte. */
object StoryFlag {
    const val HAS_TORCH = 0
    const val GOT_MAGIC_KEY = 1
    const val MET_ELDER = 2
    const val DEFEATED_DRAGON = 3
    const val FOUND_MAP = 4
    const val OPENED_SECRET = 5
    // Bits 6-7 reserved
}
