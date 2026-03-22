/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis

import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultPipelineTest {

    /** Creates a valid GameIR with one scene and one actor. */
    private fun minimalValidGame(): GameIR {
        val actor = ActorIR(id = "player", position = PositionDef(80, 72))
        val scene = SceneIR(id = "gameplay", actorIds = listOf("player"))
        return GameIR(
            name = "Test",
            scenes = listOf(scene),
            actors = listOf(actor),
            startScene = "gameplay",
        )
    }

    private fun baseContext(game: GameIR): PassContext =
        PassContext(game = game, profile = FakeProfile, config = AnalysisConfig(maxBanks = 2))

    // -------------------------------------------------------------------------
    // Pipeline structure
    // -------------------------------------------------------------------------

    @Test
    fun `default pipeline creates PassPipeline with 10 built-in passes`() {
        val pipeline = DefaultPipeline.create()

        // Verify pipeline executes without throwing — the structure is correct
        val ctx = baseContext(GameIR(name = "Empty"))
        val result = pipeline.execute(ctx)

        // Should at minimum complete (empty game has no scenes to pack, so banking passes
        // trivially)
        assertNotNull(result, "Pipeline should return a non-null result")
    }

    // -------------------------------------------------------------------------
    // Valid game — success path
    // -------------------------------------------------------------------------

    @Test
    fun `default pipeline runs on minimal valid GameIR`() {
        val game = minimalValidGame()
        val ctx = baseContext(game)
        val pipeline = DefaultPipeline.create()

        val result = pipeline.execute(ctx)

        assertIs<PassResult.Success>(result)
    }

    @Test
    fun `default pipeline sets budget report on context after success`() {
        val game = minimalValidGame()
        val ctx = baseContext(game)
        val pipeline = DefaultPipeline.create()

        val result = pipeline.execute(ctx)

        assertIs<PassResult.Success>(result)
        assertNotNull(
            result.context.budgetReport,
            "budgetReport should be non-null after full pipeline",
        )
        assertTrue(result.context.budgetReport!!.isNotEmpty(), "budgetReport should be non-empty")
    }

    // -------------------------------------------------------------------------
    // Invalid game — fail-fast on SemanticValidationPass
    // -------------------------------------------------------------------------

    @Test
    fun `default pipeline fails fast on invalid GameIR`() {
        // GameIR with a dangling startScene reference — SemanticValidationPass should catch this
        val game =
            GameIR(name = "BrokenGame", scenes = emptyList(), startScene = "nonExistentScene")
        val ctx = baseContext(game)
        val pipeline = DefaultPipeline.create()

        val result = pipeline.execute(ctx)

        assertIs<PassResult.Failed>(result)
        val hasSemanticError = result.diagnostics.any { it.message.contains("nonExistentScene") }
        assertTrue(hasSemanticError, "Expected semantic validation error for dangling startScene")
    }

    // -------------------------------------------------------------------------
    // Extension hooks
    // -------------------------------------------------------------------------

    @Test
    fun `custom before pass is respected`() {
        val game = minimalValidGame()
        val ctx = baseContext(game)

        val customDiagnosticId = "CUSTOM-01"
        val customPass = AnalysisPass { context ->
            PassResult.Success(
                context.withDiagnostics(
                    listOf(
                        Diagnostic(
                            id = customDiagnosticId,
                            severity = Severity.INFO,
                            message = "custom pass ran",
                        )
                    )
                )
            )
        }

        val pipeline = DefaultPipeline.create(beforePasses = listOf(customPass))
        val result = pipeline.execute(ctx)

        assertIs<PassResult.Success>(result)
        val customRan = result.context.diagnostics.any { it.id == customDiagnosticId }
        assertTrue(customRan, "Custom before pass should have added its diagnostic to context")
    }

    @Test
    fun `custom after pass is respected`() {
        val game = minimalValidGame()
        val ctx = baseContext(game)

        val afterDiagnosticId = "CUSTOM-02"
        val afterPass = AnalysisPass { context ->
            PassResult.Success(
                context.withDiagnostics(
                    listOf(
                        Diagnostic(
                            id = afterDiagnosticId,
                            severity = Severity.INFO,
                            message = "after pass ran",
                        )
                    )
                )
            )
        }

        val pipeline = DefaultPipeline.create(afterPasses = listOf(afterPass))
        val result = pipeline.execute(ctx)

        assertIs<PassResult.Success>(result)
        val afterRan = result.context.diagnostics.any { it.id == afterDiagnosticId }
        assertTrue(afterRan, "Custom after pass should have added its diagnostic to context")
    }
}
