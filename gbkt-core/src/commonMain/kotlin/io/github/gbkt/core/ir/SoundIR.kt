/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import io.github.gbkt.core.Channel
import io.github.gbkt.core.Panning
import io.github.gbkt.core.SoundPriority

// =============================================================================
// SOUND IR NODES
// =============================================================================

data class IRSoundPlay(val soundName: String, val priority: SoundPriority = SoundPriority.NORMAL) :
    IRStatement

data class IRSoundStop(val soundName: String) : IRStatement

data class IRMusicPlay(val musicName: String) : IRStatement

data object IRMusicStop : IRStatement

data object IRMusicPause : IRStatement

data object IRMusicResume : IRStatement

data class IRMusicFade(val frames: Int) : IRStatement

data class IRSoundMasterVolume(val volume: Int) : IRStatement

data class IRSoundEnable(val enable: Boolean) : IRStatement

data class IRSoundPan(val channel: Channel, val panning: Panning) : IRStatement

data class IRSoundMuteChannel(val channel: Channel, val mute: Boolean) : IRStatement
