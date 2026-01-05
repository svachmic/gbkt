/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package sample

import io.github.gbkt.core.AnimationRef
import io.github.gbkt.core.Cartridge
import io.github.gbkt.core.SceneRef
import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.builder.camera
import io.github.gbkt.core.builder.palette
import io.github.gbkt.core.buttons
import io.github.gbkt.core.dpad
import io.github.gbkt.core.entity.entity
import io.github.gbkt.core.entity.pool
import io.github.gbkt.core.gbGame
import io.github.gbkt.core.graphics.frames
import io.github.gbkt.core.ir.GBCMode
import io.github.gbkt.core.ir.u16Var
import io.github.gbkt.core.ir.u8Var
import io.github.gbkt.core.ir.x
import io.github.gbkt.core.print
import io.github.gbkt.core.printCentered
import io.github.gbkt.core.screen
import io.github.gbkt.core.whenever

/**
 * Adventure Game: Complex Game Example
 *
 * A comprehensive example demonstrating advanced gbkt features:
 * - Tilemap level with collision
 * - Multiple scenes (title, gameplay, gameover)
 * - Entity pool for enemies
 * - Camera following player
 * - GBC palettes and animations
 * - Physics and collision detection
 *
 * This is a complete mini-game showcasing production patterns.
 */
val adventureGame =
    gbGame("AdventureDemo") {

        // === Configuration ===
        config {
            cartridge = Cartridge.ROM_ONLY
            romBanks = 2
            gbcSupport = true
            gbcMode = GBCMode.COMPATIBLE
        }

        // === GBC Color Palettes ===
        val playerPalette = palette("player") { colors(0xFFFFFF, 0x88FF88, 0x448844, 0x000000) }

        val enemyPalette = palette("enemy") { colors(0xFFFFFF, 0xFF8888, 0x884444, 0x000000) }

        val coinPalette = palette("coin") { colors(0xFFFFFF, 0xFFDD44, 0xDD8800, 0x000000) }

        // === Game Variables ===
        var score by u16Var(0)
        var lives by u8Var(3)
        var coinsCollected by u8Var(0)
        var coinsRequired by u8Var(5) // Coins needed to win

        // Physics
        var playerVelY by u8Var(0)
        var gravity by u8Var(1)
        var isGrounded by u8Var(1)
        var jumpPower by u8Var(6)

        // === Tags for Entity Grouping ===
        val enemyTag = tag("enemy")
        val coinTag = tag("coin")

        // === Camera System ===
        val camera = camera {
            smoothing = 0.15f
            deadzone(16 x 8)
        }

        // === Tilemap Level ===
        // NOTE: Requires level.json from Tiled map editor
        // val level = tilemap("level.json") {
        //     collisionLayer = "Collision"
        // }

        // === Animation References ===
        lateinit var idleAnim: AnimationRef
        lateinit var runAnim: AnimationRef
        lateinit var jumpAnim: AnimationRef
        lateinit var spinAnim: AnimationRef

        // === Player Entity ===
        val player by entity {
            position(40, 100)
            velocity(0, 0)

            sprite(SpriteAsset("player.png")) {
                size = 8 x 16
                palette = playerPalette
                hitbox(1, 0, 6, 16)

                regions {
                    "idle" at 0 size 2
                    "run" at 2 size 4
                    "jump" at 6 size 1
                }

                animations {
                    idleAnim = "idle" plays (region("idle") every 30.frames)
                    runAnim = "run" plays (region("run") every 6.frames)
                    jumpAnim = "jump" plays regionFrame("jump", 0)
                }
            }
        }

        // === Coin Entities (Static Collectibles) ===
        val coin1 by entity {
            position(60, 80)
            tag(coinTag)

            sprite(SpriteAsset("coin.png")) {
                size = 8 x 8
                palette = coinPalette
                hitbox(0, 0, 8, 8)

                regions { "spin" at 0 size 4 }
                animations { spinAnim = "spin" plays (region("spin") every 8.frames) }
            }
        }

        val coin2 by entity {
            position(100, 60)
            tag(coinTag)

            sprite(SpriteAsset("coin.png")) {
                size = 8 x 8
                palette = coinPalette
                hitbox(0, 0, 8, 8)

                regions { "spin" at 0 size 4 }
                animations { "spin" plays (region("spin") every 8.frames) }
            }
        }

        val coin3 by entity {
            position(140, 80)
            tag(coinTag)

            sprite(SpriteAsset("coin.png")) {
                size = 8 x 8
                palette = coinPalette
                hitbox(0, 0, 8, 8)

                regions { "spin" at 0 size 4 }
                animations { "spin" plays (region("spin") every 8.frames) }
            }
        }

        // === Enemy Pool ===
        // Pool of 4 enemies that patrol back and forth
        val enemyPool =
            pool("enemy", size = 4) {
                position(0, 0)
                velocity(-1, 0)

                sprite(SpriteAsset("enemy.png")) {
                    size = 8 x 8
                    palette = enemyPalette
                    hitbox(0, 0, 8, 8)
                }

                // Per-entity state
                state {
                    val minX by u8Var(0) // Left patrol bound
                    val maxX by u8Var(160) // Right patrol bound
                }

                onFrame {
                    // Apply velocity
                    x += velX

                    // Bounce at patrol bounds
                    whenever(x isBelow this["minX"]) { velX set 1 }
                    whenever(x isAbove this["maxX"]) { velX set -1 }
                }

                // Despawn if off-screen (cleanup)
                despawnWhen { y isAbove 160 }

                onDespawn { hide() }
            }

        // === Scene Definitions ===
        lateinit var titleScene: SceneRef
        lateinit var gameplayScene: SceneRef
        lateinit var gameoverScene: SceneRef
        lateinit var winScene: SceneRef

        // === Title Scene ===
        titleScene =
            scene("title") {
                enter {
                    screen.clear()
                    screen.hideSprites()

                    printCentered("ADVENTURE") at 4
                    printCentered("DEMO") at 5

                    print("Collect ", coinsRequired, " coins") at (3 to 8)
                    print("to win!") at (6 to 9)

                    print("Avoid enemies!") at (3 to 11)

                    printCentered("PRESS START") at 15
                }

                every.frame {
                    whenever(buttons.start.pressed) {
                        camera.fadeOut(20.frames) { scene(gameplayScene) }
                    }
                }
            }

        // === Gameplay Scene ===
        gameplayScene =
            scene("gameplay") {
                enter {
                    screen.clear()
                    screen.showSprites()

                    // Reset game state
                    score set 0
                    lives set 3
                    coinsCollected set 0
                    playerVelY set 0
                    isGrounded set 1

                    // Reset player position
                    player.x set 40
                    player.y set 100
                    player.play(idleAnim)

                    // Reset coins (show all)
                    coin1.x set 60
                    coin1.y set 80
                    coin1.show()
                    coin1.play(spinAnim)

                    coin2.x set 100
                    coin2.y set 60
                    coin2.show()
                    coin2.play(spinAnim)

                    coin3.x set 140
                    coin3.y set 80
                    coin3.show()
                    coin3.play(spinAnim)

                    // Spawn enemies
                    enemyPool.despawnAll()

                    // Enemy 1: Patrols bottom left
                    enemyPool.spawn {
                        x set 20
                        y set 110
                        velX set 1
                        this["minX"] set 20
                        this["maxX"] set 70
                    }

                    // Enemy 2: Patrols bottom right
                    enemyPool.spawn {
                        x set 100
                        y set 110
                        velX set -1
                        this["minX"] set 90
                        this["maxX"] set 140
                    }

                    // Enemy 3: Patrols middle
                    enemyPool.spawn {
                        x set 60
                        y set 70
                        velX set 1
                        this["minX"] set 50
                        this["maxX"] set 110
                    }

                    // Setup camera
                    camera.follow(player)
                    camera.fadeIn(15.frames)

                    // HUD
                    print("COINS:") at (0 to 0)
                    print("LIVES:") at (12 to 0)
                }

                every.frame {
                    // Update systems
                    enemyPool.update()
                    camera.update()

                    // === Player Input ===

                    // Horizontal movement
                    whenever(dpad.right) {
                        player.x += 2
                        player.play(runAnim)
                    }
                    whenever(dpad.left) {
                        player.x -= 2
                        player.play(runAnim)
                    }

                    // Idle when not moving
                    whenever(dpad.none) {
                        whenever(isGrounded isEqualTo 1) { player.play(idleAnim) }
                    }

                    // Jump
                    whenever(buttons.a.pressed) {
                        whenever(isGrounded isEqualTo 1) {
                            playerVelY set jumpPower
                            isGrounded set 0
                            player.play(jumpAnim)
                        }
                    }

                    // === Physics ===

                    // Apply gravity
                    whenever(isGrounded isEqualTo 0) {
                        whenever(playerVelY isAbove 0) { playerVelY -= gravity }
                    }

                    // Apply vertical velocity
                    whenever(playerVelY isAbove 0) { player.y -= 1 }

                    // Ground collision (simple floor at y=110)
                    whenever(player.y isAtLeast 110) {
                        player.y set 110
                        playerVelY set 0
                        isGrounded set 1
                    }

                    // Screen boundaries
                    whenever(player.x isBelow 4) { player.x set 4 }
                    whenever(player.x isAbove 148) { player.x set 148 }
                    whenever(player.y isBelow 16) { player.y set 16 }

                    // === Coin Collection ===

                    whenever(player collidesWith coin1) {
                        coin1.hide()
                        coin1.y set 200 // Move off-screen
                        coinsCollected += 1
                        score += 100
                    }

                    whenever(player collidesWith coin2) {
                        coin2.hide()
                        coin2.y set 200
                        coinsCollected += 1
                        score += 100
                    }

                    whenever(player collidesWith coin3) {
                        coin3.hide()
                        coin3.y set 200
                        coinsCollected += 1
                        score += 100
                    }

                    // === Enemy Collision ===
                    enemyPool.forEachActive {
                        whenever(collidesWith(player)) {
                            lives -= 1
                            camera.shake(4, 10.frames)

                            // Knockback player
                            player.x set 40
                            player.y set 100
                            playerVelY set 0
                            isGrounded set 1

                            // Check game over
                            whenever(lives isEqualTo 0) {
                                camera.fadeOut(20.frames) { scene(gameoverScene) }
                            }
                        }
                    }

                    // === Win Condition ===
                    whenever(coinsCollected isAtLeast coinsRequired) {
                        camera.fadeOut(30.frames) { scene(winScene) }
                    }

                    // === HUD Updates ===
                    print(coinsCollected, "/", coinsRequired) at (7 to 0)
                    print(lives) at (18 to 0)

                    // === Pause / Exit ===
                    whenever(buttons.select.pressed) { scene(titleScene) }
                }

                exit { enemyPool.despawnAll() }
            }

        // === Game Over Scene ===
        gameoverScene =
            scene("gameover") {
                enter {
                    screen.clear()
                    screen.hideSprites()

                    printCentered("GAME OVER") at 5
                    print("SCORE: ", score) at (5 to 8)
                    print("COINS: ", coinsCollected, "/", coinsRequired) at (5 to 10)

                    printCentered("PRESS START") at 14
                }

                every.frame { whenever(buttons.start.pressed) { scene(titleScene) } }
            }

        // === Win Scene ===
        winScene =
            scene("win") {
                enter {
                    screen.clear()
                    screen.hideSprites()

                    printCentered("YOU WIN!") at 5
                    print("FINAL SCORE: ", score) at (3 to 8)
                    print("All coins collected!") at (2 to 10)

                    printCentered("PRESS START") at 14
                }

                every.frame { whenever(buttons.start.pressed) { scene(titleScene) } }
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
        |    gbkt - Adventure Game Demo         |
        +---------------------------------------+
        | Features:                             |
        |   - Multiple scenes                   |
        |   - Entity pool for enemies           |
        |   - Camera following                  |
        |   - GBC color palettes                |
        |   - Physics and collision             |
        |   - Win/lose conditions               |
        +---------------------------------------+
        """
            .trimIndent()
    )
    println()

    println("Compiling: ${adventureGame.name}")

    val code = adventureGame.compile()

    println()
    println("Generated ${code.lines().size} lines of C")
    println()

    println("=".repeat(50))
    println(code)
    println("=".repeat(50))
}
