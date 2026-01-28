/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.labyrinth

import io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator
import java.io.File

/**
 * Code generation entry point for Labyrinth of the Dragon.
 *
 * This generates GBDK-compatible C code from the Kotlin DSL game definition. Uses multi-file
 * generation to split code by bank (GBDK-2020 requires separate source files for different bank
 * assignments).
 *
 * Usage: ./gradlew :LabyrinthOfTheDragon-port:generateC
 *
 * Or run directly: ./gradlew :LabyrinthOfTheDragon-port:run --args="output/"
 */
fun main(args: Array<String>) {
    println("========================================")
    println("  gbkt Code Generator")
    println("  Labyrinth of the Dragon")
    println("========================================")
    println()

    // Compile the game to C code (multi-file for proper bank support)
    println("Compiling Kotlin DSL to C code...")
    val startTime = System.currentTimeMillis()

    @Suppress("TooGenericExceptionCaught")
    val files: Map<String, String> =
        try {
            // Use multi-file generation to split code by bank
            // GBDK-2020 doesn't support multiple #pragma bank directives in a single file
            println("Loading and converting assets from 'res/' directory...")
            val generator = GBDKCodeGenerator(labyrinthOfTheDragon)
            generator.generateMultiFile()
        } catch (e: Exception) {
            System.err.println("ERROR: Code generation failed!")
            System.err.println(e.message)
            // Log cause chain for debugging
            var cause = e.cause
            while (cause != null) {
                System.err.println("  Caused by: ${cause.message}")
                cause = cause.cause
            }
            System.exit(1)
            return
        }

    val duration = System.currentTimeMillis() - startTime
    val totalLines = files.values.sumOf { it.lines().size }
    println("Code generation completed in ${duration}ms")
    println("Generated ${files.size} files with $totalLines total lines of C code")

    // Output the files
    if (args.isNotEmpty()) {
        val outputDir = File(args[0])
        outputDir.mkdirs()
        files.forEach { (filename, content) ->
            val outputFile = File(outputDir, filename)
            outputFile.writeText(content)
            println("  Written: ${outputFile.absolutePath} (${content.lines().size} lines)")
        }
        println()
        println("Output directory: ${outputDir.absolutePath}")
    } else {
        // Print file list if no output directory specified
        println()
        println("=== Generated Files ===")
        files.forEach { (filename, content) ->
            println("  $filename: ${content.lines().size} lines")
        }
    }

    println()
    println("Next steps:")
    println("  1. Run: ./gradlew :LabyrinthOfTheDragon-port:buildRom")
    println("  2. Run: ./gradlew :LabyrinthOfTheDragon-port:runEmulator")
}
