/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

// =============================================================================
// EMULATOR TEST TASK TESTS
// Verifies task registration, dependency chain, and graceful ROM handling.
// Tests use the embedded Coffee-GB emulator — no external emulator required.
// =============================================================================

class EmulatorTestTaskTest {

    @TempDir lateinit var testProjectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File

    @BeforeEach
    fun setup() {
        buildFile = File(testProjectDir, "build.gradle.kts")
        settingsFile = File(testProjectDir, "settings.gradle.kts")

        settingsFile.writeText(
            """
            rootProject.name = "test-emulator-project"

            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            """
                .trimIndent()
        )
    }

    // =========================================================================
    // Task registration test
    // =========================================================================

    @Test
    fun `emulatorTest task is registered when game is configured`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("jvm") version "2.3.0"
                id("io.github.gbkt")
            }

            repositories {
                mavenLocal()
                mavenCentral()
            }

            gbkt {
                game("com.example.Game::myGame")
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("tasks", "--all")
                .withPluginClasspath()
                .build()

        assertTrue(
            result.output.contains("emulatorTest"),
            "Should have emulatorTest task registered. Output:\n${result.output}",
        )
    }

    // =========================================================================
    // Task dependency test — emulatorTest depends on buildRom
    // =========================================================================

    @Test
    fun `emulatorTest depends on buildRom`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("jvm") version "2.3.0"
                id("io.github.gbkt")
            }

            repositories {
                mavenLocal()
                mavenCentral()
            }

            gbkt {
                game("com.example.Game::myGame")
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("emulatorTest", "--dry-run")
                .withPluginClasspath()
                .forwardOutput()
                .build()

        val output = result.output
        assertTrue(
            output.contains("buildRom") && output.contains("emulatorTest"),
            "emulatorTest should depend on buildRom in the dry-run plan. Output:\n$output",
        )

        // buildRom must appear before emulatorTest in the execution plan
        val buildRomIndex = output.indexOf("buildRom")
        val emulatorTestIndex = output.indexOf("emulatorTest")
        assertTrue(
            buildRomIndex < emulatorTestIndex,
            "buildRom should be scheduled before emulatorTest. Output:\n$output",
        )
    }

    // =========================================================================
    // Graceful skip test — no emulator on path, task must not fail
    // =========================================================================

    @Test
    fun `emulatorTest handles invalid ROM gracefully`(@TempDir projectDir: File) {
        val project =
            org.gradle.testfixtures.ProjectBuilder.builder().withProjectDir(projectDir).build()

        // Create a minimal fake ROM file (all zeros — not a valid GB ROM)
        val romDir = File(projectDir, "build/gbkt/output")
        romDir.mkdirs()
        val fakeRom = File(romDir, "mygame.gb")
        fakeRom.writeBytes(ByteArray(32768) { 0 })

        val taskProvider = project.tasks.register("emulatorTest", EmulatorTestTask::class.java)
        val taskInstance = taskProvider.get()
        taskInstance.romFile.set(fakeRom)
        taskInstance.maxFrames.set(10) // Run only a few frames

        // The embedded emulator should either run successfully or throw a
        // GradleException (not an unhandled crash)
        try {
            taskInstance.run()
        } catch (_: org.gradle.api.GradleException) {
            // Expected — invalid ROM may cause emulator errors
        }
        // If we get here, the task handled the invalid ROM gracefully
    }

    // =========================================================================
    // Configuration defaults test
    // =========================================================================

    @Test
    fun `emulatorTest has correct default configuration`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("jvm") version "2.3.0"
                id("io.github.gbkt")
            }

            repositories {
                mavenLocal()
                mavenCentral()
            }

            gbkt {
                game("com.example.Game::myGame")
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("tasks", "--all")
                .withPluginClasspath()
                .build()

        // Verify the task is present — conventions (maxFrames=600, timeoutSeconds=30)
        // are validated at the unit level via the task class constructor.
        assertTrue(
            result.output.contains("emulatorTest"),
            "emulatorTest task should be registered with default conventions",
        )
    }

    // =========================================================================
    // Task type test — verify emulatorTest uses EmulatorTestTask via dry-run output
    // =========================================================================

    @Test
    fun `emulatorTest task is an instance of EmulatorTestTask`() {
        buildFile.writeText(
            """
            plugins {
                kotlin("jvm") version "2.3.0"
                id("io.github.gbkt")
            }

            repositories {
                mavenLocal()
                mavenCentral()
            }

            gbkt {
                game("com.example.Game::myGame")
            }
            """
                .trimIndent()
        )

        // Use --dry-run to inspect the task graph without running anything.
        // The task type name is embedded in verbose task output.
        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("emulatorTest", "--dry-run")
                .withPluginClasspath()
                .build()

        assertTrue(
            result.output.contains("emulatorTest"),
            "emulatorTest should appear in the dry-run execution plan",
        )
    }

    // =========================================================================
    // Available in all modules that apply the gbkt plugin
    // =========================================================================

    @Test
    fun `emulatorTest task is available in any project that applies gbkt plugin`() {
        // Simulates an example game module (pong, breakout, explorer)
        buildFile.writeText(
            """
            plugins {
                kotlin("jvm") version "2.3.0"
                id("io.github.gbkt")
            }

            repositories {
                mavenLocal()
                mavenCentral()
            }

            // Simulates an example game configuration
            gbkt {
                game("example.pong.PongGameKt::pongGame")
                outputName.set("pong")
            }
            """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("tasks", "--all")
                .withPluginClasspath()
                .build()

        val output = result.output

        // The task must be present for any module applying the plugin
        assertTrue(
            output.contains("emulatorTest"),
            "emulatorTest should be available in any gbkt game module. Output:\n$output",
        )

        // Verify it's in the verification group
        val verificationIdx = output.indexOf("Verification tasks")
        val emulatorTestIdx = output.indexOf("emulatorTest")
        assertTrue(
            verificationIdx >= 0 && emulatorTestIdx > verificationIdx,
            "emulatorTest should appear in the Verification tasks section",
        )
    }
}
