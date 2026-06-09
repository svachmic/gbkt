/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.core.ir.VarRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// =============================================================================
// WRAP-AT TESTS  (Wave 0 RED — Plan 13.2-01 Task 2)
//
// Pins Req #9 / D-14 / D-15 / D-16: `u8Var(0, wrapAt = N)` declarative
// wrapping with two distinct emission paths.
//
// Behaviors verified:
//   (A) Power-of-two path (wrapAt = 16, N=16, N-1=15):
//       After `idx++`, emitted ops contain:
//       `Assign("idx", BinaryExpr(VarRef("idx"), AND, Literal(15)), SET)`
//       (bitmask path — `& (N-1)`)
//
//   (B) Non-power-of-two path (wrapAt = 5):
//       After `idx++`, emitted ops contain:
//       `IfOp(BinaryExpr(VarRef("idx"), GTE, Literal(5)),
//             [Assign("idx", Literal(0), SET)])`
//       (compare-reset path — `>= N → set 0`)
//
//   (D-16 explicit) Power-of-two detection:
//       wrapAt = 16 → mask path (16 is power of two, mask = 15)
//       wrapAt = 5  → compare-reset path (5 is NOT power of two)
//
// These tests are RED today because the `wrapAt` parameter does not yet exist
// on `u8Var` / `U8VarDelegate` (compile-fail). Plan 13.2-06 adds the param
// and auto-wrap emission, turning these RED tests GREEN.
//
// Reference decisions: D-14 (declarative wrapAt param), D-15 (smart wrap:
//   bitmask for pow2, compare-reset for non-pow2), D-16 (power-of-two test).
// =============================================================================

class WrapAtTest {

    private fun buildScript(block: ScriptBuilder.() -> Unit): List<ScriptOp> {
        val builder = ScriptBuilder()
        ScriptBuilderContext.with(builder) { builder.block() }
        return builder.build()
    }

    // =========================================================================
    // Path A: power-of-two wrapAt = 16  →  bitmask AND (N-1 = 15)
    // Covers D-16 power-of-two detection
    // =========================================================================

    @Test
    fun `wrapAt=16 inc emits bitmask assign AND 15`() {
        val ir = game("Test") {
            var idx by u8Var(0, wrapAt = 16)   // compile-fail today — wrapAt param does not exist
            val sScene = scene("s") {
                frame {
                    idx++
                }
            }
            start = sScene
        }.build()

        val frameOps = ir.scenes.first().frameOps
        // After idx++ there must be a mask assign: idx = idx & 15
        val maskAssign = frameOps.filterIsInstance<Assign>().firstOrNull { assign ->
            assign.target == "idx" &&
                assign.op == AssignOp.SET &&
                assign.value is BinaryExpr &&
                (assign.value as BinaryExpr).op == BinaryOp.AND
        }
        val bitmaskExpr = assertIs<BinaryExpr>(
            maskAssign?.value,
            "wrapAt=16 must emit Assign with BinaryExpr(AND) as value (bitmask path, D-16)"
        )
        assertEquals(VarRef("idx"), bitmaskExpr.left,
            "bitmask expr left must be VarRef(\"idx\")")
        assertEquals(BinaryOp.AND, bitmaskExpr.op,
            "wrapAt=16 (power-of-two) must use AND bitmask, not compare-reset (D-15/D-16)")
        assertEquals(Literal(15), bitmaskExpr.right,
            "wrapAt=16 mask must be Literal(15) = 16 - 1 (D-15)")
    }

    // =========================================================================
    // Path B: non-power-of-two wrapAt = 5  →  compare-reset IfOp (>= 5 → set 0)
    // Covers D-16 non-power-of-two detection
    // =========================================================================

    @Test
    fun `wrapAt=5 inc emits compare-reset IfOp GTE 5`() {
        val ir = game("Test") {
            var idx by u8Var(0, wrapAt = 5)   // compile-fail today
            val sScene = scene("s") {
                frame {
                    idx++
                }
            }
            start = sScene
        }.build()

        val frameOps = ir.scenes.first().frameOps
        // After idx++ there must be: if (idx >= 5) { idx = 0 }
        val wrapIfOp = frameOps.filterIsInstance<IfOp>().firstOrNull { ifOp ->
            val cond = ifOp.condition
            cond is BinaryExpr &&
                cond.op == BinaryOp.GTE &&
                cond.left == VarRef("idx") &&
                cond.right == Literal(5)
        }
        assertIs<IfOp>(wrapIfOp,
            "wrapAt=5 must emit IfOp with condition BinaryExpr(VarRef(\"idx\"), GTE, Literal(5)) (D-15/D-16)")

        assertEquals(1, wrapIfOp!!.then.size,
            "compare-reset IfOp body must have exactly 1 op")
        val resetAssign = assertIs<Assign>(wrapIfOp.then[0],
            "compare-reset IfOp body must be Assign")
        assertEquals("idx", resetAssign.target)
        assertEquals(AssignOp.SET, resetAssign.op,
            "compare-reset assigns SET (reset to 0)")
        assertEquals(Literal(0), resetAssign.value,
            "compare-reset must set idx = Literal(0)")
    }

    // =========================================================================
    // D-16 explicit: verify that ONLY the mask assign is emitted for pow2,
    //   and NO compare-reset IfOp exists for wrapAt=16
    // =========================================================================

    @Test
    fun `wrapAt=16 does NOT emit a compare-reset IfOp`() {
        val ir = game("Test") {
            var idx by u8Var(0, wrapAt = 16)   // compile-fail today
            val sScene = scene("s") {
                frame {
                    idx++
                }
            }
            start = sScene
        }.build()

        val frameOps = ir.scenes.first().frameOps
        val gteIfOps = frameOps.filterIsInstance<IfOp>().filter { ifOp ->
            val cond = ifOp.condition
            cond is BinaryExpr && cond.op == BinaryOp.GTE
        }
        assertEquals(0, gteIfOps.size,
            "wrapAt=16 (power-of-two) must NOT emit a compare-reset IfOp (D-16)")
    }

    // =========================================================================
    // D-16 explicit: verify that ONLY the IfOp compare-reset is emitted for
    //   non-pow2, and NO AND bitmask assign exists for wrapAt=5
    // =========================================================================

    @Test
    fun `wrapAt=5 does NOT emit a bitmask AND assign`() {
        val ir = game("Test") {
            var idx by u8Var(0, wrapAt = 5)   // compile-fail today
            val sScene = scene("s") {
                frame {
                    idx++
                }
            }
            start = sScene
        }.build()

        val frameOps = ir.scenes.first().frameOps
        val andMaskAssigns = frameOps.filterIsInstance<Assign>().filter { assign ->
            assign.value is BinaryExpr &&
                (assign.value as BinaryExpr).op == BinaryOp.AND
        }
        assertEquals(0, andMaskAssigns.size,
            "wrapAt=5 (non-power-of-two) must NOT emit a bitmask AND assign (D-16)")
    }
}
