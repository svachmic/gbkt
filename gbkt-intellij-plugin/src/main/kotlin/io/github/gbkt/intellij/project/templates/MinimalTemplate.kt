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
 * Minimal template - a simple hello world game.
 *
 * Creates a basic game with:
 * - Single scene
 * - Player entity with movement
 * - Simple sprite
 */
object MinimalTemplate : GameTemplate {
    override val displayName: String = "Minimal (Hello World)"
    override val description: String = "A simple starting point with basic movement"

    override fun generateMainSource(gameName: String, packageName: String): String =
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
        |fun main() {
        |    val game = gbGame("$gameName") {
        |        // Define the player entity
        |        val player by entity {
        |            position(80, 72)  // Center of screen
        |            sprite(SpriteAsset("player.png")) {
        |                size = 8 x 8
        |            }
        |        }
        |
        |        // Main gameplay scene
        |        val gameplay = scene("gameplay") {
        |            enter {
        |                screen.showSprites()
        |            }
        |
        |            every.frame {
        |                // D-pad movement
        |                whenever(dpad.right) { player.x += 1 }
        |                whenever(dpad.left) { player.x -= 1 }
        |                whenever(dpad.up) { player.y -= 1 }
        |                whenever(dpad.down) { player.y += 1 }
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
                AssetDescription.Placeholder(
                    "8x8 player sprite with 4 colors (including transparent)"
                )
        )
}
