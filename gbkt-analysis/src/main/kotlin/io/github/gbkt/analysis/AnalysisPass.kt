/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis

/**
 * A single analysis pass that examines a [PassContext] and produces a [PassResult].
 *
 * Passes are composable units of analysis: each pass receives the accumulated context from previous
 * passes and either enriches it (Success) or halts the pipeline (Failed).
 *
 * The functional interface pattern allows passes to be expressed as lambdas:
 * ```kotlin
 * val myPass = AnalysisPass { ctx ->
 *     PassResult.Success(ctx.withDiagnostics(listOf(...)))
 * }
 * ```
 */
fun interface AnalysisPass {
    fun run(context: PassContext): PassResult
}

/**
 * Result of a single [AnalysisPass] execution.
 * - [Success] carries the enriched context to be fed to the next pass.
 * - [Failed] carries diagnostics explaining why analysis halted; no subsequent passes run.
 */
sealed interface PassResult {
    /** Pass succeeded — carry the updated context forward to the next pass. */
    data class Success(val context: PassContext) : PassResult

    /** Pass encountered a hard error — pipeline stops immediately with these diagnostics. */
    data class Failed(val diagnostics: List<Diagnostic>) : PassResult
}
