/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package sample

import io.github.gbkt.core.Cartridge
import io.github.gbkt.core.Checksum
import io.github.gbkt.core.SceneRef
import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.builder.saveData
import io.github.gbkt.core.buttons
import io.github.gbkt.core.dpad
import io.github.gbkt.core.entity.entity
import io.github.gbkt.core.gbGame
import io.github.gbkt.core.ir.plus
import io.github.gbkt.core.ir.u16Var
import io.github.gbkt.core.ir.u8Var
import io.github.gbkt.core.ir.x
import io.github.gbkt.core.print
import io.github.gbkt.core.printCentered
import io.github.gbkt.core.screen
import io.github.gbkt.core.whenever

/**
 * Save Game: Persistence Demo
 *
 * Demonstrates the gbkt save system:
 * - Save data structure definition
 * - Save/load operations
 * - Multiple save slots
 *
 * Perfect for learning SRAM persistence patterns.
 */
val saveGame =
    gbGame("SaveDemo") {

        // === Configuration ===
        // Note: cartridge type uses MBC5_RAM_BATTERY for SRAM support
        config { cartridge = Cartridge.MBC5_RAM_BATTERY }

        // === Save Data Structure ===
        val save =
            saveData("savegame") {
                // Define fields for save data
                u16Field() // score
                u16Field() // highScore
                u8Field() // gamesPlayed
                u8Field(default = 1) // level

                config {
                    slots = 1 // Single save slot
                    checksum = Checksum.CRC8 // Data integrity check
                    magic = "SAVE" // Validation marker
                    version = 1 // Save format version
                }
            }

        // === Runtime Variables ===
        var currentScore by u16Var(0) // Score for current session
        var highScore by u16Var(0) // Best score (loaded from save)
        var scoreMultiplier by u8Var(1) // Points per collection
        var isPlaying by u8Var(0) // 1 = in game, 0 = in menu

        // === Player Entity ===
        val player by entity {
            position(80, 72)

            sprite(SpriteAsset("player.png")) {
                size = 8 x 8
                hitbox(0, 0, 8, 8)
            }
        }

        // === Collectible Entity ===
        val coin by entity {
            position(40, 40)

            sprite(SpriteAsset("coin.png")) {
                size = 8 x 8
                hitbox(0, 0, 8, 8)
            }
        }

        // === Scene Definitions ===
        lateinit var titleScene: SceneRef
        lateinit var gameScene: SceneRef

        // === Title Scene ===
        titleScene =
            scene("title") {
                enter {
                    screen.clear()
                    screen.hideSprites()
                    isPlaying set 0

                    printCentered("SAVE DEMO") at 3

                    // Check if save exists
                    whenever(save.exists(slot = 0)) {
                        save.load(slot = 0)
                        printCentered("SAVE FOUND") at 6
                        printCentered("CONTINUE") at 10
                    }

                    printCentered("PRESS START") at 13
                    print("SELECT: Erase save") at (1 to 16)
                }

                every.frame {
                    // Start game
                    whenever(buttons.start.pressed) { scene(gameScene) }

                    // Erase save data
                    whenever(buttons.select.pressed) {
                        save.erase(slot = 0)
                        printCentered("ERASED!") at 8
                    }
                }
            }

        // === Game Scene ===
        gameScene =
            scene("game") {
                enter {
                    screen.clear()
                    screen.showSprites()
                    isPlaying set 1

                    // Initialize game
                    currentScore set 0
                    scoreMultiplier set 1

                    // Reset positions
                    player.x set 80
                    player.y set 72
                    coin.x set 40
                    coin.y set 40

                    // HUD
                    print("SCORE:") at (0 to 0)
                    print("A:Save B:Exit") at (0 to 17)
                }

                every.frame {
                    // === Player Movement ===
                    whenever(dpad.right) { player.x += 2 }
                    whenever(dpad.left) { player.x -= 2 }
                    whenever(dpad.down) { player.y += 2 }
                    whenever(dpad.up) { player.y -= 2 }

                    // Boundaries
                    whenever(player.x isBelow 0) { player.x set 0 }
                    whenever(player.x isAbove 152) { player.x set 152 }
                    whenever(player.y isBelow 16) { player.y set 16 }
                    whenever(player.y isAbove 136) { player.y set 136 }

                    // === Coin Collection ===
                    whenever(player collidesWith coin) {
                        // Add points
                        currentScore += scoreMultiplier * 10

                        // Move coin to new position
                        coin.x set 20 + ((currentScore * 7) rem 120)
                        coin.y set 24 + ((currentScore * 13) rem 100)
                    }

                    // === Save on A button ===
                    whenever(buttons.a.pressed) {
                        save.save(slot = 0)
                        print("SAVED!") at (7 to 8)
                    }

                    // === Exit on B button ===
                    whenever(buttons.b.pressed) { scene(titleScene) }
                }
            }

        // Start with title scene
        start = titleScene
    }

// =============================================================================
// Build & Run
// =============================================================================

fun main() {
    println(
        """
        +---------------------------------------+
        |      gbkt - Save System Demo          |
        +---------------------------------------+
        | Features:                             |
        |   - SRAM save/load                    |
        |   - Checksum validation               |
        |   - Single save slot                  |
        +---------------------------------------+
    """
            .trimIndent()
    )
    println()

    println("Compiling: ${saveGame.name}")

    val code = saveGame.compile()

    println()
    println("Generated ${code.lines().size} lines of C")
    println()

    println("=".repeat(50))
    println(code)
    println("=".repeat(50))
}
