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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathfindingTest {

    @Test
    fun testNavGridCreation() {
        // Create a simple 4x4 grid
        val grid =
            NavGrid(
                name = "test",
                width = 4,
                height = 4,
                walkableData = BooleanArray(16) { true },
                weightData = IntArray(16) { 1 },
                sourceMap = null
            )

        assertEquals(4, grid.width)
        assertEquals(4, grid.height)
        assertTrue(grid.isWalkableAt(0, 0))
        assertTrue(grid.isWalkableAt(3, 3))
    }

    @Test
    fun testNavGridWithObstacles() {
        // Create a 4x4 grid with a wall in the middle
        val data = BooleanArray(16) { true }
        data[5] = false // Block (1, 1)
        data[6] = false // Block (2, 1)
        val weights = IntArray(16) { 1 }
        weights[5] = 0
        weights[6] = 0

        val grid =
            NavGrid(
                name = "test",
                width = 4,
                height = 4,
                walkableData = data,
                weightData = weights,
                sourceMap = null
            )

        assertTrue(grid.isWalkableAt(0, 0))
        assertFalse(grid.isWalkableAt(1, 1))
        assertFalse(grid.isWalkableAt(2, 1))
        assertTrue(grid.isWalkableAt(3, 3))
    }

    @Test
    fun testNavGridBuilderManual() {
        val builder = NavGridBuilder("arena")
        builder.size = 8 x 8
        builder.default = false
        builder.walkable(1..6, 1..6)
        builder.blocked(3, 3)

        val grid = builder.build()

        assertEquals(8, grid.width)
        assertEquals(8, grid.height)
        assertFalse(grid.isWalkableAt(0, 0)) // Outside walkable region
        assertTrue(grid.isWalkableAt(1, 1)) // Inside walkable region
        assertFalse(grid.isWalkableAt(3, 3)) // Explicitly blocked
        assertTrue(grid.isWalkableAt(5, 5)) // Inside walkable region
    }

    @Test
    fun testNavGridOutOfBounds() {
        val grid =
            NavGrid(
                name = "test",
                width = 4,
                height = 4,
                walkableData = BooleanArray(16) { true },
                weightData = IntArray(16) { 1 },
                sourceMap = null
            )

        // Out of bounds should return false
        assertFalse(grid.isWalkableAt(-1, 0))
        assertFalse(grid.isWalkableAt(0, -1))
        assertFalse(grid.isWalkableAt(4, 0))
        assertFalse(grid.isWalkableAt(0, 4))
        assertFalse(grid.isWalkableAt(10, 10))
    }

    @Test
    fun testPathOptions() {
        val options = PathOptions(diagonal = true, maxDepth = 100, heuristic = Heuristic.CHEBYSHEV)

        assertTrue(options.diagonal)
        assertEquals(100, options.maxDepth)
        assertEquals(Heuristic.CHEBYSHEV, options.heuristic)
    }

    @Test
    fun testPathOptionsBuilder() {
        val builder = PathOptionsBuilder()
        builder.diagonal = true
        builder.maxDepth = 50
        builder.heuristic = Heuristic.MANHATTAN

        val options = builder.build()

        assertTrue(options.diagonal)
        assertEquals(50, options.maxDepth)
        assertEquals(Heuristic.MANHATTAN, options.heuristic)
    }

    @Test
    fun testHeuristicEnum() {
        assertEquals(3, Heuristic.entries.size)
        assertEquals(Heuristic.MANHATTAN, Heuristic.entries[0])
        assertEquals(Heuristic.CHEBYSHEV, Heuristic.entries[1])
        assertEquals(Heuristic.EUCLIDEAN, Heuristic.entries[2])
    }

    @Test
    fun testPoolPathfindingConfig() {
        val navGrid =
            NavGrid(
                name = "test",
                width = 8,
                height = 8,
                walkableData = BooleanArray(64) { true },
                weightData = IntArray(64) { 1 },
                sourceMap = null
            )

        val builder = PoolPathfindingBuilder()
        builder.navGrid = navGrid
        builder.updateInterval = 30.frames
        builder.maxDepth = 24
        builder.diagonal = true

        val config = builder.build()

        assertEquals(navGrid, config.navGrid)
        assertEquals(30, config.updateInterval.count)
        assertEquals(24, config.maxDepth)
        assertTrue(config.diagonal)
    }

    @Test
    fun testNavGridDataEncoding() {
        // Test that walkable data is correctly stored in bit-packed format
        val data = BooleanArray(16)
        data[0] = true // bit 0 of byte 0
        data[1] = false // bit 1 of byte 0
        data[2] = true // bit 2 of byte 0
        data[8] = true // bit 0 of byte 1
        val weights = IntArray(16) { if (data[it]) 1 else 0 }

        val grid =
            NavGrid(
                name = "test",
                width = 4,
                height = 4,
                walkableData = data,
                weightData = weights,
                sourceMap = null
            )

        assertTrue(grid.isWalkableAt(0, 0)) // data[0]
        assertFalse(grid.isWalkableAt(1, 0)) // data[1]
        assertTrue(grid.isWalkableAt(2, 0)) // data[2]
        assertTrue(grid.isWalkableAt(0, 2)) // data[8]
    }

    @Test
    fun testNavGridWeights() {
        // Test weighted tiles
        val builder = NavGridBuilder("weighted")
        builder.size = 4 x 4
        builder.default = true
        builder.weight(1..2, 1..2, cost = 3) // Slow terrain in center

        val grid = builder.build()

        assertEquals(1, grid.weightAt(0, 0)) // Default weight
        assertEquals(3, grid.weightAt(1, 1)) // Weighted terrain
        assertEquals(3, grid.weightAt(2, 2)) // Weighted terrain
        assertEquals(1, grid.weightAt(3, 3)) // Default weight
        assertTrue(grid.hasWeights)
    }
}
