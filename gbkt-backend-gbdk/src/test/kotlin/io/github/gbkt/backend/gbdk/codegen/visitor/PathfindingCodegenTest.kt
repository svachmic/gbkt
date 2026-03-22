/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CArray
import io.github.gbkt.backend.gbdk.codegen.ast.CBlock
import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFile
import io.github.gbkt.backend.gbdk.codegen.ast.CFor
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CWhile
import io.github.gbkt.backend.gbdk.codegen.emit.CEmitter
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.PathfindStep
import io.github.gbkt.core.ir.PathfindingSystem
import io.github.gbkt.core.ir.WaypointStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// =============================================================================
// PATHFINDING CODEGEN TESTS
// Unit tests for GBDKSystemVisitor.visitPathfindingSystem and
// ScriptOpVisitor.visitPathfindStep / visitWaypointStep.
//
// Tests:
// 1. Pathfinding system generates A* infrastructure arrays (_pf_open, _pf_closed, etc.)
// 2. Pathfinding generates iterative while loop, not recursion
// 3. Pathfinding closed set uses bit-packing (bit shift / mask operations)
// 4. PathfindStep generates pf_find_path + pf_step_toward calls
// 5. WaypointStep generates waypoint index advancement with wrap
// 6. Pathfinding with small open list generates correct array sizes
// 7. A* walkability calls _map_collision dispatch (not _pf_collision_fn)
// 8. pathfinding globals do not include _pf_collision_fn
// =============================================================================

class PathfindingCodegenTest {

    private val emptyGameIR = GameIR(name = "Test", config = CartridgeConfig())

    // =========================================================================
    // TEST 1: Pathfinding system generates A* infrastructure arrays
    // =========================================================================

    @Test
    fun `pathfinding system generates A star infrastructure arrays`() {
        val system =
            PathfindingSystem(
                id = "pathfinding",
                gridSize = 8,
                mapWidth = 32,
                mapHeight = 32,
                maxOpenNodes = 32,
                maxPathLength = 32,
            )

        val globals = GBDKSystemVisitor.buildPathfindingGlobals(system)

        val names = globals.map { it.name }
        assertTrue(names.contains("_pf_open"), "Expected _pf_open array in globals, got: $names")
        assertTrue(
            names.contains("_pf_open_count"),
            "Expected _pf_open_count in globals, got: $names",
        )
        assertTrue(
            names.contains("_pf_closed"),
            "Expected _pf_closed array in globals, got: $names",
        )
        assertTrue(
            names.contains("_pf_path_x"),
            "Expected _pf_path_x array in globals, got: $names",
        )
        assertTrue(
            names.contains("_pf_path_y"),
            "Expected _pf_path_y array in globals, got: $names",
        )
        assertTrue(
            names.contains("_pf_path_length"),
            "Expected _pf_path_length in globals, got: $names",
        )

        // _pf_open[maxOpenNodes * 4] = 32 * 4 = 128 elements
        val openArray = globals.first { it.name == "_pf_open" }
        val openArrayType = assertIs<CArray>(openArray.type, "Expected _pf_open to be a CArray")
        assertEquals(128, openArrayType.size, "Expected _pf_open[128] (32 nodes * 4 bytes)")

        // _pf_closed[mapWidth * mapHeight / 8 + 1] = 32 * 32 / 8 + 1 = 129 elements
        val closedArray = globals.first { it.name == "_pf_closed" }
        val closedArrayType =
            assertIs<CArray>(closedArray.type, "Expected _pf_closed to be a CArray")
        assertEquals(129, closedArrayType.size, "Expected _pf_closed[129] (32x32 map / 8 + 1)")

        // _pf_path_x and _pf_path_y[maxPathLength] = 32 elements
        val pathXArray = globals.first { it.name == "_pf_path_x" }
        val pathXType = assertIs<CArray>(pathXArray.type, "Expected _pf_path_x to be a CArray")
        assertEquals(32, pathXType.size, "Expected _pf_path_x[32] (maxPathLength=32)")
    }

    // =========================================================================
    // TEST 2: Pathfinding generates iterative while loop, not recursion
    // =========================================================================

    @Test
    fun `pathfinding generates iterative while loop not recursion`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val system = PathfindingSystem(id = "pf", mapWidth = 16, mapHeight = 16)

        val functions = visitor.visitPathfindingSystem(system)

        // pf_find_path must exist
        val findPathFn =
            functions.find { it.name == "pf_find_path" }
                ?: error("Expected pf_find_path function, got: ${functions.map { it.name }}")

        // Body must contain a CWhile (iterative) — not a recursive call to pf_find_path
        val hasWhile = findWhileLoop(findPathFn.body)
        assertTrue(hasWhile, "pf_find_path should contain a CWhile loop (iterative A*)")

        // Must NOT contain a recursive CCall to pf_find_path itself
        val hasRecursiveCall = findRecursiveCall(findPathFn.body, "pf_find_path")
        assertFalse(
            hasRecursiveCall,
            "pf_find_path must NOT be recursive (Game Boy stack is ~128 bytes)",
        )
    }

    // =========================================================================
    // TEST 3: Pathfinding closed set uses bit-packing
    // =========================================================================

    @Test
    fun `pathfinding closed set uses bit-packing with shift and mask`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val system = PathfindingSystem(id = "pf")

        val functions = visitor.visitPathfindingSystem(system)

        // pf_is_closed must use bit-packed operations
        val isClosedFn =
            functions.find { it.name == "pf_is_closed" } ?: error("Expected pf_is_closed function")

        val emitted = emitFunctionBody(isClosedFn)

        // Bit shift right: idx >> 3 (to get byte index in closed set)
        assertTrue(
            emitted.contains(">>"),
            "pf_is_closed should use >> to compute byte index, got:\n$emitted",
        )
        // Bitwise AND: & (to mask the bit)
        assertTrue(
            emitted.contains("&"),
            "pf_is_closed should use & to test the bit, got:\n$emitted",
        )
        // Reference the closed set array
        assertTrue(
            emitted.contains("_pf_closed"),
            "pf_is_closed should access _pf_closed array, got:\n$emitted",
        )

        // pf_set_closed must use bit-set operation
        val setClosedFn =
            functions.find { it.name == "pf_set_closed" }
                ?: error("Expected pf_set_closed function")

        val emittedSet = emitFunctionBody(setClosedFn)
        // Bit-set: |= (to set the bit)
        assertTrue(
            emittedSet.contains("|="),
            "pf_set_closed should use |= to set bit, got:\n$emittedSet",
        )
        assertTrue(
            emittedSet.contains("_pf_closed"),
            "pf_set_closed should access _pf_closed array",
        )
    }

    // =========================================================================
    // TEST 4: PathfindStep generates pf_find_path + pf_step_toward calls
    // =========================================================================

    @Test
    fun `pathfindStep generates pf_find_path and pf_step_toward calls`() {
        val op = PathfindStep(npcActorId = "guard", targetActorId = "player")

        val result = ScriptOpVisitor.visit(op)

        val block = assertIs<CBlock>(result, "Expected CBlock from visitPathfindStep")

        // Must have at least 2 statements: pf_find_path call + pf_step_toward call
        assertTrue(
            block.statements.size >= 2,
            "Expected at least 2 statements in PathfindStep output",
        )

        val emitted = CEmitter.emitStatement(block)

        // pf_find_path call with NPC and target tile coordinates
        assertTrue(emitted.contains("pf_find_path"), "Expected pf_find_path call, got:\n$emitted")
        // NPC pixel-to-tile conversion: _guard_x / PF_GRID_SIZE
        assertTrue(
            emitted.contains("_guard_x"),
            "Expected _guard_x in pf_find_path args, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_guard_y"),
            "Expected _guard_y in pf_find_path args, got:\n$emitted",
        )
        // Target pixel-to-tile conversion: _player_x / PF_GRID_SIZE
        assertTrue(
            emitted.contains("_player_x"),
            "Expected _player_x in pf_find_path args, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_player_y"),
            "Expected _player_y in pf_find_path args, got:\n$emitted",
        )
        // Grid size constant for pixel-to-tile division
        assertTrue(
            emitted.contains("PF_GRID_SIZE"),
            "Expected PF_GRID_SIZE constant, got:\n$emitted",
        )
        // pf_step_toward call to move NPC one step
        assertTrue(
            emitted.contains("pf_step_toward"),
            "Expected pf_step_toward call, got:\n$emitted",
        )
    }

    // =========================================================================
    // TEST 5: WaypointStep generates waypoint index advancement with wrap
    // =========================================================================

    @Test
    fun `waypointStep generates waypoint movement and index advancement`() {
        val op = WaypointStep(npcActorId = "guard")

        val result = ScriptOpVisitor.visit(op)

        val block = assertIs<CBlock>(result, "Expected CBlock from visitWaypointStep")

        // Must have at least 2 statements: pf_step_toward + if check for advancement
        assertTrue(
            block.statements.size >= 2,
            "Expected at least 2 statements in WaypointStep output",
        )

        val emitted = CEmitter.emitStatement(block)

        // Move toward current waypoint
        assertTrue(
            emitted.contains("pf_step_toward"),
            "Expected pf_step_toward in waypoint step, got:\n$emitted",
        )
        // Waypoint data arrays for guard NPC
        assertTrue(
            emitted.contains("_guard_wp_x"),
            "Expected _guard_wp_x waypoint data, got:\n$emitted",
        )
        assertTrue(
            emitted.contains("_guard_wp_y"),
            "Expected _guard_wp_y waypoint data, got:\n$emitted",
        )
        // Waypoint index variable
        assertTrue(
            emitted.contains("_guard_wp_idx"),
            "Expected _guard_wp_idx index variable, got:\n$emitted",
        )

        // Waypoint index advancement with wrap (if reached target, advance and wrap)
        val hasIfCheck = block.statements.any { it is CIf }
        assertTrue(
            hasIfCheck,
            "WaypointStep should have CIf for arrival check and index advancement",
        )

        // The advancement logic wraps using waypoint count
        assertTrue(
            emitted.contains("_guard_wp_count"),
            "Expected _guard_wp_count for wrap logic, got:\n$emitted",
        )
    }

    // =========================================================================
    // TEST 6: Pathfinding with small open list generates correct array sizes
    // =========================================================================

    @Test
    fun `pathfinding with small open list generates correct array sizes`() {
        val system =
            PathfindingSystem(
                id = "mini_pf",
                gridSize = 8,
                mapWidth = 16,
                mapHeight = 16,
                maxOpenNodes = 16,
                maxPathLength = 16,
            )

        val globals = GBDKSystemVisitor.buildPathfindingGlobals(system)

        // _pf_open[maxOpenNodes * 4] = 16 * 4 = 64 elements
        val openArray = globals.first { it.name == "_pf_open" }
        val openArrayType = assertIs<CArray>(openArray.type, "Expected _pf_open to be a CArray")
        assertEquals(64, openArrayType.size, "Expected _pf_open[64] (16 nodes * 4 bytes)")

        // _pf_closed[mapWidth * mapHeight / 8 + 1] = 16 * 16 / 8 + 1 = 33 elements
        val closedArray = globals.first { it.name == "_pf_closed" }
        val closedArrayType =
            assertIs<CArray>(closedArray.type, "Expected _pf_closed to be a CArray")
        assertEquals(33, closedArrayType.size, "Expected _pf_closed[33] (16x16 map / 8 + 1)")

        // _pf_path_x[maxPathLength] = 16 elements
        val pathXArray = globals.first { it.name == "_pf_path_x" }
        val pathXType = assertIs<CArray>(pathXArray.type, "Expected _pf_path_x to be a CArray")
        assertEquals(16, pathXType.size, "Expected _pf_path_x[16] (maxPathLength=16)")

        // WRAM budget: 64 + 1 + 33 + 16 + 16 + 1 = 131 bytes — well within Game Boy WRAM
        val pathYArray = globals.first { it.name == "_pf_path_y" }
        val pathYType = assertIs<CArray>(pathYArray.type, "Expected _pf_path_y to be a CArray")
        val totalBytes =
            openArrayType.size!! +
                1 + // _pf_open_count
                closedArrayType.size!! +
                pathXType.size!! +
                pathYType.size!! +
                1 // _pf_path_length
        assertTrue(
            totalBytes < 512,
            "WRAM budget for 16x16 map should be under 512 bytes, got $totalBytes",
        )
    }

    // =========================================================================
    // TEST 7: A* walkability calls _map_collision dispatch
    // =========================================================================

    @Test
    fun `A-star walkability calls _map_collision dispatch`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val system =
            PathfindingSystem(
                id = "nav",
                gridSize = 8,
                maxOpenNodes = 16,
                maxPathLength = 16,
                mapWidth = 16,
                mapHeight = 16,
            )
        val functions = visitor.visitPathfindingSystem(system)
        val findPathFn = functions.first { it.name == "pf_find_path" }
        val output = CEmitter.emit(CFile("test.c", functions = listOf(findPathFn)))

        // Verify _map_collision(nx, ny) call is present in A* expansion
        assertTrue(
            output.contains("_map_collision(nx, ny)"),
            "A* neighbor expansion must call _map_collision(nx, ny) for walkability check, got:\n$output",
        )

        // Verify _pf_collision_fn is NOT present anywhere
        assertFalse(
            output.contains("_pf_collision_fn"),
            "_pf_collision_fn should be removed — use _map_collision dispatch instead",
        )
    }

    // =========================================================================
    // TEST 8: pathfinding globals do not include _pf_collision_fn
    // =========================================================================

    @Test
    fun `pathfinding globals do not include _pf_collision_fn`() {
        val system =
            PathfindingSystem(
                id = "nav",
                gridSize = 8,
                maxOpenNodes = 16,
                maxPathLength = 16,
                mapWidth = 16,
                mapHeight = 16,
            )
        val globals = GBDKSystemVisitor.buildPathfindingGlobals(system)
        val names = globals.map { it.name }
        assertFalse(
            names.contains("_pf_collision_fn"),
            "buildPathfindingGlobals should not include _pf_collision_fn",
        )
        // Verify expected globals ARE present
        assertTrue(names.contains("_pf_open"), "Should have _pf_open array")
        assertTrue(names.contains("_pf_closed"), "Should have _pf_closed array")
        assertTrue(names.contains("_pf_path_x"), "Should have _pf_path_x array")
        assertTrue(names.contains("_pf_path_y"), "Should have _pf_path_y array")
        assertTrue(names.contains("_pf_path_length"), "Should have _pf_path_length counter")
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Emit all body statements of a CFunction to a single C string. */
    private fun emitFunctionBody(fn: io.github.gbkt.backend.gbdk.codegen.ast.CFunction): String =
        fn.body.joinToString("\n") { stmt -> CEmitter.emitStatement(stmt) }

    /** Recursively check if a statement list contains a CWhile loop. */
    private fun findWhileLoop(
        stmts: List<io.github.gbkt.backend.gbdk.codegen.ast.CStatement>
    ): Boolean {
        for (stmt in stmts) {
            when (stmt) {
                is CWhile -> return true
                is CBlock -> if (findWhileLoop(stmt.statements)) return true
                is CFor -> if (findWhileLoop(stmt.body)) return true
                is CIf -> {
                    if (findWhileLoop(stmt.thenBody)) return true
                    if (findWhileLoop(stmt.elseBody)) return true
                }
                else -> Unit
            }
        }
        return false
    }

    /** Recursively check if a statement list contains a CCall to the given function name. */
    private fun findRecursiveCall(
        stmts: List<io.github.gbkt.backend.gbdk.codegen.ast.CStatement>,
        fnName: String,
    ): Boolean {
        for (stmt in stmts) {
            when (stmt) {
                is CExprStatement -> {
                    val expr = stmt.expr
                    if (expr is CCall && expr.function == fnName) return true
                }
                is CBlock -> if (findRecursiveCall(stmt.statements, fnName)) return true
                is CFor -> if (findRecursiveCall(stmt.body, fnName)) return true
                is CWhile -> if (findRecursiveCall(stmt.body, fnName)) return true
                is CIf -> {
                    if (findRecursiveCall(stmt.thenBody, fnName)) return true
                    if (findRecursiveCall(stmt.elseBody, fnName)) return true
                }
                else -> Unit
            }
        }
        return false
    }
}
