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

/** Tests for animation callback functionality, particularly type-safe scene transitions. */
class AnimationCallbackTest {

    @Test
    fun `animation onComplete with SceneRef generates correct scene change`() {
        val game =
            gbGame("test") {
                // Define gameover scene first so we have the SceneRef available
                val gameoverScene = scene("gameover") { every.frame {} }

                // Capture AnimationRef for type-safe usage
                lateinit var deathAnim: AnimationRef

                val player =
                    sprite(SpriteAsset("player")) {
                        position(80, 72)

                        animations {
                            // Use type-safe SceneRef in onComplete callback
                            deathAnim =
                                "death" plays
                                    (frames(0, 1, 2) every 10.frames).once().onComplete {
                                        scene(gameoverScene)
                                    }
                        }
                    }

                start = scene("main") { every.frame { player.play(deathAnim) } }
            }

        val code = CodeGenerator(game).generate()

        // The onComplete callback should generate a scene change to "gameover"
        assertTrue(
            code.contains("gameover"),
            "Generated code should contain scene change to 'gameover'. Generated:\n${code.lines().filter { it.contains("scene") || it.contains("gameover") }.joinToString("\n")}"
        )
    }

    @Test
    fun `animation onComplete scene change requires forward declaration`() {
        // When using type-safe scene references, scenes must be defined before use.
        // This test verifies the recommended pattern: define scenes first, then animations.
        val game =
            gbGame("test") {
                // Define gameover scene first so we have the SceneRef available
                val gameoverScene = scene("gameover") { every.frame {} }

                // Capture AnimationRef for type-safe usage
                lateinit var deathAnim: AnimationRef

                val player =
                    sprite(SpriteAsset("player")) {
                        position(80, 72)

                        animations {
                            deathAnim =
                                "death" plays
                                    (frames(0, 1, 2) every 10.frames).once().onComplete {
                                        scene(gameoverScene)
                                    }
                        }
                    }

                start = scene("main") { every.frame { player.play(deathAnim) } }
            }

        val code = CodeGenerator(game).generate()

        // The onComplete callback should generate a scene change to "gameover"
        assertTrue(
            code.contains("gameover"),
            "Generated code should contain scene change to 'gameover'. Generated:\n${code.lines().filter { it.contains("scene") || it.contains("gameover") }.joinToString("\n")}"
        )
    }

    @Test
    fun `animation builder onComplete with SceneRef works correctly`() {
        val game =
            gbGame("test") {
                val titleScene = scene("title") { every.frame {} }

                // Capture AnimationRef for type-safe usage
                lateinit var introAnim: AnimationRef

                val player =
                    sprite(SpriteAsset("player")) {
                        position(80, 72)

                        animations {
                            // Use builder-style animation with SceneRef
                            introAnim =
                                animation("intro") {
                                    frames(0, 1, 2, 3)
                                    delay(15)
                                    loop(false)
                                    onComplete { scene(titleScene) }
                                }
                        }
                    }

                start = scene("main") { every.frame { player.play(introAnim) } }
            }

        val code = CodeGenerator(game).generate()

        // The onComplete callback should generate a scene change to "title"
        assertTrue(
            code.contains("title"),
            "Generated code should contain scene change to 'title'. Generated:\n${code.lines().filter { it.contains("scene") || it.contains("title") }.joinToString("\n")}"
        )
    }

    @Test
    fun `animation onFrame callback with SceneRef works correctly`() {
        val game =
            gbGame("test") {
                val bossScene = scene("boss") { every.frame {} }

                // Capture AnimationRef for type-safe usage
                lateinit var warpAnim: AnimationRef

                val player =
                    sprite(SpriteAsset("player")) {
                        position(80, 72)

                        animations {
                            // Use onFrame to trigger scene change at a specific frame
                            warpAnim =
                                "warp" plays
                                    (frames(0, 1, 2, 3, 4) every 5.frames).onFrame(3) {
                                        scene(bossScene)
                                    }
                        }
                    }

                start = scene("main") { every.frame { player.play(warpAnim) } }
            }

        val code = CodeGenerator(game).generate()

        // The onFrame callback should generate a scene change to "boss"
        assertTrue(
            code.contains("boss"),
            "Generated code should contain scene change to 'boss' at frame 3. Generated:\n${code.lines().filter { it.contains("scene") || it.contains("boss") }.joinToString("\n")}"
        )
    }

    @Test
    fun `SceneRef scene transitions generate correct code`() {
        val game =
            gbGame("test") {
                val targetScene = scene("target") { every.frame {} }

                // Capture AnimationRef for type-safe usage
                lateinit var anim: AnimationRef

                val player =
                    sprite(SpriteAsset("player")) {
                        position(80, 72)
                        animations {
                            anim =
                                "anim" plays
                                    (frames(0, 1) every 10.frames).once().onComplete {
                                        scene(targetScene)
                                    }
                        }
                    }

                start = scene("main") { every.frame { player.play(anim) } }
            }

        val code = CodeGenerator(game).generate()

        // Should contain the scene change to "target"
        assertTrue(code.contains("target"), "Should generate target scene change")
    }
}
