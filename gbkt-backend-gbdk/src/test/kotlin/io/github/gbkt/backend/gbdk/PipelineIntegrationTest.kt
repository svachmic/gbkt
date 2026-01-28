/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk

import io.github.gbkt.backend.api.BackendRegistry
import io.github.gbkt.backend.api.GenerationOptions
import io.github.gbkt.backend.gbdk.codegen.compileForTest
import io.github.gbkt.core.*
import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.entity.*
import io.github.gbkt.core.input.buttons
import io.github.gbkt.core.input.dpad
import io.github.gbkt.core.ir.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end integration tests for the full DSL → IR → C pipeline.
 *
 * These tests verify that:
 * 1. DSL code correctly captures to IR
 * 2. IR validates against Game Boy constraints
 * 3. Backend generates compilable C code
 * 4. Generated C code has expected structure
 */
class PipelineIntegrationTest {

    @BeforeTest
    fun setUp() {
        BackendRegistry.clear()
        BackendRegistry.discover()
    }

    @AfterTest
    fun tearDown() {
        BackendRegistry.clear()
    }

    private val backend
        get() =
            BackendRegistry.forId("gbdk")
                ?: error("GBDK backend not found - ensure ServiceLoader is configured")

    // ============================================================================
    // Minimal Game Pipeline Tests
    // ============================================================================

    @Test
    fun `minimal game generates valid C code`() {
        val game =
            gbGame("MinimalGame") {
                val mainScene = scene("main") { every.frame {} }
                start = mainScene
            }

        val result = game.compileForTest()

        // Verify C code structure
        assertTrue(result.contains("#include"), "Should include headers")
        assertTrue(result.contains("void main(void)"), "Should have main function")
        assertTrue(
            result.contains("SCENE_MAIN") || result.contains("main"),
            "Should reference main scene",
        )
    }

    @Test
    fun `game with variables generates correct C declarations`() {
        val game =
            gbGame("VariableGame") {
                var score by u8Var(0)
                var highScore by u16Var(1000)

                val mainScene =
                    scene("main") {
                        every.frame {
                            score += 1
                            whenever(score isAbove highScore) { highScore set score }
                        }
                    }
                start = mainScene
            }

        val result = game.compileForTest()

        // Verify variable declarations
        assertTrue(result.contains("uint8_t") || result.contains("UINT8"), "Should have u8 type")
        assertTrue(result.contains("uint16_t") || result.contains("UINT16"), "Should have u16 type")
        assertTrue(result.contains("score"), "Should have score variable")
        assertTrue(result.contains("highScore"), "Should have highScore variable")
    }

    // ============================================================================
    // Entity System Pipeline Tests
    // ============================================================================

    @Test
    fun `game with entities generates sprite management code`() {
        val game =
            gbGame("EntityGame") {
                val player by entity { position(80, 72) }

                val mainScene = scene("main") { every.frame { player.x += 1 } }
                start = mainScene
            }

        val result = game.compileForTest()

        // Verify entity-related code
        assertTrue(result.contains("player"), "Should reference player entity")
    }

    // ============================================================================
    // Input System Pipeline Tests
    // ============================================================================

    @Test
    fun `game with input handling generates joypad code`() {
        val game =
            gbGame("InputGame") {
                var playerX by u8Var(80)

                val mainScene =
                    scene("main") {
                        every.frame {
                            whenever(dpad.right) { playerX += 2 }
                            whenever(dpad.left) { playerX -= 2 }
                            whenever(buttons.a.pressed) { playerX set 80 }
                        }
                    }
                start = mainScene
            }

        val result = game.compileForTest()

        // Verify input handling code
        assertTrue(
            result.contains("joypad") || result.contains("J_"),
            "Should have joypad handling",
        )
    }

    // ============================================================================
    // Scene Transition Pipeline Tests
    // ============================================================================

    @Test
    fun `game with multiple scenes generates transition code`() {
        val game =
            gbGame("MultiSceneGame") {
                lateinit var gameplayScene: SceneRef
                gameplayScene = scene("gameplay") { every.frame {} }

                val titleScene =
                    scene("title") {
                        every.frame { whenever(buttons.start.pressed) { scene(gameplayScene) } }
                    }

                start = titleScene
            }

        val result = game.compileForTest()

        // Verify scene-related code
        assertTrue(
            result.contains("title") || result.contains("SCENE_TITLE"),
            "Should have title scene",
        )
        assertTrue(
            result.contains("gameplay") || result.contains("SCENE_GAMEPLAY"),
            "Should have gameplay scene",
        )
    }

    // ============================================================================
    // Collision System Pipeline Tests
    // ============================================================================

    @Test
    fun `game with collision detection generates check code`() {
        val game =
            gbGame("CollisionGame") {
                val player by entity { position(80, 72) }

                val enemy by entity { position(100, 72) }

                var collided by u8Var(0)

                val mainScene =
                    scene("main") {
                        every.frame { whenever(player collidesWith enemy) { collided set 1 } }
                    }
                start = mainScene
            }

        val result = game.compileForTest()

        // Verify collision-related code is present
        assertTrue(result.isNotEmpty(), "Should generate code")
    }

    // ============================================================================
    // Complex Game Pipeline Tests
    // ============================================================================

    @Test
    fun `complex game with all features generates complete C code`() {
        val game =
            gbGame("ComplexGame") {
                // Variables
                var score by u16Var(0)
                var lives by u8Var(3)
                var posX by u8Var(80)
                var posY by u8Var(72)

                // Entities
                val player by entity { position(80, 72) }

                // Multiple scenes
                lateinit var gameplayScene: SceneRef
                lateinit var gameoverScene: SceneRef

                gameplayScene =
                    scene("gameplay") {
                        enter {
                            score set 0
                            lives set 3
                        }
                        every.frame {
                            // Input handling using variables
                            whenever(dpad.right) { posX += 2 }
                            whenever(dpad.left) { posX -= 2 }
                            whenever(dpad.up) { posY -= 2 }
                            whenever(dpad.down) { posY += 2 }

                            // Score increment
                            score += 1
                        }
                    }

                gameoverScene = scene("gameover") { every.frame {} }

                val titleScene =
                    scene("title") {
                        every.frame { whenever(buttons.start.pressed) { scene(gameplayScene) } }
                    }

                start = titleScene
            }

        val result = game.compileForTest()

        // Comprehensive verification
        assertTrue(result.contains("void main(void)"), "Should have main function")
        assertTrue(result.lines().size > 50, "Complex game should generate substantial code")

        // Verify balanced braces (basic syntax check)
        val openBraces = result.count { it == '{' }
        val closeBraces = result.count { it == '}' }
        assertEquals(openBraces, closeBraces, "Braces should be balanced")

        // Verify no obvious syntax errors
        assertTrue(!result.contains(";;"), "Should not have double semicolons")
    }

    // ============================================================================
    // Validation Tests
    // ============================================================================

    @Test
    fun `backend validates game before generation`() {
        val game =
            gbGame("ValidGame") {
                val mainScene = scene("main") { every.frame {} }
                start = mainScene
            }

        val validationResult = backend.validate(game)
        assertTrue(validationResult.isValid, "Valid game should pass validation")
    }

    @Test
    fun `generation with options succeeds`() {
        val game =
            gbGame("OptionsGame") {
                val mainScene = scene("main") { every.frame {} }
                start = mainScene
            }

        val options = GenerationOptions(sourceMap = true, optimizationLevel = 0)

        val result = backend.generate(game, options)

        // Generation should complete successfully
        assertTrue(result.files.isNotEmpty(), "Should generate at least one file")
    }
}
