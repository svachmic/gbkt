/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import kotlin.test.*

/**
 * Tests for the Expr sealed expression hierarchy.
 *
 * Verifies construction of each Expr subtype and the BinaryOp/UnaryOp enums.
 */
class ExprTest {

    @Test
    fun `Literal constructs correctly`() {
        val expr = Literal(42)
        assertEquals(42, expr.value)
    }

    @Test
    fun `VarRef constructs correctly`() {
        val expr = VarRef("score")
        assertEquals("score", expr.name)
    }

    @Test
    fun `BinaryExpr constructs correctly`() {
        val left = VarRef("x")
        val right = Literal(5)
        val expr = BinaryExpr(left = left, op = BinaryOp.ADD, right = right)
        assertEquals(BinaryOp.ADD, expr.op)
    }

    @Test
    fun `UnaryExpr constructs correctly`() {
        val expr = UnaryExpr(op = UnaryOp.NEGATE, operand = VarRef("x"))
        assertEquals(UnaryOp.NEGATE, expr.op)
    }

    @Test
    fun `CallExpr constructs correctly`() {
        val expr = CallExpr(function = "getHealth", args = listOf(VarRef("player")))
        assertEquals("getHealth", expr.function)
        assertEquals(1, expr.args.size)
    }

    @Test
    fun `TernaryExpr constructs correctly`() {
        val expr =
            TernaryExpr(
                condition = BinaryExpr(VarRef("hp"), BinaryOp.GT, Literal(0)),
                thenExpr = Literal(1),
                elseExpr = Literal(0),
            )
        assertNotNull(expr.condition)
        assertNotNull(expr.thenExpr)
        assertNotNull(expr.elseExpr)
    }

    @Test
    fun `ArrayAccessExpr constructs correctly`() {
        val expr = ArrayAccessExpr(array = "inventory", index = Literal(0))
        assertEquals("inventory", expr.array)
    }

    @Test
    fun `PropertyAccessExpr constructs correctly`() {
        val expr = PropertyAccessExpr(objectId = "player", property = "x")
        assertEquals("player", expr.objectId)
        assertEquals("x", expr.property)
    }

    @Test
    fun `StringLiteral constructs correctly`() {
        val expr = StringLiteral("Hello!")
        assertEquals("Hello!", expr.value)
    }

    @Test
    fun `BinaryOp has all arithmetic operators`() {
        val arithmeticOps =
            setOf(BinaryOp.ADD, BinaryOp.SUB, BinaryOp.MUL, BinaryOp.DIV, BinaryOp.MOD)
        assertTrue(BinaryOp.entries.containsAll(arithmeticOps))
    }

    @Test
    fun `BinaryOp has all bitwise operators`() {
        val bitwiseOps = setOf(BinaryOp.AND, BinaryOp.OR, BinaryOp.XOR, BinaryOp.SHL, BinaryOp.SHR)
        assertTrue(BinaryOp.entries.containsAll(bitwiseOps))
    }

    @Test
    fun `BinaryOp has all comparison operators`() {
        val compOps =
            setOf(BinaryOp.EQ, BinaryOp.NEQ, BinaryOp.LT, BinaryOp.LTE, BinaryOp.GT, BinaryOp.GTE)
        assertTrue(BinaryOp.entries.containsAll(compOps))
    }

    @Test
    fun `BinaryOp has logical operators`() {
        val logicalOps = setOf(BinaryOp.LOGICAL_AND, BinaryOp.LOGICAL_OR)
        assertTrue(BinaryOp.entries.containsAll(logicalOps))
    }

    @Test
    fun `UnaryOp has NEGATE BITWISE_NOT and LOGICAL_NOT`() {
        val expected = setOf(UnaryOp.NEGATE, UnaryOp.BITWISE_NOT, UnaryOp.LOGICAL_NOT)
        assertEquals(expected, UnaryOp.entries.toSet())
    }

    @Test
    fun `Expr subtypes are all sealed subclasses`() {
        // Verify that we can pattern-match all known Expr subtypes
        val exprs: List<Expr> =
            listOf(
                Literal(1),
                VarRef("x"),
                BinaryExpr(Literal(0), BinaryOp.ADD, Literal(1)),
                UnaryExpr(UnaryOp.NEGATE, Literal(5)),
                CallExpr("fn", emptyList()),
                TernaryExpr(Literal(1), Literal(2), Literal(3)),
                ArrayAccessExpr("arr", Literal(0)),
                PropertyAccessExpr("obj", "prop"),
                StringLiteral("text"),
            )
        // All must match via exhaustive when (compile-time check via describeExpr)
        for (expr in exprs) {
            val desc = describeExpr(expr)
            assertTrue(desc.isNotEmpty())
        }
    }

    @Test
    fun `nested expressions work correctly`() {
        // (x + 5) * (y - 2)
        val xPlus5 = BinaryExpr(VarRef("x"), BinaryOp.ADD, Literal(5))
        val yMinus2 = BinaryExpr(VarRef("y"), BinaryOp.SUB, Literal(2))
        val product = BinaryExpr(xPlus5, BinaryOp.MUL, yMinus2)

        assertTrue(product.left is BinaryExpr)
        assertTrue(product.right is BinaryExpr)
        assertEquals(BinaryOp.MUL, product.op)
    }
}
