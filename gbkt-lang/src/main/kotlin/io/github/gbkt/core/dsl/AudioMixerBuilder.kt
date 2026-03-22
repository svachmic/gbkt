/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.dsl

import io.github.gbkt.core.ir.GenericSystem

// =============================================================================
// AUDIO MIXER BUILDER
// DSL for configuring the audio channel group mixing system (A5).
//
// The AudioMixer uses GenericSystem with type="audio_mixer" so that no new
// SystemIR sealed type is needed — the config map carries all structured data
// for the backend to consume.
//
// Channel groups map logical audio categories (music, sfx, ui) to Game Boy
// hardware channels (CH1=pulse, CH2=pulse, CH3=wave, CH4=noise). The backend
// generates NR50/NR51 register writes for volume control and channel enable.
// =============================================================================

/**
 * Definition of a channel group for the audio mixer.
 *
 * @property name Group name used in generated constants (MIXER_GROUP_MUSIC, etc.)
 * @property channels Game Boy channel numbers in this group (1=CH1 pulse, 2=CH2 pulse, 3=CH3 wave,
 *   4=CH4 noise)
 * @property defaultVolume Initial volume 0–7 (Game Boy hardware NR50 range)
 * @property priority Priority level for channel preemption (higher = more important)
 */
data class ChannelGroupDef(
    val name: String,
    val channels: Set<Int> = emptySet(),
    val defaultVolume: Int = 7,
    val priority: Int = 0,
)

/**
 * Builder for a single channel group within the audio mixer.
 *
 * Usage:
 * ```kotlin
 * audioMixer {
 *     group("music") {
 *         channels(1, 2)
 *         volume(7)
 *         priority(0)
 *     }
 * }
 * ```
 */
@GbktDsl
class ChannelGroupBuilder(private val name: String) {
    private var channels: Set<Int> = emptySet()
    private var volume: Int = 7
    private var priority: Int = 0

    /** Sets the Game Boy hardware channels in this group (1=CH1, 2=CH2, 3=CH3, 4=CH4). */
    fun channels(vararg ch: Int) {
        channels = ch.toSet()
    }

    /** Sets the initial volume for this group. Range 0–7 (clamped). */
    fun volume(vol: Int) {
        volume = vol.coerceIn(0, 7)
    }

    /** Sets the priority level for channel preemption. Higher = more important. */
    fun priority(p: Int) {
        priority = p
    }

    internal fun build(): ChannelGroupDef =
        ChannelGroupDef(
            name = name,
            channels = channels,
            defaultVolume = volume,
            priority = priority,
        )
}

/**
 * Builder for the audio channel group mixing system (A5).
 *
 * Generates a [GenericSystem] with `type="audio_mixer"` carrying structured group configuration for
 * the GBDK backend to emit NR50/NR51 register writes.
 *
 * **Default groups (Gap 3):** If no explicit [group] calls are made, the following defaults are
 * used:
 * - `music`: channels 1,2 — pulse channels for music tracks (priority 0)
 * - `sfx`: channels 3,4 — wave/noise for sound effects (priority 1)
 * - `ui`: channel 3 — wave channel for UI feedback sounds (priority 2)
 *
 * Usage with defaults:
 * ```kotlin
 * audioMixer { }
 * ```
 *
 * Usage with custom groups:
 * ```kotlin
 * audioMixer {
 *     group("music") { channels(1, 2); volume(7); priority(0) }
 *     group("sfx")   { channels(3, 4); volume(7); priority(1) }
 *     masterVolume(7)
 *     autoDucking(enabled = true, duckLevel = 3)
 * }
 * ```
 */
@GbktDsl
class AudioMixerBuilder(private val id: String = "audio_mixer") {
    private val groups = mutableListOf<ChannelGroupDef>()

    /** Master volume 0–7. Scales all group volumes when calculating NR50 writes. */
    private var masterVolume: Int = 7

    /** Whether music auto-ducks during SFX/dialog playback (Gap 6). */
    private var autoDucking: Boolean = false

    /** Volume to duck music to during SFX/dialog (0–7, clamped). */
    private var autoDuckLevel: Int = 3

    /**
     * Defines a named channel group with the given configuration.
     *
     * If at least one [group] call is made, the default groups (music, sfx, ui) are NOT added.
     */
    fun group(name: String, block: ChannelGroupBuilder.() -> Unit) {
        groups += ChannelGroupBuilder(name).apply(block).build()
    }

    /**
     * Sets the master volume for all channel groups.
     *
     * Range 0–7 (clamped). Master volume scales all group volumes when computing NR50 register
     * writes.
     */
    fun masterVolume(vol: Int) {
        masterVolume = vol.coerceIn(0, 7)
    }

    /**
     * Enables or disables auto-ducking of music during SFX/dialog playback (Gap 6).
     *
     * When enabled, `audio_mixer_duck()` saves the current music volume and sets it to [duckLevel],
     * and `audio_mixer_unduck()` restores the saved volume.
     *
     * @param enabled True to enable auto-ducking behavior.
     * @param duckLevel Target volume for music during ducking (0–7, clamped, default 3).
     */
    fun autoDucking(enabled: Boolean, duckLevel: Int = 3) {
        autoDucking = enabled
        autoDuckLevel = duckLevel.coerceIn(0, 7)
    }

    /**
     * Builds the [GenericSystem] with type="audio_mixer" carrying structured configuration.
     *
     * **Gap 3:** If no [group] calls were made, default groups (music, sfx, ui) are inserted. This
     * provides sensible defaults while remaining fully overridable by the user.
     */
    internal fun build(): GenericSystem {
        // Gap 3: Add default groups if none defined by the user
        val effectiveGroups =
            if (groups.isEmpty()) {
                listOf(
                    ChannelGroupDef(
                        name = "music",
                        channels = setOf(1, 2),
                        defaultVolume = 7,
                        priority = 0,
                    ),
                    ChannelGroupDef(
                        name = "sfx",
                        channels = setOf(3, 4),
                        defaultVolume = 7,
                        priority = 1,
                    ),
                    ChannelGroupDef(
                        name = "ui",
                        channels = setOf(3),
                        defaultVolume = 7,
                        priority = 2,
                    ),
                )
            } else {
                groups.toList()
            }

        return GenericSystem(
            id = id,
            config =
                mapOf(
                    "type" to "audio_mixer",
                    "groups" to effectiveGroups,
                    "master_volume" to masterVolume,
                    "auto_ducking" to autoDucking,
                    "auto_duck_level" to autoDuckLevel,
                ),
        )
    }
}
