/*
 * Copyright 2026 Michal Svacha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.gbkt.intellij.project.templates

/**
 * Platformer template - a side-scrolling action game starter.
 *
 * Creates a game with:
 * - Physics-based movement
 * - Jumping and gravity
 * - Tile-based collision
 * - Scrolling camera
 */
object PlatformerTemplate : GameTemplate {
    override val displayName: String = "Platformer (Action)"
    override val description: String = "Side-scrolling platformer with physics and jumping"

    override fun generateMainSource(gameName: String, packageName: String): String =
        generateHeader(gameName, packageName) + generateGameCode(gameName)

    private fun generateHeader(gameName: String, packageName: String): String =
        """
        |/*
        | * $gameName - Created with gbkt
        | */
        |package $packageName
        |
        |import io.github.gbkt.core.*
        |import io.github.gbkt.core.dsl.*
        |import io.github.gbkt.core.entity.*
        |import io.github.gbkt.core.graphics.*
        |import io.github.gbkt.core.scene.*
        |
        """
            .trimMargin()

    private fun generateGameCode(gameName: String): String =
        """
        |fun main() {
        |    val game = gbGame("$gameName") {
        |        // Physics configuration
        |        physics {
        |            gravity(0, 1)  // Downward gravity
        |            friction(0.9f)
        |        }
        |
        |        // Player entity with physics
        |        val player by entity {
        |            position(40, 100)
        |            sprite(SpriteAsset("player.png")) {
        |                size = 8 x 16
        |                hitbox(1, 0, 6, 16)
        |            }
        |            physics {
        |                velocityMax(3, 6)
        |                gravity(true)
        |            }
        |        }
        |
        |        // Ground detection variable
        |        var onGround by u8Var(0)
        |
        |        // Level tilemap
        |        val level by tilemap("level") {
        |            tileset("tiles.png")
        |            size(64, 32)
        |            collision(layer = "collision")
        |        }
        |
        |        // Camera that follows player
        |        val camera = camera {
        |            smoothing = 0.1f
        |            bounds(0, 0, 512, 256)
        |        }
        |
        |        // Main gameplay scene
        |        val gameplay = scene("gameplay") {
        |            enter {
        |                screen.showSprites()
        |                screen.showBackground()
        |                camera.follow(player)
        |            }
        |
        |            every.frame {
        |                // Horizontal movement
        |                runIf(dpad.right) { player.vx += 1 }
        |                runIf(dpad.left) { player.vx -= 1 }
        |
        |                // Jump when on ground
        |                runIf(buttons.a.pressed and (onGround isAbove 0)) {
        |                    player.vy set -5
        |                    onGround set 0
        |                }
        |
        |                // Check ground collision
        |                runIf(player collidesWithTilemap level) { onGround set 1 }
        |
        |                // Update camera
        |                camera.update()
        |            }
        |        }
        |
        |        start = gameplay
        |    }
        |
        |    game.build()
        |}
        """
            .trimMargin()

    override fun getSampleAssets(): Map<String, AssetDescription> =
        mapOf(
            "sprites/player.png" to
                AssetDescription.Placeholder("8x16 player sprite sheet (idle, run, jump frames)"),
            "tilemaps/level.tmx" to
                AssetDescription.Tilemap(64, 32, "Level tilemap with collision layer"),
            "tilemaps/tiles.png" to
                AssetDescription.Placeholder("8x8 tileset for platforms, ground, decorations"),
        )
}
