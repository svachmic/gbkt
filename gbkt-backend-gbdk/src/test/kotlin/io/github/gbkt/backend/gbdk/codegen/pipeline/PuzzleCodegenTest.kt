/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.ActivatePuzzleObject
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.DeactivatePuzzleObject
import io.github.gbkt.core.ir.DoorObjectIR
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.HidePuzzleObject
import io.github.gbkt.core.ir.PressurePlateObjectIR
import io.github.gbkt.core.ir.RevealPuzzleObject
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SwitchObjectIR
import io.github.gbkt.core.ir.TimedBlockObjectIR
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// PUZZLE CODEGEN TESTS
// Verifies that GBDKPipelineV2 emits the correct C for all four puzzle object
// types: switch, door, pressure plate, and timed block.
// =============================================================================

class PuzzleCodegenTest {

    private val pipeline = GBDKPipelineV2()

    private fun makeGame(vararg puzzleObjects: io.github.gbkt.core.ir.PuzzleObjectIR): GameIR =
        GameIR(
            name = "PuzzleGame",
            config = CartridgeConfig(),
            scenes = listOf(SceneIR(id = "main")),
            puzzleObjects = puzzleObjects.toList(),
        )

    // =========================================================================
    // Switch — state variable + activate/deactivate functions
    // =========================================================================

    @Test
    fun `switch generates state variable _switch_id_active`() {
        val game = makeGame(SwitchObjectIR(id = "sw1", x = 5, y = 3))
        val mainC = pipeline.generate(game).files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_switch_sw1_active"),
            "main.c should contain _switch_sw1_active state variable",
        )
    }

    @Test
    fun `switch generates puzzle_activate function`() {
        val game = makeGame(SwitchObjectIR(id = "sw1", x = 5, y = 3))
        val mainC = pipeline.generate(game).files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("puzzle_activate_sw1"),
            "main.c should contain puzzle_activate_sw1 function",
        )
    }

    @Test
    fun `switch generates puzzle_deactivate function`() {
        val game = makeGame(SwitchObjectIR(id = "sw1", x = 5, y = 3))
        val mainC = pipeline.generate(game).files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("puzzle_deactivate_sw1"),
            "main.c should contain puzzle_deactivate_sw1 function",
        )
    }

    @Test
    fun `hidden switch generates _switch_id_hidden state variable`() {
        val game = makeGame(SwitchObjectIR(id = "hiddenSw", x = 2, y = 2, hidden = true))
        val mainC = pipeline.generate(game).files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_switch_hiddenSw_hidden"),
            "main.c should contain _switch_hiddenSw_hidden state variable",
        )
    }

    // =========================================================================
    // Door — state variable + activate/deactivate with tile swap
    // =========================================================================

    @Test
    fun `door generates state variable _door_id_open`() {
        val game =
            makeGame(
                DoorObjectIR(id = "bossDoor", x = 10, y = 5, openTile = 0x20, closedTile = 0x21)
            )
        val mainC = pipeline.generate(game).files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_door_bossDoor_open"),
            "main.c should contain _door_bossDoor_open state variable",
        )
    }

    @Test
    fun `door generate puzzle_activate with openTile in set_bkg_tile_xy call`() {
        val game =
            makeGame(
                DoorObjectIR(id = "bossDoor", x = 10, y = 5, openTile = 0x20, closedTile = 0x21)
            )
        val mainC = pipeline.generate(game).files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("puzzle_activate_bossDoor"),
            "main.c should contain puzzle_activate_bossDoor function",
        )
        // The activate function should call set_bkg_tile_xy with the open tile (0x20 = 32)
        assertTrue(
            mainC.contains("set_bkg_tile_xy"),
            "puzzle_activate_bossDoor should call set_bkg_tile_xy for tile swap",
        )
        assertTrue(mainC.contains("32"), "open tile 0x20=32 should appear in set_bkg_tile_xy call")
    }

    @Test
    fun `door generates puzzle_deactivate with closedTile in set_bkg_tile_xy call`() {
        val game =
            makeGame(
                DoorObjectIR(id = "bossDoor", x = 10, y = 5, openTile = 0x20, closedTile = 0x21)
            )
        val mainC = pipeline.generate(game).files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("puzzle_deactivate_bossDoor"),
            "main.c should contain puzzle_deactivate_bossDoor function",
        )
        // 0x21 = 33
        assertTrue(
            mainC.contains("33"),
            "closed tile 0x21=33 should appear in deactivate set_bkg_tile_xy call",
        )
    }

    // =========================================================================
    // Pressure plate — state variable + check function with actor position test
    // =========================================================================

    @Test
    fun `pressure plate generates state variable _plate_id_pressed`() {
        val game =
            makeGame(
                PressurePlateObjectIR(
                    id = "entryPlate",
                    x = 7,
                    y = 4,
                    respondToActorIds = listOf("player"),
                )
            )
        val mainC = pipeline.generate(game).files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_plate_entryPlate_pressed"),
            "main.c should contain _plate_entryPlate_pressed state variable",
        )
    }

    @Test
    fun `pressure plate generates puzzle_check_plate function`() {
        val game =
            makeGame(
                PressurePlateObjectIR(
                    id = "entryPlate",
                    x = 7,
                    y = 4,
                    respondToActorIds = listOf("player"),
                )
            )
        val mainC = pipeline.generate(game).files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("puzzle_check_plate_entryPlate"),
            "main.c should contain puzzle_check_plate_entryPlate function",
        )
    }

    @Test
    fun `pressure plate check function tests respondTo actor positions`() {
        val game =
            makeGame(
                PressurePlateObjectIR(
                    id = "plate",
                    x = 7,
                    y = 4,
                    respondToActorIds = listOf("player"),
                )
            )
        val mainC = pipeline.generate(game).files["main.c"] ?: error("main.c not generated")

        // Should reference the actor's position variables
        assertTrue(
            mainC.contains("_player_x"),
            "check function should reference _player_x for position test",
        )
        assertTrue(
            mainC.contains("_player_y"),
            "check function should reference _player_y for position test",
        )
    }

    @Test
    fun `pressure plate is added to puzzle_update_all per-frame calls`() {
        val game =
            makeGame(
                PressurePlateObjectIR(
                    id = "plate",
                    x = 7,
                    y = 4,
                    respondToActorIds = listOf("player"),
                )
            )
        val mainC = pipeline.generate(game).files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("puzzle_update_all"),
            "main.c should contain puzzle_update_all function",
        )
    }

    // =========================================================================
    // Timed block — timer variable + update function with tile swap
    // =========================================================================

    @Test
    fun `timed block generates timer state variable`() {
        val game =
            makeGame(
                TimedBlockObjectIR(
                    id = "timerBlock",
                    x = 12,
                    y = 6,
                    solidTile = 0x15,
                    emptyTile = 0x00,
                    interval = 60,
                )
            )
        val mainC = pipeline.generate(game).files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_timedblock_timerBlock_timer"),
            "main.c should contain _timedblock_timerBlock_timer variable",
        )
    }

    @Test
    fun `timed block generates solid state variable`() {
        val game =
            makeGame(
                TimedBlockObjectIR(
                    id = "timerBlock",
                    x = 12,
                    y = 6,
                    solidTile = 0x15,
                    emptyTile = 0x00,
                    interval = 60,
                )
            )
        val mainC = pipeline.generate(game).files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_timedblock_timerBlock_solid"),
            "main.c should contain _timedblock_timerBlock_solid state variable",
        )
    }

    @Test
    fun `timed block generates puzzle_update_timedblock function`() {
        val game =
            makeGame(
                TimedBlockObjectIR(
                    id = "timerBlock",
                    x = 12,
                    y = 6,
                    solidTile = 0x15,
                    emptyTile = 0x00,
                    interval = 60,
                )
            )
        val mainC = pipeline.generate(game).files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("puzzle_update_timedblock_timerBlock"),
            "main.c should contain puzzle_update_timedblock_timerBlock function",
        )
    }

    @Test
    fun `timed block update function uses set_bkg_tile_xy for tile swap`() {
        val game =
            makeGame(
                TimedBlockObjectIR(
                    id = "timerBlock",
                    x = 12,
                    y = 6,
                    solidTile = 0x15,
                    emptyTile = 0x00,
                    interval = 60,
                )
            )
        val mainC = pipeline.generate(game).files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("set_bkg_tile_xy"),
            "timed block update function should call set_bkg_tile_xy for tile swap",
        )
        // interval=60 should appear in timer comparison
        assertTrue(mainC.contains("60"), "interval value 60 should appear in timer comparison")
    }

    @Test
    fun `timed block is added to puzzle_update_all per-frame calls`() {
        val game =
            makeGame(
                TimedBlockObjectIR(
                    id = "timerBlock",
                    x = 12,
                    y = 6,
                    solidTile = 0x15,
                    emptyTile = 0x00,
                    interval = 60,
                )
            )
        val mainC = pipeline.generate(game).files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("puzzle_update_all"),
            "main.c should contain puzzle_update_all per-frame dispatcher",
        )
    }

    // =========================================================================
    // puzzle_update_all — called in main game loop
    // =========================================================================

    @Test
    fun `puzzle_update_all is called in the main game loop when puzzle objects exist`() {
        val game = makeGame(SwitchObjectIR(id = "sw", x = 0, y = 0))
        val mainC = pipeline.generate(game).files["main.c"] ?: error("main.c not generated")

        // puzzle_update_all should be called — the main function calls it each frame
        assertTrue(
            mainC.contains("puzzle_update_all"),
            "puzzle_update_all should be called in the main game loop",
        )
    }

    @Test
    fun `puzzle_update_all is NOT emitted when no puzzle objects exist`() {
        val game =
            GameIR(
                name = "NoPuzzles",
                config = CartridgeConfig(),
                scenes = listOf(SceneIR(id = "main")),
                puzzleObjects = emptyList(),
            )
        val mainC = pipeline.generate(game).files["main.c"] ?: error("main.c not generated")

        assertFalse(
            mainC.contains("puzzle_update_all"),
            "puzzle_update_all should NOT appear when there are no puzzle objects",
        )
    }

    // =========================================================================
    // ScriptOpVisitor: ActivatePuzzleObject → puzzle_activate_{id}()
    // =========================================================================

    @Test
    fun `ActivatePuzzleObject ScriptOp emits puzzle_activate call in codegen`() {
        // Simulate the op being emitted in a scene's frame ops
        val scene =
            SceneIR(id = "dungeon", frameOps = listOf(ActivatePuzzleObject(objectId = "sw1")))
        val game =
            GameIR(
                name = "TestGame",
                config = CartridgeConfig(),
                scenes = listOf(scene),
                puzzleObjects = listOf(SwitchObjectIR(id = "sw1", x = 0, y = 0)),
            )
        val bank1C = pipeline.generate(game).files["bank1.c"] ?: error("bank1.c not generated")

        assertTrue(
            bank1C.contains("puzzle_activate_sw1"),
            "ActivatePuzzleObject should emit puzzle_activate_sw1() call in scene codegen",
        )
    }

    @Test
    fun `DeactivatePuzzleObject ScriptOp emits puzzle_deactivate call in codegen`() {
        val scene =
            SceneIR(
                id = "dungeon",
                frameOps = listOf(DeactivatePuzzleObject(objectId = "bossDoor")),
            )
        val game =
            GameIR(
                name = "TestGame",
                config = CartridgeConfig(),
                scenes = listOf(scene),
                puzzleObjects = listOf(DoorObjectIR(id = "bossDoor", x = 10, y = 5)),
            )
        val bank1C = pipeline.generate(game).files["bank1.c"] ?: error("bank1.c not generated")

        assertTrue(
            bank1C.contains("puzzle_deactivate_bossDoor"),
            "DeactivatePuzzleObject should emit puzzle_deactivate_bossDoor() call",
        )
    }

    @Test
    fun `RevealPuzzleObject ScriptOp emits puzzle_reveal call in codegen`() {
        val scene =
            SceneIR(id = "main", frameOps = listOf(RevealPuzzleObject(objectId = "hiddenSw")))
        val game =
            GameIR(
                name = "TestGame",
                config = CartridgeConfig(),
                scenes = listOf(scene),
                puzzleObjects = listOf(SwitchObjectIR(id = "hiddenSw", x = 0, y = 0, hidden = true)),
            )
        val bank1C = pipeline.generate(game).files["bank1.c"] ?: error("bank1.c not generated")

        assertTrue(
            bank1C.contains("puzzle_reveal_hiddenSw"),
            "RevealPuzzleObject should emit puzzle_reveal_hiddenSw() call",
        )
    }

    @Test
    fun `HidePuzzleObject ScriptOp emits puzzle_hide call in codegen`() {
        val scene = SceneIR(id = "main", frameOps = listOf(HidePuzzleObject(objectId = "sw1")))
        val game =
            GameIR(
                name = "TestGame",
                config = CartridgeConfig(),
                scenes = listOf(scene),
                puzzleObjects = listOf(SwitchObjectIR(id = "sw1", x = 0, y = 0)),
            )
        val bank1C = pipeline.generate(game).files["bank1.c"] ?: error("bank1.c not generated")

        assertTrue(
            bank1C.contains("puzzle_hide_sw1"),
            "HidePuzzleObject should emit puzzle_hide_sw1() call",
        )
    }
}
