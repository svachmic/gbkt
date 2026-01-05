/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import io.github.gbkt.core.entity.*
import io.github.gbkt.core.graphics.*
import io.github.gbkt.core.ir.*
import kotlin.test.*

/** Tests for audio mixer system - channel groups, volume control, and priority. */
class AudioMixerTest {

    @Test
    fun `mixer definition generates group data`() {
        val game =
            gbGame("test") {
                val mixer = audioMixer {
                    group("sfx") {
                        channels(Channel.PULSE1, Channel.NOISE)
                        volume = 100
                        priority = MixerPriority.HIGH
                    }
                    group("music") {
                        channels(Channel.PULSE2, Channel.WAVE)
                        volume = 70
                        priority = MixerPriority.LOW
                    }
                }

                start = scene("main") {}
            }

        val code = game.compileForTest()

        // Should have mixer group count
        assertTrue(code.contains("#define MIXER_GROUP_COUNT 2"), "Should define group count")

        // Should have group ID constants
        assertTrue(code.contains("MIXER_GROUP_SFX"), "Should define SFX group ID")
        assertTrue(code.contains("MIXER_GROUP_MUSIC"), "Should define MUSIC group ID")

        // Should have per-group state variables
        assertTrue(code.contains("_mixer_sfx_volume = 100"), "Should have SFX volume at 100")
        assertTrue(code.contains("_mixer_music_volume = 70"), "Should have music volume at 70")

        // Should have priority values
        assertTrue(code.contains("_mixer_sfx_priority"), "Should have SFX priority")
        assertTrue(code.contains("_mixer_music_priority"), "Should have music priority")
    }

    @Test
    fun `setGroupVolume generates correct code`() {
        val game =
            gbGame("test") {
                val mixer = audioMixer {
                    group("sfx") {
                        channels(Channel.PULSE1)
                        volume = 100
                    }
                }

                start = scene("main") { enter { mixer.setGroupVolume("sfx", 50) } }
            }

        val code = game.compileForTest()

        // Should set volume in scene enter
        assertTrue(code.contains("_mixer_sfx_volume"), "Should reference sfx volume variable")
        assertTrue(code.contains("50"), "Should set volume to 50")
        assertTrue(code.contains("_mixer_apply_volume()"), "Should apply volume after setting")
    }

    @Test
    fun `fadeGroup generates fade state code`() {
        val game =
            gbGame("test") {
                val mixer = audioMixer {
                    group("music") {
                        channels(Channel.WAVE)
                        volume = 100
                    }
                }

                start = scene("main") { enter { mixer.fadeGroup("music", to = 0, over = 30) } }
            }

        val code = game.compileForTest()

        // Should set fade state variables
        assertTrue(code.contains("_mixer_music_fade_start"), "Should store fade start")
        assertTrue(code.contains("_mixer_music_fade_target = 0"), "Should store fade target")
        assertTrue(code.contains("_mixer_music_fade_duration = 30"), "Should store fade duration")
        assertTrue(code.contains("_mixer_music_fade_active = 1"), "Should activate fade")
    }

    @Test
    fun `muteGroup generates mute code`() {
        val game =
            gbGame("test") {
                val mixer = audioMixer { group("sfx") { channels(Channel.PULSE1, Channel.NOISE) } }

                start = scene("main") { enter { mixer.muteGroup("sfx") } }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("_mixer_sfx_muted = 1"), "Should set muted flag")
        assertTrue(code.contains("_mixer_apply_volume()"), "Should apply volume after muting")
    }

    @Test
    fun `unmuteGroup generates unmute code`() {
        val game =
            gbGame("test") {
                val mixer = audioMixer { group("sfx") { channels(Channel.PULSE1) } }

                start = scene("main") { enter { mixer.unmuteGroup("sfx") } }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("_mixer_sfx_muted = 0"), "Should clear muted flag")
    }

    @Test
    fun `sound play checks mixer priority`() {
        val game =
            gbGame("test") {
                audioMixer {
                    group("sfx") {
                        channels(Channel.PULSE1)
                        priority = MixerPriority.HIGH
                    }
                }

                val jump = soundEffect("jump") { preset = SoundPreset.JUMP }

                start =
                    scene("main") { every.frame { whenever(buttons.a.pressed) { jump.play() } } }
            }

        val code = game.compileForTest()

        // Sound play should check mixer priority
        assertTrue(code.contains("_mixer_can_play"), "Should check mixer priority before playing")
        assertTrue(code.contains("_mixer_is_muted"), "Should check mute state before playing")
    }

    @Test
    fun `channel to group mapping is generated`() {
        val game =
            gbGame("test") {
                audioMixer {
                    group("sfx") {
                        channels(Channel.PULSE1, Channel.NOISE) // Channels 0 and 3
                    }
                    group("music") {
                        channels(Channel.PULSE2, Channel.WAVE) // Channels 1 and 2
                    }
                }

                start = scene("main") {}
            }

        val code = game.compileForTest()

        // Should have channel-to-group lookup array
        assertTrue(code.contains("_mixer_channel_group[4]"), "Should have 4-channel lookup array")
    }

    @Test
    fun `mixer generates set volume function`() {
        val game =
            gbGame("test") {
                audioMixer { group("sfx") { channels(Channel.PULSE1) } }

                start = scene("main") {}
            }

        val code = game.compileForTest()

        assertTrue(code.contains("void mixer_set_volume("), "Should generate set volume function")
        assertTrue(code.contains("if (volume > 100) volume = 100"), "Should clamp volume to 100")
    }

    @Test
    fun `mixer generates fade update function`() {
        val game =
            gbGame("test") {
                audioMixer { group("music") { channels(Channel.WAVE) } }

                start = scene("main") {}
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("void _mixer_fade_update("),
            "Should generate fade update function",
        )
        assertTrue(code.contains("_mixer_music_fade_active"), "Should check fade active flag")
    }

    @Test
    fun `mixer generates priority check function`() {
        val game =
            gbGame("test") {
                audioMixer {
                    group("sfx") {
                        channels(Channel.PULSE1)
                        priority = MixerPriority.HIGH
                    }
                }

                start = scene("main") {}
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("UINT8 _mixer_can_play("),
            "Should generate priority check function",
        )
        assertTrue(code.contains("sound_priority >= group_priority"), "Should compare priorities")
    }

    @Test
    fun `mixer rejects overlapping channels`() {
        val exception = assertFails {
            gbGame("test") {
                audioMixer {
                    group("sfx") { channels(Channel.PULSE1) }
                    group("effects") { channels(Channel.PULSE1) } // Same channel!
                }
                start = scene("main") {}
            }
        }

        assertTrue(
            exception.message?.contains("already assigned") == true,
            "Should reject overlapping channels",
        )
    }

    @Test
    fun `only one audioMixer allowed per game`() {
        val exception = assertFails {
            gbGame("test") {
                audioMixer { group("a") { channels(Channel.PULSE1) } }
                audioMixer { group("b") { channels(Channel.PULSE2) } }
                start = scene("main") {}
            }
        }

        assertTrue(
            exception.message?.contains("Only one") == true,
            "Should reject multiple audioMixer blocks",
        )
    }

    @Test
    fun `group must have at least one channel`() {
        val exception = assertFails {
            gbGame("test") {
                audioMixer {
                    group("empty") {
                        // No channels added
                        volume = 100
                    }
                }
                start = scene("main") {}
            }
        }

        assertTrue(
            exception.message?.contains("at least one channel") == true,
            "Should reject empty channel groups",
        )
    }

    @Test
    fun `volume is clamped to valid range`() {
        val game =
            gbGame("test") {
                audioMixer {
                    group("sfx") {
                        channels(Channel.PULSE1)
                        volume = 150 // Over 100!
                    }
                }

                start = scene("main") {}
            }

        // Should compile without error (volume clamped to 100)
        val code = game.compileForTest()
        assertTrue(code.contains("_mixer_sfx_volume = 100"), "Should clamp volume to 100")
    }

    @Test
    fun `toggleMuteGroup generates toggle code`() {
        val game =
            gbGame("test") {
                val mixer = audioMixer { group("sfx") { channels(Channel.PULSE1) } }

                start = scene("main") { enter { mixer.toggleMuteGroup("sfx") } }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("!_mixer_sfx_muted"), "Should toggle muted state")
    }

    @Test
    fun `mixer generates apply volume function`() {
        val game =
            gbGame("test") {
                audioMixer { group("sfx") { channels(Channel.PULSE1) } }

                start = scene("main") {}
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("void _mixer_apply_volume("),
            "Should generate apply volume function",
        )
        assertTrue(code.contains("NR50_REG"), "Should set master volume register")
    }

    @Test
    fun `fadeGroup with FrameTiming syntax works`() {
        val game =
            gbGame("test") {
                val mixer = audioMixer { group("music") { channels(Channel.WAVE) } }

                start =
                    scene("main") { enter { mixer.fadeGroup("music", to = 0, over = 30.frames) } }
            }

        val code = game.compileForTest()

        assertTrue(
            code.contains("_mixer_music_fade_duration = 30"),
            "Should accept FrameTiming syntax",
        )
    }

    // =========================================================================
    // ERROR PATH TESTS
    // =========================================================================

    @Test
    fun `setGroupVolume with unknown group throws`() {
        val exception = assertFails {
            gbGame("test") {
                val mixer = audioMixer { group("sfx") { channels(Channel.PULSE1) } }

                start = scene("main") { enter { mixer.setGroupVolume("nonexistent", 50) } }
            }
        }

        assertTrue(
            exception.message?.contains("Unknown group") == true ||
                exception.message?.contains("nonexistent") == true,
            "Should reject unknown group name. Got: ${exception.message}",
        )
    }

    @Test
    fun `fadeGroup with unknown group throws`() {
        val exception = assertFails {
            gbGame("test") {
                val mixer = audioMixer { group("music") { channels(Channel.WAVE) } }

                start = scene("main") { enter { mixer.fadeGroup("unknown", to = 0, over = 30) } }
            }
        }

        assertTrue(
            exception.message?.contains("Unknown group") == true ||
                exception.message?.contains("unknown") == true,
            "Should reject unknown group name. Got: ${exception.message}",
        )
    }

    @Test
    fun `muteGroup with unknown group throws`() {
        val exception = assertFails {
            gbGame("test") {
                val mixer = audioMixer { group("sfx") { channels(Channel.PULSE1) } }

                start = scene("main") { enter { mixer.muteGroup("invalid") } }
            }
        }

        assertTrue(
            exception.message?.contains("Unknown group") == true ||
                exception.message?.contains("invalid") == true,
            "Should reject unknown group name. Got: ${exception.message}",
        )
    }

    @Test
    fun `unmuteGroup with unknown group throws`() {
        val exception = assertFails {
            gbGame("test") {
                val mixer = audioMixer { group("sfx") { channels(Channel.PULSE1) } }

                start = scene("main") { enter { mixer.unmuteGroup("bad_group") } }
            }
        }

        assertTrue(
            exception.message?.contains("Unknown group") == true ||
                exception.message?.contains("bad_group") == true,
            "Should reject unknown group name. Got: ${exception.message}",
        )
    }

    @Test
    fun `toggleMuteGroup with unknown group throws`() {
        val exception = assertFails {
            gbGame("test") {
                val mixer = audioMixer { group("sfx") { channels(Channel.PULSE1) } }

                start = scene("main") { enter { mixer.toggleMuteGroup("missing") } }
            }
        }

        assertTrue(
            exception.message?.contains("Unknown group") == true ||
                exception.message?.contains("missing") == true,
            "Should reject unknown group name. Got: ${exception.message}",
        )
    }

    @Test
    fun `negative volume is clamped to 0`() {
        val game =
            gbGame("test") {
                audioMixer {
                    group("sfx") {
                        channels(Channel.PULSE1)
                        volume = -50 // Negative!
                    }
                }

                start = scene("main") {}
            }

        // Should compile without error (volume clamped to 0)
        val code = game.compileForTest()
        assertTrue(code.contains("_mixer_sfx_volume = 0"), "Should clamp volume to 0")
    }

    @Test
    fun `all four channels can be assigned to different groups`() {
        val game =
            gbGame("test") {
                audioMixer {
                    group("group1") { channels(Channel.PULSE1) }
                    group("group2") { channels(Channel.PULSE2) }
                    group("group3") { channels(Channel.WAVE) }
                    group("group4") { channels(Channel.NOISE) }
                }

                start = scene("main") {}
            }

        val code = game.compileForTest()

        // Should have all four groups
        assertTrue(code.contains("#define MIXER_GROUP_COUNT 4"), "Should have 4 groups")
        assertTrue(code.contains("MIXER_GROUP_GROUP1"), "Should have group1 ID")
        assertTrue(code.contains("MIXER_GROUP_GROUP2"), "Should have group2 ID")
        assertTrue(code.contains("MIXER_GROUP_GROUP3"), "Should have group3 ID")
        assertTrue(code.contains("MIXER_GROUP_GROUP4"), "Should have group4 ID")
    }

    @Test
    fun `mixer with single group works`() {
        val game =
            gbGame("test") {
                val mixer = audioMixer {
                    group("all") { channels(Channel.PULSE1, Channel.PULSE2, Channel.WAVE, Channel.NOISE) }
                }

                start = scene("main") { enter { mixer.setGroupVolume("all", 75) } }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("#define MIXER_GROUP_COUNT 1"), "Should have 1 group")
        assertTrue(code.contains("_mixer_all_volume"), "Should have all group volume")
    }

    @Test
    fun `multiple priority levels work correctly`() {
        val game =
            gbGame("test") {
                audioMixer {
                    group("low") {
                        channels(Channel.NOISE)
                        priority = MixerPriority.LOW
                    }
                    group("normal") {
                        channels(Channel.PULSE1)
                        priority = MixerPriority.NORMAL
                    }
                    group("high") {
                        channels(Channel.PULSE2, Channel.WAVE)
                        priority = MixerPriority.HIGH
                    }
                }

                start = scene("main") {}
            }

        val code = game.compileForTest()

        // Should have all priority levels defined
        assertTrue(code.contains("_mixer_low_priority"), "Should have low priority")
        assertTrue(code.contains("_mixer_normal_priority"), "Should have normal priority")
        assertTrue(code.contains("_mixer_high_priority"), "Should have high priority")
    }

    // =========================================================================
    // MIXER UTILITY METHOD TESTS
    // =========================================================================

    @Test
    fun `getGroupPriority returns correct priority`() {
        lateinit var mixer: AudioMixer
        gbGame("test") {
            mixer = audioMixer {
                group("sfx") {
                    channels(Channel.PULSE1)
                    priority = MixerPriority.HIGH
                }
                group("music") {
                    channels(Channel.WAVE)
                    priority = MixerPriority.LOW
                }
            }
            start = scene("main") {}
        }

        assertEquals(MixerPriority.HIGH, mixer.getGroupPriority("sfx"))
        assertEquals(MixerPriority.LOW, mixer.getGroupPriority("music"))
    }

    @Test
    fun `getGroupPriority returns NORMAL for unknown group`() {
        lateinit var mixer: AudioMixer
        gbGame("test") {
            mixer = audioMixer { group("sfx") { channels(Channel.PULSE1) } }
            start = scene("main") {}
        }

        assertEquals(MixerPriority.NORMAL, mixer.getGroupPriority("unknown"))
    }

    @Test
    fun `getGroupForChannel returns correct group`() {
        lateinit var mixer: AudioMixer
        gbGame("test") {
            mixer = audioMixer {
                group("sfx") { channels(Channel.PULSE1, Channel.NOISE) }
                group("music") { channels(Channel.WAVE) }
            }
            start = scene("main") {}
        }

        assertEquals("sfx", mixer.getGroupForChannel(Channel.PULSE1)?.name)
        assertEquals("sfx", mixer.getGroupForChannel(Channel.NOISE)?.name)
        assertEquals("music", mixer.getGroupForChannel(Channel.WAVE)?.name)
    }

    @Test
    fun `getGroupForChannel returns null for unassigned channel`() {
        lateinit var mixer: AudioMixer
        gbGame("test") {
            mixer = audioMixer { group("sfx") { channels(Channel.PULSE1) } }
            start = scene("main") {}
        }

        assertNull(mixer.getGroupForChannel(Channel.PULSE2))
        assertNull(mixer.getGroupForChannel(Channel.WAVE))
        assertNull(mixer.getGroupForChannel(Channel.NOISE))
    }

    @Test
    fun `canPlayOnChannel allows HIGH priority on LOW priority channel`() {
        lateinit var mixer: AudioMixer
        gbGame("test") {
            mixer = audioMixer {
                group("ambient") {
                    channels(Channel.WAVE)
                    priority = MixerPriority.LOW
                }
            }
            start = scene("main") {}
        }

        assertTrue(mixer.canPlayOnChannel(Channel.WAVE, SoundPriority.HIGH))
        assertTrue(mixer.canPlayOnChannel(Channel.WAVE, SoundPriority.NORMAL))
        assertTrue(mixer.canPlayOnChannel(Channel.WAVE, SoundPriority.LOW))
    }

    @Test
    fun `canPlayOnChannel blocks LOW priority on HIGH priority channel`() {
        lateinit var mixer: AudioMixer
        gbGame("test") {
            mixer = audioMixer {
                group("critical") {
                    channels(Channel.PULSE1)
                    priority = MixerPriority.HIGH
                }
            }
            start = scene("main") {}
        }

        assertTrue(mixer.canPlayOnChannel(Channel.PULSE1, SoundPriority.HIGH))
        assertFalse(mixer.canPlayOnChannel(Channel.PULSE1, SoundPriority.NORMAL))
        assertFalse(mixer.canPlayOnChannel(Channel.PULSE1, SoundPriority.LOW))
    }

    @Test
    fun `canPlayOnChannel allows any priority on unassigned channel`() {
        lateinit var mixer: AudioMixer
        gbGame("test") {
            mixer = audioMixer { group("sfx") { channels(Channel.PULSE1) } }
            start = scene("main") {}
        }

        // PULSE2 is not in any group, so any priority should work
        assertTrue(mixer.canPlayOnChannel(Channel.PULSE2, SoundPriority.LOW))
        assertTrue(mixer.canPlayOnChannel(Channel.PULSE2, SoundPriority.NORMAL))
        assertTrue(mixer.canPlayOnChannel(Channel.PULSE2, SoundPriority.HIGH))
    }

    @Test
    fun `setGroupVolume with expression generates correct code`() {
        val game =
            gbGame("test") {
                val mixer = audioMixer { group("sfx") { channels(Channel.PULSE1) } }
                val dynamicVol by u8Var(50)

                start =
                    scene("main") { enter { mixer.setGroupVolume("sfx", dynamicVol) } }
            }

        val code = game.compileForTest()

        assertTrue(code.contains("_mixer_sfx_volume"), "Should reference sfx volume variable")
        assertTrue(code.contains("dynamicVol") || code.contains("_v_"), "Should use dynamic variable")
    }

    @Test
    fun `channel method adds single channel to group`() {
        val game =
            gbGame("test") {
                audioMixer {
                    group("solo") {
                        channel(Channel.WAVE)
                        volume = 80
                    }
                }

                start = scene("main") {}
            }

        val code = game.compileForTest()

        assertTrue(code.contains("MIXER_GROUP_SOLO"), "Should define solo group ID")
        assertTrue(code.contains("_mixer_solo_volume = 80"), "Should have volume 80")
    }

    @Test
    fun `ChannelGroup validates volume range in constructor`() {
        val exception = assertFails {
            ChannelGroup(
                name = "invalid",
                channels = setOf(Channel.PULSE1),
                volume = 150,
            )
        }

        assertTrue(
            exception.message?.contains("Volume must be 0-100") == true,
            "Should reject volume > 100",
        )
    }

    @Test
    fun `ChannelGroup validates non-empty channels in constructor`() {
        val exception = assertFails {
            ChannelGroup(
                name = "empty",
                channels = emptySet(),
                volume = 100,
            )
        }

        assertTrue(
            exception.message?.contains("at least one channel") == true,
            "Should reject empty channel set",
        )
    }

    @Test
    fun `mixer with no groups throws`() {
        val exception = assertFails {
            gbGame("test") {
                audioMixer {
                    // No groups defined
                }
                start = scene("main") {}
            }
        }

        assertTrue(
            exception.message?.contains("at least one group") == true,
            "Should reject empty mixer",
        )
    }

    @Test
    fun `MixerPriority enum has correct values`() {
        assertEquals(0, MixerPriority.LOW.value)
        assertEquals(1, MixerPriority.NORMAL.value)
        assertEquals(2, MixerPriority.HIGH.value)
    }

    @Test
    fun `groups are assigned sequential IDs`() {
        lateinit var mixer: AudioMixer
        gbGame("test") {
            mixer = audioMixer {
                group("first") { channels(Channel.PULSE1) }
                group("second") { channels(Channel.PULSE2) }
                group("third") { channels(Channel.WAVE) }
            }
            start = scene("main") {}
        }

        val ids = mixer.groups.values.map { it.id }.sorted()
        assertEquals(listOf(0, 1, 2), ids, "Groups should have sequential IDs")
    }
}
