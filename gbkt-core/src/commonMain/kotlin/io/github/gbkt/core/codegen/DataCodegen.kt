/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.Channel
import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.EnvelopeDirection
import io.github.gbkt.core.NoiseWidth
import io.github.gbkt.core.SoundEffect
import io.github.gbkt.core.SweepDirection

// =============================================================================
// DATA CODE GENERATION
// Tile data, map data, sound data, palette data
// =============================================================================

internal fun CodeGenerator.generateTileData() {
    if (game.tileData.isEmpty()) return

    line("// === Tile Data ===")
    for (data in game.tileData) {
        line("const unsigned char ${data.name}_tiles[] = {")
        indent++
        for ((index, tile) in data.data.withIndex()) {
            val hex =
                tile.joinToString(", ") {
                    "0x${(it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()}"
                }
            val comma = if (index < data.data.size - 1) "," else ""
            line("$hex$comma")
        }
        indent--
        line("};")
        line("#define ${data.name.uppercase()}_TILE_COUNT ${data.tileCount}")
        line()
    }
}

internal fun CodeGenerator.generateMapData() {
    if (game.mapData.isEmpty()) return

    line("// === Map Data ===")
    for (map in game.mapData) {
        line("const unsigned char ${map.name}_map[] = {")
        indent++

        // Output map data in rows for readability
        for (row in 0 until map.height) {
            val start = row * map.width
            val end = minOf(start + map.width, map.data.size)
            val rowData = map.data.slice(start until end)
            val hex =
                rowData.joinToString(", ") {
                    "0x${(it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()}"
                }
            val comma = if (row < map.height - 1) "," else ""
            line("$hex$comma  // row $row")
        }

        indent--
        line("};")
        line("#define ${map.name.uppercase()}_WIDTH ${map.width}")
        line("#define ${map.name.uppercase()}_HEIGHT ${map.height}")

        // Generate collision map if available
        if (map.collisionData != null) {
            line()
            line("// Collision map (0 = walkable, >0 = blocked)")
            line("const unsigned char ${map.name}_collision[] = {")
            indent++

            // Output collision data in rows for readability
            for (row in 0 until map.height) {
                val start = row * map.width
                val end = minOf(start + map.width, map.collisionData.size)
                val rowData = map.collisionData.slice(start until end)
                val hex =
                    rowData.joinToString(", ") {
                        "0x${(it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()}"
                    }
                val comma = if (row < map.height - 1) "," else ""
                line("$hex$comma  // row $row")
            }

            indent--
            line("};")
        }
        line()
    }
}

internal fun CodeGenerator.generateSoundData() {
    if (game.soundEffects.isEmpty() && game.music.isEmpty()) return

    line("// === Sound Data ===")
    line()

    // Generate extern declarations for music
    for (music in game.music) {
        line("extern const hUGESong_t ${music.name}_song;")
    }
    if (game.music.isNotEmpty()) line()

    // Generate sound effect play functions
    for (sfx in game.soundEffects) {
        generateSoundEffectFunction(sfx)
    }
}

private fun CodeGenerator.generateSoundEffectFunction(sfx: SoundEffect) {
    val regs = sfx.registers

    block("void play_${sfx.name}(void)") {
        when (sfx.channel) {
            Channel.PULSE1 -> {
                // NR10: Sweep
                val sweepByte =
                    if (regs.sweep != null) {
                        val s = regs.sweep
                        (s.time shl 4) or
                            (if (s.direction == SweepDirection.DECREASE) 0x08 else 0) or
                            s.shift
                    } else 0
                line("NR10_REG = 0x${sweepByte.toString(16).padStart(2, '0').uppercase()};")

                // NR11: Duty/Length
                val dutyByte = (regs.duty.bits shl 6) or (regs.length and 0x3F)
                line("NR11_REG = 0x${dutyByte.toString(16).padStart(2, '0').uppercase()};")

                // NR12: Envelope
                val envByte =
                    if (regs.envelope != null) {
                        val e = regs.envelope
                        (e.volume shl 4) or
                            (if (e.direction == EnvelopeDirection.INCREASE) 0x08 else 0) or
                            e.pace
                    } else 0xF0
                line("NR12_REG = 0x${envByte.toString(16).padStart(2, '0').uppercase()};")

                // NR13/NR14: Frequency
                val freqLow = regs.frequency and 0xFF
                val freqHigh =
                    ((regs.frequency shr 8) and 0x07) or
                        (if (regs.trigger) 0x80 else 0) or
                        (if (regs.lengthEnable) 0x40 else 0)
                line("NR13_REG = 0x${freqLow.toString(16).padStart(2, '0').uppercase()};")
                line("NR14_REG = 0x${freqHigh.toString(16).padStart(2, '0').uppercase()};")
            }
            Channel.PULSE2 -> {
                // NR21: Duty/Length
                val dutyByte = (regs.duty.bits shl 6) or (regs.length and 0x3F)
                line("NR21_REG = 0x${dutyByte.toString(16).padStart(2, '0').uppercase()};")

                // NR22: Envelope
                val envByte =
                    if (regs.envelope != null) {
                        val e = regs.envelope
                        (e.volume shl 4) or
                            (if (e.direction == EnvelopeDirection.INCREASE) 0x08 else 0) or
                            e.pace
                    } else 0xF0
                line("NR22_REG = 0x${envByte.toString(16).padStart(2, '0').uppercase()};")

                // NR23/NR24: Frequency
                val freqLow = regs.frequency and 0xFF
                val freqHigh =
                    ((regs.frequency shr 8) and 0x07) or
                        (if (regs.trigger) 0x80 else 0) or
                        (if (regs.lengthEnable) 0x40 else 0)
                line("NR23_REG = 0x${freqLow.toString(16).padStart(2, '0').uppercase()};")
                line("NR24_REG = 0x${freqHigh.toString(16).padStart(2, '0').uppercase()};")
            }
            Channel.WAVE -> {
                // NR30: DAC enable
                line("NR30_REG = 0x80;")

                // Load waveform if specified
                if (regs.waveform != null && regs.waveform.size == 16) {
                    for (i in 0 until 16) {
                        val byte = regs.waveform[i].toInt() and 0xFF
                        line(
                            "*((UINT8*)0xFF30 + $i) = 0x${byte.toString(16).padStart(2, '0').uppercase()};"
                        )
                    }
                }

                // NR31: Length
                line("NR31_REG = ${regs.length};")

                // NR32: Output level
                line(
                    "NR32_REG = 0x${(regs.outputLevel.bits shl 5).toString(16).padStart(2, '0').uppercase()};"
                )

                // NR33/NR34: Frequency
                val freqLow = regs.frequency and 0xFF
                val freqHigh =
                    ((regs.frequency shr 8) and 0x07) or
                        (if (regs.trigger) 0x80 else 0) or
                        (if (regs.lengthEnable) 0x40 else 0)
                line("NR33_REG = 0x${freqLow.toString(16).padStart(2, '0').uppercase()};")
                line("NR34_REG = 0x${freqHigh.toString(16).padStart(2, '0').uppercase()};")
            }
            Channel.NOISE -> {
                // NR41: Length
                line("NR41_REG = ${regs.length and 0x3F};")

                // NR42: Envelope
                val envByte =
                    if (regs.envelope != null) {
                        val e = regs.envelope
                        (e.volume shl 4) or
                            (if (e.direction == EnvelopeDirection.INCREASE) 0x08 else 0) or
                            e.pace
                    } else 0xF0
                line("NR42_REG = 0x${envByte.toString(16).padStart(2, '0').uppercase()};")

                // NR43: Noise parameters
                val noiseByte =
                    (regs.clockShift shl 4) or
                        (if (regs.widthMode == NoiseWidth.SEVEN_BIT) 0x08 else 0) or
                        regs.divisor
                line("NR43_REG = 0x${noiseByte.toString(16).padStart(2, '0').uppercase()};")

                // NR44: Trigger
                val triggerByte =
                    (if (regs.trigger) 0x80 else 0) or (if (regs.lengthEnable) 0x40 else 0)
                line("NR44_REG = 0x${triggerByte.toString(16).padStart(2, '0').uppercase()};")
            }
        }
    }
    line()
}

internal fun CodeGenerator.generatePaletteData() {
    if (!game.config.gbcSupport || game.palettes.isEmpty()) return

    line("// === GBC Palette Data ===")
    for (palette in game.palettes) {
        line("UINT16 ${palette.name}_pal[] = { ${palette.toCArrayLiteral()} };")
    }
    line()
}
