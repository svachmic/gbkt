/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.emulator

import java.io.File
import java.nio.file.Path

/**
 * Shared test utility for creating minimal valid Game Boy ROMs.
 *
 * Coffee-GB with [eu.rekawek.coffeegb.core.Gameboy.BootstrapMode.SKIP] bypasses boot ROM
 * validation, so we only need a valid cartridge header (type, size, checksum).
 */
object TestRomFactory {
    /**
     * Creates a minimal valid 32KB Game Boy ROM with a correct header checksum.
     *
     * @param dir Directory to write the ROM file into.
     * @param name ROM file name (default: "test.gb").
     * @param title Game Boy title string (max 11 chars, default: "GBKT TEST").
     */
    fun createMinimalRom(dir: Path, name: String = "test.gb", title: String = "GBKT TEST"): File {
        val romBytes = ByteArray(0x8000) // 32KB — smallest valid ROM
        romBytes[0x147] = 0x00 // Cartridge type: ROM ONLY
        romBytes[0x148] = 0x00 // ROM size: 32KB
        romBytes[0x149] = 0x00 // RAM size: None
        title.take(11).forEachIndexed { i, c -> romBytes[0x134 + i] = c.code.toByte() }
        var checksum = 0
        for (i in 0x134..0x14C) {
            checksum = checksum - romBytes[i] - 1
        }
        romBytes[0x14D] = checksum.toByte()

        val file = dir.resolve(name).toFile()
        file.writeBytes(romBytes)
        return file
    }
}
