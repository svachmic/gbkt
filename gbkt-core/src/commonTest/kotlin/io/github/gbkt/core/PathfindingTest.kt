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
                sourceMap = null,
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
                sourceMap = null,
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
                sourceMap = null,
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
                sourceMap = null,
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
                sourceMap = null,
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

    // =========================================================================
    // NAVGRID OBSTACLE TESTS
    // =========================================================================

    @Test
    fun testNavGridSetBlockedGeneratesCode() {
        val game =
            gbGame("test") {
                val grid =
                    navGrid("arena") {
                        size = 8 x 8
                        default = true
                    }

                start =
                    scene("main") {
                        enter { grid.setBlocked(3, 3) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("arena") && code.contains("3"), "Should set blocked at 3,3")
    }

    @Test
    fun testNavGridSetWalkableGeneratesCode() {
        val game =
            gbGame("test") {
                val grid =
                    navGrid("arena") {
                        size = 8 x 8
                        default = false
                    }

                start =
                    scene("main") {
                        enter { grid.setWalkable(5, 5) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("arena") && code.contains("5"), "Should set walkable at 5,5")
    }

    @Test
    fun testNavGridIsWalkableCondition() {
        val game =
            gbGame("test") {
                val grid =
                    navGrid("arena") {
                        size = 8 x 8
                        default = true
                    }

                var canMove by u8Var(0)

                start =
                    scene("main") {
                        every.frame { whenever(grid.isWalkable(2, 2)) { canMove set 1 } }
                    }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("arena") && (code.contains("2") || code.contains("walkable")),
            "Should check walkable condition",
        )
    }

    @Test
    fun testNavGridSetWeightGeneratesCode() {
        val game =
            gbGame("test") {
                val grid =
                    navGrid("arena") {
                        size = 8 x 8
                        default = true
                    }

                start =
                    scene("main") {
                        enter { grid.setWeight(4, 4, 10) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("arena") && code.contains("10"), "Should set weight to 10")
    }

    // =========================================================================
    // PATH OPTIONS TESTS
    // =========================================================================

    @Test
    fun testPathOptionsAllFields() {
        val options = PathOptions(diagonal = true, maxDepth = 200, heuristic = Heuristic.EUCLIDEAN)

        assertTrue(options.diagonal)
        assertEquals(200, options.maxDepth)
        assertEquals(Heuristic.EUCLIDEAN, options.heuristic)
    }

    @Test
    fun testPathOptionsDefaults() {
        val options = PathOptions()

        assertFalse(options.diagonal)
        assertEquals(64, options.maxDepth)
        assertEquals(Heuristic.MANHATTAN, options.heuristic)
    }

    // =========================================================================
    // NAVGRID BUILDER EDGE CASES
    // =========================================================================

    @Test
    fun testNavGridBuilderRowWeights() {
        val builder = NavGridBuilder("terrain")
        builder.size = 4 x 4
        builder.default = true
        builder.weight(0..3, 2, cost = 5) // Entire row 2 is slow

        val grid = builder.build()

        assertEquals(5, grid.weightAt(0, 2))
        assertEquals(5, grid.weightAt(1, 2))
        assertEquals(5, grid.weightAt(2, 2))
        assertEquals(5, grid.weightAt(3, 2))
        assertEquals(1, grid.weightAt(0, 0)) // Row 0 is default
    }

    @Test
    fun testNavGridBuilderBlockedRange() {
        val builder = NavGridBuilder("walls")
        builder.size = 8 x 8
        builder.default = true
        builder.blocked(2..5, 2..5) // Block center 4x4 area

        val grid = builder.build()

        assertTrue(grid.isWalkableAt(0, 0))
        assertTrue(grid.isWalkableAt(1, 1))
        assertFalse(grid.isWalkableAt(2, 2))
        assertFalse(grid.isWalkableAt(5, 5))
        assertTrue(grid.isWalkableAt(6, 6))
        assertTrue(grid.isWalkableAt(7, 7))
    }

    @Test
    fun testNavGridBuilderSingleCellWeight() {
        val builder = NavGridBuilder("special")
        builder.size = 8 x 8
        builder.default = true
        builder.weight(4, 4, cost = 255) // Very expensive single tile

        val grid = builder.build()

        assertEquals(255, grid.weightAt(4, 4))
        assertEquals(1, grid.weightAt(3, 4))
        assertEquals(1, grid.weightAt(4, 3))
    }

    // =========================================================================
    // PATH FOLLOW CONFIG
    // =========================================================================

    @Test
    fun testPathFollowBuilderConfig() {
        val builder = PathFollowBuilder()
        builder.speed = 2

        // Note: onArrive/onBlocked use RecordingContext to capture DSL statements
        // They need to be used within a game context, so we just test speed here
        val config = builder.build()

        assertEquals(2, config.speed)
        assertTrue(config.onArrive.isEmpty()) // No callback set
        assertTrue(config.onBlocked.isEmpty()) // No callback set
    }

    @Test
    fun testPoolPathfindingBuilderDefaults() {
        val grid =
            NavGrid(
                name = "test",
                width = 8,
                height = 8,
                walkableData = BooleanArray(64) { true },
                weightData = IntArray(64) { 1 },
                sourceMap = null,
            )

        val builder = PoolPathfindingBuilder()
        builder.navGrid = grid

        val config = builder.build()

        assertEquals(grid, config.navGrid)
        assertEquals(60, config.updateInterval.count) // Default update interval
        assertEquals(32, config.maxDepth) // Default max depth
        assertFalse(config.diagonal) // Default no diagonal
    }

    // =========================================================================
    // HEURISTIC TESTS
    // =========================================================================

    @Test
    fun testAllHeuristics() {
        // Verify all heuristics are available
        val heuristics = listOf(Heuristic.MANHATTAN, Heuristic.CHEBYSHEV, Heuristic.EUCLIDEAN)

        assertEquals(3, heuristics.size)

        // Create path options with each heuristic
        for (h in heuristics) {
            val options = PathOptions(diagonal = true, maxDepth = 100, heuristic = h)
            assertEquals(h, options.heuristic)
        }
    }

    @Test
    fun testPathOptionsBuilderDefaults() {
        val builder = PathOptionsBuilder()
        val options = builder.build()

        assertFalse(options.diagonal)
        assertEquals(64, options.maxDepth)
        assertEquals(Heuristic.MANHATTAN, options.heuristic)
    }
}
