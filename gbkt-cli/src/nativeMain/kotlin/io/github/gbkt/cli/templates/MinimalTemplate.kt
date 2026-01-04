/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.cli.templates

/** Minimal template - empty game with one sprite. */
object MinimalTemplate : Template {
    override val name = "minimal"
    override val description = "Empty game with one sprite"

    override fun buildGradle(projectName: String): String = commonBuildGradle(projectName)

    override fun settingsGradle(projectName: String): String = commonSettingsGradle(projectName)

    override fun gameKt(projectName: String): String =
        """
        |import io.github.gbkt.core.assets.SpriteAsset
        |import io.github.gbkt.core.dsl.*
        |
        |/**
        | * $projectName - A Game Boy game built with gbkt
        | *
        | * This is a minimal starter template with a single sprite.
        | * Customize this file to build your game!
        | */
        |fun main() = gameBoy("$projectName") {
        |    // Define a simple player sprite
        |    // Place your sprite image at assets/player.png (8x8 or 8x16 pixels)
        |    val player = sprite(SpriteAsset("player.png")) {
        |        size = 8 x 8
        |        position(80, 72)  // Center of screen (160x144)
        |    }
        |
        |    // Main game scene
        |    scene("main") {
        |        // Handle input every frame
        |        every.frame {
        |            // Move player with D-pad
        |            whenever(button.left.isPressed) { player.x -= 1 }
        |            whenever(button.right.isPressed) { player.x += 1 }
        |            whenever(button.up.isPressed) { player.y -= 1 }
        |            whenever(button.down.isPressed) { player.y += 1 }
        |        }
        |    }
        |
        |    // Start the game
        |    start("main")
        |}
        """
            .trimMargin()
}
