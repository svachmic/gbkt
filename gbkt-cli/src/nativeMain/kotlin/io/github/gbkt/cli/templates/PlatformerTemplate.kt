/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.cli.templates

/** Platformer template - player with gravity and platforms. */
object PlatformerTemplate : Template {
    override val name = "platformer"
    override val description = "Player with gravity and platforms"

    override fun buildGradle(projectName: String): String = commonBuildGradle(projectName)

    override fun settingsGradle(projectName: String): String = commonSettingsGradle(projectName)

    override fun gameKt(projectName: String): String =
        """
        |import io.github.gbkt.core.assets.SpriteAsset
        |import io.github.gbkt.core.dsl.*
        |
        |/**
        | * $projectName - A platformer game built with gbkt
        | *
        | * Features:
        | * - Player with gravity and jumping
        | * - Platform collision detection
        | * - Simple level layout
        | */
        |fun main() = gameBoy("$projectName") {
        |    // Physics constants
        |    val gravity = 1
        |    val jumpForce = -4
        |    val moveSpeed = 2
        |    val groundY = 120  // Ground level
        |
        |    // Player state
        |    var velocityY by s8Var()  // Signed for negative (upward) velocity
        |    var onGround by u8Var()
        |
        |    // Player sprite (8x16 for typical platformer character)
        |    val player = sprite(SpriteAsset("player.png")) {
        |        size = 8 x 16
        |        position(20, groundY - 16)
        |    }
        |
        |    // Load level tilemap (create assets/level.json with Tiled)
        |    // val level = tilemap("level.json") {
        |    //     collisionLayer = "Collision"
        |    // }
        |
        |    // Camera follows player
        |    val camera = camera {
        |        smoothing = 0.1f
        |    }
        |    camera.follow(player)
        |
        |    scene("gameplay") {
        |        every.frame {
        |            // Horizontal movement
        |            whenever(button.left.isPressed) { player.x -= moveSpeed }
        |            whenever(button.right.isPressed) { player.x += moveSpeed }
        |
        |            // Jump when on ground
        |            whenever(button.a.justPressed and (onGround isEqual 1)) {
        |                velocityY set jumpForce
        |                onGround set 0
        |            }
        |
        |            // Apply gravity
        |            velocityY += gravity
        |
        |            // Cap falling speed
        |            whenever(velocityY isAbove 6) { velocityY set 6 }
        |
        |            // Apply vertical velocity
        |            player.y += velocityY
        |
        |            // Simple ground collision
        |            whenever(player.y isAbove (groundY - 16)) {
        |                player.y set (groundY - 16)
        |                velocityY set 0
        |                onGround set 1
        |            }
        |
        |            // Screen bounds
        |            whenever(player.x isBelow 0) { player.x set 0 }
        |            whenever(player.x isAbove 152) { player.x set 152 }
        |        }
        |    }
        |
        |    start("gameplay")
        |}
        """
            .trimMargin()
}
