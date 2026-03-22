/**
 * Labyrinth of the Dragon - gbkt V2 Port
 *
 * Build configuration for the reference RPG implementation.
 * Uses the io.github.gbkt Gradle plugin for code generation and ROM compilation.
 *
 * V2 port: object-based entry point, canonical asset layout, GBC-only mode.
 */
plugins {
    kotlin("jvm") version "2.3.0"
    id("io.github.gbkt")
}

group = "io.github.gbkt.examples"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    // Use BOM for version coordination
    implementation(platform(project(":gbkt-bom")))

    // Backend brings in full chain: gbkt-backend-gbdk -> gbkt-backend-api -> gbkt-core
    // This enables ServiceLoader-based backend discovery
    implementation(project(":gbkt-backend-gbdk"))

    // RPG genre plugin — characters, monsters, abilities, battle system
    implementation(project(":gbkt-genre-rpg"))

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

// =============================================================================
// GBKT PLUGIN CONFIGURATION
// =============================================================================

gbkt {
    game("io.github.gbkt.examples.labyrinth.LabyrinthOfTheDragonKt::labyrinthOfTheDragon")
    assets("res")
    outputName.set("labyrinth")

    // GBC-only mode (required for color palettes)
    gbcMode.set("ONLY")

    // 4 RAM banks (32KB SRAM) - matches original game
    ramBanks.set(4)

    // Binary resources for INCBIN (tilemaps, tileset data)
    resourceDirectory.set(file("res"))
}

// =============================================================================
// ASSET MIGRATION TASK (port-specific utility)
// =============================================================================

tasks.register("migrateAssets") {
    group = "gbkt"
    description = "Migrate assets from original game to gbkt format"

    val originalDir = file("../LabyrinthOfTheDragon")
    val assetsSrc = file("$originalDir/assets/tiles")
    val tilemapsSrc = file("$originalDir/res/maps")
    val assetsDst = file("res")

    // Sprite mappings
    val spriteMappings = mapOf(
        "hero.png" to "sprites/hero.png",
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
        "dungeon.png" to "tiles/dungeon_tiles.png",
        "battle.png" to "tiles/battle_tiles.png",
        "objects.png" to "tiles/objects.png",
        "font.png" to "tiles/font.png",
        "title_1.png" to "ui/title.png",
        "title_fire.png" to "ui/title_fire.png",
        "title_smoke.png" to "ui/title_smoke.png",
        "monsters.png" to "tiles/monsters_sheet.png",
    )

    // Floor tilemap mappings
    val floorMappings = mapOf(
        "floor1.tilemap" to "maps/floor1.tmx",
        "floor2.tilemap" to "maps/floor2.tmx",
        "floor3.tilemap" to "maps/floor3.tmx",
        "floor4.tilemap" to "maps/floor4.tmx",
        "floor5.tilemap" to "maps/floor5.tmx",
        "floor6.tilemap" to "maps/floor6.tmx",
        "floor7.tilemap" to "maps/floor7.tmx",
        "floor8.tilemap" to "maps/floor8.tmx",
    )

    doFirst {
        if (!originalDir.exists()) {
            throw GradleException("Original game not found at: ${originalDir.absolutePath}")
        }
    }

    doLast {
        println("╔════════════════════════════════════════════╗")
        println("║  Labyrinth of the Dragon Asset Migration   ║")
        println("╚════════════════════════════════════════════╝")

        // Create directories
        listOf("sprites", "monsters", "tiles", "ui", "maps").forEach { dir ->
            file("$assetsDst/$dir").mkdirs()
        }

        // Migrate sprites
        var spriteCount = 0
        spriteMappings.forEach { (src, dst) ->
            val srcFile = file("$assetsSrc/$src")
            val dstFile = file("$assetsDst/$dst")
            if (srcFile.exists()) {
                srcFile.copyTo(dstFile, overwrite = true)
                println("  ✓ $src → $dst")
                spriteCount++
            } else {
                println("  ✗ $src (not found)")
            }
        }

        // Migrate tilemaps
        var tilemapCount = 0
        floorMappings.forEach { (src, dst) ->
            val srcFile = file("$tilemapsSrc/$src")
            val dstFile = file("$assetsDst/$dst")
            if (srcFile.exists()) {
                val tmx = convertTilemapToTMX(srcFile)
                dstFile.writeText(tmx)
                println("  ✓ $src → $dst")
                tilemapCount++
            } else {
                println("  ✗ $src (not found)")
            }
        }

        println()
        println("═══════════════════════════════════════════════")
        println("  Migration Complete!")
        println("  Sprites copied:   $spriteCount")
        println("  Tilemaps created: $tilemapCount")
        println("═══════════════════════════════════════════════")
    }
}

fun convertTilemapToTMX(file: File): String {
    val data = file.readBytes()
    val tileCount = data.size / 2

    // Detect map dimensions
    val (width, height) = when {
        tileCount == 360 -> 20 to 18
        tileCount == 240 -> 20 to 12
        tileCount % 32 == 0 -> 32 to (tileCount / 32)
        tileCount % 20 == 0 -> 20 to (tileCount / 20)
        else -> 16 to (tileCount / 16)
    }

    // Extract tile indices (TMX uses 1-based, 0 = empty)
    val tiles = (0 until tileCount).map { i ->
        (data[i * 2].toInt() and 0xFF) + 1
    }

    val tileData = tiles.chunked(width).joinToString("\n") { row ->
        row.joinToString(",")
    }

    return """<?xml version="1.0" encoding="UTF-8"?>
<map version="1.10" tiledversion="1.10.2" orientation="orthogonal"
     renderorder="right-down" width="$width" height="$height"
     tilewidth="8" tileheight="8" infinite="0">

 <tileset firstgid="1" name="dungeon" tilewidth="8" tileheight="8">
  <image source="../tiles/dungeon_tiles.png" width="128" height="192"/>
 </tileset>

 <layer id="1" name="${file.nameWithoutExtension}" width="$width" height="$height">
  <data encoding="csv">
$tileData
  </data>
 </layer>
</map>
"""
}

// =============================================================================
// DEBUG TASK (port-specific utility)
// =============================================================================

tasks.register("debugRom") {
    group = "gbkt"
    description = "Build and run ROM in mGBA with serial logging"
    dependsOn("buildRom")

    doLast {
        val romFile = layout.buildDirectory.file("gbkt/output/labyrinth.gb").get().asFile
        val logFile = layout.buildDirectory.file("debug_serial.log").get().asFile

        val mgba = listOf(
            "/Applications/mGBA.app/Contents/MacOS/mGBA",
            "/usr/local/bin/mgba",
            "${System.getProperty("user.home")}/Applications/mGBA.app/Contents/MacOS/mGBA",
        ).firstOrNull { file(it).exists() } ?: "mgba"

        logger.lifecycle("ROM: ${romFile.absolutePath}")
        logger.lifecycle("Log: ${logFile.absolutePath}")
        logger.lifecycle("Close mGBA to capture serial output.")

        val process = ProcessBuilder(mgba, "-l", "2048", romFile.absolutePath)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()

        logFile.writeText(output)

        if (output.isNotBlank()) {
            logger.lifecycle("\n=== SERIAL OUTPUT ===")
            logger.lifecycle(output)
            logger.lifecycle("=== END ===")
        } else {
            logger.lifecycle("No serial output captured.")
        }
    }
}
