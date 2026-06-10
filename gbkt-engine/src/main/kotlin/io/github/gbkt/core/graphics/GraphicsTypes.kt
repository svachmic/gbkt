/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.graphics

/**
 * Pixel dimensions of a sprite tile or sprite composite.
 *
 * Game Boy sprites are 8x8 or 8x16 hardware tiles; larger sprites are composites.
 *
 * @property width Width in pixels.
 * @property height Height in pixels.
 */
data class SpriteSize(val width: Int, val height: Int)

/**
 * A single frame within an animation sequence.
 *
 * @property tileIndex Zero-based index of the tile within the sprite sheet.
 * @property duration Number of game frames this animation frame is shown for.
 */
data class AnimationFrame(val tileIndex: Int, val duration: Int)

/**
 * A named animation sequence composed of one or more [AnimationFrame]s.
 *
 * @property frames Ordered list of frames in playback order.
 * @property loop Whether the animation restarts after the last frame. Defaults to `true`.
 */
data class AnimationDef(val frames: List<AnimationFrame>, val loop: Boolean = true)

/**
 * A reference to a palette slot.
 *
 * On Game Boy Color, palette indices 0–7 map to the 8 available OBJ palettes. On original Game Boy,
 * the index is ignored (single monochrome palette).
 *
 * @property index Palette slot index in the range 0–7.
 */
data class PaletteIndex(val index: Int)
