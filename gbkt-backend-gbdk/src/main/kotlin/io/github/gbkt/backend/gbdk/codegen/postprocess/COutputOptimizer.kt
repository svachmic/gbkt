/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.postprocess

// =============================================================================
// C OUTPUT OPTIMIZER
// Orchestrates C-output-level optimization passes on generated C text.
//
// Pipeline (both passes enabled by default):
//   1. SharedConstantTablePass  — deduplicates identical const arrays
//   2. FunctionDeduplicationPass — deduplicates identical function bodies
//
// Each pass is individually toggleable via constructor parameters.
// Plan 08 (integration) wires this into GBDKPipelineV2, reading toggles from
// AnalysisConfig. This class has NO compile-time dependency on AnalysisConfig.
// =============================================================================

/**
 * Combined summary of all C-output optimization passes applied to a set of files.
 *
 * @property constantArraysDeduped Total number of duplicate constant arrays replaced across all
 *   files.
 * @property functionsDeduped Total number of duplicate functions replaced across all files.
 * @property details Human-readable descriptions of each individual optimization applied.
 */
data class COutputOptimizationSummary(
    val constantArraysDeduped: Int = 0,
    val functionsDeduped: Int = 0,
    val details: List<String> = emptyList(),
)

/**
 * Orchestrator that applies C-output post-processing passes to a map of generated C files.
 *
 * Each pass is applied independently to each file. Passes run in sequence: constant deduplication
 * first, then function deduplication.
 *
 * @param sharedConstantTablesEnabled Enable/disable [SharedConstantTablePass].
 * @param functionDeduplicationEnabled Enable/disable [FunctionDeduplicationPass].
 */
class COutputOptimizer(
    val sharedConstantTablesEnabled: Boolean = true,
    val functionDeduplicationEnabled: Boolean = true,
) {

    /**
     * Apply enabled optimization passes to the given map of C files.
     *
     * Function deduplication redirects are applied cross-file: if `dpad_held` is deduplicated into
     * `button_held` in main.c, call sites in bank1.c and prototypes in game.h are also rewritten.
     *
     * @param files Map of filename to C source content.
     * @return A pair of the optimized file map and a summary of what was changed.
     */
    fun optimize(
        files: Map<String, String>
    ): Pair<Map<String, String>, COutputOptimizationSummary> {
        var totalConstDeduped = 0
        var totalFuncDeduped = 0
        val allDetails = mutableListOf<String>()

        // Collect all cross-file redirects from function deduplication
        val allRedirects = mutableMapOf<String, String>()

        var optimized =
            files.mapValues { (_, content) ->
                var result = content

                if (sharedConstantTablesEnabled) {
                    val constResult = SharedConstantTablePass.optimize(result)
                    result = constResult.optimizedContent
                    totalConstDeduped += constResult.arraysDeduped
                    allDetails.addAll(constResult.details)
                }

                if (functionDeduplicationEnabled) {
                    val funcResult = FunctionDeduplicationPass.optimize(result)
                    result = funcResult.optimizedContent
                    totalFuncDeduped += funcResult.functionsDeduped
                    allDetails.addAll(funcResult.details)
                    allRedirects.putAll(funcResult.redirects)
                }

                result
            }

        // Apply cross-file call-site rewrites (e.g. bank1.c calling a function deduped in main.c)
        if (allRedirects.isNotEmpty()) {
            optimized =
                optimized.mapValues { (_, content) ->
                    var result = content
                    for ((dupName, canonicalName) in allRedirects) {
                        val pattern = Regex("""\b${Regex.escape(dupName)}\b""")
                        result = pattern.replace(result, canonicalName)
                    }
                    result
                }
        }

        val summary =
            COutputOptimizationSummary(
                constantArraysDeduped = totalConstDeduped,
                functionsDeduped = totalFuncDeduped,
                details = allDetails,
            )

        return optimized to summary
    }
}
