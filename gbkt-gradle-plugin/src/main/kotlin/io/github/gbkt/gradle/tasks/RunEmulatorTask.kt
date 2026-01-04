/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

/**
 * Task that launches an emulator (mGBA) with the built ROM.
 *
 * This task provides cross-platform support for mGBA with automatic path detection on macOS, Linux,
 * and Windows.
 *
 * Features:
 * - Auto-detection of mGBA on common installation paths
 * - Live reload support via mGBA's Lua scripting (optional)
 * - Cross-platform: macOS (.app bundles), Linux, Windows
 */
abstract class RunEmulatorTask @Inject constructor(private val execOperations: ExecOperations) :
    DefaultTask() {

    /** Path to the emulator executable. If not provided, will attempt to auto-detect mGBA. */
    @get:Input @get:Optional abstract val emulatorPath: Property<String>

    /** Additional command-line arguments for the emulator. */
    @get:Input @get:Optional abstract val emulatorArgs: ListProperty<String>

    /** ROM file to run in the emulator. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val romFile: RegularFileProperty

    /**
     * Enable live reload functionality. When true, generates and loads a Lua script that monitors
     * the ROM file for changes and automatically reloads.
     */
    @get:Input @get:Optional abstract val liveReload: Property<Boolean>

    /**
     * Custom path to a live reload Lua script. If not provided, a default script will be generated.
     */
    @get:Input @get:Optional abstract val liveReloadScript: Property<String>

    /** Build directory for generated scripts. */
    @get:Internal abstract val buildDirectory: DirectoryProperty

    init {
        description = "Run the built ROM in mGBA emulator"
        group = "gbkt"
    }

    @TaskAction
    fun run() {
        val rom = romFile.get().asFile

        // Validate ROM exists
        if (!rom.exists()) {
            throw GradleException(
                """
                |ROM file not found: ${rom.absolutePath}
                |
                |Make sure to build the ROM first by running:
                |  ./gradlew buildRom
            """
                    .trimMargin()
            )
        }

        // Find emulator
        val emulator = findEmulator()

        logger.lifecycle("Launching mGBA with ROM: ${rom.name}")
        logger.info("Emulator: ${emulator.absolutePath}")
        logger.info("ROM: ${rom.absolutePath}")

        // Build command
        val command = buildCommand(emulator, rom)
        logger.info("Command: ${command.joinToString(" ")}")

        try {
            // Launch emulator (non-blocking, let it run independently)
            val processBuilder = ProcessBuilder(command)
            processBuilder.inheritIO()
            processBuilder.start()

            logger.lifecycle("mGBA started successfully")
        } catch (e: Exception) {
            throw GradleException("Failed to launch emulator: ${e.message}", e)
        }
    }

    private fun findEmulator(): File {
        // First check if user provided explicit path
        if (emulatorPath.isPresent) {
            val path = emulatorPath.get()
            val file = resolveEmulatorPath(path)
            if (file != null && file.exists()) {
                return file
            }
            throw GradleException(
                """
                |Emulator not found at configured path: $path
                |
                |Please verify the path is correct or remove the configuration
                |to use auto-detection.
            """
                    .trimMargin()
            )
        }

        // Auto-detect based on OS
        val detected = detectEmulator()
        if (detected != null) {
            logger.info("Auto-detected mGBA at: ${detected.absolutePath}")
            return detected
        }

        throw GradleException(buildEmulatorNotFoundMessage())
    }

    private fun resolveEmulatorPath(path: String): File? {
        val file = File(path)

        // If it's a macOS .app bundle, find the actual executable
        if (file.isDirectory && file.name.endsWith(".app")) {
            return getMacOSAppExecutable(file)
        }

        return file
    }

    private fun detectEmulator(): File? {
        val osName = System.getProperty("os.name").lowercase()

        return when {
            osName.contains("mac") -> detectMacOS()
            osName.contains("linux") -> detectLinux()
            osName.contains("windows") -> detectWindows()
            else -> null
        }
    }

    private fun detectMacOS(): File? {
        val paths =
            listOf(
                // App bundle in Applications
                "/Applications/mGBA.app",
                // User Applications folder
                "${System.getProperty("user.home")}/Applications/mGBA.app",
                // Homebrew
                "/usr/local/bin/mgba",
                "/opt/homebrew/bin/mgba",
                // Qt version (Homebrew)
                "/usr/local/bin/mgba-qt",
                "/opt/homebrew/bin/mgba-qt"
            )

        for (path in paths) {
            val file = File(path)
            if (file.exists()) {
                // If it's an .app bundle, get the actual executable
                if (file.isDirectory && file.name.endsWith(".app")) {
                    val executable = getMacOSAppExecutable(file)
                    if (executable != null && executable.exists()) {
                        return executable
                    }
                } else if (file.isFile && file.canExecute()) {
                    return file
                }
            }
        }

        return null
    }

    private fun getMacOSAppExecutable(appBundle: File): File? {
        // mGBA.app/Contents/MacOS/mGBA
        val macosDir = File(appBundle, "Contents/MacOS")
        if (macosDir.exists()) {
            val mgba = File(macosDir, "mGBA")
            if (mgba.exists() && mgba.canExecute()) {
                return mgba
            }
            // Also check for lowercase
            val mgbaLower = File(macosDir, "mgba")
            if (mgbaLower.exists() && mgbaLower.canExecute()) {
                return mgbaLower
            }
        }
        return null
    }

    private fun detectLinux(): File? {
        val paths =
            listOf(
                "/usr/bin/mgba",
                "/usr/bin/mgba-qt",
                "/usr/local/bin/mgba",
                "/usr/local/bin/mgba-qt",
                "${System.getProperty("user.home")}/.local/bin/mgba",
                // Flatpak
                "/var/lib/flatpak/exports/bin/io.mgba.mGBA",
                "${System.getProperty("user.home")}/.local/share/flatpak/exports/bin/io.mgba.mGBA",
                // Snap
                "/snap/bin/mgba"
            )

        for (path in paths) {
            val file = File(path)
            if (file.exists() && file.canExecute()) {
                return file
            }
        }

        return null
    }

    private fun detectWindows(): File? {
        val paths =
            listOf(
                "C:\\Program Files\\mGBA\\mGBA.exe",
                "C:\\Program Files (x86)\\mGBA\\mGBA.exe",
                "${System.getenv("LOCALAPPDATA")}\\mGBA\\mGBA.exe",
                "${System.getenv("USERPROFILE")}\\scoop\\apps\\mgba\\current\\mGBA.exe"
            )

        for (path in paths) {
            val file = File(path)
            if (file.exists()) {
                return file
            }
        }

        return null
    }

    private fun buildCommand(emulator: File, rom: File): List<String> {
        val command = mutableListOf<String>()
        command.add(emulator.absolutePath)

        // Add user-provided arguments
        emulatorArgs.getOrElse(emptyList()).forEach { arg -> command.add(arg) }

        // Add live reload script if enabled (default: true)
        if (liveReload.getOrElse(true)) {
            val scriptPath = getLiveReloadScriptPath(rom)
            if (scriptPath != null) {
                command.add("-l")
                command.add(scriptPath.absolutePath)
                logger.lifecycle("Live reload enabled: ${scriptPath.name}")
            } else {
                logger.warn("Live reload requested but script could not be created")
            }
        }

        // Add ROM path as the last argument
        command.add(rom.absolutePath)

        return command
    }

    /**
     * Gets or creates the live reload Lua script. Returns the path to the script file, or null if
     * it couldn't be created.
     *
     * Search order:
     * 1. Custom script path from configuration
     * 2. Bundled script at scripts/live-reload.lua
     * 3. Generated inline script as fallback
     */
    private fun getLiveReloadScriptPath(rom: File): File? {
        // Check for custom script path first
        if (liveReloadScript.isPresent) {
            val customScript = File(liveReloadScript.get())
            if (customScript.exists()) {
                logger.info("Using custom live reload script: ${customScript.absolutePath}")
                return createWrapperScript(customScript, rom)
            }
            logger.warn("Custom live reload script not found: ${customScript.absolutePath}")
        }

        // Check for bundled script in project
        val bundledScript = findBundledScript()
        if (bundledScript != null) {
            logger.info("Using bundled live reload script: ${bundledScript.absolutePath}")
            return createWrapperScript(bundledScript, rom)
        }

        // Generate default script as fallback
        return try {
            logger.info("Generating inline live reload script")
            generateLiveReloadScript(rom)
        } catch (e: Exception) {
            logger.warn("Failed to generate live reload script: ${e.message}")
            null
        }
    }

    /** Find the bundled live-reload.lua script in the project. */
    private fun findBundledScript(): File? {
        val candidates =
            listOf(
                // Project root scripts directory
                File(project.rootProject.projectDir, "scripts/live-reload.lua"),
                // Current project scripts directory
                File(project.projectDir, "scripts/live-reload.lua")
            )

        for (candidate in candidates) {
            if (candidate.exists()) {
                return candidate
            }
        }

        return null
    }

    /**
     * Create a wrapper script that sets the ROM path and loads the main script. This allows the
     * bundled script to know which ROM to monitor.
     */
    private fun createWrapperScript(mainScript: File, rom: File): File? {
        return try {
            val scriptsDir =
                if (buildDirectory.isPresent) {
                    buildDirectory.get().dir("gbkt/scripts").asFile
                } else {
                    File(project.layout.buildDirectory.get().asFile, "gbkt/scripts")
                }
            scriptsDir.mkdirs()

            val wrapperFile = File(scriptsDir, "live-reload-wrapper.lua")

            // Escape backslashes for Lua strings (Windows paths)
            val escapedRomPath = rom.absolutePath.replace("\\", "\\\\")
            val escapedMainScript = mainScript.absolutePath.replace("\\", "\\\\")

            wrapperFile.writeText(
                """
                |-- gbkt live-reload wrapper script
                |-- Auto-generated - do not edit
                |
                |-- Set the ROM path for the live-reload script to monitor
                |GBKT_ROM_PATH = "$escapedRomPath"
                |
                |-- Load the main live-reload script
                |dofile("$escapedMainScript")
            """
                    .trimMargin()
            )

            logger.info("Created wrapper script: ${wrapperFile.absolutePath}")
            wrapperFile
        } catch (e: Exception) {
            logger.warn("Failed to create wrapper script: ${e.message}")
            null
        }
    }

    /**
     * Generates a Lua script for mGBA that monitors the ROM file for changes and automatically
     * reloads when it detects modifications.
     */
    private fun generateLiveReloadScript(rom: File): File {
        val scriptsDir =
            if (buildDirectory.isPresent) {
                buildDirectory.get().dir("gbkt/scripts").asFile
            } else {
                File(rom.parentFile, "scripts")
            }
        scriptsDir.mkdirs()

        val scriptFile = File(scriptsDir, "live-reload.lua")

        // Use forward slashes for Lua on all platforms
        val romPath = rom.absolutePath.replace("\\", "/")

        val script =
            """
            |-- gbkt Live Reload Script for mGBA
            |-- Monitors ROM file for changes and automatically reloads
            |
            |local rom_path = "$romPath"
            |local last_modified = 0
            |local check_interval = 60  -- Check every ~1 second (60 frames)
            |local frame_count = 0
            |
            |-- Get file modification time
            |local function get_mtime()
            |    local f = io.open(rom_path, "rb")
            |    if f then
            |        f:close()
            |        -- Use os.time as a proxy since Lua doesn't have direct mtime access
            |        -- mGBA will reload if the file content changes
            |        local handle = io.popen('stat -f "%m" "' .. rom_path .. '" 2>/dev/null || stat -c "%Y" "' .. rom_path .. '" 2>/dev/null')
            |        if handle then
            |            local result = handle:read("*a")
            |            handle:close()
            |            return tonumber(result) or 0
            |        end
            |    end
            |    return 0
            |end
            |
            |-- Initialize last_modified
            |last_modified = get_mtime()
            |console:log("Live reload watching: " .. rom_path)
            |
            |-- Frame callback to check for changes
            |callbacks:add("frame", function()
            |    frame_count = frame_count + 1
            |    if frame_count >= check_interval then
            |        frame_count = 0
            |        local current_mtime = get_mtime()
            |        if current_mtime > 0 and current_mtime ~= last_modified then
            |            console:log("ROM changed, reloading...")
            |            last_modified = current_mtime
            |            emu:loadFile(rom_path)
            |        end
            |    end
            |end)
        """
                .trimMargin()

        scriptFile.writeText(script)
        logger.info("Generated live reload script: ${scriptFile.absolutePath}")

        return scriptFile
    }

    private fun buildEmulatorNotFoundMessage(): String {
        val osName = System.getProperty("os.name").lowercase()

        val installInstructions =
            when {
                osName.contains("mac") ->
                    """
                |Installation options for macOS:
                |
                |  1. Download from official website:
                |     https://mgba.io/downloads.html
                |     Install mGBA.app to /Applications
                |
                |  2. Using Homebrew:
                |     brew install mgba
            """
                        .trimMargin()
                osName.contains("linux") ->
                    """
                |Installation options for Linux:
                |
                |  1. Using apt (Debian/Ubuntu):
                |     sudo apt install mgba-qt
                |
                |  2. Using dnf (Fedora):
                |     sudo dnf install mgba-qt
                |
                |  3. Using Flatpak:
                |     flatpak install flathub io.mgba.mGBA
                |
                |  4. Download from official website:
                |     https://mgba.io/downloads.html
            """
                        .trimMargin()
                osName.contains("windows") ->
                    """
                |Installation options for Windows:
                |
                |  1. Download from official website:
                |     https://mgba.io/downloads.html
                |     Extract to C:\Program Files\mGBA
                |
                |  2. Using Scoop:
                |     scoop install mgba
            """
                        .trimMargin()
                else ->
                    """
                |Download mGBA from:
                |  https://mgba.io/downloads.html
            """
                        .trimMargin()
            }

        return """
            |mGBA emulator not found!
            |
            |$installInstructions
            |
            |Alternatively, configure the path explicitly in build.gradle.kts:
            |
            |  gbkt {
            |      emulator {
            |          path.set("/path/to/mgba")
            |      }
            |  }
        """
            .trimMargin()
    }
}
