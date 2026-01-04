/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package sample

import io.github.gbkt.core.Cartridge
import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.dpad
import io.github.gbkt.core.entity.entity
import io.github.gbkt.core.gbGame
import io.github.gbkt.core.ir.u8Var
import io.github.gbkt.core.ir.x
import io.github.gbkt.core.print
import io.github.gbkt.core.printCentered
import io.github.gbkt.core.screen

/**
 * Minimal Game: Beginner-Friendly Example
 *
 * This is the simplest possible gbkt game demonstrating:
 * - Single sprite with owned position
 * - D-pad movement
 * - Simple boundary checking
 *
 * Perfect starting point for learning the gbkt DSL.
 */
val minimalGame =
    gbGame("MinimalGame") {

        // === Configuration ===
        // Using ROM_ONLY (no MBC) - simplest cartridge type
        config { cartridge = Cartridge.ROM_ONLY }

        // === Player Sprite ===
        // Define an 8x8 sprite with built-in position tracking
        val player by entity {
            position(80, 72) // Start at center of screen

            sprite(SpriteAsset("player.png")) {
                size = 8 x 8
                hitbox(0, 0, 8, 8)
            }
        }

        // === Movement Speed ===
        var speed by u8Var(2) // Pixels per frame

        // === Main Scene ===
        start =
            scene("main") {
                enter {
                    // Initialize screen
                    screen.clear()
                    screen.showSprites()

                    // Set starting position
                    player.x set 80
                    player.y set 72

                    // Display title
                    printCentered("MINIMAL DEMO") at 1
                    print("USE D-PAD TO MOVE") at (1 to 3)
                }

                every.frame {
                    // === D-Pad Movement ===
                    // Move right
                    whenever(dpad.right.held) { player.x += speed }

                    // Move left
                    whenever(dpad.left.held) { player.x -= speed }

                    // Move down
                    whenever(dpad.down.held) { player.y += speed }

                    // Move up
                    whenever(dpad.up.held) { player.y -= speed }

                    // === Boundary Checking ===
                    // Keep player within screen bounds (160x144 pixels)
                    // Account for sprite size (8x8)

                    // Left boundary (x >= 0)
                    whenever(player.x isBelow 0) { player.x set 0 }

                    // Right boundary (x <= 152, since sprite is 8px wide)
                    whenever(player.x isAbove 152) { player.x set 152 }

                    // Top boundary (y >= 16, leaving room for text)
                    whenever(player.y isBelow 40) { player.y set 40 }

                    // Bottom boundary (y <= 136, since sprite is 8px tall)
                    whenever(player.y isAbove 136) { player.y set 136 }
                }
            }
    }

// =============================================================================
// Build & Run
// =============================================================================

fun main() {
    println(
        """
        +---------------------------------------+
        |      gbkt - Minimal Game Example      |
        +---------------------------------------+
        | Features:                             |
        |   - Single 8x8 sprite                 |
        |   - D-pad movement                    |
        |   - Boundary checking                 |
        +---------------------------------------+
    """
            .trimIndent()
    )
    println()

    println("Compiling: ${minimalGame.name}")

    val code = minimalGame.compile()

    println()
    println("Generated ${code.lines().size} lines of C")
    println()

    println("=".repeat(50))
    println(code)
    println("=".repeat(50))
}
