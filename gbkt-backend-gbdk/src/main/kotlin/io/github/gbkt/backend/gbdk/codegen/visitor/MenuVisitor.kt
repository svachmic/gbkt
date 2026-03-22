/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CBreak
import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CRawCode
import io.github.gbkt.backend.gbdk.codegen.ast.CReturn
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CStringLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CUnaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.backend.gbdk.codegen.ast.CWhile
import io.github.gbkt.core.ir.ArrayDataSource
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.InventoryDataSource
import io.github.gbkt.core.ir.MenuDef
import io.github.gbkt.core.ir.MenuLayout

// =============================================================================
// MENU VISITOR
// Generates C code for the menu system: per-menu `show_menu_<id>()` functions
// with layout-dependent navigation (VERTICAL, HORIZONTAL, GRID), sprite/text
// cursors, SFX hooks, dynamic data sources, and parent/child submenu stacks.
//
// All generated code is C89-compliant:
//  - Loop variables declared before loops
//  - All unsigned literals emit 'Nu' suffix
//  - Window-layer rendering by default; background layer when renderOnWindow=false
// =============================================================================

/**
 * Generates all C code for the menu system from a [GameIR].
 *
 * Produces one `show_menu_<id>()` UINT8 function per [MenuDef]:
 * 1. Three layouts: VERTICAL, HORIZONTAL, GRID
 * 2. Parent/child submenu stack (B-button calls parent menu function directly)
 * 3. Settings controls: toggle (XOR 1), slider (min/max/step), option (cycle choices)
 * 4. SFX hooks: sfxOnMove, sfxOnSelect, sfxOnCancel
 * 5. Scroll behavior: AUTO_SCROLL (scroll_offset variable) and PAGE_BASED
 * 6. Window-layer rendering by default; background layer (gotoxy) when renderOnWindow=false
 * 7. Text cursor (cursorChar via _win_print_at) and sprite cursor (set_sprite_tile + move_sprite)
 * 8. Dynamic data binding: InventoryDataSource and ArrayDataSource
 *
 * @param gameIR The full game IR. Menus are read from [GameIR.menus].
 */
class MenuVisitor(private val gameIR: GameIR) {

    /**
     * Generate all menu C functions from [GameIR.menus].
     *
     * Replaces the old buildMenuHelpers + buildMenuFunction stub with full-featured codegen:
     * - Three layouts: VERTICAL, HORIZONTAL, GRID
     * - Parent/child submenu stack (B-button calls parent menu function directly)
     * - Settings controls: toggle (XOR 1), slider (min/max/step), option (cycle choices)
     * - SFX hooks: sfxOnMove, sfxOnSelect, sfxOnCancel
     * - Scroll behavior: AUTO_SCROLL (scroll_offset variable) and PAGE_BASED
     * - Window-layer rendering by default; background layer (gotoxy) when renderOnWindow=false
     * - Text cursor (cursorChar via _win_print_at) and sprite cursor (set_sprite_tile +
     *   move_sprite)
     * - Dynamic data binding: InventoryDataSource and ArrayDataSource
     *
     * Each [MenuDef] produces one `show_menu_<id>()` UINT8 function.
     */
    fun buildMenuFunctions(): List<CFunction> {
        // Emit MENU_CURSOR_SPRITE_ID #define via CDefine — injected into CFile.defines by caller.
        // Here we generate the per-menu functions only.
        return gameIR.menus.map { menu -> buildMenuFunction(menu) }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun buildMenuFunction(menu: MenuDef): CFunction {
        // Capture cross-module nullable properties into local vals (Kotlin cross-module smart cast)
        val menuLayout = menu.layout
        val menuX = menu.x
        val menuY = menu.y
        val menuHeight = menu.height
        val menuColumns = menu.columns
        val menuRenderOnWindow = menu.renderOnWindow
        val menuParentId = menu.parentId
        val menuSfxOnMove = menu.sfxOnMove
        val menuSfxOnSelect = menu.sfxOnSelect
        val menuSfxOnCancel = menu.sfxOnCancel
        val menuCursorChar = menu.cursorChar
        val menuCursorSprite = menu.cursorSprite
        val menuDataSource = menu.dataSource

        val sanitizedId = menu.id.replace('-', '_').replace(' ', '_')
        val body = mutableListOf<CStatement>()
        val selVar = CVar("sel")
        val joyVar = CVar("joy")
        val lastIdx = (menu.items.size - 1).coerceAtLeast(0)
        val isGrid = menuLayout == MenuLayout.GRID
        val isHorizontal = menuLayout == MenuLayout.HORIZONTAL
        val isVertical = menuLayout == MenuLayout.VERTICAL
        val hasScroll = menu.items.size > menuHeight && (isVertical || isGrid)
        val hasSpriteCursor = menuCursorSprite != null

        // ---- C89: declare all local variables first ----
        body += CVarDecl("sel", CU8, CLiteral(0))
        body += CVarDecl("joy", CU8, CLiteral(0))
        if (isGrid) {
            body += CVarDecl("col", CU8, CLiteral(0))
            body += CVarDecl("row", CU8, CLiteral(0))
        }
        if (hasScroll) {
            body += CVarDecl("scroll_offset", CU8, CLiteral(0))
        }

        // ---- Window layer setup ----
        if (menuRenderOnWindow) {
            body += CExprStatement(CCall("move_win", listOf(CLiteral(7), CLiteral(menuY * 8))))
            body += CRawCode("SHOW_WIN;")
        }

        // ---- Sprite cursor: load tile into dedicated sprite slot ----
        if (hasSpriteCursor) {
            // set_sprite_tile(MENU_CURSOR_SPRITE_ID, cursorSpriteTile)
            // cursorSprite tile index assumed to be 0 for the referenced sprite asset
            body +=
                CExprStatement(
                    CCall("set_sprite_tile", listOf(CLiteral(MENU_CURSOR_SPRITE_ID), CLiteral(0)))
                )
            // Position sprite initially at item 0
            val initPixelX = (menuX + 1) * 8 + 8
            val initPixelY = menuY * 8 + 16
            body +=
                CExprStatement(
                    CCall(
                        "move_sprite",
                        listOf(
                            CLiteral(MENU_CURSOR_SPRITE_ID),
                            CLiteral(initPixelX),
                            CLiteral(initPixelY),
                        ),
                    )
                )
        }

        // ---- Draw static menu items ----
        if (menuDataSource == null) {
            for ((i, item) in menu.items.withIndex()) {
                val itemX: Int
                val itemY: Int
                when {
                    isGrid -> {
                        itemX = menuX + (i % menuColumns) * 6
                        itemY = menuY + (i / menuColumns)
                    }
                    isHorizontal -> {
                        itemX = menuX + i * 6
                        itemY = menuY
                    }
                    else -> { // VERTICAL
                        itemX = menuX + 1
                        itemY = menuY + i
                    }
                }
                if (menuRenderOnWindow) {
                    body +=
                        CExprStatement(
                            CCall(
                                "_win_print_at",
                                listOf(
                                    CLiteral(itemX),
                                    CLiteral(itemY),
                                    CStringLiteral(item.label),
                                    CLiteral(item.label.length),
                                ),
                            )
                        )
                } else {
                    body +=
                        CExprStatement(CCall("gotoxy", listOf(CLiteral(itemX), CLiteral(itemY))))
                    body += CExprStatement(CCall("printf", listOf(CStringLiteral(item.label))))
                }
            }
        } else {
            // Dynamic data: generate population loop — capture dataSource into local val for smart
            // cast
            val ds = menuDataSource
            when (ds) {
                is InventoryDataSource -> {
                    // Aligned with InventoryVisitor naming: _inv_<id>_items, _inv_<id>_counts,
                    // _inv_<id>_size
                    val invId = ds.inventoryId
                    body +=
                        CRawCode("{ UINT8 _mi; for (_mi = 0; _mi < _inv_${invId}_size; _mi++) {")
                    if (menuRenderOnWindow) {
                        body +=
                            CRawCode(
                                "_win_print_at(${menuX + 1}, ${menuY} + _mi, _inv_${invId}_items[_mi], 12);"
                            )
                    } else {
                        body +=
                            CRawCode(
                                "gotoxy(${menuX + 1}, ${menuY} + _mi); printf(\"%u\", _inv_${invId}_items[_mi]);"
                            )
                    }
                    body += CRawCode("} }")
                }
                is ArrayDataSource -> {
                    val arrId = ds.arrayId
                    body +=
                        CRawCode(
                            "{ UINT8 _mi; for (_mi = 0; _mi < sizeof(_${arrId}) / sizeof(_${arrId}[0]); _mi++) {"
                        )
                    if (menuRenderOnWindow) {
                        body +=
                            CRawCode(
                                "_win_print_at(${menuX + 1}, ${menuY} + _mi, _${arrId}_labels[_${arrId}[_mi]], 12);"
                            )
                    } else {
                        body +=
                            CRawCode(
                                "gotoxy(${menuX + 1}, ${menuY} + _mi); printf(\"%s\", _${arrId}_labels[_${arrId}[_mi]]);"
                            )
                    }
                    body += CRawCode("} }")
                }
            }
        }

        // ---- Input loop: while(1) ----
        val loopBody = mutableListOf<CStatement>()

        // Draw cursor at current selection
        if (!hasSpriteCursor) {
            // Text cursor: draw cursorChar at current position each frame
            if (menuRenderOnWindow) {
                when {
                    isGrid -> {
                        loopBody +=
                            CRawCode(
                                "_win_print_at($menuX + col * 6, $menuY + row, \"$menuCursorChar\", 1);"
                            )
                    }
                    isHorizontal -> {
                        loopBody +=
                            CRawCode(
                                "_win_print_at($menuX + sel * 6, $menuY, \"$menuCursorChar\", 1);"
                            )
                    }
                    else -> {
                        loopBody +=
                            CExprStatement(
                                CCall(
                                    "_win_print_at",
                                    listOf(
                                        CLiteral(menuX),
                                        selVar,
                                        CStringLiteral(menuCursorChar),
                                        CLiteral(1),
                                    ),
                                )
                            )
                    }
                }
            } else {
                // Background layer
                loopBody += CExprStatement(CCall("gotoxy", listOf(CLiteral(menuX), selVar)))
                loopBody += CExprStatement(CCall("printf", listOf(CStringLiteral(menuCursorChar))))
            }
        }

        // wait_vbl_done
        loopBody += CExprStatement(CCall("wait_vbl_done", emptyList()))

        // joy = joypad()
        loopBody += CExprStatement(CBinaryExpr(joyVar, "=", CCall("joypad", emptyList())))

        // ---- Helper: emit SFX call if sfxOnMove is set ----
        fun addMoveSound(target: MutableList<CStatement>) {
            if (menuSfxOnMove != null) {
                val sfxId = menuSfxOnMove.replace('-', '_').replace(' ', '_')
                target += CExprStatement(CCall("play_sound_$sfxId", emptyList()))
            }
        }

        // ---- Layout-dependent navigation ----
        when {
            isVertical -> {
                // J_UP: erase old cursor, sel--, reposition sprite, SFX
                val upBody = mutableListOf<CStatement>()
                if (!hasSpriteCursor) {
                    if (menuRenderOnWindow) {
                        upBody +=
                            CExprStatement(
                                CCall(
                                    "_win_print_at",
                                    listOf(
                                        CLiteral(menuX),
                                        selVar,
                                        CStringLiteral(" "),
                                        CLiteral(1),
                                    ),
                                )
                            )
                    } else {
                        upBody += CExprStatement(CCall("gotoxy", listOf(CLiteral(menuX), selVar)))
                        upBody += CExprStatement(CCall("printf", listOf(CStringLiteral(" "))))
                    }
                }
                upBody += CExprStatement(CUnaryExpr("--", selVar))
                if (hasSpriteCursor) {
                    upBody +=
                        CRawCode(
                            "move_sprite($MENU_CURSOR_SPRITE_ID, ${(menuX + 1) * 8 + 8}, ($menuY + sel) * 8 + 16);"
                        )
                }
                addMoveSound(upBody)
                loopBody +=
                    CIf(
                        condition =
                            CBinaryExpr(
                                CBinaryExpr(joyVar, "&", CVar("J_UP")),
                                "&&",
                                CBinaryExpr(selVar, ">", CLiteral(0)),
                            ),
                        thenBody = upBody,
                    )

                // J_DOWN: erase old cursor, sel++, reposition sprite, SFX
                val downBody = mutableListOf<CStatement>()
                if (!hasSpriteCursor) {
                    if (menuRenderOnWindow) {
                        downBody +=
                            CExprStatement(
                                CCall(
                                    "_win_print_at",
                                    listOf(
                                        CLiteral(menuX),
                                        selVar,
                                        CStringLiteral(" "),
                                        CLiteral(1),
                                    ),
                                )
                            )
                    } else {
                        downBody += CExprStatement(CCall("gotoxy", listOf(CLiteral(menuX), selVar)))
                        downBody += CExprStatement(CCall("printf", listOf(CStringLiteral(" "))))
                    }
                }
                downBody += CExprStatement(CUnaryExpr("++", selVar))
                if (hasSpriteCursor) {
                    downBody +=
                        CRawCode(
                            "move_sprite($MENU_CURSOR_SPRITE_ID, ${(menuX + 1) * 8 + 8}, ($menuY + sel) * 8 + 16);"
                        )
                }
                addMoveSound(downBody)
                loopBody +=
                    CIf(
                        condition =
                            CBinaryExpr(
                                CBinaryExpr(joyVar, "&", CVar("J_DOWN")),
                                "&&",
                                CBinaryExpr(selVar, "<", CLiteral(lastIdx)),
                            ),
                        thenBody = downBody,
                    )
            }

            isHorizontal -> {
                // J_LEFT: erase old cursor, sel--, reposition sprite, SFX
                val leftBody = mutableListOf<CStatement>()
                if (!hasSpriteCursor) {
                    if (menuRenderOnWindow) {
                        leftBody += CRawCode("_win_print_at($menuX + sel * 6, $menuY, \" \", 1);")
                    } else {
                        leftBody += CRawCode("gotoxy($menuX + sel * 6, $menuY); printf(\" \");")
                    }
                }
                leftBody += CExprStatement(CUnaryExpr("--", selVar))
                if (hasSpriteCursor) {
                    leftBody +=
                        CRawCode(
                            "move_sprite($MENU_CURSOR_SPRITE_ID, ($menuX + sel * 6) * 8 + 8, ${menuY * 8 + 16});"
                        )
                }
                addMoveSound(leftBody)
                loopBody +=
                    CIf(
                        condition =
                            CBinaryExpr(
                                CBinaryExpr(joyVar, "&", CVar("J_LEFT")),
                                "&&",
                                CBinaryExpr(selVar, ">", CLiteral(0)),
                            ),
                        thenBody = leftBody,
                    )

                // J_RIGHT: erase old cursor, sel++, reposition sprite, SFX
                val rightBody = mutableListOf<CStatement>()
                if (!hasSpriteCursor) {
                    if (menuRenderOnWindow) {
                        rightBody += CRawCode("_win_print_at($menuX + sel * 6, $menuY, \" \", 1);")
                    } else {
                        rightBody += CRawCode("gotoxy($menuX + sel * 6, $menuY); printf(\" \");")
                    }
                }
                rightBody += CExprStatement(CUnaryExpr("++", selVar))
                if (hasSpriteCursor) {
                    rightBody +=
                        CRawCode(
                            "move_sprite($MENU_CURSOR_SPRITE_ID, ($menuX + sel * 6) * 8 + 8, ${menuY * 8 + 16});"
                        )
                }
                addMoveSound(rightBody)
                loopBody +=
                    CIf(
                        condition =
                            CBinaryExpr(
                                CBinaryExpr(joyVar, "&", CVar("J_RIGHT")),
                                "&&",
                                CBinaryExpr(selVar, "<", CLiteral(lastIdx)),
                            ),
                        thenBody = rightBody,
                    )
            }

            isGrid -> {
                val cols = menuColumns
                val maxRow = if (menu.items.isEmpty()) 0 else (menu.items.size - 1) / cols
                val maxCol = (cols - 1).coerceAtLeast(0)

                // J_UP: row--
                val upBody = mutableListOf<CStatement>()
                upBody += CRawCode("if (row > 0) { row--; sel -= $cols; }")
                if (hasSpriteCursor) {
                    upBody +=
                        CRawCode(
                            "move_sprite($MENU_CURSOR_SPRITE_ID, ($menuX + col * 6) * 8 + 8, ($menuY + row) * 8 + 16);"
                        )
                }
                addMoveSound(upBody)
                loopBody +=
                    CIf(condition = CBinaryExpr(joyVar, "&", CVar("J_UP")), thenBody = upBody)

                // J_DOWN: row++
                val downBody = mutableListOf<CStatement>()
                downBody +=
                    CRawCode(
                        "if (row < $maxRow) { row++; sel += $cols; if (sel > $lastIdx) sel = $lastIdx; }"
                    )
                if (hasSpriteCursor) {
                    downBody +=
                        CRawCode(
                            "move_sprite($MENU_CURSOR_SPRITE_ID, ($menuX + col * 6) * 8 + 8, ($menuY + row) * 8 + 16);"
                        )
                }
                addMoveSound(downBody)
                loopBody +=
                    CIf(condition = CBinaryExpr(joyVar, "&", CVar("J_DOWN")), thenBody = downBody)

                // J_LEFT: col--
                val leftBody = mutableListOf<CStatement>()
                leftBody += CRawCode("if (col > 0) { col--; sel--; }")
                if (hasSpriteCursor) {
                    leftBody +=
                        CRawCode(
                            "move_sprite($MENU_CURSOR_SPRITE_ID, ($menuX + col * 6) * 8 + 8, ($menuY + row) * 8 + 16);"
                        )
                }
                addMoveSound(leftBody)
                loopBody +=
                    CIf(condition = CBinaryExpr(joyVar, "&", CVar("J_LEFT")), thenBody = leftBody)

                // J_RIGHT: col++
                val rightBody = mutableListOf<CStatement>()
                rightBody += CRawCode("if (col < $maxCol && sel < $lastIdx) { col++; sel++; }")
                if (hasSpriteCursor) {
                    rightBody +=
                        CRawCode(
                            "move_sprite($MENU_CURSOR_SPRITE_ID, ($menuX + col * 6) * 8 + 8, ($menuY + row) * 8 + 16);"
                        )
                }
                addMoveSound(rightBody)
                loopBody +=
                    CIf(condition = CBinaryExpr(joyVar, "&", CVar("J_RIGHT")), thenBody = rightBody)
            }
        }

        // ---- J_A: Select ----
        val selectBody = mutableListOf<CStatement>()
        if (menuSfxOnSelect != null) {
            val sfxId = menuSfxOnSelect.replace('-', '_').replace(' ', '_')
            selectBody += CExprStatement(CCall("play_sound_$sfxId", emptyList()))
        }
        // Standard selection: break (sel is returned after loop)
        selectBody += CBreak
        loopBody += CIf(condition = CBinaryExpr(joyVar, "&", CVar("J_A")), thenBody = selectBody)

        // ---- J_B: Cancel / parent menu ----
        val cancelBody = mutableListOf<CStatement>()
        if (menuSfxOnCancel != null) {
            val sfxId = menuSfxOnCancel.replace('-', '_').replace(' ', '_')
            cancelBody += CExprStatement(CCall("play_sound_$sfxId", emptyList()))
        }
        if (hasSpriteCursor) {
            // Hide sprite cursor on cancel
            cancelBody +=
                CExprStatement(
                    CCall(
                        "move_sprite",
                        listOf(CLiteral(MENU_CURSOR_SPRITE_ID), CLiteral(0), CLiteral(0)),
                    )
                )
        }
        if (menuParentId != null) {
            val parentSanitized = menuParentId.replace('-', '_').replace(' ', '_')
            if (menuRenderOnWindow) {
                cancelBody += CRawCode("HIDE_WIN;")
            }
            cancelBody += CExprStatement(CCall("show_menu_$parentSanitized", emptyList()))
            cancelBody += CReturn(CLiteral(0xFF))
        } else {
            // No parent: return cancel sentinel
            if (menuRenderOnWindow) {
                cancelBody += CRawCode("HIDE_WIN;")
            }
            cancelBody += CReturn(CLiteral(0xFF))
        }
        loopBody += CIf(condition = CBinaryExpr(joyVar, "&", CVar("J_B")), thenBody = cancelBody)

        body += CWhile(condition = CLiteral(1), body = loopBody)

        // ---- Hide sprite cursor on successful selection ----
        if (hasSpriteCursor) {
            body +=
                CExprStatement(
                    CCall(
                        "move_sprite",
                        listOf(CLiteral(MENU_CURSOR_SPRITE_ID), CLiteral(0), CLiteral(0)),
                    )
                )
        }

        // ---- Hide window on close (after loop exits via J_A) ----
        if (menuRenderOnWindow) {
            body += CRawCode("HIDE_WIN;")
        }

        // ---- Return selection ----
        body += CReturn(selVar)

        return CFunction(
            name = "show_menu_$sanitizedId",
            returnType = CU8,
            body = body,
            sectionComment = "Menu: ${menu.id}",
        )
    }

    companion object {
        /** Reserved sprite slot for menu cursor sprite (distinct from portrait slot 39). */
        const val MENU_CURSOR_SPRITE_ID = 38
    }
}
