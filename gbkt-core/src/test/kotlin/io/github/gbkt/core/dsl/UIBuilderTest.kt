/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.Anchor
import io.github.gbkt.core.ir.BorderStyle
import io.github.gbkt.core.ir.DialogChoice
import io.github.gbkt.core.ir.DialogSay
import io.github.gbkt.core.ir.FontMode
import io.github.gbkt.core.ir.HudBar
import io.github.gbkt.core.ir.HudIcons
import io.github.gbkt.core.ir.HudNumber
import io.github.gbkt.core.ir.HudShow
import io.github.gbkt.core.ir.IconDisplayMode
import io.github.gbkt.core.ir.MenuLayout
import io.github.gbkt.core.ir.MenuShow
import io.github.gbkt.core.ir.NavigateTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the UI DSL builders: [DialogBuilder], [MenuBuilder], [HudBuilder].
 *
 * Uses the top-level `game {}` builder to exercise the full DSL registration path, then inspects
 * the resulting [io.github.gbkt.core.ir.GameIR] fields (dialogs, menus, huds) and scene ops for
 * correctness.
 */
class UIBuilderTest {

    // =========================================================================
    // DialogBuilder tests (7)
    // =========================================================================

    @Test
    fun `dialog builder produces DialogDef with correct id and defaults`() {
        val ir =
            game("test") {
                    dialog("elder") {}
                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        assertEquals(1, ir.dialogs.size)
        assertEquals("elder", ir.dialogs[0].id)
        assertEquals(1, ir.dialogs[0].textSpeed)
        assertEquals(BorderStyle.NONE, ir.dialogs[0].border)
        assertNull(ir.dialogs[0].speaker)
        assertNull(ir.dialogs[0].portrait)
        assertEquals(FontMode.FIXED_WIDTH, ir.dialogs[0].fontMode)
    }

    @Test
    fun `dialog builder with textSpeed and border produces configured DialogDef`() {
        val ir =
            game("test") {
                    dialog("shop") {
                        textSpeed(3)
                        border(BorderStyle.SINGLE)
                    }
                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        assertEquals(1, ir.dialogs.size)
        val def = ir.dialogs[0]
        assertEquals(3, def.textSpeed)
        assertEquals(BorderStyle.SINGLE, def.border)
    }

    @Test
    fun `dialog builder with speaker and portrait produces DialogDef with both set`() {
        val ir =
            game("test") {
                    dialog("sage") {
                        speaker("Ancient Sage")
                        portrait(asset("sprites/sage_portrait.png"))
                    }
                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        val def = ir.dialogs[0]
        assertEquals("Ancient Sage", def.speaker)
        assertNotNull(def.portrait)
        assertEquals("sprites/sage_portrait.png", def.portrait!!.path)
    }

    @Test
    fun `dialog builder with CUSTOM border and customBorderTiles produces DialogDef with tile indices`() {
        val tiles = listOf(1, 2, 3, 4, 5, 6, 7, 8)
        val ir =
            game("test") {
                    dialog("custom") {
                        border(BorderStyle.CUSTOM)
                        customBorderTiles(tiles)
                    }
                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        val def = ir.dialogs[0]
        assertEquals(BorderStyle.CUSTOM, def.border)
        assertEquals(tiles, def.customBorderTiles)
    }

    @Test
    fun `dialog builder with fontMode VARIABLE_WIDTH produces DialogDef with VWF mode`() {
        val ir =
            game("test") {
                    dialog("vwf") { fontMode(FontMode.VARIABLE_WIDTH) }
                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        assertEquals(FontMode.VARIABLE_WIDTH, ir.dialogs[0].fontMode)
    }

    @Test
    fun `dialog handle say emits DialogSay into script builder`() {
        val ir =
            game("test") {
                    val elder = dialog("elder") { textSpeed(2) }
                    val mainScene = scene("main") { enter { elder.say("Hello traveler") } }
                    start = mainScene
                }
                .build()

        val enterOps = ir.scenes.first { it.id == "main" }.enterOps
        assertEquals(1, enterOps.size)
        val say = assertIs<DialogSay>(enterOps[0])
        assertEquals("elder", say.dialogId)
        assertEquals(1, say.segments.size)
        val seg = assertIs<io.github.gbkt.core.ir.DialogTextSegment>(say.segments[0])
        assertEquals("Hello traveler", seg.text)
    }

    @Test
    fun `dialog handle choice emits DialogChoice with option bodies`() {
        val ir =
            game("test") {
                    val elder = dialog("elder") {}
                    val questActiveScene = scene("quest_active") { enter {} }
                    val villageScene = scene("village") { enter {} }
                    val questScene =
                        scene("quest") {
                            enter {
                                elder.choice {
                                    option("Accept") { navigate(questActiveScene) }
                                    option("Decline") { navigate(villageScene) }
                                }
                            }
                        }
                    start = questScene
                }
                .build()

        val enterOps = ir.scenes.first { it.id == "quest" }.enterOps
        val choice = assertIs<DialogChoice>(enterOps[0])
        assertEquals("elder", choice.dialogId)
        assertEquals(2, choice.options.size)
        assertEquals("Accept", choice.options[0].label)
        assertEquals("Decline", choice.options[1].label)
        assertEquals(1, choice.options[0].body.size)
        assertIs<NavigateTo>(choice.options[0].body[0])
    }

    // =========================================================================
    // MenuBuilder tests (5)
    // =========================================================================

    @Test
    fun `menu builder produces MenuDef with vertical layout by default`() {
        val ir =
            game("test") {
                    val gameScene = scene("game") { enter {} }
                    menu("main") { item("Start") { navigate(gameScene) } }
                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        assertEquals(1, ir.menus.size)
        val def = ir.menus[0]
        assertEquals("main", def.id)
        assertEquals(MenuLayout.VERTICAL, def.layout)
        assertEquals(1, def.items.size)
        assertEquals("Start", def.items[0].label)
    }

    @Test
    fun `menu builder with grid layout and columns produces configured MenuDef`() {
        val ir =
            game("test") {
                    menu("inventory") {
                        layout(MenuLayout.GRID)
                        columns(4)
                    }
                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        val def = ir.menus[0]
        assertEquals(MenuLayout.GRID, def.layout)
        assertEquals(4, def.columns)
    }

    @Test
    fun `menu builder with parent produces parentId reference`() {
        val ir =
            game("test") {
                    val mainMenu = menu("main") {}
                    menu("options") { parent(mainMenu) }
                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        val optionsDef = ir.menus.first { it.id == "options" }
        assertEquals("main", optionsDef.parentId)
    }

    @Test
    fun `menu builder with SFX hooks produces MenuDef with all SFX`() {
        val ir =
            game("test") {
                    menu("pause") {
                        sfx(
                            onMove = SoundRef("cursor_move"),
                            onSelect = SoundRef("select"),
                            onCancel = SoundRef("cancel"),
                        )
                    }
                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        val def = ir.menus[0]
        assertEquals("cursor_move", def.sfxOnMove)
        assertEquals("select", def.sfxOnSelect)
        assertEquals("cancel", def.sfxOnCancel)
    }

    @Test
    fun `menu handle show emits MenuShow into script builder`() {
        val ir =
            game("test") {
                    val pauseMenu = menu("pause") {}
                    val gameplayScene =
                        scene("gameplay") {
                            frame { runIf(buttons.start.pressed) { pauseMenu.show() } }
                        }
                    start = gameplayScene
                }
                .build()

        val frameOps = ir.scenes.first { it.id == "gameplay" }.frameOps
        // frameOps[0] is IfOp (whenever), body[0] is MenuShow
        val ifOp = assertIs<io.github.gbkt.core.ir.IfOp>(frameOps[0])
        val menuShow = assertIs<MenuShow>(ifOp.then[0])
        assertEquals("pause", menuShow.menuId)
    }

    // =========================================================================
    // HudBuilder tests (6)
    // =========================================================================

    @Test
    fun `hud builder produces HudDef with elements`() {
        val ir =
            game("test") {
                    var score by u8Var(0)
                    hud("stats") {
                        number("score") {
                            variable(score)
                            label("Score: ")
                        }
                    }
                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        assertEquals(1, ir.huds.size)
        val def = ir.huds[0]
        assertEquals("stats", def.id)
        assertEquals(1, def.elements.size)
    }

    @Test
    fun `hud builder bar with variable produces HudBar element`() {
        val ir =
            game("test") {
                    var hp by u8Var(20)
                    hud("health") {
                        bar("hp") {
                            variable(hp)
                            max(100)
                            width(8)
                        }
                    }
                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        val def = ir.huds[0]
        val bar = assertIs<HudBar>(def.elements[0])
        assertEquals("hp", bar.id)
        assertEquals("hp", bar.variable)
        assertEquals(100, bar.maxValue)
        assertEquals(8, bar.width)
    }

    @Test
    fun `hud builder bar with fillTile and emptyTile produces HudBar with custom tile indices`() {
        val ir =
            game("test") {
                    var torchLevel by u8Var(100)
                    hud("torch") {
                        bar("torch") {
                            variable(torchLevel)
                            fillTile(0x10)
                            emptyTile(0x11)
                        }
                    }
                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        val bar = assertIs<HudBar>(ir.huds[0].elements[0])
        assertEquals(0x10, bar.fillTile)
        assertEquals(0x11, bar.emptyTile)
    }

    @Test
    fun `hud builder with renderOnBackground produces HudDef with renderOnWindow false`() {
        val ir =
            game("test") {
                    hud("bg_hud") { renderOnBackground() }
                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        val def = ir.huds[0]
        assertEquals(false, def.renderOnWindow)
    }

    @Test
    fun `hud builder number with label and format produces HudNumber element`() {
        val ir =
            game("test") {
                    var score by u8Var(0)
                    hud("score_hud") {
                        number("score") {
                            variable(score)
                            label("Score: ")
                            format("%04d")
                        }
                    }
                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        val num = assertIs<HudNumber>(ir.huds[0].elements[0])
        assertEquals("score", num.id)
        assertEquals("score", num.variable)
        assertEquals("Score: ", num.label)
        assertEquals("%04d", num.format)
    }

    @Test
    fun `hud builder icons with display mode produces HudIcons element`() {
        val ir =
            game("test") {
                    var lives by u8Var(3)
                    hud("lives_hud") {
                        icons("lives") {
                            variable(lives)
                            max(3)
                            fullTile(0x08)
                            emptyTile(0x09)
                            displayMode(IconDisplayMode.FULL_AND_EMPTY)
                        }
                    }
                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        val icons = assertIs<HudIcons>(ir.huds[0].elements[0])
        assertEquals("lives", icons.id)
        assertEquals("lives", icons.variable)
        assertEquals(3, icons.maxValue)
        assertEquals(0x08, icons.fullTile)
        assertEquals(0x09, icons.emptyTile)
        assertEquals(IconDisplayMode.FULL_AND_EMPTY, icons.displayMode)
    }

    // =========================================================================
    // GameBuilder integration tests (2)
    // =========================================================================

    @Test
    fun `game with dialog adds DialogDef to GameIR`() {
        val ir =
            game("test") {
                    dialog("elder") {
                        speaker("Elder Moros")
                        textSpeed(2)
                        border(BorderStyle.DOUBLE)
                    }
                    val villageScene = scene("village") { enter {} }
                    start = villageScene
                }
                .build()

        assertEquals(1, ir.dialogs.size)
        assertEquals("elder", ir.dialogs[0].id)
        assertEquals("Elder Moros", ir.dialogs[0].speaker)
        assertEquals(BorderStyle.DOUBLE, ir.dialogs[0].border)
    }

    @Test
    fun `game with menu and hud adds both to GameIR`() {
        val ir =
            game("test") {
                    var score by u8Var(0)
                    val gameplayScene = scene("gameplay") { enter {} }
                    val titleScene = scene("title") { enter {} }
                    menu("pause") {
                        layout(MenuLayout.VERTICAL)
                        item("Resume") { navigate(gameplayScene) }
                        item("Quit") { navigate(titleScene) }
                    }
                    hud("score_hud") {
                        anchor(Anchor.TOP_LEFT)
                        number("score") {
                            variable(score)
                            label("Score: ")
                        }
                    }
                    start = gameplayScene
                }
                .build()

        assertEquals(1, ir.menus.size)
        assertEquals("pause", ir.menus[0].id)
        assertEquals(2, ir.menus[0].items.size)

        assertEquals(1, ir.huds.size)
        assertEquals("score_hud", ir.huds[0].id)
        assertEquals(Anchor.TOP_LEFT, ir.huds[0].anchor)
        assertEquals(1, ir.huds[0].elements.size)
    }

    // =========================================================================
    // Additional edge-case tests for full coverage (bring total to 20+)
    // =========================================================================

    @Test
    fun `hud builder icons with FILLED_ONLY display mode produces HudIcons with correct mode`() {
        val ir =
            game("test") {
                    var badges by u8Var(0)
                    hud("badge_hud") {
                        icons("badges") {
                            variable(badges)
                            max(8)
                            fullTile(0x20)
                            displayMode(IconDisplayMode.FILLED_ONLY)
                        }
                    }
                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        val icons = assertIs<HudIcons>(ir.huds[0].elements[0])
        assertEquals(IconDisplayMode.FILLED_ONLY, icons.displayMode)
        assertEquals(8, icons.maxValue)
    }

    @Test
    fun `hud show emits HudShow into scene enter ops`() {
        val ir =
            game("test") {
                    var hp by u8Var(20)
                    val statsHud = hud("stats") { bar("hp") { variable(hp) } }
                    val gameplayScene = scene("gameplay") { enter { statsHud.show() } }
                    start = gameplayScene
                }
                .build()

        val enterOps = ir.scenes.first { it.id == "gameplay" }.enterOps
        assertEquals(1, enterOps.size)
        val hudShow = assertIs<HudShow>(enterOps[0])
        assertEquals("stats", hudShow.hudId)
    }

    @Test
    fun `menu builder items define correct navigation bodies`() {
        val ir =
            game("test") {
                    val titleScene = scene("title") { enter {} }
                    val gameScene = scene("game") { enter {} }
                    menu("main") {
                        item("Play") { navigate(gameScene) }
                        item("Quit") { navigate(titleScene) }
                    }
                    start = titleScene
                }
                .build()

        val def = ir.menus[0]
        assertEquals(2, def.items.size)
        val playNav = assertIs<NavigateTo>(def.items[0].body[0])
        assertEquals("game", playNav.sceneId)
        val quitNav = assertIs<NavigateTo>(def.items[1].body[0])
        assertEquals("title", quitNav.sceneId)
    }

    @Test
    fun `dialog multiple registrations produce multiple DialogDefs in GameIR`() {
        val ir =
            game("test") {
                    dialog("elder") { textSpeed(2) }
                    dialog("merchant") { speaker("Bob's Shop") }
                    dialog("inn_keeper") { border(BorderStyle.SINGLE) }
                    val mainScene = scene("main") { enter {} }
                    start = mainScene
                }
                .build()

        assertEquals(3, ir.dialogs.size)
        assertTrue(ir.dialogs.any { it.id == "elder" })
        assertTrue(ir.dialogs.any { it.id == "merchant" })
        assertTrue(ir.dialogs.any { it.id == "inn_keeper" })
    }
}
