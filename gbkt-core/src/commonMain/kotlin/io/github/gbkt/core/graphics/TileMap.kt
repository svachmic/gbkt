/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.graphics

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IRCall
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRRaw
import io.github.gbkt.core.ir.IRVar

// =============================================================================
// TILEMAP
// =============================================================================

/**
 * A background tilemap from a Tiled JSON export.
 *
 * Tilemaps are used for static background layers that can scroll. The Game Boy supports one 32x32
 * tile background (256x256 pixels).
 *
 * Usage: val level = tilemap("level1.json") { tileset = "tiles.png" // Override tileset if needed }
 *
 * scene("gameplay") { enter { level.show() } every.frame { level.scrollTo(cameraX, cameraY) } }
 */
class TileMap(
    val name: String,
    val asset: String, // Path to Tiled JSON file
    val tilesetAsset: String?, // Optional tileset override
    val layerName: String?, // Optional specific layer name
    val collisionLayerName: String?, // Optional collision layer name
    val slot: Int, // Map slot (for future multi-map support)
    // These are populated at code generation time from the Tiled JSON
    internal var widthInTiles: Int = 32,
    internal var heightInTiles: Int = 32,
    internal var tileData: IntArray = IntArray(0),
    internal var layerData: Map<String, IntArray> = emptyMap(),
    internal var collisionData: IntArray? = null // Collision map data (0 = walkable, >0 = blocked)
) {
    /** Get tile data for a specific layer. Returns null if layer doesn't exist. */
    fun getLayerData(layerName: String): IntArray? = layerData[layerName]

    /**
     * Get collision data for the specified collision layer. Returns null if no collision layer was
     * specified or data is not available.
     *
     * @param layerName Optional specific layer name. If null, uses the configured collision layer.
     * @return IntArray of collision values (0 = walkable, >0 = blocked), or null if unavailable.
     */
    fun getCollisionData(layerName: String? = null): IntArray? {
        val targetLayer = layerName ?: collisionLayerName
        return if (targetLayer != null) {
            layerData[targetLayer]
        } else {
            collisionData
        }
    }

    /**
     * Check if a tile is blocked at the given coordinates.
     *
     * @param tileX Tile X coordinate
     * @param tileY Tile Y coordinate
     * @return true if the tile is blocked, false if walkable
     */
    fun isBlocked(tileX: Int, tileY: Int): Boolean {
        val data = getCollisionData() ?: return false
        if (tileX < 0 || tileX >= widthInTiles || tileY < 0 || tileY >= heightInTiles) {
            return true // Out of bounds = blocked
        }
        val idx = tileY * widthInTiles + tileX
        return if (idx < data.size) data[idx] > 0 else false
    }

    /**
     * Check if a pixel position is blocked.
     *
     * @param pixelX Pixel X coordinate
     * @param pixelY Pixel Y coordinate
     * @return true if the tile at this position is blocked
     */
    fun isBlockedAtPixel(pixelX: Int, pixelY: Int): Boolean {
        return isBlocked(pixelX / 8, pixelY / 8)
    }

    /**
     * Show the background (makes it visible). Also loads the initial tile data if not already
     * loaded.
     */
    fun show() {
        RecordingContext.require().emit(IRRaw("SHOW_BKG;"))
        RecordingContext.require()
            .emit(
                IRCall(
                    "set_bkg_tiles",
                    listOf(
                        IRLiteral(0),
                        IRLiteral(0),
                        IRVar("${name.uppercase()}_WIDTH"),
                        IRVar("${name.uppercase()}_HEIGHT"),
                        IRVar("${name}_map")
                    )
                )
            )
    }

    /** Hide the background */
    fun hide() {
        RecordingContext.require().emit(IRRaw("HIDE_BKG;"))
    }

    /** Scroll the background to position (x, y). Uses pixel coordinates for smooth scrolling. */
    fun scrollTo(x: Expr, y: Expr) {
        RecordingContext.require().emit(IRCall("move_bkg", listOf(x.ir, y.ir)))
    }

    /** Scroll the background to position (x, y). Overload for literal values. */
    fun scrollTo(x: Int, y: Int) {
        RecordingContext.require().emit(IRCall("move_bkg", listOf(IRLiteral(x), IRLiteral(y))))
    }

    /** Scroll background by delta values. */
    fun scrollBy(dx: Expr, dy: Expr) {
        RecordingContext.require().emit(IRCall("scroll_bkg", listOf(dx.ir, dy.ir)))
    }
}

/** Builder for creating TileMap instances. */
@GbktDsl
class TileMapBuilder(private val asset: String, private val slot: Int) {
    /** Override tileset image (optional) */
    var tileset: String? = null

    /** Specific layer name to use (optional, defaults to first visible layer) */
    var layer: String? = null

    /** Collision layer name (optional, used for collision detection) */
    var collisionLayer: String? = null

    private var _name: String? = null

    /** Set a custom name for the map */
    fun name(value: String) {
        _name = value
    }

    fun build(): TileMap {
        val mapName =
            _name
                ?: asset
                    .substringAfterLast("/")
                    .substringBeforeLast(".")
                    .replace(Regex("[^a-zA-Z0-9_]"), "_")

        return TileMap(
            name = mapName,
            asset = asset,
            tilesetAsset = tileset,
            layerName = layer,
            collisionLayerName = collisionLayer,
            slot = slot
        )
    }
}
