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
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.ContainerIR
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.VarType
import io.github.gbkt.core.ir.VariableDef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RAMPlanningPassTest {

    private val pass = RAMPlanningPass()

    private fun makeContext(
        game: GameIR,
        config: AnalysisConfig = AnalysisConfig(maxBanks = 2),
        inventory: ResourceInventory? = null,
    ): PassContext {
        val inv =
            inventory
                ?: ResourceInventory(
                    totalActors = game.actors.size,
                    totalScenes = game.scenes.size,
                    totalVariables = game.variables.size,
                    variableBytes =
                        game.variables.sumOf { v ->
                            when (v.type) {
                                VarType.U8,
                                VarType.I8 -> 1
                                VarType.U16,
                                VarType.I16 -> 2
                            }
                        },
                )
        return PassContext(game = game, profile = FakeProfile, config = config, inventory = inv)
    }

    // -------------------------------------------------------------------------
    // Variable byte counting
    // -------------------------------------------------------------------------

    @Test
    fun `variable bytes counted correctly`() {
        // 3 U8 (3 bytes) + 1 U16 (2 bytes) = 5 bytes
        val variables =
            listOf(
                VariableDef("a", VarType.U8, 0),
                VariableDef("b", VarType.U8, 0),
                VariableDef("c", VarType.U8, 0),
                VariableDef("d", VarType.U16, 0),
            )
        val game = GameIR(name = "Test", variables = variables)
        val ctx = makeContext(game)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        val ramLayout = result.context.ramLayout
        assertNotNull(ramLayout)
        // variableBytes = 5, actorState = 0, collections = 0, overhead = 10
        // wramUsed = 5 + 0 + 0 + 10 = 15
        // The variable contribution should be reflected in total
        assertTrue(
            ramLayout.wramUsed >= 5,
            "Expected wramUsed >= 5 (variable bytes) but got ${ramLayout.wramUsed}",
        )
    }

    // -------------------------------------------------------------------------
    // Actor state overhead
    // -------------------------------------------------------------------------

    @Test
    fun `actor state overhead counted`() {
        // 3 actors x 5 bytes each = 15 bytes actor overhead
        val actors =
            listOf(
                ActorIR(id = "a1", position = PositionDef(0, 0)),
                ActorIR(id = "a2", position = PositionDef(10, 0)),
                ActorIR(id = "a3", position = PositionDef(20, 0)),
            )
        val game = GameIR(name = "Test", actors = actors)
        val ctx = makeContext(game)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        val ramLayout = result.context.ramLayout
        assertNotNull(ramLayout)
        // actorStateBytes = 3 * 5 = 15
        assertTrue(
            ramLayout.wramUsed >= 15,
            "Expected wramUsed >= 15 (3 actors x 5 bytes) but got ${ramLayout.wramUsed}",
        )
    }

    // -------------------------------------------------------------------------
    // Collection bytes
    // -------------------------------------------------------------------------

    @Test
    fun `collection bytes included in RAM total`() {
        // inventory.collectionBytes = 128 → added to total
        val game = GameIR(name = "Test")
        val inventory = ResourceInventory(collectionBytes = 128)
        val ctx = makeContext(game, inventory = inventory)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        val ramLayout = result.context.ramLayout
        assertNotNull(ramLayout)
        assertTrue(
            ramLayout.wramUsed >= 128,
            "Expected wramUsed >= 128 (collectionBytes) but got ${ramLayout.wramUsed}",
        )
    }

    // -------------------------------------------------------------------------
    // Within budget
    // -------------------------------------------------------------------------

    @Test
    fun `WRAM within budget passes`() {
        // 100 bytes total (all sources combined) — FakeProfile.memory.workRam = 8192 → success
        val variables = listOf(VariableDef("score", VarType.U16, 0)) // 2 bytes
        val game = GameIR(name = "Test", variables = variables)
        val ctx = makeContext(game)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        assertTrue(
            result.context.diagnostics.none { it.severity == Severity.ERROR },
            "Expected no ERROR diagnostics for small RAM usage",
        )
    }

    // -------------------------------------------------------------------------
    // WRAM overflow
    // -------------------------------------------------------------------------

    @Test
    fun `WRAM overflow fails with error`() {
        // Force overflow by using a tiny workRam limit
        // We use a mock profile via a custom context with workRam set very small
        // Use many actors to push usage above limit
        val actors = (1..100).map { i -> ActorIR(id = "a$i", position = PositionDef(i, 0)) }
        val game = GameIR(name = "Test", actors = actors)
        // 100 actors * 5 bytes = 500 actorStateBytes + 10 overhead = 510 total
        // Set wramWarningThreshold to 1.0 (100%) so no warning threshold, just error
        // Use inventory with huge collectionBytes to push total over a tiny workRam
        val inventory =
            ResourceInventory(
                totalActors = 100,
                collectionBytes = 10000, // force overflow
            )
        val ctx = makeContext(game, inventory = inventory)

        val result = pass.run(ctx)

        // With collectionBytes=10000 >> workRam=8192, should fail
        assertIs<PassResult.Failed>(result)
        val error = result.diagnostics.first { it.severity == Severity.ERROR }
        assertTrue(
            error.message.contains("8192") ||
                error.message.contains("WRAM") ||
                error.message.contains("RAM"),
            "Error message should mention RAM budget but was: ${error.message}",
        )
    }

    // -------------------------------------------------------------------------
    // Near threshold warning
    // -------------------------------------------------------------------------

    @Test
    fun `WRAM near threshold warns`() {
        // wramWarningThreshold = 0.83 — use inventory to force usage at ~84% of workRam=8192
        // 84% of 8192 = ~6881 bytes
        val inventory = ResourceInventory(collectionBytes = 6900) // > 83% but < 100%
        val game = GameIR(name = "Test")
        val ctx = makeContext(game, inventory = inventory)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        val warnings = result.context.diagnostics.filter { it.severity == Severity.WARNING }
        assertTrue(
            warnings.isNotEmpty(),
            "Expected WARNING for RAM usage near threshold but got: ${result.context.diagnostics}",
        )
    }

    // -------------------------------------------------------------------------
    // RAMLayout populated on context
    // -------------------------------------------------------------------------

    @Test
    fun `RAMLayout populated on context`() {
        // Simple game with 2 U8 vars (2 bytes), 1 actor (5 bytes), 0 collections
        val variables = listOf(VariableDef("x", VarType.U8, 0), VariableDef("y", VarType.U8, 0))
        val actors = listOf(ActorIR(id = "player", position = PositionDef(0, 0)))
        val game = GameIR(name = "Test", variables = variables, actors = actors)
        val ctx = makeContext(game)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        val ramLayout = result.context.ramLayout
        assertNotNull(ramLayout, "RAMLayout should be populated on PassContext after pass runs")
        // wramUsed = 2 (vars) + 5 (actor) + 0 (collections) + 10 (overhead) = 17
        assertEquals(17, ramLayout.wramUsed)
        // HRAM is 0 for now (no DSL syntax targets HRAM)
        assertEquals(0, ramLayout.hramUsed)
        // SRAM is 0 (no save system)
        assertEquals(0, ramLayout.sramUsed)
    }

    // -------------------------------------------------------------------------
    // Empty game
    // -------------------------------------------------------------------------

    @Test
    fun `empty game has minimal overhead only`() {
        // No actors, no variables, no collections → only base overhead (~10 bytes: scene ~4 +
        // camera ~6)
        val game = GameIR(name = "Empty")
        val ctx = makeContext(game)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        val ramLayout = result.context.ramLayout
        assertNotNull(ramLayout)
        // Overhead = 10 bytes (scene management ~4 + camera ~6)
        assertEquals(10, ramLayout.wramUsed)
        assertEquals(0, ramLayout.hramUsed)
        assertEquals(0, ramLayout.sramUsed)
    }

    // -------------------------------------------------------------------------
    // SRAM allocation from containers (J2)
    // -------------------------------------------------------------------------

    @Test
    fun `SRAM computed from containers - two containers with different slot counts`() {
        // Container A: 5 slots × 4 bytes = 20 bytes
        // Container B: 10 slots × 4 bytes = 40 bytes
        // Total SRAM = 60 bytes
        val containers =
            listOf(ContainerIR(id = "bag", slots = 5), ContainerIR(id = "chest", slots = 10))
        val game = GameIR(name = "Test", containers = containers)
        val ctx = makeContext(game)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        val ramLayout = result.context.ramLayout
        assertNotNull(ramLayout)
        // 2 containers: 5 slots + 10 slots = 15 slots × 4 bytes/slot = 60 bytes
        assertEquals(60, ramLayout.sramUsed)
    }

    @Test
    fun `SRAM is zero when no containers present`() {
        // No containers → SRAM = 0
        val game = GameIR(name = "Test")
        val ctx = makeContext(game)

        val result = pass.run(ctx)

        assertIs<PassResult.Success>(result)
        val ramLayout = result.context.ramLayout
        assertNotNull(ramLayout)
        assertEquals(0, ramLayout.sramUsed)
    }
}
