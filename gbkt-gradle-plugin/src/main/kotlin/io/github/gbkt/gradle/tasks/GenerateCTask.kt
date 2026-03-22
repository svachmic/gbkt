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
                // v2 path: call build() to get GameIR, then route to generateV2
                executeV2Path(rawGame, outputDir, target)
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
                println("Generated source map: ${sourceMapFile.absolutePath}")
            } catch (e: Exception) {
                // Source map generation is optional - don't fail the build
                System.err.println("WARNING: Source map not generated: ${e.message}")
                System.err.println(
                    "WARNING: Compiler errors will reference C line numbers, not Kotlin source."
                )
            }

            // Write build metadata for CompileRomTask
            writeBuildMetadata(game, outputDir)

            println("Generated ${files.size} C files ($totalLines total lines)")
            println("Output directory: ${outputDir.absolutePath}")

            // Run asset optimization analysis if enabled
            if (parameters.optimizationEnabled.getOrElse(true)) {
                runAssetOptimization(game, assetDir)
            }
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
     * Execute the v2 pipeline path for games defined with [io.github.gbkt.core.dsl.GameBuilder].
     *
     * Calls [GameBuilder.build] to produce a [GameIR], discovers the backend for the target, and
     * invokes [GBDKBackend.generateV2] via reflection.
     *
     * Source map generation is skipped for v2 games. The v1 [GBDKCodeGenerator] is architecturally
     * incompatible with [GameIR]; v2 source map support requires a new implementation in
     * [GBDKPipelineV2] and is deferred to a gap-closure plan after Phase 5 core integration.
     */
    private fun executeV2Path(rawGame: Any, outputDir: File, target: String) {
        // 1. Call build() to get GameIR
        val gameIR =
            rawGame.javaClass.getMethod("build").invoke(rawGame)
                ?: throw GradleException("GameBuilder.build() returned null")

        // 2. Find backend for target
        val backend =
            BackendReflection.findBackendForTarget(target)
                ?: throw GradleException("No backend found for target '$target'")

        println("Using backend: ${BackendReflection.getBackendDisplayName(backend)}")

        // 3. Call generateV2(GameIR, AssetManifest?, File?) via reflection
        val gameIrClass = Class.forName("io.github.gbkt.core.ir.GameIR")
        val assetManifestClass = Class.forName("io.github.gbkt.core.AssetManifest")
        val generateV2Method =
            backend.javaClass.getMethod(
                "generateV2",
                gameIrClass,
                assetManifestClass,
                java.io.File::class.java,
            )
        val result =
            generateV2Method.invoke(backend, gameIR, null, outputDir)
                ?: throw GradleException("generateV2 returned null")

        // 4. Extract files from GenerationResult via reflection
        val generationResultWrapper = GenerationResultWrapper(result)
        val files = generationResultWrapper.getFilesOrThrow()

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
                println("Generated source map: $filename.gbkt.map")
            }
        }

        // 7. Build metadata — may fail for GameIR (no getConfig() matching v1 Game.config);
        //    wrap in try-catch and skip gracefully
        try {
            writeBuildMetadata(gameIR, outputDir)
        } catch (_: Exception) {
            // Not critical — skip silently for v2 games
        }

        println("Generated ${files.size} C files ($totalLines total lines)")
        println("Output directory: ${outputDir.absolutePath}")
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
     * This writes a `gbkt-build.properties` file that CompileRomTask reads to determine the correct
     * MBC type flag instead of hardcoding it.
     */
    private fun writeBuildMetadata(game: Any, outputDir: File) {
        try {
            val gameClass = game::class.java
            val configMethod =
                try {
                    gameClass.getMethod("getConfig")
                } catch (_: NoSuchMethodException) {
                    return
                }
            val config = configMethod.invoke(game) ?: return

            val cartridgeMethod =
                try {
                    config::class.java.getMethod("getCartridge")
                } catch (_: NoSuchMethodException) {
                    return
                }
            val cartridge = cartridgeMethod.invoke(config) ?: return
            val cartridgeName = cartridge.toString()

            val mbcType = CARTRIDGE_MBC_MAP[cartridgeName] ?: "0x00"

            val props = java.util.Properties()
            props.setProperty("cartridge", cartridgeName)
            props.setProperty("mbcType", mbcType)

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
            println("Build metadata: cartridge=$cartridgeName, mbcType=$mbcType")
        } catch (e: Exception) {
            println("WARNING: Could not extract build metadata: ${e.message}")
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

    companion object {
        /** Maps Cartridge enum names to GBDK `-Wm-yt` hex codes. */
        val CARTRIDGE_MBC_MAP =
            mapOf(
                "ROM_ONLY" to "0x00",
                "MBC1" to "0x01",
                "MBC1_RAM" to "0x02",
                "MBC1_RAM_BATTERY" to "0x03",
                "MBC3_TIMER_BATTERY" to "0x10",
                "MBC5" to "0x19",
                "MBC5_RAM_BATTERY" to "0x1B",
            )
    }
}
