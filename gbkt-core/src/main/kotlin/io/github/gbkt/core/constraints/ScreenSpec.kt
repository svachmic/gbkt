/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.constraints

/**
 * Screen/display hardware specifications.
 *
 * @property width Screen width in pixels.
 * @property height Screen height in pixels.
 * @property bitsPerPixel Color depth per tile pixel (2 for both GB and GBC — GBC color comes from
 *   per-tile palette attributes, not deeper tile data).
 * @property tileSize Tile dimensions in pixels (typically 8x8).
 * @property backgroundLayers Number of background layers supported.
 * @property supportsPalettes Whether the platform supports color palettes.
 * @property paletteCount Number of available palettes (0 if not supported).
 * @property colorsPerPalette Colors per palette (4 for GB/GBC).
 */
data class ScreenSpec(
    val width: Int,
    val height: Int,
    val bitsPerPixel: Int,
    val tileSize: Int = 8,
    val backgroundLayers: Int = 1,
    val supportsPalettes: Boolean = false,
    val paletteCount: Int = 0,
    val colorsPerPalette: Int = 4,
) {
    /** Screen width in tiles. */
    val widthInTiles: Int
        get() = width / tileSize

    /** Screen height in tiles. */
    val heightInTiles: Int
        get() = height / tileSize

    /** Total visible tiles on screen. */
    val totalTiles: Int
        get() = widthInTiles * heightInTiles
}
