/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.pong

import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SoundPreset

/**
 * Pong game defined using the gbkt DSL.
 *
 * Demonstrates:
 * - Two actors (paddle1, paddle2) + one ball actor — using `val x by actor { }` name inference
 * - Type-safe input: `dpad.up.held`, `buttons.start.pressed`
 * - Scene references for navigation (SceneRef instead of magic strings)
 * - 4 variables: p1Score, p2Score, ballDx, ballDy
 * - 3 scenes: title, game, gameover
 * - Input-driven movement (d-pad for P1, simple AI for P2)
 * - Ball physics and scoring
 *
 * This is an acceptance test for the gbkt IR/DSL — no RPG genre package required.
 *
 * Navigation cycle: title→game→gameover→title. Scenes are defined in order:
 * 1. gameoverScene (no outward SceneRef deps during definition)
 * 2. gameScene (uses gameoverScene SceneRef — already defined)
 * 3. titleScene (uses gameScene SceneRef — already defined) gameoverScene navigates to titleScene —
 *    but titleScene is defined after gameoverScene. The forward-declared `titleRef =
 *    sceneRef("title")` breaks the cycle cleanly with a typed SceneRef.
 */
@Suppress("LongMethod")
val pong =
    game("Pong") {
        config {
            cartridge(Cartridge.ROM_ONLY)
        }

        // Forward-declare titleRef for circular navigation (title defined after gameover)
        val titleRef = sceneRef("title")

        // -------------------------------------------------------------------------
        // Variables
        // -------------------------------------------------------------------------

        var p1Score by u8Var(0)
        var p2Score by u8Var(0)
        var ballDx by i8Var(1)
        var ballDy by i8Var(1)

        // -------------------------------------------------------------------------
        // Sound effects
        // -------------------------------------------------------------------------

        val bounceSfx by soundEffect { preset(SoundPreset.HIT) }
        val scoreSfx by soundEffect { preset(SoundPreset.COIN) }
        val winSfx by soundEffect { preset(SoundPreset.WIN) }

        // -------------------------------------------------------------------------
        // Actors — name inferred from Kotlin property via ActorDelegate.provideDelegate
        // -------------------------------------------------------------------------

        val paddle1 by actor {
            position(0, 64)
            sprite(asset("sprites/paddle.png")) {
                size(4, 16)
                hitbox(0, 0, 4, 16)
            }
        }

        val paddle2 by actor {
            position(152, 64)
            sprite(asset("sprites/paddle.png")) {
                size(4, 16)
                hitbox(0, 0, 4, 16)
            }
        }

        val ball by actor {
            position(80, 72)
            sprite(asset("sprites/ball.png")) {
                size(4, 4)
                hitbox(0, 0, 4, 4)
            }
        }

        // -------------------------------------------------------------------------
        // Game-over scene — defined first (no SceneRef deps in frame block)
        // -------------------------------------------------------------------------

        val gameoverScene =
            scene("gameover") {
                enter {
                    hideSprites()
                    clear()
                    // "GAME OVER"=9 → col 5, centered
                    print("GAME OVER", position = PositionDef(6, 5))
                    print(
                        "P1:%d    P2:%d",
                        p1Score.toExpr(),
                        p2Score.toExpr(),
                        position = PositionDef(5, 8),
                    )
                    print("PRESS START", position = PositionDef(5, 13))
                }
                frame {
                    whenever(buttons.start.pressed) {
                        p1Score set 0
                        p2Score set 0
                        // Navigate back to title — uses forward-declared titleRef (SceneRef)
                        navigate(titleRef)
                    }
                }
            }

        // -------------------------------------------------------------------------
        // Game scene — uses gameoverScene ref (defined above)
        // -------------------------------------------------------------------------

        val gameScene =
            scene("game") {
                enter {
                    clear()
                    showSprites()
                    ball.moveTo(80, 72)
                    ballDx set 1
                    ballDy set 1
                    p1Score set 0
                    p2Score set 0
                    // Draw initial score header (Y=1 for padding from top edge)
                    print(
                        "P1:%d    P2:%d",
                        p1Score.toExpr(),
                        p2Score.toExpr(),
                        position = PositionDef(5, 1),
                    )
                }
                frame {
                    // P1 d-pad controls for paddle1 (clamped to screen: 16..112)
                    whenever(dpad.up.held) {
                        whenever(paddle1.y isAbove 16) { moveBy(paddle1, 0, -2) }
                    }
                    whenever(dpad.down.held) {
                        whenever(paddle1.y isBelow 112) { moveBy(paddle1, 0, 2) }
                    }

                    // Simple AI for paddle2 — track ball to paddle CENTER (y+8), speed 2px/frame
                    whenever((paddle2.y + 8) isAbove ball.y) {
                        whenever(paddle2.y isAbove 16) { moveBy(paddle2, 0, -2) }
                    }
                    whenever((paddle2.y + 8) isBelow ball.y) {
                        whenever(paddle2.y isBelow 112) { moveBy(paddle2, 0, 2) }
                    }

                    // Ball movement
                    ball.x += ballDx
                    ball.y += ballDy

                    // Top / bottom wall bounce (below score bar at y=16, above bottom at y=120)
                    whenever(ball.y isBelow 16) {
                        ballDy set 1
                        playSound(bounceSfx)
                    }
                    whenever(ball.y isAbove 120) {
                        ballDy set -1
                        playSound(bounceSfx)
                    }

                    // Left paddle collision — bounce only in paddle zone (x 2..8) with Y overlap
                    // Exception to ball.collides() pattern: Pong uses coordinate-range checks
                    // (x 2..8, x 148..156) for tighter gameplay feel than AABB hitbox overlap.
                    whenever(ball.x isBelow 8) {
                        whenever(ball.x isAtLeast 2) {
                            whenever(ball.y isAtLeast paddle1.y) {
                                whenever(ball.y isBelow (paddle1.y + 16)) {
                                    ballDx set 1
                                    playSound(bounceSfx)
                                }
                            }
                        }
                    }

                    // Right paddle collision — bounce only in paddle zone (x 148..156) with Y
                    // overlap
                    whenever(ball.x isAbove 148) {
                        whenever(ball.x isBelow 156) {
                            whenever(ball.y isAtLeast paddle2.y) {
                                whenever(ball.y isBelow (paddle2.y + 16)) {
                                    ballDx set -1
                                    playSound(bounceSfx)
                                }
                            }
                        }
                    }

                    // Scoring — ball exits left side past paddle (P2 scores)
                    whenever(ball.x isBelow 2) {
                        p2Score += 1
                        playSound(scoreSfx)
                        // Visual feedback: flash sprites off/on, update score, reset ball
                        hideSprites()
                        print(
                            "P1:%d    P2:%d",
                            p1Score.toExpr(),
                            p2Score.toExpr(),
                            position = PositionDef(5, 1),
                        )
                        print("     SCORE!     ", position = PositionDef(2, 9))
                        delay(30)
                        print("                ", position = PositionDef(2, 9))
                        ball.moveTo(80, 72)
                        ballDx set 1
                        showSprites()
                    }

                    // Scoring — ball exits right side (P1 scores)
                    whenever(ball.x isAbove 156) {
                        p1Score += 1
                        playSound(scoreSfx)
                        hideSprites()
                        print(
                            "P1:%d    P2:%d",
                            p1Score.toExpr(),
                            p2Score.toExpr(),
                            position = PositionDef(5, 1),
                        )
                        print("     SCORE!     ", position = PositionDef(2, 9))
                        delay(30)
                        print("                ", position = PositionDef(2, 9))
                        ball.moveTo(80, 72)
                        ballDx set -1
                        showSprites()
                    }

                    // Win condition — navigate to gameover via SceneRef
                    whenever(p1Score isAtLeast 5) {
                        playSound(winSfx)
                        navigate(gameoverScene)
                    }
                    whenever(p2Score isAtLeast 5) {
                        playSound(winSfx)
                        navigate(gameoverScene)
                    }
                }
            }

        // -------------------------------------------------------------------------
        // Title scene — uses gameScene ref (defined above)
        // -------------------------------------------------------------------------

        val titleScene =
            scene("title") {
                enter {
                    hideSprites()
                    clear()
                    // GB screen: 20 columns. "PONG"=4 → col 8, "PRESS START"=11 → col 4
                    print("PONG", position = PositionDef(8, 7))
                    print("PRESS START", position = PositionDef(5, 10))
                    print("FIRST TO 5", position = PositionDef(5, 13))
                }
                frame { whenever(buttons.start.pressed) { navigate(gameScene) } }
            }

        start = titleScene
    }
