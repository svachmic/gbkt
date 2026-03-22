/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import io.github.gbkt.emulator.GbEmulator
import io.github.gbkt.emulator.MemoryAccess
import io.github.gbkt.emulator.debug.DebugLogEntry
import java.io.File
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SavestateManagerTest {

    @TempDir lateinit var tempDir: File

    /**
     * Creates a mock GbEmulator backed by a flat byte array.
     *
     * @param isPaused Whether the emulator reports as paused.
     * @param initMemory Initial memory patches to apply (address to value).
     */
    private fun mockEmulator(
        isPaused: Boolean = true,
        initMemory: Map<Int, Int> = emptyMap(),
    ): Pair<GbEmulator, IntArray> {
        val mem = IntArray(0x10000) { 0 }
        for ((addr, value) in initMemory) {
            mem[addr] = value
        }
        val emulator =
            object : GbEmulator {
                override fun start() = Unit

                override fun stop() = Unit

                override fun pause() = Unit

                override fun resume() = Unit

                override fun stepFrame() = Unit

                override fun setSpeed(multiplier: Float) = Unit

                override fun getFrameBuffer(): IntArray = IntArray(160 * 144)

                override fun getMemory(): MemoryAccess =
                    object : MemoryAccess {
                        override fun readByte(address: Int): Int = mem[address]

                        override fun writeByte(address: Int, value: Int) {
                            mem[address] = value
                        }
                    }

                override fun getDebugLog(): List<DebugLogEntry> = emptyList()

                override fun isRunning(): Boolean = !isPaused

                override fun isPaused(): Boolean = isPaused

                override val isHeadless: Boolean = true
            }
        return emulator to mem
    }

    @Test
    fun `saved file has correct total size`() {
        val (emulator, _) = mockEmulator()
        val file = File(tempDir, "state.gbst")
        SavestateManager.save(emulator, file)
        // 4 (magic) + 8192 (WRAM) + 160 (OAM) + 127 (HRAM) = 8483 bytes
        assertEquals(8483L, file.length())
    }

    @Test
    fun `saved file starts with GBST magic bytes`() {
        val (emulator, _) = mockEmulator()
        val file = File(tempDir, "state.gbst")
        SavestateManager.save(emulator, file)
        val magic = file.readBytes().take(4).toByteArray()
        assertArrayEquals(
            byteArrayOf('G'.code.toByte(), 'B'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte()),
            magic,
        )
    }

    @Test
    fun `round-trip restores WRAM bytes correctly`() {
        val wramPatch = mapOf(0xC000 to 0x42, 0xC100 to 0xFF, 0xDFFF to 0x7E)
        val (saveEmulator, _) = mockEmulator(initMemory = wramPatch)
        val file = File(tempDir, "state.gbst")
        SavestateManager.save(saveEmulator, file)

        // Load into a fresh emulator
        val (loadEmulator, loadMem) = mockEmulator()
        SavestateManager.load(loadEmulator, file)

        assertEquals(0x42, loadMem[0xC000])
        assertEquals(0xFF, loadMem[0xC100])
        assertEquals(0x7E, loadMem[0xDFFF])
    }

    @Test
    fun `round-trip restores OAM bytes correctly`() {
        val oamPatch = mapOf(0xFE00 to 0x10, 0xFE10 to 0x20, 0xFE9F to 0x30)
        val (saveEmulator, _) = mockEmulator(initMemory = oamPatch)
        val file = File(tempDir, "state.gbst")
        SavestateManager.save(saveEmulator, file)

        val (loadEmulator, loadMem) = mockEmulator()
        SavestateManager.load(loadEmulator, file)

        assertEquals(0x10, loadMem[0xFE00])
        assertEquals(0x20, loadMem[0xFE10])
        assertEquals(0x30, loadMem[0xFE9F])
    }

    @Test
    fun `round-trip restores HRAM bytes correctly`() {
        val hramPatch = mapOf(0xFF80 to 0xAA, 0xFFFE to 0xBB)
        val (saveEmulator, _) = mockEmulator(initMemory = hramPatch)
        val file = File(tempDir, "state.gbst")
        SavestateManager.save(saveEmulator, file)

        val (loadEmulator, loadMem) = mockEmulator()
        SavestateManager.load(loadEmulator, file)

        assertEquals(0xAA, loadMem[0xFF80])
        assertEquals(0xBB, loadMem[0xFFFE])
    }

    @Test
    fun `save throws when emulator is not paused`() {
        val (emulator, _) = mockEmulator(isPaused = false)
        val file = File(tempDir, "state.gbst")
        assertThrows(IllegalArgumentException::class.java) { SavestateManager.save(emulator, file) }
    }

    @Test
    fun `load throws when emulator is not paused`() {
        // First save a valid file with a paused emulator
        val (saveEmulator, _) = mockEmulator(isPaused = true)
        val file = File(tempDir, "state.gbst")
        SavestateManager.save(saveEmulator, file)

        // Now try to load into a running emulator
        val (runningEmulator, _) = mockEmulator(isPaused = false)
        assertThrows(IllegalArgumentException::class.java) {
            SavestateManager.load(runningEmulator, file)
        }
    }

    @Test
    fun `load throws on invalid magic bytes`() {
        val file = File(tempDir, "bad.gbst")
        file.writeBytes(ByteArray(8483) { 0 }) // all zeros, wrong magic
        val (emulator, _) = mockEmulator()
        assertThrows(IllegalArgumentException::class.java) { SavestateManager.load(emulator, file) }
    }
}
