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
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.RAMLayout
import io.github.gbkt.analysis.Severity

/**
 * Analysis pass that computes WRAM/HRAM/SRAM usage from all IR sources.
 *
 * ### Computation model
 *
 * WRAM usage is the sum of:
 * - **Variable bytes**: sum of [VarType] sizes from [ResourceInventory.variableBytes] (U8/I8 = 1
 *   byte, U16/I16 = 2 bytes)
 * - **Actor state bytes**: `game.actors.size * 5` (x:1, y:1, visible:1, type:1, reserved:1)
 * - **Collection bytes**: [ResourceInventory.collectionBytes] (static collection instances)
 * - **Overhead bytes**: constant 10 bytes (scene management ~4 + camera state ~6)
 *
 * ### Thresholds
 * - WRAM > [MemorySpec.workRam] → hard error with breakdown
 * - WRAM > [AnalysisConfig.wramWarningThreshold] × workRam → warning
 *
 * ### HRAM and SRAM
 * - HRAM: always 0 — no DSL construct targets HRAM directly (future extension point)
 * - SRAM: computed from [GameIR.containers] — each container slot is [CONTAINER_ELEMENT_SIZE] bytes
 *
 * Outputs: Populates [PassContext.ramLayout] with a [RAMLayout] instance.
 *
 * Prerequisites: [ResourceInventoryPass] must run first — [PassContext.inventory] must be non-null.
 */
class RAMPlanningPass : AnalysisPass {

    companion object {
        /**
         * Per-actor state overhead in bytes.
         *
         * Each actor requires: x (u8, 1), y (u8, 1), visible (u8, 1), type (u8, 1), reserved
         * (u8, 1) = 5 bytes.
         */
        const val ACTOR_STATE_BYTES = 5

        /**
         * Constant WRAM overhead for engine subsystems.
         *
         * Breakdown: scene management state ~4 bytes + camera position/target state ~6 bytes = 10.
         */
        const val ENGINE_OVERHEAD_BYTES = 10

        /**
         * Bytes allocated in SRAM per container slot.
         *
         * Layout per slot: category (UINT8, 1) + count (UINT8, 1) + id/padding (2 bytes) = 4 bytes.
         * Used to compute SRAM usage from [GameIR.containers] via `slots * CONTAINER_ELEMENT_SIZE`.
         */
        const val CONTAINER_ELEMENT_SIZE = 4
    }

    override fun run(context: PassContext): PassResult {
        val game = context.game
        val workRam = context.profile.memory.workRam
        val diagnostics = mutableListOf<Diagnostic>()

        // 1. Compute WRAM components
        val variableBytes = context.inventory?.variableBytes ?: computeVariableBytes(context)
        val actorStateBytes = game.actors.size * ACTOR_STATE_BYTES
        val collectionBytes = context.inventory?.collectionBytes ?: 0
        val overheadBytes = ENGINE_OVERHEAD_BYTES
        val totalWram = variableBytes + actorStateBytes + collectionBytes + overheadBytes

        // 2. Hard overflow check
        if (totalWram > workRam) {
            return PassResult.Failed(
                listOf(
                    Diagnostic(
                        id = "ANLZ-05",
                        severity = Severity.ERROR,
                        message =
                            "WRAM overflow: $totalWram bytes required but only $workRam bytes " +
                                "available. Breakdown: variables=${variableBytes}B, " +
                                "actors=${actorStateBytes}B, collections=${collectionBytes}B, " +
                                "overhead=${overheadBytes}B.",
                        suggestion =
                            "Reduce variable count, remove actors, or use SRAM/banking to " +
                                "offload data. Consider replacing large variables with u8 types.",
                    )
                )
            )
        }

        // 3. Warning if near threshold
        val wramWarningBytes = (context.config.wramWarningThreshold * workRam).toInt()
        if (totalWram > wramWarningBytes) {
            diagnostics.add(
                Diagnostic(
                    id = "ANLZ-05",
                    severity = Severity.WARNING,
                    message =
                        "WRAM usage at ${totalWram * 100 / workRam}% ($totalWram / $workRam bytes). " +
                            "Approaching the ${(context.config.wramWarningThreshold * 100).toInt()}% " +
                            "warning threshold.",
                    suggestion =
                        "Consider converting U16 variables to U8 where range permits, or " +
                            "reducing the number of actors to free up WRAM.",
                )
            )
        }

        // 4. HRAM: always 0 — no v2 DSL construct targets HRAM yet.
        //    Extension point: add hramVar() DSL and scan here.
        val hramUsed =
            0 // No v2 DSL construct targets HRAM yet. Extension point: add hramVar() DSL and scan
        // here.

        // 5. SRAM: computed from inventory container slots. Each slot is CONTAINER_ELEMENT_SIZE
        //    bytes (category:1 + count:1 + id/padding:2 = 4 bytes).
        val sramUsed = game.containers.sumOf { it.slots * CONTAINER_ELEMENT_SIZE }

        val ramLayout = RAMLayout(wramUsed = totalWram, hramUsed = hramUsed, sramUsed = sramUsed)

        return PassResult.Success(
            context.copy(ramLayout = ramLayout, diagnostics = context.diagnostics + diagnostics)
        )
    }

    /**
     * Fallback variable byte computation when inventory is not available.
     *
     * Reads directly from [PassContext.game] variables. Prefer [ResourceInventory.variableBytes]
     * when the inventory pass has already run.
     */
    private fun computeVariableBytes(context: PassContext): Int =
        context.game.variables.sumOf { v ->
            when (v.type) {
                io.github.gbkt.core.ir.VarType.U8,
                io.github.gbkt.core.ir.VarType.I8 -> 1
                io.github.gbkt.core.ir.VarType.U16,
                io.github.gbkt.core.ir.VarType.I16 -> 2
            }
        }
}
