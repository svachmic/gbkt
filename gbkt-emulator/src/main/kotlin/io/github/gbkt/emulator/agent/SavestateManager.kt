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
 * Captures SRAM (0xA000–0xBFFF), WRAM (0xC000–0xDFFF), OAM (0xFE00–0xFE9F), and HRAM
 * (0xFF80–0xFFFE) — all RAM regions that hold game state including banked save data. ROM
 * (0x0000–0x7FFF) and VRAM (0x8000–0x9FFF) are not saved; ROM is always available from the
 * cartridge and VRAM resets on scene transitions.
 *
 * **File format (GBS2):**
 *
 * ```
 * Offset   Size    Content
 * 0        4       Magic: 'G' 'B' 'S' '2'
 * 4        8192    SRAM (0xA000–0xBFFF, RAM bank 0)
 * 8196     8192    WRAM (0xC000–0xDFFF)
 * 16388    160     OAM  (0xFE00–0xFE9F)
 * 16548    127     HRAM (0xFF80–0xFFFE)
 * Total: 16675 bytes
 * ```
 *
 * **Breaking change (Phase 11.1 — SEED-016 NARROW fix):** Magic bumped GBST -> GBS2. Pre-fix .gbst
 * files are rejected by the MAGIC validation guard in [load].
 *
 * The emulator must be paused before calling [save] or [load] to avoid capturing mid-frame state.
 */
object SavestateManager {

    /** First byte of SRAM address range (RAM bank 0). */
    const val SRAM_START = 0xA000

    /** Number of SRAM bytes (8KB, one RAM bank). */
    const val SRAM_SIZE = 0x2000

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

    /** Four-byte file magic: ASCII "GBS2". */
    val MAGIC =
        byteArrayOf('G'.code.toByte(), 'B'.code.toByte(), 'S'.code.toByte(), '2'.code.toByte())

    /**
     * Saves emulator memory state to [file].
     *
     * Writes SRAM + WRAM + OAM + HRAM bytes to a binary file prefixed with the [MAGIC] header.
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
            for (addr in SRAM_START until SRAM_START + SRAM_SIZE) {
                out.writeByte(memory.readByte(addr))
            }
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
     * Reads SRAM + WRAM + OAM + HRAM bytes and writes them back via [GbEmulator.getMemory].
     *
     * **MBC5 ENABLE_RAM / DISABLE_RAM bracket (REVIEW CR-01 / Phase 11.1 gap-closure plan
     * 11.1-10):**
     *
     * MBC5 SRAM writes are gated on `ramWriteEnabled`. After any ROM that ends `save_game_saves`
     * with DISABLE_RAM, the gate is closed; `load()` opens it briefly so the SRAM restore loop's
     * writes are honoured, then re-closes it to the canonical-disabled state.
     *
     * Concretely: writing `0x0A` to address `0x0000` enables SRAM writes (`ramWriteEnabled = true`)
     * per CoffeeGB's `Mbc5.setByte` gate. Writing `0x00` (or any non-`0x0A` value) to `0x0000`
     * restores the canonical DISABLE_RAM state that Banks.kt's `save_game_saves` routine leaves
     * behind. Without this bracket every SRAM write in the restore loop is silently dropped, and
     * the savestate appears to restore correctly on the flat-IntArray mock but fails on real MBC5.
     *
     * @param emulator The emulator to restore into. Must be paused.
     * @param file Source file previously created by [save].
     * @throws IllegalArgumentException if the emulator is not paused or the file has wrong magic
     *   (GBS2).
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
                "Invalid savestate file: expected magic 'GBS2' but got " +
                    "${magic.map { it.toInt().and(0xFF).toString(16) }}"
            }
            // MBC5 ENABLE_RAM: write 0x0A to 0x0000 to open the ramWriteEnabled gate before the
            // SRAM restore loop. Without this, writes to 0xA000-0xBFFF are silently dropped by
            // the MBC5 gate after any game's save_game_saves routine ends with DISABLE_RAM.
            // See REVIEW CR-01 / Phase 11.1 gap-closure plan 11.1-10.
            memory.writeByte(0x0000, 0x0A)
            for (addr in SRAM_START until SRAM_START + SRAM_SIZE) {
                memory.writeByte(addr, inp.readUnsignedByte())
            }
            // MBC5 DISABLE_RAM: restore the canonical-disabled state (mirrors what Banks.kt's
            // save_game_saves leaves behind). Writing 0x00 to 0x0000 closes the ramWriteEnabled
            // gate.
            memory.writeByte(0x0000, 0x00)
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
