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
 * Floor 6 - Haunted Depths
 *
 * Eerie passages where restless spirits dwell. Death Knights start appearing.
 */
@Suppress("LongMethod") // Floor definition requires many map objects and encounter entries
fun GameBuilder.initFloor6(monsters: Monsters, items: Items, floor5: GenericZone): ZoneDelegate =
    zone {
        type(ZoneType.DUNGEON)
        name("Dungeon Level 6")
        defaultPosition(8, 7) // Original: DEFAULT_X = 8, DEFAULT_Y = 7

        map("main") {
            tileset("tiles/dungeon.png")
            size(32, 32)
        }

        objects {
            genericObject("f6_chest1", PredefinedObjectTypes.CHEST) {
                position(25, 8)
                flag(8)
                property("item_0", items.elixir.id)
                property("quantity_0", "2")
                onInteract { showMessage("Found 2 Elixirs!") }
            }

            genericObject("f6_save", PredefinedObjectTypes.SAVE_POINT) {
                position(8, 8)
                property("healsParty", "true")
            }

            genericObject("f6_sconce1", PredefinedObjectTypes.SCONCE) {
                position(16, 16)
                initialState(true)
                property("lightRadius", "3")
                onStateChange { refillTorch(100) }
            }
        }

        exits { stairsUp(from = "main" at (5 x 5), to = floor5 at (28 x 28)) }

        encounters {
            initialChance(18)
            incrementPerStep(6)

            // Level-gated encounters: threshold at level 43
            // Original: encounters_low (< 43) / encounters_high (>= 43)
            levelThreshold(43)

            // Original: config_random_encounter(4, 1, 1, true) - safeSteps = 4
            lowLevel {
                safeSteps(4)
                // ODDS_10P: 1 C-Tier Will-o'-Wisp (Lv39)
                entry(weight = 10) { +monsters.willOWispC }
                // ODDS_20P: 2 Gelatinous Cube C (Lv41)
                entry(weight = 20) {
                    +monsters.gelatinousCubeC
                    +monsters.gelatinousCubeC
                }
                // ODDS_35P: 1 B-Tier Owlbear (Lv42)
                entry(weight = 35) { +monsters.owlbear }
                // ODDS_35P: 2 Bugbear C (Lv41)
                entry(weight = 35) {
                    +monsters.bugbearC
                    +monsters.bugbearC
                }
            }

            highLevel {
                safeSteps(4)
                // ODDS_25P: 1 C-Tier Gelatinous Cube (Lv45)
                entry(weight = 25) { +monsters.gelatinousCubeC }
                // ODDS_30P: 2 B-Tier Owlbear (Lv43)
                entry(weight = 30) {
                    +monsters.owlbear
                    +monsters.owlbear
                }
                // ODDS_30P: 1 C-Tier Displacer Beast (Lv45)
                entry(weight = 30) { +monsters.displacerBeastC }
                // ODDS_15P: Goblin C + Kobold B + Goblin C (Lv41-43)
                entry(weight = 15) {
                    +monsters.goblin
                    +monsters.koboldB
                    +monsters.goblin
                }
            }
        }
    }
