/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import io.github.gbkt.gradle.internal.BackendReflection
import io.github.gbkt.gradle.internal.GenerationResultWrapper
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

/**
 * Task that generates GBDK C code from Kotlin game definitions.
 *
 * This task:
 * 1. Loads compiled Kotlin classes containing the game definition
 * 2. Finds the specified game property via reflection
 * 3. Calls compileWithAssets() to generate C code
 * 4. Writes the output to a file
 */
@CacheableTask
abstract class GenerateCTask @Inject constructor(private val workerExecutor: WorkerExecutor) :
    DefaultTask() {

    /** Game definition in format "package.ClassName::propertyName". */
    @get:Input abstract val gameSpec: Property<String>

    /** Target platform for code generation (e.g., "gbc", "gb"). */
    @get:Input @get:Optional abstract val target: Property<String>

    /** Directory containing sprite assets for the asset pipeline. */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val assetDirectory: DirectoryProperty

    /** Output directory for generated C files (one per bank). */
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    /** Runtime classpath containing compiled classes and gbkt-core. */
    @get:Classpath abstract val runtimeClasspath: ConfigurableFileCollection

    // ==================== Optimization Settings ====================

    /** Enable asset optimization analysis during build. */
    @get:Input @get:Optional abstract val optimizationEnabled: Property<Boolean>

    /** Show per-asset details in optimization output. */
    @get:Input @get:Optional abstract val optimizationVerbose: Property<Boolean>

    /** Suppress output when all assets are optimal. */
    @get:Input @get:Optional abstract val optimizationQuietWhenOptimal: Property<Boolean>

    /** Enable duplicate tile detection. */
    @get:Input @get:Optional abstract val detectDuplicates: Property<Boolean>

    /** Enable empty tile detection. */
    @get:Input @get:Optional abstract val detectEmpty: Property<Boolean>

    /** Enable low-entropy tile detection. */
    @get:Input @get:Optional abstract val detectLowEntropy: Property<Boolean>

    /** Threshold for low-entropy detection. */
    @get:Input @get:Optional abstract val lowEntropyThreshold: Property<Float>

    /** Use ANSI colors in output. */
    @get:Input @get:Optional abstract val useColor: Property<Boolean>

    /** Use Unicode characters in output. */
    @get:Input @get:Optional abstract val useUnicode: Property<Boolean>

    /**
     * Compile-time locale for PO file selection.
     *
     * When set, the framework selects `res/strings/{locale}.po` as the localization source. This
     * makes locale an explicit build input so Gradle incremental builds work correctly — changing
     * locale invalidates the generateC task output cache.
     *
     * Default: "en"
     */
    @get:Input @get:Optional abstract val locale: Property<String>

    /** Skip validation errors (print as warnings instead of failing). */
    @get:Input @get:Optional abstract val skipValidation: Property<Boolean>

    /**
     * Directory containing pre-processed asset markers. If set, indicates which assets need
     * regeneration.
     */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val processedAssetsDir: DirectoryProperty

    init {
        description = "Generate GBDK C code from Kotlin game definition"
        group = "gbkt"
    }

    @TaskAction
    fun generate() {
        val spec = gameSpec.get()
        val parts = spec.split("::")
        if (parts.size != 2) {
            throw GradleException(
                """
                |Invalid game spec format: $spec
                |Expected format: "package.ClassName::propertyName"
                |Example: "sample.RunnerGameKt::runnerGame"
            """
                    .trimMargin()
            )
        }

        val (className, propertyName) = parts

        // Ensure output directory exists
        outputDir.get().asFile.mkdirs()

        // Use worker with classpath isolation to load user code
        val workQueue = workerExecutor.classLoaderIsolation { classpath.from(runtimeClasspath) }
        workQueue.submit(GenerateCWorkAction::class.java) {
            this.className.set(className)
            this.propertyName.set(propertyName)
            this.assetDir.set(assetDirectory.orNull?.asFile?.absolutePath)
            this.outputDir.set(this@GenerateCTask.outputDir.get().asFile)
            this.target.set(this@GenerateCTask.target.getOrElse("gbc"))
            this.skipValidation.set(this@GenerateCTask.skipValidation.getOrElse(false))

            // Optimization settings
            this.optimizationEnabled.set(this@GenerateCTask.optimizationEnabled.getOrElse(true))
            this.optimizationVerbose.set(this@GenerateCTask.optimizationVerbose.getOrElse(false))
            this.optimizationQuietWhenOptimal.set(
                this@GenerateCTask.optimizationQuietWhenOptimal.getOrElse(true)
            )
            this.detectDuplicates.set(this@GenerateCTask.detectDuplicates.getOrElse(true))
            this.detectEmpty.set(this@GenerateCTask.detectEmpty.getOrElse(true))
            this.detectLowEntropy.set(this@GenerateCTask.detectLowEntropy.getOrElse(true))
            this.lowEntropyThreshold.set(this@GenerateCTask.lowEntropyThreshold.getOrElse(0.5f))
            this.useColor.set(this@GenerateCTask.useColor.orNull)
            this.useUnicode.set(this@GenerateCTask.useUnicode.orNull)
        }
    }
}

/** Parameters for the worker action. */
interface GenerateCParams : WorkParameters {
    val className: Property<String>
    val propertyName: Property<String>
    val assetDir: Property<String>
    val outputDir: Property<File>
    val target: Property<String>
    val skipValidation: Property<Boolean>

    // Optimization settings
    val optimizationEnabled: Property<Boolean>
    val optimizationVerbose: Property<Boolean>
    val optimizationQuietWhenOptimal: Property<Boolean>
    val detectDuplicates: Property<Boolean>
    val detectEmpty: Property<Boolean>
    val detectLowEntropy: Property<Boolean>
    val lowEntropyThreshold: Property<Float>
    val useColor: Property<Boolean>
    val useUnicode: Property<Boolean>
}

/** Worker action that performs the actual code generation in an isolated classloader. */
abstract class GenerateCWorkAction : WorkAction<GenerateCParams> {

    override fun execute() {
        val className = parameters.className.get()
        val propertyName = parameters.propertyName.get()
        val assetDir = parameters.assetDir.orNull
        val outputDir = parameters.outputDir.get()
        val target = parameters.target.getOrElse("gbc")

        try {
            // Load the class containing the game definition
            val clazz = Class.forName(className)

            // Kotlin top-level properties are compiled as static methods
            // For "val runnerGame", Kotlin generates "getRunnerGame()"
            val getterName = "get${propertyName.replaceFirstChar { it.uppercase() }}"
            val getter =
                try {
                    clazz.getMethod(getterName)
                } catch (e: NoSuchMethodException) {
                    throw GradleException(
                        """
                    |Could not find game property: $className::$propertyName
                    |
                    |Expected a top-level val named '$propertyName' in the class.
                    |
                    |Example:
                    |  val $propertyName = gbGame("MyGame") { ... }
                    |
                    |Make sure:
                    |  1. The property exists and is public
                    |  2. The class name includes 'Kt' suffix for top-level declarations
                    |     (e.g., MyGameKt for MyGame.kt)
                """
                            .trimMargin()
                    )
                }

            // Invoke the getter to get the Game object
            val rawGame =
                getter.invoke(null)
                    ?: throw GradleException("Game property '$propertyName' returned null")

            // Detect v2 GameBuilder and route to the v2 pipeline
            val gameBuilderClass =
                try {
                    Class.forName("io.github.gbkt.core.dsl.GameBuilder")
                } catch (_: ClassNotFoundException) {
                    null
                }

            if (gameBuilderClass != null && gameBuilderClass.isInstance(rawGame)) {
                // GameBuilder path: call build() to get GameIR, then route to generate
                executePath(rawGame, outputDir, target)
                return
            }

            // v1 path: Process assets (convert PNG sprites to tile data) before code generation
            val game =
                if (assetDir != null) {
                    try {
                        val pipelineClass = Class.forName("io.github.gbkt.core.AssetPipelineKt")
                        val gameClass = rawGame::class.java
                        val processMethod =
                            pipelineClass.getMethod("processAssets", gameClass, String::class.java)
                        val processed = processMethod.invoke(null, rawGame, assetDir)
                        if (processed != null) {
                            println("Asset processing complete")
                            processed
                        } else {
                            rawGame
                        }
                    } catch (e: Exception) {
                        println("WARNING: Asset processing skipped: ${e.message}")
                        rawGame
                    }
                } else {
                    rawGame
                }

            // Get the Game class
            val gameClass = game::class.java

            // Use multi-file generation to split code by bank
            // GBDK-2020 doesn't support multiple #pragma bank directives in a single file
            @Suppress("UNCHECKED_CAST")
            val files: Map<String, String> =
                generateWithBackendRegistry(game, target)
                    ?: throw GradleException(
                        """
                        |No backend found for target '$target'.
                        |Make sure gbkt-backend-gbdk is on the classpath.
                        """
                            .trimMargin()
                    )

            // Write each generated file to output directory
            var totalLines = 0
            files.forEach { (filename, content) ->
                val outputFile = File(outputDir, filename)
                outputFile.writeText(content)
                totalLines += content.lines().size
                println("Generated: $filename (${content.lines().size} lines)")
            }

            val writtenSidecars = mutableSetOf<String>()

            // Generate and write source map for main.c
            try {
                val codeGenClass =
                    Class.forName("io.github.gbkt.backend.gbdk.codegen.GBDKCodeGenerator")
                val constructor = codeGenClass.getConstructor(gameClass)
                val codeGen = constructor.newInstance(game)

                val generateWithSourceMapMethod = codeGenClass.getMethod("generateWithSourceMap")
                @Suppress("UNCHECKED_CAST")
                val result = generateWithSourceMapMethod.invoke(codeGen) as Pair<String, Any>
                val sourceMap = result.second

                val toJsonMethod = sourceMap::class.java.getMethod("toJson")
                val sourceMapJson = toJsonMethod.invoke(sourceMap) as String

                val sourceMapFile = File(outputDir, "main.c.gbkt.map")
                sourceMapFile.writeText(sourceMapJson)
                writtenSidecars += "main.c.gbkt.map"
                println("Generated source map: ${sourceMapFile.absolutePath}")
            } catch (e: Exception) {
                // Source map generation is optional - don't fail the build
                System.err.println("WARNING: Source map not generated: ${e.message}")
                System.err.println(
                    "WARNING: Compiler errors will reference C line numbers, not Kotlin source."
                )
            }

            // Write build metadata for CompileRomTask
            if (writeBuildMetadata(game, outputDir)) {
                writtenSidecars += "gbkt-build.properties"
            }

            println("Generated ${files.size} C files ($totalLines total lines)")
            println("Output directory: ${outputDir.absolutePath}")

            // Run asset optimization analysis if enabled
            if (parameters.optimizationEnabled.getOrElse(true)) {
                runAssetOptimization(game, assetDir)
            }

            // 09.2 D-S-03 + plan-05 CR-01: reconcile output dir; emittedSet built from actual write
            // outcomes
            val emittedSet = files.keys + writtenSidecars
            syncOutputDir(outputDir, emittedSet)
        } catch (e: ClassNotFoundException) {
            throw GradleException(
                """
                |Class not found: $className
                |
                |Make sure:
                |  1. The class exists in your source files
                |  2. The project has been compiled (compileKotlin ran successfully)
                |  3. The class name is fully qualified (includes package)
                |
                |For a file 'src/main/kotlin/sample/RunnerGame.kt' containing
                |top-level declarations, the class would be 'sample.RunnerGameKt'
            """
                    .trimMargin()
            )
        } catch (e: Exception) {
            throw GradleException("Failed to generate C code: ${e.message}", e)
        }
    }

    /**
     * Execute the pipeline path for games defined with [io.github.gbkt.core.dsl.GameBuilder].
     *
     * Calls [GameBuilder.build] to produce a [GameIR], discovers the backend for the target, and
     * invokes [GBDKBackend.generate] via reflection.
     *
     * Source map generation is skipped for GameBuilder games. The legacy [GBDKCodeGenerator] is
     * architecturally incompatible with [GameIR]; source map support requires a new implementation
     * in [GBDKPipeline] and is deferred to a gap-closure plan after Phase 5 core integration.
     */
    private fun executePath(rawGame: Any, outputDir: File, target: String) {
        // 1. Call build() to get GameIR
        val gameIR =
            rawGame.javaClass.getMethod("build").invoke(rawGame)
                ?: throw GradleException("GameBuilder.build() returned null")

        // Phase 12.4 D-01b validation gate: every metasprite must have a sprite(asset(...)) binding
        // before backend codegen runs. Runs BEFORE backend lookup so the error surfaces immediately
        // with an actionable fix message rather than deep in the codegen stack or at lcc link time.
        validateMetaspriteSpritePaths(gameIR)

        // 2. Find backend for target
        val backend =
            BackendReflection.findBackendForTarget(target)
                ?: throw GradleException("No backend found for target '$target'")

        println("Using backend: ${BackendReflection.getBackendDisplayName(backend)}")

        // 3. Call generate(GameIR, AssetManifest?, File?) via reflection
        val gameIrClass = Class.forName("io.github.gbkt.core.ir.GameIR")
        val assetManifestClass = Class.forName("io.github.gbkt.core.AssetManifest")
        val generateMethod =
            backend.javaClass.getMethod(
                "generate",
                gameIrClass,
                assetManifestClass,
                java.io.File::class.java,
            )
        val result =
            generateMethod.invoke(backend, gameIR, null, outputDir)
                ?: throw GradleException("generate returned null")

        // 4. Extract files from GenerationResult via reflection
        val generationResultWrapper = GenerationResultWrapper(result)
        val files = generationResultWrapper.getFilesOrThrow()
        val writtenSidecars = mutableSetOf<String>()

        // 5. Write each file to output directory, and write source map files where available
        var totalLines = 0
        files.forEach { (filename, content) ->
            val outputFile = File(outputDir, filename)
            outputFile.writeText(content)
            totalLines += content.lines().size
            println("Generated: $filename (${content.lines().size} lines)")

            // Write v2 source map alongside the C file (if available for this file)
            val sourceMapJson = generationResultWrapper.getSourceMapJsonForFile(filename)
            if (sourceMapJson != null) {
                val sourceMapFile = File(outputDir, "$filename.gbkt.map")
                sourceMapFile.writeText(sourceMapJson)
                writtenSidecars += "$filename.gbkt.map"
                println("Generated source map: $filename.gbkt.map")
            }
        }

        // 7. Build metadata — may fail for GameIR (no getConfig() matching v1 Game.config);
        //    wrap in try-catch and skip gracefully
        try {
            if (writeBuildMetadata(gameIR, outputDir)) {
                writtenSidecars += "gbkt-build.properties"
            }
        } catch (_: Exception) {
            // Not critical — skip silently for v2 games
        }

        println("Generated ${files.size} C files ($totalLines total lines)")
        println("Output directory: ${outputDir.absolutePath}")

        // 09.2 D-S-03 + plan-05 CR-01: reconcile output dir; emittedSet built from actual write
        // outcomes
        val emittedSet = files.keys + writtenSidecars
        syncOutputDir(outputDir, emittedSet)
        // Note: asset optimization skipped for v2 games (expects v1 Game object)
    }

    /**
     * Try to generate using BackendRegistry (gbkt-backend-api).
     *
     * This method uses [io.github.gbkt.gradle.internal.BackendReflection] to interact with the
     * backend API via reflection. Reflection is required because the worker runs with the user's
     * classpath (via classloader isolation), which is separate from the plugin's compile-time
     * classpath.
     *
     * @return Generated files map, or null if backend API unavailable
     */
    private fun generateWithBackendRegistry(game: Any, target: String): Map<String, String>? {
        return try {
            // Discover available backends
            val backends = BackendReflection.discoverBackends()
            if (backends.isNullOrEmpty()) {
                println("No backends discovered via ServiceLoader")
                return null
            }

            // Find backend for target platform
            val backend = BackendReflection.findBackendForTarget(target)
            if (backend == null) {
                val availableIds = backends.map { BackendReflection.getBackendId(it) }
                println("No backend found for target '$target', available: $availableIds")
                return null
            }

            println("Using backend: ${BackendReflection.getBackendDisplayName(backend)}")

            // Validate the game before generation
            val validationResult = BackendReflection.validateGame(backend, game)
            validationResult.printDiagnostics()
            if (!parameters.skipValidation.getOrElse(false)) {
                validationResult.throwIfInvalid()
            }

            // Generate code
            val generationResult = BackendReflection.generateCode(backend, game)
            generationResult.getFilesOrThrow()
        } catch (e: ClassNotFoundException) {
            // BackendRegistry not available
            null
        } catch (e: org.gradle.api.GradleException) {
            // Re-throw Gradle exceptions (validation/generation failures)
            throw e
        } catch (e: Exception) {
            println("WARNING: Backend generation failed: ${e.message}")
            null
        }
    }

    /**
     * Extract cartridge type from game config via reflection and write build metadata.
     *
     * Returns `true` when `gbkt-build.properties` was successfully written this run; `false` on
     * every silent-skip path (game class lacks `getConfig`, config lacks `getCartridge`, null
     * values along the way, or any uncaught exception). Callers MUST gate `writtenSidecars +=
     * "gbkt-build.properties"` on this return value so a failed write this run does not protect a
     * stale sidecar from a prior run (09.2 plan 05, CR-01 fix per REVIEW.md).
     */
    private fun writeBuildMetadata(game: Any, outputDir: File): Boolean {
        try {
            val gameClass = game::class.java
            val configMethod =
                try {
                    gameClass.getMethod("getConfig")
                } catch (_: NoSuchMethodException) {
                    return false
                }
            val config = configMethod.invoke(game) ?: return false

            val cartridgeMethod =
                try {
                    config::class.java.getMethod("getCartridge")
                } catch (_: NoSuchMethodException) {
                    return false
                }
            val cartridge = cartridgeMethod.invoke(config) ?: return false
            val cartridgeName = cartridge.toString()

            // D-03/D-04: resolve mbcByte reflectively from the enum instance
            // (enum is the single source of truth per D-03). Guarded to handle legacy
            // Cartridge enums that may not have getMbcByte — mirrors getRamBanks/getGbcTarget.
            val mbcByteMethod =
                try {
                    cartridge::class.java.getMethod("getMbcByte")
                } catch (_: NoSuchMethodException) {
                    null
                }
            val mbcType = if (mbcByteMethod != null) {
                val mbcByteInt = mbcByteMethod.invoke(cartridge) as? Int
                if (mbcByteInt != null) "0x%02X".format(mbcByteInt) else null
            } else null

            val props = java.util.Properties()
            props.setProperty("cartridge", cartridgeName)
            if (mbcType != null) props.setProperty("mbcType", mbcType)

            // D-07: write ramBanks from DSL config to gbkt-build.properties
            // (same reflective try/catch pattern as gbcTarget below)
            val ramBanksMethod =
                try {
                    config::class.java.getMethod("getRamBanks")
                } catch (_: NoSuchMethodException) {
                    null
                }
            if (ramBanksMethod != null) {
                val ramBanksValue = ramBanksMethod.invoke(config)
                if (ramBanksValue != null) {
                    props.setProperty("ramBanks", ramBanksValue.toString())
                }
            }

            // Write GBC target mode for CompileRomTask (DSL config {
            // target(GbcTarget.GBC_COMPATIBLE) })
            val gbcTargetMethod =
                try {
                    config::class.java.getMethod("getGbcTarget")
                } catch (_: NoSuchMethodException) {
                    null
                }
            if (gbcTargetMethod != null) {
                val gbcTargetValue = gbcTargetMethod.invoke(config)
                if (gbcTargetValue != null) {
                    val gbcTargetName = gbcTargetValue.toString()
                    val gbcMode =
                        when (gbcTargetName) {
                            "GBC_COMPATIBLE" -> "COMPATIBLE"
                            "GBC_ONLY" -> "ONLY"
                            else -> "DISABLED"
                        }
                    props.setProperty("gbcMode", gbcMode)
                    println("Build metadata: gbcTarget=$gbcTargetName, gbcMode=$gbcMode")
                }
            }

            val propsFile = File(outputDir, "gbkt-build.properties")
            propsFile.outputStream().use { props.store(it, "gbkt build metadata") }
            println("Build metadata: cartridge=$cartridgeName" + if (mbcType != null) ", mbcType=$mbcType" else "")
            return true
        } catch (e: Exception) {
            println("WARNING: Could not extract build metadata: ${e.message}")
            return false
        }
    }

    /** Run asset optimization analysis via reflection. */
    private fun runAssetOptimization(game: Any, assetDir: String?) {
        try {
            // Load optimization classes
            val analyzerConfigClass =
                Class.forName("io.github.gbkt.core.optimization.AnalyzerConfig")
            val analyzerClass = Class.forName("io.github.gbkt.core.optimization.AssetAnalyzer")
            val reporterConfigClass =
                Class.forName("io.github.gbkt.core.optimization.ReporterConfig")
            val reporterClass = Class.forName("io.github.gbkt.core.optimization.ConsoleReporter")
            val gameClass = Class.forName("io.github.gbkt.core.Game")

            // Create AnalyzerConfig via reflection
            // Kotlin data classes with all-default params have synthetic constructors
            val analyzerConfigConstructors = analyzerConfigClass.constructors
            val analyzerConfig = run {
                val args =
                    arrayOf(
                        parameters.lowEntropyThreshold.getOrElse(0.5f), // lowEntropyThreshold
                        0.8f, // similarityThreshold
                        256, // maxTilesForSimilarity
                        parameters.detectDuplicates.getOrElse(true), // detectDuplicates
                        parameters.detectEmpty.getOrElse(true), // detectEmpty
                        parameters.detectLowEntropy.getOrElse(true), // detectLowEntropy
                        true, // analyzePalette
                        true, // analyzeCompression
                    )
                // Try exact 8-param constructor first
                val exact = analyzerConfigConstructors.find { it.parameterCount == 8 }
                if (exact != null) {
                    exact.newInstance(*args)
                } else {
                    // Kotlin synthetic: 8 fields + defaults mask + DefaultConstructorMarker
                    val synthetic =
                        analyzerConfigConstructors.find { it.parameterCount == 10 }
                            ?: analyzerConfigConstructors.first()
                    synthetic.newInstance(*args, 0, null)
                }
            }

            // Create AssetAnalyzer
            val analyzerConstructor = analyzerClass.getConstructor(analyzerConfigClass)
            val analyzer = analyzerConstructor.newInstance(analyzerConfig)

            // Create File for asset directory
            val assetDirFile = assetDir?.let { java.io.File(it) }

            // Call analyze method
            val analyzeMethod =
                analyzerClass.getMethod("analyze", gameClass, java.io.File::class.java)
            val report = analyzeMethod.invoke(analyzer, game, assetDirFile)

            // Detect color/unicode support via Companion instance methods
            val useColor =
                parameters.useColor.orNull
                    ?: try {
                        val companionClass =
                            reporterConfigClass.getDeclaredClasses().find {
                                it.simpleName == "Companion"
                            }
                        val companionInstance = reporterConfigClass.getField("Companion").get(null)
                        companionClass?.getMethod("detectColorSupport")?.invoke(companionInstance)
                            as? Boolean ?: true
                    } catch (_: Exception) {
                        true
                    }
            val useUnicode =
                parameters.useUnicode.orNull
                    ?: try {
                        val companionClass =
                            reporterConfigClass.getDeclaredClasses().find {
                                it.simpleName == "Companion"
                            }
                        val companionInstance = reporterConfigClass.getField("Companion").get(null)
                        companionClass?.getMethod("detectUnicodeSupport")?.invoke(companionInstance)
                            as? Boolean ?: true
                    } catch (_: Exception) {
                        true
                    }

            // Create ReporterConfig (5 fields, all with defaults → synthetic has 7 params)
            val reporterConfigConstructors = reporterConfigClass.constructors
            val reporterConfig = run {
                val args =
                    arrayOf(
                        useColor, // useColor
                        useUnicode, // useUnicode
                        parameters.optimizationVerbose.getOrElse(false), // showPerAsset
                        true, // showSuggestions
                        parameters.optimizationQuietWhenOptimal.getOrElse(true), // quietWhenOptimal
                    )
                val exact = reporterConfigConstructors.find { it.parameterCount == 5 }
                if (exact != null) {
                    exact.newInstance(*args)
                } else {
                    val synthetic =
                        reporterConfigConstructors.find { it.parameterCount == 7 }
                            ?: reporterConfigConstructors.first()
                    synthetic.newInstance(*args, 0, null)
                }
            }

            // Create reporter and generate report
            val reporterConstructor = reporterClass.getConstructor(reporterConfigClass)
            val reporter = reporterConstructor.newInstance(reporterConfig)

            val reportClass = Class.forName("io.github.gbkt.core.optimization.AssetReport")
            val reportMethod = reporterClass.getMethod("report", reportClass)
            reportMethod.invoke(reporter, report)
        } catch (e: ClassNotFoundException) {
            // Optimization classes not available, skip silently
        } catch (e: Exception) {
            // Log warning but don't fail the build
            println("Warning: Asset optimization analysis failed: ${e.message}")
        }
    }

}

// =============================================================================
// 09.2 D-S-01 + D-S-02: syncOutputDir helper (dormant — call sites added in Plan 2)
// =============================================================================

/**
 * Always-survive sidecar filenames whose presence in [outputDir] is tolerated even when no pipeline
 * writes them this run — used for backward-compatible cleanup of files that may have been written
 * by an earlier pipeline version against the same output dir.
 *
 * NOTE: `main.c.gbkt.map` is NOT in this list. Source-map files (`*.gbkt.map`) MUST be deleted if
 * not written this run — that is the exact staleness bug Phase 09.2 was created to eliminate. v1
 * and v2 call sites track actual write outcomes via `writtenSidecars` and pass `emittedSet =
 * files.keys + writtenSidecars` to [syncOutputDir]. (09.2 plan 05, CR-01 + WR-01 disposition per
 * REVIEW.md)
 */
private val SIDECAR_WHITELIST: Set<String> = setOf("gbkt-build.properties", "game_metadata.json")

private fun isProtected(name: String, emittedSet: Set<String>): Boolean =
    name in emittedSet || name in SIDECAR_WHITELIST || name.startsWith(".")

/**
 * Reconcile [outputDir] to exactly [emittedSet]. Files present in [outputDir] but absent from
 * [emittedSet] are deleted unless protected by the whitelist or dotfile exemption.
 *
 * Protection rules (09.2 D-S-02):
 * - Files in [emittedSet] survive.
 * - Files in [SIDECAR_WHITELIST] survive: `gbkt-build.properties`, `game_metadata.json` (only files
 *   written outside the codegen `files` map AND tolerated when missing this run).
 * - Files with `.gbkt.map` suffix survive ONLY if listed in [emittedSet] — each call site tracks
 *   ACTUAL write outcomes via `writtenSidecars` (added only after successful write) and constructs
 *   `emittedSet = files.keys + writtenSidecars`. A failed sidecar write this run leaves the
 *   prior-run stale copy unprotected and deleted. (09.2 plan 05, CR-01 fix)
 * - Files starting with `.` survive (dotfiles: `.gitkeep`, `.DS_Store`, IDE markers).
 *
 * Per deletion: emits `println("Removed stale: $name")` matching the existing `Generated: $name`
 * lifecycle log style (09.2 D-S-04, lines 281, 387).
 *
 * On delete failure (read-only / locked file): emits a WARNING line and skips — does NOT fail the
 * task (09.2 RESEARCH §Pitfall 2).
 *
 * @return List of deleted filenames (may be empty).
 */
internal fun syncOutputDir(outputDir: File, emittedSet: Set<String>): List<String> {
    if (!outputDir.exists() || !outputDir.isDirectory) return emptyList()

    val deleted = mutableListOf<String>()
    outputDir
        .listFiles()
        ?.filter { it.isFile }
        ?.forEach { file ->
            val name = file.name
            if (isProtected(name, emittedSet)) return@forEach
            if (file.delete()) {
                println("Removed stale: $name")
                deleted += name
            } else {
                println("WARNING: Could not delete stale: $name — file may be locked or read-only")
            }
        }
    return deleted
}

// =============================================================================
// Phase 12.4 D-01b — MetaspriteIR.spritePath null-check gate
// =============================================================================

/**
 * Pre-codegen validation gate: throws [GradleException] if any [MetaspriteIR] in [gameIR] has a
 * null `spritePath` field.
 *
 * **Why reflection?** `GenerateCTask` runs in a Gradle classloader-isolated worker (see
 * `GenerateCWorkAction`). The `gameIR` value is produced by the user's classpath via reflection
 * throughout `executePath()`; direct typed access to `gbkt-ir` types would require adding
 * `gbkt-ir` as a compile dependency to the plugin, coupling the plugin build to the IR module
 * version. Using reflection keeps the same coupling assumptions as the rest of the file (lines 362,
 * 373, 374, etc.).
 *
 * **When is this gate active?** The `MetaspriteIR.spritePath` field is *nullable* during the Phase
 * 12.4 migration window (D-01b) so that existing tests that create `MetaspriteIR` instances without
 * `spritePath` still compile. Once Plans 12.4-09/10/11 migrate all 3 in-tree games to call
 * `sprite(asset(...))`, the gate enforces the contract: any future metasprite without a binding
 * fails the build with an actionable message.
 *
 * **Silent-skip alternative rejected:** Without this gate, a metasprite with null `spritePath` is
 * silently skipped by the sidecar emitter in [GBDKPipeline], so [ConvertSpritesTask] never sees
 * it, `sprites_<id>_tiles` is never defined, and `lcc` fails with an opaque "undefined symbol"
 * error deep in the link step — exactly the failure mode D-04 targeted.
 *
 * @param gameIR The IR root produced by `GameBuilder.build()` — typed as `Any` because this
 *   function is called from a classloader-isolated worker context where `GameIR` is only accessible
 *   via reflection.
 * @throws GradleException if any metasprite has `spritePath == null`.
 */
@Suppress("UNCHECKED_CAST")
internal fun validateMetaspriteSpritePaths(gameIR: Any) {
    val metasprites =
        try {
            gameIR.javaClass.getMethod("getMetasprites").invoke(gameIR) as List<Any>
        } catch (_: NoSuchMethodException) {
            // GameIR version predates metasprites field — skip validation gracefully.
            return
        }

    for (ms in metasprites) {
        val spritePath =
            try {
                ms.javaClass.getMethod("getSpritePath").invoke(ms) as String?
            } catch (_: NoSuchMethodException) {
                // MetaspriteIR version predates spritePath field — skip this entry.
                continue
            }

        if (spritePath == null) {
            val id =
                try {
                    ms.javaClass.getMethod("getId").invoke(ms) as String
                } catch (_: Exception) {
                    "<unknown>"
                }
            throw GradleException(
                "Metasprite '$id' is missing sprite(asset(...)) — add " +
                    "sprite(asset(\"<path/to/sprite.png>\")) to your metasprite { } block. " +
                    "Every metasprite MUST declare its PNG asset explicitly so " +
                    "ConvertSpritesTask can resolve it via the game_metadata.json sidecar. " +
                    "(Phase 12.4 D-01b)"
            )
        }

        // Resolve the metasprite id for use in Phase 12.5 D-04b error messages below.
        // Done here (after spritePath check) so the id is available for all subsequent checks.
        val id =
            try {
                ms.javaClass.getMethod("getId").invoke(ms) as String
            } catch (_: Exception) {
                "<unknown>"
            }

        // ---------------------------------------------------------------
        // Phase 12.5 D-04b checks — png2asset cutting flags
        //
        // Per Pitfall 3 (RESEARCH.md): use invoke(ms) != null (NOT type-cast)
        // because classloader isolation makes casting across boundaries unsafe.
        // ---------------------------------------------------------------

        // Check spriteMode — declared via mode(SpriteMode.SPR8x16) or mode(SpriteMode.SPR8x8)
        val spriteMode =
            try {
                ms.javaClass.getMethod("getSpriteMode").invoke(ms)
            } catch (_: NoSuchMethodException) {
                null  // legacy IR — treat same as not-set; gate below throws
            }
        if (spriteMode == null)
            throw GradleException(
                "Metasprite '$id' missing mode() — declare mode(SpriteMode.SPR8x16) or " +
                    "mode(SpriteMode.SPR8x8) inside sprite() { ... } block. (Phase 12.5 D-04b)"
            )

        // Check pivot — declared via pivot(x, y); either null means pivot() was not called
        val pivotX =
            try {
                ms.javaClass.getMethod("getPivotX").invoke(ms)
            } catch (_: NoSuchMethodException) {
                null
            }
        val pivotY =
            try {
                ms.javaClass.getMethod("getPivotY").invoke(ms)
            } catch (_: NoSuchMethodException) {
                null
            }
        if (pivotX == null || pivotY == null)
            throw GradleException(
                "Metasprite '$id' missing pivot() — declare pivot(x, y) inside " +
                    "sprite() { ... } block. (Phase 12.5 D-04b)"
            )

        // Check frameSize — declared via frameSize(w, h); either null means frameSize() was not
        // called
        val frameWidth =
            try {
                ms.javaClass.getMethod("getFrameWidth").invoke(ms)
            } catch (_: NoSuchMethodException) {
                null
            }
        val frameHeight =
            try {
                ms.javaClass.getMethod("getFrameHeight").invoke(ms)
            } catch (_: NoSuchMethodException) {
                null
            }
        if (frameWidth == null || frameHeight == null)
            throw GradleException(
                "Metasprite '$id' missing frameSize() — declare frameSize(w, h) inside " +
                    "sprite() { ... } block. (Phase 12.5 D-04b)"
            )
    }

    // -----------------------------------------------------------------------
    // WR-03: cross-metasprite mixed-mode guard
    //
    // GBDK hardware sprite mode is a GLOBAL LCDC bit. All metasprites in a game
    // must declare the same SpriteMode. Mixing SPR8x8 and SPR8x16 produces
    // incorrect rendering at runtime: the global SPRITES_8x16 macro sets 8×16
    // hardware mode, which causes SPR8x8 metasprites to render with doubled rows.
    //
    // Collect the distinct toString() representations of each metasprite's
    // spriteMode (via invoke) and throw if more than one distinct value exists.
    // Using toString() avoids cross-classloader enum comparison issues (Pitfall 3).
    // -----------------------------------------------------------------------
    val spriteModeNames =
        metasprites
            .mapNotNull { ms ->
                try {
                    ms.javaClass.getMethod("getSpriteMode").invoke(ms)?.toString()
                } catch (_: NoSuchMethodException) {
                    null
                }
            }
            .distinct()
    if (spriteModeNames.size > 1)
        throw GradleException(
            "All metasprites in a game must use the same SpriteMode — hardware LCDC.SPRITE_SIZE " +
                "is a global bit. Found both ${spriteModeNames.joinToString(" and ")}. " +
                "Declare the same mode(SpriteMode.SPR8x8) or mode(SpriteMode.SPR8x16) in every " +
                "metasprite { } block. (Phase 12.5 WR-03)"
        )
}
