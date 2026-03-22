/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.puzzle.dsl

import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.genre.puzzle.domain.CellBehavior
import io.github.gbkt.genre.puzzle.domain.GravityDirection
import io.github.gbkt.genre.puzzle.domain.MatchConfig
import io.github.gbkt.genre.puzzle.domain.PuzzleMode
import io.github.gbkt.genre.puzzle.domain.TimerMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests proving that the puzzle DSL builders produce correct GenericSystem IR types.
 *
 * Key constraint: NO new sealed IR subtypes created. puzzleGrid() produces GenericSystem (a core IR
 * type, not a puzzle-specific sealed subtype). All puzzle data travels in the config map with type
 * key "puzzle_grid".
 */
class PuzzleBuildersTest {

    // -------------------------------------------------------------------------
    // puzzleGrid {} — GenericSystem type verification
    // -------------------------------------------------------------------------

    @Test
    fun `puzzleGrid produces GenericSystem with type puzzle_grid`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") {}
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" }
        assertNotNull(system, "Expected system with id 'grid'")
        assertIs<GenericSystem>(
            system,
            "puzzleGrid must produce GenericSystem, not a custom subtype",
        )
        assertEquals("puzzle_grid", system.config["type"])
    }

    @Test
    fun `all puzzleGrid configurations produce type puzzle_grid`() {
        val ir =
            game("PuzzleTest") {
                    // Match mode
                    puzzleGrid("match_grid") { matchMode { minMatchLength(3) } }
                    // Block-push mode
                    puzzleGrid("push_grid") { blockPushMode { goal(3, 3) } }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        for (system in ir.systems) {
            if (
                system is GenericSystem && (system.id == "match_grid" || system.id == "push_grid")
            ) {
                assertEquals(
                    "puzzle_grid",
                    system.config["type"],
                    "System ${system.id} must have type 'puzzle_grid'",
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Grid dimensions
    // -------------------------------------------------------------------------

    @Test
    fun `grid dimensions are captured correctly`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") { size(8, 10) }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        assertEquals(8, system.config["width"])
        assertEquals(10, system.config["height"])
    }

    @Test
    fun `default grid dimensions are 6x6`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") {}
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        assertEquals(6, system.config["width"])
        assertEquals(6, system.config["height"])
    }

    // -------------------------------------------------------------------------
    // Match mode — min match length and gravity direction
    // -------------------------------------------------------------------------

    @Test
    fun `match mode builder with min match length 4`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") { matchMode { minMatchLength(4) } }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        assertEquals(PuzzleMode.MATCH, system.config["mode"])
        val matchConfig = system.config["matchConfig"] as MatchConfig
        assertEquals(4, matchConfig.minMatchLength)
    }

    @Test
    fun `match mode builder with upward gravity`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") { matchMode { gravity(GravityDirection.UP) } }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        val matchConfig = system.config["matchConfig"] as MatchConfig
        assertEquals(GravityDirection.UP, matchConfig.gravityDirection)
    }

    @Test
    fun `match mode builder with no gravity`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") { matchMode { gravity(GravityDirection.NONE) } }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        val matchConfig = system.config["matchConfig"] as MatchConfig
        assertEquals(GravityDirection.NONE, matchConfig.gravityDirection)
    }

    @Test
    fun `match mode default gravity is DOWN`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") { matchMode {} }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        val matchConfig = system.config["matchConfig"] as MatchConfig
        assertEquals(GravityDirection.DOWN, matchConfig.gravityDirection)
    }

    // -------------------------------------------------------------------------
    // Block-push mode — goal tiles and undo stack
    // -------------------------------------------------------------------------

    @Test
    fun `block-push mode builder with goal tiles`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") {
                        blockPushMode {
                            goal(3, 3)
                            goal(4, 4)
                        }
                    }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        assertEquals(PuzzleMode.BLOCK_PUSH, system.config["mode"])
        val blockPushConfig =
            system.config["blockPushConfig"] as io.github.gbkt.genre.puzzle.domain.BlockPushConfig
        assertEquals(2, blockPushConfig.goalTiles.size)
        assertEquals(Pair(3, 3), blockPushConfig.goalTiles[0])
        assertEquals(Pair(4, 4), blockPushConfig.goalTiles[1])
    }

    @Test
    fun `block-push undo is unlimited by default`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") { blockPushMode {} }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        val blockPushConfig =
            system.config["blockPushConfig"] as io.github.gbkt.genre.puzzle.domain.BlockPushConfig
        assertTrue(blockPushConfig.undoEnabled)
        assertEquals(Int.MAX_VALUE, blockPushConfig.undoMaxDepth)
    }

    @Test
    fun `block-push undo stack depth can be limited by developer`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") { blockPushMode { undoDepth(20) } }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        val blockPushConfig =
            system.config["blockPushConfig"] as io.github.gbkt.genre.puzzle.domain.BlockPushConfig
        assertEquals(20, blockPushConfig.undoMaxDepth)
    }

    @Test
    fun `block-push undo can be disabled`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") { blockPushMode { disableUndo() } }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        val blockPushConfig =
            system.config["blockPushConfig"] as io.github.gbkt.genre.puzzle.domain.BlockPushConfig
        assertFalse(blockPushConfig.undoEnabled)
    }

    // -------------------------------------------------------------------------
    // Custom cell type registration
    // -------------------------------------------------------------------------

    @Test
    fun `custom cell type registration — bomb`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") {
                        cellType("bomb") {
                            name("Bomb")
                            behavior(CellBehavior.BOMB)
                        }
                    }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        @Suppress("UNCHECKED_CAST")
        val types =
            system.config["customCellTypes"]
                as List<io.github.gbkt.genre.puzzle.domain.CustomCellType>
        assertEquals(1, types.size)
        assertEquals("bomb", types[0].id)
        assertEquals("Bomb", types[0].name)
        assertEquals(CellBehavior.BOMB, types[0].behavior)
    }

    @Test
    fun `custom cell type registration — wildcard`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") {
                        cellType("wild") {
                            name("Wildcard")
                            behavior(CellBehavior.WILDCARD)
                        }
                    }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        @Suppress("UNCHECKED_CAST")
        val types =
            system.config["customCellTypes"]
                as List<io.github.gbkt.genre.puzzle.domain.CustomCellType>
        assertEquals(CellBehavior.WILDCARD, types[0].behavior)
    }

    @Test
    fun `custom cell type registration — ice`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") {
                        cellType("ice") {
                            name("Ice")
                            behavior(CellBehavior.ICE)
                        }
                    }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        @Suppress("UNCHECKED_CAST")
        val types =
            system.config["customCellTypes"]
                as List<io.github.gbkt.genre.puzzle.domain.CustomCellType>
        assertEquals(CellBehavior.ICE, types[0].behavior)
    }

    @Test
    fun `multiple custom cell types can be registered`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") {
                        cellType("bomb") { behavior(CellBehavior.BOMB) }
                        cellType("wild") { behavior(CellBehavior.WILDCARD) }
                        cellType("ice") { behavior(CellBehavior.ICE) }
                    }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        @Suppress("UNCHECKED_CAST")
        val types =
            system.config["customCellTypes"]
                as List<io.github.gbkt.genre.puzzle.domain.CustomCellType>
        assertEquals(3, types.size)
    }

    // -------------------------------------------------------------------------
    // Timer — countdown and elapsed modes
    // -------------------------------------------------------------------------

    @Test
    fun `timer countdown mode is configured correctly`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") { timer { countdown(durationFrames = 1800) } }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        val timer = system.config["timer"] as io.github.gbkt.genre.puzzle.domain.TimerConfig
        assertEquals(TimerMode.COUNTDOWN, timer.mode)
        assertEquals(1800, timer.durationFrames)
    }

    @Test
    fun `timer elapsed mode is configured correctly`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") { timer { elapsed() } }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        val timer = system.config["timer"] as io.github.gbkt.genre.puzzle.domain.TimerConfig
        assertEquals(TimerMode.ELAPSED, timer.mode)
    }

    @Test
    fun `timer is absent from config map when not configured`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") {}
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        // timer key is absent from map when not configured (no nullable Any values in config)
        assertFalse(
            system.config.containsKey("timer"),
            "timer key must not be present when not configured",
        )
    }

    // -------------------------------------------------------------------------
    // Move counter flag
    // -------------------------------------------------------------------------

    @Test
    fun `move counter is disabled by default`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") {}
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        assertEquals(false, system.config["moveCounterEnabled"])
    }

    @Test
    fun `move counter can be enabled`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") { moveCounter(enabled = true) }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        assertEquals(true, system.config["moveCounterEnabled"])
    }

    // -------------------------------------------------------------------------
    // Mode switch between MATCH and BLOCK_PUSH
    // -------------------------------------------------------------------------

    @Test
    fun `mode switch from default MATCH to BLOCK_PUSH`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") { blockPushMode {} }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        assertEquals(PuzzleMode.BLOCK_PUSH, system.config["mode"])
    }

    @Test
    fun `default mode is MATCH`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") {}
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        assertEquals(PuzzleMode.MATCH, system.config["mode"])
    }

    // -------------------------------------------------------------------------
    // Scoring configuration
    // -------------------------------------------------------------------------

    @Test
    fun `scoring config is captured in GenericSystem config map`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid("grid") {
                        scoring {
                            baseScore(200)
                            chainMultiplier(2.0f)
                            moveBonus(500)
                            timeBonus(1000)
                        }
                    }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" } as GenericSystem
        val scoring =
            system.config["scoringConfig"] as io.github.gbkt.genre.puzzle.domain.PuzzleScoringConfig
        assertEquals(200, scoring.baseScore)
        assertEquals(2.0f, scoring.chainMultiplier)
        assertEquals(500, scoring.moveBonus)
        assertEquals(1000, scoring.timeBonus)
    }

    // -------------------------------------------------------------------------
    // Minimal happy path (smoke test)
    // -------------------------------------------------------------------------

    @Test
    fun `minimal puzzleGrid usage compiles and builds successfully`() {
        val ir =
            game("MinimalPuzzle") {
                    puzzleGrid("grid") {}
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        assertNotNull(ir)
        assertEquals("MinimalPuzzle", ir.name)
        assertTrue(ir.systems.any { it.id == "grid" })
    }

    @Test
    fun `puzzleGrid with id default compiles successfully`() {
        val ir =
            game("PuzzleTest") {
                    puzzleGrid { matchMode { minMatchLength(3) } }
                    scene("start") { enter {} }
                    start = "start"
                }
                .build()

        val system = ir.systems.find { it.id == "grid" }
        assertNotNull(system, "Expected system with default id 'grid'")
    }
}
