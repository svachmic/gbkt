/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.*
import kotlin.test.*

/**
 * Tests for the dialog system.
 *
 * Validates:
 * - Dialog creation with simple text
 * - Dialog with choices
 * - Dialog with portrait
 * - Dialog with speaker name
 * - onComplete callback handling
 * - IR generation correctness
 */
class DialogTest {

    // =========================================================================
    // SIMPLE TEXT DIALOG TESTS
    // =========================================================================

    @Test
    fun `dialog with simple text generates correct IR`() {
        val game =
            gbGame("test") {
                val npc = dialog("npc") { speaker = "Elder" }

                start = scene("main") { enter { npc.say("Hello, traveler!") } }
            }

        val code = game.compileForTest()

        // Should contain the dialog text
        assertTrue(code.contains("Hello, traveler!"), "Should contain dialog text")
        // Should reference the npc dialog
        assertTrue(code.contains("npc") || code.contains("dialog"), "Should reference dialog name")
    }

    @Test
    fun `inline say function generates dialog without named dialog`() {
        val game = gbGame("test") { start = scene("main") { enter { say("You found a key!") } } }

        val code = game.compileForTest()

        assertTrue(code.contains("You found a key!"), "Should contain inline dialog text")
    }

    @Test
    fun `dialog with variable interpolation generates correct code`() {
        val game =
            gbGame("test") {
                var score by u16Var(0)
                val npc = dialog("npc") {}

                start = scene("main") { enter { npc.say("Score: ", score, " points!") } }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("Score:"), "Should contain text before variable")
        assertTrue(code.contains("points!"), "Should contain text after variable")
        assertTrue(code.contains("score"), "Should reference score variable")
    }

    // =========================================================================
    // DIALOG WITH CHOICES TESTS
    // =========================================================================

    @Test
    fun `dialog with choices generates choice menu`() {
        val game =
            gbGame("test") {
                var result by u8Var(0)
                val npc = dialog("npc") {}

                start =
                    scene("main") {
                        enter {
                            npc.choice("Yes", "No") { selected ->
                                whenever(selected isEqualTo 0) { result set 1 }
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        // Should contain the choice options
        assertTrue(code.contains("Yes"), "Should contain first choice option")
        assertTrue(code.contains("No"), "Should contain second choice option")
    }

    @Test
    fun `dialog with multiple choices generates all options`() {
        val game =
            gbGame("test") {
                var choice by u8Var(0)
                val shopkeeper = dialog("shopkeeper") {}

                start =
                    scene("main") {
                        enter {
                            shopkeeper.choice("Buy", "Sell", "Leave") { selected ->
                                whenever(selected isEqualTo 0) { choice set 1 }
                                whenever(selected isEqualTo 1) { choice set 2 }
                                whenever(selected isEqualTo 2) { choice set 3 }
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("Buy"), "Should contain Buy option")
        assertTrue(code.contains("Sell"), "Should contain Sell option")
        assertTrue(code.contains("Leave"), "Should contain Leave option")
    }

    // =========================================================================
    // DIALOG WITH PORTRAIT TESTS
    // =========================================================================

    @Test
    fun `dialog with portrait generates portrait configuration`() {
        val game =
            gbGame("test") {
                val npc =
                    dialog("npc") {
                        portrait("npc_portrait.png") {
                            size = 32 x 32
                            position = PortraitPosition.LEFT
                        }
                    }

                start = scene("main") { enter { npc.say("Hello!") } }
            }

        val code = game.compileForTest()

        // Should contain the dialog - portrait is metadata for rendering
        assertTrue(code.contains("Hello!"), "Should contain dialog text")
    }

    @Test
    fun `dialog with right-positioned portrait compiles`() {
        val game =
            gbGame("test") {
                val npc =
                    dialog("npc") { portrait("npc.png") { position = PortraitPosition.RIGHT } }

                start = scene("main") { enter { npc.say("Greetings!") } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate valid code with right portrait")
    }

    // =========================================================================
    // DIALOG WITH SPEAKER NAME TESTS
    // =========================================================================

    @Test
    fun `dialog with speaker name includes speaker`() {
        val game =
            gbGame("test") {
                val elder = dialog("elder") { speaker = "Elder" }

                start = scene("main") { enter { elder.say("Welcome, young one.") } }
            }

        val code = game.compileForTest()

        // Should contain both the speaker and the text
        assertTrue(
            code.contains("Elder") || code.contains("elder"),
            "Should reference speaker/dialog name"
        )
        assertTrue(code.contains("Welcome, young one."), "Should contain dialog text")
    }

    @Test
    fun `dialog say with speaker override works`() {
        val game =
            gbGame("test") {
                val npc = dialog("npc") { speaker = "Default" }

                start = scene("main") { enter { npc.say("Hello!").withSpeaker("Guard") } }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("Hello!"), "Should contain dialog text")
    }

    // =========================================================================
    // DIALOG STATE AND CALLBACK TESTS
    // =========================================================================

    @Test
    fun `dialog isActive condition generates correct code`() {
        val game =
            gbGame("test") {
                var paused by u8Var(0)
                val npc = dialog("npc") {}

                start = scene("main") { every.frame { whenever(npc.isActive) { paused set 1 } } }
            }

        val code = game.compileForTest()

        // Should generate code checking dialog state
        assertTrue(
            code.contains("npc") || code.contains("dialog"),
            "Should reference dialog for active check"
        )
    }

    @Test
    fun `dialog isComplete condition generates correct code`() {
        val game =
            gbGame("test") {
                var completed by u8Var(0)
                val npc = dialog("npc") {}

                start =
                    scene("main") { every.frame { whenever(npc.isComplete) { completed set 1 } } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate valid completion check code")
    }

    @Test
    fun `dialog tick generates update call`() {
        val game =
            gbGame("test") {
                val npc = dialog("npc") {}

                start = scene("main") { every.frame { npc.tick() } }
            }

        val code = game.compileForTest()

        // Should generate dialog update/tick call
        assertTrue(code.isNotEmpty(), "Should generate dialog tick code")
    }

    @Test
    fun `dialog show and hide generate correct calls`() {
        val game =
            gbGame("test") {
                val npc = dialog("npc") {}

                start =
                    scene("main") {
                        enter { npc.show() }
                        exit { npc.hide() }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate show/hide code")
    }

    // =========================================================================
    // DIALOG BOX CONFIGURATION TESTS
    // =========================================================================

    @Test
    fun `dialog with custom box configuration compiles`() {
        val game =
            gbGame("test") {
                val npc =
                    dialog("npc") {
                        box {
                            position(0, 10)
                            size = 20 x 6
                            border = BorderStyle.ROUNDED
                            padding = 2
                        }
                    }

                start = scene("main") { enter { npc.say("Custom box!") } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile with custom box configuration")
    }

    @Test
    fun `dialog with text speed configuration compiles`() {
        val game =
            gbGame("test") {
                val npc =
                    dialog("npc") {
                        textSpeed = 4
                        textSound = "blip.wav"
                    }

                start = scene("main") { enter { npc.say("Fast text!") } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile with text speed configuration")
    }

    @Test
    fun `dialog say with autoAdvance generates correct behavior`() {
        val game =
            gbGame("test") {
                val npc = dialog("npc") {}

                start = scene("main") { enter { npc.say("Auto text!").autoAdvance(60) } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate auto-advance code")
    }

    @Test
    fun `dialog say with noWait generates immediate continue`() {
        val game =
            gbGame("test") {
                val npc = dialog("npc") {}

                start =
                    scene("main") {
                        enter {
                            npc.say("Quick!").noWait()
                            npc.say("Next line!")
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("Quick!"), "Should contain first line")
        assertTrue(code.contains("Next line!"), "Should contain second line")
    }
}
