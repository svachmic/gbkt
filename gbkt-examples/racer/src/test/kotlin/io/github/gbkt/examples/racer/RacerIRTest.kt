/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.racer

import io.github.gbkt.core.ir.CameraSystem
import io.github.gbkt.core.ir.GbcTarget
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * IR validation tests for the Racer v2 DSL definition.
 *
 * Verifies:
 * - Correct number of scenes (3), actors (1), variables (3)
 * - Start scene is "title"
 * - Racing system registered with correct type
 * - Camera system present with follow config
 * - Zone defined for circuit track
 * - GBC_COMPATIBLE target configured
 * - Named sound effects present: engineSfx, turnSfx, lapSfx, winSfx
 * - Car actor initial position is (40, 100)
 */
class RacerIRTest {

    private val ir = racer.build()

    @Test
    fun `has 3 scenes`() {
        assertEquals(3, ir.scenes.size)
    }

    @Test
    fun `start scene is title`() {
        assertEquals("title", ir.startScene)
    }

    @Test
    fun `has 1 actor`() {
        assertEquals(1, ir.actors.size)
    }

    @Test
    fun `has 3 variables`() {
        assertEquals(3, ir.variables.size)
    }

    @Test
    fun `has lap variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "lap" && it.type == VarType.U8 })
    }

    @Test
    fun `has raceTime variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "raceTime" && it.type == VarType.U8 })
    }

    @Test
    fun `has position variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "position" && it.type == VarType.U8 })
    }

    @Test
    fun `has sound effects`() {
        assertTrue(ir.soundEffects.isNotEmpty(), "Should have at least one sound effect")
    }

    @Test
    fun `has named sound effects`() {
        assertTrue(
            ir.soundEffects.any { it.id == "engineSfx" },
            "Expected 'engineSfx' sound effect",
        )
        assertTrue(ir.soundEffects.any { it.id == "turnSfx" }, "Expected 'turnSfx' sound effect")
        assertTrue(ir.soundEffects.any { it.id == "lapSfx" }, "Expected 'lapSfx' sound effect")
        assertTrue(ir.soundEffects.any { it.id == "winSfx" }, "Expected 'winSfx' sound effect")
    }

    @Test
    fun `has racing system`() {
        val genericSystems = ir.systems.filterIsInstance<io.github.gbkt.core.ir.GenericSystem>()
        assertTrue(
            genericSystems.any { it.config["type"] == "sport_racing" },
            "Expected racing system with type=sport_racing",
        )
    }

    @Test
    fun `has camera system`() {
        val camera = ir.systems.filterIsInstance<CameraSystem>().firstOrNull()
        assertNotNull(camera, "Expected camera system to be configured")
    }

    @Test
    fun `camera follows car`() {
        val camera = ir.systems.filterIsInstance<CameraSystem>().firstOrNull()
        assertNotNull(camera, "Camera must be present")
        assertEquals("car", camera.followActorId)
    }

    @Test
    fun `has zone definition`() {
        assertTrue(ir.zones.isNotEmpty(), "Expected at least one zone (circuit track)")
    }

    @Test
    fun `has circuit zone`() {
        assertTrue(ir.zones.any { it.id == "circuit" }, "Expected zone with id 'circuit'")
    }

    @Test
    fun `has GBC compatible target`() {
        assertEquals(GbcTarget.GBC_COMPATIBLE, ir.config.gbcTarget)
    }

    @Test
    fun `scenes include race and results`() {
        val sceneIds = ir.scenes.map { it.id }.toSet()
        assertTrue(sceneIds.contains("title"))
        assertTrue(sceneIds.contains("race"))
        assertTrue(sceneIds.contains("results"))
    }

    @Test
    fun `car actor has correct initial position`() {
        assertEquals(PositionDef(40, 100), ir.actors.first { it.id == "car" }.position)
    }

    @Test
    fun `car actor has a sprite`() {
        assertNotNull(ir.actors.first { it.id == "car" }.sprite)
    }

    @Test
    fun `race scene has frame ops`() {
        assertTrue(ir.scenes.first { it.id == "race" }.frameOps.isNotEmpty())
    }

    @Test
    fun `title scene has enter ops`() {
        assertTrue(ir.scenes.first { it.id == "title" }.enterOps.isNotEmpty())
    }

    @Test
    fun `results scene has enter ops`() {
        assertTrue(ir.scenes.first { it.id == "results" }.enterOps.isNotEmpty())
    }
}
