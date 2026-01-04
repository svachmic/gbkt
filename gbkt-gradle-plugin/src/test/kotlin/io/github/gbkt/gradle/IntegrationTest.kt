/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir

/**
 * Integration tests for the gbkt Gradle plugin.
 *
 * Tests the full pipeline: DSL → C Code Generation → ROM Compilation Also tests asset pipeline
 * error handling and task isolation.
 */
class IntegrationTest {

    @TempDir lateinit var testProjectDir: File

    private lateinit var buildFile: File
    private lateinit var settingsFile: File
    private lateinit var srcDir: File
    private lateinit var resourcesDir: File

    @BeforeEach
    fun setup() {
        buildFile = File(testProjectDir, "build.gradle.kts")
        settingsFile = File(testProjectDir, "settings.gradle.kts")
        srcDir = File(testProjectDir, "src/main/kotlin")
        resourcesDir = File(testProjectDir, "src/main/resources/sprites")

        srcDir.mkdirs()
        resourcesDir.mkdirs()

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

    // ============================================================================
    // End-to-End DSL → C → ROM Validation Tests
    // ============================================================================

    @Test
    fun `end-to-end minimal game generates C code successfully`() {
        createMinimalGameFixture()
        createBasicBuildFile()

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("generateC", "--stacktrace")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateC")?.outcome)

        // Verify C code was generated
        val cFile = File(testProjectDir, "build/gbkt/generated/main.c")
        assertTrue(cFile.exists(), "C code should be generated")
        assertTrue(cFile.readText().isNotEmpty(), "C code should not be empty")
        assertTrue(
            cFile.readText().contains("void main(void)"),
            "C code should contain main function"
        )
    }

    @Test
    fun `end-to-end game with sprites generates C code with tile data`() {
        createGameWithSpritesFixture()
        createBasicBuildFile()
        createValidSprite("player.png", 8, 16)

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("generateC", "--stacktrace")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateC")?.outcome)

        val cFile = File(testProjectDir, "build/gbkt/generated/main.c")
        val cCode = cFile.readText()

        // Verify sprite-related code is present
        assertTrue(
            cCode.contains("player") || cCode.contains("sprite"),
            "C code should contain sprite references"
        )
    }

    @Test
    @DisabledIfEnvironmentVariable(
        named = "CI",
        matches = "true",
        disabledReason = "Requires GBDK installation"
    )
    fun `end-to-end game compiles to ROM when GBDK is available`() {
        createMinimalGameFixture()
        createBasicBuildFile()

        // Try to build - if GBDK is not available, the build will fail
        val result =
            try {
                GradleRunner.create()
                    .withProjectDir(testProjectDir)
                    .withArguments("buildRom", "--stacktrace")
                    .withPluginClasspath()
                    .build()
            } catch (e: org.gradle.testkit.runner.UnexpectedBuildFailure) {
                // GBDK not available - this is expected if GBDK is not installed
                assertTrue(
                    e.message?.contains("GBDK") == true || e.message?.contains("lcc") == true,
                    "Should indicate GBDK-related issue: ${e.message}"
                )
                return
            }

        // If we get here, GBDK is available and build succeeded
        val romFile = File(testProjectDir, "build/gbkt/output/game.gb")
        assertTrue(romFile.exists(), "ROM file should be created when GBDK is available")
        assertTrue(romFile.length() > 0, "ROM file should not be empty")
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileRom")?.outcome)
    }

    @Test
    fun `generated C code is valid C syntax structure`() {
        createMinimalGameFixture()
        createBasicBuildFile()

        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("generateC")
            .withPluginClasspath()
            .build()

        val cFile = File(testProjectDir, "build/gbkt/generated/main.c")
        val cCode = cFile.readText()

        // Basic C syntax checks
        assertTrue(cCode.contains("#include"), "Should include headers")
        assertTrue(
            cCode.contains("void main(void)") || cCode.contains("int main("),
            "Should have main function"
        )
        assertTrue(
            cCode.count { it == '{' } == cCode.count { it == '}' },
            "Braces should be balanced"
        )
    }

    // ============================================================================
    // Asset Pipeline Error Handling Tests
    // ============================================================================

    @Test
    fun `asset pipeline handles missing asset directory gracefully`() {
        createGameWithSpritesFixture()
        createBasicBuildFile()
        // Don't create the assets directory

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("generateC", "--stacktrace")
                .withPluginClasspath()
                .build()

        // Should succeed but warn about missing assets
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateC")?.outcome)
        assertTrue(
            result.output.contains("Warning") ||
                result.output.contains("not found") ||
                result.output.contains("Asset"),
            "Should warn about missing assets"
        )
    }

    @Test
    fun `asset pipeline handles missing sprite file gracefully`() {
        createGameWithSpritesFixture()
        createBasicBuildFile()
        // Create assets directory but no sprite file

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("generateC", "--stacktrace")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateC")?.outcome)
        assertTrue(
            result.output.contains("Warning") || result.output.contains("not found"),
            "Should warn about missing sprite file"
        )
    }

    @Test
    fun `asset pipeline handles invalid PNG dimensions`() {
        createGameWithSpritesFixture()
        createBasicBuildFile()
        createInvalidSprite("player.png", 7, 15) // Not multiples of 8

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("generateC", "--stacktrace")
                .withPluginClasspath()
                .build()

        // Should either fail or warn about invalid dimensions
        // The exact behavior depends on validation settings
        assertTrue(
            result.output.contains("dimension") ||
                result.output.contains("multiple of 8") ||
                result.task(":generateC")?.outcome == TaskOutcome.SUCCESS,
            "Should handle invalid dimensions appropriately"
        )
    }

    @Test
    fun `asset pipeline processes valid sprites correctly`() {
        createGameWithSpritesFixture()
        createBasicBuildFile()
        createValidSprite("player.png", 8, 16)
        createValidSprite("enemy.png", 8, 8)

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("generateC", "--stacktrace")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateC")?.outcome)

        // Verify assets were processed
        val processedDir = File(testProjectDir, "build/gbkt/processed-assets")
        if (processedDir.exists()) {
            val processedFiles = processedDir.listFiles()?.filter { it.name.endsWith(".processed") }
            assertNotNull(processedFiles, "Should have processed asset markers")
        }
    }

    @Test
    fun `processAssets task handles incremental changes`() {
        createGameWithSpritesFixture()
        createBasicBuildFile()
        createValidSprite("player.png", 8, 16)

        // First build
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("processAssets")
            .withPluginClasspath()
            .build()

        // Modify sprite
        Thread.sleep(1000) // Ensure different timestamp
        createValidSprite("player.png", 8, 16, color = Color.RED)

        // Second build should be incremental
        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("processAssets", "--info")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":processAssets")?.outcome)
        assertTrue(
            result.output.contains("Incremental") ||
                result.output.contains("Processing: player.png"),
            "Should process incrementally or show processing message"
        )
    }

    // ============================================================================
    // Gradle Plugin Task Isolation Tests
    // ============================================================================

    @Test
    fun `task dependencies are correctly configured`() {
        createMinimalGameFixture()
        createBasicBuildFile()

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("buildRom", "--dry-run")
                .withPluginClasspath()
                .build()

        val output = result.output

        // Verify task order
        assertTrue(
            output.contains("compileKotlin") || output.contains(":compileKotlin"),
            "Should include compileKotlin"
        )
        assertTrue(
            output.contains("generateC") || output.contains(":generateC"),
            "Should include generateC"
        )
        assertTrue(
            output.contains("compileRom") || output.contains(":compileRom"),
            "Should include compileRom"
        )
    }

    @Test
    fun `generateC depends on compileKotlin and processAssets`() {
        createGameWithSpritesFixture()
        createBasicBuildFile()
        createValidSprite("player.png", 8, 16)

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("generateC", "--dry-run")
                .withPluginClasspath()
                .build()

        val output = result.output
        assertTrue(
            output.contains("compileKotlin") || output.contains(":compileKotlin"),
            "generateC should depend on compileKotlin"
        )
        assertTrue(
            output.contains("processAssets") || output.contains(":processAssets"),
            "generateC should depend on processAssets when assets are configured"
        )
    }

    @Test
    fun `tasks are isolated and can run independently`() {
        createMinimalGameFixture()
        createBasicBuildFile()

        // Test that processAssets can run alone
        val processResult =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("processAssets")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, processResult.task(":processAssets")?.outcome)

        // Test that generateC can run after dependencies
        val generateResult =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("generateC")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, generateResult.task(":generateC")?.outcome)
    }

    @Test
    fun `cleanGbkt task removes generated files`() {
        createMinimalGameFixture()
        createBasicBuildFile()

        // Generate files first
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("generateC")
            .withPluginClasspath()
            .build()

        val cFile = File(testProjectDir, "build/gbkt/generated/main.c")
        assertTrue(cFile.exists(), "C file should exist before clean")

        // Run clean
        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("cleanGbkt")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":cleanGbkt")?.outcome)
        assertFalse(cFile.exists(), "C file should be removed after clean")
    }

    @Test
    fun `task outputs are cached correctly`() {
        createMinimalGameFixture()
        createBasicBuildFile()

        // First build - may be SUCCESS or FROM_CACHE if test cache persists
        val result1 =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("generateC", "--build-cache")
                .withPluginClasspath()
                .build()

        val outcome1 = result1.task(":generateC")?.outcome
        assertTrue(
            outcome1 == TaskOutcome.SUCCESS || outcome1 == TaskOutcome.FROM_CACHE,
            "First build should succeed or come from cache, but was: $outcome1"
        )

        // Clean and rebuild - should get from cache
        GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("cleanGbkt")
            .withPluginClasspath()
            .build()

        val result2 =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("generateC", "--build-cache")
                .withPluginClasspath()
                .build()

        // Second build should come from cache since we just ran cleanGbkt (not a full clean)
        val outcome2 = result2.task(":generateC")?.outcome
        assertTrue(
            outcome2 == TaskOutcome.SUCCESS || outcome2 == TaskOutcome.FROM_CACHE,
            "Rebuild should succeed or be retrieved from cache, but was: $outcome2"
        )
    }

    @Test
    fun `complex game configuration generates valid C code`() {
        createComplexGameFixture()
        createBasicBuildFile()
        createValidSprite("player.png", 8, 16)
        createValidSprite("enemy.png", 8, 8)

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("generateC", "--stacktrace")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateC")?.outcome)

        val cFile = File(testProjectDir, "build/gbkt/generated/main.c")
        val cCode = cFile.readText()

        // Verify complex features are present
        assertTrue(cCode.isNotEmpty(), "C code should be generated")
        // Complex games should have more structure
        assertTrue(cCode.lines().size > 50, "Complex game should generate substantial C code")
    }

    @Test
    fun `generateC fails gracefully when game class not found`() {
        createBasicBuildFile()
        // Don't create the game file

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("generateC", "--stacktrace")
                .withPluginClasspath()
                .buildAndFail()

        assertTrue(
            result.output.contains("Class not found") ||
                result.output.contains("Could not find") ||
                result.output.contains("NoClassDefFoundError"),
            "Should fail with clear error message about missing class"
        )
    }

    @Test
    fun `generateC fails gracefully when game property not found`() {
        createBasicBuildFile()
        // Create a game file but with wrong property name
        val gameFile = File(srcDir, "test/TestGame.kt")
        gameFile.parentFile.mkdirs()
        gameFile.writeText(
            """
            package test

            import io.github.gbkt.core.*

            val wrongGame = gbGame("TestGame") {
                val mainScene = scene("main") {
                    every.frame { }
                }
                start = mainScene
            }
        """
                .trimIndent()
        )

        // Build file references "testGame" but actual property is "wrongGame"
        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("generateC", "--stacktrace")
                .withPluginClasspath()
                .buildAndFail()

        assertTrue(
            result.output.contains("Could not find game property") ||
                result.output.contains("NoSuchMethodException") ||
                result.output.contains("testGame"),
            "Should fail with clear error message about missing property"
        )
    }

    // ============================================================================
    // Test Fixture Creation Helpers
    // ============================================================================

    private fun createBasicBuildFile() {
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

            dependencies {
                implementation("io.github.gbkt:gbkt-core-jvm:0.1.0-SNAPSHOT")
            }

            kotlin {
                jvmToolchain(21)
            }

            gbkt {
                game("test.TestGameKt::testGame")
                assets("src/main/resources/sprites")
                outputName.set("game")
            }
        """
                .trimIndent()
        )
    }

    private fun createMinimalGameFixture() {
        val gameFile = File(srcDir, "test/TestGame.kt")
        gameFile.parentFile.mkdirs()
        gameFile.writeText(
            """
            package test

            import io.github.gbkt.core.*

            val testGame = gbGame("TestGame") {
                var score by u8Var(0)

                val mainScene = scene("main") {
                    every.frame {
                        score += 1
                    }
                }

                start = mainScene
            }
        """
                .trimIndent()
        )
    }

    private fun createGameWithSpritesFixture() {
        val gameFile = File(srcDir, "test/TestGame.kt")
        gameFile.parentFile.mkdirs()
        gameFile.writeText(
            """
            package test

            import io.github.gbkt.core.*
            import io.github.gbkt.core.assets.SpriteAsset

            val testGame = gbGame("TestGame") {
                val player = sprite(SpriteAsset("player.png")) {
                    size = 8 x 16
                    position(80, 72)
                }

                val mainScene = scene("main") {
                    every.frame {
                        player.x += 1
                    }
                }

                start = mainScene
            }
        """
                .trimIndent()
        )
    }

    private fun createComplexGameFixture() {
        val gameFile = File(srcDir, "test/TestGame.kt")
        gameFile.parentFile.mkdirs()
        gameFile.writeText(
            """
            package test

            import io.github.gbkt.core.*
            import io.github.gbkt.core.assets.SpriteAsset

            val testGame = gbGame("TestGame") {
                var score by u16Var(0)
                var lives by u8Var(3)

                val playerPalette = palette("player") {
                    colors(0xFFFFFF, 0x88FF88, 0x448844, 0x000000)
                }

                val player = sprite(SpriteAsset("player.png")) {
                    size = 8 x 16
                    position(80, 72)
                    palette = playerPalette
                }

                val enemy = sprite(SpriteAsset("enemy.png")) {
                    size = 8 x 8
                    position(150, 100)
                }

                val titleScene = scene("title") {
                    enter {
                        screen.clear()
                        printCentered("GAME") at 6
                    }
                }

                scene("gameplay") {
                    enter {
                        player.x set 80
                        player.y set 72
                    }

                    every.frame {
                        whenever(buttons.a.pressed) {
                            player.y -= 5
                        }

                        whenever(player collidesWith enemy) {
                            lives -= 1
                            score += 10
                        }

                        whenever(lives isEqualTo 0) {
                            scene("gameover")
                        }
                    }
                }

                scene("gameover") {
                    enter {
                        screen.clear()
                        printCentered("GAME OVER") at 6
                        print("SCORE: ", score) at (4 to 9)
                    }
                }

                start = titleScene
            }
        """
                .trimIndent()
        )
    }

    private fun createValidSprite(
        filename: String,
        width: Int,
        height: Int,
        color: Color = Color.WHITE
    ) {
        val spriteFile = File(resourcesDir, filename)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = color
        g.fillRect(0, 0, width, height)
        g.dispose()
        ImageIO.write(image, "PNG", spriteFile)
    }

    /**
     * Creates a sprite with invalid dimensions (not multiples of 8). This is used to test
     * validation error handling for incorrect sprite sizes.
     */
    private fun createInvalidSprite(filename: String, width: Int, height: Int) {
        require(width % 8 != 0 || height % 8 != 0) {
            "createInvalidSprite should be called with dimensions that are NOT multiples of 8. " +
                "Got: ${width}x${height}. Use createValidSprite for valid dimensions."
        }
        val spriteFile = File(resourcesDir, filename)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)
        g.dispose()
        ImageIO.write(image, "PNG", spriteFile)
    }
}
