/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.core.ir.SetPosition
import io.github.gbkt.core.ir.VarRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// =============================================================================
// MOVE-TO EXPR OVERLOAD TESTS
// Locks the SEED-002 lowering contract: ActorRef.moveTo(Expr, Expr) must emit
// exactly one SetPosition op (not two SetActorProperty ops) — D-07.
// =============================================================================

class MoveToExprOverloadTest {

    private fun buildScript(block: ScriptBuilder.() -> Unit): List<ScriptOp> {
        val builder = ScriptBuilder()
        ScriptBuilderContext.with(builder) { builder.block() }
        return builder.build()
    }

    // =========================================================================
    // Primary invariant: moveTo(Expr, Expr) emits exactly 1 SetPosition op
    // =========================================================================

    @Test
    fun `moveTo(Expr, Expr) emits exactly one SetPosition op`() {
        val actor = ActorRef("smiley")
        val xExpr: Expr = BinaryExpr(VarRef("posX"), BinaryOp.SHR, Literal(4))
        val yExpr: Expr = BinaryExpr(VarRef("posY"), BinaryOp.SHR, Literal(4))

        val ops = buildScript { actor.moveTo(xExpr, yExpr) }

        assertEquals(
            1,
            ops.size,
            "Per D-07: moveTo(Expr,Expr) must emit exactly 1 SetPosition op, not 2 SetActorProperty",
        )
        val setPos = assertIs<SetPosition>(ops[0])
        assertEquals("smiley", setPos.actorId)
        assertIs<BinaryExpr>(setPos.x)
        assertIs<BinaryExpr>(setPos.y)
        assertEquals(
            xExpr,
            setPos.x,
            "Per D-07: x Expr must be passed through unchanged (no Literal wrapping)",
        )
        assertEquals(
            yExpr,
            setPos.y,
            "Per D-07: y Expr must be passed through unchanged (no Literal wrapping)",
        )
    }

    // =========================================================================
    // Negative control: moveTo(Int, Int) still records 1 SetPosition with Literal x/y
    // Guards against accidental signature collision with the new overload.
    // =========================================================================

    @Test
    fun `moveTo(Int, Int) still emits exactly one SetPosition op with Literal x and y`() {
        val actor = ActorRef("smiley")

        val ops = buildScript { actor.moveTo(80, 72) }

        assertEquals(1, ops.size, "moveTo(Int,Int) must emit exactly 1 SetPosition op")
        val setPos = assertIs<SetPosition>(ops[0])
        assertEquals("smiley", setPos.actorId)
        assertEquals(Literal(80), setPos.x)
        assertEquals(Literal(72), setPos.y)
    }
}
