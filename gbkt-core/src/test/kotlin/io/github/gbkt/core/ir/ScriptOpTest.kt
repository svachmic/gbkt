/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import kotlin.test.*

/**
 * Tests for the ScriptOp sealed instruction set.
 *
 * Verifies construction of each ScriptOp subtype, default values, and the AssignOp enum variants.
 */
class ScriptOpTest {

    @Test
    fun `Assign constructs with default SET op`() {
        val op = Assign(target = "score", value = Literal(100))
        assertEquals("score", op.target)
        assertEquals(AssignOp.SET, op.op)
        assertNull(op.sourceLocation)
    }

    @Test
    fun `Assign supports all AssignOp variants`() {
        val ops =
            listOf(
                AssignOp.SET,
                AssignOp.ADD,
                AssignOp.SUB,
                AssignOp.MUL,
                AssignOp.DIV,
                AssignOp.MOD,
                AssignOp.AND,
                AssignOp.OR,
                AssignOp.XOR,
            )
        for (assignOp in ops) {
            val op = Assign(target = "x", value = Literal(1), op = assignOp)
            assertEquals(assignOp, op.op)
        }
        assertEquals(9, ops.size)
    }

    @Test
    fun `ArrayAssign constructs correctly`() {
        val op =
            ArrayAssign(
                array = "inventory",
                index = Literal(0),
                value = Literal(5),
                op = AssignOp.SET,
            )
        assertEquals("inventory", op.array)
        assertEquals(AssignOp.SET, op.op)
    }

    @Test
    fun `IfOp has default empty otherwise list`() {
        val op =
            IfOp(condition = Literal(1), then = listOf(Assign(target = "x", value = Literal(1))))
        assertEquals(emptyList(), op.otherwise)
        assertNull(op.sourceLocation)
    }

    @Test
    fun `IfOp constructs with otherwise branch`() {
        val op =
            IfOp(
                condition = VarRef("flag"),
                then = listOf(Assign(target = "x", value = Literal(1))),
                otherwise = listOf(Assign(target = "x", value = Literal(0))),
            )
        assertEquals(1, op.then.size)
        assertEquals(1, op.otherwise.size)
    }

    @Test
    fun `WhileOp constructs correctly`() {
        val op =
            WhileOp(
                condition = BinaryExpr(VarRef("x"), BinaryOp.LT, Literal(10)),
                body = listOf(Assign(target = "x", value = Literal(1), op = AssignOp.ADD)),
            )
        assertEquals(1, op.body.size)
        assertNull(op.sourceLocation)
    }

    @Test
    fun `ForOp constructs correctly`() {
        val op =
            ForOp(
                variable = "i",
                from = Literal(0),
                to = Literal(10),
                body = listOf(RawOp("/* loop */")),
            )
        assertEquals("i", op.variable)
        assertEquals(1, op.body.size)
    }

    @Test
    fun `SetPosition constructs correctly`() {
        val op = SetPosition(actorId = "player", x = Literal(80), y = Literal(72))
        assertEquals("player", op.actorId)
    }

    @Test
    fun `MoveBy constructs correctly`() {
        val op = MoveBy(actorId = "player", dx = Literal(2), dy = Literal(0))
        assertEquals("player", op.actorId)
    }

    @Test
    fun `NavigateTo constructs correctly`() {
        val op = NavigateTo(sceneId = "gameoverScene")
        assertEquals("gameoverScene", op.sceneId)
    }

    @Test
    fun `TriggerSystem constructs with default empty args`() {
        val op = TriggerSystem(systemId = "battle")
        assertEquals("battle", op.systemId)
        assertEquals(emptyMap(), op.args)
    }

    @Test
    fun `TriggerSystem accepts args`() {
        val op = TriggerSystem(systemId = "battle", args = mapOf("enemy" to VarRef("goblin")))
        assertEquals(1, op.args.size)
    }

    @Test
    fun `PlaySound constructs correctly`() {
        val op = PlaySound(soundId = "jump")
        assertEquals("jump", op.soundId)
    }

    @Test
    fun `DialogSay constructs correctly`() {
        val segments = listOf(DialogTextSegment("Hello!"), DialogTextSegment("How are you?"))
        val op = DialogSay(dialogId = "npc1", segments = segments)
        assertEquals("npc1", op.dialogId)
        assertEquals(2, op.segments.size)
    }

    @Test
    fun `DialogChoice constructs correctly`() {
        val op =
            DialogChoice(
                dialogId = "elder",
                options =
                    listOf(
                        DialogOption("Accept", listOf(NavigateTo("quest"))),
                        DialogOption("Decline", listOf(NavigateTo("village"))),
                    ),
            )
        assertEquals("elder", op.dialogId)
        assertEquals(2, op.options.size)
    }

    @Test
    fun `MenuShow and MenuHide construct correctly`() {
        val show = MenuShow(menuId = "mainMenu")
        val hide = MenuHide(menuId = "mainMenu")
        assertEquals("mainMenu", show.menuId)
        assertEquals("mainMenu", hide.menuId)
    }

    @Test
    fun `PrintAt constructs with defaults`() {
        val op = PrintAt(x = 0, y = 14, text = "Score: 0")
        assertEquals(0, op.x)
        assertEquals(14, op.y)
        assertEquals("Score: 0", op.text)
        assertEquals(FontMode.FIXED_WIDTH, op.fontMode)
    }

    @Test
    fun `ScreenClear and ScreenFill construct correctly`() {
        val clear = ScreenClear()
        val fill = ScreenFill(tile = 0x01)
        assertNull(clear.sourceLocation)
        assertEquals(0x01, fill.tile)
    }

    @Test
    fun `RawOp constructs correctly`() {
        val op = RawOp(code = "custom_c_function();")
        assertEquals("custom_c_function();", op.code)
    }

    @Test
    fun `CallOp constructs correctly`() {
        val op = CallOp(function = "takeDamage", args = listOf(Literal(10)))
        assertEquals("takeDamage", op.function)
        assertEquals(1, op.args.size)
    }

    @Test
    fun `ReturnOp with and without value`() {
        val withValue = ReturnOp(value = Literal(42))
        val withoutValue = ReturnOp()
        assertNotNull(withValue.value)
        assertNull(withoutValue.value)
    }

    @Test
    fun `WaitFrames constructs correctly`() {
        val op = WaitFrames(frames = 30)
        assertEquals(30, op.frames)
    }

    @Test
    fun `SpawnActor constructs correctly`() {
        val op = SpawnActor(actorId = "enemy")
        assertEquals("enemy", op.actorId)
    }

    @Test
    fun `DestroyActor constructs correctly`() {
        val op = DestroyActor(actorId = "enemy")
        assertEquals("enemy", op.actorId)
    }

    @Test
    fun `SetVisible constructs correctly`() {
        val show = SetVisible(actorId = "player", visible = true)
        val hide = SetVisible(actorId = "player", visible = false)
        assertTrue(show.visible)
        assertFalse(hide.visible)
    }

    @Test
    fun `AnimateOp constructs correctly`() {
        val op = AnimateOp(actorId = "player", animation = "walk")
        assertEquals("player", op.actorId)
        assertEquals("walk", op.animation)
    }

    @Test
    fun `CameraOp constructs correctly`() {
        val op = CameraOp(action = CameraAction.FOLLOW, args = mapOf("target" to VarRef("player")))
        assertEquals(CameraAction.FOLLOW, op.action)
        assertEquals(1, op.args.size)
    }

    @Test
    fun `CameraAction has all variants`() {
        val actions = CameraAction.entries
        assertTrue(actions.contains(CameraAction.FOLLOW))
        assertTrue(actions.contains(CameraAction.UNFOLLOW))
        assertTrue(actions.contains(CameraAction.SHAKE))
        assertTrue(actions.contains(CameraAction.MOVE_TO))
    }

    @Test
    fun `FadeOp constructs with default empty after list`() {
        val op = FadeOp(fadeIn = true, frames = 30)
        assertTrue(op.fadeIn)
        assertEquals(30, op.frames)
        assertEquals(emptyList(), op.after)
    }

    @Test
    fun `PrintOp constructs with defaults`() {
        val op = PrintOp(text = "Score: %d")
        assertEquals("Score: %d", op.text)
        assertEquals(emptyList(), op.values)
        assertNull(op.position)
    }

    @Test
    fun `MathOp constructs correctly`() {
        val op =
            MathOp(
                result = "damage",
                op = MathFunction.MAX,
                args = listOf(VarRef("atk"), Literal(1)),
            )
        assertEquals("damage", op.result)
        assertEquals(MathFunction.MAX, op.op)
    }

    @Test
    fun `ScriptOp sourceLocation defaults to null for all subtypes`() {
        val ops: List<ScriptOp> =
            listOf(
                Assign(target = "x", value = Literal(0)),
                IfOp(condition = Literal(1), then = emptyList()),
                WhileOp(condition = Literal(0), body = emptyList()),
                NavigateTo(sceneId = "next"),
                PlaySound(soundId = "boom"),
                RawOp(code = "noop();"),
            )
        for (op in ops) {
            assertNull(
                op.sourceLocation,
                "sourceLocation should be null for ${op::class.simpleName}",
            )
        }
    }
}
