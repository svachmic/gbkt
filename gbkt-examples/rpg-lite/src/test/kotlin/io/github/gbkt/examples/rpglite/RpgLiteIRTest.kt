/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.rpglite

import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * IR validation tests for the RPG Lite v2 DSL definition.
 *
 * Verifies:
 * - Correct number of scenes (4: title, town, dungeon, gameover)
 * - Correct number of actors (1: heroActor)
 * - Correct number of variables (4: hp, gold, dungeonLevel, stepCount)
 * - Start scene is "title"
 * - Sound effects registered (hitSfx, coinSfx, winSfx, loseSfx)
 * - CombatEngineSystem present for "combat" battle system
 * - Scene IDs include town and dungeon
 * - Actor position and sprite present
 */
class RpgLiteIRTest {

    private val ir = rpgLite.build()

    @Test
    fun `has 4 scenes`() {
        assertEquals(4, ir.scenes.size)
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
    fun `has 4 variables`() {
        assertEquals(4, ir.variables.size)
    }

    @Test
    fun `has hp variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "hp" && it.type == VarType.U8 })
    }

    @Test
    fun `has gold variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "gold" && it.type == VarType.U8 })
    }

    @Test
    fun `has dungeonLevel variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "dungeonLevel" && it.type == VarType.U8 })
    }

    @Test
    fun `has stepCount variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "stepCount" && it.type == VarType.U8 })
    }

    @Test
    fun `has sound effects`() {
        assertTrue(ir.soundEffects.isNotEmpty(), "Should have at least one sound effect")
    }

    @Test
    fun `has all 4 named sound effects`() {
        assertTrue(ir.soundEffects.any { it.id == "hitSfx" }, "Expected 'hitSfx' sound effect")
        assertTrue(ir.soundEffects.any { it.id == "coinSfx" }, "Expected 'coinSfx' sound effect")
        assertTrue(ir.soundEffects.any { it.id == "winSfx" }, "Expected 'winSfx' sound effect")
        assertTrue(ir.soundEffects.any { it.id == "loseSfx" }, "Expected 'loseSfx' sound effect")
    }

    @Test
    fun `has CombatEngineSystem for combat`() {
        val combatSystems = ir.systems.filterIsInstance<CombatEngineSystem>()
        assertTrue(
            combatSystems.any { it.id == "combat" },
            "Expected a CombatEngineSystem with id 'combat'",
        )
    }

    @Test
    fun `scenes are title town dungeon gameover`() {
        val sceneIds = ir.scenes.map { it.id }.toSet()
        assertTrue(sceneIds.contains("title"))
        assertTrue(sceneIds.contains("town"))
        assertTrue(sceneIds.contains("dungeon"))
        assertTrue(sceneIds.contains("gameover"))
    }

    @Test
    fun `heroActor has correct initial position`() {
        assertEquals(PositionDef(80, 72), ir.actors.first { it.id == "heroActor" }.position)
    }

    @Test
    fun `heroActor has a sprite`() {
        assertNotNull(ir.actors.first { it.id == "heroActor" }.sprite)
    }

    @Test
    fun `dungeon scene has frame ops`() {
        assertTrue(ir.scenes.first { it.id == "dungeon" }.frameOps.isNotEmpty())
    }

    @Test
    fun `title scene has enter ops`() {
        assertTrue(ir.scenes.first { it.id == "title" }.enterOps.isNotEmpty())
    }

    @Test
    fun `town scene has enter ops`() {
        assertTrue(ir.scenes.first { it.id == "town" }.enterOps.isNotEmpty())
    }

    @Test
    fun `gameover scene has enter ops`() {
        assertTrue(ir.scenes.first { it.id == "gameover" }.enterOps.isNotEmpty())
    }

    @Test
    fun `has ability system for fireball`() {
        val abilitySystems = ir.systems.filterIsInstance<GenericSystem>()
        assertTrue(
            abilitySystems.any { it.config["type"] == "rpg_ability" && it.id == "fireball" },
            "Expected GenericSystem with type='rpg_ability' and id='fireball'",
        )
    }
}
