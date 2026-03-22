/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Asset manifest data model and JSON serializer/deserializer.
 *
 * The manifest is a structured JSON file produced by the asset processing Gradle task. It lists
 * every processed asset with type-specific metadata for downstream codegen consumption.
 *
 * Output file name: [MANIFEST_FILENAME] = "asset-manifest.json"
 *
 * JSON schema:
 * ```json
 * {
 *   "version": 1,
 *   "assets": [
 *     { "type": "SPRITE", "path": "...", "tileCount": N, ... },
 *     { "type": "TILEMAP", "path": "...", "width": W, ... }
 *   ]
 * }
 * ```
 *
 * Usage:
 * ```kotlin
 * val manifest = AssetManifest(assets = listOf(spriteEntry, tilemapEntry))
 * File("build/generated/assets/asset-manifest.json").writeText(manifest.toJson())
 * val back = AssetManifest.fromJson(content)
 * ```
 */
data class AssetManifest(val version: Int = 1, val assets: List<AssetManifestEntry>) {
    /**
     * Serialize to JSON with 2-space indentation.
     *
     * @return JSON string representation of this manifest.
     */
    fun toJson(): String {
        val root = JSONObject()
        root.put("version", version)
        val assetsArray = JSONArray()
        for (entry in assets) {
            assetsArray.put(entry.toJson())
        }
        root.put("assets", assetsArray)
        return root.toString(2)
    }

    companion object {
        /** Standard output filename for the asset manifest. */
        const val MANIFEST_FILENAME = "asset-manifest.json"

        /**
         * Deserialize an [AssetManifest] from a JSON string.
         *
         * @param content JSON string previously produced by [toJson]
         * @return Parsed [AssetManifest]
         * @throws IllegalArgumentException if the JSON structure is invalid
         */
        fun fromJson(content: String): AssetManifest {
            val root = JSONObject(content)
            val version = root.getInt("version")
            val assetsArray = root.getJSONArray("assets")
            val assets =
                (0 until assetsArray.length()).map { i ->
                    AssetManifestEntry.fromJson(assetsArray.getJSONObject(i))
                }
            return AssetManifest(version = version, assets = assets)
        }
    }
}

/**
 * Sealed hierarchy of asset manifest entries.
 *
 * Each subtype corresponds to a distinct asset category with its own metadata fields.
 */
sealed class AssetManifestEntry {

    /** Asset file path (relative to asset directory). */
    abstract val path: String

    /** Type discriminator string written to JSON. */
    abstract val typeName: String

    /**
     * Serialize this entry to a [JSONObject].
     *
     * @return JSON object with "type" discriminator and entry-specific fields.
     */
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("type", typeName)
        obj.put("path", path)
        writeFields(obj)
        return obj
    }

    /** Write subtype-specific fields to the given [JSONObject]. */
    protected abstract fun writeFields(obj: JSONObject)

    companion object {
        /**
         * Deserialize an [AssetManifestEntry] from a [JSONObject].
         *
         * @param obj JSON object with a "type" discriminator field.
         * @return Parsed entry.
         * @throws IllegalArgumentException if the type discriminator is unrecognized.
         */
        fun fromJson(obj: JSONObject): AssetManifestEntry {
            return when (val type = obj.getString("type")) {
                "SPRITE" -> SpriteEntry.fromJson(obj)
                "TILEMAP" -> TilemapEntry.fromJson(obj)
                else -> throw IllegalArgumentException("Unknown asset manifest entry type: $type")
            }
        }
    }

    /**
     * Manifest entry for a sprite sheet (PNG with tiles).
     *
     * @param path File path relative to asset directory.
     * @param tileCount Total tile count in the sprite sheet (including duplicates).
     * @param uniqueTileCount Unique tile count after deduplication.
     * @param widthInTiles Width of the sprite sheet in 8x8 tiles.
     * @param heightInTiles Height of the sprite sheet in 8x8 tiles.
     * @param palette GB luminance palette (4 threshold values).
     * @param frameWidth Animation frame width in pixels.
     * @param frameHeight Animation frame height in pixels.
     * @param frameCount Number of animation frames in the sheet.
     */
    data class SpriteEntry(
        override val path: String,
        val tileCount: Int,
        val uniqueTileCount: Int,
        val widthInTiles: Int,
        val heightInTiles: Int,
        val palette: List<Int>,
        val frameWidth: Int,
        val frameHeight: Int,
        val frameCount: Int,
    ) : AssetManifestEntry() {
        override val typeName = "SPRITE"

        override fun writeFields(obj: JSONObject) {
            obj.put("tileCount", tileCount)
            obj.put("uniqueTileCount", uniqueTileCount)
            obj.put("widthInTiles", widthInTiles)
            obj.put("heightInTiles", heightInTiles)
            obj.put("palette", JSONArray(palette))
            obj.put("frameWidth", frameWidth)
            obj.put("frameHeight", frameHeight)
            obj.put("frameCount", frameCount)
        }

        companion object {
            internal fun fromJson(obj: JSONObject): SpriteEntry {
                val paletteArray = obj.getJSONArray("palette")
                val palette = (0 until paletteArray.length()).map { paletteArray.getInt(it) }
                return SpriteEntry(
                    path = obj.getString("path"),
                    tileCount = obj.getInt("tileCount"),
                    uniqueTileCount = obj.getInt("uniqueTileCount"),
                    widthInTiles = obj.getInt("widthInTiles"),
                    heightInTiles = obj.getInt("heightInTiles"),
                    palette = palette,
                    frameWidth = obj.getInt("frameWidth"),
                    frameHeight = obj.getInt("frameHeight"),
                    frameCount = obj.getInt("frameCount"),
                )
            }
        }
    }

    /**
     * Manifest entry for a tilemap (TMX or LDtk map file).
     *
     * @param path File path relative to asset directory.
     * @param width Map width in tiles.
     * @param height Map height in tiles.
     * @param hasCollision True if the map has a collision layer.
     * @param tilesetPath Optional path to the associated tileset PNG.
     * @param uniqueTileCount Number of unique 8x8 tiles used by this tilemap after deduplication.
     *   Zero means the count is unknown (e.g. manifest produced before this field was added). Used
     *   by [VRAMLayoutPass] to replace the conservative 256-tile heuristic with actual data.
     */
    data class TilemapEntry(
        override val path: String,
        val width: Int,
        val height: Int,
        val hasCollision: Boolean,
        val tilesetPath: String?,
        val uniqueTileCount: Int = 0,
    ) : AssetManifestEntry() {
        override val typeName = "TILEMAP"

        override fun writeFields(obj: JSONObject) {
            obj.put("width", width)
            obj.put("height", height)
            obj.put("hasCollision", hasCollision)
            if (tilesetPath != null) {
                obj.put("tilesetPath", tilesetPath)
            }
            obj.put("uniqueTileCount", uniqueTileCount)
        }

        companion object {
            internal fun fromJson(obj: JSONObject): TilemapEntry {
                return TilemapEntry(
                    path = obj.getString("path"),
                    width = obj.getInt("width"),
                    height = obj.getInt("height"),
                    hasCollision = obj.getBoolean("hasCollision"),
                    tilesetPath =
                        if (obj.has("tilesetPath")) obj.getString("tilesetPath") else null,
                    uniqueTileCount =
                        if (obj.has("uniqueTileCount")) obj.getInt("uniqueTileCount") else 0,
                )
            }
        }
    }
}
