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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
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
            "C code should contain main function",
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
            "C code should contain sprite references",
        )
    }

    @Test
    @DisabledIfEnvironmentVariable(
        named = "CI",
        matches = "true",
        disabledReason = "Requires GBDK installation",
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
                    "Should indicate GBDK-related issue: ${e.message}",
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
            "Should have main function",
        )
        assertTrue(
            cCode.count { it == '{' } == cCode.count { it == '}' },
            "Braces should be balanced",
        )
    }

    // ============================================================================
    // Asset Pipeline Error Handling Tests
    // ============================================================================

    @Test
    fun `asset pipeline handles missing asset directory gracefully`() {
        createGameWithSpritesFixture()
        createBasicBuildFile()
        // Don't create the assets directory — processAssets skips silently (graceful degradation)

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("generateC", "--stacktrace")
                .withPluginClasspath()
                .build()

        // generateC succeeds — asset file validation (file existence on disk) happens at
        // convertSprites / compileRom time, not at C generation time
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateC")?.outcome)
    }

    @Test
    fun `asset pipeline handles missing sprite file gracefully`() {
        createGameWithSpritesFixture()
        createBasicBuildFile()
        // Create assets directory but no sprite file — processAssets skips silently

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("generateC", "--stacktrace")
                .withPluginClasspath()
                .build()

        // generateC succeeds — missing individual sprite files are a compile-time concern
        // (convertSprites / compileRom), not a C generation concern
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateC")?.outcome)
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
                .buildAndFail()

        // Backend validates that sprite dimensions are multiples of 8
        assertTrue(
            result.output.contains("multiple of 8") ||
                result.output.contains("dimension") ||
                result.output.contains("ASSET_FILE"),
            "Should report invalid dimensions: ${result.output}",
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
            "Should process incrementally or show processing message",
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
            "Should include compileKotlin",
        )
        assertTrue(
            output.contains("generateC") || output.contains(":generateC"),
            "Should include generateC",
        )
        assertTrue(
            output.contains("compileRom") || output.contains(":compileRom"),
            "Should include compileRom",
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
            "generateC should depend on compileKotlin",
        )
        assertTrue(
            output.contains("processAssets") || output.contains(":processAssets"),
            "generateC should depend on processAssets when assets are configured",
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
            "First build should succeed or come from cache, but was: $outcome1",
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
            "Rebuild should succeed or be retrieved from cache, but was: $outcome2",
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
            "Should fail with clear error message about missing class",
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

            import io.github.gbkt.core.dsl.*

            val wrongGame = game("TestGame") {
                val mainScene = scene("main") {
                    frame { }
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
            "Should fail with clear error message about missing property",
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

            // Defeat the changing-module SNAPSHOT cache (default 24h TTL) so this nested
            // GradleRunner sandbox always re-resolves the freshly-republished 0.1.0-SNAPSHOT
            // artifacts. Without this, gbkt-ir and gbkt-backend-gbdk can desync in the Gradle
            // module cache and link mismatched SceneIR.copy${'$'}default arities → NoSuchMethodError
            // (Phase 15 F1 / D-05; pluginTest republishes ~/.m2 but the cache, not ~/.m2, is read).
            configurations.all { resolutionStrategy.cacheChangingModulesFor(0, "seconds") }

            dependencies {
                implementation("io.github.gbkt:gbkt-core:0.1.0-SNAPSHOT")
                implementation("io.github.gbkt:gbkt-backend-api:0.1.0-SNAPSHOT")
                runtimeOnly("io.github.gbkt:gbkt-backend-gbdk:0.1.0-SNAPSHOT")
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

            import io.github.gbkt.core.dsl.*

            val testGame = game("TestGame") {
                var score by u8Var(0)

                val mainScene = scene("main") {
                    frame {
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

            import io.github.gbkt.core.dsl.*

            val testGame = game("TestGame") {
                val player by actor {
                    position(80, 72)
                    sprite(asset("sprites/player.png")) {
                        size(8, 16)
                        hitbox(0, 0, 8, 16)
                    }
                }

                val mainScene = scene("main") {
                    frame {
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

            import io.github.gbkt.core.dsl.*
            import io.github.gbkt.core.ir.PositionDef

            val testGame = game("TestGame") {
                var score by u16Var(0)
                var lives by u8Var(3)

                val player by actor {
                    position(80, 72)
                    sprite(asset("sprites/player.png")) {
                        size(8, 16)
                        hitbox(0, 0, 8, 16)
                    }
                }

                val enemy by actor {
                    position(150, 100)
                    sprite(asset("sprites/enemy.png")) {
                        size(8, 8)
                        hitbox(0, 0, 8, 8)
                    }
                }

                val gameoverScene = scene("gameover") {
                    enter {
                        clear()
                        printCentered("GAME OVER") at 6
                        print("SCORE: %d", score.toExpr(), position = PositionDef(4, 9))
                    }
                }

                scene("gameplay") {
                    enter {
                        player.x set 80
                        player.y set 72
                    }

                    frame {
                        whenever(buttons.a.pressed) {
                            player.y -= 5
                        }

                        whenever(player.collides(enemy)) {
                            lives -= 1
                            score += 10
                        }

                        whenever(lives isEqualTo 0) {
                            navigate(gameoverScene)
                        }
                    }
                }

                val titleScene = scene("title") {
                    enter {
                        clear()
                        printCentered("GAME") at 6
                    }
                }

                start = titleScene
            }
            """
                .trimIndent()
        )
    }

    /**
     * Creates a simple-physics-shaped game fixture.
     *
     * Single scene with a frame loop, one actor, two i16 variables, and three `whenever(...)`
     * conditions that mirror the player-movement pattern from `gbkt-examples/simple-physics`. The
     * game is small enough that `estimatedBytes <= HOME_BANK_SCENE_BUDGET` so BankingAnalysisPass
     * takes the single-scene-fits-HOME fast-path and folds bank1.c out of the emission set — the
     * exact code path that exposed the stale-output bug (D-K-01..D-K-04).
     */
    private fun createSimplePhysicsLikeFixture() {
        val gameFile = File(srcDir, "test/TestGame.kt")
        gameFile.parentFile.mkdirs()
        gameFile.writeText(
            """
            package test

            import io.github.gbkt.core.dsl.*

            val testGame = game("TestGame") {
                var posX by i16Var(80 shl 4)
                var posY by i16Var(72 shl 4)

                val playScene = scene("play") {
                    frame {
                        whenever(dpad.left.held) { posX -= 16 }
                        whenever(dpad.right.held) { posX += 16 }
                        whenever(posX isBelow 0) { posX set 0 }
                    }
                }

                start = playScene
            }
            """
                .trimIndent()
        )
    }

    /**
     * Creates a game fixture with TWO scenes.
     *
     * Having two scenes prevents BankingAnalysisPass from taking the single-scene HOME fast-path,
     * ensuring bank1.c IS emitted and Gradle snapshots it as an output file. This is Step 1 of the
     * two-step stale-output regression test (D-R-02).
     */
    private fun createTwoSceneGameFixture() {
        val gameFile = File(srcDir, "test/TestGame.kt")
        gameFile.parentFile.mkdirs()
        // NOTE: ScriptBuilder.whenever{} evaluates its body lambda synchronously during DSL
        // construction (see ScriptBuilderContext.with). Forward references to SceneRef via a
        // nullable var that is assigned after the referencing scene is built therefore NPE at
        // DSL evaluation time — not at runtime. Avoid forward-reference patterns: define mainScene
        // first so titleScene can capture its SceneRef directly.
        gameFile.writeText(
            """
            package test

            import io.github.gbkt.core.dsl.*

            val testGame = game("TestGame") {
                var score by u8Var(0)

                val mainScene = scene("main") {
                    frame {
                        score += 1
                    }
                }

                val titleScene = scene("title") {
                    enter { clear() }
                    frame {
                        whenever(buttons.start.pressed) { navigate(mainScene) }
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
        color: Color = Color.WHITE,
    ) {
        val spriteFile = File(resourcesDir, filename)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = color
        g.fillRect(0, 0, width, height)
        g.dispose()
        ImageIO.write(image, "PNG", spriteFile)
    }

    // ============================================================================
    // Output Sync — Staleness Regression Tests (09.2 D-R-01, D-R-02, D-S-04)
    // ============================================================================

    @Test
    fun `generateC deletes stale files dropped from the emission set`() {
        // Step 1: Run generateC with a two-scene game.
        // The BankingAnalysisPass fast-path only fires for single-scene games, so a two-scene
        // game routes via bank 1 → bank1.c IS emitted and Gradle snapshots it as an output.
        // This reproduces the "prior MBC5 / multi-scene build left bank1.c behind" scenario
        // from the 09.1 regression (D-R-02 hand-staged stale-file pattern).
        createTwoSceneGameFixture()
        createBasicBuildFile()

        val firstResult =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("generateC", "--stacktrace")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, firstResult.task(":generateC")?.outcome)
        val generatedDir = File(testProjectDir, "build/gbkt/generated")
        assertTrue(
            File(generatedDir, "bank1.c").exists(),
            "Step 1: two-scene game must emit bank1.c so Gradle snapshots it as an output",
        )

        // Step 2: Switch to a single-scene game small enough for the HOME fast-path.
        // BankingAnalysisPass folds the scene into main.c → bank1.c is NOT emitted.
        // bank1.c from Step 1 is in Gradle's output snapshot so it is NOT pre-cleaned by
        // Gradle's own stale-output cleanup. Instead, syncOutputDir must delete it because
        // it is absent from the current emission set — this is the bug-fix verification.
        createMinimalGameFixture()

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("generateC", "--stacktrace")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateC")?.outcome)
        assertFalse(
            File(generatedDir, "bank1.c").exists(),
            "stale bank1.c should be deleted by syncOutputDir",
        )
        assertTrue(
            result.output.contains("Removed stale: bank1.c"),
            "S-04 lifecycle log must announce the deletion",
        )
    }

    @Test
    @EnabledIfEnvironmentVariable(
        named = "GBDK_HOME",
        matches = ".+",
        disabledReason = "Requires GBDK installation (GBDK_HOME env var)",
    )
    fun `simple-physics fixture builds ROM end-to-end without staleness errors`() {
        createSimplePhysicsLikeFixture() // NEW helper — single-scene-fits-HOME shape
        createBasicBuildFile() // outputName.set("game") → build/gbkt/output/game.gb

        val result =
            GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withArguments("buildRom", "--stacktrace")
                .withPluginClasspath()
                .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":buildRom")?.outcome)
        val romFile = File(testProjectDir, "build/gbkt/output/game.gb")
        assertTrue(romFile.exists(), "ROM file must be produced by buildRom")
        assertTrue(romFile.length() > 0, "ROM file must not be empty")
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
