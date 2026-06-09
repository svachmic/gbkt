/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.RawOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SEED-005 / D-Seed005 — `bgFillCheckerboard()` MUST emit a true 4x4 checker byte literal, not the
 * historical diagonal-stripe pattern (`0x80,0x80,0x40,0x40,…`) that masquerades as a checkerboard.
 * See `.planning/seeds/SEED-005-metasprites-diagonal-bg-not-checkerboard.md`.
 *
 * Asserts on the raw C code captured in the `RawOp` emitted by `bgFillCheckerboard()`. Mirrors the
 * `assertTrue(text.contains(…))` style used by
 * `gbkt-backend-gbdk/.../visitor/MetaspriteVisitorFrameSwitchTest.kt` (per Plan 10.1-02 PATTERNS
 * line 27).
 */
class Seed005CheckerboardBytePatternTest {

    private fun emittedCheckerboardCode(): String {
        val ops = ScriptBuilder.buildOps { bgFillCheckerboard() }
        val rawOp =
            ops.filterIsInstance<RawOp>().firstOrNull()
                ?: error("Expected RawOp from bgFillCheckerboard(), got ops=$ops")
        return rawOp.code
    }

    @Test
    fun pattern_contains_8_consecutive_0xF0_then_8_consecutive_0x0F() {
        val code = emittedCheckerboardCode()

        // True 4x4 SQUARE checker: alternation period in tile rows must be 4 (not 2).
        // Each tile row = 2 bytes (plane 0 + plane 1), so 4 rows × 2 bytes/row = 8 bytes
        // per half. Correct literal: 8× 0xF0 (top 4 rows: left half lit) then 8× 0x0F
        // (bottom 4 rows: right half lit) ⇒ uniform tiling renders 4×4-pixel squares.
        // Plan 10.1-18 fixed DEF-10.1-13-B by swapping the Plan 10.1-02 interleaved
        // 4-byte F0/0F shape (which renders 4w×2h rectangles) to this 8/8 half-grouping.
        // See .planning/phases/10.1-metasprites-surplus-codegen-defects-inserted/
        //     evidence/d-v2-visual-diagnostic/d-v2-visual-finding.md.
        val correctSquareCheckerLiteral =
            "0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,0xF0," + "0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0F"
        val normalized = code.replace("\n", "").replace(" ", "")
        val occurrences = countOccurrences(normalized, correctSquareCheckerLiteral)
        assertEquals(
            1,
            occurrences,
            "Expected exactly 1 occurrence of '$correctSquareCheckerLiteral' in emitted " +
                "code (normalized), got $occurrences. Code was:\n$code",
        )
        assertTrue(
            normalized.contains(correctSquareCheckerLiteral),
            "Expected emitted code (normalized) to contain 4x4-square-checker literal " +
                "'$correctSquareCheckerLiteral'. Code was:\n$code",
        )
    }

    @Test
    fun pattern_does_not_contain_diagonal_byte_sequence() {
        val code = emittedCheckerboardCode()

        // Diagonal-stripe regression guard: the buggy pre-D-Seed005 literal began with
        // 0x80,0x80,0x40,0x40,…. This MUST NOT appear after the D-Seed005 fix.
        val diagonalSequence = "0x80,0x80,0x40,0x40"
        assertFalse(
            code.contains(diagonalSequence),
            "Expected emitted code NOT to contain diagonal-stripe sequence '$diagonalSequence' " +
                "(D-Seed005 regression). Code was:\n$code",
        )
    }

    @Test
    fun pattern_does_not_contain_2_row_period_substring() {
        val code = emittedCheckerboardCode()

        // DEF-10.1-13-B regression guard: the Plan 10.1-02 interleaved 4-byte F0/0F shape
        // "0xF0,0xF0,0xF0,0xF0,0x0F,0x0F,0x0F,0x0F" renders 4-wide × 2-tall RECTANGLES
        // (not 4×4 squares). Plan 10.1-18 swapped to the 8/8 half-grouping; this exact
        // 2-row-period substring MUST NOT reappear. See Plan 10.1-17 d-v2-visual-finding.md.
        val wrongPeriodSubstring = "0xF0,0xF0,0xF0,0xF0,0x0F,0x0F,0x0F,0x0F"
        assertFalse(
            code.contains(wrongPeriodSubstring),
            "Emitted code MUST NOT contain the 2-row-period substring " +
                "'$wrongPeriodSubstring' — this is the Plan 10.1-02 shape that renders as " +
                "4w × 2h RECTANGLES (DEF-10.1-13-B). Code was:\n$code",
        )
    }

    private fun countOccurrences(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var idx = 0
        var count = 0
        while (true) {
            val found = haystack.indexOf(needle, idx)
            if (found < 0) break
            count++
            idx = found + needle.length
        }
        return count
    }
}
