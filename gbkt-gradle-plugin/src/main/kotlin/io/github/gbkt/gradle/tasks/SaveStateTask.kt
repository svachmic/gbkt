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
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that saves (and optionally loads) emulator state checkpoints for ROM testing.
 *
 * Runs the ROM headlessly for [frames] frames, optionally loading a prior state first, then saves
 * the current emulator state (WRAM+OAM+HRAM) to a binary GBST file.
 *
 * When [loadStateFile] is configured, the task will:
 * 1. Start the emulator
 * 2. Load the specified state
 * 3. Run [frames] additional frames
 * 4. Save to [stateFile]
 *
 * When only saving (no [loadStateFile]):
 * 1. Start the emulator
 * 2. Run [frames] frames from the beginning
 * 3. Save to [stateFile]
 *
 * Usage:
 * ```
 * ./gradlew saveState --frames=300 --state-file=build/gbkt/states/checkpoint.gbst
 * ./gradlew saveState --frames=60 --load-state-file=build/gbkt/states/checkpoint.gbst --state-file=build/gbkt/states/checkpoint2.gbst
 * ```
 */
abstract class SaveStateTask : DefaultTask() {

    /** ROM file to run. Must exist at task execution time. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val romFile: RegularFileProperty

    /** Number of frames to run before saving state. Default: 60. */
    @get:Input abstract val frames: Property<Int>

    /**
     * Destination file for the saved emulator state (GBST binary). Parent directories are created
     * automatically.
     */
    @get:OutputFile abstract val stateFile: RegularFileProperty

    /** Optional SDCC .sym file for variable name resolution. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val symFile: RegularFileProperty

    /**
     * Optional state file to load before running frames. When provided, the task resumes from this
     * checkpoint rather than starting from ROM boot.
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val loadStateFile: RegularFileProperty

    init {
        group = "gbkt-agent"
        description = "Save emulator state checkpoint after N frames (agent-callable)"
        frames.convention(60)
    }

    @TaskAction
    fun run() {
        val rom = romFile.get().asFile
        if (!rom.exists()) {
            throw GradleException("ROM file not found: ${rom.absolutePath}. Run buildRom first.")
        }

        val frameCount = frames.get()
        val outFile = stateFile.get().asFile
        outFile.parentFile?.mkdirs()

        val loadFrom = loadStateFile.orNull?.asFile

        if (loadFrom != null) {
            logger.lifecycle(
                "saveState: loading state from ${loadFrom.name}, running $frameCount frames, saving to ${outFile.name}..."
            )
        } else {
            logger.lifecycle(
                "saveState: running ${rom.name} for $frameCount frames, saving to ${outFile.name}..."
            )
        }

        val config = AgentSessionConfig(romFile = rom, symFile = symFile.orNull?.asFile)

        AgentDebugSession(config).use { session ->
            session.start()

            if (loadFrom != null) {
                if (!loadFrom.exists()) {
                    throw GradleException("loadStateFile not found: ${loadFrom.absolutePath}")
                }
                session.loadState(loadFrom)
                logger.lifecycle("saveState: state loaded from ${loadFrom.absolutePath}")
            }

            session.runFrames(frameCount)
            session.saveState(outFile)
            logger.lifecycle("saveState: DONE — ${outFile.absolutePath}")
        }
    }
}
