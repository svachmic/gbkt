/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// FOUNDATION TYPES
// =============================================================================

/** 2D integer position. */
data class PositionDef(val x: Int, val y: Int)

/** 2D integer size in pixels. */
data class SizeDef(val width: Int, val height: Int)

/** Axis-aligned hitbox rectangle, relative to the actor's position. */
data class HitboxDef(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * Sprite definition binding an asset reference to display size and optional hitbox.
 *
 * [frameWidth] and [frameHeight] are optional frame layout metadata for multi-frame sprite sheets.
 * When set, the GBDK backend computes per-frame tile offsets for `set_sprite_tile()`:
 * `tiles_per_frame = (frameWidth / 8) * (frameHeight / 8)`.
 *
 * Both default to `null`, meaning no multi-frame metadata — sprite treated as a single frame.
 */
data class SpriteDef(
    val assetRef: AssetRef,
    val size: SizeDef,
    val hitbox: HitboxDef? = null,
    val frameWidth: Int? = null,
    val frameHeight: Int? = null,
)

/** Source-level location for error messages and sourcemap generation. */
data class SourceLocation(val file: String, val line: Int, val col: Int)

// =============================================================================
// ENUMS
// =============================================================================

/** Variable assignment operations. */
enum class AssignOp {
    SET,
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    AND,
    OR,
    XOR,
}

/** Binary expression operations — arithmetic, bitwise, comparison, and logical. */
enum class BinaryOp {
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    AND,
    OR,
    XOR,
    SHL,
    SHR,
    EQ,
    NEQ,
    LT,
    LTE,
    GT,
    GTE,
    LOGICAL_AND,
    LOGICAL_OR,
}

/** Unary expression operations. */
enum class UnaryOp {
    NEGATE,
    BITWISE_NOT,
    LOGICAL_NOT,
}

/** Math utility functions for MathOp. */
enum class MathFunction {
    ABS,
    MIN,
    MAX,
    CLAMP,
    RAND,
}

/** Game Boy variable types mapping to GBDK C types. */
enum class VarType {
    U8,
    U16,
    I8,
    I16,
}

/** Returns the byte size of this [VarType] on Game Boy hardware. */
val VarType.byteSize: Int
    get() =
        when (this) {
            VarType.U8,
            VarType.I8 -> 1
            VarType.U16,
            VarType.I16 -> 2
        }

// =============================================================================
// GAME CONFIGURATION
// =============================================================================

/**
 * GBC hardware target mode.
 *
 * Controls which GBDK compiler flags are passed when building the ROM. The value flows through
 * [CartridgeConfig] → `gbkt-build.properties` → [CompileRomTask], which converts it to the GBDK
 * `-Wm-yc` (COMPATIBLE) or `-Wm-yC` (ONLY) flag.
 * - [DMG]: Classic grayscale only. No GBC palette features.
 * - [GBC_COMPATIBLE]: Works on both DMG and GBC (`-Wm-yc`). Enables [GBCPalette] colors on GBC.
 * - [GBC_ONLY]: GBC exclusive, will not run on DMG (`-Wm-yC`). Full GBC feature access.
 */
enum class GbcTarget {
    DMG,
    GBC_COMPATIBLE,
    GBC_ONLY,
}

/**
 * Game Boy cartridge type — owns the MBC hardware byte and the ROM bank cap per D-03/WR-01.
 *
 * [mbcByte] is the GBDK cartridge-type byte written to gbkt-build.properties
 * and ultimately to the ROM header. Values are the official Game Boy hardware
 * MBC identifier bytes (Nintendo cart type table, DMG/CGB specs).
 *
 * [maxRomBanks] is the maximum number of ROM banks this cartridge type supports.
 * This is the authoritative per-type ROM bank cap used by [AnalysisConfig.fromCartridgeConfig]
 * and the D-05/D-06 bank derivation logic. Derived bank counts must be clamped to this cap
 * before being used as an effective bank count.
 */
enum class Cartridge(val mbcByte: Int, val maxRomBanks: Int) {
    ROM_ONLY(0x00, 2),
    MBC1(0x01, 32),
    MBC1_RAM(0x02, 32),
    MBC1_RAM_BATTERY(0x03, 32),
    MBC2(0x05, 16),
    MBC2_BATTERY(0x06, 16),
    MBC3_TIMER_BATTERY(0x10, 128),
    MBC3(0x11, 128),
    MBC3_RAM_BATTERY(0x13, 128),
    MBC5(0x19, 256),
    MBC5_RAM_BATTERY(0x1B, 256),
}

/** Cartridge hardware configuration. */
data class CartridgeConfig(
    val cartridge: Cartridge = Cartridge.ROM_ONLY,
    val romBanks: Int? = null,
    val ramBanks: Int = 0,
    val gbcTarget: GbcTarget = GbcTarget.DMG,
)

/** Global variable definition. */
data class VariableDef(val name: String, val type: VarType, val initialValue: Int = 0)

/** Global array variable definition — emits as `UINT8 _name[size];` in generated C. */
data class ArrayDef(val name: String, val elementType: VarType, val size: Int)

// =============================================================================
// SOUND EFFECT DEFINITIONS
// =============================================================================

/** Game Boy audio channel. */
enum class SoundChannel {
    PULSE1, // CH1: square wave with sweep
    PULSE2, // CH2: square wave (no sweep)
    WAVE, // CH3: programmable wave
    NOISE, // CH4: pseudo-random noise
}

/** Sound effect priority level for AudioMixer channel preemption. */
enum class SfxPriority(val value: Int) {
    LOW(64),
    MEDIUM(128),
    HIGH(192),
    CRITICAL(255),
}

/** Duty cycle for pulse channels (CH1/CH2). */
enum class DutyCycle(val bits: Int) {
    TWELVE_POINT_FIVE(0),
    TWENTY_FIVE(1),
    FIFTY(2),
    SEVENTY_FIVE(3),
}

/** Envelope direction for volume change. */
enum class EnvelopeDirection {
    INCREASE,
    DECREASE,
}

/** Sweep direction for CH1 frequency sweep. */
enum class SweepDirection {
    INCREASE,
    DECREASE,
}

/**
 * Sweep configuration for CH1 (PULSE1).
 *
 * @property time Sweep period (0-7). 0 disables sweep.
 * @property direction Whether frequency increases or decreases each sweep step.
 * @property shift Number of times to shift the frequency per sweep step (0-7).
 */
data class SweepConfig(val time: Int, val direction: SweepDirection, val shift: Int)

/**
 * Volume envelope configuration.
 *
 * @property volume Initial volume (0-15). 0 = silent, 15 = max.
 * @property direction Volume change direction per pace tick.
 * @property pace Envelope pace (0-7). 0 disables envelope.
 */
data class EnvelopeConfig(val volume: Int, val direction: EnvelopeDirection, val pace: Int)

/**
 * Low-level Game Boy NRxx register values for a sound effect.
 *
 * All fields are stored as pre-computed register values to simplify codegen.
 *
 * @property frequency 11-bit frequency value (for CH1/CH2/CH3). 0 for CH4.
 * @property length Sound length (0-63 for CH1/CH2/CH3; 0-63 for CH4).
 * @property trigger Whether to trigger (restart) the channel when played.
 * @property lengthEnable Whether to stop after [length] expires.
 * @property duty Duty cycle (for CH1/CH2 only).
 * @property envelope Envelope configuration (for CH1/CH2/CH4). Null = no envelope.
 * @property sweep Sweep configuration (for CH1 only). Null = no sweep.
 * @property noiseClockShift CH4 noise clock shift (0-15). Ignored for other channels.
 * @property noiseDivisor CH4 noise divisor code (0-7). Ignored for other channels.
 * @property noiseWidthMode CH4 LFSR width (false = 15-bit, true = 7-bit). Ignored for others.
 * @property waveOutputLevel CH3 output level (0-3). Ignored for other channels.
 * @property waveform 16-byte wave RAM data for CH3 WAVE channel. Null for other channels.
 */
data class SoundRegisters(
    val frequency: Int = 0,
    val length: Int = 0,
    val trigger: Boolean = true,
    val lengthEnable: Boolean = false,
    val duty: DutyCycle = DutyCycle.FIFTY,
    val envelope: EnvelopeConfig? = null,
    val sweep: SweepConfig? = null,
    val noiseClockShift: Int = 0,
    val noiseDivisor: Int = 0,
    val noiseWidthMode: Boolean = false,
    val waveOutputLevel: Int = 2,
    val waveform: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SoundRegisters) return false
        return frequency == other.frequency &&
            length == other.length &&
            trigger == other.trigger &&
            lengthEnable == other.lengthEnable &&
            duty == other.duty &&
            envelope == other.envelope &&
            sweep == other.sweep &&
            noiseClockShift == other.noiseClockShift &&
            noiseDivisor == other.noiseDivisor &&
            noiseWidthMode == other.noiseWidthMode &&
            waveOutputLevel == other.waveOutputLevel &&
            waveform.contentEquals(other.waveform)
    }

    override fun hashCode(): Int {
        var result = frequency
        result = 31 * result + length
        result = 31 * result + trigger.hashCode()
        result = 31 * result + lengthEnable.hashCode()
        result = 31 * result + duty.hashCode()
        result = 31 * result + (envelope?.hashCode() ?: 0)
        result = 31 * result + (sweep?.hashCode() ?: 0)
        result = 31 * result + noiseClockShift
        result = 31 * result + noiseDivisor
        result = 31 * result + noiseWidthMode.hashCode()
        result = 31 * result + waveOutputLevel
        result = 31 * result + (waveform?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Predefined sound effect presets.
 *
 * Mapped to [SoundRegisters] by [SoundEffectDef.fromPreset].
 */
enum class SoundPreset {
    BEEP,
    HIT,
    JUMP,
    COIN,
    BUMP,
    WIN,
    LOSE,
    SHOOT,
    EXPLODE,
    POWERUP,
}

/**
 * Complete definition of a sound effect entry in the game IR.
 *
 * Replaces the v1 `SoundEffect` domain model. Used by the GBDK backend to generate
 * `play_sound_<id>()` functions with real NRxx register writes.
 *
 * @property id Unique sound effect identifier (used as C function name suffix).
 * @property channel Which Game Boy audio channel to use.
 * @property registers Pre-computed register values for this effect.
 */
data class SoundEffectDef(
    val id: String,
    val channel: SoundChannel,
    val registers: SoundRegisters,
    val priority: SfxPriority = SfxPriority.MEDIUM,
) {
    companion object {
        /**
         * Create a [SoundEffectDef] from a named preset.
         *
         * All presets have been tuned to sound good on actual Game Boy hardware. Register values
         * are derived from Pan Docs-compliant bit layouts.
         */
        fun fromPreset(id: String, preset: SoundPreset): SoundEffectDef =
            when (preset) {
                SoundPreset.BEEP ->
                    SoundEffectDef(
                        id = id,
                        channel = SoundChannel.PULSE1,
                        registers =
                            SoundRegisters(
                                frequency = 1750,
                                length = 0,
                                trigger = true,
                                lengthEnable = false,
                                duty = DutyCycle.FIFTY,
                                envelope =
                                    EnvelopeConfig(
                                        volume = 12,
                                        direction = EnvelopeDirection.DECREASE,
                                        pace = 3,
                                    ),
                                sweep = null,
                            ),
                    )
                SoundPreset.HIT ->
                    SoundEffectDef(
                        id = id,
                        channel = SoundChannel.NOISE,
                        registers =
                            SoundRegisters(
                                length = 0,
                                trigger = true,
                                lengthEnable = false,
                                envelope =
                                    EnvelopeConfig(
                                        volume = 15,
                                        direction = EnvelopeDirection.DECREASE,
                                        pace = 2,
                                    ),
                                noiseClockShift = 4,
                                noiseDivisor = 0,
                                noiseWidthMode = false,
                            ),
                    )
                SoundPreset.JUMP ->
                    SoundEffectDef(
                        id = id,
                        channel = SoundChannel.PULSE2,
                        registers =
                            SoundRegisters(
                                frequency = 1024,
                                length = 0,
                                trigger = true,
                                lengthEnable = false,
                                duty = DutyCycle.TWENTY_FIVE,
                                envelope =
                                    EnvelopeConfig(
                                        volume = 10,
                                        direction = EnvelopeDirection.DECREASE,
                                        pace = 4,
                                    ),
                            ),
                    )
                SoundPreset.COIN ->
                    SoundEffectDef(
                        id = id,
                        channel = SoundChannel.PULSE1,
                        registers =
                            SoundRegisters(
                                frequency = 1900,
                                length = 20,
                                trigger = true,
                                lengthEnable = true,
                                duty = DutyCycle.FIFTY,
                                envelope =
                                    EnvelopeConfig(
                                        volume = 14,
                                        direction = EnvelopeDirection.DECREASE,
                                        pace = 1,
                                    ),
                            ),
                    )
                SoundPreset.BUMP ->
                    SoundEffectDef(
                        id = id,
                        channel = SoundChannel.NOISE,
                        registers =
                            SoundRegisters(
                                length = 0,
                                trigger = true,
                                lengthEnable = false,
                                envelope =
                                    EnvelopeConfig(
                                        volume = 8,
                                        direction = EnvelopeDirection.DECREASE,
                                        pace = 1,
                                    ),
                                noiseClockShift = 6,
                                noiseDivisor = 0,
                                noiseWidthMode = true,
                            ),
                    )
                SoundPreset.WIN ->
                    SoundEffectDef(
                        id = id,
                        channel = SoundChannel.PULSE1,
                        registers =
                            SoundRegisters(
                                frequency = 1966,
                                length = 0,
                                trigger = true,
                                lengthEnable = false,
                                duty = DutyCycle.FIFTY,
                                envelope =
                                    EnvelopeConfig(
                                        volume = 15,
                                        direction = EnvelopeDirection.DECREASE,
                                        pace = 2,
                                    ),
                            ),
                    )
                SoundPreset.LOSE ->
                    SoundEffectDef(
                        id = id,
                        channel = SoundChannel.PULSE1,
                        registers =
                            SoundRegisters(
                                frequency = 256,
                                length = 0,
                                trigger = true,
                                lengthEnable = false,
                                duty = DutyCycle.FIFTY,
                                envelope =
                                    EnvelopeConfig(
                                        volume = 12,
                                        direction = EnvelopeDirection.DECREASE,
                                        pace = 3,
                                    ),
                            ),
                    )
                SoundPreset.SHOOT ->
                    SoundEffectDef(
                        id = id,
                        channel = SoundChannel.PULSE2,
                        registers =
                            SoundRegisters(
                                frequency = 1500,
                                length = 10,
                                trigger = true,
                                lengthEnable = true,
                                duty = DutyCycle.TWELVE_POINT_FIVE,
                                envelope =
                                    EnvelopeConfig(
                                        volume = 12,
                                        direction = EnvelopeDirection.DECREASE,
                                        pace = 2,
                                    ),
                            ),
                    )
                SoundPreset.EXPLODE ->
                    SoundEffectDef(
                        id = id,
                        channel = SoundChannel.NOISE,
                        registers =
                            SoundRegisters(
                                length = 0,
                                trigger = true,
                                lengthEnable = false,
                                envelope =
                                    EnvelopeConfig(
                                        volume = 15,
                                        direction = EnvelopeDirection.DECREASE,
                                        pace = 3,
                                    ),
                                noiseClockShift = 2,
                                noiseDivisor = 1,
                                noiseWidthMode = false,
                            ),
                    )
                SoundPreset.POWERUP ->
                    SoundEffectDef(
                        id = id,
                        channel = SoundChannel.PULSE1,
                        registers =
                            SoundRegisters(
                                frequency = 1800,
                                length = 0,
                                trigger = true,
                                lengthEnable = false,
                                duty = DutyCycle.TWENTY_FIVE,
                                envelope =
                                    EnvelopeConfig(
                                        volume = 14,
                                        direction = EnvelopeDirection.INCREASE,
                                        pace = 3,
                                    ),
                                sweep =
                                    SweepConfig(
                                        time = 2,
                                        direction = SweepDirection.INCREASE,
                                        shift = 2,
                                    ),
                            ),
                    )
            }
    }
}
