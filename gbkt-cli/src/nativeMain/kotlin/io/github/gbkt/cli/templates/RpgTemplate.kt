/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.cli.templates

/** RPG template - top-down movement with basic tilemap. */
object RpgTemplate : Template {
    override val name = "rpg"
    override val description = "Top-down movement with basic tilemap"

    override fun buildGradle(projectName: String): String = commonBuildGradle(projectName)

    override fun settingsGradle(projectName: String): String = commonSettingsGradle(projectName)

    override fun gameKt(projectName: String): String =
        """
        |import io.github.gbkt.core.assets.SpriteAsset
        |import io.github.gbkt.core.dsl.*
        |
        |/**
        | * $projectName - A top-down RPG built with gbkt
        | *
        | * Features:
        | * - 4-directional player movement
        | * - Tilemap with collision layer
        | * - NPC interaction with dialogs
        | * - Scene transitions
        | */
        |fun main() = gameBoy("$projectName") {
        |    val moveSpeed = 1
        |    val tileSize = 8
        |
        |    // Player state
        |    var playerDir by u8Var()  // 0=down, 1=up, 2=left, 3=right
        |
        |    // Player sprite (16x16 for RPG character with animation frames)
        |    val player = sprite(SpriteAsset("player.png")) {
        |        size = 16 x 16
        |        position(72, 64)  // Near center
        |    }
        |
        |    // Load world tilemap (create with Tiled editor)
        |    // Export as JSON, place at assets/world.json
        |    // val world = tilemap("world.json") {
        |    //     collisionLayer = "Collision"
        |    // }
        |
        |    // Camera with smooth scrolling
        |    val camera = camera {
        |        smoothing = 0.15f
        |    }
        |    camera.follow(player)
        |
        |    // Example NPC (uncomment when you have npc.png)
        |    // val npc = sprite(SpriteAsset("npc.png")) {
        |    //     size = 16 x 16
        |    //     position(100, 80)
        |    // }
        |
        |    // Town scene
        |    scene("town") {
        |        every.frame {
        |            // 4-directional movement
        |            whenever(button.down.isPressed) {
        |                player.y += moveSpeed
        |                playerDir set 0
        |            }
        |            whenever(button.up.isPressed) {
        |                player.y -= moveSpeed
        |                playerDir set 1
        |            }
        |            whenever(button.left.isPressed) {
        |                player.x -= moveSpeed
        |                playerDir set 2
        |            }
        |            whenever(button.right.isPressed) {
        |                player.x += moveSpeed
        |                playerDir set 3
        |            }
        |
        |            // Tilemap collision (uncomment when tilemap is loaded)
        |            // val tileX = player.x / tileSize
        |            // val tileY = player.y / tileSize
        |            // whenever(world.isBlocked(tileX, tileY)) {
        |            //     // Undo movement on collision
        |            //     whenever(playerDir isEqual 0) { player.y -= moveSpeed }
        |            //     whenever(playerDir isEqual 1) { player.y += moveSpeed }
        |            //     whenever(playerDir isEqual 2) { player.x += moveSpeed }
        |            //     whenever(playerDir isEqual 3) { player.x -= moveSpeed }
        |            // }
        |
        |            // Screen bounds (remove when using tilemap)
        |            whenever(player.x isBelow 0) { player.x set 0 }
        |            whenever(player.x isAbove 144) { player.x set 144 }
        |            whenever(player.y isBelow 0) { player.y set 0 }
        |            whenever(player.y isAbove 128) { player.y set 128 }
        |
        |            // NPC interaction (uncomment when NPC is added)
        |            // whenever(button.a.justPressed) {
        |            //     val nearNpc = (player.x - npc.x).abs() < 20 and
        |            //                   (player.y - npc.y).abs() < 20
        |            //     whenever(nearNpc) {
        |            //         dialog("Villager") {
        |            //             line("Welcome to our town!")
        |            //             line("The cave to the north")
        |            //             line("holds many secrets...")
        |            //         }
        |            //     }
        |            // }
        |        }
        |    }
        |
        |    // Title screen
        |    scene("title") {
        |        // Display title text
        |        text("$projectName", 5, 6)
        |        text("Press START", 4, 10)
        |
        |        every.frame {
        |            whenever(button.start.justPressed) {
        |                camera.fadeOut(30.frames) {
        |                    scene("town")
        |                }
        |            }
        |        }
        |    }
        |
        |    start("title")
        |}
        """
            .trimMargin()
}
