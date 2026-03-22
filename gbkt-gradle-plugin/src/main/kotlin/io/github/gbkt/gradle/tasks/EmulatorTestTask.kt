/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import io.github.gbkt.emulator.CoffeeGbEmulator
import io.github.gbkt.emulator.EmulatorConfig
import io.github.gbkt.emulator.LogLevel
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Task that runs a ROM in the headless embedded Coffee-GB emulator for automated CI testing.
 *
 * Uses the embedded emulator — no external emulator (SameBoy, mGBA) installation required. Safe to
 * run in CI environments without a display server.
 *
 * Usage:
 * ```
 * ./gradlew emulatorTest
 * ```
 *
 * The task depends on `buildRom` so the ROM is always up to date before testing.
 */
abstract class EmulatorTestTask : DefaultTask() {

    /** ROM file to test. Must exist at task execution time. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val romFile: RegularFileProperty

    /**
     * Number of frames to run the ROM before declaring the test passed. Default: 600 (10 seconds at
     * 60fps).
     */
    @get:Input abstract val maxFrames: Property<Int>

    /** Run in headless mode (no display window). Default: true (CI-safe). */
    @get:Input abstract val headless: Property<Boolean>

    init {
        group = "verification"
        description = "Runs ROM in headless embedded emulator for automated testing"
        maxFrames.convention(600) // 10 seconds at 60fps
        headless.convention(true) // CI-safe default
    }

    @TaskAction
    fun run() {
        val rom = romFile.get().asFile

        if (!rom.exists()) {
            throw GradleException("ROM file not found: ${rom.absolutePath}. Run buildRom first.")
        }

        val frames = maxFrames.get()
        logger.lifecycle("Running emulator test (embedded): ${rom.name} for $frames frames")

        val config =
            EmulatorConfig(
                romFile = rom,
                headless = headless.get(),
                logFile = null, // No log file for automated tests
            )

        val emulator = CoffeeGbEmulator(config)
        try {
            emulator.start()
            // stepFrame() requires the emulator to be paused — pause immediately after start
            emulator.pause()
            repeat(frames) { emulator.stepFrame() }

            // Check for error-level log entries BEFORE stopping — the deque is valid while running
            val errors = emulator.getDebugLog().filter { it.level == LogLevel.ERROR }
            if (errors.isNotEmpty()) {
                throw GradleException(
                    "Emulator test FAILED: ${rom.name} produced ${errors.size} errors:\n" +
                        errors.joinToString("\n") { it.formatted() }
                )
            }

            logger.lifecycle("Emulator test PASSED: ${rom.name} ran $frames frames without crash")
        } catch (e: Exception) {
            if (e is GradleException) throw e
            throw GradleException("Emulator test FAILED: ${rom.name} crashed: ${e.message}", e)
        } finally {
            try {
                emulator.stop()
            } catch (_: Exception) {}
        }
    }
}
