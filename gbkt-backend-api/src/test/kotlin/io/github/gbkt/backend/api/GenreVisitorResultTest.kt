/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.api

import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.core.ir.ScriptOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks the GenreVisitorResult contract — specifically the additive `enterOps` field added in Phase
 * 07.4 Plan 09 to close GAP-A / GAP-B / GAP-D. The field mirrors `frameOps` (same key/value shape)
 * but is consumed by the GBDK pipeline during scene-enter splice, not scene-frame splice.
 */
class GenreVisitorResultTest {

    @Test
    fun `default constructor leaves enterOps empty`() {
        val result = GenreVisitorResult()
        assertTrue(result.enterOps.isEmpty(), "enterOps default must be emptyMap")
    }

    @Test
    fun `enterOps holds populated map`() {
        val ops: List<ScriptOp> = listOf(RawOp("pool_carAi_spawn(80u, 96u);"))
        val result = GenreVisitorResult(enterOps = mapOf("race" to ops))
        assertEquals(1, result.enterOps.size)
        assertEquals(ops, result.enterOps["race"])
    }

    @Test
    fun `enterOps is independent of frameOps`() {
        val enter: List<ScriptOp> = listOf(RawOp("a();"))
        val frame: List<ScriptOp> = listOf(RawOp("b();"))
        val result =
            GenreVisitorResult(frameOps = mapOf("race" to frame), enterOps = mapOf("race" to enter))
        assertEquals(frame, result.frameOps["race"])
        assertEquals(enter, result.enterOps["race"])
        // Distinct lists, distinct semantics
        assertTrue(result.frameOps["race"] != result.enterOps["race"])
    }

    @Test
    fun `single-arg construction stays back-compat`() {
        // Existing genre visitors that only set `functions` must keep compiling.
        val result = GenreVisitorResult(functions = emptyList())
        assertTrue(result.varDecls.isEmpty())
        assertTrue(result.frameOps.isEmpty())
        assertTrue(result.enterOps.isEmpty())
    }
}
