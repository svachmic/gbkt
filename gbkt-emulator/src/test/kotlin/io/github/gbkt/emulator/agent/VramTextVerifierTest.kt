/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import io.github.gbkt.emulator.MemoryAccess
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VramTextVerifierTest {

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

    /**
     * Writes a string into the tilemap at the given tile position, encoding tiles
     * according to the target layer (GBDK offset for BG, direct ASCII for WIN).
     */
    private fun writeText(
        patches: MutableList<Pair<Int, Int>>,
        text: String,
        x: Int,
        y: Int,
        layer: VramTextVerifier.TilemapLayer = VramTextVerifier.TilemapLayer.BACKGROUND,
    ) {
        val base = when (layer) {
            VramTextVerifier.TilemapLayer.BACKGROUND -> VramTextVerifier.BG_TILEMAP_BASE
            VramTextVerifier.TilemapLayer.WINDOW -> VramTextVerifier.WIN_TILEMAP_BASE
        }
        for ((i, c) in text.withIndex()) {
            val tile = when (layer) {
                VramTextVerifier.TilemapLayer.BACKGROUND -> c.code - 0x20
                VramTextVerifier.TilemapLayer.WINDOW -> c.code
            }
            patches.add((base + y * VramTextVerifier.ROW_STRIDE + x + i) to tile)
        }
    }

    @Test
    fun `readText returns correct ASCII from tilemap addresses`() {
        val patches = mutableListOf<Pair<Int, Int>>()
        writeText(patches, "PONG", 8, 7)
        val memory = mockMemory(*patches.toTypedArray())

        val text = VramTextVerifier.readText(memory, 8, 7, 4)
        assertEquals("PONG", text)
    }

    @Test
    fun `readRow returns 20 characters`() {
        val patches = mutableListOf<Pair<Int, Int>>()
        writeText(patches, "PRESS START", 5, 10)
        val memory = mockMemory(*patches.toTypedArray())

        val row = VramTextVerifier.readRow(memory, 10)
        assertEquals(20, row.length)
        assertEquals("PRESS START", row.substring(5, 16))
    }

    @Test
    fun `readAllRows returns 18 rows`() {
        val memory = mockMemory()
        val rows = VramTextVerifier.readAllRows(memory)
        assertEquals(18, rows.size)
        assertEquals(20, rows[0].length)
    }

    @Test
    fun `findText locates substring and returns position`() {
        val patches = mutableListOf<Pair<Int, Int>>()
        writeText(patches, "PONG", 8, 7)
        val memory = mockMemory(*patches.toTypedArray())

        val pos = VramTextVerifier.findText(memory, "PONG")
        assertNotNull(pos)
        assertEquals(8 to 7, pos)
    }

    @Test
    fun `findText returns null when text is absent`() {
        val memory = mockMemory()
        val pos = VramTextVerifier.findText(memory, "MISSING")
        assertNull(pos)
    }

    @Test
    fun `findTextAnyLayer searches both layers`() {
        // Write text to window layer only
        val patches = mutableListOf<Pair<Int, Int>>()
        writeText(patches, "VICTORY", 3, 5, VramTextVerifier.TilemapLayer.WINDOW)
        val memory = mockMemory(*patches.toTypedArray())

        // Should not find in background
        assertNull(VramTextVerifier.findText(memory, "VICTORY", VramTextVerifier.TilemapLayer.BACKGROUND))

        // Should find via findTextAnyLayer
        val result = VramTextVerifier.findTextAnyLayer(memory, "VICTORY")
        assertNotNull(result)
        assertEquals(3, result!!.first)
        assertEquals(5, result.second)
        assertEquals(VramTextVerifier.TilemapLayer.WINDOW, result.third)
    }

    @Test
    fun `non-printable tiles render as dot with direct decoder`() {
        // Test raw DIRECT_ASCII_DECODER behavior — tile 0x00 and 0xFF are outside printable range
        val base = VramTextVerifier.BG_TILEMAP_BASE
        val memory = mockMemory(
            base to 0x00,       // non-printable → '.'
            base + 1 to 0x41,   // 'A'
            base + 2 to 0xFF,   // non-printable → '.'
            base + 3 to 0x42,   // 'B'
        )

        val text = VramTextVerifier.readText(memory, 0, 0, 4, decoder = VramTextVerifier.DIRECT_ASCII_DECODER)
        assertEquals(".A.B", text)
    }

    @Test
    fun `readText from window layer uses correct base address`() {
        val patches = mutableListOf<Pair<Int, Int>>()
        writeText(patches, "WIN", 0, 0, VramTextVerifier.TilemapLayer.WINDOW)
        val memory = mockMemory(*patches.toTypedArray())

        val text = VramTextVerifier.readText(memory, 0, 0, 3, VramTextVerifier.TilemapLayer.WINDOW)
        assertEquals("WIN", text)

        // Background should NOT have this text — tile 0x00 decodes to ' ' via GBDK BG decoder
        val bgText = VramTextVerifier.readText(memory, 0, 0, 3, VramTextVerifier.TilemapLayer.BACKGROUND)
        assertEquals("   ", bgText)
    }

    @Test
    fun `GBDK BG decoder adds 0x20 offset`() {
        // 'P' (0x50) is stored as tile 0x30 on the BG layer
        val base = VramTextVerifier.BG_TILEMAP_BASE
        val memory = mockMemory(
            base to 0x30,       // 0x30 + 0x20 = 0x50 = 'P'
            base + 1 to 0x2F,   // 0x2F + 0x20 = 0x4F = 'O'
            base + 2 to 0x2E,   // 0x2E + 0x20 = 0x4E = 'N'
            base + 3 to 0x27,   // 0x27 + 0x20 = 0x47 = 'G'
        )

        val text = VramTextVerifier.readText(memory, 0, 0, 4)
        assertEquals("PONG", text)
    }

    @Test
    fun `GBDK BG decoder boundary values`() {
        val base = VramTextVerifier.BG_TILEMAP_BASE
        val memory = mockMemory(
            base to 0x00,       // 0x00 + 0x20 = 0x20 = ' '
            base + 1 to 0x5E,   // 0x5E + 0x20 = 0x7E = '~' (last printable)
            base + 2 to 0x5F,   // 0x5F + 0x20 = 0x7F → non-printable → '.'
        )

        val text = VramTextVerifier.readText(memory, 0, 0, 3)
        assertEquals(" ~.", text)
    }

    @Test
    fun `explicit decoder overrides layer default`() {
        // Write GBDK-encoded BG tiles but read with DIRECT_ASCII decoder
        val patches = mutableListOf<Pair<Int, Int>>()
        writeText(patches, "AB", 0, 0)
        val memory = mockMemory(*patches.toTypedArray())

        // Default decoder should decode correctly
        assertEquals("AB", VramTextVerifier.readText(memory, 0, 0, 2))

        // Direct decoder on BG tiles gives different result (tile 0x21 → '!', tile 0x22 → '"')
        val direct = VramTextVerifier.readText(memory, 0, 0, 2, decoder = VramTextVerifier.DIRECT_ASCII_DECODER)
        assertEquals("!\"", direct)
    }

    @Test
    fun `readText throws when x exceeds visible width`() {
        val memory = mockMemory()
        assertThrows<IllegalArgumentException> {
            VramTextVerifier.readText(memory, 20, 0, 1)
        }
    }

    @Test
    fun `readText throws when y exceeds visible height`() {
        val memory = mockMemory()
        assertThrows<IllegalArgumentException> {
            VramTextVerifier.readText(memory, 0, 18, 1)
        }
    }

    @Test
    fun `readText throws when length overflows visible width`() {
        val memory = mockMemory()
        assertThrows<IllegalArgumentException> {
            VramTextVerifier.readText(memory, 15, 0, 10)
        }
    }

    // ── scrollAware tests ─────────────────────────────────────────────────────

    /**
     * Helper that writes tile data at a tilemap-absolute position (not viewport-relative).
     * Used to verify scroll-aware reads land on the correct tilemap address.
     */
    private fun writeTileAtAbsolute(
        patches: MutableList<Pair<Int, Int>>,
        tileChar: Char,
        tileX: Int,
        tileY: Int,
        layer: VramTextVerifier.TilemapLayer = VramTextVerifier.TilemapLayer.BACKGROUND,
    ) {
        val base = when (layer) {
            VramTextVerifier.TilemapLayer.BACKGROUND -> VramTextVerifier.BG_TILEMAP_BASE
            VramTextVerifier.TilemapLayer.WINDOW -> VramTextVerifier.WIN_TILEMAP_BASE
        }
        val tile = when (layer) {
            VramTextVerifier.TilemapLayer.BACKGROUND -> tileChar.code - 0x20
            VramTextVerifier.TilemapLayer.WINDOW -> tileChar.code
        }
        patches.add((base + tileY * VramTextVerifier.ROW_STRIDE + tileX) to tile)
    }

    @Test
    fun `scrollAware=false is identical to default behavior`() {
        val patches = mutableListOf<Pair<Int, Int>>()
        writeText(patches, "HELLO", 3, 5)
        val memory = mockMemory(*patches.toTypedArray())

        val withoutFlag = VramTextVerifier.readText(memory, 3, 5, 5)
        val withFalse = VramTextVerifier.readText(memory, 3, 5, 5, scrollAware = false)
        assertEquals(withoutFlag, withFalse)
        assertEquals("HELLO", withFalse)
    }

    @Test
    fun `scrollAware=true with SCX=64 shifts x by 8 tiles`() {
        // SCX=64 pixels → 64/8 = 8 tile columns offset
        // Viewport x=0 maps to tilemap x=8
        val patches = mutableListOf<Pair<Int, Int>>()
        // Write "OK" at tilemap absolute position (8, 0)
        writeTileAtAbsolute(patches, 'O', 8, 0)
        writeTileAtAbsolute(patches, 'K', 9, 0)
        // Set SCX register to 64
        patches.add(VramTextVerifier.SCX_REG_ADDR to 64)
        val memory = mockMemory(*patches.toTypedArray())

        val text = VramTextVerifier.readText(memory, 0, 0, 2, scrollAware = true)
        assertEquals("OK", text)
    }

    @Test
    fun `scrollAware=true with SCY=16 shifts y by 2 tiles`() {
        // SCY=16 pixels → 16/8 = 2 tile rows offset
        // Viewport y=0 maps to tilemap y=2
        val patches = mutableListOf<Pair<Int, Int>>()
        // Write "HI" at tilemap absolute position (0, 2)
        writeTileAtAbsolute(patches, 'H', 0, 2)
        writeTileAtAbsolute(patches, 'I', 1, 2)
        // Set SCY register to 16
        patches.add(VramTextVerifier.SCY_REG_ADDR to 16)
        val memory = mockMemory(*patches.toTypedArray())

        val text = VramTextVerifier.readText(memory, 0, 0, 2, scrollAware = true)
        assertEquals("HI", text)
    }

    @Test
    fun `scrollAware=true wraps x at tile 32 boundary`() {
        // Viewport x=18, SCX=120 pixels → 120/8 = 15 tile offset
        // Tilemap x = (18 + 15) AND 31 = 33 AND 31 = 1
        val patches = mutableListOf<Pair<Int, Int>>()
        // Write "W" at tilemap absolute position (1, 0)
        writeTileAtAbsolute(patches, 'W', 1, 0)
        // Set SCX=120
        patches.add(VramTextVerifier.SCX_REG_ADDR to 120)
        val memory = mockMemory(*patches.toTypedArray())

        val text = VramTextVerifier.readText(memory, 18, 0, 1, scrollAware = true)
        assertEquals("W", text)
    }

    @Test
    fun `scrollAware=true on WINDOW layer ignores SCX and SCY`() {
        // Window layer is independent from SCX/SCY — should read at absolute position
        val patches = mutableListOf<Pair<Int, Int>>()
        // Write "WIN" at window tilemap absolute position (0, 0)
        writeTileAtAbsolute(patches, 'W', 0, 0, VramTextVerifier.TilemapLayer.WINDOW)
        writeTileAtAbsolute(patches, 'I', 1, 0, VramTextVerifier.TilemapLayer.WINDOW)
        writeTileAtAbsolute(patches, 'N', 2, 0, VramTextVerifier.TilemapLayer.WINDOW)
        // Set SCX/SCY to non-zero values — should be ignored for WINDOW
        patches.add(VramTextVerifier.SCX_REG_ADDR to 64)
        patches.add(VramTextVerifier.SCY_REG_ADDR to 16)
        val memory = mockMemory(*patches.toTypedArray())

        // scrollAware=true on WINDOW should read at viewport position (0,0) without offset
        val text = VramTextVerifier.readText(
            memory, 0, 0, 3,
            layer = VramTextVerifier.TilemapLayer.WINDOW,
            scrollAware = true,
        )
        assertEquals("WIN", text)
    }

    @Test
    fun `readRow propagates scrollAware parameter`() {
        val patches = mutableListOf<Pair<Int, Int>>()
        // SCX=8 → 1 tile offset; row y=0 viewport maps to tilemap y=0
        // Write at tilemap absolute (1, 0)
        writeTileAtAbsolute(patches, 'A', 1, 0)
        patches.add(VramTextVerifier.SCX_REG_ADDR to 8)
        val memory = mockMemory(*patches.toTypedArray())

        val row = VramTextVerifier.readRow(memory, 0, scrollAware = true)
        assertEquals(20, row.length)
        // The first character (viewport x=0) should map to tilemap x=1 → 'A'
        assertEquals('A', row[0])
    }

    @Test
    fun `readAllRows propagates scrollAware parameter`() {
        val patches = mutableListOf<Pair<Int, Int>>()
        // SCY=8 → 1 tile row offset; viewport row 0 maps to tilemap row 1
        writeTileAtAbsolute(patches, 'Z', 0, 1)
        patches.add(VramTextVerifier.SCY_REG_ADDR to 8)
        val memory = mockMemory(*patches.toTypedArray())

        val rows = VramTextVerifier.readAllRows(memory, scrollAware = true)
        assertEquals(18, rows.size)
        // Row 0 (viewport) maps to tilemap row 1 → starts with 'Z'
        assertEquals('Z', rows[0][0])
    }

    @Test
    fun `findText propagates scrollAware parameter`() {
        val patches = mutableListOf<Pair<Int, Int>>()
        // SCX=8 → 1 tile offset; viewport x=0 maps to tilemap x=1
        // Write "FIND" at tilemap absolute (1, 3)
        for ((i, c) in "FIND".withIndex()) {
            writeTileAtAbsolute(patches, c, 1 + i, 3)
        }
        patches.add(VramTextVerifier.SCX_REG_ADDR to 8)
        val memory = mockMemory(*patches.toTypedArray())

        val pos = VramTextVerifier.findText(memory, "FIND", scrollAware = true)
        assertNotNull(pos)
        // Viewport x=0 → found at viewport column 0
        assertEquals(0, pos!!.first)
        assertEquals(3, pos.second)
    }

    @Test
    fun `findTextAnyLayer propagates scrollAware parameter`() {
        val patches = mutableListOf<Pair<Int, Int>>()
        // Write text at window tilemap absolute (0, 0) — scrollAware has no effect on WIN
        for ((i, c) in "TEST".withIndex()) {
            writeTileAtAbsolute(patches, c, i, 0, VramTextVerifier.TilemapLayer.WINDOW)
        }
        // Set SCX/SCY — these should NOT affect window reads
        patches.add(VramTextVerifier.SCX_REG_ADDR to 64)
        val memory = mockMemory(*patches.toTypedArray())

        val result = VramTextVerifier.findTextAnyLayer(memory, "TEST", scrollAware = true)
        assertNotNull(result)
        assertEquals(VramTextVerifier.TilemapLayer.WINDOW, result!!.third)
    }
}
