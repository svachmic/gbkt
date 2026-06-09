/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.genre.puzzle.codegen

import io.github.gbkt.backend.api.GenreSystemVisitor
import io.github.gbkt.backend.api.GenreVisitorResult
import io.github.gbkt.backend.gbdk.codegen.ast.CArray
import io.github.gbkt.backend.gbdk.codegen.ast.CArrayAccess
import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CBlock
import io.github.gbkt.backend.gbdk.codegen.ast.CBreak
import io.github.gbkt.backend.gbdk.codegen.ast.CComment
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFor
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CI8
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CParam
import io.github.gbkt.backend.gbdk.codegen.ast.CReturn
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CSwitch
import io.github.gbkt.backend.gbdk.codegen.ast.CSwitchCase
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CUnaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.backend.gbdk.codegen.ast.CVoid
import io.github.gbkt.backend.gbdk.codegen.ast.CWhile
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.genre.puzzle.domain.BlockPushConfig
import io.github.gbkt.genre.puzzle.domain.CellBehavior
import io.github.gbkt.genre.puzzle.domain.CustomCellType
import io.github.gbkt.genre.puzzle.domain.GravityDirection
import io.github.gbkt.genre.puzzle.domain.MatchConfig
import io.github.gbkt.genre.puzzle.domain.PuzzleGridConfig
import io.github.gbkt.genre.puzzle.domain.PuzzleMode
import io.github.gbkt.genre.puzzle.domain.TimerConfig
import io.github.gbkt.genre.puzzle.domain.TimerMode

// =============================================================================
// PUZZLE VISITOR
// Implements GenreSystemVisitor for the "puzzle_grid" system type.
// Generates C functions and variable declarations for:
//   - Match-3 mode: grid init, match check, gravity, chain update
//   - Block-push mode (Sokoban-style): push validation, undo stack
//   - Timer: countdown and elapsed modes
//   - Custom cell types: enum dispatch via switch
// =============================================================================

/**
 * GenreSystemVisitor implementation for puzzle grid systems (`puzzle_grid` type).
 *
 * Registered via ServiceLoader from `gbkt-genre-puzzle` so that `GBDKPipeline` discovers it at
 * runtime without a compile-time dependency on this module.
 *
 * Handles [PuzzleGridConfig] systems produced by
 * [io.github.gbkt.genre.puzzle.dsl.PuzzleExtensions].
 */
class PuzzleVisitor : GenreSystemVisitor {

    companion object {
        const val SYSTEM_TYPE = "puzzle_grid"
    }

    override fun canHandle(systemType: String): Boolean = systemType == SYSTEM_TYPE

    override fun visit(
        systemType: String,
        systemConfig: Map<String, Any>,
        gameIR: GameIR,
    ): GenreVisitorResult {
        val config =
            systemConfig["puzzleConfig"] as? PuzzleGridConfig ?: PuzzleGridConfig(id = "puzzle")
        val id = config.id
        val gridSize = config.width * config.height

        val varDecls = buildVarDecls(id, gridSize, config)
        val functions = buildFunctions(id, gridSize, config)

        return GenreVisitorResult(functions = functions, varDecls = varDecls)
    }

    // =========================================================================
    // Variable declarations
    // =========================================================================

    private fun buildVarDecls(id: String, gridSize: Int, config: PuzzleGridConfig): List<CVarDecl> {
        val decls = mutableListOf<CVarDecl>()

        // Grid state array: _puzzle_grid_<id>[W*H]
        decls.add(CVarDecl(name = "_puzzle_grid_$id", type = CArray(CU8, gridSize)))

        // Chain counter (match mode only)
        decls.add(CVarDecl(name = "_puzzle_chain_count_$id", type = CU8))

        // Score
        decls.add(CVarDecl(name = "_puzzle_score_$id", type = CU8))

        // Move counter (always generated; used in both modes)
        decls.add(CVarDecl(name = "_puzzle_moves_$id", type = CU8))

        // Timer (only when timer is configured)
        if (config.timer != null) {
            decls.add(CVarDecl(name = "_puzzle_timer_$id", type = CU8))
        }

        // Undo stack (block-push mode only, when undo is enabled)
        if (config.mode == PuzzleMode.BLOCK_PUSH && config.blockPushConfig.undoEnabled) {
            val undoDepth = config.blockPushConfig.undoMaxDepth
            decls.add(
                CVarDecl(name = "_puzzle_undo_stack_$id", type = CArray(CU8, undoDepth * gridSize))
            )
            decls.add(CVarDecl(name = "_puzzle_undo_top_$id", type = CU8))
        }

        return decls
    }

    // =========================================================================
    // Function generation
    // =========================================================================

    private fun buildFunctions(
        id: String,
        gridSize: Int,
        config: PuzzleGridConfig,
    ): List<CFunction> {
        val functions = mutableListOf<CFunction>()

        // Always present: grid init
        functions.add(buildInitGrid(id, gridSize))

        when (config.mode) {
            PuzzleMode.MATCH -> {
                functions.add(buildCheckMatch(id, config.width, config.height, config.matchConfig))
                functions.add(
                    buildApplyGravity(id, config.width, config.height, config.matchConfig)
                )
                functions.add(buildUpdateChain(id))
            }
            PuzzleMode.BLOCK_PUSH -> {
                functions.add(buildPushBlock(id, config.width, config.height))
                if (config.blockPushConfig.undoEnabled) {
                    functions.add(
                        buildSave(id, config.width, config.height, config.blockPushConfig)
                    )
                    functions.add(
                        buildUndo(id, config.width, config.height, config.blockPushConfig)
                    )
                }
            }
        }

        // Timer update (present when timer is configured)
        if (config.timer != null) {
            functions.add(buildUpdateTimer(id, config.timer))
        }

        // Cell type dispatch (present when custom cell types are registered)
        if (config.customCellTypes.isNotEmpty()) {
            functions.add(buildCheckCellType(id, config.width, config.customCellTypes))
        }

        return functions
    }

    // =========================================================================
    // puzzle_init_grid_<id>()
    // =========================================================================

    private fun buildInitGrid(id: String, gridSize: Int): CFunction {
        val body = mutableListOf<CStatement>()
        body.add(CComment("Zero-fill grid state array"))
        // for (UINT8 i = 0; i < gridSize; i++) { _puzzle_grid_<id>[i] = 0; }
        body.add(
            CFor(
                init = CVarDecl(name = "i", type = CU8, initializer = CLiteral(0)),
                condition = CBinaryExpr(CVar("i"), "<", CLiteral(gridSize)),
                increment = CUnaryExpr("++", CVar("i")),
                body =
                    listOf(
                        CExprStatement(
                            CBinaryExpr(
                                CArrayAccess(CVar("_puzzle_grid_$id"), CVar("i")),
                                "=",
                                CLiteral(0),
                            )
                        )
                    ),
            )
        )
        body.add(CExprStatement(CBinaryExpr(CVar("_puzzle_chain_count_$id"), "=", CLiteral(0))))
        body.add(CExprStatement(CBinaryExpr(CVar("_puzzle_score_$id"), "=", CLiteral(0))))
        body.add(CExprStatement(CBinaryExpr(CVar("_puzzle_moves_$id"), "=", CLiteral(0))))
        return CFunction(
            name = "puzzle_init_grid_$id",
            returnType = CVoid,
            body = body,
            sectionComment = "Puzzle: init grid $id",
        )
    }

    // =========================================================================
    // puzzle_check_match_<id>() — match-3 mode
    // =========================================================================

    @Suppress("UnusedParameter")
    private fun buildCheckMatch(
        id: String,
        width: Int,
        height: Int,
        matchConfig: MatchConfig,
    ): CFunction {
        val minMatch = matchConfig.minMatchLength
        val body = mutableListOf<CStatement>()
        body.add(
            CComment(
                "Match detection with end-of-run cell clearing (minMatch=$minMatch). " +
                    "Returns 1 if matches found (and cleared), 0 otherwise."
            )
        )
        body.add(CVarDecl(name = "found", type = CU8, initializer = CLiteral(0)))
        body.add(CVarDecl(name = "run", type = CU8))
        body.add(CVarDecl(name = "run_start", type = CU8))
        body.add(CVarDecl(name = "cur", type = CU8))

        // Helper: build a clearing loop for horizontal run
        // for (UINT8 k = run_start; k < run_start + run; k++) { grid[r * width + k] = 0; }
        fun buildHorizClearLoop(rowVar: String): CFor =
            CFor(
                init = CVarDecl(name = "k", type = CU8, initializer = CVar("run_start")),
                condition =
                    CBinaryExpr(CVar("k"), "<", CBinaryExpr(CVar("run_start"), "+", CVar("run"))),
                increment = CUnaryExpr("++", CVar("k")),
                body =
                    listOf(
                        CExprStatement(
                            CBinaryExpr(
                                CArrayAccess(
                                    CVar("_puzzle_grid_$id"),
                                    CBinaryExpr(
                                        CBinaryExpr(CVar(rowVar), "*", CLiteral(width)),
                                        "+",
                                        CVar("k"),
                                    ),
                                ),
                                "=",
                                CLiteral(0),
                            )
                        )
                    ),
            )

        // Helper: build a clearing loop for vertical run
        // for (UINT8 k = run_start; k < run_start + run; k++) { grid[k * width + c] = 0; }
        fun buildVertClearLoop(colVar: String): CFor =
            CFor(
                init = CVarDecl(name = "k", type = CU8, initializer = CVar("run_start")),
                condition =
                    CBinaryExpr(CVar("k"), "<", CBinaryExpr(CVar("run_start"), "+", CVar("run"))),
                increment = CUnaryExpr("++", CVar("k")),
                body =
                    listOf(
                        CExprStatement(
                            CBinaryExpr(
                                CArrayAccess(
                                    CVar("_puzzle_grid_$id"),
                                    CBinaryExpr(
                                        CBinaryExpr(CVar("k"), "*", CLiteral(width)),
                                        "+",
                                        CVar(colVar),
                                    ),
                                ),
                                "=",
                                CLiteral(0),
                            )
                        )
                    ),
            )

        // Helper: CIf that clears run and sets found=1
        fun buildEndOfRunCheck(clearLoop: CFor): CIf =
            CIf(
                condition = CBinaryExpr(CVar("run"), ">=", CLiteral(minMatch)),
                thenBody =
                    listOf(clearLoop, CExprStatement(CBinaryExpr(CVar("found"), "=", CLiteral(1)))),
            )

        // Horizontal scan — end-of-run clearing
        body.add(CComment("Horizontal scan: detect and clear runs at end-of-run"))
        body.add(
            CFor(
                init = CVarDecl(name = "r", type = CU8, initializer = CLiteral(0)),
                condition = CBinaryExpr(CVar("r"), "<", CLiteral(height)),
                increment = CUnaryExpr("++", CVar("r")),
                body =
                    listOf(
                        // run = 1; run_start = 0
                        CExprStatement(CBinaryExpr(CVar("run"), "=", CLiteral(1))),
                        CExprStatement(CBinaryExpr(CVar("run_start"), "=", CLiteral(0))),
                        CFor(
                            init = CVarDecl(name = "c", type = CU8, initializer = CLiteral(1)),
                            condition = CBinaryExpr(CVar("c"), "<", CLiteral(width)),
                            increment = CUnaryExpr("++", CVar("c")),
                            body =
                                listOf(
                                    // cur = grid[r * width + c]
                                    CExprStatement(
                                        CBinaryExpr(
                                            CVar("cur"),
                                            "=",
                                            CArrayAccess(
                                                CVar("_puzzle_grid_$id"),
                                                CBinaryExpr(
                                                    CBinaryExpr(CVar("r"), "*", CLiteral(width)),
                                                    "+",
                                                    CVar("c"),
                                                ),
                                            ),
                                        )
                                    ),
                                    CIf(
                                        // if cur == grid[r * width + (c-1)]: run++
                                        condition =
                                            CBinaryExpr(
                                                CVar("cur"),
                                                "==",
                                                CArrayAccess(
                                                    CVar("_puzzle_grid_$id"),
                                                    CBinaryExpr(
                                                        CBinaryExpr(
                                                            CVar("r"),
                                                            "*",
                                                            CLiteral(width),
                                                        ),
                                                        "+",
                                                        CBinaryExpr(CVar("c"), "-", CLiteral(1)),
                                                    ),
                                                ),
                                            ),
                                        thenBody =
                                            listOf(CExprStatement(CUnaryExpr("++", CVar("run")))),
                                        // else: end-of-run detected
                                        elseBody =
                                            listOf(
                                                buildEndOfRunCheck(buildHorizClearLoop("r")),
                                                // reset run and run_start
                                                CExprStatement(
                                                    CBinaryExpr(CVar("run"), "=", CLiteral(1))
                                                ),
                                                CExprStatement(
                                                    CBinaryExpr(CVar("run_start"), "=", CVar("c"))
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                        // After inner loop: check the last run in the row
                        buildEndOfRunCheck(buildHorizClearLoop("r")),
                    ),
            )
        )

        // Vertical scan — end-of-run clearing
        body.add(CComment("Vertical scan: detect and clear runs at end-of-run"))
        body.add(
            CFor(
                init = CVarDecl(name = "c2", type = CU8, initializer = CLiteral(0)),
                condition = CBinaryExpr(CVar("c2"), "<", CLiteral(width)),
                increment = CUnaryExpr("++", CVar("c2")),
                body =
                    listOf(
                        CExprStatement(CBinaryExpr(CVar("run"), "=", CLiteral(1))),
                        CExprStatement(CBinaryExpr(CVar("run_start"), "=", CLiteral(0))),
                        CFor(
                            init = CVarDecl(name = "r2", type = CU8, initializer = CLiteral(1)),
                            condition = CBinaryExpr(CVar("r2"), "<", CLiteral(height)),
                            increment = CUnaryExpr("++", CVar("r2")),
                            body =
                                listOf(
                                    // cur = grid[r2 * width + c2]
                                    CExprStatement(
                                        CBinaryExpr(
                                            CVar("cur"),
                                            "=",
                                            CArrayAccess(
                                                CVar("_puzzle_grid_$id"),
                                                CBinaryExpr(
                                                    CBinaryExpr(CVar("r2"), "*", CLiteral(width)),
                                                    "+",
                                                    CVar("c2"),
                                                ),
                                            ),
                                        )
                                    ),
                                    CIf(
                                        condition =
                                            CBinaryExpr(
                                                CVar("cur"),
                                                "==",
                                                CArrayAccess(
                                                    CVar("_puzzle_grid_$id"),
                                                    CBinaryExpr(
                                                        CBinaryExpr(
                                                            CBinaryExpr(
                                                                CVar("r2"),
                                                                "-",
                                                                CLiteral(1),
                                                            ),
                                                            "*",
                                                            CLiteral(width),
                                                        ),
                                                        "+",
                                                        CVar("c2"),
                                                    ),
                                                ),
                                            ),
                                        thenBody =
                                            listOf(CExprStatement(CUnaryExpr("++", CVar("run")))),
                                        elseBody =
                                            listOf(
                                                buildEndOfRunCheck(buildVertClearLoop("c2")),
                                                CExprStatement(
                                                    CBinaryExpr(CVar("run"), "=", CLiteral(1))
                                                ),
                                                CExprStatement(
                                                    CBinaryExpr(CVar("run_start"), "=", CVar("r2"))
                                                ),
                                            ),
                                    ),
                                ),
                        ),
                        // After inner loop: check the last run in the column
                        buildEndOfRunCheck(buildVertClearLoop("c2")),
                    ),
            )
        )

        body.add(CReturn(CVar("found")))
        return CFunction(name = "puzzle_check_match_$id", returnType = CU8, body = body)
    }

    // =========================================================================
    // puzzle_apply_gravity_<id>() — match-3 mode
    // =========================================================================

    private fun buildApplyGravity(
        id: String,
        width: Int,
        height: Int,
        matchConfig: MatchConfig,
    ): CFunction {
        val body = mutableListOf<CStatement>()
        body.add(CComment("Apply gravity direction: ${matchConfig.gravityDirection}"))

        when (matchConfig.gravityDirection) {
            GravityDirection.DOWN -> {
                // swapped variable for convergence loop
                body.add(CVarDecl(name = "swapped", type = CU8, initializer = CLiteral(0)))
                // while(1) convergence: repeat until no swaps occur in a full pass
                body.add(
                    CWhile(
                        condition = CLiteral(1),
                        body =
                            listOf(
                                CExprStatement(CBinaryExpr(CVar("swapped"), "=", CLiteral(0))),
                                // For each column, bubble non-zero cells downward
                                CFor(
                                    init =
                                        CVarDecl(name = "c", type = CU8, initializer = CLiteral(0)),
                                    condition = CBinaryExpr(CVar("c"), "<", CLiteral(width)),
                                    increment = CUnaryExpr("++", CVar("c")),
                                    body =
                                        listOf(
                                            CFor(
                                                init =
                                                    CVarDecl(
                                                        name = "r",
                                                        type = CU8,
                                                        initializer = CLiteral(height - 1),
                                                    ),
                                                condition =
                                                    CBinaryExpr(CVar("r"), ">", CLiteral(0)),
                                                increment = CUnaryExpr("--", CVar("r")),
                                                body =
                                                    listOf(
                                                        CIf(
                                                            condition =
                                                                CBinaryExpr(
                                                                    CArrayAccess(
                                                                        CVar("_puzzle_grid_$id"),
                                                                        CBinaryExpr(
                                                                            CBinaryExpr(
                                                                                CVar("r"),
                                                                                "*",
                                                                                CLiteral(width),
                                                                            ),
                                                                            "+",
                                                                            CVar("c"),
                                                                        ),
                                                                    ),
                                                                    "==",
                                                                    CLiteral(0),
                                                                ),
                                                            thenBody =
                                                                listOf(
                                                                    CBlock(
                                                                        listOf(
                                                                            CVarDecl(
                                                                                name = "tmp",
                                                                                type = CU8,
                                                                                initializer =
                                                                                    CArrayAccess(
                                                                                        CVar(
                                                                                            "_puzzle_grid_$id"
                                                                                        ),
                                                                                        CBinaryExpr(
                                                                                            CBinaryExpr(
                                                                                                CVar(
                                                                                                    "r"
                                                                                                ),
                                                                                                "*",
                                                                                                CLiteral(
                                                                                                    width
                                                                                                ),
                                                                                            ),
                                                                                            "+",
                                                                                            CVar(
                                                                                                "c"
                                                                                            ),
                                                                                        ),
                                                                                    ),
                                                                            ),
                                                                            CExprStatement(
                                                                                CBinaryExpr(
                                                                                    CArrayAccess(
                                                                                        CVar(
                                                                                            "_puzzle_grid_$id"
                                                                                        ),
                                                                                        CBinaryExpr(
                                                                                            CBinaryExpr(
                                                                                                CVar(
                                                                                                    "r"
                                                                                                ),
                                                                                                "*",
                                                                                                CLiteral(
                                                                                                    width
                                                                                                ),
                                                                                            ),
                                                                                            "+",
                                                                                            CVar(
                                                                                                "c"
                                                                                            ),
                                                                                        ),
                                                                                    ),
                                                                                    "=",
                                                                                    CArrayAccess(
                                                                                        CVar(
                                                                                            "_puzzle_grid_$id"
                                                                                        ),
                                                                                        CBinaryExpr(
                                                                                            CBinaryExpr(
                                                                                                CBinaryExpr(
                                                                                                    CVar(
                                                                                                        "r"
                                                                                                    ),
                                                                                                    "-",
                                                                                                    CLiteral(
                                                                                                        1
                                                                                                    ),
                                                                                                ),
                                                                                                "*",
                                                                                                CLiteral(
                                                                                                    width
                                                                                                ),
                                                                                            ),
                                                                                            "+",
                                                                                            CVar(
                                                                                                "c"
                                                                                            ),
                                                                                        ),
                                                                                    ),
                                                                                )
                                                                            ),
                                                                            CExprStatement(
                                                                                CBinaryExpr(
                                                                                    CArrayAccess(
                                                                                        CVar(
                                                                                            "_puzzle_grid_$id"
                                                                                        ),
                                                                                        CBinaryExpr(
                                                                                            CBinaryExpr(
                                                                                                CBinaryExpr(
                                                                                                    CVar(
                                                                                                        "r"
                                                                                                    ),
                                                                                                    "-",
                                                                                                    CLiteral(
                                                                                                        1
                                                                                                    ),
                                                                                                ),
                                                                                                "*",
                                                                                                CLiteral(
                                                                                                    width
                                                                                                ),
                                                                                            ),
                                                                                            "+",
                                                                                            CVar(
                                                                                                "c"
                                                                                            ),
                                                                                        ),
                                                                                    ),
                                                                                    "=",
                                                                                    CVar("tmp"),
                                                                                )
                                                                            ),
                                                                            // Mark that a swap
                                                                            // occurred
                                                                            CExprStatement(
                                                                                CBinaryExpr(
                                                                                    CVar("swapped"),
                                                                                    "=",
                                                                                    CLiteral(1),
                                                                                )
                                                                            ),
                                                                        )
                                                                    )
                                                                ),
                                                        )
                                                    ),
                                            )
                                        ),
                                ),
                                // Break convergence loop if no swaps occurred
                                CIf(
                                    condition = CUnaryExpr("!", CVar("swapped")),
                                    thenBody = listOf(CBreak),
                                ),
                            ),
                    )
                )
            }
            GravityDirection.UP -> {
                // swapped variable for convergence loop
                body.add(CVarDecl(name = "swapped", type = CU8, initializer = CLiteral(0)))
                // while(1) convergence: repeat until no swaps occur in a full pass
                body.add(
                    CWhile(
                        condition = CLiteral(1),
                        body =
                            listOf(
                                CExprStatement(CBinaryExpr(CVar("swapped"), "=", CLiteral(0))),
                                // For each column, bubble non-zero cells upward
                                CFor(
                                    init =
                                        CVarDecl(name = "c", type = CU8, initializer = CLiteral(0)),
                                    condition = CBinaryExpr(CVar("c"), "<", CLiteral(width)),
                                    increment = CUnaryExpr("++", CVar("c")),
                                    body =
                                        listOf(
                                            CFor(
                                                init =
                                                    CVarDecl(
                                                        name = "r",
                                                        type = CU8,
                                                        initializer = CLiteral(0),
                                                    ),
                                                condition =
                                                    CBinaryExpr(
                                                        CVar("r"),
                                                        "<",
                                                        CLiteral(height - 1),
                                                    ),
                                                increment = CUnaryExpr("++", CVar("r")),
                                                body =
                                                    listOf(
                                                        CIf(
                                                            condition =
                                                                CBinaryExpr(
                                                                    CArrayAccess(
                                                                        CVar("_puzzle_grid_$id"),
                                                                        CBinaryExpr(
                                                                            CBinaryExpr(
                                                                                CVar("r"),
                                                                                "*",
                                                                                CLiteral(width),
                                                                            ),
                                                                            "+",
                                                                            CVar("c"),
                                                                        ),
                                                                    ),
                                                                    "==",
                                                                    CLiteral(0),
                                                                ),
                                                            thenBody =
                                                                listOf(
                                                                    CBlock(
                                                                        listOf(
                                                                            CVarDecl(
                                                                                name = "tmp",
                                                                                type = CU8,
                                                                                initializer =
                                                                                    CArrayAccess(
                                                                                        CVar(
                                                                                            "_puzzle_grid_$id"
                                                                                        ),
                                                                                        CBinaryExpr(
                                                                                            CBinaryExpr(
                                                                                                CVar(
                                                                                                    "r"
                                                                                                ),
                                                                                                "*",
                                                                                                CLiteral(
                                                                                                    width
                                                                                                ),
                                                                                            ),
                                                                                            "+",
                                                                                            CVar(
                                                                                                "c"
                                                                                            ),
                                                                                        ),
                                                                                    ),
                                                                            ),
                                                                            CExprStatement(
                                                                                CBinaryExpr(
                                                                                    CArrayAccess(
                                                                                        CVar(
                                                                                            "_puzzle_grid_$id"
                                                                                        ),
                                                                                        CBinaryExpr(
                                                                                            CBinaryExpr(
                                                                                                CVar(
                                                                                                    "r"
                                                                                                ),
                                                                                                "*",
                                                                                                CLiteral(
                                                                                                    width
                                                                                                ),
                                                                                            ),
                                                                                            "+",
                                                                                            CVar(
                                                                                                "c"
                                                                                            ),
                                                                                        ),
                                                                                    ),
                                                                                    "=",
                                                                                    CArrayAccess(
                                                                                        CVar(
                                                                                            "_puzzle_grid_$id"
                                                                                        ),
                                                                                        CBinaryExpr(
                                                                                            CBinaryExpr(
                                                                                                CBinaryExpr(
                                                                                                    CVar(
                                                                                                        "r"
                                                                                                    ),
                                                                                                    "+",
                                                                                                    CLiteral(
                                                                                                        1
                                                                                                    ),
                                                                                                ),
                                                                                                "*",
                                                                                                CLiteral(
                                                                                                    width
                                                                                                ),
                                                                                            ),
                                                                                            "+",
                                                                                            CVar(
                                                                                                "c"
                                                                                            ),
                                                                                        ),
                                                                                    ),
                                                                                )
                                                                            ),
                                                                            CExprStatement(
                                                                                CBinaryExpr(
                                                                                    CArrayAccess(
                                                                                        CVar(
                                                                                            "_puzzle_grid_$id"
                                                                                        ),
                                                                                        CBinaryExpr(
                                                                                            CBinaryExpr(
                                                                                                CBinaryExpr(
                                                                                                    CVar(
                                                                                                        "r"
                                                                                                    ),
                                                                                                    "+",
                                                                                                    CLiteral(
                                                                                                        1
                                                                                                    ),
                                                                                                ),
                                                                                                "*",
                                                                                                CLiteral(
                                                                                                    width
                                                                                                ),
                                                                                            ),
                                                                                            "+",
                                                                                            CVar(
                                                                                                "c"
                                                                                            ),
                                                                                        ),
                                                                                    ),
                                                                                    "=",
                                                                                    CVar("tmp"),
                                                                                )
                                                                            ),
                                                                            // Mark that a swap
                                                                            // occurred
                                                                            CExprStatement(
                                                                                CBinaryExpr(
                                                                                    CVar("swapped"),
                                                                                    "=",
                                                                                    CLiteral(1),
                                                                                )
                                                                            ),
                                                                        )
                                                                    )
                                                                ),
                                                        )
                                                    ),
                                            )
                                        ),
                                ),
                                // Break convergence loop if no swaps occurred
                                CIf(
                                    condition = CUnaryExpr("!", CVar("swapped")),
                                    thenBody = listOf(CBreak),
                                ),
                            ),
                    )
                )
            }
            GravityDirection.NONE -> {
                body.add(CComment("No gravity — cleared cells remain empty"))
            }
        }

        return CFunction(name = "puzzle_apply_gravity_$id", returnType = CVoid, body = body)
    }

    // =========================================================================
    // puzzle_update_chain_<id>() — match-3 mode
    // =========================================================================

    private fun buildUpdateChain(id: String): CFunction {
        val body =
            listOf<CStatement>(
                CComment("Increment chain counter; score bonus applied by game logic"),
                CExprStatement(CUnaryExpr("++", CVar("_puzzle_chain_count_$id"))),
            )
        return CFunction(name = "puzzle_update_chain_$id", returnType = CVoid, body = body)
    }

    // =========================================================================
    // puzzle_push_block_<id>(dx, dy) — block-push mode
    // =========================================================================

    private fun buildPushBlock(id: String, width: Int, height: Int): CFunction {
        val body = mutableListOf<CStatement>()
        body.add(CComment("Validate move and push block in direction (dx, dy)"))
        body.add(CVarDecl(name = "nx", type = CI8))
        body.add(CVarDecl(name = "ny", type = CI8))
        // Compute target position (uses raw player position from game state)
        body.add(
            CExprStatement(CBinaryExpr(CVar("nx"), "=", CBinaryExpr(CVar("px"), "+", CVar("dx"))))
        )
        body.add(
            CExprStatement(CBinaryExpr(CVar("ny"), "=", CBinaryExpr(CVar("py"), "+", CVar("dy"))))
        )
        // Bounds check
        body.add(
            CIf(
                condition =
                    CBinaryExpr(
                        CBinaryExpr(
                            CBinaryExpr(CVar("nx"), "<", CLiteral(0)),
                            "||",
                            CBinaryExpr(CVar("nx"), ">=", CLiteral(width)),
                        ),
                        "||",
                        CBinaryExpr(
                            CBinaryExpr(CVar("ny"), "<", CLiteral(0)),
                            "||",
                            CBinaryExpr(CVar("ny"), ">=", CLiteral(height)),
                        ),
                    ),
                thenBody = listOf(CReturn(CLiteral(0))),
            )
        )
        // Wall check: cell type 2 = WALL
        body.add(
            CIf(
                condition =
                    CBinaryExpr(
                        CArrayAccess(
                            CVar("_puzzle_grid_$id"),
                            CBinaryExpr(
                                CBinaryExpr(CVar("ny"), "*", CLiteral(width)),
                                "+",
                                CVar("nx"),
                            ),
                        ),
                        "==",
                        CLiteral(2),
                    ),
                thenBody = listOf(CReturn(CLiteral(0))),
            )
        )
        // Move block: copy source cell to destination, clear source
        body.add(CComment("Copy block from (px,py) to (nx,ny)"))
        body.add(
            CExprStatement(
                CBinaryExpr(
                    CArrayAccess(
                        CVar("_puzzle_grid_$id"),
                        CBinaryExpr(CBinaryExpr(CVar("ny"), "*", CLiteral(width)), "+", CVar("nx")),
                    ),
                    "=",
                    CArrayAccess(
                        CVar("_puzzle_grid_$id"),
                        CBinaryExpr(CBinaryExpr(CVar("py"), "*", CLiteral(width)), "+", CVar("px")),
                    ),
                )
            )
        )
        body.add(CComment("Clear source cell"))
        body.add(
            CExprStatement(
                CBinaryExpr(
                    CArrayAccess(
                        CVar("_puzzle_grid_$id"),
                        CBinaryExpr(CBinaryExpr(CVar("py"), "*", CLiteral(width)), "+", CVar("px")),
                    ),
                    "=",
                    CLiteral(0),
                )
            )
        )
        // Move accepted: increment move counter, return 1 (goal check delegated to caller)
        body.add(CExprStatement(CUnaryExpr("++", CVar("_puzzle_moves_$id"))))
        body.add(CReturn(CLiteral(1)))

        return CFunction(
            name = "puzzle_push_block_$id",
            returnType = CU8,
            params =
                listOf(CParam("px", CI8), CParam("py", CI8), CParam("dx", CI8), CParam("dy", CI8)),
            body = body,
        )
    }

    // =========================================================================
    // puzzle_save_<id>() — block-push mode, snapshot grid to undo stack
    // =========================================================================

    @Suppress("UnusedParameter")
    private fun buildSave(
        id: String,
        width: Int,
        height: Int,
        blockPushConfig: BlockPushConfig,
    ): CFunction {
        val gridSize = width * height
        val body = mutableListOf<CStatement>()
        body.add(CComment("Push current grid state onto undo stack"))
        // If undo stack is full, do nothing
        body.add(
            CIf(
                condition =
                    CBinaryExpr(
                        CVar("_puzzle_undo_top_$id"),
                        ">=",
                        CLiteral(blockPushConfig.undoMaxDepth),
                    ),
                thenBody = listOf(CReturn()),
            )
        )
        // Copy grid to stack slot at top
        body.add(
            CFor(
                init = CVarDecl(name = "i", type = CU8, initializer = CLiteral(0)),
                condition = CBinaryExpr(CVar("i"), "<", CLiteral(gridSize)),
                increment = CUnaryExpr("++", CVar("i")),
                body =
                    listOf(
                        CExprStatement(
                            CBinaryExpr(
                                CArrayAccess(
                                    CVar("_puzzle_undo_stack_$id"),
                                    CBinaryExpr(
                                        CBinaryExpr(
                                            CVar("_puzzle_undo_top_$id"),
                                            "*",
                                            CLiteral(gridSize),
                                        ),
                                        "+",
                                        CVar("i"),
                                    ),
                                ),
                                "=",
                                CArrayAccess(CVar("_puzzle_grid_$id"), CVar("i")),
                            )
                        )
                    ),
            )
        )
        body.add(CExprStatement(CUnaryExpr("++", CVar("_puzzle_undo_top_$id"))))
        return CFunction(name = "puzzle_save_$id", returnType = CVoid, body = body)
    }

    // =========================================================================
    // puzzle_undo_<id>() — block-push mode, undo enabled
    // =========================================================================

    @Suppress("UnusedParameter")
    private fun buildUndo(
        id: String,
        width: Int,
        height: Int,
        blockPushConfig: BlockPushConfig,
    ): CFunction {
        val gridSize = width * height
        val body = mutableListOf<CStatement>()
        body.add(CComment("Pop grid state from undo stack and restore"))
        // If undo stack is empty, do nothing
        body.add(
            CIf(
                condition = CBinaryExpr(CVar("_puzzle_undo_top_$id"), "==", CLiteral(0)),
                thenBody = listOf(CReturn()),
            )
        )
        body.add(CExprStatement(CUnaryExpr("--", CVar("_puzzle_undo_top_$id"))))
        // Copy from stack slot back to grid
        body.add(
            CFor(
                init = CVarDecl(name = "i", type = CU8, initializer = CLiteral(0)),
                condition = CBinaryExpr(CVar("i"), "<", CLiteral(gridSize)),
                increment = CUnaryExpr("++", CVar("i")),
                body =
                    listOf(
                        CExprStatement(
                            CBinaryExpr(
                                CArrayAccess(CVar("_puzzle_grid_$id"), CVar("i")),
                                "=",
                                CArrayAccess(
                                    CVar("_puzzle_undo_stack_$id"),
                                    CBinaryExpr(
                                        CBinaryExpr(
                                            CVar("_puzzle_undo_top_$id"),
                                            "*",
                                            CLiteral(gridSize),
                                        ),
                                        "+",
                                        CVar("i"),
                                    ),
                                ),
                            )
                        )
                    ),
            )
        )
        return CFunction(name = "puzzle_undo_$id", returnType = CVoid, body = body)
    }

    // =========================================================================
    // puzzle_update_timer_<id>() — when timer is configured
    // =========================================================================

    private fun buildUpdateTimer(id: String, timer: TimerConfig): CFunction {
        val body = mutableListOf<CStatement>()
        when (timer.mode) {
            TimerMode.COUNTDOWN -> {
                body.add(CComment("Countdown timer: decrement each frame until zero"))
                body.add(
                    CIf(
                        condition = CBinaryExpr(CVar("_puzzle_timer_$id"), ">", CLiteral(0)),
                        thenBody =
                            listOf(CExprStatement(CUnaryExpr("--", CVar("_puzzle_timer_$id")))),
                    )
                )
                // Initialize timer to duration on first call guard (caller responsibility)
            }
            TimerMode.ELAPSED -> {
                body.add(CComment("Elapsed timer: increment each frame"))
                body.add(CExprStatement(CUnaryExpr("++", CVar("_puzzle_timer_$id"))))
            }
        }
        return CFunction(name = "puzzle_update_timer_$id", returnType = CVoid, body = body)
    }

    // =========================================================================
    // puzzle_check_cell_type_<id>(x, y) — custom cell type dispatch
    // =========================================================================

    private fun buildCheckCellType(
        id: String,
        width: Int,
        customCellTypes: List<CustomCellType>,
    ): CFunction {
        val body = mutableListOf<CStatement>()
        body.add(CComment("Return cell type code at (x, y) with custom type dispatch"))
        body.add(
            CVarDecl(
                name = "cell",
                type = CU8,
                initializer =
                    CArrayAccess(
                        CVar("_puzzle_grid_$id"),
                        CBinaryExpr(CBinaryExpr(CVar("y"), "*", CLiteral(width)), "+", CVar("x")),
                    ),
            )
        )
        // Build switch cases for custom cell types
        val cases = mutableListOf<CSwitchCase>()
        customCellTypes.forEachIndexed { index, cellType ->
            val caseId = index + 10 // base custom type IDs start at 10
            val behaviorComment = "CustomCellType: ${cellType.id} behavior=${cellType.behavior}"
            val caseBody = mutableListOf<CStatement>()
            caseBody.add(CComment(behaviorComment))
            when (cellType.behavior) {
                CellBehavior.BOMB -> {
                    caseBody.add(CComment("BOMB: clear adjacent cells"))
                }
                CellBehavior.WILDCARD -> {
                    caseBody.add(CComment("WILDCARD: matches any type"))
                }
                CellBehavior.ICE -> {
                    caseBody.add(CComment("ICE: cleared by adjacent matches"))
                }
                CellBehavior.GRAVITY -> {
                    caseBody.add(CComment("GRAVITY: pulls adjacent cells"))
                }
                CellBehavior.NONE -> {
                    caseBody.add(CComment("NONE: standard cell"))
                }
            }
            caseBody.add(CReturn(CLiteral(caseId)))
            caseBody.add(CBreak)
            cases.add(CSwitchCase(value = CLiteral(caseId), body = caseBody))
        }
        // Default case: return the raw cell value
        cases.add(CSwitchCase(value = null, body = listOf(CReturn(CVar("cell")))))
        body.add(CSwitch(expr = CVar("cell"), cases = cases))
        // Unreachable return for compiler satisfaction
        body.add(CReturn(CLiteral(0)))
        return CFunction(
            name = "puzzle_check_cell_type_$id",
            returnType = CU8,
            params = listOf(CParam("x", CU8), CParam("y", CU8)),
            body = body,
        )
    }
}
