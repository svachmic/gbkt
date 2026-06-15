/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.ast.CArray
import io.github.gbkt.backend.gbdk.codegen.ast.CArrayAccess
import io.github.gbkt.backend.gbdk.codegen.ast.CBinaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CComment
import io.github.gbkt.backend.gbdk.codegen.ast.CExprStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CFor
import io.github.gbkt.backend.gbdk.codegen.ast.CFunction
import io.github.gbkt.backend.gbdk.codegen.ast.CIf
import io.github.gbkt.backend.gbdk.codegen.ast.CLiteral
import io.github.gbkt.backend.gbdk.codegen.ast.CParam
import io.github.gbkt.backend.gbdk.codegen.ast.CRawExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CStatement
import io.github.gbkt.backend.gbdk.codegen.ast.CU8
import io.github.gbkt.backend.gbdk.codegen.ast.CUnaryExpr
import io.github.gbkt.backend.gbdk.codegen.ast.CVar
import io.github.gbkt.backend.gbdk.codegen.ast.CVarDecl
import io.github.gbkt.backend.gbdk.codegen.ast.CVoid
import io.github.gbkt.core.ir.DialogChoice
import io.github.gbkt.core.ir.EnvelopeConfig
import io.github.gbkt.core.ir.EnvelopeDirection
import io.github.gbkt.core.ir.FadeOp
import io.github.gbkt.core.ir.ForOp
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.MusicPause
import io.github.gbkt.core.ir.MusicPlay
import io.github.gbkt.core.ir.MusicResume
import io.github.gbkt.core.ir.MusicStop
import io.github.gbkt.core.ir.PlaySound
import io.github.gbkt.core.ir.ScriptOp
import io.github.gbkt.core.ir.SoundChannel
import io.github.gbkt.core.ir.SoundEffectDef
import io.github.gbkt.core.ir.SoundRegisters
import io.github.gbkt.core.ir.SweepDirection
import io.github.gbkt.core.ir.WhileOp

// =============================================================================
// SOUND VISITOR
// Generates C code for the sound driver: channel state arrays, sound driver
// update function, play_sound() core function, and per-preset wrapper functions
// with NRxx register writes.
//
// All generated code is C89-compliant and HOME-resident (bank 0).
// =============================================================================

/**
 * Generates all C code for the sound system from a [GameIR].
 *
 * Produces:
 * 1. Sound driver channel state arrays (_sound_channels, _sound_priority, _sound_duration)
 * 2. sound_driver_update() — frame-based channel duration management
 * 3. play_sound(id, channel, priority, duration) — core driver with priority preemption
 * 4. play_sound_<id>() — per-preset wrapper functions with NRxx register writes
 *
 * @param gameIR The full game IR. Sound effects and scene scripts are scanned for PlaySound ops.
 */
class SoundVisitor(private val gameIR: GameIR) {

    /**
     * Generate global variable declarations for the sound driver channel state.
     *
     * Three arrays track the state of GB's 4 sound channels (CH1-pulse, CH2-pulse, CH3-wave,
     * CH4-noise):
     * - `_sound_channels[4]`: which sound is playing on each channel (0xFF = none)
     * - `_sound_priority[4]`: priority level per channel (higher priority is harder to preempt)
     * - `_sound_duration[4]`: remaining frames per channel (0 = free)
     */
    fun buildSoundDriverGlobals(): List<CVarDecl> {
        val arrayType = CArray(CU8, 4)
        return listOf(
            CVarDecl(
                name = "_sound_channels",
                type = arrayType,
                initializer = CRawExpr("{0xFF, 0xFF, 0xFF, 0xFF}"),
            ),
            CVarDecl(
                name = "_sound_priority",
                type = arrayType,
                initializer = CRawExpr("{0, 0, 0, 0}"),
            ),
            CVarDecl(
                name = "_sound_duration",
                type = arrayType,
                initializer = CRawExpr("{0, 0, 0, 0}"),
            ),
        )
    }

    /**
     * Generate all sound driver and preset wrapper functions for the HOME bank.
     *
     * Functions generated:
     * 1. `sound_driver_update()` — decrements channel durations, marks free channels
     * 2. `play_sound(id, channel, priority, duration)` — core driver with priority preemption
     * 3. `play_sound_<id>()` — per-preset wrapper for each unique PlaySound ID in the game
     *
     * All functions are HOME-resident (bank 0) per RESEARCH.md pitfall 4 — sound functions called
     * from banked scene code require HOME residency so GBDK can call them without bank switching.
     */
    fun buildSoundFunctions(): List<CFunction> {
        val functions = mutableListOf<CFunction>()

        // 1. sound_driver_update() — called once per frame to age channel durations
        val chVar = CVar("ch")
        val durationArr = CVar("_sound_duration")
        val channelsArr = CVar("_sound_channels")
        functions +=
            CFunction(
                name = "sound_driver_update",
                returnType = CVoid,
                body =
                    buildList {
                        // C89: declare loop variable before for loop
                        add(CVarDecl("ch", CU8, initializer = null))
                        add(
                            CFor(
                                init = CExprStatement(CBinaryExpr(chVar, "=", CLiteral(0))),
                                condition = CBinaryExpr(chVar, "<", CLiteral(4)),
                                increment = CUnaryExpr("++", chVar),
                                body =
                                    listOf(
                                        CIf(
                                            condition =
                                                CBinaryExpr(
                                                    CArrayAccess(durationArr, chVar),
                                                    ">",
                                                    CLiteral(0),
                                                ),
                                            thenBody =
                                                listOf(
                                                    CExprStatement(
                                                        CUnaryExpr(
                                                            "--",
                                                            CArrayAccess(durationArr, chVar),
                                                        )
                                                    ),
                                                    CIf(
                                                        condition =
                                                            CBinaryExpr(
                                                                CArrayAccess(durationArr, chVar),
                                                                "==",
                                                                CLiteral(0),
                                                            ),
                                                        thenBody =
                                                            listOf(
                                                                CExprStatement(
                                                                    CBinaryExpr(
                                                                        CArrayAccess(
                                                                            channelsArr,
                                                                            chVar,
                                                                        ),
                                                                        "=",
                                                                        CLiteral(0xFF),
                                                                    )
                                                                )
                                                            ),
                                                    ),
                                                ),
                                        )
                                    ),
                            )
                        )
                    },
                sectionComment = "Sound driver (channel allocation with priority preemption)",
            )

        // 2. play_sound(id, channel, priority, duration) — core driver function
        val channelVar = CVar("channel")
        val priorityVar = CVar("priority")
        functions +=
            CFunction(
                name = "play_sound",
                returnType = CVoid,
                params =
                    listOf(
                        CParam("sound_id", CU8),
                        CParam("channel", CU8),
                        CParam("priority", CU8),
                        CParam("duration", CU8),
                    ),
                body =
                    buildList {
                        // if (_sound_duration[channel] == 0 || priority >=
                        // _sound_priority[channel]) {
                        add(
                            CIf(
                                condition =
                                    CBinaryExpr(
                                        CBinaryExpr(
                                            CArrayAccess(CVar("_sound_duration"), channelVar),
                                            "==",
                                            CLiteral(0),
                                        ),
                                        "||",
                                        CBinaryExpr(
                                            priorityVar,
                                            ">=",
                                            CArrayAccess(CVar("_sound_priority"), channelVar),
                                        ),
                                    ),
                                thenBody =
                                    listOf(
                                        CExprStatement(
                                            CBinaryExpr(
                                                CArrayAccess(CVar("_sound_channels"), channelVar),
                                                "=",
                                                CVar("sound_id"),
                                            )
                                        ),
                                        CExprStatement(
                                            CBinaryExpr(
                                                CArrayAccess(CVar("_sound_priority"), channelVar),
                                                "=",
                                                priorityVar,
                                            )
                                        ),
                                        CExprStatement(
                                            CBinaryExpr(
                                                CArrayAccess(CVar("_sound_duration"), channelVar),
                                                "=",
                                                CVar("duration"),
                                            )
                                        ),
                                    ),
                            )
                        )
                    },
            )

        // 3. play_sound_<id>() wrapper for each unique PlaySound sound ID
        //    Also include wrappers for explicitly registered SoundEffectDefs (even if not in
        // scripts)
        val scriptSoundIds = collectUniqueSoundIds().toSet()
        val defSoundIds = gameIR.soundEffects.map { it.id }.toSet()
        val allSoundIds = (scriptSoundIds + defSoundIds).sorted()
        for (soundId in allSoundIds) {
            functions += buildSoundWrapperFunction(soundId)
        }

        return functions
    }

    /**
     * Collect all unique PlaySound IDs from all scene scripts in the game.
     *
     * Walks enterOps, frameOps, and exitOps for each scene recursively.
     */
    fun collectUniqueSoundIds(): List<String> {
        val ids = mutableSetOf<String>()
        for (scene in gameIR.scenes) {
            collectSoundIdsFromOps(scene.enterOps, ids)
            collectSoundIdsFromOps(scene.frameOps, ids)
            collectSoundIdsFromOps(scene.exitOps, ids)
        }
        return ids.sorted()
    }

    /**
     * Return true if any scene in the game uses a music ScriptOp (MusicPlay, MusicStop, MusicPause,
     * MusicResume).
     *
     * When true, the pipeline adds `#include <hUGEDriver.h>` and a `hUGE_dosound()` call to the
     * main game loop so that hUGETracker's audio interrupt driver is wired in.
     */
    fun hasMusicOps(): Boolean {
        for (scene in gameIR.scenes) {
            if (
                containsMusicOp(scene.enterOps) ||
                    containsMusicOp(scene.frameOps) ||
                    containsMusicOp(scene.exitOps)
            ) {
                return true
            }
        }
        return false
    }

    private fun collectSoundIdsFromOps(ops: List<ScriptOp>, ids: MutableSet<String>) {
        for (op in ops) {
            when (op) {
                is PlaySound -> ids += op.soundId
                is IfOp -> {
                    collectSoundIdsFromOps(op.then, ids)
                    collectSoundIdsFromOps(op.otherwise, ids)
                }
                is WhileOp -> collectSoundIdsFromOps(op.body, ids)
                is ForOp -> collectSoundIdsFromOps(op.body, ids)
                is FadeOp -> collectSoundIdsFromOps(op.after, ids)
                is DialogChoice -> {
                    for (option in op.options) collectSoundIdsFromOps(option.body, ids)
                }
                else -> Unit
            }
        }
    }

    /** Recursively scan [ops] for any music ScriptOp. */
    private fun containsMusicOp(ops: List<ScriptOp>): Boolean = ops.any { op ->
        when (op) {
            is MusicPlay,
            is MusicStop,
            is MusicPause,
            is MusicResume -> true
            is IfOp -> containsMusicOp(op.then) || containsMusicOp(op.otherwise)
            is WhileOp -> containsMusicOp(op.body)
            is ForOp -> containsMusicOp(op.body)
            is FadeOp -> containsMusicOp(op.after)
            is DialogChoice -> op.options.any { containsMusicOp(it.body) }
            else -> false
        }
    }

    /**
     * Build a `play_sound_<id>()` wrapper for the given sound ID.
     *
     * If a [SoundEffectDef] is registered in [gameIR.soundEffects][GameIR.soundEffects] for this
     * ID, generates real NRxx register writes derived from the preset data (A1). Supports all four
     * Game Boy audio channels:
     * - [SoundChannel.PULSE1] -> NR10-NR14 (CH1: square with sweep)
     * - [SoundChannel.PULSE2] -> NR21-NR24 (CH2: square without sweep)
     * - [SoundChannel.WAVE] -> NR30-NR34 + wave RAM load (CH3: programmable wave, A3)
     * - [SoundChannel.NOISE] -> NR41-NR44 (CH4: pseudo-random noise)
     *
     * For WAVE channel: if [SoundRegisters.waveform] is non-null (16 bytes), also generates
     * `wave_data_<id>[]` constant and loads it into wave RAM before triggering CH3.
     *
     * Falls back to a `play_sound()` stub call when no [SoundEffectDef] is registered (backward
     * compat for games that use [PlaySound] without registering a sound effect definition).
     */
    private fun buildSoundWrapperFunction(soundId: String): CFunction {
        val sanitizedId = soundId.replace('-', '_').replace(' ', '_')
        val def = gameIR.soundEffects.find { it.id == soundId }

        val body =
            if (def == null) {
                // Fallback: no SoundEffectDef registered — emit comment stub
                listOf(CComment("no SoundEffectDef for '$soundId' — stub"))
            } else {
                buildNRxxRegisterWrites(def)
            }

        return CFunction(name = "play_sound_$sanitizedId", returnType = CVoid, body = body)
    }

    /**
     * Generate Game Boy NRxx register writes for a [SoundEffectDef].
     *
     * Dispatches to a per-channel helper (buildPulse1Writes / buildPulse2Writes / buildWaveWrites /
     * buildNoiseWrites) defined as file-private top-level functions below. Register bit layouts
     * follow Pan Docs; see each helper's KDoc for details.
     *
     * All register values are emitted as hex literals with the `u` unsigned suffix (e.g. `0xF0u`).
     */
    @Suppress("MagicNumber")
    private fun buildNRxxRegisterWrites(def: SoundEffectDef): List<CStatement> =
        when (def.channel) {
            SoundChannel.PULSE1 -> buildPulse1Writes(def.registers)
            SoundChannel.PULSE2 -> buildPulse2Writes(def.registers)
            SoundChannel.WAVE -> buildWaveWrites(def.registers)
            SoundChannel.NOISE -> buildNoiseWrites(def.registers)
        }
}

// =============================================================================
// File-private NRxx helpers — extracted from SoundVisitor.buildNRxxRegisterWrites (E-06)
// One helper per GB audio channel; nrWrite + envelopeValue are shared utilities.
// =============================================================================

/**
 * Emit `register = 0xVVu` as a C hex-literal assignment (unsigned suffix, per hardware convention).
 */
@Suppress("MagicNumber")
private fun nrWrite(register: String, value: Int): CExprStatement {
    val hexLit = "0x${value.toString(16).uppercase().padStart(2, '0')}u"
    return CExprStatement(CBinaryExpr(CVar(register), "=", CRawExpr(hexLit)))
}

/**
 * Compute NR12/NR22/NR42 envelope byte: vol(7:4) | dir(3) | pace(2:0).
 *
 * Returns 0x00 when [env] is null (DAC-off / no envelope configured).
 */
@Suppress("MagicNumber")
private fun envelopeValue(env: EnvelopeConfig?): Int =
    if (env != null) {
        (env.volume shl 4) or
            (if (env.direction == EnvelopeDirection.INCREASE) 0x08 else 0x00) or
            (env.pace and 0x07)
    } else {
        0x00
    }

/**
 * Build NRxx writes for CH1 (PULSE1: sweep + square wave + envelope).
 *
 * Register layout: NR10 sweep, NR11 duty+length, NR12 envelope, NR13 freq-low, NR14
 * trigger+length-enable+freq-high.
 */
@Suppress("MagicNumber")
private fun buildPulse1Writes(regs: SoundRegisters): List<CStatement> {
    val body = mutableListOf<CStatement>()
    // NR10: sweep — bit 6:4=time, bit 3=direction, bit 2:0=shift
    val sweepVal =
        regs.sweep?.let { s ->
            (s.time shl 4) or
                (if (s.direction == SweepDirection.DECREASE) 0x08 else 0x00) or
                (s.shift and 0x07)
        } ?: 0x00
    body += nrWrite("NR10_REG", sweepVal)
    // NR11: duty + length — bit 7:6=duty, bit 5:0=length
    body += nrWrite("NR11_REG", (regs.duty.bits shl 6) or (regs.length and 0x3F))
    // NR12: envelope — bit 7:4=volume, bit 3=direction, bit 2:0=pace
    body += nrWrite("NR12_REG", envelopeValue(regs.envelope))
    // NR13: frequency low byte
    body += nrWrite("NR13_REG", regs.frequency and 0xFF)
    // NR14: trigger + length-enable + frequency high 3 bits
    val nr14 =
        (if (regs.trigger) 0x80 else 0x00) or
            (if (regs.lengthEnable) 0x40 else 0x00) or
            ((regs.frequency shr 8) and 0x07)
    body += nrWrite("NR14_REG", nr14)
    return body
}

/**
 * Build NRxx writes for CH2 (PULSE2: square wave + envelope, no sweep).
 *
 * Register layout: NR21 duty+length, NR22 envelope, NR23 freq-low, NR24
 * trigger+length-enable+freq-high.
 */
@Suppress("MagicNumber")
private fun buildPulse2Writes(regs: SoundRegisters): List<CStatement> {
    val body = mutableListOf<CStatement>()
    // NR21: duty + length — bit 7:6=duty, bit 5:0=length
    body += nrWrite("NR21_REG", (regs.duty.bits shl 6) or (regs.length and 0x3F))
    // NR22: envelope — bit 7:4=volume, bit 3=direction, bit 2:0=pace
    body += nrWrite("NR22_REG", envelopeValue(regs.envelope))
    // NR23: frequency low byte
    body += nrWrite("NR23_REG", regs.frequency and 0xFF)
    // NR24: trigger + length-enable + frequency high 3 bits
    val nr24 =
        (if (regs.trigger) 0x80 else 0x00) or
            (if (regs.lengthEnable) 0x40 else 0x00) or
            ((regs.frequency shr 8) and 0x07)
    body += nrWrite("NR24_REG", nr24)
    return body
}

/**
 * Build NRxx writes for CH3 (WAVE: programmable wave with optional wave RAM load).
 *
 * Register layout: optional NR30=0x00 + wave RAM load, NR30=DAC-on, NR31 length, NR32 output-level,
 * NR33 freq-low, NR34 trigger+length-enable+freq-high.
 */
@Suppress("MagicNumber")
private fun buildWaveWrites(regs: SoundRegisters): List<CStatement> {
    val body = mutableListOf<CStatement>()
    // Load wave RAM if custom waveform data is provided (A3)
    val waveform = regs.waveform
    if (waveform != null && waveform.size >= 16) {
        body += nrWrite("NR30_REG", 0x00) // disable CH3 before loading wave RAM
        for (i in 0 until 16) {
            val byteVal = waveform[i].toInt() and 0xFF
            body +=
                CExprStatement(
                    CBinaryExpr(
                        CArrayAccess(CVar("AUD3WAVERAM"), CLiteral(i)),
                        "=",
                        CLiteral(byteVal),
                    )
                )
        }
    }
    // NR30: CH3 on/off (bit 7=DAC power)
    body += nrWrite("NR30_REG", 0x80) // DAC on
    // NR31: length
    body += nrWrite("NR31_REG", regs.length and 0xFF)
    // NR32: output level (bit 6:5 = 0=mute, 1=100%, 2=50%, 3=25%)
    body += nrWrite("NR32_REG", (regs.waveOutputLevel and 0x03) shl 5)
    // NR33: frequency low byte
    body += nrWrite("NR33_REG", regs.frequency and 0xFF)
    // NR34: trigger + length-enable + frequency high 3 bits
    val nr34 =
        (if (regs.trigger) 0x80 else 0x00) or
            (if (regs.lengthEnable) 0x40 else 0x00) or
            ((regs.frequency shr 8) and 0x07)
    body += nrWrite("NR34_REG", nr34)
    return body
}

/**
 * Build NRxx writes for CH4 (NOISE: pseudo-random noise generator).
 *
 * Register layout: NR41 length, NR42 envelope, NR43 noise-params (clock-shift + width-mode +
 * divisor), NR44 trigger+length-enable.
 */
@Suppress("MagicNumber")
private fun buildNoiseWrites(regs: SoundRegisters): List<CStatement> {
    val body = mutableListOf<CStatement>()
    // NR41: length (bit 5:0=length)
    body += nrWrite("NR41_REG", regs.length and 0x3F)
    // NR42: envelope — bit 7:4=volume, bit 3=direction, bit 2:0=pace
    body += nrWrite("NR42_REG", envelopeValue(regs.envelope))
    // NR43: noise params — bit 7:4=clock-shift, bit 3=width-mode, bit 2:0=divisor
    val nr43 =
        ((regs.noiseClockShift and 0x0F) shl 4) or
            (if (regs.noiseWidthMode) 0x08 else 0x00) or
            (regs.noiseDivisor and 0x07)
    body += nrWrite("NR43_REG", nr43)
    // NR44: trigger + length-enable
    val nr44 = (if (regs.trigger) 0x80 else 0x00) or (if (regs.lengthEnable) 0x40 else 0x00)
    body += nrWrite("NR44_REG", nr44)
    return body
}
