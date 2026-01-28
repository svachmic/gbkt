/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.constraints

/**
 * Sprite hardware specifications.
 *
 * @property maxSprites Maximum number of hardware sprites (OAM entries).
 * @property maxPerScanline Maximum sprites per scanline before flickering.
 * @property sizes List of supported sprite sizes in pixels (width x height).
 * @property supportsPalettes Whether sprites can use multiple palettes.
 * @property paletteCount Number of sprite palettes available.
 * @property supportsFlipping Whether hardware supports horizontal/vertical flip.
 * @property supportsPriority Whether sprites have priority/layer control.
 */
data class SpriteSpec(
    val maxSprites: Int,
    val maxPerScanline: Int,
    val sizes: List<SpriteSize>,
    val supportsPalettes: Boolean = false,
    val paletteCount: Int = 1,
    val supportsFlipping: Boolean = true,
    val supportsPriority: Boolean = true,
) {
    /** Check if a sprite size is supported. */
    fun supportsSize(width: Int, height: Int): Boolean =
        sizes.any { it.width == width && it.height == height }
}

/**
 * A supported sprite size.
 *
 * @property width Sprite width in pixels.
 * @property height Sprite height in pixels.
 */
data class SpriteSize(val width: Int, val height: Int) {
    override fun toString() = "${width}x$height"
}
