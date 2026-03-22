/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.test

import io.github.gbkt.emulator.agent.AgentSessionConfig
import java.io.File

/**
 * Convention-based ROM, sym, and metadata path resolution for game testing.
 *
 * Understands both the standalone project layout and the multi-game example layout.
 *
 * Usage:
 * ```kotlin
 * val config = GameDiscovery.configForGame("pong") ?: return
 * ```
 */
object GameDiscovery {

    /**
     * Resolved information about a built game ROM.
     *
     * @param name Game name (ROM file base name without extension).
     * @param romFile Path to the .gb ROM file.
     * @param hasMetadata Whether a game_metadata.json file exists alongside the ROM.
     */
    data class GameInfo(val name: String, val romFile: File, val hasMetadata: Boolean)

    /**
     * Resolves an [AgentSessionConfig] for a game by convention.
     *
     * Looks for the ROM at build/gbkt/output/GAMENAME.gb under [projectRoot]. Returns null if the
     * ROM file does not exist - callers can use JUnit5 Assumptions.assumeTrue to skip tests
     * gracefully.
     *
     * @param gameName The game name (must match the ROM file base name, e.g., "pong").
     * @param screenshotDir Override the screenshot output directory. Defaults to
     *   build/gbkt/test-failures under [projectRoot].
     * @param projectRoot Root directory to resolve paths from. Defaults to File("."), which is
     *   the working directory at test runtime (the subproject root when run by Gradle).
     * @return [AgentSessionConfig] if the ROM exists, or null if not found.
     */
    fun configForGame(
        gameName: String,
        screenshotDir: File? = null,
        projectRoot: File = File("."),
    ): AgentSessionConfig? {
        val romFile = File(projectRoot, "build/gbkt/output/$gameName.gb")
        if (!romFile.exists()) return null
        val resolvedScreenshotDir = screenshotDir ?: File(projectRoot, "build/gbkt/test-failures")
        return AgentSessionConfig.discoverFiles(romFile, resolvedScreenshotDir)
    }

    /**
     * Scans for all built game ROMs under [projectRoot], checking both standalone and multi-game
     * (gbkt-examples) layouts.
     *
     * Standalone layout: build/gbkt/output/NAME.gb under [projectRoot]
     * Multi-game layout: gbkt-examples/NAME/build/gbkt/output/NAME.gb under [projectRoot]
     *
     * @param projectRoot Root directory to scan. Defaults to File(".").
     * @return Sorted list of [GameInfo] entries for all found ROMs.
     */
    fun scanForBuiltRoms(projectRoot: File = File(".")): List<GameInfo> {
        val results = mutableListOf<GameInfo>()

        // Standalone: build/gbkt/output/
        val standaloneDir = File(projectRoot, "build/gbkt/output")
        if (standaloneDir.isDirectory) {
            standaloneDir.listFiles { f -> f.extension == "gb" }?.forEach { rom ->
                val metadataFile = File(projectRoot, "build/gbkt/generated/game_metadata.json")
                results.add(GameInfo(rom.nameWithoutExtension, rom, metadataFile.exists()))
            }
        }

        // Multi-game: gbkt-examples/NAME/build/gbkt/output/
        val examplesDir = File(projectRoot, "gbkt-examples")
        if (examplesDir.isDirectory) {
            examplesDir.listFiles { f -> f.isDirectory }?.forEach { gameDir ->
                val outputDir = File(gameDir, "build/gbkt/output")
                outputDir.listFiles { f -> f.extension == "gb" }?.forEach { rom ->
                    val metadataFile = File(gameDir, "build/gbkt/generated/game_metadata.json")
                    results.add(GameInfo(rom.nameWithoutExtension, rom, metadataFile.exists()))
                }
            }
        }

        return results.sortedBy { it.name }
    }
}
