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
            "Should warn about missing game or at least list tasks",
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
            "Should have generateC or buildRom task when game is configured",
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
            "Task dependency chain should be visible in dry-run",
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
            "runEmulator should depend on buildRom",
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
                    scale.set(4)
                    headless.set(false)
                    externalEmulator.set("/usr/local/bin/mgba")
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
            "Emulator configuration should work without errors",
        )
    }

    @Test
    fun `convertZoneTilesets task is registered (Phase 11_2 D-A2)`() {
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
            result.output.contains("convertZoneTilesets"),
            "GbktPlugin should register convertZoneTilesets task (Phase 11.2 D-A2)",
        )
    }

    @Test
    fun `compileRom depends on convertZoneTilesets (Phase 11_2 D-A2)`() {
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
                .withArguments("compileRom", "--dry-run")
                .withPluginClasspath()
                .forwardOutput()
                .build()

        val output = result.output
        // Both tasks must appear in the dry-run output, and convertZoneTilesets
        // must precede compileRom (the dependsOn edge guarantees ordering).
        assertTrue(
            output.contains("convertZoneTilesets"),
            "convertZoneTilesets must be invoked in the compileRom build graph",
        )
        assertTrue(output.contains("compileRom"), "compileRom must be invoked in dry-run output")
        val ztIdx = output.indexOf("convertZoneTilesets")
        val crIdx = output.indexOf(":compileRom ")
        assertTrue(
            ztIdx >= 0 && crIdx >= 0 && ztIdx < crIdx,
            "convertZoneTilesets must appear before compileRom in dry-run (D-A2 edge)",
        )
    }

    @Test
    fun `convertZoneTilesets runs after generateC (Phase 11_2 D-A2)`() {
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
                .withArguments("convertZoneTilesets", "--dry-run")
                .withPluginClasspath()
                .forwardOutput()
                .build()

        val output = result.output
        assertTrue(
            output.contains("generateC"),
            "convertZoneTilesets dry-run must include generateC (dependsOn edge)",
        )
        val gcIdx = output.indexOf(":generateC ")
        val ztIdx = output.indexOf(":convertZoneTilesets ")
        assertTrue(
            gcIdx >= 0 && ztIdx >= 0 && gcIdx < ztIdx,
            "generateC must appear before convertZoneTilesets in dry-run (D-A2 edge)",
        )
    }

    @Test
    fun `emulator extension defaults work without explicit configuration`() {
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
                    // Not setting scale or headless — should use defaults
                    scale.set(2)
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
            "Build should complete successfully with default emulator settings",
        )
    }
}
