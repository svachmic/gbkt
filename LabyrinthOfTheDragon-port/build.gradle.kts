/**
 * Labyrinth of the Dragon - gbkt Port
 *
 * Build configuration for the reference RPG implementation.
 */
plugins {
    kotlin("jvm") version "2.3.0"
    application
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

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("io.github.gbkt.examples.labyrinth.GenerateCKt")
}

// =============================================================================
// GBKT BUILD CONFIGURATION
// =============================================================================

val outputDir = layout.buildDirectory.dir("generated/gbdk")
val romOutputFile = outputDir.map { it.file("labyrinth.gbc") }

// Detect GBDK location - check environment variable or common paths
val gbdkHome: String? = System.getenv("GBDK_HOME")
    ?: listOf(
        "/opt/gbdk",
        "/usr/local/gbdk",
        "${System.getProperty("user.home")}/gbdk",
        "${System.getProperty("user.home")}/opt/gbdk",
        "${System.getProperty("user.home")}/Library/gbdk",  // macOS
        "C:/gbdk",  // Windows
    ).firstOrNull { file(it).exists() }

// =============================================================================
// CODE GENERATION TASK
// =============================================================================

tasks.register<JavaExec>("generateC") {
    group = "gbkt"
    description = "Generate C code from Kotlin DSL"
    dependsOn("classes")

    mainClass.set("io.github.gbkt.examples.labyrinth.GenerateCKt")
    classpath = sourceSets["main"].runtimeClasspath

    // Pass output directory path as argument
    args = listOf(outputDir.get().asFile.absolutePath)

    doFirst {
        outputDir.get().asFile.mkdirs()
    }

    outputs.dir(outputDir)
}

// =============================================================================
// ROM BUILD TASK (requires GBDK)
// =============================================================================

tasks.register("buildRom") {
    group = "gbkt"
    description = "Build the GBC ROM file using GBDK"
    dependsOn("generateC")

    inputs.dir(outputDir)
    outputs.file(romOutputFile)

    doLast {
        val srcDir = outputDir.get().asFile
        val lcc = if (gbdkHome != null) {
            file("$gbdkHome/bin/lcc").absolutePath
        } else {
            "lcc" // Assume it's in PATH
        }

        if (gbdkHome == null) {
            logger.warn("GBDK_HOME not set. Assuming 'lcc' is in PATH.")
            logger.warn("Set GBDK_HOME environment variable for reliable builds.")
        } else {
            logger.lifecycle("Using GBDK from: $gbdkHome")
        }

        // Find all .c files and sort them (main.c first, then bank files)
        val cFiles = srcDir.listFiles { f -> f.extension == "c" }
            ?.sortedBy { f ->
                when {
                    f.name == "main.c" -> 0
                    f.name.startsWith("bank") -> {
                        f.name.removePrefix("bank").removeSuffix(".c").toIntOrNull() ?: 999
                    }
                    else -> 1000
                }
            }
            ?: emptyList()

        if (cFiles.isEmpty()) {
            throw GradleException("No C source files found in ${srcDir.absolutePath}")
        }

        logger.lifecycle("Compiling ${cFiles.size} source files...")
        cFiles.forEach { f -> logger.lifecycle("  - ${f.name}") }

        val args = mutableListOf(
            lcc,
            "-Wa-l",           // Generate listing
            "-Wl-m",           // Generate map file
            "-Wl-j",           // Generate symbol file
            "-DUSE_SFR_FOR_REG",
            "-msm83:gb",       // Target Game Boy
            "-Wl-yt0x1B",      // MBC5 with RAM and battery
            "-Wl-yo32",        // 32 ROM banks (512KB) - matches original
            "-Wl-ya4",         // 4 RAM banks (32KB SRAM) - matches original
            "-o", romOutputFile.get().asFile.name,
        )
        args.addAll(cFiles.map { it.name })

        val process = ProcessBuilder(args)
            .directory(srcDir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (output.isNotBlank()) {
            println(output)
        }

        if (exitCode != 0) {
            throw GradleException("GBDK compilation failed with exit code $exitCode")
        }

        logger.lifecycle("ROM built: ${romOutputFile.get().asFile.absolutePath}")
    }
}

// =============================================================================
// ASSET MIGRATION TASK
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
// EMULATOR TASK
// =============================================================================

tasks.register("runEmulator") {
    group = "gbkt"
    description = "Run the ROM in an emulator"
    dependsOn("buildRom")

    doLast {
        val romFile = romOutputFile.get().asFile
        logger.lifecycle("ROM built: ${romFile.absolutePath}")
        // macOS: open with default app
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("mac")) {
            ProcessBuilder("open", romFile.absolutePath).start()
        } else {
            logger.lifecycle("Run with your preferred emulator (e.g., mgba, bgb)")
        }
    }
}
