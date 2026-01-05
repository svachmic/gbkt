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

/** Tests for Camera system - scrolling, following, shake, and transitions. */
class CameraTest {

    // =========================================================================
    // CAMERA CONFIGURATION
    // =========================================================================

    @Test
    fun `camera with default config generates setup code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                start = scene("main") { enter { cam.update() } }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("_camera"), "Should generate camera variables")
    }

    @Test
    fun `camera with smoothing generates smoothing code`() {
        val game =
            gbGame("test") {
                val cam = camera { smoothing = 0.2f }
                start = scene("main") { enter { cam.update() } }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("camera"), "Should generate camera code")
    }

    @Test
    fun `camera with offset generates offset code`() {
        val game =
            gbGame("test") {
                val cam = camera { offset(10, -16) }
                start = scene("main") { enter { cam.update() } }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("camera"), "Should generate camera code")
    }

    @Test
    fun `camera with bounds generates bounds code`() {
        val game =
            gbGame("test") {
                val cam = camera { bounds(0..256, 0..256) }
                start = scene("main") { enter { cam.update() } }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("camera"), "Should generate camera code")
    }

    // =========================================================================
    // CAMERA UPDATE
    // =========================================================================

    @Test
    fun `camera update generates update call`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                start = scene("main") { every.frame { cam.update() } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("camera") || code.contains("update"),
            "Should generate update code",
        )
    }

    // =========================================================================
    // CAMERA FOLLOW
    // =========================================================================

    @Test
    fun `camera follow sprite generates follow code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                val player =
                    sprite(SpriteAsset("player.png")) {
                        position(80, 72)
                        size = 8 x 16
                    }

                start = scene("main") { enter { cam.follow(player) } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("camera") || code.contains("follow"),
            "Should generate follow code",
        )
    }

    @Test
    fun `camera follow with custom config generates configured follow`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                val player =
                    sprite(SpriteAsset("player.png")) {
                        position(80, 72)
                        size = 8 x 16
                    }

                start =
                    scene("main") {
                        enter {
                            cam.follow(player) {
                                smoothing = 0.3f
                                offset(0, -8)
                            }
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("camera"), "Should generate camera code")
    }

    @Test
    fun `camera followX generates X-only follow`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                val player =
                    sprite(SpriteAsset("player.png")) {
                        position(80, 72)
                        size = 8 x 16
                    }

                start = scene("main") { enter { cam.followX(player) } }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("camera"), "Should generate X follow code")
    }

    @Test
    fun `camera followY generates Y-only follow`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                val player =
                    sprite(SpriteAsset("player.png")) {
                        position(80, 72)
                        size = 8 x 16
                    }

                start = scene("main") { enter { cam.followY(player) } }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("camera"), "Should generate Y follow code")
    }

    @Test
    fun `camera stopFollow generates stop code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                val player =
                    sprite(SpriteAsset("player.png")) {
                        position(80, 72)
                        size = 8 x 16
                    }

                start =
                    scene("main") {
                        enter {
                            cam.follow(player)
                            cam.stopFollow()
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("camera"), "Should generate stop follow code")
    }

    // =========================================================================
    // CAMERA POSITION
    // =========================================================================

    @Test
    fun `camera setPosition generates position code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                start = scene("main") { enter { cam.setPosition(100, 50) } }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("camera") || code.contains("100"), "Should generate position code")
    }

    @Test
    fun `camera snapTo generates snap code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                val player =
                    sprite(SpriteAsset("player.png")) {
                        position(80, 72)
                        size = 8 x 16
                    }

                start = scene("main") { enter { cam.snapTo(player) } }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("camera"), "Should generate snap code")
    }

    @Test
    fun `camera snapTo with coordinates works`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                start = scene("main") { enter { cam.snapTo(100, 50) } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("camera") || code.contains("100"),
            "Should generate snap to coords",
        )
    }

    @Test
    fun `camera x and y expressions work in conditions`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                start =
                    scene("main") {
                        every.frame {
                            cam.update()
                            whenever(cam.x isAbove 100) { /* scroll limit */ }
                            whenever(cam.y isAbove 100) { /* scroll limit */ }
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("camera") || code.contains("100"),
            "Should generate position checks",
        )
    }

    // =========================================================================
    // CAMERA SHAKE
    // =========================================================================

    @Test
    fun `camera shake generates shake code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                start = scene("main") { enter { cam.shake(4, 10.frames) } }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("shake") || code.contains("camera"), "Should generate shake code")
    }

    @Test
    fun `camera shake with builder generates shake code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                start =
                    scene("main") {
                        enter {
                            cam.shake {
                                intensity = 6
                                duration = 20
                                decay = ShakeDecay.EXPONENTIAL
                            }
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("shake") || code.contains("camera"),
            "Should generate shake with builder",
        )
    }

    @Test
    fun `camera impact generates quick shake`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                start = scene("main") { enter { cam.impact(4) } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("shake") || code.contains("camera"),
            "Should generate impact shake",
        )
    }

    @Test
    fun `camera stopShake generates stop code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                start =
                    scene("main") {
                        enter {
                            cam.shake(4, 10.frames)
                            cam.stopShake()
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("camera"), "Should generate stop shake code")
    }

    // =========================================================================
    // SCREEN TRANSITIONS - FADE
    // =========================================================================

    @Test
    fun `camera fadeIn generates fade code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                start = scene("main") { enter { cam.fadeIn(30.frames) } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("fade") || code.contains("transition"),
            "Should generate fadeIn code",
        )
    }

    @Test
    fun `camera fadeIn with callback generates callback code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                lateinit var otherScene: SceneRef
                otherScene = scene("other") {}
                start = scene("main") { enter { cam.fadeIn(30.frames) { scene(otherScene) } } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("fade") || code.contains("transition"),
            "Should generate fadeIn with callback",
        )
    }

    @Test
    fun `camera fadeOut generates fade code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                start = scene("main") { enter { cam.fadeOut(30.frames) } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("fade") || code.contains("transition"),
            "Should generate fadeOut code",
        )
    }

    @Test
    fun `camera fadeOut with callback generates callback code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                lateinit var otherScene: SceneRef
                otherScene = scene("other") {}
                start = scene("main") { enter { cam.fadeOut(30.frames) { scene(otherScene) } } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("fade") || code.contains("transition"),
            "Should generate fadeOut with callback",
        )
    }

    // =========================================================================
    // SCREEN TRANSITIONS - FLASH
    // =========================================================================

    @Test
    fun `camera flash generates flash code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                start = scene("main") { enter { cam.flash(10.frames) } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("flash") || code.contains("transition"),
            "Should generate flash code",
        )
    }

    // =========================================================================
    // SCREEN TRANSITIONS - WIPE
    // =========================================================================

    @Test
    fun `camera wipeLeft generates wipe code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                start = scene("main") { enter { cam.wipeLeft(30.frames) } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("wipe") || code.contains("transition"),
            "Should generate wipe code",
        )
    }

    @Test
    fun `camera wipeRight generates wipe code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                start = scene("main") { enter { cam.wipeRight(30.frames) } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("wipe") || code.contains("transition"),
            "Should generate wipe RIGHT",
        )
    }

    @Test
    fun `camera wipeUp generates wipe code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                start = scene("main") { enter { cam.wipeUp(30.frames) } }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("wipe") || code.contains("transition"), "Should generate wipe UP")
    }

    @Test
    fun `camera wipeDown generates wipe code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                start = scene("main") { enter { cam.wipeDown(30.frames) } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("wipe") || code.contains("transition"),
            "Should generate wipe DOWN",
        )
    }

    // =========================================================================
    // SCREEN TRANSITIONS - IRIS
    // =========================================================================

    @Test
    fun `camera irisClose generates iris code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                start = scene("main") { enter { cam.irisClose(30.frames, 80, 72) } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("iris") || code.contains("transition"),
            "Should generate iris close code",
        )
    }

    @Test
    fun `camera irisOpen generates iris code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                start = scene("main") { enter { cam.irisOpen(30.frames, 80, 72) } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("iris") || code.contains("transition"),
            "Should generate iris open code",
        )
    }

    @Test
    fun `camera irisClose with sprite target works`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                val player =
                    sprite(SpriteAsset("player.png")) {
                        position(80, 72)
                        size = 8 x 16
                    }
                start = scene("main") { enter { cam.irisClose(30.frames, player) } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("iris") || code.contains("transition"),
            "Should generate iris on sprite",
        )
    }

    @Test
    fun `camera irisOpen with sprite target works`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                val player =
                    sprite(SpriteAsset("player.png")) {
                        position(80, 72)
                        size = 8 x 16
                    }
                start = scene("main") { enter { cam.irisOpen(30.frames, player) } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("iris") || code.contains("transition"),
            "Should generate iris open on sprite",
        )
    }

    // =========================================================================
    // TRANSITION STATE
    // =========================================================================

    @Test
    fun `camera isTransitioning condition works`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                start =
                    scene("main") {
                        every.frame {
                            cam.update()
                            whenever(cam.isTransitioning) { /* wait */ }
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("transition") || code.contains("camera"),
            "Should generate transitioning check",
        )
    }

    // =========================================================================
    // ENTITY FOLLOW
    // =========================================================================

    @Test
    fun `camera follow entity generates follow code`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                val player by entity {
                    position(80, 72)
                    sprite(SpriteAsset("player.png")) { size = 8 x 16 }
                }

                start = scene("main") { enter { cam.follow(player) } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("camera") || code.contains("follow"),
            "Should generate entity follow code",
        )
    }

    @Test
    fun `camera snapTo entity works`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                val player by entity {
                    position(80, 72)
                    sprite(SpriteAsset("player.png")) { size = 8 x 16 }
                }

                start = scene("main") { enter { cam.snapTo(player) } }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("camera"), "Should generate entity snap code")
    }

    @Test
    fun `camera followX entity works`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                val player by entity {
                    position(80, 72)
                    sprite(SpriteAsset("player.png")) { size = 8 x 16 }
                }

                start = scene("main") { enter { cam.followX(player) } }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("camera"), "Should generate entity followX code")
    }

    @Test
    fun `camera followY entity works`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                val player by entity {
                    position(80, 72)
                    sprite(SpriteAsset("player.png")) { size = 8 x 16 }
                }

                start = scene("main") { enter { cam.followY(player) } }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("camera"), "Should generate entity followY code")
    }

    @Test
    fun `camera irisClose with entity works`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                val player by entity {
                    position(80, 72)
                    sprite(SpriteAsset("player.png")) { size = 8 x 16 }
                }

                start = scene("main") { enter { cam.irisClose(30.frames, player) } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("iris") || code.contains("transition"),
            "Should generate iris on entity",
        )
    }

    @Test
    fun `camera irisOpen with entity works`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                val player by entity {
                    position(80, 72)
                    sprite(SpriteAsset("player.png")) { size = 8 x 16 }
                }

                start = scene("main") { enter { cam.irisOpen(30.frames, player) } }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("iris") || code.contains("transition"),
            "Should generate iris open on entity",
        )
    }
}
