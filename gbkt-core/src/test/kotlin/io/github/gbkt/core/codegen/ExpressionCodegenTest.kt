/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.*
import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.entity.*
import io.github.gbkt.core.graphics.*
import io.github.gbkt.core.ir.*
import io.github.gbkt.core.scene.transition
import kotlin.test.*

/**
 * Tests for ExpressionCodegen - verifies expression code generation.
 *
 * Tests cover:
 * - Literal values (int, string)
 * - Variable references
 * - Unary operators
 * - Binary operators
 * - Ternary expressions
 * - Array access
 * - Comparison operators
 * - Logical operators
 */
class ExpressionCodegenTest {

    // =========================================================================
    // LITERAL EXPRESSIONS
    // =========================================================================

    @Test
    fun `positive integer literal generates unsigned suffix`() {
        val game =
            gbGame("test") {
                var result by u8Var(0)

                start = scene("main") { enter { result set 42 } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("42u"), "Positive integer should have 'u' suffix")
    }

    @Test
    fun `zero literal generates unsigned suffix`() {
        val game =
            gbGame("test") {
                var result by u8Var(1)

                start = scene("main") { enter { result set 0 } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("0u") || code.contains("= 0;"), "Zero should be handled correctly")
    }

    @Test
    fun `variable reference generates correct name`() {
        val game =
            gbGame("test") {
                var source by u8Var(10)
                var target by u8Var(0)

                start = scene("main") { enter { target set source } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("target = source"), "Should reference variable by name")
    }

    // =========================================================================
    // UNARY OPERATORS
    // =========================================================================

    @Test
    fun `negation generates correct C code`() {
        val game =
            gbGame("test") {
                var value by i8Var(10)
                var result by i8Var(0)

                start = scene("main") { enter { result set -value } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("-value") || code.contains("- value"), "Should generate negation")
    }

    @Test
    fun `logical not generates correct C code`() {
        val game =
            gbGame("test") {
                var flag by u8Var(1)
                var result by u8Var(0)

                start = scene("main") { every.frame { whenever(flag.isZero) { result set 1 } } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(
            code.contains("!flag") || code.contains("== 0") || code.contains("flag == 0u"),
            "Should generate logical not or zero check",
        )
    }

    // =========================================================================
    // BINARY OPERATORS
    // =========================================================================

    @Test
    fun `addition generates plus operator`() {
        val game =
            gbGame("test") {
                var a by u8Var(5)
                var b by u8Var(3)
                var result by u8Var(0)

                start = scene("main") { enter { result set (a + b) } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("+"), "Should generate addition operator")
    }

    @Test
    fun `subtraction generates minus operator`() {
        val game =
            gbGame("test") {
                var a by u8Var(10)
                var b by u8Var(3)
                var result by u8Var(0)

                start = scene("main") { enter { result set (a - b) } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("-") || code.contains("7u"), "Should generate subtraction")
    }

    @Test
    fun `multiplication generates star operator`() {
        val game =
            gbGame("test") {
                var a by u8Var(4)
                var b by u8Var(3)
                var result by u8Var(0)

                start = scene("main") { enter { result set (a * b) } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("*") || code.contains("12u"), "Should generate multiplication")
    }

    @Test
    fun `division generates slash operator`() {
        val game =
            gbGame("test") {
                var a by u8Var(20)
                var b by u8Var(4)
                var result by u8Var(0)

                start = scene("main") { enter { result set (a / b) } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("/") || code.contains("5u"), "Should generate division")
    }

    @Test
    fun `modulo generates percent operator`() {
        val game =
            gbGame("test") {
                var a by u8Var(17)
                var b by u8Var(5)
                var result by u8Var(0)

                start = scene("main") { enter { result set (a rem b) } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("%") || code.contains("2u"), "Should generate modulo")
    }

    @Test
    fun `bitwise AND generates ampersand operator`() {
        val game =
            gbGame("test") {
                var a by u8Var(0xFF)
                var b by u8Var(0x0F)
                var result by u8Var(0)

                start = scene("main") { enter { result set (a and b) } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("&") || code.contains("15u"), "Should generate bitwise AND")
    }

    @Test
    fun `bitwise OR generates pipe operator`() {
        val game =
            gbGame("test") {
                var a by u8Var(0x0F)
                var b by u8Var(0xF0)
                var result by u8Var(0)

                start = scene("main") { enter { result set (a or b) } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("|") || code.contains("255u"), "Should generate bitwise OR")
    }

    @Test
    fun `left shift generates double less-than`() {
        val game =
            gbGame("test") {
                var a by u8Var(1)
                var result by u8Var(0)

                start = scene("main") { enter { result set (a shl 2) } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("<<") || code.contains("4u"), "Should generate left shift")
    }

    @Test
    fun `right shift generates double greater-than`() {
        val game =
            gbGame("test") {
                var a by u8Var(16)
                var result by u8Var(0)

                start = scene("main") { enter { result set (a shr 2) } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains(">>") || code.contains("4u"), "Should generate right shift")
    }

    // =========================================================================
    // COMPARISON OPERATORS
    // =========================================================================

    @Test
    fun `equals comparison generates double equals`() {
        val game =
            gbGame("test") {
                var a by u8Var(5)
                var result by u8Var(0)

                start = scene("main") { every.frame { whenever(a isEqualTo 5) { result set 1 } } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("=="), "Should generate equals comparison")
    }

    @Test
    fun `not equals comparison generates bang equals`() {
        val game =
            gbGame("test") {
                var a by u8Var(5)
                var result by u8Var(0)

                start =
                    scene("main") { every.frame { whenever(a isNotEqualTo 0) { result set 1 } } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("!=") || code.contains("!"), "Should generate not equals")
    }

    @Test
    fun `greater than comparison generates angle bracket`() {
        val game =
            gbGame("test") {
                var a by u8Var(10)
                var result by u8Var(0)

                start = scene("main") { every.frame { whenever(a isAbove 5) { result set 1 } } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains(">"), "Should generate greater than")
    }

    @Test
    fun `less than comparison generates angle bracket`() {
        val game =
            gbGame("test") {
                var a by u8Var(3)
                var result by u8Var(0)

                start = scene("main") { every.frame { whenever(a isBelow 10) { result set 1 } } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("<"), "Should generate less than")
    }

    @Test
    fun `greater or equal comparison generates operators`() {
        val game =
            gbGame("test") {
                var a by u8Var(5)
                var result by u8Var(0)

                start = scene("main") { every.frame { whenever(a isAtLeast 5) { result set 1 } } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains(">="), "Should generate greater or equal")
    }

    @Test
    fun `less or equal comparison generates operators`() {
        val game =
            gbGame("test") {
                var a by u8Var(5)
                var result by u8Var(0)

                start = scene("main") { every.frame { whenever(a isAtMost 10) { result set 1 } } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("<="), "Should generate less or equal")
    }

    // =========================================================================
    // LOGICAL OPERATORS
    // =========================================================================

    @Test
    fun `logical AND generates double ampersand`() {
        val game =
            gbGame("test") {
                var a by u8Var(1)
                var b by u8Var(1)
                var result by u8Var(0)

                start =
                    scene("main") {
                        every.frame { whenever((a isAbove 0) and (b isAbove 0)) { result set 1 } }
                    }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("&&"), "Should generate logical AND")
    }

    @Test
    fun `logical OR generates double pipe`() {
        val game =
            gbGame("test") {
                var a by u8Var(1)
                var b by u8Var(0)
                var result by u8Var(0)

                start =
                    scene("main") {
                        every.frame { whenever((a isAbove 0) or (b isAbove 0)) { result set 1 } }
                    }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("||"), "Should generate logical OR")
    }

    // =========================================================================
    // TERNARY EXPRESSIONS
    // =========================================================================

    @Test
    fun `ternary expression generates question-colon syntax`() {
        val game =
            gbGame("test") {
                var flag by u8Var(1)
                var result by u8Var(0)

                start =
                    scene("main") {
                        every.frame {
                            val cond = flag.isNonZero
                            result set cond.then(10, 5)
                        }
                    }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("?") && code.contains(":"), "Should generate ternary operator")
    }

    @Test
    fun `ternary with infix syntax generates correctly`() {
        val game =
            gbGame("test") {
                var flag by u8Var(1)
                var result by u8Var(0)

                start =
                    scene("main") {
                        every.frame {
                            val cond = flag.isNonZero
                            result set (cond then 20 otherwise 10)
                        }
                    }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(
            code.contains("?") && code.contains(":"),
            "Should generate ternary with infix syntax",
        )
    }

    // =========================================================================
    // ARRAY ACCESS
    // =========================================================================

    @Test
    fun `array access with literal index generates bracket notation`() {
        val game =
            gbGame("test") {
                val data by u8Array(10)
                var result by u8Var(0)

                start = scene("main") { enter { result set data[3] } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("data[") && code.contains("3"), "Should generate array access")
    }

    @Test
    fun `array access with variable index generates bracket notation`() {
        val game =
            gbGame("test") {
                val data by u8Array(10)
                var idx by u8Var(5)
                var result by u8Var(0)

                start = scene("main") { enter { result set data[idx] } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(
            code.contains("data[") && code.contains("idx"),
            "Should generate array access with variable",
        )
    }

    // =========================================================================
    // ENTITY PROPERTY ACCESS
    // =========================================================================

    @Test
    fun `entity x property generates correct variable name`() {
        val game =
            gbGame("test") {
                val player by entity { position(80, 72) }
                var result by u8Var(0)

                start = scene("main") { every.frame { result set player.x } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("player_x"), "Should generate entity x property")
    }

    @Test
    fun `entity y property generates correct variable name`() {
        val game =
            gbGame("test") {
                val player by entity { position(80, 72) }
                var result by u8Var(0)

                start = scene("main") { every.frame { result set player.y } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("player_y"), "Should generate entity y property")
    }

    // =========================================================================
    // CAMERA EXPRESSIONS
    // =========================================================================

    @Test
    fun `camera x generates correct variable`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                var result by u8Var(0)

                start = scene("main") { every.frame { result set cam.x } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("_camera_x"), "Should generate camera x")
    }

    @Test
    fun `camera y generates correct variable`() {
        val game =
            gbGame("test") {
                val cam = camera {}
                var result by u8Var(0)

                start = scene("main") { every.frame { result set cam.y } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("_camera_y"), "Should generate camera y")
    }

    // =========================================================================
    // POOL EXPRESSIONS
    // =========================================================================

    @Test
    fun `pool hasSpace generates capacity check`() {
        val game =
            gbGame("test") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        onFrame {}
                    }

                start =
                    scene("main") { every.frame { whenever(bullets.hasSpace) { bullets.spawn() } } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(
            code.contains("bullet_pool_count") || code.contains("BULLET_POOL_SIZE"),
            "Should check pool capacity",
        )
    }

    @Test
    fun `pool isFull generates capacity check`() {
        val game =
            gbGame("test") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        onFrame {}
                    }
                var result by u8Var(0)

                start = scene("main") { every.frame { whenever(bullets.isFull) { result set 1 } } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(
            code.contains("bullet_pool_count") || code.contains("BULLET_POOL_SIZE"),
            "Should check if pool is full",
        )
    }

    @Test
    fun `pool activeCount generates count variable`() {
        val game =
            gbGame("test") {
                val bullets =
                    pool("bullet", size = 8) {
                        position(0, 0)
                        onFrame {}
                    }
                var result by u8Var(0)

                start = scene("main") { every.frame { result set bullets.activeCount } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("bullet_pool_count"), "Should reference pool count")
    }

    // =========================================================================
    // MENU EXPRESSIONS
    // =========================================================================

    @Test
    fun `menu isVisible generates visibility flag check`() {
        val game =
            gbGame("test") {
                val pauseMenu = menu("pause") { item("Resume") }
                var result by u8Var(0)

                start =
                    scene("main") { every.frame { whenever(pauseMenu.isVisible) { result set 1 } } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("_pause_visible"), "Should check menu visibility")
    }

    @Test
    fun `menu selectedIndex generates cursor index`() {
        val game =
            gbGame("test") {
                val pauseMenu =
                    menu("pause") {
                        item("Resume")
                        item("Quit")
                    }
                var result by u8Var(0)

                start = scene("main") { every.frame { result set pauseMenu.selectedIndex } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(code.contains("_pause_cursor"), "Should reference menu cursor")
    }

    // =========================================================================
    // DIALOG EXPRESSIONS
    // =========================================================================

    @Test
    fun `dialog isActive generates active flag check`() {
        val game =
            gbGame("test") {
                val chat = dialog("chat") {}
                var result by u8Var(0)

                start = scene("main") { every.frame { whenever(chat.isActive) { result set 1 } } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(
            code.contains("_chat_active") || code.contains("chat"),
            "Should check dialog active state",
        )
    }

    // =========================================================================
    // TRANSITION EXPRESSIONS
    // =========================================================================

    @Test
    fun `transition isActive generates active flag check`() {
        val game =
            gbGame("test") {
                var result by u8Var(0)

                start =
                    scene("main") { every.frame { whenever(transition.isActive) { result set 1 } } }
            }

        val code = CodeGenerator(game).generate()

        assertTrue(
            code.contains("_transition_active") || code.contains("transition"),
            "Should check transition active state",
        )
    }
}
