/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk

import io.github.gbkt.analysis.DefaultPipeline
import io.github.gbkt.analysis.PassContext
import io.github.gbkt.analysis.PassOptimizationSummary
import io.github.gbkt.analysis.PassResult
import io.github.gbkt.analysis.Severity
import io.github.gbkt.analysis.config.AnalysisConfig
import io.github.gbkt.backend.api.CodegenBackend
import io.github.gbkt.backend.api.GeneratedFile
import io.github.gbkt.backend.api.GenerationOptions
import io.github.gbkt.backend.api.GenerationResult
import io.github.gbkt.backend.api.ValidationResult
import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipelineV2
import io.github.gbkt.backend.gbdk.codegen.postprocess.COutputOptimizer
import io.github.gbkt.backend.gbdk.profiles.GameBoyColorProfile
import io.github.gbkt.core.AssetManifest
import io.github.gbkt.core.constraints.TargetProfile
import io.github.gbkt.core.ir.GameIR

/**
 * GBDK-2020 backend for Game Boy / Game Boy Color.
 *
 * This backend generates GBDK-compatible C code from v2 [GameIR] using the typed C AST pipeline.
 */
class GBDKBackend(override val profile: TargetProfile = GameBoyColorProfile) : CodegenBackend {
    override val id = "gbdk"
    override val displayName = "GBDK-2020 for ${profile.name}"
    override val romExtension = if (profile.id == "gbc") "gbc" else "gb"

    override fun validate(game: GameIR): ValidationResult {
        // Basic validation — full constraint checking is handled by the analysis pipeline
        // inside generate(). Return SUCCESS here; analysis errors surface as
        // GenerationResult.failed.
        return ValidationResult.SUCCESS
    }

    /**
     * Generate C source files from a [GameIR] using the typed C AST pipeline.
     *
     * Runs the full ten-pass analysis pipeline before code generation:
     * 1. Analysis pipeline validates IR, allocates resources, and generates the budget report.
     * 2. Budget report is printed to stdout per the locked developer UX decision.
     * 3. Bank/VRAM/OAM annotations are applied to the GameIR copy.
     * 4. Annotated IR is passed to [GBDKPipelineV2] for C code generation.
     *
     * @return [GenerationResult] containing main.c, bank1.c, and game.h
     */
    @Suppress("TooGenericExceptionCaught")
    override fun generate(game: GameIR, options: GenerationOptions): GenerationResult {
        return generateV2(game)
    }

    /**
     * Generate C source files from a [GameIR] using the typed C AST pipeline.
     *
     * This method is kept for backward compatibility with code that calls generateV2 directly via
     * reflection (e.g., the Gradle plugin).
     *
     * @param gameIR The game IR to compile.
     * @param assetManifest Optional asset manifest produced by the asset pipeline. When provided,
     *   analysis passes (e.g., [VRAMLayoutPass]) use actual tile counts instead of heuristic
     *   estimates. Wired to [PassContext.assetManifest].
     * @param outputDirectory Optional build output directory. When provided, [BudgetAuditPass]
     *   writes `optimization-report.json` here. Wired to [PassContext.outputDirectory].
     */
    @Suppress("TooGenericExceptionCaught")
    fun generateV2(
        gameIR: GameIR,
        assetManifest: AssetManifest? = null,
        outputDirectory: java.io.File? = null,
    ): GenerationResult {
        return try {
            // 1. Run analysis pipeline
            val analysisConfig = AnalysisConfig.fromCartridgeConfig(gameIR.config)
            val pipeline = DefaultPipeline.create()
            val initialContext =
                PassContext(
                    game = gameIR,
                    profile = profile,
                    config = analysisConfig,
                    assetManifest = assetManifest,
                    outputDirectory = outputDirectory,
                )
            val analysisResult = pipeline.execute(initialContext)

            // Print budget report if available (shown during every build per locked decision)
            if (analysisResult is PassResult.Success) {
                analysisResult.context.budgetReport?.let { println(it) }
            }

            if (analysisResult is PassResult.Failed) {
                val errorMessages =
                    analysisResult.diagnostics
                        .filter { it.severity == Severity.ERROR }
                        .joinToString("\n") { "[${it.id}] ${it.message}" }
                return GenerationResult.failed("Analysis failed:\n$errorMessages")
            }

            val annotatedContext = (analysisResult as PassResult.Success).context

            // 2. Apply bank/VRAM/OAM annotations to GameIR (produces annotated copy)
            val annotatedGame = applyAnnotations(annotatedContext.game, annotatedContext)

            // 3. Generate C code from annotated IR
            val pipelineV2 = GBDKPipelineV2()
            val output = pipelineV2.generate(annotatedGame)

            // 4. Apply C-output optimizations (shared constant tables + function deduplication)
            val optimizer =
                COutputOptimizer(
                    sharedConstantTablesEnabled = analysisConfig.sharedConstantTablesEnabled,
                    functionDeduplicationEnabled = analysisConfig.functionDeduplicationEnabled,
                )
            val (optimizedFiles, cOutputSummary) = optimizer.optimize(output.files)

            // 4a. Append C-output summary to optimization report if any deduplication occurred
            var report = annotatedContext.optimizationReport
            if (cOutputSummary.constantArraysDeduped > 0) {
                report =
                    report.withSummary(
                        PassOptimizationSummary(
                            passName = "SharedConstantTables",
                            itemsRemoved = cOutputSummary.constantArraysDeduped,
                            details = cOutputSummary.details.filter { "constant" in it.lowercase() },
                        )
                    )
            }
            if (cOutputSummary.functionsDeduped > 0) {
                report =
                    report.withSummary(
                        PassOptimizationSummary(
                            passName = "FunctionDeduplication",
                            itemsRemoved = cOutputSummary.functionsDeduped,
                            details = cOutputSummary.details.filter { "function" in it.lowercase() },
                        )
                    )
            }

            // 4b. Write updated report to disk if outputDirectory was set and report changed
            if (
                outputDirectory != null &&
                    report.passes.size > annotatedContext.optimizationReport.passes.size
            ) {
                val reportFile = java.io.File(outputDirectory, "optimization-report.json")
                reportFile.parentFile?.mkdirs()
                reportFile.writeText(report.toJson())
            }

            val generatedFiles =
                optimizedFiles.mapValues { (path, content) ->
                    GeneratedFile(
                        path = path,
                        content = content,
                        description = "Generated by GBDKPipelineV2",
                        sourceMapJson = output.sourceMaps[path],
                    )
                }
            GenerationResult(success = true, files = generatedFiles)
        } catch (e: Exception) {
            GenerationResult.failed(e.message ?: "V2 code generation failed")
        }
    }

    /**
     * Produces an annotated copy of [game] with bank/VRAM/OAM slots filled from [context].
     *
     * Uses data class [copy] to produce immutable IR nodes — no mutation of existing nodes.
     */
    private fun applyAnnotations(game: GameIR, context: PassContext): GameIR {
        val annotatedScenes =
            game.scenes.map { scene ->
                scene.copy(
                    bankSlot = context.bankAssignments[scene.id] ?: scene.bankSlot,
                    vramRange = context.vramAssignments[scene.id] ?: scene.vramRange,
                )
            }
        val annotatedActors =
            game.actors.map { actor ->
                actor.copy(
                    bankSlot = context.bankAssignments[actor.id] ?: actor.bankSlot,
                    vramRange = context.vramAssignments[actor.id] ?: actor.vramRange,
                    oamSlot = context.oamAssignments[actor.id] ?: actor.oamSlot,
                )
            }
        return game.copy(scenes = annotatedScenes, actors = annotatedActors)
    }

    companion object {
        /** Create backend for Game Boy (DMG). */
        fun forGameBoy() = GBDKBackend(io.github.gbkt.backend.gbdk.profiles.GameBoyProfile)

        /** Create backend for Game Boy Color. */
        fun forGameBoyColor() = GBDKBackend(GameBoyColorProfile)
    }
}
