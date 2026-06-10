/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CArrayAccess
import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CCast
import io.github.gbkt.backend.gbdk.codegen.ast.CI16
import io.github.gbkt.backend.gbdk.codegen.ast.CI8
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CStringLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CTernary
import io.github.gbkt.backend.gbdk.codegen.ast.CU16
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CUnaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.core.ir.ArrayAccessExpr
import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.CallExpr
import io.github.gbkt.core.ir.CastExpr
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.PropertyAccessExpr
import io.github.gbkt.core.ir.StringLiteral
import io.github.gbkt.core.ir.TernaryExpr
import io.github.gbkt.core.ir.UnaryExpr
import io.github.gbkt.core.ir.UnaryOp
import io.github.gbkt.core.ir.VarRef
import io.github.gbkt.core.ir.VarType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExprVisitorTest {

    // =========================================================================
    // TEST 1: Literal converts to CLiteral
    // =========================================================================
    @Test
    fun `Literal converts to CLiteral`() {
        val result = ExprVisitor.visit(Literal(42))
        assertEquals(CLiteral(42), result)
    }

    // =========================================================================
    // TEST 2: StringLiteral converts to CStringLiteral
    // =========================================================================
    @Test
    fun `StringLiteral converts to CStringLiteral`() {
        val result = ExprVisitor.visit(StringLiteral("PONG"))
        assertEquals(CStringLiteral("PONG"), result)
    }

    // =========================================================================
    // TEST 3: VarRef converts to CVar with underscore prefix and dot sanitization
    // =========================================================================
    @Test
    fun `VarRef converts to CVar with dot-to-underscore sanitization`() {
        val result = ExprVisitor.visit(VarRef("ball.x"))
        assertEquals(CVar("_ball_x"), result)
    }

    @Test
    fun `VarRef simple name gets underscore prefix`() {
        val result = ExprVisitor.visit(VarRef("score"))
        assertEquals(CVar("_score"), result)
    }

    // =========================================================================
    // TEST 4: BinaryExpr ADD converts to CBinaryExpr
    // =========================================================================
    @Test
    fun `BinaryExpr ADD converts to CBinaryExpr with plus operator`() {
        val result = ExprVisitor.visit(BinaryExpr(VarRef("x"), BinaryOp.ADD, Literal(1)))
        assertEquals(CBinaryExpr(CVar("_x"), "+", CLiteral(1)), result)
    }

    // =========================================================================
    // TEST 5: BinaryExpr GTE, LT, EQ operator mapping
    // =========================================================================
    @Test
    fun `BinaryExpr GTE converts to CBinaryExpr with GTE operator`() {
        val result = ExprVisitor.visit(BinaryExpr(VarRef("hp"), BinaryOp.GTE, Literal(0)))
        assertEquals(CBinaryExpr(CVar("_hp"), ">=", CLiteral(0)), result)
    }

    @Test
    fun `BinaryExpr LT converts to CBinaryExpr with LT operator`() {
        val result = ExprVisitor.visit(BinaryExpr(VarRef("x"), BinaryOp.LT, Literal(160)))
        assertEquals(CBinaryExpr(CVar("_x"), "<", CLiteral(160)), result)
    }

    @Test
    fun `BinaryExpr EQ converts to CBinaryExpr with EQ operator`() {
        val result = ExprVisitor.visit(BinaryExpr(VarRef("state"), BinaryOp.EQ, Literal(1)))
        assertEquals(CBinaryExpr(CVar("_state"), "==", CLiteral(1)), result)
    }

    // =========================================================================
    // TEST 6: UnaryExpr NOT converts to CUnaryExpr
    // =========================================================================
    @Test
    fun `UnaryExpr LOGICAL_NOT converts to CUnaryExpr with exclamation`() {
        val result = ExprVisitor.visit(UnaryExpr(UnaryOp.LOGICAL_NOT, VarRef("flag")))
        assertEquals(CUnaryExpr("!", CVar("_flag")), result)
    }

    @Test
    fun `UnaryExpr NEGATE converts to CUnaryExpr with minus`() {
        val result = ExprVisitor.visit(UnaryExpr(UnaryOp.NEGATE, VarRef("dx")))
        assertEquals(CUnaryExpr("-", CVar("_dx")), result)
    }

    @Test
    fun `UnaryExpr BITWISE_NOT converts to CUnaryExpr with tilde`() {
        val result = ExprVisitor.visit(UnaryExpr(UnaryOp.BITWISE_NOT, VarRef("mask")))
        assertEquals(CUnaryExpr("~", CVar("_mask")), result)
    }

    // =========================================================================
    // TEST 7: CallExpr converts to CCall
    // =========================================================================
    @Test
    fun `CallExpr converts to CCall with mapped arguments`() {
        val result = ExprVisitor.visit(CallExpr("joypad_pressed", listOf(StringLiteral("start"))))
        assertEquals(CCall("joypad_pressed", listOf(CStringLiteral("start"))), result)
    }

    @Test
    fun `CallExpr with no args converts to CCall with empty args`() {
        val result = ExprVisitor.visit(CallExpr("rand", emptyList()))
        assertEquals(CCall("rand", emptyList()), result)
    }

    // =========================================================================
    // TEST 8: TernaryExpr converts to CTernary
    // =========================================================================
    @Test
    fun `TernaryExpr converts to CTernary with correct mapping`() {
        val condition = BinaryExpr(VarRef("hp"), BinaryOp.GT, Literal(0))
        val thenExpr = Literal(1)
        val elseExpr = Literal(0)

        val result = ExprVisitor.visit(TernaryExpr(condition, thenExpr, elseExpr))

        val expected =
            CTernary(CBinaryExpr(CVar("_hp"), ">", CLiteral(0)), CLiteral(1), CLiteral(0))
        assertEquals(expected, result)
    }

    // =========================================================================
    // TEST 9: ArrayAccessExpr converts to CArrayAccess
    // =========================================================================
    @Test
    fun `ArrayAccessExpr converts to CArrayAccess`() {
        val result = ExprVisitor.visit(ArrayAccessExpr("inventory", Literal(0)))
        assertEquals(CArrayAccess(CVar("_inventory"), CLiteral(0)), result)
    }

    @Test
    fun `ArrayAccessExpr with dynamic index converts to CArrayAccess`() {
        val result = ExprVisitor.visit(ArrayAccessExpr("scores", VarRef("slot")))
        assertEquals(CArrayAccess(CVar("_scores"), CVar("_slot")), result)
    }

    // =========================================================================
    // TEST 10: PropertyAccessExpr converts to CVar with dot-to-underscore
    // =========================================================================
    @Test
    fun `PropertyAccessExpr converts to CVar with combined name`() {
        val result = ExprVisitor.visit(PropertyAccessExpr("player", "x"))
        assertEquals(CVar("_player_x"), result)
    }

    @Test
    fun `PropertyAccessExpr with ball objectId converts to CVar`() {
        val result = ExprVisitor.visit(PropertyAccessExpr("ball", "y"))
        assertEquals(CVar("_ball_y"), result)
    }

    // =========================================================================
    // TEST 11: CastExpr generates correct C type cast expressions
    // =========================================================================
    @Test
    fun `CastExpr U16 generates CCast with CU16 and inner expr`() {
        val result = ExprVisitor.visit(CastExpr(VarType.U16, VarRef("score")))
        assertEquals(CCast(CU16, CVar("_score")), result)
    }

    @Test
    fun `CastExpr U8 generates CCast with CU8`() {
        val result = ExprVisitor.visit(CastExpr(VarType.U8, VarRef("value")))
        assertEquals(CCast(CU8, CVar("_value")), result)
    }

    @Test
    fun `CastExpr I8 generates CCast with CI8`() {
        val result = ExprVisitor.visit(CastExpr(VarType.I8, VarRef("dx")))
        assertEquals(CCast(CI8, CVar("_dx")), result)
    }

    @Test
    fun `CastExpr I16 generates CCast with CI16`() {
        val result = ExprVisitor.visit(CastExpr(VarType.I16, VarRef("offset")))
        assertEquals(CCast(CI16, CVar("_offset")), result)
    }

    @Test
    fun `CastExpr emits (UINT16)(_score) in C output via CEmitter`() {
        // Verifies the full pipeline: CastExpr -> CCast -> CEmitter renders "(UINT16)(_score)"
        val cExpr = ExprVisitor.visit(CastExpr(VarType.U16, VarRef("score")))
        val cast = cExpr as CCast
        // CCast is emitted as "(TYPE)inner" by CEmitter
        assertEquals(CU16, cast.type)
        assertEquals(CVar("_score"), cast.expr)
    }

    // =========================================================================
    // TEST 13: BinaryOp covers all 18 values — each maps to valid C operator
    // =========================================================================
    @Test
    fun `BinaryOp to C operator mapping covers all 18 BinaryOp values`() {
        val validCOperators =
            setOf(
                "+",
                "-",
                "*",
                "/",
                "%",
                "&",
                "|",
                "^",
                "<<",
                ">>",
                "==",
                "!=",
                "<",
                "<=",
                ">",
                ">=",
                "&&",
                "||",
            )

        for (binaryOp in BinaryOp.entries) {
            val expr = BinaryExpr(Literal(1), binaryOp, Literal(2))
            val result = ExprVisitor.visit(expr)
            val cBinExpr = result as? CBinaryExpr
            assertNotNull(cBinExpr, "Expected CBinaryExpr for BinaryOp.$binaryOp")
            assertTrue(
                cBinExpr.op in validCOperators,
                "BinaryOp.$binaryOp produced invalid C operator: '${cBinExpr.op}'",
            )
        }
    }
}
