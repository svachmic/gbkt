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
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Gradle task that reads a named DSL variable from a running ROM after N frames.
 *
 * Runs the ROM headlessly for [frames] frames, then reads the named variable from the emulator
 * memory using the .sym file for address resolution. The value is printed via `logger.lifecycle`
 * for agent consumption.
 *
 * Use `variableName = "all"` to dump all variables from the sym file.
 *
 * Usage:
 * ```
 * ./gradlew readVariable --variable=score --frames=300
 * ./gradlew readVariable --variable=all --frames=300
 * ```
 *
 * A sym file must be provided for variable name resolution; without it, variable names cannot be
 * resolved and the task will log a warning.
 */
@DisableCachingByDefault(
    because = "Variable read task reads live emulator state — caching would return stale values"
)
abstract class ReadVariableTask : DefaultTask() {

    /** ROM file to run. Must exist at task execution time. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val romFile: RegularFileProperty

    /** Number of frames to run before reading. Default: 60 (1 second at 60fps). */
    @get:Input abstract val frames: Property<Int>

    /**
     * Name of the DSL variable to read. Use "all" to dump all variables from the sym file. Default:
     * "all".
     */
    @get:Input abstract val variableName: Property<String>

    /** Optional SDCC .sym file for resolving DSL variable names to memory addresses. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val symFile: RegularFileProperty

    init {
        group = "gbkt-agent"
        description = "Read a named DSL variable from ROM after N frames (agent-callable)"
        frames.convention(60)
        variableName.convention("all")
    }

    @TaskAction
    fun run() {
        val rom = romFile.get().asFile
        if (!rom.exists()) {
            throw GradleException("ROM file not found: ${rom.absolutePath}. Run buildRom first.")
        }

        val frameCount = frames.get()
        val varName = variableName.get()

        if (symFile.orNull == null) {
            logger.warn("readVariable: no symFile configured — variable names cannot be resolved.")
        }

        logger.lifecycle(
            "readVariable: running ${rom.name} for $frameCount frames, then reading '$varName'..."
        )

        val config = AgentSessionConfig(romFile = rom, symFile = symFile.orNull?.asFile)

        AgentDebugSession(config).use { session ->
            session.start()
            session.runFrames(frameCount)

            if (varName == "all") {
                val vars = session.readAllVariables()
                if (vars.isEmpty()) {
                    logger.lifecycle(
                        "readVariable: no variables found (sym file empty or not provided)"
                    )
                } else {
                    logger.lifecycle("readVariable: ${vars.size} variable(s):")
                    vars.entries
                        .sortedBy { it.key }
                        .forEach { (name, value) ->
                            logger.lifecycle(
                                "  $name = $value (0x${value.toString(16).uppercase()})"
                            )
                        }
                }
            } else {
                val value = session.readVariable(varName)
                if (value == null) {
                    logger.lifecycle("readVariable: '$varName' not found in sym file")
                } else {
                    logger.lifecycle(
                        "readVariable: $varName = $value (0x${value.toString(16).uppercase()})"
                    )
                }
            }
        }
    }
}
