/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

// =============================================================================
// IR NODES FOR AUDIO MIXER SYSTEM
// =============================================================================

/** Set the volume of a mixer group. */
data class IRMixerSetVolume(val groupId: Int, val volume: IRExpression) : IRStatement

/** Fade a mixer group's volume over time. */
data class IRMixerFade(val groupId: Int, val targetVolume: Int, val durationFrames: Int) :
    IRStatement

/** Mute or unmute a mixer group. */
data class IRMixerMute(val groupId: Int, val mute: Boolean) : IRStatement

/** Toggle the mute state of a mixer group. */
data class IRMixerToggleMute(val groupId: Int) : IRStatement

/** Check mixer priority before playing a sound. This is used internally during sound playback. */
data class IRMixerPriorityCheck(
    val channelIndex: Int,
    val soundPriority: Int,
    val thenStatements: List<IRStatement>
) : IRStatement

// =============================================================================
// AUDIO MIXER EXPRESSIONS
// =============================================================================

/** Get the current volume of a mixer group. */
data class IRMixerGroupVolume(val groupId: Int) : IRExpression

/** Check if a mixer group is muted. */
data class IRMixerGroupIsMuted(val groupId: Int) : IRExpression

/** Check if a mixer group is currently fading. */
data class IRMixerGroupIsFading(val groupId: Int) : IRExpression
