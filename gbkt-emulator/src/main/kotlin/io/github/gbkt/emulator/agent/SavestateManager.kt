/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator.agent

import io.github.gbkt.emulator.GbEmulator
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Saves and restores Game Boy emulator memory state to/from a binary file.
 *
 * Captures WRAM (0xC000–0xDFFF), OAM (0xFE00–0xFE9F), and HRAM (0xFF80–0xFFFE) — the three
 * general-purpose RAM regions that hold game state. ROM (0x0000–0x7FFF) and VRAM (0x8000–0x9FFF)
 * are not saved; ROM is always available from the cartridge and VRAM resets on scene transitions.
 *
 * **File format:**
 * ```
 * Offset   Size    Content
 * 0        4       Magic: 'G' 'B' 'S' 'T'
 * 4        8192    WRAM (0xC000–0xDFFF)
 * 8196     160     OAM  (0xFE00–0xFE9F)
 * 8356     127     HRAM (0xFF80–0xFFFE)
 * Total: 8483 bytes
 * ```
 *
 * The emulator must be paused before calling [save] or [load] to avoid capturing mid-frame state.
 */
object SavestateManager {

    /** First byte of WRAM address range. */
    const val WRAM_START = 0xC000

    /** Number of WRAM bytes (8KB). */
    const val WRAM_SIZE = 0x2000

    /** First byte of OAM address range. */
    const val OAM_START = 0xFE00

    /** Number of OAM bytes (40 sprites × 4 bytes). */
    const val OAM_SIZE = 0xA0

    /** First byte of HRAM address range. */
    const val HRAM_START = 0xFF80

    /** Number of HRAM bytes. */
    const val HRAM_SIZE = 0x7F

    /** Four-byte file magic: ASCII "GBST". */
    val MAGIC = byteArrayOf('G'.code.toByte(), 'B'.code.toByte(), 'S'.code.toByte(), 'T'.code.toByte())

    /**
     * Saves emulator memory state to [file].
     *
     * Writes WRAM + OAM + HRAM bytes to a binary file prefixed with the [MAGIC] header.
     *
     * @param emulator The emulator to snapshot. Must be paused.
     * @param file Destination file. Created or overwritten.
     * @throws IllegalArgumentException if the emulator is not paused.
     */
    fun save(emulator: GbEmulator, file: File) {
        require(emulator.isPaused()) {
            "Emulator must be paused before saving state. Call emulator.pause() first."
        }
        val memory = emulator.getMemory()
        DataOutputStream(file.outputStream().buffered()).use { out ->
            out.write(MAGIC)
            for (addr in WRAM_START until WRAM_START + WRAM_SIZE) {
                out.writeByte(memory.readByte(addr))
            }
            for (addr in OAM_START until OAM_START + OAM_SIZE) {
                out.writeByte(memory.readByte(addr))
            }
            for (addr in HRAM_START until HRAM_START + HRAM_SIZE) {
                out.writeByte(memory.readByte(addr))
            }
        }
    }

    /**
     * Restores emulator memory state from [file].
     *
     * Reads WRAM + OAM + HRAM bytes and writes them back via [GbEmulator.getMemory].
     *
     * @param emulator The emulator to restore into. Must be paused.
     * @param file Source file previously created by [save].
     * @throws IllegalArgumentException if the emulator is not paused or the file has wrong magic.
     */
    fun load(emulator: GbEmulator, file: File) {
        require(emulator.isPaused()) {
            "Emulator must be paused before loading state. Call emulator.pause() first."
        }
        val memory = emulator.getMemory()
        DataInputStream(file.inputStream().buffered()).use { inp ->
            val magic = ByteArray(4)
            inp.readFully(magic)
            require(magic.contentEquals(MAGIC)) {
                "Invalid savestate file: expected magic 'GBST' but got ${magic.map { it.toInt().and(0xFF).toString(16) }}"
            }
            for (addr in WRAM_START until WRAM_START + WRAM_SIZE) {
                memory.writeByte(addr, inp.readUnsignedByte())
            }
            for (addr in OAM_START until OAM_START + OAM_SIZE) {
                memory.writeByte(addr, inp.readUnsignedByte())
            }
            for (addr in HRAM_START until HRAM_START + HRAM_SIZE) {
                memory.writeByte(addr, inp.readUnsignedByte())
            }
        }
    }
}
