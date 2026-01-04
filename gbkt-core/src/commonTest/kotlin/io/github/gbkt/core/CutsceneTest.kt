/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.entity.*
import io.github.gbkt.core.graphics.*
import io.github.gbkt.core.ir.*
import kotlin.test.*

class CutsceneTest {

    @Test
    fun `cutscene DSL creates CutsceneDefinition`() {
        val game =
            gbGame("CutsceneTest") {
                val opening =
                    cutscene("opening") {
                        wait(30)
                        action {}
                        wait(60)
                    }

                val testScene =
                    scene("test") {
                        enter { opening.start() }
                        every.frame { opening.update() }
                    }

                start = testScene
            }

        assertEquals(1, game.cutscenes.size)
        assertEquals("opening", game.cutscenes[0].name)
        assertEquals(3, game.cutscenes[0].steps.size)
    }

    @Test
    fun `cutscene with parallel steps`() {
        val game =
            gbGame("ParallelTest") {
                var x by u8Var(0)

                val intro =
                    cutscene("intro") {
                        parallel {
                            action { x set 1 }
                            action { x set 2 }
                        }
                    }

                val testScene = scene("test") { every.frame { intro.update() } }

                start = testScene
            }

        assertEquals(1, game.cutscenes.size)
        val step = game.cutscenes[0].steps[0]
        assertTrue(step is TimelineStep.Parallel)
    }

    @Test
    fun `cutscene generates C code`() {
        val game =
            gbGame("CutsceneCodegenTest") {
                val opening =
                    cutscene("opening") {
                        wait(30)
                        action {}
                    }

                val testScene = scene("test") { every.frame { opening.update() } }

                start = testScene
            }

        val code = game.compileForTest()

        // Check for cutscene state variables
        assertTrue(code.contains("_opening_playing"), "Should have _opening_playing")
        assertTrue(code.contains("_opening_step"), "Should have _opening_step")
        assertTrue(code.contains("_opening_timer"), "Should have _opening_timer")

        // Check for cutscene functions
        assertTrue(code.contains("_opening_start"), "Should have _opening_start function")
        assertTrue(code.contains("_opening_update"), "Should have _opening_update function")
    }

    @Test
    fun `cutscene skip can be disabled`() {
        val game =
            gbGame("NonSkippableTest") {
                val important =
                    cutscene("important") {
                        skippable = false
                        wait(100)
                    }

                val testScene = scene("test") { every.frame {} }

                start = testScene
            }

        assertFalse(game.cutscenes[0].skippable)
    }

    @Test
    fun `cutscene isPlaying and isComplete conditions`() {
        val game =
            gbGame("CutsceneConditionsTest") {
                val intro = cutscene("intro") { wait(10) }

                val testScene =
                    scene("test") {
                        every.frame {
                            whenever(intro.isPlaying) {}
                            whenever(intro.isComplete) {}
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        assertTrue(code.contains("_intro_playing"))
        assertTrue(code.contains("_intro_complete"))
    }
}
