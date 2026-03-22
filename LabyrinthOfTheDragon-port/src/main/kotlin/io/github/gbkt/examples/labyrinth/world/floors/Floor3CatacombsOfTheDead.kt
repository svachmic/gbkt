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
// FLOOR 3 — CATACOMBS OF THE DEAD
// =============================================================================
//
// Third dungeon floor. Mid-difficulty encounters (bugbear, zombie, kobold, goblin).
// Four special one-time encounters (goblin defenders guarding sconce skull puzzles).
// Boss: Gelatinous Cube (S-tier) gated at level 24+. Elite: Zombie A-tier.
// Defeating boss opens DOOR_3 (next level door to floor 4).
//
// Original source: LabyrinthOfTheDragon/src/floor3.c (bank 8)
//   - DEFAULT_X=26, DEFAULT_Y=17
//   - MAP_A: floor_three_data (32x32, BANK_16)
//   - 5 chests, 11 exits (internal + floor4), 3 doors, 8 dynamic + 6 static sconces
//   - 1 lever (LEVER_1 at (1,30) — one-time use, opens DOOR_1)
//   - NPC_1 (boss): Gelatinous Cube S-tier at (4,14); NPC_2 (elite): Zombie A-tier at (3,2)
//   - 4 special encounters: goblin B-tier defenders at skull sconce tiles
//   - Random encounters: encounters_low (level <21), encounters_high (level >=21)
// =============================================================================

/**
 * Dungeon Floor 3 — Catacombs of the Dead.
 *
 * Ported from `floor3.c`. Single-map layout with sconce-gated boss door, four goblin-defender
 * special encounters, and a boss Gelatinous Cube fight.
 *
 * ## Chests (floor3.c:28-73)
 * - CHEST_1 (1,3): magic key — `chest_add_magic_key` callback (floor_common.c:5)
 * - CHEST_2 (13,15): 1x Potion
 * - CHEST_3 (18,22): 1x Ether
 * - CHEST_4 (25,23): Regen Potion — magic key locked (locked=true, magicKeyOk=true)
 * - CHEST_5 (29,23): Remedy — magic key locked (locked=true, magicKeyOk=true)
 *
 * ## Encounters
 * - encounters_low (player.level < 21, floor3.c:299-320): 10% Bugbear B-tier lv18, 20% Zombie
 *   B-tier lv18, 35% 3x Kobold B-tier (lv17+19+18), 35% Zombie C + Bugbear C
 * - encounters_high (player.level >= 21, floor3.c:323-346): 20% 2x Zombie A-tier lv21, 25%
 *   Bugbear+2x Goblin [LAYOUT_1M_2S], 25% 2x Zombie C-tier lv22, 30% Kobold+Goblin+Kobold
 *
 * ## Sconce Puzzle
 * Player faces UP at skull positions (3,26), (9,26), (22,3), (28,3) to activate sconces. When all 4
 * boss-door sconces (SCONCE_1..4) are lit → opens DOOR_2 (floor3.c:410-423).
 *
 * ## Special Encounters
 * One-time B-tier Goblin lv19 defender at each skull tile (floor3.c:362-396).
 *
 * Original reference: `LabyrinthOfTheDragon/src/floor3.c`
 */
object Floor3CatacombsOfTheDead {

    // Encounter group IDs — map to CombatSystem encounter configurations
    private const val LOW_BUGBEAR_SOLO = "floor3_low_bugbear_solo"
    private const val LOW_ZOMBIE_SOLO = "floor3_low_zombie_solo"
    private const val LOW_KOBOLD_TRIPLE = "floor3_low_kobold_triple"
    private const val LOW_ZOMBIE_BUGBEAR = "floor3_low_zombie_bugbear"
    private const val HIGH_ZOMBIE_PAIR_A = "floor3_high_zombie_pair_a"
    private const val HIGH_BUGBEAR_GOBLINS = "floor3_high_bugbear_goblins"
    private const val HIGH_ZOMBIE_PAIR_C = "floor3_high_zombie_pair_c"
    private const val HIGH_KOBOLD_GOBLIN_TRIPLE = "floor3_high_kobold_goblin_triple"

    /**
     * Registers Floor 3 in the game builder and returns a typed [ZoneRef].
     *
     * Original: floor3.c — `const Floor floor3 = { ... }`
     *
     * @param chestFlags Chest open-state flags for floors 1-4
     * @param worldFlags World progression flags (floor3BossDefeated, floor3EliteDefeated)
     */
    fun register(
        builder: GameBuilder,
        chestFlags: ChestFlags1to4,
        worldFlags: WorldFlags,
    ): ZoneRef =
        builder.run {
            zone("floor3") {
                name("Dungeon Level 3 - Catacombs of the Dead")
                // Original: floor_three_data (BANK_16), 32x32 — floor3.c:18-21
                tileset("tilemaps/floors/floor3.tilemap")
                size(32, 32)

                // -------------------------------------------------------------------------
                // Encounter tables
                // Original floor3.c:299-346
                // config_random_encounter(4, 1, 1, true) — floor3.c:358
                //
                // encounters_low (player.level < 21):
                //   10%  - 1x Bugbear B-Tier lv18 [LAYOUT_1]
                //   20%  - 1x Zombie B-Tier lv18 [LAYOUT_1]
                //   35%  - 3x Kobold B-Tier: lv17 B + lv19 B + lv18 B [LAYOUT_3S]
                //   35%  - 1x Zombie C-Tier lv19 + 1x Bugbear C-Tier lv18 [LAYOUT_2]
                //
                // encounters_high (player.level >= 21):
                //   20%  - 2x Zombie A-Tier lv21 [LAYOUT_2]
                //   25%  - 1x Bugbear B lv20 + 1x Goblin C lv23 + 1x Goblin C lv21 [LAYOUT_1M_2S]
                //   25%  - 2x Zombie C-Tier lv22 [LAYOUT_2]
                //   30%  - 3x: Kobold C lv21 + Goblin B lv20 + Kobold C lv19 [LAYOUT_3S]
                // -------------------------------------------------------------------------
                encounters {
                    safeSteps(4) // Original: config_random_encounter(4, ...)

                    // Low encounters (player < level 21)
                    entry(LOW_BUGBEAR_SOLO, weight = 10) { maxLevel(21) }
                    entry(LOW_ZOMBIE_SOLO, weight = 20) { maxLevel(21) }
                    entry(LOW_KOBOLD_TRIPLE, weight = 35) { maxLevel(21) }
                    entry(LOW_ZOMBIE_BUGBEAR, weight = 35) { maxLevel(21) }

                    // High encounters (player >= level 21)
                    entry(HIGH_ZOMBIE_PAIR_A, weight = 20) { minLevel(21) }
                    entry(HIGH_BUGBEAR_GOBLINS, weight = 25) { minLevel(21) }
                    entry(HIGH_ZOMBIE_PAIR_C, weight = 25) { minLevel(21) }
                    entry(HIGH_KOBOLD_GOBLIN_TRIPLE, weight = 30) { minLevel(21) }
                }

                objects {
                    // -------------------------------------------------------------------------
                    // Chests — floor3.c:28-73
                    // -------------------------------------------------------------------------

                    // CHEST_1 (1,3): magic key — floor3.c:42-47
                    // callback: chest_add_magic_key (floor_common.c:5-15)
                    chest("chest1_magic_key", x = 1, y = 3) {
                        usedFlag(chestFlags.chest1.name)
                        onOpen { callOp("add_item", StringLiteral("magic_key")) }
                    }

                    // CHEST_2 (13,15): 1x Potion — floor3.c:48-53
                    chest("chest2_potion", x = 13, y = 15) {
                        usedFlag(chestFlags.chest2.name)
                        onOpen { callOp("add_item", StringLiteral("potion")) }
                    }

                    // CHEST_3 (18,22): 1x Ether — floor3.c:54-59
                    chest("chest3_ether", x = 18, y = 22) {
                        usedFlag(chestFlags.chest3.name)
                        onOpen { callOp("add_item", StringLiteral("ether")) }
                    }

                    // CHEST_4 (25,23): Regen Potion — floor3.c:60-65
                    // Original: locked=true, magicKeyOk=true (magic key required to open)
                    chest("chest4_regen_locked", x = 25, y = 23) {
                        usedFlag(chestFlags.chest4.name)
                        onOpen { callOp("add_item", StringLiteral("regen_potion")) }
                    }

                    // CHEST_5 (29,23): Remedy — floor3.c:66-71
                    // Original: locked=true, magicKeyOk=true
                    chest("chest5_remedy_locked", x = 29, y = 23) {
                        usedFlag(chestFlags.chest5.name)
                        onOpen { callOp("add_item", StringLiteral("remedy")) }
                    }

                    // -------------------------------------------------------------------------
                    // Signs — floor3.c:117-129
                    // Only one sign on floor 3:
                    //   (27,26) UP — str_floor3_choose_wisely (maze entrance hint)
                    // -------------------------------------------------------------------------
                    sign("sign_choose_wisely", x = 27, y = 26) {
                        onRead { callOp("map_textbox", StringLiteral("str_floor3_choose_wisely")) }
                    }

                    // -------------------------------------------------------------------------
                    // Lever — floor3.c:142-155
                    // LEVER_1 at (1,30) MAP_A — one-time use lever
                    // Original: { LEVER_1, MAP_A, 1, 30, true, false, on_lever_pull }
                    //   on_lever_pull: play sfx_monster_critical, open_door(DOOR_1), textbox
                    // DOOR_1 (17,6) — connects to the magic key room (DOOR_NORMAL)
                    // -------------------------------------------------------------------------
                    lever("lever1_door_open", x = 1, y = 30) {
                        usedFlag(worldFlags.lever1F3Used.name) // one-time (oneUse=true in Original)
                        onActivate {
                            // floor3.c:135-140 — on_lever_pull callback
                            callOp("map_textbox", StringLiteral("str_floor2_door_opens"))
                        }
                    }

                    // -------------------------------------------------------------------------
                    // Sconces — floor3.c:182-213
                    //
                    // Static sconces (SCONCE_STATIC — decorative, always lit blue):
                    //   (13,6)B, (25,6)B, (3,12)B, (5,12)B, (6,29)B, (18,29)B
                    //
                    // Boss-door sconces (SCONCE_1..4) — must all be lit to open DOOR_2:
                    //   SCONCE_1 (24,13), SCONCE_2 (25,13), SCONCE_3 (27,13), SCONCE_4 (28,13)
                    //   When all 4 lit → play sfx_monster_critical, open_door(DOOR_2)
                    // [floor3.c:410-423]
                    //
                    // Skull push-button display sconces (SCONCE_5..8):
                    //   SCONCE_5 (3,24), SCONCE_6 (9,24), SCONCE_7 (22,1), SCONCE_8 (28,1)
                    //   Lit by player action (on_action): facing UP at adjacent skull tile
                    //   Each skull SCONCE lights corresponding boss-door SCONCE + itself
                    // -------------------------------------------------------------------------

                    // Static corridor sconces — floor3.c:192-197
                    sconce("sconce_static_key_room_1", x = 13, y = 6)
                    sconce("sconce_static_key_room_2", x = 25, y = 6)
                    sconce("sconce_static_side_1", x = 3, y = 12)
                    sconce("sconce_static_side_2", x = 5, y = 12)
                    sconce("sconce_static_lower_1", x = 6, y = 29)
                    sconce("sconce_static_lower_2", x = 18, y = 29)

                    // Boss-door sconces — floor3.c:200-204
                    // Activated by player action at skull positions below
                    sconce("sconce1_boss_door_a", x = 24, y = 13) {
                        // When lit, check if all 4 are lit → open DOOR_2
                        onLit { callOp("check_boss_sconces_f3") }
                    }
                    sconce("sconce2_boss_door_b", x = 25, y = 13) {
                        onLit { callOp("check_boss_sconces_f3") }
                    }
                    sconce("sconce3_boss_door_c", x = 27, y = 13) {
                        onLit { callOp("check_boss_sconces_f3") }
                    }
                    sconce("sconce4_boss_door_d", x = 28, y = 13) {
                        onLit { callOp("check_boss_sconces_f3") }
                    }

                    // Skull push-button display sconces — floor3.c:206-212
                    // Player interacts facing UP at adjacent tile (on_action), not via normal
                    // sconce touch
                    sconce("sconce5_skull_sw", x = 3, y = 24)
                    sconce("sconce6_skull_se", x = 9, y = 24)
                    sconce("sconce7_skull_nw", x = 22, y = 1)
                    sconce("sconce8_skull_ne", x = 28, y = 1)

                    // -------------------------------------------------------------------------
                    // NPCs — floor3.c:270-284
                    //
                    // NPC_1 at (4,14): Gelatinous Cube S-tier — boss encounter
                    //   Level gate: player.level < 24 → show str_floor3_boss_not_yet message
                    //   On victory: opens DOOR_3 (next level door), NPC disappears
                    //
                    // NPC_2 at (3,2): Zombie A-tier — elite encounter
                    //   On victory: grants ABILITY_2 (class ability slot 2), NPC disappears
                    // -------------------------------------------------------------------------
                    npc("gelatinous_cube_boss", x = 4, y = 14) {
                        // Visible until boss defeated — floor3.c:220-223
                        visibleFlag(worldFlags.floor3BossDefeated.name, visibleWhenUnset = true)
                        onTalk {
                            // floor3.c:253-268 — on_npc_action for NPC_1
                            callOp("map_textbox", StringLiteral("str_floor3_boss"))
                            setFlag(worldFlags.floor3BossDefeated.name)
                        }
                    }
                    npc("zombie_elite", x = 3, y = 2) {
                        // Visible until elite defeated — floor3.c:224-230
                        visibleFlag(worldFlags.floor3EliteDefeated.name, visibleWhenUnset = true)
                        onTalk {
                            // floor3.c:264-268 — on_npc_action for NPC_2
                            callOp("map_textbox", StringLiteral("str_floor3_brains"))
                            setFlag(worldFlags.floor3EliteDefeated.name)
                        }
                    }
                }

                // -------------------------------------------------------------------------
                // Zone transition to floor 4
                // Original floor3.c:108 — { MAP_A, 4, 12, MAP_A, 24, 30, UP, EXIT_STAIRS,
                // &bank_floor4 }
                // Entry on floor4 at DEFAULT_X=24, DEFAULT_Y=30 (floor4.c:11-12)
                // Gated by boss defeat (DOOR_3 is the next-level door — floor3.c:172-174)
                // -------------------------------------------------------------------------
                transition {
                    to("floor4")
                    entryX(24)
                    entryY(30)
                    conditionFlag(worldFlags.floor3BossDefeated)
                }
            }
        }
}
