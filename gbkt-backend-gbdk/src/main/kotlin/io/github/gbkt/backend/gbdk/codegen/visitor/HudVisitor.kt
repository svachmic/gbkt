/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.GBDKMacros
import io.github.gbkt.backend.gbdk.codegen.ast.CArray
import io.github.gbkt.backend.gbdk.codegen.ast.CArrayAccess
import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CConst
import io.github.gbkt.backend.gbdk.codegen.ast.CExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFor
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CParam
import io.github.gbkt.backend.gbdk.codegen.ast.CPointer
import io.github.gbkt.backend.gbdk.codegen.ast.CRawExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CReturn
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CStringLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CUnaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.backend.gbdk.codegen.ast.CVoid
import io.github.gbkt.core.ir.Anchor
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.HudBar
import io.github.gbkt.core.ir.HudDef
import io.github.gbkt.core.ir.HudElement
import io.github.gbkt.core.ir.HudIcons
import io.github.gbkt.core.ir.HudNumber
import io.github.gbkt.core.ir.IconDisplayMode

// =============================================================================
// HUD VISITOR
// Generates C code for HUD panels: global variables (visibility flags, prev
// values, tile constants), helper functions (digit printing, background-layer
// text/clear), and per-HUD update/show/hide functions with change-detection
// rendering.
//
// All generated code is C89-compliant:
//  - Loop variables declared before loops (CVarDecl before CFor with init=null)
//  - Tile writes use set_win_tiles (window layer) or set_bkg_tiles (background)
//  - Change detection via _prev sentinel (0xFF) forces full redraw on show
// =============================================================================

class HudVisitor(private val gameIR: GameIR) {

    /**
     * Generate UINT8 global variable declarations for all HUD panels:
     * - `_hud_<id>_visible` — 0 = hidden, 1 = visible
     * - `_hud_<id>_<elem>_prev` — previous value for each element; initialized to 0xFF sentinel to
     *   force a full redraw on the first frame the HUD becomes visible.
     *
     * Also generates per-HUD display variables for animated bars (when fillFrames > 0):
     * - `_hud_<id>_<bar>_display` — current animated display value, converges toward actual value
     */
    fun buildHudGlobalVars(): List<CVarDecl> {
        val vars = mutableListOf<CVarDecl>()

        // Space tile constant (tile index 0) for FILLED_ONLY icon mode empty slots
        val hasFilledOnlyIcons =
            gameIR.huds.any { hud ->
                hud.elements.any { it is HudIcons && it.displayMode == IconDisplayMode.FILLED_ONLY }
            }
        if (hasFilledOnlyIcons) {
            vars +=
                CVarDecl(name = "_hud_space_tile", type = CConst(CU8), initializer = CLiteral(0))
        }

        for (hud in gameIR.huds) {
            val hudId = hud.id.replace('-', '_').replace(' ', '_')
            // Visibility flag
            vars += CVarDecl(name = "_hud_${hudId}_visible", type = CU8, initializer = CLiteral(0))
            // Per-element prev value (0xFF sentinel forces first-frame redraw)
            // and tile constant globals
            for (elem in hud.elements) {
                val elemId = hudElementId(elem)
                vars +=
                    CVarDecl(
                        name = "_hud_${hudId}_${elemId}_prev",
                        type = CU8,
                        initializer = CRawExpr("0xFF"),
                    )
                // Animated bar display variable
                if (elem is HudBar && elem.fillFrames > 0) {
                    vars +=
                        CVarDecl(
                            name = "_hud_${hudId}_${elemId}_display",
                            type = CU8,
                            initializer = CLiteral(0),
                        )
                }
                // Tile constant globals — const UINT8 so address can be passed to set_win_tiles
                when (elem) {
                    is HudBar -> {
                        vars +=
                            CVarDecl(
                                name = "_hud_fill_tile_${hudId}_${elemId}",
                                type = CConst(CU8),
                                initializer = CLiteral(elem.fillTile),
                            )
                        vars +=
                            CVarDecl(
                                name = "_hud_empty_tile_${hudId}_${elemId}",
                                type = CConst(CU8),
                                initializer = CLiteral(elem.emptyTile),
                            )
                    }
                    is HudIcons -> {
                        vars +=
                            CVarDecl(
                                name = "_hud_full_icon_${hudId}_${elemId}",
                                type = CConst(CU8),
                                initializer = CLiteral(elem.fullTile),
                            )
                        vars +=
                            CVarDecl(
                                name = "_hud_empty_icon_${hudId}_${elemId}",
                                type = CConst(CU8),
                                initializer = CLiteral(elem.emptyTile),
                            )
                    }
                    else -> Unit
                }
            }
        }
        return vars
    }

    /**
     * Generate all HUD-related C functions for the HOME bank:
     * - `_hud_print_u8(x, y, value)` — digit extraction helper (window layer)
     * - `_bkg_print_at(x, y, str, len)` — background layer text helper (when renderOnWindow=false)
     * - `_bkg_clear_region(x, y, w, h)` — background layer clear helper (when renderOnWindow=false)
     * - `update_hud_<id>()` — per-panel update function with change-detection
     * - `show_hud_<id>()` — set visible flag, reset _prev to force full redraw
     * - `hide_hud_<id>()` — set visible flag to 0, clear region
     *
     * All tile writes use `set_win_tiles` (window layer, default) or `set_bkg_tiles` (background
     * layer, when [HudDef.renderOnWindow] = false). The selection is static per HudDef.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun buildHudFunctions(): List<CFunction> {
        if (gameIR.huds.isEmpty()) return emptyList()
        val functions = mutableListOf<CFunction>()

        // _hud_print_u8 helper — decimal digit extraction for UINT8 values
        functions += buildHudPrintU8Helper()

        // Background layer helpers if any HUD explicitly uses BG rendering (not suppressed ones)
        val hasBackgroundHuds = gameIR.huds.any { !it.renderOnWindow && !isHudSuppressed(it) }
        if (hasBackgroundHuds) {
            functions += buildBkgPrintAtHelper()
            functions += buildBkgClearRegionHelper()
        }

        // Per-HUD functions
        for (hud in gameIR.huds) {
            functions += buildHudUpdateFunction(hud)
            functions += buildHudShowFunction(hud)
            functions += buildHudHideFunction(hud)
        }

        return functions
    }

    /**
     * Inject `update_hud_<id>()` calls at the start of each scene frame function body.
     *
     * Mirrors addMovementAndAnimationCalls — HUD updates run BEFORE user script ops so that display
     * state reflects the current variable values.
     */
    fun addHudUpdateCalls(functions: List<CFunction>, sceneId: String): List<CFunction> {
        if (gameIR.huds.isEmpty()) return functions

        val hudCalls =
            gameIR.huds.map { hud ->
                val hudId = hud.id.replace('-', '_').replace(' ', '_')
                CExprStatement(CCall("update_hud_$hudId", emptyList()))
            }

        return functions.map { fn ->
            if (fn.name == "${sceneId}_frame") {
                fn.copy(body = hudCalls + fn.body)
            } else {
                fn
            }
        }
    }

    /** Extract the identifier string for a [HudElement]. */
    private fun hudElementId(elem: HudElement): String =
        when (elem) {
            is HudBar -> elem.id.replace('-', '_').replace(' ', '_')
            is HudNumber -> elem.id.replace('-', '_').replace(' ', '_')
            is HudIcons -> elem.id.replace('-', '_').replace(' ', '_')
        }

    /**
     * Resolve a [HudDef.anchor] to (tileX, tileY) tile coordinates, respecting [HudDef.tileX] /
     * [HudDef.tileY] overrides.
     *
     * Screen dimensions: 20 tiles wide x 18 tiles tall (160x144 pixels at 8x8 tile size).
     */
    private fun resolveHudPosition(hud: HudDef, width: Int = 0): Pair<Int, Int> {
        val tx = hud.tileX
        val ty = hud.tileY
        if (tx != null && ty != null) return tx to ty
        val center = (20 - width) / 2
        return when (hud.anchor) {
            Anchor.TOP_LEFT -> 0 to 0
            Anchor.TOP_RIGHT -> (20 - width) to 0
            Anchor.BOTTOM_LEFT -> 0 to 17
            Anchor.BOTTOM_RIGHT -> (20 - width) to 17
            Anchor.TOP -> center to 0
            Anchor.BOTTOM -> center to 17
            Anchor.LEFT -> 0 to 9
            Anchor.RIGHT -> (20 - width) to 9
            Anchor.CENTER -> center to 8
        }
    }

    /**
     * Determine whether a HUD should effectively render on the window layer.
     *
     * Game Boy hardware limitation: the window layer always extends from its Y position to the
     * bottom of the screen. A top-positioned window-layer HUD (tileY < 9) would cover all BG
     * content below it (bricks, text, etc.). Only bottom-half HUDs can safely use the window layer.
     *
     * The [HudDef.renderOnWindow] flag represents the user's intent. This method overrides it based
     * on anchor position because the hardware can't support top-positioned window overlays without
     * scanline interrupts.
     */
    private fun effectiveRenderOnWindow(hud: HudDef): Boolean {
        if (!hud.renderOnWindow) return false
        val (_, tileY) = resolveHudPosition(hud)
        return tileY >= 9
    }

    /**
     * Whether a HUD should be suppressed entirely.
     *
     * A top-anchored window HUD (tileY < 9, renderOnWindow = true) cannot render on the window
     * layer (would cover the entire BG) and cannot render on the BG layer either (its tile-based
     * elements like icons use custom tile indices that produce garbled output on the BG font).
     * These HUDs are suppressed — show/update/hide become no-ops.
     *
     * Games with top-anchored HUDs should use `renderOnBackground()` in the DSL (which uses
     * BG-compatible text rendering) or use `print()` calls for BG-layer score display.
     */
    private fun isHudSuppressed(hud: HudDef): Boolean {
        if (!hud.renderOnWindow) return false
        val (_, tileY) = resolveHudPosition(hud)
        return tileY < 9
    }

    /**
     * Generate `_hud_print_u8(x, y, value)` — prints a UINT8 value (0-255) as decimal digits to the
     * window layer using `set_win_tiles`.
     *
     * Generated C (simplified):
     * ```c
     * void _hud_print_u8(UINT8 x, UINT8 y, UINT8 value) {
     *     UINT8 buf[3];
     *     UINT8 len;
     *     UINT8 i;
     *     buf[0] = '0' + value / 100;
     *     buf[1] = '0' + (value / 10) % 10;
     *     buf[2] = '0' + value % 10;
     *     len = 3;
     *     for (i = 0; i < len; i++) {
     *         set_win_tiles(x + i, y, 1, 1, &buf[i]);
     *     }
     * }
     * ```
     */
    private fun buildHudPrintU8Helper(): CFunction {
        val body =
            buildList<CStatement> {
                // C89: all declarations before statements
                add(CVarDecl("buf", CArray(CU8, 3), initializer = null))
                add(CVarDecl("len", CU8, initializer = null))
                add(CVarDecl("i", CU8, initializer = null))
                // buf[0] = '0' + value / 100
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(CVar("buf"), CLiteral(0)),
                            "=",
                            CBinaryExpr(
                                CLiteral(48),
                                "+",
                                CBinaryExpr(CVar("value"), "/", CLiteral(100)),
                            ),
                        )
                    )
                )
                // buf[1] = '0' + (value / 10) % 10
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(CVar("buf"), CLiteral(1)),
                            "=",
                            CBinaryExpr(
                                CLiteral(48),
                                "+",
                                CBinaryExpr(
                                    CBinaryExpr(CVar("value"), "/", CLiteral(10)),
                                    "%",
                                    CLiteral(10),
                                ),
                            ),
                        )
                    )
                )
                // buf[2] = '0' + value % 10
                add(
                    CExprStatement(
                        CBinaryExpr(
                            CArrayAccess(CVar("buf"), CLiteral(2)),
                            "=",
                            CBinaryExpr(
                                CLiteral(48),
                                "+",
                                CBinaryExpr(CVar("value"), "%", CLiteral(10)),
                            ),
                        )
                    )
                )
                // len = 3
                add(CExprStatement(CBinaryExpr(CVar("len"), "=", CLiteral(3))))
                // for (i = 0; i < len; i++) { set_win_tiles(x+i, y, 1, 1, &buf[i]); }
                add(CExprStatement(CBinaryExpr(CVar("i"), "=", CLiteral(0))))
                add(
                    CFor(
                        init = null,
                        condition = CBinaryExpr(CVar("i"), "<", CVar("len")),
                        increment = CUnaryExpr("++", CVar("i")),
                        body =
                            listOf(
                                CExprStatement(
                                    CCall(
                                        "set_win_tiles",
                                        listOf(
                                            CBinaryExpr(CVar("x"), "+", CVar("i")),
                                            CVar("y"),
                                            CLiteral(1),
                                            CLiteral(1),
                                            CRawExpr("&buf[i]"),
                                        ),
                                    )
                                )
                            ),
                    )
                )
            }
        return CFunction(
            name = "_hud_print_u8",
            returnType = CVoid,
            params = listOf(CParam("x", CU8), CParam("y", CU8), CParam("value", CU8)),
            body = body,
            bank = 0,
            sectionComment = "HUD helpers (change-detection rendering)",
        )
    }

    /**
     * Generate `_bkg_print_at(x, y, str, len)` — background-layer equivalent of `_win_print_at`.
     * Used by HUDs with [HudDef.renderOnWindow] = false.
     */
    private fun buildBkgPrintAtHelper(): CFunction {
        val loopVar = CVarDecl("i", CU8, initializer = null)
        val forLoop =
            CFor(
                init = null,
                condition = CBinaryExpr(CVar("i"), "<", CVar("len")),
                increment = CUnaryExpr("++", CVar("i")),
                body =
                    listOf(
                        CExprStatement(
                            CCall(
                                "set_bkg_tiles",
                                listOf(
                                    CBinaryExpr(CVar("x"), "+", CVar("i")),
                                    CVar("y"),
                                    CLiteral(1),
                                    CLiteral(1),
                                    CRawExpr("(unsigned char*)&str[i]"),
                                ),
                            )
                        )
                    ),
            )
        return CFunction(
            name = "_bkg_print_at",
            returnType = CVoid,
            params =
                listOf(
                    CParam("x", CU8),
                    CParam("y", CU8),
                    CParam("str", CPointer(CConst(CU8))),
                    CParam("len", CU8),
                ),
            body = listOf(loopVar, forLoop),
            bank = 0,
        )
    }

    /**
     * Generate `_bkg_clear_region(x, y, w, h)` — background-layer equivalent of
     * `_win_clear_region`. Used by HUDs with [HudDef.renderOnWindow] = false.
     */
    private fun buildBkgClearRegionHelper(): CFunction {
        val emptyTileBuf = CRawExpr("&_bkg_clear_tile")
        val body =
            buildList<CStatement> {
                add(CVarDecl("_bkg_clear_tile", CU8, CLiteral(0)))
                add(CVarDecl("_bcy", CU8, initializer = null))
                add(CVarDecl("_bcx", CU8, initializer = null))
                add(CExprStatement(CBinaryExpr(CVar("_bcy"), "=", CLiteral(0))))
                add(
                    CFor(
                        init = null,
                        condition = CBinaryExpr(CVar("_bcy"), "<", CVar("h")),
                        increment = CUnaryExpr("++", CVar("_bcy")),
                        body =
                            listOf(
                                CExprStatement(CBinaryExpr(CVar("_bcx"), "=", CLiteral(0))),
                                CFor(
                                    init = null,
                                    condition = CBinaryExpr(CVar("_bcx"), "<", CVar("w")),
                                    increment = CUnaryExpr("++", CVar("_bcx")),
                                    body =
                                        listOf(
                                            CExprStatement(
                                                CCall(
                                                    "set_bkg_tiles",
                                                    listOf(
                                                        CBinaryExpr(CVar("x"), "+", CVar("_bcx")),
                                                        CBinaryExpr(CVar("y"), "+", CVar("_bcy")),
                                                        CLiteral(1),
                                                        CLiteral(1),
                                                        emptyTileBuf,
                                                    ),
                                                )
                                            )
                                        ),
                                ),
                            ),
                    )
                )
            }
        return CFunction(
            name = "_bkg_clear_region",
            returnType = CVoid,
            params = listOf(CParam("x", CU8), CParam("y", CU8), CParam("w", CU8), CParam("h", CU8)),
            body = body,
            bank = 0,
        )
    }

    /**
     * Generate `update_hud_<id>()` function for a [HudDef].
     *
     * Structure:
     * 1. Early return if not visible (`_hud_<id>_visible == 0`)
     * 2. For each element: check current value against _prev (change detection)
     * 3. If changed: update _prev, redraw element
     *
     * All tile writes use `set_win_tiles` (renderOnWindow=true, default) or `set_bkg_tiles`
     * (renderOnWindow=false). The selection is static per HudDef (Kotlin-time choice).
     */
    @Suppress("LongMethod")
    private fun buildHudUpdateFunction(hud: HudDef): CFunction {
        val hudId = hud.id.replace('-', '_').replace(' ', '_')
        val tileFunc = if (hud.renderOnWindow) "set_win_tiles" else "set_bkg_tiles"
        val printFunc = if (hud.renderOnWindow) "_win_print_at" else "_bkg_print_at"
        val (baseX, baseY) = resolveHudPosition(hud)

        val body =
            buildList<CStatement> {
                // Early return if not visible
                add(
                    CIf(
                        condition = CBinaryExpr(CVar("_hud_${hudId}_visible"), "==", CLiteral(0)),
                        thenBody = listOf(CReturn(null)),
                    )
                )

                // Per-element rendering with change detection
                var elemOffset = 0
                for (elem in hud.elements) {
                    val elemId = hudElementId(elem)
                    val prevVar = "_hud_${hudId}_${elemId}_prev"
                    val elemX = baseX + elemOffset

                    when (elem) {
                        is HudBar -> {
                            val varName = "_${elem.variable.replace('-', '_').replace(' ', '_')}"
                            // Capture nullable field into local val for cross-module smart cast
                            val barMaxVariable: String? = elem.maxVariable
                            val maxVarOrLit: CExpr =
                                if (barMaxVariable != null)
                                    CVar("_${barMaxVariable.replace('-', '_').replace(' ', '_')}")
                                else CLiteral(elem.maxValue)
                            val thenBody =
                                buildList<CStatement> {
                                    // Update prev
                                    add(
                                        CExprStatement(
                                            CBinaryExpr(CVar(prevVar), "=", CVar(varName))
                                        )
                                    )
                                    // Calculate filled tiles: filled = (value * width) / maxValue
                                    add(CVarDecl("_hfi", CU8, initializer = null))
                                    add(CVarDecl("_hfilled", CU8, initializer = null))
                                    add(
                                        CExprStatement(
                                            CBinaryExpr(
                                                CVar("_hfilled"),
                                                "=",
                                                CBinaryExpr(
                                                    CBinaryExpr(
                                                        CVar(varName),
                                                        "*",
                                                        CLiteral(elem.width),
                                                    ),
                                                    "/",
                                                    maxVarOrLit,
                                                ),
                                            )
                                        )
                                    )
                                    // For loop rendering fill tiles
                                    add(CExprStatement(CBinaryExpr(CVar("_hfi"), "=", CLiteral(0))))
                                    add(
                                        CFor(
                                            init = null,
                                            condition =
                                                CBinaryExpr(
                                                    CVar("_hfi"),
                                                    "<",
                                                    CLiteral(elem.width),
                                                ),
                                            increment = CUnaryExpr("++", CVar("_hfi")),
                                            body =
                                                listOf(
                                                    CIf(
                                                        condition =
                                                            CBinaryExpr(
                                                                CVar("_hfi"),
                                                                "<",
                                                                CVar("_hfilled"),
                                                            ),
                                                        thenBody =
                                                            listOf(
                                                                CExprStatement(
                                                                    CCall(
                                                                        tileFunc,
                                                                        listOf(
                                                                            CBinaryExpr(
                                                                                CLiteral(elemX),
                                                                                "+",
                                                                                CVar("_hfi"),
                                                                            ),
                                                                            CLiteral(baseY),
                                                                            CLiteral(1),
                                                                            CLiteral(1),
                                                                            CRawExpr(
                                                                                "(unsigned char*)&_hud_fill_tile_${hudId}_${elemId}"
                                                                            ),
                                                                        ),
                                                                    )
                                                                )
                                                            ),
                                                        elseBody =
                                                            listOf(
                                                                CExprStatement(
                                                                    CCall(
                                                                        tileFunc,
                                                                        listOf(
                                                                            CBinaryExpr(
                                                                                CLiteral(elemX),
                                                                                "+",
                                                                                CVar("_hfi"),
                                                                            ),
                                                                            CLiteral(baseY),
                                                                            CLiteral(1),
                                                                            CLiteral(1),
                                                                            CRawExpr(
                                                                                "(unsigned char*)&_hud_empty_tile_${hudId}_${elemId}"
                                                                            ),
                                                                        ),
                                                                    )
                                                                )
                                                            ),
                                                    )
                                                ),
                                        )
                                    )
                                }
                            add(
                                CIf(
                                    condition = CBinaryExpr(CVar(varName), "!=", CVar(prevVar)),
                                    thenBody = thenBody,
                                )
                            )
                            elemOffset += elem.width
                        }

                        is HudNumber -> {
                            val varName = "_${elem.variable.replace('-', '_').replace(' ', '_')}"
                            val thenBody =
                                buildList<CStatement> {
                                    add(
                                        CExprStatement(
                                            CBinaryExpr(CVar(prevVar), "=", CVar(varName))
                                        )
                                    )
                                    // Print label if non-empty
                                    if (elem.label.isNotEmpty()) {
                                        add(
                                            CExprStatement(
                                                CCall(
                                                    printFunc,
                                                    listOf(
                                                        CLiteral(elemX),
                                                        CLiteral(baseY),
                                                        CStringLiteral(elem.label),
                                                        CLiteral(elem.label.length),
                                                    ),
                                                )
                                            )
                                        )
                                    }
                                    // Print value digits
                                    val valueX = elemX + elem.label.length
                                    add(
                                        CExprStatement(
                                            CCall(
                                                "_hud_print_u8",
                                                listOf(
                                                    CLiteral(valueX),
                                                    CLiteral(baseY),
                                                    CVar(varName),
                                                ),
                                            )
                                        )
                                    )
                                }
                            add(
                                CIf(
                                    condition = CBinaryExpr(CVar(varName), "!=", CVar(prevVar)),
                                    thenBody = thenBody,
                                )
                            )
                            elemOffset += elem.label.length + 3 // label + max 3 digits
                        }

                        is HudIcons -> {
                            val varName = "_${elem.variable.replace('-', '_').replace(' ', '_')}"
                            val thenBody =
                                buildList<CStatement> {
                                    add(
                                        CExprStatement(
                                            CBinaryExpr(CVar(prevVar), "=", CVar(varName))
                                        )
                                    )
                                    add(CVarDecl("_hii", CU8, initializer = null))
                                    add(CExprStatement(CBinaryExpr(CVar("_hii"), "=", CLiteral(0))))
                                    val iconElseBody: List<CStatement> =
                                        if (elem.displayMode == IconDisplayMode.FULL_AND_EMPTY) {
                                            listOf(
                                                CExprStatement(
                                                    CCall(
                                                        tileFunc,
                                                        listOf(
                                                            CBinaryExpr(
                                                                CLiteral(elemX),
                                                                "+",
                                                                CVar("_hii"),
                                                            ),
                                                            CLiteral(baseY),
                                                            CLiteral(1),
                                                            CLiteral(1),
                                                            CRawExpr(
                                                                "(unsigned char*)&_hud_empty_icon_${hudId}_${elemId}"
                                                            ),
                                                        ),
                                                    )
                                                )
                                            )
                                        } else {
                                            // FILLED_ONLY: write space tile (0) for empty slots
                                            listOf(
                                                CExprStatement(
                                                    CCall(
                                                        tileFunc,
                                                        listOf(
                                                            CBinaryExpr(
                                                                CLiteral(elemX),
                                                                "+",
                                                                CVar("_hii"),
                                                            ),
                                                            CLiteral(baseY),
                                                            CLiteral(1),
                                                            CLiteral(1),
                                                            CRawExpr(
                                                                "(unsigned char*)&_hud_space_tile"
                                                            ),
                                                        ),
                                                    )
                                                )
                                            )
                                        }
                                    add(
                                        CFor(
                                            init = null,
                                            condition =
                                                CBinaryExpr(
                                                    CVar("_hii"),
                                                    "<",
                                                    CLiteral(elem.maxValue),
                                                ),
                                            increment = CUnaryExpr("++", CVar("_hii")),
                                            body =
                                                listOf(
                                                    CIf(
                                                        condition =
                                                            CBinaryExpr(
                                                                CVar("_hii"),
                                                                "<",
                                                                CVar(varName),
                                                            ),
                                                        thenBody =
                                                            listOf(
                                                                CExprStatement(
                                                                    CCall(
                                                                        tileFunc,
                                                                        listOf(
                                                                            CBinaryExpr(
                                                                                CLiteral(elemX),
                                                                                "+",
                                                                                CVar("_hii"),
                                                                            ),
                                                                            CLiteral(baseY),
                                                                            CLiteral(1),
                                                                            CLiteral(1),
                                                                            CRawExpr(
                                                                                "(unsigned char*)&_hud_full_icon_${hudId}_${elemId}"
                                                                            ),
                                                                        ),
                                                                    )
                                                                )
                                                            ),
                                                        elseBody = iconElseBody,
                                                    )
                                                ),
                                        )
                                    )
                                }
                            add(
                                CIf(
                                    condition = CBinaryExpr(CVar(varName), "!=", CVar(prevVar)),
                                    thenBody = thenBody,
                                )
                            )
                            elemOffset += elem.maxValue
                        }
                    }
                }
            }

        return CFunction(
            name = "update_hud_$hudId",
            returnType = CVoid,
            body = body,
            bank = 0,
            sectionComment = "HUD: ${hud.id}",
        )
    }

    /**
     * Generate `show_hud_<id>()` — set visibility flag to 1 and reset all _prev globals to 0xFF
     * sentinel so the next update_hud_<id> call performs a full redraw.
     *
     * For window-layer HUDs ([HudDef.renderOnWindow] = true), also emits `SHOW_WIN`. For
     * background-layer HUDs, SHOW_WIN is omitted (background always visible).
     */
    private fun buildHudShowFunction(hud: HudDef): CFunction {
        val hudId = hud.id.replace('-', '_').replace(' ', '_')
        // Suppressed HUDs get an empty show function (visible stays 0 → update is a no-op)
        if (isHudSuppressed(hud)) {
            return CFunction(
                name = "show_hud_$hudId",
                returnType = CVoid,
                body = emptyList(),
                bank = 0,
            )
        }
        val body =
            buildList<CStatement> {
                // Set visible flag
                add(CExprStatement(CBinaryExpr(CVar("_hud_${hudId}_visible"), "=", CLiteral(1))))
                // Reset all prev variables to 0xFF to force full redraw
                for (elem in hud.elements) {
                    val elemId = hudElementId(elem)
                    add(
                        CExprStatement(
                            CBinaryExpr(CVar("_hud_${hudId}_${elemId}_prev"), "=", CRawExpr("0xFF"))
                        )
                    )
                }
                // Position and show window layer if applicable (bottom-anchored HUDs only)
                if (effectiveRenderOnWindow(hud)) {
                    val (_, baseY) = resolveHudPosition(hud)
                    add(CExprStatement(CCall("move_win", listOf(CLiteral(7), CLiteral(baseY * 8)))))
                    add(GBDKMacros.showWin())
                }
            }
        return CFunction(name = "show_hud_$hudId", returnType = CVoid, body = body, bank = 0)
    }

    /**
     * Generate `hide_hud_<id>()` — set visibility flag to 0 and clear the HUD region.
     *
     * For window-layer HUDs ([HudDef.renderOnWindow] = true), calls `_win_clear_region`. For
     * background-layer HUDs, calls `_bkg_clear_region`. HIDE_WIN is NOT emitted — other UI elements
     * may still be using the window layer.
     */
    private fun buildHudHideFunction(hud: HudDef): CFunction {
        val hudId = hud.id.replace('-', '_').replace(' ', '_')
        val (baseX, baseY) = resolveHudPosition(hud)
        // Estimate HUD width from elements (sum of element widths)
        val totalWidth =
            hud.elements
                .sumOf { elem ->
                    when (elem) {
                        is HudBar -> elem.width
                        is HudNumber -> elem.label.length + 3
                        is HudIcons -> elem.maxValue
                    }
                }
                .coerceAtLeast(1)
        val clearFunc = if (hud.renderOnWindow) "_win_clear_region" else "_bkg_clear_region"
        val body =
            buildList<CStatement> {
                add(CExprStatement(CBinaryExpr(CVar("_hud_${hudId}_visible"), "=", CLiteral(0))))
                add(
                    CExprStatement(
                        CCall(
                            clearFunc,
                            listOf(
                                CLiteral(baseX),
                                CLiteral(baseY),
                                CLiteral(totalWidth),
                                CLiteral(1),
                            ),
                        )
                    )
                )
            }
        return CFunction(name = "hide_hud_$hudId", returnType = CVoid, body = body, bank = 0)
    }
}
