/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.ArrayAccessExpr
import io.github.gbkt.core.ir.ArrayAssign
import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.ForOp
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.VarRef
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// =============================================================================
// ARRAY VAR HELPERS TESTS
// Verifies that ArrayVar.fill, forEach, indexOf, count emit correct IR ops
// when called inside a ScriptBuilder block.
// =============================================================================

class ArrayVarHelpersTest {

    private fun buildScript(
        block: ScriptBuilder.() -> Unit
    ): List<io.github.gbkt.core.ir.ScriptOp> {
        val builder = ScriptBuilder()
        ScriptBuilderContext.with(builder) { builder.block() }
        return builder.build()
    }

    private val bricks = ArrayVar("bricks", VarType.U8, 5)

    // =========================================================================
    // fill(Int) — emits ForOp with ArrayAssign body
    // =========================================================================

    @Test
    fun `fill int emits ForOp with ArrayAssign body`() {
        val ops = buildScript { bricks.fill(0) }

        assertEquals(1, ops.size, "fill should emit exactly 1 ForOp")
        val forOp = assertIs<ForOp>(ops[0])
        assertEquals("_arr_bricks_i", forOp.variable)
        assertEquals(Literal(0), forOp.from)
        assertEquals(Literal(4), forOp.to, "to should be size-1 = 4")
        assertEquals(1, forOp.body.size)
        val assign = assertIs<ArrayAssign>(forOp.body[0])
        assertEquals("bricks", assign.array)
        assertEquals(VarRef("_arr_bricks_i"), assign.index)
        assertEquals(Literal(0), assign.value)
    }

    @Test
    fun `fill int with non-zero value emits ForOp with correct value`() {
        val ops = buildScript { bricks.fill(1) }

        val forOp = assertIs<ForOp>(ops[0])
        val assign = assertIs<ArrayAssign>(forOp.body[0])
        assertEquals(Literal(1), assign.value)
    }

    @Test
    fun `fill generates loop from 0 to size-1 for size-3 array`() {
        val arr = ArrayVar("items", VarType.U8, 3)
        val ops = buildScript { arr.fill(0) }

        val forOp = assertIs<ForOp>(ops[0])
        assertEquals(Literal(0), forOp.from)
        assertEquals(Literal(2), forOp.to, "to should be size-1 = 2 for a 3-element array")
    }

    // =========================================================================
    // forEach — emits ForOp with user-defined body using element expr
    // =========================================================================

    @Test
    fun `forEach emits ForOp with body from block`() {
        val temp = AssignableVar("temp")
        val ops = buildScript {
            bricks.forEach { element ->
                // The element is an ArrayAccessExpr — assign via modern delegate set
                temp set element
            }
        }

        assertEquals(1, ops.size, "forEach should emit exactly 1 ForOp")
        val forOp = assertIs<ForOp>(ops[0])
        assertEquals("_arr_bricks_i", forOp.variable)
        assertEquals(Literal(0), forOp.from)
        assertEquals(Literal(4), forOp.to)
        assertEquals(1, forOp.body.size, "forEach body should have 1 op from block")
    }

    @Test
    fun `forEach element expression is ArrayAccessExpr at loop index`() {
        var capturedElement: io.github.gbkt.core.ir.Expr? = null

        val ops = buildScript { bricks.forEach { element -> capturedElement = element } }

        val forOp = assertIs<ForOp>(ops[0])
        val element = capturedElement
        val arr = assertIs<ArrayAccessExpr>(element, "element should be ArrayAccessExpr")
        assertEquals("bricks", arr.array)
        assertEquals(VarRef("_arr_bricks_i"), arr.index)
    }

    // =========================================================================
    // indexOf — emits init assign + ForOp with IfOp in body, returns VarRef
    // =========================================================================

    @Test
    fun `indexOf emits Assign then ForOp and returns VarRef to result variable`() {
        var resultExpr: io.github.gbkt.core.ir.Expr? = null
        val ops = buildScript { resultExpr = bricks.indexOf(Literal(1)) }

        // Should emit: Assign(sentinel) + ForOp
        assertEquals(2, ops.size, "indexOf should emit 2 ops: sentinel assign + search loop")
        // First op: initialize sentinel to size
        val init = assertIs<Assign>(ops[0])
        assertEquals("_arr_bricks_idx", init.target)
        assertEquals(Literal(5), init.value, "sentinel should be size=5")
        // Second op: search ForOp
        val forOp = assertIs<ForOp>(ops[1])
        assertEquals("_arr_bricks_i", forOp.variable)
        assertEquals(1, forOp.body.size, "loop body should have 1 IfOp")
        assertIs<IfOp>(forOp.body[0])
        // Return value is VarRef to result variable
        assertEquals(VarRef("_arr_bricks_idx"), resultExpr)
    }

    // =========================================================================
    // count — emits zero-init assign + ForOp with IfOp in body, returns VarRef
    // =========================================================================

    @Test
    fun `count emits Assign zero then ForOp and returns VarRef to counter variable`() {
        var resultExpr: io.github.gbkt.core.ir.Expr? = null
        val ops = buildScript { resultExpr = bricks.count(Literal(1)) }

        // Should emit: Assign(0) + ForOp
        assertEquals(2, ops.size, "count should emit 2 ops: zero init + count loop")
        // First op: initialize counter to 0
        val init = assertIs<Assign>(ops[0])
        assertEquals("_arr_bricks_cnt", init.target)
        assertEquals(Literal(0), init.value)
        // Second op: count ForOp
        val forOp = assertIs<ForOp>(ops[1])
        assertEquals("_arr_bricks_i", forOp.variable)
        assertEquals(1, forOp.body.size, "loop body should have 1 IfOp")
        val ifOp = assertIs<IfOp>(forOp.body[0])
        assertEquals(1, ifOp.then.size, "if body should have 1 Assign (increment)")
        val increment = assertIs<Assign>(ifOp.then[0])
        assertEquals("_arr_bricks_cnt", increment.target)
        assertEquals(AssignOp.ADD, increment.op, "increment should use ADD op")
        // Return value is VarRef to counter variable
        assertEquals(VarRef("_arr_bricks_cnt"), resultExpr)
    }
}
