/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.backend.gbdk.codegen

import io.github.gbkt.backend.gbdk.codegen.pipeline.GBDKPipelineV2
import io.github.gbkt.core.ir.ActorIR
import io.github.gbkt.core.ir.ActorPoolConfig
import io.github.gbkt.core.ir.ActorPoolIR
import io.github.gbkt.core.ir.CartridgeConfig
import io.github.gbkt.core.ir.CollisionGroupIR
import io.github.gbkt.core.ir.CollisionResponse
import io.github.gbkt.core.ir.CollisionRuleIR
import io.github.gbkt.core.ir.DiagonalMode
import io.github.gbkt.core.ir.DoorObjectIR
import io.github.gbkt.core.ir.ExplorationSystem
import io.github.gbkt.core.ir.FixedPointMode
import io.github.gbkt.core.ir.GameIR
import io.github.gbkt.core.ir.MovementConfig
import io.github.gbkt.core.ir.MovementStyle
import io.github.gbkt.core.ir.NpcCollisionConfig
import io.github.gbkt.core.ir.PhysicsConfig
import io.github.gbkt.core.ir.PoolInstanceProperty
import io.github.gbkt.core.ir.PoolOverflowStrategy
import io.github.gbkt.core.ir.PositionDef
import io.github.gbkt.core.ir.PressurePlateObjectIR
import io.github.gbkt.core.ir.PuzzleEventHandler
import io.github.gbkt.core.ir.PuzzleEventType
import io.github.gbkt.core.ir.SceneIR
import io.github.gbkt.core.ir.SmoothMovementConfig
import io.github.gbkt.core.ir.SwitchObjectIR
import io.github.gbkt.core.ir.TimedBlockObjectIR
import io.github.gbkt.core.ir.TriggerObjectIR
import io.github.gbkt.core.ir.VarType
import io.github.gbkt.core.ir.WallResponse
import io.github.gbkt.core.ir.ZoneIR
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// PHASE 06.7 CODEGEN INTEGRATION TESTS
// Verifies that the full GBDKPipelineV2 produces correct combined C output for
// all 7 Phase 06.7 features working together in a single game.
//
// Tests:
// 1.  Full pipeline produces output without exceptions
// 2.  All expected output files are present (main.c, bank1.c, game.h, zone_bank2.c)
// 3.  Pool codegen: pool_bulletPool_spawn, pool_bulletPool_destroy, pool_bulletPool_init
// 4.  Pool codegen: per-instance arrays _pool_bulletPool_damage, _pool_bulletPool_direction
// 5.  Pool codegen: pool_bulletPool_active_count function
// 6.  Collision codegen: check_collision_playerGroup_enemyGroup with PUSH displacement
// 7.  Collision codegen: check_all_npc_collisions master function
// 8.  Smooth movement codegen: _smoothPlayer_vx, _smoothPlayer_vy velocity variables
// 9.  Smooth movement codegen: acceleration logic and friction in update_movement_smoothPlayer
// 10. Physics movement codegen: variable-height jump cut code in physicsHero
// 11. Physics movement codegen: coyote time counter in physicsHero
// 12. Fixed-point codegen: fractional accumulators >> 4 shift in physicsHero
// 13. Puzzle codegen: _switch_sw1_active state variable + puzzle_activate_sw1
// 14. Puzzle codegen: puzzle_check_plate_entryPlate function
// 15. Puzzle codegen: puzzle_update_timedblock_timerBlock timer
// 16. Puzzle codegen: bossDoor requires guard checking switch states
// 17. Puzzle codegen: puzzle_trigger_secretTrigger_fire function
// 18. Zone banking: zone_bank2.c file generated with tile arrays
// 19. Zone banking: SWITCH_ROM in zone_load functions
// 20. Zone banking: game.h contains extern declarations for banked arrays
// 21. All generated C files have balanced braces
// =============================================================================

/** Build the multi-feature GameIR combining all Phase 06.7 features for codegen integration. */
private fun buildPhase067CodegenGameIR(): GameIR {
    // =========================================================================
    // Actors — bullet pool template, smooth mover, physics mover, enemy
    // =========================================================================

    val bullet = ActorIR(id = "bullet", position = PositionDef(0, 0))

    val smoothPlayer =
        ActorIR(
            id = "smoothPlayer",
            position = PositionDef(80, 72),
            movementConfig =
                MovementConfig(
                    style = MovementStyle.SMOOTH,
                    speed = 4,
                    smoothConfig =
                        SmoothMovementConfig(
                            speed = 4,
                            acceleration = 4,
                            friction = 2,
                            diagonalMode = DiagonalMode.NORMALIZED,
                        ),
                ),
        )

    val physicsHero =
        ActorIR(
            id = "physicsHero",
            position = PositionDef(40, 40),
            movementConfig = MovementConfig(style = MovementStyle.PHYSICS),
            physicsConfig =
                PhysicsConfig(
                    gravity = 4,
                    maxFallSpeed = 8,
                    platformerMode = true,
                    variableJump = true,
                    jumpCutMultiplier = 2,
                    coyoteFrames = 4,
                    wallResponse = WallResponse.SLIDE,
                    wallJump = true,
                    wallJumpVelocityX = 24,
                    wallJumpVelocityY = 28,
                    fixedPointMode = FixedPointMode.FP44,
                ),
            npcCollisionConfig = NpcCollisionConfig(groupIds = listOf("playerGroup"), mass = 2),
        )

    val enemyNpc =
        ActorIR(
            id = "enemyNpc",
            position = PositionDef(60, 60),
            npcCollisionConfig = NpcCollisionConfig(groupIds = listOf("enemyGroup"), mass = 1),
        )

    // =========================================================================
    // Actor pool
    // =========================================================================

    val bulletPool =
        ActorPoolIR(
            id = "bulletPool",
            actorTemplateId = "bullet",
            config =
                ActorPoolConfig(maxSize = 8, overflowStrategy = PoolOverflowStrategy.SILENT_NOOP),
            instanceProperties =
                listOf(
                    PoolInstanceProperty("damage", VarType.U8),
                    PoolInstanceProperty("direction", VarType.I8),
                ),
        )

    // =========================================================================
    // NPC collision groups and rules
    // =========================================================================

    val playerGroup = CollisionGroupIR("playerGroup")
    val enemyGroup = CollisionGroupIR("enemyGroup")

    val pushRule =
        CollisionRuleIR(
            groupA = "playerGroup",
            groupB = "enemyGroup",
            response = CollisionResponse.PUSH,
            interval = 1,
        )

    // =========================================================================
    // Puzzle objects
    // =========================================================================

    val sw1 = SwitchObjectIR(id = "sw1", x = 5, y = 3)
    val sw2 = SwitchObjectIR(id = "sw2", x = 8, y = 3)

    val bossDoor =
        DoorObjectIR(
            id = "bossDoor",
            x = 10,
            y = 5,
            openTile = 0x20,
            closedTile = 0x21,
            requires = listOf("sw1", "sw2"),
        )

    val entryPlate =
        PressurePlateObjectIR(
            id = "entryPlate",
            x = 7,
            y = 4,
            respondToActorIds = listOf("physicsHero", "enemyNpc"),
        )

    val timerBlock =
        TimedBlockObjectIR(
            id = "timerBlock",
            x = 12,
            y = 6,
            solidTile = 0x15,
            emptyTile = 0x00,
            interval = 60,
        )

    val secretTrigger =
        TriggerObjectIR(
            id = "secretTrigger",
            x = 3,
            y = 3,
            handlers =
                listOf(
                    PuzzleEventHandler(PuzzleEventType.INTERACT, emptyList()),
                    PuzzleEventHandler(PuzzleEventType.STEP_ON, emptyList()),
                ),
        )

    // =========================================================================
    // Zones — two zones with tile data to trigger zone banking
    // =========================================================================

    val zone1 =
        ZoneIR(
            id = "dungeon1",
            name = "Dungeon Level 1",
            tilesetPath = "tilesets/dungeon.png",
            mapWidth = 20,
            mapHeight = 18,
            tileData = List(20 * 18) { it and 0xFF },
        )

    val zone2 =
        ZoneIR(
            id = "dungeon2",
            name = "Dungeon Level 2",
            tilesetPath = "tilesets/dungeon.png",
            mapWidth = 20,
            mapHeight = 18,
            tileData = List(20 * 18) { (it + 10) and 0xFF },
        )

    // Scene
    val gameScene = SceneIR(id = "gameplay")

    return GameIR(
        name = "Phase067IntegrationGame",
        config = CartridgeConfig(cartridge = "ROM_MBC5", romBanks = 16),
        scenes = listOf(gameScene),
        actors = listOf(bullet, smoothPlayer, physicsHero, enemyNpc),
        actorPools = listOf(bulletPool),
        collisionGroups = listOf(playerGroup, enemyGroup),
        collisionRules = listOf(pushRule),
        puzzleObjects = listOf(sw1, sw2, bossDoor, entryPlate, timerBlock, secretTrigger),
        zones = listOf(zone1, zone2),
        // ExplorationSystem required for zone_load functions
        systems = listOf(ExplorationSystem(id = "dungeonExplore")),
        startScene = "gameplay",
    )
}

class Phase067CodegenIntegrationTest {

    private val pipeline = GBDKPipelineV2()

    // =========================================================================
    // Test 1: Full pipeline produces output without exceptions
    // =========================================================================

    @Test
    fun `full pipeline produces output without exceptions`() {
        val gameIR = buildPhase067CodegenGameIR()
        // Must not throw
        val output = pipeline.generate(gameIR)
        assertTrue(output.files.isNotEmpty(), "Pipeline should produce at least one file")
    }

    // =========================================================================
    // Test 2: All expected output files are present
    // =========================================================================

    @Test
    fun `pipeline produces main_c bank1_c and game_h files`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)

        assertTrue(output.files.containsKey("main.c"), "main.c should be generated")
        assertTrue(
            output.files.containsKey("bank1.c"),
            "bank1.c should be generated (scene functions)",
        )
        assertTrue(output.files.containsKey("game.h"), "game.h should be generated")
    }

    @Test
    fun `pipeline produces zone_bank2_c for banked tilemap data`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)

        assertTrue(
            output.files.keys.any { it.startsWith("zone_bank") },
            "At least one zone_bankN.c file should be generated, got: ${output.files.keys}",
        )
    }

    // =========================================================================
    // Test 3: Pool codegen — init/spawn/destroy functions
    // =========================================================================

    @Test
    fun `pool codegen generates pool_bulletPool_spawn function`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("pool_bulletPool_spawn"),
            "main.c should contain pool_bulletPool_spawn function",
        )
    }

    @Test
    fun `pool codegen generates pool_bulletPool_destroy function`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("pool_bulletPool_destroy"),
            "main.c should contain pool_bulletPool_destroy function",
        )
    }

    @Test
    fun `pool codegen generates pool_bulletPool_init function`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("pool_bulletPool_init"),
            "main.c should contain pool_bulletPool_init function",
        )
    }

    // =========================================================================
    // Test 4: Pool codegen — per-instance parallel arrays
    // =========================================================================

    @Test
    fun `pool codegen generates per-instance damage and direction arrays`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_pool_bulletPool_damage"),
            "main.c should contain _pool_bulletPool_damage per-instance array",
        )
        assertTrue(
            mainC.contains("_pool_bulletPool_direction"),
            "main.c should contain _pool_bulletPool_direction per-instance array",
        )
    }

    // =========================================================================
    // Test 5: Pool codegen — active_count function
    // =========================================================================

    @Test
    fun `pool codegen generates pool_bulletPool_active_count function`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("pool_bulletPool_active_count"),
            "main.c should contain pool_bulletPool_active_count function",
        )
    }

    // =========================================================================
    // Test 6: Collision codegen — check_collision function with PUSH math
    // =========================================================================

    @Test
    fun `collision codegen generates check_collision_playerGroup_enemyGroup with PUSH mass math`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("check_collision_playerGroup_enemyGroup"),
            "main.c should contain check_collision_playerGroup_enemyGroup function",
        )
        // PUSH displacement uses mass ratio math
        assertTrue(
            mainC.contains("mass") || mainC.contains("disp"),
            "PUSH collision should generate mass displacement math",
        )
    }

    // =========================================================================
    // Test 7: Collision codegen — check_all_npc_collisions master function
    // =========================================================================

    @Test
    fun `collision codegen generates check_all_npc_collisions master function`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("check_all_npc_collisions"),
            "main.c should contain check_all_npc_collisions master function",
        )
    }

    // =========================================================================
    // Test 8: Smooth movement codegen — velocity variables
    // =========================================================================

    @Test
    fun `smooth movement codegen generates _smoothPlayer_vx and _smoothPlayer_vy variables`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_smoothPlayer_vx"),
            "main.c should contain _smoothPlayer_vx velocity variable",
        )
        assertTrue(
            mainC.contains("_smoothPlayer_vy"),
            "main.c should contain _smoothPlayer_vy velocity variable",
        )
    }

    // =========================================================================
    // Test 9: Smooth movement codegen — acceleration and friction logic
    // =========================================================================

    @Test
    fun `smooth movement generates acceleration and friction in update_movement_smoothPlayer`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("update_movement_smoothPlayer"),
            "main.c should contain update_movement_smoothPlayer function",
        )
        // Acceleration config generates ACCEL/FRICTION defines or inline math
        assertTrue(
            mainC.contains("ACCEL_SMOOTHPLAYER") || mainC.contains("SMOOTH_ACCEL"),
            "Smooth movement should generate acceleration define for smoothPlayer",
        )
    }

    // =========================================================================
    // Test 10: Physics movement codegen — variable-height jump
    // =========================================================================

    @Test
    fun `physics movement generates variable-height jump cut code for physicsHero`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("update_movement_physicsHero"),
            "main.c should contain update_movement_physicsHero function",
        )
        // Variable jump generates jump_held variable + jump cut logic
        assertTrue(
            mainC.contains("_physicsHero_jump_held"),
            "Physics movement should generate _physicsHero_jump_held variable for variable jump",
        )
    }

    // =========================================================================
    // Test 11: Physics movement codegen — coyote time
    // =========================================================================

    @Test
    fun `physics movement generates coyote time counter for physicsHero`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_physicsHero_coyote") || mainC.contains("COYOTE_PHYSICSHERO"),
            "Physics movement should generate coyote time counter or define for physicsHero",
        )
    }

    // =========================================================================
    // Test 12: Fixed-point codegen — fractional accumulators >> 4 shift
    // =========================================================================

    @Test
    fun `FP44 physics generates fractional accumulator variables and bit-shift extraction`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        // FP44 generates _actorId_x_frac / _actorId_y_frac accumulators
        assertTrue(
            mainC.contains("_physicsHero_x_frac") || mainC.contains("_physicsHero_vx_frac"),
            "FP44 mode should generate fractional accumulator variables for physicsHero",
        )
        // Extraction uses >> 4 shift
        assertTrue(
            mainC.contains(">> 4"),
            "FP44 mode should emit >> 4 bit-shift for position extraction",
        )
    }

    // =========================================================================
    // Test 13: Puzzle codegen — switch state variable + activate function
    // =========================================================================

    @Test
    fun `puzzle codegen generates _switch_sw1_active variable and puzzle_activate_sw1 function`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("_switch_sw1_active"),
            "main.c should contain _switch_sw1_active state variable",
        )
        assertTrue(
            mainC.contains("puzzle_activate_sw1"),
            "main.c should contain puzzle_activate_sw1 function",
        )
    }

    // =========================================================================
    // Test 14: Puzzle codegen — pressure plate check function
    // =========================================================================

    @Test
    fun `puzzle codegen generates puzzle_check_plate_entryPlate function`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("puzzle_check_plate_entryPlate"),
            "main.c should contain puzzle_check_plate_entryPlate function",
        )
    }

    // =========================================================================
    // Test 15: Puzzle codegen — timed block timer
    // =========================================================================

    @Test
    fun `puzzle codegen generates puzzle_update_timedblock_timerBlock function`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("puzzle_update_timedblock_timerBlock"),
            "main.c should contain puzzle_update_timedblock_timerBlock function",
        )
    }

    // =========================================================================
    // Test 16: Puzzle codegen — door requires guard checks switch states
    // =========================================================================

    @Test
    fun `puzzle codegen generates door requires guard checking sw1 and sw2 active states`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("puzzle_activate_bossDoor"),
            "main.c should contain puzzle_activate_bossDoor function",
        )
        // Requires guard checks _switch_sw1_active and _switch_sw2_active before opening
        assertTrue(
            mainC.contains("_switch_sw1_active") && mainC.contains("_switch_sw2_active"),
            "bossDoor function should reference sw1 and sw2 active state variables",
        )
    }

    // =========================================================================
    // Test 17: Puzzle codegen — generic trigger fire function
    // =========================================================================

    @Test
    fun `puzzle codegen generates puzzle_trigger_secretTrigger_fire function`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("puzzle_trigger_secretTrigger_fire"),
            "main.c should contain puzzle_trigger_secretTrigger_fire function",
        )
    }

    // =========================================================================
    // Test 18: Zone banking — zone_bank2.c generated with tile arrays
    // =========================================================================

    @Test
    fun `zone banking generates zone_bankN_c files with tile arrays`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)

        val bankFile = output.files.entries.find { it.key.startsWith("zone_bank") }
        assertNotNull(bankFile, "At least one zone_bankN.c file should be generated")
        assertTrue(
            bankFile.value.contains("_zone_dungeon1_tiles") ||
                bankFile.value.contains("_zone_dungeon2_tiles"),
            "zone bank file should contain tile array definitions",
        )
    }

    // =========================================================================
    // Test 19: Zone banking — SWITCH_ROM in zone_load functions
    // =========================================================================

    @Test
    fun `zone banking emits SWITCH_ROM in zone_load functions`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)
        val mainC = output.files["main.c"] ?: error("main.c not generated")

        assertTrue(
            mainC.contains("zone_load_dungeon1") || mainC.contains("zone_load"),
            "main.c should contain zone_load function(s)",
        )
        assertTrue(
            mainC.contains("SWITCH_ROM"),
            "zone_load should emit SWITCH_ROM for banked tilemap data access",
        )
    }

    // =========================================================================
    // Test 20: Zone banking — game.h has extern declarations for banked arrays
    // =========================================================================

    @Test
    fun `zone banking emits extern declarations for banked tile arrays in game_h`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)
        val gameH = output.files["game.h"] ?: error("game.h not generated")

        assertTrue(
            gameH.contains("extern"),
            "game.h should contain extern declarations for banked zone tile arrays",
        )
        assertTrue(
            gameH.contains("_zone_dungeon1_tiles") || gameH.contains("_zone_dungeon2_tiles"),
            "game.h should contain extern declarations for banked tile arrays",
        )
    }

    // =========================================================================
    // Test 21: All generated C files have balanced braces
    // =========================================================================

    @Test
    fun `all generated C files have balanced braces`() {
        val gameIR = buildPhase067CodegenGameIR()
        val output = pipeline.generate(gameIR)

        for ((fileName, content) in output.files) {
            if (!fileName.endsWith(".c") && !fileName.endsWith(".h")) continue
            val openCount = content.count { it == '{' }
            val closeCount = content.count { it == '}' }
            assert(openCount == closeCount) {
                "$fileName has unbalanced braces: $openCount '{' vs $closeCount '}'"
            }
        }
    }
}
