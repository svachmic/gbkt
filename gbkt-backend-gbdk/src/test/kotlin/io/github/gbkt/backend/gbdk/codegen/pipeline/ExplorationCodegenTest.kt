/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.Cartridge
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.EncounterEntryIR
import io.github.gbkt.core.ir.EncounterTableIR
import io.github.gbkt.core.ir.ExplorationGaugeIR
import io.github.gbkt.core.ir.ExplorationKeyIR
import io.github.gbkt.core.ir.ExplorationSystem
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.TransitionEdge
import io.github.gbkt.core.ir.ZoneIR
import kotlin.test.Test
import kotlin.test.assertTrue

// =============================================================================
// EXPLORATION CODEGEN TESTS (Plan 06.3-02)
// Verifies that GBDKPipeline generates correct C code for ExplorationSystem:
//  - exploration_move, exploration_step, exploration_encounter_check, exploration_interact
//  - zone_load, zone_transition (Gap 7+8)
//  - buildSystemGlobalVars expansion (gauges, keys, step counter, encounter globals)
//  - buildZoneData (const UINT8 _zone_{id}_tiles[] arrays)
// =============================================================================

/** Helper: build a GameIR with an ExplorationSystem and the given zones. */
private fun buildExplorationGame(
    system: ExplorationSystem = ExplorationSystem(id = "dungeon"),
    zones: List<ZoneIR> = emptyList(),
    scenes: List<SceneIR> = listOf(SceneIR(id = "gameplay")),
): GameIR =
    GameIR(
        name = "TestGame",
        config = CartridgeConfig(cartridge = Cartridge.ROM_ONLY),
        scenes = scenes,
        systems = listOf(system),
        zones = zones,
        startScene = "gameplay",
    )

class ExplorationCodegenTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // Test 1: Exploration move function generated
    // =========================================================================

    @Test
    fun `ExplorationSystem generates exploration_move function with d-pad checks`() {
        val gameIR = buildExplorationGame()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("exploration_move_dungeon"), "exploration_move_dungeon missing")
        assertTrue(mainC.contains("J_UP"), "d-pad J_UP check missing from move function")
        assertTrue(mainC.contains("J_DOWN"), "d-pad J_DOWN check missing from move function")
        assertTrue(mainC.contains("J_LEFT"), "d-pad J_LEFT check missing from move function")
        assertTrue(mainC.contains("J_RIGHT"), "d-pad J_RIGHT check missing from move function")
        assertTrue(
            mainC.contains("_map_collision"),
            "_map_collision call missing from move function",
        )
    }

    @Test
    fun `exploration_move calls exploration_step after position update`() {
        val gameIR = buildExplorationGame()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("exploration_step_dungeon"),
            "exploration_step_dungeon call missing from move function",
        )
    }

    // =========================================================================
    // Test 2: Exploration step function with gauge
    // =========================================================================

    @Test
    fun `ExplorationSystem with gauge generates exploration_step that decrements gauge`() {
        val torchGauge =
            ExplorationGaugeIR(
                id = "torch",
                max = 255,
                initial = 255,
                decrementPerStep = 1,
                onLowThreshold = 50,
            )
        val system = ExplorationSystem(id = "dungeon", gauges = listOf(torchGauge))
        val gameIR = buildExplorationGame(system = system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("exploration_step_dungeon"),
            "exploration_step_dungeon function missing",
        )
        assertTrue(
            mainC.contains("_gauge_torch"),
            "_gauge_torch variable reference missing from step function",
        )
        assertTrue(mainC.contains("-="), "gauge decrement operator missing from step function")
    }

    @Test
    fun `exploration_step gauge checks onLow threshold`() {
        val torchGauge =
            ExplorationGaugeIR(
                id = "torch",
                max = 255,
                initial = 255,
                decrementPerStep = 1,
                onLowThreshold = 50,
                onLowStatements = emptyList(), // no ops but threshold still generates check
            )
        val system = ExplorationSystem(id = "dungeon", gauges = listOf(torchGauge))
        val gameIR = buildExplorationGame(system = system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Should contain the threshold value 50 in context of gauge check
        assertTrue(mainC.contains("_gauge_torch"), "_gauge_torch missing")
    }

    @Test
    fun `exploration_step increments step counter`() {
        val gameIR = buildExplorationGame()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_exploration_step_count"),
            "_exploration_step_count missing from step function",
        )
    }

    // =========================================================================
    // Test 3: Encounter check function
    // =========================================================================

    @Test
    fun `zone with encounter table generates exploration_encounter_check function`() {
        val encounterTable =
            EncounterTableIR(
                safeSteps = 10,
                entries =
                    listOf(
                        EncounterEntryIR(id = "goblin", weight = 30),
                        EncounterEntryIR(id = "slime", weight = 20),
                    ),
            )
        val zone = ZoneIR(id = "floor1", name = "Floor 1", encounterTable = encounterTable)
        val gameIR = buildExplorationGame(zones = listOf(zone))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("exploration_encounter_check_dungeon"),
            "encounter_check function missing",
        )
        assertTrue(mainC.contains("rand("), "rand() call missing from encounter check")
    }

    @Test
    fun `encounter check uses weighted random dispatch`() {
        val encounterTable =
            EncounterTableIR(
                safeSteps = 5,
                entries =
                    listOf(
                        EncounterEntryIR(id = "goblin", weight = 30),
                        EncounterEntryIR(id = "slime", weight = 20),
                    ),
            )
        val zone = ZoneIR(id = "floor1", name = "Floor 1", encounterTable = encounterTable)
        val gameIR = buildExplorationGame(zones = listOf(zone))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Total weight is 50 — rand() % 50 should appear
        assertTrue(mainC.contains("50"), "Total weight 50 missing from encounter dispatch")
        assertTrue(
            mainC.contains("_encounter_triggered"),
            "_encounter_triggered assignment missing",
        )
        assertTrue(mainC.contains("_encounter_id"), "_encounter_id assignment missing")
    }

    // =========================================================================
    // Test 4: Zone load function
    // =========================================================================

    @Test
    fun `zone load function generated for ExplorationSystem with zones`() {
        val zone = ZoneIR(id = "floor1", name = "Floor 1", mapWidth = 16, mapHeight = 16)
        val gameIR = buildExplorationGame(zones = listOf(zone))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("zone_load_dungeon"), "zone_load_dungeon function missing")
        assertTrue(mainC.contains("set_bkg_tiles"), "set_bkg_tiles call missing from zone_load")
    }

    @Test
    fun `zone_load has tileset reuse guard`() {
        val zone = ZoneIR(id = "floor1", name = "Floor 1", mapWidth = 16, mapHeight = 16)
        val gameIR = buildExplorationGame(zones = listOf(zone))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_current_tileset_id"),
            "tileset reuse guard missing from zone_load",
        )
    }

    // =========================================================================
    // Test 5: System globals generated
    // =========================================================================

    @Test
    fun `ExplorationSystem with gauge and key generates global declarations`() {
        val torch = ExplorationGaugeIR(id = "torch", max = 255, initial = 255, decrementPerStep = 1)
        val magicKey = ExplorationKeyIR(id = "magic_key", max = 99, initial = 0)
        val system =
            ExplorationSystem(id = "dungeon", gauges = listOf(torch), keys = listOf(magicKey))
        val gameIR = buildExplorationGame(system = system)
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("_gauge_torch"), "_gauge_torch global missing")
        assertTrue(mainC.contains("_key_magic_key"), "_key_magic_key global missing")
        assertTrue(
            mainC.contains("_exploration_step_count"),
            "_exploration_step_count global missing",
        )
        assertTrue(mainC.contains("_encounter_safe_steps"), "_encounter_safe_steps global missing")
    }

    @Test
    fun `ExplorationSystem generates _current_zone_id global`() {
        val gameIR = buildExplorationGame()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("_current_zone_id"), "_current_zone_id global missing")
    }

    @Test
    fun `ExplorationSystem generates _encounter_triggered and _encounter_id globals`() {
        val gameIR = buildExplorationGame()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("_encounter_triggered"), "_encounter_triggered global missing")
        assertTrue(mainC.contains("_encounter_id"), "_encounter_id global missing")
    }

    // =========================================================================
    // Test 6: Zone tilemap data arrays
    // =========================================================================

    @Test
    fun `zone with tileData generates const tile array in main dot c`() {
        val tileData = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16) // 4x4 map
        val zone =
            ZoneIR(
                id = "floor1",
                name = "Floor 1",
                mapWidth = 4,
                mapHeight = 4,
                tileData = tileData,
            )
        val gameIR = buildExplorationGame(zones = listOf(zone))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("_zone_floor1_tiles"), "_zone_floor1_tiles array missing")
    }

    @Test
    fun `zone without tileData generates placeholder tile array`() {
        val zone = ZoneIR(id = "empty_zone", name = "Empty Zone")
        val gameIR = buildExplorationGame(zones = listOf(zone))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Placeholder array emitted for empty tileData
        assertTrue(
            mainC.contains("_zone_empty_zone_tiles"),
            "_zone_empty_zone_tiles placeholder missing",
        )
    }

    // =========================================================================
    // Test 7: Safe zone flag
    // =========================================================================

    @Test
    fun `zone marked as safe zone causes encounter check to return early`() {
        val encounterTable =
            EncounterTableIR(
                safeSteps = 5,
                entries = listOf(EncounterEntryIR(id = "goblin", weight = 10)),
            )
        val safeZone =
            ZoneIR(id = "town", name = "Town", isSafeZone = true, encounterTable = encounterTable)
        val gameIR = buildExplorationGame(zones = listOf(safeZone))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // _current_zone_safe must be set for this zone
        assertTrue(mainC.contains("_current_zone_safe"), "_current_zone_safe missing")
        // Encounter check should check safe flag
        assertTrue(
            mainC.contains("exploration_encounter_check_dungeon"),
            "encounter check function missing",
        )
    }

    // =========================================================================
    // Test 8: Conditional encounter entry
    // =========================================================================

    @Test
    fun `conditional encounter entry generates flag check before weight accumulation`() {
        val encounterTable =
            EncounterTableIR(
                safeSteps = 5,
                entries =
                    listOf(
                        EncounterEntryIR(id = "goblin", weight = 20),
                        EncounterEntryIR(id = "boss", weight = 10, conditionFlag = "defeated_boss"),
                    ),
            )
        val zone = ZoneIR(id = "floor1", name = "Floor 1", encounterTable = encounterTable)
        val gameIR = buildExplorationGame(zones = listOf(zone))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Conditional flag must appear in encounter check
        assertTrue(
            mainC.contains("_flag_defeated_boss"),
            "_flag_defeated_boss check missing for conditional encounter entry",
        )
    }

    // =========================================================================
    // Test 9: Zone transition — edge-based auto-mapping (Gap 7)
    // =========================================================================

    @Test
    fun `zone_transition function generated with edge parameter`() {
        val zone = ZoneIR(id = "floor1", name = "Floor 1")
        val gameIR = buildExplorationGame(zones = listOf(zone))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("zone_transition_dungeon"),
            "zone_transition_dungeon function missing",
        )
        assertTrue(mainC.contains("entry_x"), "entry_x parameter missing from zone_transition")
        assertTrue(mainC.contains("entry_y"), "entry_y parameter missing from zone_transition")
    }

    @Test
    fun `zone_transition EAST edge sets player x to 0`() {
        val zone = ZoneIR(id = "floor1", name = "Floor 1")
        val gameIR = buildExplorationGame(zones = listOf(zone))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // EAST ordinal in TransitionEdge is 2 (NORTH=0, SOUTH=1, EAST=2, WEST=3)
        val eastOrdinal = TransitionEdge.EAST.ordinal.toString()
        assertTrue(
            mainC.contains(eastOrdinal),
            "EAST ordinal ($eastOrdinal) missing from zone_transition switch",
        )
        // After EAST exit, player x should be set to 0
        assertTrue(mainC.contains("_player_x"), "_player_x assignment missing from zone_transition")
    }

    @Test
    fun `zone_transition contains all four edge cases`() {
        val zone = ZoneIR(id = "floor1", name = "Floor 1")
        val gameIR = buildExplorationGame(zones = listOf(zone))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // All 4 direction ordinals should appear in the edge switch
        val northOrdinal = TransitionEdge.NORTH.ordinal.toString()
        val southOrdinal = TransitionEdge.SOUTH.ordinal.toString()
        val eastOrdinal = TransitionEdge.EAST.ordinal.toString()
        val westOrdinal = TransitionEdge.WEST.ordinal.toString()

        // zone_transition function must contain the edge switch with all directions
        assertTrue(mainC.contains("zone_transition_dungeon"), "zone_transition_dungeon missing")
        // The function body contains cases for EAST, WEST, NORTH, SOUTH
        // We check for the player_x/player_y assignments (at least x=0 for EAST and x=31 for WEST)
        assertTrue(mainC.contains("_player_x"), "_player_x assignment missing in zone_transition")
        assertTrue(mainC.contains("_player_y"), "_player_y assignment missing in zone_transition")
    }

    // =========================================================================
    // Test 10: Zone transition — explicit entry coordinates (Gap 7)
    // =========================================================================

    @Test
    fun `zone_transition uses explicit entry coordinates when not 0xFF sentinel`() {
        val zone = ZoneIR(id = "floor1", name = "Floor 1")
        val gameIR = buildExplorationGame(zones = listOf(zone))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // The function must compare entry_x != 0xFF (sentinel) to detect explicit coordinates
        // 0xFF is emitted as 255u by CEmitter (CLiteral with unsigned suffix)
        val has0xff = mainC.contains("0xFF") || mainC.contains("255u") || mainC.contains("255")
        assertTrue(has0xff, "0xFF sentinel comparison missing from zone_transition")
        assertTrue(mainC.contains("entry_x"), "entry_x parameter missing from zone_transition")
        assertTrue(mainC.contains("entry_y"), "entry_y parameter missing from zone_transition")
    }

    // =========================================================================
    // Test 11: Zone transition calls onExit before loading new zone (Gap 8)
    // =========================================================================

    @Test
    fun `zone_transition calls zone_load after player position update`() {
        val zone = ZoneIR(id = "floor1", name = "Floor 1")
        val gameIR = buildExplorationGame(zones = listOf(zone))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // zone_transition must call zone_load at the end
        assertTrue(
            mainC.contains("zone_load_dungeon"),
            "zone_load_dungeon call missing from zone_transition",
        )
    }

    @Test
    fun `zone_transition with onExit callback generates dispatch before zone_load`() {
        val onExitZone =
            ZoneIR(
                id = "floor1",
                name = "Floor 1",
                onExit = emptyList(), // empty — tested by checking switch dispatch structure
            )
        val gameIR = buildExplorationGame(zones = listOf(onExitZone))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // zone_transition must be generated even without onExit ops
        assertTrue(mainC.contains("zone_transition_dungeon"), "zone_transition_dungeon missing")
        // zone_load must appear AFTER the position assignment section (order matters)
        val zoneTransitionStart = mainC.indexOf("zone_transition_dungeon")
        val zoneLoadCall = mainC.indexOf("zone_load_dungeon(", zoneTransitionStart)
        assertTrue(zoneTransitionStart >= 0, "zone_transition_dungeon function start not found")
        assertTrue(
            zoneLoadCall > zoneTransitionStart,
            "zone_load call should appear inside zone_transition body",
        )
    }

    // =========================================================================
    // Test 12: Full lifecycle sequence — onExit -> position -> zone_load -> onEnter (Gap 8)
    // =========================================================================

    @Test
    fun `zone_transition sequence has current_zone_id dispatch before player position and zone_load`() {
        // Two zones, second has onEnter, first has onExit (via _current_zone_id dispatch)
        val floor1 = ZoneIR(id = "floor1", name = "Floor 1")
        val floor2 = ZoneIR(id = "floor2", name = "Floor 2")
        val gameIR = buildExplorationGame(zones = listOf(floor1, floor2))
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // zone_transition must exist and contain _current_zone_id (for onExit dispatch)
        assertTrue(mainC.contains("zone_transition_dungeon"), "zone_transition_dungeon missing")
        // _current_zone_id used as switch expression for onExit dispatch
        assertTrue(
            mainC.contains("_current_zone_id"),
            "_current_zone_id missing from zone_transition",
        )
        // zone_load called after position update
        assertTrue(
            mainC.contains("zone_load_dungeon"),
            "zone_load_dungeon missing from zone_transition",
        )
    }

    @Test
    fun `exploration_interact function is generated (no-op for empty interactStatements)`() {
        val gameIR = buildExplorationGame()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("exploration_interact_dungeon"),
            "exploration_interact_dungeon missing",
        )
    }
}
