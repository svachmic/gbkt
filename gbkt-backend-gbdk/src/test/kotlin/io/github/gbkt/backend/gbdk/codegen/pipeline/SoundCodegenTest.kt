/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.Assign
import io.github.gbkt.core.ir.AssignOp
import io.github.gbkt.core.ir.BinaryExpr
import io.github.gbkt.core.ir.BinaryOp
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.IfOp
import io.github.gbkt.core.ir.Literal
import io.github.gbkt.core.ir.PlaySound
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.VarRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// SOUND CODEGEN TESTS
// Verifies that GBDKPipelineV2 generates the correct sound driver infrastructure:
// - Sound driver global variables (_sound_channels, _sound_priority, _sound_duration)
// - sound_driver_update() called in main loop
// - play_sound() core driver function generated
// - play_sound_<id>() wrapper functions for each unique PlaySound ID
// - Sound functions in HOME bank
// - Sound hardware init (NR52/NR50/NR51) in main()
// - No duplicate sound wrapper functions
// - Priority preemption logic in play_sound()
// =============================================================================

/** Build a minimal GameIR with scenes containing PlaySound ops. */
private fun buildGameWithSounds(vararg soundIds: String): GameIR {
    val frameOps = soundIds.map { PlaySound(it) }
    return GameIR(
        name = "TestGame",
        config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
        scenes = listOf(SceneIR(id = "main", frameOps = frameOps)),
        startScene = "main",
    )
}

class SoundCodegenTest {

    private val pipeline = GBDKPipelineV2()

    // =========================================================================
    // Test 1: Sound driver globals generated for games with PlaySound
    // =========================================================================
    @Test
    fun `sound driver globals generated when game has PlaySound ops`() {
        val gameIR = buildGameWithSounds("hit")
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("_sound_channels"), "_sound_channels array missing")
        assertTrue(mainC.contains("_sound_priority"), "_sound_priority array missing")
        assertTrue(mainC.contains("_sound_duration"), "_sound_duration array missing")
    }

    // =========================================================================
    // Test 2: sound_driver_update() called in main loop
    // =========================================================================
    @Test
    fun `sound_driver_update called in main game loop`() {
        val gameIR = buildGameWithSounds("sfx")
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("sound_driver_update()"),
            "sound_driver_update() not in main loop",
        )
    }

    // =========================================================================
    // Test 3: play_sound() core driver function generated with channel/priority/duration params
    // =========================================================================
    @Test
    fun `play_sound driver function generated with correct signature`() {
        val gameIR = buildGameWithSounds("hit")
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Check function signature is present
        assertTrue(mainC.contains("play_sound("), "play_sound() function not generated")
        // Check priority preemption logic exists
        assertTrue(mainC.contains("_sound_priority"), "Priority check not in play_sound()")
        assertTrue(mainC.contains("_sound_duration"), "Duration tracking not in play_sound()")
    }

    // =========================================================================
    // Test 4: Wrapper functions generated for each unique sound ID
    // =========================================================================
    @Test
    fun `play_sound wrapper functions generated for each unique sound ID`() {
        val gameIR = buildGameWithSounds("hit", "jump", "coin")
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("play_sound_hit"), "play_sound_hit() wrapper missing")
        assertTrue(mainC.contains("play_sound_jump"), "play_sound_jump() wrapper missing")
        assertTrue(mainC.contains("play_sound_coin"), "play_sound_coin() wrapper missing")
    }

    // =========================================================================
    // Test 5: Sound function DEFINITIONS are in HOME bank (main.c), not bank1.c
    // =========================================================================
    @Test
    fun `sound function definitions are in HOME bank main c not bank1 c`() {
        val gameIR = buildGameWithSounds("hit")
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Sound function definitions must be in main.c (HOME bank)
        assertFalse(mainC.isEmpty(), "main.c should not be empty")
        // A function definition has the return type + name pattern
        assertTrue(
            mainC.contains("void play_sound_hit"),
            "play_sound_hit definition should be in main.c (HOME bank)",
        )

        // bank1.c may CALL play_sound_hit() (it's the scene code calling it),
        // but should NOT contain the function DEFINITION
        val bank1C = output.files["bank1.c"] ?: ""
        assertFalse(
            bank1C.contains("void play_sound_hit"),
            "play_sound_hit DEFINITION should not be in bank1.c (only calls allowed)",
        )
    }

    // =========================================================================
    // Test 6: Sound hardware init registers in main()
    // =========================================================================
    @Test
    fun `sound hardware init NR52 NR50 NR51 registers in main`() {
        val gameIR = buildGameWithSounds("hit")
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("NR52_REG"), "NR52_REG init missing in main()")
        assertTrue(mainC.contains("NR50_REG"), "NR50_REG init missing in main()")
        assertTrue(mainC.contains("NR51_REG"), "NR51_REG init missing in main()")
    }

    // =========================================================================
    // Test 7: No duplicate sound wrapper DEFINITIONS when same ID in multiple scenes
    // =========================================================================
    @Test
    fun `no duplicate sound wrapper functions when same sound ID in multiple scenes`() {
        val gameIR =
            GameIR(
                name = "TestGame",
                config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
                scenes =
                    listOf(
                        SceneIR(id = "scene1", frameOps = listOf(PlaySound("hit"))),
                        SceneIR(id = "scene2", frameOps = listOf(PlaySound("hit"))),
                    ),
                startScene = "scene1",
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // The function DEFINITION "void play_sound_hit" should appear exactly once in main.c
        // (scenes calling "play_sound_hit()" are in bank1.c, not main.c)
        val fnDefPattern = Regex("void play_sound_hit")
        val defCount = fnDefPattern.findAll(mainC).count()
        assertTrue(defCount >= 1, "play_sound_hit definition should exist in main.c")
        assertEquals(
            1,
            defCount,
            "play_sound_hit should only be defined once in main.c (no duplicates)",
        )
    }

    // =========================================================================
    // Test 8: Priority preemption logic in play_sound() body
    // =========================================================================
    @Test
    fun `play_sound function contains priority preemption check`() {
        val gameIR = buildGameWithSounds("hit")
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Verify priority comparison logic exists in play_sound()
        assertTrue(
            mainC.contains("_sound_priority") && mainC.contains("priority"),
            "Priority preemption check missing from play_sound()",
        )
    }

    // =========================================================================
    // Test 9: Sound driver globals also appear in game.h as externs
    // =========================================================================
    @Test
    fun `sound driver globals appear in game h as extern declarations`() {
        val gameIR = buildGameWithSounds("hit")
        val output = pipeline.generate(gameIR)
        val gameH = output.files["game.h"] ?: error("game.h not generated")

        assertTrue(gameH.contains("extern"), "game.h should have extern declarations")
        assertTrue(gameH.contains("_sound_channels"), "extern _sound_channels missing from game.h")
        assertTrue(gameH.contains("_sound_duration"), "extern _sound_duration missing from game.h")
    }

    // =========================================================================
    // Test 10: PlaySound in nested IfOp is collected for wrapper generation
    // =========================================================================
    @Test
    fun `PlaySound inside nested IfOp is collected for wrapper function generation`() {
        val innerOps = listOf(PlaySound("nested_sfx"))
        val condition = BinaryExpr(VarRef("x"), BinaryOp.GT, Literal(0))
        val gameIR =
            GameIR(
                name = "TestGame",
                config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
                scenes = listOf(SceneIR(id = "main", frameOps = listOf(IfOp(condition, innerOps)))),
                startScene = "main",
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("play_sound_nested_sfx"),
            "Nested PlaySound wrapper not generated",
        )
    }

    // =========================================================================
    // Test 11: Game without PlaySound still generates sound driver infrastructure
    // =========================================================================
    @Test
    fun `game without PlaySound still generates sound driver update and globals`() {
        val gameIR =
            GameIR(
                name = "Silent",
                config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
                scenes =
                    listOf(
                        SceneIR(
                            id = "main",
                            frameOps = listOf(Assign("x", Literal(1), AssignOp.SET)),
                        )
                    ),
                startScene = "main",
            )
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Sound driver infrastructure always generated (game may add sounds later)
        assertTrue(
            mainC.contains("sound_driver_update"),
            "sound_driver_update missing even for silent game",
        )
        assertTrue(
            mainC.contains("_sound_channels"),
            "Sound driver globals missing for silent game",
        )
    }
}
