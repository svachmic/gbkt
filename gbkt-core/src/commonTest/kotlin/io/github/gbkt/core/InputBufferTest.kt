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

/** Tests for input buffering system - frame-perfect input timing for platformers. */
class InputBufferTest {

    @Test
    fun `inputBuffer declaration generates correct variable`() {
        val game =
            gbGame("test") {
                val jumpBuffer = inputBuffer(buttons.a, 6)

                start =
                    scene("main") {
                        every.frame {
                            whenever(jumpBuffer.consumed) {
                                // Jump logic would go here
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        // Should declare the buffer variable
        assertTrue(code.contains("static UINT8 buffer_0;"), "Should declare buffer variable")
        assertTrue(code.contains("Window: 6 frames"), "Should have window size comment")
    }

    @Test
    fun `inputBuffer generates decrement logic in main loop`() {
        val game =
            gbGame("test") {
                val jumpBuffer = inputBuffer(buttons.a, 6)

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should generate decrement logic
        assertTrue(
            code.contains("if (buffer_0 > 0) buffer_0--;"),
            "Should decrement buffer each frame",
        )
    }

    @Test
    fun `inputBuffer generates button press detection`() {
        val game =
            gbGame("test") {
                val jumpBuffer = inputBuffer(buttons.a, 6)

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should detect button press and fill buffer
        // Button A has mask 0x10
        assertTrue(
            code.contains("_joypad & 0x10") && code.contains("buffer_0 = 6"),
            "Should detect A button press and fill buffer to 6",
        )
    }

    @Test
    fun `inputBuffer consumed generates atomic check-and-reset`() {
        val game =
            gbGame("test") {
                var grounded by u8Var(1)
                val jumpBuffer = inputBuffer(buttons.a, 6)

                start =
                    scene("main") {
                        every.frame { whenever(jumpBuffer.consumed) { grounded set 0 } }
                    }
            }

        val code = game.compileForTest()

        // The consumed check should use the comma operator for atomic check-and-reset
        assertTrue(
            code.contains("buffer_0 > 0u && (buffer_0 = 0u, 1u)"),
            "Consumed should generate atomic check-and-reset expression",
        )
    }

    @Test
    fun `inputBuffer with different buttons uses correct masks`() {
        val game =
            gbGame("test") {
                val jumpBuffer = inputBuffer(buttons.a, 6) // A = 0x10
                val attackBuffer = inputBuffer(buttons.b, 4) // B = 0x20

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        // Should have both buffers with correct masks
        assertTrue(code.contains("_joypad & 0x10"), "Should detect A button (0x10)")
        assertTrue(code.contains("_joypad & 0x20"), "Should detect B button (0x20)")
        assertTrue(code.contains("buffer_0 = 6"), "Jump buffer should fill to 6")
        assertTrue(code.contains("buffer_1 = 4"), "Attack buffer should fill to 4")
    }

    @Test
    fun `inputBuffer named version uses custom name`() {
        val game =
            gbGame("test") {
                val jumpBuffer = inputBuffer("jump", buttons.a, 6)

                start = scene("main") { every.frame {} }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("buffer_jump"), "Should use custom name 'buffer_jump'")
    }

    @Test
    fun `inputBuffer active check does not consume`() {
        val game =
            gbGame("test") {
                var indicator by u8Var(0)
                val jumpBuffer = inputBuffer(buttons.a, 6)

                start =
                    scene("main") {
                        every.frame { whenever(jumpBuffer.active) { indicator set 1 } }
                    }
            }

        val code = game.compileForTest()

        // Active check should only check > 0, not reset
        assertTrue(code.contains("(buffer_0 > 0u)"), "Active check should not include reset logic")
        assertFalse(
            code.contains("buffer_0 > 0u && (buffer_0 = 0u"),
            "Active check should NOT have comma operator reset",
        )
    }

    @Test
    fun `inputBuffer reset generates correct code`() {
        val game =
            gbGame("test") {
                val jumpBuffer = inputBuffer(buttons.a, 6)

                start = scene("main") { enter { jumpBuffer.reset() } }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("buffer_0 = 0;"), "Reset should set buffer to 0")
    }

    @Test
    fun `inputBuffer fill generates correct code`() {
        val game =
            gbGame("test") {
                val jumpBuffer = inputBuffer(buttons.a, 6)

                start = scene("main") { enter { jumpBuffer.fill() } }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("buffer_0 = 6;"), "Fill should set buffer to max window size")
    }

    @Test
    fun `inputBuffer validates frame window range`() {
        // Should reject 0 frames
        val exception1 = assertFails {
            gbGame("test") {
                inputBuffer(buttons.a, 0)
                start = scene("main") {}
            }
        }
        assertTrue(exception1.message?.contains("1-255") == true, "Should reject 0 frames")

        // Should reject > 255 frames
        val exception2 = assertFails {
            gbGame("test") {
                inputBuffer(buttons.a, 256)
                start = scene("main") {}
            }
        }
        assertTrue(exception2.message?.contains("1-255") == true, "Should reject > 255 frames")
    }

    @Test
    fun `inputBuffer combined with grounded condition`() {
        val game =
            gbGame("test") {
                var grounded by u8Var(1)
                var velocityY by u8Var(0)
                val jumpBuffer = inputBuffer(buttons.a, 6)

                start =
                    scene("main") {
                        every.frame {
                            // Classic platformer pattern: jump if buffered AND grounded
                            whenever(jumpBuffer.consumed and (grounded isEqualTo 1)) {
                                velocityY set 10
                                grounded set 0
                            }
                        }
                    }
            }

        val code = game.compileForTest()

        // Should have the combined condition
        assertTrue(
            code.contains("buffer_0 > 0u && (buffer_0 = 0u, 1u)"),
            "Should have consumed check",
        )
        assertTrue(code.contains("grounded"), "Should have grounded check")
    }

    @Test
    fun `multiple input buffers are independent`() {
        val game =
            gbGame("test") {
                val jumpBuffer = inputBuffer(buttons.a, 6)
                val attackBuffer = inputBuffer(buttons.b, 4)
                val dashBuffer = inputBuffer(buttons.select, 8)

                start =
                    scene("main") {
                        every.frame {
                            whenever(jumpBuffer.consumed) {}
                            whenever(attackBuffer.consumed) {}
                            whenever(dashBuffer.consumed) {}
                        }
                    }
            }

        val code = game.compileForTest()

        // All three buffers should exist
        assertTrue(code.contains("buffer_0"), "Should have jump buffer")
        assertTrue(code.contains("buffer_1"), "Should have attack buffer")
        assertTrue(code.contains("buffer_2"), "Should have dash buffer")

        // Each should have its own decrement
        assertTrue(code.contains("if (buffer_0 > 0) buffer_0--;"), "Jump buffer decrement")
        assertTrue(code.contains("if (buffer_1 > 0) buffer_1--;"), "Attack buffer decrement")
        assertTrue(code.contains("if (buffer_2 > 0) buffer_2--;"), "Dash buffer decrement")
    }

    @Test
    fun `inputBuffer with FrameTiming syntax works`() {
        val game =
            gbGame("test") {
                // Using .frames extension for natural readability
                val jumpBuffer = inputBuffer(buttons.a, 6.frames)

                start = scene("main") { every.frame { whenever(jumpBuffer.consumed) {} } }
            }

        val code = game.compileForTest()

        // Should work exactly the same as integer syntax
        assertTrue(code.contains("static UINT8 buffer_0;"), "Should declare buffer variable")
        assertTrue(code.contains("buffer_0 = 6"), "Should fill buffer to 6 frames")
    }
}
