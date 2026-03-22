/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.breakout

import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * IR validation tests for the Breakout v2 DSL definition.
 *
 * Verifies:
 * - Correct number of scenes (4: title, game, gameover, win)
 * - Correct number of actors (2: paddle, ball)
 * - Correct number of variables (5: score, lives, bricksLeft, ballDx, ballDy)
 * - Start scene is "title"
 * - Sound effects registered
 * - Frame ops present in game scene
 * - Enter ops present in all scenes
 * - No RPG-specific system nodes in output
 */
class BreakoutIRTest {

    private val ir = breakoutV2.build()

    @Test
    fun `has 4 scenes`() {
        assertEquals(4, ir.scenes.size)
    }

    @Test
    fun `has 2 actors`() {
        assertEquals(2, ir.actors.size)
    }

    @Test
    fun `start scene is title`() {
        assertEquals("title", ir.startScene)
    }

    @Test
    fun `has at least 5 game variables`() {
        // Original 5: score, lives, bricksLeft, ballDx, ballDy
        // Plus 3 brick collision intermediates: _bc, _brow, _bidx
        assertTrue(
            ir.variables.size >= 5,
            "Expected at least 5 variables, got ${ir.variables.size}",
        )
    }

    @Test
    fun `has score variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "score" && it.type == VarType.U8 })
    }

    @Test
    fun `has lives variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "lives" && it.type == VarType.U8 })
    }

    @Test
    fun `has bricksLeft variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "bricksLeft" && it.type == VarType.U8 })
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
    fun `win scene has enter ops`() {
        assertTrue(ir.scenes.first { it.id == "win" }.enterOps.isNotEmpty())
    }

    @Test
    fun `ball actor has correct initial position`() {
        assertEquals(PositionDef(80, 120), ir.actors.first { it.id == "ball" }.position)
    }

    @Test
    fun `paddle actor has correct initial position`() {
        assertEquals(PositionDef(72, 132), ir.actors.first { it.id == "paddle" }.position)
    }

    @Test
    fun `ball actor has a sprite`() {
        assertNotNull(ir.actors.first { it.id == "ball" }.sprite)
    }

    @Test
    fun `paddle actor has a sprite`() {
        assertNotNull(ir.actors.first { it.id == "paddle" }.sprite)
    }

    @Test
    fun `has sound effect systems`() {
        // Sound effects are now stored in ir.soundEffects (SoundEffectDef list)
        // rather than as SoundSystem instances in ir.systems.
        assertTrue(
            ir.soundEffects.isNotEmpty(),
            "Breakout must have at least one sound effect registered",
        )
        assertTrue(ir.soundEffects.any { it.id == "hitSfx" }, "Expected 'hitSfx' sound effect")
        assertTrue(ir.soundEffects.any { it.id == "scoreSfx" }, "Expected 'scoreSfx' sound effect")
    }

    @Test
    fun `no RPG systems in output`() {
        val genericSystems = ir.systems.filterIsInstance<io.github.gbkt.core.ir.GenericSystem>()
        assertTrue(
            genericSystems.none { it.config["type"] == "simple_battle" },
            "Breakout must not contain any RPG simple_battle system",
        )
    }

    @Test
    fun `scenes are title game gameover win`() {
        val sceneIds = ir.scenes.map { it.id }.toSet()
        assertTrue(sceneIds.contains("title"))
        assertTrue(sceneIds.contains("game"))
        assertTrue(sceneIds.contains("gameover"))
        assertTrue(sceneIds.contains("win"))
    }

    @Test
    fun `actors are paddle and ball`() {
        val actorIds = ir.actors.map { it.id }.toSet()
        assertTrue(actorIds.contains("paddle"))
        assertTrue(actorIds.contains("ball"))
    }
}
