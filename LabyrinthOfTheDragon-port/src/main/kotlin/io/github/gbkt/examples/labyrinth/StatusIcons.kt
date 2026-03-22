/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth

import io.github.gbkt.core.dsl.GameBuilder

/**
 * Labyrinth of the Dragon — status effect icon sprite definitions.
 *
 * Declares the 16 entity sprite slots used to display active status effects during battle. Four
 * icon slots are allocated per combatant: one for the player and three for monster slots.
 *
 * ## Original C Reference
 *
 * Status icon sprites use the shared OAM sprite slots. Tile indices 0x60–0x72 are reserved in VRAM
 * for status effect icon tiles (buff = green icon, debuff = red icon). Palette assignment matches
 * the Palettes.kt palette indices:
 * - Palette 6 ([Palettes.battleBuff]) — buff status indicators (ATK Up, DEF Up, Haste, Regen)
 * - Palette 7 ([Palettes.battleDebuff]) — debuff indicators (Poison, Bleed, Silence, Paralyze)
 *
 * ## Layout
 *
 * ```
 * Combatant   | Icon slots | VRAM tile offset range
 * ------------|------------|----------------------
 * Player      | 1-4        | 0x60 – 0x63
 * Monster 1   | 1-4        | 0x64 – 0x67
 * Monster 2   | 1-4        | 0x68 – 0x6B
 * Monster 3   | 1-4        | 0x6C – 0x6F
 * ```
 *
 * ## Screen Positions
 *
 * Icons are positioned along the top edge of the battle screen, above each combatant's HP/SP bar.
 * Each icon is 8x8 pixels:
 * - Player icons: x = 8, 16, 24, 32 ; y = 8
 * - Monster 1 icons: x = 88, 96, 104, 112 ; y = 8
 * - Monster 2 icons: x = 88, 96, 104, 112 ; y = 56
 * - Monster 3 icons: x = 88, 96, 104, 112 ; y = 104
 */
object StatusIcons {

    // =========================================================================
    // Icon metadata constants
    // =========================================================================

    /**
     * VRAM tile base offset for status icon tiles.
     *
     * @source core.h — `#define STATUS_ICON_TILE_BASE 0x60`
     */
    const val TILE_BASE = 0x60

    /**
     * Number of status icon slots per combatant.
     *
     * @source battle.h — 4 simultaneous status effects per combatant (max displayable)
     */
    const val ICONS_PER_COMBATANT = 4

    /**
     * Total number of status icon sprites (4 combatants x 4 icons each).
     *
     * @source battle.h — 16 OAM slots reserved for status icons
     */
    const val TOTAL_ICONS = 16

    /**
     * Palette index for buff status indicators (positive effects). References [Palettes.battleBuff]
     * at sprite palette slot 6.
     *
     * @source battle.c — buff sprites use sprite palette 6
     */
    const val BUFF_PALETTE_INDEX = 6

    /**
     * Palette index for debuff status indicators (negative effects). References
     * [Palettes.battleDebuff] at sprite palette slot 7.
     *
     * @source battle.c — debuff sprites use sprite palette 7
     */
    const val DEBUFF_PALETTE_INDEX = 7

    // =========================================================================
    // Screen position constants for icon placement
    // =========================================================================

    /** X position of player status icon slot 1 (first icon). */
    const val PLAYER_ICON_X = 8

    /** Y position of player status icons. */
    const val PLAYER_ICON_Y = 8

    /** X position of monster status icon slot 1 (first icon per monster). */
    const val MONSTER_ICON_X = 88

    /** Y position of monster 1 status icons. */
    const val MONSTER1_ICON_Y = 8

    /** Y position of monster 2 status icons. */
    const val MONSTER2_ICON_Y = 56

    /** Y position of monster 3 status icons. */
    const val MONSTER3_ICON_Y = 104

    /** Pixel spacing between consecutive icon sprites (8px = 1 tile). */
    const val ICON_SPACING = 8

    // =========================================================================
    // OAM sprite slot assignments
    // =========================================================================

    // Player icon sprite slots (OAM indices 0-3)

    /** Player status icon slot 1 — OAM sprite index 0, tile 0x60. */
    const val PLAYER_ICON1_SPRITE = 0

    /** Player status icon slot 2 — OAM sprite index 1, tile 0x61. */
    const val PLAYER_ICON2_SPRITE = 1

    /** Player status icon slot 3 — OAM sprite index 2, tile 0x62. */
    const val PLAYER_ICON3_SPRITE = 2

    /** Player status icon slot 4 — OAM sprite index 3, tile 0x63. */
    const val PLAYER_ICON4_SPRITE = 3

    // Monster 1 icon sprite slots (OAM indices 4-7)

    /** Monster 1 status icon slot 1 — OAM sprite index 4, tile 0x64. */
    const val MONSTER1_ICON1_SPRITE = 4

    /** Monster 1 status icon slot 2 — OAM sprite index 5, tile 0x65. */
    const val MONSTER1_ICON2_SPRITE = 5

    /** Monster 1 status icon slot 3 — OAM sprite index 6, tile 0x66. */
    const val MONSTER1_ICON3_SPRITE = 6

    /** Monster 1 status icon slot 4 — OAM sprite index 7, tile 0x67. */
    const val MONSTER1_ICON4_SPRITE = 7

    // Monster 2 icon sprite slots (OAM indices 8-11)

    /** Monster 2 status icon slot 1 — OAM sprite index 8, tile 0x68. */
    const val MONSTER2_ICON1_SPRITE = 8

    /** Monster 2 status icon slot 2 — OAM sprite index 9, tile 0x69. */
    const val MONSTER2_ICON2_SPRITE = 9

    /** Monster 2 status icon slot 3 — OAM sprite index 10, tile 0x6A. */
    const val MONSTER2_ICON3_SPRITE = 10

    /** Monster 2 status icon slot 4 — OAM sprite index 11, tile 0x6B. */
    const val MONSTER2_ICON4_SPRITE = 11

    // Monster 3 icon sprite slots (OAM indices 12-15)

    /** Monster 3 status icon slot 1 — OAM sprite index 12, tile 0x6C. */
    const val MONSTER3_ICON1_SPRITE = 12

    /** Monster 3 status icon slot 2 — OAM sprite index 13, tile 0x6D. */
    const val MONSTER3_ICON2_SPRITE = 13

    /** Monster 3 status icon slot 3 — OAM sprite index 14, tile 0x6E. */
    const val MONSTER3_ICON3_SPRITE = 14

    /** Monster 3 status icon slot 4 — OAM sprite index 15, tile 0x6F. */
    const val MONSTER3_ICON4_SPRITE = 15

    // =========================================================================
    // Registration helper
    // =========================================================================

    /**
     * Registers the status icon system into the [GameBuilder] scope.
     *
     * Called inside the `game { }` DSL block. The 16 icon sprites are defined as OAM sprite slot
     * constants rather than actor DSL definitions — they are directly manipulated by the battle
     * system via VRAM/OAM writes.
     *
     * Returns this [StatusIcons] object for caller convenience.
     *
     * @source battle.c — `update_status_icons()` writes to OAM sprite slots 0-15 each battle frame
     */
    fun register(@Suppress("UNUSED_PARAMETER") builder: GameBuilder): StatusIcons = this
}
