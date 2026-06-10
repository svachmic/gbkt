/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CRawCode
import io.github.gbkt.backend.gbdk.codegen.ast.CVoid
import io.github.gbkt.core.ir.RawOp
import io.github.gbkt.core.ir.ScriptOp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks the addGenreEnterOps splice contract — Phase 07.4 Plan 10 closure of GAP-A / GAP-B / GAP-D.
 * Mirror of (and parallel to) the addGenreFrameOps test pattern from Plan 07.4-05.
 *
 * Tests use reflection to call the private addGenreEnterOps helper directly, avoiding the full
 * ServiceLoader-driven pipeline (kept simple and fast).
 */
class GenreEnterOpsSpliceTest {
    private val pipeline = GBDKPipeline()

    private fun callAddGenreEnterOps(
        functions: List<CFunction>,
        sceneId: String,
        ops: List<ScriptOp>,
    ): List<CFunction> {
        val method =
            pipeline::class
                .java
                .getDeclaredMethod(
                    "addGenreEnterOps",
                    List::class.java,
                    String::class.java,
                    List::class.java,
                )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(pipeline, functions, sceneId, ops) as List<CFunction>
    }

    @Test
    fun `no enter ops leaves functions untouched`() {
        val raceEnter =
            CFunction(
                name = "race_enter",
                returnType = CVoid,
                params = emptyList(),
                body = listOf(CRawCode("cls();")),
                isBanked = true,
            )
        val result = callAddGenreEnterOps(listOf(raceEnter), "race", emptyList())
        assertEquals(listOf(raceEnter), result)
    }

    @Test
    fun `populated enter ops prepend at head of body in declared order`() {
        val raceEnter =
            CFunction(
                name = "race_enter",
                returnType = CVoid,
                params = emptyList(),
                body = listOf(CRawCode("cls();")),
                isBanked = true,
            )
        val ops: List<ScriptOp> = listOf(RawOp("foo();"), RawOp("bar();"))
        val result = callAddGenreEnterOps(listOf(raceEnter), "race", ops)
        val updatedEnter = result.first { it.name == "race_enter" }
        // Two prepended statements + 1 original
        assertEquals(3, updatedEnter.body.size)
        // The first two statements are the foo and bar RawOps lowered to CRawCode
        val first = updatedEnter.body[0]
        val second = updatedEnter.body[1]
        assertTrue(first is CRawCode && first.code.contains("foo()"))
        assertTrue(second is CRawCode && second.code.contains("bar()"))
        // The original cls() is preserved at index 2
        val third = updatedEnter.body[2]
        assertTrue(third is CRawCode && third.code.contains("cls()"))
    }

    @Test
    fun `enter ops do not affect non-enter functions`() {
        val raceFrame =
            CFunction(
                name = "race_frame",
                returnType = CVoid,
                params = emptyList(),
                body = listOf(CRawCode("update();")),
                isBanked = true,
            )
        val ops: List<ScriptOp> = listOf(RawOp("foo();"))
        val result = callAddGenreEnterOps(listOf(raceFrame), "race", ops)
        assertEquals(listOf(raceFrame), result, "race_frame must be untouched by addGenreEnterOps")
    }

    @Test
    fun `enter ops match by sceneId, leaving other scenes alone`() {
        val raceEnter =
            CFunction(
                name = "race_enter",
                returnType = CVoid,
                params = emptyList(),
                body = listOf(CRawCode("cls();")),
                isBanked = true,
            )
        val titleEnter =
            CFunction(
                name = "title_enter",
                returnType = CVoid,
                params = emptyList(),
                body = listOf(CRawCode("cls();")),
                isBanked = true,
            )
        val result =
            callAddGenreEnterOps(
                listOf(raceEnter, titleEnter),
                "race",
                listOf(RawOp("zoneLoad();")),
            )
        // race_enter prepended
        assertEquals(2, result.first { it.name == "race_enter" }.body.size)
        // title_enter unchanged
        assertEquals(1, result.first { it.name == "title_enter" }.body.size)
    }
}
