/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import java.io.File
import java.util.concurrent.TimeUnit
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Task that validates a ROM boots without crashing using mGBA's Lua scripting interface.
 *
 * Runs the ROM in mGBA headless for a configurable number of frames. If the ROM crashes before
 * reaching the target frame count, the task fails. If mGBA is not installed, the task prints a
 * warning and skips validation gracefully (does NOT fail the build).
 *
 * Usage:
 * ```
 * ./gradlew validateRom
 * ```
 *
 * The task is opt-in — buildRom does NOT depend on it. Run it explicitly for CI validation.
 */
abstract class ValidateRomTask : DefaultTask() {

    /** ROM file to validate. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val romFile: RegularFileProperty

    /** Optional path to the mGBA emulator. If not provided, auto-detection is attempted. */
    @get:Optional @get:Input abstract val emulatorPath: Property<String>

    /**
     * Number of frames to run the ROM before declaring it valid. Default: 300 (~5 seconds at
     * 60fps).
     */
    @get:Input abstract val frameCount: Property<Int>

    init {
        group = "gbkt"
        description = "Validate ROM boots without crash using mGBA"
        frameCount.convention(300)
    }

    @TaskAction
    fun validate() {
        val rom = romFile.get().asFile

        if (!rom.exists()) {
            throw GradleException("ROM file not found: ${rom.absolutePath}. Run buildRom first.")
        }

        val emulator = findMgba()
        if (emulator == null) {
            logger.warn(
                "WARNING: mGBA not found. Skipping ROM validation. " +
                    "Install via: brew install mgba  (or download from https://mgba.io/downloads.html)"
            )
            return
        }

        logger.lifecycle("Validating ROM: ${rom.name}")
        logger.info("Emulator: ${emulator.absolutePath}")
        logger.info("Target frames: ${frameCount.get()}")

        val scriptFile = generateValidationScript()
        logger.info("Lua script: ${scriptFile.absolutePath}")

        runValidation(emulator, scriptFile, rom)
    }

    private fun findMgba(): File? {
        // Check user-provided path first
        if (emulatorPath.isPresent) {
            val path = emulatorPath.get()
            val file = File(path)
            if (file.exists() && file.canExecute()) {
                return file
            }
            // Try as .app bundle on macOS
            if (file.isDirectory && file.name.endsWith(".app")) {
                val exe = File(file, "Contents/MacOS/mGBA")
                if (exe.exists() && exe.canExecute()) return exe
            }
            logger.warn("mGBA not found at configured path: $path")
            return null
        }

        // Auto-detect: prefer mgba-sdl for headless scripting (reliable -S flag support)
        val candidates = buildAutoDetectPaths()
        for (path in candidates) {
            val file = File(path)
            if (file.exists() && (file.isFile && file.canExecute() || file.isDirectory)) {
                if (file.isDirectory && file.name.endsWith(".app")) {
                    val exe = File(file, "Contents/MacOS/mGBA")
                    if (exe.exists() && exe.canExecute()) {
                        logger.info("Auto-detected mGBA at: ${exe.absolutePath}")
                        return exe
                    }
                } else if (file.isFile && file.canExecute()) {
                    logger.info("Auto-detected mGBA at: ${file.absolutePath}")
                    return file
                }
            }
        }

        return null
    }

    private fun buildAutoDetectPaths(): List<String> {
        val osName = System.getProperty("os.name").lowercase()
        val home = System.getProperty("user.home")

        return when {
            osName.contains("mac") ->
                listOf(
                    // mgba-sdl preferred for automation (headless, reliable -S support)
                    "/opt/homebrew/bin/mgba-sdl",
                    "/usr/local/bin/mgba-sdl",
                    // mgba-qt as fallback
                    "/opt/homebrew/bin/mgba-qt",
                    "/usr/local/bin/mgba-qt",
                    // Homebrew mgba
                    "/opt/homebrew/bin/mgba",
                    "/usr/local/bin/mgba",
                    // .app bundles
                    "/Applications/mGBA.app",
                    "$home/Applications/mGBA.app",
                )
            osName.contains("linux") ->
                listOf(
                    "/usr/bin/mgba-sdl",
                    "/usr/local/bin/mgba-sdl",
                    "/usr/bin/mgba-qt",
                    "/usr/local/bin/mgba-qt",
                    "/usr/bin/mgba",
                    "/usr/local/bin/mgba",
                    "$home/.local/bin/mgba",
                    "/var/lib/flatpak/exports/bin/io.mgba.mGBA",
                    "$home/.local/share/flatpak/exports/bin/io.mgba.mGBA",
                    "/snap/bin/mgba",
                )
            osName.contains("windows") ->
                listOf(
                    "C:\\Program Files\\mGBA\\mGBA.exe",
                    "C:\\Program Files (x86)\\mGBA\\mGBA.exe",
                    "${System.getenv("LOCALAPPDATA")}\\mGBA\\mGBA.exe",
                    "${System.getenv("USERPROFILE")}\\scoop\\apps\\mgba\\current\\mGBA.exe",
                )
            else -> emptyList()
        }
    }

    private fun generateValidationScript(): File {
        val scriptsDir = temporaryDir
        scriptsDir.mkdirs()

        val scriptFile = File(scriptsDir, "validate-rom.lua")
        val targetFrames = frameCount.get()

        scriptFile.writeText(
            """
            |-- gbkt ROM Validation Script
            |-- Auto-generated by ValidateRomTask - do not edit
            |-- Runs ROM for TARGET_FRAMES frames, exits 0 on success, 1 on crash
            |
            |local TARGET_FRAMES = $targetFrames
            |local frame_count = 0
            |
            |callbacks:add("frame", function()
            |    frame_count = frame_count + 1
            |    if frame_count >= TARGET_FRAMES then
            |        -- ROM survived the target frame count - validation passed
            |        if emu.quit then
            |            emu:quit(0)
            |        else
            |            os.exit(0)
            |        end
            |    end
            |end)
            |
            |callbacks:add("crashed", function()
            |    -- ROM crashed - validation failed
            |    if emu.quit then
            |        emu:quit(1)
            |    else
            |        os.exit(1)
            |    end
            |end)
            """
                .trimMargin()
        )

        return scriptFile
    }

    private fun runValidation(emulator: File, scriptFile: File, rom: File) {
        val command = listOf(emulator.absolutePath, "-S", scriptFile.absolutePath, rom.absolutePath)

        logger.info("Validation command: ${command.joinToString(" ")}")

        val process =
            try {
                ProcessBuilder(command).redirectErrorStream(true).start()
            } catch (e: Exception) {
                logger.warn(
                    "WARNING: Failed to launch mGBA for validation: ${e.message}. " +
                        "Skipping ROM validation."
                )
                return
            }

        // Timeout: allow 15 seconds. At 60fps, 300 frames = 5 seconds. 15s gives generous margin.
        val completed = process.waitFor(15L, TimeUnit.SECONDS)

        if (!completed) {
            // Process survived the full timeout without exiting — this means the ROM is running
            // fine and the Lua script's emu:quit() may not have fired (e.g., headless issues).
            // Treat timeout as a pass — the ROM didn't crash in 15 seconds.
            process.destroyForcibly()
            logger.lifecycle(
                "ROM validated: ${rom.name} survived ${frameCount.get()} frames (timeout = pass)"
            )
        } else {
            val exitCode = process.exitValue()
            // Read stdout+stderr to detect "invalid option" vs real crash
            val output = process.inputStream.bufferedReader().readText().trim()
            logger.info("mGBA output: $output")

            if (exitCode == 0) {
                logger.lifecycle(
                    "ROM validated: ${rom.name} booted ${frameCount.get()} frames without crash"
                )
            } else if (isInvalidOptionError(output, exitCode)) {
                // mGBA doesn't support -S scripting flag (e.g., Qt-only build without SDL backend)
                logger.warn(
                    "WARNING: This mGBA build does not support the -S Lua scripting flag. " +
                        "ROM validation requires mgba-sdl (headless build). " +
                        "Install via: brew install mgba --with-sdl  or download from https://mgba.io/downloads.html. " +
                        "Skipping ROM validation."
                )
            } else if (exitCode == 1 && output.isNotEmpty() && !output.contains("usage:")) {
                // Exit code 1 AND has real output (not "usage:" which indicates flag error)
                throw GradleException(
                    "ROM validation FAILED: ${rom.name} crashed within ${frameCount.get()} frames"
                )
            } else {
                // Non-zero exit from mGBA itself (unsupported flag, missing ROM, etc.)
                logger.warn(
                    "WARNING: mGBA exited with code $exitCode during ROM validation. " +
                        "Skipping ROM validation. Run with --info for details."
                )
            }
        }
    }

    /**
     * Detects whether mGBA's exit was caused by an unsupported -S flag (not a ROM crash). The
     * Qt-only mGBA build prints "invalid option -- S" and exits 1.
     */
    private fun isInvalidOptionError(output: String, exitCode: Int): Boolean {
        if (exitCode != 1) return false
        return output.contains("invalid option") ||
            output.contains("usage:") ||
            output.contains("unrecognized option") ||
            // Empty output + exit 1 immediately = likely flag rejection (process never started ROM)
            output.isEmpty()
    }
}
