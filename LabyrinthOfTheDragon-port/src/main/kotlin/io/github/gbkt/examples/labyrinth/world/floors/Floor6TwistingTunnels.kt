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
// FLOOR 6 — TWISTING TUNNELS
// =============================================================================
//
// Sixth dungeon floor. Hard encounters (will-o-wisp, gelatinous cube, owlbear,
// displacer beast, bugbear, goblin, kobold). Two-sub-map layout with portal routing.
// Two routing levers select one of 4 active portals. Two one-time levers open
// elite and item room doors; both open → boss door opens.
// Boss: Mindflayer S-tier lv45. Elite: Will-o'-Wisp A-tier lv43.
//
// Original source: LabyrinthOfTheDragon/src/floor6.c
//   - DEFAULT_X=8, DEFAULT_Y=7
//   - MAP_A: floor_six_a (32x32, BANK_17)
//   - MAP_B: floor_six_b (16x8) — boss + elite rooms
//   - 8 chests: 3 puzzle floor + 3 treasure room (locked) + 2 secret boss room
//   - 4 levers: LEVER_1+2 routing, LEVER_3+4 one-time door opens
//   - NPC_1 at MAP_B (3,4): Mindflayer S-tier, NPC_2 at MAP_A (23,4): Will-o-Wisp
// =============================================================================

/**
 * Dungeon Floor 6 — Twisting Tunnels.
 *
 * Ported from `floor6.c`. Two-sub-map layout with four-portal routing puzzle driven by two toggle
 * levers. Two one-time levers unlock elite/item rooms; when both unlocked, the boss room door
 * opens.
 *
 * ## Encounters
 * - encounters_low (player.level < 43): wisp 10%, 2x g.cube 20%, owlbear B 35%, 2x bugbear 35%
 * - encounters_high (>=level 43): g.cube C 25%, 2x owlbear B 30%, displacer C 30%, 3x combo 15%
 *
 * Original reference: `LabyrinthOfTheDragon/src/floor6.c`
 */
object Floor6TwistingTunnels {

    // Encounter group IDs — map to CombatSystem encounter configurations
    private const val LOW_WISP_C = "floor6_low_wisp_c"
    private const val LOW_GCUBE_PAIR = "floor6_low_gcube_pair"
    private const val LOW_OWLBEAR_B = "floor6_low_owlbear_b"
    private const val LOW_BUGBEAR_PAIR = "floor6_low_bugbear_pair"
    private const val HIGH_GCUBE_C = "floor6_high_gcube_c"
    private const val HIGH_OWLBEAR_PAIR = "floor6_high_owlbear_pair"
    private const val HIGH_DISPLACER_C = "floor6_high_displacer_c"
    private const val HIGH_GOBLIN_KOBOLD_TRIPLE = "floor6_high_goblin_kobold_triple"

    /**
     * Registers Floor 6 in the game builder and returns a typed [ZoneRef].
     *
     * Original: floor6.c — `const Floor floor6 = { ... }`
     *
     * @param chestFlags Chest open-state flags for floors 5-8
     * @param worldFlags World progression flags (floor6BossDefeated gates exit to floor 7)
     */
    fun register(
        builder: GameBuilder,
        chestFlags: ChestFlags5to8,
        worldFlags: WorldFlags,
    ): ZoneRef =
        builder.run {
            zone("floor6") {
                name("Dungeon Level 6 - Twisting Tunnels")
                // Original: floor_six_a (BANK_17), 32x32 — floor6.c:19-23
                tileset("tilemaps/floors/floor6.tilemap")
                size(32, 32)

                // -------------------------------------------------------------------------
                // Encounter tables — floor6.c:380-423
                //
                // encounters_low (player.level < 43):
                //   10% Will-o-Wisp C lv39, 20% 2x G.Cube C lv41,
                //   35% Owlbear B lv42, 35% 2x Bugbear C lv41
                //
                // encounters_high (player.level >= 43):
                //   25% G.Cube C lv45, 30% 2x Owlbear B lv43,
                //   30% Displacer Beast C lv45, 15% 3x Goblin+Kobold+Goblin
                // -------------------------------------------------------------------------
                encounters {
                    safeSteps(4)

                    entry(LOW_WISP_C, weight = 10) { maxLevel(43) }
                    entry(LOW_GCUBE_PAIR, weight = 20) { maxLevel(43) }
                    entry(LOW_OWLBEAR_B, weight = 35) { maxLevel(43) }
                    entry(LOW_BUGBEAR_PAIR, weight = 35) { maxLevel(43) }

                    entry(HIGH_GCUBE_C, weight = 25) { minLevel(43) }
                    entry(HIGH_OWLBEAR_PAIR, weight = 30) { minLevel(43) }
                    entry(HIGH_DISPLACER_C, weight = 30) { minLevel(43) }
                    entry(HIGH_GOBLIN_KOBOLD_TRIPLE, weight = 15) { minLevel(43) }
                }

                objects {
                    // -------------------------------------------------------------------------
                    // Chests — floor6.c:29-57
                    // -------------------------------------------------------------------------
                    chest("chest1_elixir", x = 12, y = 16) {
                        usedFlag(chestFlags.chest1.name)
                        onOpen { callOp("add_item", StringLiteral("elixir")) }
                    }
                    chest("chest2_magic_key", x = 7, y = 25) {
                        usedFlag(chestFlags.chest2.name)
                        onOpen { callOp("add_item", StringLiteral("magic_key")) }
                    }
                    chest("chest3_magic_key_b", x = 20, y = 25) {
                        usedFlag(chestFlags.chest3.name)
                        onOpen { callOp("add_item", StringLiteral("magic_key")) }
                    }
                    // Treasure Room (locked, magic key) — floor6.c:48-50
                    chest("chest4_potions3", x = 26, y = 4) {
                        usedFlag(chestFlags.chest4.name)
                        onOpen { callOp("add_item", StringLiteral("potion_x3")) }
                    }
                    chest("chest5_regen3", x = 27, y = 4) {
                        usedFlag(chestFlags.chest5.name)
                        onOpen { callOp("add_item", StringLiteral("regen_x3")) }
                    }
                    chest("chest6_ethers3", x = 28, y = 4) {
                        usedFlag(chestFlags.chest6.name)
                        onOpen { callOp("add_item", StringLiteral("ether_x3")) }
                    }
                    // Secret Boss Room chests (MAP_B) — floor6.c:53-54
                    chest("chest7_boss_potion", x = 11, y = 3) {
                        usedFlag(chestFlags.chest7.name)
                        onOpen { callOp("add_item", StringLiteral("potion")) }
                    }
                    chest("chest8_boss_ether", x = 13, y = 3) {
                        usedFlag(chestFlags.chest8.name)
                        onOpen { callOp("add_item", StringLiteral("ether")) }
                    }

                    // -------------------------------------------------------------------------
                    // Levers — floor6.c:186-203
                    // LEVER_1+2: portal routing toggle (binary combination selects portal)
                    // LEVER_3: one-time — opens DOOR_3 (elite room)
                    // LEVER_4: one-time — opens DOOR_4 (item room)
                    // Both DOOR_3+DOOR_4 open → opens DOOR_1 (boss room)
                    // -------------------------------------------------------------------------
                    lever("lever1_portal_routing_a", x = 7, y = 4) {
                        onActivate { callOp("update_portal_routing_f6") }
                        onDeactivate { callOp("update_portal_routing_f6") }
                    }
                    lever("lever2_portal_routing_b", x = 9, y = 4) {
                        onActivate { callOp("update_portal_routing_f6") }
                        onDeactivate { callOp("update_portal_routing_f6") }
                    }
                    lever("lever3_elite_room_door", x = 3, y = 15) {
                        usedFlag("lever3_f6_used")
                        onActivate { callOp("open_elite_room_f6") }
                    }
                    lever("lever4_item_room_door", x = 12, y = 24) {
                        usedFlag("lever4_f6_used")
                        onActivate { callOp("open_item_room_f6") }
                    }

                    // -------------------------------------------------------------------------
                    // Sconces — floor6.c:238-261 (all static, blue)
                    // -------------------------------------------------------------------------
                    sconce("sconce_static_1", x = 0, y = 19)
                    sconce("sconce_static_2", x = 5, y = 12)
                    sconce("sconce_static_3", x = 14, y = 19)
                    sconce("sconce_static_4", x = 8, y = 21)
                    sconce("sconce_static_5", x = 19, y = 25)
                    sconce("sconce_static_6", x = 22, y = 17)
                    sconce("sconce_static_7", x = 24, y = 3)
                    sconce("sconce_static_8", x = 28, y = 8)
                    sconce("sconce_static_9", x = 4, y = 5)
                    sconce("sconce_static_10", x = 12, y = 5)
                    sconce("sconce_static_boss_1", x = 2, y = 2)
                    sconce("sconce_static_boss_2", x = 4, y = 2)

                    // -------------------------------------------------------------------------
                    // NPCs — floor6.c:318-332
                    //
                    // NPC_1 at MAP_B (3,4): Mindflayer S-tier — boss (level-gated >=45)
                    //   Victory: opens DOOR_2 (next level door MAP_B (3,2))
                    //
                    // NPC_2 at MAP_A (23,4): Will-o-Wisp A-tier — elite
                    //   Victory: grants ABILITY_5
                    // -------------------------------------------------------------------------
                    npc("mindflayer_boss", x = 3, y = 4) {
                        visibleFlag(worldFlags.floor6BossDefeated.name, visibleWhenUnset = true)
                        onTalk {
                            callOp("map_textbox", StringLiteral("str_floor6_boss"))
                            setFlag(worldFlags.floor6BossDefeated)
                        }
                    }
                    npc("wisp_elite", x = 23, y = 4) {
                        usedFlag("npc_wisp_elite_f6")
                        onTalk { callOp("map_textbox", StringLiteral("str_floor6_elite_attack")) }
                    }
                }

                // -------------------------------------------------------------------------
                // Zone transition to floor 7
                // Original floor6.c:104 — { MAP_B, 3, 2, MAP_A, 8, 30, UP, EXIT_STAIRS,
                // &bank_floor7 }
                // -------------------------------------------------------------------------
                transition {
                    to("floor7")
                    entryX(8)
                    entryY(30)
                    conditionFlag(worldFlags.floor6BossDefeated)
                }
            }
            ZoneRef("floor6")
        }
}
