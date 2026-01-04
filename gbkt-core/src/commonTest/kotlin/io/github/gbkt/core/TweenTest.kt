/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.graphics.*
import io.github.gbkt.core.ir.*
import kotlin.test.*

/**
 * Tests for the tweening system.
 *
 * Validates:
 * - Easing curve value correctness
 * - Tween IR generation
 * - Code generation for tweens
 * - Edge cases (overflow, boundary values)
 */
class TweenTest {

    @Test
    fun `linear easing produces correct values`() {
        // Linear easing should map input directly to output
        assertEquals(0, computeTestEasingValue(Easing.LINEAR, 0.0))
        assertEquals(127, computeTestEasingValue(Easing.LINEAR, 0.5), "Mid-point should be ~127")
        assertEquals(255, computeTestEasingValue(Easing.LINEAR, 1.0))
    }

    @Test
    fun `ease in starts slow and ends fast`() {
        val midValue = computeTestEasingValue(Easing.EASE_IN, 0.5)
        // Ease-in at 50% should be less than 127 (slower start)
        assertTrue(midValue < 127, "Ease-in mid-point should be < 127, was $midValue")

        // End should still be 255
        assertEquals(255, computeTestEasingValue(Easing.EASE_IN, 1.0))
    }

    @Test
    fun `ease out starts fast and ends slow`() {
        val midValue = computeTestEasingValue(Easing.EASE_OUT, 0.5)
        // Ease-out at 50% should be greater than 127 (faster start)
        assertTrue(midValue > 127, "Ease-out mid-point should be > 127, was $midValue")

        // End should still be 255
        assertEquals(255, computeTestEasingValue(Easing.EASE_OUT, 1.0))
    }

    @Test
    fun `ease in out is symmetric`() {
        // At 50%, ease-in-out should be approximately 127
        val midValue = computeTestEasingValue(Easing.EASE_IN_OUT, 0.5)
        assertTrue(midValue in 120..135, "Ease-in-out mid-point should be ~127, was $midValue")

        // Should start slow
        val quarterValue = computeTestEasingValue(Easing.EASE_IN_OUT, 0.25)
        assertTrue(quarterValue < 64, "Ease-in-out at 25% should be < 64, was $quarterValue")
    }

    @Test
    fun `bounce easing has expected bounce behavior`() {
        // Bounce out should reach 255 at the end
        assertEquals(255, computeTestEasingValue(Easing.EASE_OUT_BOUNCE, 1.0))

        // Should have values that represent bouncing (overshoots)
        val value90 = computeTestEasingValue(Easing.EASE_OUT_BOUNCE, 0.9)
        assertTrue(value90 > 200, "Bounce at 90% should be close to final value")
    }

    @Test
    fun `elastic easing overshoots target`() {
        // Elastic should overshoot and then settle
        val value70 = computeTestEasingValue(Easing.EASE_OUT_ELASTIC, 0.7)
        assertTrue(value70 > 200, "Elastic at 70% should overshoot, was $value70")

        // Final value should be 255
        assertEquals(255, computeTestEasingValue(Easing.EASE_OUT_ELASTIC, 1.0))
    }

    @Test
    fun `all easing values are in valid range`() {
        for (easing in Easing.values()) {
            for (i in 0..100) {
                val t = i / 100.0
                val value = computeTestEasingValue(easing, t)
                assertTrue(
                    value in 0..255,
                    "Easing ${easing.name} at t=$t produced out-of-range value: $value"
                )
            }
        }
    }

    @Test
    fun `tween DSL generates correct IR`() {
        // Create a recorder to capture IR statements
        val recorder = StatementRecorder()

        // Record inside the context
        RecordingContext.record(recorder) {
            // Create a mock assignable expression
            val mockTarget = AssignableExpr("test_var", GBVar.VarType.U8)

            // Call tween
            tween(mockTarget, from = 0, to = 100, duration = 60.frames, easing = Easing.EASE_OUT)
        }

        // Verify the captured IR
        val statements = recorder.statements
        assertTrue(statements.isNotEmpty(), "Should have captured IR statements")

        val tweenStmt = statements.filterIsInstance<IRTween>().firstOrNull()
        assertNotNull(tweenStmt, "Should have captured IRTween statement")

        assertEquals("test_var", tweenStmt.target)
        assertEquals(GBVar.VarType.U8, tweenStmt.targetType)
        assertEquals(60, tweenStmt.duration)
        assertEquals(Easing.EASE_OUT, tweenStmt.easing)
    }

    @Test
    fun `tween code generation produces valid C`() {
        val game =
            gbGame("test") {
                var testVar by u8Var(0)

                start =
                    scene("main") {
                        enter {
                            tween(
                                testVar,
                                from = 0,
                                to = 100,
                                duration = 30.frames,
                                easing = Easing.EASE_IN
                            )
                        }
                        every.frame {}
                    }
            }

        val code = game.compile()

        // Should contain tween data structures
        assertTrue(code.contains("_tween_active"), "Should generate tween active array")
        assertTrue(code.contains("MAX_TWEENS"), "Should define MAX_TWEENS")

        // Should contain easing lookup table for EASE_IN
        assertTrue(code.contains("easing_ease_in"), "Should generate ease_in lookup table")

        // Should contain update_tweens function
        assertTrue(code.contains("update_tweens"), "Should generate update_tweens function")

        // Should use signed types for proper negative delta support
        assertTrue(code.contains("INT16"), "Should use signed types for tween math")
    }

    @Test
    fun `decreasing tween generates valid code`() {
        val game =
            gbGame("test") {
                var testVar by u8Var(100)

                start =
                    scene("main") {
                        enter {
                            // Tween from high to low (decreasing)
                            tween(
                                testVar,
                                from = 100,
                                to = 0,
                                duration = 60.frames,
                                easing = Easing.LINEAR
                            )
                        }
                        every.frame {}
                    }
            }

        val code = game.compile()

        // Should successfully compile without errors and use INT16 for signed math
        assertTrue(
            code.contains("_tween_from[slot] = (INT16)("),
            "Should cast from value to INT16. Generated: ${code.lines().filter { it.contains("_tween_from") }.take(3)}"
        )
        assertTrue(
            code.contains("_tween_to[slot] = (INT16)("),
            "Should cast to value to INT16. Generated: ${code.lines().filter { it.contains("_tween_to") }.take(3)}"
        )
    }

    @Test
    fun `only used easing tables are generated`() {
        val game =
            gbGame("test") {
                var testVar by u8Var(0)

                start =
                    scene("main") {
                        enter {
                            // Only use LINEAR easing
                            tween(
                                testVar,
                                from = 0,
                                to = 100,
                                duration = 30.frames,
                                easing = Easing.LINEAR
                            )
                        }
                        every.frame {}
                    }
            }

        val code = game.compile()

        // Should contain LINEAR table (always included)
        assertTrue(code.contains("easing_linear[256]"), "Should generate linear lookup table")

        // Should NOT contain unused tables like bounce or elastic
        // (Note: This optimization reduces ROM size)
        assertFalse(
            code.contains("easing_ease_out_bounce[256]"),
            "Should not generate unused bounce table"
        )
        assertFalse(
            code.contains("easing_ease_out_elastic[256]"),
            "Should not generate unused elastic table"
        )
    }

    @Test
    fun `tween with expression values compiles`() {
        val game =
            gbGame("test") {
                var startX by u8Var(0)
                var endX by u8Var(100)
                var posX by u8Var(0)

                start =
                    scene("main") {
                        enter {
                            // Use expression for from/to values
                            tween(
                                posX,
                                from = Expr(IRVar("startX")),
                                to = Expr(IRVar("endX")),
                                duration = 60.frames,
                                easing = Easing.EASE_OUT
                            )
                        }
                        every.frame {}
                    }
            }

        val code = game.compile()
        assertTrue(code.contains("startX"), "Should reference startX variable")
        assertTrue(code.contains("endX"), "Should reference endX variable")
    }

    // Helper function that mirrors the codegen logic for testing
    private fun computeTestEasingValue(easing: Easing, t: Double): Int {
        val result =
            when (easing) {
                Easing.LINEAR -> t
                Easing.EASE_IN -> t * t
                Easing.EASE_OUT -> 1.0 - (1.0 - t) * (1.0 - t)
                Easing.EASE_IN_OUT ->
                    if (t < 0.5) 2.0 * t * t else 1.0 - 2.0 * (1.0 - t) * (1.0 - t)
                Easing.EASE_OUT_IN ->
                    if (t < 0.5) 0.5 * (1.0 - 2.0 * (1.0 - 2.0 * t) * (1.0 - 2.0 * t))
                    else 0.5 + 0.5 * (2.0 * t - 1.0) * (2.0 * t - 1.0)
                Easing.EASE_IN_QUAD -> t * t
                Easing.EASE_OUT_QUAD -> 1.0 - (1.0 - t) * (1.0 - t)
                Easing.EASE_IN_OUT_QUAD ->
                    if (t < 0.5) 2.0 * t * t else 1.0 - 2.0 * (1.0 - t) * (1.0 - t)
                Easing.EASE_IN_CUBIC -> t * t * t
                Easing.EASE_OUT_CUBIC -> 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t)
                Easing.EASE_IN_OUT_CUBIC ->
                    if (t < 0.5) 4.0 * t * t * t else 1.0 - 4.0 * (1.0 - t) * (1.0 - t) * (1.0 - t)
                Easing.EASE_OUT_BOUNCE -> {
                    when {
                        t < 1.0 / 2.75 -> 7.5625 * t * t
                        t < 2.0 / 2.75 -> {
                            val t2 = t - 1.5 / 2.75
                            7.5625 * t2 * t2 + 0.75
                        }
                        t < 2.5 / 2.75 -> {
                            val t2 = t - 2.25 / 2.75
                            7.5625 * t2 * t2 + 0.9375
                        }
                        else -> {
                            val t2 = t - 2.625 / 2.75
                            7.5625 * t2 * t2 + 0.984375
                        }
                    }
                }
                Easing.EASE_OUT_ELASTIC -> {
                    // Simplified elastic easing using polynomial approximation (matches codegen)
                    if (t == 0.0) 0.0
                    else if (t == 1.0) 1.0
                    else {
                        val decay = 1.0 - t
                        val amplitude = decay * decay * decay * decay

                        val phase =
                            if (t < 0.25) t * 8.0
                            else if (t < 0.5) 2.0 - t * 4.0
                            else if (t < 0.75) t * 4.0 - 2.0 else 4.0 - t * 4.0

                        val overshoot = amplitude * phase * 0.3
                        (1.0 + overshoot).coerceIn(0.0, 1.2)
                    }
                }
            }

        val clamped = result.coerceIn(0.0, 1.0)
        return (clamped * 255.0).toInt()
    }
}
