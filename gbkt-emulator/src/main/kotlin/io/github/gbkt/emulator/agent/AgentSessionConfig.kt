/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import io.github.gbkt.emulator.EmulatorConfig
import java.io.File

/**
 * Configuration for creating an [AgentDebugSession].
 *
 * Holds all settings required to run a headless agent playtest session against a Game Boy ROM. The
 * session always runs in headless mode (no display window); use [toEmulatorConfig] to produce the
 * underlying [EmulatorConfig].
 *
 * @param romFile Path to the Game Boy ROM file (.gb or .gbc). Must exist at session creation time.
 * @param symFile Optional SDCC `.sym` file for resolving DSL variable names to memory addresses.
 *   When provided, [AgentDebugSession.readVariable] and [AgentDebugSession.readAllVariables] are
 *   backed by this symbol table.
 * @param sourceMapsDir Optional directory containing .gbkt.map source map files produced by the
 *   gbkt Gradle plugin. When provided, debug log entries are enriched with Kotlin source locations.
 * @param screenshotDir Directory to write captured screenshots and JSON sidecars. Defaults to a
 *   `screenshots/` subdirectory adjacent to [romFile].
 * @param logFile Optional file to persist the debug log. When set, log entries are written in
 *   addition to the in-memory buffer.
 * @param watchVariables List of DSL variable names to include in screenshot JSON sidecars. When
 *   empty, all variables from the sym file are included in each sidecar snapshot.
 * @param gbcMode When true, the emulator is configured for GBC (Game Boy Color) mode. Use for ROMs
 *   compiled with `-Wm-yc` or `-Wm-yC` flags.
 */
data class AgentSessionConfig(
    val romFile: File,
    val symFile: File? = null,
    val sourceMapsDir: File? = null,
    val metadataFile: File? = null,
    val screenshotDir: File = File(romFile.parentFile ?: File("."), "screenshots"),
    val logFile: File? = null,
    val watchVariables: List<String> = emptyList(),
    val gbcMode: Boolean = false,
    /**
     * When `true` (default), the emulator runs without a display window — suitable for CI and
     * automated tests. When `false`, a Swing window opens showing the Game Boy LCD so the developer
     * can watch the agent play in real time (Netflix "smart monkey" style).
     */
    val headless: Boolean = true,
) {
    init {
        require(romFile.exists()) { "ROM file does not exist: ${romFile.absolutePath}" }
    }

    companion object {
        /**
         * Discovers companion files (sym, metadata, source maps) based on the standard Gradle
         * plugin output layout.
         *
         * Convention: ROM at `build/gbkt/output/game.gb` -> sym at `build/gbkt/output/game.noi`,
         * metadata at `build/gbkt/generated/game_metadata.json`, source maps at
         * `build/gbkt/generated/`.
         */
        fun discoverFiles(romFile: File, screenshotDir: File? = null): AgentSessionConfig {
            val outputDir = romFile.parentFile
            val gbktDir = outputDir?.parentFile
            val generatedDir = gbktDir?.let { File(it, "generated") }
            val baseName = romFile.nameWithoutExtension

            val symFile =
                outputDir?.let { File(it, "$baseName.noi") }?.takeIf { it.exists() }
                    ?: outputDir?.let { File(it, "$baseName.sym") }?.takeIf { it.exists() }
            val metadataFile =
                generatedDir?.let { File(it, "game_metadata.json") }?.takeIf { it.exists() }
            val sourceMapsDir = generatedDir?.takeIf { it.exists() }

            return AgentSessionConfig(
                romFile = romFile,
                symFile = symFile,
                metadataFile = metadataFile,
                sourceMapsDir = sourceMapsDir,
                screenshotDir = screenshotDir ?: File(outputDir ?: File("."), "screenshots"),
            )
        }
    }

    /**
     * Converts this agent session configuration to an [EmulatorConfig] suitable for creating a
     * [io.github.gbkt.emulator.CoffeeGbEmulator].
     *
     * When [headless] is `true` (default), no Swing window is created — suitable for CI. When
     * `false`, the emulator config enables display so [AgentDebugSession] can attach a viewer
     * window for the developer to watch the agent play.
     *
     * @return An [EmulatorConfig] with all relevant fields mapped.
     */
    fun toEmulatorConfig(): EmulatorConfig =
        EmulatorConfig(
            romFile = romFile,
            headless = headless,
            sourceMapsDir = sourceMapsDir,
            logFile = logFile,
            gbcMode = gbcMode,
        )
}
