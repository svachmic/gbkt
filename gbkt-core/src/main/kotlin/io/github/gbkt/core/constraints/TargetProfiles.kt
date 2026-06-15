/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.constraints

/**
 * Canonical [ScreenSpec] presets for supported Game Boy targets.
 *
 * This object is the **single source of truth** for the 160×144 screen dimensions shared by the
 * original Game Boy (DMG) and the Game Boy Color (GBC). All backends and constants that need the
 * **screen dimensions** (`width`/`height`) MUST derive from this object rather than repeating the
 * numeric literals. Other fields (e.g. `bitsPerPixel`) are documentation constants until
 * [[SEED-TARGETPROFILE-SCREEN-THREADING]] wires them into codegen (v0.2.0).
 *
 * Using a central preset ensures that any future multi-target work (e.g. Super Game Boy border
 * resolution, GBA mode) has an unambiguous hook point and that literal replacement plans (e.g.
 * 17-05) remain byte-identical by construction.
 */
object TargetProfiles {

    /**
     * Canonical screen specification for the original Game Boy (DMG).
     *
     * Single source of truth for the 160×144 pixel display, 2 bits per pixel, 8×8 tiles, one
     * background layer, no hardware palette support.
     */
    val GAME_BOY_SCREEN =
        ScreenSpec(
            width = 160,
            height = 144,
            bitsPerPixel = 2,
            tileSize = 8,
            backgroundLayers = 1,
            supportsPalettes = false,
            paletteCount = 0,
            colorsPerPalette = 4,
        )

    /**
     * Canonical screen specification for the Game Boy Color (GBC).
     *
     * Single source of truth for the 160×144 pixel display, 2 bits per pixel, color via 8 hardware
     * palettes (4 colours each), 8×8 tiles, one background layer. GBC color depth comes from
     * per-tile palette attributes, not deeper tile data — tiles are always 2bpp, same as DMG.
     */
    val GAME_BOY_COLOR_SCREEN =
        ScreenSpec(
            width = 160,
            height = 144,
            bitsPerPixel = 2,
            tileSize = 8,
            backgroundLayers = 1,
            supportsPalettes = true,
            paletteCount = 8,
            colorsPerPalette = 4,
        )
}
