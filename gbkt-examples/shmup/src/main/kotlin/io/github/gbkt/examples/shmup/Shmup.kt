/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.shmup

import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SoundPreset

/**
 * Shmup — vertical scrolling shoot-em-up game defined using the v2 DSL.
 *
 * Demonstrates:
 * - Entity pools for bullets (max 8) and enemies (max 4) via `val x by pool(template, max = N)`
 * - Vertical scroll simulation using a `scrollY` variable incremented each frame
 * - Cooldown-based shooting: `buttons.a.pressed` fires only when cooldown reaches 0
 * - Wave-based enemy spawning via a `waveTimer` counter
 * - Collision detection: `bullet.collides(enemy)` and `enemy.collides(player)`
 * - Type-safe input: `dpad.up.held`, `dpad.left.held`, `buttons.a.pressed`
 * - Scene references for navigation: title → gameplay → gameover → title (cycle)
 * - Sound effects: shoot, explode, hit, score
 *
 * Scene ordering: gameover → gameplay → title. The cycle is broken with `val titleRef =
 * sceneRef("title")` forward-declaration at the top.
 */
@Suppress("LongMethod")
val shmup =
    game("Shmup") {
        config {
            cartridge = "ROM_ONLY"
            romBanks = 2
        }

        // Forward-declare titleRef for circular navigation (title defined after gameover)
        val titleRef = sceneRef("title")

        // -------------------------------------------------------------------------
        // Variables
        // -------------------------------------------------------------------------

        var score by u8Var(0)
        var lives by u8Var(3)
        var scrollY by u8Var(0)
        var shootCooldown by u8Var(0)
        var waveTimer by u8Var(0)

        // -------------------------------------------------------------------------
        // Sound effects
        // -------------------------------------------------------------------------

        val shootSfx by soundEffect { preset(SoundPreset.HIT) }
        val explodeSfx by soundEffect { preset(SoundPreset.EXPLODE) }
        val hitSfx by soundEffect { preset(SoundPreset.BUMP) }
        val scoreSfx by soundEffect { preset(SoundPreset.COIN) }

        // -------------------------------------------------------------------------
        // Actors — templates for entity pools + player ship
        // name inferred from Kotlin property via ActorDelegate.provideDelegate
        // -------------------------------------------------------------------------

        val bullet by actor {
            position(-8, -8)
            sprite(asset("sprites/bullet.png")) {
                size(8, 8)
                hitbox(2, 0, 4, 8)
            }
        }

        val enemy by actor {
            position(80, 0)
            sprite(asset("sprites/enemy.png")) {
                size(16, 16)
                hitbox(0, 0, 16, 16)
            }
        }

        val player by actor {
            position(80, 120)
            sprite(asset("sprites/ship.png")) {
                size(16, 16)
                hitbox(2, 0, 12, 16)
            }
        }

        // -------------------------------------------------------------------------
        // Entity pools — showcase key feature of this example
        // -------------------------------------------------------------------------

        val bulletPool by pool(bullet, max = 8)
        val enemyPool by pool(enemy, max = 4)

        // -------------------------------------------------------------------------
        // Game-over scene — defined first (no SceneRef deps in frame block)
        // -------------------------------------------------------------------------

        val gameoverScene =
            scene("gameover") {
                enter {
                    hideSprites()
                    clear()
                    print("GAME OVER", position = PositionDef(5, 5))
                    print("SCORE: %d", score.toExpr(), position = PositionDef(5, 8))
                    print("PRESS START", position = PositionDef(5, 13))
                }
                frame {
                    whenever(buttons.start.pressed) {
                        score set 0
                        lives set 3
                        // Navigate to title — uses forward-declared titleRef (SceneRef)
                        navigate(titleRef)
                    }
                }
            }

        // -------------------------------------------------------------------------
        // Gameplay scene — uses gameoverScene ref (defined above)
        // -------------------------------------------------------------------------

        val gameplayScene =
            scene("gameplay") {
                enter {
                    clear()
                    showSprites()
                    score set 0
                    lives set 3
                    scrollY set 0
                    shootCooldown set 0
                    waveTimer set 0
                    player.moveTo(80, 120)
                    // Destroy any leftover pool entities from a previous run
                    destroyAll(bulletPool)
                    destroyAll(enemyPool)
                    print(
                        "SC:%d LV:%d",
                        score.toExpr(),
                        lives.toExpr(),
                        position = PositionDef(0, 0),
                    )
                }
                frame {
                    // ---- Vertical scroll simulation ----
                    scrollY += 1

                    // ---- Player movement — 4-directional, screen-clamped ----
                    whenever(dpad.up.held) {
                        whenever(player.y isAbove 8) { moveBy(player, 0, -2) }
                    }
                    whenever(dpad.down.held) {
                        whenever(player.y isBelow 128) { moveBy(player, 0, 2) }
                    }
                    whenever(dpad.left.held) {
                        whenever(player.x isAbove 4) { moveBy(player, -2, 0) }
                    }
                    whenever(dpad.right.held) {
                        whenever(player.x isBelow 140) { moveBy(player, 2, 0) }
                    }

                    // ---- Shooting: A pressed + cooldown guard ----
                    whenever(buttons.a.pressed) {
                        whenever(shootCooldown isEqualTo 0) {
                            spawn(bulletPool, player.x.toExpr(), player.y.toExpr())
                            shootCooldown set 8
                            playSound(shootSfx)
                        }
                    }

                    // ---- Cooldown countdown ----
                    whenever(shootCooldown isAbove 0) { shootCooldown -= 1 }

                    // ---- Move all active bullets upward ----
                    forEachActive(bulletPool, "bi") { bi ->
                        // Bullets fly upward at 4px/frame; destroy when offscreen
                        whenever(bullet.y isBelow 4) { destroy(bulletPool, bi.toExpr()) }
                    }

                    // ---- Wave spawning: spawn an enemy every 60 frames ----
                    waveTimer += 1
                    whenever(waveTimer isAtLeast 60) {
                        spawn(enemyPool, 80, 0)
                        waveTimer set 0
                    }

                    // ---- Move all active enemies downward ----
                    forEachActive(enemyPool, "ei") { ei ->
                        // Enemies descend at 1px/frame; destroy when below screen
                        whenever(enemy.y isAbove 144) { destroy(enemyPool, ei.toExpr()) }
                    }

                    // ---- Bullet–enemy collision ----
                    whenever(bullet.collides(enemy)) {
                        score += 10
                        playSound(explodeSfx)
                        playSound(scoreSfx)
                        print(
                            "SC:%d LV:%d",
                            score.toExpr(),
                            lives.toExpr(),
                            position = PositionDef(0, 0),
                        )
                    }

                    // ---- Enemy–player collision ----
                    whenever(enemy.collides(player)) {
                        lives -= 1
                        playSound(hitSfx)
                        print(
                            "SC:%d LV:%d",
                            score.toExpr(),
                            lives.toExpr(),
                            position = PositionDef(0, 0),
                        )
                        // Game over when all lives exhausted
                        whenever(lives isEqualTo 0) { navigate(gameoverScene) }
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
                    // GB screen: 20 cols. "SHMUP"=5 → col 7
                    print("SHMUP", position = PositionDef(7, 6))
                    print("SHOOT-EM-UP", position = PositionDef(5, 9))
                    print("PRESS START", position = PositionDef(5, 13))
                }
                frame { whenever(buttons.start.pressed) { navigate(gameplayScene) } }
            }

        start = titleScene.id
    }
