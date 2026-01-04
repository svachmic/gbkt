/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.simulation

import io.github.gbkt.core.*
import io.github.gbkt.core.test.*
import kotlin.test.*

/** Tests for the simulation engine itself - verifies IR execution works correctly. */
class SimulationEngineTest {

    @Test
    fun testSimValueArithmetic() {
        val a = SimValue.of(10)
        val b = SimValue.of(3)

        assertEquals(13, (a + b).toInt())
        assertEquals(7, (a - b).toInt())
        assertEquals(30, (a * b).toInt())
        assertEquals(3, (a / b).toInt())
        assertEquals(1, (a % b).toInt())
    }

    @Test
    fun testSimValueBitwise() {
        val a = SimValue.of(0b1100)
        val b = SimValue.of(0b1010)

        assertEquals(0b1000, (a and b).toInt())
        assertEquals(0b1110, (a or b).toInt())
        assertEquals(0b0110, (a xor b).toInt())
    }

    @Test
    fun testSimValueComparison() {
        val a = SimValue.of(10)
        val b = SimValue.of(5)

        assertTrue((a gt b).isTrue)
        assertTrue((a gte b).isTrue)
        assertTrue((b lt a).isTrue)
        assertTrue((b lte a).isTrue)
        assertTrue((a eq SimValue.of(10)).isTrue)
        assertTrue((a neq b).isTrue)
    }

    @Test
    fun testSimValueLogical() {
        val t = SimValue.TRUE
        val f = SimValue.FALSE

        assertTrue((t land t).isTrue)
        assertFalse((t land f).isTrue)
        assertTrue((t lor f).isTrue)
        assertFalse((f lor f).isTrue)
        assertTrue(f.lnot().isTrue)
        assertFalse(t.lnot().isTrue)
    }

    @Test
    fun testMockInputProvider() {
        val input = MockInputProvider()

        assertEquals(0, input.joypad)

        input.press(Button.A)
        assertTrue(input.isPressed(Button.A))
        assertFalse(input.isPressed(Button.B))

        input.press(Button.RIGHT)
        assertTrue(input.isPressed(Button.A))
        assertTrue(input.isPressed(Button.RIGHT))

        input.release(Button.A)
        assertFalse(input.isPressed(Button.A))
        assertTrue(input.isPressed(Button.RIGHT))

        input.releaseAll()
        assertEquals(0, input.joypad)
    }

    @Test
    fun testSimSprite() {
        val sprite = SimSprite("player", 80, 72)

        assertTrue(sprite.isAt(80, 72))
        assertFalse(sprite.isAt(0, 0))

        sprite.x = 100
        assertTrue(sprite.isAt(100, 72))
    }

    @Test
    fun testSimSpriteCollision() {
        val player = SimSprite("player", 50, 50)
        val enemy = SimSprite("enemy", 55, 55)
        val farEnemy = SimSprite("far", 200, 200)

        assertTrue(player.collidesWith(enemy))
        assertFalse(player.collidesWith(farEnemy))
    }

    @Test
    fun testSimPoolBasics() {
        val pool =
            SimPool(
                name = "bullets",
                size = 4,
                hasPosition = true,
                hasVelocity = false,
                stateFields = emptyList(),
                onFrameStatements = emptyList(),
                despawnConditions = emptyList()
            )

        assertEquals(0, pool.activeCount)
        assertTrue(pool.hasSpace)
        assertFalse(pool.isFull)
    }
}
