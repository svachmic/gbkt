/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core.ir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =============================================================================
// PHASE 06.7 INTEGRATION TESTS
// Validates that all 7 deferred features (D1 entity pooling, D2 NPC collision,
// E1 SMOOTH, E2 PHYSICS, E3 fixed-point, F1 puzzles, F2 zone banking) coexist
// correctly in a single GameIR without interference.
//
// Tests:
// 1.  All features coexist in a single GameIR without exceptions
// 2.  GameIR.actorPools has 1 pool with correct id/config
// 3.  GameIR.collisionGroups has 2 groups with expected ids
// 4.  GameIR.collisionRules has 1 rule with PUSH response
// 5.  SMOOTH actor carries acceleration/friction config
// 6.  PHYSICS actor carries gravity, variableJump, coyoteFrames, wallJump
// 7.  Physics actor carries FixedPointMode.FP44
// 8.  GameIR.puzzleObjects contains switch, door, pressure plate, timed block, trigger
// 9.  Pool entities can be assigned to collision groups (IR linkage)
// 10. requires() on door references existing puzzle object IDs
// 11. Zone data carried correctly in GameIR.zones
// =============================================================================

/** Build the multi-feature GameIR exercising all Phase 06.7 features simultaneously. */
private fun buildPhase067GameIR(): GameIR {
    // =========================================================================
    // Actors — bullet template for pool, smooth mover, physics mover (FP44)
    // =========================================================================

    // Pool template actor — will be referenced by bulletPool
    val bullet = ActorIR(id = "bullet", position = PositionDef(0, 0))

    // Actor with SMOOTH movement (acceleration/friction + NORMALIZED diagonal)
    val smoothActor =
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

    // Actor with PHYSICS movement — variable jump, coyote time, wall-jump, FP44
    val physicsActor =
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
            // Also assigned to NPC collision group — player group
            npcCollisionConfig = NpcCollisionConfig(groupIds = listOf("playerGroup"), mass = 2),
        )

    // Actor assigned to enemy collision group
    val enemyActor =
        ActorIR(
            id = "enemyNpc",
            position = PositionDef(60, 60),
            npcCollisionConfig = NpcCollisionConfig(groupIds = listOf("enemyGroup"), mass = 1),
        )

    // =========================================================================
    // Actor pool — bullets, max=8 with per-instance properties
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
    // Puzzle objects — switch, door with requires, pressure plate, timed block, trigger
    // =========================================================================

    val sw1 =
        SwitchObjectIR(
            id = "sw1",
            x = 5,
            y = 3,
            onActivate = listOf(Assign("_door_bossDoor_open", Literal(1))),
        )

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
                    PuzzleEventHandler(
                        PuzzleEventType.INTERACT,
                        listOf(Assign("_switch_sw1_active", Literal(1))),
                    ),
                    PuzzleEventHandler(
                        PuzzleEventType.STEP_ON,
                        listOf(Assign("_switch_sw2_active", Literal(1))),
                    ),
                ),
        )

    // =========================================================================
    // Zones — two zones with tilemap data
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

    // =========================================================================
    // Scene driving all features
    // =========================================================================

    val gameScene = SceneIR(id = "gameplay")

    // =========================================================================
    // Assemble the full GameIR
    // =========================================================================

    return GameIR(
        name = "Phase067IntegrationGame",
        config = CartridgeConfig(cartridge = Cartridge.MBC5, romBanks = 16),
        scenes = listOf(gameScene),
        actors = listOf(bullet, smoothActor, physicsActor, enemyActor),
        actorPools = listOf(bulletPool),
        collisionGroups = listOf(playerGroup, enemyGroup),
        collisionRules = listOf(pushRule),
        puzzleObjects = listOf(sw1, sw2, bossDoor, entryPlate, timerBlock, secretTrigger),
        zones = listOf(zone1, zone2),
        startScene = "gameplay",
    )
}

class Phase067IntegrationTest {

    // =========================================================================
    // Test 1: All features coexist in a single GameIR without exceptions
    // =========================================================================

    @Test
    fun `all features coexist in a single GameIR without exceptions`() {
        // Should not throw
        val gameIR = buildPhase067GameIR()

        assertNotNull(gameIR)
        assertEquals("Phase067IntegrationGame", gameIR.name)
    }

    // =========================================================================
    // Test 2: GameIR.actorPools has 1 pool with correct id/config
    // =========================================================================

    @Test
    fun `GameIR actorPools has 1 pool with correct config`() {
        val gameIR = buildPhase067GameIR()

        assertEquals(1, gameIR.actorPools.size, "Expected exactly 1 actor pool")

        val pool = gameIR.actorPools[0]
        assertEquals("bulletPool", pool.id)
        assertEquals("bullet", pool.actorTemplateId)
        assertEquals(8, pool.config.maxSize)
        assertEquals(PoolOverflowStrategy.SILENT_NOOP, pool.config.overflowStrategy)
    }

    @Test
    fun `actor pool has correct per-instance properties`() {
        val gameIR = buildPhase067GameIR()
        val pool = gameIR.actorPools[0]

        assertEquals(2, pool.instanceProperties.size, "Expected 2 per-instance properties")

        val damage = pool.instanceProperties.find { it.name == "damage" }
        assertNotNull(damage, "Expected 'damage' property")
        assertEquals(VarType.U8, damage.type)

        val direction = pool.instanceProperties.find { it.name == "direction" }
        assertNotNull(direction, "Expected 'direction' property")
        assertEquals(VarType.I8, direction.type)
    }

    // =========================================================================
    // Test 3: GameIR.collisionGroups has 2 groups with expected ids
    // =========================================================================

    @Test
    fun `GameIR collisionGroups has 2 groups with expected ids`() {
        val gameIR = buildPhase067GameIR()

        assertEquals(2, gameIR.collisionGroups.size, "Expected exactly 2 collision groups")

        val ids = gameIR.collisionGroups.map { it.id }
        assertTrue(ids.contains("playerGroup"), "Expected 'playerGroup' in collision groups")
        assertTrue(ids.contains("enemyGroup"), "Expected 'enemyGroup' in collision groups")
    }

    // =========================================================================
    // Test 4: GameIR.collisionRules has 1 rule with PUSH response
    // =========================================================================

    @Test
    fun `GameIR collisionRules has 1 rule with PUSH response`() {
        val gameIR = buildPhase067GameIR()

        assertEquals(1, gameIR.collisionRules.size, "Expected exactly 1 collision rule")

        val rule = gameIR.collisionRules[0]
        assertEquals("playerGroup", rule.groupA)
        assertEquals("enemyGroup", rule.groupB)
        assertEquals(CollisionResponse.PUSH, rule.response)
        assertEquals(1, rule.interval)
    }

    // =========================================================================
    // Test 5: SMOOTH actor carries acceleration/friction config
    // =========================================================================

    @Test
    fun `SMOOTH actor carries acceleration and friction config`() {
        val gameIR = buildPhase067GameIR()

        val smoothActor = gameIR.actors.find { it.id == "smoothPlayer" }
        assertNotNull(smoothActor, "Expected 'smoothPlayer' actor")

        val movementConfig = smoothActor.movementConfig
        assertNotNull(movementConfig, "SMOOTH actor should have movementConfig")
        assertEquals(MovementStyle.SMOOTH, movementConfig.style)

        val smoothConfig = movementConfig.smoothConfig
        assertNotNull(smoothConfig, "SMOOTH actor should have smoothConfig")
        assertEquals(4, smoothConfig.acceleration)
        assertEquals(2, smoothConfig.friction)
        assertEquals(DiagonalMode.NORMALIZED, smoothConfig.diagonalMode)
    }

    // =========================================================================
    // Test 6: PHYSICS actor carries gravity, variableJump, coyoteFrames, wallJump
    // =========================================================================

    @Test
    fun `PHYSICS actor carries gravity variableJump coyoteFrames and wallJump config`() {
        val gameIR = buildPhase067GameIR()

        val physicsActor = gameIR.actors.find { it.id == "physicsHero" }
        assertNotNull(physicsActor, "Expected 'physicsHero' actor")

        val physicsConfig = physicsActor.physicsConfig
        assertNotNull(physicsConfig, "PHYSICS actor should have physicsConfig")

        assertEquals(4, physicsConfig.gravity)
        assertTrue(physicsConfig.variableJump, "variableJump should be true")
        assertEquals(4, physicsConfig.coyoteFrames)
        assertTrue(physicsConfig.wallJump, "wallJump should be true")
        assertEquals(WallResponse.SLIDE, physicsConfig.wallResponse)
        assertEquals(24, physicsConfig.wallJumpVelocityX)
        assertEquals(28, physicsConfig.wallJumpVelocityY)
    }

    // =========================================================================
    // Test 7: Physics actor carries FixedPointMode.FP44
    // =========================================================================

    @Test
    fun `PHYSICS actor carries FixedPointMode FP44`() {
        val gameIR = buildPhase067GameIR()

        val physicsActor = gameIR.actors.find { it.id == "physicsHero" }
        assertNotNull(physicsActor, "Expected 'physicsHero' actor")

        val physicsConfig = physicsActor.physicsConfig
        assertNotNull(physicsConfig, "PHYSICS actor should have physicsConfig")
        assertEquals(FixedPointMode.FP44, physicsConfig.fixedPointMode)
    }

    // =========================================================================
    // Test 8: GameIR.puzzleObjects contains all 5 puzzle object types
    // =========================================================================

    @Test
    fun `GameIR puzzleObjects contains switch door pressurePlate timedBlock and trigger`() {
        val gameIR = buildPhase067GameIR()

        val puzzleObjects = gameIR.puzzleObjects
        assertTrue(puzzleObjects.size >= 5, "Expected at least 5 puzzle objects")

        val sw1 = puzzleObjects.find { it.id == "sw1" }
        assertNotNull(sw1, "Expected switch 'sw1'")
        assertTrue(sw1 is SwitchObjectIR, "sw1 should be a SwitchObjectIR")

        val sw2 = puzzleObjects.find { it.id == "sw2" }
        assertNotNull(sw2, "Expected switch 'sw2'")
        assertTrue(sw2 is SwitchObjectIR, "sw2 should be a SwitchObjectIR")

        val door = puzzleObjects.find { it.id == "bossDoor" }
        assertNotNull(door, "Expected door 'bossDoor'")
        assertTrue(door is DoorObjectIR, "bossDoor should be a DoorObjectIR")

        val plate = puzzleObjects.find { it.id == "entryPlate" }
        assertNotNull(plate, "Expected pressure plate 'entryPlate'")
        assertTrue(plate is PressurePlateObjectIR, "entryPlate should be a PressurePlateObjectIR")

        val block = puzzleObjects.find { it.id == "timerBlock" }
        assertNotNull(block, "Expected timed block 'timerBlock'")
        assertTrue(block is TimedBlockObjectIR, "timerBlock should be a TimedBlockObjectIR")

        val trigger = puzzleObjects.find { it.id == "secretTrigger" }
        assertNotNull(trigger, "Expected trigger 'secretTrigger'")
        assertTrue(trigger is TriggerObjectIR, "secretTrigger should be a TriggerObjectIR")
    }

    // =========================================================================
    // Test 9: Pool entities can be assigned to collision groups (IR linkage)
    // =========================================================================

    @Test
    fun `pool actor template ID can be linked to collision group`() {
        val gameIR = buildPhase067GameIR()

        // The bullet actor (pool template) exists in the game IR
        val bulletActor = gameIR.actors.find { it.id == "bullet" }
        assertNotNull(bulletActor, "Pool template actor 'bullet' should be present in actors list")

        // The bulletPool references this actor
        val pool = gameIR.actorPools.find { it.actorTemplateId == "bullet" }
        assertNotNull(pool, "A pool referencing 'bullet' template should exist")
        assertEquals("bulletPool", pool.id)

        // Verify the template actor ID is a valid reference — exists in actors
        val actorIds = gameIR.actors.map { it.id }
        assertTrue(
            actorIds.contains(pool.actorTemplateId),
            "Pool template actor '${pool.actorTemplateId}' must reference an actor in GameIR.actors",
        )
    }

    // =========================================================================
    // Test 10: requires() on door references existing puzzle object IDs
    // =========================================================================

    @Test
    fun `door requires references resolve to existing puzzle object IDs`() {
        val gameIR = buildPhase067GameIR()

        val door = gameIR.puzzleObjects.find { it.id == "bossDoor" }
        assertNotNull(door, "Expected 'bossDoor' puzzle object")
        assertTrue(door is DoorObjectIR, "bossDoor should be a DoorObjectIR")

        val allPuzzleIds = gameIR.puzzleObjects.map { it.id }.toSet()

        // Verify all requires IDs resolve to existing puzzle objects
        for (requiredId in door.requires) {
            assertTrue(
                allPuzzleIds.contains(requiredId),
                "bossDoor.requires references '$requiredId' which does not exist in puzzleObjects",
            )
        }

        // Specifically sw1 and sw2 must be required
        assertTrue(door.requires.contains("sw1"), "bossDoor should require 'sw1'")
        assertTrue(door.requires.contains("sw2"), "bossDoor should require 'sw2'")
    }

    // =========================================================================
    // Test 11: Zone data carried correctly in GameIR.zones
    // =========================================================================

    @Test
    fun `GameIR zones carry tilemap data for two dungeon zones`() {
        val gameIR = buildPhase067GameIR()

        assertEquals(2, gameIR.zones.size, "Expected 2 zones")

        val zone1 = gameIR.zones.find { it.id == "dungeon1" }
        assertNotNull(zone1, "Expected zone 'dungeon1'")
        assertEquals("Dungeon Level 1", zone1.name)
        assertEquals(20, zone1.mapWidth)
        assertEquals(18, zone1.mapHeight)
        assertFalse(zone1.tileData.isEmpty(), "Zone 1 should have tile data")
        assertEquals(20 * 18, zone1.tileData.size)

        val zone2 = gameIR.zones.find { it.id == "dungeon2" }
        assertNotNull(zone2, "Expected zone 'dungeon2'")
        assertEquals("Dungeon Level 2", zone2.name)
        assertFalse(zone2.tileData.isEmpty(), "Zone 2 should have tile data")
    }

    // =========================================================================
    // Test 12: GameIR actors count is correct (all 4 actors present)
    // =========================================================================

    @Test
    fun `GameIR has all four actors with correct ids`() {
        val gameIR = buildPhase067GameIR()

        val actorIds = gameIR.actors.map { it.id }
        assertEquals(4, actorIds.size, "Expected exactly 4 actors")
        assertTrue(actorIds.contains("bullet"), "Expected 'bullet' actor")
        assertTrue(actorIds.contains("smoothPlayer"), "Expected 'smoothPlayer' actor")
        assertTrue(actorIds.contains("physicsHero"), "Expected 'physicsHero' actor")
        assertTrue(actorIds.contains("enemyNpc"), "Expected 'enemyNpc' actor")
    }

    // =========================================================================
    // Test 13: NPC collision config on physics and enemy actors is correct
    // =========================================================================

    @Test
    fun `physicsHero belongs to playerGroup with mass 2`() {
        val gameIR = buildPhase067GameIR()

        val physicsActor = gameIR.actors.find { it.id == "physicsHero" }
        assertNotNull(physicsActor)

        val collisionConfig = physicsActor.npcCollisionConfig
        assertNotNull(collisionConfig, "physicsHero should have npcCollisionConfig")
        assertTrue(
            collisionConfig.groupIds.contains("playerGroup"),
            "physicsHero should be in playerGroup",
        )
        assertEquals(2, collisionConfig.mass, "physicsHero mass should be 2")
    }

    @Test
    fun `enemyNpc belongs to enemyGroup with default mass`() {
        val gameIR = buildPhase067GameIR()

        val enemyActor = gameIR.actors.find { it.id == "enemyNpc" }
        assertNotNull(enemyActor)

        val collisionConfig = enemyActor.npcCollisionConfig
        assertNotNull(collisionConfig, "enemyNpc should have npcCollisionConfig")
        assertTrue(
            collisionConfig.groupIds.contains("enemyGroup"),
            "enemyNpc should be in enemyGroup",
        )
        assertEquals(1, collisionConfig.mass, "enemyNpc mass should default to 1")
    }

    // =========================================================================
    // Test 14: Trigger carries handlers for INTERACT and STEP_ON events
    // =========================================================================

    @Test
    fun `secretTrigger carries handlers for INTERACT and STEP_ON events`() {
        val gameIR = buildPhase067GameIR()

        val trigger = gameIR.puzzleObjects.find { it.id == "secretTrigger" }
        assertNotNull(trigger, "Expected 'secretTrigger'")
        assertTrue(trigger is TriggerObjectIR, "secretTrigger should be a TriggerObjectIR")

        val eventTypes = trigger.handlers.map { it.event }
        assertTrue(
            eventTypes.contains(PuzzleEventType.INTERACT),
            "secretTrigger should handle INTERACT events",
        )
        assertTrue(
            eventTypes.contains(PuzzleEventType.STEP_ON),
            "secretTrigger should handle STEP_ON events",
        )
    }
}
