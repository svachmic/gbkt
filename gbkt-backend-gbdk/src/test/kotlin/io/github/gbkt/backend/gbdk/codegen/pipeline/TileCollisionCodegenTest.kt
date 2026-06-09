/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen.pipeline

import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.ExplorationSystem
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.SceneIR
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// TILE COLLISION CODEGEN TESTS
// Verifies G1+G2+G3: SceneIR.collisionData → collision array, lookup functions,
// dispatch function, and exploration movement wiring.
// =============================================================================

class TileCollisionCodegenTest {

    private val pipeline = GBDKPipeline()

    // =========================================================================
    // G1 — SceneIR collisionData field
    // =========================================================================

    @Test
    fun `SceneIR accepts collisionData and mapWidth`() {
        // A 2x2 grid: top row passable, bottom row wall
        val data = byteArrayOf(0, 0, 1, 1)
        val scene = SceneIR(id = "dungeon", collisionData = data, mapWidth = 2)

        assertTrue(scene.collisionData != null, "SceneIR should store collisionData")
        assertTrue(scene.mapWidth != null, "SceneIR should store mapWidth")
    }

    @Test
    fun `SceneIR without collisionData has null fields`() {
        val scene = SceneIR(id = "menu")

        assertTrue(
            scene.collisionData == null,
            "SceneIR without data should have null collisionData",
        )
        assertTrue(scene.mapWidth == null, "SceneIR without data should have null mapWidth")
    }

    // =========================================================================
    // G2 — Collision array codegen
    // =========================================================================

    @Test
    fun `scene with collisionData generates map_collision array in main dot c`() {
        // 4 tiles: passable, wall, passable, wall (2x2 map)
        val collisionData = byteArrayOf(0, 1, 0, 1)
        val scene = SceneIR(id = "dungeon", collisionData = collisionData, mapWidth = 2)
        val game = GameIR(name = "TestGame", config = CartridgeConfig(), scenes = listOf(scene))

        val output = pipeline.generate(game)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Should emit the collision byte array
        assertTrue(
            mainC.contains("map_dungeon_collision"),
            "main.c should contain map_dungeon_collision array declaration",
        )
        // Should contain the raw byte values
        assertTrue(
            mainC.contains("0, 1, 0, 1"),
            "main.c should contain collision byte values { 0, 1, 0, 1 }",
        )
    }

    @Test
    fun `scene with collisionData generates per-scene lookup function`() {
        val collisionData = byteArrayOf(0, 0, 1, 1)
        val scene = SceneIR(id = "maze", collisionData = collisionData, mapWidth = 2)
        val game = GameIR(name = "TestGame", config = CartridgeConfig(), scenes = listOf(scene))

        val output = pipeline.generate(game)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Should generate _map_collision_maze(UINT8 x, UINT8 y) lookup function
        assertTrue(
            mainC.contains("_map_collision_maze"),
            "main.c should contain _map_collision_maze lookup function",
        )
        // Must use UINT16 cast to avoid 8-bit overflow on wide maps
        assertTrue(
            mainC.contains("(UINT16)"),
            "Collision lookup function must cast to UINT16 to prevent overflow",
        )
        // Should reference map_maze_collision array
        assertTrue(
            mainC.contains("map_maze_collision"),
            "Lookup function should reference the collision data array",
        )
    }

    @Test
    fun `scene with collisionData generates dispatch function switching on current_scene`() {
        val collisionData = byteArrayOf(0, 1, 0, 0)
        val scene = SceneIR(id = "dungeon", collisionData = collisionData, mapWidth = 2)
        val game = GameIR(name = "TestGame", config = CartridgeConfig(), scenes = listOf(scene))

        val output = pipeline.generate(game)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Should generate the dispatch function
        assertTrue(
            mainC.contains("_map_collision"),
            "main.c should contain _map_collision dispatch function",
        )
        // Must switch on current_scene
        assertTrue(
            mainC.contains("current_scene"),
            "Dispatch function must switch on current_scene",
        )
        // Must dispatch to per-scene function
        assertTrue(
            mainC.contains("_map_collision_dungeon"),
            "Dispatch function must call _map_collision_dungeon for SCENE_DUNGEON",
        )
    }

    @Test
    fun `dispatch function has default case returning 0 for passable`() {
        val collisionData = byteArrayOf(1, 1, 1, 1)
        val scene = SceneIR(id = "level1", collisionData = collisionData, mapWidth = 2)
        val game = GameIR(name = "TestGame", config = CartridgeConfig(), scenes = listOf(scene))

        val output = pipeline.generate(game)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Default case must return 0 (always passable for unknown scenes)
        assertTrue(
            mainC.contains("return 0;"),
            "Dispatch function default case must return 0 (always passable)",
        )
    }

    @Test
    fun `multiple scenes each generate their own collision lookup function`() {
        val dungeonData = byteArrayOf(0, 1, 0, 0)
        val caveData = byteArrayOf(1, 0, 1, 0)
        val dungeon = SceneIR(id = "dungeon", collisionData = dungeonData, mapWidth = 2)
        val cave = SceneIR(id = "cave", collisionData = caveData, mapWidth = 2)
        val game =
            GameIR(name = "TestGame", config = CartridgeConfig(), scenes = listOf(dungeon, cave))

        val output = pipeline.generate(game)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("map_dungeon_collision"),
            "Should generate dungeon collision array",
        )
        assertTrue(mainC.contains("map_cave_collision"), "Should generate cave collision array")
        assertTrue(
            mainC.contains("_map_collision_dungeon"),
            "Should generate _map_collision_dungeon lookup function",
        )
        assertTrue(
            mainC.contains("_map_collision_cave"),
            "Should generate _map_collision_cave lookup function",
        )
    }

    // =========================================================================
    // G2 — Collision prototypes in game.h
    // =========================================================================

    @Test
    fun `scene with collisionData generates collision prototype in game dot h`() {
        val collisionData = byteArrayOf(0, 0, 0, 1)
        val scene = SceneIR(id = "floor1", collisionData = collisionData, mapWidth = 2)
        val game = GameIR(name = "TestGame", config = CartridgeConfig(), scenes = listOf(scene))

        val output = pipeline.generate(game)
        val gameH = output.files["game.h"] ?: error("game.h not generated")

        // Per-scene prototype
        assertTrue(
            gameH.contains("_map_collision_floor1"),
            "game.h should declare _map_collision_floor1 prototype",
        )
        // Dispatch prototype
        assertTrue(
            gameH.contains("_map_collision"),
            "game.h should declare _map_collision dispatch prototype",
        )
    }

    // =========================================================================
    // G2 — No collision code for scenes without data
    // =========================================================================

    @Test
    fun `scene without collisionData does not generate any collision code`() {
        val scene = SceneIR(id = "menu")
        val game = GameIR(name = "TestGame", config = CartridgeConfig(), scenes = listOf(scene))

        val output = pipeline.generate(game)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertFalse(
            mainC.contains("map_menu_collision"),
            "Scene without collision data should not generate map_menu_collision",
        )
        assertFalse(
            mainC.contains("_map_collision_menu"),
            "Scene without collision data should not generate _map_collision_menu",
        )
    }

    @Test
    fun `game with no collision scenes does not generate dispatch function`() {
        val scene1 = SceneIR(id = "menu")
        val scene2 = SceneIR(id = "title")
        val game =
            GameIR(name = "TestGame", config = CartridgeConfig(), scenes = listOf(scene1, scene2))

        val output = pipeline.generate(game)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // No dispatch function should be generated when no scenes have collision data
        // The string "_map_collision" should not appear in a game with zero collision scenes
        assertFalse(
            mainC.contains("UINT8 _map_collision("),
            "Game with no collision scenes should not emit _map_collision dispatch function",
        )
    }

    // =========================================================================
    // G3 — Exploration movement respects tile collision
    // =========================================================================

    @Test
    fun `exploration movement function checks tile collision before updating position`() {
        val collisionData = byteArrayOf(0, 1, 0, 0)
        val dungeonScene = SceneIR(id = "dungeon", collisionData = collisionData, mapWidth = 2)
        val game =
            GameIR(
                name = "TestGame",
                config = CartridgeConfig(),
                scenes = listOf(dungeonScene),
                systems = listOf(ExplorationSystem(id = "dungeon_explore")),
            )

        val output = pipeline.generate(game)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // exploration_move must call _map_collision before updating player position
        assertTrue(
            mainC.contains("_map_collision"),
            "exploration_move should call _map_collision for tile passability check",
        )
        // The check must be a guard: if tile is blocked, return early
        assertTrue(
            mainC.contains("if (_map_collision(nx, ny))"),
            "exploration_move should guard movement with if (_map_collision(nx, ny))",
        )
        // Player position must still be updated (movement is allowed on passable tiles)
        assertTrue(
            mainC.contains("_player_x = nx"),
            "exploration_move should still update _player_x for passable tiles",
        )
    }

    @Test
    fun `exploration movement tile collision check is ordered before position update`() {
        val collisionData = byteArrayOf(0, 0, 0, 0)
        val scene = SceneIR(id = "level", collisionData = collisionData, mapWidth = 2)
        val game =
            GameIR(
                name = "TestGame",
                config = CartridgeConfig(),
                scenes = listOf(scene),
                systems = listOf(ExplorationSystem(id = "explore")),
            )

        val output = pipeline.generate(game)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // Collision check must appear before position update in the generated C
        val collisionCheckIndex = mainC.indexOf("_map_collision(nx, ny)")
        val positionUpdateIndex = mainC.indexOf("_player_x = nx")

        assertTrue(collisionCheckIndex >= 0, "Collision check must appear in generated C")
        assertTrue(positionUpdateIndex >= 0, "Position update must appear in generated C")
        assertTrue(
            collisionCheckIndex < positionUpdateIndex,
            "Collision check must come before position update in exploration_move body",
        )
    }
}
