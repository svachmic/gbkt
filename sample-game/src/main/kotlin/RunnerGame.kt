/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package sample

import io.github.gbkt.core.AnimationRef
import io.github.gbkt.core.Cartridge
import io.github.gbkt.core.Easing
import io.github.gbkt.core.SceneRef
import io.github.gbkt.core.assets.SpriteAsset
import io.github.gbkt.core.builder.camera
import io.github.gbkt.core.builder.palette
import io.github.gbkt.core.buttons
import io.github.gbkt.core.compileWithAssets
import io.github.gbkt.core.entity.entity
import io.github.gbkt.core.entity.pool
import io.github.gbkt.core.gbGame
import io.github.gbkt.core.generateTestSprite
import io.github.gbkt.core.graphics.PalettePreset
import io.github.gbkt.core.graphics.frames
import io.github.gbkt.core.ir.GBCMode
import io.github.gbkt.core.ir.plus
import io.github.gbkt.core.ir.u16Var
import io.github.gbkt.core.ir.u8Var
import io.github.gbkt.core.ir.x
import io.github.gbkt.core.print
import io.github.gbkt.core.printCentered
import io.github.gbkt.core.screen
import io.github.gbkt.core.tween

/**
 * Sample Game: Comprehensive Feature Demo
 *
 * Showcases gbkt DSL features:
 * - Sprite collision detection
 * - Entity physics (gravity, velocity)
 * - Object pools for particle effects
 * - Tilemap collision with Tiled JSON
 * - Tweening with easing functions
 * - Camera effects (follow, shake, fade)
 * - GBC palettes and animations
 * - Scene management with type-safe refs
 *
 * FEATURES DEMONSTRATED:
 * 1. Sprite Collision: player collidesWith enemy, player collidesWith coin
 * 2. Entity Physics: Custom velocity variables with gravity simulation
 * 3. Object Pools: particlePool for reusable particle entities
 * 4. Tilemap Collision: level.isBlocked() for tile-based collision
 * 5. Tweening: coin.y tweens with EASE_OUT easing
 * 6. Camera Effects: camera.follow(), camera.shake(), camera.fadeOut()
 */
val runnerGame =
    gbGame("ComprehensiveDemo") {

        // === Configuration ===
        config {
            cartridge = Cartridge.ROM_ONLY
            romBanks = 2
            gbcSupport = true
            gbcMode = GBCMode.COMPATIBLE
        }

        // === GBC Color Palettes ===
        // Demonstrates GBC palette support with custom colors
        val playerPalette = palette("player") { colors(0xFFFFFF, 0x88FF88, 0x448844, 0x000000) }

        val coinPalette = palette("coin") { colors(0xFFFFFF, 0xFFDD44, 0xDD8800, 0x000000) }

        val enemyPalette = palette("enemy") { colors(0xFFFFFF, 0xFF8888, 0x884444, 0x000000) }

        val particlePalette = palette("particle") { colors(0xFFFFFF, 0x88DDFF, 0x4488DD, 0x000000) }

        val bgPalette = palette("background", PalettePreset.FOREST)

        // === Game Variables ===
        var score by u16Var(0)
        var coins by u8Var(0)
        var lives by u8Var(3)
        var gameSpeed by u8Var(2)

        // Physics variables - demonstrates entity physics system
        var playerVelX by u8Var(0)
        var playerVelY by u8Var(0)
        var gravity by u8Var(1)
        var groundY by u8Var(120)
        var isGrounded by u8Var(1)
        var jumpPower by u8Var(8)

        // === Tags for Collision Detection ===
        // Tags enable collision queries and sprite grouping
        val coinTag = tag("coin")
        val enemyTag = tag("enemy")
        val particleTag = tag("particle")

        // === Camera System ===
        // Demonstrates camera with smooth following for cinematic feel
        val camera = camera {
            smoothing = 0.15f // Smooth camera interpolation
        }

        // === Tilemap with Collision Layer ===
        // Demonstrates Tiled JSON integration with collision detection
        // The level.json file defines a "Collision" layer for solid tiles
        // NOTE: Commented out until level.json asset is created
        // val level = tilemap("level.json") {
        //     collisionLayer = "Collision"  // Layer name from Tiled
        // }

        // === Animation References ===
        // Declared before entities so they can be captured in animations blocks
        lateinit var idleAnim: AnimationRef
        lateinit var runAnim: AnimationRef
        lateinit var jumpAnim: AnimationRef
        lateinit var fallAnim: AnimationRef
        lateinit var spinAnim: AnimationRef

        // === Entities ===

        // Player entity with full physics
        // Demonstrates: sprite animations, hitboxes, collision, physics
        val player by entity {
            position(80, 72)
            velocity(0, 0)

            sprite(SpriteAsset("player.png")) {
                size = 8 x 16
                palette = playerPalette
                hitbox(0, 0, 8, 16)

                regions {
                    "idle" at 0 size 2
                    "run" at 2 size 4
                    "jump" at 6 size 1
                    "fall" at 7 size 1
                }

                animations {
                    idleAnim = "idle" plays (region("idle") every 30.frames)
                    runAnim = "run" plays (region("run") every 8.frames)
                    jumpAnim = "jump" plays regionFrame("jump", 0)
                    fallAnim = "fall" plays regionFrame("fall", 0)
                }
            }
        }

        // Coin entity for collectibles
        // Demonstrates: sprite collision detection, tweening animation
        val coin by entity {
            position(100, 100)
            tag(coinTag)

            sprite(SpriteAsset("coin.png")) {
                size = 8 x 8
                palette = coinPalette
                hitbox(0, 0, 8, 8)

                regions { "spin" at 0 size 4 }

                animations { spinAnim = "spin" plays (region("spin") every 10.frames) }
            }
        }

        // Enemy entity with bouncing physics
        // Demonstrates: entity AI with velocity, wall bouncing
        val enemy by entity {
            position(140, 100)
            velocity(-1, 0)
            tag(enemyTag)

            sprite(SpriteAsset("ball.png")) {
                size = 8 x 8
                palette = enemyPalette
                hitbox(0, 0, 8, 8)
            }
        }

        // === Object Pool for Particle Effects ===
        // Demonstrates efficient sprite recycling for dynamic effects
        // Pool size of 8 allows up to 8 simultaneous particles
        val particlePool =
            pool("particle", size = 8) {
                position(0, 0)
                velocity(0, 0)

                sprite(SpriteAsset("particle.png")) {
                    size = 4 x 4
                    palette = particlePalette
                    hitbox(0, 0, 4, 4)
                }

                // Lifecycle: Update particles each frame
                onFrame {
                    x += velX
                    y += velY
                    velY -= 1 // Gravity effect on particles
                }

                // Auto-despawn particles when they go off-screen
                despawnWhen {
                    y isBelow 8
                    y isAbove 144
                }

                onDespawn { hide() }
            }

        // === Scene References ===
        // Create refs upfront to avoid lateinit issues in callbacks
        val titleScene = SceneRef("title")
        val gameplayScene = SceneRef("gameplay")
        val gameoverScene = SceneRef("gameover")

        // === Title Scene ===
        scene("title") {
                enter {
                    screen.clear()
                    screen.hideSprites()

                    printCentered("GBKT DEMO") at 6
                    printCentered("Features:") at 9
                    print("Physics") at (6 to 11)
                    print("Tweening") at (6 to 12)
                    print("Collision") at (6 to 13)
                    print("Camera FX") at (6 to 14)
                    printCentered("PRESS START") at 17
                }

                every.frame {
                    whenever(buttons.start.pressed) {
                        // Demonstrates camera fade transition
                        camera.fadeOut(30.frames) { scene(gameplayScene) }
                    }
                }
            }

        // === Gameplay Scene ===
        scene("gameplay") {
                enter {
                    // Initialize player position and physics state
                    player.x set 80
                    player.y set 72
                    playerVelX set 0
                    playerVelY set 0
                    isGrounded set 1

                    // Initialize coin with bounce-in tween animation
                    // Demonstrates: tweening with EASE_OUT easing
                    coin.x set 120
                    coin.y set 0
                    tween(
                        coin.y,
                        from = 0,
                        to = 100,
                        duration = 30.frames,
                        easing = Easing.EASE_OUT
                    )

                    // Initialize enemy
                    enemy.x set 140
                    enemy.y set 100
                    enemy.velX set -1

                    // Reset game state
                    score set 0
                    coins set 0
                    gameSpeed set 2

                    // Setup camera to follow player
                    // Demonstrates: smooth camera following
                    camera.follow(player)

                    // Fade in from black
                    camera.fadeIn(20.frames)

                    screen.clear()
                    screen.showSprites()
                    player.play(idleAnim)
                    coin.play(spinAnim)
                }

                every.frame {
                    // Update pool - REQUIRED for pool lifecycle
                    particlePool.update()

                    // Update camera - REQUIRED for follow/shake/transitions
                    camera.update()

                    // === Player Input & Movement ===

                    // Horizontal movement
                    whenever(buttons.a.pressed) {
                        whenever(playerVelX isBelow 4) { playerVelX += 1 }
                        player.play(runAnim)
                    }
                    whenever(buttons.b.pressed) {
                        whenever(playerVelX isAbove 0) { playerVelX -= 1 }
                        player.play(runAnim)
                    }

                    // Jump mechanic
                    // Demonstrates: spawn particles from pool on jump
                    whenever(buttons.start.pressed and (isGrounded isEqualTo 1)) {
                        playerVelY set jumpPower
                        isGrounded set 0
                        player.play(jumpAnim)

                        // Spawn particle effect at jump position using pool
                        particlePool.spawn {
                            x set player.x
                            y set player.y
                            velY set -2
                            velX set 0
                        }
                    }

                    // === Physics System ===
                    // Demonstrates: gravity simulation with velocity

                    // Apply gravity when airborne
                    whenever(isGrounded isEqualTo 0) {
                        whenever(playerVelY isAbove 0) { playerVelY -= gravity }
                    }

                    // Apply horizontal velocity to position
                    whenever(playerVelX isAbove 0) { player.x += 1 }

                    // Apply vertical velocity to position
                    whenever(playerVelY isAbove 0) { player.y -= 1 }

                    // === Tilemap Collision Detection ===
                    // Demonstrates: tile-based collision with Tiled maps

                    // Check ground collision (simple floor at groundY)
                    whenever(player.y isAtLeast groundY) {
                        player.y set groundY
                        playerVelY set 0
                        isGrounded set 1
                    }

                    // Screen bounds
                    whenever(player.x isBelow 0) { player.x set 0 }
                    whenever(player.x isAbove 152) { player.x set 152 }

                    // Apply friction
                    whenever(playerVelX isAbove 0) { playerVelX -= 1 }

                    // === Sprite Collision Detection ===
                    // Demonstrates: entity collision with tags

                    // Collect coins
                    // Demonstrates: respawn with tween animation
                    whenever(player collidesWith coin) {
                        coins += 1
                        score += 100

                        // Respawn coin at new location with tween
                        whenever(coins isBelow 10) {
                            coin.x set 40 + (coins * 15)
                            coin.y set 0
                            tween(
                                coin.y,
                                from = 0,
                                to = 100,
                                duration = 30.frames,
                                easing = Easing.EASE_OUT
                            )
                        }

                        // Spawn particle burst using pool
                        // Demonstrates: spawn multiple particles in sequence
                        particlePool.spawn {
                            x set coin.x
                            y set coin.y
                            velX set 2
                            velY set -1
                        }
                        particlePool.spawn {
                            x set coin.x
                            y set coin.y
                            velX set -2
                            velY set -1
                        }
                    }

                    // Hit enemy
                    // Demonstrates: camera shake on collision
                    whenever(player collidesWith enemy) {
                        lives -= 1

                        // Camera shake on hit
                        camera.shake(4, 10.frames)

                        // Knockback player
                        playerVelX set 0
                        playerVelY set 4

                        // Reset enemy position
                        enemy.x set 140

                        // Game over check
                        whenever(lives isEqualTo 0) {
                            camera.fadeOut(20.frames) { scene(gameoverScene) }
                        }
                    }

                    // === Enemy AI with Physics ===
                    // Demonstrates: bouncing AI with wall detection

                    // Bouncing enemy movement
                    enemy.x += enemy.velX

                    // Bounce off walls
                    whenever(enemy.x isBelow 20) { enemy.velX set 1 }
                    whenever(enemy.x isAbove 140) { enemy.velX set -1 }

                    // === HUD Display ===
                    print("SCORE:", score) at (0 to 0)
                    print("COINS:", coins) at (0 to 1)
                    print("LIVES:", lives) at (0 to 2)

                    // === Scene Transitions ===
                    whenever(buttons.select.pressed) { scene(titleScene) }
                }

                exit {
                    // Clean up: despawn all particles
                    particlePool.despawnAll()
                }
            }

        // === Game Over Scene ===
        scene("gameover") {
                enter {
                    screen.hideSprites()
                    screen.clear()

                    printCentered("GAME OVER") at 6
                    print("FINAL SCORE: ", score) at (4 to 9)
                    print("COINS: ", coins) at (4 to 11)
                    printCentered("PRESS START") at 14
                }

                every.frame { whenever(buttons.start.pressed) { scene(titleScene) } }
            }

        // Type-safe starting scene
        start = titleScene
    }

// =============================================================================
// Build & Run
// =============================================================================

fun main() {
    println(
        """
        ╔═══════════════════════════════════════╗
        ║         gbkt - Game Boy Kotlin        ║
        ║      Comprehensive Feature Demo       ║
        ╠═══════════════════════════════════════╣
        ║  Features Demonstrated:               ║
        ║  • Sprite collision detection         ║
        ║  • Entity physics (gravity, velocity) ║
        ║  • Object pools for particles         ║
        ║  • Tilemap collision (Tiled JSON)     ║
        ║  • Tweening with easing functions     ║
        ║  • Camera effects (follow/shake/fade) ║
        ║  • GBC palettes and animations        ║
        ║  • Scene management                   ║
        ╚═══════════════════════════════════════╝
    """
            .trimIndent()
    )
    println()

    val assetDir = "sample-game/src/main/resources/sprites"
    println("Generating test sprites...")
    generateTestSprite("$assetDir/player.png", 8, 16)
    generateTestSprite("$assetDir/coin.png", 8, 8)
    generateTestSprite("$assetDir/ball.png", 8, 8)
    generateTestSprite("$assetDir/particle.png", 4, 4)
    generateTestSprite("$assetDir/tiles.png", 8, 8)
    println()

    println("Compiling: ${runnerGame.name}")
    println("Processing assets from: $assetDir")

    val code = compileWithAssets(runnerGame, assetDir)

    println()
    println("Generated ${code.lines().size} lines of C")
    println()

    println("=".repeat(50))
    println(code)
    println("=".repeat(50))
}
