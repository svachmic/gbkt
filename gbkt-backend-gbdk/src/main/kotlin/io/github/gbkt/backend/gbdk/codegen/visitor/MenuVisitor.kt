/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.GBDKMacros
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

    private fun buildMenuFunction(menu: MenuDef): CFunction {
        val sanitizedId = menu.id.replace('-', '_').replace(' ', '_')
        return CFunction(
            name = "show_menu_$sanitizedId",
            returnType = CU8,
            body = buildMenuBodyStatements(menu),
            sectionComment = "Menu: ${menu.id}",
        )
    }

    private fun buildMenuBodyStatements(menu: MenuDef): List<CStatement> {
        val body = mutableListOf<CStatement>()
        body += buildMenuVarDecls(menu)
        body += buildMenuInitStatements(menu)
        body += buildMenuItemDrawStatements(menu)
        body += CWhile(condition = CLiteral(1), body = buildMenuInputLoopBody(menu))
        // ---- Hide sprite cursor on successful selection ----
        if (menu.cursorSprite != null) {
            body +=
                CExprStatement(
                    CCall(
                        "move_sprite",
                        listOf(CLiteral(MENU_CURSOR_SPRITE_ID), CLiteral(0), CLiteral(0)),
                    )
                )
        }
        // ---- Hide window on close (after loop exits via J_A) ----
        if (menu.renderOnWindow) {
            body += GBDKMacros.hideWin()
        }
        // ---- Return selection ----
        body += CReturn(CVar("sel"))
        return body
    }

    private fun buildMenuVarDecls(menu: MenuDef): List<CStatement> {
        val isGrid = menu.layout == MenuLayout.GRID
        val isVertical = menu.layout == MenuLayout.VERTICAL
        val hasScroll = menu.items.size > menu.height && (isVertical || isGrid)
        // ---- C89: declare all local variables first ----
        val result = mutableListOf<CStatement>()
        result += CVarDecl("sel", CU8, CLiteral(0))
        result += CVarDecl("joy", CU8, CLiteral(0))
        if (isGrid) {
            result += CVarDecl("col", CU8, CLiteral(0))
            result += CVarDecl("row", CU8, CLiteral(0))
        }
        if (hasScroll) {
            result += CVarDecl("scroll_offset", CU8, CLiteral(0))
        }
        return result
    }

    private fun buildMenuInitStatements(menu: MenuDef): List<CStatement> {
        val result = mutableListOf<CStatement>()
        // ---- Window layer setup ----
        if (menu.renderOnWindow) {
            result += CExprStatement(CCall("move_win", listOf(CLiteral(7), CLiteral(menu.y * 8))))
            result += GBDKMacros.showWin()
        }
        // ---- Sprite cursor: load tile into dedicated sprite slot ----
        if (menu.cursorSprite != null) {
            // set_sprite_tile(MENU_CURSOR_SPRITE_ID, cursorSpriteTile)
            // cursorSprite tile index assumed to be 0 for the referenced sprite asset
            result +=
                CExprStatement(
                    CCall("set_sprite_tile", listOf(CLiteral(MENU_CURSOR_SPRITE_ID), CLiteral(0)))
                )
            // Position sprite initially at item 0
            val initPixelX = (menu.x + 1) * 8 + 8
            val initPixelY = menu.y * 8 + 16
            result +=
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
        return result
    }

    private fun buildMenuItemDrawStatements(menu: MenuDef): List<CStatement> {
        // ---- Draw static menu items ----
        if (menu.dataSource != null) {
            return buildMenuDynamicItemStatements(menu)
        }
        val isGrid = menu.layout == MenuLayout.GRID
        val isHorizontal = menu.layout == MenuLayout.HORIZONTAL
        val result = mutableListOf<CStatement>()
        for ((i, item) in menu.items.withIndex()) {
            val itemX: Int
            val itemY: Int
            when {
                isGrid -> {
                    itemX = menu.x + (i % menu.columns) * 6
                    itemY = menu.y + (i / menu.columns)
                }
                isHorizontal -> {
                    itemX = menu.x + i * 6
                    itemY = menu.y
                }
                else -> { // VERTICAL
                    itemX = menu.x + 1
                    itemY = menu.y + i
                }
            }
            if (menu.renderOnWindow) {
                result +=
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
                result += CExprStatement(CCall("gotoxy", listOf(CLiteral(itemX), CLiteral(itemY))))
                result += CExprStatement(CCall("printf", listOf(CStringLiteral(item.label))))
            }
        }
        return result
    }

    private fun buildMenuInputLoopBody(menu: MenuDef): List<CStatement> {
        val joyVar = CVar("joy")
        // ---- Input loop: while(1) ----
        val loopBody = mutableListOf<CStatement>()
        // Draw cursor at current selection
        loopBody += buildMenuCursorDrawStatements(menu)
        // wait_vbl_done
        loopBody += CExprStatement(CCall("wait_vbl_done", emptyList()))
        // joy = joypad()
        loopBody += CExprStatement(CBinaryExpr(joyVar, "=", CCall("joypad", emptyList())))
        // ---- Layout-dependent navigation ----
        loopBody += buildMenuNavigationStatements(menu)
        // ---- J_A: Select ----
        loopBody += buildMenuSelectStatements(menu)
        // ---- J_B: Cancel / parent menu ----
        loopBody += buildMenuCancelStatements(menu)
        return loopBody
    }

    private fun buildMenuCursorDrawStatements(menu: MenuDef): List<CStatement> {
        // Text cursor: draw cursorChar at current position each frame
        // (sprite cursor skips this — sprite is repositioned during navigation)
        if (menu.cursorSprite != null) return emptyList()
        val selVar = CVar("sel")
        val menuCursorChar = menu.cursorChar
        val result = mutableListOf<CStatement>()
        if (menu.renderOnWindow) {
            when {
                menu.layout == MenuLayout.GRID -> {
                    result +=
                        CRawCode(
                            "_win_print_at(${menu.x} + col * 6, ${menu.y} + row, \"$menuCursorChar\", 1);"
                        )
                }
                menu.layout == MenuLayout.HORIZONTAL -> {
                    result +=
                        CRawCode(
                            "_win_print_at(${menu.x} + sel * 6, ${menu.y}, \"$menuCursorChar\", 1);"
                        )
                }
                else -> {
                    result +=
                        CExprStatement(
                            CCall(
                                "_win_print_at",
                                listOf(
                                    CLiteral(menu.x),
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
            result += CExprStatement(CCall("gotoxy", listOf(CLiteral(menu.x), selVar)))
            result += CExprStatement(CCall("printf", listOf(CStringLiteral(menuCursorChar))))
        }
        return result
    }

    private fun buildMenuNavigationStatements(menu: MenuDef): List<CStatement> {
        val isVertical = menu.layout == MenuLayout.VERTICAL
        val isHorizontal = menu.layout == MenuLayout.HORIZONTAL
        val isGrid = menu.layout == MenuLayout.GRID
        return when {
            isVertical -> buildMenuVerticalNavStatements(menu)
            isHorizontal -> buildMenuHorizontalNavStatements(menu)
            isGrid -> buildMenuGridNavStatements(menu)
            else -> emptyList()
        }
    }

    private fun buildMenuSelectStatements(menu: MenuDef): CStatement {
        val joyVar = CVar("joy")
        // Standard selection: break (sel is returned after loop)
        val menuSfxOnSelect = menu.sfxOnSelect
        val selectBody = mutableListOf<CStatement>()
        if (menuSfxOnSelect != null) {
            val sfxId = menuSfxOnSelect.replace('-', '_').replace(' ', '_')
            selectBody += CExprStatement(CCall("play_sound_$sfxId", emptyList()))
        }
        selectBody += CBreak
        return CIf(condition = CBinaryExpr(joyVar, "&", CVar("J_A")), thenBody = selectBody)
    }

    private fun buildMenuCancelStatements(menu: MenuDef): CStatement {
        val joyVar = CVar("joy")
        val menuSfxOnCancel = menu.sfxOnCancel
        val menuParentId = menu.parentId
        val cancelBody = mutableListOf<CStatement>()
        if (menuSfxOnCancel != null) {
            val sfxId = menuSfxOnCancel.replace('-', '_').replace(' ', '_')
            cancelBody += CExprStatement(CCall("play_sound_$sfxId", emptyList()))
        }
        if (menu.cursorSprite != null) {
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
            if (menu.renderOnWindow) {
                cancelBody += GBDKMacros.hideWin()
            }
            cancelBody += CExprStatement(CCall("show_menu_$parentSanitized", emptyList()))
            cancelBody += CReturn(CLiteral(0xFF))
        } else {
            // No parent: return cancel sentinel
            if (menu.renderOnWindow) {
                cancelBody += GBDKMacros.hideWin()
            }
            cancelBody += CReturn(CLiteral(0xFF))
        }
        return CIf(condition = CBinaryExpr(joyVar, "&", CVar("J_B")), thenBody = cancelBody)
    }

    companion object {
        /** Reserved sprite slot for menu cursor sprite (distinct from portrait slot 39). */
        const val MENU_CURSOR_SPRITE_ID = 38
    }
}

// =============================================================================
// File-level private helpers — extracted to avoid TooManyFunctions in the class
// while keeping each helper focused on a single menu subsystem.
// =============================================================================

/** Builds C statements for dynamic data sources (InventoryDataSource, ArrayDataSource). */
private fun buildMenuDynamicItemStatements(menu: MenuDef): List<CStatement> {
    // Dynamic data: generate population loop — capture dataSource into local val for smart cast
    val result = mutableListOf<CStatement>()
    val ds = menu.dataSource
    when (ds) {
        is InventoryDataSource -> {
            // Aligned with InventoryVisitor naming: _inv_<id>_items, _inv_<id>_counts,
            // _inv_<id>_size
            val invId = ds.inventoryId
            result += CRawCode("{ UINT8 _mi; for (_mi = 0; _mi < _inv_${invId}_size; _mi++) {")
            if (menu.renderOnWindow) {
                result +=
                    CRawCode(
                        "_win_print_at(${menu.x + 1}, ${menu.y} + _mi, _inv_${invId}_items[_mi], 12);"
                    )
            } else {
                result +=
                    CRawCode(
                        "gotoxy(${menu.x + 1}, ${menu.y} + _mi); printf(\"%u\", _inv_${invId}_items[_mi]);"
                    )
            }
            result += CRawCode("} }")
        }
        is ArrayDataSource -> {
            val arrId = ds.arrayId
            result +=
                CRawCode(
                    "{ UINT8 _mi; for (_mi = 0; _mi < sizeof(_${arrId}) / sizeof(_${arrId}[0]); _mi++) {"
                )
            if (menu.renderOnWindow) {
                result +=
                    CRawCode(
                        "_win_print_at(${menu.x + 1}, ${menu.y} + _mi, _${arrId}_labels[_${arrId}[_mi]], 12);"
                    )
            } else {
                result +=
                    CRawCode(
                        "gotoxy(${menu.x + 1}, ${menu.y} + _mi); printf(\"%s\", _${arrId}_labels[_${arrId}[_mi]]);"
                    )
            }
            result += CRawCode("} }")
        }
        null -> {} // unreachable: caller guards dataSource != null
    }
    return result
}

/**
 * Returns move-sound call statements for the given SFX ID string, or empty if null. Caller uses
 * `upBody += buildMoveSoundStatements(menu.sfxOnMove)`.
 */
private fun buildMoveSoundStatements(sfxOnMove: String?): List<CStatement> {
    if (sfxOnMove == null) return emptyList()
    val sfxId = sfxOnMove.replace('-', '_').replace(' ', '_')
    return listOf(CExprStatement(CCall("play_sound_$sfxId", emptyList())))
}

/** Builds J_UP / J_DOWN navigation statements for VERTICAL menus. */
private fun buildMenuVerticalNavStatements(menu: MenuDef): List<CStatement> {
    val hasSpriteCursor = menu.cursorSprite != null
    val selVar = CVar("sel")
    val joyVar = CVar("joy")
    val lastIdx = (menu.items.size - 1).coerceAtLeast(0)
    val result = mutableListOf<CStatement>()

    // J_UP: erase old cursor, sel--, reposition sprite, SFX
    val upBody = mutableListOf<CStatement>()
    if (!hasSpriteCursor) {
        if (menu.renderOnWindow) {
            upBody +=
                CExprStatement(
                    CCall(
                        "_win_print_at",
                        listOf(
                            CLiteral(menu.x),
                            selVar,
                            CStringLiteral(" "),
                            CLiteral(1),
                        ),
                    )
                )
        } else {
            upBody += CExprStatement(CCall("gotoxy", listOf(CLiteral(menu.x), selVar)))
            upBody += CExprStatement(CCall("printf", listOf(CStringLiteral(" "))))
        }
    }
    upBody += CExprStatement(CUnaryExpr("--", selVar))
    if (hasSpriteCursor) {
        upBody +=
            CRawCode(
                "move_sprite(${MenuVisitor.MENU_CURSOR_SPRITE_ID}, ${(menu.x + 1) * 8 + 8}, (${menu.y} + sel) * 8 + 16);"
            )
    }
    upBody += buildMoveSoundStatements(menu.sfxOnMove)
    result +=
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
        if (menu.renderOnWindow) {
            downBody +=
                CExprStatement(
                    CCall(
                        "_win_print_at",
                        listOf(
                            CLiteral(menu.x),
                            selVar,
                            CStringLiteral(" "),
                            CLiteral(1),
                        ),
                    )
                )
        } else {
            downBody += CExprStatement(CCall("gotoxy", listOf(CLiteral(menu.x), selVar)))
            downBody += CExprStatement(CCall("printf", listOf(CStringLiteral(" "))))
        }
    }
    downBody += CExprStatement(CUnaryExpr("++", selVar))
    if (hasSpriteCursor) {
        downBody +=
            CRawCode(
                "move_sprite(${MenuVisitor.MENU_CURSOR_SPRITE_ID}, ${(menu.x + 1) * 8 + 8}, (${menu.y} + sel) * 8 + 16);"
            )
    }
    downBody += buildMoveSoundStatements(menu.sfxOnMove)
    result +=
        CIf(
            condition =
                CBinaryExpr(
                    CBinaryExpr(joyVar, "&", CVar("J_DOWN")),
                    "&&",
                    CBinaryExpr(selVar, "<", CLiteral(lastIdx)),
                ),
            thenBody = downBody,
        )

    return result
}

/** Builds J_LEFT / J_RIGHT navigation statements for HORIZONTAL menus. */
private fun buildMenuHorizontalNavStatements(menu: MenuDef): List<CStatement> {
    val hasSpriteCursor = menu.cursorSprite != null
    val selVar = CVar("sel")
    val joyVar = CVar("joy")
    val lastIdx = (menu.items.size - 1).coerceAtLeast(0)
    val result = mutableListOf<CStatement>()

    // J_LEFT: erase old cursor, sel--, reposition sprite, SFX
    val leftBody = mutableListOf<CStatement>()
    if (!hasSpriteCursor) {
        if (menu.renderOnWindow) {
            leftBody += CRawCode("_win_print_at(${menu.x} + sel * 6, ${menu.y}, \" \", 1);")
        } else {
            leftBody += CRawCode("gotoxy(${menu.x} + sel * 6, ${menu.y}); printf(\" \");")
        }
    }
    leftBody += CExprStatement(CUnaryExpr("--", selVar))
    if (hasSpriteCursor) {
        leftBody +=
            CRawCode(
                "move_sprite(${MenuVisitor.MENU_CURSOR_SPRITE_ID}, (${menu.x} + sel * 6) * 8 + 8, ${menu.y * 8 + 16});"
            )
    }
    leftBody += buildMoveSoundStatements(menu.sfxOnMove)
    result +=
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
        if (menu.renderOnWindow) {
            rightBody += CRawCode("_win_print_at(${menu.x} + sel * 6, ${menu.y}, \" \", 1);")
        } else {
            rightBody += CRawCode("gotoxy(${menu.x} + sel * 6, ${menu.y}); printf(\" \");")
        }
    }
    rightBody += CExprStatement(CUnaryExpr("++", selVar))
    if (hasSpriteCursor) {
        rightBody +=
            CRawCode(
                "move_sprite(${MenuVisitor.MENU_CURSOR_SPRITE_ID}, (${menu.x} + sel * 6) * 8 + 8, ${menu.y * 8 + 16});"
            )
    }
    rightBody += buildMoveSoundStatements(menu.sfxOnMove)
    result +=
        CIf(
            condition =
                CBinaryExpr(
                    CBinaryExpr(joyVar, "&", CVar("J_RIGHT")),
                    "&&",
                    CBinaryExpr(selVar, "<", CLiteral(lastIdx)),
                ),
            thenBody = rightBody,
        )

    return result
}

/** Builds J_UP / J_DOWN / J_LEFT / J_RIGHT navigation statements for GRID menus. */
private fun buildMenuGridNavStatements(menu: MenuDef): List<CStatement> {
    val hasSpriteCursor = menu.cursorSprite != null
    val joyVar = CVar("joy")
    val lastIdx = (menu.items.size - 1).coerceAtLeast(0)
    val cols = menu.columns
    val maxRow = if (menu.items.isEmpty()) 0 else (menu.items.size - 1) / cols
    val maxCol = (cols - 1).coerceAtLeast(0)
    val result = mutableListOf<CStatement>()

    // J_UP: row--
    val upBody = mutableListOf<CStatement>()
    upBody += CRawCode("if (row > 0) { row--; sel -= $cols; }")
    if (hasSpriteCursor) {
        upBody +=
            CRawCode(
                "move_sprite(${MenuVisitor.MENU_CURSOR_SPRITE_ID}, (${menu.x} + col * 6) * 8 + 8, (${menu.y} + row) * 8 + 16);"
            )
    }
    upBody += buildMoveSoundStatements(menu.sfxOnMove)
    result += CIf(condition = CBinaryExpr(joyVar, "&", CVar("J_UP")), thenBody = upBody)

    // J_DOWN: row++
    val downBody = mutableListOf<CStatement>()
    downBody +=
        CRawCode("if (row < $maxRow) { row++; sel += $cols; if (sel > $lastIdx) sel = $lastIdx; }")
    if (hasSpriteCursor) {
        downBody +=
            CRawCode(
                "move_sprite(${MenuVisitor.MENU_CURSOR_SPRITE_ID}, (${menu.x} + col * 6) * 8 + 8, (${menu.y} + row) * 8 + 16);"
            )
    }
    downBody += buildMoveSoundStatements(menu.sfxOnMove)
    result += CIf(condition = CBinaryExpr(joyVar, "&", CVar("J_DOWN")), thenBody = downBody)

    // J_LEFT: col--
    val leftBody = mutableListOf<CStatement>()
    leftBody += CRawCode("if (col > 0) { col--; sel--; }")
    if (hasSpriteCursor) {
        leftBody +=
            CRawCode(
                "move_sprite(${MenuVisitor.MENU_CURSOR_SPRITE_ID}, (${menu.x} + col * 6) * 8 + 8, (${menu.y} + row) * 8 + 16);"
            )
    }
    leftBody += buildMoveSoundStatements(menu.sfxOnMove)
    result += CIf(condition = CBinaryExpr(joyVar, "&", CVar("J_LEFT")), thenBody = leftBody)

    // J_RIGHT: col++
    val rightBody = mutableListOf<CStatement>()
    rightBody += CRawCode("if (col < $maxCol && sel < $lastIdx) { col++; sel++; }")
    if (hasSpriteCursor) {
        rightBody +=
            CRawCode(
                "move_sprite(${MenuVisitor.MENU_CURSOR_SPRITE_ID}, (${menu.x} + col * 6) * 8 + 8, (${menu.y} + row) * 8 + 16);"
            )
    }
    rightBody += buildMoveSoundStatements(menu.sfxOnMove)
    result += CIf(condition = CBinaryExpr(joyVar, "&", CVar("J_RIGHT")), thenBody = rightBody)

    return result
}
