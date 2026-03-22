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
// FLOOR 7 — ABYSSAL CHAMBERS
// =============================================================================
//
// Seventh dungeon floor. Two symmetrical wings (left/right) with sconce puzzles.
// Eye/skull puzzle driven by two toggle levers (START_STUCK) and a lookup table:
//   a_lookup = {1,4,1,1,3,6,7,7}, b_lookup = {2,0,3,5,2,4,0,7}
// Puzzle state 7 (all eyes open) opens DOOR_2 (boss room).
// DOOR_3/DOOR_4 driven by puzzle eye states A and C respectively.
// Each wing has: magic-key chest, item chest, 3 sconces, 2 locked doors.
// Level-gated encounters: low (player.level < 50), high (>=50).
// Boss: Beholder S-tier lv54. Elite: Displacer Beast A-tier lv50.
//
// Original source: LabyrinthOfTheDragon/src/floor7.c
//   - DEFAULT_X=8, DEFAULT_Y=30
//   - MAP_A: floor_seven_data (32x32, BANK_16)
//   - 8 chests: 2 wing magic keys + 2 wing items + 1 item room + 3 secret maze
//   - 2 levers at (7,28) and (9,28) — START_STUCK, eye puzzle state machine
//   - 6 puzzle sconces (3 per wing) + 12 static green/blue sconces
//   - 1 sign: str_floor7_riddle at (10,26)
//   - NPC_1 at (27,7): Beholder S-tier, NPC_2 at (2,17): Displacer Beast A-tier
// =============================================================================

/**
 * Dungeon Floor 7 — Abyssal Chambers.
 *
 * Ported from `floor7.c`. Two-wing layout with an eye puzzle state machine driven by two toggle
 * levers. Both wings have locked doors, sconce puzzles, and magic-key chests. State 7 (all eyes
 * lit) opens the boss room door.
 *
 * ## Encounters
 * - encounters_low (player.level < 50): mindflayer C 15%, 2x displacer 35%, bugbear+goblin 15%, 3x
 *   zombie 35%
 * - encounters_high (>=50): g.cube C 25%, wisp B 30%, 2x g.cube 30%, deathknight A 15%
 *
 * Original reference: `LabyrinthOfTheDragon/src/floor7.c`
 */
object Floor7AbyssalChambers {

    // Encounter group IDs — map to CombatSystem encounter configurations
    private const val LOW_MINDFLAYER_C = "floor7_low_mindflayer_c"
    private const val LOW_DISPLACER_PAIR = "floor7_low_displacer_pair"
    private const val LOW_BUGBEAR_GOBLIN = "floor7_low_bugbear_goblin"
    private const val LOW_ZOMBIE_TRIPLE = "floor7_low_zombie_triple"
    private const val HIGH_GCUBE_C = "floor7_high_gcube_c"
    private const val HIGH_WISP_B = "floor7_high_wisp_b"
    private const val HIGH_GCUBE_PAIR = "floor7_high_gcube_pair"
    private const val HIGH_DEATHKNIGHT_A = "floor7_high_deathknight_a"

    /**
     * Registers Floor 7 in the game builder and returns a typed [ZoneRef].
     *
     * Original: floor7.c — `const Floor floor7 = { ... }`
     *
     * @param chestFlags Chest open-state flags for floors 5-8
     * @param worldFlags World progression flags (floor7BossDefeated gates exit to floor 8)
     */
    fun register(
        builder: GameBuilder,
        chestFlags: ChestFlags5to8,
        worldFlags: WorldFlags,
    ): ZoneRef =
        builder.run {
            zone("floor7") {
                name("Dungeon Level 7 - Abyssal Chambers")
                // Original: floor_seven_data (BANK_16), 32x32 — floor7.c:25-27
                tileset("tilemaps/floors/floor7.tilemap")
                size(32, 32)

                // -------------------------------------------------------------------------
                // Encounter tables — floor7.c:414-457
                //
                // encounters_low (player.level < 50):
                //   15% Mindflayer C lv46, 35% 2x Displacer Beast C lv47,
                //   15% Bugbear B + Goblin C lv46, 35% 3x Zombie C lv44
                //
                // encounters_high (player.level >= 50):
                //   25% G.Cube C lv45, 30% Will-o-Wisp B lv49,
                //   30% 2x G.Cube C lv48, 15% Deathknight A lv47
                // -------------------------------------------------------------------------
                encounters {
                    safeSteps(4)

                    entry(LOW_MINDFLAYER_C, weight = 15) { maxLevel(50) }
                    entry(LOW_DISPLACER_PAIR, weight = 35) { maxLevel(50) }
                    entry(LOW_BUGBEAR_GOBLIN, weight = 15) { maxLevel(50) }
                    entry(LOW_ZOMBIE_TRIPLE, weight = 35) { maxLevel(50) }

                    entry(HIGH_GCUBE_C, weight = 25) { minLevel(50) }
                    entry(HIGH_WISP_B, weight = 30) { minLevel(50) }
                    entry(HIGH_GCUBE_PAIR, weight = 30) { minLevel(50) }
                    entry(HIGH_DEATHKNIGHT_A, weight = 15) { minLevel(50) }
                }

                objects {
                    // -------------------------------------------------------------------------
                    // Chests — floor7.c:33-74
                    // -------------------------------------------------------------------------

                    // Left & Right Wing Magic Key Chests — floor7.c:48-49
                    chest("chest1_left_wing_magic_key", x = 2, y = 1) {
                        usedFlag(chestFlags.chest1.name)
                        onOpen { callOp("add_item", StringLiteral("magic_key")) }
                    }
                    chest("chest2_right_wing_magic_key", x = 18, y = 1) {
                        usedFlag(chestFlags.chest2.name)
                        onOpen { callOp("add_item", StringLiteral("magic_key")) }
                    }

                    // Left & Right Wing Item Chests — floor7.c:52-53
                    chest("chest3_left_wing_haste", x = 6, y = 5) {
                        usedFlag(chestFlags.chest3.name)
                        onOpen { callOp("add_item", StringLiteral("haste")) }
                    }
                    chest("chest4_right_wing_regen", x = 14, y = 5) {
                        usedFlag(chestFlags.chest4.name)
                        onOpen { callOp("add_item", StringLiteral("regen")) }
                    }

                    // Item Room Chest — floor7.c:56-61
                    chest("chest5_item_room_elixirs3", x = 10, y = 17) {
                        usedFlag(chestFlags.chest5.name)
                        onOpen { callOp("add_item", StringLiteral("elixir_x3")) }
                    }

                    // Secret Maze Chests — floor7.c:63-71
                    chest("chest6_maze_regen", x = 25, y = 28) {
                        usedFlag(chestFlags.chest6.name)
                        onOpen { callOp("add_item", StringLiteral("regen")) }
                    }
                    chest("chest7_maze_regen_b", x = 30, y = 22) {
                        usedFlag(chestFlags.chest7.name)
                        onOpen { callOp("add_item", StringLiteral("regen")) }
                    }
                    chest("chest8_maze_potion", x = 25, y = 18) {
                        usedFlag(chestFlags.chest8.name)
                        onOpen { callOp("add_item", StringLiteral("potion")) }
                    }

                    // -------------------------------------------------------------------------
                    // Sign — floor7.c:133
                    // -------------------------------------------------------------------------
                    sign("sign_riddle", x = 10, y = 26) {
                        onRead { callOp("map_textbox", StringLiteral("str_floor7_riddle")) }
                    }

                    // -------------------------------------------------------------------------
                    // Levers — floor7.c:195-209
                    //
                    // Both levers START_STUCK — must be unlocked by walking on trigger tiles
                    // Pulling lever A or B advances puzzle_state via lookup table:
                    //   a_lookup = {1,4,1,1,3,6,7,7}
                    //   b_lookup = {2,0,3,5,2,4,0,7}
                    // puzzle_state == 7 → opens DOOR_2 (boss room)
                    // -------------------------------------------------------------------------
                    lever("lever1_eye_puzzle_a", x = 7, y = 28) {
                        onActivate { callOp("advance_eye_puzzle_f7", StringLiteral("a")) }
                        onDeactivate { callOp("advance_eye_puzzle_f7", StringLiteral("a")) }
                    }
                    lever("lever2_eye_puzzle_b", x = 9, y = 28) {
                        onActivate { callOp("advance_eye_puzzle_f7", StringLiteral("b")) }
                        onDeactivate { callOp("advance_eye_puzzle_f7", StringLiteral("b")) }
                    }

                    // -------------------------------------------------------------------------
                    // Sconces — floor7.c:261-296
                    //
                    // Left wing puzzle sconces (SCONCE_1-3):
                    //   SCONCE_1 at (3,27), SCONCE_2 at (3,9), SCONCE_3 at (3,4)
                    //   SCONCE_3 on_lit → opens DOOR_6 if switch_door_6 active
                    //
                    // Right wing puzzle sconces (SCONCE_4-6):
                    //   SCONCE_4 at (13,27), SCONCE_5 at (17,9), SCONCE_6 at (17,4)
                    //   SCONCE_6 on_lit → opens DOOR_8 if switch_door_8 active
                    // -------------------------------------------------------------------------

                    // Left wing puzzle sconces
                    sconce("sconce_left_wing_entry", x = 3, y = 27) {
                        usedFlag("sconce_left_entry_f7_lit")
                        onInteract { callOp("light_sconce_f7", StringLiteral("left_entry")) }
                    }
                    sconce("sconce_left_wing_mid", x = 3, y = 9) {
                        usedFlag("sconce_left_mid_f7_lit")
                        onInteract { callOp("light_sconce_f7", StringLiteral("left_mid")) }
                    }
                    sconce("sconce_left_wing_key", x = 3, y = 4) {
                        usedFlag("sconce_left_key_f7_lit")
                        onInteract { callOp("light_sconce_f7", StringLiteral("left_key")) }
                    }

                    // Right wing puzzle sconces
                    sconce("sconce_right_wing_entry", x = 13, y = 27) {
                        usedFlag("sconce_right_entry_f7_lit")
                        onInteract { callOp("light_sconce_f7", StringLiteral("right_entry")) }
                    }
                    sconce("sconce_right_wing_mid", x = 17, y = 9) {
                        usedFlag("sconce_right_mid_f7_lit")
                        onInteract { callOp("light_sconce_f7", StringLiteral("right_mid")) }
                    }
                    sconce("sconce_right_wing_key", x = 17, y = 4) {
                        usedFlag("sconce_right_key_f7_lit")
                        onInteract { callOp("light_sconce_f7", StringLiteral("right_key")) }
                    }

                    // Static prelit sconces — floor7.c:282-295 (all green except one blue at
                    // (26,5))
                    sconce("sconce_static_1", x = 6, y = 26)
                    sconce("sconce_static_2", x = 22, y = 27)
                    sconce("sconce_static_3", x = 1, y = 16)
                    sconce("sconce_static_4", x = 3, y = 16)
                    sconce("sconce_static_5", x = 9, y = 16)
                    sconce("sconce_static_6", x = 11, y = 16)
                    sconce("sconce_static_7", x = 2, y = 0)
                    sconce("sconce_static_8", x = 8, y = 4)
                    sconce("sconce_static_9", x = 12, y = 4)
                    sconce("sconce_static_10", x = 18, y = 0)
                    sconce("sconce_static_11", x = 26, y = 5)
                    sconce("sconce_static_12", x = 28, y = 5)

                    // -------------------------------------------------------------------------
                    // NPCs — floor7.c:352-367
                    //
                    // NPC_1 at (27,7): Beholder S-tier lv54 — boss (level-gated >=45)
                    //   Victory: opens DOOR_1 (next level door at (27,5))
                    //
                    // NPC_2 at (2,17): Displacer Beast A-tier lv50 — elite
                    //   Victory: grants item (1x Haste)
                    // -------------------------------------------------------------------------
                    npc("beholder_boss", x = 27, y = 7) {
                        visibleFlag(worldFlags.floor7BossDefeated.name, visibleWhenUnset = true)
                        onTalk {
                            callOp("map_textbox", StringLiteral("str_floor7_boss"))
                            setFlag(worldFlags.floor7BossDefeated)
                        }
                    }
                    npc("displacer_beast_elite", x = 2, y = 17) {
                        usedFlag("npc_displacer_elite_f7")
                        onTalk { callOp("map_textbox", StringLiteral("str_floor7_elite_attack")) }
                    }
                }

                // -------------------------------------------------------------------------
                // Zone transition to floor 8
                // Original floor7.c:116 — { MAP_A, 27, 5, DEFAULT_X, DEFAULT_Y, UP, EXIT_STAIRS,
                // &bank_floor8 }
                // -------------------------------------------------------------------------
                transition {
                    to("floor8")
                    entryX(8)
                    entryY(29)
                    conditionFlag(worldFlags.floor7BossDefeated)
                }
            }
            ZoneRef("floor7")
        }
}
