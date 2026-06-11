/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk

import io.github.gbkt.analysis.DefaultPipeline
import io.github.gbkt.analysis.OptimizationReport
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
import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.backend.gbdk.codegen.postprocess.COutputOptimizationSummary
import io.github.gbkt.backend.gbdk.codegen.postprocess.COutputOptimizer
import io.github.gbkt.backend.gbdk.profiles.GameBoyColorProfile
import io.github.gbkt.core.AssetManifest
import io.github.gbkt.core.constraints.TargetProfile
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.nextPowerOfTwo

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

    /** Satisfies the [CodegenBackend] interface; delegates to the full-signature overload. */
    override fun generate(game: GameIR, options: GenerationOptions): GenerationResult =
        generate(gameIR = game, assetManifest = null, outputDirectory = null, assetRoot = null)

    /**
     * Generate C source files from a [GameIR] using the typed C AST pipeline.
     *
     * Runs the full ten-pass analysis pipeline before code generation:
     * 1. Analysis pipeline validates IR, allocates resources, and generates the budget report.
     * 2. Budget report is printed to stdout per the locked developer UX decision.
     * 3. Bank/VRAM/OAM annotations are applied to the GameIR copy.
     * 4. Annotated IR is passed to [GBDKPipeline] for C code generation.
     *
     * @param gameIR The game IR to compile.
     * @param assetManifest Optional asset manifest produced by the asset pipeline. When provided,
     *   analysis passes (e.g., [VRAMLayoutPass]) use actual tile counts instead of heuristic
     *   estimates. Wired to [PassContext.assetManifest].
     * @param outputDirectory Optional build output directory. When provided, [BudgetAuditPass]
     *   writes `optimization-report.json` here. Wired to [PassContext.outputDirectory].
     * @param assetRoot Optional project asset root directory. When provided,
     *   [io.github.gbkt.analysis.passes.BankingAnalysisPass] enables the cumulative tilemap-bank
     *   overflow guard (Phase 12.2 REQ-5 / D-claude-2). Wired to [PassContext.assetRoot]. When null
     *   (the current Gradle-plugin default), the overflow guard is skipped silently and the
     *   existing build behavior is unchanged.
     */
    @JvmOverloads
    @Suppress("TooGenericExceptionCaught")
    fun generate(
        gameIR: GameIR,
        assetManifest: AssetManifest? = null,
        outputDirectory: java.io.File? = null,
        assetRoot: java.io.File? = null,
    ): GenerationResult {
        return try {
            // D-05/D-06: derive romBanks when omitted, or validate an explicit value. This runs
            // before AnalysisConfig construction so the effective bank count is known before the
            // real pipeline pass.
            val effectiveRomBanks = deriveEffectiveRomBanks(gameIR, assetManifest, assetRoot)

            // 1. Run analysis pipeline
            val analysisConfig =
                AnalysisConfig.fromCartridgeConfig(gameIR.config.copy(romBanks = effectiveRomBanks))
            val pipeline = DefaultPipeline.create()
            val initialContext =
                PassContext(
                    game = gameIR.copy(config = gameIR.config.copy(romBanks = effectiveRomBanks)),
                    profile = profile,
                    config = analysisConfig,
                    assetManifest = assetManifest,
                    outputDirectory = outputDirectory,
                    assetRoot = assetRoot,
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
            val codegenPipeline = GBDKPipeline()
            val output = codegenPipeline.generate(annotatedGame)

            // 4. Apply C-output optimizations (shared constant tables + function deduplication)
            val optimizer =
                COutputOptimizer(
                    sharedConstantTablesEnabled = analysisConfig.sharedConstantTablesEnabled,
                    functionDeduplicationEnabled = analysisConfig.functionDeduplicationEnabled,
                )
            val (optimizedFiles, cOutputSummary) = optimizer.optimize(output.files)

            // 4a. Append C-output summary to optimization report if any deduplication occurred
            val report = appendCOutputSummaries(annotatedContext.optimizationReport, cOutputSummary)

            // 4b. Write updated report to disk if outputDirectory was set and report changed
            if (
                outputDirectory != null &&
                    report.passes.size > annotatedContext.optimizationReport.passes.size
            ) {
                val reportFile = java.io.File(outputDirectory, "optimization-report.json")
                reportFile.parentFile?.mkdirs()
                reportFile.writeText(report.toJson())
            }

            val generatedFiles = optimizedFiles.mapValues { (path, content) ->
                GeneratedFile(
                    path = path,
                    content = content,
                    description = "Generated by GBDKPipeline",
                    sourceMapJson = output.sourceMaps[path],
                )
            }
            GenerationResult(success = true, files = generatedFiles)
        } catch (e: Exception) {
            GenerationResult.failed(e.message ?: "code generation failed")
        }
    }

    /**
     * Appends per-pass summaries for the C-output optimizations (shared constant tables, function
     * deduplication) to the optimization report when any deduplication occurred.
     */
    private fun appendCOutputSummaries(
        report: OptimizationReport,
        cOutputSummary: COutputOptimizationSummary,
    ): OptimizationReport {
        var updated = report
        if (cOutputSummary.constantArraysDeduped > 0) {
            updated =
                updated.withSummary(
                    PassOptimizationSummary(
                        passName = "SharedConstantTables",
                        itemsRemoved = cOutputSummary.constantArraysDeduped,
                        details = cOutputSummary.details.filter { "constant" in it.lowercase() },
                    )
                )
        }
        if (cOutputSummary.functionsDeduped > 0) {
            updated =
                updated.withSummary(
                    PassOptimizationSummary(
                        passName = "FunctionDeduplication",
                        itemsRemoved = cOutputSummary.functionsDeduped,
                        details = cOutputSummary.details.filter { "function" in it.lowercase() },
                    )
                )
        }
        return updated
    }

    /**
     * D-05/D-06: derive the effective ROM bank count when `romBanks` is omitted, or pass an
     * explicit value through as-is (D-06 validation runs inside `BankingAnalysisPass`).
     *
     * D-05 derivation runs a `BankingAnalysisPass` probe with an unconstrained maxBanks (type max)
     * so every scene can be assigned; the highest assigned bank determines the minimum needed,
     * rounded up to a power of two and clamped to the cartridge type's bank cap. The probe passes
     * outputDirectory=null to suppress BudgetAuditPass file writes (Pitfall 1).
     */
    private fun deriveEffectiveRomBanks(
        gameIR: GameIR,
        assetManifest: AssetManifest?,
        assetRoot: java.io.File?,
    ): Int =
        when (val declared = gameIR.config.romBanks) {
            null -> {
                val probeConfig =
                    AnalysisConfig.fromCartridgeConfig(
                        gameIR.config.copy(romBanks = null).let { cfg ->
                            // Derive typeMax from the cartridge type (unconstrained ceiling).
                            // fromCartridgeConfig with romBanks=null gives maxBanks=typeMax.
                            val typeMaxConfig = AnalysisConfig.fromCartridgeConfig(cfg)
                            cfg.copy(romBanks = typeMaxConfig.maxBanks)
                        }
                    )
                val probeContext =
                    PassContext(
                        game = gameIR,
                        profile = profile,
                        config = probeConfig,
                        assetManifest = assetManifest,
                        outputDirectory = null, // Pitfall 1: suppress BudgetAuditPass file writes
                        assetRoot = assetRoot,
                    )
                val probeResult = DefaultPipeline.create().execute(probeContext)
                val maxBank =
                    if (probeResult is PassResult.Success)
                        probeResult.context.bankAssignments.values.maxOfOrNull { it.bank } ?: 0
                    else 0
                val cartridgeCap = gameIR.config.cartridge.maxRomBanks
                minOf(maxOf(2, nextPowerOfTwo(maxBank + 1)), cartridgeCap)
            }
            else -> declared
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
