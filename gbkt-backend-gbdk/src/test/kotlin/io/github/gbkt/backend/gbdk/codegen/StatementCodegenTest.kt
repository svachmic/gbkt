/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen

import io.github.gbkt.core.*
import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.entity.*
import io.github.gbkt.core.graphics.*
import io.github.gbkt.core.input.buttons
import io.github.gbkt.core.input.dpad
import io.github.gbkt.core.ir.*
import io.github.gbkt.core.scene.transition
import kotlin.test.*

/**
 * Tests for StatementCodegen - verifies statement-level code generation.
 *
 * Tests cover:
 * - Core control flow: if/else, when, while, for
 * - Assignment statements
 * - Function calls
 * - Scene changes
 * - Raw code injection
 * - Array operations
 * - Sound/Music statements
 * - Dialog statements
 * - Menu statements
 * - Camera statements
 * - Transition statements
 */
class StatementCodegenTest {

    // =========================================================================
    // IF/ELSE STATEMENTS
    // =========================================================================

    @Test
    fun `if statement generates correct C code`() {
        val game =
            gbGame("test") {
                var flag by u8Var(0)
                var result by u8Var(0)

                start = scene("main") { every.frame { whenever(flag.isNonZero) { result set 1 } } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("if (flag"), "Should generate if statement with condition")
        assertTrue(code.contains("result = 1"), "Should generate assignment in if body")
    }

    @Test
    fun `if-else statement generates correct C code`() {
        val game =
            gbGame("test") {
                var flag by u8Var(0)
                var result by u8Var(0)

                start =
                    scene("main") {
                        every.frame {
                            whenever(flag.isNonZero) { result set 1 } otherwise { result set 0 }
                        }
                    }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("if (flag"), "Should generate if")
        assertTrue(code.contains("else {"), "Should generate else block")
    }

    @Test
    fun `nested if statements generate correctly`() {
        val game =
            gbGame("test") {
                var a by u8Var(0)
                var b by u8Var(0)
                var result by u8Var(0)

                start =
                    scene("main") {
                        every.frame {
                            whenever(a.isNonZero) { whenever(b.isNonZero) { result set 1 } }
                        }
                    }
            }

        val code = GBDKCodeGenerator(game).generate()

        // Count if statements
        val ifCount = code.windowed(3).count { it == "if " }
        assertTrue(ifCount >= 2, "Should have at least 2 if statements for nesting")
    }

    // =========================================================================
    // WHEN/BRANCH STATEMENTS
    // =========================================================================

    @Test
    fun `branch generates if-else chain`() {
        val game =
            gbGame("test") {
                var state by u8Var(0)
                var result by u8Var(0)

                start =
                    scene("main") {
                        every.frame {
                            branch {
                                (state isEqualTo 0) then { result set 10 }
                                (state isEqualTo 1) then { result set 20 }
                                (state isEqualTo 2) then { result set 30 }
                            }
                        }
                    }
            }

        val code = GBDKCodeGenerator(game).generate()

        // Branch generates chained if/else statements with all branch values
        assertTrue(
            code.contains("state") && code.contains("0u"),
            "Should have state == 0 condition",
        )
        assertTrue(
            code.contains("result = 10u") || code.contains("result = 10"),
            "Should assign 10 for state 0",
        )
        assertTrue(
            code.contains("result = 20u") || code.contains("result = 20"),
            "Should assign 20 for state 1",
        )
        assertTrue(
            code.contains("result = 30u") || code.contains("result = 30"),
            "Should assign 30 for state 2",
        )
    }

    @Test
    fun `whenever with otherwise generates else clause`() {
        val game =
            gbGame("test") {
                var state by u8Var(0)
                var result by u8Var(0)

                start =
                    scene("main") {
                        every.frame {
                            whenever(state isEqualTo 0) { result set 10 } otherwise
                                {
                                    result set 99
                                }
                        }
                    }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("else {"), "Should have else clause")
        assertTrue(code.contains("99"), "Should have default value")
    }

    // =========================================================================
    // WHILE LOOPS
    // =========================================================================

    @Test
    fun `while loop generates correct C code`() {
        val game =
            gbGame("test") {
                var counter by u8Var(10)

                start = scene("main") { enter { repeatWhile(counter isAbove 0) { counter -= 1 } } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("while"), "Should generate while loop")
        assertTrue(code.contains("counter"), "Should reference counter")
    }

    @Test
    fun `while with complex condition generates correctly`() {
        val game =
            gbGame("test") {
                var a by u8Var(0)
                var b by u8Var(10)

                start =
                    scene("main") {
                        enter {
                            repeatWhile((a isBelow 5) and (b isAbove 0)) {
                                a += 1
                                b -= 1
                            }
                        }
                    }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("while"), "Should generate while")
        assertTrue(code.contains("&&"), "Should have AND operator for compound condition")
    }

    // =========================================================================
    // FOR LOOPS
    // =========================================================================

    @Test
    fun `for loop generates correct C code`() {
        val game =
            gbGame("test") {
                var sum by u8Var(0)

                start = scene("main") { enter { repeat(5) { sum += 1 } } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("for"), "Should generate for loop")
        assertTrue(code.contains("UINT8"), "Should declare loop counter")
        assertTrue(code.contains("++"), "Should have increment")
    }

    @Test
    fun `for loop with range generates bounds`() {
        val game =
            gbGame("test") {
                var sum by u8Var(0)

                start = scene("main") { enter { repeat(10) { sum += 1 } } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("<= 9") || code.contains("< 10"), "Should have correct loop bound")
    }

    // =========================================================================
    // FUNCTION CALLS
    // =========================================================================

    @Test
    fun `function call generates correct C syntax`() {
        val game = gbGame("test") { start = scene("main") { enter { raw("custom_init();") } } }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("custom_init();"), "Should include function call")
    }

    // =========================================================================
    // SCENE CHANGES
    // =========================================================================

    @Test
    fun `scene change generates next scene assignment`() {
        val game =
            gbGame("test") {
                lateinit var menuScene: SceneRef
                menuScene = scene("menu") {}

                start = scene("main") { enter { scene(menuScene) } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("_next_scene = SCENE_MENU"), "Should set next scene")
        assertTrue(code.contains("_scene_changed = 1"), "Should mark scene changed")
    }

    @Test
    fun `multiple scenes generate unique constants`() {
        val game =
            gbGame("test") {
                val scene1 = scene("game") {}
                val scene2 = scene("pause") {}
                val scene3 = scene("gameover") {}

                start = scene("main") {}
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("SCENE_GAME"), "Should have SCENE_GAME constant")
        assertTrue(code.contains("SCENE_PAUSE"), "Should have SCENE_PAUSE constant")
        assertTrue(code.contains("SCENE_GAMEOVER"), "Should have SCENE_GAMEOVER constant")
        assertTrue(code.contains("SCENE_MAIN"), "Should have SCENE_MAIN constant")
    }

    // =========================================================================
    // RAW CODE
    // =========================================================================

    @Test
    fun `raw code is inserted verbatim`() {
        val game =
            gbGame("test") {
                start =
                    scene("main") {
                        enter {
                            raw("NR52_REG = 0x80;")
                            raw("NR50_REG = 0x77;")
                        }
                    }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("NR52_REG = 0x80;"), "Should include first raw line")
        assertTrue(code.contains("NR50_REG = 0x77;"), "Should include second raw line")
    }

    @Test
    fun `multiline raw code generates correctly`() {
        val game =
            gbGame("test") {
                start =
                    scene("main") {
                        enter {
                            raw(
                                """
                                // Custom code block
                                UINT8 temp = 0;
                                temp = 42;
                                """
                                    .trimIndent()
                            )
                        }
                    }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("// Custom code block"), "Should include comment")
        assertTrue(code.contains("UINT8 temp"), "Should include declaration")
        assertTrue(code.contains("temp = 42"), "Should include assignment")
    }

    // =========================================================================
    // ARRAY OPERATIONS
    // =========================================================================

    @Test
    fun `array literal index assignment uses bounds check`() {
        val game =
            gbGame("test") {
                val data by u8Array(10)

                start =
                    scene("main") {
                        enter {
                            data[0] set 5
                            data[5] set 10
                        }
                    }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("GB_ARRAY_SET"), "Should use bounds-checked macro")
        assertTrue(code.contains("data"), "Should reference array name")
        assertTrue(code.contains("10"), "Should include array size in bounds check")
    }

    @Test
    fun `array variable index assignment uses bounds check`() {
        val game =
            gbGame("test") {
                val data by u8Array(8)
                var idx by u8Var(0)

                start = scene("main") { every.frame { data[idx] set 99 } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(
            code.contains("GB_ARRAY_SET(data, idx, 8,"),
            "Should use bounds-checked macro with variable index",
        )
    }

    // =========================================================================
    // MUSIC STATEMENTS
    // =========================================================================

    @Test
    fun `music play generates hUGE driver call`() {
        val game =
            gbGame("test") {
                val bgm = music("battle")

                start = scene("main") { enter { bgm.play() } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(
            code.contains("hUGE_init") || code.contains("battle"),
            "Should reference music track",
        )
    }

    @Test
    fun `music fade generates fade timer`() {
        val game =
            gbGame("test") {
                val bgm = music("battle")

                start = scene("main") { enter { bgm.fadeOut(60) } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("_music_fade_timer"), "Should set fade timer")
    }

    // =========================================================================
    // DIALOG STATEMENTS
    // =========================================================================

    @Test
    fun `dialog show sets visible flag`() {
        val game =
            gbGame("test") {
                val chat = dialog("chat") {}

                start = scene("main") { enter { chat.show() } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("_chat_visible = 1"), "Should set dialog visible")
    }

    @Test
    fun `dialog hide clears visible flag`() {
        val game =
            gbGame("test") {
                val chat = dialog("chat") {}

                start = scene("main") { enter { chat.hide() } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("_chat_visible = 0"), "Should clear dialog visible")
    }

    @Test
    fun `dialog say generates text display`() {
        val game =
            gbGame("test") {
                val chat = dialog("chat") {}

                start = scene("main") { enter { chat.say("Hello, world!") } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(
            code.contains("Hello") || code.contains("_chat_text"),
            "Should reference dialog text",
        )
    }

    // =========================================================================
    // MENU STATEMENTS
    // =========================================================================

    @Test
    fun `menu show activates menu`() {
        val game =
            gbGame("test") {
                val pauseMenu =
                    menu("pause") {
                        item("Resume")
                        item("Quit")
                    }

                start =
                    scene("main") {
                        every.frame { whenever(buttons.start.pressed) { pauseMenu.show() } }
                    }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("_pause_visible = 1"), "Should set menu visible")
        assertTrue(code.contains("_pause_active = 1"), "Should set menu active")
    }

    @Test
    fun `menu hide deactivates menu`() {
        val game =
            gbGame("test") {
                val pauseMenu = menu("pause") { item("Resume") }

                start = scene("main") { enter { pauseMenu.hide() } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("_pause_visible = 0"), "Should clear menu visible")
        assertTrue(code.contains("_pause_active = 0"), "Should clear menu active")
    }

    @Test
    fun `menu generates cursor tracking`() {
        val game =
            gbGame("test") {
                val mainMenu =
                    menu("main") {
                        item("Start")
                        item("Options")
                        item("Exit")
                    }

                start = scene("main") { enter { mainMenu.show() } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("_main_cursor"), "Should have cursor variable")
    }

    // =========================================================================
    // CAMERA STATEMENTS
    // =========================================================================

    @Test
    fun `camera update generates scroll code`() {
        val game =
            gbGame("test") {
                val cam = camera {}

                start = scene("main") { every.frame { cam.update() } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(
            code.contains("SCX_REG") || code.contains("SCY_REG") || code.contains("_camera"),
            "Should generate camera scroll update",
        )
    }

    @Test
    fun `camera follow generates target tracking`() {
        val game =
            gbGame("test") {
                val player by entity { position(80, 72) }
                val cam = camera {}

                start = scene("main") { enter { cam.follow(player) } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(
            code.contains("_camera") && code.contains("player"),
            "Should setup camera follow",
        )
    }

    @Test
    fun `camera shake generates shake effect`() {
        val game =
            gbGame("test") {
                val cam = camera {}

                start = scene("main") { enter { cam.shake(4, 10.frames) } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(
            code.contains("shake") || code.contains("_camera_shake"),
            "Should generate shake effect",
        )
    }

    @Test
    fun `camera bounds generates clamping`() {
        val game =
            gbGame("test") {
                val cam = camera { bounds(0..256, 0..256) }

                start = scene("main") { every.frame { cam.update() } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("_camera_bounds"), "Should setup camera bounds")
    }

    // =========================================================================
    // TRANSITION STATEMENTS
    // =========================================================================

    @Test
    fun `fade out generates transition code`() {
        val game =
            gbGame("test") {
                lateinit var next: SceneRef
                next = scene("next") {}

                start = scene("main") { enter { transitionTo(next) { fadeOut(30.frames) } } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(
            code.contains("fade") || code.contains("transition"),
            "Should generate fade transition",
        )
    }

    @Test
    fun `fade in generates transition code`() {
        val game =
            gbGame("test") {
                lateinit var next: SceneRef
                next = scene("next") {}

                start = scene("main") { enter { transitionTo(next) { fadeIn(20.frames) } } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("fade") || code.contains("transition"), "Should generate fade in")
    }

    @Test
    fun `transition cancel generates cancel code`() {
        val game =
            gbGame("test") {
                start =
                    scene("main") {
                        every.frame { whenever(buttons.b.pressed) { transition.cancel() } }
                    }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("cancel") || code.contains("transition"), "Should generate cancel")
    }

    // =========================================================================
    // INPUT-DRIVEN STATEMENTS
    // =========================================================================

    @Test
    fun `button press generates joypad check`() {
        val game =
            gbGame("test") {
                var jumped by u8Var(0)

                start =
                    scene("main") { every.frame { whenever(buttons.a.pressed) { jumped set 1 } } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("joypad") || code.contains("J_A"), "Should check joypad input")
    }

    @Test
    fun `dpad generates directional check`() {
        val game =
            gbGame("test") {
                var posX by u8Var(80)

                start =
                    scene("main") {
                        every.frame {
                            whenever(dpad.right) { posX += 1 }
                            whenever(dpad.left) { posX -= 1 }
                        }
                    }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(
            code.contains("J_RIGHT") || code.contains("joypad"),
            "Should check directional input",
        )
    }

    // =========================================================================
    // POOL STATEMENTS
    // =========================================================================

    @Test
    fun `pool spawn generates initialization`() {
        val game =
            gbGame("test") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        onFrame {}
                    }

                start =
                    scene("main") {
                        every.frame { whenever(buttons.a.pressed) { bullets.spawn() } }
                    }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("bullet_spawn"), "Should call spawn function")
    }

    @Test
    fun `pool despawn generates cleanup`() {
        val game =
            gbGame("test") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        onFrame {}
                    }

                start = scene("main") { enter { bullets.despawnAll() } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("bullet_despawn_all"), "Should call despawn all function")
    }

    @Test
    fun `pool update generates iteration`() {
        val game =
            gbGame("test") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        onFrame {}
                    }

                start = scene("main") { every.frame { bullets.update() } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("bullet_update"), "Should call update function")
    }

    // =========================================================================
    // ENTITY UPDATE STATEMENTS
    // =========================================================================

    @Test
    fun `entity position update generates assignment`() {
        val game =
            gbGame("test") {
                val player by entity { position(80, 72) }

                start = scene("main") { every.frame { player.x += 1 } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("player_x"), "Should reference entity x position")
    }

    // =========================================================================
    // SAVE STATEMENTS
    // =========================================================================

    @Test
    fun `save generates SRAM write`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("data") {
                        u8Field() // score
                        config { slots = 1 }
                    }

                start = scene("main") { enter { save.save(0) } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(
            code.contains("ENABLE_RAM") || code.contains("save") || code.contains("SRAM"),
            "Should generate save code",
        )
    }

    @Test
    fun `load generates SRAM read`() {
        val game =
            gbGame("test") {
                val save =
                    saveData("data") {
                        u8Field() // score
                        config { slots = 1 }
                    }

                start = scene("main") { enter { save.load(0) } }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(
            code.contains("ENABLE_RAM") || code.contains("load") || code.contains("SRAM"),
            "Should generate load code",
        )
    }

    // =========================================================================
    // COMPLEX STATEMENT COMBINATIONS
    // =========================================================================

    @Test
    fun `nested control flow generates correct structure`() {
        val game =
            gbGame("test") {
                var state by u8Var(0)
                var counter by u8Var(0)
                var result by u8Var(0)

                start =
                    scene("main") {
                        every.frame {
                            whenever(state isEqualTo 1) {
                                repeatWhile(counter isBelow 10) {
                                    whenever(counter isEqualTo 5) { result set 1 } otherwise
                                        {
                                            result set 0
                                        }
                                    counter += 1
                                }
                            }
                        }
                    }
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("if"), "Should have if")
        assertTrue(code.contains("while"), "Should have while")
        assertTrue(code.contains("else"), "Should have else")
    }

    @Test
    fun `multiple scenes generate constants correctly`() {
        val game =
            gbGame("test") {
                // Define all scenes first (without forward references)
                val gameOverScene = scene("gameover") {}
                val pauseScene = scene("pause") {}
                val gameScene = scene("game") {}

                start = gameScene
            }

        val code = GBDKCodeGenerator(game).generate()

        assertTrue(code.contains("SCENE_GAME"), "Should have game scene")
        assertTrue(code.contains("SCENE_PAUSE"), "Should have pause scene")
        assertTrue(code.contains("SCENE_GAMEOVER"), "Should have gameover scene")
    }
}
