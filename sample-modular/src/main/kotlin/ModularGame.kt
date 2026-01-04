/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package modular

import io.github.gbkt.core.compileWithAssets
import io.github.gbkt.core.gbGame
import io.github.gbkt.core.generateTestSprite
import modular.entities.createEnemy
import modular.entities.createPlayer
import modular.modules.setupPlayerModule
import modular.scenes.createGameplayScene
import modular.scenes.createTitleScene
import modular.scenes.setGameplayScene

/**
 * Sample Modular Game
 *
 * Demonstrates multi-file game organization with gbkt. See CONTRIBUTING.md and
 * context/DEVELOPER_EXPERIENCE.md for patterns.
 *
 * Structure:
 * - modules/ Extension functions for GameBuilder setup
 * - scenes/ Scene factory functions
 * - entities/ Entity factory functions
 */
val modularGame =
    gbGame("ModularDemo") {

        // === Module Setup ===
        // Modules initialize palettes, global variables, and configuration.
        // Order may matter - initialize dependencies first.
        setupPlayerModule()

        // === Entity Creation ===
        // Entities are created via factory functions.
        // These return Entity references for use in scenes.
        val player = createPlayer()
        val enemy = createEnemy()

        // === Scene Creation ===
        // Scenes are created via factory functions.
        // Pass dependencies explicitly as parameters.
        val titleScene = createTitleScene()
        val gameplayScene = createGameplayScene(player, enemy, titleScene)

        // Link scenes for navigation (since title is created before gameplay)
        setGameplayScene(gameplayScene)

        // === Starting Scene ===
        start = titleScene
    }

// =============================================================================
// Build Entry Point
// =============================================================================

fun main() {
    println(
        """
        ╔═══════════════════════════════════════╗
        ║      gbkt - Modular Game Example      ║
        ╠═══════════════════════════════════════╣
        ║  Demonstrates multi-file organization ║
        ║  See: CONTRIBUTING.md                 ║
        ║  See: context/DEVELOPER_EXPERIENCE.md ║
        ╚═══════════════════════════════════════╝
    """
            .trimIndent()
    )
    println()

    val assetDir = "sample-modular/src/main/resources/sprites"
    println("Generating test sprites...")
    generateTestSprite("$assetDir/player.png", 8, 16)
    generateTestSprite("$assetDir/enemy.png", 8, 8)
    println()

    println("Compiling: ${modularGame.name}")
    val code = compileWithAssets(modularGame, assetDir)

    println("Generated ${code.lines().size} lines of C")
    println()
    println("=".repeat(50))
    println(code)
    println("=".repeat(50))
}
