/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.world.floors

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.exploration.refillTorch
import io.github.gbkt.core.showMessage
import io.github.gbkt.core.world.GlobalFlags
import io.github.gbkt.core.world.PredefinedObjectTypes
import io.github.gbkt.core.world.ZoneDelegate
import io.github.gbkt.core.world.ZoneType
import io.github.gbkt.core.world.set
import io.github.gbkt.core.world.zone
import io.github.gbkt.examples.labyrinth.rpg.Items
import io.github.gbkt.examples.labyrinth.rpg.Monsters

/**
 * Floor 1 - Dungeon Entrance
 *
 * The starting area of the dungeon. Easy encounters with mostly single kobolds. Features save
 * point, elder NPC with quest info, and basic treasure.
 */
@Suppress("LongMethod") // Floor definition requires many map objects and encounter entries
fun GameBuilder.initFloor1(monsters: Monsters, items: Items, gameFlags: GlobalFlags): ZoneDelegate =
    zone {
        type(ZoneType.DUNGEON)
        name("Dungeon Level 1")
        defaultPosition(12, 16) // Original: DEFAULT_X = 12, DEFAULT_Y = 16

        map("main") {
            tileset("tiles/dungeon.png")
            size(32, 32)
        }

        // Interactive objects on this floor
        // Original positions from floor1.c: (6,2) Torch, (21,13) Magic Key, (21,10) Potion,
        // (2,2) 2 Potions locked, (29,21) Ether
        objects {
            // Chest 1: Torch refill at (6,2) - Original: ITEM_TORCH
            genericObject("f1_chest1", PredefinedObjectTypes.CHEST) {
                position(6, 2)
                flag(0) // Flag index in chests_1_4 page
                onInteract {
                    refillTorch(255)
                    showMessage("Found a Torch!")
                }
            }

            // Chest 2: Magic Key at (21,13) - Original: ITEM_MAGIC_KEY
            genericObject("f1_chest2", PredefinedObjectTypes.CHEST) {
                position(21, 13)
                flag(1)
                property("item_0", "magic_key")
                property("quantity_0", "1")
                onInteract { showMessage("Found a Magic Key!") }
            }

            // Chest 3: Potion at (21,10)
            genericObject("f1_chest3", PredefinedObjectTypes.CHEST) {
                position(21, 10)
                flag(2)
                property("item_0", items.potion.id)
                property("quantity_0", "1")
                onInteract { showMessage("Found a Potion!") }
            }

            // Chest 4: 2 Potions (locked) at (2,2)
            genericObject("f1_chest4", PredefinedObjectTypes.CHEST) {
                position(2, 2)
                flag(3)
                property("item_0", items.potion.id)
                property("quantity_0", "2")
                property("locked", "true")
                property("keyItemId", "magic_key")
                property("consumesKey", "true")
                onInteract { showMessage("Found 2 Potions!") }
            }

            // Chest 5: Ether at (29,21)
            genericObject("f1_chest5", PredefinedObjectTypes.CHEST) {
                position(29, 21)
                flag(4)
                property("item_0", items.ether.id)
                property("quantity_0", "1")
                onInteract { showMessage("Found an Ether!") }
            }

            // Sconce for torch refill
            genericObject("f1_sconce1", PredefinedObjectTypes.SCONCE) {
                position(15, 8)
                initialState(true)
                property("lightRadius", "4")
                onStateChange {
                    refillTorch(100)
                    showMessage("Torch refilled!")
                }
            }

            // Save point near entrance
            genericObject("f1_save", PredefinedObjectTypes.SAVE_POINT) {
                position(8, 5)
                property("healsParty", "true")
            }

            // Elder NPC - gives quest info
            genericObject("elder", PredefinedObjectTypes.NPC) {
                position(20, 10)
                property("name", "Elder")
                property("facing", "DOWN")
                onInteract {
                    gameFlags.getFlag("metElder")?.set()
                    showMessage("Brave adventurer!\nSeek the Dragon below.")
                }
            }

            // Floor 1 Boss: S-tier Goblin
            // Original: { NPC_1, MAP_A, 12, 5, MONSTER_GOBLIN, S_TIER, on_npc_action }
            genericObject("f1_boss", PredefinedObjectTypes.NPC) {
                position(12, 5) // Original position from floor1.c
                property("name", "Goblin Chief")
                property("facing", "DOWN")
                property("minLevel", "10") // Only trigger at level 10+
                property("bossEncounter", "true")
                property("monsterType", "goblinS")
                onInteract {
                    // Boss encounter triggers when player ≥ Level 10
                    showMessage("The Goblin Chief blocks\nyour path!")
                }
            }
        }

        // Note: Stairs down to Floor 2 are defined in Floor2GoblinWarrens.kt
        // Each floor defines stairsUp to the previous floor, creating the bidirectional connection.

        encounters {
            initialChance(5)
            incrementPerStep(3)

            // Level-gated encounters: easier for lower levels, harder for higher
            levelThreshold(9)

            // Original encounter_lv5 (player level < 9) from floor1.c:264-284
            lowLevel {
                safeSteps(7) // Original: config_random_encounter(7, 1, 1, true)
                // ODDS_10P: 1 Kobold (Lv6, B_TIER)
                entry(weight = 10) { +monsters.koboldB }
                // ODDS_20P: 1 Goblin (Lv5, C_TIER)
                entry(weight = 20) { +monsters.goblin }
                // ODDS_35P: 2 Kobolds (Lv5 C + Lv6 C)
                entry(weight = 35) {
                    +monsters.kobold
                    +monsters.kobold
                }
                // ODDS_35P: Goblin (Lv5 C) + Kobold (Lv5 C)
                entry(weight = 35) {
                    +monsters.goblin
                    +monsters.kobold
                }
            }

            // Original encounter_lv9 (player level >= 9) from floor1.c:286-309
            highLevel {
                safeSteps(7)
                // ODDS_20P: 1 Goblin (Lv9, B_TIER)
                entry(weight = 20) { +monsters.goblinB }
                // ODDS_25P: 3 Kobolds (Lv8 C + Lv7 B + Lv8 C)
                entry(weight = 25) {
                    +monsters.kobold
                    +monsters.koboldB
                    +monsters.kobold
                }
                // ODDS_25P: Goblin (Lv9 C) + 2 Kobolds (Lv7 C)
                entry(weight = 25) {
                    +monsters.goblin
                    +monsters.kobold
                    +monsters.kobold
                }
                // ODDS_30P: Zombie (Lv9 C) + Kobold (Lv5 C)
                entry(weight = 30) {
                    +monsters.zombie
                    +monsters.kobold
                }
            }
        }
    }
