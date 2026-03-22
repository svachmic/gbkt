/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.analysis.FakeProfile
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.Severity
import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DeadCodeEliminationPassTest {

    private val pass = DeadCodeEliminationPass()

    private fun makeContext(game: GameIR): PassContext =
        PassContext(game = game, profile = FakeProfile, config = AnalysisConfig(maxBanks = 32))

    @Test
    fun `all scenes reachable produces no diagnostics`() {
        // A -> B -> C: all reachable from startScene=A
        val sceneA = SceneIR(id = "A", enterOps = listOf(NavigateTo("B")))
        val sceneB = SceneIR(id = "B", enterOps = listOf(NavigateTo("C")))
        val sceneC = SceneIR(id = "C")
        val game = GameIR(name = "Test", scenes = listOf(sceneA, sceneB, sceneC), startScene = "A")

        val result = pass.run(makeContext(game))

        val success = assertIs<PassResult.Success>(result)
        val infoDiagnostics = success.context.diagnostics.filter { it.severity == Severity.INFO }
        assertTrue(
            infoDiagnostics.isEmpty(),
            "Expected no INFO diagnostics for fully reachable graph, got: $infoDiagnostics",
        )
    }

    @Test
    fun `unreachable scene produces INFO diagnostic`() {
        // A -> B; scene C exists with no incoming edges — unreachable
        val sceneA = SceneIR(id = "A", enterOps = listOf(NavigateTo("B")))
        val sceneB = SceneIR(id = "B")
        val sceneC = SceneIR(id = "C") // unreachable
        val game = GameIR(name = "Test", scenes = listOf(sceneA, sceneB, sceneC), startScene = "A")

        val result = pass.run(makeContext(game))

        val success = assertIs<PassResult.Success>(result)
        val infoDiagnostics = success.context.diagnostics.filter { it.severity == Severity.INFO }
        assertTrue(infoDiagnostics.isNotEmpty(), "Expected INFO diagnostic for unreachable scene C")
        assertTrue(
            infoDiagnostics.any { it.message.contains("C") },
            "Expected diagnostic mentioning scene 'C', got: $infoDiagnostics",
        )
    }

    @Test
    fun `no startScene skips analysis`() {
        // startScene=null: pass through with no diagnostics
        val sceneA = SceneIR(id = "A")
        val sceneB = SceneIR(id = "B") // would be unreachable if startScene were set
        val game = GameIR(name = "Test", scenes = listOf(sceneA, sceneB), startScene = null)

        val result = pass.run(makeContext(game))

        val success = assertIs<PassResult.Success>(result)
        val diagnostics = success.context.diagnostics
        assertTrue(
            diagnostics.isEmpty(),
            "Expected no diagnostics when startScene is null, got: $diagnostics",
        )
    }

    @Test
    fun `multiple unreachable scenes each get a diagnostic`() {
        // A -> B; scenes D and E are unreachable -> 2 INFO diagnostics
        val sceneA = SceneIR(id = "A", enterOps = listOf(NavigateTo("B")))
        val sceneB = SceneIR(id = "B")
        val sceneD = SceneIR(id = "D") // unreachable
        val sceneE = SceneIR(id = "E") // unreachable
        val game =
            GameIR(name = "Test", scenes = listOf(sceneA, sceneB, sceneD, sceneE), startScene = "A")

        val result = pass.run(makeContext(game))

        val success = assertIs<PassResult.Success>(result)
        val infoDiagnostics = success.context.diagnostics.filter { it.severity == Severity.INFO }
        assertTrue(
            infoDiagnostics.size >= 2,
            "Expected at least 2 INFO diagnostics for 2 unreachable scenes, got: $infoDiagnostics",
        )
        assertTrue(
            infoDiagnostics.any { it.message.contains("D") },
            "Expected diagnostic for scene 'D'",
        )
        assertTrue(
            infoDiagnostics.any { it.message.contains("E") },
            "Expected diagnostic for scene 'E'",
        )
    }

    @Test
    fun `transitive reachability works`() {
        // A -> B -> C -> D: all reachable via chain
        val sceneA = SceneIR(id = "A", enterOps = listOf(NavigateTo("B")))
        val sceneB = SceneIR(id = "B", frameOps = listOf(NavigateTo("C")))
        val sceneC = SceneIR(id = "C", exitOps = listOf(NavigateTo("D")))
        val sceneD = SceneIR(id = "D")
        val game =
            GameIR(name = "Test", scenes = listOf(sceneA, sceneB, sceneC, sceneD), startScene = "A")

        val result = pass.run(makeContext(game))

        val success = assertIs<PassResult.Success>(result)
        val infoDiagnostics = success.context.diagnostics.filter { it.severity == Severity.INFO }
        assertTrue(
            infoDiagnostics.isEmpty(),
            "Expected no diagnostics for fully connected chain A->B->C->D, got: $infoDiagnostics",
        )
    }
}
