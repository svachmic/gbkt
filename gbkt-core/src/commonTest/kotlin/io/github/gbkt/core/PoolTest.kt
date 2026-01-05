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
 * Tests for the entity pool system.
 *
 * Validates:
 * - Pool creation with capacity
 * - Spawn from pool
 * - Despawn back to pool
 * - Pool full behavior
 * - trySpawn/orElse pattern
 */
class PoolTest {

    // =========================================================================
    // POOL CREATION WITH CAPACITY
    // =========================================================================

    @Test
    fun `pool is created with correct name`() {
        val game =
            gbGame("PoolNameTest") {
                val bullets = pool("bullet", size = 8) { position(0, 0) }

                start = scene("main") { every.frame {} }
            }

        assertEquals(1, game.pools.size, "Should have 1 pool")
        assertEquals("bullet", game.pools[0].name, "Pool name should be 'bullet'")
    }

    @Test
    fun `pool has correct capacity`() {
        val game =
            gbGame("PoolCapacityTest") {
                val bullets = pool("bullet", size = 16) { position(0, 0) }

                start = scene("main") { every.frame {} }
            }

        assertEquals(16, game.pools[0].size, "Pool size should be 16")
    }

    @Test
    fun `pool with different sizes`() {
        val game =
            gbGame("PoolSizesTest") {
                val smallPool = pool("small", size = 4) { position(0, 0) }

                val mediumPool = pool("medium", size = 16) { position(0, 0) }

                val largePool = pool("large", size = 32) { position(0, 0) }

                start = scene("main") { every.frame {} }
            }

        assertEquals(3, game.pools.size, "Should have 3 pools")
        assertEquals(4, game.pools[0].size, "Small pool size should be 4")
        assertEquals(16, game.pools[1].size, "Medium pool size should be 16")
        assertEquals(32, game.pools[2].size, "Large pool size should be 32")
    }

    @Test
    fun `pool generates size constant in code`() {
        val game =
            gbGame("PoolSizeConstTest") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }
                    }

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("BULLET_POOL_SIZE") && code.contains("8"),
            "Should define pool size constant",
        )
    }

    // =========================================================================
    // SPAWN FROM POOL
    // =========================================================================

    @Test
    fun `pool spawn generates spawn call`() {
        val game =
            gbGame("PoolSpawnTest") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }
                    }

                start =
                    scene("main") {
                        enter { bullets.spawn() }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("bullet_spawn"), "Should generate spawn call")
    }

    @Test
    fun `pool spawn with initialization`() {
        val game =
            gbGame("PoolSpawnInitTest") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }
                    }

                start =
                    scene("main") {
                        enter {
                            bullets.spawn {
                                x set 80
                                y set 72
                            }
                        }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("bullet_spawn"), "Should generate spawn call")
        assertTrue(code.contains("80"), "Should set X to 80")
        assertTrue(code.contains("72"), "Should set Y to 72")
    }

    @Test
    fun `pool spawnAt sets position`() {
        val game =
            gbGame("PoolSpawnAtTest") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }
                    }

                start =
                    scene("main") {
                        enter { bullets.spawnAt(100, 50) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("100") && code.contains("50"), "Should spawn at position 100, 50")
    }

    @Test
    fun `pool spawn with expression position`() {
        val game =
            gbGame("PoolSpawnExprTest") {
                val player by entity { position(80, 72) }

                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }
                    }

                start =
                    scene("main") {
                        enter { bullets.spawnAt(player.x, player.y) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("player_x"), "Should reference player_x")
        assertTrue(code.contains("player_y"), "Should reference player_y")
    }

    // =========================================================================
    // DESPAWN BACK TO POOL
    // =========================================================================

    @Test
    fun `pool despawnAll generates despawn all call`() {
        val game =
            gbGame("PoolDespawnAllTest") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }
                    }

                start =
                    scene("main") {
                        enter { bullets.despawnAll() }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("bullet_despawn_all"), "Should generate despawn_all call")
    }

    @Test
    fun `pool despawnWhen generates conditional despawn`() {
        val game =
            gbGame("PoolDespawnWhenTest") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }

                        despawnWhen { y isBelow 8 }
                    }

                start = scene("main") { every.frame { bullets.update() } }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("8") && code.contains("<"),
            "Should generate despawn condition for y < 8",
        )
    }

    @Test
    fun `pool onDespawn hook is called`() {
        val game =
            gbGame("PoolOnDespawnTest") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }

                        onDespawn { hide() }
                    }

                start = scene("main") { every.frame { bullets.update() } }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("bullet_hide") || code.contains("bullet_despawn"),
            "Should call hide in despawn",
        )
    }

    // =========================================================================
    // POOL FULL BEHAVIOR
    // =========================================================================

    @Test
    fun `pool hasSpace condition is generated`() {
        val game =
            gbGame("PoolHasSpaceTest") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }
                    }

                start =
                    scene("main") { every.frame { whenever(bullets.hasSpace) { bullets.spawn() } } }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("bullet_pool_count") && code.contains("BULLET_POOL_SIZE"),
            "Should check if pool has space",
        )
    }

    @Test
    fun `pool isFull condition is generated`() {
        val game =
            gbGame("PoolIsFullTest") {
                var poolFull by u8Var(0)

                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }
                    }

                start =
                    scene("main") { every.frame { whenever(bullets.isFull) { poolFull set 1 } } }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("bullet_pool_count") && code.contains("BULLET_POOL_SIZE"),
            "Should check if pool is full",
        )
    }

    @Test
    fun `pool activeCount is accessible`() {
        val game =
            gbGame("PoolActiveCountTest") {
                var count by u8Var(0)

                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }
                    }

                start = scene("main") { every.frame { count set bullets.activeCount } }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("bullet_pool_count"), "Should reference pool count")
    }

    // =========================================================================
    // TRYSPAWN/ORELSE PATTERN
    // =========================================================================

    @Test
    fun `trySpawn orElse generates conditional spawn`() {
        val game =
            gbGame("TrySpawnTest") {
                var spawnFailed by u8Var(0)

                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }
                    }

                start =
                    scene("main") {
                        every.frame { bullets.trySpawn { x set 80 } orElse { spawnFailed set 1 } }
                    }
            }

        val code = game.compileForTest()

        // Should have both spawn attempt and else block
        assertTrue(code.contains("bullet"), "Should reference bullet pool")
        assertTrue(code.contains("spawnFailed"), "Should reference spawnFailed variable")
    }

    @Test
    fun `trySpawn orElse with complex else block`() {
        val game =
            gbGame("TrySpawnComplexElseTest") {
                var errorCount by u8Var(0)
                var lastError by u8Var(0)

                val bullets =
                    pool("bullet", size = 4) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }
                    }

                start =
                    scene("main") {
                        every.frame {
                            bullets.trySpawn {
                                x set 80
                                y set 72
                            } orElse
                                {
                                    errorCount += 1
                                    lastError set 1
                                }
                        }
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("errorCount"), "Should reference errorCount")
        assertTrue(code.contains("lastError"), "Should reference lastError")
    }

    // =========================================================================
    // POOL LIFECYCLE HOOKS
    // =========================================================================

    @Test
    fun `pool onSpawn hook is called`() {
        val game =
            gbGame("PoolOnSpawnTest") {
                // Capture AnimationRef for type-safe usage
                lateinit var flyAnim: AnimationRef

                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) {
                            size = 4 x 4
                            animations { flyAnim = "fly" plays (frames(0, 1) every 4.frames) }
                        }

                        onSpawn { play(flyAnim) }
                    }

                start =
                    scene("main") {
                        enter { bullets.spawn() }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("bullet_spawn"), "Should have spawn function")
    }

    @Test
    fun `pool onFrame hook is called`() {
        val game =
            gbGame("PoolOnFrameTest") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        velocity(0, -4)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }

                        onFrame { y += velY }
                    }

                start = scene("main") { every.frame { bullets.update() } }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("bullet_update"), "Should have update function")
    }

    // =========================================================================
    // POOL ENTITY VAR OPERATORS
    // =========================================================================

    @Test
    fun `pool entity var operators generate correct IR`() {
        val game =
            gbGame("PoolEntityVarOpsTest") {
                val particle =
                    pool("particle", size = 8) {
                        position(0, 0)
                        velocity(0, 0)

                        onSpawn {
                            // Test set with Int
                            x set 50
                            y set 60

                            // Test set with Expr
                            x set (y + 10)

                            // Test addAssign with Int
                            x addAssign 5

                            // Test addAssign with Expr
                            y addAssign velX

                            // Test subAssign with Int
                            x subAssign 3

                            // Test subAssign with Expr
                            y subAssign velY
                        }

                        onFrame {
                            // Test plusAssign with Int
                            x += 1

                            // Test plusAssign with Expr
                            y += velY

                            // Test minusAssign with Int
                            x -= 2

                            // Test minusAssign with Expr
                            y -= velX

                            // Test timesAssign with Int
                            x *= 2

                            // Test timesAssign with Expr
                            y *= velY

                            // Test divAssign with Int
                            x /= 2

                            // Test divAssign with Expr
                            y /= velX

                            // Test remAssign with Int
                            x %= 10

                            // Test remAssign with Expr
                            y %= velY
                        }
                    }

                start =
                    scene("main") {
                        enter { particle.spawn() }
                        every.frame { particle.update() }
                    }
            }

        val code = game.compileForTest()

        // Verify set operations with array indexing
        assertTrue(
            code.contains("particle_x[_particle_i] = 50"),
            "Should generate array-indexed set for x",
        )
        assertTrue(
            code.contains("particle_y[_particle_i] = 60"),
            "Should generate array-indexed set for y",
        )

        // Verify set with expr generates array indexing
        assertTrue(
            code.contains("particle_x[_particle_i] = particle_y[_particle_i] + 10"),
            "Should generate array-indexed set with expr",
        )

        // Verify addAssign generates array indexing with addition
        assertTrue(
            code.contains("particle_x[_particle_i] = particle_x[_particle_i] + 5"),
            "Should generate addAssign with Int",
        )
        assertTrue(
            code.contains(
                "particle_y[_particle_i] = particle_y[_particle_i] + particle_vel_x[_particle_i]"
            ),
            "Should generate addAssign with Expr",
        )

        // Verify subAssign generates array indexing with subtraction
        assertTrue(
            code.contains("particle_x[_particle_i] = particle_x[_particle_i] - 3"),
            "Should generate subAssign with Int",
        )
        assertTrue(
            code.contains(
                "particle_y[_particle_i] = particle_y[_particle_i] - particle_vel_y[_particle_i]"
            ),
            "Should generate subAssign with Expr",
        )

        // Verify plusAssign generates array indexing
        assertTrue(
            code.contains("particle_x[_particle_i] = particle_x[_particle_i] + 1"),
            "Should generate plusAssign with Int",
        )

        // Verify minusAssign generates array indexing
        assertTrue(
            code.contains("particle_x[_particle_i] = particle_x[_particle_i] - 2"),
            "Should generate minusAssign with Int",
        )

        // Verify timesAssign generates array indexing (x * 2 -> x << 1 via strength reduction)
        assertTrue(
            code.contains("particle_x[_particle_i] = particle_x[_particle_i] << 1"),
            "Should generate timesAssign with Int (strength reduced to shift)",
        )
        assertTrue(
            code.contains(
                "particle_y[_particle_i] = particle_y[_particle_i] * particle_vel_y[_particle_i]"
            ),
            "Should generate timesAssign with Expr",
        )

        // Verify divAssign generates array indexing (x / 2 -> x >> 1 via strength reduction)
        assertTrue(
            code.contains("particle_x[_particle_i] = particle_x[_particle_i] >> 1"),
            "Should generate divAssign with Int (strength reduced to shift)",
        )
        assertTrue(
            code.contains(
                "particle_y[_particle_i] = particle_y[_particle_i] / particle_vel_x[_particle_i]"
            ),
            "Should generate divAssign with Expr",
        )

        // Verify remAssign generates array indexing
        assertTrue(
            code.contains("particle_x[_particle_i] = particle_x[_particle_i] % 10"),
            "Should generate remAssign with Int",
        )
        assertTrue(
            code.contains(
                "particle_y[_particle_i] = particle_y[_particle_i] % particle_vel_y[_particle_i]"
            ),
            "Should generate remAssign with Expr",
        )
    }

    // =========================================================================
    // POOL STATE FIELDS
    // =========================================================================

    @Test
    fun `pool with custom state fields`() {
        val game =
            gbGame("PoolStateFieldsTest") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }

                        state {
                            val timer by u8Var(60)
                            val damage by u8Var(10)
                        }

                        onFrame { this["timer"] -= 1 }

                        despawnWhen { this["timer"] isEqualTo 0 }
                    }

                start = scene("main") { every.frame { bullets.update() } }
            }

        assertEquals(2, game.pools[0].stateFields.size, "Should have 2 state fields")
        assertTrue(game.pools[0].stateFields.any { it.name == "timer" }, "Should have timer field")
        assertTrue(
            game.pools[0].stateFields.any { it.name == "damage" },
            "Should have damage field",
        )
    }

    @Test
    fun `pool state field default values`() {
        val game =
            gbGame("PoolStateDefaultsTest") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)

                        state {
                            val health by u8Var(100)
                            val speed by u8Var(5)
                        }
                    }

                start = scene("main") { every.frame {} }
            }

        val healthField = game.pools[0].stateFields.find { it.name == "health" }
        val speedField = game.pools[0].stateFields.find { it.name == "speed" }

        assertNotNull(healthField, "Should have health field")
        assertNotNull(speedField, "Should have speed field")
        assertEquals(100, healthField.defaultValue, "Health default should be 100")
        assertEquals(5, speedField.defaultValue, "Speed default should be 5")
    }

    // =========================================================================
    // POOL ITERATION
    // =========================================================================

    @Test
    fun `pool forEachActive generates iteration`() {
        val game =
            gbGame("PoolForEachTest") {
                val player by entity { position(80, 72) }

                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }
                    }

                start =
                    scene("main") {
                        every.frame {
                            bullets.forEachActive { whenever(collidesWith(player)) { despawn() } }
                        }
                    }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("for") && code.contains("BULLET_POOL_SIZE"),
            "Should iterate over pool",
        )
    }

    // =========================================================================
    // POOL WITH SPRITE AND ANIMATIONS
    // =========================================================================

    @Test
    fun `pool with sprite and animations`() {
        val game =
            gbGame("PoolSpriteAnimTest") {
                // Capture AnimationRef for type-safe usage
                lateinit var explodeAnim: AnimationRef

                val explosions =
                    pool("explosion", size = 4) {
                        position(0, 0)
                        sprite(SpriteAsset("explosion.png")) {
                            size = 16 x 16
                            animations {
                                explodeAnim = "explode" plays (frames(0, 1, 2, 3) every 4.frames)
                            }
                        }

                        onSpawn { play(explodeAnim, loop = false) }

                        despawnWhen { isAnimationComplete }
                    }

                start = scene("main") { every.frame { explosions.update() } }
            }

        assertEquals("explosion.png", game.pools[0].spriteAsset, "Should have sprite asset")
        assertEquals(16, game.pools[0].spriteWidth, "Sprite width should be 16")
        assertEquals(16, game.pools[0].spriteHeight, "Sprite height should be 16")
    }

    // =========================================================================
    // POOL COLLISION
    // =========================================================================

    @Test
    fun `pool entity collision with entity`() {
        val game =
            gbGame("PoolCollisionTest") {
                val player by entity {
                    position(80, 72)
                    sprite(SpriteAsset("player.png")) { size = 8 x 16 }
                }

                var hit by u8Var(0)

                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }
                    }

                start =
                    scene("main") {
                        every.frame {
                            bullets.forEachActive {
                                whenever(collidesWith(player)) {
                                    hit set 1
                                    despawn()
                                }
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("player"), "Should reference player")
        assertTrue(code.contains("bullet"), "Should reference bullet pool")
    }

    // =========================================================================
    // POOL ANIMATION CODE GENERATION
    // =========================================================================

    @Test
    fun `pool animation generates animation constants and arrays`() {
        val game =
            gbGame("PoolAnimationCodegenTest") {
                lateinit var spinAnim: AnimationRef

                val particles =
                    pool("particle", size = 4) {
                        position(0, 0)
                        sprite(SpriteAsset("particle.png")) {
                            size = 8 x 8
                            animations {
                                spinAnim = "spin" plays (frames(0, 1, 2, 3) every 4.frames)
                            }
                        }

                        onSpawn { play(spinAnim) }
                    }

                start = scene("main") { every.frame { particles.update() } }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("ANIM_PARTICLE_SPIN"), "Should generate animation constant")
        assertTrue(
            code.contains("particle_anim[PARTICLE_POOL_SIZE]"),
            "Should generate animation index array",
        )
        assertTrue(
            code.contains("particle_frame[PARTICLE_POOL_SIZE]"),
            "Should generate frame array",
        )
        assertTrue(
            code.contains("particle_timer[PARTICLE_POOL_SIZE]"),
            "Should generate timer array",
        )
    }

    @Test
    fun `pool animation play generates correct indexed assignment`() {
        val game =
            gbGame("PoolAnimPlayTest") {
                lateinit var idleAnim: AnimationRef
                lateinit var walkAnim: AnimationRef

                val enemies =
                    pool("enemy", size = 4) {
                        position(0, 0)
                        sprite(SpriteAsset("enemy.png")) {
                            size = 8 x 8
                            animations {
                                idleAnim = "idle" plays (frames(0, 1) every 8.frames)
                                walkAnim = "walk" plays (frames(2, 3, 4, 5) every 4.frames)
                            }
                        }

                        onSpawn { play(idleAnim) }
                        onFrame { play(walkAnim) }
                    }

                start = scene("main") { every.frame { enemies.update() } }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("ANIM_ENEMY_IDLE"), "Should have idle animation constant")
        assertTrue(code.contains("ANIM_ENEMY_WALK"), "Should have walk animation constant")
        assertTrue(
            code.contains("enemy_anim[_enemy_i]"),
            "Should use indexed access for animation state",
        )
    }

    @Test
    fun `pool animation complete flag generates correct code`() {
        val game =
            gbGame("PoolAnimCompleteTest") {
                lateinit var explodeAnim: AnimationRef

                val effects =
                    pool("effect", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("effect.png")) {
                            size = 16 x 16
                            animations {
                                explodeAnim = "explode" plays (frames(0, 1, 2, 3) every 2.frames)
                            }
                        }

                        onSpawn { play(explodeAnim, loop = false) }
                        despawnWhen { isAnimationComplete }
                    }

                start = scene("main") { every.frame { effects.update() } }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("effect_anim_complete[EFFECT_POOL_SIZE]"),
            "Should generate animation complete flag array",
        )
        // Check that animation complete flag is used somewhere (the exact index var name may vary)
        assertTrue(
            code.contains("effect_anim_complete["),
            "Should use animation complete flag array",
        )
    }

    @Test
    fun `pool without animations does not generate animation arrays`() {
        val game =
            gbGame("PoolNoAnimTest") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }
                    }

                start = scene("main") { every.frame { bullets.update() } }
            }

        val code = game.compileForTest()

        assertFalse(
            code.contains("bullet_anim["),
            "Should not generate animation array for pool without animations",
        )
        assertFalse(
            code.contains("bullet_frame["),
            "Should not generate frame array for pool without animations",
        )
    }

    // =========================================================================
    // ADDITIONAL POOL EDGE CASES
    // =========================================================================

    @Test
    fun `pool despawnWhere generates conditional bulk despawn`() {
        val game =
            gbGame("PoolDespawnWhereTest") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }
                    }

                start =
                    scene("main") {
                        enter { bullets.despawnWhere { x isAbove 160 } }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("160"), "Should check x > 160 condition")
        assertTrue(code.contains("bullet") && code.contains("despawn"), "Should despawn bullets")
    }

    @Test
    fun `pool isPlaying generates animation check`() {
        val game =
            gbGame("PoolIsPlayingTest") {
                lateinit var walkAnim: AnimationRef

                val enemies =
                    pool("enemy", size = 4) {
                        position(0, 0)
                        sprite(SpriteAsset("enemy.png")) {
                            size = 8 x 8
                            animations { walkAnim = "walk" plays (frames(0, 1) every 4.frames) }
                        }

                        onFrame { whenever(isPlaying("walk")) { x += 1 } }
                    }

                start = scene("main") { every.frame { enemies.update() } }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("ANIM_ENEMY_WALK") || code.contains("enemy_anim"),
            "Should check current animation",
        )
    }

    @Test
    fun `pool show and hide generate visibility code`() {
        val game =
            gbGame("PoolShowHideTest") {
                val particles =
                    pool("particle", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("particle.png")) { size = 4 x 4 }

                        onSpawn { show() }
                        onDespawn { hide() }
                    }

                start =
                    scene("main") {
                        enter { particles.spawn() }
                        every.frame { particles.update() }
                    }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("particle_visible") || code.contains("show"),
            "Should generate visibility code",
        )
    }

    @Test
    fun `pool state field with different types`() {
        val game =
            gbGame("PoolStateTypesTest") {
                val entities =
                    pool("entity", size = 4) {
                        position(0, 0)

                        state {
                            val byteField by u8Var(0)
                            val signedField by i8Var(-5)
                            val wordField by u16Var(1000)
                        }
                    }

                start = scene("main") { every.frame {} }
            }

        val fields = game.pools[0].stateFields

        assertEquals(3, fields.size, "Should have 3 state fields")

        val byteField = fields.find { it.name == "byteField" }
        val signedField = fields.find { it.name == "signedField" }
        val wordField = fields.find { it.name == "wordField" }

        assertNotNull(byteField, "Should have byteField")
        assertNotNull(signedField, "Should have signedField")
        assertNotNull(wordField, "Should have wordField")

        assertEquals(GBVar.VarType.U8, byteField.type, "byteField should be U8")
        assertEquals(GBVar.VarType.I8, signedField.type, "signedField should be I8")
        assertEquals(GBVar.VarType.U16, wordField.type, "wordField should be U16")
    }

    @Test
    fun `pool field accessor generates indexed access`() {
        val game =
            gbGame("PoolFieldAccessTest") {
                val entities =
                    pool("entity", size = 4) {
                        position(0, 0)

                        state {
                            val health by u8Var(100)
                        }

                        onFrame {
                            this["health"] -= 1
                            whenever(this["health"] isEqualTo 0) { despawn() }
                        }
                    }

                start = scene("main") { every.frame { entities.update() } }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("entity_health["), "Should generate indexed access for health")
    }

    @Test
    fun `pool multiple spawn calls in scene`() {
        val game =
            gbGame("PoolMultiSpawnTest") {
                val bullets =
                    pool("bullet", size = 16) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) { size = 4 x 4 }
                    }

                start =
                    scene("main") {
                        enter {
                            bullets.spawnAt(10, 10)
                            bullets.spawnAt(20, 20)
                            bullets.spawnAt(30, 30)
                        }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("10") && code.contains("20") && code.contains("30"),
            "Should spawn at multiple positions",
        )
    }

    @Test
    fun `pool with hitbox generates collision code`() {
        val game =
            gbGame("PoolHitboxTest") {
                val player by entity {
                    position(80, 72)
                    sprite(SpriteAsset("player.png")) { size = 8 x 16 }
                }

                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        sprite(SpriteAsset("bullet.png")) {
                            size = 4 x 4
                            hitbox(0, 0, 4, 4)
                        }
                    }

                start =
                    scene("main") {
                        every.frame {
                            bullets.forEachActive { whenever(collidesWith(player)) { despawn() } }
                        }
                    }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("bullet") && code.contains("player"),
            "Should have collision check",
        )
    }

    @Test
    fun `pool velocity fields are accessible`() {
        val game =
            gbGame("PoolVelocityTest") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        velocity(2, -4)

                        onFrame {
                            x += velX
                            y += velY
                        }
                    }

                start = scene("main") { every.frame { bullets.update() } }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("bullet_vel_x") || code.contains("velX"), "Should have velocity X")
        assertTrue(code.contains("bullet_vel_y") || code.contains("velY"), "Should have velocity Y")
    }
}
