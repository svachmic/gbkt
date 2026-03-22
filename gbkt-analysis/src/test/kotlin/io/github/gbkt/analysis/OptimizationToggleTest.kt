/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis

import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SceneIR
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests verifying per-pass optimization toggle behavior and optimization report generation.
 *
 * Each test exercises:
 * - Toggle flags on [AnalysisConfig] controlling which passes are included
 * - [OptimizationReport] accumulation in [PassContext]
 * - JSON serialization via [OptimizationReport.toJson]
 * - Report file write to disk by [BudgetAuditPass]
 */
class OptimizationToggleTest {

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    /**
     * Minimal valid game: one scene, one actor, no dead scenes, one foldable expression.
     *
     * The Assign op `result = 2 + 3` provides a foldable [BinaryExpr] that [ConstantFoldingPass]
     * will reduce to [Literal(5)].
     */
    private fun gameWithFoldableExpression(): GameIR {
        val foldable = BinaryExpr(Literal(2), BinaryOp.ADD, Literal(3))
        val assignOp = Assign(target = "result", value = foldable)
        val actor = ActorIR(id = "player", position = PositionDef(80, 72))
        val scene =
            SceneIR(id = "gameplay", actorIds = listOf("player"), enterOps = listOf(assignOp))
        return GameIR(
            name = "FoldableGame",
            scenes = listOf(scene),
            actors = listOf(actor),
            startScene = "gameplay",
        )
    }

    /**
     * Game with two scenes where "orphan" is unreachable from "start". Useful for verifying
     * [DeadCodeEliminationPass] runs or is skipped.
     */
    private fun gameWithDeadScene(): GameIR {
        val actor = ActorIR(id = "player", position = PositionDef(80, 72))
        val startScene =
            SceneIR(
                id = "start",
                actorIds = listOf("player"),
                frameOps = listOf(NavigateTo(sceneId = "end")),
            )
        val endScene = SceneIR(id = "end", actorIds = listOf("player"))
        val orphanScene = SceneIR(id = "orphan")
        return GameIR(
            name = "DeadSceneGame",
            scenes = listOf(startScene, endScene, orphanScene),
            actors = listOf(actor),
            startScene = "start",
        )
    }

    private fun makeContext(game: GameIR, config: AnalysisConfig): PassContext =
        PassContext(game = game, profile = FakeProfile, config = config)

    // -------------------------------------------------------------------------
    // Toggle tests — all passes enabled (default)
    // -------------------------------------------------------------------------

    @Test
    fun `all passes enabled by default - optimization report has 3 entries`() {
        val game = gameWithFoldableExpression()
        val config = AnalysisConfig(maxBanks = 2)
        val ctx = makeContext(game, config)
        val pipeline = DefaultPipeline.create(config = config)

        val result = pipeline.execute(ctx)

        assertIs<PassResult.Success>(result)
        val report = result.context.optimizationReport
        val passNames = report.passes.map { it.passName }
        assertTrue(
            "DeadCodeEliminationPass" in passNames,
            "Expected DeadCodeEliminationPass in report. Got: $passNames",
        )
        assertTrue(
            "ConstantFoldingPass" in passNames,
            "Expected ConstantFoldingPass in report. Got: $passNames",
        )
        assertTrue(
            "BitwiseOptimizationPass" in passNames,
            "Expected BitwiseOptimizationPass in report. Got: $passNames",
        )
        assertEquals(3, report.passes.size, "Expected exactly 3 optimization pass summaries")
    }

    // -------------------------------------------------------------------------
    // Toggle tests — individual pass disable
    // -------------------------------------------------------------------------

    @Test
    fun `constant folding disabled - no ConstantFoldingPass entry in report`() {
        val game = gameWithFoldableExpression()
        val config = AnalysisConfig(maxBanks = 2, constantFoldingEnabled = false)
        val ctx = makeContext(game, config)
        val pipeline = DefaultPipeline.create(config = config)

        val result = pipeline.execute(ctx)

        assertIs<PassResult.Success>(result)
        val report = result.context.optimizationReport
        val passNames = report.passes.map { it.passName }
        assertFalse(
            "ConstantFoldingPass" in passNames,
            "ConstantFoldingPass should be absent when constantFoldingEnabled=false. Got: $passNames",
        )
        // Other passes still run
        assertTrue(
            "DeadCodeEliminationPass" in passNames,
            "DeadCodeEliminationPass should still run",
        )
        assertTrue(
            "BitwiseOptimizationPass" in passNames,
            "BitwiseOptimizationPass should still run",
        )
    }

    @Test
    fun `dead code elimination disabled - no DeadCodeEliminationPass entry in report`() {
        val game = gameWithDeadScene()
        val config = AnalysisConfig(maxBanks = 2, deadCodeEliminationEnabled = false)
        val ctx = makeContext(game, config)
        val pipeline = DefaultPipeline.create(config = config)

        val result = pipeline.execute(ctx)

        assertIs<PassResult.Success>(result)
        val report = result.context.optimizationReport
        val passNames = report.passes.map { it.passName }
        assertFalse(
            "DeadCodeEliminationPass" in passNames,
            "DeadCodeEliminationPass should be absent when deadCodeEliminationEnabled=false. Got: $passNames",
        )
        assertTrue("ConstantFoldingPass" in passNames, "ConstantFoldingPass should still run")
        assertTrue(
            "BitwiseOptimizationPass" in passNames,
            "BitwiseOptimizationPass should still run",
        )
    }

    @Test
    fun `bitwise optimization disabled - no BitwiseOptimizationPass entry in report`() {
        val game = gameWithFoldableExpression()
        val config = AnalysisConfig(maxBanks = 2, bitwiseOptimizationEnabled = false)
        val ctx = makeContext(game, config)
        val pipeline = DefaultPipeline.create(config = config)

        val result = pipeline.execute(ctx)

        assertIs<PassResult.Success>(result)
        val report = result.context.optimizationReport
        val passNames = report.passes.map { it.passName }
        assertFalse(
            "BitwiseOptimizationPass" in passNames,
            "BitwiseOptimizationPass should be absent when bitwiseOptimizationEnabled=false. Got: $passNames",
        )
        assertTrue(
            "DeadCodeEliminationPass" in passNames,
            "DeadCodeEliminationPass should still run",
        )
        assertTrue("ConstantFoldingPass" in passNames, "ConstantFoldingPass should still run")
    }

    @Test
    fun `all IR passes disabled - empty optimization report`() {
        val game = gameWithFoldableExpression()
        val config =
            AnalysisConfig(
                maxBanks = 2,
                constantFoldingEnabled = false,
                deadCodeEliminationEnabled = false,
                bitwiseOptimizationEnabled = false,
            )
        val ctx = makeContext(game, config)
        val pipeline = DefaultPipeline.create(config = config)

        val result = pipeline.execute(ctx)

        assertIs<PassResult.Success>(result)
        val report = result.context.optimizationReport
        assertEquals(
            0,
            report.passes.size,
            "Expected empty optimization report when all IR passes disabled",
        )
    }

    // -------------------------------------------------------------------------
    // Report generation — JSON content
    // -------------------------------------------------------------------------

    @Test
    fun `optimization report toJson produces valid JSON with required fields`() {
        val game = gameWithFoldableExpression()
        val config = AnalysisConfig(maxBanks = 2)
        val ctx = makeContext(game, config)
        val pipeline = DefaultPipeline.create(config = config)

        val result = pipeline.execute(ctx)

        assertIs<PassResult.Success>(result)
        val json = result.context.optimizationReport.toJson()

        assertTrue(json.contains("\"version\""), "JSON should contain 'version' field")
        assertTrue(json.contains("\"passes\""), "JSON should contain 'passes' array")
        assertTrue(json.contains("\"totalRemoved\""), "JSON should contain 'totalRemoved' field")
        assertTrue(
            json.contains("\"totalTransformed\""),
            "JSON should contain 'totalTransformed' field",
        )
        assertTrue(json.contains("\"pass\""), "JSON should contain 'pass' field in each entry")
        assertTrue(json.contains("\"itemsRemoved\""), "JSON should contain 'itemsRemoved' field")
        assertTrue(
            json.contains("\"itemsTransformed\""),
            "JSON should contain 'itemsTransformed' field",
        )
    }

    @Test
    fun `constant folding pass records non-zero itemsTransformed for foldable expressions`() {
        val game = gameWithFoldableExpression()
        val config = AnalysisConfig(maxBanks = 2)
        val ctx = makeContext(game, config)
        val pipeline = DefaultPipeline.create(config = config)

        val result = pipeline.execute(ctx)

        assertIs<PassResult.Success>(result)
        val report = result.context.optimizationReport
        val foldSummary = report.passes.find { it.passName == "ConstantFoldingPass" }
        assertNotNull(foldSummary, "Expected ConstantFoldingPass summary in report")
        assertTrue(
            foldSummary.itemsTransformed > 0,
            "Expected non-zero itemsTransformed for game with foldable expression. Got: ${foldSummary.itemsTransformed}",
        )
    }

    @Test
    fun `dead code elimination pass records unreachable scenes in report`() {
        val game = gameWithDeadScene()
        val config = AnalysisConfig(maxBanks = 2)
        val ctx = makeContext(game, config)
        val pipeline = DefaultPipeline.create(config = config)

        val result = pipeline.execute(ctx)

        assertIs<PassResult.Success>(result)
        val report = result.context.optimizationReport
        val deadSummary = report.passes.find { it.passName == "DeadCodeEliminationPass" }
        assertNotNull(deadSummary, "Expected DeadCodeEliminationPass summary in report")
        assertEquals(
            1,
            deadSummary.itemsRemoved,
            "Expected 1 unreachable scene ('orphan') recorded in report",
        )
    }

    @Test
    fun `optimization report JSON totals are sums of per-pass counts`() {
        val game = gameWithFoldableExpression()
        val config = AnalysisConfig(maxBanks = 2)
        val ctx = makeContext(game, config)
        val pipeline = DefaultPipeline.create(config = config)

        val result = pipeline.execute(ctx)

        assertIs<PassResult.Success>(result)
        val report = result.context.optimizationReport
        val json = report.toJson()
        val expectedTotalRemoved = report.passes.sumOf { it.itemsRemoved }
        val expectedTotalTransformed = report.passes.sumOf { it.itemsTransformed }

        // Verify totals appear in JSON (basic string check — detailed parsing outside test scope)
        assertTrue(
            json.contains("\"totalRemoved\": $expectedTotalRemoved"),
            "JSON totalRemoved should equal sum of per-pass itemsRemoved ($expectedTotalRemoved)",
        )
        assertTrue(
            json.contains("\"totalTransformed\": $expectedTotalTransformed"),
            "JSON totalTransformed should equal sum of per-pass itemsTransformed ($expectedTotalTransformed)",
        )
    }

    // -------------------------------------------------------------------------
    // Report file write to disk
    // -------------------------------------------------------------------------

    @Test
    fun `optimization report JSON file written to outputDirectory when set`() {
        val tmpDir = Files.createTempDirectory("gbkt-opt-test").toFile()
        try {
            val game = gameWithFoldableExpression()
            val config = AnalysisConfig(maxBanks = 2)
            val ctx =
                PassContext(
                    game = game,
                    profile = FakeProfile,
                    config = config,
                    outputDirectory = tmpDir,
                )
            val pipeline = DefaultPipeline.create(config = config)

            val result = pipeline.execute(ctx)

            assertIs<PassResult.Success>(result)
            val reportFile = File(tmpDir, "optimization-report.json")
            assertTrue(
                reportFile.exists(),
                "optimization-report.json should be created in outputDirectory",
            )
            val content = reportFile.readText()
            assertTrue(
                content.contains("\"version\""),
                "Written JSON should contain 'version' field",
            )
            assertTrue(content.contains("\"passes\""), "Written JSON should contain 'passes' array")
            assertTrue(content.isNotBlank(), "Written JSON should be non-empty")
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    @Test
    fun `optimization report JSON file not written when outputDirectory is null`() {
        val game = gameWithFoldableExpression()
        val config = AnalysisConfig(maxBanks = 2)
        val ctx =
            PassContext(game = game, profile = FakeProfile, config = config, outputDirectory = null)
        val pipeline = DefaultPipeline.create(config = config)

        // Should succeed without throwing a NullPointerException or IOException
        val result = pipeline.execute(ctx)

        assertIs<PassResult.Success>(
            result,
            "Pipeline should succeed even when outputDirectory is null",
        )
    }
}
