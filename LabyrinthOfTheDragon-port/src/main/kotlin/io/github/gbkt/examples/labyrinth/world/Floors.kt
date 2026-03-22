/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress("LongMethod")

package io.github.gbkt.examples.labyrinth.world

import io.github.gbkt.core.dsl.FlagRef
import io.github.gbkt.core.dsl.GameBuilder
import io.github.gbkt.core.dsl.ZoneRef
import io.github.gbkt.examples.labyrinth.world.floors.Floor1Entrance
import io.github.gbkt.examples.labyrinth.world.floors.Floor2GoblinWarrens
import io.github.gbkt.examples.labyrinth.world.floors.Floor3CatacombsOfTheDead
import io.github.gbkt.examples.labyrinth.world.floors.Floor4ForgottenHalls
import io.github.gbkt.examples.labyrinth.world.floors.Floor5CrypticDepths
import io.github.gbkt.examples.labyrinth.world.floors.Floor6TwistingTunnels
import io.github.gbkt.examples.labyrinth.world.floors.Floor7AbyssalChambers
import io.github.gbkt.examples.labyrinth.world.floors.Floor8DragonLair

// =============================================================================
// FLOORS COORDINATOR — Labyrinth of the Dragon
// =============================================================================
//
// Coordinates all 8 dungeon floor definitions and provides typed flag references
// for chest state tracking and world progression. Each floor is registered
// individually via its object's register() function to keep files manageable.
//
// Original sources:
//   - floor1.c through floor8.c — individual floor definitions
//   - floor_common.c — shared callbacks (chest_add_magic_key, chest_add_torch)
//   - core.h — FlagPage enum: FLAGS_CHEST_OPEN(27), FLAGS_CHEST_LOCKED(29), etc.
//
// Chest flags: Original uses CHEST_1..CHEST_8 per floor as flags (stored per floor).
//   In the port, we use two flag pages (8 flags each for 16 total chests per page).
//   Floors 1-4 use chestFlags1to4; floors 5-8 use chestFlags5to8.
// =============================================================================

/**
 * Container for chest flag references for floors 1-4.
 *
 * Each chest flag tracks whether that chest has been opened (prevents re-opening). Maps to
 * Original's CHEST_1..CHEST_8 constants used as per-floor flags.
 */
data class ChestFlags1to4(
    val chest1: FlagRef,
    val chest2: FlagRef,
    val chest3: FlagRef,
    val chest4: FlagRef,
    val chest5: FlagRef,
    val chest6: FlagRef,
    val chest7: FlagRef,
    val chest8: FlagRef,
)

/**
 * Container for chest flag references for floors 5-8.
 *
 * Mirrors [ChestFlags1to4] for the upper dungeon floors.
 */
data class ChestFlags5to8(
    val chest1: FlagRef,
    val chest2: FlagRef,
    val chest3: FlagRef,
    val chest4: FlagRef,
    val chest5: FlagRef,
    val chest6: FlagRef,
    val chest7: FlagRef,
    val chest8: FlagRef,
)

/**
 * Container for world progression flag references.
 *
 * Tracks boss defeats, elite defeats, door states, and story progression across all 8 floors. Maps
 * to Original's world flag pages in core.h (FLAGS_WORLD groupings).
 *
 * Elite defeats grant class abilities (ABILITY_1..8) and are separate from boss defeats which
 * unlock the next-level staircase door. Each floor has both an elite NPC and a boss NPC (Original:
 * NPC_1 is boss, NPC_2 is elite on floors 2-8).
 */
data class WorldFlags(
    val floor1BossDefeated: FlagRef,
    val floor2BossDefeated: FlagRef,
    val floor2EliteDefeated: FlagRef,
    val floor3BossDefeated: FlagRef,
    val floor3EliteDefeated: FlagRef,
    val floor4BossDefeated: FlagRef,
    val floor4EliteDefeated: FlagRef,
    val floor5BossDefeated: FlagRef,
    val floor6BossDefeated: FlagRef,
    val floor7BossDefeated: FlagRef,
    val floor8DragonDefeated: FlagRef,
    val lever1F3Used: FlagRef,
)

/**
 * Returned by [GameBuilder.registerFloors] — provides typed zone refs and flag containers for all 8
 * dungeon floors.
 */
data class Floors(
    val floor1: ZoneRef,
    val floor2: ZoneRef,
    val floor3: ZoneRef,
    val floor4: ZoneRef,
    val floor5: ZoneRef,
    val floor6: ZoneRef,
    val floor7: ZoneRef,
    val floor8: ZoneRef,
    val chestFlags1to4: ChestFlags1to4,
    val chestFlags5to8: ChestFlags5to8,
    val worldFlags: WorldFlags,
)

/**
 * Registers all 8 dungeon floors and their flag definitions, returning typed [Floors] references.
 *
 * Must be called within a `game { }` builder block. The returned [Floors] object provides:
 * - Typed [ZoneRef] objects for each floor (used in `startZone()` and `transition { to(floor2) }`)
 * - Typed [ChestFlags1to4] and [ChestFlags5to8] for per-floor chest state tracking
 * - Typed [WorldFlags] for boss defeat and story progression tracking
 *
 * Original: floors defined in floor1.c through floor8.c; shared callbacks in floor_common.c
 */
fun GameBuilder.registerFloors(): Floors {

    // -------------------------------------------------------------------------
    // Flag page: chest state for floors 1-4
    // Original: CHEST_1..CHEST_8 constants (per-floor, reused across floors)
    // In port: flattened into two global pages of 8 flags each
    // -------------------------------------------------------------------------
    var chest1to4Flags: ChestFlags1to4? = null
    var chest5to8Flags: ChestFlags5to8? = null
    var worldFlagRefs: WorldFlags? = null

    flags("labyrinth_flags") {
        page("chests_1_4") {
            val c1 = flag("chest1_1to4")
            val c2 = flag("chest2_1to4")
            val c3 = flag("chest3_1to4")
            val c4 = flag("chest4_1to4")
            val c5 = flag("chest5_1to4")
            val c6 = flag("chest6_1to4")
            val c7 = flag("chest7_1to4")
            val c8 = flag("chest8_1to4")
            chest1to4Flags = ChestFlags1to4(c1, c2, c3, c4, c5, c6, c7, c8)
        }
        page("chests_5_8") {
            val c1 = flag("chest1_5to8")
            val c2 = flag("chest2_5to8")
            val c3 = flag("chest3_5to8")
            val c4 = flag("chest4_5to8")
            val c5 = flag("chest5_5to8")
            val c6 = flag("chest6_5to8")
            val c7 = flag("chest7_5to8")
            val c8 = flag("chest8_5to8")
            chest5to8Flags = ChestFlags5to8(c1, c2, c3, c4, c5, c6, c7, c8)
        }
        page("world") {
            val f1Boss = flag("floor1_boss_defeated")
            val f2Boss = flag("floor2_boss_defeated")
            val f2Elite = flag("floor2_elite_defeated")
            val f3Boss = flag("floor3_boss_defeated")
            val f3Elite = flag("floor3_elite_defeated")
            val f4Boss = flag("floor4_boss_defeated")
            val f4Elite = flag("floor4_elite_defeated")
            val f5Boss = flag("floor5_boss_defeated")
            val f6Boss = flag("floor6_boss_defeated")
            val f7Boss = flag("floor7_boss_defeated")
            val f8Dragon = flag("floor8_dragon_defeated")
            val lev1F3 = flag("lever1_f3_used")
            worldFlagRefs =
                WorldFlags(
                    floor1BossDefeated = f1Boss,
                    floor2BossDefeated = f2Boss,
                    floor2EliteDefeated = f2Elite,
                    floor3BossDefeated = f3Boss,
                    floor3EliteDefeated = f3Elite,
                    floor4BossDefeated = f4Boss,
                    floor4EliteDefeated = f4Elite,
                    floor5BossDefeated = f5Boss,
                    floor6BossDefeated = f6Boss,
                    floor7BossDefeated = f7Boss,
                    floor8DragonDefeated = f8Dragon,
                    lever1F3Used = lev1F3,
                )
        }
    }

    val chestFlags14 = checkNotNull(chest1to4Flags) { "chest flags 1-4 not initialized" }
    val chestFlags58 = checkNotNull(chest5to8Flags) { "chest flags 5-8 not initialized" }
    val worldFlags = checkNotNull(worldFlagRefs) { "world flags not initialized" }

    // -------------------------------------------------------------------------
    // Register all 8 floors
    // -------------------------------------------------------------------------
    val floor1Ref = Floor1Entrance.register(this, chestFlags14, worldFlags)
    val floor2Ref = Floor2GoblinWarrens.register(this, chestFlags14, worldFlags)
    val floor3Ref = Floor3CatacombsOfTheDead.register(this, chestFlags14, worldFlags)
    val floor4Ref = Floor4ForgottenHalls.register(this, chestFlags14, worldFlags)
    val floor5Ref = Floor5CrypticDepths.register(this, chestFlags58, worldFlags)
    val floor6Ref = Floor6TwistingTunnels.register(this, chestFlags58, worldFlags)
    val floor7Ref = Floor7AbyssalChambers.register(this, chestFlags58, worldFlags)
    val floor8Ref = Floor8DragonLair.register(this, chestFlags58, worldFlags)

    return Floors(
        floor1 = floor1Ref,
        floor2 = floor2Ref,
        floor3 = floor3Ref,
        floor4 = floor4Ref,
        floor5 = floor5Ref,
        floor6 = floor6Ref,
        floor7 = floor7Ref,
        floor8 = floor8Ref,
        chestFlags1to4 = chestFlags14,
        chestFlags5to8 = chestFlags58,
        worldFlags = worldFlags,
    )
}
