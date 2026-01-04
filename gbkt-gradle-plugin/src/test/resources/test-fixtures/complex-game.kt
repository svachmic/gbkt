/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package test

import io.github.gbkt.core.*
import io.github.gbkt.core.assets.SpriteAsset

/**
 * Complex game fixture - tests multiple features:
 * - Variables (u8, u16)
 * - Palettes (GBC)
 * - Sprites with animations
 * - Multiple scenes
 * - Collision detection
 * - Input handling
 * - Dialog system
 */
val complexGame =
    gbGame("ComplexGame") {
        // Configuration
        config {
            cartridge = Cartridge.ROM_ONLY
            romBanks = 2
            gbcSupport = true
            gbcMode = GBCMode.COMPATIBLE
        }

        // Palettes
        val playerPalette = palette("player") { colors(0xFFFFFF, 0x88FF88, 0x448844, 0x000000) }

        val enemyPalette = palette("enemy") { colors(0xFFFFFF, 0xFF8888, 0x884444, 0x000000) }

        // Variables
        var score by u16Var(0)
        var lives by u8Var(3)
        var gameSpeed by u8Var(2)

        // Dialog
        val narrator =
            dialog("narrator") {
                speaker = "System"
                textSpeed = 3
                box {
                    position(0, 12)
                    size = 20 x 4
                    border = BorderStyle.SIMPLE
                }
            }

        // Sprites
        val player =
            sprite(SpriteAsset("player.png")) {
                size = 8 x 16
                position(80, 72)
                palette = playerPalette
                hitbox(0, 0, 8, 16)

                regions {
                    "idle" at 0 size 2
                    "run" at 2 size 4
                }

                animations {
                    "idle" plays (region("idle") every 30.frames)
                    "run" plays (region("run") every 8.frames)
                }
            }

        val enemy =
            sprite(SpriteAsset("enemy.png")) {
                size = 8 x 8
                position(150, 100)
                palette = enemyPalette
                hitbox(0, 0, 8, 8)
            }

        // Scenes
        val titleScene =
            scene("title") {
                enter {
                    screen.clear()
                    printCentered("COMPLEX GAME") at 6
                    printCentered("PRESS START") at 10
                }

                every.frame { whenever(buttons.start.pressed) { scene("gameplay") } }
            }

        val gameplayScene =
            scene("gameplay") {
                enter {
                    player.x set 80
                    player.y set 72
                    enemy.x set 150
                    score set 0
                    lives set 3
                    gameSpeed set 2
                    screen.showSprites()
                    player.play("run")
                }

                every.frame {
                    // Player movement
                    whenever(buttons.dpad.left.pressed) { player.x -= gameSpeed }
                    whenever(buttons.dpad.right.pressed) { player.x += gameSpeed }
                    whenever(buttons.a.pressed) { player.y -= 5 }

                    // Enemy movement
                    enemy.x -= gameSpeed

                    // Collision
                    whenever(player collidesWith enemy) {
                        lives -= 1
                        score += 10
                        narrator.say("Hit! Lives: ", lives)
                    }

                    // Game over condition
                    whenever(lives isEqualTo 0) { scene("gameover") }

                    // Speed increase
                    whenever((score isAbove 0) and ((score rem 500) isEqualTo 0)) {
                        whenever(gameSpeed isBelow 6) { gameSpeed += 1 }
                    }
                }

                exit {
                    whenever(score isAbove 0) {
                        // Save high score logic would go here
                    }
                }
            }

        val gameoverScene =
            scene("gameover") {
                enter {
                    screen.hideSprites()
                    screen.clear()
                    printCentered("GAME OVER") at 6
                    print("SCORE: ", score) at (4 to 9)
                    printCentered("PRESS START") at 14
                }

                every.frame { whenever(buttons.start.pressed) { scene("title") } }
            }

        // Set starting scene
        start = titleScene
    }
