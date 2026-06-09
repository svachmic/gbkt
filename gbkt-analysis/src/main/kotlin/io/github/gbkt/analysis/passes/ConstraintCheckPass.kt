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
import io.github.gbkt.analysis.Severity

/**
 * Analysis pass that validates resource inventory counts against hardware limits from
 * [TargetProfile].
 *
 * Prerequisites: [ResourceInventoryPass] must run before this pass — [context.inventory] must be
 * non-null.
 *
 * Checks performed:
 * - Total actors vs [SpriteSpec.maxSprites]: error if exceeded, warning if above
 *   [AnalysisConfig.oamWarningThreshold]
 * - Total variable + collection bytes vs [MemorySpec.workRam]: error if exceeded, warning if above
 *   the warning threshold fraction
 *
 * Returns [PassResult.Failed] on any ERROR diagnostic; [PassResult.Success] with warnings
 * otherwise.
 */
class ConstraintCheckPass : AnalysisPass {

    override fun run(context: PassContext): PassResult {
        val inventory =
            requireNotNull(context.inventory) {
                "ConstraintCheckPass requires a non-null ResourceInventory on PassContext. " +
                    "Ensure ResourceInventoryPass runs before ConstraintCheckPass."
            }

        val diagnostics = mutableListOf<Diagnostic>()
        checkOamBudget(inventory, context, diagnostics)
        checkWramBudget(inventory, context, diagnostics)

        val errors = diagnostics.filter { it.severity == Severity.ERROR }
        return if (errors.isNotEmpty()) {
            PassResult.Failed(diagnostics)
        } else {
            PassResult.Success(context.withDiagnostics(diagnostics))
        }
    }

    /**
     * Validates total OAM entries against [SpriteSpec.maxSprites]. Uses total OAM entries
     * (accounting for multi-tile sprites — 16x16 needs 4 entries, 8x16 needs 2, etc.) rather than a
     * naive actor count.
     */
    private fun checkOamBudget(
        inventory: io.github.gbkt.analysis.ResourceInventory,
        context: PassContext,
        diagnostics: MutableList<Diagnostic>,
    ) {
        val profile = context.profile
        val config = context.config
        val totalOamEntries = inventory.spriteTileCounts.values.sum()
        val maxSprites = profile.sprites.maxSprites
        when {
            totalOamEntries >= config.oamErrorThreshold || totalOamEntries > maxSprites -> {
                diagnostics +=
                    Diagnostic(
                        id = "ANLZ-02",
                        severity = Severity.ERROR,
                        message =
                            "Game needs $totalOamEntries OAM entries for ${inventory.totalActors} actors " +
                                "but hardware limit is $maxSprites OAM sprites. " +
                                "Reduce actor count, use smaller sprites, or use sprite pooling.",
                        location = "game.actors",
                        suggestion =
                            "Use sprite pooling or reduce the number of simultaneously active actors. " +
                                "16x16 sprites use 4 OAM entries each.",
                    )
            }
            totalOamEntries >= config.oamWarningThreshold -> {
                diagnostics +=
                    Diagnostic(
                        id = "ANLZ-02",
                        severity = Severity.WARNING,
                        message =
                            "Game needs $totalOamEntries OAM entries for ${inventory.totalActors} actors " +
                                "— approaching the hardware OAM limit of $maxSprites sprites " +
                                "(warning threshold: ${config.oamWarningThreshold}).",
                        location = "game.actors",
                        suggestion =
                            "Monitor OAM usage as you add more actors; maximum is $maxSprites entries.",
                    )
            }
        }
    }

    /**
     * Validates total WRAM consumption (variables + collections + actor state + engine overhead)
     * against [MemorySpec.workRam].
     */
    private fun checkWramBudget(
        inventory: io.github.gbkt.analysis.ResourceInventory,
        context: PassContext,
        diagnostics: MutableList<Diagnostic>,
    ) {
        val profile = context.profile
        val config = context.config
        val actorStateBytes = context.game.actors.size * RAMPlanningPass.ACTOR_STATE_BYTES
        val overheadBytes = RAMPlanningPass.ENGINE_OVERHEAD_BYTES
        val totalRamBytes =
            inventory.variableBytes + inventory.collectionBytes + actorStateBytes + overheadBytes
        val workRam = profile.memory.workRam
        val wramWarningBytes = (workRam * config.wramWarningThreshold).toInt()
        when {
            totalRamBytes > workRam -> {
                diagnostics +=
                    Diagnostic(
                        id = "ANLZ-03",
                        severity = Severity.ERROR,
                        message =
                            "Variables and collections consume $totalRamBytes bytes of WRAM but " +
                                "hardware limit is $workRam bytes. Reduce variable or collection usage.",
                        location = "game.variables",
                        suggestion =
                            "Use smaller variable types (U8 instead of U16) or reduce collection sizes.",
                    )
            }
            totalRamBytes > wramWarningBytes -> {
                diagnostics +=
                    Diagnostic(
                        id = "ANLZ-03",
                        severity = Severity.WARNING,
                        message =
                            "Variables and collections consume $totalRamBytes bytes of WRAM " +
                                "(${(totalRamBytes * 100 / workRam)}% of $workRam byte limit).",
                        location = "game.variables",
                        suggestion =
                            "WRAM is approaching capacity. Review variable and collection sizes.",
                    )
            }
        }
    }
}
