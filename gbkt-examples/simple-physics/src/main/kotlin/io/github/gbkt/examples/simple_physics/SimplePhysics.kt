/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.simple_physics

import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.Cartridge

// Physics constants — mirror phys.c #define block (lines 30-34). Visibility = internal so tests can
// import (D-02).
internal const val MAX_X_SPEED_IN_SUBPIXELS = 64
internal const val MAX_Y_SPEED_IN_SUBPIXELS = 64
internal const val X_ACCELERATION_IN_SUBPIXELS = 2
internal const val Y_ACCELERATION_IN_SUBPIXELS = 2
internal const val JUMP_ACCELERATION_IN_SUBPIXELS = 32
internal const val INITIAL_POS_IN_SUBPIXELS = 1024

/**
 * SimplePhysics — idiomatic gbkt port of GBDK's `simple_physics` cross-platform example.
 *
 * Faithful port of `/Users/michalsvacha/gbdk/examples/cross-platform/simple_physics/src/phys.c`.
 *
 * Sub-pixel arithmetic uses 12.4 fixed-point packed into INT16 via `i16FixedVar`: construct in
 * pixels, use `.toPixel()` to extract the pixel coordinate for rendering. The reference's
 * `SUBPIXELS_TO_PIXELS(v) (v >> 4)` macro is reproduced as `.toPixel()`.
 *
 * Three behaviors mirror the reference's update loop:
 * - **D-pad held → per-axis accel + clamp.** Each direction adds/subtracts 2 sub-pixels/frame to
 *   spdX/spdY, then clamps against ±64 (MAX_SPEED_IN_SUBPIXELS).
 * - **A pressed → jump impulse.** Edge-triggered: sets spdY to `-JUMP_ACCELERATION_IN_SUBPIXELS`
 *   (matches phys.c:83).
 * - **No input → decel toward zero.** spdX/spdY ease back to 0 by ±1 sub-pixel/frame.
 *
 * Position sync uses `ActorRef.moveTo(Expr, Expr)` to teleport the ball actor to the
 * sub-pixel-converted coordinate (the `ActorRef.moveTo(Expr, Expr)` overload added in Phase 9.1
 * Plan 01 — SEED-002 — lowers the call to a single SetPosition op).
 */
@Suppress("LongMethod")
val simplePhysics =
    game("SimplePhysics") {
        config { cartridge(Cartridge.ROM_ONLY) }

        // ---------------------------------------------------------------------
        // Variables — 12.4 fixed-point position (i16FixedVar: construct in pixels)
        // posX/posY initialized to pixel 64 → stored as 64 shl 4 = 1024 sub-pixels (matches
        // phys.c:59)
        // spdX/spdY are raw speed in sub-pixels/frame — stay i16Var (Pitfall 1/2: speed ≠ position)
        // ---------------------------------------------------------------------

        var posX by i16FixedVar(64)
        var posY by i16FixedVar(64)
        var spdX by i16Var(0)
        var spdY by i16Var(0)

        // ---------------------------------------------------------------------
        // Actor — single 8×8 ball sprite at pixel (64, 64)
        // ---------------------------------------------------------------------

        val ball by actor {
            position(64, 64)
            sprite(asset("sprites/ball.png")) {
                size(8, 8)
                hitbox(0, 0, 8, 8)
            }
        }

        // ---------------------------------------------------------------------
        // Single play scene (D-06 — no title, no game-over)
        // ---------------------------------------------------------------------

        val playScene =
            scene("play") {
                enter {
                    showSprites()
                    posX set (64 shl 4)
                    posY set (64 shl 4)
                    spdX set 0
                    spdY set 0
                }
                frame {
                    // ------------------------------------------------------
                    // Y axis: UP/DOWN accel + clamp (phys.c L67-L73)
                    // ------------------------------------------------------
                    whenever(dpad.up.held) {
                        spdY -= Y_ACCELERATION_IN_SUBPIXELS
                        runIf(spdY isBelow -MAX_Y_SPEED_IN_SUBPIXELS) {
                            spdY set -MAX_Y_SPEED_IN_SUBPIXELS
                        }
                    }
                    whenever(dpad.down.held) {
                        spdY += Y_ACCELERATION_IN_SUBPIXELS
                        runIf(spdY isAbove MAX_Y_SPEED_IN_SUBPIXELS) {
                            spdY set MAX_Y_SPEED_IN_SUBPIXELS
                        }
                    }

                    // ------------------------------------------------------
                    // X axis: LEFT/RIGHT accel + clamp (phys.c L74-L80)
                    // ------------------------------------------------------
                    whenever(dpad.left.held) {
                        spdX -= X_ACCELERATION_IN_SUBPIXELS
                        runIf(spdX isBelow -MAX_X_SPEED_IN_SUBPIXELS) {
                            spdX set -MAX_X_SPEED_IN_SUBPIXELS
                        }
                    }
                    whenever(dpad.right.held) {
                        spdX += X_ACCELERATION_IN_SUBPIXELS
                        runIf(spdX isAbove MAX_X_SPEED_IN_SUBPIXELS) {
                            spdX set MAX_X_SPEED_IN_SUBPIXELS
                        }
                    }

                    // ------------------------------------------------------
                    // A pressed (edge) → jump impulse (phys.c L82-L84)
                    // ------------------------------------------------------
                    whenever(buttons.a.pressed) { spdY set -JUMP_ACCELERATION_IN_SUBPIXELS }

                    // ------------------------------------------------------
                    // Position integration (phys.c L87)
                    // ------------------------------------------------------
                    posX += spdX
                    posY += spdY

                    // Sub-pixel → pixel render (phys.c L90)
                    ball.moveTo(posX.toPixel(), posY.toPixel())

                    // ------------------------------------------------------
                    // Decel toward zero (phys.c L93-L94)
                    // Mutually exclusive — no else-if needed (Pitfall 4).
                    // ------------------------------------------------------
                    spdY.easeToZero()
                    spdX.easeToZero()
                }
            }

        start = playScene
    }
