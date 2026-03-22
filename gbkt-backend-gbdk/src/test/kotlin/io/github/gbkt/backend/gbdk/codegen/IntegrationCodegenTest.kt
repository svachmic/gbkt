/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipelineV2
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.AnimationStateDef
import io.github.gbkt.core.ir.CameraSystem
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.ExplorationSystem
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.MovementConfig
import io.github.gbkt.core.ir.MovementStyle
import io.github.gbkt.core.ir.PathfindStep
import io.github.gbkt.core.ir.PathfindingSystem
import io.github.gbkt.core.ir.PhysicsConfig
import io.github.gbkt.core.ir.PhysicsStep
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.SaveSystem
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.VarType
import io.github.gbkt.core.ir.VariableDef
import io.github.gbkt.core.ir.WaypointRoute
import io.github.gbkt.core.ir.WaypointStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// =============================================================================
// INTEGRATION CODEGEN TEST (Phase 06.1-06 success criterion)
// Exercises ALL Phase 06.1 features simultaneously:
//   - Camera with follow + bounds
//   - Actor with grid movement
//   - Actor with physics (velocity, gravity)
//   - Actor with animation state machine (idle, walk)
//   - Save system with 3 slots and checksum
//   - Pathfinding system (16x16 grid)
//   - Actor with waypoint route
//   - Scene that drives physics and pathfinding per frame
//
// Key assertions:
// 1. Output contains expected function names (camera, movement, animation, save, pathfinding)
// 2. Brace balance check: count { and } in all generated C — must be equal
// 3. No hashCode() artifacts in generated output
// 4. no regressions — generation completes without exceptions
// =============================================================================

/**
 * Build a GameIR using ALL Phase 06.1 features simultaneously for integration testing.
 *
 * The fixture exercises the composition of all Phase 06.1 feature systems through the pipeline.
 */
private fun buildFullFeatureGameIR(): GameIR {
    // Player actor — grid movement + animation (idle, walk) + physics
    val player =
        ActorIR(
            id = "hero",
            position = PositionDef(80, 72),
            movementConfig = MovementConfig(style = MovementStyle.GRID, speed = 8, tileSize = 8),
            animationStates =
                listOf(
                    AnimationStateDef(name = "idle", startFrame = 0, endFrame = 0, speed = 8),
                    AnimationStateDef(name = "walk", startFrame = 1, endFrame = 3, speed = 4),
                ),
            physicsConfig =
                PhysicsConfig(
                    velocityX = 0,
                    velocityY = 0,
                    gravity = 1,
                    maxFallSpeed = 6,
                    bounce = 0,
                ),
        )

    // Enemy NPC — waypoint patrol route
    val enemy =
        ActorIR(
            id = "guard",
            position = PositionDef(40, 40),
            waypointRoute =
                WaypointRoute(
                    points = listOf(Pair(40, 40), Pair(120, 40), Pair(120, 80), Pair(40, 80)),
                    loop = true,
                ),
        )

    // Scene with physics and pathfinding updates per frame
    val gameScene =
        SceneIR(
            id = "game",
            frameOps =
                listOf(
                    PhysicsStep(actorId = "hero"),
                    PathfindStep(npcActorId = "guard", targetActorId = "hero"),
                    WaypointStep(npcActorId = "guard"),
                ),
        )

    // Systems: camera (follows hero), exploration, save (3 slots + checksum), pathfinding
    return GameIR(
        name = "IntegrationTest",
        config = CartridgeConfig(cartridge = "ROM_ONLY", romBanks = 2),
        scenes = listOf(gameScene),
        actors = listOf(player, enemy),
        variables =
            listOf(
                VariableDef("score", VarType.U8, 0),
                VariableDef("level", VarType.U8, 1),
                VariableDef("lives", VarType.U8, 3),
            ),
        systems =
            listOf(
                CameraSystem(
                    id = "cam",
                    followActorId = "hero",
                    boundsWidth = 256,
                    boundsHeight = 256,
                ),
                ExplorationSystem(id = "explore"),
                SaveSystem(
                    id = "save",
                    slots = 3,
                    useChecksum = true,
                    transientVarNames = emptySet(),
                ),
                PathfindingSystem(
                    id = "pathfinding",
                    gridSize = 8,
                    mapWidth = 16,
                    mapHeight = 16,
                    maxOpenNodes = 24,
                    maxPathLength = 24,
                ),
            ),
        startScene = "game",
    )
}

class IntegrationCodegenTest {

    private val pipeline = GBDKPipelineV2()

    // =========================================================================
    // Test 1: Full feature pipeline generates output without exceptions
    // =========================================================================

    @Test
    fun `full feature pipeline generates output without exceptions`() {
        val gameIR = buildFullFeatureGameIR()

        // Must not throw
        val output = pipeline.generate(gameIR)

        assertTrue(output.files.isNotEmpty(), "Pipeline should generate at least one file")
        assertTrue(output.files.containsKey("main.c"), "main.c must be generated")
        assertTrue(
            output.files.containsKey("bank1.c"),
            "bank1.c must be generated (scenes in bank 1)",
        )
        assertTrue(output.files.containsKey("game.h"), "game.h must be generated")
    }

    // =========================================================================
    // Test 2: Brace balance — every { has a matching }
    // =========================================================================

    @Test
    fun `all generated C files have balanced braces`() {
        val gameIR = buildFullFeatureGameIR()
        val output = pipeline.generate(gameIR)

        for ((fileName, content) in output.files) {
            val openCount = content.count { it == '{' }
            val closeCount = content.count { it == '}' }
            assertEquals(
                openCount,
                closeCount,
                "$fileName has unbalanced braces: $openCount '{' vs $closeCount '}'",
            )
        }
    }

    // =========================================================================
    // Test 3: Camera system generates update_camera function
    // =========================================================================

    @Test
    fun `full feature pipeline generates camera update function`() {
        val gameIR = buildFullFeatureGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("update_camera"),
            "Camera system should generate update_camera function",
        )
    }

    // =========================================================================
    // Test 4: Physics generates velocity variables
    // =========================================================================

    @Test
    fun `full feature pipeline generates physics velocity variables for hero`() {
        val gameIR = buildFullFeatureGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("_hero_vx"), "Physics should generate _hero_vx velocity variable")
        assertTrue(mainC.contains("_hero_vy"), "Physics should generate _hero_vy velocity variable")
    }

    // =========================================================================
    // Test 5: Animation generates state machine for hero
    // =========================================================================

    @Test
    fun `full feature pipeline generates animation state machine for hero`() {
        val gameIR = buildFullFeatureGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("update_animation_hero"),
            "Animation should generate update_animation_hero state machine",
        )
    }

    // =========================================================================
    // Test 6: Save system generates save_game and load_game
    // =========================================================================

    @Test
    fun `full feature pipeline generates save and load functions`() {
        val gameIR = buildFullFeatureGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(mainC.contains("save_game"), "SaveSystem should generate save_game function")
        assertTrue(mainC.contains("load_game"), "SaveSystem should generate load_game function")
    }

    // =========================================================================
    // Test 7: Pathfinding generates A* infrastructure
    // =========================================================================

    @Test
    fun `full feature pipeline generates A star pathfinding infrastructure`() {
        val gameIR = buildFullFeatureGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("pf_find_path"),
            "Pathfinding should generate pf_find_path function",
        )
        assertTrue(
            mainC.contains("pf_step_toward"),
            "Pathfinding should generate pf_step_toward function",
        )
    }

    // =========================================================================
    // Test 8: Waypoint patrol generates waypoint step function
    // =========================================================================

    @Test
    fun `full feature pipeline generates waypoint step for guard NPC`() {
        val gameIR = buildFullFeatureGameIR()
        val output = pipeline.generate(gameIR)
        val bank1C = output.files["bank1.c"] ?: error("bank1.c not generated")

        assertTrue(
            bank1C.contains("waypoint") || bank1C.contains("_guard_"),
            "Waypoint route should generate guard NPC movement code",
        )
    }

    // =========================================================================
    // Test 9: No hashCode artifacts in any output
    // =========================================================================

    @Test
    fun `full feature pipeline generates no hashCode artifacts`() {
        val gameIR = buildFullFeatureGameIR()
        val output = pipeline.generate(gameIR)

        for ((fileName, content) in output.files) {
            assertFalse(
                content.contains("hashCode()"),
                "$fileName contains hashCode() — old stub pattern leaked into generated C",
            )
        }
    }

    // =========================================================================
    // Test 10: Exploration system generates exploration_move function
    // =========================================================================

    @Test
    fun `full feature pipeline generates exploration_move for exploration system`() {
        val gameIR = buildFullFeatureGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("exploration_move"),
            "ExplorationSystem should generate exploration_move function",
        )
    }
}
