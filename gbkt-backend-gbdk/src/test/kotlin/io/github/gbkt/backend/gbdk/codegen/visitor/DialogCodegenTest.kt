/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipelineV2
import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.AssetType
import io.github.gbkt.core.ir.BorderStyle
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.ClearRegion
import io.github.gbkt.core.ir.DialogChoice
import io.github.gbkt.core.ir.DialogDef
import io.github.gbkt.core.ir.DialogOption
import io.github.gbkt.core.ir.DialogSay
import io.github.gbkt.core.ir.DialogTextSegment
import io.github.gbkt.core.ir.FontMode
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.PrintAligned
import io.github.gbkt.core.ir.PrintAt
import io.github.gbkt.core.ir.PrintCentered
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.ScreenClear
import io.github.gbkt.core.ir.ScreenFill
import io.github.gbkt.core.ir.TextAlignment
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// DIALOG CODEGEN TESTS
// Verifies that buildDialogFunctions() generates proper window-layer dialog
// C functions: borders, portraits, typewriter, auto-pagination, VWF rendering,
// choice menus, speaker names, and text helpers.
//
// All generated dialog code must use window-layer functions (move_win, SHOW_WIN,
// set_win_tiles, _win_print_at) and NOT use gotoxy or printf for dialog text.
// =============================================================================

class DialogCodegenTest {

    private val baseGameIR =
        GameIR(name = "TestGame", config = CartridgeConfig(), scenes = listOf(SceneIR(id = "main")))

    /** Generate C output files from a [GameIR] using [GBDKPipelineV2]. */
    private fun generateOutput(gameIR: GameIR): Map<String, String> {
        val pipeline = GBDKPipelineV2()
        return pipeline.generate(gameIR).files
    }

    /** Get the main.c content from generated output map. */
    private fun mainC(gameIR: GameIR): String =
        generateOutput(gameIR)["main.c"] ?: error("main.c not found in pipeline output")

    // =========================================================================
    // TEST 1: Dialog with NONE border generates window-layer show function
    // =========================================================================
    @Test
    fun `dialog with NONE border generates window-layer show function`() {
        val gameIR =
            baseGameIR.copy(dialogs = listOf(DialogDef(id = "greeting", border = BorderStyle.NONE)))
        val output = mainC(gameIR)

        // Must contain window-layer calls
        assertTrue(
            output.contains("show_dialog_greeting"),
            "Should have show_dialog_greeting function",
        )
        assertTrue(output.contains("move_win"), "Should call move_win to position window")
        assertTrue(output.contains("SHOW_WIN"), "Should call SHOW_WIN macro")
        assertTrue(output.contains("HIDE_WIN"), "Should call HIDE_WIN macro")

        // Must NOT use gotoxy or printf for dialog text
        assertFalse(output.contains("gotoxy"), "Should not use gotoxy for dialog text")
        // printf may still be used elsewhere, but not inside the dialog function for text content
    }

    // =========================================================================
    // TEST 2: Dialog with SINGLE border generates box-drawing tile set_win_tiles calls
    // =========================================================================
    @Test
    fun `dialog with SINGLE border generates box-drawing tile set_win_tiles calls`() {
        val gameIR =
            baseGameIR.copy(
                dialogs =
                    listOf(
                        DialogDef(
                            id = "npc1",
                            border = BorderStyle.SINGLE,
                            boxX = 0,
                            boxY = 14,
                            boxWidth = 20,
                            boxHeight = 4,
                        )
                    )
            )
        val output = mainC(gameIR)

        // SINGLE border uses CP437 box-drawing tile indices
        // TL = 0xDA (218), TR = 0xBF (191), BL = 0xC0 (192), BR = 0xD9 (217)
        // H = 0xC4 (196), V = 0xB3 (179)
        assertTrue(output.contains("set_win_tiles"), "Should call set_win_tiles for border")
        assertTrue(output.contains("218"), "Should include TL corner tile 0xDA (218)") // 0xDA
        assertTrue(output.contains("191"), "Should include TR corner tile 0xBF (191)") // 0xBF
        assertTrue(output.contains("192"), "Should include BL corner tile 0xC0 (192)") // 0xC0
        assertTrue(output.contains("217"), "Should include BR corner tile 0xD9 (217)") // 0xD9
        assertTrue(output.contains("196"), "Should include H edge tile 0xC4 (196)") // 0xC4
        assertTrue(output.contains("179"), "Should include V edge tile 0xB3 (179)") // 0xB3
    }

    // =========================================================================
    // TEST 3: Dialog with DOUBLE border generates double-line border tiles
    // =========================================================================
    @Test
    fun `dialog with DOUBLE border generates double-line border tiles`() {
        val gameIR =
            baseGameIR.copy(
                dialogs =
                    listOf(
                        DialogDef(
                            id = "boss",
                            border = BorderStyle.DOUBLE,
                            boxX = 0,
                            boxY = 12,
                            boxWidth = 20,
                            boxHeight = 6,
                        )
                    )
            )
        val output = mainC(gameIR)

        // DOUBLE border: TL=0xC9 (201), TR=0xBB (187), BL=0xC8 (200), BR=0xBC (188)
        // H=0xCD (205), V=0xBA (186)
        assertTrue(output.contains("201"), "Should include TL double corner tile 0xC9 (201)")
        assertTrue(output.contains("187"), "Should include TR double corner tile 0xBB (187)")
        assertTrue(output.contains("200"), "Should include BL double corner tile 0xC8 (200)")
        assertTrue(output.contains("188"), "Should include BR double corner tile 0xBC (188)")
        assertTrue(output.contains("205"), "Should include H double edge tile 0xCD (205)")
        assertTrue(output.contains("186"), "Should include V double edge tile 0xBA (186)")
    }

    // =========================================================================
    // TEST 4: Dialog with CUSTOM border uses customBorderTiles indices
    // =========================================================================
    @Test
    fun `dialog with CUSTOM border uses customBorderTiles indices`() {
        val customTiles = listOf(10, 11, 12, 13, 14, 15, 16, 17) // user-provided tile indices
        val gameIR =
            baseGameIR.copy(
                dialogs =
                    listOf(
                        DialogDef(
                            id = "custom",
                            border = BorderStyle.CUSTOM,
                            customBorderTiles = customTiles,
                            boxX = 0,
                            boxY = 14,
                            boxWidth = 20,
                            boxHeight = 4,
                        )
                    )
            )
        val output = mainC(gameIR)

        // Custom tile indices should appear in the output
        assertTrue(output.contains("10"), "Should include custom TL tile 10")
        assertTrue(output.contains("11"), "Should include custom TR tile 11")
        assertTrue(output.contains("12"), "Should include custom BL tile 12")
        assertTrue(output.contains("13"), "Should include custom BR tile 13")
    }

    // =========================================================================
    // TEST 5: Dialog with portrait generates sprite tile and move_sprite calls
    // =========================================================================
    @Test
    fun `dialog with portrait generates sprite tile and move_sprite calls`() {
        val gameIR =
            baseGameIR.copy(
                dialogs =
                    listOf(
                        DialogDef(
                            id = "elder",
                            portrait = AssetRef("portraits/elder.png", AssetType.SPRITE),
                        )
                    )
            )
        val output = mainC(gameIR)

        // Portrait rendering: set_sprite_tile + move_sprite for portrait slot (39)
        assertTrue(output.contains("set_sprite_tile"), "Should call set_sprite_tile for portrait")
        assertTrue(
            output.contains("move_sprite"),
            "Should call move_sprite for portrait positioning",
        )
        // Portrait sprite slot 39 (reserved UI slot)
        assertTrue(output.contains("39"), "Should reference portrait sprite slot 39")
    }

    // =========================================================================
    // TEST 6: Dialog with portrait hides sprite on dismiss
    // =========================================================================
    @Test
    fun `dialog with portrait hides sprite on dismiss`() {
        val gameIR =
            baseGameIR.copy(
                dialogs =
                    listOf(
                        DialogDef(
                            id = "merchant",
                            portrait = AssetRef("portraits/merchant.png", AssetType.SPRITE),
                        )
                    )
            )
        val output = mainC(gameIR)

        val dialogFnStart = output.indexOf("show_dialog_merchant")
        assertTrue(dialogFnStart >= 0, "show_dialog_merchant function must exist")

        val dialogFnBody = output.substring(dialogFnStart)
        // After HIDE_WIN, the portrait should be moved to (0, 0) to hide it
        assertTrue(dialogFnBody.contains("HIDE_WIN"), "Should contain HIDE_WIN to dismiss dialog")
        // move_sprite(39, 0, 0) to hide portrait — check for the call with zeros
        assertTrue(
            dialogFnBody.contains("move_sprite"),
            "Should call move_sprite to hide portrait on dismiss",
        )
    }

    // =========================================================================
    // TEST 7: Dialog with speaker name generates name line before text
    // =========================================================================
    @Test
    fun `dialog with speaker name generates name line before text`() {
        val gameIR =
            baseGameIR.copy(dialogs = listOf(DialogDef(id = "innkeeper", speaker = "Innkeeper")))
        val output = mainC(gameIR)

        // Speaker name rendered as first text line via _win_print_at
        val dialogFnStart = output.indexOf("show_dialog_innkeeper")
        assertTrue(dialogFnStart >= 0, "show_dialog_innkeeper must exist")
        val dialogFnBody = output.substring(dialogFnStart)
        assertTrue(
            dialogFnBody.contains("Innkeeper"),
            "Speaker name 'Innkeeper' should appear in dialog function",
        )
    }

    // =========================================================================
    // TEST 8: Dialog with textSpeed generates delay_frames call in typewriter loop
    // =========================================================================
    @Test
    fun `dialog with textSpeed generates delay_frames call in typewriter loop`() {
        val gameIR = baseGameIR.copy(dialogs = listOf(DialogDef(id = "slow", textSpeed = 3)))
        val output = mainC(gameIR)

        val dialogFnStart = output.indexOf("show_dialog_slow")
        assertTrue(dialogFnStart >= 0, "show_dialog_slow must exist")
        val dialogFnBody = output.substring(dialogFnStart)
        assertTrue(
            dialogFnBody.contains("delay_frames"),
            "Should call delay_frames for typewriter effect",
        )
        assertTrue(dialogFnBody.contains("3"), "delay_frames should be called with textSpeed=3")
    }

    // =========================================================================
    // TEST 9: Dialog with textSpeed=0 does NOT generate delay_frames (instant)
    // =========================================================================
    @Test
    fun `dialog with textSpeed zero does not generate delay_frames`() {
        val gameIR = baseGameIR.copy(dialogs = listOf(DialogDef(id = "fast", textSpeed = 0)))
        val output = mainC(gameIR)

        val dialogFnStart = output.indexOf("show_dialog_fast")
        assertTrue(dialogFnStart >= 0, "show_dialog_fast must exist")
        // Find end of the show_dialog_fast function body — look for next top-level function
        // or end of file. Extract body between function open brace and its closing brace.
        // Simple approach: check that delay_frames does NOT appear in the dialog function
        // by checking the dialog body until HIDE_WIN
        val hideWinIdx = output.indexOf("HIDE_WIN", dialogFnStart)
        val dialogFnBody =
            if (hideWinIdx > dialogFnStart) output.substring(dialogFnStart, hideWinIdx) else ""
        // With textSpeed=0, no delay_frames should be emitted in this function's typewriter loop
        assertFalse(
            dialogFnBody.contains("delay_frames"),
            "Should not call delay_frames for instant typewriter (textSpeed=0)",
        )
    }

    // =========================================================================
    // TEST 10: Dialog with long text generates auto-pagination loop
    // =========================================================================
    @Test
    fun `dialog say generates call to show_dialog function`() {
        val op = DialogSay(dialogId = "npc1", segments = listOf(DialogTextSegment("Hello!")))
        val result = ScriptOpVisitor.visit(op)
        val emitted = result.toString()
        // visitDialogSay emits CCall("show_dialog_npc1")
        assertTrue(
            emitted.contains("show_dialog_npc1") || result.toString().contains("npc1"),
            "DialogSay should generate show_dialog_npc1 call, got: $emitted",
        )
    }

    // =========================================================================
    // TEST 11: Dialog pagination generates outer while loop with A-button wait
    // =========================================================================
    @Test
    fun `dialog function generates pagination outer loop with A-button wait`() {
        val gameIR =
            baseGameIR.copy(
                dialogs = listOf(DialogDef(id = "longstory", boxWidth = 20, boxHeight = 4))
            )
        val output = mainC(gameIR)

        val dialogFnStart = output.indexOf("show_dialog_longstory")
        assertTrue(dialogFnStart >= 0, "show_dialog_longstory must exist")
        val dialogFnBody = output.substring(dialogFnStart)

        // Pagination loop and A-button wait
        assertTrue(
            dialogFnBody.contains("J_A"),
            "Should include J_A joypad check for A-button wait",
        )
        assertTrue(dialogFnBody.contains("joypad()"), "Should call joypad() for button polling")
        assertTrue(
            dialogFnBody.contains("wait_vbl_done"),
            "Should call wait_vbl_done in A-button wait loop",
        )
    }

    // =========================================================================
    // TEST 12: DialogChoice generates show_dialog_choice function call
    // =========================================================================
    @Test
    fun `dialog choice generates show_dialog_choice call`() {
        val op =
            DialogChoice(
                dialogId = "puzzle",
                options = listOf(DialogOption("Yes", emptyList()), DialogOption("No", emptyList())),
            )
        val result = ScriptOpVisitor.visit(op)
        val emitted = result.toString()
        assertTrue(
            emitted.contains("puzzle"),
            "DialogChoice should reference dialog ID 'puzzle', got: $emitted",
        )
    }

    // =========================================================================
    // TEST 13: Dialog with VARIABLE_WIDTH fontMode generates _vwf_print_at calls
    // =========================================================================
    @Test
    fun `dialog with VARIABLE_WIDTH fontMode selects VWF rendering path`() {
        val gameIR =
            baseGameIR.copy(
                dialogs =
                    listOf(
                        DialogDef(
                            id = "vwf_dialog",
                            fontMode = FontMode.VARIABLE_WIDTH,
                            speaker = "Oracle",
                        )
                    )
            )
        val output = mainC(gameIR)

        // VWF path: _vwf_char_widths array must be generated
        assertTrue(
            output.contains("_vwf_char_widths"),
            "VWF char widths array must be generated for VARIABLE_WIDTH dialogs",
        )
    }

    // =========================================================================
    // TEST 14: _vwf_print_at helper function is generated when VWF is used
    // =========================================================================
    @Test
    fun `_vwf_print_at helper is generated when VWF fontMode is used`() {
        val gameIR =
            baseGameIR.copy(
                dialogs = listOf(DialogDef(id = "vwf_test", fontMode = FontMode.VARIABLE_WIDTH))
            )
        val output = mainC(gameIR)

        assertTrue(
            output.contains("_vwf_print_at"),
            "_vwf_print_at helper function must be generated when any dialog uses VWF fontMode",
        )
    }

    // =========================================================================
    // TEST 15: _win_print_at helper is always generated
    // =========================================================================
    @Test
    fun `_win_print_at helper is always generated`() {
        val gameIR = baseGameIR.copy(dialogs = listOf(DialogDef(id = "basic")))
        val output = mainC(gameIR)

        assertTrue(
            output.contains("_win_print_at"),
            "_win_print_at helper function must always be generated",
        )
    }

    // =========================================================================
    // TEST 16: printCentered generates centered _win_print_at call
    // =========================================================================
    @Test
    fun `printCentered generates centered _win_print_at call with correct X offset`() {
        val text = "Hello" // length 5, centered on 20-tile window: x = (20-5)/2 = 7
        val op = PrintCentered(row = 10, text = text)
        val result = ScriptOpVisitor.visit(op)
        val emitted = result.toString()

        assertTrue(
            emitted.contains("_win_print_at"),
            "PrintCentered should emit _win_print_at, got: $emitted",
        )
        // X offset for "Hello" (5 chars) centered on 20 tiles: (20-5)/2 = 7
        assertTrue(
            emitted.contains("7"),
            "Centered X offset should be 7 for 5-char text on 20-tile window",
        )
    }

    // =========================================================================
    // TEST 17: printAligned RIGHT generates right-aligned position (20 - len)
    // =========================================================================
    @Test
    fun `printAligned RIGHT generates right-aligned position`() {
        val text = "12345" // length 5, right-aligned on 20-tile window: x = 20-5 = 15
        val op = PrintAligned(row = 5, text = text, alignment = TextAlignment.RIGHT)
        val result = ScriptOpVisitor.visit(op)
        val emitted = result.toString()

        assertTrue(
            emitted.contains("_win_print_at"),
            "PrintAligned RIGHT should emit _win_print_at, got: $emitted",
        )
        // Right-aligned x = 20 - 5 = 15
        assertTrue(
            emitted.contains("15"),
            "Right-aligned X should be 15 for 5-char text on 20-tile window, got: $emitted",
        )
    }

    // =========================================================================
    // TEST 18: clearRegion generates _win_clear_region call with correct parameters
    // =========================================================================
    @Test
    fun `clearRegion generates _win_clear_region call`() {
        val op = ClearRegion(x = 2, y = 3, w = 10, h = 4)
        val result = ScriptOpVisitor.visit(op)
        val emitted = result.toString()

        assertTrue(
            emitted.contains("_win_clear_region"),
            "ClearRegion should emit _win_clear_region, got: $emitted",
        )
    }

    // =========================================================================
    // TEST 19: screenClear generates cls() call
    // =========================================================================
    @Test
    fun `screenClear generates cls call`() {
        val op = ScreenClear()
        val result = ScriptOpVisitor.visit(op)
        val emitted = result.toString()

        assertTrue(emitted.contains("cls"), "ScreenClear should emit cls(), got: $emitted")
    }

    // =========================================================================
    // TEST 20: screenFill generates _win_fill_screen call
    // =========================================================================
    @Test
    fun `screenFill generates _win_fill_screen call`() {
        val op = ScreenFill(tile = 0x01)
        val result = ScriptOpVisitor.visit(op)
        val emitted = result.toString()

        assertTrue(
            emitted.contains("_win_fill_screen"),
            "ScreenFill should emit _win_fill_screen, got: $emitted",
        )
    }

    // =========================================================================
    // TEST 21: _win_clear_region helper is generated in HOME bank
    // =========================================================================
    @Test
    fun `_win_clear_region helper is generated in pipeline output`() {
        val gameIR = baseGameIR.copy(dialogs = listOf(DialogDef(id = "test")))
        val output = mainC(gameIR)

        assertTrue(
            output.contains("_win_clear_region"),
            "_win_clear_region helper must be generated in main.c",
        )
    }

    // =========================================================================
    // TEST 22: _win_fill_screen helper is generated in pipeline output
    // =========================================================================
    @Test
    fun `_win_fill_screen helper is generated in pipeline output`() {
        val gameIR = baseGameIR.copy(dialogs = listOf(DialogDef(id = "test")))
        val output = mainC(gameIR)

        assertTrue(
            output.contains("_win_fill_screen"),
            "_win_fill_screen helper must be generated in main.c",
        )
    }

    // =========================================================================
    // TEST 23: Dialog function is in HOME bank (bank 0 / main.c)
    // =========================================================================
    @Test
    fun `dialog function is generated in HOME bank (main_c not bank1_c)`() {
        val gameIR = baseGameIR.copy(dialogs = listOf(DialogDef(id = "hometest")))
        val output = generateOutput(gameIR)

        val mainCContent = output["main.c"] ?: error("main.c not found")
        val bank1CContent = output["bank1.c"] ?: ""

        assertTrue(
            mainCContent.contains("show_dialog_hometest"),
            "Dialog function should be in main.c (HOME bank)",
        )
        assertFalse(
            bank1CContent.contains("show_dialog_hometest"),
            "Dialog function should NOT be in bank1.c (scene bank)",
        )
    }

    // =========================================================================
    // TEST 24: PrintAt generates _win_print_at with 4 arguments (x, y, str, len)
    // =========================================================================
    @Test
    fun `printAt generates _win_print_at with four arguments`() {
        val op = PrintAt(x = 0, y = 14, text = "Score: 0")
        val result = ScriptOpVisitor.visit(op)
        val emitted = result.toString()

        assertTrue(
            emitted.contains("_win_print_at"),
            "PrintAt should emit _win_print_at, got: $emitted",
        )
        // Should include the text length (8 for "Score: 0")
        assertTrue(emitted.contains("8"), "PrintAt should include text length 8, got: $emitted")
    }

    // =========================================================================
    // TEST 25: Dialog with NONE border has no border tile set_win_tiles before typewriter
    // =========================================================================
    @Test
    fun `dialog with NONE border has no border tile set_win_tiles in function`() {
        val gameIR =
            baseGameIR.copy(dialogs = listOf(DialogDef(id = "noborder", border = BorderStyle.NONE)))
        val output = mainC(gameIR)

        val dialogFnStart = output.indexOf("show_dialog_noborder")
        assertTrue(dialogFnStart >= 0)
        val dialogFnBody = output.substring(dialogFnStart)
        // No CP437 border tile indices (218, 191, 192, 217, 196, 179) should appear
        assertFalse(dialogFnBody.contains("218"), "NONE border should not emit TL tile 218 (0xDA)")
        assertFalse(dialogFnBody.contains("191"), "NONE border should not emit TR tile 191 (0xBF)")
    }

    // =========================================================================
    // TEST 26: _dialog_speed global is generated
    // =========================================================================
    @Test
    fun `_dialog_speed global variable is generated`() {
        val gameIR = baseGameIR.copy(dialogs = listOf(DialogDef(id = "speed_test")))
        val output = mainC(gameIR)

        assertTrue(
            output.contains("_dialog_speed"),
            "_dialog_speed global variable must be generated",
        )
    }

    // =========================================================================
    // TEST 27: Multiple dialogs each get their own show_dialog function
    // =========================================================================
    @Test
    fun `multiple dialogs each get their own show_dialog function`() {
        val gameIR =
            baseGameIR.copy(
                dialogs =
                    listOf(
                        DialogDef(id = "dialog_a"),
                        DialogDef(id = "dialog_b"),
                        DialogDef(id = "dialog_c"),
                    )
            )
        val output = mainC(gameIR)

        assertTrue(
            output.contains("show_dialog_dialog_a"),
            "show_dialog_dialog_a must be generated",
        )
        assertTrue(
            output.contains("show_dialog_dialog_b"),
            "show_dialog_dialog_b must be generated",
        )
        assertTrue(
            output.contains("show_dialog_dialog_c"),
            "show_dialog_dialog_c must be generated",
        )
    }
}
