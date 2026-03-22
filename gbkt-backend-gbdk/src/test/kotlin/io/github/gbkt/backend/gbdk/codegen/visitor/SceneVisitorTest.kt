/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CDefine
import io.github.gbkt.backend.gbdk.codegen.ast.CVoid
import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.BankSlot
import io.github.gbkt.core.ir.FadeOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.PrintOp
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SceneVisitorTest {

    // =========================================================================
    // TEST 1: scene with enterOps produces enter function
    // =========================================================================
    @Test
    fun `scene produces enter function`() {
        val scene =
            SceneIR(
                id = "title",
                enterOps = listOf(PrintOp(text = "PONG", position = PositionDef(6, 4))),
            )
        val functions = SceneVisitor.visit(scene)

        val enterFn = functions.find { it.name == "title_enter" }
        assertNotNull(enterFn, "Expected title_enter function")
        assertEquals(CVoid, enterFn.returnType)
        assertTrue(enterFn.isBanked)
    }

    // =========================================================================
    // TEST 2: scene produces frame function
    // =========================================================================
    @Test
    fun `scene produces frame function`() {
        val scene =
            SceneIR(id = "game", frameOps = listOf(Assign("score", Literal(1), AssignOp.ADD)))
        val functions = SceneVisitor.visit(scene)

        val frameFn = functions.find { it.name == "game_frame" }
        assertNotNull(frameFn, "Expected game_frame function")
    }

    // =========================================================================
    // TEST 3: scene produces exit function
    // =========================================================================
    @Test
    fun `scene produces exit function`() {
        val scene = SceneIR(id = "game", exitOps = listOf(FadeOp(fadeIn = false, frames = 0)))
        val functions = SceneVisitor.visit(scene)

        val exitFn = functions.find { it.name == "game_exit" }
        assertNotNull(exitFn, "Expected game_exit function")
    }

    // =========================================================================
    // TEST 4: empty lifecycle handler skips function
    // =========================================================================
    @Test
    fun `empty lifecycle handlers are not generated as functions`() {
        val scene =
            SceneIR(
                id = "title",
                enterOps = listOf(PrintOp(text = "PONG")),
                frameOps = emptyList(),
                exitOps = emptyList(),
            )
        val functions = SceneVisitor.visit(scene)
        val names = functions.map { it.name }

        assertTrue("title_enter" in names, "Expected title_enter to exist")
        assertFalse("title_frame" in names, "Expected title_frame to be absent (empty frameOps)")
        assertFalse("title_exit" in names, "Expected title_exit to be absent (empty exitOps)")
    }

    // =========================================================================
    // TEST 5: function naming convention preserved
    // =========================================================================
    @Test
    fun `function naming convention preserves scene ID`() {
        val scene =
            SceneIR(
                id = "gameover",
                enterOps = listOf(Assign("score", Literal(0), AssignOp.SET)),
                frameOps = listOf(Assign("timer", Literal(1), AssignOp.ADD)),
            )
        val functions = SceneVisitor.visit(scene)
        val names = functions.map { it.name }

        assertTrue("gameover_enter" in names)
        assertTrue("gameover_frame" in names)
    }

    // =========================================================================
    // TEST 6: functions are marked isBanked=true
    // =========================================================================
    @Test
    fun `all generated scene functions are marked isBanked true`() {
        val scene =
            SceneIR(
                id = "gameplay",
                enterOps = listOf(Assign("x", Literal(0), AssignOp.SET)),
                frameOps = listOf(Assign("y", Literal(0), AssignOp.SET)),
                exitOps = listOf(Assign("z", Literal(0), AssignOp.SET)),
            )
        val functions = SceneVisitor.visit(scene)

        assertTrue(functions.isNotEmpty())
        assertTrue(functions.all { it.isBanked }, "All scene functions should be isBanked=true")
    }

    // =========================================================================
    // TEST 7: sectionComment includes scene name
    // =========================================================================
    @Test
    fun `enter function sectionComment includes scene name`() {
        val scene = SceneIR(id = "title", enterOps = listOf(Assign("x", Literal(0), AssignOp.SET)))
        val functions = SceneVisitor.visit(scene)

        val enterFn = functions.find { it.name == "title_enter" }
        assertNotNull(enterFn)
        assertEquals("Scene: title", enterFn.sectionComment)
    }

    @Test
    fun `non-enter functions do not repeat sectionComment`() {
        val scene =
            SceneIR(
                id = "game",
                enterOps = listOf(Assign("x", Literal(0), AssignOp.SET)),
                frameOps = listOf(Assign("y", Literal(0), AssignOp.SET)),
            )
        val functions = SceneVisitor.visit(scene)

        val frameFn = functions.find { it.name == "game_frame" }
        assertNotNull(frameFn)
        // Frame function should not repeat the section comment (only enter gets it)
        assertEquals(null, frameFn.sectionComment)
    }

    // =========================================================================
    // TEST 8: enter function body contains ScriptOp visitor output
    // =========================================================================
    @Test
    fun `enter function body contains CStatements from ScriptOpVisitor`() {
        val scene =
            SceneIR(
                id = "title",
                enterOps =
                    listOf(
                        Assign("score", Literal(0), AssignOp.SET),
                        Assign("lives", Literal(3), AssignOp.SET),
                    ),
            )
        val functions = SceneVisitor.visit(scene)

        val enterFn = functions.find { it.name == "title_enter" }
        assertNotNull(enterFn)
        assertEquals(2, enterFn.body.size)
    }

    // =========================================================================
    // TEST 9: generateSceneEnum produces define constants
    // =========================================================================
    @Test
    fun `generateSceneEnum produces CDefine constants with sequential indices`() {
        val sceneIds = listOf("title", "game", "gameover")
        val defines = SceneVisitor.generateSceneEnum(sceneIds)

        assertEquals(3, defines.size)
        assertEquals(CDefine("SCENE_TITLE", "0"), defines[0])
        assertEquals(CDefine("SCENE_GAME", "1"), defines[1])
        assertEquals(CDefine("SCENE_GAMEOVER", "2"), defines[2])
    }

    @Test
    fun `generateSceneEnum with single scene produces one define`() {
        val defines = SceneVisitor.generateSceneEnum(listOf("menu"))
        assertEquals(1, defines.size)
        assertEquals(CDefine("SCENE_MENU", "0"), defines[0])
    }

    // =========================================================================
    // TEST 10: scene with bankSlot sets bank on CFunction
    // =========================================================================
    @Test
    fun `scene with bankSlot sets bank on CFunction`() {
        val scene =
            SceneIR(
                id = "gameplay",
                enterOps = listOf(Assign("x", Literal(0), AssignOp.SET)),
                frameOps = listOf(Assign("y", Literal(0), AssignOp.SET)),
                bankSlot = BankSlot(bank = 2),
            )
        val functions = SceneVisitor.visit(scene)

        assertTrue(functions.isNotEmpty())
        functions.forEach { fn ->
            assertEquals(2, fn.bank, "Expected bank=2 on function ${fn.name}")
            assertTrue(fn.isBanked, "Expected isBanked=true on function ${fn.name}")
        }
    }

    // =========================================================================
    // TEST 11: scene without bankSlot keeps isBanked true with null bank
    // =========================================================================
    @Test
    fun `scene without bankSlot keeps isBanked true with null bank`() {
        val scene =
            SceneIR(
                id = "title",
                enterOps = listOf(Assign("x", Literal(0), AssignOp.SET)),
                // bankSlot = null (default)
            )
        val functions = SceneVisitor.visit(scene)

        assertTrue(functions.isNotEmpty())
        functions.forEach { fn ->
            assertNull(fn.bank, "Expected bank=null when no bankSlot on function ${fn.name}")
            assertTrue(fn.isBanked, "Expected isBanked=true for backward compat on ${fn.name}")
        }
    }

    // =========================================================================
    // TEST 12: scene with bankSlot=0 keeps backward compat (isBanked=false for HOME bank)
    // =========================================================================
    @Test
    fun `scene with bankSlot bank=0 sets isBanked=false`() {
        val scene =
            SceneIR(
                id = "title",
                enterOps = listOf(Assign("x", Literal(0), AssignOp.SET)),
                bankSlot = BankSlot(bank = 0),
            )
        val functions = SceneVisitor.visit(scene)

        assertTrue(functions.isNotEmpty())
        functions.forEach { fn ->
            assertEquals(0, fn.bank, "Expected bank=0 on function ${fn.name}")
            assertFalse(fn.isBanked, "Expected isBanked=false for HOME bank function ${fn.name}")
        }
    }
}
