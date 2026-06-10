/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.breakout

import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.Anchor
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.IconDisplayMode
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SoundPreset

/** Prompt shown on the title and end screens. */
private const val PRESS_START_TEXT = "PRESS START"

/** HUD line showing the current score and remaining lives. */
private const val HUD_FORMAT = "SCORE:%d  LIVES:%d"

/** One row of 10 bricks drawn as characters on the BG layer. */
private const val BRICK_ROW = "##########"

/**
 * Breakout game defined using the gbkt DSL.
 *
 * Demonstrates:
 * - Paddle and ball actors — using `val x by actor { }` name inference
 * - Type-safe input: `dpad.left.held`, `dpad.right.held`, `buttons.start.pressed`
 * - Scene references for navigation
 * - Score, lives, bricksLeft variables with HUD display
 * - Visible brick rows drawn on background tile layer
 * - Sound effects on brick hit, wall bounce, life lost
 * - 4 scenes: title, game, gameover, win
 * - HUD panel with score number and lives icons — demonstrates new `hud()` DSL builder
 *
 * Bricks are drawn as characters on the BG layer. When a brick is "hit", one character is erased
 * using raw C (gotoxy + printf). Bricks erase right-to-left, bottom-to-top across 3 rows of 10.
 *
 * Scene ordering: win → gameover → game → title (each references only earlier-defined scenes). The
 * cycle is broken with `val titleRef = sceneRef("title")` forward-declaration at the top.
 */
@Suppress("LongMethod")
val breakout =
    game("Breakout") {
        config { cartridge(Cartridge.ROM_ONLY) }

        // Forward-declare titleRef for circular navigation (title defined after win/gameover)
        val titleRef = sceneRef("title")

        // -------------------------------------------------------------------------
        // Variables
        // -------------------------------------------------------------------------

        var score by u8Var(0)
        var lives by u8Var(3)
        var bricksLeft by u8Var(30)
        var ballDx by i8Var(1)
        var ballDy by i8Var(-1)

        // Intermediate variables for brick collision calculations
        var bc by u8Var(0)
        var brow by u8Var(0)
        var bidx by u8Var(0)

        // Global brick state array: 3 rows x 10 cols = 30 bricks
        val bricks by u8Array(30)

        // -------------------------------------------------------------------------
        // Sound effects
        // -------------------------------------------------------------------------

        val hitSfx by soundEffect { preset(SoundPreset.HIT) }
        val scoreSfx by soundEffect { preset(SoundPreset.COIN) }
        val loseSfx by soundEffect { preset(SoundPreset.EXPLODE) }
        val winSfx by soundEffect { preset(SoundPreset.POWERUP) }

        // -------------------------------------------------------------------------
        // Actors — name inferred from Kotlin property via ActorDelegate.provideDelegate
        // -------------------------------------------------------------------------

        val paddle by actor {
            position(72, 132)
            sprite(asset("sprites/paddle.png")) {
                size(24, 8)
                hitbox(0, 0, 24, 8)
            }
        }

        val ball by actor {
            position(80, 120)
            sprite(asset("sprites/ball.png")) {
                size(8, 8)
                hitbox(0, 0, 8, 8)
            }
        }

        // -------------------------------------------------------------------------
        // HUD — score number and lives icons (new hud() DSL builder from Phase 06.2)
        // The HUD panel renders on the window layer at top-left.
        // Note: the existing print() calls at the top of the game scene also update
        // the score/lives — the HUD builder here demonstrates the API; both paths coexist.
        // -------------------------------------------------------------------------

        val gameHud =
            hud("breakout_hud") {
                anchor(Anchor.TOP_LEFT)
                number("score") {
                    variable(score)
                    label("SC:")
                    format("%d")
                }
                icons("lives") {
                    variable(lives)
                    max(3)
                    fullTile(0x08)
                    emptyTile(0x09)
                    displayMode(IconDisplayMode.FULL_AND_EMPTY)
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
                    print("YOU WIN!", position = PositionDef(6, 6))
                    print("SCORE: %d", score.toExpr(), position = PositionDef(6, 9))
                    print(PRESS_START_TEXT, position = PositionDef(5, 13))
                }
                // Navigate to title — uses forward-declared titleRef (SceneRef)
                frame { whenever(buttons.start.pressed) { navigate(titleRef) } }
            }

        // -------------------------------------------------------------------------
        // Game-over scene — defined second; winScene ref available if needed
        // -------------------------------------------------------------------------

        val gameoverScene =
            scene("gameover") {
                enter {
                    hideSprites()
                    clear()
                    print("GAME OVER", position = PositionDef(6, 6))
                    print("SCORE: %d", score.toExpr(), position = PositionDef(6, 9))
                    print(PRESS_START_TEXT, position = PositionDef(5, 13))
                }
                // Navigate to title — uses forward-declared titleRef (SceneRef)
                frame { whenever(buttons.start.pressed) { navigate(titleRef) } }
            }

        // -------------------------------------------------------------------------
        // Game scene — uses winScene and gameoverScene refs (defined above)
        // -------------------------------------------------------------------------

        val gameScene =
            scene("game") {
                enter {
                    clear()
                    showSprites()
                    setPosition(paddle.id, 72, 132)
                    // Show HUD panel via HudPanel.show() — demonstrates new hud DSL
                    gameHud.show()
                    ball.moveTo(80, 120)
                    ballDx set 1
                    ballDy set -1
                    score set 0
                    lives set 3
                    bricksLeft set 30
                    // Initialize brick state array — all bricks alive
                    bidx set 0
                    whileOp(bidx isBelow bricks.size) {
                        bricks[bidx] = 1
                        bidx += 1
                    }
                    // Draw HUD at top
                    print(HUD_FORMAT, score.toExpr(), lives.toExpr(), position = PositionDef(2, 1))
                    // Draw 3 rows of 10 bricks (rows 3, 4, 5 on the tile grid)
                    print(BRICK_ROW, position = PositionDef(5, 3))
                    print(BRICK_ROW, position = PositionDef(5, 4))
                    print(BRICK_ROW, position = PositionDef(5, 5))
                }
                frame {
                    // Paddle movement — type-safe d-pad API (clamped to screen: 0..136)
                    whenever(dpad.left.held) {
                        whenever(paddle.x isAbove 3) { moveBy(paddle, -3, 0) }
                    }
                    whenever(dpad.right.held) {
                        whenever(paddle.x isBelow 136) { moveBy(paddle, 3, 0) }
                    }

                    // Ball movement
                    ball.x += ballDx
                    ball.y += ballDy

                    // Left/right wall bounce
                    whenever(ball.x isBelow 4) {
                        ballDx set 1
                        playSound(hitSfx)
                    }
                    whenever(ball.x isAbove 152) {
                        ballDx set -1
                        playSound(hitSfx)
                    }

                    // Top wall bounce (below HUD at y=16)
                    whenever(ball.y isBelow 16) {
                        ballDy set 1
                        playSound(hitSfx)
                    }

                    // Paddle collision — bounce upward on AABB overlap
                    whenever(ball.collides(paddle)) {
                        ballDy set -1
                        playSound(hitSfx)
                    }

                    // Ball below paddle — lose a life
                    whenever(ball.y isAbove 144) {
                        lives -= 1
                        playSound(loseSfx)
                        // Update HUD
                        print(
                            HUD_FORMAT,
                            score.toExpr(),
                            lives.toExpr(),
                            position = PositionDef(2, 1),
                        )
                        // Brief pause
                        hideSprites()
                        print("  BALL LOST!  ", position = PositionDef(3, 9))
                        delay(40)
                        print("              ", position = PositionDef(3, 9))
                        showSprites()
                        // Reset ball
                        ball.moveTo(80, 120)
                        ballDx set 1
                        ballDy set -1
                        // Check game over via SceneRef
                        whenever(lives isEqualTo 0) { navigate(gameoverScene) }
                    }

                    // Brick hit — positional collision with tile-based brick grid
                    // Brick grid: tiles (5,3)-(14,5) = pixels x[40,120) y[24,48)
                    // col = (ball.x - 40) >> 3, row = (ball.y - 24) >> 3 (8px tiles)
                    whenever((ball.y isAtLeast 24) logicalAnd (ball.y isBelow 48)) {
                        whenever((ball.x isAtLeast 40) logicalAnd (ball.x isBelow 120)) {
                            // Calculate brick column and row from ball position
                            bc set ((ball.x - 40) shr 3)
                            brow set ((ball.y - 24) shr 3)
                            bidx set (brow * 10 + bc)
                            whenever(bidx isBelow 30) {
                                whenever(bricks[bidx] isEqualTo 1) {
                                    bricks[bidx] = 0
                                    // Erase the brick character from the BG tile layer
                                    gotoxy(bc + 5, brow + 3)
                                    print(" ")
                                    bricksLeft -= 1
                                    score += 10
                                    ballDy *= -1
                                    playSound(hitSfx)
                                    print(
                                        HUD_FORMAT,
                                        score.toExpr(),
                                        lives.toExpr(),
                                        position = PositionDef(2, 1),
                                    )
                                }
                            }
                        }
                    }

                    // Win condition — navigate via SceneRef
                    whenever(bricksLeft isEqualTo 0) {
                        playSound(winSfx)
                        navigate(winScene)
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
                    // GB screen: 20 cols. "BREAKOUT"=8 → col 6
                    print("BREAKOUT", position = PositionDef(6, 6))
                    print(PRESS_START_TEXT, position = PositionDef(5, 10))
                }
                frame { whenever(buttons.start.pressed) { navigate(gameScene) } }
            }

        start = titleScene
    }
