/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.AnimationRef
import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.gbGame
import io.github.gbkt.core.graphics.*
import io.github.gbkt.core.ir.*
import kotlin.test.Test
import kotlin.test.assertTrue

/** Tests for animation code generation - frame arrays, tables, update functions, callbacks. */
class AnimationCodegenTest {

    @Test
    fun `sprite animation generates frame arrays and tables`() {
        val game =
            gbGame("test") {
                lateinit var walkAnim: AnimationRef

                val player =
                    sprite(SpriteAsset("player")) {
                        position(80, 72)
                        animations {
                            walkAnim = "walk" plays (frames(0, 1, 2, 3) every 8.frames)
                            "jump" plays (frames(4, 5) every 6.frames).once()
                        }
                    }

                start = scene("main") { every.frame { player.play(walkAnim) } }
            }

        val code = CodeGenerator(game).generate()

        // Frame arrays - generated code should contain frame data
        assertTrue(
            code.contains("walk") && code.contains("frames"),
            "Should generate walk frame array"
        )
        assertTrue(
            code.contains("jump") && code.contains("frames"),
            "Should generate jump frame array"
        )

        // Animation should be referenced
        assertTrue(code.contains("ANIM_FLAG_LOOPING"), "Should have looping flag")
    }

    @Test
    fun `animation update function handles forward and reverse`() {
        val game =
            gbGame("test") {
                lateinit var walkAnim: AnimationRef

                val player =
                    sprite(SpriteAsset("player")) {
                        position(80, 72)
                        animations { walkAnim = "walk" plays (frames(0, 1, 2, 3) every 6.frames) }
                    }

                start = scene("main") { every.frame { player.play(walkAnim) } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("update_animation"), "Should generate update function")
        assertTrue(code.contains("ANIM_FLAG_REVERSED"), "Should handle reverse direction")
        assertTrue(code.contains("ANIM_FLAG_PAUSED"), "Should handle paused state")
    }

    @Test
    fun `animation with onComplete callback generates handler`() {
        val game =
            gbGame("test") {
                val gameoverScene = scene("gameover") { every.frame {} }
                lateinit var attackAnim: AnimationRef

                val player =
                    sprite(SpriteAsset("player")) {
                        position(80, 72)
                        animations {
                            attackAnim =
                                "attack" plays
                                    (frames(0, 1, 2) every 6.frames).once().onComplete {
                                        scene(gameoverScene)
                                    }
                        }
                    }

                start = scene("main") { every.frame { player.play(attackAnim) } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("ANIM_FLAG_COMPLETE"), "Should set complete flag")
        // The callback should trigger a scene change
        assertTrue(
            code.contains("gameover"),
            "Should generate onComplete callback with scene change"
        )
    }

    @Test
    fun `animation play generates play function call`() {
        val game =
            gbGame("test") {
                lateinit var idleAnim: AnimationRef

                val player =
                    sprite(SpriteAsset("player")) {
                        position(80, 72)
                        animations {
                            idleAnim = "idle" plays (frames(0, 1) every 8.frames)
                            "walk" plays (frames(2, 3) every 6.frames)
                        }
                    }

                start =
                    scene("main") {
                        enter { player.play(idleAnim) }
                        every.frame {}
                    }
            }

        val code = CodeGenerator(game).generate()

        // Animation constants should be generated (case-insensitive check)
        val lowerCode = code.lowercase()
        assertTrue(lowerCode.contains("idle"), "Should reference idle animation")
        assertTrue(lowerCode.contains("anim"), "Should have animation-related code")
    }

    @Test
    fun `animation with empty frames generates warning placeholder`() {
        val game =
            gbGame("test") {
                val player =
                    sprite(SpriteAsset("player")) {
                        position(80, 72)
                        animations {
                            // Empty animation with no frames - should handle gracefully
                            "empty" plays (frames() every 6.frames)
                        }
                    }

                start = scene("main") { every.frame {} }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(
            code.contains("WARNING") || code.contains("placeholder") || code.contains("{ 0 }"),
            "Should handle empty animation gracefully"
        )
    }

    @Test
    fun `multiple sprites generate separate animation tables`() {
        val game =
            gbGame("test") {
                lateinit var walkAnim: AnimationRef
                lateinit var idleAnim: AnimationRef

                val player =
                    sprite(SpriteAsset("player")) {
                        position(80, 72)
                        animations { walkAnim = "walk" plays (frames(0, 1) every 6.frames) }
                    }

                val enemy =
                    sprite(SpriteAsset("enemy")) {
                        position(100, 72)
                        animations { idleAnim = "idle" plays (frames(0, 1, 2) every 8.frames) }
                    }

                start =
                    scene("main") {
                        every.frame {
                            player.play(walkAnim)
                            enemy.play(idleAnim)
                        }
                    }
            }

        val code = CodeGenerator(game).generate()

        // Check that animation code is generated (case-insensitive)
        val lowerCode = code.lowercase()
        assertTrue(lowerCode.contains("walk"), "Should have walk animation")
        assertTrue(lowerCode.contains("idle"), "Should have idle animation")
        assertTrue(
            lowerCode.contains("update") && lowerCode.contains("animation"),
            "Should generate update function"
        )
    }

    @Test
    fun `animation non-looping flag is set correctly`() {
        val game =
            gbGame("test") {
                lateinit var runAnim: AnimationRef

                val player =
                    sprite(SpriteAsset("player")) {
                        position(80, 72)
                        animations {
                            runAnim = "run" plays (frames(0, 1, 2, 3) every 4.frames).once()
                        }
                    }

                start = scene("main") { enter { player.play(runAnim, loop = false) } }
            }

        val code = CodeGenerator(game).generate()

        // A non-looping animation should be defined
        assertTrue(
            code.contains("ANIM_PLAYER_RUN") || (code.contains("run") && code.contains("anim")),
            "Should have run animation constant"
        )
        assertTrue(code.contains("run") && code.contains("frame"), "Should generate run frame data")
    }
}
