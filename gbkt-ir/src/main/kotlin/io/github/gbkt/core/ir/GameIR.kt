/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

/**
 * A declared music track referencing a hUGETracker .uge asset.
 *
 * Registered via `val theme by music(asset("music/dungeon.uge"))` in the game DSL. The GBDK backend
 * generates `extern const hUGESong_t song_<id>;` declarations for each entry.
 *
 * @param id Unique identifier for this music track (inferred from the property name).
 * @param assetRef Reference to the .uge asset file.
 * @param sourceLocation Optional source location for error reporting.
 */
data class MusicDef(
    val id: String,
    val assetRef: AssetRef,
    val sourceLocation: SourceLocation? = null,
)

/**
 * Top-level IR node representing the complete game definition.
 *
 * This is the root of the IR tree passed to a backend for code generation. It is NOT sealed — there
 * is only one game per compilation unit.
 *
 * Fields:
 * - [name]: the game title (used in ROM header)
 * - [config]: cartridge hardware configuration
 * - [scenes]: all scene definitions
 * - [actors]: all actor definitions (sprites, entities)
 * - [systems]: engine-level systems (dialog, sound, save, camera, exploration, combat)
 * - [variables]: global mutable variables
 * - [arrays]: global mutable array variables
 * - [soundEffects]: registered sound effect definitions (for real NRxx register codegen)
 * - [hashTables]: static hash table collection declarations
 * - [pools]: static object pool collection declarations
 * - [ringBuffers]: static ring buffer collection declarations
 * - [fixedSlots]: static fixed-slots collection declarations
 * - [assets]: all referenced game assets
 * - [palettes]: GBC color palettes declared in the game (empty for DMG-only games)
 * - [startScene]: ID of the scene to start on boot; null means the backend decides
 * - [dialogs]: named dialog box definitions (rendered via DialogSay/DialogChoice script ops)
 * - [menus]: named interactive menu definitions (shown/hidden via MenuShow/MenuHide script ops)
 * - [huds]: named HUD panel definitions (shown/hidden via HudShow/HudHide script ops)
 * - [zones]: world/dungeon zone definitions (navigable areas with tilemaps and encounters)
 * - [flags]: global story flag containers (grouped boolean flags for game state tracking)
 * - [itemCategories]: item category definitions with default stacking rules
 * - [items]: item catalog (all item definitions)
 * - [containers]: inventory container definitions (bags, chests, etc.)
 * - [dropTables]: drop/loot table definitions for enemy drops and chest loot
 * - [musicDefs]: declared music tracks (registered via the `music()` DSL delegate)
 * - [structs]: struct type definitions (flat primitive fields); emitted as typedef struct in C
 * - [actorPools]: sprite-lifecycle-aware entity pools (bullets, bricks, particles)
 * - [puzzleObjects]: interactive puzzle world objects (switches, doors, pressure plates, timed
 *   blocks)
 * - [collisionGroups]: NPC collision groups declared via `val x by collisionGroup()` delegates
 * - [collisionRules]: NPC collision rules between groups with response type and optional callback
 */
data class GameIR(
    val name: String,
    val config: CartridgeConfig = CartridgeConfig(),
    val scenes: List<SceneIR> = emptyList(),
    val actors: List<ActorIR> = emptyList(),
    val systems: List<SystemIR> = emptyList(),
    val variables: List<VariableDef> = emptyList(),
    val arrays: List<ArrayDef> = emptyList(), // global array variables
    val soundEffects: List<SoundEffectDef> = emptyList(), // registered sound effect definitions
    val structs: List<StructDef> = emptyList(), // struct type definitions
    val hashTables: List<IRCollHashTable> = emptyList(),
    val pools: List<IRCollPool> = emptyList(),
    val ringBuffers: List<IRCollRingBuffer> = emptyList(),
    val fixedSlots: List<IRCollFixedSlots> = emptyList(),
    val assets: List<AssetRef> = emptyList(),
    val palettes: List<GBCPalette> = emptyList(), // GBC color palettes (empty for DMG-only games)
    val startScene: String? = null,
    val sourceLocation: SourceLocation? = null,
    val dialogs: List<DialogDef> = emptyList(), // named dialog box definitions
    val menus: List<MenuDef> = emptyList(), // named interactive menu definitions
    val huds: List<HudDef> = emptyList(), // named HUD panel definitions
    val zones: List<ZoneIR> = emptyList(), // world/dungeon zone definitions
    val flags: List<GlobalFlagsIR> = emptyList(), // global story flag containers
    val itemCategories: List<ItemCategoryDef> =
        emptyList(), // item category definitions with default stacking rules
    val items: List<ItemDef> = emptyList(), // item catalog
    val containers: List<ContainerIR> = emptyList(), // inventory container definitions
    val dropTables: List<DropTableIR> = emptyList(), // drop/loot table definitions
    val musicDefs: List<MusicDef> = emptyList(), // declared music tracks
    val actorPools: List<ActorPoolIR> = emptyList(), // sprite-lifecycle-aware entity pools
    val puzzleObjects: List<PuzzleObjectIR> = emptyList(), // interactive puzzle world objects
    val collisionGroups: List<CollisionGroupIR> =
        emptyList(), // NPC-NPC collision group declarations
    val collisionRules: List<CollisionRuleIR> = emptyList(), // NPC-NPC collision rules with response
)
