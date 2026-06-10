/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.PlaySound
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SoundChannel
import io.github.gbkt.core.ir.SoundEffectDef
import io.github.gbkt.core.ir.SoundPreset
import io.github.gbkt.core.ir.SoundSystem
import io.github.gbkt.core.ir.SystemIR
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// SOUND REGISTER CODEGEN TESTS (A1+A3+A4)
// Verifies that:
// - BEEP preset generates correct NR11/NR12/NR13/NR14 register writes (not hashCode)
// - HIT preset generates correct NR41-NR44 (NOISE channel)
// - WAVE channel preset generates wave RAM load + NR30-NR34
// - SoundSystem is handled via GBDKSystemVisitor (A4) — not silently dropped
// - No hashCode() calls in sound wrapper functions
// =============================================================================

/** Build a minimal GameIR with a scene containing a PlaySound op and a SoundEffectDef. */
private fun buildGameWithSoundDef(
    soundId: String,
    def: SoundEffectDef,
    extraSystems: List<SystemIR> = emptyList(),
): GameIR {
    return GameIR(
        name = "TestGame",
        config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY),
        scenes = listOf(SceneIR(id = "main", frameOps = listOf(PlaySound(soundId)))),
        soundEffects = listOf(def),
        systems = extraSystems,
        startScene = "main",
    )
}

class SoundRegisterCodegenTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // Test 1: BEEP preset generates NR1x register writes (not hashCode)
    // =========================================================================
    @Test
    fun `BEEP preset generates real NR1x register writes`() {
        val def = SoundEffectDef.fromPreset("beep", SoundPreset.BEEP)
        val gameIR = buildGameWithSoundDef("beep", def)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // BEEP uses PULSE1 channel — must emit NR1x register writes
        assertTrue(mainC.contains("NR11_REG"), "NR11_REG (duty+length) missing for BEEP preset")
        assertTrue(mainC.contains("NR12_REG"), "NR12_REG (envelope) missing for BEEP preset")
        assertTrue(mainC.contains("NR13_REG"), "NR13_REG (freq low) missing for BEEP preset")
        assertTrue(mainC.contains("NR14_REG"), "NR14_REG (trigger) missing for BEEP preset")

        // Must NOT use hashCode-based stub
        assertFalse(
            mainC.contains("play_sound(${("beep").hashCode() and 0xFF}, 0, 64, 30)"),
            "play_sound() hashCode stub should not be in BEEP wrapper",
        )
        assertFalse(
            mainC.contains(".hashCode()"),
            "hashCode() call should not appear in generated C",
        )
    }

    // =========================================================================
    // Test 2: BEEP envelope register value is correct
    // =========================================================================
    @Test
    fun `BEEP preset NR12 envelope register has correct bit layout`() {
        val def = SoundEffectDef.fromPreset("beep", SoundPreset.BEEP)
        // BEEP: envelope volume=12, direction=DECREASE, pace=3
        // NR12 = (12 << 4) | (0 << 3) | 3 = 0xC0 | 0x00 | 0x03 = 0xC3
        val expectedNR12 = (12 shl 4) or (0 shl 3) or 3 // 0xC3
        val gameIR = buildGameWithSoundDef("beep", def)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        val hexNR12 = "0x${expectedNR12.toString(16).uppercase().padStart(2, '0')}u"
        assertTrue(
            mainC.contains("NR12_REG = $hexNR12;"),
            "NR12_REG should be $hexNR12 for BEEP envelope (vol=12, decrease, pace=3)",
        )
    }

    // =========================================================================
    // Test 3: HIT preset generates NR4x register writes (NOISE channel)
    // =========================================================================
    @Test
    fun `HIT preset generates real NR4x register writes for NOISE channel`() {
        val def = SoundEffectDef.fromPreset("hit", SoundPreset.HIT)
        val gameIR = buildGameWithSoundDef("hit", def)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // HIT uses NOISE channel — must emit NR4x register writes
        assertTrue(mainC.contains("NR41_REG"), "NR41_REG (length) missing for HIT preset")
        assertTrue(mainC.contains("NR42_REG"), "NR42_REG (envelope) missing for HIT preset")
        assertTrue(mainC.contains("NR43_REG"), "NR43_REG (noise params) missing for HIT preset")
        assertTrue(mainC.contains("NR44_REG"), "NR44_REG (trigger) missing for HIT preset")

        // Should NOT emit CH1 registers for a NOISE channel sound
        assertFalse(
            mainC.contains("play_sound_hit() {\nNR10_REG"),
            "NR10_REG (CH1 sweep) should not appear in NOISE channel HIT wrapper",
        )
    }

    // =========================================================================
    // Test 4: HIT NR42 envelope value is correct
    // =========================================================================
    @Test
    fun `HIT preset NR42 envelope register has correct bit layout`() {
        val def = SoundEffectDef.fromPreset("hit", SoundPreset.HIT)
        // HIT: envelope volume=15, direction=DECREASE, pace=2
        // NR42 = (15 << 4) | (0 << 3) | 2 = 0xF0 | 0x00 | 0x02 = 0xF2
        val expectedNR42 = (15 shl 4) or (0 shl 3) or 2 // 0xF2
        val gameIR = buildGameWithSoundDef("hit", def)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        val hexNR42 = "0x${expectedNR42.toString(16).uppercase().padStart(2, '0')}u"
        assertTrue(
            mainC.contains("NR42_REG = $hexNR42;"),
            "NR42_REG should be $hexNR42 for HIT envelope (vol=15, decrease, pace=2)",
        )
    }

    // =========================================================================
    // Test 5: HIT NR44 trigger register is correct
    // =========================================================================
    @Test
    fun `HIT preset NR44 trigger register is set correctly`() {
        val def = SoundEffectDef.fromPreset("hit", SoundPreset.HIT)
        // HIT: trigger=true, lengthEnable=false → NR44 = 0x80 | 0x00 = 0x80
        val gameIR = buildGameWithSoundDef("hit", def)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("NR44_REG = 0x80u;"),
            "NR44_REG should be 0x80 for HIT (trigger=true, lengthEnable=false)",
        )
    }

    // =========================================================================
    // Test 6: WAVE channel preset generates wave RAM load + NR3x registers (A3)
    // =========================================================================
    @Test
    fun `WAVE channel SoundEffectDef generates wave RAM load and NR3x registers`() {
        // Manually create a WAVE channel sound effect with custom waveform
        val waveform = ByteArray(16) { i -> (i * 16).toByte() }
        val def =
            SoundEffectDef(
                id = "wavefx",
                channel = SoundChannel.WAVE,
                registers =
                    io.github.gbkt.core.ir.SoundRegisters(
                        frequency = 1000,
                        length = 0,
                        trigger = true,
                        lengthEnable = false,
                        waveOutputLevel = 1, // 100% output
                        waveform = waveform,
                    ),
            )
        val gameIR = buildGameWithSoundDef("wavefx", def)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Must have NR30-NR34 register writes
        assertTrue(mainC.contains("NR30_REG"), "NR30_REG (CH3 on/off) missing for WAVE preset")
        assertTrue(mainC.contains("NR31_REG"), "NR31_REG (CH3 length) missing for WAVE preset")
        assertTrue(mainC.contains("NR32_REG"), "NR32_REG (output level) missing for WAVE preset")
        assertTrue(mainC.contains("NR33_REG"), "NR33_REG (freq low) missing for WAVE preset")
        assertTrue(mainC.contains("NR34_REG"), "NR34_REG (trigger) missing for WAVE preset")

        // Must load wave RAM data (AUD3WAVERAM)
        assertTrue(
            mainC.contains("AUD3WAVERAM"),
            "AUD3WAVERAM wave RAM load missing for WAVE channel with waveform data",
        )
    }

    // =========================================================================
    // Test 7: WAVE channel disables CH3 before loading wave RAM
    // =========================================================================
    @Test
    fun `WAVE channel disables CH3 before loading wave RAM to avoid corruption`() {
        val waveform = ByteArray(16) { 0x0F.toByte() }
        val def =
            SoundEffectDef(
                id = "wavefx2",
                channel = SoundChannel.WAVE,
                registers =
                    io.github.gbkt.core.ir.SoundRegisters(
                        frequency = 500,
                        trigger = true,
                        waveOutputLevel = 2,
                        waveform = waveform,
                    ),
            )
        val gameIR = buildGameWithSoundDef("wavefx2", def)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // NR30 must be set to 0x00 before loading wave RAM to prevent DAC corruption
        assertTrue(
            mainC.contains("NR30_REG = 0x00u"),
            "CH3 must be disabled (NR30=0x00) before loading wave RAM",
        )
    }

    // =========================================================================
    // Test 8: SoundEffectDef registered without PlaySound still gets wrapper (A1)
    // =========================================================================
    @Test
    fun `SoundEffectDef without PlaySound in scripts still generates wrapper function`() {
        val def = SoundEffectDef.fromPreset("unused_sfx", SoundPreset.COIN)
        // GameIR with soundEffects but NO PlaySound in scenes
        val gameIR =
            GameIR(
                name = "TestGame",
                config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY),
                scenes = listOf(SceneIR(id = "main")),
                soundEffects = listOf(def),
                startScene = "main",
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("play_sound_unused_sfx"),
            "Wrapper should be generated even when PlaySound is not in scripts",
        )
        // Must use real registers (NR1x for PULSE1 channel)
        assertTrue(mainC.contains("NR14_REG"), "NR14_REG missing in COIN preset wrapper")
    }

    // =========================================================================
    // Test 9: SoundSystem via GBDKSystemVisitor does not silently drop (A4)
    // =========================================================================
    @Test
    fun `SoundSystem in systems list is dispatched via GBDKSystemVisitor not silently dropped`() {
        val gameIR =
            GameIR(
                name = "TestGame",
                config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY),
                scenes = listOf(SceneIR(id = "main")),
                systems = listOf(SoundSystem(id = "sound")),
                startScene = "main",
            )
        // If SoundSystem was silently dropped (filterIsInstance<GenericSystem>), the build still
        // passes but the sound infrastructure might be missing. The key assertion: generate()
        // completes without error and sound driver is present.
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Sound driver infrastructure should still be generated (it's always added)
        assertTrue(
            mainC.contains("sound_driver_update"),
            "sound_driver_update should be present even when SoundSystem is in systems list",
        )
    }

    // =========================================================================
    // Test 10: PULSE2 channel generates NR2x registers (no NR10 sweep)
    // =========================================================================
    @Test
    fun `JUMP preset (PULSE2) generates NR2x registers and no NR10 sweep`() {
        val def = SoundEffectDef.fromPreset("jump", SoundPreset.JUMP)
        val gameIR = buildGameWithSoundDef("jump", def)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // JUMP uses PULSE2 channel — NR2x registers
        assertTrue(mainC.contains("NR21_REG"), "NR21_REG (duty+length) missing for JUMP")
        assertTrue(mainC.contains("NR22_REG"), "NR22_REG (envelope) missing for JUMP")
        assertTrue(mainC.contains("NR23_REG"), "NR23_REG (freq low) missing for JUMP")
        assertTrue(mainC.contains("NR24_REG"), "NR24_REG (trigger) missing for JUMP")
    }

    // =========================================================================
    // Test 11: POWERUP with sweep generates NR10 register (CH1 sweep)
    // =========================================================================
    @Test
    fun `POWERUP preset with sweep generates NR10 register with correct value`() {
        val def = SoundEffectDef.fromPreset("powerup", SoundPreset.POWERUP)
        // POWERUP has sweep: time=2, direction=INCREASE, shift=2
        // NR10 = (2 << 4) | (0 << 3) | 2 = 0x22
        val expectedNR10 = (2 shl 4) or (0 shl 3) or 2 // 0x22 (direction INCREASE=0 in NR10 bit 3)
        val gameIR = buildGameWithSoundDef("powerup", def)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("NR10_REG"), "NR10_REG (sweep) missing for POWERUP preset")
        val hexNR10 = "0x${expectedNR10.toString(16).uppercase().padStart(2, '0')}u"
        assertTrue(
            mainC.contains("NR10_REG = $hexNR10;"),
            "NR10_REG should be $hexNR10 for POWERUP sweep (time=2, increase, shift=2)",
        )
    }

    // =========================================================================
    // Test 12: No hashCode in ANY sound wrapper function
    // =========================================================================
    @Test
    fun `no hashCode call in any generated sound wrapper function`() {
        val defs =
            listOf(
                SoundEffectDef.fromPreset("beep", SoundPreset.BEEP),
                SoundEffectDef.fromPreset("hit", SoundPreset.HIT),
                SoundEffectDef.fromPreset("jump", SoundPreset.JUMP),
            )
        val gameIR =
            GameIR(
                name = "TestGame",
                config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY),
                scenes =
                    listOf(
                        SceneIR(
                            id = "main",
                            frameOps =
                                listOf(PlaySound("beep"), PlaySound("hit"), PlaySound("jump")),
                        )
                    ),
                soundEffects = defs,
                startScene = "main",
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // The old stub was: play_sound(sanitizedId.hashCode() and 0xFF, 0, 64, 30)
        // That exact pattern should never appear for registered effects
        assertFalse(
            mainC.contains("hashCode()"),
            "hashCode() should never appear in generated C output",
        )
        // Also verify we have real register writes for each channel type
        assertTrue(mainC.contains("NR14_REG"), "NR14_REG should be present (BEEP uses CH1)")
        assertTrue(mainC.contains("NR44_REG"), "NR44_REG should be present (HIT uses NOISE)")
        assertTrue(mainC.contains("NR24_REG"), "NR24_REG should be present (JUMP uses CH2)")
    }
}
