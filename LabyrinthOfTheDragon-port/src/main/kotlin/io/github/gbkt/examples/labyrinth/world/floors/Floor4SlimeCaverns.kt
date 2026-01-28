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
 * Floor 4 - Slime Caverns
 *
 * Wet, slimy tunnels. Gelatinous Cubes lurk in the darkness.
 */
@Suppress("LongMethod") // Floor definition requires many map objects and encounter entries
fun GameBuilder.initFloor4(monsters: Monsters, items: Items, floor3: GenericZone): ZoneDelegate =
    zone {
        type(ZoneType.DUNGEON)
        name("Dungeon Level 4")
        defaultPosition(24, 30) // Original: DEFAULT_X = 24, DEFAULT_Y = 30

        map("main") {
            tileset("tiles/dungeon.png")
            size(32, 32)
        }

        objects {
            genericObject("f4_chest1", PredefinedObjectTypes.CHEST) {
                position(18, 10)
                flag(24)
                property("item_0", items.remedy.id)
                property("quantity_0", "2")
                onInteract { showMessage("Found 2 Remedies!") }
            }

            genericObject("f4_save", PredefinedObjectTypes.SAVE_POINT) {
                position(10, 10)
                property("healsParty", "true")
            }

            genericObject("f4_sconce1", PredefinedObjectTypes.SCONCE) {
                position(20, 20)
                initialState(true)
                property("lightRadius", "3")
                onStateChange { refillTorch(100) }
            }
        }

        exits { stairsUp(from = "main" at (5 x 5), to = floor3 at (28 x 28)) }

        encounters {
            initialChance(12)
            incrementPerStep(5)

            // Level-gated encounters: threshold at level 29
            // Original: encounters_low (< 29) / encounters_high (>= 29)
            levelThreshold(29)

            // Original: config_random_encounter(4, 1, 1, true) - safeSteps = 4
            lowLevel {
                safeSteps(4)
                // ODDS_10P: 1 B-Tier Owlbear (Lv25)
                entry(weight = 10) { +monsters.owlbear }
                // ODDS_20P: 1 C-Tier Bugbear (Lv24)
                entry(weight = 20) { +monsters.bugbearC }
                // ODDS_35P: 2 Goblins C + B (Lv25)
                entry(weight = 35) {
                    +monsters.goblin
                    +monsters.goblinB
                }
                // ODDS_35P: 2 Zombies C (Lv18-19)
                entry(weight = 35) {
                    +monsters.zombie
                    +monsters.zombie
                }
            }

            highLevel {
                safeSteps(4)
                // ODDS_20P: 2 Owlbear C (Lv27, Lv29)
                entry(weight = 20) {
                    +monsters.owlbearC
                    +monsters.owlbearC
                }
                // ODDS_25P: 1 B-Tier Bugbear (Lv31)
                entry(weight = 25) { +monsters.bugbear }
                // ODDS_25P: 2 Zombie C (Lv29)
                entry(weight = 25) {
                    +monsters.zombie
                    +monsters.zombie
                }
                // ODDS_30P: 3 Goblins C + B + C (Lv28-29)
                entry(weight = 30) {
                    +monsters.goblin
                    +monsters.goblinB
                    +monsters.goblin
                }
            }
        }
    }
