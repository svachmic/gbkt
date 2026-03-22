/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth

/**
 * Gameplay constants ported from the original Labyrinth of the Dragon C implementation.
 *
 * Every constant here cross-references its origin in the original source. This serves as the
 * definitive reference for all hard-coded values used throughout the gbkt V2 port.
 *
 * Original source files referenced:
 * - `LabyrinthOfTheDragon/src/core.h` — display, VRAM, tile, and font constants
 * - `LabyrinthOfTheDragon/src/player.h` — player and ability constants
 * - `LabyrinthOfTheDragon/src/map.h` — map layout, object counts, and HUD constants
 * - `LabyrinthOfTheDragon/src/monster.h` — monster tile dimensions
 */
object GameConfig {

    // -------------------------------------------------------------------------
    // Tile and display constants
    // @source core.h (implicit via GBDK), map.h
    // -------------------------------------------------------------------------

    /**
     * Width (and height) of a single tile in pixels.
     *
     * Game Boy tiles are always 8x8. This constant documents that assumption explicitly.
     *
     * @source Implicit in all GBDK tile addressing; confirmed by map movement logic in map.h.
     */
    const val TILE_SIZE = 8

    /**
     * Width of a full dungeon map in tiles.
     *
     * Maps are 32x32 in the original. Used for boundary checks and scroll math.
     *
     * @source map.h — Map struct width/height fields and scroll system
     */
    const val MAP_WIDTH = 32

    /**
     * Height of a full dungeon map in tiles.
     *
     * @source map.h — Map struct width/height fields
     */
    const val MAP_HEIGHT = 32

    // -------------------------------------------------------------------------
    // Floor and class counts
    // @source player.h, floor*.c
    // -------------------------------------------------------------------------

    /**
     * Total number of dungeon floors (levels) in the game.
     *
     * Eight floors progress from the entrance to the dragon's lair.
     *
     * @source floor1.c through floor8.c — eight floor source files confirm the count
     */
    const val FLOOR_COUNT = 8

    /**
     * Number of playable character classes.
     *
     * Classes: Druid (0), Fighter (1), Monk (2), Sorcerer (3).
     *
     * @source player.h — `typedef enum PlayerClass { CLASS_DRUID=0, CLASS_FIGHTER=1, CLASS_MONK=2,
     *   CLASS_SORCERER=3 }`
     */
    const val CLASS_COUNT = 4

    // -------------------------------------------------------------------------
    // Font / VRAM tile offsets
    // @source core.h
    // -------------------------------------------------------------------------

    /**
     * Tile index offset where the font tileset begins in VRAM.
     *
     * The font occupies the shared BG/sprite tile range starting at 0x80. Used when drawing text to
     * the window or background layer.
     *
     * @source core.h — `#define FONT_OFFSET 0x80`
     */
    const val FONT_OFFSET = 0x80

    /**
     * Tile id for the '0' character when the font tileset is loaded.
     *
     * Subsequent digits are consecutive: `FONT_DIGIT_OFFSET + digit` gives the tile id.
     *
     * @source core.h — `#define FONT_DIGIT_OFFSET 0xB0`
     */
    const val FONT_DIGIT_OFFSET = 0xB0

    /**
     * Tile id for a space character when the font tileset is loaded.
     *
     * @source core.h — `#define FONT_SPACE 0xA0`
     */
    const val FONT_SPACE = 0xA0

    /**
     * Tile id for a '/' character when the font tileset is loaded.
     *
     * Used for HP/SP fraction displays (e.g. "25/50").
     *
     * @source core.h — `#define FONT_SLASH 0xAF`
     */
    const val FONT_SLASH = 0xAF

    /**
     * Tile id for the horizontal menu border top row (flip-y gives the bottom row).
     *
     * @source core.h — `#define FONT_BORDER_TOP 0x91`
     */
    const val FONT_BORDER_TOP = 0x91

    // -------------------------------------------------------------------------
    // Torch system
    // @source player.h, map.h
    // -------------------------------------------------------------------------

    /**
     * Maximum value for the torch fuel gauge.
     *
     * Stored as a UINT8 (0–255). Starting value when a new torch is lit or when the player restores
     * it from a sconce.
     *
     * @source player.h — `uint8_t torch_gauge` field in Player struct; map.h torch HUD logic
     */
    const val TORCH_MAX = 255

    /**
     * Torch fuel level at which the "torch dimming" low-torch warning triggers.
     *
     * Below this threshold the player receives a visual/audio warning that the torch is running
     * low.
     *
     * @source map.h — referenced by torch gauge display and warning trigger logic
     */
    const val TORCH_LOW_THRESHOLD = 50

    /**
     * Number of frames between each torch gauge decrement by 1 unit.
     *
     * At 60 fps on GBC, this means the torch drains at approximately 6 units per second.
     *
     * @source map.h — `#define TORCH_GAUGE_SPEED 10`
     */
    const val TORCH_GAUGE_SPEED = 10

    // -------------------------------------------------------------------------
    // Ability system
    // @source player.h
    // -------------------------------------------------------------------------

    /**
     * Maximum number of abilities a player can acquire over the course of the game.
     *
     * Each class has exactly 6 ability slots (0–5). Abilities are granted on level-up or via
     * scripted events.
     *
     * @source player.h — `#define MAX_ABILITIES 6`; also `extern const Ability
     *   *player_abilities[6]`
     */
    const val MAX_ABILITIES = 6

    /**
     * Experience level at which new characters begin the game.
     *
     * New characters start at level 5 rather than level 1, giving them a small stat head-start.
     *
     * @source player.h — `#define NEW_CHARACTER_LEVEL 5`
     */
    const val NEW_CHARACTER_LEVEL = 5

    // -------------------------------------------------------------------------
    // Save system
    // @source SRAM layout / save.c
    // -------------------------------------------------------------------------

    /**
     * Number of save slots available to the player.
     *
     * Three distinct SRAM save areas, each storing a full player state snapshot.
     *
     * @source SRAM layout in save.c and the title screen save-slot selection UI
     */
    const val SAVE_SLOTS = 3

    // -------------------------------------------------------------------------
    // Monster tile dimensions
    // @source monster.h
    // -------------------------------------------------------------------------

    /**
     * Width of a monster sprite sheet tile (in 8-pixel tiles).
     *
     * Each monster is represented as a 7×7 tile grid (56×56 pixels) in two-frame animation.
     *
     * @source monster.h — `#define MONSTER_TILES 7 * 7 * 2` implies 7-wide grid
     */
    const val MONSTER_TILES_W = 7

    /**
     * Height of a monster sprite sheet tile (in 8-pixel tiles).
     *
     * @source monster.h — `#define MONSTER_TILES 7 * 7 * 2` implies 7-tall grid
     */
    const val MONSTER_TILES_H = 7

    // -------------------------------------------------------------------------
    // Map object limits per floor
    // @source map.h
    // -------------------------------------------------------------------------

    /**
     * Maximum number of maps (sub-areas) per dungeon floor, excluding sentinel.
     *
     * Each floor can contain up to 4 distinct map areas (MAP_A through MAP_D).
     *
     * @source map.h — `#define MAX_MAPS 4 + 1` (the +1 is the null-terminator sentinel)
     */
    const val MAX_MAPS_PER_FLOOR = 4

    /**
     * Maximum number of exit connections per floor, excluding sentinel.
     *
     * @source map.h — `#define MAX_EXITS 24 + 1`
     */
    const val MAX_EXITS_PER_FLOOR = 24

    /**
     * Maximum number of treasure chests per floor, excluding sentinel.
     *
     * @source map.h — `#define MAX_CHESTS 8 + 1`
     */
    const val MAX_CHESTS_PER_FLOOR = 8

    /**
     * Maximum number of signs per floor, excluding sentinel.
     *
     * @source map.h — `#define MAX_SIGNS 8 + 1`
     */
    const val MAX_SIGNS_PER_FLOOR = 8

    /**
     * Maximum number of levers per floor, excluding sentinel.
     *
     * @source map.h — `#define MAX_LEVERS 8 + 1`
     */
    const val MAX_LEVERS_PER_FLOOR = 8

    /**
     * Maximum number of sconces (wall torches) per floor, excluding sentinel.
     *
     * @source map.h — `#define MAX_SCONCES 32 + 1`
     */
    const val MAX_SCONCES_PER_FLOOR = 32

    /**
     * Maximum number of NPCs per floor, excluding sentinel.
     *
     * @source map.h — `#define MAX_NPCS 2 + 1`
     */
    const val MAX_NPCS_PER_FLOOR = 2

    /**
     * Maximum number of doors per floor, excluding sentinel.
     *
     * @source map.h — `#define MAX_DOORS 12 + 1`
     */
    const val MAX_DOORS_PER_FLOOR = 12

    // -------------------------------------------------------------------------
    // HUD / torch gauge sprite constants
    // @source map.h
    // -------------------------------------------------------------------------

    /**
     * X screen position (in pixels) for the torch gauge HUD sprites.
     *
     * @source map.h — `#define TORCH_GAUGE_X 16`
     */
    const val TORCH_GAUGE_X = 16

    /**
     * Y screen position (in pixels) for the torch gauge HUD sprites.
     *
     * @source map.h — `#define TORCH_GAUGE_Y 24`
     */
    const val TORCH_GAUGE_Y = 24

    /**
     * Number of monsters in the game (12 unique creature types).
     *
     * Kobold, Goblin, Zombie, Bugbear, Owlbear, Gelatinous Cube, Displacer Beast, Will-o'-Wisp,
     * Death Knight, Mind Flayer, Beholder, Dragon.
     *
     * @source monster.h — `typedef enum MonsterType` has 12 non-sentinel entries
     */
    const val MONSTER_COUNT = 12

    /**
     * Number of available magic keys (max collectible quantity).
     *
     * @source map.h — map key system tracks per-floor key counters
     */
    const val MAGIC_KEY_MAX = 9
}
