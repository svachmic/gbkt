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
import org.gradle.work.DisableCachingByDefault

/**
 * Task that launches the embedded Coffee-GB emulator with the built ROM.
 *
 * Uses the embedded emulator by default — no external emulator installation required. If
 * [externalEmulator] is configured, launches that executable instead.
 *
 * The embedded emulator opens a Swing window with the game display, toolbar, log viewer, and memory
 * inspector. The Gradle task returns immediately after launching the window; the JVM stays alive
 * while the window is open.
 */
@DisableCachingByDefault(
    because = "Emulator launch opens an interactive window — cannot be cached or replayed"
)
abstract class RunEmulatorTask : DefaultTask() {

    /** ROM file to run in the emulator. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val romFile: RegularFileProperty

    /** Scale factor for the emulator display window. Default: 4 (640x576). */
    @get:Input @get:Optional abstract val scale: Property<Int>

    /** Run in headless mode (no display window). Default: false. */
    @get:Input @get:Optional abstract val headless: Property<Boolean>

    /**
     * Optional path to an external emulator executable (e.g., mGBA). When set, the external
     * emulator is launched instead of the embedded Coffee-GB emulator. When unset (default), the
     * embedded emulator is used.
     */
    @get:Input @get:Optional abstract val externalEmulator: Property<String>

    /** Build directory for generated scripts and logs. */
    @get:Internal abstract val buildDirectory: DirectoryProperty

    init {
        description = "Run the built ROM in the embedded Coffee-GB emulator"
        group = "gbkt"
    }

    @TaskAction
    fun run() {
        val rom = romFile.get().asFile

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

        // Check if an external emulator is configured
        val externalPath = externalEmulator.orNull
        if (externalPath != null) {
            logger.lifecycle("Launching external emulator: $externalPath ${rom.name}")
            val process = ProcessBuilder(externalPath, rom.absolutePath).inheritIO().start()
            process.waitFor()
            return
        }

        // Default: use embedded Coffee-GB emulator
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

        logger.lifecycle("Launching embedded emulator: ${rom.name}")
        logger.lifecycle("Debug log: ${config.logFile?.absolutePath}")

        val session = EmulatorSession(config)
        session.launch() // Non-blocking — Swing window keeps JVM alive

        logger.lifecycle("Emulator started. Close window to exit.")
    }
}
