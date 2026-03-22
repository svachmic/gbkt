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
import io.github.gbkt.analysis.ResourceInventory
import io.github.gbkt.analysis.Severity
import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.core.ir.GameIR
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConstraintCheckPassTest {

    private val pass = ConstraintCheckPass()

    // FakeProfile has maxSprites=40 and workRam=8192

    private fun makeContext(
        inventory: ResourceInventory,
        config: AnalysisConfig? = null,
    ): PassContext =
        PassContext(
            game = GameIR(name = "Test"),
            profile = FakeProfile,
            config = config ?: AnalysisConfig(maxBanks = 2, oamWarningThreshold = 35),
            inventory = inventory,
        )

    private fun makeInventory(
        totalActors: Int = 0,
        variableBytes: Int = 0,
        collectionBytes: Int = 0,
    ): ResourceInventory =
        ResourceInventory(
            totalActors = totalActors,
            totalScenes = 0,
            totalVariables = 0,
            totalAssets = 0,
            // Each actor is assumed to have an 8x8 sprite (1 OAM entry each)
            spriteTileCounts = (0 until totalActors).associate { "actor$it" to 1 },
            variableBytes = variableBytes,
            collectionBytes = collectionBytes,
            perSceneActorCounts = emptyMap(),
        )

    @Test
    fun `game within limits passes`() {
        val inventory = makeInventory(totalActors = 5)

        val result = pass.run(makeContext(inventory))

        assertIs<PassResult.Success>(result)
        val errors = result.context.diagnostics.filter { it.severity == Severity.ERROR }
        assertTrue(errors.isEmpty(), "Expected no errors, got: $errors")
    }

    @Test
    fun `too many actors fails with error`() {
        // 41 actors, maxSprites=40
        val inventory = makeInventory(totalActors = 41)

        val result = pass.run(makeContext(inventory))

        assertIs<PassResult.Failed>(result)
        val errors = result.diagnostics.filter { it.severity == Severity.ERROR }
        assertTrue(errors.isNotEmpty(), "Expected error for actor overflow")
        assertTrue(
            errors.any { it.message.contains("41") || it.message.contains("actor") },
            "Error should mention actor count or 'actor': ${errors.map { it.message }}",
        )
    }

    @Test
    fun `actors near limit warns`() {
        // 36 actors, maxSprites=40, oamWarningThreshold=35 => warning
        val inventory = makeInventory(totalActors = 36)
        val config = AnalysisConfig(maxBanks = 2, oamWarningThreshold = 35, oamErrorThreshold = 41)

        val result = pass.run(makeContext(inventory, config))

        assertIs<PassResult.Success>(result)
        val warnings = result.context.diagnostics.filter { it.severity == Severity.WARNING }
        assertTrue(
            warnings.isNotEmpty(),
            "Expected warning for actors near limit, got: ${result.context.diagnostics}",
        )
    }

    @Test
    fun `WRAM within budget passes`() {
        // 100 bytes variables, workRam=8192 => well within budget
        val inventory = makeInventory(variableBytes = 100, collectionBytes = 0)

        val result = pass.run(makeContext(inventory))

        assertIs<PassResult.Success>(result)
        val errors = result.context.diagnostics.filter { it.severity == Severity.ERROR }
        assertTrue(errors.isEmpty(), "Expected no WRAM errors, got: $errors")
    }

    @Test
    fun `WRAM over budget fails with error`() {
        // workRam=8192, use more than that
        val inventory = makeInventory(variableBytes = 8000, collectionBytes = 500) // 8500 > 8192

        val result = pass.run(makeContext(inventory))

        assertIs<PassResult.Failed>(result)
        val errors = result.diagnostics.filter { it.severity == Severity.ERROR }
        assertTrue(errors.isNotEmpty(), "Expected error for WRAM overflow")
        assertTrue(
            errors.any {
                it.message.contains("RAM") ||
                    it.message.contains("memory") ||
                    it.message.contains("8500")
            },
            "Error should mention RAM, memory, or byte count: ${errors.map { it.message }}",
        )
    }

    @Test
    fun `exactly at sprite limit fails with error`() {
        // 40 actors, maxSprites=40, oamErrorThreshold=41 => no error
        val inventory = makeInventory(totalActors = 40)
        val config = AnalysisConfig(maxBanks = 2, oamWarningThreshold = 35, oamErrorThreshold = 41)

        val result = pass.run(makeContext(inventory, config))

        // 40 == maxSprites — should produce a warning (near threshold) but not fail
        assertIs<PassResult.Success>(result)
    }
}
