/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

/**
 * A single OAM entry within a metasprite frame.
 *
 * Describes one hardware sprite tile's position relative to the metasprite origin and its tile
 * index in the sprite VRAM block.
 *
 * @param relX Relative X offset from the metasprite origin (pixels, hardware OAM coordinates).
 * @param relY Relative Y offset from the metasprite origin (pixels, hardware OAM coordinates).
 * @param tileId Base tile index into the sprite tile data VRAM block.
 */
data class MetaspriteTile(val relX: Int, val relY: Int, val tileId: Int)

/**
 * One frame of a metasprite — a list of OAM entries that together form a composite sprite image.
 *
 * Variable-length: different frames can have different tile counts (e.g. partial frames where some
 * tiles are transparent are represented with fewer entries).
 *
 * @param tiles Ordered list of OAM tiles for this frame.
 */
data class MetaspriteFrame(val tiles: List<MetaspriteTile>)

/**
 * IR node for a GBDK-style variable-length metasprite.
 *
 * A metasprite is a composite sprite built from multiple hardware OAM tiles. The GBDK metasprite
 * API uses `sprite_metasprites[]` arrays of pointers to per-frame tile descriptor arrays. Each
 * descriptor element is a `{relY, relX, tileId, attributes}` struct.
 *
 * @param id Unique identifier (inferred from the Kotlin property name via delegate).
 * @param frames Ordered list of animation frames; each frame has its own tile list.
 * @param posXVarName Optional name of the user-declared variable bound as this metasprite's X
 *   position (set via [MetaspriteBuilder.posX] DSL binder). Captured as the Kotlin property name
 *   from `AssignableVar.name`. When `null`, the visitor (Plan 05) falls back to the canonical
 *   `_posX` global for back-compat with the Phase 10 port — see CR-03 / WR-01.
 * @param posYVarName Optional name of the user-declared variable bound as this metasprite's Y
 *   position. Same fallback semantics as [posXVarName].
 * @param idxVarName Optional name of the user-declared variable bound as this metasprite's frame
 *   index. Same fallback semantics as [posXVarName].
 * @param rotVarName Optional name of the user-declared variable bound as this metasprite's
 *   rotation/orientation state. Same fallback semantics as [posXVarName].
 * @param mirrorDedup Per-metasprite opt-in (Plan 10.1-16 Task 2) to allow png2asset's mirror-pair
 *   tile deduplication for this metasprite's source PNG. When `false` (default),
 *   [ConvertSpritesTask] passes `-noflip` to png2asset, producing the full unique-tile array --
 *   this preserves DSL faithfulness to the reference's `-noflip` id space and is the correct
 *   default for metasprites transcribed from a reference's `-noflip` output (e.g. the GBDK
 *   metasprites example's elephant; DEF-10.1-13-A). When `true`, [ConvertSpritesTask] OMITS
 *   `-noflip` so png2asset detects mirror tile pairs and emits one tile + `S_FLIPX`/`S_FLIPY`
 *   METASPR_ITEM attrs for the mirrored variant -- a smaller tile array, intended for from-scratch
 *   authored metasprites that can take advantage of the dedup. Set via DSL: `metasprite("foo") {
 *   mirrorDedup() }`. See [MetaspriteBuilder.mirrorDedup] + ConvertSpritesTask wiring in Plan
 *   10.1-16 Task 4.
 * @param spritePath Optional explicit PNG asset path bound to this metasprite via the
 *   `sprite(asset(...))` DSL binder. Captured as `AssetRef.path` (typed — no magic strings per
 *   feedback_no_magic_strings.md). When non-null, the codegen pipeline (Phase 12.4 D-02) emits this
 *   metasprite into the `sprites[]` section of `game_metadata.json`; ConvertSpritesTask reads the
 *   sidecar and resolves `{assetDir}/{spritePath}` to the PNG file for png2asset invocation. When
 *   null (default — migration window per D-01b), the metasprite is skipped by the sidecar emitter
 *   and GenerateCTask's validation gate (Plan 12.4-05) throws GradleException at codegen time.
 * @param frameCount Optional author-declared animation frame count (from the `frames(N)` DSL call;
 *   Phase 13.3 D-07). When non-null, [ConvertSpritesTask] parses the actual count from png2asset's
 *   output `.c` file and fails the build if the two disagree — catching DSL/asset desync at build
 *   time. When null (default — no `frames(N)` declaration), the validation is skipped. Semantically
 *   distinct from [frames].size (escape-hatch DSL frames) and from [idxVar]'s `wrapAt` (per
 *   RESEARCH recommendation, these are independent concepts).
 * @param sourceLocation Optional DSL source location for error reporting.
 */
data class MetaspriteIR(
    val id: String,
    val frames: List<MetaspriteFrame>,
    val posXVarName: String? = null,
    val posYVarName: String? = null,
    val idxVarName: String? = null,
    val rotVarName: String? = null,
    val mirrorDedup: Boolean = false,
    val spritePath: String? = null,
    val sourceLocation: SourceLocation? = null,
    // Phase 12.5 D-04b — png2asset cutting flags; nullable for migration window;
    // validation gate in GenerateCTask throws GradleException if any is null at codegen time.
    val spriteMode: SpriteMode? = null,
    val pivotX: Int? = null,
    val pivotY: Int? = null,
    val frameWidth: Int? = null,
    val frameHeight: Int? = null,
    // Phase 13.3 D-07 — author-declared animation frame count for build-time cross-validation
    // against png2asset's parsed output. Null when `frames(N)` was not called in the DSL.
    val frameCount: Int? = null,
    /**
     * Compile-time OBJ palette slot for the `set_sprite_palette` upload (Req 5, 12.9 WR-05).
     *
     * When non-null, `GBDKPipeline.buildMetaspriteSpritePaletteStatements` uses this value as the
     * hardware OBJ sub-palette slot index (0–7 on GBC) instead of the metasprite's list position.
     * This ensures the upload slot matches the draw-path's sub-palette selection even when the
     * metasprite list order does not match the intended OBJ slot layout.
     *
     * When null (default), the pipeline falls back to the metasprite's list index — preserving
     * byte-identity for all existing shipped games that have not declared an explicit slot.
     *
     * IMPORTANT: This is the COMPILE-TIME upload target, distinct from the RUNTIME variable
     * `_<id>_subPalette` (UINT8, initialized to 0, changed at runtime via `ms.subPalette set
     * expr`). Setting [initialSubPaletteSlot] does NOT change the initial value of
     * `_<id>_subPalette`.
     */
    val initialSubPaletteSlot: Int? = null,
    /**
     * Scene that owns this metasprite — used for scene-scoped OBJ palette suppression (Req 4, 13.7
     * WR-05).
     *
     * When non-null, the scene-scoped predicate in
     * `GBDKPipeline.buildMetaspriteSpritePaletteStatements` checks whether THIS metasprite's owning
     * scene declares a `spritePalette{}` (`GBCPalette(type=SPRITE)`). Only if that specific scene
     * has a SPRITE palette is the asset-driven auto-upload suppressed — a `spritePalette{}` in any
     * other scene has no effect on this metasprite's upload.
     *
     * When null (default), the pipeline falls back to the game-global predicate
     * (`gameIR.palettes.any { it.type == PaletteType.SPRITE }`) — preserving byte-identity for all
     * existing shipped games where metasprites carry no scene linkage.
     */
    val sceneId: String? = null,
)
