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
import java.io.File

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

private fun createProject(name: String, template: Template) {
    println("Creating new ${template.name} project: $name")

    val projectDir = File(name)

    // Check if directory already exists
    if (projectDir.exists()) {
        println("Error: Directory '$name' already exists.")
        return
    }

    // Create project directories
    val srcDir = File(projectDir, "src/main/kotlin")
    val assetsDir = File(projectDir, "assets")

    if (!srcDir.mkdirs() || !assetsDir.mkdirs()) {
        println("Error: Could not create project directories.")
        return
    }

    // Write build.gradle.kts
    File(projectDir, "build.gradle.kts").writeText(template.buildGradle(name))

    // Write settings.gradle.kts
    File(projectDir, "settings.gradle.kts").writeText(template.settingsGradle(name))

    // Write Game.kt
    File(srcDir, "Game.kt").writeText(template.gameKt(name))

    // Write .gitignore
    File(projectDir, ".gitignore")
        .writeText(
            """
            |.gradle/
            |build/
            |*.gb
            |*.gbc
            |.idea/
            |*.iml
            """
                .trimMargin()
        )

    println()
    println("Project '$name' created successfully!")
    println()
    println("Next steps:")
    println("  cd $name")
    println("  gbkt build    # Build the ROM")
    println("  gbkt run      # Run in emulator")
}

fun handleBuild(args: List<String>) {
    // Parse --target flag
    val targetArg = args.find { it.startsWith("--target=") }
    val target = targetArg?.substringAfter("=") ?: "gbc"

    println("Building ROM for target: $target")

    // Check if gradlew exists
    val gradlew = if (File("gradlew").exists()) "./gradlew" else "gradle"

    val processBuilder =
        ProcessBuilder(gradlew, "buildRom", "-Pgbkt.target=$target")
            .inheritIO()
            .directory(File("."))

    val result = processBuilder.start().waitFor()
    if (result != 0) {
        println("Build failed with exit code: $result")
    }
}

@Suppress("UnusedParameter", "kotlin:S1172") // Reserved for future flags
fun handleRun(@Suppress("UNUSED_PARAMETER") args: List<String>) {
    println("Running emulator...")

    // Check if gradlew exists
    val gradlew = if (File("gradlew").exists()) "./gradlew" else "gradle"

    val processBuilder = ProcessBuilder(gradlew, "runEmulator").inheritIO().directory(File("."))

    val result = processBuilder.start().waitFor()
    if (result != 0) {
        println("Run failed with exit code: $result")
    }
}
