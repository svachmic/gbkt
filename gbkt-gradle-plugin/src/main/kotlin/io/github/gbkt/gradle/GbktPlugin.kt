/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle

import io.github.gbkt.gradle.internal.GbdkToolchain
import io.github.gbkt.gradle.tasks.CompileRomTask
import io.github.gbkt.gradle.tasks.CopyGeneratedCTask
import io.github.gbkt.gradle.tasks.DebugEmulatorTask
import io.github.gbkt.gradle.tasks.GenerateAssetsTask
import io.github.gbkt.gradle.tasks.GenerateCTask
import io.github.gbkt.gradle.tasks.ProcessAssetsTask
import io.github.gbkt.gradle.tasks.RunEmulatorTask
import io.github.gbkt.gradle.tasks.WebExportTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.register

/**
 * Gradle plugin for building Game Boy ROMs from Kotlin DSL.
 *
 * Apply this plugin and configure the `gbkt` extension:
 * ```kotlin
 * plugins {
 *     kotlin("jvm")
 *     id("io.github.gbkt")
 * }
 *
 * gbkt {
 *     game("sample.RunnerGameKt::runnerGame")
 *     assets("src/main/resources/sprites")
 * }
 * ```
 *
 * Then run: `./gradlew buildRom`
 */
class GbktPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Create extension
        val extension = project.extensions.create<GbktExtension>("gbkt")

        // Set defaults
        extension.outputName.convention("game")
        extension.debug.convention(true)
        extension.compilerFlags.convention(emptyList())
        extension.gbcMode.convention("DISABLED")

        // Emulator defaults
        extension.emulator.args.convention(emptyList())
        extension.emulator.liveReload.convention(true)

        // Web export defaults
        extension.web.enableControls.convention(true)
        extension.web.emulatorJsVersion.convention("stable")

        // Default asset directory
        extension.assetDirectory.convention(
            project.layout.projectDirectory.dir("src/main/resources/assets")
        )

        // Optimization defaults
        extension.optimization.enabled.convention(true)
        extension.optimization.verbose.convention(false)
        extension.optimization.quietWhenOptimal.convention(true)
        extension.optimization.detectDuplicates.convention(true)
        extension.optimization.detectEmpty.convention(true)
        extension.optimization.detectLowEntropy.convention(true)
        extension.optimization.detectPaletteWaste.convention(true)
        extension.optimization.lowEntropyThreshold.convention(0.5f)

        // Output defaults
        extension.output.keepGeneratedC.convention(false)
        extension.output.keepSourceMaps.convention(true)
        extension.output.cOutputDir.convention(project.layout.buildDirectory.dir("gbkt/src"))

        // Asset generation defaults
        extension.generateAssets.enabled.convention(false)
        extension.generateAssets.objectName.convention("Assets")

        // Register tasks after project evaluation to pick up configuration
        project.afterEvaluate { registerTasks(project, extension) }
    }

    private fun registerTasks(project: Project, extension: GbktExtension) {
        // Validate required configuration
        if (!extension.game.isPresent) {
            project.logger.warn(
                """
                |gbkt: No game defined. Configure in build.gradle.kts:
                |
                |  gbkt {
                |      game("package.ClassName::propertyName")
                |  }
            """
                    .trimMargin()
            )
            return
        }

        // Get the runtime classpath from the main source set
        val sourceSets = project.extensions.findByType<SourceSetContainer>()
        val mainSourceSet = sourceSets?.findByName("main")

        if (mainSourceSet == null) {
            throw GradleException(
                """
                |gbkt: Cannot find 'main' source set.
                |Make sure you have applied the 'kotlin("jvm")' plugin.
            """
                    .trimMargin()
            )
        }

        val runtimeClasspath = mainSourceSet.runtimeClasspath
        val compileKotlinTask =
            project.tasks.findByName("compileKotlin")
                ?: throw GradleException(
                    "gbkt: 'compileKotlin' task not found. Apply kotlin(\"jvm\") plugin."
                )

        // Register processAssets task for incremental asset processing
        val processAssets =
            project.tasks.register<ProcessAssetsTask>("processAssets") {
                // Only set if assetDirectory is configured
                if (extension.assetDirectory.isPresent) {
                    assetDirectory.set(extension.assetDirectory)
                } else {
                    // Default to src/main/resources/assets if it exists
                    val defaultDir = project.file("src/main/resources/assets")
                    if (defaultDir.exists()) {
                        assetDirectory.set(defaultDir)
                    }
                }
                outputDirectory.set(project.layout.buildDirectory.dir("gbkt/processed-assets"))
                manifestFile.set(project.layout.buildDirectory.file("gbkt/asset-manifest.txt"))
            }

        // Register generateAssets task when enabled
        if (extension.generateAssets.enabled.getOrElse(false)) {
            val packageName =
                extension.generateAssets.packageName.orNull
                    ?: throw GradleException(
                        """
                    |gbkt: generateAssets.packageName is required when generateAssets.enabled is true.
                    |
                    |Configure in build.gradle.kts:
                    |  gbkt {
                    |      generateAssets {
                    |          enabled.set(true)
                    |          packageName.set("com.example.mygame")
                    |      }
                    |  }
                """
                            .trimMargin()
                    )

            val generateAssetsTask =
                project.tasks.register<GenerateAssetsTask>("generateAssets") {
                    // Asset directory from extension or default
                    if (extension.assetDirectory.isPresent) {
                        assetDirectory.set(extension.assetDirectory)
                    } else {
                        val defaultDir = project.file("src/main/resources/assets")
                        assetDirectory.set(defaultDir)
                    }

                    // Output file in generated sources
                    val packagePath = packageName.replace(".", "/")
                    val objectName = extension.generateAssets.objectName.get()
                    outputFile.set(
                        project.layout.buildDirectory.file(
                            "generated/source/gbkt/main/kotlin/$packagePath/$objectName.kt"
                        )
                    )

                    this.packageName.set(packageName)
                    this.objectName.set(extension.generateAssets.objectName)
                }

            // Add generated source directory to main source set
            // Use the Kotlin SourceSet extension to add the source directory
            val kotlinSourceSet = mainSourceSet.extensions.findByName("kotlin")
            if (kotlinSourceSet != null) {
                @Suppress("UNCHECKED_CAST")
                val srcDirs = kotlinSourceSet.javaClass.getMethod("srcDir", Any::class.java)
                srcDirs.invoke(
                    kotlinSourceSet,
                    project.layout.buildDirectory.dir("generated/source/gbkt/main/kotlin")
                )
            } else {
                // Fallback: add to java source set
                mainSourceSet.java.srcDir(
                    project.layout.buildDirectory.dir("generated/source/gbkt/main/kotlin")
                )
            }

            // Make compileKotlin depend on generateAssets
            compileKotlinTask.dependsOn(generateAssetsTask)
        }

        // Register generateC task
        val generateC =
            project.tasks.register<GenerateCTask>("generateC") {
                dependsOn(compileKotlinTask)
                dependsOn(processAssets)

                gameSpec.set(extension.game)
                assetDirectory.set(extension.assetDirectory)
                outputCFile.set(project.layout.buildDirectory.file("gbkt/generated/main.c"))
                this.runtimeClasspath.from(runtimeClasspath)

                // Wire processed assets directory
                processedAssetsDir.set(processAssets.flatMap { it.outputDirectory })

                // Optimization settings
                optimizationEnabled.set(extension.optimization.enabled)
                optimizationVerbose.set(extension.optimization.verbose)
                optimizationQuietWhenOptimal.set(extension.optimization.quietWhenOptimal)
                detectDuplicates.set(extension.optimization.detectDuplicates)
                detectEmpty.set(extension.optimization.detectEmpty)
                detectLowEntropy.set(extension.optimization.detectLowEntropy)
                lowEntropyThreshold.set(extension.optimization.lowEntropyThreshold)
                useColor.set(extension.optimization.useColor)
                useUnicode.set(extension.optimization.useUnicode)
            }

        // Register compileRom task - GBDK discovery is lazy (at task execution time)
        val compileRom =
            project.tasks.register<CompileRomTask>("compileRom") {
                dependsOn(generateC)

                // Lazily determine GBDK home - only when task executes
                gbdkHome.set(
                    extension.gbdkHome.orElse(
                        project.provider { GbdkToolchain.find(null).absolutePath }
                    )
                )
                cSourceFile.set(generateC.flatMap { it.outputCFile })
                compilerFlags.set(extension.compilerFlags)
                generateDebugFiles.set(extension.debug)
                gbcMode.set(extension.gbcMode)
                outputRom.set(
                    project.layout.buildDirectory.file(
                        "gbkt/output/${extension.outputName.get()}.gb"
                    )
                )
            }

        // Register buildRom lifecycle task
        val buildRom =
            project.tasks.register("buildRom") {
                group = "gbkt"
                description = "Build Game Boy ROM from Kotlin DSL"
                dependsOn(compileRom)

                doLast {
                    val romFile = compileRom.get().outputRom.get().asFile
                    if (romFile.exists()) {
                        project.logger.lifecycle("")
                        project.logger.lifecycle("=".repeat(50))
                        project.logger.lifecycle("ROM built successfully!")
                        project.logger.lifecycle("Output: ${romFile.absolutePath}")
                        project.logger.lifecycle("=".repeat(50))
                    }
                }
            }

        // Register copyGeneratedC task only when keepGeneratedC is enabled
        if (extension.output.keepGeneratedC.getOrElse(false)) {
            val copyGeneratedC =
                project.tasks.register<CopyGeneratedCTask>("copyGeneratedC") {
                    dependsOn(generateC)

                    sourceCFile.set(generateC.flatMap { it.outputCFile })

                    // Source map file is next to C file with .gbkt.map extension
                    val buildDir = project.layout.buildDirectory
                    sourceMapFile.set(buildDir.file("gbkt/generated/main.c.gbkt.map"))

                    outputDir.set(extension.output.cOutputDir)

                    copySourceMaps.set(extension.output.keepSourceMaps)
                }

            // Make buildRom depend on copyGeneratedC when enabled
            buildRom.configure { dependsOn(copyGeneratedC) }
        }

        // Register runEmulator task
        project.tasks.register<RunEmulatorTask>("runEmulator") {
            dependsOn(buildRom)

            romFile.set(compileRom.flatMap { it.outputRom })
            emulatorPath.set(extension.emulator.path)
            emulatorArgs.set(extension.emulator.args)
            liveReload.set(extension.emulator.liveReload)
            liveReloadScript.set(extension.emulator.liveReloadScript)
            buildDirectory.set(project.layout.buildDirectory)
        }

        // Register debug emulator task
        val debugEmulator =
            project.tasks.register<DebugEmulatorTask>("debugEmulator") {
                dependsOn(buildRom)

                romFile.set(compileRom.flatMap { it.outputRom })
                sourceMapFile.set(
                    project.layout.buildDirectory.file("gbkt/generated/main.c.gbkt.map")
                )
                emulatorPath.set(extension.emulator.path)
                buildDirectory.set(project.layout.buildDirectory)
            }

        // Register webExport task
        project.tasks.register<WebExportTask>("webExport") {
            dependsOn(buildRom)

            romFile.set(compileRom.flatMap { it.outputRom })
            title.set(extension.web.title.orElse(extension.outputName))
            enableControls.set(extension.web.enableControls)
            emulatorJsVersion.set(extension.web.emulatorJsVersion)
            outputDir.set(project.layout.buildDirectory.dir("web"))
        }

        // Register runWatch task - convenience task for live development
        project.tasks.register("runWatch") {
            group = "gbkt"
            description =
                "Start emulator with live reload and print instructions for continuous build"

            dependsOn(buildRom)

            doLast {
                val romFile = compileRom.get().outputRom.get().asFile
                val liveReloadEnabled = extension.emulator.liveReload.getOrElse(true)

                project.logger.lifecycle("")
                project.logger.lifecycle("=".repeat(60))
                project.logger.lifecycle("LIVE RELOAD DEVELOPMENT MODE")
                project.logger.lifecycle("=".repeat(60))
                project.logger.lifecycle("")
                project.logger.lifecycle("ROM: ${romFile.absolutePath}")
                project.logger.lifecycle("")

                if (liveReloadEnabled) {
                    project.logger.lifecycle(
                        "Live reload is ENABLED. The emulator will automatically"
                    )
                    project.logger.lifecycle("reload the ROM when it detects changes.")
                    project.logger.lifecycle("")
                    project.logger.lifecycle(
                        "To enable continuous builds, run in a separate terminal:"
                    )
                    project.logger.lifecycle("")
                    project.logger.lifecycle("    ./gradlew -t buildRom")
                    project.logger.lifecycle("")
                    project.logger.lifecycle(
                        "This will rebuild the ROM automatically when you save files."
                    )
                } else {
                    project.logger.lifecycle(
                        "Live reload is DISABLED. Enable it in build.gradle.kts:"
                    )
                    project.logger.lifecycle("")
                    project.logger.lifecycle("    gbkt {")
                    project.logger.lifecycle("        emulator {")
                    project.logger.lifecycle("            liveReload.set(true)")
                    project.logger.lifecycle("        }")
                    project.logger.lifecycle("    }")
                }

                project.logger.lifecycle("")
                project.logger.lifecycle("=".repeat(60))
                project.logger.lifecycle("")
            }

            // After printing instructions, launch the emulator
            finalizedBy("runEmulator")
        }

        // Register a clean task for gbkt outputs
        project.tasks.register("cleanGbkt") {
            group = "gbkt"
            description = "Clean gbkt build outputs"
            doLast { project.delete(project.layout.buildDirectory.dir("gbkt")) }
        }
    }
}
