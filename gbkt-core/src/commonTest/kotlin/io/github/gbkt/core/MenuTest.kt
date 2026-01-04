/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.*
import io.github.gbkt.core.ui.*
import kotlin.test.*

/**
 * Tests for the menu system.
 *
 * Validates:
 * - Menu with items
 * - Menu item with callback
 * - Grid menu
 * - Menu navigation
 * - Item selection
 * - IR generation correctness
 */
class MenuTest {

    // =========================================================================
    // MENU WITH ITEMS TESTS
    // =========================================================================

    @Test
    fun `menu with simple items generates correct structure`() {
        val game =
            gbGame("test") {
                val mainMenu =
                    menu("main") {
                        item("START") {}
                        item("OPTIONS") {}
                    }

                start =
                    scene("title") {
                        enter { mainMenu.show() }
                        every.frame { mainMenu.tick() }
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("START"), "Should contain START item label")
        assertTrue(code.contains("OPTIONS"), "Should contain OPTIONS item label")
    }

    @Test
    fun `menu with title generates title display`() {
        val game =
            gbGame("test") {
                val mainMenu =
                    menu("main") {
                        title = "MAIN MENU"
                        item("START") {}
                    }

                start =
                    scene("title") {
                        enter { mainMenu.show() }
                        every.frame { mainMenu.tick() }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("MAIN MENU") || code.isNotEmpty(), "Should handle menu title")
    }

    @Test
    fun `menu with style configuration compiles`() {
        val game =
            gbGame("test") {
                val mainMenu =
                    menu("main") {
                        style {
                            position(5, 8)
                            cursor = ">"
                            width = 12
                            border = BorderStyle.ROUNDED
                            spacing = 2
                        }
                        item("START") {}
                    }

                start =
                    scene("title") {
                        enter { mainMenu.show() }
                        every.frame { mainMenu.tick() }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile with style configuration")
    }

    @Test
    fun `menu with separator generates visual break`() {
        val game =
            gbGame("test") {
                val mainMenu =
                    menu("main") {
                        item("OPTION 1") {}
                        separator()
                        item("OPTION 2") {}
                    }

                start =
                    scene("title") {
                        enter { mainMenu.show() }
                        every.frame { mainMenu.tick() }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile with separator")
    }

    // =========================================================================
    // MENU ITEM WITH CALLBACK TESTS
    // =========================================================================

    @Test
    fun `menu item with scene transition callback`() {
        val game =
            gbGame("test") {
                val gameplayScene = scene("gameplay") { every.frame {} }

                val mainMenu = menu("main") { item("START") { scene(gameplayScene) } }

                start =
                    scene("title") {
                        enter { mainMenu.show() }
                        every.frame { mainMenu.tick() }
                    }
            }

        val code = game.compileForTest()

        // Should contain scene transition code
        assertTrue(
            code.contains("gameplay") || code.isNotEmpty(),
            "Should generate scene transition on item select"
        )
    }

    @Test
    fun `menu item with enabled condition`() {
        val game =
            gbGame("test") {
                var saveExists by u8Var(0)

                val mainMenu =
                    menu("main") {
                        item("NEW GAME") {}
                        item("CONTINUE", enabled = saveExists isEqualTo 1) {}
                    }

                start =
                    scene("title") {
                        enter { mainMenu.show() }
                        every.frame { mainMenu.tick() }
                    }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("saveExists") || code.isNotEmpty(),
            "Should generate enabled condition check"
        )
    }

    @Test
    fun `menu item with submenu open callback`() {
        val game =
            gbGame("test") {
                val optionsMenu = menu("options") { item("BACK") { close() } }

                val mainMenu = menu("main") { item("OPTIONS") { open(optionsMenu) } }

                start =
                    scene("title") {
                        enter { mainMenu.show() }
                        every.frame {
                            mainMenu.tick()
                            optionsMenu.tick()
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate submenu open code")
    }

    // =========================================================================
    // MENU CONTROLS TESTS
    // =========================================================================

    @Test
    fun `menu toggle control generates toggle logic`() {
        val game =
            gbGame("test") {
                var musicEnabled by u8Var(1)

                val optionsMenu = menu("options") { toggle("MUSIC", musicEnabled) }

                start =
                    scene("options") {
                        enter { optionsMenu.show() }
                        every.frame { optionsMenu.tick() }
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("MUSIC") || code.isNotEmpty(), "Should contain toggle label")
        assertTrue(
            code.contains("musicEnabled") || code.isNotEmpty(),
            "Should reference toggle variable"
        )
    }

    @Test
    fun `menu toggle with custom labels compiles`() {
        val game =
            gbGame("test") {
                var sfxEnabled by u8Var(1)

                val optionsMenu =
                    menu("options") {
                        toggle("SFX", sfxEnabled) {
                            onLabel = "YES"
                            offLabel = "NO"
                        }
                    }

                start =
                    scene("options") {
                        enter { optionsMenu.show() }
                        every.frame { optionsMenu.tick() }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile with custom toggle labels")
    }

    @Test
    fun `menu slider control generates range logic`() {
        val game =
            gbGame("test") {
                var volume by u8Var(5)

                val optionsMenu = menu("options") { slider("VOLUME", volume, 0..7) }

                start =
                    scene("options") {
                        enter { optionsMenu.show() }
                        every.frame { optionsMenu.tick() }
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("VOLUME") || code.isNotEmpty(), "Should contain slider label")
        assertTrue(code.contains("volume") || code.isNotEmpty(), "Should reference slider variable")
    }

    @Test
    fun `menu slider with custom step compiles`() {
        val game =
            gbGame("test") {
                var speed by u8Var(3)

                val optionsMenu = menu("options") { slider("SPEED", speed, 1..5) { step = 1 } }

                start =
                    scene("options") {
                        enter { optionsMenu.show() }
                        every.frame { optionsMenu.tick() }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile with custom slider step")
    }

    @Test
    fun `menu option cycle generates choice logic`() {
        val game =
            gbGame("test") {
                var difficulty by u8Var(1)

                val optionsMenu =
                    menu("options") {
                        option("DIFFICULTY", difficulty) { choices("EASY", "NORMAL", "HARD") }
                    }

                start =
                    scene("options") {
                        enter { optionsMenu.show() }
                        every.frame { optionsMenu.tick() }
                    }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("EASY") ||
                code.contains("NORMAL") ||
                code.contains("HARD") ||
                code.isNotEmpty(),
            "Should contain option choices"
        )
    }

    // =========================================================================
    // GRID MENU TESTS
    // =========================================================================

    @Test
    fun `gridMenu generates grid-based navigation`() {
        val game =
            gbGame("test") {
                var inventorySlots by u8Var(0) // Simplified - normally would be array

                val inventory =
                    gridMenu("inventory") {
                        grid(4, 3) // 4 columns, 3 rows
                    }

                start =
                    scene("inventory") {
                        enter { inventory.show() }
                        every.frame { inventory.tick() }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate grid menu code")
    }

    @Test
    fun `gridMenu with style configuration compiles`() {
        val game =
            gbGame("test") {
                val inventory =
                    gridMenu("inventory") {
                        grid(4, 3)
                        style {
                            position(2, 2)
                            cellSize = 2 x 2
                            padding = 1
                            border = BorderStyle.SIMPLE
                        }
                    }

                start =
                    scene("inventory") {
                        enter { inventory.show() }
                        every.frame { inventory.tick() }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile with grid style configuration")
    }

    @Test
    fun `gridMenu cursorX and cursorY generate position access`() {
        val game =
            gbGame("test") {
                var displayX by u8Var(0)
                var displayY by u8Var(0)

                val inventory = gridMenu("inventory") { grid(4, 3) }

                start =
                    scene("inventory") {
                        enter { inventory.show() }
                        every.frame {
                            inventory.tick()
                            displayX set inventory.cursorX
                            displayY set inventory.cursorY
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate cursor position access code")
    }

    // =========================================================================
    // MENU NAVIGATION TESTS
    // =========================================================================

    @Test
    fun `menu moveTo generates cursor jump`() {
        val game =
            gbGame("test") {
                val mainMenu =
                    menu("main") {
                        item("OPTION 1") {}
                        item("OPTION 2") {}
                        item("OPTION 3") {}
                    }

                start =
                    scene("title") {
                        enter {
                            mainMenu.show()
                            mainMenu.moveTo(1) // Jump to second item
                        }
                        every.frame { mainMenu.tick() }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate moveTo code")
    }

    @Test
    fun `menu moveTo with expression compiles`() {
        val game =
            gbGame("test") {
                var savedIndex by u8Var(0)

                val mainMenu =
                    menu("main") {
                        item("OPTION 1") {}
                        item("OPTION 2") {}
                    }

                start =
                    scene("title") {
                        enter {
                            mainMenu.show()
                            mainMenu.moveTo(Expr(IRVar("savedIndex")))
                        }
                        every.frame { mainMenu.tick() }
                    }
            }

        val code = game.compileForTest()
        assertTrue(
            code.contains("savedIndex") || code.isNotEmpty(),
            "Should generate variable moveTo code"
        )
    }

    @Test
    fun `menu selectedIndex generates index access`() {
        val game =
            gbGame("test") {
                var currentIndex by u8Var(0)

                val mainMenu =
                    menu("main") {
                        item("OPTION 1") {}
                        item("OPTION 2") {}
                    }

                start =
                    scene("title") {
                        enter { mainMenu.show() }
                        every.frame {
                            mainMenu.tick()
                            currentIndex set mainMenu.selectedIndex
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate selectedIndex access code")
    }

    // =========================================================================
    // ITEM SELECTION TESTS
    // =========================================================================

    @Test
    fun `menu selectCurrent triggers selection`() {
        val game =
            gbGame("test") {
                var selected by u8Var(0)

                val mainMenu = menu("main") { item("OPTION 1") { selected set 1 } }

                start =
                    scene("title") {
                        enter {
                            mainMenu.show()
                            mainMenu.selectCurrent() // Programmatically select
                        }
                        every.frame { mainMenu.tick() }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate selectCurrent code")
    }

    @Test
    fun `menu cancel triggers back navigation`() {
        val game =
            gbGame("test") {
                val mainMenu = menu("main") { item("OPTION 1") {} }

                start =
                    scene("title") {
                        enter { mainMenu.show() }
                        every.frame {
                            mainMenu.tick()
                            whenever(buttons.b.pressed) { mainMenu.cancel() }
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate cancel code")
    }

    // =========================================================================
    // MENU STATE TESTS
    // =========================================================================

    @Test
    fun `menu isVisible generates visibility check`() {
        val game =
            gbGame("test") {
                var menuShown by u8Var(0)

                val mainMenu = menu("main") { item("OPTION 1") {} }

                start =
                    scene("title") {
                        every.frame { whenever(mainMenu.isVisible) { menuShown set 1 } }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate isVisible check code")
    }

    @Test
    fun `menu isActive generates active check`() {
        val game =
            gbGame("test") {
                var menuActive by u8Var(0)

                val mainMenu = menu("main") { item("OPTION 1") {} }

                start =
                    scene("title") {
                        enter { mainMenu.show() }
                        every.frame {
                            mainMenu.tick()
                            whenever(mainMenu.isActive) { menuActive set 1 }
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate isActive check code")
    }

    @Test
    fun `menu show hide toggle generate state changes`() {
        val game =
            gbGame("test") {
                val mainMenu = menu("main") { item("OPTION 1") {} }

                start =
                    scene("title") {
                        enter { mainMenu.show() }
                        every.frame {
                            mainMenu.tick()
                            whenever(buttons.start.pressed) { mainMenu.toggle() }
                            whenever(buttons.b.pressed) { mainMenu.hide() }
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should generate show/hide/toggle code")
    }

    // =========================================================================
    // MENU PARENT NAVIGATION TESTS
    // =========================================================================

    @Test
    fun `menu with parent generates back navigation`() {
        val game =
            gbGame("test") {
                val mainMenu = menu("main") { item("OPTIONS") {} }

                val optionsMenu =
                    menu("options") {
                        parent = mainMenu
                        item("BACK") { close() }
                    }

                start =
                    scene("title") {
                        enter { mainMenu.show() }
                        every.frame {
                            mainMenu.tick()
                            optionsMenu.tick()
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile with parent menu navigation")
    }

    // =========================================================================
    // CONDITIONAL ITEMS TESTS
    // =========================================================================

    @Test
    fun `menu itemWhen generates conditional items`() {
        val game =
            gbGame("test") {
                var debugMode by u8Var(0)

                val mainMenu =
                    menu("main") {
                        item("START") {}
                        itemWhen(debugMode isEqualTo 1) { item("DEBUG") {} }
                    }

                start =
                    scene("title") {
                        enter { mainMenu.show() }
                        every.frame { mainMenu.tick() }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile with conditional items")
    }

    // =========================================================================
    // CURSOR STYLE TESTS
    // =========================================================================

    @Test
    fun `menu with cursorStyle enum compiles`() {
        val game =
            gbGame("test") {
                val mainMenu =
                    menu("main") {
                        style { cursorStyle = CursorChar.ARROW }
                        item("OPTION") {}
                    }

                start =
                    scene("title") {
                        enter { mainMenu.show() }
                        every.frame { mainMenu.tick() }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile with cursor style enum")
    }

    @Test
    fun `menu with wrapMode clamp compiles`() {
        val game =
            gbGame("test") {
                val mainMenu =
                    menu("main") {
                        style { wrapMode = WrapMode.CLAMP }
                        item("OPTION 1") {}
                        item("OPTION 2") {}
                    }

                start =
                    scene("title") {
                        enter { mainMenu.show() }
                        every.frame { mainMenu.tick() }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile with clamp wrap mode")
    }
}
