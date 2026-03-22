/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
@file:Suppress(
    "TooManyFunctions", // Asset conversion has multiple per-format helpers
    "LongMethod", // convertSprite builds a multi-step pipeline per asset
    "TooGenericExceptionCaught", // PNG conversion wraps all tool exceptions
)

package io.github.gbkt.gradle.tasks

import io.github.gbkt.gradle.internal.GbdkToolchain
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations

/**
 * Task that converts sprite PNG assets to GBDK-compatible C tile data files using `png2asset`.
 *
 * This task bridges the v2 codegen pipeline's sprite asset includes with GBDK's native asset
 * format. The v2 pipeline emits `#include "sprites/paddle.h"` directives and `set_sprite_data(...,
 * sprites_paddle_tiles)` calls. This task produces the corresponding `.c` tile data file and a `.h`
 * header that declares the path-based name alias.
 *
 * Pipeline:
 * 1. Scans the generated C file ([mainCFile]) for sprite header include directives
 * 2. For each sprite include, locates the corresponding PNG in [assetDirectory]
 * 3. Runs `png2asset` to produce a C file with tile data (e.g. `paddle_tiles[]`)
 * 4. Generates a companion `.h` header that declares the path-based alias (e.g.
 *    `sprites_paddle_tiles`) matching the `set_sprite_data` call
 * 5. Places output in [cSourceDir] subdirectories for lcc compilation
 *
 * The generated `.c` file is compiled by [CompileRomTask] alongside `main.c` and `bank1.c`. The
 * `.h` header is included by `main.c` via the relative path `"sprites/paddle.h"`.
 */
@CacheableTask
abstract class ConvertSpritesTask @Inject constructor(private val execOperations: ExecOperations) :
    DefaultTask() {

    /** Path to GBDK installation directory (must contain `bin/png2asset`). */
    @get:Input abstract val gbdkHome: Property<String>

    /** Directory containing sprite asset PNGs (e.g. `res/sprites/`). */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetDirectory: DirectoryProperty

    /** The generated main.c file to scan for sprite includes. */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainCFile: RegularFileProperty

    /**
     * Directory where converted sprite C files are placed.
     *
     * Should be the same directory as [main.c] so lcc can find sprite headers via relative path.
     * Default: `build/gbkt/generated`.
     */
    @get:OutputDirectory abstract val cSourceDir: DirectoryProperty

    init {
        description = "Convert sprite PNG assets to GBDK C tile data using png2asset"
        group = "gbkt"
    }

    @TaskAction
    fun convertSprites() {
        val mainC = mainCFile.orNull?.asFile
        if (mainC == null || !mainC.exists()) {
            logger.lifecycle("ConvertSpritesTask: No main.c found — skipping sprite conversion")
            return
        }

        val assetDir = assetDirectory.orNull?.asFile
        if (assetDir == null || !assetDir.exists()) {
            logger.lifecycle("ConvertSpritesTask: No asset directory — skipping sprite conversion")
            return
        }

        val gbdkDir = File(gbdkHome.get())
        val png2assetExe = GbdkToolchain.getPng2asset(gbdkDir)
        if (!png2assetExe.exists()) {
            logger.warn(
                "ConvertSpritesTask: png2asset not found at ${png2assetExe.absolutePath} — skipping sprite conversion"
            )
            return
        }

        // Scan main.c for sprite header includes: #include "sprites/paddle.h"
        val spriteIncludes = parseSpriteIncludes(mainC)
        if (spriteIncludes.isEmpty()) {
            logger.lifecycle("ConvertSpritesTask: No sprite includes found in main.c")
            return
        }

        logger.lifecycle("ConvertSpritesTask: Found ${spriteIncludes.size} sprite include(s)")

        val sourceDir = cSourceDir.get().asFile
        for (includePath in spriteIncludes) {
            // includePath: "sprites/paddle.h" → relative path from cSourceDir
            val pngRelPath = includePath.removeSuffix(".h") + ".png"
            val pngFile = File(assetDir, pngRelPath)

            if (!pngFile.exists()) {
                logger.warn("ConvertSpritesTask: Sprite PNG not found: ${pngFile.absolutePath}")
                logger.warn("  Generating stub header for missing sprite: $includePath")
                generateStubHeader(pngRelPath, includePath, sourceDir)
                continue
            }

            convertSprite(pngFile, pngRelPath, includePath, sourceDir, png2assetExe)
        }
    }

    /**
     * Parse sprite header include directives from a generated C file.
     *
     * Matches `#include "subdir/name.h"` patterns (not system or game.h). Returns relative paths
     * like `["sprites/paddle.h", "sprites/ball.h"]`.
     */
    private fun parseSpriteIncludes(mainC: File): List<String> {
        val pattern = Regex("""^#include\s+"([^"]+\.h)"\s*$""")
        val systemHeaders = setOf("game.h")
        return mainC
            .readLines()
            .mapNotNull { line -> pattern.find(line)?.groupValues?.get(1) }
            .filter { path ->
                path !in systemHeaders &&
                    !path.startsWith("<") &&
                    path.contains("/") // Only subdirectory includes (e.g. sprites/paddle.h)
            }
    }

    /**
     * Convert a sprite PNG using png2asset and generate the companion header.
     *
     * Steps:
     * 1. Create output subdirectory in cSourceDir (e.g. `build/gbkt/generated/sprites/`)
     * 2. Run png2asset to produce `<name>.c` with native tile data array (e.g. `paddle_tiles`)
     * 3. Generate `<name>.h` declaring the path-based array alias for v2 pipeline compatibility
     * 4. The `.c` file will be compiled by CompileRomTask alongside main.c/bank1.c
     *
     * Array naming:
     * - png2asset generates: `paddle_tiles[]`
     * - v2 pipeline expects: `sprites_paddle_tiles[]`
     * - Generated header bridges with: `#define sprites_paddle_tiles paddle_tiles`
     */
    private fun convertSprite(
        pngFile: File,
        pngRelPath: String, // "sprites/paddle.png"
        includePath: String, // "sprites/paddle.h"
        sourceDir: File,
        png2assetExe: File,
    ) {
        // Output directories: build/gbkt/generated/sprites/
        val outputSubDir = File(sourceDir, includePath.substringBeforeLast('/'))
        outputSubDir.mkdirs()

        val stemName = pngFile.nameWithoutExtension // "paddle"
        val outputC = File(outputSubDir, "$stemName.c") // build/gbkt/generated/sprites/paddle.c
        val outputH = File(outputSubDir, "$stemName.h") // build/gbkt/generated/sprites/paddle.h

        // Derive the native array name (png2asset convention: just the filename)
        val nativeArrayName = "${stemName}_tiles"

        // Derive the v2 pipeline array name (path-based convention)
        val pathBasedArrayName =
            pngRelPath.substringBeforeLast('.').replace('/', '_').replace('-', '_') + "_tiles"

        logger.lifecycle("  Converting: ${pngFile.name} → ${outputC.name}")

        // Determine sprite mode based on file dimensions
        // png2asset defaults to 8x16 metasprites — pass -spr8x8 for 8x8 sprites
        val spriteMode = determineSpriteMode(pngFile)

        // Run png2asset to generate the C tile data file
        try {
            val args = mutableListOf(pngFile.absolutePath, "-o", outputC.absolutePath)
            when (spriteMode) {
                SpriteMode.SPR8x8 -> args.add("-spr8x8")
                SpriteMode.SPR8x16 -> {
                    /* default, no flag needed */
                }
            }

            val result =
                execOperations.exec {
                    executable = png2assetExe.absolutePath
                    setArgs(args)
                    isIgnoreExitValue = true
                }

            if (result.exitValue != 0) {
                logger.warn("  png2asset exited with code ${result.exitValue} for ${pngFile.name}")
                logger.warn("  Generating stub header for ${pngFile.name}")
                generateStubCFile(stemName, nativeArrayName, pathBasedArrayName, outputC)
            } else {
                // Post-process: fix zero-size arrays that fail to compile with lcc (C89/C99)
                // An all-transparent PNG produces `uint8_t name[0] = {}` which is not valid C
                fixZeroSizeArrays(outputC, stemName, nativeArrayName, pathBasedArrayName)
            }
        } catch (e: Exception) {
            logger.warn("  png2asset failed for ${pngFile.name}: ${e.message}")
            generateStubCFile(stemName, nativeArrayName, pathBasedArrayName, outputC)
        }

        // Generate companion header with v2 pipeline alias
        generateSpriteHeader(stemName, nativeArrayName, pathBasedArrayName, outputH)
        logger.lifecycle("    → ${outputH.name} (alias: $pathBasedArrayName)")
    }

    /**
     * Determine the sprite mode by checking the PNG height.
     *
     * Game Boy hardware supports 8x8 and 8x16 sprite sizes. png2asset needs to know which. We
     * default to 8x16 for sprites that are 16px or taller, 8x8 for shorter sprites.
     */
    private fun determineSpriteMode(pngFile: File): SpriteMode {
        return try {
            val stream = javax.imageio.ImageIO.createImageInputStream(pngFile)
            val reader = javax.imageio.ImageIO.getImageReaders(stream).next()
            reader.setInput(stream, true)
            val height = reader.getHeight(0)
            reader.dispose()
            stream.close()
            if (height < 16) SpriteMode.SPR8x8 else SpriteMode.SPR8x16
        } catch (e: Exception) {
            logger.warn("  Could not read PNG dimensions for ${pngFile.name}: ${e.message}")
            SpriteMode.SPR8x16
        }
    }

    /**
     * Generate a `.h` header file that:
     * 1. Declares the native array from png2asset output as `extern`
     * 2. Provides a `#define` alias mapping the v2 pipeline name to the native name
     *
     * This bridges the naming gap:
     * - png2asset generates `paddle_tiles` (filename-based)
     * - GBDKPipelineV2 calls `sprites_paddle_tiles` (path-based)
     */
    private fun generateSpriteHeader(
        stemName: String, // "paddle"
        nativeArrayName: String, // "paddle_tiles"
        pathBasedArrayName: String, // "sprites_paddle_tiles"
        outputH: File,
    ) {
        val guard = outputH.nameWithoutExtension.uppercase() + "_H"
        val content = buildString {
            appendLine("/* Auto-generated by gbkt ConvertSpritesTask — DO NOT EDIT */")
            appendLine("#ifndef ${guard}")
            appendLine("#define ${guard}")
            appendLine()
            appendLine("#include <stdint.h>")
            appendLine("#include <gbdk/platform.h>")
            appendLine()
            appendLine("/* Native tile data array from png2asset output */")
            appendLine("extern const uint8_t ${nativeArrayName}[];")
            appendLine()
            if (nativeArrayName != pathBasedArrayName) {
                appendLine("/* Path-based alias for GBDKPipelineV2 set_sprite_data() calls */")
                appendLine("#define ${pathBasedArrayName} ${nativeArrayName}")
                appendLine()
            }
            appendLine("#endif /* ${guard} */")
        }
        outputH.writeText(content)
    }

    /**
     * Generate a stub `.c` file with an empty tile data array.
     *
     * Used as fallback when png2asset fails or the PNG is missing. The stub provides a compilable
     * placeholder — sprites will appear as empty (invisible).
     */
    private fun generateStubCFile(
        stemName: String,
        nativeArrayName: String,
        pathBasedArrayName: String,
        outputC: File,
    ) {
        // Generate 64 bytes (4 tiles) to cover common metasprite sizes up to 16x16.
        // Excess tiles are harmless — set_sprite_data only reads what it needs.
        val content = buildString {
            appendLine("/* Stub sprite data — generated by gbkt ConvertSpritesTask */")
            appendLine("/* Replace with a valid PNG to get real sprite graphics */")
            appendLine("#include <stdint.h>")
            appendLine("#include <gbdk/platform.h>")
            appendLine()
            appendLine("const uint8_t ${nativeArrayName}[64] = {")
            appendLine("    /* placeholder tiles (4x 8x8 tiles, checkerboard pattern) */")
            appendLine("    0xAA, 0xAA, 0x55, 0x55, 0xAA, 0xAA, 0x55, 0x55,")
            appendLine("    0xAA, 0xAA, 0x55, 0x55, 0xAA, 0xAA, 0x55, 0x55,")
            appendLine("    0xAA, 0xAA, 0x55, 0x55, 0xAA, 0xAA, 0x55, 0x55,")
            appendLine("    0xAA, 0xAA, 0x55, 0x55, 0xAA, 0xAA, 0x55, 0x55,")
            appendLine("    0xAA, 0xAA, 0x55, 0x55, 0xAA, 0xAA, 0x55, 0x55,")
            appendLine("    0xAA, 0xAA, 0x55, 0x55, 0xAA, 0xAA, 0x55, 0x55,")
            appendLine("    0xAA, 0xAA, 0x55, 0x55, 0xAA, 0xAA, 0x55, 0x55,")
            appendLine("    0xAA, 0xAA, 0x55, 0x55, 0xAA, 0xAA, 0x55, 0x55")
            appendLine("};")
        }
        outputC.writeText(content)
    }

    /**
     * Generate a stub header for a sprite whose PNG could not be found.
     *
     * Provides a compilable `.h` with an empty tile data array inline (not extern).
     */
    private fun generateStubHeader(
        pngRelPath: String, // "sprites/paddle.png"
        includePath: String, // "sprites/paddle.h"
        sourceDir: File,
    ) {
        val stemName = File(pngRelPath).nameWithoutExtension
        val nativeArrayName = "${stemName}_tiles"
        val pathBasedArrayName =
            pngRelPath.substringBeforeLast('.').replace('/', '_').replace('-', '_') + "_tiles"

        val outputSubDir = File(sourceDir, includePath.substringBeforeLast('/'))
        outputSubDir.mkdirs()
        val outputH = File(outputSubDir, "$stemName.h")
        val outputC = File(outputSubDir, "$stemName.c")

        generateStubCFile(stemName, nativeArrayName, pathBasedArrayName, outputC)
        generateSpriteHeader(stemName, nativeArrayName, pathBasedArrayName, outputH)
    }

    /**
     * Fix zero-size arrays in png2asset output that fail to compile with lcc (C89/C99).
     *
     * An all-transparent PNG produces `const uint8_t name[0] = {}` which is invalid C before ISO
     * C23. When detected, the tile array is replaced with a 16-byte placeholder so the file
     * compiles and links correctly. Sprites will appear invisible (all-zero).
     */
    private fun fixZeroSizeArrays(
        outputC: File,
        stemName: String,
        nativeArrayName: String,
        pathBasedArrayName: String,
    ) {
        if (!outputC.exists()) return
        val content = outputC.readText()
        // Detect zero-size tile array: `name[0] = {` or `name[0]={`
        val zeroArrayPattern = Regex("""${Regex.escape(nativeArrayName)}\s*\[\s*0\s*\]""")
        if (zeroArrayPattern.containsMatchIn(content)) {
            logger.lifecycle("    Zero-size tile array detected — replacing with 16-byte stub")
            generateStubCFile(stemName, nativeArrayName, pathBasedArrayName, outputC)
        }
    }

    private enum class SpriteMode {
        SPR8x8,
        SPR8x16,
    }
}
