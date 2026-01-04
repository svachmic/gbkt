/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.ir.IRMusicFade
import io.github.gbkt.core.ir.IRMusicPause
import io.github.gbkt.core.ir.IRMusicPlay
import io.github.gbkt.core.ir.IRMusicResume
import io.github.gbkt.core.ir.IRMusicStop
import io.github.gbkt.core.ir.IRSoundEnable
import io.github.gbkt.core.ir.IRSoundMasterVolume
import io.github.gbkt.core.ir.IRSoundMuteChannel
import io.github.gbkt.core.ir.IRSoundPan
import io.github.gbkt.core.ir.IRSoundPlay
import io.github.gbkt.core.ir.IRSoundStop

/**
 * Sound/Music DSL for Game Boy audio.
 *
 * Game Boy has 4 audio channels:
 * - CH1 (Pulse1): Square wave with frequency sweep
 * - CH2 (Pulse2): Square wave without sweep
 * - CH3 (Wave): Custom 4-bit waveform
 * - CH4 (Noise): LFSR noise for drums/explosions
 *
 * Usage: val jump = soundEffect("jump") { preset = SoundPreset.JUMP } val bgm = music("forest.uge")
 *
 * scene("gameplay") { enter { bgm.play() } every.frame { whenever(buttons.a.pressed) { jump.play()
 * } } }
 */

// =============================================================================
// CHANNELS
// =============================================================================

enum class Channel(val index: Int) {
    PULSE1(0), // CH1: Square wave with sweep
    PULSE2(1), // CH2: Square wave
    WAVE(2), // CH3: Custom waveform
    NOISE(3) // CH4: Noise
}

// =============================================================================
// SOUND PRESETS
// =============================================================================

enum class SoundPreset {
    BEEP, // Simple beep
    JUMP, // Upward sweep
    LAND, // Short thud
    COIN, // Two-note pickup
    LASER, // Downward sweep
    EXPLOSION, // Noise burst
    HIT, // Impact sound
    SELECT, // Menu selection
    PAUSE, // Pause sound
    DEATH, // Descending tones
    POWERUP, // Ascending arpeggio
    TICK // Soft tick (for timers)
}

// =============================================================================
// DUTY CYCLE (Pulse channels only)
// =============================================================================

enum class DutyCycle(val bits: Int) {
    TWELVE_POINT_FIVE(0), // 12.5% duty
    TWENTY_FIVE(1), // 25% duty
    FIFTY_PERCENT(2), // 50% duty
    SEVENTY_FIVE(3) // 75% duty
}

// =============================================================================
// SWEEP CONFIGURATION (CH1 only)
// =============================================================================

enum class SweepDirection {
    INCREASE,
    DECREASE
}

data class SweepConfig(
    val time: Int = 0, // 0-7: sweep time
    val direction: SweepDirection = SweepDirection.INCREASE,
    val shift: Int = 0 // 0-7: frequency change magnitude
)

class SweepBuilder {
    var time: Int = 0
    var direction: SweepDirection = SweepDirection.INCREASE
    var shift: Int = 0

    fun build() = SweepConfig(time.coerceIn(0, 7), direction, shift.coerceIn(0, 7))
}

// =============================================================================
// ENVELOPE CONFIGURATION
// =============================================================================

enum class EnvelopeDirection {
    INCREASE,
    DECREASE
}

data class EnvelopeConfig(
    val volume: Int = 15, // 0-15 initial volume
    val direction: EnvelopeDirection = EnvelopeDirection.DECREASE,
    val pace: Int = 0 // 0-7: envelope speed (0 = no envelope)
)

class EnvelopeBuilder {
    var volume: Int = 15
    var direction: EnvelopeDirection = EnvelopeDirection.DECREASE
    var pace: Int = 3

    fun build() = EnvelopeConfig(volume.coerceIn(0, 15), direction, pace.coerceIn(0, 7))
}

// =============================================================================
// WAVE CHANNEL SETTINGS
// =============================================================================

enum class WaveOutputLevel(val bits: Int) {
    MUTE(0),
    FULL(1), // 100%
    HALF(2), // 50%
    QUARTER(3) // 25%
}

// =============================================================================
// NOISE CHANNEL SETTINGS
// =============================================================================

enum class NoiseWidth {
    FIFTEEN_BIT,
    SEVEN_BIT
}

// =============================================================================
// SOUND PRIORITY
// =============================================================================

enum class SoundPriority {
    LOW,
    NORMAL,
    HIGH
}

// =============================================================================
// REGISTER CONFIGURATION
// =============================================================================

data class SoundRegisters(
    // Common settings
    val sweep: SweepConfig? = null,
    val duty: DutyCycle = DutyCycle.FIFTY_PERCENT,
    val length: Int = 0,
    val envelope: EnvelopeConfig? = null,
    val frequency: Int = 1000,
    val trigger: Boolean = true,
    val lengthEnable: Boolean = false,

    // Wave channel specific
    val waveform: ByteArray? = null,
    val outputLevel: WaveOutputLevel = WaveOutputLevel.FULL,

    // Noise channel specific
    val clockShift: Int = 0,
    val widthMode: NoiseWidth = NoiseWidth.FIFTEEN_BIT,
    val divisor: Int = 0
) {
    override fun equals(other: Any?) =
        other is SoundRegisters &&
            sweep == other.sweep &&
            duty == other.duty &&
            length == other.length &&
            envelope == other.envelope &&
            frequency == other.frequency &&
            trigger == other.trigger &&
            lengthEnable == other.lengthEnable &&
            waveform.contentEquals(other.waveform) &&
            outputLevel == other.outputLevel &&
            clockShift == other.clockShift &&
            widthMode == other.widthMode &&
            divisor == other.divisor

    override fun hashCode(): Int {
        var result = sweep?.hashCode() ?: 0
        result = 31 * result + duty.hashCode()
        result = 31 * result + length
        result = 31 * result + (envelope?.hashCode() ?: 0)
        result = 31 * result + frequency
        result = 31 * result + trigger.hashCode()
        result = 31 * result + lengthEnable.hashCode()
        result = 31 * result + (waveform?.contentHashCode() ?: 0)
        result = 31 * result + outputLevel.hashCode()
        result = 31 * result + clockShift
        result = 31 * result + widthMode.hashCode()
        result = 31 * result + divisor
        return result
    }
}

// =============================================================================
// SOUND EFFECT
// =============================================================================

class SoundEffect(
    val name: String,
    val channel: Channel,
    val registers: SoundRegisters,
    val preset: SoundPreset? = null
) {
    /** Play the sound effect */
    fun play(priority: SoundPriority = SoundPriority.NORMAL) {
        RecordingContext.require().emit(IRSoundPlay(name, priority))
    }

    /** Stop this sound if playing */
    fun stop() {
        RecordingContext.require().emit(IRSoundStop(name))
    }
}

// =============================================================================
// SOUND EFFECT BUILDER
// =============================================================================

class SoundEffectBuilder(private val name: String) {
    var preset: SoundPreset? = null
    var channel: Channel = Channel.PULSE1

    // Register values
    private var _sweep: SweepConfig? = null
    var duty: DutyCycle = DutyCycle.FIFTY_PERCENT
    var length: Int = 0
    private var _envelope: EnvelopeConfig? = null
    var frequency: Int = 1000
    var trigger: Boolean = true
    var lengthEnable: Boolean = false

    // Wave-specific
    var waveform: ByteArray? = null
    var outputLevel: WaveOutputLevel = WaveOutputLevel.FULL

    // Noise-specific
    var clockShift: Int = 0
    var widthMode: NoiseWidth = NoiseWidth.FIFTEEN_BIT
    var divisor: Int = 0

    fun sweep(init: SweepBuilder.() -> Unit) {
        _sweep = SweepBuilder().apply(init).build()
    }

    fun envelope(init: EnvelopeBuilder.() -> Unit) {
        _envelope = EnvelopeBuilder().apply(init).build()
    }

    fun build(): SoundEffect {
        // If preset is set, use preset values
        val (resolvedChannel, registers) =
            if (preset != null) {
                getPresetConfig(preset!!)
            } else {
                channel to
                    SoundRegisters(
                        sweep = _sweep,
                        duty = duty,
                        length = length,
                        envelope = _envelope,
                        frequency = frequency,
                        trigger = trigger,
                        lengthEnable = lengthEnable,
                        waveform = waveform,
                        outputLevel = outputLevel,
                        clockShift = clockShift,
                        widthMode = widthMode,
                        divisor = divisor
                    )
            }

        return SoundEffect(name, resolvedChannel, registers, preset)
    }
}

// =============================================================================
// PRESET DEFINITIONS
// =============================================================================

private fun getPresetConfig(preset: SoundPreset): Pair<Channel, SoundRegisters> =
    when (preset) {
        SoundPreset.BEEP ->
            Channel.PULSE1 to
                SoundRegisters(
                    duty = DutyCycle.FIFTY_PERCENT,
                    envelope = EnvelopeConfig(12, EnvelopeDirection.DECREASE, 3),
                    frequency = 1500,
                    length = 20,
                    lengthEnable = true
                )
        SoundPreset.JUMP ->
            Channel.PULSE1 to
                SoundRegisters(
                    sweep = SweepConfig(2, SweepDirection.INCREASE, 3),
                    duty = DutyCycle.FIFTY_PERCENT,
                    envelope = EnvelopeConfig(10, EnvelopeDirection.DECREASE, 2),
                    frequency = 1200,
                    length = 15,
                    lengthEnable = true
                )
        SoundPreset.LAND ->
            Channel.NOISE to
                SoundRegisters(
                    envelope = EnvelopeConfig(8, EnvelopeDirection.DECREASE, 1),
                    clockShift = 10,
                    widthMode = NoiseWidth.FIFTEEN_BIT,
                    divisor = 3,
                    length = 5,
                    lengthEnable = true
                )
        SoundPreset.COIN ->
            Channel.PULSE1 to
                SoundRegisters(
                    duty = DutyCycle.TWENTY_FIVE,
                    envelope = EnvelopeConfig(8, EnvelopeDirection.DECREASE, 1),
                    frequency = 1800,
                    length = 10,
                    lengthEnable = true
                )
        SoundPreset.LASER ->
            Channel.PULSE1 to
                SoundRegisters(
                    sweep = SweepConfig(3, SweepDirection.DECREASE, 4),
                    duty = DutyCycle.TWELVE_POINT_FIVE,
                    envelope = EnvelopeConfig(15, EnvelopeDirection.DECREASE, 2),
                    frequency = 1900,
                    length = 25,
                    lengthEnable = true
                )
        SoundPreset.EXPLOSION ->
            Channel.NOISE to
                SoundRegisters(
                    envelope = EnvelopeConfig(15, EnvelopeDirection.DECREASE, 3),
                    clockShift = 5,
                    widthMode = NoiseWidth.SEVEN_BIT,
                    divisor = 1,
                    length = 40,
                    lengthEnable = true
                )
        SoundPreset.HIT ->
            Channel.NOISE to
                SoundRegisters(
                    envelope = EnvelopeConfig(12, EnvelopeDirection.DECREASE, 1),
                    clockShift = 8,
                    widthMode = NoiseWidth.FIFTEEN_BIT,
                    divisor = 2,
                    length = 8,
                    lengthEnable = true
                )
        SoundPreset.SELECT ->
            Channel.PULSE1 to
                SoundRegisters(
                    duty = DutyCycle.FIFTY_PERCENT,
                    envelope = EnvelopeConfig(6, EnvelopeDirection.DECREASE, 2),
                    frequency = 1600,
                    length = 5,
                    lengthEnable = true
                )
        SoundPreset.PAUSE ->
            Channel.PULSE1 to
                SoundRegisters(
                    duty = DutyCycle.FIFTY_PERCENT,
                    envelope = EnvelopeConfig(10, EnvelopeDirection.DECREASE, 4),
                    frequency = 1400,
                    length = 30,
                    lengthEnable = true
                )
        SoundPreset.DEATH ->
            Channel.PULSE1 to
                SoundRegisters(
                    sweep = SweepConfig(5, SweepDirection.DECREASE, 5),
                    duty = DutyCycle.FIFTY_PERCENT,
                    envelope = EnvelopeConfig(15, EnvelopeDirection.DECREASE, 5),
                    frequency = 1600,
                    length = 63,
                    lengthEnable = true
                )
        SoundPreset.POWERUP ->
            Channel.PULSE1 to
                SoundRegisters(
                    sweep = SweepConfig(1, SweepDirection.INCREASE, 2),
                    duty = DutyCycle.TWENTY_FIVE,
                    envelope = EnvelopeConfig(12, EnvelopeDirection.DECREASE, 2),
                    frequency = 1200,
                    length = 30,
                    lengthEnable = true
                )
        SoundPreset.TICK ->
            Channel.PULSE1 to
                SoundRegisters(
                    duty = DutyCycle.TWELVE_POINT_FIVE,
                    envelope = EnvelopeConfig(4, EnvelopeDirection.DECREASE, 0),
                    frequency = 1800,
                    length = 2,
                    lengthEnable = true
                )
    }

// =============================================================================
// MUSIC
// =============================================================================

class Music(val name: String, val asset: String, val slot: Int) {
    fun play() {
        RecordingContext.require().emit(IRMusicPlay(name))
    }

    fun stop() {
        RecordingContext.require().emit(IRMusicStop)
    }

    fun pause() {
        RecordingContext.require().emit(IRMusicPause)
    }

    fun resume() {
        RecordingContext.require().emit(IRMusicResume)
    }

    fun fadeOut(frames: Int) {
        RecordingContext.require().emit(IRMusicFade(frames))
    }
}

// =============================================================================
// MUSIC BUILDER
// =============================================================================

class MusicBuilder(private val asset: String, private val slot: Int) {
    private var _name: String? = null

    fun name(value: String) {
        _name = value
    }

    fun build(): Music {
        val musicName =
            _name
                ?: asset
                    .substringAfterLast("/")
                    .substringBeforeLast(".")
                    .replace(Regex("[^a-zA-Z0-9_]"), "_")

        return Music(musicName, asset, slot)
    }
}

// =============================================================================
// GLOBAL SOUND OBJECT
// =============================================================================

object sound {
    /** Mute all sound */
    fun mute() {
        RecordingContext.require().emit(IRSoundEnable(false))
    }

    /** Unmute all sound */
    fun unmute() {
        RecordingContext.require().emit(IRSoundEnable(true))
    }

    /** Set master volume (0-7) */
    fun setVolume(level: Int) {
        RecordingContext.require().emit(IRSoundMasterVolume(level.coerceIn(0, 7)))
    }

    /** Pan a channel to left speaker only */
    fun panLeft(channel: Channel) {
        RecordingContext.require().emit(IRSoundPan(channel, Panning.LEFT))
    }

    /** Pan a channel to right speaker only */
    fun panRight(channel: Channel) {
        RecordingContext.require().emit(IRSoundPan(channel, Panning.RIGHT))
    }

    /** Pan a channel to both speakers (center) */
    fun panCenter(channel: Channel) {
        RecordingContext.require().emit(IRSoundPan(channel, Panning.CENTER))
    }

    /** Mute a specific channel */
    fun muteChannel(channel: Channel) {
        RecordingContext.require().emit(IRSoundMuteChannel(channel, true))
    }

    /** Unmute a specific channel */
    fun unmuteChannel(channel: Channel) {
        RecordingContext.require().emit(IRSoundMuteChannel(channel, false))
    }
}

enum class Panning {
    LEFT,
    RIGHT,
    CENTER
}

// =============================================================================
// SOUND IR NODES
// =============================================================================
// Moved to io.github.gbkt.core.ir.SoundIR
