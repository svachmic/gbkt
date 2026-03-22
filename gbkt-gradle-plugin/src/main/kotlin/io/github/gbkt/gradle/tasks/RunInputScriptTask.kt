/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import io.github.gbkt.emulator.agent.AgentDebugSession
import io.github.gbkt.emulator.agent.AgentSessionConfig
import io.github.gbkt.emulator.agent.Button
import io.github.gbkt.emulator.agent.InputScript
import io.github.gbkt.emulator.agent.InputScriptBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task that executes an input script against a ROM in the headless embedded emulator.
 *
 * The script file uses a simple line-based text format:
 * ```
 * wait 60
 * press RIGHT 30
 * press A
 * wait 120
 * screenshot after_input
 * ```
 *
 * Supported commands:
 * - `wait <frames>` — advance [frames] frames with no input
 * - `press <BUTTON>` — press and release a button for 1 frame
 * - `press <BUTTON> <frames>` — press and hold a button for [frames] frames
 * - `hold <BUTTON>` — press button and hold until `release`
 * - `release <BUTTON>` — release a held button
 * - `screenshot <label>` — capture a screenshot with the given label
 * - `# comment` — ignored
 *
 * Button names (case-insensitive): UP, DOWN, LEFT, RIGHT, A, B, START, SELECT
 *
 * Usage:
 * ```
 * ./gradlew runScript --script=scripts/pong_test.txt
 * ```
 */
abstract class RunInputScriptTask : DefaultTask() {

    /** ROM file to run. Must exist at task execution time. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val romFile: RegularFileProperty

    /** Script file containing line-based input commands. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val scriptFile: RegularFileProperty

    /** Optional SDCC .sym file for variable name resolution. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val symFile: RegularFileProperty

    init {
        group = "gbkt-agent"
        description = "Execute an input script against ROM in headless emulator (agent-callable)"
    }

    @TaskAction
    fun run() {
        val rom = romFile.get().asFile
        if (!rom.exists()) {
            throw GradleException("ROM file not found: ${rom.absolutePath}. Run buildRom first.")
        }

        val script = scriptFile.get().asFile
        if (!script.exists()) {
            throw GradleException("Script file not found: ${script.absolutePath}")
        }

        logger.lifecycle("runScript: executing ${script.name} against ${rom.name}...")

        val (inputScript, screenshotCommands) = parseScript(script.readText())

        val screenshotDir = rom.parentFile.resolve("screenshots").also { it.mkdirs() }

        val config =
            AgentSessionConfig(
                romFile = rom,
                symFile = symFile.orNull?.asFile,
                screenshotDir = screenshotDir,
            )

        AgentDebugSession(config).use { session ->
            session.start()

            // Execute the input script (covers press/hold/release/wait steps)
            session.executeInputScript(inputScript)

            // Execute screenshot commands at the end
            for (label in screenshotCommands) {
                val png = session.captureScreenshot(label)
                logger.lifecycle("runScript: screenshot — ${png.absolutePath}")
            }

            logger.lifecycle("runScript: DONE — ${session.frameCount} frames executed")
        }
    }

    /**
     * Parses the line-based script format into an [InputScript] and a list of screenshot labels.
     *
     * Screenshot commands are extracted separately because they interleave with emulator frames in
     * a way that requires direct session access. For v1 simplicity, all screenshot commands are
     * collected and executed after the input script completes.
     */
    internal fun parseScript(text: String): Pair<InputScript, List<String>> {
        val builder = InputScriptBuilder()
        val screenshotLabels = mutableListOf<String>()

        text.lines().forEachIndexed { lineNum, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed

            val parts = line.split("\\s+".toRegex())
            when (parts[0].lowercase()) {
                "wait" -> {
                    val frames =
                        parts.getOrNull(1)?.toIntOrNull()
                            ?: throw GradleException(
                                "Script line ${lineNum + 1}: 'wait' requires an integer frame count. Got: $line"
                            )
                    builder.wait(frames)
                }
                "press" -> {
                    val button = parseButton(parts.getOrNull(1), lineNum + 1, line)
                    val frames = parts.getOrNull(2)?.toIntOrNull() ?: 1
                    builder.press(button, frames)
                }
                "hold" -> {
                    val button = parseButton(parts.getOrNull(1), lineNum + 1, line)
                    builder.hold(button)
                }
                "release" -> {
                    val button = parseButton(parts.getOrNull(1), lineNum + 1, line)
                    builder.release(button)
                }
                "screenshot" -> {
                    val label = parts.getOrNull(1) ?: "screenshot"
                    screenshotLabels += label
                    // Insert a wait-1-frame step so the screenshot is taken at the right frame
                    builder.wait(1)
                }
                else ->
                    throw GradleException(
                        "Script line ${lineNum + 1}: unknown command '${parts[0]}'. " +
                            "Supported: wait, press, hold, release, screenshot. Got: $line"
                    )
            }
        }

        return builder.build() to screenshotLabels
    }

    private fun parseButton(raw: String?, lineNum: Int, fullLine: String): Button {
        val name =
            raw?.uppercase()
                ?: throw GradleException(
                    "Script line $lineNum: missing button name. Got: $fullLine"
                )
        return try {
            Button.valueOf(name)
        } catch (_: IllegalArgumentException) {
            throw GradleException(
                "Script line $lineNum: unknown button '$raw'. " +
                    "Valid buttons: ${Button.entries.joinToString()}. Got: $fullLine"
            )
        }
    }
}
