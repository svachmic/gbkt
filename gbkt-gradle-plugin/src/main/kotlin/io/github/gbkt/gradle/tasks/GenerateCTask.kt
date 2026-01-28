/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import io.github.gbkt.gradle.internal.BackendReflection
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
            val game =
                getter.invoke(null)
                    ?: throw GradleException("Game property '$propertyName' returned null")

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
            validationResult.throwIfInvalid()

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

            // Create AnalyzerConfig
            val analyzerConfigConstructor = analyzerConfigClass.constructors.first()
            val analyzerConfig =
                analyzerConfigConstructor.newInstance(
                    parameters.lowEntropyThreshold.getOrElse(0.5f), // lowEntropyThreshold
                    0.8f, // similarityThreshold
                    256, // maxTilesForSimilarity
                    parameters.detectDuplicates.getOrElse(true), // detectDuplicates
                    parameters.detectEmpty.getOrElse(true), // detectEmpty
                    parameters.detectLowEntropy.getOrElse(true), // detectLowEntropy
                    true, // analyzePalette
                    true, // analyzeCompression
                )

            // Create AssetAnalyzer
            val analyzerConstructor = analyzerClass.getConstructor(analyzerConfigClass)
            val analyzer = analyzerConstructor.newInstance(analyzerConfig)

            // Create File for asset directory
            val assetDirFile = assetDir?.let { java.io.File(it) }

            // Call analyze method
            val analyzeMethod =
                analyzerClass.getMethod("analyze", gameClass, java.io.File::class.java)
            val report = analyzeMethod.invoke(analyzer, game, assetDirFile)

            // Create ReporterConfig
            val reporterConfigConstructor = reporterConfigClass.constructors.first()

            // Detect color/unicode support
            val detectColorMethod =
                reporterConfigClass
                    .getDeclaredClasses()
                    .find { it.simpleName == "Companion" }
                    ?.getMethod("detectColorSupport")
            val detectUnicodeMethod =
                reporterConfigClass
                    .getDeclaredClasses()
                    .find { it.simpleName == "Companion" }
                    ?.getMethod("detectUnicodeSupport")

            val useColor =
                parameters.useColor.orNull ?: (detectColorMethod?.invoke(null) as? Boolean ?: true)
            val useUnicode =
                parameters.useUnicode.orNull
                    ?: (detectUnicodeMethod?.invoke(null) as? Boolean ?: true)

            val reporterConfig =
                reporterConfigConstructor.newInstance(
                    useColor, // useColor
                    useUnicode, // useUnicode
                    parameters.optimizationVerbose.getOrElse(false), // showPerAsset
                    true, // showSuggestions
                    parameters.optimizationQuietWhenOptimal.getOrElse(true), // quietWhenOptimal
                )

            // Create reporter and generate report
            val reporterConstructor = reporterClass.getConstructor(reporterConfigClass)
            val reporter = reporterConstructor.newInstance(reporterConfig)

            val reportMethod = reporterClass.getMethod("report", report::class.java)
            reportMethod.invoke(reporter, report)
        } catch (e: ClassNotFoundException) {
            // Optimization classes not available, skip silently
        } catch (e: Exception) {
            // Log warning but don't fail the build
            println("Warning: Asset optimization analysis failed: ${e.message}")
        }
    }
}
