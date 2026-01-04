/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.Checksum
import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.SaveData
import io.github.gbkt.core.SaveFieldType

// =============================================================================
// SAVE DATA CODE GENERATION
// =============================================================================

internal fun CodeGenerator.generateSaveData() {
    val save = game.saveData ?: return

    line("// === Save Data ===")
    line("#define SRAM_BASE 0xA000")
    line("#define ${save.name.uppercase()}_SLOT_SIZE ${save.slotSize}u")
    line("#define ${save.name.uppercase()}_SLOT_COUNT ${save.config.slots}u")

    // Magic bytes
    if (save.config.magic != null) {
        val magicBytes =
            save.config.magic
                .take(4)
                .padEnd(4, '\u0000')
                .map { "0x${it.code.toString(16).padStart(2, '0').uppercase()}" }
                .joinToString(", ")
        line("#define ${save.name.uppercase()}_MAGIC { $magicBytes }")
    }
    line("#define ${save.name.uppercase()}_VERSION ${save.config.version}u")
    line()

    // Generate save data structure
    line("typedef struct {")
    indent++
    if (save.config.magic != null) {
        line("UINT8 magic[4];")
    }
    line("UINT8 version;")
    line("UINT8 reserved;")

    for (field in save.fields) {
        when (field.type) {
            SaveFieldType.ARRAY,
            SaveFieldType.STRING -> line("${field.type.cType} ${field.name}[${field.arraySize}];")
            else -> line("${field.type.cType} ${field.name};")
        }
    }

    if (save.config.checksum != Checksum.NONE) {
        val checksumType = if (save.config.checksum == Checksum.SUM16) "UINT16" else "UINT8"
        line("$checksumType checksum;")
    }
    indent--
    line("} ${save.name}_t;")
    line()

    // RAM buffer and state
    line("static ${save.name}_t ${save.name}_data;")
    line("static UINT8 ${save.name}_current_slot;")
    line()

    // Generate helper functions
    generateSaveChecksumFunction(save)
    generateSaveValidateFunction(save)
    generateSaveLoadFunction(save)
    generateSaveSaveFunction(save)
    generateSaveEraseFunction(save)
    generateSaveCopyFunction(save)
}

private fun CodeGenerator.generateSaveChecksumFunction(save: SaveData) {
    if (save.config.checksum == Checksum.NONE) return

    val name = save.name
    when (save.config.checksum) {
        Checksum.XOR -> {
            block("static UINT8 ${name}_calc_checksum(UINT8 *data, UINT8 len)") {
                line("UINT8 sum = 0;")
                line("for (UINT8 i = 0; i < len; i++) sum ^= data[i];")
                line("return sum;")
            }
        }
        Checksum.CRC8 -> {
            block("static UINT8 ${name}_calc_checksum(UINT8 *data, UINT8 len)") {
                line("// CRC-8-CCITT polynomial 0x07")
                line("UINT8 crc = 0xFF;")
                block("for (UINT8 i = 0; i < len; i++)") {
                    line("crc ^= data[i];")
                    block("for (UINT8 j = 0; j < 8; j++)") {
                        line("crc = (crc & 0x80) ? ((crc << 1) ^ 0x07) : (crc << 1);")
                    }
                }
                line("return crc;")
            }
        }
        Checksum.SUM16 -> {
            block("static UINT16 ${name}_calc_checksum(UINT8 *data, UINT8 len)") {
                line("UINT16 sum = 0;")
                line("for (UINT8 i = 0; i < len; i++) sum += data[i];")
                line("return sum;")
            }
        }
        Checksum.NONE -> {}
    }
    line()
}

private fun CodeGenerator.generateSaveValidateFunction(save: SaveData) {
    val name = save.name
    block("static UINT8 ${name}_validate(UINT8 slot)") {
        line("UINT8 *ptr = (UINT8*)(SRAM_BASE + (slot * ${name.uppercase()}_SLOT_SIZE));")
        line("ENABLE_RAM;")

        if (save.config.magic != null) {
            line("// Check magic bytes")
            line("static const UINT8 expected_magic[] = ${name.uppercase()}_MAGIC;")
            block("for (UINT8 i = 0; i < 4; i++)") {
                line("if (ptr[i] != expected_magic[i]) { DISABLE_RAM; return 0; }")
            }
        }

        if (save.config.checksum != Checksum.NONE) {
            line("// Verify checksum")
            line("${name}_t *data = (${name}_t*)ptr;")
            val checksumSize = save.slotSize - save.config.checksum.size
            line("if (data->checksum != ${name}_calc_checksum(ptr, ${checksumSize}u)) {")
            indent++
            line("DISABLE_RAM; return 0;")
            indent--
            line("}")
        }

        line("DISABLE_RAM;")
        line("return 1;")
    }
    line()
}

private fun CodeGenerator.generateSaveLoadFunction(save: SaveData) {
    val name = save.name
    block("static void ${name}_load(UINT8 slot)") {
        line("UINT8 *src = (UINT8*)(SRAM_BASE + (slot * ${name.uppercase()}_SLOT_SIZE));")
        line("UINT8 *dst = (UINT8*)&${name}_data;")
        line("${name}_current_slot = slot;")
        line()
        line("ENABLE_RAM;")
        line("for (UINT8 i = 0; i < sizeof(${name}_t); i++) dst[i] = src[i];")
        line("DISABLE_RAM;")
    }
    line()
}

private fun CodeGenerator.generateSaveSaveFunction(save: SaveData) {
    val name = save.name
    block("static void ${name}_save(UINT8 slot)") {
        line("UINT8 *dst = (UINT8*)(SRAM_BASE + (slot * ${name.uppercase()}_SLOT_SIZE));")
        line("UINT8 *src = (UINT8*)&${name}_data;")
        line("${name}_current_slot = slot;")
        line()

        // Set header before saving
        if (save.config.magic != null) {
            line("// Set magic bytes")
            line("static const UINT8 magic[] = ${name.uppercase()}_MAGIC;")
            line("for (UINT8 i = 0; i < 4; i++) ${name}_data.magic[i] = magic[i];")
        }
        line("${name}_data.version = ${name.uppercase()}_VERSION;")
        line("${name}_data.reserved = 0;")

        if (save.config.checksum != Checksum.NONE) {
            val checksumSize = save.slotSize - save.config.checksum.size
            line("${name}_data.checksum = ${name}_calc_checksum(src, ${checksumSize}u);")
        }
        line()

        line("ENABLE_RAM;")
        line("for (UINT8 i = 0; i < sizeof(${name}_t); i++) dst[i] = src[i];")
        line("DISABLE_RAM;")
    }
    line()
}

private fun CodeGenerator.generateSaveEraseFunction(save: SaveData) {
    val name = save.name
    block("static void ${name}_erase(UINT8 slot)") {
        line("UINT8 *dst = (UINT8*)(SRAM_BASE + (slot * ${name.uppercase()}_SLOT_SIZE));")
        line("ENABLE_RAM;")
        line("for (UINT8 i = 0; i < ${name.uppercase()}_SLOT_SIZE; i++) dst[i] = 0xFF;")
        line("DISABLE_RAM;")
    }
    line()
}

private fun CodeGenerator.generateSaveCopyFunction(save: SaveData) {
    val name = save.name
    block("static void ${name}_copy(UINT8 from_slot, UINT8 to_slot)") {
        line("UINT8 *src = (UINT8*)(SRAM_BASE + (from_slot * ${name.uppercase()}_SLOT_SIZE));")
        line("UINT8 *dst = (UINT8*)(SRAM_BASE + (to_slot * ${name.uppercase()}_SLOT_SIZE));")
        line("ENABLE_RAM;")
        line("for (UINT8 i = 0; i < ${name.uppercase()}_SLOT_SIZE; i++) dst[i] = src[i];")
        line("DISABLE_RAM;")
    }
    line()
}
