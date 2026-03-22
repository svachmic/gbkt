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
// FLOOR 8 — DRAGON'S LAIR
// =============================================================================
//
// The final dungeon floor. NO random encounters — only direct boss encounters.
// A gauntlet of 7 mini-bosses (one per creature tile in the main corridor)
// must all be defeated to open DOOR_1 (boss room antechamber door at (8,9)).
// Six healing mirrors scattered on the floor can each be used once.
// Dragon boss (NPC_1) is the final battle: encounter.is_final_boss=true.
// Beholder elite (NPC_2) is the last challenge before the dragon.
//
// Original source: LabyrinthOfTheDragon/src/floor8.c (bank 8)
//   - DEFAULT_X=8, DEFAULT_Y=29
//   - MAP_A: floor_eight_data (32x32, BANK_16)
//   - 8 chests: scattered through the gauntlet corridor and antechamber
//   - NO random encounters (on_move returns false)
//   - 7 mini-boss tile triggers at specific tiles in the corridor
//   - 6 healing mirrors at specific tiles (one-time use per session)
//   - NPC_1 at (8,3): Dragon S-tier — final boss (is_final_boss=true)
//   - NPC_2 at (8,11): Beholder A-tier — pre-boss mini-boss elite
//   - DOOR_1 at (8,9) opens when all 7 mini-bosses defeated
//   - DOOR_2 at (8,1) — next level (triggers victory screen)
// =============================================================================

/**
 * Dungeon Floor 8 — Dragon's Lair.
 *
 * Ported from `floor8.c`. Final floor — no random encounters, only mandatory boss fights. The
 * player ascends through a gauntlet of 7 mini-bosses (one of each creature type), can use up to 6
 * healing mirrors, then faces the Beholder elite and the Dragon boss.
 *
 * ## Game Flow
 * Entry (8,29) → mini-boss gauntlet tiles → all 7 defeated → DOOR_1 opens (8,9) → Beholder elite
 * (8,11) → DOOR_1 antechamber (8,9→8,6) → Dragon boss (8,3) → VICTORY
 *
 * ## Chests (floor8.c:148-218)
 * - CHEST_1 (2,26) MAP_A: 3x Potion
 * - CHEST_2 (14,26) MAP_A: 3x Ether
 * - CHEST_3 (3,21) MAP_A: 1x ATK Up + 1x DEF Up
 * - CHEST_4 (13,21) MAP_A: 3x Haste
 * - CHEST_5 (4,16) MAP_A: 3x Regen
 * - CHEST_6 (12,16) MAP_A: 3x Elixir
 * - CHEST_7 (20,9) MAP_A: 3x Elixir (antechamber)
 * - CHEST_8 (22,9) MAP_A: 1x Potion (antechamber)
 *
 * ## Mini-Boss Tiles (floor8.c:397-443, on_special callback)
 * Tile triggers (player walks onto tile → triggers one-time mini-boss battle):
 * - (2,27): Goblin A-tier lv45
 * - (14,27): Owlbear A-tier lv45
 * - (3,22): Gelatinous Cube A-tier lv45
 * - (13,22): Displacer Beast A-tier lv45
 * - (4,17): Deathknight A-tier lv45
 * - (12,17): Mindflayer A-tier lv45
 * - Beholder A-tier lv45 — triggered by NPC_2 (not a tile trigger) All 7 defeated → opens DOOR_1
 *
 * ## Healing Mirrors (floor8.c:405-427, on_action callback)
 * Player faces UP at mirror tile → full heal (one use per mirror per session):
 * - MIRROR_1 (5,27), MIRROR_2 (11,27), MIRROR_3 (5,22), MIRROR_4 (11,22)
 * - MIRROR_5 (19,10), MIRROR_6 (23,10)
 *
 * Original reference: `LabyrinthOfTheDragon/src/floor8.c`
 */
object Floor8DragonLair {

    /**
     * Registers Floor 8 in the game builder and returns a typed [ZoneRef].
     *
     * Original: floor8.c — `const Floor floor8 = { ... }`
     *
     * @param chestFlags Chest open-state flags for floors 5-8
     * @param worldFlags World progression flags (floor8DragonDefeated triggers victory)
     */
    fun register(
        builder: GameBuilder,
        chestFlags: ChestFlags5to8,
        worldFlags: WorldFlags,
    ): ZoneRef =
        builder.run {
            zone("floor8") {
                name("Dungeon Level 8 - Dragon's Lair")
                // Original: floor_eight_data (BANK_16), 32x32 — floor8.c:138-142
                tileset("tilemaps/floors/floor8.tilemap")
                size(32, 32)

                // -------------------------------------------------------------------------
                // NO random encounters on floor 8
                // Original floor8.c:446-448 — on_move returns false (no random encounter check)
                // -------------------------------------------------------------------------
                safeZone()

                objects {
                    // -------------------------------------------------------------------------
                    // Chests — floor8.c:148-218
                    // All chests are open (locked=false, magicKeyOk=false)
                    // Laid out in pairs at each gauntlet level
                    // -------------------------------------------------------------------------

                    // Corridor entrance chests — floor8.c:161-176
                    chest("chest1_potions3", x = 2, y = 26) {
                        usedFlag(chestFlags.chest1.name)
                        onOpen { callOp("add_item", StringLiteral("potion_x3")) }
                    }
                    chest("chest2_ethers3", x = 14, y = 26) {
                        usedFlag(chestFlags.chest2.name)
                        onOpen { callOp("add_item", StringLiteral("ether_x3")) }
                    }

                    // Mid-corridor chests — floor8.c:177-193
                    chest("chest3_atkup_defup", x = 3, y = 21) {
                        usedFlag(chestFlags.chest3.name)
                        onOpen { callOp("add_item", StringLiteral("atk_up_def_up")) }
                    }
                    chest("chest4_haste3", x = 13, y = 21) {
                        usedFlag(chestFlags.chest4.name)
                        onOpen { callOp("add_item", StringLiteral("haste_x3")) }
                    }

                    // Upper-corridor chests — floor8.c:194-210
                    chest("chest5_regen3", x = 4, y = 16) {
                        usedFlag(chestFlags.chest5.name)
                        onOpen { callOp("add_item", StringLiteral("regen_x3")) }
                    }
                    chest("chest6_elixirs3", x = 12, y = 16) {
                        usedFlag(chestFlags.chest6.name)
                        onOpen { callOp("add_item", StringLiteral("elixir_x3")) }
                    }

                    // Antechamber chests — floor8.c:204-218
                    chest("chest7_antechamber_elixirs", x = 20, y = 9) {
                        usedFlag(chestFlags.chest7.name)
                        onOpen { callOp("add_item", StringLiteral("elixir_x3")) }
                    }
                    chest("chest8_antechamber_potion", x = 22, y = 9) {
                        usedFlag(chestFlags.chest8.name)
                        onOpen { callOp("add_item", StringLiteral("potion")) }
                    }

                    // -------------------------------------------------------------------------
                    // Signs — floor8.c:244-255
                    // No signs on floor 8 (array is { END } in Original)
                    // -------------------------------------------------------------------------

                    // -------------------------------------------------------------------------
                    // Healing mirrors (implemented as interactive sconces)
                    // Original floor8.c:405-427 — on_action callback checks player facing UP
                    // Each mirror: full heal once per session, then shows "no power" message
                    // Positions: (5,27), (11,27), (5,22), (11,22), (19,10), (23,10)
                    // -------------------------------------------------------------------------
                    sconce("mirror1_entrance_left", x = 5, y = 27) {
                        usedFlag("mirror1_f8_used")
                        onInteract { callOp("use_healing_mirror_f8", StringLiteral("mirror1")) }
                    }
                    sconce("mirror2_entrance_right", x = 11, y = 27) {
                        usedFlag("mirror2_f8_used")
                        onInteract { callOp("use_healing_mirror_f8", StringLiteral("mirror2")) }
                    }
                    sconce("mirror3_mid_left", x = 5, y = 22) {
                        usedFlag("mirror3_f8_used")
                        onInteract { callOp("use_healing_mirror_f8", StringLiteral("mirror3")) }
                    }
                    sconce("mirror4_mid_right", x = 11, y = 22) {
                        usedFlag("mirror4_f8_used")
                        onInteract { callOp("use_healing_mirror_f8", StringLiteral("mirror4")) }
                    }
                    sconce("mirror5_upper_left", x = 19, y = 10) {
                        usedFlag("mirror5_f8_used")
                        onInteract { callOp("use_healing_mirror_f8", StringLiteral("mirror5")) }
                    }
                    sconce("mirror6_upper_right", x = 23, y = 10) {
                        usedFlag("mirror6_f8_used")
                        onInteract { callOp("use_healing_mirror_f8", StringLiteral("mirror6")) }
                    }

                    // -------------------------------------------------------------------------
                    // Static decorative sconces — floor8.c:299-327
                    //   (6,26)R, (10,26)R — corridor level 1
                    //   (6,21)G, (10,21)G — corridor level 2
                    //   (6,16)B, (10,16)B — corridor level 3
                    //   (4,10)R, (12,10)R, (21,9)R — antechamber
                    //   (7,1)R, (9,1)R — boss room
                    // -------------------------------------------------------------------------
                    sconce("sconce_static_corridor1_l", x = 6, y = 26)
                    sconce("sconce_static_corridor1_r", x = 10, y = 26)
                    sconce("sconce_static_corridor2_l", x = 6, y = 21)
                    sconce("sconce_static_corridor2_r", x = 10, y = 21)
                    sconce("sconce_static_corridor3_l", x = 6, y = 16)
                    sconce("sconce_static_corridor3_r", x = 10, y = 16)
                    sconce("sconce_static_ante_l", x = 4, y = 10)
                    sconce("sconce_static_ante_r", x = 12, y = 10)
                    sconce("sconce_static_ante_mid", x = 21, y = 9)
                    sconce("sconce_static_boss_l", x = 7, y = 1)
                    sconce("sconce_static_boss_r", x = 9, y = 1)

                    // -------------------------------------------------------------------------
                    // Mini-boss tile triggers (implemented as NPCs at gauntlet positions)
                    // Original floor8.c:397-443 — on_special: check_mini_boss_tile(b, x, y)
                    // Player walks onto tile → if not already defeated → starts mini-boss battle
                    //
                    // All mini-bosses are A-Tier at level 45, same stats, different monster type.
                    // On victory: tiles changes to 0x36 (skull), defeated flag set.
                    // All 7 defeated → DOOR_1 opens.
                    //
                    // Mini-boss positions (gauntlet corridor):
                    //   (2,27): Goblin A-tier   | (14,27): Owlbear A-tier
                    //   (3,22): G.Cube A-tier   | (13,22): Displacer Beast A-tier
                    //   (4,17): Deathknight A-t | (12,17): Mindflayer A-tier
                    //   Beholder: via NPC_2 (not tile-triggered)
                    //
                    // Port approach: NPC objects at gauntlet positions represent the mini-bosses.
                    // The callOp("start_mini_boss_f8", ...) maps to the generated battle setup.
                    // -------------------------------------------------------------------------
                    npc("miniboss_goblin", x = 2, y = 27) {
                        usedFlag("miniboss_goblin_f8")
                        onTalk { callOp("start_mini_boss_f8", StringLiteral("goblin")) }
                    }
                    npc("miniboss_owlbear", x = 14, y = 27) {
                        usedFlag("miniboss_owlbear_f8")
                        onTalk { callOp("start_mini_boss_f8", StringLiteral("owlbear")) }
                    }
                    npc("miniboss_gcube", x = 3, y = 22) {
                        usedFlag("miniboss_gcube_f8")
                        onTalk { callOp("start_mini_boss_f8", StringLiteral("gcube")) }
                    }
                    npc("miniboss_displacer", x = 13, y = 22) {
                        usedFlag("miniboss_displacer_f8")
                        onTalk { callOp("start_mini_boss_f8", StringLiteral("displacer")) }
                    }
                    npc("miniboss_deathknight", x = 4, y = 17) {
                        usedFlag("miniboss_deathknight_f8")
                        onTalk { callOp("start_mini_boss_f8", StringLiteral("deathknight")) }
                    }
                    npc("miniboss_mindflayer", x = 12, y = 17) {
                        usedFlag("miniboss_mindflayer_f8")
                        onTalk { callOp("start_mini_boss_f8", StringLiteral("mindflayer")) }
                    }

                    // -------------------------------------------------------------------------
                    // NPC encounters — floor8.c:372-386
                    //
                    // NPC_2 at (8,11): Beholder A-tier lv55 — 7th mini-boss (via NPC, not tile)
                    //   Original: on_elite_victory → calls on_mini_boss_victory(MINI_BOSS_BEHOLDER)
                    //   This is the gatekeeper before the dragon boss room
                    //
                    // NPC_1 at (8,3): Dragon S-tier lv60 — FINAL BOSS (is_final_boss=true)
                    //   No level gate — fight begins immediately when player talks to NPC
                    //   On dragon victory: encounter.is_final_boss=true triggers victory sequence
                    // -------------------------------------------------------------------------
                    npc("beholder_elite", x = 8, y = 11) {
                        // Beholder guards the dragon boss room — floor8.c:384
                        visibleFlag("beholder_elite_f8_defeated", visibleWhenUnset = true)
                        onTalk {
                            // floor8.c:359-369 — on_npc_action for NPC_2
                            callOp("map_textbox", StringLiteral("str_floor8_elite"))
                            callOp("start_beholder_elite_f8")
                        }
                    }
                    npc("dragon_boss", x = 8, y = 3) {
                        // Dragon is always visible (no visibility flag) — floor8.c:383
                        // is_final_boss=true in Original: encounter.is_final_boss = true
                        onTalk {
                            // floor8.c:352-355 — on_npc_action for NPC_1
                            // Dragon encounter triggers final boss sequence + victory on defeat
                            callOp("map_textbox", StringLiteral("str_floor8_boss"))
                            setFlag(worldFlags.floor8DragonDefeated)
                            callOp("start_dragon_final_boss_f8")
                        }
                    }
                }

                // -------------------------------------------------------------------------
                // Zone transition to victory
                // Original floor8.c:235 — { MAP_A, 8, 1, DOOR_NEXT_LEVEL }
                // DOOR_2 at (8,1) — opened after defeating the dragon
                // In the port, defeating the dragon sets floor8DragonDefeated flag
                // which the victory scene checks to navigate to the victory screen.
                //
                // No cross-floor transition needed — dragon defeat triggers victory directly.
                // -------------------------------------------------------------------------
                transition {
                    to("victory")
                    entryX(0)
                    entryY(0)
                    conditionFlag(worldFlags.floor8DragonDefeated)
                }
            }
            ZoneRef("floor8")
        }
}
