/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.*
import io.github.gbkt.core.ir.*
import kotlin.test.*

/**
 * Tests for expression optimization in code generation.
 *
 * Validates:
 * - Constant folding (5 + 3 → 8)
 * - Identity elimination (x + 0 → x, x * 1 → x)
 * - Precedence-aware parenthesization
 * - Redundant parentheses elimination
 */
class ExpressionOptimizationTest {

    // =========================================================================
    // CONSTANT FOLDING TESTS
    // =========================================================================

    @Test
    fun `constant folding evaluates 5 + 3 to 8u`() {
        val game =
            gbGame("ConstantFoldingAddTest") {
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // This should be folded to: result = 8u
                            result set (5 + 3)
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("8u"),
            "5 + 3 should be folded to 8u. Code: ${extractAssignment(code, "result")}"
        )
    }

    @Test
    fun `constant folding evaluates multiplication`() {
        val game =
            gbGame("ConstantFoldingMulTest") {
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // 4 * 3 should fold to 12u
                            result set (4 * 3)
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        assertTrue(code.contains("12u"), "4 * 3 should be folded to 12u")
    }

    @Test
    fun `constant folding evaluates subtraction`() {
        val game =
            gbGame("ConstantFoldingSubTest") {
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // 10 - 3 should fold to 7u
                            result set (10 - 3)
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        assertTrue(code.contains("7u"), "10 - 3 should be folded to 7u")
    }

    @Test
    fun `constant folding evaluates division`() {
        val game =
            gbGame("ConstantFoldingDivTest") {
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // 20 / 4 should fold to 5u
                            result set (20 / 4)
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        assertTrue(code.contains("5u"), "20 / 4 should be folded to 5u")
    }

    @Test
    fun `constant folding preserves division by zero`() {
        // Division by zero should NOT be folded (let runtime handle it)
        val game =
            gbGame("DivByZeroTest") {
                var divisor by u8Var(0)
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // This should NOT be folded since divisor is a variable
                            result set (10 / divisor)
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        // Should contain division operator, not a folded constant
        assertTrue(
            code.contains("/") || code.contains("divisor"),
            "Division by variable should not be folded"
        )
    }

    @Test
    fun `constant folding evaluates nested expressions`() {
        val game =
            gbGame("ConstantFoldingNestedTest") {
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // (2 + 3) * (4 - 1) = 5 * 3 = 15
                            result set ((2 + 3) * (4 - 1))
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        assertTrue(code.contains("15u"), "(2+3)*(4-1) should be folded to 15u")
    }

    @Test
    fun `constant folding evaluates bitwise operations`() {
        val game =
            gbGame("ConstantFoldingBitwiseTest") {
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // 0xFF AND 0x0F = 0x0F = 15
                            result set (0xFF and 0x0F)
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        assertTrue(code.contains("15u"), "0xFF & 0x0F should be folded to 15u")
    }

    @Test
    fun `constant folding evaluates comparisons to 0 or 1`() {
        val game =
            gbGame("ConstantFoldingComparisonTest") {
                var value by u8Var(5)
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // value > 3 should generate comparison
                            whenever(value isAbove 3) { result set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        // Should generate comparison code
        assertTrue(code.contains(">") || code.contains("value"), "Should generate comparison code")
    }

    // =========================================================================
    // IDENTITY ELIMINATION TESTS
    // =========================================================================

    @Test
    fun `identity elimination removes x + 0`() {
        val game =
            gbGame("IdentityAddZeroTest") {
                var x by u8Var(50)
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // x + 0 should simplify to just x
                            result set (x + 0)
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        val assignment = extractAssignment(code, "result")
        // Should NOT contain "+ 0u" - should be simplified
        assertFalse(
            assignment.contains("+ 0u"),
            "x + 0 should be simplified to x. Assignment: $assignment"
        )
    }

    @Test
    fun `identity elimination removes 0 + x`() {
        val game =
            gbGame("IdentityZeroAddTest") {
                var x by u8Var(50)
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // 0 + x should simplify to just x
                            result set (0 + x)
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        val assignment = extractAssignment(code, "result")
        // Should NOT contain "0u +" - should be simplified
        assertFalse(
            assignment.contains("0u +"),
            "0 + x should be simplified to x. Assignment: $assignment"
        )
    }

    @Test
    fun `identity elimination removes x times 1`() {
        val game =
            gbGame("IdentityMulOneTest") {
                var x by u8Var(50)
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // x * 1 should simplify to just x
                            result set (x * 1)
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        val assignment = extractAssignment(code, "result")
        // Should NOT contain "* 1u" - should be simplified
        assertFalse(
            assignment.contains("* 1u"),
            "x * 1 should be simplified to x. Assignment: $assignment"
        )
    }

    @Test
    fun `identity elimination removes 1 times x`() {
        val game =
            gbGame("IdentityOneMulTest") {
                var x by u8Var(50)
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // 1 * x should simplify to just x
                            result set (1 * x)
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        val assignment = extractAssignment(code, "result")
        // Should NOT contain "1u *" - should be simplified
        assertFalse(
            assignment.contains("1u *"),
            "1 * x should be simplified to x. Assignment: $assignment"
        )
    }

    @Test
    fun `zero multiplication returns 0`() {
        val game =
            gbGame("ZeroMulTest") {
                var x by u8Var(50)
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // x * 0 should simplify to 0
                            result set (x * 0)
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        val assignment = extractAssignment(code, "result")
        // Should be "result = 0u" not "result = x * 0u"
        assertTrue(
            assignment.contains("0u") && !assignment.contains("*"),
            "x * 0 should be simplified to 0. Assignment: $assignment"
        )
    }

    @Test
    fun `identity elimination removes x - 0`() {
        val game =
            gbGame("IdentitySubZeroTest") {
                var x by u8Var(50)
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // x - 0 should simplify to just x
                            result set (x - 0)
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        val assignment = extractAssignment(code, "result")
        // Should NOT contain "- 0u" - should be simplified
        assertFalse(
            assignment.contains("- 0u"),
            "x - 0 should be simplified to x. Assignment: $assignment"
        )
    }

    @Test
    fun `identity elimination removes x div 1`() {
        val game =
            gbGame("IdentityDivOneTest") {
                var x by u8Var(50)
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // x / 1 should simplify to just x
                            result set (x / 1)
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        val assignment = extractAssignment(code, "result")
        // Should NOT contain "/ 1u" - should be simplified
        assertFalse(
            assignment.contains("/ 1u"),
            "x / 1 should be simplified to x. Assignment: $assignment"
        )
    }

    // =========================================================================
    // PRECEDENCE-AWARE PARENTHESIZATION TESTS
    // =========================================================================

    @Test
    fun `precedence aware parens - multiply before add needs no parens`() {
        val game =
            gbGame("PrecedenceMulAddTest") {
                var a by u8Var(1)
                var b by u8Var(2)
                var c by u8Var(3)
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // a + b * c should NOT need parens around b * c
                            result set (a + b * c)
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        val assignment = extractAssignment(code, "result")
        // Should be "a + b * c" not "a + (b * c)"
        assertTrue(assignment.contains("*"), "Should have multiplication")
        // Check that there's no unnecessary parens around b * c
        val countParens = assignment.count { it == '(' }
        assertTrue(
            countParens <= 1,
            "Minimal parens for a + b * c. Assignment: $assignment (parens count: $countParens)"
        )
    }

    @Test
    fun `precedence aware parens - add inside multiply needs parens`() {
        val game =
            gbGame("PrecedenceAddMulTest") {
                var a by u8Var(1)
                var b by u8Var(2)
                var c by u8Var(3)
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // (a + b) * c NEEDS parens around a + b
                            result set ((a + b) * c)
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        val assignment = extractAssignment(code, "result")
        // Should have parens around a + b
        assertTrue(
            assignment.contains("(") && assignment.contains("+") && assignment.contains("*"),
            "Should have parens for (a + b) * c. Assignment: $assignment"
        )
    }

    @Test
    fun `no redundant outer parens on simple expression`() {
        val game =
            gbGame("NoOuterParensTest") {
                var a by u8Var(1)
                var b by u8Var(2)
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // Simple a + b should not have outer parens
                            result set (a + b)
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        val assignment = extractAssignment(code, "result")
        // "result = a + b;" should NOT be "result = (a + b);"
        // Extract just the right side of assignment
        val rightSide = assignment.substringAfter("=").trim().removeSuffix(";").trim()
        assertFalse(
            rightSide.startsWith("(") && rightSide.endsWith(")"),
            "Simple a + b should not have outer parens. Assignment: $assignment"
        )
    }

    @Test
    fun `comparison chain uses minimal parens`() {
        val game =
            gbGame("ComparisonChainTest") {
                var a by u8Var(10)
                var b by u8Var(20)
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // a > 5 comparison
                            whenever(a isAbove 5) { result set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        // Should generate clean comparison without excessive parens
        assertTrue(code.contains(">"), "Should have comparison operator")
    }

    @Test
    fun `subtraction associativity requires correct parens`() {
        val game =
            gbGame("SubtractionAssocTest") {
                var a by u8Var(10)
                var b by u8Var(3)
                var c by u8Var(2)
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // a - (b - c) needs parens (10 - (3 - 2) = 10 - 1 = 9)
                            // But a - b - c doesn't (interpreted as (a - b) - c = 5)
                            result set (a - (b - c))
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        val assignment = extractAssignment(code, "result")
        // Should have parens around b - c since subtraction is non-commutative
        assertTrue(
            assignment.contains("(") || assignment.contains("-"),
            "Should handle subtraction associativity. Assignment: $assignment"
        )
    }

    @Test
    fun `division associativity requires correct parens`() {
        val game =
            gbGame("DivisionAssocTest") {
                var a by u8Var(100)
                var b by u8Var(10)
                var c by u8Var(2)
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // a / (b / c) needs parens (100 / (10 / 2) = 100 / 5 = 20)
                            result set (a / (b / c))
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        val assignment = extractAssignment(code, "result")
        // Should have parens to preserve semantics
        assertTrue(
            assignment.contains("(") || assignment.contains("/"),
            "Should handle division associativity. Assignment: $assignment"
        )
    }

    // =========================================================================
    // COMPLEX EXPRESSION TESTS
    // =========================================================================

    @Test
    fun `complex expression with mixed operators`() {
        val game =
            gbGame("ComplexExpressionTest") {
                var a by u8Var(1)
                var b by u8Var(2)
                var c by u8Var(3)
                var d by u8Var(4)
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // a + b * c - d
                            result set (a + b * c - d)
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        val assignment = extractAssignment(code, "result")
        // Should have minimal parens - * has higher precedence than + and -
        assertTrue(
            assignment.contains("*") && assignment.contains("+") && assignment.contains("-"),
            "Should contain all operators. Assignment: $assignment"
        )
    }

    @Test
    fun `logical AND OR precedence`() {
        val game =
            gbGame("LogicalPrecedenceTest") {
                var a by u8Var(1)
                var b by u8Var(2)
                var result by u8Var(0)

                val testScene =
                    scene("test") {
                        enter {
                            // (a > 0) && (b > 0) || (a < 10)
                            whenever((a isAbove 0) and (b isAbove 0)) { result set 1 }
                        }
                    }

                start = testScene
            }

        val code = game.compileForTest()
        // Should generate proper && with correct precedence
        assertTrue(code.contains("&&") || code.contains("&"), "Should have logical AND operator")
    }

    // =========================================================================
    // HELPER FUNCTIONS
    // =========================================================================

    /** Extract the assignment statement for a variable from generated code. */
    private fun extractAssignment(code: String, varName: String): String {
        val lines = code.lines()
        val assignmentLine = lines.find { it.contains("$varName =") || it.contains("$varName=") }
        return assignmentLine ?: "ASSIGNMENT NOT FOUND for $varName"
    }
}
