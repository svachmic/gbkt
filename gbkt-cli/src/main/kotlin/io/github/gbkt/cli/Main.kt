/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.cli

import io.github.gbkt.backend.api.BackendRegistry

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printHelp()
        return
    }

    when (args[0]) {
        "new" -> handleNew(args.drop(1))
        "build" -> handleBuild(args.drop(1))
        "run" -> handleRun(args.drop(1))
        "list-targets" -> handleListTargets()
        "help",
        "--help",
        "-h" -> printHelp()
        "version",
        "--version",
        "-v" -> printVersion()
        else -> {
            println("Unknown command: ${args[0]}")
            printHelp()
        }
    }
}

private fun printHelp() {
    println(
        """
        |gbkt - Game Boy Kotlin CLI
        |
        |USAGE:
        |    gbkt <command> [options]
        |
        |COMMANDS:
        |    new <template> <name>    Create a new project from template
        |    build [--target=<id>]    Build the ROM (runs ./gradlew buildRom)
        |    run                      Run the ROM in emulator (runs ./gradlew runEmulator)
        |    list-targets             List available target platforms
        |    help                     Show this help message
        |    version                  Show version information
        |
        |TEMPLATES:
        |    minimal      Empty game with one sprite
        |    platformer   Player with gravity and platforms
        |    rpg          Top-down movement with basic tilemap
        |    puzzle       Grid-based puzzle game starter
        |
        |TARGETS:
        |    gb           Original Game Boy (DMG)
        |    gbc          Game Boy Color (default)
        |
        |EXAMPLES:
        |    gbkt new minimal my-game
        |    gbkt new platformer super-jump
        |    cd my-game && gbkt build
        |    gbkt build --target=gb
        |    gbkt run
        """
            .trimMargin()
    )
}

private fun printVersion() {
    println("gbkt version 0.1.0-SNAPSHOT")
    println("Game Boy Kotlin - DSL framework for Game Boy development")

    // Show available backends
    val backends = BackendRegistry.discover()
    if (backends.isNotEmpty()) {
        println()
        println("Available backends:")
        backends.forEach { println("  ${it.id}: ${it.displayName}") }
    }
}

private fun handleListTargets() {
    println("Available target platforms:")
    println()

    val backends = BackendRegistry.discover()
    if (backends.isEmpty()) {
        println("  (no backends found)")
        return
    }

    for (backend in backends) {
        println("  ${backend.profile.id}")
        println("    Name: ${backend.profile.name}")
        println("    Backend: ${backend.displayName}")
        println("    Screen: ${backend.profile.screen.width}x${backend.profile.screen.height}")
        println("    Max ROM: ${backend.profile.maxRomSize / 1024} KB")
        println()
    }
}
