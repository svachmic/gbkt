/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.ast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CAstTest {

    // =========================================================================
    // TEST 1: CStatement sealed when is exhaustive (no else needed)
    // =========================================================================
    @Test
    fun `CStatement sealed when is exhaustive`() {
        val stmt: CStatement = CRawCode("test;")
        // This when must compile without an else branch — proves the sealed hierarchy is
        // exhaustive.
        val result =
            when (stmt) {
                is CIf -> "if"
                is CFor -> "for"
                is CWhile -> "while"
                is CSwitch -> "switch"
                is CReturn -> "return"
                is CBlock -> "block"
                is CVarDecl -> "varDecl"
                is CExprStatement -> "exprStatement"
                is CRawCode -> "rawCode"
                is CComment -> "comment"
                is CBlankLine -> "blankLine"
                is CBreak -> "break"
                is CContinue -> "continue"
            }
        assertEquals("rawCode", result)
    }

    // =========================================================================
    // TEST 2: CExpr sealed when is exhaustive (no else needed)
    // =========================================================================
    @Test
    fun `CExpr sealed when is exhaustive`() {
        val expr: CExpr = CVar("x")
        // This when must compile without an else branch — proves the sealed hierarchy is
        // exhaustive.
        val result =
            when (expr) {
                is CLiteral -> "literal"
                is CIntLiteral -> "intLiteral"
                is CStringLiteral -> "stringLiteral"
                is CVar -> "var"
                is CBinaryExpr -> "binary"
                is CUnaryExpr -> "unary"
                is CCall -> "call"
                is CTernary -> "ternary"
                is CArrayAccess -> "arrayAccess"
                is CCast -> "cast"
                is CRawExpr -> "rawExpr"
            }
        assertEquals("var", result)
    }

    // =========================================================================
    // TEST 3: CType sealed when is exhaustive (no else needed)
    // =========================================================================
    @Test
    fun `CType sealed when is exhaustive`() {
        val type: CType = CU8
        // This when must compile without an else branch — proves the sealed hierarchy is
        // exhaustive.
        val result =
            when (type) {
                CU8 -> "u8"
                CU16 -> "u16"
                CI8 -> "i8"
                CI16 -> "i16"
                CVoid -> "void"
                is CPointer -> "pointer"
                is CArray -> "array"
                is CConst -> "const"
            }
        assertEquals("u8", result)
    }

    // =========================================================================
    // TEST 4: CFile bank field assignment
    // =========================================================================
    @Test
    fun `CFile bank field assignment`() {
        val file = CFile(name = "bank1.c", bank = 1)
        assertEquals(1, file.bank)
    }

    // =========================================================================
    // TEST 5: CFunction bank inherits null by default
    // =========================================================================
    @Test
    fun `CFunction bank inherits null by default`() {
        val func = CFunction(name = "update", returnType = CVoid)
        assertNull(func.bank)
    }

    // =========================================================================
    // TEST 6: CFunction isBanked flag
    // =========================================================================
    @Test
    fun `CFunction isBanked flag`() {
        val func = CFunction(name = "renderScene", returnType = CVoid, isBanked = true)
        assertTrue(func.isBanked)
    }

    // =========================================================================
    // TEST 7: CIf construction with else
    // =========================================================================
    @Test
    fun `CIf construction with else`() {
        val condition = CBinaryExpr(CVar("hp"), "<", CLiteral(0))
        val thenBody = listOf<CStatement>(CReturn(CLiteral(0)))
        val elseBody = listOf<CStatement>(CExprStatement(CCall("update", emptyList())))

        val ifStmt = CIf(condition = condition, thenBody = thenBody, elseBody = elseBody)

        assertEquals(1, ifStmt.thenBody.size)
        assertEquals(1, ifStmt.elseBody.size)
        assertEquals(condition, ifStmt.condition)
    }

    // =========================================================================
    // TEST 8: CRawCode escape hatch exists
    // =========================================================================
    @Test
    fun `CRawCode escape hatch exists as CStatement`() {
        val raw = CRawCode("SWITCH_ROM(1);")
        assertEquals("SWITCH_ROM(1);", raw.code)
        assertTrue(raw is CStatement)
    }

    // =========================================================================
    // TEST 9: CFile contains functions list
    // =========================================================================
    @Test
    fun `CFile contains functions list`() {
        val func1 = CFunction(name = "init", returnType = CVoid)
        val func2 = CFunction(name = "update", returnType = CVoid)
        val file = CFile(name = "main.c", bank = 0, functions = listOf(func1, func2))

        assertEquals(2, file.functions.size)
        assertEquals("init", file.functions[0].name)
        assertEquals("update", file.functions[1].name)
    }

    // =========================================================================
    // BONUS TESTS: Edge cases and additional coverage
    // =========================================================================

    @Test
    fun `CRawExpr escape hatch exists as CExpr`() {
        val raw = CRawExpr("gbdk_special_call()")
        assertEquals("gbdk_special_call()", raw.code)
        assertTrue(raw is CExpr)
    }

    @Test
    fun `CFile default bank is zero`() {
        val file = CFile(name = "main.c")
        assertEquals(0, file.bank)
    }

    @Test
    fun `CFunction with explicit bank override`() {
        val func = CFunction(name = "bankedFunc", returnType = CU8, bank = 2, isBanked = true)
        assertEquals(2, func.bank)
        assertTrue(func.isBanked)
    }

    @Test
    fun `CVarDecl immutable no var fields`() {
        val decl =
            CVarDecl(
                name = "score",
                type = CU16,
                initializer = CLiteral(0),
                isStatic = true,
                isConst = false,
            )
        assertEquals("score", decl.name)
        assertEquals(CU16, decl.type)
        assertTrue(decl.isStatic)
        assertEquals(CLiteral(0), decl.initializer)
    }

    @Test
    fun `CPointer wraps inner type`() {
        val ptrType = CPointer(CU8)
        assertEquals(CU8, ptrType.pointedType)
    }

    @Test
    fun `CArray with null size is unbounded`() {
        val arrayType = CArray(CU8, size = null)
        assertNull(arrayType.size)
    }

    @Test
    fun `CSwitch with default case`() {
        val defaultCase = CSwitchCase(value = null, body = listOf(CRawCode("break;")))
        val switchStmt = CSwitch(expr = CVar("state"), cases = listOf(defaultCase))
        assertNull(switchStmt.cases[0].value)
        assertEquals(1, switchStmt.cases.size)
    }

    @Test
    fun `CComment stores text`() {
        val comment = CComment("Scene: gameplay")
        assertEquals("Scene: gameplay", comment.text)
        assertTrue(comment is CStatement)
    }

    @Test
    fun `CBlankLine is a CStatement`() {
        assertTrue(CBlankLine is CStatement)
    }
}
