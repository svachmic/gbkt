/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.rpg

import io.github.gbkt.core.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the battle menu UI framework.
 *
 * Validates:
 * - Battle menu type definitions
 * - Battle menu configuration
 * - IR generation
 * - Code generation
 */
class BattleMenuTest {

    // =========================================================================
    // BATTLE MENU TYPE DEFINITIONS
    // =========================================================================

    @Test
    fun `battle menu types are defined`() {
        val types = BattleMenuType.entries
        assertTrue(types.contains(BattleMenuType.MAIN))
        assertTrue(types.contains(BattleMenuType.ABILITY))
        assertTrue(types.contains(BattleMenuType.ITEM))
        assertTrue(types.contains(BattleMenuType.TARGET_ENEMY))
        assertTrue(types.contains(BattleMenuType.TARGET_ALLY))
        assertTrue(types.contains(BattleMenuType.TARGET_ALL))
        assertEquals(6, types.size)
    }

    // =========================================================================
    // BATTLE MENU POSITION DEFINITIONS
    // =========================================================================

    @Test
    fun `battle menu positions are defined`() {
        val positions = BattleMenuPosition.entries
        assertTrue(positions.contains(BattleMenuPosition.BOTTOM_LEFT))
        assertTrue(positions.contains(BattleMenuPosition.BOTTOM_CENTER))
        assertTrue(positions.contains(BattleMenuPosition.BOTTOM_RIGHT))
        assertTrue(positions.contains(BattleMenuPosition.RIGHT_SIDE))
        assertTrue(positions.contains(BattleMenuPosition.BOTTOM_FULL))
        assertEquals(5, positions.size)
    }

    // =========================================================================
    // BATTLE MENU BUILDER
    // =========================================================================

    @Test
    fun `battleMenu creates system with default main menu`() {
        val menu = battleMenu("test") {}

        assertEquals("test", menu.system.name)
        assertNotNull(menu.system.mainMenu)
        assertEquals(BattleMenuType.MAIN, menu.system.mainMenu.menuType)
    }

    @Test
    fun `battleMenu can configure main menu position`() {
        val menu = battleMenu("test") { mainMenu { position(2, 10) } }

        assertEquals(2, menu.system.mainMenu.x)
        assertEquals(10, menu.system.mainMenu.y)
    }

    @Test
    fun `battleMenu can configure main menu commands`() {
        val menu =
            battleMenu("test") {
                mainMenu {
                    command(BattleActionType.ATTACK, "HIT")
                    command(BattleActionType.DEFEND, "BLOCK")
                }
            }

        assertEquals(2, menu.system.mainMenu.commands.size)
        assertEquals("HIT", menu.system.mainMenu.commands[0].label)
        assertEquals(BattleActionType.ATTACK, menu.system.mainMenu.commands[0].type)
        assertEquals("BLOCK", menu.system.mainMenu.commands[1].label)
        assertEquals(BattleActionType.DEFEND, menu.system.mainMenu.commands[1].type)
    }

    @Test
    fun `battleMenu can configure ability submenu`() {
        val menu =
            battleMenu("test") {
                abilityMenu {
                    position(10, 4)
                    size(10, 12)
                }
            }

        val abilityMenu = requireNotNull(menu.system.abilityMenu)
        assertEquals(10, abilityMenu.x)
        assertEquals(4, abilityMenu.y)
        assertEquals(10, abilityMenu.width)
        assertEquals(12, abilityMenu.height)
    }

    @Test
    fun `battleMenu can configure item submenu`() {
        val menu = battleMenu("test") { itemMenu { position(10, 4) } }

        val itemMenu = requireNotNull(menu.system.itemMenu)
        assertEquals(BattleMenuType.ITEM, itemMenu.menuType)
    }

    @Test
    fun `battleMenu can configure target menu`() {
        val menu =
            battleMenu("test") {
                targetMenu {
                    position(0, 0)
                    size(20, 10)
                }
            }

        assertNotNull(menu.system.targetMenu)
    }

    @Test
    fun `battleMenu can configure status display`() {
        val menu =
            battleMenu("test") {
                statusDisplay {
                    position(10, 12)
                    width(10)
                    show(hp = true, sp = true, name = true)
                    hpBarWidth(8)
                }
            }

        val statusDisplay = requireNotNull(menu.system.statusDisplay)
        assertEquals(10, statusDisplay.x)
        assertEquals(12, statusDisplay.y)
        assertEquals(10, statusDisplay.width)
        assertTrue(statusDisplay.showHP)
        assertTrue(statusDisplay.showSP)
        assertTrue(statusDisplay.showName)
        assertEquals(8, statusDisplay.hpBarWidth)
    }

    // =========================================================================
    // IR GENERATION
    // =========================================================================

    @Test
    fun `registerBattleMenu emits IR`() {
        val game =
            gbGame("RegisterBattleMenuIRTest") {
                val menu = battleMenu("battle") {}

                start =
                    scene("main") {
                        enter { registerBattleMenu(menu) }
                        every.frame {}
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasConfigIR = scene.onEnter.any { stmt -> stmt is IRBattleMenuConfig }
        assertTrue(hasConfigIR, "Should emit IRBattleMenuConfig")
    }

    @Test
    fun `battleMenu open emits IR`() {
        val game =
            gbGame("BattleMenuOpenIRTest") {
                val menu = battleMenu("battle") {}

                start =
                    scene("main") {
                        enter { registerBattleMenu(menu) }
                        every.frame { menu.openMain() }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasOpenIR = scene.onFrame.any { stmt -> stmt is IRBattleMenuOpen }
        assertTrue(hasOpenIR, "Should emit IRBattleMenuOpen")
    }

    @Test
    fun `battleMenu tick emits IR`() {
        val game =
            gbGame("BattleMenuTickIRTest") {
                val menu = battleMenu("battle") {}

                start =
                    scene("main") {
                        enter { registerBattleMenu(menu) }
                        every.frame { menu.tick() }
                    }
            }

        val scene = game.scenes["main"]
        assertNotNull(scene)

        val hasTickIR = scene.onFrame.any { stmt -> stmt is IRBattleMenuTick }
        assertTrue(hasTickIR, "Should emit IRBattleMenuTick")
    }

    // =========================================================================
    // CODE GENERATION
    // =========================================================================

    @Test
    fun `battle menu generates type constants`() {
        val game =
            gbGame("BattleMenuCodegenConstantsTest") {
                val menu = battleMenu("battle") {}

                start =
                    scene("main") {
                        enter { registerBattleMenu(menu) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("BMENU_TYPE_MAIN"), "Should generate MAIN type constant")
        assertTrue(code.contains("BMENU_TYPE_ABILITY"), "Should generate ABILITY type constant")
        assertTrue(code.contains("BMENU_TYPE_ITEM"), "Should generate ITEM type constant")
    }

    @Test
    fun `battle menu generates state variables`() {
        val game =
            gbGame("BattleMenuCodegenVarsTest") {
                val menu = battleMenu("battle") {}

                start =
                    scene("main") {
                        enter { registerBattleMenu(menu) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("_bmenu_battle_visible"), "Should generate visible var")
        assertTrue(code.contains("_bmenu_battle_active"), "Should generate active var")
        assertTrue(code.contains("_bmenu_battle_cursor"), "Should generate cursor var")
        assertTrue(code.contains("_bmenu_battle_type"), "Should generate type var")
    }

    @Test
    fun `battle menu generates draw function`() {
        val game =
            gbGame("BattleMenuCodegenDrawTest") {
                val menu = battleMenu("battle") {}

                start =
                    scene("main") {
                        enter { registerBattleMenu(menu) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("_bmenu_battle_draw"), "Should generate draw function")
    }

    @Test
    fun `battle menu generates tick function`() {
        val game =
            gbGame("BattleMenuCodegenTickTest") {
                val menu = battleMenu("battle") {}

                start =
                    scene("main") {
                        enter { registerBattleMenu(menu) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("_bmenu_battle_tick"), "Should generate tick function")
    }

    @Test
    fun `battle menu generates select function`() {
        val game =
            gbGame("BattleMenuCodegenSelectTest") {
                val menu = battleMenu("battle") {}

                start =
                    scene("main") {
                        enter { registerBattleMenu(menu) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("_bmenu_battle_do_select"), "Should generate select function")
    }

    @Test
    fun `battle menu generates command labels`() {
        val game =
            gbGame("BattleMenuCodegenLabelsTest") {
                val menu =
                    battleMenu("battle") {
                        mainMenu {
                            command(BattleActionType.ATTACK, "STRIKE")
                            command(BattleActionType.DEFEND, "GUARD")
                        }
                    }

                start =
                    scene("main") {
                        enter { registerBattleMenu(menu) }
                        every.frame {}
                    }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("\"STRIKE\""), "Should contain STRIKE label")
        assertTrue(code.contains("\"GUARD\""), "Should contain GUARD label")
    }

    // =========================================================================
    // BATTLE MENU COMMAND DATA CLASS
    // =========================================================================

    @Test
    fun `BattleMenuCommand stores correct values`() {
        val cmd = BattleMenuCommand(type = BattleActionType.ATTACK, label = "ATTACK")

        assertEquals(BattleActionType.ATTACK, cmd.type)
        assertEquals("ATTACK", cmd.label)
    }

    // =========================================================================
    // BATTLE STATUS CONFIG DATA CLASS
    // =========================================================================

    @Test
    fun `BattleStatusConfig has correct defaults`() {
        val config = BattleStatusConfig(x = 10, y = 12, width = 10)

        assertEquals(10, config.x)
        assertEquals(12, config.y)
        assertEquals(10, config.width)
        assertTrue(config.showHP)
        assertTrue(config.showSP)
        assertTrue(config.showName)
        assertEquals(false, config.showLevel)
        assertEquals(8, config.hpBarWidth)
    }
}
