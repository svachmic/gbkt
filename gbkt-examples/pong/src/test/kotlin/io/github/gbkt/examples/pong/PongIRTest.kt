/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.pong

import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * IR validation tests for the Pong v2 DSL definition.
 *
 * Verifies:
 * - Correct number of scenes (3), actors (3), variables (4)
 * - Start scene is "title"
 * - Frame ops present in game scene
 * - Enter ops present in title scene
 * - Actor positions are correct
 * - Actor sprites are non-null
 * - No RPG-specific system nodes in output
 */
class PongIRTest {

    private val ir = pongV2.build()

    @Test
    fun `has 3 scenes`() {
        assertEquals(3, ir.scenes.size)
    }

    @Test
    fun `has 3 actors`() {
        assertEquals(3, ir.actors.size)
    }

    @Test
    fun `start scene is title`() {
        assertEquals("title", ir.startScene)
    }

    @Test
    fun `has 4 variables`() {
        assertEquals(4, ir.variables.size)
    }

    @Test
    fun `has p1Score variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "p1Score" && it.type == VarType.U8 })
    }

    @Test
    fun `has p2Score variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "p2Score" && it.type == VarType.U8 })
    }

    @Test
    fun `has ballDx variable of type I8`() {
        assertTrue(ir.variables.any { it.name == "ballDx" && it.type == VarType.I8 })
    }

    @Test
    fun `has ballDy variable of type I8`() {
        assertTrue(ir.variables.any { it.name == "ballDy" && it.type == VarType.I8 })
    }

    @Test
    fun `game scene has frame ops`() {
        assertTrue(ir.scenes.first { it.id == "game" }.frameOps.isNotEmpty())
    }

    @Test
    fun `title scene has enter ops`() {
        assertTrue(ir.scenes.first { it.id == "title" }.enterOps.isNotEmpty())
    }

    @Test
    fun `gameover scene has enter ops`() {
        assertTrue(ir.scenes.first { it.id == "gameover" }.enterOps.isNotEmpty())
    }

    @Test
    fun `ball actor has correct initial position`() {
        assertEquals(PositionDef(80, 72), ir.actors.first { it.id == "ball" }.position)
    }

    @Test
    fun `paddle1 actor has correct initial position`() {
        assertEquals(PositionDef(16, 64), ir.actors.first { it.id == "paddle1" }.position)
    }

    @Test
    fun `paddle2 actor has correct initial position`() {
        assertEquals(PositionDef(152, 64), ir.actors.first { it.id == "paddle2" }.position)
    }

    @Test
    fun `ball actor has a sprite`() {
        assertNotNull(ir.actors.first { it.id == "ball" }.sprite)
    }

    @Test
    fun `paddle1 actor has a sprite`() {
        assertNotNull(ir.actors.first { it.id == "paddle1" }.sprite)
    }

    @Test
    fun `no RPG systems in output`() {
        // Pong uses core-only — no GenericSystem from RPG builders expected
        val genericSystems = ir.systems.filterIsInstance<io.github.gbkt.core.ir.GenericSystem>()
        assertTrue(
            genericSystems.none { it.config["type"] == "simple_battle" },
            "Pong must not contain any RPG simple_battle system",
        )
    }

    @Test
    fun `scenes are title game gameover`() {
        val sceneIds = ir.scenes.map { it.id }.toSet()
        assertTrue(sceneIds.contains("title"))
        assertTrue(sceneIds.contains("game"))
        assertTrue(sceneIds.contains("gameover"))
    }

    @Test
    fun `actors are paddle1 paddle2 ball`() {
        val actorIds = ir.actors.map { it.id }.toSet()
        assertTrue(actorIds.contains("paddle1"))
        assertTrue(actorIds.contains("paddle2"))
        assertTrue(actorIds.contains("ball"))
    }

    @Test
    fun `has sound effects`() {
        assertTrue(ir.soundEffects.isNotEmpty(), "Should have at least one sound effect")
    }

    @Test
    fun `has bounce and score sound effects`() {
        assertTrue(
            ir.soundEffects.any { it.id == "bounceSfx" },
            "Expected 'bounceSfx' sound effect",
        )
        assertTrue(ir.soundEffects.any { it.id == "scoreSfx" }, "Expected 'scoreSfx' sound effect")
        assertTrue(ir.soundEffects.any { it.id == "winSfx" }, "Expected 'winSfx' sound effect")
    }
}
