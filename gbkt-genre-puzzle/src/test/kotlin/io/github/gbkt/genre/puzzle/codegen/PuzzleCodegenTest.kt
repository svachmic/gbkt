/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.puzzle.codegen

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.genre.puzzle.domain.BlockPushConfig
import io.github.gbkt.genre.puzzle.domain.CellBehavior
import io.github.gbkt.genre.puzzle.domain.CustomCellType
import io.github.gbkt.genre.puzzle.domain.GravityDirection
import io.github.gbkt.genre.puzzle.domain.MatchConfig
import io.github.gbkt.genre.puzzle.domain.PuzzleGridConfig
import io.github.gbkt.genre.puzzle.domain.PuzzleMode
import io.github.gbkt.genre.puzzle.domain.TimerConfig
import io.github.gbkt.genre.puzzle.domain.TimerMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// PUZZLE CODEGEN TESTS (Plan 06.8-11 Task 2 success criteria)
//
// 6 tests verifying PuzzleVisitor "puzzle_grid" branch via GBDKPipeline:
//   - Match mode: grid init, match check, gravity, chain functions generated
//   - Block-push mode: push, undo, goal check functions generated
//   - Undo stack array sized correctly to undoMaxDepth * gridSize
//   - Timer countdown function generated
//   - Custom cell type enum generated with behavior dispatch
//   - Grid var decl sized to W*H
// =============================================================================

/** Build a minimal GameIR carrying a puzzle_grid GenericSystem. */
private fun buildPuzzleGameIR(
    config: PuzzleGridConfig = PuzzleGridConfig(id = "main"),
    id: String = "main",
): GameIR {
    val system =
        GenericSystem(id = id, config = mapOf("type" to "puzzle_grid", "puzzleConfig" to config))
    return GameIR(
        name = "TestPuzzleGame",
        config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY, romBanks = 2),
        scenes = listOf(SceneIR(id = "gameplay")),
        systems = listOf(system),
        startScene = "gameplay",
    )
}

class PuzzleCodegenTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // Test 1: Match mode generates grid init, match check, gravity, chain functions
    // =========================================================================

    @Test
    fun `match mode generates grid init match check gravity and chain functions`() {
        val config =
            PuzzleGridConfig(
                id = "match",
                mode = PuzzleMode.MATCH,
                width = 6,
                height = 6,
                matchConfig =
                    MatchConfig(
                        minMatchLength = 3,
                        gravityDirection = GravityDirection.DOWN,
                        chainMultiplier = 1.5f,
                    ),
            )
        val gameIR = buildPuzzleGameIR(config, "match")
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("puzzle_init_grid_match"),
            "Expected 'puzzle_init_grid_match' function in generated C",
        )
        assertTrue(
            mainC.contains("puzzle_check_match_match"),
            "Expected 'puzzle_check_match_match' function in generated C",
        )
        assertTrue(
            mainC.contains("puzzle_apply_gravity_match"),
            "Expected 'puzzle_apply_gravity_match' function in generated C",
        )
        assertTrue(
            mainC.contains("puzzle_update_chain_match"),
            "Expected 'puzzle_update_chain_match' function in generated C",
        )
    }

    // =========================================================================
    // Test 2: Block-push mode generates push and undo functions
    // =========================================================================

    @Test
    fun `block push mode generates push block and undo functions`() {
        val config =
            PuzzleGridConfig(
                id = "sokoban",
                mode = PuzzleMode.BLOCK_PUSH,
                width = 8,
                height = 8,
                blockPushConfig =
                    BlockPushConfig(
                        goalTiles = listOf(3 to 3, 5 to 5),
                        undoEnabled = true,
                        undoMaxDepth = 10,
                    ),
            )
        val gameIR = buildPuzzleGameIR(config, "sokoban")
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("puzzle_push_block_sokoban"),
            "Expected 'puzzle_push_block_sokoban' function in generated C",
        )
        assertTrue(
            mainC.contains("puzzle_undo_sokoban"),
            "Expected 'puzzle_undo_sokoban' function in generated C",
        )
        // Match functions must NOT be generated in block-push mode
        assertFalse(
            mainC.contains("puzzle_check_match_sokoban"),
            "BLOCK_PUSH mode should NOT generate 'puzzle_check_match_sokoban'",
        )
    }

    // =========================================================================
    // Test 3: Undo stack array sized correctly (undoMaxDepth * gridSize, capped at 16)
    // =========================================================================

    @Test
    fun `undo stack array sized correctly to undo max depth times grid size`() {
        val config =
            PuzzleGridConfig(
                id = "undo",
                mode = PuzzleMode.BLOCK_PUSH,
                width = 4,
                height = 4, // gridSize = 16
                blockPushConfig =
                    BlockPushConfig(
                        undoEnabled = true,
                        undoMaxDepth = 8, // capped at 16, use 8 → stack = 8 * 16 = 128 cells
                    ),
            )
        val gameIR = buildPuzzleGameIR(config, "undo")
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_puzzle_undo_stack_undo"),
            "Expected '_puzzle_undo_stack_undo' undo stack array in generated C",
        )
        assertTrue(
            mainC.contains("_puzzle_undo_top_undo"),
            "Expected '_puzzle_undo_top_undo' stack pointer in generated C",
        )
    }

    // =========================================================================
    // Test 4: Timer countdown function generated
    // =========================================================================

    @Test
    fun `timer countdown function generated`() {
        val config =
            PuzzleGridConfig(
                id = "timed",
                mode = PuzzleMode.MATCH,
                width = 6,
                height = 6,
                timer = TimerConfig(mode = TimerMode.COUNTDOWN, durationFrames = 1800),
            )
        val gameIR = buildPuzzleGameIR(config, "timed")
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("puzzle_update_timer_timed"),
            "Expected 'puzzle_update_timer_timed' function in generated C",
        )
        assertTrue(
            mainC.contains("_puzzle_timer_timed"),
            "Expected '_puzzle_timer_timed' timer variable in generated C",
        )
    }

    // =========================================================================
    // Test 5: Custom cell type dispatch generated
    // =========================================================================

    @Test
    fun `custom cell type enum generated with behavior dispatch`() {
        val config =
            PuzzleGridConfig(
                id = "custom",
                mode = PuzzleMode.MATCH,
                width = 6,
                height = 6,
                customCellTypes =
                    listOf(
                        CustomCellType(
                            id = "bomb_cell",
                            name = "Bomb",
                            behavior = CellBehavior.BOMB,
                        ),
                        CustomCellType(id = "ice_cell", name = "Ice", behavior = CellBehavior.ICE),
                    ),
            )
        val gameIR = buildPuzzleGameIR(config, "custom")
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("puzzle_check_cell_type_custom"),
            "Expected 'puzzle_check_cell_type_custom' function in generated C",
        )
        // Custom cell types generate BOMB and ICE dispatch comments
        assertTrue(mainC.contains("BOMB"), "Expected BOMB behavior comment in cell type dispatch")
        assertTrue(mainC.contains("ICE"), "Expected ICE behavior comment in cell type dispatch")
    }

    // =========================================================================
    // Test 6: Grid var decl sized to W*H
    // =========================================================================

    @Test
    fun `grid var decl sized to width times height`() {
        val config =
            PuzzleGridConfig(
                id = "grid5x4",
                mode = PuzzleMode.MATCH,
                width = 5,
                height = 4, // gridSize = 20
            )
        val gameIR = buildPuzzleGameIR(config, "grid5x4")
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_puzzle_grid_grid5x4"),
            "Expected '_puzzle_grid_grid5x4' array in generated C",
        )
        // Timer should NOT be generated when not configured
        assertFalse(
            mainC.contains("_puzzle_timer_grid5x4"),
            "No timer configured — should NOT generate '_puzzle_timer_grid5x4'",
        )
    }

    // =========================================================================
    // Test F-034: Match detection clears matched cells at end of run
    // =========================================================================

    @Test
    fun `match detection clears matched cells at end of run`() {
        val config =
            PuzzleGridConfig(
                id = "clrmatch",
                mode = PuzzleMode.MATCH,
                width = 6,
                height = 6,
                matchConfig =
                    MatchConfig(minMatchLength = 3, gravityDirection = GravityDirection.DOWN),
            )
        val gameIR = buildPuzzleGameIR(config, "clrmatch")
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // The check_match function must declare run_start for tracking run begin
        assertTrue(
            mainC.contains("run_start"),
            "puzzle_check_match must declare 'run_start' variable for end-of-run detection. mainC snippet: " +
                mainC
                    .lines()
                    .filter { it.contains("puzzle_check_match") || it.contains("run_start") }
                    .take(10),
        )

        // The check_match function must clear matched cells (assign 0 to grid cells)
        // We look for clearing-loop pattern with "= 0" inside puzzle_check_match_clrmatch
        val checkMatchSection =
            mainC
                .substringAfter("puzzle_check_match_clrmatch")
                .substringBefore("puzzle_apply_gravity_clrmatch")
        assertTrue(
            checkMatchSection.contains("= 0"),
            "puzzle_check_match must clear matched cells (assign 0). Section: ${checkMatchSection.take(500)}",
        )
    }

    // =========================================================================
    // Test F-035: Gravity convergence loop (DOWN and UP)
    // =========================================================================

    @Test
    fun `gravity DOWN has convergence loop`() {
        val config =
            PuzzleGridConfig(
                id = "grav_down",
                mode = PuzzleMode.MATCH,
                width = 6,
                height = 6,
                matchConfig =
                    MatchConfig(minMatchLength = 3, gravityDirection = GravityDirection.DOWN),
            )
        val gameIR = buildPuzzleGameIR(config, "grav_down")
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // The gravity function must contain a swapped variable for convergence
        val gravitySection =
            mainC
                .substringAfter("puzzle_apply_gravity_grav_down")
                .substringBefore("puzzle_update_chain_grav_down")
        assertTrue(
            gravitySection.contains("swapped"),
            "puzzle_apply_gravity DOWN must use 'swapped' convergence variable. Section: ${gravitySection.take(500)}",
        )
        assertTrue(
            gravitySection.contains("while"),
            "puzzle_apply_gravity DOWN must use a while(1) convergence loop. Section: ${gravitySection.take(500)}",
        )
    }

    @Test
    fun `gravity UP has convergence loop`() {
        val config =
            PuzzleGridConfig(
                id = "grav_up",
                mode = PuzzleMode.MATCH,
                width = 6,
                height = 6,
                matchConfig =
                    MatchConfig(minMatchLength = 3, gravityDirection = GravityDirection.UP),
            )
        val gameIR = buildPuzzleGameIR(config, "grav_up")
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // The gravity function must contain a swapped variable for convergence
        val gravitySection =
            mainC
                .substringAfter("puzzle_apply_gravity_grav_up")
                .substringBefore("puzzle_update_chain_grav_up")
        assertTrue(
            gravitySection.contains("swapped"),
            "puzzle_apply_gravity UP must use 'swapped' convergence variable. Section: ${gravitySection.take(500)}",
        )
        assertTrue(
            gravitySection.contains("while"),
            "puzzle_apply_gravity UP must use a while(1) convergence loop. Section: ${gravitySection.take(500)}",
        )
    }
}
