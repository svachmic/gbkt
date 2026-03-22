/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import io.github.gbkt.emulator.MemoryAccess

/**
 * A single OAM (Object Attribute Memory) sprite entry parsed from the Game Boy's 0xFE00–0xFE9F.
 *
 * The Game Boy supports 40 hardware sprites. Each sprite occupies 4 bytes in OAM:
 * - Byte 0: Y position (rawY), displayed at `rawY - 16`
 * - Byte 1: X position (rawX), displayed at `rawX - 8`
 * - Byte 2: Tile index into VRAM tile data
 * - Byte 3: Attributes (priority, flip, palette, GBC bank/palette)
 *
 * @param index OAM slot number (0–39).
 * @param screenX Actual pixel X position (`rawX - 8`). Can be negative (off-screen left).
 * @param screenY Actual pixel Y position (`rawY - 16`). Can be negative (off-screen top).
 * @param rawX OAM byte 1 — X position with +8 offset.
 * @param rawY OAM byte 0 — Y position with +16 offset.
 * @param tileIndex OAM byte 2 — tile number in VRAM.
 * @param behindBg Attribute bit 7 — when true, sprite renders behind BG colors 1–3.
 * @param yFlip Attribute bit 6 — vertical flip.
 * @param xFlip Attribute bit 5 — horizontal flip.
 * @param dmgPalette Attribute bit 4 — DMG palette select (0 = OBP0, 1 = OBP1).
 * @param gbcVramBank Attribute bit 3 — GBC VRAM bank (0 or 1).
 * @param gbcPalette Attribute bits 2–0 — GBC palette number (0–7).
 * @param rawAttributes Full attribute byte for pass-through inspection.
 */
data class SpriteEntry(
    val index: Int,
    val screenX: Int,
    val screenY: Int,
    val rawX: Int,
    val rawY: Int,
    val tileIndex: Int,
    val behindBg: Boolean,
    val yFlip: Boolean,
    val xFlip: Boolean,
    val dmgPalette: Int,
    val gbcVramBank: Int,
    val gbcPalette: Int,
    val rawAttributes: Int,
)

/**
 * Stateless parser for the Game Boy's OAM (Object Attribute Memory) sprite table.
 *
 * Reads the 40-entry sprite table at 0xFE00–0xFE9F and parses each 4-byte entry into a
 * [SpriteEntry]. Follows the same stateless-singleton pattern as [VramTextVerifier].
 *
 * Usage:
 * ```kotlin
 * val memory = session.getMemory()
 * val visible = OamSpriteReader.readVisible(memory)
 * val tall = OamSpriteReader.isTallSpriteMode(memory)
 * ```
 */
object OamSpriteReader {

    /** Start address of OAM in the Game Boy address space. */
    const val OAM_START = 0xFE00

    /** Total number of hardware sprite slots. */
    const val SPRITE_COUNT = 40

    /** Number of bytes per OAM entry. */
    const val BYTES_PER_SPRITE = 4

    /** Address of the LCDC (LCD Control) register. */
    const val LCDC_ADDRESS = 0xFF40

    /**
     * Reads all 40 OAM sprite entries from memory.
     *
     * @param memory The emulator memory interface.
     * @return List of exactly 40 [SpriteEntry] instances, one per OAM slot.
     */
    fun readAll(memory: MemoryAccess): List<SpriteEntry> =
        (0 until SPRITE_COUNT).map { i ->
            val base = OAM_START + i * BYTES_PER_SPRITE
            val rawY = memory.readByte(base)
            val rawX = memory.readByte(base + 1)
            val tile = memory.readByte(base + 2)
            val attr = memory.readByte(base + 3)
            SpriteEntry(
                index = i,
                screenX = rawX - 8,
                screenY = rawY - 16,
                rawX = rawX,
                rawY = rawY,
                tileIndex = tile,
                behindBg = (attr shr 7) and 1 == 1,
                yFlip = (attr shr 6) and 1 == 1,
                xFlip = (attr shr 5) and 1 == 1,
                dmgPalette = (attr shr 4) and 1,
                gbcVramBank = (attr shr 3) and 1,
                gbcPalette = attr and 0x07,
                rawAttributes = attr,
            )
        }

    /**
     * Reads only visible OAM sprites — those whose screen position overlaps the 160×144 LCD.
     *
     * A sprite is considered visible when:
     * - `rawY > 0` (not hidden via Y=0)
     * - `rawX > 0` (not hidden via X=0)
     * - `screenY < 144` (at least partially on-screen vertically, accounting for sprite height)
     * - `screenX < 160` (at least partially on-screen horizontally)
     *
     * @param memory The emulator memory interface.
     * @return Filtered list of on-screen [SpriteEntry] instances.
     */
    fun readVisible(memory: MemoryAccess): List<SpriteEntry> {
        val height = spriteHeight(memory)
        return readAll(memory).filter { sprite ->
            sprite.rawY > 0 &&
                sprite.rawX > 0 &&
                sprite.screenY < 144 &&
                sprite.screenY > -height &&
                sprite.screenX < 160 &&
                sprite.screenX > -8
        }
    }

    /**
     * Checks the LCDC register bit 2 to determine if tall (8×16) sprite mode is active.
     *
     * @param memory The emulator memory interface.
     * @return `true` if sprites are 8×16 pixels, `false` if 8×8.
     */
    fun isTallSpriteMode(memory: MemoryAccess): Boolean {
        val lcdc = memory.readByte(LCDC_ADDRESS)
        return (lcdc shr 2) and 1 == 1
    }

    /**
     * Returns the current hardware sprite height (8 or 16) based on LCDC register.
     *
     * @param memory The emulator memory interface.
     * @return 16 if tall sprite mode, 8 otherwise.
     */
    fun spriteHeight(memory: MemoryAccess): Int =
        if (isTallSpriteMode(memory)) 16 else 8
}
