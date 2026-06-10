/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import io.github.gbkt.emulator.MemoryAccess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OamSpriteReaderTest {

    /** Creates a mock MemoryAccess backed by a flat byte array for the full 64KB address space. */
    private fun mockMemory(vararg patches: Pair<Int, Int>): MemoryAccess {
        val mem = IntArray(0x10000) { 0 }
        for ((addr, value) in patches) {
            mem[addr] = value
        }
        return object : MemoryAccess {
            override fun readByte(address: Int): Int = mem[address]

            override fun writeByte(address: Int, value: Int) {
                mem[address] = value
            }
        }
    }

    /** Writes a 4-byte OAM entry at the given slot index. */
    private fun oamPatches(
        slot: Int,
        rawY: Int,
        rawX: Int,
        tile: Int,
        attr: Int,
    ): List<Pair<Int, Int>> {
        val base = OamSpriteReader.OAM_START + slot * OamSpriteReader.BYTES_PER_SPRITE
        return listOf(base to rawY, base + 1 to rawX, base + 2 to tile, base + 3 to attr)
    }

    @Test
    fun `readAll returns exactly 40 sprites`() {
        val memory = mockMemory()
        val sprites = OamSpriteReader.readAll(memory)
        assertEquals(40, sprites.size)
        assertEquals(0, sprites.first().index)
        assertEquals(39, sprites.last().index)
    }

    @Test
    fun `positions decoded correctly with offset`() {
        // Sprite at OAM slot 0: rawY=32, rawX=24 → screenY=16, screenX=16
        val patches = oamPatches(0, rawY = 32, rawX = 24, tile = 0, attr = 0)
        val memory = mockMemory(*patches.toTypedArray())

        val sprite = OamSpriteReader.readAll(memory)[0]
        assertEquals(32, sprite.rawY)
        assertEquals(24, sprite.rawX)
        assertEquals(16, sprite.screenY)
        assertEquals(16, sprite.screenX)
    }

    @Test
    fun `tile index from byte 2`() {
        val patches = oamPatches(5, rawY = 32, rawX = 24, tile = 0x42, attr = 0)
        val memory = mockMemory(*patches.toTypedArray())

        val sprite = OamSpriteReader.readAll(memory)[5]
        assertEquals(0x42, sprite.tileIndex)
    }

    @Test
    fun `attribute flags parsed correctly`() {
        // attr = 0b1110_1101 = 0xED
        // bit 7: behindBg = 1
        // bit 6: yFlip = 1
        // bit 5: xFlip = 1
        // bit 4: dmgPalette = 0
        // bit 3: gbcVramBank = 1
        // bits 2-0: gbcPalette = 5 (0b101)
        val patches = oamPatches(0, rawY = 32, rawX = 24, tile = 0, attr = 0xED)
        val memory = mockMemory(*patches.toTypedArray())

        val sprite = OamSpriteReader.readAll(memory)[0]
        assertTrue(sprite.behindBg)
        assertTrue(sprite.yFlip)
        assertTrue(sprite.xFlip)
        assertEquals(0, sprite.dmgPalette)
        assertEquals(1, sprite.gbcVramBank)
        assertEquals(5, sprite.gbcPalette)
        assertEquals(0xED, sprite.rawAttributes)
    }

    @Test
    fun `readVisible filters Y=0 and X=0`() {
        val patches = mutableListOf<Pair<Int, Int>>()
        // Slot 0: hidden via Y=0
        patches += oamPatches(0, rawY = 0, rawX = 24, tile = 0, attr = 0)
        // Slot 1: hidden via X=0
        patches += oamPatches(1, rawY = 32, rawX = 0, tile = 0, attr = 0)
        // Slot 2: visible
        patches += oamPatches(2, rawY = 32, rawX = 24, tile = 0, attr = 0)

        val memory = mockMemory(*patches.toTypedArray())
        val visible = OamSpriteReader.readVisible(memory)

        assertEquals(1, visible.size)
        assertEquals(2, visible[0].index)
    }

    @Test
    fun `readVisible respects tall sprite mode`() {
        val patches = mutableListOf<Pair<Int, Int>>()
        // Sprite at screenY = 143 (rawY = 159) — visible in 8x8, visible in 8x16
        patches += oamPatches(0, rawY = 159, rawX = 24, tile = 0, attr = 0)
        // Sprite at screenY = 144 (rawY = 160) — off-screen in 8x8, partially visible in 8x16
        patches += oamPatches(1, rawY = 160, rawX = 24, tile = 0, attr = 0)

        // Normal mode (8x8): LCDC bit 2 = 0
        val memNormal = mockMemory(*patches.toTypedArray())
        val visibleNormal = OamSpriteReader.readVisible(memNormal)
        // Slot 0 screenY=143 < 144 → visible. Slot 1 screenY=144 ≥ 144 → not visible.
        assertEquals(1, visibleNormal.size)
        assertEquals(0, visibleNormal[0].index)

        // Tall mode (8x16): LCDC bit 2 = 1
        val tallPatches = patches.toMutableList()
        tallPatches += (OamSpriteReader.LCDC_ADDRESS to 0x04)
        val memTall = mockMemory(*tallPatches.toTypedArray())
        val visibleTall = OamSpriteReader.readVisible(memTall)
        // Both sprites visible in 8x16 mode (screenY < 144 is still checked, but sprite height
        // affects the -height bound). Slot 1 screenY=144 is NOT < 144 so still excluded.
        assertEquals(1, visibleTall.size)

        // Sprite that is partially on-screen in tall mode: screenY = -15 (rawY = 1)
        val edgePatches = mutableListOf<Pair<Int, Int>>()
        edgePatches += oamPatches(0, rawY = 1, rawX = 24, tile = 0, attr = 0) // screenY = -15
        edgePatches += (OamSpriteReader.LCDC_ADDRESS to 0x04)
        val memEdge = mockMemory(*edgePatches.toTypedArray())
        val visibleEdge = OamSpriteReader.readVisible(memEdge)
        // screenY = -15, height = 16: screenY > -16 → true, so visible
        assertEquals(1, visibleEdge.size)
    }

    @Test
    fun `all attributes zero produces defaults`() {
        val memory = mockMemory()
        val sprite = OamSpriteReader.readAll(memory)[0]
        assertFalse(sprite.behindBg)
        assertFalse(sprite.yFlip)
        assertFalse(sprite.xFlip)
        assertEquals(0, sprite.dmgPalette)
        assertEquals(0, sprite.gbcVramBank)
        assertEquals(0, sprite.gbcPalette)
        assertEquals(0, sprite.rawAttributes)
        assertEquals(0, sprite.tileIndex)
    }
}
