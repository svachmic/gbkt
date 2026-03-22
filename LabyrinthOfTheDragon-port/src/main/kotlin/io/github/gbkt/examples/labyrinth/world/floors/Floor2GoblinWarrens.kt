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
// FLOOR 2 — GOBLIN WARRENS
// =============================================================================
//
// Second dungeon floor. Goblin and zombie encounters, two sub-maps (A+B),
// lever-gated doors, and two NPC encounters (elite Bugbear and boss Owlbear).
// The boss Owlbear is level-gated (player must be level 17+). Defeating the
// boss unlocks DOOR_9 (staircase to floor 3).
//
// Original source: LabyrinthOfTheDragon/src/floor2.c
//   - DEFAULT_X=10, DEFAULT_Y=13
//   - MAP_A: floor_two_v2 (24x24, BANK_17)
//   - MAP_B: floor_two_v2_b (32x16, BANK_17)
//   - 5 chests, 14 exits, 9 doors (3 magic key + 2 toggle pair + 1 sconce + 1 next-level)
//   - 4 levers (LEVER_1 toggles doors, LEVER_2+3 light sconces → opens DOOR_8, LEVER_4 opens
// DOOR_1)
//   - NPC_1 (elite): Bugbear B-Tier at MAP_A (3,5); NPC_2 (boss): Owlbear S-Tier at MAP_A (10,3)
//   - Random encounters: random_enc_lv12 (level <16), random_enc_lv16 (level >=16)
// =============================================================================

/**
 * Dungeon Floor 2 — Goblin Warrens.
 *
 * Ported from `floor2.c`. Two-sub-map layout with lever puzzles, magic-key doors, and NPC
 * elite/boss battles gating progress to floor 3.
 */
object Floor2GoblinWarrens {

    // Encounter group IDs — map to CombatSystem encounter configurations
    private const val LOW_GOBLIN_PAIR = "floor2_low_goblin_pair"
    private const val LOW_ZOMBIE = "floor2_low_zombie"
    private const val LOW_KOBOLD_TRIPLE = "floor2_low_kobold_triple"
    private const val LOW_GOBLIN_KOBOLDS = "floor2_low_goblin_kobolds"
    private const val HIGH_GOBLIN_A = "floor2_high_goblin_a"
    private const val HIGH_KOBOLD_TRIPLE = "floor2_high_kobold_triple"
    private const val HIGH_ZOMBIE_PAIR = "floor2_high_zombie_pair"
    private const val HIGH_KOBOLD_A = "floor2_high_kobold_a"

    /**
     * Registers Floor 2 in the game builder and returns a typed [ZoneRef].
     *
     * Original: floor2.c — `const Floor floor2 = { ... }`
     */
    fun register(
        builder: GameBuilder,
        chestFlags: ChestFlags1to4,
        worldFlags: WorldFlags,
    ): ZoneRef =
        builder.run {
            zone("floor2") {
                name("Dungeon Level 2 - Goblin Warrens")
                tileset("tilemaps/floors/floor2.tilemap")
                size(24, 24)

                // -------------------------------------------------------------------------
                // Encounter tables
                // Original floor2.c:340-394
                //
                // random_enc_lv12 (player.level < 16):
                //   25% - 2x Goblin B-Tier (lv10, lv11)
                //   30% - 1x Zombie C-Tier lv12
                //   25% - 3x Kobold (lv9 C, lv9 B, lv9 C)
                //   20% - 1x Kobold C-Tier lv11 + 1x Goblin B-Tier lv12 + 1x Kobold C-Tier lv11
                //
                // random_enc_lv16 (player.level >= 16):
                //   25% - 1x Goblin A-Tier lv14
                //   25% - 3x Kobold (lv14 C, lv15 A, lv14 C)
                //   25% - 2x Zombie B-Tier (lv15, lv15)
                //   25% - 1x Kobold A-Tier lv15
                // -------------------------------------------------------------------------
                encounters {
                    safeSteps(6) // Original: config_random_encounter(6, ...)

                    // Low encounters (level < 16)
                    entry(LOW_GOBLIN_PAIR, weight = 25) { maxLevel(16) }
                    entry(LOW_ZOMBIE, weight = 30) { maxLevel(16) }
                    entry(LOW_KOBOLD_TRIPLE, weight = 25) { maxLevel(16) }
                    entry(LOW_GOBLIN_KOBOLDS, weight = 20) { maxLevel(16) }

                    // High encounters (level >= 16)
                    entry(HIGH_GOBLIN_A, weight = 25) { minLevel(16) }
                    entry(HIGH_KOBOLD_TRIPLE, weight = 25) { minLevel(16) }
                    entry(HIGH_ZOMBIE_PAIR, weight = 25) { minLevel(16) }
                    entry(HIGH_KOBOLD_A, weight = 25) { minLevel(16) }
                }

                // -------------------------------------------------------------------------
                // Chests (MAP_A unless noted)
                // Original floor2.c:29-72
                //   CHEST_1 (16,5) MAP_A — 2x Potion
                //   CHEST_2 (18,5) MAP_A — 1x Remedy
                //   CHEST_3 (23,5) MAP_B — Magic Key
                //   CHEST_4 (25,5) MAP_B — Magic Key
                //   CHEST_5 (29,3) MAP_B — 1x Potion
                // -------------------------------------------------------------------------
                objects {
                    chest("chest1_potions2", x = 16, y = 5) {
                        usedFlag(chestFlags.chest1.name)
                        onOpen {
                            callOp("add_item", StringLiteral("potion"))
                            callOp("add_item", StringLiteral("potion"))
                        }
                    }
                    chest("chest2_remedy", x = 18, y = 5) {
                        usedFlag(chestFlags.chest2.name)
                        onOpen { callOp("add_item", StringLiteral("remedy")) }
                    }
                    // Chests 3-4 in sub-map B (portal area) — represented at exit portal coords
                    chest("chest3_magic_key_b", x = 23, y = 5) {
                        usedFlag(chestFlags.chest3.name)
                        onOpen { callOp("add_item", StringLiteral("magic_key")) }
                    }
                    chest("chest4_magic_key_b2", x = 25, y = 5) {
                        usedFlag(chestFlags.chest4.name)
                        onOpen { callOp("add_item", StringLiteral("magic_key")) }
                    }
                    chest("chest5_potion_b", x = 29, y = 3) {
                        usedFlag(chestFlags.chest5.name)
                        onOpen { callOp("add_item", StringLiteral("potion")) }
                    }

                    // -------------------------------------------------------------------------
                    // Signs
                    // Original floor2.c:121-133
                    //   MAP_A (17,4) UP — str_floor2_sign_items_room
                    //   MAP_A (10,19) UP — str_floor2_sign_levers
                    // -------------------------------------------------------------------------
                    sign("sign_items_room", x = 17, y = 4) {
                        onRead {
                            callOp("map_textbox", StringLiteral("str_floor2_sign_items_room"))
                        }
                    }
                    sign("sign_levers", x = 10, y = 19) {
                        onRead { callOp("map_textbox", StringLiteral("str_floor2_sign_levers")) }
                    }

                    // -------------------------------------------------------------------------
                    // Levers
                    // Original floor2.c:171-186
                    //   LEVER_1 (10,21) MAP_A — toggles DOOR_4..7
                    //   LEVER_2 (2,6) MAP_B — lights sconces 1+3; if LEVER_2+3 → opens DOOR_8
                    //   LEVER_3 (10,6) MAP_B — lights sconces 2+4; if LEVER_2+3 → opens DOOR_8
                    //   LEVER_4 (24,5) MAP_B — opens DOOR_1 (central access)
                    // -------------------------------------------------------------------------
                    lever("lever1_door_toggle", x = 10, y = 21) {
                        onActivate { callOp("map_textbox", StringLiteral("str_floor_door_toggle")) }
                    }
                    lever("lever2_sconce_left", x = 2, y = 6) {
                        onActivate { callOp("map_textbox", StringLiteral("str_floor_light_fire")) }
                    }
                    lever("lever3_sconce_right", x = 10, y = 6) {
                        onActivate { callOp("map_textbox", StringLiteral("str_floor_light_fire")) }
                    }
                    lever("lever4_door_open", x = 24, y = 5) {
                        onActivate { callOp("map_textbox", StringLiteral("str_floor2_door_opens")) }
                    }

                    // -------------------------------------------------------------------------
                    // NPCs
                    // Original floor2.c:319-334
                    //   NPC_1 (3,5) MAP_A — Bugbear B-Tier (elite encounter)
                    //   NPC_2 (10,3) MAP_A — Owlbear S-Tier (boss, level-gated >=17)
                    //   NPC_1 victory: grants ABILITY_1
                    //   NPC_2 victory: opens DOOR_9 (next level)
                    // -------------------------------------------------------------------------
                    npc("bugbear_elite", x = 3, y = 5) {
                        visibleFlag(worldFlags.floor2EliteDefeated.name, visibleWhenUnset = true)
                        onTalk {
                            callOp("map_textbox", StringLiteral("str_floor2_elite_msg"))
                            setFlag(worldFlags.floor2EliteDefeated.name)
                        }
                    }
                    npc("owlbear_boss", x = 10, y = 3) {
                        visibleFlag(worldFlags.floor2BossDefeated.name, visibleWhenUnset = true)
                        onTalk {
                            callOp("map_textbox", StringLiteral("str_floor2_boss_msg"))
                            setFlag(worldFlags.floor2BossDefeated.name)
                        }
                    }
                }

                // -------------------------------------------------------------------------
                // Zone transition to floor 3
                // Original floor2.c:112 — { MAP_A, 10, 1, MAP_A, 26, 17, UP, EXIT_STAIRS,
                // &bank_floor3 }
                // -------------------------------------------------------------------------
                transition {
                    to("floor3")
                    entryX(26)
                    entryY(17)
                    conditionFlag(worldFlags.floor2BossDefeated)
                }
            }
            ZoneRef("floor2")
        }
}
