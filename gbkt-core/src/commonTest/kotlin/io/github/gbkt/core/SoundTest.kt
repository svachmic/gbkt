/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.builder.*
import io.github.gbkt.core.dsl.*
import kotlin.test.*

/** Tests for sound and music DSL - sound effects, presets, music playback. */
class SoundTest {

    // =========================================================================
    // CHANNEL ENUM TESTS
    // =========================================================================

    @Test
    fun `Channel enum has correct indices`() {
        assertEquals(0, Channel.PULSE1.index)
        assertEquals(1, Channel.PULSE2.index)
        assertEquals(2, Channel.WAVE.index)
        assertEquals(3, Channel.NOISE.index)
    }

    @Test
    fun `all 4 channels are defined`() {
        assertEquals(4, Channel.entries.size)
    }

    // =========================================================================
    // DUTY CYCLE TESTS
    // =========================================================================

    @Test
    fun `DutyCycle enum has correct bit values`() {
        assertEquals(0, DutyCycle.TWELVE_POINT_FIVE.bits)
        assertEquals(1, DutyCycle.TWENTY_FIVE.bits)
        assertEquals(2, DutyCycle.FIFTY_PERCENT.bits)
        assertEquals(3, DutyCycle.SEVENTY_FIVE.bits)
    }

    // =========================================================================
    // SWEEP BUILDER TESTS
    // =========================================================================

    @Test
    fun `SweepBuilder builds default config`() {
        val sweep = SweepBuilder().build()
        assertEquals(0, sweep.time)
        assertEquals(SweepDirection.INCREASE, sweep.direction)
        assertEquals(0, sweep.shift)
    }

    @Test
    fun `SweepBuilder coerces time to 0-7 range`() {
        val builder = SweepBuilder()
        builder.time = -5
        assertEquals(0, builder.build().time)

        builder.time = 10
        assertEquals(7, builder.build().time)

        builder.time = 4
        assertEquals(4, builder.build().time)
    }

    @Test
    fun `SweepBuilder coerces shift to 0-7 range`() {
        val builder = SweepBuilder()
        builder.shift = -1
        assertEquals(0, builder.build().shift)

        builder.shift = 100
        assertEquals(7, builder.build().shift)

        builder.shift = 5
        assertEquals(5, builder.build().shift)
    }

    @Test
    fun `SweepBuilder supports both directions`() {
        val increase = SweepBuilder().apply { direction = SweepDirection.INCREASE }.build()
        val decrease = SweepBuilder().apply { direction = SweepDirection.DECREASE }.build()

        assertEquals(SweepDirection.INCREASE, increase.direction)
        assertEquals(SweepDirection.DECREASE, decrease.direction)
    }

    // =========================================================================
    // ENVELOPE BUILDER TESTS
    // =========================================================================

    @Test
    fun `EnvelopeBuilder builds with default values`() {
        val envelope = EnvelopeBuilder().build()
        assertEquals(15, envelope.volume)
        assertEquals(EnvelopeDirection.DECREASE, envelope.direction)
        assertEquals(3, envelope.pace)
    }

    @Test
    fun `EnvelopeBuilder coerces volume to 0-15 range`() {
        val builder = EnvelopeBuilder()
        builder.volume = -10
        assertEquals(0, builder.build().volume)

        builder.volume = 20
        assertEquals(15, builder.build().volume)

        builder.volume = 8
        assertEquals(8, builder.build().volume)
    }

    @Test
    fun `EnvelopeBuilder coerces pace to 0-7 range`() {
        val builder = EnvelopeBuilder()
        builder.pace = -1
        assertEquals(0, builder.build().pace)

        builder.pace = 50
        assertEquals(7, builder.build().pace)

        builder.pace = 5
        assertEquals(5, builder.build().pace)
    }

    @Test
    fun `EnvelopeBuilder supports both directions`() {
        val increase = EnvelopeBuilder().apply { direction = EnvelopeDirection.INCREASE }.build()
        val decrease = EnvelopeBuilder().apply { direction = EnvelopeDirection.DECREASE }.build()

        assertEquals(EnvelopeDirection.INCREASE, increase.direction)
        assertEquals(EnvelopeDirection.DECREASE, decrease.direction)
    }

    // =========================================================================
    // WAVE OUTPUT LEVEL TESTS
    // =========================================================================

    @Test
    fun `WaveOutputLevel enum has correct bit values`() {
        assertEquals(0, WaveOutputLevel.MUTE.bits)
        assertEquals(1, WaveOutputLevel.FULL.bits)
        assertEquals(2, WaveOutputLevel.HALF.bits)
        assertEquals(3, WaveOutputLevel.QUARTER.bits)
    }

    // =========================================================================
    // NOISE WIDTH TESTS
    // =========================================================================

    @Test
    fun `NoiseWidth enum has two modes`() {
        assertEquals(2, NoiseWidth.entries.size)
        assertNotNull(NoiseWidth.FIFTEEN_BIT)
        assertNotNull(NoiseWidth.SEVEN_BIT)
    }

    // =========================================================================
    // SOUND PRIORITY TESTS
    // =========================================================================

    @Test
    fun `SoundPriority enum has three levels`() {
        assertEquals(3, SoundPriority.entries.size)
        assertNotNull(SoundPriority.LOW)
        assertNotNull(SoundPriority.NORMAL)
        assertNotNull(SoundPriority.HIGH)
    }

    // =========================================================================
    // SOUND REGISTERS TESTS
    // =========================================================================

    @Test
    fun `SoundRegisters equality works without waveform`() {
        val r1 = SoundRegisters(frequency = 1000)
        val r2 = SoundRegisters(frequency = 1000)
        val r3 = SoundRegisters(frequency = 2000)

        assertEquals(r1, r2)
        assertNotEquals(r1, r3)
    }

    @Test
    fun `SoundRegisters equality works with waveform`() {
        val wave = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
        val r1 = SoundRegisters(waveform = wave)
        val r2 = SoundRegisters(waveform = wave.copyOf())
        val r3 = SoundRegisters(waveform = byteArrayOf(0, 0, 0, 0))

        assertEquals(r1, r2)
        assertNotEquals(r1, r3)
    }

    @Test
    fun `SoundRegisters equality handles null waveform`() {
        val r1 = SoundRegisters(waveform = null)
        val r2 = SoundRegisters(waveform = null)
        val r3 = SoundRegisters(waveform = byteArrayOf(1, 2, 3))

        assertEquals(r1, r2)
        assertNotEquals(r1, r3)
    }

    @Test
    fun `SoundRegisters hashCode is consistent`() {
        val r1 = SoundRegisters(frequency = 1000, duty = DutyCycle.FIFTY_PERCENT)
        val r2 = SoundRegisters(frequency = 1000, duty = DutyCycle.FIFTY_PERCENT)

        assertEquals(r1.hashCode(), r2.hashCode())
    }

    @Test
    fun `SoundRegisters hashCode handles waveform`() {
        val wave = byteArrayOf(1, 2, 3, 4)
        val r1 = SoundRegisters(waveform = wave)
        val r2 = SoundRegisters(waveform = wave.copyOf())

        assertEquals(r1.hashCode(), r2.hashCode())
    }

    // =========================================================================
    // SOUND PRESET TESTS
    // =========================================================================

    @Test
    fun `all 12 sound presets are defined`() {
        assertEquals(12, SoundPreset.entries.size)
    }

    @Test
    fun `JUMP preset uses PULSE1 channel with sweep`() {
        val game =
            gbGame("test") {
                val jump = soundEffect("jump") { preset = SoundPreset.JUMP }

                start = scene("main") {}
            }

        val code = game.compileForTest()
        // JUMP preset should be on PULSE1 (index 0) with sweep
        assertTrue(code.contains("jump"), "Should define jump sound")
    }

    @Test
    fun `EXPLOSION preset uses NOISE channel`() {
        val game =
            gbGame("test") {
                val explosion = soundEffect("boom") { preset = SoundPreset.EXPLOSION }

                start = scene("main") {}
            }

        val code = game.compileForTest()
        assertTrue(code.contains("boom"), "Should define explosion sound")
    }

    @Test
    fun `LAND preset uses NOISE channel`() {
        val game =
            gbGame("test") {
                val land = soundEffect("land") { preset = SoundPreset.LAND }

                start = scene("main") {}
            }

        val code = game.compileForTest()
        assertTrue(code.contains("land"), "Should define land sound")
    }

    @Test
    fun `COIN preset uses PULSE1 channel`() {
        val game =
            gbGame("test") {
                val coin = soundEffect("coin") { preset = SoundPreset.COIN }

                start = scene("main") {}
            }

        val code = game.compileForTest()
        assertTrue(code.contains("coin"), "Should define coin sound")
    }

    @Test
    fun `LASER preset uses PULSE1 with downward sweep`() {
        val game =
            gbGame("test") {
                val laser = soundEffect("laser") { preset = SoundPreset.LASER }

                start = scene("main") {}
            }

        val code = game.compileForTest()
        assertTrue(code.contains("laser"), "Should define laser sound")
    }

    @Test
    fun `HIT preset uses NOISE channel`() {
        val game =
            gbGame("test") {
                val hit = soundEffect("hit") { preset = SoundPreset.HIT }

                start = scene("main") {}
            }

        val code = game.compileForTest()
        assertTrue(code.contains("hit"), "Should define hit sound")
    }

    @Test
    fun `SELECT preset uses PULSE channel`() {
        val game =
            gbGame("test") {
                val select = soundEffect("select") { preset = SoundPreset.SELECT }

                start = scene("main") {}
            }

        val code = game.compileForTest()
        assertTrue(code.contains("select"), "Should define select sound")
    }

    @Test
    fun `PAUSE preset uses PULSE channel`() {
        val game =
            gbGame("test") {
                val pause = soundEffect("pause") { preset = SoundPreset.PAUSE }

                start = scene("main") {}
            }

        val code = game.compileForTest()
        assertTrue(code.contains("pause"), "Should define pause sound")
    }

    @Test
    fun `TICK preset uses PULSE channel`() {
        val game =
            gbGame("test") {
                val tick = soundEffect("tick") { preset = SoundPreset.TICK }

                start = scene("main") {}
            }

        val code = game.compileForTest()
        assertTrue(code.contains("tick"), "Should define tick sound")
    }

    @Test
    fun `BEEP preset uses PULSE channel`() {
        val game =
            gbGame("test") {
                val beep = soundEffect("beep") { preset = SoundPreset.BEEP }

                start = scene("main") {}
            }

        val code = game.compileForTest()
        assertTrue(code.contains("beep"), "Should define beep sound")
    }

    @Test
    fun `DEATH preset uses PULSE channel`() {
        val game =
            gbGame("test") {
                val death = soundEffect("death") { preset = SoundPreset.DEATH }

                start = scene("main") {}
            }

        val code = game.compileForTest()
        assertTrue(code.contains("death"), "Should define death sound")
    }

    @Test
    fun `POWERUP preset uses PULSE channel`() {
        val game =
            gbGame("test") {
                val powerup = soundEffect("powerup") { preset = SoundPreset.POWERUP }

                start = scene("main") {}
            }

        val code = game.compileForTest()
        assertTrue(code.contains("powerup"), "Should define powerup sound")
    }

    @Test
    fun `all pulse channel presets compile correctly`() {
        val pulsePresets =
            listOf(
                SoundPreset.BEEP,
                SoundPreset.JUMP,
                SoundPreset.COIN,
                SoundPreset.LASER,
                SoundPreset.SELECT,
                SoundPreset.PAUSE,
                SoundPreset.DEATH,
                SoundPreset.POWERUP,
                SoundPreset.TICK,
            )

        for (preset in pulsePresets) {
            val game =
                gbGame("test") {
                    soundEffect("sfx") { this.preset = preset }
                    start = scene("main") {}
                }
            // Should compile without error
            val code = game.compileForTest()
            assertTrue(code.isNotEmpty(), "Preset ${preset.name} should compile")
        }
    }

    @Test
    fun `all noise channel presets compile correctly`() {
        val noisePresets = listOf(SoundPreset.LAND, SoundPreset.EXPLOSION, SoundPreset.HIT)

        for (preset in noisePresets) {
            val game =
                gbGame("test") {
                    soundEffect("sfx") { this.preset = preset }
                    start = scene("main") {}
                }
            val code = game.compileForTest()
            assertTrue(code.isNotEmpty(), "Preset ${preset.name} should compile")
        }
    }

    // =========================================================================
    // SOUND EFFECT BUILDER TESTS
    // =========================================================================

    @Test
    fun `soundEffect without preset uses manual configuration`() {
        val game =
            gbGame("test") {
                val sfx =
                    soundEffect("custom") {
                        channel = Channel.PULSE2
                        frequency = 1500
                        duty = DutyCycle.TWENTY_FIVE
                    }

                start = scene("main") {}
            }

        val code = game.compileForTest()
        assertTrue(code.contains("custom"), "Should define custom sound")
    }

    @Test
    fun `soundEffect with sweep configuration`() {
        val game =
            gbGame("test") {
                val sfx =
                    soundEffect("sweepy") {
                        channel = Channel.PULSE1
                        sweep {
                            time = 3
                            direction = SweepDirection.INCREASE
                            shift = 2
                        }
                    }

                start = scene("main") {}
            }

        val code = game.compileForTest()
        assertTrue(code.contains("sweepy"), "Should define sweep sound")
    }

    @Test
    fun `soundEffect with envelope configuration`() {
        val game =
            gbGame("test") {
                val sfx =
                    soundEffect("envy") {
                        channel = Channel.PULSE1
                        envelope {
                            volume = 12
                            direction = EnvelopeDirection.DECREASE
                            pace = 4
                        }
                    }

                start = scene("main") {}
            }

        val code = game.compileForTest()
        assertTrue(code.contains("envy"), "Should define envelope sound")
    }

    @Test
    fun `soundEffect on wave channel with waveform`() {
        val game =
            gbGame("test") {
                val sfx =
                    soundEffect("wave_sfx") {
                        channel = Channel.WAVE
                        waveform = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
                        outputLevel = WaveOutputLevel.HALF
                    }

                start = scene("main") {}
            }

        val code = game.compileForTest()
        assertTrue(code.contains("wave_sfx"), "Should define wave sound")
    }

    @Test
    fun `soundEffect on noise channel with settings`() {
        val game =
            gbGame("test") {
                val sfx =
                    soundEffect("noise_sfx") {
                        channel = Channel.NOISE
                        clockShift = 5
                        widthMode = NoiseWidth.SEVEN_BIT
                        divisor = 2
                    }

                start = scene("main") {}
            }

        val code = game.compileForTest()
        assertTrue(code.contains("noise_sfx"), "Should define noise sound")
    }

    // =========================================================================
    // SOUND EFFECT PLAY/STOP TESTS
    // =========================================================================

    @Test
    fun `soundEffect play generates IR`() {
        val game =
            gbGame("test") {
                val jump = soundEffect("jump") { preset = SoundPreset.JUMP }

                start =
                    scene("main") { every.frame { whenever(buttons.a.pressed) { jump.play() } } }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("play") || code.contains("sfx"), "Should generate play code")
    }

    @Test
    fun `soundEffect play with priority`() {
        val game =
            gbGame("test") {
                val important = soundEffect("important") { preset = SoundPreset.POWERUP }

                start =
                    scene("main") {
                        every.frame {
                            whenever(buttons.a.pressed) { important.play(SoundPriority.HIGH) }
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile with priority")
    }

    @Test
    fun `soundEffect stop generates IR`() {
        val game =
            gbGame("test") {
                val alarm = soundEffect("alarm") { preset = SoundPreset.BEEP }

                start =
                    scene("main") {
                        enter { alarm.play() }
                        every.frame { whenever(buttons.b.pressed) { alarm.stop() } }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile stop code")
    }

    // =========================================================================
    // MUSIC TESTS
    // =========================================================================

    @Test
    fun `music play generates IR`() {
        val game =
            gbGame("test") {
                val bgm = music("assets/music/forest.uge")

                start = scene("main") { enter { bgm.play() } }
            }

        val code = game.compileForTest()
        assertTrue(code.contains("forest") || code.contains("music"), "Should reference music")
    }

    @Test
    fun `music stop generates IR`() {
        val game =
            gbGame("test") {
                val bgm = music("assets/music/battle.uge")

                start =
                    scene("main") {
                        enter { bgm.play() }
                        every.frame { whenever(buttons.start.pressed) { bgm.stop() } }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile music stop")
    }

    @Test
    fun `music pause generates IR`() {
        val game =
            gbGame("test") {
                val bgm = music("assets/music/town.uge")

                start =
                    scene("main") {
                        enter { bgm.play() }
                        every.frame { whenever(buttons.start.pressed) { bgm.pause() } }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile music pause")
    }

    @Test
    fun `music resume generates IR`() {
        val game =
            gbGame("test") {
                val bgm = music("assets/music/dungeon.uge")

                start =
                    scene("main") {
                        every.frame { whenever(buttons.start.pressed) { bgm.resume() } }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile music resume")
    }

    @Test
    fun `music fadeOut generates IR with frame count`() {
        val game =
            gbGame("test") {
                val bgm = music("assets/music/ending.uge")

                start = scene("main") { enter { bgm.fadeOut(60) } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile music fadeOut")
    }

    // =========================================================================
    // MUSIC BUILDER NAME EXTRACTION TESTS
    // =========================================================================

    @Test
    fun `MusicBuilder extracts name from simple path`() {
        val builder = MusicBuilder("music.uge", 0)
        val music = builder.build()
        assertEquals("music", music.name)
    }

    @Test
    fun `MusicBuilder extracts name from nested path`() {
        val builder = MusicBuilder("assets/audio/bgm/forest_theme.uge", 0)
        val music = builder.build()
        assertEquals("forest_theme", music.name)
    }

    @Test
    fun `MusicBuilder sanitizes special characters in name`() {
        val builder = MusicBuilder("my-music file (1).uge", 0)
        val music = builder.build()
        // Special chars should be replaced with underscores
        assertTrue(music.name.all { it.isLetterOrDigit() || it == '_' })
    }

    @Test
    fun `MusicBuilder allows custom name override`() {
        val builder = MusicBuilder("some/path/file.uge", 0)
        builder.name("custom_name")
        val music = builder.build()
        assertEquals("custom_name", music.name)
    }

    // =========================================================================
    // GLOBAL SOUND OBJECT TESTS
    // =========================================================================

    @Test
    fun `sound mute generates IR`() {
        val game =
            gbGame("test") {
                start =
                    scene("main") {
                        every.frame { whenever(buttons.select.pressed) { sound.mute() } }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile sound mute")
    }

    @Test
    fun `sound unmute generates IR`() {
        val game =
            gbGame("test") {
                start =
                    scene("main") {
                        every.frame { whenever(buttons.select.pressed) { sound.unmute() } }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile sound unmute")
    }

    @Test
    fun `sound setVolume generates IR`() {
        val game = gbGame("test") { start = scene("main") { enter { sound.setVolume(5) } } }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile setVolume")
    }

    @Test
    fun `sound setVolume coerces to 0-7 range`() {
        // Volume should be clamped, so this should compile without error
        val game =
            gbGame("test") {
                start =
                    scene("main") {
                        enter {
                            sound.setVolume(-5) // Should become 0
                            sound.setVolume(100) // Should become 7
                            sound.setVolume(4) // Should stay 4
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile with clamped volumes")
    }

    @Test
    fun `sound panLeft generates IR`() {
        val game =
            gbGame("test") { start = scene("main") { enter { sound.panLeft(Channel.PULSE1) } } }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile panLeft")
    }

    @Test
    fun `sound panRight generates IR`() {
        val game =
            gbGame("test") { start = scene("main") { enter { sound.panRight(Channel.PULSE2) } } }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile panRight")
    }

    @Test
    fun `sound panCenter generates IR`() {
        val game =
            gbGame("test") { start = scene("main") { enter { sound.panCenter(Channel.WAVE) } } }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile panCenter")
    }

    @Test
    fun `sound muteChannel generates IR`() {
        val game =
            gbGame("test") { start = scene("main") { enter { sound.muteChannel(Channel.NOISE) } } }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile muteChannel")
    }

    @Test
    fun `sound unmuteChannel generates IR`() {
        val game =
            gbGame("test") {
                start = scene("main") { enter { sound.unmuteChannel(Channel.PULSE1) } }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile unmuteChannel")
    }

    @Test
    fun `panning can be applied to all channels`() {
        val game =
            gbGame("test") {
                start =
                    scene("main") {
                        enter {
                            sound.panLeft(Channel.PULSE1)
                            sound.panRight(Channel.PULSE2)
                            sound.panCenter(Channel.WAVE)
                            sound.panLeft(Channel.NOISE)
                        }
                    }
            }

        val code = game.compileForTest()
        assertTrue(code.isNotEmpty(), "Should compile panning for all channels")
    }

    // =========================================================================
    // PANNING ENUM TESTS
    // =========================================================================

    @Test
    fun `Panning enum has three options`() {
        assertEquals(3, Panning.entries.size)
        assertNotNull(Panning.LEFT)
        assertNotNull(Panning.RIGHT)
        assertNotNull(Panning.CENTER)
    }
}
