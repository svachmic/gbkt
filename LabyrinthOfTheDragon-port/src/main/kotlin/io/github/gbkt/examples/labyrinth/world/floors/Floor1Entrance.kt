/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("LongMethod", "MagicNumber")

package io.github.gbkt.examples.labyrinth.world.floors

import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.dsl.ZoneRef
import io.github.gbkt.core.ir.StringLiteral
import io.github.gbkt.examples.labyrinth.world.ChestFlags1to4
import io.github.gbkt.examples.labyrinth.world.WorldFlags

// =============================================================================
// FLOOR 1 — ENTRANCE
// =============================================================================
//
// The starting dungeon floor. Low-difficulty encounters (kobolds and goblins),
// basic chests (torch, magic key, potions, ether), and an NPC boss encounter
// (S-Tier Goblin at level 10). Contains a treasure room behind a sconce puzzle.
//
// Original source: LabyrinthOfTheDragon/src/floor1.c
//   - DEFAULT_X=12, DEFAULT_Y=16
//   - MAP_A: floor_one_v2 (32x32, BANK_17)
//   - 5 chests, 11 exits (internal + floor2), 4 doors, 4 sconces + static
//   - Encounter tables: encounter_lv5 (level <9), encounter_lv9 (level >=9)
//   - NPC boss: Goblin S-Tier level 10 at tile (12,5), gated on player.level >= 10
//   - Special: hidden mini-boss kobold A-Tier level 10 at tile (29,22) once per session
// =============================================================================

/**
 * Dungeon Floor 1 — Entrance to the Labyrinth.
 *
 * Ported from `floor1.c`. Starting area with kobold/goblin random encounters, a sconce-gated
 * treasure room, and an NPC boss fight gating progress to floor 2.
 */
object Floor1Entrance {

    // Encounter group IDs — map to CombatSystem encounter configurations
    private const val LOW_KOBOLD_SOLO = "floor1_low_kobold_solo"
    private const val LOW_GOBLIN_SOLO = "floor1_low_goblin_solo"
    private const val LOW_KOBOLD_PAIR = "floor1_low_kobold_pair"
    private const val LOW_GOBLIN_KOBOLD = "floor1_low_goblin_kobold"
    private const val HIGH_GOBLIN_B = "floor1_high_goblin_b"
    private const val HIGH_KOBOLD_TRIPLE = "floor1_high_kobold_triple"
    private const val HIGH_GOBLIN_KOBOLDS = "floor1_high_goblin_kobolds"
    private const val HIGH_ZOMBIE_KOBOLD = "floor1_high_zombie_kobold"

    /**
     * Registers Floor 1 in the game builder and returns a typed [ZoneRef].
     *
     * Original: floor1.c — `const Floor floor1 = { ... }`
     *
     * @param chestFlags Chest open-state flags for floors 1-4
     * @param worldFlags World progression flags (floor1BossDefeated)
     */
    fun register(
        builder: GameBuilder,
        chestFlags: ChestFlags1to4,
        worldFlags: WorldFlags,
    ): ZoneRef =
        builder.run {
            zone("floor1") {
                name("Dungeon Level 1 - Entrance")
                // Original: floor_one_v2 (BANK_17), 32x32
                tileset("tilemaps/floors/floor1.tilemap")
                size(32, 32)

                // -------------------------------------------------------------------------
                // Encounter tables
                // Original floor1.c:264-309 — encounter_lv5 and encounter_lv9
                //
                // encounter_lv5 (player.level < 9):
                //   10% - 1x Kobold B-Tier lv6
                //   20% - 1x Goblin C-Tier lv5
                //   35% - 2x Kobold C-Tier (lv5, lv6)
                //   35% - 1x Goblin C-Tier lv5 + 1x Kobold C-Tier lv5
                //
                // encounter_lv9 (player.level >= 9):
                //   20% - 1x Goblin B-Tier lv9
                //   25% - 3x Kobold (lv8 C, lv7 B, lv8 C)
                //   25% - 1x Goblin C-Tier lv9 + 2x Kobold C-Tier lv7
                //   30% - 1x Zombie C-Tier lv9 + 1x Kobold C-Tier lv5
                // -------------------------------------------------------------------------
                encounters {
                    safeSteps(7) // Original: config_random_encounter(7, ...)

                    // Low-level encounters (player < level 9)
                    entry(LOW_KOBOLD_SOLO, weight = 10) { maxLevel(9) }
                    entry(LOW_GOBLIN_SOLO, weight = 20) { maxLevel(9) }
                    entry(LOW_KOBOLD_PAIR, weight = 35) { maxLevel(9) }
                    entry(LOW_GOBLIN_KOBOLD, weight = 35) { maxLevel(9) }

                    // High-level encounters (player >= level 9)
                    entry(HIGH_GOBLIN_B, weight = 20) { minLevel(9) }
                    entry(HIGH_KOBOLD_TRIPLE, weight = 25) { minLevel(9) }
                    entry(HIGH_GOBLIN_KOBOLDS, weight = 25) { minLevel(9) }
                    entry(HIGH_ZOMBIE_KOBOLD, weight = 30) { minLevel(9) }
                }

                // -------------------------------------------------------------------------
                // Chests
                // Original floor1.c:30-75
                //   CHEST_1 (6,2) — torch (chest_add_torch callback)
                //   CHEST_2 (21,13) — magic key (chest_add_magic_key callback)
                //   CHEST_3 (21,10) — 1x Potion
                //   CHEST_4 (2,2) — 2x Potion (locked door, requires sconce puzzle)
                //   CHEST_5 (29,21) — 1x Ether
                // -------------------------------------------------------------------------
                objects {
                    chest("chest1_torch", x = 6, y = 2) {
                        usedFlag(chestFlags.chest1.name)
                        onOpen { callOp("add_item", StringLiteral("torch")) }
                    }
                    chest("chest2_magic_key", x = 21, y = 13) {
                        usedFlag(chestFlags.chest2.name)
                        onOpen { callOp("add_item", StringLiteral("magic_key")) }
                    }
                    chest("chest3_potion", x = 21, y = 10) {
                        usedFlag(chestFlags.chest3.name)
                        onOpen { callOp("add_item", StringLiteral("potion")) }
                    }
                    chest("chest4_potions2", x = 2, y = 2) {
                        usedFlag(chestFlags.chest4.name)
                        onOpen {
                            callOp("add_item", StringLiteral("potion"))
                            callOp("add_item", StringLiteral("potion"))
                        }
                    }
                    chest("chest5_ether", x = 29, y = 21) {
                        usedFlag(chestFlags.chest5.name)
                        onOpen { callOp("add_item", StringLiteral("ether")) }
                    }

                    // -------------------------------------------------------------------------
                    // Signs
                    // Original floor1.c:112-124
                    //   (4,2) UP — str_floor1_sign_monster_no_fire
                    //   (2,30) DOWN — str_floor1_sign_empty_chest
                    //   (12,17) DOWN — str_floor1_sign_tunnel_cave_in
                    //   (10,23) UP — str_floor1_sign_hidden_passage_hint
                    //   (21,25) UP — str_floor1_sign_missing_elite
                    // -------------------------------------------------------------------------
                    sign("sign_monster_no_fire", x = 4, y = 2) {
                        onRead {
                            callOp("map_textbox", StringLiteral("str_floor1_sign_monster_no_fire"))
                        }
                    }
                    sign("sign_empty_chest", x = 2, y = 30) {
                        onRead {
                            callOp("map_textbox", StringLiteral("str_floor1_sign_empty_chest"))
                        }
                    }
                    sign("sign_tunnel_cave_in", x = 12, y = 17) {
                        onRead {
                            callOp("map_textbox", StringLiteral("str_floor1_sign_tunnel_cave_in"))
                        }
                    }
                    sign("sign_hidden_passage", x = 10, y = 23) {
                        onRead {
                            callOp(
                                "map_textbox",
                                StringLiteral("str_floor1_sign_hidden_passage_hint"),
                            )
                        }
                    }
                    sign("sign_missing_elite", x = 21, y = 25) {
                        onRead {
                            callOp("map_textbox", StringLiteral("str_floor1_sign_missing_elite"))
                        }
                    }

                    // -------------------------------------------------------------------------
                    // Sconces (interactive — trigger events)
                    // Original floor1.c:165-203
                    //   SCONCE_1 (3,2) on_lit → unlocks CHEST_4
                    //   SCONCE_2 (11,12) on_lit → if SCONCE_2+SCONCE_3 lit → opens DOOR_2
                    //   SCONCE_3 (13,12) on_lit → if SCONCE_2+SCONCE_3 lit → opens DOOR_2
                    //   SCONCE_4 (8,23) on_lit → opens DOOR_4 (elite lair)
                    //   Static sconces: (5,2)R, (6,13)R, (8,13)R, (9,29)R, (11,3)B, (13,3)B,
                    // (20,7)R
                    // -------------------------------------------------------------------------
                    sconce("sconce1_treasure", x = 3, y = 2) {
                        onLit { setFlag(chestFlags.chest4.name) } // unlocks chest 4
                    }
                    sconce("sconce2_boss_door_a", x = 11, y = 12) {
                        onLit { setFlag(worldFlags.floor1BossDefeated.name) }
                    }
                    sconce("sconce3_boss_door_b", x = 13, y = 12) {
                        onLit { setFlag(worldFlags.floor1BossDefeated.name) }
                    }
                    sconce("sconce4_elite", x = 8, y = 23) {
                        onLit { callOp("map_textbox", StringLiteral("str_floor_door_opens")) }
                    }

                    // -------------------------------------------------------------------------
                    // NPC boss (floor 1 area boss)
                    // Original floor1.c:244-258
                    //   NPC_1 at (12,5) — S-Tier Goblin, gated on player.level >= 10
                    //   On victory: opens DOOR_3 (next level door), NPC disappears
                    // -------------------------------------------------------------------------
                    npc("goblin_boss", x = 12, y = 5) {
                        visibleFlag(worldFlags.floor1BossDefeated.name, visibleWhenUnset = true)
                        onTalk {
                            callOp("map_textbox", StringLiteral("str_floor_common_growl"))
                            setFlag(worldFlags.floor1BossDefeated.name)
                        }
                    }
                }

                // -------------------------------------------------------------------------
                // Zone transitions
                // Original floor1.c:103 — { MAP_A, 12, 3, MAP_A, 10, 13, UP, EXIT_STAIRS,
                // &bank_floor2 }
                // Internal stairs (within same floor tilemap) are handled by the object system.
                // Cross-floor transition at tile (12,3) → floor2 at (10,13)
                // -------------------------------------------------------------------------
                transition {
                    to("floor2")
                    entryX(10)
                    entryY(13)
                    conditionFlag(worldFlags.floor1BossDefeated)
                }
            }
            ZoneRef("floor1")
        }
}
