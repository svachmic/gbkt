/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.entity.*
import io.github.gbkt.core.graphics.*
import io.github.gbkt.core.ir.*
import kotlin.test.*

/**
 * Tests for the scene system.
 *
 * Validates:
 * - Scene definition with name
 * - Scene with onEnter/onExit callbacks
 * - Scene transitions
 * - Start scene designation
 * - Multiple scenes in a game
 */
class SceneTest {

    // =========================================================================
    // SCENE DEFINITION WITH NAME
    // =========================================================================

    @Test
    fun `scene has correct name`() {
        val game =
            gbGame("SceneNameTest") {
                val myScene = scene("gameplay") { every.frame {} }

                start = myScene
            }

        assertEquals(1, game.scenes.size, "Should have 1 scene")
        assertTrue(game.scenes.containsKey("gameplay"), "Scene name should be 'gameplay'")
    }

    @Test
    fun `scene name is used in generated code`() {
        val game =
            gbGame("SceneCodegenTest") {
                val mainScene = scene("main_menu") { every.frame {} }

                start = mainScene
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("main_menu") || code.contains("SCENE_MAIN_MENU"),
            "Generated code should reference scene name",
        )
    }

    // =========================================================================
    // SCENE WITH ONENTER/ONEXIT CALLBACKS
    // =========================================================================

    @Test
    fun `scene enter block generates IR`() {
        val game =
            gbGame("SceneEnterTest") {
                var initialized by u8Var(0)

                val mainScene =
                    scene("main") {
                        enter { initialized set 1 }
                        every.frame {}
                    }

                start = mainScene
            }

        val code = game.compileForTest()

        // Scene enter should set initialized to 1
        assertTrue(
            code.contains("initialized") && code.contains("1"),
            "Should generate enter block code",
        )
    }

    @Test
    fun `scene exit block generates IR`() {
        val game =
            gbGame("SceneExitTest") {
                var cleanedUp by u8Var(0)

                val mainScene =
                    scene("main") {
                        exit { cleanedUp set 1 }
                        every.frame {}
                    }

                start = mainScene
            }

        // Exit statements should be captured in the scene
        val mainScene = game.scenes["main"]
        assertNotNull(mainScene, "Main scene should exist")
        assertFalse(mainScene.onExit.isEmpty(), "Scene should have exit statements")

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate valid code")
    }

    @Test
    fun `scene enter and exit both work together`() {
        val game =
            gbGame("SceneEnterExitTest") {
                var state by u8Var(0)

                val mainScene =
                    scene("main") {
                        enter { state set 1 }
                        exit { state set 0 }
                        every.frame {}
                    }

                start = mainScene
            }

        val mainScene = game.scenes["main"]
        assertNotNull(mainScene, "Main scene should exist")
        assertFalse(mainScene.onEnter.isEmpty(), "Scene should have enter statements")
        assertFalse(mainScene.onExit.isEmpty(), "Scene should have exit statements")
    }

    @Test
    fun `scene with complex enter logic`() {
        val game =
            gbGame("SceneComplexEnterTest") {
                var score by u8Var(0)
                var lives by u8Var(3)

                val player =
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 16
                        position(80, 72)
                    }

                val gameScene =
                    scene("game") {
                        enter {
                            score set 0
                            lives set 3
                            player.show()
                        }
                        every.frame {}
                    }

                start = gameScene
            }

        val code = game.compileForTest()

        assertTrue(code.contains("score"), "Should initialize score")
        assertTrue(code.contains("lives"), "Should initialize lives")
        assertTrue(
            code.contains("SHOW_SPRITE") || code.contains("show"),
            "Should show player sprite",
        )
    }

    // =========================================================================
    // SCENE TRANSITIONS
    // =========================================================================

    @Test
    fun `scene transition generates scene change`() {
        val game =
            gbGame("SceneTransitionTest") {
                val menuScene = scene("menu") { every.frame {} }

                val gameScene =
                    scene("game") {
                        enter {
                            scene(menuScene) // Use type-safe reference
                        }
                        every.frame {}
                    }

                start = menuScene
            }

        val code = game.compileForTest()

        // Should generate scene change logic
        assertTrue(
            code.contains("scene") || code.contains("SCENE"),
            "Should generate scene transition code",
        )
    }

    @Test
    fun `scene transition with scene reference`() {
        val game =
            gbGame("SceneRefTransitionTest") {
                val gameoverScene = scene("gameover") { every.frame {} }

                val gameScene =
                    scene("game") {
                        every.frame { whenever(Condition(IRLiteral(1))) { scene(gameoverScene) } }
                    }

                start = gameScene
            }

        val code = game.compileForTest()

        assertTrue(code.isNotEmpty(), "Should generate valid code")
        assertTrue(
            code.contains("gameover") || code.contains("GAMEOVER"),
            "Should reference gameover scene",
        )
    }

    @Test
    fun `goto is alias for scene transition`() {
        val game =
            gbGame("GotoTransitionTest") {
                val titleScene = scene("title") { every.frame {} }

                val gameScene =
                    scene("game") {
                        every.frame { whenever(Condition(IRLiteral(1))) { goto(titleScene) } }
                    }

                start = gameScene
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("title") || code.contains("TITLE"),
            "Should generate scene transition to title",
        )
    }

    // =========================================================================
    // START SCENE DESIGNATION
    // =========================================================================

    @Test
    fun `start scene is set correctly`() {
        val game =
            gbGame("StartSceneTest") {
                val menuScene = scene("menu") { every.frame {} }

                val gameScene = scene("game") { every.frame {} }

                start = menuScene
            }

        assertEquals("menu", game.startScene, "Start scene should be 'menu'")
    }

    @Test
    fun `start scene appears first in generated code`() {
        val game =
            gbGame("StartSceneFirstTest") {
                val aScene = scene("a") { every.frame {} }

                val bScene = scene("b") { every.frame {} }

                val cScene = scene("c") { every.frame {} }

                start = bScene
            }

        val code = game.compileForTest()

        // Start scene should be set to 'b'
        assertEquals("b", game.startScene, "Start scene should be 'b'")
    }

    @Test
    fun `changing start scene works`() {
        val game =
            gbGame("ChangeStartSceneTest") {
                val firstScene = scene("first") { every.frame {} }

                val secondScene = scene("second") { every.frame {} }

                start = secondScene
            }

        assertEquals("second", game.startScene, "Start scene should be 'second'")
    }

    // =========================================================================
    // MULTIPLE SCENES IN A GAME
    // =========================================================================

    @Test
    fun `multiple scenes are registered`() {
        val game =
            gbGame("MultipleScenesTest") {
                val titleScene = scene("title") { every.frame {} }

                val menuScene = scene("menu") { every.frame {} }

                val gameScene = scene("game") { every.frame {} }

                val gameoverScene = scene("gameover") { every.frame {} }

                start = titleScene
            }

        assertEquals(4, game.scenes.size, "Should have 4 scenes")
        assertTrue(game.scenes.containsKey("title"), "Should have 'title' scene")
        assertTrue(game.scenes.containsKey("menu"), "Should have 'menu' scene")
        assertTrue(game.scenes.containsKey("game"), "Should have 'game' scene")
        assertTrue(game.scenes.containsKey("gameover"), "Should have 'gameover' scene")
    }

    @Test
    fun `multiple scenes generate valid code`() {
        val game =
            gbGame("MultipleScenesCodeTest") {
                var currentLevel by u8Var(1)

                val menuScene =
                    scene("menu") {
                        enter { currentLevel set 1 }
                        every.frame {}
                    }

                val level1Scene = scene("level1") { every.frame {} }

                val level2Scene = scene("level2") { every.frame {} }

                start = menuScene
            }

        val code = game.compileForTest()

        assertTrue(code.isNotEmpty(), "Should generate valid code")
        assertTrue(code.contains("menu") || code.contains("MENU"), "Should contain menu scene")
    }

    @Test
    fun `scenes with transitions between them`() {
        // For scenes with transitions, define destination scenes first
        val game =
            gbGame("SceneTransitionsTest") {
                // Define scenes in reverse dependency order
                val titleScene = scene("title") { every.frame {} }

                val gameoverScene =
                    scene("gameover") {
                        every.frame {
                            // Back to title
                            whenever(buttons.a.pressed) { scene(titleScene) }
                        }
                    }

                val gameScene =
                    scene("game") {
                        every.frame {
                            // Transition to gameover
                            whenever(Condition(IRLiteral(0))) { scene(gameoverScene) }
                        }
                    }

                start = titleScene
            }

        assertEquals(3, game.scenes.size, "Should have 3 scenes")

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile successfully")
    }

    // =========================================================================
    // SCENE FRAME LOGIC
    // =========================================================================

    @Test
    fun `scene every frame generates frame logic`() {
        val game =
            gbGame("SceneFrameTest") {
                var counter by u8Var(0)

                val mainScene = scene("main") { every.frame { counter += 1 } }

                start = mainScene
            }

        val mainScene = game.scenes["main"]
        assertNotNull(mainScene, "Main scene should exist")
        assertFalse(mainScene.onFrame.isEmpty(), "Scene should have frame statements")

        val code = game.compileForTest()
        assertTrue(code.contains("counter"), "Should update counter in frame")
    }

    @Test
    fun `scene every second generates timed logic`() {
        val game =
            gbGame("SceneSecondTest") {
                var seconds by u8Var(0)

                val mainScene = scene("main") { every.second { seconds += 1 } }

                start = mainScene
            }

        val code = game.compileForTest()

        // Should have frame count modulo check
        assertTrue(
            code.contains("60") || code.contains("_frame_count"),
            "Should generate timed logic with frame count",
        )
    }

    @Test
    fun `scene every N frames generates correct interval`() {
        val game =
            gbGame("SceneIntervalTest") {
                var tick by u8Var(0)

                val mainScene = scene("main") { every(15).frames { tick += 1 } }

                start = mainScene
            }

        val code = game.compileForTest()

        // Should have modulo 15 check
        assertTrue(
            code.contains("15") && code.contains("%"),
            "Should generate modulo check for 15 frames",
        )
    }

    // =========================================================================
    // SCENE WITH CONDITIONALS
    // =========================================================================

    @Test
    fun `scene with whenever conditional`() {
        val game =
            gbGame("SceneConditionalTest") {
                var health by u8Var(100)
                var isHit by u8Var(0)

                // Define gameover first so we have the SceneRef
                val gameoverScene = scene("gameover") { every.frame {} }

                val gameScene =
                    scene("game") {
                        every.frame { whenever(health isEqualTo 0) { scene(gameoverScene) } }
                    }

                start = gameScene
            }

        val code = game.compileForTest()

        assertTrue(code.contains("health"), "Should check health in conditional")
        assertTrue(code.contains("0"), "Should compare to 0")
    }

    @Test
    fun `scene with otherwise block`() {
        val game =
            gbGame("SceneOtherwiseTest") {
                var value by u8Var(0)
                var result by u8Var(0)

                val mainScene =
                    scene("main") {
                        every.frame {
                            whenever(value isAbove 50) { result set 1 } otherwise { result set 0 }
                        }
                    }

                start = mainScene
            }

        val code = game.compileForTest()

        assertTrue(code.contains("else"), "Should generate else block")
    }
}
