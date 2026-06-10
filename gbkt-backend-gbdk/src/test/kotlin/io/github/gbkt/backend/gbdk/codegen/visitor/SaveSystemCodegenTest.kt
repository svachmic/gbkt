/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CFor
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CRawCode
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.emit.CEmitter
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.SaveSystem
import io.github.gbkt.core.ir.VarType
import io.github.gbkt.core.ir.VariableDef
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// SAVE SYSTEM CODEGEN TESTS
// Verifies visitSaveSystem generates structured SRAM layout with typed C AST:
// - Default single-slot save/load
// - Multi-slot slot offset arithmetic
// - Transient variable exclusion
// - Optional 8-bit checksum generation
// - ENABLE_RAM/DISABLE_RAM pairing
// - Sentinel check in load function
// =============================================================================

class SaveSystemCodegenTest {

    /** Emit all statements in a function body to a single C text string. */
    private fun emitBody(stmts: List<CStatement>): String =
        stmts.joinToString("\n") { CEmitter.emitStatement(it) }

    /** Recursively collect all CRawCode strings from a statement list. */
    private fun collectRawCode(stmts: List<CStatement>): List<String> {
        val result = mutableListOf<String>()
        for (stmt in stmts) {
            when (stmt) {
                is CRawCode -> result += stmt.code
                is CIf -> {
                    result += collectRawCode(stmt.thenBody)
                    result += collectRawCode(stmt.elseBody)
                }
                is CFor -> result += collectRawCode(stmt.body)
                else -> Unit
            }
        }
        return result
    }

    /** Recursively find all CFor nodes in a statement list. */
    private fun collectForLoops(stmts: List<CStatement>): List<CFor> {
        val result = mutableListOf<CFor>()
        for (stmt in stmts) {
            when (stmt) {
                is CFor -> {
                    result += stmt
                    result += collectForLoops(stmt.body)
                }
                is CIf -> {
                    result += collectForLoops(stmt.thenBody)
                    result += collectForLoops(stmt.elseBody)
                }
                else -> Unit
            }
        }
        return result
    }

    /** Recursively find all CIf nodes in a statement list. */
    private fun collectIfs(stmts: List<CStatement>): List<CIf> {
        val result = mutableListOf<CIf>()
        for (stmt in stmts) {
            when (stmt) {
                is CIf -> {
                    result += stmt
                    result += collectIfs(stmt.thenBody)
                    result += collectIfs(stmt.elseBody)
                }
                is CFor -> result += collectIfs(stmt.body)
                else -> Unit
            }
        }
        return result
    }

    // =========================================================================
    // Test 1: Default single-slot save/load generates correct structure
    // =========================================================================

    @Test
    fun `save system with defaults generates single slot save and load functions`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                variables =
                    listOf(VariableDef("score", VarType.U8, 0), VariableDef("lives", VarType.U8, 3)),
            )
        val visitor = GBDKSystemVisitor(gameIR)
        val system = SaveSystem(id = "save")

        val functions = visitor.visitSaveSystem(system)

        assertTrue(functions.size >= 2, "Should generate at least save + load functions")
        val names = functions.map { it.name }
        assertTrue(names.any { it == "save_game_save" }, "Should have save_game_save, got: $names")
        assertTrue(names.any { it == "load_game_save" }, "Should have load_game_save, got: $names")
    }

    @Test
    fun `save system with defaults emits SRAM base 0xA000 in save function`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                variables = listOf(VariableDef("score", VarType.U8, 0)),
            )
        val visitor = GBDKSystemVisitor(gameIR)
        val system = SaveSystem(id = "save")

        val functions = visitor.visitSaveSystem(system)
        val saveGame = functions.first { it.name.contains("save_game") }
        val emitted = emitBody(saveGame.body)

        assertTrue(
            emitted.contains("0xA000"),
            "Save function should reference SRAM base 0xA000, got:\n$emitted",
        )
    }

    // =========================================================================
    // Test 2: Multi-slot generates slot index parameter and offset arithmetic
    // =========================================================================

    @Test
    fun `save system with 3 slots generates slotIndex parameter`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                variables = listOf(VariableDef("score", VarType.U8, 0)),
            )
        val visitor = GBDKSystemVisitor(gameIR)
        val system = SaveSystem(id = "saves", slots = 3)

        val functions = visitor.visitSaveSystem(system)
        val saveGame = functions.first { it.name.contains("save_game") }

        assertNotNull(
            saveGame.params.find { it.name == "slotIndex" },
            "save_game should have slotIndex parameter",
        )
        val emitted = emitBody(saveGame.body)
        assertTrue(
            emitted.contains("slotIndex"),
            "Save body should use slotIndex for slot offset arithmetic, got:\n$emitted",
        )
    }

    @Test
    fun `save system load function also accepts slotIndex parameter`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                variables = listOf(VariableDef("score", VarType.U8, 0)),
            )
        val visitor = GBDKSystemVisitor(gameIR)
        val system = SaveSystem(id = "saves", slots = 3)

        val functions = visitor.visitSaveSystem(system)
        val loadGame = functions.first { it.name.contains("load_game") }

        assertNotNull(
            loadGame.params.find { it.name == "slotIndex" },
            "load_game should have slotIndex parameter",
        )
    }

    // =========================================================================
    // Test 3: Transient variables excluded from save/load
    // =========================================================================

    @Test
    fun `save system excludes transient variables from save function`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                variables =
                    listOf(
                        VariableDef("score", VarType.U8, 0),
                        VariableDef("temp", VarType.U8, 0), // transient
                        VariableDef("lives", VarType.U8, 3),
                    ),
            )
        val visitor = GBDKSystemVisitor(gameIR)
        val system = SaveSystem(id = "save", transientVarNames = setOf("temp"))

        val functions = visitor.visitSaveSystem(system)
        val saveGame = functions.first { it.name.contains("save_game") }
        val emitted = emitBody(saveGame.body)

        assertTrue(
            emitted.contains("_score"),
            "Save should include _score (non-transient), got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_lives"),
            "Save should include _lives (non-transient), got:\n$emitted",
        )
        assertFalse(
            emitted.contains("_temp"),
            "Save should NOT include _temp (transient), got:\n$emitted",
        )
    }

    @Test
    fun `save system excludes transient variables from load function`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                variables =
                    listOf(
                        VariableDef("score", VarType.U8, 0),
                        VariableDef("temp", VarType.U8, 0), // transient
                    ),
            )
        val visitor = GBDKSystemVisitor(gameIR)
        val system = SaveSystem(id = "save", transientVarNames = setOf("temp"))

        val functions = visitor.visitSaveSystem(system)
        val loadGame = functions.first { it.name.contains("load_game") }
        val emitted = emitBody(loadGame.body)

        assertTrue(
            emitted.contains("_score"),
            "Load should include _score (non-transient), got:\n$emitted",
        )
        assertFalse(
            emitted.contains("_temp"),
            "Load should NOT include _temp (transient), got:\n$emitted",
        )
    }

    // =========================================================================
    // Test 4: Checksum generates CFor accumulator loop
    // =========================================================================

    @Test
    fun `save system with checksum generates CFor accumulator loop in save function`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                variables = listOf(VariableDef("score", VarType.U8, 0)),
            )
        val visitor = GBDKSystemVisitor(gameIR)
        val system = SaveSystem(id = "save", useChecksum = true)

        val functions = visitor.visitSaveSystem(system)
        val saveGame = functions.first { it.name.contains("save_game") }

        val forLoops = collectForLoops(saveGame.body)
        assertTrue(
            forLoops.isNotEmpty(),
            "Checksum save should generate a CFor accumulator loop, found 0 for loops",
        )
    }

    @Test
    fun `save system with checksum generates CFor accumulator loop in load function`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                variables = listOf(VariableDef("score", VarType.U8, 0)),
            )
        val visitor = GBDKSystemVisitor(gameIR)
        val system = SaveSystem(id = "save", useChecksum = true)

        val functions = visitor.visitSaveSystem(system)
        val loadGame = functions.first { it.name.contains("load_game") }

        val forLoops = collectForLoops(loadGame.body)
        assertTrue(
            forLoops.isNotEmpty(),
            "Checksum load should generate a CFor accumulator loop, found 0 for loops",
        )
    }

    @Test
    fun `save system without checksum generates no CFor loops`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                variables = listOf(VariableDef("score", VarType.U8, 0)),
            )
        val visitor = GBDKSystemVisitor(gameIR)
        val system = SaveSystem(id = "save", useChecksum = false)

        val functions = visitor.visitSaveSystem(system)
        val saveGame = functions.first { it.name.contains("save_game") }

        val forLoops = collectForLoops(saveGame.body)
        assertTrue(
            forLoops.isEmpty(),
            "No-checksum save should generate zero CFor loops, got: ${forLoops.size}",
        )
    }

    // =========================================================================
    // Test 5: ENABLE_RAM / DISABLE_RAM pairing — DISABLE_RAM always last
    // =========================================================================

    @Test
    fun `save system pairs ENABLE_RAM and DISABLE_RAM correctly in save function`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                variables = listOf(VariableDef("score", VarType.U8, 0)),
            )
        val visitor = GBDKSystemVisitor(gameIR)
        val system = SaveSystem(id = "save")

        val functions = visitor.visitSaveSystem(system)
        val saveGame = functions.first { it.name.contains("save_game") }
        val rawCode = collectRawCode(saveGame.body)

        assertTrue(rawCode.any { it.contains("ENABLE_RAM") }, "save_game should call ENABLE_RAM")
        assertTrue(rawCode.any { it.contains("DISABLE_RAM") }, "save_game should call DISABLE_RAM")
        // DISABLE_RAM is always the last CRawCode in the top-level body
        val lastTopLevelRaw = saveGame.body.filterIsInstance<CRawCode>().last()
        assertTrue(
            lastTopLevelRaw.code.contains("DISABLE_RAM"),
            "DISABLE_RAM must be the last top-level raw statement in save_game",
        )
    }

    @Test
    fun `save system pairs ENABLE_RAM and DISABLE_RAM correctly in load function`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                variables = listOf(VariableDef("score", VarType.U8, 0)),
            )
        val visitor = GBDKSystemVisitor(gameIR)
        val system = SaveSystem(id = "save")

        val functions = visitor.visitSaveSystem(system)
        val loadGame = functions.first { it.name.contains("load_game") }
        val rawCode = collectRawCode(loadGame.body)

        assertTrue(rawCode.any { it.contains("ENABLE_RAM") }, "load_game should call ENABLE_RAM")
        assertTrue(rawCode.any { it.contains("DISABLE_RAM") }, "load_game should call DISABLE_RAM")
        // DISABLE_RAM is always the last CRawCode in the top-level body
        val lastTopLevelRaw = loadGame.body.filterIsInstance<CRawCode>().last()
        assertTrue(
            lastTopLevelRaw.code.contains("DISABLE_RAM"),
            "DISABLE_RAM must be the last top-level raw statement in load_game",
        )
    }

    // =========================================================================
    // Test 6: Load function checks sentinel before loading variables
    // =========================================================================

    @Test
    fun `load function checks sentinel before loading variables`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                variables =
                    listOf(VariableDef("score", VarType.U8, 0), VariableDef("lives", VarType.U8, 3)),
            )
        val visitor = GBDKSystemVisitor(gameIR)
        val system = SaveSystem(id = "save")

        val functions = visitor.visitSaveSystem(system)
        val loadGame = functions.first { it.name.contains("load_game") }

        // Sentinel check is a CIf in the function body
        val ifNodes = collectIfs(loadGame.body)
        assertTrue(
            ifNodes.isNotEmpty(),
            "load_game should have at least one CIf for sentinel check",
        )

        // The emitted C should contain the sentinel value (0xAB = 171, emitted as 171u by CEmitter)
        val emitted = emitBody(loadGame.body)
        assertTrue(
            emitted.contains("171") || emitted.contains("0xAB"),
            "load_game should check sentinel 0xAB (171 decimal), got:\n$emitted",
        )
    }

    @Test
    fun `load function with checksum has CIf for checksum mismatch before sentinel check`() {
        val gameIR =
            GameIR(
                name = "Test",
                config = CartridgeConfig(),
                variables = listOf(VariableDef("score", VarType.U8, 0)),
            )
        val visitor = GBDKSystemVisitor(gameIR)
        val system = SaveSystem(id = "save", useChecksum = true)

        val functions = visitor.visitSaveSystem(system)
        val loadGame = functions.first { it.name.contains("load_game") }

        // With checksum: there should be 2 CIf nodes — checksum mismatch + sentinel check
        val ifNodes = collectIfs(loadGame.body)
        assertTrue(
            ifNodes.size >= 2,
            "load_game with checksum should have 2 CIf nodes (checksum + sentinel), got: ${ifNodes.size}",
        )
    }
}
