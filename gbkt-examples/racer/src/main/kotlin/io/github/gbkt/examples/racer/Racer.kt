/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.racer

import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.GbcTarget
import io.github.gbkt.core.ir.MovementStyle
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SoundPreset
import io.github.gbkt.genre.sport.domain.RacingMode
import io.github.gbkt.genre.sport.dsl.racing

/**
 * Top-down Racer game defined using the v2 DSL.
 *
 * Demonstrates:
 * - racing() DSL from gbkt-genre-sport: track/waypoints, vehicleStats, AI configuration
 * - Camera follow with smoothing and map bounds
 * - Smooth movement on car actor for fluid overhead racing feel
 * - GBC_COMPATIBLE target (color enhances the racing track)
 * - 3 scenes: title, race, results
 * - Forward-declared titleRef for circular navigation (title defined last)
 *
 * Scene ordering: results → race → title. The cycle from results back to title is broken with `val
 * titleRef = sceneRef("title")` at the top.
 */
@Suppress("LongMethod")
val racer =
    game("Racer") {
        config {
            cartridge = "ROM_ONLY"
            romBanks = 2
            target(GbcTarget.GBC_COMPATIBLE)
        }

        // Forward-declare titleRef for circular navigation (title defined after results)
        val titleRef = sceneRef("title")

        // -------------------------------------------------------------------------
        // Variables
        // -------------------------------------------------------------------------

        var lap by u8Var(0)
        var raceTime by u8Var(0)
        var position by u8Var(1) // race position (1st, 2nd, etc.)

        // -------------------------------------------------------------------------
        // Sound effects
        // -------------------------------------------------------------------------

        val engineSfx by soundEffect { preset(SoundPreset.BEEP) }
        val turnSfx by soundEffect { preset(SoundPreset.BUMP) }
        val lapSfx by soundEffect { preset(SoundPreset.COIN) }
        val winSfx by soundEffect { preset(SoundPreset.WIN) }

        // -------------------------------------------------------------------------
        // Racing system — showcases racing() DSL with track/waypoints, vehicleStats, AI
        // -------------------------------------------------------------------------

        racing("track1") {
            mode(RacingMode.AI_OPPONENT)
            laps(3)
            track("circuit") {
                waypoint(x = 5, y = 5, checkpoint = true)
                waypoint(x = 15, y = 5, checkpoint = false)
                waypoint(x = 15, y = 15, checkpoint = true)
                waypoint(x = 5, y = 15, checkpoint = false)
            }
            vehicle("car_player") {
                name("Racer")
                stats {
                    speed(200)
                    acceleration(160)
                    handling(180)
                }
            }
            vehicle("car_ai") {
                name("Rival")
                stats {
                    speed(180)
                    acceleration(150)
                    handling(200)
                }
            }
            ai {
                speedPercent(85)
                difficulty(3)
                rubberBanding(enabled = true, strength = 40)
            }
        }

        // -------------------------------------------------------------------------
        // Zone — circuit track map (32x32 tiles at 8px = 256x256 pixel world)
        // -------------------------------------------------------------------------

        zone("circuit") {
            name("Circuit Track")
            tileset("sprites/track.png")
            size(32, 32)
            safeZone()
        }

        // -------------------------------------------------------------------------
        // Actors — car uses smooth movement for fluid racing feel
        // -------------------------------------------------------------------------

        val car by actor {
            position(40, 100)
            sprite(asset("sprites/car.png")) {
                size(8, 16)
                hitbox(0, 0, 8, 16)
            }
            movement {
                style(MovementStyle.SMOOTH)
                speed(3)
                acceleration(1)
                friction(1)
            }
        }

        // -------------------------------------------------------------------------
        // Camera — follows the car with smoothing, bounded to 256x256 pixel world
        // -------------------------------------------------------------------------

        camera {
            follow(car)
            smoothing = 0.3f
            bounds(256, 256)
        }

        // -------------------------------------------------------------------------
        // Results scene — defined first (no outward SceneRef deps in frame block)
        // -------------------------------------------------------------------------

        val resultsScene =
            scene("results") {
                enter {
                    hideSprites()
                    clear()
                    print("RACE COMPLETE", position = PositionDef(4, 4))
                    print("POSITION: %d", position.toExpr(), position = PositionDef(4, 7))
                    print("TIME: %d", raceTime.toExpr(), position = PositionDef(4, 9))
                    print("PRESS START", position = PositionDef(5, 13))
                }
                frame {
                    whenever(buttons.start.pressed) {
                        lap set 0
                        raceTime set 0
                        position set 1
                        navigate(titleRef)
                    }
                }
            }

        // -------------------------------------------------------------------------
        // Race scene — uses resultsScene ref (defined above)
        // -------------------------------------------------------------------------

        val raceScene =
            scene("race") {
                enter {
                    clear()
                    showSprites()
                    car.moveTo(40, 100)
                    lap set 0
                    raceTime set 0
                    position set 1
                    print("LAP: %d", lap.toExpr(), position = PositionDef(0, 0))
                }
                frame {
                    // D-pad driving controls — up/down accelerate/brake, left/right steer
                    whenever(dpad.up.held) {
                        moveBy(car, 0, -3)
                        playSound(engineSfx)
                    }
                    whenever(dpad.down.held) {
                        moveBy(car, 0, 2)
                        playSound(engineSfx)
                    }
                    whenever(dpad.left.held) {
                        moveBy(car, -3, 0)
                        playSound(turnSfx)
                    }
                    whenever(dpad.right.held) {
                        moveBy(car, 3, 0)
                        playSound(turnSfx)
                    }

                    // Lap counting — detect when car returns to start/finish area
                    whenever(car.x isBelow 50) {
                        whenever(car.y isBelow 115) {
                            whenever(car.y isAtLeast 95) {
                                whenever(lap isBelow 3) {
                                    lap += 1
                                    playSound(lapSfx)
                                    print("LAP: %d", lap.toExpr(), position = PositionDef(0, 0))
                                }
                            }
                        }
                    }

                    // Race timer — increment each frame
                    raceTime += 1

                    // Finish condition — all 3 laps complete
                    whenever(lap isAtLeast 3) {
                        playSound(winSfx)
                        navigate(resultsScene)
                    }
                }
            }

        // -------------------------------------------------------------------------
        // Title scene — uses raceScene ref (defined above)
        // -------------------------------------------------------------------------

        val titleScene =
            scene("title") {
                enter {
                    hideSprites()
                    clear()
                    print("RACER", position = PositionDef(8, 5))
                    print("TOP-DOWN RACING", position = PositionDef(3, 8))
                    print("PRESS START", position = PositionDef(5, 12))
                    print("3 LAPS TO WIN", position = PositionDef(4, 14))
                }
                frame { whenever(buttons.start.pressed) { navigate(raceScene) } }
            }

        start = titleScene.id
    }
