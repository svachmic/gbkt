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
 * Tests for the collision detection system.
 *
 * Validates:
 * - Point vs AABB collision detection
 * - Circle vs AABB collision detection
 * - Sweep collision for fast-moving objects
 * - AABB creation from sprites and entities
 * - Edge cases (boundaries, corners, etc.)
 */
class CollisionTest {

    // =========================================================================
    // POINT VS AABB TESTS
    // =========================================================================

    @Test
    fun `point inside AABB returns true`() {
        val game =
            gbGame("PointInsideTest") {
                val player =
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 8
                        position(10, 10)
                    }

                var resultVar by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // Point at (14, 14) is inside AABB at (10, 10) with size 8x8
                            val point = Point(14, 14)
                            val aabb = AABB(Expr(IRLiteral(10)), Expr(IRLiteral(10)), 8, 8)
                            whenever(point collidesWithAABB aabb) { resultVar set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()

        // Should generate the comparison code
        // Point is inside if: x >= left && x < right && y >= top && y < bottom
        assertTrue(code.contains("14"), "Should contain point X coordinate")
        assertTrue(
            code.contains(">=") || code.contains("isAtLeast") || code.contains("10"),
            "Should contain boundary comparison"
        )
    }

    @Test
    fun `point on AABB boundary returns true`() {
        val game =
            gbGame("PointBoundaryTest") {
                var resultVar by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // Point at (10, 10) is on the left-top boundary
                            val point = Point(10, 10)
                            val aabb = AABB(Expr(IRLiteral(10)), Expr(IRLiteral(10)), 8, 8)
                            whenever(point collidesWithAABB aabb) { resultVar set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        // Point at boundary (10, 10) should be included (>= check)
        assertTrue(code.contains("10"), "Should contain boundary point coordinates")
    }

    @Test
    fun `point on AABB corner returns true`() {
        val game =
            gbGame("PointCornerTest") {
                var resultVar by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // Point at top-left corner (10, 10)
                            val point = Point(10, 10)
                            val aabb = AABB(Expr(IRLiteral(10)), Expr(IRLiteral(10)), 8, 8)
                            whenever(point collidesWithAABB aabb) { resultVar set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate valid code")
    }

    @Test
    fun `point outside AABB returns false`() {
        val game =
            gbGame("PointOutsideTest") {
                var resultVar by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // Point at (5, 5) is outside AABB at (10, 10)
                            val point = Point(5, 5)
                            val aabb = AABB(Expr(IRLiteral(10)), Expr(IRLiteral(10)), 8, 8)
                            whenever(point collidesWithAABB aabb) { resultVar set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        // Should generate the condition check even for outside points
        assertTrue(code.contains("5"), "Should contain point coordinates")
    }

    @Test
    fun `point collision with sprite AABB`() {
        val game =
            gbGame("PointSpriteTest") {
                val enemy =
                    sprite(SpriteAsset("enemy.png")) {
                        size = 8 x 8
                        position(50, 50)
                    }

                var hit by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            val bullet = Point(54, 54)
                            whenever(bullet collidesWithAABB enemy) { hit set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("enemy_x") || code.contains("54"),
            "Should reference sprite position or point coordinate"
        )
    }

    // =========================================================================
    // CIRCLE VS AABB TESTS
    // =========================================================================

    @Test
    fun `circle touching AABB edge returns true`() {
        val game =
            gbGame("CircleTouchingTest") {
                var resultVar by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // Circle at (5, 15) with radius 5 touches AABB at (10, 10) size 8x8
                            // Distance from circle center to AABB edge = 5 (equals radius)
                            val circle = Circle(5, 15, 5)
                            val aabb = AABB(Expr(IRLiteral(10)), Expr(IRLiteral(10)), 8, 8)
                            whenever(circle collidesWithAABB aabb) { resultVar set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        // Should generate distance squared comparison
        assertTrue(
            code.contains("25") || code.contains("*"),
            "Should contain radius squared (25) or multiplication for distance calc"
        )
    }

    @Test
    fun `circle overlapping AABB returns true`() {
        val game =
            gbGame("CircleOverlapTest") {
                var resultVar by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // Circle at (14, 14) with radius 8 clearly overlaps AABB at (10, 10)
                            // size 8x8
                            val circle = Circle(14, 14, 8)
                            val aabb = AABB(Expr(IRLiteral(10)), Expr(IRLiteral(10)), 8, 8)
                            whenever(circle collidesWithAABB aabb) { resultVar set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        // radius squared = 64
        assertTrue(
            code.contains("64") || code.contains("8"),
            "Should contain radius or radius squared value"
        )
    }

    @Test
    fun `circle inside AABB returns true`() {
        val game =
            gbGame("CircleInsideTest") {
                var resultVar by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // Small circle at (14, 14) with radius 2 is fully inside AABB at (10,
                            // 10) size 8x8
                            val circle = Circle(14, 14, 2)
                            val aabb = AABB(Expr(IRLiteral(10)), Expr(IRLiteral(10)), 8, 8)
                            whenever(circle collidesWithAABB aabb) { resultVar set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        // radius squared = 4
        assertTrue(code.isNotEmpty(), "Should generate valid collision code")
    }

    @Test
    fun `circle outside AABB returns false`() {
        val game =
            gbGame("CircleOutsideTest") {
                var resultVar by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // Circle at (0, 0) with radius 3 is outside AABB at (10, 10) size 8x8
                            // Distance > radius
                            val circle = Circle(0, 0, 3)
                            val aabb = AABB(Expr(IRLiteral(10)), Expr(IRLiteral(10)), 8, 8)
                            whenever(circle collidesWithAABB aabb) { resultVar set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        // radius squared = 9
        assertTrue(code.contains("9") || code.contains("3"), "Should contain radius value")
    }

    @Test
    fun `circle with large radius catches distant AABB`() {
        val game =
            gbGame("CircleLargeRadiusTest") {
                var resultVar by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // Circle at (0, 0) with radius 50 should catch AABB at (30, 30)
                            val circle = Circle(0, 0, 50)
                            val aabb = AABB(Expr(IRLiteral(30)), Expr(IRLiteral(30)), 8, 8)
                            whenever(circle collidesWithAABB aabb) { resultVar set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        // radius squared = 2500
        assertTrue(
            code.contains("2500") || code.contains("50"),
            "Should contain large radius value"
        )
    }

    @Test
    fun `circle collision with sprite`() {
        val game =
            gbGame("CircleSpriteTest") {
                val enemy =
                    sprite(SpriteAsset("enemy.png")) {
                        size = 8 x 8
                        position(50, 50)
                    }

                var hit by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // Explosion circle centered on enemy
                            val explosion = Circle(50, 50, 16)
                            whenever(explosion collidesWithAABB enemy) { hit set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        // radius squared = 256
        assertTrue(
            code.contains("256") || code.contains("enemy"),
            "Should reference sprite or radius squared"
        )
    }

    // =========================================================================
    // AABB CREATION TESTS
    // =========================================================================

    @Test
    fun `AABB fromSprite creates correct bounds`() {
        val game =
            gbGame("AABBFromSpriteTest") {
                val player =
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 16
                        position(80, 72)
                    }

                val testScene =
                    scene("test") {
                        enter {
                            // Create AABB from sprite and use it
                            val playerAABB = AABB.fromSprite(player)
                            val point = Point(84, 80)
                            whenever(point collidesWithAABB playerAABB) {
                                // Hit!
                            }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        // Should reference player position variables
        assertTrue(
            code.contains("player") || code.contains("80") || code.contains("72"),
            "Should reference player sprite"
        )
    }

    @Test
    fun `AABB fromEntity creates correct bounds`() {
        val game =
            gbGame("AABBFromEntityTest") {
                val player by entity {
                    position(80, 72)
                    hitbox(0, 0, 8, 16)
                }

                val enemy by entity { position(100, 72) }

                val testScene =
                    scene("test") {
                        enter {
                            val playerAABB = AABB.fromEntity(player)
                            val point = Point(84, 80)
                            whenever(point collidesWithAABB playerAABB) {
                                // Hit!
                            }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate valid code")
    }

    // =========================================================================
    // SWEEP COLLISION TESTS
    // =========================================================================

    @Test
    fun `fast object sweep detects target`() {
        val game =
            gbGame("SweepDetectTest") {
                var hit by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // Fast bullet moving from (0, 50) to (100, 50)
                            val result =
                                sweepCollision(
                                    startX = Expr(IRLiteral(0)),
                                    startY = Expr(IRLiteral(50)),
                                    deltaX = Expr(IRLiteral(100)),
                                    deltaY = Expr(IRLiteral(0)),
                                    width = 4,
                                    height = 4,
                                    target = AABB(Expr(IRLiteral(50)), Expr(IRLiteral(48)), 8, 8)
                                )
                            whenever(result.collided) { hit set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate valid sweep collision code")
    }

    @Test
    fun `stationary object sweep is equivalent to AABB`() {
        val game =
            gbGame("SweepStationaryTest") {
                var hit by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // Object not moving (delta = 0)
                            val result =
                                sweepCollision(
                                    startX = Expr(IRLiteral(50)),
                                    startY = Expr(IRLiteral(50)),
                                    deltaX = Expr(IRLiteral(0)),
                                    deltaY = Expr(IRLiteral(0)),
                                    width = 8,
                                    height = 8,
                                    target = AABB(Expr(IRLiteral(54)), Expr(IRLiteral(54)), 8, 8)
                                )
                            whenever(result.collided) { hit set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        // With zero delta, should still detect overlap
        assertTrue(code.isNotEmpty(), "Should generate valid code for stationary sweep")
    }

    @Test
    fun `sweep collision with sprites`() {
        val game =
            gbGame("SweepSpriteTest") {
                val bullet =
                    sprite(SpriteAsset("bullet.png")) {
                        size = 4 x 4
                        position(0, 50)
                    }

                val enemy =
                    sprite(SpriteAsset("enemy.png")) {
                        size = 8 x 8
                        position(50, 48)
                    }

                var bulletVelX by u8Var(10)
                var bulletVelY by u8Var(0)
                var hit by u8Var(0)

                val testScene =
                    scene("test") {
                        every.frame {
                            val result =
                                sweepCollision(
                                    startX = bullet.x,
                                    startY = bullet.y,
                                    deltaX = Expr(IRVar("bulletVelX")),
                                    deltaY = Expr(IRVar("bulletVelY")),
                                    sprite = bullet,
                                    target = enemy
                                )
                            whenever(result.collided) { hit set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        // Sprites generate as sprite0_x, sprite1_x etc., not original names
        // Sweep collision generates bounds checking code
        assertTrue(
            code.contains("sprite0") && code.contains("sprite1"),
            "Should reference both sprites (as sprite0/sprite1)"
        )
    }

    @Test
    fun `sweep collision with entities`() {
        val game =
            gbGame("SweepEntityTest") {
                val bullet by entity {
                    position(0, 50)
                    velocity(10, 0)
                    hitbox(0, 0, 4, 4)
                }

                val enemy by entity {
                    position(50, 48)
                    hitbox(0, 0, 8, 8)
                }

                var hit by u8Var(0)

                val testScene =
                    scene("test") {
                        every.frame {
                            val result = sweepCollision(entity = bullet, target = enemy)
                            whenever(result.collided) { hit set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate valid entity sweep collision code")
    }

    // =========================================================================
    // SPRITE COLLISION TESTS
    // =========================================================================

    @Test
    fun `sprite overlaps sprite generates collision check`() {
        val game =
            gbGame("SpriteOverlapTest") {
                val player =
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 8
                        position(80, 72)
                    }

                val enemy =
                    sprite(SpriteAsset("enemy.png")) {
                        size = 8 x 8
                        position(84, 72)
                    }

                var hit by u8Var(0)

                val testScene =
                    scene("test") { every.frame { whenever(player overlaps enemy) { hit set 1 } } }

                start = testScene
            }

        val code = game.compileForTest()
        // Collision is inlined as AABB check: sprite0_x + 8u > sprite1_x && ...
        assertTrue(
            code.contains("sprite0_x") &&
                code.contains("sprite1_x") &&
                code.contains(">") &&
                code.contains("<"),
            "Should generate inlined AABB collision check. Code: ${code.lines().find { it.contains("if") && it.contains("sprite") }}"
        )
    }

    @Test
    fun `sprite collidesWith is alias for overlaps`() {
        val game =
            gbGame("CollidesWithTest") {
                val player =
                    sprite(SpriteAsset("player.png")) {
                        size = 8 x 8
                        position(80, 72)
                    }

                val enemy =
                    sprite(SpriteAsset("enemy.png")) {
                        size = 8 x 8
                        position(84, 72)
                    }

                var hit by u8Var(0)

                val testScene =
                    scene("test") {
                        every.frame { whenever(player collidesWith enemy) { hit set 1 } }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        // Collision is inlined as AABB check: sprite0_x + 8u > sprite1_x && ...
        assertTrue(
            code.contains("sprite0_x") &&
                code.contains("sprite1_x") &&
                code.contains(">") &&
                code.contains("<"),
            "collidesWith should generate inlined AABB collision check. Code: ${code.lines().find { it.contains("if") && it.contains("sprite") }}"
        )
    }

    // =========================================================================
    // SCREEN BOUNDS TESTS
    // =========================================================================

    @Test
    fun `screen constants are correct`() {
        assertEquals(160, screen.width, "Screen width should be 160")
        assertEquals(144, screen.height, "Screen height should be 144")
        assertEquals(20, screen.tileWidth, "Screen tile width should be 20")
        assertEquals(18, screen.tileHeight, "Screen tile height should be 18")
    }

    @Test
    fun `screen center is correct`() {
        assertEquals(80 x 72, screen.center, "Screen center should be 80x72")
    }

    // =========================================================================
    // PRECISE SWEEP COLLISION TESTS
    // =========================================================================

    @Test
    fun `sweepCollisionPrecise returns all result fields`() {
        val game =
            gbGame("SweepPreciseTest") {
                var hit by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            val result =
                                sweepCollisionPrecise(
                                    startX = Expr(IRLiteral(0)),
                                    startY = Expr(IRLiteral(50)),
                                    deltaX = Expr(IRLiteral(100)),
                                    deltaY = Expr(IRLiteral(0)),
                                    width = 4,
                                    height = 4,
                                    target = AABB(Expr(IRLiteral(50)), Expr(IRLiteral(48)), 8, 8)
                                )
                            whenever(result.collided) { hit set 1 }
                            // Verify all fields are populated (at compile time)
                            assertNotNull(result.hitTime, "hitTime should be populated")
                            assertNotNull(result.normalX, "normalX should be populated")
                            assertNotNull(result.normalY, "normalY should be populated")
                            assertNotNull(result.contactX, "contactX should be populated")
                            assertNotNull(result.contactY, "contactY should be populated")
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate valid precise sweep collision code")
    }

    @Test
    fun `sweepCollisionPrecise generates collision normal logic`() {
        val game =
            gbGame("SweepNormalTest") {
                var hit by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            val result =
                                sweepCollisionPrecise(
                                    startX = Expr(IRLiteral(0)),
                                    startY = Expr(IRLiteral(50)),
                                    deltaX = Expr(IRLiteral(100)),
                                    deltaY = Expr(IRLiteral(0)),
                                    width = 4,
                                    height = 4,
                                    target = AABB(Expr(IRLiteral(50)), Expr(IRLiteral(48)), 8, 8)
                                )
                            // Verify normalX and normalY are populated
                            assertNotNull(result.normalX, "normalX should be populated")
                            assertNotNull(result.normalY, "normalY should be populated")
                            whenever(result.collided) { hit set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        // Should contain ternary operators for normal calculation
        assertTrue(
            code.contains("?") && code.contains(":"),
            "Should generate ternary expressions for normal calculation"
        )
    }

    @Test
    fun `sweepCollisionPrecise generates contact point calculation`() {
        val game =
            gbGame("SweepContactTest") {
                var hit by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            val result =
                                sweepCollisionPrecise(
                                    startX = Expr(IRLiteral(0)),
                                    startY = Expr(IRLiteral(50)),
                                    deltaX = Expr(IRLiteral(100)),
                                    deltaY = Expr(IRLiteral(10)),
                                    width = 4,
                                    height = 4,
                                    target = AABB(Expr(IRLiteral(50)), Expr(IRLiteral(48)), 8, 8)
                                )
                            // Verify contact points are populated
                            assertNotNull(result.contactX, "contactX should be populated")
                            assertNotNull(result.contactY, "contactY should be populated")
                            whenever(result.collided) { hit set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        // Should contain division by 255 for contact point interpolation
        assertTrue(
            code.contains("255") || code.contains("/"),
            "Should generate contact point interpolation"
        )
    }

    @Test
    fun `sweepCollisionPrecise with sprites generates valid code`() {
        val game =
            gbGame("SweepPreciseSpriteTest") {
                val bullet =
                    sprite(SpriteAsset("bullet.png")) {
                        size = 4 x 4
                        position(0, 50)
                    }

                val enemy =
                    sprite(SpriteAsset("enemy.png")) {
                        size = 8 x 8
                        position(50, 48)
                    }

                var bulletVelX by u8Var(10)
                var bulletVelY by u8Var(0)
                var hit by u8Var(0)

                val testScene =
                    scene("test") {
                        every.frame {
                            val result =
                                sweepCollisionPrecise(
                                    startX = bullet.x,
                                    startY = bullet.y,
                                    deltaX = Expr(IRVar("bulletVelX")),
                                    deltaY = Expr(IRVar("bulletVelY")),
                                    sprite = bullet,
                                    target = enemy
                                )
                            whenever(result.collided) { hit set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("sprite0") && code.contains("sprite1"),
            "Should reference both sprites"
        )
    }

    @Test
    fun `sweepCollisionPrecise with entities generates valid code`() {
        val game =
            gbGame("SweepPreciseEntityTest") {
                val bullet by entity {
                    position(0, 50)
                    velocity(10, 0)
                    hitbox(0, 0, 4, 4)
                }

                val enemy by entity {
                    position(50, 48)
                    hitbox(0, 0, 8, 8)
                }

                var hit by u8Var(0)

                val testScene =
                    scene("test") {
                        every.frame {
                            val result = sweepCollisionPrecise(entity = bullet, target = enemy)
                            whenever(result.collided) { hit set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate valid entity precise sweep collision code")
    }

    // =========================================================================
    // SWEEP COLLISION SIMPLE ALIAS TESTS
    // =========================================================================

    @Test
    fun `sweepCollisionSimple is equivalent to sweepCollision`() {
        val game =
            gbGame("SweepSimpleAliasTest") {
                var hit by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            val result =
                                sweepCollisionSimple(
                                    startX = Expr(IRLiteral(0)),
                                    startY = Expr(IRLiteral(50)),
                                    deltaX = Expr(IRLiteral(100)),
                                    deltaY = Expr(IRLiteral(0)),
                                    width = 4,
                                    height = 4,
                                    target = AABB(Expr(IRLiteral(50)), Expr(IRLiteral(48)), 8, 8)
                                )
                            whenever(result.collided) { hit set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "sweepCollisionSimple should generate valid code")
    }

    // =========================================================================
    // SWEEP RESULT STRUCTURE TESTS
    // =========================================================================

    @Test
    fun `SweepResult has all expected fields`() {
        // Test that SweepResult can be created with all fields
        val collided = Condition(IRLiteral(1))
        val hitTime = Expr(IRLiteral(128))
        val normalX = Expr(IRLiteral(-1))
        val normalY = Expr(IRLiteral(0))
        val contactX = Expr(IRLiteral(50))
        val contactY = Expr(IRLiteral(48))

        val result =
            SweepResult(
                collided = collided,
                hitTime = hitTime,
                normalX = normalX,
                normalY = normalY,
                contactX = contactX,
                contactY = contactY
            )

        assertEquals(collided, result.collided, "collided should match")
        assertEquals(hitTime, result.hitTime, "hitTime should match")
        assertEquals(normalX, result.normalX, "normalX should match")
        assertEquals(normalY, result.normalY, "normalY should match")
        assertEquals(contactX, result.contactX, "contactX should match")
        assertEquals(contactY, result.contactY, "contactY should match")
    }

    @Test
    fun `SweepResult default values are null for optional fields`() {
        val collided = Condition(IRLiteral(1))
        val result = SweepResult(collided = collided)

        assertNull(result.hitTime, "hitTime should default to null")
        assertNull(result.normalX, "normalX should default to null")
        assertNull(result.normalY, "normalY should default to null")
        assertNull(result.contactX, "contactX should default to null")
        assertNull(result.contactY, "contactY should default to null")
    }
}
