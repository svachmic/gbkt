/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis

import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.analysis.passes.BankingAnalysisPass
import io.github.gbkt.analysis.passes.BitwiseOptimizationPass
import io.github.gbkt.analysis.passes.BudgetAuditPass
import io.github.gbkt.analysis.passes.ConstantFoldingPass
import io.github.gbkt.analysis.passes.ConstraintCheckPass
import io.github.gbkt.analysis.passes.DeadCodeEliminationPass
import io.github.gbkt.analysis.passes.OAMAllocationPass
import io.github.gbkt.analysis.passes.RAMPlanningPass
import io.github.gbkt.analysis.passes.ResourceInventoryPass
import io.github.gbkt.analysis.passes.SemanticValidationPass
import io.github.gbkt.analysis.passes.VRAMLayoutPass

/**
 * Factory for the standard analysis pipeline.
 *
 * Pass order:
 * 1. [SemanticValidationPass] — ref resolution, duplicates
 * 2. [ResourceInventoryPass] — count all resources
 * 3. [ConstraintCheckPass] — hardware limit validation
 * 4. [DeadCodeEliminationPass] — unreachable scene detection (if enabled in config)
 * 5. [ConstantFoldingPass] — compile-time expression evaluation (if enabled in config)
 * 6. [BitwiseOptimizationPass] — power-of-2 arithmetic → bitwise rewrites (if enabled in config)
 * 7. [BankingAnalysisPass] — FFD ROM bank allocation
 * 8. [VRAMLayoutPass] — per-scene tile allocation
 * 9. [OAMAllocationPass] — sprite slot assignment
 * 10. [RAMPlanningPass] — WRAM/HRAM/SRAM layout
 * 11. [BudgetAuditPass] — report generation + hard fail on errors
 *
 * DeadCode and ConstantFolding run before allocation passes so dead code doesn't waste bank space
 * and folded constants give more accurate size estimates. BudgetAuditPass is last — it reads all
 * accumulated annotations.
 *
 * The three optimization passes (4–6) are conditionally included based on [AnalysisConfig] toggle
 * fields. All passes are always-on by default.
 *
 * Use [beforePasses] and [afterPasses] to inject custom passes without modifying the core pipeline.
 */
object DefaultPipeline {
    /**
     * Creates a [PassPipeline] with built-in analysis passes in the correct order.
     *
     * The three IR-level optimization passes are included or skipped based on [config] flags:
     * - [AnalysisConfig.deadCodeEliminationEnabled] controls [DeadCodeEliminationPass]
     * - [AnalysisConfig.constantFoldingEnabled] controls [ConstantFoldingPass]
     * - [AnalysisConfig.bitwiseOptimizationEnabled] controls [BitwiseOptimizationPass]
     *
     * @param config Analysis configuration controlling pass inclusion and thresholds. Defaults to
     *   maximum bank count — callers should supply a config derived from the game's cartridge type.
     * @param beforePasses Custom passes to run before the built-in passes (user extension hook).
     * @param afterPasses Custom passes to run after the built-in passes (user extension hook).
     */
    fun create(
        config: AnalysisConfig = AnalysisConfig(maxBanks = 2),
        beforePasses: List<AnalysisPass> = emptyList(),
        afterPasses: List<AnalysisPass> = emptyList(),
    ): PassPipeline {
        val optPasses = buildList {
            if (config.deadCodeEliminationEnabled) add(DeadCodeEliminationPass())
            if (config.constantFoldingEnabled) add(ConstantFoldingPass())
            if (config.bitwiseOptimizationEnabled) add(BitwiseOptimizationPass())
        }

        return PassPipeline(
            beforePasses = beforePasses,
            builtInPasses =
                buildList {
                    add(SemanticValidationPass())
                    add(ResourceInventoryPass())
                    add(ConstraintCheckPass())
                    addAll(optPasses)
                    add(BankingAnalysisPass())
                    add(VRAMLayoutPass())
                    add(OAMAllocationPass())
                    add(RAMPlanningPass())
                    add(BudgetAuditPass())
                },
            afterPasses = afterPasses,
        )
    }
}
