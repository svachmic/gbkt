/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.codegen

import io.github.gbkt.core.AudioMixer
import io.github.gbkt.core.CodeGenerator
import io.github.gbkt.core.ir.IRExpression
import io.github.gbkt.core.ir.IRMixerFade
import io.github.gbkt.core.ir.IRMixerGroupIsFading
import io.github.gbkt.core.ir.IRMixerGroupIsMuted
import io.github.gbkt.core.ir.IRMixerGroupVolume
import io.github.gbkt.core.ir.IRMixerMute
import io.github.gbkt.core.ir.IRMixerPriorityCheck
import io.github.gbkt.core.ir.IRMixerSetVolume
import io.github.gbkt.core.ir.IRMixerToggleMute
import io.github.gbkt.core.ir.IRStatement

// =============================================================================
// AUDIO MIXER CODE GENERATION
// Generates C code for mixer state variables, volume control, and fading
// =============================================================================

/** Generate mixer data: state variables, channel-to-group mappings. */
internal fun CodeGenerator.generateMixerData() {
    val mixer = game.audioMixer ?: return
    if (mixer.groups.isEmpty()) return

    line("// === Audio Mixer Data ===")
    line()

    // Generate group count constant
    val groupCount = mixer.groups.size
    line("#define MIXER_GROUP_COUNT $groupCount")
    line()

    // Generate group ID constants
    line("// Mixer group IDs")
    for ((name, group) in mixer.groups) {
        line("#define MIXER_GROUP_${name.uppercase()} ${group.id}")
    }
    line()

    // Generate per-group state variables
    line("// Per-group state: volume (0-100), muted flag")
    for ((name, group) in mixer.groups) {
        line("static UINT8 _mixer_${name}_volume = ${group.volume};")
        line("static UINT8 _mixer_${name}_muted = ${if (group.muted) 1 else 0};")
        line("static UINT8 _mixer_${name}_priority = ${group.priority.value};")
    }
    line()

    // Generate fade state variables
    line("// Fade state per group")
    for ((name, _) in mixer.groups) {
        line("static UINT8 _mixer_${name}_fade_active = 0;")
        line("static UINT8 _mixer_${name}_fade_target = 0;")
        line("static UINT8 _mixer_${name}_fade_start = 0;")
        line("static UINT8 _mixer_${name}_fade_timer = 0;")
        line("static UINT8 _mixer_${name}_fade_duration = 0;")
    }
    line()

    // Generate channel-to-group lookup array
    line("// Channel to group mapping (indexed by channel: 0=PULSE1, 1=PULSE2, 2=WAVE, 3=NOISE)")
    line("// Value is group ID, 255 = no group")
    val channelMapping = IntArray(4) { 255 } // 255 = no group
    for ((_, group) in mixer.groups) {
        for (channel in group.channels) {
            channelMapping[channel.index] = group.id
        }
    }
    line("static const UINT8 _mixer_channel_group[4] = { ${channelMapping.joinToString(", ")} };")
    line()
}

/** Generate mixer helper functions. */
internal fun CodeGenerator.generateMixerFunctions() {
    val mixer = game.audioMixer ?: return
    if (mixer.groups.isEmpty()) return

    line("// === Audio Mixer Functions ===")
    line()

    // Generate set volume function
    generateMixerSetVolumeFunction(mixer)

    // Generate fade update function
    generateMixerFadeUpdateFunction(mixer)

    // Generate mute/unmute functions
    generateMixerMuteFunctions(mixer)

    // Generate priority check function
    generateMixerPriorityCheckFunction(mixer)

    // Generate apply volume to hardware function
    generateMixerApplyVolumeFunction(mixer)
}

private fun CodeGenerator.generateMixerSetVolumeFunction(mixer: AudioMixer) {
    block("void mixer_set_volume(UINT8 group_id, UINT8 volume)") {
        line("if (volume > 100) volume = 100;")
        block("switch (group_id)") {
            for ((name, group) in mixer.groups) {
                line("case ${group.id}: _mixer_${name}_volume = volume; break;")
            }
        }
        line("_mixer_apply_volume();")
    }
    line()
}

private fun CodeGenerator.generateMixerFadeUpdateFunction(mixer: AudioMixer) {
    block("void _mixer_fade_update(void)") {
        for ((name, group) in mixer.groups) {
            block("if (_mixer_${name}_fade_active)") {
                line("_mixer_${name}_fade_timer++;")
                block("if (_mixer_${name}_fade_timer >= _mixer_${name}_fade_duration)") {
                    line("_mixer_${name}_volume = _mixer_${name}_fade_target;")
                    line("_mixer_${name}_fade_active = 0;")
                }
                block("else") {
                    line("// Linear interpolation")
                    line(
                        "INT16 diff = (INT16)_mixer_${name}_fade_target - (INT16)_mixer_${name}_fade_start;"
                    )
                    line(
                        "INT16 progress = (diff * _mixer_${name}_fade_timer) / _mixer_${name}_fade_duration;"
                    )
                    line("_mixer_${name}_volume = _mixer_${name}_fade_start + (UINT8)progress;")
                }
                line("_mixer_apply_volume();")
            }
        }
    }
    line()

    // Generate start fade helper
    block("void mixer_fade_group(UINT8 group_id, UINT8 target, UINT8 frames)") {
        block("switch (group_id)") {
            for ((name, group) in mixer.groups) {
                block("case ${group.id}:") {
                    line("_mixer_${name}_fade_start = _mixer_${name}_volume;")
                    line("_mixer_${name}_fade_target = target > 100 ? 100 : target;")
                    line("_mixer_${name}_fade_timer = 0;")
                    line("_mixer_${name}_fade_duration = frames;")
                    line("_mixer_${name}_fade_active = 1;")
                    line("break;")
                }
            }
        }
    }
    line()
}

private fun CodeGenerator.generateMixerMuteFunctions(mixer: AudioMixer) {
    block("void mixer_mute_group(UINT8 group_id, UINT8 mute)") {
        block("switch (group_id)") {
            for ((name, group) in mixer.groups) {
                line("case ${group.id}: _mixer_${name}_muted = mute; break;")
            }
        }
        line("_mixer_apply_volume();")
    }
    line()

    block("void mixer_toggle_mute(UINT8 group_id)") {
        block("switch (group_id)") {
            for ((name, group) in mixer.groups) {
                line("case ${group.id}: _mixer_${name}_muted = !_mixer_${name}_muted; break;")
            }
        }
        line("_mixer_apply_volume();")
    }
    line()
}

private fun CodeGenerator.generateMixerPriorityCheckFunction(mixer: AudioMixer) {
    block("UINT8 _mixer_can_play(UINT8 channel, UINT8 sound_priority)") {
        line("UINT8 group_id = _mixer_channel_group[channel];")
        line("if (group_id == 255) return 1;  // No group owns this channel")
        line()
        line("UINT8 group_priority;")
        block("switch (group_id)") {
            for ((name, group) in mixer.groups) {
                line("case ${group.id}: group_priority = _mixer_${name}_priority; break;")
            }
            line("default: return 1;")
        }
        line()
        line("// Sound can play if its priority >= group priority")
        line("return sound_priority >= group_priority;")
    }
    line()

    // Also generate muted check
    block("UINT8 _mixer_is_muted(UINT8 channel)") {
        line("UINT8 group_id = _mixer_channel_group[channel];")
        line("if (group_id == 255) return 0;  // No group = not muted")
        line()
        block("switch (group_id)") {
            for ((name, group) in mixer.groups) {
                line("case ${group.id}: return _mixer_${name}_muted;")
            }
            line("default: return 0;")
        }
    }
    line()
}

private fun CodeGenerator.generateMixerApplyVolumeFunction(mixer: AudioMixer) {
    block("void _mixer_apply_volume(void)") {
        line("// Calculate effective volume for each channel and apply to hardware")
        line("// Game Boy has 4 volume levels (0-3) via NR50 for master volume")
        line("// Per-channel volume is handled via envelope registers")
        line()

        // Calculate combined volume from all group volumes
        // This is a simplified implementation - real hardware would need
        // to modify envelope values when playing sounds
        line("// For now, calculate master volume from highest priority unmuted group")
        line("UINT8 master_vol = 0;")

        // Find the highest volume from non-muted groups
        for ((name, _) in mixer.groups) {
            block("if (!_mixer_${name}_muted && _mixer_${name}_volume > master_vol)") {
                line("master_vol = _mixer_${name}_volume;")
            }
        }

        line()
        line("// Convert 0-100 to 0-7 for NR50")
        line("UINT8 hw_vol = (master_vol * 7) / 100;")
        line("NR50_REG = (hw_vol << 4) | hw_vol;  // Same volume for L and R")
    }
    line()
}

/** Generate mixer IR statement handling. */
internal fun CodeGenerator.generateMixerStatement(stmt: IRStatement) {
    val mixer = game.audioMixer ?: return

    when (stmt) {
        is IRMixerSetVolume -> {
            val groupName =
                mixer.groups.values.find { it.id == stmt.groupId }?.name
                    ?: error("Unknown mixer group ID: ${stmt.groupId}")
            val volumeExpr = generateExpr(stmt.volume)
            line("_mixer_${groupName}_volume = ($volumeExpr > 100) ? 100 : $volumeExpr;")
            line("_mixer_apply_volume();")
        }
        is IRMixerFade -> {
            val groupName =
                mixer.groups.values.find { it.id == stmt.groupId }?.name
                    ?: error("Unknown mixer group ID: ${stmt.groupId}")
            line("_mixer_${groupName}_fade_start = _mixer_${groupName}_volume;")
            line("_mixer_${groupName}_fade_target = ${stmt.targetVolume};")
            line("_mixer_${groupName}_fade_timer = 0;")
            line("_mixer_${groupName}_fade_duration = ${stmt.durationFrames};")
            line("_mixer_${groupName}_fade_active = 1;")
        }
        is IRMixerMute -> {
            val groupName =
                mixer.groups.values.find { it.id == stmt.groupId }?.name
                    ?: error("Unknown mixer group ID: ${stmt.groupId}")
            line("_mixer_${groupName}_muted = ${if (stmt.mute) 1 else 0};")
            line("_mixer_apply_volume();")
        }
        is IRMixerToggleMute -> {
            val groupName =
                mixer.groups.values.find { it.id == stmt.groupId }?.name
                    ?: error("Unknown mixer group ID: ${stmt.groupId}")
            line("_mixer_${groupName}_muted = !_mixer_${groupName}_muted;")
            line("_mixer_apply_volume();")
        }
        is IRMixerPriorityCheck -> {
            block("if (_mixer_can_play(${stmt.channelIndex}, ${stmt.soundPriority}))") {
                stmt.thenStatements.forEach { generateStatement(it) }
            }
        }
        else -> {
            // Not a mixer statement
        }
    }
}

/** Generate mixer expression. */
internal fun CodeGenerator.generateMixerExpr(expr: IRExpression): String? {
    val mixer = game.audioMixer ?: return null

    return when (expr) {
        is IRMixerGroupVolume -> {
            val groupName =
                mixer.groups.values.find { it.id == expr.groupId }?.name
                    ?: error("Unknown mixer group ID: ${expr.groupId}")
            "_mixer_${groupName}_volume"
        }
        is IRMixerGroupIsMuted -> {
            val groupName =
                mixer.groups.values.find { it.id == expr.groupId }?.name
                    ?: error("Unknown mixer group ID: ${expr.groupId}")
            "_mixer_${groupName}_muted"
        }
        is IRMixerGroupIsFading -> {
            val groupName =
                mixer.groups.values.find { it.id == expr.groupId }?.name
                    ?: error("Unknown mixer group ID: ${expr.groupId}")
            "_mixer_${groupName}_fade_active"
        }
        else -> null
    }
}
