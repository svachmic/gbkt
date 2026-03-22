/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import io.github.gbkt.emulator.EmulatorConfig
import io.github.gbkt.emulator.EmulatorSession
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Task that launches the embedded Coffee-GB emulator with full debug tooling enabled.
 *
 * Features:
 * - Opens the main emulator window with game display and toolbar
 * - Enables the LogCat window for real-time debug log viewing
 * - Enables the Memory Inspector window for live memory inspection
 * - Writes a persistent debug log file alongside the ROM
 * - Loads source maps for Kotlin DSL → C line number resolution
 *
 * Usage:
 * ```
 * ./gradlew debugEmulator
 * ```
 *
 * After launch, tail the debug log for continuous output:
 * ```
 * tail -f build/gbkt/logs/debug.log
 * ```
 */
abstract class DebugEmulatorTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val romFile: RegularFileProperty

    @get:Internal abstract val buildDirectory: DirectoryProperty

    /** Run in headless mode (no display window). Default: false. */
    @get:Input @get:Optional abstract val headless: Property<Boolean>

    /** Display window scale factor. Default: 4 (640x576). */
    @get:Input @get:Optional abstract val scale: Property<Int>

    init {
        description = "Run ROM in the embedded emulator with full debug tooling"
        group = "gbkt"
    }

    @TaskAction
    fun run() {
        val rom = romFile.get().asFile
        if (!rom.exists()) {
            throw GradleException("ROM not found: ${rom.absolutePath}. Run buildRom first.")
        }

        val buildDir = buildDirectory.get().asFile
        val logsDir = File(buildDir, "gbkt/logs")
        logsDir.mkdirs()

        val config =
            EmulatorConfig(
                romFile = rom,
                headless = headless.getOrElse(false),
                scale = scale.getOrElse(4),
                sourceMapsDir = File(buildDir, "gbkt/generated"),
                logFile = File(logsDir, "debug.log"),
            )

        logger.lifecycle("Launching debug emulator: ${rom.name}")
        logger.lifecycle("Debug log: ${config.logFile?.absolutePath}")

        val session = EmulatorSession(config)
        session.launch() // Non-blocking — Swing window keeps JVM alive

        logger.lifecycle("Debug emulator started. Close window to exit.")
        logger.lifecycle("Tail the debug log: tail -f ${config.logFile?.absolutePath}")
    }
}
