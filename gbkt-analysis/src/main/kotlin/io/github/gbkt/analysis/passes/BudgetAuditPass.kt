/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.analysis.passes

import io.github.gbkt.analysis.AnalysisPass
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.Severity
import io.github.gbkt.analysis.report.BudgetReporter

/**
 * Final analysis pass that generates the ASCII budget report and hard-fails if any error-severity
 * diagnostics are present in the accumulated [PassContext].
 *
 * This pass is always last in the pipeline — it reads all accumulated annotations (bank
 * assignments, VRAM assignments, OAM assignments, RAM layout) and produces a human-readable
 * terminal report styled like Rust's `cargo build` output.
 *
 * ### Behaviour
 * 1. Generate the ASCII budget report via [BudgetReporter.formatReport].
 * 2. Store the report on the context via `context.copy(budgetReport = report)`.
 * 3. If any `ERROR`-severity diagnostic exists in [PassContext.diagnostics], return
 *    [PassResult.Failed] with all accumulated diagnostics.
 * 4. Otherwise return [PassResult.Success] with the report-enriched context.
 *
 * The report is printed to the terminal by [GBDKBackend.generate] after pipeline execution, per the
 * locked developer UX decision: "Warnings shown during every build — developer always sees resource
 * pressure."
 */
class BudgetAuditPass : AnalysisPass {

    override fun run(context: PassContext): PassResult {
        // 1. Generate the ASCII budget report from all accumulated annotations
        val report = BudgetReporter.formatReport(context)

        // 2. Store the report on context
        val updatedContext = context.copy(budgetReport = report)

        // 3. Write optimization report JSON to disk (if output directory is set)
        val optReport = context.optimizationReport
        if (optReport.passes.isNotEmpty()) {
            val outputDir = context.outputDirectory
            if (outputDir != null) {
                val reportFile = outputDir.resolve("optimization-report.json")
                reportFile.parentFile?.mkdirs()
                reportFile.writeText(optReport.toJson())
            }
        }

        // 4. Hard-fail if any error-severity diagnostics are present
        val errors = updatedContext.diagnostics.filter { it.severity == Severity.ERROR }
        if (errors.isNotEmpty()) {
            return PassResult.Failed(updatedContext.diagnostics)
        }

        // 5. All clean — return success with the budget report on context
        return PassResult.Success(updatedContext)
    }
}
