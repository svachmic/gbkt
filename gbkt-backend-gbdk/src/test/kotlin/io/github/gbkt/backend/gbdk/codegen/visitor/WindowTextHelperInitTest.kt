/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.DialogDef
import io.github.gbkt.core.ir.FontMode
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

// =============================================================================
// WINDOW TEXT HELPER INIT TEST — Plan 07.4-31 / DEFERRED-07.4-27-01
//
// Pins the invariant: every loop-counter CVarDecl in the _win_* (and _vwf_*)
// helpers emitted by DialogVisitor.kt MUST have initializer = CLiteral(0).
//
// Without this initializer SDCC places an uninitialised UINT8 on the stack;
// the for-loop condition reads garbage, producing wrong text rendering or
// partial clear regions on real hardware.
//
// These tests FAIL at HEAD (pre-fix) because DialogVisitor.kt currently emits
// `CVarDecl("i", CU8, initializer = null)` etc. They go GREEN after the fix
// in Task 2.
// =============================================================================

class WindowTextHelperInitTest {

    private val baseGameIR =
        GameIR(name = "TestGame", config = CartridgeConfig(), scenes = listOf(SceneIR(id = "main")))

    // GameIR with a VWF dialog — required for _vwf_print_at to be emitted
    private val vwfGameIR =
        baseGameIR.copy(
            dialogs = listOf(DialogDef(id = "greeting", fontMode = FontMode.VARIABLE_WIDTH))
        )

    /** Helper: find the named CFunction from DialogVisitor.buildDialogFunctions() body. */
    private fun findFunction(gameIR: GameIR, name: String) =
        DialogVisitor(gameIR).buildDialogFunctions().find { it.name == name }
            ?: error("$name not found in buildDialogFunctions() output")

    /** Helper: locate all CVarDecl statements (including nested in function body). */
    private fun CFunction.varDecls(): List<CVarDecl> = body.filterIsInstance<CVarDecl>()

    // =========================================================================
    // TEST 1: _win_print_at — loop counter `i` must have initializer = CLiteral(0)
    // =========================================================================
    @Test
    fun `win_print_at helper initialises loop counter i to zero`() {
        val fn = findFunction(baseGameIR, "_win_print_at")
        val iDecl =
            fn.varDecls().find { it.name == "i" }
                ?: error("CVarDecl 'i' not found in _win_print_at body; body = ${fn.body}")

        assertNotNull(
            iDecl.initializer,
            "CVarDecl 'i' in _win_print_at must have a non-null initializer",
        )
        assertEquals(
            CLiteral(0),
            iDecl.initializer,
            "CVarDecl 'i' in _win_print_at must be initialised to CLiteral(0)",
        )
    }

    // =========================================================================
    // TEST 2: _win_clear_region — loop counters `ry` and `rx` must be initialised
    // =========================================================================
    @Test
    fun `win_clear_region helper initialises loop counters ry and rx to zero`() {
        val fn = findFunction(baseGameIR, "_win_clear_region")
        val decls = fn.varDecls()

        val ryDecl =
            decls.find { it.name == "ry" }
                ?: error("CVarDecl 'ry' not found in _win_clear_region body; body = ${fn.body}")
        val rxDecl =
            decls.find { it.name == "rx" }
                ?: error("CVarDecl 'rx' not found in _win_clear_region body; body = ${fn.body}")

        assertNotNull(
            ryDecl.initializer,
            "CVarDecl 'ry' in _win_clear_region must have a non-null initializer",
        )
        assertEquals(
            CLiteral(0),
            ryDecl.initializer,
            "CVarDecl 'ry' in _win_clear_region must be initialised to CLiteral(0)",
        )

        assertNotNull(
            rxDecl.initializer,
            "CVarDecl 'rx' in _win_clear_region must have a non-null initializer",
        )
        assertEquals(
            CLiteral(0),
            rxDecl.initializer,
            "CVarDecl 'rx' in _win_clear_region must be initialised to CLiteral(0)",
        )
    }

    // =========================================================================
    // TEST 3: _win_fill_screen — loop counters `fy` and `fx` must be initialised
    // =========================================================================
    @Test
    fun `win_fill_screen helper initialises loop counters fy and fx to zero`() {
        val fn = findFunction(baseGameIR, "_win_fill_screen")
        val decls = fn.varDecls()

        val fyDecl =
            decls.find { it.name == "fy" }
                ?: error("CVarDecl 'fy' not found in _win_fill_screen body; body = ${fn.body}")
        val fxDecl =
            decls.find { it.name == "fx" }
                ?: error("CVarDecl 'fx' not found in _win_fill_screen body; body = ${fn.body}")

        assertNotNull(
            fyDecl.initializer,
            "CVarDecl 'fy' in _win_fill_screen must have a non-null initializer",
        )
        assertEquals(
            CLiteral(0),
            fyDecl.initializer,
            "CVarDecl 'fy' in _win_fill_screen must be initialised to CLiteral(0)",
        )

        assertNotNull(
            fxDecl.initializer,
            "CVarDecl 'fx' in _win_fill_screen must have a non-null initializer",
        )
        assertEquals(
            CLiteral(0),
            fxDecl.initializer,
            "CVarDecl 'fx' in _win_fill_screen must be initialised to CLiteral(0)",
        )
    }

    // =========================================================================
    // TEST 4: _vwf_print_at — loop counters `i`, `px`, `tile_x` must be initialised
    // (emitted only when FontMode.VARIABLE_WIDTH dialog exists)
    // =========================================================================
    @Test
    fun `vwf_print_at helper initialises loop counters i px and tile_x to zero`() {
        val fn = findFunction(vwfGameIR, "_vwf_print_at")
        val decls = fn.varDecls()

        val iDecl =
            decls.find { it.name == "i" }
                ?: error("CVarDecl 'i' not found in _vwf_print_at body; body = ${fn.body}")
        val pxDecl =
            decls.find { it.name == "px" }
                ?: error("CVarDecl 'px' not found in _vwf_print_at body; body = ${fn.body}")
        val tileXDecl =
            decls.find { it.name == "tile_x" }
                ?: error("CVarDecl 'tile_x' not found in _vwf_print_at body; body = ${fn.body}")

        assertNotNull(
            iDecl.initializer,
            "CVarDecl 'i' in _vwf_print_at must have a non-null initializer",
        )
        assertEquals(
            CLiteral(0),
            iDecl.initializer,
            "CVarDecl 'i' in _vwf_print_at must be initialised to CLiteral(0)",
        )

        assertNotNull(
            pxDecl.initializer,
            "CVarDecl 'px' in _vwf_print_at must have a non-null initializer",
        )
        assertEquals(
            CLiteral(0),
            pxDecl.initializer,
            "CVarDecl 'px' in _vwf_print_at must be initialised to CLiteral(0)",
        )

        assertNotNull(
            tileXDecl.initializer,
            "CVarDecl 'tile_x' in _vwf_print_at must have a non-null initializer",
        )
        assertEquals(
            CLiteral(0),
            tileXDecl.initializer,
            "CVarDecl 'tile_x' in _vwf_print_at must be initialised to CLiteral(0) (but tile_x = x is set immediately after, so the initializer just needs to be present)",
        )
    }
}
