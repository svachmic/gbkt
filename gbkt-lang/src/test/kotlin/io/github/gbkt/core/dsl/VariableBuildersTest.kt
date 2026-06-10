/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.ArrayAccessExpr
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

// =============================================================================
// VARIABLE BUILDERS TESTS
// Verifies ArrayVar compile-time bounds checking (SC-3).
// =============================================================================

class VariableBuildersTest {

    private fun buildScript(
        block: ScriptBuilder.() -> Unit
    ): List<io.github.gbkt.core.ir.ScriptOp> {
        val builder = ScriptBuilder()
        ScriptBuilderContext.with(builder) { builder.block() }
        return builder.build()
    }

    private val bricks = ArrayVar("bricks", VarType.U8, 30)

    // =========================================================================
    // ArrayVar.get(Int) — compile-time bounds checking (SC-3)
    // =========================================================================

    @Test
    fun `ArrayVar get at index 0 returns ArrayAccessExpr`() {
        val expr = bricks[0]
        val access = assertIs<ArrayAccessExpr>(expr)
        assertEquals("bricks", access.array)
        assertEquals(Literal(0), access.index)
    }

    @Test
    fun `ArrayVar get at last valid index (size - 1) returns ArrayAccessExpr`() {
        val expr = bricks[29] // last valid index for size-30 array
        val access = assertIs<ArrayAccessExpr>(expr)
        assertEquals("bricks", access.array)
        assertEquals(Literal(29), access.index)
    }

    @Test
    fun `ArrayVar get at index equal to size throws IllegalArgumentException`() {
        val ex =
            assertFailsWith<IllegalArgumentException> {
                bricks[30] // one past end for size-30 array
            }
        assertContains(ex.message!!, "Array bounds error")
        assertContains(ex.message!!, "30")
    }

    @Test
    fun `ArrayVar get at negative index throws IllegalArgumentException`() {
        val ex = assertFailsWith<IllegalArgumentException> { bricks[-1] }
        assertContains(ex.message!!, "Array bounds error")
        assertContains(ex.message!!, "-1")
    }

    @Test
    fun `ArrayVar get error message contains array name`() {
        val ex = assertFailsWith<IllegalArgumentException> { bricks[50] }
        assertContains(ex.message!!, "bricks")
    }

    @Test
    fun `ArrayVar get error message contains array size`() {
        val ex = assertFailsWith<IllegalArgumentException> { bricks[30] }
        assertContains(ex.message!!, "size=30")
    }

    // =========================================================================
    // ArrayVar.set(Int, ...) — compile-time bounds checking (SC-3)
    // =========================================================================

    @Test
    fun `ArrayVar set at index 0 succeeds`() {
        // No exception thrown
        buildScript { bricks[0] = 1 }
    }

    @Test
    fun `ArrayVar set at last valid index (size - 1) succeeds`() {
        // No exception thrown
        buildScript { bricks[29] = 1 }
    }

    @Test
    fun `ArrayVar set at index equal to size throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { buildScript { bricks[30] = 1 } }
    }

    @Test
    fun `ArrayVar set at negative index throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { buildScript { bricks[-1] = 0 } }
    }

    // =========================================================================
    // ArrayVar.get(AssignableVar) and get(Expr) — no bounds check (dynamic)
    // =========================================================================

    @Test
    fun `ArrayVar get with AssignableVar index does not throw`() {
        val i = AssignableVar("i")
        // Dynamic index — no compile-time bounds check
        val expr = bricks[i]
        assertIs<ArrayAccessExpr>(expr)
    }

    // =========================================================================
    // Small array edge cases
    // =========================================================================

    @Test
    fun `single element array valid at index 0`() {
        val arr = ArrayVar("arr", VarType.U8, 1)
        val expr = arr[0]
        assertIs<ArrayAccessExpr>(expr)
    }

    @Test
    fun `single element array throws at index 1`() {
        val arr = ArrayVar("arr", VarType.U8, 1)
        assertFailsWith<IllegalArgumentException> { arr[1] }
    }
}
