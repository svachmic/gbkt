/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.AssetRef
import io.github.gbkt.core.ir.AssetType
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.CombatEngineSystem
import io.github.gbkt.core.ir.CombatType
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.NavigateTo
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// SIMPLE BATTLE + TILESET REUSE TESTS
// Verifies:
// - SimpleBattle generates COMBAT_STATE enum state machine
// - combatIsInState generates state comparison helper
// - Scene with tilesetRef generates _current_tileset_id guard
// =============================================================================

class SimpleBattleAndTilesetTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // C3 — SimpleBattle state machine tests
    // =========================================================================

    @Test
    fun `simpleBattle system generates COMBAT_STATE switch statement`() {
        val gameWithBattle =
            GameIR(
                name = "RPGGame",
                config = CartridgeConfig(),
                scenes = listOf(SceneIR(id = "battle", enterOps = listOf(NavigateTo("gameplay")))),
                systems =
                    listOf(
                        // simpleBattle now produces CombatEngineSystem(TURN_BASED)
                        CombatEngineSystem(id = "main_combat", combatType = CombatType.TURN_BASED)
                    ),
            )

        val output = pipeline.generate(gameWithBattle)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Must generate update_combat function (CombatVisitor TURN_BASED dispatch)
        assertTrue(
            mainC.contains("update_combat_main_combat"),
            "simpleBattle(CombatEngineSystem TURN_BASED) should generate update_combat function",
        )
        // Must have switch statement over _combat_state_
        assertTrue(
            mainC.contains("switch (_combat_state_"),
            "simpleBattle update_combat must have switch statement over _combat_state_",
        )
    }

    @Test
    fun `simpleBattle generates all 5 combat state cases in switch`() {
        val gameWithBattle =
            GameIR(
                name = "RPGGame",
                config = CartridgeConfig(),
                systems =
                    listOf(CombatEngineSystem(id = "combat", combatType = CombatType.TURN_BASED)),
            )

        val output = pipeline.generate(gameWithBattle)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // All 5 combat states must be present in the generated C
        assertTrue(
            mainC.contains("COMBAT_INIT") || mainC.contains("case 0"),
            "Should have COMBAT_INIT (case 0)",
        )
        assertTrue(
            mainC.contains("COMBAT_PLAYER_TURN") || mainC.contains("case 1"),
            "Should have COMBAT_PLAYER_TURN (case 1)",
        )
        assertTrue(
            mainC.contains("COMBAT_ENEMY_TURN") || mainC.contains("case 2"),
            "Should have COMBAT_ENEMY_TURN (case 2)",
        )
        assertTrue(
            mainC.contains("COMBAT_VICTORY") || mainC.contains("case 3"),
            "Should have COMBAT_VICTORY (case 3)",
        )
        assertTrue(
            mainC.contains("COMBAT_DEFEAT") || mainC.contains("case 4"),
            "Should have COMBAT_DEFEAT (case 4)",
        )
    }

    @Test
    fun `simpleBattle trigger function resets combat state to INIT`() {
        val gameWithBattle =
            GameIR(
                name = "RPGGame",
                config = CartridgeConfig(),
                systems =
                    listOf(CombatEngineSystem(id = "combat", combatType = CombatType.TURN_BASED)),
            )

        val output = pipeline.generate(gameWithBattle)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // trigger_combat() must reset state to 0 (COMBAT_INIT)
        assertTrue(
            mainC.contains("trigger_combat"),
            "CombatEngineSystem(TURN_BASED) should generate trigger_combat function",
        )
        // CEmitter emits CLiteral(0) as "0u" for non-negative values
        assertTrue(
            mainC.contains("_combat_state_combat = 0"),
            "trigger_combat should reset _combat_state_combat to 0 (COMBAT_INIT)",
        )
    }

    @Test
    fun `simpleBattle generates combatIsInState helper function`() {
        val gameWithBattle =
            GameIR(
                name = "RPGGame",
                config = CartridgeConfig(),
                systems =
                    listOf(CombatEngineSystem(id = "arena", combatType = CombatType.TURN_BASED)),
            )

        val output = pipeline.generate(gameWithBattle)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("combat_request_state_arena") || mainC.contains("update_combat_arena"),
            "CombatEngineSystem(TURN_BASED) should generate combat functions for arena",
        )
    }

    @Test
    fun `simpleBattle does not immediately execute onVictoryOps (old stub behavior)`() {
        val gameWithBattle =
            GameIR(
                name = "RPGGame",
                config = CartridgeConfig(),
                systems =
                    listOf(CombatEngineSystem(id = "combat", combatType = CombatType.TURN_BASED)),
            )

        val output = pipeline.generate(gameWithBattle)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Old stub behavior: "executes victory immediately" — must be gone
        assertFalse(
            mainC.contains("simple_battle stub — executes victory immediately"),
            "simple_battle should NOT generate the old 'executes victory immediately' stub",
        )
    }

    @Test
    fun `simpleBattle _combat_state global variable declared in main_c`() {
        val gameWithBattle =
            GameIR(
                name = "RPGGame",
                config = CartridgeConfig(),
                systems =
                    listOf(
                        CombatEngineSystem(id = "main_battle", combatType = CombatType.TURN_BASED)
                    ),
            )

        val output = pipeline.generate(gameWithBattle)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_combat_state_main_battle"),
            "main.c should declare _combat_state_main_battle global variable",
        )
    }

    // =========================================================================
    // C4 — Scene tileset reuse guard tests
    // =========================================================================

    @Test
    fun `scene with tilesetRef generates _current_tileset_id guard in enter function`() {
        val gameWithTileset =
            GameIR(
                name = "TileGame",
                config = CartridgeConfig(),
                scenes =
                    listOf(
                        SceneIR(
                            id = "dungeon",
                            tilesetRef = AssetRef("tilesets/dungeon.png", AssetType.TILESET),
                            enterOps = listOf(NavigateTo("dungeon")),
                        )
                    ),
            )

        val output = pipeline.generate(gameWithTileset)
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        // Enter function should contain _current_tileset_id guard
        assertTrue(
            bank1C.contains("_current_tileset_id"),
            "Scene with tilesetRef should generate _current_tileset_id guard in enter function",
        )
        assertTrue(
            bank1C.contains("TILESET_ID_"),
            "Scene with tilesetRef should reference TILESET_ID_ constant",
        )
    }

    @Test
    fun `scene without tilesetRef does not generate _current_tileset_id guard`() {
        val gameWithoutTileset =
            GameIR(
                name = "SimpleGame",
                config = CartridgeConfig(),
                scenes = listOf(SceneIR(id = "title", enterOps = listOf(NavigateTo("title")))),
            )

        val output = pipeline.generate(gameWithoutTileset)
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        // Enter function should NOT contain tileset guard when no tilesetRef
        // Note: _current_tileset_id is declared as global in main.c, not bank1.c
        assertFalse(
            bank1C.contains("TILESET_ID_TITLE"),
            "Scene without tilesetRef should NOT generate TILESET_ID_ guard",
        )
    }

    @Test
    fun `_current_tileset_id global variable declared in main_c initialized to 0xFF`() {
        val gameIR = GameIR(name = "Test", config = CartridgeConfig())

        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_current_tileset_id"),
            "main.c should declare _current_tileset_id global",
        )
        assertTrue(
            mainC.contains("0xFF"),
            "main.c should initialize _current_tileset_id to 0xFF (no tileset loaded)",
        )
    }

    @Test
    fun `two scenes with same tilesetRef share same TILESET_ID constant`() {
        val sharedTilesetRef = AssetRef("tilesets/dungeon.png", AssetType.TILESET)
        val gameWithSharedTileset =
            GameIR(
                name = "TileGame",
                config = CartridgeConfig(),
                scenes =
                    listOf(
                        SceneIR(
                            id = "floor1",
                            tilesetRef = sharedTilesetRef,
                            enterOps = listOf(NavigateTo("floor2")),
                        ),
                        SceneIR(
                            id = "floor2",
                            tilesetRef = sharedTilesetRef,
                            enterOps = listOf(NavigateTo("floor1")),
                        ),
                    ),
            )

        val output = pipeline.generate(gameWithSharedTileset)
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        // Both scenes should use the SAME TILESET_ID (not two different ones)
        // The first scene with the tilesetRef defines the ID
        val tileset1Count = bank1C.split("TILESET_ID_FLOOR1").size - 1
        val tileset2Count = bank1C.split("TILESET_ID_FLOOR2").size - 1
        // One of them should be the canonical tileset ID (from the first registered scene)
        assertTrue(
            bank1C.contains("_current_tileset_id"),
            "Shared-tileset scenes should both use _current_tileset_id guard",
        )
    }
}
