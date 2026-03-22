/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipelineV2
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.ProceduralWave
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.ScriptedWave
import io.github.gbkt.core.ir.WaveDef
import io.github.gbkt.core.ir.WaveSurvivalConfig
import io.github.gbkt.core.ir.WaveTrigger
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// WAVE SURVIVAL CODEGEN TESTS (Plan 06.5-09 Task 2 success criterion)
// 6 tests covering CombatVisitor WAVE_SURVIVAL branch:
//   - wave survival generates wave counter and timer globals
//   - scripted wave populates combatants from monster list
//   - procedural wave uses PRNG for monster selection
//   - between-wave heal generates HP restoration code
//   - maxWaves=0 generates endless loop (no victory check)
//   - PLAYER_READY trigger generates input check
// =============================================================================

/** Build a minimal GameIR with a WAVE_SURVIVAL CombatEngineSystem. */
private fun buildWaveGameIR(system: CombatEngineSystem, startScene: String = "gameplay"): GameIR =
    GameIR(
        name = "TestWaveGame",
        config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
        scenes = listOf(SceneIR(id = startScene)),
        systems = listOf(system),
        startScene = startScene,
    )

/** Default wave system ID used across all tests. */
private const val WAVE_ID = "waves"

/** A minimal WAVE_SURVIVAL system with no optional features. */
private fun minimalWaveSystem(): CombatEngineSystem =
    CombatEngineSystem(id = WAVE_ID, combatType = CombatType.WAVE_SURVIVAL)

class WaveSurvivalCodegenTest {

    private val pipeline = GBDKPipelineV2()

    // =========================================================================
    // Test 1: wave survival generates wave counter and timer globals
    // =========================================================================

    @Test
    fun `wave survival generates wave counter and timer globals`() {
        val gameIR = buildWaveGameIR(minimalWaveSystem())
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_wave_${WAVE_ID}_current"),
            "Expected '_wave_${WAVE_ID}_current' global in main.c",
        )
        assertTrue(
            mainC.contains("_wave_${WAVE_ID}_timer"),
            "Expected '_wave_${WAVE_ID}_timer' global in main.c",
        )
    }

    // =========================================================================
    // Test 2: scripted wave populates combatants from monster list
    // =========================================================================

    @Test
    fun `scripted wave populates combatants from monster list`() {
        val system =
            CombatEngineSystem(
                id = WAVE_ID,
                combatType = CombatType.WAVE_SURVIVAL,
                waveSurvivalConfig =
                    WaveSurvivalConfig(
                        waves =
                            listOf(
                                WaveDef(
                                    waveNumber = 1,
                                    content = ScriptedWave(monsters = listOf("goblin", "orc")),
                                )
                            )
                    ),
            )
        val gameIR = buildWaveGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // start_wave_<id> function must be emitted
        assertTrue(
            mainC.contains("void start_wave_$WAVE_ID(UINT8 wave_num)"),
            "Expected 'void start_wave_$WAVE_ID(UINT8 wave_num)' in main.c",
        )
        // Scripted wave: spawn_monster calls with monster IDs
        assertTrue(
            mainC.contains("spawn_monster"),
            "Expected 'spawn_monster' calls in start_wave for scripted wave",
        )
        assertTrue(
            mainC.contains("_monster_id_goblin"),
            "Expected '_monster_id_goblin' ID reference in scripted wave",
        )
        assertTrue(
            mainC.contains("_monster_id_orc"),
            "Expected '_monster_id_orc' ID reference in scripted wave",
        )
    }

    // =========================================================================
    // Test 3: procedural wave uses PRNG for monster selection
    // =========================================================================

    @Test
    fun `procedural wave uses PRNG for monster selection`() {
        val system =
            CombatEngineSystem(
                id = WAVE_ID,
                combatType = CombatType.WAVE_SURVIVAL,
                waveSurvivalConfig =
                    WaveSurvivalConfig(
                        waves =
                            listOf(
                                WaveDef(
                                    waveNumber = 3,
                                    content =
                                        ProceduralWave(
                                            monsterPool = listOf("goblin", "orc", "troll"),
                                            minCount = 2,
                                            maxCount = 4,
                                        ),
                                )
                            )
                    ),
            )
        val gameIR = buildWaveGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Procedural wave must use rand() for PRNG selection
        assertTrue(
            mainC.contains("rand()"),
            "Expected 'rand()' PRNG call in procedural wave start_wave function",
        )
        // Pool dispatch uses spawn_monster_from_pool
        assertTrue(
            mainC.contains("spawn_monster_from_pool"),
            "Expected 'spawn_monster_from_pool' call in procedural wave",
        )
    }

    // =========================================================================
    // Test 4: between-wave heal generates HP restoration code
    // =========================================================================

    @Test
    fun `between-wave heal generates HP restoration code`() {
        val system =
            CombatEngineSystem(
                id = WAVE_ID,
                combatType = CombatType.WAVE_SURVIVAL,
                waveSurvivalConfig = WaveSurvivalConfig(healBetweenWaves = 25),
            )
        val gameIR = buildWaveGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // between_wave_<id> function must be emitted
        assertTrue(
            mainC.contains("void between_wave_$WAVE_ID(void)"),
            "Expected 'void between_wave_$WAVE_ID(void)' in main.c",
        )
        // heal_party call with configured amount
        assertTrue(
            mainC.contains("heal_party(25"),
            "Expected 'heal_party(25' call in between_wave with healBetweenWaves=25",
        )
    }

    // =========================================================================
    // Test 5: maxWaves=0 generates endless loop (no victory check)
    // =========================================================================

    @Test
    fun `maxWaves=0 generates endless loop without victory check`() {
        val system =
            CombatEngineSystem(
                id = WAVE_ID,
                combatType = CombatType.WAVE_SURVIVAL,
                waveSurvivalConfig =
                    WaveSurvivalConfig(
                        maxWaves = 0 // endless mode
                    ),
            )
        val gameIR = buildWaveGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // advance_wave_<id> must be emitted
        assertTrue(
            mainC.contains("void advance_wave_$WAVE_ID(void)"),
            "Expected 'void advance_wave_$WAVE_ID(void)' in main.c",
        )
        // Endless mode comment
        assertTrue(
            mainC.contains("Endless mode"),
            "Expected 'Endless mode' comment in advance_wave for maxWaves=0",
        )
        // No request to VICTORY (state 4) in advance_wave for endless mode
        assertFalse(
            mainC.contains("combat_request_state_${WAVE_ID}(4"),
            "Endless mode (maxWaves=0) should NOT generate a victory request in advance_wave",
        )
    }

    // =========================================================================
    // Test 6: PLAYER_READY trigger generates input check instead of timer
    // =========================================================================

    @Test
    fun `PLAYER_READY trigger generates J_A input check in BETWEEN_WAVE state`() {
        val system =
            CombatEngineSystem(
                id = WAVE_ID,
                combatType = CombatType.WAVE_SURVIVAL,
                waveSurvivalConfig = WaveSurvivalConfig(nextWaveTrigger = WaveTrigger.PLAYER_READY),
            )
        val gameIR = buildWaveGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // PLAYER_READY: check button_pressed(J_A)
        assertTrue(
            mainC.contains("button_pressed(J_A)"),
            "Expected 'button_pressed(J_A)' in BETWEEN_WAVE case for PLAYER_READY trigger",
        )
        // TIMER: no countdown decrement in BETWEEN_WAVE
        assertFalse(
            mainC.contains("_wave_${WAVE_ID}_timer -= 1"),
            "PLAYER_READY trigger should NOT generate timer countdown in BETWEEN_WAVE",
        )
    }
}
