/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.graphics.Sprite
import io.github.gbkt.core.ir.GBCColor
import io.github.gbkt.core.ir.GBCPalette
import io.github.gbkt.core.ir.PaletteType
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Asset Pipeline - Converts PNG images to Game Boy tile data.
 *
 * Game Boy tiles are 8x8 pixels, 2 bits per pixel (4 colors). Each tile is 16 bytes: 2 bytes per
 * row, with bit planes interleaved.
 *
 * Usage: val tiles = AssetPipeline.loadSprite("player.png") val cCode =
 * AssetPipeline.generateTileData("player_tiles", tiles)
 */
object AssetPipeline {

    /** Game Boy palette - maps to 4 shades. 0 = lightest (white), 3 = darkest (black) */
    data class GBColor(val shade: Int) {
        init {
            require(shade in 0..3) { "GB color must be 0-3" }
        }
    }

    /** A single 8x8 tile in GB format. */
    data class Tile(val data: ByteArray) {
        init {
            require(data.size == 16) { "Tile must be 16 bytes" }
        }

        override fun equals(other: Any?) = other is Tile && data.contentEquals(other.data)

        override fun hashCode() = data.contentHashCode()
    }

    /** Sprite sheet containing multiple tiles. */
    data class SpriteSheet(val tiles: List<Tile>, val widthInTiles: Int, val heightInTiles: Int)

    /**
     * Load a PNG and convert to GB tiles.
     *
     * @param path Path to PNG file
     * @param palette Optional custom palette mapping (luminance thresholds)
     * @return SpriteSheet with converted tiles
     */
    fun loadSprite(path: String, palette: IntArray = DEFAULT_PALETTE): SpriteSheet {
        val file = File(path)
        require(file.exists()) { "Asset not found: $path" }

        val image = ImageIO.read(file)
        return convertImage(image, palette)
    }

    /** Load from a File object. */
    fun loadSprite(file: File, palette: IntArray = DEFAULT_PALETTE): SpriteSheet {
        require(file.exists()) { "Asset not found: ${file.path}" }
        val image = ImageIO.read(file)
        return convertImage(image, palette)
    }

    /** Convert a BufferedImage to GB tiles. */
    fun convertImage(image: BufferedImage, palette: IntArray = DEFAULT_PALETTE): SpriteSheet {
        val width = image.width
        val height = image.height

        // Dimensions must be multiples of 8
        require(width % 8 == 0) { "Image width must be multiple of 8, got $width" }
        require(height % 8 == 0) { "Image height must be multiple of 8, got $height" }

        val tilesWide = width / 8
        val tilesHigh = height / 8
        val tiles = mutableListOf<Tile>()

        // Convert each 8x8 block to a tile
        for (ty in 0 until tilesHigh) {
            for (tx in 0 until tilesWide) {
                val tile = convertTile(image, tx * 8, ty * 8, palette)
                tiles.add(tile)
            }
        }

        return SpriteSheet(tiles, tilesWide, tilesHigh)
    }

    /** Convert an 8x8 region of an image to a GB tile. */
    private fun convertTile(
        image: BufferedImage,
        startX: Int,
        startY: Int,
        palette: IntArray
    ): Tile {
        val data = ByteArray(16)

        for (row in 0 until 8) {
            var lowByte = 0
            var highByte = 0

            for (col in 0 until 8) {
                val rgb = image.getRGB(startX + col, startY + row)
                val gbColor = rgbToGBColor(rgb, palette)

                // GB format: bit 0 in low byte, bit 1 in high byte
                // Pixels are MSB first (leftmost pixel in bit 7)
                val bit = 7 - col
                if (gbColor and 1 != 0) lowByte = lowByte or (1 shl bit)
                if (gbColor and 2 != 0) highByte = highByte or (1 shl bit)
            }

            data[row * 2] = lowByte.toByte()
            data[row * 2 + 1] = highByte.toByte()
        }

        return Tile(data)
    }

    /** Convert RGB to GB color (0-3). Uses luminance to determine shade. */
    private fun rgbToGBColor(rgb: Int, palette: IntArray): Int {
        val a = (rgb shr 24) and 0xFF
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF

        // Transparent pixels map to color 0 (usually transparent in sprites)
        if (a < 128) return 0

        // Calculate luminance (perceived brightness)
        val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

        // Map to GB color based on thresholds
        return when {
            luminance >= palette[0] -> 0 // Lightest
            luminance >= palette[1] -> 1
            luminance >= palette[2] -> 2
            else -> 3 // Darkest
        }
    }

    /**
     * Generate C code for tile data.
     *
     * @param name Variable name for the tile data
     * @param sheet The sprite sheet to convert
     * @return C code declaring the tile data array
     */
    fun generateTileData(name: String, sheet: SpriteSheet): String {
        val sb = StringBuilder()

        sb.appendLine("// Tile data for $name (${sheet.widthInTiles}x${sheet.heightInTiles} tiles)")
        sb.appendLine("const unsigned char ${name}[] = {")

        for ((index, tile) in sheet.tiles.withIndex()) {
            sb.append("    ")
            sb.append(
                tile.data.joinToString(", ") {
                    "0x${(it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()}"
                }
            )
            if (index < sheet.tiles.size - 1) sb.append(",")
            sb.appendLine()
        }

        sb.appendLine("};")
        sb.appendLine()
        sb.appendLine("#define ${name.uppercase()}_TILE_COUNT ${sheet.tiles.size}")

        return sb.toString()
    }

    /** Generate C code for multiple sprites. */
    fun generateAllTileData(sprites: Map<String, SpriteSheet>): String {
        val sb = StringBuilder()
        sb.appendLine("// === Sprite Tile Data ===")
        sb.appendLine("// Generated by gbkt asset pipeline")
        sb.appendLine()

        for ((name, sheet) in sprites) {
            sb.appendLine(generateTileData(name, sheet))
        }

        return sb.toString()
    }

    /**
     * Default palette thresholds for luminance mapping. Values are luminance thresholds:
     * [light, medium-light, medium-dark] Anything below the last threshold becomes darkest.
     */
    val DEFAULT_PALETTE = intArrayOf(192, 128, 64)

    /** High contrast palette - good for pixel art with clear colors. */
    val HIGH_CONTRAST_PALETTE = intArrayOf(200, 140, 80)

    /** Inverted palette - swaps light and dark. */
    val INVERTED_PALETTE = intArrayOf(64, 128, 192)

    // =========================================================================
    // GBC COLOR SUPPORT
    // =========================================================================

    /** Sprite sheet with optional extracted GBC palette. */
    data class SpriteSheetGBC(
        val tiles: List<Tile>,
        val widthInTiles: Int,
        val heightInTiles: Int,
        val extractedPalette: GBCPalette?,
        val colorCount: Int
    )

    /**
     * Extract dominant colors from an image and create a GBC palette. Uses frequency-based color
     * selection.
     *
     * @param image Source image
     * @param name Palette name
     * @param maxColors Maximum colors to extract (default 4)
     * @return GBCPalette with extracted colors
     */
    fun extractPalette(image: BufferedImage, name: String, maxColors: Int = 4): GBCPalette {
        val colorCounts = mutableMapOf<Int, Int>()

        // Count all unique non-transparent colors
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                val alpha = (rgb shr 24) and 0xFF
                if (alpha >= 128) { // Skip transparent pixels
                    val color = rgb and 0xFFFFFF
                    colorCounts[color] = colorCounts.getOrDefault(color, 0) + 1
                }
            }
        }

        // Sort by frequency, take top colors
        val sortedColors =
            colorCounts.entries
                .sortedByDescending { it.value }
                .take(maxColors)
                .map { GBCColor.fromHex(it.key) }

        // Ensure we have exactly 4 colors (pad with grayscale if needed)
        val colors = sortedColors.toMutableList()
        if (colors.isEmpty()) colors.add(GBCColor.WHITE)
        while (colors.size < 4) {
            // Add grayscale colors based on what we have
            val luminanceStep = 255 / (4 - colors.size + 1)
            val lum = 255 - (colors.size * luminanceStep)
            colors.add(GBCColor.fromRGB888(lum, lum, lum))
        }

        // Sort by luminance (lightest first, as is GB convention)
        colors.sortByDescending { color ->
            // Convert RGB555 back to approximate luminance
            val r = (color.red shl 3) or (color.red shr 2)
            val g = (color.green shl 3) or (color.green shr 2)
            val b = (color.blue shl 3) or (color.blue shr 2)
            (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        return GBCPalette(name, colors.take(4))
    }

    /** Extract palette from a PNG file. */
    fun extractPalette(path: String, name: String): GBCPalette {
        val file = File(path)
        require(file.exists()) { "Asset not found: $path" }
        val image = ImageIO.read(file)
        return extractPalette(image, name)
    }

    /** Count unique non-transparent colors in an image. */
    fun countUniqueColors(image: BufferedImage): Int {
        val colors = mutableSetOf<Int>()
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                val alpha = (rgb shr 24) and 0xFF
                if (alpha >= 128) {
                    colors.add(rgb and 0xFFFFFF)
                }
            }
        }
        return colors.size
    }

    /**
     * Load sprite with GBC palette extraction. If the image has 4 or fewer colors, uses them
     * directly. If more, extracts the 4 most frequent colors.
     *
     * @param path Path to PNG file
     * @param paletteName Name for the extracted palette
     * @return SpriteSheetGBC with tiles and extracted palette
     */
    fun loadSpriteGBC(path: String, paletteName: String): SpriteSheetGBC {
        val file = File(path)
        require(file.exists()) { "Asset not found: $path" }
        val image = ImageIO.read(file)
        return convertImageGBC(image, paletteName)
    }

    /**
     * Convert image to GB tiles with GBC palette extraction.
     *
     * @param image Source image
     * @param paletteName Name for the extracted palette
     * @param targetPalette Optional target palette (if provided, maps colors to it)
     * @return SpriteSheetGBC with tiles and palette info
     */
    fun convertImageGBC(
        image: BufferedImage,
        paletteName: String,
        targetPalette: GBCPalette? = null
    ): SpriteSheetGBC {
        val width = image.width
        val height = image.height

        require(width % 8 == 0) { "Image width must be multiple of 8, got $width" }
        require(height % 8 == 0) { "Image height must be multiple of 8, got $height" }

        val colorCount = countUniqueColors(image)
        val palette = targetPalette ?: extractPalette(image, paletteName)

        if (colorCount > 4) {
            println("  Warning: Image has $colorCount colors, quantizing to 4")
        }

        val tilesWide = width / 8
        val tilesHigh = height / 8
        val tiles = mutableListOf<Tile>()

        // Convert each 8x8 block to a tile using palette color mapping
        for (ty in 0 until tilesHigh) {
            for (tx in 0 until tilesWide) {
                val tile = convertTileGBC(image, tx * 8, ty * 8, palette)
                tiles.add(tile)
            }
        }

        return SpriteSheetGBC(tiles, tilesWide, tilesHigh, palette, colorCount)
    }

    /**
     * Convert an 8x8 region using GBC palette color mapping. Each pixel is mapped to the nearest
     * color in the palette.
     */
    private fun convertTileGBC(
        image: BufferedImage,
        startX: Int,
        startY: Int,
        palette: GBCPalette
    ): Tile {
        val data = ByteArray(16)

        for (row in 0 until 8) {
            var lowByte = 0
            var highByte = 0

            for (col in 0 until 8) {
                val rgb = image.getRGB(startX + col, startY + row)
                val paletteIndex = rgbToPaletteIndex(rgb, palette)

                // GB format: bit 0 in low byte, bit 1 in high byte
                val bit = 7 - col
                if (paletteIndex and 1 != 0) lowByte = lowByte or (1 shl bit)
                if (paletteIndex and 2 != 0) highByte = highByte or (1 shl bit)
            }

            data[row * 2] = lowByte.toByte()
            data[row * 2 + 1] = highByte.toByte()
        }

        return Tile(data)
    }

    /** Map RGB color to nearest palette index using Euclidean distance. */
    private fun rgbToPaletteIndex(rgb: Int, palette: GBCPalette): Int {
        val a = (rgb shr 24) and 0xFF
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF

        // Transparent pixels map to color 0
        if (a < 128) return 0

        var bestIndex = 0
        var bestDistance = Int.MAX_VALUE

        for ((index, color) in palette.colors.withIndex()) {
            // Convert RGB555 back to RGB888 for comparison
            val pr = (color.red shl 3) or (color.red shr 2)
            val pg = (color.green shl 3) or (color.green shr 2)
            val pb = (color.blue shl 3) or (color.blue shr 2)

            // Euclidean distance in RGB space
            val dist = (r - pr) * (r - pr) + (g - pg) * (g - pg) + (b - pb) * (b - pb)
            if (dist < bestDistance) {
                bestDistance = dist
                bestIndex = index
            }
        }

        return bestIndex
    }

    /** Generate C code for GBC palette data. */
    fun generatePaletteData(palette: GBCPalette): String {
        val sb = StringBuilder()
        sb.appendLine("// GBC Palette: ${palette.name}")
        sb.appendLine("const UINT16 ${palette.name}_pal[] = { ${palette.toCArrayLiteral()} };")
        return sb.toString()
    }
}

/** Extension to load sprite and get both the Sprite object and tile data. */
fun loadSpriteWithTiles(
    path: String,
    slot: Int,
    palette: IntArray = AssetPipeline.DEFAULT_PALETTE
): Pair<Sprite, AssetPipeline.SpriteSheet> {
    val sheet = AssetPipeline.loadSprite(path)
    val sprite =
        Sprite(
            name = File(path).nameWithoutExtension,
            asset = path,
            width = sheet.widthInTiles * 8,
            height = sheet.heightInTiles * 8,
            oamSlot = slot
        )
    return sprite to sheet
}

/**
 * Load and compile a Tiled map file.
 *
 * @param mapPath Path to the Tiled JSON file
 * @param tilesetPath Optional override for tileset image path
 * @param layerName Optional specific layer name to use
 * @param collisionLayerName Optional collision layer name to use
 * @return Pair of CompiledMapData and CompiledTileData for the tileset
 */
fun loadTileMap(
    mapPath: String,
    tilesetPath: String? = null,
    layerName: String? = null,
    collisionLayerName: String? = null
): Pair<CompiledMapData, CompiledTileData?> {
    val mapFile = File(mapPath)
    require(mapFile.exists()) { "Tiled map not found: $mapPath" }

    val map = TiledParser.parse(mapFile)

    // Get the appropriate layer
    val layer =
        if (layerName != null) {
            map.getLayer(layerName)
                ?: throw IllegalArgumentException("Layer '$layerName' not found in map")
        } else {
            map.getVisibleLayer() ?: throw IllegalArgumentException("No visible tile layers in map")
        }

    val tileset =
        map.tilesets.firstOrNull() ?: throw IllegalArgumentException("No tilesets found in map")

    // Normalize layer data (convert GIDs to 0-based indices)
    val normalizedData = TiledParser.normalizeLayer(layer, tileset.firstGid)

    // Parse collision layer if specified
    val collisionData: ByteArray? =
        if (collisionLayerName != null) {
            val collisionLayer =
                map.getLayer(collisionLayerName)
                    ?: throw IllegalArgumentException(
                        "Collision layer '$collisionLayerName' not found in map"
                    )
            // Validate collision layer dimensions match visual layer
            require(collisionLayer.width == layer.width && collisionLayer.height == layer.height) {
                "Collision layer dimensions (${collisionLayer.width}x${collisionLayer.height}) " +
                    "must match visual layer dimensions (${layer.width}x${layer.height})"
            }
            // Normalize collision layer data (non-zero tiles = blocked)
            TiledParser.normalizeLayer(collisionLayer, tileset.firstGid)
        } else {
            null
        }

    val mapName = mapFile.nameWithoutExtension.replace(Regex("[^a-zA-Z0-9_]"), "_")

    val compiledMap =
        CompiledMapData(
            name = mapName,
            width = layer.width,
            height = layer.height,
            data = normalizedData,
            tilesetName = tileset.name,
            collisionData = collisionData
        )

    // Load tileset if available
    val compiledTileset =
        if (tileset.image.isNotEmpty()) {
            val actualTilesetPath = tilesetPath ?: tileset.image
            val tilesetFile = File(actualTilesetPath)
            if (tilesetFile.exists()) {
                try {
                    val sheet = AssetPipeline.loadSprite(tilesetFile)
                    CompiledTileData(
                        name = "${mapName}_tileset",
                        data = sheet.tiles.map { it.data },
                        tileCount = sheet.tiles.size
                    )
                } catch (e: Exception) {
                    println("  Warning: Failed to load tileset: ${e.message}")
                    null
                }
            } else {
                println("  Warning: Tileset not found: $actualTilesetPath")
                null
            }
        } else null

    return compiledMap to compiledTileset
}

/**
 * Compile a game with automatic asset processing.
 *
 * Usage: val code = compileWithAssets(myGame, "src/main/resources/sprites")
 *
 * This will:
 * 1. Find all sprite PNG files in the asset directory
 * 2. Convert them to GB tile format
 * 3. Load and convert Tiled map files
 * 4. Include the tile/map data in the generated C code
 */
fun compileWithAssets(game: Game, assetDir: String? = game.assetDir): String {
    if (assetDir == null) {
        return game.compile()
    }

    val dir = File(assetDir)
    if (!dir.exists() || !dir.isDirectory) {
        println("Warning: Asset directory not found: $assetDir")
        return game.compile()
    }

    // Convert each sprite's asset to tile data
    val tileDataList = mutableListOf<CompiledTileData>()

    for (sprite in game.sprites) {
        val assetFile = File(dir, sprite.asset)
        if (assetFile.exists()) {
            try {
                val sheet = AssetPipeline.loadSprite(assetFile)
                val name =
                    sprite.asset.substringBeforeLast(".").replace("/", "_").replace("\\", "_")
                tileDataList.add(
                    CompiledTileData(
                        name = name,
                        data = sheet.tiles.map { it.data },
                        tileCount = sheet.tiles.size
                    )
                )
                println("  Converted: ${sprite.asset} (${sheet.tiles.size} tiles)")
            } catch (e: Exception) {
                println("  Warning: Failed to convert ${sprite.asset}: ${e.message}")
            }
        } else {
            println("  Warning: Asset not found: ${sprite.asset}")
        }
    }

    // Convert each tilemap
    val mapDataList = mutableListOf<CompiledMapData>()

    for (tilemap in game.tilemaps) {
        val mapFile = File(dir, tilemap.asset)
        if (mapFile.exists()) {
            try {
                val tilesetPath = tilemap.tilesetAsset?.let { File(dir, it).path }
                val (mapData, tilesetData) =
                    loadTileMap(
                        mapFile.path,
                        tilesetPath,
                        tilemap.layerName,
                        tilemap.collisionLayerName
                    )
                mapDataList.add(mapData)

                // Add tileset if loaded
                if (tilesetData != null) {
                    tileDataList.add(tilesetData)
                }

                println("  Converted: ${tilemap.asset} (${mapData.width}x${mapData.height} tiles)")
            } catch (e: Exception) {
                println("  Warning: Failed to convert ${tilemap.asset}: ${e.message}")
            }
        } else {
            println("  Warning: Map not found: ${tilemap.asset}")
        }
    }

    // Extract palettes from sprite assets if GBC mode is enabled
    val extractedPalettes = mutableListOf<GBCPalette>()
    if (game.config.gbcSupport) {
        var paletteSlot = 0
        for (sprite in game.sprites) {
            // Only extract if sprite doesn't already have a palette assigned
            if (sprite.paletteRef == null && sprite.paletteIndex == 0) {
                val assetFile = File(dir, sprite.asset)
                if (assetFile.exists()) {
                    try {
                        val paletteName = sprite.name.replace("sprite_", "") + "_pal"
                        val palette = AssetPipeline.extractPalette(assetFile.path, paletteName)
                        // Assign a slot
                        val assignedPalette =
                            GBCPalette(
                                name = paletteName,
                                colors = palette.colors,
                                slot = paletteSlot++,
                                type = PaletteType.SPRITE
                            )
                        extractedPalettes.add(assignedPalette)
                        println("  Extracted palette: $paletteName (${palette.colors.size} colors)")
                    } catch (e: Exception) {
                        println(
                            "  Warning: Failed to extract palette from ${sprite.asset}: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    // Merge extracted palettes with explicitly defined palettes
    val allPalettes = game.palettes + extractedPalettes

    // Create new game with all asset data
    val gameWithAssets =
        Game(
            name = game.name,
            config = game.config,
            variables = game.variables,
            sprites = game.sprites,
            entities = game.entities,
            pools = game.pools,
            particleSystems = game.particleSystems,
            tilemaps = game.tilemaps,
            soundEffects = game.soundEffects,
            music = game.music,
            scenes = game.scenes,
            startScene = game.startScene,
            assetDir = assetDir,
            tileData = tileDataList,
            mapData = mapDataList,
            palettes = allPalettes,
            stateMachines = game.stateMachines,
            saveData = game.saveData,
            dialogs = game.dialogs,
            menus = game.menus,
            camera = game.camera,
            physicsWorld = game.physicsWorld,
            navGrids = game.navGrids,
            inputBuffers = game.inputBuffers,
            audioMixer = game.audioMixer,
            link = game.link,
            cutscenes = game.cutscenes
        )

    return gameWithAssets.compile(warnOnValidationErrors = true)
}

/**
 * Generate a simple test sprite PNG programmatically. Useful for testing without requiring actual
 * assets.
 */
fun generateTestSprite(path: String, width: Int = 8, height: Int = 8) {
    val image =
        java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()

    // Simple checkerboard pattern
    for (y in 0 until height) {
        for (x in 0 until width) {
            val shade =
                when {
                    (x + y) % 4 == 0 -> 0xFFFFFFFF.toInt() // White
                    (x + y) % 4 == 1 -> 0xFFAAAAAA.toInt() // Light gray
                    (x + y) % 4 == 2 -> 0xFF555555.toInt() // Dark gray
                    else -> 0xFF000000.toInt() // Black
                }
            image.setRGB(x, y, shade)
        }
    }

    g.dispose()

    val file = File(path)
    file.parentFile?.mkdirs()
    ImageIO.write(image, "PNG", file)
}
