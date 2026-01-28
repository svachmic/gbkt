/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth.world

import io.github.gbkt.core.builder.GameBuilder
import io.github.gbkt.core.world.GenericZone
import io.github.gbkt.core.world.GlobalFlags
import io.github.gbkt.examples.labyrinth.rpg.Items
import io.github.gbkt.examples.labyrinth.rpg.Monsters
import io.github.gbkt.examples.labyrinth.world.floors.initFloor1
import io.github.gbkt.examples.labyrinth.world.floors.initFloor2
import io.github.gbkt.examples.labyrinth.world.floors.initFloor3
import io.github.gbkt.examples.labyrinth.world.floors.initFloor4
import io.github.gbkt.examples.labyrinth.world.floors.initFloor5
import io.github.gbkt.examples.labyrinth.world.floors.initFloor6
import io.github.gbkt.examples.labyrinth.world.floors.initFloor7
import io.github.gbkt.examples.labyrinth.world.floors.initFloor8

// =============================================================================
// LABYRINTH OF THE DRAGON - DUNGEON FLOORS
// =============================================================================
// 8 dungeon floors with progressive difficulty.
// Encounter rates and monster composition match the original game.
//
// Monster Progression:
// - Floors 1-2: Kobold, Goblin, Zombie (Tier: COMMON)
// - Floors 3-4: Bugbear, Owlbear, Gelatinous Cube (Tier: UNCOMMON)
// - Floors 5-6: Displacer Beast, Will-o'-Wisp (Tier: RARE)
// - Floors 7-8: Death Knight, Mind Flayer, Beholder (Tier: ELITE/BOSS)
// - Floor 8 Boss: Dragon

/**
 * Floor coordinator for Labyrinth of the Dragon.
 *
 * This class coordinates all dungeon floor definitions:
 * - [Floor1Entrance][initFloor1] - Dungeon entrance, easy encounters
 * - [Floor2GoblinWarrens][initFloor2] - Goblin tunnels
 * - [Floor3BeastDens][initFloor3] - Bugbears and owlbears
 * - [Floor4SlimeCaverns][initFloor4] - Gelatinous cubes
 * - [Floor5ShadowHalls][initFloor5] - Displacer beasts and wisps
 * - [Floor6HauntedDepths][initFloor6] - Haunted passages
 * - [Floor7DeathsDomain][initFloor7] - Elite enemies
 * - [Floor8DragonLair][initFloor8] - The Dragon's lair
 */
class Floors(builder: GameBuilder, monsters: Monsters, items: Items, gameFlags: GlobalFlags) {

    // Initialize floors using property delegation.
    // Each floor depends on the previous floor for stairsUp exits.
    // Floor 1 and Floor 8 receive gameFlags for NPC interactions (elder, dragon boss).
    val floor1: GenericZone by builder.initFloor1(monsters, items, gameFlags)
    val floor2: GenericZone by builder.initFloor2(monsters, items, floor1)
    val floor3: GenericZone by builder.initFloor3(monsters, items, floor2)
    val floor4: GenericZone by builder.initFloor4(monsters, items, floor3)
    val floor5: GenericZone by builder.initFloor5(monsters, items, floor4)
    val floor6: GenericZone by builder.initFloor6(monsters, items, floor5)
    val floor7: GenericZone by builder.initFloor7(monsters, items, floor6)
    val floor8: GenericZone by builder.initFloor8(monsters, items, floor7, gameFlags)

    /** Get all floors as a list for iteration */
    fun all(): List<GenericZone> =
        listOf(floor1, floor2, floor3, floor4, floor5, floor6, floor7, floor8)
}

/** Initialize all floors for the game. */
fun initFloors(
    builder: GameBuilder,
    monsters: Monsters,
    items: Items,
    gameFlags: GlobalFlags,
): Floors {
    return Floors(builder, monsters, items, gameFlags)
}
