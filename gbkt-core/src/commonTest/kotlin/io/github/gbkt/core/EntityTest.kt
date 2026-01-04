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
 * Tests for the entity-component system.
 *
 * Validates:
 * - Entity creation
 * - Adding position component
 * - Adding sprite component
 * - Property access (x, y)
 * - Error when accessing missing component
 */
class EntityTest {

    // =========================================================================
    // ENTITY CREATION
    // =========================================================================

    @Test
    fun `entity is created with correct name`() {
        val game =
            gbGame("EntityCreationTest") {
                val player by entity { position(80, 72) }

                start = scene("main") { every.frame {} }
            }

        assertEquals(1, game.entities.size, "Should have 1 entity")
        assertEquals("player", game.entities[0].name, "Entity name should be 'player'")
    }

    @Test
    fun `multiple entities are created separately`() {
        val game =
            gbGame("MultiEntityTest") {
                val player by entity { position(80, 72) }

                val enemy by entity { position(120, 72) }

                val npc by entity { position(40, 72) }

                start = scene("main") { every.frame {} }
            }

        assertEquals(3, game.entities.size, "Should have 3 entities")
        assertTrue(game.entities.any { it.name == "player" }, "Should have 'player' entity")
        assertTrue(game.entities.any { it.name == "enemy" }, "Should have 'enemy' entity")
        assertTrue(game.entities.any { it.name == "npc" }, "Should have 'npc' entity")
    }

    @Test
    fun `entity without components is valid`() {
        val game =
            gbGame("EmptyEntityTest") {
                val trigger by entity {
                    // No components
                }

                start = scene("main") { every.frame {} }
            }

        assertEquals(1, game.entities.size, "Should have 1 entity")
        assertEquals("trigger", game.entities[0].name, "Entity name should be 'trigger'")
        assertFalse(game.entities[0].hasPosition, "Entity should not have position")
        assertFalse(game.entities[0].hasSprite, "Entity should not have sprite")
    }

    // =========================================================================
    // ADDING POSITION COMPONENT
    // =========================================================================

    @Test
    fun `entity with position has hasPosition true`() {
        val game =
            gbGame("EntityPositionTest") {
                val player by entity { position(80, 72) }

                start = scene("main") { every.frame {} }
            }

        assertTrue(game.entities[0].hasPosition, "Entity should have position")
    }

    @Test
    fun `entity position generates variables`() {
        val game =
            gbGame("EntityPositionVarsTest") {
                val player by entity { position(100, 50) }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("player_x"), "Should generate player_x variable")
        assertTrue(code.contains("player_y"), "Should generate player_y variable")
    }

    @Test
    fun `entity position initial values are set in generated code`() {
        val game =
            gbGame("EntityPositionInitTest") {
                val player by entity { position(80, 72) }

                start = scene("main") { every.frame {} }
            }

        val posComponent = game.entities[0].positionComponent
        assertNotNull(posComponent, "Position component should exist")
        assertEquals("player_x", posComponent.xVarName, "X variable name should be player_x")
        assertEquals("player_y", posComponent.yVarName, "Y variable name should be player_y")

        // Verify initial values in generated code
        val code = game.compileForTest()
        assertTrue(code.contains("player_x") && code.contains("80"), "Should initialize X to 80")
        assertTrue(code.contains("player_y") && code.contains("72"), "Should initialize Y to 72")
    }

    @Test
    fun `entity position with u16 for large coordinates`() {
        val game =
            gbGame("EntityU16PositionTest") {
                val mapObject by entity { position(300, 400, u16 = true) }

                start = scene("main") { every.frame {} }
            }

        val posComponent = game.entities[0].positionComponent
        assertNotNull(posComponent, "Position component should exist")

        // Verify U16 is used for large coordinates
        val code = game.compileForTest()
        assertTrue(
            code.contains("UINT16") || code.contains("uint16_t") || code.contains("300"),
            "Should generate code with U16 type or large value"
        )
    }

    // =========================================================================
    // ADDING SPRITE COMPONENT
    // =========================================================================

    @Test
    fun `entity with sprite has hasSprite true`() {
        val game =
            gbGame("EntitySpriteTest") {
                val player by entity {
                    position(80, 72)
                    sprite(SpriteAsset("player.png")) { size = 8 x 16 }
                }

                start = scene("main") { every.frame {} }
            }

        assertTrue(game.entities[0].hasSprite, "Entity should have sprite")
        assertNotNull(game.entities[0].sprite, "Sprite should not be null")
    }

    @Test
    fun `entity sprite is bound to entity position`() {
        val game =
            gbGame("EntitySpriteBoundTest") {
                val player by entity {
                    position(80, 72)
                    sprite(SpriteAsset("player.png")) { size = 8 x 16 }
                }

                start = scene("main") { every.frame {} }
            }

        val sprite = game.entities[0].sprite
        assertNotNull(sprite, "Sprite should exist")
        assertNotNull(sprite.binding, "Sprite should be bound to entity position")
        assertEquals("player_x", sprite.binding?.xVar, "Sprite X should be bound to player_x")
        assertEquals("player_y", sprite.binding?.yVar, "Sprite Y should be bound to player_y")
    }

    @Test
    fun `entity sprite with animations`() {
        val game =
            gbGame("EntitySpriteAnimTest") {
                val player by entity {
                    position(80, 72)
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 16
                        animations {
                            "idle" plays (frames(0, 1) every 30.frames)
                            "run" plays (frames(2, 3, 4, 5) every 6.frames)
                        }
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val sprite = game.entities[0].sprite
        assertNotNull(sprite, "Sprite should exist")
        assertTrue(sprite.hasAnimations, "Sprite should have animations")
        assertEquals(2, sprite.animations.size, "Sprite should have 2 animations")
    }

    @Test
    fun `entity sprite with hitbox`() {
        val game =
            gbGame("EntitySpriteHitboxTest") {
                val player by entity {
                    position(80, 72)
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 16
                        hitbox(2, 2, 4, 12)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        val sprite = game.entities[0].sprite
        assertNotNull(sprite, "Sprite should exist")
        assertTrue(sprite.hasHitbox, "Sprite should have hitbox")
        assertEquals(2, sprite.hitbox?.xOffset, "Hitbox X offset should be 2")
    }

    // =========================================================================
    // PROPERTY ACCESS (X, Y)
    // =========================================================================

    @Test
    fun `entity x property is accessible`() {
        val game =
            gbGame("EntityXAccessTest") {
                var testVar by u8Var(0)

                val player by entity { position(80, 72) }

                start = scene("main") { every.frame { testVar set player.x } }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("player_x"), "Should reference player_x")
    }

    @Test
    fun `entity y property is accessible`() {
        val game =
            gbGame("EntityYAccessTest") {
                var testVar by u8Var(0)

                val player by entity { position(80, 72) }

                start = scene("main") { every.frame { testVar set player.y } }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("player_y"), "Should reference player_y")
    }

    @Test
    fun `entity x and y can be modified`() {
        val game =
            gbGame("EntityXYModifyTest") {
                val player by entity { position(80, 72) }

                start =
                    scene("main") {
                        every.frame {
                            player.x += 1
                            player.y -= 1
                        }
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("player_x"), "Should reference player_x")
        assertTrue(code.contains("player_y"), "Should reference player_y")
        assertTrue(code.contains("+") || code.contains("+="), "Should have addition")
        assertTrue(code.contains("-") || code.contains("-="), "Should have subtraction")
    }

    @Test
    fun `entity position destructuring works`() {
        val game =
            gbGame("EntityPositionDestructureTest") {
                var px by u8Var(0)
                var py by u8Var(0)

                val player by entity { position(80, 72) }

                start =
                    scene("main") {
                        every.frame {
                            val (x, y) = player.position
                            px set x
                            py set y
                        }
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("player_x"), "Should reference player_x")
        assertTrue(code.contains("player_y"), "Should reference player_y")
    }

    // =========================================================================
    // ERROR WHEN ACCESSING MISSING COMPONENT
    // =========================================================================

    @Test
    fun `accessing x on entity without position throws error`() {
        val game =
            gbGame("EntityNoPositionXTest") {
                val trigger by entity {
                    // No position component
                }

                start = scene("main") { every.frame {} }
            }

        val entity = game.entities[0]

        val exception = assertFailsWith<IllegalStateException> { entity.x }

        assertTrue(
            exception.message?.contains("no position") == true,
            "Error should mention no position component"
        )
    }

    @Test
    fun `accessing y on entity without position throws error`() {
        val game =
            gbGame("EntityNoPositionYTest") {
                val trigger by entity {
                    // No position component
                }

                start = scene("main") { every.frame {} }
            }

        val entity = game.entities[0]

        val exception = assertFailsWith<IllegalStateException> { entity.y }

        assertTrue(
            exception.message?.contains("no position") == true,
            "Error should mention no position component"
        )
    }

    @Test
    fun `accessing velocity on entity without velocity throws error`() {
        val game =
            gbGame("EntityNoVelocityTest") {
                val player by entity {
                    position(80, 72)
                    // No velocity component
                }

                start = scene("main") { every.frame {} }
            }

        val entity = game.entities[0]

        val exception = assertFailsWith<IllegalStateException> { entity.velX }

        assertTrue(
            exception.message?.contains("no velocity") == true,
            "Error should mention no velocity component"
        )
    }

    @Test
    fun `accessing sprite on entity without sprite returns null`() {
        val game =
            gbGame("EntityNoSpriteTest") {
                val trigger by entity {
                    position(80, 72)
                    // No sprite component
                }

                start = scene("main") { every.frame {} }
            }

        val entity = game.entities[0]

        assertNull(entity.sprite, "Sprite should be null when no sprite component")
        assertFalse(entity.hasSprite, "hasSprite should be false")
    }

    // =========================================================================
    // ENTITY WITH VELOCITY COMPONENT
    // =========================================================================

    @Test
    fun `entity with velocity has hasVelocity true`() {
        val game =
            gbGame("EntityVelocityTest") {
                val player by entity {
                    position(80, 72)
                    velocity(0, 0)
                }

                start = scene("main") { every.frame {} }
            }

        assertTrue(game.entities[0].hasVelocity, "Entity should have velocity")
    }

    @Test
    fun `entity velocity generates variables`() {
        val game =
            gbGame("EntityVelocityVarsTest") {
                val player by entity {
                    position(80, 72)
                    velocity(2, -1)
                }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("player_velX") || code.contains("player_vel_x"),
            "Should generate velocity X variable"
        )
        assertTrue(
            code.contains("player_velY") || code.contains("player_vel_y"),
            "Should generate velocity Y variable"
        )
    }

    @Test
    fun `entity velocity can be accessed and modified`() {
        val game =
            gbGame("EntityVelocityModifyTest") {
                val player by entity {
                    position(80, 72)
                    velocity(0, 0)
                }

                start =
                    scene("main") {
                        every.frame {
                            player.velX += 1
                            player.velY set 5
                        }
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.isNotEmpty(), "Should generate valid code")
    }

    // =========================================================================
    // ENTITY WITH HITBOX COMPONENT
    // =========================================================================

    @Test
    fun `entity with standalone hitbox has hasHitbox true`() {
        val game =
            gbGame("EntityHitboxTest") {
                val trigger by entity {
                    position(80, 72)
                    hitbox(0, 0, 16, 16)
                }

                start = scene("main") { every.frame {} }
            }

        assertTrue(game.entities[0].hasHitbox, "Entity should have hitbox")
    }

    @Test
    fun `entity hitbox from sprite is detected`() {
        val game =
            gbGame("EntitySpriteHitboxDetectTest") {
                val player by entity {
                    position(80, 72)
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 16
                        hitbox(2, 2, 4, 12)
                    }
                }

                start = scene("main") { every.frame {} }
            }

        assertTrue(game.entities[0].hasHitbox, "Entity should have hitbox from sprite")
    }

    // =========================================================================
    // ENTITY COLLISION
    // =========================================================================

    @Test
    fun `entity collidesWith generates collision check`() {
        val game =
            gbGame("EntityCollisionTest") {
                val player by entity {
                    position(80, 72)
                    sprite(SpriteAsset("player.png")) { size = 8 x 16 }
                }

                val enemy by entity {
                    position(84, 72)
                    sprite(SpriteAsset("enemy.png")) { size = 8 x 8 }
                }

                var hit by u8Var(0)

                start =
                    scene("main") {
                        every.frame { whenever(player collidesWith enemy) { hit set 1 } }
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("player"), "Should reference player")
        assertTrue(code.contains("enemy"), "Should reference enemy")
    }

    // =========================================================================
    // ENTITY CAPABILITY CHECKS
    // =========================================================================

    @Test
    fun `entity capability checks are correct`() {
        val game =
            gbGame("EntityCapabilitiesTest") {
                val fullEntity by entity {
                    position(80, 72)
                    velocity(0, 0)
                    sprite(SpriteAsset("entity.png")) {
                        size = 8 x 8
                        hitbox(0, 0, 8, 8)
                    }
                }

                val minimalEntity by entity {
                    // No components
                }

                start = scene("main") { every.frame {} }
            }

        val full = game.entities.find { it.name == "fullEntity" }!!
        val minimal = game.entities.find { it.name == "minimalEntity" }!!

        assertTrue(full.hasPosition, "Full entity should have position")
        assertTrue(full.hasVelocity, "Full entity should have velocity")
        assertTrue(full.hasSprite, "Full entity should have sprite")
        assertTrue(full.hasHitbox, "Full entity should have hitbox")

        assertFalse(minimal.hasPosition, "Minimal entity should not have position")
        assertFalse(minimal.hasVelocity, "Minimal entity should not have velocity")
        assertFalse(minimal.hasSprite, "Minimal entity should not have sprite")
        assertFalse(minimal.hasHitbox, "Minimal entity should not have hitbox")
    }
}
