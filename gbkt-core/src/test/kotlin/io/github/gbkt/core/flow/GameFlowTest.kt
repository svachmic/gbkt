/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.flow

import io.github.gbkt.core.SceneRef
import io.github.gbkt.core.gbGame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tests for the game flow system. */
class GameFlowTest {

    // =========================================================================
    // GAME FLOW CONFIGURATION
    // =========================================================================

    @Test
    fun `gameFlow creates default config`() {
        val flow = gameFlow {}

        assertFalse(flow.isDevMode)
        assertNull(flow.getStartScene())
    }

    @Test
    fun `gameFlow registers title screen`() {
        var titleRef: SceneRef? = null

        val game =
            gbGame("TitleScreenTest") {
                titleRef = scene("title") { every.frame {} }

                val flow = gameFlow { titleScreen(titleRef!!) }

                assertTrue(flow.hasScene(GameFlowScene.TITLE))
                assertEquals(titleRef, flow.getScene(GameFlowScene.TITLE))

                start = titleRef!!
            }
    }

    @Test
    fun `gameFlow registers multiple scenes`() {
        val game =
            gbGame("MultiSceneFlowTest") {
                val titleRef = scene("title") { every.frame {} }
                val gameplayRef = scene("gameplay") { every.frame {} }
                val gameoverRef = scene("gameover") { every.frame {} }

                val flow = gameFlow {
                    titleScreen(titleRef)
                    gameplay(gameplayRef)
                    gameOver(gameoverRef)
                }

                assertTrue(flow.hasScene(GameFlowScene.TITLE))
                assertTrue(flow.hasScene(GameFlowScene.GAMEPLAY))
                assertTrue(flow.hasScene(GameFlowScene.GAME_OVER))
                assertFalse(flow.hasScene(GameFlowScene.BATTLE))
                assertFalse(flow.hasScene(GameFlowScene.CHARACTER_SELECT))

                start = titleRef
            }
    }

    @Test
    fun `gameFlow respects dev mode startAt`() {
        val game =
            gbGame("DevModeTest") {
                val titleRef = scene("title") { every.frame {} }
                val gameplayRef = scene("gameplay") { every.frame {} }

                val flow = gameFlow {
                    titleScreen(titleRef)
                    gameplay(gameplayRef)

                    devMode { startAt(gameplayRef) }
                }

                assertTrue(flow.isDevMode)
                // In dev mode, should start at gameplay instead of title
                assertEquals(gameplayRef, flow.getStartScene())

                start = titleRef
            }
    }

    @Test
    fun `gameFlow without devMode starts at title`() {
        val game =
            gbGame("NormalModeTest") {
                val titleRef = scene("title") { every.frame {} }
                val gameplayRef = scene("gameplay") { every.frame {} }

                val flow = gameFlow {
                    titleScreen(titleRef)
                    gameplay(gameplayRef)
                }

                assertFalse(flow.isDevMode)
                assertEquals(titleRef, flow.getStartScene())

                start = titleRef
            }
    }

    // =========================================================================
    // SAVE MENU
    // =========================================================================

    @Test
    fun `saveMenu creates config with defaults`() {
        val menu = saveMenu("load") { slots(3) }

        assertEquals("load", menu.name)
        assertEquals(3, menu.slotCount)
        assertFalse(menu.isSaveMode)
    }

    @Test
    fun `saveMenu can be configured for save mode`() {
        val menu =
            saveMenu("save") {
                slots(3)
                mode(true)
            }

        assertTrue(menu.isSaveMode)
    }

    @Test
    fun `saveMenu supports slot display configuration`() {
        val menu =
            saveMenu("load") {
                slots(3)

                slotDisplay {
                    showName(true)
                    showLevel(true)
                    showPlayTime(true)
                    emptyText("- No Data -")
                }
            }

        assertEquals("load", menu.name)
    }

    @Test
    fun `saveMenu supports style configuration`() {
        val menu =
            saveMenu("load") {
                slots(3)

                style {
                    position(2, 4)
                    width(16)
                    border(true)
                }
            }

        assertEquals("load", menu.name)
    }

    // =========================================================================
    // PAUSE MENU
    // =========================================================================

    @Test
    fun `pauseMenu creates config with defaults`() {
        val menu = pauseMenu("pause") { resume("RESUME") }

        assertEquals("pause", menu.name)
        assertEquals(1, menu.itemCount)
        assertTrue(menu.isAutoWired)
        assertTrue(menu.pausesLogic)
    }

    @Test
    fun `pauseMenu supports multiple standard items`() {
        val menu =
            pauseMenu("pause") {
                resume("RESUME")
                save("SAVE")
                options("OPTIONS")
                quit("QUIT")
            }

        assertEquals(4, menu.itemCount)
    }

    @Test
    fun `pauseMenu supports custom items`() {
        val menu =
            pauseMenu("pause") {
                resume()
                item("INVENTORY")
                item("STATUS")
                quit()
            }

        assertEquals(4, menu.itemCount)
    }

    @Test
    fun `pauseMenu can disable auto-wire`() {
        val menu =
            pauseMenu("pause") {
                autoWire(false)
                resume()
            }

        assertFalse(menu.isAutoWired)
    }

    @Test
    fun `pauseMenu can disable pause logic`() {
        val menu =
            pauseMenu("pause") {
                pauseLogic(false)
                resume()
            }

        assertFalse(menu.pausesLogic)
    }

    @Test
    fun `pauseMenu supports style configuration`() {
        val menu =
            pauseMenu("pause") {
                resume()

                style {
                    position(5, 5)
                    width(10)
                    border(true)
                    dimBackground(true)
                }
            }

        assertEquals("pause", menu.name)
    }
}
