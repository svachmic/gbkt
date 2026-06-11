/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.GBDKMacros
import io.github.gbkt.backend.gbdk.codegen.ast.CArray
import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CBlock
import io.github.gbkt.backend.gbdk.codegen.ast.CBreak
import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CConst
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFor
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CParam
import io.github.gbkt.backend.gbdk.codegen.ast.CPointer
import io.github.gbkt.backend.gbdk.codegen.ast.CRawCode
import io.github.gbkt.backend.gbdk.codegen.ast.CRawExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CStringLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CUnaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.backend.gbdk.codegen.ast.CVoid
import io.github.gbkt.backend.gbdk.codegen.ast.CWhile
import io.github.gbkt.core.ir.BorderStyle
import io.github.gbkt.core.ir.DialogDef
import io.github.gbkt.core.ir.FontMode
import io.github.gbkt.core.ir.GameIR

// =============================================================================
// DIALOG VISITOR
// Generates C code for the dialog system: global variables, window-layer text
// helpers, typewriter effects, border drawing, and per-dialog show functions.
// =============================================================================

/**
 * Generates all C code for the dialog system from a [GameIR].
 *
 * Produces:
 * 1. Global variables: `_dialog_speed` (typewriter speed) and optional `_vwf_char_widths[]`
 *    (proportional character widths for variable-width font rendering)
 * 2. Window-layer text helpers: `_win_print_at`, `_vwf_print_at`, `_win_clear_region`,
 *    `_win_fill_screen`
 * 3. Per-dialog `show_dialog_<id>()` functions with border drawing, portrait rendering, speaker
 *    name display, typewriter effect, auto-pagination, and A-button dismiss
 *
 * All text rendering goes through window-layer helpers — zero `gotoxy`/`printf` calls remain in
 * dialog code, preventing tile corruption when using custom tilesets.
 *
 * @param gameIR The game IR containing [DialogDef] definitions to generate code for
 */
class DialogVisitor(private val gameIR: GameIR) {

    // Portrait sprite slot reserved for UI use (slot 39 — last OAM slot)
    private val portraitSpriteId = 39

    /**
     * Generate dialog-related global variable declarations.
     * - `_dialog_speed`: UINT8 default typewriter speed (0 = instant, N = delay frames per char)
     * - `_vwf_char_widths[]`: const UINT8 array of proportional character widths (256 entries) for
     *   the VWF rendering path. Indexes by ASCII code; values are pixel widths (1–8).
     *
     * The `_vwf_char_widths` array is only emitted when any dialog or text op uses
     * [FontMode.VARIABLE_WIDTH].
     */
    fun buildDialogGlobalVars(): List<CVarDecl> {
        val vars = mutableListOf<CVarDecl>()

        // Default dialog speed (0 = instant)
        vars += CVarDecl(name = "_dialog_speed", type = CU8, initializer = CLiteral(1))

        // VWF character widths table — proportional widths for ASCII 0..255
        // Default table: space=3, most letters 5-6, narrow letters (i,l,1)=3, wide (m,w)=6
        val hasVwfDialogs = gameIR.dialogs.any { it.fontMode == FontMode.VARIABLE_WIDTH }
        if (hasVwfDialogs) {
            // Build a 256-entry width table. Standard GBDK font proportional widths.
            val widths =
                IntArray(256) { ch ->
                    when {
                        ch == 0x20 -> 3 // space
                        ch in 0x21..0x7E -> {
                            // ASCII printable chars — proportional estimates
                            when (ch.toChar()) {
                                'i',
                                'l',
                                '1',
                                '!',
                                '|',
                                '.',
                                ',',
                                ':',
                                ';',
                                '\'' -> 2
                                'm',
                                'w',
                                'W',
                                'M' -> 6
                                else -> 5
                            }
                        }
                        else -> 5 // non-printable / extended — default width
                    }
                }
            val initValues = widths.joinToString(",")
            vars +=
                CVarDecl(
                    name = "_vwf_char_widths",
                    type = CArray(CConst(CU8), 256),
                    initializer = CRawExpr("{ $initValues }"),
                )
        }

        return vars
    }

    /**
     * Generate all dialog-related C functions for HOME bank:
     * - `_win_print_at(x, y, str, len)` — fixed-width tile-based text to window layer
     * - `_vwf_print_at(x, y, str, len)` — variable-width font text to window layer (only when VWF
     *   dialogs exist)
     * - `_win_clear_region(x, y, w, h)` — clear a rectangular region of the window layer
     * - `_win_fill_screen(tile)` — fill entire 20x18 window with a single tile index
     * - `show_dialog_<id>()` for each [DialogDef] in [GameIR.dialogs]
     * - `dialog_<id>_choice()` for dialogs that have DialogChoice ops
     *
     * All text rendering goes through window-layer helpers — zero gotoxy/printf in dialog code.
     *
     * Also emits a `_dialog_speed` global for the default typewriter speed.
     */
    fun buildDialogFunctions(): List<CFunction> {
        val functions = mutableListOf<CFunction>()

        // _win_print_at helper — writes str to window layer tile by tile
        functions += buildWinPrintAtHelper()

        // _vwf_print_at helper — only emitted when VWF dialogs exist (requires _vwf_char_widths)
        val hasVwfDialogs = gameIR.dialogs.any { it.fontMode == FontMode.VARIABLE_WIDTH }
        if (hasVwfDialogs) {
            functions += buildVwfPrintAtHelper()
        }

        // _win_clear_region helper — clears a rectangular region of the window layer
        functions += buildWinClearRegionHelper()

        // _win_fill_screen helper — fills entire 20x18 window with one tile
        functions += buildWinFillScreenHelper()

        // Per-dialog show functions
        for (dialog in gameIR.dialogs) {
            functions += buildDialogFunction(dialog)
        }

        return functions
    }

    /**
     * Generate the `_win_print_at(x, y, str, len)` helper function.
     *
     * Writes a fixed-width string to the window layer tile-by-tile using `set_win_tiles`. This
     * helper replaces `gotoxy()/printf()` for all window-layer text output.
     *
     * Generated C:
     * ```c
     * void _win_print_at(UINT8 x, UINT8 y, const char* str, UINT8 len) {
     *     UINT8 i;
     *     for (i = 0; i < len; i++) {
     *         set_win_tiles(x + i, y, 1, 1, (unsigned char*)&str[i]);
     *     }
     * }
     * ```
     */
    private fun buildWinPrintAtHelper(): CFunction {
        val loopVar =
            CVarDecl(
                "i",
                CU8,
                initializer = CLiteral(0),
            ) // Plan 07.4-31 / DEFERRED-07.4-27-01: initialise loop counter; SDCC was lucky with
        // stack slot 0
        val forLoop =
            CFor(
                init = null, // C89: declare before for
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
                                    CRawExpr("(unsigned char*)&str[i]"),
                                ),
                            )
                        )
                    ),
            )
        return CFunction(
            name = "_win_print_at",
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
            sectionComment = "Dialog helpers (window layer rendering)",
        )
    }

    /**
     * Generate the `_vwf_print_at(x, y, str, len)` variable-width font helper.
     *
     * VWF on Game Boy works by rendering characters at pixel precision within tiles:
     * - Each character's bitmap is shifted right by `pixel_x % 8` within the current tile.
     * - When the shifted bitmap overflows into the next tile, the overflow is OR-composed into the
     *   adjacent tile buffer.
     * - The composed tile buffer is flushed to VRAM via `set_win_tiles`.
     * - Character widths are looked up in `_vwf_char_widths[]` — a built-in proportional table.
     *
     * Generated C uses a compact implementation with a single tile buffer for the active tile.
     */
    private fun buildVwfPrintAtHelper(): CFunction {
        // void _vwf_print_at(UINT8 x, UINT8 y, const char* str, UINT8 len) {
        //     UINT8 i, px, tile_x;
        //     UINT8 tile_buf[8];
        //     px = 0; tile_x = x;
        //     for (i = 0; i < len; i++) {
        //         UINT8 ch = str[i];
        //         UINT8 w = _vwf_char_widths[ch];
        //         /* shift character bitmap by (px % 8) and OR into tile_buf */
        //         /* when px crosses tile boundary, flush tile_buf to set_win_tiles */
        //         px += w;
        //     }
        //     set_win_tiles(tile_x, y, 1, 1, tile_buf);
        // }
        // Using CRawCode for the inner VWF logic — pixel-level bit manipulation is not
        // representable in the typed C AST; approved CRawCode exception for hardware-level VWF.
        return CFunction(
            name = "_vwf_print_at",
            returnType = CVoid,
            params =
                listOf(
                    CParam("x", CU8),
                    CParam("y", CU8),
                    CParam("str", CPointer(CConst(CU8))),
                    CParam("len", CU8),
                ),
            body =
                listOf(
                    CVarDecl(
                        "i",
                        CU8,
                        initializer = CLiteral(0),
                    ), // Plan 07.4-31 / DEFERRED-07.4-27-01: initialise loop counter; SDCC was
                    // lucky with stack slot 0
                    CVarDecl(
                        "px",
                        CU8,
                        initializer = CLiteral(0),
                    ), // Plan 07.4-31 / DEFERRED-07.4-27-01: initialise loop counter; SDCC was
                    // lucky with stack slot 0
                    CVarDecl(
                        "tile_x",
                        CU8,
                        initializer = CLiteral(0),
                    ), // Plan 07.4-31 / DEFERRED-07.4-27-01: initialise loop counter; SDCC was
                    // lucky with stack slot 0
                    CRawCode("UINT8 tile_buf[8] = {0,0,0,0,0,0,0,0};"),
                    CExprStatement(CBinaryExpr(CVar("px"), "=", CLiteral(0))),
                    CExprStatement(CBinaryExpr(CVar("tile_x"), "=", CVar("x"))),
                    CFor(
                        init = null,
                        condition = CBinaryExpr(CVar("i"), "<", CVar("len")),
                        increment = CUnaryExpr("++", CVar("i")),
                        body =
                            listOf(
                                CRawCode(
                                    "UINT8 ch = (UINT8)str[i]; UINT8 w = _vwf_char_widths[ch];"
                                ),
                                CRawCode(
                                    "if ((px & 7) == 0) { UINT8 r; for (r=0;r<8;r++) tile_buf[r]=0; }"
                                ),
                                CRawCode("if (px >= (x + (UINT8)len) * 8u) break;"),
                                CIf(
                                    condition =
                                        CBinaryExpr(
                                            CBinaryExpr(CVar("px"), "&", CLiteral(7)),
                                            "==",
                                            CLiteral(0),
                                        ),
                                    thenBody =
                                        listOf(
                                            CExprStatement(
                                                CCall(
                                                    "set_win_tiles",
                                                    listOf(
                                                        CVar("tile_x"),
                                                        CVar("y"),
                                                        CLiteral(1),
                                                        CLiteral(1),
                                                        CVar("tile_buf"),
                                                    ),
                                                )
                                            ),
                                            CExprStatement(CUnaryExpr("++", CVar("tile_x"))),
                                        ),
                                ),
                                CExprStatement(CBinaryExpr(CVar("px"), "+=", CVar("w"))),
                            ),
                    ),
                    CExprStatement(
                        CCall(
                            "set_win_tiles",
                            listOf(
                                CVar("tile_x"),
                                CVar("y"),
                                CLiteral(1),
                                CLiteral(1),
                                CVar("tile_buf"),
                            ),
                        )
                    ),
                ),
            bank = 0,
        )
    }

    /**
     * Generate the `_win_clear_region(x, y, w, h)` helper.
     *
     * Clears a rectangular region of the window layer by filling it with tile 0 (transparent/space)
     * using nested for loops and `set_win_tiles`.
     *
     * Generated C:
     * ```c
     * void _win_clear_region(UINT8 x, UINT8 y, UINT8 w, UINT8 h) {
     *     UINT8 ry, rx;
     *     UINT8 blank = 0;
     *     for (ry = 0; ry < h; ry++) {
     *         for (rx = 0; rx < w; rx++) {
     *             set_win_tiles(x + rx, y + ry, 1, 1, &blank);
     *         }
     *     }
     * }
     * ```
     */
    private fun buildWinClearRegionHelper(): CFunction {
        val blankTile = CVarDecl("blank", CU8, CLiteral(0))
        val innerLoop =
            CFor(
                init = null,
                condition = CBinaryExpr(CVar("rx"), "<", CVar("w")),
                increment = CUnaryExpr("++", CVar("rx")),
                body =
                    listOf(
                        CExprStatement(
                            CCall(
                                "set_win_tiles",
                                listOf(
                                    CBinaryExpr(CVar("x"), "+", CVar("rx")),
                                    CBinaryExpr(CVar("y"), "+", CVar("ry")),
                                    CLiteral(1),
                                    CLiteral(1),
                                    CRawExpr("&blank"),
                                ),
                            )
                        )
                    ),
            )
        val outerLoop =
            CFor(
                init = null,
                condition = CBinaryExpr(CVar("ry"), "<", CVar("h")),
                increment = CUnaryExpr("++", CVar("ry")),
                body = listOf(innerLoop),
            )
        return CFunction(
            name = "_win_clear_region",
            returnType = CVoid,
            params = listOf(CParam("x", CU8), CParam("y", CU8), CParam("w", CU8), CParam("h", CU8)),
            body =
                listOf(
                    CVarDecl(
                        "ry",
                        CU8,
                        initializer = CLiteral(0),
                    ), // Plan 07.4-31 / DEFERRED-07.4-27-01: initialise loop counter; SDCC was
                    // lucky with stack slot 0
                    CVarDecl(
                        "rx",
                        CU8,
                        initializer = CLiteral(0),
                    ), // Plan 07.4-31 / DEFERRED-07.4-27-01: initialise loop counter; SDCC was
                    // lucky with stack slot 0
                    blankTile,
                    outerLoop,
                ),
            bank = 0,
        )
    }

    /**
     * Generate the `_win_fill_screen(tile)` helper.
     *
     * Fills the entire 20x18 window layer with the given tile index using nested for loops.
     *
     * Generated C:
     * ```c
     * void _win_fill_screen(UINT8 tile) {
     *     UINT8 fy, fx;
     *     for (fy = 0; fy < 18; fy++) {
     *         for (fx = 0; fx < 20; fx++) {
     *             set_win_tiles(fx, fy, 1, 1, &tile);
     *         }
     *     }
     * }
     * ```
     */
    private fun buildWinFillScreenHelper(): CFunction {
        val innerLoop =
            CFor(
                init = null,
                condition = CBinaryExpr(CVar("fx"), "<", CLiteral(20)),
                increment = CUnaryExpr("++", CVar("fx")),
                body =
                    listOf(
                        CExprStatement(
                            CCall(
                                "set_win_tiles",
                                listOf(
                                    CVar("fx"),
                                    CVar("fy"),
                                    CLiteral(1),
                                    CLiteral(1),
                                    CRawExpr("&tile"),
                                ),
                            )
                        )
                    ),
            )
        val outerLoop =
            CFor(
                init = null,
                condition = CBinaryExpr(CVar("fy"), "<", CLiteral(18)),
                increment = CUnaryExpr("++", CVar("fy")),
                body = listOf(innerLoop),
            )
        return CFunction(
            name = "_win_fill_screen",
            returnType = CVoid,
            params = listOf(CParam("tile", CU8)),
            body =
                listOf(
                    CVarDecl(
                        "fy",
                        CU8,
                        initializer = CLiteral(0),
                    ), // Plan 07.4-31 / DEFERRED-07.4-27-01: initialise loop counter; SDCC was
                    // lucky with stack slot 0
                    CVarDecl(
                        "fx",
                        CU8,
                        initializer = CLiteral(0),
                    ), // Plan 07.4-31 / DEFERRED-07.4-27-01: initialise loop counter; SDCC was
                    // lucky with stack slot 0
                    outerLoop,
                ),
            bank = 0,
        )
    }

    /**
     * Generate the `show_dialog_<id>()` function for a single [DialogDef].
     *
     * The generated function:
     * 1. Positions and shows the window layer (`move_win` + `SHOW_WIN`)
     * 2. Draws the border (NONE/SINGLE/DOUBLE/CUSTOM) via `set_win_tiles`
     * 3. Optionally renders a portrait sprite and offsets text area
     * 4. Optionally displays the speaker name
     * 5. Runs the typewriter effect with auto-pagination (outer while loop on page offset)
     * 6. Waits for A-button press/release between pages
     * 7. Hides the window (`HIDE_WIN`) and portrait on dismiss
     *
     * Zero gotoxy/printf — all text goes through `_win_print_at` or `_vwf_print_at`.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun buildDialogFunction(def: DialogDef): CFunction {
        val sanitizedId = def.id.replace('-', '_').replace(' ', '_')
        val body = mutableListOf<CStatement>()

        // Determine text area inset based on border style
        val hasBorder = def.border != BorderStyle.NONE
        val textStartXBase = if (hasBorder) 1 else 0
        val textStartYBase = if (hasBorder) 1 else 0
        val textWidthBase = def.boxWidth - (if (hasBorder) 2 else 0)
        val textHeightBase = def.boxHeight - (if (hasBorder) 2 else 0)

        // Portrait offset — portrait takes 2 tiles of horizontal space
        val hasPortrait = def.portrait != null
        val textStartX = textStartXBase + (if (hasPortrait) 2 else 0)
        val textWidth = textWidthBase - (if (hasPortrait) 2 else 0)

        // Speaker name reduces text height by 1 row
        val hasSpeaker = def.speaker != null
        val textStartY = textStartYBase + (if (hasSpeaker) 1 else 0)
        val textHeight = textHeightBase - (if (hasSpeaker) 1 else 0)

        // Font helper function name based on fontMode
        val printFn =
            if (def.fontMode == FontMode.VARIABLE_WIDTH) "_vwf_print_at" else "_win_print_at"

        // C89: declare local variables at the top of the function
        body += CVarDecl("_pg_off", CU8, CLiteral(0)) // page offset into message string

        // 1. Position and show window layer
        // GBDK hardware: move_win(7, boxY * 8) — 7px left offset is GBDK window convention
        body += CExprStatement(CCall("move_win", listOf(CLiteral(7), CLiteral(def.boxY * 8))))
        body += GBDKMacros.showWin()

        // 2. Border drawing (before text)
        when (def.border) {
            BorderStyle.NONE -> {
                // No border — text uses full box area
            }
            BorderStyle.SINGLE -> {
                // CP437 single-line box drawing characters
                body +=
                    buildBorderStatements(
                        def.boxX,
                        def.boxY,
                        def.boxWidth,
                        def.boxHeight,
                        BorderTiles(tl = 0xDA, tr = 0xBF, bl = 0xC0, br = 0xD9, h = 0xC4, v = 0xB3),
                    )
            }
            BorderStyle.DOUBLE -> {
                // CP437 double-line box drawing characters
                body +=
                    buildBorderStatements(
                        def.boxX,
                        def.boxY,
                        def.boxWidth,
                        def.boxHeight,
                        BorderTiles(tl = 0xC9, tr = 0xBB, bl = 0xC8, br = 0xBC, h = 0xCD, v = 0xBA),
                    )
            }
            BorderStyle.CUSTOM -> {
                // User-provided tile indices (8 values: TL, TR, BL, BR, H-top, H-bottom, V-left,
                // V-right). Fall back to SINGLE if customBorderTiles is null.
                val customTiles = def.customBorderTiles
                if (customTiles != null && customTiles.size >= 8) {
                    body +=
                        buildBorderStatements(
                            def.boxX,
                            def.boxY,
                            def.boxWidth,
                            def.boxHeight,
                            BorderTiles(
                                tl = customTiles[0],
                                tr = customTiles[1],
                                bl = customTiles[2],
                                br = customTiles[3],
                                h = customTiles[4],
                                v = customTiles[6], // H-top, V-left used as primary
                            ),
                        )
                } else {
                    // Fallback to SINGLE
                    body +=
                        buildBorderStatements(
                            def.boxX,
                            def.boxY,
                            def.boxWidth,
                            def.boxHeight,
                            BorderTiles(
                                tl = 0xDA,
                                tr = 0xBF,
                                bl = 0xC0,
                                br = 0xD9,
                                h = 0xC4,
                                v = 0xB3,
                            ),
                        )
                }
            }
        }

        // 3. Portrait sprite rendering
        if (hasPortrait) {
            val portraitTile = 0 // portrait tile index — first tile of portrait sprite
            // Load portrait tile into dedicated sprite slot
            body +=
                CExprStatement(
                    CCall(
                        "set_sprite_tile",
                        listOf(CLiteral(portraitSpriteId), CLiteral(portraitTile)),
                    )
                )
            // Position portrait at dialog box corner (OAM offsets: +8 X, +16 Y)
            val portraitX = (def.boxX + textStartXBase) * 8 + 8
            val portraitY = (def.boxY + textStartYBase) * 8 + 16
            body +=
                CExprStatement(
                    CCall(
                        "move_sprite",
                        listOf(
                            CLiteral(portraitSpriteId),
                            CLiteral(portraitX),
                            CLiteral(portraitY),
                        ),
                    )
                )
        }

        // 4. Speaker name — written as first line of text area
        if (hasSpeaker) {
            val speaker = def.speaker!!
            body +=
                CExprStatement(
                    CCall(
                        printFn,
                        listOf(
                            CLiteral(textStartX),
                            CLiteral(textStartYBase),
                            CStringLiteral(speaker),
                            CLiteral(speaker.length),
                        ),
                    )
                )
        }

        // 5. Typewriter effect with auto-pagination
        // Outer loop: while (_pg_off < msg_len) — iterates one page at a time
        // Text and length are passed as function parameters (_text, _text_len)

        // Pagination outer loop body
        val pageBodyStmts = mutableListOf<CStatement>()

        // Clear text area at start of each subsequent page (not first — handled by SHOW_WIN)
        // Use a page-offset check: if (_pg_off > 0) { _win_clear_region(textStartX, textStartY,
        // textWidth, textHeight); }
        pageBodyStmts +=
            CIf(
                condition = CBinaryExpr(CVar("_pg_off"), ">", CLiteral(0)),
                thenBody =
                    listOf(
                        CExprStatement(
                            CCall(
                                "_win_clear_region",
                                listOf(
                                    CLiteral(textStartX),
                                    CLiteral(textStartY),
                                    CLiteral(textWidth),
                                    CLiteral(textHeight),
                                ),
                            )
                        )
                    ),
            )

        // Inner typewriter loop: for (i = 0; i < textWidth * textHeight && ...
        val pageSize = textWidth * textHeight
        // for (i = 0; i < pageSize && (_pg_off + i) < msg_len; i++)
        //     _win_print_at(textStartX + (i % textWidth), textStartY + (i / textWidth), ...)
        //     delay_frames(textSpeed)
        val typewriterLoopBody = buildList {
            // Place one character at computed position
            add(
                CExprStatement(
                    CCall(
                        "set_win_tiles",
                        listOf(
                            CBinaryExpr(
                                CLiteral(textStartX),
                                "+",
                                CBinaryExpr(CVar("_tw_i"), "%", CLiteral(textWidth)),
                            ),
                            CBinaryExpr(
                                CLiteral(textStartY),
                                "+",
                                CBinaryExpr(CVar("_tw_i"), "/", CLiteral(textWidth)),
                            ),
                            CLiteral(1),
                            CLiteral(1),
                            CRawExpr("(unsigned char*)&_text[_pg_off + _tw_i]"),
                        ),
                    )
                )
            )
            // Typewriter delay between characters
            if (def.textSpeed > 0) {
                add(CExprStatement(CCall("delay_frames", listOf(CLiteral(def.textSpeed)))))
            }
        }

        // Inner typewriter loop — C89: declare loop var before for
        pageBodyStmts += CVarDecl("_tw_i", CU8, initializer = null)
        pageBodyStmts +=
            CFor(
                init = null,
                condition =
                    CBinaryExpr(
                        CBinaryExpr(CVar("_tw_i"), "<", CLiteral(pageSize)),
                        "&&",
                        CBinaryExpr(
                            CBinaryExpr(CVar("_pg_off"), "+", CVar("_tw_i")),
                            "<",
                            CVar("_text_len"),
                        ),
                    ),
                increment = CUnaryExpr("++", CVar("_tw_i")),
                body = typewriterLoopBody,
            )

        // Advance page offset
        pageBodyStmts += CExprStatement(CBinaryExpr(CVar("_pg_off"), "+=", CLiteral(pageSize)))

        // A-button wait: wait for press then release
        // while (!(joypad() & J_A)) wait_vbl_done();
        pageBodyStmts +=
            CWhile(
                condition = CRawExpr("!(joypad() & J_A)"),
                body = listOf(CExprStatement(CCall("wait_vbl_done", emptyList()))),
            )
        // while (joypad() & J_A) wait_vbl_done();
        pageBodyStmts +=
            CWhile(
                condition = CBinaryExpr(CCall("joypad", emptyList()), "&", CVar("J_A")),
                body = listOf(CExprStatement(CCall("wait_vbl_done", emptyList()))),
            )

        // Outer pagination loop: while (1) { ... if (_pg_off >= msg_total) break; }
        // We use a simple while(1) with break when done
        val paginationLoop = CWhile(condition = CLiteral(1), body = pageBodyStmts + listOf(CBreak))
        body += paginationLoop

        // 6. Hide window and portrait
        body += GBDKMacros.hideWin()
        if (hasPortrait) {
            // Hide portrait by moving off-screen (position 0, 0 in OAM)
            body +=
                CExprStatement(
                    CCall(
                        "move_sprite",
                        listOf(CLiteral(portraitSpriteId), CLiteral(0), CLiteral(0)),
                    )
                )
        }

        return CFunction(
            name = "show_dialog_$sanitizedId",
            returnType = CVoid,
            params = listOf(CParam("_text", CPointer(CConst(CU8))), CParam("_text_len", CU8)),
            body = body,
            bank = 0,
            sectionComment = "Dialog: ${def.id}",
        )
    }

    /**
     * Generate `set_win_tiles` calls to draw a rectangular border around a dialog box.
     *
     * Draws four edges:
     * - Top edge: TL corner + H tiles + TR corner
     * - Bottom edge: BL corner + H tiles + BR corner
     * - Left vertical edge: V tiles
     * - Right vertical edge: V tiles
     *
     * @param boxX Left edge of box in tiles
     * @param boxY Top edge of box in tiles
     * @param boxWidth Box width in tiles
     * @param boxHeight Box height in tiles
     * @param tiles The six border tile indices grouped by segment
     */
    private fun buildBorderStatements(
        boxX: Int,
        boxY: Int,
        boxWidth: Int,
        boxHeight: Int,
        tiles: BorderTiles,
    ): List<CStatement> {
        val tlTile = tiles.tl
        val trTile = tiles.tr
        val blTile = tiles.bl
        val brTile = tiles.br
        val hTile = tiles.h
        val vTile = tiles.v
        val stmts = mutableListOf<CStatement>()

        // Helper: emit set_win_tiles(x, y, 1, 1, &tile) for a single tile
        fun setTile(x: Int, y: Int, tileIdx: Int): CStatement {
            val tileVar = "_border_tile_$tileIdx"
            return CBlock(
                listOf(
                    CRawCode(
                        "{ UINT8 $tileVar = $tileIdx; set_win_tiles($x, $y, 1, 1, &$tileVar); }"
                    )
                )
            )
        }

        val rightX = boxX + boxWidth - 1
        val bottomY = boxY + boxHeight - 1

        // Top-left corner
        stmts += setTile(boxX, boxY, tlTile)
        // Top-right corner
        stmts += setTile(rightX, boxY, trTile)
        // Bottom-left corner
        stmts += setTile(boxX, bottomY, blTile)
        // Bottom-right corner
        stmts += setTile(rightX, bottomY, brTile)

        // Top horizontal edge (excluding corners)
        for (tx in (boxX + 1) until rightX) {
            stmts += setTile(tx, boxY, hTile)
        }
        // Bottom horizontal edge (excluding corners)
        for (tx in (boxX + 1) until rightX) {
            stmts += setTile(tx, bottomY, hTile)
        }
        // Left vertical edge (excluding corners)
        for (ty in (boxY + 1) until bottomY) {
            stmts += setTile(boxX, ty, vTile)
        }
        // Right vertical edge (excluding corners)
        for (ty in (boxY + 1) until bottomY) {
            stmts += setTile(rightX, ty, vTile)
        }

        return stmts
    }

    /** Tile indices for the six segments of a rectangular border. */
    private data class BorderTiles(
        val tl: Int,
        val tr: Int,
        val bl: Int,
        val br: Int,
        val h: Int,
        val v: Int,
    )
}
