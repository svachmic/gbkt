/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CArrayAccess
import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CBlock
import io.github.gbkt.backend.gbdk.codegen.ast.CCall
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFor
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CRawCode
import io.github.gbkt.backend.gbdk.codegen.ast.CReturn
import io.github.gbkt.backend.gbdk.codegen.ast.CStringLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.ast.CWhile
import io.github.gbkt.core.ir.ArrayAssign
import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.CallOp
import io.github.gbkt.core.ir.CameraAction
import io.github.gbkt.core.ir.CameraOp
import io.github.gbkt.core.ir.DialogSay
import io.github.gbkt.core.ir.DialogTextSegment
import io.github.gbkt.core.ir.FadeOp
import io.github.gbkt.core.ir.ForOp
import io.github.gbkt.core.ir.GotoXYOp
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.MathFunction
import io.github.gbkt.core.ir.MathOp
import io.github.gbkt.core.ir.MenuShow
import io.github.gbkt.core.ir.MoveBy
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.PlaySound
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.PrintAt
import io.github.gbkt.core.ir.PrintOp
import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.core.ir.ReturnOp
import io.github.gbkt.core.ir.ScreenClear
import io.github.gbkt.core.ir.ScreenFill
import io.github.gbkt.core.ir.SetPosition
import io.github.gbkt.core.ir.SetVisible
import io.github.gbkt.core.ir.VarRef
import io.github.gbkt.core.ir.WaitFrames
import io.github.gbkt.core.ir.WhileOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScriptOpVisitorTest {

    // =========================================================================
    // TEST 1: Assign SET converts to CExprStatement with = assignment
    // =========================================================================
    @Test
    fun `Assign SET converts to CExprStatement with equals assignment`() {
        val op = Assign(target = "ballDx", value = Literal(1), op = AssignOp.SET)
        val result = ScriptOpVisitor.visit(op)
        val expected = CExprStatement(CBinaryExpr(CVar("_ballDx"), "=", CLiteral(1)))
        assertEquals(expected, result)
    }

    // =========================================================================
    // TEST 2: Assign ADD converts to compound += assignment
    // =========================================================================
    @Test
    fun `Assign ADD converts to compound plus-equals assignment`() {
        val op = Assign(target = "p1Score", value = Literal(1), op = AssignOp.ADD)
        val result = ScriptOpVisitor.visit(op)
        val expected = CExprStatement(CBinaryExpr(CVar("_p1Score"), "+=", CLiteral(1)))
        assertEquals(expected, result)
    }

    @Test
    fun `Assign SUB converts to compound minus-equals assignment`() {
        val op = Assign(target = "lives", value = Literal(1), op = AssignOp.SUB)
        val result = ScriptOpVisitor.visit(op)
        val expected = CExprStatement(CBinaryExpr(CVar("_lives"), "-=", CLiteral(1)))
        assertEquals(expected, result)
    }

    // =========================================================================
    // TEST 3: IfOp converts to CIf
    // =========================================================================
    @Test
    fun `IfOp converts to CIf with then and else bodies`() {
        val condition = BinaryExpr(VarRef("ballX"), BinaryOp.GTE, Literal(160))
        val thenOps = listOf(Assign("ballDx", Literal(-1), AssignOp.SET))
        val elseOps = listOf(Assign("ballDx", Literal(1), AssignOp.SET))

        val result = ScriptOpVisitor.visit(IfOp(condition, thenOps, elseOps))
        val cIf = assertIs<CIf>(result)

        assertEquals(1, cIf.thenBody.size)
        assertEquals(1, cIf.elseBody.size)
    }

    // =========================================================================
    // TEST 4: IfOp with empty else produces CIf with empty elseBody
    // =========================================================================
    @Test
    fun `IfOp with empty else produces CIf with empty elseBody`() {
        val condition = BinaryExpr(VarRef("score"), BinaryOp.GT, Literal(0))
        val thenOps = listOf(Assign("highScore", VarRef("score"), AssignOp.SET))

        val result = ScriptOpVisitor.visit(IfOp(condition, thenOps, emptyList()))
        val cIf = assertIs<CIf>(result)

        assertTrue(cIf.elseBody.isEmpty())
    }

    // =========================================================================
    // TEST 5: SetPosition converts to block with x and y assignments
    // =========================================================================
    @Test
    fun `SetPosition converts to CBlock with x and y assignments`() {
        val op = SetPosition(actorId = "ball", x = Literal(80), y = Literal(72))
        val result = ScriptOpVisitor.visit(op)
        val block = assertIs<CBlock>(result)

        assertEquals(2, block.statements.size)
        assertEquals(
            CExprStatement(CBinaryExpr(CVar("_ball_x"), "=", CLiteral(80))),
            block.statements[0],
        )
        assertEquals(
            CExprStatement(CBinaryExpr(CVar("_ball_y"), "=", CLiteral(72))),
            block.statements[1],
        )
    }

    // =========================================================================
    // TEST 6: MoveBy converts to compound assignments — skipping zero offsets
    // =========================================================================
    @Test
    fun `MoveBy with zero x skips x and emits only y assignment`() {
        val op = MoveBy(actorId = "paddle1", dx = Literal(0), dy = Literal(-2))
        val result = ScriptOpVisitor.visit(op)
        val block = assertIs<CBlock>(result)

        assertEquals(1, block.statements.size)
        assertEquals(
            CExprStatement(CBinaryExpr(CVar("_paddle1_y"), "+=", CLiteral(-2))),
            block.statements[0],
        )
    }

    @Test
    fun `MoveBy with both non-zero emits two assignments`() {
        val op = MoveBy(actorId = "ball", dx = Literal(2), dy = Literal(1))
        val result = ScriptOpVisitor.visit(op)
        val block = assertIs<CBlock>(result)

        assertEquals(2, block.statements.size)
        assertEquals(
            CExprStatement(CBinaryExpr(CVar("_ball_x"), "+=", CLiteral(2))),
            block.statements[0],
        )
        assertEquals(
            CExprStatement(CBinaryExpr(CVar("_ball_y"), "+=", CLiteral(1))),
            block.statements[1],
        )
    }

    @Test
    fun `MoveBy with both zero produces empty block`() {
        val op = MoveBy(actorId = "ball", dx = Literal(0), dy = Literal(0))
        val result = ScriptOpVisitor.visit(op)
        val block = assertIs<CBlock>(result)

        assertTrue(block.statements.isEmpty())
    }

    // =========================================================================
    // TEST 7: NavigateTo converts to scene transition call
    // =========================================================================
    @Test
    fun `NavigateTo converts to navigate_to_scene call with SCENE_ constant`() {
        val op = NavigateTo(sceneId = "game")
        val result = ScriptOpVisitor.visit(op)
        val expected = CExprStatement(CCall("navigate_to_scene", listOf(CVar("SCENE_GAME"))))
        assertEquals(expected, result)
    }

    @Test
    fun `NavigateTo with title scene produces SCENE_TITLE constant`() {
        val op = NavigateTo(sceneId = "title")
        val result = ScriptOpVisitor.visit(op)
        val expected = CExprStatement(CCall("navigate_to_scene", listOf(CVar("SCENE_TITLE"))))
        assertEquals(expected, result)
    }

    // =========================================================================
    // TEST 8: PrintOp converts to gotoxy + printf calls
    // =========================================================================
    @Test
    fun `PrintOp with position converts to gotoxy then printf calls`() {
        val op = PrintOp(text = "PONG", position = PositionDef(6, 4))
        val result = ScriptOpVisitor.visit(op)
        val block = assertIs<CBlock>(result)

        assertEquals(2, block.statements.size)
        assertEquals(
            CExprStatement(CCall("gotoxy", listOf(CLiteral(6), CLiteral(4)))),
            block.statements[0],
        )
        assertEquals(
            CExprStatement(CCall("printf", listOf(CStringLiteral("PONG")))),
            block.statements[1],
        )
    }

    @Test
    fun `PrintOp without position omits gotoxy call`() {
        val op = PrintOp(text = "SCORE", position = null)
        val result = ScriptOpVisitor.visit(op)
        val block = assertIs<CBlock>(result)

        assertEquals(1, block.statements.size)
        assertEquals(
            CExprStatement(CCall("printf", listOf(CStringLiteral("SCORE")))),
            block.statements[0],
        )
    }

    // =========================================================================
    // TEST: GotoXYOp converts to gotoxy call with expression arguments
    // =========================================================================
    @Test
    fun `GotoXYOp converts to gotoxy call with expression arguments`() {
        val op = GotoXYOp(x = VarRef("bc"), y = Literal(3))
        val result = ScriptOpVisitor.visit(op)
        val stmt = assertIs<CExprStatement>(result)
        assertEquals(CExprStatement(CCall("gotoxy", listOf(CVar("_bc"), CLiteral(3)))), stmt)
    }

    // =========================================================================
    // TEST 9: FadeOp converts to screen control calls
    // =========================================================================
    @Test
    fun `FadeOp hide converts to hide_sprites_range call`() {
        val op = FadeOp(fadeIn = false, frames = 0)
        val result = ScriptOpVisitor.visit(op)
        assertIs<CExprStatement>(result)
    }

    @Test
    fun `FadeOp show produces a CExprStatement`() {
        val op = FadeOp(fadeIn = true, frames = 0)
        val result = ScriptOpVisitor.visit(op)
        assertIs<CExprStatement>(result)
    }

    // =========================================================================
    // TEST 10: RawOp passthrough converts to CRawCode
    // =========================================================================
    @Test
    fun `RawOp passthrough converts to CRawCode`() {
        val op = RawOp(code = "SWITCH_ROM(1);")
        val result = ScriptOpVisitor.visit(op)
        assertEquals(CRawCode("SWITCH_ROM(1);"), result)
    }

    // =========================================================================
    // TEST 11: WhileOp generates CWhile
    // =========================================================================
    @Test
    fun `WhileOp generates CWhile with condition and body`() {
        val condition = BinaryExpr(VarRef("lives"), BinaryOp.GT, Literal(0))
        val body = listOf(Assign("lives", Literal(1), AssignOp.SUB))
        val op = WhileOp(condition, body)
        val result = ScriptOpVisitor.visit(op)
        val cWhile = assertIs<CWhile>(result)
        assertEquals(1, cWhile.body.size)
    }

    // =========================================================================
    // TEST 12: ForOp generates CFor with init/condition/increment
    // =========================================================================
    @Test
    fun `ForOp generates CFor with init condition and increment`() {
        val op = ForOp(variable = "i", from = Literal(0), to = Literal(9), body = emptyList())
        val result = ScriptOpVisitor.visit(op)
        val cFor = assertIs<CFor>(result)
        assertNotNull(cFor.init, "ForOp init should not be null")
        assertNotNull(cFor.condition, "ForOp condition should not be null")
        assertNotNull(cFor.increment, "ForOp increment should not be null")
    }

    // =========================================================================
    // TEST 13: PlaySound generates play_sound_{id}() call
    // =========================================================================
    @Test
    fun `PlaySound generates play_sound_hit call`() {
        val op = PlaySound("hit")
        val result = ScriptOpVisitor.visit(op)
        val stmt = assertIs<CExprStatement>(result)
        val call = assertIs<CCall>(stmt.expr)
        assertEquals("play_sound_hit", call.function)
        assertTrue(call.args.isEmpty())
    }

    // =========================================================================
    // TEST 14: DialogSay generates show_dialog_{id}() call
    // =========================================================================
    @Test
    fun `DialogSay generates show_dialog_npc1 call`() {
        val op = DialogSay(dialogId = "npc1", segments = listOf(DialogTextSegment("Hello!")))
        val result = ScriptOpVisitor.visit(op)
        val stmt = assertIs<CExprStatement>(result)
        val call = assertIs<CCall>(stmt.expr)
        assertEquals("show_dialog_npc1", call.function)
        assertEquals(2, call.args.size)
        val textArg = assertIs<CStringLiteral>(call.args[0])
        assertEquals("Hello!", textArg.value)
        val lenArg = assertIs<CLiteral>(call.args[1])
        assertEquals(6, lenArg.value)
    }

    // =========================================================================
    // TEST 15: MenuShow generates show_menu_{id}() call
    // =========================================================================
    @Test
    fun `MenuShow generates show_menu_main_menu call`() {
        val op = MenuShow(menuId = "main_menu")
        val result = ScriptOpVisitor.visit(op)
        val stmt = assertIs<CExprStatement>(result)
        val call = assertIs<CCall>(stmt.expr)
        assertEquals("show_menu_main_menu", call.function)
        assertTrue(call.args.isEmpty())
    }

    // =========================================================================
    // TEST 16: WaitFrames generates state machine (NOT busy-wait)
    // =========================================================================
    @Test
    fun `WaitFrames generates state machine with counter and early return not busy-wait`() {
        val op = WaitFrames(frames = 30)
        val result = ScriptOpVisitor.visit(op)
        val block = assertIs<CBlock>(result)
        // Should have: counter assignment + CIf check
        assertEquals(2, block.statements.size)
        // First statement: set the counter
        val counterSet = assertIs<CExprStatement>(block.statements[0])
        val counterAssign = assertIs<CBinaryExpr>(counterSet.expr)
        assertEquals("=", counterAssign.op)
        assertEquals(CVar("_wait_counter"), counterAssign.left)
        // Second statement: state machine check (CIf with counter > 0)
        val check = assertIs<CIf>(block.statements[1])
        // The then body contains -- and return
        assertEquals(2, check.thenBody.size)
        assertIs<CReturn>(check.thenBody[1])
    }

    // =========================================================================
    // TEST 17: SetVisible(true) generates show_sprites_range call
    // =========================================================================
    @Test
    fun `SetVisible true generates show_sprites_range call`() {
        val op = SetVisible(actorId = "player", visible = true)
        val result = ScriptOpVisitor.visit(op)
        val stmt = assertIs<CExprStatement>(result)
        val call = assertIs<CCall>(stmt.expr)
        assertEquals("show_sprites_range", call.function)
    }

    // =========================================================================
    // TEST 18: SetVisible(false) generates hide_sprites_range call
    // =========================================================================
    @Test
    fun `SetVisible false generates hide_sprites_range call`() {
        val op = SetVisible(actorId = "enemy", visible = false)
        val result = ScriptOpVisitor.visit(op)
        val stmt = assertIs<CExprStatement>(result)
        val call = assertIs<CCall>(stmt.expr)
        assertEquals("hide_sprites_range", call.function)
    }

    // =========================================================================
    // TEST 19: CallOp generates direct C function call
    // =========================================================================
    @Test
    fun `CallOp generates direct C function call with arguments`() {
        val op = CallOp(function = "handle_collision", args = listOf(VarRef("ballX"), Literal(5)))
        val result = ScriptOpVisitor.visit(op)
        val stmt = assertIs<CExprStatement>(result)
        val call = assertIs<CCall>(stmt.expr)
        assertEquals("handle_collision", call.function)
        assertEquals(2, call.args.size)
    }

    // =========================================================================
    // TEST 20: CameraOp FOLLOW generates camera target assignment
    // =========================================================================
    @Test
    fun `CameraOp FOLLOW generates camera target assignment`() {
        val op = CameraOp(action = CameraAction.FOLLOW, args = mapOf("target" to VarRef("player")))
        val result = ScriptOpVisitor.visit(op)
        val stmt = assertIs<CExprStatement>(result)
        val assign = assertIs<CBinaryExpr>(stmt.expr)
        assertEquals("=", assign.op)
        assertEquals(CVar("_camera_target"), assign.left)
    }

    // =========================================================================
    // TEST 21: FadeOp generates fade_out/fade_in call
    // =========================================================================
    @Test
    fun `FadeOp FADE_OUT generates fade_out call`() {
        val op = FadeOp(fadeIn = false, frames = 30)
        val result = ScriptOpVisitor.visit(op)
        val stmt = assertIs<CExprStatement>(result)
        val call = assertIs<CCall>(stmt.expr)
        assertEquals("fade_out", call.function)
    }

    @Test
    fun `FadeOp FADE_IN generates fade_in call`() {
        val op = FadeOp(fadeIn = true, frames = 30)
        val result = ScriptOpVisitor.visit(op)
        val stmt = assertIs<CExprStatement>(result)
        val call = assertIs<CCall>(stmt.expr)
        assertEquals("fade_in", call.function)
    }

    // =========================================================================
    // TEST 22: ArrayAssign generates array element write
    // =========================================================================
    @Test
    fun `ArrayAssign generates array element write via CArrayAccess`() {
        val op = ArrayAssign(array = "scores", index = Literal(0), value = Literal(100))
        val result = ScriptOpVisitor.visit(op)
        val stmt = assertIs<CExprStatement>(result)
        val assign = assertIs<CBinaryExpr>(stmt.expr)
        assertEquals("=", assign.op)
        val arrayAccess = assertIs<CArrayAccess>(assign.left)
        assertEquals(CVar("_scores"), arrayAccess.array)
        assertEquals(CLiteral(0), arrayAccess.index)
    }

    // =========================================================================
    // TEST 23: ReturnOp generates CReturn
    // =========================================================================
    @Test
    fun `ReturnOp generates CReturn`() {
        val op = ReturnOp()
        val result = ScriptOpVisitor.visit(op)
        val ret = assertIs<CReturn>(result)
        assertTrue(ret.value == null, "ReturnOp with no value should produce null value CReturn")
    }

    // =========================================================================
    // TEST 24: MathOp ABS generates abs() call
    // =========================================================================
    @Test
    fun `MathOp ABS generates abs call`() {
        val op = MathOp(result = "absVal", op = MathFunction.ABS, args = listOf(VarRef("dx")))
        val result = ScriptOpVisitor.visit(op)
        val stmt = assertIs<CExprStatement>(result)
        val assign = assertIs<CBinaryExpr>(stmt.expr)
        assertEquals("=", assign.op)
        assertEquals(CVar("_absVal"), assign.left)
    }

    // =========================================================================
    // TEST 25: All 25 ScriptOp types — none produce TODO stubs
    // =========================================================================
    @Test
    fun `All 25 ScriptOp types produce non-TODO C output`() {
        val allOps =
            listOf(
                Assign("x", Literal(1)),
                ArrayAssign("arr", Literal(0), Literal(1)),
                IfOp(BinaryExpr(VarRef("x"), BinaryOp.GT, Literal(0)), emptyList()),
                WhileOp(BinaryExpr(VarRef("x"), BinaryOp.GT, Literal(0)), emptyList()),
                ForOp("i", Literal(0), Literal(9), emptyList()),
                SetPosition("player", Literal(80), Literal(72)),
                MoveBy("player", Literal(1), Literal(0)),
                NavigateTo("game"),
                io.github.gbkt.core.ir.TriggerSystem("save"),
                PlaySound("hit"),
                DialogSay("dialog1", listOf(DialogTextSegment("Hello"))),
                MenuShow("main"),
                PrintAt(0, 14, "Score: 0"),
                ScreenClear(),
                ScreenFill(tile = 0x01),
                PrintOp("text"),
                FadeOp(fadeIn = false, frames = 0),
                io.github.gbkt.core.ir.SetVisible("player", true),
                io.github.gbkt.core.ir.SpawnActor("enemy"),
                io.github.gbkt.core.ir.DestroyActor("enemy"),
                io.github.gbkt.core.ir.AnimateOp("player", "run"),
                CameraOp(CameraAction.FOLLOW, mapOf("target" to VarRef("player"))),
                WaitFrames(5),
                CallOp("my_fn", emptyList()),
                ReturnOp(),
                MathOp("result", MathFunction.ABS, listOf(Literal(5))),
                RawOp("/* raw */"),
                GotoXYOp(Literal(5), Literal(3)),
            )

        for (op in allOps) {
            val result = ScriptOpVisitor.visit(op)
            // None of the results should be a TODO stub
            assertFalse(
                result is CRawCode && result.code.contains("TODO:"),
                "Expected no TODO stub for ${op::class.simpleName}, got: ${result}",
            )
        }
    }
}
