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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class VariableInspectorTest {

    @TempDir
    lateinit var tempDir: File

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

    private fun writeSym(lines: List<String>): File {
        val file = File(tempDir, "test.sym")
        file.writeText(lines.joinToString("\n"))
        return file
    }

    @Test
    fun `loadSymbols parses DEF lines and strips underscore prefix`() {
        val symFile =
            writeSym(
                listOf(
                    "DEF _score 00:C100",
                    "DEF _lives 00:C101",
                    "DEF _ignored_no_underscore C102",
                    "; comment line",
                    "DEF _ballDx 00:C103",
                )
            )
        val inspector = VariableInspector(mockMemory())
        inspector.loadSymbols(symFile)

        val names = inspector.listVariables()
        assertTrue(names.contains("score"), "should contain 'score'")
        assertTrue(names.contains("lives"), "should contain 'lives'")
        assertTrue(names.contains("ballDx"), "should contain 'ballDx'")
        assertEquals(3, names.size, "should load exactly 3 symbols")
    }

    @Test
    fun `readNamed returns correct value for known variable`() {
        val symFile = writeSym(listOf("DEF _score 00:C100"))
        val memory = mockMemory(0xC100 to 42)
        val inspector = VariableInspector(memory)
        inspector.loadSymbols(symFile)

        val value = inspector.readNamed("score")
        assertNotNull(value)
        assertEquals(42, value)
    }

    @Test
    fun `readNamed returns null for unknown variable`() {
        val inspector = VariableInspector(mockMemory())
        assertNull(inspector.readNamed("nonexistent"))
    }

    @Test
    fun `readNamedInt16 reads little-endian 16-bit value`() {
        // Little-endian: lo byte at addr, hi byte at addr+1
        // Value = 0x0201 = 513 decimal
        val symFile = writeSym(listOf("DEF _counter16 00:C200"))
        val memory = mockMemory(0xC200 to 0x01, 0xC201 to 0x02)
        val inspector = VariableInspector(memory)
        inspector.loadSymbols(symFile)

        val value = inspector.readNamedInt16("counter16")
        assertNotNull(value)
        assertEquals(0x0201, value)
    }

    @Test
    fun `readNamedInt16 returns null for unknown variable`() {
        val inspector = VariableInspector(mockMemory())
        assertNull(inspector.readNamedInt16("nonexistent"))
    }

    @Test
    fun `readAddress reads raw byte from specific address`() {
        val memory = mockMemory(0xC300 to 99)
        val inspector = VariableInspector(memory)
        assertEquals(99, inspector.readAddress(0xC300))
    }

    @Test
    fun `readAll returns snapshot of all loaded variables`() {
        val symFile =
            writeSym(
                listOf(
                    "DEF _score 00:C100",
                    "DEF _lives 00:C101",
                )
            )
        val memory = mockMemory(0xC100 to 10, 0xC101 to 3)
        val inspector = VariableInspector(memory)
        inspector.loadSymbols(symFile)

        val all = inspector.readAll()
        assertEquals(2, all.size)
        assertEquals(10, all["score"])
        assertEquals(3, all["lives"])
    }

    @Test
    fun `listVariables returns sorted names`() {
        val symFile =
            writeSym(
                listOf(
                    "DEF _score 00:C100",
                    "DEF _ballDx 00:C101",
                    "DEF _lives 00:C102",
                )
            )
        val inspector = VariableInspector(mockMemory())
        inspector.loadSymbols(symFile)

        val names = inspector.listVariables()
        assertEquals(listOf("ballDx", "lives", "score"), names)
    }

    @Test
    fun `constructor with symFile loads symbols immediately`() {
        val symFile = writeSym(listOf("DEF _hp 00:C050"))
        val inspector = VariableInspector(mockMemory(), symFile)
        assertTrue(inspector.listVariables().contains("hp"))
    }

    @Test
    fun `type inference dx and dy map to INT8`() {
        val symFile =
            writeSym(
                listOf(
                    "DEF _ballDx 00:C100",
                    "DEF _velDy 00:C101",
                )
            )
        val inspector = VariableInspector(mockMemory())
        inspector.loadSymbols(symFile)

        assertEquals("INT8", inspector.getSymbolType("ballDx"))
        assertEquals("INT8", inspector.getSymbolType("velDy"))
    }

    @Test
    fun `type inference 16 and addr map to UINT16`() {
        val symFile =
            writeSym(
                listOf(
                    "DEF _ptr16 00:C100",
                    "DEF _addr 00:C102",
                )
            )
        val inspector = VariableInspector(mockMemory())
        inspector.loadSymbols(symFile)

        assertEquals("UINT16", inspector.getSymbolType("ptr16"))
        assertEquals("UINT16", inspector.getSymbolType("addr"))
    }

    @Test
    fun `type inference defaults to UINT8`() {
        val symFile = writeSym(listOf("DEF _score 00:C100"))
        val inspector = VariableInspector(mockMemory())
        inspector.loadSymbols(symFile)

        assertEquals("UINT8", inspector.getSymbolType("score"))
    }

    @Test
    fun `loadSymbols parses GBDK noi format with 0x prefix and double underscores`() {
        // GBDK .noi format: DEF __symbolName 0xADDR (double underscore, hex with 0x prefix)
        val symFile =
            writeSym(
                listOf(
                    "DEF __p1Score 0xC0D5",
                    "DEF __p2Score 0xC0D6",
                    "DEF __ballDx 0xC0D7",
                )
            )
        val memory = mockMemory(0xC0D5 to 3, 0xC0D6 to 1, 0xC0D7 to 1)
        val inspector = VariableInspector(memory)
        inspector.loadSymbols(symFile)

        val names = inspector.listVariables()
        assertTrue(names.contains("p1Score"), "should strip double underscore: p1Score")
        assertTrue(names.contains("p2Score"), "should strip double underscore: p2Score")
        assertTrue(names.contains("ballDx"), "should strip double underscore: ballDx")
        assertEquals(3, names.size, "should load exactly 3 symbols from noi format")

        // Verify values are read at correct addresses
        assertEquals(3, inspector.readNamed("p1Score"), "p1Score should read value at 0xC0D5")
        assertEquals(1, inspector.readNamed("p2Score"), "p2Score should read value at 0xC0D6")
    }

    @Test
    fun `loadSymbols filters out non-WRAM addresses`() {
        val symFile =
            writeSym(
                listOf(
                    "DEF _update_joypad 0x0200",    // ROM function — below WRAM
                    "DEF _P1_REG 0xFF00",            // I/O register — above WRAM
                    "DEF _banked_func 0x14000",      // Banked address — exceeds 16-bit
                    "DEF _score 0xC0D5",             // WRAM — should be kept
                    "DEF _lives 0xC0D6",             // WRAM — should be kept
                    "DEF _highAddr 0xE000",          // Above WRAM end — should be excluded
                    "DEF _lowWram 0xBFFF",           // Just below WRAM start — should be excluded
                )
            )
        val inspector = VariableInspector(mockMemory())
        inspector.loadSymbols(symFile)

        val names = inspector.listVariables()
        assertEquals(2, names.size, "Only WRAM symbols should be loaded")
        assertTrue(names.contains("score"))
        assertTrue(names.contains("lives"))
    }

    @Test
    fun `readAll works without try-catch for WRAM-only symbols`() {
        val symFile =
            writeSym(
                listOf(
                    "DEF _score 0xC0D5",
                    "DEF _lives 0xC0D6",
                )
            )
        val memory = mockMemory(0xC0D5 to 42, 0xC0D6 to 3)
        val inspector = VariableInspector(memory)
        inspector.loadSymbols(symFile)

        val all = inspector.readAll()
        assertEquals(2, all.size)
        assertEquals(42, all["score"])
        assertEquals(3, all["lives"])
    }

    @Test
    fun `invalid sym line is skipped gracefully`() {
        val symFile =
            writeSym(
                listOf(
                    "DEF _good 00:C100",
                    "DEF _badhex 00:ZZZZ",
                    "NOTDEF _other 00:C102",
                    "DEF _alsogood 00:C103",
                )
            )
        val inspector = VariableInspector(mockMemory())
        inspector.loadSymbols(symFile)

        val names = inspector.listVariables()
        assertTrue(names.contains("good"))
        assertTrue(names.contains("alsogood"))
        assertEquals(2, names.size)
    }

    // ── Type-aware reading tests ──────────────────────────────────────────────

    @Test
    fun `readAll returns unsigned 200 for UINT8 variable`() {
        val symFile = writeSym(listOf("DEF _score 00:C100"))
        val memory = mockMemory(0xC100 to 200)
        val inspector = VariableInspector(memory)
        inspector.loadSymbols(symFile)

        // score defaults to UINT8 (no dx/dy/vel/dir/16/addr/ptr in name)
        assertEquals(200, inspector.readAll()["score"])
    }

    @Test
    fun `readAll returns signed -56 for INT8 variable with byte value 200`() {
        // 200 > 127, so signed: 200 - 256 = -56
        val symFile = writeSym(listOf("DEF _ballDx 00:C100"))
        val memory = mockMemory(0xC100 to 200)
        val inspector = VariableInspector(memory)
        inspector.loadSymbols(symFile)

        // ballDx is inferred as INT8 due to "dx" in name
        assertEquals(-56, inspector.readAll()["ballDx"])
    }

    @Test
    fun `readAll returns 770 for UINT16 variable with lo=0x02 hi=0x03`() {
        // little-endian: lo=0x02 at addr, hi=0x03 at addr+1 → (0x03 shl 8) or 0x02 = 0x0302 = 770
        val symFile = writeSym(listOf("DEF _ptr16 00:C200"))
        val memory = mockMemory(0xC200 to 0x02, 0xC201 to 0x03)
        val inspector = VariableInspector(memory)
        inspector.loadSymbols(symFile)

        // ptr16 is inferred as UINT16 due to "16" in name
        assertEquals(770, inspector.readAll()["ptr16"])
    }

    @Test
    fun `readAll returns -1 for INT16 variable with lo=0xFF hi=0xFF`() {
        // 0xFFFF = 65535 > 32767, so signed: 65535 - 65536 = -1
        val symFile = writeSym(listOf("DEF _counter16 00:C200"))
        val memory = mockMemory(0xC200 to 0xFF, 0xC201 to 0xFF)
        val inspector = VariableInspector(memory)
        inspector.loadSymbols(symFile)

        // Override type to INT16 to test signed 16-bit
        inspector.overrideTypes(mapOf("counter16" to "INT16"))
        assertEquals(-1, inspector.readAll()["counter16"])
    }

    @Test
    fun `overrideTypes changes UINT8 to INT8 and readAll returns signed value`() {
        val symFile = writeSym(listOf("DEF _score 00:C100"))
        val memory = mockMemory(0xC100 to 200)
        val inspector = VariableInspector(memory)
        inspector.loadSymbols(symFile)

        // Before override: UINT8 → 200
        assertEquals(200, inspector.readAll()["score"])

        // After override to INT8: 200 > 127 → 200 - 256 = -56
        inspector.overrideTypes(mapOf("score" to "INT8"))
        assertEquals(-56, inspector.readAll()["score"])
    }

    @Test
    fun `overrideTypes changes UINT8 to UINT16 and readAll reads 2 bytes`() {
        val symFile = writeSym(listOf("DEF _score 00:C100"))
        val memory = mockMemory(0xC100 to 0x34, 0xC101 to 0x12)
        val inspector = VariableInspector(memory)
        inspector.loadSymbols(symFile)

        // Before override: UINT8 → 0x34 = 52
        assertEquals(52, inspector.readAll()["score"])

        // After override to UINT16: (0x12 shl 8) or 0x34 = 0x1234 = 4660
        inspector.overrideTypes(mapOf("score" to "UINT16"))
        assertEquals(0x1234, inspector.readAll()["score"])
    }

    @Test
    fun `WRAM boundary safety for UINT16 at address 0xDFFF`() {
        // UINT16 at WRAM_END (0xDFFF) — reading address+1 = 0xE000 is out of WRAM
        // Should fall back to returning only the low byte
        val symFile = writeSym(listOf("DEF _addr 00:DFFF"))
        val memory = mockMemory(0xDFFF to 0x42)
        val inspector = VariableInspector(memory)
        inspector.loadSymbols(symFile)

        // addr inferred as UINT16 (contains "addr")
        // boundary check: address + 1 > WRAM_END → return raw low byte = 0x42
        assertEquals(0x42, inspector.readAll()["addr"])
    }
}
