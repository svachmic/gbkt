/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

// =============================================================================
// TYPE ALIASES - Semantic clarity for parameters
// =============================================================================

/** Distance in pixels on the Game Boy screen (160x144 visible area) */
typealias Pixels = Int

/** Duration in frames (~60fps on Game Boy, ~16.7ms per frame) */
typealias FrameCount = Int

/** Position in tile units (8x8 pixels per tile, 20x18 visible tiles) */
typealias TileUnits = Int

/** Color value as RGB888 hex (0xRRGGBB) */
typealias ColorHex = Int

/** Palette slot index (0-7 for both sprite and background palettes) */
typealias PaletteSlot = Int

/** OAM (Object Attribute Memory) slot index (0-39, 40 sprites max) */
typealias OamSlot = Int

/** Tile index within a sprite sheet or tileset */
typealias TileIndex = Int
