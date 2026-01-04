/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.internal

import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/** Utility for discovering and validating GBDK (Game Boy Development Kit) installations. */
object GbdkToolchain {

    private val logger: Logger = Logging.getLogger(GbdkToolchain::class.java)

    /**
     * Find GBDK installation directory.
     *
     * Search order:
     * 1. Explicit path (if provided)
     * 2. GBDK_HOME environment variable
     * 3. Common installation paths
     *
     * @param explicitPath User-configured path, or null to auto-detect
     * @return Path to valid GBDK installation
     * @throws GradleException if GBDK cannot be found
     */
    fun find(explicitPath: String?): File {
        // 1. Try explicit configuration
        if (explicitPath != null) {
            val dir = File(explicitPath)
            if (isValidGbdk(dir)) {
                logger.info("Using configured GBDK at: ${dir.absolutePath}")
                return dir
            }
            throw GradleException(
                """
                |Configured GBDK path is invalid: $explicitPath
                |
                |Expected to find:
                |  - bin/lcc (compiler)
                |  - lib/gb (libraries)
                |
                |Please verify your GBDK installation.
            """
                    .trimMargin()
            )
        }

        // 2. Try GBDK_HOME environment variable
        val envHome = System.getenv("GBDK_HOME")
        if (envHome != null) {
            val dir = File(envHome)
            if (isValidGbdk(dir)) {
                logger.info("Using GBDK from GBDK_HOME: ${dir.absolutePath}")
                return dir
            }
            logger.warn("GBDK_HOME is set but path is invalid: $envHome")
        }

        // 3. Try common installation paths
        for (path in commonPaths()) {
            val dir = File(path)
            if (isValidGbdk(dir)) {
                logger.info("Found GBDK at common path: ${dir.absolutePath}")
                return dir
            }
        }

        // Not found - provide helpful error message
        throw GradleException(
            """
            |GBDK (Game Boy Development Kit) not found.
            |
            |Please install GBDK from:
            |  https://github.com/gbdk-2020/gbdk-2020/releases
            |
            |Then either:
            |  1. Set GBDK_HOME environment variable:
            |     export GBDK_HOME=/path/to/gbdk-2020
            |
            |  2. Or configure in build.gradle.kts:
            |     gbkt {
            |         gbdkHome.set("/path/to/gbdk-2020")
            |     }
            |
            |Searched paths:
            |${commonPaths().joinToString("\n") { "  - $it" }}
        """
                .trimMargin()
        )
    }

    /** Get the lcc compiler executable. */
    fun getLcc(gbdkHome: File): File {
        val lcc =
            if (isWindows()) {
                File(gbdkHome, "bin/lcc.exe")
            } else {
                File(gbdkHome, "bin/lcc")
            }

        if (!lcc.exists()) {
            throw GradleException("GBDK compiler not found: ${lcc.absolutePath}")
        }

        return lcc
    }

    /** Validate that a directory contains a valid GBDK installation. */
    fun isValidGbdk(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false

        val lcc =
            if (isWindows()) {
                File(dir, "bin/lcc.exe")
            } else {
                File(dir, "bin/lcc")
            }

        val libDir = File(dir, "lib/gb")

        return lcc.exists() && libDir.exists()
    }

    /** Common GBDK installation paths for different platforms. */
    private fun commonPaths(): List<String> {
        val userHome = System.getProperty("user.home")

        return when {
            isMacOS() ->
                listOf(
                    "/usr/local/gbdk-2020",
                    "/opt/homebrew/gbdk-2020",
                    "/opt/gbdk-2020",
                    "$userHome/gbdk-2020",
                    "$userHome/dev/gbdk-2020"
                )
            isWindows() ->
                listOf(
                    "C:\\gbdk-2020",
                    "C:\\Program Files\\gbdk-2020",
                    "C:\\Program Files (x86)\\gbdk-2020",
                    "${System.getenv("USERPROFILE")}\\gbdk-2020"
                )
            else ->
                listOf( // Linux and others
                    "/usr/local/gbdk-2020",
                    "/opt/gbdk-2020",
                    "$userHome/gbdk-2020",
                    "$userHome/dev/gbdk-2020"
                )
        }
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("windows")

    private fun isMacOS(): Boolean = System.getProperty("os.name").lowercase().contains("mac")
}
