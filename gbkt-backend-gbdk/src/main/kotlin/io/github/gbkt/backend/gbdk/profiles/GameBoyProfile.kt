/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.profiles

import io.github.gbkt.core.constraints.MemorySpec
import io.github.gbkt.core.constraints.ScreenSpec
import io.github.gbkt.core.constraints.SpriteSpec
import io.github.gbkt.core.constraints.TargetProfile

/**
 * Target profile for the original Game Boy (DMG).
 *
 * Hardware specifications:
 * - CPU: Sharp LR35902 @ 4.19 MHz
 * - Screen: 160x144 pixels, 4 shades of green
 * - Sprites: 40 OAM entries, 10 per scanline, 8x8 or 8x16
 * - Work RAM: 8 KB
 * - Video RAM: 8 KB
 * - ROM: Up to 8 MB with MBC5 (32 KB without banking)
 */
object GameBoyProfile : TargetProfile {
    override val name = "Nintendo Game Boy"
    override val id = "gb"

    override val screen =
        ScreenSpec(
            width = GameBoyConstants.SCREEN_WIDTH,
            height = GameBoyConstants.SCREEN_HEIGHT,
            bitsPerPixel = GameBoyConstants.BITS_PER_PIXEL,
            tileSize = GameBoyConstants.TILE_SIZE,
            backgroundLayers = GameBoyConstants.BACKGROUND_LAYERS,
            supportsPalettes = false, // DMG has fixed 4-shade palette
            paletteCount = 0,
            colorsPerPalette = GameBoyConstants.COLORS_PER_PALETTE,
        )

    override val sprites =
        SpriteSpec(
            maxSprites = GameBoyConstants.MAX_SPRITES,
            maxPerScanline = GameBoyConstants.MAX_SPRITES_PER_SCANLINE,
            sizes = GameBoyConstants.SPRITE_SIZES,
            supportsPalettes = false, // DMG has 2 sprite palettes but no color
            paletteCount = 2,
            supportsFlipping = true,
            supportsPriority = true,
        )

    override val memory =
        MemorySpec(
            workRam = 8 * 1024, // 8 KB (DMG-specific)
            videoRam = 8 * 1024, // 8 KB (DMG-specific)
            oamSize = GameBoyConstants.OAM_SIZE,
            hiRam = GameBoyConstants.HRAM_SIZE,
            romBankSize = GameBoyConstants.ROM_BANK_SIZE,
            ramBankSize = GameBoyConstants.RAM_BANK_SIZE,
            stackSize = GameBoyConstants.STACK_SIZE,
        )

    override val audio = GameBoyConstants.AUDIO_SPEC

    override val supportsBanking = true
    override val maxRomSize = GameBoyConstants.MAX_ROM_SIZE
    override val defaultRomBanks = GameBoyConstants.DEFAULT_ROM_BANKS
    override val maxRamBanks = GameBoyConstants.MAX_RAM_BANKS
}
