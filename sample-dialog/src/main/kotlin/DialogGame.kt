/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package sample

import io.github.gbkt.core.BorderStyle
import io.github.gbkt.core.Cartridge
import io.github.gbkt.core.SceneRef
import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.builder.dialog
import io.github.gbkt.core.builder.menu
import io.github.gbkt.core.buttons
import io.github.gbkt.core.dpad
import io.github.gbkt.core.entity.entity
import io.github.gbkt.core.gbGame
import io.github.gbkt.core.ir.u8Var
import io.github.gbkt.core.ir.x
import io.github.gbkt.core.print
import io.github.gbkt.core.printCentered
import io.github.gbkt.core.screen
import io.github.gbkt.core.whenever

/**
 * Dialog Game: UI System Showcase
 *
 * Demonstrates the gbkt UI systems:
 * - Dialog system with typewriter text
 * - Menu system with navigation
 * - Choice-based responses
 * - NPC interactions
 *
 * Perfect for learning RPG and adventure game patterns.
 */
val dialogGame =
    gbGame("DialogDemo") {

        // === Configuration ===
        config { cartridge = Cartridge.ROM_ONLY }

        // === Game State Variables ===
        var talkCount by u8Var(0) // How many times player talked to NPC
        var selectedOption by u8Var(0) // Last selected menu option
        var hasKey by u8Var(0) // Quest flag: player has key
        var doorUnlocked by u8Var(0) // Quest flag: door is unlocked

        // === Player Entity ===
        val player by entity {
            position(40, 72)

            sprite(SpriteAsset("player.png")) {
                size = 8 x 16
                hitbox(0, 0, 8, 16)
            }
        }

        // === NPC Entity ===
        val npc by entity {
            position(100, 72)

            sprite(SpriteAsset("npc.png")) {
                size = 8 x 16
                hitbox(0, 0, 8, 16)
            }
        }

        // === Dialog Definitions ===
        // Main NPC dialog with custom styling
        val elderDialog =
            dialog("elder") {
                speaker = "Elder"
                textSpeed = 2 // Characters per frame

                box {
                    position(0, 12) // Bottom of screen (tile coords)
                    size = 20 x 6 // Width x Height in tiles
                    border = BorderStyle.SIMPLE
                    padding = 1
                }
            }

        // Simpler dialog for quick messages
        val systemDialog =
            dialog("system") {
                textSpeed = 4 // Faster text

                box {
                    position(1, 13)
                    size = 18 x 4
                    border = BorderStyle.SIMPLE
                    padding = 1
                }
            }

        // === Menu Definitions ===
        // Main interaction menu when near NPC
        val talkMenu =
            menu("talk") {
                style {
                    position(2, 2)
                    cursor = ">"
                    border = BorderStyle.SIMPLE
                    spacing = 1
                }

                item("TALK") { talkCount += 1 }
                item("ASK FOR KEY") { selectedOption set 1 }
                item("LEAVE") { selectedOption set 2 }
            }

        // Yes/No confirmation menu
        val confirmMenu =
            menu("confirm") {
                style {
                    position(10, 6)
                    cursor = ">"
                    border = BorderStyle.SIMPLE
                    spacing = 1
                }

                item("YES") { selectedOption set 10 }
                item("NO") { selectedOption set 11 }
            }

        // === Scene Definitions ===
        lateinit var titleScene: SceneRef
        lateinit var gameScene: SceneRef
        lateinit var talkingScene: SceneRef

        // === Title Scene ===
        titleScene =
            scene("title") {
                enter {
                    screen.clear()
                    screen.hideSprites()

                    printCentered("DIALOG DEMO") at 5
                    printCentered("Features:") at 8
                    print("  - Dialog system") at (2 to 10)
                    print("  - Menu navigation") at (2 to 11)
                    print("  - Choice responses") at (2 to 12)
                    printCentered("PRESS START") at 15
                }

                every.frame { whenever(buttons.start.pressed) { scene(gameScene) } }
            }

        // === Main Game Scene ===
        gameScene =
            scene("game") {
                enter {
                    screen.clear()
                    screen.showSprites()

                    // Reset positions
                    player.x set 40
                    player.y set 72
                    npc.x set 100
                    npc.y set 72

                    // HUD
                    print("DIALOG DEMO") at (0 to 0)
                    print("A: Interact") at (0 to 1)
                }

                every.frame {
                    // === Player Movement ===
                    whenever(dpad.right) { player.x += 2 }
                    whenever(dpad.left) { player.x -= 2 }

                    // Boundaries
                    whenever(player.x isBelow 8) { player.x set 8 }
                    whenever(player.x isAbove 144) { player.x set 144 }

                    // === NPC Interaction ===
                    // Check if player is near NPC (within 20 pixels)
                    whenever(player collidesWith npc) {
                        print("Press A") at (10 to 17)

                        whenever(buttons.a.pressed) { scene(talkingScene) }
                    }

                    // === Display Quest Status ===
                    whenever(hasKey isEqualTo 1) { print("KEY") at (17 to 0) }
                }
            }

        // === Talking Scene (Dialog Active) ===
        talkingScene =
            scene("talking") {
                enter {
                    selectedOption set 0

                    // Show interaction menu
                    talkMenu.show()
                }

                every.frame {
                    // Update menu (handles input)
                    talkMenu.tick()

                    // Update dialog if active
                    elderDialog.tick()

                    // === Handle Menu Selections ===

                    // TALK option
                    whenever(talkCount isAbove 0) {
                        talkMenu.hide()

                        // Different responses based on talk count
                        whenever(talkCount isEqualTo 1) {
                            elderDialog.say("Welcome, traveler!")
                            elderDialog.say("I am the village elder.")
                        }

                        whenever(talkCount isEqualTo 2) {
                            elderDialog.say("Have you seen the locked door?")
                            elderDialog.say("I may have a key...")
                        }

                        whenever(talkCount isAbove 2) {
                            elderDialog.say("The key opens many doors.")
                        }

                        // Reset for next time
                        talkCount set 0
                    }

                    // ASK FOR KEY option
                    whenever(selectedOption isEqualTo 1) {
                        talkMenu.hide()
                        selectedOption set 0

                        whenever(hasKey isEqualTo 0) {
                            elderDialog.say("You want the ancient key?")
                            elderDialog.say("Very well, take it.")
                            hasKey set 1
                        }
                        whenever(hasKey isEqualTo 1) {
                            elderDialog.say("You already have the key!")
                        }
                    }

                    // LEAVE option
                    whenever(selectedOption isEqualTo 2) {
                        talkMenu.hide()
                        elderDialog.hide()
                        selectedOption set 0
                        scene(gameScene)
                    }

                    // === Dialog Completion ===
                    whenever(elderDialog.isComplete) {
                        // Show menu again after dialog finishes
                        whenever(buttons.a.pressed) {
                            elderDialog.hide()
                            talkMenu.show()
                        }
                    }

                    // === Exit with B button ===
                    whenever(buttons.b.pressed) {
                        talkMenu.hide()
                        elderDialog.hide()
                        scene(gameScene)
                    }
                }

                exit {
                    talkMenu.hide()
                    elderDialog.hide()
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
        |      gbkt - Dialog System Demo        |
        +---------------------------------------+
        | Features:                             |
        |   - Dialog with typewriter effect     |
        |   - Menu system with cursor           |
        |   - Choice-based responses            |
        |   - NPC interaction                   |
        +---------------------------------------+
    """
            .trimIndent()
    )
    println()

    println("Compiling: ${dialogGame.name}")

    val code = dialogGame.compile()

    println()
    println("Generated ${code.lines().size} lines of C")
    println()

    println("=".repeat(50))
    println(code)
    println("=".repeat(50))
}
