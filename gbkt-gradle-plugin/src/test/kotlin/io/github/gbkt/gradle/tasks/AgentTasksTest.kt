/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

// =============================================================================
// AGENT TASKS TESTS
// Verifies task registration, group assignment, and default property conventions
// for all 5 gbkt-agent tasks: captureScreenshot, runScript, readVariable,
// saveState, diffScreenshots.
// =============================================================================

class AgentTasksTest {

    @TempDir lateinit var testProjectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File

    @BeforeEach
    fun setup() {
        buildFile = File(testProjectDir, "build.gradle.kts")
        settingsFile = File(testProjectDir, "settings.gradle.kts")

        settingsFile.writeText(
            """
            rootProject.name = "test-agent-tasks-project"

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
    }

    // =========================================================================
    // All 5 agent tasks are registered in the gbkt-agent group
    // =========================================================================

    @Test
    fun `all 5 agent tasks are registered when game is configured`() {
        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("tasks", "--all")
                .withPluginClasspath()
                .build()

        val taskNames =
            listOf("captureScreenshot", "runScript", "readVariable", "saveState", "diffScreenshots")
        for (name in taskNames) {
            assertTrue(
                result.output.contains(name),
                "Task '$name' should be registered. Output:\n${result.output}",
            )
        }
    }

    // =========================================================================
    // All agent tasks appear under gbkt-agent group in the tasks listing
    // =========================================================================

    @Test
    fun `agent tasks appear in the gbkt-agent group`() {
        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("tasks", "--all")
                .withPluginClasspath()
                .build()

        val output = result.output

        // The tasks listing prints group headers like "Gbkt-agent tasks"
        // (Gradle capitalizes the first letter of the group name in the header).
        assertTrue(
            output.contains("gbkt-agent", ignoreCase = true),
            "Output should contain 'gbkt-agent' group header. Output:\n$output",
        )
    }

    // =========================================================================
    // Task dependency: rom-dependent tasks depend on buildRom
    // =========================================================================

    @Test
    fun `captureScreenshot depends on buildRom`() {
        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("captureScreenshot", "--dry-run")
                .withPluginClasspath()
                .forwardOutput()
                .build()

        val output = result.output
        assertTrue(
            output.contains("buildRom") && output.contains("captureScreenshot"),
            "captureScreenshot should depend on buildRom in dry-run. Output:\n$output",
        )

        val buildRomIdx = output.indexOf("buildRom")
        val captureIdx = output.indexOf("captureScreenshot")
        assertTrue(
            buildRomIdx < captureIdx,
            "buildRom should be scheduled before captureScreenshot. Output:\n$output",
        )
    }

    @Test
    fun `readVariable depends on buildRom`() {
        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("readVariable", "--dry-run")
                .withPluginClasspath()
                .forwardOutput()
                .build()

        val output = result.output
        assertTrue(
            output.contains("buildRom") && output.contains("readVariable"),
            "readVariable should depend on buildRom in dry-run. Output:\n$output",
        )
    }

    @Test
    fun `saveState depends on buildRom`() {
        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("saveState", "--dry-run")
                .withPluginClasspath()
                .forwardOutput()
                .build()

        val output = result.output
        assertTrue(
            output.contains("buildRom") && output.contains("saveState"),
            "saveState should depend on buildRom in dry-run. Output:\n$output",
        )
    }

    // =========================================================================
    // CaptureScreenshotTask default conventions
    // =========================================================================

    @Test
    fun `captureScreenshot has correct default conventions`(@TempDir projectDir: File) {
        val task =
            org.gradle.testfixtures.ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build()
                .tasks
                .register("captureScreenshot", CaptureScreenshotTask::class.java)
                .get()

        assertEquals(60, task.frames.get(), "frames default should be 60")
        assertEquals("screenshot", task.label.get(), "label default should be 'screenshot'")
    }

    // =========================================================================
    // ReadVariableTask default conventions
    // =========================================================================

    @Test
    fun `readVariable has correct default conventions`(@TempDir projectDir: File) {
        val task =
            org.gradle.testfixtures.ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build()
                .tasks
                .register("readVariable", ReadVariableTask::class.java)
                .get()

        assertEquals(60, task.frames.get(), "frames default should be 60")
        assertEquals("all", task.variableName.get(), "variableName default should be 'all'")
    }

    // =========================================================================
    // SaveStateTask default conventions
    // =========================================================================

    @Test
    fun `saveState has correct default conventions`(@TempDir projectDir: File) {
        val task =
            org.gradle.testfixtures.ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build()
                .tasks
                .register("saveState", SaveStateTask::class.java)
                .get()

        assertEquals(60, task.frames.get(), "frames default should be 60")
    }

    // =========================================================================
    // DiffScreenshotsTask default conventions
    // =========================================================================

    @Test
    fun `diffScreenshots has correct default tolerance`(@TempDir projectDir: File) {
        val task =
            org.gradle.testfixtures.ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build()
                .tasks
                .register("diffScreenshots", DiffScreenshotsTask::class.java)
                .get()

        assertEquals(0.0, task.tolerance.get(), "tolerance default should be 0.0")
    }

    // =========================================================================
    // All agent tasks have correct group assignment via ProjectBuilder
    // =========================================================================

    @Test
    fun `all agent task classes use gbkt-agent group`(@TempDir projectDir: File) {
        val project =
            org.gradle.testfixtures.ProjectBuilder.builder().withProjectDir(projectDir).build()

        val captureTask =
            project.tasks.register("captureScreenshot", CaptureScreenshotTask::class.java).get()
        val runScriptTask =
            project.tasks.register("runScript", RunInputScriptTask::class.java).get()
        val readVarTask = project.tasks.register("readVariable", ReadVariableTask::class.java).get()
        val saveStateTask = project.tasks.register("saveState", SaveStateTask::class.java).get()
        val diffTask =
            project.tasks.register("diffScreenshots", DiffScreenshotsTask::class.java).get()

        assertNotNull(captureTask)
        assertNotNull(runScriptTask)
        assertNotNull(readVarTask)
        assertNotNull(saveStateTask)
        assertNotNull(diffTask)

        assertEquals("gbkt-agent", captureTask.group)
        assertEquals("gbkt-agent", runScriptTask.group)
        assertEquals("gbkt-agent", readVarTask.group)
        assertEquals("gbkt-agent", saveStateTask.group)
        assertEquals("gbkt-agent", diffTask.group)
    }

    // =========================================================================
    // RunInputScriptTask script parser correctness
    // =========================================================================

    @Test
    fun `parseScript handles all command types`(@TempDir projectDir: File) {
        val task =
            org.gradle.testfixtures.ProjectBuilder.builder()
                .withProjectDir(projectDir)
                .build()
                .tasks
                .register("runScript", RunInputScriptTask::class.java)
                .get()

        val script =
            """
            # test script
            wait 60
            press RIGHT 30
            press A
            hold LEFT
            release LEFT
            screenshot my_label
            """
                .trimIndent()

        val (inputScript, labels) = task.parseScript(script)

        assertEquals(
            6,
            inputScript.steps.size,
            "Should have 6 input steps (including wait-1 for screenshot)",
        )
        assertEquals(listOf("my_label"), labels, "Should have 1 screenshot label")
    }
}
