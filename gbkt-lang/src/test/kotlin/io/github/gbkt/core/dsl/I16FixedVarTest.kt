/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.VarRef
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// =============================================================================
// I16 FIXED VAR TESTS  (Wave 0 RED — Plan 13.2-01 Task 1)
//
// Pins Req #3 / D-10 / D-11 / D-12: the `i16FixedVar` delegate and its
// `.toPixel()` extension.
//
// Behaviors verified:
//   (1) `i16FixedVar(64)` registers a `VariableDef` with
//       `initialValue == 1024` (= 64 shl 4).
//   (2) `posX.toPixel()` returns
//       `BinaryExpr(VarRef("posX"), BinaryOp.SHR, Literal(4))`.
//   (3) `i16FixedVar(64, fractionalBits = 4)` and `i16FixedVar(64)` produce
//       byte-identical `VariableDef` (same name, type, initialValue).
//
// These tests are RED today because neither `i16FixedVar` nor `.toPixel()`
// exist yet. The compile error IS the intended RED state — no stubs must be
// added to make them compile. Plan 13.2-03 adds production code to turn these
// GREEN.
//
// Reference decisions: D-09 (ship i16FixedVar), D-10 (fractionalBits = 4 default),
//   D-11 (pixel extraction via .toPixel()), D-12 (byte-identical lowering).
// =============================================================================

class I16FixedVarTest {

    // =========================================================================
    // Behavior 0 (CR-01 regression guard): i16FixedVar(80, fractionalBits = 8)
    //   .toPixel() must emit BinaryExpr(VarRef, SHR, Literal(8)) — not Literal(4)
    //
    // This test was added RED-first (plan 13.2-08) to lock that fractionalBits
    // from the delegate flows through to toPixel() rather than the hard default 4.
    // =========================================================================

    @Test
    fun `toPixel uses fractionalBits from i16FixedVar when non-default`() {
        var capturedExpr: io.github.gbkt.core.ir.Expr? = null
        game("Test") {
            var posX by i16FixedVar(80, fractionalBits = 8)
            val sScene = scene("s") {
                frame {
                    capturedExpr = posX.toPixel()
                }
            }
            start = sScene
        }.build()

        val expr = capturedExpr ?: error("toPixel() expression was not captured")
        val binaryExpr = assertIs<BinaryExpr>(expr,
            "toPixel() must return a BinaryExpr (CR-01)")
        assertEquals(BinaryOp.SHR, binaryExpr.op,
            "toPixel() must produce SHR (right-shift) operation (CR-01)")
        assertEquals(VarRef("posX"), binaryExpr.left,
            "toPixel() must reference variable by name via VarRef (CR-01)")
        assertEquals(Literal(8), binaryExpr.right,
            "toPixel() must emit shr 8 when i16FixedVar was declared with fractionalBits=8 (CR-01)")
    }

    // =========================================================================
    // Behavior 1: i16FixedVar(64) registers VariableDef with initialValue = 1024
    //   (64 × 16 = 64 shl 4 = 1024)
    // =========================================================================

    @Test
    fun `i16FixedVar(64) registers VariableDef with initialValue 1024`() {
        val ir = game("Test") {
            var posX by i16FixedVar(64)   // compile-fail today — i16FixedVar does not exist
            val sScene = scene("s") {}
            start = sScene
        }.build()

        val def = ir.variables.firstOrNull { it.name == "posX" }
            ?: error("Variable 'posX' not registered")
        assertEquals(VarType.I16, def.type,
            "i16FixedVar must register as VarType.I16")
        assertEquals(1024, def.initialValue,
            "i16FixedVar(64) must store initialValue = 64 shl 4 = 1024 (D-10 default fractionalBits=4)")
    }

    // =========================================================================
    // Behavior 2: posX.toPixel() returns BinaryExpr(VarRef("posX"), SHR, Literal(4))
    // =========================================================================

    @Test
    fun `posX toPixel returns BinaryExpr with SHR 4`() {
        var capturedExpr: io.github.gbkt.core.ir.Expr? = null
        game("Test") {
            var posX by i16FixedVar(64)   // compile-fail today
            val sScene = scene("s") {
                frame {
                    capturedExpr = posX.toPixel()   // compile-fail today — toPixel does not exist
                }
            }
            start = sScene
        }.build()

        val expr = capturedExpr ?: error("toPixel() expression was not captured")
        val binaryExpr = assertIs<BinaryExpr>(expr,
            "toPixel() must return a BinaryExpr (D-11)")
        assertEquals(BinaryOp.SHR, binaryExpr.op,
            "toPixel() must produce SHR (right-shift) operation (D-11)")
        assertEquals(VarRef("posX"), binaryExpr.left,
            "toPixel() must reference the variable by name via VarRef (D-11)")
        assertEquals(Literal(4), binaryExpr.right,
            "toPixel() with default fractionalBits=4 must shift by Literal(4) (D-11)")
    }

    // =========================================================================
    // Behavior 3: i16FixedVar(64, fractionalBits = 4) == i16FixedVar(64)
    //   Both produce byte-identical VariableDef
    // =========================================================================

    @Test
    fun `i16FixedVar(64, fractionalBits=4) produces same VariableDef as i16FixedVar(64)`() {
        val ir1 = game("Default") {
            var posX by i16FixedVar(64)                          // compile-fail today
            val sScene = scene("s") {}
            start = sScene
        }.build()

        val ir2 = game("Explicit") {
            var posX by i16FixedVar(64, fractionalBits = 4)     // compile-fail today
            val sScene = scene("s") {}
            start = sScene
        }.build()

        val def1 = ir1.variables.first { it.name == "posX" }
        val def2 = ir2.variables.first { it.name == "posX" }

        assertEquals(def1.type, def2.type,
            "Default and explicit fractionalBits=4 must produce identical VarType (D-12)")
        assertEquals(def1.initialValue, def2.initialValue,
            "Default and explicit fractionalBits=4 must produce identical initialValue (D-12)")
    }
}
