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
// EASE-TO-ZERO TESTS  (Wave 0 RED — Plan 13.2-01 Task 2)
//
// Pins Req #8 / D-13: `easeToZero()` must emit exactly TWO `IfOp` nodes
// (not one if-else), matching the hand-rolled two-`whenever` ladder from
// SimplePhysics.kt and Metasprites.kt.
//
// Pitfall 3 (from 13.2-RESEARCH.md): the two branches (`< 0 → v++` and
// `> 0 → v--`) are mutually exclusive but are emitted as SEPARATE `IfOp`
// nodes, NOT as a single `if/else`, to preserve byte-identity with the
// hand-rolled form.
//
// Behaviors verified:
//   (1) `spdY.easeToZero()` emits exactly 2 `IfOp` nodes.
//   (2) First IfOp: condition is `BinaryExpr(VarRef("spdY"), LT, Literal(0))`,
//       then body is `Assign("spdY", Literal(1), ADD)`.
//   (3) Second IfOp: condition is `GT Literal(0)`,
//       then body is `Assign("spdY", Literal(1), SUB)`.
//   (4) `easeToZero(by = 2)` emits `+= 2` / `-= 2`.
//
// These tests are RED today because `easeToZero` does not yet exist
// (compile-fail). Plan 13.2-05 adds the production extension function,
// turning these RED tests GREEN.
//
// Reference decisions: D-13 (method-on-variable, two-IfOp ladder),
//   Pitfall 3 (two IfOp not one if-else).
// =============================================================================

class EaseToZeroTest {

    private fun buildScript(block: ScriptBuilder.() -> Unit): List<ScriptOp> {
        val builder = ScriptBuilder()
        ScriptBuilderContext.with(builder) { builder.block() }
        return builder.build()
    }

    // =========================================================================
    // Behavior 1+2+3: easeToZero(by=1) emits exactly two IfOp nodes with
    //   correct conditions and ADD/SUB assign bodies
    // =========================================================================

    @Test
    fun `easeToZero emits exactly two IfOp nodes`() {
        val spdY = AssignableVar("spdY")
        val ops = buildScript {
            spdY.easeToZero()
        } // compile-fail today — easeToZero does not exist

        assertEquals(
            2,
            ops.size,
            "easeToZero() must emit exactly 2 IfOp nodes (Pitfall 3: NOT one if-else)",
        )
    }

    @Test
    fun `easeToZero first IfOp condition is LT 0`() {
        val spdY = AssignableVar("spdY")
        val ops = buildScript { spdY.easeToZero() } // compile-fail today

        val first = assertIs<IfOp>(ops[0], "first op must be IfOp")
        val cond =
            assertIs<BinaryExpr>(first.condition, "first IfOp condition must be a BinaryExpr")
        assertEquals(BinaryOp.LT, cond.op, "first IfOp condition must be LT (less-than zero check)")
        assertEquals(
            VarRef("spdY"),
            cond.left,
            "first IfOp condition left must be VarRef(\"spdY\")",
        )
        assertEquals(Literal(0), cond.right, "first IfOp condition right must be Literal(0)")
    }

    @Test
    fun `easeToZero first IfOp body is ADD 1 assign`() {
        val spdY = AssignableVar("spdY")
        val ops = buildScript { spdY.easeToZero() } // compile-fail today

        val first = assertIs<IfOp>(ops[0])
        assertEquals(1, first.then.size, "first IfOp body must have exactly 1 op")
        val assign = assertIs<Assign>(first.then[0], "first IfOp body op must be Assign")
        assertEquals("spdY", assign.target, "first IfOp body assign target must be spdY")
        assertEquals(
            AssignOp.ADD,
            assign.op,
            "first IfOp body must use ADD (increment toward zero from negative side)",
        )
        assertEquals(
            Literal(1),
            assign.value,
            "first IfOp body default by=1 must assign Literal(1)",
        )
    }

    @Test
    fun `easeToZero second IfOp condition is GT 0`() {
        val spdY = AssignableVar("spdY")
        val ops = buildScript { spdY.easeToZero() } // compile-fail today

        val second = assertIs<IfOp>(ops[1], "second op must be IfOp")
        val cond =
            assertIs<BinaryExpr>(second.condition, "second IfOp condition must be a BinaryExpr")
        assertEquals(
            BinaryOp.GT,
            cond.op,
            "second IfOp condition must be GT (greater-than zero check)",
        )
        assertEquals(VarRef("spdY"), cond.left)
        assertEquals(Literal(0), cond.right)
    }

    @Test
    fun `easeToZero second IfOp body is SUB 1 assign`() {
        val spdY = AssignableVar("spdY")
        val ops = buildScript { spdY.easeToZero() } // compile-fail today

        val second = assertIs<IfOp>(ops[1])
        assertEquals(1, second.then.size, "second IfOp body must have exactly 1 op")
        val assign = assertIs<Assign>(second.then[0])
        assertEquals("spdY", assign.target)
        assertEquals(
            AssignOp.SUB,
            assign.op,
            "second IfOp body must use SUB (decrement toward zero from positive side)",
        )
        assertEquals(Literal(1), assign.value)
    }

    // =========================================================================
    // Behavior 4: easeToZero(by=2) emits += 2 / -= 2
    // =========================================================================

    @Test
    fun `easeToZero(by=2) emits ADD 2 and SUB 2 assigns`() {
        val spdX = AssignableVar("spdX")
        val ops = buildScript { spdX.easeToZero(by = 2) } // compile-fail today

        assertEquals(2, ops.size, "easeToZero(by=2) must still emit exactly 2 IfOp nodes")

        val firstAssign = assertIs<Assign>(assertIs<IfOp>(ops[0]).then[0])
        assertEquals(AssignOp.ADD, firstAssign.op)
        assertEquals(
            Literal(2),
            firstAssign.value,
            "easeToZero(by=2) first branch must add Literal(2)",
        )

        val secondAssign = assertIs<Assign>(assertIs<IfOp>(ops[1]).then[0])
        assertEquals(AssignOp.SUB, secondAssign.op)
        assertEquals(
            Literal(2),
            secondAssign.value,
            "easeToZero(by=2) second branch must subtract Literal(2)",
        )
    }

    // =========================================================================
    // Pitfall 3 guard: the two IfOp nodes must NOT share an otherwise branch
    // =========================================================================

    @Test
    fun `easeToZero two IfOp nodes each have empty otherwise (not one if-else)`() {
        val spdY = AssignableVar("spdY")
        val ops = buildScript { spdY.easeToZero() } // compile-fail today

        val first = assertIs<IfOp>(ops[0])
        val second = assertIs<IfOp>(ops[1])
        assertEquals(
            emptyList(),
            first.otherwise,
            "Pitfall 3: first IfOp must have no otherwise branch (two separate IfOp, not if-else)",
        )
        assertEquals(
            emptyList(),
            second.otherwise,
            "Pitfall 3: second IfOp must have no otherwise branch (two separate IfOp, not if-else)",
        )
    }
}
