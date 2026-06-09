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
import io.github.gbkt.core.ir.Literal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// =============================================================================
// FLIP ACCESSOR EMISSION TESTS (Plan 10-08)
//
// Verifies D-07 runtime accessors for flipX / flipY:
//   1. Assign("elephant.flipX", Literal(1), SET) → CExprStatement(_elephant_flipX = 1u)
//   2. Assign("elephant.flipY", Literal(0), SET) → CExprStatement(_elephant_flipY = 0u)
//   3. end-to-end pipeline: DSL `elephant.flipX set true` emits _elephant_flipX = 1u in play_frame
//   4. end-to-end pipeline: `UINT8 _elephant_flipX` global declaration present in main.c
//
// Uses brace-walk extraction on `play_frame` body (CLAUDE.md §"Scope-level grep gates").
// =============================================================================

// ---------------------------------------------------------------------------
// Brace-walk helper: extract the body of the first C function matching signature
// (shared with GbcCompatEmissionTest — local copy for visitor test isolation)
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

class FlipAccessorEmissionTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // TEST 1: ScriptOpVisitor: Assign("elephant.flipX", Literal(1), SET) →
    //         CExprStatement(CBinaryExpr(CVar("_elephant_flipX"), "=", CLiteral(1)))
    //
    // Verifies the unit-level lowering: dot-notation target sanitization converts
    // "elephant.flipX" to "_elephant_flipX" (unsigned u8 write).
    // =========================================================================
    @Test
    fun `Assign elephant dot flipX SET lowers to _elephant_flipX = 1u`() {
        val op = Assign(target = "elephant.flipX", value = Literal(1), op = AssignOp.SET)
        val result = ScriptOpVisitor.visit(op)
        val expected = CExprStatement(CBinaryExpr(CVar("_elephant_flipX"), "=", CLiteral(1)))
        assertEquals(
            expected,
            result,
            "Expected _elephant_flipX = 1u assignment statement from visitAssign",
        )
    }

    // =========================================================================
    // TEST 2: ScriptOpVisitor: Assign("elephant.flipY", Literal(0), SET) →
    //         CExprStatement(CBinaryExpr(CVar("_elephant_flipY"), "=", CLiteral(0)))
    // =========================================================================
    @Test
    fun `Assign elephant dot flipY SET lowers to _elephant_flipY = 0u`() {
        val op = Assign(target = "elephant.flipY", value = Literal(0), op = AssignOp.SET)
        val result = ScriptOpVisitor.visit(op)
        val expected = CExprStatement(CBinaryExpr(CVar("_elephant_flipY"), "=", CLiteral(0)))
        assertEquals(
            expected,
            result,
            "Expected _elephant_flipY = 0u assignment statement from visitAssign",
        )
    }

    // =========================================================================
    // TEST 3 (end-to-end): DSL `elephant.flipX set true` emits `_elephant_flipX = 1u;`
    // in the play_frame function body (brace-walk scoped).
    //
    // Confirms the full DSL → IR → ScriptOpVisitor → CEmitter pipeline.
    // =========================================================================
    @Test
    fun `DSL elephant dot flipX set true emits _elephant_flipX = 1u in play_frame`() {
        val gameIR =
            game("FlipTest") {
                    val elephant by metasprite { frame { tile(0, 0, 0) } }
                    val playScene = scene("play") { frame { elephant.flipX set true } }
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val bankC = result.files["bank1.c"] ?: error("bank1.c not generated")

        val playFrameBody =
            extractFunctionBody(bankC, "play_frame")
                ?: error("Could not extract play_frame body from bank1.c — brace-walk failed")

        assertTrue(
            playFrameBody.contains("_elephant_flipX = 1u"),
            "Expected '_elephant_flipX = 1u' in play_frame body. play_frame body:\n$playFrameBody",
        )
    }

    // =========================================================================
    // TEST 4 (end-to-end): DSL `elephant.flipY set false` emits `_elephant_flipY = 0u;`
    // in the play_frame function body (brace-walk scoped).
    // =========================================================================
    @Test
    fun `DSL elephant dot flipY set false emits _elephant_flipY = 0u in play_frame`() {
        val gameIR =
            game("FlipTest") {
                    val elephant by metasprite { frame { tile(0, 0, 0) } }
                    val playScene = scene("play") { frame { elephant.flipY set false } }
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val bankC = result.files["bank1.c"] ?: error("bank1.c not generated")

        val playFrameBody =
            extractFunctionBody(bankC, "play_frame")
                ?: error("Could not extract play_frame body from bank1.c — brace-walk failed")

        assertTrue(
            playFrameBody.contains("_elephant_flipY = 0u"),
            "Expected '_elephant_flipY = 0u' in play_frame body. play_frame body:\n$playFrameBody",
        )
    }

    // =========================================================================
    // TEST 5 (global declaration): main.c contains UINT8 _elephant_flipX global
    // variable declaration when metasprite with flipX accessor is used.
    //
    // Ensures the generated C is self-contained — SDCC requires all globals to be
    // declared before use. Without this declaration, the SDCC compile step would fail
    // with "implicit declaration" or "undefined identifier" errors.
    // =========================================================================
    @Test
    fun `metasprite defines UINT8 _elephant_flipX global declaration in main c`() {
        val gameIR =
            game("FlipTest") {
                    val elephant by metasprite { frame { tile(0, 0, 0) } }
                    val playScene = scene("play") { frame { elephant.flipX set true } }
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val mainC = result.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_elephant_flipX"),
            "Expected '_elephant_flipX' global declaration in main.c. " +
                "The variable must be declared for SDCC to compile the generated C.",
        )
    }

    // =========================================================================
    // TEST 6 (global declaration): main.c contains UINT8 _elephant_flipY global
    // variable declaration when metasprite is defined (always emit both).
    // =========================================================================
    @Test
    fun `metasprite defines UINT8 _elephant_flipY global declaration in main c`() {
        val gameIR =
            game("FlipTest") {
                    @Suppress("UNUSED_VARIABLE")
                    val elephant by metasprite { frame { tile(0, 0, 0) } }
                    val playScene = scene("play") {}
                    start = playScene
                }
                .build()

        val result = pipeline.generate(gameIR)
        val mainC = result.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_elephant_flipY"),
            "Expected '_elephant_flipY' global declaration in main.c. " +
                "Both flipX and flipY vars are always declared for each metasprite.",
        )
    }
}
