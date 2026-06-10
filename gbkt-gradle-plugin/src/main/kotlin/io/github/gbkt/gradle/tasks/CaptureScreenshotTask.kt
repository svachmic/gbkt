/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import io.github.gbkt.emulator.agent.AgentDebugSession
import io.github.gbkt.emulator.agent.AgentSessionConfig
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that captures a screenshot from a ROM after running a configurable number of frames.
 *
 * Runs the ROM headlessly in the embedded Coffee-GB emulator for the specified number of frames,
 * then captures the LCD frame buffer as a 160x144 PNG with a JSON metadata sidecar.
 *
 * Usage:
 * ```
 * ./gradlew captureScreenshot --frames=120 --label=title_screen
 * ```
 *
 * The screenshot is written to the configured [screenshotDir] (default: `build/gbkt/screenshots`).
 * The output path is logged to `logger.lifecycle` for agent consumption.
 */
abstract class CaptureScreenshotTask : DefaultTask() {

    /** ROM file to run. Must exist at task execution time. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val romFile: RegularFileProperty

    /** Number of frames to run before capturing. Default: 60 (1 second at 60fps). */
    @get:Input abstract val frames: Property<Int>

    /** Label used as the file name prefix for the screenshot. Default: "screenshot". */
    @get:Input abstract val label: Property<String>

    /** Optional SDCC .sym file for variable name resolution in the JSON sidecar. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val symFile: RegularFileProperty

    /** Directory to write captured screenshots. Default: `build/gbkt/screenshots`. */
    @get:OutputDirectory abstract val screenshotDir: DirectoryProperty

    init {
        group = "gbkt-agent"
        description = "Capture screenshot from ROM after N frames (headless, agent-callable)"
        frames.convention(60)
        label.convention("screenshot")
    }

    @TaskAction
    fun run() {
        val rom = romFile.get().asFile
        if (!rom.exists()) {
            throw GradleException("ROM file not found: ${rom.absolutePath}. Run buildRom first.")
        }

        val frameCount = frames.get()
        val screenshotLabel = label.get()
        val outDir = screenshotDir.get().asFile
        outDir.mkdirs()

        logger.lifecycle("captureScreenshot: running ${rom.name} for $frameCount frames...")

        val config =
            AgentSessionConfig(
                romFile = rom,
                symFile = symFile.orNull?.asFile,
                screenshotDir = outDir,
            )

        AgentDebugSession(config).use { session ->
            session.start()
            session.runFrames(frameCount)
            val png = session.captureScreenshot(screenshotLabel)
            logger.lifecycle("captureScreenshot: DONE — ${png.absolutePath}")
        }
    }
}
