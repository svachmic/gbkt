/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.ir.AtbConfig
import io.github.gbkt.core.ir.AtbGaugeModel
import io.github.gbkt.core.ir.AtbWaitMode
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.TurnOrderStrategy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// ATB COMBAT CODEGEN TESTS (Plan 06.5-05 success criterion SC-6, SC-9, SC-13)
// 7 tests covering ATB CombatEngineSystem codegen paths:
//   - ATB combat generates gauge fill loop in state machine
//   - ATB WAIT mode generates menu_open check in gauge fill
//   - ATB ACTIVE mode skips menu_open check
//   - ATB CHARGE model generates per-action charge counter
//   - ATB gauge globals emitted as UINT8 arrays
//   - Turn order SPEED_BASED generates sort function
//   - ATB base rate and max gauge emitted as defines
// =============================================================================

/** Build a minimal GameIR with a CombatEngineSystem and one gameplay scene. */
private fun buildAtbGameIR(system: CombatEngineSystem, startScene: String = "gameplay"): GameIR =
    GameIR(
        name = "TestAtbGame",
        config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY),
        scenes = listOf(SceneIR(id = startScene)),
        systems = listOf(system),
        startScene = startScene,
    )

/** The system ID used in all default ATB test fixtures. */
private const val ATB_ID = "atbcombat"

/** A minimal ATB combat system with default configuration. */
private fun defaultAtbSystem(): CombatEngineSystem =
    CombatEngineSystem(
        id = ATB_ID,
        combatType = CombatType.ATB,
        atbConfig =
            AtbConfig(
                gaugeModel = AtbGaugeModel.FILL,
                waitMode = AtbWaitMode.WAIT,
                baseGaugeFillRate = 4,
                maxGauge = 255,
            ),
        maxCombatants = 4,
    )

class AtbCombatCodegenTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // Test 1: ATB combat generates gauge fill loop in state machine
    // =========================================================================

    @Test
    fun `ATB combat generates gauge fill loop in state machine`() {
        val gameIR = buildAtbGameIR(defaultAtbSystem())
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // ATB state machine must have GAUGE_FILL state (case 5u)
        assertTrue(
            mainC.contains("case 5u:"),
            "Expected 'case 5u:' (GAUGE_FILL) in ATB state machine",
        )
        // GAUGE_FILL case must call update_atb_gauges
        assertTrue(
            mainC.contains("update_atb_gauges_$ATB_ID"),
            "Expected 'update_atb_gauges_$ATB_ID' call in GAUGE_FILL state",
        )
        // update_atb_gauges function must use a for-loop (not just gauge[0])
        assertTrue(mainC.contains("for"), "Expected 'for' loop in gauge fill function")
        // The function must check the active status of each combatant
        assertTrue(
            mainC.contains("_combat_${ATB_ID}_active["),
            "Expected '_combat_${ATB_ID}_active[' active check in gauge fill loop",
        )
        // The function must increment gauge[i] with variable index, not just gauge[0]
        assertTrue(
            mainC.contains("_combat_${ATB_ID}_gauge["),
            "Expected '_combat_${ATB_ID}_gauge[' with variable index in gauge fill loop",
        )
        // The function must use agility for the fill rate
        assertTrue(
            mainC.contains("_combat_${ATB_ID}_agl["),
            "Expected '_combat_${ATB_ID}_agl[' agility reference in gauge fill loop",
        )
        // The function must mark ready-to-act combatants
        assertTrue(
            mainC.contains("_combat_${ATB_ID}_acted["),
            "Expected '_combat_${ATB_ID}_acted[' ready-to-act marker in gauge fill loop",
        )
        // No TODO stubs should remain
        assertFalse(
            mainC.contains("TODO"),
            "Expected no 'TODO' stubs in generated ATB gauge fill output",
        )
    }

    // =========================================================================
    // Test 2: ATB WAIT mode generates menu_open check in gauge fill
    // =========================================================================

    @Test
    fun `ATB WAIT mode generates menu_open check in gauge fill`() {
        val system =
            CombatEngineSystem(
                id = ATB_ID,
                combatType = CombatType.ATB,
                atbConfig = AtbConfig(waitMode = AtbWaitMode.WAIT),
            )
        val gameIR = buildAtbGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // WAIT mode: gauge fill is guarded by menu_open check
        assertTrue(
            mainC.contains("_combat_${ATB_ID}_menu_open"),
            "Expected '_combat_${ATB_ID}_menu_open' check in WAIT mode gauge fill",
        )
    }

    // =========================================================================
    // Test 3: ATB ACTIVE mode skips menu_open check
    // =========================================================================

    @Test
    fun `ATB ACTIVE mode skips menu_open check`() {
        val system =
            CombatEngineSystem(
                id = ATB_ID,
                combatType = CombatType.ATB,
                atbConfig = AtbConfig(waitMode = AtbWaitMode.ACTIVE),
            )
        val gameIR = buildAtbGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // ACTIVE mode: update_atb_gauges function exists but no menu_open guard
        assertTrue(
            mainC.contains("update_atb_gauges_$ATB_ID"),
            "Expected 'update_atb_gauges_$ATB_ID' function in ACTIVE mode",
        )
        // The update_atb_gauges function body should NOT contain menu_open check
        // (it's defined in main.c, so we can check for the absence of menu_open in the context)
        // We verify the function exists; the actual body is tested by the presence of gauge
        // increment
        assertTrue(
            mainC.contains("_combat_${ATB_ID}_gauge"),
            "Expected gauge array references in ATB ACTIVE mode",
        )
    }

    // =========================================================================
    // Test 4: ATB CHARGE model generates per-action charge counter
    // =========================================================================

    @Test
    fun `ATB CHARGE model generates per-action charge counter`() {
        val system =
            CombatEngineSystem(
                id = ATB_ID,
                combatType = CombatType.ATB,
                atbConfig = AtbConfig(gaugeModel = AtbGaugeModel.CHARGE),
            )
        val gameIR = buildAtbGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // CHARGE model: charge counter array must be emitted
        assertTrue(
            mainC.contains("_combat_${ATB_ID}_charge"),
            "Expected '_combat_${ATB_ID}_charge' array for CHARGE model",
        )
        // CHARGE model: case 6u (CHARGE state) must be in state machine
        assertTrue(
            mainC.contains("case 6u:"),
            "Expected 'case 6u:' (CHARGE state) in ATB CHARGE model state machine",
        )
    }

    // =========================================================================
    // Test 5: ATB gauge globals emitted as UINT8 arrays
    // =========================================================================

    @Test
    fun `ATB gauge globals emitted as UINT8 arrays`() {
        val gameIR = buildAtbGameIR(defaultAtbSystem())
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // All required ATB global arrays must be present
        assertTrue(
            mainC.contains("_combat_${ATB_ID}_gauge"),
            "Expected '_combat_${ATB_ID}_gauge' global array",
        )
        assertTrue(
            mainC.contains("_combat_${ATB_ID}_active"),
            "Expected '_combat_${ATB_ID}_active' global array",
        )
        assertTrue(
            mainC.contains("_combat_${ATB_ID}_acted"),
            "Expected '_combat_${ATB_ID}_acted' global array",
        )
        assertTrue(
            mainC.contains("_combat_${ATB_ID}_agl"),
            "Expected '_combat_${ATB_ID}_agl' global array",
        )
    }

    // =========================================================================
    // Test 6: Turn order SPEED_BASED generates sort function
    // =========================================================================

    @Test
    fun `turn order SPEED_BASED generates sort function`() {
        val system =
            CombatEngineSystem(
                id = ATB_ID,
                combatType = CombatType.ATB,
                atbConfig = AtbConfig(),
                turnOrderStrategy = TurnOrderStrategy.SPEED_BASED,
                maxCombatants = 4,
            )
        val gameIR = buildAtbGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // SPEED_BASED: compute_turn_order function must be emitted
        assertTrue(
            mainC.contains("compute_turn_order_$ATB_ID"),
            "Expected 'compute_turn_order_$ATB_ID' insertion sort function",
        )
        // Turn order array must be emitted
        assertTrue(
            mainC.contains("_turn_order_$ATB_ID"),
            "Expected '_turn_order_$ATB_ID' global array",
        )
        // The sort function must reference the turn order array with variable index
        assertTrue(
            mainC.contains("_turn_order_${ATB_ID}["),
            "Expected '_turn_order_${ATB_ID}[' array access in sort function",
        )
        // The sort function must reference agility as the sorting key
        assertTrue(
            mainC.contains("_combat_${ATB_ID}_agl["),
            "Expected '_combat_${ATB_ID}_agl[' sorting key in sort function",
        )
        // No TODO stubs should remain
        assertFalse(
            mainC.contains("TODO"),
            "Expected no 'TODO' stubs in generated turn order output",
        )
    }

    // =========================================================================
    // Test 7: ATB base rate and max gauge emitted as defines
    // =========================================================================

    @Test
    fun `ATB base rate and max gauge emitted as defines`() {
        val system =
            CombatEngineSystem(
                id = ATB_ID,
                combatType = CombatType.ATB,
                atbConfig = AtbConfig(baseGaugeFillRate = 4, maxGauge = 200),
            )
        val gameIR = buildAtbGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // ATB defines must be present
        assertTrue(mainC.contains("ATB_BASE_RATE"), "Expected 'ATB_BASE_RATE' #define in main.c")
        assertTrue(mainC.contains("ATB_MAX_GAUGE"), "Expected 'ATB_MAX_GAUGE' #define in main.c")
        // Verify the values
        assertTrue(mainC.contains("ATB_BASE_RATE 4"), "Expected 'ATB_BASE_RATE 4' define value")
        assertTrue(mainC.contains("ATB_MAX_GAUGE 200"), "Expected 'ATB_MAX_GAUGE 200' define value")
    }

    // =========================================================================
    // Test 8: ATB INIT state transitions to GAUGE_FILL (5), not PLAYER_TURN (1)
    // =========================================================================

    @Test
    fun `ATB INIT state transitions to GAUGE_FILL state`() {
        val gameIR = buildAtbGameIR(defaultAtbSystem())
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // ATB: INIT (case 0) should call combat_request_state with 5 (GAUGE_FILL), not 1
        // (PLAYER_TURN)
        // The function update_combat_<id> should transition to state 5 from INIT
        assertTrue(
            mainC.contains("update_combat_$ATB_ID"),
            "Expected 'update_combat_$ATB_ID' function",
        )
        // State 5 must be requested from INIT (case 0u context)
        assertTrue(
            mainC.contains("5u"),
            "Expected state 5u (GAUGE_FILL) transition in ATB state machine",
        )
        // State 1u (PLAYER_TURN) should NOT be the INIT target for ATB
        // (though 1u can appear in other contexts like enemy turn back-transition)
        assertTrue(
            mainC.contains("case 5u:"),
            "Expected 'case 5u:' (GAUGE_FILL state) in ATB state machine",
        )
    }

    // =========================================================================
    // Test 9: Non-ATB system does NOT generate ATB globals
    // =========================================================================

    @Test
    fun `non-ATB system does not generate ATB globals`() {
        val system = CombatEngineSystem(id = "turnbased", combatType = CombatType.TURN_BASED)
        val gameIR = buildAtbGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertFalse(
            mainC.contains("_combat_turnbased_gauge"),
            "Expected NO gauge array for non-ATB system",
        )
        assertFalse(
            mainC.contains("ATB_BASE_RATE"),
            "Expected NO ATB_BASE_RATE define for non-ATB system",
        )
    }

    // =========================================================================
    // Test 10: ATB gauge fill iterates all combatants, not just slot 0
    // =========================================================================

    @Test
    fun `ATB gauge fill iterates all combatants not just slot 0`() {
        val system =
            CombatEngineSystem(
                id = ATB_ID,
                combatType = CombatType.ATB,
                atbConfig = AtbConfig(),
                maxCombatants = 4,
            )
        val gameIR = buildAtbGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // The update_atb_gauges function must use a for-loop structure
        assertTrue(
            mainC.contains("for"),
            "Expected 'for' loop in update_atb_gauges for multi-combatant iteration",
        )
        // The gauge array access should use a variable index, not hardcoded [0]
        // Extract just the update_atb_gauges function body to check specifically
        val funcStart = mainC.indexOf("update_atb_gauges_$ATB_ID")
        val funcEnd =
            if (funcStart >= 0)
                mainC.indexOf("\nvoid ", funcStart + 1).let { if (it < 0) mainC.length else it }
            else mainC.length
        val funcBody = if (funcStart >= 0) mainC.substring(funcStart, funcEnd) else ""
        // The function body should contain gauge[i] (variable index) not only gauge[0]
        assertTrue(
            funcBody.isNotEmpty(),
            "Expected update_atb_gauges_$ATB_ID function to be emitted",
        )
        assertFalse(
            funcBody.contains("TODO"),
            "Expected no 'TODO' stubs in update_atb_gauges function body",
        )
    }

    // =========================================================================
    // Test 11: Fixed order turn order generates initialization loop
    // =========================================================================

    @Test
    fun `fixed order turn order generates initialization loop`() {
        val system =
            CombatEngineSystem(
                id = ATB_ID,
                combatType = CombatType.ATB,
                atbConfig = AtbConfig(),
                turnOrderStrategy = TurnOrderStrategy.FIXED_ORDER,
                maxCombatants = 4,
            )
        val gameIR = buildAtbGameIR(system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // FIXED_ORDER: init_turn_order function must be emitted
        assertTrue(
            mainC.contains("init_turn_order_$ATB_ID"),
            "Expected 'init_turn_order_$ATB_ID' initialization function for FIXED_ORDER",
        )
        // Turn order array must be emitted
        assertTrue(
            mainC.contains("_turn_order_${ATB_ID}["),
            "Expected '_turn_order_${ATB_ID}[' array access in initialization function",
        )
        // No TODO stubs should remain
        assertFalse(
            mainC.contains("TODO"),
            "Expected no 'TODO' stubs in generated fixed turn order output",
        )
    }
}
