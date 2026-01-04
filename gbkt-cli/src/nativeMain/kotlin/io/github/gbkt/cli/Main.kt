/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.cli

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printHelp()
        return
    }

    when (args[0]) {
        "new" -> handleNew(args.drop(1))
        "build" -> handleBuild()
        "run" -> handleRun()
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
        |    build                    Build the ROM (runs ./gradlew buildRom)
        |    run                      Run the ROM in emulator (runs ./gradlew runEmulator)
        |    help                     Show this help message
        |    version                  Show version information
        |
        |TEMPLATES:
        |    minimal      Empty game with one sprite
        |    platformer   Player with gravity and platforms
        |    rpg          Top-down movement with basic tilemap
        |    puzzle       Grid-based puzzle game starter
        |
        |EXAMPLES:
        |    gbkt new minimal my-game
        |    gbkt new platformer super-jump
        |    cd my-game && gbkt build
        |    gbkt run
        """
            .trimMargin()
    )
}

private fun printVersion() {
    println("gbkt version 0.1.0-SNAPSHOT")
    println("Game Boy Kotlin - DSL framework for Game Boy development")
}
