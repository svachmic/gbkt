/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.gradle

import io.github.gbkt.gradle.internal.GbdkToolchain
import io.github.gbkt.gradle.tasks.BudgetReportTask
import io.github.gbkt.gradle.tasks.CaptureScreenshotTask
import io.github.gbkt.gradle.tasks.CompileRomTask
import io.github.gbkt.gradle.tasks.ConvertSpritesTask
import io.github.gbkt.gradle.tasks.ConvertZoneTilesetsTask
import io.github.gbkt.gradle.tasks.CopyGeneratedCTask
import io.github.gbkt.gradle.tasks.DebugEmulatorTask
import io.github.gbkt.gradle.tasks.DiffScreenshotsTask
import io.github.gbkt.gradle.tasks.EmulatorTestTask
import io.github.gbkt.gradle.tasks.GenerateAssetsTask
import io.github.gbkt.gradle.tasks.GenerateCTask
import io.github.gbkt.gradle.tasks.GenerateGameConstantsTask
import io.github.gbkt.gradle.tasks.GeneratePlaybookTask
import io.github.gbkt.gradle.tasks.ProcessAssetsTask
import io.github.gbkt.gradle.tasks.ReadVariableTask
import io.github.gbkt.gradle.tasks.RunEmulatorTask
import io.github.gbkt.gradle.tasks.RunInputScriptTask
import io.github.gbkt.gradle.tasks.SaveStateTask
import io.github.gbkt.gradle.tasks.SetupClaudeTask
import io.github.gbkt.gradle.tasks.ValidateRomTask
import io.github.gbkt.gradle.tasks.WebExportTask
import java.io.File
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
        extension.target.convention("gbc")
        extension.ramBanks.convention(0)
        extension.skipValidation.convention(false)
        extension.budgetReport.convention(true)
        // Emulator defaults (embedded Coffee-GB)
        extension.emulator.scale.convention(4)
        extension.emulator.headless.convention(false)
        extension.emulator.maxFrames.convention(600)

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

        // Sprites pipeline defaults (Phase 13.6 REQ-4 / D-01 / D-02)
        extension.sprites.strictTransparency.convention(false)

        // Locale defaults (compile-time i18n)
        extension.locale.convention("en")

        // Apply -Pgbkt.locale project property override.
        // Enables: ./gradlew buildRom -Pgbkt.locale=cs → labyrinth_cs.gb
        val localeFromProp = project.findProperty("gbkt.locale") as? String
        if (localeFromProp != null) {
            extension.locale.set(localeFromProp)
        }

        // Register gbktSetupClaude BEFORE afterEvaluate — available even without a game configured
        registerSetupClaudeTask(project)

        // Register tasks after project evaluation to pick up configuration
        project.afterEvaluate {
            checkClaudeSkillsVersion(project)
            registerTasks(project, extension)
        }
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
                outputDirectory.set(project.layout.buildDirectory.dir("generated/assets"))
                manifestFile.set(
                    project.layout.buildDirectory.file("generated/assets/asset-manifest.json")
                )
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
                    project.layout.buildDirectory.dir("generated/source/gbkt/main/kotlin"),
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
                target.set(extension.target)
                assetDirectory.set(extension.assetDirectory)
                outputDir.set(project.layout.buildDirectory.dir("gbkt/generated"))
                this.runtimeClasspath.from(runtimeClasspath)

                // Wire processed assets directory
                processedAssetsDir.set(processAssets.flatMap { it.outputDirectory })

                // Locale for PO file selection (compile-time i18n)
                locale.set(extension.locale)

                // Validation
                skipValidation.set(extension.skipValidation)

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

        // Register generateGameConstants task — reads game_metadata.json, generates
        // GameConstants.kt
        // for test code to use instead of magic strings. Output goes to the test source set.
        val generateGameConstants =
            project.tasks.register<GenerateGameConstantsTask>("generateGameConstants") {
                dependsOn(generateC)
                metadataFile.set(
                    generateC.flatMap { it.outputDir }.map { it.file("game_metadata.json") }
                )

                val className = extension.game.get().split("::")[0]
                val pkg = className.substringBeforeLast(".")
                val packagePath = pkg.replace(".", "/")

                this.packageName.set(pkg)
                outputFile.set(
                    project.layout.buildDirectory.file(
                        "generated/source/gbkt/test/kotlin/$packagePath/GameConstants.kt"
                    )
                )
            }

        // Register generatePlaybook task — generates PLAYBOOK.md skeleton from metadata.
        // Output goes to the project root (not build/) so developers can edit it in source control.
        // The task does NOT overwrite an existing PLAYBOOK.md — idempotent once authored.
        project.tasks.register<GeneratePlaybookTask>("generatePlaybook") {
            dependsOn(generateC)
            metadataFile.set(
                generateC.flatMap { it.outputDir }.map { it.file("game_metadata.json") }
            )
            gameName.set(extension.outputName)
            outputFile.set(project.file("PLAYBOOK.md"))
        }

        // Add generated test source directory to test source set
        val testSourceSet = sourceSets?.findByName("test")
        if (testSourceSet != null) {
            val testKotlinSS = testSourceSet.extensions.findByName("kotlin")
            if (testKotlinSS != null) {
                @Suppress("UNCHECKED_CAST")
                val srcDirs = testKotlinSS.javaClass.getMethod("srcDir", Any::class.java)
                srcDirs.invoke(
                    testKotlinSS,
                    project.layout.buildDirectory.dir("generated/source/gbkt/test/kotlin"),
                )
            } else {
                testSourceSet.java.srcDir(
                    project.layout.buildDirectory.dir("generated/source/gbkt/test/kotlin")
                )
            }
            project.tasks.findByName("compileTestKotlin")?.dependsOn(generateGameConstants)
        }

        // Register budgetReport task — runs analysis pipeline and prints ASCII budget report
        project.tasks.register<BudgetReportTask>("budgetReport") {
            dependsOn(compileKotlinTask)

            gameSpec.set(extension.game)
            target.set(extension.target)
            this.runtimeClasspath.from(runtimeClasspath)
        }

        // Register convertSprites task — runs png2asset on sprite PNGs, generates .c/.h files
        // alongside the generated C so lcc can find sprite includes like "sprites/paddle.h"
        val convertSprites =
            project.tasks.register<ConvertSpritesTask>("convertSprites") {
                dependsOn(generateC)

                gbdkHome.set(
                    extension.gbdkHome.orElse(
                        project.provider { GbdkToolchain.find(null).absolutePath }
                    )
                )
                assetDirectory.set(extension.assetDirectory)
                metadataFile.set(
                    generateC.flatMap { it.outputDir }.map { it.file("game_metadata.json") }
                )
                cSourceDir.set(generateC.flatMap { it.outputDir })
                // Phase 13.6 REQ-4 / D-01 / D-02: thread strictTransparency from SpritesExtension
                // into the task so it is an @Input (Gradle up-to-date / caching key).
                strictTransparency.set(extension.sprites.strictTransparency)
            }

        // generateGameConstants reads game_metadata.json from build/gbkt/generated/ — the same
        // directory that convertSprites declares as @OutputDirectory. Gradle 9 validation rejects
        // an @InputFile inside another task's @OutputDirectory without an explicit ordering edge.
        // dependsOn (not mustRunAfter) is required because mustRunAfter is an ordering hint only
        // and does not satisfy the file-input/output overlap validation. convertSprites skips
        // gracefully when GBDK is not installed, so this does not break test-only workflows.
        generateGameConstants.configure { dependsOn(convertSprites) }

        // Phase 11.2 (D-A2): convertZoneTilesets — png2asset for zone tileset PNGs (sibling to
        // convertSprites)
        val convertZoneTilesets =
            project.tasks.register<ConvertZoneTilesetsTask>("convertZoneTilesets") {
                dependsOn(generateC)
                // convertSprites and convertZoneTilesets share cSourceDir = build/gbkt/generated as
                // an @OutputDirectory. Gradle 9 fails fast when one task's input directory overlaps
                // another's output without an explicit ordering edge. mustRunAfter (not dependsOn)
                // is correct here — the two tasks are independent in purpose; they only share the
                // output dir for lcc include resolution.
                mustRunAfter(convertSprites)
                gbdkHome.set(
                    extension.gbdkHome.orElse(
                        project.provider { GbdkToolchain.find(null).absolutePath }
                    )
                )
                assetDirectory.set(extension.assetDirectory)
                metadataFile.set(
                    generateC.flatMap { it.outputDir }.map { it.file("game_metadata.json") }
                )
                cSourceDir.set(generateC.flatMap { it.outputDir })
            }

        // convertZoneTilesets also declares build/gbkt/generated/ as @OutputDirectory — same
        // Gradle 9 file-input/output overlap pattern as convertSprites above. mustRunAfter
        // satisfies the Gradle 9 configuration validation (unlike convertSprites which requires
        // dependsOn because it must complete first to avoid flaking INV-8). convertZoneTilesets
        // writes zone-specific tilemap .c files to the same directory; generateGameConstants only
        // reads game_metadata.json (written by generateC, not by this task). mustRunAfter
        // provides the ordering guarantee Gradle 9 requires without adding an execution dependency.
        generateGameConstants.configure { mustRunAfter(convertZoneTilesets) }

        // Register copyResources task - copies binary assets needed by INCBIN directives
        val copyResources =
            project.tasks.register("copyResources") {
                group = "gbkt"
                description = "Copy resource files for GBDK compilation"
                dependsOn(generateC)

                // Only configure if resourceDirectory is set and exists
                val resDir = extension.resourceDirectory
                if (resDir.isPresent && resDir.get().asFile.exists()) {
                    inputs.dir(resDir)
                    outputs.dir(generateC.flatMap { it.outputDir }.map { it.dir("res") })

                    doLast {
                        val srcDir = resDir.get().asFile
                        val destDir = File(generateC.get().outputDir.get().asFile, "res")
                        destDir.mkdirs()
                        srcDir.copyRecursively(destDir, overwrite = true)
                        project.logger.lifecycle(
                            "Copied resources: ${srcDir.absolutePath} -> ${destDir.absolutePath}"
                        )
                    }
                }
            }

        // Register compileRom task - GBDK discovery is lazy (at task execution time)
        val compileRom =
            project.tasks.register<CompileRomTask>("compileRom") {
                dependsOn(generateC)
                dependsOn(convertSprites)
                dependsOn(convertZoneTilesets)
                dependsOn(copyResources)

                // Lazily determine GBDK home - only when task executes
                gbdkHome.set(
                    extension.gbdkHome.orElse(
                        project.provider { GbdkToolchain.find(null).absolutePath }
                    )
                )
                cSourceDir.set(generateC.flatMap { it.outputDir })
                compilerFlags.set(extension.compilerFlags)
                generateDebugFiles.set(extension.debug)
                gbcMode.set(extension.gbcMode)
                ramBanks.set(extension.ramBanks)
                // Build locale-aware output ROM name.
                // Locale suffix is appended ONLY when an explicit locale override was
                // specified (via -Pgbkt.locale or gbkt { locale.set(...) }).
                // When only the convention default is in effect, the name is unchanged
                // to preserve backward compatibility (game.gb not game_en.gb).
                // Examples:
                //   ./gradlew buildRom -Pgbkt.locale=cs → labyrinth_cs.gb
                //   ./gradlew buildRom (no locale override) → game.gb
                val baseName = extension.outputName.get()
                val localeExplicitlySet = project.findProperty("gbkt.locale") != null
                val locale = extension.locale.getOrElse("en")
                val romFileName =
                    if (
                        localeExplicitlySet && locale.isNotBlank() && !baseName.endsWith("_$locale")
                    ) {
                        "${baseName}_${locale}.gb"
                    } else {
                        "${baseName}.gb"
                    }
                outputRom.set(project.layout.buildDirectory.file("gbkt/output/$romFileName"))
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

        // Register `run` lifecycle task — full pipeline: generateC → buildRom → launch emulator
        // This is the recommended single command for the full development loop.
        // Uses dependsOn (not finalizedBy) so the emulator only launches when the build succeeds.
        project.tasks.register("run") {
            group = "gbkt"
            description = "Full pipeline: generateC → buildRom → launch embedded emulator"
            dependsOn("runEmulator") // runEmulator already dependsOn(buildRom)
        }

        // Register validateRom task — opt-in validation via mGBA Lua scripting
        // Does NOT depend on buildRom; user runs ./gradlew validateRom explicitly
        // Uses externalEmulator path if configured (falls back to mGBA auto-detection)
        project.tasks.register<ValidateRomTask>("validateRom") {
            dependsOn(compileRom)
            romFile.set(compileRom.flatMap { it.outputRom })
            emulatorPath.set(extension.emulator.externalEmulator)
        }

        // Register emulatorTest task — CI-safe headless emulator test
        // Depends on buildRom so the ROM is always current before testing.
        // Uses embedded Coffee-GB emulator — no external emulator required.
        project.tasks.register<EmulatorTestTask>("emulatorTest") {
            dependsOn(buildRom)
            romFile.set(compileRom.flatMap { it.outputRom })
            maxFrames.set(extension.emulator.maxFrames)
            headless.set(true)
        }

        // Register copyGeneratedC task only when keepGeneratedC is enabled
        if (extension.output.keepGeneratedC.getOrElse(false)) {
            val copyGeneratedC =
                project.tasks.register<CopyGeneratedCTask>("copyGeneratedC") {
                    dependsOn(generateC)

                    sourceCDir.set(generateC.flatMap { it.outputDir })
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
            scale.set(extension.emulator.scale)
            headless.set(extension.emulator.headless)
            externalEmulator.set(extension.emulator.externalEmulator)
            buildDirectory.set(project.layout.buildDirectory)
        }

        // Register debug emulator task
        project.tasks.register<DebugEmulatorTask>("debugEmulator") {
            dependsOn(buildRom)

            romFile.set(compileRom.flatMap { it.outputRom })
            headless.set(extension.emulator.headless)
            scale.set(extension.emulator.scale)
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

        // Register runWatch task - convenience task for live development (embedded emulator)
        project.tasks.register("runWatch") {
            group = "gbkt"
            description = "Full pipeline: generateC → buildRom → launch embedded emulator"

            doLast {
                val romFile = compileRom.get().outputRom.get().asFile

                project.logger.lifecycle("")
                project.logger.lifecycle("=".repeat(60))
                project.logger.lifecycle("GBKT EMBEDDED EMULATOR")
                project.logger.lifecycle("=".repeat(60))
                project.logger.lifecycle("")
                project.logger.lifecycle("ROM: ${romFile.absolutePath}")
                project.logger.lifecycle("")
                project.logger.lifecycle("The embedded Coffee-GB emulator is launching.")
                project.logger.lifecycle("To rebuild and re-launch, run in a separate terminal:")
                project.logger.lifecycle("")
                project.logger.lifecycle("    ./gradlew -t buildRom")
                project.logger.lifecycle("")
                project.logger.lifecycle("=".repeat(60))
                project.logger.lifecycle("")
            }

            // After printing instructions, launch the emulator
            dependsOn("runEmulator")
        }

        // ── Agent DX tasks (gbkt-agent group) ──────────────────────────────────
        // These tasks expose the AgentDebugSession primitives as CLI-callable commands so
        // agents (Claude via Bash) can invoke them with ./gradlew <task>.

        // Register captureScreenshot task
        project.tasks.register<CaptureScreenshotTask>("captureScreenshot") {
            description = "Capture screenshot from ROM after N frames"
            dependsOn(buildRom)
            romFile.set(compileRom.flatMap { it.outputRom })
            screenshotDir.set(project.layout.buildDirectory.dir("gbkt/screenshots"))
        }

        // Register runScript task — executes a line-based input script against the ROM
        project.tasks.register<RunInputScriptTask>("runScript") {
            description = "Execute an input script against ROM in headless emulator"
            dependsOn(buildRom)
            romFile.set(compileRom.flatMap { it.outputRom })
        }

        // Register readVariable task — reads a named DSL variable from the running ROM
        project.tasks.register<ReadVariableTask>("readVariable") {
            description = "Read a named DSL variable from ROM after N frames"
            dependsOn(buildRom)
            romFile.set(compileRom.flatMap { it.outputRom })
        }

        // Register saveState task — saves (and optionally loads) emulator state checkpoints
        project.tasks.register<SaveStateTask>("saveState") {
            description = "Save emulator state checkpoint after N frames"
            dependsOn(buildRom)
            romFile.set(compileRom.flatMap { it.outputRom })
            stateFile.set(project.layout.buildDirectory.file("gbkt/states/checkpoint.gbst"))
        }

        // Register diffScreenshots task — pixel-level screenshot comparison
        // Note: does NOT depend on buildRom — it is a pure file comparison task.
        project.tasks.register<DiffScreenshotsTask>("diffScreenshots") {
            description = "Compare two screenshots pixel-by-pixel (fails on mismatch)"
        }

        // Register a clean task for gbkt outputs
        project.tasks.register("cleanGbkt") {
            group = "gbkt"
            description = "Clean gbkt build outputs"
            doLast { project.delete(project.layout.buildDirectory.dir("gbkt")) }
        }
    }

    private fun registerSetupClaudeTask(project: Project) {
        project.tasks.register<SetupClaudeTask>("gbktSetupClaude") {
            pluginVersion.set(project.provider { resolvePluginVersion() })
            claudeDir.set(project.rootProject.layout.projectDirectory.dir(".claude"))
            headedMode.convention(true)
        }
    }

    private fun checkClaudeSkillsVersion(project: Project) {
        // Only warn once per build (avoid duplicate warnings in multi-module builds)
        val alreadyChecked = project.rootProject.extensions.extraProperties
        val key = "gbkt.claude.version.checked"
        if (alreadyChecked.has(key)) return
        alreadyChecked.set(key, true)

        val versionFile = File(project.rootProject.projectDir, ".claude/.gbkt-version")
        if (!versionFile.exists()) return
        val installed = versionFile.readText().trim()
        val current = resolvePluginVersion()
        if (installed != current) {
            project.logger.warn(
                "gbkt: Claude Code skills are outdated (installed: $installed, current: $current). " +
                    "Run ./gradlew gbktSetupClaude to update."
            )
        }
    }

    private fun resolvePluginVersion(): String =
        GbktPlugin::class.java.`package`?.implementationVersion ?: "dev"
}
