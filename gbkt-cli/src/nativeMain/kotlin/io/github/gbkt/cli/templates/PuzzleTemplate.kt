/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.cli.templates

/** Puzzle template - grid-based puzzle game starter. */
object PuzzleTemplate : Template {
    override val name = "puzzle"
    override val description = "Grid-based puzzle game starter"

    override fun buildGradle(projectName: String): String = commonBuildGradle(projectName)

    override fun settingsGradle(projectName: String): String = commonSettingsGradle(projectName)

    override fun gameKt(projectName: String): String =
        """
        |import io.github.gbkt.core.assets.SpriteAsset
        |import io.github.gbkt.core.dsl.*
        |
        |/**
        | * $projectName - A puzzle game built with gbkt
        | *
        | * Features:
        | * - Grid-based movement
        | * - Tile-snapped player position
        | * - Move counter and level tracking
        | * - Simple goal detection
        | */
        |fun main() = gameBoy("$projectName") {
        |    // Grid configuration
        |    val gridSize = 16      // Pixels per grid cell
        |    val gridWidth = 10     // Grid cells wide
        |    val gridHeight = 9     // Grid cells tall
        |
        |    // Game state
        |    var playerGridX by u8Var()
        |    var playerGridY by u8Var()
        |    var moveCount by u16Var()
        |    var level by u8Var()
        |    var inputCooldown by u8Var()  // Prevent too-fast movement
        |
        |    // Goal position (for win condition)
        |    var goalX by u8Var()
        |    var goalY by u8Var()
        |
        |    // Initialize starting positions
        |    playerGridX set 1
        |    playerGridY set 1
        |    goalX set 8
        |    goalY set 7
        |    level set 1
        |
        |    // Player cursor/piece sprite
        |    val player = sprite(SpriteAsset("player.png")) {
        |        size = 16 x 16
        |        position(playerGridX * gridSize, playerGridY * gridSize)
        |    }
        |
        |    // Goal marker sprite
        |    // val goal = sprite(SpriteAsset("goal.png")) {
        |    //     size = 16 x 16
        |    //     position(goalX * gridSize, goalY * gridSize)
        |    // }
        |
        |    // Gameplay scene
        |    scene("gameplay") {
        |        every.frame {
        |            // Cooldown timer for grid movement
        |            whenever(inputCooldown isAbove 0) {
        |                inputCooldown -= 1
        |            }
        |
        |            // Grid-based movement (only when cooldown is 0)
        |            whenever(inputCooldown isEqual 0) {
        |                whenever(button.left.isPressed and (playerGridX isAbove 0)) {
        |                    playerGridX -= 1
        |                    moveCount += 1
        |                    inputCooldown set 8
        |                }
        |                whenever(button.right.isPressed and (playerGridX isBelow (gridWidth - 1))) {
        |                    playerGridX += 1
        |                    moveCount += 1
        |                    inputCooldown set 8
        |                }
        |                whenever(button.up.isPressed and (playerGridY isAbove 0)) {
        |                    playerGridY -= 1
        |                    moveCount += 1
        |                    inputCooldown set 8
        |                }
        |                whenever(button.down.isPressed and (playerGridY isBelow (gridHeight - 1))) {
        |                    playerGridY += 1
        |                    moveCount += 1
        |                    inputCooldown set 8
        |                }
        |            }
        |
        |            // Snap player sprite to grid
        |            player.x set (playerGridX * gridSize)
        |            player.y set (playerGridY * gridSize)
        |
        |            // Check win condition
        |            whenever((playerGridX isEqual goalX) and (playerGridY isEqual goalY)) {
        |                scene("win")
        |            }
        |
        |            // Reset level with SELECT
        |            whenever(button.select.justPressed) {
        |                playerGridX set 1
        |                playerGridY set 1
        |                moveCount set 0
        |            }
        |        }
        |    }
        |
        |    // Win scene
        |    scene("win") {
        |        text("LEVEL CLEAR!", 4, 6)
        |        text("Moves: ", 4, 8)
        |        // text(moveCount.toString(), 11, 8)  // Display move count
        |
        |        text("A: Next Level", 3, 12)
        |
        |        every.frame {
        |            whenever(button.a.justPressed) {
        |                level += 1
        |                moveCount set 0
        |                playerGridX set 1
        |                playerGridY set 1
        |                // Update goal position for next level
        |                scene("gameplay")
        |            }
        |        }
        |    }
        |
        |    // Title scene
        |    scene("title") {
        |        text("$projectName", 4, 5)
        |        text("A Puzzle Game", 3, 7)
        |        text("Press START", 4, 11)
        |
        |        every.frame {
        |            whenever(button.start.justPressed) {
        |                scene("gameplay")
        |            }
        |        }
        |    }
        |
        |    start("title")
        |}
        """
            .trimMargin()
}
