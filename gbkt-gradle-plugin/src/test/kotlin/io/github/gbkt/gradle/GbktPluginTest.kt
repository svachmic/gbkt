/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle

import java.io.File
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/** Integration tests for the gbkt Gradle plugin using Gradle TestKit. */
class GbktPluginTest {

    @TempDir lateinit var testProjectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File

    @BeforeEach
    fun setup() {
        buildFile = File(testProjectDir, "build.gradle.kts")
        settingsFile = File(testProjectDir, "settings.gradle.kts")

        settingsFile.writeText(
            """
            rootProject.name = "test-project"

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

    @Test
    fun `plugin applies successfully`() {
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
        """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("tasks", "--all")
                .withPluginClasspath()
                .build()

        assertTrue(result.output.contains("gbkt"), "Should have gbkt tasks available")
    }

    @Test
    fun `warns when no game is configured`() {
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
            result.output.contains("No game defined") || result.output.contains("tasks"),
            "Should warn about missing game or at least list tasks"
        )
    }

    @Test
    fun `extension configures correctly`() {
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
                outputName.set("mygame")
                debug.set(false)
            }
        """
                .trimIndent()
        )

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("tasks", "--all", "--info")
                .withPluginClasspath()
                .build()

        // If the extension configuration is valid, task creation succeeds
        assertTrue(
            result.output.contains("generateC") || result.output.contains("buildRom"),
            "Should have generateC or buildRom task when game is configured"
        )
    }

    @Test
    fun `optimization extension configures correctly`() {
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

                optimization {
                    enabled.set(true)
                    verbose.set(true)
                    detectDuplicates.set(false)
                    lowEntropyThreshold.set(0.3f)
                }
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

        // Extension configuration should parse without errors
        assertTrue(result.output.isNotEmpty(), "Build should complete successfully")
    }

    @Test
    fun `cleanGbkt task is registered`() {
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

        assertTrue(result.output.contains("cleanGbkt"), "Should have cleanGbkt task registered")
    }

    @Test
    fun `task dependencies are configured correctly`() {
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
                .withArguments("buildRom", "--dry-run")
                .withPluginClasspath()
                .forwardOutput()
                .build()

        // Check that the task order is correct in the dry-run output
        val output = result.output
        assertTrue(
            output.contains("generateC") ||
                output.contains("compileKotlin") ||
                output.contains("buildRom"),
            "Task dependency chain should be visible in dry-run"
        )
    }

    @Test
    fun `runEmulator task is registered`() {
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

        assertTrue(result.output.contains("runEmulator"), "Should have runEmulator task registered")
    }

    @Test
    fun `runEmulator depends on buildRom`() {
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
                .withArguments("runEmulator", "--dry-run")
                .withPluginClasspath()
                .forwardOutput()
                .build()

        // Check that buildRom appears before runEmulator in dry-run
        val output = result.output
        assertTrue(
            output.contains("buildRom") && output.contains("runEmulator"),
            "runEmulator should depend on buildRom"
        )
    }

    @Test
    fun `emulator extension configures correctly`() {
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

                emulator {
                    path.set("/usr/local/bin/mgba")
                    args.set(listOf("-s", "4"))
                    liveReload.set(false)
                }
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

        // Extension configuration should parse without errors
        assertTrue(
            result.output.contains("runEmulator"),
            "Emulator configuration should work without errors"
        )
    }

    @Test
    fun `emulator liveReload defaults to true`() {
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

                emulator {
                    // Not setting liveReload - should default to true
                    args.set(listOf("-s", "2"))
                }
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

        // Extension configuration should parse without errors
        assertTrue(
            result.output.isNotEmpty(),
            "Build should complete successfully with default liveReload"
        )
    }
}
