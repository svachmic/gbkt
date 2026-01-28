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
 * Floor 7 - Death's Domain
 *
 * The penultimate floor. Elite enemies like Death Knights, Mind Flayers, and Beholders.
 */
@Suppress("LongMethod") // Floor definition requires many map objects and encounter entries
fun GameBuilder.initFloor7(monsters: Monsters, items: Items, floor6: GenericZone): ZoneDelegate =
    zone {
        type(ZoneType.DUNGEON)
        name("Dungeon Level 7")
        defaultPosition(8, 30) // Original: DEFAULT_X = 8, DEFAULT_Y = 30

        map("main") {
            tileset("tiles/dungeon.png")
            size(32, 32)
        }

        objects {
            genericObject("f7_chest1", PredefinedObjectTypes.CHEST) {
                position(20, 20)
                flag(16)
                property("item_0", items.haste.id)
                property("quantity_0", "1")
                onInteract { showMessage("Found Haste!") }
            }

            genericObject("f7_chest2", PredefinedObjectTypes.CHEST) {
                position(10, 5)
                flag(17)
                property("item_0", items.elixir.id)
                property("quantity_0", "2")
                property("locked", "true")
                property("keyItemId", "magic_key")
                property("consumesKey", "true")
                onInteract { showMessage("Found 2 Elixirs!") }
            }

            genericObject("f7_sconce1", PredefinedObjectTypes.SCONCE) {
                position(15, 12)
                initialState(true)
                property("lightRadius", "3")
                onStateChange { refillTorch(100) }
            }
        }

        exits { stairsUp(from = "main" at (5 x 5), to = floor6 at (28 x 28)) }

        encounters {
            initialChance(20)
            incrementPerStep(6)

            // Level-gated encounters: threshold at level 50
            // Original: encounters_low (< 50) / encounters_high (>= 50)
            levelThreshold(50)

            // Original: config_random_encounter(4, 1, 1, true) - safeSteps = 4
            lowLevel {
                safeSteps(4)
                // ODDS_15P: 1 C-Tier Mind Flayer (Lv46)
                entry(weight = 15) { +monsters.mindflayerC }
                // ODDS_35P: 2 Displacer Beast C (Lv47)
                entry(weight = 35) {
                    +monsters.displacerBeastC
                    +monsters.displacerBeastC
                }
                // ODDS_15P: Bugbear B (Lv46) + Goblin C (Lv46)
                entry(weight = 15) {
                    +monsters.bugbear
                    +monsters.goblin
                }
                // ODDS_35P: 3 Zombie C (Lv44)
                entry(weight = 35) {
                    +monsters.zombie
                    +monsters.zombie
                    +monsters.zombie
                }
            }

            highLevel {
                safeSteps(4)
                // ODDS_25P: 1 C-Tier Gelatinous Cube (Lv45)
                entry(weight = 25) { +monsters.gelatinousCubeC }
                // ODDS_30P: 1 B-Tier Will-o'-Wisp (Lv49)
                entry(weight = 30) { +monsters.willOWispB }
                // ODDS_30P: 2 Gelatinous Cube C (Lv48)
                entry(weight = 30) {
                    +monsters.gelatinousCubeC
                    +monsters.gelatinousCubeC
                }
                // ODDS_15P: 1 A-Tier Death Knight (Lv47)
                entry(weight = 15) { +monsters.deathknightA }
            }
        }
    }
