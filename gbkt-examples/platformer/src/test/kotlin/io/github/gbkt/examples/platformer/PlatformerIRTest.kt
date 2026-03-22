/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.platformer

import io.github.gbkt.core.ir.GbcTarget
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.VarType
import io.github.gbkt.genre.platformer.domain.PlatformType
import io.github.gbkt.genre.platformer.domain.PlatformerPhysicsConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * IR validation tests for the Platformer DMG game definition.
 *
 * Verifies:
 * - 3 scenes (title, gameplay, win), start scene is "title"
 * - 1 actor (player) with correct initial position and sprite
 * - 1 variable (lives, U8)
 * - 3 sound effects (jumpSfx, landSfx, winSfx)
 * - Physics system present with correct configuration
 * - Camera system present
 * - 3 platform definitions (ground, mid_platform, high_platform)
 * - 1 goal zone (exit) at position (112, 40)
 * - DMG target (no GBC config)
 */
class PlatformerIRTest {

    private val ir = platformer.build()

    @Test
    fun `has 3 scenes`() {
        assertEquals(3, ir.scenes.size)
    }

    @Test
    fun `start scene is title`() {
        assertEquals("title", ir.startScene)
    }

    @Test
    fun `scenes are title gameplay win`() {
        val sceneIds = ir.scenes.map { it.id }.toSet()
        assertTrue(sceneIds.contains("title"))
        assertTrue(sceneIds.contains("gameplay"))
        assertTrue(sceneIds.contains("win"))
    }

    @Test
    fun `has 1 actor`() {
        assertEquals(1, ir.actors.size)
    }

    @Test
    fun `has player actor`() {
        assertTrue(ir.actors.any { it.id == "player" }, "Expected 'player' actor")
    }

    @Test
    fun `player actor has correct initial position`() {
        assertEquals(PositionDef(20, 104), ir.actors.first { it.id == "player" }.position)
    }

    @Test
    fun `player actor has a sprite`() {
        assertNotNull(ir.actors.first { it.id == "player" }.sprite)
    }

    @Test
    fun `has 1 variable - lives`() {
        assertEquals(1, ir.variables.size)
        assertTrue(ir.variables.any { it.name == "lives" && it.type == VarType.U8 })
    }

    @Test
    fun `has 3 sound effects`() {
        assertEquals(3, ir.soundEffects.size)
    }

    @Test
    fun `has named sound effects jumpSfx landSfx winSfx`() {
        assertTrue(ir.soundEffects.any { it.id == "jumpSfx" }, "Expected 'jumpSfx' sound effect")
        assertTrue(ir.soundEffects.any { it.id == "landSfx" }, "Expected 'landSfx' sound effect")
        assertTrue(ir.soundEffects.any { it.id == "winSfx" }, "Expected 'winSfx' sound effect")
    }

    @Test
    fun `has physics system`() {
        val physics =
            ir.systems.filterIsInstance<GenericSystem>().firstOrNull {
                it.config["type"] == "platformer_physics"
            }
        assertNotNull(physics, "Expected platformer_physics system")
    }

    @Test
    fun `physics system has correct configuration`() {
        val physics =
            ir.systems.filterIsInstance<GenericSystem>().first {
                it.config["type"] == "platformer_physics"
            }
        val config = physics.config["physicsConfig"] as PlatformerPhysicsConfig
        assertEquals(2, config.gravity)
        assertEquals(8, config.jumpForce)
        assertEquals(12, config.terminalVelocity)
        assertEquals(6, config.coyoteFrames)
        assertEquals(8, config.jumpBufferFrames)
    }

    @Test
    fun `has camera system`() {
        val camera =
            ir.systems.filterIsInstance<GenericSystem>().firstOrNull {
                it.config["type"] == "platformer_camera"
            }
        assertNotNull(camera, "Expected platformer_camera system")
    }

    @Test
    fun `has 3 platform definitions`() {
        val platforms =
            ir.systems.filterIsInstance<GenericSystem>().filter {
                it.config["type"] == "platformer_platform"
            }
        assertEquals(3, platforms.size)
    }

    @Test
    fun `has ground platform as SOLID`() {
        val platforms =
            ir.systems.filterIsInstance<GenericSystem>().filter {
                it.config["type"] == "platformer_platform"
            }
        val ground = platforms.firstOrNull { it.id == "ground" }
        assertNotNull(ground, "Expected 'ground' platform")
        val def = ground.config["platform"] as io.github.gbkt.genre.platformer.domain.PlatformDef
        assertEquals(PlatformType.SOLID, def.type)
    }

    @Test
    fun `has one-way platforms`() {
        val platforms =
            ir.systems.filterIsInstance<GenericSystem>().filter {
                it.config["type"] == "platformer_platform"
            }
        val oneWay =
            platforms.filter {
                (it.config["platform"] as io.github.gbkt.genre.platformer.domain.PlatformDef)
                    .type == PlatformType.ONE_WAY
            }
        assertEquals(
            2,
            oneWay.size,
            "Expected 2 one-way platforms (mid_platform and high_platform)",
        )
    }

    @Test
    fun `has goal zone`() {
        val goal =
            ir.systems.filterIsInstance<GenericSystem>().firstOrNull {
                it.config["type"] == "platformer_goal"
            }
        assertNotNull(goal, "Expected platformer_goal system")
        assertEquals("exit", goal.id)
    }

    @Test
    fun `goal zone has correct position`() {
        val goal =
            ir.systems.filterIsInstance<GenericSystem>().first {
                it.config["type"] == "platformer_goal"
            }
        val def = goal.config["goalZone"] as io.github.gbkt.genre.platformer.domain.GoalZoneDef
        assertEquals(112, def.x)
        assertEquals(40, def.y)
    }

    @Test
    fun `is DMG target - no GBC config`() {
        assertEquals(GbcTarget.DMG, ir.config.gbcTarget)
    }

    @Test
    fun `title scene has enter ops`() {
        assertTrue(ir.scenes.first { it.id == "title" }.enterOps.isNotEmpty())
    }

    @Test
    fun `gameplay scene has frame ops`() {
        assertTrue(ir.scenes.first { it.id == "gameplay" }.frameOps.isNotEmpty())
    }

    @Test
    fun `win scene has enter ops`() {
        assertTrue(ir.scenes.first { it.id == "win" }.enterOps.isNotEmpty())
    }
}
