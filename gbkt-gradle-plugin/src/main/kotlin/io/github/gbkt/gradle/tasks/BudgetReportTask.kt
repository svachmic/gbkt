/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle.tasks

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

/**
 * Gradle task that runs the gbkt analysis pipeline and prints the ASCII budget report.
 *
 * This task:
 * 1. Loads compiled Kotlin classes containing the game definition (same as GenerateCTask).
 * 2. Builds a GameIR from the game definition via reflection.
 * 3. Runs DefaultPipeline with the game's CartridgeConfig to produce PassContext.
 * 4. Prints the ASCII budget report from PassContext.budgetReport to the build log.
 *
 * Uses classloader isolation (same pattern as GenerateCTask) so the analysis classes are loaded
 * from the user's runtime classpath, which includes gbkt-analysis.
 */
abstract class BudgetReportTask @Inject constructor(private val workerExecutor: WorkerExecutor) :
    DefaultTask() {

    /** Game definition in format "package.ClassName::propertyName". */
    @get:Input abstract val gameSpec: Property<String>

    /** Target platform for analysis (e.g., "gbc", "gb"). */
    @get:Input @get:Optional abstract val target: Property<String>

    /** Runtime classpath containing compiled classes, gbkt-core, and gbkt-analysis. */
    @get:Classpath abstract val runtimeClasspath: ConfigurableFileCollection

    init {
        description = "Run analysis pipeline and print ASCII budget report"
        group = "gbkt"
    }

    @TaskAction
    fun report() {
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

        val workQueue = workerExecutor.classLoaderIsolation { classpath.from(runtimeClasspath) }
        workQueue.submit(BudgetReportWorkAction::class.java) {
            this.className.set(parts[0])
            this.propertyName.set(parts[1])
            this.target.set(this@BudgetReportTask.target.getOrElse("gbc"))
        }
    }
}

/** Parameters for the budget report worker action. */
interface BudgetReportParams : WorkParameters {
    val className: Property<String>
    val propertyName: Property<String>
    val target: Property<String>
}

/** Worker action that runs the analysis pipeline and prints the budget report. */
abstract class BudgetReportWorkAction : WorkAction<BudgetReportParams> {

    override fun execute() {
        val className = parameters.className.get()
        val propertyName = parameters.propertyName.get()

        try {
            // Load the class containing the game definition
            val clazz =
                try {
                    Class.forName(className)
                } catch (e: ClassNotFoundException) {
                    throw GradleException(
                        """
                        |Class not found: $className
                        |
                        |Make sure the project has been compiled (compileKotlin ran successfully).
                    """
                            .trimMargin()
                    )
                }

            // Kotlin top-level properties are compiled as static getter methods
            val getterName = "get${propertyName.replaceFirstChar { it.uppercase() }}"
            val getter =
                try {
                    clazz.getMethod(getterName)
                } catch (e: NoSuchMethodException) {
                    throw GradleException(
                        """
                        |Could not find game property: $className::$propertyName
                        |Make sure the property exists and is public.
                    """
                            .trimMargin()
                    )
                }

            val rawGame =
                getter.invoke(null)
                    ?: throw GradleException("Game property '$propertyName' returned null")

            // Run analysis pipeline via reflection
            runAnalysisPipeline(rawGame)
        } catch (e: GradleException) {
            throw e
        } catch (e: Exception) {
            throw GradleException("Budget report failed: ${e.message}", e)
        }
    }

    /**
     * Run the gbkt analysis pipeline on the given game object via reflection.
     *
     * Reflection is required because this worker action runs in an isolated classloader with the
     * user's runtime classpath. The analysis pipeline classes (DefaultPipeline, PassContext,
     * AnalysisConfig, GameBoyColorProfile) are loaded from the user's classpath at runtime.
     */
    private fun runAnalysisPipeline(game: Any) {
        try {
            // 1. Try to get a GameIR from the game — v2 game objects have getGameIR() or similar
            // GBDKBackend.generate() builds the pipeline from a GameIR. We need to access:
            //   - GameIR: if game has getIr() or game itself is a GameIR
            //   - Otherwise fall back to using a v1 Game object path

            // Try to resolve GameIR directly from v2 game object
            val gameIR = resolveGameIR(game)

            if (gameIR != null) {
                runAnalysisOnGameIR(gameIR)
            } else {
                println(
                    "gbkt budget report: game is not a v2 GameIR-backed game. " +
                        "Analysis pipeline requires a v2 GameIR. " +
                        "Skipping budget report."
                )
            }
        } catch (e: ClassNotFoundException) {
            println(
                "gbkt budget report: analysis classes not found on classpath. " +
                    "Make sure gbkt-analysis is in the runtime classpath."
            )
        } catch (e: Exception) {
            throw GradleException("Budget report analysis failed: ${e.message}", e)
        }
    }

    /**
     * Attempt to extract or cast the game object to a GameIR.
     *
     * Tries the following resolution strategies in order:
     * 1. Game is already a GameIR — return directly.
     * 2. Game has a `build()` method (v2 GameBuilder DSL) — call build() to get GameIR.
     * 3. Game has a `getIr()` method (legacy accessor) — call getIr().
     *
     * Returns null if none of the above strategies succeeds.
     */
    private fun resolveGameIR(game: Any): Any? {
        val gameIrClass =
            try {
                Class.forName("io.github.gbkt.core.ir.GameIR")
            } catch (e: ClassNotFoundException) {
                return null
            }

        return when {
            gameIrClass.isInstance(game) -> game
            else -> {
                // Try GameBuilder.build() first (v2 DSL)
                try {
                    game.javaClass.getMethod("build").invoke(game)
                } catch (_: NoSuchMethodException) {
                    // Fall back to getIr() (legacy accessor)
                    try {
                        game.javaClass.getMethod("getIr").invoke(game)
                    } catch (_: NoSuchMethodException) {
                        null
                    }
                }
            }
        }
    }

    /** Run the 10-pass analysis pipeline on a GameIR and print the budget report. */
    private fun runAnalysisOnGameIR(gameIR: Any) {
        // Load analysis classes
        val analysisConfigClass = Class.forName("io.github.gbkt.analysis.config.AnalysisConfig")
        val passContextClass = Class.forName("io.github.gbkt.analysis.PassContext")
        val defaultPipelineClass = Class.forName("io.github.gbkt.analysis.DefaultPipeline")
        val passResultSuccessClass = Class.forName("io.github.gbkt.analysis.PassResult\$Success")
        val passResultFailedClass = Class.forName("io.github.gbkt.analysis.PassResult\$Failed")

        // Load target profile (GameBoyColorProfile)
        val profileClass =
            try {
                Class.forName("io.github.gbkt.backend.gbdk.profiles.GameBoyColorProfile")
            } catch (_: ClassNotFoundException) {
                // Try the object pattern
                Class.forName("io.github.gbkt.backend.gbdk.profiles.GameBoyColorProfileKt")
            }

        val profile =
            try {
                profileClass.kotlin.objectInstance
                    ?: profileClass.getDeclaredField("INSTANCE").get(null)
            } catch (_: Exception) {
                // Try companion or singleton access
                profileClass.getMethod("getInstance").invoke(null)
            }

        // Get CartridgeConfig from GameIR to build AnalysisConfig
        val getConfigMethod = gameIR.javaClass.getMethod("getConfig")
        val cartridgeConfig = getConfigMethod.invoke(gameIR)
        val cartridgeConfigClass = cartridgeConfig.javaClass

        // Build AnalysisConfig.fromCartridgeConfig(cartridgeConfig)
        val companionField = analysisConfigClass.getDeclaredField("Companion")
        val companion = companionField.get(null)
        val fromCartridgeConfigMethod =
            companion.javaClass.getMethod("fromCartridgeConfig", cartridgeConfigClass)
        val analysisConfig = fromCartridgeConfigMethod.invoke(companion, cartridgeConfig)

        // Build initial PassContext
        val targetProfileClass = Class.forName("io.github.gbkt.core.constraints.TargetProfile")
        val gameIrClass = Class.forName("io.github.gbkt.core.ir.GameIR")

        // PassContext primary constructor: (game, profile, config, bankAssignments, ...,
        // budgetReport)
        // Use the 3-arg convenience by finding the synthetic constructor
        val passContextConstructors = passContextClass.constructors
        val initialContext =
            buildPassContext(
                passContextConstructors,
                passContextClass,
                gameIrClass,
                targetProfileClass,
                analysisConfigClass,
                gameIR,
                profile,
                analysisConfig,
            )

        // Create DefaultPipeline via DefaultPipeline.create()
        val defaultPipelineInstance =
            defaultPipelineClass.kotlin.objectInstance
                ?: defaultPipelineClass.getDeclaredField("INSTANCE").get(null)

        val emptyListClass = Class.forName("java.util.List")
        val createMethod = defaultPipelineClass.getMethod("create", emptyListClass, emptyListClass)
        val pipeline =
            createMethod.invoke(defaultPipelineInstance, emptyList<Any>(), emptyList<Any>())

        // Execute pipeline
        val passPipelineClass = pipeline.javaClass
        val executeMethod = passPipelineClass.getMethod("execute", passContextClass)
        val result = executeMethod.invoke(pipeline, initialContext)

        // Print budget report or errors
        when {
            passResultSuccessClass.isInstance(result) -> {
                val getContextMethod = passResultSuccessClass.getMethod("getContext")
                val finalContext = getContextMethod.invoke(result)
                val getBudgetReportMethod = passContextClass.getMethod("getBudgetReport")
                val budgetReport = getBudgetReportMethod.invoke(finalContext) as? String
                if (budgetReport != null) {
                    println(budgetReport)
                } else {
                    println("gbkt budget report: analysis complete (no report generated)")
                }
            }
            passResultFailedClass.isInstance(result) -> {
                val getDiagnosticsMethod = passResultFailedClass.getMethod("getDiagnostics")
                @Suppress("UNCHECKED_CAST")
                val diagnostics = getDiagnosticsMethod.invoke(result) as List<Any>
                val messages =
                    diagnostics.joinToString("\n") { d ->
                        val msg = d.javaClass.getMethod("getMessage").invoke(d) as String
                        val id = d.javaClass.getMethod("getId").invoke(d) as String
                        "[$id] $msg"
                    }
                throw GradleException("Analysis pipeline failed:\n$messages")
            }
            else -> println("gbkt budget report: unknown pipeline result")
        }
    }

    /**
     * Build an initial PassContext using constructor reflection.
     *
     * PassContext has many optional fields with defaults. We use the synthetic constructor (all
     * fields + defaults mask + marker) to instantiate it with only the required fields set.
     */
    private fun buildPassContext(
        constructors: Array<java.lang.reflect.Constructor<*>>,
        passContextClass: Class<*>,
        gameIrClass: Class<*>,
        targetProfileClass: Class<*>,
        analysisConfigClass: Class<*>,
        gameIR: Any,
        profile: Any,
        analysisConfig: Any,
    ): Any {
        // PassContext data class fields:
        // 1. game: GameIR
        // 2. profile: TargetProfile
        // 3. config: AnalysisConfig
        // 4. bankAssignments: Map<String, BankSlot> (default emptyMap)
        // 5. vramAssignments: Map<String, VRAMRange> (default emptyMap)
        // 6. oamAssignments: Map<String, OAMSlot> (default emptyMap)
        // 7. ramLayout: RAMLayout? (default null)
        // 8. inventory: ResourceInventory? (default null)
        // 9. diagnostics: List<Diagnostic> (default emptyList)
        // 10. budgetReport: String? (default null)

        // Try the 3-param primary variant or the synthetic constructor
        val exact =
            constructors.find {
                it.parameterCount == 3 &&
                    it.parameterTypes[0] == gameIrClass &&
                    it.parameterTypes[1] == targetProfileClass &&
                    it.parameterTypes[2] == analysisConfigClass
            }
        if (exact != null) {
            return exact.newInstance(gameIR, profile, analysisConfig)
        }

        // Kotlin synthetic constructor with defaults bitmask
        // 10 fields + int mask + DefaultConstructorMarker = 12 params
        val synthetic =
            constructors.find { it.parameterCount == 12 }
                ?: constructors.find { it.parameterCount > 3 }
                ?: constructors.first()

        val paramCount = synthetic.parameterCount
        val args = arrayOfNulls<Any>(paramCount)
        args[0] = gameIR
        args[1] = profile
        args[2] = analysisConfig
        // Fill optional args with null/defaults — the bitmask will indicate which to use
        // Remaining fields: bankAssignments, vramAssignments, oamAssignments, ramLayout, inventory,
        // diagnostics, budgetReport
        // Set indices 3..9 to null (will be replaced by defaults via bitmask)
        for (i in 3 until paramCount - 2) {
            args[i] = null
        }
        // Second-to-last is the defaults bitmask: bits 3-9 set = 0b1111111000 = 0x3F8 = 1016
        // Bit N is set if parameter N should use its default value
        // Parameters 3-9 (7 params) → bits 3-9 → (1111111 << 3) = 0x3F8
        args[paramCount - 2] = 0x3F8 // defaults bitmask for fields 3-9
        args[paramCount - 1] = null // DefaultConstructorMarker
        return synthetic.newInstance(*args)
    }
}
