/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.ir.IRAssign
import io.github.gbkt.core.ir.IRSceneChange
import io.github.gbkt.core.test.testLogic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the testLogic{} testing DSL.
 *
 * Validates:
 * - testLogic creates a valid scope
 * - Variables can be defined and used
 * - record{} captures IR statements
 * - RecordedIR assertions work correctly
 * - execute() runs IR against variable state
 * - expect() validates variable values
 */
class LogicTestDslTest {

    // =========================================================================
    // BASIC TESTLOGIC USAGE
    // =========================================================================

    @Test
    fun `testLogic creates valid scope`() = testLogic {
        // Just verify we can enter the scope without error
        assertTrue(true, "testLogic scope should be accessible")
    }

    @Test
    fun `testLogic allows variable creation`() = testLogic {
        var counter by u8Var(0)
        var score by u16Var(100)

        // Variables should exist (accessed via name)
        assertTrue(true, "Variables should be created successfully")
    }

    // =========================================================================
    // RECORDING IR
    // =========================================================================

    @Test
    fun `record captures IR statements`() = testLogic {
        var counter by u8Var(0)

        val recorded = record { counter += 1 }

        assertTrue(recorded.statements.isNotEmpty(), "Should capture at least one statement")
    }

    @Test
    fun `record returns RecordedIR for assertions`() = testLogic {
        var value by u8Var(0)

        val recorded = record { value set 42 }

        // Should be able to chain assertions
        recorded.assertCount(1).assertEmitted<IRAssign>()
    }

    @Test
    fun `empty record captures no statements`() = testLogic {
        val recorded = record {
            // Empty block
        }

        recorded.assertCount(0)
    }

    // =========================================================================
    // RECORDEDIR ASSERTIONS
    // =========================================================================

    @Test
    fun `assertEmitted succeeds when statement type exists`() = testLogic {
        var value by u8Var(0)

        record { value set 10 }.assertEmitted<IRAssign>()
    }

    @Test
    fun `assertEmitted fails when statement type missing`() = testLogic {
        var value by u8Var(0)

        val recorded = record { value set 10 }

        assertFailsWith<AssertionError> { recorded.assertEmitted<IRSceneChange>() }
    }

    @Test
    fun `assertNotEmitted succeeds when statement type missing`() = testLogic {
        var value by u8Var(0)

        record { value set 10 }.assertNotEmitted<IRSceneChange>()
    }

    @Test
    fun `assertNotEmitted fails when statement type exists`() = testLogic {
        var value by u8Var(0)

        val recorded = record { value set 10 }

        assertFailsWith<AssertionError> { recorded.assertNotEmitted<IRAssign>() }
    }

    @Test
    fun `assertCount succeeds with correct count`() = testLogic {
        var a by u8Var(0)
        var b by u8Var(0)

        record {
                a set 1
                b set 2
            }
            .assertCount(2)
    }

    @Test
    fun `assertCount fails with incorrect count`() = testLogic {
        var value by u8Var(0)

        val recorded = record { value set 10 }

        assertFailsWith<AssertionError> { recorded.assertCount(5) }
    }

    @Test
    fun `assertAtLeast succeeds with sufficient statements`() = testLogic {
        var a by u8Var(0)
        var b by u8Var(0)

        record {
                a set 1
                b set 2
            }
            .assertAtLeast(1)
    }

    @Test
    fun `assertAtLeast fails with insufficient statements`() = testLogic {
        var value by u8Var(0)

        val recorded = record { value set 10 }

        assertFailsWith<AssertionError> { recorded.assertAtLeast(5) }
    }

    @Test
    fun `assertFirst checks first statement type`() = testLogic {
        var value by u8Var(0)

        record { value set 10 }.assertFirst<IRAssign>()
    }

    @Test
    fun `assertEmitted with predicate filters statements`() = testLogic {
        var counter by u8Var(0)

        record { counter += 5 }.assertEmitted<IRAssign> { it.target == "counter" }
    }

    // =========================================================================
    // RECORDED IR UTILITIES
    // =========================================================================

    @Test
    fun `filter returns statements of specific type`() = testLogic {
        var value by u8Var(0)

        val recorded = record { value set 42 }
        val assigns = recorded.filter<IRAssign>()

        assertEquals(1, assigns.size, "Should find one IRAssign")
    }

    @Test
    fun `first returns first statement of type or null`() = testLogic {
        var value by u8Var(0)

        val recorded = record { value set 42 }
        val firstAssign = recorded.first<IRAssign>()
        val firstSceneChange = recorded.first<IRSceneChange>()

        assertNotNull(firstAssign, "Should find an IRAssign")
        assertEquals(null, firstSceneChange, "Should not find IRSceneChange")
    }

    @Test
    fun `toList returns all statements`() = testLogic {
        var a by u8Var(0)
        var b by u8Var(0)

        val recorded = record {
            a set 1
            b set 2
        }

        assertEquals(2, recorded.toList().size, "Should have 2 statements in list")
    }

    // =========================================================================
    // EXECUTION AND EXPECTATIONS
    // =========================================================================

    @Test
    fun `execute runs recorded IR`() = testLogic {
        var counter by u8Var(0)

        record { counter set 42 }
        execute()

        expect("counter").toEqual(42)
    }

    @Test
    fun `execute with statements parameter runs given IR`() = testLogic {
        var value by u8Var(0)

        val recorded = record { value set 100 }
        execute(recorded.statements)

        expect("value").toEqual(100)
    }

    @Test
    fun `expect toEqual succeeds with correct value`() = testLogic {
        var counter by u8Var(50)

        record { counter set 50 }
        execute()

        expect("counter").toEqual(50)
    }

    @Test
    fun `expect toEqual fails with incorrect value`() = testLogic {
        var counter by u8Var(0)

        record { counter set 42 }
        execute()

        assertFailsWith<AssertionError> { expect("counter").toEqual(999) }
    }

    @Test
    fun `expect toBeGreaterThan succeeds with greater value`() = testLogic {
        var score by u8Var(0)

        record { score set 100 }
        execute()

        expect("score").toBeGreaterThan(50)
    }

    @Test
    fun `expect toBeLessThan succeeds with lesser value`() = testLogic {
        var health by u8Var(0)

        record { health set 10 }
        execute()

        expect("health").toBeLessThan(50)
    }

    // =========================================================================
    // VARIABLE MANIPULATION
    // =========================================================================

    @Test
    fun `setVariable sets value directly`() = testLogic {
        setVariable("myVar", 100)

        expect("myVar").toEqual(100)
    }

    @Test
    fun `valueOf returns current variable value`() = testLogic {
        var test by u8Var(0)

        record { test set 77 }
        execute()

        val value = valueOf("test")
        assertEquals(77, value.toInt(), "valueOf should return 77")
    }

    // =========================================================================
    // BOOLEAN ASSERTIONS
    // =========================================================================

    @Test
    fun `expectTrue succeeds when condition is true`() = testLogic {
        expectTrue(1 + 1 == 2, "Math should work")
    }

    @Test
    fun `expectTrue fails when condition is false`() = testLogic {
        assertFailsWith<AssertionError> { expectTrue(1 + 1 == 3, "This should fail") }
    }

    @Test
    fun `expectFalse succeeds when condition is false`() = testLogic {
        expectFalse(1 + 1 == 3, "Math still works")
    }

    @Test
    fun `expectFalse fails when condition is true`() = testLogic {
        assertFailsWith<AssertionError> { expectFalse(1 + 1 == 2, "This should fail") }
    }

    // =========================================================================
    // ARITHMETIC OPERATIONS
    // =========================================================================

    @Test
    fun `addition is executed correctly`() = testLogic {
        var counter by u8Var(10)

        record { counter += 5 }
        execute()

        expect("counter").toEqual(15)
    }

    @Test
    fun `subtraction is executed correctly`() = testLogic {
        var counter by u8Var(20)

        record { counter -= 7 }
        execute()

        expect("counter").toEqual(13)
    }

    @Test
    fun `set operation is executed correctly`() = testLogic {
        var value by u8Var(0)

        record { value set 42 }
        execute()

        expect("value").toEqual(42)
    }
}
