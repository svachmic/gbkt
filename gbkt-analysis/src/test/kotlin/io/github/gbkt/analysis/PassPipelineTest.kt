/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Pass that records its execution by appending a diagnostic with the given [label]. */
private fun recordingPass(label: String): AnalysisPass = AnalysisPass { ctx ->
    PassResult.Success(
        ctx.withDiagnostics(
            listOf(Diagnostic(id = label, severity = Severity.INFO, message = "pass $label ran"))
        )
    )
}

/** Pass that always fails with a single diagnostic carrying the given [label]. */
private fun failingPass(label: String): AnalysisPass = AnalysisPass {
    PassResult.Failed(
        listOf(Diagnostic(id = label, severity = Severity.ERROR, message = "pass $label failed"))
    )
}

/** Pass that should never be called — fails with assertion if executed. */
private val unreachablePass: AnalysisPass = AnalysisPass {
    error("This pass must not run (fail-fast was not respected)")
}

class PassPipelineTest {

    @Test
    fun `pipeline with no passes returns success with original context`() {
        val ctx = baseContext()
        val pipeline = PassPipeline(builtInPasses = emptyList())

        val result = pipeline.execute(ctx)

        assertIs<PassResult.Success>(result)
        assertEquals(ctx, result.context)
    }

    @Test
    fun `pipeline chains passes in order`() {
        val pipeline =
            PassPipeline(
                builtInPasses = listOf(recordingPass("A"), recordingPass("B"), recordingPass("C"))
            )

        val result = pipeline.execute(baseContext())

        assertIs<PassResult.Success>(result)
        val ids = result.context.diagnostics.map { it.id }
        assertEquals(listOf("A", "B", "C"), ids)
    }

    @Test
    fun `pipeline fails fast on first error — second pass does not run`() {
        val pipeline = PassPipeline(builtInPasses = listOf(failingPass("ERR"), unreachablePass))

        val result = pipeline.execute(baseContext())

        assertIs<PassResult.Failed>(result)
        assertEquals(1, result.diagnostics.size)
        assertEquals("ERR", result.diagnostics.first().id)
    }

    @Test
    fun `beforePasses run before builtInPasses`() {
        val pipeline =
            PassPipeline(
                beforePasses = listOf(recordingPass("before")),
                builtInPasses = listOf(recordingPass("builtin")),
            )

        val result = pipeline.execute(baseContext())

        assertIs<PassResult.Success>(result)
        val ids = result.context.diagnostics.map { it.id }
        assertEquals(listOf("before", "builtin"), ids)
    }

    @Test
    fun `afterPasses run after builtInPasses`() {
        val pipeline =
            PassPipeline(
                builtInPasses = listOf(recordingPass("builtin")),
                afterPasses = listOf(recordingPass("after")),
            )

        val result = pipeline.execute(baseContext())

        assertIs<PassResult.Success>(result)
        val ids = result.context.diagnostics.map { it.id }
        assertEquals(listOf("builtin", "after"), ids)
    }

    @Test
    fun `failed pass diagnostics are returned`() {
        val pipeline = PassPipeline(builtInPasses = listOf(failingPass("ANLZ-99")))

        val result = pipeline.execute(baseContext())

        assertIs<PassResult.Failed>(result)
        assertEquals(1, result.diagnostics.size)
        val diag = result.diagnostics.first()
        assertEquals("ANLZ-99", diag.id)
        assertEquals(Severity.ERROR, diag.severity)
    }

    @Test
    fun `beforePasses failure stops execution before builtInPasses`() {
        val pipeline =
            PassPipeline(
                beforePasses = listOf(failingPass("BEFORE-ERR")),
                builtInPasses = listOf(unreachablePass),
                afterPasses = listOf(unreachablePass),
            )

        val result = pipeline.execute(baseContext())

        assertIs<PassResult.Failed>(result)
        assertEquals("BEFORE-ERR", result.diagnostics.first().id)
    }

    @Test
    fun `builtIn pass failure stops afterPasses from running`() {
        val pipeline =
            PassPipeline(
                builtInPasses = listOf(failingPass("BUILTIN-ERR")),
                afterPasses = listOf(unreachablePass),
            )

        val result = pipeline.execute(baseContext())

        assertIs<PassResult.Failed>(result)
        assertEquals("BUILTIN-ERR", result.diagnostics.first().id)
    }

    @Test
    fun `full three-stage ordering is preserved`() {
        val pipeline =
            PassPipeline(
                beforePasses = listOf(recordingPass("B1"), recordingPass("B2")),
                builtInPasses = listOf(recordingPass("C1"), recordingPass("C2")),
                afterPasses = listOf(recordingPass("A1")),
            )

        val result = pipeline.execute(baseContext())

        assertIs<PassResult.Success>(result)
        val ids = result.context.diagnostics.map { it.id }
        assertEquals(listOf("B1", "B2", "C1", "C2", "A1"), ids)
    }
}
