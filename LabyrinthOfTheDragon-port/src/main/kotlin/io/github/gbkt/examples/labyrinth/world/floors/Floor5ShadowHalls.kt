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
 * Floor 5 - Shadow Halls
 *
 * Dark corridors where light barely penetrates. Displacer Beasts and Will-o'-Wisps appear.
 */
@Suppress("LongMethod") // Floor definition requires many map objects and encounter entries
fun GameBuilder.initFloor5(monsters: Monsters, items: Items, floor4: GenericZone): ZoneDelegate =
    zone {
        type(ZoneType.DUNGEON)
        name("Dungeon Level 5")
        defaultPosition(12, 30) // Original: DEFAULT_X = 12, DEFAULT_Y = 30

        map("main") {
            tileset("tiles/dungeon.png")
            size(32, 32)
        }

        objects {
            genericObject("f5_chest1", PredefinedObjectTypes.CHEST) {
                position(22, 12)
                flag(0) // First flag in chests_5_8 page
                property("item_0", items.elixir.id)
                property("quantity_0", "1")
                onInteract { showMessage("Found an Elixir!") }
            }

            genericObject("f5_chest2", PredefinedObjectTypes.CHEST) {
                position(8, 24)
                flag(1)
                property("item_0", items.regen.id)
                property("quantity_0", "1")
                property("locked", "true")
                property("keyItemId", "magic_key")
                property("consumesKey", "true")
                onInteract { showMessage("Found Regen!") }
            }

            genericObject("f5_sconce1", PredefinedObjectTypes.SCONCE) {
                position(15, 15)
                initialState(true)
                property("lightRadius", "3")
                onStateChange { refillTorch(100) }
            }
        }

        exits { stairsUp(from = "main" at (5 x 5), to = floor4 at (28 x 28)) }

        encounters {
            initialChance(15)
            incrementPerStep(5)

            // Level-gated encounters: threshold at level 37
            // Original: encounters_low (< 37) / encounters_high (>= 37)
            levelThreshold(37)

            // Original: config_random_encounter(4, 1, 1, true) - safeSteps = 4
            lowLevel {
                safeSteps(4)
                // ODDS_10P: 1 B-Tier Gelatinous Cube (Lv30)
                entry(weight = 10) { +monsters.gelatinousCube }
                // ODDS_20P: 2 Bugbear C (Lv32)
                entry(weight = 20) {
                    +monsters.bugbearC
                    +monsters.bugbearC
                }
                // ODDS_35P: 1 B-Tier Owlbear (Lv30)
                entry(weight = 35) { +monsters.owlbear }
                // ODDS_35P: 2 Zombie C (Lv30, Lv32)
                entry(weight = 35) {
                    +monsters.zombie
                    +monsters.zombie
                }
            }

            highLevel {
                safeSteps(4)
                // ODDS_25P: 2 Gelatinous Cube C (Lv34)
                entry(weight = 25) {
                    +monsters.gelatinousCubeC
                    +monsters.gelatinousCubeC
                }
                // ODDS_30P: 1 B-Tier Owlbear (Lv38)
                entry(weight = 30) { +monsters.owlbear }
                // ODDS_30P: 2 Zombie C + B (Lv36)
                entry(weight = 30) {
                    +monsters.zombie
                    +monsters.zombieB
                }
                // ODDS_15P: 3 Goblins C + B + C (Lv38-39)
                entry(weight = 15) {
                    +monsters.goblin
                    +monsters.goblinB
                    +monsters.goblin
                }
            }
        }
    }
