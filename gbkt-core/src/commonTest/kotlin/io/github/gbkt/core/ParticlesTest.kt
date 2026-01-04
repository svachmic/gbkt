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

/** Tests for the particle system DSL - visual effects with automatic lifetime management. */
class ParticlesTest {

    @Test
    fun `particles declaration creates pool with lifetime field`() {
        val game =
            gbGame("test") {
                val sparks =
                    particles("spark") {
                        sprite(SpriteAsset("spark.png")) { size = 2 x 2 }
                        count = 8
                        lifetime = 15
                    }

                start = scene("main") { every.frame { sparks.update() } }
            }

        val code = game.compileForTest()

        // Should create a pool named "spark"
        assertTrue(code.contains("SPARK_POOL_SIZE 8"), "Should define pool size")
        assertTrue(code.contains("spark_spawn"), "Should generate spawn function")
        assertTrue(code.contains("spark_update"), "Should generate update function")
        assertTrue(code.contains("spark_despawn"), "Should generate despawn function")

        // Should have lifetime state field
        assertTrue(
            code.contains("spark") && code.contains("_lifetime"),
            "Should have lifetime field"
        )
    }

    @Test
    fun `particles lifetime is initialized on spawn`() {
        val game =
            gbGame("test") {
                val sparks =
                    particles("spark") {
                        count = 4
                        lifetime = 30
                    }

                start = scene("main") { enter { sparks.emit(80, 72) } }
            }

        val code = game.compileForTest()

        // Should initialize lifetime to 30 on spawn
        assertTrue(
            code.contains("_lifetime") && code.contains("30"),
            "Should initialize lifetime on spawn"
        )
    }

    @Test
    fun `particles lifetime decrements each frame`() {
        val game =
            gbGame("test") {
                val sparks =
                    particles("spark") {
                        count = 4
                        lifetime = 20
                    }

                start = scene("main") { every.frame { sparks.update() } }
            }

        val code = game.compileForTest()

        // Should decrement lifetime in onFrame
        assertTrue(
            code.contains("_lifetime") &&
                (code.contains("- 1") || code.contains("-= 1") || code.contains("--")),
            "Should decrement lifetime each frame"
        )
    }

    @Test
    fun `particles auto-despawn when lifetime reaches zero`() {
        val game =
            gbGame("test") {
                val sparks =
                    particles("spark") {
                        count = 4
                        lifetime = 10
                    }

                start = scene("main") { every.frame { sparks.update() } }
            }

        val code = game.compileForTest()

        // Should have despawn condition when lifetime == 0
        assertTrue(
            code.contains("_lifetime") && code.contains("== 0"),
            "Should auto-despawn when lifetime reaches 0"
        )
    }

    @Test
    fun `particles emit spawns at position`() {
        val game =
            gbGame("test") {
                val sparks =
                    particles("spark") {
                        count = 8
                        lifetime = 15
                    }

                start = scene("main") { enter { sparks.emit(80, 72) } }
            }

        val code = game.compileForTest()

        // Should spawn at the given position
        assertTrue(code.contains("spark_spawn"), "Should call spawn")
        assertTrue(code.contains("80") && code.contains("72"), "Should set position to 80, 72")
    }

    @Test
    fun `particles burst spawns multiple particles`() {
        val game =
            gbGame("test") {
                val sparks =
                    particles("spark") {
                        count = 8
                        lifetime = 15
                    }

                start = scene("main") { enter { sparks.burst(50, 60, count = 3) } }
            }

        val code = game.compileForTest()

        // Should have multiple spawn calls (3 particles in burst)
        val spawnCount = "spark_spawn".toRegex().findAll(code).count()
        assertTrue(spawnCount >= 3, "Should spawn 3 particles, found $spawnCount spawns")
    }

    @Test
    fun `particles with velocity generates velocity fields`() {
        val game =
            gbGame("test") {
                val sparks =
                    particles("spark") {
                        count = 4
                        lifetime = 20
                        velocity(0, -2) // Move upward

                        onFrame {
                            x += velX
                            y += velY
                        }
                    }

                start = scene("main") { every.frame { sparks.update() } }
            }

        val code = game.compileForTest()

        // Should have velocity variables
        assertTrue(code.contains("spark") && code.contains("vel_x"), "Should have velocity X field")
        assertTrue(code.contains("spark") && code.contains("vel_y"), "Should have velocity Y field")
    }

    @Test
    fun `particles onSpawn hook is called`() {
        val game =
            gbGame("test") {
                var spawnCount by u8Var(0)

                val sparks =
                    particles("spark") {
                        count = 4
                        lifetime = 10

                        onSpawn {
                            // Increment a counter when spawning
                        }
                    }

                start = scene("main") { enter { sparks.emit(80, 72) } }
            }

        val code = game.compileForTest()

        // Should have onSpawn section
        assertTrue(
            code.contains("onSpawn") || code.contains("spark_spawn"),
            "Should have spawn logic"
        )
    }

    @Test
    fun `particles onFrame hook is called`() {
        val game =
            gbGame("test") {
                val sparks =
                    particles("spark") {
                        count = 4
                        lifetime = 10
                        velocity(0, 0)

                        onFrame {
                            y -= 1 // Move up
                        }
                    }

                start = scene("main") { every.frame { sparks.update() } }
            }

        val code = game.compileForTest()

        // Should have the movement logic
        assertTrue(
            code.contains("spark") && code.contains("y"),
            "Should update Y position in onFrame"
        )
    }

    @Test
    fun `particles onDespawn hook is called`() {
        val game =
            gbGame("test") {
                val sparks =
                    particles("spark") {
                        count = 4
                        lifetime = 10

                        onDespawn { hide() }
                    }

                start = scene("main") { every.frame { sparks.update() } }
            }

        val code = game.compileForTest()

        // Should call hide on despawn
        assertTrue(
            code.contains("spark_despawn") && code.contains("spark_hide"),
            "Should call hide in despawn"
        )
    }

    @Test
    fun `particles activeCount returns pool count`() {
        val game =
            gbGame("test") {
                var myCount by u8Var(0)
                val sparks =
                    particles("spark") {
                        count = 8
                        lifetime = 15
                    }

                start = scene("main") { every.frame { myCount set sparks.activeCount } }
            }

        val code = game.compileForTest()

        // Should access pool count
        assertTrue(code.contains("spark_pool_count"), "Should access spark_pool_count")
    }

    @Test
    fun `particles despawnAll clears pool`() {
        val game =
            gbGame("test") {
                val sparks =
                    particles("spark") {
                        count = 8
                        lifetime = 15
                    }

                start = scene("main") { enter { sparks.despawnAll() } }
            }

        val code = game.compileForTest()

        // Should call despawn_all
        assertTrue(code.contains("spark_despawn_all"), "Should call spark_despawn_all")
    }

    @Test
    fun `particles with FrameTiming lifetime syntax`() {
        val game =
            gbGame("test") {
                val sparks =
                    particles("spark") {
                        count = 4
                        lifetimeFrames = 25.frames // Using FrameTiming syntax
                    }

                start = scene("main") { every.frame { sparks.update() } }
            }

        // Should work the same as integer syntax
        assertEquals(25, game.particleSystems.first().lifetime, "Lifetime should be 25 frames")
    }

    @Test
    fun `particles registers in game particleSystems list`() {
        val game =
            gbGame("test") {
                val sparks =
                    particles("spark") {
                        count = 4
                        lifetime = 10
                    }

                val smoke =
                    particles("smoke") {
                        count = 6
                        lifetime = 30
                    }

                start = scene("main") {}
            }

        assertEquals(2, game.particleSystems.size, "Should have 2 particle systems")
        assertEquals("spark", game.particleSystems[0].name, "First should be 'spark'")
        assertEquals("smoke", game.particleSystems[1].name, "Second should be 'smoke'")
    }

    @Test
    fun `particles pool is registered for code generation`() {
        val game =
            gbGame("test") {
                val sparks =
                    particles("spark") {
                        count = 8
                        lifetime = 20
                    }

                start = scene("main") {}
            }

        // Particle system should create an underlying pool
        assertEquals(1, game.pools.size, "Should have 1 pool from particle system")
        assertEquals("spark", game.pools[0].name, "Pool should be named 'spark'")
        assertEquals(8, game.pools[0].size, "Pool size should be 8")
    }

    @Test
    fun `particles default values are sensible`() {
        val game =
            gbGame("test") {
                val sparks =
                    particles("spark") {
                        // Only set name, use defaults
                    }

                start = scene("main") {}
            }

        val ps = game.particleSystems.first()
        assertEquals(8, ps.count, "Default count should be 8")
        assertEquals(30, ps.lifetime, "Default lifetime should be 30 frames")
    }

    @Test
    fun `particles with custom state fields`() {
        val game =
            gbGame("test") {
                val sparks =
                    particles("spark") {
                        count = 4
                        lifetime = 20

                        state {
                            val scale by u8Var(1)
                            val color by u8Var(0)
                        }
                    }

                start = scene("main") {}
            }

        val pool = game.pools.first()
        // Should have _lifetime plus 2 custom fields
        assertEquals(
            3,
            pool.stateFields.size,
            "Should have _lifetime + scale + color = 3 state fields"
        )
        assertTrue(pool.stateFields.any { it.name == "_lifetime" }, "Should have _lifetime")
        assertTrue(pool.stateFields.any { it.name == "scale" }, "Should have scale")
        assertTrue(pool.stateFields.any { it.name == "color" }, "Should have color")
    }

    @Test
    fun `particles forEachActive iterates over particles`() {
        val game =
            gbGame("test") {
                var total by u8Var(0)
                val sparks =
                    particles("spark") {
                        count = 8
                        lifetime = 20
                    }

                start =
                    scene("main") {
                        every.frame {
                            sparks.forEachActive {
                                // Access each particle
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        // Should generate foreach loop
        assertTrue(
            code.contains("for") && code.contains("SPARK_POOL_SIZE"),
            "Should iterate over pool"
        )
    }

    @Test
    fun `particles hasSpace and isFull conditions work`() {
        val game =
            gbGame("test") {
                val sparks =
                    particles("spark") {
                        count = 4
                        lifetime = 10
                    }

                start =
                    scene("main") {
                        every.frame { whenever(sparks.hasSpace) { sparks.emit(80, 72) } }
                    }
            }

        val code = game.compileForTest()

        // Should check pool count vs size
        assertTrue(
            code.contains("spark_pool_count") && code.contains("SPARK_POOL_SIZE"),
            "Should check if pool has space"
        )
    }

    @Test
    fun `particles emit with entity position`() {
        val game =
            gbGame("test") {
                val player by entity { position(80, 72) }

                val sparks =
                    particles("spark") {
                        count = 8
                        lifetime = 15
                    }

                start = scene("main") { enter { sparks.emit(player.x, player.y) } }
            }

        val code = game.compileForTest()

        // Should reference player position (entity vars use entityName_x format)
        assertTrue(
            code.contains("player_x") && code.contains("player_y"),
            "Should use player position for emit"
        )
    }
}
