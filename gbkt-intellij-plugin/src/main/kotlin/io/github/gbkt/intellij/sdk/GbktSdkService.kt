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
package io.github.gbkt.intellij.sdk

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Service for managing GBDK-2020 SDK detection and configuration.
 *
 * Provides:
 * - Automatic SDK detection on common paths
 * - SDK validation (checks for required binaries)
 * - Emulator detection (bgb, sameboy, etc.)
 * - Configuration persistence
 */
@Service(Service.Level.PROJECT)
class GbktSdkService(private val project: Project) {

    private val logger = Logger.getInstance(GbktSdkService::class.java)

    /** Currently configured GBDK-2020 path, or null if not configured. */
    var gbdkPath: Path? = null
        private set

    /** Currently configured emulator path, or null if not configured. */
    var emulatorPath: Path? = null
        private set

    /** Type of configured emulator. */
    var emulatorType: EmulatorType = EmulatorType.NONE
        private set

    /** Validation result for the current SDK configuration. */
    var sdkStatus: SdkStatus = SdkStatus.NOT_CONFIGURED
        private set

    init {
        // Attempt auto-detection on service creation
        autoDetectSdk()
        autoDetectEmulator()
    }

    /**
     * Attempts to automatically detect GBDK-2020 installation.
     *
     * Checks common installation paths:
     * - GBDK_HOME environment variable
     * - /opt/gbdk (Linux)
     * - /usr/local/gbdk (Linux/macOS)
     * - ~/gbdk (user home)
     * - C:\gbdk (Windows)
     */
    fun autoDetectSdk(): Boolean {
        logger.info("Attempting GBDK-2020 auto-detection...")

        // Check environment variable first
        System.getenv("GBDK_HOME")?.let { path ->
            if (validateGbdkPath(Paths.get(path))) {
                gbdkPath = Paths.get(path)
                sdkStatus = SdkStatus.CONFIGURED
                logger.info("Found GBDK-2020 via GBDK_HOME: $path")
                return true
            }
        }

        // Check common paths
        val commonPaths =
            listOf(
                "/opt/gbdk",
                "/usr/local/gbdk",
                System.getProperty("user.home") + "/gbdk",
                System.getProperty("user.home") + "/gbdk-2020",
                "C:\\gbdk",
                "C:\\gbdk-2020",
            )

        for (pathStr in commonPaths) {
            val path = Paths.get(pathStr)
            if (validateGbdkPath(path)) {
                gbdkPath = path
                sdkStatus = SdkStatus.CONFIGURED
                logger.info("Found GBDK-2020 at: $path")
                return true
            }
        }

        sdkStatus = SdkStatus.NOT_FOUND
        logger.warn("GBDK-2020 not found in common locations")
        return false
    }

    /**
     * Attempts to automatically detect a Game Boy emulator.
     *
     * Checks for common emulators:
     * - BGB (bgb.exe on Windows)
     * - SameBoy
     * - mGBA
     */
    fun autoDetectEmulator(): Boolean {
        logger.info("Attempting emulator auto-detection...")

        // Check for BGB (popular Windows emulator)
        val bgbPaths =
            listOf(
                "C:\\bgb\\bgb.exe",
                "C:\\Program Files\\bgb\\bgb.exe",
                "C:\\Program Files (x86)\\bgb\\bgb.exe",
            )
        for (pathStr in bgbPaths) {
            val path = Paths.get(pathStr)
            if (path.toFile().exists() && path.toFile().canExecute()) {
                emulatorPath = path
                emulatorType = EmulatorType.BGB
                logger.info("Found BGB emulator at: $path")
                return true
            }
        }

        // Check for SameBoy (macOS)
        val sameboyPaths =
            listOf(
                "/Applications/SameBoy.app/Contents/MacOS/SameBoy",
                System.getProperty("user.home") + "/Applications/SameBoy.app/Contents/MacOS/SameBoy",
            )
        for (pathStr in sameboyPaths) {
            val path = Paths.get(pathStr)
            if (path.toFile().exists()) {
                emulatorPath = path
                emulatorType = EmulatorType.SAMEBOY
                logger.info("Found SameBoy emulator at: $path")
                return true
            }
        }

        // Check for mGBA
        val mgbaPaths =
            listOf(
                "/usr/bin/mgba",
                "/usr/local/bin/mgba",
                "/Applications/mGBA.app/Contents/MacOS/mGBA",
                "C:\\Program Files\\mGBA\\mGBA.exe",
            )
        for (pathStr in mgbaPaths) {
            val path = Paths.get(pathStr)
            if (path.toFile().exists()) {
                emulatorPath = path
                emulatorType = EmulatorType.MGBA
                logger.info("Found mGBA emulator at: $path")
                return true
            }
        }

        logger.info("No emulator auto-detected")
        return false
    }

    /**
     * Validates a GBDK-2020 installation path.
     *
     * Checks for:
     * - lcc compiler binary
     * - include directory
     * - lib directory
     */
    fun validateGbdkPath(path: Path): Boolean {
        val dir = path.toFile()
        if (!dir.exists() || !dir.isDirectory) return false

        // Check for lcc compiler
        val lccPath =
            if (System.getProperty("os.name").lowercase().contains("windows")) {
                path.resolve("bin/lcc.exe")
            } else {
                path.resolve("bin/lcc")
            }

        // Check for include and lib directories
        val includePath = path.resolve("include")
        val libPath = path.resolve("lib")

        return lccPath.toFile().exists() &&
            includePath.toFile().exists() &&
            libPath.toFile().exists()
    }

    /** Sets the GBDK-2020 path manually. */
    fun setGbdkPath(path: Path): Boolean {
        if (validateGbdkPath(path)) {
            gbdkPath = path
            sdkStatus = SdkStatus.CONFIGURED
            return true
        }
        sdkStatus = SdkStatus.INVALID
        return false
    }

    /** Sets the emulator path manually. */
    fun setEmulatorPath(path: Path, type: EmulatorType) {
        emulatorPath = path
        emulatorType = type
    }

    companion object {
        fun getInstance(project: Project): GbktSdkService = project.service()
    }
}

/** SDK configuration status. */
enum class SdkStatus {
    NOT_CONFIGURED,
    NOT_FOUND,
    INVALID,
    CONFIGURED,
}

/** Supported emulator types. */
enum class EmulatorType {
    NONE,
    BGB,
    SAMEBOY,
    MGBA,
    CUSTOM,
}
