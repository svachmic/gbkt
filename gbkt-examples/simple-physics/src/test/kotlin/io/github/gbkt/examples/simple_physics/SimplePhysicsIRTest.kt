/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.simple_physics

import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * IR validation tests for the SimplePhysics DSL definition.
 *
 * Verifies:
 * - Exactly 1 scene (`play`) — D-06 single-scene rail.
 * - Exactly 1 actor (`ball`) at PositionDef(64, 64).
 * - Exactly 4 variables (posX, posY, spdX, spdY) all typed I16 — D-08 i16Var-only rail.
 * - `play` is the start scene.
 * - `enter` and `frame` ops are non-empty.
 */
class SimplePhysicsIRTest {

    private val ir = simplePhysics.build()

    @Test
    fun `has 1 scene`() {
        assertEquals(1, ir.scenes.size)
    }

    @Test
    fun `has 1 actor`() {
        assertEquals(1, ir.actors.size)
    }

    @Test
    fun `start scene is play`() {
        assertEquals("play", ir.startScene)
    }

    @Test
    fun `has 4 variables`() {
        assertEquals(4, ir.variables.size)
    }

    @Test
    fun `has posX variable of type I16`() {
        assertTrue(ir.variables.any { it.name == "posX" && it.type == VarType.I16 })
    }

    @Test
    fun `has posY variable of type I16`() {
        assertTrue(ir.variables.any { it.name == "posY" && it.type == VarType.I16 })
    }

    @Test
    fun `has spdX variable of type I16`() {
        assertTrue(ir.variables.any { it.name == "spdX" && it.type == VarType.I16 })
    }

    @Test
    fun `has spdY variable of type I16`() {
        assertTrue(ir.variables.any { it.name == "spdY" && it.type == VarType.I16 })
    }

    @Test
    fun `play scene has enter ops`() {
        assertTrue(ir.scenes.first { it.id == "play" }.enterOps.isNotEmpty())
    }

    @Test
    fun `play scene has frame ops`() {
        assertTrue(ir.scenes.first { it.id == "play" }.frameOps.isNotEmpty())
    }

    @Test
    fun `ball actor has initial position 64 64`() {
        assertEquals(PositionDef(64, 64), ir.actors.first { it.id == "ball" }.position)
    }

    @Test
    fun `ball actor has a sprite`() {
        assertNotNull(ir.actors.first { it.id == "ball" }.sprite)
    }

    @Test
    fun `scenes are play only`() {
        val sceneIds = ir.scenes.map { it.id }.toSet()
        assertEquals(setOf("play"), sceneIds)
    }

    @Test
    fun `actors are ball only`() {
        val actorIds = ir.actors.map { it.id }.toSet()
        assertEquals(setOf("ball"), actorIds)
    }
}
