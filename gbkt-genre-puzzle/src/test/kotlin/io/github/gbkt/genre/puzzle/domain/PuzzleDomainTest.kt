/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.puzzle.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for puzzle domain data classes.
 *
 * Domain objects are plain Kotlin data classes — NOT IR types. They carry puzzle configuration
 * (grid layout, match rules, block-push settings, scoring) that is used by DSL builders to produce
 * core IR types (GenericSystem).
 */
class PuzzleDomainTest {

    // -------------------------------------------------------------------------
    // PuzzleGridConfig — default values
    // -------------------------------------------------------------------------

    @Test
    fun `PuzzleGridConfig has correct default values`() {
        val config = PuzzleGridConfig(id = "grid")
        assertEquals("grid", config.id)
        assertEquals(PuzzleMode.MATCH, config.mode)
        assertEquals(6, config.width)
        assertEquals(6, config.height)
        assertNull(config.timer)
        assertFalse(config.moveCounterEnabled)
        assertTrue(config.customCellTypes.isEmpty())
    }

    @Test
    fun `PuzzleGridConfig supports copy()`() {
        val original = PuzzleGridConfig(id = "grid", width = 8, height = 8)
        val copy = original.copy(mode = PuzzleMode.BLOCK_PUSH)
        assertEquals(PuzzleMode.BLOCK_PUSH, copy.mode)
        assertEquals(8, copy.width)
        assertEquals("grid", copy.id)
    }

    @Test
    fun `PuzzleGridConfig equality`() {
        val config1 = PuzzleGridConfig(id = "grid")
        val config2 = PuzzleGridConfig(id = "grid")
        assertEquals(config1, config2)
    }

    // -------------------------------------------------------------------------
    // MatchConfig
    // -------------------------------------------------------------------------

    @Test
    fun `MatchConfig default values are correct`() {
        val config = MatchConfig()
        assertEquals(3, config.minMatchLength)
        assertEquals(GravityDirection.DOWN, config.gravityDirection)
        assertEquals(1.5f, config.chainMultiplier)
    }

    @Test
    fun `MatchConfig with custom min match length`() {
        val config = MatchConfig(minMatchLength = 4)
        assertEquals(4, config.minMatchLength)
    }

    @Test
    fun `MatchConfig with upward gravity`() {
        val config = MatchConfig(gravityDirection = GravityDirection.UP)
        assertEquals(GravityDirection.UP, config.gravityDirection)
    }

    @Test
    fun `MatchConfig with no gravity`() {
        val config = MatchConfig(gravityDirection = GravityDirection.NONE)
        assertEquals(GravityDirection.NONE, config.gravityDirection)
    }

    @Test
    fun `MatchConfig with custom chain multiplier`() {
        val config = MatchConfig(chainMultiplier = 2.0f)
        assertEquals(2.0f, config.chainMultiplier)
    }

    // -------------------------------------------------------------------------
    // BlockPushConfig
    // -------------------------------------------------------------------------

    @Test
    fun `BlockPushConfig has unlimited undo by default`() {
        val config = BlockPushConfig()
        assertTrue(config.undoEnabled)
        assertEquals(Int.MAX_VALUE, config.undoMaxDepth)
        assertTrue(config.goalTiles.isEmpty())
    }

    @Test
    fun `BlockPushConfig with goal tiles`() {
        val config = BlockPushConfig(goalTiles = listOf(Pair(3, 3), Pair(4, 4)))
        assertEquals(2, config.goalTiles.size)
        assertEquals(Pair(3, 3), config.goalTiles[0])
        assertEquals(Pair(4, 4), config.goalTiles[1])
    }

    @Test
    fun `BlockPushConfig with limited undo depth`() {
        val config = BlockPushConfig(undoMaxDepth = 10)
        assertEquals(10, config.undoMaxDepth)
    }

    @Test
    fun `BlockPushConfig with undo disabled`() {
        val config = BlockPushConfig(undoEnabled = false)
        assertFalse(config.undoEnabled)
    }

    // -------------------------------------------------------------------------
    // CustomCellType registration
    // -------------------------------------------------------------------------

    @Test
    fun `CustomCellType with NONE behavior by default`() {
        val cell = CustomCellType(id = "red", name = "Red")
        assertEquals("red", cell.id)
        assertEquals("Red", cell.name)
        assertEquals(CellBehavior.NONE, cell.behavior)
    }

    @Test
    fun `CustomCellType with BOMB behavior`() {
        val cell = CustomCellType(id = "bomb", name = "Bomb", behavior = CellBehavior.BOMB)
        assertEquals(CellBehavior.BOMB, cell.behavior)
    }

    @Test
    fun `CustomCellType with WILDCARD behavior`() {
        val cell = CustomCellType(id = "wild", name = "Wildcard", behavior = CellBehavior.WILDCARD)
        assertEquals(CellBehavior.WILDCARD, cell.behavior)
    }

    @Test
    fun `CustomCellType with ICE behavior`() {
        val cell = CustomCellType(id = "ice", name = "Ice", behavior = CellBehavior.ICE)
        assertEquals(CellBehavior.ICE, cell.behavior)
    }

    @Test
    fun `multiple custom cell types in PuzzleGridConfig`() {
        val bomb = CustomCellType(id = "bomb", name = "Bomb", behavior = CellBehavior.BOMB)
        val wild = CustomCellType(id = "wild", name = "Wildcard", behavior = CellBehavior.WILDCARD)
        val config = PuzzleGridConfig(id = "grid", customCellTypes = listOf(bomb, wild))
        assertEquals(2, config.customCellTypes.size)
        assertEquals("bomb", config.customCellTypes[0].id)
        assertEquals("wild", config.customCellTypes[1].id)
    }

    // -------------------------------------------------------------------------
    // TimerConfig
    // -------------------------------------------------------------------------

    @Test
    fun `TimerConfig COUNTDOWN mode defaults`() {
        val timer = TimerConfig()
        assertEquals(TimerMode.COUNTDOWN, timer.mode)
        assertEquals(3600, timer.durationFrames)
    }

    @Test
    fun `TimerConfig ELAPSED mode`() {
        val timer = TimerConfig(mode = TimerMode.ELAPSED)
        assertEquals(TimerMode.ELAPSED, timer.mode)
    }

    @Test
    fun `TimerConfig custom duration`() {
        val timer = TimerConfig(durationFrames = 1800)
        assertEquals(1800, timer.durationFrames)
    }

    @Test
    fun `PuzzleGridConfig with timer enabled`() {
        val timerInput = TimerConfig(mode = TimerMode.COUNTDOWN, durationFrames = 3600)
        val config = PuzzleGridConfig(id = "timed_grid", timer = timerInput)
        val timer = config.timer
        assertNotNull(timer)
        assertEquals(TimerMode.COUNTDOWN, timer.mode)
    }

    // -------------------------------------------------------------------------
    // PuzzleScoringConfig
    // -------------------------------------------------------------------------

    @Test
    fun `PuzzleScoringConfig default values`() {
        val scoring = PuzzleScoringConfig()
        assertEquals(100, scoring.baseScore)
        assertEquals(1.5f, scoring.chainMultiplier)
        assertEquals(0, scoring.moveBonus)
        assertEquals(0, scoring.timeBonus)
    }

    @Test
    fun `PuzzleScoringConfig with custom values`() {
        val scoring =
            PuzzleScoringConfig(
                baseScore = 200,
                chainMultiplier = 2.0f,
                moveBonus = 500,
                timeBonus = 1000,
            )
        assertEquals(200, scoring.baseScore)
        assertEquals(2.0f, scoring.chainMultiplier)
        assertEquals(500, scoring.moveBonus)
        assertEquals(1000, scoring.timeBonus)
    }

    // -------------------------------------------------------------------------
    // PuzzleMode enum
    // -------------------------------------------------------------------------

    @Test
    fun `PuzzleMode has MATCH and BLOCK_PUSH values`() {
        val modes = PuzzleMode.entries
        assertTrue(modes.contains(PuzzleMode.MATCH))
        assertTrue(modes.contains(PuzzleMode.BLOCK_PUSH))
    }

    // -------------------------------------------------------------------------
    // GravityDirection enum
    // -------------------------------------------------------------------------

    @Test
    fun `GravityDirection has DOWN, UP, and NONE values`() {
        val directions = GravityDirection.entries
        assertTrue(directions.contains(GravityDirection.DOWN))
        assertTrue(directions.contains(GravityDirection.UP))
        assertTrue(directions.contains(GravityDirection.NONE))
    }

    // -------------------------------------------------------------------------
    // BaseCellType enum
    // -------------------------------------------------------------------------

    @Test
    fun `BaseCellType has NORMAL, EMPTY, and WALL values`() {
        val types = BaseCellType.entries
        assertTrue(types.contains(BaseCellType.NORMAL))
        assertTrue(types.contains(BaseCellType.EMPTY))
        assertTrue(types.contains(BaseCellType.WALL))
    }
}
