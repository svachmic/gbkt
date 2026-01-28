/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.constraints

/**
 * Audio hardware specifications.
 *
 * @property channels List of available audio channels and their types.
 * @property sampleRate Native sample rate in Hz (0 if not applicable).
 * @property supportsPCM Whether the platform supports PCM/sample playback.
 * @property supportsWavetable Whether the platform supports wavetable synthesis.
 */
data class AudioSpec(
    val channels: List<AudioChannel>,
    val sampleRate: Int = 0,
    val supportsPCM: Boolean = false,
    val supportsWavetable: Boolean = false,
) {
    /** Total number of audio channels. */
    val channelCount: Int
        get() = channels.size

    /** Get channels of a specific type. */
    fun channelsOfType(type: AudioChannelType): List<AudioChannel> =
        channels.filter { it.type == type }
}

/**
 * An audio channel definition.
 *
 * @property name Human-readable channel name.
 * @property type The type of synthesis this channel supports.
 * @property index Hardware channel index.
 */
data class AudioChannel(val name: String, val type: AudioChannelType, val index: Int)

/** Types of audio synthesis supported by channels. */
enum class AudioChannelType {
    /** Square wave with duty cycle control. */
    PULSE,

    /** Programmable waveform (wavetable). */
    WAVE,

    /** Pseudo-random noise generator. */
    NOISE,

    /** Direct PCM sample playback. */
    PCM,

    /** Frequency modulation synthesis. */
    FM,
}
