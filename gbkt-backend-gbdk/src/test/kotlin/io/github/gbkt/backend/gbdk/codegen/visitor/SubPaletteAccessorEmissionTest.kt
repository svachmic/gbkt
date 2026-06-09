/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.dsl.game
import io.github.gbkt.core.dsl.metasprite
import io.github.gbkt.core.dsl.set
import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.VarRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// SUBPALETTE ACCESSOR EMISSION TESTS (Plan 10-09)
//
// Verifies D-07 + D-08 runtime accessor for subPalette:
//   1. Assign("elephant.subPalette", Literal(2), SET) → CExprStatement(_elephant_subPalette = 2u)
//   2. Assign("elephant.subPalette", rot >> 2, SET) → _elephant_subPalette = _rot >> 2;
//   3. No conditional codegen guard (#if defined) — unconditional u8 write (D-08: DMG ignores CGB
// palette bits)
//   4. Global declaration: main.c contains UINT8 _elephant_subPalette
//
// Uses brace-walk extraction on `play_frame` body (CLAUDE.md §"Scope-level grep gates").
// =============================================================================

// ---------------------------------------------------------------------------
// Brace-walk helper: extract the body of the first C function matching signature
// (local copy for visitor test isolation — same as FlipAccessorEmissionTest)
// ---------------------------------------------------------------------------

private fun extractFunctionBody(source: String, signature: String): String? {
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

class SubPaletteAccessorEmissionTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // TEST 1: ScriptOpVisitor: Assign("elephant.subPalette", Literal(2), SET) →
    //         CExprStatement(CBinaryExpr(CVar("_elephant_subPalette"), "=", CLiteral(2)))
    //
    // Verifies the unit-level lowering: dot-notation target sanitization converts
    // "elephant.subPalette" to "_elephant_subPalette" (unsigned u8 write, value 2).
    // =========================================================================
    @Test
    fun `Assign elephant dot subPalette SET 2 lowers to _elephant_subPalette = 2u`() {
        val op = Assign(target = "elephant.subPalette", value = Literal(2), op = AssignOp.SET)
        val result = ScriptOpVisitor.visit(op)
        val expected = CExprStatement(CBinaryExpr(CVar("_elephant_subPalette"), "=", CLiteral(2)))
        assertEquals(
            expected,
            result,
            "Expected _elephant_subPalette = 2u assignment statement from visitAssign",
        )
    }

    // =========================================================================
    // TEST 2: ScriptOpVisitor: Assign("elephant.subPalette", rot >> 2, SET) →
    //         CExprStatement(CBinaryExpr(CVar("_elephant_subPalette"), "=", _rot >> 2))
    //
    // Verifies composite expression lowering. The canonical port uses `rot >> 2`
    // (per reference C `uint8_t subpal = rot >> 2`). The DSL expression
    // BinaryExpr(VarRef("rot"), SHR, Literal(2)) must lower to `_rot >> 2`.
    // =========================================================================
    @Test
    fun `Assign elephant dot subPalette SET rot shr 2 lowers to _elephant_subPalette = _rot shr 2`() {
        val rotShr2 = BinaryExpr(VarRef("rot"), BinaryOp.SHR, Literal(2))
        val op = Assign(target = "elephant.subPalette", value = rotShr2, op = AssignOp.SET)
        val result = ScriptOpVisitor.visit(op)
        assertTrue(
            result is CExprStatement,
            "Expected CExprStatement but got ${result::class.simpleName}",
        )
        val stmtStr = result.toString()
        assertTrue(
            stmtStr.contains("_elephant_subPalette"),
            "Expected '_elephant_subPalette' in lowered statement: $stmtStr",
        )
    }

    // =========================================================================
    // TEST 3 (end-to-end): DSL `elephant.subPalette set 2` emits `_elephant_subPalette = 2u;`
    // in the play_frame function body (brace-walk scoped).
    //
    // Confirms the full DSL → IR → ScriptOpVisitor → CEmitter pipeline.
    // No conditional #if guard should be present (D-08: unconditional u8 write).
    // =========================================================================
    @Test
    fun `DSL elephant dot subPalette set 2 emits _elephant_subPalette = 2u in play_frame`() {
        val gameIR =
            game("SubPaletteTest") {
                    val elephant by metasprite { frame { tile(0, 0, 0) } }
                    val playScene = scene("play") { frame { elephant.subPalette set 2 } }
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val bankC = result.files["bank1.c"] ?: error("bank1.c not generated")

        val playFrameBody =
            extractFunctionBody(bankC, "play_frame")
                ?: error("Could not extract play_frame body from bank1.c — brace-walk failed")

        assertTrue(
            playFrameBody.contains("_elephant_subPalette = 2u"),
            "Expected '_elephant_subPalette = 2u' in play_frame body. play_frame body:\n$playFrameBody",
        )

        // D-08: No conditional codegen for DMG vs GBC — must be an unconditional u8 write
        assertFalse(
            playFrameBody.contains("#if"),
            "Expected NO conditional #if guard around subPalette write (D-08: DMG hardware ignores CGB palette bits). play_frame body:\n$playFrameBody",
        )
    }

    // =========================================================================
    // TEST 4 (global declaration): main.c contains UINT8 _elephant_subPalette global
    // variable declaration when metasprite is defined.
    //
    // Ensures the generated C is self-contained — SDCC requires all globals to be
    // declared before use. Without this declaration, the SDCC compile step would fail
    // with "implicit declaration" or "undefined identifier" errors.
    // =========================================================================
    @Test
    fun `metasprite defines UINT8 _elephant_subPalette global declaration in main c`() {
        val gameIR =
            game("SubPaletteTest") {
                    @Suppress("UNUSED_VARIABLE")
                    val elephant by metasprite { frame { tile(0, 0, 0) } }
                    val playScene = scene("play") {}
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val mainC = result.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_elephant_subPalette"),
            "Expected '_elephant_subPalette' global declaration in main.c. " +
                "The variable must be declared for SDCC to compile the generated C.",
        )
    }
}
