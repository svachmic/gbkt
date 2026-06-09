/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.examples.metasprites

import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.GbcTarget
import io.github.gbkt.core.ir.SpriteMode

// Constants — mirror metasprites.c #define block
internal const val NUM_FRAMES = 5       // 5-frame elephant animation
internal const val SPR_NUM_START = 0    // OAM slot start
internal const val TILE_NUM_START = 0   // VRAM tile start
internal const val MAX_SPEED = 32       // sub-pixels/frame (ACC limit)
internal const val ACCEL = 2            // acceleration per frame

/**
 * Metasprites — idiomatic gbkt port of GBDK's `metasprites` cross-platform example.
 *
 * Asset-driven OAM composition: `sprite(asset(...)) { mode/pivot/frameSize }` + `frames(N)`.
 * No tile transcription or baseId hand-coding — the asset pipeline invokes png2asset.
 *
 * Behaviors mirror the reference (metasprites.c):
 * - D-pad held: per-axis accel + clamp to ±MAX_SPEED; decel to zero when released.
 * - B pressed: advance animation frame (`idx` cycles 0..NUM_FRAMES-1).
 * - A pressed: cycle flip + sub-palette (`rot & 0x3` = flip; `rot >> 2` = palette slot 0-3).
 */
@Suppress("LongMethod")
val metasprites =
    game("Metasprites") {
        config {
            cartridge(Cartridge.ROM_ONLY)
            target(GbcTarget.GBC_COMPATIBLE) // D-09: required for sub-palette cycling
        }

        // 12.4 fixed-point position; speed in raw sub-pixels/frame
        var posX by i16FixedVar(80)
        var posY by i16FixedVar(72)
        var spdX by i16Var(0)
        var spdY by i16Var(0)

        // Animation + flip/subpal state (u8 for correct unsigned wrap comparisons — Pitfall 6)
        var idx by u8Var(0, wrapAt = NUM_FRAMES)
        var rot by u8Var(0, wrapAt = 16) // rot & 0x3 = flip; rot >> 2 = sub-palette 0-3

        // Sprite sub-palettes — Color.rgb555 (5-bit native components, no precision loss)
        // Source: metasprites.c gray_pal/pink_pal/cyan_pal/green_pal
        // 13.3-22 COLOR-VALUE fix (root cause (b) palette-INDEX-POLARITY; see
        // evidence/13.3-DIAGNOSTIC.md "## COLOR-VALUE root cause" + D-19): the ramp ASCENDS in
        // luminance (color0=darkest..color3=lightest) to match png2asset's source-derived tile
        // index order, so the index-3 body (RGB8(224,248,207), 29.2% of pixels) lands on the LIGHT
        // end. The prior DMG-descending order (color0=lightest) mapped the light body to black =
        // the user's "inverted/wrongly set" residual. color0 is the sprite transparent key.
        val gray by spritePalette {
            color0(Color.rgb555(0, 0, 0));    color1(Color.rgb555(10, 10, 10))
            color2(Color.rgb555(21, 21, 21)); color3(Color.rgb555(31, 31, 31))
        }
        // 13.3-24 — same COLOR-VALUE index-polarity fix extended to the `rot>>2` cycle palettes
        // (pink/cyan/green) so each tinted elephant renders with correct, non-inverted shading. The
        // two VISIBLE shades are swapped (color1<->color3, leaving color0 = transparent key and the
        // unused color2 in place): the index-3 body now lands on the BRIGHT hue and the index-1
        // outline on the DARK hue — keeping each elephant recognizably coloured (NOT a white body
        // with coloured trim, which a full reversal like gray would produce).
        val pink by spritePalette {
            color0(Color.rgb555(31, 31, 31)); color1(Color.rgb555(10, 0, 10))
            color2(Color.rgb555(21, 0, 21));  color3(Color.rgb555(31, 0, 31))
        }
        val cyan by spritePalette {
            color0(Color.rgb555(31, 31, 31)); color1(Color.rgb555(0, 10, 10))
            color2(Color.rgb555(0, 21, 21));  color3(Color.rgb555(10, 31, 31))
        }
        val green by spritePalette {
            color0(Color.rgb555(31, 31, 31)); color1(Color.rgb555(0, 10, 0))
            color2(Color.rgb555(0, 21, 0));   color3(Color.rgb555(21, 31, 21))
        }

        // Asset-driven elephant — png2asset cuts elephant.png (64×240px, 5 frames at 64×48px each)
        // pivot(32, 24) = center of the 64×48 frame so move_metasprite(80, 72) anchors at center.
        // png2asset receives -px 32 -py 24 → descriptor offsets are relative to (32,24).
        val elephant by metasprite {
            sprite(asset("sprites/elephant.png")) {
                mode(SpriteMode.SPR8x8)
                pivot(32, 24)
                frameSize(64, 48)
            }
            frames(NUM_FRAMES) // build-time cross-validation against png2asset output
        }

        val playScene =
            scene("play") {
                palette(gray); palette(pink); palette(cyan); palette(green)
                enter {
                    showSprites(); bgFillCheckerboard()
                    posX set (80 shl 4); posY set (72 shl 4)
                    spdX set 0; spdY set 0; idx set 0; rot set 0
                }
                frame {
                    whenever(dpad.up.held)    { spdY -= ACCEL; runIf(spdY isBelow -MAX_SPEED) { spdY set -MAX_SPEED } }
                    whenever(dpad.down.held)  { spdY += ACCEL; runIf(spdY isAbove  MAX_SPEED) { spdY set  MAX_SPEED } }
                    whenever(dpad.left.held)  { spdX -= ACCEL; runIf(spdX isBelow -MAX_SPEED) { spdX set -MAX_SPEED } }
                    whenever(dpad.right.held) { spdX += ACCEL; runIf(spdX isAbove  MAX_SPEED) { spdX set  MAX_SPEED } }
                    whenever(buttons.b.pressed) { idx++ }
                    whenever(buttons.a.pressed) { rot++ }
                    posX += spdX; posY += spdY
                    moveMetasprite(elephant)
                    spdY.easeToZero(); spdX.easeToZero()
                }
            }

        start = playScene
    }
