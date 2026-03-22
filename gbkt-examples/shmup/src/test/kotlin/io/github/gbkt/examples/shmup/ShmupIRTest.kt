/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.shmup

import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * IR validation tests for the Shmup v2 DSL definition.
 *
 * Verifies:
 * - Correct number of scenes (3), actors (3), variables (5)
 * - Start scene is "title"
 * - Actor pools present for bullets (max 8) and enemies (max 4)
 * - Sound effects registered: shootSfx, explodeSfx, hitSfx, scoreSfx
 * - Variable types match DSL declarations
 * - Scene IDs include gameplay, title, gameover
 * - Player initial position is (80, 120)
 */
class ShmupIRTest {

    private val ir = shmup.build()

    @Test
    fun `has 3 scenes`() {
        assertEquals(3, ir.scenes.size)
    }

    @Test
    fun `start scene is title`() {
        assertEquals("title", ir.startScene)
    }

    @Test
    fun `has 3 actors`() {
        assertEquals(3, ir.actors.size)
    }

    @Test
    fun `has 5 variables`() {
        assertEquals(5, ir.variables.size)
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
    fun `has scrollY variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "scrollY" && it.type == VarType.U8 })
    }

    @Test
    fun `has shootCooldown variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "shootCooldown" && it.type == VarType.U8 })
    }

    @Test
    fun `has waveTimer variable of type U8`() {
        assertTrue(ir.variables.any { it.name == "waveTimer" && it.type == VarType.U8 })
    }

    @Test
    fun `has sound effects`() {
        assertTrue(ir.soundEffects.isNotEmpty(), "Should have at least one sound effect")
    }

    @Test
    fun `has named sound effects`() {
        assertTrue(ir.soundEffects.any { it.id == "shootSfx" }, "Expected 'shootSfx' sound effect")
        assertTrue(
            ir.soundEffects.any { it.id == "explodeSfx" },
            "Expected 'explodeSfx' sound effect",
        )
        assertTrue(ir.soundEffects.any { it.id == "hitSfx" }, "Expected 'hitSfx' sound effect")
        assertTrue(ir.soundEffects.any { it.id == "scoreSfx" }, "Expected 'scoreSfx' sound effect")
    }

    @Test
    fun `has bullet pool`() {
        assertTrue(ir.actorPools.any { it.id == "bulletPool" }, "Expected 'bulletPool' actor pool")
    }

    @Test
    fun `bullet pool has max 8 slots`() {
        val pool = ir.actorPools.first { it.id == "bulletPool" }
        assertEquals(8, pool.config.maxSize)
    }

    @Test
    fun `has enemy pool`() {
        assertTrue(ir.actorPools.any { it.id == "enemyPool" }, "Expected 'enemyPool' actor pool")
    }

    @Test
    fun `enemy pool has max 4 slots`() {
        val pool = ir.actorPools.first { it.id == "enemyPool" }
        assertEquals(4, pool.config.maxSize)
    }

    @Test
    fun `scene IDs include title gameplay gameover`() {
        val sceneIds = ir.scenes.map { it.id }.toSet()
        assertTrue(sceneIds.contains("title"))
        assertTrue(sceneIds.contains("gameplay"))
        assertTrue(sceneIds.contains("gameover"))
    }

    @Test
    fun `player actor has correct initial position`() {
        assertEquals(PositionDef(80, 120), ir.actors.first { it.id == "player" }.position)
    }

    @Test
    fun `player actor has a sprite`() {
        assertNotNull(ir.actors.first { it.id == "player" }.sprite)
    }

    @Test
    fun `bullet actor has correct initial position`() {
        assertEquals(PositionDef(-8, -8), ir.actors.first { it.id == "bullet" }.position)
    }

    @Test
    fun `actors are player bullet enemy`() {
        val actorIds = ir.actors.map { it.id }.toSet()
        assertTrue(actorIds.contains("player"))
        assertTrue(actorIds.contains("bullet"))
        assertTrue(actorIds.contains("enemy"))
    }

    @Test
    fun `title scene has enter ops`() {
        assertTrue(
            ir.scenes.first { it.id == "title" }.enterOps.isNotEmpty(),
            "Title scene should have enter ops (hideSprites, clear, print)",
        )
    }

    @Test
    fun `gameover scene has enter ops`() {
        assertTrue(
            ir.scenes.first { it.id == "gameover" }.enterOps.isNotEmpty(),
            "Gameover scene should have enter ops (hideSprites, clear, print score)",
        )
    }
}
