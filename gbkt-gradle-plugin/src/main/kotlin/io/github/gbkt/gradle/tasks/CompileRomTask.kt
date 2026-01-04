/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import io.github.gbkt.gradle.internal.ErrorEnhancer
import io.github.gbkt.gradle.internal.GbdkErrorParser
import io.github.gbkt.gradle.internal.GbdkToolchain
import io.github.gbkt.gradle.internal.SourceMapLoader
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.ExecOperations

/**
 * Task that compiles generated C code to a Game Boy ROM using GBDK.
 *
 * This task invokes GBDK's lcc compiler to produce a .gb ROM file.
 */
@CacheableTask
abstract class CompileRomTask @Inject constructor(private val execOperations: ExecOperations) :
    DefaultTask() {

    /** Path to GBDK installation directory. */
    @get:Input abstract val gbdkHome: Property<String>

    /** Input C source file to compile. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val cSourceFile: RegularFileProperty

    /** Additional compiler flags for lcc. */
    @get:Input @get:Optional abstract val compilerFlags: ListProperty<String>

    /** Generate debug files (.map, .sym). */
    @get:Input abstract val generateDebugFiles: Property<Boolean>

    /** GBC mode: DISABLED, COMPATIBLE, or ONLY. Adds appropriate -Wm-yc or -Wm-yC flags. */
    @get:Input @get:Optional abstract val gbcMode: Property<String>

    /** Output ROM file. */
    @get:OutputFile abstract val outputRom: RegularFileProperty

    /** Output map file (optional, for debugging). */
    @get:OutputFile @get:Optional abstract val outputMap: RegularFileProperty

    /** Output symbol file (optional, for debugging). */
    @get:OutputFile @get:Optional abstract val outputSym: RegularFileProperty

    init {
        description = "Compile C code to Game Boy ROM using GBDK"
        group = "gbkt"
    }

    @TaskAction
    fun compile() {
        val gbdkDir = File(gbdkHome.get())
        val lcc = GbdkToolchain.getLcc(gbdkDir)
        val sourceFile = cSourceFile.get().asFile
        val romFile = outputRom.get().asFile

        // Ensure output directory exists
        romFile.parentFile.mkdirs()

        // Build lcc command
        val args = mutableListOf<String>()

        // Standard GBDK flags for Game Boy
        args.add("-Wa-l") // Generate assembly listing
        args.add("-Wl-m") // Generate map file
        args.add("-Wl-j") // Generate symbol file

        // Add user-provided flags
        compilerFlags.getOrElse(emptyList()).forEach { flag -> args.add(flag) }

        // Add GBC mode flags
        val mode = gbcMode.getOrElse("DISABLED")
        when (mode.uppercase()) {
            "COMPATIBLE" -> {
                args.add("-Wm-yc") // CGB compatible (works on both DMG and CGB)
                logger.lifecycle("Building with GBC COMPATIBLE mode")
            }
            "ONLY" -> {
                args.add("-Wm-yC") // CGB only (won't run on DMG)
                logger.lifecycle("Building with GBC ONLY mode")
            }
            "DISABLED" -> {
                // No special flags needed for DMG-only mode
            }
            else -> {
                logger.warn("Unknown gbcMode: $mode, using DISABLED")
            }
        }

        // Output file
        args.add("-o")
        args.add(romFile.absolutePath)

        // Input source file
        args.add(sourceFile.absolutePath)

        logger.lifecycle("Compiling ROM: ${sourceFile.name} -> ${romFile.name}")
        logger.info("GBDK: ${gbdkDir.absolutePath}")
        logger.info("Command: ${lcc.absolutePath} ${args.joinToString(" ")}")

        // Capture output for error reporting
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()

        try {
            val result =
                execOperations.exec {
                    executable = lcc.absolutePath
                    setArgs(args)
                    standardOutput = stdout
                    errorOutput = stderr
                    isIgnoreExitValue = true

                    // Set GBDK environment
                    environment["GBDK_DIR"] = gbdkDir.absolutePath
                }

            val stdoutText = stdout.toString().trim()
            val stderrText = stderr.toString().trim()

            if (stdoutText.isNotEmpty()) {
                logger.info(stdoutText)
            }

            if (result.exitValue != 0) {
                // Combine stdout and stderr for error parsing
                val combinedOutput =
                    buildString {
                            if (stderrText.isNotEmpty()) appendLine(stderrText)
                            if (stdoutText.isNotEmpty()) appendLine(stdoutText)
                        }
                        .trim()

                // Try to enhance errors with source map
                val errorMessage =
                    try {
                        enhanceErrorMessage(combinedOutput, sourceFile)
                    } catch (e: Exception) {
                        // If enhancement fails, fall back to basic error message
                        logger.warn("Source map error enhancement failed: ${e.message}")
                        logger.warn(
                            "Error locations may not map to Kotlin source. Check generated C code."
                        )
                        logger.info("Full exception: ", e)
                        buildBasicErrorMessage(combinedOutput, sourceFile, result.exitValue)
                    }

                throw GradleException(errorMessage)
            }

            // Verify ROM was created
            if (!romFile.exists()) {
                throw GradleException("ROM file was not created: ${romFile.absolutePath}")
            }

            val romSize = romFile.length()
            logger.lifecycle("ROM created: ${romFile.name} (${formatSize(romSize)})")

            // Handle debug files if generated
            if (generateDebugFiles.getOrElse(true)) {
                handleDebugFiles(romFile)
            }
        } catch (e: GradleException) {
            throw e
        } catch (e: Exception) {
            throw GradleException("Failed to execute GBDK compiler: ${e.message}", e)
        }
    }

    private fun handleDebugFiles(romFile: File) {
        val baseName = romFile.nameWithoutExtension
        val romDir = romFile.parentFile

        // GBDK generates .map and .sym files alongside the ROM
        val mapFile = File(romDir, "$baseName.map")
        val symFile = File(romDir, "$baseName.sym")

        if (mapFile.exists()) {
            logger.info("Map file: ${mapFile.name}")
            outputMap.set(mapFile)
        }

        if (symFile.exists()) {
            logger.info("Symbol file: ${symFile.name}")
            outputSym.set(symFile)
        }
    }

    private fun formatSize(bytes: Long): String =
        when {
            bytes < 1024 -> "$bytes bytes"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }

    /** Enhance error message with source map mappings and suggestions. */
    private fun enhanceErrorMessage(compilerOutput: String, sourceFile: File): String {
        // Parse errors from compiler output
        val errors = GbdkErrorParser.parseErrors(compilerOutput, sourceFile)

        if (errors.isEmpty()) {
            // No parseable errors, return basic message
            return buildBasicErrorMessage(compilerOutput, sourceFile, 1)
        }

        // Try to load source map
        val sourceMapFile = SourceMapLoader.findSourceMapFile(sourceFile)
        val sourceMap = SourceMapLoader.load(sourceMapFile)

        if (sourceMap == null) {
            logger.warn("Source map not found: ${sourceMapFile.name}")
            logger.warn("Error line numbers refer to generated C code, not Kotlin source.")
            logger.info("Expected source map at: ${sourceMapFile.absolutePath}")
        }

        // Enhance errors with source map mappings and suggestions
        val enhancedErrors = ErrorEnhancer.enhanceErrors(errors, sourceMap)

        // Format enhanced errors
        return ErrorEnhancer.formatEnhancedErrors(enhancedErrors, sourceFile)
    }

    /** Build a basic error message when enhancement is not possible. */
    private fun buildBasicErrorMessage(
        compilerOutput: String,
        sourceFile: File,
        exitCode: Int
    ): String {
        return buildString {
            appendLine("GBDK compilation failed (exit code: $exitCode)")
            appendLine()
            appendLine("Compiler output:")
            appendLine(compilerOutput)
            appendLine()
            appendLine("Source file: ${sourceFile.absolutePath}")
            appendLine()
            appendLine("To debug, inspect the generated C code and fix any issues.")
        }
    }
}
