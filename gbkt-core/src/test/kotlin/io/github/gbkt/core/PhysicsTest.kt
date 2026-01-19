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

class PhysicsTest {

    @Test
    fun `physics world DSL creates PhysicsWorld`() {
        val game =
            gbGame("PhysicsTest") {
                val physicsWorld = physics {
                    gravity = 0.5f
                    friction = 0.9f
                    bounce = 0.3f
                }

                val testScene =
                    scene("test") {
                        enter { physicsWorld.collide("player", "enemy") }
                        every.frame { physicsWorld.update() }
                    }

                start = testScene
            }

        assertNotNull(game.physicsWorld, "Physics world should be created")
        val physicsWorld = game.physicsWorld
        assertEquals(0.5f, physicsWorld.config.gravity)
        assertEquals(0.9f, physicsWorld.config.friction)
        assertEquals(0.3f, physicsWorld.config.bounce)
    }

    @Test
    fun `physics world generates C code`() {
        val game =
            gbGame("PhysicsCodegenTest") {
                val physicsWorld = physics {
                    gravity = 0.5f
                    friction = 0.9f
                    bounce = 0.3f
                }

                val player by entity {
                    position(80, 72)
                    velocity(0, 0).physics {
                        gravity = 0.0f // Override global gravity
                        friction = 1.0f
                        maxVelocity = 4 to 8
                    }
                }

                val testScene = scene("test") { every.frame { physicsWorld.update() } }

                start = testScene
            }

        val code = game.compileForTest()

        // Check for physics world constants
        assertTrue(code.contains("PHYSICS_GRAVITY"), "Should have PHYSICS_GRAVITY constant")
        assertTrue(code.contains("PHYSICS_FRICTION"), "Should have PHYSICS_FRICTION constant")
        assertTrue(code.contains("PHYSICS_BOUNCE"), "Should have PHYSICS_BOUNCE constant")

        // Check for physics world update function
        assertTrue(
            code.contains("_physics_world_update"),
            "Should have _physics_world_update function",
        )

        // Check that gravity 0.5 becomes 128 in fixed-point (0.5 * 256)
        assertTrue(code.contains("PHYSICS_GRAVITY 128"), "Gravity 0.5 should be 128 in fixed-point")

        // Check that friction 0.9 becomes ~230 in fixed-point (0.9 * 256 = 230.4)
        assertTrue(
            code.contains("PHYSICS_FRICTION 230") || code.contains("PHYSICS_FRICTION 231"),
            "Friction 0.9 should be ~230 in fixed-point",
        )

        // Check that bounce 0.3 becomes ~77 in fixed-point (0.3 * 256 = 76.8)
        assertTrue(
            code.contains("PHYSICS_BOUNCE 77") || code.contains("PHYSICS_BOUNCE 76"),
            "Bounce 0.3 should be ~77 in fixed-point",
        )
    }

    @Test
    fun `physics world applies to entities with physics component`() {
        val game =
            gbGame("PhysicsEntitiesTest") {
                val physicsWorld = physics { gravity = 0.5f }

                val player by entity {
                    position(80, 72)
                    velocity(0, 0).physics { maxVelocity = 4 to 8 }
                }

                val enemy by entity {
                    position(100, 72)
                    velocity(0, 0).physics { maxVelocity = 2 to 4 }
                }

                val testScene =
                    scene("test") {
                        every.frame {
                            physicsWorld.update()
                            // Access entities to ensure they're registered
                            player.x // Force registration
                            enemy.x // Force registration
                        }
                    }

                start = testScene
            }

        // Verify entities are registered with physics
        assertEquals(2, game.entities.size, "Should have 2 entities")
        assertTrue(
            game.entities.any { it.name == "player" && it.hasPhysics },
            "Player should have physics",
        )
        assertTrue(
            game.entities.any { it.name == "enemy" && it.hasPhysics },
            "Enemy should have physics",
        )

        val code = game.compileForTest()

        // Should have physics world update function
        assertTrue(
            code.contains("_physics_world_update"),
            "Should have physics world update function",
        )

        // Should use global gravity from physics world (PHYSICS_GRAVITY)
        assertTrue(code.contains("PHYSICS_GRAVITY"), "Should define PHYSICS_GRAVITY constant")

        // Verify entities with physics are processed
        // The physics update function should contain comments like "// Physics update for player"
        val physicsIndex = code.indexOf("_physics_world_update")
        assertTrue(physicsIndex >= 0, "Should find _physics_world_update function")

        // Extract a reasonable section of the physics function (3000 chars should be enough)
        val physicsSection =
            code.substring(physicsIndex, (physicsIndex + 3000).coerceAtMost(code.length))

        // Check for entity-specific physics update blocks
        // The codegen generates: "// Physics update for player" and similar comments
        assertTrue(
            physicsSection.contains("Physics update for player") ||
                physicsSection.contains("_player_vel") ||
                physicsSection.contains("PLAYER_"),
            "Physics world update should process player entity. Section preview: ${physicsSection.take(800)}",
        )
        assertTrue(
            physicsSection.contains("Physics update for enemy") ||
                physicsSection.contains("_enemy_vel") ||
                physicsSection.contains("ENEMY_"),
            "Physics world update should process enemy entity. Section preview: ${physicsSection.take(800)}",
        )
    }

    @Test
    fun `collision response registration`() {
        val game =
            gbGame("CollisionResponseTest") {
                val physicsWorld = physics { bounce = 0.5f }

                val playerTag = tag("player")
                val enemyTag = tag("enemy")

                val player by entity {
                    position(80, 72)
                    tag(playerTag)
                    velocity(0, 0).physics { maxVelocity = 4 to 8 }
                }

                val enemy by entity {
                    position(100, 72)
                    tag(enemyTag)
                    velocity(0, 0).physics { maxVelocity = 2 to 4 }
                }

                val testScene =
                    scene("test") {
                        enter {
                            physicsWorld.collide("player", "enemy")
                            physicsWorld.collide(playerTag, enemyTag) // Type-safe version
                        }
                        every.frame { physicsWorld.update() }
                    }

                start = testScene
            }

        // Check that collision pairs are registered
        val physicsWorld = game.physicsWorld!!
        assertEquals(2, physicsWorld.collisionPairs.size, "Should have 2 collision pairs")

        val code = game.compileForTest()

        // Should generate collision response code
        assertTrue(
            code.contains("Collision response: player <-> enemy"),
            "Should generate collision response code",
        )
    }

    @Test
    fun `physics world update emits IRPhysicsWorldUpdate`() {
        val game =
            gbGame("IRTest") {
                val physicsWorld = physics { gravity = 0.5f }

                val testScene = scene("test") { every.frame { physicsWorld.update() } }

                start = testScene
            }

        // Check that the scene contains IRPhysicsWorldUpdate
        val scene = game.scenes["test"]
        assertNotNull(scene, "Scene should exist")
        assertTrue(
            scene.onFrame.any { it is IRPhysicsWorldUpdate },
            "Should emit IRPhysicsWorldUpdate in scene",
        )
    }

    @Test
    fun `collision response emits IRCollisionResponse`() {
        val game =
            gbGame("CollisionIRTest") {
                val physicsWorld = physics { bounce = 0.3f }

                val testScene =
                    scene("test") {
                        enter { physicsWorld.collide("player", "enemy") }
                        every.frame { physicsWorld.update() }
                    }

                start = testScene
            }

        // Check that the scene contains IRCollisionResponse
        val scene = game.scenes["test"]
        assertNotNull(scene, "Scene should exist")
        assertTrue(
            scene.onEnter.any { it is IRCollisionResponse },
            "Should emit IRCollisionResponse in scene",
        )

        val stmt = scene.onEnter.first { it is IRCollisionResponse } as IRCollisionResponse
        assertEquals("player", stmt.tag1)
        assertEquals("enemy", stmt.tag2)
    }

    @Test
    fun `physics world with no entities generates empty update`() {
        val game =
            gbGame("EmptyPhysicsTest") {
                val physicsWorld = physics { gravity = 0.5f }

                val testScene = scene("test") { every.frame { physicsWorld.update() } }

                start = testScene
            }

        val code = game.compileForTest()

        // Should still generate the function, but with no entity updates
        assertTrue(code.contains("_physics_world_update"), "Should generate update function")
        assertTrue(
            code.contains("No entities with physics component") ||
                !code.contains("Physics update for"),
            "Should not have entity-specific physics code",
        )
    }

    @Test
    fun `physics world default values`() {
        val game =
            gbGame("DefaultPhysicsTest") {
                val physicsWorld = physics {}

                val testScene = scene("test") { every.frame { physicsWorld.update() } }

                start = testScene
            }

        val physicsWorld = game.physicsWorld!!
        assertEquals(0.5f, physicsWorld.config.gravity, "Default gravity should be 0.5")
        assertEquals(0.9f, physicsWorld.config.friction, "Default friction should be 0.9")
        assertEquals(0.3f, physicsWorld.config.bounce, "Default bounce should be 0.3")
    }
}
