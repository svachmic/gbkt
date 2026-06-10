/*
 * Copyright 2026 Michal Svacha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.gbkt.intellij.editors.tilemap

import java.awt.image.BufferedImage

/**
 * Model representing a tilemap for Game Boy development.
 *
 * Game Boy tilemaps are typically 32x32 tiles (256x256 pixels) but can vary. Each tile is 8x8
 * pixels and referenced by an index into a tileset.
 */
data class TilemapModel(
    val name: String,
    val width: Int,
    val height: Int,
    val tiles: IntArray,
    val attributes: IntArray,
) {
    /** Tile attributes for collision and behavior. */
    enum class TileAttribute(val flag: Int) {
        NONE(0),
        WALL(1),
        WATER(2),
        PIT(4),
        DAMAGE(8),
        EXIT(16),
        SPECIAL(32),
        SAVE_POINT(64),
    }

    init {
        require(tiles.size == width * height) { "Tiles array size must match width * height" }
        require(attributes.size == width * height) {
            "Attributes array size must match width * height"
        }
    }

    /** Gets the tile index at the specified position. */
    fun getTile(x: Int, y: Int): Int {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0
        return tiles[y * width + x]
    }

    /** Sets the tile index at the specified position. */
    fun setTile(x: Int, y: Int, tileIndex: Int) {
        if (x < 0 || x >= width || y < 0 || y >= height) return
        tiles[y * width + x] = tileIndex
    }

    /** Gets the attribute at the specified position. */
    fun getAttribute(x: Int, y: Int): Int {
        if (x < 0 || x >= width || y < 0 || y >= height) return 0
        return attributes[y * width + x]
    }

    /** Sets the attribute at the specified position. */
    fun setAttribute(x: Int, y: Int, attribute: Int) {
        if (x < 0 || x >= width || y < 0 || y >= height) return
        attributes[y * width + x] = attribute
    }

    /** Checks if a position has a specific attribute flag. */
    fun hasAttribute(x: Int, y: Int, attr: TileAttribute): Boolean {
        return (getAttribute(x, y) and attr.flag) != 0
    }

    /** Toggles an attribute flag at the specified position. */
    fun toggleAttribute(x: Int, y: Int, attr: TileAttribute) {
        val current = getAttribute(x, y)
        setAttribute(x, y, current xor attr.flag)
    }

    /** Fills a rectangular region with the specified tile. */
    fun fillRect(x1: Int, y1: Int, x2: Int, y2: Int, tileIndex: Int) {
        val minX = minOf(x1, x2).coerceIn(0, width - 1)
        val maxX = maxOf(x1, x2).coerceIn(0, width - 1)
        val minY = minOf(y1, y2).coerceIn(0, height - 1)
        val maxY = maxOf(y1, y2).coerceIn(0, height - 1)

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                setTile(x, y, tileIndex)
            }
        }
    }

    /** Generates gbkt code for this tilemap. */
    fun toGbktCode(): String {
        val sb = StringBuilder()
        sb.append("val $name by tilemap {\n")
        sb.append("    width = $width\n")
        sb.append("    height = $height\n")
        sb.append("    tiles = intArrayOf(\n")

        for (y in 0 until height) {
            sb.append("        ")
            for (x in 0 until width) {
                sb.append("${getTile(x, y)}")
                if (x < width - 1 || y < height - 1) sb.append(", ")
            }
            sb.append("\n")
        }
        sb.append("    )\n")
        sb.append("}\n")

        return sb.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TilemapModel) return false
        return name == other.name &&
            width == other.width &&
            height == other.height &&
            tiles.contentEquals(other.tiles) &&
            attributes.contentEquals(other.attributes)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + tiles.contentHashCode()
        result = 31 * result + attributes.contentHashCode()
        return result
    }

    companion object {
        /** Game Boy tile size in pixels. */
        const val TILE_SIZE = 8

        /** Creates an empty tilemap. */
        fun create(name: String, width: Int, height: Int): TilemapModel {
            return TilemapModel(
                name = name,
                width = width,
                height = height,
                tiles = IntArray(width * height),
                attributes = IntArray(width * height),
            )
        }

        /** Standard Game Boy screen size in tiles. */
        fun createScreenSize(name: String): TilemapModel = create(name, 20, 18)

        /** Standard Game Boy map size (32x32 tiles). */
        fun createStandardSize(name: String): TilemapModel = create(name, 32, 32)
    }
}

/**
 * Represents a tileset (collection of tiles).
 *
 * A tileset is a sprite sheet where each 8x8 region is a tile that can be placed on a tilemap.
 */
data class TilesetModel(val name: String, val image: BufferedImage, val tileSize: Int = 8) {

    /** Number of tiles in the X direction. */
    val tilesX: Int = image.width / tileSize

    /** Number of tiles in the Y direction. */
    val tilesY: Int = image.height / tileSize

    /** Total number of tiles. */
    val tileCount: Int = tilesX * tilesY

    /** Gets the sub-image for a specific tile index. */
    fun getTileImage(index: Int): BufferedImage {
        if (index < 0 || index >= tileCount) {
            return BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB)
        }

        val x = (index % tilesX) * tileSize
        val y = (index / tilesX) * tileSize
        return image.getSubimage(x, y, tileSize, tileSize)
    }

    /** Gets the tile index at the specified pixel position in the tileset. */
    fun getTileIndexAt(pixelX: Int, pixelY: Int): Int {
        val tileX = pixelX / tileSize
        val tileY = pixelY / tileSize
        if (tileX < 0 || tileX >= tilesX || tileY < 0 || tileY >= tilesY) return -1
        return tileY * tilesX + tileX
    }
}

/** Represents a map object placed on the tilemap. */
data class MapObjectData(
    val id: String,
    val type: ObjectType,
    val x: Int,
    val y: Int,
    val properties: Map<String, Any> = emptyMap(),
) {
    /** Types of map objects. */
    enum class ObjectType(val displayName: String, val color: java.awt.Color) {
        CHEST("Chest", java.awt.Color(255, 215, 0)),
        DOOR("Door", java.awt.Color(139, 69, 19)),
        LEVER("Lever", java.awt.Color(128, 128, 128)),
        SIGN("Sign", java.awt.Color(210, 180, 140)),
        NPC("NPC", java.awt.Color(100, 149, 237)),
        SCONCE("Sconce", java.awt.Color(255, 140, 0)),
        SAVE_POINT("Save Point", java.awt.Color(50, 205, 50)),
        SPAWN("Spawn Point", java.awt.Color(0, 191, 255)),
        TRIGGER("Trigger", java.awt.Color(255, 0, 255, 128)),
    }
}

/** Represents an exit/connection between maps. */
data class ExitData(
    val id: String,
    val fromX: Int,
    val fromY: Int,
    val toMap: String,
    val toX: Int,
    val toY: Int,
    val direction: Direction = Direction.ANY,
) {
    /** Direction the player must be facing to trigger the exit. */
    enum class Direction {
        ANY,
        UP,
        DOWN,
        LEFT,
        RIGHT,
    }
}
