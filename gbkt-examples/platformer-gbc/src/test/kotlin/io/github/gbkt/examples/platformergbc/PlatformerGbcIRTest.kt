/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.platformergbc

import io.github.gbkt.core.ir.GbcTarget
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.genre.platformer.domain.PlatformType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * IR validation tests for the Platformer GBC game definition.
 *
 * Verifies GBC-specific configuration and structural parity with the DMG variant:
 * - Same scene count (3) as DMG
 * - Start scene is "title"
 * - GBC_COMPATIBLE target configured
 * - Physics system present
 * - 3 platform definitions
 * - 3 sound effects
 * - Same actor count (1) as DMG
 * - Player actor present with correct position
 */
class PlatformerGbcIRTest {

    private val ir = platformerGbc.build()

    @Test
    fun `has same scene count as DMG - 3 scenes`() {
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
    fun `has GBC_COMPATIBLE config`() {
        assertEquals(
            GbcTarget.GBC_COMPATIBLE,
            ir.config.gbcTarget,
            "GBC variant must have GBC_COMPATIBLE target",
        )
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
    fun `has 3 platform definitions - same as DMG`() {
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
    fun `has 3 sound effects - same as DMG`() {
        assertEquals(3, ir.soundEffects.size)
    }

    @Test
    fun `has named sound effects jumpSfx landSfx winSfx`() {
        assertTrue(ir.soundEffects.any { it.id == "jumpSfx" }, "Expected 'jumpSfx' sound effect")
        assertTrue(ir.soundEffects.any { it.id == "landSfx" }, "Expected 'landSfx' sound effect")
        assertTrue(ir.soundEffects.any { it.id == "winSfx" }, "Expected 'winSfx' sound effect")
    }

    @Test
    fun `has same actor count as DMG - 1 actor`() {
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
    fun `has goal zone`() {
        val goal =
            ir.systems.filterIsInstance<GenericSystem>().firstOrNull {
                it.config["type"] == "platformer_goal"
            }
        assertNotNull(goal, "Expected platformer_goal system")
        assertEquals("exit", goal.id)
    }

    @Test
    fun `has camera system`() {
        val camera =
            ir.systems.filterIsInstance<GenericSystem>().firstOrNull {
                it.config["type"] == "platformer_camera"
            }
        assertNotNull(camera, "Expected platformer_camera system")
    }
}
