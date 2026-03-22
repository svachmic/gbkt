/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis

/**
 * Ordered executor for a list of [AnalysisPass] instances.
 *
 * Execution order:
 * 1. [beforePasses] — user-registered pre-processing passes (extension hook)
 * 2. [builtInPasses] — framework passes registered by the backend (resource allocation, validation)
 * 3. [afterPasses] — user-registered post-processing passes (extension hook)
 *
 * **Fail-fast behavior:** The pipeline stops at the first [PassResult.Failed] result. Subsequent
 * passes in the same stage and all later stages are skipped. The failed result is returned
 * directly.
 *
 * **Immutable context:** Each pass receives the [PassContext] returned by the previous pass. The
 * original context is never modified.
 *
 * @property beforePasses Passes that run before all built-in passes (user extension hook).
 * @property builtInPasses Core analysis passes provided by the framework.
 * @property afterPasses Passes that run after all built-in passes (user extension hook).
 */
class PassPipeline(
    private val beforePasses: List<AnalysisPass> = emptyList(),
    private val builtInPasses: List<AnalysisPass>,
    private val afterPasses: List<AnalysisPass> = emptyList(),
) {
    /**
     * Executes all passes in order, threading the [PassContext] through each one.
     *
     * Returns [PassResult.Success] with the final accumulated context if every pass succeeds, or
     * [PassResult.Failed] with the diagnostics from the first failing pass.
     */
    fun execute(initial: PassContext): PassResult {
        var context = initial
        val allPasses = beforePasses + builtInPasses + afterPasses
        for (pass in allPasses) {
            when (val result = pass.run(context)) {
                is PassResult.Success -> context = result.context
                is PassResult.Failed -> return result // Fail fast — no subsequent passes run
            }
        }
        return PassResult.Success(context)
    }
}
