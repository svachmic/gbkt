/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Parser for Tiled map editor JSON exports.
 *
 * Supports Tiled JSON format (.json files exported from Tiled). See:
 * https://doc.mapeditor.org/en/stable/reference/json-map-format/
 *
 * Usage: val map = TiledParser.parse("level1.json") val layer = map.layers.first { it.visible } val
 * indices = TiledParser.normalizeLayer(layer, map.tilesets.first().firstGid)
 */
object TiledParser {

    /** Game Boy background tilemap width and height in tiles (32×32 tile grid). */
    private const val GB_BG_MAP_DIMENSION = 32

    /** Maximum VRAM tile index (0-based), matching the 256-entry tile table. */
    private const val GB_MAX_TILE_COUNT = 256

    /**
     * Parse a Tiled JSON map file.
     *
     * @param path Path to the JSON file
     * @return Parsed TiledMap structure
     */
    fun parse(path: String): TiledMap {
        val file = File(path)
        require(file.exists()) { "Tiled map not found: $path" }

        val json = JSONObject(file.readText())
        return parseJson(json, file.parentFile)
    }

    /** Parse a Tiled JSON map from a File. */
    fun parse(file: File): TiledMap {
        require(file.exists()) { "Tiled map not found: ${file.path}" }
        val json = JSONObject(file.readText())
        return parseJson(json, file.parentFile)
    }

    /**
     * Parse a Tiled JSON map directly from a JSON string.
     *
     * Intended for testing: allows inline JSON content without writing to disk.
     *
     * @param content JSON string with Tiled map data
     * @param baseDir Optional base directory for resolving relative tileset paths
     * @return Parsed TiledMap structure
     */
    fun parseContent(content: String, baseDir: File? = null): TiledMap {
        val json = JSONObject(content)
        return parseJson(json, baseDir)
    }

    private fun parseJson(json: JSONObject, baseDir: File?): TiledMap {
        val width = json.getInt("width")
        val height = json.getInt("height")
        val tileWidth = json.getInt("tilewidth")
        val tileHeight = json.getInt("tileheight")

        val layers = parseLayers(json.getJSONArray("layers"))
        val tilesets = parseTilesets(json.getJSONArray("tilesets"), baseDir)

        val map =
            TiledMap(
                width = width,
                height = height,
                tileWidth = tileWidth,
                tileHeight = tileHeight,
                layers = layers,
                tilesets = tilesets,
            )

        validate(map)
        return map
    }

    private fun parseLayers(layersJson: JSONArray): List<TiledLayer> {
        return (0 until layersJson.length())
            .map { layersJson.getJSONObject(it) }
            .filter { it.getString("type") == "tilelayer" }
            .map { layerJson ->
                TiledLayer(
                    name = layerJson.getString("name"),
                    width = layerJson.getInt("width"),
                    height = layerJson.getInt("height"),
                    data = parseLayerData(layerJson.getJSONArray("data")),
                    visible = layerJson.optBoolean("visible", true),
                    properties = parseLayerProperties(layerJson),
                )
            }
    }

    private fun parseLayerProperties(layerJson: JSONObject): Map<String, Any> {
        val propsArray = layerJson.optJSONArray("properties") ?: return emptyMap()
        val properties = mutableMapOf<String, Any>()
        for (i in 0 until propsArray.length()) {
            val prop = propsArray.getJSONObject(i)
            val name = prop.getString("name")
            val type = prop.optString("type", "string")
            val value: Any =
                when (type) {
                    "bool" -> prop.getBoolean("value")
                    "int" -> prop.getInt("value")
                    "float" -> prop.getDouble("value")
                    else -> prop.getString("value")
                }
            properties[name] = value
        }
        return properties
    }

    private fun parseLayerData(dataJson: JSONArray): List<Int> {
        return (0 until dataJson.length()).map { dataJson.getInt(it) }
    }

    private fun parseTilesets(tilesetsJson: JSONArray, baseDir: File?): List<TiledTileset> {
        return (0 until tilesetsJson.length())
            .map { tilesetsJson.getJSONObject(it) }
            .map { tilesetJson ->
                // Handle both embedded and external tilesets
                val imagePath = tilesetJson.optString("image", "")
                val resolvedImage =
                    if (imagePath.isNotEmpty() && baseDir != null) {
                        File(baseDir, imagePath).path
                    } else {
                        imagePath
                    }

                TiledTileset(
                    name = tilesetJson.optString("name", "tileset"),
                    firstGid = tilesetJson.getInt("firstgid"),
                    tileCount = tilesetJson.optInt("tilecount", 0),
                    tileWidth = tilesetJson.optInt("tilewidth", 8),
                    tileHeight = tilesetJson.optInt("tileheight", 8),
                    image = resolvedImage,
                    columns = tilesetJson.optInt("columns", 0),
                )
            }
    }

    /**
     * Validate a Tiled map against Game Boy constraints.
     *
     * @throws IllegalArgumentException if validation fails
     */
    fun validate(map: TiledMap) {
        // Tile size must be 8x8
        require(map.tileWidth == 8 && map.tileHeight == 8) {
            """
            Invalid tile size: ${map.tileWidth}x${map.tileHeight}
            Game Boy requires 8x8 pixel tiles.
            Configure in Tiled: Map → Map Properties → Tile Size
            """
                .trimIndent()
        }

        // Map size must not exceed GB_BG_MAP_DIMENSION x GB_BG_MAP_DIMENSION tiles
        require(map.width <= GB_BG_MAP_DIMENSION && map.height <= GB_BG_MAP_DIMENSION) {
            """
            Map too large: ${map.width}x${map.height} tiles
            Maximum background size is ${GB_BG_MAP_DIMENSION}x${GB_BG_MAP_DIMENSION} tiles (${GB_BG_MAP_DIMENSION * 8}x${GB_BG_MAP_DIMENSION * 8} pixels).
            For larger levels, consider using bank switching or scrolling regions.
            """
                .trimIndent()
        }

        // Tileset must not exceed GB_MAX_TILE_COUNT tiles
        val totalTiles = map.tilesets.sumOf { it.tileCount }
        require(totalTiles <= GB_MAX_TILE_COUNT) {
            """
            Too many tiles: $totalTiles
            Maximum is $GB_MAX_TILE_COUNT unique tiles (indices 0-${GB_MAX_TILE_COUNT - 1}).
            Reduce tileset size or use tile sharing between layers.
            """
                .trimIndent()
        }
    }

    /**
     * Convert Tiled GIDs to local tile indices (0-based).
     *
     * Tiled uses Global IDs (GIDs) that start from firstGid of each tileset. This function
     * normalizes them to 0-based indices for the Game Boy.
     *
     * @param layer The tile layer to normalize
     * @param firstGid The firstGid of the tileset
     * @return ByteArray of normalized tile indices
     */
    fun normalizeLayer(layer: TiledLayer, firstGid: Int): ByteArray {
        return layer.data
            .map { gid ->
                if (gid == 0) {
                    0.toByte() // Empty/transparent tile
                } else {
                    ((gid - firstGid) and 0xFF).toByte()
                }
            }
            .toByteArray()
    }

    /** Get the first visible tile layer from a map. */
    fun getFirstVisibleLayer(map: TiledMap): TiledLayer? {
        return map.layers.firstOrNull { it.visible }
    }
}

/** Parsed Tiled map structure. */
data class TiledMap(
    val width: Int, // Map width in tiles
    val height: Int, // Map height in tiles
    val tileWidth: Int, // Tile width in pixels (should be 8)
    val tileHeight: Int, // Tile height in pixels (should be 8)
    val layers: List<TiledLayer>,
    val tilesets: List<TiledTileset>,
) {
    /** Get a layer by name */
    fun getLayer(name: String): TiledLayer? = layers.find { it.name == name }

    /** Get the first visible layer */
    fun getVisibleLayer(): TiledLayer? = layers.firstOrNull { it.visible }
}

/** A tile layer from a Tiled map. */
data class TiledLayer(
    val name: String,
    val width: Int, // Layer width in tiles
    val height: Int, // Layer height in tiles
    val data: List<Int>, // Tile GIDs (Global IDs)
    val visible: Boolean,
    /** Custom properties from the Tiled layer's properties array. */
    val properties: Map<String, Any> = emptyMap(),
) {
    /**
     * Returns true if this layer is designated as a collision layer via the gbkt_collision=true
     * custom property.
     */
    val isCollisionLayer: Boolean
        get() = properties["gbkt_collision"] == true

    /** Get tile at position (x, y) */
    fun getTile(x: Int, y: Int): Int {
        require(x in 0 until width && y in 0 until height) {
            "Position ($x, $y) out of bounds for layer ${width}x${height}"
        }
        return data[y * width + x]
    }
}

/** A tileset reference from a Tiled map. */
data class TiledTileset(
    val name: String,
    val firstGid: Int, // First Global ID in this tileset
    val tileCount: Int, // Number of tiles in tileset
    val tileWidth: Int, // Tile width (should match map's tilewidth)
    val tileHeight: Int, // Tile height (should match map's tileheight)
    val image: String, // Path to tileset PNG (resolved relative to map)
    val columns: Int, // Number of columns in tileset image
)
