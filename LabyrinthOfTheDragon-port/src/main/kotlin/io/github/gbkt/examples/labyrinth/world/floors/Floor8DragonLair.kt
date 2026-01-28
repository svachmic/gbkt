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
import io.github.gbkt.core.world.GlobalFlags
import io.github.gbkt.core.world.PredefinedObjectTypes
import io.github.gbkt.core.world.ZoneDelegate
import io.github.gbkt.core.world.ZoneType
import io.github.gbkt.core.world.at
import io.github.gbkt.core.world.set
import io.github.gbkt.core.world.x
import io.github.gbkt.core.world.zone
import io.github.gbkt.examples.labyrinth.rpg.Items
import io.github.gbkt.examples.labyrinth.rpg.Monsters

/**
 * Floor 8 - Dragon's Lair
 *
 * The final floor. The Dragon awaits in its lair along with elite guardians. Features the scripted
 * boss encounter at the dragon's throne.
 *
 * ## Boss Encounter Flow
 * 1. Player approaches the dragon throne at position (16, 16)
 * 2. Interacting with the throne sets the "dragonBattleTriggered" flag
 * 3. GameplayScene detects this flag and transitions to battle with the dragon
 * 4. After victory, "defeatedDragon" flag is set, unlocking the dragon hoard chest
 */
@Suppress("LongMethod", "UnusedParameter")
fun GameBuilder.initFloor8(
    @Suppress("UNUSED_PARAMETER") monsters: Monsters,
    items: Items,
    floor7: GenericZone,
    gameFlags: GlobalFlags,
): ZoneDelegate = zone {
    type(ZoneType.DUNGEON)
    name("Dragon's Lair")
    defaultPosition(8, 29) // Original: DEFAULT_X = 8, DEFAULT_Y = 29

    map("main") {
        tileset("tiles/dungeon.png")
        size(32, 32)
    }

    objects {
        // Final save point before dragon
        genericObject("f8_save", PredefinedObjectTypes.SAVE_POINT) {
            position(5, 10)
            property("healsParty", "true")
        }

        // Dragon's throne - scripted boss encounter trigger
        // When interacted with, sets the dragonBattleTriggered flag.
        // GameplayScene checks this flag and initiates the boss battle.
        genericObject("dragon_throne", PredefinedObjectTypes.NPC) {
            position(16, 16)
            property("name", "Dragon")
            property("facing", "DOWN")
            onInteract {
                showMessage("THE DRAGON AWAKENS!\nPrepare for battle!")
                gameFlags.getFlag("dragonBattleTriggered")?.set()
            }
        }

        // Treasure hoard chest
        genericObject("f8_chest1", PredefinedObjectTypes.CHEST) {
            position(28, 28)
            flag(24)
            property("item_0", items.elixir.id)
            property("quantity_0", "3")
            onInteract { showMessage("Found 3 Elixirs!") }
        }

        // Dragon's treasure - only accessible after defeating dragon
        genericObject("f8_dragon_hoard", PredefinedObjectTypes.CHEST) {
            position(16, 28)
            flag(25)
            property("item_0", items.elixir.id)
            property("quantity_0", "5")
            property("locked", "true")
            property("keyItemId", "dragon_key")
            property("consumesKey", "false")
            onInteract { showMessage("The Dragon's hoard!\n5 Elixirs found!") }
        }

        genericObject("f8_sconce1", PredefinedObjectTypes.SCONCE) {
            position(16, 5)
            initialState(true)
            property("lightRadius", "4")
            onStateChange { refillTorch(100) }
        }
    }

    exits { stairsUp(from = "main" at (5 x 5), to = floor7 at (28 x 28)) }

    // NOTE: Floor 8 has NO random encounters in the original game.
    // All encounters are scripted mini-boss encounters at specific positions.
    // The dragon encounter is triggered by interacting with the dragon_throne object.
}
