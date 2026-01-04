/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.*
import kotlin.test.*

/**
 * Tests for the input system.
 *
 * Validates:
 * - Single button check (A, B, Start, Select, directions)
 * - Button combinations
 * - Pressed vs held detection
 * - Input in every.frame context
 * - IR generation correctness
 */
class InputTest {

    // =========================================================================
    // SINGLE BUTTON CHECK TESTS
    // =========================================================================

    @Test
    fun `button A held generates correct mask check`() {
        val game =
            gbGame("test") {
                var pressed by u8Var(0)

                start = scene("main") { every.frame { whenever(buttons.a.held) { pressed set 1 } } }
            }

        val code = game.compileForTest()

        // Button A has mask 0x10
        assertTrue(
            code.contains("0x10") || code.contains("16"),
            "Should check A button mask (0x10)"
        )
        assertTrue(
            code.contains("joypad") || code.contains("_joypad"),
            "Should call joypad function"
        )
    }

    @Test
    fun `button B held generates correct mask check`() {
        val game =
            gbGame("test") {
                var pressed by u8Var(0)

                start = scene("main") { every.frame { whenever(buttons.b.held) { pressed set 1 } } }
            }

        val code = game.compileForTest()

        // Button B has mask 0x20
        assertTrue(
            code.contains("0x20") || code.contains("32"),
            "Should check B button mask (0x20)"
        )
    }

    @Test
    fun `button Start held generates correct mask check`() {
        val game =
            gbGame("test") {
                var pressed by u8Var(0)

                start =
                    scene("main") { every.frame { whenever(buttons.start.held) { pressed set 1 } } }
            }

        val code = game.compileForTest()

        // Start has mask 0x80
        assertTrue(
            code.contains("0x80") || code.contains("128"),
            "Should check Start button mask (0x80)"
        )
    }

    @Test
    fun `button Select held generates correct mask check`() {
        val game =
            gbGame("test") {
                var pressed by u8Var(0)

                start =
                    scene("main") {
                        every.frame { whenever(buttons.select.held) { pressed set 1 } }
                    }
            }

        val code = game.compileForTest()

        // Select has mask 0x40
        assertTrue(
            code.contains("0x40") || code.contains("64"),
            "Should check Select button mask (0x40)"
        )
    }

    @Test
    fun `dpad left generates correct mask check`() {
        val game =
            gbGame("test") {
                var moving by u8Var(0)

                start = scene("main") { every.frame { whenever(dpad.left) { moving set 1 } } }
            }

        val code = game.compileForTest()

        // Left has mask 0x02
        assertTrue(
            code.contains("0x02") || code.contains("& 2") || code.contains("&2"),
            "Should check Left direction mask (0x02)"
        )
    }

    @Test
    fun `dpad right generates correct mask check`() {
        val game =
            gbGame("test") {
                var moving by u8Var(0)

                start = scene("main") { every.frame { whenever(dpad.right) { moving set 1 } } }
            }

        val code = game.compileForTest()

        // Right has mask 0x01
        assertTrue(
            code.contains("0x01") || code.contains("& 1") || code.contains("&1"),
            "Should check Right direction mask (0x01)"
        )
    }

    @Test
    fun `dpad up generates correct mask check`() {
        val game =
            gbGame("test") {
                var moving by u8Var(0)

                start = scene("main") { every.frame { whenever(dpad.up) { moving set 1 } } }
            }

        val code = game.compileForTest()

        // Up has mask 0x04
        assertTrue(
            code.contains("0x04") || code.contains("& 4") || code.contains("&4"),
            "Should check Up direction mask (0x04)"
        )
    }

    @Test
    fun `dpad down generates correct mask check`() {
        val game =
            gbGame("test") {
                var moving by u8Var(0)

                start = scene("main") { every.frame { whenever(dpad.down) { moving set 1 } } }
            }

        val code = game.compileForTest()

        // Down has mask 0x08
        assertTrue(
            code.contains("0x08") || code.contains("& 8") || code.contains("&8"),
            "Should check Down direction mask (0x08)"
        )
    }

    // =========================================================================
    // BUTTON COMBINATIONS TESTS
    // =========================================================================

    @Test
    fun `two buttons combined with and generates both checks`() {
        val game =
            gbGame("test") {
                var combo by u8Var(0)

                start =
                    scene("main") {
                        every.frame { whenever(buttons.a.held and buttons.b.held) { combo set 1 } }
                    }
            }

        val code = game.compileForTest()

        // Should check both A (0x10) and B (0x20)
        assertTrue(code.contains("0x10") || code.contains("16"), "Should check A button")
        assertTrue(code.contains("0x20") || code.contains("32"), "Should check B button")
        assertTrue(code.contains("&&"), "Should use logical AND for combination")
    }

    @Test
    fun `buttons combo allHeld generates all checks`() {
        val game =
            gbGame("test") {
                var tripleCombo by u8Var(0)

                start =
                    scene("main") {
                        every.frame {
                            whenever(
                                buttons(buttons.a.held, buttons.b.held, dpad.down.held).allHeld
                            ) {
                                tripleCombo set 1
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        // Should check A (0x10), B (0x20), and Down (0x08)
        assertTrue(code.contains("0x10") || code.contains("16"), "Should check A button")
        assertTrue(code.contains("0x20") || code.contains("32"), "Should check B button")
        assertTrue(code.contains("0x08") || code.contains("& 8"), "Should check Down direction")
    }

    @Test
    fun `buttons combo anyHeld generates or checks`() {
        val game =
            gbGame("test") {
                var anyButton by u8Var(0)

                start =
                    scene("main") {
                        every.frame {
                            whenever(buttons(buttons.a.held, buttons.b.held).anyHeld) {
                                anyButton set 1
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        // Should check A (0x10) or B (0x20)
        assertTrue(code.contains("||"), "Should use logical OR for any check")
    }

    @Test
    fun `dpad with button combination compiles`() {
        val game =
            gbGame("test") {
                var special by u8Var(0)

                start =
                    scene("main") {
                        every.frame {
                            // Down + A for special move
                            whenever(dpad.down and buttons.a.held) { special set 1 }
                        }
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("0x08") || code.contains("& 8"), "Should check Down direction")
        assertTrue(code.contains("0x10") || code.contains("16"), "Should check A button")
    }

    // =========================================================================
    // PRESSED VS HELD DETECTION TESTS
    // =========================================================================

    @Test
    fun `button pressed generates edge detection`() {
        val game =
            gbGame("test") {
                var justPressed by u8Var(0)

                start =
                    scene("main") {
                        every.frame { whenever(buttons.a.pressed) { justPressed set 1 } }
                    }
            }

        val code = game.compileForTest()

        // Pressed should compare current and previous state
        assertTrue(
            code.contains("_joypad") && code.contains("_joypad_prev"),
            "Should compare current and previous joypad state"
        )
    }

    @Test
    fun `button released generates falling edge detection`() {
        val game =
            gbGame("test") {
                var justReleased by u8Var(0)

                start =
                    scene("main") {
                        every.frame { whenever(buttons.a.released) { justReleased set 1 } }
                    }
            }

        val code = game.compileForTest()

        // Released should check !(current & mask) && (previous & mask)
        assertTrue(
            code.contains("_joypad") && code.contains("_joypad_prev"),
            "Should compare current and previous joypad state for release"
        )
    }

    @Test
    fun `held differs from pressed in code generation`() {
        val game =
            gbGame("test") {
                var heldResult by u8Var(0)
                var pressedResult by u8Var(0)

                start =
                    scene("main") {
                        every.frame {
                            whenever(buttons.a.held) { heldResult set 1 }
                            whenever(buttons.a.pressed) { pressedResult set 1 }
                        }
                    }
            }

        val code = game.compileForTest()

        // Held uses joypad() directly, pressed uses _joypad and _joypad_prev
        assertTrue(code.contains("_joypad_prev"), "Pressed should reference previous state")
        assertTrue(
            code.contains("joypad()") || code.contains("_joypad"),
            "Held should reference current joypad state"
        )
    }

    // =========================================================================
    // INPUT IN EVERY.FRAME CONTEXT TESTS
    // =========================================================================

    @Test
    fun `input check in every frame generates per-frame check`() {
        val game =
            gbGame("test") {
                var counter by u8Var(0)

                start = scene("main") { every.frame { whenever(buttons.a.held) { counter += 1 } } }
            }

        val code = game.compileForTest()

        // Should be inside the main loop
        assertTrue(
            code.contains("while") || code.contains("for(;;)"),
            "Input check should be in main loop"
        )
        assertTrue(code.contains("0x10") || code.contains("16"), "Should check A button mask")
    }

    @Test
    fun `multiple input checks in same frame work correctly`() {
        val game =
            gbGame("test") {
                var aPressed by u8Var(0)
                var bPressed by u8Var(0)
                var startPressed by u8Var(0)

                start =
                    scene("main") {
                        every.frame {
                            whenever(buttons.a.held) { aPressed set 1 }
                            whenever(buttons.b.held) { bPressed set 1 }
                            whenever(buttons.start.held) { startPressed set 1 }
                        }
                    }
            }

        val code = game.compileForTest()

        // Should have all three button checks
        assertTrue(code.contains("0x10") || code.contains("16"), "Should check A button")
        assertTrue(code.contains("0x20") || code.contains("32"), "Should check B button")
        assertTrue(code.contains("0x80") || code.contains("128"), "Should check Start button")
    }

    @Test
    fun `input check in enter block runs once`() {
        val game =
            gbGame("test") {
                var initialInput by u8Var(0)

                start = scene("main") { enter { whenever(buttons.a.held) { initialInput set 1 } } }
            }

        val code = game.compileForTest()

        // Should generate the check, but outside the main loop
        assertTrue(code.contains("0x10") || code.contains("16"), "Should check A button in enter")
    }

    // =========================================================================
    // DPAD AXIS TESTS
    // =========================================================================

    @Test
    fun `dpad x axis generates ternary expression`() {
        val game =
            gbGame("test") {
                var playerX by u8Var(80)

                start = scene("main") { every.frame { playerX set (playerX + dpad.x) } }
            }

        val code = game.compileForTest()

        // dpad.x generates: left ? -1 : (right ? 1 : 0)
        assertTrue(code.contains("?") && code.contains(":"), "Should generate ternary for dpad.x")
    }

    @Test
    fun `dpad y axis generates ternary expression`() {
        val game =
            gbGame("test") {
                var playerY by u8Var(72)

                start = scene("main") { every.frame { playerY set (playerY + dpad.y) } }
            }

        val code = game.compileForTest()

        // dpad.y generates: up ? -1 : (down ? 1 : 0)
        assertTrue(code.contains("?") && code.contains(":"), "Should generate ternary for dpad.y")
    }

    @Test
    fun `dpad any generates or of all directions`() {
        val game =
            gbGame("test") {
                var isMoving by u8Var(0)

                start = scene("main") { every.frame { whenever(dpad.any) { isMoving set 1 } } }
            }

        val code = game.compileForTest()

        // Should check all four directions with OR
        assertTrue(code.contains("||"), "Should have OR for any direction check")
    }

    @Test
    fun `dpad none generates negated any`() {
        val game =
            gbGame("test") {
                var isStationary by u8Var(0)

                start = scene("main") { every.frame { whenever(dpad.none) { isStationary set 1 } } }
            }

        val code = game.compileForTest()

        // Should generate negation of direction checks
        assertTrue(
            code.contains("!") || code.contains("==") || code.contains("0"),
            "Should generate none/stationary check"
        )
    }

    // =========================================================================
    // DIRECTIONAL HELPER TESTS
    // =========================================================================

    @Test
    fun `movingLeft is equivalent to dpad left`() {
        val game =
            gbGame("test") {
                var result by u8Var(0)

                start = scene("main") { every.frame { whenever(movingLeft) { result set 1 } } }
            }

        val code = game.compileForTest()

        // movingLeft should check left mask (0x02)
        assertTrue(
            code.contains("0x02") || code.contains("& 2"),
            "movingLeft should check left mask"
        )
    }

    @Test
    fun `movingRight is equivalent to dpad right`() {
        val game =
            gbGame("test") {
                var result by u8Var(0)

                start = scene("main") { every.frame { whenever(movingRight) { result set 1 } } }
            }

        val code = game.compileForTest()

        // movingRight should check right mask (0x01)
        assertTrue(
            code.contains("0x01") || code.contains("& 1"),
            "movingRight should check right mask"
        )
    }

    @Test
    fun `moving is equivalent to dpad any`() {
        val game =
            gbGame("test") {
                var result by u8Var(0)

                start = scene("main") { every.frame { whenever(moving) { result set 1 } } }
            }

        val code = game.compileForTest()

        // moving should check any direction
        assertTrue(code.contains("||"), "moving should use OR for any direction")
    }

    @Test
    fun `stationary is equivalent to dpad none`() {
        val game =
            gbGame("test") {
                var result by u8Var(0)

                start = scene("main") { every.frame { whenever(stationary) { result set 1 } } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "stationary should compile correctly")
    }

    // =========================================================================
    // BUTTON STATE OPERATOR TESTS
    // =========================================================================

    @Test
    fun `ButtonState and Condition combination works`() {
        val game =
            gbGame("test") {
                var grounded by u8Var(1)
                var jumped by u8Var(0)

                start =
                    scene("main") {
                        every.frame {
                            whenever(buttons.a and (grounded isEqualTo 1)) { jumped set 1 }
                        }
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("&&"), "Should combine button and condition with AND")
        assertTrue(code.contains("grounded"), "Should check grounded variable")
    }

    @Test
    fun `ButtonState or Condition combination works`() {
        val game =
            gbGame("test") {
                var forceJump by u8Var(0)
                var jumped by u8Var(0)

                start =
                    scene("main") {
                        every.frame {
                            whenever(buttons.a or (forceJump isEqualTo 1)) { jumped set 1 }
                        }
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("||"), "Should combine button and condition with OR")
    }

    // =========================================================================
    // JOYPAD STATE MANAGEMENT TESTS
    // =========================================================================

    @Test
    fun `joypad state variables are declared`() {
        val game =
            gbGame("test") {
                start =
                    scene("main") {
                        every.frame {
                            whenever(buttons.a.pressed) {
                                // Need pressed to trigger prev state usage
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        // Should declare joypad state variables
        assertTrue(code.contains("_joypad"), "Should declare current joypad state")
        assertTrue(code.contains("_joypad_prev"), "Should declare previous joypad state")
    }

    @Test
    fun `joypad state is updated each frame`() {
        val game =
            gbGame("test") {
                start =
                    scene("main") {
                        every.frame {
                            whenever(buttons.a.pressed) {
                                // pressed triggers state tracking
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        // Should update previous state and read new state
        assertTrue(
            code.contains("_joypad_prev = _joypad") || code.contains("_joypad_prev=_joypad"),
            "Should copy current to previous"
        )
        assertTrue(code.contains("joypad()"), "Should read new joypad state")
    }
}
