/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.graphics.FrameTiming
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRMixerFade
import io.github.gbkt.core.ir.IRMixerMute
import io.github.gbkt.core.ir.IRMixerSetVolume
import io.github.gbkt.core.ir.IRMixerToggleMute

/**
 * Audio Mixer DSL for Game Boy audio channel management.
 *
 * The mixer provides channel groups with independent volume control, priority-based playback, and
 * fade effects.
 *
 * Game Boy has 4 audio channels:
 * - CH1 (Pulse1): Square wave with frequency sweep
 * - CH2 (Pulse2): Square wave without sweep
 * - CH3 (Wave): Custom 4-bit waveform
 * - CH4 (Noise): LFSR noise for drums/explosions
 *
 * Usage:
 * ```kotlin
 * val mixer = audioMixer {
 *     group("sfx") {
 *         channels(Channel.PULSE1, Channel.NOISE)
 *         volume = 100
 *         priority = MixerPriority.HIGH
 *     }
 *     group("music") {
 *         channels(Channel.PULSE2, Channel.WAVE)
 *         volume = 70
 *         priority = MixerPriority.LOW
 *     }
 * }
 *
 * scene("gameplay") {
 *     enter {
 *         mixer.setGroupVolume("sfx", 50)
 *     }
 *     every.frame {
 *         whenever(buttons.start.pressed) {
 *             mixer.fadeGroup("music", to = 0, over = 30.frames)
 *         }
 *     }
 * }
 * ```
 */

// =============================================================================
// MIXER PRIORITY
// =============================================================================

/**
 * Priority levels for audio channel groups. When multiple sounds compete for the same channel,
 * higher priority wins.
 */
enum class MixerPriority(val value: Int) {
    LOW(0),
    NORMAL(1),
    HIGH(2)
}

// =============================================================================
// CHANNEL GROUP
// =============================================================================

/**
 * A group of audio channels with shared volume and priority settings.
 *
 * @property name Unique identifier for the group
 * @property channels Set of channels belonging to this group
 * @property volume Volume level (0-100)
 * @property priority Priority level for channel contention
 * @property muted Whether the group is currently muted
 */
data class ChannelGroup(
    val name: String,
    val channels: Set<Channel>,
    val volume: Int = 100,
    val priority: MixerPriority = MixerPriority.NORMAL,
    val muted: Boolean = false
) {
    init {
        require(volume in 0..100) { "Volume must be 0-100, got $volume" }
        require(channels.isNotEmpty()) { "Channel group must have at least one channel" }
    }

    /** Get the group's ID (index) for code generation */
    internal var id: Int = -1
}

// =============================================================================
// CHANNEL GROUP BUILDER
// =============================================================================

/** Builder for defining a channel group. */
@GbktDsl
class ChannelGroupBuilder(private val name: String) {
    private val _channels = mutableSetOf<Channel>()
    var volume: Int = 100
    var priority: MixerPriority = MixerPriority.NORMAL

    /** Add channels to this group. */
    fun channels(vararg ch: Channel) {
        _channels.addAll(ch)
    }

    /** Add a single channel to this group. */
    fun channel(ch: Channel) {
        _channels.add(ch)
    }

    internal fun build(): ChannelGroup {
        require(_channels.isNotEmpty()) { "Channel group '$name' must have at least one channel" }
        return ChannelGroup(
            name = name,
            channels = _channels.toSet(),
            volume = volume.coerceIn(0, 100),
            priority = priority,
            muted = false
        )
    }
}

// =============================================================================
// AUDIO MIXER
// =============================================================================

/**
 * Audio mixer for managing channel groups.
 *
 * Provides volume control, muting, and fading for groups of channels.
 */
class AudioMixer(val groups: Map<String, ChannelGroup>) {
    init {
        // Assign IDs to groups
        groups.values.forEachIndexed { index, group -> group.id = index }
    }

    /**
     * Set the volume of a channel group.
     *
     * @param groupName Name of the group
     * @param volume Volume level (0-100)
     */
    fun setGroupVolume(groupName: String, volume: Int) {
        require(groupName in groups) { "Unknown group: $groupName" }
        val group = groups[groupName]!!
        RecordingContext.require()
            .emit(IRMixerSetVolume(group.id, IRLiteral(volume.coerceIn(0, 100))))
    }

    /**
     * Set the volume of a channel group using an expression.
     *
     * @param groupName Name of the group
     * @param volume Volume expression (0-100)
     */
    fun setGroupVolume(groupName: String, volume: Expr) {
        require(groupName in groups) { "Unknown group: $groupName" }
        val group = groups[groupName]!!
        RecordingContext.require().emit(IRMixerSetVolume(group.id, volume.ir))
    }

    /**
     * Fade a channel group's volume over time.
     *
     * @param groupName Name of the group
     * @param to Target volume (0-100)
     * @param over Duration in frames
     */
    fun fadeGroup(groupName: String, to: Int, over: FrameTiming) {
        require(groupName in groups) { "Unknown group: $groupName" }
        val group = groups[groupName]!!
        RecordingContext.require().emit(IRMixerFade(group.id, to.coerceIn(0, 100), over.count))
    }

    /**
     * Fade a channel group's volume over time.
     *
     * @param groupName Name of the group
     * @param to Target volume (0-100)
     * @param over Duration in frames
     */
    fun fadeGroup(groupName: String, to: Int, over: Int) {
        require(groupName in groups) { "Unknown group: $groupName" }
        val group = groups[groupName]!!
        RecordingContext.require().emit(IRMixerFade(group.id, to.coerceIn(0, 100), over))
    }

    /**
     * Mute a channel group.
     *
     * @param groupName Name of the group
     */
    fun muteGroup(groupName: String) {
        require(groupName in groups) { "Unknown group: $groupName" }
        val group = groups[groupName]!!
        RecordingContext.require().emit(IRMixerMute(group.id, true))
    }

    /**
     * Unmute a channel group.
     *
     * @param groupName Name of the group
     */
    fun unmuteGroup(groupName: String) {
        require(groupName in groups) { "Unknown group: $groupName" }
        val group = groups[groupName]!!
        RecordingContext.require().emit(IRMixerMute(group.id, false))
    }

    /**
     * Toggle mute state of a channel group.
     *
     * @param groupName Name of the group
     */
    fun toggleMuteGroup(groupName: String) {
        require(groupName in groups) { "Unknown group: $groupName" }
        val group = groups[groupName]!!
        RecordingContext.require().emit(IRMixerToggleMute(group.id))
    }

    /** Get the priority of a channel group. This is used internally for sound priority checking. */
    fun getGroupPriority(groupName: String): MixerPriority {
        return groups[groupName]?.priority ?: MixerPriority.NORMAL
    }

    /** Find which group owns a channel. */
    fun getGroupForChannel(channel: Channel): ChannelGroup? {
        return groups.values.find { channel in it.channels }
    }

    /**
     * Check if a sound can play on a channel based on priority.
     *
     * @param channel The channel to check
     * @param soundPriority The priority of the sound wanting to play
     * @return true if the sound can play
     */
    fun canPlayOnChannel(channel: Channel, soundPriority: SoundPriority): Boolean {
        val group = getGroupForChannel(channel) ?: return true
        return soundPriority.ordinal >= group.priority.ordinal
    }
}

// =============================================================================
// AUDIO MIXER BUILDER
// =============================================================================

/** Builder for creating an audio mixer. */
@GbktDsl
class AudioMixerBuilder {
    private val _groups = mutableMapOf<String, ChannelGroup>()

    /**
     * Define a channel group.
     *
     * @param name Unique name for the group
     * @param init Configuration block
     */
    fun group(name: String, init: ChannelGroupBuilder.() -> Unit) {
        val builder = ChannelGroupBuilder(name)
        builder.init()
        val group = builder.build()

        // Check for channel conflicts
        for ((existingName, existingGroup) in _groups) {
            val overlap = group.channels.intersect(existingGroup.channels)
            require(overlap.isEmpty()) {
                "Channel(s) ${overlap.joinToString()} already assigned to group '$existingName'"
            }
        }

        _groups[name] = group
    }

    internal fun build(): AudioMixer {
        require(_groups.isNotEmpty()) { "Audio mixer must have at least one group" }
        return AudioMixer(_groups.toMap())
    }
}

// =============================================================================
// AUDIO MIXER IR NODES
// =============================================================================
