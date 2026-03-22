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
import io.github.gbkt.examples.labyrinth.world.ChestFlags5to8
import io.github.gbkt.examples.labyrinth.world.WorldFlags

// =============================================================================
// FLOOR 5 — CRYPTIC DEPTHS
// =============================================================================
//
// Fifth dungeon floor. Hard encounters (gelatinous cube, owlbear, bugbear, zombie).
// Two-sub-map layout: MAP_A (32x32 main maze) + MAP_B (23x7 treasure/boss rooms).
// Three-lever flame puzzle (RED + GREEN + BLUE) gates DOOR_3 (boss room staircase).
// A fourth lightable sconce (SCONCE_4) opens the item room door (DOOR_2).
// Boss: Deathknight B-tier lv39, gated at level 40+. Elite: Gelatinous Cube B-tier lv37.
//
// Original source: LabyrinthOfTheDragon/src/floor5.c (bank 8)
//   - DEFAULT_X=12, DEFAULT_Y=30
//   - MAP_A: floor_five_data (32x32, BANK_16)
//   - MAP_B: floor_five_sub_data (23x7, BANK_16) — treasure + boss rooms
//   - 7 chests: 4 maze chests + 3 treasure room chests (magic-key locked)
//   - 3 levers: LEVER_1 (skull, needs RED), LEVER_2 (potion, needs GREEN),
//               LEVER_3 (boss, needs BLUE) — each cycles flame color
//   - SCONCE_4 (3,9) — when lit → opens DOOR_2 (item room)
//   - NPC_1 at MAP_B (3,3): Deathknight S-tier — boss, level-gated >=40
//   - NPC_2 at MAP_B (11,3): Gelatinous Cube A-tier — elite, no level gate
//   - Encounters low (<level 37): g.cube B 10%, 2x bugbear 20%, owlbear B 35%, 2x zombie 35%
//   - Encounters high (>=level 37): 2x g.cube C 25%, owlbear B 30%, zombie+zombie_b 30%, 3x goblin
// 15%
// =============================================================================

/**
 * Dungeon Floor 5 — Cryptic Depths.
 *
 * Ported from `floor5.c`. Two-sub-map layout with a three-lever flame color puzzle gating the boss
 * room entrance. Features rare-tier monsters from floor 5 onward.
 *
 * ## Chests (floor5.c:31-57)
 * - CHEST_1 (23,11) MAP_A: Maze — 1x Remedy
 * - CHEST_2 (21,18) MAP_A: Maze — 3x Potion
 * - CHEST_3 (6,16) MAP_A: Maze — Magic Key
 * - CHEST_4 (6,30) MAP_A: Maze — Magic Key
 * - CHEST_5 (17,3) MAP_B: Treasure Room — 3x Ether (locked, magic key)
 * - CHEST_6 (19,2) MAP_B: Treasure Room — 1x Elixir (locked, magic key)
 * - CHEST_7 (21,3) MAP_B: Treasure Room — 1x ATK Up + 1x DEF Up (locked, magic key)
 *
 * ## Flame Puzzle (floor5.c:133-168, on_lever_pulled callback)
 * Levers 1-3 cycle their linked sconce color: RED → GREEN → BLUE → RED ... Correct combo:
 * SCONCE_1=RED, SCONCE_2=GREEN, SCONCE_3=BLUE → opens DOOR_3 Wrong: closes DOOR_3
 *
 * ## Encounters
 * - encounters_low (player.level < 37, floor5.c:363-383): 10% Gelatinous Cube B-tier lv30, 20% 2x
 *   Bugbear C lv32, 35% Owlbear B-tier lv30, 35% 2x Zombie C (lv30+32)
 * - encounters_high (player.level >= 37, floor5.c:386-408): 25% 2x Gelatinous Cube C lv34, 30%
 *   Owlbear B lv38, 30% 2x Zombie C/B lv36, 15% 3x Goblin (lv39 C + lv38 B + lv39 C)
 *
 * Original reference: `LabyrinthOfTheDragon/src/floor5.c`
 */
object Floor5CrypticDepths {

    // Encounter group IDs — map to CombatSystem encounter configurations
    private const val LOW_GCUBE_B = "floor5_low_gcube_b"
    private const val LOW_BUGBEAR_PAIR = "floor5_low_bugbear_pair"
    private const val LOW_OWLBEAR_B = "floor5_low_owlbear_b"
    private const val LOW_ZOMBIE_PAIR = "floor5_low_zombie_pair"
    private const val HIGH_GCUBE_PAIR = "floor5_high_gcube_pair"
    private const val HIGH_OWLBEAR_B = "floor5_high_owlbear_b"
    private const val HIGH_ZOMBIE_PAIR = "floor5_high_zombie_pair"
    private const val HIGH_GOBLIN_TRIPLE = "floor5_high_goblin_triple"

    /**
     * Registers Floor 5 in the game builder and returns a typed [ZoneRef].
     *
     * Original: floor5.c — `const Floor floor5 = { ... }`
     *
     * @param chestFlags Chest open-state flags for floors 5-8
     * @param worldFlags World progression flags (floor5BossDefeated gates exit to floor 6)
     */
    fun register(
        builder: GameBuilder,
        chestFlags: ChestFlags5to8,
        worldFlags: WorldFlags,
    ): ZoneRef =
        builder.run {
            zone("floor5") {
                name("Dungeon Level 5 - Cryptic Depths")
                // Original: floor_five_data (BANK_16), 32x32 — floor5.c:19-23
                // MAP_B: floor_five_sub_data (23x7) — boss/elite/item rooms
                tileset("tilemaps/floors/floor5.tilemap")
                size(32, 32)

                // -------------------------------------------------------------------------
                // Encounter tables
                // Original floor5.c:363-408
                // config_random_encounter(4, 1, 1, true) — floor5.c:411
                //
                // encounters_low (player.level < 37):
                //   10%  - 1x Gelatinous Cube B-Tier lv30 [LAYOUT_1]
                //   20%  - 2x Bugbear C-Tier lv32 [LAYOUT_2]
                //   35%  - 1x Owlbear B-Tier lv30 [LAYOUT_1]
                //   35%  - 2x Zombie C-Tier (lv30, lv32) [LAYOUT_2]
                //
                // encounters_high (player.level >= 37):
                //   25%  - 2x Gelatinous Cube C-Tier lv34 [LAYOUT_2]
                //   30%  - 1x Owlbear B-Tier lv38 [LAYOUT_1]
                //   30%  - 2x Zombie (lv36 C, lv36 B) [LAYOUT_2]
                //   15%  - 3x Goblin (lv39 C, lv38 B, lv39 C) [LAYOUT_3S]
                // -------------------------------------------------------------------------
                encounters {
                    safeSteps(4) // Original: config_random_encounter(4, ...)

                    // Low encounters (player < level 37)
                    entry(LOW_GCUBE_B, weight = 10) { maxLevel(37) }
                    entry(LOW_BUGBEAR_PAIR, weight = 20) { maxLevel(37) }
                    entry(LOW_OWLBEAR_B, weight = 35) { maxLevel(37) }
                    entry(LOW_ZOMBIE_PAIR, weight = 35) { maxLevel(37) }

                    // High encounters (player >= level 37)
                    entry(HIGH_GCUBE_PAIR, weight = 25) { minLevel(37) }
                    entry(HIGH_OWLBEAR_B, weight = 30) { minLevel(37) }
                    entry(HIGH_ZOMBIE_PAIR, weight = 30) { minLevel(37) }
                    entry(HIGH_GOBLIN_TRIPLE, weight = 15) { minLevel(37) }
                }

                objects {
                    // -------------------------------------------------------------------------
                    // Chests — floor5.c:31-57
                    // -------------------------------------------------------------------------

                    // CHEST_1 (23,11) MAP_A: Maze — 1x Remedy — floor5.c:46
                    chest("chest1_remedy", x = 23, y = 11) {
                        usedFlag(chestFlags.chest1.name)
                        onOpen { callOp("add_item", StringLiteral("remedy")) }
                    }

                    // CHEST_2 (21,18) MAP_A: Maze — 3x Potion — floor5.c:47
                    chest("chest2_potions3", x = 21, y = 18) {
                        usedFlag(chestFlags.chest2.name)
                        onOpen { callOp("add_item", StringLiteral("potion_x3")) }
                    }

                    // CHEST_3 (6,16) MAP_A: Maze — Magic Key — floor5.c:48
                    // callback: chest_add_magic_key (floor_common.c:5)
                    chest("chest3_magic_key", x = 6, y = 16) {
                        usedFlag(chestFlags.chest3.name)
                        onOpen { callOp("add_item", StringLiteral("magic_key")) }
                    }

                    // CHEST_4 (6,30) MAP_A: Maze — Magic Key — floor5.c:49
                    chest("chest4_magic_key_b", x = 6, y = 30) {
                        usedFlag(chestFlags.chest4.name)
                        onOpen { callOp("add_item", StringLiteral("magic_key")) }
                    }

                    // CHEST_5 (17,3) MAP_B: Treasure Room — 3x Ether — floor5.c:52
                    // Original: locked=true, magicKeyOk=true
                    chest("chest5_ethers3", x = 17, y = 3) {
                        usedFlag(chestFlags.chest5.name)
                        onOpen { callOp("add_item", StringLiteral("ether_x3")) }
                    }

                    // CHEST_6 (19,2) MAP_B: Treasure Room — 1x Elixir — floor5.c:53
                    // Original: locked=true, magicKeyOk=true
                    chest("chest6_elixir", x = 19, y = 2) {
                        usedFlag(chestFlags.chest6.name)
                        onOpen { callOp("add_item", StringLiteral("elixir")) }
                    }

                    // CHEST_7 (21,3) MAP_B: Treasure Room — 1x ATK Up + 1x DEF Up — floor5.c:54
                    // Original: locked=true, magicKeyOk=true
                    chest("chest7_atkup_defup", x = 21, y = 3) {
                        usedFlag(chestFlags.chest7.name)
                        onOpen { callOp("add_item", StringLiteral("atk_up_def_up")) }
                    }

                    // -------------------------------------------------------------------------
                    // Signs — floor5.c:96-112
                    //   (12,27) UP MAP_A — str_floor5_demands (cryptic entry message)
                    //   (20,29) UP MAP_A — str_floor5_secrets (cryptic secret message)
                    // -------------------------------------------------------------------------
                    sign("sign_demands", x = 12, y = 27) {
                        onRead { callOp("map_textbox", StringLiteral("str_floor5_demands")) }
                    }
                    sign("sign_secrets", x = 20, y = 29) {
                        onRead { callOp("map_textbox", StringLiteral("str_floor5_secrets")) }
                    }

                    // -------------------------------------------------------------------------
                    // Levers — floor5.c:170-192
                    // Three levers cycle through flame colors (RED → GREEN → BLUE → RED).
                    // Target: SCONCE_1=RED, SCONCE_2=GREEN, SCONCE_3=BLUE → opens DOOR_3
                    //
                    // LEVER_1 (10,12) — "Skull" lever — sconce needs RED
                    // LEVER_2 (2,3) — "Potion" lever — sconce needs GREEN
                    // LEVER_3 (27,20) — "Boss" lever — sconce needs BLUE
                    // -------------------------------------------------------------------------
                    lever("lever1_skull_red", x = 10, y = 12) {
                        onActivate { callOp("cycle_lever_flame_f5", StringLiteral("lever1")) }
                    }
                    lever("lever2_potion_green", x = 2, y = 3) {
                        onActivate { callOp("cycle_lever_flame_f5", StringLiteral("lever2")) }
                    }
                    lever("lever3_boss_blue", x = 27, y = 20) {
                        onActivate { callOp("cycle_lever_flame_f5", StringLiteral("lever3")) }
                    }

                    // -------------------------------------------------------------------------
                    // Sconces — floor5.c:234-277
                    //
                    // Puzzle sconces (linked to levers — display current flame color):
                    //   SCONCE_1 (10,10) MAP_A — linked to LEVER_1 (must be RED)
                    //   SCONCE_2 (2,1) MAP_A — linked to LEVER_2 (must be GREEN)
                    //   SCONCE_3 (27,18) MAP_A — linked to LEVER_3 (must be BLUE)
                    //
                    // Lightable maze sconce (SCONCE_4):
                    //   SCONCE_4 (3,9) MAP_A — on_lit → opens DOOR_2 (item room)
                    //
                    // Other lightable maze sconces (SCONCE_5..8):
                    //   SCONCE_5 (13,12), SCONCE_6 (7,11), SCONCE_7 (22,7), SCONCE_8 (30,7) MAP_A
                    //
                    // Signpost sconces at entryway (static, hint puzzle):
                    //   (11,26)R, (12,26)G, (13,26)B MAP_A
                    //
                    // Static maze sconces:
                    //   (5,1)R, (14,17)R, (8,27)R, (16,27)R, (28,27)R, (19,1)R, (28,11)R, (4,18)B
                    //
                    // Static boss room sconces (MAP_B):
                    //   (2,1)R, (4,1)R
                    // -------------------------------------------------------------------------

                    // Puzzle sconces (display lever's current color) — floor5.c:246-249
                    sconce("sconce1_lever1_display", x = 10, y = 10)
                    sconce("sconce2_lever2_display", x = 2, y = 1)
                    sconce("sconce3_lever3_display", x = 27, y = 18)

                    // Lightable sconce that opens item room — floor5.c:251-253
                    sconce("sconce4_item_room", x = 3, y = 9) {
                        onLit { callOp("open_item_room_f5") }
                    }

                    // Other lightable maze sconces — floor5.c:254-257
                    sconce("sconce5_maze_a", x = 13, y = 12)
                    sconce("sconce6_maze_b", x = 7, y = 11)
                    sconce("sconce7_maze_c", x = 22, y = 7)
                    sconce("sconce8_maze_d", x = 30, y = 7)

                    // Entryway hint sconces (static) — floor5.c:259-261
                    sconce("sconce_static_hint_red", x = 11, y = 26)
                    sconce("sconce_static_hint_green", x = 12, y = 26)
                    sconce("sconce_static_hint_blue", x = 13, y = 26)

                    // Static maze sconces — floor5.c:263-270
                    sconce("sconce_static_maze_1", x = 5, y = 1)
                    sconce("sconce_static_maze_2", x = 14, y = 17)
                    sconce("sconce_static_maze_3", x = 8, y = 27)
                    sconce("sconce_static_maze_4", x = 16, y = 27)
                    sconce("sconce_static_maze_5", x = 28, y = 27)
                    sconce("sconce_static_maze_6", x = 19, y = 1)
                    sconce("sconce_static_maze_7", x = 28, y = 11)
                    sconce("sconce_static_maze_8", x = 4, y = 18)

                    // -------------------------------------------------------------------------
                    // NPCs — floor5.c:334-349
                    //
                    // NPC_1 at MAP_B (3,3): Deathknight S-tier — boss encounter
                    //   Level gate: player.level < 40 → show str_floor5_boss_not_yet
                    //   On victory: opens DOOR_1 (next level door at MAP_B (3,1)), NPC disappears
                    //
                    // NPC_2 at MAP_B (11,3): Gelatinous Cube A-tier — elite encounter
                    //   On victory: grants ABILITY_4, NPC disappears
                    // -------------------------------------------------------------------------
                    npc("deathknight_boss", x = 3, y = 3) {
                        // Visible until boss defeated — floor5.c:283-286
                        visibleFlag(worldFlags.floor5BossDefeated.name, visibleWhenUnset = true)
                        onTalk {
                            // floor5.c:316-330 — on_npc_action for NPC_1
                            callOp("map_textbox", StringLiteral("str_floor5_boss"))
                            setFlag(worldFlags.floor5BossDefeated)
                        }
                    }
                    npc("gcube_elite", x = 11, y = 3) {
                        // Elite NPC disappears after first interaction — tracked via usedFlag
                        // floor5.c:326-330 — on_npc_action for NPC_2
                        usedFlag("npc_gcube_elite_f5")
                        onTalk { callOp("map_textbox", StringLiteral("str_floor5_elite_attack")) }
                    }
                }

                // -------------------------------------------------------------------------
                // Zone transition to floor 6
                // Original floor5.c:87 — { MAP_B, 3, 1, MAP_A, 8, 7, UP, EXIT_STAIRS, &bank_floor6
                // }
                // DOOR_1 (MAP_B, 3, 1) is DOOR_NEXT_LEVEL — opened when boss is defeated
                // Entry on floor6 at DEFAULT_X=8, DEFAULT_Y=7 (floor6.c:11-12)
                // -------------------------------------------------------------------------
                transition {
                    to("floor6")
                    entryX(8)
                    entryY(7)
                    conditionFlag(worldFlags.floor5BossDefeated)
                }
            }
            ZoneRef("floor5")
        }
}
