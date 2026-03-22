/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import java.io.File
import org.json.JSONObject

/**
 * Parser for LDtk map editor JSON format (.ldtk files).
 *
 * LDtk is a modern 2D level editor that exports to JSON. This parser supports format version 1.5.x
 * and reads layer types: Tiles, IntGrid, Entities, AutoLayer.
 *
 * Collision layer detection: layers with a `gbkt_collision=true` field in `fieldInstances` are
 * marked as collision layers — consistent with TiledParser's `gbkt_collision=true` custom property
 * convention.
 *
 * LDtk JSON format reference: https://ldtk.io/json/
 *
 * Usage:
 * ```kotlin
 * val map = LdtkParser.parse(content)
 * val collisionLayer = map.layers.firstOrNull { it.isCollision }
 * ```
 */
object LdtkParser {

    /** Supported LDtk JSON format version prefix (major.minor). */
    private const val SUPPORTED_VERSION_PREFIX = "1.5"

    /**
     * Parse an LDtk JSON map from a file.
     *
     * @param file Path to the .ldtk file
     * @return Parsed [LdtkMap]
     * @throws IllegalArgumentException if the file doesn't exist or the format version is
     *   unsupported
     */
    fun parse(file: File): LdtkMap {
        require(file.exists()) { "LDtk map not found: ${file.path}" }
        return parse(file.readText())
    }

    /**
     * Parse an LDtk JSON map from a string.
     *
     * @param content LDtk JSON string
     * @return Parsed [LdtkMap]
     * @throws IllegalArgumentException if the format version is unsupported
     */
    fun parse(content: String): LdtkMap {
        val json = JSONObject(content)

        val jsonVersion = json.optString("jsonVersion", "")
        require(jsonVersion.startsWith(SUPPORTED_VERSION_PREFIX)) {
            "Unsupported LDtk JSON format version: $jsonVersion. " +
                "Expected version $SUPPORTED_VERSION_PREFIX.x. " +
                "Please export your LDtk project with format version $SUPPORTED_VERSION_PREFIX or later."
        }

        val defaultGridSize = json.optInt("defaultGridSize", 8)
        val levels = json.getJSONArray("levels")
        val level = levels.getJSONObject(0) // Phase 3: single-level support
        val layerInstances = level.optJSONArray("layerInstances")

        val layers =
            if (layerInstances != null) {
                (0 until layerInstances.length()).map { i ->
                    parseLayer(layerInstances.getJSONObject(i))
                }
            } else emptyList()

        return LdtkMap(tileSize = defaultGridSize, layers = layers)
    }

    private fun parseLayer(layer: JSONObject): LdtkLayer {
        val identifier = layer.getString("__identifier")
        val type = layer.getString("__type")
        val gridSize = layer.getInt("__gridSize")
        val cWid = layer.getInt("__cWid")
        val cHei = layer.getInt("__cHei")

        // IntGrid CSV data (0 = empty cell)
        val intGridCsvArray = layer.optJSONArray("intGridCsv")
        val intGridCsv =
            if (intGridCsvArray != null) {
                (0 until intGridCsvArray.length()).map { intGridCsvArray.getInt(it) }
            } else emptyList()

        // Tile placements — try gridTiles first, then autoLayerTiles
        val gridTilesJson = layer.optJSONArray("gridTiles") ?: layer.optJSONArray("autoLayerTiles")
        val gridTiles =
            if (gridTilesJson != null) {
                (0 until gridTilesJson.length()).map { i ->
                    val t = gridTilesJson.getJSONObject(i)
                    val pxArray = t.getJSONArray("px")
                    LdtkTilePlacement(
                        px = Pair(pxArray.getInt(0), pxArray.getInt(1)),
                        t = t.getInt("t"),
                    )
                }
            } else emptyList()

        // Detect collision via fieldInstances: look for gbkt_collision=true
        val fieldInstances = layer.optJSONArray("fieldInstances")
        val isCollision =
            if (fieldInstances != null) {
                (0 until fieldInstances.length()).any { i ->
                    val field = fieldInstances.getJSONObject(i)
                    field.optString("__identifier") == "gbkt_collision" &&
                        field.optBoolean("__value", false)
                }
            } else false

        return LdtkLayer(
            identifier = identifier,
            type = type,
            gridSize = gridSize,
            cWid = cWid,
            cHei = cHei,
            intGridCsv = intGridCsv,
            gridTiles = gridTiles,
            isCollision = isCollision,
        )
    }
}

/** Parsed LDtk map structure. */
data class LdtkMap(
    /** Default tile size in pixels (from root defaultGridSize). */
    val tileSize: Int,
    /** All layer instances from the first level. */
    val layers: List<LdtkLayer>,
)

/** A layer from an LDtk map. */
data class LdtkLayer(
    /** Layer identifier (name) from LDtk. */
    val identifier: String,
    /** Layer type: "Tiles", "IntGrid", "Entities", or "AutoLayer". */
    val type: String,
    /** Grid cell size in pixels for this layer. */
    val gridSize: Int,
    /** Width in cells. */
    val cWid: Int,
    /** Height in cells. */
    val cHei: Int,
    /** Flat array of int grid values for IntGrid layers (0 = empty). */
    val intGridCsv: List<Int>,
    /** Tile placement entries for Tiles and AutoLayer layers. */
    val gridTiles: List<LdtkTilePlacement>,
    /** True if this layer has a gbkt_collision=true field in fieldInstances. */
    val isCollision: Boolean,
)

/** A single tile placement in an LDtk Tiles or AutoLayer layer. */
data class LdtkTilePlacement(
    /** Pixel coordinates of this tile placement as (x, y). */
    val px: Pair<Int, Int>,
    /** Tile source ID (index into the tileset). */
    val t: Int,
)
