/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.AssetType
import io.github.gbkt.core.ir.GBCPalette
import io.github.gbkt.core.ir.MusicPlay
import io.github.gbkt.core.ir.MusicStop
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SetPalette
import io.github.gbkt.core.ir.ZoneIR

/**
 * Lightweight typed reference to a scene.
 *
 * Returned by [GameBuilder.scene] for use in [ScriptBuilder.navigate] operations without requiring
 * string literals.
 */
data class SceneRef(val id: String) {
    override fun toString(): String = id
}

/**
 * Builder for a scene definition.
 *
 * Records the three lifecycle handlers — [enter], [frame], [exit] — each producing a list of
 * [io.github.gbkt.core.ir.ScriptOp] nodes.
 *
 * Call [build] to produce the [SceneIR] node.
 */
@GbktDsl
class SceneBuilder(val id: String, private val refs: RefRegistry) {
    private var enterOps: List<io.github.gbkt.core.ir.ScriptOp> = emptyList()
    private var frameOps: List<io.github.gbkt.core.ir.ScriptOp> = emptyList()
    private var exitOps: List<io.github.gbkt.core.ir.ScriptOp> = emptyList()
    private var tilesetRef: AssetRef? = null
    private var collisionBytes: ByteArray? = null
    private var collisionMapWidth: Int? = null
    // Music ops prepended to enter and appended to exit (set by music())
    private var musicEnterOp: io.github.gbkt.core.ir.ScriptOp? = null
    private var musicExitOp: io.github.gbkt.core.ir.ScriptOp? = null
    // Palette ops injected at the start of enter (set by palette())
    private val paletteOps = mutableListOf<io.github.gbkt.core.ir.ScriptOp>()
    // Zone IDs to load on scene-enter (SEED-014; set by zone(), flushed to SceneIR at build() time)
    private val zoneRefs = mutableListOf<String>()

    /** Registers the scene enter handler. Runs once when the game transitions into this scene. */
    fun enter(block: ScriptBuilder.() -> Unit) {
        val builder = ScriptBuilder()
        ScriptBuilderContext.with(builder) { builder.block() }
        enterOps = builder.build()
    }

    /** Registers the scene frame handler. Runs every game frame while in this scene. */
    fun frame(block: ScriptBuilder.() -> Unit) {
        val builder = ScriptBuilder()
        ScriptBuilderContext.with(builder) { builder.block() }
        frameOps = builder.build()
    }

    /** Registers the scene exit handler. Runs once when the game transitions out of this scene. */
    fun exit(block: ScriptBuilder.() -> Unit) {
        val builder = ScriptBuilder()
        ScriptBuilderContext.with(builder) { builder.block() }
        exitOps = builder.build()
    }

    /**
     * Associates a background tileset with this scene.
     *
     * The [path] should be a relative path to the tileset asset (e.g. "dungeon.png"). Used by
     * [io.github.gbkt.analysis.passes.VRAMLayoutPass] to estimate background tile usage.
     */
    fun tileset(path: String) {
        tilesetRef = AssetRef(path, AssetType.TILESET)
    }

    /**
     * Sets tile collision data for this scene.
     *
     * [data] is a flat byte array of tile passability values extracted from a TMX or LDtk collision
     * layer. Each byte represents one tile: 0 = passable, non-zero = wall. Array length should
     * equal `mapWidth * mapHeight`.
     *
     * [mapWidth] is the collision map width in tiles, used by `_map_collision(x, y)` to calculate
     * the linear index from 2D coordinates.
     *
     * Example:
     * ```kotlin
     * scene("dungeon_room") {
     *     tileset("dungeon.png")
     *     collisionData(
     *         data = byteArrayOf(1,1,1,1, 1,0,0,1, 1,0,0,1, 1,1,1,1),
     *         mapWidth = 4
     *     )
     *     enter { /* ... */ }
     * }
     * ```
     *
     * The collision data is stored in [SceneIR.collisionData] and used by
     * [GBDKPipeline][io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline] to generate
     * `_map_collision()` lookup functions.
     */
    fun collisionData(data: ByteArray, mapWidth: Int) {
        require(mapWidth > 0) { "mapWidth must be positive, got $mapWidth" }
        require(data.isNotEmpty()) { "collisionData must not be empty" }
        require(data.size % mapWidth == 0) {
            "collisionData size (${data.size}) must be divisible by mapWidth ($mapWidth)"
        }
        collisionBytes = data
        collisionMapWidth = mapWidth
    }

    /**
     * Associates a music track with this scene.
     *
     * Automatically prepends a [MusicPlay] op to the enter handler and appends a [MusicStop] op to
     * the exit handler. Can be called before or after [enter] and [exit] blocks — the ops are
     * merged at [build] time.
     *
     * Usage:
     * ```kotlin
     * scene("dungeon") {
     *     music(theme)
     *     enter { /* ... */ }
     *     exit  { /* ... */ }
     * }
     * ```
     *
     * @param musicRef Typed reference to a declared music track (from `val theme by music(...)`).
     */
    fun music(musicRef: MusicRef) {
        musicEnterOp = MusicPlay(musicRef.id)
        musicExitOp = MusicStop()
    }

    /**
     * Binds a zone's tileset to load on scene-enter. Backend emits a HOME-bank
     * `_bkg_tiles_load_banked(bank, x, y, w, h, tiles)` call prepended to the enter body.
     *
     * 11.1 limits to 1 zone per scene — multi-zone-per-scene is deferred to Phase 13 (CONTEXT
     * <deferred>).
     *
     * @param zoneRef Typed reference to a declared zone (from `val playZone by zone { ... }`).
     */
    fun zone(zoneRef: ZoneRef) {
        require(zoneRefs.isEmpty()) {
            "scene '$id' already binds a zone; multi-zone-per-scene deferred to Phase 13 (CONTEXT <deferred>)"
        }
        zoneRefs += zoneRef.id
    }

    /**
     * Binds a full-screen static image to this scene (Req #18 / D-03/D-04/D-05/D-06).
     *
     * Synthesizes an internal `_screen_<sceneId>` [ZoneIR] with [ZoneIR.screenMode] = true,
     * registers it with the enclosing [GameBuilder] (via [GameBuilderContext.current]), and adds
     * its ID to [zoneRefs] so SceneVisitor emits the screenMode superset on scene-enter:
     * hide_sprites_range + move_bkg(0,0) + fill_bkg_rect(full-plane clear) + centered
     * _bkg_tiles_load_banked placement (auto-derived from PNG IHDR via ConvertZoneTilesetsTask).
     *
     * **Must be called at scene scope** (parallel to `zone()`), NOT inside `enter { }`.
     * ScriptBuilder cannot add to [SceneIR.zoneRefs] nor register a [ZoneIR] with GameBuilder.
     *
     * **One `screen()` or `zone()` per scene** — multi-zone-per-scene is deferred (Phase 13
     * CONTEXT).
     *
     * Usage:
     * ```kotlin
     * scene("title") {
     *     screen(asset("graphics/title-screen.png"))
     *     frame { runIf(buttons.start.pressed) { navigate("gameplay") } }
     * }
     * ```
     *
     * The synthetic zone id `_screen_<sceneId>` (e.g. `_screen_title`) is automatically excluded
     * from the `setup_current_level()` switch by the title/nextlevel filter in GBDKPipeline
     * (Assumption A2 — the lower-cased id contains the scene id which contains "title" or
     * "nextlevel" for the typical use cases).
     *
     * @param assetRef Typed reference to the full-screen PNG image (from `asset("...")`).
     */
    fun screen(assetRef: AssetRef) {
        val syntheticId = "_screen_${id}"
        // WR-01 fix: validate BEFORE registering the zone so a double screen() call does not
        // leave an orphaned ZoneIR entry in GameIR.zones before the require exception fires.
        require(zoneRefs.isEmpty()) {
            "scene '$id' already binds a zone or screen; " +
                "multi-zone/screen-per-scene deferred to Phase 13 (CONTEXT <deferred>)"
        }
        val syntheticZone =
            ZoneIR(
                id = syntheticId,
                name = syntheticId,
                tilesetPath = assetRef.path,
                screenMode = true,
            )
        GameBuilderContext.current?.registerZone(syntheticZone)
            ?: error("screen() must be called inside a game { } block")
        zoneRefs += syntheticId
    }

    /**
     * Assigns a GBC palette to this scene's enter handler.
     *
     * Emits a [SetPalette] op at the beginning of enter, loading the palette data into the
     * specified hardware slot.
     *
     * Usage:
     * ```kotlin
     * scene("dungeon") {
     *     palette(dungeonPalette)
     *     enter { /* ... */ }
     * }
     * ```
     */
    fun palette(palette: GBCPalette) {
        // When slot == -1 (auto-assign), use the current paletteOps count as the next sequential
        // slot index. This ensures that palette(gray); palette(pink); palette(cyan); palette(green)
        // emits set_sprite_palette(0u,…), set_sprite_palette(1u,…), etc. — not all slot 0.
        val slot = if (palette.slot >= 0) palette.slot else paletteOps.size
        paletteOps += SetPalette(palette.name, slot, palette.type)
    }

    /**
     * Assigns a GBC palette to this scene's enter handler with an explicit hardware slot override.
     *
     * The [slot] value (0..7) is used directly — the palette's own [GBCPalette.slot] declaration is
     * ignored. This gives authors precise control ("always load the enemy palette into slot 3")
     * independent of the palette's default slot assignment.
     *
     * Throws [IllegalArgumentException] at build site (call time) if [slot] is outside 0..7 (D-11
     * range guard). A duplicate-slot check within the same scene runs at [build] time.
     *
     * Usage:
     * ```kotlin
     * scene("battle") {
     *     palette(enemyPalette, slot = 3)
     *     enter { /* ... */ }
     * }
     * ```
     */
    fun palette(palette: GBCPalette, slot: Int) {
        require(slot in 0..7) { "Palette slot must be in 0..7 for scene '$id', got $slot" }
        paletteOps += SetPalette(palette.name, slot, palette.type)
    }

    /** Builds the [SceneIR] node. */
    internal fun build(): SceneIR {
        // D-11: duplicate-slot guard — no two palettes may claim the same slot within this scene
        val slotGroups = paletteOps.filterIsInstance<SetPalette>().groupBy { it.slot }
        val duplicateSlot = slotGroups.entries.firstOrNull { (_, ops) -> ops.size > 1 }?.key
        require(duplicateSlot == null) {
            "Scene '$id' has two palettes mapped to slot $duplicateSlot — each OBP slot must be unique within a scene"
        }

        // Merge palette, music, and user ops: palette first, then music, then user enter ops
        val musicPrefix = if (musicEnterOp != null) listOf(musicEnterOp!!) else emptyList()
        val finalEnterOps = paletteOps + musicPrefix + enterOps
        val finalExitOps = if (musicExitOp != null) exitOps + listOf(musicExitOp!!) else exitOps
        return SceneIR(
            id = id,
            enterOps = finalEnterOps,
            frameOps = frameOps,
            exitOps = finalExitOps,
            tilesetRef = tilesetRef,
            collisionData = collisionBytes,
            mapWidth = collisionMapWidth,
            zoneRefs = zoneRefs.toList(),
            sourceLocation = captureV2Location(),
        )
    }
}
