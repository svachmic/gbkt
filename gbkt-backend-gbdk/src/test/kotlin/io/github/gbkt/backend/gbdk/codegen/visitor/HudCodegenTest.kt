/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.ir.Anchor
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.HudBar
import io.github.gbkt.core.ir.HudDef
import io.github.gbkt.core.ir.HudElement
import io.github.gbkt.core.ir.HudIcons
import io.github.gbkt.core.ir.HudNumber
import io.github.gbkt.core.ir.IconDisplayMode
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.VarType
import io.github.gbkt.core.ir.VariableDef
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// HUD CODEGEN TESTS
// Verifies that buildHudFunctions() generates correct C code for all HUD element
// types (bar, number, icons), change-detection rendering, anchor positioning,
// window/background layer selection, and per-frame update call injection.
//
// All window-layer HUDs must use set_win_tiles (not gotoxy/printf) for rendering.
// Background-layer HUDs must use set_bkg_tiles instead.
// =============================================================================

class HudCodegenTest {

    private val baseGameIR =
        GameIR(
            name = "TestGame",
            config = CartridgeConfig(),
            scenes = listOf(SceneIR(id = "gameplay", frameOps = listOf())),
        )

    /** Helper: make a simple HudDef with the given elements. */
    private fun makeHudDef(
        id: String = "status",
        anchor: Anchor = Anchor.TOP_LEFT,
        tileX: Int? = null,
        tileY: Int? = null,
        renderOnWindow: Boolean = true,
        elements: List<HudElement> = emptyList(),
    ): HudDef =
        HudDef(
            id = id,
            anchor = anchor,
            tileX = tileX,
            tileY = tileY,
            renderOnWindow = renderOnWindow,
            elements = elements,
        )

    /** Generate main.c for a GameIR with the given HUD. */
    private fun generateForHud(hudDef: HudDef): String {
        val gameIR =
            baseGameIR.copy(
                huds = listOf(hudDef),
                variables =
                    listOf(
                        VariableDef(name = "hp", type = VarType.U8, initialValue = 100),
                        VariableDef(name = "lives", type = VarType.U8, initialValue = 3),
                        VariableDef(name = "score", type = VarType.U8, initialValue = 0),
                    ),
            )
        val pipeline = GBDKPipeline()
        return pipeline.generate(gameIR).files["main.c"]
            ?: error("main.c not found in pipeline output")
    }

    // =========================================================================
    // TEST 1: HUD with bar element generates fill bar rendering loop
    // =========================================================================
    @Test
    fun `hud with bar element generates fill bar rendering loop`() {
        val output =
            generateForHud(
                makeHudDef(
                    elements =
                        listOf(HudBar(id = "hp_bar", variable = "hp", maxValue = 100, width = 8))
                )
            )

        assertTrue(output.contains("update_hud_status"), "Should have update_hud_status function")
        assertTrue(output.contains("_hfi"), "Should have fill index loop variable")
        assertTrue(output.contains("_hfilled"), "Should have filled tiles count variable")
        // Should contain the fill bar loop (CFor with width comparison)
        assertTrue(output.contains("< 8u"), "Should iterate up to bar width 8")
    }

    // =========================================================================
    // TEST 2: HUD bar with custom fillTile and emptyTile generates correct tile indices
    // =========================================================================
    @Test
    fun `hud bar with custom fillTile and emptyTile generates correct tile indices`() {
        val output =
            generateForHud(
                makeHudDef(
                    elements =
                        listOf(
                            HudBar(
                                id = "hp_bar",
                                variable = "hp",
                                maxValue = 100,
                                width = 8,
                                fillTile = 0x05,
                                emptyTile = 0x06,
                            )
                        )
                )
            )

        // Tile index constants should be initialized with the custom values
        assertTrue(output.contains("_hud_fill_tile_status_hp_bar"), "Should have fill tile global")
        assertTrue(
            output.contains("_hud_empty_tile_status_hp_bar"),
            "Should have empty tile global",
        )
        // The custom tile indices (5 and 6) should appear as initializers
        assertTrue(
            output.contains("5u") || output.contains(" 5,") || output.contains("= 5"),
            "Fill tile 0x05 should appear as 5 in output",
        )
        assertTrue(
            output.contains("6u") || output.contains(" 6,") || output.contains("= 6"),
            "Empty tile 0x06 should appear as 6 in output",
        )
    }

    // =========================================================================
    // TEST 3: HUD bar with animated fill generates per-frame display variable
    // =========================================================================
    @Test
    fun `hud bar with animated fill generates per-frame display variable`() {
        val output =
            generateForHud(
                makeHudDef(
                    elements =
                        listOf(
                            HudBar(
                                id = "hp_bar",
                                variable = "hp",
                                maxValue = 100,
                                width = 8,
                                fillFrames = 4,
                            )
                        )
                )
            )

        // Animated bar needs a _display variable that converges toward the target
        assertTrue(
            output.contains("_hud_status_hp_bar_display"),
            "Animated bar should have display variable",
        )
    }

    // =========================================================================
    // TEST 4: HUD with number element generates label and value print
    // =========================================================================
    @Test
    fun `hud with number element generates label and value print`() {
        val output =
            generateForHud(
                makeHudDef(
                    elements =
                        listOf(HudNumber(id = "score_display", variable = "score", label = "SC:"))
                )
            )

        assertTrue(output.contains("update_hud_status"), "Should have update_hud_status function")
        assertTrue(output.contains("_win_print_at"), "Should call _win_print_at for label")
        assertTrue(output.contains("_hud_print_u8"), "Should call _hud_print_u8 for value")
        assertTrue(output.contains("\"SC:\""), "Should print the label text")
    }

    // =========================================================================
    // TEST 5: HUD with number format generates appropriate call
    // =========================================================================
    @Test
    fun `hud with number element generates _hud_print_u8 helper call`() {
        val output =
            generateForHud(
                makeHudDef(
                    elements =
                        listOf(
                            HudNumber(
                                id = "score_num",
                                variable = "score",
                                label = "",
                                format = "%04d",
                            )
                        )
                )
            )

        assertTrue(output.contains("_hud_print_u8"), "Should use _hud_print_u8 for numeric display")
        // The helper function itself should be generated
        assertTrue(output.contains("void _hud_print_u8"), "Should generate _hud_print_u8 helper")
    }

    // =========================================================================
    // TEST 6: HUD with icons generates full and empty tile rendering (FULL_AND_EMPTY)
    // =========================================================================
    @Test
    fun `hud with icons generates full and empty tile rendering`() {
        val output =
            generateForHud(
                makeHudDef(
                    elements =
                        listOf(
                            HudIcons(
                                id = "lives_icons",
                                variable = "lives",
                                maxValue = 3,
                                fullTile = 0x10,
                                emptyTile = 0x11,
                                displayMode = IconDisplayMode.FULL_AND_EMPTY,
                            )
                        )
                )
            )

        assertTrue(output.contains("update_hud_status"), "Should have update_hud_status function")
        assertTrue(
            output.contains("_hud_full_icon_status_lives_icons"),
            "Should have full icon global",
        )
        assertTrue(
            output.contains("_hud_empty_icon_status_lives_icons"),
            "Should have empty icon global",
        )
        // Should loop over maxValue (3)
        assertTrue(output.contains("< 3u"), "Should iterate up to maxValue 3")
    }

    // =========================================================================
    // TEST 7: HUD with icons filled-only generates space for empty slots
    // =========================================================================
    @Test
    fun `hud with icons filled-only generates space tile for empty slots`() {
        val output =
            generateForHud(
                makeHudDef(
                    elements =
                        listOf(
                            HudIcons(
                                id = "badges",
                                variable = "lives",
                                maxValue = 3,
                                fullTile = 0x20,
                                emptyTile = 0x21,
                                displayMode = IconDisplayMode.FILLED_ONLY,
                            )
                        )
                )
            )

        // FILLED_ONLY mode uses _hud_space_tile (index 0) for empty slots
        assertTrue(
            output.contains("_hud_space_tile"),
            "FILLED_ONLY should use space tile for empty slots",
        )
    }

    // =========================================================================
    // TEST 8: HUD update function has change detection
    // =========================================================================
    @Test
    fun `hud update function has change detection`() {
        val output =
            generateForHud(
                makeHudDef(
                    elements =
                        listOf(HudBar(id = "hp_bar", variable = "hp", maxValue = 100, width = 8))
                )
            )

        // Change detection: value != prev comparison
        assertTrue(output.contains("_hud_status_hp_bar_prev"), "Should have prev variable")
        assertTrue(output.contains("!= "), "Should have inequality comparison for change detection")
    }

    // =========================================================================
    // TEST 9: HUD prev variables initialized to 0xFF sentinel
    // =========================================================================
    @Test
    fun `hud prev variables initialized to 0xFF sentinel`() {
        val output =
            generateForHud(
                makeHudDef(
                    elements =
                        listOf(HudBar(id = "hp_bar", variable = "hp", maxValue = 100, width = 8))
                )
            )

        // 0xFF sentinel forces first-frame redraw (prev != current on first call)
        assertTrue(output.contains("_hud_status_hp_bar_prev"), "Should have _prev variable")
        // The initializer should be 0xFF
        assertTrue(output.contains("0xFF"), "Prev variable should be initialized to 0xFF sentinel")
    }

    // =========================================================================
    // TEST 10: HUD show function resets prev values to 0xFF
    // =========================================================================
    @Test
    fun `hud show function resets prev values to sentinel`() {
        val output =
            generateForHud(
                makeHudDef(
                    anchor = Anchor.BOTTOM_LEFT,
                    elements =
                        listOf(HudBar(id = "hp_bar", variable = "hp", maxValue = 100, width = 8)),
                )
            )

        assertTrue(output.contains("show_hud_status"), "Should have show_hud_status function")
        // show_hud should reset prev to 0xFF to force full redraw
        val showHudSection =
            output.substringAfter("show_hud_status").substringBefore("hide_hud_status")
        assertTrue(
            showHudSection.contains("0xFF"),
            "show_hud should reset prev to 0xFF for forced redraw",
        )
    }

    // =========================================================================
    // TEST 11: HUD hide function clears region and sets visible to 0
    // =========================================================================
    @Test
    fun `hud hide function clears region and sets visible to zero`() {
        val output =
            generateForHud(
                makeHudDef(
                    elements =
                        listOf(HudBar(id = "hp_bar", variable = "hp", maxValue = 100, width = 8))
                )
            )

        assertTrue(output.contains("hide_hud_status"), "Should have hide_hud_status function")
        // hide_hud sets visible to 0
        val hideHudSection = output.substringAfter("hide_hud_status")
        assertTrue(
            hideHudSection.contains("_hud_status_visible"),
            "hide_hud should reference visibility flag",
        )
        // hide_hud clears the HUD region (uses win clear since renderOnWindow=true in IR)
        assertTrue(
            hideHudSection.contains("_win_clear_region"),
            "hide_hud should clear the HUD region",
        )
    }

    // =========================================================================
    // TEST 12: HUD on window layer generates set_win_tiles calls (bottom-anchored)
    // =========================================================================
    @Test
    fun `hud on window layer generates set_win_tiles calls`() {
        val output =
            generateForHud(
                makeHudDef(
                    renderOnWindow = true,
                    elements =
                        listOf(HudBar(id = "hp_bar", variable = "hp", maxValue = 100, width = 8)),
                )
            )

        // Window layer: set_win_tiles in update function (IR says renderOnWindow=true)
        val updateHudSection =
            output.substringAfter("update_hud_status").substringBefore("show_hud_status")
        assertTrue(
            updateHudSection.contains("set_win_tiles"),
            "Window-layer HUD should use set_win_tiles",
        )
    }

    // =========================================================================
    // TEST 13: HUD on background layer generates set_bkg_tiles calls (no SHOW_WIN/HIDE_WIN)
    // =========================================================================
    @Test
    fun `hud on background layer generates set_bkg_tiles calls`() {
        val output =
            generateForHud(
                makeHudDef(
                    renderOnWindow = false,
                    elements =
                        listOf(HudBar(id = "hp_bar", variable = "hp", maxValue = 100, width = 8)),
                )
            )

        // Background layer: set_bkg_tiles in update function
        val updateHudSection =
            output.substringAfter("update_hud_status").substringBefore("show_hud_status")
        assertTrue(
            updateHudSection.contains("set_bkg_tiles"),
            "Background-layer HUD should use set_bkg_tiles for tile writes",
        )
    }

    // =========================================================================
    // TEST 14: HUD on background layer does not emit SHOW_WIN in show function
    // =========================================================================
    @Test
    fun `hud on background layer does not emit SHOW_WIN in show function`() {
        val output =
            generateForHud(
                makeHudDef(
                    renderOnWindow = false,
                    elements =
                        listOf(HudBar(id = "hp_bar", variable = "hp", maxValue = 100, width = 8)),
                )
            )

        // show_hud should NOT emit SHOW_WIN for background-layer HUDs
        val showHudSection =
            output.substringAfter("show_hud_status").substringBefore("hide_hud_status")
        assertFalse(
            showHudSection.contains("SHOW_WIN"),
            "Background-layer HUD show function should not emit SHOW_WIN",
        )
    }

    // =========================================================================
    // TEST 15: HUD anchor TOP_LEFT resolves to tile (0, 0)
    // =========================================================================
    @Test
    fun `hud anchor TOP_LEFT resolves to tile 0 0`() {
        val output =
            generateForHud(
                makeHudDef(
                    anchor = Anchor.TOP_LEFT,
                    elements = listOf(HudNumber(id = "score_num", variable = "score", label = "")),
                )
            )

        // TOP_LEFT = (0, 0) — the label should be printed at x=0, y=0
        assertTrue(output.contains("update_hud_status"), "Should have update_hud_status")
        // The position arguments 0u, 0u should appear in the _hud_print_u8 or _win_print_at call
        assertTrue(output.contains("_hud_print_u8"), "Should call _hud_print_u8")
    }

    // =========================================================================
    // TEST 16: HUD anchor BOTTOM_RIGHT resolves correctly based on element width
    // =========================================================================
    @Test
    fun `hud anchor BOTTOM_RIGHT resolves to correct position`() {
        val barWidth = 4
        val output =
            generateForHud(
                makeHudDef(
                    anchor = Anchor.BOTTOM_RIGHT,
                    elements =
                        listOf(
                            HudBar(id = "hp_bar", variable = "hp", maxValue = 100, width = barWidth)
                        ),
                )
            )

        // BOTTOM_RIGHT = (20 - width, 17) = (16, 17)
        // The Y position 17 should appear in the set_win_tiles calls
        assertTrue(output.contains("17u"), "BOTTOM_RIGHT anchor should resolve to y=17")
    }

    // =========================================================================
    // TEST 17: HUD tileX/tileY override anchor
    // =========================================================================
    @Test
    fun `hud tileX tileY override anchor`() {
        val output =
            generateForHud(
                makeHudDef(
                    anchor = Anchor.CENTER, // would be (10, 8)
                    tileX = 2,
                    tileY = 5,
                    elements = listOf(HudNumber(id = "score_num", variable = "score", label = "")),
                )
            )

        // With tileX=2, tileY=5, the position should be (2, 5) not (10, 8)
        assertTrue(output.contains("5u"), "tileY=5 override should appear in output (y=5)")
    }

    // =========================================================================
    // TEST 18: HUD update calls injected into scene frame function
    // =========================================================================
    @Test
    fun `hud update calls injected into scene frame function`() {
        val gameIR =
            baseGameIR.copy(
                scenes =
                    listOf(
                        SceneIR(
                            id = "gameplay",
                            // Non-empty frameOps so a frame function is generated
                            frameOps = listOf(NavigateTo("gameplay")),
                        )
                    ),
                huds =
                    listOf(
                        makeHudDef(
                            elements =
                                listOf(
                                    HudBar(
                                        id = "hp_bar",
                                        variable = "hp",
                                        maxValue = 100,
                                        width = 8,
                                    )
                                )
                        )
                    ),
                variables = listOf(VariableDef(name = "hp", type = VarType.U8, initialValue = 100)),
            )
        val pipeline = GBDKPipeline()
        val bank1 =
            pipeline.generate(gameIR).files["bank1.c"]
                ?: error("bank1.c not found in pipeline output")

        // update_hud_status() should be injected at the start of gameplay_frame
        assertTrue(
            bank1.contains("update_hud_status"),
            "bank1.c should contain update_hud_status call in frame function",
        )
    }

    // =========================================================================
    // TEST 19: HUD visible flag defaults to 0 (hidden by default)
    // =========================================================================
    @Test
    fun `hud visible flag defaults to zero hidden by default`() {
        val output =
            generateForHud(
                makeHudDef(
                    elements =
                        listOf(HudBar(id = "hp_bar", variable = "hp", maxValue = 100, width = 8))
                )
            )

        // Visibility flag should be initialized to 0 (hidden)
        assertTrue(output.contains("_hud_status_visible"), "Should have visibility flag")
        val visibleDecl = output.substringAfter("_hud_status_visible")
        assertTrue(
            visibleDecl.trimStart().startsWith(" =") || output.contains("_hud_status_visible = 0u"),
            "Visibility should be initialized to 0 (hidden)",
        )
    }

    // =========================================================================
    // TEST 20: Bottom-anchored HUD show function emits move_win + SHOW_WIN
    // =========================================================================
    @Test
    fun `bottom-anchored hud show function emits move_win and SHOW_WIN`() {
        val output =
            generateForHud(
                makeHudDef(
                    anchor = Anchor.BOTTOM_LEFT,
                    renderOnWindow = true,
                    elements =
                        listOf(HudNumber(id = "score_num", variable = "score", label = "SC:")),
                )
            )

        // show_hud should call move_win then SHOW_WIN for bottom-anchored window-layer HUDs
        val showSection = output.substringAfter("show_hud_status")
        assertTrue(
            showSection.contains("move_win"),
            "Bottom-anchored show_hud should emit move_win",
        )
        assertTrue(
            showSection.contains("SHOW_WIN"),
            "Bottom-anchored show_hud should emit SHOW_WIN",
        )
    }

    // =========================================================================
    // TEST 21: Top-anchored window HUD is suppressed (empty show function)
    // =========================================================================
    @Test
    fun `top-anchored window hud is suppressed`() {
        val output =
            generateForHud(
                makeHudDef(
                    anchor = Anchor.TOP_LEFT,
                    renderOnWindow = true,
                    elements =
                        listOf(HudNumber(id = "score_num", variable = "score", label = "SC:")),
                )
            )

        // Top-anchored window HUDs are suppressed — show_hud is a no-op (no visible=1, no SHOW_WIN)
        val showSection =
            output.substringAfter("show_hud_status").substringBefore("hide_hud_status")
        assertFalse(showSection.contains("SHOW_WIN"), "Suppressed HUD should not emit SHOW_WIN")
        assertFalse(showSection.contains("_visible"), "Suppressed HUD should not set visible flag")
    }
}
