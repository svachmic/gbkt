/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.core.ir.UnaryExpr
import io.github.gbkt.core.ir.UnaryOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// =============================================================================
// RUN-IF ALIAS TESTS  (Wave 0 RED — Plan 13.2-01 Task 2)
//
// Pins Req #2 / D-06: `runIf` / `unless` / `.orElse` must produce the same
// `IfOp` IR shape as the existing `ifOp` / `elseOp`.
//
// Behaviors verified:
//   (1) `runIf(cond) { }` produces an IfOp equal to `ifOp(cond) { }`.
//   (2) `unless(cond) { }` produces an IfOp whose condition is
//       `UnaryExpr(UnaryOp.LOGICAL_NOT, cond)`.
//   (3) `runIf(cond) { }.orElse { }` produces the same two-arm IfOp as
//       `ifOp(cond) { }.elseOp { }`.
//
// These tests are RED today because `runIf`, `unless`, and `orElse` do not
// yet exist in `ScriptBuilder` (compile-fail). Plan 13.2-04 adds these
// aliases, turning these RED tests GREEN.
//
// Reference decisions: D-06 (user-facing names), D-07 (whenever coexists,
//   not deprecated).
// =============================================================================

class RunIfAliasTest {

    private fun buildScript(block: ScriptBuilder.() -> Unit): List<ScriptOp> {
        val builder = ScriptBuilder()
        ScriptBuilderContext.with(builder) { builder.block() }
        return builder.build()
    }

    // =========================================================================
    // Behavior 1: runIf produces same IfOp shape as ifOp
    // =========================================================================

    @Test
    fun `runIf produces same IfOp shape as ifOp`() {
        val cond = Literal(1) // dummy condition
        val ifOpOps = buildScript { ifOp(cond) {} }
        val runIfOps = buildScript { runIf(cond) {} } // compile-fail today — runIf does not exist

        assertEquals(
            ifOpOps,
            runIfOps,
            "runIf must produce IR identical to ifOp (D-06 same IfOp lowering)",
        )
    }

    // =========================================================================
    // Behavior 2: unless negates the condition via UnaryExpr(LOGICAL_NOT, cond)
    // =========================================================================

    @Test
    fun `unless produces IfOp with LOGICAL_NOT of condition`() {
        val cond = Literal(1)
        val ops = buildScript { unless(cond) {} } // compile-fail today — unless does not exist

        assertEquals(1, ops.size, "unless must emit exactly one IfOp")
        val ifOp = assertIs<IfOp>(ops[0], "unless must emit an IfOp")
        val negated =
            assertIs<UnaryExpr>(
                ifOp.condition,
                "unless condition must be a UnaryExpr wrapping the original condition",
            )
        assertEquals(UnaryOp.LOGICAL_NOT, negated.op, "unless must negate via UnaryOp.LOGICAL_NOT")
        assertEquals(cond, negated.operand, "unless must wrap the original condition unchanged")
    }

    // =========================================================================
    // Behavior 3: runIf + orElse produces same two-arm IfOp as ifOp + elseOp
    // =========================================================================

    @Test
    fun `runIf with orElse produces same IfOp as ifOp with elseOp`() {
        val cond = Literal(1)
        val ifElseOps = buildScript {
            ifOp(cond) {}
            elseOp {}
        }
        val runIfOrElseOps = buildScript {
            runIf(cond) {} // compile-fail today
            orElse {} // compile-fail today — orElse does not exist
        }

        assertEquals(
            ifElseOps,
            runIfOrElseOps,
            "runIf + orElse must produce IR identical to ifOp + elseOp (D-06)",
        )
    }
}
