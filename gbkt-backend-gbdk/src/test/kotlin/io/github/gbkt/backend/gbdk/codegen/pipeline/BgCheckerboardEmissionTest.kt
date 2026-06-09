/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.dsl.bgFillCheckerboard
import io.github.gbkt.core.dsl.game
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// BG CHECKERBOARD EMISSION TESTS (Plan 10-11; pattern revised by Plan 10.1-02)
//
// Verifies D-10 BG fill init:
//   1. DSL `bgFillCheckerboard()` in scene.enter { } emits `fill_bkg_rect(..., 0)` in play_enter
//   2. DSL `bgFillCheckerboard()` emits `set_bkg_data(0, 1, ...)` in play_enter
//   3. A 16-byte TRUE 4x4 checker pattern constant is emitted at file scope
//   4. No `printf(` calls in the BG-fill path (Pitfall 5 guard — debugGraphics=false)
//
// D-Seed005 history: the reference metasprites.c line 43 ships a diagonal-stripe
// literal (0x80,0x80,0x40,0x40,...,0x01,0x01) under the "checkerboard" label, which when
// tiled renders parallel diagonal stripes — NOT a checkerboard. Plan 10.1-02 corrected the
// gbkt emission to a TRUE 4x4 checker (0xF0,...,0x0F,...) per D-12b "name should match what
// it emits", and updated this test alongside.
//
// Reference metasprites.c lines 177-180 (call shape, NOT the literal):
//   fill_bkg_rect(0, 0, DEVICE_SCREEN_WIDTH, DEVICE_SCREEN_HEIGHT, 0);
//   set_bkg_data(0, 1, pattern);
//
// Uses brace-walk extraction on `play_enter` body (CLAUDE.md §"Scope-level grep gates").
// =============================================================================

// ---------------------------------------------------------------------------
// Brace-walk helper: extract the body of the first C function matching signature
// ---------------------------------------------------------------------------

private fun extractFunctionBodyBgTest(source: String, signature: String): String? {
    val sigIdx = source.indexOf(signature)
    if (sigIdx == -1) return null
    val openIdx = source.indexOf('{', sigIdx + signature.length)
    if (openIdx == -1) return null
    var depth = 0
    var i = openIdx
    while (i < source.length) {
        when (source[i]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return source.substring(openIdx + 1, i)
            }
        }
        i++
    }
    return null
}

class BgCheckerboardEmissionTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // TEST 1: DSL bgFillCheckerboard() in scene.enter { } emits fill_bkg_rect(...)
    // in play_enter (brace-walk scoped to play_enter body only).
    //
    // Reference (metasprites.c line 177):
    //   fill_bkg_rect(0, 0, DEVICE_SCREEN_WIDTH, DEVICE_SCREEN_HEIGHT, 0);
    // =========================================================================
    @Test
    fun `bgFillCheckerboard emits fill_bkg_rect in play_enter`() {
        val gameIR =
            game("BgCheckerboardTest") {
                    val playScene = scene("play") { enter { bgFillCheckerboard() } }
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val bankC = result.files["bank1.c"] ?: error("bank1.c not generated")

        val playEnterBody =
            extractFunctionBodyBgTest(bankC, "play_enter")
                ?: error("Could not extract play_enter body from bank1.c — brace-walk failed")

        assertTrue(
            playEnterBody.contains(
                "fill_bkg_rect(0, 0, DEVICE_SCREEN_WIDTH, DEVICE_SCREEN_HEIGHT, 0)"
            ),
            "Expected 'fill_bkg_rect(0, 0, DEVICE_SCREEN_WIDTH, DEVICE_SCREEN_HEIGHT, 0)' in play_enter body.\nplay_enter body:\n$playEnterBody",
        )
    }

    // =========================================================================
    // TEST 2: DSL bgFillCheckerboard() in scene.enter { } emits set_bkg_data(0, 1, ...)
    // in play_enter (brace-walk scoped to play_enter body only).
    //
    // Reference (metasprites.c line 180):
    //   set_bkg_data(0, 1, pattern);
    // =========================================================================
    @Test
    fun `bgFillCheckerboard emits set_bkg_data in play_enter`() {
        val gameIR =
            game("BgCheckerboardTest") {
                    val playScene = scene("play") { enter { bgFillCheckerboard() } }
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val bankC = result.files["bank1.c"] ?: error("bank1.c not generated")

        val playEnterBody =
            extractFunctionBodyBgTest(bankC, "play_enter")
                ?: error("Could not extract play_enter body from bank1.c — brace-walk failed")

        assertTrue(
            playEnterBody.contains("set_bkg_data(0, 1,"),
            "Expected 'set_bkg_data(0, 1, ...)' in play_enter body.\nplay_enter body:\n$playEnterBody",
        )
    }

    // =========================================================================
    // TEST 3: A 16-byte TRUE 4x4 SQUARE checker pattern constant is emitted at file scope.
    //
    // Plan 10.1-02 (D-Seed005) replaced the diagonal-stripe literal with a checker
    // literal. Plan 10.1-18 (DEF-10.1-13-B) then corrected the *row grouping*: each tile
    // row consumes 2 bytes, so a 4×4-square checker needs the alternation period in tile
    // rows to be 4 (= 8 consecutive identical bytes per half), NOT 2. The Plan 10.1-02
    // shape "0xF0×4,0x0F×4 repeated" produced 4w×2h rectangles; the corrected shape is
    // 8× 0xF0 then 8× 0x0F.
    //
    // Current emission (MetaspriteBuilder.bgFillCheckerboard, Plan 10.1-18):
    //   static const UINT8 _checkerboard_bg_pattern[] = {
    //       0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,
    //       0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0F};
    //
    // See .planning/phases/10.1-metasprites-surplus-codegen-defects-inserted/
    //     evidence/d-v2-visual-diagnostic/d-v2-visual-finding.md.
    // =========================================================================
    @Test
    fun `bgFillCheckerboard emits checkerboard pattern constant at file scope`() {
        val gameIR =
            game("BgCheckerboardTest") {
                    val playScene = scene("play") { enter { bgFillCheckerboard() } }
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val bankC = result.files["bank1.c"] ?: error("bank1.c not generated")

        // True 4x4 SQUARE checker pattern: top half is 8 consecutive 0xF0 bytes (4 tile
        // rows × 2 bytes/row, left half lit), bottom half is 8 consecutive 0x0F bytes
        // (4 rows × 2 bytes/row, right half lit). Locking BOTH halves catches any
        // regrouping regression (e.g., reversion to Plan 10.1-02's interleaved shape).
        assertTrue(
            bankC.contains("0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,0xF0"),
            "Expected top half '0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,0xF0,0xF0' (4 tile rows of " +
                "left-half-lit bytes) in bank1.c — Plan 10.1-18 corrected literal " +
                "(DEF-10.1-13-B fix). bank1.c was:\n$bankC",
        )
        assertTrue(
            bankC.contains("0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0F"),
            "Expected bottom half '0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0F,0x0F' (4 tile rows of " +
                "right-half-lit bytes) in bank1.c — Plan 10.1-18 corrected literal " +
                "(DEF-10.1-13-B fix). bank1.c was:\n$bankC",
        )

        // Diagonal-stripe regression guard: the pre-D-Seed005 literal MUST NOT reappear.
        assertFalse(
            bankC.contains("0x80,0x80,0x40,0x40"),
            "Diagonal-stripe pattern '0x80,0x80,0x40,0x40,...' has reappeared in bank1.c — " +
                "D-Seed005 regression. MetaspriteBuilder.bgFillCheckerboard must emit the 4x4 " +
                "checker literal, not the reference's mislabeled diagonal-stripe literal.",
        )

        // DEF-10.1-13-B regression guard: the Plan 10.1-02 interleaved 4-byte F0/0F
        // substring "0xF0,0xF0,0xF0,0xF0,0x0F,0x0F,0x0F,0x0F" renders 4w×2h rectangles
        // (not 4×4 squares). Plan 10.1-18 swapped to the 8/8 half-grouping; this exact
        // 2-row-period substring MUST NOT reappear.
        assertFalse(
            bankC.contains("0xF0,0xF0,0xF0,0xF0,0x0F,0x0F,0x0F,0x0F"),
            "Plan 10.1-02 2-row-period substring '0xF0,0xF0,0xF0,0xF0,0x0F,0x0F,0x0F,0x0F' " +
                "has reappeared in bank1.c — DEF-10.1-13-B regression. This shape renders " +
                "4-wide × 2-tall RECTANGLES instead of 4×4 squares. bank1.c was:\n$bankC",
        )
    }

    // =========================================================================
    // TEST 4: No printf( calls in play_enter body (Pitfall 5 — debugGraphics guard).
    //
    // debugGraphics = false by default. If it were true, GBDK printf() would write
    // to the background tile layer, corrupting the checkerboard pattern.
    // =========================================================================
    @Test
    fun `bgFillCheckerboard does not emit printf in play_enter`() {
        val gameIR =
            game("BgCheckerboardTest") {
                    val playScene = scene("play") { enter { bgFillCheckerboard() } }
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val bankC = result.files["bank1.c"] ?: error("bank1.c not generated")

        val playEnterBody =
            extractFunctionBodyBgTest(bankC, "play_enter")
                ?: error("Could not extract play_enter body from bank1.c — brace-walk failed")

        assertFalse(
            playEnterBody.contains("printf("),
            "Unexpected 'printf(' found in play_enter body — Pitfall 5 violation. " +
                "debugGraphics must default to false to avoid BG tile layer corruption.\n" +
                "play_enter body:\n$playEnterBody",
        )
    }
}
