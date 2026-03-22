/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.dsl.ChannelGroupDef
import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.GenericSystem
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.MusicPause
import io.github.gbkt.core.ir.MusicPlay
import io.github.gbkt.core.ir.MusicResume
import io.github.gbkt.core.ir.MusicStop
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// MUSIC CODEGEN TESTS (A2+A5)
// Verifies that GBDKPipelineV2 generates correct hUGETracker integration:
// A2: MusicPlay → hUGE_init(&song_<id>)
//     MusicStop  → hUGEDriver_mute_channel(0..3)
//     MusicPause → hUGE_set_pause(1)
//     MusicResume → hUGE_set_pause(0)
//     hUGEDriver.h #include added when music ops are present
//     hUGE_dosound() added to main game loop when music ops are present
// A5: GenericSystem(type="audio_mixer") generates real NR50/NR51 register control:
//     set_group_volume() writes NR50_REG scaled by master volume
//     mute/unmute_group() manipulate NR51_REG channel bits
//     fade_group() generates per-frame interpolation loop
//     set_master_volume() recalculates NR50 from new master
//     audio_mixer_request_channel() implements priority-based preemption (Gap 5)
//     audio_mixer_duck/unduck() implement auto-ducking (Gap 6)
//     audio_mixer_save_state/load_state() enable persistable settings (Gap 4)
//     Default channel groups (music, sfx, ui) auto-populated when none defined (Gap 3)
// =============================================================================

/** Build a minimal GameIR with a scene containing the given frame ops. */
private fun buildGameWithFrameOps(vararg ops: io.github.gbkt.core.ir.ScriptOp): GameIR {
    return GameIR(
        name = "TestGame",
        config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
        scenes = listOf(SceneIR(id = "main", frameOps = ops.toList())),
        startScene = "main",
    )
}

/** Build a minimal GameIR with an audio_mixer GenericSystem and the given groups. */
private fun buildGameWithAudioMixer(
    groups: List<ChannelGroupDef>? = null,
    masterVolume: Int = 7,
    autoDucking: Boolean = false,
    autoDuckLevel: Int = 3,
): GameIR {
    val config =
        buildMap<String, Any> {
            put("type", "audio_mixer")
            if (groups != null) put("groups", groups)
            put("master_volume", masterVolume)
            put("auto_ducking", autoDucking)
            put("auto_duck_level", autoDuckLevel)
        }
    val audioMixer = GenericSystem(id = "mixer", config = config)
    return GameIR(
        name = "TestGame",
        config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
        scenes = listOf(SceneIR(id = "main")),
        systems = listOf(audioMixer),
        startScene = "main",
    )
}

class MusicCodegenTest {

    private val pipeline = GBDKPipelineV2()

    // =========================================================================
    // Test 1: MusicPlay generates hUGE_init call in scene code
    // =========================================================================
    @Test
    fun `MusicPlay ScriptOp generates hUGE_init call in scene code`() {
        val gameIR = buildGameWithFrameOps(MusicPlay(songId = "theme"))
        val output = pipeline.generate(gameIR)
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        assertTrue(
            bank1C.contains("hUGE_init(&song_theme)"),
            "MusicPlay should generate hUGE_init(&song_theme) in scene code",
        )
    }

    // =========================================================================
    // Test 2: MusicStop generates hUGEDriver_mute_channel calls for all 4 channels
    // =========================================================================
    @Test
    fun `MusicStop ScriptOp generates hUGEDriver_mute_channel calls for all channels`() {
        val gameIR = buildGameWithFrameOps(MusicStop())
        val output = pipeline.generate(gameIR)
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        // MusicStop mutes all 4 channels (0=CH1, 1=CH2, 2=CH3, 3=CH4)
        assertTrue(
            bank1C.contains("hUGEDriver_mute_channel(0)"),
            "MusicStop should mute CH1 (hUGEDriver_mute_channel(0))",
        )
        assertTrue(
            bank1C.contains("hUGEDriver_mute_channel(1)"),
            "MusicStop should mute CH2 (hUGEDriver_mute_channel(1))",
        )
        assertTrue(
            bank1C.contains("hUGEDriver_mute_channel(2)"),
            "MusicStop should mute CH3 (hUGEDriver_mute_channel(2))",
        )
        assertTrue(
            bank1C.contains("hUGEDriver_mute_channel(3)"),
            "MusicStop should mute CH4 (hUGEDriver_mute_channel(3))",
        )
    }

    // =========================================================================
    // Test 3: MusicPause generates hUGE_set_pause call with pause=1
    // =========================================================================
    @Test
    fun `MusicPause ScriptOp generates hUGE_set_pause call with pause flag set`() {
        val gameIR = buildGameWithFrameOps(MusicPause())
        val output = pipeline.generate(gameIR)
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        // CLiteral(1) is emitted as "1u" by CEmitter (unsigned suffix for non-negative values)
        assertTrue(
            bank1C.contains("hUGE_set_pause(1u)"),
            "MusicPause should generate hUGE_set_pause(1u) (CLiteral uses 'u' suffix)",
        )
    }

    // =========================================================================
    // Test 4: MusicResume generates hUGE_set_pause call with pause=0
    // =========================================================================
    @Test
    fun `MusicResume ScriptOp generates hUGE_set_pause call with pause cleared`() {
        val gameIR = buildGameWithFrameOps(MusicResume())
        val output = pipeline.generate(gameIR)
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        // CLiteral(0) is emitted as "0u" by CEmitter (unsigned suffix for non-negative values)
        assertTrue(
            bank1C.contains("hUGE_set_pause(0u)"),
            "MusicResume should generate hUGE_set_pause(0u) (CLiteral uses 'u' suffix)",
        )
    }

    // =========================================================================
    // Test 5: hUGEDriver.h include added to main.c when music ops are present
    // =========================================================================
    @Test
    fun `hUGEDriver_h include added to main_c when scene has MusicPlay op`() {
        val gameIR = buildGameWithFrameOps(MusicPlay(songId = "battle"))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("<hUGEDriver.h>"),
            "main.c should include <hUGEDriver.h> when MusicPlay op is present",
        )
    }

    // =========================================================================
    // Test 6: hUGE_dosound() added to main game loop when music ops are present
    // =========================================================================
    @Test
    fun `hUGE_dosound call added to main game loop when music ops are present`() {
        val gameIR = buildGameWithFrameOps(MusicPlay(songId = "title"))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("hUGE_dosound()"),
            "hUGE_dosound() should be called in main game loop when music ops are present",
        )
    }

    // =========================================================================
    // Test 7: No hUGEDriver.h include in main.c when no music ops are present
    // =========================================================================
    @Test
    fun `no hUGEDriver_h include in main_c when game has no music ops`() {
        val gameIR = buildGameWithFrameOps(Assign("x", Literal(1), AssignOp.SET)) // no music ops
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertFalse(
            mainC.contains("<hUGEDriver.h>"),
            "main.c should NOT include <hUGEDriver.h> when no music ops are used",
        )
    }

    // =========================================================================
    // Test 8: No hUGE_dosound call when no music ops are present
    // =========================================================================
    @Test
    fun `no hUGE_dosound call in main game loop when no music ops`() {
        val gameIR = buildGameWithFrameOps(Assign("x", Literal(1), AssignOp.SET)) // no music ops
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertFalse(
            mainC.contains("hUGE_dosound()"),
            "hUGE_dosound() should NOT appear in main loop when no music ops are present",
        )
    }

    // =========================================================================
    // Test 9: MusicStop also triggers hUGEDriver.h include (any music op triggers it)
    // =========================================================================
    @Test
    fun `hUGEDriver_h include added when MusicStop is used even without MusicPlay`() {
        val gameIR = buildGameWithFrameOps(MusicStop())
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("<hUGEDriver.h>"),
            "main.c should include <hUGEDriver.h> when MusicStop op is present",
        )
    }

    // =========================================================================
    // Test 10 (A5): AudioMixer generates set_group_volume with NR50 write
    // =========================================================================
    @Test
    fun `audio_mixer generates set_group_volume with NR50_REG write`() {
        val groups =
            listOf(
                ChannelGroupDef("music", setOf(1, 2), 7, 0),
                ChannelGroupDef("sfx", setOf(3, 4), 7, 1),
            )
        val gameIR = buildGameWithAudioMixer(groups = groups)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("void set_group_volume("),
            "set_group_volume function missing for audio_mixer system",
        )
        assertTrue(
            mainC.contains("_mixer_group_vol["),
            "_mixer_group_vol array access missing in set_group_volume",
        )
        assertTrue(
            mainC.contains("NR50_REG"),
            "NR50_REG register write missing in set_group_volume",
        )
        assertTrue(
            mainC.contains("_mixer_master_vol"),
            "_mixer_master_vol reference missing in set_group_volume",
        )
        // Assert no stub comment remains
        assertFalse(
            mainC.contains("AudioMixer stub"),
            "Stub comment must not appear in audio_mixer output",
        )
    }

    // =========================================================================
    // Test 11 (A5): AudioMixer generates mute/unmute with NR51 manipulation
    // =========================================================================
    @Test
    fun `audio_mixer generates mute_group and unmute_group with NR51_REG manipulation`() {
        val groups =
            listOf(
                ChannelGroupDef("music", setOf(1, 2), 7, 0),
                ChannelGroupDef("sfx", setOf(3, 4), 7, 1),
            )
        val gameIR = buildGameWithAudioMixer(groups = groups)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("void mute_group("),
            "mute_group function missing for audio_mixer system",
        )
        assertTrue(
            mainC.contains("void unmute_group("),
            "unmute_group function missing for audio_mixer system",
        )
        assertTrue(
            mainC.contains("NR51_REG"),
            "NR51_REG register manipulation missing in mute/unmute functions",
        )
        assertTrue(
            mainC.contains("_mixer_group_muted["),
            "_mixer_group_muted array access missing in mute/unmute functions",
        )
    }

    // =========================================================================
    // Test 12 (A5): AudioMixer generates fade_group with frame interpolation
    // =========================================================================
    @Test
    fun `audio_mixer generates fade_group with per-frame interpolation loop`() {
        val gameIR = buildGameWithAudioMixer()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("void fade_group("),
            "fade_group function missing for audio_mixer system",
        )
        // Verify the function has 3 parameters: group, target_vol, frames
        assertTrue(
            mainC.contains("fade_group(UINT8 group, UINT8 target_vol, UINT8 frames)"),
            "fade_group should have 3 parameters: group, target_vol, frames",
        )
        // Verify set_group_volume is called inside the loop
        val fadeGroupStart = mainC.indexOf("void fade_group(")
        val fadeGroupEnd =
            mainC.indexOf("\nvoid ", fadeGroupStart + 1).let { if (it == -1) mainC.length else it }
        val fadeGroupBody = mainC.substring(fadeGroupStart, fadeGroupEnd)
        assertTrue(
            fadeGroupBody.contains("set_group_volume"),
            "fade_group body should call set_group_volume for interpolation",
        )
    }

    // =========================================================================
    // Test 13 (A5): AudioMixer generates master volume control
    // =========================================================================
    @Test
    fun `audio_mixer generates set_master_volume that updates _mixer_master_vol`() {
        val gameIR = buildGameWithAudioMixer()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("void set_master_volume("),
            "set_master_volume function missing for audio_mixer system",
        )
        // Check it updates _mixer_master_vol
        val fnStart = mainC.indexOf("void set_master_volume(")
        val fnEnd = mainC.indexOf("\nvoid ", fnStart + 1).let { if (it == -1) mainC.length else it }
        val fnBody = mainC.substring(fnStart, fnEnd)
        assertTrue(
            fnBody.contains("_mixer_master_vol"),
            "set_master_volume body should update _mixer_master_vol",
        )
    }

    // =========================================================================
    // Test 14 (A5): AudioMixer globals generated for channel groups
    // =========================================================================
    @Test
    fun `audio_mixer globals generated including vol arrays and priority tracking`() {
        val groups =
            listOf(
                ChannelGroupDef("music", setOf(1, 2), 7, 0),
                ChannelGroupDef("sfx", setOf(3, 4), 6, 1),
            )
        val gameIR = buildGameWithAudioMixer(groups = groups)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("_mixer_group_vol"), "_mixer_group_vol array declaration missing")
        assertTrue(
            mainC.contains("_mixer_master_vol"),
            "_mixer_master_vol global declaration missing",
        )
        assertTrue(
            mainC.contains("_mixer_channel_mask_music"),
            "_mixer_channel_mask_music constant missing",
        )
        assertTrue(
            mainC.contains("_mixer_channel_mask_sfx"),
            "_mixer_channel_mask_sfx constant missing",
        )
        assertTrue(
            mainC.contains("_mixer_group_muted"),
            "_mixer_group_muted array declaration missing",
        )
        assertTrue(
            mainC.contains("_mixer_priority"),
            "_mixer_priority[4] array declaration missing",
        )
        assertTrue(
            mainC.contains("_mixer_preduck_vol"),
            "_mixer_preduck_vol global declaration missing",
        )
    }

    // =========================================================================
    // Test 15 (A5, Gap 3): Default channel groups auto-populated when none defined
    // =========================================================================
    @Test
    fun `audio_mixer default channel groups music sfx ui generated when no groups defined`() {
        // No explicit groups — uses defaults
        val audioMixer = GenericSystem(id = "mixer", config = mapOf("type" to "audio_mixer"))
        val gameIR =
            GameIR(
                name = "TestGame",
                config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
                scenes = listOf(SceneIR(id = "main")),
                systems = listOf(audioMixer),
                startScene = "main",
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Default groups create MIXER_GROUP_MUSIC, MIXER_GROUP_SFX, MIXER_GROUP_UI defines
        assertTrue(mainC.contains("MIXER_GROUP_MUSIC"), "Default MIXER_GROUP_MUSIC define missing")
        assertTrue(mainC.contains("MIXER_GROUP_SFX"), "Default MIXER_GROUP_SFX define missing")
        assertTrue(mainC.contains("MIXER_GROUP_UI"), "Default MIXER_GROUP_UI define missing")
        // _mixer_group_vol with 3 default groups
        assertTrue(
            mainC.contains("_mixer_group_vol"),
            "_mixer_group_vol missing for default groups",
        )
        // channel masks for default groups
        assertTrue(
            mainC.contains("_mixer_channel_mask_music"),
            "_mixer_channel_mask_music missing for default groups",
        )
        assertTrue(
            mainC.contains("_mixer_channel_mask_sfx"),
            "_mixer_channel_mask_sfx missing for default groups",
        )
        assertTrue(
            mainC.contains("_mixer_channel_mask_ui"),
            "_mixer_channel_mask_ui missing for default groups",
        )
    }

    // =========================================================================
    // Test 16 (A5, Gap 3): Custom groups override defaults
    // =========================================================================
    @Test
    fun `audio_mixer custom groups override default groups`() {
        val customGroups = listOf(ChannelGroupDef("custom", setOf(1), 5, 0))
        val gameIR = buildGameWithAudioMixer(groups = customGroups)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Custom group present
        assertTrue(mainC.contains("MIXER_GROUP_CUSTOM"), "Custom MIXER_GROUP_CUSTOM define missing")
        assertTrue(
            mainC.contains("_mixer_channel_mask_custom"),
            "_mixer_channel_mask_custom constant missing",
        )
        // Default groups must NOT be present
        assertFalse(
            mainC.contains("MIXER_GROUP_SFX"),
            "Default MIXER_GROUP_SFX should not appear when custom groups are used",
        )
        assertFalse(
            mainC.contains("MIXER_GROUP_UI"),
            "Default MIXER_GROUP_UI should not appear when custom groups are used",
        )
    }

    // =========================================================================
    // Test 17 (A5, Gap 4): AudioMixer save/load state functions
    // =========================================================================
    @Test
    fun `audio_mixer generates save_state and load_state functions with buffer IO`() {
        val groups =
            listOf(
                ChannelGroupDef("music", setOf(1, 2), 7, 0),
                ChannelGroupDef("sfx", setOf(3, 4), 7, 1),
            )
        val gameIR = buildGameWithAudioMixer(groups = groups)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("void audio_mixer_save_state("),
            "audio_mixer_save_state function missing",
        )
        assertTrue(
            mainC.contains("void audio_mixer_load_state("),
            "audio_mixer_load_state function missing",
        )
        // Save writes _mixer_master_vol to ptr[0]
        val saveStart = mainC.indexOf("void audio_mixer_save_state(")
        val saveEnd =
            mainC.indexOf("\nvoid ", saveStart + 1).let { if (it == -1) mainC.length else it }
        val saveBody = mainC.substring(saveStart, saveEnd)
        assertTrue(
            saveBody.contains("ptr"),
            "audio_mixer_save_state body should write to ptr buffer",
        )
        assertTrue(
            saveBody.contains("_mixer_master_vol"),
            "audio_mixer_save_state should save _mixer_master_vol",
        )
        // Load calls set_group_volume to restore hardware state
        val loadStart = mainC.indexOf("void audio_mixer_load_state(")
        val loadEnd =
            mainC.indexOf("\nvoid ", loadStart + 1).let { if (it == -1) mainC.length else it }
        val loadBody = mainC.substring(loadStart, loadEnd)
        assertTrue(
            loadBody.contains("set_group_volume"),
            "audio_mixer_load_state should call set_group_volume to restore hardware state",
        )
    }

    // =========================================================================
    // Test 18 (A5, Gap 5): Priority-based channel request
    // =========================================================================
    @Test
    fun `audio_mixer generates request_channel with priority comparison and 0xFF denial`() {
        val gameIR = buildGameWithAudioMixer()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("audio_mixer_request_channel("),
            "audio_mixer_request_channel function missing",
        )
        // Priority comparison uses _mixer_priority array
        val fnStart = mainC.indexOf("audio_mixer_request_channel(UINT8 group, UINT8 priority)")
        val fnEnd = mainC.indexOf("\nvoid ", fnStart + 1).let { if (it == -1) mainC.length else it }
        val fnBody = mainC.substring(fnStart, fnEnd)
        assertTrue(
            fnBody.contains("_mixer_priority["),
            "_mixer_priority array access missing in audio_mixer_request_channel",
        )
        assertTrue(
            fnBody.contains("0xFF"),
            "0xFF denial return missing in audio_mixer_request_channel",
        )
    }

    // =========================================================================
    // Test 19 (A5, Gap 6): Auto-ducking functions
    // =========================================================================
    @Test
    fun `audio_mixer generates duck and unduck functions with preduck volume tracking`() {
        val groups =
            listOf(
                ChannelGroupDef("music", setOf(1, 2), 7, 0),
                ChannelGroupDef("sfx", setOf(3, 4), 7, 1),
            )
        val gameIR = buildGameWithAudioMixer(groups = groups, autoDucking = true, autoDuckLevel = 3)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("void audio_mixer_duck("), "audio_mixer_duck function missing")
        assertTrue(
            mainC.contains("void audio_mixer_unduck("),
            "audio_mixer_unduck function missing",
        )
        // Duck saves current music volume to _mixer_preduck_vol
        val duckStart = mainC.indexOf("void audio_mixer_duck(")
        val duckEnd =
            mainC.indexOf("\nvoid ", duckStart + 1).let { if (it == -1) mainC.length else it }
        val duckBody = mainC.substring(duckStart, duckEnd)
        assertTrue(
            duckBody.contains("_mixer_preduck_vol"),
            "audio_mixer_duck should save to _mixer_preduck_vol",
        )
        assertTrue(
            duckBody.contains("set_group_volume"),
            "audio_mixer_duck should call set_group_volume to apply duck level",
        )
        // Unduck restores from _mixer_preduck_vol
        val unduckStart = mainC.indexOf("void audio_mixer_unduck(")
        val unduckEnd =
            mainC.indexOf("\nvoid ", unduckStart + 1).let { if (it == -1) mainC.length else it }
        val unduckBody = mainC.substring(unduckStart, unduckEnd)
        assertTrue(
            unduckBody.contains("_mixer_preduck_vol"),
            "audio_mixer_unduck should restore from _mixer_preduck_vol",
        )
    }
}
