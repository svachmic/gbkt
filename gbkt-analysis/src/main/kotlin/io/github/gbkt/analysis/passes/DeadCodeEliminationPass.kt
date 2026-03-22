/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.analysis.AnalysisPass
import io.github.gbkt.analysis.Diagnostic
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassOptimizationSummary
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.Severity
import io.github.gbkt.core.ir.NavigateTo

/**
 * Analysis pass that detects unreachable scenes via BFS from [GameIR.startScene].
 *
 * ### Algorithm
 * 1. If [GameIR.startScene] is null, skip analysis — no entry point defined.
 * 2. Build a scene transition graph by walking all ScriptOp lists (enterOps, frameOps, exitOps) in
 *    every scene. For each [NavigateTo] op, record an edge from that scene to the target. Nested
 *    ops inside [IfOp], [WhileOp], [ForOp], [FadeOp], and [DialogChoice] are also walked.
 * 3. BFS from [GameIR.startScene] to compute the reachable set.
 * 4. For each scene not in the reachable set, emit an [Severity.INFO] diagnostic.
 *
 * ### Notes
 * - This pass is advisory — it does **not** remove unreachable scenes from [GameIR].
 * - Unreachable scenes often indicate a DSL authoring mistake (forgotten transition).
 * - Diagnostics use ID "ANLZ-01".
 * - A [PassOptimizationSummary] is appended to [PassContext.optimizationReport] with the count of
 *   unreachable scenes detected as [PassOptimizationSummary.itemsRemoved].
 */
class DeadCodeEliminationPass : AnalysisPass {

    override fun run(context: PassContext): PassResult {
        val game = context.game
        val startScene = game.startScene

        // If no start scene defined, skip analysis entirely
        if (startScene == null) {
            val summary =
                PassOptimizationSummary(
                    passName = "DeadCodeEliminationPass",
                    itemsRemoved = 0,
                    details = listOf("Skipped — no start scene defined"),
                )
            return PassResult.Success(
                context.copy(optimizationReport = context.optimizationReport.withSummary(summary))
            )
        }

        // Build transition graph: sceneId -> set of reachable scene IDs
        val transitionGraph = buildTransitionGraph(game)

        // BFS from startScene to compute the reachable set
        val reachable = bfsReachable(startScene, transitionGraph)

        // Emit INFO diagnostics for each unreachable scene
        val diagnostics = mutableListOf<Diagnostic>()
        val deadSceneDetails = mutableListOf<String>()
        for (scene in game.scenes) {
            if (scene.id !in reachable) {
                diagnostics +=
                    Diagnostic(
                        id = "ANLZ-01",
                        severity = Severity.INFO,
                        message =
                            "Scene '${scene.id}' is unreachable from start scene '$startScene'",
                        location = "scene '${scene.id}'",
                        suggestion =
                            "Ensure a NavigateTo('${scene.id}') exists in some reachable scene, " +
                                "or remove the dead scene.",
                    )
                deadSceneDetails += "Unreachable scene: '${scene.id}'"
            }
        }

        val summary =
            PassOptimizationSummary(
                passName = "DeadCodeEliminationPass",
                itemsRemoved = deadSceneDetails.size,
                details = deadSceneDetails,
            )

        val updatedContext =
            context
                .withDiagnostics(diagnostics)
                .copy(optimizationReport = context.optimizationReport.withSummary(summary))

        return PassResult.Success(updatedContext)
    }

}
