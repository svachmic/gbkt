/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis

import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-pass optimization summary recorded by each IR-level optimization pass.
 *
 * @property passName Human-readable name of the pass (e.g. "ConstantFoldingPass").
 * @property itemsRemoved Number of IR nodes removed (e.g. dead scenes, unreachable code).
 * @property itemsTransformed Number of IR nodes rewritten in-place (e.g. folded expressions,
 *   bitwise rewrites).
 * @property details Human-readable descriptions of specific transformations applied.
 */
data class PassOptimizationSummary(
    val passName: String,
    val itemsRemoved: Int = 0,
    val itemsTransformed: Int = 0,
    val details: List<String> = emptyList(),
)

/**
 * Accumulated optimization report built up through the analysis pass chain.
 *
 * Each IR-level optimization pass adds a [PassOptimizationSummary] via [withSummary]. The final
 * report is written to `optimization-report.json` in the build output directory by
 * [BudgetAuditPass].
 *
 * @property passes Per-pass summaries in pipeline execution order.
 */
data class OptimizationReport(val passes: List<PassOptimizationSummary> = emptyList()) {
    /**
     * Serializes this report to a formatted JSON string.
     *
     * The JSON structure is:
     * ```json
     * {
     *   "version": 1,
     *   "totalRemoved": 3,
     *   "totalTransformed": 7,
     *   "passes": [
     *     { "pass": "ConstantFoldingPass", "itemsRemoved": 0, "itemsTransformed": 4, "details": [...] },
     *     ...
     *   ]
     * }
     * ```
     */
    fun toJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        val passArray = JSONArray()
        for (summary in passes) {
            val obj = JSONObject()
            obj.put("pass", summary.passName)
            obj.put("itemsRemoved", summary.itemsRemoved)
            obj.put("itemsTransformed", summary.itemsTransformed)
            obj.put("details", JSONArray(summary.details))
            passArray.put(obj)
        }
        root.put("passes", passArray)
        root.put("totalRemoved", passes.sumOf { it.itemsRemoved })
        root.put("totalTransformed", passes.sumOf { it.itemsTransformed })
        return root.toString(2)
    }

    /**
     * Returns a new [OptimizationReport] with [summary] appended to the passes list.
     *
     * This immutable accumulation pattern matches the [PassContext] copy-and-replace convention.
     */
    fun withSummary(summary: PassOptimizationSummary): OptimizationReport =
        copy(passes = passes + summary)
}
