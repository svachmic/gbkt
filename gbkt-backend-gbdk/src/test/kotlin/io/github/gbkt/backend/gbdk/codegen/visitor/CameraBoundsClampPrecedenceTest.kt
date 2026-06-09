/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CCast
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CTernary
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.emit.CEmitter
import io.github.gbkt.core.ir.CameraSystem
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// =============================================================================
// CAMERA BOUNDS CLAMP PRECEDENCE TESTS — Plan 07.4-32 (RED locks for GREEN
// Plan 07.4-34)
//
// Locks the contract that the (UINT8) cast in the bounds-clamp assignment MUST
// wrap the ENTIRE nested ternary, not just the inner `<` comparison. The
// "cast-wraps-ternary" contract has TWO tiers:
//
//   1. AST tier — visitCameraSystem returns CCast(CU8, CTernary(...)). This is
//      already correct at HEAD; the AST is well-formed.
//   2. EMITTED-TEXT tier — CEmitter renders CCast as `(UINT8)<inner>` and renders
//      CTernary WITHOUT outer parentheses, so the final text emits
//
//          _camera_x = (UINT8)(rawX < 0) ? 0 : (rawX > 0) ? 0 : rawX;
//
//      C operator precedence then binds the (UINT8) cast to the FIRST
//      parenthesised group `(rawX < 0)` alone (yielding 0 or 1) before the outer
//      `?:` operator fires. _camera_x is therefore permanently 0 — the camera
//      cannot follow the actor (round-7 UAT: GAP-CAMERA-NO-FOLLOW).
//
// The RED test FAILS at HEAD on the EMITTED-TEXT tier; the AST-tier assertion
// passes because the AST is already correct. The GREEN fix in Plan 07.4-34 must
// re-parenthesise the emitter (or wrap the inner CTernary at AST level) so the
// emitted text reads
//
//   _camera_x = (UINT8)((rawX < 0) ? 0 : (rawX > 0) ? 0 : rawX);
//
// — i.e. the (UINT8) cast applies to the entire `?:` chain.
//
// Why test the emitted text and not just the AST: the C runtime sees the
// emitted text. The bug — and the visual GAP-CAMERA-NO-FOLLOW failure — lives
// in the gap between a correct AST and an incorrect rendering. Per CLAUDE.md
// "Verification Methodology — Visual Evidence Rule" / "Scope-level grep gates",
// JVM-tier codegen tests lock the GENERATED C SHAPE which is the contract one
// level below the runtime visual. The shape is captured AT THE EMITTED TEXT.
//
// Bounds chosen as (152, 152): mirrors racer's actual zone (19×19 tiles) which
// is where GAP-CAMERA-NO-FOLLOW manifests. After Plan 07.4-21's max(0, ...)
// clamp, maxX = max(0, 152-160) = 0 and maxY = max(0, 152-144) = 8. The
// inner-ternary upper-bound literals therefore differ across axes (X uses 0,
// Y uses 8), which lets the assertions distinguish per-axis emissions.
// =============================================================================

class CameraBoundsClampPrecedenceTest {

    private val emptyGameIR = GameIR(name = "Test", config = CartridgeConfig())

    /** Build update_camera_<id>() body and emit it as a single C-text blob. */
    private fun emitCameraBody(system: CameraSystem): String {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val functions = visitor.visitCameraSystem(system)
        return functions.first().body.joinToString("\n") { CEmitter.emitStatement(it) }
    }

    /**
     * Return the single CExprStatement assigning to `_camera_<axis>` in the visitor output. Used by
     * the AST-tier diagnostic assertions.
     */
    private fun findCameraAssignment(system: CameraSystem, axis: String): CExprStatement {
        val visitor = GBDKSystemVisitor(emptyGameIR)
        val functions = visitor.visitCameraSystem(system)
        val body = functions.first().body
        val assign =
            body.filterIsInstance<CExprStatement>().firstOrNull {
                val expr = it.expr
                expr is CBinaryExpr &&
                    expr.op == "=" &&
                    (expr.left as? CVar)?.name == "_camera_$axis"
            }
        requireNotNull(assign) { "expected an `_camera_$axis = …` CExprStatement in visitor body" }
        return assign
    }

    // =========================================================================
    // TEST 1 — _camera_x: emitted cast must wrap the ENTIRE ternary
    // =========================================================================

    @Test
    fun bounds_clamp_cast_wraps_entire_ternary_for_x_axis() {
        // Racer's actual numbers: 19x19 tiles = 152x152 px.
        // After Plan 07.4-21 clamp: maxX = max(0, 152-160) = 0.
        val system =
            CameraSystem(
                id = "camera",
                followActorId = "car",
                boundsWidth = 152,
                boundsHeight = 152,
            )

        // ---- AST tier (passes at HEAD — documents the AST is already shaped right) ----
        val xAssign = findCameraAssignment(system, "x")
        val xBinary = xAssign.expr as CBinaryExpr
        val xRhs = xBinary.right
        assertTrue(
            xRhs is CCast,
            "AST: RHS of `_camera_x = …` must be a CCast node; got ${xRhs::class.simpleName}",
        )
        val xCast = xRhs as CCast
        assertTrue(
            xCast.expr is CTernary,
            "AST contract (cast wraps ternary): CCast.expr must be CTernary for _camera_x; " +
                "got ${xCast.expr::class.simpleName}. This is the contract one tier above the " +
                "broken emitted text.",
        )

        // ---- EMITTED-TEXT tier (FAILS at HEAD — locks the precedence bug) ----
        val body = emitCameraBody(system)

        // The broken-precedence shape at HEAD: `(UINT8)(rawX < 0) ? 0 : ...` —
        // i.e. the (UINT8) cast is followed IMMEDIATELY by the inner
        // comparison's parenthesised group, with NO outer paren wrapping the
        // whole ternary. Acceptance: this substring MUST be absent post-fix.
        val brokenXShape = "(UINT8)(rawX < 0)"
        assertTrue(
            !body.contains(brokenXShape),
            "EMITTED TEXT (cast must wrap ternary): the broken-precedence shape `$brokenXShape` " +
                "must NOT appear in the emitted camera body. C operator precedence binds the " +
                "(UINT8) cast to the inner `(rawX < 0)` comparison (yielding 0 or 1), then the " +
                "outer `?:` fires on that — so _camera_x is permanently 0 and the camera cannot " +
                "follow the actor (GAP-CAMERA-NO-FOLLOW).\n\n" +
                "Required post-fix shape: `(UINT8)((rawX < 0) ? 0 : (rawX > 0) ? 0 : rawX)` — " +
                "the (UINT8) cast wraps the ENTIRE nested ternary (CCast around CTernary). " +
                "Emitted body:\n$body",
        )

        // Belt-and-suspenders: the assignment line for _camera_x must contain a
        // `(UINT8)(` followed at some point by `?` and `:`. After the fix, the
        // shape `(UINT8)((` (cast followed by outer paren AROUND the ternary)
        // MUST appear at least once. RED at HEAD because HEAD emits `(UINT8)(rawX`
        // (no outer paren).
        val expectedFixedShape = "(UINT8)((rawX"
        assertTrue(
            body.contains(expectedFixedShape),
            "EMITTED TEXT (cast must wrap ternary): expected the fix-shape `$expectedFixedShape` " +
                "in the X-axis assignment (cast followed by an OUTER paren that wraps the ternary). " +
                "Currently the emitter produces `(UINT8)(rawX < 0) ? …` which is the broken " +
                "precedence pattern. Emitted body:\n$body",
        )
    }

    // =========================================================================
    // TEST 2 — _camera_y: emitted cast must wrap the ENTIRE ternary
    // =========================================================================

    @Test
    fun bounds_clamp_cast_wraps_entire_ternary_for_y_axis() {
        // Same fixture; for boundsHeight=152, maxY = max(0, 152-144) = 8.
        // Differing literal from X axis (0) confirms per-axis emission shape.
        val system =
            CameraSystem(
                id = "camera",
                followActorId = "car",
                boundsWidth = 152,
                boundsHeight = 152,
            )

        // ---- AST tier (passes at HEAD) ----
        val yAssign = findCameraAssignment(system, "y")
        val yBinary = yAssign.expr as CBinaryExpr
        val yRhs = yBinary.right
        assertTrue(
            yRhs is CCast,
            "AST: RHS of `_camera_y = …` must be a CCast node; got ${yRhs::class.simpleName}",
        )
        val yCast = yRhs as CCast
        assertTrue(
            yCast.expr is CTernary,
            "AST contract (cast wraps ternary): CCast.expr must be CTernary for _camera_y; " +
                "got ${yCast.expr::class.simpleName}.",
        )

        // ---- EMITTED-TEXT tier (FAILS at HEAD) ----
        val body = emitCameraBody(system)

        val brokenYShape = "(UINT8)(rawY < 0)"
        assertTrue(
            !body.contains(brokenYShape),
            "EMITTED TEXT (cast must wrap ternary): the broken-precedence shape `$brokenYShape` " +
                "must NOT appear in the emitted camera body. C operator precedence binds the " +
                "(UINT8) cast to the inner `(rawY < 0)` comparison (yielding 0 or 1), then the " +
                "outer `?:` fires on that — so _camera_y is permanently 0/8 and the camera " +
                "cannot follow the actor (GAP-CAMERA-NO-FOLLOW).\n\n" +
                "Required post-fix shape: `(UINT8)((rawY < 0) ? 0 : (rawY > 8) ? 8 : rawY)` — " +
                "the (UINT8) cast wraps the ENTIRE nested ternary (CCast around CTernary). " +
                "Emitted body:\n$body",
        )

        val expectedFixedShape = "(UINT8)((rawY"
        assertTrue(
            body.contains(expectedFixedShape),
            "EMITTED TEXT (cast must wrap ternary): expected the fix-shape `$expectedFixedShape` " +
                "in the Y-axis assignment (cast followed by an OUTER paren that wraps the ternary). " +
                "Currently the emitter produces `(UINT8)(rawY < 0) ? …` which is the broken " +
                "precedence pattern. Emitted body:\n$body",
        )
    }

    // =========================================================================
    // TEST 3 — cast is the OUTERMOST node in clamp assignment (partial-fix guard)
    // Catches a degenerate fix where the cast moves but a stray CCast remains
    // deeper in the tree.
    // =========================================================================

    @Test
    fun cast_is_outermost_node_in_clamp_assignment() {
        val system =
            CameraSystem(
                id = "camera",
                followActorId = "car",
                boundsWidth = 152,
                boundsHeight = 152,
            )

        for (axis in listOf("x", "y")) {
            val assign = findCameraAssignment(system, axis)
            val binary = assign.expr as CBinaryExpr
            val rhs = binary.right

            // The OUTERMOST RHS node is a CCast.
            assertTrue(
                rhs is CCast,
                "AST outermost: RHS of `_camera_$axis = …` must be a CCast (the outermost node); " +
                    "got ${rhs::class.simpleName}. Any partial fix that pushes the cast deeper " +
                    "and leaves a non-CCast at the outermost position would re-introduce the " +
                    "precedence bug.",
            )

            // Count CCast occurrences in the RHS subtree — must be exactly one.
            assertEquals(
                1,
                countCCast(rhs),
                "AST outermost: expected exactly ONE CCast in the `_camera_$axis = …` RHS subtree " +
                    "(the outermost cast wrapping the ternary). Multiple CCasts would indicate a " +
                    "partial fix.",
            )
        }
    }

    /** Recursively count CCast nodes in a CExpr subtree. */
    private fun countCCast(expr: io.github.gbkt.backend.gbdk.codegen.ast.CExpr): Int {
        return when (expr) {
            is CCast -> 1 + countCCast(expr.expr)
            is CTernary ->
                countCCast(expr.condition) + countCCast(expr.thenExpr) + countCCast(expr.elseExpr)
            is CBinaryExpr -> countCCast(expr.left) + countCCast(expr.right)
            else -> 0
        }
    }
}
