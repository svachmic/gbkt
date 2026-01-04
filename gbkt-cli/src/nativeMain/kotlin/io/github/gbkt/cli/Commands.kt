/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.cli

import io.github.gbkt.cli.templates.MinimalTemplate
import io.github.gbkt.cli.templates.PlatformerTemplate
import io.github.gbkt.cli.templates.PuzzleTemplate
import io.github.gbkt.cli.templates.RpgTemplate
import io.github.gbkt.cli.templates.Template
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.system

private val templates: Map<String, Template> =
    mapOf(
        "minimal" to MinimalTemplate,
        "platformer" to PlatformerTemplate,
        "rpg" to RpgTemplate,
        "puzzle" to PuzzleTemplate,
    )

fun handleNew(args: List<String>) {
    if (args.size < 2) {
        println("Usage: gbkt new <template> <name>")
        println()
        println("Available templates:")
        templates.forEach { (name, template) -> println("  $name - ${template.description}") }
        return
    }

    val templateName = args[0]
    val projectName = args[1]

    val template = templates[templateName]
    if (template == null) {
        println("Unknown template: $templateName")
        println("Available templates: ${templates.keys.joinToString(", ")}")
        return
    }

    createProject(projectName, template)
}

@OptIn(ExperimentalForeignApi::class)
private fun createProject(name: String, template: Template) {
    println("Creating new ${template.name} project: $name")

    // Check if directory already exists
    if (fileExists(name)) {
        println("Error: Directory '$name' already exists.")
        return
    }

    // Create project directory and subdirectories using shell command
    // This is cross-platform compatible (works on Linux, macOS)
    val mkdirResult = system("mkdir -p '$name/src/main/kotlin' '$name/assets'")
    if (mkdirResult != 0) {
        println("Error: Could not create project directories.")
        return
    }

    // Write build.gradle.kts
    writeFile("$name/build.gradle.kts", template.buildGradle(name))

    // Write settings.gradle.kts
    writeFile("$name/settings.gradle.kts", template.settingsGradle(name))

    // Write Game.kt
    writeFile("$name/src/main/kotlin/Game.kt", template.gameKt(name))

    // Write .gitignore
    writeFile(
        "$name/.gitignore",
        """
        |.gradle/
        |build/
        |*.gb
        |*.gbc
        |.idea/
        |*.iml
        """
            .trimMargin(),
    )

    println()
    println("Project '$name' created successfully!")
    println()
    println("Next steps:")
    println("  cd $name")
    println("  gbkt build    # Build the ROM")
    println("  gbkt run      # Run in emulator")
}

@OptIn(ExperimentalForeignApi::class)
private fun writeFile(path: String, content: String) {
    val file = platform.posix.fopen(path, "w")
    if (file != null) {
        platform.posix.fputs(content, file)
        platform.posix.fclose(file)
    } else {
        println("Warning: Could not write file: $path")
    }
}

@OptIn(ExperimentalForeignApi::class)
fun handleBuild() {
    println("Building ROM...")

    // Check if gradlew exists
    val gradlew = if (fileExists("gradlew")) "./gradlew" else "gradle"

    val result = system("$gradlew buildRom")
    if (result != 0) {
        println("Build failed with exit code: $result")
    }
}

@OptIn(ExperimentalForeignApi::class)
fun handleRun() {
    println("Running emulator...")

    // Check if gradlew exists
    val gradlew = if (fileExists("gradlew")) "./gradlew" else "gradle"

    val result = system("$gradlew runEmulator")
    if (result != 0) {
        println("Run failed with exit code: $result")
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun fileExists(path: String): Boolean {
    // First try as file
    val file = platform.posix.fopen(path, "r")
    if (file != null) {
        platform.posix.fclose(file)
        return true
    }
    // Then try as directory using opendir
    val dir = platform.posix.opendir(path)
    if (dir != null) {
        platform.posix.closedir(dir)
        return true
    }
    return false
}
