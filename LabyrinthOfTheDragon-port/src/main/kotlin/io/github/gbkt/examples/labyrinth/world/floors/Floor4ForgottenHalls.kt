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
// FLOOR 4 — FORGOTTEN HALLS
// =============================================================================
//
// Fourth dungeon floor. Mid-to-hard encounters (owlbear, bugbear, zombie, goblin).
// Three-part flame color puzzle (6 interactive sconces) unlocking the boss door.
// Boss: Displacer Beast (S-tier) at level 29+. Elite: Owlbear B-tier.
// Defeating boss opens DOOR_2 (next level door to floor 5).
//
// Original source: LabyrinthOfTheDragon/src/floor4.c (bank 8)
//   - DEFAULT_X=24, DEFAULT_Y=30
//   - MAP_A: floor_four_data (32x32, BANK_16)
//   - 6 chests (including 2 magic-key locked), 13 exits (internal + floor5), 4 doors
//   - 3 levers (LEVER_1..3): solve color combination puzzle (LEVER_2 OFF, LEVER_3 ON)
//   - 6 interactive colored-flame sconces (3 pairs: green+green, red+red, blue+blue)
//   - NPC_1 (boss): Displacer Beast S-tier at (28,21); NPC_2 (elite): Owlbear A-tier at (1,3)
//   - Random encounters: encounters_low (level <29), encounters_high (level >=29)
// =============================================================================

/**
 * Dungeon Floor 4 — Forgotten Halls.
 *
 * Ported from `floor4.c`. Single-map layout with three-wing structure (west, central, east wings)
 * connected through the main hall. Features a three-pair colored flame puzzle to unlock the boss
 * door.
 *
 * ## Chests (floor4.c:29-86)
 * - CHEST_1 (12,28): Secret 1 — 1x Potion
 * - CHEST_2 (3,28): Secret 2 — 1x Ether
 * - CHEST_3 (2,13): West Wing — magic key
 * - CHEST_4 (18,13): East Wing — 1x Ether
 * - CHEST_5 (18,3): Treasure Room — Regen Potion (magic key locked)
 * - CHEST_6 (20,3): Treasure Room — Haste Potion (magic key locked)
 *
 * ## Lever Puzzle (floor4.c:156-174, on_pull callback)
 * Three levers (west/central/east wings). Correct combo: LEVER_1=OFF, LEVER_2=ON, LEVER_3=ON.
 * - Correct: plays sfx_big_powerup, opens DOOR_3 (elite room) + DOOR_4 (treasure room)
 * - Incorrect: plays sfx_door_unlock, closes DOOR_3 + DOOR_4 (resets)
 *
 * ## Colored Flame Puzzle (floor4.c:244-276, on_lit callback)
 * Three sconce pairs, each must be same color:
 * - SCONCE_1+2 at west wing (2,9), (4,9) → must both be GREEN
 * - SCONCE_3+4 at central hall (9,9), (10,9) → must both be RED
 * - SCONCE_5+6 at east wing (17,9), (18,9) → must both be BLUE When all 3 pairs correct → opens
 *   DOOR_1 (boss door at 28,27) Wrong color → extinguishes both sconces in pair (must retry)
 *
 * ## Encounters
 * - encounters_low (player.level < 29, floor4.c:405-425): 10% Owlbear B-tier lv25, 20% Bugbear
 *   C-tier lv24, 35% 2x Goblin C/B (lv25, lv25), 35% 2x Zombie C (lv19, lv18)
 * - encounters_high (player.level >= 29, floor4.c:428-450): 20% 2x Owlbear C (lv27, lv29), 25%
 *   Bugbear B lv31, 25% 2x Zombie C lv29, 30% 3x Goblin (lv28 C, lv29 B, lv28 C)
 *
 * Original reference: `LabyrinthOfTheDragon/src/floor4.c`
 */
object Floor4ForgottenHalls {

    // Encounter group IDs — map to CombatSystem encounter configurations
    private const val LOW_OWLBEAR_SOLO = "floor4_low_owlbear_solo"
    private const val LOW_BUGBEAR_SOLO = "floor4_low_bugbear_solo"
    private const val LOW_GOBLIN_PAIR = "floor4_low_goblin_pair"
    private const val LOW_ZOMBIE_PAIR = "floor4_low_zombie_pair"
    private const val HIGH_OWLBEAR_PAIR = "floor4_high_owlbear_pair"
    private const val HIGH_BUGBEAR_B = "floor4_high_bugbear_b"
    private const val HIGH_ZOMBIE_PAIR = "floor4_high_zombie_pair"
    private const val HIGH_GOBLIN_TRIPLE = "floor4_high_goblin_triple"

    /**
     * Registers Floor 4 in the game builder and returns a typed [ZoneRef].
     *
     * Original: floor4.c — `const Floor floor4 = { ... }`
     *
     * @param chestFlags Chest open-state flags for floors 1-4
     * @param worldFlags World progression flags (floor4BossDefeated, floor4EliteDefeated)
     */
    fun register(
        builder: GameBuilder,
        chestFlags: ChestFlags1to4,
        worldFlags: WorldFlags,
    ): ZoneRef =
        builder.run {
            zone("floor4") {
                name("Dungeon Level 4 - Forgotten Halls")
                // Original: floor_four_data (BANK_16), 32x32 — floor4.c:18-22
                tileset("tilemaps/floors/floor4.tilemap")
                size(32, 32)

                // -------------------------------------------------------------------------
                // Encounter tables
                // Original floor4.c:405-450
                // config_random_encounter(4, 1, 1, true) — floor4.c:452
                //
                // encounters_low (player.level < 29):
                //   10%  - 1x Owlbear B-Tier lv25 [LAYOUT_1]
                //   20%  - 1x Bugbear C-Tier lv24 [LAYOUT_1]
                //   35%  - 2x Goblin (lv25 C, lv25 B) [LAYOUT_2]
                //   35%  - 2x Zombie (lv19 C, lv18 C) [LAYOUT_2]
                //
                // encounters_high (player.level >= 29):
                //   20%  - 2x Owlbear C (lv27, lv29) [LAYOUT_2]
                //   25%  - 1x Bugbear B-Tier lv31 [LAYOUT_1]
                //   25%  - 2x Zombie C lv29 [LAYOUT_2]
                //   30%  - 3x Goblin (lv28 C, lv29 B, lv28 C) [LAYOUT_3S]
                // -------------------------------------------------------------------------
                encounters {
                    safeSteps(4) // Original: config_random_encounter(4, ...)

                    // Low encounters (player < level 29)
                    entry(LOW_OWLBEAR_SOLO, weight = 10) { maxLevel(29) }
                    entry(LOW_BUGBEAR_SOLO, weight = 20) { maxLevel(29) }
                    entry(LOW_GOBLIN_PAIR, weight = 35) { maxLevel(29) }
                    entry(LOW_ZOMBIE_PAIR, weight = 35) { maxLevel(29) }

                    // High encounters (player >= level 29)
                    entry(HIGH_OWLBEAR_PAIR, weight = 20) { minLevel(29) }
                    entry(HIGH_BUGBEAR_B, weight = 25) { minLevel(29) }
                    entry(HIGH_ZOMBIE_PAIR, weight = 25) { minLevel(29) }
                    entry(HIGH_GOBLIN_TRIPLE, weight = 30) { minLevel(29) }
                }

                objects {
                    // -------------------------------------------------------------------------
                    // Chests — floor4.c:29-86
                    // -------------------------------------------------------------------------

                    // CHEST_1 (12,28): Secret 1 — 1x Potion — floor4.c:43-48
                    chest("chest1_secret_potion", x = 12, y = 28) {
                        usedFlag(chestFlags.chest1.name)
                        onOpen { callOp("add_item", StringLiteral("potion")) }
                    }

                    // CHEST_2 (3,28): Secret 2 — 1x Ether — floor4.c:49-55
                    chest("chest2_secret_ether", x = 3, y = 28) {
                        usedFlag(chestFlags.chest2.name)
                        onOpen { callOp("add_item", StringLiteral("ether")) }
                    }

                    // CHEST_3 (2,13): West Wing — magic key — floor4.c:56-62
                    // callback: chest_add_magic_key (floor_common.c:5)
                    chest("chest3_west_magic_key", x = 2, y = 13) {
                        usedFlag(chestFlags.chest3.name)
                        onOpen { callOp("add_item", StringLiteral("magic_key")) }
                    }

                    // CHEST_4 (18,13): East Wing — 1x Ether — floor4.c:63-68
                    chest("chest4_east_ether", x = 18, y = 13) {
                        usedFlag(chestFlags.chest4.name)
                        onOpen { callOp("add_item", StringLiteral("ether")) }
                    }

                    // CHEST_5 (18,3): Treasure Room — Regen Potion — floor4.c:71-77
                    // Original: locked=true, magicKeyOk=true
                    chest("chest5_treasure_regen", x = 18, y = 3) {
                        usedFlag(chestFlags.chest5.name)
                        onOpen { callOp("add_item", StringLiteral("regen_potion")) }
                    }

                    // CHEST_6 (20,3): Treasure Room — Haste Potion — floor4.c:78-84
                    // Original: locked=true, magicKeyOk=true
                    chest("chest6_treasure_haste", x = 20, y = 3) {
                        usedFlag(chestFlags.chest6.name)
                        onOpen { callOp("add_item", StringLiteral("haste_potion")) }
                    }

                    // -------------------------------------------------------------------------
                    // Signs — floor4.c:140-150
                    // No signs on floor 4 (array is { END } in Original)
                    // -------------------------------------------------------------------------

                    // -------------------------------------------------------------------------
                    // Levers — floor4.c:176-198
                    // Lever puzzle: LEVER_2 OFF + LEVER_3 ON opens DOOR_3 (elite) + DOOR_4
                    // (treasure)
                    //
                    // LEVER_1 (1,17) MAP_A — West Wing — floor4.c:188-189
                    // LEVER_2 (10,16) MAP_A — Central Hall — floor4.c:191-192
                    // LEVER_3 (19,17) MAP_A — East Wing — floor4.c:194-195
                    //
                    // on_pull callback checks: !lever1 && lever2 && lever3 — floor4.c:163-174
                    //   Correct: sfx_big_powerup, open DOOR_3+DOOR_4
                    //   Wrong: sfx_door_unlock, close DOOR_3+DOOR_4
                    // -------------------------------------------------------------------------
                    lever("lever1_west_wing", x = 1, y = 17) {
                        onActivate { callOp("check_lever_puzzle_f4") }
                        onDeactivate { callOp("check_lever_puzzle_f4") }
                    }
                    lever("lever2_central_hall", x = 10, y = 16) {
                        onActivate { callOp("check_lever_puzzle_f4") }
                        onDeactivate { callOp("check_lever_puzzle_f4") }
                    }
                    lever("lever3_east_wing", x = 19, y = 17) {
                        onActivate { callOp("check_lever_puzzle_f4") }
                        onDeactivate { callOp("check_lever_puzzle_f4") }
                    }

                    // -------------------------------------------------------------------------
                    // Sconces — floor4.c:280-317
                    //
                    // Colored flame puzzle — three pairs of interactive sconces:
                    // - West Wing pair: SCONCE_1 (2,9), SCONCE_2 (4,9) — must be GREEN
                    // - Central Hall pair: SCONCE_3 (9,9), SCONCE_4 (10,9) — must be RED
                    // - East Wing pair: SCONCE_5 (17,9), SCONCE_6 (18,9) — must be BLUE
                    // Logic: on_lit → test_sconces() checks if both in pair are same color
                    //   - Both correct color → puzzle_count++ → if puzzle_count>=3 → opens DOOR_1
                    //   - Wrong color → extinguish both sconces in pair
                    //
                    // Main room static colored sconces:
                    //   (23,27)B, (24,27)G, (25,27)R — hint at correct colors per wing
                    //
                    // Treasure+Elite room static sconces:
                    //   (1,2)R, (19,2)R
                    //
                    // Boss room static sconces:
                    //   (27,19)R, (29,19)R
                    // -------------------------------------------------------------------------

                    // West Wing interactive pair — floor4.c:291-292
                    sconce("sconce1_west_a", x = 2, y = 9) {
                        onLit { callOp("check_color_puzzle_f4", StringLiteral("green")) }
                        onExtinguished { callOp("reset_color_pair_f4", StringLiteral("west")) }
                    }
                    sconce("sconce2_west_b", x = 4, y = 9) {
                        onLit { callOp("check_color_puzzle_f4", StringLiteral("green")) }
                        onExtinguished { callOp("reset_color_pair_f4", StringLiteral("west")) }
                    }

                    // Central Hall interactive pair — floor4.c:295-296
                    sconce("sconce3_central_a", x = 9, y = 9) {
                        onLit { callOp("check_color_puzzle_f4", StringLiteral("red")) }
                        onExtinguished { callOp("reset_color_pair_f4", StringLiteral("central")) }
                    }
                    sconce("sconce4_central_b", x = 10, y = 9) {
                        onLit { callOp("check_color_puzzle_f4", StringLiteral("red")) }
                        onExtinguished { callOp("reset_color_pair_f4", StringLiteral("central")) }
                    }

                    // East Wing interactive pair — floor4.c:299-300
                    sconce("sconce5_east_a", x = 17, y = 9) {
                        onLit { callOp("check_color_puzzle_f4", StringLiteral("blue")) }
                        onExtinguished { callOp("reset_color_pair_f4", StringLiteral("east")) }
                    }
                    sconce("sconce6_east_b", x = 18, y = 9) {
                        onLit { callOp("check_color_puzzle_f4", StringLiteral("blue")) }
                        onExtinguished { callOp("reset_color_pair_f4", StringLiteral("east")) }
                    }

                    // Main room hint sconces (static) — floor4.c:303-305
                    // Blue, Green, Red display the correct answer order
                    sconce("sconce_static_hint_blue", x = 23, y = 27)
                    sconce("sconce_static_hint_green", x = 24, y = 27)
                    sconce("sconce_static_hint_red", x = 25, y = 27)

                    // Treasure and Elite room static sconces — floor4.c:308-309
                    sconce("sconce_static_treasure", x = 1, y = 2)
                    sconce("sconce_static_east_room", x = 19, y = 2)

                    // Boss room static sconces — floor4.c:311-312
                    sconce("sconce_static_boss_a", x = 27, y = 19)
                    sconce("sconce_static_boss_b", x = 29, y = 19)

                    // -------------------------------------------------------------------------
                    // NPCs — floor4.c:374-391
                    //
                    // NPC_1 (28,21): Displacer Beast S-tier — boss encounter
                    //   Level gate: player.level < 29 → show str_floor4_boss_not_yet
                    //   On victory: opens DOOR_2 (next level door), NPC disappears
                    //
                    // NPC_2 (1,3): Owlbear A-tier — elite encounter
                    //   On victory: grants ABILITY_3, NPC disappears
                    // -------------------------------------------------------------------------
                    npc("displacer_beast_boss", x = 28, y = 21) {
                        // Visible until boss defeated — floor4.c:323-327
                        visibleFlag(worldFlags.floor4BossDefeated.name, visibleWhenUnset = true)
                        onTalk {
                            // floor4.c:356-370 — on_npc_action for NPC_1
                            callOp("map_textbox", StringLiteral("str_floor4_boss"))
                            setFlag(worldFlags.floor4BossDefeated.name)
                        }
                    }
                    npc("owlbear_elite", x = 1, y = 3) {
                        // Visible until elite defeated — floor4.c:328-333
                        visibleFlag(worldFlags.floor4EliteDefeated.name, visibleWhenUnset = true)
                        onTalk {
                            // floor4.c:364-369 — on_npc_action for NPC_2
                            callOp("map_textbox", StringLiteral("str_floor4_elite_attack"))
                            setFlag(worldFlags.floor4EliteDefeated.name)
                        }
                    }
                }

                // -------------------------------------------------------------------------
                // Zone transition to floor 5
                // Original floor4.c:131 — { MAP_A, 28, 19, MAP_A, 12, 30, UP, EXIT_STAIRS,
                // &bank_floor5 }
                // DOOR_2 (28,19) is the DOOR_NEXT_LEVEL — opened when boss is defeated
                // Entry on floor5 at (12,30) — floor5 default position
                // -------------------------------------------------------------------------
                transition {
                    to("floor5")
                    entryX(12)
                    entryY(30)
                    conditionFlag(worldFlags.floor4BossDefeated)
                }
            }
        }
}
