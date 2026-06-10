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
 * DEF-10.1-13-B / D-Seed005 visual-partial — `bgFillCheckerboard()` MUST emit a byte literal whose
 * per-tile rendering produces SQUARE 4x4-pixel checker cells, not 4-wide-by-2-tall rectangles.
 *
 * **Diagnostic, not unit-shape lock.** This test is paired with the Plan 10.1-17 diagnostic finding
 * at
 * `.planning/phases/10.1-metasprites-surplus-codegen-defects-inserted/evidence/d-v2-visual-diagnostic/d-v2-visual-finding.md`.
 * It asserts the STRUCTURAL invariant that justifies the correct literal: the first 8 bytes of the
 * tile (= the top 4 pixel rows, since each tile row is 2 bytes) are all `0xF0`, and the last 8
 * bytes (= the bottom 4 pixel rows) are all `0x0F`.
 *
 * **State of this test on `HEAD` at Plan 10.1-17 commit time: RED (failing).** Plan 10.1-02 shipped
 * a literal that interleaves 4-byte F0/0F groups instead of grouping them as 8/8 halves — see
 * `gb-tile-plane-encoding-notes.md` for the pixel-by-pixel decode that proves the current literal
 * renders as 4-pixel-wide × 2-pixel-tall rectangles (matching the user-verified screenshot at
 * `.planning/phases/10-port-metasprites-gbdk-example-to-gbkt/evidence/uat-screenshots/behavior1-animation-advance.png`).
 *
 * **State of this test on `HEAD` after Plan 10.1-18 lands: GREEN (passing).** Plan 18's 1-edit fix
 * swaps the literal to 8× `0xF0` followed by 8× `0x0F`, which satisfies the structural invariant
 * asserted here.
 *
 * Paired with `Seed005CheckerboardBytePatternTest`. After Plan 18, that test will be updated (per
 * Plan 18's PLAN.md) to lock the new substring; this diagnostic test remains as a permanent
 * semantic regression guard ("checker cells are square").
 */
class BgAspectDiagnosticTest {

    private fun emittedCheckerboardCode(): String {
        val ops = ScriptBuilder.buildOps { bgFillCheckerboard() }
        val rawOp =
            ops.filterIsInstance<RawOp>().firstOrNull()
                ?: error("Expected RawOp from bgFillCheckerboard(), got ops=$ops")
        return rawOp.code
    }

    @Test
    fun checker_literal_has_8_consecutive_F0_bytes_then_8_consecutive_0F_bytes() {
        // True 4x4 square checker requires the alternation period in TILE ROWS to be 4,
        // not 2. Since each tile row consumes 2 bytes (plane 0 + plane 1), a 4-row period
        // means 8 consecutive identical bytes per "half". The correct literal is therefore
        // 8× 0xF0 followed by 8× 0x0F (top 4 pixel rows: left half lit; bottom 4 pixel
        // rows: right half lit ⇒ 4×4 square cells under uniform tiling).
        val code = emittedCheckerboardCode()
        val correctSquareCheckerLiteral =
            "0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,0xF0," + "0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0F"
        assertTrue(
            code.replace("\n", "").replace(" ", "").contains(correctSquareCheckerLiteral),
            "Expected emitted code to contain the 4x4-square-checker byte literal " +
                "'$correctSquareCheckerLiteral' (8× 0xF0 then 8× 0x0F = top half left-4-lit, " +
                "bottom half right-4-lit ⇒ true 4×4 square cells when tiled). Plan 10.1-02's " +
                "literal interleaves 4-byte F0/0F groups, producing 4w×2h RECTANGLES. See " +
                ".planning/phases/10.1-metasprites-surplus-codegen-defects-inserted/" +
                "evidence/d-v2-visual-diagnostic/d-v2-visual-finding.md for the named root " +
                "cause and the Plan 10.1-18 fix shape. Code was:\n$code",
        )
    }

    @Test
    fun checker_literal_must_not_contain_2_row_period_substring() {
        // Regression guard for the SPECIFIC Plan 10.1-02 wrong shape. The 2-row-period
        // substring "0xF0,0xF0,0xF0,0xF0,0x0F,0x0F,0x0F,0x0F" appears twice in the buggy
        // literal — that exact 8-byte pattern is what produces 4w×2h rectangles. The
        // corrected literal does NOT contain this substring (it groups F0 and 0F into
        // 8-byte halves instead).
        val code = emittedCheckerboardCode()
        val wrongPeriodSubstring = "0xF0,0xF0,0xF0,0xF0,0x0F,0x0F,0x0F,0x0F"
        assertFalse(
            code.contains(wrongPeriodSubstring),
            "Emitted code MUST NOT contain the 2-row-period substring " +
                "'$wrongPeriodSubstring' — this is the Plan 10.1-02 shape that renders as " +
                "4-wide × 2-tall RECTANGLES (DEF-10.1-13-B). Code was:\n$code",
        )
    }

    @Test
    fun checker_literal_F0_and_0F_counts_match_4x4_checker() {
        // Structural counts: a 16-byte tile literal must contain exactly 8 occurrences of
        // 0xF0 and exactly 8 occurrences of 0x0F — that count is shared between the wrong
        // and correct literals, so this test alone is not sufficient, but combined with the
        // structural ordering test above it locks the full shape. Kept as a sanity check
        // against any future "let's add more bytes" regression.
        val code = emittedCheckerboardCode()
        val f0Count = countOccurrences(code, "0xF0")
        val zeroFCount = countOccurrences(code, "0x0F")
        assertEquals(8, f0Count, "Expected exactly 8 occurrences of 0xF0, got $f0Count")
        assertEquals(8, zeroFCount, "Expected exactly 8 occurrences of 0x0F, got $zeroFCount")
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
