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
 * Floor 3 - Beast Dens
 *
 * Home to larger creatures. Bugbears and owlbears make their appearance.
 */
@Suppress("LongMethod") // Floor definition requires many map objects and encounter entries
fun GameBuilder.initFloor3(monsters: Monsters, items: Items, floor2: GenericZone): ZoneDelegate =
    zone {
        type(ZoneType.DUNGEON)
        name("Dungeon Level 3")
        defaultPosition(26, 17) // Original: DEFAULT_X = 26, DEFAULT_Y = 17

        map("main") {
            tileset("tiles/dungeon.png")
            size(32, 32)
        }

        objects {
            genericObject("f3_chest1", PredefinedObjectTypes.CHEST) {
                position(8, 15)
                flag(16)
                property("item_0", items.ether.id)
                property("quantity_0", "2")
                onInteract { showMessage("Found 2 Ethers!") }
            }

            genericObject("f3_chest2", PredefinedObjectTypes.CHEST) {
                position(24, 22)
                flag(17)
                property("item_0", items.defUp.id)
                property("quantity_0", "1")
                property("locked", "true")
                property("keyItemId", "magic_key")
                property("consumesKey", "true")
                onInteract { showMessage("Found DEF Up!") }
            }

            genericObject("f3_sconce1", PredefinedObjectTypes.SCONCE) {
                position(16, 8)
                initialState(true)
                property("lightRadius", "3")
                onStateChange { refillTorch(100) }
            }
        }

        exits { stairsUp(from = "main" at (5 x 5), to = floor2 at (28 x 28)) }

        encounters {
            initialChance(10)
            incrementPerStep(4)

            // Level-gated encounters: threshold at level 21
            // Original: encounters_low (< 21) / encounters_high (>= 21)
            levelThreshold(21)

            // Original: config_random_encounter(4, 1, 1, true) - safeSteps = 4
            lowLevel {
                safeSteps(4)
                // ODDS_10P: 1 B-Tier Bugbear (Lv18)
                entry(weight = 10) { +monsters.bugbear }
                // ODDS_20P: 1 B-Tier Zombie (Lv18)
                entry(weight = 20) { +monsters.zombieB }
                // ODDS_35P: 3 B-Tier Kobolds (Lv17-19)
                entry(weight = 35) {
                    +monsters.koboldB
                    +monsters.koboldB
                    +monsters.koboldB
                }
                // ODDS_35P: Zombie C (Lv19) + Bugbear C (Lv18)
                entry(weight = 35) {
                    +monsters.zombie
                    +monsters.bugbearC
                }
            }

            highLevel {
                safeSteps(4)
                // ODDS_20P: 2 A-Tier Zombies (Lv21)
                entry(weight = 20) {
                    +monsters.zombieA
                    +monsters.zombieA
                }
                // ODDS_25P: Bugbear B (Lv20) + 2 Goblins C (Lv21-23)
                entry(weight = 25) {
                    +monsters.bugbear
                    +monsters.goblin
                    +monsters.goblin
                }
                // ODDS_25P: 2 Zombies C (Lv22)
                entry(weight = 25) {
                    +monsters.zombie
                    +monsters.zombie
                }
                // ODDS_30P: Kobold C + Goblin B + Kobold C (Lv19-21)
                entry(weight = 30) {
                    +monsters.kobold
                    +monsters.goblinB
                    +monsters.kobold
                }
            }
        }
    }
