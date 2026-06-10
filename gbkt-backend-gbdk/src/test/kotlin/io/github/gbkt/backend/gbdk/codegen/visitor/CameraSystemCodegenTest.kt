/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CRawCode
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CTernary
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.backend.gbdk.codegen.emit.CEmitter
import io.github.gbkt.core.ir.CameraSystem
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// CAMERA SYSTEM CODEGEN TESTS
// Verifies that visitCameraSystem generates typed C AST (zero CRawCode for
// follow, bounds clamping, and shake logic) and correct function structure.
// =============================================================================

class CameraSystemCodegenTest {

    private val emptyGameIR = GameIR(name = "Test", config = CartridgeConfig())

    // =========================================================================
    // Helper: collect all CRawCode strings recursively from a list of statements
    // =========================================================================

    private fun collectRawCode(stmts: List<CStatement>): List<String> {
        val result = mutableListOf<String>()
        for (stmt in stmts) {
            when (stmt) {
                is CRawCode -> result += stmt.code
                is CIf -> {
                    result += collectRawCode(stmt.thenBody)
                    result += collectRawCode(stmt.elseBody)
                }
                else -> Unit
            }
        }
        return result
    }

    /** Recursively collect all CVarDecl nodes from a list of statements (including inside CIf). */
    private fun collectVarDecls(stmts: List<CStatement>): List<CVarDecl> {
        val result = mutableListOf<CVarDecl>()
        for (stmt in stmts) {
            if (stmt is CVarDecl) result += stmt
            if (stmt is CIf) {
                result += collectVarDecls(stmt.thenBody)
                result += collectVarDecls(stmt.elseBody)
            }
        }
        return result
    }

    /**
     * Recursively collect all CExprStatement nodes from a list of statements (including inside
     * CIf).
     */
    private fun collectExprStatements(stmts: List<CStatement>): List<CExprStatement> {
        val result = mutableListOf<CExprStatement>()
        for (stmt in stmts) {
            if (stmt is CExprStatement) result += stmt
            if (stmt is CIf) {
                result += collectExprStatements(stmt.thenBody)
                result += collectExprStatements(stmt.elseBody)
            }
        }
        return result
    }

    // =========================================================================
    // TEST 1: camera with no follow generates basic update function
    // =========================================================================

    @Test
    fun `camera with no follow generates basic update function`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val system = CameraSystem(id = "cam")

        val functions = visitor.visitCameraSystem(system)

        assertEquals(1, functions.size, "CameraSystem should produce exactly one function")
        val fn = functions.first()
        assertTrue(fn.name.contains("update_camera"), "Function name should contain update_camera")
        assertEquals("update_camera_cam", fn.name)
    }

    // =========================================================================
    // TEST 2: camera with no follow emits SCX_REG and SCY_REG writes
    // =========================================================================

    @Test
    fun `camera with no follow body references SCX_REG and SCY_REG`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val system = CameraSystem(id = "cam")

        val functions = visitor.visitCameraSystem(system)
        val fn = functions.first()
        val rawCode = collectRawCode(fn.body)

        assertTrue(
            rawCode.any { it.contains("SCX_REG") },
            "Camera update body should reference SCX_REG, got: $rawCode",
        )
        assertTrue(
            rawCode.any { it.contains("SCY_REG") },
            "Camera update body should reference SCY_REG, got: $rawCode",
        )
    }

    // =========================================================================
    // TEST 3: camera with follow generates actor tracking (reads _hero_x/_hero_y)
    // =========================================================================

    @Test
    fun `camera with follow generates actor tracking variables`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val system = CameraSystem(id = "main_cam", followActorId = "hero")

        val functions = visitor.visitCameraSystem(system)
        val fn = functions.first()

        // Emitted C text should reference _hero_x and _hero_y
        val emittedC = fn.body.joinToString("\n") { CEmitter.emitStatement(it) }
        assertTrue(
            emittedC.contains("_hero_x"),
            "Follow camera should reference _hero_x in emitted C, got:\n$emittedC",
        )
        assertTrue(
            emittedC.contains("_hero_y"),
            "Follow camera should reference _hero_y in emitted C, got:\n$emittedC",
        )
    }

    // =========================================================================
    // TEST 4: camera with follow uses INT16 intermediate variable (rawX, rawY)
    // =========================================================================

    @Test
    fun `camera with follow uses INT16 intermediate rawX and rawY variables`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val system = CameraSystem(id = "cam", followActorId = "hero")

        val functions = visitor.visitCameraSystem(system)
        val fn = functions.first()

        val varDecls = collectVarDecls(fn.body)
        val rawXDecl = varDecls.firstOrNull { it.name == "rawX" }
        val rawYDecl = varDecls.firstOrNull { it.name == "rawY" }

        assertTrue(rawXDecl != null, "Camera with follow should declare rawX INT16 variable")
        assertTrue(rawYDecl != null, "Camera with follow should declare rawY INT16 variable")

        // Verify both are INT16 type (CI16)
        assertEquals(
            io.github.gbkt.backend.gbdk.codegen.ast.CI16,
            rawXDecl.type,
            "rawX should be INT16 to avoid UINT8 underflow",
        )
        assertEquals(
            io.github.gbkt.backend.gbdk.codegen.ast.CI16,
            rawYDecl.type,
            "rawY should be INT16 to avoid UINT8 underflow",
        )
    }

    // =========================================================================
    // TEST 5: camera with follow and bounds generates clamp logic (CTernary)
    // =========================================================================

    @Test
    fun `camera with follow and bounds generates CTernary clamp logic`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val system =
            CameraSystem(id = "cam", followActorId = "hero", boundsWidth = 256, boundsHeight = 256)

        val functions = visitor.visitCameraSystem(system)
        val fn = functions.first()

        // Should have CExprStatement nodes that contain CTernary for clamping
        val exprStmts = collectExprStatements(fn.body)
        val hasTernary = exprStmts.any { stmt -> containsTernary(stmt.expr) }
        assertTrue(hasTernary, "Camera with bounds should produce CTernary clamp expressions")
    }

    /** Recursively check if a CExpr contains a CTernary node. */
    private fun containsTernary(expr: io.github.gbkt.backend.gbdk.codegen.ast.CExpr): Boolean {
        return when (expr) {
            is CTernary -> true
            is io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr ->
                containsTernary(expr.left) || containsTernary(expr.right)
            is io.github.gbkt.backend.gbdk.codegen.ast.CCast -> containsTernary(expr.expr)
            else -> false
        }
    }

    // =========================================================================
    // TEST 6: camera with follow and bounds emitted C contains correct maxX clamp
    // =========================================================================

    @Test
    fun `camera with follow and bounds emits correct map minus screen size clamping`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        // 256x256 map → maxX = 256 - 160 = 96, maxY = 256 - 144 = 112
        val system =
            CameraSystem(id = "cam", followActorId = "hero", boundsWidth = 256, boundsHeight = 256)

        val functions = visitor.visitCameraSystem(system)
        val fn = functions.first()
        val emittedC = fn.body.joinToString("\n") { CEmitter.emitStatement(it) }

        // maxX = 256 - 160 = 96, maxY = 256 - 144 = 112
        assertTrue(
            emittedC.contains("96"),
            "Bounds clamp should include maxX = 256-160 = 96 in emitted C, got:\n$emittedC",
        )
        assertTrue(
            emittedC.contains("112"),
            "Bounds clamp should include maxY = 256-144 = 112 in emitted C, got:\n$emittedC",
        )
    }

    // =========================================================================
    // TEST 7: camera shake uses typed CIf (not CRawCode) for the if/else wrapper
    // =========================================================================

    @Test
    fun `camera shake logic uses typed CIf not CRawCode`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val system = CameraSystem(id = "cam")

        val functions = visitor.visitCameraSystem(system)
        val fn = functions.first()

        // Top-level body should contain a CIf (the shake timer check)
        val topLevelIfs = fn.body.filterIsInstance<CIf>()
        assertTrue(
            topLevelIfs.isNotEmpty(),
            "Camera shake wrapper should be a CIf (typed AST), not CRawCode",
        )
    }

    // =========================================================================
    // TEST 8: camera shake CIf condition checks _camera_shake_timer > 0
    // =========================================================================

    @Test
    fun `camera shake CIf condition checks _camera_shake_timer`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val system = CameraSystem(id = "cam")

        val functions = visitor.visitCameraSystem(system)
        val fn = functions.first()
        val shakeIf = fn.body.filterIsInstance<CIf>().first()

        // The condition should reference _camera_shake_timer
        val conditionText = CEmitter.emitStatement(CExprStatement(shakeIf.condition))
        assertTrue(
            conditionText.contains("_camera_shake_timer"),
            "CIf condition should reference _camera_shake_timer, got: $conditionText",
        )
    }

    // =========================================================================
    // TEST 9: no CRawCode remains for the shake if/else wrapper structure
    //         (only SCX_REG/SCY_REG register writes are CRawCode — GBDK lvalue macro)
    // =========================================================================

    @Test
    fun `only hardware register writes remain as CRawCode in shake logic`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val system = CameraSystem(id = "cam")

        val functions = visitor.visitCameraSystem(system)
        val fn = functions.first()

        // The top-level body should NOT have a CRawCode that contains "if ("
        val topLevelRawCode = fn.body.filterIsInstance<CRawCode>()
        val hasRawIfStatement = topLevelRawCode.any { it.code.startsWith("if (") }
        assertFalse(
            hasRawIfStatement,
            "CIf should replace raw 'if (' code — shake wrapper must not be CRawCode",
        )
    }

    // =========================================================================
    // TEST 10: camera system with dashes in ID is sanitized correctly
    // =========================================================================

    @Test
    fun `camera system ID with dashes is sanitized to underscores in function name`() {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val system = CameraSystem(id = "main-camera")

        val functions = visitor.visitCameraSystem(system)
        val fn = functions.first()

        assertEquals(
            "update_camera_main_camera",
            fn.name,
            "Dashes in camera ID should be replaced with underscores",
        )
    }
}
