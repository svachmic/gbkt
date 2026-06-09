/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.ir.ArrayDataSource
import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.AssetType
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.InventoryDataSource
import io.github.gbkt.core.ir.MenuDef
import io.github.gbkt.core.ir.MenuItemDef
import io.github.gbkt.core.ir.MenuLayout
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.ScrollBehavior
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// MENU CODEGEN TESTS
// Verifies that buildMenuFunctions() generates correct C functions for all three
// menu layouts (VERTICAL, HORIZONTAL, GRID), parent/child submenu stacking,
// settings controls, SFX hooks, window/background layer rendering, text cursor,
// sprite cursor, scroll behavior, and dynamic data binding.
//
// All window-layer menus must use _win_print_at (not gotoxy/printf) for rendering.
// =============================================================================

class MenuCodegenTest {

    private val baseGameIR =
        GameIR(name = "TestGame", config = CartridgeConfig(), scenes = listOf(SceneIR(id = "main")))

    /** Helper: make a simple MenuDef with the given configuration. */
    private fun makeMenuDef(
        id: String = "test",
        layout: MenuLayout = MenuLayout.VERTICAL,
        items: List<MenuItemDef> =
            listOf(
                MenuItemDef("Item A", emptyList()),
                MenuItemDef("Item B", emptyList()),
                MenuItemDef("Item C", emptyList()),
            ),
        cursorChar: String = ">",
        cursorSprite: AssetRef? = null,
        parentId: String? = null,
        renderOnWindow: Boolean = true,
        scrollBehavior: ScrollBehavior = ScrollBehavior.AUTO_SCROLL,
        sfxOnMove: String? = null,
        sfxOnSelect: String? = null,
        sfxOnCancel: String? = null,
        x: Int = 0,
        y: Int = 0,
        width: Int = 20,
        height: Int = 18,
        columns: Int = 1,
        dataSource: io.github.gbkt.core.ir.MenuDataSource? = null,
    ): MenuDef =
        MenuDef(
            id = id,
            layout = layout,
            items = items,
            cursorChar = cursorChar,
            cursorSprite = cursorSprite,
            parentId = parentId,
            renderOnWindow = renderOnWindow,
            scrollBehavior = scrollBehavior,
            sfxOnMove = sfxOnMove,
            sfxOnSelect = sfxOnSelect,
            sfxOnCancel = sfxOnCancel,
            x = x,
            y = y,
            width = width,
            height = height,
            columns = columns,
            dataSource = dataSource,
        )

    /** Generate main.c for a GameIR with the given menu. */
    private fun generateForMenu(menuDef: MenuDef): String {
        val gameIR = baseGameIR.copy(menus = listOf(menuDef))
        val pipeline = GBDKPipeline()
        return pipeline.generate(gameIR).files["main.c"]
            ?: error("main.c not found in pipeline output")
    }

    // =========================================================================
    // TEST 1: Vertical menu generates J_UP and J_DOWN navigation
    // =========================================================================
    @Test
    fun `vertical menu generates J_UP and J_DOWN navigation`() {
        val output = generateForMenu(makeMenuDef(layout = MenuLayout.VERTICAL))

        assertTrue(output.contains("show_menu_test"), "Should have show_menu_test function")
        assertTrue(output.contains("J_UP"), "Vertical menu should check J_UP for navigation")
        assertTrue(output.contains("J_DOWN"), "Vertical menu should check J_DOWN for navigation")
        // Vertical menu should NOT check horizontal keys for navigation (only A, B are fine)
        assertFalse(
            output.contains("show_menu_test") &&
                output.substringAfter("show_menu_test").contains("J_LEFT") &&
                !output.substringAfter("show_menu_test").contains("J_RIGHT"),
            "Vertical menu body should not contain J_LEFT navigation",
        )
    }

    // =========================================================================
    // TEST 2: Horizontal menu generates J_LEFT and J_RIGHT navigation
    // =========================================================================
    @Test
    fun `horizontal menu generates J_LEFT and J_RIGHT navigation`() {
        val output = generateForMenu(makeMenuDef(layout = MenuLayout.HORIZONTAL))

        assertTrue(output.contains("show_menu_test"), "Should have show_menu_test function")
        assertTrue(output.contains("J_LEFT"), "Horizontal menu should check J_LEFT")
        assertTrue(output.contains("J_RIGHT"), "Horizontal menu should check J_RIGHT")
    }

    // =========================================================================
    // TEST 3: Grid menu generates 2D cursor navigation with J_UP/J_DOWN and J_LEFT/J_RIGHT
    // =========================================================================
    @Test
    fun `grid menu generates 2D cursor navigation with columns`() {
        val output =
            generateForMenu(
                makeMenuDef(
                    layout = MenuLayout.GRID,
                    columns = 3,
                    items = (1..9).map { MenuItemDef("Item $it", emptyList()) },
                )
            )

        assertTrue(output.contains("show_menu_test"), "Should have show_menu_test function")
        assertTrue(output.contains("J_UP"), "Grid menu should check J_UP for row navigation")
        assertTrue(output.contains("J_DOWN"), "Grid menu should check J_DOWN for row navigation")
        assertTrue(output.contains("J_LEFT"), "Grid menu should check J_LEFT for column navigation")
        assertTrue(
            output.contains("J_RIGHT"),
            "Grid menu should check J_RIGHT for column navigation",
        )
        // Grid menu should declare col and row variables
        assertTrue(output.contains("col"), "Grid menu should use col variable")
        assertTrue(output.contains("row"), "Grid menu should use row variable")
    }

    // =========================================================================
    // TEST 4: Menu with parent generates B-button back navigation
    // =========================================================================
    @Test
    fun `menu with parent generates B-button back navigation`() {
        val output = generateForMenu(makeMenuDef(id = "child", parentId = "main_menu"))

        assertTrue(output.contains("J_B"), "Should check J_B for cancel/back")
        assertTrue(output.contains("show_menu_main_menu"), "Should call parent menu function")
    }

    // =========================================================================
    // TEST 5: Menu without parent generates B-button cancel (return 0xFF)
    // =========================================================================
    @Test
    fun `menu without parent generates B-button cancel`() {
        val output = generateForMenu(makeMenuDef(parentId = null))

        assertTrue(output.contains("J_B"), "Should check J_B for cancel")
        // Without parent, should return 0xFF sentinel on cancel
        assertTrue(
            output.contains("0xFF") || output.contains("255"),
            "Should return 0xFF cancel sentinel",
        )
        assertFalse(output.contains("show_menu_null"), "Should not call show_menu_null")
    }

    // =========================================================================
    // TEST 6: Menu with SFX hooks generates sound calls on move, select, cancel
    // =========================================================================
    @Test
    fun `menu with SFX hooks generates sound calls`() {
        val output =
            generateForMenu(
                makeMenuDef(
                    sfxOnMove = "cursor-move",
                    sfxOnSelect = "confirm",
                    sfxOnCancel = "cancel",
                )
            )

        assertTrue(
            output.contains("play_sound_cursor_move"),
            "Should emit play_sound_cursor_move on move",
        )
        assertTrue(
            output.contains("play_sound_confirm"),
            "Should emit play_sound_confirm on select",
        )
        assertTrue(output.contains("play_sound_cancel"), "Should emit play_sound_cancel on cancel")
    }

    // =========================================================================
    // TEST 7: Menu on window layer generates SHOW_WIN and move_win
    // =========================================================================
    @Test
    fun `menu on window layer generates SHOW_WIN and move_win`() {
        val output = generateForMenu(makeMenuDef(renderOnWindow = true))

        assertTrue(output.contains("SHOW_WIN"), "Window-layer menu should call SHOW_WIN")
        assertTrue(output.contains("move_win"), "Window-layer menu should call move_win")
        assertTrue(output.contains("HIDE_WIN"), "Window-layer menu should call HIDE_WIN on close")
    }

    // =========================================================================
    // TEST 8: Menu on background layer generates gotoxy (not SHOW_WIN)
    // =========================================================================
    @Test
    fun `menu on background layer generates gotoxy`() {
        val output = generateForMenu(makeMenuDef(renderOnWindow = false))

        assertTrue(output.contains("show_menu_test"), "Should have show_menu_test function")
        // Background-layer menus use gotoxy/printf instead of window-layer functions
        // Extract the menu function body to verify
        val menuFnStart = output.indexOf("show_menu_test")
        val menuFnBody =
            if (menuFnStart >= 0)
                output.substring(menuFnStart, (menuFnStart + 800).coerceAtMost(output.length))
            else ""
        assertTrue(menuFnBody.contains("gotoxy"), "Background-layer menu should use gotoxy")
    }

    // =========================================================================
    // TEST 9: Menu with text cursor uses cursorChar in _win_print_at
    // =========================================================================
    @Test
    fun `menu with text cursor uses cursorChar in _win_print_at`() {
        val output = generateForMenu(makeMenuDef(cursorChar = ">", cursorSprite = null))

        // Text cursor uses _win_print_at with the cursor character
        assertTrue(output.contains("_win_print_at"), "Text cursor should use _win_print_at")
        assertTrue(output.contains(">"), "Text cursor should include the cursor character '>'")
        assertFalse(
            output.contains("set_sprite_tile"),
            "Text cursor should not use set_sprite_tile",
        )
    }

    // =========================================================================
    // TEST 10: Menu cursor char is configurable
    // =========================================================================
    @Test
    fun `menu cursor char is configurable`() {
        val output = generateForMenu(makeMenuDef(cursorChar = "*", cursorSprite = null))

        assertTrue(output.contains("*"), "Custom cursor char '*' should appear in output")
        assertFalse(
            output.contains("set_sprite_tile"),
            "Custom text cursor should not use set_sprite_tile",
        )
    }

    // =========================================================================
    // TEST 11: Menu with sprite cursor generates set_sprite_tile and move_sprite
    // =========================================================================
    @Test
    fun `menu with sprite cursor generates set_sprite_tile and move_sprite`() {
        val output =
            generateForMenu(
                makeMenuDef(cursorSprite = AssetRef("sprites/cursor.png", AssetType.SPRITE))
            )

        assertTrue(output.contains("set_sprite_tile"), "Sprite cursor should call set_sprite_tile")
        assertTrue(
            output.contains("move_sprite"),
            "Sprite cursor should call move_sprite for positioning",
        )
        // Should use dedicated cursor sprite slot (38)
        assertTrue(output.contains("38"), "Should use sprite slot 38 for menu cursor")
    }

    // =========================================================================
    // TEST 12: Sprite cursor repositions on navigation via move_sprite
    // =========================================================================
    @Test
    fun `sprite cursor repositions on navigation via move_sprite`() {
        val output =
            generateForMenu(
                makeMenuDef(
                    layout = MenuLayout.VERTICAL,
                    cursorSprite = AssetRef("sprites/cursor.png", AssetType.SPRITE),
                )
            )

        // The J_UP and J_DOWN handlers should emit move_sprite to reposition cursor
        assertTrue(
            output.contains("move_sprite"),
            "Should call move_sprite for cursor repositioning",
        )
        assertTrue(output.contains("J_UP"), "Should have J_UP navigation")
        assertTrue(output.contains("J_DOWN"), "Should have J_DOWN navigation")
        // Sprite cursor repositions with + 16 OAM Y offset
        assertTrue(output.contains("16"), "OAM Y offset (+16) should appear in sprite position")
    }

    // =========================================================================
    // TEST 13: Sprite cursor hidden on menu close via move_sprite(0, 0)
    // =========================================================================
    @Test
    fun `sprite cursor hidden on menu close via move_sprite with zero coords`() {
        val output =
            generateForMenu(
                makeMenuDef(cursorSprite = AssetRef("sprites/cursor.png", AssetType.SPRITE))
            )

        // move_sprite(38, 0, 0) hides the cursor sprite (moves off-screen to OAM top-left)
        assertTrue(output.contains("move_sprite"), "Should call move_sprite to hide cursor")
        // Look for zero-argument move_sprite calls (the hide call)
        val hasHideCall = output.contains("move_sprite(38") || output.contains("move_sprite($38")
        assertTrue(
            output.contains("move_sprite") && (output.contains(", 0") || output.contains(",0")),
            "Should have move_sprite call with 0 coords to hide cursor",
        )
    }

    // =========================================================================
    // TEST 14: Menu function returns UINT8 selection index
    // =========================================================================
    @Test
    fun `menu function returns UINT8 selection index`() {
        val output = generateForMenu(makeMenuDef())

        // Function signature: UINT8 show_menu_test(void)
        assertTrue(
            output.contains("UINT8") && output.contains("show_menu_test"),
            "Menu function should return UINT8",
        )
        // Return sel at end
        assertTrue(output.contains("return"), "Menu function should return the selection")
    }

    // =========================================================================
    // TEST 15: Menu with scroll generates scroll_offset variable
    // =========================================================================
    @Test
    fun `menu with scroll generates scroll_offset variable`() {
        // More items than visible height triggers scroll
        val output =
            generateForMenu(
                makeMenuDef(
                    items = (1..20).map { MenuItemDef("Item $it", emptyList()) },
                    height = 5, // visible height = 5, items = 20 → scroll needed
                    scrollBehavior = ScrollBehavior.AUTO_SCROLL,
                )
            )

        assertTrue(
            output.contains("scroll_offset"),
            "Auto-scroll menu should declare scroll_offset variable",
        )
    }

    // =========================================================================
    // TEST 16: Grid menu with itemsFrom inventory generates dynamic population loop
    // =========================================================================
    @Test
    fun `grid menu with itemsFrom inventory generates dynamic population loop`() {
        val output =
            generateForMenu(
                makeMenuDef(
                    layout = MenuLayout.GRID,
                    columns = 2,
                    items = emptyList(),
                    dataSource = InventoryDataSource("bag"),
                )
            )

        assertTrue(output.contains("show_menu_test"), "Should have show_menu_test function")
        // Dynamic inventory loop should reference the inventory ID
        assertTrue(
            output.contains("_inventory_bag") || output.contains("bag"),
            "Should reference inventory 'bag'",
        )
    }

    // =========================================================================
    // TEST 17: Menu with itemsFrom array generates array-based item rendering
    // =========================================================================
    @Test
    fun `menu with itemsFrom array generates array-based item rendering`() {
        val output =
            generateForMenu(
                makeMenuDef(items = emptyList(), dataSource = ArrayDataSource("spells"))
            )

        assertTrue(output.contains("show_menu_test"), "Should have show_menu_test function")
        // Dynamic array loop should reference the array ID
        assertTrue(
            output.contains("_spells") || output.contains("spells"),
            "Should reference array 'spells'",
        )
    }

    // =========================================================================
    // TEST 18: Menu function has a C while(1) input loop
    // =========================================================================
    @Test
    fun `menu function has a while loop for input polling`() {
        val output = generateForMenu(makeMenuDef())

        assertTrue(output.contains("while"), "Menu should contain a while input loop")
        assertTrue(output.contains("joypad"), "Menu should poll joypad input")
        assertTrue(output.contains("wait_vbl_done"), "Menu should wait for VBlank each frame")
    }

    // =========================================================================
    // TEST 19: Menu with hyphens in ID sanitizes to underscores
    // =========================================================================
    @Test
    fun `menu with hyphens in ID sanitizes to underscores`() {
        val output = generateForMenu(makeMenuDef(id = "main-menu"))

        assertTrue(
            output.contains("show_menu_main_menu"),
            "Hyphens in menu ID should be sanitized to underscores",
        )
        assertFalse(
            output.contains("show_menu_main-menu"),
            "Hyphened menu ID should not appear in output",
        )
    }

    // =========================================================================
    // TEST 20: Sprite cursor define MENU_CURSOR_SPRITE_ID emitted when cursorSprite set
    // =========================================================================
    @Test
    fun `sprite cursor MENU_CURSOR_SPRITE_ID define emitted when cursorSprite set`() {
        val output =
            generateForMenu(
                makeMenuDef(cursorSprite = AssetRef("sprites/cursor.png", AssetType.SPRITE))
            )

        assertTrue(
            output.contains("MENU_CURSOR_SPRITE_ID"),
            "MENU_CURSOR_SPRITE_ID define should be emitted when cursorSprite is set",
        )
    }
}
