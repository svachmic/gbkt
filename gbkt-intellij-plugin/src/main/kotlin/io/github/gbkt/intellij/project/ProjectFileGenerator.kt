/*
 * Copyright 2026 Michal Svacha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.gbkt.intellij.project

import io.github.gbkt.intellij.project.templates.AssetDescription
import io.github.gbkt.intellij.project.templates.GameTemplate
import java.io.File

/**
 * Generates all files for a new gbkt project.
 *
 * Creates:
 * - Gradle build files (build.gradle.kts, settings.gradle.kts)
 * - Gradle wrapper (gradlew, gradle/wrapper/...)
 * - Source directory structure
 * - Resource/asset folders
 * - Starter game code from template
 */
class ProjectFileGenerator(
    private val projectDir: File,
    private val gameName: String,
    private val packageName: String,
    private val template: GameTemplate,
    private val includeSampleAssets: Boolean,
    private val targetPlatform: GbktModuleBuilder.TargetPlatform,
) {
    private val packagePath = packageName.replace('.', '/')

    /** Generate the complete project structure. */
    fun generate() {
        createDirectoryStructure()
        generateGradleFiles()
        generateGradleWrapper()
        generateSourceFiles()
        generateGitignore()
        if (includeSampleAssets) {
            generateSampleAssets()
        }
        generateAssetReadme()
    }

    private fun createDirectoryStructure() {
        // Source directories
        File(projectDir, "src/main/kotlin/$packagePath").mkdirs()
        File(projectDir, "src/main/resources").mkdirs()
        File(projectDir, "src/test/kotlin/$packagePath").mkdirs()

        // Resource directories
        File(projectDir, "res/sprites").mkdirs()
        File(projectDir, "res/tilemaps").mkdirs()
        File(projectDir, "res/palettes").mkdirs()
        File(projectDir, "res/music").mkdirs()
        File(projectDir, "res/sfx").mkdirs()

        // Generated output (will be gitignored)
        File(projectDir, "generated").mkdirs()
    }

    private fun generateGradleFiles() {
        // build.gradle.kts
        File(projectDir, "build.gradle.kts").writeText(generateBuildGradle())

        // settings.gradle.kts
        File(projectDir, "settings.gradle.kts").writeText(generateSettingsGradle())

        // gradle.properties
        File(projectDir, "gradle.properties").writeText(generateGradleProperties())
    }

    private fun generateBuildGradle(): String =
        """
        |plugins {
        |    kotlin("jvm") version "2.3.0"
        |    id("io.github.gbkt") version "0.1.0"
        |}
        |
        |group = "$packageName"
        |version = "0.1.0"
        |
        |repositories {
        |    mavenCentral()
        |    mavenLocal()
        |}
        |
        |dependencies {
        |    implementation("io.github.gbkt:gbkt-core:0.1.0")
        |    testImplementation(kotlin("test"))
        |}
        |
        |kotlin {
        |    jvmToolchain(21)
        |}
        |
        |gbkt {
        |    gameName = "$gameName"
        |    targetPlatform = "${targetPlatform.name}"
        |
        |    assets {
        |        sprites {
        |            sourceDir = file("res/sprites")
        |            sliceSize = 8 to 8
        |        }
        |        tilemaps {
        |            sourceDir = file("res/tilemaps")
        |        }
        |        palettes {
        |            sourceDir = file("res/palettes")
        |        }
        |    }
        |}
        |
        |tasks.test {
        |    useJUnitPlatform()
        |}
    """
            .trimMargin()

    private fun generateSettingsGradle(): String =
        """
        |rootProject.name = "${gameName.lowercase().replace(" ", "-")}"
        |
        |pluginManagement {
        |    repositories {
        |        mavenLocal()
        |        gradlePluginPortal()
        |        mavenCentral()
        |    }
        |}
        |
        |dependencyResolutionManagement {
        |    repositories {
        |        mavenLocal()
        |        mavenCentral()
        |    }
        |}
    """
            .trimMargin()

    private fun generateGradleProperties(): String =
        """
        |# Gradle properties
        |org.gradle.jvmargs=-Xmx2g -XX:+UseParallelGC
        |org.gradle.parallel=true
        |org.gradle.caching=true
        |
        |# Kotlin properties
        |kotlin.code.style=official
        """
            .trimMargin()

    private fun generateGradleWrapper() {
        // Create wrapper directory
        val wrapperDir = File(projectDir, "gradle/wrapper")
        wrapperDir.mkdirs()

        // gradle-wrapper.properties
        File(wrapperDir, "gradle-wrapper.properties")
            .writeText(
                """
                |distributionBase=GRADLE_USER_HOME
                |distributionPath=wrapper/dists
                |distributionUrl=https\://services.gradle.org/distributions/gradle-9.0-bin.zip
                |networkTimeout=10000
                |validateDistributionUrl=true
                |zipStoreBase=GRADLE_USER_HOME
                |zipStorePath=wrapper/dists
                """
                    .trimMargin()
            )

        // gradlew script (Unix)
        val gradlew = File(projectDir, "gradlew")
        gradlew.writeText(GRADLEW_SCRIPT)
        if (!gradlew.setExecutable(true)) {
            System.err.println(
                "Warning: Failed to make gradlew executable at ${gradlew.absolutePath}"
            )
        }

        // gradlew.bat script (Windows)
        File(projectDir, "gradlew.bat").writeText(GRADLEW_BAT_SCRIPT)

        // Note: gradle-wrapper.jar would need to be bundled with the plugin
        // For now, users can run 'gradle wrapper' to generate it
    }

    private fun generateSourceFiles() {
        // Main game file
        val mainFile = File(projectDir, "src/main/kotlin/$packagePath/$gameName.kt")
        mainFile.writeText(template.generateMainSource(gameName, packageName))

        // Additional source files from template
        template.getAdditionalSources(gameName, packageName).forEach { (relativePath, content) ->
            val file = File(projectDir, "src/main/kotlin/$packagePath/$relativePath")
            file.parentFile.mkdirs()
            file.writeText(content)
        }
    }

    private fun generateGitignore(): String {
        val content =
            """
            |# Gradle
            |.gradle/
            |build/
            |!gradle/wrapper/gradle-wrapper.jar
            |
            |# IDE
            |.idea/
            |*.iml
            |.vscode/
            |
            |# Generated files
            |generated/
            |*.gbc
            |*.gb
            |*.map
            |*.sym
            |
            |# GBDK build artifacts
            |obj/
            |*.o
            |*.lst
            |*.asm
            |
            |# OS files
            |.DS_Store
            |Thumbs.db
            |
            |# Temporary files
            |*.tmp
            |*.bak
            |*~
            """
                .trimMargin()

        File(projectDir, ".gitignore").writeText(content)
        return content
    }

    private fun generateSampleAssets() {
        template.getSampleAssets().forEach { (relativePath, description) ->
            val file = File(projectDir, "res/$relativePath")
            file.parentFile.mkdirs()

            when (description) {
                is AssetDescription.Placeholder -> {
                    // Create a placeholder text file
                    val placeholderFile =
                        File(file.parentFile, "${file.nameWithoutExtension}.placeholder.txt")
                    placeholderFile.writeText(
                        """
                        |Asset: ${file.name}
                        |Description: ${description.description}
                        |
                        |Replace this placeholder with your actual asset file.
                    """
                            .trimMargin()
                    )
                }
                is AssetDescription.PngImage -> {
                    val placeholderFile =
                        File(file.parentFile, "${file.nameWithoutExtension}.placeholder.txt")
                    placeholderFile.writeText(
                        """
                        |Asset: ${file.name}
                        |Type: PNG Image
                        |Size: ${description.width}x${description.height}
                        |Description: ${description.description}
                        |
                        |Create a ${description.width}x${description.height} PNG image with 4 colors max.
                    """
                            .trimMargin()
                    )
                }
                is AssetDescription.Tilemap -> {
                    val placeholderFile =
                        File(file.parentFile, "${file.nameWithoutExtension}.placeholder.txt")
                    placeholderFile.writeText(
                        """
                        |Asset: ${file.name}
                        |Type: Tilemap
                        |Size: ${description.width}x${description.height} tiles
                        |Description: ${description.description}
                        |
                        |Use Tiled (https://www.mapeditor.org/) to create this tilemap.
                    """
                            .trimMargin()
                    )
                }
            }
        }
    }

    private fun generateAssetReadme() {
        File(projectDir, "res/README.md")
            .writeText(
                """
                |# Game Assets
                |
                |This folder contains all game assets in developer-friendly formats.
                |The gbkt build system automatically converts these to GBDK-compatible formats.
                |
                |## Folder Structure
                |
                |```
                |res/
                |├── sprites/      # PNG sprite sheets and individual sprites
                |├── tilemaps/     # Tiled editor (.tmx) or JSON tilemaps
                |├── palettes/     # Color palettes (.gpl, .pal)
                |├── music/        # Music files (MOD, XM tracker formats)
                |└── sfx/          # Sound effects (WAV)
                |```
                |
                |## Asset Guidelines
                |
                |### Sprites (`sprites/`)
                |- Use PNG format with indexed colors
                |- Maximum 4 colors per sprite (including transparent)
                |- Recommended sizes: 8x8, 8x16, 16x16
                |- Name convention: `entity_animation_frame.png` or sprite sheets
                |
                |### Tilemaps (`tilemaps/`)
                |- Use Tiled editor (https://www.mapeditor.org/)
                |- Export as TMX or JSON format
                |- Separate layers for: background, collision, objects
                |- Tileset references should be relative paths
                |
                |### Palettes (`palettes/`)
                |- GIMP palette format (.gpl) or Aseprite format
                |- 4 colors per palette (GBC limitation)
                |- Use GBC-safe colors (5-bit per channel)
                |
                |### Music (`music/`)
                |- MOD or XM tracker format
                |- Will be converted to GBT Player format
                |- Keep within GBC sound limitations
                |
                |### Sound Effects (`sfx/`)
                |- WAV format (mono, 8-bit recommended)
                |- Short duration (GBC has limited audio RAM)
                |
                |## Building
                |
                |Run `./gradlew buildRom` to process all assets and generate the ROM.
                """
                    .trimMargin()
            )
    }

    companion object {
        // Shell pattern for removing path suffix (avoids Kotlin comment parsing issues)
        private const val SHELL_PATH_SUFFIX = "/" + "*"

        // Simplified gradlew script - users should run 'gradle wrapper' for full version
        private val GRADLEW_SCRIPT =
            """
            |#!/bin/sh
            |# Gradle wrapper bootstrap script
            |# Run 'gradle wrapper' to regenerate the full version
            |
            |APP_HOME="${"$"}{0%$SHELL_PATH_SUFFIX}"
            |[ -z "${"$"}APP_HOME" ] && APP_HOME="."
            |APP_HOME="${"$"}(cd "${"$"}APP_HOME" && pwd)"
            |
            |CLASSPATH="${"$"}APP_HOME/gradle/wrapper/gradle-wrapper.jar"
            |
            |if [ -n "${"$"}JAVA_HOME" ]; then
            |    JAVACMD="${"$"}JAVA_HOME/bin/java"
            |else
            |    JAVACMD="java"
            |fi
            |
            |exec "${"$"}JAVACMD" -classpath "${"$"}CLASSPATH" org.gradle.wrapper.GradleWrapperMain "${"$"}@"
        """
                .trimMargin()

        // Simplified gradlew.bat script
        private val GRADLEW_BAT_SCRIPT =
            """
            |@rem Gradle wrapper bootstrap script for Windows
            |@rem Run 'gradle wrapper' to regenerate the full version
            |@if "%DEBUG%"=="" @echo off
            |
            |set DIRNAME=%~dp0
            |if "%DIRNAME%"=="" set DIRNAME=.
            |set APP_HOME=%DIRNAME%
            |
            |set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar
            |
            |if defined JAVA_HOME (
            |    set JAVA_EXE=%JAVA_HOME%\bin\java.exe
            |) else (
            |    set JAVA_EXE=java.exe
            |)
            |
            |"%JAVA_EXE%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
            """
                .trimMargin()
    }
}
