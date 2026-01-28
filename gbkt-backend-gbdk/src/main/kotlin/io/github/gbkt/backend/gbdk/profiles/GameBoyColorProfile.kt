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
 * Target profile for the Game Boy Color (GBC).
 *
 * Hardware specifications:
 * - CPU: Sharp LR35902 @ 8.38 MHz (double speed mode)
 * - Screen: 160x144 pixels, 32,768 colors (RGB555)
 * - Sprites: 40 OAM entries, 10 per scanline, 8x8 or 8x16
 * - Palettes: 8 sprite palettes + 8 background palettes, 4 colors each
 * - Work RAM: 32 KB (4 KB + 7 x 4 KB switchable banks)
 * - Video RAM: 16 KB (2 x 8 KB banks)
 * - ROM: Up to 8 MB with MBC5
 */
object GameBoyColorProfile : TargetProfile {
    override val name = "Nintendo Game Boy Color"
    override val id = "gbc"

    /** Number of background palettes on GBC. */
    private const val GBC_BG_PALETTE_COUNT = 8

    /** Number of sprite palettes on GBC. */
    private const val GBC_SPRITE_PALETTE_COUNT = 8

    override val screen =
        ScreenSpec(
            width = GameBoyConstants.SCREEN_WIDTH,
            height = GameBoyConstants.SCREEN_HEIGHT,
            bitsPerPixel = GameBoyConstants.BITS_PER_PIXEL, // Still 2bpp tiles, but with palettes
            tileSize = GameBoyConstants.TILE_SIZE,
            backgroundLayers = GameBoyConstants.BACKGROUND_LAYERS, // Plus window layer
            supportsPalettes = true,
            paletteCount = GBC_BG_PALETTE_COUNT,
            colorsPerPalette = GameBoyConstants.COLORS_PER_PALETTE,
        )

    override val sprites =
        SpriteSpec(
            maxSprites = GameBoyConstants.MAX_SPRITES,
            maxPerScanline = GameBoyConstants.MAX_SPRITES_PER_SCANLINE,
            sizes = GameBoyConstants.SPRITE_SIZES,
            supportsPalettes = true,
            paletteCount = GBC_SPRITE_PALETTE_COUNT,
            supportsFlipping = true,
            supportsPriority = true,
        )

    override val memory =
        MemorySpec(
            workRam = 32 * 1024, // 32 KB (GBC-specific, banked)
            videoRam = 16 * 1024, // 16 KB (GBC-specific, 2 banks)
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
