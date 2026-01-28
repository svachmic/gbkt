#!/usr/bin/env kotlin

/**
 * Asset Migration Script for Labyrinth of the Dragon
 *
 * Migrates assets from the original C game to gbkt-compatible format:
 * - Copies PNG sprites directly (already in correct format)
 * - Converts binary .tilemap files to TMX format
 * - Extracts palette information
 *
 * Usage: kotlin migrate-assets.main.kts
 *
 * Run from the LabyrinthOfTheDragon-port directory.
 */

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

// =============================================================================
// CONFIGURATION
// =============================================================================

val ORIGINAL_DIR = File("../LabyrinthOfTheDragon")
val PORT_DIR = File(".")
val ASSETS_SRC = File(ORIGINAL_DIR, "assets/tiles")
val TILEMAPS_SRC = File(ORIGINAL_DIR, "res/maps")
val ASSETS_DST = File(PORT_DIR, "res")

// Sprite mappings: original filename -> port path
val SPRITE_MAPPINGS = mapOf(
    // Hero sprite
    "hero.png" to "sprites/hero.png",

    // Monster sprites
    "kobold.png" to "monsters/kobold.png",
    "goblin.png" to "monsters/goblin.png",
    "zombie.png" to "monsters/zombie.png",
    "bugbear.png" to "monsters/bugbear.png",
    "owlbear.png" to "monsters/owlbear.png",
    "gelatinous_cube.png" to "monsters/gelatinous_cube.png",
    "displacer_beast.png" to "monsters/displacer_beast.png",
    "will_o_wisp.png" to "monsters/will_o_wisp.png",
    "deathknight.png" to "monsters/deathknight.png",
    "mindflayer.png" to "monsters/mindflayer.png",
    "beholder.png" to "monsters/beholder.png",
    "dragon.png" to "monsters/dragon.png",

    // Tilesets
    "dungeon.png" to "tiles/dungeon_tiles.png",
    "battle.png" to "tiles/battle_tiles.png",
    "objects.png" to "tiles/objects.png",
    "font.png" to "tiles/font.png",

    // UI
    "title_1.png" to "ui/title.png",
    "title_fire.png" to "ui/title_fire.png",
    "title_smoke.png" to "ui/title_smoke.png",

    // Combined monster sheet (for reference)
    "monsters.png" to "tiles/monsters_sheet.png",
)

// Floor tilemap mappings
val FLOOR_MAPPINGS = mapOf(
    "floor1.tilemap" to "maps/floor1.tmx",
    "floor2.tilemap" to "maps/floor2.tmx",
    "floor3.tilemap" to "maps/floor3.tmx",
    "floor4.tilemap" to "maps/floor4.tmx",
    "floor5.tilemap" to "maps/floor5.tmx",
    "floor6.tilemap" to "maps/floor6.tmx",
    "floor7.tilemap" to "maps/floor7.tmx",
    "floor8.tilemap" to "maps/floor8.tmx",
)

// =============================================================================
// MAIN
// =============================================================================

fun main() {
    println("╔════════════════════════════════════════════╗")
    println("║  Labyrinth of the Dragon Asset Migration   ║")
    println("╚════════════════════════════════════════════╝")
    println()

    // Verify source directories exist
    if (!ORIGINAL_DIR.exists()) {
        error("Original game not found at: ${ORIGINAL_DIR.absolutePath}")
    }

    // Create destination directories
    createDirectories()

    // Migrate assets
    val spriteCount = migrateSprites()
    val tilemapCount = migrateTilemaps()

    // Summary
    println()
    println("═══════════════════════════════════════════════")
    println("  Migration Complete!")
    println("═══════════════════════════════════════════════")
    println("  Sprites copied:   $spriteCount")
    println("  Tilemaps created: $tilemapCount")
    println()
    println("  Next steps:")
    println("  1. Review assets in res/ directory")
    println("  2. Update DSL references if paths changed")
    println("  3. Run ./gradlew :LabyrinthOfTheDragon-port:build")
}

// =============================================================================
// DIRECTORY SETUP
// =============================================================================

fun createDirectories() {
    println("Creating directories...")
    listOf(
        "res/sprites",
        "res/monsters",
        "res/tiles",
        "res/ui",
        "res/maps",
    ).forEach { dir ->
        val f = File(PORT_DIR, dir)
        if (!f.exists()) {
            f.mkdirs()
            println("  Created: $dir")
        }
    }
    println()
}

// =============================================================================
// SPRITE MIGRATION
// =============================================================================

fun migrateSprites(): Int {
    println("Migrating sprites...")
    var count = 0

    SPRITE_MAPPINGS.forEach { (src, dst) ->
        val srcFile = File(ASSETS_SRC, src)
        val dstFile = File(ASSETS_DST, dst)

        if (srcFile.exists()) {
            dstFile.parentFile.mkdirs()
            srcFile.copyTo(dstFile, overwrite = true)
            println("  ✓ $src → $dst")
            count++
        } else {
            println("  ✗ $src (not found)")
        }
    }
    println()
    return count
}

// =============================================================================
// TILEMAP MIGRATION
// =============================================================================

/**
 * Tilemap binary format (from original game):
 * - Each entry is 2 bytes: tile index (low byte) + attributes (high byte)
 * - Attributes include: palette bits, flip flags, priority
 * - Maps are stored row by row
 */
fun migrateTilemaps(): Int {
    println("Migrating tilemaps...")
    var count = 0

    FLOOR_MAPPINGS.forEach { (src, dst) ->
        val srcFile = File(TILEMAPS_SRC, src)
        val dstFile = File(ASSETS_DST, dst)

        if (srcFile.exists()) {
            try {
                val tmx = convertTilemapToTMX(srcFile)
                dstFile.parentFile.mkdirs()
                dstFile.writeText(tmx)
                println("  ✓ $src → $dst")
                count++
            } catch (e: Exception) {
                println("  ✗ $src (${e.message})")
            }
        } else {
            println("  ✗ $src (not found)")
        }
    }
    println()
    return count
}

/**
 * Convert binary tilemap to TMX format.
 *
 * The original format stores tile data as pairs of bytes:
 * - Byte 0: Tile index
 * - Byte 1: Attributes (palette, flip, etc.)
 *
 * TMX format is XML-based and more flexible.
 */
fun convertTilemapToTMX(file: File): String {
    val data = file.readBytes()

    // Detect map dimensions from file size
    // Original game uses 20x18 (360 tiles = 720 bytes) for full screen
    // Or variable sizes for floor maps
    val tileCount = data.size / 2
    val (width, height) = detectMapSize(tileCount, file.name)

    // Extract tile indices
    val tiles = mutableListOf<Int>()
    for (i in 0 until tileCount) {
        val tileIndex = data[i * 2].toInt() and 0xFF
        // Attributes in data[i * 2 + 1] - could extract palette/flip info
        tiles.add(tileIndex + 1) // TMX uses 1-based indices (0 = empty)
    }

    // Generate TMX XML
    return generateTMX(width, height, tiles, file.nameWithoutExtension)
}

fun detectMapSize(tileCount: Int, filename: String): Pair<Int, Int> {
    // Known sizes from original game
    return when {
        tileCount == 360 -> 20 to 18  // Full screen
        tileCount == 240 -> 20 to 12  // Half screen (textbox area)
        tileCount == 143 -> 11 to 13  // Example map
        filename.contains("floor") -> {
            // Floor maps vary - try common sizes
            when {
                tileCount == 1024 -> 32 to 32
                tileCount == 512 -> 32 to 16
                tileCount == 256 -> 16 to 16
                tileCount % 32 == 0 -> 32 to (tileCount / 32)
                tileCount % 20 == 0 -> 20 to (tileCount / 20)
                else -> {
                    // Best guess: square-ish
                    val side = kotlin.math.sqrt(tileCount.toDouble()).toInt()
                    side to (tileCount / side)
                }
            }
        }
        else -> {
            // Default to 20 wide
            if (tileCount % 20 == 0) 20 to (tileCount / 20)
            else 16 to (tileCount / 16)
        }
    }
}

fun generateTMX(width: Int, height: Int, tiles: List<Int>, name: String): String {
    val tileData = tiles.joinToString(",")

    return """<?xml version="1.0" encoding="UTF-8"?>
<map version="1.10" tiledversion="1.10.2" orientation="orthogonal"
     renderorder="right-down" width="$width" height="$height"
     tilewidth="8" tileheight="8" infinite="0">

 <tileset firstgid="1" name="dungeon" tilewidth="8" tileheight="8">
  <image source="../tiles/dungeon_tiles.png" width="128" height="192"/>
 </tileset>

 <layer id="1" name="$name" width="$width" height="$height">
  <data encoding="csv">
$tileData
  </data>
 </layer>
</map>
"""
}

// =============================================================================
// RUN
// =============================================================================

main()
