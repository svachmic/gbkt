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
 * Tests for the sprite system.
 *
 * Validates:
 * - Sprite creation with size
 * - Sprite position setting
 * - Sprite binding
 * - Animation assignment to sprite
 * - Multiple sprites with different sizes
 */
class SpriteTest {

    // =========================================================================
    // SPRITE CREATION WITH SIZE
    // =========================================================================

    @Test
    fun `sprite creation with default size`() {
        val game =
            gbGame("SpriteDefaultSizeTest") {
                val player =
                    sprite(SpriteAsset("player.png")) {
                        // Default size is 8x8
                    }

                start = scene("main") { every.frame {} }
            }

        assertEquals(1, game.sprites.size, "Should have 1 sprite")
        assertEquals(8, game.sprites[0].width, "Default width should be 8")
        assertEquals(8, game.sprites[0].height, "Default height should be 8")
    }

    @Test
    fun `sprite creation with custom size`() {
        val game =
            gbGame("SpriteCustomSizeTest") {
                val player = sprite(SpriteAsset("player.png")) { size = 8 x 16 }

                start = scene("main") { every.frame {} }
            }

        assertEquals(8, game.sprites[0].width, "Width should be 8")
        assertEquals(16, game.sprites[0].height, "Height should be 16")
    }

    @Test
    fun `sprite creation with 16x16 size`() {
        val game =
            gbGame("SpriteLargeSizeTest") {
                val boss = sprite(SpriteAsset("boss.png")) { size = 16 x 16 }

                start = scene("main") { every.frame {} }
            }

        assertEquals(16, game.sprites[0].width, "Width should be 16")
        assertEquals(16, game.sprites[0].height, "Height should be 16")
    }

    // =========================================================================
    // SPRITE POSITION SETTING
    // =========================================================================

    @Test
    fun `sprite with position generates position variables`() {
        val game =
            gbGame("SpritePositionTest") {
                val player =
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 16
                        position(80, 72)
                    }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should have position in sprite
        assertNotNull(game.sprites[0].position, "Sprite should have position")
        assertEquals(80, game.sprites[0].position?.initialX, "Initial X should be 80")
        assertEquals(72, game.sprites[0].position?.initialY, "Initial Y should be 72")

        // Should generate position variables
        assertTrue(code.contains("sprite0_x"), "Should generate X position variable")
        assertTrue(code.contains("sprite0_y"), "Should generate Y position variable")
    }

    @Test
    fun `sprite position is accessible via x and y properties`() {
        val game =
            gbGame("SpritePositionAccessTest") {
                val player =
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 8
                        position(100, 50)
                    }

                var testVar by u8Var(0)

                start =
                    scene("main") {
                        every.frame {
                            testVar set player.x
                            player.x += 1
                            player.y -= 1
                        }
                    }
            }

        val code = game.compileForTest()

        // Should reference sprite position in generated code
        assertTrue(code.contains("sprite0_x"), "Should reference sprite0_x")
        assertTrue(code.contains("sprite0_y"), "Should reference sprite0_y")
    }

    @Test
    fun `sprite without position throws error when accessing x or y`() {
        val game =
            gbGame("SpriteNoPositionTest") {
                val player =
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 8
                        // No position set
                    }

                start = scene("main") { every.frame {} }
            }

        assertFailsWith<IllegalStateException>("Should throw when accessing x without position") {
            game.sprites[0].x
        }

        assertFailsWith<IllegalStateException>("Should throw when accessing y without position") {
            game.sprites[0].y
        }
    }

    // =========================================================================
    // SPRITE BINDING
    // =========================================================================

    @Test
    fun `sprite bound to variables uses those variables`() {
        val game =
            gbGame("SpriteBindingTest") {
                var playerX by u8Var(80)
                var playerY by u8Var(72)

                val player =
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 16
                        boundTo(playerX, playerY)
                    }

                start = scene("main") { every.frame { playerX += 1 } }
            }

        val code = game.compileForTest()

        // Should have binding, not position
        assertNotNull(game.sprites[0].binding, "Sprite should have binding")
        assertNull(game.sprites[0].position, "Sprite should not have owned position")
        assertEquals("playerX", game.sprites[0].binding?.xVar, "X binding should be playerX")
        assertEquals("playerY", game.sprites[0].binding?.yVar, "Y binding should be playerY")

        // Should reference the bound variables
        assertTrue(code.contains("playerX"), "Should reference bound X variable")
        assertTrue(code.contains("playerY"), "Should reference bound Y variable")
    }

    @Test
    fun `sprite isBound returns true when position or binding is set`() {
        val game =
            gbGame("SpriteIsBoundTest") {
                val positioned =
                    sprite(SpriteAsset("a.png")) {
                        size = 8 x 8
                        position(10, 10)
                    }

                var x by u8Var(0)
                var y by u8Var(0)
                val bound =
                    sprite(SpriteAsset("b.png")) {
                        size = 8 x 8
                        boundTo(x, y)
                    }

                val unbound = sprite(SpriteAsset("c.png")) { size = 8 x 8 }

                start = scene("main") { every.frame {} }
            }

        assertTrue(game.sprites[0].isBound, "Positioned sprite should be bound")
        assertTrue(game.sprites[1].isBound, "BoundTo sprite should be bound")
        assertFalse(game.sprites[2].isBound, "Unbound sprite should not be bound")
    }

    // =========================================================================
    // ANIMATION ASSIGNMENT TO SPRITE
    // =========================================================================

    @Test
    fun `sprite with animations has hasAnimations true`() {
        val game =
            gbGame("SpriteAnimationTest") {
                val player =
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 16
                        position(80, 72)

                        animations {
                            "idle" plays (frames(0, 1) every 30.frames)
                            "run" plays (frames(2, 3, 4, 5) every 6.frames)
                        }
                    }

                start = scene("main") { every.frame {} }
            }

        assertTrue(game.sprites[0].hasAnimations, "Sprite should have animations")
        assertEquals(2, game.sprites[0].animations.size, "Should have 2 animations")
        assertTrue("idle" in game.sprites[0].animations, "Should have 'idle' animation")
        assertTrue("run" in game.sprites[0].animations, "Should have 'run' animation")
    }

    @Test
    fun `sprite play generates animation IR`() {
        val game =
            gbGame("SpritePlayAnimationTest") {
                // Capture AnimationRef for type-safe usage
                lateinit var idleAnim: AnimationRef

                val player =
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 16
                        position(80, 72)

                        animations {
                            idleAnim = "idle" plays (frames(0, 1) every 30.frames)
                            "run" plays (frames(2, 3, 4, 5) every 6.frames)
                        }
                    }

                start =
                    scene("main") {
                        enter { player.play(idleAnim) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        // Should generate animation play code
        assertTrue(
            code.contains("anim") || code.contains("animation") || code.contains("ANIM"),
            "Should generate animation-related code",
        )
    }

    @Test
    fun `sprite without animations has hasAnimations false`() {
        val game =
            gbGame("SpriteNoAnimationTest") {
                val player =
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 8
                        position(80, 72)
                    }

                start = scene("main") { every.frame {} }
            }

        assertFalse(game.sprites[0].hasAnimations, "Sprite should not have animations")
        assertTrue(game.sprites[0].animations.isEmpty(), "Animations should be empty")
    }

    @Test
    fun `sprite animation with regions`() {
        val game =
            gbGame("SpriteRegionsTest") {
                val player =
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 16
                        position(80, 72)

                        regions {
                            "idle" at 0 size 2
                            "run" at 2 size 4
                            "jump" at 6 size 1
                        }

                        animations {
                            "idle" plays (region("idle") every 30.frames)
                            "run" plays (region("run") every 6.frames)
                        }
                    }

                start = scene("main") { every.frame {} }
            }

        assertTrue(game.sprites[0].hasAnimations, "Sprite should have animations")
        assertEquals(2, game.sprites[0].animations.size, "Should have 2 animations")
    }

    // =========================================================================
    // MULTIPLE SPRITES WITH DIFFERENT SIZES
    // =========================================================================

    @Test
    fun `multiple sprites have separate OAM slots`() {
        val game =
            gbGame("MultiSpriteTest") {
                val player =
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 16
                        position(80, 72)
                    }

                val enemy =
                    sprite(SpriteAsset("enemy.png")) {
                        size = 8 x 8
                        position(120, 72)
                    }

                val bullet =
                    sprite(SpriteAsset("bullet.png")) {
                        size = 4 x 4
                        position(0, 0)
                    }

                start = scene("main") { every.frame {} }
            }

        assertEquals(3, game.sprites.size, "Should have 3 sprites")

        // Each sprite should have a unique OAM slot
        val slots = game.sprites.map { it.oamSlot }.toSet()
        assertEquals(3, slots.size, "All OAM slots should be unique")
    }

    @Test
    fun `multiple sprites with different sizes generate correct code`() {
        val game =
            gbGame("MultiSizeSpriteTest") {
                val small =
                    sprite(SpriteAsset("small.png")) {
                        size = 8 x 8
                        position(10, 10)
                    }

                val tall =
                    sprite(SpriteAsset("tall.png")) {
                        size = 8 x 16
                        position(50, 10)
                    }

                val wide =
                    sprite(SpriteAsset("wide.png")) {
                        size = 16 x 8
                        position(90, 10)
                    }

                val large =
                    sprite(SpriteAsset("large.png")) {
                        size = 16 x 16
                        position(130, 10)
                    }

                start = scene("main") { every.frame {} }
            }

        assertEquals(8, game.sprites[0].width, "Small width should be 8")
        assertEquals(8, game.sprites[0].height, "Small height should be 8")

        assertEquals(8, game.sprites[1].width, "Tall width should be 8")
        assertEquals(16, game.sprites[1].height, "Tall height should be 16")

        assertEquals(16, game.sprites[2].width, "Wide width should be 16")
        assertEquals(8, game.sprites[2].height, "Wide height should be 8")

        assertEquals(16, game.sprites[3].width, "Large width should be 16")
        assertEquals(16, game.sprites[3].height, "Large height should be 16")

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate valid code")
    }

    @Test
    fun `sprites collision between multiple sprites`() {
        val game =
            gbGame("MultiSpriteCollisionTest") {
                val player =
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 16
                        position(80, 72)
                    }

                val enemy =
                    sprite(SpriteAsset("enemy.png")) {
                        size = 8 x 8
                        position(84, 72)
                    }

                var hit by u8Var(0)

                start =
                    scene("main") { every.frame { whenever(player overlaps enemy) { hit set 1 } } }
            }

        val code = game.compileForTest()

        // Should generate collision check code
        assertTrue(
            code.contains("sprite0_x") && code.contains("sprite1_x"),
            "Should reference both sprite positions",
        )
    }

    // =========================================================================
    // SPRITE HITBOX
    // =========================================================================

    @Test
    fun `sprite with hitbox has hasHitbox true`() {
        val game =
            gbGame("SpriteHitboxTest") {
                val player =
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 16
                        position(80, 72)
                        hitbox(2, 2, 4, 12)
                    }

                start = scene("main") { every.frame {} }
            }

        assertTrue(game.sprites[0].hasHitbox, "Sprite should have hitbox")
        assertEquals(2, game.sprites[0].hitbox?.xOffset, "Hitbox X offset should be 2")
        assertEquals(2, game.sprites[0].hitbox?.yOffset, "Hitbox Y offset should be 2")
        assertEquals(4, game.sprites[0].hitbox?.width, "Hitbox width should be 4")
        assertEquals(12, game.sprites[0].hitbox?.height, "Hitbox height should be 12")
    }

    @Test
    fun `sprite effective hitbox uses sprite bounds when no hitbox defined`() {
        val game =
            gbGame("SpriteEffectiveHitboxTest") {
                val player =
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 16
                        position(80, 72)
                    }

                start = scene("main") { every.frame {} }
            }

        val hitbox = game.sprites[0].effectiveHitbox
        assertEquals(0, hitbox.xOffset, "Default hitbox X offset should be 0")
        assertEquals(0, hitbox.yOffset, "Default hitbox Y offset should be 0")
        assertEquals(8, hitbox.width, "Default hitbox width should match sprite width")
        assertEquals(16, hitbox.height, "Default hitbox height should match sprite height")
    }
}
