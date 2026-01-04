/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.entity.*
import io.github.gbkt.core.graphics.*
import io.github.gbkt.core.ir.*
import kotlin.test.*

/**
 * Tests for LogicBlock DSL.
 *
 * Validates:
 * - LogicBlock recording captures IR statements
 * - LogicBlock expansion emits statements to recorder
 * - Error when calling logic blocks outside recording context
 * - include() helper function works
 */
class LogicBlockTest {

    // =========================================================================
    // BASIC LOGIC BLOCK CREATION
    // =========================================================================

    @Test
    fun `logicBlock records statements`() {
        val game =
            gbGame("LogicBlockRecordTest") {
                var counter by u8Var(0)

                // Define a logic block that modifies counter
                val incrementCounter = logicBlock("increment") { counter += 1 }

                // The logic block should have recorded statements
                assertEquals(1, incrementCounter.size, "Logic block should have 1 statement")
                assertFalse(incrementCounter.isEmpty, "Logic block should not be empty")

                start = scene("main") { every.frame {} }
            }

        assertNotNull(game, "Game should build successfully")
    }

    @Test
    fun `empty logicBlock is valid`() {
        val game =
            gbGame("EmptyLogicBlockTest") {
                val emptyBlock =
                    logicBlock("empty") {
                        // Empty block
                    }

                assertEquals(0, emptyBlock.size, "Empty logic block should have 0 statements")
                assertTrue(emptyBlock.isEmpty, "Empty logic block should be empty")

                start = scene("main") { every.frame {} }
            }

        assertNotNull(game, "Game should build successfully")
    }

    @Test
    fun `logicBlock has correct name`() {
        val game =
            gbGame("LogicBlockNameTest") {
                val namedBlock =
                    logicBlock("myLogicBlock") {
                        // Content doesn't matter for this test
                    }

                assertTrue(
                    namedBlock.toString().contains("myLogicBlock"),
                    "Logic block toString should contain name"
                )

                start = scene("main") { every.frame {} }
            }

        assertNotNull(game, "Game should build successfully")
    }

    // =========================================================================
    // LOGIC BLOCK EXPANSION
    // =========================================================================

    @Test
    fun `logicBlock expands into recording context`() {
        val game =
            gbGame("LogicBlockExpandTest") {
                var counter by u8Var(0)

                val incrementCounter = logicBlock("increment") { counter += 1 }

                start =
                    scene("main") {
                        every.frame {
                            // Expand the logic block
                            incrementCounter()
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("counter"), "Generated code should contain counter reference")
    }

    @Test
    fun `logicBlock can be expanded multiple times`() {
        val game =
            gbGame("LogicBlockMultiExpandTest") {
                var counter by u8Var(0)

                val incrementCounter = logicBlock("increment") { counter += 1 }

                start =
                    scene("main") {
                        every.frame {
                            incrementCounter()
                            incrementCounter()
                            incrementCounter()
                        }
                    }
            }

        // Should compile successfully with multiple expansions
        val code = game.compileForTest()
        assertNotNull(code, "Should generate valid code")
    }

    @Test
    fun `include helper function works`() {
        val game =
            gbGame("IncludeHelperTest") {
                var value by u8Var(0)

                val setValueBlock = logicBlock("setValue") { value set 42 }

                start = scene("main") { every.frame { include(setValueBlock) } }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("value"), "Generated code should contain value reference")
    }

    // =========================================================================
    // LOGIC BLOCK NESTING
    // =========================================================================

    @Test
    fun `logicBlocks can be nested`() {
        val game =
            gbGame("NestedLogicBlockTest") {
                var x by u8Var(0)
                var y by u8Var(0)

                val moveX = logicBlock("moveX") { x += 1 }

                val moveY = logicBlock("moveY") { y += 1 }

                val moveXY =
                    logicBlock("moveXY") {
                        moveX()
                        moveY()
                    }

                start = scene("main") { every.frame { moveXY() } }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("x"), "Generated code should contain x reference")
        assertTrue(code.contains("y"), "Generated code should contain y reference")
    }

    // =========================================================================
    // LOGIC BLOCK IN DIFFERENT CONTEXTS
    // =========================================================================

    @Test
    fun `logicBlock works in enter block`() {
        val game =
            gbGame("LogicBlockEnterTest") {
                var initialized by u8Var(0)

                val init = logicBlock("init") { initialized set 1 }

                start =
                    scene("main") {
                        enter { init() }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate valid code")
    }

    @Test
    fun `logicBlock works in whenever block`() {
        val game =
            gbGame("LogicBlockWheneverTest") {
                var jumped by u8Var(0)

                val jump = logicBlock("jump") { jumped set 1 }

                start = scene("main") { every.frame { whenever(buttons.a.pressed) { jump() } } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate valid code")
    }
}
