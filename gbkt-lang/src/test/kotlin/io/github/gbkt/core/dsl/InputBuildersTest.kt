/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.CallExpr
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.TernaryExpr
import io.github.gbkt.core.ir.VarRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// =============================================================================
// INPUT BUILDERS TESTS
// Verifies InputRef.released, dpad.x/y axis helpers, and the button/dpad singletons
// produce correct IR expressions.
// =============================================================================

class InputBuildersTest {

    // =========================================================================
    // InputRef.held — baseline (existing behavior)
    // =========================================================================

    @Test
    fun `dpad up held produces dpad_held call with J_UP`() {
        val expr = dpad.up.held
        val call = assertIs<CallExpr>(expr)
        assertEquals("dpad_held", call.function)
        assertEquals(listOf(VarRef("J_UP")), call.args)
    }

    @Test
    fun `buttons a held produces button_held call with J_A`() {
        val expr = buttons.a.held
        val call = assertIs<CallExpr>(expr)
        assertEquals("button_held", call.function)
        assertEquals(listOf(VarRef("J_A")), call.args)
    }

    // =========================================================================
    // InputRef.pressed — baseline (existing behavior)
    // =========================================================================

    @Test
    fun `dpad left pressed produces dpad_pressed call with J_LEFT`() {
        val expr = dpad.left.pressed
        val call = assertIs<CallExpr>(expr)
        assertEquals("dpad_pressed", call.function)
        assertEquals(listOf(VarRef("J_LEFT")), call.args)
    }

    @Test
    fun `buttons start pressed produces button_pressed call with J_START`() {
        val expr = buttons.start.pressed
        val call = assertIs<CallExpr>(expr)
        assertEquals("button_pressed", call.function)
        assertEquals(listOf(VarRef("J_START")), call.args)
    }

    // =========================================================================
    // InputRef.released — SC-1: falling edge input
    // =========================================================================

    @Test
    fun `dpad up released produces dpad_released call with J_UP`() {
        val expr = dpad.up.released
        val call = assertIs<CallExpr>(expr)
        assertEquals("dpad_released", call.function)
        assertEquals(listOf(VarRef("J_UP")), call.args)
    }

    @Test
    fun `dpad down released produces dpad_released call with J_DOWN`() {
        val expr = dpad.down.released
        val call = assertIs<CallExpr>(expr)
        assertEquals("dpad_released", call.function)
        assertEquals(listOf(VarRef("J_DOWN")), call.args)
    }

    @Test
    fun `dpad left released produces dpad_released call with J_LEFT`() {
        val expr = dpad.left.released
        val call = assertIs<CallExpr>(expr)
        assertEquals("dpad_released", call.function)
        assertEquals(listOf(VarRef("J_LEFT")), call.args)
    }

    @Test
    fun `dpad right released produces dpad_released call with J_RIGHT`() {
        val expr = dpad.right.released
        val call = assertIs<CallExpr>(expr)
        assertEquals("dpad_released", call.function)
        assertEquals(listOf(VarRef("J_RIGHT")), call.args)
    }

    @Test
    fun `buttons a released produces button_released call with J_A`() {
        val expr = buttons.a.released
        val call = assertIs<CallExpr>(expr)
        assertEquals("button_released", call.function)
        assertEquals(listOf(VarRef("J_A")), call.args)
    }

    @Test
    fun `buttons b released produces button_released call with J_B`() {
        val expr = buttons.b.released
        val call = assertIs<CallExpr>(expr)
        assertEquals("button_released", call.function)
        assertEquals(listOf(VarRef("J_B")), call.args)
    }

    @Test
    fun `buttons start released produces button_released call with J_START`() {
        val expr = buttons.start.released
        val call = assertIs<CallExpr>(expr)
        assertEquals("button_released", call.function)
        assertEquals(listOf(VarRef("J_START")), call.args)
    }

    @Test
    fun `buttons select released produces button_released call with J_SELECT`() {
        val expr = buttons.select.released
        val call = assertIs<CallExpr>(expr)
        assertEquals("button_released", call.function)
        assertEquals(listOf(VarRef("J_SELECT")), call.args)
    }

    // =========================================================================
    // dpad.x axis helper — SC-2: -1/0/+1 horizontal axis
    // =========================================================================

    @Test
    fun `dpad x produces nested TernaryExpr for horizontal axis`() {
        val expr = dpad.x

        // Outer ternary: dpad_held(J_LEFT) ? -1 : inner
        val outer = assertIs<TernaryExpr>(expr)
        val outerCondition = assertIs<CallExpr>(outer.condition)
        assertEquals("dpad_held", outerCondition.function)
        assertEquals(listOf(VarRef("J_LEFT")), outerCondition.args)
        assertEquals(Literal(-1), outer.thenExpr)

        // Inner ternary: dpad_held(J_RIGHT) ? 1 : 0
        val inner = assertIs<TernaryExpr>(outer.elseExpr)
        val innerCondition = assertIs<CallExpr>(inner.condition)
        assertEquals("dpad_held", innerCondition.function)
        assertEquals(listOf(VarRef("J_RIGHT")), innerCondition.args)
        assertEquals(Literal(1), inner.thenExpr)
        assertEquals(Literal(0), inner.elseExpr)
    }

    @Test
    fun `dpad x left branch evaluates to minus one`() {
        val expr = dpad.x
        val outer = assertIs<TernaryExpr>(expr)
        // When left is held: result is -1
        assertEquals(Literal(-1), outer.thenExpr)
    }

    @Test
    fun `dpad x right branch evaluates to plus one`() {
        val expr = dpad.x
        val outer = assertIs<TernaryExpr>(expr)
        val inner = assertIs<TernaryExpr>(outer.elseExpr)
        // When right is held: result is +1
        assertEquals(Literal(1), inner.thenExpr)
    }

    @Test
    fun `dpad x neutral branch evaluates to zero`() {
        val expr = dpad.x
        val outer = assertIs<TernaryExpr>(expr)
        val inner = assertIs<TernaryExpr>(outer.elseExpr)
        // When neither is held: result is 0
        assertEquals(Literal(0), inner.elseExpr)
    }

    // =========================================================================
    // dpad.y axis helper — SC-2: -1/0/+1 vertical axis
    // =========================================================================

    @Test
    fun `dpad y produces nested TernaryExpr for vertical axis`() {
        val expr = dpad.y

        // Outer ternary: dpad_held(J_UP) ? -1 : inner
        val outer = assertIs<TernaryExpr>(expr)
        val outerCondition = assertIs<CallExpr>(outer.condition)
        assertEquals("dpad_held", outerCondition.function)
        assertEquals(listOf(VarRef("J_UP")), outerCondition.args)
        assertEquals(Literal(-1), outer.thenExpr)

        // Inner ternary: dpad_held(J_DOWN) ? 1 : 0
        val inner = assertIs<TernaryExpr>(outer.elseExpr)
        val innerCondition = assertIs<CallExpr>(inner.condition)
        assertEquals("dpad_held", innerCondition.function)
        assertEquals(listOf(VarRef("J_DOWN")), innerCondition.args)
        assertEquals(Literal(1), inner.thenExpr)
        assertEquals(Literal(0), inner.elseExpr)
    }

    @Test
    fun `dpad y up branch evaluates to minus one`() {
        val expr = dpad.y
        val outer = assertIs<TernaryExpr>(expr)
        // When up is held: result is -1
        assertEquals(Literal(-1), outer.thenExpr)
    }

    @Test
    fun `dpad y down branch evaluates to plus one`() {
        val expr = dpad.y
        val outer = assertIs<TernaryExpr>(expr)
        val inner = assertIs<TernaryExpr>(outer.elseExpr)
        // When down is held: result is +1
        assertEquals(Literal(1), inner.thenExpr)
    }

    @Test
    fun `dpad y neutral branch evaluates to zero`() {
        val expr = dpad.y
        val outer = assertIs<TernaryExpr>(expr)
        val inner = assertIs<TernaryExpr>(outer.elseExpr)
        // When neither is held: result is 0
        assertEquals(Literal(0), inner.elseExpr)
    }

    // =========================================================================
    // All six dpad properties accessible
    // =========================================================================

    @Test
    fun `dpad has six properties up down left right x y`() {
        // Accessing all 6 to verify they compile and are distinct
        val upHeld = dpad.up.held
        val downHeld = dpad.down.held
        val leftHeld = dpad.left.held
        val rightHeld = dpad.right.held
        val xAxis = dpad.x
        val yAxis = dpad.y

        // All non-null
        assertIs<CallExpr>(upHeld)
        assertIs<CallExpr>(downHeld)
        assertIs<CallExpr>(leftHeld)
        assertIs<CallExpr>(rightHeld)
        assertIs<TernaryExpr>(xAxis)
        assertIs<TernaryExpr>(yAxis)
    }

    // =========================================================================
    // InputRef has three properties: held, pressed, released
    // =========================================================================

    @Test
    fun `InputRef has held pressed released for all dpad directions`() {
        for (ref in listOf(dpad.up, dpad.down, dpad.left, dpad.right)) {
            assertIs<CallExpr>(ref.held)
            assertIs<CallExpr>(ref.pressed)
            assertIs<CallExpr>(ref.released)
        }
    }

    @Test
    fun `InputRef has held pressed released for all buttons`() {
        for (ref in listOf(buttons.a, buttons.b, buttons.start, buttons.select)) {
            assertIs<CallExpr>(ref.held)
            assertIs<CallExpr>(ref.pressed)
            assertIs<CallExpr>(ref.released)
        }
    }
}
