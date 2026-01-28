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
import io.github.gbkt.core.world.GenericZone
import io.github.gbkt.core.world.PredefinedObjectTypes
import io.github.gbkt.core.world.ZoneDelegate
import io.github.gbkt.core.world.ZoneType
import io.github.gbkt.core.world.at
import io.github.gbkt.core.world.x
import io.github.gbkt.core.world.zone
import io.github.gbkt.examples.labyrinth.rpg.Items
import io.github.gbkt.examples.labyrinth.rpg.Monsters

/**
 * Floor 2 - Goblin Warrens
 *
 * A network of goblin tunnels. Encounters shift to goblins with kobold support.
 */
@Suppress("LongMethod") // Floor definition requires many map objects and encounter entries
fun GameBuilder.initFloor2(monsters: Monsters, items: Items, floor1: GenericZone): ZoneDelegate =
    zone {
        type(ZoneType.DUNGEON)
        name("Dungeon Level 2")
        defaultPosition(10, 13) // Original: DEFAULT_X = 10, DEFAULT_Y = 13

        map("main") {
            tileset("tiles/dungeon.png")
            size(32, 32)
        }

        objects {
            genericObject("f2_chest1", PredefinedObjectTypes.CHEST) {
                position(20, 8)
                flag(8) // Second page offset for floor 2
                property("item_0", items.potion.id)
                property("quantity_0", "3")
                onInteract { showMessage("Found 3 Potions!") }
            }

            genericObject("f2_chest2", PredefinedObjectTypes.CHEST) {
                position(12, 25)
                flag(9)
                property("item_0", items.atkUp.id)
                property("quantity_0", "1")
                onInteract { showMessage("Found ATK Up!") }
            }

            genericObject("f2_sconce1", PredefinedObjectTypes.SCONCE) {
                position(16, 16)
                initialState(true) // startsLit
                property("lightRadius", "3")
                onStateChange { refillTorch(100) }
            }

            genericObject("f2_save", PredefinedObjectTypes.SAVE_POINT) {
                position(6, 6)
                property("healsParty", "true")
            }
        }

        exits {
            // Stairs up to floor 1
            stairsUp(from = "main" at (5 x 5), to = floor1 at (28 x 28))
        }

        encounters {
            initialChance(8)
            incrementPerStep(4)

            // Level-gated encounters: threshold at level 16
            // Original: random_enc_lv12 (< 16) / random_enc_lv16 (>= 16)
            levelThreshold(16)

            // Original: config_random_encounter(6, 1, 1, true) - safeSteps = 6
            lowLevel {
                safeSteps(6)
                // ODDS_25P: 2 B-Tier Goblins (Lv10, Lv11)
                entry(weight = 25) {
                    +monsters.goblinB
                    +monsters.goblinB
                }
                // ODDS_30P: 1 C-Tier Zombie (Lv12)
                entry(weight = 30) { +monsters.zombie }
                // ODDS_25P: 3 Kobolds (C, B, C tier at Lv9)
                entry(weight = 25) {
                    +monsters.kobold
                    +monsters.koboldB
                    +monsters.kobold
                }
                // ODDS_20P: 2 Kobolds C + 1 Goblin B (Lv11, Lv12, Lv11)
                entry(weight = 20) {
                    +monsters.kobold
                    +monsters.goblinB
                    +monsters.kobold
                }
            }

            highLevel {
                safeSteps(6)
                // ODDS_25P: 1 A-Tier Goblin (Lv14)
                entry(weight = 25) { +monsters.goblinA }
                // ODDS_25P: 3 Kobolds (C, A, C tier at Lv14-15)
                entry(weight = 25) {
                    +monsters.kobold
                    +monsters.koboldA
                    +monsters.kobold
                }
                // ODDS_25P: 2 B-Tier Zombies (Lv15)
                entry(weight = 25) {
                    +monsters.zombieB
                    +monsters.zombieB
                }
                // ODDS_25P: 1 A-Tier Kobold (Lv15)
                entry(weight = 25) { +monsters.koboldA }
            }
        }
    }
