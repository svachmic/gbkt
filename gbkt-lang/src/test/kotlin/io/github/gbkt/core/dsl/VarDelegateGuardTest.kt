/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import kotlin.test.Test
import kotlin.test.assertFailsWith

// =============================================================================
// VAR DELEGATE GUARD TESTS  (Wave 0 RED — Plan 13.2-01 Task 1)
//
// Pins Req #12 / WR-06 / D-04: uniform single-use guard across ALL delegate
// types (U8VarDelegate, U16VarDelegate, I8VarDelegate, I16VarDelegate,
// ArrayDelegate).
//
// Each test declares a delegate ONCE, then attempts to bind it to TWO separate
// `var`/`val` properties inside a `game { }.build()` block. The second
// `provideDelegate` call on the SAME instance MUST throw `IllegalStateException`.
//
// These tests are RED today because the single-use guard does not yet exist —
// the second `provideDelegate` currently succeeds silently (the bug WR-06
// describes). Plan 13.2-02 adds the guard to `VarDelegate` + `ArrayDelegate`,
// turning these RED tests GREEN.
//
// Reference decisions: D-04 (uniform guard), WR-06 (single-use contract).
// =============================================================================

class VarDelegateGuardTest {

    // =========================================================================
    // U8VarDelegate — UINT8 variable
    // =========================================================================

    @Test
    fun `U8VarDelegate reuse on second provideDelegate throws IllegalStateException`() {
        val d = u8Var(0)
        assertFailsWith<IllegalStateException> {
            game("Test") {
                var a by d   // first provideDelegate — OK
                var b by d   // second provideDelegate on SAME instance — must throw
                val sScene = scene("s") {}
                start = sScene
            }.build()
        }
    }

    // =========================================================================
    // U16VarDelegate — UINT16 variable
    // =========================================================================

    @Test
    fun `U16VarDelegate reuse on second provideDelegate throws IllegalStateException`() {
        val d = u16Var(0)
        assertFailsWith<IllegalStateException> {
            game("Test") {
                var a by d
                var b by d
                val sScene = scene("s") {}
                start = sScene
            }.build()
        }
    }

    // =========================================================================
    // I8VarDelegate — INT8 variable
    // =========================================================================

    @Test
    fun `I8VarDelegate reuse on second provideDelegate throws IllegalStateException`() {
        val d = i8Var(0)
        assertFailsWith<IllegalStateException> {
            game("Test") {
                var a by d
                var b by d
                val sScene = scene("s") {}
                start = sScene
            }.build()
        }
    }

    // =========================================================================
    // I16VarDelegate — INT16 variable
    // =========================================================================

    @Test
    fun `I16VarDelegate reuse on second provideDelegate throws IllegalStateException`() {
        val d = i16Var(0)
        assertFailsWith<IllegalStateException> {
            game("Test") {
                var a by d
                var b by d
                val sScene = scene("s") {}
                start = sScene
            }.build()
        }
    }

    // =========================================================================
    // ArrayDelegate — UINT8 array
    // =========================================================================

    @Test
    fun `ArrayDelegate reuse on second provideDelegate throws IllegalStateException`() {
        val d = u8Array(4)
        assertFailsWith<IllegalStateException> {
            game("Test") {
                val a by d   // first provideDelegate — OK
                val b by d   // second provideDelegate on SAME instance — must throw
                val sScene = scene("s") {}
                start = sScene
            }.build()
        }
    }
}
