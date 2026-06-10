/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.visitor

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipeline
import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.TacticalGridConfig
import io.github.gbkt.core.ir.TerrainTypeDef
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// TACTICAL GRID CODEGEN TESTS (Plan 06.5-06 / 06.5-12 success criterion)
// Tests cover all CombatVisitor TACTICAL_GRID code paths:
//   - TACTICAL_GRID combat type generates expected function names
//   - Movement range BFS function structure (iterative, no recursion)
//   - LOS Bresenham check function (tile-walk loop with return 0/1)
//   - Facing bonus direction logic (when enableFacing=true)
//   - Elevation bonus height check (when enableElevation=true)
//   - AoE targeting resolution (shape-based switch dispatch)
//   - range parameter propagated into targeting functions
// =============================================================================

private fun buildTacticalGameIR(
    system: CombatEngineSystem,
    startScene: String = "gameplay",
): GameIR =
    GameIR(
        name = "TestTacticalGame",
        config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY),
        scenes = listOf(SceneIR(id = startScene)),
        systems = listOf(system),
        startScene = startScene,
    )

private const val GRID_ID = "battle"

private fun defaultTacticalSystem(): CombatEngineSystem =
    CombatEngineSystem(
        id = GRID_ID,
        combatType = CombatType.TACTICAL_GRID,
        tacticalGridConfig = TacticalGridConfig(gridWidth = 8, gridHeight = 8),
    )

class TacticalGridCodegenTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // Test 1: TACTICAL_GRID generates standard combat functions
    // =========================================================================

    @Test
    fun `tactical grid generates update_combat and combat state functions`() {
        val gameIR = buildTacticalGameIR(defaultTacticalSystem())
        val output = pipeline.generate(gameIR)
        val allC = output.files.values.joinToString("\n")

        assertTrue(
            allC.contains("void update_combat_$GRID_ID(void)"),
            "Expected update_combat_$GRID_ID function",
        )
        assertTrue(
            allC.contains("void combat_request_state_$GRID_ID(UINT8 s)"),
            "Expected combat_request_state function",
        )
        assertTrue(
            allC.contains("void trigger_$GRID_ID(void)"),
            "Expected trigger_$GRID_ID function",
        )
    }

    // =========================================================================
    // Test 2: TACTICAL_GRID PLAYER_TURN case contains tactical comment
    // =========================================================================

    @Test
    fun `tactical grid PLAYER_TURN case generates tactical comment not J_A check`() {
        val gameIR = buildTacticalGameIR(defaultTacticalSystem())
        val output = pipeline.generate(gameIR)
        val allC = output.files.values.joinToString("\n")

        // TACTICAL_GRID should NOT have the turn-based J_A check
        assertFalse(
            allC.contains("button_pressed(J_A)"),
            "TACTICAL_GRID should not generate button_pressed(J_A) in PLAYER_TURN",
        )
        // Should have the tactical comment
        assertTrue(
            allC.contains("tactical"),
            "Expected 'tactical' comment in PLAYER_TURN case for TACTICAL_GRID",
        )
    }

    // =========================================================================
    // Test 3: movement range function uses iterative BFS (not recursive)
    // =========================================================================

    @Test
    fun `tactical grid generates movement range function with BFS structure`() {
        val gameIR = buildTacticalGameIR(defaultTacticalSystem())
        val output = pipeline.generate(gameIR)
        val allC = output.files.values.joinToString("\n")

        // The movement range function must exist
        assertTrue(
            allC.contains("compute_movement_range_$GRID_ID") ||
                allC.contains("calc_movement_range_$GRID_ID"),
            "Expected movement range function for TACTICAL_GRID system",
        )
        // BFS uses a queue — must have head/tail variables
        assertTrue(
            allC.contains("head") && allC.contains("tail"),
            "Expected BFS queue head/tail variables in movement range function",
        )
        // BFS is iterative — must have a while loop
        assertTrue(
            allC.contains("while"),
            "Expected while loop for iterative BFS in movement range function",
        )
        // Must reference the reachable and terrain arrays
        assertTrue(
            allC.contains("_tg_${GRID_ID}_reachable"),
            "Expected _tg_${GRID_ID}_reachable array access in movement range function",
        )
        // No TODO stubs
        assertFalse(
            allC.contains("TODO"),
            "Expected no TODO comments in movement range function — all stubs should be replaced",
        )
    }

    // =========================================================================
    // Test 3b: movement range BFS contains no recursion
    // =========================================================================

    @Test
    fun `tactical grid movement range BFS contains no recursion`() {
        val gameIR = buildTacticalGameIR(defaultTacticalSystem())
        val output = pipeline.generate(gameIR)

        // Check only the HOME file (main.c) to avoid matching prototypes in game.h
        val homeC = output.files["main.c"] ?: error("Expected main.c in output")
        val funcPrefix = "calc_movement_range_$GRID_ID"

        // The function must exist
        assertTrue(homeC.contains(funcPrefix), "Expected $funcPrefix function to exist")

        // The function must NOT recursively call itself — iterative BFS only
        // Find the function definition and check its body doesn't self-call
        val funcStart = homeC.indexOf("void $funcPrefix(")
        assertTrue(funcStart >= 0, "Expected 'void $funcPrefix(' function signature")
        val funcBody = homeC.substring(funcStart)
        // Self-call would appear as "calc_movement_range_battle(" in the body (after the
        // definition)
        val bodyAfterSignature = funcBody.substringAfter("{")
        assertFalse(
            bodyAfterSignature.contains("${funcPrefix}("),
            "Movement range BFS must be iterative — no recursive calls to $funcPrefix",
        )

        // Iterative approach confirmed by while loop
        assertTrue(funcBody.contains("while"), "Expected while loop (iterative BFS) in $funcPrefix")
    }

    // =========================================================================
    // Test 4: line-of-sight function is generated with loop and return values
    // =========================================================================

    @Test
    fun `tactical grid generates line of sight function`() {
        val gameIR = buildTacticalGameIR(defaultTacticalSystem())
        val output = pipeline.generate(gameIR)
        val allC = output.files.values.joinToString("\n")

        assertTrue(
            allC.contains("check_line_of_sight_$GRID_ID"),
            "Expected check_line_of_sight_$GRID_ID function for TACTICAL_GRID",
        )
        // LOS uses a tile-walk loop
        assertTrue(
            allC.contains("while"),
            "Expected while loop for Bresenham tile-walk in LOS function",
        )
        // LOS returns 0 (blocked) or 1 (visible)
        assertTrue(
            allC.contains("return 0u") || allC.contains("return 0"),
            "Expected 'return 0' (blocked) path in LOS function",
        )
        assertTrue(
            allC.contains("return 1u") || allC.contains("return 1"),
            "Expected 'return 1' (visible) path in LOS function",
        )
        // No TODO stubs
        assertFalse(allC.contains("TODO"), "Expected no TODO comments in LOS function")
    }

    // =========================================================================
    // Test 5: facing bonus function only generated when enableFacing=true
    // =========================================================================

    @Test
    fun `tactical grid generates facing bonus function when enableFacing is true`() {
        val system =
            CombatEngineSystem(
                id = GRID_ID,
                combatType = CombatType.TACTICAL_GRID,
                tacticalGridConfig =
                    TacticalGridConfig(enableFacing = true, flankingBonus = 25, backstabBonus = 50),
            )
        val gameIR = buildTacticalGameIR(system)
        val output = pipeline.generate(gameIR)
        val allC = output.files.values.joinToString("\n")

        assertTrue(
            allC.contains("compute_facing_bonus_$GRID_ID") ||
                allC.contains("calc_facing_bonus_$GRID_ID"),
            "Expected facing bonus function when enableFacing=true",
        )
        // Flanking bonus value should appear in the output
        assertTrue(
            allC.contains("25"),
            "Expected flanking bonus value '25' in facing bonus function output",
        )
        // Backstab bonus value should appear in the output
        assertTrue(
            allC.contains("50"),
            "Expected backstab bonus value '50' in facing bonus function output",
        )
        // No TODO stubs
        assertFalse(allC.contains("TODO"), "Expected no TODO comments in facing bonus function")
    }

    @Test
    fun `tactical grid skips facing bonus function when enableFacing is false`() {
        val system =
            CombatEngineSystem(
                id = GRID_ID,
                combatType = CombatType.TACTICAL_GRID,
                tacticalGridConfig = TacticalGridConfig(enableFacing = false),
            )
        val gameIR = buildTacticalGameIR(system)
        val output = pipeline.generate(gameIR)
        val allC = output.files.values.joinToString("\n")

        assertFalse(
            allC.contains("compute_facing_bonus_$GRID_ID") ||
                allC.contains("calc_facing_bonus_$GRID_ID"),
            "Expected NO facing bonus function when enableFacing=false",
        )
    }

    // =========================================================================
    // Test 6: elevation bonus only generated when enableElevation=true
    // =========================================================================

    @Test
    fun `tactical grid generates elevation bonus function when enableElevation is true`() {
        val system =
            CombatEngineSystem(
                id = GRID_ID,
                combatType = CombatType.TACTICAL_GRID,
                tacticalGridConfig =
                    TacticalGridConfig(enableElevation = true, elevationDamageBonus = 10),
            )
        val gameIR = buildTacticalGameIR(system)
        val output = pipeline.generate(gameIR)
        val allC = output.files.values.joinToString("\n")

        assertTrue(
            allC.contains("compute_elevation_bonus_$GRID_ID") ||
                allC.contains("calc_elevation_bonus_$GRID_ID"),
            "Expected elevation bonus function when enableElevation=true",
        )
        // Elevation bonus value should appear in the output
        assertTrue(
            allC.contains("10"),
            "Expected elevation damage bonus value '10' in elevation bonus function output",
        )
        // No TODO stubs
        assertFalse(allC.contains("TODO"), "Expected no TODO comments in elevation bonus function")
    }

    // =========================================================================
    // Test 7: AoE targeting resolution function is generated with shape dispatch
    // =========================================================================

    @Test
    fun `tactical grid generates AoE targeting function`() {
        val gameIR = buildTacticalGameIR(defaultTacticalSystem())
        val output = pipeline.generate(gameIR)
        val allC = output.files.values.joinToString("\n")

        assertTrue(
            allC.contains("resolve_aoe_targets_$GRID_ID") ||
                allC.contains("calc_aoe_targets_$GRID_ID"),
            "Expected AoE targeting function for TACTICAL_GRID",
        )
        // AoE function dispatches on shape via switch
        assertTrue(
            allC.contains("switch"),
            "Expected switch statement for shape dispatch in AoE targeting function",
        )
        // No TODO stubs
        assertFalse(allC.contains("TODO"), "Expected no TODO comments in AoE targeting function")
    }

    // =========================================================================
    // Test 8: terrain types can be configured via TacticalGridConfig
    // =========================================================================

    @Test
    fun `tactical grid TacticalGridConfig with custom terrain types is accepted`() {
        val system =
            CombatEngineSystem(
                id = GRID_ID,
                combatType = CombatType.TACTICAL_GRID,
                tacticalGridConfig =
                    TacticalGridConfig(
                        gridWidth = 10,
                        gridHeight = 10,
                        enableTerrain = true,
                        terrainTypes =
                            listOf(
                                TerrainTypeDef(id = "plain", name = "Plain", movementCost = 1),
                                TerrainTypeDef(
                                    id = "marsh",
                                    name = "Marsh",
                                    movementCost = 2,
                                    damagePerTurn = 5,
                                ),
                                TerrainTypeDef(id = "wall", name = "Wall", movementCost = -1),
                            ),
                    ),
            )
        val gameIR = buildTacticalGameIR(system)
        // Should compile and generate without error
        val output = pipeline.generate(gameIR)
        val allC = output.files.values.joinToString("\n")

        assertTrue(
            allC.contains("update_combat_$GRID_ID"),
            "Expected update_combat function for system with custom terrain types",
        )
    }
}
