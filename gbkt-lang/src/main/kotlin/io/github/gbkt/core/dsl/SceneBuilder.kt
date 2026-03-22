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
     * [GBDKPipelineV2][io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipelineV2] to generate
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
        val slot = if (palette.slot >= 0) palette.slot else 0
        paletteOps += SetPalette(palette.name, slot, palette.type)
    }

    /** Builds the [SceneIR] node. */
    internal fun build(): SceneIR {
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
            sourceLocation = captureV2Location(),
        )
    }
}
