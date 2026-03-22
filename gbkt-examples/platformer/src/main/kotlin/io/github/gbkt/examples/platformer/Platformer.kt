/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.platformer

import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.MovementStyle
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SoundPreset
import io.github.gbkt.genre.platformer.domain.PlatformType
import io.github.gbkt.genre.platformer.dsl.goalZone
import io.github.gbkt.genre.platformer.dsl.platform
import io.github.gbkt.genre.platformer.dsl.platformerCamera
import io.github.gbkt.genre.platformer.dsl.platformerPhysics

/**
 * Platformer game defined using the V2 DSL and gbkt-genre-platformer.
 *
 * Demonstrates:
 * - platformerPhysics() — gravity, variable-height jump, coyote time, jump buffer
 * - platformerCamera() — smooth-follow with horizontal dead zone
 * - platform() — solid ground and one-way mid-air platforms
 * - goalZone() — level-completion trigger zone
 * - Actor with MovementStyle.PHYSICS for gravity/velocity-based movement
 * - Type-safe input: dpad.left.held, dpad.right.held, buttons.a.pressed
 * - Scene references for navigation (SceneRef instead of magic strings)
 *
 * Level layout (DMG 160x144):
 * - Ground: y=120 full-width (solid)
 * - Mid platform: x=40 y=88 width=48 (one-way)
 * - High platform: x=96 y=56 width=48 (one-way)
 * - Goal zone: x=112 y=40 (on top of high platform)
 *
 * Navigation cycle: title → gameplay → win → title. titleRef is forward-declared to break the cycle
 * (title defined last).
 */
@Suppress("LongMethod")
val platformer =
    game("Platformer") {
        config {
            cartridge = "ROM_ONLY"
            romBanks = 2
        }

        // Forward-declare titleRef for circular navigation (title defined after win)
        val titleRef = sceneRef("title")

        // -------------------------------------------------------------------------
        // Variables
        // -------------------------------------------------------------------------

        var lives by u8Var(3)

        // -------------------------------------------------------------------------
        // Sound effects
        // -------------------------------------------------------------------------

        val jumpSfx by soundEffect { preset(SoundPreset.HIT) }
        val landSfx by soundEffect { preset(SoundPreset.BUMP) }
        val winSfx by soundEffect { preset(SoundPreset.WIN) }

        // -------------------------------------------------------------------------
        // Platformer systems — physics, camera, platforms, goal zone
        // -------------------------------------------------------------------------

        platformerPhysics {
            gravity(2)
            jumpForce(8)
            terminalVelocity(12)
            coyoteTime(6)
            jumpBuffer(8)
        }

        platformerCamera {
            smoothFollow()
            horizontal()
            deadZone(x = 16, y = 8)
        }

        // Ground — full-width solid floor
        platform("ground") { type(PlatformType.SOLID) }

        // Mid-air one-way platforms (jump through from below)
        platform("mid_platform") { type(PlatformType.ONE_WAY) }

        platform("high_platform") { type(PlatformType.ONE_WAY) }

        // Goal zone — reaching this area triggers level completion
        goalZone("exit") {
            position(112, 40)
            size(16, 16)
        }

        // -------------------------------------------------------------------------
        // Actors — player only (minimal platformer)
        // -------------------------------------------------------------------------

        val player by actor {
            position(20, 104)
            sprite(asset("sprites/player.png")) {
                size(8, 16)
                hitbox(0, 0, 8, 16)
            }
            movement {
                style(MovementStyle.PHYSICS)
                speed(2)
            }
        }

        // -------------------------------------------------------------------------
        // Win scene — defined first (no outward SceneRef deps)
        // -------------------------------------------------------------------------

        val winScene =
            scene("win") {
                enter {
                    hideSprites()
                    clear()
                    playSound(winSfx)
                    print("YOU WIN!", position = PositionDef(6, 6))
                    print("PRESS START", position = PositionDef(5, 10))
                }
                frame { whenever(buttons.start.pressed) { navigate(titleRef) } }
            }

        // -------------------------------------------------------------------------
        // Gameplay scene — uses winScene ref (defined above)
        // -------------------------------------------------------------------------

        val gameplayScene =
            scene("gameplay") {
                enter {
                    showSprites()
                    clear()
                    player.moveTo(20, 104)
                    lives set 3
                }
                frame {
                    // Horizontal movement — d-pad left/right
                    whenever(dpad.left.held) { moveBy(player, -2, 0) }
                    whenever(dpad.right.held) { moveBy(player, 2, 0) }

                    // Jump — A button (physics system handles gravity and landing)
                    whenever(buttons.a.pressed) { playSound(jumpSfx) }

                    // Fall detection — player fell below ground level
                    whenever(player.y isAbove 136) {
                        lives -= 1
                        playSound(landSfx)
                        player.moveTo(20, 104)
                        whenever(lives isEqualTo 0) { navigate(titleRef) }
                    }

                    // Goal zone reached — player reaches top-left of high platform
                    whenever(player.x isAtLeast 112) {
                        whenever(player.x isBelow 128) {
                            whenever(player.y isAtLeast 24) {
                                whenever(player.y isBelow 56) { navigate(winScene) }
                            }
                        }
                    }
                }
            }

        // -------------------------------------------------------------------------
        // Title scene — uses gameplayScene ref (defined above)
        // -------------------------------------------------------------------------

        val titleScene =
            scene("title") {
                enter {
                    hideSprites()
                    clear()
                    print("PLATFORMER", position = PositionDef(5, 6))
                    print("PRESS START", position = PositionDef(5, 10))
                    print("LIVES: 3", position = PositionDef(6, 13))
                }
                frame { whenever(buttons.start.pressed) { navigate(gameplayScene) } }
            }

        start = titleScene.id
    }
