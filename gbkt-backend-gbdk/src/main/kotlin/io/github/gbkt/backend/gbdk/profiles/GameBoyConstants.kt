/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.profiles

import io.github.gbkt.core.constraints.AudioChannel
import io.github.gbkt.core.constraints.AudioChannelType
import io.github.gbkt.core.constraints.AudioSpec
import io.github.gbkt.core.constraints.SpriteSize
import io.github.gbkt.core.constraints.TargetProfiles

/**
 * Shared constants for Game Boy and Game Boy Color hardware.
 *
 * Both platforms share the same ROM/RAM architecture, sprite hardware, and audio system. The main
 * differences are in color support and extended memory for GBC.
 */
object GameBoyConstants {
    // =============================================================================
    // SCREEN
    // =============================================================================

    /**
     * Screen width in pixels.
     *
     * Derived from the canonical Game Boy screen specification
     * [TargetProfiles.GAME_BOY_SCREEN] — the single source of truth for 160×144 dimensions.
     */
    val SCREEN_WIDTH = TargetProfiles.GAME_BOY_SCREEN.width

    /**
     * Screen height in pixels.
     *
     * Derived from the canonical Game Boy screen specification
     * [TargetProfiles.GAME_BOY_SCREEN] — the single source of truth for 160×144 dimensions.
     */
    val SCREEN_HEIGHT = TargetProfiles.GAME_BOY_SCREEN.height

    /** Bits per pixel for tiles (both DMG and GBC use 2bpp tiles). */
    const val BITS_PER_PIXEL = 2

    /** Tile size in pixels (8x8). */
    const val TILE_SIZE = 8

    /** Number of background layers. */
    const val BACKGROUND_LAYERS = 1

    /** Colors per palette (always 4 for 2bpp). */
    const val COLORS_PER_PALETTE = 4

    // =============================================================================
    // SPRITES
    // =============================================================================

    /** Maximum number of sprites in OAM (Object Attribute Memory). */
    const val MAX_SPRITES = 40

    /** Maximum sprites that can be rendered per scanline. */
    const val MAX_SPRITES_PER_SCANLINE = 10

    /** Number of bytes per OAM entry. */
    const val OAM_ENTRY_SIZE = 4

    /** Total OAM size in bytes. */
    const val OAM_SIZE = MAX_SPRITES * OAM_ENTRY_SIZE

    /** Supported sprite sizes. */
    val SPRITE_SIZES = listOf(SpriteSize(8, 8), SpriteSize(8, 16))

    // =============================================================================
    // MEMORY
    // =============================================================================

    /** Size of a ROM bank in bytes (16 KB). */
    const val ROM_BANK_SIZE = 16 * 1024

    /** Size of an external RAM bank in bytes (8 KB). */
    const val RAM_BANK_SIZE = 8 * 1024

    /** Size of HRAM (High RAM) in bytes. */
    const val HRAM_SIZE = 127

    /** Recommended stack size in bytes. */
    const val STACK_SIZE = 256

    /** Maximum ROM size with MBC5 (8 MB). */
    const val MAX_ROM_SIZE = 8 * 1024 * 1024

    /** Default number of ROM banks (32 KB = 2 banks). */
    const val DEFAULT_ROM_BANKS = 2

    /** Maximum number of external RAM banks with MBC5. */
    const val MAX_RAM_BANKS = 16

    // =============================================================================
    // AUDIO
    // =============================================================================

    /**
     * Game Boy audio specification.
     *
     * Both DMG and GBC share the same audio hardware:
     * - Channel 1: Pulse with sweep
     * - Channel 2: Pulse
     * - Channel 3: Wavetable
     * - Channel 4: Noise
     */
    val AUDIO_SPEC =
        AudioSpec(
            channels =
                listOf(
                    AudioChannel("Pulse 1", AudioChannelType.PULSE, 0),
                    AudioChannel("Pulse 2", AudioChannelType.PULSE, 1),
                    AudioChannel("Wave", AudioChannelType.WAVE, 2),
                    AudioChannel("Noise", AudioChannelType.NOISE, 3),
                ),
            sampleRate = 0, // No direct PCM support
            supportsPCM = false,
            supportsWavetable = true, // Channel 3 is wavetable
        )
}
