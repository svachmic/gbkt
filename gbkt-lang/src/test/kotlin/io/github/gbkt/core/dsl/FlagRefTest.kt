/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.core.ir.VarRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// =============================================================================
// FLAG REF TESTS
// Verifies typed flag operations: FlagRef, setFlag, clearFlag, checkFlag (GAP-11).
// =============================================================================

class FlagRefTest {

    private fun buildScript(block: ScriptBuilder.() -> Unit): List<ScriptOp> {
        val builder = ScriptBuilder()
        ScriptBuilderContext.with(builder) { builder.block() }
        return builder.build()
    }

    // =========================================================================
    // FlagRef — data class construction and equality
    // =========================================================================

    @Test
    fun `FlagRef holds name and supports equality`() {
        val ref1 = FlagRef("bossDefeated")
        val ref2 = FlagRef("bossDefeated")
        assertEquals("bossDefeated", ref1.name)
        assertEquals(ref1, ref2)
    }

    @Test
    fun `FlagRef copy() preserves name`() {
        val ref = FlagRef("hasKey")
        val copy = ref.copy(name = "metElder")
        assertEquals("metElder", copy.name)
    }

    // =========================================================================
    // setFlag(String) — emits Assign(_flag_{name}, 1)
    // =========================================================================

    @Test
    fun `setFlag(String) emits Assign with _flag_ prefix and value 1`() {
        val ops = buildScript { setFlag("bossDefeated") }
        assertEquals(1, ops.size)
        val assign = assertIs<Assign>(ops[0])
        assertEquals("_flag_bossDefeated", assign.target)
        assertEquals(Literal(1), assign.value)
        assertEquals(AssignOp.SET, assign.op)
    }

    @Test
    fun `setFlag(String) with underscore-separated name emits correct variable`() {
        val ops = buildScript { setFlag("door_unlocked") }
        val assign = assertIs<Assign>(ops[0])
        assertEquals("_flag_door_unlocked", assign.target)
        assertEquals(Literal(1), assign.value)
    }

    // =========================================================================
    // setFlag(FlagRef) — typed overload delegates to setFlag(String)
    // =========================================================================

    @Test
    fun `setFlag(FlagRef) emits Assign with _flag_ prefix and value 1`() {
        val flag = FlagRef("hasKey")
        val ops = buildScript { setFlag(flag) }
        assertEquals(1, ops.size)
        val assign = assertIs<Assign>(ops[0])
        assertEquals("_flag_hasKey", assign.target)
        assertEquals(Literal(1), assign.value)
        assertEquals(AssignOp.SET, assign.op)
    }

    // =========================================================================
    // clearFlag(String) — emits Assign(_flag_{name}, 0)
    // =========================================================================

    @Test
    fun `clearFlag(String) emits Assign with _flag_ prefix and value 0`() {
        val ops = buildScript { clearFlag("bossDefeated") }
        assertEquals(1, ops.size)
        val assign = assertIs<Assign>(ops[0])
        assertEquals("_flag_bossDefeated", assign.target)
        assertEquals(Literal(0), assign.value)
        assertEquals(AssignOp.SET, assign.op)
    }

    // =========================================================================
    // clearFlag(FlagRef) — typed overload delegates to clearFlag(String)
    // =========================================================================

    @Test
    fun `clearFlag(FlagRef) emits Assign with _flag_ prefix and value 0`() {
        val flag = FlagRef("hasKey")
        val ops = buildScript { clearFlag(flag) }
        assertEquals(1, ops.size)
        val assign = assertIs<Assign>(ops[0])
        assertEquals("_flag_hasKey", assign.target)
        assertEquals(Literal(0), assign.value)
        assertEquals(AssignOp.SET, assign.op)
    }

    // =========================================================================
    // checkFlag(String) — returns VarRef(_flag_{name}) without emitting ops
    // =========================================================================

    @Test
    fun `checkFlag(String) returns VarRef with _flag_ prefix`() {
        val expr = checkFlag("bossDefeated")
        val varRef = assertIs<VarRef>(expr)
        assertEquals("_flag_bossDefeated", varRef.name)
    }

    @Test
    fun `checkFlag(String) does not emit any ScriptOps`() {
        // Calling checkFlag inside buildScript — the expression is discarded but no ops should emit
        val ops = buildScript {
            checkFlag("metElder") // pure expression, no side effect
        }
        assertEquals(0, ops.size, "checkFlag() only returns an Expr — it must not emit ops")
    }

    // =========================================================================
    // checkFlag(FlagRef) — typed overload delegates to checkFlag(String)
    // =========================================================================

    @Test
    fun `checkFlag(FlagRef) returns VarRef with _flag_ prefix`() {
        val flag = FlagRef("hasKey")
        val expr = checkFlag(flag)
        val varRef = assertIs<VarRef>(expr)
        assertEquals("_flag_hasKey", varRef.name)
    }

    // =========================================================================
    // FlagPageBuilder.flag() — returns FlagRef and registers flag name
    // =========================================================================

    @Test
    fun `FlagPageBuilder flag() registers flags in the GlobalFlagsIR`() {
        val ir =
            game("FlagTest") {
                    flags {
                        page("story") {
                            flag("metElder")
                            flag("hasKey")
                            flag("defeatedBoss")
                        }
                    }
                    val startSceneRef = scene("start") { enter {} }
                    start = startSceneRef
                }
                .build()

        // Flags are registered — 1 GlobalFlagsIR with 1 page containing 3 flags
        assertEquals(1, ir.flags.size)
        val globalFlags = ir.flags[0]
        assertEquals(1, globalFlags.pages.size)
        val page = globalFlags.pages[0]
        assertEquals("story", page.name)
        assertEquals(listOf("metElder", "hasKey", "defeatedBoss"), page.flags)
    }

    @Test
    fun `FlagPageBuilder flag() returns FlagRef with name matching argument`() {
        var capturedRef: FlagRef? = null

        game("FlagRefTest") {
                flags { page("story") { capturedRef = flag("bossDefeated") } }
                val startSceneRef = scene("start") { enter {} }
                start = startSceneRef
            }
            .build()

        assertEquals(FlagRef("bossDefeated"), capturedRef)
        assertEquals("bossDefeated", capturedRef!!.name)
    }

    // =========================================================================
    // String vs FlagRef overloads produce identical IR
    // =========================================================================

    @Test
    fun `setFlag(String) and setFlag(FlagRef) produce identical Assign ops`() {
        val opsString = buildScript { setFlag("myFlag") }
        val opsTyped = buildScript { setFlag(FlagRef("myFlag")) }
        // Compare targets and values (sourceLocation may differ)
        val s = assertIs<Assign>(opsString[0])
        val t = assertIs<Assign>(opsTyped[0])
        assertEquals(s.target, t.target)
        assertEquals(s.value, t.value)
        assertEquals(s.op, t.op)
    }

    @Test
    fun `clearFlag(String) and clearFlag(FlagRef) produce identical Assign ops`() {
        val opsString = buildScript { clearFlag("myFlag") }
        val opsTyped = buildScript { clearFlag(FlagRef("myFlag")) }
        val s = assertIs<Assign>(opsString[0])
        val t = assertIs<Assign>(opsTyped[0])
        assertEquals(s.target, t.target)
        assertEquals(s.value, t.value)
        assertEquals(s.op, t.op)
    }

    @Test
    fun `checkFlag(String) and checkFlag(FlagRef) return equal VarRef expressions`() {
        val exprString = checkFlag("myFlag")
        val exprTyped = checkFlag(FlagRef("myFlag"))
        assertEquals(exprString, exprTyped)
    }
}
